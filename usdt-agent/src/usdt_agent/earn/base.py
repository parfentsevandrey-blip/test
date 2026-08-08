"""The channel contract.

A **channel** is one way of acquiring USDT from the internet. Each declares,
honestly and up front:

* what it **requires** before it can earn anything (a wallet, an account, a
  token) — and which of those a human must do personally;
* how **autonomous** it can be — end-to-end, or only up to a human decision;
* how much **capital** it needs (zero, for everything except passive yield).

Channels discover gigs and prepare work. They never declare income: only the
collector does that, and only against a confirmed on-chain transfer. A channel
that could mark its own revenue would be a channel that reports whatever makes
it look good.
"""

from __future__ import annotations

import abc
import logging
import os
from typing import Any

from ..config import AgentConfig
from .models import Autonomy, Gig, OrderStatus, Payout, Requirement, WorkOrder
from .wallet import Wallet

log = logging.getLogger(__name__)


class Channel(abc.ABC):
    """One revenue stream."""

    name: str = "channel"
    description: str = ""
    #: The best the channel can do without a human. Never optimistic.
    autonomy: Autonomy = Autonomy.ASSISTED
    #: Working capital needed before the channel can produce anything.
    capital_required_usdt: float = 0.0
    #: Rough time from starting work to money landing, for expectation-setting.
    typical_lag_days: float = 7.0

    def __init__(self, cfg: AgentConfig, wallet: Wallet, params: dict[str, Any] | None = None) -> None:
        self.cfg = cfg
        self.wallet = wallet
        self.params = {**self.defaults(), **(params or {})}

    @staticmethod
    def defaults() -> dict[str, Any]:
        return {}

    # -- readiness -------------------------------------------------------
    def requirements(self) -> list[Requirement]:
        """What must exist before this channel can earn. Override per channel."""
        return []

    def ready(self) -> tuple[bool, list[str]]:
        """``(ready, blockers)``. A channel that is not ready is never run."""
        blockers: list[str] = []
        for req in self.requirements():
            if req.optional or self._satisfied(req):
                continue
            blockers.append(f"{req.key}: {req.description}")
        return (not blockers), blockers

    def _satisfied(self, req: Requirement) -> bool:
        """Default check: an env var, or a configured wallet address."""
        if req.key.startswith("wallet."):
            chain = req.key.split(".", 1)[1]
            return bool(self.wallet.addresses.get(chain))
        if req.key == "wallet":
            return bool(self.wallet.addresses)
        return bool(os.environ.get(req.key, "").strip())

    # -- the work --------------------------------------------------------
    @abc.abstractmethod
    def discover(self) -> list[Gig]:
        """Find opportunities. Must return [] rather than raise on a dead API."""

    def plan(self, gig: Gig) -> WorkOrder:
        """Turn a gig into a concrete plan the agent (or a human) can execute."""
        return WorkOrder(
            gig_id=gig.id,
            channel=self.name,
            title=gig.title,
            plan=("review the opportunity", "do the work", "submit", "await payment"),
            status=OrderStatus.DRAFT,
            reward_usdt=gig.reward_usdt,
            estimated_hours=gig.effort_hours,
            autonomy=self.autonomy,
            meta={"url": gig.url},
        )

    def execute(self, order: WorkOrder) -> WorkOrder:
        """Advance an order as far as this channel's autonomy allows.

        The default stops at the approval boundary, which is the correct and
        safe behaviour for anything that talks to a third party on your behalf.
        """
        order.status = (
            OrderStatus.AWAITING_APPROVAL
            if self.autonomy is not Autonomy.AUTO
            else OrderStatus.SUBMITTED
        )
        return order

    def expected_payouts(self) -> list[Payout]:
        """Payouts this channel believes are owed. Never counted as income."""
        return []

    # -- receiving -------------------------------------------------------
    def receiving_address(self) -> tuple[str, str]:
        """``(chain, address)`` where this channel expects to be paid."""
        preferred = str(self.params.get("preferred_chain", ""))
        if preferred and self.wallet.addresses.get(preferred):
            return preferred, self.wallet.addresses[preferred]
        for chain, address in self.wallet.addresses.items():
            if address:
                return chain, address
        return "", ""

    # -- introspection ---------------------------------------------------
    def info(self) -> dict[str, Any]:
        ready, blockers = self.ready()
        return {
            "name": self.name,
            "description": self.description,
            "autonomy": self.autonomy.value,
            "capital_required_usdt": self.capital_required_usdt,
            "typical_lag_days": self.typical_lag_days,
            "ready": ready,
            "blockers": blockers,
            "requirements": [str(r) for r in self.requirements()],
        }


def estimate_effort_hours(text: str, labels: tuple[str, ...] = (), floor: float = 0.5) -> float:
    """Heuristic effort estimate for a technical task.

    It is a guess and is labelled as one everywhere it surfaces. Its only job is
    to stop the agent burning a day on a $10 issue; once real orders complete,
    ``actual_hours`` replaces it in every rate calculation that matters.
    """
    lowered = " ".join(labels).lower() + " " + (text or "")[:2000].lower()
    hours = 3.0
    if any(k in lowered for k in ("good first issue", "beginner", "typo", "documentation", "docs")):
        hours = 1.0
    if any(k in lowered for k in ("refactor", "migration", "architecture", "redesign")):
        hours = 12.0
    if any(k in lowered for k in ("security", "audit", "vulnerability", "exploit")):
        hours = 16.0
    if any(k in lowered for k in ("hard", "complex", "epic")):
        hours *= 1.8
    # Longer descriptions correlate with more scope, weakly.
    hours *= 1.0 + min(1.0, len(text or "") / 8000.0)
    return max(floor, round(hours, 2))


def payout_probability(
    *, has_explicit_reward: bool, assignees: int = 0, participants: int = 0,
    age_days: float = 0.0, verified_payer: bool = False,
) -> float:
    """Odds that pursuing a gig ends in money. Deliberately pessimistic.

    Competition is the dominant term: a bounty with five people already on it is
    mostly a lottery ticket, and pricing it as a certainty is how an agent
    manufactures an impressive-looking pipeline that never pays.
    """
    p = 0.45 if has_explicit_reward else 0.12
    if verified_payer:
        p += 0.20
    if assignees > 0:
        p *= 0.25          # somebody else already owns it
    if participants > 1:
        p *= max(0.2, 1.0 / participants)
    if age_days > 180:
        p *= 0.5           # stale bounties are stale for a reason
    elif age_days > 60:
        p *= 0.8
    return max(0.01, min(0.95, p))
