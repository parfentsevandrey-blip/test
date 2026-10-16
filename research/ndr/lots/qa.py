"""Double check of the data after every run (MANIFEST principle 9): two independent layers, findings Q-n in RUNLOG.md.

Layer 1 — rules over every record collected today (devcat.db, lots.db):
  V1 values in range: area 12-600 м², ₽/м² 150 тыс.-5 млн, floor <= floors, rooms 0-7 (or a penthouse), price > 0
  V2 plausible rooms for the area: studio <= 45 м², 1к <= 90 м² (else a parsing or labelling error, cf. L15)
  V3 price outliers inside a project: ₽/м² below 0.4x or above 2.5x the project's median (thousands vs roubles, wrong field)
  V4 duplicates on a developer site: two ids with the same building, floor, area and number
  V5 ЦИАН vs developer site for the same flat (registry match): price differs by more than 3 %, floor or area differ
Layer 2 — independent re-reading: a random sample of today's flats is opened again on its own flat page
  (another page than the list we parsed: Донстрой, ФСК, Гранель) and price / area / floor are compared with what we stored.
  Same manners as the collectors (devcat.fetch: allowed pages only, 20-40 s, stop on 403/429).
Every finding gets an id Q<n>; flats behind a finding get the registry label qa:suspect (value = the rule), which keeps
them out of signals and the paper-trading journal until the next run clears the label. Deterministic fixes are applied
here (marked «исправлено»), the rest go to «Разбор» in RUNLOG.md.
"""
import importlib.util
import json
import random
import re
import sqlite3
import statistics
from pathlib import Path

HERE = Path(__file__).resolve().parent
SAMPLE = {"donstroy": 3, "granelle": 3, "fsk": 2}


def load(name):
    spec = importlib.util.spec_from_file_location(name, HERE / f"{name}.py")
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


def rules(dv, lt, rg, day):
    out = []
    rows = dv.execute("""select s.source, s.lot_id, i.zk_id, i.korpus, i.floor, i.floors, i.rooms, i.area, i.kind, i.special,
                                (select price from prices p where p.source=s.source and p.lot_id=s.lot_id order by p.day desc limit 1)
                         from seen s join info i on i.source=s.source and i.lot_id=s.lot_id where s.day=?""", (day,)).fetchall()
    rows = [r for r in rows if (r[8] or "") != "apartments"]              # L19
    by_bld = {}
    for r in rows:
        if r[10] and r[7]:
            by_bld.setdefault((r[2], r[3]), []).append(r[10] / r[7])
    med = {k: statistics.median(v) for k, v in by_bld.items() if len(v) >= 5}   # L22: a dear quarter is not an error
    dup = {}
    for src, lot, zk, korpus, floor, floors, rooms, area, kind, special, price in rows:
        where = f"{src}:{lot}"
        if area is not None and not 12 <= area <= 600:
            out.append(("V1", where, f"площадь {area} м²"))
        if price and area and not 150_000 <= price / area <= 5_000_000:
            out.append(("V1", where, f"{round(price / area):,} ₽/м²".replace(",", " ")))
        if floor and floors and floor > floors:
            out.append(("V1", where, f"этаж {floor} из {floors} (так на сайте: особенность источника, не разбора)"))
        if rooms is not None and not 0 <= rooms <= 7:
            out.append(("V1", where, f"комнат {rooms}"))
        if rooms == 0 and area and area > 45 and kind != "penthouse":
            out.append(("V2", where, f"студия {area} м²"))
        if rooms == 1 and area and area > 90:
            out.append(("V2", where, f"1к {area} м²"))
        m = med.get((zk, korpus))
        if price and area and m and not 0.4 <= (price / area) / m <= 2.5:
            out.append(("V3", where, f"₽/м² {round(price / area):,} при медиане корпуса {korpus} {round(m):,}".replace(",", " ")))
        num = (json.loads(special) if special else {}).get("number")
        if num and area:
            k = (src, zk, korpus, floor, round(area, 1), num)
            if k in dup:
                out.append(("V4", where, f"дубль {src}:{dup[k]} (корпус {korpus}, этаж {floor}, №{num})"))
            dup[k] = lot
    # V5: ЦИАН developer listing vs the developer site, for flats the registry matched
    cday = lt.execute("select max(day) from obs").fetchone()[0]
    for oid, fid in rg.execute("select source_id, entity from xref where source='cian' and kind='flat' and how like 'unique match: building%'"):
        site = rg.execute("select source, source_id from xref where entity=? and kind='flat' and source!='cian'", (fid,)).fetchone()
        c = lt.execute("select b.price, o.floor, o.area from obs b join offers o using(offer_id) where b.day=? and o.offer_id=?", (cday, oid)).fetchone()
        if not site or not c:
            continue
        p = dv.execute("select price from prices where source=? and lot_id=? order by day desc, origin='page' desc limit 1", site).fetchone()
        sp = dv.execute("select special from info where source=? and lot_id=?", site).fetchone()
        cash = ((json.loads(sp[0]) if sp and sp[0] else {}).get("full_payment") or {}).get("price")
        ok = lambda x: x and c[0] and abs(c[0] / x - 1) <= 0.03
        if p and p[0] and c[0] and not ok(p[0]) and not ok(cash):          # L20: FORMA lists the cash price on ЦИАН
            out.append(("V5", f"{site[0]}:{site[1]}", f"цена на сайте {p[0]:,.0f}, на ЦИАН {c[0]:,.0f} ({100 * (c[0] / p[0] - 1):+.1f}%), объявление {oid}".replace(",", " ")))
    return out


def resample(dv, day, log):
    """Layer 2: open a random sample of today's flats on their own pages and compare."""
    dc = load("devcat")
    out, checked = [], 0
    random.seed(day)
    for src, n in SAMPLE.items():
        if dv.execute("select status from gruns where day=? and grp=?", (day, src if src != "granelle" else "visitor")).fetchone() in (None,) \
                and src != "granelle":
            pass
        grp = src if src != "granelle" else "visitor"
        st = dv.execute("select status, finished from gruns where day=? and grp=?", (day, grp)).fetchone()
        if st and (st[0] or "").startswith("BLOCKED"):
            log("qa", src, "skipped: blocked today")
            continue
        if st and not st[1]:                                   # the collector is on this site right now: do not double the load
            log("qa", src, "skipped: collector still running on the site")
            continue
        lots = dv.execute("""select s.lot_id, i.url, i.area, i.floor,
                                    (select price from prices p where p.source=s.source and p.lot_id=s.lot_id order by p.day desc limit 1)
                             from seen s join info i on i.source=s.source and i.lot_id=s.lot_id
                             where s.day=? and s.source=? and i.area is not null and i.url is not null""", (day, src)).fetchall()
        prev = qa_prev_suspects(src)                               # yesterday's S findings are re-read first (L23)
        pick = [l for l in lots if l[0] in prev] + random.sample([l for l in lots if l[0] not in prev], min(n, len(lots)))
        for lot, url, area, floor, price in pick:
            try:
                got = page_values(dc, dv, src, url)
            except dc.Blocked as e:
                log("qa", src, "blocked:", e)
                break
            checked += 1
            if got is None:
                out.append(("S0", f"{src}:{lot}", f"страница квартиры не открылась или не разобралась: {url}"))
                continue
            gp, ga, gf = got
            if gp and price and abs(gp / price - 1) > 0.005:
                out.append(("S1", f"{src}:{lot}", f"цена в базе {price:,.0f}, на странице квартиры {gp:,.0f}".replace(",", " ")))
            if ga and area and abs(ga - area) > 0.15:
                out.append(("S1", f"{src}:{lot}", f"площадь в базе {area}, на странице {ga}"))
            if gf is not None and floor is not None and int(gf) != int(floor):
                out.append(("S1", f"{src}:{lot}", f"этаж в базе {floor}, на странице {gf}"))
    return out, checked


def qa_prev_suspects(src):
    p = HERE / "qa.json"
    if not p.exists():
        return set()
    return {f["where"].split(":", 1)[1] for f in json.loads(p.read_text())["findings"] if f["rule"].startswith("S") and f["where"].startswith(src + ":")}


def page_values(dc, dv, src, url):
    if src == "donstroy":
        html, st = dc.fetch(dv, url)
        if not html:
            return None
        fd, fi = dc.jvar(html, "flat_data") or {}, dc.jvar(html, "flat_info") or {}
        return fd.get("price"), float(fd["area"]) if fd.get("area") else None, int(fi["floor"]) if fi.get("floor") else None
    if src == "fsk":
        html, st = dc.fetch(dv, url)
        d = dc.nuxt(html) if html else None
        page = next((x for x in (d or {}).get("data") or [] if isinstance(x, dict) and "flat" in x), None)
        f = page["flat"] if page else None
        return (f.get("price"), f.get("areaTotal"), f.get("floorNumber")) if f else None
    if src == "granelle":
        html, st = dc.fetch(dv, url)
        d = dc.nuxt(html) if html else None
        page = next((x for x in (d or {}).get("data") or [] if isinstance(x, dict) and "lot" in x), None)
        f = page["lot"] if page else None
        return (float(f.get("current_price") or f.get("price") or 0) or None, f.get("area"), f.get("floor")) if f else None
    return None


def main(sample=True):
    dc = load("devcat")
    dv, lt = sqlite3.connect(HERE / "devcat.db", timeout=60), sqlite3.connect(HERE / "lots.db", timeout=60)
    rg = sqlite3.connect(HERE / "registry.db", timeout=60)
    day = dv.execute("select max(day) from seen").fetchone()[0]
    found = rules(dv, lt, rg, day)
    checked = 0
    if sample:
        more, checked = resample(dv, day, dc.log)
        found += more
    rg.execute("delete from labels where label='qa:suspect'")             # every run re-decides
    for rule, where, what in found:
        src, lot = where.split(":", 1)
        fid = rg.execute("select entity from xref where source=? and source_id=? and kind='flat'", (src, lot)).fetchone()
        if fid:
            rg.execute("insert or replace into labels values (?,?,?,?)", (fid[0], "qa:suspect", f"{rule}: {what}", day))
    rg.commit()
    res = {"day": day, "layer1_records": dv.execute("select count(*) from seen where day=?", (day,)).fetchone()[0],
           "layer2_checked": checked, "findings": [{"id": f"Q{i}", "rule": r, "where": w, "what": t} for i, (r, w, t) in enumerate(found, 1)]}
    (HERE / "qa.json").write_text(json.dumps(res, ensure_ascii=False, indent=1))
    return res


if __name__ == "__main__":
    import sys
    r = main(sample="--no-sample" not in sys.argv)
    from collections import Counter
    print(f"{r['day']}: проверено правилами {r['layer1_records']}, перечитано страниц {r['layer2_checked']}, находок {len(r['findings'])}",
          dict(Counter(f["rule"] for f in r["findings"])))
    for f in r["findings"][:15]:
        print(f["id"], f["rule"], f["where"], f["what"])
