"""Export the MKAD-wide scan (all buildings under construction inside MKAD) from ndr_workflow/state.json.

Outputs in this folder:
  buildings.csv  - one row per building (наш.дом.рф object) with 12-month sales and prices
  complexes.csv  - one row per ЖК (buildings grouped by developer + name) with the report metrics
  scan.json      - the same data, structured, plus market totals by class and month
"""
import contextlib
import csv
import io
import json
import sys
from collections import Counter
from datetime import date
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / "ndr_workflow"))
import mkad  # noqa: E402
with contextlib.redirect_stdout(io.StringIO()):
    import analyze2 as A  # noqa: E402

TODAY = date(2026, 10, 15)
# Moscow districts (old Moscow) lying outside MKAD; used only for buildings without an exact geoposition
OUTSIDE_DISTRICTS = ("Митино", "Куркино", "Молжаниновский", "Северный", "Новокосино", "Косино-Ухтомский", "Некрасовка",
                     "Южное Бутово", "Северное Бутово", "Солнцево", "Ново-Переделкино", "Внуково", "Восточный",
                     "Матушкино", "Савёлки", "Старое Крюково", "Силино", "Крюково", "поселение", "Зеленоград")
st = json.loads((HERE.parent / "ndr_workflow" / "state.json").read_text())
geo = {o["objId"]: o for o in json.loads((HERE.parent / "ndr" / "moscow_map.json").read_text())}


def in_mkad(b):
    g = geo.get(b["id"])
    if g and g["isGeoposition"]:
        return mkad.inside(b["xy"][0], b["xy"][1]), "координаты"
    district = b.get("district") or ""
    return not any(x.lower() in district.lower() for x in OUTSIDE_DISTRICTS), "район (нет точных координат)"


def months_until(dmy):
    try:
        d, m, y = (int(x) for x in dmy.split("."))
        return max((y - TODAY.year) * 12 + (m - TODAY.month), 0)
    except Exception:
        return None


def series(bs):
    sales = [sum(b["real"][i] for b in bs if len(b["real"]) == 12) for i in range(12)]
    price = []
    for i in range(12):
        a = sum(b["area"][i] for b in bs if len(b["area"]) == 12)
        m = sum(b["area"][i] * b["price"][i] for b in bs if len(b["area"]) == 12)
        price.append(round(m / a) if a else None)
    return sales, price


def wavg(bs, idx):
    a = sum(b["area"][i] for b in bs for i in idx if len(b["area"]) == 12)
    m = sum(b["area"][i] * b["price"][i] for b in bs for i in idx if len(b["area"]) == 12)
    return round(m / a) if a else None


def metrics(bs):
    total, sold = sum(b["total"] for b in bs), sum(b["sold"] for b in bs)
    remaining = max(total - sold, 0)
    sales, price = series(bs)
    window = [i for i in (9, 10, 11) if any(sales[j] for j in range(i + 1))]
    n = len(window) if window and sales[window[0]] else 3
    pace = round(sum(sales[9:12]) / n, 1) if n else 0
    end = max((b["end"] or "") for b in bs)
    to_end = months_until(end) if end else None
    sellout = round(remaining / pace) if pace else None
    p0 = wavg(bs, [0, 1, 2]); p1 = wavg(bs, [9, 10, 11])
    contr = [sum(b["contr"][i] for b in bs if len(b["contr"]) == 12) for i in range(12)]
    return {
        "flats": total, "sold": sold, "sold_pct": round(100 * sold / total, 1) if total else None, "remaining": remaining,
        "sales_12m": sum(sales), "jul": sales[9], "aug": sales[10], "sep": sales[11], "pace_per_month": pace,
        "sellout_months": sellout, "months_to_completion": to_end,
        "on_track": (sellout is not None and to_end is not None and sellout <= max(to_end, 1)),
        "price_m2_q3": p1, "price_m2_q4_2025": p0, "price_change_pct": round(100 * (p1 / p0 - 1), 1) if p0 and p1 else None,
        "bulk_months": [A.MONTHS[i] for i in range(12) if contr[i] and sales[i] / contr[i] >= 5 and sales[i] >= 10],
        "completion": end, "published": min((b["pub"] or "99") for b in bs),
        "monthly_sales": sales, "monthly_price_m2": price,
    }


inside_b = {}
for bid, b in A.buildings.items():
    ok, how = in_mkad(b)
    if ok:
        inside_b[bid] = dict(b, mkad_basis=how)

complexes = {}
for key, bs in A.complexes.items():
    bs_in = [b for b in bs if b["id"] in inside_b]
    if not bs_in:
        continue
    b0 = bs_in[0]
    complexes[key] = {"name": b0["name"], "developer": b0["dev"], "class": "/".join(sorted({b["cls"] or "" for b in bs_in})),
                      "district": b0["district"], "metro": b0["metro"], "lat": b0["xy"][0], "lon": b0["xy"][1],
                      "building_ids": [b["id"] for b in bs_in], **metrics(bs_in)}

M = A.MONTHS
with open(HERE / "buildings.csv", "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["id", "name", "developer", "class", "district", "metro", "lat", "lon", "mkad_basis", "flats", "sold", "sold_pct",
                "completion", "published", "url"] + [f"sales_{m}" for m in M] + [f"price_m2_{m}" for m in M])
    for b in sorted(inside_b.values(), key=lambda b: (b["dev"] or "", b["name"] or "")):
        w.writerow([b["id"], b["name"], b["dev"], b["cls"], b["district"], b["metro"], b["xy"][0], b["xy"][1], b["mkad_basis"],
                    b["total"], b["sold"], round(100 * b["sold"] / b["total"], 1) if b["total"] else "", b["end"], b["pub"],
                    f"https://наш.дом.рф/сервисы/каталог-новостроек/объект/{b['id']}"]
                   + (b["real"] if len(b["real"]) == 12 else [""] * 12) + (b["price"] if len(b["price"]) == 12 else [""] * 12))

cols = ["name", "developer", "class", "district", "metro", "lat", "lon", "flats", "sold", "sold_pct", "remaining", "sales_12m",
        "jul", "aug", "sep", "pace_per_month", "sellout_months", "months_to_completion", "on_track", "price_m2_q4_2025",
        "price_m2_q3", "price_change_pct", "bulk_months", "completion", "published"]
with open(HERE / "complexes.csv", "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(cols + ["building_ids"] + [f"sales_{m}" for m in M] + [f"price_m2_{m}" for m in M])
    for c in sorted(complexes.values(), key=lambda c: -(c["sales_12m"] or 0)):
        w.writerow([" ".join(c[k]) if k == "bulk_months" else c[k] for k in cols] + [" ".join(map(str, c["building_ids"]))]
                   + c["monthly_sales"] + [p or "" for p in c["monthly_price_m2"]])

market = {}
for cls in sorted({b["cls"] for b in inside_b.values() if b["cls"]}):
    bs = [b for b in inside_b.values() if b["cls"] == cls]
    s, p = series(bs)
    market[cls] = {"buildings": len(bs), "flats": sum(b["total"] for b in bs), "sold": sum(b["sold"] for b in bs),
                   "monthly_sales": s, "monthly_price_m2": p}
queued = sum(len(v) for v in st["searches"].values())
out = {"collected_at": TODAY.isoformat(), "source": "ЕИСЖС наш.дом.рф, object pages (__NEXT_DATA__ + sales_agg)", "months": M,
       "coverage": {"buildings_collected_total": len(st["objects"]), "queued_total": queued,
                    "buildings_inside_mkad": len(inside_b), "complexes_inside_mkad": len(complexes)},
       "market_by_class": market, "complexes": list(complexes.values())}
(HERE / "scan.json").write_text(json.dumps(out, ensure_ascii=False, indent=1, default=str))

print(f"collected {len(st['objects'])}/{queued} | inside MKAD: {len(inside_b)} buildings, {len(complexes)} ЖК")
print("by class:", Counter(b["cls"] for b in inside_b.values()))
for cls, v in market.items():
    print(f"{cls:<10} buildings {v['buildings']:>4} flats {v['flats']:>6} sold {v['sold']:>6} | Jul/Aug/Sep {v['monthly_sales'][-3:]}")
