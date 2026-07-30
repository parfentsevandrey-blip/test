"""Сравнение с локацией: квартиры 65–95 м² вокруг метро «Академическая».

Наш лот без отделки, поэтому приведение к «квартире, в которую можно
заехать» работает в обратную сторону, чем на Кутузовском: отделка
добавляется к нашей цене, а не к цене конкурентов.
"""
import json

L = json.load(open('lev/lv_linked.json'))
FIT = 150_000
LO, HI = 65, 95
OUR = {'area': 79.5, 'price': 56_999_999, 'ppm': 716_981, 'floor': '15/19'}

SHELL = {'Без отделки', 'Черновая', 'Без ремонта'}
WB    = {'Предчистовая (white box)', 'Чистовая'}
REN   = {'Дизайнерский', 'Евроремонт', 'Под ключ / с мебелью', 'Косметический', 'С ремонтом (тип не указан)'}
grp = lambda f: 'shell' if f in SHELL else 'wb' if f in WB else 'ren' if f in REN else 'unk'

w  = lambda g: sum(x['price'] for x in g) / sum(x['area'] for x in g)
nf = lambda v: f'{v:,.0f}'.replace(',', ' ')
d1 = lambda v: f'{v:.1f}'.replace('.', ',')

ORDER = ['Левел Академическая', 'Lunar', 'Файв Тауэрс', 'Новочеремушкинская 17',
         'Вавилова 52', 'VAVILOVE', 'Вавилов ДОМ', 'Новые Черемушки']
SUB = {'Левел Академическая':   'наш дом · сдан 2025–2026',
       'Lunar':                 'премиум · сдан 2024',
       'Файв Тауэрс':           'первичка · white box · 2027',
       'Новочеремушкинская 17': 'вторичка · 2020',
       'Вавилова 52':           'вторичка · 2020',
       'VAVILOVE':              'вторичка · 2019',
       'Вавилов ДОМ':           'вторичка · 2019',
       'Новые Черемушки':       'вторичка · 2020'}

def block(name):
    band = [x for x in L[name] if LO <= x['area'] <= HI]
    if not band: return None, None
    fin = [x for x in band if grp(x['fin']) == 'ren']
    wb  = [x for x in band if grp(x['fin']) == 'wb']
    sh  = [x for x in band if grp(x['fin']) in ('shell', 'unk')]
    if fin:   base, fit, why = w(fin), 0,   f'{len(fin)} с ремонтом'
    elif wb:  base, fit, why = w(wb), FIT,  f'{len(wb)} white box'
    else:     base, fit, why = w(sh), FIT,  f'{len(sh)} без отделки'
    row = [name, str(len(band)),
           f"{d1(min(x['area'] for x in band))}–{d1(max(x['area'] for x in band))}",
           f"{d1(min(x['price'] for x in band) / 1e6)}–{d1(max(x['price'] for x in band) / 1e6)}",
           nf(w(band)), why, nf(base + fit)]
    return row, (base, fit)

rows, ready = [], {}
for n in ORDER:
    row, br = block(n)
    if row is None: print('пропуск (нет лотов в базе):', n); continue
    rows.append(row); ready[n] = [br[0], br[1], SUB[n]]

OUR_READY = OUR['ppm'] + FIT
K = json.load(open('lev/lv_tables.json')) if __import__('os').path.exists('lev/lv_tables.json') else {}
K['loc'], K['ready'], K['fit'], K['our'] = rows, ready, FIT, OUR
json.dump(K, open('lev/lv_tables.json', 'w'), ensure_ascii=False, indent=1)

for r in rows: print(' | '.join(r))
print(f"\nНАШ ЛОТ {nf(OUR['ppm'])} ₽/м² + отделка {nf(FIT)} = {nf(OUR_READY)} ₽/м²"
      f"  ({OUR['price']/1e6:.1f} + {OUR['area']*FIT/1e6:.1f} = {(OUR['price']+OUR['area']*FIT)/1e6:.1f} млн ₽)")
for n, (b, f, _) in ready.items():
    if n == 'Левел Академическая': continue
    print(f'  vs {n:22s} {100 * (OUR_READY / (b + f) - 1):+5.0f} %   ({nf(b+f)} ₽/м²)')
