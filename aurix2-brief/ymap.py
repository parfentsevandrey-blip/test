import math, io, os, sys, concurrent.futures as cf, urllib.request
from PIL import Image, ImageDraw, ImageFont

E = 0.0818191908426          # WGS84 eccentricity  (Yandex uses EPSG:3395)
TS = 256

def lonlat_to_px(lon, lat, z, scale=1):
    n = TS * scale * (2 ** z)
    x = (lon + 180.0) / 360.0 * n
    phi = math.radians(lat)
    s = math.sin(phi)
    y_ = math.log(math.tan(math.pi/4 + phi/2)) - (E/2) * math.log((1 + E*s) / (1 - E*s))
    y = (1 - y_ / math.pi) / 2 * n
    return x, y

UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36'
TILE = ('https://core-renderer-tiles.maps.yandex.net/tiles?l={layer}&x={x}&y={y}&z={z}'
        '&scale={scale}&lang=ru_RU&v=25.07.14-0')

def fetch(url):
    for _ in range(4):
        try:
            req = urllib.request.Request(url, headers={'User-Agent': UA, 'Referer': 'https://yandex.ru/maps/'})
            return Image.open(io.BytesIO(urllib.request.urlopen(req, timeout=30).read())).convert('RGBA')
        except Exception:
            pass
    return Image.new('RGBA', (TS, TS), (240, 238, 234, 255))

def render(center, z, w, h, scale=2, layer='map'):
    """center=(lon,lat); w,h in CSS px. Returns (PIL image at w*scale x h*scale, proj fn)."""
    cx, cy = lonlat_to_px(*center, z, scale)
    px0, py0 = cx - w*scale/2, cy - h*scale/2          # top-left in world px
    t = TS * scale
    tx0, ty0 = int(px0 // t), int(py0 // t)
    tx1, ty1 = int((px0 + w*scale) // t), int((py0 + h*scale) // t)
    canvas = Image.new('RGBA', ((tx1-tx0+1)*t, (ty1-ty0+1)*t))
    jobs = [(x, y) for x in range(tx0, tx1+1) for y in range(ty0, ty1+1)]
    with cf.ThreadPoolExecutor(16) as ex:
        imgs = list(ex.map(lambda j: fetch(TILE.format(layer=layer, x=j[0], y=j[1], z=z, scale=scale)), jobs))
    for (x, y), im in zip(jobs, imgs):
        if im.size != (t, t): im = im.resize((t, t))
        canvas.paste(im, ((x-tx0)*t, (y-ty0)*t))
    ox, oy = px0 - tx0*t, py0 - ty0*t
    out = canvas.crop((int(ox), int(oy), int(ox)+w*scale, int(oy)+h*scale)).convert('RGB')
    def proj(lon, lat):
        x, y = lonlat_to_px(lon, lat, z, scale)
        return x - px0, y - py0
    return out, proj
