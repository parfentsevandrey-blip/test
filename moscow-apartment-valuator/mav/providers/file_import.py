"""Loads offers from a local JSON or CSV file.

This is the recommended, ToS-safe way to feed the valuator (see the
project README for why cian.ru cannot be scraped reliably or safely from
a script): export the listings you're interested in — by hand, via a
saved search, or with a personal browser session — into the schema below,
and let this tool do the comparable-matching and scoring.

JSON format: a list of objects with (at minimum) the required fields
below; extra/unknown keys are ignored. CSV format: same field names as
column headers (comma or semicolon delimited, auto-detected).

Required fields: id, url, city, price, area_total, rooms
Optional fields: is_studio, residential_complex, built_year, address,
district, metro, floor, floors_total, area_living, area_kitchen,
finish_type_raw, seller_type, deal_type, accommodation_type,
listed_at, updated_at (dates as YYYY-MM-DD)
"""

from __future__ import annotations

import csv
import json
from pathlib import Path
from typing import List

from .._coerce import to_bool, to_date, to_float, to_int
from ..models import Offer

REQUIRED_FIELDS = ("id", "url", "city", "price", "area_total", "rooms")

_OFFER_FIELD_NAMES = {f for f in Offer.__dataclass_fields__.keys()}


def _row_to_offer(row: dict) -> Offer:
    missing = [f for f in REQUIRED_FIELDS if row.get(f) in (None, "")]
    if missing:
        raise ValueError(f"listing {row.get('id', '?')!r} is missing required field(s): {missing}")

    kwargs = {k: v for k, v in row.items() if k in _OFFER_FIELD_NAMES and k != "raw"}
    kwargs["price"] = to_float(kwargs["price"])
    kwargs["area_total"] = to_float(kwargs["area_total"])
    kwargs["rooms"] = to_int(kwargs["rooms"])
    kwargs["is_studio"] = to_bool(kwargs.get("is_studio", False))
    kwargs["built_year"] = to_int(kwargs.get("built_year"))
    kwargs["floor"] = to_int(kwargs.get("floor"))
    kwargs["floors_total"] = to_int(kwargs.get("floors_total"))
    kwargs["area_living"] = to_float(kwargs.get("area_living"))
    kwargs["area_kitchen"] = to_float(kwargs.get("area_kitchen"))
    kwargs["listed_at"] = to_date(kwargs.get("listed_at"))
    kwargs["updated_at"] = to_date(kwargs.get("updated_at"))
    kwargs.setdefault("deal_type", "sale")
    kwargs.setdefault("accommodation_type", "flat")

    return Offer(raw=row, **kwargs)


class FileImportProvider:
    """Reads offers from a .json or .csv file on disk."""

    def __init__(self, path: str):
        self.path = Path(path)

    def fetch(self) -> List[Offer]:
        if self.path.suffix.lower() == ".json":
            return self._read_json()
        if self.path.suffix.lower() == ".csv":
            return self._read_csv()
        raise ValueError(f"unsupported file extension: {self.path.suffix} (expected .json or .csv)")

    def _read_json(self) -> List[Offer]:
        rows = json.loads(self.path.read_text(encoding="utf-8"))
        if not isinstance(rows, list):
            raise ValueError("JSON input must be a list of listing objects")
        return [_row_to_offer(row) for row in rows]

    def _read_csv(self) -> List[Offer]:
        text = self.path.read_text(encoding="utf-8")
        dialect = csv.Sniffer().sniff(text.splitlines()[0])
        reader = csv.DictReader(text.splitlines(), dialect=dialect)
        return [_row_to_offer(row) for row in reader]
