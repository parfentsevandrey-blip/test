# -*- coding: utf-8 -*-
"""Таблицы и расчёты аналитики по двум клубным домам на Арбате.

«Кло 17» (Староваганьковский переулок, 17с4) и «Фамильный дом Люче»
(Крестовоздвиженский переулок, 4) — два проекта MR Group на одном квартале
между Воздвиженкой и Знаменкой, в шестидесяти метрах друг от друга.

Считается всё по выдаче Циан: 17 и 26 лотов, срез 04.09.2026. Окрестная
когорта — предложение дороже 150 млн ₽ в пешей доступности от «Арбатской»,
«Библиотеки имени Ленина», «Боровицкой» и «Александровского сада» плюс
все новостройки района Арбат.

    python3 ac_data.py      # -> ac_tables.json
"""
import json, os
from math import radians, cos, hypot
from statistics import median as med

HERE = os.path.dirname(os.path.abspath(__file__))

nf = lambda v: f'{v:,.0f}'.replace(',', ' ')
mln = lambda v: f'{v / 1e6:,.1f}'.replace(',', ' ').replace('.', ',')
one = lambda v: f'{v:.1f}'.replace('.', ',')

QUARTER = {'first': 'I', 'second': 'II', 'third': 'III', 'fourth': 'IV'}


def plural(n, one, few, many):
    a, b = abs(int(n)) % 100, abs(int(n)) % 10
    if 10 < a < 20:
        return f'{n:.0f} {many}'
    if 1 < b < 5:
        return f'{n:.0f} {few}'
    return f'{n:.0f} {one}' if b == 1 else f'{n:.0f} {many}'


def dist(a, b):
    """Расстояние по прямой в метрах между парами (широта, долгота)."""
    dx = radians(b[1] - a[1]) * cos(radians((a[0] + b[0]) / 2)) * 6371000
    dy = radians(b[0] - a[0]) * 6371000
    return hypot(dx, dy)


def load(name):
    d = json.load(open(os.path.join(HERE, name + '.json'), encoding='utf-8'))
    lots = d['lots']
    for l in lots:
        l['ppm'] = l['priceRub'] / l['totalArea']
    return d, lots


# ── два дома ────────────────────────────────────────────────────────────────
# Координаты подтверждены геокодером OpenStreetMap по адресам: 17с4 в
# Староваганьковском — 55.751093 / 37.605955, дом 4 в Крестовоздвиженском —
# 55.751618 / 37.605522. Выдача Циан даёт те же точки с точностью до метров.
_meta = {
    'clos17': {
        'name': 'Кло 17', 'full': 'Клубный дом «Кло 17»',
        'addr': 'Староваганьковский пер., 17с4',
        'flats': 26, 'parking': 29, 'ceil': '3,2 – 3,75 м',
        'arch': 'Paris Classical Architecture',
        'grade': 'делюкс', 'point': (55.751093, 37.605955),
    },
    'luce': {
        'name': 'Люче', 'full': 'Фамильный дом «Люче»',
        'addr': 'Крестовоздвиженский пер., 4',
        'flats': 46, 'parking': 64, 'ceil': '3,9 – 4,65 м',
        'arch': 'не раскрыт',
        'grade': 'элитный', 'point': (55.751618, 37.605522),
    },
}


def house(tag):
    d, lots = load(tag)
    m = dict(_meta[tag])
    by_area = sorted(lots, key=lambda l: l['totalArea'])
    dl = next((l['deadline'] for l in lots if l.get('deadline')), None)
    m.update({
        'tag': tag, 'lots': lots, 'n': len(lots), 'byArea': by_area,
        'fetched': d['fetched'],
        'floors': max(l['floors'] or 0 for l in lots),
        'floorsOn': sorted({l['floor'] for l in lots}),
        'areaLo': by_area[0]['totalArea'], 'areaHi': by_area[-1]['totalArea'],
        'areaAvg': sum(l['totalArea'] for l in lots) / len(lots),
        'sellable': sum(l['totalArea'] for l in lots),
        'gross': sum(l['priceRub'] for l in lots),
        'priceLo': min(l['priceRub'] for l in lots),
        'priceHi': max(l['priceRub'] for l in lots),
        'ppmLo': min(l['ppm'] for l in lots), 'ppmHi': max(l['ppm'] for l in lots),
        'ppmMed': med(l['ppm'] for l in lots),
        'days': med(l['daysOnMarket'] for l in lots),
        'deadline': f"{QUARTER[dl['quarter']]} кв. {dl['year']}" if dl else '—',
    })
    return m


CLOS, LUCE = house('clos17'), house('luce')
HOUSES = [CLOS, LUCE]
ALL = CLOS['lots'] + LUCE['lots']
MID = ((CLOS['point'][0] + LUCE['point'][0]) / 2,
       (CLOS['point'][1] + LUCE['point'][1]) / 2)
BETWEEN = dist(CLOS['point'], LUCE['point'])

# ── что рядом: координаты объектов из OpenStreetMap ─────────────────────────
POINTS = [
    ('Метро «Библиотека имени Ленина»', 'узел четырёх станций: Арбатская, Боровицкая,\nАлександровский сад', (55.750826, 37.609638)),
    ('Метро «Кропоткинская»', 'Сокольническая линия', (55.745382, 37.601576)),
    ('Дом Пашкова', 'Воздвиженка, 3/5 — соседний квартал', (55.749936, 37.608674)),
    ('Красная площадь', '', (55.753591, 37.621501)),
    ('Спасская башня Кремля', '', (55.752526, 37.621353)),
    ('ГМИИ имени Пушкина', 'Волхонка, 12', (55.747285, 37.605423)),
    ('Храм Христа Спасителя', '', (55.744524, 37.605511)),
    ('Большой театр', 'Театральная площадь, 1', (55.760130, 37.618612)),
    ('Гоголевский бульвар', 'начало бульвара у Пречистенских Ворот', (55.749691, 37.599897)),
]
NEARBY = [[n, s.replace('\n', ' '), f'{dist(MID, p) / 1000:.2f} км'.replace('.', ',')]
          for n, s, p in POINTS]

# ── карточка: два дома бок о бок ────────────────────────────────────────────
CARD_ROWS = [
    ['Адрес', *[h['addr'] for h in HOUSES]],
    ['Застройщик', 'MR Group', 'MR Group'],
    ['Класс по карточкам', *[h['grade'] for h in HOUSES]],
    ['Резиденций в доме', *[str(h['flats']) for h in HOUSES]],
    ['Этажей', *[str(h['floors']) for h in HOUSES]],
    ['Потолки', *[h['ceil'] for h in HOUSES]],
    ['Машино-мест', *[str(h['parking']) for h in HOUSES]],
    ['Лотов в продаже', *[str(h['n']) for h in HOUSES]],
    ['Этажи в продаже', *[f"{h['floorsOn'][0]}–{h['floorsOn'][-1]}" for h in HOUSES]],
    ['Площади, м²', *[f"{h['areaLo']:.0f} – {h['areaHi']:.0f}" for h in HOUSES]],
    ['Средняя площадь лота, м²', *[f"{h['areaAvg']:.0f}" for h in HOUSES]],
    ['Бюджет лота, млн ₽', *[f"{mln(h['priceLo'])} – {mln(h['priceHi'])}" for h in HOUSES]],
    ['Медиана метра, ₽', *[nf(round(h['ppmMed'])) for h in HOUSES]],
    ['Разброс метра, ₽', *[f"{nf(round(h['ppmLo'], -4))} – {nf(round(h['ppmHi'], -4))}"
                           for h in HOUSES]],
    ['Отделка в объявлениях', 'без отделки', 'не заявлена'],
    ['Форма продажи', 'ДДУ, 214-ФЗ', 'свободная продажа'],
    ['Срок сдачи', *[h['deadline'] for h in HOUSES]],
    ['Медианный срок экспозиции', *[plural(h['days'], 'день', 'дня', 'дней') for h in HOUSES]],
]

# ── поквартирный прайс ──────────────────────────────────────────────────────
def lot_rows(h):
    rows = []
    for l in sorted(h['lots'], key=lambda x: (x['floor'], x['totalArea'])):
        rows.append([
            str(l['floor']), one(l['totalArea']), mln(l['priceRub']),
            nf(round(l['ppm'], -4)), f"{l['daysOnMarket']}",
            {'text': 'Циан →', 'link': l['url']},
        ])
    return rows


LOTS_CLOS, LOTS_LUCE = lot_rows(CLOS), lot_rows(LUCE)

# ── цена по этажам ──────────────────────────────────────────────────────────
# В обоих домах метр растёт с этажом, а от площади почти не зависит:
# это цена вида, а не цена метража.
FLOOR_ROWS = []
for fl in sorted({l['floor'] for l in ALL}):
    row = [str(fl)]
    for h in HOUSES:
        g = [l for l in h['lots'] if l['floor'] == fl]
        row += [str(len(g)) if g else '—',
                nf(round(med(l['ppm'] for l in g))) if g else '—']
    FLOOR_ROWS.append(row)

_f2 = [l['ppm'] for l in ALL if l['floor'] == 2]
_f5 = [l['ppm'] for l in ALL if l['floor'] == 5]
FLOOR_STEP = round((med(_f5) / med(_f2) - 1) * 100)

# ── цена по размеру лота ────────────────────────────────────────────────────
BANDS = [(0, 120, 'до 120'), (120, 180, '120 – 180'),
         (180, 240, '180 – 240'), (240, 10000, 'от 240')]
AREA_ROWS = []
for lo, hi, label in BANDS:
    g = [l for l in ALL if lo <= l['totalArea'] < hi]
    if not g:
        continue
    AREA_ROWS.append([
        label + ' м²', str(len(g)),
        nf(round(med(l['ppm'] for l in g))),
        mln(med(l['priceRub'] for l in g)),
        f"{med(l['floor'] for l in g):.0f}",
    ])

# ── окрестная когорта ───────────────────────────────────────────────────────
# Радиус — километр от точки между домами: это пешая доступность внутри
# Бульварного кольца. Порог метра отсекает несопоставимое предложение.
RADIUS_M = 1000
PPM_FLOOR = 700_000
_seen, _coh = set(), []
for src in ('arbat-new', 'near-premium', 'arbat-resale', 'metro-new'):
    for l in json.load(open(os.path.join(HERE, src + '.json'), encoding='utf-8'))['lots']:
        if l['id'] in _seen or not (l.get('lat') and l.get('priceRub') and l.get('totalArea')):
            continue
        _seen.add(l['id'])
        l['ppm'] = l['priceRub'] / l['totalArea']
        l['dist'] = dist(MID, (l['lat'], l['lng']))
        if l['dist'] > RADIUS_M or l['ppm'] < PPM_FLOOR:
            continue
        _coh.append(l)

_g = {}
for l in _coh:
    key = l.get('complex') or ' '.join(x for x in (l.get('street'), l.get('house')) if x) or '—'
    _g.setdefault(key, []).append(l)

_rows = []
for name, ls in _g.items():
    if len(ls) < 3 and name not in ('Кло 17', 'Фамильный дом Люче'):
        continue
    building = sum(1 for l in ls if l.get('houseFinished') is False)
    _rows.append({
        'name': name, 'n': len(ls),
        'dist': round(min(l['dist'] for l in ls)),
        'lat': round(med(l['lat'] for l in ls), 6),
        'lng': round(med(l['lng'] for l in ls), 6),
        'ppmMed': med(l['ppm'] for l in ls),
        'ppmLo': min(l['ppm'] for l in ls),
        'ppmHi': max(l['ppm'] for l in ls),
        'areaLo': min(l['totalArea'] for l in ls),
        'areaHi': max(l['totalArea'] for l in ls),
        'what': 'строится' if building > len(ls) / 2 else 'готов',
        'ours': name in ('Кло 17', 'Фамильный дом Люче'),
    })
_rows.sort(key=lambda r: -r['ppmMed'])

# Циан пишет названия целиком, вместе с транслитерацией в скобках, — в таблицу
# такие строки не помещаются.
SHORT = {
    'Stella di Mosca Hotel & Residences (Стелла ди Моска Хотел энд Резиденсиз)': 'Stella di Mosca',
    'De Luxe квартал апартаментов Театральный Дом (Де Люкс квартал апартаментов Театральный Дом)':
        'Театральный Дом',
    'The Book (Бук)': 'The Book',
    'Золотой, жилой квартал': 'Золотой',
    'Фамильный дом Люче': 'Люче',
}
for _r in _rows:
    _r['short'] = SHORT.get(_r['name'], _r['name'])

COHORT_ROWS = [[r['short'], r['what'], f"{r['dist']} м", str(r['n']),
                f"{r['areaLo']:.0f} – {r['areaHi']:.0f}", nf(round(r['ppmMed']))]
               for r in _rows]
COH_PINS = _rows

_others = [r for r in _rows if not r['ours']]
_above = [r for r in _others if r['ppmMed'] > CLOS['ppmMed']]
COH = {
    'total': len(_coh), 'groups': len(_rows),
    'med': nf(round(med(l['ppm'] for l in _coh))),
    'above': len(_above), 'others': len(_others),
    'building': sum(1 for l in _coh if l.get('houseFinished') is False),
    'top': _others[0]['name'] if _others else '—',
    'topPpm': nf(round(_others[0]['ppmMed'])) if _others else '—',
    'radius': RADIUS_M,
}

# ── рынок элитной первички Москвы, NF Group, I квартал 2026 ─────────────────
MKT_ELITE, MKT_PREM, MKT_LUX = 2_270_000, 1_600_000, 3_200_000
MARKET = [
    ['Средневзвешенная цена, элитный сегмент', f'{nf(MKT_ELITE)} ₽/м²', '+3 % за квартал, +9 % за год'],
    ['Класс делюкс', f'{nf(MKT_LUX)} ₽/м²', 'сегмент «Кло 17» по карточке проекта'],
    ['Класс премиум', f'{nf(MKT_PREM)} ₽/м²', 'ступень ниже'],
    ['Лотов в экспозиции', '≈ 3 200', 'квартиры и апартаменты'],
    ['Продано за квартал', '≈ 270 лотов', '−45 % к I кварталу 2025'],
    ['Средняя площадь проданного лота', '99 м²', '−15 % за год: было 116 м²'],
]

# ── где источники расходятся ────────────────────────────────────────────────
# Слева — карточки проектов (novostroy-m.ru, m2.ru, elitnoe.ru, novostroev.ru),
# справа — объявления застройщика на Циан, срез 04.09.2026.
SOURCE_ROWS = [
    ['Резиденций, «Кло 17»', '22 – 26', '17 лотов в продаже'],
    ['Срок сдачи, «Кло 17»', 'I квартал 2026', 'III квартал 2026'],
    ['Площади, «Кло 17»', '96 – 615 м²', '83 – 220 м²'],
    ['Этажность, «Люче»', '6 – 7 или 8', '7 в 25 объявлениях из 26'],
    ['Площади, «Люче»', '79 – 706 м²', '97 – 359 м²'],
    ['Отделка, «Люче»', 'дизайнерская от застройщика', 'не заявлена у 24 из 26'],
    ['Форма продажи, «Люче»', 'ДДУ и эскроу по 214-ФЗ', 'свободная продажа у 25 из 26'],
]

# ── открытые вопросы ────────────────────────────────────────────────────────
RISKS = [
    ['Число резиденций в «Кло 17» источники называют по-разному',
     '«Новострой-М» пишет о 26 резиденциях, elitnoe.ru — о 22. В продаже на Циан '
     '17 лотов; сколько квартир в доме всего, проектная декларация в карточках '
     'не раскрывает.'],
    ['Срок сдачи «Кло 17» расходится на два квартала',
     'карточки проекта называют I квартал 2026 года, объявления застройщика — III. '
     'В справке стоит срок из объявлений: они обновляются чаще.'],
    ['Форма продажи в «Люче» в объявлениях помечена как свободная',
     'у 25 лотов из 26 стоит «свободная продажа» вместо ДДУ, хотя карточки проекта '
     'говорят о 214-ФЗ и эскроу. Для строящегося дома это расхождение стоит '
     'проверить по проектной декларации.'],
    ['Отделка «Люче» в объявлениях не заявлена',
     'поле пустое у 24 лотов из 26, у одного стоит чистовая, у одного — без отделки. '
     'Карточки проекта при этом обещают дизайнерскую отделку от застройщика.'],
    ['Площади в карточках шире, чем в продаже',
     'по «Кло 17» карточки называют 96–615 м², в объявлениях 83–220 м²; по «Люче» — '
     '79–706 м² против 97–359 м². Верхние лоты и пентхаусы в открытую продажу '
     'не выведены.'],
    ['Лоты стоят в экспозиции больше года',
     f"медианный срок — {plural(CLOS['days'], 'день', 'дня', 'дней')} в «Кло 17» "
     f"и {plural(LUCE['days'], 'день', 'дня', 'дней')} в «Люче». "
     'Цены объявлений за это время не менялись: движения по ним в открытых '
     'источниках нет.'],
]

# ── визуализации: что на кадрах ─────────────────────────────────────────────
SHOTS_CLOS = [
    ('clos_fas', 'Фасад со стороны Староваганьковского переулка'),
    ('clos_view', 'Вид с верхнего уровня на купола Знаменки'),
    ('clos_yard', 'Приватный сад во внутреннем дворе'),
    ('clos_lobby', 'Входная группа и лобби'),
    ('clos_liv', 'Гостиная резиденции'),
    ('clos_stair', 'Лестница общей зоны'),
]
SHOTS_LUCE = [
    ('luce_fas', 'Фасад со стороны Крестовоздвиженского переулка'),
    ('luce_air', 'Квартал с высоты: дом в глубине застройки'),
    ('luce_terr', 'Терраса верхнего уровня'),
    ('luce_ent', 'Въездная группа вечером'),
    ('luce_lobby', 'Лобби'),
    ('luce_park', 'Подземный паркинг'),
]

_cards_path = os.path.join(HERE, 'ac_cards.json')
CARDS = json.load(open(_cards_path, encoding='utf-8')) if os.path.exists(_cards_path) else []

if __name__ == '__main__':
    K = {
        'cards': CARDS,
        'cardRows': CARD_ROWS,
        'lotsClos': LOTS_CLOS,
        'lotsLuce': LOTS_LUCE,
        'floorRows': FLOOR_ROWS,
        'areaRows': AREA_ROWS,
        'cohortRows': COHORT_ROWS,
        'nearby': NEARBY,
        'market': MARKET,
        'sourceRows': SOURCE_ROWS,
        'risks': RISKS,
        'shotsClos': SHOTS_CLOS,
        'shotsLuce': SHOTS_LUCE,
        'coh': COH,
        'nums': {
            'n': len(ALL), 'nClos': CLOS['n'], 'nLuce': LUCE['n'],
            'between': f'{BETWEEN:.0f}',
            'ppmMed': nf(round(med(l['ppm'] for l in ALL))),
            'ppmClos': nf(round(CLOS['ppmMed'])), 'ppmLuce': nf(round(LUCE['ppmMed'])),
            'priceLo': mln(min(l['priceRub'] for l in ALL)),
            'priceHi': mln(max(l['priceRub'] for l in ALL)),
            'areaLo': f"{min(l['totalArea'] for l in ALL):.0f}",
            'areaHi': f"{max(l['totalArea'] for l in ALL):.0f}",
            'sellable': nf(round(CLOS['sellable'] + LUCE['sellable'])),
            'gross': mln((CLOS['gross'] + LUCE['gross']) / 1000),
            'deadline': CLOS['deadline'],
            'floorStep': str(FLOOR_STEP),
            'days': f"{med(l['daysOnMarket'] for l in ALL):.0f}",
            'toElite': f'{(med(l["ppm"] for l in ALL) / MKT_ELITE - 1) * 100:.0f}',
            'toLux': f'{(1 - med(l["ppm"] for l in ALL) / MKT_LUX) * 100:.0f}',
            'kremlin': f"{dist(MID, (55.752526, 37.621353)) / 1000:.1f}".replace('.', ','),
            'metro': f"{dist(MID, (55.750826, 37.609638)):.0f}",
            'flats': CLOS['flats'] + LUCE['flats'],
            'parking': CLOS['parking'] + LUCE['parking'],
            'penthouse': mln(LUCE['priceHi']),
            'penthouseArea': f"{LUCE['areaHi']:.0f}",
            'penthousePpm': nf(round(LUCE['ppmHi'], -4)),
        },
    }
    json.dump(K, open(os.path.join(HERE, 'ac_tables.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    json.dump(COH_PINS, open(os.path.join(HERE, 'ac_pins.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    print('ac_tables.json готов')
    for k, v in K['nums'].items():
        print(f'  {k:14s} {v}')
    print(f"\nкогорта: {COH['total']} лотов, {COH['groups']} групп, медиана {COH['med']} ₽")
    for r in COHORT_ROWS:
        print('   ' + '  '.join(f'{x:>12s}' if i else f'{x:34s}' for i, x in enumerate(r)))
