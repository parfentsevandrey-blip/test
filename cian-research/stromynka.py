#!/usr/bin/env python3
"""Стромынка, 2 (здание МФЦ на арендованной земле): рынок ЦИАН — земля, здания, коммерция рядом, ставки аренды.

python3 stromynka.py search   → stromynka-data/{land,buildings,near_sale,near_rent}.json
python3 stromynka.py cards    → stromynka-data/cards.json (карточки шорт-листа: земля, арендаторы, доходность)
"""
import argparse
import json
import math
import os
import re
import sys

from cian_client import client

OUT = 'stromynka-data'
API = 'https://api.cian.ru/search-offers/v2/search-offers-desktop/'
OBJ = dict(lat=55.7895825, lng=37.6831089)          # МФЦ района Сокольники, ул. Стромынка, 2
KREMLIN = dict(lat=55.752023, lng=37.617499)
PAGE = 28
SORTS = [None, 'price_object_order', 'creation_date_desc', 'total_meters_order', 'creation_date_asc']


def km(a, b):
    if not a or a.get('lat') is None:
        return None
    dy = (a['lat'] - b['lat']) * 111.2
    dx = (a['lng'] - b['lng']) * 111.2 * math.cos(math.radians(55.75))
    return round(math.hypot(dx, dy), 2)


def query(page, extra, sort=None, typ='commercialsale'):
    q = {'_type': typ, 'engine_version': {'type': 'term', 'value': 2}, 'region': {'type': 'terms', 'value': [1]},
         'page': {'type': 'term', 'value': page}}
    if sort:
        q['sort'] = {'type': 'term', 'value': sort}
    q.update(extra)
    d = client().api_post(API, {'jsonQuery': q}).get('data') or {}
    return d.get('offersSerialized') or [], d.get('offerCount') or 0


def crawl(extra, typ='commercialsale', max_pages=60):
    found, total = {}, None
    for sort in SORTS:
        arr, n = query(1, extra, sort, typ)
        total = total or n
        for o in arr:
            found.setdefault(o['cianId'], o)
        for p in range(2, min(max_pages, math.ceil(n / PAGE)) + 1):
            arr, _ = query(p, extra, sort, typ)
            for o in arr:
                found.setdefault(o['cianId'], o)
        print(f'  {sort}: {len(found)} из {total}', flush=True)
        if len(found) >= total:
            break
    return found, total


def land_m2(land):
    if not land or not land.get('area'):
        return None
    a = float(land['area'])
    unit = land.get('areaUnitType')
    return a * 10000 if unit == 'hectare' else a * 100 if unit == 'sotka' else a


def slim(o):
    g = o.get('geo') or {}
    addr = g.get('address') or []
    b = o.get('building') or {}
    bt = o.get('bargainTerms') or {}
    tax = bt.get('tax') or {}
    coord = g.get('coordinates') or {}
    und = sorted(g.get('undergrounds') or [], key=lambda u: (u.get('transportType') != 'walk', u.get('time') or 99))
    u = o.get('user') or {}
    area = float(o['totalArea']) if o.get('totalArea') else None
    price = bt.get('priceRur') or bt.get('price')
    return dict(
        id=o['cianId'], url=o.get('fullUrl') or f"https://www.cian.ru/sale/commercial/{o['cianId']}/",
        category=o.get('category'), title=o.get('title'), price=price, price_type=bt.get('priceType'),
        vat=bt.get('vatType'), vat_rate=tax.get('rate'), tax_type=tax.get('type'), lease_type=bt.get('leaseType'),
        area=area, land_m2=land_m2(o.get('land')), land=o.get('land'), permitted=o.get('permittedUseType'),
        okrug=next((a['name'] for a in addr if a.get('type') == 'okrug'), None),
        raion=next((a['name'] for a in addr if a.get('type') == 'raion'), None),
        raion_id=next((a['id'] for a in addr if a.get('type') == 'raion'), None),
        okrug_id=next((a['id'] for a in addr if a.get('type') == 'okrug'), None),
        address=', '.join(a['name'] for a in addr if a.get('type') in ('location', 'street', 'house') and a['name'] != 'Москва'),
        lat=coord.get('lat'), lng=coord.get('lng'), km_obj=km(coord, OBJ), km_kremlin=km(coord, KREMLIN),
        metro=[(x.get('name'), x.get('time'), x.get('transportType')) for x in und[:3]],
        floors=b.get('floorsCount'), year=b.get('buildYear'), cls=b.get('classType'), btype=b.get('type'),
        monthly_income=o.get('monthlyIncome'), seller=u.get('agencyName'), owner=bool((o.get('commercialOwnership') or {}).get('isFromOwner')),
        created=o.get('creationDate') or o.get('added'), description=o.get('description') or '',
        photos=[p.get('fullUrl') for p in (o.get('photos') or []) if p.get('fullUrl')][:12])


def save(name, data):
    json.dump(data, open(f'{OUT}/{name}.json', 'w'), ensure_ascii=False, indent=1)
    print(name, len(data))


def cmd_search(a):
    print('коммерческая земля')
    found, _ = crawl({'category': {'type': 'terms', 'value': ['commercialLandSale']}})
    save('land', {k: slim(v) for k, v in found.items()})
    print('отдельно стоящие здания')
    found, _ = crawl({'office_type': {'type': 'terms', 'value': [11]}})
    save('buildings', {k: slim(v) for k, v in found.items()})


def cmd_near(a):
    """Продажа и аренда коммерции в районах вокруг объекта (ID районов — из выдачи зданий)."""
    ids = [int(x) for x in a.raions.split(',')]
    geo = {'geo': {'type': 'geo', 'value': [{'type': 'district', 'id': i} for i in ids]}}
    print('продажа рядом')
    found, _ = crawl(dict(geo))
    save('near_sale', {k: slim(v) for k, v in found.items()})
    print('аренда рядом')
    found, _ = crawl(dict(geo), typ='commercialrent')
    save('near_rent', {k: slim(v) for k, v in found.items()})


def card(cid, kind='sale'):
    h = client().page(f'https://www.cian.ru/{kind}/commercial/{cid}/')
    j = h.find('"offerData":')
    if j < 0:
        return {'status': 'нет данных'}
    od = json.JSONDecoder().raw_decode(h[j + len('"offerData":'):])[0]
    off = od.get('offer') or {}
    feats = {f.get('label'): f.get('value') for g in (od.get('features') or []) for f in (g.get('features') or [])}
    return dict(status=off.get('status'), land=off.get('land'), building=off.get('building'), features=feats,
                description=off.get('description'), created=off.get('creationDate'),
                price_changes=[(c.get('changeTime', '')[:10], (c.get('priceData') or {}).get('price'))
                               for c in sorted(od.get('priceChanges') or [], key=lambda c: c.get('changeTime') or '')],
                photos=[p.get('fullUrl') for p in (off.get('photos') or []) if p.get('fullUrl')][:16])


def cmd_cards(a):
    ids = [int(x) for x in a.ids.split(',')]
    path = f'{OUT}/cards.json'
    cards = json.load(open(path)) if os.path.exists(path) else {}
    for n, cid in enumerate([i for i in ids if str(i) not in cards], 1):
        cards[str(cid)] = card(cid)
        if n % 10 == 0:
            json.dump(cards, open(path, 'w'), ensure_ascii=False, indent=1)
            print(f'  {n}', flush=True)
    json.dump(cards, open(path, 'w'), ensure_ascii=False, indent=1)
    print('карточек:', len(cards))


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest='cmd', required=True)
    sub.add_parser('search')
    c = sub.add_parser('near')
    c.add_argument('--raions', required=True)
    c = sub.add_parser('cards')
    c.add_argument('--ids', required=True)
    a = p.parse_args()
    os.makedirs(OUT, exist_ok=True)
    {'search': cmd_search, 'near': cmd_near, 'cards': cmd_cards}[a.cmd](a)


if __name__ == '__main__':
    sys.exit(main())
