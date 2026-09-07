#!/usr/bin/env python3
"""
Сборка документа по коммерческим лотам Циан.

    python3 tools/cian/build-doc.py --offers offers.json --out docs/commercial/

Шаги: разбор выгрузки by-ids -> загрузка фотографий листинга и статических
карт Яндекса по координатам лота -> сжатие в data: URI -> HTML.

Фотографии и карты вшиваются в файл: страница публикуется как артефакт, а
там внешние картинки режет CSP, поэтому ссылка на cdn-cian не годится.
"""
import argparse, base64, io, json, os, re, subprocess, sys
from concurrent.futures import ThreadPoolExecutor

from PIL import Image

YANDEX_STATIC = 'https://static-maps.yandex.ru/1.x/'

CATEGORY_RU = {
    'buildingSale': 'Отдельно стоящее здание',
    'businessSale': 'Готовый бизнес',
    'freeAppointmentObjectSale': 'Помещение свободного назначения',
    'shoppingAreaSale': 'Торговая площадь',
    'officeSale': 'Офис',
    'industrySale': 'Производство',
    'warehouseSale': 'Склад',
}

CONDITION_RU = {
    'typical': 'типовой ремонт',
    'design': 'дизайнерский ремонт',
    'cosmeticRepairsRequired': 'требуется косметический ремонт',
    'majorRepairsRequired': 'требуется капитальный ремонт',
    'finishing': 'под чистовую отделку',
    'office': 'офисная отделка',
}

VAT_RU = {
    'included': 'НДС включён',
    'notIncluded': 'НДС не включён',
    'usn': 'УСН',
    'noVat': 'без НДС',
}

BUILDING_TYPE_RU = {
    'free': 'свободного назначения',
    'other': 'иное',
    'business': 'бизнес-центр',
    'mansion': 'особняк',
    'shopping': 'торговый центр',
    'residential': 'жилой дом',
    'administrative': 'административное',
    'manufacture': 'производственное',
    'warehouse': 'складское',
}

HEATING_RU = {'central': 'центральное', 'autonomous': 'автономное', 'no': 'нет'}


def money(v):
    """1 234 567 — узкий пробел, чтобы цена не рвалась по строкам."""
    if v is None:
        return None
    return f'{int(round(v)):,}'.replace(',', ' ')


def clean_address(offer):
    """geo.userInput приходит в шести разных форматах — от «Россия, Москва,
    улица X» до «г Москва, ул X, д 1 стр 1». Приводим к одному виду."""
    raw = (offer.get('geo') or {}).get('userInput') or ''
    s = raw.strip()
    for prefix in ('Россия, Москва город, ', 'Россия, Москва, ', 'Москва, Москва, ',
                   'г Москва, ', 'Москва, '):
        if s.startswith(prefix):
            s = s[len(prefix):]
            break
    s = re.sub(r',\s*Москва\s*$', '', s)
    s = re.sub(r'\bдом\s+', 'д. ', s)
    s = re.sub(r'\bстроение\s+', 'стр. ', s)
    s = re.sub(r'\bд\s+(\d)', r'д. \1', s)
    s = re.sub(r'\bстр\s+(\d)', r'стр. \1', s)
    s = re.sub(r'\bул\s+', 'ул. ', s)
    s = re.sub(r'\s+', ' ', s).strip(' ,')
    return s or raw


def extract(offer):
    geo = offer.get('geo') or {}
    coords = geo.get('coordinates') or {}
    bld = offer.get('building') or {}
    bt = offer.get('bargainTerms') or {}

    metro = []
    for u in (geo.get('undergrounds') or [])[:3]:
        if u.get('name'):
            metro.append({
                'name': u['name'],
                'time': u.get('time'),
                'walk': u.get('transportType') == 'walk',
                'color': '#' + (u.get('lineColor') or '888888'),
            })

    photos = []
    for ph in offer.get('photos') or []:
        url = ph.get('fullUrl') or ph.get('thumbnail2Url')
        if url:
            photos.append({'url': url, 'plan': bool(ph.get('isFloorPlan') or ph.get('isLayout'))})

    area = offer.get('totalArea')
    total = offer.get('priceTotalRur') or bt.get('priceRur')
    per_m2 = offer.get('pricePerUnitAreaRur')
    # Циан иногда не считает ₽/м² сам — считаем, чтобы сортировка не рассыпалась.
    if not per_m2 and total and area:
        per_m2 = total / float(area)

    # Этаж: у ОСЗ этажа нет, есть этажность. floorFrom/floorTo — у площадей
    # «с 2 по 4 этаж».
    floor = None
    if offer.get('floorNumber'):
        floor = str(offer['floorNumber'])
    elif offer.get('floorFrom') and offer.get('floorTo'):
        floor = f"{offer['floorFrom']}–{offer['floorTo']}"
    elif offer.get('floorFrom'):
        floor = str(offer['floorFrom'])

    features = []
    flag_map = [
        ('hasShopWindows', 'витринное остекление'), ('hasParking', 'парковка'),
        ('hasLift', 'лифт'), ('hasSecurity', 'охрана'),
        ('hasFurniture', 'мебель'), ('hasInternet', 'интернет'),
        ('hasRamp', 'пандус'), ('hasExtinguishingSystem', 'система пожаротушения'),
        ('hasConditioner', 'кондиционирование'), ('hasEquipment', 'оборудование'),
        ('hasGarage', 'гараж'), ('hasPool', 'бассейн'),
    ]
    for key, label in flag_map:
        if offer.get(key):
            features.append(label)

    return {
        'id': offer.get('cianId') or offer.get('id'),
        'url': offer.get('fullUrl') or f"https://www.cian.ru/sale/commercial/{offer.get('cianId')}/",
        'name': offer.get('name') or CATEGORY_RU.get(offer.get('category'), 'Объект'),
        'kind': CATEGORY_RU.get(offer.get('category'), offer.get('category')),
        'address': clean_address(offer),
        'lat': coords.get('lat'),
        'lng': coords.get('lng'),
        'area': float(area) if area else None,
        'priceTotal': total,
        'pricePerM2': round(per_m2) if per_m2 else None,
        'vat': VAT_RU.get(bt.get('vatType')),
        'bargain': bool(bt.get('bargainAllowed')),
        'condition': CONDITION_RU.get(offer.get('conditionType')),
        'floor': floor,
        'floorsCount': bld.get('floorsCount'),
        'buildYear': bld.get('buildYear'),
        'buildingType': BUILDING_TYPE_RU.get(bld.get('type')),
        'heating': HEATING_RU.get(bld.get('heatingType')),
        'material': bld.get('materialType'),
        'liftsCargo': bld.get('cargoLiftsCount'),
        'liftsPass': bld.get('passengerLiftsCount'),
        'entrance': offer.get('accessType'),
        'electricity': offer.get('electricityPower'),
        'monthlyIncome': offer.get('monthlyIncome'),
        'metro': metro,
        # В geo.districts лежат оба уровня сразу: округ (okrug) и район
        # (raion). В address они не различаются — оба идут как geoType
        # district, поэтому брать надо отсюда.
        'district': next((d.get('name') for d in (geo.get('districts') or [])
                          if d.get('type') == 'raion'), None),
        'okrug': next((d.get('name') for d in (geo.get('districts') or [])
                       if d.get('type') == 'okrug'), None),
        'description': (offer.get('description') or '').strip(),
        'photos': photos,
        'features': features,
        'added': offer.get('added'),
    }


def fetch(url, dest, timeout=60):
    """curl вместо requests: прокси и CA уже настроены в окружении."""
    r = subprocess.run(['curl', '-sS', '--max-time', str(timeout), '-o', dest, '-w', '%{http_code}',
                        '-H', 'User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) '
                              'AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36',
                        url], capture_output=True, text=True)
    return r.stdout.strip() == '200' and os.path.exists(dest) and os.path.getsize(dest) > 1024


def to_data_uri(path, max_w, quality):
    """Сжимаем до вставки: 20 лотов по несколько кадров в base64 иначе
    не влезут в предел артефакта."""
    try:
        im = Image.open(path)
        im = im.convert('RGB')
        if im.width > max_w:
            im = im.resize((max_w, round(im.height * max_w / im.width)), Image.LANCZOS)
        buf = io.BytesIO()
        im.save(buf, 'JPEG', quality=quality, optimize=True, progressive=True)
        return 'data:image/jpeg;base64,' + base64.b64encode(buf.getvalue()).decode()
    except Exception as e:
        print(f'  ! не смог обработать {path}: {e}', file=sys.stderr)
        return None


def map_url(lat, lng, w=650, h=400, z=16):
    return (f'{YANDEX_STATIC}?ll={lng:.6f},{lat:.6f}&z={z}&size={w},{h}'
            f'&l=map&pt={lng:.6f},{lat:.6f},pm2rdm')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--offers', required=True)
    ap.add_argument('--out', required=True)
    ap.add_argument('--photos', type=int, default=6, help='кадров на объект')
    ap.add_argument('--photo-width', type=int, default=880)
    ap.add_argument('--quality', type=int, default=76)
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)
    cache = os.path.join(args.out, 'cache')
    os.makedirs(cache, exist_ok=True)

    offers = json.load(open(args.offers, encoding='utf-8'))
    lots = [extract(o) for o in offers]

    # Порядок в документе — по ₽/м², как просили для итоговой таблицы.
    lots.sort(key=lambda x: x['pricePerM2'] or 0)

    jobs = []
    for lot in lots:
        # Кадры интерьера важнее планировок: планировки уводим в конец.
        ordered = [p for p in lot['photos'] if not p['plan']] + [p for p in lot['photos'] if p['plan']]
        lot['_pick'] = ordered[:args.photos]
        for i, p in enumerate(lot['_pick']):
            jobs.append((p['url'], os.path.join(cache, f"{lot['id']}-{i}.jpg")))
        if lot['lat'] and lot['lng']:
            jobs.append((map_url(lot['lat'], lot['lng']),
                         os.path.join(cache, f"{lot['id']}-map.png")))

    todo = [(u, d) for u, d in jobs if not (os.path.exists(d) and os.path.getsize(d) > 1024)]
    print(f'загружаю {len(todo)} файлов (в кэше уже {len(jobs) - len(todo)})')
    with ThreadPoolExecutor(max_workers=8) as ex:
        results = list(ex.map(lambda t: fetch(t[0], t[1]), todo))
    print(f'загружено {sum(results)} из {len(todo)}')

    for lot in lots:
        imgs = []
        for i, _ in enumerate(lot['_pick']):
            p = os.path.join(cache, f"{lot['id']}-{i}.jpg")
            if os.path.exists(p) and os.path.getsize(p) > 1024:
                uri = to_data_uri(p, args.photo_width, args.quality)
                if uri:
                    imgs.append(uri)
        lot['images'] = imgs
        mp = os.path.join(cache, f"{lot['id']}-map.png")
        lot['map'] = (to_data_uri(mp, 650, 82)
                      if os.path.exists(mp) and os.path.getsize(mp) > 1024 else None)
        del lot['_pick']

    data_path = os.path.join(args.out, 'lots.json')
    with open(data_path, 'w', encoding='utf-8') as f:
        json.dump(lots, f, ensure_ascii=False, indent=1)

    total_photos = sum(len(l['images']) for l in lots)
    maps = sum(1 for l in lots if l['map'])
    size_mb = os.path.getsize(data_path) / 1e6
    print(f'лотов {len(lots)}, фото вшито {total_photos}, карт {maps}, '
          f'данные {size_mb:.1f} МБ -> {data_path}')
    missing = [l['id'] for l in lots if not l['images']]
    if missing:
        print(f'без фотографий: {missing}')
    if not all(l['map'] for l in lots):
        print(f"без карты: {[l['id'] for l in lots if not l['map']]}")


if __name__ == '__main__':
    main()
