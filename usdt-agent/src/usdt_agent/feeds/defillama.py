"""Stablecoin yield feed backed by DefiLlama's public ``/pools`` endpoint.

Filters aggressively before the data ever reaches a strategy: USDT/USDC/DAI-ish
single-sided pools only, above a TVL floor, and with an APY sanity ceiling.
A 900 % "stablecoin" pool is not an opportunity, it is a countdown.
"""

from __future__ import annotations

import logging
import time
from typing import Any

from .. import http
from ..models import FundingRate, Quote, YieldPool
from .base import Feed

log = logging.getLogger(__name__)

STABLE_TOKENS = {"USDT", "USDC", "DAI", "TUSD", "FDUSD", "USDE", "PYUSD", "USDS", "SUSDS", "GUSD"}


def _is_stable_symbol(symbol: str) -> bool:
    parts = [p.strip().upper() for p in symbol.replace("/", "-").split("-") if p.strip()]
    return bool(parts) and all(p in STABLE_TOKENS for p in parts)


class DefiLlamaFeed(Feed):
    """Yield oracle. Read-only, unauthenticated, cached between polls."""

    name = "defillama"
    URL = "https://yields.llama.fi/pools"

    def __init__(
        self,
        timeout: float = 15.0,
        min_tvl_usd: float = 5_000_000.0,
        max_apy: float = 0.60,
        limit: int = 40,
        cache_ttl_s: float = 300.0,
    ) -> None:
        self.timeout = timeout
        self.min_tvl_usd = min_tvl_usd
        self.max_apy = max_apy
        self.limit = limit
        self.cache_ttl_s = cache_ttl_s
        self._cache: tuple[float, list[YieldPool]] = (0.0, [])

    def fetch(
        self, symbols: tuple[str, ...]
    ) -> tuple[list[Quote], list[FundingRate], list[YieldPool]]:
        now = time.time()
        cached_at, cached = self._cache
        # Yields move on the scale of hours; hammering the API adds nothing.
        if cached and now - cached_at < self.cache_ttl_s:
            return [], [], cached

        payload: dict[str, Any] = http.get_json(self.URL, timeout=self.timeout) or {}
        rows = payload.get("data") or []
        pools: list[YieldPool] = []

        for row in rows:
            symbol = str(row.get("symbol") or "")
            if not row.get("stablecoin") and not _is_stable_symbol(symbol):
                continue
            tvl = float(row.get("tvlUsd") or 0.0)
            if tvl < self.min_tvl_usd:
                continue
            apy_base = float(row.get("apyBase") or 0.0) / 100.0
            apy_reward = float(row.get("apyReward") or 0.0) / 100.0
            if apy_base + apy_reward <= 0 or apy_base + apy_reward > self.max_apy:
                continue
            pools.append(
                YieldPool(
                    protocol=str(row.get("project") or "?"),
                    chain=str(row.get("chain") or "?"),
                    symbol=symbol.upper(),
                    apy_base=apy_base,
                    apy_reward=apy_reward,
                    tvl_usd=tvl,
                    stablecoin=bool(row.get("stablecoin", _is_stable_symbol(symbol))),
                    il_risk=str(row.get("ilRisk") or "no"),
                    pool_id=str(row.get("pool") or ""),
                    ts=now,
                )
            )

        pools.sort(key=lambda p: p.risk_adjusted_apy, reverse=True)
        pools = pools[: self.limit]
        self._cache = (now, pools)
        log.debug("defillama: %d pools kept of %d", len(pools), len(rows))
        return [], [], pools
