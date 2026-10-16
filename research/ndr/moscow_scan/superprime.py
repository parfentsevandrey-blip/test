"""Super-prime: projects inside MKAD with deal price >= 2 mln ₽/m² (flats; apartments counted separately). Writes superprime.json.

Project price = deal-weighted price of flats over 12 months; if a project sold no flats, the price of its non-residential
deals (apartments); if nothing was sold at all, the average price the developer declares on the object page.
"""
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

A, Ex, st = W.A, W.E, W.st
THRESHOLD = 2_000_000
NEAR = 1_700_000                                     # 1.7-2.0 mln: shown as the closest comparison group
Q = {"IV кв. 2025": range(0, 3), "I кв. 2026": range(3, 6), "II кв. 2026": range(6, 9), "III кв. 2026": range(9, 12)}


def node(bid):
    return (st["objects"].get(f"/сервисы/каталог-новостроек/объект/{bid}") or {}).get("next") or {}


def series(rows, key):
    return [(r.get(key) or 0) for r in rows] if len(rows) == 12 else [0] * 12


bld = {}
for bid, b in Ex.inside_b.items():
    n = node(bid)
    mon = n.get("monthly") or {}
    res, nr = mon.get("residential") or [], mon.get("nonResidential") or []
    bld[bid] = dict(b, n_real=series(nr, "realised"), n_area=series(nr, "soldArea"), n_price=series(nr, "priceAvg"),
                    declared=float(n.get("averagePrice") or 0), nonliving=int(n.get("nonLivingCount") or 0),
                    dist=W.district_of(b["district"]), km=round(W.km(b["xy"], W.KREMLIN), 1), mix=W.flat_mix(f"/сервисы/каталог-новостроек/объект/{bid}"))
known = [k for k, b in bld.items() if b["dist"]]
for b in bld.values():
    if not b["dist"]:
        b["dist"] = bld[min(known, key=lambda k: W.km(bld[k]["xy"], b["xy"]))]["dist"]
    b["okrug"] = W.DISTRICT_OKRUG.get(b["dist"], "?")


def money(bs, area="area", price="price", idx=range(12)):
    return sum(b[area][i] * b[price][i] for b in bs for i in idx)


def project(key, bs):
    real = [sum(b["real"][i] for b in bs) for i in range(12)]
    area = [sum(b["area"][i] for b in bs) for i in range(12)]
    m = money(bs)
    n_real = [sum(b["n_real"][i] for b in bs) for i in range(12)]
    n_area = sum(sum(b["n_area"]) for b in bs)
    n_m = money(bs, "n_area", "n_price")
    decl_w = sum(b["total"] + b["nonliving"] for b in bs) or 1
    declared = sum(b["declared"] * (b["total"] + b["nonliving"]) for b in bs) / decl_w
    price = m / sum(area) if sum(area) else (n_m / n_area if n_area else declared)
    basis = "сделки с квартирами" if sum(area) else ("сделки с апартаментами" if n_area else "заявленная цена")
    total, sold = sum(b["total"] for b in bs), sum(b["sold"] for b in bs)
    stock = [0] * 12
    s = max(total - sold, 0)
    for i in range(11, -1, -1):
        s += real[i]
        stock[i] = s
    mixc = defaultdict(int); mix_area = 0; mix_n = 0
    for b in bs:
        for k, (c, a) in b["mix"].items():
            mixc[k] += c; mix_area += c * a; mix_n += c
    b0 = max(bs, key=lambda b: b["total"] + b["nonliving"])
    sales = sum(real)
    return {
        "name": b0["name"] or b0["dev"], "dev": b0["dev"], "class": "/".join(sorted({b["cls"] or "?" for b in bs})),
        "district": b0["dist"], "okrug": b0["okrug"], "km": b0["km"], "buildings": len(bs), "ids": [b["id"] for b in bs],
        "flats": total, "apartments": sum(b["nonliving"] for b in bs), "sold": sold, "sold_pct": round(100 * sold / total, 1) if total else None,
        "remaining": max(total - sold, 0), "sales_12m": sales, "monthly": real, "months_with_sales": sum(1 for x in real if x),
        "quarters": {q: sum(real[i] for i in idx) for q, idx in Q.items()},
        "apart_sales_12m": sum(n_real), "apart_money_bln": round(n_m / 1e9, 2),
        "price_m2": round(price), "price_basis": basis, "declared_m2": round(declared),
        "price_chg_pct": W.like_for_like(bs), "deal_m2": round(sum(area) / sales, 1) if sales else None,
        "ticket_mln": round(m / sales / 1e6, 1) if sales else None, "money_bln": round(m / 1e9, 2),
        "absorb_pct": round(100 * (sales / 12) / (sum(stock) / 12), 2) if sum(stock) else None,
        "stock_avg_m2": round(mix_area / mix_n, 1) if mix_n else None,
        "mix_pct": {k: round(100 * mixc[k] / mix_n) for k in mixc} if mix_n else {},
        "completion": max((b["end"] or "") for b in bs), "endd": max((b["endd"] for b in bs if b.get("endd")), default=None) if False else None,
        "published": min((b["pub"] or "") for b in bs), "not_started": all(b["not_started"] for b in bs),
    }


groups = defaultdict(list)
for b in bld.values():
    if any(x in (b["dev"] or "") for x in W.EXCLUDE_DEV) or len(b["real"]) != 12:
        continue
    groups[(b["dev"], A.norm(b["name"]))].append(b)
P = [project(k, bs) for k, bs in groups.items()]
SUPER = sorted([p for p in P if p["price_m2"] >= THRESHOLD], key=lambda p: (-p["sales_12m"], -p["money_bln"]))
NEARG = sorted([p for p in P if NEAR <= p["price_m2"] < THRESHOLD and p["okrug"] == "ЦАО"], key=lambda p: -p["sales_12m"])
ACTIVE = [p for p in SUPER if p["sales_12m"]]


def agg(ps):
    sales = sum(p["sales_12m"] for p in ps)
    m = sum(p["money_bln"] for p in ps)
    area = sum((p["deal_m2"] or 0) * p["sales_12m"] for p in ps)
    stock = sum(p["remaining"] for p in ps)
    return {"projects": len(ps), "with_sales": sum(1 for p in ps if p["sales_12m"]), "sales_12m": sales, "money_bln": round(m, 2),
            "ticket_mln": round(m * 1e3 / sales, 1) if sales else None, "deal_m2": round(area / sales, 1) if sales else None,
            "price_m2": round(m * 1e9 / area) if area else None, "remaining": stock,
            "years_to_sell": round(stock / sales, 1) if sales else None,
            "monthly": [sum(p["monthly"][i] for p in ps) for i in range(12)],
            "quarters": {q: sum(p["quarters"][q] for p in ps) for q in Q}}


def by(keyf, ps=SUPER):
    g = defaultdict(list)
    for p in ps:
        g[keyf(p)].append(p)
    return {k: agg(v) for k, v in g.items()}


band = W.band
out = {
    "threshold": THRESHOLD, "months": W.M, "total": agg(SUPER), "near": agg(NEARG), "projects": SUPER, "near_projects": NEARG,
    "by_district": by(lambda p: p["district"]),
    "by_price": by(lambda p: band(p["price_m2"], [2_500_000, 3_500_000], ["2–2,5 млн/м²", "2,5–3,5 млн/м²", "3,5 млн/м² +"])),
    "by_size": by(lambda p: band(p["flats"], [40, 80, 150], ["до 40 квартир (клубный дом)", "40–80", "80–150", "150+"])),
    "by_ticket": by(lambda p: band(p["ticket_mln"] or -1, [150, 300, 500], ["до 150 млн", "150–300 млн", "300–500 млн", "500 млн+"])),
    "by_stage": by(lambda p: band(max((W.parse_date(p["completion"]) - W.date(2026, 10, 1)).days / 30.4, 0) if W.parse_date(p["completion"]) else 99,
                                  [12, 24], ["сдача в течение года", "через 1–2 года", "через 2+ года"])),
    "market_money_bln": round(sum(sum(b["area"][i] * b["price"][i] for i in range(12)) for b in W.ACTIVE.values()) / 1e9, 1),
    "market_sales": W.out["market"]["sum"] if hasattr(W, "out") else None,
}
(HERE / "superprime.json").write_text(json.dumps(out, ensure_ascii=False, indent=1, default=str))

if __name__ == "__main__":
    t = out["total"]
    print(f"super-prime: {t['projects']} projects ({t['with_sales']} with sales), {t['sales_12m']} flats, {t['money_bln']} bln, ticket {t['ticket_mln']}, "
          f"deal {t['deal_m2']} m2, price {t['price_m2']}, remaining {t['remaining']} (~{t['years_to_sell']} yrs)")
    print("monthly", t["monthly"], "quarters", t["quarters"])
    print("near", {k: out["near"][k] for k in ("projects", "sales_12m", "money_bln", "ticket_mln")})
    for p in SUPER:
        print(f"{p['name'][:30]:<30} {p['district'][:12]:<12} fl {p['flats']:>4} ap {p['apartments']:>3} sold {p['sold_pct']} rem {p['remaining']:>3} "
              f"s12 {p['sales_12m']:>3} m+{p['months_with_sales']:>2} q {list(p['quarters'].values())} ap12 {p['apart_sales_12m']} "
              f"price {p['price_m2']} ({p['price_basis'][:8]}) chg {p['price_chg_pct']} deal {p['deal_m2']} stock {p['stock_avg_m2']} "
              f"ticket {p['ticket_mln']} money {p['money_bln']} end {p['completion']} ns {int(p['not_started'])}")
    for k in ("by_district", "by_price", "by_size", "by_ticket", "by_stage"):
        print("\n==", k)
        for g, v in sorted(out[k].items(), key=lambda kv: -kv[1]["sales_12m"]):
            print(f"  {g:<28} proj {v['projects']:>2} ({v['with_sales']}) sales {v['sales_12m']:>4} money {v['money_bln']:>6} ticket {v['ticket_mln']} "
                  f"deal {v['deal_m2']} rem {v['remaining']} yrs {v['years_to_sell']} q {list(v['quarters'].values())}")
    print("\nnear:")
    for p in NEARG:
        print(f"  {p['name'][:30]:<30} {p['district']:<12} s12 {p['sales_12m']} price {p['price_m2']} ticket {p['ticket_mln']} rem {p['remaining']}")
