import sys; sys.path.insert(0,'.')
from PIL import Image, ImageDraw
from ymap import render
from markers import pin, label, font

S = 2
W, H, Z = 760, 420, 15
base, proj = render((37.5250, 55.7975), Z, W, H, scale=S)
img = base.convert('RGBA'); dr = ImageDraw.Draw(img, 'RGBA')

x, y = proj(37.52304, 55.79527)
pin(dr, int(x), int(y), r=27, fill=(179,40,45))
label(img, dr, int(x), int(y)+14, 'ЖК «МАСТЕРС»', 'ул. Викторенко, 16 · Capital Group', fs=38)

mx, my = proj(37.52939, 55.80081)
label(img, dr, int(mx)+40, int(my)-34, 'м. «Аэропорт»', '≈10 мин пешком', fs=30, anchor='left',
      bg=(31,42,68), fg=(255,255,255), sfg=(186,196,214))

# scale bar
import math
mpp = 156543.03392 * math.cos(math.radians(55.7975)) / (2**Z)   # metres per CSS px
for target in (500, 300, 200):
    px = target/mpp
    if px < W*0.30: break
px = int(px*S); x0, y0 = 34, H*S-52
dr.rounded_rectangle([x0-12, y0-30, x0+px+12, y0+16], 6, fill=(255,255,255,225))
dr.line([x0, y0, x0+px, y0], fill=(40,46,58), width=4)
dr.line([x0, y0-9, x0, y0+5], fill=(40,46,58), width=4)
dr.line([x0+px, y0-9, x0+px, y0+5], fill=(40,46,58), width=4)
dr.text((x0, y0-30), f'{target} м', font=font(24, True), fill=(40,46,58))
dr.text((W*S-260, H*S-36), '© Яндекс Карты', font=font(22), fill=(90,96,108))
img.convert('RGB').save('map_location.png', quality=95)
print('saved', img.size)
