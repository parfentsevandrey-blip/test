"""Configuration for market filters, comparable-grouping rules and scoring
thresholds. Loaded from a YAML file (see config.example.yaml) with sane
defaults so the tool runs out of the box.
"""

from __future__ import annotations

from dataclasses import dataclass, field, fields
from pathlib import Path
from typing import Any, Optional

import yaml


@dataclass
class MarketConfig:
    city: str = "Москва"
    min_built_year: int = 2019
    deal_type: str = "sale"
    accommodation_type: str = "flat"


@dataclass
class ComparablesConfig:
    area_tolerance_pct: float = 20.0     # Tier A peers must be within +/- this % of the target's area
    min_comparables: int = 4             # minimum peers required to trust a verdict
    target_comparables: int = 10         # peer count at which confidence saturates to 1.0
    floor_bucket: bool = True            # separate ground/top floor from middle floors when possible
    finish_type_strict: bool = False     # if true, prefer peers with the same finish category

    def __post_init__(self):
        # A verdict needs at least one peer to compute a median against; silently
        # clamp instead of letting a user-editable 0 crash the scoring pass.
        self.min_comparables = max(1, self.min_comparables)


@dataclass
class ScoringConfig:
    strong_undervalued_threshold: float = 15.0   # confidence-weighted discount %, >= this -> "strong_undervalued"
    undervalued_threshold: float = 8.0           # >= this -> "undervalued"
    overvalued_threshold: float = -8.0           # <= this -> "overvalued"
    max_plausible_discount_pct: float = 45.0     # beyond this, flag as "suspicious" instead of a genuine deal
    fresh_listing_days: int = 3                  # listings newer than this get a "hot" tag


@dataclass
class OutputConfig:
    top_n: int = 50
    include_suspicious: bool = True


@dataclass
class Config:
    market: MarketConfig = field(default_factory=MarketConfig)
    comparables: ComparablesConfig = field(default_factory=ComparablesConfig)
    scoring: ScoringConfig = field(default_factory=ScoringConfig)
    output: OutputConfig = field(default_factory=OutputConfig)


def _merge_dataclass(cls, data: Optional[dict]):
    if not data:
        return cls()
    known = {f.name for f in fields(cls)}
    kwargs = {k: v for k, v in data.items() if k in known}
    return cls(**kwargs)


def load_config(path: Optional[str] = None) -> Config:
    if not path:
        return Config()

    text = Path(path).read_text(encoding="utf-8")
    data: dict[str, Any] = yaml.safe_load(text) or {}

    return Config(
        market=_merge_dataclass(MarketConfig, data.get("market")),
        comparables=_merge_dataclass(ComparablesConfig, data.get("comparables")),
        scoring=_merge_dataclass(ScoringConfig, data.get("scoring")),
        output=_merge_dataclass(OutputConfig, data.get("output")),
    )
