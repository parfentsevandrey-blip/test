"""Grouped bars: ₽/м² by room type across the three primary-market projects.

Palette #4A7BC8 / #B9822F / #9B5DA6 validated with dataviz/scripts/validate_palette.js
(light surface, categorical, 3 slots): all six checks PASS.
"""
import json
from PIL import Image, ImageDraw, ImageFont
F  = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
FB = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
fnt = lambda s, b=False: ImageFont.truetype(FB if b else F, s)

S = 2
W, H = 1400 * S, 560 * S
SURFACE = (252, 252, 251)
INK, MUTED, GRID = (34, 40, 52), (112, 120, 134), (226, 223, 216)
SERIES = [('МАСТЕРС', (74, 123, 200)), ('Муза', (155, 93, 166)), ('Дом на Часовой', (185, 130, 47))]

A = json.load(open('all_lots.json'))
w = lambda ls: sum(x['price'] for x in ls) / sum(x['area'] for x in ls) if ls else None
GROUPS = [('Студии', 'Студия'), ('1-комнатные', '1'), ('2-комнатные', '2'), ('3-комнатные', '3')]

img = Image.new('RGB', (W, H), SURFACE); dr = ImageDraw.Draw(img)
L, R, T = 232 * S, 70 * S, 152 * S
B = H - 74 * S
PW = W - L - R
MAXV = 1_200_000
x_of = lambda v: L + PW * v / MAXV
nf = lambda v: f'{v:,.0f}'.replace(',', ' ')

dr.text((44 * S, 26 * S), 'Цена по типам квартир на первичном рынке, ₽/м²', font=fnt(25 * S, True), fill=INK)
dr.text((44 * S, 60 * S), 'Все три проекта — только от застройщика, все лоты без отделки. Циан, 28.07.2026',
        font=fnt(17 * S), fill=MUTED)

lx, ly = 44 * S, 100 * S
for i, (lab, c) in enumerate(SERIES):
    dr.rounded_rectangle([lx, ly, lx + 26 * S, ly + 15 * S], 4 * S, fill=c)
    dr.text((lx + 36 * S, ly - 3 * S), lab, font=fnt(17 * S), fill=MUTED)
    lx += 40 * S + dr.textbbox((0, 0), lab, font=fnt(17 * S))[2] + 26 * S

for v in range(0, MAXV + 1, 200_000):
    gx = x_of(v)
    dr.line([gx, T, gx, B], fill=GRID, width=1 * S)
    lab = nf(v) if v else '0'
    lw = dr.textbbox((0, 0), lab, font=fnt(15 * S))[2]
    dr.text((min(gx - lw / 2, W - 44 * S - lw), B + 14 * S), lab, font=fnt(15 * S), fill=MUTED)

ROW = (B - T) / len(GROUPS)
BAR, GAP = 22 * S, 2 * S
for i, (title, cat) in enumerate(GROUPS):
    cy = T + ROW * (i + 0.5)
    dr.text((44 * S, cy - 10 * S), title, font=fnt(20 * S, True), fill=INK)
    for j, (name, col) in enumerate(SERIES):
        v = w([x for x in A[name] if x['cat'] == cat])
        y0 = cy - 1.5 * BAR - GAP + j * (BAR + GAP)
        if v is None:
            dr.text((L + 10 * S, y0 + 2 * S), 'нет в экспозиции', font=fnt(15 * S), fill=MUTED)
            continue
        dr.rounded_rectangle([L, y0, x_of(v), y0 + BAR], 4 * S, fill=col)
        dr.rectangle([L, y0, L + 6 * S, y0 + BAR], fill=col)
        dr.text((x_of(v) + 12 * S, y0 + 1 * S), nf(v), font=fnt(16 * S, True), fill=INK)

dr.line([L, T, L, B], fill=(190, 186, 178), width=1 * S)
img.save('chart_primary.png', quality=95)
print('saved', img.size)
