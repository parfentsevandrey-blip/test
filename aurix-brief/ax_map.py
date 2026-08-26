"""Карта трёх площадок AURIX в центре Москвы.

Координаты — из OpenStreetMap по адресу, сверены 26.08.2026:

  Большая Ордынка, 25   Замоскворечье   55.74005 / 37.62510
  Земледельческий, 15   Хамовники       55.74184 / 37.57859
  Малая Полянка, 3      Якиманка        55.73594 / 37.61745

По «Большой Ордынке» в OSM размечено строение 2 того же владения — пин стоит
на квартале, а не на конкретном корпусе; проект занимает строения 1 и 4.

Как и в остальных справках, карта рисуется на канве 2× и в этом же размере
сохраняется: в документе она стоит шириной 643 px, то есть с почти трёхкратным
запасом по разрешению, и подписи остаются читаемыми.
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import ImageDraw
from ymap import render
from markers import pin, label

HERE = os.path.dirname(os.path.abspath(__file__))
RED, NAVY = (179, 40, 45), (31, 42, 68)
W, H, Z, S = 920, 560, 14, 2
CEN = (37.60190, 55.73930)
base, proj = render(CEN, Z, W, H, scale=S)
img = base.convert('RGBA'); dr = ImageDraw.Draw(img, 'RGBA')

FS, R = 27, 22

#  lon,      lat,      название,                подпись,                        dx,   dy,  якорь
SITES = [
    (37.57859, 55.74184, 'Земледельческий, 15', '77 квартир · 8,0 тыс. м² жилья',  74, -176, 'left'),
    (37.62510, 55.74005, 'Большая Ордынка, 25', '17,3 тыс. м² · сдача 2031',      -74, -176, 'right'),
    (37.61745, 55.73594, 'Малая Полянка, 3',    '34 квартиры · 4,9 тыс. м²',      -74,   36, 'right'),
]

for lon, lat, name, sub, dx, dy, anchor in SITES:
    x, y = proj(lon, lat)
    pin(dr, x, y, R, RED)
    label(img, dr, x + dx, y + dy, name, sub, anchor, FS,
          (255, 255, 255), RED, (104, 112, 126), pad=17, radius=13)

out = os.path.join(HERE, 'assets', 'ax_map.png')
img.convert('RGB').save(out)
print('written', out, img.size)
for lon, lat, name, *_ in SITES:
    x, y = proj(lon, lat)
    print(f'  {name:24s} x={x / S:6.0f} y={y / S:6.0f}')
