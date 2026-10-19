"""Проверка истории объявлений в доме на вторичке через браузерный транспорт. usage: hist_probe.py [DELAY_S]"""
import json, sys, time

sys.path.insert(0, __file__.rsplit("/tests/", 1)[0] + "/collectors")
import cian_browser as cb  # noqa: E402

time.sleep(int(sys.argv[1]) if len(sys.argv) > 1 else 0)
API = "https://api.cian.ru/search-offers/v2/search-offers-desktop/"
with cb.CianBrowser() as b:
    try:
        q = {"_type": "flatsale", "engine_version": {"type": "term", "value": 2}, "region": {"type": "terms", "value": [1]},
             "building_status": {"type": "term", "value": 1}, "page": {"type": "term", "value": 1}}
        _, d = b.api(API, {"jsonQuery": q})
        for o in d["data"]["offersSerialized"][:4]:
            house = [a for a in o["geo"]["address"] if a.get("geoType") == "house"]
            h = cb.house_history(b, o["id"], per_page=20)
            print("вторичка", o["id"], "house_geo", bool(house), "hist", h.get("totalCount"), h.get("statusCounts"), flush=True)
            if h.get("offers"):
                json.dump(h, open(f"data/demand/hist-browser-{o['id']}.json", "w"), ensure_ascii=False, indent=1)
                print(json.dumps(h["offers"][0], ensure_ascii=False)[:800])
                break
    except cb.StopCaptcha as e:
        print("STOP captcha:", e)
    print("api calls", b.n_api)
