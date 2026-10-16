"""E6. Pandera schemas vs qa.py on the same frozen data (research/lab/runs/e6/).

Hypothesis: declarative dataframe schemas catch data defects that qa.py (rules V1-V7, re-reading S0/S1) misses.
Tables: ЦИАН offers of the day (lots.db), developer flats of the day (devcat.db), наш.дом.рф buildings (buildings.csv),
наш.дом.рф complexes (complexes.csv), registry buildings and flats (registry.db).
Output: runs/e6/result.json — failures per table and check, overlap with qa.py findings, runtime.
"""
import json
import sqlite3
import time
from collections import Counter
from datetime import date
from pathlib import Path

import pandas as pd
import pandera.pandas as pa
from pandera.pandas import Check, Column, DataFrameSchema

R = Path(__file__).resolve().parent / "runs" / "e6"
PILOT_ZK = {"2812286", "7231", "3401924", "5699304", "4494942", "5221539", "7889", "4888961", "2446053", "4377697", "3202206", "4183417", "5340468"}
TODAY = pd.Timestamp(date(2026, 10, 17))


def frames():
    lt, dv, rg = (sqlite3.connect(R / n) for n in ("lots.db", "devcat.db", "registry.db"))
    cday = lt.execute("select max(day) from obs where day<='2026-10-16'").fetchone()[0]
    cian = pd.read_sql("""select o.*, b.price, b.price_m2 from obs b join offers o using(offer_id) where b.day=?""", lt, params=(cday,))
    dday = "2026-10-16"
    dev = pd.read_sql("""select s.source, s.lot_id, s.zk_id, s.status, i.kind, i.korpus, i.floor, i.floors, i.rooms, i.area, i.completion,
                                (select price from prices p where p.source=s.source and p.lot_id=s.lot_id order by p.day desc limit 1) price
                         from seen s join info i on i.source=s.source and i.lot_id=s.lot_id where s.day=?""", dv, params=(dday,))
    dev["m2"] = dev["price"] / dev["area"]
    bld = pd.read_csv(R / "buildings.csv", dtype={"id": str})
    cpx = pd.read_csv(R / "complexes.csv")
    rb = pd.read_sql("select * from buildings", rg)
    rf = pd.read_sql("select * from flats", rg)
    return {"cian_offers": cian, "dev_flats": dev, "ndr_buildings": bld, "ndr_complexes": cpx, "reg_buildings": rb, "reg_flats": rf}


m2 = Check.in_range(150_000, 5_000_000)
SCHEMAS = {
    "cian_offers": DataFrameSchema({
        "offer_id": Column(int, Check.gt(0), unique=True),
        "zk_id": Column(str, Check.isin(PILOT_ZK)),
        "rooms": Column(float, Check.in_range(0, 7), nullable=True),
        "area": Column(float, Check.in_range(12, 600)),
        "floor": Column(float, Check.in_range(-2, 100), nullable=True),
        "floors": Column(float, Check.in_range(1, 100), nullable=True),
        "price": Column(float, Check.gt(0)),
        "price_m2": Column(float, m2),
        "category": Column(str, Check.isin(["newBuildingFlatSale", "flatSale"])),
        "sale_type": Column(str, Check.isin(["fz214", "dupt", "free", "alternative"]), nullable=True),
        "house_name": Column(str, nullable=False),                                   # needed to place a lot in a building
        "created": Column(str, Check(lambda s: pd.to_datetime(s.str[:19], errors="coerce") <= TODAY, element_wise=False)),
    }, checks=[Check(lambda d: d["floor"].isna() | d["floors"].isna() | (d["floor"] <= d["floors"]), name="floor<=floors"),
               Check(lambda d: ~((d["rooms"] == 0) & (d["area"] > 45)), name="studio<=45m2"),
               Check(lambda d: (d["price_m2"] - d["price"] / d["area"]).abs() <= 0.02 * d["price_m2"], name="price_m2=price/area")],
        coerce=True, strict=False),
    "dev_flats": DataFrameSchema({
        "zk_id": Column(str, Check.isin(PILOT_ZK)),
        "rooms": Column(float, Check.in_range(0, 7), nullable=True),
        "area": Column(float, Check.in_range(12, 600), nullable=True),
        "floor": Column(float, Check.in_range(-2, 100), nullable=True),
        "price": Column(float, Check.gt(0), nullable=True),
        "m2": Column(float, m2, nullable=True),
        "lot_id": Column(str),
    }, checks=[Check(lambda d: ~d.duplicated(["source", "lot_id"]), name="unique(source,lot)"),
               Check(lambda d: d["floor"].isna() | d["floors"].isna() | (d["floor"] <= d["floors"]), name="floor<=floors"),
               Check(lambda d: ~((d["rooms"] == 0) & (d["area"] > 45) & (d["kind"] != "penthouse")), name="studio<=45m2"),
               Check(lambda d: (d["kind"] != "apartments"), name="no apartments among flats"),
               Check(lambda d: d["source"].isin(["highlife"]) | d["area"].notna(), name="area known (detail pages)")],
        coerce=True, strict=False),
    "ndr_buildings": DataFrameSchema({
        "id": Column(str, unique=True),
        "flats": Column(int, Check.ge(0)),
        "sold": Column(int, Check.ge(0)),
        "lat": Column(float, Check.in_range(55.55, 55.95)),
        "lon": Column(float, Check.in_range(37.3, 37.9)),
        "class": Column(str, Check.isin(["Комфорт", "Бизнес", "Элитный", "Типовой"])),
        "completion": Column(str, Check.str_matches(r"^\d\d\.\d\d\.\d{4}$")),
    }, checks=[Check(lambda d: d["sold"] <= d["flats"], name="sold<=flats"),
               Check(lambda d: d["flats"] > 0, name="has flats"),
               Check(lambda d: d["sold_pct"].isna() | ((d["sold_pct"] - 100 * d["sold"] / d["flats"].where(d["flats"] > 0)).abs() <= 1), name="sold_pct consistent"),
               Check(lambda d: d[[c for c in d.columns if c.startswith("sales_")]].sum(axis=1) <= d["sold"] + 5, name="12m sales <= sold"),
               Check(lambda d: d[[c for c in d.columns if c.startswith("price_m2_")]].replace(0, pd.NA).max(axis=1).isna()
                     | d[[c for c in d.columns if c.startswith("price_m2_")]].replace(0, pd.NA).max(axis=1).between(150_000, 5_000_000), name="deal price/m2 in range")],
        coerce=True, strict=False),
    "ndr_complexes": DataFrameSchema({
        "name": Column(str, unique=False),
        "flats": Column(int, Check.ge(0)),
        "sold": Column(int, Check.ge(0)),
        "remaining": Column(int),
    }, checks=[Check(lambda d: d["remaining"] == d["flats"] - d["sold"], name="remaining=flats-sold"),
               Check(lambda d: ~d.duplicated(["name", "developer"]), name="unique(name,developer)")],
        coerce=True, strict=False),
    "reg_buildings": DataFrameSchema({"key": Column(str, Check.ne("?"))}, checks=[Check(lambda d: ~d.duplicated(["prj_id", "key"]), name="unique(prj,key)")], strict=False),
    "reg_flats": DataFrameSchema({"bld_id": Column(str, nullable=False), "area": Column(float, Check.in_range(12, 600), nullable=True)}, coerce=True, strict=False),
}


def main():
    t0 = time.time()
    fr = frames()
    out = {}
    for name, sch in SCHEMAS.items():
        df = fr[name]
        try:
            sch.validate(df, lazy=True)
            out[name] = {"rows": len(df), "failures": {}}
        except pa.errors.SchemaErrors as e:
            fc = e.failure_cases
            frame_level = fc["schema_context"].eq("DataFrameSchema")
            label = fc["check"].astype(str).where(frame_level, fc["column"].astype(str) + ":" + fc["check"].astype(str))
            fc = fc.assign(label=label)
            rows = fc.dropna(subset=["index"]).groupby("label")["index"].apply(lambda x: sorted(set(x)))
            out[name] = {"rows": len(df), "failures": {k: len(v) for k, v in rows.items()},
                         "examples": {k: [df.iloc[int(i)].to_dict() for i in v[:3]] for k, v in rows.items()}}
    qa = json.loads((R / "qa.json").read_text())
    res = {"runtime_s": round(time.time() - t0, 1), "pandera": out, "qa_findings": dict(Counter(f["rule"] for f in qa["findings"]))}
    (R / "result.json").write_text(json.dumps(res, ensure_ascii=False, indent=1, default=str))
    print(json.dumps({k: {"rows": v["rows"], "failures": v["failures"]} for k, v in out.items()}, ensure_ascii=False, indent=1), "\nruntime", res["runtime_s"], "s; qa:", res["qa_findings"])


if __name__ == "__main__":
    main()
