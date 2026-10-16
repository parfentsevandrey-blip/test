"""Resale pilot model: where is clear demand for specific lot types, and which lots to buy for a <= 6-month resale.

Inputs : lots.db (daily snapshots of cian.ru listings + ЖК cards + offer cards), ../moscow_scan/efficiency.json (project pace,
         наш.дом.рф). Outputs: signals.csv (ЖК x lot type), candidates.csv (lots with a deal calculation), model.json.

Lot type  = ЖК x rooms (студия, 1к, 2к, 3к, 4к+). Sellers: developer (ДДУ), assignment (переуступка), resale (вторичка).
Signals per type (all transparent, each is a flag with its number):
  scarcity  - developer has few of this type left on the site (count and share of its window);
  premium   - resale/assignment asks per m² vs the developer's price per m² for the same type (exit above entry);
  interest  - median views per day of resale offers of the type vs the ЖК median (buyers are looking);
  stale     - share of resale offers older than 90 days or with price cuts (exit is hard);
  churn     - share of yesterday's resale offers gone today (needs >= 2 snapshots; sold or withdrawn).
Deal (legal entity, no agents): buy at the offer price, sell at the type's resale median per m² minus a bargaining discount,
  minus assignment fee to the developer, registration, holding cost of capital, VAT on the assignment margin and profit tax.
All money parameters are in DEAL below: check them with the accountant before relying on the numbers.
"""
import csv
import json
import re
import sqlite3
import statistics as stt
from collections import defaultdict
from datetime import datetime, date
from pathlib import Path

HERE = Path(__file__).resolve().parent
DB = HERE / "lots.db"

DEAL = {
    "hold_months": 6,
    "capital_cost_year": 0.18,     # opportunity cost of own money, per year
    "assignment_fee": 0.015,       # developer's fee for consent to assignment, share of the contract price (varies by developer)
    "registration_rub": 50_000,    # state duty + registration of DDU and assignment, both sides
    "bargain": 0.03,               # discount from the resale ask to the actual sale price
    "vat_on_margin": 22 / 122,     # VAT on the margin when assigning DDU rights (legal entity) - confirm with the accountant
    "profit_tax": 0.25,
    "min_roi_6m": 0.10,            # net return on money over the holding period to call a lot a candidate
}
ROOMS = {0: "студия", 1: "1к", 2: "2к", 3: "3к"}
NDR_KEYS = {  # ЦИАН ЖК id -> name fragments of the ЖК in the наш.дом.рф scan (project pace)
    "2812286": ["ОСТРОВ"], "7231": ["СИМВОЛ"], "5340468": ["ДРИМ РИВА"], "3401924": ["Дубровка от Гранель"],
    "5699304": ["Нижегородская от Гранель"], "4494942": ["Павелецкая от Гранель"], "5221539": ["Мнёвники от Гранель"],
    "7889": ["ЗИЛАРТ"], "4888961": ["Amber City"], "2446053": ["Sydney City"], "4377697": ["Сидней Прайм", "Sydney Prime"],
    "4183417": ["СОУЛ"], "3202206": ["HIGH LIFE"],
}


def rtype(rooms, flat_type):
    if flat_type == "studio" or rooms == 0:
        return "студия"
    if rooms is None:
        return "своб."
    return ROOMS.get(rooms, "4к+")


def seller(sale_type, from_dev):
    if from_dev or sale_type == "fz214":
        return "developer"
    if sale_type == "dupt":
        return "assignment"
    return "resale"


def med(xs):
    xs = [x for x in xs if x is not None]
    return stt.median(xs) if xs else None


def views_per_day(card, day):
    if not card or card["views_total"] is None or not card["publication_date"]:
        return None
    pub = datetime.fromtimestamp(int(card["publication_date"])).date()
    age = max((date.fromisoformat(day) - pub).days, 1)
    return card["views_total"] / age


def project_pace():
    p = HERE.parent / "moscow_scan" / "efficiency.json"
    if not p.exists():
        return {}
    rk = json.loads(p.read_text())
    rows = rk["ranking"]
    allz = json.loads((HERE.parent / "moscow_scan" / "what_sells.json").read_text())["complexes"]
    out = {}
    for zk, keys in NDR_KEYS.items():
        ms = [z for z in allz if any(k.lower() in (z["name"] or "").lower() for k in keys)]
        if ms:
            out[zk] = {"pace_q3": round(sum(sum(z["monthly"][-3:]) for z in ms) / 3, 1), "flats": sum(z["flats"] for z in ms),
                       "remaining": sum(z["remaining"] for z in ms), "stable_pct": next((r["stable_pct"] for r in rows
                                                                                         if any(k.lower() in r["name"].lower() for k in keys)), None)}
    return out


def deal(buy_price, area, exit_m2, via_assignment=True):
    d = DEAL
    if not (buy_price and area and exit_m2):
        return None
    sale = exit_m2 * area * (1 - d["bargain"])
    fee = buy_price * d["assignment_fee"] if via_assignment else 0
    carry = buy_price * d["capital_cost_year"] * d["hold_months"] / 12
    gross = sale - buy_price - fee - d["registration_rub"]
    vat = max(gross, 0) * d["vat_on_margin"] if via_assignment else 0
    tax = max(gross - vat, 0) * d["profit_tax"]
    net = gross - vat - tax - carry
    return {"sale": round(sale), "net": round(net), "roi_6m": round(net / buy_price, 3), "gross_spread": round(sale / buy_price - 1, 3)}


def build():
    db = sqlite3.connect(DB)
    db.row_factory = sqlite3.Row
    days = [r[0] for r in db.execute("select distinct day from obs order by day")]
    day = days[-1]
    prev = days[-2] if len(days) > 1 else None
    offers = {r["offer_id"]: dict(r) for r in db.execute("select * from offers")}
    obs = {r["offer_id"]: dict(r) for r in db.execute("select * from obs where day=?", (day,))}
    prev_ids = {r[0] for r in db.execute("select offer_id from obs where day=?", (prev,))} if prev else set()
    cards = {}
    for r in db.execute("select * from cards order by day"):
        cards[r["offer_id"]] = dict(r)
    zkc = {r["zk_id"]: json.loads(r["data"]) for r in db.execute("select zk_id, data from zk_cards where day=(select max(day) from zk_cards z where z.zk_id=zk_cards.zk_id)")}
    meta = {r["zk_id"]: dict(r) for r in db.execute("select * from zk_meta")}
    pilot = json.loads((HERE / "pilot_zk.json").read_text())
    zk_name = {z: n for d in pilot.values() for z, n in d.items()}
    zk_dev = {z: dv for dv, d in pilot.items() for z in d}
    pace = project_pace()
    room_key = {"studio": "студия", "oneRoom": "1к", "twoRoom": "2к", "threeRoom": "3к", "fourRoom": "4к+", "fiveRoom": "4к+", "freeLayout": "своб."}

    groups = defaultdict(list)
    for oid, ob in obs.items():
        o = offers[oid]
        if o["is_apartments"]:
            continue
        rec = dict(o, **ob, seller=seller(o["sale_type"], o["from_developer"]), type=rtype(o["rooms"], o["flat_type"]),
                   vpd=views_per_day(cards.get(oid), day), card=cards.get(oid))
        groups[(o["zk_id"], rec["type"])].append(rec)

    zk_vpd = defaultdict(list)
    for (zk, _), recs in groups.items():
        zk_vpd[zk] += [r["vpd"] for r in recs if r["seller"] != "developer" and r["vpd"] is not None]
    zk_vpd = {k: med(v) for k, v in zk_vpd.items()}

    signals, candidates = [], []
    for (zk, t), recs in sorted(groups.items()):
        dev = [r for r in recs if r["seller"] == "developer"]
        res = [r for r in recs if r["seller"] != "developer"]
        card = zkc.get(zk) or {}
        dev_cnt_card = sum((v.get("count") or 0) for k, v in (card.get("rooms") or {}).items() if room_key.get(k) == t) if card else None
        dev_window = card.get("from_dev") if card else None
        dev_m2, res_m2 = med(r["price_m2"] for r in dev), med(r["price_m2"] for r in res)
        res_vpd = med(r["vpd"] for r in res)
        ages = [(date.fromisoformat(day) - date.fromisoformat(r["created"][:10])).days for r in res if r.get("created")]
        cuts = [r for r in res if r["card"] and len(json.loads(r["card"]["price_changes"] or "[]")) > 1
                and json.loads(r["card"]["price_changes"])[-1]["priceData"]["price"] < json.loads(r["card"]["price_changes"])[0]["priceData"]["price"]]
        gone = None
        if prev:
            prev_res = [oid for oid in prev_ids if offers[oid]["zk_id"] == zk and rtype(offers[oid]["rooms"], offers[oid]["flat_type"]) == t
                        and seller(offers[oid]["sale_type"], offers[oid]["from_developer"]) != "developer"]
            gone = round(sum(1 for oid in prev_res if oid not in obs) / len(prev_res), 2) if prev_res else None
        s = {
            "zk_id": zk, "zk": zk_name.get(zk, zk), "developer": zk_dev.get(zk), "type": t,
            "dev_offers_site": dev_cnt_card, "dev_window_share": round(dev_cnt_card / dev_window, 2) if dev_cnt_card is not None and dev_window else None,
            "dev_listed": len(dev), "dev_price_m2": round(dev_m2) if dev_m2 else None,
            "resale_listed": len(res), "assignments": sum(1 for r in res if r["seller"] == "assignment"),
            "resale_price_m2": round(res_m2) if res_m2 else None,
            "premium_pct": round(100 * (res_m2 / dev_m2 - 1), 1) if dev_m2 and res_m2 else None,
            "resale_views_day": round(res_vpd, 1) if res_vpd else None,
            "interest_x": round(res_vpd / zk_vpd[zk], 2) if res_vpd and zk_vpd.get(zk) else None,
            "resale_median_age_days": med(ages), "stale_share": round(sum(1 for a in ages if a > 90) / len(ages), 2) if ages else None,
            "price_cut_share": round(len(cuts) / len(res), 2) if res else None, "churn_1d": gone,
            "project_pace_month": (pace.get(zk) or {}).get("pace_q3"), "project_remaining": (pace.get(zk) or {}).get("remaining"),
        }
        flags = []
        if s["dev_offers_site"] is not None and s["dev_offers_site"] <= 3:
            flags.append("дефицит у застройщика")
        if s["premium_pct"] is not None and s["premium_pct"] >= 5:
            flags.append("вторичка дороже застройщика")
        if s["interest_x"] is not None and s["interest_x"] >= 1.3:
            flags.append("высокий интерес")
        if s["stale_share"] is not None and s["stale_share"] >= 0.5:
            flags.append("вторичка залёживается")
        if s["price_cut_share"] is not None and s["price_cut_share"] >= 0.3:
            flags.append("продавцы снижают цену")
        if s["churn_1d"] is not None and s["churn_1d"] >= 0.05:
            flags.append("быстрое выбытие")
        score = (2 * ("дефицит у застройщика" in flags) + 2 * ("вторичка дороже застройщика" in flags) + ("высокий интерес" in flags)
                 + ("быстрое выбытие" in flags) - ("вторичка залёживается" in flags) - ("продавцы снижают цену" in flags))
        s["flags"] = "; ".join(flags)
        s["score"] = score
        signals.append(s)

        exit_m2 = res_m2 if res_m2 and len(res) >= 3 else None
        for r in recs:
            if not exit_m2 or not r["price"] or not r["area"]:
                continue
            if r["seller"] == "developer" or (r["price_m2"] and r["price_m2"] < 0.92 * exit_m2):
                calc = deal(r["price"], r["area"], exit_m2, via_assignment=(r["seller"] != "resale"))
                if calc and calc["roi_6m"] >= DEAL["min_roi_6m"]:
                    candidates.append({"offer_id": r["offer_id"], "url": f"https://www.cian.ru/sale/flat/{r['offer_id']}/", "zk": s["zk"],
                                       "developer": s["developer"], "type": t, "seller": r["seller"], "house": r["house_name"],
                                       "finish": r["finish"], "floor": r["floor"], "floors": r["floors"], "area": r["area"],
                                       "decoration": r["decoration"], "price": r["price"], "price_m2": r["price_m2"],
                                       "exit_m2": round(exit_m2), "type_score": score, "type_flags": s["flags"], **calc})
    signals.sort(key=lambda s: (-s["score"], -(s["premium_pct"] or -99)))
    candidates.sort(key=lambda c: (-c["type_score"], -c["roi_6m"]))
    for name, rows in (("signals.csv", signals), ("candidates.csv", candidates)):
        if rows:
            with open(HERE / name, "w", newline="") as f:
                w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
                w.writeheader()
                w.writerows(rows)
    out = {"day": day, "prev_day": prev, "deal_params": DEAL, "signals": signals, "candidates": candidates,
           "zk_cards": zkc, "coverage": {zk: {"listed_today": sum(1 for o in obs.values() if offers[o["offer_id"]]["zk_id"] == zk),
                                               "site_total": (zkc.get(zk) or {}).get("props")} for zk in zk_name}}
    (HERE / "model.json").write_text(json.dumps(out, ensure_ascii=False, indent=1, default=str))
    return out


if __name__ == "__main__":
    out = build()
    print("day", out["day"], "prev", out["prev_day"], "signals", len(out["signals"]), "candidates", len(out["candidates"]))
    for s in out["signals"][:25]:
        print(f"{s['zk'][:20]:<20} {s['type']:<6} score {s['score']:>2} dev {s['dev_offers_site']} {s['dev_price_m2']} | res {s['resale_listed']} "
              f"(уст {s['assignments']}) {s['resale_price_m2']} prem {s['premium_pct']}% vpd {s['resale_views_day']} x{s['interest_x']} "
              f"age {s['resale_median_age_days']} stale {s['stale_share']} | {s['flags']}")
