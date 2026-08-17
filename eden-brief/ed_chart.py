"""Сколько стоит метр квартиры, в которую можно заехать.

EDEN и «Кло 17» продаются без отделки, поэтому к их цене метра добавлена
стоимость отделки де-люкс — 750 тыс. ₽/м². У остальных проектов квартиры
идут с отделкой, доплачивать нечего. Цифры по конкурентам — из выгрузок
Циан от 17.08.2026, цена EDEN — расчёт по старту продаж.
"""
import json, os
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))

F  = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
FB = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
fnt = lambda s, b=False: ImageFont.truetype(FB if b else F, s)

S = 2
W, H = 1400, 620
SURFACE, INK, MUTED, GRID = (252, 252, 251), (34, 40, 52), (112, 120, 134), (226, 223, 216)
C_FLAT, C_FIT, RED = (74, 123, 200), (185, 130, 47), (179, 40, 45)

K = json.load(open(os.path.join(HERE, 'ed_tables.json')))
FIT = K['priceStats']['fit']

#  название,                 цена метра, доплата за отделку, подпись
ROWS = [
    ('EDEN Private Residence', 4_400_000, FIT,     '22 резиденции · расчёт по старту продаж'),
    ('Брюсов',                 5_608_374, 0,       '4 лота · сдача в 2026, отделка от застройщика'),
    ('Stella di Mosca',        4_283_845, 0,       '3 лота · вторичка, дизайнерский ремонт'),
    ('Никитский-6',            3_194_829, 0,       '16 лотов · чистовая от застройщика'),
    ('Кло 17 (Clos 17)',       2_627_192, FIT,     '17 лотов · всё без отделки'),
    ('Фамильный дом Люче',     2_935_276, FIT / 26, '26 лотов · 25 с отделкой, 1 без'),
    ('Turandot Residences',    1_380_790, FIT * 7 / 8, '8 лотов · 7 без отделки'),
]
ROWS.sort(key=lambda r: -(r[1] + r[2]))

img = Image.new('RGB', (W * S, H * S), SURFACE)
dr = ImageDraw.Draw(img)

L, R, T = 40 * S, 130 * S, 112 * S   # правое поле — под подпись значения у самого длинного бара
PLOT_L = L + 360 * S
PLOT_W = W * S - R - PLOT_L
MAXV = 6_000_000
x_of = lambda v: PLOT_L + PLOT_W * v / MAXV
nf = lambda v: f'{v:,.0f}'.replace(',', ' ')

dr.text((L, 32 * S), 'Метр квартиры, в которую можно заехать', font=fnt(23 * S, True), fill=INK)
dr.text((L, 68 * S), 'Синее — цена метра сейчас, бронзовое — сколько нужно доплатить за отделку',
        font=fnt(15 * S, False), fill=MUTED)

BOT = H * S - 54 * S
for g in range(0, MAXV + 1, 1_000_000):
    x = x_of(g)
    dr.line([(x, T - 8 * S), (x, BOT)], fill=GRID, width=1 * S)
    dr.text((x - dr.textlength(nf(g / 1e6), font=fnt(12 * S)) / 2, BOT + 10 * S),
            nf(g / 1e6), font=fnt(13 * S), fill=MUTED)
dr.text((PLOT_W + PLOT_L - 60 * S, BOT + 28 * S), 'млн ₽ за м²', font=fnt(13 * S), fill=MUTED)

rowh = (BOT - T) / len(ROWS)
BH = int(rowh * 0.46)
for i, (name, flat, fit, sub) in enumerate(ROWS):
    cy = int(T + rowh * i + rowh / 2)
    ours = name.startswith('EDEN')
    dr.text((L, cy - 19 * S), name, font=fnt(18 * S, ours), fill=RED if ours else INK)
    dr.text((L, cy + 5 * S), sub, font=fnt(13 * S), fill=MUTED)
    x1 = x_of(flat)
    dr.rectangle([PLOT_L, cy - BH // 2, x1, cy + BH // 2], fill=RED if ours else C_FLAT)
    if fit > 1000:
        dr.rectangle([x1, cy - BH // 2, x_of(flat + fit), cy + BH // 2], fill=C_FIT)
    tot = flat + fit
    dr.text((x_of(tot) + 12 * S, cy - 11 * S), ('≈ ' if ours else '') + nf(tot),
            font=fnt(19 * S, True), fill=RED if ours else INK)

dr.line([(PLOT_L, T - 8 * S), (PLOT_L, BOT)], fill=(200, 197, 190), width=1 * S)
dr.line([(PLOT_L, BOT), (W * S - R, BOT)], fill=(200, 197, 190), width=1 * S)

img.resize((W, H), Image.LANCZOS).save(os.path.join(HERE, 'assets', 'ed_chart.png'))
print('written assets/ed_chart.png', (W, H))
for n, f_, ft, _ in ROWS:
    print(f'  {n:24s} {nf(f_):>10s} + {nf(ft):>8s} = {nf(f_ + ft):>10s}')
