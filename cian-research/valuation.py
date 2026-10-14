#!/usr/bin/env python3
"""Оценка справедливой цены продажи трёх офисных объектов: сравнительный и доходный подходы.

Данные — объявления ЦИАН (commercial-data/offers.json, собраны commercial.py 14.10.2026).
Результат — commercial-data/valuation.json для отчёта (report.py).
"""
import json

VAT = 1.22          # НДС с 2026 г.
BARGAIN = 0.92      # торг: цена сделки ≈ 92% цены предложения (офисы, Москва)

rows = {r['id']: r for r in json.load(open('commercial-data/offers.json'))}


def ex_vat(r):
    return r['per_m2'] / VAT if r['vat'] == 'included' else r['per_m2']


# Объекты оценки: помещения, этаж, особенности
SUBJECTS = {
    'yard': dict(
        title='БЦ «ЯРД»', address='Москва, 1-й Магистральный тупик, 11, стр. 1', district='Хорошёвский (САО)',
        cls='класс B по карточкам ЦИАН (B+ — в справочнике БЦ и рекламе), здание 1958 г., реконструкция 2009 г., 5 этажей, кирпич', metro='Шелепиха (МЦК/БКЛ) — ~1 км, ~15 мин пешком; Хорошёвская (БКЛ) — 1,2 км; Беговая и Полежаевская — ~20 мин пешком',
        units=[dict(key='y1', name='Офис 1 этаж', area=303.8, floor=1, note='кабинетная планировка, оборудован под банк/депозитарий, рядом с холлом БЦ'),
               dict(key='y5', name='Офис 5 этаж', area=680.0, floor=5, note='верхний этаж, open space + кабинеты, два лестничных узла')]),
    'kaleidoscope': dict(
        title='БЦ «Калейдоскоп»', address='Москва, Хлебозаводский проезд, 7, стр. 5', district='Нагатино-Садовники (ЮАО)',
        cls='класс B, 1995 г., 3 лифта OTIS, центральное кондиционирование, потолки 2,37–2,57 м', metro='Нагорная — 1,45 км, Каширская (зелёная + БКЛ) — 1,8 км, Нагатинская — 15–25 мин пешком (в презентации — «10–15 мин»)',
        units=[dict(key='k1', name='1 этаж', area=394.9, floor=1, note='отдельный вход с улицы, высота потолка 2,57 м'),
               dict(key='k2', name='2 этаж (антресоль)', area=401.3, floor=2, note='коридорно-кабинетная планировка, высота потолка 2,37 м')]),
    'imagine': dict(
        title='БЦ Imagine Plaza', address='Москва, Нагатинская ул., 16 (стр. 9)', district='Нагатино-Садовники (ЮАО)',
        cls='класс B по карточке здания (B+ — в справочнике БЦ ЦИАН), реконструированный промышленный корпус 1974 г., 3 этажа, потолки ~4 м', metro='Нагатинская — ~15 мин пешком; Коломенская — 1,5 км; трамвай у входа',
        units=[dict(key='i1', name='№1, 1 этаж', area=168.2, floor=1), dict(key='i2', name='№2, 1 этаж', area=230.0, floor=1),
               dict(key='i3', name='№3, 1 этаж', area=524.0, floor=1), dict(key='i4', name='№4, 2 этаж', area=182.3, floor=2),
               dict(key='i5', name='№5, 2 этаж', area=460.2, floor=2), dict(key='i6', name='№6, 3 этаж, балкон', area=199.9, floor=3),
               dict(key='i7', name='№7, 3 этаж, балкон', area=195.0, floor=3), dict(key='i8', name='№8, 3 этаж, балкон', area=173.4, floor=3)]),
}

# Сравнительный подход: аналог → корректировки (%) к удельной цене без НДС после торга.
# loc — местоположение/метро; bld — класс, возраст, инженерия, потолки; fl — этаж/вход; size — масштаб; fit — отделка/арендатор.
# Тип аналога (для отчёта): office_bc — офис в бизнес-центре, office_adm — офис в административном здании,
# psn_res — помещение свободного назначения (ПСН) на 1 этаже жилого дома, psn_bc — ПСН в бизнес-центре.
SALE = {
    'yard': {
        'y1': [(322142440, 3, dict(size=-5, fit=-3), 'office_bc', 'тот же комплекс The Yard (11с7), 1 этаж, с арендатором'),
               (323817290, 2, dict(bld=-6), 'office_bc', 'Магистраль Плаза в 300 м: тот же доступ к метро, но крупнее БЦ, инфраструктура, виды'),
               (334609760, 2, dict(bld=-6), 'office_bc', 'Магистраль Плаза, 7 этаж, виды на Сити'),
               (327871277, 2, dict(bld=-6, size=3), 'office_bc', 'Магистраль Плаза, 5 этаж, ремонт'),
               (332376485, 1, dict(loc=-3, bld=10, size=5), 'office_bc', 'БЦ «Магистраль», B, 1973 г.'),
               (332676289, 1, dict(loc=-3, bld=8, size=4), 'office_adm', 'Хорошёвское ш., административное здание B, 1971 г., коридорная планировка'),
               (333555511, 1, dict(loc=-5, bld=15, size=4), 'office_adm', '4-я Магистральная, 5к1: бывший промышленный корпус B (1959), грузовой лифт'),
               (327068779, 1, dict(loc=-15, bld=5, size=-6), 'office_bc', 'БЦ «Бега» (B−, 1951 г.), 3 мин до метро Беговая и МЦД')],
        'y5': [(322142440, 2, dict(size=-12, fit=-3, fl=-3), 'office_bc', 'тот же комплекс, 1 этаж, малый лот'),
               (323817290, 2, dict(bld=-6, size=-4), 'office_bc', 'Магистраль Плаза'),
               (334609760, 2, dict(bld=-6, size=-3), 'office_bc', 'Магистраль Плаза'),
               (327871277, 3, dict(bld=-6), 'office_bc', 'Магистраль Плаза, близкий по площади блок'),
               (332376485, 1, dict(loc=-3, bld=10), 'office_bc', 'БЦ «Магистраль», B, 1973 г.'),
               (332676289, 1, dict(loc=-3, bld=8), 'office_adm', 'Хорошёвское ш., административное здание B, 1971 г.'),
               (333555511, 1, dict(loc=-5, bld=15), 'office_adm', '4-я Магистральная, 5к1: бывший промышленный корпус B (1959)'),
               (327068779, 1, dict(loc=-15, bld=5, size=-12), 'office_bc', 'БЦ «Бега», 3 мин до метро, малый лот')]},
    'kaleidoscope': {
        'k1': [(333676669, 2, dict(bld=-10, fit=-3), 'psn_bc', 'Сириус Парк стр. 4: ПСН с отдельным входом, потолки 3,05 м, арендатор-столовая'),
               (334041595, 1, dict(bld=-12), 'office_bc', 'Сириус Парк стр. 4, офис 1 этаж (в тексте объявления — шаблон аренды, надёжность ниже)'),
               (334276621, 2, dict(size=-5, fit=-5), 'psn_res', 'Каширское ш., 8к1 — ближайший (270 м): ПСН с отдельным входом в жилом доме, дизайнерский ремонт'),
               (324992057, 1, dict(loc=-5), 'psn_res', 'Варшавское ш., 69к1: ПСН 524 м², первая линия шоссе, витрины'),
               (326664641, 1, dict(loc=-5, size=-3), 'psn_res', 'Варшавское ш., 71к1: ПСН с двумя входами, первая линия'),
               (333124503, 1, dict(loc=-12, bld=-8), 'psn_res', 'Нагатинская, 10: офис на 1 этаже жилого дома, первая линия, витрины')],
        'k2': [(334119775, 3, dict(bld=-10, fit=-3), 'office_bc', 'Imagine Plaza, 2 этаж, потолки ~4 м, с арендатором'),
               (334131429, 2, dict(fit=-3, size=-3), 'office_adm', 'Электролитный пр., 3с32: этаж административного здания 1914 г., полностью сдан'),
               (333424059, 1, dict(bld=-15, fit=8, size=-5), 'office_bc', 'Сириус Парк стр. 9, 5 этаж, без отделки, рассрочка'),
               (332803851, 1, dict(bld=-15, fit=12, size=-5), 'office_bc', 'Сириус Парк стр. 9, 2 этаж, под ремонт'),
               (333978351, 1, dict(bld=-15), 'psn_res', 'ПСН на 2 этаже в ЖК, отдельный вход, потолки 3,4 м')]},
    'imagine': {
        'up': [(334119775, 4, dict(fit=-3), 'office_bc', 'тот же БЦ, 2 этаж, с арендатором и ремонтом «для себя»'),
               (334131429, 2, dict(bld=5, fit=-3), 'office_adm', 'Электролитный пр., 3с32: бывший промышленный/административный корпус 1914 г., сдан'),
               (329791543, 1, dict(loc=-10, fit=-8), 'office_adm', 'Нагатинская, 31: 2 этаж административного здания, отдельный вход, арендатор-гостиница, 4 мин до Коломенской'),
               (333424059, 1, dict(bld=-8, fit=8), 'office_bc', 'Сириус Парк стр. 9, B+, без отделки'),
               (332803851, 1, dict(bld=-8, fit=12), 'office_bc', 'Сириус Парк стр. 9, 2 этаж, под ремонт'),
               (333978351, 1, dict(bld=-8), 'psn_res', 'ПСН на 2 этаже в ЖК, отдельный вход')],
        'ground': [(333124503, 2, dict(loc=-15), 'psn_res', 'Нагатинская, 10: первая линия улицы, витрины'),
                   (331782161, 2, dict(bld=-5, fit=-5), 'psn_res', 'Нагатинская наб., 10к3: ПСН в ЖК 2015 г., арендаторы (Ozon, салон)'),
                   (334276621, 1, dict(fit=-5), 'psn_res', 'Каширское ш., 8к1: ПСН с отдельным входом, дизайнерский ремонт'),
                   (333676669, 1, dict(bld=-8, loc=3, fit=-3), 'psn_bc', 'Сириус Парк стр. 4: ПСН с арендатором-столовой'),
                   (334119775, 2, dict(fl=12, fit=-3), 'office_bc', 'тот же БЦ, 2 этаж (+12% за 1 этаж с отдельным входом)')]},
}

# Доходный подход. Ставки аналогов приводятся к единой базе «чистая ставка»: без НДС, без эксплуатационных и коммунальных
# платежей (их арендатор возмещает отдельно). Если в ставку аналога они включены — вычитаем типичные для класса B значения.
OPEX = 5000          # эксплуатационные расходы класса B, ₽/м²/год без НДС
UTIL = 1500          # коммунальные (кроме электроэнергии), ₽/м²/год
OWNER = 3000         # невозмещаемые расходы собственника: налог на имущество (2% кадастра), страховка, управление, резерв
# условия по карточкам ЦИАН (поле и текст объявления; при противоречии верим тексту)
TERMS_OVERRIDE = {324943568: dict(opex=False, util=False), 322993142: dict(opex=False, util=False)}
INCOME = {
    'y1': dict(rent=28000, vac=0.08, cap=0.115),
    'y5': dict(rent=25000, vac=0.10, cap=0.12),
    'k1': dict(rent=20000, vac=0.08, cap=0.115),
    'k2': dict(rent=14000, vac=0.10, cap=0.12),
    'ground': dict(rent=20000, vac=0.08, cap=0.115),
    'up': dict(rent=16000, vac=0.10, cap=0.12),
}
RENT_COMPS = {
    'yard': [324943568, 332870182, 334228161, 333270536, 322993142, 332870183, 332872358, 334604035, 333275891, 330435006],
    'kaleidoscope': [332872404, 334041594, 334041597, 332797266, 334666497, 333103704, 332870165, 334943043, 333126680, 332619941],
    'imagine': [332408335, 332443483, 332472602, 334159845, 332870165, 332939066, 331944357, 333723762, 327237930, 332872404],
}


TYPE_RU = {'office_bc': 'офис в БЦ', 'office_adm': 'офис в админ. здании', 'psn_res': 'ПСН в жилом доме', 'psn_bc': 'ПСН в БЦ'}


def comp_row(cid, w, adj, typ, note):
    r = rows[cid]
    base = ex_vat(r) * BARGAIN
    k = 1.0
    for v in adj.values():
        k *= 1 + v / 100
    return dict(id=cid, weight=w, adj=adj, note=note, type=TYPE_RU[typ], ask=r['per_m2'], vat=r['vat'], ex_vat=round(ex_vat(r)),
                base=round(base), adjusted=round(base * k), area=r['area'], floor=r['floor'], address=r['address'],
                bc=r['bc'], cls=r['bc_class'], year=r['year'], metro=r['metro'], metro_min=r['metro_min'],
                metro_walk=r['metro_walk'], price=r['price'], url=r['url'], category=r['category'], photos=r['photos'][:2])


def income_value(p):
    noi = p['rent'] * (1 - p['vac']) - OWNER
    return noi / p['cap'], noi


CARDS = json.load(open('commercial-data/cards.json'))


def rent_terms(cid):
    c = CARDS.get(str(cid)) or {}
    inc = c.get('included') or []
    t = dict(opex='operationalCosts' in inc, util='utilityCharges' in inc, known=bool(c))
    t.update(TERMS_OVERRIDE.get(cid, {}))
    return t


def main():
    out = {'subjects': {}, 'params': dict(vat=VAT, bargain=BARGAIN, opex=OPEX, util=UTIL, owner=OWNER)}
    for sk, s in SUBJECTS.items():
        S = dict(s, sale={}, rent=[], units_val=[])
        for grp, comps in SALE[sk].items():
            cr = [comp_row(*c) for c in comps]
            wavg = sum(c['adjusted'] * c['weight'] for c in cr) / sum(c['weight'] for c in cr)
            S['sale'][grp] = dict(comps=cr, value=round(wavg))
        for cid in RENT_COMPS[sk]:
            r = rows.get(cid)
            if r:
                t = rent_terms(cid)
                net = ex_vat(r) - (OPEX if t['opex'] else 0) - (UTIL if t['util'] else 0)
                S['rent'].append(dict(id=cid, address=r['address'], bc=r['bc'], cls=r['bc_class'], floor=r['floor'], area=r['area'],
                                      ask=r['per_m2'], vat=r['vat'], ex_vat=round(ex_vat(r)), month=r['price'], url=r['url'],
                                      opex=t['opex'], util=t['util'], net=round(net),
                                      metro=r['metro'], metro_min=r['metro_min'], metro_walk=r['metro_walk'], photos=r['photos'][:1]))
        for u in s['units']:
            if sk == 'imagine':
                grp = 'ground' if u['floor'] == 1 else 'up'
            else:
                grp = u['key']
            comp_v = S['sale'][grp]['value']
            inc_p = INCOME[grp]
            inc_v, noi = income_value(inc_p)
            per = 0.5 * comp_v + 0.5 * inc_v
            # поправки к конкретному блоку внутри группы
            extra = 0
            if sk == 'imagine':
                if u['floor'] == 3:
                    extra += 3          # балкон/терраса, верхний этаж
                if u['area'] > 400:
                    extra -= 5          # крупный лот — меньше покупателей
            per *= 1 + extra / 100
            per = round(per / 1000) * 1000
            total = per * u['area']
            S['units_val'].append(dict(u, group=grp, comp=comp_v, income=round(inc_v), noi=round(noi), inc=inc_p, extra=extra,
                                       per_m2=per, total=round(total), lo=round(total * 0.92), hi=round(total * 1.08),
                                       gross_yield=round(inc_p['rent'] / per * 100, 1)))
        out['subjects'][sk] = S
    json.dump(out, open('commercial-data/valuation.json', 'w'), ensure_ascii=False, indent=1)
    for sk, S in out['subjects'].items():
        print('##', S['title'])
        for g, v in S['sale'].items():
            print(f"   сравнительный [{g}]: {v['value']:,} ₽/м² ← " + ', '.join(f"{c['adjusted']:,}" for c in v['comps']))
        for u in S['units_val']:
            print(f"   {u['name']:22} {u['area']:>6} м²: сравн. {u['comp']:,} | доход. {u['income']:,} → {u['per_m2']:,} ₽/м² = {u['total']/1e6:,.1f} млн "
                  f"(диапазон {u['lo']/1e6:,.1f}–{u['hi']/1e6:,.1f}), доходность {u['gross_yield']}%")
        print(f"   ИТОГО {sum(u['total'] for u in S['units_val'])/1e6:,.1f} млн ₽, {sum(u['area'] for u in S['units_val']):,.1f} м²")


if __name__ == '__main__':
    main()
