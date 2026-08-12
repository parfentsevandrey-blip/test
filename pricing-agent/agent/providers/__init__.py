"""Источники рыночных данных.

Ядро ценообразования ничего не знает о том, откуда приходят аналоги. Это позволяет
начать с демо-данных, потом подключить Циан API, потом добавить выписки ЕГРН —
не трогая логику вердикта.
"""

from __future__ import annotations

from typing import Protocol

from ..models import Apartment, Comp


class MarketDataProvider(Protocol):
    """Контракт источника аналогов."""

    name: str

    def fetch_comps(self, apartment: Apartment, radius_km: float = 1.5) -> list[Comp]:
        """Возвращает аналоги для оцениваемой квартиры: экспозиция и/или сделки."""
        ...


class ChainProvider:
    """Объединяет несколько источников и дедуплицирует аналоги.

    Один и тот же лот обычно висит и на Циан, и на Авито, и в Яндекс Недвижимости.
    Без дедупликации он трижды проголосует за свою цену и перекосит коридор.
    """

    name = "chain"

    def __init__(self, *providers: MarketDataProvider) -> None:
        self.providers = providers

    def fetch_comps(self, apartment: Apartment, radius_km: float = 1.5) -> list[Comp]:
        seen: dict[tuple, Comp] = {}
        for provider in self.providers:
            for comp in provider.fetch_comps(apartment, radius_km):
                # Ключ дедупликации: адрес + этаж + площадь с округлением до 0,5 м².
                key = (comp.address.lower(), comp.floor, round(comp.area * 2) / 2)
                current = seen.get(key)
                # При дубле оставляем более информативную запись: сделка > экспозиция,
                # затем — та, где известен срок экспозиции.
                if (
                    current is None
                    or (comp.is_closed_deal and not current.is_closed_deal)
                    or (current.days_on_market is None and comp.days_on_market is not None)
                ):
                    seen[key] = comp
        return list(seen.values())
