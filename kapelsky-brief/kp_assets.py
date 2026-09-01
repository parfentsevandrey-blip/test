"""Подготовка картинок для вёрстки: PNG -> JPEG в assets/.

Карты и график рисуются в PNG с двукратным запасом по разрешению, а в
документе стоят шириной 643 px. При таком запасе качество 80 на странице
неотличимо от 92, но файл легче втрое.

Визуализации приводит к JPEG отдельный скрипт kp_renders.py — он кропает
webp с сайта проекта под форматы вёрстки.
"""
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, 'assets')

CONVERT = [
    ('kp_map_area.png', 'map_area.jpg', 80),
    ('kp_map_city.png', 'map_city.jpg', 80),
    ('kp_chart.png',    'chart.jpg',    86),
]

if __name__ == '__main__':
    os.makedirs(ASSETS, exist_ok=True)
    for src, dst, q in CONVERT:
        p = os.path.join(ASSETS, src)
        if not os.path.exists(p):
            p = os.path.join(HERE, src)
        im = Image.open(p).convert('RGB')
        out = os.path.join(ASSETS, dst)
        im.save(out, 'JPEG', quality=q, subsampling=0, optimize=True)
        print(f'{src} {im.size} -> assets/{dst}  {os.path.getsize(out) // 1024} КБ')
