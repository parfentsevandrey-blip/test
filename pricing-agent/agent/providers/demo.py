"""Демо-провайдер: читает аналоги из локального JSON.

Нужен, чтобы прототип запускался и показывал полный цикл вердикта без ключей к платным
источникам. Данные в data/comps_demo.json — СИНТЕТИЧЕСКИЕ, сгенерированы для демонстрации
механики. Использовать их для реальных решений по цене нельзя.
"""

from __future__ import annotations

import json
from datetime import date
from pathlib import Path

from ..models import Apartment, Comp, Finish

DEFAULT_PATH = Path(__file__).resolve().parents[2] / "data" / "comps_demo.json"


class DemoProvider:
    name = "demo"

    def __init__(self, path: Path | str = DEFAULT_PATH) -> None:
        raw = json.loads(Path(path).read_text(encoding="utf-8"))
        self._by_lot: dict[str, list[dict]] = raw["comps"]
        self.disclaimer: str = raw.get("_disclaimer", "")

    def fetch_comps(self, apartment: Apartment, radius_km: float = 1.5) -> list[Comp]:
        out: list[Comp] = []
        for item in self._by_lot.get(apartment.id, []):
            if not item["same_complex"] and item["distance_km"] > radius_km:
                continue
            out.append(
                Comp(
                    source=item.get("source", "demo"),
                    external_id=item["external_id"],
                    complex_name=item["complex_name"],
                    address=item["address"],
                    rooms=item["rooms"],
                    area=item["area"],
                    floor=item["floor"],
                    floors_total=item["floors_total"],
                    price=item["price"],
                    finish=Finish(item["finish"]),
                    has_parking=item["has_parking"],
                    same_complex=item["same_complex"],
                    distance_km=item["distance_km"],
                    days_on_market=item.get("days_on_market"),
                    price_cut_pct=item.get("price_cut_pct"),
                    is_closed_deal=item.get("is_closed_deal", False),
                    observed_at=(
                        date.fromisoformat(item["observed_at"])
                        if item.get("observed_at")
                        else None
                    ),
                    url=item.get("url", ""),
                )
            )
        return out
