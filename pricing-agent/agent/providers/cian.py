"""Адаптер Циан API — каркас под боевой источник.

ВАЖНО: это заготовка, а не рабочая интеграция. Точные пути методов и схема ответа
приходят вместе с ключом доступа, который выдаётся по заявке на import@cian.ru
(см. docs/IMPLEMENTATION.md). Здесь зафиксирована форма интеграции: где делать запрос,
как маппить ответ в наш Comp, что кэшировать и как деградировать при недоступности.

Что из этого реально пригодится по документации Циан для агентств:
  * оценка ₽/м² в многоквартирном доме по адресу и данные БТИ — базовая линия по дому;
  * статистика по нашим объявлениям (просмотры, звонки, избранное) — сигналы спроса,
    которых нет в xlsx-реестре;
  * список наших объявлений и списания — для контроля публикации после смены цены.

Чего в партнёрском API нет: выгрузки чужих объявлений списком. Конкурентную выборку
собирают либо через платную Циан.Аналитику, либо через договор с поставщиком данных.
"""

from __future__ import annotations

import logging
import os
from datetime import date
from typing import Any

import httpx

from ..models import Apartment, Comp, Finish

log = logging.getLogger(__name__)

BASE_URL = os.getenv("CIAN_API_BASE", "https://api.cian.ru")

# Пути методов задаются переменными окружения: подставляются из документации,
# полученной вместе с ключом. Значения по умолчанию — заведомо плейсхолдеры.
VALUATION_PATH = os.getenv("CIAN_VALUATION_PATH", "")
OFFERS_PATH = os.getenv("CIAN_OFFERS_PATH", "")


class CianProvider:
    """Источник аналогов на базе партнёрского API Циан."""

    name = "cian"

    def __init__(self, api_key: str | None = None, timeout: float = 10.0) -> None:
        self.api_key = api_key or os.getenv("CIAN_API_KEY", "")
        self.timeout = timeout
        self._client: httpx.Client | None = None
        self._warned = False

    @property
    def configured(self) -> bool:
        return bool(self.api_key and VALUATION_PATH and OFFERS_PATH)

    @property
    def client(self) -> httpx.Client:
        if self._client is None:
            self._client = httpx.Client(
                base_url=BASE_URL,
                timeout=self.timeout,
                headers={"Authorization": f"Bearer {self.api_key}"},
            )
        return self._client

    def fetch_comps(self, apartment: Apartment, radius_km: float = 1.5) -> list[Comp]:
        """Аналоги по дому и окрестности.

        Пока метод не сконфигурирован, возвращает пустой список: ChainProvider просто
        пойдёт за данными в следующий источник, а бот покажет вердикт с пометкой о том,
        что часть источников недоступна. Падать здесь нельзя — бот отвечает клиенту.
        """
        if not self.configured:
            if not self._warned:
                log.warning("Циан API не сконфигурирован (нет ключа или путей методов) — пропускаю")
                self._warned = True
            return []

        try:
            resp = self.client.get(
                OFFERS_PATH,
                params={
                    "address": apartment.address,
                    "rooms": apartment.rooms,
                    "area_min": apartment.area * 0.75,
                    "area_max": apartment.area * 1.35,
                    "radius": radius_km,
                },
            )
            resp.raise_for_status()
        except httpx.HTTPError as exc:
            log.error("Циан API недоступен: %s", exc)
            return []

        return [self._to_comp(item, apartment) for item in resp.json().get("items", [])]

    def house_valuation(self, address: str) -> float | None:
        """Оценка ₽/м² по дому — быстрая независимая проверка нашего коридора."""
        if not (self.api_key and VALUATION_PATH):
            return None
        try:
            resp = self.client.get(VALUATION_PATH, params={"address": address})
            resp.raise_for_status()
            return float(resp.json()["price_per_sqm"])
        except (httpx.HTTPError, KeyError, ValueError) as exc:
            log.error("Оценка по дому не получена: %s", exc)
            return None

    def _to_comp(self, item: dict[str, Any], subject: Apartment) -> Comp:
        """Маппинг ответа площадки в наш Comp. Правится под фактическую схему ответа."""
        area = float(item["totalArea"])
        return Comp(
            source=self.name,
            external_id=str(item["id"]),
            complex_name=item.get("newbuilding", {}).get("name", "") or item.get("jkName", ""),
            address=item.get("geo", {}).get("userInput", ""),
            rooms=int(item.get("roomsCount") or subject.rooms),
            area=area,
            floor=int(item["floorNumber"]),
            floors_total=int(item["building"]["floorsCount"]),
            price=int(item["bargainTerms"]["price"]),
            finish=_map_finish(item.get("decoration")),
            has_parking=bool(item.get("hasParking")),
            same_complex=_same_complex(item, subject),
            distance_km=float(item.get("distanceKm", 0.0)),
            days_on_market=_days_on_market(item.get("publishedAt")),
            price_cut_pct=_price_cut(item.get("priceChanges")),
            observed_at=date.today(),
            url=item.get("fullUrl", ""),
        )


def _map_finish(raw: str | None) -> Finish:
    """Справочник отделки площадки → наша порядковая шкала."""
    mapping = {
        "without": Finish.NONE,
        "rough": Finish.NONE,
        "whitebox": Finish.WHITEBOX,
        "preFine": Finish.WHITEBOX,
        "fine": Finish.DEVELOPER,
        "design": Finish.DESIGNER,
    }
    return mapping.get((raw or "").strip(), Finish.DEVELOPER)


def _same_complex(item: dict[str, Any], subject: Apartment) -> bool:
    name = (item.get("newbuilding", {}).get("name") or item.get("jkName") or "").lower()
    return bool(name) and name.strip() == subject.complex_name.lower().strip()


def _days_on_market(published_at: str | None) -> int | None:
    if not published_at:
        return None
    try:
        return (date.today() - date.fromisoformat(published_at[:10])).days
    except ValueError:
        return None


def _price_cut(changes: list[dict[str, Any]] | None) -> float | None:
    """Накопленное снижение от первой цены к текущей. Ключевой сигнал охлаждения рынка."""
    if not changes or len(changes) < 2:
        return None
    first, last = float(changes[0]["price"]), float(changes[-1]["price"])
    if first <= 0:
        return None
    return max(0.0, 1 - last / first)
