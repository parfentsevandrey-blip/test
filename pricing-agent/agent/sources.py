"""Сборка источников данных в одном месте: и CLI, и бот берут их отсюда.

Три роли разведены намеренно, потому что приходят из разных мест:

  аналоги     — из чего строится коридор. Ни Яндекс, ни партнёрское API Циан чужих
                объявлений не отдают, поэтому основной источник здесь — выгрузки
                расширения «Циан → Excel» (data/cian_exports/*.xlsx). Если их нет,
                подставляется синтетический демо-провайдер.
  спрос       — как рынок реагирует на нашу цену. Выгрузка звонков из кабинета
                Яндекса; если её нет — демо-данные (и об этом честно сообщается).
  оценка дома — независимая контрольная точка для коридора. На вердикт не влияет.
"""

from __future__ import annotations

import json
import logging
import os
from datetime import date
from pathlib import Path

from .models import Apartment, HouseValuation, OpsSnapshot
from .providers import ChainProvider
from .providers.cian_export import CianExportProvider
from .providers.demo import DemoProvider
from .providers.yandex import YandexCallLog, YandexValuation

log = logging.getLogger(__name__)

ROOT = Path(__file__).resolve().parent.parent


class DemoOps:
    """Синтетический спрос: срок экспозиции, просмотры, звонки, показы.

    Нужен, чтобы прототип показывал полный вердикт без доступа к кабинету площадки.
    Реальные данные приходят из YandexCallLog и CRM.
    """

    name = "demo-ops"

    def __init__(self, path: Path | None = None) -> None:
        self.path = path or ROOT / "data" / "ops_demo.json"
        self._data = (
            json.loads(self.path.read_text(encoding="utf-8"))["ops"]
            if self.path.exists()
            else {}
        )

    @property
    def available(self) -> bool:
        return bool(self._data)

    def fetch_ops(self, apartment: Apartment) -> OpsSnapshot | None:
        item = self._data.get(apartment.id)
        if not item:
            return None
        return OpsSnapshot(
            source=self.name,
            listed_at=date.fromisoformat(item["listed_at"]),
            views_7d=item["views_7d"],
            calls_7d=item["calls_7d"],
            viewings_30d=item["viewings_30d"],
        )


class Sources:
    """Собранный набор источников плюс отчёт о том, что реально подключено."""

    def __init__(self, *, allow_demo_ops: bool = True, allow_demo_comps: bool = True) -> None:
        self.exports = CianExportProvider(
            os.getenv("CIAN_EXPORTS_DIR", ROOT / "data" / "cian_exports"),
            include_developer=os.getenv("CIAN_INCLUDE_DEVELOPER", "0") == "1",
        )
        self.valuation = YandexValuation()
        self.calls = YandexCallLog()
        self.demo_ops = DemoOps() if allow_demo_ops else None

        self.notes: list[str] = []
        if self.exports.available:
            # Реальные аналоги есть — демо-выборка отключается целиком, иначе
            # синтетика молча размывает настоящий коридор.
            self.comps = ChainProvider(self.exports)
            self.notes.append(self.exports.summary())
        elif allow_demo_comps:
            self.comps = ChainProvider(DemoProvider())
            self.notes.append(
                "Аналоги: синтетические (data/comps_demo.json) — проверка механики, "
                "не рыночная информация. Положите выгрузки расширения в "
                f"{self.exports.directory}, чтобы считать по реальным лотам."
            )
        else:
            self.comps = ChainProvider()
            self.notes.append("Аналоги: источников нет — коридор не строится.")
        if self.calls.available:
            self.notes.append(f"Спрос: выгрузка звонков Яндекса — {self.calls.path}")
        elif self.demo_ops and self.demo_ops.available:
            self.notes.append(
                "Спрос: синтетические демо-данные. Задайте YANDEX_CALLS_EXPORT — "
                "путь к выгрузке звонков из кабинета юрлица."
            )
        else:
            self.notes.append("Спрос: данных нет — вердикт опирается только на цены.")

        if not self.valuation.configured:
            self.notes.append(
                "Оценка по дому: не подключена (YANDEX_VALUATION_URL) — "
                "коридор считается без сверки."
            )

    def apply_ops(self, apartments: list[Apartment]) -> list[Apartment]:
        """Заполняет оперативные поля. Реальные звонки перекрывают демо-данные.

        Здесь же провайдеру аналогов сообщается весь портфель: наши лоты висят на
        площадках и попадают в выгрузку, а без полного списка объект в ЖК с
        несколькими нашими квартирами сравнивался бы сам с собой.
        """
        self.exports.exclude_own(apartments)
        for a in apartments:
            snap = None
            if self.demo_ops is not None:
                snap = self.demo_ops.fetch_ops(a)

            real = self.calls.fetch_ops(a)
            if real is not None:
                # Даты выхода в экспозицию в логе звонков нет — она приходит из CRM
                # или из демо-слоя, поэтому переносим только то, что есть в выгрузке.
                snap = OpsSnapshot(
                    source=real.source,
                    listed_at=snap.listed_at if snap else None,
                    views_7d=snap.views_7d if snap else None,
                    calls_7d=real.calls_7d,
                    calls_30d=real.calls_30d,
                    viewings_30d=snap.viewings_30d if snap else None,
                )

            if snap is None:
                continue
            a.listed_at = snap.listed_at
            a.views_7d = snap.views_7d
            a.calls_7d = snap.calls_7d
            a.viewings_30d = snap.viewings_30d
        return apartments

    def valuation_for(self, apartment: Apartment) -> HouseValuation | None:
        return self.valuation.fetch_valuation(apartment)


def yrl_output_path() -> Path:
    return Path(os.getenv("YANDEX_FEED_PATH", ROOT / "data" / "yandex-feed.xml"))
