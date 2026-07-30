"""Надбавка за этаж: регрессия ln(цена за м²) по этажу с поправкой на площадь."""
import json, re, math
import numpy as np

L = json.load(open('lev/lv_linked.json'))
OUR_FL, OUR_PPM = 15, 716_981

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

DEV = [x for x in L['Левел Академическая'] if x['seller'] == 'Застройщик']
BASES = [('Левел Академическая — застройщик', DEV,                  'до 19 этажей'),
         ('Левел Академическая — вся экспозиция', L['Левел Академическая'], 'до 21 этажа'),
         ('Файв Тауэрс — не брали',        L['Файв Тауэрс'],         '75 этажей'),
         ('Lunar',                         L['Lunar'],               'до 17 этажей')]

rows, out = [], {}
for name, lots, height in BASES:
    per, r2, n, lo, hi = fit(lots)
    rows.append([name, str(n), height, f'{per:+.1f} %'.replace('.', ',')])
    out[name] = {'per': per, 'r2': r2, 'n': n, 'lo': lo, 'hi': hi}
    print(f'{name:38s} n={n:3d}  этажи {lo}–{hi}  за этаж {per:+.2f} %  R²={r2:.2f}')

K = json.load(open('lev/lv_tables.json'))
K['floor'], K['floorRows'] = out, rows
json.dump(K, open('lev/lv_tables.json', 'w'), ensure_ascii=False, indent=1)
