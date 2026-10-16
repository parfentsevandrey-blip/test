"""[П1] Selection of ЖК and buildings by the INSTRUCTION thresholds — monthly, at the building level.

Building = наш.дом.рф object (one permit; may hold one or a few корпуса), from ../moscow_scan/buildings.csv (scan of 16.10).
Criteria (INSTRUCTION П1):
  К1 class           business or premium («Бизнес», «Элитный» in наш.дом.рф)
  К2 stable pace     ≥ 1.5 % of the building's flats sold in at least 9 months of 12 (P25 of monthly sales ÷ flats)
  К3 to completion   6-18 months from today, by the building
  К4 developer       reliable (pilot: the five largest groups; manual flag in assignment_terms.csv)
  К5 assignment      allowed and not expensive (assignment_terms.csv, filled from contracts and developers' answers)
  К6 new supply      no large new supply within 2 km: flats in buildings whose sales have not started or started < 6 months
                     ago, within 2 km, compared with the building's unsold flats (large = more than 2x)
A building passes when К1-К3 and К6 hold and К4-К5 are not «нет» (unknown is shown as «уточнить»).
Outputs: p1_buildings.csv (every building of the pilot ЖК with each criterion), p1_projects.csv (per ЖК: buildings passing),
registry labels p1:<pass|fail> on the project and on the наш.дом.рф object.
"""
import csv
import json
import math
import sqlite3
import statistics
from datetime import date, datetime
from pathlib import Path

HERE = Path(__file__).resolve().parent
PACE_MIN, M_FROM, M_TO, RADIUS_KM, NEW_SUPPLY_X = 1.5, 6, 18, 2.0, 2.0
CLASSES = {"Бизнес", "Элитный"}
TERMS = HERE / "assignment_terms.csv"
TERMS_COLS = ["developer", "reliable", "assignment_allowed", "assignment_fee", "from_when", "source", "checked"]


def months_to(d, today):
    dt = datetime.strptime(d, "%d.%m.%Y").date()
    return round((dt - today).days / 30.44, 1)


def km(a, b):
    la1, lo1, la2, lo2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 6371 * 2 * math.asin(math.sqrt(h))


def terms():
    if not TERMS.exists():
        rg = sqlite3.connect(HERE / "registry.db")
        with TERMS.open("w", newline="") as f:
            w = csv.writer(f)
            w.writerow(TERMS_COLS)
            for (name,) in rg.execute("select name from developers order by id"):
                w.writerow([name, "да (топ-5 по объёму продаж, пилот)", "уточнить", "уточнить", "уточнить", "", ""])
    return {r["developer"]: r for r in csv.DictReader(TERMS.open())}


def main():
    today = date.today()
    rows = list(csv.DictReader(open(HERE.parent / "moscow_scan" / "buildings.csv")))
    rg = sqlite3.connect(HERE / "registry.db", timeout=60)
    ndr_prj = {sid: pid for sid, pid in rg.execute("select source_id, entity from xref where source='ndr' and kind='ndr_object'")}
    prj = {pid: (name, dev) for pid, name, dev in rg.execute("select p.id, p.name, d.name from projects p join developers d on d.id=p.dev_id")}
    tm = terms()
    sales_cols = [k for k in rows[0] if k.startswith("sales_")]
    pub = lambda r: datetime.strptime(r["published"], "%d.%m.%Y").date() if r["published"] else None
    # new supply: buildings with sales not started, or started (published) less than 6 months ago
    new = [r for r in rows if int(r["flats"] or 0) > 0 and (int(r["sold"] or 0) == 0 or (pub(r) and (today - pub(r)).days < 183))
           and r["lat"] and r["lon"]]
    out = []
    for r in rows:
        pid = ndr_prj.get(r["id"])
        if not pid:
            continue
        flats = int(r["flats"] or 0)
        monthly = [int(r[k] or 0) for k in sales_cols]
        p25 = statistics.quantiles(monthly, n=4)[0] if len(monthly) >= 4 else None
        stable = round(100 * p25 / flats, 2) if flats and p25 is not None else None
        mto = months_to(r["completion"], today) if r["completion"] else None
        unsold = flats - int(r["sold"] or 0)
        around = [n for n in new if n["id"] != r["id"] and r["lat"] and km((float(r["lat"]), float(r["lon"])), (float(n["lat"]), float(n["lon"]))) <= RADIUS_KM]
        near = [n for n in around if ndr_prj.get(n["id"]) != pid]            # other projects: competition from outside
        own = [n for n in around if ndr_prj.get(n["id"]) == pid]             # own new phases: shown separately
        near_flats = sum(int(n["flats"]) - int(n["sold"] or 0) for n in near)
        dev = prj[pid][1]
        t = tm.get(dev, {})
        k = {
            "К1 класс": r["class"] in CLASSES,
            "К2 темп": stable is not None and stable >= PACE_MIN,
            "К3 до ввода": mto is not None and M_FROM <= mto <= M_TO,
            "К6 новое рядом": not (unsold and near_flats > NEW_SUPPLY_X * unsold),
        }
        k4 = (t.get("reliable") or "уточнить")
        k5 = (t.get("assignment_allowed") or "уточнить")
        passed = all(k.values()) and not k4.startswith("нет") and not k5.startswith("нет")
        out.append({"project_id": pid, "zk": prj[pid][0], "developer": dev, "ndr_id": r["id"], "ndr_name": r["name"], "class": r["class"],
                    "flats": flats, "sold_pct": r["sold_pct"], "unsold": unsold, "completion": r["completion"], "months_to_completion": mto,
                    "stable_pct_month": stable, "p25_month": p25, "new_supply_2km_flats": near_flats, "new_supply_2km_objects": len(near),
                    "own_new_phases_flats": sum(int(n["flats"]) - int(n["sold"] or 0) for n in own),
                    **{n: ("да" if v else "нет") for n, v in k.items()}, "К4 застройщик": k4, "К5 переуступка": k5,
                    "passed": "да" if passed else "нет",
                    "why_not": "; ".join(n for n, v in k.items() if not v) + ("; К5 уточнить" if passed is False and k5 == "уточнить" else ""),
                    "url": r["url"]})
    out.sort(key=lambda x: (x["zk"], x["completion"] or ""))
    with (HERE / "p1_buildings.csv").open("w", newline="") as f:
        w = csv.DictWriter(f, list(out[0].keys()))
        w.writeheader()
        w.writerows(out)
    proj = []
    for pid, (name, dev) in prj.items():
        bs = [b for b in out if b["project_id"] == pid]
        if not bs:                                     # e.g. Sydney Prime: not among the selling objects of the 16.10 scan
            rg.execute("insert or replace into labels values (?,?,?,?)", (pid, "needs:ndr", "no наш.дом.рф object in the scan of 16.10",
                                                                           today.isoformat()))
        ok = [b for b in bs if b["passed"] == "да"]
        proj.append({"project_id": pid, "zk": name, "developer": dev, "buildings": len(bs), "passed": len(ok),
                     "passed_unsold_flats": sum(b["unsold"] for b in ok), "passed_ndr_ids": " ".join(b["ndr_id"] for b in ok),
                     "main_reasons": "; ".join(sorted({x for b in bs if b["passed"] == "нет" for x in b["why_not"].split("; ") if x}))})
        rg.execute("insert or replace into labels values (?,?,?,?)", (pid, "p1", f"{len(ok)} of {len(bs)} buildings pass", today.isoformat()))
        for b in bs:
            rg.execute("insert or replace into labels values (?,?,?,?)", (f"{pid}:ndr:{b['ndr_id']}", "p1", b["passed"], today.isoformat()))
    rg.commit()
    proj.sort(key=lambda p: -p["passed"])
    with (HERE / "p1_projects.csv").open("w", newline="") as f:
        w = csv.DictWriter(f, list(proj[0].keys()))
        w.writeheader()
        w.writerows(proj)
    return out, proj


if __name__ == "__main__":
    b, p = main()
    print(f"buildings {len(b)}, passing {sum(x['passed'] == 'да' for x in b)}")
    for x in p:
        print(f"{x['zk']:26s} корпусов {x['buildings']:2d} проходят {x['passed']:2d} (непроданных {x['passed_unsold_flats']}) | {x['main_reasons']}")
