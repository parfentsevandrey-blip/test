"""Small type-coercion helpers shared by anything that turns loosely-typed
external input (file import rows, scraped HTML text, CLI args) into the
typed fields `Offer`/`Config` expect.
"""

from __future__ import annotations

from datetime import date, datetime
from typing import Any, Optional


def to_float(v: Any) -> Optional[float]:
    if v is None or v == "":
        return None
    return float(str(v).replace(" ", "").replace(",", "."))


def to_int(v: Any) -> Optional[int]:
    f = to_float(v)
    return int(f) if f is not None else None


def to_bool(v: Any) -> bool:
    if isinstance(v, bool):
        return v
    return str(v).strip().lower() in {"1", "true", "yes", "y", "да"}


def to_date(v: Any) -> Optional[date]:
    if not v:
        return None
    if isinstance(v, date):
        return v
    return datetime.strptime(str(v)[:10], "%Y-%m-%d").date()
