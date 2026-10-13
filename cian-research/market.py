#!/usr/bin/env python3
"""Барометр рынка квартир ЦИАН по району.

Подкоманды:
  collect     ежедневный сбор: вся выдача района, карточки новых объявлений, просмотры, события
  history     «История продаж в доме» для всех домов района (стартовая история и еженедельное обновление)
  photos      фото новых объявлений (кэш картинок + хеши) для оценки состояния
  condition   оценка состояния по фото (CLIP + обучение на ремонте, указанном продавцом)
  build       расчёт аналитики и данных для дашборда (dashboard/data.json)

База: market-data/<район>.sqlite. Методика — MARKET.md.
"""
import argparse
import datetime as dt
import json
import math
import os
import re
import sqlite3
import statistics
import time

import cian_tool as ct
from cian_client import Blocked, client

SEARCH = 'https://www.cian.ru/cat.php'
PAGE_SIZE = 28
MAX_PAGES = 54  # ЦИАН не отдаёт глубже ~1500 объявлений на один запрос

SCHEMA = '''
create table if not exists listing(
  cian_id integer primary key, flat_id integer, category text, rooms text, area real, living real, kitchen real,
  floor integer, floors integer, house_key text, address text, jk text, lat real, lng real,
  metro text, metro_min integer, metro_mode text, build_year integer, material text, ceiling real,
  repair text, decoration text, is_new integer, is_owner integer, is_apart integer, has_furniture integer,
  sale_type text, seller text, agency text, created text, first_seen text, last_seen text, gone_seen text,
  card_status text, price_cuts integer, price_first integer, price_last integer, multi_id integer, minhash text, photos text,
  description text, url text);
create table if not exists obs(cian_id integer, date text, price integer, primary key(cian_id, date));
create table if not exists views(cian_id integer, date text, views integer, primary key(cian_id, date));
create table if not exists views_total(cian_id integer, date text, total integer, primary key(cian_id, date));
create table if not exists event(date text, cian_id integer, flat_id integer, kind text, detail text);
create table if not exists hist(cian_id integer primary key, house_key text, status text, start text, end text,
  exposition integer, price integer, price_sqm integer, price_diff text, rooms text, area real, floor integer, floors integer,
  title text, photo text);
create table if not exists cond(cian_id integer primary key, label text, conf real, probs text, n_photos integer, model text);
create table if not exists photo(cian_id integer, idx integer, url text, path text, hash text, primary key(cian_id, idx));
create table if not exists run(date text primary key, active integer, new integer, gone integer, returned integer,
  total_reported integer, seconds real);
'''


def db_open(path):
    os.makedirs(os.path.dirname(path) or '.', exist_ok=True)
    con = sqlite3.connect(path, timeout=120)   # фото и осмотр могут писать в базу одновременно
    con.row_factory = sqlite3.Row
    con.executescript(SCHEMA)
    cols = {r[1] for r in con.execute('pragma table_info(listing)')}
    for col, typ in (('price_cuts', 'integer'), ('year_lo', 'integer'), ('year_hi', 'integer')):
        if col not in cols:           # миграция базы, созданной до появления поля
            con.execute(f'alter table listing add column {col} {typ}')
    return con


# ---------- выдача ----------

SEARCH_API = 'https://api.cian.ru/search-offers/v2/search-offers-desktop/'
# Значения фильтров ЦИАН, сверены с repairType карточек (71/71 совпадений) и годами постройки (0 вне диапазона)
REPAIR_FILTER = {1: 'no', 2: 'cosmetic', 3: 'euro', 4: 'design'}
YEAR_FILTER = [(0, 1957), (1958, 1970), (1971, 1990), (1991, 2010), (2011, 2100)]


def api_query(district, page, extra=None):
    q = {'_type': 'flatsale', 'engine_version': {'type': 'term', 'value': 2},
         'geo': {'type': 'geo', 'value': [{'type': 'district', 'id': district}]},
         'region': {'type': 'terms', 'value': [1]}, 'page': {'type': 'term', 'value': page}}
    q.update(extra or {})
    return q


def search_page(district, page, extra=None):
    """Страница выдачи через JSON-API поиска (api.cian.ru не под антиботом www.cian.ru)."""
    for attempt in range(4):
        try:
            d = client().api_post(SEARCH_API, {'jsonQuery': api_query(district, page, extra)}).get('data') or {}
            return d.get('offersSerialized') or [], d.get('offerCount')
        except Exception:
            time.sleep(5 * (attempt + 1))
    return [], None


def crawl_ids(district, extra=None):
    """Все объявления выдачи с фильтром extra: {cianId: offer}, всего по данным ЦИАН."""
    first, total = search_page(district, 1, extra)
    found = {o['cianId']: o for o in first}
    pages = min(MAX_PAGES, math.ceil((total or 0) / PAGE_SIZE))
    for page in range(2, pages + 1):
        arr, _ = search_page(district, page, extra)
        for o in arr:
            found.setdefault(o['cianId'], o)
    return found, total


SORTS = [None, 'creation_date_desc', 'price_object_order', 'total_meters_order', 'creation_date_asc']


def crawl(district):
    """Все объявления района. Порядок выдачи плавает (продвижение), поэтому проходим с разными сортировками,
    пока не соберём всё. При >1500 объявлений режем выдачу по комнатности."""
    found, total = {}, None
    for sort in SORTS:
        extra = {'sort': {'type': 'term', 'value': sort}} if sort else None
        sub, t = crawl_ids(district, extra)
        total = total or t
        for k, v in sub.items():
            found.setdefault(k, v)
        if total and len(found) >= total:
            break
    if (total or 0) > PAGE_SIZE * MAX_PAGES:
        for rooms in ([1], [2], [3], [4, 5, 6], [9], [7]):
            sub, _ = crawl_ids(district, {'room': {'type': 'terms', 'value': rooms}})
            for k, v in sub.items():
                found.setdefault(k, v)
    return found, total


def label_by_filters(district):
    """Ремонт и эпоху дома узнаём фильтрами выдачи — без запросов к карточкам."""
    repair, year = {}, {}
    for code, name in REPAIR_FILTER.items():
        ids, _ = crawl_ids(district, {'repair': {'type': 'terms', 'value': [code]}})
        for i in ids:
            repair[i] = name
    for lo, hi in YEAR_FILTER:
        ids, _ = crawl_ids(district, {'house_year': {'type': 'range', 'value': {'gte': lo, 'lte': hi}}})
        for i in ids:
            year[i] = (lo, hi)
    return repair, year


def parse_offer(o):
    g = o.get('geo') or {}
    addr = g.get('address') or []
    street = next((a['name'] for a in addr if a.get('type') == 'street'), '')
    house = next((a['name'] for a in addr if a.get('type') == 'house'), '')
    und = (g.get('undergrounds') or [{}])[0]
    b = o.get('building') or {}
    bt = o.get('bargainTerms') or {}
    rooms = o.get('roomsCount')
    rooms = 'studio' if o.get('flatType') == 'studio' else 'free' if o.get('flatType') == 'openPlan' else str(rooms or '?')
    sim = (o.get('similar') or {}).get('url') or ''
    multi = re.search(r'multi_id=(\d+)', sim)
    agency = (o.get('user') or {}).get('agencyName')
    f = lambda v: float(v) if v not in (None, '') else None
    return dict(
        cian_id=o['cianId'], category=o.get('category'), rooms=rooms, area=f(o.get('totalArea')),
        living=f(o.get('livingArea')), kitchen=f(o.get('kitchenArea')), floor=o.get('floorNumber'),
        floors=b.get('floorsCount'), house_key=f'{street}, {house}'.strip(', '),
        address=', '.join(a['name'] for a in addr[2:]), jk=(g.get('jk') or {}).get('name'),
        lat=(g.get('coordinates') or {}).get('lat'), lng=(g.get('coordinates') or {}).get('lng'),
        metro=und.get('name'), metro_min=und.get('time'), metro_mode=und.get('transportType'),
        build_year=b.get('buildYear'), material=b.get('materialType'), decoration=o.get('decoration'),
        is_new=int(o.get('category') == 'newBuildingFlatSale'), is_owner=int(bool(o.get('isByHomeowner'))),
        is_apart=int(bool(o.get('isApartments'))), has_furniture=int(bool(o.get('hasFurniture'))),
        sale_type=bt.get('saleType'), seller=str(o.get('publishedUserId') or ''), agency=agency,
        created=o.get('creationDate'), price=bt.get('priceRur'), multi_id=int(multi.group(1)) if multi else None,
        minhash=json.dumps(o.get('descriptionMinhash') or []),
        photos=json.dumps([p.get('fullUrl') or p.get('thumbnailUrl') for p in (o.get('photos') or []) if not p.get('isLayout')]),
        description=o.get('description'), url=f'https://www.cian.ru/sale/flat/{o["cianId"]}/')


def card_details(cian_id):
    """Ремонт, год постройки, материал и т.п. есть только в карточке."""
    h = client().page(f'https://www.cian.ru/sale/flat/{cian_id}/')
    j = h.find('"offerData":')
    if j < 0:
        return {}
    od = json.JSONDecoder().raw_decode(h[j + len('"offerData":'):])[0]
    off = od.get('offer') or {}
    b = off.get('building') or {}
    ceiling = b.get('ceilingHeight')
    changes = sorted(od.get('priceChanges') or [], key=lambda c: c.get('changeTime') or '')
    return dict(repair=off.get('repairType'), build_year=b.get('buildYear'),
                material=b.get('materialType') or b.get('houseMaterialType'),
                ceiling=float(ceiling) if ceiling else None, card_status=off.get('status'),
                created_full=off.get('creationDate'),
                price_start=(changes[0].get('priceData') or {}).get('price') if changes else None,
                price_cuts=sum(1 for p, q in zip(changes, changes[1:])
                               if (q.get('priceData') or {}).get('price', 0) < (p.get('priceData') or {}).get('price', 0)))


def apply_card(con, cid, d):
    con.execute('update listing set repair=?, build_year=coalesce(?, build_year), material=coalesce(?, material), '
                'ceiling=?, card_status=?, created=coalesce(?, created), price_first=coalesce(?, price_first), '
                'price_cuts=? where cian_id=?',
                (d['repair'], d['build_year'], d['material'], d['ceiling'], d['card_status'], d['created_full'],
                 d['price_start'], d['price_cuts'], cid))


def cmd_cards(a):
    """Перечитать карточки активных объявлений (ремонт, год, история цены) — для дозаполнения."""
    con = db_open(a.db)
    ids = [r[0] for r in con.execute('select cian_id from listing where gone_seen is null' +
                                     ('' if a.all else ' and price_cuts is null'))]
    for n, cid in enumerate(ids, 1):
        d = card_details(cid)
        if d:
            apply_card(con, cid, d)
        if n % 25 == 0:
            con.commit()
            print(f'  карточки: {n}/{len(ids)}', flush=True)
    con.commit()


# ---------- один и тот же объект ----------

def minhash_sim(a, b):
    if not a or not b or len(a) != len(b):
        return 0.0
    return sum(x == y for x, y in zip(a, b)) / len(a)


def find_flat(con, row, today):
    """Ищет среди известных объявлений ту же квартиру. Возвращает (flat_id, способ) или (None, None)."""
    cands = con.execute('select cian_id, flat_id, rooms, area, floor, multi_id, minhash, house_key from listing '
                        'where cian_id != ? and (house_key = ? or multi_id = ?)',
                        (row['cian_id'], row['house_key'], row['multi_id'] or -1)).fetchall()
    mh = json.loads(row['minhash'] or '[]')
    for c in cands:
        if row['multi_id'] and c['multi_id'] == row['multi_id']:
            return c['flat_id'], 'группа дублей ЦИАН'
    for c in cands:
        if c['house_key'] == row['house_key'] and c['rooms'] == row['rooms'] and c['floor'] == row['floor'] \
                and row['area'] and c['area'] and abs(c['area'] - row['area']) <= ct.AREA_TOL:
            return c['flat_id'], 'параметры'
    for c in cands:
        if c['rooms'] == row['rooms'] and minhash_sim(mh, json.loads(c['minhash'] or '[]')) >= 0.6:
            return c['flat_id'], 'текст (minhash ЦИАН)'
    return None, None


# ---------- команды ----------

def cmd_collect(a):
    try:
        _collect(a)
    finally:
        print('запросы:', client().stats, flush=True)


def _collect(a):
    t0 = time.time()
    con = db_open(a.db)
    today = str(dt.date.today())
    found, total = crawl(a.district)
    print(f'выдача: найдено ЦИАН {total}, собрано {len(found)}', flush=True)
    if total and len(found) < 0.9 * total:
        print('ВНИМАНИЕ: собрано меньше 90% выдачи — возможно, антибот или смена разметки', flush=True)
    known = {r['cian_id']: r for r in con.execute('select * from listing')}
    new_ids = []
    for cid, o in found.items():
        p = parse_offer(o)
        price = p.pop('price')
        if cid in known:
            k = known[cid]
            if k['gone_seen']:
                con.execute('insert into event values(?,?,?,?,?)', (today, cid, k['flat_id'], 'returned',
                            f'то же объявление снова в выдаче после {k["gone_seen"]}'))
            con.execute('update listing set last_seen=?, gone_seen=null, price_last=?, multi_id=coalesce(?, multi_id), '
                        'photos=? where cian_id=?', (today, price, p['multi_id'], p['photos'], cid))
            if k['price_last'] and price and price != k['price_last']:
                con.execute('insert into event values(?,?,?,?,?)', (today, cid, k['flat_id'], 'price',
                            f'{k["price_last"]} → {price}'))
        else:
            p.update(first_seen=today, last_seen=today, price_first=price, price_last=price)
            cols = ','.join(p)
            con.execute(f'insert into listing({cols}) values({",".join("?" * len(p))})', list(p.values()))
            new_ids.append(cid)
        con.execute('insert or replace into obs values(?,?,?)', (cid, today, price))
    con.commit()
    # ремонт и эпоха дома — разметкой фильтрами выдачи (API), без запросов к карточкам
    rep, yr = label_by_filters(a.district)
    for cid, v in rep.items():
        con.execute('update listing set repair=coalesce(repair, ?) where cian_id=?', (v, cid))
    for cid, (lo, hi) in yr.items():
        con.execute('update listing set year_lo=?, year_hi=? where cian_id=?', (lo, hi, cid))
    con.commit()
    print(f'разметка фильтрами: ремонт {len(rep)}, эпоха дома {len(yr)}', flush=True)
    # карточки новых: ремонт, год постройки, материал
    for n, cid in enumerate(new_ids, 1):
        d = card_details(cid)
        if d:
            apply_card(con, cid, d)
        if n % 25 == 0:
            con.commit()
            print(f'  карточки новых: {n}/{len(new_ids)}', flush=True)
    # свой ID квартиры и события «новое / вернулось»
    returned = 0
    for cid in new_ids:
        row = con.execute('select * from listing where cian_id=?', (cid,)).fetchone()
        fid, how = find_flat(con, row, today)
        if fid is None:
            fid = cid
            con.execute('insert into event values(?,?,?,?,?)', (today, cid, fid, 'new', ''))
        else:
            gone = con.execute('select max(gone_seen) g, count(*) n, sum(gone_seen is null and last_seen=?) live '
                               'from listing where flat_id=? and cian_id!=?', (today, fid, cid)).fetchone()
            if gone['live']:
                con.execute('insert into event values(?,?,?,?,?)', (today, cid, fid, 'duplicate', how))
            else:
                returned += 1
                con.execute('insert into event values(?,?,?,?,?)', (today, cid, fid, 'returned',
                            f'{how}; снималась {gone["g"]}'))
        con.execute('update listing set flat_id=? where cian_id=?', (fid, cid))
    # ушедшие: были вчера, нет сегодня — проверяем статус карточки
    gone_n = 0
    missing = [r['cian_id'] for r in con.execute(
        'select cian_id from listing where gone_seen is null and last_seen < ?', (today,))]
    for cid in missing:
        st = ct.offer_meta(client().page(f'https://www.cian.ru/sale/flat/{cid}/')).get('status')
        if st == 'published':        # объявление живо, просто выпало из выдачи (сортировка/продвижение)
            con.execute('update listing set last_seen=?, card_status=? where cian_id=?', (today, st, cid))
            continue
        gone_n += 1
        fid = con.execute('select flat_id from listing where cian_id=?', (cid,)).fetchone()[0]
        con.execute('update listing set gone_seen=?, card_status=? where cian_id=?', (today, st, cid))
        con.execute('insert into event values(?,?,?,?,?)', (today, cid, fid, 'gone', st or ''))
    con.commit()
    # просмотры по дням (последние 10 дней) для всех активных
    active = con.execute('select cian_id, created from listing where gone_seen is null and last_seen=?',
                         (today,)).fetchall()
    for n, r in enumerate(active, 1):
        try:
            st = client().api_get(ct.STATS, {'offerId': r['cian_id'], 'offerCreationDate': (r['created'] or today)[:10]})
            total_v = re.match(r'\d+', st.get('totalViews') or '')
            if total_v:
                con.execute('insert or replace into views_total values(?,?,?)', (r['cian_id'], today, int(total_v.group())))
            for d in (st.get('daily') or {}).get('dailyViews') or []:
                con.execute('insert or replace into views values(?,?,?)', (r['cian_id'], d['date'], d['views']))
        except Exception:
            pass
        if n % 50 == 0:
            con.commit()
            print(f'  просмотры: {n}/{len(active)}', flush=True)
    con.execute('insert or replace into run values(?,?,?,?,?,?,?)',
                (today, len(active), len(new_ids), gone_n, returned, total, time.time() - t0))
    con.commit()
    print(f'итог {today}: активных {len(active)}, новых {len(new_ids)}, ушло {gone_n}, вернулось {returned}, '
          f'{time.time() - t0:.0f} с', flush=True)


def house_history(offer_id, deal):
    offers, page = [], 1
    while True:
        r = client().api_post(ct.HISTORY, {'offerId': int(offer_id), 'dealType': deal, 'page': page,
                                           'resultsOnPage': 50, 'isNearby': False})
        offers += r.get('offers', [])
        if not r.get('offers') or len(offers) >= r.get('totalCount', 0) or page >= 40:
            return offers
        page += 1


def cmd_history(a):
    """История продаж в каждом доме района — прошлые экспозиции и снятия для стартовой аналитики."""
    con = db_open(a.db)
    houses = con.execute('select house_key, min(cian_id) cid from listing where house_key != "" group by house_key').fetchall()
    print(f'домов: {len(houses)}', flush=True)
    for n, h in enumerate(houses, 1):
        try:
            offers = house_history(h['cid'], 'sale')
        except Exception as e:
            print('  ошибка', h['house_key'], e)
            continue
        for o in offers:
            x = ct.norm(o, 'sale', h['house_key'])
            psqm = re.sub(r'\D', '', o['prices'].get('priceSqm') or '')
            price = o['prices'].get('price') or ''
            pv = float(re.sub(r'[^\d,]', '', price).replace(',', '.') or 0) * (1e6 if 'млн' in price else 1e3 if 'тыс' in price else 1)
            con.execute('insert or replace into hist values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)',
                        (int(x['id']), h['house_key'], o['status'], str(x['start']), str(x['end']) if x['end'] else None,
                         x['exp'], int(pv), int(psqm) if psqm else None, o['prices'].get('priceDiff'), x['rooms'],
                         x['area'], x['floor'][0] if x['floor'] else None, x['floor'][1] if x['floor'] else None,
                         x['title'], o.get('previewPhoto')))
        con.commit()
        if n % 10 == 0:
            print(f'  {n}/{len(houses)}', flush=True)
    print('лотов в истории домов:', con.execute('select count(*) from hist').fetchone()[0])


def cmd_photos(a):
    """Скачивает ВСЕ фото объявлений, ещё не осмотренных текущей моделью (и первое фото — для дашборда).
    Картинки ужимаются до 768 px и лежат в кэше; хеши — в базе."""
    import asyncio
    import io
    from PIL import Image
    from scrapling.fetchers import AsyncStealthySession
    import market_vlm
    con = db_open(a.db)
    client()                      # замок: фото не качаем параллельно со сбором
    os.makedirs(a.cache, exist_ok=True)
    con.execute('create table if not exists inspect(cian_id integer primary key, model text, photos text, raw text, '
                'data text, condition text, age text, works text, seconds real, date text)')
    have = {(r[0], r[1]): (r[2], r[3]) for r in con.execute('select cian_id, idx, url, path from photo')}
    done = {r[0] for r in con.execute('select cian_id from inspect where model=?', (market_vlm.MODEL_NAME,))}
    todo = []
    for r in con.execute('select cian_id, photos from listing where gone_seen is null'):
        cid = r['cian_id']
        urls = json.loads(r['photos'] or '[]')[:a.per_listing]
        for i, u in enumerate(urls[:1] if cid in done else urls):
            url, path = have.get((cid, i), (None, None))
            # скачиваем, если фото нет или под этим номером раньше было другое фото (старые превью)
            if u and (url != u or (path and not os.path.exists(path))):
                todo.append((cid, i, u))
        con.execute('delete from photo where cian_id=? and idx>=?', (cid, len(urls)))
    print(f'фото к загрузке: {len(todo)} ({len({t[0] for t in todo})} объявлений)', flush=True)
    res = []

    async def run():
        async with AsyncStealthySession(headless=True, max_pages=a.tabs) as s:
            async def one(cid, i, u):
                path = f'{a.cache}/{cid}_{i}.jpg'
                for _ in range(2):
                    try:
                        r = await s.fetch(u)
                        if r.status == 200 and len(r.body) > 1000:
                            im = Image.open(io.BytesIO(r.body)).convert('RGB')
                            im.thumbnail((768, 768))
                            im.save(path, 'JPEG', quality=85)
                            res.append((cid, i, u, path, ct.dhash(path)))
                            return
                        if r.status == 404:
                            res.append((cid, i, u, None, None))
                            return
                    except Exception:
                        await asyncio.sleep(2)
            step = a.tabs * 10
            for k in range(0, len(todo), step):
                await asyncio.gather(*(one(*t) for t in todo[k:k + step]))
                con.executemany('insert or replace into photo(cian_id, idx, url, path, hash) values(?,?,?,?,?)', res)
                con.commit()
                res.clear()
                if (k // step) % 10 == 9:
                    print(f'  {min(k + step, len(todo))}/{len(todo)}', flush=True)
    asyncio.run(run())
    print('фото на диске:', sum(1 for r in con.execute('select path from photo where path is not null') if os.path.exists(r[0])),
          flush=True)


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--db', default='market-data/akademicheskiy.sqlite')
    p.add_argument('--district', type=int, default=100, help='ID района ЦИАН (100 — Академический)')
    sub = p.add_subparsers(dest='cmd', required=True)
    sub.add_parser('collect').set_defaults(fn=cmd_collect)
    sub.add_parser('history').set_defaults(fn=cmd_history)
    cards = sub.add_parser('cards')
    cards.add_argument('--all', action='store_true', help='перечитать все активные, а не только недозаполненные')
    cards.set_defaults(fn=cmd_cards)
    ph = sub.add_parser('photos')
    ph.add_argument('--cache', default='market-data/photos')
    ph.add_argument('--per-listing', type=int, default=60)
    ph.add_argument('--tabs', type=int, default=4)
    ph.set_defaults(fn=cmd_photos)
    import market_condition
    import market_build
    import market_vlm
    market_vlm.add_parser(sub)
    market_condition.add_parser(sub)
    market_build.add_parser(sub)
    a = p.parse_args()
    a.fn(a)


if __name__ == '__main__':
    main()
