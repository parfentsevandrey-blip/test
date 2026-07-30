"""Проверка трёх гипотез заказчика — считаем по выгрузкам, а не на глаз.

  1. Огромное конкурентное предложение в локации.
  2. Вкусные условия по рассрочке/ипотеке от застройщика.
  3. Отсутствие парковки на загруженной Летниковской.
"""
import json, re, sys, os
sys.path.insert(0, os.path.dirname(__file__))
import hl_links as HLK
from hl_fin import grp

L = json.load(open('hl/hl_linked.json'))
LO, HI = 110, 140
BLO, BHI = 86e6, 106e6
OUR_URL = 'https://www.cian.ru/sale/flat/327976256/'
nf = lambda v: f'{v:,.0f}'.replace(',', ' ')

# ── тексты объявлений (в выгрузке обрезаны на 600 знаках) ──────────────────
def raw(name):
    rs = HLK.rows(HLK.BASE + HLK.FILES[name])
    hi = next(i for i, (c, _) in enumerate(rs) if c and c[0] == '№')
    H = {n: i for i, n in enumerate(rs[hi][0])}
    g = lambda c, k: c[H[k]] if k in H and H[k] < len(c) else ''
    return [dict(area=float(g(c, 'Площадь, м²')), floor=g(c, 'Этаж'),
                 price=int(g(c, 'Цена, ₽')), seller=g(c, 'Тип продавца'),
                 d=(g(c, 'Описание') or '').replace('\n', ' '),
                 url=(href or '').split('?')[0])
            for c, href in rs[hi + 1:] if g(c, 'Площадь, м²') and g(c, 'Цена, ₽')]

RAW = {n: raw(n) for n in HLK.FILES}

# ── гипотеза 1: объём предложения ──────────────────────────────────────────
allLots = [x for v in L.values() for x in v]
band    = {n: [x for x in v if LO <= x['area'] <= HI] for n, v in L.items()}
budget  = {n: [x for x in v if BLO <= x['price'] <= BHI] for n, v in L.items()}
h1 = {
    'total': len(allLots), 'projects': len(L),
    'band': sum(len(v) for v in band.values()), 'bandHL': len(band['HIGH LIFE']),
    'budget': sum(len(v) for v in budget.values()), 'budgetHL': len(budget['HIGH LIFE']),
    'both': sum(1 for x in allLots if LO <= x['area'] <= HI and BLO <= x['price'] <= BHI),
    'bothHL': sum(1 for x in L['HIGH LIFE'] if LO <= x['area'] <= HI and BLO <= x['price'] <= BHI),
    'noBand': [n for n, v in band.items() if not v],
}
h1rows = [[n, str(len(L[n])), str(len(band[n])), str(len(budget[n])),
           str(sum(1 for x in L[n] if LO <= x['area'] <= HI and BLO <= x['price'] <= BHI))]
          for n in ['HIGH LIFE', 'Эра', 'Монблан', 'Клубный дом ОПУС', 'Левел Павелецкая Сити',
                    'ЖК «А»', 'Воксхолл', 'ЛОТ от Аквилон', 'Павелецкая от Гранель',
                    'Стремянный 2', 'Левел Павелецкая']]
h1rows.append(['Итого по локации', str(h1['total']), str(h1['band']), str(h1['budget']), str(h1['both'])])

# ── гипотеза 2: условия застройщика ────────────────────────────────────────
RASS = re.compile(r'рассроч', re.I)
R0   = re.compile(r'[Рр]ассрочка\s*0\s*%')
IPO  = re.compile(r'ипотек|субсидир|ставк', re.I)
h2rows, h2 = [], {}
for n in ['HIGH LIFE', 'Эра', 'Левел Павелецкая Сити', 'Клубный дом ОПУС', 'Монблан',
          'Павелецкая от Гранель', 'ЛОТ от Аквилон', 'ЖК «А»', 'Воксхолл']:
    v = RAW[n]
    dev = sum(1 for x in v if x['seller'] == 'Застройщик')
    r   = sum(1 for x in v if RASS.search(x['d']))
    r0  = sum(1 for x in v if R0.search(x['d']))
    ip  = sum(1 for x in v if IPO.search(x['d']))
    h2rows.append([n, str(len(v)), f'{dev} ({dev / len(v):.0%})', str(r), str(r0), str(ip)])
    h2[n] = {'n': len(v), 'dev': dev, 'rass': r, 'r0': r0, 'ipo': ip}
hl = L['HIGH LIFE']
h2['devHL']    = sum(1 for x in hl if x['seller'] == 'Застройщик')
h2['devBud']   = sum(1 for x in budget['HIGH LIFE'] if x['seller'] == 'Застройщик')
h2['devBand']  = sum(1 for x in band['HIGH LIFE'] if x['seller'] == 'Застройщик')
h2['quote']    = 'Рассрочка 0% от застройщика на готовые квартиры до 30.06.2027 ' \
                 'и индивидуальные программы кредитования'
h2['quoteEra'] = 'Рассрочка 0% с платежом от 250 000 ₽ в месяц'

# ── гипотеза 3: парковка ───────────────────────────────────────────────────
PARK = re.compile(r'машино-?мест|паркинг|парковочн|келлер|кладов', re.I)
agBand = [x for x in RAW['HIGH LIFE'] if LO <= x['area'] <= HI and x['seller'] != 'Застройщик']
withPark = [x for x in agBand if PARK.search(x['d'])]
our = next(x for x in RAW['HIGH LIFE'] if x['url'] == OUR_URL)
fl8raw = [x for x in RAW['HIGH LIFE'] if x['floor'].startswith('8/') and LO <= x['area'] <= HI]
fl8, seen = [], set()
for x in sorted(fl8raw, key=lambda x: (x['price'], -len(x['d']))):
    k = (round(x['area'], 1), x['price'])
    if k in seen: continue
    seen.add(k); fl8.append(x)
h3 = {
    'agBand': len(agBand), 'withPark': len(withPark),
    'ourPark': bool(PARK.search(our['d'])), 'ourLen': len(our['d']),
    'fl8': [[('▶ ' if x['url'] == OUR_URL else '') + f"{x['area']:.1f}".replace('.', ','),
             f"{x['price'] / 1e6:.1f}".replace('.', ','),
             'да' if PARK.search(x['d']) else 'не указано', x['url']] for x in
            sorted(fl8, key=lambda x: x['price'])],
}
#              проект, машиномест, источник
PARKROWS = [
    ['▶ HIGH LIFE',            '750',       '6 башен 24–47 эт., самый крупный паркинг в подборке'],
    ['Воксхолл',               '596',       'Группа «Эталон», Летниковская'],
    ['Эра',                    '443',       'ТЕКТА ГРУПП, Дербеневская наб.'],
    ['Клубный дом ОПУС',       '301',       '188 квартир — 1,6 места на квартиру'],
    ['Монблан',                '267',       'двухуровневый + гостевая наземная'],
    ['ЖК «А»',                 '189',       'Брусника; рядом многоэтажный паркинг на 1000 мест'],
    ['Павелецкая от Гранель',  '90',        '212 квартир в экспозиции + 14 гостевых мест'],
    ['ЛОТ от Аквилон',         'многоур.',  'число мест не опубликовано'],
]

K = json.load(open('hl/hl_tables.json'))
K['h1'], K['h1rows'], K['h2'], K['h2rows'], K['h3'], K['parkRows'] = h1, h1rows, h2, h2rows, h3, PARKROWS
json.dump(K, open('hl/hl_tables.json', 'w'), ensure_ascii=False, indent=1)

print('Г1  всего лотов', h1['total'], '| 110–140 м²', h1['band'], f"(HIGH LIFE {h1['bandHL']})",
      '| 86–106 млн', h1['budget'], f"(HIGH LIFE {h1['budgetHL']})",
      '| и то и другое', h1['both'], f"(HIGH LIFE {h1['bothHL']})")
print('    нет лотов нашего размера вообще:', ', '.join(h1['noBand']))
for r in h1rows: print('   ', ' | '.join(r))
print('\nГ2  застройщик держит', h2['devHL'], 'из', len(hl), 'лотов HIGH LIFE;',
      h2['devBud'], 'из', len(budget['HIGH LIFE']), 'в бюджете;', h2['devBand'], 'из', len(band['HIGH LIFE']), 'в базе 110–140')
for r in h2rows: print('   ', ' | '.join(r))
print('\nГ3  агентских лотов 110–140 м²:', h3['agBand'], '| продают с машиноместом:', h3['withPark'],
      '| наш лот упоминает парковку:', h3['ourPark'], '| длина описания', h3['ourLen'], 'зн.')
for r in h3['fl8']: print('    8 этаж:', r)
