# -*- coding: utf-8 -*-
"""Цена метра: «Капельский, 5» и то, что продаётся вокруг.

Полоса — от самого дешёвого лота в экспозиции до самого дорогого, засечка
внутри полосы — медиана. Данные — выдача Циан (см. kp_cian.py), срез 02.09.2026.
У «Капельского» полосы нет: лоты в открытую продажу не выведены, есть один
ориентир закрытых продаж — 1,3 млн ₽ за метр.

Пунктир — средневзвешенный метр премиум-класса Москвы, NF Group, I кв. 2026.
"""
import os
from PIL import Image, ImageDraw, ImageFont

from kp_data import PEERS, MKT_PREM, PPM

HERE = os.path.dirname(os.path.abspath(__file__))
F = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
FB = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
fnt = lambda s, b=False: ImageFont.truetype(FB if b else F, s)

S = 2
W, H = 1400, 780
SURFACE, INK, MUTED, GRID = (252, 252, 251), (34, 40, 52), (112, 120, 134), (226, 223, 216)
NEW, RES, RED, DASH = (169, 118, 47), (74, 123, 200), (179, 40, 45), (150, 100, 40)
MAXV = 3_800_000

ROWS = [{'name': 'Капельский, 5', 'sub': 'Капельский пер. · 2029 · без отделки',
         'lo': PPM, 'hi': PPM, 'med': PPM, 'kind': 'ours'}]
for r in PEERS:
    kind = r['kind']
    what = 'новостройка' if kind == 'new' else f"вторичка, {r['year'] or '—'}"
    ROWS.append({'name': f"{r['no']}. {r['name']}", 'kind': kind,
                 'sub': f"{r['dist'] / 1000:.2f} км · {what} · {r['n']} лот".replace('.', ','),
                 'lo': r['ppmLo'], 'hi': r['ppmHi'], 'med': r['ppmMed']})
ROWS.sort(key=lambda r: -r['med'])

img = Image.new('RGB', (W * S, H * S), SURFACE)
dr = ImageDraw.Draw(img)

L, R, T = 40 * S, 118 * S, 118 * S
PLOT_L = L + 380 * S
PLOT_W = W * S - R - PLOT_L
x_of = lambda v: PLOT_L + PLOT_W * min(v, MAXV) / MAXV
nf = lambda v: f'{v:,.0f}'.replace(',', ' ')

dr.text((L, 30 * S), 'Цена метра вокруг Капельского переулка', font=fnt(23 * S, True), fill=INK)
dr.text((L, 66 * S), 'Полоса — от дешёвого лота к дорогому, засечка — медиана. Бронзовое — новостройки, синее — вторичка',
        font=fnt(14 * S), fill=MUTED)

BOT = H * S - 58 * S
for g in range(0, MAXV + 1, 500_000):
    x = x_of(g)
    dr.line([(x, T - 8 * S), (x, BOT)], fill=GRID, width=1 * S)
    lab = f'{g / 1e6:.1f}'.replace('.', ',')
    dr.text((x - dr.textlength(lab, font=fnt(12 * S)) / 2, BOT + 10 * S), lab,
            font=fnt(13 * S), fill=MUTED)
dr.text((PLOT_L + PLOT_W - 62 * S, BOT + 30 * S), 'млн ₽ за м²', font=fnt(13 * S), fill=MUTED)

xp = x_of(MKT_PREM)
for y in range(int(T - 8 * S), int(BOT), 12 * S):
    dr.line([(xp, y), (xp, y + 6 * S)], fill=DASH, width=2 * S)
dr.text((xp + 8 * S, T - 30 * S), 'премиум-класс Москвы, 1,6 млн', font=fnt(13 * S), fill=DASH)

rowh = (BOT - T) / len(ROWS)
BH = int(rowh * 0.44)
for i, r in enumerate(ROWS):
    cy = int(T + rowh * i + rowh / 2)
    ours = r['kind'] == 'ours'
    dr.text((L, cy - 19 * S), r['name'], font=fnt(17 * S, ours), fill=RED if ours else INK)
    dr.text((L, cy + 4 * S), r['sub'], font=fnt(12 * S), fill=MUTED)
    fill = RED if ours else (NEW if r['kind'] == 'new' else RES)
    if ours:
        x1 = x_of(r['lo'])
        dr.rectangle([x1 - 5 * S, cy - BH // 2, x1 + 5 * S, cy + BH // 2], fill=fill)
        dr.text((x1 + 18 * S, cy - 10 * S), '≈ ' + nf(r['lo']), font=fnt(18 * S, True), fill=fill)
    else:
        dr.rectangle([x_of(r['lo']), cy - BH // 2, x_of(r['hi']), cy + BH // 2], fill=fill)
        xm = x_of(r['med'])
        dr.line([(xm, cy - BH // 2 - 3 * S), (xm, cy + BH // 2 + 3 * S)], fill=SURFACE, width=3 * S)
        over = r['hi'] > MAXV
        dr.text((x_of(r['hi']) + 12 * S, cy - 9 * S),
                nf(r['med']) + (' ›' if over else ''), font=fnt(16 * S, True), fill=INK)

dr.line([(PLOT_L, T - 8 * S), (PLOT_L, BOT)], fill=(200, 197, 190), width=1 * S)
dr.line([(PLOT_L, BOT), (W * S - R, BOT)], fill=(200, 197, 190), width=1 * S)

os.makedirs(os.path.join(HERE, 'assets'), exist_ok=True)
img.resize((W, H), Image.LANCZOS).save(os.path.join(HERE, 'assets', 'kp_chart.png'))
print('assets/kp_chart.png', (W, H))
for r in ROWS:
    print(f"  {r['name'][:26]:28s} {nf(r['lo']):>10s} – {nf(r['hi']):>10s}  мед {nf(r['med']):>10s}")
