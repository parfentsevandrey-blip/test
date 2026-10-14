#!/usr/bin/env python3
"""Кластеры конкурентов для оценки офисов (глазами покупателя).

Локация — станции метро: ближайшие к объекту + соседние 2–3 станции.
Кластеры по типу недвижимости:
  K1 — офис в бизнес-центре класса B/B+/B− (основной: так ищет покупатель офиса в БЦ класса B);
  K2 — офис в бизнес-центре класса A/A+ (верхняя граница — «что можно купить, если доплатить»);
  K3 — офис в административном/офисном здании, особняке, класс C или без класса (нижняя граница);
  K4 — помещение на 1 этаже жилого дома/ЖК (ПСН, стрит-ритейл) — альтернатива только для 1 этажей с отдельным входом;
  K0 — прочее (ТЦ, склады) — в анализ не входит.
Ставки аренды нормализуются к «чистой» (без НДС, эксплуатации и коммуналки) по тексту объявления и, где есть, по карточке.

python3 clusters.py  → commercial-data/clusters.json
"""
import json
import os
import re
import statistics as st

VAT = 1.22
OPEX, UTIL = 5000, 1500
BC_TYPES = {'businessCenter', 'businessCenter2', 'businessPark', 'businessQuarter', 'businessQuarter2', 'officeQuarter',
            'officeCenter', 'multifunctionalComplex', 'businessHouse', 'technopark', 'officeAndHotelComplex', 'tradingOfficeComplex'}
ADM_TYPES = {'administrativeBuilding', 'officeBuilding', 'standaloneBuilding', 'mansion', 'free', 'old', 'other', 'officeAndWarehouseComplex'}
RES_TYPES = {'residentialHouse', 'residentialComplex', 'officeAndResidentialComplex'}
SHOP_TYPES = {'shoppingCenter', 'shoppingComplex', 'shoppingAndEntertainmentCenter', 'outlet', 'specializedShoppingCenter',
              'shoppingAndWarehouseComplex', 'shoppingAndBusinessComplex', 'warehouse', 'industrialWarehouseComplex'}
CITY = ('Деловой центр', 'Москва-Сити', 'Выставочная', 'Международная')

# станции: ближайшие + соседние 2–3 (по линиям и по МЦК/БКЛ)
STATIONS = {
    'yard': dict(core=['Шелепиха', 'Хорошёвская', 'Беговая', 'Полежаевская'],
                 ring=['Хорошёво', 'Звенигородская', 'Улица 1905 года', 'Народное Ополчение', 'ЦСКА', 'Петровский Парк', 'Динамо',
                       'Зорге', 'Октябрьское поле', 'Мнёвники']),
    'kaleidoscope': dict(core=['Нагорная', 'Нагатинская', 'Каширская'],
                         ring=['Варшавская', 'Коломенская', 'Нахимовский проспект', 'Верхние котлы', 'Тульская', 'Кантемировская',
                               'Москворечье']),
    'imagine': dict(core=['Нагатинская', 'Коломенская', 'Нагорная'],
                    ring=['Технопарк', 'Нагатинский Затон', 'Каширская', 'Верхние котлы', 'Тульская', 'Автозаводская', 'ЗИЛ']),
}
CLUSTER_RU = {'K1': 'Офисы в БЦ класса B/B+', 'K2': 'Офисы в БЦ класса A/A+', 'K3': 'Офисы в административных зданиях, особняках, класс C',
              'K4': 'Помещения на 1 этаже жилых домов (ПСН)', 'K0': 'Прочее (ТЦ, склады)'}

NEG = r'(не\s+включ|оплачива\w+\s+отдельно|без\s+уч[её]та|дополнительно|\+\s*эксплуат|\+\s*коммун|сверху)'


def terms_from_text(desc):
    """→ (эксплуатация включена?, коммуналка включена?) по тексту; None — не сказано."""
    t = (desc or '').lower()
    opex = util = None
    for m in re.finditer(r'[^.\n;]{0,80}(эксплуатац|opex|опекс)[^.\n;]{0,80}', t):
        s = m.group(0)
        if re.search(NEG, s):
            opex = False if opex is None else opex
        elif re.search(r'включ|с уч[её]том', s):
            opex = True
    for m in re.finditer(r'[^.\n;]{0,80}коммунал[^.\n;]{0,80}', t):
        s = m.group(0)
        if re.search(NEG, s):
            util = False if util is None else util
        elif re.search(r'включ|с уч[её]том', s):
            util = True
    if re.search(r'вс[её]\s+включено|all\s*inclusive', t):
        opex = True if opex is None else opex
        util = True if util is None else util
    return opex, util


BCLS = {}         # класс здания по большинству объявлений
RES_BLD = set()   # здания, которые хотя бы одна карточка называет жилым/офисно-жилым комплексом


def norm_addr(a):
    a = (a or '').lower().replace('ё', 'е')
    for x in ('россия', 'москва город', 'москва', 'город', 'вн/тер-г', 'муниципальный округ'):
        a = a.replace(x, ' ')
    a = re.sub(r'\bг\.?\s', ' ', a)
    a = re.sub(r'\bд(ом)?\.?\s*(?=\d)', ' ', a)
    a = re.sub(r'\bкорпус\s*|\bкорп\.?\s*|\bк\.?\s+(?=\d)', 'к', a)
    a = re.sub(r'\bстроение\s*|\bстр\.?\s*', 'с', a)
    a = re.sub(r'\b(ул|улица|пр-кт|проспект|пер|переулок|ш|шоссе|наб|набережная|проезд|пр|туп|тупик|аллея|б-р|бульвар)\b\.?', '', a)
    return re.sub(r'[^0-9а-яa-z]', '', a)


def bkey(r):
    return r.get('bc_url') or re.sub(r'[^0-9а-яё]', '', (r.get('address') or '').lower().replace('россия', '').replace('москва', ''))[:40]


def cls_of(r):
    if bkey(r) in BCLS:
        return BCLS[bkey(r)]
    prof = BCP.get(r.get('bc_url') or '') or {}
    if prof.get('cls') in ('A', 'A+', 'B', 'B+', 'B-', 'C'):
        return prof['cls'].replace('-', '−')
    c = r.get('bc_dir_class') or r.get('b_class')
    return {'a': 'A', 'aPlus': 'A+', 'b': 'B', 'bPlus': 'B+', 'bMinus': 'B−', 'c': 'C'}.get(c)


FLAT = r'\d-комнатн\w+ квартир|\bкомнатн\w+ квартир|апартамент'
IN_RES = (r'\bв\s+(жилом\s+(доме|комплексе|квартале)|жк\b)|в составе жил|на (первом|1-?м|цокольном) этаже жил|'
          r'(престижн\w+|элитн\w+|клубн\w+|новом)\s+жил\w+\s+(комплекс|дом)|жилой комплекс\s+(бизнес|премиум|комфорт|элит)|'
          r'\bжк\s*[«"]|жилого (дома|комплекса)')
CLASS_A = r'класса\s*[«"]?a\+?\b|класса\s*[«"]?а\+?[»"]?(\s|,|\.)|класс\s*[«"]?а\+?[»"]?(\s|,|\.)|небоск|премиальн\w+ офисн'
OFFPLAN = r'строящ|сдача\s+(в\s+)?(\d\s*кв|20(2[7-9]|3\d))|ввод\w*\s+в\s+эксплуатац\w+\s+.{0,20}20(2[7-9]|3\d)|по\s+дду|договор\w*\s+долев'
# ручные исправления по фото и тексту: офисы в жилых комплексах, ошибочно помеченные как БЦ
CLUSTER_OVERRIDE = {329563138: 'K4', 331609720: 'K4',     # Ходынский б-р, 2 — этаж в ЖК «Лайнер» (жилой комплекс)
                    325162467: 'K4',                      # Беговая, 18к2 — 1 этаж 12-этажного жилого дома, свой вход
                    333315805: 'K4'}                      # Хорошёвское ш., 12к1 — офис в стилобате 37-этажного жилого комплекса
BCP = {}          # профили БЦ со страниц ЦИАН (bc_pages.json), ключ — url страницы БЦ


def cluster_of(r):
    """K1 — БЦ класса B; K2 — БЦ класса A и новые офисные башни; K3 — административные здания, особняки, старый фонд, класс C;
    K4 — коммерция в жилых домах/ЖК; K0 — ТЦ, склады, квартиры (вне анализа)."""
    bt = r.get('b_type') or r.get('bc_type')
    c = cls_of(r)
    desc = (r.get('description') or '').lower()
    prof = BCP.get(r.get('bc_url') or '') or {}
    title = (prof.get('title') or '').lower()
    year, floors = r.get('year') or 0, r.get('b_floors') or r.get('floors') or 0
    if bt in SHOP_TYPES or re.search(FLAT, desc[:400]):
        return 'K0'
    if bt in RES_TYPES or (bt not in BC_TYPES and re.search(IN_RES, desc)):
        return 'K4'
    if re.search(r'особняк', desc[:300]):
        return 'K3'
    if c in ('A', 'A+') or (c is None and (year >= 2000 or not year) and re.search(CLASS_A, desc)):
        return 'K2'
    if title.startswith(('административное', 'особняк', 'здание')) or bt in ADM_TYPES or c == 'C':
        return 'K3'
    is_bc = (bt in BC_TYPES or title.startswith('бизнес') or bool(r.get('bc_url')) or
             re.search(r'(в|на территории|в составе|расположен\w* в)\s+(бизнес[- ]центр\w*|бц\b|бизнес[- ]парк\w*|деловом квартале)|'
                       r'бизнес[- ]центр\w* класса', desc))
    if is_bc or c in ('B', 'B+', 'B−'):
        if c is None and (year >= 2015 or floors >= 12):
            return 'K2'          # новая офисная башня без указанного класса — по факту класс A
        return 'K1' if c in ('B', 'B+', 'B−') or floors >= 3 else 'K3'
    if re.search(r'особняк|административн|офисное здание|отдельно стоящ', desc):
        return 'K3'
    if r.get('category', '').startswith('freeAppointment') and (r.get('floor') == 1):
        return 'K4'
    return 'K3' if 'office' in (r.get('category') or '') else 'K0'


OFFPLAN_STOP = {'сплит', 'bell', 'mind'}   # названия, совпадающие с обычными словами («сплит-система»)
OFFPLAN_NAMES = []   # названия строящихся БЦ (год сдачи > 2026 на странице БЦ) — для объявлений без привязки к БЦ


def offplan(r):
    d = (r.get('description') or '').lower()
    prof = BCP.get(r.get('bc_url') or '') or {}
    py = int(str(prof.get('year'))[:4]) if str(prof.get('year') or '')[:4].isdigit() else 0
    return ((r.get('year') or 0) > 2026 or py > 2026 or bool(re.search(OFFPLAN, d)) or
            (not r.get('year') and any(re.search(r'(?<![\wа-яё])' + re.escape(nm) + r'(?![\wа-яё-])', d[:600]) for nm in OFFPLAN_NAMES)))


def enrich(r):
    r['cls'] = cls_of(r)
    r['cluster'] = CLUSTER_OVERRIDE.get(r['id']) or cluster_of(r)
    if r['cluster'] in ('K1', 'K2', 'K3') and not r.get('bc_url') and norm_addr(r.get('address')) in RES_BLD:
        r['cluster'] = 'K4'          # офис в жилом/офисно-жилом комплексе — не бизнес-центр
    if (r.get('category') or '').startswith('business'):
        r['cluster'] = 'K0'          # «готовый бизнес»: в цене может быть стоимость бизнеса
    r['offplan'] = offplan(r)
    # нерыночные условия: торги, банкротство, обременение, доля — в аналоги не берём
    d = (r.get('description') or '').lower()
    r['nonmarket'] = bool(re.search(r'(городских|на|в|результатам|победитель)\s+торг(ах|ов|и)\b|гис торги|участия в торгах|аукцион по|'
                                    r'фз 127|несостоятел|конкурсн\w+ управл', d)) and 'не торги' not in d
    r['city'] = r.get('metro') in CITY
    ex = r['per_m2'] / VAT if r['vat'] == 'included' else r['per_m2']
    r['ex_vat'] = round(ex)
    if r['deal'] == 'rent':
        opex, util = terms_from_text(r.get('description'))
        if r.get('utilities') and r['utilities'].get('includedInPrice'):
            util = True
        r['opex_in'], r['util_in'] = opex, util
        # неизвестно → считаем, что эксплуатация включена (типичная котировка БЦ класса B), коммуналка — нет
        r['net'] = round(ex - (OPEX if opex in (True, None) else 0) - (UTIL if util else 0))
        r['terms_known'] = opex is not None
    st_ = r.get('undergrounds') or []
    r['walk_min'] = next((u['min'] for u in st_ if u.get('walk')), None)
    return r


def in_area(r, key):
    s = STATIONS[key]
    names = [u['name'] for u in (r.get('undergrounds') or [])[:2]]
    if not names:
        return None
    if names[0] in s['core']:
        return 'core'
    if names[0] in s['ring'] or (len(names) > 1 and names[1] in s['core']):
        return 'ring'
    return None


def q(v, p):
    v = sorted(v)
    k = (len(v) - 1) * p
    f = int(k)
    return v[f] + (v[min(f + 1, len(v) - 1)] - v[f]) * (k - f)


def stats(vals):
    vals = [v for v in vals if v]
    if not vals:
        return None
    return dict(n=len(vals), med=round(st.median(vals)), q1=round(q(vals, .25)), q3=round(q(vals, .75)),
                lo=round(min(vals)), hi=round(max(vals)))


def main():
    if os.path.exists('commercial-data/bc_pages.json'):
        BCP.update(json.load(open('commercial-data/bc_pages.json')))
    for v in BCP.values():
        y = str(v.get('year') or '')[:4]
        if y.isdigit() and int(y) > 2026 and v.get('title'):
            nm = re.sub(r'^(бизнес-центр|деловой центр|офисное здание|бизнес-квартал)\s+', '', v['title'].lower())
            nm = re.sub(r'\s*\(.*', '', nm).strip(' «»"')
            if len(nm) >= 4 and nm not in OFFPLAN_STOP:
                OFFPLAN_NAMES.append(nm)
    rows = json.load(open('commercial-data/offers.json'))
    for r in rows:                      # строящиеся БЦ, известные по другим объявлениям
        if (r.get('year') or 0) > 2026 and r.get('bc'):
            nm = re.sub(r'\s*\(.*', '', r['bc'].lower()).strip(' «»"')
            if len(nm) >= 4 and nm not in OFFPLAN_NAMES and nm not in OFFPLAN_STOP:
                OFFPLAN_NAMES.append(nm)
    # класс здания — по большинству объявлений того же здания (одиночные ошибки в карточках не перекидывают здание в другой класс)
    votes = {}
    for r in rows:
        c = cls_of(r)
        if c:
            votes.setdefault(bkey(r), []).append(c)
    BCLS.clear()
    BCLS.update({k: max(set(v), key=v.count) for k, v in votes.items()})
    RES_BLD.clear()
    RES_BLD.update(norm_addr(r.get('address')) for r in rows
                   if r.get('b_type') in RES_TYPES or re.search(r'офисно-жил\w+ комплекс|коммерческ\w+ част\w+ (жк|жил)|в жилом доме',
                                                                (r.get('description') or '').lower()))
    rows = [enrich(r) for r in rows]
    json.dump(rows, open('commercial-data/offers.json', 'w'), ensure_ascii=False, indent=0)
    R.clear()
    R.update({r['id']: r for r in rows})
    out = {}
    for key in STATIONS:
        area = [dict(r, zone=in_area(r, key)) for r in rows if in_area(r, key) and 80 <= r['area'] <= 3000 and (r['floor'] or 0) >= 1]
        res = dict(clusters={}, bcs={})
        for cl in ('K1', 'K2', 'K3', 'K4'):
            s = [r for r in area if r['cluster'] == cl]
            sale = [r for r in s if r['deal'] == 'sale']
            rent = [r for r in s if r['deal'] == 'rent']
            res['clusters'][cl] = dict(
                name=CLUSTER_RU[cl],
                sale=stats([r['ex_vat'] for r in sale]), sale_1=stats([r['ex_vat'] for r in sale if r['floor'] == 1]),
                sale_up=stats([r['ex_vat'] for r in sale if r['floor'] >= 2]),
                rent_net=stats([r['net'] for r in rent]), rent_net_1=stats([r['net'] for r in rent if r['floor'] == 1]),
                rent_net_up=stats([r['net'] for r in rent if r['floor'] >= 2]),
                sale_ids=[r['id'] for r in sale], rent_ids=[r['id'] for r in rent])
        # профили бизнес-центров (K1, K2) в локации
        bcs = {}
        for r in area:
            if r['cluster'] not in ('K1', 'K2') or not (r.get('bc_id') or r.get('bc')):
                continue
            k = str(r.get('bc_id') or r.get('bc'))
            b = bcs.setdefault(k, dict(name=r.get('bc'), cls=r['cls'], cluster=r['cluster'], year=r.get('year'), floors=r.get('b_floors'),
                                       metro=r.get('metro'), walk=r.get('walk_min'), url=r.get('bc_url'), address=r.get('address'),
                                       sale=[], rent=[], zone=r['zone'], lat=r.get('lat'), lng=r.get('lng'), photo_offer=None))
            b['sale' if r['deal'] == 'sale' else 'rent'].append(r['id'])
            if r.get('photos') and not b['photo_offer']:
                b['photo_offer'] = r['id']
        for k, b in bcs.items():
            b['sale_med'] = round(st.median([R[i]['ex_vat'] for i in b['sale']])) if b['sale'] else None
            b['rent_med'] = round(st.median([R[i]['net'] for i in b['rent']])) if b['rent'] else None
        res['bcs'] = bcs
        res['n_area'] = len(area)
        out[key] = res
        print(f'## {key}: объявлений в локации {len(area)}, БЦ {len(bcs)}')
        for cl, c in res['clusters'].items():
            f = lambda s: f"n={s['n']:3} мед {s['med']:>8,} [{s['q1']:,}–{s['q3']:,}]" if s else '—'
            print(f"  {cl} {c['name'][:34]:34} продажа {f(c['sale'])} | аренда чистая {f(c['rent_net'])}")
    json.dump(out, open('commercial-data/clusters.json', 'w'), ensure_ascii=False, indent=1)


R = {}
if __name__ == '__main__':
    main()
