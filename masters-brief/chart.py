"""Grouped horizontal bar chart: ₽/м² «как есть» vs приведённая к базе «без отделки».

Palette #4A7BC8 / #B9822F validated with dataviz/scripts/validate_palette.js
(light surface, categorical, 2 slots): all six checks PASS.
"""
import json
from PIL import Image, ImageDraw, ImageFont

F  = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
FB = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
fnt = lambda s, b=False: ImageFont.truetype(FB if b else F, s)

S = 2                                   # device pixel ratio
W, H = 1400 * S, 700 * S
SURFACE = (252, 252, 251)
INK, MUTED, GRID = (34, 40, 52), (112, 120, 134), (226, 223, 216)
C_ASIS, C_ADJ = (74, 123, 200), (185, 130, 47)   # validated categorical pair
BANDBG = (245, 242, 237)

D = json.load(open('cmp.json'))['R']
ORDER = sorted(D, key=lambda k: -D[k]['asis'])
SUB = {'МАСТЕРС': 'первичка · без отделки · 2029',
       'Прайм Парк': 'первичка + вторичка · сдан',
       'Династия': 'вторичка · сдан',
       'Царская площадь': 'вторичка · сдан',
       'Лица': 'вторичка · сдан',
       'Лайнер': 'вторичка · сдан'}

img = Image.new('RGB', (W, H), SURFACE)
dr = ImageDraw.Draw(img)

L, R, T = 300 * S, 70 * S, 128 * S       # plot margins
B = H - 104 * S
PW = W - L - R
MAXV = 1_000_000
x_of = lambda v: L + PW * v / MAXV
nf = lambda v: f'{v:,.0f}'.replace(',', ' ')

# title + subtitle
dr.text((44 * S, 30 * S), 'Средняя цена предложения, ₽/м²', font=fnt(25 * S, True), fill=INK)
dr.text((44 * S, 64 * S),
        'Сопоставимая выборка: квартиры 40–140 м², Циан, 28.07.2026',
        font=fnt(17 * S), fill=MUTED)

# legend (always present for 2 series)
lx, ly = W - R - 430 * S, 34 * S
for i, (c, lab) in enumerate([(C_ASIS, 'Цена экспозиции, как есть'),
                              (C_ADJ,  'Приведённая к базе «без отделки»')]):
    dr.rounded_rectangle([lx, ly + i * 30 * S, lx + 26 * S, ly + 15 * S + i * 30 * S], 4 * S, fill=c)
    dr.text((lx + 38 * S, ly - 3 * S + i * 30 * S), lab, font=fnt(17 * S), fill=MUTED)

# recessive gridlines
for v in range(0, MAXV + 1, 200_000):
    gx = x_of(v)
    dr.line([gx, T, gx, B], fill=GRID, width=1 * S)
    lab = nf(v) if v else '0'
    lw = dr.textbbox((0, 0), lab, font=fnt(15 * S))[2]
    dr.text((min(gx - lw / 2, W - 44 * S - lw), B + 16 * S), lab, font=fnt(15 * S), fill=MUTED)

ROW = (B - T) / len(ORDER)
BAR, GAP = 26 * S, 2 * S                 # thin marks, 2px surface gap between fills

for i, name in enumerate(ORDER):
    d = D[name]
    cy = T + ROW * (i + 0.5)
    if name == 'МАСТЕРС':                # emphasis on the subject entity, not on rank
        dr.rounded_rectangle([44 * S, cy - ROW / 2 + 4 * S, W - 44 * S, cy + ROW / 2 - 4 * S],
                             8 * S, fill=BANDBG)
    dr.text((44 * S, cy - 26 * S), name, font=fnt(20 * S, name == 'МАСТЕРС'), fill=INK)
    dr.text((44 * S, cy + 2 * S), SUB[name], font=fnt(15 * S), fill=MUTED)
    dr.text((44 * S, cy + 24 * S), f'{d["n"]} лотов', font=fnt(15 * S), fill=MUTED)

    for j, (val, col) in enumerate([(d['asis'], C_ASIS), (d['adj'], C_ADJ)]):
        y0 = cy - BAR - GAP / 2 + j * (BAR + GAP)
        dr.rounded_rectangle([L, y0, x_of(val), y0 + BAR], 4 * S, fill=col)
        dr.rectangle([L, y0, L + 6 * S, y0 + BAR], fill=col)     # anchor to baseline
        dr.text((x_of(val) + 12 * S, y0 + 3 * S), nf(val), font=fnt(17 * S, True), fill=INK)

dr.line([L, T, L, B], fill=(190, 186, 178), width=1 * S)
dr.text((44 * S, H - 52 * S),
        'Приведение: из лотов с ремонтом вычтено 120 тыс. ₽/м², из white box / чистовой — 18 тыс. ₽/м². '
        'Расчёт по лотам с определённым уровнем отделки.',
        font=fnt(15 * S), fill=MUTED)

img.save('chart_prices.png', quality=95)
print('saved', img.size)
