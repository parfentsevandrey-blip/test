"""Загрузка реестра квартир из registry.json."""

from __future__ import annotations

import json
from datetime import date
from pathlib import Path

from .models import Apartment, Finish

DEFAULT_PATH = Path(__file__).resolve().parent.parent / "data" / "registry.json"


def _as_date(value: str | None) -> date | None:
    return date.fromisoformat(value) if value else None


def load_registry(path: Path | str = DEFAULT_PATH) -> list[Apartment]:
    items = json.loads(Path(path).read_text(encoding="utf-8"))
    return [
        Apartment(
            id=it["id"],
            complex_name=it["complex_name"],
            address=it["address"],
            rooms=it["rooms"],
            area=it["area"],
            floor=it["floor"],
            floors_total=it["floors_total"],
            price=it["price"],
            finish=Finish(it["finish"]),
            has_parking=it["has_parking"],
            comment=it.get("comment", ""),
            video_url=it.get("video_url", ""),
            presentation_url=it.get("presentation_url", ""),
            listed_at=_as_date(it.get("listed_at")),
            views_7d=it.get("views_7d"),
            calls_7d=it.get("calls_7d"),
            viewings_30d=it.get("viewings_30d"),
            last_price_change=_as_date(it.get("last_price_change")),
            price_history=[tuple(x) for x in it.get("price_history", [])],
        )
        for it in items
    ]


def get(apartment_id: str, path: Path | str = DEFAULT_PATH) -> Apartment | None:
    return next((a for a in load_registry(path) if a.id == apartment_id), None)


def portfolio_checks(apartments: list[Apartment]) -> list[str]:
    """Проверки консистентности внутри своего же портфеля.

    Дешёвый и мгновенный слой анализа: не требует никаких внешних данных, но ловит
    расхождения в собственном прайсе — их видно покупателю, который смотрит два наших
    лота рядом.
    """
    notes: list[str] = []
    by_address: dict[str, list[Apartment]] = {}
    for a in apartments:
        by_address.setdefault(a.address.lower().strip(), []).append(a)

    for address, lots in by_address.items():
        if len(lots) < 2:
            continue
        for i, a in enumerate(lots):
            for b in lots[i + 1 :]:
                # Почти одинаковые по площади лоты в одном корпусе должны отличаться
                # ценой примерно на величину этажной поправки, и не больше.
                if abs(a.area - b.area) / max(a.area, b.area) > 0.05:
                    continue
                gap = abs(a.price_per_sqm - b.price_per_sqm) / min(
                    a.price_per_sqm, b.price_per_sqm
                )
                floor_gap = abs(a.floor_ratio - b.floor_ratio)
                expected = 0.10 * floor_gap
                if gap > max(expected * 2.5, 0.03):
                    higher, lower = (a, b) if a.price_per_sqm > b.price_per_sqm else (b, a)
                    notes.append(
                        f"{lots[0].address}: лоты {lower.area:g} м² ({lower.floor} эт.) и "
                        f"{higher.area:g} м² ({higher.floor} эт.) почти идентичны, но метр "
                        f"отличается на {gap:.1%} — этажная разница объясняет только "
                        f"~{expected:.1%}. Либо в одном лоте есть неучтённое преимущество, "
                        f"либо один из них выставлен неверно."
                    )
    return notes
