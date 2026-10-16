"""[С1] Developer price lists: which flats are on sale on the developers' own sites, day by day.

Sources (all paths allowed by each site's robots.txt, checked 16.10.2026; see NOTES.md):
  highlife.ru /search          — whole catalog in __NEXT_DATA__, one page, with sold/reserved status   (flat level)
  granelle.ru /objects/<slug>/ — layouts with flat_count on sale + a few listed lots                     (layout level)
  forma.ru /market/projects/soul — buildings x rooms: flats on sale, price range, view attributes       (building x rooms)
  donstroy.moscow sitemap-flats.xml — every flat on sale; flat pages for price, area, views            (flat level)
  fsk.ru sitemap/flats/N.xml   — every flat on sale; flat pages for price, area, views                  (flat level)
Not available: zilart.ru (robots.txt: Disallow: /), dream-riva.ru (no catalog on the site) -> ЦИАН developer window.

Daily run: cheap list pages first (presence of every flat), then up to PAGE_BUDGET flat pages:
new flats without details first, then the flats whose price is the oldest (weekly refresh).
Same manners as collector.py: one request every 20-40 s, no captcha solving, stop on 403/429/captcha.
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
PAGE_BUDGET = 150              # flat pages per day (Donstroy + FSK)
PRICE_REFRESH_DAYS = 7         # instruction С1: weekly price list
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
}
NOT_COVERED = {"7889": "ЗИЛАРТ: zilart.ru запрещает обход (Disallow: /)", "5340468": "Дрим Рива: на dream-riva.ru нет каталога квартир"}

ALLOWED = [re.compile(p) for p in (
    r"https://highlife\.ru/search$",
    r"https://granelle\.ru/objects/[a-z0-9\-]+/$",
    r"https://forma\.ru/market/projects/[a-z0-9\-]+$",
    r"https://donstroy\.moscow/sitemap-flats\.xml$",
    r"https://donstroy\.moscow/objects/[a-z0-9\-]+/plans/quarter\d+/korpus\d+/section\d+/floor\d+/flat\d+/$",
    r"https://fsk\.ru/sitemap\.xml$",
    r"https://fsk\.ru/sitemap/flats/\d+\.xml$",
    r"https://fsk\.ru/[a-z0-9\-]+/flats/\d+$",
)]

SCHEMA = """
create table if not exists runs (day text primary key, started text, finished text, status text);
create table if not exists fetches (day text, url text, status text, at text);
create table if not exists seen (day text, source text, zk_id text, lot_id text, status text, price real,
                                 primary key (day, source, lot_id));
create table if not exists info (source text, lot_id text, zk_id text, url text, kind text, seller text, korpus text,
                                 section text, floor int, floors int, rooms int, area real, finish text, views text,
                                 sides text, completion text, labels text, sold int, updated text,
                                 primary key (source, lot_id));
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


_last = [0.0]


def fetch(db, url):
    from scrapling.fetchers import Fetcher
    if not any(p.match(url) for p in ALLOWED):
        raise ValueError(f"not allowed by robots policy: {url}")
    wait = random.uniform(*GAP) - (time.time() - _last[0])
    if wait > 0:
        time.sleep(wait)
    r = None
    for attempt in range(3):
        try:
            r = Fetcher.get(url, impersonate="chrome", timeout=60, retries=1, stealthy_headers=True, follow_redirects=False,
                            headers={"Accept-Language": "ru-RU,ru;q=0.9"})
            break
        except Exception as e:
            log("network error", attempt + 1, url, str(e)[:120])
            time.sleep(10 * (attempt + 1))
    _last[0] = time.time()
    status = "network" if r is None else str(r.status)
    html = "" if r is None else r.body.decode("utf-8", "replace") if isinstance(r.body, bytes) else str(r.body)
    # a captcha page has none of the data the page should carry (forma.ru links to the SmartCaptcha notice in every footer)
    data = any(m in html for m in ("__NEXT_DATA__", "__NUXT__", "<urlset", "<sitemapindex", "var flat_info"))
    blocked = r is not None and (r.status in (403, 429) or "captcha" in html.lower() and not data)
    db.execute("insert into fetches values (?,?,?,?)", (today(), url, "blocked" if blocked else status, now()))
    db.commit()
    if blocked:
        raise Blocked(f"{r.status} {url}")
    if r is None:
        return None, status
    return (html if r.status == 200 else None), status


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
                 labels=json.dumps(f.get("options") or [], ensure_ascii=False), sold=int(f["status"] == "Продажа"))
        put_price(db, "highlife", f["id"], f.get("price_discount") or f.get("price"), f.get("price"), "catalog")
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


def page_queue(db, day):
    """Flats listed today: first those never read, then those whose last page price is the oldest."""
    return db.execute("""
        select s.source, s.lot_id, i.url,
               (select max(p.day) from prices p where p.source=s.source and p.lot_id=s.lot_id and p.origin='page') last_page,
               (select max(p.day) from prices p where p.source=s.source and p.lot_id=s.lot_id) last_any
        from seen s join info i on i.source=s.source and i.lot_id=s.lot_id
        where s.day=? and s.source in ('donstroy','fsk')
        order by (i.rooms is null) desc, last_any is not null, coalesce(last_page,'0000') , random()""", (day,)).fetchall()


def run():
    db = sqlite3.connect(DB, timeout=60)
    db.executescript(SCHEMA)
    day = today()
    db.execute("insert or ignore into runs (day, started, status) values (?,?,?)", (day, now(), "running"))
    db.commit()
    done = {r[0] for r in db.execute("select distinct url from fetches where day=? and status='200'", (day,))}
    try:
        steps = [("https://highlife.ru/search", highlife)]
        steps += [(f"https://granelle.ru/objects/{s}/", lambda db, s=s: granelle(db, s)) for (src, s) in ZK if src == "granelle"]
        steps += [("https://forma.ru/market/projects/soul", forma), ("https://donstroy.moscow/sitemap-flats.xml", donstroy_list),
                  ("https://fsk.ru/sitemap.xml", fsk_list)]
        for url, fn in steps:
            if url in done:
                continue
            log("list", fn(db))
        fails, n = 0, db.execute("""select count(*) from fetches where day=? and url not like '%sitemap%' and
                                    (url like 'https://donstroy.moscow/objects/%' or url like 'https://fsk.ru/%/flats/%')""", (day,)).fetchone()[0]
        for source, lot_id, url, last_page, _ in page_queue(db, day):
            if n >= PAGE_BUDGET:
                break
            if last_page and last_page > (datetime.now(MSK) - timedelta(days=PRICE_REFRESH_DAYS)).strftime("%Y-%m-%d"):
                break                                          # everything listed has a fresh price
            st = (donstroy_page if source == "donstroy" else fsk_page)(db, lot_id, url)
            n += 1
            fails = fails + 1 if st in ("network", "500", "502", "503", "unparsed") else 0
            if fails >= MAX_FAILS:
                log("too many failures, stopping for today")
                break
        db.execute("update runs set finished=?, status=? where day=?", (now(), "done", day))
        db.commit()
        log("done", status_line(db, day))
    except Blocked as e:
        db.execute("update runs set status=? where day=?", (f"BLOCKED {e}", day))
        db.commit()
        log("BLOCKED", e)


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
        run()
    else:
        db = sqlite3.connect(DB)
        db.executescript(SCHEMA)
        print(status_line(db))
