"""The risk governor — the component with veto power over everything else.

Design rule: strategies propose, the governor disposes. No strategy, allocator
or execution path can size a position; they can only *ask*, and every ask goes
through :meth:`RiskGovernor.approve`, which returns either an approved notional
or a refusal with a reason. Refusals are logged to the ledger so a flat equity
curve can always be explained.

The kill-switches are deliberately blunt:

* **daily loss limit** — stop trading for the rest of the UTC day;
* **max drawdown** — stop trading, full stop, until a human resets it;
* **stale data** — no new positions while the world view is old.
"""

from __future__ import annotations

import logging
import time
from dataclasses import dataclass, field
from datetime import UTC, datetime

from .config import RiskConfig
from .models import Opportunity, Trade

log = logging.getLogger(__name__)


def utc_day(ts: float) -> str:
    return datetime.fromtimestamp(ts, tz=UTC).strftime("%Y-%m-%d")


@dataclass(slots=True)
class Decision:
    """Outcome of a risk check."""

    approved: bool
    notional: float = 0.0
    reason: str = ""

    def __bool__(self) -> bool:
        return self.approved


@dataclass(slots=True)
class PortfolioState:
    """Everything the governor needs to know about the book right now."""

    equity: float
    open_trades: list[Trade] = field(default_factory=list)
    treasury: float = 0.0

    @property
    def deployed(self) -> float:
        return sum(t.notional for t in self.open_trades)

    @property
    def free(self) -> float:
        return max(0.0, self.equity - self.deployed)

    def deployed_by_strategy(self, name: str) -> float:
        return sum(t.notional for t in self.open_trades if t.strategy == name)

    def deployed_by_venue(self) -> dict[str, float]:
        out: dict[str, float] = {}
        for t in self.open_trades:
            for venue, notional in t.venue_exposure().items():
                out[venue] = out.get(venue, 0.0) + notional
        return out


class RiskGovernor:
    """Stateful gatekeeper. One instance per agent run."""

    def __init__(self, cfg: RiskConfig, starting_equity: float) -> None:
        self.cfg = cfg
        self.high_water = starting_equity
        self.day = utc_day(time.time())
        self.day_start_equity = starting_equity
        self.halted = False
        self.halt_reason = ""
        self.day_halted = False
        self._cooldowns: dict[str, float] = {}
        self.refusals: dict[str, int] = {}

    # -- lifecycle -------------------------------------------------------
    def mark_equity(self, equity: float, now: float | None = None) -> None:
        """Update high-water marks and trip the day/drawdown switches."""
        now = time.time() if now is None else now
        today = utc_day(now)
        if today != self.day:
            self.day = today
            self.day_start_equity = equity
            self.day_halted = False
            log.info("new UTC day %s, daily loss limit reset", today)

        self.high_water = max(self.high_water, equity)

        if self.high_water > 0:
            dd = (self.high_water - equity) / self.high_water
            if dd >= self.cfg.max_drawdown_fraction and not self.halted:
                self.halt(f"max drawdown {dd:.2%} >= {self.cfg.max_drawdown_fraction:.2%}")

        if self.day_start_equity > 0:
            day_loss = (self.day_start_equity - equity) / self.day_start_equity
            if day_loss >= self.cfg.daily_loss_limit_fraction and not self.day_halted:
                self.day_halted = True
                log.warning(
                    "daily loss limit hit (%.2f%%): no new positions until %s+1",
                    day_loss * 100, self.day,
                )

    def halt(self, reason: str) -> None:
        self.halted = True
        self.halt_reason = reason
        log.error("KILL SWITCH: %s", reason)

    def resume(self) -> None:
        """Manual reset. Deliberately not called by any automatic code path."""
        self.halted = False
        self.halt_reason = ""
        self.day_halted = False
        log.warning("kill switch manually reset")

    def penalize(self, strategy: str, now: float | None = None) -> None:
        """Put a strategy in the penalty box after a losing trade."""
        now = time.time() if now is None else now
        self._cooldowns[strategy] = now + self.cfg.cooldown_after_loss_s

    def in_cooldown(self, strategy: str, now: float | None = None) -> bool:
        now = time.time() if now is None else now
        return self._cooldowns.get(strategy, 0.0) > now

    # -- the gate --------------------------------------------------------
    def approve(
        self,
        opp: Opportunity,
        requested: float,
        state: PortfolioState,
        data_age_s: float = 0.0,
        now: float | None = None,
    ) -> Decision:
        """Approve, shrink, or refuse a proposed position."""
        now = time.time() if now is None else now
        c = self.cfg

        def no(reason: str) -> Decision:
            self.refusals[reason.split(":")[0]] = self.refusals.get(reason.split(":")[0], 0) + 1
            return Decision(False, 0.0, reason)

        if self.halted:
            return no(f"halted: {self.halt_reason}")
        if self.day_halted:
            return no("daily loss limit reached")
        if data_age_s > c.max_data_age_s:
            return no(f"stale data: {data_age_s:.0f}s > {c.max_data_age_s:.0f}s")
        if self.in_cooldown(opp.strategy, now):
            return no(f"cooldown: {opp.strategy} is in the penalty box")
        if opp.edge_bps < c.min_edge_bps:
            return no(f"thin edge: {opp.edge_bps:.2f} < {c.min_edge_bps:.2f} bps")
        if len(state.open_trades) >= c.max_open_trades:
            return no(f"too many open trades: {len(state.open_trades)}")
        if state.equity <= 0:
            return no("no equity")

        # Successively tighten the ticket against every cap.
        allowed = min(requested, opp.capacity_usdt, c.max_ticket_usdt, state.free)

        room_total = c.max_deployed_fraction * state.equity - state.deployed
        allowed = min(allowed, room_total)

        room_strategy = c.max_strategy_fraction * state.equity - state.deployed_by_strategy(
            opp.strategy
        )
        allowed = min(allowed, room_strategy)

        venue_cap = c.max_venue_fraction * state.equity
        by_venue = state.deployed_by_venue()
        for venue in opp.venues or tuple({o.venue for o in opp.legs}):
            allowed = min(allowed, venue_cap - by_venue.get(venue, 0.0))

        if allowed < c.min_ticket_usdt:
            return no(f"ticket too small: {max(0.0, allowed):.2f} < {c.min_ticket_usdt:.2f} USDT")

        return Decision(True, float(allowed), "ok")

    # -- introspection ---------------------------------------------------
    def status(self, equity: float) -> dict[str, object]:
        dd = 0.0 if self.high_water <= 0 else (self.high_water - equity) / self.high_water
        day_pnl = equity - self.day_start_equity
        return {
            "halted": self.halted,
            "halt_reason": self.halt_reason,
            "day_halted": self.day_halted,
            "high_water": round(self.high_water, 2),
            "drawdown": round(dd, 4),
            "drawdown_limit": self.cfg.max_drawdown_fraction,
            "day": self.day,
            "day_pnl": round(day_pnl, 4),
            "cooldowns": {k: round(v, 1) for k, v in self._cooldowns.items()},
            "top_refusals": dict(sorted(self.refusals.items(), key=lambda kv: -kv[1])[:5]),
        }
