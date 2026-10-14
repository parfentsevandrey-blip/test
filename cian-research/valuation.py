#!/usr/bin/env python3
"""Справедливая цена продажи трёх офисных объектов — глазами покупателя, по кластерам конкурентов.

Покупатель ищет офис в бизнес-центре класса B у тех же или соседних 2–3 станций метро (кластер K1) — это основной ряд аналогов.
Он же видит и альтернативы: доплатить за класс A (K2), сэкономить на административном здании или старом фонде (K3),
для 1 этажей с отдельным входом — помещение на 1 этаже жилого дома (K4). Эти кластеры дают верхнюю и нижнюю границы.

Каждый аналог приводится к объекту одной шкалой поправок (покупатель «пересчитывает» чужой лот в наш):
  торг            −8% к цене предложения;
  удалённость от центра  −4% за каждый км, на который аналог ближе к центру (регрессия по 195 продажам: −5,5%/км);
  метро           −1% за каждую минуту, на которую аналог ближе к метро (МЦД — +4 мин к пути за редкие интервалы);
  здание          6% за балл визуальной оценки здания по фото (1–5: фасад, лобби, инженерия, возраст, потолки);
  этаж/вход       1 этаж с отдельным уличным входом против верхних этажей +10%, 1 этаж с входом через холл БЦ +3%;
                  верхний этаж против 1 этажа −3% (внутри БЦ медианная разница 1 и верхних этажей ≈ 0–3%), против 1 этажа с
                  отдельным входом −10%;
  масштаб         −3% за каждое удвоение площади;
  отделка         аналог без отделки +10%, white box +5%, дизайнерский ремонт −5%; продаётся с арендатором −3%.
Вес аналога = 1 / (5% + сумма модулей поправок); у одного БЦ — один вес (несколько лотов БЦ не перевешивают рынок),
лоты в том же здании, что и объект, весят вдвое. Аналоги с суммой поправок > 60% не используются.

Доходный подход: рыночная «чистая» ставка (без НДС, эксплуатации и коммуналки) из K1-аренды по той же шкале поправок →
NOI = ставка × (1 − недозагрузка) − невозмещаемые расходы собственника → стоимость = NOI / ставка капитализации.

python3 valuation.py → commercial-data/valuation.json
"""
import json
import math
import re
import statistics as st

import bc_profiles as bp

VAT = 1.22
BARGAIN = 0.92
OWNER = 3000                 # ₽/м²/год: налог на имущество, страховка, управление, резерв
MCD_PENALTY = 4              # мин: МЦД ходит реже метро
EXCLUDE = {326550288: 'Магистраль Плаза: 532 787 ₽/м² без НДС — вдвое выше соседних лотов того же БЦ (ошибка цены/дубль)',
           332583818: 'Магистраль Плаза: дубль того же лота по 532 787 ₽/м²',
           331316322: '1 Zhukov: 1 000 000 ₽/м² — вне рынка (в 3–4 раза выше района)'}

rows = {r['id']: r for r in json.load(open('commercial-data/offers.json'))}
CL = json.load(open('commercial-data/clusters.json'))
PROF = json.load(open('commercial-data/bc_profiles.json'))
RAT = json.load(open('commercial-data/bc_ratings.json'))
BCPROF = {p['url']: p for v in PROF.values() for p in v['bcs'] if p['url']}

SUBJECTS = {
    'yard': dict(
        title='БЦ «ЯРД»', address='Москва, 1-й Магистральный тупик, 11, стр. 1', district='Хорошёвский (САО)',
        url='https://www.cian.ru/biznes-centr-yard-moskva-5416/', same=r'1-й Магистральный (тупик|туп)\.?,?\s*(д\.?\s*)?11',
        cls='B+ в справочнике ЦИАН (B в карточках объявлений), 1958 г., реконструкция 2009 г., 5 этажей, кирпич, 6 241 м², парковка 28 мест',
        tech='вентиляция приточная, кондиционирование местное (не центральное, как в презентации), 2 лифта, СКУД, кафе, отделение банка',
        units=[dict(key='y1', name='Офис 1 этаж', area=303.8, floor=1, entrance='lobby', fit='bank',
                    note='кабинетная планировка, оборудован под банк/депозитарий, вход через холл БЦ'),
               dict(key='y5', name='Офис 5 этаж', area=680.0, floor=5, entrance='lobby', fit='standard',
                    note='верхний этаж, open space + кабинеты, два лестничных узла')],
        cap=0.115),
    'kaleidoscope': dict(
        title='БЦ «Калейдоскоп»', address='Москва, Хлебозаводский проезд, 7, стр. 5', district='Нагатино-Садовники (ЮАО)',
        url=None, same=r'Хлебозаводск',
        cls='класс B, 1995 г., 3 лифта OTIS, центральное кондиционирование, потолки 2,37–2,57 м', tech='',
        units=[dict(key='k1', name='1 этаж', area=394.9, floor=1, entrance='street', fit='standard', extra=-3,
                    note='отдельный вход с улицы, высота потолка 2,57 м (−3%: ниже нормы офиса 2,7–3 м)'),
               dict(key='k2', name='2 этаж (антресоль)', area=401.3, floor=2, entrance='lobby', fit='standard', extra=-7,
                    note='коридорно-кабинетная планировка, высота потолка 2,37 м (−7%: нельзя смонтировать подвесной потолок и '
                         'воздуховоды, круг покупателей заметно уже)')],
        cap=0.125),
    'imagine': dict(
        title='БЦ Imagine Plaza', address='Москва, Нагатинская ул., 16, стр. 9', district='Нагатино-Садовники (ЮАО)',
        url='https://www.cian.ru/biznes-centr-imagine-plaza-moskva-42854/', same=r'Нагатинская\s+(ул\.?|улица)?,?\s*(д\.?\s*)?16',
        cls='B+ в справочнике ЦИАН (B в карточке здания), реконструированный промышленный корпус 1974 г., 3 этажа, потолки ~4 м', tech='',
        units=[dict(key='i1', name='№1, 1 этаж', area=168.2, floor=1, entrance='street', fit='standard'),
               dict(key='i2', name='№2, 1 этаж', area=230.0, floor=1, entrance='street', fit='standard'),
               dict(key='i3', name='№3, 1 этаж', area=524.0, floor=1, entrance='street', fit='standard'),
               dict(key='i4', name='№4, 2 этаж', area=182.3, floor=2, entrance='lobby', fit='standard'),
               dict(key='i5', name='№5, 2 этаж', area=460.2, floor=2, entrance='lobby', fit='standard'),
               dict(key='i6', name='№6, 3 этаж, балкон', area=199.9, floor=3, entrance='lobby', fit='standard', extra=3,
                    note='+3% за собственный балкон'),
               dict(key='i7', name='№7, 3 этаж, балкон', area=195.0, floor=3, entrance='lobby', fit='standard', extra=3,
                    note='+3% за собственный балкон'),
               dict(key='i8', name='№8, 3 этаж, балкон', area=173.4, floor=3, entrance='lobby', fit='standard', extra=3,
                    note='+3% за собственный балкон')],
        cap=0.12),
}
DEFAULT_RATING = {'K1': 3.0, 'K2': 4.5, 'K3': 2.5, 'K4': None}


def eff_walk(lat, lng):
    """Пешая доступность с учётом МЦД: минимум по двум ближайшим станциям."""
    best = None
    for s in bp.nearest((lat, lng), 3):
        w = s['walk'] + (MCD_PENALTY if s['line'].startswith('МЦД') else 0)
        if best is None or w < best[0]:
            best = (w, s)
    return best[0], best[1]


def fit_of(r):
    d = (r.get('description') or '').lower()
    if re.search(r'без отделки|черновая|под отделку|shell|без ремонта|под ремонт|требует\w* ремонт|в бетоне', d):
        return 'shell'
    if re.search(r'white ?box|вайт ?бокс|предчистов|под чистовую', d):
        return 'white'
    if re.search(r'дизайнерск|премиальн\w+ ремонт|премиум[- ]?отделк|эксклюзивн\w+ ремонт', d):
        return 'premium'
    return 'standard'


def tenant_of(r):
    d = (r.get('description') or '').lower()
    return bool(re.search(r'арендатор\w*\s+(уже|есть|работа|заключ|на )|с арендатор|сдан\w*\s+(в аренду|арендатор)|арендный бизнес|габ\b|'
                          r'готовый (арендный )?бизнес|действующ\w+ арендатор', d))


def sep_entrance(r):
    return bool(re.search(r'отдельн\w+ вход|отдельным входом|вход с улицы|витрин', (r.get('description') or '').lower()))


def comp_attrs(r):
    p = BCPROF.get(r.get('bc_url') or '') or {}
    lat, lng = (p.get('lat'), p.get('lng')) if p.get('lat') else (r['lat'], r['lng'])
    w, s = eff_walk(lat, lng)
    rt = (RAT.get(r.get('bc_url') or '') or RAT.get(f"id:{r['id']}") or {}).get('rating')
    return dict(walk=w, station=s['name'], station_line=s['line'], center_km=round(bp.km((lat, lng), bp.CENTER), 1),
                rating=rt if rt is not None else DEFAULT_RATING[r['cluster']], rating_src='фото' if rt is not None else 'по умолч.',
                fit=fit_of(r), tenant=tenant_of(r), sep=sep_entrance(r))


def adjustments(S, u, r, a, deal):
    adj = {}
    adj['loc'] = max(-20, min(20, 4 * (a['center_km'] - S['center_km'])))
    adj['metro'] = max(-15, min(15, 1 * (a['walk'] - S['walk'])))
    if a['rating'] is not None and S['rating'] is not None:
        adj['bld'] = max(-18, min(18, 6 * (S['rating'] - a['rating'])))
    if u['floor'] == 1:
        if r['floor'] != 1:
            adj['fl'] = 10 if u['entrance'] == 'street' else 3
    elif r['floor'] == 1:
        adj['fl'] = -10 if a['sep'] else -3
    k = 3 if deal == 'sale' else 2
    adj['size'] = max(-9, min(9, -k * math.log2(u['area'] / r['area'])))
    fit = 0
    if deal == 'sale':
        fit = {'shell': 10, 'white': 5, 'premium': -5, 'standard': 0}[a['fit']]
        if a['tenant']:
            fit -= 3
    elif a['fit'] == 'shell':
        fit = 5
    if u['fit'] == 'bank':
        fit -= 5              # банковскую отделку покупателю под обычный офис придётся частично переделать
    if fit:
        adj['fit'] = fit
    return {k: round(v, 1) for k, v in adj.items() if abs(v) >= 0.05}


def comp_row(S, u, cid, deal):
    r = rows[cid]
    a = comp_attrs(r)
    adj = adjustments(S, u, r, a, deal)
    k = 1.0
    for v in adj.values():
        k *= 1 + v / 100
    gross = sum(abs(v) for v in adj.values()) / 100
    same = bool(re.search(S['same'], r.get('address') or '')) if S.get('same') else False
    base = r['ex_vat'] * BARGAIN if deal == 'sale' else r['net']
    w = 1 / (0.05 + gross) * (2 if same else 1)
    return dict(id=cid, deal=deal, cluster=r['cluster'], cls=r.get('cls'), bc=r.get('bc'), bc_url=r.get('bc_url'), address=r.get('address'),
                area=r['area'], floor=r['floor'], price=r['price'], per_m2=r['per_m2'], vat=r['vat'], ex_vat=r['ex_vat'],
                net=r.get('net'), opex_in=r.get('opex_in'), util_in=r.get('util_in'), base=round(base), adj=adj, k=round(k, 3),
                gross=round(gross, 3), adjusted=round(base * k), weight=round(w, 2), same=same, url=r['url'], photos=r['photos'][:2],
                year=r.get('year'), **a)


def sane(r):
    """Отсекает ошибки ввода: «аренда» с ценой продажи, копеечные ставки, цены вне рынка."""
    if r['deal'] == 'rent':
        return 4000 <= (r.get('net') or 0) <= 150000
    return 50000 <= (r.get('ex_vat') or 0) <= 1500000


def pool(sk, cl, deal, u):
    ids = CL[sk]['clusters'][cl]['sale_ids' if deal == 'sale' else 'rent_ids']
    out = []
    for i in ids:
        r = rows[i]
        if i in EXCLUDE or r.get('offplan') or r.get('nonmarket'):
            continue
        if not (u['area'] / 4 <= r['area'] <= u['area'] * 4):
            continue
        if not sane(r):
            continue
        if cl == 'K4' and r['floor'] != 1:
            continue
        out.append(i)
    return out


SAME_SHARE = 0.4             # доля лотов в самом здании/комплексе объекта, если они есть


def reconcile(comps, max_gross=0.6):
    """Рынок: один вес на БЦ (значение БЦ — средневзвешенное его лотов, вес — максимальный вес лота).
    Лоты в самом здании объекта — отдельная группа с долей SAME_SHARE: это прямое свидетельство цены для покупателя."""
    used = [c for c in comps if c['gross'] <= max_gross]
    for c in comps:
        c['used'] = False
    if used:
        m = st.median(c['adjusted'] for c in used)
        for c in used:
            c['outlier'] = not c['same'] and not (m / 2.5 <= c['adjusted'] <= m * 2.5)
        used = [c for c in used if not c['outlier']]
    groups = {}
    for c in used:
        c['used'] = True
        if c['same']:
            continue
        g = c['bc_url'] or re.sub(r'\s+', ' ', (c['address'] or '')[:45])
        groups.setdefault(g, []).append(c)
    num = den = 0
    for g, cs in groups.items():
        v = sum(c['adjusted'] * c['weight'] for c in cs) / sum(c['weight'] for c in cs)
        w = max(c['weight'] for c in cs)
        for c in cs:
            c['group_w'] = round(w / len(cs), 2)
        num += v * w
        den += w
    market = num / den if den else None
    same = [c for c in used if c['same']]
    own = sum(c['adjusted'] for c in same) / len(same) if same else None
    if own and market:
        value = SAME_SHARE * own + (1 - SAME_SHARE) * market
    else:
        value = own or market
    vals = [c['adjusted'] for c in used]
    return dict(value=round(value) if value else None, market=round(market) if market else None, own=round(own) if own else None,
                n=len(used), n_bc=len(groups), n_same=len(same), n_out=sum(1 for c in comps if c.get('outlier')),
                median=round(st.median(vals)) if vals else None,
                q1=round(sorted(vals)[len(vals) // 4]) if vals else None, q3=round(sorted(vals)[(3 * len(vals)) // 4]) if vals else None)


def subject_attrs(sk, S):
    lat, lng = bp.SUBJ[sk]
    w, s = eff_walk(lat, lng)
    rt = (RAT.get(S['url'] or '') or RAT.get(sk) or {}).get('rating')
    return dict(walk=w, station=s['name'], station_line=s['line'], near=bp.nearest((lat, lng), 3),
                center_km=round(bp.km((lat, lng), bp.CENTER), 1), rating=rt)


def cluster_raw(sk):
    out = {}
    for cl, c in CL[sk]['clusters'].items():
        sale = [rows[i] for i in c['sale_ids'] if i not in EXCLUDE and not rows[i].get('offplan') and not rows[i].get('nonmarket') and sane(rows[i])]
        rent = [rows[i] for i in c['rent_ids'] if not rows[i].get('offplan') and sane(rows[i])]
        off = [rows[i] for i in c['sale_ids'] if rows[i].get('offplan')]
        out[cl] = dict(name=c['name'], sale=_stats([r['ex_vat'] for r in sale]), sale_1=_stats([r['ex_vat'] for r in sale if r['floor'] == 1]),
                       sale_up=_stats([r['ex_vat'] for r in sale if r['floor'] >= 2]), rent=_stats([r['net'] for r in rent]),
                       rent_1=_stats([r['net'] for r in rent if r['floor'] == 1]), rent_up=_stats([r['net'] for r in rent if r['floor'] >= 2]),
                       offplan=_stats([r['ex_vat'] for r in off]),
                       n_bc_sale=len({r.get('bc_url') or r['address'] for r in sale}), n_bc_rent=len({r.get('bc_url') or r['address'] for r in rent}))
    return out


def _stats(v):
    v = sorted(x for x in v if x)
    if not v:
        return None
    return dict(n=len(v), med=round(st.median(v)), q1=round(v[len(v) // 4]), q3=round(v[(3 * len(v)) // 4]), lo=v[0], hi=v[-1])


def main():
    out = {'params': dict(vat=VAT, bargain=BARGAIN, owner=OWNER, mcd_penalty=MCD_PENALTY), 'exclude': EXCLUDE, 'subjects': {}}
    for sk, s in SUBJECTS.items():
        S = dict(s, **subject_attrs(sk, s))
        S['clusters'] = cluster_raw(sk)
        S['units_val'] = []
        S['comps'] = {}
        for u in s['units']:
            res = {}
            for deal in ('sale', 'rent'):
                for cl in ('K1', 'K2', 'K3', 'K4'):
                    if cl == 'K4' and (u['floor'] != 1 or u['entrance'] != 'street'):
                        continue          # ПСН в жилом доме — альтернатива только 1 этажу с уличным входом
                    comps = sorted((comp_row(S, u, i, deal) for i in pool(sk, cl, deal, u)), key=lambda c: -c['weight'])
                    rec = reconcile(comps)
                    res[f'{deal}_{cl}'] = dict(rec, comps=comps)
            S['comps'][u['key']] = res
            comp_v = res['sale_K1']['value']
            rent = res['rent_K1']['value']
            vac = 0.08 if u['area'] < 400 else 0.10
            noi = rent * (1 - vac) - OWNER
            inc_v = noi / S['cap']
            wc = 0.5 if res['sale_K1']['n_bc'] >= 5 else 0.4
            per = wc * comp_v + (1 - wc) * inc_v
            # покупатель не заплатит за наш блок заметно больше, чем за такой же блок в этом же здании (после торга и поправок)
            own_bld = [c for c in res['sale_K1']['comps'] if c['same'] and S.get('url') and c['bc_url'] == S['url']]
            cap_own = round(sum(c['adjusted'] for c in own_bld) / len(own_bld) * 1.03) if own_bld else None
            capped = bool(cap_own and per > cap_own)
            if capped:
                per = cap_own
            per *= 1 + u.get('extra', 0) / 100
            per = round(per / 1000) * 1000
            total = per * u['area']
            S['units_val'].append(dict(u, comp=comp_v, rent=rent, vac=vac, noi=round(noi), income=round(inc_v), wc=wc, per_m2=per,
                                       cap_own=cap_own, capped=capped,
                                       total=round(total), lo=round(total * 0.92), hi=round(total * 1.08), list_price=round(total / BARGAIN, -5),
                                       gross_yield=round(rent / per * 100, 1),
                                       ref={k: res[k]['value'] for k in res if k != 'sale_K1' and k != 'rent_K1'}))
        out['subjects'][sk] = S
    json.dump(out, open('commercial-data/valuation.json', 'w'), ensure_ascii=False, indent=1)
    for sk, S in out['subjects'].items():
        print(f"## {S['title']}: метро {S['station']} {S['walk']} мин (эфф.), до центра {S['center_km']} км, оценка здания {S['rating']}")
        for u in S['units_val']:
            c = S['comps'][u['key']]
            ref = ' | '.join(f"{k} {v:,}" if v else f'{k} —' for k, v in u['ref'].items())
            print(f"  {u['name']:20} {u['area']:>6}: K1 прод {u['comp']:,} (лотов {c['sale_K1']['n']}, БЦ {c['sale_K1']['n_bc']}) | "
                  f"K1 аренда {u['rent']:,} (лотов {c['rent_K1']['n']}, БЦ {c['rent_K1']['n_bc']}) → доход {u['income']:,} → "
                  f"{u['per_m2']:,} ₽/м² = {u['total'] / 1e6:,.1f} млн")
            print(f"      ориентиры: {ref}")
        print(f"  ИТОГО {sum(u['total'] for u in S['units_val']) / 1e6:,.1f} млн ₽")


if __name__ == '__main__':
    main()
