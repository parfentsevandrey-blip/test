import sys, math; sys.path.insert(0,'.')
from PIL import ImageDraw
from ymap import render
from markers import pin, label, font

NAVY, RED = (31, 42, 68), (179, 40, 45)
PH = (37.6294, 55.77278)

def scalebar(dr, W, H, Z, lat, S=2, metres=300):
    mpp = 156543.03392 * math.cos(math.radians(lat)) / (2 ** Z)
    px = int(metres / mpp * S); x0, y0 = 34, H * S - 52
    dr.rounded_rectangle([x0-12, y0-30, x0+px+12, y0+16], 6, fill=(255,255,255,225))
    dr.line([x0, y0, x0+px, y0], fill=(40,46,58), width=4)
    dr.line([x0, y0-9, x0, y0+5], fill=(40,46,58), width=4)
    dr.line([x0+px, y0-9, x0+px, y0+5], fill=(40,46,58), width=4)
    dr.text((x0, y0-30), f'{metres} м', font=font(24, True), fill=(40,46,58))
    dr.text((W*S-260, H*S-36), '© Яндекс Карты', font=font(22), fill=(90,96,108))

# ── location ───────────────────────────────────────────────────────────────
W, H, Z = 760, 420, 16
base, proj = render((37.6288, 55.7728), Z, W, H, scale=2)
img = base.convert('RGBA'); dr = ImageDraw.Draw(img, 'RGBA')
x, y = proj(*PH); x, y = int(x), int(y)
pin(dr, x, y, r=27, fill=RED)
label(img, dr, x, y+14, 'КЛУБНЫЙ ДОМ PHANTOM', 'Малая Сухаревская пл., 6 · Sense', fs=36,
      bg=RED, fg=(255,255,255), sfg=(255,214,214))
mx, my = proj(37.63172, 55.77308)
label(img, dr, int(mx)+40, int(my)-34, 'м. «Сухаревская»', '≈4 мин пешком', fs=30, anchor='left',
      bg=NAVY, fg=(255,255,255), sfg=(186,196,214))
scalebar(dr, W, H, Z, 55.7728, metres=200)
img.convert('RGB').save('ph_map_loc.png', quality=95); print('loc', img.size)

# ── competitive environment ────────────────────────────────────────────────
W, H, Z = 820, 560, 15
CEN = (37.6235, 55.7695)
base, proj = render(CEN, Z, W, H, scale=2)
img = base.convert('RGBA'); dr = ImageDraw.Draw(img, 'RGBA')
SITES = [  # lon, lat, no, name, sub, pos, dx, anchor
    (37.63548, 55.76935, 1, 'Turgenev',        'элит · сдан 2023', 'below',    0, 'center'),
    (37.62740, 55.77301, 2, 'Форум',           'элит · сдан 2026', 'below', -150, 'right'),
    (37.62695, 55.76509, 3, 'Дом Франка',      'элит · 2026',      'below',    0, 'center'),
    (37.62772, 55.76886, 4, 'Сретенка 13/26',  'элит · строится',  'below',  -60, 'center'),
    (37.61780, 55.76552, 5, 'La Rue',          'элит · строится',  'below',    0, 'center'),
    (37.62152, 55.76473, 6, 'Zvonarsky Delux', 'элит · сдан 2019', 'above',  110, 'center'),
    (37.61143, 55.76612, 7, 'На Страстном',    'элит · сдан 2002', 'above',    0, 'center'),
]
for lon, lat, no, name, sub, pos, dx, anch in SITES:
    x, y = proj(lon, lat); x, y = int(x), int(y)
    pin(dr, x, y, r=24, fill=NAVY, num=no)
    ly = y + 16 if pos == 'below' else y - 190
    label(img, dr, x + dx, ly, name, sub, fs=32, anchor=anch)
x, y = proj(*PH); x, y = int(x), int(y)
pin(dr, x, y, r=28, fill=RED)
label(img, dr, x, y + 18, 'PHANTOM', 'делюкс · III кв. 2027', fs=38,
      bg=RED, fg=(255,255,255), sfg=(255,214,214))
scalebar(dr, W, H, Z, CEN[1], metres=500)
img.convert('RGB').save('ph_map_comp.png', quality=95); print('comp', img.size)
