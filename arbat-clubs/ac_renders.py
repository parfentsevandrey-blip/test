# -*- coding: utf-8 -*-
"""Кадры визуализаций под вёрстку.

Исходники — photos/, скачаны из объявлений застройщика (ac_photos.py).
Оба дома строятся, поэтому всё, что здесь получается, — проектные
визуализации, а не фотографии.

Кропы под два формата: 2,30 : 1 для кадра во всю полосу и 16 : 9 для ряда
из трёх.
"""
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, 'photos')
ASSETS = os.path.join(HERE, 'assets')
WIDE, PAIR, HERO = 2.30, 16 / 9, 1.75


def crop_to(im, ratio, focus=0.5):
    w, h = im.size
    if w / h > ratio:
        nw = int(h * ratio)
        return im.crop((int((w - nw) * focus), 0, int((w - nw) * focus) + nw, h))
    nh = int(w / ratio)
    return im.crop((0, int((h - nh) * focus), w, int((h - nh) * focus) + nh))


#  выход,        исходник,       формат, фокус
JOBS = [
    ('hero',       'clos17_03',   HERO, 0.50),

    ('clos_fas',   'clos17_02',   PAIR, 0.50),
    ('clos_view',  'clos17_03',   PAIR, 0.40),
    ('clos_yard',  'clos17_05',   PAIR, 0.50),
    ('clos_lobby', 'clos17_09',   PAIR, 0.50),
    ('clos_ent',   'clos17_07',   PAIR, 0.50),
    ('clos_liv',   'clos17_115',  PAIR, 0.50),
    ('clos_fire',  'clos17_117',  PAIR, 0.50),
    ('clos_stair', 'clos17_100',  PAIR, 0.50),

    ('luce_fas',   'luce_02',     PAIR, 0.50),
    ('luce_air',   'luce_03',     PAIR, 0.50),
    ('luce_terr',  'luce_04',     PAIR, 0.50),
    ('luce_ent',   'luce_05',     PAIR, 0.50),
    ('luce_lobby', 'luce_112',    PAIR, 0.50),
    ('luce_yard',  'luce_110',    PAIR, 0.50),
    ('luce_fire',  'luce_113',    PAIR, 0.50),
    ('luce_park',  'luce_114',    PAIR, 0.50),
]

if __name__ == '__main__':
    os.makedirs(ASSETS, exist_ok=True)
    for name, src, ratio, focus in JOBS:
        path = os.path.join(SRC, src + '.jpg')
        if not os.path.exists(path):
            print(f'{name:11s} — нет файла {src}')
            continue
        im = crop_to(Image.open(path).convert('RGB'), ratio, focus)
        cap = 450 if ratio == PAIR else 1300
        if im.width > cap:
            im = im.resize((cap, int(cap / im.width * im.height)), Image.LANCZOS)
        out = os.path.join(ASSETS, name + '.jpg')
        im.save(out, 'JPEG', quality=86, subsampling=0, optimize=True)
        print(f'{name:11s} {im.width}x{im.height}  <- {src}')
