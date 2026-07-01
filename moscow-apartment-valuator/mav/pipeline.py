"""Ties market filtering and scoring together into a ranked shortlist."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from typing import List, Optional

from .config import Config
from .models import Offer
from .valuation.scoring import Verdict, evaluate


@dataclass
class RankedOffer:
    offer: Offer
    verdict: Verdict


# Confirmed deals outrank "suspicious" listings regardless of raw discount size:
# a huge nominal discount is exactly what makes a listing suspicious (more likely
# a data error, encumbrance or distressed sale than a real bargain), so it must
# not out-sort genuine, trustworthy deals just because the number is bigger.
_LABEL_SORT_RANK = {"strong_undervalued": 0, "undervalued": 1, "suspicious": 2}


def filter_market(offers: List[Offer], cfg: Config) -> List[Offer]:
    """Keeps only valid, in-scope listings: right city, right deal/accommodation
    type, and in a complex built no earlier than `cfg.market.min_built_year`.
    """
    out = []
    for o in offers:
        if not o.is_valid():
            continue
        if cfg.market.city and o.city and o.city.strip().lower() != cfg.market.city.strip().lower():
            continue
        if o.built_year is None or o.built_year < cfg.market.min_built_year:
            continue
        if cfg.market.deal_type and o.deal_type != cfg.market.deal_type:
            continue
        if cfg.market.accommodation_type and o.accommodation_type != cfg.market.accommodation_type:
            continue
        out.append(o)
    return out


def rank_offers(offers: List[Offer], cfg: Config, as_of: Optional[date] = None) -> List[RankedOffer]:
    """Filters to in-scope listings, scores each against its in-scope peers,
    and returns the shortlist of undervalued (and, if configured, suspicious)
    listings sorted best-deal-first.
    """
    as_of = as_of or date.today()
    pool = filter_market(offers, cfg)

    ranked = [RankedOffer(o, evaluate(o, pool, cfg)) for o in pool]

    include_labels = {"strong_undervalued", "undervalued"}
    if cfg.output.include_suspicious:
        include_labels.add("suspicious")

    selected = [r for r in ranked if r.verdict.label in include_labels]

    def is_fresh(r: RankedOffer) -> bool:
        days = r.offer.days_on_market(as_of)
        return days is not None and days <= cfg.scoring.fresh_listing_days

    selected.sort(
        key=lambda r: (_LABEL_SORT_RANK[r.verdict.label], not is_fresh(r), -r.verdict.score)
    )

    return selected[: cfg.output.top_n]
