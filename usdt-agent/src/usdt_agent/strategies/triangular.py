"""Triangular arbitrage inside a single venue.

Walks USDT -> BASE -> BRIDGE -> USDT (e.g. USDT -> BTC -> ETH -> USDT) and keeps
the loop only if the product of the three mid-rates exceeds 1 by more than the
three crossings cost.

Everything happens on one venue, so there is no transfer risk and no settlement
lag — but there are three legs, which means three chances for a partial fill to
leave the loop open. The agent's ``all_ok`` check unwinds immediately when that
happens, and the strategy prices in three full crossings up front.
"""

from __future__ import annotations

from itertools import permutations

from ..models import BPS, Instrument, MarketSnapshot, Opportunity, Order, Quote, Side, Trade
from .base import CloseSignal, Strategy, capacity_from_quotes, leg_cost_bps


class TriangularArbStrategy(Strategy):
    name = "triangular"
    description = "USDT -> A -> B -> USDT loops within one venue"

    @staticmethod
    def defaults() -> dict:
        return {"min_edge_bps": 2.0, "participation": 0.08, "max_loops": 24}

    def _bridge_quote(self, snapshot: MarketSnapshot, venue: str, a: str, b: str) -> tuple[Quote | None, bool]:
        """Find the A/B cross on ``venue``. Returns ``(quote, inverted)``."""
        direct = snapshot.quote(venue, f"{a}/{b}")
        if direct is not None:
            return direct, False
        inverse = snapshot.quote(venue, f"{b}/{a}")
        if inverse is not None:
            return inverse, True
        return None, False

    def scan(self, snapshot: MarketSnapshot) -> list[Opportunity]:
        out: list[Opportunity] = []
        max_loops = int(self.params["max_loops"])

        venues = sorted({v for (v, _) in snapshot.quotes})
        for venue in venues:
            usdt_pairs = [
                s for (v, s) in snapshot.quotes if v == venue and s.endswith("/USDT")
            ]
            bases = sorted({p.split("/")[0] for p in usdt_pairs})
            if len(bases) < 2:
                continue

            for a, b in permutations(bases, 2):
                if len(out) >= max_loops:
                    break
                qa = snapshot.quote(venue, f"{a}/USDT")
                qb = snapshot.quote(venue, f"{b}/USDT")
                cross, inverted = self._bridge_quote(snapshot, venue, a, b)
                if qa is None or qb is None:
                    continue

                # Synthetic cross rate implied by the two USDT legs.
                implied = qa.mid / qb.mid if qb.mid > 0 else 0.0
                if implied <= 0:
                    continue

                if cross is not None:
                    actual = (1.0 / cross.mid) if inverted else cross.mid
                    legs_quotes: list[Quote] = [qa, cross, qb]
                else:
                    # No direct cross listed: the loop cannot be executed.
                    continue
                if actual <= 0:
                    continue

                # Loop gain: sell A for B at the market cross, versus synthetic.
                gross_bps = (actual / implied - 1.0) / BPS
                direction_buy_a = gross_bps > 0
                gross_bps = abs(gross_bps)
                if gross_bps <= 0:
                    continue

                capacity = capacity_from_quotes(legs_quotes, float(self.params["participation"]))
                if capacity <= 0:
                    continue
                probe = min(capacity, self.cfg.risk.max_ticket_usdt)
                cost_bps = sum(leg_cost_bps(self.exec_cfg, q, probe) for q in legs_quotes)
                edge_bps = gross_bps - cost_bps
                if edge_bps < self.min_edge_bps():
                    continue

                cross_symbol = f"{b}/{a}" if inverted else f"{a}/{b}"
                first, third = (Side.BUY, Side.SELL) if direction_buy_a else (Side.SELL, Side.BUY)
                legs = (
                    Order(venue, f"{a}/USDT", first, probe, Instrument.SPOT),
                    Order(venue, cross_symbol, first.opposite, probe, Instrument.SPOT),
                    Order(venue, f"{b}/USDT", third, probe, Instrument.SPOT),
                )
                out.append(
                    self._opportunity(
                        label=f"{venue} USDT->{a}->{b}->USDT {gross_bps:.1f}bps",
                        edge_bps=edge_bps,
                        capacity=capacity,
                        horizon_s=30.0,
                        legs=legs,
                        confidence=0.7 / (1.0 + sum(q.spread_bps for q in legs_quotes) / 30.0),
                        meta={
                            "venue": venue, "loop": f"USDT/{a}/{b}",
                            "gross_bps": gross_bps, "cost_bps": cost_bps, "booked": False,
                        },
                    )
                )

        out.sort(key=lambda o: o.score, reverse=True)
        return out[: int(self.params["max_loops"])]

    def mark(self, trade: Trade, snapshot: MarketSnapshot, dt: float) -> float:
        """The loop closes on itself, so the gain is booked once at entry."""
        if trade.meta.get("booked"):
            return trade.accrued
        trade.meta["booked"] = True
        return trade.notional * float(trade.meta.get("gross_bps", 0.0)) * BPS

    def should_close(self, trade: Trade, snapshot: MarketSnapshot) -> CloseSignal:
        if trade.meta.get("booked"):
            return CloseSignal(True, "loop completed")
        return CloseSignal(False)

    def close_orders(self, trade: Trade, snapshot: MarketSnapshot) -> tuple[Order, ...]:
        """A completed loop ends in USDT — there is no inventory to unwind."""
        return ()
