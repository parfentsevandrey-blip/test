#!/usr/bin/env python3
"""Проверки оценки: устойчивость (убираем по одному бизнес-центру из аналогов) и чувствительность к допущениям.

python3 checks.py → commercial-data/checks.json (используется в отчёте, раздел «Проверка»)
"""
import json

import valuation as v

TITLES = {sk: s['title'] for sk, s in v.SUBJECTS.items()}


def totals(out):
    return {sk: sum(u['total'] for u in S['units_val']) for sk, S in out['subjects'].items()}


def with_params(**kw):
    old = {k: getattr(v, k) for k in kw}
    for k, val in kw.items():
        setattr(v, k, val)
    try:
        return totals(v.compute())
    finally:
        for k, val in old.items():
            setattr(v, k, val)


def scen(**kw):
    old = {k: dict(p) for k, p in v.SCENARIOS.items()}
    v.SCENARIOS[v.MAIN].update(kw)
    try:
        return totals(v.compute())
    finally:
        v.SCENARIOS.clear()
        v.SCENARIOS.update(old)


def weak_as_all():
    orig = v.market_yields
    v.market_yields = lambda: dict(orig(), weak=None)
    try:
        return totals(v.compute())
    finally:
        v.market_yields = orig


def main():
    base_out = v.compute()
    base = totals(base_out)
    out = {'base': base, 'sens': [], 'jack': {}}
    cases = [('Поправка за качество здания +35%/балл вместо +52%', lambda: with_params(K_BLD=0.30)),
             ('Поправка за качество здания +65%/балл', lambda: with_params(K_BLD=0.50)),
             ('Оценка зданий объектов на 0,25 балла ниже', lambda: totals(v.compute(rating_shift=-0.25))),
             ('Оценка зданий объектов на 0,25 балла выше', lambda: totals(v.compute(rating_shift=+0.25))),
             ('Удалённость от центра −9%/км (как в аренде)', lambda: with_params(K_LOC=9.0)),
             ('Без поправки на завышение аренды слабых зданий', lambda: with_params(RENT_BIAS_WEAK=0.0)),
             ('Доходность для слабых зданий как для всех (около 9%)', lambda: weak_as_all()),
             ('Доходность сделки на 0,5 п.п. ниже', lambda: scen(cap_add=0.0)),
             ('Доходность сделки на 0,5 п.п. выше', lambda: scen(cap_add=0.01)),
             ('Торг 10% вместо 13%', lambda: scen(bargain=0.10)),
             ('Торг 16% вместо 13%', lambda: scen(bargain=0.16))]
    for name, f in cases:
        t = f()
        out['sens'].append(dict(name=name, **{sk: round((t[sk] / base[sk] - 1) * 100, 1) for sk in base}))
        print(name, {sk: f'{(t[sk] / base[sk] - 1) * 100:+.1f}%' for sk in base}, flush=True)
    # устойчивость: убираем по одному зданию-аналогу (продажа и аренда K1), смотрим изменение итога
    for sk, S in base_out['subjects'].items():
        groups = {}
        for res in S['comps'].values():
            for key in ('sale_K1', 'rent_K1'):
                for c in res[key]['comps']:
                    if c.get('used') and not c['same']:
                        g = c['bc_url'] or c['address']
                        groups[g] = c.get('bc') or c['address']
        worst = []
        for g, name in groups.items():
            t = totals(v.compute(drop=lambda s, c, g=g, sk=sk: s == sk and (c['bc_url'] or c['address']) == g))[sk]
            worst.append((abs(t / base[sk] - 1), round((t / base[sk] - 1) * 100, 1), name))
        worst.sort(reverse=True)
        out['jack'][sk] = dict(n=len(groups), top=[dict(change=w[1], bc=w[2]) for w in worst[:3]])
        print(sk, len(groups), worst[:3], flush=True)
    json.dump(out, open('commercial-data/checks.json', 'w'), ensure_ascii=False, indent=1)


if __name__ == '__main__':
    main()
