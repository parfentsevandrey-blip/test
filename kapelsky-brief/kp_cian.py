# -*- coding: utf-8 -*-
"""Когорта вокруг Капельского переулка по выдаче Циан.

Выгрузки собирает клиент из `tools/cian` ветки `claude/cian-apartments-cao-7iqapp`:

    node tools/cian/cian.js search --query cian/queries/<имя>.json --all --out cian/<имя>.json

Четыре запроса: новостройки и вторичка 2010+ по Мещанскому району (id 17)
и то же самое в пешей доступности от «Проспекта Мира» и «Рижской» (id 103,
107, 384, 517; foot_min ≤ 15).

Здесь выдача сводится к когорте: лоты в радиусе от участка, цена метра,
группировка по ЖК и по домам, разбивка по отделке. Отделка на вторичке
из поля Циан ненадёжна (её заполняет продавец), поэтому вторичка
показывается отдельно от новостроек и отделка у неё помечается как заявленная.
"""
import json, os, sys
from math import radians, cos, hypot
from statistics import median

HERE = os.path.dirname(os.path.abspath(__file__))
CIAN = os.path.join(HERE, 'cian')

HOUSE_LAT, HOUSE_LON = 55.784482, 37.631307
NEAR_M = 1500            # ближняя когорта — пешая доступность от участка
RADIUS_M = 2500          # дальняя граница: соседние кварталы того же сектора
PPM_FLOOR = 400_000      # ниже этой цены метра лот не сопоставим с премиумом

DECOR = {
    'fineWithFurniture': 'под ключ с мебелью',
    'fine': 'чистовая',
    'preFine': 'предчистовая',
    'without': 'без отделки',
    'rough': 'черновая',
}
REPAIR = {
    'no': 'без ремонта', 'cosmetic': 'косметический',
    'euro': 'евроремонт', 'design': 'дизайнерский',
}


def metres(lat, lon):
    dx = radians(lon - HOUSE_LON) * cos(radians((lat + HOUSE_LAT) / 2)) * 6371000
    dy = radians(lat - HOUSE_LAT) * 6371000
    return hypot(dx, dy)


def load(name):
    path = os.path.join(CIAN, name + '.json')
    if not os.path.exists(path):
        return []
    d = json.load(open(path, encoding='utf-8'))
    lots = d.get('lots') if isinstance(d, dict) else d
    return lots or []


DROPPED = {'без координат или цены': 0, 'дальше радиуса': 0, 'метр ниже порога': 0}


def prep(lots, source):
    out = []
    for l in lots:
        area, price = l.get('totalArea'), l.get('priceRub')
        lat, lon = l.get('lat'), l.get('lng')
        if not area or not price or lat is None or lon is None:
            DROPPED['без координат или цены'] += 1
            continue
        d = metres(lat, lon)
        if d > RADIUS_M:
            DROPPED['дальше радиуса'] += 1
            continue
        ppm = price / area
        if ppm < PPM_FLOOR:
            DROPPED['метр ниже порога'] += 1
            continue
        out.append({
            'id': l.get('id'), 'source': source, 'dist': round(d),
            'complex': l.get('complex'), 'street': l.get('street'),
            'house': l.get('house'), 'rooms': l.get('rooms'),
            'area': round(area, 1), 'price': price, 'ppm': round(ppm),
            'floor': l.get('floor'), 'floors': l.get('floors'),
            'year': l.get('buildYear'),
            'deadline': l.get('deadline'), 'finished': l.get('houseFinished'),
            'apart': bool(l.get('isApartments')),
            'decor': DECOR.get(l.get('decoration') or l.get('decorFilter'), None),
            'repair': REPAIR.get(l.get('repairType'), None),
            'sale': l.get('saleType'), 'days': l.get('daysOnMarket'),
            'url': l.get('url'), 'lat': lat, 'lng': lon,
        })
    return out


def cards():
    """repairType в поисковой выдаче Циан пустой — он живёт только в карточке.

    Карточки дособраны командой `card --from cian/resale_ids.json`.
    """
    out = {}
    for name in ('resale_cards.json', 'mod_cards.json'):
        path = os.path.join(CIAN, name)
        if not os.path.exists(path):
            continue
        for c in json.load(open(path, encoding='utf-8')):
            if c.get('id'):
                out[c['id']] = c
    return out


def dedupe(lots):
    """Один и тот же лот приходит из районного и из метро-запроса."""
    seen = {}
    for l in lots:
        if l['id'] not in seen:
            seen[l['id']] = l
    return list(seen.values())


def key_of(l):
    if l['complex']:
        return l['complex']
    return ' '.join(x for x in (l['street'], l['house']) if x) or '—'


def group(lots):
    g = {}
    for l in lots:
        g.setdefault(key_of(l), []).append(l)
    rows = []
    for name, ls in g.items():
        ppm = sorted(x['ppm'] for x in ls)
        areas = sorted(x['area'] for x in ls)
        dec = {}
        for x in ls:
            k = x['decor'] or x['repair'] or 'не указана'
            dec[k] = dec.get(k, 0) + 1
        dl = [x['deadline'] for x in ls if x['deadline']]
        rows.append({
            'name': name, 'n': len(ls),
            'dist': min(x['dist'] for x in ls),
            'lat': round(median([x['lat'] for x in ls]), 6),
            'lng': round(median([x['lng'] for x in ls]), 6),
            'quarter': (dl[0].get('quarter') if dl else None),
            'floors': max([x['floors'] for x in ls if x['floors']] or [0]) or None,
            'new': sum(1 for x in ls if x['source'].endswith('new')),
            'apart': sum(1 for x in ls if x['apart']),
            'ppmLo': ppm[0], 'ppmHi': ppm[-1], 'ppmMed': round(median(ppm)),
            'areaLo': areas[0], 'areaHi': areas[-1],
            'priceLo': min(x['price'] for x in ls), 'priceHi': max(x['price'] for x in ls),
            'year': max([x['year'] for x in ls if x['year']] or [None]) if any(x['year'] for x in ls) else None,
            'deadline': max([d['year'] for d in dl] or [None]) if dl else None,
            'decor': dec,
            'daysMed': round(median([x['days'] for x in ls if x['days'] is not None] or [0])),
        })
    rows.sort(key=lambda r: -r['ppmMed'])
    return rows


nf = lambda v: f'{v:,.0f}'.replace(',', ' ')

if __name__ == '__main__':
    raw = []
    for name, src in [('mesh-new', 'mesh-new'), ('mesh-resale', 'mesh-resale'),
                      ('metro-new', 'metro-new'), ('metro-resale', 'metro-resale')]:
        ls = load(name)
        print(f'{name:14s} лотов в выгрузке: {len(ls)}')
        raw += prep(ls, src)
    lots = dedupe(raw)
    cd = cards()
    filled = 0
    for l in lots:
        c = cd.get(l['id'])
        if c and c.get('repairType') and not l['repair']:
            l['repair'] = REPAIR.get(c['repairType'], c['repairType'])
            l['bti'] = c.get('bti')
            filled += 1
    print(f'\nремонт добран из карточек у {filled} лотов вторички')
    print('отсеяно: ' + ', '.join(f'{k} — {v}' for k, v in DROPPED.items()))
    print(f'в радиусе {RADIUS_M} м и дороже {nf(PPM_FLOOR)} ₽/м²: {len(lots)} лотов')

    news = [l for l in lots if l['source'].endswith('new')]
    resale = [l for l in lots if l['source'].endswith('resale')]
    print(f'  новостройки {len(news)}, вторичка {len(resale)}, '
          f'апартаменты {sum(1 for l in lots if l["apart"])}')

    for title, subset in [('НОВОСТРОЙКИ', news), ('ВТОРИЧКА 2010+', resale)]:
        print(f'\n══ {title} ══')
        for r in group(subset):
            dec = ', '.join(f'{k}: {v}' for k, v in sorted(r['decor'].items(), key=lambda x: -x[1]))
            mark = '·' if r['dist'] <= NEAR_M else ' '
            print(f"{mark} {r['name'][:36]:38s} {r['n']:3d} лот {r['dist']:5d} м  "
                  f"{nf(r['ppmLo']):>9s}–{nf(r['ppmHi']):>9s} мед {nf(r['ppmMed']):>9s}  "
                  f"{r['areaLo']:5.1f}–{r['areaHi']:6.1f} м²  {dec}")

    gn, gr = group(news), group(resale)
    json.dump({'near': NEAR_M, 'radius': RADIUS_M, 'floor': PPM_FLOOR,
               'lots': lots, 'new': gn, 'resale': gr},
              open(os.path.join(CIAN, 'cohort.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    print('\n-> cian/cohort.json')

    # ── отбор в справку ────────────────────────────────────────────────────
    # Новостройки — все группы от восьми лотов; вторичка — дома, где в продаже
    # хотя бы три лота и есть с чем сравнивать по метру. Пары «Резиденция
    # Сокольники» и «Сокольнический вал» выпадают: 1,7–1,8 км, апартаменты
    # и метр около 0,5 млн — ту же роль в подборке играет «Мод», который ближе.
    PICK_NEW = ['ФАНТОМ', 'Клубный дом Форум', 'Дом Франка', 'Ридж', 'Мод']
    PICK_RES = ['Barkli Park (Баркли Парк)', 'Sole Hill (Соле Хилл)', 'Dialog (Диалог)',
                'Клубный дом ЦВЕТ32', 'Легенда Цветного', 'Мод']
    # Номер закреплён за домом, а не за строкой: «Мод» попадает в обе таблицы
    # (корпус ещё продаётся застройщиком и уже перепродаётся), но пин на карте
    # у него один.
    peers, nums = [], {}
    for kind, picks, rows in (('new', PICK_NEW, gn), ('resale', PICK_RES, gr)):
        for name in picks:
            r = next((x for x in rows if x['name'] == name), None)
            if not r:
                print(f'! в когорте нет группы «{name}»')
                continue
            first = name not in nums
            if first:
                nums[name] = len(nums) + 1
            peers.append({**r, 'no': nums[name], 'kind': kind, 'pin': first})
    json.dump(peers, open(os.path.join(CIAN, 'peers.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    print(f'-> cian/peers.json — {len(peers)} строк, {len(nums)} пинов на карте')
