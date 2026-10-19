"""Тестовый сбор маркеров спроса по ЖК (первичка) через браузерный транспорт cian_browser.

Для каждого ЖК: снимок всех лотов (search-offers, sort=price_object_order) + статистика просмотров каждого лота
(get-offer-card-statistic: всего и по дням за 10 дней). Каждый ответ кэшируется в OUT/; повторный запуск
продолжает с места остановки. Первая капча -- остановка (правило рабочего проекта).

usage: python demand/collect.py OUT_DIR JK_ID [JK_ID ...]
"""
import json, os, sys, time

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "collectors"))
import cian_browser as cb  # noqa: E402

API = "https://api.cian.ru/search-offers/v2/search-offers-desktop/"
out, jks = sys.argv[1], [int(x) for x in sys.argv[2:]]
os.makedirs(f"{out}/snap", exist_ok=True)
os.makedirs(f"{out}/stats", exist_ok=True)


def cached(path, fn):
    if os.path.exists(path):
        return json.load(open(path))
    data = fn()
    json.dump(data, open(path, "w"), ensure_ascii=False)
    return data


with cb.CianBrowser() as b:
    try:
        for jk in jks:
            q = {"_type": "flatsale", "engine_version": {"type": "term", "value": 2},
                 "geo": {"type": "geo", "value": [{"type": "newobject", "id": jk}]},
                 "building_status": {"type": "term", "value": 2}, "sort": {"type": "term", "value": "price_object_order"}}
            offers, page, total = [], 1, None
            while True:
                d = cached(f"{out}/snap/{jk}-p{page}.json",
                           lambda: b.api(API, {"jsonQuery": dict(q, page={"type": "term", "value": page})})[1]["data"])
                total = d["offerCount"]
                offers += d["offersSerialized"]
                if not d["offersSerialized"] or len(offers) >= total or page >= 54:
                    break
                page += 1
            print(f"ЖК {jk}: снимок {len(offers)} из {total}", flush=True)
            for i, o in enumerate(offers, 1):
                cached(f"{out}/stats/{o['id']}.json", lambda: cb.offer_stats(b, o["id"], o["creationDate"]))
                if i % 25 == 0:
                    print(f"  ЖК {jk}: просмотры {i}/{len(offers)} (api {b.n_api})", flush=True)
            print(f"ЖК {jk}: готово, api {b.n_api}", flush=True)
    except cb.StopCaptcha as e:
        print("STOP captcha:", e, "-- перезапуск продолжит с места остановки", flush=True)
        sys.exit(2)
print("done", flush=True)
