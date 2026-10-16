"""[С1] Signals from developer price lists (devcat.db): how fast each flat type leaves the developer's list.

Type = ЖК × корпус × комнатность (INSTRUCTION.md, «Термины»). Per type:
  on_sale        flats on the developer's list today (Donstroy: flats «От собственника» excluded once their page is read)
  total, left_pct  whole stock of the type and the share still on sale — only where the site shows sold flats (High Life)
  exits_28d / new_28d  flats that left / appeared on the list in the last 28 days (aggregate sources: net decrease)
  sellout_days   on_sale ÷ exits per day — how many days the rest of the type lasts at the current pace
  median_days_on_sale  of flats that left in the last 8 weeks and were first seen after our first snapshot
  price_4w_pct   median change of the price of the same flats over the last 4 weeks (not a change of the median)
Signal (С1): sellout_days ≤ 45 and left_pct < 10% (or on_sale ≤ 3 where the whole stock is unknown) and price_4w_pct > 0.
"""
import csv
import json
import sqlite3
import statistics
from datetime import date, timedelta
from pathlib import Path

HERE = Path(__file__).resolve().parent
PILOT = {zk: name for zks in json.loads((HERE / "pilot_zk.json").read_text()).values() for zk, name in zks.items()}
SELL_DAYS, LEFT_PCT, FEW = 45, 10.0, 3
FLAT_SOURCES = ("highlife", "donstroy", "fsk", "lsr", "dreamriva", "granelle", "forma")
ON_SALE = {"highlife": {"Свободно"}, "dreamriva": {"AVAILABLE"}, "forma": {"free"}, "granelle": {"1"}}   # others: listed = on sale
FULL_STOCK = {"highlife"}            # sites that also show sold flats: the whole stock of a type is known
ROOMS = {0: "студия", 1: "1к", 2: "2к", 3: "3к"}


def rname(r):
    return "—" if r is None else ROOMS.get(int(r), "4к+")


def dd(a, b):
    return (date.fromisoformat(a) - date.fromisoformat(b)).days


def flat_types(db, day, first_day):
    """Flat-level sources (FLAT_SOURCES)."""
    d28 = (date.fromisoformat(day) - timedelta(days=28)).isoformat()
    d56 = (date.fromisoformat(day) - timedelta(days=56)).isoformat()
    days = [r[0] for r in db.execute("select distinct day from seen order by 1")]
    out = {}
    lots = db.execute("""select i.source, i.lot_id, i.zk_id, i.korpus, i.rooms, i.seller, i.sold, i.kind from info i
                         where i.source in (%s)""" % ",".join("'%s'" % x for x in FLAT_SOURCES)).fetchall()
    seen = {}
    for src, lot, d, st in db.execute("select source, lot_id, day, status from seen"):
        seen.setdefault((src, lot), {})[d] = st
    for src, lot, zk, korpus, rooms, seller, sold, kind in lots:
        if seller == "owner":
            continue
        hist = seen.get((src, lot), {})
        k = (zk, korpus or "?", rname(rooms))
        t = out.setdefault(k, {"zk_id": zk, "zk": PILOT.get(zk, zk), "korpus": korpus or "?", "type": rname(rooms), "source": src,
                               "on_sale": 0, "total": 0 if src in FULL_STOCK else None, "exits_28d": 0, "new_28d": 0,
                               "_ages": [], "_dp": []})
        is_on = lambda st: st in ON_SALE[src] if src in ON_SALE else True
        on_today = day in hist and is_on(hist[day])
        if src in FULL_STOCK:
            t["total"] += 1
        if on_today:
            t["on_sale"] += 1
        on_days = [d for d, st in hist.items() if is_on(st)]
        if not on_days:
            continue
        first, last = min(on_days), max(on_days)
        if first > first_day and first >= d28:
            t["new_28d"] += 1
        if last < day:                                   # left the list after `last`
            gone = next((d for d in days if d > last), day)
            if gone >= d28:
                t["exits_28d"] += 1
            if first > first_day and gone >= d56:
                t["_ages"].append(dd(gone, first))
    # price change of the same flats over 4 weeks
    for src, lot, zk, korpus, rooms in db.execute("select source, lot_id, zk_id, korpus, rooms from info where source in (%s)"
                                                  % ",".join("'%s'" % x for x in FLAT_SOURCES)):
        ps = db.execute("""select day, price from prices where source=? and lot_id=? and day>=? and origin in ('page','catalog','similar','listed')
                           order by day""", (src, lot, d28)).fetchall()
        k = (zk, korpus or "?", rname(rooms))
        if len(ps) >= 2 and k in out and ps[0][1] and dd(ps[-1][0], ps[0][0]) >= 7:
            out[k]["_dp"].append(100 * (ps[-1][1] / ps[0][1] - 1))
    return out


def agg_types(db, day):
    """Aggregate sources: granelle (layouts), forma (building × rooms). Exits are net decreases of the counts."""
    d28 = (date.fromisoformat(day) - timedelta(days=28)).isoformat()
    out = {}
    flat_level = {(r[0], r[1]) for r in db.execute("select distinct day, source from seen")}   # aggregates only where no flat list
    rows = [r for r in db.execute("select day, source, zk_id, coalesce(building,'?'), rooms, key, n, price_min from agg where day>=? order by day",
                                  (d28,)) if (day, r[1]) not in flat_level]
    by_key = {}
    for d, src, zk, b, rooms, key, n, pmin in rows:
        by_key.setdefault((src, zk, key), []).append((d, b, rooms, n, pmin))
    for (src, zk, key), hist in by_key.items():
        d_last, b, rooms, n, _ = hist[-1]
        k = (zk, b, rname(rooms))
        t = out.setdefault(k, {"zk_id": zk, "zk": PILOT.get(zk, zk), "korpus": b, "type": rname(rooms), "source": src,
                               "on_sale": 0, "total": None, "exits_28d": 0, "new_28d": 0, "_ages": [], "_dp": []})
        if d_last == day:
            t["on_sale"] += n
        prev = None
        for d, _, _, n_, p in hist:
            if prev is not None:
                t["exits_28d"] += max(0, prev - n_)
                t["new_28d"] += max(0, n_ - prev)
            prev = n_
        if d_last < day:
            t["exits_28d"] += prev or 0
        ps = [(d, p) for d, _, _, _, p in hist if p]
        if len(ps) >= 2 and dd(ps[-1][0], ps[0][0]) >= 7:
            t["_dp"].append(100 * (ps[-1][1] / ps[0][1] - 1))   # cheapest flat of the layout: a proxy, marked in the report
    return out


def finish(t, span_days):
    t["left_pct"] = round(100 * t["on_sale"] / t["total"], 1) if t["total"] else None
    rate = t["exits_28d"] / span_days if span_days else 0
    t["sellout_days"] = round(t["on_sale"] / rate) if rate else None
    t["median_days_on_sale"] = statistics.median(t["_ages"]) if t["_ages"] else None
    t["price_4w_pct"] = round(statistics.median(t["_dp"]), 1) if t["_dp"] else None
    fast = t["sellout_days"] is not None and t["sellout_days"] <= SELL_DAYS
    few = t["left_pct"] < LEFT_PCT if t["left_pct"] is not None else 0 < t["on_sale"] <= FEW
    up = (t["price_4w_pct"] or 0) > 0
    t["flags"] = ", ".join(x for x, ok in (("быстро уходит", fast), ("мало осталось", few), ("цена растёт", up)) if ok)
    t["signal"] = int(fast and few and up)
    for k in ("_ages", "_dp"):
        t.pop(k)
    return t


def main():
    db = sqlite3.connect(HERE / "devcat.db")
    days = [r[0] for r in db.execute("select distinct day from seen order by 1")]
    if not days:
        print("no finished devcat runs yet")
        return
    day, first = days[-1], days[0]
    span = min(28, dd(day, first))
    types = {**flat_types(db, day, first), **agg_types(db, day)}
    rows = sorted((finish(t, span) for t in types.values()), key=lambda t: (-t["signal"], -len(t["flags"]), t["zk"], t["korpus"], t["type"]))
    rows = [t for t in rows if t["on_sale"] or t["exits_28d"]]
    cols = ["zk", "korpus", "type", "source", "on_sale", "total", "left_pct", "exits_28d", "new_28d", "sellout_days",
            "median_days_on_sale", "price_4w_pct", "flags", "signal", "zk_id"]
    with (HERE / "dev_signals.csv").open("w", newline="") as f:
        w = csv.DictWriter(f, cols)
        w.writeheader()
        w.writerows({c: t.get(c) for c in cols} for t in rows)
    (HERE / "dev_signals.json").write_text(json.dumps({"day": day, "first_day": first, "history_days": span,
                                                        "rules": {"sellout_days_max": SELL_DAYS, "left_pct_max": LEFT_PCT, "few": FEW},
                                                        "types": rows}, ensure_ascii=False, indent=1))
    print(f"{day}: {len(rows)} types, history {span} days, signals {sum(t['signal'] for t in rows)}, "
          f"few left {sum('мало осталось' in t['flags'] for t in rows)}")


if __name__ == "__main__":
    main()
