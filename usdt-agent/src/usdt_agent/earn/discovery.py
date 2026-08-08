"""Turning an undocumented public JSON endpoint into a configured Gig source.

Every gig platform is a single-page app, and every single-page app talks to a
JSON API that nobody wrote down. Today, adding a revenue source means writing a
parser: a new module, a new deploy, a new thing to maintain. This module removes
that step — probe a candidate endpoint once, infer its shape, propose a mapping
onto :class:`~usdt_agent.earn.models.Gig`, and from then on the source lives in
config instead of in code.

The invariant this file protects is not about money, it is about **consent**:

    The agent may read only what a site publishes to everyone, at the pace the
    site asks for, and a control that says "no" is final.

That is implemented, not merely intended. There is no parameter that accepts a
cookie, a token or a header, so authenticated access is unreachable from here.
``robots.txt`` is fetched and obeyed. Requests to a host are spaced by a token
bucket that lives at *module* scope, so constructing a fresh registry does not
hand you a fresh allowance. Above all: a ``401``, a ``403``, a ``429`` or an
anti-bot interstitial ends the probe permanently for that host — there is no
retry path, no bypass path, and deliberately no code to write one into. Probing
is reconnaissance of a public document, and the moment a site says otherwise it
stops being that.

Nothing here earns anything. Discovery produces *candidates*; the collector is
still the only component allowed to say money arrived.
"""

from __future__ import annotations

import contextlib
import http.client
import ipaddress
import json
import logging
import re
import socket
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import urllib.robotparser
from collections import deque
from dataclasses import dataclass, field
from datetime import UTC, datetime
from enum import StrEnum
from typing import Any

from ..http import USER_AGENT as _PROJECT_UA
from ..http import ssl_context
from .models import Gig, parse_reward

log = logging.getLogger(__name__)

# --------------------------------------------------------------------------
# Limits. All of them are ceilings, none of them are configurable per call:
# a guardrail with a keyword argument is a guardrail with an off switch.
# --------------------------------------------------------------------------

#: Truthful and identifying. Never a browser string, never rotated — a site that
#: wants to block this agent must be able to name it in its own robots.txt.
USER_AGENT = f"{_PROJECT_UA} source-discovery"

MIN_INTERVAL_S = 2.0            # one request per host per two seconds
MAX_WAIT_S = 60.0               # refuse rather than sleep longer than this
MAX_PROBES_PER_RUN = 24         # hard ceiling on probes for the whole process
MAX_BODY_BYTES = 2 * 1024 * 1024
MAX_REDIRECTS = 2
TIMEOUT_S = 12.0
MAX_ITEMS_PER_SOURCE = 200
MAX_SCHEMA_LEAVES = 400

#: Conservative stand-ins for an unverified source. A gig from an endpoint the
#: agent taught itself to read is a weaker signal than one from a hand-written
#: channel, and it is priced that way rather than flattered.
DEFAULT_EFFORT_HOURS = 1.0
UNVERIFIED_PAYOUT_PROBABILITY = 0.2

#: Built once per request from an immutable pair list. There is no seam here for
#: a caller to add ``Authorization`` or ``Cookie`` to, which is the point.
_HEADER_PAIRS: tuple[tuple[str, str], ...] = (
    ("User-Agent", USER_AGENT),
    ("Accept", "application/json, text/plain;q=0.5"),
    # Uncompressed on purpose: MAX_BODY_BYTES is only an honest ceiling if the
    # bytes we count are the bytes we keep. A gzip bomb expands past it.
    ("Accept-Encoding", "identity"),
)

#: Words that mark a query parameter as a credential. Matched against the
#: *tokens* of a parameter name rather than the whole name, because an exact
#: blocklist is a game of naming whack-a-mole: it catches ``api_key`` and misses
#: ``subscriptionKey``, ``oauth_token``, ``refresh_token`` and ``x-jwt``.
_CREDENTIAL_TOKENS = frozenset({
    "apikey", "auth", "authorization", "bearer", "credential", "credentials",
    "hmac", "jwt", "key", "oauth", "passwd", "password", "pwd", "secret",
    "session", "sessionid", "sid", "sig", "signature", "signed", "token",
})

#: Markers of an anti-bot control. Seeing one ends the probe; it never starts a
#: workaround. Body markers are only consulted when the response is not
#: successful JSON, so a bounty titled "fix the captcha widget" is not a block.
_CHALLENGE_MARKERS = (
    "captcha", "recaptcha", "hcaptcha", "are you human", "are you a human",
    "verify you are human", "checking your browser", "just a moment",
    "attention required", "cf-challenge", "challenge-platform", "cf_chl_",
    "__cf_chl", "ddos protection", "enable javascript and cookies to continue",
    "bot detection", "access denied",
)
_CHALLENGE_HEADERS = ("cf-mitigated", "cf-chl-bypass", "x-datadome", "x-px-block")


class ProbeStatus(StrEnum):
    """Outcome of one probe. ``BLOCKED`` is terminal, by design."""

    OK = "ok"
    BLOCKED = "blocked"            # an access control said no — never retried
    DISALLOWED = "disallowed"      # robots.txt said no
    UNREACHABLE = "unreachable"    # dead host, timeout, unexpected status
    NOT_JSON = "not_json"
    REFUSED = "refused"            # our own guards said no; nothing was sent


# --------------------------------------------------------------------------
# Per-host pacing
# --------------------------------------------------------------------------


def normalise_host(host: str) -> str:
    """Bucket key for pacing and blocking.

    Lowercased, trailing dot removed, port dropped, ``www.`` folded into the
    bare domain, IP literals canonicalised, unicode names IDNA-encoded. Without
    this, ``API.example.com.:443``, ``www.example.com``, ``exämple.com`` and
    ``xn--exmple-cua.com`` are separate allowances for one server — a rate limit
    that politely asks to be dodged, and a blocked-host set that forgets.
    """
    value = (host or "").strip().lower()
    if "@" in value:  # defensive: a bucket key must never carry credentials
        value = value.rsplit("@", 1)[1]

    if value.startswith("["):  # bracketed IPv6 literal
        value = value.partition("]")[0].lstrip("[")
    elif value.count(":") == 1:  # host:port — a bare IPv6 has several colons
        value = value.split(":", 1)[0]
    # After the port, not before it: "example.com.:443" ends in a digit, so a
    # leading rstrip leaves the root dot on and hands one server two buckets.
    value = value.rstrip(".")

    # An IP literal has exactly one canonical spelling; use it, so 0177.0.0.1,
    # ::ffff:127.0.0.1 and 127.0.0.1 cannot be three different identities.
    try:
        ip = ipaddress.ip_address(value)
    except ValueError:
        pass
    else:
        mapped = getattr(ip, "ipv4_mapped", None)
        return str(mapped or ip)

    if any(ord(ch) > 127 for ch in value):
        # Not a valid IDN: keep the literal spelling as the key rather than
        # inventing one — a stable wrong key still buckets consistently.
        with contextlib.suppress(UnicodeError, ValueError):
            value = value.encode("idna").decode("ascii")
    if value.startswith("www."):
        value = value[4:]
    return value


@dataclass(slots=True)
class _Bucket:
    tokens: float
    updated: float
    interval: float


class _HostRateLimiter:
    """Token bucket, capacity one, refilling at one token per ``interval``.

    Capacity one is deliberate: a bucket that can accumulate credit lets an idle
    agent wake up and burst, which is exactly the traffic shape a rate limit
    exists to prevent. Waiters reserve their slot by pushing the bucket's clock
    forward, so concurrent probes queue instead of colliding.
    """

    def __init__(self, interval: float = MIN_INTERVAL_S) -> None:
        self.interval = interval
        self._lock = threading.Lock()
        self._buckets: dict[str, _Bucket] = {}

    def set_interval(self, host: str, interval: float) -> bool:
        """Raise a host's interval (e.g. to honour ``Crawl-delay``). Never lowers it."""
        key = normalise_host(host)
        with self._lock:
            bucket = self._buckets.setdefault(key, _Bucket(1.0, time.monotonic(), self.interval))
            if interval > bucket.interval:
                bucket.interval = interval
                return True
        return False

    def acquire(self, host: str, max_wait: float = MAX_WAIT_S) -> float:
        """Sleep until a token is due; return seconds slept, or ``-1.0`` if refused.

        Refusing beyond ``max_wait`` keeps a hostile ``Crawl-delay: 86400`` from
        parking the agent's loop for a day. Nothing is consumed when refused.
        """
        key = normalise_host(host)
        with self._lock:
            bucket = self._buckets.get(key)
            if bucket is None:
                bucket = _Bucket(1.0, time.monotonic(), self.interval)
                self._buckets[key] = bucket
            now = time.monotonic()
            # Refill against the bucket's own clock, which may already sit in the
            # future because an earlier caller reserved a slot. Measuring from
            # `now` instead would let every queued thread compute the same short
            # wait and fire together — a rate limiter that only limits one caller.
            elapsed = max(0.0, now - bucket.updated)
            bucket.tokens = min(1.0, bucket.tokens + elapsed / bucket.interval)
            reserved_ahead = max(0.0, bucket.updated - now)
            if bucket.tokens >= 1.0 and reserved_ahead <= 0.0:
                bucket.tokens -= 1.0
                bucket.updated = now
                wait = 0.0
            else:
                wait = reserved_ahead + (1.0 - bucket.tokens) * bucket.interval
                if wait > max_wait:
                    return -1.0
                bucket.tokens = 0.0
                # Push the bucket clock to the slot this caller just claimed, so
                # the next arrival queues behind it rather than beside it.
                bucket.updated = now + wait
        if wait > 0:
            time.sleep(wait)
        return wait


#: Module scope on purpose. ``DiscoveryRegistry()`` is cheap to construct, so a
#: per-instance limiter would mean the limit is one line of code away from being
#: reset. The same reasoning applies to the blocked-host set and the budget.
_LIMITER = _HostRateLimiter()

_STATE_LOCK = threading.Lock()
_BLOCKED_HOSTS: set[str] = set()
_ROBOTS: dict[str, urllib.robotparser.RobotFileParser] = {}
#: Origins whose robots.txt could not be read, and when we last tried. Kept
#: separate from _ROBOTS and deliberately short-lived: caching "unreadable"
#: forever would turn one flaky moment into a permanent fail-open.
_ROBOTS_FAILED: dict[str, float] = {}
_ROBOTS_RETRY_S = 300.0
_probes_used = 0


def _spend_probe_budget() -> bool:
    """Consume one probe from the per-process budget. There is no reset.

    A ``reset_budget()`` helper would be the first thing an eager caller reached
    for, which is why it does not exist: the cap is meant to bind.
    """
    global _probes_used
    with _STATE_LOCK:
        if _probes_used >= MAX_PROBES_PER_RUN:
            return False
        _probes_used += 1
        return True


def remaining_probe_budget() -> int:
    with _STATE_LOCK:
        return max(0, MAX_PROBES_PER_RUN - _probes_used)


def _mark_blocked(host: str) -> None:
    with _STATE_LOCK:
        _BLOCKED_HOSTS.add(normalise_host(host))


def _is_blocked(host: str) -> bool:
    with _STATE_LOCK:
        return normalise_host(host) in _BLOCKED_HOSTS


def blocked_hosts() -> tuple[str, ...]:
    """Hosts that returned an access control. Reported, never cleared."""
    with _STATE_LOCK:
        return tuple(sorted(_BLOCKED_HOSTS))


# --------------------------------------------------------------------------
# The probe result
# --------------------------------------------------------------------------


@dataclass(slots=True)
class SourceProbe:
    """What one look at a candidate endpoint established — including refusals."""

    url: str
    host: str = ""
    status: ProbeStatus = ProbeStatus.UNREACHABLE
    http_status: int = 0
    content_type: str = ""
    item_count: int = 0
    schema: dict[str, str] = field(default_factory=dict)
    suggested_mapping: dict[str, Any] = field(default_factory=dict)
    sample: Any = None
    notes: tuple[str, ...] = ()
    probed_ts: float = 0.0
    robots_allowed: bool = False
    elapsed_ms: float = 0.0

    @property
    def probed(self) -> bool:
        """``False`` for a built-in candidate nobody has looked at yet."""
        return self.probed_ts > 0

    @property
    def usable(self) -> bool:
        """Confident enough to propose as a configured source — still needs a human.

        A mapping without a reward path is *not* usable however neat the rest of
        it looks: :func:`gigs_from_source` will refuse to price its items, so
        offering it would be advertising a source that can only ever yield
        nothing. Endpoints that publish no money field are a real and common
        case — plenty of job boards list no pay — and this is where that gets
        said, rather than downstream in an empty result nobody can explain.
        """
        return (
            self.status is ProbeStatus.OK
            and self.item_count > 0
            and mapping_is_workable(self.suggested_mapping)
            and float(self.suggested_mapping.get("confidence", 0.0)) >= 0.5
        )

    def add_note(self, *notes: str) -> None:
        self.notes = self.notes + tuple(n for n in notes if n)

    def to_dict(self) -> dict[str, Any]:
        return {
            "url": self.url,
            "host": self.host,
            "status": self.status.value,
            "http_status": self.http_status,
            "content_type": self.content_type,
            "item_count": self.item_count,
            "schema": dict(self.schema),
            "suggested_mapping": dict(self.suggested_mapping),
            "sample": self.sample,
            "notes": list(self.notes),
            "probed_ts": self.probed_ts,
            "probed": self.probed,
            "robots_allowed": self.robots_allowed,
            "elapsed_ms": round(self.elapsed_ms, 1),
            "usable": self.usable,
        }


# --------------------------------------------------------------------------
# Guards
# --------------------------------------------------------------------------


def _bad_address_reason(ip: ipaddress.IPv4Address | ipaddress.IPv6Address) -> str:
    for label, flagged in (
        ("private", ip.is_private),
        ("loopback", ip.is_loopback),
        ("link-local", ip.is_link_local),
        ("reserved", ip.is_reserved),
        ("multicast", ip.is_multicast),
        ("unspecified", ip.is_unspecified),
    ):
        if flagged:
            return label
    if not ip.is_global:
        return "not globally routable"
    return ""


def _embedded(ip: ipaddress.IPv4Address | ipaddress.IPv6Address) -> list[Any]:
    """IPv4 addresses hiding inside an IPv6 one.

    ``::ffff:127.0.0.1`` is loopback wearing a costume, and 6to4/Teredo carry a
    v4 address in their payload. Judging only the outer form misses all three.
    """
    if not isinstance(ip, ipaddress.IPv6Address):
        return []
    found = [ip.ipv4_mapped, ip.sixtofour]
    if ip.teredo:
        found.extend(ip.teredo)
    return [addr for addr in found if addr is not None]


def _address_verdict(host: str, port: int) -> tuple[list[str], str]:
    """Resolve ``host`` and judge **every** address it answers with.

    Checking only the first result is the classic SSRF hole: a name that returns
    one public address and one ``127.0.0.1`` passes a first-result check and then
    connects wherever the resolver feels like.
    """
    try:
        infos = socket.getaddrinfo(host, port, type=socket.SOCK_STREAM)
    except (OSError, UnicodeError, ValueError) as e:
        return [], f"could not resolve {host}: {str(e)[:80]}"

    resolved: list[str] = []
    for info in infos:
        raw = str(info[4][0])
        try:
            ip = ipaddress.ip_address(raw)
        except ValueError:
            return resolved, f"{host} resolved to an unparseable address {raw[:40]}"
        resolved.append(str(ip))
        for candidate in [ip, *_embedded(ip)]:
            reason = _bad_address_reason(candidate)
            if reason:
                return resolved, f"{host} resolves to {candidate} ({reason})"
    if not resolved:
        return [], f"{host} resolved to no addresses"
    return resolved, ""


def _credential_params(query: str) -> tuple[str, ...]:
    found = {
        key for key, _ in urllib.parse.parse_qsl(query, keep_blank_values=True)
        if _looks_like_credential(key)
    }
    return tuple(sorted(found))


def _static_refusal(url: str) -> tuple[urllib.parse.SplitResult | None, str]:
    """Everything we can refuse without touching the network."""
    try:
        parsed = urllib.parse.urlsplit(url.strip())
        _ = parsed.port  # raises ValueError on a malformed port
    except ValueError as e:
        return None, f"unparseable URL: {str(e)[:80]}"

    if parsed.scheme not in ("http", "https"):
        return None, f"scheme {parsed.scheme or '(none)'!r} is not http or https"
    if parsed.username or parsed.password or "@" in parsed.netloc.rpartition("]")[2]:
        return None, "URL carries userinfo (user:pass@) — this module never sends credentials"
    if not parsed.hostname:
        return None, "URL has no host"
    creds = _credential_params(parsed.query)
    if creds:
        return None, f"query carries credential parameters ({', '.join(creds)}) — not a public endpoint"
    return parsed, ""


# --------------------------------------------------------------------------
# One raw GET, with every guard in front of it
# --------------------------------------------------------------------------


@dataclass(slots=True)
class _Raw:
    status: int = 0
    content_type: str = ""
    headers: dict[str, str] = field(default_factory=dict)
    body: bytes = b""
    location: str = ""
    truncated: bool = False
    refusal: str = ""   # a guard said no — nothing left the process
    error: str = ""     # the network said no
    notes: tuple[str, ...] = ()


class _NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    """Turns every 3xx into an error so the caller re-runs the guards on the target.

    Automatic redirects are how an approved host hands you an unapproved one.
    """

    def redirect_request(self, req, fp, code, msg, headers, newurl):  # type: ignore[no-untyped-def]
        return None


def _pinned_opener(ip: str) -> urllib.request.OpenerDirector:
    """An opener that can only ever reach ``ip``.

    Closes a DNS-rebinding TOCTOU: the guard resolves the hostname and judges
    every address it answers with, and then urllib resolves the *name* again
    when it connects. A name whose answer changes in between reaches an address
    the guard never approved. Pinning the socket to the vetted address removes
    that window, while the Host header and TLS SNI still carry the real
    hostname, so virtual hosting and certificate validation are unaffected.
    """
    context = ssl_context()

    class PinnedHTTPConnection(http.client.HTTPConnection):
        def connect(self) -> None:
            self.sock = socket.create_connection((ip, self.port), self.timeout)
            if self._tunnel_host:
                self._tunnel()

    class PinnedHTTPSConnection(http.client.HTTPSConnection):
        def connect(self) -> None:
            sock = socket.create_connection((ip, self.port), self.timeout)
            if self._tunnel_host:
                self.sock = sock
                self._tunnel()
                sock = self.sock
            self.sock = context.wrap_socket(sock, server_hostname=self.host)

    class PinnedHTTPHandler(urllib.request.HTTPHandler):
        def http_open(self, req):  # type: ignore[no-untyped-def]
            return self.do_open(PinnedHTTPConnection, req)

    class PinnedHTTPSHandler(urllib.request.HTTPSHandler):
        def https_open(self, req):  # type: ignore[no-untyped-def]
            return self.do_open(PinnedHTTPSConnection, req)

    return urllib.request.build_opener(
        PinnedHTTPHandler, PinnedHTTPSHandler, _NoRedirectHandler
    )


def _raw_get(url: str, *, timeout: float) -> _Raw:
    """A single guarded GET. No retries, no redirects, no cookies, no caller headers.

    Hand-rolled rather than reusing :func:`usdt_agent.http.request` precisely
    because that helper retries 429s and 5xxs and follows redirects — helpful
    behaviour for an API we are entitled to call, and exactly the behaviour that
    must be impossible here.
    """
    parsed, refusal = _static_refusal(url)
    if parsed is None:
        return _Raw(refusal=refusal)

    host = parsed.hostname or ""
    key = normalise_host(host)
    if _is_blocked(key):
        return _Raw(refusal=f"{key} already returned an access control this run; not retried")

    port = parsed.port or (443 if parsed.scheme == "https" else 80)
    resolved, bad = _address_verdict(host, port)
    if bad:
        return _Raw(refusal=f"SSRF guard: {bad}")

    notes = [f"{key} resolves to {', '.join(resolved[:4])}"]
    waited = _LIMITER.acquire(key)
    if waited < 0:
        return _Raw(refusal=f"pacing {key} would mean sleeping over {MAX_WAIT_S:.0f}s", notes=tuple(notes))
    if waited > 0:
        notes.append(f"waited {waited:.2f}s for the {key} rate limit")

    request = urllib.request.Request(url, headers=dict(_HEADER_PAIRS), method="GET")
    # ProxyHandler reads the environment, same as every other outbound call in
    # this project. We never pick or rotate a proxy — inheriting the machine's
    # egress is not evasion. Note this also means the guard above proves what we
    # were willing to *ask for*; with a proxy in play it does not resolve names.
    # With a proxy in play we are not resolving names at all, so pinning would
    # only break egress; without one, pin to the address the guard approved.
    if urllib.request.getproxies():
        notes.append("egress proxy in use: the address guard covers the request, not the socket")
        opener = urllib.request.build_opener(
            urllib.request.HTTPSHandler(context=ssl_context()), _NoRedirectHandler
        )
    else:
        opener = _pinned_opener(resolved[0])
        notes.append(f"socket pinned to {resolved[0]}")
    try:
        with opener.open(request, timeout=timeout) as response:
            body = response.read(MAX_BODY_BYTES + 1)
            headers = {k.lower(): v for k, v in response.headers.items()}
            truncated = len(body) > MAX_BODY_BYTES
            return _Raw(
                status=int(response.status),
                content_type=headers.get("content-type", ""),
                headers=headers,
                body=body[:MAX_BODY_BYTES],
                truncated=truncated,
                notes=tuple(notes),
            )
    except urllib.error.HTTPError as e:
        headers = {k.lower(): v for k, v in (e.headers or {}).items()}
        body = b""
        try:
            body = e.read(65536)
        except Exception:  # the body may already be closed; the status is what matters
            body = b""
        return _Raw(
            status=int(e.code),
            content_type=headers.get("content-type", ""),
            headers=headers,
            body=body,
            location=headers.get("location", ""),
            notes=tuple(notes),
        )
    except Exception as e:
        # A dead endpoint degrades to a status. It never takes the earn loop down.
        return _Raw(error=f"{type(e).__name__}: {str(e)[:120]}", notes=tuple(notes))


def _challenge_reason(raw: _Raw) -> str:
    """Why this response looks like a control rather than an answer.

    Anything this returns is terminal. There is no branch below it that tries
    again, solves anything, or changes what we send.
    """
    if raw.status in (401, 403):
        return f"HTTP {raw.status}: the endpoint is not public"
    if raw.status == 429:
        return "HTTP 429: the server is rate limiting us"
    for header in _CHALLENGE_HEADERS:
        if raw.headers.get(header):
            return f"anti-bot header {header!r}"
    looks_like_json = "json" in raw.content_type.lower() and raw.status == 200
    if not looks_like_json:
        lowered = raw.body[:4096].decode("utf-8", errors="replace").lower()
        for marker in _CHALLENGE_MARKERS:
            if marker in lowered:
                return f"challenge marker {marker!r} in the response body"
    if raw.status == 503 and "cloudflare" in raw.headers.get("server", "").lower():
        return "HTTP 503 from a Cloudflare edge: interstitial, not an outage"
    return ""


# --------------------------------------------------------------------------
# robots.txt
# --------------------------------------------------------------------------


def _robots_for(parsed: urllib.parse.SplitResult) -> tuple[urllib.robotparser.RobotFileParser | None, str]:
    """Cached ``robots.txt`` for one origin. ``None`` means "could not be read"."""
    origin = f"{parsed.scheme}://{parsed.netloc.lower().rstrip('.')}"
    with _STATE_LOCK:
        cached = _ROBOTS.get(origin)
        if cached is not None:
            return cached, ""
        failed_at = _ROBOTS_FAILED.get(origin)
        if failed_at is not None and time.time() - failed_at < _ROBOTS_RETRY_S:
            return None, "robots.txt still unreadable (retried recently)"

    raw = _raw_get(f"{origin}/robots.txt", timeout=TIMEOUT_S)
    parser: urllib.robotparser.RobotFileParser | None = urllib.robotparser.RobotFileParser()
    note = ""
    if raw.refusal or raw.error:
        parser, note = None, f"robots.txt unreachable ({raw.refusal or raw.error})"
    elif raw.status in (401, 403):
        # RFC 9309: an access-controlled robots.txt means the whole site is off
        # limits. The strict reading is the only safe one.
        parser.disallow_all = True  # type: ignore[union-attr]
        note = f"robots.txt returned HTTP {raw.status} — treating the whole host as disallowed"
    elif raw.status == 200:
        parser.parse(raw.body.decode("utf-8", errors="replace").splitlines())  # type: ignore[union-attr]
        note = "robots.txt fetched and parsed"
    elif 400 <= raw.status < 500:
        parser.parse([])  # type: ignore[union-attr]
        note = f"no robots.txt (HTTP {raw.status}) — nothing is disallowed"
    else:
        parser, note = None, f"robots.txt unreadable (HTTP {raw.status})"

    with _STATE_LOCK:
        if parser is not None:
            _ROBOTS[origin] = parser
        else:
            # Never cache "unreadable" as a permanent answer: one flaky moment
            # would silently fail the whole origin open for the process
            # lifetime. Remember it briefly so a hard-down host is not re-fetched
            # on every probe, and let it go stale.
            _ROBOTS_FAILED[origin] = time.time()
    if parser is not None:
        delay = parser.crawl_delay(USER_AGENT)
        if delay and _LIMITER.set_interval(parsed.hostname or "", float(delay)):
            note += f"; honouring Crawl-delay {float(delay):.0f}s"
    return parser, note


def _robots_allows(url: str, parsed: urllib.parse.SplitResult) -> tuple[bool, tuple[str, ...]]:
    parser, note = _robots_for(parsed)
    notes = (note,) if note else ()
    if parser is None:
        # Unreachable robots.txt buys permission for this one path and nothing
        # else — the next path on the same host asks the same question again.
        path = parsed.path or "/"
        return True, (*notes, f"robots.txt could not be read; treating {path} alone as allowed")
    allowed = bool(parser.can_fetch(USER_AGENT, url))
    if not allowed:
        return False, (*notes, f"robots.txt disallows {parsed.path or '/'} for {USER_AGENT.split()[0]}")
    return True, notes


# --------------------------------------------------------------------------
# Schema inference
# --------------------------------------------------------------------------


def _type_name(value: Any) -> str:
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "bool"
    if isinstance(value, int):
        return "int"
    if isinstance(value, float):
        return "float"
    if isinstance(value, str):
        return "str"
    if isinstance(value, list):
        return "list"
    if isinstance(value, dict):
        return "dict"
    return type(value).__name__


def _join(path: str, key: str) -> str:
    return f"{path}.{key}" if path else key


def _record(out: dict[str, str], path: str, type_name: str) -> None:
    key = path or "$"
    previous = out.get(key)
    if previous is None:
        if len(out) < MAX_SCHEMA_LEAVES:
            out[key] = type_name
        return
    if type_name in (previous, "null"):
        return
    if previous == "null":
        out[key] = type_name
        return
    out[key] = "|".join(sorted({*previous.split("|"), type_name})[:3])


def _walk(value: Any, path: str, out: dict[str, str], depth: int, max_depth: int, max_items: int) -> None:
    if len(out) >= MAX_SCHEMA_LEAVES:
        return
    if depth > max_depth:
        _record(out, path, "...")
        return
    if isinstance(value, dict):
        if not value:
            _record(out, path, "dict")
            return
        for key in list(value)[:max_items]:
            # A key containing "." or "[" cannot be expressed in this path
            # grammar. Emitting it would produce a mapping that silently reads
            # the wrong field, so it is dropped instead.
            if "." in key or "[" in key:
                continue
            _walk(value[key], _join(path, str(key)), out, depth + 1, max_depth, max_items)
        return
    if isinstance(value, list):
        if not value:
            _record(out, f"{path}[]", "empty")
            return
        for element in value[:max_items]:
            _walk(element, f"{path}[]", out, depth + 1, max_depth, max_items)
        return
    _record(out, path, _type_name(value))


def infer_schema(payload: Any, max_depth: int = 6, max_items: int = 200) -> dict[str, str]:
    """Describe a JSON payload as ``{json_path: type_name}`` for its leaves.

    The path grammar is dotted, with ``[]`` marking a list whose elements are
    collapsed into one description — ``data.items[].reward.amount``. A bare
    scalar payload is reported as ``{"$": "int"}``.

    Depth, width and total leaf count are all capped. The payload comes from a
    stranger; a schema walk that trusts it to be small is a memory exhaustion
    bug waiting for the first 40 MiB deeply-nested response.
    """
    out: dict[str, str] = {}
    _walk(payload, "", out, 0, max(1, max_depth), max(1, max_items))
    return out


def _mostly_dicts(items: list[Any]) -> bool:
    head = items[:20]
    return bool(head) and sum(isinstance(x, dict) for x in head) >= max(1, int(len(head) * 0.6))


def find_item_array(payload: Any, max_nodes: int = 2000, max_depth: int = 6) -> tuple[str, list[Any]]:
    """Locate the list of objects that is the payload's item array.

    Wrapper keys are endlessly inventive — ``items``, ``data``, ``results``,
    ``bounties``, ``payload.records`` — but the item array is almost always the
    longest list of dicts in the document, whatever it is called. Shape beats a
    list of key names that needs updating every time a platform ships a redesign.

    Length alone is not quite enough: a response holding one issue with five
    labels has a longer ``labels`` array than ``items``, and picking it would
    describe a label as though it were a gig. Arrays reached *through* another
    array are therefore ranked below arrays reached only through objects — a
    list inside an item is a property of that item, not the item list.
    """
    best_path, best_items, best_rank = "", [], (2, 0)

    def consider(path: str, values: list[Any], current: tuple[str, list[Any], tuple[int, int]]
                 ) -> tuple[str, list[Any], tuple[int, int]]:
        rank = (1 if "[]" in path else 0, -len(values))
        return (path, list(values), rank) if rank < current[2] else current

    if isinstance(payload, list) and _mostly_dicts(payload):
        best_path, best_items, best_rank = consider("", payload, (best_path, best_items, best_rank))

    queue: deque[tuple[Any, str, int]] = deque([(payload, "", 0)])
    visited = 0
    while queue and visited < max_nodes:
        node, path, depth = queue.popleft()
        visited += 1
        if depth > max_depth:
            continue
        if isinstance(node, dict):
            for key, value in list(node.items())[:200]:
                if "." in str(key) or "[" in str(key):
                    continue
                child = _join(path, str(key))
                if isinstance(value, list) and _mostly_dicts(value):
                    best_path, best_items, best_rank = consider(
                        child, value, (best_path, best_items, best_rank)
                    )
                if isinstance(value, dict | list):
                    queue.append((value, child, depth + 1))
        elif isinstance(node, list):
            for element in node[:50]:
                if isinstance(element, dict | list):
                    queue.append((element, f"{path}[]", depth + 1))
    return (f"{best_path}[]" if best_items else ""), best_items


# --------------------------------------------------------------------------
# Mapping onto the Gig model
# --------------------------------------------------------------------------

_ISO_DATE = re.compile(r"^\d{4}-\d{2}-\d{2}([T ]\d{2}:\d{2}(:\d{2})?)?")
#: Accepts 500, 500.00, 1,000 and 1,234,567.89 — and rejects 1,5 / 2,50 / 1,2,3,
#: where a comma is a decimal point in most of the world. An ambiguous form
#: falls through to parse_reward, i.e. 0.0, i.e. the item is dropped rather than
#: priced an order of magnitude too high.
_BARE_NUMBER = re.compile(r"^\s*-?(?:\d+|\d{1,3}(?:,\d{3})+)(?:\.\d+)?\s*$")

#: Epoch seconds that could plausibly be a deadline: 1973 to 2286.
_EPOCH_RANGE = (1.0e8, 1.0e10)


@dataclass(frozen=True, slots=True)
class _FieldRule:
    """How one Gig field recognises itself in a stranger's schema."""

    field: str
    exact: frozenset[str]
    tokens: frozenset[str]
    avoid: frozenset[str]
    shape: str
    weight: float


_RULES: tuple[_FieldRule, ...] = (
    _FieldRule(
        "external_id",
        frozenset({"id", "uuid", "guid", "slug", "key", "number", "identifier", "externalid", "ref", "code"}),
        frozenset({"id", "uuid", "guid", "slug", "number", "identifier", "ref"}),
        frozenset({"parent", "user", "author", "owner", "node", "labels", "tags", "internal"}),
        "ident",
        0.15,
    ),
    _FieldRule(
        "title",
        frozenset({"title", "name", "headline", "subject", "summary", "position"}),
        frozenset({"title", "name", "headline", "subject", "summary"}),
        frozenset({"user", "author", "owner", "labels", "tags", "topics", "categories", "skills",
                   "company", "file"}),
        "text",
        0.25,
    ),
    _FieldRule(
        "url",
        frozenset({"url", "htmlurl", "link", "permalink", "href", "weburl", "applyurl", "landingurl"}),
        frozenset({"url", "link", "permalink", "href", "uri"}),
        frozenset({"avatar", "image", "icon", "logo", "thumbnail", "gravatar"}),
        "url",
        0.15,
    ),
    _FieldRule(
        "reward_usdt",
        frozenset({"amount", "reward", "bounty", "price", "value", "payout", "prize", "budget",
                   "compensation", "salary", "usd", "usdt", "fee", "rate"}),
        frozenset({"amount", "reward", "bounty", "price", "payout", "prize", "budget", "compensation",
                   "salary", "usd", "usdt", "money", "pay", "value", "fee"}),
        frozenset({"count", "percent", "percentage", "version", "id", "tax", "discount", "currency"}),
        "money",
        0.30,
    ),
    _FieldRule(
        "deadline_ts",
        frozenset({"deadline", "expires", "expiresat", "expiry", "due", "dueat", "duedate", "endsat",
                   "closesat", "validuntil", "enddate", "expirationdate", "closingdate"}),
        frozenset({"deadline", "expires", "expiry", "expiration", "due", "ends", "closes", "until",
                   "closing"}),
        frozenset({"created", "updated", "published", "started", "posted", "modified"}),
        "date",
        0.07,
    ),
    _FieldRule(
        "tags",
        frozenset({"labels", "tags", "topics", "categories", "skills", "keywords"}),
        frozenset({"labels", "label", "tags", "tag", "topics", "categories", "category", "skills",
                   "keywords"}),
        # A label object carries an id, a colour and a name. Only one of those is
        # a tag, and without this veto the first key in the object wins the tie.
        frozenset({"id", "uuid", "count", "url", "color", "colour", "description"}),
        "list",
        0.08,
    ),
)

GIG_FIELDS: tuple[str, ...] = tuple(rule.field for rule in _RULES)

#: A mapping is only worth having if it can produce a priced gig. Everything
#: else is optional; these two are not.
REQUIRED_GIG_FIELDS: tuple[str, ...] = ("title", "reward_usdt")
_ACCEPT_SCORE = 0.55


def mapping_is_workable(mapping: dict[str, Any]) -> bool:
    """Can this mapping yield a gig at all? Without a reward path, no."""
    return all(
        isinstance(mapping.get(key), str) and mapping.get(key) for key in REQUIRED_GIG_FIELDS
    )


def _looks_like_credential(name: str) -> bool:
    """Does this parameter name carry a secret?

    Token-wise so that ``subscriptionKey``, ``x-oauth-token`` and ``refreshToken``
    are all caught by the same short vocabulary, instead of needing an entry per
    vendor spelling. False positives cost nothing here: refusing to probe a URL
    is always the safe outcome.
    """
    return bool(_CREDENTIAL_TOKENS.intersection(_token_list(name)))


def _token_list(name: str) -> tuple[str, ...]:
    """Split ``html_url``, ``htmlUrl`` and ``HTML-URL`` into the same tokens.

    Tokens rather than substrings because substrings lie: ``valid_until``
    contains ``id`` and would otherwise read as an identifier.
    """
    spaced = re.sub(r"(?<=[a-z0-9])(?=[A-Z])", "_", name)
    return tuple(t for t in re.split(r"[^A-Za-z0-9]+", spaced.lower()) if t)


def _tokens(name: str) -> frozenset[str]:
    return frozenset(_token_list(name))


def _flat(name: str) -> str:
    """``expires_at``, ``expiresAt`` and ``ExpiresAt`` all flatten to ``expiresat``."""
    return "".join(_token_list(name))


def _relative(path: str, prefix: str) -> str:
    if prefix and path.startswith(prefix):
        return path[len(prefix):].lstrip(".")
    return path


def _leaf(rel: str) -> str:
    return rel.rsplit(".", 1)[-1].replace("[]", "")


def _dig(value: Any, rel: str) -> Any:
    """Follow a relative path into a sample item, taking the first list element."""
    if not rel:
        return None
    current = value
    for segment in rel.split("."):
        key, is_list = (segment[:-2], True) if segment.endswith("[]") else (segment, False)
        if key:
            if not isinstance(current, dict):
                return None
            current = current.get(key)
        if is_list:
            if not isinstance(current, list) or not current:
                return None
            current = current[0]
    return current


def _dig_container(value: Any, dotted: str) -> Any:
    current = value
    for segment in [s for s in dotted.split(".") if s]:
        if not isinstance(current, dict):
            return None
        current = current.get(segment)
    return current


def _shape_score(shape: str, value: Any, type_name: str, rel: str) -> float:
    """How much the observed value agrees with what the field should look like."""
    if shape == "list":
        if "[]" in rel or isinstance(value, list):
            return 0.7
        return -0.5
    if value is None:  # no sample — fall back to the inferred type, weakly
        weak = {
            "money": {"int": 0.5, "float": 0.5, "str": 0.1},
            "date": {"str": 0.2, "int": 0.3, "float": 0.3},
            "url": {"str": 0.2},
            "text": {"str": 0.2},
            "ident": {"str": 0.2, "int": 0.2},
        }
        return weak.get(shape, {}).get(type_name, 0.0)
    if shape == "money":
        if isinstance(value, bool):
            return -0.9
        if isinstance(value, int | float):
            return 0.8 if 0 < float(value) <= 1.0e7 else -0.4
        if isinstance(value, str):
            return 0.6 if (parse_reward(value) > 0 or _BARE_NUMBER.match(value)) else -0.6
        return -0.9
    if shape == "date":
        if isinstance(value, str):
            return 0.8 if _ISO_DATE.match(value.strip()) else -0.6
        if isinstance(value, int | float) and not isinstance(value, bool):
            return 0.6 if _EPOCH_RANGE[0] <= abs(float(value)) <= _EPOCH_RANGE[1] * 1000 else -0.4
        return -0.9
    if shape == "url":
        if isinstance(value, str) and (value.startswith(("http://", "https://", "/"))):
            return 0.8
        return -0.9
    if shape == "text":
        if isinstance(value, str) and 0 < len(value.strip()) <= 500 and "://" not in value:
            return 0.6
        return -0.6
    if shape == "ident":
        if isinstance(value, bool | dict | list):
            return -0.9
        if isinstance(value, int | float):
            return 0.6
        if isinstance(value, str) and 0 < len(value) <= 128:
            return 0.5
        return -0.4
    return 0.0


def _name_score(rule: _FieldRule, rel: str) -> float:
    """How strongly a field *name* claims to be this Gig field. Negative vetoes it."""
    path_tokens = _tokens(rel)
    if path_tokens & rule.avoid:
        return -1.0
    leaf = _leaf(rel)
    leaf_tokens = _tokens(leaf)
    if _flat(leaf) in frozenset(_flat(name) for name in rule.exact):
        return 1.0
    if leaf_tokens & rule.tokens:
        return 0.65
    if path_tokens & rule.tokens:
        return 0.35
    return 0.0


def _item_prefix(schema: dict[str, str], sample: Any) -> str:
    """The path prefix under which the item fields live, or ``""`` for a lone object."""
    groups: dict[str, list[str]] = {}
    for path in schema:
        marker = path.find("[]")
        if marker < 0:
            continue
        groups.setdefault(path[: marker + 2], []).append(path)
    if not groups:
        return ""
    sample_keys = set(sample) if isinstance(sample, dict) else set()

    def rank(entry: tuple[str, list[str]]) -> tuple[int, int]:
        prefix, paths = entry
        heads = {_relative(p, prefix).split(".")[0].replace("[]", "") for p in paths}
        return len(heads & sample_keys), len(paths)

    return max(groups.items(), key=rank)[0]


def suggest_gig_mapping(schema: dict[str, str], sample: Any) -> dict[str, Any]:
    """Propose ``{gig_field: json_path}`` for a schema, plus a ``confidence`` float.

    Two independent signals have to agree: the field *name* (``html_url``,
    ``bounty_amount``, ``expires_at``) and the value *shape* (an ISO-8601 string
    is a date, a number under a money-ish key is money). Names alone map
    ``valid_until`` to an id; shapes alone map every timestamp to a deadline.

    The result is a suggestion for a human to confirm. Nothing in this module
    turns a suggestion into a configured source on its own.
    """
    prefix = _item_prefix(schema, sample)
    candidates: list[tuple[str, str, str]] = []
    for path, type_name in schema.items():
        rel = _relative(path, prefix)
        if not rel or rel.count(".") > 2:  # deep nesting is noise, not a gig field
            continue
        candidates.append((path, rel, type_name))

    mapping: dict[str, Any] = {}
    earned = 0.0
    for rule in _RULES:
        best_path, best_score = "", 0.0
        for path, rel, type_name in candidates:
            name = _name_score(rule, rel)
            if name <= 0:
                continue
            score = name + _shape_score(rule.shape, _dig(sample, rel), type_name, rel)
            score -= 0.12 * rel.count(".")
            if score > best_score:
                best_path, best_score = path, score
        if best_path and best_score >= _ACCEPT_SCORE:
            mapping[rule.field] = best_path
            earned += rule.weight * min(1.0, best_score / 1.7)

    mapping["confidence"] = round(earned / sum(r.weight for r in _RULES), 3)
    return mapping


# --------------------------------------------------------------------------
# Applying a confirmed mapping
# --------------------------------------------------------------------------


def _iso_to_ts(value: str) -> float:
    try:
        parsed = datetime.fromisoformat(value.strip().replace("Z", "+00:00"))
    except ValueError:
        return 0.0
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=UTC)
    return parsed.timestamp()


def _as_money(value: Any) -> float:
    """A number, or zero. Never an estimate.

    ``parse_reward`` wants a currency marker, which a bare ``"500.00"`` string
    from a JSON field called ``amount`` does not have — reading that as five
    hundred is arithmetic, not guessing, so it is allowed. Anything else that
    fails to parse is worth zero and takes its item with it.
    """
    if isinstance(value, bool) or value is None:
        return 0.0
    if isinstance(value, int | float):
        amount = float(value)
        return amount if 0 < amount <= 1.0e7 else 0.0
    if isinstance(value, str):
        text = value.strip()
        if _BARE_NUMBER.match(text):
            try:
                amount = float(text.replace(",", ""))
            except ValueError:
                return 0.0
            return amount if 0 < amount <= 1.0e7 else 0.0
        return parse_reward(text)
    return 0.0


def _as_timestamp(value: Any) -> float:
    if isinstance(value, str):
        return _iso_to_ts(value)
    if isinstance(value, int | float) and not isinstance(value, bool):
        seconds = float(value)
        if seconds > _EPOCH_RANGE[1]:  # milliseconds
            seconds /= 1000.0
        return seconds if _EPOCH_RANGE[0] <= seconds <= _EPOCH_RANGE[1] else 0.0
    return 0.0


def _as_text(value: Any) -> str:
    if isinstance(value, str):
        return " ".join(value.split())
    if isinstance(value, int | float) and not isinstance(value, bool):
        return str(value)
    return ""


def _as_tags(item: Any, rel: str) -> tuple[str, ...]:
    base, _, leaf = rel.partition("[]")
    container = _dig_container(item, base.strip(".")) if base.strip(".") else item
    if not isinstance(container, list):
        return ()
    leaf_key = leaf.strip(".")
    tags: list[str] = []
    for element in container[:12]:
        if isinstance(element, str):
            tags.append(element)
        elif isinstance(element, dict):
            for key in (leaf_key, "name", "title", "slug"):
                if key and isinstance(element.get(key), str):
                    tags.append(str(element[key]))
                    break
    return tuple(t.strip()[:40] for t in tags if t.strip())[:8]


def _items_prefix_from(paths: dict[str, str], override: str = "") -> str:
    if override:
        return override
    for path in paths.values():
        marker = path.find("[]")
        if marker >= 0:
            return path[: marker + 2]
    return ""


def _resolve_items(payload: Any, prefix: str) -> list[Any]:
    if not prefix:
        if isinstance(payload, list):
            return list(payload)
        return [payload] if isinstance(payload, dict) else []
    nodes: list[Any] = [payload]
    for chunk in prefix.split("[]"):
        key = chunk.strip(".")
        stepped: list[Any] = []
        for node in nodes:
            target = _dig_container(node, key) if key else node
            if isinstance(target, list):
                stepped.extend(target[:MAX_ITEMS_PER_SOURCE])
            elif target is not None and not key:
                stepped.append(target)
        nodes = stepped
        if not nodes:
            return []
    return nodes[:MAX_ITEMS_PER_SOURCE]


def gigs_from_source(
    payload: Any,
    mapping: dict[str, Any],
    channel: str = "discovered",
    *,
    source: str = "configured",
    effort_hours: float = DEFAULT_EFFORT_HOURS,
    payout_probability: float = UNVERIFIED_PAYOUT_PROBABILITY,
    limit: int = MAX_ITEMS_PER_SOURCE,
) -> list[Gig]:
    """Apply a confirmed mapping to a payload and emit real :class:`Gig` objects.

    An item without a title, or whose reward will not parse into a number, is
    dropped rather than admitted with a placeholder. A gig priced at a number
    nobody wrote down ranks against gigs that were, and ``usdt_per_hour`` is the
    metric the whole earning side allocates effort by — one invented reward and
    the agent spends its day on the wrong thing. A mapping with no reward field
    at all yields nothing, for the same reason.
    """
    paths = {
        key: str(value) for key, value in mapping.items()
        if key in GIG_FIELDS and isinstance(value, str) and value
    }
    if not mapping_is_workable(paths):
        log.info("discovery: mapping lacks %s; refusing to invent either",
                 " or ".join(REQUIRED_GIG_FIELDS))
        return []

    prefix = _items_prefix_from(paths, str(mapping.get("items_path") or ""))
    relative = {key: _relative(path, prefix) for key, path in paths.items()}
    items = _resolve_items(payload, prefix)
    confidence = float(mapping.get("confidence", 0.0) or 0.0)

    gigs: list[Gig] = []
    dropped = 0
    for index, item in enumerate(items[:limit]):
        title = _as_text(_dig(item, relative["title"]))[:200]
        reward = _as_money(_dig(item, relative["reward_usdt"]))
        if not title or reward <= 0:
            dropped += 1
            continue
        url = _as_text(_dig(item, relative.get("url", "")))
        if not url.startswith(("http://", "https://")):
            url = ""
        external_id = _as_text(_dig(item, relative.get("external_id", ""))) or url or f"{prefix}#{index}"
        gigs.append(Gig(
            channel=channel,
            external_id=external_id[:120],
            title=title,
            url=url,
            reward_usdt=reward,
            effort_hours=max(0.05, effort_hours),
            payout_probability=max(0.01, min(1.0, payout_probability)),
            deadline_ts=_as_timestamp(_dig(item, relative.get("deadline_ts", ""))),
            source=source,
            tags=_as_tags(item, relative.get("tags", "")),
            meta={
                "source_is_unverified": True,
                "mapping_confidence": confidence,
                "effort_is_a_placeholder": True,
                "item_index": index,
            },
        ))
    if dropped:
        log.info("discovery: %s yielded %d gigs, dropped %d without a title or a parseable reward",
                 source, len(gigs), dropped)
    return gigs


@dataclass(frozen=True, slots=True)
class ConfiguredSource:
    """A gig source added from configuration instead of from code.

    ``verified`` stays ``False`` until a human has looked at a probe and agreed
    the mapping is right. It is carried onto every gig's ``meta`` so a number
    that came from a guessed integration can always be told apart from one that
    came from a written-down channel.
    """

    name: str
    url: str
    mapping: dict[str, Any] = field(default_factory=dict)
    channel: str = "discovered"
    effort_hours: float = DEFAULT_EFFORT_HOURS
    payout_probability: float = UNVERIFIED_PAYOUT_PROBABILITY
    verified: bool = False
    note: str = ""

    @property
    def refusal(self) -> str:
        """Why this source could never be probed, or ``""`` if it is fetchable."""
        return _static_refusal(self.url)[1]

    @property
    def confidence(self) -> float:
        return float(self.mapping.get("confidence", 0.0) or 0.0)

    @classmethod
    def from_probe(
        cls, probe: SourceProbe, name: str, *, min_confidence: float = 0.5
    ) -> ConfiguredSource | None:
        """Build a source from a probe, or ``None`` if the probe did not earn it."""
        if not probe.usable:
            return None
        if float(probe.suggested_mapping.get("confidence", 0.0)) < min_confidence:
            return None
        return cls(
            name=name,
            url=probe.url,
            mapping=dict(probe.suggested_mapping),
            note=f"mapping suggested from a probe on {time.strftime('%Y-%m-%d')}; unconfirmed",
        )

    def gigs(self, payload: Any) -> list[Gig]:
        """Parse a payload this source already returned. Does no I/O."""
        return gigs_from_source(
            payload,
            self.mapping,
            channel=self.channel,
            source=self.name,
            effort_hours=self.effort_hours,
            payout_probability=self.payout_probability,
        )

    def to_dict(self) -> dict[str, Any]:
        return {
            "name": self.name,
            "url": self.url,
            "mapping": dict(self.mapping),
            "channel": self.channel,
            "effort_hours": self.effort_hours,
            "payout_probability": self.payout_probability,
            "verified": self.verified,
            "confidence": self.confidence,
            "note": self.note,
            "refusal": self.refusal,
        }


# --------------------------------------------------------------------------
# The probe pipeline
# --------------------------------------------------------------------------


def _trim_sample(value: Any, depth: int = 0) -> Any:
    """A sample is for a human to eyeball, so it is small on purpose."""
    if depth > 3:
        return "..."
    if isinstance(value, dict):
        return {str(k): _trim_sample(v, depth + 1) for k, v in list(value.items())[:30]}
    if isinstance(value, list):
        return [_trim_sample(v, depth + 1) for v in value[:8]]
    if isinstance(value, str) and len(value) > 240:
        return value[:240] + "..."
    return value


def _settle(probe: SourceProbe, status: ProbeStatus, started: float, *notes: str) -> SourceProbe:
    probe.status = status
    probe.add_note(*notes)
    probe.probed_ts = time.time()
    probe.elapsed_ms = (time.monotonic() - started) * 1000.0
    return probe


def _probe_once(url: str, *, timeout: float) -> SourceProbe:
    """Look at one candidate endpoint. Returns a status for every failure mode."""
    started = time.monotonic()
    parsed, refusal = _static_refusal(url)
    host = normalise_host(parsed.hostname or "") if parsed else ""
    probe = SourceProbe(url=url, host=host)
    if parsed is None:
        return _settle(probe, ProbeStatus.REFUSED, started, refusal)
    if _is_blocked(host):
        return _settle(probe, ProbeStatus.REFUSED, started,
                       f"{host} returned an access control earlier this run; not retried")
    if not _spend_probe_budget():
        return _settle(probe, ProbeStatus.REFUSED, started,
                       f"probe budget for this run is spent ({MAX_PROBES_PER_RUN})")

    current, seen = url, {url}
    raw = _Raw()
    for hop in range(MAX_REDIRECTS + 1):
        allowed, robot_notes = _robots_allows(current, parsed)
        probe.robots_allowed = allowed
        probe.add_note(*robot_notes)
        if not allowed:
            return _settle(probe, ProbeStatus.DISALLOWED, started)

        raw = _raw_get(current, timeout=timeout)
        probe.add_note(*raw.notes)
        if raw.refusal:
            return _settle(probe, ProbeStatus.REFUSED, started, raw.refusal)
        if raw.error:
            return _settle(probe, ProbeStatus.UNREACHABLE, started, raw.error)

        probe.http_status = raw.status
        probe.content_type = raw.content_type
        blocked = _challenge_reason(raw)
        if blocked:
            # Terminal. The host is remembered for the life of the process, so
            # no later probe — from this registry or a brand new one — retries it.
            _mark_blocked(host)
            log.warning("discovery: %s is refusing automated access (%s); not retried", host, blocked)
            return _settle(probe, ProbeStatus.BLOCKED, started, blocked,
                           "no retry, no workaround: this host has said no")

        if raw.status not in (301, 302, 303, 307, 308):
            break
        if hop >= MAX_REDIRECTS:
            return _settle(probe, ProbeStatus.REFUSED, started,
                           f"redirect chain longer than {MAX_REDIRECTS} hops")
        if not raw.location:
            return _settle(probe, ProbeStatus.UNREACHABLE, started, f"HTTP {raw.status} without a Location")
        target = urllib.parse.urljoin(current, raw.location)
        if target in seen:
            return _settle(probe, ProbeStatus.REFUSED, started, "redirect loop")
        parsed, refusal = _static_refusal(target)
        if parsed is None:
            return _settle(probe, ProbeStatus.REFUSED, started, f"redirect target refused: {refusal}")
        # A redirect can cross hosts, so the whole guard runs again on the target
        # rather than trusting the approval the first URL earned.
        host = normalise_host(parsed.hostname or "")
        probe.host = host
        probe.add_note(f"HTTP {raw.status} redirect to {target} — re-running every guard on it")
        seen.add(target)
        current = target
    else:  # pragma: no cover — the loop always breaks or returns
        return _settle(probe, ProbeStatus.REFUSED, started, "redirect budget exhausted")

    if raw.status != 200:
        return _settle(probe, ProbeStatus.UNREACHABLE, started, f"HTTP {raw.status}")
    if raw.truncated:
        return _settle(probe, ProbeStatus.NOT_JSON, started,
                       f"body exceeded the {MAX_BODY_BYTES // 1024 // 1024} MiB cap; not parsed")
    try:
        payload = json.loads(raw.body.decode("utf-8", errors="replace"))
    except (ValueError, UnicodeDecodeError) as e:
        return _settle(probe, ProbeStatus.NOT_JSON, started, f"body is not JSON: {str(e)[:100]}")

    if "json" not in raw.content_type.lower():
        probe.add_note(f"content-type is {raw.content_type or '(none)'!r} but the body parses as JSON")

    _, items = find_item_array(payload)
    probe.item_count = len(items)
    probe.schema = infer_schema(payload)
    probe.sample = _trim_sample(items[0] if items else payload)
    probe.suggested_mapping = suggest_gig_mapping(probe.schema, probe.sample)
    confidence = float(probe.suggested_mapping.get("confidence", 0.0))
    probe.add_note(
        f"{len(items)} items, {len(probe.schema)} schema leaves, "
        f"mapping confidence {confidence:.0%} — a suggestion, not a configured source"
    )
    return _settle(probe, ProbeStatus.OK, started)


# --------------------------------------------------------------------------
# The registry
# --------------------------------------------------------------------------

#: Plausible public endpoints, every one of them a guess. They are here so the
#: first probe has somewhere to point, not because any of them is known to work:
#: SPAs rename their internal routes without notice, and half of these will have
#: moved. Nothing is fetched to build this list, and appearing on it grants no
#: exemption — each still passes robots.txt, the SSRF guard and the rate limit.
_CANDIDATES: tuple[tuple[str, str], ...] = (
    ("https://api.github.com/search/issues?q=label%3Abounty+state%3Aopen&per_page=30",
     "GitHub issue search — documented, public, 10 req/min unauthenticated"),
    ("https://algora.io/api/bounties",
     "Algora bounty board — guessed from the shape of its SPA routes"),
    ("https://api.gitcoin.co/api/v1/bounties/?is_open=true",
     "Gitcoin bounties — guessed; the public API has moved before"),
    ("https://immunefi.com/public-api/bounties.json",
     "Immunefi security bounties — guessed from its 'public-api' path"),
    ("https://api.replit.com/v0/bounties",
     "Replit bounties — guessed; may well be GraphQL only"),
    ("https://remoteok.com/api",
     "RemoteOK job board — a long-standing public JSON feed"),
    ("https://www.arbeitnow.com/api/job-board-api",
     "Arbeitnow job board — a documented public JSON feed"),
    ("https://devpost.com/api/hackathons?status%5B%5D=open",
     "Devpost hackathons — the feed its own listing page calls"),
)


class DiscoveryRegistry:
    """Probes candidate endpoints and remembers what it learned.

    The cache is per instance because it is only a convenience. Everything that
    protects somebody else — pacing, the blocked-host set, the probe budget —
    lives at module scope, so a caller cannot buy a fresh allowance by writing
    ``DiscoveryRegistry()`` again.
    """

    def __init__(self, *, timeout: float = TIMEOUT_S) -> None:
        self.timeout = timeout
        self._cache: dict[str, SourceProbe] = {}

    def candidates(self) -> tuple[tuple[str, str], ...]:
        return _CANDIDATES

    def known(self) -> list[SourceProbe]:
        """The built-in table, as probes.

        Entries nobody has probed come back ``UNREACHABLE`` with ``probed_ts``
        of zero — literally true (we have not reached them) and, more usefully,
        impossible to mistake for a working integration. There is no "unknown"
        status to hide behind.
        """
        probes: list[SourceProbe] = []
        for url, note in _CANDIDATES:
            cached = self._cache.get(url)
            if cached is not None:
                probes.append(cached)
                continue
            parsed, refusal = _static_refusal(url)
            probes.append(SourceProbe(
                url=url,
                host=normalise_host(parsed.hostname or "") if parsed else "",
                status=ProbeStatus.UNREACHABLE,
                notes=(note, refusal or "unverified candidate: never probed, status unknown"),
            ))
        return probes

    def probe(self, url: str) -> SourceProbe:
        """Probe one endpoint. Cached; never raises."""
        key = url.strip()
        cached = self._cache.get(key)
        if cached is not None:
            return cached
        try:
            probe = _probe_once(key, timeout=self.timeout)
        except Exception as e:
            # The contract is that discovery degrades. A bug in the inference
            # code must not be able to kill the earn loop either.
            log.warning("discovery: probe of %s failed unexpectedly: %s", key[:80], str(e)[:120])
            probe = SourceProbe(url=key, status=ProbeStatus.UNREACHABLE, probed_ts=time.time(),
                                notes=(f"probe raised {type(e).__name__}",))
        self._cache[key] = probe
        return probe

    def probe_all(self, urls: list[str] | tuple[str, ...]) -> list[SourceProbe]:
        """Probe several endpoints, stopping the run at the first block.

        Stopping is the point: if one host has told us it does not want
        automated traffic, carrying on down the list is how a probe becomes a
        crawl that ignores the answer it just got.
        """
        results: list[SourceProbe] = []
        for url in urls:
            probe = self.probe(url)
            results.append(probe)
            if probe.status is ProbeStatus.BLOCKED:
                log.warning("discovery: stopping this run — %s declined automated access", probe.host)
                break
            if probe.status is ProbeStatus.REFUSED and remaining_probe_budget() <= 0:
                log.info("discovery: stopping this run — probe budget exhausted")
                break
        return results

    def summary(self) -> dict[str, Any]:
        """Introspection for the UI. Every count is of probes, never of income."""
        probed = list(self._cache.values())
        by_status: dict[str, int] = {}
        for probe in probed:
            by_status[probe.status.value] = by_status.get(probe.status.value, 0) + 1
        return {
            "candidates": len(_CANDIDATES),
            "probed": len(probed),
            "by_status": by_status,
            "usable": sum(1 for p in probed if p.usable),
            "blocked_hosts": list(blocked_hosts()),
            "probe_budget_remaining": remaining_probe_budget(),
            "user_agent": USER_AGENT,
            "min_interval_s": MIN_INTERVAL_S,
        }
