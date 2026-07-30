"""Сколько стоит метр квартиры, в которую можно заехать, ₽/м².

У нашего лота отделки нет, поэтому она добавляется к нашей цене.
Палитра #4A7BC8 / #B9822F проверена dataviz/scripts/validate_palette.js.
"""
import json
from PIL import Image, ImageDraw, ImageFont

F  = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
FB = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
fnt = lambda s, b=False: ImageFont.truetype(FB if b else F, s)

S = 2
W, H = 1400 * S, 700 * S
SURFACE, INK, MUTED, GRID = (252, 252, 251), (34, 40, 52), (112, 120, 134), (226, 223, 216)
C_FLAT, C_FIT, BANDBG = (74, 123, 200), (185, 130, 47), (245, 242, 237)

D = json.load(open('lev/lv_tables.json'))
FIT, OUR = D['fit'], D['our']
ROWS = [('НАШ ЛОТ', OUR['ppm'], FIT, '79,5 м², 15 этаж · бетон + отделка')]
for n, (b, f, sub) in D['ready'].items():
    if n == 'Левел Академическая':
        continue
    ROWS.append((n, b, f, sub))
ROWS.sort(key=lambda r: -(r[1] + r[2]))

img = Image.new('RGB', (W, H), SURFACE); dr = ImageDraw.Draw(img)
L, R, T = 360 * S, 90 * S, 178 * S
B = H - 96 * S
PW, MAXV = W - L - R, 1_300_000
x_of = lambda v: L + PW * v / MAXV
nf = lambda v: f'{v:,.0f}'.replace(',', ' ')

dr.text((44 * S, 26 * S), 'Сколько стоит метр квартиры, в которую можно заехать, ₽/м²', font=fnt(25 * S, True), fill=INK)
dr.text((44 * S, 62 * S), 'Наш лот продаётся в бетоне, поэтому к его цене добавлена отделка. У соседей взяты лоты,', font=fnt(17 * S), fill=MUTED)
dr.text((44 * S, 88 * S), 'которые уже продаются с ремонтом. Сопоставимая выборка 65–95 м², Циан, 30.07.2026.', font=fnt(17 * S), fill=MUTED)

lx, ly = 44 * S, 128 * S
for lab, c in [('Цена квартиры', C_FLAT), (f'Отделка (оценка {FIT // 1000} тыс. ₽/м²)', C_FIT)]:
    dr.rounded_rectangle([lx, ly, lx + 26 * S, ly + 15 * S], 4 * S, fill=c)
    dr.text((lx + 36 * S, ly - 3 * S), lab, font=fnt(17 * S), fill=MUTED)
    lx += 40 * S + dr.textbbox((0, 0), lab, font=fnt(17 * S))[2] + 26 * S

for v in range(0, MAXV + 1, 200_000):
    dr.line([x_of(v), T, x_of(v), B], fill=GRID, width=1 * S)
    lab = nf(v) if v else '0'
    lw = dr.textbbox((0, 0), lab, font=fnt(15 * S))[2]
    dr.text((min(x_of(v) - lw / 2, W - 44 * S - lw), B + 16 * S), lab, font=fnt(15 * S), fill=MUTED)

ROW, BAR = (B - T) / len(ROWS), 32 * S
for i, (name, base, fit, sub) in enumerate(ROWS):
    cy = T + ROW * (i + 0.5); our = name == 'НАШ ЛОТ'
    if our: dr.rounded_rectangle([44 * S, cy - ROW / 2 + 5 * S, W - 44 * S, cy + ROW / 2 - 5 * S], 8 * S, fill=BANDBG)
    dr.text((44 * S, cy - 22 * S), name, font=fnt(20 * S, our), fill=INK)
    dr.text((44 * S, cy + 4 * S), sub, font=fnt(15 * S), fill=MUTED)
    y0 = cy - BAR / 2
    dr.rounded_rectangle([L, y0, x_of(base), y0 + BAR], 4 * S, fill=C_FLAT)
    dr.rectangle([L, y0, L + 6 * S, y0 + BAR], fill=C_FLAT)
    if fit:
        x1 = x_of(base) + 2 * S
        dr.rounded_rectangle([x1, y0, x_of(base + fit), y0 + BAR], 4 * S, fill=C_FIT)
        dr.rectangle([x1, y0, x1 + 6 * S, y0 + BAR], fill=C_FIT)
        dr.text((x_of(base + fit) + 14 * S, y0 - 1 * S), nf(base + fit), font=fnt(19 * S, True), fill=INK)
        dr.text((x_of(base + fit) + 14 * S, y0 + 20 * S), f'{nf(base)} + {nf(fit)}', font=fnt(14 * S), fill=MUTED)
    else:
        dr.text((x_of(base) + 14 * S, y0 + 6 * S), nf(base), font=fnt(19 * S, True), fill=INK)

dr.line([L, T, L, B], fill=(190, 186, 178), width=1 * S)
img.save('lev/lv_chart.png', quality=95); print('saved', img.size, 643 / (W / H))
