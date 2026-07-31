"""Позиция лота внутри ЖК «Золотой» и разбор его цены на две части.

Главная проверка: «голая» цена нашего лота (280,0 млн − 87,5 × 750 тыс.
= 214,4 млн = 2 450 000 ₽/м²) против прайса застройщика «Золотого».
"""
import json, re, sys, os, collections
sys.path.insert(0, os.path.dirname(__file__))
from zl_fin import grp

L = json.load(open('zl/zl_linked.json'))
Z = L['Золотой']
OUR_URL = 'https://www.cian.ru/sale/flat/326035617/'
OUR = next(x for x in Z if x['url'] == OUR_URL)
FIT = 750_000
BARE = OUR['price'] - OUR['area'] * FIT
BARE_PPM = BARE / OUR['area']

w  = lambda g: sum(x['price'] for x in g) / sum(x['area'] for x in g)
nf = lambda v: f'{v:,.0f}'.replace(',', ' ')
d1 = lambda v: f'{v:.1f}'.replace('.', ',')
CAT = {'2': '2-комн', '3': '3-комн', '4+': '4+ комн', 'Своб. планировка': 'своб. планировка'}

# ── вся экспозиция дома ────────────────────────────────────────────────────
lots = sorted(Z, key=lambda x: -x['ppm'])
own = [[('▶ ' if x['url'] == OUR_URL else '') + CAT.get(x['cat'], x['cat'] or '—'),
        d1(x['area']), x['floor'], d1(x['price'] / 1e6), nf(x['ppm']),
        {'ready': 'ремонт есть', 'wb': 'white box', 'shell': 'бетон'}[grp(x)],
        x['seller'] or '—', {'text': 'Циан →', 'link': x['url']}] for x in lots]

# ── прайс застройщика: сколько лотов идут по одной цене метра ──────────────
dev = [x for x in Z if x['seller'] == 'Застройщик' and x['url'] != OUR_URL]
cnt = collections.Counter(x['ppm'] for x in dev)
anchor, anchorN = cnt.most_common(1)[0] if cnt else (None, 0)
priceList = [[d1(x['area']), x['floor'], d1(x['price'] / 1e6), nf(x['ppm']),
              {'text': 'Циан →', 'link': x['url']}]
             for x in sorted(dev, key=lambda x: -x['ppm'])]

# ── единственный лот с ремонтом, кроме нашего ──────────────────────────────
renov = [x for x in Z if grp(x) == 'ready' and x['url'] != OUR_URL]

# ── разбор цены на две части ───────────────────────────────────────────────
split = [
    ['Прайс застройщика «Золотого»', nf(anchor) if anchor else '—',
     f'{anchorN} лота по одной цене'],
    ['«Голая» цена нашего лота', nf(BARE_PPM), f'{d1(BARE / 1e6)} млн ₽'],
    ['Расхождение', f'{abs(BARE_PPM / anchor - 1) * 100:.2f} %'.replace('.', ',') if anchor else '—',
     'то есть его нет'],
    ['Ремонт сверху', nf(FIT), f'{d1(OUR["area"] * FIT / 1e6)} млн ₽'],
    ['Итого просят', nf(OUR['ppm']), f'{d1(OUR["price"] / 1e6)} млн ₽'],
]

stats = {
    'n': len(Z), 'all': w(Z), 'rank': lots.index(OUR) + 1,
    'vs_all': OUR['ppm'] / w(Z) - 1,
    'minArea': min(x['area'] for x in Z), 'minPrice': min(x['price'] for x in Z),
    'areaRank': sorted(Z, key=lambda x: x['area']).index(OUR) + 1,
    'priceRank': sorted(Z, key=lambda x: x['price']).index(OUR) + 1,
    'dev': sum(1 for x in Z if x['seller'] == 'Застройщик'),
    'anchor': anchor, 'anchorN': anchorN, 'gap': BARE_PPM / anchor - 1 if anchor else None,
    'bare': BARE, 'barePpm': BARE_PPM, 'fit': FIT, 'fitTotal': OUR['area'] * FIT,
    'renovPpm': renov[0]['ppm'] if renov else None,
    'renovArea': renov[0]['area'] if renov else None,
    'renovPrice': renov[0]['price'] if renov else None,
    'renovPrem': (renov[0]['ppm'] / anchor - 1) if (renov and anchor) else None,
    'ourPrem': OUR['ppm'] / anchor - 1 if anchor else None,
}

K = json.load(open('zl/zl_tables.json'))
K['own'], K['priceList'], K['split'], K['ownStats'] = own, priceList, split, stats
json.dump(K, open('zl/zl_tables.json', 'w'), ensure_ascii=False, indent=1)

print(f"экспозиция «Золотого»: {stats['n']} лотов, Ø {nf(stats['all'])} ₽/м²")
print(f"наш лот — {stats['rank']}-й по цене метра ({stats['vs_all']:+.0%} к средней), "
      f"{stats['areaRank']}-й по площади, {stats['priceRank']}-й по цене\n")
for r in own: print('  ', ' | '.join(str(c['text'] if isinstance(c, dict) else c) for c in r))
print(f"\nпрайс застройщика: {anchorN} лота идут ровно по {nf(anchor)} ₽/м²")
print(f"«голая» цена нашего лота {nf(BARE_PPM)} ₽/м² — расхождение {abs(stats['gap']):.4%}")
if renov:
    r = renov[0]
    print(f"\nединственный другой лот с ремонтом: {d1(r['area'])} м², эт. {r['floor']}, "
          f"{d1(r['price'] / 1e6)} млн ₽ = {nf(r['ppm'])} ₽/м²")
    print(f"  надбавка к прайсу застройщика: {stats['renovPrem']:+.0%}  "
          f"(наш лот просит {stats['ourPrem']:+.0%})")
