"""Что вызывает капчу на api.cian.ru: одиночные запросы разной формы в случайном порядке с паузами.
usage: python tests/cian_probe.py OUTDIR [REPEATS] [PAUSE_MIN PAUSE_MAX]"""
import json, os, random, sys, time
from curl_cffi import requests

out = sys.argv[1]
reps = int(sys.argv[2]) if len(sys.argv) > 2 else 4
pmin, pmax = (float(sys.argv[3]), float(sys.argv[4])) if len(sys.argv) > 4 else (4, 7)
os.makedirs(out, exist_ok=True)
API = "https://api.cian.ru/search-offers/v2/search-offers-desktop/"
HDR = {"origin": "https://www.cian.ru", "referer": "https://www.cian.ru/", "accept-language": "ru-RU,ru;q=0.9"}
BASE = {"_type": "flatsale", "engine_version": {"type": "term", "value": 2}}
REGION = {"region": {"type": "terms", "value": [1]}, "building_status": {"type": "term", "value": 2}}
JK = {"geo": {"type": "geo", "value": [{"type": "newobject", "id": 4025502}]}}
SORT = {"sort": {"type": "term", "value": "price_object_order"}}
PRICE_WIDE = {"price": {"type": "range", "value": {"gte": 0, "lte": 3_000_000_000}}}
PRICE_REAL = {"price": {"type": "range", "value": {"gte": 10_000_000, "lte": 30_000_000}}}
VARIANTS = {
    "A region (как в soak)": {**BASE, **REGION, "room": {"type": "terms", "value": [1]}},
    "B region+sort": {**BASE, **REGION, **SORT},
    "C region+price 0..3e9": {**BASE, **REGION, **PRICE_WIDE},
    "D jk": {**BASE, **JK},
    "E jk+sort+price 0..3e9 (сборщик)": {**BASE, **JK, **SORT, **PRICE_WIDE},
    "F jk+sort+price 10..30M": {**BASE, **JK, **SORT, **PRICE_REAL},
}
plan = [v for v in VARIANTS for _ in range(reps)]
random.shuffle(plan)
S = requests.Session(impersonate="chrome")
log = []
for i, name in enumerate(plan):
    q = dict(VARIANTS[name], page={"type": "term", "value": 1})
    t0 = time.time()
    try:
        r = S.post(API, json={"jsonQuery": q}, headers=HDR, timeout=60)
        ok = r.text.lstrip().startswith("{")
        code, size = r.status_code, len(r.content)
        if not ok:
            open(f"{out}/block-{i:02d}.html", "w").write(r.text)
            hdr = dict(r.headers)
        else:
            hdr = {}
    except Exception as e:
        ok, code, size, hdr = False, e.__class__.__name__, 0, {}
    rec = {"i": i, "variant": name, "ok": ok, "code": code, "size": size, "ms": round((time.time() - t0) * 1000),
           "set_cookie": [c for c in S.cookies.keys()], "block_headers": {k: v for k, v in hdr.items() if k.lower() in ("server", "x-request-id", "set-cookie", "content-type")}}
    log.append(rec)
    print(f"{i:02d} {'OK   ' if ok else 'BLOCK'} {code} {name}", flush=True)
    time.sleep(random.uniform(pmin, pmax))
json.dump(log, open(f"{out}/log.json", "w"), ensure_ascii=False, indent=1)
print("\nИтог по вариантам:")
for name in VARIANTS:
    rs = [r for r in log if r["variant"] == name]
    print(f"  {name}: {sum(r['ok'] for r in rs)}/{len(rs)} OK")
