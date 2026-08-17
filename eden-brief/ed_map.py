"""Карта локации EDEN Private Residence и четырёх конкурентов.

Координаты домов взяты из OpenStreetMap по адресу (Overpass API) и с карточек
проектов, сверены 17.08.2026:

  EDEN Private Residence   Нижний Кисловский пер., 7      55.75380 / 37.60380
  Никитский-6              Никитский бульвар, 6/20        55.75340 / 37.60160
  Фамильный дом Люче       Крестовоздвиженский пер., 4с1  55.75193 / 37.60530
  Кло 17 (Clos 17)         Староваганьковский пер., 17с4  55.75111 / 37.60600
  Turandot Residences      улица Арбат, 24                55.75000 / 37.59278

Stella di Mosca (Б. Никитская, 9/15) на карту не нанесён: координаты дома
подтвердить не удалось, геокодер по этому адресу не отвечал.

Карта рисуется на канве 2× (1840×1120) и в этом же размере сохраняется:
в документе она стоит шириной 643 px, то есть с почти трёхкратным запасом
по разрешению. Раньше картинка перед сохранением ужималась до 920×560,
и подписи домов на странице выходили нечитаемо мелкими.
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import ImageDraw
from ymap import render
from markers import pin, label

HERE = os.path.dirname(os.path.abspath(__file__))
NAVY, RED = (31, 42, 68), (179, 40, 45)
OUR = (37.60380, 55.75380)
W, H, Z, S = 920, 560, 15, 2
CEN = (37.59960, 55.75210)
base, proj = render(CEN, Z, W, H, scale=S)
img = base.convert('RGBA'); dr = ImageDraw.Draw(img, 'RGBA')

FS, FS_OUR = 27, 30                 # кегль подписей на канве 2×
R, R_OUR = 20, 26                   # радиус пинов

#  lon,      lat,      название,               подпись,                        dx,   dy,  якорь
SITES = [
    (37.60160, 55.75340, 'Никитский-6',         '3 195 тыс. ₽/м² · с отделкой',  -62, -168, 'right'),
    (37.60530, 55.75193, 'Фамильный дом Люче',  '2 935 тыс. ₽/м² · с отделкой',  -64,   34, 'right'),
    (37.60600, 55.75111, 'Кло 17 (Clos 17)',    '2 627 тыс. ₽/м² · без отделки',  70,  -60, 'left'),
    (37.59278, 55.75000, 'Turandot Residences', '1 381 тыс. ₽/м²',               -62,   26, 'right'),
]

for lon, lat, name, sub, dx, dy, anchor in SITES:
    x, y = proj(lon, lat)
    pin(dr, x, y, R, NAVY)
    label(img, dr, x + dx, y + dy, name, sub, anchor, FS,
          (255, 255, 255), NAVY, (104, 112, 126), pad=17, radius=13)

x, y = proj(*OUR)
pin(dr, x, y, R_OUR, RED)
label(img, dr, x + 72, y - 178, 'EDEN Private Residence',
      '≈ 4 400 тыс. ₽/м² · без отделки · III кв. 2029', 'left', FS_OUR,
      (255, 255, 255), RED, (104, 112, 126), pad=17, radius=13)

out = os.path.join(HERE, 'assets', 'ed_map.png')
img.convert('RGB').save(out)
print('written', out, img.size)
