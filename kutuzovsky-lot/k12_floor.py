"""Сколько рынок реально платит за этаж — эмпирическая надбавка.

Регрессия ln(цена за м²) = a + b·этаж + c·ln(площадь) по каждому проекту.
Наш дом (12 лотов) для этого мал, поэтому градиент берётся у соседей.
Корректны только малоэтажные проекты: в 85-этажной башне прирост на этаж
структурно другой, чем в 11-этажном доме.
"""
import json, re, math
import numpy as np

L = json.load(open('k12_linked.json'))
OUR_PPM, TWIN_PPM, OUR_FL, TWIN_FL = 1_710_526, 1_400_000, 6, 2
OUR_PRICE, TWIN_PRICE, AREA = 162_500_000, 133_000_000, 95.0

def fl(x):
    m = re.match(r'(\d+)\s*/\s*(\d+)', x.get('floor') or '')
    return (int(m.group(1)), int(m.group(2))) if m else (None, None)

def fit(lots):
    r = [(fl(x)[0], x['area'], x['ppm']) for x in lots if fl(x)[0] and x['ppm'] and x['area']]
    f = np.array([q[0] for q in r], float)
    X = np.column_stack([np.ones_like(f), f, np.log([q[1] for q in r])])
    y = np.log([q[2] for q in r])
    b, *_ = np.linalg.lstsq(X, y, rcond=None)
    res = y - X @ b
    r2 = 1 - (res ** 2).sum() / ((y - y.mean()) ** 2).sum()
    return (math.exp(b[1]) - 1) * 100, r2, len(r), int(f.min()), int(f.max())

LOW = [x for n in ('Веспер Кутузовский', 'Бадаевский') for x in L[n] if (fl(x)[1] or 99) <= 19]
BASES = [('Веспер Кутузовский', L['Веспер Кутузовский'], 'элит · Кутузовский пр-т, 12с5 · 7–19 эт.'),
         ('Бадаевский',         L['Бадаевский'],         'элит · Украинский б-р, 2 · 18 эт.'),
         ('Малоэтажные вместе', LOW,                     'оба проекта, дома до 19 этажей'),
         ('Дом Дау',            L['Дом Дау'],            'премиум · башня 85–91 эт.'),
         ('Апартаменты Сити',   L['Апартаменты Сити'],   'апартаменты · башни 60–97 эт.')]

rows, out = [], {}
for name, lots, sub in BASES:
    per, r2, n, lo, hi = fit(lots)
    rows.append([name, sub, str(n), f'{lo}–{hi}', f'{per:+.2f} %'.replace('.', ','), f'{r2:.2f}'.replace('.', ',')])
    out[name] = {'per': per, 'r2': r2, 'n': n, 'lo': lo, 'hi': hi, 'sub': sub}

D = OUR_FL - TWIN_FL
ask = OUR_PPM / TWIN_PPM - 1
lo_g = min(out['Веспер Кутузовский']['per'], out['Малоэтажные вместе']['per'])
hi_g = out['Бадаевский']['per']
band = [(1 + g / 100) ** D - 1 for g in (lo_g, out['Малоэтажные вместе']['per'], hi_g)]
fair = [TWIN_PRICE * (1 + b) for b in band]

out['_calc'] = {'delta': D, 'ask': ask, 'band': band, 'fair': fair,
                'gap': [OUR_PRICE - f for f in fair]}
K = json.load(open('k12_tables.json'))
K['floor'], K['floorRows'] = out, rows
json.dump(K, open('k12_tables.json', 'w'), ensure_ascii=False, indent=1)

for r in rows: print(' | '.join(r))
print(f'\nразница этажей: {D}   лот просит {ask*100:+.1f} % к близнецу на {TWIN_FL}-м этаже')
print('рынок за эти этажи: ' + ', '.join(f'{b*100:+.1f} %' for b in band))
print('справедливо к близнецу: ' + ', '.join(f'{f/1e6:.1f} млн' for f in fair))
print('переплата: ' + ', '.join(f'{(OUR_PRICE-f)/1e6:.1f} млн' for f in fair))
