"""The undervaluation criteria system.

A listing is scored against the median price/m2 of its comparable peers
(see comparables.py). The raw discount is then discounted further by a
confidence factor driven by how many peers back up the comparison — a
20%-below-median reading backed by 12 peers is a much stronger signal
than the same reading backed by 4.

Verdict labels, in order of how "actionable" they are:

  strong_undervalued  - large, confidence-adjusted discount vs. peers
  undervalued         - moderate, confidence-adjusted discount vs. peers
  fair                - within the normal band around the peer median
  overvalued          - priced above the peer median
  suspicious          - discount so large it's more likely a data error,
                        encumbrance, distressed/related-party sale, or a
                        share-of-ownership listing than a genuine bargain;
                        surfaced separately so it isn't lost, but flagged
                        for manual due diligence rather than trusted at
                        face value
  insufficient_data   - not enough peers in the same complex to say anything
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import List, Optional

from ..models import Offer
from .comparables import find_comparables

# Order doubles as the canonical "most to least actionable/trustworthy" ranking
# (see pipeline.py's shortlist sort, which is keyed off this same order) so a
# new label only has to be added in one place to sort correctly everywhere.
LABELS = {
    "strong_undervalued": "Сильно недооценена",
    "undervalued": "Недооценена",
    "fair": "Рыночная цена",
    "overvalued": "Переоценена",
    "suspicious": "Подозрительно дёшево — проверить вручную",
    "insufficient_data": "Недостаточно данных для сравнения",
}


@dataclass
class Verdict:
    label: str
    score: float                          # confidence-weighted discount %, positive = underpriced
    discount_pct: float                   # raw % below (or above, if negative) the comparable median
    confidence: float                     # 0..1
    comparables_count: int
    comparables_tier: Optional[str]
    median_comp_ppsqm: Optional[float]
    notes: List[str] = field(default_factory=list)

    @property
    def label_ru(self) -> str:
        return LABELS[self.label]


def evaluate(target: Offer, pool: List[Offer], cfg) -> Verdict:
    comp_set = find_comparables(target, pool, cfg.comparables)
    if comp_set is None:
        return Verdict(
            label="insufficient_data",
            score=0.0,
            discount_pct=0.0,
            confidence=0.0,
            comparables_count=0,
            comparables_tier=None,
            median_comp_ppsqm=None,
        )

    n = len(comp_set.peers)
    notes: List[str] = []

    discount_pct = (comp_set.median_ppsqm - target.price_per_sqm) / comp_set.median_ppsqm * 100

    confidence = min(1.0, n / cfg.comparables.target_comparables)
    if comp_set.tier == "B":
        confidence *= 0.7
        notes.append("сравнение по всему ЖК без учёта числа комнат — мало точных аналогов")

    score = discount_pct * confidence

    if discount_pct > cfg.scoring.max_plausible_discount_pct:
        notes.append(
            f"скидка {discount_pct:.0f}% превышает правдоподобный порог "
            f"{cfg.scoring.max_plausible_discount_pct:.0f}% — возможна ошибка в данных, "
            f"обременение, доля в праве, судебный спор или иная скрытая причина"
        )
        label = "suspicious"
    elif score >= cfg.scoring.strong_undervalued_threshold:
        label = "strong_undervalued"
    elif score >= cfg.scoring.undervalued_threshold:
        label = "undervalued"
    elif score <= cfg.scoring.overvalued_threshold:
        label = "overvalued"
    else:
        label = "fair"

    if n < cfg.comparables.min_comparables * 2:
        notes.append(f"вердикт основан всего на {n} аналогах — уверенность ограничена")

    return Verdict(
        label=label,
        score=score,
        discount_pct=discount_pct,
        confidence=confidence,
        comparables_count=n,
        comparables_tier=comp_set.tier,
        median_comp_ppsqm=comp_set.median_ppsqm,
        notes=notes,
    )
