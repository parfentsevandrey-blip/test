"""Маркеры спроса Циана по объявлению: статистика просмотров и история объявлений в доме.

Эндпоинты найдены в бандле frontend-offer-card (2026-10-19):
  GET  https://api.cian.ru/offer-card/v1/get-offer-card-statistic/?offerId=&offerCreationDate=YYYY-MM-DD
  POST https://api.cian.ru/valuation-offer-history/v4/get-house-offer-history-desktop/
       {"offerId", "resultsOnPage", "page", "dealType": "sale", "offerStatus"?, "roomsCount"?, "orderBy"?}
Капча на облачных IP прерывистая (зависит от репутации адреса пула), поэтому каждый повтор -- новое соединение.
"""
import json, random, sys, time

from curl_cffi import requests

STAT = "https://api.cian.ru/offer-card/v1/get-offer-card-statistic/"
HIST = "https://api.cian.ru/valuation-offer-history/v4/get-house-offer-history-desktop/"
HDR = {"origin": "https://www.cian.ru", "referer": "https://www.cian.ru/", "accept-language": "ru-RU,ru;q=0.9"}


def call(method, url, attempts=8, delay=(2.0, 4.0), **kw):
    for i in range(attempts):
        time.sleep(random.uniform(*delay))
        try:
            r = requests.request(method, url, impersonate="chrome", headers=HDR, timeout=60, **kw)
        except Exception as e:
            print(f"  {e.__class__.__name__} -> retry", file=sys.stderr); continue
        if r.text.lstrip()[:1] in "{[" and r.text.strip():
            if r.status_code >= 400:
                raise RuntimeError(f"{r.status_code}: {r.text[:200]}")
            return r.json()
        time.sleep(10 + 10 * i)  # капча: пауза и новое соединение
    raise RuntimeError(f"gave up: {url}")


def offer_stats(offer_id, created):
    """created: '2025-02-19' или ISO-строка creationDate."""
    return call("GET", STAT, params={"offerId": offer_id, "offerCreationDate": str(created)[:10]})


def house_history(offer_id, page=1, per_page=50, **filters):
    body = {"offerId": offer_id, "resultsOnPage": per_page, "page": page, "dealType": "sale", **filters}
    return call("POST", HIST, json=body)


if __name__ == "__main__":
    oid, created = int(sys.argv[1]), sys.argv[2]
    print(json.dumps({"stats": offer_stats(oid, created), "history": house_history(oid)}, ensure_ascii=False, indent=1))
