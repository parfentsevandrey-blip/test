"""Which client may contact which site — lessons L28, L29, L37 as code. Every tool that goes to the network asks here first.

Clients: fetcher (Scrapling Fetcher / curl_cffi with a Chrome TLS fingerprint), fetcher_plain (the same Fetcher with
         impersonate=None: plain curl fingerprint, no faked browser), stealthy (Scrapling StealthySession),
         headless (plain Playwright/Patchright Chromium: audit, HAR visits, the «visitor»), ndr_workflow (headful real Chrome
         with a clearance profile, ndr_workflow.py).
Protected sites recognise a headless browser and answer 403 / VPNBlock (ЦИАН, наш.дом.рф: L29; lsr.ru Servicepipe: L28). A 403
there pauses the site for 24 h for every client (L32), the daily collectors included — so a protected site is never contacted by
a client it is not listed for, and a browser never reaches it through a third-party embed either (dreamriva.ru loads a ЦИАН
widget that calls api.cian.ru, L37): `shield()` aborts such requests inside the browser.

check(url, client)          -> None, or raises Refused (wrong client, or the site is paused in cooldown.json)
shield(page_or_context)     -> route that aborts every request to a protected host the client may not contact
python3 lib/clientpolicy.py check <url> <client>   (exit 1 when refused)
"""
import importlib.util
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
PROTECTED = {                                   # site -> clients allowed
    "cian.ru": {"fetcher"},                     # browsers: VPNBlock / 403 (NOTES 16.10, L29); api.cian.ru is never used (CLAUDE.md)
    "cdn-cian.ru": {"fetcher"},
    "xn--80az8a.xn--d1aqf.xn--p1ai": {"ndr_workflow"},   # наш.дом.рф: only the headful session with clearance (W1, W12, F2)
    "lsr.ru": {"stealthy", "fetcher"},          # plain headless -> 403 (L28)
    "pik.ru": {"stealthy", "fetcher_plain"},    # Qrator: impersonated Fetcher and StealthySession got 401 17.10 (L41); plain curl got 200 -> honest client (L46)
}
NEVER = {"api.cian.ru"}                         # robots Disallow: / and the owner's rule — no client at all
CLIENTS = {"fetcher", "fetcher_plain", "stealthy", "headless", "ndr_workflow"}


class Refused(Exception):
    pass


def host_of(url):
    m = re.match(r"^[a-z]+://([^/:?#]+)", url or "", re.I)
    h = (m.group(1) if m else str(url or "")).lower()
    try:
        return h.encode("idna").decode()                # наш.дом.рф -> xn--80az8a.xn--d1aqf.xn--p1ai
    except UnicodeError:
        return h


def site_of(url):
    h = host_of(url)
    for s in list(PROTECTED) + ["donstroy.moscow"]:
        if h == s or h.endswith("." + s):
            return s
    return ".".join(h.split(".")[-2:])


def _cooldown():
    spec = importlib.util.spec_from_file_location("cooldown", HERE / "cooldown.py")
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


def allowed(url, client):
    h, s = host_of(url), site_of(url)
    if h in NEVER:
        return False, f"{h}: never contacted (robots Disallow: /, CLAUDE.md)"
    ok = PROTECTED.get(s)
    if ok is not None and client not in ok:
        return False, f"{s}: client «{client}» not allowed, only {sorted(ok)} (L28, L29)"
    return True, ""


def check(url, client, cooldown=True):
    if client not in CLIENTS:
        raise ValueError(f"unknown client {client}")
    ok, why = allowed(url, client)
    if not ok:
        raise Refused(why)
    if cooldown:
        r = _cooldown().active(site_of(url))
        if r:
            raise Refused(f"{site_of(url)} paused until {r['until']}: {r['why']} (L32)")


def shield(target, client="headless", log=None):
    """Abort, inside a Playwright page or context, every request the client may not make (third-party embeds included)."""
    def route(r):
        ok, why = allowed(r.request.url, client)
        if not ok:
            if log:
                log("shield", why, r.request.url[:120])
            return r.abort()
        return r.fallback() if hasattr(r, "fallback") else r.continue_()
    target.route("**/*", route)


if __name__ == "__main__":
    if len(sys.argv) == 4 and sys.argv[1] == "check":
        try:
            check(sys.argv[2], sys.argv[3])
            print("ok")
        except Refused as e:
            print("refused:", e)
            sys.exit(1)
    else:
        print(__doc__)
