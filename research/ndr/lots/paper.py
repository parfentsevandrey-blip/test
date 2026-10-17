"""[П6] Paper trading: every day, record the lots our rules would buy, without money; follow what happens to them.

Rules that select a lot (each entry names its rule):
  R1 ниже сопоставимых   — model.py candidates: price below comparable resale lots enough for 10 % net in 6 months
                            (all candidates go in, with the model's «проверить вручную» notes; the journal shows which rules work)
  R2 последние у застройщика — [С1] dev_signals.csv signal types: the cheapest flat of the type still on the developer's list
  R3 дефицит по просмотрам   — [С3] c3_signals.csv deficit types: the cheapest lot of the type on ЦИАН
Entry: price now; exit estimate: the model's exit ₽/м² × area minus bargaining (R1), the type's median ₽/м² of resale lots (R2, R3).
Tracking, every day after the snapshot:
  the lot itself — still listed, gone (date), price cuts;
  the market     — lots of the same ЖК × комнатность × стадия gone from ЦИАН without a price cut after the entry
                   (a proxy of a sale), their median ₽/м² vs our exit estimate;
  outcome at 3 / 6 / 9 months — «попадание», if such sales happened at ≥ our exit ₽/м², else «промах»; price error in %.
The decision «идём с деньгами» (INSTRUCTION П6) uses the share of hits and the mean price error after 6-8 weeks.
"""
import csv
import json
import sqlite3
import statistics
from datetime import date, timedelta
from pathlib import Path

HERE = Path(__file__).resolve().parent
DB = HERE / "paper.db"
SCHEMA = """
create table if not exists trades (id integer primary key, day text, rule text, source text, lot text, zk text, zk_id text, type text,
    stage text, house text, floor int, area real, entry_price real, exit_m2 real, exit_price real, roi_6m real, note text,
    unique (day, source, lot));
create table if not exists track (trade_id int, day text, lot_listed int, lot_price real, market_sales int, market_m2 real,
    primary key (trade_id, day));
"""


def add(db, day, rule, source, lot, zk, zk_id, t, stage, house, floor, area, price, exit_m2, roi, note):
    if not (price and area and exit_m2):
        return 0
    exit_price = exit_m2 * area * 0.97
    return db.execute("""insert or ignore into trades (day, rule, source, lot, zk, zk_id, type, stage, house, floor, area, entry_price,
                         exit_m2, exit_price, roi_6m, note) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                      (day, rule, source, str(lot), zk, zk_id, t, stage, house, floor, area, price, exit_m2, exit_price, roi, note)).rowcount


def suspects():
    """qa.py findings: ЦИАН offers and site flats labelled qa:suspect in the registry do not enter the journal."""
    p = HERE / "registry.db"
    if not p.exists():
        return set()
    rg = sqlite3.connect(p)
    return {(s, i) for s, i in rg.execute("""select x.source, x.source_id from xref x join labels l on l.entity=x.entity
                                             where l.label='qa:suspect' and x.kind='flat'""")}


def select(db, day):
    n = 0
    bad = suspects()
    p1_ok = {r["zk"] for r in csv.DictReader(open(HERE / "p1_projects.csv")) if int(r["passed"])} if (HERE / "p1_projects.csv").exists() else set()
    m = json.loads((HERE / "model.json").read_text())
    zk_id = {v: k for d in json.loads((HERE / "pilot_zk.json").read_text()).values() for k, v in d.items()}
    for c in m.get("candidates", []):
        if ("cian", str(c["offer_id"])) in bad:
            continue
        n += add(db, day, "R1 ниже сопоставимых" + ("" if c["zk"] in p1_ok else " (ЖК вне П1)"), "cian", c["offer_id"], c["zk"], zk_id.get(c["zk"]), c["type"],
                 "готовый" if (c.get("finish") or "") < day[:4] else "строится", c.get("house"), c.get("floor"), c.get("area"),
                 c.get("price"), c.get("exit_m2"), c.get("roi_6m"), f"{c.get('status')}: {c.get('check') or ''}".strip(": "))
    c2 = {(r["zk_id"], r["type"], r["stage"]): r for r in csv.DictReader(open(HERE / "c2_signals.csv")) if r["level"] == "ЖК"} \
        if (HERE / "c2_signals.csv").exists() else {}
    if (HERE / "dev_signals.csv").exists():
        dv = sqlite3.connect(HERE / "devcat.db", timeout=60)
        for r in csv.DictReader(open(HERE / "dev_signals.csv")):
            if r["signal"] != "1":
                continue
            ref = next((v for (z, t, s), v in c2.items() if z == r["zk_id"] and t == r["type"]), None)
            lot = dv.execute("""select s.source, s.lot_id, i.area, i.floor, (select price from prices p where p.source=s.source and p.lot_id=s.lot_id
                                order by day desc, origin='page' desc limit 1) pr from seen s join info i on i.source=s.source and i.lot_id=s.lot_id
                                where s.day=(select max(day) from seen) and s.zk_id=? and i.korpus=? order by pr limit 1""",
                             (r["zk_id"], r["korpus"])).fetchone()
            if lot and ref and ref.get("m2"):
                n += add(db, day, "R2 последние у застройщика", lot[0], lot[1], r["zk"], r["zk_id"], r["type"], "строится", r["korpus"],
                         lot[3], lot[2], lot[4], float(ref["m2"]), None, r["flags"])
    if (HERE / "c3_signals.csv").exists():
        lt = sqlite3.connect(HERE / "lots.db", timeout=60)
        for r in csv.DictReader(open(HERE / "c3_signals.csv")):
            ref = c2.get((r["zk_id"], r["type"], r["stage"]))
            if r["deficit"] != "1" or not ref or not ref.get("m2"):
                continue
            lot = lt.execute("""select o.offer_id, o.area, o.floor, o.house_name, b.price from obs b join offers o using(offer_id)
                                where b.day=? and o.zk_id=? and not (o.from_developer=1 or o.sale_type='fz214') order by b.price_m2 limit 1""",
                             (day, r["zk_id"])).fetchone()
            if lot:
                n += add(db, day, "R3 дефицит по просмотрам", "cian", lot[0], r["zk"], r["zk_id"], r["type"], r["stage"], lot[3], lot[2],
                         lot[1], lot[4], float(ref["m2"]), None, f"interest x{r['interest_x']}")
    db.commit()
    return n


def track(db, day):
    lt = sqlite3.connect(HERE / "lots.db", timeout=60)
    c2 = json.loads((HERE / "c2_signals.json").read_text()) if (HERE / "c2_signals.json").exists() else {}
    for tid, src, lot, zk_id, t, stage, d0 in db.execute("select id, source, lot, zk_id, type, stage, day from trades").fetchall():
        listed, price = None, None
        if src == "cian":
            r = lt.execute("select price from obs where day=? and offer_id=?", (day, lot)).fetchone()
            listed, price = int(bool(r)), (r[0] if r else None)
        # market: same ЖК × type × stage lots gone from ЦИАН without a price cut after the entry day (from c2.py, rolling 28 days)
        row = next((x for x in c2.get("types", []) if x["level"] == "ЖК" and x["zk_id"] == zk_id and x["type"] == t
                    and x["stage"] == stage), None)
        db.execute("insert or replace into track values (?,?,?,?,?,?)", (tid, day, listed, price, row["gone_nocut"] if row else None,
                                                                        row["m2"] if row else None))
    db.commit()


def export(db):
    rows = db.execute("""select t.*, (select lot_listed from track k where k.trade_id=t.id order by day desc limit 1) listed_now,
                                (select min(day) from track k where k.trade_id=t.id and k.lot_listed=0) gone_day,
                                (select market_sales from track k where k.trade_id=t.id order by day desc limit 1) market_sales_28d,
                                (select market_m2 from track k where k.trade_id=t.id order by day desc limit 1) market_m2_now
                         from trades t order by day, rule, roi_6m desc""").fetchall()
    cols = [c[0] for c in db.execute("select t.*, 1 listed_now, 1 gone_day, 1 market_sales_28d, 1 market_m2_now from trades t limit 0").description]
    with (HERE / "paper_trades.csv").open("w", newline="") as f:
        w = csv.writer(f)
        w.writerow(cols + ["check_3m", "check_6m", "check_9m"])
        for r in rows:
            d0 = date.fromisoformat(r[1])
            w.writerow(list(r) + [(d0 + timedelta(days=x)).isoformat() for x in (91, 182, 274)])
    return len(rows)


if __name__ == "__main__":
    db = sqlite3.connect(DB, timeout=60)
    db.executescript(SCHEMA)
    day = sqlite3.connect(HERE / "lots.db").execute("select max(day) from obs").fetchone()[0]
    new = select(db, day)
    track(db, day)
    total = export(db)
    by = db.execute("select rule, count(*) from trades group by 1").fetchall()
    print(f"{day}: new paper trades {new}, total {total}, by rule {by}")
