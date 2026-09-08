#!/usr/bin/env python3
"""Собрать docs/premium-cao/premium-zhk-cao.xlsx из трёх источников:

  1. complexes.json  — сводка по ЖК из живой выдачи Циан (aggregate-complexes.js)
  2. pdf-table.json  — таблица заказчика (скан), строящиеся проекты
  3. planning.json   — проекты в стадии проектирования / ЗУ (веб-исследование)

Три листа: «1. Построено (вторичка)», «2. Строится», «3. Проектирование».
"""
import json, re, sys, math
from pathlib import Path
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.utils import get_column_letter
from openpyxl.formatting.rule import ColorScaleRule

ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / 'docs' / 'premium-cao'
complexes = json.load(open(DOCS / 'complexes.json'))
pdf = json.load(open(DOCS / 'pdf-table.json'))
planning = json.load(open(DOCS / 'planning.json'))
manual = json.load(open(DOCS / 'manual.json'))   # застройщик, класс, зона — ручная разметка
devs = json.load(open(DOCS / 'developers.json'))  # веб-исследование по застройщикам, с источниками
ZHK = json.load(open(DOCS / 'zhk-info.json')) if (DOCS / 'zhk-info.json').exists() else {}   # карточки ЖК Циан
DESC = {}
for f in ('desc1.json', 'desc2.json'):
    if (DOCS / f).exists(): DESC.update(json.load(open(DOCS / f)))
def describe(*names):
    for n in names:
        if n and DESC.get(n): return DESC[n]
    return ''

def card(name):
    c = ZHK.get(name) or {}
    return c if c.get('id') else {}

def card_year_num(c):
    ys = []
    for k in ('finished', 'yearText', 'deadline'):
        ys += [int(y) for y in re.findall(r'(20\d\d)', c.get(k) or '')]
    return max(ys) if ys else None

def card_stage(c):
    t = ' '.join(str(c.get(k) or '') for k in ('finished', 'yearText', 'deadline', 'stage'))
    if re.search(r'Сдача|Строится', t): return 'building'
    if re.search(r'Сдан', t) or card_year_num(c): return 'built'
    return None

def card_year(c):
    """Год из карточки ЖК: «Сдан в 2017» / «Срок сдачи 4 кв. 2026»."""
    for k in ('finished', 'yearText', 'deadline'):
        v = c.get(k)
        if not v: continue
        m = re.search(r'(\d)\s*кв\.?\s*(\d{4})', v)
        if m: return f'{m.group(1)} кв. {m.group(2)}'
        m = re.search(r'(20\d\d)\s*[–-]\s*(20\d\d)', v)
        if m: return f'{m.group(1)}–{m.group(2)}'
        m = re.search(r'(20\d\d)', v)
        if m: return m.group(1)
    return None
CLASS_ORDER = {'делюкс': 0, 'премиум': 1, 'бизнес': 2}

PREMIUM_PER_M2 = 700_000   # порог ₽/м² по медиане, ниже — бизнес-класс, в подборку не идёт

# Жёлтый контур с карты заказчика: ТТК у Сити — Звенигородское ш. — Пресненский Вал —
# Грузинский Вал (Белорусская) — Васильевская/Красина — Садовое до Нового Арбата — набережные до Сити.
PRESNYA = [
    (55.7620, 37.5450), (55.7645, 37.5610), (55.7700, 37.5700), (55.7745, 37.5790), (55.7772, 37.5850),
    (55.7705, 37.5940), (55.7607, 37.5806), (55.7520, 37.5830), (55.7500, 37.5750), (55.7545, 37.5700),
    (55.7470, 37.5390), (55.7500, 37.5330), (55.7560, 37.5320),
]
BELORUSSKAYA = (55.7770, 37.5820)   # центр, радиус 900 м
CITY = (55.742, 55.762, 37.525, 37.552)   # lat_min, lat_max, lng_min, lng_max

def in_poly(lat, lng, poly):
    inside = False
    for i in range(len(poly)):
        y1, x1 = poly[i]; y2, x2 = poly[i - 1]
        if (y1 > lat) != (y2 > lat) and lng < (x2 - x1) * (lat - y1) / (y2 - y1) + x1:
            inside = not inside
    return inside

def near(lat, lng, c, r_m):
    dy = (lat - c[0]) * 111320; dx = (lng - c[1]) * 111320 * math.cos(math.radians(55.76))
    return math.hypot(dx, dy) <= r_m

def geo_zone(lat, lng):
    """Зона по координатам, без учёта района. None — вне интереса."""
    if lat is None or lng is None: return None
    if CITY[0] < lat < CITY[1] and CITY[2] < lng < CITY[3]: return 'Сити'
    if in_poly(lat, lng, PRESNYA): return 'Пресня'
    if near(lat, lng, BELORUSSKAYA, 900): return 'Белорусская'
    return None

def zone(row):
    """Садовое кольцо / Хамовники / Сити / Пресня / Белорусская; иначе None — строка не попадает в файл."""
    d = row.get('district') or ''
    if d == 'Хамовники':
        return 'Хамовники'
    gz = geo_zone(row.get('lat'), row.get('lng'))
    if gz: return gz
    if row.get('insideRing') is True:
        return 'Садовое кольцо'
    return None

ZONE_ORDER = {'Садовое кольцо': 0, 'Хамовники': 1, 'Сити': 2, 'Пресня': 3, 'Белорусская': 4}
ALLOWED = set(ZONE_ORDER)
def zkey(z):
    return (ZONE_ORDER.get(z, 9), str(z))

def fmt_years(r):
    ys = r.get('buildYears') or []
    if ys: return '–'.join(str(y) for y in sorted(set([min(ys), max(ys)])))
    ds = r.get('deadlineYears') or []
    return '–'.join(str(y) for y in sorted(set([min(ds), max(ds)]))) if ds else ''

def floors(r):
    a, b = r.get('floorsMin'), r.get('floorsMax')
    if a is None: return ''
    return str(a) if a == b else f'{a}-{b}'

def per_m2_str(v):
    return None if v in (None, 0) else int(v)

thin = Side(style='thin', color='C8C8C8')
MED_SIDE = Side(style='medium', color='1F3864')
border = Border(left=thin, right=thin, top=thin, bottom=thin)
HEAD_FILL = PatternFill('solid', fgColor='1F3864')
HEAD_FONT = Font(name='Calibri', size=10, bold=True, color='FFFFFF')
BODY_FONT = Font(name='Calibri', size=10)
LINK_FONT = Font(name='Calibri', size=10, color='0563C1', underline='single')
TITLE_FONT = Font(name='Calibri', size=14, bold=True, color='1F3864')
SUB_FONT = Font(name='Calibri', size=9, italic=True, color='666666')
ZEBRA = PatternFill('solid', fgColor='F3F6FA')
ZONE_FILL = {'Садовое кольцо': 'FFF2CC', 'Хамовники': 'E2EFDA', 'Сити': 'DDEBF7', 'Пресня': 'FCE4D6', 'Белорусская': 'EDEDED'}
wrap = Alignment(wrap_text=True, vertical='center')
center = Alignment(horizontal='center', vertical='center', wrap_text=True)
right = Alignment(horizontal='right', vertical='center')

def is_url(v): return isinstance(v, str) and v.startswith('http')

def short_name(n):
    """«Stella di Mosca Hotel & Residences (Стелла ди Моска …)» → без транслитерации в скобках."""
    n = (n or '').strip()
    m = re.match(r'^(.{6,}?)\s*\(([^)]*)\)\s*$', n)
    if m and re.search(r'[А-Яа-я]', m.group(2)) and re.search(r'[A-Za-z]', m.group(1)): return m.group(1).strip()
    return n

def short_addr(a):
    a = re.sub(r'^Москва,\s*', '', str(a or '')).strip()
    return re.sub(r'\s*\([^)]*\)', '', a).strip()

def strip_paren(v):
    return re.sub(r'\s*\([^)]*\)', '', str(v or '')).strip()

def short_stage(v):
    v = strip_paren(v)
    v = re.split(r'[;—]', v)[0].strip()
    return v[:48].rstrip(' ,') if len(v) > 48 else v

def sheet(wb, title, headers, rows, widths, note=None, subtitle='', zone_col=None, orientation='landscape', heat_col=None):
    ws = wb.create_sheet(title)
    ncol = len(headers)
    ws.append([title]); ws.merge_cells(start_row=1, start_column=1, end_row=1, end_column=ncol)
    ws['A1'].font = TITLE_FONT; ws['A1'].alignment = Alignment(vertical='center')
    ws.row_dimensions[1].height = 24
    ws.append([subtitle]); ws.merge_cells(start_row=2, start_column=1, end_row=2, end_column=ncol)
    ws['A2'].font = SUB_FONT; ws.row_dimensions[2].height = 14
    ws.append(headers)
    for c in ws[3]:
        c.fill = HEAD_FILL; c.font = HEAD_FONT; c.alignment = center
        c.border = Border(left=thin, right=thin, top=MED_SIDE, bottom=MED_SIDE)
    ws.row_dimensions[3].height = 34
    for i, r in enumerate(rows):
        ws.append(r)
        rr = ws.max_row
        zone = r[zone_col] if zone_col is not None else None
        fill = ZEBRA if i % 2 else None
        for c in ws[rr]:
            c.border = border; c.font = BODY_FONT
            if fill: c.fill = fill
            if zone_col is not None and c.column == zone_col + 1 and zone in ZONE_FILL:
                c.fill = PatternFill('solid', fgColor=ZONE_FILL[zone]); c.font = Font(name='Calibri', size=10, bold=True)
            if isinstance(c.value, bool): pass
            elif isinstance(c.value, (int, float)):
                c.number_format = '#,##0'; c.alignment = right
            elif is_url(c.value):
                c.hyperlink = c.value; c.value = 'ссылка'; c.font = LINK_FONT; c.alignment = center
            else:
                c.alignment = wrap if c.column in (1, 4, ncol) or (isinstance(c.value, str) and len(c.value) > 18) else center
        last = ws.cell(rr, ncol)
        if isinstance(last.value, str): last.font = Font(name='Calibri', size=9); last.alignment = Alignment(wrap_text=True, vertical='center')
        # высота строки — по самой «многострочной» ячейке: длина текста / ширина колонки
        lines = 1
        for j, v in enumerate(r):
            if not isinstance(v, str) or not v or is_url(v): continue   # ссылки печатаются словом «ссылка»
            cw = widths[j] if j < len(widths) else 10
            cpl = max(4, int(cw * (1.05 if j == ncol - 1 else 0.9)))   # кириллица 10 пт шире «0» шрифта по умолчанию; примечания 9 пт
            lines = max(lines, math.ceil(len(v) / cpl))
        ws.row_dimensions[rr].height = 15 if lines == 1 else 13.5 * lines + 3
    for i, w in enumerate(widths, 1):
        ws.column_dimensions[get_column_letter(i)].width = w
    ws.freeze_panes = 'B4'
    ws.auto_filter.ref = f"A3:{get_column_letter(ncol)}{ws.max_row}"
    last_data = ws.max_row
    if heat_col is not None and last_data >= 4:
        col = get_column_letter(heat_col + 1)
        ws.conditional_formatting.add(f"{col}4:{col}{last_data}",
            ColorScaleRule(start_type='min', start_color='63BE7B', mid_type='percentile', mid_value=50, mid_color='FFEB84', end_type='max', end_color='F8696B'))
        for rr in range(4, last_data + 1):
            ws.cell(rr, heat_col + 1).font = Font(name='Calibri', size=9, bold=True)
    if note:
        ws.append([]); ws.append([note])
        ws.merge_cells(start_row=ws.max_row, start_column=1, end_row=ws.max_row, end_column=ncol)
        ws.cell(ws.max_row, 1).font = SUB_FONT; ws.cell(ws.max_row, 1).alignment = Alignment(wrap_text=True, vertical='top')
        ws.row_dimensions[ws.max_row].height = 30
    # ---- печать: один лист A3, повтор шапки ----
    ws.page_setup.paperSize = ws.PAPERSIZE_A3
    ws.page_setup.orientation = orientation
    ws.page_setup.fitToWidth = 1; ws.page_setup.fitToHeight = 1
    ws.sheet_properties.pageSetUpPr.fitToPage = True
    ws.print_title_rows = '3:3'
    ws.print_area = f"A1:{get_column_letter(ncol)}{ws.max_row}"
    ws.page_margins.left = ws.page_margins.right = 0.25
    ws.page_margins.top = ws.page_margins.bottom = 0.35
    ws.page_margins.header = ws.page_margins.footer = 0.2
    ws.oddFooter.center.text = '&A — стр. &P из &N'; ws.oddFooter.center.size = 8
    ws.print_options.horizontalCentered = True
    ws.sheet_view.showGridLines = False
    return ws

wb = Workbook()
wb.remove(wb.active)

# ---------- лист 1: построено ----------
H1 = ['Название ЖК', 'Застройщик', 'Год постройки', 'Адрес', 'Район', 'Зона', 'Корпусов', 'Этажность',
      'Статус', 'Цена за метр ОТ', 'Цена за метр медиана', 'Цена за метр ДО', 'Лотов в продаже', 'Площадь лотов, м²',
      'Класс', 'Ссылка на Циан', 'Примечание']
rows1, rows2_live = [], []
live_targets = {v.get('live') for v in manual.values() if isinstance(v, dict) and v.get('live')}
for r in complexes['complexes']:
    m = manual.get(r['complex'], {})
    if r['complex'] in live_targets: continue   # строка заказчика на листе 2 уже покрывает этот ЖК
    z = m.get('zone') if m.get('zone') in ALLOWED else zone(r)
    if not z: continue
    med = r.get('perM2Median') or 0
    if m.get('class') is None and med < PREMIUM_PER_M2: continue
    if m.get('exclude'): continue
    ymax = r.get('buildYearMax')
    built = (r.get('finishedShare') or 0) >= 50 or (ymax and ymax <= 2025 and (r.get('finishedShare') is None))
    if 'second' in ','.join(r.get('sources', [])) and ymax and ymax < 2018 and not m.get('year'): continue
    status = 'апартаменты' if r['apartmentsShare'] >= 60 else ('квартиры + апартаменты' if r['apartmentsShare'] >= 15 else 'квартиры')
    addr = ', '.join(x for x in ['Москва', r.get('street'), r.get('house')] if x)
    dv = devs.get(r['complex'], {})
    cd = card(r['complex'])
    developer = m.get('developer') or dv.get('developer') or cd.get('developer') or ''
    cy = card_year_num(cd)
    if cy and cy < 2018 and not re.search(r'[–-]', str(cd.get('yearText') or '')): continue   # по карточке ЖК дом старше 2018
    cst = card_stage(cd)
    if cst: m = {**m, 'stage': cst}
    cls = m.get('class') or dv.get('class') or 'премиум'
    if m.get('class') and dv.get('class') and dv['class'] != m['class']: cls = dv['class'] if dv['class'] == 'бизнес' else m['class']
    cc = (cd.get('cls') or '').strip().lower()
    if cc in ('делюкс', 'премиум', 'бизнес', 'комфорт', 'эконом'):
        cls = cc
        if cc in ('бизнес', 'комфорт', 'эконом') and not m.get('keep') and med < 1_500_000: continue   # класс по карточке Циан ниже премиума
    year = card_year(cd) or m.get('year') or fmt_years(r)
    link = cd.get('url') or ''   # только страница ЖК на Циан; объявления не годятся
    row = [short_name(r['complex']), strip_paren(developer), year, short_addr(m.get('address') or addr), r.get('district'), z,
           max(r.get('housesSeen') or 0, dv.get('buildings') or 0) or None, floors(r) or dv.get('floors') or '', status,
           per_m2_str(r.get('perM2Min')), per_m2_str(med), per_m2_str(r.get('perM2Max')),
           r.get('declared') or r.get('lots'), f"{int(r['areaMin'])}–{int(r['areaMax'])}" if r.get('areaMin') and r['areaMin'] != math.inf else '',
           cls, link, describe(r['complex'])]
    (rows1 if (m.get('stage', 'built' if built else 'building') == 'built') else rows2_live).append(row)
rows1.sort(key=lambda x: (x[10] is None, x[10] or 0))
sheet(wb, '1. Построено (вторичка)', H1, rows1,
      [26, 18, 11, 26, 15, 16, 6, 7, 13, 10, 10, 10, 6, 9, 10, 7, 84],
      subtitle=f"ЦАО, дома 2018+, активные объявления Циан на {complexes['fetched']}; зоны: Садовое кольцо / Хамовники / Сити / Пресня / Белорусская. Сортировка по медиане ₽/м², цвет — от дешёвых (зелёный) к дорогим (красный). Класс — по карточке ЖК на Циан", zone_col=5, heat_col=10)

# ---------- лист 2: строится ----------
H2 = ['Название ЖК', 'Застройщик', 'Срок сдачи', 'Адрес', 'Район / метро', 'Зона', 'Корпусов', 'Этажность', 'Статус',
      'Цена за метр ОТ', 'Цена за метр медиана', 'Цена за метр ДО', 'Лотов в продаже', 'Отделка', 'Ссылка на проект', 'Примечание']
rows2 = []
seen = set()
for row in pdf['rows']:
    name, dev, dl, addr, metro, b, fl, st, pf, pt, lots, fin, note = row
    m = manual.get(name, {})
    dev = m.get('developer_override', dev)
    live = m.get('live')
    lr = next((c for c in complexes['complexes'] if c['complex'] == live), None) if live else None
    z = m.get('zone') if m.get('zone') in ALLOWED else (zone(lr) if lr else None)
    if z is None: continue
    pm = None
    if lr:
        pf, pt, lots = lr.get('perM2Min') or pf, lr.get('perM2Max') or pt, lr.get('declared') or lr.get('lots') or lots
        pm = per_m2_str(lr.get('perM2Median'))
    dv = devs.get(live, {}) if live else {}
    if pm is None: pm = int((pf + pt) / 2) if pf and pt else (pf or None)
    cd = (card(live) if live else {}) or card(name)
    link2 = cd.get('url') or m.get('url') or dv.get('site') or ''
    if card_year(cd): dl = card_year(cd)   # срок по карточке ЖК свежее таблицы
    rows2.append([short_name(name), strip_paren(dev), dl, short_addr(addr), metro, z, b, fl, st, pf, pm, pt, lots, fin, link2, describe(name, live)])
    seen.add(live or name)
for row in rows2_live:
    if row[0] in seen: continue
    m = manual.get(row[0], {})
    dv = devs.get(row[0], {})
    rows2.append([row[0], strip_paren(row[1]), row[2], row[3], row[4], row[5], row[6], row[7], row[8], row[9], row[10], row[11], row[12],
                  m.get('finish') or 'бетон', row[15] or m.get('url') or dv.get('site') or '', row[16]])
rows2.sort(key=lambda x: (x[10] is None, x[10] or 0))
sheet(wb, '2. Строится', H2, rows2,
      [26, 18, 12, 26, 16, 16, 6, 7, 13, 10, 10, 10, 6, 8, 7, 84],
      subtitle=f"Таблица заказчика + новостройки из выдачи Циан на {complexes['fetched']}. Сортировка по медиане ₽/м² (где Циан не нашёл ЖК — середина диапазона от/до), цвет — от дешёвых к дорогим", zone_col=5, heat_col=10)

# ---------- лист 3: проектирование ----------
H3 = ['Проект / участок', 'Адрес', 'Район', 'Зона', 'Застройщик', 'Стадия', 'Общая площадь, м²', 'Жилая площадь, м²',
      'Этажность', 'Лотов', 'Старт (план)', 'Сдача (план)', 'Цена от, ₽/м²', 'Дата новости', 'Ссылка', 'Примечание']
rows3 = []
for p in planning:
    if p.get('name') in manual.get('_planning_drop', []): continue   # стройка уже идёт — лист 2
    pz = manual.get('_planning_zone', {}).get(p.get('name'), p.get('location_zone'))
    if pz not in ALLOWED: continue
    dev3 = re.sub(r'\s*\(.*$', '', str(p.get('developer') or '')).strip() or 'не раскрыт'
    rows3.append([strip_paren(p.get('name')), short_addr(p.get('address')), p.get('district'), pz, dev3, short_stage(p.get('stage')),
                  p.get('area_total_m2'), p.get('area_residential_m2'), p.get('floors'), p.get('units'), p.get('planned_start'),
                  p.get('planned_completion'), p.get('price_from_per_m2'), p.get('announced_date'), p.get('source_url'), p.get('notes')])
rows3.sort(key=lambda x: (x[12] is None, x[12] or 0, str(x[0])))
sheet(wb, '3. Проектирование', H3, rows3,
      [28, 26, 15, 16, 20, 30, 9, 9, 9, 6, 12, 11, 10, 10, 7, 84],
      subtitle='Участки (ЗУ, КРТ, ГПЗУ) и анонсированные проекты без стройки, публикации 2025–2026. Сортировка по заявленной цене от, ₽/м²; без цены — в конце', zone_col=3, heat_col=12)

out = DOCS / 'premium-zhk-cao.xlsx'
wb.save(out)
print(f'-> {out}: {len(rows1)} / {len(rows2)} / {len(rows3)} строк')
