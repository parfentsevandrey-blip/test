"""[С1][С2] One picture of every lot: developer sites (devcat.db) joined with ЦИАН (lots.db).

For every pilot ЖК:
  developer flats  — the latest list of each developer site, with price, discount and special characteristics
                     (views, sides, collections, options, promo), views today where the site shows them;
  ЦИАН lots        — one flat listed by several sellers is one lot (ЖК, корпус, этаж, площадь, комнатность).
Matching:
  ЦИАН developer listing  -> the same flat on the developer site: same ЖК, rooms, floor, area within 0.6 м²
                             (unique match only) -> is the developer's whole stock on ЦИАН, do prices agree;
  ЦИАН assignment/resale  -> the same layout at the developer: same ЖК, rooms, area within 0.6 м², on sale today
                             (same корпус when possible) -> resale price vs the developer's price for the same layout,
                             and the special characteristics of that layout (view, sides).
Outputs: lots_unified.csv (one row per lot, both sources), coverage.json (per ЖК: counts and match rates).
"""
import csv
import json
import re
import sqlite3
import statistics
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
PILOT = {z: n for d in json.loads((HERE / "pilot_zk.json").read_text()).values() for z, n in d.items()}
ON_SALE = {"highlife": {"Свободно"}, "dreamriva": {"AVAILABLE"}, "forma": {"free"}, "granelle": {"1"}}   # others: listed = on sale
BOOKED = {"highlife": {"Резерв", "Уст.бронь"}, "dreamriva": {"BOOKED"}, "forma": {"reserve"}}


def num(s):
    m = re.search(r"\d+", str(s or ""))
    return m.group(0) if m else None


def dev_flats(dv, day):
    out = []
    for r in dv.execute("""select s.source, s.zk_id, s.lot_id, s.status, i.korpus, i.floor, i.rooms, i.area, i.finish, i.views, i.sides,
                                  i.labels, i.special, i.seller, i.completion, i.url,
                                  (select price from prices p where p.source=s.source and p.lot_id=s.lot_id order by day desc,
                                          origin='page' desc limit 1) price,
                                  (select price_base from prices p where p.source=s.source and p.lot_id=s.lot_id order by day desc,
                                          origin='page' desc limit 1) price_base,
                                  (select views_today from demand d where d.source=s.source and d.lot_id=s.lot_id and d.day=s.day) vt
                           from seen s join info i on i.source=s.source and i.lot_id=s.lot_id
                           where s.day=(select max(day) from seen x where x.source=s.source)
                             and coalesce(i.kind,'') not in ('apartments')"""):   # L19: apartments are not flats
        d = dict(zip(("source", "zk_id", "lot_id", "status", "korpus", "floor", "rooms", "area", "finish", "views", "sides", "labels",
                      "special", "seller", "completion", "url", "price", "price_base", "views_today"), r))
        src, st = d["source"], str(d["status"])
        d["state"] = ("on_sale" if st in ON_SALE[src] else "booked" if st in BOOKED.get(src, ()) else "sold") if src in ON_SALE \
            else "on_sale"
        if d["seller"] == "owner":
            d["state"] = "owner_resale"
        out.append(d)
    return out


def cian_lots(lt):
    import importlib.util
    spec = importlib.util.spec_from_file_location("model", HERE / "model.py")
    M = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(M)
    day = lt.execute("select max(day) from obs").fetchone()[0]
    lots = {}
    for r in lt.execute("""select o.*, b.price, b.price_m2 from obs b join offers o using(offer_id) where b.day=? and not o.is_apartments""", (day,)):
        o = dict(zip([c[0] for c in lt.execute("select * from offers limit 0").description] + ["price", "price_m2"], r))
        sel = M.seller(o["sale_type"], o["from_developer"])
        rooms = 0 if M.rtype(o["rooms"], o["flat_type"]) == "студия" else o["rooms"]
        key = (o["zk_id"], o["house_name"] or "?", o["floor"], round(o["area"] or 0, 1), rooms, sel == "developer")
        L = lots.setdefault(key, {"zk_id": o["zk_id"], "house": o["house_name"], "floor": o["floor"], "area": o["area"], "rooms": rooms,
                                  "seller": sel, "ready": o["category"] == "flatSale", "decoration": o["decoration"],
                                  "price": o["price"], "offers": []})
        L["price"] = min(L["price"] or 9e18, o["price"] or 9e18)
        L["offers"].append(o["offer_id"])
        if sel != "developer" and L["seller"] == "developer":
            L["seller"] = sel
    return day, list(lots.values())


def main():
    dv, lt = sqlite3.connect(HERE / "devcat.db", timeout=60), sqlite3.connect(HERE / "lots.db", timeout=60)
    day_d = dv.execute("select max(day) from seen").fetchone()[0]
    dev = dev_flats(dv, day_d)
    day_c, cian = cian_lots(lt)
    by_zk = defaultdict(list)
    for d in dev:
        by_zk[d["zk_id"]].append(d)
    rows, cov = [], {}
    for zk, name in PILOT.items():
        flats = by_zk.get(zk, [])
        sale = [d for d in flats if d["state"] == "on_sale"]
        cl = [c for c in cian if c["zk_id"] == zk]
        c_dev = [c for c in cl if c["seller"] == "developer"]
        c_res = [c for c in cl if c["seller"] != "developer"]
        matched_dev, price_gap = 0, []
        for c in c_dev:
            m = [d for d in flats if d["rooms"] == c["rooms"] and d["floor"] == c["floor"] and d["area"] and c["area"]
                 and abs(d["area"] - c["area"]) <= 0.6]
            if len(m) == 1:
                matched_dev += 1
                d = m[0]
                d.setdefault("cian_offers", []).extend(c["offers"])
                if d["price"] and c["price"]:
                    price_gap.append(100 * (c["price"] / d["price"] - 1))
        same_layout = 0
        for c in c_res:
            m = [d for d in sale if d["rooms"] == c["rooms"] and d["area"] and c["area"] and abs(d["area"] - c["area"]) <= 0.6]
            mk = [d for d in m if num(d["korpus"]) and num(d["korpus"]) == num(c["house"])]
            m = mk or m
            if m:
                same_layout += 1
                ref = statistics.median(d["price"] / d["area"] for d in m if d["price"] and d["area"])
                c["dev_same_layout_m2"] = round(ref)
                c["vs_dev_same_layout_pct"] = round(100 * ((c["price"] / c["area"]) / ref - 1), 1) if c["price"] and c["area"] else None
                c["layout_views"] = sorted({v for d in m for v in json.loads(d["views"] or "[]")})[:6]
                c["dev_same_layout_n"] = len(m)
        cov[name] = {"zk_id": zk, "dev_source": flats[0]["source"] if flats else None, "dev_flats_known": len(flats),
                     "dev_on_sale": len(sale), "dev_booked": sum(d["state"] == "booked" for d in flats),
                     "dev_sold_visible": sum(d["state"] == "sold" for d in flats),
                     "dev_with_price": sum(1 for d in sale if d["price"]),
                     "dev_with_special": sum(1 for d in flats if d["special"] or (d["views"] and d["views"] != "[]")),
                     "cian_dev_lots": len(c_dev), "cian_dev_matched_to_site": matched_dev,
                     "cian_vs_site_price_median_pct": round(statistics.median(price_gap), 1) if price_gap else None,
                     "cian_resale_lots": len(c_res), "cian_resale_with_same_layout_at_dev": same_layout}
        for d in flats:
            rows.append({"zk": name, "side": "застройщик", "source": d["source"], "lot": d["lot_id"], "state": d["state"],
                         "korpus": d["korpus"], "floor": d["floor"], "rooms": d["rooms"], "area": d["area"], "price": d["price"],
                         "price_base": d["price_base"], "views_today": d["views_today"], "views": d["views"], "sides": d["sides"],
                         "special": d["special"], "on_cian": len(d.get("cian_offers", [])), "url": d["url"]})
        for c in c_res:
            rows.append({"zk": name, "side": "ЦИАН " + c["seller"], "source": "cian", "lot": ",".join(map(str, c["offers"][:3])),
                         "state": "готовый" if c["ready"] else "строится", "korpus": c["house"], "floor": c["floor"], "rooms": c["rooms"],
                         "area": c["area"], "price": c["price"], "dev_same_layout_m2": c.get("dev_same_layout_m2"),
                         "vs_dev_same_layout_pct": c.get("vs_dev_same_layout_pct"), "views": json.dumps(c.get("layout_views") or [], ensure_ascii=False),
                         "url": f"https://www.cian.ru/sale/flat/{c['offers'][0]}/"})
    cols = ["zk", "side", "source", "lot", "state", "korpus", "floor", "rooms", "area", "price", "price_base", "dev_same_layout_m2",
            "vs_dev_same_layout_pct", "views_today", "views", "sides", "special", "on_cian", "url"]
    with (HERE / "lots_unified.csv").open("w", newline="") as f:
        w = csv.DictWriter(f, cols, extrasaction="ignore")
        w.writeheader()
        w.writerows(rows)
    (HERE / "coverage.json").write_text(json.dumps({"devcat_day": day_d, "cian_day": day_c, "projects": cov}, ensure_ascii=False, indent=1))
    return cov


if __name__ == "__main__":
    for k, v in main().items():
        print(f"{k:26s} сайт: в продаже {v['dev_on_sale']:4d} бронь {v['dev_booked']:3d} продано {v['dev_sold_visible']:4d} "
              f"цена {v['dev_with_price']:4d} спец {v['dev_with_special']:4d} | ЦИАН застр. {v['cian_dev_lots']:4d} "
              f"совпало {v['cian_dev_matched_to_site']:4d} разн.цен {v['cian_vs_site_price_median_pct']} | "
              f"вторичка {v['cian_resale_lots']:4d} с планировкой у застр. {v['cian_resale_with_same_layout_at_dev']:4d}")
