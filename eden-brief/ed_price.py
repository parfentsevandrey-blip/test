"""Ценовое сравнение EDEN с пятью конкурентами по выгрузкам Циан от 17.08.2026.

EDEN продаётся без отделки, поэтому сравнение приводится к одному состоянию —
«квартира, в которую можно заехать». Там, где конкурент тоже без отделки,
к цене метра добавляется стоимость отделки де-люкс уровня: 750 тыс. ₽/м²,
тот же ориентир, что и для других домов де-люкс в центре.

Цена EDEN — не из выгрузки: проект продаётся закрыто. Она посчитана
из опубликованного старта продаж «от 1,1 млрд ₽ за резиденцию около
250 кв. м» и помечена в таблицах как расчётная.
"""
import json, collections

L = json.load(open('eden/ed_lots.json'))
FIT = 750_000                       # отделка де-люкс, ₽/м²
ED_PPM = 4_400_000                  # расчётная цена метра EDEN
ED_AREA_LO, ED_AREA_HI = 107.3, 410.6

SHORT = {
    'Stella di Mosca Hotel & Residences (Стелла ди Моска Хотел энд Резиденсиз)': 'Stella di Mosca',
    'Turandot Residences (Турандот Резиденсес)': 'Turandot Residences',
    'Никитский-6': 'Никитский-6',
    'Фамильный дом Люче': 'Фамильный дом Люче',
    'Кло 17': 'Кло 17 (Clos 17)',
}
ADDR = {
    'Stella di Mosca':     'Б. Никитская, 9/15',
    'Turandot Residences': 'ул. Арбат, 24',
    'Никитский-6':         'Никитский б-р, 6/20',
    'Фамильный дом Люче':  'Крестовоздвиженский пер., 4',
    'Кло 17 (Clos 17)':    'Староваганьковский пер., 17',
}
DEV = {
    'Stella di Mosca':     'вторичка, агентства',
    'Turandot Residences': 'Valartis Group',
    'Никитский-6':         'R4S / СЗ «Третий Рим»',
    'Фамильный дом Люче':  'MR Group',
    'Кло 17 (Clos 17)':    'MR Group',
}

nf = lambda v: f'{v:,.0f}'.replace(',', ' ')
d1 = lambda v: f'{v:.1f}'.replace('.', ',')
w = lambda g: sum(x['price'] for x in g) / sum(x['area'] for x in g)

BARE = {'Без отделки', 'Черновая', ''}
bare = lambda x: (x['fin'] or '') in BARE


def block(name, lots):
    n = len(lots)
    nb = sum(1 for x in lots if bare(x))
    base = w(lots)
    fit = FIT * nb / n                       # доплата пропорционально доле бетона
    why = ('всё без отделки' if nb == n else
           'всё с отделкой' if nb == 0 else
           f'{n - nb} с отделкой, {nb} без')
    return [name, ADDR[name], str(n),
            f"{d1(min(x['area'] for x in lots))}–{d1(max(x['area'] for x in lots))}",
            f"{d1(min(x['price'] for x in lots) / 1e6)}–{d1(max(x['price'] for x in lots) / 1e6)}",
            nf(base), why, nf(base + fit)], base + fit


rows, ready = [], {}
# EDEN идёт первой строкой и считается по расчётной цене
rows.append(['▶ EDEN Private Residence', 'Н. Кисловский пер., 7', '22',
             f'{d1(ED_AREA_LO)}–{d1(ED_AREA_HI)}', '≈ 470–1 807',
             f'≈ {nf(ED_PPM)}', 'всё без отделки', f'≈ {nf(ED_PPM + FIT)}'])
ready['EDEN Private Residence'] = ED_PPM + FIT

for full, short in SHORT.items():
    if full not in L:
        continue
    r, key = block(short, L[full])
    rows.append(r)
    ready[short] = key

# ── что покупают за сопоставимые деньги: топ лотов ────────────────────────
allx = [dict(x, jk=SHORT[k]) for k, v in L.items() for x in v]
band = [x for x in allx if 200 <= x['area'] <= 420]      # формат EDEN
top = sorted(band, key=lambda x: -x['price'])[:12]
city = []
for x in top:
    fin = x['fin'] or 'не указана'
    key = x['ppm'] + (FIT if bare(x) else 0)
    city.append([x['jk'], f"{d1(x['area'])} м², эт. {x['floor']}", fin.lower(),
                 d1(x['price'] / 1e6), nf(key),
                 {'text': 'Циан →', 'link': x['url']}])

stats = {
    'n': len(allx), 'nProj': len(L),
    'wAll': w(allx), 'wAllKey': None,
    'band': len(band),
    'billion': sum(1 for x in allx if x['price'] >= 1e9),
    'edPpm': ED_PPM, 'edKey': ED_PPM + FIT, 'fit': FIT,
    'vsAll': ED_PPM / w(allx) - 1,
    'maxPpm': max(x['ppm'] for x in allx),
    'minPpm': min(x['ppm'] for x in allx),
    'dearer': sum(1 for x in allx if x['ppm'] > ED_PPM),
    'finMix': collections.Counter(x['fin'] or '—' for x in allx).most_common(),
}
stats['wAllKey'] = w(allx) + FIT * sum(1 for x in allx if bare(x)) / len(allx)

if __name__ == '__main__':
    K = json.load(open('eden/ed_tables.json'))
    K['price'], K['city'], K['priceStats'] = rows, city, stats
    json.dump(K, open('eden/ed_tables.json', 'w'), ensure_ascii=False, indent=1)
    for r in rows:
        print(' | '.join(str(c) for c in r))
    print()
    print(f"всего {stats['n']} лотов в {stats['nProj']} проектах; Ø {nf(stats['wAll'])} ₽/м²")
    print(f"  приведённый к «под ключ» Ø {nf(stats['wAllKey'])} ₽/м²")
    print(f"  EDEN {nf(ED_PPM)} ₽/м² — это {stats['vsAll']:+.0%} к средней по конкурентам")
    print(f"  лотов дороже EDEN по метру: {stats['dearer']} из {stats['n']}")
    print(f"  разброс метра: {nf(stats['minPpm'])} – {nf(stats['maxPpm'])} ₽")
    print(f"  лотов дороже 1 млрд ₽: {stats['billion']}")
    print(f"  в формате EDEN (200–420 м²): {stats['band']} лотов")
    print(f"  отделка: {stats['finMix']}")
