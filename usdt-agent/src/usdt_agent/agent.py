"""The agent loop: perceive -> mark -> allocate -> act -> record.

One cycle, in order:

1. **Perceive** — pull a :class:`MarketSnapshot` from every feed concurrently.
2. **Mark** — revalue open positions, close the ones whose premise expired.
3. **Govern** — update equity, high-water and the kill switches.
4. **Scan** — ask every enabled strategy what it can see, costed.
5. **Allocate** — Thompson-sample a budget per strategy; the risk governor
   approves, shrinks or refuses each ticket.
6. **Act** — send the legs; unwind immediately if a hedge came back one-legged.
7. **Record** — append everything to the hash-chained ledger.

Two properties are load-bearing:

* **No strategy sizes itself.** Every notional passes :meth:`RiskGovernor.approve`.
* **Live capital requires proof.** In live mode a strategy gets nothing until its
  realized returns pass the statistical edge gate in :mod:`~usdt_agent.statistics`.
"""

from __future__ import annotations

import contextlib
import logging
import signal
import time
from dataclasses import dataclass, field
from typing import Any

from .allocator import BanditAllocator
from .config import AgentConfig
from .execution.base import Broker
from .feeds import CompositeFeed
from .ledger import Ledger
from .models import MarketSnapshot, Opportunity, Trade, TradeStatus
from .risk import PortfolioState, RiskGovernor
from .strategies import Strategy

log = logging.getLogger(__name__)


@dataclass(slots=True)
class CycleReport:
    """What happened in one iteration — the unit the dashboard renders."""

    cycle: int
    ts: float
    equity: float
    cash: float
    treasury: float
    deployed: float
    unrealized: float
    opportunities: int
    opened: int
    closed: int
    realized_pnl_cycle: float
    best: str = ""
    refusals: list[str] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)


class Agent:
    """Owns the money, the loop and the veto chain."""

    def __init__(
        self,
        cfg: AgentConfig,
        feed: CompositeFeed,
        broker: Broker,
        strategies: dict[str, Strategy],
        ledger: Ledger,
        data_source: str = "unknown",
    ) -> None:
        self.cfg = cfg
        self.feed = feed
        self.broker = broker
        self.strategies = strategies
        self.ledger = ledger
        self.data_source = data_source

        self.cash = cfg.starting_equity_usdt
        self.treasury = 0.0
        self.open_trades: list[Trade] = []
        self.cycle = 0
        self.last_mark_ts = time.time()
        self.stop_requested = False
        self.history: list[CycleReport] = []

        self.risk = RiskGovernor(cfg.risk, cfg.starting_equity_usdt)
        self.allocator = BanditAllocator(cfg.allocator, list(strategies), seed=cfg.seed)
        self.allocator.warm_start(
            {name: ledger.closed_returns(name) for name in strategies}
        )
        self.allocator.reassess(seed=cfg.seed)

        ledger.append(
            "run_start",
            {
                "mode": cfg.mode, "data_source": data_source, "broker": broker.name,
                "equity": cfg.starting_equity_usdt, "strategies": sorted(strategies),
            },
        )

    # ------------------------------------------------------------------
    # Equity
    # ------------------------------------------------------------------
    @property
    def deployed(self) -> float:
        return sum(t.notional for t in self.open_trades)

    @property
    def unrealized(self) -> float:
        return sum(t.accrued for t in self.open_trades)

    @property
    def equity(self) -> float:
        return self.cash + self.treasury + self.deployed + self.unrealized

    def portfolio_state(self) -> PortfolioState:
        return PortfolioState(equity=self.equity, open_trades=self.open_trades, treasury=self.treasury)

    # ------------------------------------------------------------------
    # Cycle
    # ------------------------------------------------------------------
    def run(self, max_cycles: int | None = None, on_cycle: Any = None) -> list[CycleReport]:
        """Run until stopped, halted, or ``max_cycles`` iterations have passed."""
        limit = self.cfg.max_cycles if max_cycles is None else max_cycles
        self._install_signal_handlers()

        while not self.stop_requested:
            if limit and self.cycle >= limit:
                break
            started = time.time()
            try:
                report = self.step()
            except KeyboardInterrupt:
                log.warning("interrupted by user")
                break
            except Exception as e:
                log.exception("cycle %d failed: %s", self.cycle, e)
                self.ledger.append("cycle_error", {"cycle": self.cycle, "error": str(e)})
                report = None

            if report is not None:
                self.history.append(report)
                if on_cycle is not None:
                    on_cycle(report, self)

            if self.risk.halted:
                log.error("agent halted: %s", self.risk.halt_reason)
                self.ledger.append("halt", {"reason": self.risk.halt_reason, "equity": self.equity})
                break

            if limit and self.cycle >= limit:
                break
            elapsed = time.time() - started
            time.sleep(max(0.0, self.cfg.interval_s - elapsed))

        self._shutdown()
        return self.history

    def step(self) -> CycleReport:
        """Execute exactly one cycle and return its report."""
        self.cycle += 1
        snapshot = self.feed.snapshot(self.cfg.symbols)
        now = snapshot.ts
        dt = max(0.0, now - self.last_mark_ts)
        self.last_mark_ts = now

        closed, realized = self._mark_and_close(snapshot, dt)
        self.risk.mark_equity(self.equity, now)

        opportunities = self._scan(snapshot)
        opened = self._deploy(opportunities, snapshot)

        self.ledger.record_equity(now, self.equity, self.deployed, self.treasury, len(self.open_trades))

        if self.cycle % 20 == 0:
            self.allocator.reassess(seed=self.cfg.seed)
            self.ledger.append("assessment", {
                name: {"proven": v.proven, "n": v.n, "p": round(v.p_value, 4),
                       "mean_bps": round(v.mean_bps, 3), "reason": v.reason}
                for name, v in self.allocator.verdicts.items()
            })

        report = CycleReport(
            cycle=self.cycle, ts=now, equity=self.equity, cash=self.cash,
            treasury=self.treasury, deployed=self.deployed, unrealized=self.unrealized,
            opportunities=len(opportunities), opened=opened, closed=closed,
            realized_pnl_cycle=realized,
            best=opportunities[0].label if opportunities else "",
            refusals=[f"{k}:{v}" for k, v in list(self.risk.refusals.items())[:3]],
            errors=list(snapshot.errors)[:3],
        )
        log.info(
            "cycle %d | equity %.2f | %d opps | +%d/-%d trades | cycle PnL %+.4f",
            report.cycle, report.equity, report.opportunities, report.opened,
            report.closed, report.realized_pnl_cycle,
        )
        return report

    # ------------------------------------------------------------------
    # Position management
    # ------------------------------------------------------------------
    def _mark_and_close(self, snapshot: MarketSnapshot, dt: float) -> tuple[int, float]:
        closed = 0
        realized_total = 0.0
        survivors: list[Trade] = []

        for trade in self.open_trades:
            strategy = self.strategies.get(trade.strategy)
            if strategy is None:
                survivors.append(trade)
                continue

            try:
                trade.accrued = strategy.mark(trade, snapshot, dt)
            except Exception as e:
                log.warning("mark failed for %s: %s", trade.id, e)

            signal_ = strategy.should_close(trade, snapshot)
            if not signal_:
                survivors.append(trade)
                continue

            realized_total += self._close_trade(trade, snapshot, strategy, signal_.reason)
            closed += 1

        self.open_trades = survivors
        return closed, realized_total

    def _close_trade(
        self, trade: Trade, snapshot: MarketSnapshot, strategy: Strategy, reason: str
    ) -> float:
        try:
            exit_orders = strategy.close_orders(trade, snapshot)
        except Exception as e:
            log.warning("close_orders failed for %s: %s", trade.id, e)
            exit_orders = ()

        if exit_orders:
            fills = self.broker.execute(exit_orders, snapshot)
            trade.exit_cost += self.broker.cost_of(fills)
            trade.fills.extend(fills)
            self.ledger.record_fills(trade.id, fills)

        trade.closed_ts = snapshot.ts
        trade.status = TradeStatus.CLOSED
        pnl = trade.realized_pnl

        # Return the capital and settle the PnL against cash.
        self.cash += trade.notional + trade.accrued - trade.exit_cost

        if pnl > 0 and self.cfg.treasury_sweep_fraction > 0:
            sweep = pnl * self.cfg.treasury_sweep_fraction
            self.cash -= sweep
            self.treasury += sweep
        if pnl < 0:
            self.risk.penalize(trade.strategy, snapshot.ts)

        self.allocator.observe(trade.strategy, trade.return_bps)
        self.ledger.record_trade(trade)
        log.debug("closed %s %s: %+.4f USDT (%s)", trade.strategy, trade.label, pnl, reason)
        return pnl

    # ------------------------------------------------------------------
    # Discovery and deployment
    # ------------------------------------------------------------------
    def _scan(self, snapshot: MarketSnapshot) -> list[Opportunity]:
        found: list[Opportunity] = []
        for name, strategy in self.strategies.items():
            try:
                found.extend(strategy.scan(snapshot))
            except Exception as e:
                log.warning("scan failed for %s: %s", name, e)
                self.ledger.append("scan_error", {"strategy": name, "error": str(e)})
        found.sort(key=lambda o: o.score, reverse=True)
        return found

    def _eligible(self, strategy_name: str) -> bool:
        """Live capital only goes to strategies that have *proven* an edge."""
        if not self.cfg.is_live:
            return True
        verdict = self.allocator.verdicts.get(strategy_name)
        return verdict is not None and verdict.proven

    def _deploy(self, opportunities: list[Opportunity], snapshot: MarketSnapshot) -> int:
        if not opportunities:
            return 0

        deployable = max(0.0, min(self.cash, self.cfg.risk.max_deployed_fraction * self.equity - self.deployed))
        if deployable < self.cfg.risk.min_ticket_usdt:
            return 0

        static = {name: self.cfg.strategy(name).weight for name in self.strategies}
        budgets = self.allocator.budget(deployable, static)
        data_age = max(0.0, time.time() - snapshot.ts)
        opened = 0

        for opp in opportunities:
            budget = budgets.get(opp.strategy, 0.0)
            if budget < self.cfg.risk.min_ticket_usdt:
                continue
            if not self._eligible(opp.strategy):
                continue

            decision = self.risk.approve(opp, budget, self.portfolio_state(), data_age, snapshot.ts)
            if not decision:
                log.debug("refused %s: %s", opp.label, decision.reason)
                continue

            trade = self._open_trade(opp, decision.notional, snapshot)
            if trade is None:
                continue

            budgets[opp.strategy] = budget - decision.notional
            opened += 1

        return opened

    def _open_trade(self, opp: Opportunity, notional: float, snapshot: MarketSnapshot) -> Trade | None:
        orders = opp.scaled_to(notional)
        if not orders:
            return None

        fills = self.broker.execute(orders, snapshot)
        trade = Trade(
            strategy=opp.strategy,
            label=opp.label,
            notional=notional,
            opened_ts=snapshot.ts,
            expected_edge_bps=opp.edge_bps,
            horizon_s=opp.horizon_s,
            fills=list(fills),
            meta={**opp.meta, "opportunity_id": opp.id, "confidence": opp.confidence},
        )
        trade.entry_cost = self.broker.cost_of(fills)
        self.ledger.record_fills(trade.id, fills)

        if not self.broker.all_ok(fills):
            # A partly-filled hedge is an unhedged position. Unwind at once.
            bad = next((f.reason for f in fills if not f.ok), "unknown")
            log.warning("partial fill on %s (%s) — unwinding", opp.label, bad)
            unwind = tuple(f.order.flipped() for f in fills if f.ok)
            if unwind:
                unwind_fills = self.broker.execute(unwind, snapshot)
                trade.exit_cost += self.broker.cost_of(unwind_fills)
                trade.fills.extend(unwind_fills)
                self.ledger.record_fills(trade.id, unwind_fills)
            trade.status = TradeStatus.CLOSED
            trade.closed_ts = snapshot.ts
            self.cash -= trade.entry_cost + trade.exit_cost
            self.allocator.observe(trade.strategy, trade.return_bps)
            self.ledger.record_trade(trade)
            return None

        self.cash -= notional + trade.entry_cost
        self.open_trades.append(trade)
        self.ledger.record_trade(trade)
        log.debug("opened %s %s for %.2f USDT (edge %.2f bps)",
                  opp.strategy, opp.label, notional, opp.edge_bps)
        return trade

    # ------------------------------------------------------------------
    # Shutdown
    # ------------------------------------------------------------------
    def _install_signal_handlers(self) -> None:
        def handler(signum: int, _frame: Any) -> None:
            log.warning("signal %s received — finishing the current cycle", signum)
            self.stop_requested = True

        for sig in (signal.SIGINT, signal.SIGTERM):
            # Not on the main thread (tests, embedded use): nothing to install.
            with contextlib.suppress(ValueError, OSError):
                signal.signal(sig, handler)

    def liquidate(self, snapshot: MarketSnapshot | None = None) -> float:
        """Close every open position. Used on shutdown and by ``panic``."""
        if not self.open_trades:
            return 0.0
        snap = snapshot or self.feed.snapshot(self.cfg.symbols)
        total = 0.0
        for trade in list(self.open_trades):
            strategy = self.strategies.get(trade.strategy)
            if strategy is None:
                continue
            total += self._close_trade(trade, snap, strategy, "liquidation")
        self.open_trades = []
        return total

    def _shutdown(self) -> None:
        self.allocator.reassess(seed=self.cfg.seed)
        self.ledger.append(
            "run_end",
            {
                "cycles": self.cycle, "equity": round(self.equity, 4),
                "treasury": round(self.treasury, 4),
                "open_trades": len(self.open_trades),
                "risk": self.risk.status(self.equity),
            },
        )
        self.ledger.set_state("last_equity", round(self.equity, 6))

    # ------------------------------------------------------------------
    def summary(self) -> dict[str, Any]:
        start = self.cfg.starting_equity_usdt
        pnl = self.equity - start
        return {
            "mode": self.cfg.mode,
            "data_source": self.data_source,
            "broker": self.broker.name,
            "cycles": self.cycle,
            "starting_equity": start,
            "equity": round(self.equity, 4),
            "cash": round(self.cash, 4),
            "treasury": round(self.treasury, 4),
            "deployed": round(self.deployed, 4),
            "unrealized": round(self.unrealized, 4),
            "pnl": round(pnl, 4),
            "return_pct": round(100.0 * pnl / start, 4) if start else 0.0,
            "open_trades": len(self.open_trades),
            "risk": self.risk.status(self.equity),
            "ledger": self.ledger.totals(),
            "strategies": self.ledger.strategy_stats(),
            "posteriors": self.allocator.snapshot(),
            "verdicts": {
                name: {"label": v.label, "proven": v.proven, "n": v.n,
                       "mean_bps": round(v.mean_bps, 3), "p_value": round(v.p_value, 4),
                       "reason": v.reason}
                for name, v in self.allocator.verdicts.items()
            },
        }
