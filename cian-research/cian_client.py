"""Вежливый клиент ЦИАН на Scrapling — методика работы без антибота (ANTIBOT.md).

Правила, проверенные экспериментом (antibot_test.py, antibot_stress.py):
1. Сначала API (api.cian.ru): поиск, история домов, просмотры — HTML www.cian.ru только когда без него никак.
2. Только десктопные профили chrome/safari: firefox, edge, android и ios блокируются сразу (0 из 3).
3. Один стабильный отпечаток и одна сессия с cookies на весь запуск, без ротации профилей.
4. Один процесс на IP: файловый замок не даёт двум сборщикам ходить на ЦИАН одновременно.
5. Темп: www — не чаще 1 запроса в 1,2 с, API — 1 в 0,3 с (запас относительно проверенных 58–194/мин).
6. При блокировке (редирект на 127.0.0.1, капча, ответ без данных): пауза, пробный запрос раз в 30 с,
   продолжение после снятия; если не снимается 15 минут — запуск прерывается, данные остаются в базе.
"""
import fcntl
import json
import logging
import os
import random
import time

logging.getLogger('scrapling').setLevel(logging.CRITICAL)
from scrapling.fetchers import FetcherSession  # noqa: E402

PROFILE = os.environ.get('CIAN_PROFILE', 'chrome')     # проверены: chrome, chrome136, chrome142, safari, safari184
WWW_INTERVAL, API_INTERVAL = 1.2, 0.3
PROBE_EVERY, GIVE_UP = 30, 900
LOCK_PATH = os.environ.get('CIAN_LOCK', '/tmp/cian-client.lock')


class Blocked(Exception):
    pass


class CianClient:
    def __init__(self, log=print):
        self.lock = open(LOCK_PATH, 'w')
        try:
            fcntl.flock(self.lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise SystemExit('Другой процесс уже работает с ЦИАН с этого IP — дождитесь его окончания.')
        self.s = FetcherSession(impersonate=PROFILE, stealthy_headers=True, retries=1, follow_redirects='safe').__enter__()
        self.last = {'www': 0.0, 'api': 0.0}
        self.log = log
        self.stats = dict(www=0, api=0, blocks=0, blocked_seconds=0.0)

    def _pace(self, kind):
        gap = (WWW_INTERVAL if kind == 'www' else API_INTERVAL) * random.uniform(0.9, 1.3)
        wait = self.last[kind] + gap - time.time()
        if wait > 0:
            time.sleep(wait)
        self.last[kind] = time.time()

    def _call(self, kind, fn, valid):
        for attempt in range(3):
            self._pace(kind)
            try:
                r = fn()
                if valid(r):
                    self.stats[kind] += 1
                    return r
            except Exception as e:
                if '127.0.0.1' not in str(e) and 'captcha' not in str(e).lower() and attempt < 2:
                    time.sleep(3)       # сетевой сбой — короткий повтор
                    continue
            self._wait_unblock(kind, fn, valid)
        raise Blocked(kind)

    def _wait_unblock(self, kind, fn, valid):
        self.stats['blocks'] += 1
        t0 = time.time()
        self.log(f'[антибот] блокировка {kind}, жду снятия…')
        while time.time() - t0 < GIVE_UP:
            time.sleep(PROBE_EVERY)
            try:
                if valid(fn()):
                    break
            except Exception:
                pass
        else:
            self.stats['blocked_seconds'] += time.time() - t0
            raise Blocked(f'{kind}: не снялась за {GIVE_UP} с')
        self.stats['blocked_seconds'] += time.time() - t0
        self.log(f'[антибот] снялась через {time.time() - t0:.0f} с')

    # --- публичные методы ---
    def page(self, url):
        """HTML www.cian.ru (карточка объявления). Возвращает текст страницы."""
        r = self._call('www', lambda: self.s.get(url),
                       lambda r: r.status in (200, 404) and 'captcha' not in str(r.url)
                       and ('"offerData"' in r.html_content or r.status == 404))
        return r.html_content

    def api_get(self, url, params=None):
        return self._call('api', lambda: self.s.get(url, params=params), lambda r: r.status in (200, 400)).json()

    def api_post(self, url, payload):
        return self._call('api', lambda: self.s.post(url, json=payload), lambda r: r.status in (200, 400)).json()

    def close(self):
        self.s.__exit__(None, None, None)
        fcntl.flock(self.lock, fcntl.LOCK_UN)


_client = None


def client():
    global _client
    if _client is None:
        _client = CianClient()
    return _client
