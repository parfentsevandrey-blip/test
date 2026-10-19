"""Тестовый сбор маркеров спроса по ЖК (первичка).

Для каждого ЖК: снимок всех лотов (search-offers, sort=price_object_order) + статистика просмотров каждого лота
(get-offer-card-statistic: всего и по дням за 10 дней). Транспорт -- тот же, что собрал 57 тыс. лотов Москвы:
cian.Cian()._req (TLS Chrome, откаты 10-160 с на капчу, 5 попыток). Каждый ответ кэшируется в OUT/,
повторный запуск продолжает с места остановки; лот, не отданный за 5 попыток, повторяется в конце.

usage: python demand/collect.py OUT_DIR JK_ID [JK_ID ...]
"""
import json, os, sys, time

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "collectors"))
import cian  # noqa: E402

API = "https://api.cian.ru/search-offers/v2/search-offers-desktop/"
STAT = "https://api.cian.ru/offer-card/v1/get-offer-card-statistic/"
out, jks = sys.argv[1], [int(x) for x in sys.argv[2:]]
os.makedirs(f"{out}/snap", exist_ok=True)
os.makedirs(f"{out}/stats", exist_ok=True)
c = cian.Cian()


def cached(path, fn):
    if os.path.exists(path):
        return json.load(open(path))
    data = fn()
    json.dump(data, open(path, "w"), ensure_ascii=False)
    return data


def stats(o):
    return c._req("GET", f"{STAT}?offerId={o['id']}&offerCreationDate={o['creationDate'][:10]}", expect_json=True).json()


todo = []
for jk in jks:
    q = {"_type": "flatsale", "engine_version": {"type": "term", "value": 2},
         "geo": {"type": "geo", "value": [{"type": "newobject", "id": jk}]},
         "building_status": {"type": "term", "value": 2}, "sort": {"type": "term", "value": "price_object_order"}}
    offers, page = [], 1
    while True:
        d = cached(f"{out}/snap/{jk}-p{page}.json", lambda: c._page(q, page))
        offers += d["offersSerialized"]
        if not d["offersSerialized"] or len(offers) >= d["offerCount"] or page >= 54:
            break
        page += 1
    print(f"ЖК {jk}: снимок {len(offers)} из {d['offerCount']}", flush=True)
    todo += offers

failed = []
for i, o in enumerate(todo, 1):
    try:
        cached(f"{out}/stats/{o['id']}.json", lambda: stats(o))
    except Exception as e:
        failed.append(o)
    if i % 100 == 0:
        print(f"просмотры {i}/{len(todo)}", flush=True)
for o in failed:  # второй заход по неотданным
    try:
        cached(f"{out}/stats/{o['id']}.json", lambda: stats(o))
    except Exception:
        pass
n = sum(os.path.exists(f"{out}/stats/{o['id']}.json") for o in todo)
print(f"done: просмотры по {n} из {len(todo)} лотов", flush=True)
