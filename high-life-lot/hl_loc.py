"""Сравнение с локацией: квартиры 110–140 м² вокруг Павелецкой.

Наш лот продаётся с отделкой от застройщика, поэтому приведение к «квартире,
в которую можно заехать» работает так же, как на Кутузовском, и обратно
Академической: отделка добавляется конкурентам, а не нам.

Отделка премиум-класса в Даниловском оценена в 200 тыс. ₽/м² — между двумя
уже согласованными ориентирами: 150 тыс. (бизнес-класс, Академический)
и 250 тыс. (премиум, Кутузовский проспект).
"""
import json, sys, os
sys.path.insert(0, os.path.dirname(__file__))
from hl_fin import grp

L = json.load(open('hl/hl_linked.json'))
FIT = 200_000
LO, HI = 110, 140
OUR = {'area': 125.1, 'price': 96_000_000, 'ppm': 767_386, 'floor': '8/32'}

w  = lambda g: sum(x['price'] for x in g) / sum(x['area'] for x in g)
nf = lambda v: f'{v:,.0f}'.replace(',', ' ')
d1 = lambda v: f'{v:.1f}'.replace('.', ',')

ORDER = ['HIGH LIFE', 'Монблан', 'Клубный дом ОПУС', 'Эра', 'ЖК «А»',
         'ЛОТ от Аквилон', 'Воксхолл', 'Левел Павелецкая Сити',
         'Павелецкая от Гранель', 'Стремянный 2', 'Левел Павелецкая']
SUB = {'HIGH LIFE':             'наш квартал · премиум',
       'Монблан':               'элит · Шлюзовая наб.',
       'Клубный дом ОПУС':      'элит · Дербеневская',
       'Эра':                   'премиум · Дербеневская наб.',
       'ЖК «А»':                'бизнес · Брусника',
       'ЛОТ от Аквилон':        'бизнес · Жуков пр-д',
       'Воксхолл':              'бизнес · сдан 2025–2026',
       'Левел Павелецкая Сити': 'бизнес · сдан 2023–2025',
       'Павелецкая от Гранель': 'бизнес · нет лотов нашего размера',
       'Стремянный 2':          'бизнес · нет лотов нашего размера',
       'Левел Павелецкая':      'вторичка 2019 · нет лотов нашего размера'}

def block(name):
    band = [x for x in L[name] if LO <= x['area'] <= HI]
    if not band:
        return [name, '0', '—', '—', '—', 'нет лотов 110–140 м²', '—'], None
    rd = [x for x in band if grp(x) in ('ready', 'wb')]
    sh = [x for x in band if grp(x) not in ('ready', 'wb')]
    if len(rd) >= 3:  base, fit, why = w(rd), 0,   f'{len(rd)} готовых'
    elif rd:          base, fit, why = w(band), FIT * len(sh) / len(band), f'{len(rd)} готовых, {len(sh)} в бетоне'
    else:             base, fit, why = w(sh), FIT, f'{len(sh)} в бетоне'
    row = [name, str(len(band)),
           f"{d1(min(x['area'] for x in band))}–{d1(max(x['area'] for x in band))}",
           f"{d1(min(x['price'] for x in band) / 1e6)}–{d1(max(x['price'] for x in band) / 1e6)}",
           nf(w(band)), why, nf(base + fit)]
    return row, (base, fit)

rows, ready = [], {}
for n in ORDER:
    row, br = block(n)
    rows.append(row)
    if br: ready[n] = [br[0], br[1], SUB[n]]

K = json.load(open('hl/hl_tables.json'))
K['loc'], K['ready'], K['fit'], K['our'], K['locSub'] = rows, ready, FIT, OUR, SUB
json.dump(K, open('hl/hl_tables.json', 'w'), ensure_ascii=False, indent=1)

for r in rows: print(' | '.join(r))
print(f"\nНАШ ЛОТ {nf(OUR['ppm'])} ₽/м² — отделка уже есть, доплачивать нечего "
      f"({OUR['price'] / 1e6:.1f} млн ₽)")
for n, (b, f, _) in ready.items():
    print(f'  vs {n:24s} {100 * (OUR["ppm"] / (b + f) - 1):+5.0f} %   ({nf(b + f)} ₽/м²)')
