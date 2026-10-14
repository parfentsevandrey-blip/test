#!/usr/bin/env python3
"""Короткий PDF-отчёт простым языком: справедливая цена продажи трёх офисных объектов
(данные: valuation.json, bc_profiles.json) → reports/Оценка_офисов_ЯРД_Калейдоскоп_ImaginePlaza.pdf"""
import hashlib
import html
import io
import json
import os
import urllib.request

from PIL import Image

import bc_profiles as bp

V = json.load(open('commercial-data/valuation.json'))
ROWS = {r['id']: r for r in json.load(open('commercial-data/offers.json'))}
PROF = json.load(open('commercial-data/bc_profiles.json'))
RAT = json.load(open('commercial-data/bc_ratings.json'))
PAGES = json.load(open('commercial-data/bc_pages.json'))
PH = os.path.abspath('commercial-data/photos')
BPH = os.path.abspath('commercial-data/bcphotos')
SUBJ = os.path.abspath('commercial-data/subj')
DATE = '14.10.2026'
REP = {'yard': ['y1', 'y5'], 'kaleidoscope': ['k1', 'k2'], 'imagine': ['i2', 'i4']}
GROUP = {'K1': 'БЦ класса B', 'K2': 'БЦ класса A', 'K3': 'Админздания, особняки', 'K4': '1 этажи жилых домов'}
ADJ_RU = {'loc': 'центр', 'metro': 'метро', 'bld': 'здание', 'fl': 'этаж', 'size': 'площадь', 'fit': 'ремонт'}
SC = V['scenarios']
MAINP = SC[V['main']]
KS = (1 - MAINP['bargain']) / V['params']['bargain']      # пересчёт цен аналогов под торг базового сценария
KR = 1 - MAINP['rent_disc']                                # ставки аренды со скидкой базового сценария
BG = int(MAINP['bargain'] * 100)
HDR = {'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36',
       'Referer': 'https://www.cian.ru/'}


def n(v, d=0):
    if v is None:
        return '—'
    return f'{v:,.{d}f}'.replace(',', ' ').replace('.', ',')


def mln(v):
    return n(v / 1e6, 1) + ' млн ₽'


def k(v):
    return n(v / 1000) if v else '—'


def r1(v):
    return n(v, 2).rstrip('0').rstrip(',') if v is not None else '—'


def e(s):
    return html.escape(str(s or ''))


def addr(s):
    for x in ('Россия, ', 'Москва, ', 'г Москва, ', 'Москва город, ', 'г. Москва, '):
        s = (s or '').replace(x, '')
    return s


def fetch_img(u, path, size=520):
    if os.path.exists(path):
        return True
    try:
        data = urllib.request.urlopen(urllib.request.Request(u, headers=HDR), timeout=30).read()
        im = Image.open(io.BytesIO(data)).convert('RGB')
        im.thumbnail((size, size))
        im.save(path, 'JPEG', quality=80)
        return True
    except Exception:
        return False


def photo(cid):
    p = f'{PH}/{cid}_0.jpg'
    if not os.path.exists(p):
        for u in ROWS[cid]['photos'][:2]:
            if fetch_img(u, p):
                break
    return f'file://{p}' if os.path.exists(p) else None


def bc_photo(b):
    key = hashlib.md5((b.get('url') or b.get('name') or '').encode()).hexdigest()[:12]
    p = f'{BPH}/bc_{key}.jpg'
    if not os.path.exists(p):
        for u in (b.get('photos') or [])[:2]:
            if fetch_img(u, p, 360):
                break
    if not os.path.exists(p):
        for i in b['sale'] + b['rent']:
            if ROWS[i]['photos'] and fetch_img(ROWS[i]['photos'][0], p, 360):
                break
    return f'file://{p}' if os.path.exists(p) else None


def U(sk, key):
    return next(u for u in V['subjects'][sk]['units_val'] if u['key'] == key)


def TOT(sk):
    return sum(u['total'] for u in V['subjects'][sk]['units_val'])


def TOTS(sk, key):
    return sum(u['scen'][key]['total'] for u in V['subjects'][sk]['units_val'])


def AREA(sk):
    return sum(u['area'] for u in V['subjects'][sk]['units_val'])


def cv(sk, uk, key, f='value'):
    return (V['subjects'][sk]['comps'][uk].get(key) or {}).get(f)


def med(ids, f):
    ex = {int(x) for x in V['exclude']}
    v = sorted(ROWS[i][f] for i in ids if i not in ex and not ROWS[i].get('offplan') and not ROWS[i].get('nonmarket')
               and (4000 <= (ROWS[i].get(f) or 0) <= 150000 if f == 'net' else 50000 <= (ROWS[i].get(f) or 0) <= 1500000))
    return v[len(v) // 2] if v else None


def dist(sk, b):
    return bp.km(bp.SUBJ[sk], (b['lat'], b['lng']))


# ---------------- тексты: коротко и простыми словами ----------------
TEXT = {
    'yard': dict(
        where='Хорошёвский район (САО), у ТТК, 2 км до Москва-Сити. Ближе всего станция МЦД-4 «Ермакова Роща» — 0,5 км (~9 мин); '
              'метро Шелепиха — ~17 мин пешком.',
        cian='В продаже нет. 1 этаж сдаётся в аренду (два агента, разные ставки), на 5 этаже сдаются части этажа. '
             'В соседнем корпусе комплекса продаётся офис 165 м² за 45 млн ₽.',
        pros=['У ТТК и Сити, рядом МЦД-4', 'Ухоженное кирпичное здание: ресепшен, кафе, банк, парковка',
              'Аренда в здании идёт — 6 объявлений', 'Цельный верхний этаж 680 м²',
              'Офисов класса B на продажу рядом мало — конкурентов у продавца немного'],
        cons=['По данным Циана кондиционеры — сплит-системы, а не центральная система (в презентации — центральная; проверить)',
              'До метро ~17 мин пешком', 'В 300 м более сильный БЦ «Магистраль Плаза»',
              '1 этаж отделан под банк — под обычный офис нужна переделка', 'Лот 680 м² дорогой — покупателей мало']),
    'kaleidoscope': dict(
        where='Нагатино-Садовники (ЮАО), между Варшавским и Каширским шоссе. Метро Нагорная — ~23 мин пешком '
              '(в презентации «10–15 мин»).',
        cian='Ни в продаже, ни в аренде нет. Страницы этого БЦ на Циане тоже нет.',
        pros=['Отдельный вход с улицы на 1 этаже', 'Большой БЦ: охрана, кафе, парковка, лифты OTIS',
              'Этажи можно продать по отдельности', 'Самый дешёвый способ купить офис в БЦ в этом районе'],
        cons=['Низкие потолки: 2,57 м на 1 этаже и 2,37 м на антресоли', 'До метро 23–27 мин пешком',
              'Здание 1995 г., фасад устарел (2,75 из 5 по фото — ниже соседей)', 'В 300–500 м более современный Сириус Парк',
              'Аренда в районе низкая — инвестору неинтересно']),
    'imagine': dict(
        where='Нагатинская улица (ЮАО). Метро Нагатинская — ~21 мин пешком, трамвай у входа, рядом ТЦ «Конфетти».',
        cian='Наши блоки не выставлены. В этом же здании продаётся блок 187 м² на 2 этаже за 37,4 млн ₽ '
             '(200 тыс. ₽/м² с НДС) — главный ориентир цены.',
        pros=['Ремонт во всех блоках, спринклеры, 4 лифта', 'Удобные площади 168–524 м²',
              '1 этаж — отдельные входы и витрины', 'Высокие потолки ~4 м', 'Трамвай у входа, ТЦ рядом, парковка'],
        cons=['До метро ~21 мин пешком', 'Бывший завод, вход со двора', 'Соседний блок в здании дешевле — выше его цены не продать',
              '8 блоков сразу давят на цену — продавать по очереди',
              'Площадь БЦ в презентации расходится: 4 000 и 12 000 м² (Циан — 4 992 м²)']),
}
SUBJ_IMG = {'yard': ['yard-2.jpg', 'yard-7.jpg', 'yard-9.jpg'], 'kaleidoscope': ['kal-photo.jpg', 'kal-2.jpg', 'kal-3.jpg'],
            'imagine': ['img-1.jpg', 'img-4.jpg', 'img-7.jpg']}


def why(sk):
    S = V['subjects'][sk]
    a, b = REP[sk]
    ua, ub = U(sk, a), U(sk, b)
    sa = S['comps'][a]['sale_K1']
    rd = int(MAINP['rent_disc'] * 100)
    if sk == 'yard':
        return [
            f"Похожие офисы в БЦ класса B рядом после торга {BG}% и пересчёта под наш офис — ≈<b>{k(ua['comp'])} тыс. ₽/м²</b>. "
            f"Но их мало: {sa['n']} лотов в {sa['n_bc'] + sa['n_same']} зданиях.",
            f"Если сдавать: рынок ≈{k(ua['rent'])} тыс. ₽/м² в год чистыми (это уже на {rd}% ниже ставок в объявлениях). "
            f"Инвестор при доходности {n(ua['cap_rate'] * 100, 1)}% заплатит ≈<b>{k(ua['income'])} тыс.</b>",
            f"Продаж мало, поэтому аренда весит больше: 40% «для себя» + 60% «инвестор» → <b>{k(ua['per_m2'])} тыс.</b> за 1 этаж "
            f"и <b>{k(ub['per_m2'])} тыс.</b> за 5 этаж.",
            f"Для сравнения: класс A рядом ≈{k(cv(sk, b, 'sale_K2') * KS)} тыс., старое админздание на Хорошёвском ш. — 145–175 тыс. ЯРД между ними."]
    if sk == 'kaleidoscope':
        return [
            f"Похожие офисы в БЦ класса B рядом ({sa['n']} лотов в {sa['n_bc']} зданиях) после торга {BG}% и пересчёта: "
            f"≈<b>{k(ua['comp'])} тыс. ₽/м²</b> для 1 этажа и ≈{k(ub['comp'])} — для антресоли.",
            f"Если сдавать: ≈{k(ua['rent'])} и {k(ub['rent'])} тыс. ₽/м² в год (на {rd}% ниже объявлений). Инвестор хочет "
            f"{n(ua['cap_rate'] * 100, 1)}% — здание старое — и заплатит ≈<b>{k(ua['income'])}</b> и {k(ub['income'])} тыс.",
            f"Середина минус скидка за низкий потолок (−3% и −7%) → <b>{k(ua['per_m2'])} тыс.</b> за 1 этаж и <b>{k(ub['per_m2'])} тыс.</b> за антресоль.",
            f"Для сравнения: класс A рядом (DM Tower) ≈{k(cv(sk, a, 'sale_K2') * KS)} тыс., админздания ≈{k(cv(sk, a, 'sale_K3') * KS)} тыс."]
    up = [u['per_m2'] for u in S['units_val'] if u['floor'] > 1]
    g = [u['per_m2'] for u in S['units_val'] if u['floor'] == 1]
    return [
        f"Ориентир — соседний блок в этом же здании: 164 тыс. ₽/м² без НДС в объявлении, при торге {BG}% сделка ≈{k(163934 * (1 - MAINP['bargain']))} тыс. "
        f"Наш блок не может быть заметно дороже (не больше +3%).",
        f"Офисы в БЦ класса B вокруг после торга и пересчёта — ≈{k(ub['comp'])} тыс. для 2 этажа; аренда ≈{k(ub['rent'])} тыс. в год → "
        f"инвестор при {n(ub['cap_rate'] * 100, 1)}% даст ≈{k(ub['income'])} тыс.",
        f"Итог: 1 этаж с отдельным входом — <b>{k(min(g))}–{k(max(g))} тыс.</b>, 2–3 этажи — <b>{k(min(up))}–{k(max(up))} тыс. ₽/м²</b> "
        f"(балкон +3%, большой блок дешевле за метр).",
        f"Небольшие офисы сейчас продаются лучше крупных — продавать по 2–3 блока, начиная с 1 этажа. Все 8 одним лотом — "
        f"на 10–15% дешевле: {n(TOT(sk) * .85 / 1e6, 0)}–{n(TOT(sk) * .90 / 1e6, 0)} млн ₽."]


def units_table(sk):
    S = V['subjects'][sk]
    rows = ''.join(f"<tr><td>{e(u['name'])}</td><td class=r>{n(u['area'], 1)}</td><td class=r><b>{k(u['per_m2'])}</b></td>"
                   f"<td class=r><b>{mln(u['total'])}</b></td><td class=r>{mln(u['scen']['stress']['total'])}</td>"
                   f"<td class=r>{mln(u['scen']['before']['total'])}</td><td class=r>{mln(u['list_price'])}</td></tr>" for u in S['units_val'])
    rows += (f"<tr class=tot><td>Итого</td><td class=r>{n(AREA(sk), 1)}</td><td class=r>{k(TOT(sk) / AREA(sk))}</td><td class=r>{mln(TOT(sk))}</td>"
             f"<td class=r>{mln(TOTS(sk, 'stress'))}</td><td class=r>{mln(TOTS(sk, 'before'))}</td><td class=r>{mln(TOT(sk) / (1 - MAINP['bargain']))}</td></tr>")
    return (f"<table class=t><tr><th>Помещение</th><th class=r>м²</th><th class=r>тыс. ₽/м²</th><th class=r>Ожидаемая сделка</th>"
            f"<th class=r>Если спад углубится</th><th class=r>Без спада</th><th class=r>Выставлять по</th></tr>{rows}</table>")


def groups_table(sk):
    S = V['subjects'][sk]
    a = REP[sk][0]
    c = S['clusters']
    mean = {'K1': 'главное сравнение', 'K2': 'потолок: «если доплатить за класс A»', 'K3': 'вариант «попроще», без сервиса БЦ',
            'K4': 'альтернатива только для 1 этажа с входом с улицы'}
    rows = ''
    for cl in ('K1', 'K2', 'K3', 'K4'):
        sale, rent = c[cl]['sale'], c[cl]['rent']
        vals = ' / '.join(k(cv(sk, uk, f'sale_{cl}') * KS) for uk in REP[sk] if cv(sk, uk, f'sale_{cl}'))
        note = mean[cl]
        if cl == 'K4' and not cv(sk, a, 'sale_K4'):
            note = 'к нашему объекту не подходит (вход через холл БЦ)'
        rows += (f"<tr{' class=main' if cl == 'K1' else ''}><td><b>{GROUP[cl]}</b></td>"
                 f"<td class=r>{k(sale['med']) if sale else '—'}<span class=m> · {sale['n'] if sale else 0} лотов</span></td>"
                 f"<td class=r>{k(rent['med']) if rent else '—'}<span class=m> · {rent['n'] if rent else 0}</span></td>"
                 f"<td class=r><b>{vals or '—'}</b></td><td>{note}</td></tr>")
    names = ' / '.join(e(U(sk, uk)['name']) for uk in REP[sk])
    return (f"<table class=t><tr><th>Группа конкурентов</th><th class=r>Продают (объявления), тыс. ₽/м²</th><th class=r>Сдают, тыс. ₽/м²/год</th>"
            f"<th class=r>В пересчёте на наш офис<br><span class=m>{names}</span></th><th>Что это значит</th></tr>{rows}</table>"
            f"<p class=note>Продают и сдают — медианы объявлений у тех же и соседних станций метро (без НДС; аренда — без эксплуатации и коммуналки). "
            f"«В пересчёте» — те же офисы после торга {BG}% (рынок покупателя) и поправок под наше помещение.</p>")


def bc_table(sk, lim1=10, lim2=3):
    live = [b for b in PROF[sk]['bcs'] if (b['sale'] or len(b['rent']) >= 2) and not b['offplan']]
    sel = (sorted([b for b in live if b['cluster'] == 'K1'], key=lambda b: dist(sk, b))[:lim1] +
           sorted([b for b in live if b['cluster'] == 'K2'], key=lambda b: dist(sk, b))[:lim2])
    own_url = V['subjects'][sk].get('url')
    rows = ''
    for b in sel:
        img = bc_photo(b)
        rt = (RAT.get(b['url'] or '') or {}).get('rating')
        own = b['url'] and b['url'] == own_url
        title = (b['title'] or b['name'] or '').replace('Бизнес-центр ', '').replace('Бизнес-квартал ', '').replace('Деловой квартал ', '')
        rows += (f"<tr{' class=same' if own else ''}><td>{f'<img class=th src={img}>' if img else ''}</td>"
                 f"<td><b>{e(title)[:40]}</b>{' — <b>наш</b>' if own else ''} <span class=m>{e(addr(b['address']))[:34]}</span></td>"
                 f"<td>{e(b['cls'] or '—')}</td><td class=r>{b['year'] or '—'}</td><td class=r><b>{r1(rt) if rt else '—'}</b></td>"
                 f"<td class=r>{n(dist(sk, b), 1)}</td><td>{e(b['metro'])} {b['walk']}′</td>"
                 f"<td class=r>{len(b['sale'])} / {len(b['rent'])}</td><td class=r>{k(med(b['sale'], 'ex_vat'))}</td><td class=r>{k(med(b['rent'], 'net'))}</td></tr>")
    return (f"<table class='t bct'><tr><th></th><th>Бизнес-центр</th><th>Класс</th><th class=r>Год</th><th class=r>Оценка по фото</th>"
            f"<th class=r>От нас, км</th><th>Метро пешком</th><th class=r>Продают / сдают</th><th class=r>Продажа, тыс./м²</th>"
            f"<th class=r>Аренда, тыс./м²/год</th></tr>{rows}</table>"
            f"<p class=note>{lim1} ближайших БЦ класса B и {lim2} класса A из {len(live)} с объявлениями у тех же и соседних станций. "
            f"Оценка по фото: 5 — новая башня, 4 — современный БЦ или хороший лофт, 3 — обычный класс B, 2 — старое здание без ремонта.</p>")


def pick(comps, lim):
    out, seen = [], set()
    for c in sorted([c for c in comps if c.get('used')], key=lambda c: (-c['same'], -c['weight'])):
        g = c['bc_url'] or addr(c['address'])[:30]
        if g in seen:
            continue
        seen.add(g)
        out.append(c)
        if len(out) >= lim:
            break
    return out


def card(c, label=''):
    img = photo(c['id'])
    title = c.get('bc') or addr(c['address'])[:34]
    return (f"<div class=card>{f'<img src={img}>' if img else '<div class=noimg>нет фото</div>'}<div class=cb>"
            f"{f'<span class=tag>{label}</span> ' if label else ''}<b>{e(title)[:36]}</b>"
            f"<div class=m>{n(c['area'], 0)} м² · {c['floor']} эт. · {e(c['station'])} {c['walk']}′ · здание {r1(c['rating'])}</div>"
            f"<div>{mln(c['price'])} · {k(c['ex_vat'])} тыс./м² без НДС</div>"
            f"<div>→ для нас <b>{k(c['adjusted'] * KS)} тыс.</b> <a href=\"{c['url'].split('?')[0]}\">циан</a></div></div></div>")


def cards(sk):
    a = REP[sk][0]
    res = V['subjects'][sk]['comps'][a]
    main = ''.join(card(c) for c in pick(res['sale_K1']['comps'], 6))
    other = ''
    for cl in ('K2', 'K3', 'K4'):
        r = res.get(f'sale_{cl}')
        if r and r['n']:
            other += ''.join(card(c, GROUP[cl]) for c in pick(r['comps'], 1))
    return main, other


def object_section(sk):
    S = V['subjects'][sk]
    T = TEXT[sk]
    imgs = ''.join(f'<img src="file://{SUBJ}/{f}">' for f in SUBJ_IMG[sk])
    units = '; '.join(f"{e(u['name'])} — {n(u['area'], 1)} м²" for u in S['units'])
    main, other = cards(sk)
    a = REP[sk][0]
    return f'''
<section class=obj><h2>{e(S['title'])} — {mln(TOT(sk))}</h2><div class=sub>{e(S['address'])}</div>
<div class=gal>{imgs}</div>
<div class=two><div><p><b>Что продаём:</b> {units}.</p><p><b>Где:</b> {T['where']}</p>
<p><b>Здание:</b> оценка по фото <b>{r1(S['rating'])} из 5</b>; до центра {n(S['center_km'], 1)} км.</p><p><b>На Циане:</b> {T['cian']}</p></div>
<div><div class=pros><b>Плюсы</b><ul>{''.join(f'<li>{e(x)}</li>' for x in T['pros'])}</ul></div>
<div class=cons><b>Минусы</b><ul>{''.join(f'<li>{e(x)}</li>' for x in T['cons'])}</ul></div></div></div>
<h3>Цена</h3>{units_table(sk)}
<div class=box><b>Почему такая цена</b><ul>{''.join(f'<li>{x}</li>' for x in why(sk))}</ul></div>
<h3>Конкуренты: группы</h3>{groups_table(sk)}
<h3>Бизнес-центры рядом</h3>{bc_table(sk)}
<h3>Главные аналоги — офисы в БЦ класса B ({e(U(sk, a)['name'])})</h3><div class=cards>{main}</div>
<h3>Для сравнения — другие группы</h3><div class=cards>{other}</div>
<p class=note>«Для нас» — цена аналога после торга {BG}% и поправок под наше помещение (центр, метро, здание, этаж, площадь, ремонт).
Подробный расчёт — в приложении.</p></section>'''


# ---------------- приложение ----------------
def grid(sk, uk, deal, lim):
    g = V['subjects'][sk]['comps'][uk][f'{deal}_K1']
    f = KS if deal == 'sale' else KR
    shown, per = [], {}
    for c in sorted([c for c in g['comps'] if c.get('used')], key=lambda c: (-c['same'], -c['weight'])):
        key = c['bc_url'] or addr(c['address'])[:30]
        if not c['same'] and per.get(key, 0) >= 1:
            continue
        per[key] = per.get(key, 0) + 1
        shown.append(c)
    rows = ''
    for c in shown[:lim]:
        adj = ', '.join(f"{ADJ_RU[a]} {'+' if v > 0 else ''}{n(v, 0)}%" for a, v in c['adj'].items() if abs(v) >= 0.5)
        base = k(c['ex_vat']) if deal == 'sale' else k(c['net'])
        rows += (f"<tr{' class=same' if c['same'] else ''}><td>{e(c['bc'] or addr(c['address']))[:40]}{' (то же здание)' if c['same'] else ''}</td>"
                 f"<td class=r>{n(c['area'], 0)}</td><td class=r>{c['floor']}</td><td class=r>{base}</td><td>{e(adj) or '—'}</td>"
                 f"<td class=r><b>{k(c['adjusted'] * f)}</b></td></tr>")
    unit = 'тыс. ₽/м²' if deal == 'sale' else 'тыс. ₽/м²/год'
    own = f", здание объекта (40% веса) — {k(g['own'] * f)}" if g.get('own') else ''
    head = 'Цена без НДС' if deal == 'sale' else 'Ставка чистыми'
    return (f"<table class=t><tr><th>Аналог</th><th class=r>м²</th><th class=r>Эт.</th><th class=r>{head}</th><th>Поправки</th>"
            f"<th class=r>Для нас</th></tr>{rows}<tr class=tot><td colspan=6>Всего {g['n']} объявлений в {g['n_bc']} зданиях; "
            f"среднее по рынку (один голос на здание) — {k(g['market'] * f)}{own} → <b>{k(g['value'] * f)} {unit}</b></td></tr></table>"
            f"<p class=note>{'Торг ' + str(BG) + '% к цене объявления' if deal == 'sale' else 'Ставка чистыми: без НДС, эксплуатации и коммуналки; «для нас» — со скидкой ' + str(int(MAINP['rent_disc'] * 100)) + '%, которую сейчас дают арендаторам'}.</p>")


def appendix(sk):
    S = V['subjects'][sk]
    a = REP[sk][0]
    u = U(sk, a)
    inc = ''.join(f"<tr><td>{e(x['name'])}</td><td class=r>{k(x['comp'])}</td><td class=r>{k(x['rent'])}</td><td class=r>{k(x['income'])}</td>"
                  f"<td class=r>{int(x['wc'] * 100)}/{int(100 - x['wc'] * 100)}{(' ' + ('+' if x['extra'] > 0 else '') + str(x['extra']) + '%') if x.get('extra') else ''}"
                  f"{' · потолок ' + k(x['cap_own']) if x.get('capped') else ''}</td><td class=r><b>{k(x['per_m2'])}</b></td></tr>" for x in S['units_val'])
    return f'''<section class=obj><h2>Приложение: расчёт — {e(S['title'])}</h2>
<h3>Продажи похожих офисов → цена «для себя» ({e(u['name'])})</h3>{grid(sk, a, 'sale', 10)}
<h3>Аренда → цена инвестора ({e(u['name'])})</h3>{grid(sk, a, 'rent', 8)}
<p class=note>Цена инвестора = (ставка × загрузка {int((1 - u['vac']) * 100)}% − 3 000 ₽/м² расходов собственника) ÷ доходность {n(u['cap_rate'] * 100, 1)}%.</p>
<h3>Итог по каждому помещению, тыс. ₽/м²</h3><table class=t><tr><th>Помещение</th><th class=r>«Для себя»</th><th class=r>Аренда, в год</th>
<th class=r>«Инвестор»</th><th class=r>Веса, % (+поправка)</th><th class=r>Итог</th></tr>{inc}</table></section>'''


def summary():
    rows = ''
    for sk, S in V['subjects'].items():
        rows += (f"<tr><td><b>{e(S['title'])}</b><div class=m>{e(', '.join(n(u['area'], 1) for u in S['units']))} м²</div></td>"
                 f"<td class=r>{k(TOT(sk) / AREA(sk))}</td><td class=r><b>{mln(TOT(sk))}</b></td>"
                 f"<td class=r>{mln(TOTS(sk, 'stress'))}</td><td class=r>{mln(TOTS(sk, 'before'))}</td><td class=r>{mln(TOT(sk) / (1 - MAINP['bargain']))}</td></tr>")
    return (f"<table class='t big'><tr><th>Объект</th><th class=r>тыс. ₽/м²</th><th class=r>Ожидаемая сделка</th>"
            f"<th class=r>Если спад углубится</th><th class=r>Без спада (для сравнения)</th><th class=r>Выставлять по</th></tr>{rows}</table>")


def market():
    rows = ''
    for key, p in SC.items():
        rows += (f"<tr{' class=main' if key == V['main'] else ''}><td><b>{e(p['name'])}</b></td><td class=r>{int(p['bargain'] * 100)}%</td>"
                 f"<td class=r>{('−' + str(int(p['rent_disc'] * 100)) + '%') if p['rent_disc'] else 'как в объявлениях'}</td><td class=r>+{int(p['vac_add'] * 100)} п.п.</td><td class=r>+{n(p['cap_add'] * 100, 1)} п.п.</td>"
                 f"<td>{e(p['exposure'])}</td>" + ''.join(f"<td class=r><b>{n(TOTS(sk, key) / 1e6, 1)}</b></td>" for sk in V['subjects']) + '</tr>')
    hold = ''
    for sk, S in V['subjects'].items():
        h = TOT(sk) * 0.07 + AREA(sk) * 1500
        hold += f"<li><b>{e(S['title'])}</b>: выставлять по <b>{mln(TOT(sk) / (1 - MAINP['bargain']))}</b>, рассчитывать на <b>{mln(TOT(sk))}</b>, " \
                f"соглашаться ниже {mln(TOTS(sk, 'stress'))} — только если нужно продать за 2–3 месяца. Полгода ожидания стоят ≈{mln(h)}.</li>"
    return f'''<section class=obj><h2>Рынок сейчас: продавать стало труднее</h2>
<div class=box><ul>
<li><b>Продаж стало меньше.</b> За январь–сентябрь 2026 г. в Москве продано на 21–50% меньше офисов, чем год назад
(IPG.Estate −29%, CMWP −21%, Bright Rich | Corfac −50,5%; по данным «Ведомостей»).</li>
<li><b>Сделки дешевеют, объявления — нет.</b> Цены в объявлениях выросли на 3,6% (IPG.Estate), а цены реальных сделок упали на 13,5% (Bright Rich).
Значит, торг вырос: обычные 5–8% больше не работают.</li>
<li><b>Аренда — рынок арендатора.</b> Помещения дольше стоят пустыми, владельцы снижают ставки и дают скидки до 18% (IBC Real Estate).
Пустых площадей больше всего прибавляется вне деловых районов — там, где «Калейдоскоп» и Imagine Plaza.</li>
<li><b>Деньги дорогие.</b> Ключевая ставка 14%, ЦБ отмечает ускорение цен; компании и инвесторы откладывают покупки, а инвесторы просят больше доходности.</li>
<li><b>Что в нашу пользу.</b> Небольшие офисы (до 1 000 м²) продаются лучше крупных, а нового класса A на продажу всё больше — он дорогой,
и часть покупателей ищет офис попроще и дешевле.</li></ul></div>
<h3>Три сценария</h3>
<table class=t><tr><th>Сценарий</th><th class=r>Торг</th><th class=r>Ставки аренды</th><th class=r>Простой</th><th class=r>Доходность инвестора</th>
<th>Срок продажи</th><th class=r>ЯРД, млн</th><th class=r>Калейдоскоп, млн</th><th class=r>Imagine, млн</th></tr>{rows}</table>
<p class=note>Основной — базовый сценарий: он и есть «ожидаемая сделка» во всём отчёте. «Без спада» — те же аналоги при обычном торге 8% и ставках как в объявлениях.
Imagine Plaza теряет меньше других: цену и раньше ограничивал соседний блок в этом же здании.</p>
<h3>Что делать продавцу</h3><ul>{hold}
<li>Ждать «докризисной» цены невыгодно: полгода ожидания съедают ≈7–8% цены (упущенный доход при ставке 14% и содержание помещения ~1 500 ₽/м² за полгода) —
примерно столько же, сколько отделяет базовый сценарий от «без спада».</li>
<li>Крупные лоты (680 м² в ЯРД, 524 и 460 м² в Imagine) делить на блоки по 150–350 м²: на них сейчас больше покупателей.</li></ul></section>'''


def method():
    adj = [('Торг', f'−{BG}% к цене в объявлении (в спокойном рынке −8%)'), ('Удалённость от центра', '−4% за каждый км, на который аналог ближе к центру'),
           ('Метро', '−1% за каждую минуту, на которую аналог ближе к метро'),
           ('Здание', '6% за каждый балл оценки по фото (фасад, холл, ремонт, инженерия, потолки)'),
           ('Этаж и вход', '1 этаж с входом с улицы дороже верхних на 10%'), ('Площадь', 'крупный блок дешевле: −3% за каждое удвоение'),
           ('Ремонт', 'аналог без отделки +10%; с арендатором −3%')]
    rows = ''.join(f'<tr><td><b>{a}</b></td><td>{b}</td></tr>' for a, b in adj)
    return f'''<section><h2 style="margin-top:14px">Как считали</h2>
<ol class=steps>
<li><b>Смотрим глазами покупателя.</b> Он ищет офис в бизнес-центре класса B у той же станции метро и у соседних 2–3 станций.</li>
<li><b>Делим конкурентов на 4 группы.</b> БЦ класса B — главное сравнение. БЦ класса A — сколько стоит «лучше». Админздания и особняки — сколько стоит «проще».
1 этажи жилых домов — только для наших 1 этажей с входом с улицы.</li>
<li><b>Каждый чужой офис пересчитываем в наш</b> по одной шкале (таблица ниже). У каждого бизнес-центра один голос, чтобы БЦ с десятью объявлениями не перевешивал рынок.</li>
<li><b>Получаем две цены.</b> «Для себя» — по продажам похожих офисов. «Инвестор» — по аренде: сколько заплатит тот, кто хочет 12–13% годовых. Итог — середина.</li>
<li><b>Учитываем спад рынка</b> (см. выше): торг {BG}% вместо 8%, ставки аренды на {int(MAINP['rent_disc'] * 100)}% ниже объявлений, больше простой, выше требуемая доходность.</li>
<li><b>Проверяем зданием объекта.</b> Если в том же здании продаётся или сдаётся похожий блок, он весит 40%, а цена нашего блока не может быть выше соседнего больше чем на 3%.</li></ol>
<h3>Как пересчитываем чужой офис в наш</h3><table class=t><tr><th>Что учитываем</th><th>Правило</th></tr>{rows}</table>
<p class=note>Поправки на центр и метро взяты осторожнее, чем показала статистика по 195 продажам офисов (−5,5% за км и −2% за минуту).</p>
<h3>Данные</h3><p>{n(len(ROWS))} объявлений Циана о продаже и аренде офисов (выгрузка {DATE}); страницы всех {sum(1 for v in PAGES.values() if v.get('title'))}
бизнес-центров из выборки (класс, год, инженерия, фото); оценка по фото {sum(1 for x in RAT.values() if isinstance(x, dict))} зданий; координаты станций метро, МЦК и МЦД.
Убраны строящиеся башни, городские торги и банкротства, ошибки в объявлениях и офисы в жилых комплексах, записанные как БЦ.
Цены — без НДС.</p></section>'''


def history():
    return f'''<section class=obj><h2>Что изменилось и что нельзя проверить</h2>
<table class=t><tr><th>Объект</th><th class=r>v1</th><th class=r>v2</th><th class=r>v3</th><th class=r>v4 (группы)</th><th class=r>Сейчас (спад)</th><th>Главная причина изменения</th></tr>
<tr><td>БЦ «ЯРД»</td><td class=r>189,4</td><td class=r>189,7</td><td class=r>183,1</td><td class=r>{n(TOTS('yard', 'before') / 1e6, 1)}</td><td class=r><b>{n(TOT('yard') / 1e6, 1)}</b></td>
<td>v4: аренда класса B вокруг выше, рядом МЦД-4. Сейчас: торг 13%, аренда −10%, доходность +0,5 п.п.</td></tr>
<tr><td>БЦ «Калейдоскоп»</td><td class=r>142,3</td><td class=r>124,4</td><td class=r>110,5</td><td class=r>{n(TOTS('kaleidoscope', 'before') / 1e6, 1)}</td><td class=r><b>{n(TOT('kaleidoscope') / 1e6, 1)}</b></td>
<td>v4: сравнение по 20 БЦ, скидки за потолки. Сейчас: спад рынка, особенно вне деловых районов</td></tr>
<tr><td>Imagine Plaza</td><td class=r>360,3</td><td class=r>316,7</td><td class=r>300,5</td><td class=r>{n(TOTS('imagine', 'before') / 1e6, 1)}</td><td class=r><b>{n(TOT('imagine') / 1e6, 1)}</b></td>
<td>v4: потолок по соседнему блоку в здании. Сейчас: соседний блок тоже продастся с большим торгом</td></tr></table>
<p class=note>Суммы — млн ₽ без НДС. v1–v3 — прошлые версии: смешанная подборка аналогов в радиусе 2 км; v4 — группы конкурентов без учёта спада.</p>
<h3>Что нельзя проверить</h3><ul>
<li>На Циане цены предложений, а не сделок; размер торга в спад — оценка по данным консультантов (13%, в стрессе 18%).</li>
<li>Данные о спаде — из обзоров консультантов, которые расходятся между собой (продажи −21…−50%); итоги заседания ЦБ 23 октября могут сдвинуть оценку.</li>
<li>Оценки зданий по фото — экспертные; полбалла меняют цену примерно на 3%.</li>
<li>Объекты не осматривались: площади, потолки, состояние — из презентаций. Кондиционирование ЯРД нужно уточнить.</li></ul>
<h3>Источники</h3><ul><li>Циан: объявления и страницы бизнес-центров (ссылки у аналогов), {DATE}.</li>
<li>Станции метро, МЦК, МЦД — api.hh.ru/metro/1.</li>
<li>«Ведомости»: продажи офисов в Москве за 9 мес. 2026 г. (IPG.Estate, CMWP, Bright Rich | Corfac International, «Гарнель»); IBC Real Estate (А. Ефимов) — скидки к ставкам до 18%.</li>
<li><a href="https://realty.rbc.ru/news/6a4e067f9a7947385ff01d1d">РБК Недвижимость — вакансия растёт в неделовых локациях</a>;
<a href="https://finance.rambler.ru/realty/56422251-prodazhi-nebolshih-ofisov-v-moskve-vyrosli-na-46-s-nachala-2026-goda/">продажи небольших офисов +46%</a>;
<a href="https://www.retail.ru/news/tsb-rf-sokhranil-klyuchevuyu-stavku-na-urovne-14-godovykh-12-sentyabrya-2026-282051/">ЦБ сохранил ставку 14%</a>.</li>
<li>Аналитический центр Москвы (ставки аренды класса B); NF Group (доходность офисов).</li>
<li>Презентации собственника.</li></ul></section>'''


CSS = '''
@page { size: A4; margin: 13mm 12mm; }
body { font-family: "DejaVu Sans", Arial, sans-serif; font-size: 9.2pt; color: #1b2430; line-height: 1.38; }
h1 { font-size: 20pt; margin: 0 0 4px; color: #16324f; } h2 { font-size: 15pt; color: #16324f; margin: 0 0 2px; padding-top: 3px; border-top: 3px solid #16324f; }
h3 { font-size: 10.6pt; color: #16324f; margin: 10px 0 4px; break-after: avoid; }
.sub { color: #5b6b7c; margin-bottom: 6px; } .m { color: #5b6b7c; font-size: 7.6pt; } .note { color: #4a5868; font-size: 7.9pt; margin: 3px 0; }
a { color: #1f5fa8; text-decoration: none; } p { margin: 3px 0; }
.obj { page-break-before: always; } .pb { page-break-before: always; }
.gal { display: grid; grid-template-columns: 1.3fr 1fr 1fr; gap: 5px; margin: 4px 0 6px; } .gal img { width: 100%; height: 118px; object-fit: cover; border-radius: 3px; }
.two { display: grid; grid-template-columns: 1.1fr 1fr; gap: 12px; } ul { margin: 2px 0; padding-left: 15px; } li { margin: 1px 0; }
.pros b { color: #1f7a4d; } .cons b { color: #b2332b; } .pros, .cons { margin-bottom: 4px; }
.box { background: #f2f5f8; border-left: 4px solid #16324f; padding: 6px 10px; margin: 8px 0; }
.cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; }
.card { border: 1px solid #d7dee6; border-radius: 4px; overflow: hidden; break-inside: avoid; font-size: 7.8pt; }
.card img { width: 100%; height: 88px; object-fit: cover; display: block; } .noimg { height: 88px; background: #eef1f4; color: #8a97a5; display: grid; place-items: center; }
.cb { padding: 4px 6px; } .tag { background: #e3ebf3; color: #16324f; border-radius: 3px; padding: 0 4px; font-size: 7pt; }
table.t { width: 100%; border-collapse: collapse; font-size: 7.9pt; margin: 3px 0; }
.t th { background: #16324f; color: #fff; font-weight: 600; padding: 3px 4px; text-align: left; } .t td { border-bottom: 1px solid #e1e6ec; padding: 2px 4px; vertical-align: top; }
.t .r { text-align: right; white-space: nowrap; } .t tr.tot td { background: #eaf0f6; font-weight: 600; }
.t tr.main td { background: #f1f7ef; } .t tr.same td { background: #fff7e0; } table.big { font-size: 9pt; } .big td { padding: 4px; }
img.th { width: 44px; height: 28px; object-fit: cover; border-radius: 2px; } .bct th:nth-child(2), .bct td:nth-child(2) { width: 30%; } .bct th:nth-child(1) { width: 46px; } .bct td { padding: 1px 4px; }
.kpi { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin: 10px 0; } .kpi div { border: 1px solid #d7dee6; border-radius: 5px; padding: 8px 10px; }
.kpi b { font-size: 16pt; color: #16324f; display: block; } .steps li { margin: 4px 0; }
'''


def build():
    y5, k1, k2 = U('yard', 'y5'), U('kaleidoscope', 'k1'), U('kaleidoscope', 'k2')
    y1 = U('yard', 'y1')
    im = V['subjects']['imagine']['units_val']
    g = [u['per_m2'] for u in im if u['floor'] == 1]
    up = [u['per_m2'] for u in im if u['floor'] > 1]
    body = f'''
<div class=m>Оценка · {DATE}</div>
<h1>Справедливая цена продажи трёх офисов</h1>
<div class=sub>БЦ «ЯРД» · БЦ «Калейдоскоп» · БЦ Imagine Plaza — с учётом спада на рынке офисов, цены без НДС</div>
<div class=kpi><div><b>{mln(TOT('yard'))}</b>БЦ «ЯРД», 984 м²</div><div><b>{mln(TOT('kaleidoscope'))}</b>БЦ «Калейдоскоп», 796 м²</div>
<div><b>{mln(TOT('imagine'))}</b>Imagine Plaza, 8 блоков, 2 133 м²</div></div>
{summary()}
<p class=note>Ожидаемая сделка — вероятная цена продажи за 6–12 месяцев при нынешнем рынке. «Выставлять по» — на 13% выше: запас на торг.
Цены без НДС; если продавец платит НДС, добавляется 22%.</p>
<h3>Коротко</h3><ul>
<li><b>Рынок в спаде:</b> продажи офисов за год упали на 21–50%, цены сделок — на 13,5%, арендаторы получают скидки до 18%. Поэтому цены ниже, чем
в «спокойном» рынке: на {n((1 - TOT('yard') / TOTS('yard', 'before')) * 100, 0)}% (ЯРД), {n((1 - TOT('kaleidoscope') / TOTS('kaleidoscope', 'before')) * 100, 0)}% («Калейдоскоп»)
и {n((1 - TOT('imagine') / TOTS('imagine', 'before')) * 100, 0)}% (Imagine).</li>
<li><b>ЯРД</b> — лучшая локация (ТТК, Сити, МЦД-4 рядом), но здание старше соседей и, по данным Циана, без центрального кондиционирования.
1 этаж — {k(y1['per_m2'])} тыс. ₽/м², 5 этаж — {k(y5['per_m2'])} тыс.</li>
<li><b>Калейдоскоп</b> — отдельный вход и большой БЦ, но низкие потолки и метро в 23 минутах. 1 этаж — {k(k1['per_m2'])} тыс., антресоль — {k(k2['per_m2'])} тыс.</li>
<li><b>Imagine Plaza</b> — цену держит соседний блок в том же здании. 1 этаж — {k(min(g))}–{k(max(g))} тыс., 2–3 этажи — {k(min(up))}–{k(max(up))} тыс.
Продавать по очереди: небольшие блоки сейчас продаются лучше.</li>
<li>Выставлять на 13% выше ожидаемой сделки, а ждать «докризисной» цены невыгодно: полгода ожидания стоят продавцу 7–8%.</li></ul>
{market()}
{method()}
{''.join(object_section(sk) for sk in V['subjects'])}
{''.join(appendix(sk) for sk in V['subjects'])}
{history()}'''
    return f'<!doctype html><html lang="ru"><head><meta charset="utf-8"><style>{CSS}</style></head><body>{body}</body></html>'


if __name__ == '__main__':
    os.makedirs('reports', exist_ok=True)
    open('reports/offices_valuation.html', 'w').write(build())
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        b = p.chromium.launch()
        pg = b.new_page()
        pg.goto('file://' + os.path.abspath('reports/offices_valuation.html'))
        pg.wait_for_timeout(2000)
        pg.pdf(path='reports/Оценка_офисов_ЯРД_Калейдоскоп_ImaginePlaza.pdf', format='A4', print_background=True,
               margin=dict(top='13mm', bottom='13mm', left='12mm', right='12mm'),
               display_header_footer=True, header_template='<div></div>',
               footer_template='<div style="font-size:7pt;color:#8a97a5;width:100%;text-align:center"><span class="pageNumber"></span> / <span class="totalPages"></span></div>')
        b.close()
    print('ok')
