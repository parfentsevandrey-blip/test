"""Сопоставимые объекты и ранжирование кандидатов на флип.

python3 comps.py DATA_DIR [OUT_JSON]
"""
import json
import re
import statistics as st
import sys
from pathlib import Path

DATA = Path(sys.argv[1])
OUT = Path(sys.argv[2]) if len(sys.argv) > 2 else None
rows, seen = [], set()
for f in sorted(DATA.glob("listings-*.jsonl")):
    for line in f.read_text().splitlines():
        if not line.strip():
            continue
        r = json.loads(line)
        if r["url"] in seen:
            continue
        seen.add(r["url"])
        r["city"] = re.search(r"fundainbusiness\.nl/[a-z]+/([a-z0-9\-]+)/object-", r["url"]).group(1)
        rows.append(r)

TERRITORY_CITIES = {
    "eindhoven": {"eindhoven", "veldhoven", "son-en-breugel", "best"},
    "ai-schiphol": {"hoofddorp", "nieuw-vennep", "schiphol-rijk", "schiphol", "badhoevedorp", "lijnden",
                    "oude-meer", "rijsenhout", "aalsmeer"},
    "ai-amsterdam": {"amsterdam", "diemen"},
    "agriport": {"middenmeer", "hoorn-nh", "wieringerwerf"},
    "nijmegen": {"nijmegen", "wijchen"},
    "rotterdam": {"rotterdam"},
}
CITY_TERR = {c: t for t, cs in TERRITORY_CITIES.items() for c in cs}
for r in rows:
    r["terr"] = CITY_TERR.get(r["city"])

LET = re.compile(r"verhuurd aan|is verhuurd|volledig verhuurd|lopende huurovereenkomst|huurovereenkomst (?:loopt|tot|met)|"
                 r"huurinkomsten|huuropbrengst|aanvangsrendement|huurder betaalt|zittende huurder")
INDUSTRIAL = re.compile(r"bedrijfsruimte|bedrijfshal|bedrijfsunit|bedrijfspand|magazijn|loods|werkplaats|productiehal|opslag")
NOT_IND = re.compile(r"winkel|woning|appartement|horeca|restaurant|kantoorgebouw|zorg|hotel")
AMOUNT = re.compile(r"€\s*([\d.]{4,})(?:,[\d-]+)?\s*(?:,-\s*)?(?:excl\.?\s*btw\s*)?(?:per jaar|p\.?j\.?|/\s*jaar|op jaarbasis|jaarlijks)")
BAR = re.compile(r"(?:bruto aanvangsrendement|\bbar\b|rendement van|aanvangsrendement van)[^\d]{0,30}(\d{1,2}[.,]\d{1,2})\s*%")


def money(s):
    return int(s.replace(".", "")) if s.replace(".", "").isdigit() else None


def let_rent(r):
    """Годовая аренда сданного объекта: из таблицы, из текста или через BAR."""
    price = r.get("price")
    cands = []
    if r.get("rent_year"):
        cands.append(r["rent_year"])
    low = r.get("desc", "").lower()
    for m in AMOUNT.finditer(low):
        v = money(m.group(1))
        if v:
            cands.append(v)
    if r.get("rent_in_text"):
        cands.append(r["rent_in_text"])
    for v in cands:
        if price and 0.03 <= v / price <= 0.13:
            return v, "аренда"
    m = BAR.search(low)
    if m and price:
        bar = float(m.group(1).replace(",", "."))
        if 3 <= bar <= 13:
            return round(price * bar / 100), "BAR"
    return None, None


def rent_m2(r):
    if r.get("rent_m2") and 20 <= r["rent_m2"] <= 300:
        return r["rent_m2"]
    if r.get("rent_year") and r.get("area"):
        v = r["rent_year"] / r["area"]
        return v if 20 <= v <= 300 else None
    return None


# --- сопоставимые: аренда, продажа свободных, сданные (доходность) -------------
rent_comps, sale_comps, inv_comps = [], [], []
for r in rows:
    if not r["terr"]:
        continue
    low = r.get("desc", "").lower()
    if r["kind"] == "bedrijfshal" and r["deal"] == "huur" and r.get("area") and rent_m2(r):
        rent_comps.append({"terr": r["terr"], "city": r["city"], "area": r["area"], "rent_m2": round(rent_m2(r), 1),
                           "title": r["title"], "url": r["url"], "year": r.get("year") or r.get("period")})
    if r["kind"] == "bedrijfshal" and r["deal"] == "koop" and r.get("price") and r.get("area") \
            and not LET.search(low) and 150 <= r["area"] <= 6000 and 300 <= r["price"] / r["area"] <= 6000:
        sale_comps.append({"terr": r["terr"], "city": r["city"], "area": r["area"], "price": r["price"],
                           "pm2": round(r["price"] / r["area"]), "status": r.get("status"), "title": r["title"],
                           "url": r["url"], "year": r.get("year") or r.get("period"), "rent_year": r.get("rent_year")})
    is_let = r["kind"] == "belegging" or LET.search(low)
    if is_let and r.get("price") and INDUSTRIAL.search(low) and not NOT_IND.search(low[:500]):
        rent, how = let_rent(r)
        if rent:
            inv_comps.append({"terr": r["terr"], "city": r["city"], "area": r.get("area"), "price": r["price"],
                              "rent": rent, "giy": round(rent / r["price"] * 100, 2), "how": how,
                              "status": r.get("status"), "title": r["title"], "url": r["url"]})


def q(xs):
    xs = sorted(xs)
    if not xs:
        return None
    if len(xs) < 4:
        return {"n": len(xs), "median": round(st.median(xs), 2)}
    a, b, c = st.quantiles(xs, n=4)
    return {"n": len(xs), "p25": round(a, 2), "median": round(b, 2), "p75": round(c, 2)}


summary = {}
for terr in TERRITORY_CITIES:
    summary[terr] = {
        "rent_m2": q([c["rent_m2"] for c in rent_comps if c["terr"] == terr]),
        "sale_pm2": q([c["pm2"] for c in sale_comps if c["terr"] == terr]),
        "inv_giy": q([c["giy"] for c in inv_comps if c["terr"] == terr]),
    }
    print(terr, json.dumps(summary[terr], ensure_ascii=False))

print("\nсданные склады на продажу (доходность):")
for c in sorted(inv_comps, key=lambda c: (c["terr"], c["giy"])):
    print(f"  {c['terr']:12} {c['giy']:5.2f}% {c['how']:6} € {c['price']:>9,} аренда € {c['rent']:>7,}  {c['area']}  {c['status']}  {c['url'][-55:]}")

# --- кандидаты: рыночная аренда по площадям ±50 % того же размера ------------------
bad = re.compile(r"onder bod|onder optie|verkocht|verhuurd", re.I)
registry = json.loads(Path(__file__).resolve().parents[2] / "data" / "registry.json".read_text())
shown_slugs = [o["slug"] for o in registry["objects"]]


def shown(url):
    tail = re.search(r"object-\d+-([a-z0-9\-]+)/?$", url.rstrip("/") + "/").group(1).strip("-")
    return any(s.startswith(tail) for s in shown_slugs)


ranked = []
for c in sale_comps:
    if c["price"] > 2_000_000 or c["area"] < 400 or (c["status"] and bad.search(c["status"])) or shown(c["url"]):
        continue
    if c["year"] and re.search(r"202[6-9]|aanbouw", str(c["year"])):
        continue      # новостройка продаётся v.o.n., под флип не годится
    near = [x["rent_m2"] for x in rent_comps if x["terr"] == c["terr"] and 0.4 * c["area"] <= x["area"] <= 2.5 * c["area"]]
    if len(near) < 4:
        continue
    mrent = st.median(near) * c["area"]
    inv = summary[c["terr"]]["inv_giy"]
    exit_bar = inv["median"] / 100 if inv else None
    if not exit_bar:
        continue
    value_let = mrent / exit_bar
    ranked.append({**c, "mkt_rent_m2": round(st.median(near), 1), "n_rent": len(near), "mkt_rent": round(mrent),
                   "giy_on_price": round(mrent / c["price"] * 100, 2), "exit_bar": round(exit_bar * 100, 2),
                   "value_let": round(value_let), "uplift": round(value_let / c["price"] - 1, 3)})

print("\nкандидаты по запасу «стоимость после сдачи / цена покупки»:")
for c in sorted(ranked, key=lambda c: -c["uplift"]):
    print(f"  {c['uplift']*100:6.1f}%  {c['terr']:12} € {c['price']:>9,} {c['area']:>5} м² €{c['pm2']}/м²  "
          f"аренда ≈ €{c['mkt_rent_m2']}/м² (n={c['n_rent']}) → {c['giy_on_price']}%  выход {c['exit_bar']}%  {c['year']}  {c['url'][-60:]}")

if OUT:
    OUT.write_text(json.dumps({"summary": summary, "rent": rent_comps, "sale": sale_comps, "inv": inv_comps,
                               "ranked": ranked}, ensure_ascii=False, indent=1))
