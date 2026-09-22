# -*- coding: utf-8 -*-
"""Три карты для справки.

Центр участка взят по карте: территория бывшего авиазавода ограничена
Ленинградским проспектом, 1-м и 2-м Боткинскими проездами, улицами
Авиаконструктора Сухого и Маргелова — 63,55 га по договору КРТ.
Координаты метро и городских объектов — геокодер OpenStreetMap.

Кадры: участок (Z=15), город (Z=12, квартал относительно центра)
и когорта (Z=14, что продаётся в двух километрах вокруг).
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

SITE = (37.5510, 55.7859)          # lon, lat
KREML = (37.617500, 55.752000)

#  подпись, lon, lat, сдвиг подписи, якорь
AROUND = [
    ('м. «Динамо»',          37.554900, 55.789830, -62, -30, 'right'),
    ('м. «Петровский парк»', 37.557093, 55.791922,  62, -96, 'left'),
    ('м. «ЦСКА»',            37.538412, 55.787243,  62, -96, 'left'),
    ('ВТБ Арена',            37.568919, 55.789925, -62,  40, 'right'),
    ('Петровский парк',      37.555670, 55.794236,  62,  40, 'left'),
    ('Боткинская больница',  37.553058, 55.782898,  62,  40, 'left'),
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

    # ── участок и что вокруг ──
    base, proj = render((SITE[0] + 0.0020, SITE[1] + 0.0016), 15, 960, 700, scale=S)
    img = base.convert('RGBA'); dr = ImageDraw.Draw(img, 'RGBA')
    for name, lon, lat, dx, dy, side in AROUND:
        x, y = proj(lon, lat)
        pin(dr, x, y, 17, NAVY)
        label(img, dr, x + dx, y + dy, name,
              f'{metres(SITE, (lon, lat)):.0f} м от центра участка', side, 22,
              fg=NAVY, sfg=GREY)
    x, y = proj(*SITE)
    pin(dr, x, y, 28, RED)
    label(img, dr, x - 100, y - 44, 'Квартал «МИГ»',
          '63,55 га · 2,3 млн м²', 'right', 29, fg=RED, sfg=(120, 70, 70))
    save(img, 'map_site.png')

    # ── город: квартал относительно центра ──
    base, proj = render((37.5900, 55.7720), 12, 920, 620, scale=S)
    img = base.convert('RGBA'); dr = ImageDraw.Draw(img, 'RGBA')
    kx, ky = proj(*KREML)
    pin(dr, kx, ky, 19, NAVY)
    label(img, dr, kx + 56, ky + 34, 'Кремль',
          f'{metres(SITE, KREML) / 1000:.1f} км от участка'.replace('.', ','),
          'left', 24, fg=NAVY, sfg=GREY)
    x, y = proj(*SITE)
    pin(dr, x, y, 28, RED)
    label(img, dr, x - 100, y - 44, 'Квартал «МИГ»',
          'Беговой район, внутри ТТК', 'right', 28, fg=RED, sfg=(120, 70, 70))
    save(img, 'map_city.png')

    # ── когорта ──
    pins = json.load(open(os.path.join(HERE, 'mg_pins.json'), encoding='utf-8'))
    # Дома стоят кучно вдоль Ленинградского проспекта: сдвиг и сторона
    # выноски заданы руками, иначе подписи наезжают друг на друга.
    SHIFT = {
        ('Прайм Парк', 'строится'):    (60, -96, 'left'),
        ('Слава', 'строится'):         (60, -30, 'left'),
        ('С5', 'строится'):           (-60, -96, 'right'),
        ('ВТБ Арена парк', 'готов'):  (-62, -96, 'right'),
        ('Царская площадь', 'готов'): (-60,  34, 'right'),
        ('Искра-Парк', 'готов'):      (-60, -96, 'right'),
        ('Alcon Tower', 'готов'):      (62,  44, 'left'),
    }
    base, proj = render((37.5620, 55.7845), 14, 920, 700, scale=S)
    img = base.convert('RGBA'); dr = ImageDraw.Draw(img, 'RGBA')
    for q in pins:
        key = (q['short'], q['what'])
        if key not in SHIFT:
            continue
        x, y = proj(q['lng'], q['lat'])
        col = BRONZE if q['what'] == 'строится' else NAVY
        pin(dr, x, y, 19, col)
        dx, dy, side = SHIFT[key]
        label(img, dr, x + dx, y + dy, q['short'],
              f"{q['ppmMed'] / 1e6:.2f} млн ₽/м²".replace('.', ','), side, 22,
              fg=col, sfg=GREY)
    x, y = proj(*SITE)
    pin(dr, x, y, 28, RED)
    label(img, dr, x - 104, y - 44, 'Квартал «МИГ»',
          'цены не объявлены', 'right', 27, fg=RED, sfg=(120, 70, 70))
    save(img, 'map_peers.png')

    for name, lon, lat, *_ in AROUND:
        print(f'  {name:24s} {metres(SITE, (lon, lat)):6.0f} м')
    print(f'  {"Кремль":24s} {metres(SITE, KREML):6.0f} м')
