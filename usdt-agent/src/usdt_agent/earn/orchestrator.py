"""The earning loop and the calibration check that keeps it honest.

One cycle:

1. **Readiness** — skip every channel whose requirements are unmet, loudly.
2. **Discover** — each ready channel returns gigs, already priced per hour.
3. **Rank** — one global queue across channels, sorted by USDT per hour after
   the odds of being paid. A $500 bounty at 5 % odds loses to a $40 one at 80 %.
4. **Commit** — draft work orders for the best gigs; anything that talks to a
   third party stops at the approval queue.
5. **Collect** — reconcile the wallet against expectations; book only what
   confirmed on-chain.
6. **Calibrate** — compare what was estimated against what was realized.

Step 6 is the one that stops the agent lying to itself. Effort estimates and
payout odds are guesses; once orders complete, the *measured* conversion rate
and the *measured* USDT/hour replace them in the scoreboard, and a channel with
no confirmed payouts is reported as unproven no matter how full its pipeline is.
"""

from __future__ import annotations

import logging
import time
from dataclasses import dataclass, field
from typing import Any

from ..config import AgentConfig
from ..ledger import Ledger
from .base import Channel
from .bootstrap import Ladder, assess_ladder
from .collector import CollectionResult, Collector
from .models import Autonomy, Gig, OrderStatus
from .store import EarnStore
from .wallet import Wallet

log = logging.getLogger(__name__)


@dataclass(slots=True)
class ChannelCalibration:
    """Estimated versus realized, per channel. The anti-self-deception report."""

    channel: str
    orders: int = 0
    paid: int = 0
    confirmed_usdt: float = 0.0
    estimated_hours: float = 0.0
    actual_hours: float = 0.0

    @property
    def conversion(self) -> float:
        return self.paid / self.orders if self.orders else 0.0

    @property
    def realized_rate(self) -> float:
        return self.confirmed_usdt / self.actual_hours if self.actual_hours > 0 else 0.0

    @property
    def effort_calibration(self) -> float:
        """actual/estimated hours. Above 1 means the estimates were optimistic."""
        return self.actual_hours / self.estimated_hours if self.estimated_hours > 0 else 0.0

    @property
    def proven(self) -> bool:
        """A channel is proven only by money that actually arrived."""
        return self.paid >= 3 and self.confirmed_usdt > 0

    @property
    def label(self) -> str:
        if self.proven:
            return "proven"
        if self.confirmed_usdt > 0:
            return "promising"
        return "unproven" if self.orders else "untried"


@dataclass(slots=True)
class EarnCycleReport:
    cycle: int
    ts: float
    treasury_usdt: float
    confirmed_this_cycle: float
    gigs_found: int
    gigs_new: int
    orders_created: int
    approvals_pending: int
    ready_channels: tuple[str, ...] = ()
    blocked_channels: tuple[str, ...] = ()
    best_gig: str = ""
    errors: dict[str, str] = field(default_factory=dict)


class EarningAgent:
    """Runs the revenue channels and reconciles what they actually produce."""

    def __init__(
        self,
        cfg: AgentConfig,
        channels: dict[str, Channel],
        wallet: Wallet,
        ledger: Ledger,
        *,
        max_open_orders: int = 5,
        min_rate_usdt_per_hour: float = 10.0,
    ) -> None:
        self.cfg = cfg
        self.channels = channels
        self.wallet = wallet
        self.ledger = ledger
        self.store = EarnStore(ledger)
        self.max_open_orders = max_open_orders
        self.min_rate = min_rate_usdt_per_hour
        self.cycle = 0
        self.history: list[EarnCycleReport] = []

        self.collector = Collector(
            wallet, self.store, service_channel=channels.get("services")
        )
        ledger.append("earn_start", {
            "channels": sorted(channels),
            "chains": self.wallet.chains(),
        })

    # ------------------------------------------------------------------
    @property
    def treasury_usdt(self) -> float:
        """Confirmed, on-chain earnings. Never an estimate."""
        return self.store.confirmed_total()

    def ready_channels(self) -> dict[str, Channel]:
        out: dict[str, Channel] = {}
        for name, channel in self.channels.items():
            if name == "passive" and hasattr(channel, "set_treasury"):
                channel.set_treasury(self.treasury_usdt)
            ok, blockers = channel.ready()
            if ok:
                out[name] = channel
            else:
                log.info("channel %s blocked: %s", name, "; ".join(blockers[:2]))
        return out

    # ------------------------------------------------------------------
    def discover(self) -> tuple[list[Gig], dict[str, str]]:
        """Every ready channel's opportunities, in one ranked queue."""
        gigs: list[Gig] = []
        errors: dict[str, str] = {}
        for name, channel in self.ready_channels().items():
            try:
                found = channel.discover()
                gigs.extend(found)
                log.info("channel %s: %d gigs", name, len(found))
            except Exception as e:
                errors[name] = str(e)[:160]
                log.warning("channel %s discovery failed: %s", name, str(e)[:120])
                self.ledger.append("earn_error", {"channel": name, "error": str(e)[:300]})
        gigs.sort(key=lambda g: g.score(self.min_rate), reverse=True)
        return gigs, errors

    def commit(self, gigs: list[Gig]) -> int:
        """Draft work orders for the best gigs we do not already have one for."""
        open_orders = self.store.open_orders()
        room = max(0, self.max_open_orders - len(open_orders))
        if room == 0:
            return 0

        created = 0
        for gig in gigs:
            if created >= room:
                break
            if gig.score(self.min_rate) <= 0 or self.store.has_order_for(gig.id):
                continue
            channel = self.channels.get(gig.channel)
            if channel is None:
                continue

            order = channel.plan(gig)
            order = channel.execute(order)
            self.store.save_order(order)
            created += 1

            if order.status is OrderStatus.AWAITING_APPROVAL:
                self.store.request_approval(
                    kind="work_order",
                    title=f"[{gig.channel}] {gig.title[:80]}",
                    detail=(
                        f"{gig.url}\n"
                        f"reward {gig.reward_usdt:.2f} USDT · est {gig.effort_hours:.1f} h · "
                        f"{gig.usdt_per_hour:.0f} USDT/h · payout odds {gig.payout_probability:.0%}\n"
                        f"plan:\n  - " + "\n  - ".join(order.plan)
                    ),
                    subject_id=order.id,
                    channel=gig.channel,
                )
        return created

    def register_expectations(self) -> int:
        payouts = []
        for channel in self.ready_channels().values():
            try:
                payouts.extend(channel.expected_payouts())
            except Exception as e:
                log.warning("expected_payouts failed for %s: %s", channel.name, str(e)[:100])
        return self.collector.register_expectations(payouts)

    def collect(self) -> CollectionResult:
        return self.collector.collect()

    # ------------------------------------------------------------------
    def step(self) -> EarnCycleReport:
        self.cycle += 1
        gigs, errors = self.discover()
        new = self.store.upsert_gigs(gigs)
        created = self.commit(gigs)
        self.register_expectations()
        collection = self.collect()
        errors.update(collection.errors)

        ready = tuple(sorted(self.ready_channels()))
        blocked = tuple(sorted(set(self.channels) - set(ready)))

        report = EarnCycleReport(
            cycle=self.cycle,
            ts=time.time(),
            treasury_usdt=self.treasury_usdt,
            confirmed_this_cycle=collection.confirmed_usdt,
            gigs_found=len(gigs),
            gigs_new=new,
            orders_created=created,
            approvals_pending=len(self.store.pending_approvals()),
            ready_channels=ready,
            blocked_channels=blocked,
            best_gig=gigs[0].title[:80] if gigs else "",
            errors=errors,
        )
        self.history.append(report)
        log.info(
            "earn cycle %d | treasury %.2f USDT (+%.2f) | %d gigs (%d new) | %d orders | %d awaiting approval",
            report.cycle, report.treasury_usdt, report.confirmed_this_cycle,
            report.gigs_found, report.gigs_new, report.orders_created, report.approvals_pending,
        )
        return report

    def run(self, cycles: int = 1, interval_s: float = 0.0, on_cycle: Any = None) -> list[EarnCycleReport]:
        for _ in range(max(1, cycles)):
            started = time.time()
            try:
                report = self.step()
            except KeyboardInterrupt:
                break
            except Exception as e:
                log.exception("earn cycle failed: %s", e)
                self.ledger.append("earn_cycle_error", {"cycle": self.cycle, "error": str(e)[:300]})
                continue
            if on_cycle is not None:
                on_cycle(report, self)
            if interval_s > 0:
                time.sleep(max(0.0, interval_s - (time.time() - started)))
        return self.history

    # ------------------------------------------------------------------
    def calibration(self) -> dict[str, ChannelCalibration]:
        """Estimated versus realized, per channel."""
        out: dict[str, ChannelCalibration] = {
            name: ChannelCalibration(channel=name) for name in self.channels
        }
        for row in self.store.conn.execute("SELECT * FROM orders"):
            cal = out.setdefault(row["channel"], ChannelCalibration(channel=row["channel"]))
            cal.orders += 1
            cal.estimated_hours += float(row["estimated_hours"] or 0.0)
            cal.actual_hours += float(row["actual_hours"] or 0.0)
            if row["status"] == OrderStatus.PAID.value:
                cal.paid += 1
        for name, cal in out.items():
            cal.confirmed_usdt = self.store.confirmed_total(name)
            if cal.actual_hours <= 0:
                cal.actual_hours = self.store.hours_spent(name)
        return out

    def ladder(self) -> Ladder:
        passive = self.channels.get("passive")
        floor = float(passive.params.get("min_deploy_usdt", 200.0)) if passive else 200.0
        return assess_ladder(self.channels, self.store, self.treasury_usdt, floor)

    def summary(self) -> dict[str, Any]:
        calibration = self.calibration()
        ready = self.ready_channels()
        return {
            "treasury_usdt": round(self.treasury_usdt, 6),
            "expected_usdt": round(self.store.expected_total(), 6),
            "wallet": self.wallet.status(),
            "cycles": self.cycle,
            "store": self.store.summary(),
            "ladder": self.ladder().to_dict(),
            "channels": {
                name: {
                    **channel.info(),
                    "confirmed_usdt": round(self.store.confirmed_total(name), 6),
                    "expected_usdt": round(self.store.expected_total(name), 6),
                    "calibration": {
                        "orders": calibration[name].orders,
                        "paid": calibration[name].paid,
                        "conversion": round(calibration[name].conversion, 4),
                        "realized_usdt_per_hour": round(calibration[name].realized_rate, 4),
                        "effort_calibration": round(calibration[name].effort_calibration, 3),
                        "verdict": calibration[name].label,
                    },
                }
                for name, channel in self.channels.items()
            },
            "ready_channels": sorted(ready),
            "autonomy": {
                name: channel.autonomy.value for name, channel in self.channels.items()
            },
        }


def autonomy_summary(channels: dict[str, Channel]) -> dict[str, list[str]]:
    """Group channels by how far they can run unattended."""
    out: dict[str, list[str]] = {a.value: [] for a in Autonomy}
    for name, channel in channels.items():
        out[channel.autonomy.value].append(name)
    return out
