"""Сравнение с локацией и приведение к «квартире, в которую можно заехать».

Сопоставимая база — квартиры 65–115 м² во всех восьми проектах. Наш лот
идёт как готовый (решение заказчика: премиум-ремонт сделан), доплачивать
нечего. Там, где экспозиция соседей в бетоне, к цене метра добавляется
750 000 ₽/м² — ровно та сумма, в которую ремонт обошёлся владельцу.

Это и есть проверка самой суммы на прочность: если после такой нормализации
соседи становятся дороже нас — рынок 750 тыс. ₽/м² оправдывает; если наш лот
остаётся самым дорогим — не оправдывает.
"""
import json, sys, os
sys.path.insert(0, os.path.dirname(__file__))
from zl_fin import grp, combo

L = json.load(open('zl/zl_linked.json'))
FIT, LO, HI = 750_000, 65, 115
OUR_URL = 'https://www.cian.ru/sale/flat/326035617/'
OUR = {'area': 87.5, 'price': 280_000_000, 'ppm': 3_200_000, 'floor': '4/4'}

w  = lambda g: sum(x['price'] for x in g) / sum(x['area'] for x in g)
nf = lambda v: f'{v:,.0f}'.replace(',', ' ')
d1 = lambda v: f'{v:.1f}'.replace('.', ',')

ORDER = ['Золотой', 'Дом Лаврушинский', 'Клубный дом DUO', 'Клубный дом Космо 4/22',
         'Софийский', 'Русские Сезоны', 'Резиденция 1864', 'BALCHUG VIEWPOINT']
APART = {'Софийский', 'Резиденция 1864', 'BALCHUG VIEWPOINT'}
SUB = {'Золотой':                'наш квартал · де-люкс',
       'Дом Лаврушинский':       'элит · Sminex',
       'Клубный дом DUO':        'де-люкс · Hutton',
       'Клубный дом Космо 4/22': 'де-люкс · Галс',
       'Софийский':              'апартаменты · 2018',
       'Русские Сезоны':         'элит · нет лотов 65–115 м²',
       'Резиденция 1864':        'апартаменты · нет лотов 65–115 м²',
       'BALCHUG VIEWPOINT':      'апартаменты · нет лотов 65–115 м²'}

def block(name):
    band = [x for x in L[name] if LO <= x['area'] <= HI and not combo(x)]
    star = ' *' if name in APART else ''
    if not band:
        rng = f"{min(x['area'] for x in L[name]):.0f}–{max(x['area'] for x in L[name]):.0f} м²"
        return [name + star, '0', '—', '—', '—', f'квартиры только {rng}', '—'], None
    rd = [x for x in band if grp(x) in ('ready', 'wb')]
    sh = [x for x in band if grp(x) == 'shell']
    if name == 'Золотой':                    # наш лот, ремонт сделан
        base, fit, why = w(band), 0, '1 готовый (наш)'
    elif len(rd) >= 3 and len(rd) >= len(sh): base, fit, why = w(rd), 0, f'{len(rd)} готовых'
    elif rd: base, fit, why = w(band), FIT * len(sh) / len(band), f'{len(rd)} готовых, {len(sh)} в бетоне'
    else:    base, fit, why = w(sh), FIT, f'{len(sh)} в бетоне'
    row = [name + star, str(len(band)),
           f"{d1(min(x['area'] for x in band))}–{d1(max(x['area'] for x in band))}",
           f"{d1(min(x['price'] for x in band) / 1e6)}–{d1(max(x['price'] for x in band) / 1e6)}",
           nf(w(band)), why, nf(base + fit)]
    return row, (base, fit)

rows, ready = [], {}
for n in ORDER:
    row, br = block(n)
    rows.append(row)
    if br: ready[n] = [br[0], br[1], SUB[n]]

K = json.load(open('zl/zl_tables.json'))
K['loc'], K['ready'], K['fit'], K['locSub'], K['apart'] = rows, ready, FIT, SUB, sorted(APART)
json.dump(K, open('zl/zl_tables.json', 'w'), ensure_ascii=False, indent=1)

for r in rows: print(' | '.join(r))
print(f"\nНАШ ЛОТ {nf(OUR['ppm'])} ₽/м² — ремонт сделан, доплачивать нечего")
for n, (b, f, _) in ready.items():
    if n == 'Золотой': continue
    tag = ' (апарт.)' if n in APART else ''
    print(f'  vs {n:24s}{tag:9s} {100 * (OUR["ppm"] / (b + f) - 1):+5.0f} %   ({nf(b + f)} ₽/м²)')
q = [v for n, v in ready.items() if n not in APART and n != 'Золотой']
if q:
    lo, hi = min(b + f for b, f, _ in q), max(b + f for b, f, _ in q)
    print(f'\nквартирные проекты после нормализации: {nf(lo)}–{nf(hi)} ₽/м²; '
          f'наш лот {nf(OUR["ppm"])} → {"дороже всех" if OUR["ppm"] > hi else "внутри коридора"}')
