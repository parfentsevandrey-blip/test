"""Public-endpoint feeds for Binance, Bybit and OKX.

Only *public* market data is read here — no keys, no signatures, no account
access. Each venue exposes a single "all book tickers" call, which is one HTTP
round trip for the entire symbol universe instead of one per symbol.
"""

from __future__ import annotations

import logging
import time
from typing import Any

from .. import http
from ..models import FundingRate, Quote, YieldPool
from .base import Feed, from_venue_symbol, to_venue_symbol

log = logging.getLogger(__name__)


def _f(value: Any, default: float = 0.0) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


class BinanceFeed(Feed):
    """Binance spot book tickers + USDⓈ-M perpetual funding."""

    name = "binance"
    SPOT = "https://api.binance.com/api/v3/ticker/bookTicker"
    PERP = "https://fapi.binance.com/fapi/v1/premiumIndex"
    FUNDING_INFO = "https://fapi.binance.com/fapi/v1/fundingInfo"

    def __init__(self, timeout: float = 10.0, with_funding: bool = True) -> None:
        self.timeout = timeout
        self.with_funding = with_funding

    def fetch(
        self, symbols: tuple[str, ...]
    ) -> tuple[list[Quote], list[FundingRate], list[YieldPool]]:
        wanted = set(symbols)
        quotes: list[Quote] = []
        now = time.time()

        for row in http.get_json(self.SPOT, timeout=self.timeout) or []:
            sym = from_venue_symbol(str(row.get("symbol", "")))
            if sym is None or sym not in wanted:
                continue
            bid, ask = _f(row.get("bidPrice")), _f(row.get("askPrice"))
            if bid <= 0 or ask <= 0 or ask < bid:
                continue
            quotes.append(
                Quote(self.name, sym, bid, ask, _f(row.get("bidQty")), _f(row.get("askQty")), now)
            )

        funding: list[FundingRate] = []
        if self.with_funding:
            try:
                for row in http.get_json(self.PERP, timeout=self.timeout) or []:
                    sym = from_venue_symbol(str(row.get("symbol", "")))
                    if sym is None or sym not in wanted:
                        continue
                    funding.append(
                        FundingRate(
                            venue=self.name,
                            symbol=sym,
                            rate=_f(row.get("lastFundingRate")),
                            interval_hours=8.0,
                            mark_price=_f(row.get("markPrice")),
                            next_funding_ts=_f(row.get("nextFundingTime")) / 1000.0,
                            ts=now,
                        )
                    )
            except Exception as e:
                log.debug("binance funding unavailable: %s", e)

        return quotes, funding, []


class BybitFeed(Feed):
    """Bybit v5 spot tickers + linear-perp funding (one call each)."""

    name = "bybit"
    URL = "https://api.bybit.com/v5/market/tickers"

    def __init__(self, timeout: float = 10.0, with_funding: bool = True) -> None:
        self.timeout = timeout
        self.with_funding = with_funding

    def fetch(
        self, symbols: tuple[str, ...]
    ) -> tuple[list[Quote], list[FundingRate], list[YieldPool]]:
        wanted = set(symbols)
        now = time.time()
        quotes: list[Quote] = []

        data = http.get_json(self.URL, params={"category": "spot"}, timeout=self.timeout) or {}
        for row in (data.get("result") or {}).get("list") or []:
            sym = from_venue_symbol(str(row.get("symbol", "")))
            if sym is None or sym not in wanted:
                continue
            bid, ask = _f(row.get("bid1Price")), _f(row.get("ask1Price"))
            if bid <= 0 or ask <= 0 or ask < bid:
                continue
            quotes.append(
                Quote(self.name, sym, bid, ask, _f(row.get("bid1Size")), _f(row.get("ask1Size")), now)
            )

        funding: list[FundingRate] = []
        if self.with_funding:
            try:
                lin = http.get_json(self.URL, params={"category": "linear"}, timeout=self.timeout) or {}
                for row in (lin.get("result") or {}).get("list") or []:
                    sym = from_venue_symbol(str(row.get("symbol", "")))
                    if sym is None or sym not in wanted:
                        continue
                    funding.append(
                        FundingRate(
                            venue=self.name,
                            symbol=sym,
                            rate=_f(row.get("fundingRate")),
                            interval_hours=8.0,
                            mark_price=_f(row.get("markPrice")),
                            next_funding_ts=_f(row.get("nextFundingTime")) / 1000.0,
                            ts=now,
                        )
                    )
            except Exception as e:
                log.debug("bybit funding unavailable: %s", e)

        return quotes, funding, []


class OkxFeed(Feed):
    """OKX v5 spot tickers + swap funding (funding is one call per symbol)."""

    name = "okx"
    TICKERS = "https://www.okx.com/api/v5/market/tickers"
    FUNDING = "https://www.okx.com/api/v5/public/funding-rate"

    def __init__(self, timeout: float = 10.0, with_funding: bool = True, funding_symbols: int = 4) -> None:
        self.timeout = timeout
        self.with_funding = with_funding
        self.funding_symbols = funding_symbols

    def fetch(
        self, symbols: tuple[str, ...]
    ) -> tuple[list[Quote], list[FundingRate], list[YieldPool]]:
        wanted = set(symbols)
        now = time.time()
        quotes: list[Quote] = []

        data = http.get_json(self.TICKERS, params={"instType": "SPOT"}, timeout=self.timeout) or {}
        for row in data.get("data") or []:
            sym = from_venue_symbol(str(row.get("instId", "")))
            if sym is None or sym not in wanted:
                continue
            bid, ask = _f(row.get("bidPx")), _f(row.get("askPx"))
            if bid <= 0 or ask <= 0 or ask < bid:
                continue
            quotes.append(
                Quote(self.name, sym, bid, ask, _f(row.get("bidSz")), _f(row.get("askSz")), now)
            )

        funding: list[FundingRate] = []
        if self.with_funding:
            # OKX has no bulk funding endpoint; cap the fan-out to stay polite.
            for sym in list(symbols)[: self.funding_symbols]:
                inst = f"{to_venue_symbol(sym, '-')}-SWAP"
                try:
                    fr = http.get_json(self.FUNDING, params={"instId": inst}, timeout=self.timeout) or {}
                    for row in fr.get("data") or []:
                        funding.append(
                            FundingRate(
                                venue=self.name,
                                symbol=sym,
                                rate=_f(row.get("fundingRate")),
                                interval_hours=8.0,
                                mark_price=0.0,
                                next_funding_ts=_f(row.get("fundingTime")) / 1000.0,
                                ts=now,
                            )
                        )
                except Exception as e:
                    log.debug("okx funding for %s unavailable: %s", inst, e)

        return quotes, funding, []


VENUE_FEEDS: dict[str, type[Feed]] = {
    "binance": BinanceFeed,
    "bybit": BybitFeed,
    "okx": OkxFeed,
}


def build_venue_feeds(venues: tuple[str, ...], timeout: float = 10.0) -> list[Feed]:
    feeds: list[Feed] = []
    for v in venues:
        cls = VENUE_FEEDS.get(v.lower())
        if cls is None:
            log.warning("unknown venue %r, skipping", v)
            continue
        feeds.append(cls(timeout=timeout))  # type: ignore[call-arg]
    return feeds
