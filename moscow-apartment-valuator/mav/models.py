"""Core data model for a normalized apartment listing (offer)."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date
from typing import Optional


FINISH_TYPE_ALIASES = {
    "без отделки": "shell",
    "черновая": "shell",
    "черновая отделка": "shell",
    "предчистовая": "pre_finish",
    "предчистовая отделка": "pre_finish",
    "white box": "pre_finish",
    "чистовая": "finished",
    "чистовая отделка": "finished",
    "с отделкой": "finished",
    "под ключ": "finished",
    "меблированная": "finished",
    "дизайнерский ремонт": "finished",
}


def normalize_finish_type(raw: Optional[str]) -> Optional[str]:
    if not raw:
        return None
    return FINISH_TYPE_ALIASES.get(raw.strip().lower())


@dataclass
class Offer:
    """A single apartment listing, normalized to a common schema.

    All fields are provider-agnostic: any data source (live fetch, manual
    export, CSV/JSON import) must be converted into this shape before it
    reaches the valuation engine.
    """

    id: str
    url: str
    city: str
    price: float                      # rubles, total price (not per month)
    area_total: float                 # square metres
    rooms: int                        # 1, 2, 3, 4, 5 (studios use rooms=1, is_studio=True)
    is_studio: bool = False

    residential_complex: Optional[str] = None   # ЖК name, e.g. "Символ"
    built_year: Optional[int] = None            # ЖК/house completion year

    address: Optional[str] = None
    district: Optional[str] = None
    metro: Optional[str] = None

    floor: Optional[int] = None
    floors_total: Optional[int] = None
    area_living: Optional[float] = None
    area_kitchen: Optional[float] = None
    finish_type_raw: Optional[str] = None

    seller_type: Optional[str] = None   # "owner" | "agency" | "developer" | "realtor" | None
    deal_type: str = "sale"
    accommodation_type: str = "flat"

    listed_at: Optional[date] = None
    updated_at: Optional[date] = None

    source: str = "cian.ru"
    raw: dict = field(default_factory=dict)   # original provider payload, kept for traceability

    @property
    def finish_type(self) -> Optional[str]:
        return normalize_finish_type(self.finish_type_raw)

    @property
    def price_per_sqm(self) -> float:
        if not self.area_total:
            return 0.0
        return self.price / self.area_total

    def days_on_market(self, as_of: date) -> Optional[int]:
        if not self.listed_at:
            return None
        return (as_of - self.listed_at).days

    def is_valid(self) -> bool:
        return bool(self.price and self.price > 0 and self.area_total and self.area_total > 0 and self.rooms)
