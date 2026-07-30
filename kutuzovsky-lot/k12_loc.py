"""Сравнение с локацией: квартиры 85–115 м² вокруг Кутузовского проспекта.

Считает три блока в k12_tables.json:
  loc   — таблица «Сравнение с локацией»
  ready — основания для графика «метр квартиры, готовой к заселению»
  apart — справочная цена апартаментов Москва-Сити
"""
import json

L = json.load(open('k12_linked.json'))
FIT = 250_000
LO, HI = 85, 115

SHELL = {'Без отделки', 'Черновая', 'Без ремонта'}
WB    = {'Предчистовая (white box)', 'Чистовая'}
REN   = {'Дизайнерский', 'Евроремонт', 'Под ключ / с мебелью', 'Косметический'}
grp = lambda f: 'shell' if f in SHELL else 'wb' if f in WB else 'ren' if f in REN else 'unk'

w  = lambda g: sum(x['price'] for x in g) / sum(x['area'] for x in g)
nf = lambda v: f'{v:,.0f}'.replace(',', ' ')
d1 = lambda v: f'{v:.1f}'.replace('.', ',')

ORDER = ['Кутузовский XII', 'Бадаевский', 'Веспер Кутузовский', 'Capital Towers', 'Дом Дау']
SUB = {'Кутузовский XII':    'вторичка · сдан 2020',
       'Бадаевский':         'первичка · без отделки',
       'Веспер Кутузовский': 'первичка · без отделки',
       'Capital Towers':     'первич. и вторич. · сдан 2023',
       'Дом Дау':            'первичка · без отделки'}

def block(name):
    band = [x for x in L[name] if LO <= x['area'] <= HI]
    fin  = [x for x in band if grp(x['fin']) in ('ren', 'wb')]
    sh   = [x for x in band if grp(x['fin']) == 'shell']
    if fin:  base, fit, why = w(fin), 0,   f'{len(fin)} с ремонтом'
    elif sh: base, fit, why = w(sh), FIT,  f'{len(sh)} без отделки'
    else:    base, fit, why = w(band), 0,  f'{len(band)} отделка н/д'
    row = [name, str(len(band)),
           f"{d1(min(x['area'] for x in band))}–{d1(max(x['area'] for x in band))}",
           f"{d1(min(x['price'] for x in band) / 1e6)}–{d1(max(x['price'] for x in band) / 1e6)}",
           nf(w(band)), why, nf(base + fit)]
    return row, (base, fit)

rows, ready = [], {}
for n in ORDER:
    row, br = block(n)
    rows.append(row); ready[n] = [br[0], br[1], SUB[n]]
ap_row, ap = block('Апартаменты Сити')
ap_row[0] = 'Апартаменты Москва-Сити*'
rows.append(ap_row)

K = json.load(open('k12_tables.json'))
K['loc'], K['ready'], K['apart'], K['fit'] = rows, ready, ap[0] + ap[1], FIT
json.dump(K, open('k12_tables.json', 'w'), ensure_ascii=False, indent=1)
for r in rows: print(' | '.join(r))
print('НАШ ЛОТ 1 710 526')
for n, (b, f, _) in ready.items():
    print(f'  vs {n:22s} {100 * (1_710_526 / (b + f) - 1):+5.0f} %')
