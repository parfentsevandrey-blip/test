# -*- coding: utf-8 -*-
"""Подготовка картинок для вёрстки: PNG -> JPEG в assets/.

Карты и графики рисуются с двукратным запасом по разрешению, а в документе
стоят шириной 560–643 px. При таком запасе качество 80 на странице
неотличимо от 92, а файл легче втрое.

Визуализации кропает ac_renders.py, планировки готовит ac_plans.py.
"""
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, 'assets')

CONVERT = [
    ('map_block.png',    'map_block.jpg',    80),
    ('map_city.png',     'map_city.jpg',     80),
    ('map_peers.png',    'map_peers.jpg',    80),
    ('chart_cohort.png', 'chart_cohort.jpg', 86),
    ('chart_floor.png',  'chart_floor.jpg',  86),
]

if __name__ == '__main__':
    for src, dst, q in CONVERT:
        p = os.path.join(ASSETS, src)
        im = Image.open(p).convert('RGB')
        out = os.path.join(ASSETS, dst)
        im.save(out, 'JPEG', quality=q, subsampling=0, optimize=True)
        print(f'{src} {im.size} -> assets/{dst}  {os.path.getsize(out) // 1024} КБ')
