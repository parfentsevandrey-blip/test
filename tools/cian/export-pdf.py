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
INK, GOLD, PAPER, LINE = '#16243F', '#A98A4B', '#FBFAF7', '#E4DFD4'
HEAT = [(0.0, (122, 168, 118)), (0.5, (233, 205, 128)), (1.0, (206, 129, 116))]
STATUS = {'построено': ('#2E6B46', '#E6F0E8'), 'строится': ('#8A5A16', '#F7EBD9'),
          'проектирование': ('#4C3B73', '#ECE6F5'), 'квартиры': ('#3A4A63', '#EDF0F5'),
          'апартаменты': ('#5A4636', '#F3EDE5'), 'квартиры + апартаменты': ('#3A4A63', '#EDF0F5')}

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

def money(v): return f'{int(v):,}'.replace(',', ' ')

def money_html(v):
    """Разряды разделяет вёрстка: в подмножестве Playfair пробел нулевой ширины."""
    return '<i class="sp"></i>'.join(f'{int(v):,}'.split(','))

wb = load_workbook(XLSX)
maps_idx = json.load(open(DOCS / 'maps' / 'index.json')) if (DOCS / 'maps' / 'index.json').exists() else {'maps': []}
parts = []
toc = []          # для обложки: лист → сколько объектов
medians = []

def body_rows(ws):
    return [r for r in ws.iter_rows(min_row=4, max_row=ws.max_row) if r[0].value]

def data_rows(ws):
    return [r for r in body_rows(ws) if not all(c.value is None for c in r[1:])]

for ws in wb:
    if ws.title.startswith(('Карта', 'Список', 'Источники')): continue
    rows = data_rows(ws)
    head = [c.value for c in ws[3]]
    hi = next((i for i, h in enumerate(head) if h and 'медиана' in str(h)), None)
    own = sorted(r[hi].value for r in rows if hi is not None and isinstance(r[hi].value, (int, float)))
    medians += own
    toc.append((ws.title, len(rows), own))
extra = [w.title for w in wb if w.title.startswith(('Карта', 'Список', 'Источники'))]

# ---------- обложка ----------
sub = wb[wb.sheetnames[0]]['A2'].value or ''
MONTHS = 'январ|феврал|март|апрел|ма[йя]|июн|июл|август|сентябр|октябр|ноябр|декабр'
period = re.search(r'((?:' + MONTHS + r')\w*\s+\d{4})', str(sub))
districts, developers = set(), set()
for _ws in wb:
    if _ws.title.startswith(('Карта', 'Список', 'Источники')): continue
    _head = [str(c.value or '') for c in _ws[3]]
    di = next((i for i, h in enumerate(_head) if h.strip() == 'Район'), None)
    vi = next((i for i, h in enumerate(_head) if h.strip() in ('Застройщик',)), None)
    for _r in data_rows(_ws):
        if di is not None and _r[di].value: districts.add(str(_r[di].value).strip())
        if vi is not None and _r[vi].value: developers.add(str(_r[vi].value).split('/')[0].strip())
kpi = [('Объектов в подборке', str(sum(n for _, n, _m in toc))),
       ('Медиана по подборке', (money_html(sorted(medians)[len(medians) // 2]) + ' ₽/м²') if medians else '—'),
       ('Районов · застройщиков', f'{len(districts)} · {len(developers)}'),
       ('Данные на', period.group(1) if period else '—')]
kpi_html = ''.join(f'<div class="kpi"><div class="kpi-l">{esc(a)}</div><div class="kpi-v">{b}</div></div>' for a, b in kpi)
def toc_line(t, n):
    num, name = (t.split('.', 1) + [''])[:2] if re.match(r'^\d+\.', t) else ('·', t)
    return (f'<li><span class="t-n">{esc(num)}</span><span class="t-t">{esc(name.strip())}</span>'
            f'<span class="t-d"></span><span class="t-c">{n}</span></li>')
toc_html = (''.join(toc_line(t, n) for t, n, _m in toc)
            + ''.join(f'<li class="ap"><span class="t-n">·</span><span class="t-t">{esc(t)}</span>'
                      f'<span class="t-d"></span><span class="t-c">прил.</span></li>' for t in extra))

# три карточки разделов: объём, медиана и разброс внутри каждого
DOT = {'1': '#2E6B46', '2': '#C08A2E', '3': '#6A56A0'}
SECTION = {'1': 'Построено', '2': 'Строится', '3': 'Проектирование'}
cards = ''.join(
    f'<div class="card"><div class="card-top"><span class="dot" style="background:{DOT.get(t[0], GOLD)}"></span>'
    f'<span class="card-t">{esc(SECTION.get(t[0], t.split(". ")[-1]))}</span></div>'
    f'<div class="card-n">{n}</div>'
    f'<div class="card-s">{"медиана " + money(m[len(m) // 2]) + " ₽/м²" if m else "цены не раскрыты"}</div>'
    f'<div class="card-r">{money(m[0]) + " – " + money(m[-1]) + " ₽/м²" if m else "&nbsp;"}</div></div>'
    for t, n, m in toc)
parts.append(f'''<section class="page cover">
  <div class="c-top">
    <div class="eyebrow">Аналитика рынка · Москва, ЦАО</div>
    <h1 class="c-title">Премиальное<br>жильё в центре</h1>
    <div class="c-rule"></div>
    <p class="c-lead">Садовое кольцо · Хамовники · Пресня и Сити · Белорусская</p>
    <p class="c-note">Построенное со сроком сдачи 2022 года и позже, строящееся и проектируемое — в одном документе.
       Цены и состав корпусов — по карточкам ЖК и активным предложениям на продажу; площадки без стройки — со ссылками
       на публикации.</p>
  </div>
  <div class="c-cards">{cards}</div>
  <div class="c-kpis">{kpi_html}</div>
  <div class="c-bottom">
    <div class="c-toc"><div class="c-toc-h">Содержание</div><ul>{toc_html}</ul></div>
  </div>
</section>''')

# ---------- листы ----------
for ws in wb:
    is_map = ws.title.startswith('Карта')
    title = ws['A1'].value or ws.title
    subtitle = ws['A2'].value or ''
    num = title.split('.')[0].strip() if re.match(r'^\d+\.', str(title)) else ''
    name = re.sub(r'^\d+\.\s*', '', str(title))
    name = re.sub(r'^Карта:\s*', '', name)     # слово «Карта» уже стоит надзаголовком
    if is_map:
        mp = next((m for m in maps_idx['maps'] if ws.title.endswith(m['title'][:12]) or m['slug'] in ws.title.lower()
                   or any(w in ws.title for w in m['title'].split(',')[0].split())), None)
        if not mp: continue
        img = (DOCS / mp['file']).as_uri()
        parts.append(f"""<section class="page map">
  <header class="p-head"><div class="p-eyebrow">Карта</div><h2>{esc(name)}</h2>
    <p class="p-sub">{esc(subtitle)}</p></header>
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
                tds.append(f'<td class="lnk"><a href="{esc(href)}" title="Открыть">↗</a></td>'); continue
            if i == 0: cls.append('nm')
            if i == heat_i and isinstance(v, (int, float)):
                col = heat_color(v, lo, hi)
                if col: style = f'background:{col}1f;color:{col};border-left:2px solid {col}'
                cls.append('heat')
            if isinstance(v, str) and v in STATUS:
                fg, bg = STATUS[v]
                v = v.replace('квартиры + апартаменты', 'кв. + апарт.')
                tds.append(f'<td><span class="pill" style="color:{fg};background:{bg}">{esc(v)}</span></td>'); continue
            if isinstance(v, (int, float)): cls.append('num')
            if i == ncol - 1: cls.append('note')
            tds.append(f'<td class="{" ".join(cls)}" style="{style}">{fmt(v)}</td>')
        trs.append(f'<tr class="{"odd" if n % 2 == 0 else ""}">' + ''.join(tds) + '</tr>')
    parts.append(f'''<section class="page">
  <header class="p-head"><div class="p-eyebrow">{esc("Раздел " + num) if num else "Приложение"}</div>
    <h2>{esc(name)}</h2><p class="p-sub">{esc(subtitle)}</p></header>
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

/* ---- шапка страницы ---- */
.p-head {{ border-bottom: 1.2px solid {INK}; padding-bottom: 5px; margin-bottom: 9px; position: relative; }}
.p-head::after {{ content: ''; position: absolute; left: 0; bottom: -3.2px; width: 78px; height: 2px; background: {GOLD}; }}
.p-eyebrow {{ font-size: 6.2pt; letter-spacing: .18em; text-transform: uppercase; color: {GOLD}; font-weight: 600; }}
.p-head h2 {{ font-family: 'Playfair Display', 'Liberation Serif', Georgia, serif; font-size: 19pt; font-weight: 600;
  margin: 1px 0 2px; letter-spacing: -.01em; }}
.p-sub {{ font-size: 7pt; color: #6B6B63; margin: 0; max-width: 62%; line-height: 1.35; }}

/* ---- таблица ---- */
table {{ width: 100%; border-collapse: collapse; table-layout: fixed; }}
table.grid {{ font-size: 6.5pt; }}
table.grid th {{ background: #F2EFE8; color: {INK}; font-size: 5.6pt; font-weight: 600; padding: 5px 2px;
  border-bottom: 1.2px solid {INK}; border-top: .5px solid {LINE}; text-align: center; vertical-align: middle;
  line-height: 1.15; letter-spacing: .05em; text-transform: uppercase;
  word-wrap: break-word; overflow-wrap: anywhere; hyphens: auto; }}
table.grid td {{ border-bottom: .4px solid {LINE}; padding: 3.5px 3px; text-align: center; vertical-align: middle;
  line-height: 1.3; word-wrap: break-word; overflow-wrap: anywhere; hyphens: auto; }}
table.grid td.num {{ white-space: nowrap; }}
table.grid td.nm {{ font-weight: 600; font-size: 6.8pt; letter-spacing: -.005em; }}
table.grid td.note {{ color: #5E5E57; font-size: 6pt; line-height: 1.32; }}
table.grid td.heat {{ font-weight: 700; font-size: 6.9pt; }}
table.grid tr.odd td {{ background: {PAPER}; }}
thead {{ display: table-header-group; }}
tr {{ page-break-inside: avoid; }}

.pill {{ display: inline-block; max-width: 100%; padding: 1.4px 5px; border-radius: 8px; font-size: 5.7pt;
  font-weight: 600; letter-spacing: .02em; line-height: 1.25; }}
td.lnk a {{ color: {GOLD}; font-size: 9pt; font-weight: 600; text-decoration: none; line-height: 1; }}

tr.grp td {{ background: #fff; border-bottom: .8px solid {INK}; border-top: 4px solid #fff;
  padding: 7px 0 3px; text-align: left; }}
.g-n {{ font-family: 'Playfair Display', 'Liberation Serif', Georgia, serif; font-size: 10pt; font-weight: 600; }}
.g-c {{ font-size: 6pt; letter-spacing: .12em; text-transform: uppercase; color: {GOLD}; margin-left: 9px;
  font-weight: 600; position: relative; top: -1px; }}

/* ---- карты ---- */
section.map {{ display: flex; flex-direction: column; height: 278mm; }}
.mapwrap {{ flex: 1; display: flex; align-items: center; justify-content: center; margin-top: 4px; }}
.mapwrap img {{ max-width: 100%; max-height: 258mm; object-fit: contain; border: .8px solid {LINE}; }}

/* ---- обложка ---- */
section.cover {{ height: 278mm; display: flex; flex-direction: column; justify-content: space-between;
  background: {PAPER}; padding: 12mm 14mm 10mm; }}
.eyebrow {{ font-size: 8pt; letter-spacing: .3em; text-transform: uppercase; color: {GOLD}; font-weight: 600; }}
.c-title {{ font-family: 'Playfair Display', 'Liberation Serif', Georgia, serif; font-size: 54pt; font-weight: 700;
  line-height: 1.02; margin: 10px 0 0; letter-spacing: -.02em; }}
.c-rule {{ width: 120px; height: 3px; background: {GOLD}; margin: 16px 0 14px; }}
.c-lead {{ font-size: 12pt; line-height: 1.5; color: {INK}; margin: 0 0 9px; font-weight: 500; }}
.c-note {{ font-size: 9pt; line-height: 1.6; color: #6E6E66; margin: 0; max-width: 52%; }}
.sp {{ display: inline-block; width: .24em; }}
.c-toc li.ap .t-t {{ color: #7A7A72; }}
.c-toc li.ap .t-c {{ font-size: 6.6pt; letter-spacing: .1em; text-transform: uppercase; color: #9A9A90; font-weight: 600; }}
.c-cards {{ display: flex; gap: 8mm; }}
.card {{ flex: 1; background: #fff; border: .8px solid {LINE}; border-top: 3px solid {INK}; padding: 9px 11px 11px; }}
.card-top {{ display: flex; align-items: center; gap: 7px; }}
.dot {{ width: 8px; height: 8px; border-radius: 50%; display: inline-block; }}
.card-t {{ font-size: 7pt; letter-spacing: .14em; text-transform: uppercase; font-weight: 600; color: #5C5C55; }}
.card-n {{ font-family: 'Playfair Display', 'Liberation Serif', Georgia, serif; font-size: 30pt; font-weight: 700;
  line-height: 1.05; margin: 5px 0 2px; }}
.card-s, .card-r {{ white-space: nowrap; }}
.card-s {{ font-size: 8pt; font-weight: 600; }}
.card-r {{ font-size: 7.4pt; color: #85857C; margin-top: 1px; }}
.c-kpis {{ display: flex; gap: 10mm; }}
.kpi {{ flex: 1; border-top: 1.5px solid {INK}; padding-top: 7px; }}
.kpi-l {{ font-size: 6.6pt; letter-spacing: .14em; text-transform: uppercase; color: #7A7A72; font-weight: 600; }}
.kpi-v {{ font-family: 'Playfair Display', 'Liberation Serif', Georgia, serif; font-size: 17pt; font-weight: 600;
  margin-top: 3px; white-space: nowrap; }}
.c-toc-h {{ font-size: 7pt; letter-spacing: .2em; text-transform: uppercase; color: {GOLD}; font-weight: 600;
  margin-bottom: 6px; }}
.c-toc ul {{ list-style: none; margin: 0; padding: 0; columns: 2; column-gap: 14mm; }}
.c-toc li {{ display: flex; align-items: baseline; gap: 6px; font-size: 9pt; padding: 3.5px 0;
  border-bottom: .5px solid {LINE}; break-inside: avoid; }}
.t-n {{ color: {GOLD}; font-weight: 700; width: 12px; }}
.t-d {{ flex: 1; border-bottom: .5px dotted #C9C4B8; position: relative; top: -2px; }}
.t-c {{ font-weight: 600; font-size: 8.5pt; }}
'''
HTML.write_text(f'<!doctype html><html lang="ru"><head><meta charset="utf-8"><style>{css}</style></head>'
                f'<body>{"".join(parts)}</body></html>', encoding='utf-8')

foot = (f'<div style="width:100%;font-family:Inter,Arial,sans-serif;font-size:7px;color:#8A8A80;'
        f'padding:0 9mm;display:flex;justify-content:space-between;">'
        f'<span>Премиальное жильё в центре Москвы</span>'
        f'<span class="pageNumber"></span></div>')
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
