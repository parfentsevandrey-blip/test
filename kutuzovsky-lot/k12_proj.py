"""Comparison table of the competing projects around Kutuzovsky XII."""
import json, collections

L = json.load(open('k12_linked.json'))
REN_OK = {'Дизайнерский', 'Евроремонт', 'Под ключ / с мебелью', 'Косметический'}

META = {                                   # класс, срок сдачи, застройщик
    'Кутузовский XII':    ('премиум', 'сдан\u00a0в\u00a02020',               'Capital Group'),
    'Бадаевский':         ('элит',    'IV\u00a0кв.\u00a02026 — IV\u00a0кв.\u00a02027', 'Capital Group'),
    'Веспер Кутузовский': ('элит',    'I\u00a0кв.\u00a02028',                'Vesper'),
    'Дом Дау':            ('премиум', 'II\u00a0кв.\u00a02027',               'Сумма Элементов'),
    'Capital Towers':     ('премиум', 'сдан\u00a0в\u00a02023',               'Capital Group'),
}
ORDER = ['Кутузовский XII', 'Бадаевский', 'Веспер Кутузовский', 'Дом Дау', 'Capital Towers']

nf = lambda v: f'{v:,.0f}'.replace(',', ' ')
rng = lambda vs: f'{min(vs):.0f}–{nf(max(vs))}'

rows = []
for name in ORDER:
    v = L[name]
    cls, due, dev = META[name]
    ar = [x['area'] for x in v]
    pr = [x['price'] / 1e6 for x in v]
    ren = sum(1 for x in v if x.get('fin') in REN_OK)
    fin = f'{round(ren / len(v) * 100)} % лотов с ремонтом' if ren / len(v) > 0.05 else 'без отделки'
    rows.append([name, cls, due, fin, str(len(v)), rng(ar), rng(pr),
                 nf(sum(x['price'] for x in v) / sum(x['area'] for x in v))])
BUDGET = 162_500_000                       # сколько метров даёт бюджет нашего лота
SHORT = {'Веспер Кутузовский': 'Веспер'}
power = [[f'{BUDGET / float(r[7].replace(chr(160), "").replace(" ", "")):.1f} м²'.replace('.', ','),
          SHORT.get(r[0], r[0])] for r in rows]
json.dump({'rows': rows, 'power': power}, open('k12_proj.json', 'w'), ensure_ascii=False, indent=1)
for r in rows:
    print(' | '.join(x.replace(chr(10), ' ') for x in r))
print(power)
