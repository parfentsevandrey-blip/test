"""Слой id: все объявления внутри МКАД за десятки запросов через карту Циана.

POST api.cian.ru/search-offers-index-map/v1/get-clusters-for-map/ (найден в бандле map-search-frontend:
микросервис search-offers-index с jobName=map). На zoom >= 15 сервер отдаёт кластеры уровня дома со списком id
объявлений (clusterOfferIds), на zoom <= 14 -- только счётчики. Ограничения, найденные тестом 2026-10-20:
  - вся область МКАД одним запросом -> 500; плитки 4x4 (~10x8 км) проходят, ~1 МБ и 2 с на плитку;
  - в кластере не больше 100 id: крупные корпуса новостроек добираются делением по цене внутри точки кластера.
Полнота: вторичка 32 258 id против 32 247 по счётчику поиска по полигону МКАД.
"""
import os, sys
from concurrent.futures import ThreadPoolExecutor

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import client, inside_mkad, log

MAP = "https://api.cian.ru/search-offers-index-map/v1/get-clusters-for-map/"
BOX = (55.92, 55.57, 37.36, 37.86)  # север, юг, запад, восток: прямоугольник вокруг МКАД
GRID = 4
CAP = 100


def clusters(jq, n, s, w, e, depth=0):
    body = {"zoom": 15, "bbox": [{"bottomRight": {"lat": s, "lng": e}, "topLeft": {"lat": n, "lng": w}}],
            "jsonQuery": jq, "extended": False, "subdomain": "www"}
    try:
        return client()._req("POST", MAP, expect_json=True, json=body).json()["filtered"]
    except Exception:
        if depth >= 2:
            raise
        mlat, mlng = (n + s) / 2, (w + e) / 2  # плитка не прошла -- делим на 4
        return [c for q in ((n, mlat, w, mlng), (n, mlat, mlng, e), (mlat, s, w, mlng), (mlat, s, mlng, e))
                for c in clusters(jq, *q, depth=depth + 1)]


def refine(jq, cl, lo=None, hi=None, depth=0):
    """id переполненного кластера (count > 100): тот же запрос в точке кластера, цена делится сразу на
    ceil(count / 70) равных частей; часть, которая всё ещё переполнена, делится дальше."""
    lo = int(cl["minPrice"]) if lo is None else lo
    hi = int(cl["maxPrice"]) if hi is None else hi
    p, d = cl["coordinates"], 1e-5
    k = max(2, -(-cl["count"] // 70))
    cuts = [lo + (hi - lo) * i // k for i in range(k + 1)]
    ids = set()
    for a, b in zip([cuts[0]] + [x + 1 for x in cuts[1:-1]], cuts[1:]):
        q = dict(jq, price={"type": "range", "value": {"gte": a, "lte": b}})
        for c in clusters(q, p["lat"] + d, p["lat"] - d, p["lng"] - d, p["lng"] + d, depth=2):
            got = c.get("clusterOfferIds") or []
            if c["count"] > len(got) and b - a > 1000 and depth < 8:
                ids |= refine(jq, c, a, b, depth + 1)
            else:
                ids |= set(got)
    return ids


def id_layer(region=1, workers=2):
    """-> {id: {"kind", "lat", "lng", "jk_id", "house_id"}} для всех квартир на продажу внутри МКАД."""
    out, stats = {}, {}
    n, s, w, e = BOX
    for bs, kind in ((1, "sec"), (2, "new")):
        jq = {"_type": "flatsale", "engine_version": {"type": "term", "value": 2},
              "region": {"type": "terms", "value": [region]}, "building_status": {"type": "term", "value": bs}}
        cls = []
        for i in range(GRID):
            for j in range(GRID):
                cls += clusters(jq, n - (n - s) * i / GRID, n - (n - s) * (i + 1) / GRID,
                                w + (e - w) * j / GRID, w + (e - w) * (j + 1) / GRID)
        cls = [c for c in cls if inside_mkad(c["coordinates"]["lat"], c["coordinates"]["lng"])]
        overflow = [c for c in cls if c["count"] > len(c.get("clusterOfferIds") or [])]
        with ThreadPoolExecutor(workers) as ex:  # переполненные корпуса добираются параллельно
            extra = dict(zip(map(id, overflow), ex.map(lambda c: refine(jq, c), overflow)))
        full, over = 0, len(overflow)
        for c in cls:
            p = c["coordinates"]
            ids = set(c.get("clusterOfferIds") or []) | extra.get(id(c), set())
            pin = c.get("newbuildingPin") or {}
            for i in ids:
                out[i] = {"kind": kind, "lat": p["lat"], "lng": p["lng"], "jk_id": pin.get("id"), "house_id": pin.get("houseId")}
            full += c["count"]
        got = sum(1 for v in out.values() if v["kind"] == kind)
        stats[kind] = {"clusters": len(cls), "overflow_clusters": over, "count": full, "ids": got}
        log(f"слой id {kind}: {got} id (счётчик кластеров {full}, переполненных кластеров {over})")
    return out, stats
