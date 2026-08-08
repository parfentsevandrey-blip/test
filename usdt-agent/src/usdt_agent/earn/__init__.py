"""The earning half of the agent: acquiring USDT from the internet.

Where :mod:`usdt_agent.agent` deploys capital you already have, this half goes
and gets it — paid work, customers, referrals — and proves every cent by
reading the chain rather than trusting a dashboard.

Registry pattern matches the trading strategies: subclass :class:`Channel`,
register it in :data:`CHANNEL_REGISTRY`, add a config block, done.
"""

from __future__ import annotations

import logging

from ..config import AgentConfig
from .base import Channel
from .bootstrap import Ladder, assess_ladder, setup_checklist
from .channels import AffiliateChannel, BountyChannel, PassiveYieldChannel, ServiceChannel
from .collector import Collector
from .models import Autonomy, Gig, OrderStatus, Payout, PayoutStatus, WorkOrder
from .orchestrator import EarningAgent
from .store import EarnStore
from .wallet import CHAINS, RECOMMENDED_CHAINS, Wallet, wallet_from_env_or_config

log = logging.getLogger(__name__)

CHANNEL_REGISTRY: dict[str, type[Channel]] = {
    BountyChannel.name: BountyChannel,
    ServiceChannel.name: ServiceChannel,
    AffiliateChannel.name: AffiliateChannel,
    PassiveYieldChannel.name: PassiveYieldChannel,
}

#: Order the ladder recommends switching channels on, given a zero start.
BOOTSTRAP_ORDER = ("bounties", "services", "affiliate", "passive")

__all__ = [
    "CHAINS",
    "CHANNEL_REGISTRY",
    "RECOMMENDED_CHAINS",
    "AffiliateChannel",
    "Autonomy",
    "BountyChannel",
    "Channel",
    "Collector",
    "EarnStore",
    "EarningAgent",
    "Gig",
    "Ladder",
    "OrderStatus",
    "Payout",
    "PayoutStatus",
    "ServiceChannel",
    "Wallet",
    "WorkOrder",
    "assess_ladder",
    "build_channels",
    "setup_checklist",
    "wallet_from_env_or_config",
]


def build_channels(
    cfg: AgentConfig,
    wallet: Wallet,
    channel_params: dict[str, dict] | None = None,
    enabled: tuple[str, ...] | None = None,
) -> dict[str, Channel]:
    """Instantiate the enabled channels, in bootstrap order."""
    params = channel_params or {}
    wanted = enabled if enabled is not None else tuple(CHANNEL_REGISTRY)
    out: dict[str, Channel] = {}
    for name in BOOTSTRAP_ORDER:
        if name not in wanted or name not in CHANNEL_REGISTRY:
            continue
        cls = CHANNEL_REGISTRY[name]
        try:
            out[name] = cls(cfg, wallet, params.get(name))
        except Exception as e:
            log.warning("could not build channel %s: %s", name, e)
    return out
