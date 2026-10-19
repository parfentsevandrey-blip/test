"""Сверка полноты прогона cian.py --by-jk с базой региона.
usage: python tests/reconcile_cian.py OUT.jsonl BASELINE.json [--end-baseline]"""
import collections, json, sys, time

sys.path.insert(0, __file__.rsplit("/tests/", 1)[0] + "/collectors")
import cian  # noqa: E402

out, base_path = sys.argv[1], sys.argv[2]
base = json.load(open(base_path))
rows = [json.loads(l) for l in open(out)]
stats = [json.loads(l) for l in open(out + ".jk-stats.jsonl")]
last = {}
for s in stats:  # итог по ЖК = последняя попытка
    last[s["jk_id"]] = s
ok = [s for s in last.values() if "error" not in s]
err = [s for s in last.values() if "error" in s]

end = None
if "--end-baseline" in sys.argv:
    c = cian.Cian()
    d = c._page({"_type": "flatsale", "engine_version": {"type": "term", "value": 2}, "region": {"type": "terms", "value": [1]},
                 "building_status": {"type": "term", "value": 2}}, 1)
    end = {"ts": time.strftime("%Y-%m-%d %H:%M:%S"), "offerCount": d["offerCount"], "aggregatedCount": d["aggregatedCount"]}

sum_count = sum(s["offer_count"] or 0 for s in ok)
sum_fetched = sum(s["fetched"] for s in ok)
short = sorted(((s["offer_count"] - s["fetched"], s["jk_id"], s["offer_count"], s["fetched"]) for s in ok
                if s["offer_count"] and s["fetched"] < s["offer_count"] - 2), reverse=True)
ids = collections.Counter(r["offer_id"] for r in rows)
region = (base["offerCount"] + end["offerCount"]) / 2 if end else base["offerCount"]
res = {
    "baseline_start": base, "baseline_end": end,
    "jk_total": len(last), "jk_ok": len(ok), "jk_failed": [s["jk_id"] for s in err],
    "jk_with_zero_offers": sum(1 for s in ok if not s["offer_count"]),
    "sum_offerCount_by_jk": sum_count, "sum_fetched": sum_fetched,
    "collected_rows": len(rows), "unique_offer_ids": len(ids), "duplicate_rows": sum(v - 1 for v in ids.values()),
    "cross_jk_duplicates": sum_fetched - len(ids),
    "fetched_vs_jk_counts": round(sum_fetched / sum_count * 100, 2) if sum_count else None,
    "unique_vs_region": round(len(ids) / region * 100, 2),
    "not_in_catalog_estimate": round(region - sum_count),
    "jk_shortfall_top": short[:10],
    "rows_without_jk": sum(1 for r in rows if not r.get("jk_id")),
    "with_duplicates_flag": sum(1 for r in rows if r.get("duplicates")),
}
print(json.dumps(res, ensure_ascii=False, indent=1))
json.dump(res, open(out + ".reconcile.json", "w"), ensure_ascii=False, indent=1)
