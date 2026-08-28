"""Кадры для страниц визуализаций двух петербургских проектов.

Все изображения — с сайта застройщика; файлы отдаются с хоста
api.aurix-development.ru, отдельных лендингов у этих проектов нет.

Форматы вёрстки: 2,30 : 1 для кадра во всю полосу и 16 : 9 для ряда из трёх.
"""
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, 'assets')
WIDE, PAIR = 2.30, 16 / 9


def crop_to(im, ratio, focus=0.5):
    w, h = im.size
    if w / h > ratio:
        nw = int(h * ratio)
        return im.crop((int((w - nw) * focus), 0, int((w - nw) * focus) + nw, h))
    nh = int(w / ratio)
    return im.crop((0, int((h - nh) * focus), w, int((h - nh) * focus) + nh))


JOBS = [
    # ── ЛДМ ──
    ('ldm_hero', 'ldm_neva_d.jpg',                 WIDE, 0.50),
    ('ldm_a',    '972_ldm_intro_1.jpg',            PAIR, 0.50),
    ('ldm_b',    'ldm_konts_arkh_01d.jpg',         PAIR, 0.50),
    ('ldm_c',    '921_ldm_river.jpg',              PAIR, 0.50),
    ('ldm_d',    '937_ldm_yard.jpg',               PAIR, 0.50),
    ('ldm_e',    '938_ldm_private_courtyard.jpg',  PAIR, 0.50),
    ('ldm_f',    '936_ldm_private_coutryard1.jpg', PAIR, 0.50),
    ('ldm_g',    '930_ldm_living_room_1.jpg',      PAIR, 0.50),
    ('ldm_h',    '920_ldm_views.jpg',              PAIR, 0.50),
    ('ldm_i',    '944_ldm_parking1.jpg',           PAIR, 0.50),

    # ── Мариинка Делюкс ──
    ('mar_hero', '905_after_mar_2.jpg',            WIDE, 0.50),
    ('mar_a',    '967_mar_intro_9.jpg',            PAIR, 0.50),
    ('mar_b',    'mar_intro_7.jpg',                PAIR, 0.50),
    ('mar_c',    'mar_details_2.jpg',              PAIR, 0.50),
    ('mar_d',    '895_mar_details.jpg',            PAIR, 0.50),
    ('mar_e',    '910_mar_filling_gal_4.jpg',      PAIR, 0.50),
    ('mar_f',    '911_mar_filling_gal_2.jpg',      PAIR, 0.50),
]

if __name__ == '__main__':
    os.makedirs(ASSETS, exist_ok=True)
    for name, src, ratio, focus in JOBS:
        path = os.path.join(HERE, 'src', 'full', src)
        if not os.path.exists(path):
            print(f'{name:9s} — нет файла {src}')
            continue
        im = crop_to(Image.open(path).convert('RGB'), ratio, focus)
        cap = 450 if ratio == PAIR else 1300
        if im.width > cap:
            im = im.resize((cap, int(cap / im.width * im.height)), Image.LANCZOS)
        out = os.path.join(ASSETS, name + '.jpg')
        im.save(out, 'JPEG', quality=86, subsampling=0, optimize=True)
        print(f'{name:9s} <- {src:34s} {im.size}  {os.path.getsize(out) // 1024} КБ')
