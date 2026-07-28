import sys, math; sys.path.insert(0,'.')
from PIL import Image, ImageDraw
from ymap import render
from markers import pin, label, font

S = 2
W, H, Z = 820, 560, 14
CENTER = (37.5412, 55.7875)
base, proj = render(CENTER, Z, W, H, scale=S)
img = base.convert('RGBA'); dr = ImageDraw.Draw(img, 'RGBA')

NAVY = (31, 42, 68)
COMP = [   # lon, lat, no, name, sub, label position, x-offset, anchor
    (37.54273, 55.79315, 1, 'Прайм Парк',      'премиум · сдан 2021–2024', 'below',    0, 'center'),
    (37.52038, 55.79818, 2, 'Триумф Палас',    'элит · сдан 2006',         'below',  -10, 'right'),
    (37.53841, 55.78911, 3, 'Лайнер',          'бизнес · сдан 2017–2019',  'below',  -18, 'right'),
    (37.54164, 55.78590, 4, 'Лица',            'бизнес · сдан 2021',       'below',    0, 'center'),
    (37.56196, 55.78490, 5, 'Царская площадь', 'бизнес · сдан 2018–2020',  'below',    0, 'center'),
    (37.52733, 55.77666, 6, 'Династия',        'бизнес · сдан 2019–2023',  'above',    0, 'center'),
]
for lon, lat, no, name, sub, pos, dx, anch in COMP:
    x, y = proj(lon, lat); x, y = int(x), int(y)
    pin(dr, x, y, r=24, fill=NAVY, num=no)
    ly = y + 16 if pos == 'below' else y - 190
    label(img, dr, x + dx, ly, name, sub, fs=34, anchor=anch)

x, y = proj(37.52304, 55.79527); x, y = int(x), int(y)
pin(dr, x, y, r=28, fill=(179, 40, 45))
label(img, dr, x, y + 18, 'ЖК «МАСТЕРС»', 'премиум · IV кв. 2029', fs=38,
      bg=(179, 40, 45), fg=(255, 255, 255), sfg=(255, 214, 214))

mpp = 156543.03392 * math.cos(math.radians(CENTER[1])) / (2 ** Z)
px = int(500 / mpp * S); x0, y0 = 34, H * S - 52
dr.rounded_rectangle([x0-12, y0-30, x0+px+12, y0+16], 6, fill=(255, 255, 255, 225))
dr.line([x0, y0, x0+px, y0], fill=(40, 46, 58), width=4)
dr.line([x0, y0-9, x0, y0+5], fill=(40, 46, 58), width=4)
dr.line([x0+px, y0-9, x0+px, y0+5], fill=(40, 46, 58), width=4)
dr.text((x0, y0-30), '500 м', font=font(24, True), fill=(40, 46, 58))
dr.text((W*S-260, H*S-36), '© Яндекс Карты', font=font(22), fill=(90, 96, 108))
img.convert('RGB').save('map_competitors.png', quality=95)
print('saved', img.size)
