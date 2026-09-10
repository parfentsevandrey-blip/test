#!/usr/bin/env python3
"""Собрать docs/premium-cao/premium-zhk-cao.pdf — вся книга одним документом под A3 (альбомная).

  python3 tools/cian/export-pdf.py

Читает готовый premium-zhk-cao.xlsx (значения, ширины колонок, ссылки), раскладывает
в HTML под ширину печатной полосы A3 и печатает Chromium'ом. Таблицы длиннее страницы
переносятся с повтором шапки; листы-карты идут картинкой во всю полосу и списком под ней.
"""
import json, re, subprocess, html
from pathlib import Path
from openpyxl import load_workbook
from openpyxl.utils import get_column_letter

ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / 'docs' / 'premium-cao'
XLSX = DOCS / 'premium-zhk-cao.xlsx'
OUT = DOCS / 'premium-zhk-cao.pdf'
HTML = Path('/tmp/claude-0/-home-user-test/5ef931c1-a5eb-584d-bea4-9b195abd4240/scratchpad/book.html')

ZONE_FILL = {'Садовое кольцо': '#FFF2CC', 'Хамовники': '#E2EFDA', 'Сити': '#DDEBF7', 'Пресня': '#FCE4D6', 'Белорусская': '#EDEDED'}
HEAT = [(0.0, (99, 190, 123)), (0.5, (255, 235, 132)), (1.0, (248, 105, 107))]

def heat_color(v, lo, hi):
    if v is None or hi <= lo: return None
    t = max(0.0, min(1.0, (v - lo) / (hi - lo)))
    for (t1, c1), (t2, c2) in zip(HEAT, HEAT[1:]):
        if t <= t2:
            k = 0 if t2 == t1 else (t - t1) / (t2 - t1)
            r, g, b = (int(c1[i] + (c2[i] - c1[i]) * k) for i in range(3))
            return f'#{r:02x}{g:02x}{b:02x}'
    return None

SHORT_HEAD = {'Корпусов': 'Корп.', 'Этажность': 'Этажей', 'Лотов в продаже': 'Лотов',
              'Площадь лотов, м²': 'Площадь, м²', 'Ссылка на Циан': 'Циан', 'Ссылка на проект': 'Проект',
              'Цена за метр ОТ': 'Цена ₽/м² от', 'Цена за метр медиана': 'Цена ₽/м² медиана',
              'Цена за метр ДО': 'Цена ₽/м² до', 'Общая площадь, м²': 'Площадь, м²',
              'Точный адрес / адреса': 'Точный адрес', 'Первое упоминание': '1-е упом.',
              'Последнее упоминание': 'Посл. упом.', 'Достоверность': 'Достов.', 'Срок сдачи': 'Сдача',
              'Год постройки': 'Год', 'Год сдачи': 'Год', 'Район / метро': 'Район / метро', 'Цена от, ₽/м²': 'Цена ₽/м² от'}

def esc(v): return html.escape(str(v)) if v is not None else ''

def fmt(v):
    if isinstance(v, bool) or v is None: return ''
    if isinstance(v, (int, float)): return f'{int(v):,}'.replace(',', ' ')
    return esc(v)

wb = load_workbook(XLSX)
maps_idx = json.load(open(DOCS / 'maps' / 'index.json')) if (DOCS / 'maps' / 'index.json').exists() else {'maps': []}
parts = []

for ws in wb:
    is_map = ws.title.startswith('Карта')
    title = ws['A1'].value or ws.title
    subtitle = ws['A2'].value or ''
    if is_map:
        mp = next((m for m in maps_idx['maps'] if ws.title.endswith(m['title'][:12]) or m['slug'] in ws.title.lower()
                   or any(w in ws.title for w in m['title'].split(',')[0].split())), None)
        if not mp: continue
        img = (DOCS / mp['file']).as_uri()
        parts.append(f"""<section class="page map">
  <h1>{esc(title)}</h1><p class="sub">{esc(subtitle)}</p>
  <div class="mapwrap"><img src="{img}"></div>
</section>""")
        continue

    ncol = ws.max_column
    widths = [ws.column_dimensions[get_column_letter(i + 1)].width or 10 for i in range(ncol)]
    total = sum(widths)
    head = [c.value for c in ws[3]]
    body = []
    for row in ws.iter_rows(min_row=4, max_row=ws.max_row):
        if not row[0].value: continue
        body.append(row)
    # колонка для тепловой шкалы — «Цена за метр медиана» / «Цена от»
    heat_i = next((i for i, h in enumerate(head) if h and ('медиана' in str(h) or 'Цена от' in str(h))), None)
    zone_i = next((i for i, h in enumerate(head) if h and str(h).strip() == 'Зона'), None)
    vals = [row[heat_i].value for row in body if heat_i is not None and isinstance(row[heat_i].value, (int, float))]
    lo, hi = (min(vals), max(vals)) if vals else (0, 1)
    cols = ''.join(f'<col style="width:{w / total * 100:.3f}%">' for w in widths)
    th = ''.join(f'<th>{esc(SHORT_HEAD.get(str(h).strip(), h))}</th>' for h in head)
    STATUS_BG = {'построено': '#C6EFCE', 'строится': '#FFE0B3', 'проектирование': '#E4D5F5'}
    trs = []
    n = 0
    for row in body:
        if all(c.value is None for c in row[1:]):        # заголовок кластера-района
            trs.append(f'<tr class="grp"><td colspan="{ncol}">{esc(row[0].value)}</td></tr>'); n = 0; continue
        tds = []; n += 1
        for i, c in enumerate(row):
            v = c.value; style = ''; cls = ''
            if c.hyperlink is not None or (isinstance(v, str) and v.startswith('http')):
                href = c.hyperlink.target if c.hyperlink is not None else v
                tds.append(f'<td class="lnk"><a href="{esc(href)}">ссылка</a></td>'); continue
            if i == zone_i and v in ZONE_FILL: style = f'background:{ZONE_FILL[v]};font-weight:600'
            if i == heat_i and isinstance(v, (int, float)):
                col = heat_color(v, lo, hi)
                if col: style = f'background:{col};font-weight:600'
            if isinstance(v, str) and v in STATUS_BG: style = f'background:{STATUS_BG[v]}'
            if isinstance(v, (int, float)): cls = 'num'
            elif isinstance(v, str) and len(v) > 30: cls = 'txt'
            tds.append(f'<td class="{cls}" style="{style}">{fmt(v)}</td>')
        trs.append(f'<tr class="{ "odd" if n % 2 == 0 else "" }">' + ''.join(tds) + '</tr>')
    parts.append(f'''<section class="page">
  <h1>{esc(title)}</h1><p class="sub">{esc(subtitle)}</p>
  <table class="grid"><colgroup>{cols}</colgroup><thead><tr>{th}</tr></thead><tbody>{''.join(trs)}</tbody></table>
</section>''')

css = '''
@page { size: A3 landscape; margin: 7mm 6mm 9mm 6mm; }
* { box-sizing: border-box; }
body { font-family: 'DejaVu Sans', Arial, sans-serif; margin: 0; color: #1a1a1a; }
section.page { page-break-after: always; }
section.page:last-child { page-break-after: auto; }
h1 { font-size: 15pt; color: #1F3864; margin: 0 0 2px; }
p.sub { font-size: 7pt; color: #666; font-style: italic; margin: 0 0 6px; }
table { width: 100%; border-collapse: collapse; table-layout: fixed; }
table.grid { font-size: 6.4pt; }
table.grid th { background: #1F3864; color: #fff; font-size: 5.9pt; font-weight: 700; padding: 3px 1px;
  border: 0.5px solid #9aa8bd; text-align: center; vertical-align: middle; line-height: 1.08;
  word-wrap: break-word; overflow-wrap: anywhere; hyphens: auto; }
table.grid td { border: 0.4px solid #c8c8c8; padding: 2px 2px; text-align: center; vertical-align: middle;
  word-wrap: break-word; overflow-wrap: anywhere; hyphens: auto; }
table.grid td.num { text-align: center; white-space: nowrap; }
table.grid td.txt { text-align: left; }
table.grid tr.odd td { background: #F3F6FA; }
thead { display: table-header-group; }
tr { page-break-inside: avoid; }
a { color: #0563C1; }
td.lnk { font-size: 6pt; }
section.map { display: flex; flex-direction: column; height: 100%; }
.mapwrap { flex: 1; text-align: center; display: flex; align-items: center; justify-content: center; }
.mapwrap img { max-width: 100%; max-height: 268mm; object-fit: contain; }
tr.grp td { background: #D9E2F3; color: #1F3864; font-weight: 700; font-size: 8pt; text-align: left;
  padding: 3px 6px; border: 0.5px solid #9aa8bd; }
table.legend { font-size: 6.6pt; margin-top: 5px; }
table.legend th { background: #1F3864; color: #fff; padding: 2px; border: 0.5px solid #9aa8bd; }
table.legend td { border: 0.4px solid #c8c8c8; padding: 1.5px 3px; }
table.legend td.num { width: 4%; text-align: center; font-weight: 700; }
table.legend td.addr { color: #444; }
.st { text-align: center; }
.st-b { background: #C6EFCE; } .st-c { background: #FFE0B3; } .st-p { background: #E4D5F5; }
'''
HTML.write_text(f'<!doctype html><html lang="ru"><head><meta charset="utf-8"><style>{css}</style></head><body>{"".join(parts)}</body></html>', encoding='utf-8')

js = f'''
const {{ chromium }} = require('/opt/node22/lib/node_modules/playwright');
(async () => {{
  const b = await chromium.launch({{ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome', headless: true, args: ['--no-sandbox'] }});
  const p = await (await b.newContext()).newPage();
  await p.goto('file://{HTML}', {{ waitUntil: 'networkidle', timeout: 180000 }});
  await p.pdf({{ path: '{OUT}', format: 'A3', landscape: true, printBackground: true, preferCSSPageSize: true }});
  await b.close();
}})();
'''
jsf = HTML.with_suffix('.js'); jsf.write_text(js)
subprocess.run(['node', str(jsf)], check=True)
print(f'-> {OUT} ({OUT.stat().st_size // 1024} КБ)')
