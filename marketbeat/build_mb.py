#!/usr/bin/env python3
"""Полоса «Аналитика рынка Голландии»: один сектор — одна альбомная страница 432×279 мм.

Сетка повторяет квартальный MarketBeat Cushman & Wakefield и добивает его до
плотной полосы:

  шапка         название сектора, тезис недели, номер выпуска
  главное       три ключевые цифры недели с пояснением и источником
  слева         показатели экономики и рынка со стрелками «г/г» и «12 мес.»,
                два графика на реальных рядах, таблица квартальной базы
  справа        две текстовые колонки — инвестиционный рынок и рынок
                пользователя; под ними прогноз, таблица сделок недели и
                словарь терминов
  подвал        источники и колонцифра

Внешний вид — печатное финансовое исследование, а не экранный дашборд: кремовая
бумага, чернильные и золотые линейки, высококонтрастный заголовочный серифный
шрифт (Playfair Display), буквицы, римская нумерация разделов, штриховка вместо
серых заливок в графиках. Никаких плашек, теней и скруглений.

Пустых зон на полосе быть не должно: fit.py считает недобор браком наравне с
переполнением. Шрифты лежат в fonts/ (лицензия OFL).
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
LEFT_MM, GAP_MM = 146, 9
RIGHT_MM = PAGE_W_MM - 2 * PAD_MM - LEFT_MM - GAP_MM
HEAD_MM, KEY_MM, FOOT_MM = 21, 17, 7
BODY_MM = PAGE_H_MM - 2 * PAD_MM - HEAD_MM - KEY_MM - FOOT_MM - 3 * 2.5
BOTTOM_MM = 66                                 # прогноз · сделки · термины

PAPER, DESK = '#F6F2E9', '#E7E0D2'
INK, GRAY, HAIR, GOLD = '#1C1A17', '#5E5749', '#CBC2AF', '#A8863F'
UP, DOWN, FLAT = '#2F6B3A', '#9B2C1F', '#8A8272'
ACCENTS = {'living': '#7A1F1F', 'retail': '#8A5A14', 'industrial': '#1F3A5F',
           'offices': '#1E4D3B'}
ROMAN = {'investment': 'I', 'occupier': 'II', 'outlook': 'III'}

_dic = pyphen.Pyphen(lang='ru_RU')
_chart_n = [0]


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
    return (f'<div class="ind"><div class="ind-head"><div class="lab">{esc(title)}</div>'
            f'<div class="c">г/г</div><div class="c">12 мес.</div></div>{body}'
            f'<div class="src">{esc(source)}</div></div>')


def chart(spec, accent):
    """График по реальному ряду: столбцы или линия, подписи у точек.

    Библиотека не нужна: это десяток прямоугольников и одна ломаная, а
    зависимость на этом месте только усложняет воспроизводимость. Второстепенные
    столбцы — штриховка, а не серая заливка: так делают в печати.
    """
    _chart_n[0] += 1
    hid = f'h{_chart_n[0]}'
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
    g = [f'<defs><pattern id="{hid}" patternUnits="userSpaceOnUse" width="5" height="5" '
         f'patternTransform="rotate(45)"><line x1="0" y1="0" x2="0" y2="5" stroke="{INK}" '
         f'stroke-opacity=".55" stroke-width="1"/></pattern></defs>']
    for k in range(4):                       # четыре уровня сетки
        y = T + k * (H - T - B) / 3
        v = hi - k * span / 3
        g.append(f'<line x1="{L}" y1="{y:.1f}" x2="{W-R}" y2="{y:.1f}" stroke="{HAIR}" '
                 f'stroke-width=".7" stroke-dasharray="1.5 2.5"/>')
        g.append(f'<text x="{L-6}" y="{y+3:.1f}" text-anchor="end" font-size="8.3" fill="{GRAY}">'
                 f'{esc(tick(v, spec.get("fmt", "{:.0f}")))}</text>')
    if not line and lo < 0:
        y0 = y_of(0)
        g.append(f'<line x1="{L}" y1="{y0:.1f}" x2="{W-R}" y2="{y0:.1f}" stroke="{INK}" stroke-width=".9"/>')
    if line:
        xs = [L + i * step + step / 2 for i in range(n)]
        d = ' '.join(f'{x:.1f},{y_of(p["v"]):.1f}' for x, p in zip(xs, pts))
        if lo < 0 < hi:
            y0 = y_of(0)
            g.append(f'<line x1="{L}" y1="{y0:.1f}" x2="{W-R}" y2="{y0:.1f}" stroke="{INK}" stroke-width=".9"/>')
        g.append(f'<polyline points="{d}" fill="none" stroke="{accent}" stroke-width="2" '
                 f'stroke-linejoin="round"/>')
        for i, (x, p) in enumerate(zip(xs, pts)):
            y = y_of(p['v'])
            last = i == n - 1
            g.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{4 if last else 2}" '
                     f'fill="{accent if last else PAPER}" stroke="{accent}" stroke-width="1.4"/>')
            if p.get('t'):
                g.append(f'<text x="{x:.1f}" y="{y-8:.1f}" text-anchor="middle" font-size="9" '
                         f'font-weight="700" fill="{INK}">{esc(p["t"])}</text>')
    else:
        bw = step * 0.6
        y0 = y_of(0)
        for i, p in enumerate(pts):
            x = L + i * step + (step - bw) / 2
            y = y_of(p['v'])
            fill = f'url(#{hid})' if p.get('dim') else accent
            g.append(f'<rect x="{x:.1f}" y="{min(y, y0):.1f}" width="{bw:.1f}" '
                     f'height="{max(abs(y0 - y), 1):.1f}" fill="{fill}" stroke="{INK}" stroke-width=".8"/>')
            g.append(f'<text x="{x+bw/2:.1f}" y="{min(y, y0)-4:.1f}" text-anchor="middle" '
                     f'font-size="9" font-weight="700" fill="{INK}">{esc(p.get("t", ""))}</text>')
    for i, p in enumerate(pts):
        if p.get('k'):
            g.append(f'<text x="{L + i*step + step/2:.1f}" y="{H-7}" text-anchor="middle" '
                     f'font-size="8.3" fill="{GRAY}">{esc(p["k"])}</text>')
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
        f'<div class="key-src sc">{esc(k["src"])}</div></div></div>' for k in items)


def prose_block(b, dropcap=True):
    paras = ''.join(f'<p>{hy(p)}</p>' for p in b['paras'])
    src = f'<div class="bsrc">{esc(b["sources"])}</div>' if b.get('sources') else ''
    return (f'<div class="pb{" dc" if dropcap else ""}" data-block="{esc(b["key"])}">'
            f'<div class="pb-h"><span class="num">{ROMAN.get(b["key"], "")}</span>{esc(b["title"])}'
            f'<em>{esc(b["subtitle"])}</em></div>{paras}{src}</div>')


def deals_table(spec):
    rows = ''.join(
        f'<div class="dl-row"><div class="dl-d">{esc(r[0])}</div><div>{hy(r[1])}</div>'
        f'<div class="dl-w">{hy(r[2])}</div><div class="dl-s">{esc(r[3])}</div></div>'
        for r in spec['rows'])
    note = f'<div class="src">{esc(spec["note"])}</div>' if spec.get('note') else ''
    return (f'<div class="cell deals"><div class="pb-h">{esc(spec["title"])}</div>'
            f'<div class="dl-row dl-h sc"><div>дата</div><div>объект</div><div>стороны</div>'
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
@font-face {{ font-family:'Playfair'; src:url('{ff("PlayfairDisplay.ttf")}'); font-weight:400 900; font-style:normal }}
@font-face {{ font-family:'Playfair'; src:url('{ff("PlayfairDisplay-Italic.ttf")}'); font-weight:400 900; font-style:italic }}
@page {{ size: {PAGE_W_MM}mm {PAGE_H_MM}mm; margin: 0 }}
* {{ box-sizing: border-box }}
body {{ margin:0; background:{DESK}; font-family:'PT Serif',Georgia,serif; color:{INK};
        -webkit-font-smoothing:antialiased }}
.sc {{ font-family:'PT Sans',Arial,sans-serif; font-size:7.4px; letter-spacing:.16em;
       text-transform:uppercase; color:{GRAY} }}
.lab {{ font-family:'PT Sans',Arial,sans-serif; font-size:7.4px; letter-spacing:.16em; text-transform:uppercase;
        color:{GRAY}; display:flex; align-items:center; gap:2mm }}
.lab:before {{ content:''; width:6mm; border-top:1px solid {GOLD} }}
.page {{ position:relative; width:{PAGE_W_MM}mm; height:{PAGE_H_MM}mm; background:{PAPER};
         padding:{PAD_MM}mm; overflow:hidden; page-break-after:always;
         display:flex; flex-direction:column; gap:2.5mm }}
.top {{ height:{HEAD_MM}mm; display:grid; grid-template-columns:auto 1fr auto; align-items:end;
        column-gap:10mm; border-bottom:.6px solid {HAIR}; padding-bottom:2.6mm }}
.mb {{ line-height:1.5; padding-bottom:1mm }}
.mb b {{ display:block; font-family:'PT Sans'; font-weight:700; font-size:9.5px; letter-spacing:.2em;
         text-transform:uppercase; color:{INK} }}
.mb span {{ display:block; font-family:'PT Sans'; font-size:7.4px; letter-spacing:.16em; text-transform:uppercase;
            color:{GRAY} }}
.sec {{ font-family:'Playfair'; font-size:40px; font-weight:900; line-height:.9; color:var(--ac);
        letter-spacing:-.012em }}
.sec small {{ display:block; font-family:'Playfair'; font-style:italic; font-weight:400; font-size:12.5px;
              color:{INK}; margin-top:2.2mm; letter-spacing:0; line-height:1.2 }}
.iss {{ text-align:right; line-height:1.55; padding-bottom:1mm }}
.iss b {{ display:block; font-family:'Playfair'; font-weight:700; font-size:13px; color:{INK};
          letter-spacing:.01em; font-feature-settings:'lnum' 1; margin-bottom:.6mm }}
.keys {{ height:{KEY_MM}mm; display:grid; grid-template-columns:repeat(3,1fr); column-gap:9mm }}
.key {{ padding:1.4mm 0; display:grid; grid-template-columns:auto 1fr; column-gap:4mm; align-items:center }}
.key-num {{ font-family:'Playfair'; font-size:27px; font-weight:800; color:var(--ac);
            font-variant-numeric:lining-nums tabular-nums; font-feature-settings:'lnum' 1,'tnum' 1;
            white-space:nowrap; letter-spacing:-.01em; padding-bottom:1.2mm; border-bottom:1px solid {GOLD} }}
.key-txt {{ font-size:9.7px; line-height:1.3 }}
.key-src {{ margin-top:.8mm; font-size:6.9px }}
.body {{ height:{BODY_MM}mm; display:grid; grid-template-columns:{LEFT_MM}mm {RIGHT_MM}mm;
         column-gap:{GAP_MM}mm; border-top:.6px solid {HAIR}; padding-top:2.5mm }}
.left {{ display:flex; flex-direction:column; justify-content:space-between; min-height:0;
         border-right:.6px solid {HAIR}; padding-right:{GAP_MM/2}mm; margin-right:-{GAP_MM/2}mm }}
.left > * + * {{ border-top:.6px solid {HAIR}; padding-top:2mm }}
.h {{ font-family:'Playfair'; font-size:10.5px; font-weight:700; color:{INK}; line-height:1.15 }}
.sub {{ font-family:'PT Sans'; font-size:7.9px; color:{GRAY}; margin:.5mm 0 .8mm }}
.src {{ font-family:'PT Sans'; font-size:7.2px; color:{GRAY}; margin-top:1mm }}
.c {{ text-align:center }}
.ar {{ font-size:10px }}
.inds {{ display:grid; grid-template-columns:1fr 1fr; column-gap:6mm }}
.ind-head {{ display:grid; grid-template-columns:1fr 22px 34px; padding:0 0 1mm; white-space:nowrap;
             font-family:'PT Sans'; font-size:7.1px; letter-spacing:.14em; text-transform:uppercase; color:{GRAY} }}
.ind-row {{ display:grid; grid-template-columns:1fr 22px 34px; align-items:center;
            border-top:.6px solid {HAIR}; padding:1.0mm 0 }}
.ind-val {{ font-family:'Playfair'; font-size:16.5px; font-weight:800; line-height:1.05;
            font-variant-numeric:lining-nums tabular-nums; font-feature-settings:'lnum' 1,'tnum' 1 }}
.ind-lab {{ font-family:'PT Sans'; font-size:7.7px; color:{GRAY}; line-height:1.2; margin-top:.4mm }}
.st .h {{ margin-bottom:.6mm }}
.st-row {{ display:grid; grid-template-columns:1fr 66px 62px; font-family:'PT Sans'; font-size:8.5px;
           border-top:.6px solid {HAIR}; padding:1.05mm 0; line-height:1.2 }}
.st-h {{ border-top:none; font-size:7px; letter-spacing:.14em; text-transform:uppercase; color:{GRAY} }}
.v {{ text-align:right; font-weight:700; font-variant-numeric:lining-nums tabular-nums }}
.right {{ display:flex; flex-direction:column; gap:3mm; min-height:0 }}
.pcols {{ flex:1; display:grid; grid-template-columns:1fr 1fr; column-gap:8mm; min-height:0 }}
.pcol {{ overflow:hidden }}
.pcol + .pcol {{ border-left:.6px solid {HAIR}; padding-left:8mm; margin-left:-8mm }}
.bottom {{ height:{BOTTOM_MM}mm; display:grid; grid-template-columns:1.25fr 1.3fr .95fr; column-gap:6mm;
           border-top:.6px solid {HAIR}; padding-top:2.4mm }}
.cell {{ overflow:hidden }}
.pb-h {{ font-family:'Playfair'; font-size:13px; font-weight:800; letter-spacing:.005em; color:{INK};
         margin-bottom:1.9mm; line-height:1.15 }}
.pb-h .num {{ color:{GOLD}; margin-right:2mm; font-weight:700 }}
.pb-h em {{ display:block; font-family:'Playfair'; font-style:italic; font-weight:400; font-size:10.5px;
            color:var(--ac); margin-top:.8mm; letter-spacing:0 }}
.pb p {{ font-size:10.6px; line-height:1.48; margin:0 0 1.8mm; text-align:justify; hyphens:manual }}
.pb.dc p:first-of-type::first-letter {{ font-family:'Playfair'; font-weight:800; font-size:34px; line-height:.78;
        float:left; margin:2.5px 4px 0 0; color:var(--ac) }}
.outlook p {{ font-size:9.7px; line-height:1.4 }}
.bsrc {{ font-family:'PT Sans'; font-size:7.2px; color:{GRAY}; margin-top:-.4mm }}
.dl-row {{ display:grid; grid-template-columns:30px 1.35fr 1.1fr 58px; column-gap:2mm;
           font-family:'PT Sans'; font-size:8.2px; line-height:1.22; border-top:.6px solid {HAIR};
           padding:.85mm 0 }}
.dl-h {{ border-top:none; padding-top:0; font-size:7px }}
.dl-d {{ color:{GRAY}; font-variant-numeric:tabular-nums }}
.dl-w {{ color:{GRAY} }}
.dl-s {{ text-align:right; font-weight:700; font-variant-numeric:tabular-nums; white-space:nowrap }}
.gl {{ font-size:8.6px; line-height:1.36; margin-bottom:1.3mm; text-align:justify; hyphens:manual }}
.gl b {{ font-family:'Playfair'; font-weight:700; color:var(--ac) }}
.foot {{ height:{FOOT_MM}mm; border-top:.6px solid {HAIR}; padding-top:1.6mm; display:flex;
         justify-content:space-between; gap:8mm; font-family:'PT Sans'; font-size:7.2px; color:{GRAY};
         line-height:1.35 }}
.foot .r {{ text-align:right; white-space:nowrap; letter-spacing:.14em; text-transform:uppercase; font-size:6.9px }}
.foot .r i {{ font-style:normal; color:{GOLD}; margin:0 2mm; font-size:5px; vertical-align:1px }}
"""


def page(sec, issue, n, total):
    ac = sec.get('accent') or ACCENTS.get(sec['key'], INK)
    left = (f'<div class="inds">{indicators("Экономика", sec["economic"], sec["economic_source"])}'
            f'{indicators("Рынок", sec["fundamentals"], sec["fundamentals_source"])}</div>'
            + ''.join(chart(c, ac) for c in sec['charts'])
            + stats_table(sec['stats']))
    blocks = {b['key']: b for b in sec['blocks']}
    cols = ''.join(f'<div class="pcol">{prose_block(blocks[k])}</div>'
                   for k in ('investment', 'occupier'))
    outlook = f'<div class="cell outlook">{prose_block(blocks["outlook"], dropcap=False)}</div>'
    return f"""
<div class="page" data-sector="{esc(sec['key'])}" style="--ac:{ac}">
  <div class="top">
    <div class="mb"><b>Аналитика рынка Голландии</b><span>Недельный обзор рынка недвижимости</span></div>
    <div class="sec">{esc(sec['name'])}<small>{hy(sec['tagline'])}</small></div>
    <div class="iss"><b>{esc(issue['week_label'])}</b><span class="sc">{esc(issue['issue_line'])}<br>{esc(issue['prepared_by'])}</span></div>
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
    <div class="r">{esc(issue['imprint'])}<i>◆</i>полоса {n} из {total}</div></div>
</div>"""


def build(issue, out):
    secs = issue['sectors']
    doc = ('<!DOCTYPE html><html lang="ru"><head><meta charset="utf-8">'
           f'<title>{esc(issue["issue_line"])}</title><style>{css()}</style></head><body>'
           + ''.join(page(s, issue, i + 1, len(secs)) for i, s in enumerate(secs)) + '</body></html>')
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    open(out, 'w', encoding='utf-8').write(doc)
    print(f'{out}: {len(secs)} полос')


if __name__ == '__main__':
    build(json.load(open(sys.argv[1], encoding='utf-8')),
          sys.argv[2] if len(sys.argv) > 2 else os.path.join(HERE, 'build', 'marketbeat.html'))
