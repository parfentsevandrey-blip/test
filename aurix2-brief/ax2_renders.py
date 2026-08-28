"""Кадры для страниц визуализаций.

Источники двух видов:
  src/img/   — галерея со страниц проектов на aurix-development.ru
  src/img2/  — лендинги arbat2.ru и omega-residence.com; в подвале обоих
               написано, что официальными сайтами застройщика они не являются,
               но визуализации там те же, просто их больше

Планировки лежат отдельно, в plans/, и в этот скрипт не попадают: они идут
в карточки лотов как есть, квадратными PNG.

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


#  выход,        файл,                 формат, фокус
JOBS = [
    # ── Арбат 2 ──
    ('ar_hero', 'img2/ar_final.jpg',  WIDE, 0.50),
    ('ar_a',    'img2/ar_about.jpg',  PAIR, 0.50),
    ('ar_j',    'img/arbat_0.jpg',    PAIR, 0.50),
    ('ar_b',    'img/arbat_2.jpg',    PAIR, 0.50),
    ('ar_c',    'img2/ar_location.jpg', PAIR, 0.35),
    ('ar_d',    'img/arbat_3.jpg',    PAIR, 0.50),
    ('ar_e',    'img2/ar_flats.jpg',  PAIR, 0.50),
    ('ar_f',    'img2/ar_tech.jpg',   PAIR, 0.50),
    ('ar_g',    'img2/ar_service.jpg', PAIR, 0.50),
    ('ar_h',    'img/arbat_10.jpg',   PAIR, 0.50),
    ('ar_i',    'img/arbat_9.jpg',    PAIR, 0.50),

    # ── Резиденция Омега ──
    ('om_hero', 'img2/om_about.jpg',  WIDE, 0.50),
    ('om_a',    'img2/om_arch.jpg',   PAIR, 0.50),
    ('om_b',    'img2/om_final.jpg',  PAIR, 0.50),
    ('om_c',    'img2/om_location.jpg', PAIR, 0.50),
    ('om_d',    'img2/om_dvor.jpg',   PAIR, 0.50),
    ('om_e',    'img2/om_lobby.jpg',  PAIR, 0.50),
    ('om_f',    'img2/om_tech.jpg',   PAIR, 0.50),
    ('om_g',    'img2/om_flats.jpg',  PAIR, 0.50),
    ('om_h',    'img/omega_4.jpg',    PAIR, 0.50),
    ('om_i',    'img2/om_parking.jpg', PAIR, 0.40),
]

if __name__ == '__main__':
    os.makedirs(ASSETS, exist_ok=True)
    for name, src, ratio, focus in JOBS:
        path = os.path.join(HERE, 'src', src)
        if not os.path.exists(path):
            print(f'{name:9s} — нет файла {src}')
            continue
        im = crop_to(Image.open(path).convert('RGB'), ratio, focus)
        cap = 450 if ratio == PAIR else 1300
        if im.width > cap:
            im = im.resize((cap, int(cap / im.width * im.height)), Image.LANCZOS)
        out = os.path.join(ASSETS, name + '.jpg')
        im.save(out, 'JPEG', quality=86, subsampling=0, optimize=True)
        print(f'{name:9s} <- {src:22s} {im.size}  {os.path.getsize(out) // 1024} КБ')
