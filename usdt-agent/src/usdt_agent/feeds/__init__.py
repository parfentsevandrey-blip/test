"""Market-data feeds and the factory that assembles them from config."""

from __future__ import annotations

import logging

from ..config import AgentConfig
from .base import CompositeFeed, Feed, from_venue_symbol, to_venue_symbol
from .defillama import DefiLlamaFeed
from .exchanges import BinanceFeed, BybitFeed, OkxFeed, build_venue_feeds
from .synthetic import SyntheticFeed

log = logging.getLogger(__name__)

__all__ = [
    "BinanceFeed", "BybitFeed", "CompositeFeed", "DefiLlamaFeed", "Feed", "OkxFeed",
    "SyntheticFeed", "build_feed", "build_venue_feeds", "from_venue_symbol", "to_venue_symbol",
]


def build_feed(cfg: AgentConfig) -> tuple[CompositeFeed, str]:
    """Build the feed stack for a run. Returns ``(feed, resolved_source)``.

    ``data_source = "auto"`` tries the real venues once and silently falls back
    to the simulator if the network or the exchanges are unreachable — which is
    the correct behaviour on a boxed CI runner and in a geo-blocked region.
    """
    source = cfg.data_source

    if source == "synthetic":
        return CompositeFeed([SyntheticFeed(venues=cfg.venues, seed=cfg.seed)]), "synthetic"

    live_feeds: list[Feed] = [*build_venue_feeds(cfg.venues), DefiLlamaFeed()]

    if source == "live":
        return CompositeFeed(live_feeds), "live"

    probe = CompositeFeed(live_feeds, timeout=12.0).snapshot(cfg.symbols[:1])
    if probe.quotes or probe.pools:
        log.info("live market data reachable (%d quotes, %d pools)", len(probe.quotes), len(probe.pools))
        return CompositeFeed(live_feeds), "live"

    log.warning(
        "live data unreachable (%s) — falling back to the synthetic market",
        "; ".join(probe.errors)[:200] or "no data",
    )
    return CompositeFeed([SyntheticFeed(venues=cfg.venues, seed=cfg.seed)]), "synthetic"
