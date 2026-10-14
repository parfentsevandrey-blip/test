#!/usr/bin/env python3
"""Аналоги квартиры по Москве: выдача ЦИАН через API (методика ANTIBOT.md), карточки, фото, машиноместа.

python3 flat_comps.py search   → flat-data/offers.json   (все предложения под фильтры аналогов)
python3 flat_comps.py cards    → flat-data/cards.json    (класс ЖК, ремонт, год, парковка — из карточек шорт-листа)
python3 flat_comps.py photos   → flat-data/photos/       (фото шорт-листа, браузером)
python3 flat_comps.py parking  → flat-data/parking.json  (машиноместа на продажу в доме и в ЖК рядом)
"""
import argparse
import asyncio
import io
import json
import math
import os
import re
import sys

from cian_client import client

OUT = 'flat-data'
API = 'https://api.cian.ru/search-offers/v2/search-offers-desktop/'
OUR_ID = 333127854
OUR = dict(lat=55.748873, lng=37.557277)
KREMLIN = dict(lat=55.752023, lng=37.617499)
# ЦАО (все районы) и Дорогомилово — локации внутри ТТК и у его границы
GEO = [4, 113]
FILTERS = {'total_area': {'type': 'range', 'value': {'gte': 80, 'lte': 115}},
           'price': {'type': 'range', 'value': {'gte': 90_000_000, 'lte': 250_000_000}},
           'room': {'type': 'terms', 'value': [2, 3, 4]}}
REPAIR = {4: 'дизайнерский', 3: 'евро'}
SORTS = [None, 'price_object_order', 'creation_date_desc', 'total_meters_order']
PAGE = 28


def km(a, b):
    dy = (a['lat'] - b['lat']) * 111.2
    dx = (a['lng'] - b['lng']) * 111.2 * math.cos(math.radians(55.75))
    return round(math.hypot(dx, dy), 2)


def query(page, extra, sort=None, geo=GEO, typ='flatsale'):
    q = {'_type': typ, 'engine_version': {'type': 'term', 'value': 2}, 'region': {'type': 'terms', 'value': [1]},
         'page': {'type': 'term', 'value': page}}
    if geo:
        q['geo'] = {'type': 'geo', 'value': [{'type': 'district', 'id': g} for g in geo]}
    if sort:
        q['sort'] = {'type': 'term', 'value': sort}
    q.update(extra)
    d = client().api_post(API, {'jsonQuery': q}).get('data') or {}
    return d.get('offersSerialized') or [], d.get('offerCount') or 0


def crawl(extra, **kw):
    found, total = {}, None
    for sort in SORTS:
        arr, n = query(1, extra, sort, **kw)
        total = total or n
        for o in arr:
            found.setdefault(o['cianId'], o)
        for p in range(2, math.ceil(n / PAGE) + 1):
            arr, _ = query(p, extra, sort, **kw)
            for o in arr:
                found.setdefault(o['cianId'], o)
        print(f'  сортировка {sort}: {len(found)} из {total}', flush=True)
        if len(found) >= total:
            break
    return found, total


def slim(o, repair):
    g = o.get('geo') or {}
    addr = g.get('address') or []
    b = o.get('building') or {}
    nb = o.get('newbuilding') or {}
    jk = g.get('jk') or {}
    und = sorted(g.get('undergrounds') or [], key=lambda u: u.get('time') or 99)
    coord = g.get('coordinates') or {}
    u = o.get('user') or {}
    price = (o.get('bargainTerms') or {}).get('priceRur')
    area = float(o['totalArea']) if o.get('totalArea') else None
    return dict(
        id=o['cianId'], url=f"https://www.cian.ru/sale/flat/{o['cianId']}/", category=o.get('category'),
        rooms=o.get('roomsCount'), bedrooms=o.get('bedroomsCount'), area=area,
        kitchen=float(o['kitchenArea']) if o.get('kitchenArea') else None,
        floor=o.get('floorNumber'), floors=b.get('floorsCount'), price=price,
        ppsqm=round(price / area) if price and area else None,
        okrug=next((a['name'] for a in addr if a.get('type') == 'okrug'), None),
        raion=next((a['name'] for a in addr if a.get('type') == 'raion'), None),
        address=', '.join(a['name'] for a in addr if a.get('type') in ('street', 'house')),
        jk=jk.get('name') or nb.get('name'), jk_id=jk.get('id') or nb.get('id'),
        house_id=next((a['id'] for a in addr if a.get('type') == 'house'), None),
        year=b.get('buildYear'), deadline=(b.get('deadline') or {}).get('year'), material=b.get('materialType'),
        parking=(b.get('parking') or {}).get('type'), apart=bool(o.get('isApartments')),
        repair_filter=repair, decoration=o.get('decoration'), furniture=o.get('hasFurniture'),
        metro=und[0].get('name') if und else None, metro_min=und[0].get('time') if und else None,
        metro_mode=und[0].get('transportType') if und else None,
        lat=coord.get('lat'), lng=coord.get('lng'),
        km_kremlin=km(coord, KREMLIN) if coord else None, km_our=km(coord, OUR) if coord else None,
        seller=u.get('agencyName') or ('собственник' if o.get('isByHomeowner') else None),
        owner=bool(o.get('isByHomeowner')), developer=bool(nb.get('isFromDeveloper')),
        created=o.get('creationDate'), added=o.get('added'), garage=o.get('garage'),
        description=o.get('description') or '',
        photos=[p.get('fullUrl') for p in (o.get('photos') or []) if not p.get('isLayout') and p.get('fullUrl')],
        layouts=[p.get('fullUrl') for p in (o.get('photos') or []) if p.get('isLayout') and p.get('fullUrl')])


def cmd_search(a):
    res = {}
    for code, name in REPAIR.items():
        print('ремонт', name, flush=True)
        found, total = crawl(dict(FILTERS, repair={'type': 'terms', 'value': [code]}))
        for cid, o in found.items():
            res.setdefault(cid, slim(o, name))
    print('без фильтра ремонта (продавец не указал ремонт)', flush=True)
    found, _ = crawl(dict(FILTERS))
    for cid, o in found.items():
        res.setdefault(cid, slim(o, None))
    json.dump(res, open(f'{OUT}/offers.json', 'w'), ensure_ascii=False, indent=1)
    print('предложений:', len(res))


def card(cid):
    h = client().page(f'https://www.cian.ru/sale/flat/{cid}/')
    j = h.find('"offerData":')
    if j < 0:
        return {'status': 'нет данных'}
    od = json.JSONDecoder().raw_decode(h[j + len('"offerData":'):])[0]
    off = od.get('offer') or {}
    b = off.get('building') or {}
    spec = {s.get('title'): s.get('value') for s in ((od.get('newObject') or {}).get('specifications') or [])}
    bti = (od.get('bti') or {}).get('houseData') or {}
    feats = {f.get('label'): f.get('value') for g in (od.get('features') or []) for f in (g.get('features') or [])}
    ch = sorted(od.get('priceChanges') or [], key=lambda c: c.get('changeTime') or '')
    return dict(status=off.get('status'), repair=off.get('repairType'), cls=spec.get('Класс'),
                jk_parking=spec.get('Парковка'), year=b.get('buildYear') or bti.get('yearRelease'),
                ceiling=b.get('ceilingHeight'), parking=(b.get('parking') or {}).get('type'),
                wcs=(off.get('combinedWcsCount') or 0) + (off.get('separateWcsCount') or 0),
                rooms_area=off.get('allRoomsArea'), features=feats, flat_count=bti.get('flatCount'),
                series=bti.get('seriesName'), created=off.get('creationDate'),
                price_first=(ch[0].get('priceData') or {}).get('price') if ch else None,
                price_changes=[(c.get('changeTime', '')[:10], (c.get('priceData') or {}).get('price')) for c in ch],
                views=(od.get('stats') or {}).get('totalViewsFormattedString'),
                description=off.get('description'),
                photos=[p.get('fullUrl') for p in (off.get('photos') or []) if not p.get('isLayout') and p.get('fullUrl')])


def cmd_cards(a):
    offers = json.load(open(f'{OUT}/offers.json'))
    ids = json.load(open(f'{OUT}/shortlist.json')) if not a.ids else [int(x) for x in a.ids.split(',')]
    path = f'{OUT}/cards.json'
    cards = json.load(open(path)) if os.path.exists(path) else {}
    todo = [i for i in ids if str(i) not in cards]
    print('карточек к загрузке:', len(todo), flush=True)
    for n, cid in enumerate(todo, 1):
        cards[str(cid)] = card(cid)
        if n % 10 == 0 or n == len(todo):
            json.dump(cards, open(path, 'w'), ensure_ascii=False, indent=1)
            print(f'  {n}/{len(todo)}', flush=True)
    del offers


def cmd_photos(a):
    from PIL import Image
    from scrapling.fetchers import AsyncStealthySession
    client()                                    # замок: не качаем фото параллельно с другими запросами к ЦИАН
    cards = json.load(open(f'{OUT}/cards.json'))
    offers = json.load(open(f'{OUT}/offers.json'))
    ids = json.load(open(f'{OUT}/shortlist.json'))
    os.makedirs(f'{OUT}/photos', exist_ok=True)
    todo = []
    for cid in ids:
        urls = (cards.get(str(cid)) or {}).get('photos') or (offers.get(str(cid)) or {}).get('photos') or []
        for i, u in enumerate(urls[:a.per]):
            p = f'{OUT}/photos/{cid}_{i:02d}.jpg'
            if not os.path.exists(p):
                todo.append((p, u))
    print('фото к загрузке:', len(todo), flush=True)

    async def run():
        async with AsyncStealthySession(headless=True, max_pages=4) as s:
            async def one(p, u):
                for _ in range(2):
                    try:
                        r = await s.fetch(u)
                        if r.status == 200 and len(r.body) > 1000:
                            im = Image.open(io.BytesIO(r.body)).convert('RGB')
                            im.thumbnail((1024, 1024))
                            im.save(p, 'JPEG', quality=85)
                            return
                        if r.status == 404:
                            return
                    except Exception:
                        await asyncio.sleep(2)
            for k in range(0, len(todo), 40):
                await asyncio.gather(*(one(*t) for t in todo[k:k + 40]))
                print(f'  {min(k + 40, len(todo))}/{len(todo)}', flush=True)
    asyncio.run(run())


def contact_sheet(cid, out, n=16, cols=4, tile=400, label=None):
    """Лист-превью: первые n фото объявления сеткой — для визуального сравнения ремонтов."""
    from PIL import Image, ImageDraw, ImageFont
    paths = sorted(p for p in os.listdir(f'{OUT}/photos') if p.startswith(f'{cid}_'))[:n]
    if not paths:
        return None
    rows = math.ceil(len(paths) / cols)
    top = 34 if label else 0
    sheet = Image.new('RGB', (cols * tile, rows * tile * 3 // 4 + top), 'white')
    for k, p in enumerate(paths):
        im = Image.open(f'{OUT}/photos/{p}')
        im.thumbnail((tile, tile * 3 // 4))
        x, y = (k % cols) * tile, top + (k // cols) * tile * 3 // 4
        sheet.paste(im, (x + (tile - im.width) // 2, y + (tile * 3 // 4 - im.height) // 2))
    if label:
        try:
            font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 20)
        except OSError:
            font = ImageFont.load_default()
        ImageDraw.Draw(sheet).text((8, 6), label, fill='black', font=font)
    sheet.save(out, 'JPEG', quality=82)
    return out


def cmd_sheets(a):
    offers = json.load(open(f'{OUT}/offers.json'))
    os.makedirs(f'{OUT}/sheets', exist_ok=True)
    for cid in json.load(open(f'{OUT}/shortlist.json')):
        x = offers[str(cid)]
        contact_sheet(cid, f'{OUT}/sheets/{cid}.jpg',
                      label=f"{cid} · {x['jk'] or x['address']} · {x['area']:g} м² · {x['price'] / 1e6:.1f} млн")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest='cmd', required=True)
    sub.add_parser('search')
    c = sub.add_parser('cards')
    c.add_argument('--ids', default='')
    c = sub.add_parser('photos')
    c.add_argument('--per', type=int, default=24)
    a = p.parse_args()
    os.makedirs(OUT, exist_ok=True)
    {'search': cmd_search, 'cards': cmd_cards, 'photos': cmd_photos}[a.cmd](a)


if __name__ == '__main__':
    sys.exit(main())
