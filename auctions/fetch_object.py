#!/usr/bin/env python3
"""Collect one investmoscow.ru lot into assets/<id>/ + a data record.

investmoscow.ru and torgi.mos.ru refuse connections from this network, so
everything goes through the Jina reader (r.jina.ai), which does reach them:

  * the lot page is read as HTML and the Nuxt payload is mined for the fields,
    the gallery image URLs and the coordinates;
  * every gallery image is captured as a full-viewport screenshot of the image
    URL (the reader renders it in a browser, we get the picture back as PNG);
  * the Yandex Maps widget is captured the same way and the marker is ringed.

Usage:
    python3 fetch_object.py https://investmoscow.ru/tenders/tender/20188709 obj1
"""
import json
import os
import re
import subprocess
import sys
import time

ROOT = os.path.dirname(os.path.abspath(__file__))
JINA = 'https://r.jina.ai/'


def sh(cmd, timeout=200):
    try:
        return subprocess.run(cmd, shell=True, capture_output=True, text=True,
                              timeout=timeout).stdout.strip()
    except Exception as exc:                     # noqa: BLE001 - report and retry
        return 'EXC:' + str(exc)[:100]


def read_page(url):
    """Return the lot page HTML (including the Nuxt payload)."""
    for attempt in range(4):
        html = sh('curl -sS -m 200 -H "x-timeout: 60" -H "x-return-format: html" "%s%s"'
                  % (JINA, url), timeout=240)
        if len(html) > 50000:
            return html
        print('  page retry', attempt + 1, html[:120])
        time.sleep(20 * (attempt + 1))
    raise SystemExit('could not read %s' % url)


def pageshot(url, dst, tries=6):
    """Screenshot `url` through the reader and save the PNG to `dst`."""
    if os.path.exists(dst) and os.path.getsize(dst) > 20000:
        return True
    for attempt in range(tries):
        shot = sh('curl -sS -m 150 -H "x-timeout: 60" -H "x-respond-with: pageshot" "%s%s"'
                  % (JINA, url))
        if shot.startswith('https'):
            code = sh('curl -sSL -o "%s" -m 150 -w "%%{http_code}" "%s"' % (dst, shot))
            if code == '200' and os.path.exists(dst) and os.path.getsize(dst) > 20000:
                return True
        print('  shot retry', attempt + 1, shot[:90].replace('\n', ' '))
        time.sleep(45 + 30 * attempt)
    return False


def label_values(html):
    """Pull the {"label":..,"value":..} pairs out of the flat Nuxt payload."""
    strings = re.findall(r'"((?:[^"\\]|\\.){0,400})"', html)
    pairs = {}
    for label, value in re.findall(r'"label":"([^"]{2,80})","value":"([^"]{1,300})"', html):
        pairs[label] = value
    # the payload de-duplicates strings into an index table; resolve those too
    table = strings
    for label_i, value_i in re.findall(r'"label":(\d{1,5}),"value":(\d{1,5})', html):
        li, vi = int(label_i), int(value_i)
        if li < len(table) and vi < len(table):
            pairs.setdefault(table[li], table[vi])
    return pairs


def main(url, obj_id):
    out_dir = os.path.join(ROOT, 'assets', obj_id)
    photos_dir = os.path.join(out_dir, 'photos_raw')
    os.makedirs(photos_dir, exist_ok=True)

    print('reading lot page ...')
    html = read_page(url)
    with open(os.path.join(out_dir, 'page.html'), 'w', encoding='utf-8') as fh:
        fh.write(html)

    tender_id = url.rstrip('/').split('/')[-1]
    images, seen = [], set()
    for img in re.findall(r'https://torgi\.mos\.ru/objectimages/Tenders/%s/[0-9a-f]+\.jpg' % tender_id, html):
        if img not in seen:
            seen.add(img)
            images.append(img)

    coords = re.search(r'"lat":(\d{2}\.\d+),?"?long"?:?(\d{2}\.\d+)', html)
    if not coords:
        nums = re.search(r'"coords":\d+.*?(\d{2}\.\d{4,}),(\d{2}\.\d{4,})', html, re.S)
        coords = nums
    lat, lon = (coords.group(1), coords.group(2)) if coords else (None, None)

    fields = label_values(html)
    meta = {'sourceUrl': url, 'images': len(images), 'lat': lat, 'lon': lon, 'fields': fields}
    with open(os.path.join(out_dir, 'raw.json'), 'w', encoding='utf-8') as fh:
        json.dump(meta, fh, ensure_ascii=False, indent=1)
    print('fields:', len(fields), '| images:', len(images), '| coords:', lat, lon)

    if lat and lon:
        widget = ('https://yandex.ru/map-widget/v1/?ll=%s%%2C%s&z=17&l=map'
                  '&pt=%s,%s,pm2rdm&lang=ru_RU' % (lon, lat, lon, lat))
        print('capturing map ...')
        pageshot(widget, os.path.join(out_dir, 'map_raw.png'))

    with open(os.path.join(out_dir, 'images.txt'), 'w') as fh:
        fh.write('\n'.join(images))
    for i, img in enumerate(images, 1):
        dst = os.path.join(photos_dir, '%02d.png' % i)
        print('photo %d/%d' % (i, len(images)))
        pageshot(img, dst)
        time.sleep(14)
    print('done ->', out_dir)


if __name__ == '__main__':
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    main(sys.argv[1], sys.argv[2])
