#!/usr/bin/env python3
"""Исследование «снято с публикации ≠ продано» по истории продаж ЦИАН.

Подкоманды:
  collect  <offerId> [...]   история продаж и аренды в доме каждого лота + бэкенд-статусы снятых лотов
  stats    <offerId> [...]   просмотры за 10 дней, всего просмотров, история цены
  classify                   классификация снятых лотов и отчёт по долям «не продано»

Данные кэшируются в --data (по умолчанию ./cian-data), повторный запуск дозагружает только недостающее.
Методика описана в METHODOLOGY.md.
"""
import argparse
import collections
import datetime as dt
import json
import logging
import math
import os
import re
import time

logging.getLogger('scrapling').setLevel(logging.CRITICAL)
from scrapling.fetchers import Fetcher  # noqa: E402

API = 'https://api.cian.ru'
HISTORY = f'{API}/valuation-offer-history/v4/get-house-offer-history-desktop/'
STATS = f'{API}/offer-card/v1/get-offer-card-statistic/'
MON = {'янв': 1, 'фев': 2, 'мар': 3, 'апр': 4, 'мая': 5, 'май': 5, 'июн': 6, 'июл': 7,
       'авг': 8, 'сен': 9, 'окт': 10, 'ноя': 11, 'дек': 12}
# Бэкенд-статусы из ru.cian.offer.api.model.enums.OfferStatus (приложение ЦИАН 2.448.0)
STATUS_RU = {'published': 'в продаже', 'deactivated': 'истёк срок публикации', 'draft': 'в черновиках',
             'deleted': 'удалено', 'sold': 'продано', 'removedByModerator': 'удалено модератором',
             'blocked': 'заблокировано', 'refused': 'отклонено'}
WINDOW_DAYS = 60   # сколько дней после снятия ждём возврата квартиры на рынок
RELIST_DAYS = 14   # возврат быстрее этого — перевыставление
AGENT_DAYS = 90    # возврат с другим продавцом быстрее этого — смена агента, а не перепродажа
AREA_TOL = 0.6     # допуск по площади при поиске той же квартиры, м²


def load(path, default):
    return json.load(open(path)) if os.path.exists(path) else default


def save(path, obj):
    tmp = path + '.tmp'
    json.dump(obj, open(tmp, 'w'), ensure_ascii=False)
    os.replace(tmp, path)


def get_page(offer_id, deal='sale', tries=4):
    for attempt in range(tries):
        try:
            html = Fetcher.get(f'https://www.cian.ru/{deal}/flat/{offer_id}/', retries=1).html_content
            if '"offerData"' in html:
                return html
        except Exception:
            pass
        time.sleep(10 * (attempt + 1))
    return ''


def grab(pattern, html):
    m = re.search(pattern, html)
    return m.group(1) if m else None


def house_history(offer_id, deal):
    offers, page = [], 1
    while True:
        r = Fetcher.post(HISTORY, json={'offerId': int(offer_id), 'dealType': deal, 'page': page,
                                        'resultsOnPage': 50, 'isNearby': False}).json()
        offers += r.get('offers', [])
        if not r.get('offers') or len(offers) >= r.get('totalCount', 0) or page >= 40:
            return offers
        page += 1
        time.sleep(0.3)


def offer_meta(html):
    return dict(status=grab(r'"offerData":\{"offer":\{"category":"\w+","status":"(\w+)"', html),
                user=grab(r'"publishedUserId":(\d+)', html),
                company=grab(r'"agent":\{[^{}]*?"companyName":"([^"]*)"', html),
                acc=grab(r'"agent":\{[^{}]*?"accountType":"(\w+)"', html),
                editDate=grab(r'"editDate":"([^"]+)"', html))


def dhash(path, size=16):
    """Перцептивный хеш: 256 бит, устойчив к пережатию и изменению размера."""
    from PIL import Image
    im = Image.open(path).convert('L').resize((size + 1, size))
    px = list(im.tobytes())
    bits = 0
    for r in range(size):
        for c in range(size):
            bits = (bits << 1) | (px[r * (size + 1) + c] > px[r * (size + 1) + c + 1])
    return f'{bits:064x}'


def hamming(a, b):
    return bin(int(a, 16) ^ int(b, 16)).count('1')


def collect_photos(data, houses, tabs):
    """Превью всех объявлений домов через браузер (картинки ЦИАН отдаёт только браузеру)."""
    import asyncio
    from scrapling.fetchers import AsyncStealthySession
    os.makedirs(f'{data}/photos', exist_ok=True)
    hashes = load(f'{data}/hashes.json', {})
    todo = {str(o['id']): o['previewPhoto'].split('?')[0] for h in houses.values() for deal in ('sale', 'rent')
            for o in h[deal] if o.get('previewPhoto') and str(o['id']) not in hashes}
    print(f'превью без хеша: {len(todo)} (~{len(todo) * 0.4 / 60:.0f} мин)', flush=True)
    if not todo:
        return

    async def run():
        done = 0
        async with AsyncStealthySession(headless=True, max_pages=tabs) as s:
            async def one(oid, url):
                nonlocal done
                for _ in range(2):
                    try:
                        r = await s.fetch(url)
                        if r.status == 404:      # ЦИАН удаляет фото у снятых объявлений — больше не пробуем
                            hashes[oid] = None
                            break
                        if r.status == 200 and len(r.body) > 1000:
                            path = f'{data}/photos/{oid}.img'
                            open(path, 'wb').write(r.body)
                            hashes[oid] = dhash(path)
                            break
                    except Exception:
                        await asyncio.sleep(3)
                done += 1
                if done % 200 == 0:
                    save(f'{data}/hashes.json', hashes)
                    print(f'  {done}/{len(todo)}', flush=True)
            items = list(todo.items())
            for i in range(0, len(items), tabs * 10):
                await asyncio.gather(*(one(o, u) for o, u in items[i:i + tabs * 10]))
    asyncio.run(run())
    save(f'{data}/hashes.json', hashes)
    print(f'превью с хешем: {sum(1 for v in hashes.values() if v)}, удалены ЦИАН (404): '
          f'{sum(1 for v in hashes.values() if v is None)}', flush=True)


def cmd_collect(a):
    os.makedirs(a.data, exist_ok=True)
    houses = load(f'{a.data}/houses.json', {})
    meta = load(f'{a.data}/meta.json', {})
    for oid in a.offer_ids:
        if oid not in houses:
            houses[oid] = {'sale': house_history(oid, 'sale'), 'rent': house_history(oid, 'rent')}
            save(f'{a.data}/houses.json', houses)
        h = houses[oid]
        print(f'дом лота {oid}: продаж {len(h["sale"])}, аренды {len(h["rent"])}', flush=True)
    todo = [str(o['id']) for h in houses.values() for o in h['sale']
            if o['status'] == 'deactivated' and not meta.get(str(o['id']), {}).get('status')]
    print(f'снятых лотов без бэкенд-статуса: {len(todo)} (~{len(todo) * 2.1 / 60:.0f} мин)', flush=True)
    t0 = time.time()
    for n, oid in enumerate(todo, 1):
        meta[oid] = offer_meta(get_page(oid))
        if n % 50 == 0 or n == len(todo):
            save(f'{a.data}/meta.json', meta)
            print(f'  {n}/{len(todo)}, {(time.time() - t0) / n:.1f} с/карточка', flush=True)
        time.sleep(0.8)
    if not a.no_photos:
        collect_photos(a.data, houses, a.tabs)


def cmd_stats(a):
    for oid in a.offer_ids:
        html = get_page(oid)
        created = grab(r'"creationDate":"([^"]+)"', html)
        if not created:
            print(oid, 'карточка не получена (капча?)')
            continue
        j = html.find('"priceChanges":') + len('"priceChanges":')
        prices = sorted(json.JSONDecoder().raw_decode(html[j:])[0], key=lambda x: x['changeTime'])
        st = Fetcher.get(STATS, params={'offerId': oid, 'offerCreationDate': created[:10]}).json()
        age = (dt.date.today() - dt.date.fromisoformat(created[:10])).days or 1
        total = int(re.match(r'\d+', st['totalViews']).group())
        daily = st['daily']['dailyViews']
        d10 = sum(x['views'] for x in daily)
        p0, p1 = (prices[0]['priceData']['price'], prices[-1]['priceData']['price']) if prices else (None, None)
        print(f'{oid}: в продаже {age} дн, просмотров {total} ({total / age:.1f}/день), '
              f'за 10 дней {d10} ({d10 / 10:.1f}/день): {" ".join(str(x["views"]) for x in daily)}')
        if p0:
            print(f'   цена {p0 / 1e6:.1f} → {p1 / 1e6:.1f} млн ({100 * (p1 - p0) / p0:+.1f}%), изменений {len(prices) - 1}')


def parse_date(s):
    d, m, y = s.split()
    return dt.date(int(y), MON[m[:3]], int(d))


def norm(o, deal, house):
    t = o['title'].replace('\xa0', ' ')
    floor = re.search(r'(\d+)/(\d+) этаж', t)
    rooms = 'studio' if 'студ' in t.lower() else 'room' if 'комната' in t else (re.search(r'(\d+)-комн', t) or [0, '?'])[1]
    start = parse_date(o['dateStart'])
    end = None
    if o['status'] == 'deactivated':
        end = parse_date(o['dateEnd']) if o.get('dateEnd') else \
            start + dt.timedelta(days=int(re.match(r'\d+', o['exposition']).group()) - 1)
    return dict(id=str(o['id']), deal=deal, house=house, rooms=rooms,
                floor=(int(floor[1]), int(floor[2])) if floor else None,
                area=float(re.match(r'([\d,]+)', t).group(1).replace(',', '.')),
                start=start, end=end, status=o['status'], title=t,
                exp=int(re.match(r'\d+', o['exposition']).group()))


def wilson(k, n, z=1.96):
    if not n:
        return 0, 0
    p = k / n
    den = 1 + z * z / n
    c = (p + z * z / (2 * n)) / den
    w = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / den
    return 100 * max(0, c - w), 100 * min(1, c + w)


COMMON_DIST = 10   # фото ближе этого считаются одним и тем же снимком при поиске общих фото
PHOTO_MAX = 40     # верхняя граница порога совпадения фото (из 256 бит)


def flat_key(o):
    return o['rooms'], o['floor'], round(o['area'])


def calibrate_photos(pools, hashes):
    """Находит «общие» фото (фасад, рендер, логотип) и подбирает порог по заведомо разным квартирам."""
    common, neg = set(), []
    for pool in pools:
        items = [(o, int(hashes[o['id']], 16)) for o in pool if hashes.get(o['id'])]
        for i, (o, h) in enumerate(items):
            keys = {flat_key(o)}
            for p, g in items:
                if p is not o and (h ^ g).bit_count() <= COMMON_DIST:
                    keys.add(flat_key(p))
            if len(keys) >= 3:          # один снимок у трёх и более разных квартир — общее фото
                common.add(o['id'])
        clean = [(o, h) for o, h in items if o['id'] not in common]
        for i, (o, h) in enumerate(clean):
            for p, g in clean[i + 1:i + 40]:
                if o['rooms'] != p['rooms'] and 'room' not in (o['rooms'], p['rooms']):
                    neg.append((h ^ g).bit_count())
    neg.sort()
    q = neg[len(neg) // 1000] if neg else PHOTO_MAX + 10   # 0,1%-квантиль разных квартир
    thr = max(8, min(PHOTO_MAX, q - 10))
    fp = sum(d <= thr for d in neg) / len(neg) if neg else 0
    return thr, common, dict(pairs=len(neg), min=neg[0] if neg else None, q001=q, false_rate=fp)


def classify(x, pool, meta, today, hashes=None, thr=0, common=frozenset()):
    """Возвращает (вывод, причина, подтверждено, найдено_по_фото) для снятого лота x."""
    hashes = hashes or {}

    def same_photo(y):
        hx, hy = hashes.get(x['id']), hashes.get(y['id'])
        return bool(hx and hy and x['id'] not in common and y['id'] not in common and hamming(hx, hy) <= thr)

    def same_flat(y):
        if y['id'] == x['id'] or y['rooms'] != x['rooms']:
            return False
        return (y['floor'] == x['floor'] and abs(y['area'] - x['area']) <= AREA_TOL) or same_photo(y)

    def by_photo_only(y):
        return not (y['floor'] == x['floor'] and abs(y['area'] - x['area']) <= AREA_TOL)

    def same_seller(y):
        A, B = meta.get(x['id'], {}), meta.get(y['id'], {})
        if A.get('user') and A.get('user') == B.get('user'):
            return True
        if A.get('company') and A.get('company') == B.get('company') and A.get('acc') != 'owner':
            return True
        return same_photo(y)

    if meta.get(x['id'], {}).get('status') == 'published':
        return 'НЕ продано', 'объявление снова активно под тем же ID', True, False
    rel, ovl, rnt = [], [], []
    for y in pool:
        if not same_flat(y):
            continue
        if y['deal'] == 'sale' and y['start'] > x['end']:
            rel.append(y)
        elif y['deal'] == 'sale' and (y['end'] or today) > x['end'] + dt.timedelta(days=RELIST_DAYS):
            ovl.append(y)
        elif y['deal'] == 'rent' and y['start'] >= x['end'] - dt.timedelta(days=7):
            rnt.append(y)
    rel.sort(key=lambda y: y['start'])
    if rel:
        y, gap = rel[0], (rel[0]['start'] - x['end']).days
        if gap <= RELIST_DAYS:
            return 'НЕ продано', f'перевыставлено через {gap} дн', same_seller(y), by_photo_only(y)
        if same_seller(y):
            return 'НЕ продано', f'снято и возвращено тем же продавцом через {gap} дн', True, by_photo_only(y)
        if gap <= AGENT_DAYS:
            return 'НЕ продано', f'вернулось с другим агентом через {gap} дн', False, by_photo_only(y)
    if ovl:
        return 'НЕ продано', 'снят дубль, квартира продавалась дальше', same_seller(ovl[0]), by_photo_only(ovl[0])
    if rnt and same_seller(rnt[0]):
        return 'НЕ продано', 'тот же продавец сдаёт в аренду', True, by_photo_only(rnt[0])
    if rel:
        return 'вероятно продано', 'перепродажа другим продавцом позже 90 дн', None, False
    if rnt:
        return 'вероятно продано', 'позже сдаётся другим автором', None, False
    return 'вероятно продано', 'следов нет', None, False


def cmd_classify(a):
    houses = load(f'{a.data}/houses.json', {})
    meta = load(f'{a.data}/meta.json', {})
    hashes = {} if a.no_photos else load(f'{a.data}/hashes.json', {})
    today = dt.date.today()
    cutoff = today - dt.timedelta(days=WINDOW_DAYS)
    pools = {hid: [norm(o, 'sale', hid) for o in h['sale']] + [norm(o, 'rent', hid) for o in h['rent']]
             for hid, h in houses.items()}
    thr, common = 0, set()
    if hashes:
        thr, common, cal = calibrate_photos(pools.values(), hashes)
        print(f'Сверка фото: превью {sum(1 for v in hashes.values() if v)}, общих фото отброшено {len(common)}; '
              f'порог {thr}/256 бит (разные квартиры: {cal["pairs"]} пар, мин. {cal["min"]}, 0,1%-квантиль {cal["q001"]}, '
              f'ложных совпадений при пороге {100 * cal["false_rate"]:.2f}%)\n')
    else:
        print('Сверка фото выключена (нет cian-data/hashes.json или --no-photos)\n')
    rows = []
    for hid, pool in pools.items():
        for x in pool:
            if x['deal'] != 'sale' or x['status'] != 'deactivated' or not x['floor'] or x['end'] > cutoff:
                continue
            verdict, why, conf, photo_only = classify(x, pool, meta, today, hashes, thr, common)
            rows.append(dict(id=x['id'], house=hid, title=x['title'], end=str(x['end']), exp=x['exp'],
                             status=meta.get(x['id'], {}).get('status'), verdict=verdict, why=why,
                             confirmed=conf, photo_only=photo_only, has_photo=bool(hashes.get(x['id']))))
    n = len(rows)
    if not n:
        print('нет снятых лотов с окном наблюдения ≥', WINDOW_DAYS, 'дн')
        return
    k = sum(r['verdict'] == 'НЕ продано' for r in rows)
    kc = sum(r['verdict'] == 'НЕ продано' and r['confirmed'] for r in rows)
    kp = sum(r['verdict'] == 'НЕ продано' and r['photo_only'] for r in rows)
    lo, hi = wilson(k, n)
    print(f'Снятых лотов (снятие ≥{WINDOW_DAYS} дн назад): {n}, из них с превью {sum(r["has_photo"] for r in rows)}')
    print(f'  НЕ продано: {k} = {100 * k / n:.1f}% (95% ДИ {lo:.1f}–{hi:.1f}%)')
    print(f'  подтверждено тем же продавцом или фото: {kc} = {100 * kc / n:.1f}% (нижняя граница)')
    if hashes:
        print(f'  найдено только по фото (площадь/этаж в объявлении изменили): {kp}')
    print('\nПо бэкенд-статусу карточки:')
    for s, cnt in collections.Counter(r['status'] for r in rows).most_common():
        g = [r for r in rows if r['status'] == s]
        kk = sum(r['verdict'] == 'НЕ продано' for r in g)
        lo, hi = wilson(kk, len(g))
        print(f'  {str(s):12s} {STATUS_RU.get(s, "?"):24s} n={len(g):4d}  НЕ продано {100 * kk / len(g):4.1f}% (ДИ {lo:.0f}–{hi:.0f}%)')
    print('\nПричины:')
    for (v, w), cnt in collections.Counter((r['verdict'], re.sub(r'\d+ дн', 'N дн', r['why'])) for r in rows).most_common():
        print(f'  {cnt:4d}  {v:17s} {w}')
    print('\nПо домам:')
    for hid in houses:
        g = [r for r in rows if r['house'] == hid]
        if g:
            kk = sum(r['verdict'] == 'НЕ продано' for r in g)
            print(f'  дом лота {hid}: {kk}/{len(g)} = {100 * kk / len(g):.0f}% не продано')
    save(f'{a.data}/classified.json', rows)
    print(f'\nПострочный результат: {a.data}/classified.json')


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--data', default='cian-data', help='папка для кэша и результатов')
    sub = p.add_subparsers(dest='cmd', required=True)
    for name, fn in (('collect', cmd_collect), ('stats', cmd_stats)):
        s = sub.add_parser(name)
        s.add_argument('offer_ids', nargs='+')
        s.set_defaults(fn=fn)
        if name == 'collect':
            s.add_argument('--no-photos', action='store_true', help='не качать превью для сверки фото')
            s.add_argument('--tabs', type=int, default=4, help='вкладок браузера для превью (по умолчанию 4)')
    c = sub.add_parser('classify')
    c.add_argument('--no-photos', action='store_true', help='не использовать сверку фото')
    c.set_defaults(fn=cmd_classify)
    a = p.parse_args()
    a.fn(a)


if __name__ == '__main__':
    main()
