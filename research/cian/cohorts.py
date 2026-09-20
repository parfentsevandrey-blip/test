#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Когорты и сводные цифры для документа → comps.js. Всё, что печатается
в тексте как число выборки, должно браться отсюда, а не вбиваться руками."""
import json, statistics as st, collections
z = json.load(open('zone_lots.json'))
meta = json.load(open('zone_dropped.json'))
CORR = {'Ильинское','Ильинское-Усово','Александровка','Бузланово','Глухово','Петрово-Дальнее','пос. Истра','Дмитровское'}
for l in z: l['corridor'] = l['near'] in CORR
def sel(p): return sorted([l for l in z if p(l)], key=lambda x: x['rate'])
def q(v, f): v = sorted(v); return v[int(f*(len(v)-1))]
def stat(rows):
    r = [x['rate'] for x in rows]; d = [x['days'] for x in rows if x['days'] is not None]
    return {'n': len(rows), 'med': round(st.median(r)) if r else None,
            'q1': q(r,.25) if r else None, 'q3': q(r,.75) if r else None,
            'medd': round(st.median(d)) if d else None,
            'over_year': len([x for x in d if x > 365]), 'nd': len(d)}
C = {
 'u1_up': sel(lambda l: 200 <= l['area'] <= 800 and (l['floor'] or 0) >= 2),
 'u1_gf': sel(lambda l: 200 <= l['area'] <= 800 and l['floor'] == 1),
 'u2_up': sel(lambda l: 15 <= l['area'] <= 70 and (l['floor'] or 0) >= 2),
 'u2_gf': sel(lambda l: 15 <= l['area'] <= 70 and l['floor'] == 1),
 'u3_hi': sel(lambda l: 25 <= l['area'] <= 120 and (l['floor'] or 0) >= 3),
 'zone_f0': sel(lambda l: l['floor'] is not None and l['floor'] < 1),
 'zone_f1': sel(lambda l: l['floor'] == 1),
 'zone_f2': sel(lambda l: l['floor'] == 2),
 'zone_f3': sel(lambda l: (l['floor'] or 0) >= 3),
 'zone_fx': sel(lambda l: l['floor'] is None),
}
S = {k: stat(v) for k, v in C.items()}
S['u2_up_corr'] = stat([l for l in C['u2_up'] if l['corridor']])
S['u2_gf_corr'] = stat([l for l in C['u2_gf'] if l['corridor']])
S['u3_hi_corr'] = stat([l for l in C['u3_hi'] if l['corridor']])
S['zone'] = stat(z)
S['zone_n'] = len(z)
S['total_fetched'] = meta['total_fetched']
S['sure'] = meta['sure']
S['guessed_year'] = [l['id'] for l in z if l['kind'] == 'м²/год' and not l['sure']]
S['dropped'] = meta['dropped']
S['settlements'] = dict(collections.Counter(l['near'] for l in z).most_common())
S['floor_coef_big'] = round(S['u1_up']['med'] / S['u1_gf']['med'], 2)
S['u3_derived'] = round(S['u2_up_corr']['med'] * S['floor_coef_big'] / 100) * 100
open('comps.js', 'w', encoding='utf-8').write('module.exports = ' + json.dumps({'cohorts': C, 'stats': S}, ensure_ascii=False, indent=1) + ';\n')
for k in ('u1_up','u1_gf','u2_up','u2_up_corr','u2_gf_corr','u3_hi','zone_f0','zone_f1','zone_f2','zone_f3','zone'):
    print(f"{k:12s} {S[k]}")
print('zone_n', S['zone_n'], '| выгружено', S['total_fetched'], '| тип цены из карточки', S['sure'],
      '| эвристика м²/год', len(S['guessed_year']), '| исключено', len(S['dropped']))
print('коэффициент этажа (крупные)', S['floor_coef_big'], '| расчётный уровень №3', S['u3_derived'])
