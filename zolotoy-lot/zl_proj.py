"""Паспорта восьми проектов локации: класс, срок, застройщик, паркинг, статус лотов.

Источники — сайты застройщиков и профильные порталы, проверено 31.07.2026.
Прочерк в паркинге означает, что число машиномест не опубликовано.

ВАЖНО. Три проекта из восьми продают АПАРТАМЕНТЫ, а не квартиры: «Резиденция 1864»
(10 объявлений из 10 прямо называют лоты апартаментами), «Софийский» (11 из 15)
и BALCHUG VIEWPOINT (6 из 8). Это другой юридический статус и структурно другая
цена, поэтому в средние по «квартирному» рынку они не входят и в таблицах
помечены звёздочкой.
"""
import json

L = json.load(open('zl/zl_linked.json'))

#         класс,     срок сдачи,        застройщик,           адрес,                    паркинг, апарты
META = {
    'Золотой':                ('де-люкс', 'сдан в I кв. 2023', 'Capital Group',        'Софийская наб., 18–20',      '363',      False),
    'Дом Лаврушинский':       ('элит',    'сдан в 2025',       'Sminex',               'Б. Толмачёвский пер., 5',    '263',      False),
    'Русские Сезоны':         ('элит',    'IV кв. 2026',       'СЗ «Экран»',           'Б. Ордынка, 19с9',           '267 + 30', False),
    'Клубный дом DUO':        ('де-люкс', 'IV кв. 2026',       'Hutton',               'Софийская наб., 34с3',       '—',        False),
    'Клубный дом Космо 4/22': ('де-люкс', 'сдан',              'Галс-Девелопмент',     'Космодамианская наб., 4/22', '—',        False),
    'Резиденция 1864':        ('де-люкс', 'сдан в 2022',       'Сбербанк Капитал',     'Софийская наб., 36',         '—',        True),
    'Софийский':              ('де-люкс', 'сдан в 2018',       'вторичный рынок',      'Софийская наб., 34с5',       '—',        True),
    'BALCHUG VIEWPOINT':      ('де-люкс', 'сдан в 2017',       'вторичный рынок',      'Садовническая ул., 7',       '—',        True),
}
ORDER = ['Золотой', 'Клубный дом DUO', 'Дом Лаврушинский', 'Русские Сезоны',
         'Клубный дом Космо 4/22', 'Резиденция 1864', 'Софийский', 'BALCHUG VIEWPOINT']

nf  = lambda v: f'{v:,.0f}'.replace(',', ' ')
rng = lambda vs: f'{min(vs):.0f}–{nf(max(vs))}'
OUR_P, OUR_A, FIT = 280_000_000, 87.5, 750_000
BARE = OUR_P - OUR_A * FIT                     # «голая» цена нашего лота

rows, power = [], []
for name in ORDER:
    v = L[name]
    cls, due, dev, addr, park, ap = META[name]
    ppm = sum(x['price'] for x in v) / sum(x['area'] for x in v)
    star = ' *' if ap else ''
    rows.append([name + star, cls, due, dev, str(len(v)),
                 rng([x['area'] for x in v]), rng([x['price'] / 1e6 for x in v]), nf(ppm), park])
    if not ap:
        power.append([f'{OUR_P / ppm:.0f} м²', name.replace('Клубный дом ', '')])

K = json.load(open('zl/zl_tables.json')) if __import__('os').path.exists('zl/zl_tables.json') else {}
K['proj'], K['power'], K['projMeta'] = rows, power, {n: META[n] for n in ORDER}
K['our'] = {'area': OUR_A, 'price': OUR_P, 'ppm': OUR_P / OUR_A, 'floor': '4/4',
            'fit': FIT, 'fitTotal': OUR_A * FIT, 'bare': BARE, 'barePpm': BARE / OUR_A}
json.dump(K, open('zl/zl_tables.json', 'w'), ensure_ascii=False, indent=1)

for r in rows: print(' | '.join(r))
print(f'\nнаш лот: {OUR_P/1e6:.1f} млн ₽ = {nf(OUR_P/OUR_A)} ₽/м² с ремонтом')
print(f'  ремонт {FIT/1e3:.0f} тыс. ₽/м² × {OUR_A} = {OUR_A*FIT/1e6:.1f} млн ₽')
print(f'  «голая» цена {BARE/1e6:.1f} млн ₽ = {nf(BARE/OUR_A)} ₽/м²')
print(f'\nбюджет {OUR_P/1e6:.0f} млн ₽ покупает (только квартирные проекты):',
      ', '.join(f'{p[0]} в «{p[1]}»' for p in power))
