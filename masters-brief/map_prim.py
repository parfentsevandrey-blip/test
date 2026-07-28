"""Yandex map of the three primary-market projects, with price per m² on each marker."""
import sys, math; sys.path.insert(0, '.')
from PIL import ImageDraw
from ymap import render
from markers import pin, label, font

S = 2
W, H, Z = 1400, 720, 15
CENTER = (37.5350, 55.8012)
base, proj = render(CENTER, Z, W, H, scale=S)
img = base.convert('RGBA'); dr = ImageDraw.Draw(img, 'RGBA')

NAVY, RED = (31, 42, 68), (179, 40, 45)
SITES = [
    (37.52304, 55.79527, 'ЖК «МАСТЕРС»',   '761 469 ₽/м² · IV кв. 2029', RED,  'below', 21),
    (37.54691, 55.79839, 'МУЗА',           '1 042 477 ₽/м² · I–II кв. 2029', NAVY, 'below', 17),
    (37.53500, 55.80780, 'ДОМ НА ЧАСОВОЙ', '668 643 ₽/м² · II кв. 2028', NAVY, 'above', 17),
]
for lon, lat, name, sub, col, pos, r in SITES:
    x, y = proj(lon, lat); x, y = int(x), int(y)
    pin(dr, x, y, r=r, fill=col)
    is_m = col == RED
    label(img, dr, x, y + 12 if pos == 'below' else y - 170, name, sub,
          fs=25 if is_m else 21,
          bg=RED if is_m else (255, 255, 255),
          fg=(255, 255, 255) if is_m else (28, 34, 46),
          sfg=(255, 214, 214) if is_m else (110, 118, 132))

mpp = 156543.03392 * math.cos(math.radians(CENTER[1])) / (2 ** Z)
px = int(500 / mpp * S); x0, y0 = 34, H * S - 52
dr.rounded_rectangle([x0 - 12, y0 - 30, x0 + px + 12, y0 + 16], 6, fill=(255, 255, 255, 225))
dr.line([x0, y0, x0 + px, y0], fill=(40, 46, 58), width=4)
dr.line([x0, y0 - 9, x0, y0 + 5], fill=(40, 46, 58), width=4)
dr.line([x0 + px, y0 - 9, x0 + px, y0 + 5], fill=(40, 46, 58), width=4)
dr.text((x0, y0 - 28), '500 м', font=font(20, True), fill=(40, 46, 58))
dr.text((W * S - 232, H * S - 34), '© Яндекс Карты', font=font(19), fill=(90, 96, 108))
img.convert('RGB').save('map_primary.png', quality=95)
print('saved', img.size)
