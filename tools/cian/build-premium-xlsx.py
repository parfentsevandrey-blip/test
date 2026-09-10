#!/usr/bin/env python3
"""Собрать docs/premium-cao/premium-zhk-cao.xlsx из трёх источников:

  1. complexes.json  — сводка по ЖК из живой выдачи Циан (aggregate-complexes.js)
  2. pdf-table.json  — таблица заказчика (скан), строящиеся проекты
  3. planning.json   — проекты в стадии проектирования / ЗУ (веб-исследование)

Три листа: «1. Построено (вторичка)», «2. Строится», «3. Проектирование».
"""
import json, re, sys, math
from datetime import date
from pathlib import Path
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.utils import get_column_letter
from openpyxl.formatting.rule import ColorScaleRule
from openpyxl.drawing.image import Image as XLImage

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

def card_delivery(c):
    """Срок сдачи как на карточке ЖК: «2023», «2021–2023», «4 кв. 2026»."""
    return (c.get('delivery') or '').strip()

def card_years(c):
    return [int(y) for y in re.findall(r'(20\d\d)', card_delivery(c))]

def card_year_num(c):
    ys = []
    for k in ('finished', 'yearText', 'deadline'):
        ys += [int(y) for y in re.findall(r'(20\d\d)', c.get(k) or '')]
    return max(ys) if ys else None

QUARTER_END = {1: (3, 31), 2: (6, 30), 3: (9, 30), 4: (12, 31)}

def card_deadline_date(c):
    """Дата, к которой карточка ЖК обещает сдачу: конец квартала или года."""
    t = ' '.join(str(c.get(k) or '') for k in ('deadline', 'yearText'))
    m = re.search(r'(\d)\s*кв\.?\s*(20\d\d)', t)
    if m: q, y = int(m.group(1)), int(m.group(2)); return date(y, *QUARTER_END.get(q, (12, 31)))
    ys = [int(y) for y in re.findall(r'(20\d\d)', t)]
    return date(max(ys), 12, 31) if ys else None

def card_says_done(c):
    return bool(re.search(r'Сдан', ' '.join(str(c.get(k) or '') for k in ('finished', 'yearText', 'stage'))))

def card_says_future(c):
    if not re.search(r'Сдача|Строится', ' '.join(str(c.get(k) or '') for k in ('deadline', 'yearText', 'stage'))): return False
    d = card_deadline_date(c)
    return bool(d and d > TODAY)

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

TODAY = date.fromisoformat(json.load(open(DOCS / 'complexes.json'))['fetched'])
NOW_YEAR = TODAY.year
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
GROUP_FILL = PatternFill('solid', fgColor='D9E2F3')
GROUP_FONT = Font(name='Calibri', size=11, bold=True, color='1F3864')
STATUS_FILL = {'построено': 'C6EFCE', 'строится': 'FFE0B3', 'проектирование': 'E4D5F5'}
ZONE_FILL = {'Садовое кольцо': 'FFF2CC', 'Хамовники': 'E2EFDA', 'Сити': 'DDEBF7', 'Пресня': 'FCE4D6', 'Белорусская': 'EDEDED'}
wrap = Alignment(horizontal='center', vertical='center', wrap_text=True)
center = Alignment(horizontal='center', vertical='center', wrap_text=True)
right = Alignment(horizontal='center', vertical='center')

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

def main_district(v):
    """«Пресненский (Патриаршие пруды)», «Тверской/Беговой» → «Пресненский», «Тверской»."""
    d = re.sub(r'\s*\(.*$', '', str(v or '')).split('/')[0].split(' / ')[0].strip()
    return d or '—'

def short_stage(v):
    v = strip_paren(v)
    v = re.split(r'[;—]', v)[0].strip()
    return v[:48].rstrip(' ,') if len(v) > 48 else v

def plural(n, forms):
    n10, n100 = n % 10, n % 100
    if n10 == 1 and n100 != 11: return forms[0]
    if 2 <= n10 <= 4 and not 12 <= n100 <= 14: return forms[1]
    return forms[2]

def cluster(rows, district_i, price_i, forms=('ЖК', 'ЖК', 'ЖК')):
    """Строки по кластерам-районам: крупные районы выше, внутри — по возрастанию цены."""
    groups = {}
    for r in rows: groups.setdefault(str(r[district_i] or '—').strip() or '—', []).append(r)
    out = []
    for name, rs in sorted(groups.items(), key=lambda kv: (-len(kv[1]), kv[0])):
        rs.sort(key=lambda x: (x[price_i] is None, x[price_i] or 0, str(x[0])))
        out.append(('__GROUP__', f'{name} — {len(rs)} {plural(len(rs), forms)}'))
        out.extend(rs)
    return out

def sheet(wb, title, headers, rows, widths, note=None, subtitle='', zone_col=None, orientation='landscape', heat_col=None, status_col=None):
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
    grouped = any(r and r[0] == '__GROUP__' for r in rows)
    i = 0
    for r in rows:
        if r and r[0] == '__GROUP__':          # заголовок кластера-района на всю ширину
            ws.append([r[1]]); rr = ws.max_row
            ws.merge_cells(start_row=rr, start_column=1, end_row=rr, end_column=ncol)
            for cc in ws[rr]: cc.fill = GROUP_FILL; cc.border = Border(top=MED_SIDE, bottom=MED_SIDE)
            c = ws.cell(rr, 1); c.font = GROUP_FONT
            c.alignment = Alignment(horizontal='left', vertical='center', indent=1)
            ws.row_dimensions[rr].height = 20; i = 0
            continue
        ws.append(r)
        rr = ws.max_row
        zone = r[zone_col] if zone_col is not None else None
        fill = ZEBRA if i % 2 else None
        i += 1
        for c in ws[rr]:
            c.border = border; c.font = BODY_FONT
            if fill: c.fill = fill
            if status_col is not None and c.column == status_col + 1 and c.value in STATUS_FILL:
                c.fill = PatternFill('solid', fgColor=STATUS_FILL[c.value])
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
        if isinstance(last.value, str): last.font = Font(name='Calibri', size=9); last.alignment = Alignment(horizontal='center', vertical='center', wrap_text=True)
        # высота строки — по самой «многострочной» ячейке: длина текста / ширина колонки
        lines = 1
        for j, v in enumerate(r):
            if not isinstance(v, str) or not v or is_url(v): continue   # ссылки печатаются словом «ссылка»
            cw = widths[j] if j < len(widths) else 10
            cpl = max(4, int(cw * (1.0 if j == ncol - 1 else 0.82)))   # кириллица 10 пт шире «0» шрифта по умолчанию; примечания 9 пт
            lines = max(lines, math.ceil(len(v) / cpl))
        ws.row_dimensions[rr].height = 16 if lines == 1 else 13.5 * lines + 4
    for i, w in enumerate(widths, 1):
        ws.column_dimensions[get_column_letter(i)].width = w
    ws.freeze_panes = 'B4'
    if not grouped: ws.auto_filter.ref = f"A3:{get_column_letter(ncol)}{ws.max_row}"
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
BUILT_MIN_YEAR = 2022     # вторичку сдачи раньше этого года в подборку не берём
zone_by_name = {}
H1 = ['Название ЖК', 'Застройщик', 'Год постройки', 'Адрес', 'Район', 'Корпусов', 'Этажность',
      'Статус', 'Цена за метр ОТ', 'Цена за метр медиана', 'Цена за метр ДО', 'Лотов в продаже', 'Площадь лотов, м²',
      'Класс', 'Ссылка на Циан', 'Примечание']
rows1, rows2_live = [], []
built_live = set()   # ЖК из таблицы заказчика, которые по данным Циан уже сданы
live_targets = {v.get('live') for v in manual.values() if isinstance(v, dict) and v.get('live')}
# зона строки заказчика переносится на её ЖК в выдаче: у части ЖК координаты Циан
# лежат чуть за контуром Пресни, а строка заказчика уже отнесена к зоне вручную
live_zone = {v['live']: v['zone'] for v in manual.values()
             if isinstance(v, dict) and v.get('live') and v.get('zone') in ALLOWED}
for r in complexes['complexes']:
    m = manual.get(r['complex'], {})
    z = m.get('zone') if m.get('zone') in ALLOWED else (live_zone.get(r['complex']) or zone(r))
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
    # Стадию решают лоты, а не карточка ЖК: в карточке стоит срок ПОСЛЕДНЕЙ очереди,
    # из-за чего сданные дома (Резиденция 1864, Armani/Casa) уезжали в «строится».
    # У новостройки на предпродаже Циан ставит лотам «дом сдан» задолго до ввода,
    # поэтому решают: год постройки в объявлениях, формулировка карточки ЖК и срок сдачи.
    yrs = [y for y in (r.get('buildYears') or []) + (r.get('deadlineYears') or []) if y]
    ymax_lots = max(yrs) if yrs else None
    lots_finished = (r.get('finishedShare') or 0) >= 50
    bmax = r.get('buildYearMax')
    if cd.get('done') is True: lots_built = True                                    # плашка «Сдан» на карточке ЖК
    elif cd.get('done') is False: lots_built = False                                # карточка есть, дом ещё строится
    elif lots_finished and ymax_lots and ymax_lots < NOW_YEAR: lots_built = True    # карточки нет — по объявлениям
    elif bmax and bmax <= NOW_YEAR: lots_built = True
    else: lots_built = False
    m = {**m, 'stage': 'built' if lots_built else 'building'}
    phase_note = ''
    cls = m.get('class') or dv.get('class') or 'премиум'
    if m.get('class') and dv.get('class') and dv['class'] != m['class']: cls = dv['class'] if dv['class'] == 'бизнес' else m['class']
    cc = (cd.get('cls') or '').strip().lower()
    if cc in ('делюкс', 'премиум', 'бизнес', 'комфорт', 'эконом'):
        cls = cc
        if cc in ('бизнес', 'комфорт', 'эконом') and not m.get('keep') and med < 1_500_000: continue   # класс по карточке Циан ниже премиума
    year = card_delivery(cd) or (fmt_years(r) if lots_built else None) or m.get('year') or fmt_years(r)
    link = cd.get('url') or ''   # только страница ЖК на Циан; объявления не годятся
    row = [short_name(r['complex']), strip_paren(developer), year, short_addr(m.get('address') or addr), main_district(r.get('district')),
           max(r.get('housesSeen') or 0, dv.get('buildings') or 0) or None, floors(r) or dv.get('floors') or '', status,
           per_m2_str(r.get('perM2Min')), per_m2_str(med), per_m2_str(r.get('perM2Max')),
           r.get('declared') or r.get('lots'), f"{int(r['areaMin'])}–{int(r['areaMax'])}" if r.get('areaMin') and r['areaMin'] != math.inf else '',
           cls, link, ' · '.join(x for x in [describe(r['complex']), phase_note] if x)]
    is_built = m.get('stage', 'built' if built else 'building') == 'built'
    if r['complex'] in live_targets:
        # строку из таблицы заказчика оставляем на листе 2, только пока дом не сдан
        if not is_built: continue
        built_live.add(r['complex'])
    ys = card_years(cd) or [int(y) for y in re.findall(r'(20\d\d)', str(year))]
    if is_built and ys and min(ys) < BUILT_MIN_YEAR: continue   # срок сдачи по карточке ЖК раньше 2022
    zone_by_name[row[0]] = z
    (rows1 if is_built else rows2_live).append(row)
rows1 = cluster(rows1, 4, 9)
sheet(wb, '1. Построено (вторичка)', H1, rows1,
      [26, 18, 13, 28, 16, 6, 7, 13, 10, 10, 10, 6, 9, 10, 7, 90],
      subtitle=f"ЦАО, активные объявления Циан на {complexes['fetched']}; срок сдачи по карточке ЖК — 2022 и позже. Кластеры по районам (крупные выше), внутри — по возрастанию медианы ₽/м², цвет от дешёвых (зелёный) к дорогим (красный). Класс — по карточке ЖК на Циан", heat_col=9)

# ---------- лист 2: строится ----------
H2 = ['Название ЖК', 'Застройщик', 'Срок сдачи', 'Адрес', 'Район', 'Корпусов', 'Этажность', 'Статус',
      'Цена за метр ОТ', 'Цена за метр медиана', 'Цена за метр ДО', 'Лотов в продаже', 'Отделка', 'Ссылка на проект', 'Примечание']
rows2 = []
seen = set()
for row in pdf['rows']:
    name, dev, dl, addr, metro, b, fl, st, pf, pt, lots, fin, note = row
    m = manual.get(name, {})
    dev = m.get('developer_override', dev)
    live = m.get('live')
    lr = next((c for c in complexes['complexes'] if c['complex'] == live), None) if live else None
    if live in built_live: continue   # по Циан дом сдан — строка ушла на лист 1
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
    if card_delivery(cd): dl = card_delivery(cd)   # срок сдачи как на карточке ЖК
    zone_by_name[short_name(name)] = z
    rows2.append([short_name(name), strip_paren(dev), dl, short_addr(addr), main_district(m.get('district') or (lr or {}).get('district') or metro), b, fl, st, pf, pm, pt, lots, fin, link2, describe(name, live)])
    seen.add(live or name)
for row in rows2_live:
    if row[0] in seen: continue
    m = manual.get(row[0], {})
    dv = devs.get(row[0], {})
    rows2.append([row[0], strip_paren(row[1]), row[2], row[3], main_district(row[4]), row[5], row[6], row[7], row[8], row[9], row[10], row[11],
                  m.get('finish') or 'бетон', row[14] or m.get('url') or dv.get('site') or '', row[15]])
# ---------- лист 3: проектирование (по Telegram-каналам) ----------
TG = json.load(open(DOCS / 'tg-sites.json')) if (DOCS / 'tg-sites.json').exists() else None
CONF = {'high': 'высокая', 'medium': 'средняя', 'low': 'низкая'}
H3 = ['Проект / участок', 'Адрес', 'Точный адрес / адреса', 'Район', 'Застройщик', 'Стадия', 'Общая площадь, м²', 'Лотов', 'Этажность',
      'Старт (план)', 'Сдача (план)', 'Цена от, ₽/м²', 'Первое упоминание', 'Последнее упоминание', 'Достоверность',
      'Пост 1', 'Пост 2', 'Пост 3', 'Что известно']
rows3 = []
if TG:
    for e in TG:
        if e.get('zone') not in ALLOWED: continue
        posts = sorted(e.get('posts') or [], key=lambda x: x.get('date') or '', reverse=True)[:3]
        links = [pp['url'] for pp in posts] + [''] * (3 - len(posts))
        zone_by_name[strip_paren(e.get('name'))] = e['zone']
        rows3.append([strip_paren(e.get('name')), short_addr(e.get('address')), e.get('address') or '', main_district(e.get('district')),
                      strip_paren(e.get('developer')) or 'не раскрыт', short_stage(e.get('stage')),
                      e.get('area_total_m2'), e.get('units'), strip_paren(e.get('floors')) or None,
                      strip_paren(e.get('planned_start')) or None, strip_paren(e.get('planned_completion')) or None,
                      e.get('price_from_per_m2'), e.get('first_seen'), e.get('last_seen'), CONF.get(e.get('confidence'), ''),
                      *links, e.get('what') or ''])
else:
    for p in planning:
        if p.get('name') in manual.get('_planning_drop', []): continue
        pz = manual.get('_planning_zone', {}).get(p.get('name'), p.get('location_zone'))
        if pz not in ALLOWED: continue
        zone_by_name[strip_paren(p.get('name'))] = pz
        rows3.append([strip_paren(p.get('name')), short_addr(p.get('address')), p.get('address') or '', main_district(p.get('district')),
                      re.sub(r'\s*\(.*$', '', str(p.get('developer') or '')).strip() or 'не раскрыт', short_stage(p.get('stage')),
                      p.get('area_total_m2'), p.get('units'), strip_paren(p.get('floors')) or None, strip_paren(p.get('planned_start')) or None,
                      strip_paren(p.get('planned_completion')) or None, p.get('price_from_per_m2'), p.get('announced_date'), None, '',
                      p.get('source_url') or '', '', '', p.get('notes')])
def nrm(x): return re.sub(r'[^а-яa-z0-9]', '', str(x or '').lower())
sheet12 = [(nrm(r[0]), nrm(r[3])) for r in rows1 + rows2 if r[0] != '__GROUP__']
BUILDING_RE = re.compile(r'котлован|строительств|строится|РнС выдано|продажи откры|старт продаж состоял|монолит', re.I)
keep3, moved, dropped = [], [], []
for e in rows3:
    n, a = nrm(e[0]), nrm(e[2] or e[1])
    dup = next((x for x in sheet12 if (x[0] and (x[0] in n or n in x[0]) and len(x[0]) > 6) or (x[1] and a and x[1] == a)), None)
    if dup: dropped.append((e[0], dup[0])); continue
    if BUILDING_RE.search(str(e[5] or '')):
        # проект уже строится или продаётся — его место на листе «Строится»
        price = e[11]
        rows2.append([e[0], e[4], e[10] or e[9] or '', e[1], main_district(e[3]), None, e[8], 'квартиры',
                      price, price, None, e[7], '', (e[15] or ''), (e[18] or '')])
        moved.append(e[0]); continue
    keep3.append(e)
rows3 = keep3
if dropped: print('лист 3 → убраны дубли листов 1–2:', '; '.join(f'{a} = {b}' for a, b in dropped))
if moved: print('лист 3 → перенесены в «Строится»:', '; '.join(moved))
rows2 = cluster(rows2, 4, 9)
sheet(wb, '2. Строится', H2, rows2,
      [26, 18, 13, 28, 17, 6, 7, 13, 10, 10, 10, 6, 8, 7, 90],
      subtitle=f"Таблица заказчика + новостройки из выдачи Циан на {complexes['fetched']}. Кластеры по районам, внутри — по возрастанию медианы ₽/м² (где Циан не нашёл ЖК — середина диапазона от/до)", heat_col=9)

# ---------- лист 3 ----------
rows3 = cluster(rows3, 3, 11, ('площадка', 'площадки', 'площадок'))
sheet(wb, '3. Проектирование', H3, rows3,
      [24, 20, 26, 14, 18, 20, 9, 6, 9, 10, 10, 10, 10, 10, 10, 8, 8, 8, 72],
      subtitle='Площадки (ЗУ, КРТ, ГПЗУ, АГР, сделки с участками) и анонсированные проекты без стройки по отраслевым Telegram-каналам, июнь 2025 — сентябрь 2026. Пруфы — ссылки на посты. Кластеры по районам, внутри — по возрастанию цены. Часть проектов уже вышла на стройку или в продажи — см. колонку «Стадия»',
      heat_col=11)

# ---------- лист: источники (Telegram-каналы) ----------
chs = json.load(open(DOCS / 'tg-channels.json')) if (DOCS / 'tg-channels.json').exists() else []
if chs:
    cnt = {}
    for e in (TG or []):
        for pp in e.get('posts') or []:
            m = re.search(r't\.me/([^/]+)/', pp.get('url') or ''); h = (m.group(1) if m else '').lower()
            cnt[h] = cnt.get(h, 0) + 1
    HS = ['Канал', 'Хендл', 'Ссылка', 'Профиль', 'Подписчиков', 'Постов в подборке']
    rs = [[c['title'], '@' + c['handle'], f"https://t.me/{c['handle']}", c.get('focus') or '', c.get('subscribers'), cnt.get(c['handle'].lower(), 0)] for c in chs]
    rs.sort(key=lambda r: (-r[5], -(r[4] or 0)))
    sheet(wb, 'Источники — TG-каналы', HS, rs, [34, 26, 8, 70, 13, 12],
          subtitle='Отраслевые Telegram-каналы, просканированные по публичным превью t.me/s/<канал> за июнь 2025 — сентябрь 2026; отобраны посты про площадки в наших зонах', zone_col=None)

# ---------- точки для карт ----------
coord = {c['complex']: (c.get('lat'), c.get('lng')) for c in complexes['complexes']}
points = []
for row in rows1:
    if row[0] == '__GROUP__': continue
    src = next((c for c in complexes['complexes'] if short_name(c['complex']) == row[0]), None)
    points.append({'name': row[0], 'status': 'построено', 'zone': zone_by_name.get(row[0]), 'address': row[3], 'lat': src and src.get('lat'), 'lng': src and src.get('lng')})
for row in rows2:
    if row[0] == '__GROUP__': continue
    live = None
    for k, v in manual.items():
        if isinstance(v, dict) and short_name(k) == row[0] and v.get('live'): live = v['live']
    src = next((c for c in complexes['complexes'] if c['complex'] == live or short_name(c['complex']) == row[0]), None)
    points.append({'name': row[0], 'status': 'строится', 'zone': zone_by_name.get(row[0]), 'address': row[3], 'lat': src and src.get('lat'), 'lng': src and src.get('lng')})
for row in rows3:
    if row[0] == '__GROUP__': continue
    points.append({'name': row[0], 'status': 'проектирование', 'zone': zone_by_name.get(row[0]), 'address': row[1], 'lat': None, 'lng': None})
json.dump(points, open(DOCS / 'points.json', 'w'), ensure_ascii=False, indent=1)

# ---------- листы-карты: карта на весь лист A3, список — отдельным листом ----------
SHORT_MAP = {'sadovoe': 'Садовое кольцо', 'khamovniki': 'Хамовники', 'presnya': 'Пресня и Сити'}
idx_path = DOCS / 'maps' / 'index.json'
if idx_path.exists():
    for mp in json.load(open(idx_path))['maps']:
        short = SHORT_MAP.get(mp['slug'], mp['title'])
        ws = wb.create_sheet(f'Карта · {short}'[:31])
        IMG_W = 2000                                   # px по ширине листа A3 при вписывании в страницу
        scale = IMG_W / mp['width']; img_h = int(mp['height'] * scale)
        COLW, PX = 12.0, 89                            # ширина колонки в символах ≈ 89 px
        ncols = math.ceil(IMG_W / PX)
        for i in range(1, ncols + 1): ws.column_dimensions[get_column_letter(i)].width = COLW
        ws['A1'] = f"Карта: {mp['title']} — премиум-ЖК 2018+ по статусу"; ws['A1'].font = TITLE_FONT
        ws.merge_cells(start_row=1, start_column=1, end_row=1, end_column=ncols); ws.row_dimensions[1].height = 26
        ws['A2'] = 'Зелёный — построено, оранжевый — строится, фиолетовый — проектирование. Номер маркера = номер в списке на соседнем листе. Подложка: Яндекс Карты.'
        ws['A2'].font = SUB_FONT; ws.merge_cells(start_row=2, start_column=1, end_row=2, end_column=ncols)
        im = XLImage(str(DOCS / mp['file'])); im.width = IMG_W; im.height = img_h
        ws.add_image(im, 'A3')
        nrows = 3 + math.ceil(img_h / 20)
        for rr in range(3, nrows + 1): ws.row_dimensions[rr].height = 20
        ws.page_setup.paperSize = ws.PAPERSIZE_A3; ws.page_setup.orientation = 'landscape'
        ws.page_setup.fitToWidth = 1; ws.page_setup.fitToHeight = 1; ws.sheet_properties.pageSetUpPr.fitToPage = True
        ws.print_area = f"A1:{get_column_letter(ncols)}{nrows}"
        ws.page_margins.left = ws.page_margins.right = 0.2; ws.page_margins.top = ws.page_margins.bottom = 0.25
        ws.print_options.horizontalCentered = True
        ws.sheet_view.showGridLines = False

        rows_l = [[e['n'], e['name'], e['status'], e.get('address') or ''] for e in mp['legend']]
        sheet(wb, f'Список · {short}'[:31], ['№ на карте', 'ЖК / площадка', 'Статус', 'Адрес'], rows_l,
              [10, 52, 18, 66], status_col=2,
              subtitle=f"Номера соответствуют маркерам на листе «Карта · {short}»: сначала построенные, затем строящиеся, затем площадки в проектировании")

out = DOCS / 'premium-zhk-cao.xlsx'
wb.save(out)
nz = lambda rs: sum(1 for r in rs if r[0] != '__GROUP__')
print(f'-> {out}: {nz(rows1)} / {nz(rows2)} / {nz(rows3)} строк')
