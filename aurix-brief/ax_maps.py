"""Три карты крупным планом — по одной на площадку.

Масштаб Z=16: кадр шириной около 1,2 км и высотой около 800 м — в него
попадают ближайшие станции метро и окружающий район, а не только квартал.
Координаты те же, что в общей карте (ax_map.py), из OpenStreetMap.

Как и остальные карты, кадр рисуется на канве 2× и сохраняется в этом же
размере: в документе он стоит шириной 643 px, запас по разрешению трёхкратный.
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import ImageDraw
from ymap import render
from markers import pin, label

HERE = os.path.dirname(os.path.abspath(__file__))
RED, NAVY = (179, 40, 45), (31, 42, 68)
W, H, Z, S = 920, 615, 16, 2

#  На кадр наносятся и сами станции метро — координаты из OpenStreetMap,
#  расстояние считается по прямой от участка.
#
#  Центр кадра иногда сдвинут относительно участка — чтобы в него попала
#  ближайшая станция метро. У «Земледельческого» она в километре к северу,
#  поэтому кадр смещён туда.
#
#  файл,     lon,       lat,      центр кадра,          подпись,               вторая строка,               dx,  dy,  якорь
PLOTS = [
    ('ord', 37.62510, 55.74005, (37.62430, 55.74020), 'Большая Ордынка, 25', 'стр. 1 и 4 · Замоскворечье',  -84, -190, 'right'),
    ('zem', 37.57859, 55.74184, (37.58080, 55.74430), 'Земледельческий, 15', 'Хамовники',                    84, -190, 'left'),
    ('pol', 37.61745, 55.73594, (37.61790, 55.73700), 'Малая Полянка, 3',    'Якиманка',                     84, -190, 'left'),
]

#  станции метро на кадре: ключ площадки -> (название, lon, lat, dx, dy, якорь)
METRO = {
    'ord': [('Третьяковская', 37.62734, 55.74092, -60, -172, 'right'),
            ('Новокузнецкая', 37.62920, 55.74143,  70,   60, 'left')],
    'zem': [('Смоленская',    37.58182, 55.74739,  70,   55, 'left')],
    'pol': [('Полянка',       37.61720, 55.73817,  70, -150, 'left')],
}

R_EARTH = 6371000.0


def metres(lon1, lat1, lon2, lat2):
    from math import radians, cos, hypot
    dx = radians(lon2 - lon1) * cos(radians((lat1 + lat2) / 2)) * R_EARTH
    dy = radians(lat2 - lat1) * R_EARTH
    return hypot(dx, dy)


for key, lon, lat, cen, name, sub, dx, dy, anchor in PLOTS:
    base, proj = render(cen, Z, W, H, scale=S)
    img = base.convert('RGBA'); dr = ImageDraw.Draw(img, 'RGBA')
    for mname, mlon, mlat, mdx, mdy, manchor in METRO.get(key, []):
        mx, my = proj(mlon, mlat)
        pin(dr, mx, my, 17, NAVY)
        d = metres(lon, lat, mlon, mlat)
        label(img, dr, mx + mdx, my + mdy, 'м. «' + mname + '»',
              f'≈ {round(d / 10) * 10:.0f} м от участка', manchor, 23,
              (255, 255, 255), NAVY, (104, 112, 126), pad=14, radius=11)
        print(f'    м. {mname}: {d:.0f} м')

    x, y = proj(lon, lat)
    pin(dr, x, y, 26, RED)
    label(img, dr, x + dx, y + dy, name, sub, anchor, 30,
          (255, 255, 255), RED, (104, 112, 126), pad=17, radius=13)
    out = os.path.join(HERE, 'assets', f'ax_map_{key}.png')
    img.convert('RGB').save(out)
    print('written', out, img.size)
