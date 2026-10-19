"""Вторая серия: какой паттерн вызывает капчу. usage: cian_probe2.py MODE OUTDIR
MODE: seq   -- последовательное листание одной выдачи (ЖК «Деснаречье», 30 стр.), пауза 4-7 с
      rand  -- те же 30 страниц той же выдачи, но в случайном порядке, пауза 4-7 с
      catalog -- 25 стр. каталога ЖК через newbuilding-search, затем 10 запросов поиска, пауза 1.5-3 с"""
import json, os, random, sys, time
from curl_cffi import requests

mode, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
API = "https://api.cian.ru/search-offers/v2/search-offers-desktop/"
NB = "https://api.cian.ru/newbuilding-search/v1/get-newbuildings-for-serp/"
HDR = {"origin": "https://www.cian.ru", "referer": "https://www.cian.ru/", "accept-language": "ru-RU,ru;q=0.9"}
Q = {"_type": "flatsale", "engine_version": {"type": "term", "value": 2},
     "geo": {"type": "geo", "value": [{"type": "newobject", "id": 4456060}]}, "sort": {"type": "term", "value": "price_object_order"}}
S = requests.Session(impersonate="chrome")
log = []


def call(kind, page, pause):
    t0 = time.time()
    if kind == "search":
        r = S.post(API, json={"jsonQuery": dict(Q, page={"type": "term", "value": page})}, headers=HDR, timeout=60)
    else:
        r = S.post(NB, json={"jsonQuery": {"_type": "newobjectsale", "engine_version": {"type": "term", "value": 2},
                                           "region": {"type": "terms", "value": [1]}},
                             "uri": "/newobjects/list/", "subdomain": "www", "offset": (page - 1) * 25, "count": 25}, headers=HDR, timeout=60)
    ok = r.text.lstrip().startswith("{")
    log.append({"n": len(log), "kind": kind, "page": page, "ok": ok, "code": r.status_code, "ms": round((time.time() - t0) * 1000)})
    print(f"{len(log) - 1:02d} {kind:7} p{page:<3} {'OK' if ok else 'BLOCK'}", flush=True)
    time.sleep(random.uniform(*pause))
    return ok


if mode == "seq":
    for p in range(1, 31):
        call("search", p, (4, 7))
elif mode == "rand":
    for p in random.sample(range(1, 31), 30):
        call("search", p, (4, 7))
elif mode == "catalog":
    for p in range(1, 26):
        call("catalog", p, (1.5, 3))
    for p in range(1, 11):
        call("search", p, (1.5, 3))
json.dump(log, open(f"{out}/{mode}.json", "w"), indent=1)
print(f"\n{mode}: OK {sum(x['ok'] for x in log)}/{len(log)}; первая капча на запросе #{next((x['n'] for x in log if not x['ok']), None)}")
