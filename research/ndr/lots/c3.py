"""[С3] Direct demand for specific lots: views per day by lot and by type, against price.

Sources
  ЦИАН offer cards (lots.db, cards): views in total and «за сегодня»; one card per lot (a flat listed by several sellers).
    views per day = growth of the total between two card reads (≥ 1 day apart); until a lot has two reads —
    total ÷ days since publication («оценка: среднее с публикации», lower for old listings).
  Developer sites that show views (devcat.db, demand): Дрим Рива «Сегодня просматривали N человек» per flat, daily.
Per type (ЖК × комнатность × стадия, and ЖК × корпус × комнатность × стадия):
  vpd_med       median views per day of the type's lots
  interest_x    median price-adjusted views of the type ÷ the same median over the ЖК; price-adjusted views = views per day ×
                (lot ₽/м² ÷ median ₽/м² of the type): the same views on a dearer lot mean stronger demand
  active        lots on sale (c2_signals.csv), few = at or below the ЖК's median per type
Deficit (С3): interest_x ≥ 1.3 and few lots on sale, at least 3 lots with views.
Also lots_interest.csv: every lot with views, its price-adjusted interest vs its type (the demand map by lot).
"""
import csv
import json
import sqlite3
import statistics
from collections import defaultdict
from datetime import date, datetime
from pathlib import Path

HERE = Path(__file__).resolve().parent
PILOT = {z: n for d in json.loads((HERE / "pilot_zk.json").read_text()).values() for z, n in d.items()}
HIGH, MIN_LOTS = 1.3, 3


def med(xs):
    xs = [x for x in xs if x is not None]
    return statistics.median(xs) if xs else None


def cian_lot_views(lt, day):
    import importlib.util
    spec = importlib.util.spec_from_file_location("model", HERE / "model.py")
    M = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(M)
    cards = defaultdict(list)
    for oid, d, vt, vtd, pub in lt.execute("select offer_id, day, views_total, views_today, publication_date from cards where views_total is not null order by day"):
        cards[oid].append((d, vt, vtd, pub))
    out = {}
    for r in lt.execute("""select o.offer_id, o.zk_id, o.house_name, o.floor, round(o.area,1), o.rooms, o.flat_type, o.category, o.sale_type,
                                  o.from_developer, b.price, b.price_m2 from obs b join offers o using(offer_id)
                           where b.day=? and not o.is_apartments""", (day,)):
        oid, zk, house, floor, area, rooms, ft, cat, st, fd, price, m2 = r
        if oid not in cards:
            continue
        cs = cards[oid]
        (d1, v1, _, pub), (d2, v2, vtd, _) = cs[0], cs[-1]
        span = (date.fromisoformat(d2) - date.fromisoformat(d1)).days
        if span >= 1 and v2 is not None and v1 is not None:
            vpd, how = (v2 - v1) / span, "прирост между чтениями"
        else:
            age = max((date.fromisoformat(d2) - datetime.fromtimestamp(int(pub)).date()).days, 1) if pub else None
            vpd, how = (v2 / age if age else None), "оценка: среднее с публикации"
        key = (zk, house or "?", floor, area, M.rtype(rooms, ft))
        seller = M.seller(st, fd)
        if key in out and (out[key]["vpd"] or 0) >= (vpd or 0):
            continue
        out[key] = {"zk_id": zk, "zk": PILOT.get(zk, zk), "house": house or "?", "floor": floor, "area": area, "type": M.rtype(rooms, ft),
                    "stage": "готовый" if cat == "flatSale" else "строится", "seller": seller, "price": price, "m2": m2,
                    "vpd": vpd, "views_today": vtd, "how": how, "offer": oid, "source": "cian"}
    return list(out.values())


def dev_lot_views(dv):
    out = []
    day = dv.execute("select max(day) from demand").fetchone()[0]
    if not day:
        return out
    for src, lot, vt, zk, korpus, rooms, area, floor in dv.execute("""
            select d.source, d.lot_id, d.views_today, i.zk_id, i.korpus, i.rooms, i.area, i.floor from demand d
            join info i on i.source=d.source and i.lot_id=d.lot_id where d.day=?""", (day,)):
        p = dv.execute("select price from prices where source=? and lot_id=? order by day desc, origin='page' desc limit 1", (src, lot)).fetchone()
        t = "пентхаус" if rooms is None else "студия" if rooms == 0 else {1: "1к", 2: "2к", 3: "3к"}.get(rooms, "4к+")
        out.append({"zk_id": zk, "zk": PILOT.get(zk, zk), "house": korpus, "floor": floor, "area": area, "type": t, "stage": "строится",
                    "seller": "developer", "price": p[0] if p else None, "m2": (p[0] / area) if p and area else None,
                    "vpd": vt, "views_today": vt, "how": "сайт застройщика: просмотры за сегодня", "offer": lot, "source": src})
    return out


def build():
    lt, dv = sqlite3.connect(HERE / "lots.db", timeout=60), sqlite3.connect(HERE / "devcat.db", timeout=60)
    day = lt.execute("select max(day) from obs").fetchone()[0]
    lots = [l for l in cian_lot_views(lt, day) + dev_lot_views(dv) if l["vpd"] is not None]
    # price-adjusted views within each type
    by_type = defaultdict(list)
    for l in lots:
        by_type[(l["source"] == "cian", l["zk_id"], l["type"], l["stage"])].append(l)
    for ls in by_type.values():
        m = med(l["m2"] for l in ls)
        for l in ls:
            l["vpd_adj"] = l["vpd"] * (l["m2"] / m) if m and l["m2"] else l["vpd"]
            l["vs_type_x"] = None
    zk_med = defaultdict(list)
    for l in lots:
        zk_med[(l["source"] == "cian", l["zk_id"])].append(l["vpd_adj"])
    zk_med = {k: med(v) for k, v in zk_med.items()}
    for (is_cian, zk, t, stg), ls in by_type.items():
        tm = med(l["vpd_adj"] for l in ls)
        for l in ls:
            l["vs_type_x"] = round(l["vpd_adj"] / tm, 2) if tm else None
    supply = {}
    if (HERE / "c2_signals.csv").exists():
        for r in csv.DictReader(open(HERE / "c2_signals.csv")):
            if r["level"] == "ЖК":
                supply[(r["zk_id"], r["type"], r["stage"])] = int(r["active"])
    zk_supply_med = {zk: med([v for (z, _, _), v in supply.items() if z == zk]) for zk in PILOT}
    rows = []
    for (is_cian, zk, t, stg), ls in by_type.items():
        x = med(l["vpd_adj"] for l in ls)
        base = zk_med.get((is_cian, zk))
        act = supply.get((zk, t, stg)) if is_cian else None
        few = act is not None and zk_supply_med.get(zk) is not None and act <= zk_supply_med[zk]
        r = {"zk": PILOT.get(zk, zk), "type": t, "stage": stg, "source": "ЦИАН" if is_cian else ls[0]["source"],
             "lots_with_views": len(ls), "vpd_med": round(med(l["vpd"] for l in ls), 1),
             "interest_x": round(x / base, 2) if x is not None and base else None, "active": act, "few_on_sale": int(few),
             "estimated_share": round(sum(l["how"].startswith("оценка") for l in ls) / len(ls), 2), "zk_id": zk}
        r["deficit"] = int(bool(r["interest_x"] and r["interest_x"] >= HIGH and few and len(ls) >= MIN_LOTS))
        rows.append(r)
    rows.sort(key=lambda r: (-r["deficit"], -(r["interest_x"] or 0)))
    with (HERE / "c3_signals.csv").open("w", newline="") as f:
        w = csv.DictWriter(f, list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)
    lots.sort(key=lambda l: -(l["vs_type_x"] or 0))
    cols = ["zk", "type", "stage", "seller", "house", "floor", "area", "price", "m2", "vpd", "vpd_adj", "vs_type_x", "views_today", "how", "source", "offer"]
    with (HERE / "lots_interest.csv").open("w", newline="") as f:
        w = csv.DictWriter(f, cols, extrasaction="ignore")
        w.writeheader()
        w.writerows(lots)
    (HERE / "c3_signals.json").write_text(json.dumps({"day": day, "lots_with_views": len(lots), "types": rows}, ensure_ascii=False, indent=1))
    return day, lots, rows


if __name__ == "__main__":
    day, lots, rows = build()
    print(f"{day}: lots with views {len(lots)} (ЦИАН {sum(l['source'] == 'cian' for l in lots)}), types {len(rows)}, deficit {sum(r['deficit'] for r in rows)}")
    for r in rows[:12]:
        print(r["zk"], r["type"], r["stage"], r["source"], "lots", r["lots_with_views"], "vpd", r["vpd_med"], "x", r["interest_x"], "active", r["active"], "def", r["deficit"])
