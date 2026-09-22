# -*- coding: utf-8 -*-
"""Подготовка картинок для вёрстки: PNG -> JPEG в assets/."""
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, 'assets')
CONVERT = [
    ('map_site.png',     'map_site.jpg',     80),
    ('map_city.png',     'map_city.jpg',     80),
    ('map_peers.png',    'map_peers.jpg',    80),
    ('chart_cohort.png', 'chart_cohort.jpg', 86),
    ('chart_hist.png',   'chart_hist.jpg',   86),
]

if __name__ == '__main__':
    for src, dst, q in CONVERT:
        im = Image.open(os.path.join(ASSETS, src)).convert('RGB')
        out = os.path.join(ASSETS, dst)
        im.save(out, 'JPEG', quality=q, subsampling=0, optimize=True)
        print(f'{src} {im.size} -> assets/{dst}  {os.path.getsize(out) // 1024} КБ')
