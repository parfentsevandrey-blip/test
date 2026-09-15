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

Вокруг полос секторов — аппарат коммерческого отчёта: обложка (тема недели,
график, макроцифры, оглавление, плитки секторов), резюме недели (экономика,
крупнейшие сделки, карточки секторов) и методология (как собран выпуск, пул в
цифрах, хроника недели, сводный словарь, источники).

Внешний вид — печатное финансовое исследование, а не экранный дашборд: кремовая
бумага, одиночные волосяные линейки и золото в акцентах, высококонтрастный
заголовочный серифный шрифт (Playfair Display), буквицы, римская нумерация
разделов, штриховка вместо серых заливок в графиках, корешок-закладка сектора,
нумерация иллюстраций. Никаких плашек, теней и скруглений.

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


def chart(spec, accent, num=None, H=168, W=520):
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
    fig = f'<span class="fig">Рис. {num}</span>' if num else ''
    return (f'<div class="chart"><div class="h">{fig}{esc(spec["title"])}</div>'
            f'<div class="sub">{esc(spec.get("subtitle", ""))}</div>'
            f'<svg viewBox="0 0 {W} {H}" width="100%">{"".join(g)}</svg>'
            f'<div class="src">{esc(spec.get("source", ""))}</div></div>')


def stats_table(spec, num=None):
    rows = ''.join(
        f'<div class="st-row"><div>{esc(r[0])}</div><div class="v">{esc(r[1])}</div>'
        f'<div class="v">{esc(r[2])}</div></div>' for r in spec['rows'])
    fig = f'<span class="fig">Табл. {num}</span>' if num else ''
    return (f'<div class="st"><div class="h">{fig}{esc(spec["title"])}</div>'
            f'<div class="st-row st-h"><div></div><div class="v">{esc(spec["c1"])}</div>'
            f'<div class="v">{esc(spec["c2"])}</div></div>{rows}'
            f'<div class="src">{esc(spec.get("source", ""))}</div></div>')


def emblem(size_mm=5.2):
    """Знак издания: четыре квадрата — четыре сектора, каждый в своём цвете."""
    c = [ACCENTS[k] for k in ('living', 'retail', 'industrial', 'offices')]
    return (f'<svg class="emb" viewBox="0 0 20 20" style="width:{size_mm}mm;height:{size_mm}mm">'
            f'<rect x="0" y="0" width="9" height="9" fill="{c[0]}"/>'
            f'<rect x="11" y="0" width="9" height="9" fill="{c[1]}"/>'
            f'<rect x="0" y="11" width="9" height="9" fill="{c[2]}"/>'
            f'<rect x="11" y="11" width="9" height="9" fill="{c[3]}"/></svg>')


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


def deals_table(spec, num=None):
    rows = ''.join(
        f'<div class="dl-row"><div class="dl-d">{esc(r[0])}</div><div>{hy(r[1])}</div>'
        f'<div class="dl-w">{hy(r[2])}</div><div class="dl-s">{esc(r[3])}</div></div>'
        for r in spec['rows'])
    note = f'<div class="src">{esc(spec["note"])}</div>' if spec.get('note') else ''
    fig = f'<span class="fig">Табл. {num}</span>' if num else ''
    return (f'<div class="cell deals"><div class="pb-h">{fig}{esc(spec["title"])}</div>'
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
.mb {{ display:flex; align-items:flex-end; gap:3mm }}
.emb {{ display:block; margin-bottom:1.2mm }}
.fig {{ font-family:'PT Sans'; font-size:7px; letter-spacing:.14em; text-transform:uppercase; color:{GOLD};
        margin-right:2mm; font-weight:700; vertical-align:1px }}
.tab {{ position:absolute; right:0; width:6.4mm; height:34mm; background:var(--ac); color:{PAPER};
        writing-mode:vertical-rl; transform:rotate(180deg); display:flex; align-items:center; justify-content:center;
        font-family:'PT Sans'; font-size:7.2px; letter-spacing:.22em; text-transform:uppercase }}
/* обложка */
.cover {{ padding:{PAD_MM + 3}mm {PAD_MM + 3}mm {PAD_MM}mm; gap:0 }}
.cv-top {{ display:flex; justify-content:space-between; align-items:flex-end; border-bottom:.6px solid {HAIR};
           padding-bottom:3mm }}
.cv-body {{ flex:1; display:grid; grid-template-columns:1.05fr 1fr; column-gap:16mm; padding-top:8mm; min-height:0 }}
.cv-left {{ display:flex; flex-direction:column; min-height:0 }}
.cv-title {{ font-family:'Playfair'; font-weight:900; font-size:56px; line-height:.94; letter-spacing:-.015em;
             color:{INK}; margin:3mm 0 5.5mm }}
.cv-lead {{ font-size:12.4px; line-height:1.5; margin:0 0 4mm; max-width:150mm }}
.cv-hero {{ max-width:150mm; margin-bottom:4mm }}
.cv-hero .h {{ font-size:11.5px }}
.cv-strip {{ display:grid; grid-template-columns:repeat(5,1fr); column-gap:4mm; max-width:150mm;
             border-top:.6px solid {HAIR}; border-bottom:.6px solid {HAIR}; padding:2.6mm 0 2.4mm }}
.cv-m b {{ display:block; font-family:'Playfair'; font-weight:800; font-size:19px; line-height:1; color:{INK};
           font-feature-settings:'lnum' 1,'tnum' 1; margin-bottom:1.1mm; letter-spacing:-.01em }}
.cv-m span {{ display:block; font-family:'PT Sans'; font-size:7.3px; line-height:1.25; color:{GRAY} }}
.cv-toc {{ margin-top:auto; max-width:150mm; padding-top:4mm }}
.cv-tr {{ display:grid; grid-template-columns:8mm 1fr; align-items:baseline; padding:1.3mm 0;
          border-top:.6px solid {HAIR} }}
.cv-tr:nth-child(2) {{ border-top:none; margin-top:1.5mm }}
.cv-tr b {{ font-family:'Playfair'; font-weight:700; font-size:13px; color:{GOLD}; font-feature-settings:'lnum' 1 }}
.cv-tr span {{ font-family:'Playfair'; font-weight:700; font-size:11px; color:{INK} }}
.cv-tr em {{ display:block; font-style:italic; font-size:8.9px; color:{GRAY}; margin-top:.3mm; line-height:1.3 }}
.cv-grid {{ display:grid; grid-template-columns:1fr 1fr; grid-template-rows:1fr 1fr; gap:0; min-height:0;
            border-top:.6px solid {HAIR}; border-left:.6px solid {HAIR} }}
.cv-tile {{ border-right:.6px solid {HAIR}; border-bottom:.6px solid {HAIR}; padding:4.5mm 6mm 4mm; display:flex;
            flex-direction:column; min-height:0; overflow:hidden }}
.cv-tile .lab {{ color:var(--ac) }}
.cv-tile .lab i {{ font-style:normal; margin-left:auto; color:{GRAY}; letter-spacing:0 }}
.cv-num {{ font-family:'Playfair'; font-weight:800; font-size:34px; color:var(--ac); line-height:1; margin:3.2mm 0 2mm;
           font-feature-settings:'lnum' 1,'tnum' 1; letter-spacing:-.01em }}
.cv-txt {{ font-size:10px; line-height:1.4 }}
.cv-mores {{ display:grid; grid-template-columns:1fr 1fr; column-gap:5mm; margin-top:3.2mm; padding-top:2.6mm;
             border-top:.6px solid {HAIR} }}
.cv-more b {{ display:block; font-family:'Playfair'; font-weight:800; font-size:18px; color:var(--ac); line-height:1;
              font-feature-settings:'lnum' 1,'tnum' 1; margin-bottom:1.2mm }}
.cv-more span {{ font-family:'PT Sans'; font-size:7.8px; line-height:1.3; color:{GRAY}; display:block }}
.cv-ind {{ margin-top:3.2mm; padding-top:2.4mm; border-top:.6px solid {HAIR} }}
.cv-ind .ind-val {{ font-size:13.5px }}
.cv-ind .ind-row {{ padding:.75mm 0 }}
.cv-ind .ind-lab {{ font-size:7.3px; margin-top:.2mm }}
.cv-ind .src {{ margin-top:.6mm }}
.cv-tag {{ font-family:'Playfair'; font-style:italic; font-size:9.6px; color:{GRAY}; line-height:1.3; margin-top:auto;
           border-top:.6px solid {HAIR}; padding-top:2mm }}
/* резюме */
.sm-body {{ flex:1; display:grid; grid-template-columns:118mm 1fr; column-gap:10mm; min-height:0;
            border-top:.6px solid {HAIR}; padding-top:3mm }}
.sm-left {{ display:flex; flex-direction:column; min-height:0 }}
.sm-left .st {{ margin-top:2mm }}
.sm-left .st-row {{ grid-template-columns:1fr 46px 1fr }}
.sm-left .td-row {{ grid-template-columns:24px 42px 1fr 58px; column-gap:2mm; font-size:8px }}
.td-sec {{ font-family:'PT Sans'; font-size:6.8px; letter-spacing:.1em; text-transform:uppercase; color:{GRAY};
           align-self:center }}
.mc-src {{ font-family:'PT Sans'; font-size:7.3px; color:{GRAY}; text-align:right; align-self:center }}
.sm-lead {{ font-size:10.4px; line-height:1.5; margin:2mm 0 0; text-align:justify; hyphens:manual }}
.sm-how {{ margin-top:auto; padding-top:4mm }}
.sm-note {{ font-size:9.2px; line-height:1.45; margin:2mm 0 0; color:{GRAY}; text-align:justify; hyphens:manual }}
.sm-grid {{ display:grid; grid-template-columns:1fr; grid-template-rows:repeat(4,1fr); min-height:0;
            border-left:.6px solid {HAIR}; padding-left:8mm }}
.sm-card {{ padding:2.6mm 0; border-top:.6px solid {HAIR}; display:flex; flex-direction:column; min-height:0;
            overflow:hidden }}
.sm-card:first-child {{ border-top:none; padding-top:0 }}
.sm-h {{ font-family:'Playfair'; font-size:15px; font-weight:800; color:var(--ac); line-height:1.1 }}
.sm-h .num {{ color:{GOLD}; margin-right:2mm; font-weight:700 }}
.sm-h em {{ display:block; font-style:italic; font-weight:400; font-size:9.8px; color:{INK}; margin-top:1mm }}
.sm-row {{ display:grid; grid-template-columns:54mm 1fr 66mm; column-gap:6mm; margin-top:2.4mm; flex:1; min-height:0 }}
.sm-base {{ border-left:.6px solid {HAIR}; padding-left:6mm }}
.sm-base .h {{ font-size:9.8px }}
.sm-base .st-row {{ font-size:7.9px; padding:.8mm 0; grid-template-columns:1fr 54px 50px }}
.sm-base .src {{ font-size:6.8px; margin-top:.6mm }}
.sm-keys {{ display:flex; flex-direction:column; gap:1.8mm; border-right:.6px solid {HAIR}; padding-right:5mm }}
.sm-key {{ display:grid; grid-template-columns:auto 1fr; column-gap:3mm; align-items:baseline }}
.sm-key b {{ font-family:'Playfair'; font-weight:800; font-size:15px; color:var(--ac); line-height:1;
             font-feature-settings:'lnum' 1,'tnum' 1; white-space:nowrap }}
.sm-key span {{ font-family:'PT Sans'; font-size:7.5px; line-height:1.3; color:{GRAY}; display:block }}
.sm-decks {{ border-bottom:.6px solid {HAIR}; padding-bottom:1.4mm; margin-bottom:1.6mm }}
.sm-deck {{ font-family:'Playfair'; font-size:9.8px; font-weight:700; line-height:1.4; color:{INK} }}
.sm-deck .num, .sm-p .num {{ color:{GOLD}; margin-right:1.6mm; font-weight:700 }}
.sm-deck em {{ font-weight:400; font-style:italic; color:var(--ac); margin-left:1.6mm }}
.sm-p {{ font-size:9.9px; line-height:1.46; margin:0; text-align:justify; hyphens:manual }}
.sm-p b {{ font-family:'Playfair'; font-size:9.8px; font-weight:700 }}
/* методология */
.co-body {{ display:grid; grid-template-columns:repeat(5,1fr); column-gap:9mm; border-top:.6px solid {HAIR};
            padding:3mm 0 3mm }}
.co-col p {{ font-size:9.8px; line-height:1.48; margin:1.6mm 0 0; text-align:justify; hyphens:manual }}
.co-col + .co-col {{ border-left:.6px solid {HAIR}; padding-left:9mm; margin-left:-9mm }}
.co-disc {{ font-family:'PT Sans'; font-size:8px; color:{GRAY}; margin-top:4mm }}
.co-st {{ grid-template-columns:1fr 34px 38px 34px 38px; font-size:8.2px }}
.co-st.st-h {{ font-size:6.8px }}
.co-tot {{ font-weight:700; border-top:1px solid {INK} }}
.co-note {{ font-family:'PT Sans'; font-size:7.6px; color:{GRAY}; line-height:1.4; margin-top:2mm }}
.co-glrow {{ display:grid; grid-template-columns:1fr 72mm; column-gap:8mm }}
.co-chart {{ border-left:.6px solid {HAIR}; padding-left:8mm; margin-left:-8mm }}
.co-low {{ flex:1; min-height:0; overflow:hidden; border-top:.6px solid {HAIR}; padding-top:3mm }}
.co-ch {{ column-count:5; column-gap:8mm; column-rule:.6px solid {HAIR}; margin:2mm 0 4mm }}
.ch {{ font-size:8.7px; line-height:1.38; break-inside:avoid; margin-bottom:1.2mm; text-align:justify; hyphens:manual }}
.ch-d {{ font-family:'PT Sans'; font-weight:700; font-size:8px; color:{INK}; margin-right:1.6mm;
         font-variant-numeric:tabular-nums }}
.ch-s {{ font-family:'PT Sans'; font-size:6.6px; letter-spacing:.12em; text-transform:uppercase; margin-right:1.6mm }}
.ch-o {{ font-family:'PT Sans'; font-size:7.4px; color:{GRAY} }}
.co-gl {{ column-count:4; column-gap:8mm; column-rule:.6px solid {HAIR}; margin-top:2mm }}
.co-gl .gl {{ font-size:8.8px; line-height:1.4; break-inside:avoid; margin-bottom:1.2mm }}
.co-srcline {{ font-family:'PT Sans'; font-size:7.8px; line-height:1.5; color:{GRAY}; margin:3.5mm 0 0; border-top:.6px solid {HAIR}; padding-top:2mm }}
.co-srcline b {{ letter-spacing:.14em; text-transform:uppercase; font-size:7px; color:{GRAY}; margin-right:2mm }}
"""


def page(sec, issue, n, total, k, nsec):
    ac = sec.get('accent') or ACCENTS.get(sec['key'], INK)
    left = (f'<div class="inds">{indicators("Экономика", sec["economic"], sec["economic_source"])}'
            f'{indicators("Рынок", sec["fundamentals"], sec["fundamentals_source"])}</div>'
            + ''.join(chart(c, ac, i + 1) for i, c in enumerate(sec['charts']))
            + stats_table(sec['stats'], 1))
    blocks = {b['key']: b for b in sec['blocks']}
    cols = ''.join(f'<div class="pcol">{prose_block(blocks[k])}</div>'
                   for k in ('investment', 'occupier'))
    outlook = f'<div class="cell outlook">{prose_block(blocks["outlook"], dropcap=False)}</div>'
    return f"""
<div class="page" data-sector="{esc(sec['key'])}" style="--ac:{ac}">
  <div class="tab" style="top:{34 + k * 38}mm">{esc(sec['name'])}</div>
  <div class="top">
    <div class="mb">{emblem()}<div><b>Аналитика рынка Голландии</b><span>Недельный обзор рынка недвижимости</span></div></div>
    <div class="sec">{esc(sec['name'])}<small>{hy(sec['tagline'])}</small></div>
    <div class="iss"><b>{esc(issue['week_label'])}</b><span class="sc">{esc(issue['issue_line'])}<br>{esc(issue['prepared_by'])}</span></div>
  </div>
  <div class="keys">{takeaways(sec['takeaways'])}</div>
  <div class="body">
    <div class="left">{left}</div>
    <div class="right">
      <div class="pcols">{cols}</div>
      <div class="bottom">{outlook}{deals_table(sec['deals'], 2)}{glossary(sec['glossary'])}</div>
    </div>
  </div>
  <div class="foot"><div>{esc(sec.get('sources_line', ''))}</div>
    <div class="r">{esc(issue['imprint'])}<i>◆</i>{n} / {total}</div></div>
</div>"""


def cover(issue, total):
    """Обложка: тема недели, график к ней, макроцифры, оглавление с тезисами и
    четыре плитки — по сектору: три факта недели и квартальная база."""
    cv = issue['cover']
    def tile(s, i):
        t = s['takeaways']
        more = ''.join(f'<div class="cv-more"><b>{esc(x["num"])}</b><span>{hy(x["text"])}</span></div>'
                       for x in t[1:3])
        base = indicators('Квартальная база', s['fundamentals'], s['fundamentals_source'])
        return (f'<div class="cv-tile" data-fill="плитка {esc(s["name"])}" style="--ac:{ACCENTS[s["key"]]}">'
                f'<div class="lab">{esc(s["name"])}<i>{i + 3}</i></div>'
                f'<div class="cv-num">{esc(t[0]["num"])}</div>'
                f'<div class="cv-txt">{hy(t[0]["text"])}</div>'
                f'<div class="cv-mores">{more}</div>'
                f'<div class="cv-ind">{base}</div>'
                f'<div class="cv-tag">{hy(s["tagline"])}</div></div>')
    tiles = ''.join(tile(s, i) for i, s in enumerate(issue['sectors']))
    hero = chart(cv['chart'], INK, H=180) if cv.get('chart') else ''
    strip = ''.join(f'<div class="cv-m"><b>{esc(v)}</b><span>{esc(n)}</span></div>'
                    for n, v, _ in (issue['macro'][k] for k in (0, 1, 2, 3, 7)))
    rows = []
    for idx, e in enumerate(issue['toc']):
        if e is None:
            s = issue['sectors'][idx - 1]
            e = (s['name'], s['tagline'])
        rows.append(f'<div class="cv-tr"><b>{idx + 2}</b><div><span>{esc(e[0])}</span><em>{hy(e[1])}</em></div></div>')
    return f"""
<div class="page cover" data-kind="cover">
  <div class="cv-top"><div class="mb">{emblem(6.5)}<div><b>Аналитика рынка Голландии</b><span>Недельный обзор рынка недвижимости</span></div></div>
    <div class="iss"><b>{esc(issue['week_label'])}</b><span class="sc">{esc(issue['issue_line'])}</span></div></div>
  <div class="cv-body">
    <div class="cv-left" data-fill="обложка слева">
      <div class="lab">{esc(cv['kicker'])}</div>
      <div class="cv-title">{hy(cv['title'])}</div>
      <p class="cv-lead">{hy(cv['lead'])}</p>
      <div class="cv-hero">{hero}</div>
      <div class="cv-strip">{strip}</div>
      <div class="cv-toc"><div class="lab">В этом выпуске</div>{''.join(rows)}</div>
    </div>
    <div class="cv-grid">{tiles}</div>
  </div>
  <div class="foot"><div>{esc(issue['disclaimer'])}</div><div class="r">{esc(issue['prepared_by'])}<i>◆</i>1 / {total}</div></div>
</div>"""


def summary(issue, total):
    """Резюме: экономика недели одной таблицей и четыре сектора по три цифры и прогнозу."""
    macro = ''.join(f'<div class="st-row"><div>{esc(r[0])}</div><div class="v">{esc(r[1])}</div>'
                    f'<div class="mc-src">{esc(r[2])}</div></div>' for r in issue['macro'])
    deals = ''.join(f'<div class="st-row td-row"><div class="dl-d">{esc(r[0])}</div><div class="td-sec">{esc(r[1])}</div>'
                    f'<div>{hy(r[2])}</div><div class="dl-s">{esc(r[4])}</div></div>' for r in issue['top_deals'])
    cards = []
    for i, s in enumerate(issue['sectors']):
        blocks = {b['key']: b for b in s['blocks']}
        keys = ''.join(f'<div class="sm-key"><b>{esc(t["num"])}</b><span>{hy(t["text"])}</span></div>'
                       for t in s['takeaways'])
        decks = ''.join(f'<div class="sm-deck"><span class="num">{ROMAN[k]}</span>{esc(blocks[k]["title"])}'
                        f'<em>{hy(blocks[k]["subtitle"])}</em></div>' for k in ('investment', 'occupier'))
        cards.append(
            f'<div class="sm-card" data-fill="карточка {esc(s["name"])}" style="--ac:{ACCENTS[s["key"]]}">'
            f'<div class="sm-h"><span class="num">{i + 3}</span>{esc(s["name"])}<em>{hy(s["tagline"])}</em></div>'
            f'<div class="sm-row"><div class="sm-keys">{keys}</div><div class="sm-txt">'
            f'<div class="sm-decks">{decks}</div>'
            f'<p class="sm-p"><b><span class="num">{ROMAN["outlook"]}</span>Прогноз.</b> {hy(blocks["outlook"]["paras"][0])}</p>'
            f'</div><div class="sm-base">{stats_table(s["stats"])}</div></div></div>')
    return f"""
<div class="page" data-kind="summary">
  <div class="top">
    <div class="mb">{emblem()}<div><b>Аналитика рынка Голландии</b><span>Недельный обзор рынка недвижимости</span></div></div>
    <div class="sec" style="--ac:{INK}">Резюме недели<small>{hy(issue['cover']['title'])}</small></div>
    <div class="iss"><b>{esc(issue['week_label'])}</b><span class="sc">{esc(issue['issue_line'])}<br>{esc(issue['prepared_by'])}</span></div>
  </div>
  <div class="sm-body">
    <div class="sm-left" data-fill="резюме слева">
      <div class="lab">Экономика недели</div>
      <div class="st"><div class="st-row st-h"><div>показатель</div><div class="v">значение</div><div class="mc-src">источник</div></div>{macro}</div>
      <div class="lab" style="margin-top:5mm">Главное</div>
      <p class="sm-lead">{hy(issue['cover']['lead'])}</p>
      <p class="sm-lead">{hy(issue['cover']['lead2'])}</p>
      <div class="lab" style="margin-top:5mm">Крупнейшие сделки недели</div>
      <div class="st">{deals}</div>
      <div class="sm-how"><div class="lab">Как читать полосу</div>
      <p class="sm-note">Стрелки у показателей на полосах: <span style="color:{UP}">&#9650;</span> рост, <span style="color:{DOWN}">&#9660;</span> снижение, <span style="color:{FLAT}">&#9654;</span> без изменений; первая — за год, вторая — ожидание редакции на 12 месяцев. Квартальная база (Cushman &amp; Wakefield, II квартал 2026) в тексте помечена явно. Три цифры недели в шапке каждой полосы — факты, отобранные движком; сделки, не попавшие в текст, вынесены в таблицу.</p></div>
    </div>
    <div class="sm-grid">{''.join(cards)}</div>
  </div>
  <div class="foot"><div>{esc(issue['disclaimer'])}</div><div class="r">{esc(issue['imprint'])}<i>◆</i>2 / {total}</div></div>
</div>"""


def colophon(issue, total):
    """Методология и источники: как собран пул, как отбирались факты, откуда база."""
    srcs = sorted({x.strip() for s in issue['sectors']
                   for x in re.sub(r'^Источники:\s*', '', s.get('sources_line', '')).split(',') if x.strip()})
    cols = issue['method']
    body = ''.join(
        f'<div class="co-col"><div class="lab">{esc(c["title"])}</div>' +
        ''.join(f'<p>{hy(pp)}</p>' for pp in c['paras']) + '</div>' for c in cols)
    st = issue.get('stats', [])
    stats = ''.join(f'<div class="st-row co-st"><div>{esc(r[0])}</div><div class="v">{r[1]}</div><div class="v">{r[2]}</div>'
                    f'<div class="v">{r[3]}</div><div class="v">{r[4]}</div></div>' for r in st)
    if st:
        stats += (f'<div class="st-row co-st co-tot"><div>Всего</div><div class="v">{sum(r[1] for r in st)}</div>'
                  f'<div class="v">{sum(r[2] for r in st)}</div><div class="v">{sum(r[3] for r in st)}</div><div class="v">—</div></div>')
    seen, gl = set(), []
    for s in issue['sectors']:
        for t, x in s['glossary']:
            if t not in seen:
                seen.add(t); gl.append(f'<div class="gl"><b style="--ac:{ACCENTS[s["key"]]}">{esc(t)}</b> — {hy(x)}</div>')
    glos = ''.join(gl)
    chron = ''.join(
        f'<div class="ch"><span class="ch-d">{esc(d)}</span><span class="ch-s" style="color:{ACCENTS[k]}">{esc(nm)}</span>'
        f'{hy(lead)} <span class="ch-o">{esc(outlet)}</span></div>' for d, k, nm, outlet, lead in issue.get('chronicle', []))
    byday = chart(issue['by_day'], INK, H=130, W=340) if issue.get('by_day') else ''
    return f"""
<div class="page" data-kind="colophon">
  <div class="top">
    <div class="mb">{emblem()}<div><b>Аналитика рынка Голландии</b><span>Недельный обзор рынка недвижимости</span></div></div>
    <div class="sec" style="--ac:{INK}">Методология и источники<small>как собран этот выпуск</small></div>
    <div class="iss"><b>{esc(issue['week_label'])}</b><span class="sc">{esc(issue['issue_line'])}<br>{esc(issue['prepared_by'])}</span></div>
  </div>
  <div class="co-body">{body}
    <div class="co-col"><div class="lab">Пул недели в цифрах</div>
      <div class="st"><div class="st-row st-h co-st"><div>сектор</div><div class="v">в пуле</div><div class="v">в тексте</div><div class="v">сделок</div><div class="v">источн.</div></div>{stats}</div>
      <p class="co-note">«В пуле» — проверенные по дате факты; «в тексте» — отобранные движком; остальные сделки — в таблицах полос.</p></div>
    <div class="co-col"><div class="lab">Выходные данные</div>
      <p>{esc(issue['issue_line'])}. Подготовлено {esc(issue['prepared_by'])}. Формат полосы заимствован у квартальных MarketBeat Cushman &amp; Wakefield; содержание и оценки — редакции. Шрифты PT Serif, PT Sans, Playfair Display (OFL).</p>
      <div class="lab" style="margin-top:4mm">Периодичность</div>
      <p>{hy(issue['periodicity'])}</p>
      <p class="co-disc">{esc(issue['disclaimer'])}</p></div>
  </div>
  <div class="co-low" data-fill="хроника и словарь">
    <div class="lab">Хроника недели · сюжеты первой величины, вошедшие в текст</div><div class="co-ch">{chron}</div>
    <div class="co-glrow"><div><div class="lab">Словарь выпуска</div><div class="co-gl">{glos}</div></div>
      <div class="co-chart">{byday}</div></div>
    <p class="co-srcline"><b>Источники выпуска</b> {esc(' · '.join(srcs))}</p>
  </div>
  <div class="foot"><div>{esc(issue['imprint'])}</div><div class="r">{esc(issue['prepared_by'])}<i>◆</i>{total} / {total}</div></div>
</div>"""


def build(issue, out):
    secs = issue['sectors']
    total = len(secs) + 3                      # обложка, резюме, сектора, методология
    pages = ([cover(issue, total), summary(issue, total)]
             + [page(s, issue, i + 3, total, i, len(secs)) for i, s in enumerate(secs)]
             + [colophon(issue, total)])
    doc = ('<!DOCTYPE html><html lang="ru"><head><meta charset="utf-8">'
           f'<title>{esc(issue["issue_line"])}</title><style>{css()}</style></head><body>'
           + ''.join(pages) + '</body></html>')
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    open(out, 'w', encoding='utf-8').write(doc)
    print(f'{out}: {total} полос')


if __name__ == '__main__':
    build(json.load(open(sys.argv[1], encoding='utf-8')),
          sys.argv[2] if len(sys.argv) > 2 else os.path.join(HERE, 'build', 'marketbeat.html'))
