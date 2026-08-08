"""A very small stdlib HTTP/JSON client.

The whole project is dependency-free on purpose: an agent that is supposed to
run unattended for weeks on a cheap VPS should not break because a transitive
dependency shipped a bad release. ``urllib`` already honours ``HTTPS_PROXY``
and ``SSL_CERT_FILE``/``REQUESTS_CA_BUNDLE`` from the environment.
"""

from __future__ import annotations

import contextlib
import gzip
import json
import logging
import os
import ssl
import time
import urllib.error
import urllib.parse
import urllib.request
from typing import Any

log = logging.getLogger(__name__)

USER_AGENT = "usdt-agent/1.0 (+https://github.com/parfentsevandrey-blip/test)"

_ssl_ctx: ssl.SSLContext | None = None


def ssl_context() -> ssl.SSLContext:
    """Shared TLS context honouring the usual CA-bundle env vars."""
    global _ssl_ctx
    if _ssl_ctx is None:
        ca = (
            os.environ.get("SSL_CERT_FILE")
            or os.environ.get("REQUESTS_CA_BUNDLE")
            or os.environ.get("CURL_CA_BUNDLE")
        )
        _ssl_ctx = ssl.create_default_context(cafile=ca if ca and os.path.exists(ca) else None)
    return _ssl_ctx


class HttpError(RuntimeError):
    def __init__(self, status: int, url: str, body: str = "") -> None:
        super().__init__(f"HTTP {status} for {url}: {body[:200]}")
        self.status = status
        self.url = url
        self.body = body


def request(
    url: str,
    *,
    method: str = "GET",
    params: dict[str, Any] | None = None,
    headers: dict[str, str] | None = None,
    body: bytes | None = None,
    timeout: float = 10.0,
    retries: int = 3,
    backoff: float = 0.6,
) -> Any:
    """Perform a JSON request with bounded exponential backoff.

    Retries transient failures (network errors, 429, 5xx) and gives up
    immediately on deterministic ones (4xx other than 429) — retrying a 400 is
    just a slower 400.
    """
    if params:
        url = f"{url}?{urllib.parse.urlencode(params)}"
    hdrs = {"User-Agent": USER_AGENT, "Accept": "application/json", "Accept-Encoding": "gzip"}
    hdrs.update(headers or {})

    last: Exception | None = None
    for attempt in range(retries + 1):
        try:
            req = urllib.request.Request(url, data=body, headers=hdrs, method=method)
            with urllib.request.urlopen(req, timeout=timeout, context=ssl_context()) as resp:
                raw = resp.read()
                if resp.headers.get("Content-Encoding") == "gzip":
                    raw = gzip.decompress(raw)
                text = raw.decode("utf-8", errors="replace")
                if not text.strip():
                    return None
                return json.loads(text)
        except urllib.error.HTTPError as e:
            detail = ""
            with contextlib.suppress(Exception):  # body may already be consumed
                detail = e.read().decode("utf-8", errors="replace")
            last = HttpError(e.code, url, detail)
            if e.code != 429 and e.code < 500:
                raise last from e
        except (urllib.error.URLError, TimeoutError, ssl.SSLError, json.JSONDecodeError, OSError) as e:
            last = e
        if attempt < retries:
            delay = backoff * (2**attempt)
            log.debug("retry %d/%d for %s in %.1fs (%s)", attempt + 1, retries, url, delay, last)
            time.sleep(delay)

    assert last is not None
    raise last


def get_json(url: str, **kw: Any) -> Any:
    return request(url, method="GET", **kw)


def post_json(url: str, payload: dict[str, Any] | None = None, **kw: Any) -> Any:
    data = json.dumps(payload or {}).encode()
    headers = {"Content-Type": "application/json"}
    headers.update(kw.pop("headers", {}) or {})
    return request(url, method="POST", body=data, headers=headers, **kw)
