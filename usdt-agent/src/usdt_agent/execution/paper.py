"""Paper broker with a pessimistic cost model.

Backtests lie in three predictable ways, and each gets an explicit term here:

1. **Fees are forgotten.** Charged per leg, taker by default.
2. **Fills are assumed at the touch.** Real orders walk the book, so slippage
   grows with ``notional / visible_depth`` (square-root impact).
3. **Adverse selection is ignored.** The counterparty who fills you at a great
   price usually knows something; a flat ``adverse_selection_bps`` tax models it.

On top of that: random rejections, and a latency penalty that lets the price
drift against you between decision and fill. If a strategy is still profitable
after all of this, it has a chance of being profitable for real.
"""

from __future__ import annotations

import logging
import math
import random

from ..config import ExecutionConfig
from ..models import BPS, Fill, Instrument, MarketSnapshot, Order, Side
from .base import Broker

log = logging.getLogger(__name__)


class PaperBroker(Broker):
    """Simulated execution against the snapshot's top of book."""

    name = "paper"
    is_live = False

    def __init__(self, cfg: ExecutionConfig, seed: int = 0) -> None:
        self.cfg = cfg
        self.rng = random.Random(seed)
        self.executed = 0
        self.rejected = 0

    # -- cost model ------------------------------------------------------
    def slippage_bps(self, order: Order, depth_usdt: float, spread_bps: float) -> float:
        """Cost of crossing, in bps of notional, versus the mid."""
        c = self.cfg
        if c.slippage_model == "none":
            return 0.0
        if c.slippage_model == "fixed":
            return c.fixed_slippage_bps

        # Half the spread is paid just for crossing.
        cost = spread_bps / 2.0
        # Square-root market impact beyond the visible top level.
        if depth_usdt > 0:
            ratio = order.notional / depth_usdt
            cost += c.impact_coefficient * spread_bps * math.sqrt(max(0.0, ratio))
        else:
            cost += c.fixed_slippage_bps * 3
        # Price drift during the round trip to the venue.
        cost += (c.latency_ms / 1000.0) * 0.35
        return cost + c.adverse_selection_bps

    def fee_bps(self, order: Order) -> float:
        return self.cfg.maker_fee_bps if order.post_only else self.cfg.taker_fee_bps

    # -- execution -------------------------------------------------------
    def execute(self, orders: tuple[Order, ...], snapshot: MarketSnapshot) -> list[Fill]:
        fills: list[Fill] = []
        for order in orders:
            fills.append(self._execute_one(order, snapshot))
        return fills

    def _execute_one(self, order: Order, snapshot: MarketSnapshot) -> Fill:
        if order.notional <= 0:
            return Fill(order, 0.0, 0.0, 0.0, 0.0, ok=False, reason="zero notional")

        if order.instrument is Instrument.POOL:
            # A deposit is not a book trade: no spread, just the gas/entry fee.
            fee = order.notional * self.fee_bps(order) * BPS * 0.2
            self.executed += 1
            return Fill(order, 1.0, order.notional, fee, 0.0, ts=snapshot.ts)

        quote = snapshot.quote(order.venue, order.symbol)
        if quote is None:
            self.rejected += 1
            return Fill(order, 0.0, 0.0, 0.0, 0.0, ok=False, reason=f"no quote {order.venue}:{order.symbol}")

        if self.rng.random() < self.cfg.reject_probability:
            self.rejected += 1
            return Fill(order, 0.0, 0.0, 0.0, 0.0, ok=False, reason="venue rejected the order")

        mid = quote.mid
        if mid <= 0:
            self.rejected += 1
            return Fill(order, 0.0, 0.0, 0.0, 0.0, ok=False, reason="degenerate quote")

        depth = quote.depth_usdt(order.side.value)
        slip_bps = self.slippage_bps(order, depth, quote.spread_bps)
        direction = 1.0 if order.side is Side.BUY else -1.0
        price = mid * (1.0 + direction * slip_bps * BPS)
        if price <= 0:
            self.rejected += 1
            return Fill(order, 0.0, 0.0, 0.0, 0.0, ok=False, reason="non-positive fill price")

        qty = order.notional / price
        fee = order.notional * self.fee_bps(order) * BPS
        slippage = order.notional * slip_bps * BPS

        self.executed += 1
        return Fill(order, price, qty, fee, slippage, ts=snapshot.ts)

    def stats(self) -> dict[str, float]:
        total = self.executed + self.rejected
        return {
            "executed": float(self.executed),
            "rejected": float(self.rejected),
            "reject_rate": (self.rejected / total) if total else 0.0,
        }
