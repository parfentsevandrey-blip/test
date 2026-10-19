"""Замер восстановления после блока Циана: раз в N секунд по одному запросу к трём точкам, до снятия блока.
usage: python tests/cooldown.py [INTERVAL_S] [MAX_MIN]"""
import re, sys, time
from curl_cffi import requests

interval = int(sys.argv[1]) if len(sys.argv) > 1 else 300
max_min = int(sys.argv[2]) if len(sys.argv) > 2 else 60
H = {"origin": "https://www.cian.ru", "referer": "https://www.cian.ru/"}
CHECKS = {
    "html_catalog": lambda: requests.get("https://www.cian.ru/newobjects/list/?deal_type=sale&engine_version=2&offer_type=newobject&region=1",
                                         impersonate="chrome", timeout=60),
    "api_search": lambda: requests.post("https://api.cian.ru/search-offers/v2/search-offers-desktop/", impersonate="chrome", headers=H, timeout=60,
                                        json={"jsonQuery": {"_type": "flatsale", "engine_version": {"type": "term", "value": 2},
                                                            "geo": {"type": "geo", "value": [{"type": "newobject", "id": 4025502}]}}}),
    "api_newbuildings": lambda: requests.post("https://api.cian.ru/newbuilding-search/v1/get-newbuildings-for-serp/", impersonate="chrome", headers=H, timeout=60,
                                              json={"jsonQuery": {"_type": "newobjectsale", "engine_version": {"type": "term", "value": 2},
                                                                  "region": {"type": "terms", "value": [1]}}, "uri": "/newobjects/list/",
                                                    "subdomain": "www", "offset": 0, "count": 25}),
}
t0 = time.time()
while True:
    state = {}
    for name, fn in CHECKS.items():
        try:
            r = fn()
            title = re.search(r"<title>(.*?)</title>", r.text[:5000], re.S)
            state[name] = "OK" if r.status_code == 200 and not (title and re.search("Captcha|Verification", title.group(1))) else f"BLOCK {r.status_code}"
        except Exception as e:
            state[name] = f"ERR {e.__class__.__name__}"
        time.sleep(2)
    print(f"t+{(time.time() - t0) / 60:.0f}min {state}", flush=True)
    if all(v == "OK" for v in state.values()) or time.time() - t0 > max_min * 60:
        break
    time.sleep(interval)
