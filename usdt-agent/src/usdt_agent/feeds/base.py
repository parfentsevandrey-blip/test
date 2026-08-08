"""Market-data feed interface plus the composite that fans out across venues.

A feed is anything that can produce part of a :class:`MarketSnapshot`. Feeds are
polled concurrently (one thread each — these are IO-bound HTTP calls) and a feed
that fails is *degraded, not fatal*: its error is recorded on the snapshot and
the agent carries on with whatever else came back. A single flaky exchange must
never take the whole treasury offline.
"""

from __future__ import annotations

import abc
import logging
import time
from concurrent.futures import ThreadPoolExecutor, as_completed

from ..models import FundingRate, MarketSnapshot, Quote, YieldPool

log = logging.getLogger(__name__)


class Feed(abc.ABC):
    """One data source (usually one venue)."""

    name: str = "feed"

    @abc.abstractmethod
    def fetch(self, symbols: tuple[str, ...]) -> tuple[list[Quote], list[FundingRate], list[YieldPool]]:
        """Return whatever this source knows. Any part may be empty."""

    def healthy(self) -> bool:
        try:
            q, f, p = self.fetch(("BTC/USDT",))
            return bool(q or f or p)
        except Exception:
            return False


class CompositeFeed:
    """Polls several feeds concurrently and merges them into one snapshot."""

    def __init__(self, feeds: list[Feed], timeout: float = 15.0) -> None:
        self.feeds = feeds
        self.timeout = timeout

    def snapshot(self, symbols: tuple[str, ...]) -> MarketSnapshot:
        quotes: dict[tuple[str, str], Quote] = {}
        funding: dict[tuple[str, str], FundingRate] = {}
        pools: list[YieldPool] = []
        errors: list[str] = []

        if not self.feeds:
            return MarketSnapshot(ts=time.time(), errors=("no feeds configured",))

        with ThreadPoolExecutor(max_workers=max(1, len(self.feeds))) as pool:
            futures = {pool.submit(self._safe_fetch, f, symbols): f for f in self.feeds}
            for fut in as_completed(futures, timeout=self.timeout + 5):
                feed = futures[fut]
                try:
                    q, fr, yp, err = fut.result()
                except Exception as e:
                    errors.append(f"{feed.name}: {e}")
                    continue
                if err:
                    errors.append(f"{feed.name}: {err}")
                for quote in q:
                    quotes[(quote.venue, quote.symbol)] = quote
                for rate in fr:
                    funding[(rate.venue, rate.symbol)] = rate
                pools.extend(yp)

        # Take the clock from the data, not from the wall. For live feeds these
        # coincide; for the simulator this is what makes a backtest advance in
        # simulated time instead of standing still in real time.
        stamps = [q.ts for q in quotes.values()] + [f.ts for f in funding.values()]
        stamps += [p.ts for p in pools]
        ts = max(stamps) if stamps else time.time()

        return MarketSnapshot(
            ts=ts,
            quotes=quotes,
            funding=funding,
            pools=tuple(pools),
            errors=tuple(errors),
        )

    @staticmethod
    def _safe_fetch(
        feed: Feed, symbols: tuple[str, ...]
    ) -> tuple[list[Quote], list[FundingRate], list[YieldPool], str]:
        try:
            q, f, p = feed.fetch(symbols)
            return q, f, p, ""
        except Exception as e:
            log.warning("feed %s failed: %s", feed.name, e)
            return [], [], [], str(e)


def to_venue_symbol(symbol: str, sep: str = "") -> str:
    """``"BTC/USDT"`` -> ``"BTCUSDT"`` (or ``"BTC-USDT"`` with ``sep='-'``)."""
    return symbol.replace("/", sep)


def from_venue_symbol(raw: str, quote: str = "USDT") -> str | None:
    """``"BTCUSDT"`` -> ``"BTC/USDT"``. ``None`` if it is not a ``quote`` pair."""
    if "-" in raw:
        base, _, q = raw.partition("-")
        return f"{base}/{q}" if q == quote else None
    if raw.endswith(quote) and len(raw) > len(quote):
        return f"{raw[: -len(quote)]}/{quote}"
    return None
