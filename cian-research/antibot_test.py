#!/usr/bin/env python3
"""Эксперимент: какие режимы Scrapling реже попадают под антибот ЦИАН на карточках объявлений.

Для каждой стратегии: дождаться снятия блокировки, затем запросить до N разных карточек,
останавливаясь после 3 блокировок подряд. Блокировка — редирект на 127.0.0.1, капча или страница без offerData.
Результат — antibot-results.json и таблица в консоли.
"""
import json
import logging
import random
import sqlite3
import sys
import time

logging.getLogger('scrapling').setLevel(logging.CRITICAL)
from scrapling.fetchers import Fetcher, FetcherSession  # noqa: E402

CARD = 'https://www.cian.ru/sale/flat/{}/'
PROFILES = ['chrome136', 'chrome142', 'safari184', 'firefox144', 'edge101', 'chrome131_android', 'safari180_ios']


def ok(resp):
    try:
        return resp is not None and resp.status == 200 and '"offerData"' in resp.html_content \
            and 'captcha' not in str(resp.url)
    except Exception:
        return False


def probe():
    try:
        return ok(Fetcher.get(CARD.format(PROBE_ID), retries=1))
    except Exception:
        return False


def wait_clear(max_wait=900):
    t0 = time.time()
    while time.time() - t0 < max_wait:
        if probe():
            return time.time() - t0
        time.sleep(30)
    return None


def run(name, ids, get, delay):
    res, streak, t0, first_block = [], 0, time.time(), None
    for n, cid in enumerate(ids, 1):
        try:
            good = ok(get(CARD.format(cid)))
        except Exception:
            good = False
        res.append(good)
        if not good and first_block is None:
            first_block = n
        streak = 0 if good else streak + 1
        if streak >= 3:
            break
        time.sleep(delay())
    return dict(strategy=name, requests=len(res), ok=sum(res), share=round(sum(res) / len(res), 2),
                first_block=first_block, seconds=round(time.time() - t0), rate_per_min=round(60 * len(res) / (time.time() - t0), 1))


def main():
    global PROBE_ID
    db, n = sys.argv[1], int(sys.argv[2]) if len(sys.argv) > 2 else 40
    ids = [r[0] for r in sqlite3.connect(db).execute('select cian_id from listing order by random()')]
    PROBE_ID = ids.pop()
    chunks = [ids[i * n:(i + 1) * n] for i in range(5)]

    sess = FetcherSession(impersonate='chrome', stealthy_headers=True, retries=1).__enter__()

    def warm_session():
        s = FetcherSession(impersonate='chrome', stealthy_headers=True, retries=1).__enter__()
        s.get('https://www.cian.ru/')
        time.sleep(2)
        s.get('https://www.cian.ru/cat.php?deal_type=sale&engine_version=2&offer_type=flat&region=1&district%5B0%5D=100')
        time.sleep(2)
        return s
    warm = {}

    def warm_get(u):
        if 's' not in warm:
            warm['s'] = warm_session()
        return warm['s'].get(u, headers={'Referer': 'https://www.cian.ru/cat.php?deal_type=sale&district%5B0%5D=100'
                                                    '&engine_version=2&offer_type=flat&region=1'})

    def rotate_get(u):
        with FetcherSession(impersonate=random.choice(PROFILES), stealthy_headers=True, retries=1) as s:
            return s.get(u)

    strategies = [
        ('S1 новый клиент, 1 с', lambda u: Fetcher.get(u, retries=1), lambda: 1.0),
        ('S2 сессия с cookies, 1 с', lambda u: sess.get(u), lambda: 1.0),
        ('S3 сессия + пауза 2,5–5 с', lambda u: sess.get(u), lambda: random.uniform(2.5, 5)),
        ('S4 прогрев + Referer, 1 с', warm_get, lambda: 1.0),
        ('S5 смена отпечатка, 1 с', rotate_get, lambda: 1.0),
    ]
    out = []
    for (name, get, delay), chunk in zip(strategies, chunks):
        waited = wait_clear()
        print(f'{name}: блокировка снялась через {waited if waited is None else round(waited)} с', flush=True)
        r = run(name, chunk, get, delay)
        r['cooldown_before'] = waited
        out.append(r)
        print('  ', json.dumps(r, ensure_ascii=False), flush=True)
        json.dump(out, open('antibot-results.json', 'w'), ensure_ascii=False, indent=1)


if __name__ == '__main__':
    main()
