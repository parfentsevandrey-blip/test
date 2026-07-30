"""Надбавка за этаж: регрессия ln(цена за м²) по этажу с поправкой на площадь."""
import json, re, math
import numpy as np

L = json.load(open('hl/hl_linked.json'))
OUR_FL, OUR_PPM = 8, 767_386

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
    return (math.exp(b[1]) - 1) * 100, r2, len(r), int(f.min()), int(f.max()), b

HL = L['HIGH LIFE']
DEV = [x for x in HL if x['seller'] == 'Застройщик']
K1  = [x for x in HL if x['corp'] == 'K1 SOUL TOWER']
BASES = [('HIGH LIFE — прайс застройщика', DEV, 'башни 24–47 эт.'),
         ('HIGH LIFE — вся экспозиция',    HL,  'башни 24–47 эт.'),
         ('K1 SOUL TOWER — наш корпус',    K1,  '32 этажа'),
         ('«Эра» — для сравнения',         L['Эра'], 'башни 27–52 эт.')]
rows, out, coef = [], {}, {}
for name, lots, height in BASES:
    per, r2, n, lo, hi, b = fit(lots)
    coef[name] = b
    rows.append([name, str(n), height, f'{per:+.2f} %'.replace('.', ',')])
    out[name] = {'per': per, 'r2': r2, 'n': n, 'lo': lo, 'hi': hi}
    print(f'{name:34s} n={n:3d}  этажи {lo}–{hi}  за этаж {per:+.2f} %  R²={r2:.2f}')

# Справедливая цена метра для нашего лота по модели «этаж + площадь»
OUR_A = 125.1
model = {}
for name, b in coef.items():
    p = math.exp(b[0] + b[1] * OUR_FL + b[2] * math.log(OUR_A))
    model[name] = {'pred': p, 'delta': OUR_PPM / p - 1, 'deltaRub': (OUR_PPM - p) * OUR_A,
                   'areaEff': (1.1 ** b[2] - 1) * 100}
    print(f'  модель {name:32s} ожидает {p:>9,.0f} ₽/м²  →  факт {OUR_PPM / p - 1:+.1%}  '
          f'({(OUR_PPM - p) * OUR_A / 1e6:+.1f} млн ₽);  +10 % площади = {(1.1 ** b[2] - 1) * 100:+.1f} % к метру'.replace(',', ' '))

K = json.load(open('hl/hl_tables.json')) if __import__('os').path.exists('hl/hl_tables.json') else {}
K['floor'], K['floorRows'], K['model'] = out, rows, model
json.dump(K, open('hl/hl_tables.json', 'w'), ensure_ascii=False, indent=1)
