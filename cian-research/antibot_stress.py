#!/usr/bin/env python3
"""Стресс-тест антибота ЦИАН: (1) какие профили impersonate пропускаются, (2) предельный темп одной сессии."""
import json
import sqlite3
import sys
import time
from concurrent.futures import ThreadPoolExecutor

import antibot_test as at
from scrapling.fetchers import FetcherSession


def profile_check(ids):
    out = {}
    for prof in at.PROFILES + ['chrome', 'firefox', 'safari']:
        at.wait_clear()
        good = 0
        with FetcherSession(impersonate=prof, stealthy_headers=True, retries=1) as s:
            for cid in ids[:3]:
                try:
                    good += at.ok(s.get(at.CARD.format(cid)))
                except Exception:
                    pass
                time.sleep(1)
        out[prof] = f'{good}/3'
        print(f'  профиль {prof}: {good}/3', flush=True)
    return out


def ramp(name, ids, workers, delay, limit=200):
    at.wait_clear()
    sessions = [FetcherSession(impersonate='chrome', stealthy_headers=True, retries=1).__enter__() for _ in range(workers)]
    t0, res = time.time(), []

    def one(k_cid):
        k, cid = k_cid
        try:
            g = at.ok(sessions[k % workers].get(at.CARD.format(cid)))
        except Exception:
            g = False
        time.sleep(delay)
        return g

    with ThreadPoolExecutor(workers) as ex:
        for g in ex.map(one, enumerate(ids[:limit])):
            res.append(g)
            if len(res) >= 6 and not any(res[-3:]):
                break
    first = next((i + 1 for i, g in enumerate(res) if not g), None)
    r = dict(test=name, requests=len(res), ok=sum(res), first_block=first, seconds=round(time.time() - t0),
             rate_per_min=round(60 * len(res) / (time.time() - t0), 1))
    t1 = time.time()
    at.wait_clear(1800)
    r['cooldown_after'] = round(time.time() - t1)
    print('  ', json.dumps(r, ensure_ascii=False), flush=True)
    return r


def main():
    ids = [r[0] for r in sqlite3.connect(sys.argv[1]).execute('select cian_id from listing order by random()')]
    at.PROBE_ID = ids.pop()
    out = {'profiles': profile_check(ids[:3])}
    out['ramp'] = [ramp('одна сессия, без пауз', ids[3:203], 1, 0.0),
                   ramp('4 потока, без пауз', ids[3:203][::-1], 4, 0.0)]
    json.dump(out, open('antibot-stress.json', 'w'), ensure_ascii=False, indent=1)


if __name__ == '__main__':
    main()
