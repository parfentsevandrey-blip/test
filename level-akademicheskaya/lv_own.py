"""Экспозиция ЖК «Левел Академическая» и позиция нашего лота внутри дома."""
import json, re

L = json.load(open('lev/lv_linked.json'))['Левел Академическая']
OUR_P, OUR_A, OUR_FL = 56_999_999, 79.5, 15
GRAD = json.load(open('lev/lv_tables.json'))['floor']['Левел Академическая — застройщик']['per']

w  = lambda g: sum(x['price'] for x in g) / sum(x['area'] for x in g)
nf = lambda v: f'{v:,.0f}'.replace(',', ' ')
d1 = lambda v: f'{v:.1f}'.replace('.', ',')
CAT = {'Студия': 'студия', '1': '1-комн', '2': '2-комн', '3': '3-комн', '4+': '4+ комн'}
fl = lambda x: int(re.match(r'(\d+)', x['floor']).group(1))

lots = sorted(L, key=lambda x: -x['ppm'])
rows = [[('▶ ' if x['price'] == OUR_P else '') + CAT.get(x['cat'], x['cat'] or '—'),
         d1(x['area']), x['floor'], d1(x['price'] / 1e6), nf(x['ppm']),
         x['fin'] or 'не указана', {'text': 'Циан →', 'link': x['url']}] for x in lots]

# сопоставимые трёшки 65–95 м² и приведение к нашему этажу
band = [x for x in L if x['cat'] == '3' and 65 <= x['area'] <= 95 and x['price'] != OUR_P]
cmp_rows = []
for x in sorted(band, key=lambda x: -x['ppm']):
    adj = x['ppm'] * (1 + GRAD / 100) ** (OUR_FL - fl(x))
    cmp_rows.append([d1(x['area']), x['floor'], d1(x['price'] / 1e6), nf(x['ppm']),
                     f'{OUR_FL - fl(x):+d}', nf(adj), x['fin'] or 'не указана'])

stats = {'n': len(L), 'all': w(L), 'vs_all': OUR_P / OUR_A / w(L) - 1, 'grad': GRAD,
         'rank': next(i for i, x in enumerate(lots) if x['price'] == OUR_P) + 1}
K = json.load(open('lev/lv_tables.json'))
K['own'], K['ownCmp'], K['ownStats'] = rows, cmp_rows, stats
json.dump(K, open('lev/lv_tables.json', 'w'), ensure_ascii=False, indent=1)

print(f"{stats['n']} лотов, Ø {nf(stats['all'])} ₽/м², наш лот {stats['rank']}-й, {stats['vs_all']:+.0%} к средней")
print(f"градиент этажа по прайсу застройщика: {GRAD:+.2f} % за этаж\n")
print('сопоставимые трёшки 65–95 м², приведённые к 15 этажу:')
for r in cmp_rows: print('  ', ' | '.join(r))
print(f'\n  НАШ ЛОТ 79,5 м², эт. 15/19, 57,0 млн, {nf(OUR_P/OUR_A)} ₽/м²')
