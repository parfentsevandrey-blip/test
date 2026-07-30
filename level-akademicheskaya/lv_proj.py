"""Сравнение проектов локации: класс, срок, отделка, объём экспозиции."""
import json

L = json.load(open('lev/lv_linked.json'))
REN_OK = {'Дизайнерский', 'Евроремонт', 'Под ключ / с мебелью', 'Косметический', 'С ремонтом (тип не указан)'}
WB = {'Предчистовая (white box)', 'Чистовая'}

META = {  # класс, срок сдачи, застройщик, адрес
    'Левел Академическая':   ('бизнес',  'сдан в 2025–2026', 'Level Group',    'Профсоюзная, 2/22'),
    'Lunar':                 ('премиум', 'сдан в 2024',      'Hutton Development', 'Ленинский пр-т, 38'),
    'Файв Тауэрс':           ('премиум', 'ключи в I кв. 2027', 'СЗ «5 Донской»', '5-й Донской пр-д, 21'),
    'Новочеремушкинская 17': ('бизнес',  'сдан в 2020',      'вторичный рынок', 'Новочерёмушкинская, 17'),
    'Вавилова 52':           ('бизнес',  'сдан в 2020',      'вторичный рынок', 'Вавилова, 52к1'),
    'VAVILOVE':              ('бизнес',  'сдан в 2019',      'Ingrad / Sminex', 'Вавилова, 69А'),
    'Вавилов ДОМ':           ('бизнес',  'сдан в 2019',      'вторичный рынок', 'Вавилова, 27'),
    'Новые Черемушки':       ('бизнес',  'сдан в 2020',      'вторичный рынок', '60-летия Октября, 17'),
}
ORDER = ['Левел Академическая', 'Lunar', 'Файв Тауэрс', 'Новочеремушкинская 17',
         'Вавилова 52', 'VAVILOVE', 'Вавилов ДОМ', 'Новые Черемушки']

nf = lambda v: f'{v:,.0f}'.replace(',', ' ')
rng = lambda vs: f'{min(vs):.0f}–{nf(max(vs))}'
BUDGET = 56_999_999 + 79.5 * 150_000            # бюджет «под ключ»

rows, power = [], []
for name in ORDER:
    v = L[name]
    cls, due, dev, addr = META[name]
    ren = sum(1 for x in v if x['fin'] in REN_OK)
    wb  = sum(1 for x in v if x['fin'] in WB)
    if wb / len(v) > 0.5:      fin = 'white box'
    elif ren / len(v) > 0.5:   fin = 'в основном с ремонтом'
    elif ren:                  fin = f'{round(ren / len(v) * 100)} % лотов с ремонтом'
    else:                      fin = 'без отделки'
    ppm = sum(x['price'] for x in v) / sum(x['area'] for x in v)
    rows.append([name, cls, due, fin, str(len(v)),
                 rng([x['area'] for x in v]), rng([x['price'] / 1e6 for x in v]), nf(ppm)])
    power.append([f'{BUDGET / ppm:.0f} м²', name if name != 'Новочеремушкинская 17' else 'Новочерёмушк. 17'])

K = json.load(open('lev/lv_tables.json'))
K['proj'], K['power'], K['projMeta'] = rows, power, {n: META[n] for n in ORDER}
json.dump(K, open('lev/lv_tables.json', 'w'), ensure_ascii=False, indent=1)
for r in rows: print(' | '.join(x.replace(' ', ' ') for x in r))
print('\nбюджет «под ключ»', f'{BUDGET/1e6:.1f} млн ₽ →', [p[0] for p in power])
