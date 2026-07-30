import sys, math; sys.path.insert(0,'.')
from PIL import ImageDraw
from ymap import render
from markers import pin, label, font
NAVY, RED = (31, 42, 68), (179, 40, 45)
K12 = (37.55715, 55.74889)
W, H, Z = 800, 500, 15
CEN = (37.5488, 55.7494)
base, proj = render(CEN, Z, W, H, scale=2)
img = base.convert('RGBA'); dr = ImageDraw.Draw(img, 'RGBA')
SITES = [
    (37.55750, 55.75030, 'ЖК «Бадаевский»', '1 607 089 ₽/м² · без отделки', 'above', 150),
    (37.54398, 55.75013, 'Capital Towers',  '1 305 435 ₽/м² · сдан 2023',   'below', -30),
    (37.54100, 55.74760, 'Дом Дау',         '1 021 252 ₽/м² · без отделки', 'below',   0),
]
for lon, lat, name, sub, pos, dx in SITES:
    x, y = proj(lon, lat); x, y = int(x), int(y)
    pin(dr, x, y, r=24, fill=NAVY)
    label(img, dr, x + dx, y + 16 if pos == 'below' else y - 190, name, sub, fs=32)
x, y = proj(*K12); x, y = int(x), int(y)
pin(dr, x, y, r=28, fill=RED)
label(img, dr, x, y + 18, 'НАШ ЛОТ · Кутузовский XII', '162,5 млн ₽ · 1 710 526 ₽/м²', fs=36,
      bg=RED, fg=(255,255,255), sfg=(255,214,214))
mpp = 156543.03392 * math.cos(math.radians(CEN[1])) / (2 ** Z)
px = int(500 / mpp * 2); x0, y0 = 34, H*2 - 52
dr.rounded_rectangle([x0-12, y0-30, x0+px+12, y0+16], 6, fill=(255,255,255,225))
dr.line([x0, y0, x0+px, y0], fill=(40,46,58), width=4)
dr.line([x0, y0-9, x0, y0+5], fill=(40,46,58), width=4)
dr.line([x0+px, y0-9, x0+px, y0+5], fill=(40,46,58), width=4)
dr.text((x0, y0-30), '500 м', font=font(24, True), fill=(40,46,58))
dr.text((W*2-260, H*2-36), '© Яндекс Карты', font=font(22), fill=(90,96,108))
img.convert('RGB').save('k12_map.png', quality=95); print('saved', img.size)
