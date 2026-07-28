"""Cost of one move-in-ready square metre, ₽/м².

Competitors: the price of lots that already have a renovation.
MASTERS: the developer's price plus an allowance for the fit-out it doesn't include —
shown as two stacked segments so the composition is visible.

Palette #4A7BC8 / #B9822F validated with dataviz/scripts/validate_palette.js
(light surface, categorical, 2 slots): all six checks PASS.
"""
import json
from PIL import Image, ImageDraw, ImageFont

F  = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
FB = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
fnt = lambda s, b=False: ImageFont.truetype(FB if b else F, s)

S = 2
W, H = 1400 * S, 660 * S
SURFACE = (252, 252, 251)
INK, MUTED, GRID = (34, 40, 52), (112, 120, 134), (226, 223, 216)
C_FLAT, C_FIT = (74, 123, 200), (185, 130, 47)      # validated categorical pair
BANDBG = (245, 242, 237)

FIT = 120_000                                        # оценка отделки, ₽/м²
FIN = json.load(open('cmp.json'))['FIN']
MASTERS = json.load(open('cmp.json'))['R']['МАСТЕРС']['asis']

ROWS = [('Прайм Парк',      FIN['Прайм Парк']['ren'][1],      0,   'готово сейчас · ремонт'),
        ('МАСТЕРС',         MASTERS,                          FIT, 'ключи в 2029 · ремонт свой'),
        ('Династия',        FIN['Династия']['ren'][1],        0,   'готово сейчас · ремонт'),
        ('Царская площадь', FIN['Царская площадь']['ren'][1], 0,   'готово сейчас · ремонт'),
        ('Лица',            FIN['Лица']['ren'][1],            0,   'готово сейчас · ремонт'),
        ('Лайнер',          FIN['Лайнер']['ren'][1],          0,   'готово сейчас · ремонт')]
ROWS.sort(key=lambda r: -(r[1] + r[2]))

img = Image.new('RGB', (W, H), SURFACE)
dr = ImageDraw.Draw(img)
L, R, T = 300 * S, 90 * S, 178 * S
B = H - 96 * S
PW = W - L - R
MAXV = 1_200_000
x_of = lambda v: L + PW * v / MAXV
nf = lambda v: f'{v:,.0f}'.replace(',', ' ')

dr.text((44 * S, 26 * S), 'Сколько стоит метр квартиры, в которую можно заехать, ₽/м²',
        font=fnt(25 * S, True), fill=INK)
dr.text((44 * S, 62 * S),
        'У конкурентов — цена лотов, которые уже продаются с ремонтом. У МАСТЕРС — цена застройщика',
        font=fnt(17 * S), fill=MUTED)
dr.text((44 * S, 88 * S),
        'плюс отделка, которой в цене нет. Сопоставимая выборка 40–140 м², Циан, 28.07.2026.',
        font=fnt(17 * S), fill=MUTED)

lx, ly = 44 * S, 128 * S
for lab, c in [('Цена квартиры', C_FLAT), (f'Отделка (оценка {FIT // 1000} тыс. ₽/м²)', C_FIT)]:
    dr.rounded_rectangle([lx, ly, lx + 26 * S, ly + 15 * S], 4 * S, fill=c)
    dr.text((lx + 36 * S, ly - 3 * S), lab, font=fnt(17 * S), fill=MUTED)
    lx += 40 * S + dr.textbbox((0, 0), lab, font=fnt(17 * S))[2] + 26 * S

for v in range(0, MAXV + 1, 200_000):
    gx = x_of(v)
    dr.line([gx, T, gx, B], fill=GRID, width=1 * S)
    lab = nf(v) if v else '0'
    lw = dr.textbbox((0, 0), lab, font=fnt(15 * S))[2]
    dr.text((min(gx - lw / 2, W - 44 * S - lw), B + 16 * S), lab, font=fnt(15 * S), fill=MUTED)

ROW = (B - T) / len(ROWS)
BAR = 34 * S
for i, (name, base, fit, sub) in enumerate(ROWS):
    cy = T + ROW * (i + 0.5)
    if name == 'МАСТЕРС':
        dr.rounded_rectangle([44 * S, cy - ROW / 2 + 5 * S, W - 44 * S, cy + ROW / 2 - 5 * S],
                             8 * S, fill=BANDBG)
    dr.text((44 * S, cy - 22 * S), name, font=fnt(21 * S, name == 'МАСТЕРС'), fill=INK)
    dr.text((44 * S, cy + 4 * S), sub, font=fnt(15 * S), fill=MUTED)

    y0 = cy - BAR / 2
    dr.rounded_rectangle([L, y0, x_of(base), y0 + BAR], 4 * S, fill=C_FLAT)
    dr.rectangle([L, y0, L + 6 * S, y0 + BAR], fill=C_FLAT)
    if fit:
        x1 = x_of(base) + 2 * S                       # 2px surface gap between fills
        dr.rounded_rectangle([x1, y0, x_of(base + fit), y0 + BAR], 4 * S, fill=C_FIT)
        dr.rectangle([x1, y0, x1 + 6 * S, y0 + BAR], fill=C_FIT)
        dr.text((x_of(base + fit) + 14 * S, y0 + 1 * S), nf(base + fit), font=fnt(19 * S, True), fill=INK)
        dr.text((x_of(base + fit) + 14 * S, y0 + 22 * S), f'{nf(base)} + {nf(fit)}',
                font=fnt(14 * S), fill=MUTED)
    else:
        dr.text((x_of(base) + 14 * S, y0 + 8 * S), nf(base), font=fnt(19 * S, True), fill=INK)

dr.line([L, T, L, B], fill=(190, 186, 178), width=1 * S)
img.save('chart_prices.png', quality=95)
print('saved', img.size)
