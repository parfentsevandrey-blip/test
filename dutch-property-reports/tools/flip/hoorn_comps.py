"""Сопоставимые объекты для Kernweg 1: Хорн и Западная Фрисландия.

python3 -I hoorn_comps.py DATA_DIR OUT_JSON
"""
import glob
import html
import json
import re
import sys
from pathlib import Path

DATA = Path(sys.argv[1])
TOWNS = {"hoorn-nh": "Хорн", "zwaag": "Зваг", "enkhuizen": "Энкхёйзен", "wognum": "Вогнюм",
         "medemblik": "Медемблик", "purmerend": "Пюрмеренд", "heerhugowaard": "Херхюговард",
         "alkmaar": "Алкмар", "wieringerwerf": "Вирингерверф", "middenmeer": "Мидденмер"}
LET = re.compile(r"verhuurd aan|is verhuurd|volledig verhuurd|lopende huurovereenkomst|huurovereenkomst (?:loopt|tot|met)|"
                 r"huurinkomsten|huuropbrengst|aanvangsrendement|huurder betaalt|zittende huurder|huurstroom|huurders")
INDUSTRIAL = re.compile(r"bedrijfsruimte|bedrijfshal|bedrijfsunit|bedrijfspand|bedrijfsobject|magazijn|loods|werkplaats|productiehal|opslag")
AMOUNT = re.compile(r"(?:huurstroom|huuropbrengst|huurinkomsten|jaarhuur|huurprijs|totale huur)[^€]{0,60}€\s*([\d.]{4,})")
BAR = re.compile(r"(?:bruto aanvangsrendement|\bbar\b|rendement van|aanvangsrendement van)[^\d]{0,30}(\d{1,2}[.,]\d{1,2})\s*%")

rows, seen = [], set()
for f in sorted(DATA.glob("listings-*.jsonl")):
    for line in f.read_text().splitlines():
        if not line.strip():
            continue
        r = json.loads(line)
        m = re.search(r"fundainbusiness\.nl/([a-z]+)/([a-z0-9\-]+)/object-", r["url"])
        r["kind_url"], r["city"] = m.group(1), m.group(2)
        key = (r["url"], r["deal"])
        if r["city"] not in TOWNS or key in seen:
            continue
        seen.add(key)
        rows.append(r)


def since(url):
    name = re.sub(r"\W+", "-", url)[-90:]
    files = glob.glob(str(DATA / "raw" / f"{name}.html"))
    if not files:
        return None, None
    t = Path(files[0]).read_text(encoding="utf-8")
    s = re.search(r"sinds'\s*:\s*'([^']+)'", t)
    x = re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", " ", t)))
    o = re.search(r"Oorspronkelijke vraagprijs € ([\d.]+)", x)
    return (s.group(1) if s else None), (int(o.group(1).replace(".", "")) if o else None)


def address(r):
    m = re.search(r": (.+?) \d{4} ?[A-Z]{2} ", r["title"])
    return m.group(1) if m else r["title"][:40]


def year(r):
    return r.get("year") or r.get("period")


rent, sale, inv = [], [], []
for r in rows:
    low = r.get("desc", "").lower()
    area = r.get("area")
    if r["deal"] == "huur" and r["kind"] == "bedrijfshal" and area:
        v = r.get("rent_m2") or (r["rent_year"] / area if r.get("rent_year") else None)
        if v and 25 <= v <= 250:
            rent.append({"town": TOWNS[r["city"]], "addr": address(r), "area": area, "rent_m2": round(v, 1),
                         "year": year(r), "url": r["url"], "since": since(r["url"])[0]})
    if r["deal"] == "koop" and r["kind"] == "bedrijfshal" and r.get("price") and area and area >= 400 \
            and not LET.search(low):
        s, orig = since(r["url"])
        sale.append({"town": TOWNS[r["city"]], "addr": address(r), "area": area, "price": r["price"],
                     "pm2": round(r["price"] / area), "status": r.get("status"), "year": year(r),
                     "url": r["url"], "since": s, "orig": orig, "plot": r.get("plot")})
    if r["deal"] == "koop" and r.get("price") and (r["kind"] == "belegging" or LET.search(low)) \
            and INDUSTRIAL.search(low):
        cands = [r.get("rent_year"), r.get("rent_in_text")] + [int(m.group(1).replace(".", "")) for m in AMOUNT.finditer(low)]
        val = next((c for c in cands if c and 0.04 <= c / r["price"] <= 0.13), None)
        how = "аренда"
        if not val:
            m = BAR.search(low)
            if m and 4 <= float(m.group(1).replace(",", ".")) <= 13:
                val, how = round(r["price"] * float(m.group(1).replace(",", ".")) / 100), "BAR"
        if val:
            s, orig = since(r["url"])
            inv.append({"town": TOWNS[r["city"]], "addr": address(r), "area": area, "price": r["price"],
                        "rent": val, "giy": round(val / r["price"] * 100, 2), "how": how, "year": year(r),
                        "status": r.get("status"), "url": r["url"], "since": s})

out = {"rent": sorted(rent, key=lambda c: -c["area"]), "sale": sorted(sale, key=lambda c: c["pm2"]),
       "inv": sorted(inv, key=lambda c: c["giy"])}
Path(sys.argv[2]).write_text(json.dumps(out, ensure_ascii=False, indent=1))
for k in out:
    print("==", k, len(out[k]))
    for c in out[k]:
        print("  ", {kk: v for kk, v in c.items() if kk != "url"})
