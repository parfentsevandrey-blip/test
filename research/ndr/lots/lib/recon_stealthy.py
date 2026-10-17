"""Recon for sites behind a JS check (lsr.ru, Servicepipe): one visit in Scrapling StealthySession — the only client the policy lets
on these sites (L28) — recording every own-site response the page loads itself (XHR/fetch, JSON). Prints, per data response: method,
URL, status, size, shape. Nothing raw is saved. Engine step 2: does the catalogue come as a JSON list (page size, total, sort)?

usage: recon_stealthy.py <url> [scroll_times=3]
"""
import importlib.util
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent


def _mod(name):
    spec = importlib.util.spec_from_file_location(name, HERE / f"{name}.py")
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


def shape(o, depth=0):
    if depth > 3:
        return type(o).__name__
    if isinstance(o, dict):
        return {k: shape(v, depth + 1) for k, v in list(o.items())[:30]}
    if isinstance(o, list):
        return [f"list[{len(o)}]", shape(o[0], depth + 1) if o else None]
    return type(o).__name__ if not isinstance(o, (int, float)) else o


def main(url, scrolls=3):
    from scrapling.fetchers import StealthySession
    cp = _mod("clientpolicy")
    cp.check(url, "stealthy")
    site = ".".join(url.split("/")[2].split(".")[-2:])
    seen = []

    def setup(page):
        cp.shield(page, "stealthy", print)           # no embed of ЦИАН / наш.дом.рф from inside the page (L37)

        def on_resp(r):
            if r.request.resource_type in ("xhr", "fetch") and site in r.url:
                try:
                    body = r.body()
                except Exception:
                    body = b""
                seen.append((r.request.method, r.url, r.status, r.headers.get("content-type", ""), body, r.request.post_data or ""))
        page.on("response", on_resp)

    def act(page):
        for _ in range(scrolls):
            page.mouse.wheel(0, 3000)
            page.wait_for_timeout(2500)
        return page

    with StealthySession(headless=True, network_idle=False, wait=4000, timeout=120000, google_search=False, locale="ru-RU",
                         timezone_id="Europe/Moscow", disable_resources=True, block_ads=True) as s:
        p = s.fetch(url, page_setup=setup, page_action=act)
    html = p.body.decode("utf-8", "replace") if isinstance(p.body, bytes) else str(p.body)
    print("document", p.status, len(html), "listingCard" in html, "__NEXT_DATA__" in html, "__NUXT__" in html)
    for m, u, st, ct, body, post in seen:
        line = f"{m} {u[:150]} {st} {ct[:30]} {len(body)}"
        if "json" in ct:
            try:
                line += " " + json.dumps(shape(json.loads(body)), ensure_ascii=False)[:1200]
            except Exception:
                pass
        print(line, ("post " + post[:200]) if post else "")


if __name__ == "__main__":
    main(sys.argv[1], int(sys.argv[2]) if len(sys.argv) > 2 else 3)
