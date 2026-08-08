"""Passive yield — the bridge from *earning* USDT to *deploying* it.

This channel is the handover point between the two halves of the project. It
does no discovery of its own: it watches the confirmed treasury balance and,
once there is enough of it to be worth deploying, hands sizing over to the
trading agent's :mod:`~usdt_agent.strategies.stable_yield` scanner.

It is the only channel with a capital requirement, and it is deliberately last
in the bootstrap ladder. Yield on nothing is nothing: 8 % APY on the 40 USDT a
first bounty pays is 9 cents a month, which does not cover the gas to deposit
it. The channel says so out loud instead of quietly opening a losing position.
"""

from __future__ import annotations

import logging
from typing import Any

from ...feeds import build_feed
from ...models import SECONDS_PER_YEAR
from ...strategies.stable_yield import StableYieldStrategy
from ..base import Channel
from ..models import Autonomy, Gig, Requirement

log = logging.getLogger(__name__)


class PassiveYieldChannel(Channel):
    """Turns idle treasury USDT into stablecoin yield, once there is enough."""

    name = "passive"
    description = "Deploys confirmed treasury USDT into stablecoin yield (needs capital)"
    autonomy = Autonomy.ASSISTED   # the deposit itself needs a signing wallet
    typical_lag_days = 1.0

    @staticmethod
    def defaults() -> dict[str, Any]:
        return {
            # Below this, gas and effort exceed anything the yield can produce.
            "min_deploy_usdt": 200.0,
            "min_apy": 0.04,
            "max_risk": 0.5,
            "preferred_chain": "",
        }

    def __init__(self, cfg, wallet, params=None) -> None:  # type: ignore[no-untyped-def]
        super().__init__(cfg, wallet, params)
        self.capital_required_usdt = float(self.params["min_deploy_usdt"])
        self._treasury_usdt = 0.0
        self._strategy = StableYieldStrategy(cfg, {
            "min_apy": self.params["min_apy"],
            "max_risk": self.params["max_risk"],
        })

    def set_treasury(self, amount_usdt: float) -> None:
        """Told by the orchestrator how much confirmed USDT is actually on hand."""
        self._treasury_usdt = max(0.0, amount_usdt)

    def requirements(self) -> list[Requirement]:
        return [
            Requirement(
                "wallet",
                "A wallet holding the treasury",
                Autonomy.MANUAL,
                how_to="export USDT_WALLET_<CHAIN>=<address>",
            ),
            Requirement(
                "treasury.capital",
                f"At least {self.params['min_deploy_usdt']:.0f} USDT of confirmed earnings",
                Autonomy.AUTO,
                how_to="Earn it first — the other channels feed this one",
            ),
        ]

    def _satisfied(self, req):  # type: ignore[no-untyped-def]
        if req.key == "treasury.capital":
            return self._treasury_usdt >= float(self.params["min_deploy_usdt"])
        return super()._satisfied(req)

    def discover(self) -> list[Gig]:
        """Rank live stablecoin pools, sized to the treasury we actually have."""
        capital = self._treasury_usdt
        if capital < float(self.params["min_deploy_usdt"]):
            log.info(
                "passive: treasury %.2f USDT is below the %.0f USDT deploy floor",
                capital, float(self.params["min_deploy_usdt"]),
            )
            return []

        try:
            feed, _ = build_feed(self.cfg)
            snapshot = feed.snapshot(self.cfg.symbols)
        except Exception as e:
            log.warning("passive: yield data unavailable: %s", str(e)[:120])
            return []

        out: list[Gig] = []
        for opp in self._strategy.scan(snapshot)[:5]:
            apy = float(opp.meta.get("apy", 0.0))
            size = min(capital, opp.capacity_usdt)
            annual = size * apy
            out.append(Gig(
                channel=self.name,
                external_id=str(opp.meta.get("pool_id") or opp.label),
                title=f"{opp.label} — {annual:.2f} USDT/year on {size:.0f} USDT",
                reward_usdt=annual * (opp.horizon_s / SECONDS_PER_YEAR),
                effort_hours=0.25,
                payout_probability=max(0.05, 1.0 - float(opp.meta.get("risk_score", 0.5))),
                source="defillama",
                meta={**opp.meta, "deploy_usdt": size, "annual_usdt": annual},
            ))
        return out
