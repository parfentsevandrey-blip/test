#!/usr/bin/env python3
"""Аналитическая записка (PDF): цена продажи трёх офисных объектов с учётом спада рынка.
Данные: valuation.json, bc_profiles.json, checks.json, backtest.json → reports/Оценка_офисов_ЯРД_Калейдоскоп_ImaginePlaza.pdf"""
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
CHK = json.load(open('commercial-data/checks.json'))
PH = os.path.abspath('commercial-data/photos')
BPH = os.path.abspath('commercial-data/bcphotos')
SUBJ = os.path.abspath('commercial-data/subj')
DATE = '14 октября 2026 г.'
REP = {'yard': ['y1', 'y5'], 'kaleidoscope': ['k1', 'k2'], 'imagine': ['i2', 'i4']}
GROUP = {'K1': 'Офисы в БЦ класса B', 'K2': 'Офисы в БЦ класса A', 'K3': 'Административные здания, особняки',
         'K4': 'Помещения на 1 этаже жилых домов'}
ADJ_RU = {'loc': 'центр', 'metro': 'метро', 'bld': 'здание', 'fl': 'этаж', 'size': 'площадь', 'fit': 'отделка'}
SC = V['scenarios']
MAINP = SC[V['main']]
KS = (1 - MAINP['bargain']) / V['params']['bargain']
KR = 1 - MAINP['rent_disc']
BG = int(MAINP['bargain'] * 100)
HDR = {'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36',
       'Referer': 'https://www.cian.ru/'}


class Num:
    """Сквозная нумерация таблиц и рисунков."""
    t = f = 0

    @classmethod
    def tab(cls, caption):
        cls.t += 1
        return f'<p class=cap>Таблица {cls.t}. {caption}</p>'

    @classmethod
    def fig(cls, caption):
        cls.f += 1
        return f'<figcaption>Рис. {cls.f}. {caption}</figcaption>'


def n(v, d=0):
    if v is None:
        return '–'
    return f'{v:,.{d}f}'.replace(',', ' ').replace('.', ',')


def mln(v):
    return n(v / 1e6, 1)


def k(v):
    return n(v / 1000) if v else '–'


def r1(v):
    return n(v, 2).rstrip('0').rstrip(',') if v is not None else '–'


def sg(v, d=0):
    return ('+' if v > 0 else '−' if v < 0 else '') + n(abs(v), d)


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


def U(sk, key):
    return next(u for u in V['subjects'][sk]['units_val'] if u['key'] == key)


def TOT(sk, key=None):
    return sum((u['scen'][key]['total'] if key else u['total']) for u in V['subjects'][sk]['units_val'])


def AREA(sk):
    return sum(u['area'] for u in V['subjects'][sk]['units_val'])


def cv(sk, uk, key):
    return (V['subjects'][sk]['comps'][uk].get(key) or {}).get('value')


def med(ids, f):
    ex = {int(x) for x in V['exclude']}
    v = sorted(ROWS[i][f] for i in ids if i not in ex and not ROWS[i].get('offplan') and not ROWS[i].get('nonmarket')
               and (4000 <= (ROWS[i].get(f) or 0) <= 150000 if f == 'net' else 50000 <= (ROWS[i].get(f) or 0) <= 1500000))
    return v[len(v) // 2] if v else None


def dist(sk, b):
    return bp.km(bp.SUBJ[sk], (b['lat'], b['lng']))


def pct(a, b):
    return sg((a / b - 1) * 100)


# ---------------- тексты ----------------
TEXT = {
    'yard': dict(
        where='Хорошёвский район САО, деловой квартал Магистральных улиц у Третьего транспортного кольца, в 2 км от Москва-Сити и в 5,9 км от Кремля. '
              'Ближайшая станция — «Ермакова Роща» МЦД-4 (0,5 км, около 9 минут пешком); ближайшее метро — Шелепиха (около 17 минут).',
        cian='На ЦИАН помещения в продаже не выставлены. Блок на 1 этаже сдаётся в аренду: его рекламируют два агента по разным ставкам. '
             'На 5 этаже сдаются части этажа. В соседнем строении комплекса (11с7) продаётся офис 165 м² за 45 млн руб.',
        strong=['близость к ТТК и Москва-Сити, станция МЦД-4 в 500 м', 'ухоженное кирпичное здание, ресепшен, кафе, отделение банка, парковка',
                'в здании есть арендный спрос: шесть объявлений', 'цельный верхний этаж 680 м²', 'офисов класса B на продажу рядом мало'],
        weak=['по данным ЦИАН кондиционирование местное, а не центральное, как в презентации', 'до метро около 17 минут пешком',
              'в 300 м более сильный БЦ «Магистраль Плаза»', '1 этаж отделан под банк, для обычного офиса нужна переделка',
              'лот 680 м² дорог, покупателей на него немного']),
    'kaleidoscope': dict(
        where='Нагатино-Садовники (ЮАО), между Варшавским и Каширским шоссе, 9,3 км от Кремля. Метро Нагорная — около 23 минут пешком '
              '(в презентации указано 10–15 минут). В 300–500 м — бизнес-парк «Сириус Парк», главный конкурент.',
        cian='Помещения на ЦИАН не выставлены ни на продажу, ни в аренду. Страницы бизнес-центра в справочнике ЦИАН нет.',
        strong=['отдельный вход с улицы на 1 этаже', 'крупный бизнес-центр: охрана, кафе, парковка, лифты OTIS',
                'этажи можно продавать по отдельности', 'после ремонта фасада и холла здание на уровне соседних БЦ класса B'],
        weak=['низкие потолки: 2,57 м на 1 этаже и 2,37 м на антресоли', 'до метро 23–27 минут пешком',
              'рядом более современный «Сириус Парк» с потолками около 3 м',
              'арендные ставки в районе низкие, поэтому инвестору объект малоинтересен']),
    'imagine': dict(
        where='Нагатинская улица (ЮАО), 8,2 км от Кремля. Метро Нагатинская — около 21 минуты пешком, у входа трамвай, рядом ТЦ «Конфетти». '
              'Здание — реконструированный производственный корпус 1974 года.',
        cian='Восемь блоков на ЦИАН не выставлены. В этом же здании продаётся блок 187 м² на 2 этаже за 37,4 млн руб. '
             '(200 тыс. руб./м² с НДС) — прямой ориентир цены.',
        strong=['во всех блоках сделан ремонт; спринклеры, четыре лифта', 'удобные площади 168–524 м²',
                'на 1 этаже отдельные входы и витрины', 'высокие потолки, около 4 м', 'трамвай у входа, торговый центр рядом, парковка'],
        weak=['до метро около 21 минуты пешком', 'бывший производственный корпус, вход со двора',
              'соседний блок в здании задаёт потолок цены', 'восемь лотов одновременно давят на цену',
              'площадь здания в презентации указана по-разному: 4 000 и 12 000 м² (ЦИАН — 4 992 м²)']),
}
SUBJ_IMG = {'yard': [('yard-2.jpg', 'БЦ «ЯРД» (презентация собственника)'), ('yard-7.jpg', '1 этаж, планировка'), ('yard-9.jpg', '5 этаж, планировка')],
            'kaleidoscope': [('kal-photo.jpg', 'БЦ «Калейдоскоп» (презентация собственника)'), ('kal-2.jpg', 'Описание и планировка'), ('kal-3.jpg', '2 этаж, планировка')],
            'imagine': [('img-1.jpg', 'БЦ Imagine Plaza (презентация собственника)'), ('img-4.jpg', 'Продаваемые блоки'), ('img-7.jpg', 'Интерьеры')]}


def reasoning(sk):
    S = V['subjects'][sk]
    a, b = REP[sk]
    ua, ub = U(sk, a), U(sk, b)
    sa = S['comps'][a]['sale_K1']
    rd = int(MAINP['rent_disc'] * 100)
    if sk == 'yard':
        k3 = [c['adjusted'] * KS for c in S['comps'][a].get('sale_K3', {}).get('comps', []) if c.get('used') and 'Хорошёвск' in (c['address'] or '')]
        k3s = (f" Административное здание на Хорошёвском шоссе, 38к1 — слабее ЯРД (2,5 балла) — с поправкой на эту разницу даёт {k(min(k3))}–{k(max(k3))} тыс.: "
               f"даже самый дешёвый аналог рядом не указывает на более низкую цену." if k3 else '')
        return [
            f"Продаж офисов в бизнес-центрах класса B рядом немного: {sa['n']} лотов в {sa['n_bc'] + sa['n_same']} зданиях. После торга {BG}% и поправок "
            f"на отличия от ЯРД они дают около {k(ua['comp'])} тыс. руб./м². Магистраль Плаза дороже, потому что здание сильнее; корпуса на 4-й Магистральной дешевле.",
            f"Рыночная ставка аренды для ЯРД — около {k(ua['rent'])} тыс. руб./м² в год без НДС и эксплуатации (на {rd}% ниже ставок в объявлениях). "
            f"Ставки в самом ЯРД ниже, чем у соседей, и учтены с весом 40%. При доходности {n(ua['cap_rate'] * 100, 1)}%, по которой сейчас продаются "
            f"офисы под аренду, это около {k(ua['income'])} тыс. руб./м².",
            f"Итог — среднее двух оценок: {k(ua['per_m2'])} тыс. руб./м² за 1 этаж. Для 5 этажа подходящих по площади продаж ещё меньше (всего "
            f"{S['comps'][b]['sale_K1']['n_bc']} здания), поэтому оценка по аренде весит {int(100 - ub['wc'] * 100)}%: {k(ub['per_m2'])} тыс. руб./м².",
            f"Для сравнения: офисы в БЦ класса A рядом в пересчёте стоят около {k(cv(sk, b, 'sale_K2') * KS)} тыс. руб./м².{k3s}"]
    if sk == 'kaleidoscope':
        return [
            f"Офисы в бизнес-центрах класса B у тех же и соседних станций ({sa['n']} лотов в {sa['n_bc']} зданиях) после торга {BG}% и поправок дают "
            f"около {k(ua['comp'])} тыс. руб./м² для 1 этажа и {k(ub['comp'])} тыс. для антресоли. Главная поправка — здание: у большинства соседей оно лучше.",
            f"Рыночная ставка аренды — около {k(ua['rent'])} и {k(ub['rent'])} тыс. руб./м² в год. При доходности {n(ua['cap_rate'] * 100, 1)}%, "
            f"по которой продаются офисы в таких зданиях, это около {k(ua['income'])} и {k(ub['income'])} тыс.",
            f"Итог — середина двух оценок со скидкой за низкие потолки (3% и 7%): {k(ua['per_m2'])} тыс. руб./м² за 1 этаж и {k(ub['per_m2'])} тыс. за антресоль.",
            f"Для сравнения: класс A рядом (DM Tower) в пересчёте — около {k(cv(sk, a, 'sale_K2') * KS)} тыс., административные здания — около "
            f"{k(cv(sk, a, 'sale_K3') * KS)} тыс. руб./м²."]
    up = [u['per_m2'] for u in S['units_val'] if u['floor'] > 1]
    g = [u['per_m2'] for u in S['units_val'] if u['floor'] == 1]
    sb = S['comps'][b]['sale_K1']
    return [
        f"Соседний блок в этом же здании выставлен по 164 тыс. руб./м² без НДС; при торге {BG}% сделка по нему — около {k(163934 * (1 - MAINP['bargain']))} тыс. "
        f"Аналоги в других бизнес-центрах после поправок дают для такого блока около {k(sb['market'] * KS)} тыс. — близко к цене соседнего блока.",
        f"Рыночная ставка аренды для Imagine — около {k(ub['rent'])} тыс. руб./м² в год; при доходности {n(ub['cap_rate'] * 100, 1)}% это около "
        f"{k(ub['income'])} тыс. Цены в объявлениях выше того, что окупает аренда, поэтому итог — середина двух оценок.",
        f"1 этаж с отдельными входами — {k(min(g))}–{k(max(g))} тыс. руб./м², 2–3 этажи — {k(min(up))}–{k(max(up))} тыс. "
        f"(балкон на 3 этаже +3%, крупный блок дешевле в расчёте на метр).",
        f"Блоки лучше продавать по очереди, начиная с 1 этажа. Все восемь одним лотом — со скидкой 10–15%, то есть "
        f"{n(TOT(sk) * .85 / 1e6, 0)}–{n(TOT(sk) * .90 / 1e6, 0)} млн руб."]


# ---------------- таблицы ----------------
def summary_table():
    rows = ''
    for sk, S in V['subjects'].items():
        rows += (f"<tr><td>{e(S['title'])}</td><td class=r>{n(AREA(sk), 1)}</td><td class=r>{k(TOT(sk) / AREA(sk))}</td>"
                 f"<td class=r><b>{mln(TOT(sk))}</b></td><td class=r>{mln(TOT(sk, 'stress'))}</td><td class=r>{mln(TOT(sk, 'before'))}</td>"
                 f"<td class=r>{mln(TOT(sk) / (1 - MAINP['bargain']))}</td></tr>")
    return (Num.tab('Цена продажи, без НДС') +
            f"<table class=t><thead><tr><th>Объект</th><th class=r>Площадь, м²</th><th class=r>Тыс. руб./м²</th><th class=r>Ожидаемая цена сделки, млн руб.</th>"
            f"<th class=r>При углублении спада, млн руб.</th><th class=r>Без учёта спада, млн руб.</th><th class=r>Цена предложения, млн руб.</th></tr></thead>"
            f"<tbody>{rows}</tbody></table><p class=src>Ожидаемая цена сделки — базовый сценарий, срок продажи 6–12 месяцев. Цена предложения — с запасом {BG}% на торг. "
            f"Если продавец — плательщик НДС, к ценам добавляется 22%.</p>")


def units_table(sk):
    S = V['subjects'][sk]
    rows = ''.join(f"<tr><td>{e(u['name'])}</td><td class=r>{n(u['area'], 1)}</td><td class=r>{k(u['per_m2'])}</td>"
                   f"<td class=r><b>{mln(u['total'])}</b></td><td class=r>{mln(u['scen']['stress']['total'])}</td>"
                   f"<td class=r>{mln(u['scen']['before']['total'])}</td><td class=r>{mln(u['list_price'])}</td></tr>" for u in S['units_val'])
    rows += (f"<tr class=tot><td>Итого</td><td class=r>{n(AREA(sk), 1)}</td><td class=r>{k(TOT(sk) / AREA(sk))}</td><td class=r>{mln(TOT(sk))}</td>"
             f"<td class=r>{mln(TOT(sk, 'stress'))}</td><td class=r>{mln(TOT(sk, 'before'))}</td><td class=r>{mln(TOT(sk) / (1 - MAINP['bargain']))}</td></tr>")
    return (Num.tab(f"{e(S['title'])}: цена по помещениям, без НДС") +
            f"<table class=t><thead><tr><th>Помещение</th><th class=r>м²</th><th class=r>Тыс. руб./м²</th><th class=r>Ожидаемая сделка, млн</th>"
            f"<th class=r>При углублении спада, млн</th><th class=r>Без учёта спада, млн</th><th class=r>Цена предложения, млн</th></tr></thead><tbody>{rows}</tbody></table>")


def groups_table(sk):
    S = V['subjects'][sk]
    a = REP[sk][0]
    c = S['clusters']
    role = {'K1': 'основное сравнение', 'K2': 'верхняя граница', 'K3': 'более дешёвая альтернатива',
            'K4': 'альтернатива для 1 этажа с входом с улицы'}
    rows = ''
    for cl in ('K1', 'K2', 'K3', 'K4'):
        sale, rent = c[cl]['sale'], c[cl]['rent']
        vals = ' / '.join(k(cv(sk, uk, f'sale_{cl}') * KS) for uk in REP[sk] if cv(sk, uk, f'sale_{cl}'))
        note = role[cl] if not (cl == 'K4' and not cv(sk, a, 'sale_K4')) else 'не применяется: вход через холл БЦ'
        rows += (f"<tr><td>{GROUP[cl]}</td><td class=r>{sale['n'] if sale else 0}</td><td class=r>{k(sale['med']) if sale else '–'}</td>"
                 f"<td class=r>{rent['n'] if rent else 0}</td><td class=r>{k(rent['med']) if rent else '–'}</td><td class=r>{vals or '–'}</td><td>{note}</td></tr>")
    names = ' / '.join(e(U(sk, uk)['name']).lower() for uk in REP[sk])
    return (Num.tab('Группы конкурентов у тех же и соседних станций метро') +
            f"<table class=t><thead><tr><th>Группа</th><th class=r>Продажа, лотов</th><th class=r>Медиана, тыс. руб./м²</th><th class=r>Аренда, лотов</th>"
            f"<th class=r>Медиана, тыс. руб./м² в год</th><th class=r>В пересчёте на объект, тыс. руб./м²</th><th>Роль в оценке</th></tr></thead>"
            f"<tbody>{rows}</tbody></table><p class=src>Медианы цен предложений без НДС; ставки аренды без НДС, эксплуатационных и коммунальных платежей. "
            f"Пересчёт на объект ({names}) — после торга {BG}% и поправок на отличия. В итог входит только первая группа. Источник: ЦИАН, расчёт.</p>")


def bc_table(sk, lim1=8, lim2=2):
    live = [b for b in PROF[sk]['bcs'] if (b['sale'] or len(b['rent']) >= 2) and not b['offplan']]
    sel = (sorted([b for b in live if b['cluster'] == 'K1'], key=lambda b: dist(sk, b))[:lim1] +
           sorted([b for b in live if b['cluster'] == 'K2'], key=lambda b: dist(sk, b))[:lim2])
    own_url = V['subjects'][sk].get('url')
    rows = ''
    for b in sel:
        rt = (RAT.get(b['url'] or '') or {}).get('rating')
        own = b['url'] and b['url'] == own_url
        title = (b['title'] or b['name'] or '').replace('Бизнес-центр ', '').replace('Бизнес-квартал ', '').replace('Деловой квартал ', '')
        rows += (f"<tr><td>{e(title)[:42]}{' (объект)' if own else ''}</td><td>{e(b['cls'] or '–')}</td><td class=r>{b['year'] or '–'}</td>"
                 f"<td class=r>{r1(rt) if rt else '–'}</td><td class=r>{n(dist(sk, b), 1)}</td><td>{e(b['metro'])}, {b['walk']} мин</td>"
                 f"<td class=r>{len(b['sale'])}/{len(b['rent'])}</td><td class=r>{k(med(b['sale'], 'ex_vat'))}</td><td class=r>{k(med(b['rent'], 'net'))}</td></tr>")
    return (Num.tab('Ближайшие бизнес-центры с объявлениями о продаже или аренде') +
            f"<table class=t><thead><tr><th>Бизнес-центр</th><th>Класс</th><th class=r>Год</th><th class=r>Оценка здания</th><th class=r>От объекта, км</th>"
            f"<th>Метро пешком</th><th class=r>Продажа/ аренда, лотов</th><th class=r>Продажа, тыс. руб./м²</th><th class=r>Аренда, тыс. руб./м² в год</th></tr></thead>"
            f"<tbody>{rows}</tbody></table><p class=src>Оценка здания по фотографиям, от 1 до 5: 5 — новое здание класса A, 4 — современный бизнес-центр или "
            f"качественный лофт, 3 — типичный класс B, 2 — старое здание без реконструкции. Время пешком рассчитано по координатам. "
            f"Показаны {lim1} ближайших БЦ класса B и {lim2} класса A из {len(live)}. Источник: ЦИАН, расчёт.</p>")


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


def figure(c, group=None):
    img = photo(c['id'])
    title = c.get('bc') or addr(c['address'])[:40]
    cap = (f"{e(title)}{(' (' + GROUP[group].lower() + ')') if group else ''}: {n(c['area'], 0)} м², {c['floor']} этаж, {mln(c['price'])} млн руб., "
           f"{k(c['ex_vat'])} тыс. руб./м² без НДС; в пересчёте на объект — {k(c['adjusted'] * KS)} тыс. "
           f"<a href=\"{c['url'].split('?')[0]}\">ЦИАН</a>")
    return f"<figure>{f'<img src={img}>' if img else '<div class=noimg></div>'}{Num.fig(cap)}</figure>"


def object_section(no, sk):
    S = V['subjects'][sk]
    T = TEXT[sk]
    a = REP[sk][0]
    res = S['comps'][a]
    gal = ''.join(f"<figure><img src=\"file://{SUBJ}/{f}\">{Num.fig(e(c))}</figure>" for f, c in SUBJ_IMG[sk])
    units = '; '.join(f"{e(u['name']).lower()} — {n(u['area'], 1)} м²" for u in S['units'])
    sw = ''.join(f"<tr><td>{e(s)}</td><td>{e(w)}</td></tr>" for s, w in zip(T['strong'] + [''] * 6, T['weak'] + [''] * 6) if s or w)
    main_figs = ''.join(figure(c) for c in pick(res['sale_K1']['comps'], 6))
    other_figs = ''.join(figure(c, cl) for cl in ('K2', 'K3', 'K4') for c in pick((res.get(f'sale_{cl}') or {}).get('comps', []), 1))
    reason = ''.join(f'<p>{x}</p>' for x in reasoning(sk))
    return f'''
<section class=pb><h1>{no}. {e(S['title'])}</h1>
<p>{e(S['address'])}. Продаются: {units}. {T['where']}</p>
<p>{T['cian']} Оценка здания по фотографиям — {r1(S['rating'])} из 5.</p>
<div class=gal>{gal}</div>
{units_table(sk)}
<h2>{no}.1. Обоснование цены</h2>{reason}
{Num.tab('Сильные и слабые стороны')}<table class='t sw'><thead><tr><th>Сильные стороны</th><th>Слабые стороны</th></tr></thead><tbody>{sw}</tbody></table>
<h2>{no}.2. Конкуренты</h2>
{groups_table(sk)}
{bc_table(sk)}
<p>Ниже — офисы в бизнес-центрах класса B с наибольшим весом в расчёте ({e(U(sk, a)['name']).lower()}) и по одному примеру из других групп.</p>
<div class=figs>{main_figs}</div><div class=figs>{other_figs}</div></section>'''


def market_section():
    rows = ''
    for key, p in SC.items():
        rows += (f"<tr><td>{e(p['name'])}</td><td class=r>{int(p['bargain'] * 100)}%</td>"
                 f"<td class=r>{('−' + str(int(p['rent_disc'] * 100)) + '%') if p['rent_disc'] else '0'}</td>"
                 f"<td class=r>+{int(p['vac_add'] * 100)}</td><td class=r>+{n(p['cap_add'] * 100, 1)}</td><td>{e(p['exposure'])}</td>"
                 + ''.join(f"<td class=r>{mln(TOT(sk, key))}</td>" for sk in V['subjects']) + '</tr>')
    adv = ''
    for sk, S in V['subjects'].items():
        h = TOT(sk) * 0.07 + AREA(sk) * 1500
        adv += (f"<tr><td>{e(S['title'])}</td><td class=r>{mln(TOT(sk) / (1 - MAINP['bargain']))}</td><td class=r>{mln(TOT(sk))}</td>"
                f"<td class=r>{mln(TOT(sk, 'stress'))}</td><td class=r>{mln(h)}</td></tr>")
    return f'''<section class=pb><h1>2. Состояние рынка и сценарии</h1>
<p>Спрос на офисы в Москве снижается. За январь–сентябрь 2026 года, по данным консультантов, продано на 21–50% меньше офисных площадей, чем годом ранее
(IPG.Estate — минус 29%, CMWP — минус 21%, Bright Rich | Corfac International — минус 50,5%). Цены в объявлениях за год выросли на 3,6% (IPG.Estate),
а средняя цена совершённых сделок снизилась на 13,5% (Bright Rich). Разрыв между ценой предложения и ценой сделки вырос.</p>
<p>В аренде рынок перешёл на сторону арендатора: помещения дольше ищут арендаторов, владельцы снижают ставки и дают скидки до 18% (IBC Real Estate).
Свободных площадей больше всего прибавляется вне деловых районов — там расположены «Калейдоскоп» и Imagine Plaza. Ключевая ставка — 14%, Банк России
отмечает усиление инфляционного давления; компании и инвесторы дольше принимают решения о покупке.</p>
<p>Есть и обратные сигналы. Небольшие офисы, до 1 000 м², продаются лучше крупных, а предложение новых офисов класса A растёт — они дороги,
и часть покупателей ищет помещения проще и дешевле.</p>
<p>Поскольку аналоги — это цены и ставки в объявлениях, спад учтён четырьмя параметрами: размером торга, скидкой к ставкам аренды, простоем помещений
и доходностью, которую требует инвестор. Основной сценарий — базовый; его результат и есть ожидаемая цена сделки.</p>
{Num.tab('Сценарии и итоговые цены, млн руб. без НДС')}
<table class=t><thead><tr><th>Сценарий</th><th class=r>Торг</th><th class=r>Ставки аренды</th><th class=r>Простой, п.п.</th><th class=r>Доходность, п.п.</th>
<th>Срок продажи</th><th class=r>ЯРД</th><th class=r>Калейдоскоп</th><th class=r>Imagine Plaza</th></tr></thead><tbody>{rows}</tbody></table>
<p class=src>Простой и доходность — прибавка к спокойному рынку: дополнительная доля пустующих площадей и надбавка к доходности сделки (около 10–11%). Источники: «Ведомости» (данные IPG.Estate, CMWP,
Bright Rich | Corfac International), IBC Real Estate, РБК Недвижимость, Банк России.</p>
<h2>2.1. Рекомендации продавцу</h2>
{Num.tab('Цена предложения и границы торга, млн руб.')}
<table class=t><thead><tr><th>Объект</th><th class=r>Цена предложения</th><th class=r>Ожидаемая сделка</th><th class=r>Нижняя граница</th>
<th class=r>Стоимость полугода ожидания</th></tr></thead><tbody>{adv}</tbody></table>
<p>На нижнюю границу (сценарий углубления спада) имеет смысл соглашаться, только если продать нужно за 2–3 месяца. Ждать цены спокойного рынка
невыгодно: полгода ожидания обходятся продавцу примерно в 7–8% цены (упущенный доход при ставке 14% и содержание помещения около 1 500 руб./м² за полгода).
Крупные лоты — 680 м² в ЯРД, 524 и 460 м² в Imagine Plaza — лучше делить на блоки по 150–350 м².</p></section>'''


def method_section():
    adj = [('Торг', f'−{BG}% к цене предложения (в спокойном рынке −8%)'),
           ('Удалённость от центра', '−5% за каждый километр, на который аналог ближе к Кремлю'),
           ('Время до метро', '−1% за каждую минуту, на которую аналог ближе к метро; для станций МЦД к пути прибавляется 4 минуты'),
           ('Качество здания', 'оценка по фото: +52% к цене за каждый балл, на который объект лучше аналога, и −34% за балл, на который хуже'),
           ('Этаж и вход', '1 этаж с входом с улицы — +10% к верхним этажам, 1 этаж с входом через холл — +3%'),
           ('Площадь', '−3% за каждое удвоение площади (в аренде −2%)'),
           ('Отделка', 'аналог без отделки +10%, white box +5%, дизайнерский ремонт −5%, продаётся с арендатором −3%')]
    rows = ''.join(f'<tr><td>{a}</td><td>{b}</td></tr>' for a, b in adj)
    return f'''<section class=pb><h1>3. Методика</h1>
<p>Оценка построена так, как ищет помещение покупатель. Он рассматривает офисы в бизнес-центрах класса B у той же станции метро и у соседних двух-трёх станций,
сравнивает здания, удалённость от метро и центра, этаж и отделку. Одновременно он видит альтернативы: доплатить за класс A, купить дешевле
в административном здании или, для 1 этажа, выбрать помещение в жилом доме. Поэтому все объявления разделены на четыре группы, и в расчёт цены
входит только первая — офисы в бизнес-центрах класса B. Остальные группы показаны как ориентиры.</p>
<p>Каждый аналог пересчитывается в объект по одной шкале поправок (таблица {Num.t + 1}). Коэффициенты поправок определены статистически
по ценам и ставкам аренды в рассматриваемых локациях. Каждый бизнес-центр имеет один голос, чтобы здание с десятком объявлений
не перевешивало рынок; аналоги с большими поправками получают меньший вес.</p>
<p>Получаются две оценки. Первая — по ценам продаж похожих офисов, то есть сколько заплатит покупатель для собственного использования. Вторая — по аренде:
рыночная ставка для объекта, делённая на доходность, по которой рынок продаёт офисы. Эту доходность показывают сами объявления: в {V['yields']['n']} бизнес-центрах,
где офисы одновременно продают и сдают, годовая ставка составляет в среднем {n(V['yields']['all'] * 100, 1)}% от цены (у слабых зданий — {n(V['yields']['weak'] * 100, 1)}%);
с учётом торга и спада доходность сделки — {n(V['subjects']['yard']['units_val'][0]['cap_rate'] * 100, 1)}–{n(V['subjects']['imagine']['units_val'][0]['cap_rate'] * 100, 1)}%.
Итог — среднее двух оценок
(если подходящие продажи нашлись меньше чем в пяти зданиях, как для 5 этажа ЯРД, оценке по аренде даётся 60%). Если в самом здании продаётся или сдаётся похожий блок, он учитывается с весом 40%,
а цена объекта не может превышать его цену более чем на 3%.</p>
{Num.tab('Шкала поправок')}<table class=t><thead><tr><th>Фактор</th><th>Поправка</th></tr></thead><tbody>{rows}</tbody></table>
<p class=src>Данные: {n(len(ROWS))} объявлений ЦИАН о продаже и аренде офисов площадью от 80 до 3 000 м² на 14.10.2026; страницы {sum(1 for v in PAGES.values() if v.get('title'))}
бизнес-центров (класс, год, инженерия); экспертная оценка качества {sum(1 for x in RAT.values() if isinstance(x, dict))} зданий по фотографиям; координаты станций метро, МЦК и МЦД.
Исключены строящиеся здания, городские торги и банкротства, ошибки в объявлениях, офисы в жилых комплексах и предложения «готового бизнеса».</p></section>'''


def accuracy_section():
    keep = [('Оценка зданий объектов на 0,25 балла ниже', 'Качество здания объекта на 0,25 балла ниже'),
            ('Оценка зданий объектов на 0,25 балла выше', 'Качество здания объекта на 0,25 балла выше'),
            ('Доходность сделки на 0,5 п.п. ниже', 'Доходность сделки на 0,5 п.п. ниже'),
            ('Доходность сделки на 0,5 п.п. выше', 'Доходность сделки на 0,5 п.п. выше'),
            ('Торг 10% вместо 13%', 'Торг 10% вместо 13%'), ('Торг 16% вместо 13%', 'Торг 16% вместо 13%')]
    by = {x['name']: x for x in CHK['sens']}
    sens = ''.join(f"<tr><td>{label}</td>" + ''.join(f"<td class=r>{sg(by[key][sk], 0)}%</td>" for sk in V['subjects']) + '</tr>'
                   for key, label in keep if key in by)
    rs = [abs(by[key][sk]) for key, _ in keep[:2] if key in by for sk in V['subjects']]
    jack = max(abs(t['change']) for sk in V['subjects'] for t in CHK['jack'][sk]['top'])
    return f'''<section class=pb><h1>7. Точность и ограничения</h1>
<p>Оценка опирается на цены предложений, а не на цены совершённых сделок; переход к цене сделки выполнен через размер торга, который в период спада
определён по данным консультантов. Поэтому ориентироваться стоит не на одно число, а на диапазон между базовым сценарием и сценарием углубления спада (таблица 2).</p>
<p>Результат устойчив к составу аналогов: исключение любого одного бизнес-центра меняет итоговую цену не более чем на {n(jack, 0)}%.
Сильнее всего результат зависит от оценки качества самих зданий: изменение на четверть балла меняет цену на {n(min(rs), 0)}–{n(max(rs), 0)}%.</p>
{Num.tab('Изменение итоговой цены при других допущениях')}
<table class=t><thead><tr><th>Допущение</th><th class=r>ЯРД</th><th class=r>Калейдоскоп</th><th class=r>Imagine Plaza</th></tr></thead><tbody>{sens}</tbody></table>
<p>Объекты не осматривались: площади, высота потолков и состояние помещений приняты по презентациям собственника. Перед выходом на рынок рекомендуется
уточнить тип кондиционирования в БЦ «ЯРД» (по данным ЦИАН — местное, в презентации — центральное) и общую площадь БЦ Imagine Plaza, а также подтвердить
оценку качества зданий осмотром. Юридический статус помещений, обременения и кадастровая стоимость не проверялись.</p></section>'''


def appendix_grid(sk, uk, deal, lim):
    g = V['subjects'][sk]['comps'][uk][f'{deal}_K1']
    f = KS if deal == 'sale' else KR
    shown, per = [], {}
    for c in sorted([c for c in g['comps'] if c.get('used')], key=lambda c: (-c['same'], -c['weight'])):
        key = c['bc_url'] or addr(c['address'])[:30]
        if not c['same'] and per.get(key):
            continue
        per[key] = 1
        shown.append(c)
    rows = ''
    for c in shown[:lim]:
        adj = ', '.join(f"{ADJ_RU[a]} {sg(v)}%" for a, v in c['adj'].items() if abs(v) >= 0.5)
        base = k(c['ex_vat']) if deal == 'sale' else k(c['net'])
        rows += (f"<tr><td>{e(c['bc'] or addr(c['address']))[:38]}{' (то же здание)' if c['same'] else ''}</td><td class=r>{n(c['area'], 0)}</td>"
                 f"<td class=r>{c['floor']}</td><td class=r>{r1(c['rating'])}</td><td class=r>{base}</td><td>{e(adj) or '–'}</td><td class=r>{k(c['adjusted'] * f)}</td></tr>")
    own = f"; лоты самого здания (вес 40%) — {k(g['own'] * f)}" if g.get('own') else ''
    head = 'Цена без НДС, тыс./м²' if deal == 'sale' else 'Ставка, тыс./м² в год'
    return (f"<table class=t><thead><tr><th>Аналог</th><th class=r>м²</th><th class=r>Этаж</th><th class=r>Оценка здания</th><th class=r>{head}</th>"
            f"<th>Поправки</th><th class=r>В пересчёте</th></tr></thead><tbody>{rows}</tbody></table>"
            f"<p class=src>Всего {g['n']} объявлений в {g['n_bc']} зданиях; среднее по рынку — {k(g['market'] * f)}{own}; итог — {k(g['value'] * f)} тыс. руб./м²"
            f"{' в год' if deal == 'rent' else ''}. {'Торг ' + str(BG) + '%.' if deal == 'sale' else 'Ставки без НДС, эксплуатации и коммунальных платежей, со скидкой ' + str(int(MAINP['rent_disc'] * 100)) + '%.'}</p>")


def appendix(letter, sk):
    S = V['subjects'][sk]
    a = REP[sk][0]
    u = U(sk, a)
    inc = ''.join(f"<tr><td>{e(x['name'])}</td><td class=r>{k(x['comp'])}</td><td class=r>{k(x['rent'])}</td><td class=r>{k(x['income'])}</td>"
                  f"<td class=r>{int(x['wc'] * 100)}/{int(100 - x['wc'] * 100)}</td><td class=r>{(sg(x['extra']) + '%') if x.get('extra') else '–'}</td>"
                  f"<td class=r>{k(x['per_m2'])}</td></tr>" for x in S['units_val'])
    return f'''<section class=pb><h1>Приложение {letter}. Расчёт: {e(S['title'])}</h1>
{Num.tab(f"Продажи аналогов, {e(u['name']).lower()}")}{appendix_grid(sk, a, 'sale', 9)}
{Num.tab(f"Аренда аналогов, {e(u['name']).lower()}")}{appendix_grid(sk, a, 'rent', 7)}
{Num.tab('Итог по помещениям, тыс. руб./м²')}<table class=t><thead><tr><th>Помещение</th><th class=r>По продажам</th><th class=r>Ставка аренды в год</th>
<th class=r>По аренде</th><th class=r>Веса, %</th><th class=r>Поправка блока</th><th class=r>Итог</th></tr></thead><tbody>{inc}</tbody></table>
<p class=src>Оценка по аренде = ставка × (1 − дополнительный простой {int(round(u['vac'] * 100))}%) ÷ доходность сделки {n(u['cap_rate'] * 100, 1)}%.
{'Ставка для объекта учитывает более низкое, чем у большинства аналогов, качество здания.' if (S['rating'] or 5) <= 3.0 else ''}</p></section>'''


def contents():
    items = ['1. Основные выводы', '2. Состояние рынка и сценарии', '3. Методика', '4. БЦ «ЯРД»', '5. БЦ «Калейдоскоп»', '6. БЦ Imagine Plaza',
             '7. Точность и ограничения', 'Приложения А–В. Расчёты по объектам', 'Источники']
    return '<ol class=toc>' + ''.join(f'<li>{x}</li>' for x in items) + '</ol>'


def sources():
    return '''<section class=pb><h1>Источники</h1><ol class=refs>
<li>ЦИАН (cian.ru): объявления о продаже и аренде офисов, страницы бизнес-центров; выгрузка 14.10.2026. Ссылки на аналоги — в подписях к рисункам.</li>
<li>«Ведомости»: продажи офисов в Москве за январь–сентябрь 2026 г. (IPG.Estate, Commonwealth Partnership, Bright Rich | Corfac International, ГК «Гарнель»).</li>
<li>IBC Real Estate, А. Ефимов: скидки к ставкам аренды до 18%.</li>
<li>РБК Недвижимость: рост вакантности в неделовых локациях. https://realty.rbc.ru/news/6a4e067f9a7947385ff01d1d</li>
<li>Продажи небольших офисов в Москве выросли на 46%. https://finance.rambler.ru/realty/56422251-prodazhi-nebolshih-ofisov-v-moskve-vyrosli-na-46-s-nachala-2026-goda/</li>
<li>Банк России сохранил ключевую ставку 14%. https://www.retail.ru/news/tsb-rf-sokhranil-klyuchevuyu-stavku-na-urovne-14-godovykh-12-sentyabrya-2026-282051/</li>
<li>Аналитический центр Москвы (ставки аренды класса B); NF Group (доходность офисной недвижимости).</li>
<li>Расположение станций метро, МЦК и МЦД — открытые справочные данные.</li>
<li>Презентации собственника: БЦ «ЯРД», БЦ «Калейдоскоп», БЦ Imagine Plaza.</li></ol></section>'''


CSS = '''
@page { size: A4; margin: 20mm 18mm 18mm 20mm; }
body { font-family: "Carlito", "Liberation Sans", sans-serif; font-size: 10.5pt; color: #111; line-height: 1.42; }
h1 { font-size: 15pt; font-weight: bold; margin: 0 0 8px; padding-bottom: 3px; border-bottom: 0.8pt solid #111; }
h2 { font-size: 11.5pt; font-weight: bold; margin: 14px 0 5px; break-after: avoid; }
p { margin: 0 0 6px; text-align: justify; hyphens: auto; }
a { color: #111; text-decoration: underline; text-decoration-thickness: 0.4pt; }
.pb { page-break-before: always; }
.title { margin-top: 30mm; } .title .t1 { font-size: 22pt; font-weight: bold; line-height: 1.2; margin-bottom: 10px; }
.title .t2 { font-size: 12pt; margin-bottom: 4px; } .title .meta { margin-top: 18mm; font-size: 10.5pt; color: #333; }
.toc { margin: 6px 0 0 0; padding: 0; } .toc li { list-style: none; margin: 2px 0; }
.cap { font-size: 9.5pt; margin: 10px 0 3px; text-align: left; break-after: avoid; }
table.t { width: 100%; border-collapse: collapse; font-size: 9pt; margin: 0 0 4px; border-top: 1.1pt solid #111; border-bottom: 1.1pt solid #111; }
.t thead th { font-weight: bold; text-align: left; padding: 3px 5px; border-bottom: 0.6pt solid #111; vertical-align: bottom; }
.t td { padding: 2.5px 5px; vertical-align: top; }
.t tbody tr + tr td { border-top: 0.3pt solid #c8c8c8; }
.t .r { text-align: right; } .t td.r { white-space: nowrap; } .t tr.tot td { border-top: 0.6pt solid #111; font-weight: bold; }
.t.sw td { width: 50%; }
.src { font-size: 8.5pt; color: #333; margin: 2px 0 10px; text-align: left; }
.gal { display: grid; grid-template-columns: 1.4fr 1fr 1fr; gap: 8px; margin: 8px 0 4px; }
.figs { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px 8px; margin: 8px 0; }
figure { margin: 0; break-inside: avoid; } figure img { width: 100%; height: 104px; object-fit: cover; display: block; }
.gal figure img { height: 120px; } .noimg { height: 104px; background: #eee; }
figcaption { font-size: 8.3pt; line-height: 1.3; margin-top: 3px; color: #222; text-align: left; }
.refs li { margin: 3px 0; font-size: 9.5pt; overflow-wrap: anywhere; }
'''


def build():
    y1, y5 = U('yard', 'y1'), U('yard', 'y5')
    k1, k2 = U('kaleidoscope', 'k1'), U('kaleidoscope', 'k2')
    im = V['subjects']['imagine']['units_val']
    g = [u['per_m2'] for u in im if u['floor'] == 1]
    up = [u['per_m2'] for u in im if u['floor'] > 1]
    drop = {sk: n((1 - TOT(sk) / TOT(sk, 'before')) * 100, 0) for sk in V['subjects']}
    body = f'''
<div class=title><div class=t1>Цена продажи офисных помещений в трёх бизнес-центрах Москвы</div>
<div class=t2>БЦ «ЯРД», 1-й Магистральный тупик, 11, стр. 1</div><div class=t2>БЦ «Калейдоскоп», Хлебозаводский проезд, 7, стр. 5</div>
<div class=t2>БЦ Imagine Plaza, Нагатинская улица, 16, стр. 9</div>
<div class=meta>Аналитическая записка<br>Дата оценки: {DATE}<br>Цены указаны без НДС</div>
<h2 style="margin-top:16mm">Содержание</h2>{contents()}</div>
<section class=pb><h1>1. Основные выводы</h1>
{summary_table()}
<p>Рынок офисов находится в спаде: за год объём продаж снизился на 21–50%, цены сделок — на 13,5%, арендаторы получают скидки до 18%.
Поэтому ожидаемые цены ниже, чем в спокойном рынке: на {drop['yard']}% для ЯРД, {drop['kaleidoscope']}% для «Калейдоскопа» и {drop['imagine']}% для Imagine Plaza.</p>
<p>БЦ «ЯРД» — {mln(TOT('yard'))} млн руб. Лучшая из трёх локаций: Третье транспортное кольцо, Москва-Сити, станция МЦД-4 рядом. Здание уступает соседней
Магистраль Плазе, по данным ЦИАН в нём нет центрального кондиционирования. 1 этаж — {k(y1['per_m2'])} тыс. руб./м², 5 этаж — {k(y5['per_m2'])} тыс.</p>
<p>БЦ «Калейдоскоп» — {mln(TOT('kaleidoscope'))} млн руб. Отдельный вход на 1 этаже и крупный бизнес-центр, но низкие потолки, метро в 23 минутах
и более сильный конкурент в 300–500 м. 1 этаж — {k(k1['per_m2'])} тыс. руб./м², антресоль — {k(k2['per_m2'])} тыс.</p>
<p>БЦ Imagine Plaza — {mln(TOT('imagine'))} млн руб. при продаже по блокам. 1 этаж — {k(min(g))}–{k(max(g))} тыс. руб./м², 2–3 этажи — {k(min(up))}–{k(max(up))} тыс.
Ориентир — соседний блок в этом же здании. Блоки лучше выводить на рынок по очереди.</p>
<p>Наибольшее влияние на результат оказывает качество самих зданий: «Калейдоскоп» и Imagine Plaza уступают большинству соседних бизнес-центров,
ЯРД выигрывает за счёт локации. Оценку качества зданий рекомендуется подтвердить осмотром (раздел 7).</p></section>
{market_section()}
{method_section()}
{object_section(4, 'yard')}
{object_section(5, 'kaleidoscope')}
{object_section(6, 'imagine')}
{accuracy_section()}
{appendix('А', 'yard')}{appendix('Б', 'kaleidoscope')}{appendix('В', 'imagine')}
{sources()}'''
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
               margin=dict(top='20mm', bottom='18mm', left='20mm', right='18mm'), display_header_footer=True,
               header_template='<div style="font-family:Carlito,sans-serif;font-size:7.5pt;color:#555;width:100%;padding:0 18mm 0 20mm;'
                               'display:flex;justify-content:space-between"><span>Цена продажи офисных помещений</span>'
                               '<span>14 октября 2026 г.</span></div>',
               footer_template='<div style="font-family:Carlito,sans-serif;font-size:8pt;color:#555;width:100%;text-align:center">'
                               '<span class="pageNumber"></span></div>')
        b.close()
    print('ok')
