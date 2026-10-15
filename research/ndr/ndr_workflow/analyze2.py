"""Complex-level competitor analysis from наш.дом.рф data (state.json). Writes report_data.json + prints tables."""
import json
import math
import re
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
st = json.loads((HERE / "state.json").read_text())

TARGETS = {"Ридж": [73047], "Сезар Будущее": [72174], "Сезар Силика": [73110], "Стоун Блик": [72816],
           "26 ПаркВью": [72885], "С5": [72298, 72297], "Сикрет Гарден": [72374]}
RADIUS = {"Ридж": 3.5, "26 ПаркВью": 3.5, "Сикрет Гарден": 3.5, "С5": 3.0}
SEG = {"Элитный", "Бизнес"}


def km(a, b):
    return math.hypot((a[0] - b[0]) * 111.2, (a[1] - b[1]) * 111.2 * math.cos(math.radians(55.75)))


def norm(name):
    n = (name or "").lower()
    n = re.sub(r"жилой комплекс|жилой квартал|жилой дом|многофункциональный|комплекс|жк|«|»|\"|'|корпус.*|очередь.*|этап.*", " ", n)
    n = re.sub(r"\s+", " ", n).strip()
    return n.split(" и/или ")[0][:28]


buildings = {}
for href, o in st["objects"].items():
    n = o.get("next") or {}
    agg = {}
    for b in o.get("api", []):
        if b["url"].endswith("/sales_agg"):
            agg = (b.get("json") or {}).get("data") or {}
    fl = agg.get("apartmentsAggData") or {}
    res = (n.get("monthly") or {}).get("residential") or []
    buildings[int(n["id"])] = {
        "id": int(n["id"]), "name": n.get("name"), "dev": n.get("developer"), "cls": n.get("buildingClass"),
        "xy": tuple(float(c) for c in (n.get("coordinates") or [0, 0])), "metro": (n.get("metro") or {}).get("name"),
        "district": n.get("address"), "end": n.get("dateEnd"), "pub": n.get("publicationDate"),
        # sales_agg counter (units); if the page's request wasn't captured, estimate from the page's sold share
        "total": fl.get("total") or int(n.get("flatsCount") or 0),
        "sold": fl.get("realised") if fl.get("realised") is not None else round(float(n.get("soldOutPercent") or 0) * int(n.get("flatsCount") or 0)),
        "not_started": bool(agg.get("saleNotStartedFlg")),
        "months": [r["reportMonth"][3:] for r in res], "real": [r["realised"] for r in res],
        "contr": [r["contracted"] for r in res], "area": [r["soldArea"] for r in res], "price": [r["priceAvg"] for r in res],
    }

MONTHS = next(b["months"] for b in buildings.values() if b["months"])

# group buildings into complexes by developer + normalized name
complexes = defaultdict(list)
for b in buildings.values():
    complexes[(b["dev"], norm(b["name"]))].append(b)


def summarize(bs):
    real = [sum(b["real"][i] if i < len(b["real"]) else 0 for b in bs) for i in range(12)]
    contr = [sum(b["contr"][i] if i < len(b["contr"]) else 0 for b in bs) for i in range(12)]
    area3 = sum(sum(b["area"][-3:]) for b in bs)
    money3 = sum(sum(a * p for a, p in zip(b["area"][-3:], b["price"][-3:])) for b in bs)
    total, sold = sum(b["total"] for b in bs), sum(b["sold"] for b in bs)
    remaining = max(total - sold, 0)
    pace3 = sum(real[-3:]) / 3
    bulk = [MONTHS[i] for i in range(12) if contr[i] and real[i] / contr[i] >= 5 and real[i] >= 10]
    return {"name": bs[0]["name"], "dev": bs[0]["dev"], "cls": sorted({b["cls"] for b in bs}), "ids": [b["id"] for b in bs],
            "xy": bs[0]["xy"], "metro": bs[0]["metro"], "district": bs[0]["district"], "end": max((b["end"] or "") for b in bs),
            "pub": min((b["pub"] or "99") for b in bs), "total": total, "sold": sold,
            "sold_pct": round(100 * sold / total, 1) if total else None, "remaining": remaining,
            "jul": real[-3], "aug": real[-2], "sep": real[-1], "q3": sum(real[-3:]), "pace3": round(pace3, 1),
            "y12": sum(real), "absorb_pct": round(100 * pace3 / remaining, 1) if remaining else None,
            "price3": round(money3 / area3) if area3 else None, "bulk": bulk, "monthly": real}


summ = {k: summarize(v) for k, v in complexes.items()}
target_keys = {}
for t, ids in TARGETS.items():
    for k, bs in complexes.items():
        if any(b["id"] in ids for b in bs):
            target_keys[t] = k

report = {"months": MONTHS, "targets": {}}
for t, ids in TARGETS.items():
    tk = target_keys[t]
    tc = summ[tk]
    R = RADIUS.get(t, 2.5)
    comp = []
    for k, s in summ.items():
        if k == tk or not (set(s["cls"]) & SEG):
            continue
        d = min(km(tc["xy"], b["xy"]) for b in complexes[k])
        if d <= R:
            comp.append(dict(s, km=round(d, 2)))
    comp.sort(key=lambda s: -(s["absorb_pct"] or 0))
    report["targets"][t] = {"self": tc, "radius_km": R, "competitors": comp}
    print(f"\n### {t}: {tc['cls']} {tc['district']} м.{tc['metro']} | flats {tc['total']} sold {tc['sold']} ({tc['sold_pct']}%) "
          f"| Jul/Aug/Sep {tc['jul']}/{tc['aug']}/{tc['sep']} | absorb {tc['absorb_pct']}%/mo | ₽/m² {tc['price3']} | competitors {len(comp)} in {R} km")
    print(f"{'complex':<34}{'dev':<16}{'cls':<9}{'km':>4}{'flats':>6}{'sold%':>6}{'Jul':>4}{'Aug':>4}{'Sep':>4}{'/mo':>6}{'abs%':>6}{'₽/m²':>8} bulk")
    for s in comp:
        print(f"{(s['name'] or '')[:33]:<34}{(s['dev'] or '')[:15]:<16}{'/'.join(c[:4] for c in s['cls']):<9}{s['km']:>4}{s['total']:>6}"
              f"{str(s['sold_pct']):>6}{s['jul']:>4}{s['aug']:>4}{s['sep']:>4}{s['pace3']:>6}{str(s['absorb_pct']):>6}{str(s['price3'] or '-'):>8} {','.join(s['bulk'])}")

# market tempo across all collected elite / business complexes
tempo = {}
for cls in ("Элитный", "Бизнес"):
    tempo[cls] = [sum(b["real"][i] for b in buildings.values() if b["cls"] == cls and len(b["real"]) == 12) for i in range(12)]
report["tempo"] = tempo
report["all_complexes"] = list(summ.values())
print("\nmarket tempo (all collected buildings), months:", MONTHS)
for c, v in tempo.items():
    print(f"{c:<8}", v)
(HERE / "report_data.json").write_text(json.dumps(report, ensure_ascii=False, indent=1, default=str))
