"""The dashboard's HTTP backend: one process-wide agent, many request threads.

Two invariants shape everything below.

**The agent is built once.** A :class:`ServiceChannel` holds its live invoices in
memory, so rebuilding the object graph per request would silently void every
payment instruction already handed to a customer. :class:`AgentContext` therefore
constructs config, ledger, wallet, channels and the :class:`EarningAgent` exactly
once and shares them. Sharing them means the single SQLite connection and those
in-memory invoices are touched by several threads at once, so *every* API handler
runs under one lock — reads included, because a shared sqlite3 connection is not
safe to interleave.

**A request can never take the agent down.** Each handler returns a status and a
payload; the dispatcher owns the try/except, so a dead RPC, a broken discovery
module or a malformed body degrades into a 500 with a truncated message and the
server keeps serving. That is the same rule the collector and the channels follow.

Money is reported the way the rest of the project reports it: ``confirmed_usdt``
is on-chain fact, ``expected_usdt`` is somebody's intention, and they are never
summed into one number here or anywhere the UI can reach.
"""

from __future__ import annotations

import hmac
import json
import logging
import mimetypes
import re
import secrets
import threading
import time
from http.cookies import CookieError, SimpleCookie
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, unquote, urlparse

from ..config import load_config
from ..earn import EarningAgent, build_channels, wallet_from_env_or_config
from ..earn.models import Gig, OrderStatus
from ..ledger import Ledger

log = logging.getLogger(__name__)

#: Static assets live beside this module. Resolved once: it is the fence every
#: static request is checked against.
STATIC_DIR = (Path(__file__).parent / "static").resolve()

#: A dashboard sends small JSON. Anything larger is either a bug or an attempt
#: to make the process allocate on request.
MAX_BODY_BYTES = 64 * 1024

#: Discovery talks to third-party APIs. A browser tab that polls must not turn
#: into a scraper, so rediscovery is rate-limited and the store answers between.
GIG_REFRESH_INTERVAL_S = 60.0

#: /api/state reads chain balances over the network. Collapsing a burst of
#: concurrent pollers (two tabs, a reloading panel) into one refresh keeps the
#: RPC quiet; `generated_at` always reports when the snapshot was really taken.
STATE_CACHE_TTL_S = 5.0

#: Enough history for a sparkline without shipping the whole curve every poll.
EQUITY_POINTS = 500

#: Explicit types for what the UI actually serves — `nosniff` makes a wrong
#: guess fatal (a text/plain module simply will not load), so do not rely on
#: whatever mimetypes was seeded with on this host.
CONTENT_TYPES = {
    ".html": "text/html; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".js": "text/javascript; charset=utf-8",
    ".mjs": "text/javascript; charset=utf-8",
    ".json": "application/json; charset=utf-8",
    ".map": "application/json; charset=utf-8",
    ".svg": "image/svg+xml",
    ".png": "image/png",
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".webp": "image/webp",
    ".ico": "image/x-icon",
    ".woff2": "font/woff2",
    ".txt": "text/plain; charset=utf-8",
}


# --------------------------------------------------------------------------
# Shared state
# --------------------------------------------------------------------------


class AgentContext:
    """The one agent the whole server talks to, plus the lock that guards it.

    Callers must hold :attr:`lock` for anything that touches the store, the
    ledger connection, the wallet or a channel — which is every method here.
    """

    def __init__(self, config_path: str | Path | None = None, db_path: str | None = None) -> None:
        self.cfg = load_config(config_path, db_path=db_path)
        self.lock = threading.Lock()
        #: Burned the first time the launch nonce is redeemed.
        self.bootstrap_used = False

        self.wallet = wallet_from_env_or_config(self.cfg.earn.wallet)
        self.channels = build_channels(
            self.cfg, self.wallet, self.cfg.earn.channel_params(), self.cfg.earn.enabled()
        )
        self.ledger = Ledger(self.cfg.db_path)
        self.agent = EarningAgent(
            self.cfg, self.channels, self.wallet, self.ledger,
            max_open_orders=self.cfg.earn.max_open_orders,
            min_rate_usdt_per_hour=self.cfg.earn.min_rate_usdt_per_hour,
        )
        self.agent.collector.lookback_blocks = self.cfg.earn.lookback_blocks

        self._last_discovery = 0.0
        self._discovery_errors: dict[str, str] = {}
        self._state: dict[str, Any] | None = None
        self._state_ts = 0.0

    @property
    def store(self) -> Any:
        return self.agent.store

    @property
    def services(self) -> Any:
        """The :class:`ServiceChannel`, or ``None`` when it is not enabled."""
        return self.channels.get("services")

    # -- derived views ---------------------------------------------------
    def state(self) -> dict[str, Any]:
        """The dashboard snapshot, from the orchestrator's own summary."""
        now = time.time()
        if self._state is not None and now - self._state_ts < STATE_CACHE_TTL_S:
            return self._state

        summary = self.agent.summary()
        store = summary.get("store") or {}
        ladder = summary.get("ladder") or {}
        ok, message = self.ledger.verify()

        self._state = {
            "generated_at": now,
            # Two fields, never one. `confirmed_usdt` is what the chain proved;
            # `expected_usdt` is pipeline and is not income.
            "treasury": {
                "confirmed_usdt": float(summary.get("treasury_usdt") or 0.0),
                "expected_usdt": float(summary.get("expected_usdt") or 0.0),
                "hours_spent": float(store.get("hours_spent") or 0.0),
            },
            "wallet": summary.get("wallet") or {},
            "ladder": {
                "stage": str(ladder.get("stage") or ""),
                "completed": int(ladder.get("completed") or 0),
                "total": int(ladder.get("total") or 0),
                "stages": ladder.get("stages") or [],
            },
            "channels": summary.get("channels") or {},
            "counts": {
                "gigs": int(store.get("gigs") or 0),
                "open_orders": int(store.get("open_orders") or 0),
                "pending_approvals": int(store.get("pending_approvals") or 0),
                "transfers_seen": int(store.get("transfers_seen") or 0),
            },
            "ledger": {"ok": bool(ok), "message": message},
        }
        self._state_ts = now
        return self._state

    def invalidate_state(self) -> None:
        """Drop the snapshot so the next poll shows what a mutation just did."""
        self._state = None

    def refresh_gigs(self, min_interval_s: float = GIG_REFRESH_INTERVAL_S) -> dict[str, str]:
        """Rediscover gigs at most once per interval; returns per-channel errors.

        Between refreshes the stored gigs are still the answer — discovery is a
        network walk across several third-party APIs, and a polling browser must
        not be able to trigger it on every keystroke.
        """
        if time.time() - self._last_discovery < min_interval_s:
            return dict(self._discovery_errors)
        self._last_discovery = time.time()
        try:
            gigs, errors = self.agent.discover()
            self.store.upsert_gigs(gigs)
        except Exception as e:  # a dead channel must not empty the gig list
            log.warning("web: discovery failed: %s", str(e)[:160])
            errors = {"discovery": str(e)[:160]}
        self._discovery_errors = errors
        return dict(errors)

    def close(self) -> None:
        self.ledger.close()


# --------------------------------------------------------------------------
# Routing
# --------------------------------------------------------------------------

#: ``(method, pattern) -> handler name``. Resolved to a bound method at dispatch.
ROUTES: tuple[tuple[str, re.Pattern[str], str], ...] = (
    ("GET", re.compile(r"^/api/state$"), "api_state"),
    ("GET", re.compile(r"^/api/gigs$"), "api_gigs"),
    ("POST", re.compile(r"^/api/gigs/(?P<gig_id>[A-Za-z0-9_-]{1,64})/take$"), "api_take_gig"),
    ("GET", re.compile(r"^/api/approvals$"), "api_approvals"),
    ("POST", re.compile(r"^/api/approvals/(?P<approval_id>[A-Za-z0-9_-]{1,64})/decide$"), "api_decide"),
    ("GET", re.compile(r"^/api/catalogue$"), "api_catalogue"),
    ("POST", re.compile(r"^/api/invoices$"), "api_create_invoice"),
    ("GET", re.compile(r"^/api/invoices$"), "api_invoices"),
    ("POST", re.compile(r"^/api/collect$"), "api_collect"),
    ("GET", re.compile(r"^/api/trading$"), "api_trading"),
    ("GET", re.compile(r"^/api/ledger$"), "api_ledger"),
    ("GET", re.compile(r"^/api/discovery$"), "api_discovery"),
    ("POST", re.compile(r"^/api/discovery/probe$"), "api_probe"),
)


def _int_param(query: dict[str, list[str]], name: str, default: int, low: int, high: int) -> int:
    """A query integer, clamped. Junk falls back to the default rather than 400."""
    try:
        value = int((query.get(name) or [""])[0])
    except (TypeError, ValueError):
        return default
    return max(low, min(high, value))


def _str_param(query: dict[str, list[str]], name: str, limit: int = 64) -> str:
    return (query.get(name) or [""])[0].strip()[:limit]


def _content_type(path: Path) -> str:
    suffix = path.suffix.lower()
    if suffix in CONTENT_TYPES:
        return CONTENT_TYPES[suffix]
    guessed, _ = mimetypes.guess_type(path.name)
    return guessed or "application/octet-stream"


def _gig_from_row(row: Any) -> Gig:
    """Rebuild a :class:`Gig` from its stored row.

    ``Gig.id`` is derived from channel/source/external_id, all three of which are
    columns, so the reconstruction keeps the identity the row was stored under.
    """
    try:
        meta = json.loads(row["meta"] or "{}")
    except (TypeError, ValueError):
        meta = {}
    return Gig(
        channel=row["channel"],
        external_id=row["external_id"],
        title=row["title"],
        url=row["url"],
        reward_usdt=float(row["reward_usdt"] or 0.0),
        effort_hours=float(row["effort_hours"] or 0.0),
        payout_probability=float(row["payout_probability"] or 0.0),
        deadline_ts=float(row["deadline_ts"] or 0.0),
        source=row["source"],
        meta=meta if isinstance(meta, dict) else {},
    )


SESSION_COOKIE = "usdt_agent_session"


class ApiHandler(BaseHTTPRequestHandler):
    """Every route in the UI contract. Bound to a context and a token by :func:`serve`."""

    server_version = "usdt-agent-web/1.0"
    protocol_version = "HTTP/1.1"  # safe: every response below sends Content-Length

    ctx: AgentContext
    token: str
    #: Single-use nonce that exchanges the launch URL for a session cookie.
    bootstrap: str = ""
    #: Host:port spellings this server actually answers to (anti DNS-rebinding).
    origins: frozenset[str] = frozenset()

    #: A half-open connection must not pin a worker thread forever. StreamRequest
    #: Handler applies this to the socket, and a timeout closes the connection.
    timeout = 15

    # -- plumbing --------------------------------------------------------
    def _safe(self, message: str) -> str:
        """Truncate an error, and make sure the token never rides out inside one."""
        cleaned = message.replace(self.token, "***") if self.token else message
        return cleaned[:300]

    def log_message(self, fmt: str, *args: Any) -> None:
        # Headers are never logged, and the line is scrubbed in case a token
        # ever reached a URL: this file must not be a place tokens leak.
        try:
            line = fmt % args
        except (TypeError, ValueError):
            line = fmt
        log.debug("web %s", self._safe(line))

    def _send(
        self, status: int, body: bytes, content_type: str,
        extra_headers: dict[str, str] | None = None,
    ) -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        for key, value in (extra_headers or {}).items():
            self.send_header(key, value)
        # No CORS headers, on purpose: this surface grants approval and
        # invoicing rights, so no other origin may read anything from it.
        self.send_header("X-Frame-Options", "DENY")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        # Nothing served here may sit in a disk cache.
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        if self.command == "HEAD":
            return
        try:
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError):
            self.close_connection = True  # the tab went away mid-response

    def _json(self, status: int, payload: dict[str, Any]) -> None:
        try:
            body = json.dumps(payload, default=str).encode()
        except (TypeError, ValueError) as e:
            log.warning("web: unserializable response: %s", str(e)[:120])
            status, body = 500, b'{"error": "unserializable response"}'
        self._send(status, body, "application/json; charset=utf-8")

    def _cookie_token(self) -> str:
        raw = self.headers.get("Cookie") or ""
        if not raw:
            return ""
        try:
            jar = SimpleCookie()
            jar.load(raw)
        except (CookieError, AttributeError):
            return ""
        morsel = jar.get(SESSION_COOKIE)
        return morsel.value if morsel is not None else ""

    def _authorized(self) -> bool:
        """Accept the header (scripts, curl) or the session cookie (the browser).

        The cookie is ``HttpOnly`` so page JavaScript — including anything an
        XSS gets to run — cannot read it, and ``SameSite=Strict`` so no
        cross-site navigation, form or fetch can carry it. The header path stays
        for command-line use, where there is no cookie jar to protect.
        """
        for supplied in (self.headers.get("X-Agent-Token") or "", self._cookie_token()):
            if not supplied:
                continue
            try:
                if hmac.compare_digest(supplied, self.token):
                    return True
            except TypeError:  # non-ASCII value: not our token, by definition
                continue
        return False

    def _recognised_origin(self) -> bool:
        """Reject DNS-rebinding and stray cross-origin traffic before routing.

        A remote page that points a name it controls at 127.0.0.1 reaches this
        server *same-origin*, so the browser hands it every response body. The
        ``Host`` header is what separates that from a genuine local visit, so it
        is checked against the addresses actually bound. An ``Origin`` that is
        present must match too — a same-origin fetch never sends a foreign one.
        """
        if not self.origins:
            return True
        host = (self.headers.get("Host") or "").strip().lower()
        if host not in self.origins:
            return False
        origin = self.headers.get("Origin")
        return not (origin and urlparse(origin).netloc.lower() not in self.origins)

    def _read_body(self) -> tuple[dict[str, Any], tuple[int, str] | None]:
        """``(payload, error)``. Oversized or malformed bodies never reach a handler."""
        try:
            length = int(self.headers.get("Content-Length") or 0)
        except ValueError:
            return {}, (400, "bad Content-Length")
        if length < 0:
            return {}, (400, "bad Content-Length")
        if length > MAX_BODY_BYTES:
            # Refuse without draining: reading it would be doing the attacker's
            # allocation for them. The connection cannot be reused after that.
            self.close_connection = True
            return {}, (413, "request body too large")
        if length == 0:
            return {}, None
        try:
            payload = json.loads(self.rfile.read(length))
        except (json.JSONDecodeError, UnicodeDecodeError, ValueError):
            return {}, (400, "invalid JSON body")
        if not isinstance(payload, dict):
            return {}, (400, "JSON object expected")
        return payload, None

    # -- dispatch --------------------------------------------------------
    def do_GET(self) -> None:
        self._handle("GET")

    def do_POST(self) -> None:
        self._handle("POST")

    def _handle(self, method: str) -> None:
        if not self._recognised_origin():
            # 421 is the honest code: this server does not answer for that name.
            return self._json(421, {"error": "unrecognised Host"})
        try:
            parsed = urlparse(self.path)
            path = unquote(parsed.path)
            query = parse_qs(parsed.query)
        except (ValueError, UnicodeDecodeError) as e:
            return self._json(400, {"error": self._safe(f"malformed request line: {e}")})

        if method == "GET":
            if path in ("/", "/index.html"):
                return self._serve_index(query)
            if path == "/healthz":
                return self._json(200, {"ok": True, "ts": time.time()})
            if path.startswith("/static/"):
                return self._serve_static(path[len("/static/"):])

        if not path.startswith("/api/"):
            return self._json(404, {"error": "not found"})

        # Auth gates the whole API surface, including paths that do not exist:
        # an unauthenticated caller learns nothing about the route table.
        if not self._authorized():
            return self._json(401, {"error": "unauthorized"})

        body, body_error = self._read_body()
        if body_error is not None:
            return self._json(body_error[0], {"error": body_error[1]})

        for verb, pattern, name in ROUTES:
            match = pattern.match(path)
            if match is None or verb != method:
                continue
            try:
                # One lock for reads and writes alike: the sqlite connection and
                # the in-memory invoices are shared, and this server is threaded.
                with self.ctx.lock:
                    status, payload = getattr(self, name)(match, query, body)
            except (Exception, RecursionError) as e:
                log.exception("web: %s %s failed", method, path)
                return self._json(500, {"error": self._safe(str(e) or e.__class__.__name__)})
            return self._json(status, payload)

        return self._json(404, {"error": "not found"})

    # -- static ----------------------------------------------------------
    def _serve_index(self, query: dict[str, list[str]]) -> None:
        """Serve the page — and hand over a session only against the launch nonce.

        Injecting the token into an unauthenticated page was the original design
        and it was wrong: any process on the machine could ``curl /`` and read
        it, which is full API control including approving work in your name. So
        the markup now contains no secret at all. The CLI prints a single-use
        launch URL; presenting its nonce exchanges it for an HttpOnly,
        SameSite=Strict cookie and burns the nonce. Reloading the page keeps
        working because the cookie, not the document, carries the session.
        """
        supplied = (query.get("k") or [""])[0]
        if supplied and self.bootstrap:
            with self.ctx.lock:
                unused = not self.ctx.bootstrap_used
                if unused:
                    try:
                        matched = hmac.compare_digest(supplied, self.bootstrap)
                    except TypeError:
                        matched = False
                    if matched:
                        self.ctx.bootstrap_used = True
            if unused and matched:
                cookie = f"{SESSION_COOKIE}={self.token}; Path=/; HttpOnly; SameSite=Strict"
                # Redirect so the nonce leaves the address bar and the browser
                # history immediately after it has been spent.
                return self._send(303, b"", "text/plain; charset=utf-8",
                                  {"Location": "/", "Set-Cookie": cookie})

        try:
            markup = (STATIC_DIR / "index.html").read_text(encoding="utf-8")
        except OSError:
            return self._send(
                404, b"dashboard not installed: web/static/index.html is missing\n",
                "text/plain; charset=utf-8",
            )
        return self._send(200, self._blank_token_placeholder(markup).encode(),
                          "text/html; charset=utf-8")

    @staticmethod
    def _blank_token_placeholder(markup: str) -> str:
        """Empty the placeholder tag so the page never reads ``__AGENT_TOKEN__``.

        The tag stays (the UI keys off its presence) but carries nothing; the
        session lives in an HttpOnly cookie the document cannot see.
        """
        return re.sub(
            r"<meta\s+name=[\"']agent-token[\"'][^>]*>",
            '<meta name="agent-token" content="">',
            markup, count=1, flags=re.IGNORECASE,
        )

    def _serve_static(self, name: str) -> None:
        missing = (404, b"not found\n", "text/plain; charset=utf-8")
        if not name or "\x00" in name:
            return self._send(*missing)
        target = (STATIC_DIR / name).resolve()
        # resolve() collapses `..` and follows symlinks *before* the check, so
        # containment is decided on the real path. String filtering would not be
        # enough: `%2e%2e/`, a symlink out of the tree and `/static//etc/passwd`
        # all end up here as ordinary absolute paths.
        if not target.is_relative_to(STATIC_DIR) or not target.is_file():
            return self._send(*missing)
        try:
            body = target.read_bytes()
        except OSError:
            return self._send(*missing)
        return self._send(200, body, _content_type(target))

    # -- API: state ------------------------------------------------------
    def api_state(self, match: re.Match[str], query: dict[str, list[str]],
                  body: dict[str, Any]) -> tuple[int, dict[str, Any]]:
        return 200, self.ctx.state()

    # -- API: gigs -------------------------------------------------------
    def api_gigs(self, match: re.Match[str], query: dict[str, list[str]],
                 body: dict[str, Any]) -> tuple[int, dict[str, Any]]:
        limit = _int_param(query, "limit", 50, 1, 500)
        channel = _str_param(query, "channel")
        errors = self.ctx.refresh_gigs()
        gigs = self.ctx.store.top_gigs(limit=limit, channel=channel or None)
        return 200, {"gigs": gigs, "errors": errors}

    def api_take_gig(self, match: re.Match[str], query: dict[str, list[str]],
                     body: dict[str, Any]) -> tuple[int, dict[str, Any]]:
        gig_id = match.group("gig_id")
        row = self.ctx.store.conn.execute("SELECT * FROM gigs WHERE id = ?", (gig_id,)).fetchone()
        if row is None:
            return 404, {"error": "unknown gig"}
        channel = self.ctx.channels.get(row["channel"])
        if channel is None:
            return 503, {"error": f"channel {row['channel']!r} is not enabled"}

        gig = _gig_from_row(row)
        # Taking the same gig twice is harmless: both the order id and the
        # approval id are derived from the gig, so the writes are upserts.
        order = channel.execute(channel.plan(gig))
        self.ctx.store.save_order(order)

        approval_id: str | None = None
        if order.status is OrderStatus.AWAITING_APPROVAL:
            # Anything that speaks to a third party in your name stops here.
            approval_id = self.ctx.store.request_approval(
                kind="work_order",
                title=f"[{gig.channel}] {gig.title[:80]}",
                detail=(
                    f"{gig.url}\n"
                    f"reward {gig.reward_usdt:.2f} USDT · est {gig.effort_hours:.1f} h · "
                    f"{gig.usdt_per_hour:.0f} USDT/h · payout odds {gig.payout_probability:.0%}\n"
                    "plan:\n  - " + "\n  - ".join(order.plan)
                ),
                subject_id=order.id,
                channel=gig.channel,
            )
        self.ctx.invalidate_state()
        return 200, {
            "order": {
                "id": order.id,
                "title": order.title,
                "plan": list(order.plan),
                "status": order.status.value,
                "reward_usdt": order.reward_usdt,
                "estimated_hours": order.estimated_hours,
                "autonomy": order.autonomy.value,
            },
            "approval_id": approval_id,
        }

    # -- API: approvals --------------------------------------------------
    def api_approvals(self, match: re.Match[str], query: dict[str, list[str]],
                      body: dict[str, Any]) -> tuple[int, dict[str, Any]]:
        return 200, {"approvals": self.ctx.store.pending_approvals()}

    def api_decide(self, match: re.Match[str], query: dict[str, list[str]],
                   body: dict[str, Any]) -> tuple[int, dict[str, Any]]:
        approval_id = match.group("approval_id")
        approved = bool(body.get("approved", False))
        note = str(body.get("note") or "")[:500]

        if not self.ctx.store.decide(approval_id, approved, note):
            return 404, {"error": "unknown or already decided"}
        # Same follow-through as the CLI: a decision that leaves the order
        # sitting in `awaiting_approval` would be a queue that decides nothing.
        row = self.ctx.store.conn.execute(
            "SELECT subject_id FROM approvals WHERE id = ?", (approval_id,)
        ).fetchone()
        if row and row["subject_id"]:
            self.ctx.store.set_order_status(
                row["subject_id"],
                OrderStatus.SUBMITTED if approved else OrderStatus.ABANDONED,
                note,
            )
        self.ctx.invalidate_state()
        return 200, {"ok": True, "approved": approved}

    # -- API: the storefront ---------------------------------------------
    def api_catalogue(self, match: re.Match[str], query: dict[str, list[str]],
                      body: dict[str, Any]) -> tuple[int, dict[str, Any]]:
        services = self.ctx.services
        if services is None:
            return 200, {"catalogue": [], "receiving": {"chain": "", "address": ""}}
        chain, address = services.receiving_address()
        return 200, {
            "catalogue": services.catalogue(),
            "receiving": {"chain": chain, "address": address},
        }

    def api_create_invoice(self, match: re.Match[str], query: dict[str, list[str]],
                           body: dict[str, Any]) -> tuple[int, dict[str, Any]]:
        services = self.ctx.services
        if services is None:
            return 503, {"error": "services channel is not enabled"}
        try:
            invoice = services.create_invoice(str(body.get("sku") or ""), str(body.get("ref") or "")[:64])
        except KeyError:
            return 404, {"error": "unknown sku"}
        except RuntimeError:
            # No address means no way to be paid — a configuration fact, not a
            # bad request. The exact reason stays in the channel's own message.
            return 503, {"error": "no receiving address"}
        self.ctx.invalidate_state()
        return 200, {"invoice": invoice.to_dict()}

    def api_invoices(self, match: re.Match[str], query: dict[str, list[str]],
                     body: dict[str, Any]) -> tuple[int, dict[str, Any]]:
        services = self.ctx.services
        if services is None:
            return 200, {"invoices": []}
        newest_first = sorted(services.invoices.values(), key=lambda i: i.created_ts, reverse=True)
        return 200, {"invoices": [invoice.to_dict() for invoice in newest_first]}

    # -- API: money ------------------------------------------------------
    def api_collect(self, match: re.Match[str], query: dict[str, list[str]],
                    body: dict[str, Any]) -> tuple[int, dict[str, Any]]:
        # Same order as `usdt-agent earn collect`: refresh what the channels
        # believe they are owed, then reconcile against the chain. Skipping the
        # first step would leave the pipeline at zero while invoices are live,
        # which is exactly the number the UI contrasts confirmed income against.
        self.ctx.agent.register_expectations()
        result = self.ctx.agent.collect()
        self.ctx.invalidate_state()
        return 200, {
            "confirmed_usdt": result.confirmed_usdt,
            "new_transfers": result.new_transfers,
            "matched": result.matched,
            "unattributed_usdt": result.unattributed_usdt,
            "expired": result.expired,
            "delta_detected": result.delta_detected,
            "errors": result.errors,
            "balances": result.balances,
            "baselined": result.baselined,
            # Confirmed only, straight from the store — never a running estimate.
            "treasury_usdt": self.ctx.agent.treasury_usdt,
        }

    def api_trading(self, match: re.Match[str], query: dict[str, list[str]],
                    body: dict[str, Any]) -> tuple[int, dict[str, Any]]:
        return 200, {
            "strategies": self.ctx.ledger.strategy_stats(),
            "equity": [[ts, equity] for ts, equity in self.ctx.ledger.equity_curve(EQUITY_POINTS)],
            "totals": self.ctx.ledger.totals(),
        }

    def api_ledger(self, match: re.Match[str], query: dict[str, list[str]],
                   body: dict[str, Any]) -> tuple[int, dict[str, Any]]:
        limit = _int_param(query, "limit", 50, 1, 500)
        ok, message = self.ctx.ledger.verify()
        events = [
            {"seq": e["seq"], "ts": e["ts"], "kind": e["kind"], "payload": e["payload"]}
            for e in self.ctx.ledger.events(limit=limit)
        ]
        return 200, {"events": events, "integrity": {"ok": bool(ok), "message": message}}

    # -- API: discovery --------------------------------------------------
    def _registry(self) -> tuple[Any, str]:
        """Load the discovery registry lazily; it is optional, the dashboard is not.

        Imported inside the handler so a module that is missing, half-written or
        raising at import time costs exactly one panel instead of the whole UI.
        """
        try:
            from ..earn.discovery import DiscoveryRegistry
        except Exception as e:
            return None, self._safe(f"discovery unavailable: {e}")
        try:
            try:
                registry = DiscoveryRegistry()
            except TypeError:
                registry = DiscoveryRegistry(self.ctx.cfg)  # config-taking signature
        except Exception as e:
            return None, self._safe(f"discovery unavailable: {e}")
        return registry, ""

    def api_discovery(self, match: re.Match[str], query: dict[str, list[str]],
                      body: dict[str, Any]) -> tuple[int, dict[str, Any]]:
        registry, error = self._registry()
        if registry is None:
            return 200, {"sources": [], "error": error}
        try:
            return 200, {"sources": [probe.to_dict() for probe in registry.known()]}
        except Exception as e:
            return 200, {"sources": [], "error": self._safe(str(e))}

    def api_probe(self, match: re.Match[str], query: dict[str, list[str]],
                  body: dict[str, Any]) -> tuple[int, dict[str, Any]]:
        url = str(body.get("url") or "").strip()[:2048]
        if not url:
            return 400, {"error": "url is required"}
        if not url.lower().startswith(("http://", "https://")):
            return 400, {"error": "url must start with http:// or https://"}
        registry, error = self._registry()
        if registry is None:
            return 503, {"error": error}
        return 200, {"probe": registry.probe(url).to_dict()}


# --------------------------------------------------------------------------
# Serving
# --------------------------------------------------------------------------


def serve(
    config_path: str | Path | None = None,
    db_path: str | None = None,
    host: str = "127.0.0.1",
    port: int = 8500,
    token: str | None = None,
) -> tuple[ThreadingHTTPServer, str]:
    """Build the agent once, bind the server, hand both back.

    The caller owns the loop (``httpd.serve_forever()``) and the shutdown; the
    context is attached to the server so it can close the ledger afterwards.
    Loopback by default — this surface approves work and issues payment
    instructions, so publishing it is a decision, never an accident.
    """
    ctx = AgentContext(config_path, db_path)
    secret = token or secrets.token_urlsafe(24)
    nonce = secrets.token_urlsafe(18)

    # Bind first, so the allowed-origin set can name the port actually taken
    # when the caller asked for an ephemeral one.
    probe = ThreadingHTTPServer((host, port), ApiHandler)
    bound_port = probe.server_address[1]
    probe.server_close()

    origins = frozenset(
        f"{name}:{bound_port}" for name in (host.lower(), "127.0.0.1", "localhost", "[::1]")
    ) | ({f"{host.lower()}"} if bound_port in (80, 443) else set())

    handler = type("BoundApiHandler", (ApiHandler,), {
        "ctx": ctx, "token": secret, "bootstrap": nonce, "origins": origins,
    })
    httpd = ThreadingHTTPServer((host, bound_port), handler)
    httpd.agent_context = ctx  # type: ignore[attr-defined]

    # Printed once, here, and nowhere else: not the log, not a response body,
    # and — since the token disclosure fix — not the served HTML either.
    print(f"  dashboard     http://{host}:{bound_port}/?k={nonce}")
    print("                (single-use link: it swaps the nonce for a session cookie)")
    print(f"  access token  {secret}")
    print("                (for curl/scripts: send it as X-Agent-Token)")
    if host not in ("127.0.0.1", "localhost", "::1"):
        print("  WARNING: bound to a non-loopback address — put TLS in front of it.")
    log.info("dashboard on http://%s:%d (token issued at startup, not logged)", host, bound_port)
    return httpd, secret
