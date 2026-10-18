"""[С1] Developer price lists: which flats are on sale on the developers' own sites, day by day.

Sources (all paths allowed by each site's robots.txt, checked 16.10.2026; see NOTES.md):
  highlife.ru /search          — whole catalog in __NEXT_DATA__, one page, with sold/reserved status   (flat level)
  granelle.ru /objects/<slug>/ — layouts with flat_count on sale + a few listed lots                     (layout level)
  forma.ru /market/projects/soul — buildings x rooms: flats on sale, price range, view attributes       (building x rooms)
  donstroy.moscow sitemap-flats.xml — every flat on sale; flat pages for price, area, views            (flat level)
  fsk.ru sitemap/flats/N.xml   — every flat on sale; flat pages for price, area, views                  (flat level)
  lsr.ru /msk/kvartiry-v-novostroikah/zilart/?page=N — ZILART (zilart.ru itself is Disallow: /, the developer's main site
                                 lists every flat; robots.txt: «Allow: *?page*»). Behind a Servicepipe JS check: real browser   (flat level)
  dreamriva.ru /flats/         — official Dream Riva site (dream-riva.ru is a third-party landing): whole catalog in
                                 __NEXT_DATA__ with views, sides, options, «Жемчужная коллекция», views today                 (flat level)

Special characteristics are kept for every flat where the site shows them (views, sides, options, collections, labels,
promo, discounts) in info.special; «просмотров сегодня» shown by a site goes to `demand` (С3).

Groups run as separate processes so that sites are walked in parallel, each with its own 20-40 s pace:
  lists    — one-page catalogs and sitemaps of every developer (presence of every flat every day)
  lsr      — ZILART list pages (browser)
  visitor  — Granelle (4 ЖК) and Soul flat by flat: a real browser opens the project page (allowed), follows the site's own
             link to the project's flats, sorts by price and presses «Показать еще» every 20-40 s like a visitor; we keep
             the JSON the page itself receives and never call the site's API ourselves (lib/visitor.py)
  donstroy — Donstroy flat pages (new first, then the oldest price; weekly refresh)
  fsk      — FSK flat pages (same)
  dsections — Донстрой: one floor page per section (51) in a browser; the page loads every flat of the section with its
             status, sold ones included (found with REA: POST /api/v1/floor/choose_floor/ in a HAR of the visit, NOTES.md) ->
             table stock: the whole stock of a building, so the share left is exact as for High Life
No captcha solving, stop on 403/429/captcha.
"""
import json
import random
import re
import sqlite3
import subprocess
import sys
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
DB = HERE / "devcat.db"
LOG = HERE / "devcat.log"
NUXT_JS = HERE / "lib" / "nuxt_eval.js"
MSK = timezone(timedelta(hours=3))
GAP = (20, 40)
GAP_HOST = {"fsk.ru": (60, 100)}   # fsk.ru answered 429 after ~90 pages at 20-40 s on 16.10: slower there
PAGE_BUDGET = 400              # flat pages per day per site (Donstroy, FSK); each site has its own process and pace
GROUPS = ("lists", "lsr", "visitor", "donstroy", "fsk")
PRICE_REFRESH_DAYS = 7         # instruction С1: weekly price list
FSK_TREE = True                # engine step 2: ФСК catalogue page loads /api/v3/flats/tree itself — the whole project in one response
DON_SEARCH = True              # engine step 2: Donstroy prices from the page /full-search/ loads itself (old path: flat pages)
DON_SEARCH_ENOUGH = 0.95       # a pass cut by a restart counts as done when this week's list priced ≥ 95% of available flats (L44)
MAX_FAILS = 3

# developer site project -> ЦИАН newobject id used in lots.db / pilot_zk.json
ZK = {
    ("highlife", "high-life"): "3202206",
    ("granelle", "mypriority-dubrovka"): "3401924",
    ("granelle", "mypriority-nizhegorodskaya"): "5699304",
    ("granelle", "mypriority-paveletskaya"): "4494942",
    ("granelle", "mypriority-mnevniki"): "5221539",
    ("forma", "soul"): "4183417",
    ("donstroy", "ostrov"): "2812286",
    ("donstroy", "simvol"): "7231",
    ("fsk", "amber-city"): "4888961",
    ("fsk", "sydney-city"): "2446053",
    ("fsk", "sydney-prime"): "4377697",
    ("lsr", "zilart"): "7889",
    ("dreamriva", "dream-riva"): "5340468",
}

ALLOWED = [re.compile(p) for p in (
    r"https://highlife\.ru/search$",
    r"https://granelle\.ru/objects/[a-z0-9\-]+/$",
    r"https://forma\.ru/market/projects/[a-z0-9\-]+$",
    r"https://donstroy\.moscow/sitemap-flats\.xml$",
    r"https://donstroy\.moscow/objects/[a-z0-9\-]+/genplan/quarter\d+/korpus\d+/section\d+/floor\d+/$",
    r"https://donstroy\.moscow/objects/[a-z0-9\-]+/plans/quarter\d+/korpus\d+/section\d+/floor\d+/flat\d+/$",
    r"https://fsk\.ru/sitemap\.xml$",
    r"https://fsk\.ru/sitemap/flats/\d+\.xml$",
    r"https://fsk\.ru/[a-z0-9\-]+/flats/\d+$",
    r"https://www\.lsr\.ru/msk/kvartiry-v-novostroikah/zilart/(\?page=\d+)?$",
    r"https://dreamriva\.ru/flats/$",
    r"https://granelle\.ru/flats/[a-z0-9\-]+/\d+/$",          # flat pages: qa.py re-reads a sample (allowed: no query)
)]

SCHEMA = """
create table if not exists runs (day text primary key, started text, finished text, status text);
create table if not exists fetches (day text, url text, status text, at text);
create table if not exists seen (day text, source text, zk_id text, lot_id text, status text, price real,
                                 primary key (day, source, lot_id));
create table if not exists info (source text, lot_id text, zk_id text, url text, kind text, seller text, korpus text,
                                 section text, floor int, floors int, rooms int, area real, finish text, views text,
                                 sides text, completion text, labels text, sold int, updated text, special text,
                                 primary key (source, lot_id));
create table if not exists declared (day text, source text, zk_id text, n int, primary key (day, source, zk_id));
create table if not exists stock (day text, source text, zk_id text, section text, internal_id text, floor int, available int,
                                  status text, primary key (day, source, internal_id));
create table if not exists demand (day text, source text, lot_id text, views_today int, primary key (day, source, lot_id));
create table if not exists gruns (day text, grp text, started text, finished text, status text, primary key (day, grp));
create table if not exists prices (source text, lot_id text, day text, price real, price_base real, origin text,
                                   primary key (source, lot_id, day, origin));
create table if not exists agg (day text, source text, zk_id text, building text, rooms int, key text, area_min real,
                                area_max real, n int, price_min real, price_max real, completion text, finish text,
                                attrs text, primary key (day, source, zk_id, key));
"""


def now():
    return datetime.now(MSK).isoformat(timespec="seconds")


def today():
    return datetime.now(MSK).strftime("%Y-%m-%d")


def log(*a):
    line = f"{now()} " + " ".join(str(x) for x in a)
    print(line, flush=True)
    with LOG.open("a") as f:
        f.write(line + "\n")


class Blocked(Exception):
    pass


_last = {}          # host -> time of the last request (each site keeps its own 20-40 s pace)
_browser = {}       # one real-browser session per process, opened on first use


def _browser_get(url):
    """Real browser (Scrapling StealthySession) for sites behind a JS check that any browser passes (lsr.ru, Servicepipe).
    No captcha solving: a page that still shows a challenge after loading counts as blocked."""
    from scrapling.fetchers import StealthySession
    if "s" not in _browser:
        _browser["s"] = StealthySession(headless=True, network_idle=False, wait=4000, timeout=120000, google_search=False,
                                        locale="ru-RU", timezone_id="Europe/Moscow",
                                        disable_resources=True, block_ads=True)   # AUDIT.md: no images, fonts, media, ads
        _browser["s"].__enter__()
    p = _browser["s"].fetch(url)
    return p.status, (p.body.decode("utf-8", "replace") if isinstance(p.body, bytes) else str(p.body))


def close_browser():
    if "s" in _browser:
        _browser.pop("s").__exit__(None, None, None)


def _cp():
    if "m" not in _cp.__dict__:
        import importlib.util
        spec = importlib.util.spec_from_file_location("clientpolicy", HERE / "lib" / "clientpolicy.py")
        _cp.m = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(_cp.m)
    return _cp.m


def fetch(db, url, browser=False):
    from scrapling.fetchers import Fetcher
    if not any(p.match(url) for p in ALLOWED):
        raise ValueError(f"not allowed by robots policy: {url}")
    _cp().check(url, "stealthy" if browser else "fetcher", cooldown=False)    # L28, L29, L37 (pauses: run_group)
    host = url.split("/")[2]
    wait = random.uniform(*GAP_HOST.get(host, GAP)) - (time.time() - _last.get(host, 0))
    if wait > 0:
        time.sleep(wait)
    code, html = None, ""
    for attempt in range(3):
        try:
            if browser:
                code, html = _browser_get(url)
            else:
                r = Fetcher.get(url, impersonate="chrome", timeout=60, retries=1, follow_redirects=False,
                                headers={"Accept-Language": "ru-RU,ru;q=0.9"})
                code, html = r.status, (r.body.decode("utf-8", "replace") if isinstance(r.body, bytes) else str(r.body))
            break
        except Exception as e:
            log("network error", attempt + 1, url, str(e)[:120])
            time.sleep(10 * (attempt + 1))
    _last[host] = time.time()
    status = "network" if code is None else str(code)
    # a captcha / challenge page has none of the data the page should carry (forma.ru links to the SmartCaptcha notice
    # in every footer; lsr.ru answers a plain client with a Servicepipe check)
    data = any(m in html for m in ("__NEXT_DATA__", "__NUXT__", "<urlset", "<sitemapindex", "var flat_info", "listingCard"))
    blocked = code is not None and (code in (403, 429) or ("captcha" in html.lower() or "servicepipe" in html) and not data)
    db.execute("insert into fetches values (?,?,?,?)", (today(), url, "blocked" if blocked else status, now()))
    db.commit()
    if blocked:
        raise Blocked(f"{code} {url}")
    if code is None:
        return None, status
    return (html if code == 200 else None), status


def next_data(html):
    m = re.search(r'<script id="__NEXT_DATA__"[^>]*>(.*?)</script>', html, re.S)
    return json.loads(m.group(1))["props"]["pageProps"] if m else None


def nuxt(html):
    """Nuxt 2 pages keep their state in a pure-data IIFE; evaluate it in an empty node vm (no globals, 5 s timeout)."""
    p = subprocess.run(["node", str(NUXT_JS)], input=html, capture_output=True, text=True, timeout=60)
    return json.loads(p.stdout) if p.returncode == 0 and p.stdout else None


def put_info(db, source, lot_id, **kw):
    kw.update(source=source, lot_id=str(lot_id), updated=today())
    cols = ",".join(kw)
    upd = ",".join(f"{k}=coalesce(excluded.{k},{k})" for k in kw if k not in ("source", "lot_id"))
    db.execute(f"insert into info ({cols}) values ({','.join('?' * len(kw))}) on conflict(source, lot_id) do update set {upd}",
               list(kw.values()))


def put_declared(db, source, zk, n):
    """How many flats the site itself says it has on sale today: the yardstick of completeness (RUNLOG.md)."""
    if n:
        db.execute("insert or replace into declared values (?,?,?,?)", (today(), source, zk, int(n)))


def put_price(db, source, lot_id, price, base=None, origin="page"):
    if price:
        db.execute("insert or replace into prices values (?,?,?,?,?,?)", (source, str(lot_id), today(), float(price),
                                                                        float(base) if base else None, origin))


# ---------- list sources ----------

def highlife(db):
    html, st = fetch(db, "https://highlife.ru/search")
    if not html:
        return f"highlife {st}"
    q = next_data(html)["initialState"]["api"]["queries"]
    data = next(v for k, v in q.items() if k.startswith("getParams"))["data"]
    corp = {c["num"]: c for c in data["corpuses"]}
    zk, day = ZK[("highlife", "high-life")], today()
    for f in data["flats"]:
        c = corp.get(f["corpus"], {})
        # status: «Свободно» on sale, «Резерв»/«Уст.бронь» booked, «Продажа» sold (deal signed)
        db.execute("insert or replace into seen values (?,?,?,?,?,?)", (day, "highlife", zk, str(f["id"]), f["status"],
                                                                      f.get("price_discount") or f.get("price")))
        put_info(db, "highlife", f["id"], zk_id=zk, url="https://highlife.ru/search", kind=f["type"], seller="developer",
                 korpus=f["corpus"], floor=f["floor"], rooms=f["rooms"], area=f["square"], finish=f["finishing"],
                 views=json.dumps(f.get("view_raw") or [], ensure_ascii=False), completion=c.get("term"),
                 labels=json.dumps(f.get("options") or [], ensure_ascii=False), sold=int(f["status"] == "Продажа"),
                 special=json.dumps({"options": f.get("options"), "views": f.get("view_raw"), "promo": f.get("promo"),
                                     "type": f.get("type"), "discount_pct": round(100 * (1 - f["price_discount"] / f["price"]), 1)
                                     if f.get("price") and f.get("price_discount") else None}, ensure_ascii=False))
        if f["status"] != "Продажа":                     # L18: sold flats show a placeholder price, not a real one
            put_price(db, "highlife", f["id"], f.get("price_discount") or f.get("price"), f.get("price"), "catalog")
    put_declared(db, "highlife", zk, len(data["flats"]))
    db.commit()
    return f"highlife {len(data['flats'])}"


def granelle(db, slug):
    html, st = fetch(db, f"https://granelle.ru/objects/{slug}/")
    if not html:
        return f"{slug} {st}"
    d = nuxt(html)
    page = next(x["data"] for x in d["data"] if isinstance(x, dict) and "objectLotsData" in x.get("data", {}))
    zk, day = ZK[("granelle", slug)], today()
    lay = page["layouts"]["layouts"]
    for l in lay:
        key = f"layout{l['id']}"
        db.execute("insert or replace into agg values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", (
            day, "granelle", zk, None, l["rooms"], key, l.get("min_area") or l.get("area"), l.get("max_area") or l.get("area"),
            l["flat_count"], l.get("min_price"), None, f"{l.get('completion_year')}Q{l.get('completion_quarter')}",
            ",".join(l.get("finish_types") or []), json.dumps({"type": l["type"], "euro": l.get("euro"),
                                                                "chars": l.get("layout_characteristic")}, ensure_ascii=False)))
    for f in page["objectLotsData"]["list"]:
        put_info(db, "granelle", f["id"], zk_id=zk, url=f"https://granelle.ru/flats/{slug}/{f['id']}/", kind=f["type"],
                 seller="developer", korpus=f.get("building"), floor=f.get("floor"), floors=f.get("floor_count"),
                 rooms=f.get("rooms"), area=f.get("area"), finish=f.get("finish_type"),
                 views=json.dumps(f.get("window_views") or [], ensure_ascii=False),
                 sides=json.dumps(f.get("cardinal_directions") or [], ensure_ascii=False),
                 completion=f"{f.get('completion_year')}Q{f.get('completion_quarter')}")
        put_price(db, "granelle", f["id"], f.get("current_price"), f.get("price"), "listed")
    db.commit()
    return f"{slug} {sum(l['flat_count'] for l in lay)} in {len(lay)} layouts"


def forma(db, slug="soul"):
    html, st = fetch(db, f"https://forma.ru/market/projects/{slug}")
    if not html:
        return f"forma {st}"
    pp = next_data(html)
    zk, day = ZK[("forma", slug)], today()
    n = 0
    for b in pp["bulkInfo"]:
        for r in b.get("rooms") or []:
            rooms = 0 if r["rooms"] == "studio" else int(r["rooms"])
            db.execute("insert or replace into agg values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", (
                day, "forma", zk, b["name"], rooms, f"bulk{b['id']}-r{r['rooms']}", r["area"]["min"], r["area"]["max"],
                r["flatsCount"], r["price"]["min"], r["price"]["max"], b.get("settlement_date"), ",".join(r.get("finish") or []),
                json.dumps(r.get("attributes") or [], ensure_ascii=False)))
            n += r["flatsCount"]
    db.commit()
    return f"forma {n}"


DREAMRIVA_COLLECTIONS = {"Жемчужная коллекция": "квартиры с видом на реку и панорамой на 270° (подсказка на сайте)"}


def dreamriva(db):
    html, st = fetch(db, "https://dreamriva.ru/flats/")
    if not html:
        return f"dreamriva {st}"
    fl = next_data(html)["initialState"]["flats"]["entities"]
    zk, day = ZK[("dreamriva", "dream-riva")], today()
    for f in fl:
        db.execute("insert or replace into seen values (?,?,?,?,?,?)", (day, "dreamriva", zk, str(f["id"]), f["status"], f.get("price")))
        put_info(db, "dreamriva", f["id"], zk_id=zk, url=f"https://dreamriva.ru/flats/{f['id']}/",
                 kind="penthouse" if f.get("penthouse") else f.get("type"), seller="developer",
                 korpus=f.get("corpus"), floor=f.get("floor"), floors=f.get("floorCount"),
                 rooms=None if f.get("penthouse") and not f.get("rooms") else f.get("rooms"),   # L15: penthouses come as 0 rooms
                 area=f.get("area"),
                 views=json.dumps(f.get("views") or [], ensure_ascii=False), sides=json.dumps([f.get("side")], ensure_ascii=False),
                 labels=json.dumps(f.get("options") or [], ensure_ascii=False), sold=0,
                 special=json.dumps({"collection": f.get("pers_type") or None, "collection_note": DREAMRIVA_COLLECTIONS.get(f.get("pers_type")),
                                     "penthouse": f.get("penthouse"), "options": f.get("options"), "number": f.get("number"),
                                     "discount_pct": round(100 * (1 - f["price"] / f["oldPrice"]), 1)
                                     if f.get("oldPrice") and f.get("price") and f["oldPrice"] > f["price"] else None}, ensure_ascii=False))
        put_price(db, "dreamriva", f["id"], f.get("price"), f.get("oldPrice"), "catalog")
        if str(f.get("flat_views") or "").isdigit():               # «Сегодня просматривали N человек» (С3)
            db.execute("insert or replace into demand values (?,?,?,?)", (day, "dreamriva", str(f["id"]), int(f["flat_views"])))
    put_declared(db, "dreamriva", zk, len(fl))
    db.commit()
    return f"dreamriva {len(fl)}"


LSR_CARD = re.compile(r'class="listingCard listingCard--isFlat[^"]*".*?(?=class="listingCard listingCard--isFlat|data-js-paramsearch-showmore|$)', re.S)


def lsr_cards(html):
    out = []
    for m in LSR_CARD.finditer(html):
        c = m.group(0)
        href = re.search(r'href="/msk/kvartiry-v-novostroikah/([a-z0-9\-]+)/flat/(\d+)/"', c)
        if not href:
            continue
        txt = lambda x: re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", x)).strip()
        main = re.search(r'listingCard__main">(.*?)</div>', c, re.S)
        spans = [txt(x) for x in re.findall(r"<span[^>]*>(.*?)</span>", main.group(1), re.S)] if main else []
        label = [txt(x) for x in re.findall(r"<span[^>]*>(.*?)</span>", (re.search(r'listingCard__label">(.*?)</div>', c, re.S) or [None, ""])[1], re.S)]
        tags = [txt(x) for x in re.findall(r'<div class="tag[^"]*">\s*<span>(.*?)</span>', c, re.S)]
        prices = [int(re.sub(r"\D", "", p)) for p in re.findall(r"([\d\s ]{6,})₽", c)]
        title = spans[0] if spans else ""
        rooms = 0 if title.lower().startswith("студ") else int(re.match(r"(\d)", title).group(1)) if re.match(r"\d", title) else None
        fl = re.search(r"эт\.\s*(\d+)\s*/\s*(\d+)", " ".join(label))
        bar = txt((re.search(r'listingCard__bar[^>]*>(.*?)</div>', c, re.S) or [None, ""])[1])
        out.append({"id": href[2], "sub": href[1], "promo": "isPromotion" in c[:200], "bar": bar or None, "title": title, "rooms": rooms, "euro": "евро" in title.lower(),
                    "area": float(re.match(r"([\d.]+)", spans[1])[1]) if len(spans) > 1 and re.match(r"[\d.]+", spans[1]) else None,
                    "price": min(prices) if prices else None, "price_base": max(prices) if prices else None,
                    "house": label[0] if label else None, "floor": int(fl[1]) if fl else None, "floors": int(fl[2]) if fl else None,
                    "tags": tags, "number": (re.search(r"№\s*(\d+)", title) or [None, None])[1]})
    return out


def lsr(db):
    """ZILART on lsr.ru: list pages ?page=1..N (10 flats each), real browser. Resumes from the last page read today."""
    zk, day, base = ZK[("lsr", "zilart")], today(), "https://www.lsr.ru/msk/kvartiry-v-novostroikah/zilart/"
    done = {r[0] for r in db.execute("select url from fetches where day=? and status='200' and url like ?", (day, base + "%"))}
    have = lambda: db.execute("select count(*) from seen where day=? and source='lsr'", (day,)).fetchone()[0]
    page, n, total, npass = 1, 0, None, 1
    try:
        while True:
            url = base if page == 1 else f"{base}?page={page}"
            if url not in done or npass > 1:
                html, st = fetch(db, url, browser=True)
                if not html:
                    return f"lsr {st} at page {page}"
                cards = lsr_cards(html)
                m = re.search(r"Найдено\s+(\d+)\s+квартир", html)
                total = int(m[1]) if m else total
                for f in cards:
                    db.execute("insert or replace into seen values (?,?,?,?,?,?)", (day, "lsr", zk, f["id"], "listed", f["price"]))
                    completion = next((t for t in f["tags"] if re.search(r"кв\.\s*\d{4}|сдан", t, re.I)), None)
                    finish = next((t for t in f["tags"] if "отделк" in t.lower()), None)
                    put_info(db, "lsr", f["id"], zk_id=zk, url=f"https://www.lsr.ru/msk/kvartiry-v-novostroikah/{f['sub']}/flat/{f['id']}/",
                             kind="flat", seller="developer", korpus=f["house"], floor=f["floor"], floors=f["floors"], rooms=f["rooms"],
                             area=f["area"], finish=finish, completion=completion, sold=0,
                             labels=json.dumps([t for t in f["tags"] if t not in (completion, finish)], ensure_ascii=False),
                             special=json.dumps({"subproject": f["sub"], "euro": f["euro"], "number": f["number"], "tags": f["tags"],
                                                 "promotion": f["promo"], "promo_label": f["bar"],
                                                 "discount_pct": round(100 * (1 - f["price"] / f["price_base"]), 1)
                                                 if f["price"] and f["price_base"] and f["price_base"] > f["price"] else None}, ensure_ascii=False))
                    put_price(db, "lsr", f["id"], f["price"], f["price_base"], "catalog")
                db.commit()
                n += len(cards)
                if not cards:
                    break
                if npass > 1 and total and have() >= total:      # L27: a repeat pass stops once everything is collected
                    break
            page += 1
            if total and (page - 1) * 10 >= total or page > 200:
                if total and have() < total and npass < 3:     # equal prices reorder between pages: walk them again
                    log("lsr", f"pass {npass}: {have()} of {total}, walking the pages again")
                    npass, page = npass + 1, 1
                    continue
                break
    finally:
        close_browser()
    put_declared(db, "lsr", zk, total)
    db.commit()
    return f"lsr passes {npass}, flats today {have()} of {total}"


GRANELLE_SLUGS = [s for (src, s) in ZK if src == "granelle"]


def granelle_flats(db, slug):
    import importlib.util
    spec = importlib.util.spec_from_file_location("visitor", HERE / "lib" / "visitor.py")
    V = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(V)
    try:
        got = V.visit(f"https://granelle.ru/objects/{slug}/", f'css:a[href="/flats/?project={slug}"]', r"Показать еще \d+ из",
                      r"/api/flats/\?", "__NUXT__", sort_clicks=("Сначала дешевле",), gap=GAP, log=log)
    except V.Challenge as e:
        raise Blocked(f"challenge {e}")
    db.execute("insert into fetches values (?,?,?,?)", (today(), f"visitor:granelle:{slug}", "200", now()))
    db.commit()                                        # never hold the write lock through the second visit (L38)
    zk, day = ZK[("granelle", slug)], today()
    flats = {f["id"]: f for g in got if isinstance(g, dict) for f in g.get("results", [])}
    count = max((g.get("count") or 0 for g in got if isinstance(g, dict)), default=0)
    if len(flats) < count:                       # equal prices reorder between pages: walk once more from the other end
        log("visitor", slug, f"{len(flats)} of {count}, second pass «Сначала дороже»")
        got2 = V.visit(f"https://granelle.ru/objects/{slug}/", f'css:a[href="/flats/?project={slug}"]', r"Показать еще \d+ из",
                       r"/api/flats/\?", "__NUXT__", sort_clicks=("Сначала дороже",), gap=GAP, log=log)
        flats.update({f["id"]: f for g in got2 if isinstance(g, dict) for f in g.get("results", [])})
    for f in flats.values():
        db.execute("insert or replace into seen values (?,?,?,?,?,?)", (day, "granelle", zk, str(f["id"]), str(f.get("status")),
                                                                      float(f.get("current_price") or f.get("price") or 0) or None))
        put_info(db, "granelle", f["id"], zk_id=zk, url=f"https://granelle.ru/flats/{slug}/{f['id']}/", kind=f.get("type"), seller="developer",
                 korpus=f.get("building"), section=str(f.get("section")), floor=f.get("floor"), floors=f.get("floor_count"),
                 rooms=f.get("rooms"), area=f.get("area"), finish=f.get("finish_type"),
                 views=json.dumps(f.get("window_views") or [], ensure_ascii=False),
                 sides=json.dumps(f.get("cardinal_directions") or [], ensure_ascii=False),
                 completion=f"{f.get('completion_year')}Q{f.get('completion_quarter')}", sold=0,
                 labels=json.dumps([x for x in (f.get("balconies") or [])] + [x for x in (f.get("layout_characteristic") or [])], ensure_ascii=False),
                 special=json.dumps({"number": f.get("number"), "euro": f.get("euro"), "balconies": f.get("balconies"),
                                     "layout": f.get("layout_characteristic"), "layout_type": f.get("layout_type"),
                                     "ceiling": f.get("ceiling_height"), "relative_discount": f.get("relative_discount"),
                                     "promo": [k for k in ("black_friday", "cyber_monday", "hot_ten_promo", "mortgage_promo", "new_year_promo",
                                                           "shopping", "is_property_of_the_day") if f.get(k)],
                                     "discount_pct": round(100 * (1 - float(f["current_price"]) / f["price"]), 1)
                                     if f.get("price") and f.get("current_price") and float(f["current_price"]) < f["price"] else None},
                                    ensure_ascii=False))
        put_price(db, "granelle", f["id"], f.get("current_price"), f.get("price"), "catalog")
    put_declared(db, "granelle", zk, count)
    db.commit()
    return f"granelle {slug} {len(flats)} of {count}"


def forma_flats(db, slug="soul"):
    import importlib.util
    spec = importlib.util.spec_from_file_location("visitor", HERE / "lib" / "visitor.py")
    V = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(V)
    try:
        got = V.visit(f"https://forma.ru/market/projects/{slug}", r"Показать \d+ квартир", None, r"manager\.forma\.ru/api/v2/marketplace",
                      "__NEXT_DATA__", gap=GAP, log=log)
    except V.Challenge as e:
        raise Blocked(f"challenge {e}")
    db.execute("insert into fetches values (?,?,?,?)", (today(), f"visitor:forma:{slug}", "200", now()))
    zk, day = ZK[("forma", slug)], today()
    flats = {f["id"]: f for g in got if isinstance(g, dict) for f in g.get("flats", [])}
    total = max((g.get("total") or 0 for g in got if isinstance(g, dict)), default=0)
    num = lambda x: float(re.sub(r"[^\d.]", "", str(x))) if x not in (None, "") and re.search(r"\d", str(x)) else None
    for f in flats.values():
        b = f.get("bulk") or {}
        cash = (f.get("benefits") or {}).get("cash") or []
        best = min(cash, key=lambda c: c.get("benefitPrice") or 1e18) if cash else {}
        attrs = [k for k, v in (f.get("attributes") or {}).items() if v]
        db.execute("insert or replace into seen values (?,?,?,?,?,?)", (day, "forma", zk, str(f["id"]), f.get("status"), num(f.get("price"))))
        put_info(db, "forma", f["id"], zk_id=zk, url=f"https://forma.ru/market/flat/{f['id']}", kind="flat", seller="developer",
                 korpus=b.get("name"), section=str((f.get("section") or {}).get("number")), floor=f.get("floor"),
                 floors=(f.get("section") or {}).get("floorsCount"), rooms=0 if f.get("is_studio") else f.get("rooms"), area=f.get("area"),
                 finish=f.get("finishing"), views=json.dumps([a for a in attrs if a.lower().startswith("vid")], ensure_ascii=False),
                 completion=b.get("settlement_date"), labels=json.dumps(attrs, ensure_ascii=False), sold=0,
                 special=json.dumps({"number": f.get("number"), "attributes": attrs, "penthouse": f.get("is_penthouse"),
                                     "status": f.get("status"), "promo": f.get("promo"), "red_price": f.get("redPrice"),
                                     "full_payment": {"name": best.get("name"), "price": best.get("benefitPrice"),
                                                      "discount_pct": best.get("discountPercent")} if best else None}, ensure_ascii=False))
        put_price(db, "forma", f["id"], num(f.get("price")), num(f.get("base_price")), "catalog")
    put_declared(db, "forma", zk, total)
    db.commit()
    return f"forma {slug} {len(flats)} of {total}"


DON_FLOOR = re.compile(r"https://donstroy\.moscow/objects/(ostrov|simvol)/genplan/quarter(\d+)/korpus(\d+)/section(\d+)/floor(\d+)/$")


def donstroy_sections(db, limit=None):
    """One floor page per section, opened in a real browser; keep the section stock the page itself loads."""
    day = today()
    xml, st = fetch(db, "https://donstroy.moscow/sitemap-flats.xml")
    if not xml:
        return f"dsections sitemap {st}"
    first = {}
    for u in sitemap_locs(xml):
        m = DON_FLOOR.match(u)
        if m:
            first.setdefault(m.groups()[:4], u)
    done = {r[0] for r in db.execute("select distinct section from stock where day=? and source='donstroy'", (day,))}
    todo = [(k, u) for k, u in sorted(first.items()) if "-".join(k) not in done][:limit]
    from patchright.sync_api import sync_playwright
    n_flats = 0
    with sync_playwright() as p:
        b = p.chromium.launch(headless=True)
        pg = b.new_page(locale="ru-RU", timezone_id="Europe/Moscow")
        import importlib.util
        spec = importlib.util.spec_from_file_location("visitor", HERE / "lib" / "visitor.py")
        V = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(V)
        V.lean(pg, "donstroy.moscow")                 # no images / fonts / trackers (AUDIT.md)
        _cp().check("https://donstroy.moscow/", "headless", cooldown=False)
        _cp().shield(pg, "headless", log)            # L37: no embed of a protected site from inside the page
        for (obj, q, k, sec), url in todo:
            wait = random.uniform(*GAP) - (time.time() - _last.get("donstroy.moscow", 0))
            if wait > 0:
                time.sleep(wait)
            try:
                with pg.expect_response(lambda r: "/api/v1/floor/choose_floor/" in r.url, timeout=60000) as resp:
                    pg.goto(url, wait_until="domcontentloaded", timeout=90000)
                data = resp.value.json()
                status = "200"
            except Exception as e:
                data, status = None, f"error {str(e)[:60]}"
            _last["donstroy.moscow"] = time.time()
            db.execute("insert into fetches values (?,?,?,?)", (day, "visitor:" + url, status, now()))
            if data is None:
                db.commit()
                if "403" in status or "429" in status:
                    raise Blocked(status)
                continue
            key = "-".join((obj, q, k, sec))
            zk = ZK[("donstroy", obj)]
            for fl in data.get("floors") or []:
                for f in fl.get("flats") or []:
                    db.execute("insert or replace into stock values (?,?,?,?,?,?,?,?)", (day, "donstroy", zk, key, str(f["id"]),
                               int(fl.get("num") or 0), int(bool(f.get("available"))), f.get("prop-status-id")))
                    n_flats += 1
            db.commit()
        b.close()
    tot = db.execute("select count(*), sum(available) from stock where day=? and source='donstroy'", (day,)).fetchone()
    return f"dsections {len(first)} sections, flats {tot[0]} (available {tot[1]})"


def sitemap_locs(xml):
    return re.findall(r"<loc>\s*([^<\s]+)\s*</loc>", xml)


DON = re.compile(r"https://donstroy\.moscow/objects/([a-z0-9\-]+)/plans/quarter(\d+)/korpus(\d+)/section(\d+)/floor(\d+)/flat(\d+)/$")
FSK = re.compile(r"https://fsk\.ru/([a-z0-9\-]+)/flats/(\d+)$")


def donstroy_list(db):
    xml, st = fetch(db, "https://donstroy.moscow/sitemap-flats.xml")
    if not xml:
        return f"donstroy {st}"
    day, n = today(), 0
    for u in sitemap_locs(xml):
        m = DON.match(u)
        if not m or ("donstroy", m[1]) not in ZK:
            continue
        zk = ZK[("donstroy", m[1])]
        db.execute("insert or replace into seen values (?,?,?,?,?,?)", (day, "donstroy", zk, m[6], "listed", None))
        db.execute("insert or ignore into info (source, lot_id, zk_id, url, korpus, section, floor, updated) values (?,?,?,?,?,?,?,?)",
                   ("donstroy", m[6], zk, u, f"{m[2]}-{m[3]}", m[4], int(m[5]), None))
        n += 1
    db.commit()
    return f"donstroy {n}"


def donstroy_search(db, max_clicks=200):
    """Engine step 2 (ADAPTERS.md): /full-search/ loads `choose_params_api_flats` itself, 12 flats per «Показать еще» with price,
    price before discount, area, rooms, корпус, section, floor and the flat link (= our lot id). ≈160 portions instead of 1 179 pages."""
    import importlib.util
    spec = importlib.util.spec_from_file_location("visitor", HERE / "lib" / "visitor.py")
    V = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(V)
    try:
        got = V.visit("https://donstroy.moscow/full-search/", None, r"(Показать|Загрузить)\s*(ещё|еще|больше)", r"choose_params_api_flats",
                      "Расширенный подбор", max_clicks=max_clicks, gap=GAP, log=log,
                      sink=lambda j: donstroy_search_store(db, [j]))   # each portion stored at once (L43)
    except V.Challenge as e:
        raise Blocked(f"challenge {e}")
    db.execute("insert into fetches values (?,?,?,?)", (today(), "visitor:donstroy:full-search", "200", now()))
    db.commit()
    return donstroy_search_store(db, got)


def don_search_enough(db, day, week):
    """(enough, priced, available): this week's search list priced ≥ DON_SEARCH_ENOUGH of today's available flats (L44)."""
    priced = db.execute("select count(distinct lot_id) from prices where source='donstroy' and origin='catalog' and day>=?",
                        (week,)).fetchone()[0]
    avail = db.execute("select sum(available) from stock where day=? and source='donstroy'", (day,)).fetchone()[0] or 0
    return bool(avail) and priced >= DON_SEARCH_ENOUGH * avail, priced, avail


def donstroy_search_store(db, got):
    flats = {f["id"]: f for g in got if isinstance(g, dict) for f in g.get("flats") or [] if f.get("link")}
    total = max((g.get("total_flats") or 0 for g in got if isinstance(g, dict)), default=0)
    n, others = 0, {}
    for f in flats.values():
        m = DON.match("https://donstroy.moscow" + f["link"])
        if not m:
            continue
        if ("donstroy", m[1]) not in ZK:                  # other Donstroy projects: discovery for the engine (ADAPTERS.md)
            others[m[1]] = others.get(m[1], 0) + 1
            continue
        lot = m[6]
        put_info(db, "donstroy", lot, zk_id=ZK[("donstroy", m[1])], url="https://donstroy.moscow" + f["link"], korpus=f"{m[2]}-{m[3]}",
                 section=m[4], floor=int(m[5]), rooms=f.get("rooms"), area=float(f["area"]) if f.get("area") else None)
        if f.get("price") and not f.get("price_request"):
            put_price(db, "donstroy", lot, f["price"], f.get("price_old") or None, "catalog")
        n += 1
    db.commit()
    return f"dsearch {len(flats)} of {total}: pilot {n}, other projects {others}"


def fsk_list(db):
    idx, st = fetch(db, "https://fsk.ru/sitemap.xml")
    if not idx:
        return f"fsk {st}"
    day, n = today(), 0
    for sm in [u for u in sitemap_locs(idx) if re.match(r"https://fsk\.ru/sitemap/flats/\d+\.xml$", u)]:
        xml, st = fetch(db, sm)
        if not xml:
            return f"fsk {st} at {sm}"
        for u in sitemap_locs(xml):
            m = FSK.match(u)
            if not m or ("fsk", m[1]) not in ZK:
                continue
            zk = ZK[("fsk", m[1])]
            db.execute("insert or replace into seen values (?,?,?,?,?,?)", (day, "fsk", zk, m[2], "listed", None))
            db.execute("insert or ignore into info (source, lot_id, zk_id, url, updated) values (?,?,?,?,?)", ("fsk", m[2], zk, u, None))
            n += 1
    db.commit()
    return f"fsk {n}"


# ---------- flat pages ----------

def jvar(html, name):
    i = html.find(f"var {name} = ")
    return json.JSONDecoder().raw_decode(html[i + len(name) + 7:])[0] if i >= 0 else None


SIMILAR = re.compile(r'href="(/objects/[a-z0-9\-]+/plans/quarter\d+/korpus\d+/section\d+/floor\d+/flat(\d+)/)">\s*(\d+)-<span>.*?'
                     r'd-flat-card__square">([\d.]+) м²</div>.*?d-flat-card__price[^>]*>\s*<div[^>]*>([\d\s ]+)₽', re.S)


def donstroy_page(db, lot_id, url):
    html, st = fetch(db, url)
    if not html:
        if st.startswith("3") or st == "404":           # the flat page is gone: off sale
            put_info(db, "donstroy", lot_id, sold=1)
            db.commit()
        return st
    fi, fd = jvar(html, "flat_info") or {}, jvar(html, "flat_data") or {}
    labels = [x["title"] for x in fd.get("labels") or []]
    plan = next((v for v in fd.get("views") or [] if v.get("sides")), {})
    put_info(db, "donstroy", lot_id, kind="flat", rooms=fi.get("rooms"), area=float(fd["area"]) if fd.get("area") else None,
             seller="owner" if "От собственника" in labels else "developer",       # Donstroy also lists owners' resale flats
             views=json.dumps(sorted({v for v in (plan.get("sides") or {}).values() if v}), ensure_ascii=False),
             sides=json.dumps({"compass": plan.get("compassDegree"), "sides": plan.get("sides")}, ensure_ascii=False),
             completion=fd.get("quarterName"), labels=json.dumps(labels, ensure_ascii=False), sold=int(bool(fi.get("is_sold"))))
    m_int = re.search(r"flat_(\d+)___", html)
    if m_int:
        put_info(db, "donstroy", lot_id, special=json.dumps({"internal_id": m_int[1]}))
    put_price(db, "donstroy", lot_id, fd.get("price"), fd.get("priceOld") or None, "page")
    for m in SIMILAR.finditer(html[html.find("d-flat-similar"):] if "d-flat-similar" in html else ""):
        put_price(db, "donstroy", m[2], m[5].replace(" ", "").replace(" ", ""), None, "similar")
        db.execute("update info set rooms=coalesce(rooms,?), area=coalesce(area,?) where source='donstroy' and lot_id=?",
                   (int(m[3]), float(m[4]), m[2]))
    db.commit()
    return st


def fsk_flat_row(db, f, origin):
    zk = ZK.get(("fsk", (f.get("project") or f.get("complex") or {}).get("slug")))
    put_info(db, "fsk", f["externalId"], zk_id=zk, kind=f.get("kind"), seller="developer", korpus=(f.get("corpus") or {}).get("number"),
             section=(f.get("section") or {}).get("number"), floor=f.get("floorNumber"), floors=(f.get("section") or {}).get("floorsCount"),
             rooms=f.get("rooms"), area=f.get("areaTotal"), finish=str(f.get("finishing")),
             views=json.dumps([v.get("title") for v in f.get("view") or []], ensure_ascii=False),
             completion=((f.get("corpus") or {}).get("dateDelivery") or "")[:10] or None,
             labels=json.dumps([x.get("title") for x in f.get("labels") or []], ensure_ascii=False),
             sold=int(f.get("status") not in (0, None)))
    put_price(db, "fsk", f["externalId"], f.get("price"), f.get("priceWoDiscount"), origin)


def fsk_tree(db, slug):
    """Engine step 2 (ADAPTERS.md): one visit of the allowed catalogue page /<project>/flats; the page itself loads
    /api/v3/flats/tree with every flat for sale (price, price before discount, area, rooms, корпус, section, floor, number,
    status). 17.10, Amber City: 352 of 352 sitemap flats, 38 of 38 prices equal to the flat pages."""
    import importlib.util
    spec = importlib.util.spec_from_file_location("visitor", HERE / "lib" / "visitor.py")
    V = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(V)
    try:
        got = V.visit(f"https://fsk.ru/{slug}/flats", None, None, r"/api/v3/flats/tree\?", "__NUXT__", gap=GAP_HOST["fsk.ru"], log=log)
    except V.Challenge as e:
        raise Blocked(f"challenge {e}")
    db.execute("insert into fetches values (?,?,?,?)", (today(), f"visitor:fsk:tree:{slug}", "200" if got else "empty", now()))
    db.commit()
    return fsk_tree_store(db, slug, got)


def fsk_tree_store(db, slug, got):
    n = 0
    for tree in got:
        for b in tree if isinstance(tree, list) else []:
            for s in b.get("sections") or []:
                for fl in s.get("floors") or []:
                    for f in (fl.get("flats") or []) if isinstance(fl, dict) else []:
                        if not f.get("externalId"):
                            continue
                        f.setdefault("project", {"slug": slug})
                        if not (f.get("project") or {}).get("slug"):
                            f["project"] = {"slug": slug}
                        fsk_flat_row(db, f, "catalog")
                        n += 1
    db.commit()
    return f"fsk tree {slug} {n}"


def fsk_page(db, lot_id, url):
    html, st = fetch(db, url)
    if not html:
        if st.startswith("3") or st == "404":
            put_info(db, "fsk", lot_id, sold=1)
            db.commit()
        return st
    d = nuxt(html)
    page = next((x for x in d.get("data") or [] if isinstance(x, dict) and "flat" in x), None) if d else None
    if not page:
        return "unparsed"
    fsk_flat_row(db, page["flat"], "page")
    for f in page["flat"].get("similar") or []:
        fsk_flat_row(db, f, "similar")
    for rec in page.get("recommendations") or []:
        for f in rec.get("items") or []:
            fsk_flat_row(db, f, "similar")
    db.commit()
    return st


def page_queue(db, day, source):
    """Flats of `source` listed today: first those never read, then those whose last page price is the oldest."""
    return db.execute("""
        select s.lot_id, i.url,
               (select max(p.day) from prices p where p.source=s.source and p.lot_id=s.lot_id and p.origin='page') last_page
        from seen s join info i on i.source=s.source and i.lot_id=s.lot_id
        where s.day=? and s.source=?
        order by (i.rooms is null) desc, last_page is not null, coalesce(last_page,'0000'), random()""", (day, source)).fetchall()


def migrate(db):
    db.executescript(SCHEMA)
    cols = {r[1] for r in db.execute("pragma table_info(info)")}
    if "special" not in cols:
        db.execute("alter table info add column special text")
    db.commit()


def _cd():
    import importlib.util
    spec = importlib.util.spec_from_file_location("cooldown", HERE / "lib" / "cooldown.py")
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


def site_of(url):
    h = url.split("/")[2] if "://" in url else url
    return ".".join(h.split(".")[-2:])


GROUP_SITE = {"lsr": "lsr.ru", "fsk": "fsk.ru", "donstroy": "donstroy.moscow"}


def run_group(db, grp, day):
    if grp == "lists":
        done = {r[0] for r in db.execute("select distinct url from fetches where day=? and status='200'", (day,))}
        steps = [("https://highlife.ru/search", highlife), ("https://dreamriva.ru/flats/", dreamriva)]
        steps += [(f"https://granelle.ru/objects/{s}/", lambda db, s=s: granelle(db, s)) for (src, s) in ZK if src == "granelle"]
        steps += [("https://forma.ru/market/projects/soul", forma), ("https://donstroy.moscow/sitemap-flats.xml", donstroy_list),
                  ("https://fsk.ru/sitemap.xml", fsk_list)]
        for url, fn in steps:
            if url in done:
                continue
            if _cd().active(site_of(url)):                 # L32: the site is paused after a block
                log("list", url, "skipped: site paused", _cd().active(site_of(url))["until"])
                continue
            log("list", fn(db))
    elif grp == "lsr":
        log("list", lsr(db))

    elif grp == "visitor":
        done = {r[0] for r in db.execute("select url from fetches where day=? and url like 'visitor:%'", (day,))}
        for key, fn in [(f"visitor:granelle:{s}", (lambda s=s: granelle_flats(db, s))) for s in GRANELLE_SLUGS] + \
                       [("visitor:forma:soul", lambda: forma_flats(db))]:
            if key not in done:
                try:
                    log("list", fn())
                except Blocked:
                    raise
                except Exception as e:                  # one project failing must not stop the others
                    log("visitor", key, "failed:", str(e)[:200])
    else:                                             # flat pages of one site
        if not db.execute("select 1 from seen where day=? and source=? limit 1", (day, grp)).fetchone():
            log(grp, "today's list is not read yet, waiting for the lists group")
            return "waiting"
        if grp == "donstroy":                           # section stock first (whole stock, sold included), then flat pages
            log("list", donstroy_sections(db))
            week = (datetime.now(MSK) - timedelta(days=PRICE_REFRESH_DAYS - 1)).strftime("%Y-%m-%d")
            if DON_SEARCH and not db.execute("select 1 from fetches where url='visitor:donstroy:full-search' and day>=? limit 1",
                                             (week,)).fetchone():     # only a completed pass counts (the marker is written at its end)
                enough, priced, avail = don_search_enough(db, day, week)
                if enough:                                 # «show more» cannot resume mid-way: no second pass of ≈160 portions
                    db.execute("insert into fetches values (?,?,?,?)", (day, "visitor:donstroy:full-search", "partial", now()))
                    db.commit()
                    log("list", f"dsearch partial pass accepted: {priced} of {avail} available flats priced this week (L44)")
                else:
                    log("list", donstroy_search(db))           # weekly price list in ≈160 portions (engine step 2)
        like = {"donstroy": "https://donstroy.moscow/objects/%", "fsk": "https://fsk.ru/%/flats/%"}[grp]
        n = db.execute("select count(*) from fetches where day=? and url like ?", (day, like)).fetchone()[0]
        fresh = (datetime.now(MSK) - timedelta(days=PRICE_REFRESH_DAYS)).strftime("%Y-%m-%d")
        fails = 0
        tree_done = bool(db.execute("select 1 from fetches where day=? and url like 'visitor:fsk:tree:%' and status='200'", (day,)).fetchone())
        if grp == "fsk" and FSK_TREE and not tree_done:
            for slug in [s for (src, s) in ZK if src == "fsk"]:   # one catalogue visit per project, 60-100 s apart (L7)
                log("list", fsk_tree(db, slug))
                time.sleep(random.uniform(*GAP_HOST["fsk.ru"]))
        for lot_id, url, last_page in page_queue(db, day, grp):
            if n >= PAGE_BUDGET or (last_page and last_page > fresh):
                break
            if grp == "fsk" and FSK_TREE:                    # the tree gives every field: a flat page only for a missing one
                r = db.execute("select rooms, area from info where source='fsk' and lot_id=?", (lot_id,)).fetchone()
                if r and r[0] is not None and r[1]:
                    continue
            if grp == "donstroy" and DON_SEARCH:             # prices come from the search list: a page only for missing details
                r = db.execute("select rooms, area from info where source='donstroy' and lot_id=?", (lot_id,)).fetchone()
                if r and r[0] is not None and r[1]:
                    continue
            st = (donstroy_page if grp == "donstroy" else fsk_page)(db, lot_id, url)
            n += 1
            fails = fails + 1 if st in ("network", "500", "502", "503", "unparsed") else 0
            if fails >= MAX_FAILS:
                log(grp, "too many failures, stopping for today")
                break
        log(grp, "pages today", n)
    return "done"


def connect():
    """Readers (registry, qa, unify, reports) must not block a collector's write (L38): WAL + a 5-minute busy wait."""
    db = sqlite3.connect(DB, timeout=300)
    db.execute("pragma journal_mode=WAL")
    return db


def run(grp):
    db = connect()
    migrate(db)
    day = today()
    db.execute("insert or ignore into runs (day, started, status) values (?,?,?)", (day, now(), "running"))
    db.execute("insert or ignore into gruns (day, grp, started, status) values (?,?,?,?)", (day, grp, now(), "running"))
    db.commit()
    st, fin = db.execute("select status, finished from gruns where day=? and grp=?", (day, grp)).fetchone()
    st = st or ""
    if fin and st == "done" and grp != "lists":         # finished today: a restart must not walk the site again (L42)
        log(grp, "done today at", fin[11:16], "— not repeated (L42)")
        return
    if st.startswith("BLOCKED"):                        # a site said 403/429 today: leave it alone until tomorrow
        log(grp, "blocked earlier today, not resuming:", st)
        return
    if GROUP_SITE.get(grp) and _cd().active(GROUP_SITE[grp]):
        log(grp, "site paused until", _cd().active(GROUP_SITE[grp])["until"], "(L32)")
        db.execute("update gruns set status=? where day=? and grp=?", ("paused", day, grp))
        db.commit()
        return
    try:
        if grp != "lists":
            for _ in range(60):                         # flat-page groups need today's lists; wait up to 30 min
                if db.execute("select finished from gruns where day=? and grp='lists'", (day,)).fetchone() not in (None, (None,)):
                    break
                time.sleep(30)
        st = run_group(db, grp, day)
        db.execute("update gruns set finished=?, status=? where day=? and grp=?", (now() if st == "done" else None, st, day, grp))
    except Blocked as e:
        db.execute("update gruns set status=? where day=? and grp=?", (f"BLOCKED {e}", day, grp))
        log(grp, "BLOCKED", e)
        m = re.search(r"https?://([^/\s]+)", str(e))
        if m:
            _cd().set_block(site_of(m[1]), 24, f"devcat {grp}: {e}")
    finished = db.execute("select count(*) from gruns where day=? and finished is not null", (day,)).fetchone()[0]
    if finished == len(GROUPS):
        db.execute("update runs set finished=?, status=? where day=?", (now(), "done", day))
        log("done", status_line(db, day))
    db.commit()


def status_line(db, day=None):
    day = day or today()
    seen = dict(db.execute("select source, count(*) from seen where day=? group by 1", (day,)).fetchall())
    agg = dict(db.execute("select source, sum(n) from agg where day=? group by 1", (day,)).fetchall())
    pages = db.execute("select count(*) from fetches where day=?", (day,)).fetchone()[0]
    fin = (db.execute("select finished, status from runs where day=?", (day,)).fetchone() or (None, None))
    return (f"flats listed {seen}, layouts/buildings on sale {agg}, requests {pages}"
            + (f", finished {fin[0]}" if fin[0] else f", {fin[1] or 'not started'}"))


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "status"
    if cmd == "run":
        run(sys.argv[2] if len(sys.argv) > 2 else "lists")
    else:
        db = connect()
        migrate(db)
        print(status_line(db))
