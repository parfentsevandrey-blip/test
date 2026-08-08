"""Delta-neutral funding carry — the workhorse of "earning USDT" in crypto.

Mechanics: buy the asset on spot, short the same size of its perpetual swap.
The two legs cancel, so the position has (almost) no price exposure; what it
earns is the **funding rate** that longs pay shorts every 8 hours whenever the
perp trades above spot — which, in a market where most retail leverage is long,
is most of the time.

What it is not: free money. The real risks, all of which the sizing here
respects, are basis blow-outs while you hold, a funding flip to negative, and
the fact that the round trip costs four crossings. The strategy therefore only
opens when *one* funding payment already covers the whole round trip.
"""

from __future__ import annotations

import math

from ..models import BPS, SECONDS_PER_YEAR, Instrument, MarketSnapshot, Opportunity, Order, Side, Trade
from .base import CloseSignal, Strategy, age_of, capacity_from_quotes, round_trip_cost_bps


class FundingCarryStrategy(Strategy):
    name = "funding_carry"
    description = "Long spot + short perp; harvests positive funding, delta-neutral"

    @staticmethod
    def defaults() -> dict:
        return {
            "min_apr": 0.05,           # ignore anything under 5 % annualised
            "max_hold_intervals": 30,  # ~10 days: carry needs time to beat fees
            "participation": 0.12,
            "negative_streak_exit": 6,  # consecutive negative marks before bailing
            "min_edge_bps": 1.0,
        }

    def scan(self, snapshot: MarketSnapshot) -> list[Opportunity]:
        out: list[Opportunity] = []
        min_apr = float(self.params["min_apr"])
        max_hold = max(1, int(self.params["max_hold_intervals"]))

        for (venue, symbol), fr in snapshot.funding.items():
            if fr.rate <= 0:
                continue  # negative funding pays longs; that is the other trade
            spot = snapshot.quote(venue, symbol)
            if spot is None:
                continue
            if fr.apr < min_apr:
                continue

            capacity = capacity_from_quotes([spot], float(self.params["participation"]))
            if capacity <= 0:
                continue

            probe = min(capacity, self.cfg.risk.max_ticket_usdt)
            # Four crossings: spot in/out plus perp in/out.
            cost_bps = round_trip_cost_bps(self.exec_cfg, [spot, spot], probe)

            # Solve for the shortest hold that actually repays the round trip.
            # Holding longer earns more but leans harder on funding staying
            # positive, so take the minimum that clears the bar, not the maximum.
            per_interval_bps = fr.rate / BPS
            if per_interval_bps <= 0:
                continue
            needed = (cost_bps + self.min_edge_bps()) / per_interval_bps
            hold_n = max(1, math.ceil(needed))
            if hold_n > max_hold:
                continue

            # Hold until `hold_n` funding payments have been collected.
            first = fr.seconds_to_funding(snapshot.ts)
            horizon_s = first + (hold_n - 1) * fr.interval_hours * 3600.0
            if horizon_s <= 0:
                continue

            gross_bps = per_interval_bps * hold_n
            edge_bps = gross_bps - cost_bps
            if edge_bps < self.min_edge_bps():
                continue

            # Confidence falls as we rely on funding staying positive for longer,
            # and as the perp's implied APR gets implausibly large.
            confidence = 0.85 / math.sqrt(hold_n)
            if fr.apr > 0.60:
                confidence *= 0.5

            legs = (
                Order(venue, symbol, Side.BUY, probe, Instrument.SPOT),
                Order(venue, symbol, Side.SELL, probe, Instrument.PERP,
                      meta={"hedge_of": symbol}),
            )
            out.append(
                self._opportunity(
                    label=f"{symbol}@{venue} funding {fr.apr:+.1%} APR",
                    edge_bps=edge_bps,
                    capacity=capacity,
                    horizon_s=horizon_s,
                    legs=legs,
                    confidence=confidence,
                    meta={
                        "venue": venue, "symbol": symbol,
                        "rate": fr.rate, "apr": fr.apr,
                        "interval_hours": fr.interval_hours,
                        "hold_intervals": hold_n,
                        "gross_bps": gross_bps, "cost_bps": cost_bps,
                        # Snapshot the spot/perp basis at entry, so later marks
                        # book the *change* in basis rather than its level.
                        "entry_basis": (
                            (fr.mark_price - spot.mid) / spot.mid
                            if fr.mark_price > 0 and spot.mid > 0 else 0.0
                        ),
                        "neg_streak": 0,
                    },
                )
            )

        out.sort(key=lambda o: o.score, reverse=True)
        return out

    def mark(self, trade: Trade, snapshot: MarketSnapshot, dt: float) -> float:
        """Accrue funding pro rata; the two price legs cancel by construction."""
        venue = trade.meta.get("venue", "")
        symbol = trade.meta.get("symbol", "")
        fr = snapshot.funding.get((venue, symbol))
        rate = fr.rate if fr is not None else float(trade.meta.get("rate", 0.0))
        interval_s = float(trade.meta.get("interval_hours", 8.0)) * 3600.0
        if interval_s <= 0:
            return trade.accrued

        carry = trade.notional * rate * (dt / interval_s)

        # Basis risk: spot and perp do not move in perfect lockstep. Model the
        # residual as the drift of the perp's mark against the entry basis.
        residual = 0.0
        entry_basis = float(trade.meta.get("entry_basis", 0.0))
        if fr is not None and fr.mark_price > 0:
            spot = snapshot.quote(venue, symbol)
            if spot is not None and spot.mid > 0:
                basis_now = (fr.mark_price - spot.mid) / spot.mid
                # A short perp loses when the perp richens relative to spot.
                residual = -trade.notional * (basis_now - entry_basis)

        # `residual` is a level, not an increment: book only what changed since
        # the last mark, otherwise the same basis move is charged every cycle.
        delta_residual = residual - float(trade.meta.get("last_residual", 0.0))
        trade.meta["last_residual"] = residual
        return trade.accrued + carry + delta_residual

    def should_close(self, trade: Trade, snapshot: MarketSnapshot) -> CloseSignal:
        venue, symbol = trade.meta.get("venue", ""), trade.meta.get("symbol", "")
        fr = snapshot.funding.get((venue, symbol))

        # A single negative print is noise — funding oscillates around zero all
        # the time. Bailing on the first one guarantees paying four crossings
        # for nothing, so require a run of them before conceding the premise.
        if fr is not None:
            streak = int(trade.meta.get("neg_streak", 0))
            streak = streak + 1 if fr.rate < 0 else 0
            trade.meta["neg_streak"] = streak
            if streak >= int(self.params["negative_streak_exit"]):
                return CloseSignal(True, f"funding negative {streak} marks running")
        if trade.horizon_s > 0 and age_of(trade, snapshot) >= trade.horizon_s:
            return CloseSignal(True, "collected the planned funding payments")
        # Emergency exit: the carry can no longer repay the exit cost.
        if trade.accrued < -0.02 * trade.notional:
            return CloseSignal(True, "basis moved against the hedge")
        return CloseSignal(False)

    def annualized_apr(self, opp: Opportunity) -> float:
        horizon = max(1e-9, opp.horizon_s)
        return opp.edge_bps * BPS * (SECONDS_PER_YEAR / horizon)
