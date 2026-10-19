"""Дозированный нагрузочный тест сборщиков: ступенчатый рост параллельности с остановкой при блоке.

Запросы — сырые (без ретраев сборщика), чтобы видеть реальное поведение сайта.
usage: python tests/stress.py {cian,domrf} [--levels 1,2,4,8] [--per-worker 12] [--cooldown 30] --out FILE.json
"""
import argparse, json, random, statistics, sys, threading, time
from urllib.parse import quote

from curl_cffi import requests

DOMRF = "https://xn--80az8a.xn--d1aqf.xn--p1ai"
CIAN_API = "https://api.cian.ru/search-offers/v2/search-offers-desktop/"


def domrf_call(s):
    """Чередуем три основных метода: список, compare, квартиры."""
    k = random.random()
    if k < 0.5:
        off = random.randrange(0, 13000, 50)
        url = f"{DOMRF}{quote('/сервисы/api/kn/object')}?offset={off}&limit=50&objStatus=0&sortField=objPublDt&sortType=desc"
        r = s.get(url, timeout=60)
        ok = r.status_code == 200 and r.text.startswith("{") and len(r.json()["data"]["list"]) > 0
    elif k < 0.8:
        ids = random.sample([73741, 56693, 13398, 74005, 73958, 73899, 73857, 73522, 73323, 73789], 10)
        url = f"{DOMRF}{quote('/сервисы/api/kn/compare')}?" + "&".join(f"objectId={i}" for i in ids) + "&diff=false"
        r = s.get(url, timeout=60)
        ok = r.status_code == 200 and r.text.startswith("{")
    else:
        r = s.post(f"{DOMRF}/portal-kn/api/sales/portal/flats",
                   json={"externalIds": [73741], "sort": "area", "order": "asc", "limit": 100, "offset": random.randrange(0, 400, 100)},
                   timeout=60)
        ok = r.status_code in (200, 201) and r.text.startswith("[")
    blocked = r.status_code == 403 or "servicepipe" in r.text[:3000] or "заблокирован" in r.text[:3000]
    return r.status_code, ok, blocked, len(r.content)


def cian_call(s):
    q = {"_type": "flatsale", "engine_version": {"type": "term", "value": 2}, "region": {"type": "terms", "value": [1]},
         "building_status": {"type": "term", "value": 2}, "page": {"type": "term", "value": random.randint(1, 54)},
         "room": {"type": "terms", "value": [random.randint(1, 3)]}}
    r = s.post(CIAN_API, json={"jsonQuery": q}, timeout=60,
               headers={"origin": "https://www.cian.ru", "referer": "https://www.cian.ru/"})
    ok = r.status_code == 200 and r.text.startswith("{") and "offersSerialized" in r.text[:200000]
    blocked = r.status_code in (403, 429) or "cian_waf_block" in r.text[:30000] or "captcha" in r.text[:3000].lower()
    return r.status_code, ok, blocked, len(r.content)


def run_level(site, n, per_worker):
    call = domrf_call if site == "domrf" else cian_call
    imp = "safari18_0" if site == "domrf" else "chrome"
    results, lock = [], threading.Lock()

    def worker():
        s = requests.Session(impersonate=imp)
        for _ in range(per_worker):
            t0 = time.time()
            try:
                code, ok, blocked, size = call(s)
                err = None
            except Exception as e:
                code, ok, blocked, size, err = None, False, False, 0, e.__class__.__name__
            with lock:
                results.append({"code": code, "ok": ok, "blocked": blocked, "ms": round((time.time() - t0) * 1000), "size": size, "err": err})
            if blocked:
                return

    t0 = time.time()
    threads = [threading.Thread(target=worker) for _ in range(n)]
    [t.start() for t in threads]
    [t.join() for t in threads]
    wall = time.time() - t0
    ms = sorted(r["ms"] for r in results)
    summary = {
        "workers": n, "requests": len(results), "wall_s": round(wall, 1), "rps": round(len(results) / wall, 2),
        "ok": sum(r["ok"] for r in results), "blocked": sum(r["blocked"] for r in results),
        "errors": sum(1 for r in results if r["err"]), "codes": {str(c): sum(1 for r in results if r["code"] == c) for c in {r["code"] for r in results}},
        "p50_ms": statistics.median(ms) if ms else None, "p95_ms": ms[int(len(ms) * 0.95) - 1] if ms else None, "max_ms": ms[-1] if ms else None,
    }
    return summary, results


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("site", choices=["cian", "domrf"])
    ap.add_argument("--levels", default="1,2,4,8")
    ap.add_argument("--per-worker", type=int, default=12)
    ap.add_argument("--cooldown", type=int, default=30)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    report = []
    for i, n in enumerate(int(x) for x in a.levels.split(",")):
        if i:
            time.sleep(a.cooldown)
        summary, raw = run_level(a.site, n, a.per_worker)
        print(json.dumps(summary, ensure_ascii=False), flush=True)
        report.append({"summary": summary, "raw": raw})
        if summary["blocked"] or summary["errors"] > summary["requests"] * 0.2:
            print(f"STOP: block/errors at {n} workers", flush=True)
            break
    json.dump(report, open(a.out, "w"), ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
