"""Provider interface: anything that can hand back a list of `Offer`s.

The valuation engine (mav.pipeline / mav.valuation) never talks to a data
source directly — it only ever sees `Offer` objects. That's what makes it
possible to test the scoring logic deterministically (see tests/) and to
swap the data source (file import today, something else tomorrow) without
touching the analysis code at all.
"""

from __future__ import annotations

from typing import List, Protocol

from ..models import Offer


class OfferProvider(Protocol):
    def fetch(self) -> List[Offer]:
        ...
