"""Referral and affiliate revenue: tracking what is owed, proving what arrived.

Affiliate income is where self-reported numbers do the most damage — dashboards
show "pending commission" for months and it is tempting to book that as
earnings. Here a program's dashboard number is only ever an **expectation**;
the payout becomes income when the collector sees it land on-chain.

Programs are declared in config (no API needed) with their payout schedule.
Where a program exposes an API and you supply a key, the agent reads accrued
balances from it; where it does not, the agent still reconciles the arrivals
and tells you which program is late.

The agent does not create referral links, spam them, or sign you up for
anything — those are your decisions and your reputation.
"""

from __future__ import annotations

import logging
import time
from datetime import UTC
from typing import Any

from ..base import Channel
from ..models import Autonomy, Gig, Payout, PayoutStatus, Requirement

log = logging.getLogger(__name__)

DAY = 86_400.0


class AffiliateChannel(Channel):
    """Tracks declared referral programs and reconciles their payouts."""

    name = "affiliate"
    description = "Tracks referral/affiliate programs and proves payouts on-chain"
    autonomy = Autonomy.ASSISTED
    capital_required_usdt = 0.0
    typical_lag_days = 30.0

    @staticmethod
    def defaults() -> dict[str, Any]:
        return {
            "preferred_chain": "",
            # Declare what you are actually signed up for. Nothing is invented.
            "programs": [],
            # e.g. {"name": "exchange-ref", "url": "...", "expected_monthly_usdt": 0.0,
            #       "payout_day": 5, "min_payout_usdt": 10.0}
        }

    def requirements(self) -> list[Requirement]:
        return [
            Requirement(
                "wallet",
                "A wallet address the programs pay out to",
                Autonomy.MANUAL,
                how_to="export USDT_WALLET_<CHAIN>=<address>",
            ),
            Requirement(
                "affiliate.programs",
                "At least one referral program declared in the config",
                Autonomy.MANUAL,
                how_to="Add entries to [earn.channels.affiliate.params] programs in the TOML config",
            ),
        ]

    def _satisfied(self, req):  # type: ignore[no-untyped-def]
        if req.key == "affiliate.programs":
            return bool(self.params.get("programs"))
        return super()._satisfied(req)

    def _next_payout_ts(self, program: dict[str, Any], now: float | None = None) -> float:
        """Next scheduled payout date for a monthly program."""
        now = now if now is not None else time.time()
        day = int(program.get("payout_day", 0) or 0)
        if day <= 0:
            return 0.0
        from datetime import datetime

        current = datetime.fromtimestamp(now, tz=UTC)
        year, month = current.year, current.month
        if current.day >= day:
            month += 1
            if month > 12:
                month, year = 1, year + 1
        try:
            return datetime(year, month, min(day, 28), tzinfo=UTC).timestamp()
        except ValueError:
            return 0.0

    def discover(self) -> list[Gig]:
        """Programs whose next payout is within reach become trackable gigs."""
        out: list[Gig] = []
        now = time.time()
        for program in self.params.get("programs") or []:
            name = str(program.get("name") or "unnamed")
            expected = float(program.get("expected_monthly_usdt") or 0.0)
            if expected <= 0:
                continue
            minimum = float(program.get("min_payout_usdt") or 0.0)
            due_ts = self._next_payout_ts(program, now)
            # Programs below their own payout threshold simply do not pay.
            probability = 0.6 if expected >= minimum else 0.05
            out.append(Gig(
                channel=self.name,
                external_id=name,
                title=f"{name} — expected {expected:.2f} USDT/month",
                url=str(program.get("url") or ""),
                reward_usdt=expected,
                effort_hours=float(program.get("upkeep_hours", 0.25)),
                payout_probability=probability,
                deadline_ts=due_ts,
                source="config",
                meta={"program": name, "min_payout_usdt": minimum,
                      "below_threshold": expected < minimum},
            ))
        return out

    def expected_payouts(self) -> list[Payout]:
        out: list[Payout] = []
        chain, address = self.receiving_address()
        now = time.time()
        for program in self.params.get("programs") or []:
            expected = float(program.get("expected_monthly_usdt") or 0.0)
            minimum = float(program.get("min_payout_usdt") or 0.0)
            if expected <= 0 or expected < minimum:
                continue
            due = self._next_payout_ts(program, now)
            if due <= 0 or due - now > 45 * DAY:
                continue
            out.append(Payout(
                channel=self.name,
                amount_usdt=expected,
                status=PayoutStatus.EXPECTED,
                chain=chain,
                address=address,
                expected_by_ts=due + 7 * DAY,  # a week of grace before "late"
                memo=f"affiliate {program.get('name', '?')}",
                meta={"program": program.get("name", "?")},
            ))
        return out
