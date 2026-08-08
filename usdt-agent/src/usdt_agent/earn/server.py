"""A tiny HTTP storefront that sells the service catalogue for USDT.

Implements the HTTP **402 Payment Required** flow with on-chain settlement and
no custody:

    GET  /                    → the catalogue
    POST /order/<sku>         → 402 + "send exactly N.NNNN USDT to <address>"
    GET  /order/<invoice_id>  → 402 while unpaid, 200 + deliverable once settled
    GET  /health              → liveness

Bound to localhost by default. Exposing it publicly is a deliberate act: put it
behind a reverse proxy with TLS, because the payment instructions it returns are
exactly the thing an attacker would want to rewrite in flight.

Note what the server never has: a private key, a database of customer funds, or
the ability to move anything. It watches an address and hands over a file.
"""

from __future__ import annotations

import json
import logging
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any
from urllib.parse import urlparse

from .channels.services import ServiceChannel
from .collector import Collector

log = logging.getLogger(__name__)


class StorefrontHandler(BaseHTTPRequestHandler):
    """Request handler bound to a :class:`ServiceChannel` and a collector."""

    server_version = "usdt-agent-storefront/1.0"
    channel: ServiceChannel
    collector: Collector | None = None
    _last_poll: float = 0.0
    _poll_lock = threading.Lock()

    # -- helpers ---------------------------------------------------------
    def _json(self, status: int, payload: dict[str, Any], extra_headers: dict[str, str] | None = None) -> None:
        body = json.dumps(payload, indent=2).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        for key, value in (extra_headers or {}).items():
            self.send_header(key, value)
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt: str, *args: Any) -> None:
        log.info("storefront %s", fmt % args)

    def _poll_chain(self, min_interval_s: float = 10.0) -> None:
        """Reconcile on demand, rate-limited so a refresh loop cannot DoS the RPC."""
        if self.collector is None:
            return
        with StorefrontHandler._poll_lock:
            if time.time() - StorefrontHandler._last_poll < min_interval_s:
                return
            StorefrontHandler._last_poll = time.time()
        try:
            self.collector.collect()
        except Exception as e:
            log.warning("storefront: collection failed: %s", str(e)[:120])

    # -- routes ----------------------------------------------------------
    def do_GET(self) -> None:
        path = urlparse(self.path).path.rstrip("/") or "/"

        if path == "/health":
            return self._json(200, {"ok": True, "ts": time.time()})

        if path == "/":
            chain, _address = self.channel.receiving_address()
            return self._json(200, {
                "service": "usdt-agent storefront",
                "payment": {"asset": "USDT", "chain": chain, "settlement": "on-chain, no custody"},
                "catalogue": self.channel.catalogue(),
                "how_to_buy": "POST /order/<sku> to get payment instructions",
            })

        if path.startswith("/order/"):
            invoice_id = path.rsplit("/", 1)[-1]
            invoice = self.channel.invoices.get(invoice_id)
            if invoice is None:
                return self._json(404, {"error": "unknown invoice"})

            if invoice.status != "paid":
                self._poll_chain()
                invoice = self.channel.invoices.get(invoice_id, invoice)

            if invoice.status == "paid":
                return self._json(200, {
                    "invoice": invoice.to_dict(),
                    "tx_hash": invoice.tx_hash,
                    "deliverable": json.loads(self.channel.deliver(invoice)),
                })
            if invoice.is_expired:
                return self._json(410, {"invoice": invoice.to_dict(), "error": "invoice expired"})
            return self._json(402, {"invoice": invoice.to_dict(), "status": "awaiting payment"})

        return self._json(404, {"error": "not found"})

    def do_POST(self) -> None:
        path = urlparse(self.path).path.rstrip("/")
        if not path.startswith("/order/"):
            return self._json(404, {"error": "not found"})

        sku = path.rsplit("/", 1)[-1]
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length) if 0 < length < 65_536 else b""
        try:
            payload = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            payload = {}

        try:
            invoice = self.channel.create_invoice(sku, str(payload.get("ref", ""))[:64])
        except KeyError:
            return self._json(404, {"error": f"unknown sku {sku!r}",
                                    "catalogue": self.channel.catalogue()})
        except RuntimeError as e:
            return self._json(503, {"error": str(e)})

        return self._json(402, {
            "invoice": invoice.to_dict(),
            "poll": f"/order/{invoice.invoice_id}",
        }, {"Retry-After": "30"})


def serve(
    channel: ServiceChannel,
    collector: Collector | None = None,
    host: str = "127.0.0.1",
    port: int = 8402,
) -> ThreadingHTTPServer:
    """Start the storefront. Returns the server so callers can shut it down."""
    handler = type("BoundStorefront", (StorefrontHandler,), {
        "channel": channel, "collector": collector,
    })
    httpd = ThreadingHTTPServer((host, port), handler)
    chain, address = channel.receiving_address()
    log.info("storefront on http://%s:%d — payments to %s (%s)", host, port, address or "?", chain or "?")
    return httpd
