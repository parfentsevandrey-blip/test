"""Cross-venue spot arbitrage: buy where it is cheap, sell where it is dear.

For each symbol the scanner finds the venue with the best ask and the venue with
the best bid. If ``best_bid > best_ask`` after all four crossing costs, the two
legs are sent together and the position is flat immediately — the "hold" is
measured in seconds, so there is no market risk, only execution risk.

The honest caveats, all encoded below rather than in a footnote:

* the edge is quoted against **mid-to-mid**, and the broker charges the spreads;
* capacity is the thinner of the two books, not the fatter;
* this ignores withdrawal/transfer time — it assumes inventory is *already*
  pre-positioned on both venues, which is how this trade is actually run.
"""

from __future__ import annotations

from ..models import BPS, Instrument, MarketSnapshot, Opportunity, Order, Side, Trade
from .base import CloseSignal, Strategy, capacity_from_quotes, leg_cost_bps


class CrossVenueArbStrategy(Strategy):
    name = "cross_venue"
    description = "Simultaneous buy/sell of the same pair across two venues"

    @staticmethod
    def defaults() -> dict:
        return {"min_edge_bps": 3.0, "participation": 0.10, "hold_s": 45.0}

    def scan(self, snapshot: MarketSnapshot) -> list[Opportunity]:
        out: list[Opportunity] = []
        symbols = {s for (_, s) in snapshot.quotes}

        for symbol in sorted(symbols):
            quotes = [q for (v, s), q in snapshot.quotes.items() if s == symbol]
            if len(quotes) < 2:
                continue

            cheap = min(quotes, key=lambda q: q.ask)  # buy here
            rich = max(quotes, key=lambda q: q.bid)  # sell here
            if cheap.venue == rich.venue:
                continue

            ref = (cheap.mid + rich.mid) / 2.0
            if ref <= 0:
                continue

            # Gross edge measured mid-to-mid; the spreads are charged as cost.
            gross_bps = (rich.mid - cheap.mid) / ref / BPS
            if gross_bps <= 0:
                continue

            capacity = capacity_from_quotes([cheap, rich], float(self.params["participation"]))
            if capacity <= 0:
                continue
            probe = min(capacity, self.cfg.risk.max_ticket_usdt)

            # Two crossings now + two to unwind the inventory later.
            cost_bps = 2.0 * (
                leg_cost_bps(self.exec_cfg, cheap, probe) + leg_cost_bps(self.exec_cfg, rich, probe)
            )
            edge_bps = gross_bps - cost_bps
            if edge_bps < self.min_edge_bps():
                continue

            hold_s = float(self.params["hold_s"])
            # Wide spreads mean the quoted mid is less trustworthy.
            confidence = 0.9 / (1.0 + (cheap.spread_bps + rich.spread_bps) / 20.0)

            legs = (
                Order(cheap.venue, symbol, Side.BUY, probe, Instrument.SPOT),
                Order(rich.venue, symbol, Side.SELL, probe, Instrument.SPOT),
            )
            out.append(
                self._opportunity(
                    label=f"{symbol} {cheap.venue}->{rich.venue} {gross_bps:.1f}bps",
                    edge_bps=edge_bps,
                    capacity=capacity,
                    horizon_s=hold_s,
                    legs=legs,
                    confidence=confidence,
                    meta={
                        "symbol": symbol,
                        "buy_venue": cheap.venue, "sell_venue": rich.venue,
                        "entry_buy_mid": cheap.mid, "entry_sell_mid": rich.mid,
                        "gross_bps": gross_bps, "cost_bps": cost_bps,
                        "booked": False,
                    },
                )
            )

        out.sort(key=lambda o: o.score, reverse=True)
        return out

    def mark(self, trade: Trade, snapshot: MarketSnapshot, dt: float) -> float:
        """The spread is captured at entry, once, and never re-marked.

        Both legs go on together, so the position is flat the moment it is
        filled: there is nothing left to mark to market.
        """
        if trade.meta.get("booked"):
            return trade.accrued
        buy_mid = float(trade.meta.get("entry_buy_mid", 0.0))
        sell_mid = float(trade.meta.get("entry_sell_mid", 0.0))
        if buy_mid <= 0 or sell_mid <= 0:
            return trade.accrued
        trade.meta["booked"] = True
        ref = (buy_mid + sell_mid) / 2.0
        return trade.notional * (sell_mid - buy_mid) / ref

    def should_close(self, trade: Trade, snapshot: MarketSnapshot) -> CloseSignal:
        # Flat already; unwind as soon as the spread capture is booked.
        if trade.meta.get("booked"):
            return CloseSignal(True, "spread captured, unwinding inventory")
        return CloseSignal(False)
