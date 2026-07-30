"""Экспозиция ЖК «Кутузовский XII»: таблица лотов со ссылками на Циан.

Дубли объявлений схлопнуты ещё в k12_links.py — одну и ту же квартиру
выставляют несколько агентств.
"""
import json

L = json.load(open('k12_linked.json'))['Кутузовский XII']
OUR = 162_500_000

lots = sorted(L, key=lambda x: -x['ppm'])          # дубли схлопнуты в k12_links.py
print(f'{len(lots)} лотов')

w  = lambda g: sum(x['price'] for x in g) / sum(x['area'] for x in g)
nf = lambda v: f'{v:,.0f}'.replace(',', ' ')
d1 = lambda v: f'{v:.1f}'.replace('.', ',')
CAT = {'Своб. планировка': 'своб. пл.', '1': '1-комн', '2': '2-комн',
       '3': '3-комн', '4+': '4+ комн'}
cat = lambda c: CAT.get(c, c or '—')

rows = [[('▶ ' if x['price'] == OUR else '') + cat(x['cat']),
         d1(x['area']), x['floor'], d1(x['price'] / 1e6), nf(x['ppm']),
         x['fin'] or 'не указана', {'text': 'Циан →', 'link': x['url']}]
        for x in lots]

ren   = [x for x in lots if x['fin'] == 'Дизайнерский']
shell = [x for x in lots if x['fin'] == 'Без отделки']
stats = {'n': len(lots), 'all': w(lots), 'ren': w(ren), 'shell': w(shell),
         'premium': w(ren) / w(shell) - 1,
         'vs_all': OUR / 95.0 / w(lots) - 1, 'vs_ren': OUR / 95.0 / w(ren) - 1}

K = json.load(open('k12_tables.json'))
K['own'], K['ownStats'] = rows, stats
json.dump(K, open('k12_tables.json', 'w'), ensure_ascii=False, indent=1)

for r in rows:
    print(' | '.join(str(c if not isinstance(c, dict) else c['text']) for c in r))
print(f"\nвсего {stats['n']} лотов, Ø {nf(stats['all'])} ₽/м²")
print(f"дизайнерский ({len(ren)}) {nf(stats['ren'])} · без отделки ({len(shell)}) {nf(stats['shell'])}"
      f" · премия {stats['premium']*100:+.0f} %")
print(f"наш лот к дому {stats['vs_all']*100:+.0f} %, к лотам с ремонтом {stats['vs_ren']*100:+.0f} %")
