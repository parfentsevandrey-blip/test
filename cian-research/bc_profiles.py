#!/usr/bin/env python3
"""Конкурентная карта бизнес-центров вокруг каждого объекта.

Склеивает: кластеры и лоты (clusters.json) + страницы БЦ на ЦИАН (bc_pages.json) + собственный расчёт пешей доступности метро
по координатам (станции — metro_hh.json; маршрут ≈ прямая × 1,35, 80 м/мин — так же считает ЦИАН) + визуальную оценку
здания по фото (bc_ratings.json, шкала 1–5, ставится вручную по листам sheet_rate_*.jpg).

python3 bc_profiles.py           → commercial-data/bc_profiles.json
python3 bc_profiles.py sheets    → листы фото для БЦ без оценки: commercial-data/bcphotos/sheet_rate_N.jpg
"""
import io
import json
import math
import os
import sys
import urllib.request

from PIL import Image, ImageDraw, ImageFont

CENTER = (55.7520, 37.6175)          # Кремль
SUBJ = {'yard': (55.766283, 37.526851), 'kaleidoscope': (55.66892, 37.63093), 'imagine': (55.678996, 37.640381)}
SUBJ_URL = {'yard': 'https://www.cian.ru/biznes-centr-yard-moskva-5416/',
            'imagine': 'https://www.cian.ru/biznes-centr-imagine-plaza-moskva-42854/'}
PH = 'commercial-data/bcphotos'


def km(a, b):
    dy = (a[0] - b[0]) * 111.2
    dx = (a[1] - b[1]) * 111.2 * math.cos(math.radians((a[0] + b[0]) / 2))
    return math.hypot(dx, dy)


def stations():
    d = json.load(open('commercial-data/metro_hh.json'))
    out = {}
    for line in d['lines']:
        for s in line['stations']:
            name = s['name'].replace('ё', 'е').replace('Ё', 'Е')
            out.setdefault(name, (s['lat'], s['lng'], line['name'].strip()))
    return out


ST = stations()


def nearest(pt, n=2):
    res = sorted(((km(pt, (la, ln)), name, line) for name, (la, ln, line) in ST.items()))[:n]
    return [dict(name=name, line=line, km=round(d, 2), walk=round(d * 1.35 * 1000 / 80)) for d, name, line in res]


def yr(v):
    try:
        return int(str(v)[:4])
    except (TypeError, ValueError):
        return None


def load():
    cl = json.load(open('commercial-data/clusters.json'))
    pages = json.load(open('commercial-data/bc_pages.json')) if os.path.exists('commercial-data/bc_pages.json') else {}
    rat = json.load(open('commercial-data/bc_ratings.json')) if os.path.exists('commercial-data/bc_ratings.json') else {}
    rows = {r['id']: r for r in json.load(open('commercial-data/offers.json'))}
    return cl, pages, rat, rows


def profile(b, page, rat, rows):
    ids = b['sale'] + b['rent']
    pt = (page.get('lat'), page.get('lng')) if page.get('lat') else (b.get('lat'), b.get('lng'))
    if not pt[0]:
        r0 = rows[ids[0]]
        pt = (r0['lat'], r0['lng'])
    near = nearest(pt)
    rt = rat.get(b.get('url') or '') or {}
    return dict(
        url=b.get('url'), name=b.get('name'), title=page.get('title') or b.get('name'), cls=page.get('cls') or b.get('cls'),
        cluster=b['cluster'], year=yr(page.get('year')) or yr(b.get('year')), floors=page.get('floors') or b.get('floors'),
        total_area=page.get('total_area'), parking=page.get('parking'), rent_range=page.get('rent_range'), entrance=page.get('entrance'),
        lifts=page.get('lifts'), infra=page.get('infra') or [], tech=page.get('tech') or {}, address=b.get('address'),
        cian_metro=page.get('metro') or [], metro=near[0]['name'], walk=near[0]['walk'], metro2=near[1], near=near,
        center_km=round(km(pt, CENTER), 1), lat=pt[0], lng=pt[1], zone=b.get('zone'),
        sale=b['sale'], rent=b['rent'], sale_med=b.get('sale_med'), rent_med=b.get('rent_med'),
        offplan=(yr(page.get('year')) or yr(b.get('year')) or 0) > 2026 or (any(rows[i].get('offplan') for i in ids) and (yr(page.get('year')) or 2030) > 2025),
        rating=rt.get('rating'), rating_note=rt.get('note'), photos=(page.get('photos') or [])[:4],
        offer_photos=[p for i in ids for p in rows[i]['photos'][:2]][:4])


def main():
    cl, pages, rat, rows = load()
    out = {}
    for key, res in cl.items():
        bcs = []
        for b in res['bcs'].values():
            bcs.append(profile(b, pages.get(b.get('url') or '', {}), rat, rows))
        bcs.sort(key=lambda p: (p['cluster'], p['walk']))
        subj = dict(key=key, near=nearest(SUBJ[key], 3), center_km=round(km(SUBJ[key], CENTER), 1),
                    page=pages.get(SUBJ_URL.get(key, ''), {}), rating=(rat.get(SUBJ_URL.get(key, '')) or rat.get(key) or {}).get('rating'))
        out[key] = dict(subject=subj, bcs=bcs)
        print(f"## {key}: метро {subj['near'][0]['name']} {subj['near'][0]['walk']} мин, до центра {subj['center_km']} км, БЦ {len(bcs)}, "
              f"без оценки {sum(1 for p in bcs if p['rating'] is None)}")
    json.dump(out, open('commercial-data/bc_profiles.json', 'w'), ensure_ascii=False, indent=1)


def get(u):
    req = urllib.request.Request(u, headers={'User-Agent': 'Mozilla/5.0'})
    return urllib.request.urlopen(req, timeout=30).read()


def sheets():
    """Листы для визуальной оценки: БЦ без оценки, у которых есть продажи или ≥2 аренды в локации объектов."""
    cl, pages, rat, rows = load()
    todo, seen = [], set()
    for key, res in cl.items():
        for b in res['bcs'].values():
            u = b.get('url')
            if not u or u in seen or u in rat or not (b['sale'] or len(b['rent']) >= 2):
                continue
            seen.add(u)
            page = pages.get(u, {})
            ph = (page.get('photos') or [])[:2] + [p for i in b['sale'] + b['rent'] for p in rows[i]['photos'][:1]]
            todo.append((b, page, ph[:4]))
    os.makedirs(PH, exist_ok=True)
    font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 15)
    W, H, per = 260, 190, 6
    index = []
    for s in range(0, len(todo), per):
        part = todo[s:s + per]
        img = Image.new('RGB', (W * 4 + 10, (H + 24) * len(part)), 'white')
        d = ImageDraw.Draw(img)
        for i, (b, page, ph) in enumerate(part):
            y = i * (H + 24)
            no = s + i + 1
            index.append(dict(no=no, url=b['url'], name=page.get('title') or b['name']))
            d.text((4, y + 3), f"{no}. {page.get('title') or b['name']} | {page.get('cls') or b['cls']} | {page.get('year') or b['year']} | "
                               f"{page.get('floors') or ''} | {(b.get('address') or '').replace('Россия, ', '').replace('Москва, ', '')[:38]}",
                   fill='black', font=font)
            for k, u in enumerate(ph):
                try:
                    im = Image.open(io.BytesIO(get(u))).convert('RGB')
                    im.thumbnail((W - 4, H - 4))
                    img.paste(im, (k * W, y + 22))
                except Exception as e:
                    d.text((k * W + 10, y + 80), f'нет фото ({str(e)[:20]})', fill='red', font=font)
        img.save(f'{PH}/sheet_rate_{s // per + 1}.jpg', quality=72)
    json.dump(index, open(f'{PH}/sheet_rate_index.json', 'w'), ensure_ascii=False, indent=1)
    print('БЦ без оценки:', len(todo), 'листов:', math.ceil(len(todo) / per))


if __name__ == '__main__':
    sheets() if sys.argv[1:] == ['sheets'] else main()
