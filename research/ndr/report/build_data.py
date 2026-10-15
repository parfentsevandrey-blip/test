"""Prepare the report data model from the наш.дом.рф collection (ndr_workflow/state.json)."""
import contextlib
import io
import json
import math
import sys
from datetime import date
from pathlib import Path

WF = Path(__file__).resolve().parent.parent / "ndr_workflow"
sys.path.insert(0, str(WF))
with contextlib.redirect_stdout(io.StringIO()):
    import analyze2 as A  # noqa: E402  (builds buildings / complexes / summ / target_keys)

TODAY = date(2026, 10, 15)
MONTHS = A.MONTHS                       # ['10.2025', ..., '09.2026']
EXTRA = {"Стоун Блик": [62125, 64096], "Ридж": [52455, 52456]}   # ERA, Dream Riva; Soyuz (from market ratings)
MAX_COMP = 12


def months_until(dmy):
    try:
        d, m, y = (int(x) for x in dmy.split("."))
        return max((y - TODAY.year) * 12 + (m - TODAY.month), 0)
    except Exception:
        return None


def complex_series(bs):
    sales = [sum(b["real"][i] for b in bs if len(b["real"]) == 12) for i in range(12)]
    price = []
    for i in range(12):
        a = sum(b["area"][i] for b in bs if len(b["area"]) == 12)
        money = sum(b["area"][i] * b["price"][i] for b in bs if len(b["area"]) == 12)
        price.append(round(money / a) if a else None)
    return sales, price


def wavg_price(bs, idx):
    a = sum(b["area"][i] for b in bs for i in idx if len(b["area"]) == 12)
    m = sum(b["area"][i] * b["price"][i] for b in bs for i in idx if len(b["area"]) == 12)
    return round(m / a) if a else None


def describe(key):
    bs = A.complexes[key]
    s = dict(A.summ[key])
    sales, price = complex_series(bs)
    s["sales12"], s["price12"] = sales, price
    s["sold12"] = sum(sales)
    p_start = wavg_price(bs, [0, 1, 2])
    first = next((i for i, v in enumerate(price) if v), None)
    if p_start is None and first is not None and first < 9:
        p_start = wavg_price(bs, [first, first + 1, first + 2])
    p_end = wavg_price(bs, [9, 10, 11])
    s["price_start"], s["price_end"] = p_start, p_end
    s["price_chg"] = round(100 * (p_end / p_start - 1), 1) if p_start and p_end else None
    s["price_start_label"] = "окт–дек 2025" if wavg_price(bs, [0, 1, 2]) else (f"с {MONTHS[first]}" if first is not None else "")
    # pace over the last 3 months, but for new launches only over the months since registrations began
    window = [i for i in (9, 10, 11) if any(sales[j] for j in range(i + 1))]
    n_months = len(window) if window and sales[window[0]] else 3
    pace = round(sum(sales[9:12]) / n_months, 1) if n_months else 0
    s["pace"], s["pace_months"] = pace, n_months
    s["sellout_months"] = round(s["remaining"] / pace) if pace else None
    s["to_completion"] = months_until(s["end"]) if s["end"] else None
    s["on_track"] = (s["sellout_months"] is not None and s["to_completion"] is not None
                     and s["sellout_months"] <= max(s["to_completion"], 1))
    s["lat"], s["lon"] = s["xy"]
    return s


model = {"months": MONTHS, "targets": {}, "market": {}, "elite_rank": []}
tids = {i for ids in A.TARGETS.values() for i in ids}
for t, ids in A.TARGETS.items():
    tk = A.target_keys[t]
    me = describe(tk)
    comps = []
    for c in A.report["targets"][t]["competitors"]:
        key = (c["dev"], A.norm(c["name"]))
        if key not in A.complexes or c["total"] <= 0:
            continue
        d = describe(key)
        d["km"] = c["km"]
        if d["sold12"] > 0 or "Элитный" in d["cls"]:
            comps.append(d)
    for ex in EXTRA.get(t, []):
        key = next((k for k, bs in A.complexes.items() if any(b["id"] == ex for b in bs)), None)
        if key and all(key != (c["dev"], A.norm(c["name"])) for c in comps):
            d = describe(key)
            d["km"] = round(A.km(me["xy"], d["xy"]), 2)
            d["outside_radius"] = True
            comps.append(d)
    comps.sort(key=lambda c: (c["sellout_months"] is None, c["sellout_months"] or 0))
    model["targets"][t] = {"self": me, "competitors": comps[:MAX_COMP], "radius_km": A.RADIUS.get(t, 2.5)}

# market tempo across all collected buildings, excluding the 7 targets
for cls in ("Элитный", "Бизнес"):
    sales = [0] * 12
    money, area = [0] * 12, [0] * 12
    for b in A.buildings.values():
        if b["cls"] != cls or b["id"] in tids or len(b["real"]) != 12:
            continue
        for i in range(12):
            sales[i] += b["real"][i]
            money[i] += b["area"][i] * b["price"][i]
            area[i] += b["area"][i]
    model["market"][cls] = {"sales": sales, "price": [round(m / a) if a else None for m, a in zip(money, area)]}
model["market"]["targets_sales"] = [sum(model["targets"][t]["self"]["sales12"][i] for t in A.TARGETS) for i in range(12)]

# elite ranking among all active complexes (12 months)
for k in A.complexes:
    d = describe(k)
    if "Элитный" in d["cls"] and d["pace3"] > 0 and d["total"] > 0:
        model["elite_rank"].append({x: d[x] for x in ("name", "dev", "metro", "total", "sold_pct", "pace3", "sellout_months",
                                                      "price_end", "price_chg", "sold12", "ids", "pace")})
model["elite_rank"].sort(key=lambda d: d["sellout_months"])
(Path(__file__).resolve().parent / "report_model.json").write_text(json.dumps(model, ensure_ascii=False, indent=1, default=str))

# console summary for writing conclusions
for t, v in model["targets"].items():
    s = v["self"]
    print(f"\n### {t}: sold {s['sold']}/{s['total']} ({s['sold_pct']}%), 12m {s['sold12']}, Jul-Sep {s['sales12'][-3:]}, pace {s['pace3']}, "
          f"pace* {s['pace']}/{s['pace_months']}m sellout {s['sellout_months']} mo vs completion {s['to_completion']} mo, price {s['price_start']}→{s['price_end']} ({s['price_chg']}%) {s['price_start_label']}")
    for c in v["competitors"]:
        print(f"  {(c['name'] or '')[:32]:<33}{'/'.join(c['cls'])[:12]:<13}{c['km']:>5}km sold {c['sold_pct']}% 12m {c['sold12']:>4} "
              f"pace {c['pace3']:>5} sellout {c['sellout_months']} vs {c['to_completion']} | price {c['price_start']}→{c['price_end']} ({c['price_chg']}%)"
              + (" [outside radius]" if c.get("outside_radius") else ""))
print("\nmarket elite", model["market"]["Элитный"]["sales"], "\nmarket biz", model["market"]["Бизнес"]["sales"],
      "\ntargets", model["market"]["targets_sales"])
print("elite price", model["market"]["Элитный"]["price"], "\nbiz price", model["market"]["Бизнес"]["price"])
