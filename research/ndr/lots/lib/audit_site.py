"""Audit one web resource with REA: visit an allowed page like a visitor (real browser, HAR), let REA decode the capture and
group the page's requests into an API surface, and summarise what the page loads: requests, bytes, embedded state, and every
own-origin data response (JSON shape: keys and list sizes). Output: <out_dir>/<name>.json (+ the HAR and REA evidence).

usage: audit_site.py <name> <url> <out_dir> [click-text]
"""
import base64
import json
import re
import subprocess
import sys
import urllib.parse
from pathlib import Path

from patchright.sync_api import sync_playwright

STATE = {"__NEXT_DATA__": "Next.js state", "window.__NUXT__": "Nuxt state", "_cianConfig": "ЦИАН state", "var flat_info": "Донстрой flat_info",
         "__INITIAL_STATE__": "initial state", "application/ld+json": "JSON-LD"}


def shape(o, depth=0):
    if depth > 2:
        return type(o).__name__
    if isinstance(o, dict):
        return {k: shape(v, depth + 1) for k, v in list(o.items())[:25]}
    if isinstance(o, list):
        return [f"list[{len(o)}]", shape(o[0], depth + 1) if o else None]
    return type(o).__name__


def body(e):
    c = e["response"].get("content", {})
    t = c.get("text")
    if t is None:
        return None
    return base64.b64decode(t).decode("utf-8", "replace") if c.get("encoding") == "base64" else t


def _policy():
    """lib/clientpolicy.py: which client may contact which site (L28, L29, L37)."""
    import importlib.util
    from pathlib import Path as _P
    spec = importlib.util.spec_from_file_location("clientpolicy", _P(__file__).resolve().parent / "clientpolicy.py")
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


def main(name, url, out, click=None):
    out = Path(out).resolve()                         # REA wants absolute paths
    out.mkdir(parents=True, exist_ok=True)
    har = out / f"{name}.har"
    cp = _policy()
    cp.check(url, "headless")       # ЦИАН, наш.дом.рф, lsr.ru: never with this browser (L28, L29 — the 403 of 17.10 00:02)
    with sync_playwright() as p:
        b = p.chromium.launch(headless=True)
        ctx = b.new_context(locale="ru-RU", timezone_id="Europe/Moscow", record_har_path=str(har), record_har_content="embed")
        cp.shield(ctx, "headless", print)                # embeds of protected sites (dreamriva.ru -> api.cian.ru, L37)
        pg = ctx.new_page()
        pg.goto(url, wait_until="domcontentloaded", timeout=90000)
        pg.wait_for_timeout(8000)
        pg.mouse.wheel(0, 4000)
        pg.wait_for_timeout(2500)
        clicked = None
        if click:
            el = pg.locator("button:visible, a:visible", has_text=re.compile(click, re.I)).first
            if el.count():
                clicked = el.text_content().strip()[:60]
                el.click(timeout=15000)
                pg.wait_for_timeout(6000)
        title = pg.title()
        ctx.close()
        b.close()
    h = json.loads(har.read_text())
    host = urllib.parse.urlsplit(url).hostname
    site = ".".join(host.split(".")[-2:])
    entries = h["log"]["entries"]
    total_bytes = sum(max(e["response"].get("bodySize") or 0, e["response"].get("content", {}).get("size") or 0) for e in entries)
    doc = next((e for e in entries if e["request"]["url"].split("#")[0] == url.split("#")[0] or e.get("_resourceType") == "document"), None)
    html = body(doc) if doc else ""
    data = []
    for e in entries:
        u = e["request"]["url"]
        uh = urllib.parse.urlsplit(u).hostname or ""
        mime = (e["response"].get("content", {}).get("mimeType") or "").lower()
        if not uh.endswith(site) or "json" not in mime or e["response"]["status"] != 200:
            continue
        t = body(e)
        try:
            j = json.loads(t) if t else None
        except Exception:
            j = None
        data.append({"method": e["request"]["method"], "url": urllib.parse.unquote(u)[:300], "bytes": len(t or ""),
                     "post": (e["request"].get("postData") or {}).get("text", "")[:300], "shape": shape(j) if j is not None else None})
    # REA: decode the capture and derive the API surface (own origin and third parties)
    clean = out / f"{name}.clean.har"
    keep = []
    for e in entries:
        if not e["request"]["url"].startswith("http"):
            continue
        e["request"]["url"] = urllib.parse.quote(e["request"]["url"], safe=":/?&=%#@+,;~!$'()*[]")
        if e.get("_resourceType") in ("document", "xhr", "fetch") or "json" in (e["response"].get("content", {}).get("mimeType") or ""):
            keep.append(e)
    h["log"]["entries"] = keep
    clean.write_text(json.dumps(h))
    ev = subprocess.run(["rea", "inspect-web-network-capture", str(clean), "har", "--format", "json"], capture_output=True, text=True, timeout=600)
    surface, rea_err = [], None
    try:
        evd = json.loads(ev.stdout)
        (out / f"{name}.rea.json").write_text(ev.stdout)
        vin = out / f"{name}.view_in.json"
        vin.write_text(json.dumps({"source": {"kind": "inline", "evidence": evd}, "view": {"kind": "api-surface", "include_query_parameter_names": True, "limit": 200}}))
        vr = json.loads(subprocess.run(["rea", "inspect-analysis-view", str(vin), "--format", "json"], capture_output=True, text=True, timeout=600).stdout)
        for g in vr["normalized_result"]["groups"]:
            surface.append({"method": g["method"], "origin": g["origin"], "path": g["path_template"], "n": len(g["record_ordinals"]),
                            "status": g["statuses"], "type": g["content_types"], "query": [q["name"] for q in g.get("query_parameter_names", [])]})
        evidence_id = evd.get("evidence_id")
    except Exception as e:
        rea_err, evidence_id = f"{e}: {ev.stdout[:300]} {ev.stderr[:200]}", None
    res = {"name": name, "url": url, "title": title, "clicked": clicked, "requests": len(entries), "bytes": total_bytes,
           "third_party_requests": sum(1 for e in entries if not (urllib.parse.urlsplit(e["request"]["url"]).hostname or "").endswith(site)),
           "document_bytes": len(html or ""), "embedded_state": [v for k, v in STATE.items() if k in (html or "")],
           "captcha_or_check": any(x in (html or "").lower() for x in ("captcha", "servicepipe", "vpnblock")) and not any(k in (html or "") for k in STATE),
           "own_data_responses": data, "rea_evidence_id": evidence_id, "rea_error": rea_err,
           "rea_api_surface_own": [s for s in surface if (urllib.parse.urlsplit(s["origin"]).hostname or "").endswith(site)],
           "rea_api_surface_third_party_origins": sorted({s["origin"] for s in surface if not (urllib.parse.urlsplit(s["origin"]).hostname or "").endswith(site)})}
    (out / f"{name}.json").write_text(json.dumps(res, ensure_ascii=False, indent=1))
    print(f"{name}: {res['requests']} requests, {round(res['bytes'] / 1e6, 1)} MB, document {round(res['document_bytes'] / 1e3)} KB, "
          f"state {res['embedded_state']}, own JSON {len(data)}, REA groups own {len(res['rea_api_surface_own'])}, check {res['captcha_or_check']}")


if __name__ == "__main__":
    main(*sys.argv[1:4], sys.argv[4] if len(sys.argv) > 4 else None)
