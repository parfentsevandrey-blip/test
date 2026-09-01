"""Две карты для справки.

Координаты дома — из OpenStreetMap: здание с адресом «Капельский переулок, 5»
(way 50834023, центр контура 55.784482 / 37.631307). На этом участке сегодня
стоит четырёхэтажный офисный особняк 2 430 м².

Станции метро — тоже OSM:
  Проспект Мира (Кольцевая)      55.780851 / 37.631981   ≈ 0,41 км по прямой
  Рижская (Калужско-Рижская)     55.793884 / 37.634328   ≈ 1,06 км
  Москва-Рижская (МЦД)           55.795067 / 37.632255   ≈ 1,18 км

Кадра два: районный (Z=15, видно метро и парки) и обзорный (Z=13, дом
относительно Садового кольца и Кремля).
"""
import sys, os
from math import radians, cos, hypot

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import ImageDraw
from ymap import render
from markers import pin, label

HERE = os.path.dirname(os.path.abspath(__file__))
RED, NAVY, GREEN = (179, 40, 45), (31, 42, 68), (58, 96, 62)
R_EARTH = 6371000.0

HOUSE = (37.631307, 55.784482)
KREML = (37.617500, 55.752000)

#  станция,        lon,       lat,        сдвиг подписи и якорь
METRO = [
    ('Проспект Мира', 37.631981, 55.780851, -84, -172, 'right'),
    ('Рижская',       37.634328, 55.793884,  84,   46, 'left'),
]


def metres(lon1, lat1, lon2, lat2):
    dx = radians(lon2 - lon1) * cos(radians((lat1 + lat2) / 2)) * R_EARTH
    dy = radians(lat2 - lat1) * R_EARTH
    return hypot(dx, dy)


W, H, S = 920, 620, 2

if __name__ == '__main__':
    os.makedirs(os.path.join(HERE, 'assets'), exist_ok=True)

    # ── район: дом, две станции метро, парки вокруг ──
    base, proj = render((37.63260, 55.78700), 15, W, H, scale=S)
    img = base.convert('RGBA'); dr = ImageDraw.Draw(img, 'RGBA')

    for name, lon, lat, dx, dy, side in METRO:
        mx, my = proj(lon, lat)
        pin(dr, mx, my, 18, NAVY)
        d = metres(*HOUSE, lon, lat)
        label(img, dr, mx + dx, my + dy, 'м. «' + name + '»',
              f'≈ {d / 1000:.2f} км от дома'.replace('.', ','), side, 25,
              fg=NAVY, sfg=(96, 104, 118))

    hx, hy = proj(*HOUSE)
    pin(dr, hx, hy, 26, RED)
    label(img, dr, hx + 96, hy - 30, 'Капельский, 5',
          '46 резиденций · сдача 2029', 'left', 30, fg=RED, sfg=(120, 70, 70))

    img.convert('RGB').save(os.path.join(HERE, 'assets', 'kp_map_area.png'))
    print('assets/kp_map_area.png', img.size)

    # ── обзорный кадр: дом и центр ──
    base, proj = render((37.62600, 55.77000), 13, W, 560, scale=S)
    img = base.convert('RGBA'); dr = ImageDraw.Draw(img, 'RGBA')

    kx, ky = proj(*KREML)
    pin(dr, kx, ky, 18, NAVY)
    label(img, dr, kx - 80, ky + 44, 'Кремль',
          f'≈ {metres(*HOUSE, *KREML) / 1000:.1f} км от дома'.replace('.', ','),
          'right', 25, fg=NAVY, sfg=(96, 104, 118))

    hx, hy = proj(*HOUSE)
    pin(dr, hx, hy, 26, RED)
    label(img, dr, hx + 96, hy - 20, 'Капельский, 5',
          'Мещанский район, ЦАО', 'left', 30, fg=RED, sfg=(120, 70, 70))

    img.convert('RGB').save(os.path.join(HERE, 'assets', 'kp_map_city.png'))
    print('assets/kp_map_city.png', img.size)

    for name, lon, lat in [(m[0], m[1], m[2]) for m in METRO] + [('Кремль', *KREML)]:
        print(f'  {name:16s} {metres(*HOUSE, lon, lat):7.0f} м')
