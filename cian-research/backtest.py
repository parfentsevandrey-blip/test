#!/usr/bin/env python3
"""Обратная проверка шкалы поправок (leave-one-building-out).

Для каждого объявления K1 в зданиях с оценкой по фото: «притворяемся», что это наш объект, и оцениваем его по объявлениям
ДРУГИХ зданий той же локации той же процедурой, что и объекты (поправки, один голос на здание, отсечение выбросов).
Сравниваем с фактической ценой (продажа — после торга 8%, аренда — чистая ставка). Ошибка — в процентах, отдельно
для слабых зданий (оценка ≤ 3,25, как «Калейдоскоп» и Imagine Plaza).

python3 backtest.py → таблица ошибок для наборов коэффициентов + commercial-data/backtest.json
"""
import json
import math
import statistics as st

import valuation as v

ATTR = {}


def attrs(r):
    if r['id'] not in ATTR:
        ATTR[r['id']] = v.comp_attrs(r)
    return ATTR[r['id']]


def predict(lot, pool, deal):
    a = attrs(lot)
    S = dict(center_km=a['center_km'], walk=a['walk'], rating=a['rating'], url=None, same=None)
    u = dict(area=lot['area'], floor=lot['floor'], entrance='street' if a['sep'] else 'lobby', fit='standard')
    key = lot.get('bc_url') or lot['address']
    comps = []
    for i in pool:
        r = v.rows[i]
        if (r.get('bc_url') or r['address']) == key or not (u['area'] / 4 <= r['area'] <= u['area'] * 4):
            continue
        c = v.comp_row(S, u, i, deal)
        c['same'] = False
        comps.append(c)
    return v.reconcile(comps)['value'] if comps else None


def run(params):
    for k, val in params.items():
        setattr(v, k, val)
    errs = {'sale': [], 'rent': []}
    seen = set()
    for sk in v.CL:
        for deal in ('sale', 'rent'):
            ids = [i for i in v.CL[sk]['clusters']['K1'][f'{deal}_ids'] if i not in v.EXCLUDE and not v.rows[i].get('offplan')
                   and not v.rows[i].get('nonmarket') and v.sane(v.rows[i]) and v.rows[i]['cluster'] == 'K1']
            for i in ids:
                lot = v.rows[i]
                if (deal, i) in seen or attrs(lot)['rating_src'] != 'фото':
                    continue
                seen.add((deal, i))
                p = predict(lot, ids, deal)
                if not p:
                    continue
                fact = lot['ex_vat'] * v.BARGAIN if deal == 'sale' else lot['net']
                errs[deal].append((math.log(p / fact), attrs(lot)['rating'], lot.get('bc_url') or lot['address']))
    out = {}
    for deal, e in errs.items():
        # один голос на здание: средняя ошибка по зданию
        bld = {}
        for x, rt, b in e:
            bld.setdefault(b, ([], rt))[0].append(x)
        per = [(st.mean(xs), rt) for xs, rt in bld.values()]
        low = [x for x, rt in per if rt <= 3.25]
        high = [x for x, rt in per if rt >= 4]
        out[deal] = dict(n_bld=len(per), mae=round(st.median(abs(x) for x, _ in per) * 100, 1), bias=round(st.mean(x for x, _ in per) * 100, 1),
                         bias_low=round(st.mean(low) * 100, 1) if low else None, n_low=len(low),
                         bias_high=round(st.mean(high) * 100, 1) if high else None, n_high=len(high))
    return out


# прежняя шкала (6%/балл, среднее арифметическое — см. ARITH) и итоговая
GRID = [dict(K_BLD=0.06, CAP_BLD=18, K_LOC=4, MAX_GROSS=0.6, GEO=False), dict(K_BLD=0.42, CAP_BLD=70, K_LOC=5, MAX_GROSS=0.9, GEO=True)]

if __name__ == '__main__':
    res = []
    for g in GRID:
        r = run(g)
        res.append(dict(params=g, **r))
        print(g, '| продажа: ошибка', r['sale']['mae'], '% , смещение слабых', r['sale']['bias_low'], f"(n={r['sale']['n_low']})",
              'сильных', r['sale']['bias_high'], 'всех', r['sale']['bias'], '| аренда: ошибка', r['rent']['mae'], '% , смещение слабых', r['rent']['bias_low'],
              f"(n={r['rent']['n_low']})", 'сильных', r['rent']['bias_high'], 'всех', r['rent']['bias'], '| зданий', r['sale']['n_bld'], r['rent']['n_bld'], flush=True)
    json.dump(res, open('commercial-data/backtest.json', 'w'), ensure_ascii=False, indent=1)
