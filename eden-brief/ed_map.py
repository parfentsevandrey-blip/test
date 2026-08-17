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
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import ImageDraw
from ymap import render
from markers import pin, label

NAVY, RED = (31, 42, 68), (179, 40, 45)
OUR = (37.60380, 55.75380)
W, H, Z = 920, 560, 15
CEN = (37.59960, 55.75210)
base, proj = render(CEN, Z, W, H, scale=2)
img = base.convert('RGBA'); dr = ImageDraw.Draw(img, 'RGBA')

#  lon,      lat,      название,               подпись,                       dx,  dy,  якорь
SITES = [
    (37.60160, 55.75340, 'Никитский-6',         '3 195 тыс. ₽/м² · с отделкой',  -44,  -92, 'right'),
    (37.60530, 55.75193, 'Фамильный дом Люче',  '2 935 тыс. ₽/м² · с отделкой',  -46,   20, 'right'),
    (37.60600, 55.75111, 'Кло 17 (Clos 17)',    '2 627 тыс. ₽/м² · без отделки',  50,  -34, 'left'),
    (37.59278, 55.75000, 'Turandot Residences', '1 381 тыс. ₽/м²',               -44,   14, 'right'),
]

for lon, lat, name, sub, dx, dy, anchor in SITES:
    x, y = proj(lon, lat)
    pin(dr, x, y, 13, NAVY)
    label(img, dr, x + dx, y + dy, name, sub, anchor, 15, (255, 255, 255), NAVY, (110, 118, 132))

x, y = proj(*OUR)
pin(dr, x, y, 17, RED)
label(img, dr, x + 52, y - 96, 'EDEN Private Residence',
      '≈ 4 400 тыс. ₽/м² · без отделки · III кв. 2029', 'left', 17,
      (255, 255, 255), RED, (110, 118, 132))

img.convert('RGB').resize((W, H), 1).save('eden/ed_map.png')
print('written eden/ed_map.png', (W, H))
