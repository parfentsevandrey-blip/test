"""Кадры визуализаций для вёрстки.

Исходники — src/img/, скачаны с официального сайта kapelsky5.ru: сайт на
Next.js, пути к картинкам лежат прямо в разметке (/img/home/...webp).
Здесь webp приводится к JPEG и кропается под два формата вёрстки:
2,30 : 1 для кадра во всю полосу и 16 : 9 для ряда из трёх.

Дом не построен: всё, что в этой папке, — проектные визуализации.
"""
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, 'src', 'img')
ASSETS = os.path.join(HERE, 'assets')
WIDE, PAIR, HERO = 2.30, 16 / 9, 1.75


def crop_to(im, ratio, focus=0.5):
    w, h = im.size
    if w / h > ratio:
        nw = int(h * ratio)
        return im.crop((int((w - nw) * focus), 0, int((w - nw) * focus) + nw, h))
    nh = int(w / ratio)
    return im.crop((0, int((h - nh) * focus), w, int((h - nh) * focus) + nh))


#  выход,     файл,                      формат, фокус по короткой стороне
JOBS = [
    # ── общий вид дома ──
    ('hero',   'home_t6_v5.webp',          HERO, 0.50),
    ('kvartal', 'home_genplan_v3_mob.webp', WIDE, 0.50),

    # ── фасады ──
    ('fas_a',  'home_video_1_v3.webp',     PAIR, 0.50),
    ('fas_b',  'home_t4_v2__.webp',        PAIR, 0.50),
    ('fas_c',  'home_mob_3.webp',          PAIR, 0.45),
    ('fas_d',  'home_slider_img7_v2.webp', PAIR, 0.50),

    # ── верхние этажи и террасы ──
    ('top_a',  'home_family_fam_v1.webp',  PAIR, 0.45),
    ('top_b',  'home_t5_v3.webp',          PAIR, 0.45),
    ('top_c',  'home_taste_mbig5.webp',    PAIR, 0.35),

    # ── двор ──
    ('yard_a', 'home_slider_img1_v3.webp', PAIR, 0.50),
    ('yard_b', 'home_slider_img3.webp',    PAIR, 0.50),
    ('yard_c', 'home_child_child.webp',    PAIR, 0.40),
    ('yard_d', 'home_slider_img8_v3.webp', PAIR, 0.50),

    # ── общие зоны и паркинг ──
    ('in_a',   'home_t2_1_new.webp',       PAIR, 0.50),
    ('in_b',   'home_infra_2_v1.webp',     PAIR, 0.50),
    ('in_c',   'home_family_2.webp',       PAIR, 0.50),

]

if __name__ == '__main__':
    os.makedirs(ASSETS, exist_ok=True)
    for name, src, ratio, focus in JOBS:
        path = os.path.join(SRC, src)
        if not os.path.exists(path):
            print(f'{name:9s} — нет файла {src}')
            continue
        im = crop_to(Image.open(path).convert('RGB'), ratio, focus)
        cap = 450 if ratio == PAIR else 1300
        if im.width > cap:
            im = im.resize((cap, int(cap / im.width * im.height)), Image.LANCZOS)
        out = os.path.join(ASSETS, name + '.jpg')
        im.save(out, 'JPEG', quality=86, subsampling=0, optimize=True)
        print(f'{name:9s} <- {src:28s} {im.size}  {os.path.getsize(out) // 1024} КБ')
