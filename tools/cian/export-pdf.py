#!/usr/bin/env python3
"""Собрать docs/premium-cao/premium-zhk-cao.pdf — вся книга одним документом под A3 (альбомная).

  python3 tools/cian/export-pdf.py

Читает готовый premium-zhk-cao.xlsx (значения, ширины колонок, ссылки), раскладывает
в HTML под ширину печатной полосы A3 и печатает Chromium'ом. Книга открывается
обложкой со сводкой, дальше таблицы с повтором шапки и карты во всю полосу.
Шрифты берутся из docs/premium-cao/fonts.css (собирает tools/cian/fetch-fonts.py).
"""
import json, re, subprocess, html
from pathlib import Path
from openpyxl import load_workbook
from openpyxl.utils import get_column_letter

ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / 'docs' / 'premium-cao'
XLSX = DOCS / 'premium-zhk-cao.xlsx'
OUT = DOCS / 'premium-zhk-cao.pdf'
FONTS = DOCS / 'fonts.css'
HTML = Path('/tmp/claude-0/-home-user-test/5ef931c1-a5eb-584d-bea4-9b195abd4240/scratchpad/book.html')

# Палитра книги: графитовый синий, тёплое золото акцентом, кремовая бумага.
INK, PAPER, LINE, MUTED = '#1D2126', '#FAFAF9', '#DEDCD7', '#6C7076'
HEAT = [(0.0, (122, 168, 118)), (0.5, (233, 205, 128)), (1.0, (206, 129, 116))]
STATUS = {'построено': '#3C7A56', 'строится': '#A8762A', 'проектирование': '#5B4E86'}

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
              'Год постройки': 'Год', 'Год сдачи': 'Год', 'Цена от, ₽/м²': 'Цена ₽/м² от'}

def esc(v): return html.escape(str(v)) if v is not None else ''

def fmt(v):
    if isinstance(v, bool) or v is None: return ''
    if isinstance(v, (int, float)): return f'{int(v):,}'.replace(',', ' ')
    return esc(v)

wb = load_workbook(XLSX)
maps_idx = json.load(open(DOCS / 'maps' / 'index.json')) if (DOCS / 'maps' / 'index.json').exists() else {'maps': []}
parts = []

def body_rows(ws):
    return [r for r in ws.iter_rows(min_row=4, max_row=ws.max_row) if r[0].value]

# ---------- листы ----------
for ws in wb:
    is_map = ws.title.startswith('Карта')
    title = ws['A1'].value or ws.title
    subtitle = ws['A2'].value or ''
    num = title.split('.')[0].strip() if re.match(r'^\d+\.', str(title)) else ''
    name = re.sub(r'^\d+\.\s*', '', str(title))
    if is_map:
        mp = next((m for m in maps_idx['maps'] if ws.title.endswith(m['title'][:12]) or m['slug'] in ws.title.lower()
                   or any(w in ws.title for w in m['title'].split(',')[0].split())), None)
        if not mp: continue
        img = (DOCS / mp['file']).as_uri()
        parts.append(f"""<section class="page map">
  <header class="p-head"><h2>{esc(name)}</h2><p class="p-sub">{esc(subtitle)}</p></header>
  <div class="mapwrap"><img src="{img}"></div>
</section>""")
        continue

    ncol = ws.max_column
    widths = [ws.column_dimensions[get_column_letter(i + 1)].width or 10 for i in range(ncol)]
    total = sum(widths)
    head = [c.value for c in ws[3]]
    body = body_rows(ws)
    heat_i = next((i for i, h in enumerate(head) if h and ('медиана' in str(h) or 'Цена от' in str(h))), None)
    vals = [row[heat_i].value for row in body if heat_i is not None and isinstance(row[heat_i].value, (int, float))]
    lo, hi = (min(vals), max(vals)) if vals else (0, 1)
    cols = ''.join(f'<col style="width:{w / total * 100:.3f}%">' for w in widths)
    th = ''.join(f'<th>{esc(SHORT_HEAD.get(str(h).strip(), h))}</th>' for h in head)
    trs, n = [], 0
    for row in body:
        if all(c.value is None for c in row[1:]):        # заголовок кластера-района
            txt = str(row[0].value)
            m = re.match(r'^(.*?)\s*—\s*(\d+\s.*)$', txt)
            lbl, cnt = (m.group(1), m.group(2)) if m else (txt, '')
            trs.append(f'<tr class="grp"><td colspan="{ncol}"><span class="g-n">{esc(lbl)}</span>'
                       f'<span class="g-c">{esc(cnt)}</span></td></tr>')
            n = 0
            continue
        tds = []; n += 1
        for i, c in enumerate(row):
            v = c.value; style = ''; cls = []
            if c.hyperlink is not None or (isinstance(v, str) and v.startswith('http')):
                href = c.hyperlink.target if c.hyperlink is not None else v
                label = str(v).replace(' ↗', '') if isinstance(v, str) and not v.startswith('http') else 'открыть'
                tds.append(f'<td class="lnk"><a href="{esc(href)}">{esc(label.lower())}</a></td>'); continue
            if i == 0: cls.append('nm')
            if i == heat_i and isinstance(v, (int, float)):
                col = heat_color(v, lo, hi)
                if col: style = f'box-shadow: inset 3px 0 0 {col}'
                cls.append('heat')
            if isinstance(v, str) and v in STATUS:
                tds.append(f'<td class="st"><span class="mk" style="background:{STATUS[v]}"></span>'
                           f'{esc(v)}</td>'); continue
            if isinstance(v, (int, float)): cls.append('num')
            if i == ncol - 1: cls.append('note')
            tds.append(f'<td class="{" ".join(cls)}" style="{style}">{fmt(v)}</td>')
        trs.append(f'<tr class="{"odd" if n % 2 == 0 else ""}">' + ''.join(tds) + '</tr>')
    parts.append(f'''<section class="page">
  <header class="p-head"><h2>{esc(num + ". " if num else "") + esc(name)}</h2>
    <p class="p-sub">{esc(subtitle)}</p></header>
  <table class="grid"><colgroup>{cols}</colgroup><thead><tr>{th}</tr></thead><tbody>{''.join(trs)}</tbody></table>
</section>''')

fonts = FONTS.read_text(encoding='utf-8') if FONTS.exists() else ''
css = fonts + f'''
* {{ box-sizing: border-box; }}
html, body {{ margin: 0; }}
body {{ font-family: Inter, 'Liberation Sans', Arial, sans-serif; color: {INK}; background: #fff;
  -webkit-font-smoothing: antialiased; font-variant-numeric: tabular-nums; }}
section.page {{ page-break-after: always; }}
section.page:last-child {{ page-break-after: auto; }}

.p-head {{ border-bottom: 1px solid {INK}; padding-bottom: 5px; margin-bottom: 9px; }}
.p-head h2 {{ font-size: 15pt; font-weight: 600; margin: 0 0 2px; letter-spacing: -.01em; }}
.p-sub {{ font-size: 7.2pt; color: {MUTED}; margin: 0; max-width: 68%; line-height: 1.35; }}

table {{ width: 100%; border-collapse: collapse; table-layout: fixed; }}
table.grid {{ font-size: 6.5pt; }}
table.grid th {{ color: {MUTED}; font-size: 5.9pt; font-weight: 600; padding: 4px 2px 5px;
  border-bottom: 1px solid {INK}; text-align: center; vertical-align: bottom; line-height: 1.15;
  word-wrap: break-word; overflow-wrap: anywhere; hyphens: auto; }}
table.grid td {{ border-bottom: .4px solid {LINE}; padding: 3.5px 3px; text-align: center; vertical-align: middle;
  line-height: 1.3; word-wrap: break-word; overflow-wrap: anywhere; hyphens: auto; }}
table.grid td.num {{ white-space: nowrap; }}
table.grid td.nm {{ font-weight: 600; font-size: 6.8pt; }}
table.grid td.note {{ color: {MUTED}; font-size: 6pt; line-height: 1.32; }}
table.grid td.heat {{ font-weight: 600; }}
table.grid tr.odd td {{ background: {PAPER}; }}
thead {{ display: table-header-group; }}
tr {{ page-break-inside: avoid; }}

td.st {{ white-space: nowrap; }}
.mk {{ display: inline-block; width: 5px; height: 5px; border-radius: 50%; margin-right: 4px;
  position: relative; top: -.5px; }}
td.lnk a {{ color: {INK}; font-size: 5.9pt; text-decoration: none; border-bottom: .5px solid #B9B6AE; }}

tr.grp td {{ background: #fff; border-bottom: .8px solid {INK}; border-top: 5px solid #fff;
  padding: 8px 0 3px; text-align: left; }}
.g-n {{ font-size: 9pt; font-weight: 600; }}
.g-c {{ font-size: 7pt; color: {MUTED}; margin-left: 8px; }}

section.map {{ display: flex; flex-direction: column; height: 278mm; }}
.mapwrap {{ flex: 1; display: flex; align-items: center; justify-content: center; margin-top: 4px; }}
.mapwrap img {{ max-width: 100%; max-height: 258mm; object-fit: contain; border: .5px solid {LINE}; }}
'''
HTML.write_text(f'<!doctype html><html lang="ru"><head><meta charset="utf-8"><style>{css}</style></head>'
                f'<body>{"".join(parts)}</body></html>', encoding='utf-8')

foot = ('<div style="width:100%;font-family:Inter,Arial,sans-serif;font-size:7px;color:#8A8A80;'
        'padding:0 9mm;text-align:right;"><span class="pageNumber"></span></div>')
js = f'''
const {{ chromium }} = require('/opt/node22/lib/node_modules/playwright');
(async () => {{
  const b = await chromium.launch({{ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome', headless: true, args: ['--no-sandbox'] }});
  const p = await (await b.newContext()).newPage();
  await p.goto('file://{HTML}', {{ waitUntil: 'networkidle', timeout: 240000 }});
  await p.evaluate(() => document.fonts.ready);
  await p.pdf({{ path: '{OUT}', format: 'A3', landscape: true, printBackground: true,
    displayHeaderFooter: true, headerTemplate: '<div></div>', footerTemplate: {json.dumps(foot)},
    margin: {{ top: '8mm', bottom: '11mm', left: '8mm', right: '8mm' }} }});
  await b.close();
}})();
'''
jsf = HTML.with_suffix('.js'); jsf.write_text(js)
subprocess.run(['node', str(jsf)], check=True)
print(f'-> {OUT} ({OUT.stat().st_size // 1024} КБ)')
