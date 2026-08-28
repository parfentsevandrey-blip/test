"""Карты двух петербургских проектов AURIX.

Координаты обоих домов взяты со страниц проектов на aurix-development.ru —
они зашиты в разметку карты застройщика:

  ЛДМ               59.971471 / 30.286732   Аптекарский остров, Песочная наб.
  Мариинка Делюкс   59.923819 / 30.280157   Матисов остров, река Пряжка

Станции метро и расстояния по прямой — из OpenStreetMap.

Строится три кадра: общий (оба дома в одном кадре, Z=12) и два районных
крупным планом (Z=15), где видно ближайшее метро.
"""
import sys, os
from math import radians, cos, hypot

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import ImageDraw
from ymap import render
from markers import pin, label

HERE = os.path.dirname(os.path.abspath(__file__))
RED, NAVY = (179, 40, 45), (31, 42, 68)
R_EARTH = 6371000.0

LDM = (30.286732, 59.971471)
MAR = (30.280157, 59.923819)


def metres(lon1, lat1, lon2, lat2):
    dx = radians(lon2 - lon1) * cos(radians((lat1 + lat2) / 2)) * R_EARTH
    dy = radians(lat2 - lat1) * R_EARTH
    return hypot(dx, dy)


#  ключ:  (точка, центр кадра, зум, подпись, вторая строка, dx, dy, якорь, станции метро)
VIEWS = {
    'ldm': (LDM, (30.28100, 59.96740), 14, 'ЛДМ', 'Аптекарский остров · премиум',
            86, -190, 'left',
            [('Крестовский остров', 30.262192, 59.970596, -80, 44, 'right'),
             ('Чкаловская', 30.292420, 59.959334, 80, 44, 'left')]),
    'mar': (MAR, (30.29200, 59.92500), 14, 'Мариинка Делюкс', 'Матисов остров · премиум',
            -86, 44, 'right',
            [('Спасская', 30.317694, 59.926262, -80, -175, 'right')]),
}

W, H, S = 920, 615, 2

if __name__ == '__main__':
    os.makedirs(os.path.join(HERE, 'assets'), exist_ok=True)

    # ── два района крупным планом ──
    for key, (pt, cen, z, name, sub, dx, dy, anchor, metro) in VIEWS.items():
        base, proj = render(cen, z, W, H, scale=S)
        img = base.convert('RGBA'); dr = ImageDraw.Draw(img, 'RGBA')
        for mname, mlon, mlat, mdx, mdy, manchor in metro:
            mx, my = proj(mlon, mlat)
            pin(dr, mx, my, 17, NAVY)
            d = metres(pt[0], pt[1], mlon, mlat)
            label(img, dr, mx + mdx, my + mdy, 'м. «' + mname + '»',
                  f'≈ {round(d / 10) * 10:.0f} м от дома', manchor, 23,
                  (255, 255, 255), NAVY, (104, 112, 126), pad=14, radius=11)
            print(f'  {key}: м. {mname} — {d:.0f} м')
        x, y = proj(*pt)
        pin(dr, x, y, 26, RED)
        label(img, dr, x + dx, y + dy, name, sub, anchor, 30,
              (255, 255, 255), RED, (104, 112, 126), pad=17, radius=13)
        out = os.path.join(HERE, 'assets', f'spb_map_{key}.png')
        img.convert('RGB').save(out)
        print('written', out, img.size)

    # ── общий кадр: оба дома ──
    base, proj = render((30.28400, 59.94800), 12, W, 560, scale=S)
    img = base.convert('RGBA'); dr = ImageDraw.Draw(img, 'RGBA')
    for pt, nm, sub, dx, dy, anchor in (
            (LDM, 'ЛДМ', 'Аптекарский остров', 86, -190, 'left'),
            (MAR, 'Мариинка Делюкс', 'Матисов остров', 86, 44, 'left')):
        x, y = proj(*pt)
        pin(dr, x, y, 26, RED)
        label(img, dr, x + dx, y + dy, nm, sub, anchor, 30,
              (255, 255, 255), RED, (104, 112, 126), pad=17, radius=13)
    out = os.path.join(HERE, 'assets', 'spb_map.png')
    img.convert('RGB').save(out)
    print('written', out, img.size)
    print(f'между домами {metres(*LDM, *MAR) / 1000:.1f} км')
