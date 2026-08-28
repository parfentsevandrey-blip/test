"""Подготовка картинок для вёрстки: PNG -> JPEG в assets/.

Карта и график рисуются в PNG (ed_map.py, ed_chart.py), но в docx кладутся
JPEG: PNG карты весит 750 КБ, JPEG того же качества — 235 КБ, а страниц
с картинками в справке пять. Визуализации из Telegram уже приходят JPEG
и просто копируются под именами r1..r4 (см. ed_tg.py).
"""
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, 'assets')

#  исходник,      результат,   качество
#  карта рисуется 1840×1120, а в документе стоит шириной 643 px — почти
#  трёхкратный запас, поэтому качество 82 на странице неотличимо от 92,
#  но файл легче на четверть
CONVERT = [
    ('spb_map.png',     'map.jpg',     80),
    ('spb_map_ldm.png', 'map_ldm.jpg', 78),
    ('spb_map_mar.png', 'map_mar.jpg', 78),
]

if __name__ == '__main__':
    os.makedirs(ASSETS, exist_ok=True)
    for src, dst, q in CONVERT:
        p = os.path.join(HERE, src)
        if not os.path.exists(p):
            p = os.path.join(ASSETS, src)
        im = Image.open(p).convert('RGB')
        out = os.path.join(ASSETS, dst)
        im.save(out, 'JPEG', quality=q, subsampling=0, optimize=True)
        print(f'{src} {im.size} -> assets/{dst}  {os.path.getsize(out) // 1024} КБ')
