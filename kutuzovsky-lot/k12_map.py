"""Карта локации. Координаты проектов сверены по геокодеру OSM / Яндекс Недвижимости:
   Кутузовский XII  55.748889 37.557152   (Кутузовский пр-т, 12)
   Веспер           55.749866 37.556760   (Кутузовский пр-т, 12с5)
   Бадаевский       55.751614 37.559420   (Украинский бульвар, 2с1)
   Capital Towers   55.751521 37.549107   (Краснопресненская наб., 14)
   Дом Дау          55.750934 37.540811   (Пресненская наб., 2А)
"""
import sys, math; sys.path.insert(0, '.')
from PIL import ImageDraw
from ymap import render
from markers import pin, label, font

NAVY, RED = (31, 42, 68), (179, 40, 45)
K12 = (37.557152, 55.748889)
W, H, Z = 800, 470, 15
CEN = (37.5501, 55.7502)
base, proj = render(CEN, Z, W, H, scale=2)
img = base.convert('RGBA'); dr = ImageDraw.Draw(img, 'RGBA')

# lon, lat, название, подпись, смещение подписи от острия пина, привязка подписи
SITES = [
    (37.559420, 55.751614, 'ЖК «Бадаевский»',    '1 607 089 ₽/м² · без отделки',   0, -105, 'center'),
    (37.556760, 55.749866, 'Веспер Кутузовский', '1 538 394 ₽/м² · без отделки', -46, -112, 'right'),
    (37.549107, 55.751521, 'Capital Towers',     '1 305 435 ₽/м² · сдан 2023',   -60, -105, 'center'),
    (37.540811, 55.750934, 'Дом Дау',            '1 021 252 ₽/м² · без отделки', -60,   16, 'center'),
]
for lon, lat, name, sub, dx, dy, anc in SITES:
    x, y = proj(lon, lat); x, y = int(x), int(y)
    pin(dr, x, y, r=24, fill=NAVY)
    label(img, dr, x + dx, y + dy, name, sub, fs=32, anchor=anc)

x, y = proj(*K12); x, y = int(x), int(y)
pin(dr, x, y, r=28, fill=RED)
label(img, dr, x, y + 18, 'НАШ ЛОТ · Кутузовский XII', '162,5 млн ₽ · 1 710 526 ₽/м²', fs=36,
      bg=RED, fg=(255, 255, 255), sfg=(255, 214, 214))

mpp = 156543.03392 * math.cos(math.radians(CEN[1])) / (2 ** Z)
px = int(500 / mpp * 2); x0, y0 = 34, H * 2 - 52
dr.rounded_rectangle([x0 - 12, y0 - 30, x0 + px + 12, y0 + 16], 6, fill=(255, 255, 255, 225))
dr.line([x0, y0, x0 + px, y0], fill=(40, 46, 58), width=4)
dr.line([x0, y0 - 9, x0, y0 + 5], fill=(40, 46, 58), width=4)
dr.line([x0 + px, y0 - 9, x0 + px, y0 + 5], fill=(40, 46, 58), width=4)
dr.text((x0, y0 - 30), '500 м', font=font(24, True), fill=(40, 46, 58))
dr.text((W * 2 - 260, H * 2 - 36), '© Яндекс Карты', font=font(22), fill=(90, 96, 108))
img.convert('RGB').save('k12_map.png', quality=95); print('saved', img.size)
