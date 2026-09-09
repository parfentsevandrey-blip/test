#!/usr/bin/env python3
"""Полоса MarketBeat: один сектор — одна альбомная страница 432×279 мм.

Формат заимствован у квартальных MarketBeat Cushman & Wakefield и не
изобретается заново: шапка, две таблицы показателей со стрелками «год к году» и
«прогноз на 12 месяцев», два повествовательных блока — инвестиционный рынок и
рынок пользователя, — график и подвал с источниками.

Отличие от оригинала одно: содержание блоков не квартальное, а недельное, и
набор сюжетов в них выбирает select.py, а не редактор вручную.
"""
import html
import json
import os
import sys

import pyphen

HERE = os.path.dirname(os.path.abspath(__file__))

# страница как в оригинале: 1224×792 pt
PAGE_W_MM, PAGE_H_MM = 431.8, 279.4
PAD_MM = 13
RAIL_MM = 88          # левая колонка с показателями
GAP_MM = 9
MAIN_MM = PAGE_W_MM - 2 * PAD_MM - RAIL_MM - GAP_MM
COL_MM = (MAIN_MM - 9) / 2

MM = 96 / 25.4
# высота, доступная сюжетам в двух колонках основного поля
MAIN_H_PX = 430          # высота колонки основного поля
BODY_BUDGET_PX = 2 * MAIN_H_PX

INK, INK2, GRAY = '#0B2545', '#274C77', '#5A6B7D'
HAIR, PAPER, PALE = '#D5D9DE', '#FFFFFF', '#EEF1F4'
ACCENT, UP, DOWN = '#12796F', '#2E7D32', '#B3261E'

_dic = pyphen.Pyphen(lang='ru_RU')


def esc(t):
    return html.escape(str(t), quote=False)


def hy(t):
    """Мягкие переносы: без них узкая колонка на кириллице рвётся дырами."""
    out = []
    for w in str(t).split(' '):
        out.append(_dic.inserted(w, '­') if len(w) > 7 and w.isalpha() else w)
    return esc(' '.join(out)).replace('­', '&shy;')


def arrow(v):
    """Стрелка направления: вверх, вниз или вбок."""
    if v is None:
        return f'<span style="color:{GRAY}">—</span>'
    if v > 0:
        return f'<span style="color:{UP}">&#9650;</span>'
    if v < 0:
        return f'<span style="color:{DOWN}">&#9660;</span>'
    return f'<span style="color:{GRAY}">&#9654;</span>'


def indicators(title, rows, source):
    """Таблица показателей: значение, подпись, стрелка г/г, стрелка прогноза."""
    head = (f'<div class="ind-head"><div>{esc(title)}</div>'
            f'<div class="ind-col">г/г</div><div class="ind-col">12 мес.</div></div>')
    body = ''.join(
        f'<div class="ind-row"><div><div class="ind-val">{esc(r["value"])}</div>'
        f'<div class="ind-lab">{esc(r["label"])}</div></div>'
        f'<div class="ind-col">{arrow(r.get("yoy"))}</div>'
        f'<div class="ind-col">{arrow(r.get("fc"))}</div></div>'
        for r in rows)
    return (f'<div class="ind">{head}{body}'
            f'<div class="ind-src">Источник: {esc(source)}</div></div>')


def chart(spec):
    """Столбчатый график — как в оригинале, из реальных чисел выпуска.

    Рисуется вручную, потому что тянуть библиотеку ради восьми прямоугольников
    значит добавить зависимость, которая ничего не упрощает.
    """
    if not spec:
        return ''
    vals = [p['v'] for p in spec['points']]
    lo = min(0, min(vals))
    hi = max(vals) or 1
    w, h = 300, 118
    pad_b, pad_l = 18, 26
    span = (hi - lo) or 1
    n = len(spec['points'])
    bw = (w - pad_l - 6) / n * 0.62
    step = (w - pad_l - 6) / n
    zero = h - pad_b - (0 - lo) / span * (h - pad_b - 8)
    bars = []
    for i, p in enumerate(spec['points']):
        x = pad_l + i * step + (step - bw) / 2
        y = h - pad_b - (p['v'] - lo) / span * (h - pad_b - 8)
        top, height = min(y, zero), abs(zero - y)
        col = ACCENT if not p.get('dim') else '#9FC0BC'
        bars.append(f'<rect x="{x:.1f}" y="{top:.1f}" width="{bw:.1f}" '
                    f'height="{max(height,1):.1f}" fill="{col}"/>')
        bars.append(f'<text x="{x + bw/2:.1f}" y="{h - 6:.1f}" text-anchor="middle" '
                    f'font-size="7.5" fill="{GRAY}">{esc(p["k"])}</text>')
        bars.append(f'<text x="{x + bw/2:.1f}" y="{top - 3:.1f}" text-anchor="middle" '
                    f'font-size="7.5" font-weight="600" fill="{INK}">{esc(p["t"])}</text>')
    axis = (f'<line x1="{pad_l-4}" y1="{zero:.1f}" x2="{w-2}" y2="{zero:.1f}" '
            f'stroke="{HAIR}" stroke-width="1"/>')
    return (f'<div class="chart"><div class="chart-t">{esc(spec["title"])}</div>'
            f'<svg viewBox="0 0 {w} {h}" width="100%">{axis}{"".join(bars)}</svg>'
            f'<div class="chart-s">{esc(spec.get("source",""))}</div></div>')


def stats_table(spec):
    """Таблица рыночных показателей — нижний блок левой колонки оригинала."""
    if not spec:
        return ''
    rows = ''.join(
        f'<div class="st-row"><div>{esc(r[0])}</div><div class="st-v">{esc(r[1])}</div>'
        f'<div class="st-v">{esc(r[2])}</div></div>' for r in spec['rows'])
    return (f'<div class="st"><div class="chart-t">{esc(spec["title"])}</div>'
            f'<div class="st-row st-h"><div></div><div class="st-v">{esc(spec["c1"])}</div>'
            f'<div class="st-v">{esc(spec["c2"])}</div></div>{rows}'
            f'<div class="chart-s">{esc(spec.get("source",""))}</div></div>')


def item_html(item):
    """Сюжет в тексте полосы: ведущая фраза жирным, дальше — сам факт.

    Тот же HTML идёт и в измеритель, и на полосу, иначе измеренная высота
    перестанет соответствовать настоящей.
    """
    src = f' <span class="src">({esc(item["outlet"])}, {esc(item["date"][8:10])}.{esc(item["date"][5:7])})</span>'
    return (f'<p class="item"><b>{hy(item["lead"])}</b> {hy(item["text"])}{src}</p>')


def block(title, subtitle, items):
    return (f'<div class="bl"><div class="bl-h">{esc(title)}: '
            f'<span class="bl-s">{esc(subtitle)}</span></div>'
            + ''.join(item_html(i) for i in items) + '</div>')


CSS = f"""
@page {{ size: {PAGE_W_MM}mm {PAGE_H_MM}mm; margin: 0 }}
* {{ box-sizing: border-box }}
body {{ margin:0; background:{PALE}; font-family:'Helvetica Neue',Arial,sans-serif;
        color:{INK}; -webkit-font-smoothing:antialiased }}
.page {{ position:relative; width:{PAGE_W_MM}mm; height:{PAGE_H_MM}mm; background:{PAPER};
         padding:{PAD_MM}mm; overflow:hidden; page-break-after:always }}
.top {{ display:flex; align-items:flex-end; justify-content:space-between;
        border-bottom:2.6px solid {INK}; padding-bottom:3mm }}
.mb {{ font-size:30px; font-weight:800; letter-spacing:.14em; line-height:1 }}
.sec {{ font-size:17px; font-weight:600; letter-spacing:.06em; margin-top:2mm;
        color:{ACCENT}; text-transform:uppercase }}
.cty {{ font-size:12px; letter-spacing:.24em; color:{GRAY}; text-transform:uppercase }}
.week {{ text-align:right; font-size:11.5px; color:{GRAY}; line-height:1.5 }}
.grid {{ display:grid; grid-template-columns:{RAIL_MM}mm {MAIN_MM}mm;
         column-gap:{GAP_MM}mm; margin-top:4.5mm }}
.ind {{ border-top:1.4px solid {INK}; padding-top:2mm; margin-bottom:4mm }}
.ind-head {{ display:grid; grid-template-columns:1fr 28px 40px; font-size:8.5px;
             font-weight:700; letter-spacing:.09em; text-transform:uppercase;
             color:{GRAY}; padding-bottom:1.6mm }}
.ind-row {{ display:grid; grid-template-columns:1fr 28px 40px; align-items:center;
            border-top:1px solid {HAIR}; padding:1.5mm 0 }}
.ind-val {{ font-size:19px; font-weight:700; line-height:1.05 }}
.ind-lab {{ font-size:9px; color:{GRAY}; line-height:1.25; margin-top:.6mm }}
.ind-col {{ text-align:center; font-size:12px }}
.ind-src {{ font-size:8px; color:{GRAY}; margin-top:1.6mm; font-style:italic }}
.chart {{ border-top:1.4px solid {INK}; padding-top:2mm; margin-bottom:3mm }}
.chart-t {{ font-size:8.5px; font-weight:700; letter-spacing:.07em;
            text-transform:uppercase; margin-bottom:1mm }}
.st {{ border-top:1.4px solid {INK}; padding-top:2mm; margin-bottom:3mm }}
.st-row {{ display:grid; grid-template-columns:1fr 52px 52px; font-size:9px;
           border-top:1px solid {HAIR}; padding:1.1mm 0; line-height:1.25 }}
.st-h {{ border-top:none; font-size:8px; font-weight:700; letter-spacing:.06em;
         text-transform:uppercase; color:{GRAY} }}
.st-v {{ text-align:right; font-weight:600; font-variant-numeric:tabular-nums }}
.chart-s {{ font-size:7.5px; color:{GRAY}; font-style:italic }}
.main {{ columns:2; column-gap:9mm; column-fill:balance; height:{MAIN_H_PX}px }}
.bl {{ break-inside:avoid-column; margin-bottom:3.4mm }}
.bl-h {{ font-size:10.5px; font-weight:800; letter-spacing:.05em; text-transform:uppercase;
         color:{INK}; border-bottom:1px solid {HAIR}; padding-bottom:1.2mm;
         margin-bottom:1.8mm }}
.bl-s {{ color:{ACCENT} }}
.item {{ font-size:10.2px; line-height:1.46; margin:0 0 2.2mm; text-align:justify;
         hyphens:manual }}
.item b {{ font-weight:700 }}
.src {{ color:{GRAY}; font-size:9px; white-space:nowrap }}
.foot {{ position:absolute; left:{PAD_MM}mm; right:{PAD_MM}mm; bottom:{PAD_MM-4}mm;
         border-top:1px solid {HAIR}; padding-top:1.8mm; display:flex;
         justify-content:space-between; gap:6mm }}
.foot div {{ font-size:7.6px; color:{GRAY}; line-height:1.4 }}
.foot .r {{ text-align:right; white-space:nowrap }}
"""


def page(sec, issue):
    ind = indicators('Экономика', sec['economic'], sec['economic_source'])
    fun = indicators('Показатели рынка', sec['fundamentals'], sec['fundamentals_source'])
    ch = ''.join(chart(c) for c in sec.get('charts', []))
    st = stats_table(sec.get('stats'))
    blocks = ''.join(
        block(b['title'], b['subtitle'], b['items']) for b in sec['blocks'])
    return f"""
<div class="page">
  <div class="top">
    <div>
      <div class="mb">MARKETBEAT</div>
      <div class="sec">{esc(sec['name'])} · {esc(issue['week_label'])}</div>
      <div class="cty">Нидерланды</div>
    </div>
    <div class="week">{esc(issue['issue_line'])}<br>{esc(issue['prepared_by'])}</div>
  </div>
  <div class="grid">
    <div>{ind}{fun}{ch}{st}</div>
    <div class="main">{blocks}</div>
  </div>
  <div class="foot">
    <div>{esc(sec.get('sources_line',''))}</div>
    <div class="r">{esc(issue['imprint'])}</div>
  </div>
</div>"""


def build(issue, out):
    pages = ''.join(page(s, issue) for s in issue['sectors'])
    doc = ('<!DOCTYPE html><html lang="ru"><head><meta charset="utf-8">'
           f'<title>{esc(issue["issue_line"])}</title><style>{CSS}</style></head>'
           f'<body>{pages}</body></html>')
    open(out, 'w', encoding='utf-8').write(doc)
    print(f'{out}: {len(issue["sectors"])} полос, {len(doc)} байт')
    for s in issue['sectors']:
        n = sum(len(b['items']) for b in s['blocks'])
        print(f'  {s["name"]:<26} {n} сюжетов')


if __name__ == '__main__':
    issue = json.load(open(sys.argv[1], encoding='utf-8'))
    build(issue, sys.argv[2] if len(sys.argv) > 2 else 'build/marketbeat.html')
