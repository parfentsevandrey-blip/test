"""Карта локации EDEN Private Residence.

Координаты взяты из карточек проектов на профильных порталах
и сверены между собой 02.08.2026:

  EDEN Private Residence   Нижний Кисловский пер., 7   55.75380 / 37.60380
  Клубный дом «Никитский 6» Никитский бульвар, 6/20    55.75340 / 37.60160
  «Большая Никитская, 16»  Большая Никитская, 16       55.75687 / 37.60440
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import ImageDraw
from ymap import render
from markers import pin, label

NAVY, RED, BRONZE = (31, 42, 68), (179, 40, 45), (169, 118, 47)
OUR = (37.60380, 55.75380)
W, H, Z = 920, 500, 16
CEN = (37.6055, 55.75430)
base, proj = render(CEN, Z, W, H, scale=2)
img = base.convert('RGBA'); dr = ImageDraw.Draw(img, 'RGBA')

#  lon,      lat,      название,                  подпись,                   dx,  dy,  якорь
SITES = [
    (37.60160, 55.75340, 'Клубный дом «Никитский 6»', '2,46–3,15 млн ₽/м² · IV кв. 2026', -46,  12, 'right'),
    (37.60440, 55.75687, '«Большая Никитская, 16»',   '4 квартиры · III кв. 2026',         46, -80, 'left'),
]

for lon, lat, name, sub, dx, dy, anchor in SITES:
    x, y = proj(lon, lat)
    pin(dr, x, y, 13, NAVY)
    label(img, dr, x + dx, y + dy, name, sub, anchor, 15, (255, 255, 255), NAVY, (110, 118, 132))

x, y = proj(*OUR)
pin(dr, x, y, 17, RED)
label(img, dr, x + 52, y + 14, 'EDEN Private Residence',
      '22 резиденции · от 1,1 млрд ₽ · III кв. 2029', 'left', 17,
      (255, 255, 255), RED, (110, 118, 132))

img.convert('RGB').resize((W, H), 1).save('eden/ed_map.png')
print('written eden/ed_map.png', (W, H))
