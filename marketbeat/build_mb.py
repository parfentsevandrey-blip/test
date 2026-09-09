#!/usr/bin/env python3
"""Полоса MarketBeat: один сектор — одна альбомная страница 432×279 мм.

Сетка повторяет оригинал Cushman & Wakefield и добивает его до плотной полосы:

  шапка         название сектора, тезис недели, номер выпуска
  главное       три ключевые цифры недели с пояснением и источником
  слева         показатели экономики и рынка со стрелками «г/г» и «12 мес.»,
                два графика на реальных рядах, таблица квартальной базы
  справа        две текстовые колонки — инвестиционный рынок и рынок
                пользователя; под ними прогноз, таблица сделок недели и
                словарь терминов
  подвал        источники

Пустых зон на полосе быть не должно: каждая область получает столько
содержания, сколько вмещает, а fit.py считает недобор браком наравне с
переполнением. Шрифты — PT Serif для текста и PT Sans для цифр и заголовков:
оба с полной кириллицей, лежат в fonts/ (лицензия OFL).
"""
import html
import json
import os
import re
import sys

import pyphen

HERE = os.path.dirname(os.path.abspath(__file__))
FONTS = os.path.join(HERE, 'fonts')

PAGE_W_MM, PAGE_H_MM = 431.8, 279.4          # 1224×792 pt, как в оригинале
PAD_MM = 11
LEFT_MM, GAP_MM = 146, 8
RIGHT_MM = PAGE_W_MM - 2 * PAD_MM - LEFT_MM - GAP_MM
HEAD_MM, KEY_MM, FOOT_MM = 19, 17, 7
BODY_MM = PAGE_H_MM - 2 * PAD_MM - HEAD_MM - KEY_MM - FOOT_MM - 3 * 2.5
BOTTOM_MM = 66                                 # прогноз · сделки · термины

INK, GRAY, HAIR, PAPER, PALE = '#151A21', '#5D6670', '#CFD4DA', '#FFFFFF', '#F2F0EB'
UP, DOWN, FLAT = '#1F7A3A', '#B3261E', '#7A838C'
ACCENTS = {'living': '#8A2D2D', 'retail': '#B4741A', 'industrial': '#2C5C8A',
           'offices': '#2E6B55'}

_dic = pyphen.Pyphen(lang='ru_RU')


def esc(t):
    return html.escape(str(t), quote=False)


def hy(t):
    """Мягкие переносы — без них выключка по формату рвёт кириллицу дырами.

    Разряды чисел («1 000», «24 902 кв. м») склеиваются неразрывным пробелом:
    число, разорванное по строкам, читается как два числа.
    """
    t = re.sub(r'(?<=\d) (?=\d{3}(?!\d))', ' ', str(t))
    t = t.replace('кв. м', 'кв. м').replace('млн ', 'млн ').replace('млрд ', 'млрд ')
    out = []
    for w in t.split(' '):
        core = w.strip('«»(),.;:—')
        out.append(_dic.inserted(w, '­') if len(core) > 8 and core.isalpha() else w)
    return esc(' '.join(out)).replace('­', '&shy;')


def arrow(v):
    if v is None:
        return f'<span class="ar" style="color:{FLAT}">—</span>'
    if v > 0:
        return f'<span class="ar" style="color:{UP}">&#9650;</span>'
    if v < 0:
        return f'<span class="ar" style="color:{DOWN}">&#9660;</span>'
    return f'<span class="ar" style="color:{FLAT}">&#9654;</span>'


def tick(v, fmt):
    """Подпись шкалы по-русски: запятая в дробях, пробел между разрядами."""
    if abs(v) < 1e-9:
        v = 0.0                                 # иначе на шкале появляется «−0,0»
    s = fmt.format(v)
    return s.replace(',', ' ').replace('.', ',') if ',' in fmt else s.replace('.', ',')


def indicators(title, rows, source):
    body = ''.join(
        f'<div class="ind-row"><div><div class="ind-val">{esc(r["value"])}</div>'
        f'<div class="ind-lab">{esc(r["label"])}</div></div>'
        f'<div class="c">{arrow(r.get("yoy"))}</div><div class="c">{arrow(r.get("fc"))}</div></div>'
        for r in rows)
    return (f'<div class="ind"><div class="ind-head"><div>{esc(title)}</div>'
            f'<div class="c">г/г</div><div class="c">12 мес.</div></div>{body}'
            f'<div class="src">{esc(source)}</div></div>')


def chart(spec, accent):
    """График по реальному ряду: столбцы или линия с заливкой, подписи у точек.

    Библиотека не нужна: это десяток прямоугольников и одна ломаная, а
    зависимость на этом месте только усложняет воспроизводимость.
    """
    pts = spec['points']
    vals = [p['v'] for p in pts]
    line = spec.get('kind') == 'line'
    lo = min(0.0, min(vals))
    hi = max(vals) if max(vals) > 0 else 1.0
    if line:
        pad = (max(vals) - min(vals)) * 0.3 or 1.0
        lo, hi = min(vals) - pad, max(vals) + pad
    span = (hi - lo) or 1.0
    W, H = 520, 168
    L, R, T, B = 36, 10, 15, 24
    n = len(pts)
    step = (W - L - R) / n
    y_of = lambda v: T + (hi - v) / span * (H - T - B)
    g = []
    for k in range(4):                       # четыре уровня сетки
        y = T + k * (H - T - B) / 3
        v = hi - k * span / 3
        g.append(f'<line x1="{L}" y1="{y:.1f}" x2="{W-R}" y2="{y:.1f}" stroke="{HAIR}" stroke-width=".8"/>')
        g.append(f'<text x="{L-6}" y="{y+3:.1f}" text-anchor="end" font-size="8.5" fill="{GRAY}">'
                 f'{esc(tick(v, spec.get("fmt", "{:.0f}")))}</text>')
    if line:
        xs = [L + i * step + step / 2 for i in range(n)]
        d = ' '.join(f'{x:.1f},{y_of(p["v"]):.1f}' for x, p in zip(xs, pts))
        area = f'{xs[0]:.1f},{H-B} {d} {xs[-1]:.1f},{H-B}'
        g.append(f'<polygon points="{area}" fill="{accent}" fill-opacity=".10"/>')
        g.append(f'<polyline points="{d}" fill="none" stroke="{accent}" stroke-width="2.6" '
                 f'stroke-linejoin="round"/>')
        for i, (x, p) in enumerate(zip(xs, pts)):
            y = y_of(p['v'])
            last = i == n - 1
            g.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{4.2 if last else 2.4}" '
                     f'fill="{accent if last else PAPER}" stroke="{accent}" stroke-width="1.6"/>')
            if p.get('t'):
                g.append(f'<text x="{x:.1f}" y="{y-8:.1f}" text-anchor="middle" font-size="9" '
                         f'font-weight="700" fill="{INK}">{esc(p["t"])}</text>')
    else:
        bw = step * 0.62
        y0 = y_of(0)
        for i, p in enumerate(pts):
            x = L + i * step + (step - bw) / 2
            y = y_of(p['v'])
            fill = accent if not p.get('dim') else '#B9C1C9'
            g.append(f'<rect x="{x:.1f}" y="{min(y, y0):.1f}" width="{bw:.1f}" '
                     f'height="{max(abs(y0 - y), 1):.1f}" fill="{fill}"/>')
            g.append(f'<text x="{x+bw/2:.1f}" y="{min(y, y0)-4:.1f}" text-anchor="middle" '
                     f'font-size="9" font-weight="700" fill="{INK}">{esc(p.get("t", ""))}</text>')
    for i, p in enumerate(pts):
        if p.get('k'):
            g.append(f'<text x="{L + i*step + step/2:.1f}" y="{H-7}" text-anchor="middle" '
                     f'font-size="8.5" fill="{GRAY}">{esc(p["k"])}</text>')
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


def takeaways(items):
    return ''.join(
        f'<div class="key"><div class="key-num">{esc(k["num"])}</div>'
        f'<div><div class="key-txt">{hy(k["text"])}</div>'
        f'<div class="key-src">{esc(k["src"])}</div></div></div>' for k in items)


def prose_block(b):
    paras = ''.join(f'<p>{hy(p)}</p>' for p in b['paras'])
    src = f'<div class="bsrc">{esc(b["sources"])}</div>' if b.get('sources') else ''
    return (f'<div class="pb" data-block="{esc(b["key"])}"><div class="pb-h">{esc(b["title"])}'
            f'<span>{esc(b["subtitle"])}</span></div>{paras}{src}</div>')


def deals_table(spec):
    rows = ''.join(
        f'<div class="dl-row"><div class="dl-d">{esc(r[0])}</div><div>{hy(r[1])}</div>'
        f'<div class="dl-w">{hy(r[2])}</div><div class="dl-s">{esc(r[3])}</div></div>'
        for r in spec['rows'])
    note = f'<div class="src">{esc(spec["note"])}</div>' if spec.get('note') else ''
    return (f'<div class="cell deals"><div class="pb-h">{esc(spec["title"])}</div>'
            f'<div class="dl-row dl-h"><div>дата</div><div>объект</div><div>стороны</div>'
            f'<div class="dl-s">объём</div></div>{rows}{note}</div>')


def glossary(items):
    rows = ''.join(f'<div class="gl"><b>{esc(t)}</b> — {hy(x)}</div>' for t, x in items)
    return f'<div class="cell gloss"><div class="pb-h">Термины на этой полосе</div>{rows}</div>'


def css():
    ff = lambda n: 'file://' + os.path.join(FONTS, n)
    return f"""
@font-face {{ font-family:'PT Serif'; src:url('{ff("PT_Serif-Web-Regular.ttf")}'); font-weight:400 }}
@font-face {{ font-family:'PT Serif'; src:url('{ff("PT_Serif-Web-Bold.ttf")}'); font-weight:700 }}
@font-face {{ font-family:'PT Sans'; src:url('{ff("PT_Sans-Web-Regular.ttf")}'); font-weight:400 }}
@font-face {{ font-family:'PT Sans'; src:url('{ff("PT_Sans-Web-Bold.ttf")}'); font-weight:700 }}
@page {{ size: {PAGE_W_MM}mm {PAGE_H_MM}mm; margin: 0 }}
* {{ box-sizing: border-box }}
body {{ margin:0; background:{PALE}; font-family:'PT Serif',Georgia,serif; color:{INK};
        -webkit-font-smoothing:antialiased }}
.sans {{ font-family:'PT Sans',Arial,sans-serif }}
.page {{ position:relative; width:{PAGE_W_MM}mm; height:{PAGE_H_MM}mm; background:{PAPER};
         padding:{PAD_MM}mm; overflow:hidden; page-break-after:always;
         display:flex; flex-direction:column; gap:2.5mm }}
.top {{ height:{HEAD_MM}mm; display:grid; grid-template-columns:auto 1fr auto; align-items:end;
        column-gap:8mm; border-bottom:3px solid var(--ac); padding-bottom:2.2mm }}
.mb {{ font-family:'PT Sans'; font-size:24px; font-weight:700; letter-spacing:.16em; line-height:1 }}
.mb small {{ display:block; font-size:8.5px; letter-spacing:.22em; color:{GRAY}; margin-top:1.6mm;
             font-weight:400 }}
.sec {{ font-family:'PT Serif'; font-size:27px; font-weight:700; line-height:1; color:var(--ac) }}
.sec small {{ display:block; font-family:'PT Serif'; font-size:11.5px; font-weight:400; color:{INK};
              margin-top:1.8mm; letter-spacing:0 }}
.iss {{ text-align:right; font-family:'PT Sans'; font-size:9.5px; color:{GRAY}; line-height:1.5 }}
.iss b {{ color:{INK}; font-size:11px }}
.keys {{ height:{KEY_MM}mm; display:grid; grid-template-columns:repeat(3,1fr); column-gap:2.5mm }}
.key {{ background:{PALE}; border-left:3px solid var(--ac); padding:1.8mm 3mm; display:grid;
        grid-template-columns:auto 1fr; column-gap:3.5mm; align-items:center }}
.key-num {{ font-family:'PT Sans'; font-size:24px; font-weight:700; color:var(--ac);
            font-variant-numeric:tabular-nums; white-space:nowrap }}
.key-txt {{ font-size:9.6px; line-height:1.28 }}
.key-src {{ font-family:'PT Sans'; font-size:7.6px; color:{GRAY}; margin-top:.7mm }}
.body {{ height:{BODY_MM}mm; display:grid; grid-template-columns:{LEFT_MM}mm {RIGHT_MM}mm;
         column-gap:{GAP_MM}mm }}
.left {{ display:flex; flex-direction:column; justify-content:space-between; min-height:0 }}
.h {{ font-family:'PT Sans'; font-size:8.6px; font-weight:700; letter-spacing:.1em;
      text-transform:uppercase; color:{INK} }}
.sub {{ font-family:'PT Sans'; font-size:8px; color:{GRAY}; margin:.4mm 0 .8mm }}
.src {{ font-family:'PT Sans'; font-size:7.4px; color:{GRAY}; margin-top:1mm }}
.c {{ text-align:center }}
.ar {{ font-size:11px }}
.inds {{ display:grid; grid-template-columns:1fr 1fr; column-gap:5mm }}
.ind {{ border-top:2px solid {INK}; padding-top:1.4mm }}
.ind-head {{ display:grid; grid-template-columns:1fr 22px 34px; font-family:'PT Sans'; font-size:7.6px;
             font-weight:700; letter-spacing:.08em; text-transform:uppercase; color:{GRAY};
             padding-bottom:.8mm; white-space:nowrap }}
.ind-row {{ display:grid; grid-template-columns:1fr 22px 34px; align-items:center;
            border-top:1px solid {HAIR}; padding:1.15mm 0 }}
.ind-val {{ font-family:'PT Sans'; font-size:15.5px; font-weight:700; line-height:1.05;
            font-variant-numeric:tabular-nums }}
.ind-lab {{ font-family:'PT Sans'; font-size:7.8px; color:{GRAY}; line-height:1.2; margin-top:.4mm }}
.chart {{ border-top:2px solid {INK}; padding-top:1.4mm }}
.st {{ border-top:2px solid {INK}; padding-top:1.4mm }}
.st-row {{ display:grid; grid-template-columns:1fr 62px 62px; font-family:'PT Sans'; font-size:8.6px;
           border-top:1px solid {HAIR}; padding:1.05mm 0; line-height:1.2 }}
.st-h {{ border-top:none; font-size:7.4px; font-weight:700; letter-spacing:.06em;
         text-transform:uppercase; color:{GRAY} }}
.v {{ text-align:right; font-weight:700; font-variant-numeric:tabular-nums }}
.right {{ display:flex; flex-direction:column; gap:3mm; min-height:0 }}
.pcols {{ flex:1; display:grid; grid-template-columns:1fr 1fr; column-gap:7mm; min-height:0 }}
.pcol {{ overflow:hidden }}
.bottom {{ height:{BOTTOM_MM}mm; display:grid; grid-template-columns:1.25fr 1.3fr .95fr; column-gap:6mm;
           border-top:1px solid {HAIR}; padding-top:2mm }}
.cell {{ overflow:hidden }}
.pb-h {{ font-family:'PT Sans'; font-size:10.5px; font-weight:700; letter-spacing:.06em;
         text-transform:uppercase; border-bottom:2px solid {INK}; padding-bottom:1.1mm; margin-bottom:1.6mm }}
.pb-h span {{ display:block; font-family:'PT Serif'; font-weight:400; text-transform:none;
              letter-spacing:0; font-size:10px; color:var(--ac); margin-top:.6mm }}
.pb p {{ font-size:10.6px; line-height:1.48; margin:0 0 1.8mm; text-align:justify; hyphens:manual }}
.pb p b {{ font-family:'PT Sans'; font-size:9.6px; letter-spacing:.02em }}
.outlook p {{ font-size:9.7px; line-height:1.4 }}
.bsrc {{ font-family:'PT Sans'; font-size:7.4px; color:{GRAY}; margin-top:-.4mm }}
.dl-row {{ display:grid; grid-template-columns:30px 1.35fr 1.1fr 58px; column-gap:2mm;
           font-family:'PT Sans'; font-size:8.2px; line-height:1.22; border-top:1px solid {HAIR};
           padding:.85mm 0 }}
.dl-h {{ border-top:none; font-size:7.2px; font-weight:700; letter-spacing:.06em; text-transform:uppercase;
         color:{GRAY}; padding-top:0 }}
.dl-d {{ color:{GRAY}; font-variant-numeric:tabular-nums }}
.dl-w {{ color:{GRAY} }}
.dl-s {{ text-align:right; font-weight:700; font-variant-numeric:tabular-nums; white-space:nowrap }}
.gl {{ font-size:8.6px; line-height:1.36; margin-bottom:1.3mm; text-align:justify; hyphens:manual }}
.gl b {{ font-family:'PT Sans'; color:var(--ac) }}
.foot {{ height:{FOOT_MM}mm; border-top:1px solid {HAIR}; padding-top:1.4mm; display:flex;
         justify-content:space-between; gap:8mm; font-family:'PT Sans'; font-size:7.4px; color:{GRAY};
         line-height:1.35 }}
.foot .r {{ text-align:right; white-space:nowrap }}
"""


def page(sec, issue):
    ac = sec.get('accent') or ACCENTS.get(sec['key'], INK)
    left = (f'<div class="inds">{indicators("Экономика", sec["economic"], sec["economic_source"])}'
            f'{indicators("Рынок", sec["fundamentals"], sec["fundamentals_source"])}</div>'
            + ''.join(chart(c, ac) for c in sec['charts'])
            + stats_table(sec['stats']))
    blocks = {b['key']: b for b in sec['blocks']}
    cols = ''.join(f'<div class="pcol">{prose_block(blocks[k])}</div>'
                   for k in ('investment', 'occupier'))
    outlook = f'<div class="cell outlook">{prose_block(blocks["outlook"])}</div>'
    return f"""
<div class="page" data-sector="{esc(sec['key'])}" style="--ac:{ac}">
  <div class="top">
    <div class="mb">MARKETBEAT<small>НИДЕРЛАНДЫ · НЕДЕЛЬНЫЙ ОБЗОР РЫНКА НЕДВИЖИМОСТИ</small></div>
    <div class="sec">{esc(sec['name'])}<small>{hy(sec['tagline'])}</small></div>
    <div class="iss"><b>{esc(issue['week_label'])}</b><br>{esc(issue['issue_line'])}<br>{esc(issue['prepared_by'])}</div>
  </div>
  <div class="keys">{takeaways(sec['takeaways'])}</div>
  <div class="body">
    <div class="left">{left}</div>
    <div class="right">
      <div class="pcols">{cols}</div>
      <div class="bottom">{outlook}{deals_table(sec['deals'])}{glossary(sec['glossary'])}</div>
    </div>
  </div>
  <div class="foot"><div>{esc(sec.get('sources_line', ''))}</div>
    <div class="r">{esc(issue['imprint'])}</div></div>
</div>"""


def build(issue, out):
    doc = ('<!DOCTYPE html><html lang="ru"><head><meta charset="utf-8">'
           f'<title>{esc(issue["issue_line"])}</title><style>{css()}</style></head><body>'
           + ''.join(page(s, issue) for s in issue['sectors']) + '</body></html>')
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    open(out, 'w', encoding='utf-8').write(doc)
    print(f'{out}: {len(issue["sectors"])} полос')


if __name__ == '__main__':
    build(json.load(open(sys.argv[1], encoding='utf-8')),
          sys.argv[2] if len(sys.argv) > 2 else os.path.join(HERE, 'build', 'marketbeat.html'))
