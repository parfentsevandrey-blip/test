"""[С2] Resale and assignment market on ЦИАН in the pilot ЖК (owner decision 16.10: ЦИАН only, no Avito / Yandex).

One flat listed by several sellers is one lot: (ЖК, корпус, этаж, площадь, комнатность). Per type — ЖК × корпус × комнатность
(and the ЖК × комнатность rollup), separately for houses under construction (assignments) and completed houses (resale):
  active            lots listed in the latest snapshot
  exposure_days     median days on ЦИАН of active lots (from the listing's creation date)
  cut_share         share of active lots whose price was ever cut (offer card history or our snapshots)
  gone_28d          lots that left ЦИАН in the last 28 days;  gone_nocut — of them, without a price cut (a proxy of a sale)
  nocut_share       gone_nocut / gone_28d
  sales_month       gone_nocut per month; until 28 days of history and >= 7 days: scaled from the history we have;
                    under 7 days: the project's monthly pace (наш.дом.рф) × the type's share of the developer stock — «оценка»
  supply_months     active / sales_month
  vs_dev_pct        median ₽/м² of the type vs the developer's median ₽/м² for the same ЖК × комнатность (same корпус if >= 3)
Signal (С2): supply_months < 3, exposure_days < 60, vs_dev_pct >= 0, at least 3 lots on sale.
"""
import csv
import json
import sqlite3
import statistics
from collections import defaultdict
from datetime import date, timedelta
from pathlib import Path

import importlib.util

HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("model", HERE / "model.py")
M = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(M)
SUPPLY_MAX, EXPOSURE_MAX, MIN_LOTS = 3, 60, 3      # a type with fewer than 3 lots on sale gives no signal


def med(xs):
    xs = [x for x in xs if x is not None]
    return statistics.median(xs) if xs else None


def card_cut(card):
    """ЦИАН lists price changes newest first: a cut is a newest price below an earlier one."""
    pc = json.loads(card["price_changes"] or "[]") if card else []
    prices = [p["priceData"]["price"] for p in pc if p.get("priceData")]
    return any(newer < older for newer, older in zip(prices, prices[1:]))


def build():
    db = sqlite3.connect(HERE / "lots.db")
    db.row_factory = sqlite3.Row
    days = [r[0] for r in db.execute("select distinct day from obs order by day")]
    day, first = days[-1], days[0]
    nxt = {a: b for a, b in zip(days, days[1:])}
    d28 = (date.fromisoformat(day) - timedelta(days=28)).isoformat()
    span = (date.fromisoformat(day) - date.fromisoformat(first)).days
    offers = {r["offer_id"]: dict(r) for r in db.execute("select * from offers")}
    cards = {}
    for r in db.execute("select * from cards order by day"):
        cards[r["offer_id"]] = dict(r)
    pace = M.project_pace()
    pilot = {z: n for d in json.loads((HERE / "pilot_zk.json").read_text()).values() for z, n in d.items()}

    lots = {}                                   # lot key -> facts
    for r in db.execute("select day, offer_id, price, price_m2 from obs order by day"):
        o = offers[r["offer_id"]]
        if o["is_apartments"] or o["zk_id"] not in pilot:     # pilot ЖК only: extra ЖК (extra_zk.json) get their own analysis
            continue
        sel = M.seller(o["sale_type"], o["from_developer"])
        t = M.rtype(o["rooms"], o["flat_type"])
        house = o["house_name"] or (str(o["house_id"]) if o["house_id"] else "?")
        key = (o["zk_id"], house, o["floor"], round(o["area"] or 0), t, sel == "developer")
        L = lots.setdefault(key, {"zk": o["zk_id"], "house": house, "type": t, "dev": sel == "developer",
                                  "ready": o["category"] == "flatSale", "days": {}, "created": None, "cut": False, "m2": None})
        prev = L["days"].get(r["day"])
        L["days"][r["day"]] = min(prev, r["price"]) if prev and r["price"] else (r["price"] or prev)
        L["m2"] = r["price_m2"] or L["m2"]
        c = (o.get("created") or "")[:10]
        L["created"] = min(L["created"], c) if L["created"] and c else (c or L["created"])
        L["cut"] = L["cut"] or card_cut(cards.get(r["offer_id"]))
    for L in lots.values():
        ps = [L["days"][d] for d in sorted(L["days"]) if L["days"][d]]
        L["cut"] = L["cut"] or any(b < a for a, b in zip(ps, ps[1:]))
        L["active"] = day in L["days"]
        last = max(L["days"])
        L["gone_day"] = None if L["active"] else nxt.get(last)

    # developer reference ₽/м²: same ЖК × комнатность, same корпус when it has >= 3 developer lots
    dev_m2 = defaultdict(list)
    for L in lots.values():
        if L["dev"] and L["active"]:
            dev_m2[(L["zk"], L["type"])].append(L["m2"])
            dev_m2[(L["zk"], L["house"], L["type"])].append(L["m2"])
    dev_share = defaultdict(lambda: defaultdict(int))
    for L in lots.values():
        if L["dev"] and L["active"]:
            dev_share[L["zk"]][L["type"]] += 1

    rows = []
    for level in ("ЖК", "корпус"):
        groups = defaultdict(list)
        for L in lots.values():
            if L["dev"] or L["type"] == "своб.":
                continue
            k = (L["zk"], L["house"] if level == "корпус" else "все", L["type"], L["ready"])
            groups[k].append(L)
        for (zk, house, t, ready), ls in groups.items():
            act = [L for L in ls if L["active"]]
            gone = [L for L in ls if L["gone_day"] and L["gone_day"] >= d28]
            nocut = [L for L in gone if not L["cut"]]
            exp = med((date.fromisoformat(day) - date.fromisoformat(L["created"])).days for L in act if L["created"])
            if span >= 7:
                sales, how = len(nocut) * 30 / min(span, 28), "по выбытию"
            else:
                p = (pace.get(zk) or {}).get("pace_q3")
                share = dev_share[zk][t] / sum(dev_share[zk].values()) if dev_share[zk] else None
                sales = p * share if p and share else None            # the type in the whole ЖК
                if sales and level == "корпус":                        # this корпус's part of the type's resale supply
                    zk_act = sum(1 for L in lots.values() if L["zk"] == zk and not L["dev"] and L["active"] and L["type"] == t)
                    sales *= len(act) / zk_act if zk_act else 0
                how = "оценка: темп проекта × доля типа"
            ref = dev_m2.get((zk, house, t)) if level == "корпус" and len(dev_m2.get((zk, house, t), [])) >= 3 else dev_m2.get((zk, t))
            ref_m2, m2 = med(ref or []), med(L["m2"] for L in act)
            r = {"level": level, "zk": pilot.get(zk, zk), "house": house, "type": t, "stage": "готовый" if ready else "строится",
                 "active": len(act), "exposure_days": exp, "cut_share": round(sum(L["cut"] for L in act) / len(act), 2) if act else None,
                 "gone_28d": len(gone), "gone_nocut": len(nocut), "nocut_share": round(len(nocut) / len(gone), 2) if gone else None,
                 "sales_month": round(sales, 1) if sales else None, "sales_how": how,
                 "supply_months": round(len(act) / sales, 1) if sales else None,
                 "m2": round(m2) if m2 else None, "dev_m2": round(ref_m2) if ref_m2 else None,
                 "vs_dev_pct": round(100 * (m2 / ref_m2 - 1), 1) if m2 and ref_m2 else None, "zk_id": zk}
            ok = [r["supply_months"] is not None and r["supply_months"] < SUPPLY_MAX,
                  r["exposure_days"] is not None and r["exposure_days"] < EXPOSURE_MAX,
                  r["vs_dev_pct"] is not None and r["vs_dev_pct"] >= 0]
            r["flags"] = ", ".join(n for n, x in zip(("запас < 3 мес.", "экспозиция < 60 дн.", "не дешевле застройщика"), ok) if x)
            r["signal"] = int(all(ok) and len(act) >= MIN_LOTS)
            rows.append(r)
    rows = [r for r in rows if r["active"] or r["gone_28d"]]
    rows.sort(key=lambda r: (-r["signal"], -len(r["flags"]), r["level"], r["zk"], r["house"], r["type"]))
    with (HERE / "c2_signals.csv").open("w", newline="") as f:
        w = csv.DictWriter(f, list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)
    out = {"day": day, "first_day": first, "history_days": span, "lots": len(lots),
           "rules": {"supply_months_max": SUPPLY_MAX, "exposure_days_max": EXPOSURE_MAX, "min_lots": MIN_LOTS}, "types": rows}
    (HERE / "c2_signals.json").write_text(json.dumps(out, ensure_ascii=False, indent=1))
    return out


if __name__ == "__main__":
    o = build()
    sig = [r for r in o["types"] if r["signal"]]
    print(f"{o['day']}: lots {o['lots']}, types {len(o['types'])}, history {o['history_days']} d, signals {len(sig)}")
    for r in sig[:15]:
        print(r["level"], r["zk"], r["house"], r["type"], r["stage"], r["active"], r["supply_months"], r["exposure_days"], r["vs_dev_pct"], r["sales_how"])
