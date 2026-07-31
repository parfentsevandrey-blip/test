"""Ниша лота: вся Москва внутри Садового кольца по параметрам нашей квартиры.

Фильтр Циан на 31.07.2026: дом от 2017 года постройки, квартиры (не апартаменты),
площадь до 110 м², дизайнерский ремонт, внутри Садового кольца.
Выдача — 12 объявлений; после схлопывания двух дублей остаётся 10 квартир.

Наш лот в эту выдачу НЕ попадает: он проходит все фильтры, кроме отделки —
в карточке Циан у него стоит «Без отделки», хотя отделка выполнена.
"""
import json, sys, os
sys.path.insert(0, os.path.dirname(__file__))
import zl_links as Z

SRC = '/root/.claude/uploads/f10b7ab2-1385-5931-8285-d55741d60955/628f43a5-cian_______________________20260731_12_____.xls'
OUR = {'name': 'ЖК «Золотой» — наш лот', 'metro': 'Третьяковская', 'tometro': '11',
       'area': 87.5, 'floor': '4', 'year': '2023', 'price': 280_000_000, 'ppm': 3_200_000,
       'url': 'https://www.cian.ru/sale/flat/326035617/'}

# Дубли: одна квартира от двух агентов. Площадь в шапке отличается на десятые,
# поэтому обычный ключ дедупликации их не ловит — схлопнуто вручную по тексту.
DUP = {'https://www.cian.ru/sale/flat/316015975/': '108,0 м² = 107,7 м², STELLA DI MOSCA, эт. 2, 350,0 млн',
       'https://www.cian.ru/sale/flat/326522097/': '67,0 м² = 67,4 м², клубный дом «Абрикосов», эт. 2, 120,0 млн'}

# Читаемые названия домов: поле «Корпус / секция» местами пустое,
# адрес взят из первых строк объявления.
NAME = {
    '324077709': ('Клубный дом «Ордынский»', 'Ордынский туп., 4А'),
    '320386160': ('STELLA DI MOSCA', 'Б. Никитская, 9'),
    '267826294': ('«Дом на Хлебном»', 'Хлебный пер., 19'),
    '328651371': ('«Красный Октябрь»', 'остров Балчуг'),
    '332443720': ('Клубный дом у Арбатской', 'исторический особняк'),
    '325281485': ('Клубный дом', 'Костянский пер., 13'),
    '326531270': ('Клубный дом «Абрикосов»', 'Потаповский пер., 6с1'),
    '328377077': ('«Маросейка, 11»', 'палаты Нарышкина'),
    '330235731': ('Клубный дом', 'Б. Николоворобинский, 9к1'),
    '328117893': ('Клубный дом у Павелецкой', 'частная резиденция'),
}

rs = Z.rows(SRC)
hi = next(i for i, (c, _) in enumerate(rs) if c and c[0] == '№')
H = {n: i for i, n in enumerate(rs[hi][0])}
g = lambda c, k: c[H[k]] if k in H and H[k] < len(c) else ''

raw, lots = [], []
for c, href in rs[hi + 1:]:
    if not g(c, 'Площадь, м²'): continue
    u = (href or '').split('?')[0]
    raw.append(u)
    if u in DUP: continue
    key = u.rstrip('/').rsplit('/', 1)[-1]
    name, addr = NAME.get(key, ('Клубный дом', g(c, 'Корпус / секция') or '—'))
    lots.append({'name': name, 'addr': addr, 'metro': g(c, 'Метро'), 'tometro': g(c, 'До метро, мин'),
                 'area': float(g(c, 'Площадь, м²')), 'floor': g(c, 'Этаж').split('/')[0],
                 'year': g(c, 'Год дома') or '—', 'price': int(g(c, 'Цена, ₽')),
                 'ppm': int(g(c, 'Цена за м², ₽')), 'url': u, 'ours': False})
lots.append({**OUR, 'addr': 'Софийская наб., 18', 'ours': True})
lots.sort(key=lambda x: -x['ppm'])

nf = lambda v: f'{v:,.0f}'.replace(',', ' ')
d1 = lambda v: f'{v:.1f}'.replace('.', ',')
rows = [[('▶ ' if x['ours'] else '') + x['name'] + ', ' + x['addr'],
         f"{x['metro']}, {x['tometro']} мин" if x['metro'] else '—',
         d1(x['area']), x['floor'], x['year'], d1(x['price'] / 1e6), nf(x['ppm']),
         {'text': 'Циан →', 'link': x['url']}] for x in lots]

ppm = sorted(x['ppm'] for x in lots)
pr = sorted(x['price'] for x in lots)
ours = next(x for x in lots if x['ours'])
stats = {
    'ads': len(raw), 'dups': len(DUP), 'flats': len(lots) - 1, 'withOurs': len(lots),
    'rankPpm': lots.index(ours) + 1,
    'rankPrice': sorted(lots, key=lambda x: -x['price']).index(ours) + 1,
    'minPpm': ppm[0], 'maxPpm': ppm[-1], 'spread': ppm[-1] / ppm[0],
    'medPpm': ppm[len(ppm) // 2], 'medPrice': pr[len(pr) // 2],
    'vsMed': ours['ppm'] / ppm[len(ppm) // 2] - 1,
    'minArea': min(x['area'] for x in lots), 'maxArea': max(x['area'] for x in lots),
    'balchug': sum(1 for x in lots if 'Балчуг' in x['addr'] or 'Софийская' in x['addr']),
    'dearer': sum(1 for x in lots if x['ppm'] > ours['ppm']),
}

K = json.load(open('zl/zl_tables.json'))
K['niche'], K['nicheStats'], K['nicheDup'] = rows, stats, list(DUP.values())
json.dump(K, open('zl/zl_tables.json', 'w'), ensure_ascii=False, indent=1)

print(f"выдача Циан: {stats['ads']} объявлений, дублей {stats['dups']}, "
      f"квартир {stats['flats']}; с нашей — {stats['withOurs']}")
print(f"наш лот {stats['rankPpm']}-й по цене метра и {stats['rankPrice']}-й по цене; "
      f"дороже нас по метру {stats['dearer']}")
print(f"коридор {nf(stats['minPpm'])}–{nf(stats['maxPpm'])} ₽/м² (разброс {stats['spread']:.1f}×), "
      f"медиана {nf(stats['medPpm'])}, наш {stats['vsMed']:+.0%} к медиане")
print(f"площади {d1(stats['minArea'])}–{d1(stats['maxArea'])} м²; "
      f"на Балчуге и Софийской — {stats['balchug']} из {stats['withOurs']}")
for r in rows: print('  ', ' | '.join(str(c['text'] if isinstance(c, dict) else c) for c in r))
