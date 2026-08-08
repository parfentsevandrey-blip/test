"""Paid technical work: bounty-labelled issues and crypto-paying task boards.

This is the highest-yield channel for someone starting from literally zero —
no capital, no audience, no accounts beyond a GitHub login and a wallet. The
money is real, and so is the catch: **somebody has to actually do the work.**
The agent finds the opportunities, prices them per hour, drafts an approach and
tracks the payout; it does not pretend that a bounty pays itself.

Sources are pluggable and degrade independently. GitHub's search API is the
backbone; extra boards are queried only if reachable, and a dead board removes
its listings from the ranking rather than taking the channel down.

Rate limits: unauthenticated GitHub search allows 10 requests/minute, which is
enough for a poll every few minutes. Setting ``GITHUB_TOKEN`` raises it to 30
and is strongly recommended.
"""

from __future__ import annotations

import logging
import os
import time
from datetime import datetime
from typing import Any

from ... import http
from ..base import Channel, estimate_effort_hours, payout_probability
from ..models import Autonomy, Gig, OrderStatus, Requirement, WorkOrder, parse_reward

log = logging.getLogger(__name__)

GITHUB_SEARCH = "https://api.github.com/search/issues"

#: Label conventions used by the major bounty platforms.
DEFAULT_QUERIES: tuple[str, ...] = (
    'is:issue is:open label:"💎 Bounty"',        # Algora
    'is:issue is:open label:bounty',
    'is:issue is:open label:"bounty" label:"help wanted"',
    'is:issue is:open label:"💰 Reward"',
)


def _iso_to_ts(value: str) -> float:
    if not value:
        return 0.0
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00")).timestamp()
    except ValueError:
        return 0.0


class BountyChannel(Channel):
    """Discovers paid issues, ranks them by USDT per hour, drafts an approach."""

    name = "bounties"
    description = "Bounty-labelled issues and crypto-paying tasks, ranked by USDT/hour"
    autonomy = Autonomy.ASSISTED  # the agent can draft; a human ships and claims
    capital_required_usdt = 0.0
    typical_lag_days = 14.0

    @staticmethod
    def defaults() -> dict[str, Any]:
        return {
            "queries": list(DEFAULT_QUERIES),
            "per_page": 30,
            "min_reward_usdt": 25.0,
            "min_rate_usdt_per_hour": 15.0,
            "max_results": 60,
            "languages": [],          # e.g. ["python", "typescript"] to filter
            "exclude_assigned": True,
            "preferred_chain": "",
        }

    def requirements(self) -> list[Requirement]:
        return [
            Requirement(
                "GITHUB_TOKEN",
                "GitHub token — raises the search rate limit and is needed to claim work",
                Autonomy.MANUAL,
                how_to="github.com → Settings → Developer settings → personal access token (public_repo scope is enough)",
                optional=True,
            ),
            Requirement(
                "wallet",
                "A wallet address to be paid to",
                Autonomy.MANUAL,
                how_to="Set USDT_WALLET_TRON (or _BSC/_ETHEREUM/_ARBITRUM) to a receiving address",
            ),
        ]

    # -- discovery -------------------------------------------------------
    def _headers(self) -> dict[str, str]:
        headers = {"Accept": "application/vnd.github+json"}
        token = os.environ.get("GITHUB_TOKEN", "").strip()
        if token:
            headers["Authorization"] = f"Bearer {token}"
        return headers

    def _search(self, query: str) -> list[dict[str, Any]]:
        data = http.get_json(
            GITHUB_SEARCH,
            params={"q": query, "per_page": int(self.params["per_page"]), "sort": "created",
                    "order": "desc"},
            headers=self._headers(),
            timeout=20.0,
            retries=1,
        ) or {}
        return data.get("items") or []

    def _to_gig(self, item: dict[str, Any]) -> Gig | None:
        title = str(item.get("title") or "")
        body = str(item.get("body") or "")[:4000]
        labels = tuple(str(lbl.get("name", "")) for lbl in item.get("labels", []) or [])

        # The reward can be in the title, a label ("$500"), or the body.
        reward = max(
            parse_reward(title),
            parse_reward(" ".join(labels)),
            parse_reward(body[:1500]),
        )
        if reward < float(self.params["min_reward_usdt"]):
            return None

        assignees = len(item.get("assignees") or [])
        if self.params["exclude_assigned"] and assignees > 0:
            return None

        created = _iso_to_ts(str(item.get("created_at") or ""))
        age_days = (time.time() - created) / 86_400.0 if created else 0.0
        effort = estimate_effort_hours(f"{title}\n{body}", labels)
        probability = payout_probability(
            has_explicit_reward=reward > 0,
            assignees=assignees,
            participants=int(item.get("comments") or 0) // 4,
            age_days=age_days,
            verified_payer=any("bounty" in lbl.lower() or "💎" in lbl for lbl in labels),
        )

        url = str(item.get("html_url") or "")
        repo = url.split("/issues/")[0].replace("https://github.com/", "") if url else ""

        return Gig(
            channel=self.name,
            external_id=str(item.get("id") or url),
            title=title[:200],
            url=url,
            reward_usdt=reward,
            effort_hours=effort,
            payout_probability=probability,
            difficulty="easy" if effort <= 2 else ("hard" if effort >= 10 else "medium"),
            source="github",
            tags=labels[:8],
            meta={
                "repo": repo,
                "comments": item.get("comments", 0),
                "created_at": item.get("created_at", ""),
                "age_days": round(age_days, 1),
                "effort_is_estimated": True,
                "body_excerpt": body[:600],
            },
        )

    def discover(self) -> list[Gig]:
        gigs: dict[str, Gig] = {}
        languages = [str(x).lower() for x in self.params.get("languages") or []]

        for query in self.params["queries"]:
            full = query
            if languages:
                full += " " + " ".join(f"language:{lang}" for lang in languages[:1])
            try:
                items = self._search(full)
            except Exception as e:
                log.info("bounty query failed (%s): %s", query[:40], str(e)[:120])
                continue
            for item in items:
                gig = self._to_gig(item)
                if gig is not None:
                    gigs[gig.id] = gig

        ranked = sorted(gigs.values(), key=lambda g: g.score(), reverse=True)
        floor = float(self.params["min_rate_usdt_per_hour"])
        kept = [g for g in ranked if g.usdt_per_hour >= floor][: int(self.params["max_results"])]
        log.info("bounties: %d found, %d clear %.0f USDT/h", len(gigs), len(kept), floor)
        return kept

    # -- planning --------------------------------------------------------
    def plan(self, gig: Gig) -> WorkOrder:
        repo = gig.meta.get("repo", "the repository")
        return WorkOrder(
            gig_id=gig.id,
            channel=self.name,
            title=gig.title,
            plan=(
                f"read the issue and {repo}'s CONTRIBUTING guide",
                "confirm the bounty is unclaimed and comment to claim it",
                "reproduce the problem locally, write a failing test",
                "implement the fix and make the test pass",
                "open a PR referencing the issue, link the bounty",
                f"expect payment to {self.receiving_address()[0] or 'your wallet'} after merge",
            ),
            status=OrderStatus.DRAFT,
            reward_usdt=gig.reward_usdt,
            estimated_hours=gig.effort_hours,
            autonomy=self.autonomy,
            notes=(
                f"Estimated {gig.effort_hours:.1f} h at {gig.usdt_per_hour:.0f} USDT/h "
                f"(payout odds {gig.payout_probability:.0%}). Effort is an estimate, "
                f"not a measurement."
            ),
            meta={"url": gig.url, "repo": repo},
        )

    def execute(self, order: WorkOrder) -> WorkOrder:
        """Never claims a bounty on its own — claiming is a promise to deliver."""
        order.status = OrderStatus.AWAITING_APPROVAL
        return order
