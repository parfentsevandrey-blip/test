"""Value objects for the earning side of the agent.

The trading agent moves capital you already have. This half *acquires* USDT
from the internet — paid work, customers, referrals — and the vocabulary is
different: gigs instead of quotes, payouts instead of fills, effort instead of
notional.

One rule governs everything here:

    **Nothing counts as earned until it is confirmed on-chain.**

A discovered gig is a guess. A submitted work order is a hope. An "expected"
payout is somebody else's intention. Only a transfer the agent can see in a
block is income, and only that is allowed to move the treasury number.
"""

from __future__ import annotations

import hashlib
import re
import time
from dataclasses import dataclass, field
from enum import StrEnum
from typing import Any

SECONDS_PER_HOUR = 3600.0


class Autonomy(StrEnum):
    """How far the agent can take a step on its own.

    Being explicit about this is the difference between an honest agent and one
    that quietly does nothing while claiming to work. A channel that needs a
    human says so, up front, and the orchestrator routes it to the approval
    queue instead of pretending.
    """

    AUTO = "auto"          # end-to-end, no human in the loop
    ASSISTED = "assisted"  # agent prepares, a human approves or submits
    MANUAL = "manual"      # only a human can do it (KYC, signing, wallet custody)

    @property
    def rank(self) -> int:
        return {"auto": 0, "assisted": 1, "manual": 2}[self.value]


@dataclass(frozen=True, slots=True)
class Requirement:
    """Something that must exist before a channel can earn anything."""

    key: str                 # e.g. "GITHUB_TOKEN", "wallet.ethereum"
    description: str
    autonomy: Autonomy = Autonomy.MANUAL
    how_to: str = ""
    optional: bool = False

    def __str__(self) -> str:
        mark = "optional" if self.optional else "required"
        return f"{self.key} ({mark}): {self.description}"


# --------------------------------------------------------------------------
# Opportunities
# --------------------------------------------------------------------------

_MONEY = re.compile(
    r"(?:(?P<cur>\$|usd|usdt|usdc)\s*(?P<a>[\d][\d,\s]*(?:\.\d+)?)"
    r"|(?P<b>[\d][\d,\s]*(?:\.\d+)?)\s*(?P<cur2>usd|usdt|usdc|\$))",
    re.IGNORECASE,
)


def parse_reward(text: str) -> float:
    """Pull a USD/USDT amount out of free text. Returns 0.0 when unsure.

    Deliberately conservative: an unparseable reward is worth zero, not a
    guess. Ranking on a hallucinated number is how an agent ends up spending
    six hours on a $5 issue.
    """
    if not text:
        return 0.0
    best = 0.0
    for m in _MONEY.finditer(text):
        raw = m.group("a") or m.group("b") or ""
        raw = raw.replace(",", "").replace(" ", "")
        try:
            value = float(raw)
        except ValueError:
            continue
        # Filter out obvious non-money matches (years, issue numbers).
        if 1.0 <= value <= 1_000_000.0:
            best = max(best, value)
    return best


@dataclass(slots=True)
class Gig:
    """A concrete way to be paid: a bounty, a task, a customer order."""

    channel: str
    external_id: str
    title: str
    url: str = ""
    reward_usdt: float = 0.0
    effort_hours: float = 1.0
    payout_probability: float = 0.5
    deadline_ts: float = 0.0
    difficulty: str = "unknown"
    source: str = ""
    tags: tuple[str, ...] = ()
    discovered_ts: float = field(default_factory=time.time)
    meta: dict[str, Any] = field(default_factory=dict)

    @property
    def id(self) -> str:
        blob = f"{self.channel}|{self.source}|{self.external_id}".encode()
        return hashlib.sha256(blob).hexdigest()[:16]

    @property
    def expected_usdt(self) -> float:
        """Reward discounted by the odds of actually being paid."""
        return max(0.0, self.reward_usdt) * max(0.0, min(1.0, self.payout_probability))

    @property
    def usdt_per_hour(self) -> float:
        """The only ranking metric that matters when effort is the scarce input."""
        return self.expected_usdt / max(0.05, self.effort_hours)

    @property
    def expires_in_s(self) -> float:
        return 0.0 if self.deadline_ts <= 0 else max(0.0, self.deadline_ts - time.time())

    @property
    def is_expired(self) -> bool:
        return self.deadline_ts > 0 and self.deadline_ts < time.time()

    def score(self, min_rate: float = 0.0) -> float:
        """Rank gigs; anything below ``min_rate`` USDT/hour scores zero."""
        rate = self.usdt_per_hour
        if rate <= min_rate or self.is_expired:
            return 0.0
        # Prefer sooner deadlines only among otherwise comparable gigs.
        urgency = 1.0
        if self.deadline_ts > 0:
            days = self.expires_in_s / 86_400.0
            urgency = 1.15 if days < 3 else (1.0 if days < 30 else 0.9)
        return rate * urgency


class OrderStatus(StrEnum):
    DRAFT = "draft"                # agent has a plan, nothing sent
    AWAITING_APPROVAL = "awaiting_approval"
    SUBMITTED = "submitted"        # sent to the payer
    ACCEPTED = "accepted"          # payer agreed to pay
    REJECTED = "rejected"
    ABANDONED = "abandoned"
    PAID = "paid"                  # confirmed on-chain


@dataclass(slots=True)
class WorkOrder:
    """The agent's committed attempt at one gig."""

    gig_id: str
    channel: str
    title: str
    plan: tuple[str, ...] = ()
    status: OrderStatus = OrderStatus.DRAFT
    reward_usdt: float = 0.0
    estimated_hours: float = 0.0
    actual_hours: float = 0.0
    autonomy: Autonomy = Autonomy.ASSISTED
    deliverable: str = ""
    created_ts: float = field(default_factory=time.time)
    updated_ts: float = field(default_factory=time.time)
    notes: str = ""
    meta: dict[str, Any] = field(default_factory=dict)

    @property
    def id(self) -> str:
        return hashlib.sha256(f"{self.channel}|{self.gig_id}".encode()).hexdigest()[:16]

    @property
    def is_open(self) -> bool:
        return self.status not in (
            OrderStatus.PAID, OrderStatus.REJECTED, OrderStatus.ABANDONED
        )

    @property
    def realized_rate(self) -> float:
        """USDT per hour actually achieved. Zero until paid — by design."""
        if self.status is not OrderStatus.PAID or self.actual_hours <= 0:
            return 0.0
        return self.reward_usdt / self.actual_hours


# --------------------------------------------------------------------------
# Money
# --------------------------------------------------------------------------


class PayoutStatus(StrEnum):
    EXPECTED = "expected"    # somebody said they would pay
    PENDING = "pending"      # seen on-chain, not enough confirmations
    CONFIRMED = "confirmed"  # money is ours — the only status that counts
    FAILED = "failed"
    EXPIRED = "expired"      # never arrived within the deadline


@dataclass(slots=True)
class Payout:
    """Money owed or received. Only ``CONFIRMED`` moves the treasury."""

    channel: str
    amount_usdt: float
    status: PayoutStatus = PayoutStatus.EXPECTED
    gig_id: str = ""
    order_id: str = ""
    chain: str = ""
    address: str = ""
    tx_hash: str = ""
    confirmations: int = 0
    expected_by_ts: float = 0.0
    created_ts: float = field(default_factory=time.time)
    confirmed_ts: float = 0.0
    memo: str = ""
    meta: dict[str, Any] = field(default_factory=dict)

    @property
    def id(self) -> str:
        seed = self.tx_hash or f"{self.channel}|{self.gig_id}|{self.created_ts}"
        return hashlib.sha256(seed.encode()).hexdigest()[:16]

    @property
    def is_income(self) -> bool:
        return self.status is PayoutStatus.CONFIRMED

    @property
    def is_overdue(self) -> bool:
        return (
            self.status is PayoutStatus.EXPECTED
            and self.expected_by_ts > 0
            and self.expected_by_ts < time.time()
        )


@dataclass(frozen=True, slots=True)
class OnChainTransfer:
    """An incoming USDT transfer observed in a block. The unit of truth."""

    chain: str
    to_address: str
    from_address: str
    amount_usdt: float
    tx_hash: str
    block: int
    ts: float = 0.0

    @property
    def key(self) -> str:
        return f"{self.chain}:{self.tx_hash}"


@dataclass(slots=True)
class ChannelReport:
    """Per-channel scoreboard. ``confirmed_usdt`` is the only real number."""

    channel: str
    ready: bool
    blockers: tuple[str, ...] = ()
    gigs_found: int = 0
    orders_open: int = 0
    expected_usdt: float = 0.0
    confirmed_usdt: float = 0.0
    hours_spent: float = 0.0
    error: str = ""

    @property
    def realized_rate(self) -> float:
        return self.confirmed_usdt / self.hours_spent if self.hours_spent > 0 else 0.0
