"""Полнота слоя id через карту Циана (search-offers-index-map/v1/get-clusters-for-map, zoom 15): все id внутри МКАД
по плиткам, плитка делится на 4 при ошибке сервера. Сравнение со счётчиком поиска по полигону МКАД."""
import json, os, sys, time
sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "daily"))
from common import client, inside_mkad, MKAD_QUERY
MAP = "https://api.cian.ru/search-offers-index-map/v1/get-clusters-for-map/"
c = client()
def tile(jq, n, s, w, e, depth=0):
    body = {"zoom": 15, "bbox": [{"bottomRight": {"lat": s, "lng": e}, "topLeft": {"lat": n, "lng": w}}], "jsonQuery": jq, "extended": False, "subdomain": "www"}
    try:
        d = c._req("POST", MAP, expect_json=True, json=body).json()
    except Exception as ex:
        if depth >= 3: raise
        print("  делю плитку", round(n, 3), round(w, 3), str(ex)[:60], flush=True)
        mlat, mlng = (n + s) / 2, (w + e) / 2
        return [x for q in ((n, mlat, w, mlng), (n, mlat, mlng, e), (mlat, s, w, mlng), (mlat, s, mlng, e)) for x in tile(jq, *q, depth + 1)]
    return d["filtered"]
base = {"_type": "flatsale", "engine_version": {"type": "term", "value": 2}, "region": {"type": "terms", "value": [1]}}
N, S, W, E, k = 55.92, 55.57, 37.36, 37.86, 4
for bs in (1, 2):
    jq = dict(base, building_status={"type": "term", "value": bs})
    t, clusters = time.time(), []
    for i in range(k):
        for j in range(k):
            clusters += tile(jq, N - (N - S) * i / k, N - (N - S) * (i + 1) / k, W + (E - W) * j / k, W + (E - W) * (j + 1) / k)
    ids = {}
    for cl in clusters:
        if inside_mkad(cl["coordinates"]["lat"], cl["coordinates"]["lng"]):
            for i in cl.get("clusterOfferIds") or []:
                ids[i] = cl
    no_ids = sum(cl["count"] for cl in clusters if not cl.get("clusterOfferIds"))
    print(f"building_status={bs}: кластеров {len(clusters)}, id внутри МКАД {len(ids)}, кластеры без id: {no_ids} объявл., {time.time()-t:.0f} с", flush=True)
    json.dump(sorted(ids), open(f"data/re/map-ids-bs{bs}.json", "w"))
    pc = c._page(dict(base, geo={"type": "geo", "value": [MKAD_QUERY]}, building_status={"type": "term", "value": bs}), 1)
    print(f"  поиск по полигону: offerCount {pc['offerCount']}", flush=True)
