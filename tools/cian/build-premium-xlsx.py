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

ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / 'docs' / 'premium-cao'
complexes = json.load(open(DOCS / 'complexes.json'))
pdf = json.load(open(DOCS / 'pdf-table.json'))
planning = json.load(open(DOCS / 'planning.json'))
manual = json.load(open(DOCS / 'manual.json'))   # застройщик, класс, зона — ручная разметка

PREMIUM_PER_M2 = 700_000   # порог ₽/м² по медиане, ниже — бизнес-класс, в подборку не идёт

def zone(row):
    """Садовое кольцо / Хамовники / Сити / вне зоны — по району и координатам."""
    d = row.get('district') or ''
    if d == 'Хамовники':
        return 'Хамовники'
    in_city = row.get('lat') and row.get('lng') and 55.740 < row['lat'] < 55.768 and 37.505 < row['lng'] < 37.555
    if d in ('Пресненский', 'Хорошёвский', 'Хорошевский') and in_city:
        return 'Сити'
    if d in ('Хорошёвский', 'Хорошевский'):
        return None   # САО, вне ЦАО — берём только часть у Сити
    if row.get('insideRing') is True:
        return 'Садовое кольцо'
    return f'{d} (вне Садового)' if d else None

ZONE_ORDER = {'Садовое кольцо': 0, 'Хамовники': 1, 'Сити': 2}
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

thin = Side(style='thin', color='BBBBBB')
border = Border(left=thin, right=thin, top=thin, bottom=thin)
head_fill = PatternFill('solid', fgColor='FFF200')
head_font = Font(bold=True)
wrap = Alignment(wrap_text=True, vertical='top')
center = Alignment(horizontal='center', vertical='top', wrap_text=True)

def sheet(wb, title, headers, rows, widths, note=None):
    ws = wb.create_sheet(title)
    ws.append(headers)
    for c in ws[1]:
        c.fill = head_fill; c.font = head_font; c.alignment = center; c.border = border
    for r in rows:
        ws.append(r)
    for row in ws.iter_rows(min_row=2, max_row=ws.max_row):
        for c in row:
            c.border = border
            c.alignment = wrap if isinstance(c.value, str) else center
            if isinstance(c.value, (int, float)) and not isinstance(c.value, bool):
                c.number_format = '#,##0'
    for i, w in enumerate(widths, 1):
        ws.column_dimensions[get_column_letter(i)].width = w
    ws.freeze_panes = 'B2'
    ws.auto_filter.ref = ws.dimensions
    if note:
        ws.append([]); ws.append([note])
        ws.cell(ws.max_row, 1).font = Font(italic=True, color='666666')
    return ws

wb = Workbook()
wb.remove(wb.active)

# ---------- лист 1: построено ----------
H1 = ['Название ЖК', 'Застройщик', 'Год постройки', 'Адрес', 'Район', 'Зона', 'Корпусов (видно в выдаче)', 'Этажность',
      'Статус', 'Цена за метр ОТ', 'Цена за метр медиана', 'Цена за метр ДО', 'Лотов в продаже', 'Площадь лотов, м²',
      'Класс', 'Ссылка на Циан', 'Примечание']
rows1, rows2_live = [], []
live_targets = {v.get('live') for v in manual.values() if isinstance(v, dict) and v.get('live')}
for r in complexes['complexes']:
    m = manual.get(r['complex'], {})
    if r['complex'] in live_targets: continue   # строка заказчика на листе 2 уже покрывает этот ЖК
    z = m.get('zone') or zone(r)
    if not z: continue
    med = r.get('perM2Median') or 0
    if m.get('class') is None and med < PREMIUM_PER_M2: continue
    if m.get('exclude'): continue
    ymax = r.get('buildYearMax')
    built = (r.get('finishedShare') or 0) >= 50 or (ymax and ymax <= 2025 and (r.get('finishedShare') is None))
    if 'second' in ','.join(r.get('sources', [])) and ymax and ymax < 2018 and not m.get('year'): continue
    status = 'апартаменты' if r['apartmentsShare'] >= 60 else ('квартиры + апартаменты' if r['apartmentsShare'] >= 15 else 'квартиры')
    addr = ', '.join(x for x in ['Москва', r.get('street'), r.get('house')] if x)
    link = r['urls'][0] if r.get('urls') else ''
    row = [r['complex'], m.get('developer', ''), m.get('year') or fmt_years(r), m.get('address') or addr, r.get('district'), z,
           r.get('housesSeen'), floors(r), status, per_m2_str(r.get('perM2Min')), per_m2_str(med), per_m2_str(r.get('perM2Max')),
           r.get('declared') or r.get('lots'), f"{int(r['areaMin'])}–{int(r['areaMax'])}" if r.get('areaMin') and r['areaMin'] != math.inf else '',
           m.get('class', 'премиум'), link, m.get('note', '')]
    (rows1 if (m.get('stage', 'built' if built else 'building') == 'built') else rows2_live).append(row)
rows1.sort(key=lambda x: (zkey(x[5]), -(x[10] or 0)))
sheet(wb, '1. Построено (вторичка)', H1, rows1,
      [34, 22, 12, 36, 16, 16, 10, 10, 14, 14, 14, 14, 10, 14, 12, 40, 40],
      note=f"Источник: живая выдача Циан {complexes['fetched']} (api.cian.ru, инструмент tools/cian/cian.js), вторичка с годом дома 2018+ и новостройки по 10 районам ЦАО. Цены — ₽/м² по активным объявлениям. Застройщик и класс — по открытым данным (ручная разметка docs/premium-cao/manual.json).")

# ---------- лист 2: строится ----------
H2 = ['Название ЖК', 'Застройщик', 'Срок сдачи', 'Адрес', 'Район / метро', 'Зона', 'Корпусов', 'Этажность', 'Статус',
      'Цена за метр ОТ', 'Цена за метр ДО', 'Лотов в продаже', 'Отделка', 'Ссылка на проект', 'Источник', 'Примечание']
rows2 = []
seen = set()
for row in pdf['rows']:
    name, dev, dl, addr, metro, b, fl, st, pf, pt, lots, fin, note = row
    m = manual.get(name, {})
    dev = m.get('developer_override', dev)
    z = m.get('zone')
    if z is None: continue
    live = m.get('live')
    lr = next((c for c in complexes['complexes'] if c['complex'] == live), None) if live else None
    src = 'таблица заказчика'
    if lr:
        pf, pt, lots = lr.get('perM2Min') or pf, lr.get('perM2Max') or pt, lr.get('declared') or lr.get('lots') or lots
        src = f"таблица заказчика + Циан {complexes['fetched']}"
        if (lr.get('finishedShare') or 0) >= 50: note = '; '.join(x for x in [note, 'по Циан дом сдан'] if x)
        if lr.get('urls') and not m.get('url'): m = {**m, 'url': lr['urls'][0]}
    rows2.append([name, dev, dl, addr, metro, z, b, fl, st, pf, pt, lots, fin, m.get('url', ''), src, '; '.join(x for x in [note, m.get('note')] if x)])
    seen.add(live or name)
for row in rows2_live:
    if row[0] in seen: continue
    m = manual.get(row[0], {})
    rows2.append([row[0], row[1], row[2], row[3], row[4], row[5], row[6], row[7], row[8], row[9], row[11], row[12], m.get('finish', ''), row[15], f"Циан {complexes['fetched']}", row[16]])
rows2.sort(key=lambda x: (zkey(x[5]), str(x[2])))
sheet(wb, '2. Строится', H2, rows2,
      [34, 22, 12, 36, 18, 16, 9, 10, 14, 14, 14, 10, 10, 40, 24, 40],
      note='Строки из таблицы заказчика дополнены живой выдачей Циан там, где ЖК найден в базе (цены и число лотов обновлены). Остальные строки — найдены в выдаче Циан по новостройкам ЦАО.')

# ---------- лист 3: проектирование ----------
H3 = ['Проект / участок', 'Адрес', 'Район', 'Зона', 'Застройщик', 'Стадия', 'Общая площадь, м²', 'Жилая площадь, м²',
      'Этажность', 'Лотов', 'Старт (план)', 'Сдача (план)', 'Цена от, ₽/м²', 'Дата новости', 'Источник', 'Ссылка', 'Примечание']
rows3 = []
for p in planning:
    if p.get('name') in manual.get('_planning_drop', []): continue   # стройка уже идёт — лист 2
    rows3.append([p.get('name'), p.get('address'), p.get('district'), manual.get('_planning_zone', {}).get(p.get('name'), p.get('location_zone')), p.get('developer'), p.get('stage'),
                  p.get('area_total_m2'), p.get('area_residential_m2'), p.get('floors'), p.get('units'), p.get('planned_start'),
                  p.get('planned_completion'), p.get('price_from_per_m2'), p.get('announced_date'), p.get('source_name'),
                  p.get('source_url'), p.get('notes')])
rows3.sort(key=lambda x: (zkey(x[3]), str(x[0])))
sheet(wb, '3. Проектирование', H3, rows3,
      [34, 34, 16, 16, 22, 26, 12, 12, 10, 8, 12, 12, 14, 12, 22, 44, 50],
      note='Источники: открытые публикации 2025–2026 (stroi.mos.ru, mos.ru, отраслевые СМИ, публичные Telegram-каналы). Закрытый канал t.me/c/3370602239 недоступен без членства — пост №1364 про ЗУ «Большой Тишинский, 8» подтверждён по открытым источникам.')

out = DOCS / 'premium-zhk-cao.xlsx'
wb.save(out)
print(f'-> {out}: {len(rows1)} / {len(rows2)} / {len(rows3)} строк')
