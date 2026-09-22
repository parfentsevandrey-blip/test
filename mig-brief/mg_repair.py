# -*- coding: utf-8 -*-
"""Квартиры с дизайнерским ремонтом в готовых домах вокруг участка «МИГ».

Поле `repairType` в поисковой выдаче Циан пустое — оно живёт только в карточке
объявления, поэтому карточки 102 лотов готовых домов дочитаны отдельно
командой `card --from resale_ids.json`.

    python3 mg_repair.py           # отобрать лоты и скачать кадры в photos/
    python3 mg_repair.py sheet     # контактные листы photos/_repair*.jpg
    python3 mg_repair.py cards     # собрать карточки из PICK -> assets/card_*.jpg
"""
import json, os, sys, urllib.request
import concurrent.futures as cf
from math import radians, cos, hypot
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
PHOTOS = os.path.join(HERE, 'photos')
ASSETS = os.path.join(HERE, 'assets')
UA = ('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 '
      '(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36')
TAKE = [0, 1, 2, 3, 4, 5]
MID = (55.7859, 37.5510)
RATIO, WIDE = 3 / 2, 420

nf = lambda v: f'{v:,.0f}'.replace(',', ' ')
ru = lambda v: f'{v:.1f}'.replace('.', ',')

# Циан пишет названия целиком, вместе с транслитерацией в скобках.
NAME = {
    'Прайм Парк (Prime Park)': 'Прайм Парк',
    'Комплекс апартаментов Alcon Tower (Алкон Тауэр)': 'Alcon Tower',
    'Квартал апартаментов Искра-Парк': 'Искра-Парк',
}

#  id объявления: номер кадра, который идёт в карточку.
#  Выбирается глазами по контактным листам: первый кадр часто отдан фасаду,
#  плану или подъезду, а в карточке нужен интерьер.
PICK = {
    334001282: 0,   # Прайм Парк, 72,8 м²
    333460889: 2,   # Прайм Парк, 138 м²
    328256698: 0,   # Прайм Парк, 138,2 м²
    332578060: 1,   # Прайм Парк, 136 м²
    330806085: 0,   # Прайм Парк, 96,3 м²
    334054284: 0,   # Прайм Парк, 134,4 м²
    333563160: 1,   # Прайм Парк, 96 м²
    333582691: 0,   # Прайм Парк, 111 м²
    332092029: 1,   # Прайм Парк, 137 м²
    333567045: 0,   # ВТБ Арена парк, 202 м²
    334133382: 0,   # Прайм Парк, 47,9 м²
    333604640: 0,   # Прайм Парк, 42 м²
    331333652: 4,   # Прайм Парк, 113 м²
    334063192: 0,   # Прайм Парк, 118 м²
    332987951: 0,   # ВТБ Арена парк, 165 м²
    333888589: 0,   # Прайм Парк, 41,9 м²
    333642622: 0,   # ВТБ Арена парк, 142,2 м²
    325383444: 0,   # ВТБ Арена парк, 150 м²
}


def metres(lat, lng):
    dx = radians(lng - MID[1]) * cos(radians((lat + MID[0]) / 2)) * 6371000
    dy = radians(lat - MID[0]) * 6371000
    return hypot(dx, dy)


def feed():
    """Все лоты выдачи по id: цены, площади, фото."""
    out = {}
    for f in ('metro-resale.json', 'metro-new.json', 'begovoy-new.json'):
        p = os.path.join(HERE, f)
        if not os.path.exists(p):
            continue
        for l in json.load(open(p, encoding='utf-8'))['lots']:
            out.setdefault(l['id'], l)
    return out


def cards_raw():
    p = os.path.join(HERE, 'resale_cards.json')
    if not os.path.exists(p):
        return {}
    return {c['id']: c for c in json.load(open(p, encoding='utf-8')) if c.get('id')}


def enrich():
    """Лоты готовых домов с прочитанным ремонтом."""
    fd, cd = feed(), cards_raw()
    out = []
    for i, c in cd.items():
        l = fd.get(i)
        if not l or not l.get('totalArea') or not l.get('priceRub'):
            continue
        out.append({
            'id': i,
            'complex': NAME.get(l.get('complex') or '', l.get('complex'))
                       or ' '.join(x for x in (l.get('street'), l.get('house')) if x),
            'area': round(l['totalArea'], 1),
            'price': l['priceRub'],
            'ppm': round(l['priceRub'] / l['totalArea']),
            'floor': l.get('floor'), 'floors': l.get('floors'),
            'year': (c.get('bti') or {}).get('yearRelease') or l.get('buildYear'),
            'repair': c.get('repairType'),
            'dist': round(metres(l['lat'], l['lng'])),
            'url': l.get('url'), 'photos': l.get('photos') or [],
        })
    return out


def design(lots, limit=30, radius=2000):
    """Дизайнерский ремонт в километре вокруг, сверху вниз по цене метра."""
    ls = [l for l in lots
          if l['repair'] == 'design' and len(l['photos']) >= 4 and l['dist'] <= radius]
    ls.sort(key=lambda l: -l['ppm'])
    return ls[:limit]


def fetch(args):
    url, dst = args
    if os.path.exists(dst) and os.path.getsize(dst) > 8000:
        return dst
    req = urllib.request.Request(url, headers={'User-Agent': UA,
                                               'Referer': 'https://www.cian.ru/'})
    for _ in range(4):
        try:
            data = urllib.request.urlopen(req, timeout=40).read()
            open(dst, 'wb').write(data)
            Image.open(dst).verify()
            return dst
        except Exception:
            if os.path.exists(dst):
                os.remove(dst)
    print(f'  не скачался {os.path.basename(dst)}')
    return None


def download(ls):
    os.makedirs(PHOTOS, exist_ok=True)
    jobs = [(l['photos'][n], os.path.join(PHOTOS, f"{l['id']}_{n}.jpg"))
            for l in ls for n in TAKE if n < len(l['photos'])]
    with cf.ThreadPoolExecutor(8) as ex:
        got = [x for x in ex.map(fetch, jobs) if x]
    print(f'кадров скачано {len(got)} из {len(jobs)}')


def sheets(ls):
    """Контактный лист по каждому лоту: подписан id и номер кадра."""
    F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 15)
    FB = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 13)
    per, cell = 6, 250
    pages = [ls[i:i + 6] for i in range(0, len(ls), 6)]
    for pi, page in enumerate(pages):
        sheet = Image.new('RGB', (per * cell, len(page) * (cell + 24)), 'white')
        dr = ImageDraw.Draw(sheet)
        for ri, l in enumerate(page):
            y = ri * (cell + 24)
            dr.text((4, y + cell + 4),
                    f"{l['id']}  {l['complex']}  {l['area']} м²  эт {l['floor']}  "
                    f"{l['ppm'] // 1000} тыс/м²", fill='black', font=F)
            for ci, n in enumerate(TAKE):
                p = os.path.join(PHOTOS, f"{l['id']}_{n}.jpg")
                if not os.path.exists(p):
                    continue
                im = Image.open(p).convert('RGB')
                im.thumbnail((cell - 6, cell - 6))
                sheet.paste(im, (ci * cell + 3, y + 3))
                dr.rectangle([ci * cell + 3, y + 3, ci * cell + 26, y + 20], fill='black')
                dr.text((ci * cell + 8, y + 4), str(n), fill='white', font=FB)
        out = os.path.join(PHOTOS, f'_repair{pi}.jpg')
        sheet.save(out, quality=78)
        print(out, sheet.size)


def crop(im):
    w, h = im.size
    if w / h > RATIO:
        nw = int(h * RATIO)
        return im.crop(((w - nw) // 2, 0, (w - nw) // 2 + nw, h))
    nh = int(w / RATIO)
    return im.crop((0, (h - nh) // 2, w, (h - nh) // 2 + nh))


def build_cards(ls):
    by_id = {l['id']: l for l in ls}
    cards = []
    for lot_id, frame in PICK.items():
        l = by_id.get(lot_id)
        src = os.path.join(PHOTOS, f'{lot_id}_{frame}.jpg')
        if not l or not os.path.exists(src):
            print(f'{lot_id} — нет лота или кадра {frame}')
            continue
        im = crop(Image.open(src).convert('RGB'))
        if im.width > WIDE:
            im = im.resize((WIDE, int(WIDE / RATIO)), Image.LANCZOS)
        out = os.path.join(ASSETS, f'card_{lot_id}.jpg')
        im.save(out, 'JPEG', quality=84, subsampling=0, optimize=True)
        cards.append({
            'file': f'card_{lot_id}.jpg', 'name': l['complex'],
            'area': ru(l['area']),
            'floor': f"{l['floor']}" + (f" из {l['floors']}" if l['floors'] else ''),
            'price': ru(l['price'] / 1e6), 'ppm': nf(l['ppm']), 'ppmNum': l['ppm'],
            'dist': l['dist'], 'url': l['url'],
        })
        print(f"{l['complex'][:22]:24s} кадр {frame}  {im.size}")
    cards.sort(key=lambda c: -c['ppmNum'])
    json.dump(cards, open(os.path.join(HERE, 'mg_repair_cards.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    print(f'-> mg_repair_cards.json — {len(cards)} карточек')


if __name__ == '__main__':
    lots = enrich()
    print(f'карточек прочитано: {len(lots)}')
    kinds = {}
    for l in lots:
        kinds[l['repair']] = kinds.get(l['repair'], 0) + 1
    print('ремонт:', kinds)
    ds = design(lots)
    print(f'с дизайнерским ремонтом и кадрами: {len(ds)}')
    json.dump(lots, open(os.path.join(HERE, 'mg_repair_lots.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    arg = sys.argv[1] if len(sys.argv) > 1 else ''
    if arg == 'sheet':
        sheets(ds)
    elif arg == 'cards':
        build_cards(ds)
    else:
        download(ds)
