"""Отбор под флип: склады на продажу внутри выбранных территорий, одна модель.

python3 -I screen3.py WORK_DIR
Читает WORK/data/listings-*.jsonl, WORK/data/current-sale.json, WORK/comps.json, WORK/located.json;
недостающие адреса привязывает через reportgen.selection; пишет WORK/screen3.json.
"""
import json
import re
import statistics as st
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(REPO))
sys.path.append("/root/.local/lib/python3.11/site-packages")
import flipmodel as F  # noqa: E402
from kernweg import BASE  # noqa: E402

WORK = Path(sys.argv[1])
EXIT_ALMERE = .0725
EXIT = {"ai-amsterdam": .07, "ai-schiphol": .0725, "eindhoven": .0775, "nijmegen": .085,
        "agriport": .0825, "rotterdam": .075}
LET = re.compile(r"verhuurd aan|is verhuurd|volledig verhuurd|lopende huurovereenkomst|huurovereenkomst (?:loopt|tot|met)|"
                 r"huurinkomsten|huuropbrengst|aanvangsrendement|huurder betaalt|zittende huurder|huurstroom")
BAD = re.compile(r"onder bod|onder optie|verkocht", re.I)

rows = {}
for f in sorted((WORK / "data").glob("listings-*.jsonl")):
    for line in f.read_text().splitlines():
        if line.strip():
            r = json.loads(line)
            if r["kind"] == "bedrijfshal" and r["deal"] == "koop":
                rows[r["url"]] = r
current = json.loads((WORK / "data" / "current-sale.json").read_text())
comps = json.loads((WORK / "comps.json").read_text())
# аренда: сопоставимые из выгрузки, включая Алмере (его нет в comps.json)
RENT = list(comps["rent"])
for f in sorted((WORK / "data").glob("listings-*.jsonl")):
    for line in f.read_text().splitlines():
        if not line.strip():
            continue
        x = json.loads(line)
        if "/almere/" in x["url"] and x["deal"] == "huur" and x["kind"] == "bedrijfshal" and x.get("area"):
            v = x.get("rent_m2") or (x["rent_year"] / x["area"] if x.get("rent_year") else None)
            if v and 25 <= v <= 300:
                RENT.append({"terr": "ai-amsterdam", "city": "almere", "area": x["area"], "rent_m2": v,
                             "year": x.get("year") or x.get("period")})
located = json.loads((WORK / "located.json").read_text())
registry = json.loads((REPO / "data" / "registry.json").read_text())
slugs = [o["slug"].replace("-", "") for o in registry["objects"]]


def shown(url):
    tail = re.search(r"object-\d+-([a-z0-9\-]+)/?$", url).group(1).replace("-", "")
    return any(s.startswith(tail) for s in slugs)


def address(r):
    m = re.search(r"te koop(?: en te huur)?: (.+?) \d{4} ?[A-Z]{2} (.+?) \[", r["title"])
    return f"{m.group(1)}, {m.group(2)}" if m else r["title"]


from reportgen import selection  # noqa: E402

out = []
for url, terr0 in current.items():
    r = rows.get(url)
    if not r or not r.get("price") or not r.get("area"):
        continue
    if r["price"] > 2_000_000 or r["area"] < 150:
        continue
    if r.get("status") and BAD.search(r["status"]):
        continue
    year = str(r.get("year") or r.get("period") or "")
    low = r.get("desc", "").lower()
    # новое помещение: покупка и продажа с НДС, без налога на передачу (ст. 15 WBR)
    new = bool(re.search(r"202[4-7]|aanbouw|na 2020", year.lower())) or "nieuwbouw" in low
    if new and not re.search(r"btw|v\.o\.n|vrij op naam", low + " " + (r.get("price_text") or "").lower()):
        new = False
    building = bool(re.search(r"aanbouw|2027", year.lower()))
    if LET.search(low) or shown(url):
        continue
    if url not in located:
        try:
            lat, lon, _ = selection.geocode(address(r))
            res = selection.locate(lat, lon, with_drive=False)
            located[url] = {"addr": address(r), "lat": lat, "lon": lon,
                            "terrs": [(t["key"], (t["zone"]["name"] + (" IN" if t["zone"]["inside"] else
                                                                      f" {t['zone']['metres']}m"))
                                       if t.get("zone") else "whole") for t in res["territories"]]}
        except Exception as exc:                   # noqa: BLE001
            print("geocode", address(r), exc)
            continue
    loc = located[url]
    if not loc["terrs"]:
        continue
    terr = loc["terrs"][0][0]
    city = re.search(r"fundainbusiness\.nl/[a-z]+/([a-z0-9\-]+)/object-", url).group(1)
    pool = [c for c in RENT if 0.4 * r["area"] <= c["area"] <= 2.5 * r["area"]]
    if new:
        fresh = [c for c in pool if re.search(r"20(1[5-9]|2\d)|na 2020", str(c.get("year") or "").lower())]
        pool = fresh if len([c for c in fresh if c["city"] == city]) >= 4 else pool
    same = [c["rent_m2"] for c in pool if c["city"] == city]
    near = same if len(same) >= 4 else [c["rent_m2"] for c in pool if c["terr"] == terr]
    if len(near) < 4:
        continue
    rm2 = 0.95 * st.median(near)
    p = dict(BASE, price=r["price"], area=r["area"], rent_m2=rm2,
             exit_bar=EXIT_ALMERE if city == "almere" else EXIT[terr])
    if new:
        p.update(ovb=0.0, capex_m2=(30 if re.search(r"turn-?key opgeleverd|instapklaar|volledig afgebouwd", low) and "casco" not in low else 200), capex_vat=0.0, notary=0, lease_month=3, rent_free=2, sale_month=8)
    be = F.solve("price", 0.0, "profit", 0.05 * r["price"], 4 * r["price"], p)
    m10 = F.solve("price", 0.10, "margin", 0.05 * r["price"], 4 * r["price"], p)
    if be is None or m10 is None:
        print("no root", url)
        continue
    out.append({"url": url, "addr": loc["addr"], "terr": terr, "zone": loc["terrs"][0][1], "price": r["price"],
                "area": r["area"], "pm2": round(r["price"] / r["area"]), "year": year, "rent_m2": rm2,
                "n": len(near), "bar": p["exit_bar"], "giy": rm2 * r["area"] / r["price"],
                "profit": F.run(p)["profit"], "disc_be": be / r["price"] - 1, "disc_m10": m10 / r["price"] - 1,
                "status": r.get("status"), "height": r.get("height"), "new": new, "building": building,
                "acceptance": r.get("acceptance")})
(WORK / "located.json").write_text(json.dumps(located, ensure_ascii=False, indent=1))
(WORK / "screen3.json").write_text(json.dumps(out, ensure_ascii=False, indent=1))
for c in sorted(out, key=lambda c: -c["disc_be"])[:45]:
    print(f"{'НОВ' if c['new'] else '   '}{'*' if c['building'] else ' '} {c['disc_be'] * 100:6.1f}% {c['disc_m10'] * 100:6.1f}%  {c['terr']:12} {c['zone'][:26]:26} €{c['price']:>9,} "
          f"{c['area']:>5} м² €{c['pm2']:>5} аренда €{c['rent_m2']:5.1f} ({c['n']:2}) {c['giy'] * 100:4.1f}% BAR {c['bar'] * 100:.2f} "
          f"{c['year'][:9]:9} {c['addr'][:40]}")
