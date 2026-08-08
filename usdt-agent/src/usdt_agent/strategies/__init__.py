"""Strategy registry.

Adding a strategy is three steps: subclass :class:`~.base.Strategy`, register it
in :data:`REGISTRY`, and give it an entry in ``[strategies]`` in the TOML config.
The agent picks it up with no other changes — the allocator will start it on a
small exploration share and grow it only if it earns.
"""

from __future__ import annotations

from ..config import AgentConfig
from .base import CloseSignal, Strategy, leg_cost_bps, round_trip_cost_bps
from .cross_venue import CrossVenueArbStrategy
from .funding_carry import FundingCarryStrategy
from .grid import GridStrategy
from .stable_yield import StableYieldStrategy
from .triangular import TriangularArbStrategy

REGISTRY: dict[str, type[Strategy]] = {
    FundingCarryStrategy.name: FundingCarryStrategy,
    CrossVenueArbStrategy.name: CrossVenueArbStrategy,
    TriangularArbStrategy.name: TriangularArbStrategy,
    StableYieldStrategy.name: StableYieldStrategy,
    GridStrategy.name: GridStrategy,
}

__all__ = [
    "REGISTRY",
    "CloseSignal",
    "CrossVenueArbStrategy",
    "FundingCarryStrategy",
    "GridStrategy",
    "StableYieldStrategy",
    "Strategy",
    "TriangularArbStrategy",
    "build_strategies",
    "leg_cost_bps",
    "round_trip_cost_bps",
]


def build_strategies(cfg: AgentConfig) -> dict[str, Strategy]:
    """Instantiate every enabled strategy named in the config."""
    out: dict[str, Strategy] = {}
    for name, scfg in cfg.strategies.items():
        if not scfg.enabled:
            continue
        cls = REGISTRY.get(name)
        if cls is None:
            continue
        out[name] = cls(cfg, scfg.params)
    return out
