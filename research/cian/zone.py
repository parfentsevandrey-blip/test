#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Отбор сопоставимых лотов строго внутри изучаемой зоны.

Зона задана рамкой по карте: Ильинское шоссе от Дмитровского до Глухова,
Рублёво-Успенское от Раздоров до Горок-2. Город Красногорск, Павшино,
Опалиха, Нахабино и Одинцово за рамку не попадают — проверено по их
координатам.
"""
import json, math, sys, statistics as st

# рамка зоны
LAT = (55.695, 55.805)
LNG = (37.050, 37.330)
OBJ = (55.75742, 37.238906)          # с. Ильинское, ул. Ленина, 28

# что должно остаться снаружи — контроль рамки
OUTSIDE = {'Красногорск (центр)': (55.8317, 37.3300), 'Павшино': (55.8085, 37.3536),
           'Опалиха': (55.8236, 37.2864), 'Нахабино': (55.8404, 37.1746),
           'Одинцово': (55.6789, 37.2731)}

def inzone(l):
    la, ln = l.get('lat'), l.get('lng')
    if la is None or ln is None: return False
    return LAT[0] <= la <= LAT[1] and LNG[0] <= ln <= LNG[1]

def km(l):
    return round(math.hypot((l['lat']-OBJ[0])*111,
                            (l['lng']-OBJ[1])*111*math.cos(math.radians(OBJ[0]))), 1)

def load(*files):
    import glob
    lots = {}
    for pat in files:
        hits = sorted(glob.glob(pat))
        if not hits: print('нет файла', pat); continue
        for f in hits:
            d = json.load(open(f))
            for l in d['lots']: lots[l['id']] = l
    return lots

def rate(l):
    """₽/м²/год. Часть карточек хранит в цене ставку за м² в месяц."""
    a, p = l.get('totalArea'), l.get('priceRub')
    if not a or not p: return None
    per_m2_month = p / a
    # если «цена» меньше 6000, это почти наверняка ставка за м² в месяц
    if p < 6000: return round(p * 12)
    if per_m2_month < 120: return None          # слишком дёшево даже для склада
    return round(per_m2_month * 12)

def show(name, rows):
    print(f'\n══ {name}: {len(rows)} лотов')
    if not rows: return
    rs = sorted(r['rate'] for r in rows)
    print(f'   медиана {st.median(rs):,.0f}  квартили {rs[len(rs)//4]:,.0f}–{rs[-max(1,len(rs)//4)]:,.0f}'
          .replace(',', ' '))
    for r in sorted(rows, key=lambda x: x['rate']):
        print(f"   {r['id']}  {r['area']:>7} м²  эт.{str(r['floor']):>4}/{str(r['floors']):<3} "
              f"{r['rate']:>8,} ₽/м²/год  {r['days']:>5} дн  {r['km']:>5} км".replace(',', ' '))

if __name__ == '__main__':
    lots = load('cianwork/okrug.json', 'cianwork/ilinskoe.json', 'cianwork/loc/*.json')
    print('всего выгружено:', len(lots))

    print('\nконтроль рамки (всё это должно быть ВНЕ зоны):')
    for n, (la, ln) in OUTSIDE.items():
        ok = not (LAT[0] <= la <= LAT[1] and LNG[0] <= ln <= LNG[1])
        print(f'   {n:20s} {"вне зоны ✓" if ok else "ВНУТРИ — рамка плохая"}')

    zone = []
    for l in lots.values():
        if not inzone(l): continue
        r = rate(l)
        if not r: continue
        zone.append({'id': l['id'], 'area': l['totalArea'], 'floor': l.get('floor'),
                     'floors': l.get('floors'), 'rate': r, 'days': l.get('daysOnMarket'),
                     'created': l.get('created'), 'km': km(l), 'url': l['url'],
                     'price': l.get('priceRub'), 'lat': l['lat'], 'lng': l['lng']})
    print('в зоне и со считаемой ставкой:', len(zone))
    json.dump(zone, open('zone_lots.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=1)

    f = lambda pred: [z for z in zone if pred(z)]
    show('№1 — крупный блок 200–800 м²', f(lambda z: 200 <= z['area'] <= 800))
    show('№2 — малый блок 15–70 м², этаж 2 и выше',
         f(lambda z: 15 <= z['area'] <= 70 and (z['floor'] or 0) >= 2))
    show('№2 — тот же размер, 1-й этаж (для поправки на этаж)',
         f(lambda z: 15 <= z['area'] <= 70 and (z['floor'] or 0) == 1))
    show('№3 — блок 25–120 м², этаж 3 и выше',
         f(lambda z: 25 <= z['area'] <= 120 and (z['floor'] or 0) >= 3))
    show('№3 — блок 25–120 м², этаж 2', f(lambda z: 25 <= z['area'] <= 120 and z['floor'] == 2))
