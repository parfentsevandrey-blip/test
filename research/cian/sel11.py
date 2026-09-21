#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Выборка сопоставимых предложений, отобранная собственником вручную.

Одиннадцать карточек ЦИАН, проверенных по ссылке. Все производные цифры
раздела 3 считаются здесь и уходят в sel11.js, чтобы в тексте документа
не было ни одного числа, вбитого руками.

Ставка. У ЦИАН в коммерции цена бывает трёх видов, и без типа цены
выборка читается неверно: 2 500 в карточке — это не 2 500 ₽ за месяц
за помещение, а 2 500 ₽ за м² в месяц. Тип берётся из поля карточки;
там, где поля нет, цена трактуется как сумма за месяц и это помечается.
"""
import json, glob, math, statistics as st
from datetime import date

ASOF = date(2026, 9, 21)
OBJ = (55.75742, 37.238906)            # с. Ильинское, ул. Ленина, 28

IDS = [329868661, 277458208, 333822157, 327894962, 290241753, 331957732,
       333311589, 283508170, 329788014, 329788329, 322849091]

# у четырёх карточек адрес не заполнен — подписываем по координатам и содержанию
FALLBACK = {329868661: 'с. Ильинское, адрес в карточке не указан',
            277458208: 'с. Ильинское, офисный центр «Ильинский»',
            329788014: 'Ильинское-Усово, дом 9 этажей',
            329788329: 'Ильинское-Усово, дом 9 этажей'}

OURS = [('№1', 430.5, 2, 499_000), ('№2', 39.6, 2, 129_000), ('№3', 45.3, 3, 98_000)]

def km(a, b):
    return math.hypot((a[0]-b[0])*111, (a[1]-b[1])*111*math.cos(math.radians(a[0])))

def load():
    lots = {}
    for pat in ('cianwork/okrug.json', 'cianwork/ilinskoe.json',
                'cianwork/loc/*.json', 'cianwork/loc2/*.json'):
        for f in sorted(glob.glob(pat)):
            for l in json.load(open(f))['lots']:
                prev = lots.get(l['id'])
                if prev and not l.get('priceType') and prev.get('priceType'):
                    continue
                lots[l['id']] = l
    return lots

def build():
    lots, out = load(), []
    for i in IDS:
        l = lots[i]
        a, p = l['totalArea'], l['priceRub']
        pt, pp = l.get('priceType'), l.get('paymentPeriod')
        if pt == 'squareMeter' and pp == 'annual':
            rate, rent, kind, sure = p, p*a/12, 'за м² в год', True
        elif pt == 'squareMeter':
            rate, rent, kind, sure = p*12, p*a, 'за м² в месяц', True
        elif pt:
            rate, rent, kind, sure = p*12/a, p, 'за месяц', True
        else:
            rate, rent, kind, sure = p*12/a, p, 'за месяц', False
        y, m, d = map(int, l['created'].split('-'))
        addr = ' '.join(x for x in (l.get('street'), l.get('house')) if x) or FALLBACK.get(i, '—')
        out.append({'id': i, 'addr': addr, 'area': a, 'floor': l.get('floor'), 'floors': l.get('floors'),
                    'rate': round(rate), 'rent': round(rent), 'kind': kind, 'sure': sure,
                    'days': (ASOF - date(y, m, d)).days, 'created': l['created'],
                    'vat': l.get('vatType'), 'km': round(km((l['lat'], l['lng']), OBJ), 1),
                    'url': l['url']})
    return sorted(out, key=lambda x: (x['floor'] or 0, x['rate']))

def stats(sel):
    f = lambda k: [x for x in sel if (x['floor'] or 0) == k]
    f1, f2, f3 = f(1), f(2), [x for x in sel if (x['floor'] or 0) >= 3]
    med = lambda r: round(st.median([x['rate'] for x in r])) if r else None
    medd = lambda r: round(st.median([x['days'] for x in r])) if r else None
    # на втором этаже выборка делится по цене: что стоит месяцы и что стоит годы
    fast = [x for x in f2 if x['days'] <= 365]
    slow = [x for x in f2 if x['days'] > 365]
    S = {'n': len(sel),
         'f1': {'n': len(f1), 'med': med(f1), 'medd': medd(f1),
                'lo': min(x['rate'] for x in f1), 'hi': max(x['rate'] for x in f1)},
         'f2': {'n': len(f2), 'med': med(f2), 'medd': medd(f2),
                'lo': min(x['rate'] for x in f2), 'hi': max(x['rate'] for x in f2)},
         'f3': {'n': len(f3), 'med': med(f3), 'medd': medd(f3)},
         'fast': {'n': len(fast), 'lo': min(x['rate'] for x in fast), 'hi': max(x['rate'] for x in fast),
                  'medd': medd(fast)},
         'slow': {'n': len(slow), 'lo': min(x['rate'] for x in slow), 'hi': max(x['rate'] for x in slow),
                  'medd': medd(slow)},
         'coef': round(med(f2)/med(f1), 2),
         'sure': sum(1 for x in sel if x['sure']),
         'big_gf': next(x for x in f1 if x['area'] >= 150),
         'big_up': f3[0],
         'gf_cheapest': min(f1, key=lambda x: x['rate']),
         'gf_dearest': max(f1, key=lambda x: x['rate']),
         }
    S['ours'] = {name: {'area': a, 'floor': fl, 'rent': r, 'rate': round(r*12/a)}
                 for name, a, fl, r in OURS}
    o = S['ours']
    S['gap2'] = round((o['№2']['rate']/S['f2']['med'] - 1) * 100)
    S['gap3'] = round((o['№3']['rate']/S['f3']['med'] - 1) * 100)
    S['rel1'] = round(o['№1']['rate']/S['f3']['med'], 2)
    return S

if __name__ == '__main__':
    sel = build(); S = stats(sel)
    print(f"{'id':>10} {'адрес':34} {'эт':>5} {'м²':>7} {'₽/мес':>9} {'₽/м²/год':>9} {'дней':>5}  цена в карточке")
    for o in sel:
        print(f"{o['id']:>10} {o['addr'][:34]:34} {str(o['floor'])+'/'+str(o['floors']):>5} "
              f"{o['area']:>7} {o['rent']:>9} {o['rate']:>9} {o['days']:>5}  {o['kind']}"
              f"{'' if o['sure'] else '  (тип цены не указан)'}")
    print(f"\n1-й этаж: n={S['f1']['n']} медиана {S['f1']['med']} ({S['f1']['lo']}–{S['f1']['hi']}), экспозиция {S['f1']['medd']} дн")
    print(f"2-й этаж: n={S['f2']['n']} медиана {S['f2']['med']} ({S['f2']['lo']}–{S['f2']['hi']}), экспозиция {S['f2']['medd']} дн")
    print(f"3-й этаж: n={S['f3']['n']} {S['f3']['med']}, экспозиция {S['f3']['medd']} дн")
    print(f"коэффициент этажа: {S['coef']}")
    print(f"2-й этаж, до года: {S['fast']['n']} лота по {S['fast']['lo']}–{S['fast']['hi']}, медиана срока {S['fast']['medd']} дн")
    print(f"2-й этаж, дольше года: {S['slow']['n']} лота по {S['slow']['lo']}–{S['slow']['hi']}, медиана срока {S['slow']['medd']} дн")
    print(f"наши: №2 +{S['gap2']}% к медиане этажа; №3 +{S['gap3']}%; №1 ×{S['rel1']} к единственному верхнему крупному")
    open('sel11.js', 'w', encoding='utf-8').write(
        'module.exports = ' + json.dumps({'lots': sel, 'stats': S}, ensure_ascii=False, indent=1) + ';\n')
    print('\nsel11.js записан')
