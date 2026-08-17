"""Ценовое сравнение EDEN с шестью конкурентами по выгрузкам Циан от 17.08.2026.

EDEN продаётся без отделки, поэтому сравнение приводится к одному состоянию —
«квартира, в которую можно заехать». Там, где конкурент тоже без отделки,
к цене метра добавляется стоимость отделки де-люкс уровня: 750 тыс. ₽/м²,
тот же ориентир, что и для других домов де-люкс в центре.

Цена EDEN — не из выгрузки: проект продаётся закрыто. За базовую принята
оценка 3,5 млн ₽ за метр; расчёт по опубликованному старту продаж
(«от 1,1 млрд ₽ за резиденцию около 250 кв. м») даёт 4,4 млн — это верхняя
граница диапазона. Обоснование — в ed_data.py.
"""
import json, collections, os

L = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'ed_lots.json')))
FIT = 750_000                       # отделка де-люкс, ₽/м²
ED_PPM = 3_500_000                  # базовая оценка метра EDEN (см. ed_data.py)
ED_AREA_LO, ED_AREA_HI = 107.3, 410.6

SHORT = {
    'Stella di Mosca Hotel & Residences (Стелла ди Моска Хотел энд Резиденсиз)': 'Stella di Mosca',
    'Turandot Residences (Турандот Резиденсес)': 'Turandot Residences',
    'Никитский-6': 'Никитский-6',
    'Фамильный дом Люче': 'Фамильный дом Люче',
    'Кло 17': 'Кло 17 (Clos 17)',
    'БРЮСОВ': 'Брюсов',
}
ADDR = {
    'Stella di Mosca':     'Б. Никитская, 9/15',
    'Turandot Residences': 'ул. Арбат, 24',
    'Никитский-6':         'Никитский б-р, 6/20',
    'Фамильный дом Люче':  'Крестовоздвиженский пер., 4',
    'Кло 17 (Clos 17)':    'Староваганьковский пер., 17',
    'Брюсов':              'Брюсов пер., 2',
}
DEV = {
    'Stella di Mosca':     'вторичка, агентства',
    'Turandot Residences': 'Valartis Group',
    'Никитский-6':         'R4S / СЗ «Третий Рим»',
    'Фамильный дом Люче':  'MR Group',
    'Кло 17 (Clos 17)':    'MR Group',
    'Брюсов':              'клубный дом на 17 резиденций',
}

# Пустое Циан-поле «Отделка/ремонт» само по себе не значит «бетон». У одного
# лота — вторичной квартиры 209,0 м² в «Брюсове» (дом 1914 года, продаёт
# агентство) — поле пустое, но это готовое жильё, а не бетон от застройщика.
# Ремонт в описании прямо не назван, поэтому лот не считается ни «под ключ»,
# ни «без отделки»: доплата за отделку к нему просто не прибавляется.
UNKNOWN_FIN = {'332940679'}
lot_id = lambda x: (x['url'].rsplit('/flat/', 1)[-1].split('/')[0] if '/flat/' in x['url'] else '')

nf = lambda v: f'{v:,.0f}'.replace(',', ' ')
d1 = lambda v: f'{v:.1f}'.replace('.', ',')
w = lambda g: sum(x['price'] for x in g) / sum(x['area'] for x in g)

BARE = {'Без отделки', 'Черновая', ''}
bare = lambda x: (x['fin'] or '') in BARE and lot_id(x) not in UNKNOWN_FIN


def block(name, lots):
    n = len(lots)
    nb = sum(1 for x in lots if bare(x))
    nu = sum(1 for x in lots if lot_id(x) in UNKNOWN_FIN)
    base = w(lots)
    fit = FIT * nb / n                       # доплата пропорционально доле бетона
    why = ('всё без отделки' if nb == n else
           'всё с отделкой' if nb == 0 and nu == 0 else
           ', '.join(p for p in (f'{n - nb - nu} с отделкой' if n - nb - nu else '',
                                 f'{nb} без' if nb else '',
                                 f'{nu} не указана' if nu else '') if p))
    return [name, ADDR[name], str(n),
            f"{d1(min(x['area'] for x in lots))}–{d1(max(x['area'] for x in lots))}",
            f"{d1(min(x['price'] for x in lots) / 1e6)}–{d1(max(x['price'] for x in lots) / 1e6)}",
            nf(base), why, nf(base + fit)], base + fit


rows, ready = [], {}
# EDEN идёт первой строкой и считается по расчётной цене
rows.append(['▶ EDEN Private Residence', 'Н. Кисловский пер., 7', '22',
             f'{d1(ED_AREA_LO)}–{d1(ED_AREA_HI)}', '≈ 376–1 437',
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
    K = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'ed_tables.json')))
    K['price'], K['city'], K['priceStats'] = rows, city, stats
    json.dump(K, open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'ed_tables.json'), 'w'), ensure_ascii=False, indent=1)
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
