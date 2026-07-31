"""Карта локации ЖК «Золотой». Координаты сверены 31.07.2026:

  1 Золотой (наш)        Софийская наб., 18       55.74718 / 37.61827   Яндекс Недвижимость
  2 Софийский            Софийская наб., 34с5     55.74681 / 37.62245   Яндекс Недвижимость
  3 Клубный дом DUO      Софийская наб., 34с3     55.74636 / 37.62271   Яндекс Недвижимость
  4 Резиденция 1864      Софийская наб., 36       55.74747 / 37.62359   2ГИС
  5 BALCHUG VIEWPOINT    Садовническая, 7         55.74779 / 37.62764   геокодер OSM
  6 Клубный дом Космо 4/22 Космодамианская, 4/22с4 55.74558 / 37.63879  Яндекс Недвижимость
  7 Русские Сезоны       Б. Ордынка, 19с9         55.74203 / 37.62705   Яндекс Недвижимость
  8 Дом Лаврушинский     Б. Толмачёвский, 5с1     55.73960 / 37.62207   Яндекс Недвижимость

Пять проектов из восьми стоят на одном отрезке Софийской набережной, и подписи
на них не помещаются — поэтому пины пронумерованы, а расшифровка вынесена
в легенду. Звёздочка — апартаменты, а не квартиры.
"""
import sys, math, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from PIL import ImageDraw, Image, ImageFilter
from ymap import render
from markers import pin, label, font

NAVY, RED, INK, MUTED = (31, 42, 68), (179, 40, 45), (28, 34, 46), (110, 118, 132)
W, H, Z = 920, 640, 15
CEN = (37.62830, 55.74360)
base, proj = render(CEN, Z, W, H, scale=2)
img = base.convert('RGBA'); dr = ImageDraw.Draw(img, 'RGBA')

#   №, lon,      lat,      название,                 подпись в легенде
SITES = [
    (1, 37.61827, 55.74718, '▶ ЖК «Золотой» — наш лот',   '2 408 536 ₽/м² · де-люкс · Capital Group'),
    (2, 37.62245, 55.74681, '«Софийский» *',              '1 517 047 ₽/м² · апартаменты · 2018'),
    (3, 37.62271, 55.74636, 'Клубный дом DUO',            '2 906 671 ₽/м² · де-люкс · Hutton'),
    (4, 37.62359, 55.74747, '«Резиденция 1864» *',        '2 063 476 ₽/м² · апартаменты · Сбербанк Капитал'),
    (5, 37.62764, 55.74779, 'BALCHUG VIEWPOINT *',        '1 597 813 ₽/м² · апартаменты · 2017'),
    (6, 37.63879, 55.74558, 'Клубный дом «Космо 4/22»',   '2 307 948 ₽/м² · де-люкс · Галс-Девелопмент'),
    (7, 37.62705, 55.74203, '«Русские Сезоны»',           '4 307 056 ₽/м² · де-люкс · СЗ «Экран»'),
    (8, 37.62207, 55.73960, '«Дом Лаврушинский»',         '3 874 043 ₽/м² · элит · Sminex'),
]
for num, lon, lat, *_ in SITES:
    x, y = proj(lon, lat); x, y = int(x), int(y)
    ours = num == 1
    pin(dr, x, y, r=30 if ours else 25, fill=RED if ours else NAVY, num=num)

# подпись только у нашего лота — она должна читаться первой;
# ставим её НАД пином, в пустое поле реки: снизу вплотную стоит пин 3 (DUO)
x, y = proj(37.61827, 55.74718)
label(img, dr, int(x), int(y) - 200, 'НАШ ЛОТ · ЖК «Золотой»',
      '87,5 м² · 280,0 млн ₽ · 3 200 000 ₽/м²', fs=32,
      bg=RED, fg=(255, 255, 255), sfg=(255, 214, 214), anchor='center')

# ── легенда ────────────────────────────────────────────────────────────────
fT, fS = font(27, True), font(23)
rowH, padX, padY = 46, 22, 20
wN = 40
wT = max(dr.textbbox((0, 0), t, font=fT)[2] for _, _, _, t, _ in SITES)
wS = max(dr.textbbox((0, 0), s, font=fS)[2] for _, _, _, _, s in SITES)
bw = wN + wT + 30 + wS + padX * 2
bh = rowH * len(SITES) + padY * 2
bx, by = W * 2 - bw - 34, H * 2 - bh - 34
sh = Image.new('RGBA', img.size, (0, 0, 0, 0))
ImageDraw.Draw(sh).rounded_rectangle([bx, by + 4, bx + bw, by + bh + 4], 14, fill=(0, 0, 0, 70))
img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(7)))
dr.rounded_rectangle([bx, by, bx + bw, by + bh], 14, fill=(255, 255, 255, 244), outline=(0, 0, 0, 30), width=1)
for i, (num, _, _, title, sub) in enumerate(SITES):
    cy = by + padY + rowH * i
    c = RED if num == 1 else NAVY
    dr.ellipse([bx + padX, cy + 6, bx + padX + 28, cy + 34], fill=c)
    b = dr.textbbox((0, 0), str(num), font=font(20, True))
    dr.text((bx + padX + 14 - (b[2] - b[0]) / 2 - b[0], cy + 20 - (b[3] - b[1]) / 2 - b[1]),
            str(num), font=font(20, True), fill=(255, 255, 255))
    dr.text((bx + padX + wN, cy + 8), title, font=fT, fill=INK if num != 1 else RED)
    dr.text((bx + padX + wN + wT + 30, cy + 11), sub, font=fS, fill=MUTED)

mpp = 156543.03392 * math.cos(math.radians(CEN[1])) / (2 ** Z)
px = int(500 / mpp * 2); x0, y0 = 34, H * 2 - 52
dr.rounded_rectangle([x0 - 12, y0 - 30, x0 + px + 12, y0 + 16], 6, fill=(255, 255, 255, 225))
dr.line([x0, y0, x0 + px, y0], fill=(40, 46, 58), width=4)
dr.line([x0, y0 - 9, x0, y0 + 5], fill=(40, 46, 58), width=4)
dr.line([x0 + px, y0 - 9, x0 + px, y0 + 5], fill=(40, 46, 58), width=4)
dr.text((x0, y0 - 30), '500 м', font=font(24, True), fill=(40, 46, 58))
dr.text((34, 20), '© Яндекс Карты', font=font(22), fill=(90, 96, 108))
img.convert('RGB').save('zl/zl_map.png', quality=95)
print('saved', img.size, 'высота на странице:', round(643 / (W / H)))
