"""The honesty layer: is the observed edge distinguishable from luck?

Any bot can show a rising equity curve for a week. This module answers the only
question that matters before real money is committed — *would this result be
surprising if the strategy's true edge were zero?* — and the agent uses the
answer as a gate, not as decoration.

Implemented with the stdlib only:

* :func:`bootstrap_p_value` — stationary-bootstrap one-sided test on the mean.
* :func:`sharpe` / :func:`deflated_sharpe` — Sharpe corrected for the fact that
  we tried several strategies and picked the best (multiple-testing haircut,
  after Bailey & López de Prado).
* :func:`max_drawdown`, :func:`summarize` — descriptive stats for reporting.
"""

from __future__ import annotations

import math
import random
from dataclasses import dataclass
from statistics import fmean, pstdev

# --------------------------------------------------------------------------
# Normal distribution helpers (no scipy)
# --------------------------------------------------------------------------


def norm_cdf(x: float) -> float:
    return 0.5 * (1.0 + math.erf(x / math.sqrt(2.0)))


def norm_ppf(p: float) -> float:
    """Inverse normal CDF (Acklam's rational approximation, ~1e-9 accurate)."""
    if not 0.0 < p < 1.0:
        raise ValueError("p must be in (0, 1)")
    a = (-3.969683028665376e01, 2.209460984245205e02, -2.759285104469687e02,
         1.383577518672690e02, -3.066479806614716e01, 2.506628277459239e00)
    b = (-5.447609879822406e01, 1.615858368580409e02, -1.556989798598866e02,
         6.680131188771972e01, -1.328068155288572e01)
    c = (-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e00,
         -2.549732539343734e00, 4.374664141464968e00, 2.938163982698783e00)
    d = (7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e00,
         3.754408661907416e00)
    plow, phigh = 0.02425, 1 - 0.02425
    if p < plow:
        q = math.sqrt(-2 * math.log(p))
        return (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / (
            (((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1
        )
    if p > phigh:
        q = math.sqrt(-2 * math.log(1 - p))
        return -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / (
            (((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1
        )
    q = p - 0.5
    r = q * q
    return (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q / (
        ((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1
    )


# --------------------------------------------------------------------------
# Descriptive
# --------------------------------------------------------------------------


def mean(xs: list[float]) -> float:
    return fmean(xs) if xs else 0.0


def stdev(xs: list[float]) -> float:
    return pstdev(xs) if len(xs) > 1 else 0.0


def sharpe(returns: list[float], periods_per_year: float = 365.0) -> float:
    """Annualised Sharpe of a per-trade (or per-period) return series."""
    if len(returns) < 2:
        return 0.0
    sd = stdev(returns)
    if sd <= 0:
        return 0.0
    return mean(returns) / sd * math.sqrt(periods_per_year)


def skewness(xs: list[float]) -> float:
    n = len(xs)
    if n < 3:
        return 0.0
    m, sd = mean(xs), stdev(xs)
    if sd <= 0:
        return 0.0
    return sum(((x - m) / sd) ** 3 for x in xs) / n


def kurtosis(xs: list[float]) -> float:
    """Non-excess kurtosis (normal == 3)."""
    n = len(xs)
    if n < 4:
        return 3.0
    m, sd = mean(xs), stdev(xs)
    if sd <= 0:
        return 3.0
    return sum(((x - m) / sd) ** 4 for x in xs) / n


def max_drawdown(equity: list[float]) -> float:
    """Largest peak-to-trough decline as a fraction of the peak."""
    peak, worst = float("-inf"), 0.0
    for v in equity:
        peak = max(peak, v)
        if peak > 0:
            worst = max(worst, (peak - v) / peak)
    return worst


# --------------------------------------------------------------------------
# Inference
# --------------------------------------------------------------------------


def bootstrap_p_value(
    returns: list[float], n_resamples: int = 2000, block: int = 4, seed: int = 0
) -> float:
    """One-sided p-value for H0: mean return <= 0, via a moving-block bootstrap.

    Blocks (rather than single draws) preserve short-range autocorrelation —
    arbitrage fills cluster, and pretending they are i.i.d. flatters the result.
    Returns 1.0 for a series too short to say anything about, which the caller
    correctly reads as "not proven".
    """
    n = len(returns)
    if n < 8:
        return 1.0
    observed = mean(returns)
    if observed <= 0:
        return 1.0

    # Centre the sample so the bootstrap world genuinely has zero mean.
    centred = [r - observed for r in returns]
    rng = random.Random(seed)
    block = max(1, min(block, n))
    n_blocks = math.ceil(n / block)

    hits = 0
    for _ in range(n_resamples):
        total = 0.0
        count = 0
        for _ in range(n_blocks):
            start = rng.randrange(n)
            for k in range(block):
                total += centred[(start + k) % n]
                count += 1
        if count and total / count >= observed:
            hits += 1
    return (hits + 1) / (n_resamples + 1)


def deflated_sharpe(
    returns: list[float], n_trials: int = 1, periods_per_year: float = 365.0
) -> float:
    """Probability the true Sharpe is positive, after a multiple-testing haircut.

    Searching N strategies and reporting the winner inflates Sharpe even when
    every strategy is worthless, so the *expected maximum* Sharpe under the null
    across N trials is subtracted before the significance test. All arithmetic
    is done in per-period units; ``periods_per_year`` only affects the reported
    Sharpe elsewhere, not this probability.
    """
    n = len(returns)
    if n < 8:
        return 0.0
    sd = stdev(returns)
    if sd <= 0:
        return 0.0
    sr_p = mean(returns) / sd  # per-period Sharpe
    if sr_p <= 0:
        return 0.0

    # E[max of N null Sharpe estimates], in units of their standard error
    # (which is ~1/sqrt(n-1) for a per-period Sharpe under the null).
    trials = max(1, n_trials)
    euler = 0.5772156649015329
    if trials > 1:
        e_max_z = (1 - euler) * norm_ppf(1 - 1.0 / trials) + euler * norm_ppf(
            1 - 1.0 / (trials * math.e)
        )
    else:
        e_max_z = 0.0

    g, k = skewness(returns), kurtosis(returns)
    denom = math.sqrt(max(1e-12, 1 - g * sr_p + (k - 1) / 4 * sr_p**2))
    z = (sr_p * math.sqrt(n - 1) - e_max_z) / denom
    return max(0.0, min(1.0, norm_cdf(z)))


@dataclass(slots=True)
class EdgeVerdict:
    """The gate's answer for one strategy."""

    strategy: str
    n: int
    mean_bps: float
    stdev_bps: float
    sharpe: float
    p_value: float
    deflated_sharpe: float
    proven: bool
    reason: str

    @property
    def label(self) -> str:
        if self.proven:
            return "proven"
        return "insufficient" if self.n < 8 else "unproven"


def assess(
    strategy: str,
    returns: list[float],
    *,
    min_trades: int = 30,
    p_threshold: float = 0.05,
    n_trials: int = 1,
    seed: int = 0,
) -> EdgeVerdict:
    """Decide whether a strategy has *demonstrated* an edge (not merely shown one)."""
    n = len(returns)
    m, sd = mean(returns), stdev(returns)
    if n < min_trades:
        return EdgeVerdict(
            strategy, n, m, sd, sharpe(returns), 1.0, 0.0, False,
            f"only {n}/{min_trades} closed trades",
        )
    p = bootstrap_p_value(returns, seed=seed)
    dsr = deflated_sharpe(returns, n_trials=n_trials)
    proven = p < p_threshold and m > 0 and dsr > 0.90
    if proven:
        reason = f"mean {m:+.2f} bps, p={p:.3f}, DSR={dsr:.2f}"
    elif m <= 0:
        reason = f"mean return is {m:+.2f} bps"
    elif p >= p_threshold:
        reason = f"p={p:.3f} >= {p_threshold}"
    else:
        reason = f"deflated Sharpe {dsr:.2f} <= 0.90 (looks like selection bias)"
    return EdgeVerdict(strategy, n, m, sd, sharpe(returns), p, dsr, proven, reason)


def summarize(returns: list[float], equity: list[float] | None = None) -> dict[str, float]:
    """Descriptive block used by the reporter."""
    out = {
        "n": float(len(returns)),
        "mean_bps": mean(returns),
        "stdev_bps": stdev(returns),
        "sharpe": sharpe(returns),
        "win_rate": (sum(1 for r in returns if r > 0) / len(returns)) if returns else 0.0,
        "best_bps": max(returns) if returns else 0.0,
        "worst_bps": min(returns) if returns else 0.0,
    }
    if equity:
        out["max_drawdown"] = max_drawdown(equity)
    return out
