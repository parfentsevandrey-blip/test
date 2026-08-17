"""Цена метра: EDEN против элитных районов Москвы.

Столбики — средневзвешенная цена предложения по элитным районам
(NF Group, I квартал 2026). Красный — расчётная цена EDEN, полученная
из единственной опубликованной цифры: от 1,1 млрд ₽ за резиденцию
около 250 м².
"""
from PIL import Image, ImageDraw, ImageFont

F  = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
FB = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
fnt = lambda s, b=False: ImageFont.truetype(FB if b else F, s)

S = 2
W, H = 1400, 700
SURFACE, INK, MUTED, GRID = (252, 252, 251), (34, 40, 52), (112, 120, 134), (226, 223, 216)
BLUE, BRONZE, RED = (74, 123, 200), (185, 130, 47), (179, 40, 45)

ROWS = [
    ('Остоженка – Пречистенка',     4798, BLUE,   'самый дорогой район Москвы'),
    ('EDEN Private Residence',      4400, RED,    'расчёт по старту продаж'),
    ('Патриаршие пруды',            3948, BLUE,   ''),
    ('Тверской',                    3410, BLUE,   ''),
    ('Средняя по классу делюкс',    3242, BRONZE, 'вся Москва, июнь 2026'),
    ('Арбат — район проекта',       3169, BRONZE, 'средняя по району'),
    ('Якиманка',                    2938, BLUE,   ''),
    ('Хамовники',                   2808, BLUE,   ''),
    ('Замоскворечье',               2020, BLUE,   ''),
]

img = Image.new('RGB', (W * S, H * S), SURFACE)
dr = ImageDraw.Draw(img)

L, R, T = 40 * S, 60 * S, 116 * S
PLOT_L = L + 400 * S
PLOT_W = W * S - R - PLOT_L
MAXV = 5200
x_of = lambda v: PLOT_L + PLOT_W * v / MAXV

dr.text((L, 32 * S), 'Цена метра: EDEN и элитные районы Москвы', font=fnt(23 * S, True), fill=INK)
dr.text((L, 68 * S), 'Тысяч ₽ за м², цена предложения. NF Group, I квартал 2026',
        font=fnt(14 * S, False), fill=MUTED)

BOT = H * S - 54 * S
for g in range(0, MAXV + 1, 1000):
    x = x_of(g)
    dr.line([(x, T - 8 * S), (x, BOT)], fill=GRID, width=1 * S)
    lab = f'{g:,}'.replace(',', ' ')
    dr.text((x - dr.textlength(lab, font=fnt(12 * S)) / 2, BOT + 10 * S), lab,
            font=fnt(12 * S), fill=MUTED)

rowh = (BOT - T) / len(ROWS)
BH = int(rowh * 0.50)
for i, (name, val, col, sub) in enumerate(ROWS):
    cy = int(T + rowh * i + rowh / 2)
    ours = col == RED
    dr.text((L, cy - (16 * S if sub else 9 * S)), name, font=fnt(15 * S, ours), fill=INK)
    if sub:
        dr.text((L, cy + 4 * S), sub, font=fnt(11 * S), fill=MUTED)
    dr.rectangle([PLOT_L, cy - BH // 2, x_of(val), cy + BH // 2], fill=col)
    dr.text((x_of(val) + 12 * S, cy - 11 * S),
            ('≈ ' if ours else '') + f'{val:,}'.replace(',', ' '),
            font=fnt(18 * S, True), fill=col)

dr.line([(PLOT_L, T - 8 * S), (PLOT_L, BOT)], fill=(200, 197, 190), width=1 * S)
dr.line([(PLOT_L, BOT), (W * S - R, BOT)], fill=(200, 197, 190), width=1 * S)

img.resize((W, H), Image.LANCZOS).save('eden/ed_chart.png')
print('written eden/ed_chart.png', (W, H))
