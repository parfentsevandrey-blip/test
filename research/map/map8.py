#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Карта ретейл-кластера улицы Ленина для раздела 4.1.

Вместо прежней схемы с условным положением объектов — настоящая карта.
Координаты конкурентов взяты из карточек ЦИАН по тем же адресам,
координата объекта — геокод дома 28 (та же точка, что и на карте раздела 3).
"""
import math, subprocess, os
from PIL import Image, ImageDraw, ImageFont

OUT = 'doc_img/c8_map.jpg'
FB = '/usr/share/fonts/truetype/paratype/PTS75F.ttf'
FR = '/usr/share/fonts/truetype/paratype/PTS55F.ttf'
FN = '/usr/share/fonts/truetype/paratype/PTN57F.ttf'

OURS = (55.760702, 37.242607)
PTS = [  # номер, широта, долгота, подпись, что это
    (1, 55.758808, 37.236453, 'ТЦ «Ильинский»', 'ул. Ленина, 11'),
    (2, 55.760611, 37.242274, 'ТЦ «Амбар 2»', 'ул. Ленина, 26'),
    (3, 55.761284, 37.243271, 'ТК «Ильинка Вилладж»', 'ул. Ленина, 30'),
]


def metres(a, b):
    return math.hypot((a[0] - b[0]) * 111320,
                      (a[1] - b[1]) * 111320 * math.cos(math.radians(a[0])))


def fetch(name, lats, lngs, padm, size=(650, 450)):
    """Статическая карта Яндекса по bbox с запасом padm метров по краям."""
    clat = sum(lats) / len(lats)
    dlat = padm / 111320
    dlng = padm / (111320 * math.cos(math.radians(clat)))
    lo = (min(lngs) - dlng, min(lats) - dlat)
    hi = (max(lngs) + dlng, max(lats) + dlat)
    pt = '~'.join(f'{p[2]:.6f},{p[1]:.6f},pm2dbm{p[0]}' for p in PTS
                  if lo[1] <= p[1] <= hi[1] and lo[0] <= p[2] <= hi[0])
    pt += f'~{OURS[1]:.6f},{OURS[0]:.6f},pm2rdl'
    url = (f'https://static-maps.yandex.ru/1.x/?l=map&size={size[0]},{size[1]}'
           f'&bbox={lo[0]:.6f},{lo[1]:.6f}~{hi[0]:.6f},{hi[1]:.6f}&pt={pt}')
    p = f'map/{name}.png'
    subprocess.run(['curl', '-sS', '--max-time', '60', '-o', p, url], check=True)
    im = Image.open(p)
    print(f'  {name:<6} {im.size}  {os.path.getsize(p)} B')
    return im.convert('RGB')


def main():
    os.makedirs('map', exist_ok=True)
    os.makedirs('doc_img', exist_ok=True)
    lats = [p[1] for p in PTS] + [OURS[0]]
    lngs = [p[2] for p in PTS] + [OURS[1]]
    wide = fetch('c8wide', lats, lngs, 110)
    near = fetch('c8near', [p[1] for p in PTS[1:]] + [OURS[0]],
                 [p[2] for p in PTS[1:]] + [OURS[1]], 90)

    # кегль подобран под масштаб в документе: картинка ужимается вдвое,
    # поэтому 23 px здесь — это примерно 8,7 пункта на странице
    bold, reg, nar = (ImageFont.truetype(FB, 26), ImageFont.truetype(FR, 23),
                      ImageFont.truetype(FN, 22))
    small = ImageFont.truetype(FR, 18)
    HDR, GAP, LEG = 40, 16, 164
    W = wide.width + GAP + near.width
    H = HDR + wide.height + LEG
    c = Image.new('RGB', (W, H), 'white')
    d = ImageDraw.Draw(c)
    d.text((0, 6), 'Улица Ленина: кто уже работает рядом', font=bold, fill='#1E1C19')
    d.text((wide.width + GAP, 6), 'Наш квартал крупным планом', font=bold, fill='#1E1C19')
    c.paste(wide, (0, HDR))
    c.paste(near, (wide.width + GAP, HDR))

    # легенда: наш объект и три конкурента с расстоянием по прямой
    y, R = HDR + wide.height + 18, 11
    d.ellipse((2, y + 4, 2 + 2 * R, y + 4 + 2 * R), fill='#933A20')
    d.text((2 + 2 * R + 12, y), 'ТЦ «Амбар 1», ул. Ленина, 28 — наш объект, 2 191,5 м², '
           '42 парковочных места', font=reg, fill='#1E1C19')
    y += 36
    for num, lat, lng, name, addr in PTS:
        d.ellipse((2, y + 4, 2 + 2 * R, y + 4 + 2 * R), fill='#25599C')
        w = d.textlength(str(num), font=small)
        d.text((2 + R - w / 2, y + 7), str(num), font=small, fill='white')
        m = metres(OURS, (lat, lng))
        dist = round(m / 5) * 5 if m < 100 else round(m / 10) * 10
        d.text((2 + 2 * R + 12, y), f'{name}, {addr}', font=reg, fill='#1E1C19')
        off = 2 + 2 * R + 12 + d.textlength(f'{name}, {addr}', font=reg)
        d.text((off, y + 1), f'  — {dist} м по прямой', font=nar, fill='#6E6860')
        y += 36

    c.save(OUT, quality=92, subsampling=0)
    print('→', OUT, c.size)
    for num, lat, lng, name, addr in PTS:
        print(f'   {name:<24} {metres(OURS, (lat, lng)):6.0f} м')


if __name__ == '__main__':
    main()
