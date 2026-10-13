#!/usr/bin/env python3
"""Исследование «снято с публикации ≠ продано» по истории продаж ЦИАН.

Подкоманды:
  collect  <offerId> [...]   история продаж и аренды в доме каждого лота + бэкенд-статусы снятых лотов
  stats    <offerId> [...]   просмотры за 10 дней, всего просмотров, история цены
  snapshot <offerId> [...]   ежедневный мониторинг: всё из collect + полные снимки активных объявлений
  classify                   классификация снятых лотов, доли «не продано», анализ выживаемости

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


def fetch_hashes(data, items, tabs, label):
    """items: {ключ: url}. Качает картинки через браузер (ЦИАН отдаёт их только браузеру) и возвращает
    {ключ: dHash | None}; None — ЦИАН ответил 404 (фото удалено), больше не пробовать."""
    import asyncio
    from scrapling.fetchers import AsyncStealthySession
    os.makedirs(f'{data}/photos', exist_ok=True)
    out = {}
    if not items:
        return out
    print(f'{label}: {len(items)} фото (~{len(items) * 0.4 / 60:.0f} мин)', flush=True)

    async def run():
        async with AsyncStealthySession(headless=True, max_pages=tabs) as s:
            async def one(key, url):
                for _ in range(2):
                    try:
                        r = await s.fetch(url)
                        if r.status == 404:
                            out[key] = None
                            return
                        if r.status == 200 and len(r.body) > 1000:
                            path = f'{data}/photos/{re.sub(r"[^0-9A-Za-z_-]", "_", key)}.img'
                            open(path, 'wb').write(r.body)
                            out[key] = dhash(path)
                            os.remove(path)          # хранить нужно только хеш
                            return
                    except Exception:
                        await asyncio.sleep(3)
            keys = list(items.items())
            for i in range(0, len(keys), tabs * 10):
                await asyncio.gather(*(one(k, u) for k, u in keys[i:i + tabs * 10]))
                if (i // (tabs * 10)) % 5 == 4:
                    print(f'  {min(i + tabs * 10, len(keys))}/{len(keys)}', flush=True)
    asyncio.run(run())
    return out


def collect_photos(data, houses, tabs):
    """Превью всех объявлений домов — для сверки фото в истории."""
    hashes = load(f'{data}/hashes.json', {})
    todo = {str(o['id']): o['previewPhoto'].split('?')[0] for h in houses.values() for deal in ('sale', 'rent')
            for o in h[deal] if o.get('previewPhoto') and str(o['id']) not in hashes}
    hashes.update(fetch_hashes(data, todo, tabs, 'превью без хеша'))
    save(f'{data}/hashes.json', hashes)
    print(f'превью с хешем: {sum(1 for v in hashes.values() if v)}, удалены ЦИАН (404): '
          f'{sum(1 for v in hashes.values() if v is None)}', flush=True)


def cmd_collect(a):
    os.makedirs(a.data, exist_ok=True)
    houses = load(f'{a.data}/houses.json', {})
    meta = load(f'{a.data}/meta.json', {})
    for oid in a.offer_ids:
        if oid not in houses or getattr(a, 'refresh', False):
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


MAX_PHOTOS = 12   # сколько фото объявления сохранять в мониторинге


def cmd_snapshot(a):
    """Ежедневный снимок: история домов, статусы снятых, активные объявления целиком, пока ЦИАН их не почистил."""
    a.refresh = True
    cmd_collect(a)
    houses = load(f'{a.data}/houses.json', {})
    snaps = load(f'{a.data}/snapshots.json', {})
    today = str(dt.date.today())
    live = {str(o['id']): (hid, o) for hid, h in houses.items() for o in h['sale'] if o['status'] == 'published'}
    gone = back = 0
    for i, sn in snaps.items():
        if i in live:
            if sn.get('gone_seen'):
                sn.setdefault('returns', []).append([sn.pop('gone_seen'), today])
                back += 1
            sn['last_seen'] = today
            sn['price'] = live[i][1]['prices']['price']
        elif not sn.get('gone_seen'):
            sn['gone_seen'] = today          # первый снимок, где объявления уже нет
            gone += 1
    new = [i for i in live if i not in snaps]
    print(f'мониторинг: активных {len(live)}, новых {len(new)}, исчезли {gone}, вернулись {back}', flush=True)
    for n, i in enumerate(new, 1):
        hid, o = live[i]
        html = get_page(i)
        j = html.find('"offerData":')
        if j < 0:
            continue
        od = json.JSONDecoder().raw_decode(html[j + len('"offerData":'):])[0]
        off, agent = od.get('offer', {}), od.get('agent') or {}
        snaps[i] = dict(house=hid, title=o['title'], first_seen=today, last_seen=today, price=o['prices']['price'],
                        desc=off.get('description'), creationDate=off.get('creationDate'),
                        photo_urls=[p.get('thumbnailUrl') for p in (off.get('photos') or [])[:MAX_PHOTOS]],
                        photos=[], publishTerms=off.get('publishTerms'), isByHomeowner=off.get('isByHomeowner'),
                        user=str(off.get('publishedUserId') or ''), company=agent.get('companyName'),
                        acc=agent.get('accountType'))
        if n % 20 == 0:
            save(f'{a.data}/snapshots.json', snaps)
            print(f'  снимков {n}/{len(new)}', flush=True)
        time.sleep(0.8)
    save(f'{a.data}/snapshots.json', snaps)
    if a.no_photos:
        return
    # '' — не скачалось (повторим в следующий раз), None — ЦИАН удалил фото (404)
    need = lambda sn: not sn['photos'] or '' in sn['photos']
    items = {f'{i}#{k}': u for i, sn in snaps.items() if need(sn) for k, u in enumerate(sn['photo_urls'])
             if u and (not sn['photos'] or sn['photos'][k] == '')}
    got = fetch_hashes(a.data, items, a.tabs, 'фото активных объявлений')
    for i, sn in snaps.items():
        if need(sn) and sn['photo_urls']:
            old = sn['photos'] or [''] * len(sn['photo_urls'])
            sn['photos'] = [got.get(f'{i}#{k}', '') if old[k] == '' else old[k] for k in range(len(sn['photo_urls']))]
    save(f'{a.data}/snapshots.json', snaps)
    print(f'в мониторинге {len(snaps)} объявлений, с фото {sum(1 for x in snaps.values() if any(x["photos"]))}', flush=True)


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


TEXT_JACCARD = 0.5   # доля общих 5-словных фрагментов описания, начиная с которой тексты считаются одним объявлением
TEXT_MIN_WORDS = 30  # короче — описание слишком шаблонное для сравнения
SURVIVAL_DAYS = (7, 14, 30, 60, 90, 180, 365)


def shingles(text, k=5):
    words = re.findall(r'\w+', (text or '').lower())
    if len(words) < TEXT_MIN_WORDS:
        return frozenset()
    return frozenset(' '.join(words[i:i + k]) for i in range(len(words) - k + 1))


class Matcher:
    """Признаки «это та же квартира»: параметры, превью, все фото и текст из мониторинга, продавец."""

    def __init__(self, meta, hashes, thr, common, snaps):
        self.meta, self.hashes, self.thr, self.common = meta, hashes, thr, common
        self.snaps = snaps
        self.snap_hashes = {i: [int(h, 16) for h in s.get('photos', []) if h] for i, s in snaps.items()}
        self.snap_text = {i: shingles(s.get('desc')) for i, s in snaps.items()}
        # Общие фото мониторинга: снимок (±COMMON_DIST бит) встречается у объявлений двух и более разных квартир
        # (фасад, двор, лобби от застройщика). По ним квартиры не сравниваем.
        key = {i: (s.get('title') or '').split(',')[0] + '|' + ','.join((s.get('title') or '').split(',')[1:3])
               for i, s in snaps.items()}
        flat = [(i, h) for i, hs in self.snap_hashes.items() for h in hs]
        self.common_snap = set()
        for i, h in flat:
            keys = {key[j] for j, g in flat if j != i and (h ^ g).bit_count() <= COMMON_DIST and key[j] != key[i]}
            if keys:
                self.common_snap.add(h)

    def photo(self, x, y):
        hx, hy = self.hashes.get(x['id']), self.hashes.get(y['id'])
        if hx and hy and x['id'] not in self.common and y['id'] not in self.common and hamming(hx, hy) <= self.thr:
            return True
        A = [a for a in self.snap_hashes.get(x['id'], ()) if a not in self.common_snap]
        B = [b for b in self.snap_hashes.get(y['id'], ()) if b not in self.common_snap]
        if A and B:   # два снимка мониторинга: нужно ≥2 совпавших фото
            return sum(any((a ^ b).bit_count() <= self.thr for b in B) for a in A) >= 2
        for pv, other in ((hx, B), (hy, A)):   # превью из истории против всех фото мониторинга
            if pv and other and not ({x['id'], y['id']} & self.common):
                p = int(pv, 16)
                if any((p ^ b).bit_count() <= self.thr for b in other):
                    return True
        return False

    def text(self, x, y):
        A, B = self.snap_text.get(x['id']), self.snap_text.get(y['id'])
        return bool(A and B and len(A & B) / len(A | B) >= TEXT_JACCARD)

    def params(self, x, y):
        return y['floor'] == x['floor'] and abs(y['area'] - x['area']) <= AREA_TOL

    def same_flat(self, x, y):
        """Возвращает способ совпадения ('параметры'/'фото'/'текст') или None."""
        if y['id'] == x['id'] or y['rooms'] != x['rooms']:
            return None
        if self.params(x, y):
            return 'параметры'
        if self.photo(x, y):
            return 'фото'
        if self.text(x, y):
            return 'текст'
        return None

    def confirmed(self, x, y):
        A, B = self.meta.get(x['id'], {}), self.meta.get(y['id'], {})
        if A.get('user') and A.get('user') == B.get('user'):
            return True
        if A.get('company') and A.get('company') == B.get('company') and A.get('acc') != 'owner':
            return True
        return self.photo(x, y) or self.text(x, y)


def classify(x, pool, m, today):
    """Вывод по снятому лоту x: verdict, why, confirmed, method, event (дней до возврата на рынок или None)."""
    def out(verdict, why, conf=None, method=None, event=None):
        return dict(verdict=verdict, why=why, confirmed=conf, method=method, event=event)

    if m.meta.get(x['id'], {}).get('status') == 'published':
        return out('НЕ продано', 'объявление снова активно под тем же ID', True, 'ID', 0)
    rel, ovl, rnt = [], [], []
    for y in pool:
        how = m.same_flat(x, y)
        if not how:
            continue
        if y['deal'] == 'sale' and y['start'] > x['end']:
            rel.append((y, how))
        elif y['deal'] == 'sale' and (y['end'] or today) > x['end'] + dt.timedelta(days=RELIST_DAYS):
            ovl.append((y, how))
        elif y['deal'] == 'rent' and y['start'] >= x['end'] - dt.timedelta(days=7):
            rnt.append((y, how))
    rel.sort(key=lambda p: p[0]['start'])
    if rel:
        (y, how), gap = rel[0], (rel[0][0]['start'] - x['end']).days
        if gap <= RELIST_DAYS:
            return out('НЕ продано', f'перевыставлено через {gap} дн', m.confirmed(x, y), how, gap)
        if m.confirmed(x, y):
            return out('НЕ продано', f'снято и возвращено тем же продавцом через {gap} дн', True, how, gap)
        if gap <= AGENT_DAYS:
            return out('НЕ продано', f'вернулось с другим агентом через {gap} дн', False, how, gap)
    if ovl:
        y, how = ovl[0]
        return out('НЕ продано', 'снят дубль, квартира продавалась дальше', m.confirmed(x, y), how, 0)
    rnt.sort(key=lambda p: p[0]['start'])
    for y, how in rnt:
        if m.confirmed(x, y):
            return out('НЕ продано', 'тот же продавец сдаёт в аренду', True, how, max(0, (y['start'] - x['end']).days))
    if rel:
        return out('вероятно продано', 'перепродажа другим продавцом позже 90 дн')
    if rnt:
        return out('вероятно продано', 'позже сдаётся другим автором')
    return out('вероятно продано', 'следов нет')


def kaplan_meier(items):
    """items: [(время, событие:bool)]. Возвращает функцию t -> (F(t), нижняя, верхняя, под риском) для F = 1 - S."""
    items = sorted(items)
    times = sorted({t for t, e in items if e})
    n_at = len(items)
    s, var, curve, i = 1.0, 0.0, [], 0
    for t in times:
        while i < len(items) and items[i][0] < t:
            n_at -= 1
            i += 1
        d = sum(1 for tt, e in items if tt == t and e)
        if n_at > 0 and n_at > d:
            s *= 1 - d / n_at
            var += d / (n_at * (n_at - d))
        curve.append((t, s, var, n_at))

    def at(t):
        s_t, v_t, risk = 1.0, 0.0, len(items)
        for tt, ss, vv, nn in curve:
            if tt > t:
                break
            s_t, v_t, risk = ss, vv, nn
        risk = sum(1 for tt, _ in items if tt >= t)
        if s_t in (0.0, 1.0):
            return 1 - s_t, 1 - s_t, 1 - s_t, risk
        # ДИ Гринвуда на log(-log S)
        se = math.sqrt(v_t) / abs(math.log(s_t))
        lo_s, hi_s = s_t ** math.exp(1.96 * se), s_t ** math.exp(-1.96 * se)
        return 1 - s_t, 1 - hi_s, 1 - lo_s, risk
    return at


def print_survival(label, items):
    at = kaplan_meier(items)
    cells = []
    for t in SURVIVAL_DAYS:
        f, lo, hi, risk = at(t)
        if risk < 20:
            break
        cells.append(f'{t:>3} дн: {100 * f:4.1f}% ({100 * lo:.0f}–{100 * hi:.0f})')
    print(f'  {label:34s} n={len(items):4d}  ' + ' | '.join(cells))


def cmd_classify(a):
    houses = load(f'{a.data}/houses.json', {})
    meta = load(f'{a.data}/meta.json', {})
    hashes = {} if a.no_photos else load(f'{a.data}/hashes.json', {})
    snaps = {} if a.no_photos else load(f'{a.data}/snapshots.json', {})
    today = dt.date.today()
    cutoff = today - dt.timedelta(days=WINDOW_DAYS)
    pools = {hid: [norm(o, 'sale', hid) for o in h['sale']] + [norm(o, 'rent', hid) for o in h['rent']]
             for hid, h in houses.items()}
    thr, common = PHOTO_MAX, set()
    if hashes:
        thr, common, cal = calibrate_photos(pools.values(), hashes)
        print(f'Сверка фото: превью {sum(1 for v in hashes.values() if v)}, общих фото отброшено {len(common)}; '
              f'порог {thr}/256 бит (разные квартиры: {cal["pairs"]} пар, мин. {cal["min"]}, 0,1%-квантиль {cal["q001"]}, '
              f'ложных совпадений при пороге {100 * cal["false_rate"]:.2f}%)')
    else:
        print('Сверка превью выключена (нет hashes.json или --no-photos)')
    print(f'Мониторинг: объявлений со снимком {len(snaps)}, '
          f'из них снято с момента начала наблюдения {sum(1 for s in snaps.values() if s.get("gone_seen"))}\n')
    m = Matcher(meta, hashes, thr, common, snaps)
    rows = []
    for hid, pool in pools.items():
        for x in pool:
            if x['deal'] != 'sale' or x['status'] != 'deactivated' or not x['floor']:
                continue
            r = classify(x, pool, m, today)
            rows.append(dict(id=x['id'], house=hid, title=x['title'], end=str(x['end']), exp=x['exp'],
                             follow=(today - x['end']).days, status=meta.get(x['id'], {}).get('status'),
                             has_photo=bool(hashes.get(x['id'])), in_monitor=x['id'] in snaps, **r))
    win = [r for r in rows if r['follow'] >= WINDOW_DAYS]
    n = len(win)
    if not n:
        print('нет снятых лотов с окном наблюдения ≥', WINDOW_DAYS, 'дн')
        return
    k = sum(r['verdict'] == 'НЕ продано' for r in win)
    kc = sum(r['verdict'] == 'НЕ продано' and r['confirmed'] for r in win)
    lo, hi = wilson(k, n)
    print(f'Снятых лотов (снятие ≥{WINDOW_DAYS} дн назад): {n}, с превью {sum(r["has_photo"] for r in win)}, '
          f'в мониторинге {sum(r["in_monitor"] for r in win)}')
    print(f'  НЕ продано: {k} = {100 * k / n:.1f}% (95% ДИ {lo:.1f}–{hi:.1f}%)')
    print(f'  подтверждено тем же продавцом, фото или текстом: {kc} = {100 * kc / n:.1f}% (нижняя граница)')
    print('  способ совпадения:', dict(collections.Counter(r['method'] for r in win if r['method'])))
    print('\nПо бэкенд-статусу карточки:')
    for s, cnt in collections.Counter(r['status'] for r in win).most_common():
        g = [r for r in win if r['status'] == s]
        kk = sum(r['verdict'] == 'НЕ продано' for r in g)
        lo, hi = wilson(kk, len(g))
        print(f'  {str(s):12s} {STATUS_RU.get(s, "?"):24s} n={len(g):4d}  НЕ продано {100 * kk / len(g):4.1f}% (ДИ {lo:.0f}–{hi:.0f}%)')
    print('\nПричины:')
    for (v, w), cnt in collections.Counter((r['verdict'], re.sub(r'\d+ дн', 'N дн', r['why'])) for r in win).most_common():
        print(f'  {cnt:4d}  {v:17s} {w}')
    print('\nПо домам:')
    for hid in houses:
        g = [r for r in win if r['house'] == hid]
        if g:
            kk = sum(r['verdict'] == 'НЕ продано' for r in g)
            print(f'  дом лота {hid}: {kk}/{len(g)} = {100 * kk / len(g):.0f}% не продано')

    print(f'\nАнализ выживаемости (Каплан–Мейер): доля снятых квартир, вернувшихся на рынок к N-му дню.')
    print(f'Все {len(rows)} снятых лотов, каждый со своим сроком наблюдения; в скобках 95% ДИ, столбцы до ≥20 лотов под риском.')
    surv = lambda g: [(r['event'], True) if r['event'] is not None else (r['follow'], False) for r in g]
    print_survival('все снятые', surv(rows))
    for s, cnt in collections.Counter(r['status'] for r in rows).most_common():
        print_survival(f'{s} ({STATUS_RU.get(s, "?")})', surv([r for r in rows if r['status'] == s]))
    for hid in houses:
        print_survival(f'дом лота {hid}', surv([r for r in rows if r['house'] == hid]))
    save(f'{a.data}/classified.json', rows)
    print(f'\nПострочный результат: {a.data}/classified.json')


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--data', default='cian-data', help='папка для кэша и результатов')
    sub = p.add_subparsers(dest='cmd', required=True)
    for name, fn in (('collect', cmd_collect), ('snapshot', cmd_snapshot), ('stats', cmd_stats)):
        s = sub.add_parser(name)
        s.add_argument('offer_ids', nargs='+')
        s.set_defaults(fn=fn)
        if name in ('collect', 'snapshot'):
            s.add_argument('--no-photos', action='store_true', help='не качать превью для сверки фото')
            s.add_argument('--tabs', type=int, default=4, help='вкладок браузера для превью (по умолчанию 4)')
    c = sub.add_parser('classify')
    c.add_argument('--no-photos', action='store_true', help='не использовать сверку фото')
    c.set_defaults(fn=cmd_classify)
    a = p.parse_args()
    a.fn(a)


if __name__ == '__main__':
    main()
