"""Из чего складывается цена: квартира этажом ниже + четыре этажа.

Вместо процентов и индексов — рубли: сколько стоит такая же квартира на
втором этаже, сколько к ней добавляют четыре этажа по ценам соседних
домов и сколько остаётся необъяснённым.
Палитра #4A7BC8 / #B9822F / #B3282D — как в остальных иллюстрациях.
"""
import json
from PIL import Image, ImageDraw, ImageFont

F  = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
FB = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
fnt = lambda s, b=False: ImageFont.truetype(FB if b else F, s)

S = 2
W, H = 1000 * S, 400 * S
SURFACE, INK, MUTED, GRID = (252, 252, 251), (34, 40, 52), (112, 120, 134), (226, 223, 216)
BLUE, BRONZE, BRONZE_L, RED, BANDBG = (74, 123, 200), (185, 130, 47), (226, 201, 156), (179, 40, 45), (245, 242, 237)

C = json.load(open('k12_tables.json'))['floor']['_calc']
TWIN, ASK = 133.0, 162.5
LO, HI = [f / 1e6 for f in C['fair']][0], [f / 1e6 for f in C['fair']][2]   # 144,5 и 150,0

img = Image.new('RGB', (W, H), SURFACE)
dr = ImageDraw.Draw(img)
L, R, T = 300 * S, 170 * S, 150 * S
B = H - 74 * S
PW, MAXV = W - L - R, 175.0
x_of = lambda v: L + PW * v / MAXV
d1 = lambda v: f'{v:.1f}'.replace('.', ',')

dr.text((44 * S, 26 * S), 'Из чего складывается цена, млн ₽', font=fnt(25 * S, True), fill=INK)
dr.text((44 * S, 62 * S), 'Такая же квартира в том же доме на втором этаже стоит 133,0 млн ₽.',
        font=fnt(17 * S), fill=MUTED)
dr.text((44 * S, 88 * S), 'Сколько к ней добавляют четыре этажа — посчитано по ценам соседних домов.',
        font=fnt(17 * S), fill=MUTED)
dr.text((44 * S, 116 * S), 'Разница между лотами — только этаж.', font=fnt(17 * S, True), fill=MUTED)

for v in range(0, 151, 30):
    dr.line([x_of(v), T, x_of(v), B], fill=GRID, width=1 * S)
    dr.text((x_of(v) - 9 * S, B + 14 * S), str(v), font=fnt(15 * S), fill=MUTED)

ROW, BAR = (B - T) / 3, 40 * S
def row(i, name, sub, bold=False, band=False):
    cy = T + ROW * (i + 0.5)
    if band:
        dr.rounded_rectangle([44 * S, cy - ROW / 2 + 6 * S, W - 44 * S, cy + ROW / 2 - 6 * S], 8 * S, fill=BANDBG)
    dr.text((44 * S, cy - 24 * S), name, font=fnt(19 * S, bold), fill=INK)
    dr.text((44 * S, cy + 4 * S), sub, font=fnt(15 * S), fill=MUTED)
    return cy, cy - BAR / 2

# 1 — квартира этажом ниже
cy, y0 = row(0, 'Квартира этажом ниже', 'те же 95 м², тот же ремонт')
dr.rounded_rectangle([L, y0, x_of(TWIN), y0 + BAR], 5 * S, fill=BLUE)
dr.rectangle([L, y0, L + 7 * S, y0 + BAR], fill=BLUE)
dr.text((x_of(TWIN) + 14 * S, y0 + 8 * S), d1(TWIN), font=fnt(20 * S, True), fill=INK)

# 2 — она же плюс четыре этажа по рынку
cy, y0 = row(1, 'Она же + четыре этажа', 'по ценам соседних домов')
dr.rounded_rectangle([L, y0, x_of(TWIN), y0 + BAR], 5 * S, fill=BLUE)
dr.rectangle([L, y0, L + 7 * S, y0 + BAR], fill=BLUE)
dr.rectangle([x_of(TWIN) + 2 * S, y0, x_of(LO), y0 + BAR], fill=BRONZE)
dr.rounded_rectangle([x_of(LO), y0, x_of(HI), y0 + BAR], 5 * S, fill=BRONZE_L)
dr.text((x_of(HI) + 14 * S, y0 + 8 * S), f'{d1(LO)} – {d1(HI)}', font=fnt(20 * S, True), fill=INK)

# 3 — цена нашего лота
cy, y0 = row(2, 'НАША КВАРТИРА', '95 м², 6 этаж', bold=True, band=True)
dr.rectangle([L, y0, x_of(HI), y0 + BAR], fill=(196, 208, 228))
dr.rounded_rectangle([L, y0, x_of(HI), y0 + BAR], 5 * S, fill=(196, 208, 228))
dr.rounded_rectangle([x_of(HI) + 2 * S, y0, x_of(ASK), y0 + BAR], 5 * S, fill=RED)
dr.rectangle([x_of(HI) + 2 * S, y0, x_of(HI) + 9 * S, y0 + BAR], fill=RED)
dr.text((x_of(ASK) + 14 * S, y0 + 8 * S), d1(ASK), font=fnt(20 * S, True), fill=INK)

ann = f'сверх рынка: {d1(ASK - HI)}–{d1(ASK - LO)} млн ₽'
aw = dr.textbbox((0, 0), ann, font=fnt(16 * S, True))[2]
dr.text((x_of(HI) - 22 * S - aw, y0 + 11 * S), ann, font=fnt(16 * S, True), fill=(255, 255, 255))

lx, ly = 44 * S, H - 34 * S
for lab, c in [('цена квартиры на 2 этаже', BLUE), ('надбавка за 4 этажа', BRONZE), ('сверх рынка', RED)]:
    dr.rounded_rectangle([lx, ly, lx + 24 * S, ly + 14 * S], 4 * S, fill=c)
    dr.text((lx + 34 * S, ly - 3 * S), lab, font=fnt(16 * S), fill=MUTED)
    lx += 34 * S + dr.textbbox((0, 0), lab, font=fnt(16 * S))[2] + 30 * S

dr.line([L, T, L, B], fill=(190, 186, 178), width=1 * S)
img.save('k12_floor.png', quality=95)
print('saved', img.size, f'{LO:.1f}–{HI:.1f}')
