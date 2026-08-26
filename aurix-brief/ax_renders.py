"""Подготовка визуализаций для страниц площадок.

Все изображения — настоящие проектные визуализации, а не фотографии и не
сгенерированные картинки. Происхождение каждого кадра:

  Большая Ордынка, 25   — портфолио бюро «ЭталонПроект», страница проекта
  Малая Полянка, 3      — портфолио бюро «ЭталонПроект», страница проекта
  Земледельческий, 15   — публикация отраслевого Telegram-канала от 25.08.2026,
                          где три проекта AURIX показаны с авторской нумерацией;
                          собственной страницы у проекта нет ни у застройщика,
                          ни у бюро

Отдельно стоит сказать про сайт bolshayaordynka25.ru: он не принадлежит
застройщику (на официальном сайте AURIX страницы проекта нет), а картинки
на нём лежат под именами вида ChatGPT-Image-Apr-25-2026-*.png, то есть
сгенерированы нейросетью. В справку они не берутся.

Кадры кропаются под два формата вёрстки: 2,30 : 1 для широкой картинки
во всю ширину полосы и 16 : 9 для пары картинок в строку.
"""
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, 'src', 'img')
ASSETS = os.path.join(HERE, 'assets')

WIDE, PAIR = 2.30, 16 / 9


def crop_to(im, ratio, focus=0.5):
    """Обрезает до нужного соотношения; focus — где держать центр кадра (0..1)."""
    w, h = im.size
    if w / h > ratio:                       # слишком широкий — режем по бокам
        nw = int(h * ratio)
        x = int((w - nw) * focus)
        return im.crop((x, 0, x + nw, h))
    nh = int(w / ratio)                     # слишком высокий — режем сверху/снизу
    y = int((h - nh) * focus)
    return im.crop((0, y, w, y + nh))


#  выход,        исходник,      формат, фокус, доп. предварительный кроп (l,t,r,b)
JOBS = [
    ('ord_hero', 'ord_5.jpg',    WIDE, 0.50, None),
    ('ord_a',    'ord_0.jpg',    PAIR, 0.50, None),
    ('ord_b',    'ord_4.jpg',    PAIR, 0.50, None),

    ('zem_hero', 'tg35495_3.jpg', WIDE, 0.50, None),
    ('zem_a',    'tg35495_2.jpg', PAIR, 0.50, (60, 60, 800, 476)),
    ('zem_b',    'tg35495_2.jpg', PAIR, 0.50, (250, 30, 650, 255)),

    ('pol_hero', 'pol_5.jpg',    WIDE, 0.50, None),
    ('pol_a',    'pol_4.jpg',    PAIR, 0.50, None),
    ('pol_b',    'pol_7.jpg',    PAIR, 0.50, None),
]

if __name__ == '__main__':
    os.makedirs(ASSETS, exist_ok=True)
    for name, src, ratio, focus, pre in JOBS:
        im = Image.open(os.path.join(SRC, src)).convert('RGB')
        if pre:
            im = im.crop(pre)
        im = crop_to(im, ratio, focus)
        cap = 1300 if ratio == WIDE else 700   # двойной запас к ширине в вёрстке
        if im.width > cap:
            im = im.resize((cap, int(cap / im.width * im.height)), Image.LANCZOS)
        out = os.path.join(ASSETS, name + '.jpg')
        im.save(out, 'JPEG', quality=86, subsampling=0, optimize=True)
        print(f'{name:9s} <- {src:16s} {im.size}  {os.path.getsize(out) // 1024} КБ')
