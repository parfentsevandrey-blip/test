# -*- coding: utf-8 -*-
"""Два графика.

`chart_cohort.png` — цена метра по домам в двух километрах вокруг участка:
полоса от самого дешёвого лота группы к самому дорогому, засечка — медиана.
`chart_hist.png` — распределение всех лотов когорты по цене метра: видно,
где стоит основная масса предложения.

Данные — выдача Циан, срез 22.09.2026 (см. mg_data.py).
"""
import os
from PIL import Image, ImageDraw, ImageFont

from mg_data import COH_PINS, COH, _coh, MKT_PREM, PPM_LOCAL
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
ROWS = COH_PINS
MAXV = 2_000_000
W, H = 1400, 760
img = Image.new('RGB', (W * S, H * S), SURFACE)
dr = ImageDraw.Draw(img)
L, R, T = 40 * S, 118 * S, 118 * S
PLOT_L = L + 330 * S
PLOT_W = W * S - R - PLOT_L
x_of = lambda v: PLOT_L + PLOT_W * min(v, MAXV) / MAXV

dr.text((L, 30 * S), 'Цена метра в двух километрах вокруг участка', font=fnt(23 * S, True), fill=INK)
dr.text((L, 66 * S), 'Полоса — от дешёвого лота группы к дорогому, засечка — медиана. '
        'Бронзовое — строится, тёмное — готово', font=fnt(14 * S), fill=MUTED)

BOT = H * S - 58 * S
for g in range(0, MAXV + 1, 250_000):
    x = x_of(g)
    dr.line([(x, T - 8 * S), (x, BOT)], fill=GRID, width=1 * S)
    lab = f'{g / 1e6:.2f}'.replace('.', ',')
    dr.text((x - dr.textlength(lab, font=fnt(12 * S)) / 2, BOT + 10 * S), lab,
            font=fnt(13 * S), fill=MUTED)
dr.text((PLOT_L + PLOT_W - 62 * S, BOT + 30 * S), 'млн ₽ за м²', font=fnt(13 * S), fill=MUTED)

xp = x_of(PPM_LOCAL)
for y in range(int(T - 8 * S), int(BOT), 12 * S):
    dr.line([(xp, y), (xp, y + 6 * S)], fill=DASH, width=2 * S)
dr.text((xp + 8 * S, T - 30 * S), f'медиана стройки вокруг, {nf(round(PPM_LOCAL))} ₽',
        font=fnt(13 * S), fill=DASH)

rowh = (BOT - T) / len(ROWS)
BH = int(rowh * 0.46)
for i, r in enumerate(ROWS):
    cy = int(T + rowh * i + rowh / 2)
    dr.text((L, cy - 19 * S), r['short'], font=fnt(17 * S), fill=INK)
    dr.text((L, cy + 4 * S), f"{r['dist']} м · {r['what']} · {plural(r['n'])}",
            font=fnt(12 * S), fill=MUTED)
    fill = BRONZE if r['what'] == 'строится' else NAVY
    dr.rectangle([x_of(r['ppmLo']), cy - BH // 2, x_of(r['ppmHi']), cy + BH // 2], fill=fill)
    xm = x_of(r['ppmMed'])
    dr.line([(xm, cy - BH // 2 - 3 * S), (xm, cy + BH // 2 + 3 * S)], fill=SURFACE, width=3 * S)
    over = r['ppmHi'] > MAXV
    dr.text((x_of(min(r['ppmHi'], MAXV)) + 12 * S, cy - 9 * S),
            nf(round(r['ppmMed'])) + (' ›' if over else ''), font=fnt(16 * S, True), fill=INK)

dr.line([(PLOT_L, T - 8 * S), (PLOT_L, BOT)], fill=(200, 197, 190), width=1 * S)
dr.line([(PLOT_L, BOT), (W * S - R, BOT)], fill=(200, 197, 190), width=1 * S)
img.resize((W, H), Image.LANCZOS).save(os.path.join(HERE, 'assets', 'chart_cohort.png'))
print('assets/chart_cohort.png', (W, H))

# ── распределение лотов ────────────────────────────────────────────────────
STEP = 100_000
TOP = 1_800_000
bins = {}
for l in _coh:
    b = min(int(l['ppm'] // STEP) * STEP, TOP)
    bins[b] = bins.get(b, 0) + 1
keys = sorted(bins)
W2, H2 = 1400, 560
img = Image.new('RGB', (W2 * S, H2 * S), SURFACE)
dr = ImageDraw.Draw(img)
L2, T2 = 60 * S, 132 * S
BOT2 = H2 * S - 76 * S
PLOT_W2 = W2 * S - 100 * S - L2
maxn = max(bins.values())

dr.text((L2 - 20 * S, 30 * S), 'Сколько лотов в каком ценовом коридоре',
        font=fnt(23 * S, True), fill=INK)
dr.text((L2 - 20 * S, 66 * S),
        f"{COH['total']} лотов в продаже в двух километрах вокруг участка, шаг — 100 тыс. ₽ за метр. "
        f"Бронзовое — дороже медианы строящегося предложения",
        font=fnt(14 * S), fill=MUTED)

bw = PLOT_W2 / len(keys)
for i, k in enumerate(keys):
    n = bins[k]
    h = (BOT2 - T2) * n / maxn
    x0 = L2 + bw * i + bw * 0.12
    x1 = L2 + bw * (i + 1) - bw * 0.12
    col = BRONZE if k >= int(PPM_LOCAL // STEP) * STEP else NAVY
    dr.rectangle([x0, BOT2 - h, x1, BOT2], fill=col)
    dr.text(((x0 + x1) / 2 - dr.textlength(str(n), font=fnt(14 * S, True)) / 2,
             BOT2 - h - 26 * S), str(n), font=fnt(14 * S, True), fill=col)
    lab = f'{k / 1e6:.1f}'.replace('.', ',') + ('+' if k == TOP else '')
    dr.text(((x0 + x1) / 2 - dr.textlength(lab, font=fnt(13 * S)) / 2, BOT2 + 12 * S),
            lab, font=fnt(13 * S), fill=MUTED)

xm = L2 + bw * (sorted(keys).index(min(int(med([l['ppm'] for l in _coh]) // STEP) * STEP, TOP)) + 0.5)
dr.line([(xm, T2 - 20 * S), (xm, BOT2)], fill=RED, width=2 * S)
dr.text((xm + 10 * S, T2 - 62 * S), f"медиана когорты {COH['med']} ₽",
        font=fnt(14 * S, True), fill=RED)

dr.text((L2 + PLOT_W2 - 62 * S, BOT2 + 34 * S), 'млн ₽ за м²', font=fnt(13 * S), fill=MUTED)
dr.line([(L2, BOT2), (L2 + PLOT_W2, BOT2)], fill=(200, 197, 190), width=1 * S)
img.resize((W2, H2), Image.LANCZOS).save(os.path.join(HERE, 'assets', 'chart_hist.png'))
print('assets/chart_hist.png', (W2, H2))
