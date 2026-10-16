"""E7 evaluation: turn Splink scores into links (best candidate per record and per other source, margin >= 1 in match weight),
compare with the registry baseline (building key equality, ЦИАН<->site only), and dump a random sample of 50 links plus
20 known hard cases for manual review (runs/e7/review.csv)."""
import random
from pathlib import Path

import pandas as pd

R = Path(__file__).resolve().parent / "runs" / "e7"
p = pd.read_csv(R / "pairs.csv")
rec = pd.read_csv(R / "records.csv").set_index("unique_id")
both = pd.concat([p.rename(columns=lambda c: c.replace("_l", "_a").replace("_r", "_b")),
                  p.rename(columns=lambda c: c.replace("_r", "_a").replace("_l", "_b"))], ignore_index=True)
links = []
for (a, src_b), g in both.groupby(["unique_id_a", "src_b"]):
    g = g.sort_values("match_weight", ascending=False)
    best, second = g.iloc[0], (g.iloc[1]["match_weight"] if len(g) > 1 else -99)
    if best["match_weight"] - second >= 1 and best["match_weight"] > -5:
        links.append({"a": a, "b": best["unique_id_b"], "src_a": a.split(":")[0], "src_b": src_b, "w": round(best["match_weight"], 1),
                      "margin": round(best["match_weight"] - second, 1)})
L = pd.DataFrame(links)
L["pair"] = L.apply(lambda r: "-".join(sorted([r.src_a, r.src_b])), axis=1)
mutual = L.merge(L, left_on=["a", "b"], right_on=["b", "a"], suffixes=("", "_back"))
L["mutual"] = L.set_index(["a", "b"]).index.isin(mutual.set_index(["a", "b"]).index)
# baseline: registry rule = same project and same building key (ЦИАН<->site)
cs = rec[rec.src.isin(["cian", "site"])].reset_index()
base = cs.merge(cs, on=["prj", "bkey"]).query("src_x=='cian' and src_y=='site'")
print("Splink links (best, margin>=1):", L.groupby("pair").size().to_dict(), "mutual:", L[L.mutual].groupby("pair").size().to_dict())
print("Baseline links ЦИАН<->site by building key:", len(base), "| ndr links:", 0)
agree = set(map(tuple, base[["unique_id_x", "unique_id_y"]].values))
sl = set(map(tuple, L[(L.src_a == "cian") & (L.src_b == "site")][["a", "b"]].values))
print("ЦИАН->site: both", len(agree & sl), "only baseline", len(agree - sl), "only Splink", len(sl - agree))
fields = ["prj", "bkey", "yq", "floors", "name", "n"]
def row(a, b, why):
    ra, rb = rec.loc[a], rec.loc[b]
    return {"why": why, "a": a, "b": b, **{f"{f}_a": ra[f] for f in fields}, **{f"{f}_b": rb[f] for f in fields}}
random.seed(17)
sample = L[L.mutual].sample(50, random_state=17) if L.mutual.sum() >= 50 else L.sample(min(50, len(L)), random_state=17)
rows = [row(r.a, r.b, "random") for r in sample.itertuples()]
hard = [x for x in rec.index if any(k in x for k in (":2812286:", ":7231:", ":7889:", ":3202206:", ":5340468:", ":4183417:"))]
H = L[L.a.isin(hard) & L.mutual].sample(20, random_state=3) if len(L[L.a.isin(hard) & L.mutual]) >= 20 else L[L.a.isin(hard)].head(20)
rows += [row(r.a, r.b, "hard") for r in H.itertuples()]
pd.DataFrame(rows).to_csv(R / "review.csv", index=False)
L.to_csv(R / "links.csv", index=False)
print("review rows:", len(rows))
