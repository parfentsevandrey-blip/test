#!/usr/bin/env python3
"""Карты зон с маркерами ЖК по статусу — для печати на A3.

  python3 tools/cian/render-maps.py

Вход:  docs/premium-cao/points.json (пишет build-premium-xlsx.py)
Выход: docs/premium-cao/maps/<slug>.jpg + docs/premium-cao/maps/index.json (нумерация, легенда)
Тайлы Яндекс Карт (EPSG:3395), кэш в scratch; адреса без координат — геокодер Nominatim (кэш geocode.json).
"""
import json, math, io, os, re, time, urllib.request, urllib.parse
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / 'docs' / 'premium-cao'
MAPS = DOCS / 'maps'; MAPS.mkdir(exist_ok=True)
CACHE = Path(os.environ.get('TILE_CACHE', '/tmp/claude-0/-home-user-test/5ef931c1-a5eb-584d-bea4-9b195abd4240/scratchpad/tiles')); CACHE.mkdir(parents=True, exist_ok=True)
GEO = DOCS / 'geocode.json'
geocache = json.load(open(GEO)) if GEO.exists() else {}

E = 0.0818191908426
def tile_xy(lat, lon, z):
    n = 2 ** z
    x = (lon + 180) / 360 * n
    phi = math.radians(lat); es = E * math.sin(phi)
    y = (1 - math.log(math.tan(math.pi / 4 + phi / 2) * ((1 - es) / (1 + es)) ** (E / 2)) / math.pi) / 2 * n
    return x, y

def fetch_tile(x, y, z):
    f = CACHE / f'{z}_{x}_{y}.png'
    if f.exists(): return Image.open(f).convert('RGB')
    u = f'https://core-renderer-tiles.maps.yandex.net/tiles?l=map&x={x}&y={y}&z={z}&scale=1&lang=ru_RU'
    for attempt in range(3):
        try:
            req = urllib.request.Request(u, headers={'User-Agent': 'Mozilla/5.0'})
            data = urllib.request.urlopen(req, timeout=30).read()
            f.write_bytes(data); time.sleep(0.05)
            return Image.open(io.BytesIO(data)).convert('RGB')
        except Exception as e:
            time.sleep(2 * (attempt + 1)); err = e
    raise err

def geocode(addr):
    key = re.sub(r'\s+', ' ', addr).strip()
    if key in geocache: return geocache[key]
    q = re.sub(r'\s*\([^)]*\)', '', key).split(';')[0]
    q = re.sub(r'\b(вл\.?|владение)\s*', '', q)
    q = re.sub(r',?\s*(стр\.|строение|к\.|корп\.)\s*[\w/]+(\s*,\s*\d+)*', '', q)
    q = re.sub(r'\bпер\.', 'переулок', q); q = re.sub(r'\bул\.', 'улица', q); q = re.sub(r'\bнаб\.', 'набережная', q); q = re.sub(r'\bпл\.', 'площадь', q); q = re.sub(r'\bбул\.', 'бульвар', q)
    q = re.sub(r'\s+и\s+\d+\b', '', q).strip(' ,')
    res = None
    for query in (f'Москва, {q}', f'Москва, {q.split(",")[0]}'):
        params = urllib.parse.urlencode({'q': query, 'format': 'json', 'limit': 1, 'countrycodes': 'ru'})
        req = urllib.request.Request('https://nominatim.openstreetmap.org/search?' + params, headers={'User-Agent': 'premium-cao-research/1.0'})
        try:
            j = json.loads(urllib.request.urlopen(req, timeout=30).read())
        except Exception:
            j = []
        time.sleep(1.1)
        if j and 55.6 < float(j[0]['lat']) < 55.9:
            res = {'lat': float(j[0]['lat']), 'lng': float(j[0]['lon']), 'src': 'nominatim', 'match': j[0].get('display_name', '')[:80]}
            break
    geocache[key] = res
    json.dump(geocache, open(GEO, 'w'), ensure_ascii=False, indent=1)
    return res

# ---------- карты ----------
MAPS_DEF = [
    {'slug': 'sadovoe', 'title': 'Садовое кольцо', 'zones': ['Садовое кольцо'], 'bbox': (55.727, 37.572, 55.781, 37.668), 'z': 16},
    {'slug': 'khamovniki', 'title': 'Хамовники', 'zones': ['Хамовники'], 'bbox': (55.708, 37.550, 55.752, 37.615), 'z': 16},
    {'slug': 'presnya', 'title': 'Пресня, Сити, Белорусская', 'zones': ['Пресня', 'Сити', 'Белорусская'], 'bbox': (55.738, 37.515, 55.786, 37.605), 'z': 16},
]
COLORS = {'построено': (46, 158, 79), 'строится': (242, 140, 40), 'проектирование': (123, 79, 191)}
ORDER = {'построено': 0, 'строится': 1, 'проектирование': 2}

points = json.load(open(DOCS / 'points.json'))
MANUAL = json.load(open(DOCS / 'geocode-manual.json')) if (DOCS / 'geocode-manual.json').exists() else {}
missing = []
for p in points:
    if p['name'] in MANUAL: p['lat'], p['lng'], p['geo'] = MANUAL[p['name']][0], MANUAL[p['name']][1], 'вручную'
    if p.get('lat') and p.get('lng'): continue
    g = geocode(p['address'] or p['name'])
    if g: p['lat'], p['lng'], p['geo'] = g['lat'], g['lng'], 'по адресу (OSM)'
    else: missing.append(p['name'])

font_b = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 34)
font_t = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 60)
font_l = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 40)

index = {'maps': [], 'missing': missing}
for md in MAPS_DEF:
    lat1, lng1, lat2, lng2 = md['bbox']; z = md['z']
    x1, y1 = tile_xy(lat2, lng1, z); x2, y2 = tile_xy(lat1, lng2, z)   # верх-лево, низ-право
    # кадр под пропорции листа A3 (альбомная), чтобы карта занимала всю страницу
    A3 = 420 / 297
    w, h = x2 - x1, y2 - y1
    if w / h < A3:
        need = h * A3; xc = (x1 + x2) / 2; x1, x2 = xc - need / 2, xc + need / 2
    else:
        need = w / A3; yc = (y1 + y2) / 2; y1, y2 = yc - need / 2, yc + need / 2
    tx1, ty1, tx2, ty2 = int(x1), int(y1), int(x2), int(y2)
    W, H = (tx2 - tx1 + 1) * 256, (ty2 - ty1 + 1) * 256
    img = Image.new('RGB', (W, H), (240, 240, 240))
    for tx in range(tx1, tx2 + 1):
        for ty in range(ty1, ty2 + 1):
            img.paste(fetch_tile(tx, ty, z), ((tx - tx1) * 256, (ty - ty1) * 256))
    # обрезаем по bbox
    px1, py1 = (x1 - tx1) * 256, (y1 - ty1) * 256
    px2, py2 = (x2 - tx1) * 256, (y2 - ty1) * 256
    img = img.crop((int(px1), int(py1), int(px2), int(py2)))
    d = ImageDraw.Draw(img)
    def to_px(lat, lng):
        x, y = tile_xy(lat, lng, z)
        return (x - tx1) * 256 - px1, (y - ty1) * 256 - py1
    pts = [p for p in points if p.get('zone') in md['zones'] and p.get('lat')]
    pts.sort(key=lambda p: (ORDER[p['status']], p['name']))
    legend = []
    for i, p in enumerate(pts, 1):
        x, y = to_px(p['lat'], p['lng'])
        if not (0 <= x < img.width and 0 <= y < img.height): p['off'] = True; continue
        r = 36; c = COLORS[p['status']]
        d.ellipse((x - r + 4, y - r + 6, x + r + 4, y + r + 6), fill=(70, 70, 70))
        d.ellipse((x - r, y - r, x + r, y + r), fill=c, outline='white', width=5)
        t = str(i); tw = d.textlength(t, font=font_b)
        d.text((x - tw / 2, y - 21), t, fill='white', font=font_b)
        legend.append({'n': i, 'name': p['name'], 'status': p['status'], 'address': p.get('address'), 'geo': p.get('geo')})
    # заголовок и легенда статусов на самой карте
    pad = 24
    head = f'Премиум-ЖК: {md["title"]}'
    # ширина плашки — по самой длинной надписи, иначе заголовок вылезает за рамку
    bw = int(max(d.textlength(head, font=font_t) + 48,
                 max(d.textlength(st, font=font_l) for st in COLORS) + 140)) + 24
    bh = 110 + 52 * len(COLORS) + 8
    d.rectangle((pad, pad, pad + bw, pad + bh), fill=(255, 255, 255), outline=(60, 60, 60), width=2)
    d.text((pad + 24, pad + 18), head, fill=(31, 56, 100), font=font_t)
    yy = pad + 110
    for st, c in COLORS.items():
        d.ellipse((pad + 30, yy, pad + 74, yy + 44), fill=c, outline='white', width=3)
        d.text((pad + 92, yy - 2), st, fill=(30, 30, 30), font=font_l); yy += 52
    d.text((img.width - 900, img.height - 50), '© Яндекс Карты · номера — см. список на листе', fill=(60, 60, 60), font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 28))
    out = MAPS / f'{md["slug"]}.jpg'
    img.save(out, 'JPEG', quality=86, optimize=True)
    index['maps'].append({'slug': md['slug'], 'title': md['title'], 'file': str(out.relative_to(DOCS)), 'width': img.width, 'height': img.height, 'legend': legend,
                          'off': [p['name'] for p in pts if p.get('off')]})
    print(f'{md["slug"]}: {img.width}x{img.height}, маркеров {len(legend)}, вне кадра {len([p for p in pts if p.get("off")])}')
json.dump(index, open(MAPS / 'index.json', 'w'), ensure_ascii=False, indent=1)
print('без координат:', missing)
