# -*- coding: utf-8 -*-
"""Два графика.

`chart_cohort.png` — цена метра в километре вокруг двух домов: полоса от
самого дешёвого лота группы к самому дорогому, засечка — медиана.
`chart_floor.png` — медиана метра по этажам в каждом доме: в обоих проектах
цена растёт с высотой, а от площади лота почти не зависит.

Данные — выдача Циан, срез 04.09.2026 (см. ac_data.py).
"""
import os
from PIL import Image, ImageDraw, ImageFont

from ac_data import COH_PINS, CLOS, LUCE, ALL, MKT_ELITE
from statistics import median as med

HERE = os.path.dirname(os.path.abspath(__file__))
F = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
FB = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
fnt = lambda s, b=False: ImageFont.truetype(FB if b else F, s)

S = 2
SURFACE, INK, MUTED, GRID = (252, 252, 251), (34, 40, 52), (112, 120, 134), (226, 223, 216)
BRONZE, NAVY, RED, DASH = (169, 118, 47), (47, 62, 94), (179, 40, 45), (150, 100, 40)
nf = lambda v: f'{v:,.0f}'.replace(',', ' ')


def plural(n, one='лот', few='лота', many='лотов'):
    a, b = abs(n) % 100, abs(n) % 10
    if 10 < a < 20:
        return f'{n} {many}'
    if 1 < b < 5:
        return f'{n} {few}'
    return f'{n} {one}' if b == 1 else f'{n} {many}'

os.makedirs(os.path.join(HERE, 'assets'), exist_ok=True)

# ── когорта ────────────────────────────────────────────────────────────────
ROWS = [r for r in COH_PINS][:16]
MAXV = 6_500_000
W, H = 1400, 820
img = Image.new('RGB', (W * S, H * S), SURFACE)
dr = ImageDraw.Draw(img)
L, R, T = 40 * S, 118 * S, 118 * S
PLOT_L = L + 360 * S
PLOT_W = W * S - R - PLOT_L
x_of = lambda v: PLOT_L + PLOT_W * min(v, MAXV) / MAXV

dr.text((L, 30 * S), 'Цена метра в километре вокруг двух домов', font=fnt(23 * S, True), fill=INK)
dr.text((L, 66 * S), 'Полоса — от дешёвого лота группы к дорогому, засечка — медиана. '
        'Бронзовое — строится, тёмное — готово', font=fnt(14 * S), fill=MUTED)

BOT = H * S - 58 * S
for g in range(0, MAXV + 1, 500_000):
    x = x_of(g)
    dr.line([(x, T - 8 * S), (x, BOT)], fill=GRID, width=1 * S)
    lab = f'{g / 1e6:.1f}'.replace('.', ',')
    dr.text((x - dr.textlength(lab, font=fnt(12 * S)) / 2, BOT + 10 * S), lab,
            font=fnt(13 * S), fill=MUTED)
dr.text((PLOT_L + PLOT_W - 62 * S, BOT + 30 * S), 'млн ₽ за м²', font=fnt(13 * S), fill=MUTED)

xp = x_of(MKT_ELITE)
for y in range(int(T - 8 * S), int(BOT), 12 * S):
    dr.line([(xp, y), (xp, y + 6 * S)], fill=DASH, width=2 * S)
dr.text((xp + 8 * S, T - 30 * S), 'элитный сегмент Москвы, 2,27 млн', font=fnt(13 * S), fill=DASH)

rowh = (BOT - T) / len(ROWS)
BH = int(rowh * 0.46)
for i, r in enumerate(ROWS):
    cy = int(T + rowh * i + rowh / 2)
    ours = r['ours']
    dr.text((L, cy - 19 * S), r['short'], font=fnt(17 * S, ours), fill=RED if ours else INK)
    dr.text((L, cy + 4 * S),
            f"{r['dist']} м · {r['what']} · {plural(r['n'])}",
            font=fnt(12 * S), fill=MUTED)
    fill = RED if ours else (BRONZE if r['what'] == 'строится' else NAVY)
    dr.rectangle([x_of(r['ppmLo']), cy - BH // 2, x_of(r['ppmHi']), cy + BH // 2], fill=fill)
    xm = x_of(r['ppmMed'])
    dr.line([(xm, cy - BH // 2 - 3 * S), (xm, cy + BH // 2 + 3 * S)], fill=SURFACE, width=3 * S)
    over = r['ppmHi'] > MAXV
    dr.text((x_of(r['ppmHi']) + 12 * S, cy - 9 * S), nf(round(r['ppmMed'])) + (' ›' if over else ''),
            font=fnt(16 * S, True), fill=RED if ours else INK)

dr.line([(PLOT_L, T - 8 * S), (PLOT_L, BOT)], fill=(200, 197, 190), width=1 * S)
dr.line([(PLOT_L, BOT), (W * S - R, BOT)], fill=(200, 197, 190), width=1 * S)
img.resize((W, H), Image.LANCZOS).save(os.path.join(HERE, 'assets', 'chart_cohort.png'))
print('assets/chart_cohort.png', (W, H))

# ── этажи ──────────────────────────────────────────────────────────────────
FLOORS = sorted({l['floor'] for l in ALL})
W2, H2 = 1400, 520
img = Image.new('RGB', (W2 * S, H2 * S), SURFACE)
dr = ImageDraw.Draw(img)
L, T2 = 40 * S, 124 * S
BOT2 = H2 * S - 74 * S
PLOT_L2 = L + 96 * S
PLOT_W2 = W2 * S - 150 * S - PLOT_L2
MAXF = 6_500_000
x2 = lambda v: PLOT_L2 + PLOT_W2 * min(v, MAXF) / MAXF

dr.text((L, 30 * S), 'Медиана метра по этажам', font=fnt(23 * S, True), fill=INK)
dr.text((L, 66 * S), 'Бронзовое — «Кло 17», тёмное — «Люче». Верхний лот «Люче» — пентхаус '
        'на шестом этаже', font=fnt(14 * S), fill=MUTED)

for g in range(0, MAXF + 1, 500_000):
    x = x2(g)
    dr.line([(x, T2 - 10 * S), (x, BOT2)], fill=GRID, width=1 * S)
    lab = f'{g / 1e6:.1f}'.replace('.', ',')
    dr.text((x - dr.textlength(lab, font=fnt(12 * S)) / 2, BOT2 + 10 * S), lab,
            font=fnt(13 * S), fill=MUTED)
dr.text((PLOT_L2 + PLOT_W2 - 62 * S, BOT2 + 30 * S), 'млн ₽ за м²', font=fnt(13 * S), fill=MUTED)

rowh = (BOT2 - T2) / len(FLOORS)
for i, fl in enumerate(FLOORS):
    top = T2 + rowh * i
    dr.text((L, top + rowh / 2 - 12 * S), f'{fl}-й этаж', font=fnt(16 * S, True), fill=INK)
    for j, (h, col) in enumerate(((CLOS, BRONZE), (LUCE, NAVY))):
        g = [l for l in h['lots'] if l['floor'] == fl]
        if not g:
            continue
        v = med(l['ppm'] for l in g)
        bh = int(rowh * 0.30)
        cy = int(top + rowh * (0.32 + 0.36 * j))
        dr.rectangle([PLOT_L2, cy - bh // 2, x2(v), cy + bh // 2], fill=col)
        dr.text((x2(v) + 12 * S, cy - 9 * S), f'{nf(round(v))}   ({len(g)})',
                font=fnt(15 * S, True), fill=col)

dr.line([(PLOT_L2, T2 - 10 * S), (PLOT_L2, BOT2)], fill=(200, 197, 190), width=1 * S)
dr.line([(PLOT_L2, BOT2), (W2 * S - 150 * S, BOT2)], fill=(200, 197, 190), width=1 * S)
img.resize((W2, H2), Image.LANCZOS).save(os.path.join(HERE, 'assets', 'chart_floor.png'))
print('assets/chart_floor.png', (W2, H2))
