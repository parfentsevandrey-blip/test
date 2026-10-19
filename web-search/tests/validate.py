"""Проверки корректности пагинации и полноты (однопоточно, штатные паузы сборщиков).

usage: python tests/validate.py {cian-paging,domrf-paging,domrf-edge} --out FILE.json
"""
import argparse, collections, json, sys, time
from urllib.parse import quote

sys.path.insert(0, __file__.rsplit("/tests/", 1)[0] + "/collectors")
import cian, domrf  # noqa: E402


def cian_paging(sort=None):
    """Листает запрос на ~300-800 объявлений целиком и считает дубли/пропуски относительно offerCount."""
    c = cian.Cian(delay=(1.0, 2.0))
    q = {"_type": "flatsale", "engine_version": {"type": "term", "value": 2}, "region": {"type": "terms", "value": [1]},
         "building_status": {"type": "term", "value": 2}, "room": {"type": "terms", "value": [1]},
         "price": {"type": "range", "value": {"gte": 14_000_000, "lte": 15_000_000}}}
    if sort:
        q["sort"] = {"type": "term", "value": sort}
    total, offers = c.search(q)
    ids = [o["id"] for o in offers]
    cnt = collections.Counter(ids)
    return {"sort": sort or "default(ml)", "offerCount": total, "fetched": len(ids), "unique": len(cnt),
            "duplicates": sum(v - 1 for v in cnt.values()), "missing_vs_count": total - len(cnt)}


def domrf_paging(sort_field):
    d = domrf.DomRF()
    ids, total, page = [], None, 0
    while True:
        qs = f"offset={page * 50}&limit=50&objStatus=0&place=77&sortField={sort_field}&sortType=desc"
        data = d._req("GET", f"{domrf.H}{domrf.LIST}?{qs}")["data"]
        total = data["total"]
        ids += [o["objId"] for o in data["list"]]
        page += 1
        if len(data["list"]) < 50:
            break
    cnt = collections.Counter(ids)
    return {"sortField": sort_field, "total": total, "fetched": len(ids), "unique": len(cnt),
            "duplicates": sum(v - 1 for v in cnt.values()), "missing_vs_total": total - len(cnt)}


def domrf_edge():
    """Граничные случаи: объект без продаж/квартирографии, несуществующий objId, сданный объект."""
    d = domrf.DomRF()
    out = {}
    for name, fn in {
        "flats_no_sales(13398)": lambda: sum(1 for _ in d.flats(13398)),
        "units_no_data(13398)": lambda: sum(1 for _ in d.units(13398)),
        "flats_bad_id(999999999)": lambda: sum(1 for _ in d.flats(999999999)),
        "units_bad_id(999999999)": lambda: sum(1 for _ in d.units(999999999)),
        "compare_bad_id": lambda: len(d.details([999999999])),
    }.items():
        t0 = time.time()
        try:
            out[name] = {"result": fn(), "s": round(time.time() - t0, 1)}
        except Exception as e:
            out[name] = {"error": f"{e.__class__.__name__}: {str(e)[:160]}", "s": round(time.time() - t0, 1)}
        print(name, out[name], flush=True)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("check", choices=["cian-paging", "domrf-paging", "domrf-edge"])
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    if a.check == "cian-paging":
        res = [cian_paging(s) for s in (None, None, "price_object_order")]
    elif a.check == "domrf-paging":
        res = [domrf_paging(f) for f in ("objPublDt", "objId", "objPublDt")]
    else:
        res = domrf_edge()
    print(json.dumps(res, ensure_ascii=False, indent=1))
    json.dump(res, open(a.out, "w"), ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
