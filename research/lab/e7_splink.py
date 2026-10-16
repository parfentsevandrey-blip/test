"""E7. Splink: link buildings between наш.дом.рф objects, ЦИАН houses and developer-site buildings (frozen data runs/e7/).

Records, one per building per source:
  site  — devcat.db, (source, ЖК, корпус): flats listed, max floor, completion as on the site
  cian  — lots.db, (ЖК, house_name): offers, floors, finish quarter
  ndr   — наш.дом.рф objects linked to the pilot ЖК (state.json): name, floorMax, flatsCount, dateEnd
Features: project (blocking), building key (registry.bkey), completion (year*4+quarter), floors, name.
Baseline: registry rules (site<->ЦИАН by building key only; ndr<->site: no links at all).
Output: runs/e7/pairs.csv (all scored pairs), runs/e7/result.json.
"""
import importlib.util
import json
import re
import sqlite3
import sys
from pathlib import Path

import pandas as pd
import splink.comparison_level_library as cll
import splink.comparison_library as cl
from splink import DuckDBAPI, Linker, SettingsCreator, block_on

R = Path(__file__).resolve().parent / "runs" / "e7"
SCR = Path("/tmp/claude-0/-home-user-test/ea57c60a-b461-5b01-9fed-bf21bf3c9920/scratchpad")
spec = importlib.util.spec_from_file_location("registry", SCR / "lots" / "registry.py")
REG = importlib.util.module_from_spec(spec)
spec.loader.exec_module(REG)
ROMAN = {"I": 1, "II": 2, "III": 3, "IV": 4}


def yq(text):
    """Completion -> year*4+quarter (0-based quarter); year only -> mid-year."""
    s = str(text or "")
    m = re.search(r"(\d{4})[-\s]?Q(\d)", s) or re.search(r"(\d{4})-(\d\d)-\d\d", s)
    if m and "-" in m.group(0) and "Q" not in m.group(0):
        return int(m[1]) * 4 + (int(m[2]) - 1) // 3
    if m:
        return int(m[1]) * 4 + int(m[2]) - 1
    m = re.search(r"(\d\d)\.(\d\d)\.(\d{4})", s)
    if m:
        return int(m[3]) * 4 + (int(m[2]) - 1) // 3
    m = re.search(r"\b(IV|III|II|I)\s*кв\.?\s*(\d{4})", s)
    if m:
        return int(m[2]) * 4 + ROMAN[m[1]] - 1
    m = re.search(r"(20\d\d)", s)
    return int(m[1]) * 4 + 1 if m else None


def records():
    dv, lt, rg = (sqlite3.connect(R / n) for n in ("devcat.db", "lots.db", "registry.db"))
    prj = dict(rg.execute("select cian_zk_id, id from projects"))
    slug = dict(rg.execute("select id, site_slug from projects"))
    rows = []
    for src, zk, korpus, n, floors, maxfloor, comp in dv.execute("""
            select i.source, i.zk_id, i.korpus, count(*), max(i.floors), max(i.floor), group_concat(distinct i.completion)
            from seen s join info i on i.source=s.source and i.lot_id=s.lot_id
            where s.day='2026-10-16' and coalesce(i.kind,'')!='apartments' and i.korpus is not null group by 1,2,3"""):
        p = prj.get(zk)
        sub = dv.execute("select json_extract(special,'$.subproject') from info where source=? and zk_id=? and korpus=? and special like '%subproject%' limit 1",
                         (src, zk, korpus)).fetchone()
        rows.append({"unique_id": f"site:{src}:{zk}:{korpus}", "src": "site", "prj": p, "bkey": REG.bkey(src, korpus, slug.get(p)),
                     "yq": yq(comp), "floors": floors or maxfloor, "name": f"{korpus} {(sub or [''])[0] or ''}".strip(), "n": n})
    for zk, house, n, floors, finish in lt.execute("""select o.zk_id, o.house_name, count(*), max(o.floors), group_concat(distinct o.finish)
            from obs b join offers o using(offer_id) where b.day='2026-10-16' and not o.is_apartments and o.house_name is not null group by 1,2"""):
        p = prj.get(zk)
        rows.append({"unique_id": f"cian:{zk}:{house}", "src": "cian", "prj": p, "bkey": REG.bkey("cian", house, slug.get(p)),
                     "yq": yq(finish), "floors": floors, "name": house, "n": n})
    st = json.load(open(SCR / "ndr_workflow" / "state.json"))
    for sid, p in rg.execute("select source_id, entity from xref where source='ndr' and kind='ndr_object'"):
        o = next((v["next"] for k, v in st["objects"].items() if k.endswith("/" + sid) and v.get("next")), None)
        if not o:
            continue
        rows.append({"unique_id": f"ndr:{sid}", "src": "ndr", "prj": p, "bkey": None, "yq": yq(o.get("dateEnd")),
                     "floors": o.get("floorMax"), "name": o.get("name"), "n": o.get("flatsCount")})
    df = pd.DataFrame(rows)
    df["bkey"] = df["bkey"].where(df["bkey"] != "?", None)
    return df


def main():
    df = records()
    settings = SettingsCreator(
        link_type="dedupe_only",
        blocking_rules_to_generate_predictions=[block_on("prj")],
        comparisons=[
            cl.ExactMatch("bkey"),
            cl.CustomComparison(output_column_name="yq", comparison_levels=[
                cll.NullLevel("yq"), cll.ExactMatchLevel("yq"),
                cll.CustomLevel("abs(yq_l - yq_r) <= 1", "±1 квартал"), cll.CustomLevel("abs(yq_l - yq_r) <= 4", "±1 год"), cll.ElseLevel()]),
            cl.CustomComparison(output_column_name="floors", comparison_levels=[
                cll.NullLevel("floors"), cll.ExactMatchLevel("floors"),
                cll.AbsoluteDifferenceLevel("floors", 2), cll.AbsoluteDifferenceLevel("floors", 5), cll.ElseLevel()]),
            cl.JaroWinklerAtThresholds("name", [0.9, 0.75]),
        ],
        additional_columns_to_retain=["src", "n"],
    )
    db = DuckDBAPI()
    linker = Linker(db.register(df, dataset_display_name="buildings"), settings)
    linker.training.estimate_u_using_random_sampling(max_pairs=1e5)
    linker.training.estimate_parameters_using_expectation_maximisation(block_on("prj"))
    pairs = linker.inference.predict(threshold_match_probability=0.0).as_pandas_dataframe()
    pairs = pairs[pairs["src_l"] != pairs["src_r"]]
    pairs.to_csv(R / "pairs.csv", index=False)
    df.to_csv(R / "records.csv", index=False)
    print(df.groupby("src").size().to_dict(), "pairs across sources", len(pairs), "p>=0.9", int((pairs["match_probability"] >= 0.9).sum()))


if __name__ == "__main__":
    main()
