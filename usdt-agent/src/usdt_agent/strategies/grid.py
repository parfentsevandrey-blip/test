"""Mean-reversion grid on tight stable-to-stable pairs (USDC/USDT and friends).

Buys when the pair trades a configured distance below its recent mean and sells
back at the mean. On a pegged pair this is a high-hit-rate trade — right up
until the peg is the thing that breaks, which is exactly why this strategy
carries a hard stop and the smallest default weight of the five.

This is the only strategy here that takes genuine directional risk, and the only
one whose :meth:`mark` can go negative on price alone. That is intentional: the
bandit allocator needs at least one arm that can actually lose so that its
learning has something to learn.
"""

from __future__ import annotations

import statistics
from collections import defaultdict, deque

from ..models import BPS, Instrument, MarketSnapshot, Opportunity, Order, Side, Trade
from .base import CloseSignal, Strategy, age_of, capacity_from_quotes, leg_cost_bps


class GridStrategy(Strategy):
    name = "grid"
    description = "Mean-reversion band trading on pegged pairs (USDC/USDT)"

    @staticmethod
    def defaults() -> dict:
        return {
            "symbols": ["USDC/USDT"],
            "band_bps": 25.0,       # how far below the mean to buy
            "target_bps": 5.0,      # take profit distance from entry
            "stop_bps": 60.0,       # hard stop: the peg is not holding
            "window": 40,           # observations in the rolling mean
            "participation": 0.05,
            "max_hold_s": 6 * 3600.0,
            "min_edge_bps": 1.0,
        }

    def __init__(self, cfg, params=None) -> None:  # type: ignore[no-untyped-def]
        super().__init__(cfg, params)
        window = int(self.params["window"])
        self._history: dict[tuple[str, str], deque[float]] = defaultdict(
            lambda: deque(maxlen=window)
        )

    def observe(self, snapshot: MarketSnapshot) -> None:
        for (venue, symbol), q in snapshot.quotes.items():
            if symbol in set(self.params["symbols"]) and q.mid > 0:
                self._history[(venue, symbol)].append(q.mid)

    def scan(self, snapshot: MarketSnapshot) -> list[Opportunity]:
        self.observe(snapshot)
        out: list[Opportunity] = []
        band = float(self.params["band_bps"])
        target = float(self.params["target_bps"])
        window = int(self.params["window"])

        for (venue, symbol), hist in self._history.items():
            if len(hist) < max(8, window // 4):
                continue  # not enough history to know what "the mean" is
            q = snapshot.quote(venue, symbol)
            if q is None or q.mid <= 0:
                continue

            mean = statistics.fmean(hist)
            if mean <= 0:
                continue
            deviation_bps = (q.mid - mean) / mean / BPS
            if deviation_bps > -band:
                continue  # only buy the dip, never chase the rip

            capacity = capacity_from_quotes([q], float(self.params["participation"]))
            if capacity <= 0:
                continue
            probe = min(capacity, self.cfg.risk.max_ticket_usdt)

            gross_bps = min(abs(deviation_bps), abs(deviation_bps) + target)
            cost_bps = 2.0 * leg_cost_bps(self.exec_cfg, q, probe)
            edge_bps = gross_bps - cost_bps
            if edge_bps < self.min_edge_bps():
                continue

            # Deeper dislocations revert less reliably, not more.
            confidence = 0.55 * min(1.0, band / max(1e-9, abs(deviation_bps)))

            legs = (Order(venue, symbol, Side.BUY, probe, Instrument.SPOT),)
            out.append(
                self._opportunity(
                    label=f"{symbol}@{venue} {deviation_bps:.1f}bps below mean",
                    edge_bps=edge_bps,
                    capacity=capacity,
                    horizon_s=float(self.params["max_hold_s"]),
                    legs=legs,
                    confidence=confidence,
                    meta={
                        "venue": venue, "symbol": symbol,
                        "entry_mid": q.mid, "mean": mean,
                        "deviation_bps": deviation_bps,
                        "target_mid": mean * (1.0 + target * BPS),
                        "gross_bps": gross_bps, "cost_bps": cost_bps,
                    },
                )
            )

        out.sort(key=lambda o: o.score, reverse=True)
        return out

    def mark(self, trade: Trade, snapshot: MarketSnapshot, dt: float) -> float:
        """Genuine mark-to-market: this position can and does lose money."""
        q = snapshot.quote(trade.meta.get("venue", ""), trade.meta.get("symbol", ""))
        entry = float(trade.meta.get("entry_mid", 0.0))
        if q is None or q.mid <= 0 or entry <= 0:
            return trade.accrued
        return trade.notional * (q.mid - entry) / entry

    def should_close(self, trade: Trade, snapshot: MarketSnapshot) -> CloseSignal:
        q = snapshot.quote(trade.meta.get("venue", ""), trade.meta.get("symbol", ""))
        entry = float(trade.meta.get("entry_mid", 0.0))
        if q is None or entry <= 0:
            return CloseSignal(True, "lost the quote for an open position")

        move_bps = (q.mid - entry) / entry / BPS
        if move_bps >= float(self.params["target_bps"]):
            return CloseSignal(True, f"target hit ({move_bps:+.1f} bps)")
        if move_bps <= -float(self.params["stop_bps"]):
            return CloseSignal(True, f"stop hit ({move_bps:+.1f} bps)")
        if age_of(trade, snapshot) >= float(self.params["max_hold_s"]):
            return CloseSignal(True, "max hold elapsed")
        return CloseSignal(False)
