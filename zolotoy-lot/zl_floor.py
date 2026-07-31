"""Надбавка за этаж и скидка за площадь — регрессией, а не на глаз.

Модель: ln(цена за м²) = a + b·этаж + c·ln(площадь).
Отчитываем (e^b − 1)·100 % за этаж и (1,1^c − 1)·100 % за +10 % площади.

Важно: все базы — лоты БЕЗ отделки (в де-люксе застройщики продают бетон),
поэтому сравнивать модель нужно с «голой» ценой нашего лота 2 450 000 ₽/м²
(280,0 млн − 87,5 × 750 тыс.), а не с 3 200 000 ₽/м² с ремонтом.
"""
import json, re, math, sys, os
import numpy as np
sys.path.insert(0, os.path.dirname(__file__))
from zl_fin import grp, combo

L = json.load(open('zl/zl_linked.json'))
OUR_FL, OUR_A = 4, 87.5
OUR_PPM, OUR_BARE = 3_200_000, 2_450_000
OUR_URL = 'https://www.cian.ru/sale/flat/326035617/'

fl = lambda x: int(m.group(1)) if (m := re.match(r'(\d+)', x['floor'] or '')) else None
hi = lambda x: int(m.group(1)) if (m := re.search(r'/(\d+)', x['floor'] or '')) else None

def fit(lots):
    r = [(fl(x), x['area'], x['ppm']) for x in lots
         if fl(x) and x['ppm'] and x['area'] and not combo(x)]
    if len(r) < 8: return None
    f = np.array([q[0] for q in r], float)
    X = np.column_stack([np.ones_like(f), f, np.log([q[1] for q in r])])
    y = np.log([q[2] for q in r])
    b, *_ = np.linalg.lstsq(X, y, rcond=None)
    res = y - X @ b
    r2 = 1 - (res ** 2).sum() / ((y - y.mean()) ** 2).sum()
    pred = math.exp(b[0] + b[1] * OUR_FL + b[2] * math.log(OUR_A))
    return {'n': len(r), 'lo': int(f.min()), 'hi': int(f.max()),
            'per': (math.exp(b[1]) - 1) * 100, 'area': (1.1 ** b[2] - 1) * 100,
            'r2': r2, 'pred': pred}

dev   = lambda n: [x for x in L[n] if x['seller'] == 'Застройщик']
shell = lambda n: [x for x in L[n] if grp(x) == 'shell']
POOL  = [x for n in ('Дом Лаврушинский', 'Клубный дом DUO', 'Клубный дом Космо 4/22',
                     'Русские Сезоны', 'Золотой') for x in L[n] if grp(x) == 'shell']

BASES = [
    ('«Дом Лаврушинский» — прайс застройщика', dev('Дом Лаврушинский')),
    ('«Клубный дом DUO» — прайс застройщика',  dev('Клубный дом DUO')),
    ('«Космо 4/22» — вся экспозиция',          L['Клубный дом Космо 4/22']),
    ('Все новостройки де-люкс, лоты в бетоне', POOL),
    ('ЖК «Золотой» — без нашего лота',         [x for x in L['Золотой']
                                                if x['url'] != OUR_URL]),
]

nf = lambda v: f'{v:,.0f}'.replace(',', ' ')
rows, out, model = [], {}, {}
print(f'наш лот: {OUR_A} м², этаж {OUR_FL};  с ремонтом {nf(OUR_PPM)} ₽/м², '
      f'«голый» {nf(OUR_BARE)} ₽/м²\n')
for name, lots in BASES:
    r = fit(lots)
    if r is None:
        print(f'{name:42s} — лотов {len(lots)}, для регрессии мало'); continue
    rows.append([name, str(r['n']), f"{r['lo']}–{r['hi']}",
                 f"{r['per']:+.2f} %".replace('.', ','),
                 f"{r['area']:+.1f} %".replace('.', ','),
                 f"{r['r2']:.2f}".replace('.', ','), nf(r['pred'])])
    out[name] = r
    model[name] = {'pred': r['pred'], 'vsBare': OUR_BARE / r['pred'] - 1,
                   'vsFull': OUR_PPM / r['pred'] - 1,
                   'bareRub': (OUR_BARE - r['pred']) * OUR_A,
                   'fullRub': (OUR_PPM - r['pred']) * OUR_A}
    print(f"{name:42s} n={r['n']:3d}  этажи {r['lo']}–{r['hi']:<2d} "
          f"за этаж {r['per']:+.2f} %  +10 % площади {r['area']:+.1f} %  R²={r['r2']:.2f}")
    print(f"{'':42s}   ожидает для 87,5 м² на 4 этаже {nf(r['pred'])} ₽/м² → "
          f"«голая» цена {OUR_BARE / r['pred'] - 1:+.1%} "
          f"({(OUR_BARE - r['pred']) * OUR_A / 1e6:+.1f} млн ₽), "
          f"с ремонтом {OUR_PPM / r['pred'] - 1:+.1%}")

# лестница этажей по главной базе: сколько стоит метр 87,5 м² на каждом этаже
MAIN = 'Все новостройки де-люкс, лоты в бетоне'
ladder = []
if MAIN in out:
    r = out[MAIN]
    base = r['pred'] / (1 + r['per'] / 100) ** OUR_FL
    for f in range(1, 7):
        v = base * (1 + r['per'] / 100) ** f
        ladder.append([f'{f} этаж', nf(v), f'{v * OUR_A / 1e6:.0f} млн ₽'.replace('.', ',')])
    print('\nчто модель ожидает для 87,5 м² в бетоне по этажам:')
    for r2 in ladder: print('   ', ' | '.join(r2))

K = json.load(open('zl/zl_tables.json'))
K['floor'], K['floorRows'], K['model'], K['ladder'] = out, rows, model, ladder
json.dump(K, open('zl/zl_tables.json', 'w'), ensure_ascii=False, indent=1)
