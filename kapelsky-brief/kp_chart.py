# -*- coding: utf-8 -*-
"""Цена метра: «Капельский, 5» и элитная первичка вокруг.

Полоса — диапазон цены метра по лотам, которые проект держит в экспозиции
(срез mskguru, 01.09.2026). У «Капельского» диапазона нет: лоты в открытую
продажу не выведены, есть один ориентир закрытых продаж — 1,3 млн ₽ за метр.
Пунктир — средневзвешенный метр премиум-класса Москвы по NF Group, I кв. 2026.
"""
import os
from PIL import Image, ImageDraw, ImageFont

from kp_data import PEERS, MKT_PREM

HERE = os.path.dirname(os.path.abspath(__file__))
F = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
FB = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
fnt = lambda s, b=False: ImageFont.truetype(FB if b else F, s)

S = 2
W, H = 1400, 700
SURFACE, INK, MUTED, GRID = (252, 252, 251), (34, 40, 52), (112, 120, 134), (226, 223, 216)
BAR, RED, DASH = (74, 123, 200), (179, 40, 45), (169, 118, 47)

ROWS = sorted(PEERS, key=lambda r: -r[6])
MAXV = 5_200_000

img = Image.new('RGB', (W * S, H * S), SURFACE)
dr = ImageDraw.Draw(img)

L, R, T = 40 * S, 120 * S, 118 * S
PLOT_L = L + 380 * S
PLOT_W = W * S - R - PLOT_L
x_of = lambda v: PLOT_L + PLOT_W * v / MAXV
nf = lambda v: f'{v:,.0f}'.replace(',', ' ')

dr.text((L, 30 * S), 'Цена метра: диапазон экспозиции', font=fnt(23 * S, True), fill=INK)
dr.text((L, 66 * S), 'Синее — от самого дешёвого лота до самого дорогого. Красное — ориентир закрытых продаж',
        font=fnt(15 * S), fill=MUTED)

BOT = H * S - 58 * S
for g in range(0, MAXV + 1, 1_000_000):
    x = x_of(g)
    dr.line([(x, T - 8 * S), (x, BOT)], fill=GRID, width=1 * S)
    lab = f'{g // 1_000_000}'
    dr.text((x - dr.textlength(lab, font=fnt(13 * S)) / 2, BOT + 10 * S), lab,
            font=fnt(13 * S), fill=MUTED)
dr.text((PLOT_L + PLOT_W - 62 * S, BOT + 30 * S), 'млн ₽ за м²', font=fnt(13 * S), fill=MUTED)

# средний метр премиум-класса
xp = x_of(MKT_PREM)
for y in range(int(T - 8 * S), int(BOT), 12 * S):
    dr.line([(xp, y), (xp, y + 6 * S)], fill=DASH, width=2 * S)
dr.text((xp + 8 * S, T - 30 * S), 'премиум-класс Москвы, 1,6 млн', font=fnt(13 * S), fill=DASH)

rowh = (BOT - T) / len(ROWS)
BH = int(rowh * 0.42)
for i, (name, metro, klass, term, fin, lo, hi) in enumerate(ROWS):
    cy = int(T + rowh * i + rowh / 2)
    ours = name.startswith('Капельский')
    dr.text((L, cy - 20 * S), name, font=fnt(18 * S, ours), fill=RED if ours else INK)
    dr.text((L, cy + 4 * S), f'{metro} · {term} · {fin}', font=fnt(12 * S), fill=MUTED)
    if ours:
        x1 = x_of(lo)
        dr.rectangle([x1 - 5 * S, cy - BH // 2, x1 + 5 * S, cy + BH // 2], fill=RED)
        dr.text((x1 + 18 * S, cy - 11 * S), '≈ ' + nf(lo), font=fnt(19 * S, True), fill=RED)
    else:
        dr.rectangle([x_of(lo), cy - BH // 2, x_of(hi), cy + BH // 2], fill=BAR)
        dr.text((x_of(hi) + 12 * S, cy - 10 * S), nf(hi), font=fnt(17 * S, True), fill=INK)

dr.line([(PLOT_L, T - 8 * S), (PLOT_L, BOT)], fill=(200, 197, 190), width=1 * S)
dr.line([(PLOT_L, BOT), (W * S - R, BOT)], fill=(200, 197, 190), width=1 * S)

os.makedirs(os.path.join(HERE, 'assets'), exist_ok=True)
img.resize((W, H), Image.LANCZOS).save(os.path.join(HERE, 'assets', 'kp_chart.png'))
print('assets/kp_chart.png', (W, H))
for n, _, _, _, _, lo, hi in ROWS:
    print(f'  {n:22s} {nf(lo):>10s} – {nf(hi):>10s}')
