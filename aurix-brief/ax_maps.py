"""Три карты крупным планом — по одной на площадку.

Масштаб Z=17: кадр шириной около шестисот метров, видно квартал и соседние
улицы. Координаты те же, что в общей карте (ax_map.py), из OpenStreetMap.

Как и остальные карты, кадр рисуется на канве 2× и сохраняется в этом же
размере: в документе он стоит шириной 643 px, запас по разрешению трёхкратный.
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import ImageDraw
from ymap import render
from markers import pin, label

HERE = os.path.dirname(os.path.abspath(__file__))
RED = (179, 40, 45)
W, H, Z, S = 920, 322, 17, 2

#  файл,     lon,       lat,      подпись на пине,        вторая строка,                 dx,  dy,  якорь
PLOTS = [
    ('ord', 37.62510, 55.74005, 'Большая Ордынка, 25', 'стр. 1 и 4 · Замоскворечье',  -80, -180, 'right'),
    ('zem', 37.57859, 55.74184, 'Земледельческий, 15', 'Хамовники',                    80, -180, 'left'),
    ('pol', 37.61745, 55.73594, 'Малая Полянка, 3',    'Якиманка',                     80, -180, 'left'),
]

for key, lon, lat, name, sub, dx, dy, anchor in PLOTS:
    base, proj = render((lon, lat), Z, W, H, scale=S)
    img = base.convert('RGBA'); dr = ImageDraw.Draw(img, 'RGBA')
    x, y = proj(lon, lat)
    pin(dr, x, y, 24, RED)
    label(img, dr, x + dx, y + dy, name, sub, anchor, 28,
          (255, 255, 255), RED, (104, 112, 126), pad=17, radius=13)
    out = os.path.join(HERE, 'assets', f'ax_map_{key}.png')
    img.convert('RGB').save(out)
    print('written', out, img.size)
