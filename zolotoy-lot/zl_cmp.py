"""Сопоставимые лоты локации, приведённые к нашему этажу.

Берём квартиры 75–105 м² в квартирных проектах (апартаменты не участвуют),
приводим цену метра к 4-му этажу по замеренной надбавке +6,03 % за этаж
и добавляем отделку там, где лот продаётся в бетоне. Получается прямое
сравнение: сколько стоит метр квартиры, готовой к заселению, на нашем этаже.
"""
import json, re, sys, os
sys.path.insert(0, os.path.dirname(__file__))
from zl_fin import grp, combo

L = json.load(open('zl/zl_linked.json'))
QUART = ['Золотой', 'Дом Лаврушинский', 'Клубный дом DUO', 'Клубный дом Космо 4/22', 'Русские Сезоны']
LO, HI, OUR_FL, FIT = 75, 105, 4, 750_000
OUR_URL = 'https://www.cian.ru/sale/flat/326035617/'
GRAD = json.load(open('zl/zl_tables.json'))['floor']['Все новостройки де-люкс, лоты в бетоне']['per']

fl = lambda x: int(re.match(r'(\d+)', x['floor']).group(1))
nf = lambda v: f'{v:,.0f}'.replace(',', ' ')
d1 = lambda v: f'{v:.1f}'.replace('.', ',')
SHORT = {'Клубный дом Космо 4/22': '«Космо 4/22»', 'Клубный дом DUO': 'DUO',
         'Дом Лаврушинский': '«Лаврушинский»', 'Золотой': 'ЖК «Золотой»',
         'Русские Сезоны': '«Русские Сезоны»'}

rows = []
for n in QUART:
    for x in L[n]:
        if not (LO <= x['area'] <= HI) or combo(x): continue
        adj = x['ppm'] * (1 + GRAD / 100) ** (OUR_FL - fl(x))
        key = adj + (FIT if grp(x) == 'shell' else 0)
        rows.append({'proj': n, 'x': x, 'adj': adj, 'key': key,
                     'ours': x['url'] == OUR_URL})
rows.sort(key=lambda r: -r['key'])

cmpRows = [[('▶ ' if r['ours'] else '') + SHORT[r['proj']],
            d1(r['x']['area']), r['x']['floor'].split('/')[0], d1(r['x']['price'] / 1e6),
            nf(r['x']['ppm']), nf(r['adj']),
            'ремонт есть' if grp(r['x']) != 'shell' else 'бетон',
            nf(r['key']), {'text': 'Циан →', 'link': r['x']['url']}] for r in rows]

ours = next(r for r in rows if r['ours'])
rank = rows.index(ours) + 1
cheaper = [r for r in rows if r['key'] < ours['key']]
stats = {'n': len(rows), 'rank': rank, 'cheaper': len(cheaper),
         'grad': GRAD, 'lo': LO, 'hi': HI,
         'min': rows[-1]['key'], 'max': rows[0]['key'],
         'median': sorted(r['key'] for r in rows)[len(rows) // 2],
         'vsMedian': ours['key'] / sorted(r['key'] for r in rows)[len(rows) // 2] - 1}

K = json.load(open('zl/zl_tables.json'))
K['cmp'], K['cmpStats'] = cmpRows, stats
json.dump(K, open('zl/zl_tables.json', 'w'), ensure_ascii=False, indent=1)

print(f'сопоставимых лотов {LO}–{HI} м² в квартирных проектах: {len(rows)}; '
      f'наш — {rank}-й по метру «под ключ», дешевле нас только {len(cheaper)}')
print(f'коридор {nf(stats["min"])}–{nf(stats["max"])} ₽/м², медиана {nf(stats["median"])}, '
      f'наш {ours["key"]:,.0f} ({stats["vsMedian"]:+.1%} к медиане)'.replace(',', ' '))
for r in cmpRows: print('  ', ' | '.join(str(c['text'] if isinstance(c, dict) else c) for c in r))
