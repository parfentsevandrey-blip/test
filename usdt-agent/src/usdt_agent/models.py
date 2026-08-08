"""Core value objects shared by feeds, strategies, execution and the ledger.

Everything here is plain stdlib: frozen dataclasses for market data (so a
snapshot can be hashed / cached / replayed safely) and mutable dataclasses for
things that genuinely evolve (orders, trades).

Money convention
----------------
All notionals, costs and PnL are denominated in **USDT** and stored as floats.
Rates are stored as *fractions* (0.0001 == 1 bp == 0.01 %). Anything named
``*_bps`` is in basis points, anything named ``*_apr`` is an annualised
fraction (0.12 == 12 % a year).
"""

from __future__ import annotations

import math
import time
import uuid
from dataclasses import dataclass, field
from enum import StrEnum
from typing import Any

SECONDS_PER_YEAR = 365.0 * 24 * 3600
BPS = 1e-4


# --------------------------------------------------------------------------
# Market data
# --------------------------------------------------------------------------


@dataclass(frozen=True, slots=True)
class Quote:
    """Top-of-book for one symbol on one venue."""

    venue: str
    symbol: str  # canonical "BASE/QUOTE", e.g. "BTC/USDT"
    bid: float
    ask: float
    bid_size: float  # in base units
    ask_size: float  # in base units
    ts: float = field(default_factory=time.time)

    @property
    def mid(self) -> float:
        return (self.bid + self.ask) / 2.0

    @property
    def spread_bps(self) -> float:
        m = self.mid
        return 0.0 if m <= 0 else (self.ask - self.bid) / m / BPS

    @property
    def base(self) -> str:
        return self.symbol.split("/")[0]

    @property
    def quote(self) -> str:
        return self.symbol.split("/")[1]

    def depth_usdt(self, side: str) -> float:
        """Notional resting on one side of the book (top level only)."""
        return self.ask_size * self.ask if side == "buy" else self.bid_size * self.bid


@dataclass(frozen=True, slots=True)
class FundingRate:
    """A perpetual-swap funding rate observation.

    ``rate`` is the fraction paid *per interval* by longs to shorts. A positive
    rate means shorts get paid, which is what a delta-neutral carry trade
    (long spot / short perp) harvests.
    """

    venue: str
    symbol: str
    rate: float
    interval_hours: float
    mark_price: float
    next_funding_ts: float = 0.0
    ts: float = field(default_factory=time.time)

    @property
    def apr(self) -> float:
        periods = SECONDS_PER_YEAR / (self.interval_hours * 3600.0)
        return self.rate * periods

    def seconds_to_funding(self, now: float | None = None) -> float:
        if self.next_funding_ts <= 0:
            return self.interval_hours * 3600.0
        return max(0.0, self.next_funding_ts - (now if now is not None else time.time()))


@dataclass(frozen=True, slots=True)
class YieldPool:
    """A stablecoin yield venue (lending market or LP) as reported by an oracle."""

    protocol: str
    chain: str
    symbol: str
    apy_base: float
    apy_reward: float
    tvl_usd: float
    stablecoin: bool = True
    il_risk: str = "no"
    pool_id: str = ""
    ts: float = field(default_factory=time.time)

    @property
    def apy(self) -> float:
        return self.apy_base + self.apy_reward

    @property
    def risk_score(self) -> float:
        """0 (pristine) .. 1 (do not touch). Heuristic, deliberately conservative.

        Reward-heavy, low-TVL, IL-exposed or implausibly-high-APY pools are
        penalised because that is exactly where stablecoin yield goes to die.
        """
        score = 0.0
        if self.tvl_usd < 1_000_000:
            score += 0.45
        elif self.tvl_usd < 10_000_000:
            score += 0.25
        elif self.tvl_usd < 100_000_000:
            score += 0.10
        if self.apy > 0.0:
            reward_share = self.apy_reward / self.apy
            score += 0.30 * max(0.0, min(1.0, reward_share))
        if self.il_risk != "no":
            score += 0.20
        if not self.stablecoin:
            score += 0.35
        # An "impossible" APY is a risk signal, not an opportunity signal.
        if self.apy > 0.30:
            score += min(0.35, (self.apy - 0.30) * 0.8)
        return max(0.0, min(1.0, score))

    @property
    def risk_adjusted_apy(self) -> float:
        return self.apy * (1.0 - self.risk_score)


@dataclass(frozen=True, slots=True)
class MarketSnapshot:
    """Everything the agent knows about the world at one instant."""

    ts: float
    quotes: dict[tuple[str, str], Quote] = field(default_factory=dict)
    funding: dict[tuple[str, str], FundingRate] = field(default_factory=dict)
    pools: tuple[YieldPool, ...] = ()
    errors: tuple[str, ...] = ()

    def quote(self, venue: str, symbol: str) -> Quote | None:
        return self.quotes.get((venue, symbol))

    def venues_for(self, symbol: str) -> list[str]:
        return sorted(v for (v, s) in self.quotes if s == symbol)

    def symbols_on(self, venue: str) -> list[str]:
        return sorted(s for (v, s) in self.quotes if v == venue)

    @property
    def age_s(self) -> float:
        return max(0.0, time.time() - self.ts)

    @property
    def is_empty(self) -> bool:
        return not self.quotes and not self.funding and not self.pools


# --------------------------------------------------------------------------
# Orders / fills
# --------------------------------------------------------------------------


class Side(StrEnum):
    BUY = "buy"
    SELL = "sell"

    @property
    def sign(self) -> int:
        return 1 if self is Side.BUY else -1

    @property
    def opposite(self) -> Side:
        return Side.SELL if self is Side.BUY else Side.BUY


class Instrument(StrEnum):
    SPOT = "spot"
    PERP = "perp"
    POOL = "pool"  # a DeFi deposit, not an exchange order


@dataclass(frozen=True, slots=True)
class Order:
    """An instruction to a broker. ``notional`` is always in USDT."""

    venue: str
    symbol: str
    side: Side
    notional: float
    instrument: Instrument = Instrument.SPOT
    post_only: bool = False
    reduce_only: bool = False
    meta: dict[str, Any] = field(default_factory=dict)

    def flipped(self) -> Order:
        return Order(
            venue=self.venue,
            symbol=self.symbol,
            side=self.side.opposite,
            notional=self.notional,
            instrument=self.instrument,
            post_only=self.post_only,
            reduce_only=True,
            meta=dict(self.meta),
        )


@dataclass(frozen=True, slots=True)
class Fill:
    """The result of an order actually touching a book."""

    order: Order
    price: float
    qty: float  # base units
    fee_usdt: float
    slippage_usdt: float
    ts: float = field(default_factory=time.time)
    ok: bool = True
    reason: str = ""

    @property
    def notional(self) -> float:
        return self.price * self.qty

    @property
    def cost_usdt(self) -> float:
        """Everything the fill cost us versus a frictionless mid-price fill."""
        return self.fee_usdt + self.slippage_usdt


# --------------------------------------------------------------------------
# Opportunities
# --------------------------------------------------------------------------


@dataclass(slots=True)
class Opportunity:
    """A costed, executable idea produced by a strategy.

    ``edge_bps`` is the expected **net** profit per unit of deployed notional
    for one full cycle (entry + hold + exit), already after fees and modelled
    slippage. ``expected_apr`` annualises it over ``horizon_s``.
    """

    strategy: str
    label: str
    edge_bps: float
    capacity_usdt: float
    horizon_s: float
    legs: tuple[Order, ...] = ()
    confidence: float = 0.5
    venues: tuple[str, ...] = ()
    meta: dict[str, Any] = field(default_factory=dict)
    ts: float = field(default_factory=time.time)
    id: str = field(default_factory=lambda: uuid.uuid4().hex[:12])

    @property
    def expected_apr(self) -> float:
        if self.horizon_s <= 0:
            return 0.0
        cycles = SECONDS_PER_YEAR / self.horizon_s
        return self.edge_bps * BPS * cycles

    @property
    def score(self) -> float:
        """Ranking score: annualised edge discounted by confidence.

        ``log1p`` on the APR keeps a single implausible 4000 % pool from
        crowding out a reliable 9 % one.
        """
        if self.edge_bps <= 0:
            return 0.0
        return math.log1p(max(0.0, self.expected_apr)) * self.confidence

    def scaled_to(self, notional: float) -> tuple[Order, ...]:
        """Re-scale the leg notionals so the *largest* leg equals ``notional``.

        Leg ratios are preserved, which is what keeps a hedge delta-neutral.
        """
        if not self.legs:
            return ()
        biggest = max(o.notional for o in self.legs)
        if biggest <= 0:
            return ()
        k = notional / biggest
        return tuple(
            Order(
                venue=o.venue,
                symbol=o.symbol,
                side=o.side,
                notional=o.notional * k,
                instrument=o.instrument,
                post_only=o.post_only,
                reduce_only=o.reduce_only,
                meta=dict(o.meta),
            )
            for o in self.legs
        )


# --------------------------------------------------------------------------
# Trades
# --------------------------------------------------------------------------


class TradeStatus(StrEnum):
    OPEN = "open"
    CLOSED = "closed"
    REJECTED = "rejected"


@dataclass(slots=True)
class Trade:
    """A position the agent actually holds (or held)."""

    strategy: str
    label: str
    notional: float
    opened_ts: float
    entry_cost: float = 0.0
    exit_cost: float = 0.0
    accrued: float = 0.0
    closed_ts: float | None = None
    status: TradeStatus = TradeStatus.OPEN
    expected_edge_bps: float = 0.0
    horizon_s: float = 0.0
    fills: list[Fill] = field(default_factory=list)
    meta: dict[str, Any] = field(default_factory=dict)
    id: str = field(default_factory=lambda: uuid.uuid4().hex[:12])

    @property
    def realized_pnl(self) -> float:
        return self.accrued - self.entry_cost - self.exit_cost

    @property
    def return_bps(self) -> float:
        return 0.0 if self.notional <= 0 else self.realized_pnl / self.notional / BPS

    @property
    def age_s(self) -> float:
        end = self.closed_ts if self.closed_ts is not None else time.time()
        return max(0.0, end - self.opened_ts)

    @property
    def is_open(self) -> bool:
        return self.status is TradeStatus.OPEN

    def venue_exposure(self) -> dict[str, float]:
        """Gross notional held per venue — what the risk governor caps."""
        out: dict[str, float] = {}
        for f in self.fills:
            if not f.ok:
                continue
            out[f.order.venue] = out.get(f.order.venue, 0.0) + abs(f.notional)
        return out
