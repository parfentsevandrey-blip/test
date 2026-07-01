"""Groups a listing with its peers so a price/m2 baseline can be computed.

Comparable selection is the core of the "is this lot underpriced" question:
comparing a listing against the wrong peer group (different complex, wildly
different area or a different fit-out level) produces a meaningless price
signal. We only ever compare within the same residential complex (ЖК), and
narrow further by room count, area band, floor position and finish level
when there is enough data to do so without starving the sample.

This module trusts that its caller has already scoped `pool` to the market
of interest (city, deal type, built-year cutoff — see pipeline.filter_market);
it does not re-check those fields itself.
"""

from __future__ import annotations

from dataclasses import dataclass
from statistics import median
from typing import List, Optional

from ..models import Offer

# Scales the median absolute deviation to be comparable to a standard
# deviation under normality, so downstream code can reason about it the
# same way it would about a std-dev-based z-score.
MAD_TO_STD_SCALE = 1.4826


@dataclass
class ComparableSet:
    tier: str            # "A" (same ЖК + rooms + area band) or "B" (same ЖК only)
    peers: List[Offer]
    median_ppsqm: float
    mad_ppsqm: float      # robust dispersion estimate, scaled like a std-dev


def _robust_stats(values: List[float]) -> tuple[float, float]:
    m = median(values)
    mad = median(abs(v - m) for v in values) * MAD_TO_STD_SCALE
    return m, mad


def _floor_bucket(offer: Offer) -> Optional[str]:
    if offer.floor is None or offer.floors_total is None:
        return None
    if offer.floor == 1:
        return "low"
    if offer.floor == offer.floors_total:
        return "high"
    return "mid"


def _same_complex(a: Offer, b: Offer) -> bool:
    if not a.residential_complex or not b.residential_complex:
        return False
    return a.residential_complex.strip().lower() == b.residential_complex.strip().lower()


def find_comparables(target: Offer, pool: List[Offer], cfg) -> Optional[ComparableSet]:
    """Returns the best available comparable set for `target`, or None if
    even the loosest tier doesn't have enough peers to be trustworthy.
    """
    same_complex = [o for o in pool if o is not target and o.is_valid() and _same_complex(target, o)]
    if not same_complex:
        return None

    area_lo = target.area_total * (1 - cfg.area_tolerance_pct / 100)
    area_hi = target.area_total * (1 + cfg.area_tolerance_pct / 100)

    tier_a = [
        o for o in same_complex
        if o.is_studio == target.is_studio
        and o.rooms == target.rooms
        and area_lo <= o.area_total <= area_hi
    ]

    if cfg.floor_bucket:
        target_bucket = _floor_bucket(target)
        if target_bucket is not None:
            bucketed = [o for o in tier_a if _floor_bucket(o) == target_bucket]
            if len(bucketed) >= cfg.min_comparables:
                tier_a = bucketed

    if cfg.finish_type_strict and target.finish_type:
        finished_matched = [o for o in tier_a if o.finish_type == target.finish_type]
        if len(finished_matched) >= cfg.min_comparables:
            tier_a = finished_matched

    if len(tier_a) >= cfg.min_comparables:
        m, mad = _robust_stats([o.price_per_sqm for o in tier_a])
        return ComparableSet(tier="A", peers=tier_a, median_ppsqm=m, mad_ppsqm=mad)

    if len(same_complex) >= cfg.min_comparables:
        m, mad = _robust_stats([o.price_per_sqm for o in same_complex])
        return ComparableSet(tier="B", peers=same_complex, median_ppsqm=m, mad_ppsqm=mad)

    return None
