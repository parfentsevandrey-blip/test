"""Recon for the adapter factory (engine step 2): open one allowed page as a visitor and wait for the data request the page itself
makes (no request of ours to the site's API). Prints the response's shape: status, size, top-level keys, list lengths, totals and
one item's keys — enough to decide whether the page gives the whole catalogue in ≤ 50 requests. Nothing raw is saved.

usage: recon_xhr.py <url> <response-url-regex> [wait_s=90]
"""
import importlib.util
import json
import re
import sys
from pathlib import Path

from patchright.sync_api import sync_playwright

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
        return {k: shape(v, depth + 1) for k, v in list(o.items())[:40]}
    if isinstance(o, list):
        return [f"list[{len(o)}]", shape(o[0], depth + 1) if o else None]
    return type(o).__name__ if not isinstance(o, (int, float)) else o


def main(url, pattern, wait_s=90):
    cp, V = _mod("clientpolicy"), _mod("visitor")
    cp.check(url, "headless")
    site = ".".join(url.split("/")[2].split(".")[-2:])
    with sync_playwright() as p:
        b = p.chromium.launch(headless=True)
        pg = b.new_page(locale="ru-RU", timezone_id="Europe/Moscow")
        V.lean(pg, site)
        cp.shield(pg, "headless", print)
        with pg.expect_response(lambda r: re.search(pattern, r.url) is not None, timeout=wait_s * 1000) as resp:
            pg.goto(url, wait_until="domcontentloaded", timeout=90000)
        r = resp.value
        body = r.body()
        req = r.request.post_data or ""
        b.close()
    print("status", r.status, "bytes", len(body), "type", r.headers.get("content-type"))
    print("request body", req[:400])
    try:
        j = json.loads(body)
    except Exception:
        print("not JSON; head:", body[:300])
        return
    print(json.dumps(shape(j), ensure_ascii=False)[:3000])


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else 90)
