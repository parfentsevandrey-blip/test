"""Lot collector for the resale pilot: daily snapshot of every listing in the pilot ЖК on cian.ru.

Only paths allowed by https://www.cian.ru/robots.txt are requested: SEO listing pages of a ЖК
(/kupit-...-zhiloy-kompleks-<slug>-<id>/, taken from the site's own sitemap) and offer cards (/sale/flat/<id>/),
never URLs with a query string. Gentle pace (15-35 s between pages), one session, stop on any block signal.

Usage:  python3 -I collector.py run      # resumable daily run: list pages first, then offer cards within budget
        python3 -I collector.py status
Data:   lots.db (sqlite) - see SCHEMA; raw pages are not kept.
"""
import json
import random
import re
import sqlite3
import sys
import time
from datetime import datetime, timezone, timedelta
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "lib"))
from cianstate import configs  # noqa: E402

DB = HERE / "lots.db"
LOG = HERE / "collector.log"
PILOT = json.loads((HERE / "pilot_zk.json").read_text())
URLS = json.loads((HERE / "pilot_urls.json").read_text())
MSK = timezone(timedelta(hours=3))
GAP = (15, 35)                 # seconds between requests
CARD_BUDGET = 160              # offer cards per daily run
CARD_REFRESH_DAYS = 3          # re-read views/price history of a live offer every N days
MAX_FAILS = 3                  # consecutive failed pages -> stop the run (cooldown until next run)

SCHEMA = """
create table if not exists runs(day text primary key, started text, finished text, pages_ok int, pages_fail int, cards int, note text);
create table if not exists pages(day text, zk_id text, url text, total int, n_offers int, ok int, fetched text, primary key(day, url));
create table if not exists offers(offer_id integer primary key, zk_id text, first_seen text, last_seen text, rooms int, flat_type text,
    area real, floor int, floors int, house_id int, house_name text, section text, finish text, decoration text, category text,
    sale_type text, from_developer int, by_owner int, seller text, account_type text, is_apartments int, created text);
create table if not exists obs(day text, offer_id integer, zk_id text, price integer, price_m2 integer, discount text, booked int,
    primary key(day, offer_id));
create table if not exists cards(offer_id integer, day text, status text, views_total int, views_today int, price_changes text,
    publication_date text, primary key(offer_id, day));
create table if not exists zk_cards(day text, zk_id text, data text, primary key(day, zk_id));
create table if not exists zk_meta(zk_id text primary key, jk_url text, builder text);
"""


def now():
    return datetime.now(MSK)


def today():
    return now().strftime("%Y-%m-%d")


def log(*a):
    line = now().isoformat(timespec="seconds") + " " + " ".join(str(x) for x in a)
    print(line, flush=True)
    with LOG.open("a") as f:
        f.write(line + "\n")


def allowed(url):
    """robots.txt of cian.ru: '/*?' and '*=' are disallowed; our two path families are allowed."""
    return "?" not in url and "=" not in url and (
        re.match(r"https://www\.cian\.ru/kupit-[a-z0-9\-]*zhiloy-kompleks-[a-z0-9\-]+-\d+/$", url)
        or re.match(r"https://www\.cian\.ru/sale/flat/\d+/$", url)
        or re.match(r"https://zhk-[a-z0-9\-]+\.cian\.ru/$", url))


class Blocked(Exception):
    pass


_last = [0.0]


def fetch(url):
    from scrapling.fetchers import Fetcher
    if not allowed(url):
        raise ValueError(f"not allowed by robots policy: {url}")
    wait = random.uniform(*GAP) - (time.time() - _last[0])
    if wait > 0:
        time.sleep(wait)
    r = None
    for attempt in range(3):
        try:
            r = Fetcher.get(url, impersonate="chrome", timeout=60, retries=1, stealthy_headers=True,
                            headers={"Accept-Language": "ru-RU,ru;q=0.9"})
            break
        except Exception as e:                       # network resets through the proxy: retry, then give up
            log("network error", attempt + 1, str(e)[:120])
            time.sleep(10 * (attempt + 1))
    _last[0] = time.time()
    if r is None:
        return None, "network"
    html = r.body.decode("utf-8", "replace") if isinstance(r.body, bytes) else str(r.body)
    if r.status in (403, 429) or ("captcha" in html.lower() and "_cianConfig" not in html):
        raise Blocked(f"{r.status} {url}")
    return (html if r.status == 200 else None), r.status


def num(x):
    try:
        return float(str(x).replace(",", "."))
    except Exception:
        return None


def parse_list(html):
    cfg = configs(html)
    st = cfg.get("frontend-serp", {}).get("initialState") or {}
    res = st.get("results") or {}
    return res.get("offers") or [], res.get("totalOffers")


def offer_row(o, zk_id):
    nb = o.get("newbuilding") or {}
    house = nb.get("house") or {}
    fin = house.get("finishDate") or {}
    bt = o.get("bargainTerms") or {}
    user = o.get("user") or {}
    price = bt.get("priceRur") or bt.get("price")
    area = num(o.get("totalArea"))
    return {
        "offer_id": o["id"], "zk_id": zk_id, "rooms": o.get("roomsCount"), "flat_type": o.get("flatType"), "area": area,
        "floor": o.get("floorNumber"), "floors": (o.get("building") or {}).get("floorsCount"),
        "house_id": house.get("id"), "house_name": house.get("name"), "section": house.get("section"),
        "finish": f"{fin.get('year')}-Q{fin.get('quarter')}" if fin else None, "decoration": o.get("decoration"),
        "category": o.get("category"), "sale_type": bt.get("saleType"), "from_developer": int(bool(o.get("fromDeveloper"))),
        "by_owner": int(bool(o.get("isByHomeowner"))), "seller": user.get("agencyName") or user.get("companyName"),
        "account_type": user.get("accountType"), "is_apartments": int(bool(o.get("isApartments"))), "created": o.get("creationDate"),
        "price": price, "price_m2": round(price / area) if price and area else None,
        "discount": json.dumps(o.get("discount"), ensure_ascii=False) if o.get("discount") else None,
        "booked": int(bool(o.get("isBookedFromDeveloper"))),
    }


def zk_card_summary(html):
    """Developer's window on ЦИАН for one ЖК: counts and price ranges by room type and by house."""
    st = configs(html).get("newbuilding-card-desktop-fichering-frontend", {}).get("initialState") or {}
    nb = st.get("newbuilding") or {}
    tot = ((st.get("offers") or {}).get("data") or {}).get("total") or {}
    if not nb:
        return None
    return {
        "name": nb.get("name"), "builder": ", ".join(b.get("name", "") for b in nb.get("builders") or []),
        "class": nb.get("newbuildingClassName"), "props": nb.get("propsCount"), "from_dev": nb.get("fromDevelopersPropsCount"),
        "from_agents": nb.get("fromAgentsPropsCount"), "min_price_m2_dev": nb.get("minPriceForMeterFromDeveloperValue"),
        "rooms": {r.get("roomType"): {"count": r.get("fromDeveloperPropsCount"), "range": r.get("price2Range"),
                                      "min_area": r.get("totalAreaMinDisplay"),
                                      "layouts": [{"offer": l.get("offerId"), "house": l.get("houseName"), "area": l.get("totalArea"),
                                                   "price": l.get("price")} for l in r.get("layouts") or []]}
                  for r in tot.get("fromDeveloperRooms") or []},
        "houses": [{"id": h.get("houseId"), "name": h.get("houseName"), "finish": h.get("finishStatus"),
                    "from_dev": h.get("fromDeveloperPropsCount"), "escrow": h.get("isEscrow")} for h in nb.get("houses") or []],
    }


def save_offer(db, r, day):
    db.execute("""insert into offers(offer_id, zk_id, first_seen, last_seen, rooms, flat_type, area, floor, floors, house_id, house_name,
                  section, finish, decoration, category, sale_type, from_developer, by_owner, seller, account_type, is_apartments, created)
                  values(:offer_id, :zk_id, :day, :day, :rooms, :flat_type, :area, :floor, :floors, :house_id, :house_name, :section, :finish,
                  :decoration, :category, :sale_type, :from_developer, :by_owner, :seller, :account_type, :is_apartments, :created)
                  on conflict(offer_id) do update set last_seen=:day, sale_type=:sale_type, seller=:seller, decoration=:decoration""",
               dict(r, day=day))
    db.execute("insert or replace into obs values(?,?,?,?,?,?,?)",
               (day, r["offer_id"], r["zk_id"], r["price"], r["price_m2"], r["discount"], r["booked"]))


VIEWS = re.compile(r"(\d[\d\s]*)\s+просмотр\w*(?:,\s*(\d[\d\s]*)\s+за сегодня)?")


def parse_card(html):
    ds = configs(html).get("frontend-offer-card", {}).get("defaultState") or {}
    od = ds.get("offerData") or {}
    off = od.get("offer") or {}
    s = ((od.get("stats") or {}).get("totalViewsFormattedString")) or ""
    m = VIEWS.search(s)
    total = int(m.group(1).replace(" ", "").replace("\xa0", "")) if m else None
    today_v = int(m.group(2).replace(" ", "").replace("\xa0", "")) if m and m.group(2) else (0 if m else None)
    return {"status": off.get("status"), "views_total": total, "views_today": today_v,
            "price_changes": json.dumps(od.get("priceChanges") or [], ensure_ascii=False), "publication_date": off.get("publicationDate")}


def run():
    db = sqlite3.connect(DB)
    db.executescript(SCHEMA)
    day = today()
    db.execute("insert or ignore into runs(day, started, pages_ok, pages_fail, cards) values(?,?,0,0,0)", (day, now().isoformat()))
    db.commit()
    fails = 0
    try:
        # 1. listing pages of every ЖК (skip the ones already done today)
        for dev, zks in PILOT.items():
            for zk_id, name in zks.items():
                for url in dict.fromkeys(URLS.get(zk_id, [])):
                    if db.execute("select 1 from pages where day=? and url=? and ok=1", (day, url)).fetchone():
                        continue
                    html, status = fetch(url)
                    offers, total = parse_list(html) if html else ([], None)
                    ok = int(html is not None and total is not None)
                    for o in offers:
                        save_offer(db, offer_row(o, zk_id), day)
                        if o.get("jkUrl") and (o.get("newbuilding") or {}).get("id") == int(zk_id):
                            db.execute("insert into zk_meta(zk_id, jk_url) values(?,?) on conflict(zk_id) do update set jk_url=excluded.jk_url",
                                       (zk_id, o["jkUrl"]))
                    db.execute("insert or replace into pages values(?,?,?,?,?,?,?)", (day, zk_id, url, total, len(offers), ok, now().isoformat()))
                    db.execute(f"update runs set pages_{'ok' if ok else 'fail'}=pages_{'ok' if ok else 'fail'}+1 where day=?", (day,))
                    db.commit()
                    log("page", status, name, url.split("cian.ru/")[1][:60], f"{len(offers)}/{total}")
                    fails = 0 if ok else fails + 1
                    if fails >= MAX_FAILS:
                        raise Blocked(f"{fails} failed pages in a row")
        # 2. ЖК cards: developer's counts by room type and house
        for zk_id, jk_url in db.execute("select zk_id, jk_url from zk_meta").fetchall():
            if db.execute("select 1 from zk_cards where day=? and zk_id=?", (day, zk_id)).fetchone() or not allowed(jk_url):
                continue
            html, status = fetch(jk_url)
            summ = zk_card_summary(html) if html else None
            if summ:
                db.execute("insert or replace into zk_cards values(?,?,?)", (day, zk_id, json.dumps(summ, ensure_ascii=False)))
                db.execute("update zk_meta set builder=? where zk_id=?", (summ["builder"], zk_id))
                db.commit()
                log("zk card", summ["name"], summ["builder"], f"dev {summ['from_dev']} agents {summ['from_agents']}")
        # 3. offer cards: new offers first, then the oldest-read live offers, within the daily budget
        done = db.execute("select count(*) from cards where day=?", (day,)).fetchone()[0]
        todo = db.execute("""select o.offer_id from offers o left join (select offer_id, max(day) d from cards group by offer_id) c
                             on c.offer_id=o.offer_id where o.last_seen=? and (c.d is null or c.d <= date(?, ?))
                             order by c.d is not null, c.d, o.from_developer""",
                          (day, day, f"-{CARD_REFRESH_DAYS} day")).fetchall()
        for (oid,) in todo[:max(CARD_BUDGET - done, 0)]:
            html, status = fetch(f"https://www.cian.ru/sale/flat/{oid}/")
            if html:
                c = parse_card(html)
                db.execute("insert or replace into cards values(?,?,?,?,?,?,?)",
                           (oid, day, c["status"], c["views_total"], c["views_today"], c["price_changes"], c["publication_date"]))
                db.execute("update runs set cards=cards+1 where day=?", (day,))
                db.commit()
                fails = 0
            else:
                fails += 1
                log("card failed", status, oid)
                if fails >= MAX_FAILS:
                    raise Blocked(f"{fails} failed cards in a row")
        db.execute("update runs set finished=? where day=?", (now().isoformat(), day))
        db.commit()
        log("run finished", day, status_line(db))
    except Blocked as e:
        db.execute("update runs set note=? where day=?", (f"stopped: {e}", day))
        db.commit()
        log("BLOCKED / stopped:", e)


def status_line(db):
    day = today()
    r = db.execute("select pages_ok, pages_fail, cards, finished from runs where day=?", (day,)).fetchone()
    n = db.execute("select count(*) from obs where day=?", (day,)).fetchone()[0]
    total_pages = sum(len(dict.fromkeys(v)) for v in URLS.values())
    return f"pages {r[0] if r else 0}/{total_pages} (fail {r[1] if r else 0}), offers today {n}, cards {r[2] if r else 0}, finished {r[3] if r else None}"


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "status"
    if cmd == "run":
        run()
    else:
        db = sqlite3.connect(DB)
        db.executescript(SCHEMA)
        print(status_line(db))
