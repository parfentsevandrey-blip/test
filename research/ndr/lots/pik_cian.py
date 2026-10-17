"""ПИК inside MKAD on ЦИАН (owner's task 17.10, NOTES.md): find the ЖК of builder ПИК (ЦИАН builder id 9) from the allowed SEO pages
«новостройки от застройщика ПИК — Москва — <район / метро>» listed in the ЦИАН sitemap, keep those inside MKAD by coordinates
(moscow_scan/mkad.py), write pik_zk.json: ЦИАН ЖК id, name, coordinates, offers count shown, page it came from.

Manners as the collector: Fetcher only (clientpolicy), 20–40 s between pages, stop and pause ЦИАН for 24 h on 403/429/captcha (L32).
usage: pik_cian.py run [max_pages]      pik_cian.py status
"""
import importlib.util
import json
import random
import re
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / "pik_zk.json"
PAGES = HERE / "pik_pages.json"            # the page list (from the sitemap), with what each gave
OUTSIDE = re.compile(r"buninskaya|butovo|filatov|kotelniki|lesnoy-gorodok|lyubercy|mitino|myakinino|ostafevo|panki|pyatnickoe|"
                     r"prokshino|shcerbinka|uhtomskaya|ulica-dmitrievskogo|ulica-gorchakova|ulica-skobelevskaya|"
                     r"ulica-starokachalovskaya|vnukovo|zhulebino|penyagino|volokolamskaya|kosino")   # by name; coordinates decide


def _mod(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


def zk_from_state(html):
    """Every ЖК object in the page's embedded state: id, name, coordinates, builder, offers."""
    cs = _mod(HERE / "lib" / "cianstate.py", "cianstate")
    found = {}

    def walk(o, depth=0):
        if depth > 12:
            return
        if isinstance(o, dict):
            nid = o.get("newbuildingId") or (o.get("id") if ("fullAddress" in o or "geo" in o or "coordinates" in o) else None)
            name = o.get("name") or o.get("displayName") or o.get("title")
            geo = o.get("coordinates") or o.get("geo") or {}
            lat = geo.get("lat") if isinstance(geo, dict) else None
            lng = (geo.get("lng") or geo.get("lon")) if isinstance(geo, dict) else None
            if nid and name and lat and lng:
                found[str(nid)] = {"id": str(nid), "name": name, "lat": float(lat), "lon": float(lng),
                                   "builder": json.dumps(o.get("builders") or o.get("builder") or "", ensure_ascii=False)[:200],
                                   "offers": o.get("offersCount") or o.get("flatsCount")}
            for v in o.values():
                walk(v, depth + 1)
        elif isinstance(o, list):
            for v in o:
                walk(v, depth + 1)
    walk(cs.configs(html) or {})                            # {project: {key: value}}
    return list(found.values())


def run(max_pages=70):
    cp = _mod(HERE / "lib" / "clientpolicy.py", "clientpolicy")
    cd = _mod(HERE / "lib" / "cooldown.py", "cooldown")
    mk = _mod(HERE.parent / "moscow_scan" / "mkad.py", "mkad")
    from scrapling.fetchers import Fetcher
    pages = json.loads(PAGES.read_text())
    zk = json.loads(OUT.read_text()) if OUT.exists() else {}
    done = 0
    for p in pages:
        if p.get("done") or OUTSIDE.search(p["url"]) or done >= max_pages:
            continue
        if cd.active("cian.ru"):
            print("ЦИАН paused:", cd.active("cian.ru")["until"]); break
        cp.check(p["url"], "fetcher", cooldown=False)
        r = Fetcher.get(p["url"], impersonate="chrome", timeout=60, retries=1, stealthy_headers=True, follow_redirects=False,
                        headers={"Accept-Language": "ru-RU,ru;q=0.9"})
        html = r.body.decode("utf-8", "replace") if isinstance(r.body, bytes) else str(r.body)
        if r.status in (403, 429) or ("captcha" in html.lower() and "_cianConfig" not in html):
            cd.set_block("cian.ru", 24, f"{r.status} on {p['url']} (pik_cian.py)")
            print("BLOCKED", r.status, p["url"]); break
        got = zk_from_state(html) if r.status == 200 else []
        for z in got:
            z["inside_mkad"] = bool(mk.inside(z["lat"], z["lon"]))
            z.setdefault("pages", [])
            zk.setdefault(z["id"], z)
            zk[z["id"]]["pages"] = sorted(set(zk[z["id"]].get("pages", []) + [p["url"].split("moskva-")[1].rstrip("/")]))
        p.update(done=True, status=r.status, zk=len(got))
        done += 1
        PAGES.write_text(json.dumps(pages, ensure_ascii=False, indent=1))
        OUT.write_text(json.dumps(zk, ensure_ascii=False, indent=1))
        print(time.strftime("%H:%M:%S"), r.status, p["url"].split("moskva-")[1], "ЖК", len(got), "всего", len(zk), flush=True)
        time.sleep(random.uniform(20, 40))
    status()


def status():
    zk = json.loads(OUT.read_text()) if OUT.exists() else {}
    pages = json.loads(PAGES.read_text()) if PAGES.exists() else []
    ins = [z for z in zk.values() if z.get("inside_mkad")]
    print(f"pages {sum(1 for p in pages if p.get('done'))}/{len(pages)}, ЖК {len(zk)}, inside MKAD {len(ins)}")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "run":
        run(int(sys.argv[2]) if len(sys.argv) > 2 else 70)
    else:
        status()
