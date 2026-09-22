# -*- coding: utf-8 -*-
"""Кадры визуализаций квартала под вёрстку.

Исходники — photos/, скачаны со страниц, где опубликованы официальные
визуализации проекта и мастер-плана. Квартал не построен: всё, что здесь
получается, — проектные визуализации, а не фотографии.

Кропы под два формата: 1,75 : 1 для кадра во всю полосу и 16 : 9 для пары.
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


#  выход,      исходник,   формат, фокус
JOBS = [
    ('hero',     'mig_d',    HERO, 0.50),   # бульвар с башнями
    ('sky',      'mig_e',    1.5,  0.50),   # квартал в панораме города, кадр целиком

    ('boulevard', 'mig_a',   PAIR, 0.50),   # улица квартала зимой
    ('tower',    'mig_c',    PAIR, 0.50),   # башня с бульвара
    ('square',   'mig_b',    PAIR, 0.50),   # площадь у башни
    ('aerial',   'mig_arch', PAIR, 0.50),   # квартал сверху
    ('plan',     'mp_4',     PAIR, 0.50),   # генплан: парк и пруд
    ('night',    'mp_5',     PAIR, 0.45),   # башни вечером
]

if __name__ == '__main__':
    os.makedirs(ASSETS, exist_ok=True)
    for name, src, ratio, focus in JOBS:
        path = next((os.path.join(SRC, src + e) for e in ('.jpg', '.webp')
                     if os.path.exists(os.path.join(SRC, src + e))), None)
        if not path:
            print(f'{name:10s} — нет файла {src}')
            continue
        im = crop_to(Image.open(path).convert('RGB'), ratio, focus)
        cap = 450 if ratio == PAIR else 1300
        if im.width > cap:
            im = im.resize((cap, int(cap / im.width * im.height)), Image.LANCZOS)
        out = os.path.join(ASSETS, name + '.jpg')
        im.save(out, 'JPEG', quality=86, subsampling=0, optimize=True)
        print(f'{name:10s} {im.width}x{im.height}  <- {os.path.basename(path)}')
