#!/usr/bin/env python3
"""Полоса MarketBeat: один сектор — одна альбомная страница 432×279 мм.

Три колонки, как в оригинале Cushman & Wakefield:
  слева    — показатели экономики и рынка со стрелками «г/г» и «12 мес.»
  в центре — два графика на реальных рядах
  справа   — плотная аналитическая проза: инвестиционный рынок, рынок
             пользователя, прогноз

Первая версия делала справа ленту коротких заметок в две колонки и оставляла
половину полосы пустой. Формат держится на прозе: блок — это 200–300 слов
связного текста с цифрами внутри, а не список заголовков.
"""
import html
import json
import os
import re
import sys

import pyphen

HERE = os.path.dirname(os.path.abspath(__file__))

PAGE_W_MM, PAGE_H_MM = 431.8, 279.4          # 1224×792 pt, как в оригинале
PAD_MM = 12
RAIL_MM, CHART_MM, GAP_MM = 78, 132, 7
PROSE_MM = PAGE_W_MM - 2 * PAD_MM - RAIL_MM - CHART_MM - 2 * GAP_MM
BODY_H_PX = 812                               # высота тела между шапкой и подвалом

INK, GRAY, HAIR, PAPER, PALE = '#0B2545', '#5A6B7D', '#D5D9DE', '#FFFFFF', '#EEF1F4'
ACCENT, ACCENT2, UP, DOWN = '#12796F', '#9FC0BC', '#2E7D32', '#B3261E'

_dic = pyphen.Pyphen(lang='ru_RU')


def esc(t):
    return html.escape(str(t), quote=False)


def hy(t):
    """Мягкие переносы — без них выключка по формату рвёт кириллицу дырами.

    Разряды чисел («1 000», «24 902 кв. м») склеиваются неразрывным пробелом:
    число, разорванное по строкам, читается как два числа.
    """
    t = re.sub(r'(?<=\d) (?=\d{3}(?!\d))', ' ', str(t))
    t = t.replace('кв. м', 'кв. м').replace('млн ', 'млн ').replace('млрд ', 'млрд ')
    out = []
    for w in t.split(' '):
        core = w.strip('«»(),.;:—')
        out.append(_dic.inserted(w, '­') if len(core) > 8 and core.isalpha() else w)
    return esc(' '.join(out)).replace('­', '&shy;')


def arrow(v):
    if v is None:
        return f'<span style="color:{GRAY}">—</span>'
    if v > 0:
        return f'<span style="color:{UP}">&#9650;</span>'
    if v < 0:
        return f'<span style="color:{DOWN}">&#9660;</span>'
    return f'<span style="color:{GRAY}">&#9654;</span>'


def indicators(title, rows, source):
    head = (f'<div class="ind-head"><div>{esc(title)}</div>'
            f'<div class="c">г/г</div><div class="c">12 мес.</div></div>')
    body = ''.join(
        f'<div class="ind-row"><div><div class="ind-val">{esc(r["value"])}</div>'
        f'<div class="ind-lab">{esc(r["label"])}</div></div>'
        f'<div class="c">{arrow(r.get("yoy"))}</div><div class="c">{arrow(r.get("fc"))}</div></div>'
        for r in rows)
    return f'<div class="ind">{head}{body}<div class="src">Источник: {esc(source)}</div></div>'


def tick(v, fmt):
    """Подпись шкалы по-русски: запятая в дробях, пробел между разрядами."""
    s = fmt.format(v)
    return s.replace(',', ' ').replace('.', ',') if ',' in fmt else s.replace('.', ',')


def chart(spec):
    """График по реальному ряду: столбцы или линия, подписи значений у точек.

    Библиотека не нужна: это десяток прямоугольников и одна ломаная, а
    зависимость на этом месте только усложняет воспроизводимость.
    """
    pts = spec['points']
    vals = [p['v'] for p in pts]
    lo = min(0.0, min(vals))
    hi = max(vals) if max(vals) > 0 else 1.0
    if spec.get('kind') == 'line':
        pad = (max(vals) - min(vals)) * 0.25 or 1.0
        lo, hi = min(vals) - pad, max(vals) + pad
    span = (hi - lo) or 1.0
    W, H = 480, 265
    L, R, T, B = 34, 8, 14, 30
    n = len(pts)
    step = (W - L - R) / n
    y_of = lambda v: T + (hi - v) / span * (H - T - B)
    g = []
    # сетка по четырём уровням — шкала читается без осей
    for k in range(4):
        y = T + k * (H - T - B) / 3
        v = hi - k * span / 3
        g.append(f'<line x1="{L}" y1="{y:.1f}" x2="{W-R}" y2="{y:.1f}" stroke="{HAIR}" stroke-width=".8"/>')
        g.append(f'<text x="{L-5}" y="{y+3:.1f}" text-anchor="end" font-size="8" fill="{GRAY}">'
                 f'{esc(tick(v, spec.get("fmt", "{:.0f}")))}</text>')
    if spec.get('kind') == 'line':
        d = ' '.join(f'{L + i*step + step/2:.1f},{y_of(p["v"]):.1f}' for i, p in enumerate(pts))
        g.append(f'<polyline points="{d}" fill="none" stroke="{ACCENT}" stroke-width="2.4" '
                 f'stroke-linejoin="round"/>')
        for i, p in enumerate(pts):
            x, y = L + i * step + step / 2, y_of(p['v'])
            g.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="2.6" fill="{ACCENT}"/>')
            if p.get('t'):
                g.append(f'<text x="{x:.1f}" y="{y-7:.1f}" text-anchor="middle" font-size="8.5" '
                         f'font-weight="700" fill="{INK}">{esc(p["t"])}</text>')
    else:
        bw = step * 0.6
        y0 = y_of(0)
        for i, p in enumerate(pts):
            x = L + i * step + (step - bw) / 2
            y = y_of(p['v'])
            g.append(f'<rect x="{x:.1f}" y="{min(y, y0):.1f}" width="{bw:.1f}" '
                     f'height="{max(abs(y0 - y), 1):.1f}" fill="{ACCENT2 if p.get("dim") else ACCENT}"/>')
            g.append(f'<text x="{x+bw/2:.1f}" y="{min(y, y0)-4:.1f}" text-anchor="middle" '
                     f'font-size="8.5" font-weight="700" fill="{INK}">{esc(p.get("t", ""))}</text>')
    for i, p in enumerate(pts):
        if p.get('k'):
            g.append(f'<text x="{L + i*step + step/2:.1f}" y="{H-8}" text-anchor="middle" '
                     f'font-size="8" fill="{GRAY}">{esc(p["k"])}</text>')
    return (f'<div class="chart"><div class="h">{esc(spec["title"])}</div>'
            f'<div class="sub">{esc(spec.get("subtitle", ""))}</div>'
            f'<svg viewBox="0 0 {W} {H}" width="100%">{"".join(g)}</svg>'
            f'<div class="src">{esc(spec.get("source", ""))}</div></div>')


def stats_table(spec):
    rows = ''.join(
        f'<div class="st-row"><div>{esc(r[0])}</div><div class="v">{esc(r[1])}</div>'
        f'<div class="v">{esc(r[2])}</div></div>' for r in spec['rows'])
    return (f'<div class="st"><div class="h">{esc(spec["title"])}</div>'
            f'<div class="st-row st-h"><div></div><div class="v">{esc(spec["c1"])}</div>'
            f'<div class="v">{esc(spec["c2"])}</div></div>{rows}'
            f'<div class="src">{esc(spec.get("source", ""))}</div></div>')


def prose_block(b):
    """Блок прозы: заголовок как в оригинале, абзацы, строка источников."""
    paras = ''.join(f'<p>{hy(p)}</p>' for p in b['paras'])
    src = f'<div class="bsrc">{esc(b["sources"])}</div>' if b.get('sources') else ''
    return (f'<div class="pb" data-block="{esc(b["key"])}"><div class="pb-h">{esc(b["title"])}: '
            f'<span>{esc(b["subtitle"])}</span></div>{paras}{src}</div>')


CSS = f"""
@page {{ size: {PAGE_W_MM}mm {PAGE_H_MM}mm; margin: 0 }}
* {{ box-sizing: border-box }}
body {{ margin:0; background:{PALE}; font-family:'Helvetica Neue',Arial,sans-serif; color:{INK};
        -webkit-font-smoothing:antialiased }}
.page {{ position:relative; width:{PAGE_W_MM}mm; height:{PAGE_H_MM}mm; background:{PAPER};
         padding:{PAD_MM}mm; overflow:hidden; page-break-after:always }}
.top {{ display:flex; align-items:flex-end; justify-content:space-between;
        border-bottom:2.6px solid {INK}; padding-bottom:2.6mm; height:24mm }}
.mb {{ font-size:30px; font-weight:800; letter-spacing:.14em; line-height:1 }}
.sec {{ font-size:16px; font-weight:700; letter-spacing:.06em; margin-top:2mm; color:{ACCENT};
        text-transform:uppercase }}
.cty {{ font-size:11px; letter-spacing:.24em; color:{GRAY}; text-transform:uppercase; margin-top:.8mm }}
.week {{ text-align:right; font-size:11px; color:{GRAY}; line-height:1.5 }}
.body {{ display:grid; grid-template-columns:{RAIL_MM}mm {CHART_MM}mm {PROSE_MM}mm;
         column-gap:{GAP_MM}mm; margin-top:4mm; height:{BODY_H_PX}px }}
.h {{ font-size:8.5px; font-weight:700; letter-spacing:.09em; text-transform:uppercase; color:{INK} }}
.sub {{ font-size:8px; color:{GRAY}; margin:.4mm 0 1mm }}
.src {{ font-size:7.6px; color:{GRAY}; margin-top:1.4mm; font-style:italic }}
.c {{ text-align:center; font-size:12px }}
.ind {{ border-top:1.4px solid {INK}; padding-top:1.8mm; margin-bottom:3.2mm }}
.ind-head {{ display:grid; grid-template-columns:1fr 28px 52px; font-size:8.5px; font-weight:700;
             letter-spacing:.09em; text-transform:uppercase; color:{GRAY}; padding-bottom:1.2mm;
             white-space:nowrap }}
.ind-row {{ display:grid; grid-template-columns:1fr 28px 52px; align-items:center;
            border-top:1px solid {HAIR}; padding:1.7mm 0 }}
.ind-val {{ font-size:18px; font-weight:700; line-height:1.05 }}
.ind-lab {{ font-size:8.6px; color:{GRAY}; line-height:1.25; margin-top:.5mm }}
.st {{ border-top:1.4px solid {INK}; padding-top:1.8mm }}
.st-row {{ display:grid; grid-template-columns:1fr 50px 50px; font-size:8.6px;
           border-top:1px solid {HAIR}; padding:1.3mm 0; line-height:1.25 }}
.st-h {{ border-top:none; font-size:7.6px; font-weight:700; letter-spacing:.06em;
         text-transform:uppercase; color:{GRAY} }}
.v {{ text-align:right; font-weight:600; font-variant-numeric:tabular-nums }}
.charts {{ display:flex; flex-direction:column; justify-content:space-between; height:100% }}
.chart {{ border-top:1.4px solid {INK}; padding-top:1.8mm }}
.prose {{ height:100% }}
.pb {{ margin-bottom:3.2mm }}
.pb-h {{ font-size:10.5px; font-weight:800; letter-spacing:.05em; text-transform:uppercase;
         border-bottom:1px solid {HAIR}; padding-bottom:1.2mm; margin-bottom:1.6mm }}
.pb-h span {{ color:{ACCENT} }}
.pb p {{ font-size:11px; line-height:1.5; margin:0 0 1.9mm; text-align:justify; hyphens:manual }}
.bsrc {{ font-size:7.8px; color:{GRAY}; font-style:italic; margin-top:-.6mm }}
.foot {{ position:absolute; left:{PAD_MM}mm; right:{PAD_MM}mm; bottom:{PAD_MM-4}mm;
         border-top:1px solid {HAIR}; padding-top:1.6mm; display:flex; justify-content:space-between;
         gap:6mm; font-size:7.6px; color:{GRAY}; line-height:1.4 }}
.foot .r {{ text-align:right; white-space:nowrap }}
"""


def page(sec, issue):
    rail = (indicators('Экономика', sec['economic'], sec['economic_source'])
            + indicators('Показатели рынка', sec['fundamentals'], sec['fundamentals_source'])
            + stats_table(sec['stats']))
    charts = ''.join(chart(c) for c in sec['charts'])
    prose = ''.join(prose_block(b) for b in sec['blocks'])
    return f"""
<div class="page" data-sector="{esc(sec['key'])}">
  <div class="top">
    <div><div class="mb">MARKETBEAT</div>
      <div class="sec">{esc(sec['name'])} · {esc(issue['week_label'])}</div>
      <div class="cty">Нидерланды</div></div>
    <div class="week">{esc(issue['issue_line'])}<br>{esc(issue['prepared_by'])}</div>
  </div>
  <div class="body">
    <div class="rail">{rail}</div>
    <div class="charts">{charts}</div>
    <div class="prose">{prose}</div>
  </div>
  <div class="foot"><div>{esc(sec.get('sources_line', ''))}</div>
    <div class="r">{esc(issue['imprint'])}</div></div>
</div>"""


def build(issue, out):
    doc = ('<!DOCTYPE html><html lang="ru"><head><meta charset="utf-8">'
           f'<title>{esc(issue["issue_line"])}</title><style>{CSS}</style></head><body>'
           + ''.join(page(s, issue) for s in issue['sectors']) + '</body></html>')
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    open(out, 'w', encoding='utf-8').write(doc)
    print(f'{out}: {len(issue["sectors"])} полос')


if __name__ == '__main__':
    build(json.load(open(sys.argv[1], encoding='utf-8')),
          sys.argv[2] if len(sys.argv) > 2 else os.path.join(HERE, 'build', 'marketbeat.html'))
