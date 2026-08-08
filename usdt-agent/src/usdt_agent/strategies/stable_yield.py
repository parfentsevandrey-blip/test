"""Stablecoin yield allocation — the boring strategy that usually wins.

Parks idle USDT in lending markets and stable-only pools, ranked by
**risk-adjusted** APY rather than headline APY. The ranking penalty (see
:attr:`~usdt_agent.models.YieldPool.risk_score`) is what stops the agent from
chain-chasing a 40 % emissions farm that will be 4 % next week and zero after
the exploit.

Note on realism: this module *decides and accounts*; it does not sign
transactions. Executing an on-chain deposit needs a wallet, a signer and a
per-protocol adapter, which is a deliberate boundary — the agent will happily
tell you where the yield is, and paper-trade the allocation, without ever
holding your private key. The ``pool`` instrument type marks these legs so the
live broker refuses them rather than mis-routing them to an exchange.
"""

from __future__ import annotations

from ..models import BPS, SECONDS_PER_YEAR, Instrument, MarketSnapshot, Opportunity, Order, Side, Trade
from .base import CloseSignal, Strategy, age_of


class StableYieldStrategy(Strategy):
    name = "stable_yield"
    description = "Allocates idle USDT to the best risk-adjusted stablecoin yield"

    @staticmethod
    def defaults() -> dict:
        return {
            "min_apy": 0.04,
            "max_risk": 0.50,
            # Lending is a *slow* trade: at 6 % APY a 3-day hold grosses ~5 bps,
            # which does not repay the round trip. Two weeks is the realistic
            # minimum for the position to be worth entering at all.
            "hold_days": 14.0,
            "max_positions": 3,
            "entry_cost_bps": 4.0,   # gas + protocol entry, round trip
            "min_edge_bps": 1.0,
        }

    def scan(self, snapshot: MarketSnapshot) -> list[Opportunity]:
        out: list[Opportunity] = []
        min_apy = float(self.params["min_apy"])
        max_risk = float(self.params["max_risk"])
        hold_s = float(self.params["hold_days"]) * 86_400.0
        cost_bps = float(self.params["entry_cost_bps"])

        pools = [p for p in snapshot.pools if p.apy >= min_apy and p.risk_score <= max_risk]
        pools.sort(key=lambda p: p.risk_adjusted_apy, reverse=True)

        for pool in pools[: int(self.params["max_positions"])]:
            gross_bps = pool.risk_adjusted_apy * (hold_s / SECONDS_PER_YEAR) / BPS
            edge_bps = gross_bps - cost_bps
            if edge_bps < self.min_edge_bps():
                continue

            # Never be a meaningful share of a pool: exit liquidity matters more
            # than entry yield.
            capacity = max(0.0, pool.tvl_usd * 0.0005)
            if capacity <= 0:
                continue
            probe = min(capacity, self.cfg.risk.max_ticket_usdt)

            confidence = (1.0 - pool.risk_score) * 0.9
            legs = (
                Order(
                    venue=f"{pool.protocol}@{pool.chain}",
                    symbol=pool.symbol,
                    side=Side.BUY,
                    notional=probe,
                    instrument=Instrument.POOL,
                    meta={"pool_id": pool.pool_id},
                ),
            )
            out.append(
                self._opportunity(
                    label=f"{pool.protocol}/{pool.chain} {pool.symbol} {pool.apy:.2%} APY",
                    edge_bps=edge_bps,
                    capacity=capacity,
                    horizon_s=hold_s,
                    legs=legs,
                    confidence=confidence,
                    meta={
                        "protocol": pool.protocol, "chain": pool.chain, "symbol": pool.symbol,
                        "apy": pool.apy, "apy_base": pool.apy_base, "apy_reward": pool.apy_reward,
                        "risk_score": pool.risk_score, "pool_id": pool.pool_id,
                        "tvl_usd": pool.tvl_usd,
                    },
                )
            )

        out.sort(key=lambda o: o.score, reverse=True)
        return out

    def mark(self, trade: Trade, snapshot: MarketSnapshot, dt: float) -> float:
        """Accrue interest pro rata, tracking the pool's *current* APY."""
        pool_id = trade.meta.get("pool_id", "")
        apy = float(trade.meta.get("apy", 0.0))
        for p in snapshot.pools:
            if p.pool_id and p.pool_id == pool_id:
                apy = p.apy  # yields float; use the live number, not the entry one
                break
        return trade.accrued + trade.notional * apy * (dt / SECONDS_PER_YEAR)

    def should_close(self, trade: Trade, snapshot: MarketSnapshot) -> CloseSignal:
        pool_id = trade.meta.get("pool_id", "")
        entry_apy = float(trade.meta.get("apy", 0.0))
        for p in snapshot.pools:
            if p.pool_id and p.pool_id == pool_id:
                if p.apy < entry_apy * 0.4:
                    return CloseSignal(True, f"APY collapsed {entry_apy:.2%} -> {p.apy:.2%}")
                if p.risk_score > float(self.params["max_risk"]) + 0.15:
                    return CloseSignal(True, "pool risk score deteriorated")
                break
        if trade.horizon_s > 0 and age_of(trade, snapshot) >= trade.horizon_s:
            return CloseSignal(True, "hold period complete")
        return CloseSignal(False)
