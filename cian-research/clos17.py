#!/usr/bin/env python3
"""Квартира в клубном доме «Кло 17» (Староваганьковский пер., 17): предложения в ЖК, первичка и вторичка рядом.

python3 clos17.py jk      → clos17-data/jk.json        (все лоты «Кло 17» и соседнего «Люче»: застройщик и переуступки)
python3 clos17.py new     → clos17-data/new.json       (новостройки ЦАО 60–200 м²)
python3 clos17.py resale  → clos17-data/resale.json    (вторичка 70–160 м² в районах вокруг)
python3 clos17.py cards --ids …  → clos17-data/cards.json (история цены, отделка, описание)
"""
import argparse
import json
import math
import os
import sys

import flat_comps as fc
from cian_client import client

OUT = 'clos17-data'
OBJ = dict(lat=55.7510938, lng=37.6058712)        # «Кло 17» по координатам ЦИАН
JK_NAMES = ('Кло 17', 'Фамильный дом Люче')
CAO = 4                                           # ЦАО в справочнике районов ЦИАН


def km(c):
    return fc.km(c, OBJ) if c and c.get('lat') is not None else None


def slim(o):
    s = fc.slim(o, None)
    nb = o.get('newbuilding') or {}
    g = o.get('geo') or {}
    s.update(km_obj=km(g.get('coordinates')), jk=nb.get('name') or s['jk'], jk_id=nb.get('id') or s['jk_id'],
             house=(nb.get('house') or {}).get('name'), finish=(nb.get('house') or {}).get('finishDate'),
             developer=bool(nb.get('isFromDeveloper')), from_seller=bool(nb.get('isFromSeller')),
             sale_type=(o.get('bargainTerms') or {}).get('saleType'), category=o.get('category'),
             raion_id=next((a['id'] for a in g.get('address') or [] if a.get('type') == 'raion'), None))
    s.pop('photos', None)
    s['photos'] = [p.get('fullUrl') for p in (o.get('photos') or []) if not p.get('isLayout') and p.get('fullUrl')][:8]
    return s


def crawl(extra, geo):
    found, total = fc.crawl(extra, geo=geo)
    return {k: slim(v) for k, v in found.items()}, total


def save(name, data):
    json.dump(data, open(f'{OUT}/{name}.json', 'w'), ensure_ascii=False, indent=1)
    print(name, len(data))


def cmd_new(a):
    """Новостройки ЦАО (первичка и переуступки) 60–200 м²."""
    res, total = crawl({'building_status': {'type': 'term', 'value': 2},
                        'total_area': {'type': 'range', 'value': {'gte': 60, 'lte': 200}}}, [CAO])
    save('new', res)


def cmd_jk(a):
    new = json.load(open(f'{OUT}/new.json'))
    ids = sorted({x['jk_id'] for x in new.values() if x['jk'] in JK_NAMES and x['jk_id']})
    print('ЖК', ids)
    res = {}
    for i in ids:                                  # ЖК в ЦИАН — элемент geo с типом newobject
        page, total = 1, None
        while total is None or (page - 1) * fc.PAGE < min(total, 600):
            q = {'_type': 'flatsale', 'engine_version': {'type': 'term', 'value': 2}, 'region': {'type': 'terms', 'value': [1]},
                 'geo': {'type': 'geo', 'value': [{'type': 'newobject', 'id': i}]}, 'page': {'type': 'term', 'value': page}}
            d = client().api_post(fc.API, {'jsonQuery': q}).get('data') or {}
            total = d.get('offerCount') or 0
            res.update({o['cianId']: slim(o) for o in d.get('offersSerialized') or []})
            page += 1
        print(' ', i, total)
    save('jk', res)


def cmd_resale(a):
    """Вторичка в районах, где лежат новостройки ЦАО рядом с объектом (до 2,5 км)."""
    new = json.load(open(f'{OUT}/new.json'))
    raions = sorted({x['raion_id'] for x in new.values() if x['raion_id'] and x['km_obj'] is not None and x['km_obj'] <= 2.5})
    print('районы', raions)
    res, total = crawl({'building_status': {'type': 'term', 'value': 1},
                        'total_area': {'type': 'range', 'value': {'gte': 70, 'lte': 160}}}, raions)
    save('resale', res)


def cmd_cards(a):
    path = f'{OUT}/cards.json'
    cards = json.load(open(path)) if os.path.exists(path) else {}
    for n, cid in enumerate([int(x) for x in a.ids.split(',') if x and x not in cards], 1):
        h = client().page(f'https://www.cian.ru/sale/flat/{cid}/')
        j = h.find('"offerData":')
        if j < 0:
            cards[str(cid)] = {'status': 'нет данных'}
            continue
        od = json.JSONDecoder().raw_decode(h[j + len('"offerData":'):])[0]
        off = od.get('offer') or {}
        cards[str(cid)] = dict(
            status=off.get('status'), created=off.get('creationDate'), description=off.get('description'),
            decoration=off.get('decoration'), seller=(od.get('agent') or {}).get('companyName') or (od.get('user') or {}).get('companyName'),
            is_developer=bool((od.get('newObject') or {}).get('isFromDeveloper') or (od.get('company') or {}).get('categoryId') == 2002),
            stats=od.get('stats'), floor=off.get('floorNumber'), area=off.get('totalArea'),
            price_changes=[(c.get('changeTime', '')[:10], (c.get('priceData') or {}).get('price'))
                           for c in sorted(od.get('priceChanges') or [], key=lambda c: c.get('changeTime') or '')])
        if n % 10 == 0:
            json.dump(cards, open(path, 'w'), ensure_ascii=False, indent=1)
            print(' ', n, flush=True)
    json.dump(cards, open(path, 'w'), ensure_ascii=False, indent=1)
    print('карточек', len(cards))


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest='cmd', required=True)
    for c in ('new', 'jk', 'resale'):
        sub.add_parser(c)
    c = sub.add_parser('cards')
    c.add_argument('--ids', required=True)
    a = p.parse_args()
    os.makedirs(OUT, exist_ok=True)
    {'new': cmd_new, 'jk': cmd_jk, 'resale': cmd_resale, 'cards': cmd_cards}[a.cmd](a)


if __name__ == '__main__':
    sys.exit(main())
