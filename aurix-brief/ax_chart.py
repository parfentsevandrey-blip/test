"""Сколько стоит метр в районах трёх площадок — и сколько за него хотят просить.

Цены предложения по элитным районам Москвы — NF Group, I квартал 2026 года.
Красная строка — единственный публичный ценовой ориентир по трём площадкам
(«не менее 3 млн ₽ за метр»), он же рабочая величина в этой справке.
"""
import json, os
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
F  = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
FB = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
fnt = lambda s, b=False: ImageFont.truetype(FB if b else F, s)

S = 2
W, H = 1400, 680
SURFACE, INK, MUTED, GRID = (252, 252, 251), (34, 40, 52), (112, 120, 134), (226, 223, 216)
BLUE, RED = (74, 123, 200), (179, 40, 45)

#  название,                       тыс. ₽/м², подпись
ROWS = [
    ('Остоженка – Пречистенка',     4798, 'самый дорогой адрес Москвы'),
    ('Патриаршие пруды',            3948, ''),
    ('Тверской',                    3410, ''),
    ('Средняя по классу делюкс',    3242, 'по всей Москве'),
    ('Ориентир по трём площадкам',  3000, 'отраслевая оценка · прайса нет'),
    ('Якиманка',                    2938, 'здесь «Малая Полянка, 3»'),
    ('Хамовники',                   2808, 'здесь «Земледельческий, 15»'),
    ('Замоскворечье',               2020, 'здесь «Большая Ордынка, 25»'),
]

img = Image.new('RGB', (W * S, H * S), SURFACE)
dr = ImageDraw.Draw(img)

L, R, T = 40 * S, 130 * S, 112 * S
PLOT_L = L + 380 * S
PLOT_W = W * S - R - PLOT_L
MAXV = 5000
x_of = lambda v: PLOT_L + PLOT_W * v / MAXV
nf = lambda v: f'{v:,.0f}'.replace(',', ' ')

dr.text((L, 32 * S), 'Цена метра в районах трёх площадок', font=fnt(23 * S, True), fill=INK)
dr.text((L, 68 * S), 'Средняя цена предложения, тыс. ₽ за м² · NF Group, I квартал 2026 года',
        font=fnt(15 * S, False), fill=MUTED)

BOT = H * S - 54 * S
for g in range(0, MAXV + 1, 1000):
    x = x_of(g)
    dr.line([(x, T - 8 * S), (x, BOT)], fill=GRID, width=1 * S)
    dr.text((x - dr.textlength(nf(g / 1000), font=fnt(13 * S)) / 2, BOT + 10 * S),
            nf(g / 1000), font=fnt(13 * S), fill=MUTED)
dr.text((PLOT_W + PLOT_L - 70 * S, BOT + 28 * S), 'млн ₽ за м²', font=fnt(13 * S), fill=MUTED)

rowh = (BOT - T) / len(ROWS)
BH = int(rowh * 0.46)
for i, (name, v, sub) in enumerate(ROWS):
    cy = int(T + rowh * i + rowh / 2)
    ours = name.startswith('Ориентир')
    dr.text((L, cy - 19 * S), ('▶ ' if ours else '') + name,
            font=fnt(18 * S, ours), fill=RED if ours else INK)
    if sub:
        dr.text((L, cy + 5 * S), sub, font=fnt(13 * S), fill=MUTED)
    dr.rectangle([PLOT_L, cy - BH // 2, x_of(v), cy + BH // 2], fill=RED if ours else BLUE)
    dr.text((x_of(v) + 12 * S, cy - 11 * S), ('≈ ' if ours else '') + nf(v),
            font=fnt(19 * S, True), fill=RED if ours else INK)

dr.line([(PLOT_L, T - 8 * S), (PLOT_L, BOT)], fill=(200, 197, 190), width=1 * S)
dr.line([(PLOT_L, BOT), (W * S - R, BOT)], fill=(200, 197, 190), width=1 * S)

img.resize((W, H), Image.LANCZOS).save(os.path.join(HERE, 'assets', 'ax_chart.png'))
print('written assets/ax_chart.png', (W, H))
for n, v, _ in ROWS:
    print(f'  {n:32s} {nf(v):>6s}')
