"""Карта локации ЖК «Левел Академическая». Координаты — геокодер OSM."""
import sys, math; sys.path.insert(0, '.')
from PIL import ImageDraw
from ymap import render
from markers import pin, label, font

NAVY, RED = (31, 42, 68), (179, 40, 45)
OUR = (37.572258, 55.687484)                       # Профсоюзная, 2/22
W, H, Z = 820, 560, 14
CEN = (37.5726, 55.6924)
base, proj = render(CEN, Z, W, H, scale=2)
img = base.convert('RGBA'); dr = ImageDraw.Draw(img, 'RGBA')

SITES = [
    (37.571731, 55.703869, 'ЖК Lunar',        '1 084 480 ₽/м² · сдан 2024', -120,   14, 'center'),
    (37.593837, 55.700832, 'ЖК «Файв Тауэрс»', '638 305 ₽/м² · white box',  -44,  -96, 'right'),
    (37.571274, 55.696560, '«Вавилов ДОМ»',    '579 563 ₽/м² · 2019',      -44,   14, 'right'),
    (37.579824, 55.695331, '«Новые Черемушки»', '564 314 ₽/м² · 2020',      44,   14, 'left'),
    (37.556902, 55.689417, 'Вавилова, 52к1',   '651 689 ₽/м² · 2020',        0,   14, 'center'),
    (37.583155, 55.682842, 'Новочерёмушкинская, 17', '854 374 ₽/м² · 2020',  44,   14, 'left'),
    (37.551127, 55.681021, 'VAVILOVE',         '565 523 ₽/м² · 2019',        0, -100, 'center'),
]
for lon, lat, name, sub, dx, dy, anc in SITES:
    x, y = proj(lon, lat); x, y = int(x), int(y)
    pin(dr, x, y, r=22, fill=NAVY)
    label(img, dr, x + dx, y + dy, name, sub, fs=30, anchor=anc)

x, y = proj(*OUR); x, y = int(x), int(y)
pin(dr, x, y, r=27, fill=RED)
label(img, dr, x, y + 16, 'НАШ ЛОТ · Левел Академическая', '57,0 млн ₽ · 716 981 ₽/м² · бетон', fs=34,
      bg=RED, fg=(255, 255, 255), sfg=(255, 214, 214))

mpp = 156543.03392 * math.cos(math.radians(CEN[1])) / (2 ** Z)
px = int(1000 / mpp * 2); x0, y0 = 34, H * 2 - 52
dr.rounded_rectangle([x0 - 12, y0 - 30, x0 + px + 12, y0 + 16], 6, fill=(255, 255, 255, 225))
dr.line([x0, y0, x0 + px, y0], fill=(40, 46, 58), width=4)
dr.line([x0, y0 - 9, x0, y0 + 5], fill=(40, 46, 58), width=4)
dr.line([x0 + px, y0 - 9, x0 + px, y0 + 5], fill=(40, 46, 58), width=4)
dr.text((x0, y0 - 30), '1 км', font=font(24, True), fill=(40, 46, 58))
dr.text((W * 2 - 260, H * 2 - 36), '© Яндекс Карты', font=font(22), fill=(90, 96, 108))
img.convert('RGB').save('lev/lv_map.png', quality=95); print('saved', img.size, 643/(W/H))
