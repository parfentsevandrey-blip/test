#!/usr/bin/env python3
"""Конкуренты для оценки офисов: продажа и аренда офисов/ПСН на ЦИАН вокруг объектов.

python3 commercial.py collect   — выдача по районам вокруг объектов → commercial-data/offers.json
python3 commercial.py photos    — фото отобранных конкурентов → commercial-data/photos/
"""
import json
import math
import os
import sys
import time

from cian_client import client

API = 'https://api.cian.ru/search-offers/v2/search-offers-desktop/'
OUT = 'commercial-data'
OBJECTS = {
    'yard': dict(name='БЦ «ЯРД», 1-й Магистральный туп., 11с1', lat=55.766283, lng=37.526851, group='north'),
    'kaleidoscope': dict(name='БЦ «Калейдоскоп», Хлебозаводский пр., 7с5', lat=55.66892, lng=37.63093, group='south'),
    'imagine': dict(name='БЦ Imagine Plaza, Нагатинская ул., 16', lat=55.678996, lng=37.640381, group='south'),
}
GROUPS = {'north': [38, 24, 18, 130, 34, 23],          # Хорошевский, Беговой, Пресненский, Хорошёво-Мнёвники, Савёловский, Аэропорт
          'south': [91, 93, 92, 90, 87, 88, 104]}      # Нагатино-Садовники, Нагорный, Нагатинский затон, Москворечье-Сабурово, Даниловский, Донской, Котловка


def dist_km(a_lat, a_lng, b_lat, b_lng):
    r = math.radians
    d = math.sin(r(b_lat - a_lat) / 2) ** 2 + math.cos(r(a_lat)) * math.cos(r(b_lat)) * math.sin(r(b_lng - a_lng) / 2) ** 2
    return 6371 * 2 * math.asin(math.sqrt(d))


def query(deal, districts, page, sort=None):
    q = {'_type': f'commercial{deal}', 'engine_version': {'type': 'term', 'value': 2},
         'region': {'type': 'terms', 'value': [1]},
         'geo': {'type': 'geo', 'value': [{'type': 'district', 'id': d} for d in districts]},
         'office_type': {'type': 'terms', 'value': [1, 5]},
         'total_area': {'type': 'range', 'value': {'gte': 100, 'lte': 2500}},
         'page': {'type': 'term', 'value': page}}
    if sort:
        q['sort'] = {'type': 'term', 'value': sort}
    for attempt in range(4):
        try:
            d = client().api_post(API, {'jsonQuery': q}).get('data') or {}
            return d.get('offersSerialized') or [], d.get('offerCount') or 0
        except Exception:
            time.sleep(5 * (attempt + 1))
    return [], 0


def norm(o, deal):
    """Цена: продажа — ₽ всего и ₽/м²; аренда — ₽/мес всего и ₽/м²/год."""
    b = o.get('bargainTerms') or {}
    area = float(o.get('totalArea') or 0) or None
    p = b.get('priceRur') or b.get('price')
    if not p or not area:
        return None
    if deal == 'sale':
        total = p * area if b.get('priceType') == 'squareMeter' else p
        per = total / area
    else:
        per_period = p * area if b.get('priceType') == 'squareMeter' else p
        monthly = per_period / 12 if b.get('paymentPeriod') == 'annual' else per_period
        total, per = monthly, monthly * 12 / area
    g = o.get('geo') or {}
    c = g.get('coordinates') or {}
    und = [u for u in (g.get('undergrounds') or [])]
    und.sort(key=lambda u: (u.get('transportType') != 'walk', u.get('time') or 99))
    bc = o.get('businessShoppingCenter') or {}
    bld = o.get('building') or {}
    return dict(
        id=o['cianId'], deal=deal, category=o.get('category'), area=area, floor=o.get('floorNumber'),
        floors=bld.get('floorsCount'), price=round(total), per_m2=round(per), vat=b.get('vatType'),
        tax=(b.get('tax') or {}).get('type'), lat=c.get('lat'), lng=c.get('lng'), address=g.get('userInput'),
        metro=und[0].get('name') if und else None, metro_min=und[0].get('time') if und else None,
        metro_walk=und[0].get('transportType') == 'walk' if und else None,
        bc=bc.get('name') or bc.get('fullName'), bc_class=(bc.get('buildingClassType') or bld.get('classType')),
        bc_id=bc.get('id'), bc_url=bc.get('fullUrl'), year=bld.get('buildYear'), condition=o.get('conditionType'),
        layout=o.get('layout'), entrance=o.get('inputType'), furniture=o.get('furniturePresence'),
        lease=b.get('leaseType'), sale_type=b.get('saleType'), seller=(o.get('user') or {}).get('agencyName'),
        is_by_owner=(o.get('user') or {}).get('isAgent') is False, added=o.get('added'),
        photos=[p.get('fullUrl') for p in (o.get('photos') or []) if p.get('fullUrl') and not p.get('isLayout')][:12],
        url=o.get('fullUrl') or f'https://www.cian.ru/{deal}/commercial/{o["cianId"]}/',
        title=o.get('title'), description=(o.get('description') or '')[:1500])


def cmd_collect():
    os.makedirs(OUT, exist_ok=True)
    found = {}
    for group, districts in GROUPS.items():
        for deal in ('sale', 'rent'):
            first, total = query(deal, districts, 1)
            got = {o['cianId']: o for o in first}
            # выдача отдаёт не больше ~54 страниц; несколько сортировок добирают остальное
            for sort in (None, 'price_object_order', 'price_object_order_desc', 'total_meters_order', 'creation_date_desc'):
                page = 1 if sort else 2
                while len(got) < total and page <= 60:
                    arr, _ = query(deal, districts, page, sort)
                    if not arr:
                        break
                    new = sum(o['cianId'] not in got for o in arr)
                    got.update({o['cianId']: o for o in arr})
                    page += 1
                    if sort and new == 0 and page > 3:
                        break
                if len(got) >= total:
                    break
            print(f'{group} {deal}: в выдаче {total}, собрано {len(got)}', flush=True)
            for o in got.values():
                n = norm(o, deal)
                if n:
                    n['group'] = group
                    found[(deal, n['id'])] = n
    rows = list(found.values())
    for r in rows:
        for k, ob in OBJECTS.items():
            r[f'd_{k}'] = round(dist_km(ob['lat'], ob['lng'], r['lat'], r['lng']), 2) if r['lat'] else None
    json.dump(rows, open(f'{OUT}/offers.json', 'w'), ensure_ascii=False, indent=0)
    print('всего объявлений:', len(rows), flush=True)


def cmd_photos(ids):
    import asyncio
    from scrapling.fetchers import AsyncStealthySession
    from PIL import Image
    import io
    rows = {str(r['id']): r for r in json.load(open(f'{OUT}/offers.json'))}
    os.makedirs(f'{OUT}/photos', exist_ok=True)
    todo = [(i, k, u) for i in ids for k, u in enumerate(rows[i]['photos'][:2])]

    async def run():
        async with AsyncStealthySession(headless=True, max_pages=4) as s:
            async def one(i, k, u):
                path = f'{OUT}/photos/{i}_{k}.jpg'
                if os.path.exists(path):
                    return
                for _ in range(2):
                    try:
                        r = await s.fetch(u)
                        if r.status == 200 and len(r.body) > 1000:
                            im = Image.open(io.BytesIO(r.body)).convert('RGB')
                            im.thumbnail((900, 900))
                            im.save(path, 'JPEG', quality=82)
                            return
                    except Exception:
                        await asyncio.sleep(2)
            for k in range(0, len(todo), 16):
                await asyncio.gather(*(one(*t) for t in todo[k:k + 16]))
    asyncio.run(run())
    print('фото:', len(os.listdir(f'{OUT}/photos')))


if __name__ == '__main__':
    if sys.argv[1] == 'collect':
        cmd_collect()
    elif sys.argv[1] == 'photos':
        cmd_photos(sys.argv[2].split(','))
