"""ПИК inside MKAD on ЦИАН (owner's task 17.10, NOTES.md): find the ЖК of builder ПИК (ЦИАН builder id 9) from the allowed SEO pages
«новостройки от застройщика ПИК — Москва — <район / метро>» listed in the ЦИАН sitemap, keep those inside MKAD by coordinates
(list pages carry none — matched with the наш.дом.рф scan inside MKAD, moscow_scan/scan.json), write pik_zk.json: ЦИАН ЖК id,
name, district, metro, offers from the builder, pages it came from.

Manners as the collector: Fetcher only (clientpolicy), 20–40 s between pages, stop and pause ЦИАН for 24 h on 403/429/captcha (L32).
usage: pik_cian.py run [max_pages] | match (scan + modern filter) | status
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
    """ЖК of the page from its initial state (offersData.newbuildings): id, name, okrug, district, metro, offers from the builder.
    List pages carry no coordinates: inside/outside MKAD is decided later by matching with the наш.дом.рф scan (match_scan)."""
    cs = _mod(HERE / "lib" / "cianstate.py", "cianstate")
    st = cs.initial_state(html, "newbuilding-search-frontend") or {}
    od = st.get("offersData") or {}
    out = []
    for n in od.get("newbuildings") or []:
        if not any(str(b.get("id")) == "9" for b in n.get("builders") or []):
            continue                                          # only ПИК (ЦИАН builder 9)
        addr = [a.get("title") for a in n.get("addressLine") or []]
        out.append({"id": str(n["id"]), "name": n.get("name") or n.get("displayName"), "okrug": addr[1] if len(addr) > 1 else "",
                    "district": addr[2] if len(addr) > 2 else "", "metro": (n.get("undergroundInfo") or {}).get("name", ""),
                    "offers": n.get("fromDeveloperPropsCount"), "status": n.get("specialStatusDisplay", ""), "url": n.get("url", "")})
    return out, od.get("total")


def _norm(s):
    s = re.sub(r"(?i)жилой комплекс|жилой квартал|\bжк\b|квартал|[\"«»'.,]", " ", s or "")
    return re.sub(r"\s+", " ", s).strip().lower().replace("ё", "е")


def match_scan():
    """Inside MKAD = found in the наш.дом.рф scan of objects inside MKAD (moscow_scan/scan.json, coordinates there):
    same normalised name, or one name inside the other within the same district. Unmatched ones are listed for a check."""
    scan = json.loads((HERE.parent / "moscow_scan" / "scan.json").read_text())["complexes"]
    zk = json.loads(OUT.read_text())
    for z in zk.values():
        zn, zd = _norm(z["name"]), _norm(z.get("district")).replace(" район", "").replace("район ", "")
        hit = None
        for c in scan:
            cn, cd = _norm(c.get("name")), _norm(c.get("district")).replace(" район", "").replace("район ", "")
            if cn == zn or ((zn in cn or cn in zn) and min(len(zn), len(cn)) >= 4 and (not zd or zd == cd)):
                hit = c
                break
        z["ndr"] = {k: hit.get(k) for k in ("name", "developer", "class", "district", "lat", "lon", "flats", "remaining", "completion")} if hit else None
        z["inside_mkad"] = bool(hit)
    OUT.write_text(json.dumps(zk, ensure_ascii=False, indent=1))
    return zk


def modern():
    """The approved «только современные» filter (research/engine/config.yaml, MODERN_FILTER.md) on the ПИК ЖК inside MKAD:
    OK, M1 no flats, M2 renovation, M3 old stock, M4 year, M5 economy (комфорт/типовой below COMFORT_PLUS_MIN_M2), G1 grey zone."""
    import statistics
    import yaml
    cfg = yaml.safe_load((HERE.parents[2] / "engine" / "config.yaml").read_text()) if (HERE.parents[2] / "engine").exists() else None
    if cfg is None:
        cfg = yaml.safe_load(Path("/home/user/test/research/engine/config.yaml").read_text())
    f = cfg["modern_filter"]
    scan = {c["name"]: c for c in json.loads((HERE.parent / "moscow_scan" / "scan.json").read_text())["complexes"]}
    zk = json.loads(OUT.read_text())
    for z in zk.values():
        n = z.get("ndr")
        if not n:
            z["modern"] = None
            continue
        c = scan.get(n["name"], {})
        prices = [x for x in (c.get("monthly_price_m2") or []) if x]
        med = statistics.median(prices) if prices else None
        year = int(str(c.get("published") or "0000")[-4:] or 0)
        cls = [s.strip() for s in str(c.get("class") or "").split("/")]
        if not c.get("flats"):
            v = "M1"
        elif re.search(f["exclude_developers_re"], c.get("developer") or "", re.I):
            v = "M2"
        elif re.search(f["exclude_names_re"], c.get("name") or "", re.I):
            v = "M3"
        elif year and year < f["MIN_START_YEAR"]:
            v = "M4"
        elif any(k in f["classes_in"] for k in cls):
            v = "OK"
        elif med is None:
            v = "G1"
        else:
            v = "OK" if med >= f["COMFORT_PLUS_MIN_M2"] else "M5"
        z["modern"] = {"verdict": v, "class": c.get("class"), "median_m2_12m": round(med) if med else None, "published": c.get("published")}
    OUT.write_text(json.dumps(zk, ensure_ascii=False, indent=1))
    return zk


def run(max_pages=70):
    cp = _mod(HERE / "lib" / "clientpolicy.py", "clientpolicy")
    cd = _mod(HERE / "lib" / "cooldown.py", "cooldown")
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
        got, total = zk_from_state(html) if r.status == 200 else ([], None)
        for z in got:
            zk.setdefault(z["id"], z)
            zk[z["id"]]["pages"] = sorted(set(zk[z["id"]].get("pages", []) + [p["url"].split("moskva-")[1].rstrip("/")]))
        p.update(done=True, status=r.status, zk=len(got), total=total)
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
    more = [p["url"] for p in pages if p.get("total") and p["total"] > p.get("zk", 0)]
    if more:
        print("pages with more ЖК than shown:", more)
    print(f"pages {sum(1 for p in pages if p.get('done'))}/{len(pages)}, ЖК {len(zk)}, inside MKAD {len(ins)}")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "match":
        match_scan(); modern(); status()
    elif len(sys.argv) > 1 and sys.argv[1] == "run":
        run(int(sys.argv[2]) if len(sys.argv) > 2 else 70)
    else:
        status()
