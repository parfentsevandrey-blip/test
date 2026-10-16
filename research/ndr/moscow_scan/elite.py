"""Elite class only (inside MKAD): what sells best and most steadily. Reuses what_sells.py; writes elite.json."""
import contextlib
import io
import json
import sys
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
with contextlib.redirect_stdout(io.StringIO()):
    import what_sells as W  # noqa: E402

A, B, ACTIVE = W.A, W.B, W.ACTIVE
EL = {k: b for k, b in B.items() if b["cls"] == "Элитный"}
EL_ACT = [b for b in EL.values() if b["id"] in ACTIVE]


def money(b, idx=range(12)):
    return sum(b["area"][i] * b["price"][i] for i in idx)


def ticket(bs):
    """Average price of one sold flat, mln ₽."""
    n = sum(sum(b["real"]) for b in bs)
    return round(sum(money(b) for b in bs) / n / 1e6, 1) if n else None


def b_price(b):
    a = sum(b["area"])
    return money(b) / a if a else None


def b_ticket(b):
    n = sum(b["real"])
    return money(b) / n / 1e6 if n else None


def by(keyf):
    out = W.by(keyf, pool=list(EL.values()))
    for k, v in out.items():
        bs = [b for b in EL_ACT if keyf(b) == k]
        v["ticket_mln"] = ticket(bs)
        v["money_bln"] = round(sum(money(b) for b in bs) / 1e9, 1)
    return out


band = W.band
PRICE = by(lambda b: band(b_price(b) if b_price(b) else -1, [700_000, 1_000_000, 1_500_000, 2_500_000],
                          ["до 700 тыс/м²", "700 тыс – 1 млн", "1–1,5 млн", "1,5–2,5 млн", "2,5 млн+"]))
TICKET = by(lambda b: band(b_ticket(b) if b_ticket(b) else -1, [40, 70, 120, 250],
                           ["до 40 млн", "40–70 млн", "70–120 млн", "120–250 млн", "250 млн+"]))
OKRUG = by(lambda b: b["okrug"])
DIST = by(lambda b: b["dist"])
KM = by(lambda b: band(b["km"], [3, 6, 10], ["до 3 км (≈Садовое)", "3–6 км (≈ТТК)", "6–10 км", "10+ км"]))
STAGE = by(lambda b: band(max((b["endd"] - W.date(2026, 10, 1)).days / 30.4, 0) if b["endd"] else 99, [6, 18, 36],
                          ["сдача ≤ 6 мес", "6–18 мес", "18–36 мес", "36+ мес"]))
DEVS = by(lambda b: b["dev"])

# ЖК built from elite buildings only
ZK = []
for key, bs0 in A.complexes.items():
    bs = [B[b["id"]] for b in bs0 if b["id"] in EL and b["id"] in ACTIVE]
    if not bs:
        continue
    b0 = max(bs, key=lambda b: b["total"])
    g = W.group_metrics(bs)
    ZK.append({"name": b0["name"] or b0["dev"], "dev": b0["dev"], "class": "Элитный", "district": b0["dist"], "okrug": b0["okrug"], "km": b0["km"],
               "metro": b0["metro"], "long_run": W.long_run(bs), "completion": max((b["end"] or "") for b in bs),
               "ticket_mln": ticket(bs), "money_bln": round(sum(money(b) for b in bs) / 1e9, 2), **g})
ZK.sort(key=lambda z: (-z["p25"], -z["sum"]))

# idle elite buildings (no sale in 12 months)
IDLE = []
for b in EL.values():
    if b["id"] in ACTIVE:
        continue
    IDLE.append({"name": b["name"] or b["dev"], "dev": b["dev"], "district": b["dist"], "flats": b["total"], "sold": b["sold"],
                 "not_started": b["not_started"], "published": b["pub"], "completion": b["end"]})

# money share of elite in the whole market
money_cls = defaultdict(float)
flats_cls = defaultdict(int)
for b in ACTIVE.values():
    money_cls[b["cls"]] += money(b)
    flats_cls[b["cls"]] += sum(b["real"])
MONEY_SHARE = {c: {"money_bln": round(m / 1e9, 1), "money_pct": round(100 * m / sum(money_cls.values()), 1),
                   "flats_pct": round(100 * flats_cls[c] / sum(flats_cls.values()), 1)} for c, m in money_cls.items()}

el_money_m = [round(sum(b["area"][i] * b["price"][i] for b in EL_ACT) / 1e9, 2) for i in range(12)]
el_ticket_m = [round(sum(b["area"][i] * b["price"][i] for b in EL_ACT) / max(sum(b["real"][i] for b in EL_ACT), 1) / 1e6, 1) for i in range(12)]
el_price_m = [round(sum(b["area"][i] * b["price"][i] for b in EL_ACT) / max(sum(b["area"][i] for b in EL_ACT), 1)) for i in range(12)]

out = {"months": W.M, "market": W.group_metrics(EL_ACT) | {"idle_buildings": len(IDLE), "ticket_mln": ticket(EL_ACT),
                                                         "money_bln": round(sum(money(b) for b in EL_ACT) / 1e9, 1)},
       "money_share": MONEY_SHARE, "money_by_month_bln": el_money_m, "ticket_by_month_mln": el_ticket_m, "price_by_month": el_price_m,
       "price_bands": PRICE, "ticket_bands": TICKET, "okrugs": OKRUG, "districts": DIST, "distance": KM, "stage": STAGE,
       "developers": DEVS, "mix": W.MIX.get("Элитный"), "small_share": W.SMALL.get("Элитный"),
       "complexes": ZK, "idle": sorted(IDLE, key=lambda x: -x["flats"])}
(HERE / "elite.json").write_text(json.dumps(out, ensure_ascii=False, indent=1, default=str))

if __name__ == "__main__":
    mk = out["market"]
    print(f"elite: active {len(EL_ACT)}, idle {len(IDLE)} (not started {sum(i['not_started'] for i in IDLE)}), ЖК {len(ZK)} "
          f"(long-run {sum(z['long_run'] for z in ZK)}), sales {mk['sum']}, money {mk['money_bln']} bln, ticket {mk['ticket_mln']} mln")
    print("monthly", mk["monthly"]); print("money", el_money_m); print("ticket", el_ticket_m); print("price", el_price_m)
    print("money share", MONEY_SHARE)
    keys = ("buildings", "idle_buildings", "zk", "sum", "p25", "cv", "absorb_pct", "price_m2", "price_chg_pct", "deal_m2", "ticket_mln", "money_bln", "sold_pct")
    def tab(t, d):
        print("\n==", t); print(f"{'':<24}" + "".join(f"{k[:8]:>9}" for k in keys))
        for k, v in sorted(d.items(), key=lambda kv: -kv[1]["sum"]):
            print(f"{str(k)[:24]:<24}" + "".join(f"{str(v.get(x)):>9}" for x in keys))
    for t, d in [("price", PRICE), ("ticket", TICKET), ("okrug", OKRUG), ("km", KM), ("stage", STAGE)]:
        tab(t, d)
    tab("districts", {k: v for k, v in DIST.items() if v["buildings"] + v["idle_buildings"] >= 3})
    tab("devs top", dict(sorted(DEVS.items(), key=lambda kv: -kv[1]["sum"])[:12]))
    print("\n== ЖК")
    for z in ZK[:30]:
        print(f"{str(z['name'])[:34]:<34} {str(z['district'])[:16]:<16} lr {int(z['long_run'])} p25 {z['p25']:>5} med {z['median']:>4} cv {z['cv']} sum {z['sum']:>4} "
              f"abs {z['absorb_pct']} rem {z['remaining']:>4} sold {z['sold_pct']} price {z['price_m2']} chg {z['price_chg_pct']} ticket {z['ticket_mln']} money {z['money_bln']}")
    print("\n== laggards: big remaining, slow")
    for z in sorted([z for z in ZK if z["long_run"] and z["remaining"] >= 150], key=lambda z: z["absorb_pct"] or 0)[:12]:
        print(f"{str(z['name'])[:34]:<34} {str(z['district'])[:16]:<16} rem {z['remaining']} abs {z['absorb_pct']} sum {z['sum']} price {z['price_m2']} chg {z['price_chg_pct']} end {z['completion']}")
    print("\n== idle (opened sales, zero deals)")
    for i in [i for i in out["idle"] if not i["not_started"]][:15]:
        print(i)
    print("\nmix", out["mix"], "\nsmall", out["small_share"])
