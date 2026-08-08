"""The ladder from zero — what to do first when you have nothing.

Starting with no capital, no accounts and no audience, the channels are not
equally available, and pretending otherwise wastes weeks. The order below is
forced by economics, not preference:

* **Yield needs capital.** 8 % APY on 0 USDT is 0 USDT. Passive income is the
  last rung, never the first.
* **Selling needs customers.** A service can earn while you sleep, but only
  after someone knows it exists. Zero marginal cost, non-zero marketing cost.
* **Referrals need traffic.** No audience, no clicks, no commission.
* **Paid work needs only skill and a wallet** — which is exactly why it is the
  first rung for someone starting from nothing.

The agent computes which rung you are actually on from observable facts (is a
wallet configured, has any payout confirmed, how large is the treasury) and
prints the next concrete action. It will not advance a stage on optimism.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Any

from .models import Autonomy

if TYPE_CHECKING:  # pragma: no cover
    from .base import Channel
    from .store import EarnStore


@dataclass(slots=True)
class Stage:
    """One rung of the ladder."""

    key: str
    title: str
    rationale: str
    done: bool = False
    actions: tuple[str, ...] = ()
    blocking: bool = True


@dataclass(slots=True)
class Ladder:
    """The full assessment: where you are, and precisely what unblocks you."""

    stages: list[Stage] = field(default_factory=list)
    treasury_usdt: float = 0.0
    confirmed_usdt: float = 0.0

    @property
    def current(self) -> Stage | None:
        return next((s for s in self.stages if not s.done), None)

    @property
    def completed(self) -> int:
        return sum(1 for s in self.stages if s.done)

    def next_actions(self) -> list[str]:
        stage = self.current
        return list(stage.actions) if stage else []

    def to_dict(self) -> dict[str, Any]:
        return {
            "treasury_usdt": round(self.treasury_usdt, 6),
            "confirmed_usdt": round(self.confirmed_usdt, 6),
            "stage": self.current.key if self.current else "self-funding",
            "completed": self.completed,
            "total": len(self.stages),
            "stages": [
                {"key": s.key, "title": s.title, "done": s.done, "actions": list(s.actions)}
                for s in self.stages
            ],
        }


def assess_ladder(
    channels: dict[str, Channel],
    store: EarnStore,
    treasury_usdt: float,
    min_deploy_usdt: float = 200.0,
) -> Ladder:
    """Work out which rung the operator is on, from facts only."""
    confirmed = store.confirmed_total()
    ready = {name: ch.ready()[0] for name, ch in channels.items()}
    wallet_ok = any(
        bool(getattr(ch, "wallet", None) and ch.wallet.addresses) for ch in channels.values()
    )
    orders = store.open_orders()
    stages: list[Stage] = []

    # -- 1. a place to receive money -------------------------------------
    stages.append(Stage(
        key="wallet",
        title="Have somewhere to be paid",
        rationale="Every channel below ends in a transfer. Without an address there is nowhere for it to land.",
        done=wallet_ok,
        actions=(
            "Create a wallet you control (Trust/Rabby/TronLink) — the agent never sees the key",
            "export USDT_WALLET_TRON=T...   # Tron: cents per transfer, best for small payouts",
            "Optionally add USDT_WALLET_BSC / _ARBITRUM / _ETHEREUM for payers who insist",
            "Verify with: usdt-agent wallet",
        ),
    ))

    # -- 2. a channel that can run at all --------------------------------
    any_ready = any(ready.values())
    unready = [f"{n}: {'; '.join(ch.ready()[1][:1])}" for n, ch in channels.items() if not ready[n]]
    stages.append(Stage(
        key="channel",
        title="Get at least one channel out of the blocked state",
        rationale="A channel with unmet requirements is skipped entirely — it cannot earn a cent.",
        done=any_ready,
        actions=tuple(
            ["Resolve one of these blockers:"] + [f"  · {u}" for u in unready[:4]]
            + ["Then re-check with: usdt-agent channels"]
        ) if unready else ("Run: usdt-agent channels",),
    ))

    # -- 3. work in flight -----------------------------------------------
    stages.append(Stage(
        key="pipeline",
        title="Put real work in flight",
        rationale=(
            "From zero, the only channels that pay without capital or an audience are the "
            "ones that trade your effort for money. Nothing arrives until something is submitted."
        ),
        done=bool(orders) or confirmed > 0,
        actions=(
            "Run: usdt-agent earn scan      # ranked by USDT per hour, net of the odds of being paid",
            "Pick one gig you can actually finish, then: usdt-agent earn take <gig-id>",
            "Approve the claim: usdt-agent earn approvals  →  usdt-agent earn approve <id>",
            "Do the work, submit it, and let the collector watch for the payout",
        ),
    ))

    # -- 4. proof the loop closes ----------------------------------------
    stages.append(Stage(
        key="first_payout",
        title="Confirm the first payout on-chain",
        rationale=(
            "Until a transfer lands, none of this is income — it is a pipeline. "
            "The first confirmation is what proves the whole loop actually closes."
        ),
        done=confirmed > 0,
        actions=(
            "Run: usdt-agent earn collect   # reads the chain, books only what really arrived",
            "Check: usdt-agent earn report",
        ),
    ))

    # -- 5. capital worth deploying --------------------------------------
    stages.append(Stage(
        key="capital",
        title=f"Accumulate {min_deploy_usdt:.0f} USDT so yield is worth switching on",
        rationale=(
            f"Below {min_deploy_usdt:.0f} USDT, gas and effort exceed anything stablecoin yield "
            "returns. The passive channel stays off on purpose until the maths works."
        ),
        done=treasury_usdt >= min_deploy_usdt,
        actions=(
            f"Keep earning: {max(0.0, min_deploy_usdt - treasury_usdt):.2f} USDT to go",
            "The passive channel switches itself on when the treasury clears the floor",
        ),
    ))

    # -- 6. compounding ---------------------------------------------------
    stages.append(Stage(
        key="compound",
        title="Let earned capital work alongside earned income",
        rationale="Once yield is live, the trading agent manages it under its own risk governor.",
        done=treasury_usdt >= min_deploy_usdt and confirmed > 0 and ready.get("passive", False),
        actions=(
            "Run: usdt-agent earn run       # earning loop",
            "Run: usdt-agent run            # yield/trading loop, paper first",
        ),
        blocking=False,
    ))

    return Ladder(stages=stages, treasury_usdt=treasury_usdt, confirmed_usdt=confirmed)


def setup_checklist(channels: dict[str, Channel]) -> list[dict[str, Any]]:
    """Everything a human must do once, grouped by whether the agent can help."""
    seen: set[str] = set()
    out: list[dict[str, Any]] = []
    for channel in channels.values():
        for req in channel.requirements():
            if req.key in seen:
                continue
            seen.add(req.key)
            out.append({
                "key": req.key,
                "description": req.description,
                "optional": req.optional,
                "who": "you" if req.autonomy is Autonomy.MANUAL else "agent",
                "how_to": req.how_to,
                "satisfied": channel._satisfied(req),
                "channel": channel.name,
            })
    out.sort(key=lambda r: (r["satisfied"], r["optional"], r["key"]))
    return out
