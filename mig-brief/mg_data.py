# -*- coding: utf-8 -*-
"""Таблицы и расчёты справки по кварталу «МИГ» на Ленинградском проспекте.

Поквартирного прайса у проекта нет: продажи не открыты, брокерские карточки
пишут «готовность — проектирование» и собирают лист ожидания. Поэтому здесь
не парсинг прайса, а сведение цифр из открытых источников и та арифметика,
которую они позволяют: экономика участка по договору КРТ и то, во сколько
локация оценивает метр сегодня.

Рынок вокруг собран клиентом из `tools/cian` ветки
`claude/cian-apartments-cao-7iqapp`, срез 22.09.2026.

    python3 mg_data.py      # -> mg_tables.json, mg_pins.json
"""
import json, os
from math import radians, cos, hypot
from statistics import median as med

HERE = os.path.dirname(os.path.abspath(__file__))

nf = lambda v: f'{v:,.0f}'.replace(',', ' ')
mln = lambda v: f'{v / 1e6:,.1f}'.replace(',', ' ').replace('.', ',')
one = lambda v: f'{v:.1f}'.replace('.', ',')
QUARTER = {'first': 'I', 'second': 'II', 'third': 'III', 'fourth': 'IV',
           1: 'I', 2: 'II', 3: 'III', 4: 'IV'}


def plural(n, one_, few, many):
    a, b = abs(int(n)) % 100, abs(int(n)) % 10
    if 10 < a < 20:
        return f'{n:.0f} {many}'
    if 1 < b < 5:
        return f'{n:.0f} {few}'
    return f'{n:.0f} {one_}' if b == 1 else f'{n:.0f} {many}'


def dist(a, b):
    """Расстояние по прямой в метрах между парами (широта, долгота)."""
    dx = radians(b[1] - a[1]) * cos(radians((a[0] + b[0]) / 2)) * 6371000
    dy = radians(b[0] - a[0]) * 6371000
    return hypot(dx, dy)


# ── участок и проект ────────────────────────────────────────────────────────
# Границы территории — Ленинградский проспект, 1-й и 2-й Боткинские проезды,
# улицы Авиаконструктора Сухого и Маргелова. Центр контура взят по карте.
SITE = (55.7859, 37.5510)
AREA_HA = 63.55                  # площадь КРТ по договору
TOTAL_SQM = 2_300_000            # объём застройки, «Московская перспектива»
HOUSING_SQM = 1_400_000          # жильё в объёме застройки
FLATS = 19_000                   # оценка брокеров по числу квартир
LANDSCAPE_HA = 6.1               # благоустройство по данным Стройкомплекса
LOT_PRICE = 987_900_000          # стартовая цена лота на торгах КРТ
INVEST = 300_000_000_000         # оценка инвестиций в проект
KRT_YEARS = 26                   # срок договора КРТ
RENOVATION_SQM = 30_000          # квартиры под реновацию, передаются городу
FLOORS_MAX = 50

# Станции метро — точки вестибюлей по геокодеру Яндекса (через Циан); точки
# OpenStreetMap для «Динамо» и «ЦСКА» оказались автобусными остановками.
DINAMO = (55.789735, 37.558239)
PPARK = (55.791922, 37.557093)
CSKA = (55.786597, 37.533283)

# ── рынок: NF Group и выдача Циан ───────────────────────────────────────────
MKT_BIZ, MKT_PREM, MKT_ELITE = 620_000, 1_600_000, 2_270_000


def load(name):
    d = json.load(open(os.path.join(HERE, name + '.json'), encoding='utf-8'))
    out = []
    for l in d['lots']:
        if not (l.get('priceRub') and l.get('totalArea') and l.get('lat')):
            continue
        l['ppm'] = l['priceRub'] / l['totalArea']
        # дешевле 250 тыс. ₽ за м² в этой части города — доли, комнаты или
        # ошибка площади в объявлении; в когорту такие лоты не берутся
        if l['ppm'] < 250_000:
            continue
        l['dist'] = dist(SITE, (l['lat'], l['lng']))
        out.append(l)
    return d, out


# Новостройки — пешая доступность от «Динамо» и «ЦСКА» плюс всё строящееся
# в четырёх районах вокруг участка (Беговой, Хорошёвский, Аэропорт,
# Савёловский). Готовые дома — пешая доступность от тех же двух станций
# (фильтр «дом 2015 года и новее» Циан соблюдает нестрого, в выдачу попадают
# и старые дома). Одно объявление может прийти из нескольких запросов, дубли
# по id отбрасываются.
FEEDS_NEW = ['metro-new', 'cska-new', 'begovoy-new', 'khoroshevsky-new',
             'aeroport-new', 'savelovsky-new']
FEEDS_RES = ['metro-resale', 'cska-resale']
FEED_META = {}


def merge(names):
    seen, out = set(), []
    for n in names:
        if not os.path.exists(os.path.join(HERE, n + '.json')):
            continue
        meta, ls = load(n)
        FEED_META[n] = (meta.get('declaredCount'), len(meta['lots']))
        for l in ls:
            if l['id'] not in seen:
                seen.add(l['id'])
                out.append(l)
    return out


NEW = merge(FEEDS_NEW)
RES = merge(FEEDS_RES)
ALL = NEW + RES

# ── карточка проекта ────────────────────────────────────────────────────────
CARD = [
    ['Адрес', 'Ленинградский проспект, 33'],
    ['Второй адрес в карточках', '1-й Боткинский проезд, вл. 7'],
    ['Район', 'Беговой, САО'],
    ['Площадь территории', f'{AREA_HA} га'.replace('.', ',')],
    ['Объём застройки', f'{TOTAL_SQM / 1e6:.1f} млн м²'.replace('.', ',')],
    ['Из них жильё', f'{HOUSING_SQM / 1e6:.1f} млн м²'.replace('.', ',')],
    ['Квартир', f'≈ {nf(FLATS)}'],
    ['Этажность', f'до {FLOORS_MAX} этажей'],
    ['Класс', 'Бизнес и премиум'],
    ['Отделка', 'Без отделки'],
    ['Площади', 'от 35 м², семейные от 60 м²'],
    ['Благоустройство', f'{LANDSCAPE_HA} га'.replace('.', ',')],
    ['Мастер-план', 'бюро «Камень»'],
    ['Застройщик', 'ООО СЗ «Энимерози», Capital Group'],
    ['Договор КРТ', f'{KRT_YEARS} лет, с августа 2023'],
    ['Первый дом', '311 квартир, сдача во II кв. 2028'],
    ['Статус продаж', 'Не открыты, лист ожидания'],
]

# ── экономика участка ───────────────────────────────────────────────────────
# Единственные публичные деньги проекта — цена лота на торгах и оценка
# инвестиций. Отнесённые к метру жилья, они дают нижнюю границу себестоимости.
LAND_PER_SQM = LOT_PRICE / HOUSING_SQM
INVEST_PER_SQM = INVEST / HOUSING_SQM
FLAT_AVG = HOUSING_SQM / FLATS

ECONOMY = [
    ['Стартовая цена лота на торгах КРТ', f'{mln(LOT_PRICE)} млн ₽'],
    ['Оценка инвестиций в проект', f'≈ {nf(INVEST / 1e9)} млрд ₽'],
    ['Жилья в проекте', f'{HOUSING_SQM / 1e6:.1f} млн м²'.replace('.', ',')],
    ['Цена участка на метр жилья', f'{nf(LAND_PER_SQM)} ₽'],
    ['Инвестиции на метр жилья', f'≈ {nf(round(INVEST_PER_SQM, -3))} ₽'],
    ['Средняя квартира в проекте', f'≈ {FLAT_AVG:.0f} м²'],
    ['Квартиры городу под реновацию', f'{nf(RENOVATION_SQM)} м²'],
    ['Срок договора КРТ', f'{KRT_YEARS} лет, до 2049 года'],
]

# ── из чего складывается объём застройки ────────────────────────────────────
COMPOSITION = [
    ['Всего недвижимости', f'{TOTAL_SQM / 1e6:.1f} млн м²'.replace('.', ','), 'договор КРТ, данные Стройкомплекса'],
    ['Жильё', f'{HOUSING_SQM / 1e6:.1f} млн м²'.replace('.', ','), f'≈ {nf(FLATS)} квартир, средняя {HOUSING_SQM / FLATS:.0f} м²'],
    ['Коммерция, офисы, сервис', f'≈ {(TOTAL_SQM - HOUSING_SQM) / 1e6:.1f} млн м²'.replace('.', ','), 'остаток объёма за вычетом жилья'],
    ['Квартиры городу под реновацию', f'{nf(RENOVATION_SQM)} м²', 'передаются безвозмездно'],
    ['Благоустройство', f'{LANDSCAPE_HA} га'.replace('.', ','), 'парк с прудом, бульвар, дворы'],
    ['Социальные объекты', 'школы, детсады, поликлиника', 'плюс ФОК с бассейном и отделение МВД'],
    ['Высотные акценты', f'до {FLOORS_MAX} этажей', 'по данным брокерских карточек'],
    ['Горизонт проекта', f'{KRT_YEARS} лет', 'договор КРТ до 2049 года'],
]

# ── что рядом: координаты из OpenStreetMap ──────────────────────────────────
POINTS = [
    ('Метро «Динамо»', 'Замоскворецкая линия', DINAMO),
    ('Метро «Петровский парк»', 'Большая кольцевая линия', PPARK),
    ('Метро «ЦСКА»', 'Большая кольцевая линия', CSKA),
    ('ВТБ Арена и стадион «Динамо»', '', (55.789925, 37.568919)),
    ('Петровский парк', '22 га', (55.794236, 37.555670)),
    ('Боткинская больница', '2-й Боткинский проезд, 5', (55.782898, 37.553058)),
    ('Парк Ходынское поле', '25 га', (55.787047, 37.528137)),
    ('Белорусский вокзал', 'МЦД-1 и МЦД-4', (55.776371, 37.581701)),
]
NEARBY = [[n, s, f'{dist(SITE, p) / 1000:.2f} км'.replace('.', ',')] for n, s, p in POINTS]

# ── когорта: что продаётся вокруг ───────────────────────────────────────────
RADIUS_M = 2500
_coh = [l for l in ALL if l['dist'] <= RADIUS_M]
# Группа — это дом плюс стадия: в «Прайм Парке» одни корпуса ещё строятся,
# другие уже перепродаются, и смешивать их в одну строку нельзя.
_g = {}
for _l in _coh:
    _key = _l.get('complex') or ' '.join(x for x in (_l.get('street'), _l.get('house')) if x) or '—'
    _stage = 'строится' if _l.get('houseFinished') is False else 'готов'
    _g.setdefault((_key, _stage), []).append(_l)

SHORT = {
    'Прайм Парк (Prime Park)': 'Прайм Парк',
    'Комплекс апартаментов Alcon Tower (Алкон Тауэр)': 'Alcon Tower',
    'Квартал апартаментов Искра-Парк': 'Искра-Парк',
    'ВТБ Арена парк': 'ВТБ Арена парк',
    'Авторская коллекция резиденций МУЗА': 'МУЗА',
    'Премиальный дом МАСТЕРС': 'МАСТЕРС',
}

_rows = []
for (_name, _stage), _ls in _g.items():
    if len(_ls) < 3:
        continue
    _ppm = sorted(x['ppm'] for x in _ls)
    _yrs = [x['buildYear'] for x in _ls if x.get('buildYear')]
    _dl = [x['deadline'] for x in _ls if x.get('deadline')]
    _rows.append({
        'name': _name, 'short': SHORT.get(_name, _name), 'n': len(_ls),
        'dist': round(min(x['dist'] for x in _ls)),
        'lat': round(med([x['lat'] for x in _ls]), 6),
        'lng': round(med([x['lng'] for x in _ls]), 6),
        'ppmLo': _ppm[0], 'ppmHi': _ppm[-1], 'ppmMed': med(_ppm),
        'areaLo': min(x['totalArea'] for x in _ls),
        'areaHi': max(x['totalArea'] for x in _ls),
        'what': _stage,
        'year': max(_yrs) if _yrs else None,
        'deadline': (f"{QUARTER[_dl[0]['quarter']]} кв. {_dl[0]['year']}"
                     if _dl and _dl[0].get('quarter') else None),
        'apart': sum(1 for x in _ls if x.get('isApartments')),
    })
# Порядок один для таблиц, графика и карты: сначала стройка, потом готовые
# дома, внутри — по убыванию медианы. Номер группы — номер пина на карте.
_rows.sort(key=lambda r: (r['what'] != 'строится', -r['ppmMed']))
for _i, _r in enumerate(_rows, 1):
    _r['num'] = _i
    _r['cska'] = round(dist(CSKA, (_r['lat'], _r['lng'])))
    _r['dinamo'] = round(dist(DINAMO, (_r['lat'], _r['lng'])))
COH_PINS = _rows

_new_rows = [r for r in _rows if r['what'] == 'строится']
_res_rows = [r for r in _rows if r['what'] == 'готов']
_rng = lambda r: f"{r['ppmLo'] / 1e6:.2f} – {r['ppmHi'] / 1e6:.2f}".replace('.', ',')
NEW_ROWS = [[str(r['num']), r['short'], r['deadline'] or '—', f"{r['dist']} м", str(r['n']),
             f"{r['areaLo']:.0f} – {r['areaHi']:.0f}", _rng(r), nf(round(r['ppmMed']))]
            for r in _new_rows]
RES_ROWS = [[str(r['num']), r['short'], str(r['year'] or '—'), f"{r['dist']} м", str(r['n']),
             f"{r['areaLo']:.0f} – {r['areaHi']:.0f}", _rng(r), nf(round(r['ppmMed']))]
            for r in _res_rows]
# Дома у «ЦСКА»: не дальше 1,5 км от станции и ближе к ней, чем к «Динамо».
_cska_rows = sorted([r for r in _rows if r['cska'] <= 1500 and r['cska'] < r['dinamo']],
                    key=lambda r: r['cska'])
CSKA_ROWS = [[str(r['num']), r['short'], r['what'], f"{r['cska']} м", f"{r['dist']} м",
              str(r['n']), nf(round(r['ppmMed']))] for r in _cska_rows]

_new_lots = [l for l in _coh if l.get('houseFinished') is False]
_res_lots = [l for l in _coh if l.get('houseFinished') is not False]
COH = {
    'total': len(_coh), 'groups': len(_rows),
    'new': len(_new_lots), 'resale': len(_res_lots),
    'med': nf(round(med([l['ppm'] for l in _coh]))),
    'newMed': nf(round(med([l['ppm'] for l in _new_lots]))) if _new_lots else '—',
    'resMed': nf(round(med([l['ppm'] for l in _res_lots]))) if _res_lots else '—',
    'lo': nf(round(min(l['ppm'] for l in _coh), -3)),
    'hi': nf(round(max(l['ppm'] for l in _coh), -3)),
    'apart': sum(1 for l in _coh if l.get('isApartments')),
    'radius': RADIUS_M,
    'newGroups': len(_new_rows), 'resGroups': len(_res_rows),
    'cskaGroups': len(_cska_rows), 'cskaLots': sum(r['n'] for r in _cska_rows),
}

# ── чем различаются стройки вокруг ──────────────────────────────────────────
DEC = {'without': 'без отделки', 'fine': 'чистовая', 'preFine': 'предчистовая',
       'rough': 'черновая', 'fineWithFurniture': 'под ключ'}


def _detail(r):
    ls = _g[(r['name'], r['what'])]
    dec = {}
    for l in ls:
        if l.get('decoration'):
            dec[l['decoration']] = dec.get(l['decoration'], 0) + 1
    # если отделку заявляет меньше половины лотов, самый частый вариант
    # ничего не говорит о проекте
    top = (DEC.get(max(dec, key=dec.get), '—')
           if dec and sum(dec.values()) >= len(ls) / 2 else 'не указана')
    return [str(r['num']), r['short'], f"{max(l.get('floors') or 0 for l in ls)}",
            r['deadline'] or '—', top, str(r['n']),
            f"{med([l['totalArea'] for l in ls]):.0f}", nf(round(r['ppmMed']))]


NEW_DETAIL = [_detail(r) for r in _new_rows]

# ── бюджет квартиры по ценам локации ────────────────────────────────────────
# Метр покупки — медиана строящегося предложения вокруг; отделка бизнес- и
# премиум-класса в Москве — 150–250 тыс. ₽ за метр, в таблице середина вилки.
PPM_LOCAL = med([l['ppm'] for l in _new_lots]) if _new_lots else 0
FINISH = 200_000
BUDGET_AREAS = [35, 45, 60, 74, 100, 140]
BUDGET = [[f'{a}', mln(a * PPM_LOCAL), mln(a * FINISH), mln(a * (PPM_LOCAL + FINISH))]
          for a in BUDGET_AREAS]

# ── что даёт тот же бюджет в готовых домах рядом ────────────────────────────
BUDGETS = [30_000_000, 45_000_000, 60_000_000, 80_000_000]
_ready = [l for l in _coh if l.get('houseFinished') is not False]
_build = [l for l in _coh if l.get('houseFinished') is False]
EQUAL_ROWS = []
for _b in BUDGETS:
    row = [f'{_b / 1e6:.0f} млн ₽']
    for _pool in (_build, _ready):
        _l = min(_pool, key=lambda x: abs(x['priceRub'] - _b))
        _nm = SHORT.get(_l.get('complex') or '', _l.get('complex')) or _l.get('street')
        row += [f"{_nm}, {_l['totalArea']:.0f} м²", mln(_l['priceRub'])]
    EQUAL_ROWS.append(row)

MARKET = [
    ['Медиана метра в новостройках рядом', f"{COH['newMed']} ₽/м²", 'Циан, 22.09.2026'],
    ['Медиана метра в готовых домах рядом', f"{COH['resMed']} ₽/м²", 'у «Динамо» и «ЦСКА»'],
    ['Разброс по когорте', f"{COH['lo']} – {COH['hi']} ₽/м²", f"{COH['total']} лотов"],
    ['Средневзвешенная цена, премиум Москвы', f'{nf(MKT_PREM)} ₽/м²', 'данные NF Group, I кв. 2026'],
    ['Средневзвешенная цена, элитный сегмент', f'{nf(MKT_ELITE)} ₽/м²', 'данные NF Group, I кв. 2026'],
    ['Средняя квартира в проекте', f'≈ {FLAT_AVG:.0f} м²', '1,4 млн м² на 19 тысяч квартир'],
]

# ── что известно о ценах ────────────────────────────────────────────────────
# Прайса у «МИГа» нет. Ближайшая точка отсчёта — текущий прайс соседнего
# проекта того же застройщика: МАСТЕРС на улице Викторенко, 16, в 2 км от
# центра участка. Выгрузка — открытый каталог сайта cg-projects.ru
# (api/properties, project=masters), срез 22.09.2026: цена со скидкой
# «Лучшая цена до 30.09.2026» и исходная цена прайса.
BROKER_FROM = 500_000            # «ориентировочные цены — от 500 000 ₽/м²»
FIRST_HOUSE = {'flats': 311, 'sqm': 33_479, 'height': 80,
               'invest': 6_200_000_000, 'deadline': 'II кв. 2028'}
_ms_path = os.path.join(HERE, 'masters-cg.json')
MASTERS = json.load(open(_ms_path, encoding='utf-8'))['items'] if os.path.exists(_ms_path) else []
MS_SITE = (55.79433, 37.522135)
ROOMS_RU = {0: 'Студии', 1: 'Однокомнатные', 2: 'Двухкомнатные', 3: 'Трёхкомнатные', 4: 'Четырёхкомнатные'}

PRICE_SRC = [
    ['Брокерские карточки проекта', '«Ориентировочные цены от 500 000 ₽/м²», старт продаж в 2026 году', '2025 – 2026'],
    ['Проектная декларация первого дома', '311 квартир, 33 479 м², высота около 80 м, сдача во II кв. 2028, инвестиции 6,2 млрд ₽', 'июль 2026'],
    ['Сайт Capital Group', 'Проекта «МИГ» в каталоге нет, в продаже МАСТЕРС на ул. Викторенко, 16', '22.09.2026'],
]
MS_ROWS, MS = [], {}
if MASTERS:
    for _r in sorted({x['rooms'] for x in MASTERS}):
        _xs = [x for x in MASTERS if x['rooms'] == _r]
        _ar = [float(x['area']) for x in _xs]
        _pr = [float(x['price']) for x in _xs]
        MS_ROWS.append([ROOMS_RU.get(_r, f'{_r}-комн.'), str(len(_xs)),
                        f'{min(_ar):.0f} – {max(_ar):.0f}',
                        f'{min(_pr) / 1e6:.1f} – {max(_pr) / 1e6:.1f}'.replace('.', ','),
                        nf(round(med(float(x['price_per_meter']) for x in _xs)))])
    _ppm = [float(x['price_per_meter']) for x in MASTERS]
    _orig = [float(x['original_price'] or x['price']) / float(x['area']) for x in MASTERS]
    _disc = [1 - float(x['price']) / float(x['original_price'] or x['price']) for x in MASTERS]
    _floors = {x['section']['number']: int(x['section']['floors_count']) for x in MASTERS}
    MS = {
        'n': len(MASTERS),
        'med': nf(round(med(_ppm))), 'medNum': med(_ppm),
        'orig': nf(round(med(_orig), -2)), 'origNum': med(_orig),
        'lo': nf(round(min(_ppm), -3)), 'hi': nf(round(max(_ppm), -3)),
        'priceLo': mln(min(float(x['price']) for x in MASTERS)),
        'priceHi': mln(max(float(x['price']) for x in MASTERS)),
        'discLo': f'{min(_disc) * 100:.0f}', 'discHi': f'{max(_disc) * 100:.0f}',
        'floorsLo': min(_floors.values()), 'floorsHi': max(_floors.values()),
        'dist': f'{dist(SITE, MS_SITE) / 1000:.1f}'.replace('.', ','),
        'cska': f'{dist(CSKA, MS_SITE) / 1000:.1f}'.replace('.', ','),
    }

if MS:
    PRICE_SRC.append(['Прайс МАСТЕРС, Capital Group',
                      f"{MS['n']} квартир, {MS['priceLo']} – {MS['priceHi']} млн ₽, "
                      f"медиана {MS['med']} ₽/м² со скидкой", '22.09.2026'])

# ── хронология ──────────────────────────────────────────────────────────────
TIMELINE = [
    ['1930-е', 'На площадке начинает работать авиационный завод, позднее здесь производственная площадка «МиГ»'],
    ['13 июля 2023', 'Зарегистрировано ООО СЗ «Энимерози», уставный капитал 10 000 ₽'],
    ['Август 2023', 'ООО «Энимерози» выигрывает торги по КРТ, стартовая цена лота 987,9 млн ₽'],
    ['2024 – 2025', 'Проект планировки, вывод производства, снос заводских корпусов'],
    ['2026', 'Опубликован мастер-план бюро «Камень» с центральным парком, прудом и бульваром'],
    ['Июль 2026', 'Опубликована проектная декларация первого дома: 311 квартир, сдача во II кв. 2028'],
    ['III – IV кв. 2026', 'Заявленный старт продаж первых корпусов. На 22 сентября продажи не открыты'],
    ['2049', 'Окончание срока договора КРТ'],
]

# ── чем площадка отличается от соседей ──────────────────────────────────────
FEATURES = [
    ['63,55 га одним участком внутри ТТК',
     'Восьмая часть площади Бегового района. Договор комплексного развития '
     'заключён на 26 лет, до 2049 года.'],
    ['Башни до 50 этажей',
     'По мастер-плану бюро «Камень» высотные корпуса стоят в середине '
     'территории, вокруг центрального парка с прудом. Вдоль Ленинградского '
     'проспекта заявлена средняя этажность.'],
    ['Около 19 000 квартир',
     f'Средняя квартира около 74 м². Сейчас в радиусе 2,5 км от участка '
     f'продаётся {COH["total"]} квартир и апартаментов, то есть проект выведет '
     f'на рынок примерно в {plural(round(FLATS / COH["total"]), "раз", "раза", "раз")} больше.'],
    [f'Три станции метро в {round(dist(SITE, DINAMO), -1):.0f}–{round(dist(SITE, CSKA), -1):.0f} м',
     f'«Динамо» на Замоскворецкой линии в {round(dist(SITE, DINAMO), -1):.0f} м от центра участка, '
     f'«Петровский парк» и «ЦСКА» на Большой кольцевой в {round(dist(SITE, PPARK), -1):.0f} '
     f'и {round(dist(SITE, CSKA), -1):.0f} м. От края территории до станций ближе.'],
    ['Социальные объекты строит инвестор',
     'Школы, детские сады, поликлиника, ФОК с бассейном и отделение МВД '
     'передаются городу. Ещё 30 000 м² квартир уходят под реновацию.'],
    ['Продажи не открыты',
     'На 22 сентября 2026 года прайса нет, декларация опубликована только '
     'по первому дому. Брокеры записывают покупателей в лист ожидания.'],
]

# ── район Беговой ───────────────────────────────────────────────────────────
DISTRICT = [
    ['Площадь района', '5,56 км²'],
    ['Население, 2025', '43 662 человека'],
    ['Плотность', '7 853 чел. на км²'],
    ['Место по плотности', '95-е из районов Москвы'],
    ['Округ', 'Северный, САО'],
    ['Станции метро', 'на границах: Динамо, Петровский парк, Беговая, Белорусская'],
    ['Как район образован', '5 июля 1995 года'],
    ['Главная магистраль', 'Ленинградский проспект'],
]

DIST_PRO = [
    ['7 853 человека на км²',
     'На Арбате 17 508, в Мещанском районе 12 102. По плотности Беговой '
     'на 95-м месте в Москве.'],
    ['Два парка и два спорткомплекса в пределах 1,5 км',
     'Петровский парк (22 га) в 0,97 км, Ходынское поле (25 га) в 1,44 км, '
     'ВТБ Арена в 1,21 км, спорткомплекс ЦСКА у одноимённой станции.'],
    ['Две линии метро и два МЦД',
     '«Динамо» на Замоскворецкой линии, «Петровский парк» и «ЦСКА» на '
     'Большой кольцевой. Белорусский вокзал с МЦД-1 и МЦД-4 в 2,2 км.'],
    ['Боткинская больница в 360 м',
     'На соседнем участке ММНКЦ имени Боткина и МНИОИ имени Герцена.'],
    [f'{COH["new"]} лотов в строящихся домах рядом',
     f'{plural(COH["newGroups"], "проект", "проекта", "проектов")} в радиусе 2,5 км от участка. '
     f'В готовых домах рядом продаётся ещё {COH["resale"]} лотов.'],
]

DIST_CONTRA = [
    ['Ленинградский проспект',
     'Территория выходит на него длинной стороной, первая линия корпусов '
     'получит шум и трафик. Жители соседних домов спорят о проезде через '
     'бывшую заводскую территорию.'],
    ['Промышленное прошлое',
     'До 2020-х здесь работал авиазавод. Данных о рекультивации почвы '
     'в открытых источниках нет.'],
    ['26 лет стройки',
     'Первые корпуса будут заселяться рядом с действующей стройплощадкой.'],
    ['Рост предложения',
     f'19 000 квартир примерно в {plural(round(FLATS / COH["total"]), "раз", "раза", "раз")} больше '
     f'сегодняшнего предложения в радиусе 2,5 км. По мере ввода очередей '
     f'это будет сдерживать цены на соседнюю вторичку.'],
    ['Школы и сады строятся вместе с очередями',
     'Первые жильцы какое-то время будут пользоваться существующими школами '
     'и поликлиниками района.'],
]

# ── застройщик ──────────────────────────────────────────────────────────────
BUILDER = [
    ['Группа', 'Capital Group, на рынке с 1993 года'],
    ['Портфель', 'более 11 млн м² построенного и строящегося'],
    ['Известные проекты', '«Город Столиц», OKO, Capital Towers, «Бадаевский»'],
    ['Статус', 'системообразующий застройщик'],
    ['Юрлицо проекта', 'ООО СЗ «Энимерози»'],
    ['ИНН', '9703150422'],
    ['Регистрация юрлица', '13 июля 2023 года'],
    ['Уставный капитал', '10 000 ₽'],
    ['Как получен участок', 'торги по КРТ, август 2023'],
    ['Мастер-план', 'бюро «Камень»'],
]

# ── где источники расходятся ────────────────────────────────────────────────
SOURCE_ROWS = [
    ['Площадь территории', '63,55 га — договор КРТ', '«более 64 га» — карточки'],
    ['Объём застройки', '2,3 млн м² — Стройкомплекс', '«свыше 2,5 млн м²» — карточки'],
    ['Жильё в объёме', '1,4 млн м² — «Мос. перспектива»', '1 млн м² — карточки'],
    ['Квартир', 'в документах КРТ не названо', '≈ 19 000 — карточки'],
    ['Этажность', 'в документах КРТ не названа', 'до 50 этажей — карточки'],
    ['Адрес проекта', 'Ленинградский проспект, 33', '1-й Боткинский проезд, вл. 7'],
    ['Цены', 'не опубликованы', '«от 500 000 ₽/м²», лист ожидания'],
    ['Первый дом', 'декларация: 311 квартир, II кв. 2028', 'в карточках не упоминается'],
]

# ── открытые вопросы ────────────────────────────────────────────────────────
RISKS = [
    ['Объём застройки',
     'Стройкомплекс и «Московская перспектива» называют 2,3 млн м², брокеры '
     'пишут «более 2,5 млн м²». Жилья 1,4 или 1 млн м² в зависимости '
     'от источника, разница 40 %.'],
    ['Число квартир и этажность',
     '19 000 квартир и 50 этажей есть только в брокерских карточках. '
     'В официальных сообщениях о КРТ этих цифр нет.'],
    ['Цены',
     'Прайса нет ни у проекта, ни у брокеров. Все цены в справке относятся '
     'к соседним домам.'],
    ['Очереди и сроки',
     'Известен только срок договора КРТ, 26 лет. Деление на очереди и срок '
     'сдачи первой не опубликованы.'],
    ['Социальные объекты',
     'Число мест в школах и садах и их привязка к очередям не опубликованы.'],
    ['Проезд через территорию',
     'Проезд от Ленинградского проспекта к улице Сухого упоминается '
     'в публикациях как планируемый. Официальной схемы нет.'],
]

# ── сколько рынок просит за ремонт ──────────────────────────────────────────
# repairType добран из карточек Циан: в поисковой выдаче поля нет. Сравнение
# идёт внутри одного дома — локация, год и класс у обеих групп совпадают,
# различается только заявленное состояние квартиры.
REPAIR_RU = {'design': 'дизайнерский', 'euro': 'евроремонт',
             'cosmetic': 'косметический', 'no': 'без ремонта'}
_rep_path = os.path.join(HERE, 'mg_repair_lots.json')
REPAIR_LOTS = json.load(open(_rep_path, encoding='utf-8')) if os.path.exists(_rep_path) else []

_by_house = {}
for _l in REPAIR_LOTS:
    if _l.get('repair'):
        _by_house.setdefault(SHORT.get(_l['complex'], _l['complex']), {}) \
                 .setdefault(_l['repair'], []).append(_l['ppm'])

FIN_ROWS, _fpairs = [], []
for _name, _v in _by_house.items():
    _a = _v.get('design')
    _b = [x for r, xs in _v.items() if r != 'design' for x in xs]
    if not _a or not _b:
        continue
    _ma, _mb = med(_a), med(_b)
    _fpairs.append(((_ma / _mb - 1) * 100))
    FIN_ROWS.append([_name, str(len(_a)), nf(round(_ma)), str(len(_b)), nf(round(_mb)),
                     f'{(_ma / _mb - 1) * 100:+.0f} %'.replace('+0 %', '0 %').replace('-', '\u2212')])
FIN_ROWS.sort(key=lambda r: -float(r[5].replace(' %', '').replace('+', '').replace('\u2212', '-')))

_kinds = {}
for _l in REPAIR_LOTS:
    _kinds[_l.get('repair')] = _kinds.get(_l.get('repair'), 0) + 1
KIND_ROWS = [[REPAIR_RU.get(k, 'не указан'), str(v),
              nf(round(med([l['ppm'] for l in REPAIR_LOTS if l.get('repair') == k])))]
             for k, v in sorted(_kinds.items(), key=lambda kv: -kv[1])]

FIN = {}
if _fpairs:
    _srt = sorted(_fpairs)
    FIN = {
        'houses': len(_fpairs), 'read': len(REPAIR_LOTS),
        'design': _kinds.get('design', 0),
        'other': sum(v for k, v in _kinds.items() if k and k != 'design'),
        'designShare': round(_kinds.get('design', 0) /
                             sum(v for k, v in _kinds.items() if k) * 100),
        'med': f'{med(_srt):+.0f}'.replace('+0', '0').replace('-', '\u2212'),
        'lo': f'{_srt[0]:+.0f}'.replace('-', '\u2212'), 'hi': f'{_srt[-1]:+.0f}',
        'designMed': nf(round(med([l['ppm'] for l in REPAIR_LOTS if l.get('repair') == 'design']))),
    }

# Квартиры с дизайнерским ремонтом в выборке крупнее остальных, а крупный лот
# в этой локации стоит дороже за метр сам по себе. Разрез по площадям проверяет,
# не сводится ли надбавка к метражу. Срез сквозной по всем домам, поэтому он
# слабее подомного сравнения выше и стоит рядом с ним, а не вместо него.
_bands = [('до 60 м²', 0, 60), ('60 – 100 м²', 60, 100), ('от 100 м²', 100, 1e9)]
BAND_ROWS = []
for _lab, _lo, _hi in _bands:
    _a = [l['ppm'] for l in REPAIR_LOTS
          if l.get('repair') == 'design' and _lo <= l['area'] < _hi]
    _b = [l['ppm'] for l in REPAIR_LOTS
          if l.get('repair') and l['repair'] != 'design' and _lo <= l['area'] < _hi]
    if not _a or not _b:
        continue
    _ma, _mb = med(_a), med(_b)
    BAND_ROWS.append([_lab, str(len(_a)), nf(round(_ma)), str(len(_b)), nf(round(_mb)),
                      f'{(_ma / _mb - 1) * 100:+.0f} %'.replace('+0 %', '0 %').replace('-', '−')])

if REPAIR_LOTS:
    _ad = [l['area'] for l in REPAIR_LOTS if l.get('repair') == 'design']
    _ao = [l['area'] for l in REPAIR_LOTS if l.get('repair') and l['repair'] != 'design']
    FIN['areaDesign'] = f'{med(_ad):.0f}'
    FIN['areaOther'] = f'{med(_ao):.0f}'

_cards_path = os.path.join(HERE, 'mg_repair_cards.json')
REPAIR_CARDS = json.load(open(_cards_path, encoding='utf-8')) if os.path.exists(_cards_path) else []

# ── первый дом по декларации ────────────────────────────────────────────────
FIRST_ROWS = [
    ['Квартир', f"{FIRST_HOUSE['flats']}"],
    ['Площадь', f"{nf(FIRST_HOUSE['sqm'])} м²"],
    ['Высота', f"около {FIRST_HOUSE['height']} м"],
    ['Сдача', FIRST_HOUSE['deadline']],
    ['Инвестиции', f"{one(FIRST_HOUSE['invest'] / 1e9)} млрд ₽"],
    ['Инвестиции на м²', f"≈ {nf(round(FIRST_HOUSE['invest'] / FIRST_HOUSE['sqm'], -3))} ₽"],
    ['Застройщик', 'СЗ «Энимерози»'],
]

# ── готовые дома по возрасту ────────────────────────────────────────────────
AGE_ROWS = []
for _lab, _lo, _hi in (('до 1970 года', 0, 1970), ('1970 – 2009', 1970, 2010),
                       ('2010 и новее', 2010, 3000)):
    _gs = [r for r in _res_rows if r['year'] and _lo <= r['year'] < _hi]
    _ls = [l for r in _gs for l in _g[(r['name'], r['what'])]]
    if _ls:
        AGE_ROWS.append([_lab, str(len(_gs)), str(len(_ls)),
                         f"{med([l['totalArea'] for l in _ls]):.0f}", nf(round(med([l['ppm'] for l in _ls])))])

# ── бюджет по типам квартир: новостройки рядом и МАСТЕРС ────────────────────
def _rooms(l):
    return 0 if l.get('flatType') == 'studio' else l.get('rooms')


ROOM_ROWS = []
for _r, _lab in ((0, 'Студии'), (1, 'Однокомнатные'), (2, 'Двухкомнатные'), (3, 'Трёхкомнатные')):
    _ls = [l for l in _new_lots if _rooms(l) == _r]
    _ms = [x for x in MASTERS if x['rooms'] == _r]
    if not _ls:
        continue
    ROOM_ROWS.append([_lab, str(len(_ls)), f"{med([l['totalArea'] for l in _ls]):.0f}",
                      mln(med([l['priceRub'] for l in _ls])),
                      mln(med([float(x['price']) for x in _ms])) if _ms else '—',
                      f"{med([float(x['area']) for x in _ms]):.0f}" if _ms else '—'])

# ── ценовые ориентиры для графика на странице о ценах ───────────────────────
_near_new_v = med([l['ppm'] for l in _new_lots if l['dist'] <= 1200])
BENCH = [
    ['Брокерский ориентир «МИГа», нижняя граница', BROKER_FROM, 'mig'],
    ['Готовые дома рядом, медиана', med([l['ppm'] for l in _res_lots]), 'res'],
    ['МАСТЕРС, прайс со скидкой, медиана', MS.get('medNum', 0), 'cg'],
    ['Новостройки в 2,5 км, медиана', med([l['ppm'] for l in _new_lots]), 'new'],
    ['Стройки в 1,2 км от участка, медиана', _near_new_v, 'new'],
    ['МАСТЕРС, прайс без скидки, медиана', MS.get('origNum', 0), 'cg'],
    ['Премиум Москвы в среднем, NF Group', MKT_PREM, 'mkt'],
]

# ── числа для текста ────────────────────────────────────────────────────────
# Всё, что в тексте справки зависит от выгрузки, считается здесь, чтобы
# формулировки не расходились с таблицами после пересборки.
import re as _re


def q(name):
    """Кавычки-ёлочки для названий ЖК; адреса и латиница — без кавычек."""
    return name if _re.search(r'[0-9A-Za-z]', name) else f'«{name}»'


def pct(a, b):
    return round((a / b - 1) * 100)


def two(v):
    return f'{v:.2f}'.replace('.', ',')


_ppm_all = [l['ppm'] for l in _coh]
_ppm_new = med([l['ppm'] for l in _new_lots])
_ppm_res = med([l['ppm'] for l in _res_lots])
_dec_known = [l for l in _new_lots if l.get('decoration')]
_big_new = sorted(_new_rows, key=lambda r: -r['n'])[:3]
_by_n = [r for r in _rows if r['n'] >= 10]
_spread = sorted(_by_n, key=lambda r: r['ppmHi'] / r['ppmLo'])
_aparts = [r for r in _rows if r['apart'] >= r['n'] / 2]


def _rank(xs):
    s = sorted(range(len(xs)), key=lambda i: xs[i])
    r = [0] * len(xs)
    for k, i in enumerate(s):
        r[i] = k
    return r


def _spearman(a, b):
    ra, rb = _rank(a), _rank(b)
    n = len(a)
    return 1 - 6 * sum((x - y) ** 2 for x, y in zip(ra, rb)) / (n * (n * n - 1))


_rho = _spearman([r['dist'] for r in _rows], [r['ppmMed'] for r in _rows])
_n0710 = sum(1 for v in _ppm_all if 700_000 <= v < 1_000_000)
_ms_med = MS.get('medNum', 0)
# ближайшие к участку стройки (до 1,2 км) — самый прямой ориентир по месту
_near_new = med([l['ppm'] for l in _new_lots if l['dist'] <= 1200])
_cards_med = med([c['ppmNum'] for c in REPAIR_CARDS]) if REPAIR_CARDS else 0
_studios = [float(x['price']) for x in MASTERS if x['rooms'] == 0]
_ms_row = next((r for r in _rows if 'МАСТЕРС' in r['name'].upper()), None)


def _nearest(pool, budget):
    l = min(pool, key=lambda x: abs(x['priceRub'] - budget))
    nm = SHORT.get(l.get('complex') or '', l.get('complex')) or \
        ' '.join(x for x in (l.get('street'), l.get('house')) if x)
    return l['totalArea'], nm


_eq = [(b, _nearest(_build, b), _nearest(_ready, b)) for b in BUDGETS]


def _cnt(cond):
    from collections import Counter
    return Counter(SHORT.get(l.get('complex') or '', l.get('complex'))
                   or ' '.join(x for x in (l.get('street'), l.get('house')) if x)
                   for l in _coh if cond(l['ppm']))

STAT = {
    'noFinishShare': round(sum(1 for l in _dec_known if l['decoration'] == 'without')
                           / len(_dec_known) * 100) if _dec_known else 0,
    'brokerGap': round((1 - BROKER_FROM / _ppm_new) * 100),
    'estLo': two(_ms_med / 1e6), 'estHi': two(MS.get('origNum', 0) / 1e6),
    'nearNew': nf(round(_near_new)),
    'landShare': two(LAND_PER_SQM / _ppm_new * 100),
    'finishPct': round(FINISH / _ppm_new * 100),
    'fullPpm': two((_ppm_new + FINISH) / 1e6),
    'buy74': mln(74 * _ppm_new), 'ms74': mln(74 * _ms_med),
    'buy35': mln(35 * _ppm_new), 'fin35': mln(35 * FINISH),
    'msStudioLo': mln(min(_studios)) if _studios else '—',
    'cardsVsNew': pct(_cards_med, _ppm_new) if _cards_med else 0,
    'gapNewRes': pct(_ppm_new, _ppm_res),
    'n0710': _n0710, 'p0710': round(_n0710 / len(_ppm_all) * 100),
    'below05': sum(1 for v in _ppm_all if v < 500_000),
    'above12': sum(1 for v in _ppm_all if v >= 1_200_000),
    'rho': two(_rho),
    # высота графика когорты в документе: ширина 643 px, пропорции PNG
    'chartH': round(643 * (150 + 40 * len(_rows)) / 1400),
}

# ── маркированные списки, собранные из данных ──────────────────────────────
STAT['cohortBullets'] = [
    [f"{COH['new']} лотов в {plural(COH['newGroups'], 'строящемся проекте', 'строящихся проектах', 'строящихся проектах')}. ",
     'Больше всего лотов: ' + ', '.join(f"{q(r['short'])} ({r['n']})" for r in _big_new) + '.'],
    [f"Медиана метра {COH['med']} ₽. ",
     f"В новостройках {COH['newMed']} ₽, в готовых домах {COH['resMed']} ₽. "
     f"Новый дом дороже готового на {STAT['gapNewRes']} %."],
    [f"У «ЦСКА» {plural(COH['cskaGroups'], 'дом', 'дома', 'домов')}, {plural(COH['cskaLots'], 'лот', 'лота', 'лотов')}. ",
     'Не дальше 1,5 км от станции и ближе к ней, чем к «Динамо».'],
]
if _aparts:
    STAT['cohortBullets'].append(
        [f"Апартаменты: {plural(COH['apart'], 'лот', 'лота', 'лотов')} из {COH['total']}. ",
         'В основном ' + ', '.join(q(r['short']) for r in _aparts) + '.'])

_nb = []
for r in _big_new:
    _nb.append([f"{q(r['short'])}: {r['n']} лотов, медиана {nf(round(r['ppmMed']))} ₽. ",
                f"Площади {r['areaLo']:.0f}–{r['areaHi']:.0f} м², сдача {r['deadline'] or 'не указана'}, "
                f"{r['dist']} м от участка."])
_top_new = max(_new_rows, key=lambda r: r['ppmMed'])
_low_new = min(_new_rows, key=lambda r: r['ppmMed'])
_nb.append([f"Самый дорогой метр: {q(_top_new['short'])}, {nf(round(_top_new['ppmMed']))} ₽. ",
            f"Самый дешёвый: {q(_low_new['short'])}, {nf(round(_low_new['ppmMed']))} ₽."])
if _ms_row:
    _nb.append([f"МАСТЕРС на Циан: {_ms_row['n']} лотов, медиана {nf(round(_ms_row['ppmMed']))} ₽. ",
                f"В прайсе застройщика со скидкой {MS['med']} ₽, без скидки {MS['orig']} ₽."])
STAT['newBullets'] = _nb

_tallest = max(NEW_DETAIL, key=lambda r: int(r[2]) if r[2].isdigit() else 0)
_dkey = lambda s: (int(s.split()[-1]), ['I', 'II', 'III', 'IV'].index(s.split()[0]))
_first_dl = min((r for r in _new_rows if r['deadline']), key=lambda r: _dkey(r['deadline']))
_last_dl = max((r for r in _new_rows if r['deadline']), key=lambda r: _dkey(r['deadline']))
_floors = [int(r[2]) for r in NEW_DETAIL if r[2].isdigit() and int(r[2]) > 0]
_areas = [int(r[6]) for r in NEW_DETAIL]
_dls = sorted({r['deadline'] for r in _new_rows if r['deadline']},
              key=lambda s: (int(s.split()[-1]), ['I', 'II', 'III', 'IV'].index(s.split()[0])))
STAT['detailBullets'] = [
    [f"Этажность от {min(_floors)} до {max(_floors)} этажей. ",
     f"Выше всех {q(_tallest[1])} ({_tallest[2]} этажей). «МИГ» заявлен до 50 этажей."],
    [f"Сроки сдачи от {_dls[0]} до {_dls[-1]}. ",
     f"Раньше всех сдаётся {q(_first_dl['short'])}, позже всех {q(_last_dl['short'])}."],
    [f"Медианная площадь лота {min(_areas)}–{max(_areas)} м². ",
     'Средняя квартира «МИГа» (74 м²) в пределах этого диапазона.'],
]

_top_res = max(_res_rows, key=lambda r: r['ppmMed'])
_low_res = sorted(_res_rows, key=lambda r: r['ppmMed'])[:3]
STAT['resBullets'] = [
    [f"Самый дорогой готовый дом: {q(_top_res['short'])}, {nf(round(_top_res['ppmMed']))} ₽ за м². ",
     f"{plural(_top_res['n'], 'лот', 'лота', 'лотов')}, {_top_res['dist']} м от участка."],
    [f"Самые дешёвые: {', '.join(q(r['short']) for r in _low_res)}. ",
     f"Медианы {nf(round(_low_res[0]['ppmMed'], -3))}–{nf(round(_low_res[-1]['ppmMed'], -3))} ₽ за м², "
     f"дома {min(r['year'] for r in _low_res if r['year'])}–{max(r['year'] for r in _low_res if r['year'])} годов."],
]
_pp = {r['what']: r for r in _rows if r['name'] == 'Прайм Парк (Prime Park)'}
if len(_pp) == 2:
    STAT['resBullets'].append(
        [f"Готовые корпуса «Прайм Парка» дороже строящихся на {pct(_pp['готов']['ppmMed'], _pp['строится']['ppmMed'])} %. ",
         f"{nf(round(_pp['готов']['ppmMed']))} ₽ против {nf(round(_pp['строится']['ppmMed']))} ₽ за м² в одном проекте."])

_cs = _cska_rows
_cs_top = max(_cs, key=lambda r: r['ppmMed']) if _cs else None
_cs_big = max(_cs, key=lambda r: r['n']) if _cs else None
STAT['cskaBullets'] = []
if _cs:
    STAT['cskaBullets'] = [
        [f"{plural(len(_cs), 'дом', 'дома', 'домов')}, {plural(sum(r['n'] for r in _cs), 'лот', 'лота', 'лотов')}. ",
         f"Медиана по ним {nf(round(med([l['ppm'] for r in _cs for l in _g[(r['name'], r['what'])]])))} ₽ за м²."],
        [f"Больше всего лотов: {q(_cs_big['short'])} ({_cs_big['what']}), {_cs_big['n']}. ",
         f"Медиана {nf(round(_cs_big['ppmMed']))} ₽, {_cs_big['cska']} м до «ЦСКА»."],
        [f"Самый дорогой метр: {q(_cs_top['short'])} ({_cs_top['what']}), {nf(round(_cs_top['ppmMed']))} ₽. ",
         f"{_cs_top['cska']} м до «ЦСКА»."],
    ]

STAT['chartBullets'] = [
    [f"Самый широкий разброс: {q(_spread[-1]['short'])} ({_spread[-1]['what']}). ",
     f"От {two(_spread[-1]['ppmLo'] / 1e6)} до {two(_spread[-1]['ppmHi'] / 1e6)} млн ₽ за м², {plural(_spread[-1]['n'], 'лот', 'лота', 'лотов')}."],
    [f"Самый узкий: {q(_spread[0]['short'])}. ",
     f"{plural(_spread[0]['n'], 'лот', 'лота', 'лотов')} от {two(_spread[0]['ppmLo'] / 1e6)} до {two(_spread[0]['ppmHi'] / 1e6)} млн ₽ за м²."],
    [f"Выше медианы новостроек ({COH['newMed']} ₽) {plural(sum(1 for r in _rows if r['ppmMed'] > _ppm_new), 'группа', 'группы', 'групп')} из {len(_rows)}. ",
     ', '.join(q(r['short']) + (' (готов)' if r['what'] == 'готов' else '') for r in _rows if r['ppmMed'] > _ppm_new) + '.'],
    [f"Расстояние до участка почти не связано с ценой. ",
     f"Ранговая корреляция между расстоянием и медианой дома {STAT['rho']}. Цена сильнее зависит от возраста и класса дома."],
]

STAT['histBullets'] = [
    [f"{STAT['n0710']} лотов из {COH['total']} ({STAT['p0710']} %) стоят от 0,7 до 1,0 млн ₽ за м². ",
     'Выше и ниже этого диапазона предложений заметно меньше.'],
    [f"Дешевле 0,5 млн ₽ за м²: {plural(STAT['below05'], 'лот', 'лота', 'лотов')}. ",
     'Больше всего: ' + ', '.join(f'{q(k)} ({v})' for k, v in _cnt(lambda v: v < 500_000).most_common(2)) + '. Остальное в основном дома 1920–1960-х.'],
    [f"От 1,2 млн ₽ за м²: {plural(STAT['above12'], 'лот', 'лота', 'лотов')}. ",
     'Почти все: ' + ', '.join(f'{q(k)} ({v})' for k, v in _cnt(lambda v: v >= 1_200_000).most_common(3)) + '. В основном верхние этажи башен.'],
    [f"Премиум по Москве в среднем {nf(MKT_PREM)} ₽ за м² (NF Group). ",
     f"Это в {one(MKT_PREM / _ppm_new)} раза больше медианы новостроек рядом с участком."],
]

STAT['equalBullets'] = []
for _b, (_an, _nn), (_ar, _nr) in _eq:
    STAT['equalBullets'].append(
        [f"{_b / 1e6:.0f} млн ₽: {_an:.0f} м² в стройке или {_ar:.0f} м² в готовом доме. ",
         f"{q(_nn)} и {q(_nr)}."])

_closest_new = sorted(_new_rows, key=lambda r: r['dist'])[:2]
STAT['conclusions'] = [
    ['Цен у «МИГа» нет. ',
     f"Брокеры называли ориентир от 500 000 ₽ за м². Соседний МАСТЕРС того же застройщика продаётся по медиане {MS['med']} ₽ со скидкой."],
    [f"Оценка для первых корпусов {STAT['estLo']}–{STAT['estHi']} млн ₽ за м². ",
     f"От цены МАСТЕРС со скидкой до его прайса без скидки. Ближайшие к участку стройки идут по {STAT['nearNew']} ₽."],
    ['Первый задекларированный дом, скорее всего, для реновации. ',
     '311 квартир, сдача во II кв. 2028. Рыночные корпуса пока без деклараций.'],
    [f"{COH['total']} лотов в продаже в радиусе 2,5 км. ",
     f"«МИГ» добавит около 19 000 квартир, примерно в {plural(round(FLATS / COH['total']), 'раз', 'раза', 'раз')} больше."],
    [f"63,55 га внутри ТТК у трёх станций метро. ",
     f"От центра участка до «Динамо» {round(dist(SITE, DINAMO), -1):.0f} м, до «Петровского парка» "
     f"{round(dist(SITE, PPARK), -1):.0f} м, до «ЦСКА» {round(dist(SITE, CSKA), -1):.0f} м."],
    ['Стройка на 26 лет. ',
     'Парк и бульвар появятся с центральными очередями, первые жильцы будут жить рядом со стройкой.'],
    [f"Ближайшие стройки: " + ' и '.join(q(r['short']) for r in _closest_new) + '. ',
     'Медианы ' + ' и '.join(nf(round(r['ppmMed'])) for r in _closest_new) + ' ₽ за м². '
     'Больше всего лотов: ' + ', '.join(q(r['short']) for r in sorted(_new_rows, key=lambda r: -r['n'])[:3]) + '.'],
]

if __name__ == '__main__':
    K = {
        'card': CARD,
        'composition': COMPOSITION,
        'newDetail': NEW_DETAIL,
        'equalRows': EQUAL_ROWS,
        'sourceRows': SOURCE_ROWS,
        'economy': ECONOMY,
        'nearby': NEARBY,
        'cskaRows': CSKA_ROWS,
        'newRows': NEW_ROWS,
        'resRows': RES_ROWS,
        'budget': BUDGET,
        'market': MARKET,
        'timeline': TIMELINE,
        'features': FEATURES,
        'district': DISTRICT,
        'distPro': DIST_PRO,
        'distContra': DIST_CONTRA,
        'builder': BUILDER,
        'risks': RISKS,
        'cards': REPAIR_CARDS,
        'finRows': FIN_ROWS,
        'bandRows': BAND_ROWS,
        'kindRows': KIND_ROWS,
        'fin': FIN,
        'cardsMed': nf(round(med([c['ppmNum'] for c in REPAIR_CARDS]))) if REPAIR_CARDS else '—',
        'coh': COH,
        'firstRows': FIRST_ROWS,
        'ageRows': AGE_ROWS,
        'roomRows': ROOM_ROWS,
        'stat': STAT,
        'priceSrc': PRICE_SRC,
        'msRows': MS_ROWS,
        'ms': {k: v for k, v in MS.items() if not k.endswith('Num')},
        'nums': {
            'ha': f'{AREA_HA}'.replace('.', ','),
            'total': f'{TOTAL_SQM / 1e6:.1f}'.replace('.', ','),
            'housing': f'{HOUSING_SQM / 1e6:.1f}'.replace('.', ','),
            'flats': nf(FLATS), 'flatAvg': f'{FLAT_AVG:.0f}',
            'floors': str(FLOORS_MAX),
            'years': str(KRT_YEARS),
            'landscape': f'{LANDSCAPE_HA}'.replace('.', ','),
            'lotPrice': mln(LOT_PRICE),
            'invest': nf(INVEST / 1e9),
            'landPer': nf(round(LAND_PER_SQM)),
            'investPer': nf(round(INVEST_PER_SQM, -3)),
            'ppmLocal': nf(round(PPM_LOCAL)),
            'finish': nf(FINISH),
            'budget74': mln(74 * (PPM_LOCAL + FINISH)),
            'metro': f'{round(dist(SITE, DINAMO), -1):.0f}',
            'metroBkl': f'{round(dist(SITE, PPARK), -1):.0f}',
            'metroCska': f'{round(dist(SITE, CSKA), -1):.0f}',
            'renovation': nf(RENOVATION_SQM),
            # Белая площадь (Лесная, 5) — OpenStreetMap; Москва-Сити — башня «Федерация»
            'belaya': one(dist(SITE, (55.778012, 37.587320)) / 1000),
            'city': one(dist(SITE, (55.749500, 37.537400)) / 1000),
        },
    }
    json.dump(K, open(os.path.join(HERE, 'mg_tables.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    json.dump(BENCH, open(os.path.join(HERE, 'mg_bench.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    json.dump(COH_PINS, open(os.path.join(HERE, 'mg_pins.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    print('mg_tables.json готов')
    for k, v in K['nums'].items():
        print(f'  {k:12s} {v}')
    print(f"\nкогорта: {COH['total']} лотов, {COH['groups']} групп, медиана {COH['med']} ₽")
    for r in COH_PINS:
        print(f"   {r['num']:3d} {r['short'][:34]:34s} {r['what']:9s} {r['dist']:5d} м "
              f"{r['n']:4d} лотов  {round(r['ppmMed']):>9} ₽  ЦСКА {r['cska']:5d} м")
    print('\nвыгрузки (заявлено, перечислено):', FEED_META)
