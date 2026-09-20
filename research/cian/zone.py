#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Отбор сопоставимых лотов строго внутри изучаемой зоны.

Зона задана рамкой по карте: Ильинское шоссе от Дмитровского до Глухова,
Рублёво-Успенское от Раздоров до Горок-2. Город Красногорск, Павшино,
Опалиха, Нахабино и Одинцово за рамку не попадают — проверено по их
координатам.

Ставка. У ЦИАН в коммерции цена бывает трёх видов: сумма за месяц,
за м² в месяц и за м² в год. Где выгрузка принесла тип цены — берём его.
Где нет — эвристика: цена ниже 6 000 читается как «за м² в месяц»;
цена, которая как сумма за месяц дала бы меньше 8 000 ₽/м²/год, но сама
лежит в 10–60 тыс., читается как «за м² в год» (так не сдают ни один
первый этаж нового ЖК). Что не восстанавливается — исключается и
перечисляется отдельно.
"""
import json, math, glob, statistics as st

LAT = (55.695, 55.805)
LNG = (37.050, 37.330)
OBJ = (55.75742, 37.238906)          # с. Ильинское, ул. Ленина, 28

OUTSIDE = {'Красногорск (центр)': (55.8317, 37.3300), 'Павшино': (55.8085, 37.3536),
           'Опалиха': (55.8236, 37.2864), 'Нахабино': (55.8404, 37.1746),
           'Одинцово': (55.6789, 37.2731)}

SETTLE = {'Ильинское': (55.75742, 37.238906), 'Александровка': (55.75168, 37.212504),
          'Ильинское-Усово': (55.766136, 37.220274), 'Бузланово': (55.771039, 37.198841),
          'Архангельское': (55.789375, 37.301833), 'Дмитровское': (55.753463, 37.122017),
          'Петрово-Дальнее': (55.751371, 37.178691), 'Глухово': (55.771256, 37.253818),
          'пос. Истра': (55.77557, 37.14982), 'Барвиха': (55.7237, 37.2944),
          'Жуковка': (55.7378, 37.2436), 'Раздоры': (55.746309, 37.304536),
          'Горки-2': (55.724849, 37.161677), 'Усово': (55.729934, 37.209459)}

# улица однозначно называет населённый пункт — это точнее координат
STREET_TO_SETTLE = {'Заповедная': 'Ильинское-Усово', 'Архангельская': 'Ильинское-Усово',
                    'Романовская': 'Глухово', 'Ленина': 'Ильинское',
                    'Экспериментальная': 'Ильинское', 'Лесная Сторожка': 'пос. Истра',
                    'Рублево-Успенское': 'Горки-2', 'Новый поселок': 'Бузланово',
                    'Рублевское Предместье': 'Глухово'}

def inzone(l):
    la, ln = l.get('lat'), l.get('lng')
    return la is not None and ln is not None and LAT[0] <= la <= LAT[1] and LNG[0] <= ln <= LNG[1]

def km(a, b):
    return math.hypot((a[0]-b[0])*111, (a[1]-b[1])*111*math.cos(math.radians(a[0])))

def load():
    """Старые свипы, поверх них — пересвип с типом цены (loc2)."""
    lots = {}
    for pat in ('cianwork/okrug.json', 'cianwork/ilinskoe.json', 'cianwork/loc/*.json',
                'cianwork/loc2/*.json'):
        for f in sorted(glob.glob(pat)):
            for l in json.load(open(f))['lots']:
                prev = lots.get(l['id'])
                if prev and not l.get('priceType') and prev.get('priceType'):
                    continue                      # не затирать запись с типом цены
                lots[l['id']] = l
    return lots

def rate(l):
    """→ (₽/м²/год, вид цены, достоверно ли). None — не восстанавливается."""
    a, p = l.get('totalArea'), l.get('priceRub')
    if not a: return None, 'нет площади', False
    if not p: return None, 'нет цены', False
    pt, pp = l.get('priceType'), l.get('paymentPeriod')
    if pt and pp:
        if pt == 'squareMeter' and pp == 'annual':  r, kind = p, 'м²/год'
        elif pt == 'squareMeter':                   r, kind = p*12, 'м²/мес'
        elif pp == 'annual':                        r, kind = p/a, 'год'
        else:                                       r, kind = p*12/a, 'мес'
        # ниже 3 000 ₽/м²/год в этой зоне не сдаётся ничего — это ошибка в карточке, а не ставка
        if r < 3000: return None, 'неясно', False
        return round(r), kind, True
    if p < 6000:                                    return round(p*12), 'м²/мес', False
    r = p*12/a
    if r >= 8000:                                   return round(r), 'мес', False
    if (l.get('floor') or 1) < 1 and r >= 4000:     return round(r), 'мес', False   # цоколь дёшев законно
    if 10000 <= p <= 60000:                         return round(p), 'м²/год', False
    return None, 'неясно', False

def addr(l):
    s, h, c = l.get('street'), l.get('house'), l.get('complex')
    if s and h: return f'{s}, {h}'
    if s:       return s
    if c and h: return f'ЖК «{c}», {h}'
    if c:       return f'ЖК «{c}»'
    return None

def settle(l):
    s = l.get('street')
    if s in STREET_TO_SETTLE: return STREET_TO_SETTLE[s]
    return min(SETTLE.items(), key=lambda kv: km((l['lat'], l['lng']), kv[1]))[0]

if __name__ == '__main__':
    lots = load()
    print('всего выгружено:', len(lots),
          '| с типом цены:', sum(1 for l in lots.values() if l.get('priceType')))
    print('\nконтроль рамки (всё это должно быть ВНЕ зоны):')
    for n, pt in OUTSIDE.items():
        ok = not (LAT[0] <= pt[0] <= LAT[1] and LNG[0] <= pt[1] <= LNG[1])
        print(f'   {n:20s} {"вне зоны" if ok else "ВНУТРИ — рамка плохая"}')

    zone, dropped = [], []
    for l in lots.values():
        if not inzone(l): continue
        r, kind, sure = rate(l)
        # срок — от даты создания до единой даты отсчёта 18.09.2026, чтобы свипы разных дней не расходились
        days = l.get('daysOnMarket')
        if l.get('created'):
            from datetime import date
            y, m, d = map(int, l['created'].split('-')); days = max(0, (date(2026, 9, 18) - date(y, m, d)).days)
        rec = {'id': l['id'], 'area': l['totalArea'], 'floor': l.get('floor'), 'floors': l.get('floors'),
               'rate': r, 'kind': kind, 'sure': sure, 'days': days,
               'created': l.get('created'), 'km': round(km((l['lat'], l['lng']), OBJ), 1),
               'url': l['url'], 'price': l.get('priceRub'), 'lat': l['lat'], 'lng': l['lng'],
               'addr': addr(l), 'near': settle(l), 'vat': l.get('vatType'),
               'rent': round(r * l['totalArea'] / 12) if r else None}
        (zone if r else dropped).append(rec)
    print(f'\nв зоне и со считаемой ставкой: {len(zone)}; исключено: {len(dropped)}')
    for d in dropped:
        print(f"   исключён {d['id']} {d['near']} {d['addr'] or ''} {d['area']} м² цена {d['price']} — {d['kind']}")
    guessed = [z for z in zone if z['kind'] == 'м²/год' and not z['sure']]
    print(f'\nставка за м² в год по эвристике (проверить по ссылке): {len(guessed)}')
    for g in guessed: print(f"   {g['id']} {g['near']} {g['addr'] or ''} {g['area']} м² → {g['rate']} ₽/м²/год")
    json.dump(zone, open('zone_lots.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    json.dump({'dropped': dropped, 'total_fetched': len(lots),
               'sure': sum(1 for z in zone if z['sure'])},
              open('zone_dropped.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=1)

    import collections
    print('\nпо населённым пунктам:', dict(collections.Counter(z['near'] for z in zone).most_common()))
    def med(rows): return round(st.median([r['rate'] for r in rows])) if rows else None
    print('медиана зоны:', med(zone))
    print('крупные 200–800, эт.2+:', med([z for z in zone if 200 <= z['area'] <= 800 and (z['floor'] or 0) >= 2]),
          '| 1-й эт.:', med([z for z in zone if 200 <= z['area'] <= 800 and z['floor'] == 1]))
    corr = {'Ильинское', 'Ильинское-Усово', 'Александровка', 'Бузланово', 'Глухово',
            'Петрово-Дальнее', 'пос. Истра', 'Дмитровское'}
    print('малые 15–70, эт.2+, коридор:', med([z for z in zone if 15 <= z['area'] <= 70 and (z['floor'] or 0) >= 2 and z['near'] in corr]),
          '| 1-й эт. коридор:', med([z for z in zone if 15 <= z['area'] <= 70 and z['floor'] == 1 and z['near'] in corr]))
