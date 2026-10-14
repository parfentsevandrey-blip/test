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
        cls='B+ (по ЦИАН), реконструкция 2009 г., 5 этажей, кирпич', metro='Беговая — 20 мин пешком; Хорошёвская (БКЛ) и МЦК Шелепиха — 4–5 мин транспортом; Полежаевская — 20 мин пешком',
        units=[dict(key='y1', name='Офис 1 этаж', area=303.8, floor=1, note='кабинетная планировка, оборудован под банк/депозитарий, рядом с холлом БЦ'),
               dict(key='y5', name='Офис 5 этаж', area=680.0, floor=5, note='верхний этаж, open space + кабинеты, два лестничных узла')]),
    'kaleidoscope': dict(
        title='БЦ «Калейдоскоп»', address='Москва, Хлебозаводский проезд, 7, стр. 5', district='Нагатино-Садовники (ЮАО)',
        cls='B, 1995 г., 3 лифта OTIS, центральное кондиционирование', metro='Нагатинская (серая, МЦД-2) и Каширская (зелёная, БКЛ) — 10–15 мин пешком',
        units=[dict(key='k1', name='1 этаж', area=394.9, floor=1, note='отдельный вход с улицы, высота потолка 2,57 м'),
               dict(key='k2', name='2 этаж (антресоль)', area=401.3, floor=2, note='коридорно-кабинетная планировка, высота потолка 2,37 м')]),
    'imagine': dict(
        title='БЦ Imagine Plaza', address='Москва, Нагатинская ул., 16 (стр. 9)', district='Нагатино-Садовники (ЮАО)',
        cls='B / B+ (по ЦИАН), реконструкция здания 1974 г., 3 этажа', metro='Нагатинская — 12–15 мин пешком; Коломенская — ~1,1 км; трамвай у входа',
        units=[dict(key='i1', name='№1, 1 этаж', area=168.2, floor=1), dict(key='i2', name='№2, 1 этаж', area=230.0, floor=1),
               dict(key='i3', name='№3, 1 этаж', area=524.0, floor=1), dict(key='i4', name='№4, 2 этаж', area=182.3, floor=2),
               dict(key='i5', name='№5, 2 этаж', area=460.2, floor=2), dict(key='i6', name='№6, 3 этаж, балкон', area=199.9, floor=3),
               dict(key='i7', name='№7, 3 этаж, балкон', area=195.0, floor=3), dict(key='i8', name='№8, 3 этаж, балкон', area=173.4, floor=3)]),
}

# Сравнительный подход: аналог → корректировки (%) к удельной цене без НДС после торга.
# loc — местоположение/метро; bld — класс, возраст, инженерия, потолки; fl — этаж/вход; size — масштаб; fit — отделка/арендатор.
SALE = {
    'yard': {
        'y1': [(322142440, 3, dict(size=-5, fit=-3), 'тот же комплекс The Yard (11с7), 1 этаж, с арендатором'),
               (323817290, 2, dict(loc=-5, bld=-3), 'Магистраль Плаза, B+, 15 мин до Беговой, крупный БЦ с инфраструктурой'),
               (334609760, 2, dict(loc=-5, bld=-3), 'Магистраль Плаза, 7 этаж, виды на Сити'),
               (327871277, 2, dict(loc=-5, bld=-3, size=3), 'Магистраль Плаза, 5 этаж, ремонт'),
               (332376485, 1, dict(loc=-3, bld=10, size=5), 'БЦ «Магистраль», B, 1973 г.'),
               (332676289, 1, dict(loc=-3, bld=8, size=4), 'Хорошёвское ш., B, 1971 г., 12 мин до ЦСКА')],
        'y5': [(322142440, 2, dict(size=-12, fit=-3, fl=-3), 'тот же комплекс, 1 этаж, малый лот'),
               (323817290, 2, dict(loc=-5, bld=-3, size=-4), 'Магистраль Плаза'),
               (334609760, 2, dict(loc=-5, bld=-3, size=-3), 'Магистраль Плаза'),
               (327871277, 3, dict(loc=-5, bld=-3), 'Магистраль Плаза, близкий по площади блок'),
               (332376485, 1, dict(loc=-3, bld=10), 'БЦ «Магистраль», B, 1973 г.'),
               (332676289, 1, dict(loc=-3, bld=8), 'Хорошёвское ш., B, 1971 г.')]},
    'kaleidoscope': {
        'k1': [(333676669, 2, dict(bld=-12, fit=-3), 'Сириус Парк стр. 4, B+, потолки 3,6 м, с арендатором'),
               (334041595, 2, dict(bld=-12), 'Сириус Парк стр. 4, 1 этаж'),
               (333124503, 2, dict(loc=-12, bld=-5), 'Нагатинская, 10: первая линия, витрины, потолки 4,5 м'),
               (333978351, 1, dict(bld=-8, fl=5), 'офис в ЖК, 2 этаж, отдельный вход, потолки 3,4 м')],
        'k2': [(333424059, 2, dict(bld=-12, fit=8, size=-5), 'Сириус Парк стр. 9, 5 этаж, без отделки'),
               (332803851, 1, dict(bld=-12, fit=8, size=-5), 'Сириус Парк стр. 9, 2 этаж, без отделки'),
               (333978351, 2, dict(bld=-12), 'офис в ЖК, 2 этаж, потолки 3,4 м'),
               (334119775, 3, dict(size=-3, fit=-3), 'Imagine Plaza, 2 этаж, с арендатором')]},
    'imagine': {
        'up': [(334119775, 4, dict(fit=-3), 'тот же БЦ, 2 этаж, с арендатором и ремонтом «для себя»'),
               (333424059, 1, dict(bld=-12, fit=8), 'Сириус Парк стр. 9, B+, без отделки'),
               (332803851, 1, dict(bld=-12, fit=8), 'Сириус Парк стр. 9, 2 этаж, без отделки'),
               (333978351, 2, dict(bld=-8), 'офис в ЖК, отдельный вход')],
        'ground': [(333124503, 2, dict(loc=-15), 'Нагатинская, 10: первая линия улицы, витрины'),
                   (334614820, 1, dict(bld=-5), 'Нагатинская наб., 10к1: ПСН в жилом доме, торги по банкротству'),
                   (333676669, 2, dict(bld=-12, loc=3, fit=-3), 'Сириус Парк стр. 4, 1 этаж, с арендатором'),
                   (334119775, 2, dict(fl=12, fit=-3), 'тот же БЦ, 2 этаж (+ за 1 этаж)')]},
}

# Доходный подход: рыночная ставка аренды без НДС (₽/м²/год), недозагрузка, операционные расходы, ставка капитализации
INCOME = {
    'y1': dict(rent=28000, vac=0.08, opex=0.12, cap=0.115),
    'y5': dict(rent=25000, vac=0.10, opex=0.12, cap=0.12),
    'k1': dict(rent=27000, vac=0.08, opex=0.12, cap=0.115),
    'k2': dict(rent=21000, vac=0.10, opex=0.12, cap=0.12),
    'ground': dict(rent=27000, vac=0.08, opex=0.12, cap=0.115),
    'up': dict(rent=22000, vac=0.10, opex=0.12, cap=0.12),
}
RENT_COMPS = {
    'yard': [324943568, 332870182, 334228161, 333270536, 322993142, 332870183, 332872358, 334604035, 333275891, 330435006],
    'kaleidoscope': [332872404, 334041594, 334041597, 332797266, 334666497, 332870165, 333103704],
    'imagine': [334090223, 332408335, 332443483, 332472602, 332870165, 332939066, 332872404],
}


def comp_row(cid, w, adj, note):
    r = rows[cid]
    base = ex_vat(r) * BARGAIN
    k = 1.0
    for v in adj.values():
        k *= 1 + v / 100
    return dict(id=cid, weight=w, adj=adj, note=note, ask=r['per_m2'], vat=r['vat'], ex_vat=round(ex_vat(r)),
                base=round(base), adjusted=round(base * k), area=r['area'], floor=r['floor'], address=r['address'],
                bc=r['bc'], cls=r['bc_class'], year=r['year'], metro=r['metro'], metro_min=r['metro_min'],
                metro_walk=r['metro_walk'], price=r['price'], url=r['url'], category=r['category'], photos=r['photos'][:2])


def income_value(p):
    noi = p['rent'] * (1 - p['vac']) * (1 - p['opex'])
    return noi / p['cap'], noi


def main():
    out = {'subjects': {}, 'params': dict(vat=VAT, bargain=BARGAIN)}
    for sk, s in SUBJECTS.items():
        S = dict(s, sale={}, rent=[], units_val=[])
        for grp, comps in SALE[sk].items():
            cr = [comp_row(*c) for c in comps]
            wavg = sum(c['adjusted'] * c['weight'] for c in cr) / sum(c['weight'] for c in cr)
            S['sale'][grp] = dict(comps=cr, value=round(wavg))
        for cid in RENT_COMPS[sk]:
            r = rows.get(cid)
            if r:
                S['rent'].append(dict(id=cid, address=r['address'], bc=r['bc'], cls=r['bc_class'], floor=r['floor'], area=r['area'],
                                      ask=r['per_m2'], vat=r['vat'], ex_vat=round(ex_vat(r)), month=r['price'], url=r['url'],
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
