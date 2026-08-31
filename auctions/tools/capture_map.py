#!/usr/bin/env python3
"""Capture the Yandex Maps view for one object, framed to include its metro.

The zoom is not fixed: the nearest metro station is geocoded (OpenStreetMap
Nominatim) and the zoom is chosen as the closest one that still leaves the
station inside the cropped frame, so every object's map shows both the pin and
the station it is sold on.

    python3 tools/capture_map.py 55.768071 37.628451 "Трубная" assets/obj1/map.png
"""
import json
import math
import os
import subprocess
import sys
import time
import urllib.parse

from PIL import Image

# the reader renders the widget at 1280x1280; this is the window we keep.
# The cookie notice sits below y=1080, so the crop never reaches past it.
CROP_W, CROP_H = 1160, 950
BANNER_TOP = 1080
SAFE = 0.85                     # keep the station within 85% of the half-extents
UA = 'weekly-auction-doc/1.0'


def sh(cmd, t=260):
    try:
        return subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=t).stdout.strip()
    except Exception as exc:                     # noqa: BLE001
        return 'EXC:' + str(exc)[:90]


def geocode_metro(name):
    """Return (lat, lon) of the metro station, or None."""
    query = urllib.parse.quote(name)
    raw = sh('curl -sS -m 40 -A "%s" '
             '"https://nominatim.openstreetmap.org/search?q=%s&format=json&countrycodes=ru&limit=12"'
             % (UA, query))
    try:
        hits = json.loads(raw)
    except Exception:
        return None
    for hit in hits:
        if hit.get('class') == 'railway' and hit.get('type') in ('station', 'subway_entrance', 'halt'):
            return float(hit['lat']), float(hit['lon'])
    for hit in hits:
        if 'метро' in hit.get('display_name', '').lower() or 'метро' in hit.get('name', '').lower():
            return float(hit['lat']), float(hit['lon'])
    return None


def pick_zoom(lat, lon, target, zmin=13, zmax=17):
    """Largest zoom that still keeps `target` inside the cropped frame."""
    if not target:
        return 16
    dx = abs(lon - target[1]) * 111320 * math.cos(math.radians(lat))
    dy = abs(lat - target[0]) * 110574
    need = max(dx / (CROP_W / 2 * SAFE), dy / (CROP_H / 2 * SAFE))   # metres per pixel
    for z in range(zmax, zmin - 1, -1):
        res = 156543.034 * math.cos(math.radians(lat)) / (2 ** z)
        if res >= need:
            return z
    return zmin


def capture(url, dst, tries=6):
    for attempt in range(tries):
        shot = sh('curl -sS -m 240 -H "x-timeout: 90" -H "x-respond-with: pageshot" "https://r.jina.ai/%s"' % url)
        if shot.startswith('https'):
            sh('curl -sSL -o "%s" -m 250 -w "%%{http_code}" "%s"' % (dst, shot))
            try:
                if Image.open(dst).size[1] >= 1000:
                    return True
            except Exception:
                pass
            print('  bad capture')
        else:
            print('  api-fail', shot[:80].replace('\n', ' '))
        time.sleep(60 + 45 * attempt)
    return False


def main(lat, lon, metro, out_path):
    lat, lon = float(lat), float(lon)
    target = geocode_metro(metro) if metro else None
    zoom = pick_zoom(lat, lon, target)
    print('metro', metro, target, '-> zoom', zoom)

    widget = ('https://yandex.ru/map-widget/v1/?ll=%s%%2C%s&z=%d&l=map&pt=%s,%s,pm2rdm&lang=ru_RU'
              % (lon, lat, zoom, lon, lat))
    raw = out_path.replace('.png', '_raw.png')
    if not (os.path.exists(raw) and Image.open(raw).size[1] >= 1000):
        if not capture(widget, raw):
            raise SystemExit('map capture failed')

    im = Image.open(raw).convert('RGB')
    h = im.size[1]
    cx, cy = 1280 // 2, h // 2          # widget is centred on `ll`; ignore the scrollbar column
    left = max(0, cx - CROP_W // 2)
    top = min(max(0, cy - CROP_H // 2), BANNER_TOP - CROP_H)
    im.crop((left, top, left + CROP_W, top + CROP_H)).save(out_path)
    print('written', out_path, Image.open(out_path).size)


if __name__ == '__main__':
    if len(sys.argv) < 5:
        raise SystemExit(__doc__)
    main(*sys.argv[1:5])
