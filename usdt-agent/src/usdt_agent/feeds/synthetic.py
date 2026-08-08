"""A deterministic offline market — the agent's flight simulator.

Why this exists: an autonomous money-handling program must be testable without
touching a real venue, and its behaviour must be reproducible bug-for-bug. Given
a seed, this feed generates the identical tape every run.

The model is deliberately *unkind*, because a simulator that is generous is a
simulator that lies:

* correlated geometric Brownian motion for reference prices;
* per-venue basis that mean-reverts, so cross-venue spreads open and close;
* spreads that widen with volatility (they are widest exactly when you want to
  trade), plus occasional liquidity droughts;
* mean-reverting funding rates that sometimes go negative;
* rare, short dislocations — real arbitrage windows that a slow agent misses;
* stablecoin pools whose APY drifts and whose reward APY decays.

Most generated "opportunities" are smaller than the round-trip cost. That is the
point: a strategy that only prints money here is a strategy that only prints
money against a friendly simulator.
"""

from __future__ import annotations

import math
import random
import time

from ..models import FundingRate, Quote, YieldPool
from .base import Feed

REFERENCE_PRICES: dict[str, float] = {
    "BTC/USDT": 68_000.0,
    "ETH/USDT": 3_500.0,
    "SOL/USDT": 165.0,
    "BNB/USDT": 590.0,
    "XRP/USDT": 0.62,
    "USDC/USDT": 1.0,
    "DOGE/USDT": 0.14,
    "ADA/USDT": 0.45,
}

#: Cross pairs, priced *off* the USDT legs rather than as free processes — so a
#: triangular loop is only ever dislocated by the small noise term below, which
#: is what makes triangular arbitrage rare and thin here, as it is in reality.
CROSS_PAIRS: dict[str, tuple[str, str]] = {
    "ETH/BTC": ("ETH/USDT", "BTC/USDT"),
    "SOL/ETH": ("SOL/USDT", "ETH/USDT"),
    "BNB/BTC": ("BNB/USDT", "BTC/USDT"),
}

DEFAULT_VOL: dict[str, float] = {"USDC/USDT": 0.0006}
BASE_VOL = 0.02  # per step, at step_s == 60


class SyntheticFeed(Feed):
    """Self-contained market generator implementing the :class:`Feed` protocol."""

    name = "synthetic"

    def __init__(
        self,
        venues: tuple[str, ...] = ("binance", "bybit", "okx"),
        seed: int = 7,
        step_s: float = 60.0,
        volatility: float = 1.0,
        dislocation_prob: float = 0.06,
    ) -> None:
        self.venues = venues
        self.seed = seed
        self.step_s = step_s
        self.volatility = volatility
        self.dislocation_prob = dislocation_prob
        self.rng = random.Random(seed)
        self.step = 0
        self.clock = time.time()

        self.prices: dict[str, float] = dict(REFERENCE_PRICES)
        self.symbols: tuple[str, ...] = (*REFERENCE_PRICES, *CROSS_PAIRS)
        # Per-venue log-basis in bps: the reason cross-venue arbitrage exists.
        self.basis: dict[tuple[str, str], float] = {
            (v, s): self.rng.gauss(0, 3.0) for v in venues for s in self.symbols
        }
        # Deviation of each venue's listed cross from the ratio implied by its
        # two USDT legs — the only source of triangular edge in this market.
        self.cross_noise: dict[tuple[str, str], float] = {
            (v, s): self.rng.gauss(0, 2.0) for v in venues for s in CROSS_PAIRS
        }
        self.funding: dict[tuple[str, str], float] = {
            (v, s): self.rng.gauss(0.0002, 0.0001) for v in venues for s in REFERENCE_PRICES
        }
        self._pools = self._seed_pools()

    # -- generation ------------------------------------------------------
    def _seed_pools(self) -> list[YieldPool]:
        specs = [
            ("aave-v3", "Ethereum", "USDT", 0.048, 0.004, 820_000_000.0),
            ("aave-v3", "Arbitrum", "USDC", 0.052, 0.006, 310_000_000.0),
            ("compound-v3", "Ethereum", "USDC", 0.041, 0.009, 540_000_000.0),
            ("morpho-blue", "Ethereum", "USDC", 0.061, 0.011, 180_000_000.0),
            ("curve-dex", "Ethereum", "USDT-USDC-DAI", 0.019, 0.023, 240_000_000.0),
            ("fluid-lending", "Ethereum", "USDT", 0.058, 0.014, 95_000_000.0),
            ("spark", "Ethereum", "DAI", 0.055, 0.0, 1_100_000_000.0),
            ("venus", "BSC", "USDT", 0.067, 0.021, 62_000_000.0),
        ]
        return [
            YieldPool(
                protocol=p, chain=c, symbol=s, apy_base=b, apy_reward=r, tvl_usd=t,
                stablecoin=True, il_risk="no" if "-" not in s else "yes",
                pool_id=f"syn-{p}-{c}-{s}".lower(), ts=self.clock,
            )
            for p, c, s, b, r, t in specs
        ]

    def advance(self, steps: int = 1) -> None:
        """Move the simulated clock forward, evolving every process."""
        for _ in range(max(1, steps)):
            self.step += 1
            self.clock += self.step_s
            scale = math.sqrt(self.step_s / 60.0) * self.volatility

            for sym in self.prices:
                vol = DEFAULT_VOL.get(sym, BASE_VOL) * scale
                shock = self.rng.gauss(0.0, vol)
                if sym == "USDC/USDT":
                    # A peg: mean-reverting around 1.0, not a random walk.
                    self.prices[sym] += 0.35 * (1.0 - self.prices[sym]) + shock
                    self.prices[sym] = max(0.97, min(1.03, self.prices[sym]))
                else:
                    self.prices[sym] = max(1e-8, self.prices[sym] * math.exp(shock))

            for key in self.basis:
                # Ornstein-Uhlenbeck: dislocations close, they do not persist.
                self.basis[key] += -0.25 * self.basis[key] + self.rng.gauss(0.0, 1.6)
                if self.rng.random() < self.dislocation_prob / max(1, len(self.basis)) * 8:
                    self.basis[key] += self.rng.choice((-1, 1)) * self.rng.uniform(8.0, 45.0)

            for key in self.cross_noise:
                self.cross_noise[key] += -0.40 * self.cross_noise[key] + self.rng.gauss(0.0, 1.1)
                if self.rng.random() < 0.01:
                    self.cross_noise[key] += self.rng.choice((-1, 1)) * self.rng.uniform(6.0, 22.0)

            for key in self.funding:
                self.funding[key] += -0.15 * (self.funding[key] - 0.0002) + self.rng.gauss(0, 8e-5)

            self._pools = [
                YieldPool(
                    protocol=p.protocol, chain=p.chain, symbol=p.symbol,
                    apy_base=max(0.001, p.apy_base + self.rng.gauss(0, 0.0015)),
                    apy_reward=max(0.0, p.apy_reward * 0.999 + self.rng.gauss(0, 0.0008)),
                    tvl_usd=max(1e6, p.tvl_usd * math.exp(self.rng.gauss(0, 0.01))),
                    stablecoin=p.stablecoin, il_risk=p.il_risk, pool_id=p.pool_id, ts=self.clock,
                )
                for p in self._pools
            ]

    def _reference(self, symbol: str, venue: str) -> float | None:
        """Mid before the per-venue basis is applied.

        Cross pairs are *derived* from their two USDT legs so the triangle is
        arbitrage-free by construction, then nudged by a small mean-reverting
        noise term. Without that derivation the simulator would print permanent
        thousand-bp loops and every triangular backtest would be a fantasy.
        """
        if symbol in self.prices:
            return self.prices[symbol]
        legs = CROSS_PAIRS.get(symbol)
        if legs is None:
            return None
        a, b = self.prices.get(legs[0]), self.prices.get(legs[1])
        if not a or not b:
            return None
        return (a / b) * math.exp(self.cross_noise.get((venue, symbol), 0.0) * 1e-4)

    # -- Feed interface --------------------------------------------------
    def fetch(
        self, symbols: tuple[str, ...]
    ) -> tuple[list[Quote], list[FundingRate], list[YieldPool]]:
        self.advance()
        quotes: list[Quote] = []
        funding: list[FundingRate] = []
        known = set(self.symbols)
        universe = [s for s in symbols if s in known] or list(self.symbols)

        for sym in universe:
            for venue in self.venues:
                ref = self._reference(sym, venue)
                if ref is None or ref <= 0:
                    continue
                basis_bps = self.basis.get((venue, sym), 0.0)
                mid = ref * math.exp(basis_bps * 1e-4)
                # Spread: floor + volatility term + occasional liquidity drought.
                if sym == "USDC/USDT":
                    base_spread = 0.6
                elif sym in CROSS_PAIRS:
                    base_spread = 2.4  # crosses are always thinner than the majors
                else:
                    base_spread = 1.2
                spread_bps = base_spread + abs(self.rng.gauss(0, 1.0))
                if self.rng.random() < 0.02:
                    spread_bps *= self.rng.uniform(3.0, 9.0)
                half = mid * spread_bps * 1e-4 / 2.0
                depth_usdt = self.rng.uniform(20_000, 400_000)
                if sym in ("BTC/USDT", "ETH/USDT"):
                    depth_usdt *= 4
                elif sym in CROSS_PAIRS:
                    depth_usdt *= 0.35
                quotes.append(
                    Quote(
                        venue=venue, symbol=sym,
                        bid=mid - half, ask=mid + half,
                        bid_size=depth_usdt / max(1e-9, mid),
                        ask_size=depth_usdt / max(1e-9, mid),
                        ts=self.clock,
                    )
                )
                if sym in REFERENCE_PRICES and sym != "USDC/USDT":
                    funding.append(
                        FundingRate(
                            venue=venue, symbol=sym,
                            rate=self.funding.get((venue, sym), 0.0001),
                            interval_hours=8.0, mark_price=mid,
                            next_funding_ts=self.clock + (8 * 3600 - (self.step * self.step_s) % (8 * 3600)),
                            ts=self.clock,
                        )
                    )

        return quotes, funding, list(self._pools)

    def healthy(self) -> bool:
        return True
