"""Воронка спроса: от всего вторичного рынка Москвы к профилю нашего лота.

Каждая ступень измеряет СДЕЛКИ, чтобы величины были сопоставимы между собой.
Шкала логарифмическая: иначе последняя ступень (30 сделок) не видна рядом
с первой (123 610), а именно этот контраст и есть содержание графика.
Палитра #4A7BC8 / #B9822F / #B3282D.
"""
import math
from PIL import Image, ImageDraw, ImageFont

F  = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
FB = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
fnt = lambda s, b=False: ImageFont.truetype(FB if b else F, s)

S = 2
W, H = 1400 * S, 660 * S
SURFACE, INK, MUTED, GRID = (252, 252, 251), (34, 40, 52), (112, 120, 134), (226, 223, 216)
BLUE, BRONZE, RED = (74, 123, 200), (185, 130, 47), (179, 40, 45)

STEPS = [
    ('Вторичный рынок Москвы, все классы', 123_610, 'Росреестр, янв.–нояб. 2025', BLUE),
    ('Весь элитный рынок Москвы: первичка и вторичка', 3_020, 'Intermark, 2025', BLUE),
    ('Элитная первичка: премиум и делюкс', 1_790, 'NF Group, 2025', BLUE),
    ('Класс делюкс целиком', 300, 'NF Group, 2025', BRONZE),
    ('Профиль лота: до 100 м² и 200–300 млн ₽', 30, 'NF Group (доля) и расчёт, 2025', RED),
]

nf = lambda v: f'{v:,}'.replace(',', ' ')

img = Image.new('RGB', (W, H), SURFACE)
dr = ImageDraw.Draw(img)

L, R, T = 40 * S, 60 * S, 108 * S
PLOT_L = L + 470 * S                      # левая колонка под подписи ступеней
PLOT_W = W - R - PLOT_L
LO, HI = 10, 300_000
x_of = lambda v: PLOT_L + PLOT_W * (math.log10(v) - math.log10(LO)) / (math.log10(HI) - math.log10(LO))

# заголовок
dr.text((L, 30 * S), 'Сколько сделок в год приходится на профиль нашего лота',
        font=fnt(23 * S, True), fill=INK)
dr.text((L, 64 * S), 'Число сделок за год, логарифмическая шкала',
        font=fnt(14 * S, False), fill=MUTED)

# сетка
BOT = H - 52 * S
for g in (10, 100, 1_000, 10_000, 100_000):
    x = x_of(g)
    dr.line([(x, T - 6 * S), (x, BOT)], fill=GRID, width=1 * S)
    lab = nf(g)
    dr.text((x - dr.textlength(lab, font=fnt(12 * S)) / 2, BOT + 9 * S),
            lab, font=fnt(12 * S), fill=MUTED)

rowh = (BOT - T) / len(STEPS)
BH = int(rowh * 0.44)

for i, (name, val, sub, col) in enumerate(STEPS):
    cy = int(T + rowh * i + rowh / 2)
    # подпись ступени слева
    dr.text((L, cy - 15 * S), name, font=fnt(15 * S, i == len(STEPS) - 1), fill=INK)
    dr.text((L, cy + 5 * S), sub, font=fnt(12 * S), fill=MUTED)
    # столбик
    x1 = x_of(max(val, LO * 1.02))
    dr.rectangle([PLOT_L, cy - BH // 2, x1, cy + BH // 2], fill=col)
    # значение
    txt = ('≈ ' if val == 30 else '') + nf(val)
    dr.text((x1 + 12 * S, cy - 11 * S), txt, font=fnt(19 * S, True), fill=col)

# ось
dr.line([(PLOT_L, T - 6 * S), (PLOT_L, BOT)], fill=(200, 197, 190), width=1 * S)
dr.line([(PLOT_L, BOT), (W - R, BOT)], fill=(200, 197, 190), width=1 * S)

img.resize((W // S, H // S), Image.LANCZOS).save('zl/zl_dem_chart.png')
print('written zl/zl_dem_chart.png', (W // S, H // S))
