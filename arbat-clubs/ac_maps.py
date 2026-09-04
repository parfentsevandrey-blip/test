# -*- coding: utf-8 -*-
"""Три карты для аналитики.

Координаты домов подтверждены геокодером OpenStreetMap по адресам:
  Староваганьковский переулок, 17с4   55.751093 / 37.605955
  Крестовоздвиженский переулок, 4     55.751618 / 37.605522
Между ними 62 метра: дома стоят на одном квартале, спина к спине.

Кадры: квартал (Z=17, оба дома по отдельности), центр (Z=15, дома
относительно Кремля и Бульварного кольца) и когорта (Z=15, что ещё
продаётся дороже 700 тыс. ₽ за метр в километре вокруг).
"""
import os, sys, json
from math import radians, cos, hypot

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import ImageDraw
from ymap import render
from markers import pin, label

HERE = os.path.dirname(os.path.abspath(__file__))
RED, NAVY, BRONZE = (179, 40, 45), (31, 42, 68), (156, 106, 38)
GREY = (104, 110, 122)
R = 6371000.0

CLOS = (37.605955, 55.751093)      # lon, lat
LUCE = (37.605522, 55.751618)
MID = ((CLOS[0] + LUCE[0]) / 2, (CLOS[1] + LUCE[1]) / 2)

#  что рядом: подпись, lon, lat, сдвиг подписи, якорь
AROUND = [
    ('Кремль',                 37.621353, 55.752526,  -60,  50, 'right'),
    ('Храм Христа Спасителя',  37.605511, 55.744524,   60, -96, 'left'),
    ('ГМИИ имени Пушкина',     37.605423, 55.747285,  -60, -96, 'right'),
    ('Большой театр',          37.618612, 55.760130,   60, -96, 'left'),
    ('м. «Библиотека\nимени Ленина»', 37.609638, 55.750826, 62, 34, 'left'),
]


def metres(a, b):
    dx = radians(b[0] - a[0]) * cos(radians((a[1] + b[1]) / 2)) * R
    dy = radians(b[1] - a[1]) * R
    return hypot(dx, dy)


def save(img, name):
    path = os.path.join(HERE, 'assets', name)
    img.convert('RGB').save(path)
    print(f'{path}  {img.size}')


if __name__ == '__main__':
    os.makedirs(os.path.join(HERE, 'assets'), exist_ok=True)
    S = 2

    # ── квартал: два дома по отдельности ──
    base, proj = render((MID[0] + 0.0009, MID[1]), 17, 920, 560, scale=S)
    img = base.convert('RGBA'); dr = ImageDraw.Draw(img, 'RGBA')
    for point, name, sub, dx, dy, side in (
            (LUCE, 'Фамильный дом Люче', '46 резиденций · 26 лотов в продаже', -70, -150, 'right'),
            (CLOS, 'Кло 17', '26 резиденций · 17 лотов в продаже', -70, 40, 'right')):
        x, y = proj(*point)
        pin(dr, x, y, 26, RED)
        label(img, dr, x + dx, y + dy, name, sub, side, 28, fg=RED, sfg=(120, 70, 70))
    mx, my = proj(*MID)
    label(img, dr, mx + 150, my - 44, f'{metres(CLOS, LUCE):.0f} м',
          'между домами', 'left', 26, fg=NAVY, sfg=GREY)
    save(img, 'map_block.png')

    # ── центр: дома, Кремль, бульвары ──
    base, proj = render((37.61150, 55.75420), 15, 920, 660, scale=S)
    img = base.convert('RGBA'); dr = ImageDraw.Draw(img, 'RGBA')
    for name, lon, lat, dx, dy, side in AROUND:
        x, y = proj(lon, lat)
        pin(dr, x, y, 17, NAVY)
        d = metres(MID, (lon, lat))
        label(img, dr, x + dx, y + dy, name.replace('\n', ' '),
              f'{d / 1000:.2f} км от домов'.replace('.', ','), side, 23,
              fg=NAVY, sfg=GREY)
    x, y = proj(*MID)
    pin(dr, x, y, 27, RED)
    label(img, dr, x - 96, y - 40, '«Кло 17» и «Люче»',
          '43 лота · 2,65 млн ₽ за м²', 'right', 29, fg=RED, sfg=(120, 70, 70))
    save(img, 'map_city.png')

    # ── когорта: чем дороже метр, тем выше в списке ──
    pins = json.load(open(os.path.join(HERE, 'ac_pins.json'), encoding='utf-8'))
    # Дома в переулках стоят кучно: сторона выноски и сдвиг заданы руками,
    # иначе подписи наезжают друг на друга.
    SHIFT = {
        'БРЮСОВ':               (54, -92, 'left'),
        'Лё Дом':               (54, -92, 'left'),
        'Дом на Хлебном':      (-54, -92, 'right'),
        'Stella di Mosca':     (-54, -92, 'right'),
        'Никитский-6':          (54, -92, 'left'),
        'Золотой':              (54,  30, 'left'),
        'Охотный Ряд 2':        (54, -92, 'left'),
        'Афанасьевский':       (-58, -96, 'right'),
        'Поварская 20':        (-54, -30, 'right'),
        'Клубный дом на Арбате': (-58, 44, 'right'),
        'Филипповский':        (-58,  44, 'right'),
        'Театральный Дом':      (54,  30, 'left'),
    }
    base, proj = render((37.60620, 55.75080), 15, 920, 780, scale=S)
    img = base.convert('RGBA'); dr = ImageDraw.Draw(img, 'RGBA')
    for q in pins:
        if q['ours'] or q['short'] not in SHIFT:
            continue
        x, y = proj(q['lng'], q['lat'])
        col = BRONZE if q['what'] == 'строится' else NAVY
        pin(dr, x, y, 19, col)
        dx, dy, side = SHIFT[q['short']]
        label(img, dr, x + dx, y + dy, q['short'],
              f"{q['ppmMed'] / 1e6:.2f} млн ₽/м²".replace('.', ','), side, 22,
              fg=col, sfg=GREY)
    x, y = proj(*MID)
    pin(dr, x, y, 27, RED)
    label(img, dr, x - 100, y - 42, '«Кло 17» и «Люче»',
          '2,65 млн ₽ за м²', 'right', 27, fg=RED, sfg=(120, 70, 70))
    save(img, 'map_peers.png')

    for name, lon, lat, *_ in AROUND:
        print(f"  {name.replace(chr(10), ' '):32s} {metres(MID, (lon, lat)):6.0f} м")
