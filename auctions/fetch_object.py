#!/usr/bin/env python3
"""Read one investmoscow.ru lot and write its normalised record + photo list.

investmoscow.ru refuses connections from this network, so the page is read
through the Jina reader. The page ships its whole data model as a devalue-
encoded array in <script id="__NUXT_DATA__">, which is parsed here rather than
scraped: prices, dates, deposit, coordinates, metro and the gallery all come out
of it as they were served.

    python3 fetch_object.py https://investmoscow.ru/tenders/tender/20188709 obj1

Photos and the map are captured separately (tools/make_grid.py +
tools/capture_grids.py, tools/capture_map.py).
"""
import json
import os
import re
import subprocess
import sys
import time

ROOT = os.path.dirname(os.path.abspath(__file__))
JINA = 'https://r.jina.ai/'


def sh(cmd, timeout=260):
    try:
        return subprocess.run(cmd, shell=True, capture_output=True, text=True,
                              timeout=timeout).stdout.strip()
    except Exception as exc:                     # noqa: BLE001 - report and retry
        return 'EXC:' + str(exc)[:100]


def read_page(url, tries=5):
    for attempt in range(tries):
        html = sh('curl -sS -m 240 -H "x-timeout: 90" -H "x-return-format: html" "%s%s"'
                  % (JINA, url))
        if '__NUXT_DATA__' in html:
            return html
        print('  page retry %d: %s' % (attempt + 1, html[:110].replace('\n', ' ')))
        time.sleep(45 + 30 * attempt)
    raise SystemExit('could not read %s' % url)


def parse_payload(html):
    """Resolve the devalue array the page ships its data model in."""
    blob = re.search(r'id="__NUXT_DATA__"[^>]*>(.*?)</script>', html, re.S)
    arr = json.loads(blob.group(1))

    def deref(i, depth=0):
        if depth > 16 or not isinstance(i, int) or i < 0 or i >= len(arr):
            return None
        node = arr[i]
        if isinstance(node, dict):
            return {k: deref(v, depth + 1) for k, v in node.items()}
        if isinstance(node, list):
            if len(node) == 2 and node[0] in ('ShallowReactive', 'Reactive', 'Ref', 'EmptyRef'):
                return deref(node[1], depth + 1)
            return [deref(v, depth + 1) for v in node]
        return node

    data = deref(1)['data']
    for value in data.values():
        if isinstance(value, dict) and 'headerInfo' in value:
            return value
    raise SystemExit('tender block not found in payload')


def pairs(items):
    return [[i['label'], i['value']] for i in (items or []) if i.get('label')]


def normalise(tender, url):
    header = tender['headerInfo']
    sidebar = tender['sidebar']
    coords = (tender.get('mapInfo') or {}).get('coords') or {}
    subway = (header.get('subway') or [{}])[0]
    procedure = {label: value for label, value in pairs(tender.get('procedureInfo'))}

    return {
        'sourceUrl': url,
        'title': header.get('title'),
        'address': header.get('displayAddress') or header.get('address'),
        'lot': header.get('investObjectId'),
        'objectType': header.get('tenderObjectTypeName'),
        'tenderType': header.get('tenderTypeName'),
        'area': header.get('objectAreaInMeters'),
        'startPrice': sidebar.get('startPrice'),
        'pricePerSqm': sidebar.get('perPrice'),
        'deposit': procedure.get('Размер задатка'),
        'step': procedure.get('Шаг аукциона'),
        'form': procedure.get('Форма проведения'),
        'applicationStart': procedure.get('Дата начала приёма заявок'),
        'applicationDeadline': procedure.get('Дата окончания приёма заявок'),
        'participantSelection': procedure.get('Отбор участников'),
        'auctionDate': procedure.get('Проведение торгов'),
        'results': procedure.get('Подведение итогов'),
        'metro': subway.get('subwayStationName'),
        'metroWalk': subway.get('walkingTime'),
        'metroDistanceKm': subway.get('distanceToObject'),
        'lat': coords.get('lat'),
        'lon': coords.get('long'),
        'objectInfo': pairs(tender.get('objectInfo')),
        'visual': pairs(tender.get('visualBlockInfo')),
        'images': [i['url'] for i in (tender.get('imageInfo') or {}).get('attachedImages', [])],
    }


def main(url, obj_id):
    out_dir = os.path.join(ROOT, 'assets', obj_id)
    os.makedirs(out_dir, exist_ok=True)
    print('reading', url)
    record = normalise(parse_payload(read_page(url)), url)

    with open(os.path.join(out_dir, 'lot.json'), 'w', encoding='utf-8') as fh:
        json.dump(record, fh, ensure_ascii=False, indent=1)
    with open(os.path.join(out_dir, 'images.txt'), 'w') as fh:
        fh.write('\n'.join(record['images']))

    print('  %s | %s' % (record['title'], record['address']))
    print('  %s / %s | задаток %s | метро %s (%s мин, %s км) | %s фото'
          % (record['startPrice'], record['pricePerSqm'], record['deposit'],
             record['metro'], record['metroWalk'], record['metroDistanceKm'],
             len(record['images'])))


if __name__ == '__main__':
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    main(sys.argv[1], sys.argv[2])
