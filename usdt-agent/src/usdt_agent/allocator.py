"""Capital allocation as a multi-armed bandit.

The agent does not know in advance which strategy actually works on the venues,
symbols and fee tier *you* have. So it treats each strategy as an arm and uses
**Thompson sampling** over a Normal posterior on per-trade return (in bps):

    posterior_mean = (prior_mean/prior_var + sum(w_i * r_i)/obs_var)
                     / (1/prior_var + sum(w_i)/obs_var)

Observations are exponentially weighted by recency (``memory_halflife_trades``)
so a strategy that stopped working stops getting capital — crypto edges decay,
and an allocator with perfect memory is an allocator that keeps funding a dead
arbitrage for months.

Each cycle every arm draws one sample from its posterior; positive draws are
normalised into weights, with an exploration floor so a temporarily unlucky arm
never starves to zero.
"""

from __future__ import annotations

import logging
import math
import random
from dataclasses import dataclass, field

from .config import AllocatorConfig
from .statistics import EdgeVerdict, assess

log = logging.getLogger(__name__)


@dataclass(slots=True)
class ArmPosterior:
    """Recency-weighted Normal posterior over one strategy's mean return (bps)."""

    name: str
    prior_mean: float
    prior_var: float
    obs_var: float
    halflife: float
    weighted_sum: float = 0.0
    weight_total: float = 0.0
    n: int = 0
    returns: list[float] = field(default_factory=list)

    def observe(self, return_bps: float) -> None:
        """Add one realized trade return, decaying everything seen before it."""
        decay = 0.5 ** (1.0 / max(1e-9, self.halflife))
        self.weighted_sum = self.weighted_sum * decay + return_bps
        self.weight_total = self.weight_total * decay + 1.0
        self.n += 1
        self.returns.append(return_bps)
        if len(self.returns) > 1000:
            del self.returns[: len(self.returns) - 1000]

    @property
    def precision(self) -> float:
        return 1.0 / self.prior_var + self.weight_total / self.obs_var

    @property
    def mean(self) -> float:
        return (self.prior_mean / self.prior_var + self.weighted_sum / self.obs_var) / self.precision

    @property
    def var(self) -> float:
        return 1.0 / self.precision

    def sample(self, rng: random.Random) -> float:
        return rng.gauss(self.mean, math.sqrt(self.var))


class BanditAllocator:
    """Thompson-sampling allocator over the enabled strategies."""

    def __init__(self, cfg: AllocatorConfig, strategies: list[str], seed: int = 0) -> None:
        self.cfg = cfg
        self.rng = random.Random(seed)
        self.arms: dict[str, ArmPosterior] = {
            name: ArmPosterior(
                name=name,
                prior_mean=cfg.prior_mean_bps,
                prior_var=max(1e-9, cfg.prior_stdev_bps**2),
                obs_var=max(1e-9, cfg.observation_stdev_bps**2),
                halflife=cfg.memory_halflife_trades,
            )
            for name in strategies
        }
        self.verdicts: dict[str, EdgeVerdict] = {}

    # -- learning --------------------------------------------------------
    def observe(self, strategy: str, return_bps: float) -> None:
        arm = self.arms.get(strategy)
        if arm is None:
            return
        arm.observe(return_bps)

    def warm_start(self, history: dict[str, list[float]]) -> None:
        """Replay persisted per-trade returns so a restart is not a lobotomy."""
        for name, returns in history.items():
            for r in returns:
                self.observe(name, r)

    # -- allocation ------------------------------------------------------
    def weights(self, static_weights: dict[str, float] | None = None) -> dict[str, float]:
        """One Thompson draw per arm, normalised to shares that sum to 1."""
        if not self.arms:
            return {}
        static = static_weights or {}
        draws = {
            name: arm.sample(self.rng) * max(0.0, static.get(name, 1.0))
            for name, arm in self.arms.items()
        }
        positive = {k: v for k, v in draws.items() if v > 0}

        n = len(self.arms)
        floor = min(self.cfg.exploration_floor, 1.0 / n)
        if not positive:
            # Nothing looks profitable: spread thin and keep exploring.
            return {k: 1.0 / n for k in self.arms}

        total = sum(positive.values())
        raw = {k: (positive.get(k, 0.0) / total) for k in self.arms}
        # Mix in the exploration floor and renormalise.
        mixed = {k: floor + (1.0 - floor * n) * v for k, v in raw.items()}
        s = sum(mixed.values())
        return {k: v / s for k, v in mixed.items()}

    def budget(
        self, deployable_usdt: float, static_weights: dict[str, float] | None = None
    ) -> dict[str, float]:
        """Turn shares into per-strategy USDT budgets."""
        w = self.weights(static_weights)
        return {k: deployable_usdt * v for k, v in w.items()}

    # -- promotion gate --------------------------------------------------
    def reassess(self, seed: int = 0) -> dict[str, EdgeVerdict]:
        """Re-run the statistical edge test for every arm.

        ``n_trials`` is the number of arms, which is exactly the multiple-testing
        correction we owe ourselves for running five strategies and cheering for
        whichever one is ahead.
        """
        n_trials = max(1, len(self.arms))
        self.verdicts = {
            name: assess(
                name,
                arm.returns,
                min_trades=self.cfg.min_trades_for_promotion,
                p_threshold=self.cfg.promotion_p_value,
                n_trials=n_trials,
                seed=seed,
            )
            for name, arm in self.arms.items()
        }
        return self.verdicts

    def proven(self) -> set[str]:
        return {name for name, v in self.verdicts.items() if v.proven}

    def snapshot(self) -> dict[str, dict[str, float]]:
        return {
            name: {
                "n": float(arm.n),
                "posterior_mean_bps": round(arm.mean, 3),
                "posterior_stdev_bps": round(math.sqrt(arm.var), 3),
                "effective_samples": round(arm.weight_total, 2),
            }
            for name, arm in self.arms.items()
        }
