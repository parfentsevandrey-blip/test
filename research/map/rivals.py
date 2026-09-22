#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Торговые центры в окружении: карта и расстояния.

Координаты получены поиском по Яндекс.Картам (карточка организации отдаёт
точку), расстояния считаются по прямой от дома 28 по улице Ленина.
"""
import math, subprocess, os, json
from PIL import Image, ImageDraw, ImageFont

OURS = (55.760702, 37.242607)
FB = '/usr/share/fonts/truetype/paratype/PTS75F.ttf'
FR = '/usr/share/fonts/truetype/paratype/PTS55F.ttf'
FN = '/usr/share/fonts/truetype/paratype/PTN57F.ttf'

# название, адрес, широта, долгота, формат
MALLS = [
    ('ТЦ «Амбар 2»',            'с. Ильинское, ул. Ленина, 26',        55.760611, 37.242274, 'районный, 1 эт. + цоколь'),
    ('ТК «Ильинка Вилладж»',    'с. Ильинское, ул. Ленина, 30',        55.761284, 37.243271, 'премиальный, малоэтажный'),
    ('ТЦ «Ильинский»',          'с. Ильинское, ул. Ленина, 11',        55.758722, 37.237021, 'районный, 2 эт. + цоколь'),
    ('Dream House',             'д. Барвиха, 85/1',                    55.738524, 37.270322, 'тематический, 4 уровня'),
    ('Барвиха Luxury Village',  'д. Барвиха, Рублёво-Успенское ш., 1', 55.739333, 37.265281, 'люкс, формат «улица»'),
    ('Архангельское Аутлет',    'д. Воронки, 1, корп. 2',              55.801329, 37.280934, 'аутлет'),
    ('РигаМолл',                'Новорижское шоссе',                   55.799865, 37.274366, 'окружной'),
    ('Гипермаркет «Глобус»',    'Новорижское ш., 22-й км',             55.802167, 37.300347, 'гипермаркет'),
    ('ТРК «Павлово Подворье»',  'д. Новинки, 115, стр. 2',             55.810458, 37.128367, 'окружной, 2 эт.'),
    ('ТЦ «Октябрь»',            'с. Павловская Слобода, Октябрьская, 1', 55.813618, 37.088354, 'районный, 3 эт. + цоколь'),
    ('ТЦ «Княжий двор»',        'д. Борзые, Невская ул., 704',         55.812426, 37.041998, 'районный, 3 уровня'),
    ('Новая Рига Аутлет',       'д. Покровское, Центральная ул., 33',  55.811801, 37.023633, 'аутлет'),
    ('Vegas Крокус Сити',       'Красногорск, Международная ул., 12',  55.820766, 37.388148, 'суперрегиональный, 6 эт.'),
    ('ТЦ «Красногорский»',      'Красногорск, ул. Ленина, 40',         55.834573, 37.297540, 'районный, 2 эт.'),
]


def metres(a, b):
    return math.hypot((a[0] - b[0]) * 111320,
                      (a[1] - b[1]) * 111320 * math.cos(math.radians(a[0])))


def fetch(name, pts, padm, size=(650, 450)):
    lats = [p[0] for p in pts] + [OURS[0]]
    lngs = [p[1] for p in pts] + [OURS[1]]
    clat = sum(lats) / len(lats)
    dlat = padm / 111320
    dlng = padm / (111320 * math.cos(math.radians(clat)))
    lo = (min(lngs) - dlng, min(lats) - dlat)
    hi = (max(lngs) + dlng, max(lats) + dlat)
    mk = '~'.join(f'{p[1]:.6f},{p[0]:.6f},pm2dbm{p[2]}' for p in pts)
    mk += f'~{OURS[1]:.6f},{OURS[0]:.6f},pm2rdl'
    url = (f'https://static-maps.yandex.ru/1.x/?l=map&size={size[0]},{size[1]}'
           f'&bbox={lo[0]:.6f},{lo[1]:.6f}~{hi[0]:.6f},{hi[1]:.6f}&pt={mk}')
    p = f'map/{name}.png'
    subprocess.run(['curl', '-sS', '--max-time', '60', '-o', p, url], check=True)
    im = Image.open(p)
    print(f'  {name:<10} {im.size}  {os.path.getsize(p)} B')
    return im.convert('RGB')


def main():
    os.makedirs('map', exist_ok=True)
    os.makedirs('doc_img', exist_ok=True)
    rows = []
    for name, addr, lat, lng, fmt in MALLS:
        rows.append({'name': name, 'addr': addr, 'lat': lat, 'lng': lng,
                     'fmt': fmt, 'km': metres(OURS, (lat, lng)) / 1000})
    rows.sort(key=lambda r: r['km'])
    for i, r in enumerate(rows, 1):
        r['n'] = i

    wide = fetch('rv_wide', [(r['lat'], r['lng'], r['n']) for r in rows], 900)
    near_rows = [r for r in rows if r['km'] < 1]
    near = fetch('rv_near', [(r['lat'], r['lng'], r['n']) for r in near_rows], 130)

    bold = ImageFont.truetype(FB, 26)
    W = wide.width + 16 + near.width
    H = 40 + wide.height
    c = Image.new('RGB', (W, H), 'white')
    d = ImageDraw.Draw(c)
    d.text((0, 6), 'Все торговые центры в зоне охвата', font=bold, fill='#1E1C19')
    d.text((wide.width + 16, 6), 'Улица Ленина крупным планом', font=bold, fill='#1E1C19')
    c.paste(wide, (0, 40))
    c.paste(near, (wide.width + 16, 40))
    c.save('doc_img/rivals_map.jpg', quality=92, subsampling=0)
    print('→ doc_img/rivals_map.jpg', c.size)

    json.dump(rows, open('rivals.json', 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    for r in rows:
        print(f"  {r['n']:>2}  {r['name']:<24} {r['km']:5.1f} км  {r['fmt']}")


if __name__ == '__main__':
    main()
