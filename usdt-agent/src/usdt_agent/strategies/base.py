"""Strategy contract and the shared cost arithmetic.

Accounting model (one rule, applied everywhere)
-----------------------------------------------
    realized_pnl = accrued - entry_cost - exit_cost

``entry_cost``/``exit_cost`` come from the broker and are measured **against the
mid price**: fees + half-spread + market impact + adverse selection. Therefore a
flat mid-to-mid round trip books exactly zero ``accrued``, and each strategy only
has to answer one question in :meth:`Strategy.mark`: *how much is this position
worth, at mid, relative to where it went on?*

That split is what keeps the numbers honest — there is nowhere for a strategy to
hide a cost, because it never gets to compute its own costs.
"""

from __future__ import annotations

import abc
import logging
from dataclasses import dataclass

from ..config import AgentConfig, ExecutionConfig
from ..models import BPS, MarketSnapshot, Opportunity, Order, Quote, Trade

log = logging.getLogger(__name__)


def leg_cost_bps(exec_cfg: ExecutionConfig, quote: Quote | None, notional: float = 0.0) -> float:
    """Expected all-in cost of crossing once, in bps of notional.

    Mirrors :class:`~usdt_agent.execution.paper.PaperBroker` so that a strategy's
    *expected* edge and its *realized* edge are computed on the same basis.
    """
    fee = exec_cfg.taker_fee_bps
    if quote is None:
        return fee + exec_cfg.fixed_slippage_bps * 3 + exec_cfg.adverse_selection_bps
    spread = quote.spread_bps
    cost = fee + spread / 2.0 + exec_cfg.adverse_selection_bps
    cost += (exec_cfg.latency_ms / 1000.0) * 0.35
    if notional > 0:
        depth = max(1.0, quote.depth_usdt("buy"))
        cost += exec_cfg.impact_coefficient * spread * (notional / depth) ** 0.5
    return cost


def round_trip_cost_bps(
    exec_cfg: ExecutionConfig, quotes: list[Quote | None], notional: float = 0.0
) -> float:
    """Cost of opening *and* closing every leg."""
    return 2.0 * sum(leg_cost_bps(exec_cfg, q, notional) for q in quotes)


def capacity_from_quotes(quotes: list[Quote | None], participation: float = 0.15) -> float:
    """How much notional the thinnest leg can absorb without moving the market."""
    depths = [min(q.depth_usdt("buy"), q.depth_usdt("sell")) for q in quotes if q is not None]
    if not depths:
        return 0.0
    return max(0.0, min(depths) * participation)


@dataclass(slots=True)
class CloseSignal:
    close: bool
    reason: str = ""

    def __bool__(self) -> bool:
        return self.close


class Strategy(abc.ABC):
    """One way of earning USDT. Proposes; never sizes, never executes."""

    name: str = "strategy"
    #: Human-readable one-liner shown by ``usdt-agent strategies``.
    description: str = ""

    def __init__(self, cfg: AgentConfig, params: dict | None = None) -> None:
        self.cfg = cfg
        self.exec_cfg = cfg.execution
        self.params = {**self.defaults(), **(params or {})}

    @staticmethod
    def defaults() -> dict:
        return {}

    # -- discovery -------------------------------------------------------
    @abc.abstractmethod
    def scan(self, snapshot: MarketSnapshot) -> list[Opportunity]:
        """Return every costed opportunity visible in this snapshot."""

    # -- position lifecycle ----------------------------------------------
    @abc.abstractmethod
    def mark(self, trade: Trade, snapshot: MarketSnapshot, dt: float) -> float:
        """Return the position's **total** accrued value in USDT, at mid.

        ``dt`` is the time since the previous mark, for carry-style strategies.
        """

    def should_close(self, trade: Trade, snapshot: MarketSnapshot) -> CloseSignal:
        """Default: close once the intended horizon has elapsed."""
        if trade.horizon_s > 0 and age_of(trade, snapshot) >= trade.horizon_s:
            return CloseSignal(True, "horizon reached")
        return CloseSignal(False)

    def close_orders(self, trade: Trade, snapshot: MarketSnapshot) -> tuple[Order, ...]:
        """Default: send the inverse of every leg that actually filled."""
        return tuple(f.order.flipped() for f in trade.fills if f.ok)

    # -- helpers ---------------------------------------------------------
    def min_edge_bps(self) -> float:
        return float(self.params.get("min_edge_bps", self.cfg.risk.min_edge_bps))

    def _opportunity(
        self,
        label: str,
        edge_bps: float,
        capacity: float,
        horizon_s: float,
        legs: tuple[Order, ...],
        confidence: float,
        meta: dict,
    ) -> Opportunity:
        return Opportunity(
            strategy=self.name,
            label=label,
            edge_bps=edge_bps,
            capacity_usdt=capacity,
            horizon_s=horizon_s,
            legs=legs,
            confidence=max(0.01, min(1.0, confidence)),
            venues=tuple(sorted({o.venue for o in legs})),
            meta=meta,
        )


def age_of(trade: Trade, snapshot: MarketSnapshot) -> float:
    """Position age measured on the *market* clock, not the wall clock.

    ``Trade.age_s`` uses ``time.time()``, which is right in production and wrong
    in a backtest where a cycle covers hours of simulated time. Every holding
    decision goes through here so both modes behave identically.
    """
    return max(0.0, snapshot.ts - trade.opened_ts)


def mid_or_none(snapshot: MarketSnapshot, venue: str, symbol: str) -> float | None:
    q = snapshot.quote(venue, symbol)
    return q.mid if q is not None and q.mid > 0 else None


def pct_to_bps(x: float) -> float:
    return x / BPS
