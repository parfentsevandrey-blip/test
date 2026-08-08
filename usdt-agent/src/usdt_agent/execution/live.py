"""Live broker — real orders, real money, deliberately hard to switch on.

Four independent interlocks must *all* be satisfied before a single order can
reach an exchange:

1. ``mode = "live"`` in the config;
2. the environment variable ``USDT_AGENT_LIVE_CONFIRM`` set to the exact string
   ``I_UNDERSTAND_THE_RISK``;
3. API credentials present in the environment for the venue;
4. the strategy has passed the statistical edge gate (enforced by the agent, not
   here) and the order is within ``max_order_usdt``.

Anything missing and the broker refuses the order rather than "helpfully"
falling back to something. Keys are read from the environment only, are never
logged, and only ever sign requests to the venue's own host.

Only spot orders are implemented. Perp/pool legs are refused explicitly instead
of being silently dropped, which would leave a hedge one-legged — the single
most expensive bug this class could have.
"""

from __future__ import annotations

import hashlib
import hmac
import logging
import os
import time
import urllib.parse
from typing import Any

from .. import http
from ..config import ExecutionConfig, api_credentials
from ..models import BPS, Fill, Instrument, MarketSnapshot, Order, Side
from .base import Broker, BrokerError

log = logging.getLogger(__name__)

CONFIRM_ENV = "USDT_AGENT_LIVE_CONFIRM"
CONFIRM_VALUE = "I_UNDERSTAND_THE_RISK"


class LiveTradingBlocked(BrokerError):
    """Raised when an interlock is not satisfied."""


def live_interlocks(venues: tuple[str, ...]) -> list[str]:
    """Return the list of unmet preconditions for live trading (empty == ready)."""
    problems: list[str] = []
    if os.environ.get(CONFIRM_ENV, "") != CONFIRM_VALUE:
        problems.append(f"{CONFIRM_ENV} is not set to {CONFIRM_VALUE}")
    for v in venues:
        key, secret = api_credentials(v)
        if not key or not secret:
            problems.append(f"missing {v.upper()}_API_KEY / {v.upper()}_API_SECRET")
    return problems


class BinanceLiveBroker(Broker):
    """Signed spot order placement on Binance. Market orders, quote-denominated."""

    name = "binance-live"
    is_live = True
    BASE = "https://api.binance.com"

    def __init__(
        self,
        cfg: ExecutionConfig,
        *,
        max_order_usdt: float = 100.0,
        recv_window_ms: int = 5_000,
        dry_run: bool = True,
    ) -> None:
        self.cfg = cfg
        self.max_order_usdt = max_order_usdt
        self.recv_window_ms = recv_window_ms
        self.dry_run = dry_run
        self.key, self.secret = api_credentials("binance")

        problems = live_interlocks(("binance",))
        if problems:
            raise LiveTradingBlocked("live trading blocked: " + "; ".join(problems))
        log.warning(
            "LIVE broker armed (dry_run=%s, max_order=%.2f USDT) — real funds are at risk",
            dry_run, max_order_usdt,
        )

    # -- signing ---------------------------------------------------------
    def _signed_query(self, params: dict[str, Any]) -> str:
        params = {**params, "timestamp": int(time.time() * 1000), "recvWindow": self.recv_window_ms}
        query = urllib.parse.urlencode(params)
        sig = hmac.new(self.secret.encode(), query.encode(), hashlib.sha256).hexdigest()
        return f"{query}&signature={sig}"

    def _post(self, path: str, params: dict[str, Any]) -> Any:
        url = f"{self.BASE}{path}?{self._signed_query(params)}"
        return http.request(
            url, method="POST", headers={"X-MBX-APIKEY": self.key}, timeout=10.0, retries=1
        )

    def _get(self, path: str, params: dict[str, Any] | None = None) -> Any:
        url = f"{self.BASE}{path}?{self._signed_query(params or {})}"
        return http.request(url, method="GET", headers={"X-MBX-APIKEY": self.key}, timeout=10.0)

    # -- account ---------------------------------------------------------
    def balances(self) -> dict[str, float]:
        data = self._get("/api/v3/account") or {}
        return {
            b["asset"]: float(b["free"])
            for b in data.get("balances", [])
            if float(b.get("free", 0)) > 0
        }

    def usdt_balance(self) -> float:
        return self.balances().get("USDT", 0.0)

    # -- execution -------------------------------------------------------
    def execute(self, orders: tuple[Order, ...], snapshot: MarketSnapshot) -> list[Fill]:
        return [self._execute_one(o, snapshot) for o in orders]

    def _execute_one(self, order: Order, snapshot: MarketSnapshot) -> Fill:
        def reject(reason: str) -> Fill:
            log.error("live order refused (%s): %s %s %.2f USDT",
                      reason, order.side.value, order.symbol, order.notional)
            return Fill(order, 0.0, 0.0, 0.0, 0.0, ok=False, reason=reason)

        if order.instrument is not Instrument.SPOT:
            return reject(f"instrument {order.instrument.value} not supported by the live broker")
        if order.venue != "binance":
            return reject(f"venue {order.venue} not supported by this broker")
        if order.notional > self.max_order_usdt:
            return reject(f"notional {order.notional:.2f} exceeds max_order_usdt {self.max_order_usdt:.2f}")
        if order.notional <= 0:
            return reject("zero notional")

        symbol = order.symbol.replace("/", "")
        quote = snapshot.quote(order.venue, order.symbol)
        ref_price = quote.mid if quote else 0.0

        if self.dry_run:
            log.warning("[DRY RUN] would send %s %s for %.2f USDT", order.side.value, symbol, order.notional)
            fee = order.notional * self.cfg.taker_fee_bps * BPS
            qty = order.notional / ref_price if ref_price > 0 else 0.0
            return Fill(order, ref_price, qty, fee, 0.0, reason="dry-run")

        params: dict[str, Any] = {"symbol": symbol, "side": order.side.value.upper(), "type": "MARKET"}
        if order.side is Side.BUY:
            params["quoteOrderQty"] = f"{order.notional:.8f}"
        else:
            if ref_price <= 0:
                return reject("no reference price to size a sell")
            params["quantity"] = f"{order.notional / ref_price:.8f}"

        try:
            resp = self._post("/api/v3/order", params) or {}
        except Exception as e:
            return reject(f"exchange error: {e}")

        executed_qty = float(resp.get("executedQty") or 0.0)
        cummulative = float(resp.get("cummulativeQuoteQty") or 0.0)
        if executed_qty <= 0:
            return reject(f"order not filled (status={resp.get('status')})")

        price = cummulative / executed_qty
        fee = sum(float(f.get("commission") or 0.0) for f in resp.get("fills", []) or [])
        if fee <= 0:
            fee = cummulative * self.cfg.taker_fee_bps * BPS
        slippage = abs(price - ref_price) * executed_qty if ref_price > 0 else 0.0

        log.info("live fill: %s %s qty=%.8f @ %.6f (fee %.6f)",
                 order.side.value, symbol, executed_qty, price, fee)
        return Fill(order, price, executed_qty, fee, slippage, ts=time.time())


def build_broker(
    cfg: ExecutionConfig, *, live: bool, venues: tuple[str, ...], seed: int = 0,
    max_order_usdt: float = 100.0, dry_run: bool = True,
) -> Broker:
    """Factory: paper unless every live interlock is satisfied."""
    from .paper import PaperBroker

    if not live:
        return PaperBroker(cfg, seed=seed)
    problems = live_interlocks(("binance",))
    if problems:
        raise LiveTradingBlocked("live trading blocked: " + "; ".join(problems))
    return BinanceLiveBroker(cfg, max_order_usdt=max_order_usdt, dry_run=dry_run)
