"""Надбавка за этаж: рыночный градиент против запроса по нашему лоту.

Палитра #4A7BC8 / #B3282D — та же, что в остальных иллюстрациях справки.
"""
import json
from PIL import Image, ImageDraw, ImageFont

F  = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
FB = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
fnt = lambda s, b=False: ImageFont.truetype(FB if b else F, s)

S = 2
W, H = 1000 * S, 430 * S
SURFACE, INK, MUTED, GRID = (252, 252, 251), (34, 40, 52), (112, 120, 134), (226, 223, 216)
BLUE, RED, BAND = (74, 123, 200), (179, 40, 45), (206, 219, 240)

K = json.load(open('k12_tables.json'))['floor']
G_LO, G_MID, G_HI = K['Веспер Кутузовский']['per'], K['Малоэтажные вместе']['per'], K['Бадаевский']['per']
ASK = (1 + K['_calc']['ask']) * 100
F0, F1, OURF = 2, 11, 6

img = Image.new('RGB', (W, H), SURFACE)
dr = ImageDraw.Draw(img)
L, R, T, B = 104 * S, 56 * S, 176 * S, H - 76 * S
PW, PH = W - L - R, B - T
YMIN, YMAX = 96, 128
x_of = lambda f: L + PW * (f - F0) / (F1 - F0)
y_of = lambda v: B - PH * (v - YMIN) / (YMAX - YMIN)
curve = lambda g: [(x_of(f), y_of(100 * (1 + g / 100) ** (f - F0))) for f in range(F0, F1 + 1)]

dr.text((44 * S, 26 * S), 'Надбавка за этаж: сколько платит рынок и сколько просит лот',
        font=fnt(25 * S, True), fill=INK)
dr.text((44 * S, 62 * S), 'Цена метра относительно 2-го этажа, %. Рыночный градиент — регрессия по 232 лотам',
        font=fnt(17 * S), fill=MUTED)
dr.text((44 * S, 88 * S), '«Веспера Кутузовского» и «Бадаевского»: дома до 19 этажей, один застройщик, все без отделки.',
        font=fnt(17 * S), fill=MUTED)

lx, ly = 44 * S, 128 * S
for lab, c in [(f'Рынок: +{G_LO:.1f}…+{G_HI:.1f} % за этаж'.replace('.', ','), BAND),
               ('Наш дом: те же 95 м² с тем же ремонтом', RED)]:
    dr.rounded_rectangle([lx, ly, lx + 26 * S, ly + 15 * S], 4 * S, fill=c)
    dr.text((lx + 36 * S, ly - 3 * S), lab, font=fnt(17 * S), fill=MUTED)
    lx += 40 * S + dr.textbbox((0, 0), lab, font=fnt(17 * S))[2] + 26 * S

for v in range(100, YMAX + 1, 5):
    dr.line([L, y_of(v), W - R, y_of(v)], fill=GRID, width=1 * S)
    dr.text((44 * S, y_of(v) - 11 * S), f'{v} %', font=fnt(15 * S), fill=MUTED)
for f in range(F0, F1 + 1):
    dr.text((x_of(f) - 5 * S, B + 16 * S), str(f), font=fnt(15 * S), fill=MUTED)
dr.text((W // 2 - 34 * S, B + 46 * S), 'этаж', font=fnt(16 * S), fill=MUTED)

hi, lo = curve(G_HI), curve(G_LO)
dr.polygon(hi + lo[::-1], fill=BAND)
dr.line(curve(G_MID), fill=BLUE, width=3 * S)
dr.line([L, y_of(100), W - R, y_of(100)], fill=(190, 186, 178), width=1 * S)

mid6 = 100 * (1 + G_MID / 100) ** (OURF - F0)
gx = x_of(OURF)
dr.line([gx, y_of(mid6), gx, y_of(ASK)], fill=RED, width=2 * S)
for i in range(int(y_of(ASK)), int(y_of(mid6)), 14 * S):       # пунктир к рынку
    dr.line([gx - 9 * S, i, gx + 9 * S, i], fill=RED, width=1 * S)
dr.line([x_of(F0), y_of(100), gx, y_of(ASK)], fill=RED, width=3 * S)
for f, v, lab in [(F0, 100, '133,0 млн ₽ · 2 этаж'), (OURF, ASK, '162,5 млн ₽ · 6 этаж')]:
    x, y = x_of(f), y_of(v)
    dr.ellipse([x - 9 * S, y - 9 * S, x + 9 * S, y + 9 * S], fill=RED, outline=(255, 255, 255), width=3 * S)
    off = 14 * S if f == F0 else -52 * S
    dr.text((x + 18 * S, y + off), lab, font=fnt(18 * S, True), fill=INK)

box = f'рынок за 4 этажа: +{mid6 - 100:.1f} %'.replace('.', ',')
ask = f'лот просит: +{ASK - 100:.1f} %'.replace('.', ',')
bx, by = gx + 26 * S, y_of((ASK + mid6) / 2) - 26 * S
dr.rounded_rectangle([bx, by, bx + 330 * S, by + 62 * S], 8 * S, fill=(255, 255, 255), outline=GRID, width=1 * S)
dr.text((bx + 14 * S, by + 8 * S), box, font=fnt(17 * S), fill=MUTED)
dr.text((bx + 14 * S, by + 32 * S), ask, font=fnt(17 * S, True), fill=RED)

dr.line([L, T, L, B], fill=(190, 186, 178), width=1 * S)
img.save('k12_floor.png', quality=95)
print('saved', img.size)
