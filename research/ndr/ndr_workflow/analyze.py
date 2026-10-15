"""Build competitor tables from state.json (data collected by ndr_workflow from наш.дом.рф pages)."""
import csv
import json
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
st = json.loads((HERE / "state.json").read_text())

TARGETS = {"Ридж": 73047, "Сезар Будущее": 72174, "Сезар Силика": 73110, "Стоун Блик": 72816,
           "26 ПаркВью": 72885, "С5": 72298, "Сикрет Гарден": 72374}
RADIUS = {"Ридж": 3.5, "26 ПаркВью": 3.5, "Сикрет Гарден": 3.5}
SAME_SEGMENT = {"Элитный", "Премиум", "Бизнес"}


def km(a, b):
    return math.hypot((a[0] - b[0]) * 111.2, (a[1] - b[1]) * 111.2 * math.cos(math.radians(55.75)))


rows = {}
for href, o in st["objects"].items():
    n = o.get("next")
    if not n:
        continue
    res = (n.get("monthly") or {}).get("residential") or []
    months = [r["reportMonth"][3:] for r in res]
    real = [r["realised"] for r in res]
    contr = [r["contracted"] for r in res]
    area = [r["soldArea"] for r in res]
    price = [r["priceAvg"] for r in res]
    flats = int(n.get("flatsCount") or 0)
    sold_pct = float(n.get("soldOutPercent") or 0) * 100
    bulk = [m for m, r, c in zip(months, real, contr) if c and r / c >= 5 and r >= 10]
    last3 = sum(real[-3:])
    sold_area3, money3 = sum(area[-3:]), sum(a * p for a, p in zip(area[-3:], price[-3:]))
    rows[int(n["id"])] = {
        "id": int(n["id"]), "name": n.get("name"), "developer": n.get("developer"), "class": n.get("buildingClass"),
        "coords": tuple(float(c) for c in (n.get("coordinates") or [0, 0])), "flats": flats,
        "sold_pct_area": round(sold_pct, 1), "published": n.get("publicationDate"), "end": n.get("dateEnd"),
        "price_m2": n.get("averagePrice"), "months": months, "realised": real, "contracted": contr,
        "last3": last3, "pace3": round(last3 / 3, 1), "year": sum(real),
        "price3": round(money3 / sold_area3) if sold_area3 else None, "bulk_months": bulk,
        "metro": (n.get("metro") or {}).get("name"), "district": n.get("address"),
    }


def table(target, only_segment=True, file=sys.stdout):
    t = rows.get(TARGETS[target])
    if not t:
        print(f"## {target}: not collected yet", file=file)
        return []
    R = RADIUS.get(target, 2.5)
    comp = [r for r in rows.values() if r["id"] != t["id"] and km(t["coords"], r["coords"]) <= R
            and (not only_segment or r["class"] in SAME_SEGMENT)]
    comp.sort(key=lambda r: -r["pace3"])
    out = [t] + comp
    print(f"\n## {target} ({t['class']}, {t['district']}, м. {t['metro']}) — competitors within {R} km, classes {sorted(SAME_SEGMENT)}", file=file)
    print(f"{'object':<46} {'class':<8} {'km':>4} {'flats':>5} {'sold%':>5} {'year':>4} {'3m':>4} {'/mo':>5} {'₽/m² 3m':>9}  monthly (last 6) {'bulk':>6}", file=file)
    for r in out:
        d = 0 if r is t else km(t["coords"], r["coords"])
        tag = "★ " if r is t else "  "
        print(f"{tag}{(r['name'] or '')[:30]+' /'+(r['developer'] or '')[:12]:<44} {(r['class'] or '')[:8]:<8} {d:4.1f} {r['flats']:>5} "
              f"{r['sold_pct_area']:>5} {r['year']:>4} {r['last3']:>4} {r['pace3']:>5} {str(r['price3'] or '-'):>9}  "
              f"{r['realised'][-6:]} {','.join(r['bulk_months']) or '':>6}", file=file)
    return out


if __name__ == "__main__":
    print(f"objects with data: {len(rows)} / queued {sum(len(v) for v in st['searches'].values())}")
    from collections import Counter
    print("classes:", Counter(r["class"] for r in rows.values()))
    with open(HERE / "competitors.txt", "w") as f:
        for tg in TARGETS:
            table(tg, file=f)
            table(tg)
    with open(HERE / "objects.csv", "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["id", "name", "developer", "class", "lat", "lon", "district", "metro", "flats", "sold_pct_area",
                    "published", "end", "price_m2", "year_realised", "last3", "pace3", "price3", "bulk_months"]
                   + [f"m_{m}" for m in (next(iter(rows.values()))["months"] if rows else [])])
        for r in rows.values():
            w.writerow([r["id"], r["name"], r["developer"], r["class"], *r["coords"], r["district"], r["metro"], r["flats"],
                        r["sold_pct_area"], r["published"], r["end"], r["price_m2"], r["year"], r["last3"], r["pace3"],
                        r["price3"], " ".join(r["bulk_months"])] + r["realised"])
