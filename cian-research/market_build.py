"""Аналитика барометра: данные для дашборда (dashboard/data.json)."""
import collections
import datetime as dt
import json
import os
import statistics

import cian_tool as ct

ERA_BINS = [(0, 1957, 'сталинка (до 1957)'), (1958, 1970, 'хрущёвка (1958–1970)'),
            (1971, 1990, 'поздний СССР (1971–1990)'), (1991, 2010, '1991–2010'), (2011, 2100, '2011+')]
AREA_BINS = [(0, 35, 'до 35 м²'), (35, 50, '35–50 м²'), (50, 70, '50–70 м²'), (70, 100, '70–100 м²'), (100, 1e9, '100+ м²')]
ROOMS_RU = {'studio': 'студия', 'free': 'своб. план', '1': '1-комн', '2': '2-комн', '3': '3-комн',
            '4': '4+ комн', '5': '4+ комн', '6': '4+ комн', 'room': 'комната'}
MATERIAL_RU = {'brick': 'кирпич', 'panel': 'панель', 'monolith': 'монолит', 'monolithBrick': 'монолит-кирпич',
               'block': 'блок', 'wood': 'дерево', 'stalin': 'сталинский', 'old': 'старый фонд'}
COND_ORDER = ['без отделки', 'white box', 'требует ремонта', 'косметический', 'современный', 'дизайнерский']
NEEDS_WORK = {'без отделки', 'white box', 'требует ремонта', 'косметический'}
READY = {'современный', 'дизайнерский'}


def era(year, is_new):
    if is_new:
        return 'новостройка'
    if not year:
        return 'год неизвестен'
    return next(n for a, b, n in ERA_BINS if a <= year <= b)


def area_bin(a):
    return next((n for lo, hi, n in AREA_BINS if a and lo <= a < hi), '—')


def med(xs):
    xs = [x for x in xs if x is not None]
    return statistics.median(xs) if xs else None


def cmd_build(a):
    from market import db_open
    con = db_open(a.db)
    today = dt.date.today()
    d = lambda s: dt.date.fromisoformat(s[:10]) if s else None
    L = [dict(r) for r in con.execute('select * from listing')]
    cond = {r['cian_id']: dict(r) for r in con.execute('select * from cond')}
    views = collections.defaultdict(dict)
    for r in con.execute('select * from views'):
        views[r['cian_id']][r['date']] = r['views']
    vtot = {r['cian_id']: r['total'] for r in con.execute(
        'select cian_id, total from views_total v where date = (select max(date) from views_total where cian_id = v.cian_id)')}
    events = [dict(r) for r in con.execute('select * from event order by date desc, rowid desc limit 400')]
    returned_flats = {r['flat_id'] for r in con.execute("select flat_id from event where kind='returned'")}
    house_year = collections.defaultdict(list)
    for x in L:
        if x['build_year']:
            house_year[x['house_key']].append(x['build_year'])
    house_era = {h: era(max(set(ys), key=ys.count), False) for h, ys in house_year.items()}
    house_new = {x['house_key'] for x in L if x['is_new']}

    from market_condition import seller_label
    rows = []
    last7 = [str(today - dt.timedelta(days=i)) for i in range(1, 8)]
    for x in L:
        active = x['gone_seen'] is None
        created = d(x['created']) or d(x['first_seen'])
        end = d(x['gone_seen']) if not active else today
        days = (end - created).days if created else None
        v = views.get(x['cian_id'], {})
        v7 = sum(v.get(s, 0) for s in last7)
        seen7 = sum(1 for s in last7 if s in v)
        c = cond.get(x['cian_id'])
        sl = seller_label(x)
        price = x['price_last']
        rows.append(dict(
            id=x['cian_id'], flat=x['flat_id'], url=x['url'], active=active, gone=x['gone_seen'], card_status=x['card_status'],
            rooms=ROOMS_RU.get(x['rooms'], x['rooms']), area=x['area'], kitchen=x['kitchen'], floor=x['floor'], floors=x['floors'],
            price=price, ppsqm=round(price / x['area']) if price and x['area'] else None,
            price_first=x['price_first'], price_chg=round(100 * (price - x['price_first']) / x['price_first'], 1)
            if price and x['price_first'] else None,
            era=era(x['build_year'], x['is_new']), year=x['build_year'], material=MATERIAL_RU.get(x['material'], x['material']),
            area_bin=area_bin(x['area']), is_new=bool(x['is_new']), owner=bool(x['is_owner']), apart=bool(x['is_apart']),
            cond=c['label'] if c else None, cond_conf=round(c['conf'], 2) if c else None, seller_cond=sl,
            mismatch=bool(c and sl and c['label'] != sl and (c['label'] in READY) != (sl in READY)),
            metro=x['metro'], metro_min=x['metro_min'], metro_mode=x['metro_mode'], address=x['address'], jk=x['jk'],
            days=days, created=str(created) if created else None, views7=v7 if seen7 else None,
            views_day=round(v7 / seen7, 1) if seen7 else None, views_total=vtot.get(x['cian_id']),
            views_series=[v.get(str(today - dt.timedelta(days=i)), None) for i in range(10, 0, -1)],
            returned=x['flat_id'] in returned_flats, photo=(json.loads(x['photos'] or '[]') or [None])[0]))
    # относительный спрос: просмотры в день к медиане своего сегмента (комнаты × новостройка/вторичка)
    seg_med = {}
    for r in rows:
        if r['active'] and r['views_day'] is not None:
            seg_med.setdefault((r['rooms'], r['is_new']), []).append(r['views_day'])
    seg_med = {k: statistics.median(v) for k, v in seg_med.items()}
    for r in rows:
        m = seg_med.get((r['rooms'], r['is_new']))
        r['demand'] = round(r['views_day'] / m, 2) if (m and r['views_day'] is not None) else None

    # история домов района: снятия → вероятная продажа / возврат (методика cian_tool)
    H = [dict(r) for r in con.execute('select * from hist')]
    pools = collections.defaultdict(list)
    for h in H:
        if not h['start']:
            continue
        pools[h['house_key']].append(dict(
            id=str(h['cian_id']), deal='sale', rooms=h['rooms'], floor=(h['floor'], h['floors']) if h['floor'] else None,
            area=h['area'] or 0, start=d(h['start']), end=d(h['end']), status=h['status'], exp=h['exposition'] or 0))
    m = ct.Matcher({}, {}, ct.PHOTO_MAX, set(), {})
    hist_rows = []
    for hk, pool in pools.items():
        for x in pool:
            if x['status'] != 'deactivated' or not x['floor'] or not x['end']:
                continue
            res = ct.classify(x, pool, m, today)
            src = next(h for h in H if str(h['cian_id']) == x['id'])
            hist_rows.append(dict(id=int(x['id']), house=hk, era='новостройка' if hk in house_new else house_era.get(hk, 'год неизвестен'),
                                  rooms=ROOMS_RU.get(x['rooms'], x['rooms']), area_bin=area_bin(x['area']), start=str(x['start']),
                                  end=str(x['end']), exp=x['exp'], ppsqm=src['price_sqm'], price=src['price'],
                                  sold=res['verdict'] == 'вероятно продано', relist_days=res['event'],
                                  follow=(today - x['end']).days))
    # недельная динамика района по истории домов и ежедневным запускам
    weeks = collections.defaultdict(lambda: dict(new=0, gone=0, sold=0, ppsqm=[]))
    wk = lambda s: str(d(s) - dt.timedelta(days=d(s).weekday()))
    for h in H:
        if h['start'] and d(h['start']) >= today - dt.timedelta(days=400):
            weeks[wk(h['start'])]['new'] += 1
            if h['price_sqm']:
                weeks[wk(h['start'])]['ppsqm'].append(h['price_sqm'])
    for r in hist_rows:
        if d(r['end']) >= today - dt.timedelta(days=400):
            weeks[wk(r['end'])]['gone'] += 1
            weeks[wk(r['end'])]['sold'] += r['sold']
    timeline = [dict(week=w, new=v['new'], gone=v['gone'], sold=v['sold'], ppsqm=med(v['ppsqm']))
                for w, v in sorted(weeks.items()) if w < str(today - dt.timedelta(days=today.weekday()))]

    runs = [dict(r) for r in con.execute('select * from run order by date')]
    act = [r for r in rows if r['active']]
    sold12 = [r for r in hist_rows if r['sold'] and d(r['end']) >= today - dt.timedelta(days=365)]
    summary = dict(
        active=len(act), new_listings=sum(1 for r in act if r['created'] and d(r['created']) >= today - dt.timedelta(days=7)),
        median_ppsqm=med([r['ppsqm'] for r in act]), median_days=med([r['days'] for r in act]),
        median_views_day=med([r['views_day'] for r in act]),
        sold_12m=len(sold12), sold_per_month=round(len(sold12) / 12, 1),
        months_of_supply=round(len(act) / (len(sold12) / 12), 1) if sold12 else None,
        relist_share=round(100 * sum(1 for r in hist_rows if not r['sold']) / len(hist_rows), 1) if hist_rows else None,
        median_exposure_sold=med([r['exp'] for r in sold12]), houses=len(pools), hist_lots=len(H),
        cond_rated=sum(1 for r in act if r['cond']), mismatches=sum(1 for r in act if r['mismatch']))
    out = dict(generated=str(today), district=a.district_name, summary=summary, runs=runs, listings=rows,
               history=hist_rows, timeline=timeline, events=events, cond_order=COND_ORDER,
               era_order=[n for _, _, n in ERA_BINS] + ['новостройка', 'год неизвестен'],
               area_order=[n for _, _, n in AREA_BINS],
               model=next(iter({c['model'] for c in cond.values()}), None))
    os.makedirs(os.path.dirname(a.out), exist_ok=True)
    json.dump(out, open(a.out, 'w'), ensure_ascii=False, separators=(',', ':'))
    print(f'data.json: объявлений {len(rows)} (активных {len(act)}), история {len(hist_rows)} снятий, '
          f'недель {len(timeline)}, {os.path.getsize(a.out) // 1024} КБ')
    print('сводка:', json.dumps(summary, ensure_ascii=False))


def add_parser(sub):
    b = sub.add_parser('build', help='данные для дашборда')
    b.add_argument('--out', default='dashboard/data.json')
    b.add_argument('--district-name', default='Академический')
    b.set_defaults(fn=cmd_build)
