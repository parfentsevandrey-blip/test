"""Структура предложения: с кем лот конкурирует на самом деле.

Плюс проверка суммы ремонта на прочность: при какой стоимости ремонта
покупателю выгоднее купить чужой бетон, а не нашу готовую квартиру.
"""
import json, re, sys, os
sys.path.insert(0, os.path.dirname(__file__))
from zl_fin import grp, combo

L = json.load(open('zl/zl_linked.json'))
R = json.load(open('zl/zl_raw.json'))
LO, HI = 65, 115
BLO, BHI = 240e6, 320e6
FIT = 750_000
OUR_A, OUR_P, OUR_PPM = 87.5, 280_000_000, 3_200_000
APART = {'Софийский', 'Резиденция 1864', 'BALCHUG VIEWPOINT'}
ORDER = ['Золотой', 'Дом Лаврушинский', 'Клубный дом DUO', 'Клубный дом Космо 4/22',
         'Русские Сезоны', 'Софийский', 'Резиденция 1864', 'BALCHUG VIEWPOINT']

nf = lambda v: f'{v:,.0f}'.replace(',', ' ')
w  = lambda g: sum(x['price'] for x in g) / sum(x['area'] for x in g)

band   = {n: [x for x in v if LO <= x['area'] <= HI and not combo(x)] for n, v in L.items()}
budget = {n: [x for x in v if BLO <= x['price'] <= BHI and not combo(x)] for n, v in L.items()}
both   = {n: [x for x in v if LO <= x['area'] <= HI and BLO <= x['price'] <= BHI and not combo(x)]
          for n, v in L.items()}

rows = []
for n in ORDER:
    v = L[n]
    dev = sum(1 for x in v if x['seller'] == 'Застройщик')
    rows.append([n + (' *' if n in APART else ''), str(len(v)),
                 f'{dev} ({dev / len(v):.0%})', str(len(band[n])),
                 str(len(budget[n])), str(len(both[n]))])
tot = sum(len(v) for v in L.values())
rows.append(['Итого по локации', str(tot),
             f"{sum(1 for v in L.values() for x in v if x['seller'] == 'Застройщик')}",
             str(sum(len(v) for v in band.values())),
             str(sum(len(v) for v in budget.values())),
             str(sum(len(v) for v in both.values()))])

# ── точка безразличия: при какой стоимости ремонта наш лот перестаёт выигрывать ──
QUART = ['Дом Лаврушинский', 'Клубный дом DUO', 'Клубный дом Космо 4/22']
breakeven = []
for n in QUART:
    sh = [x for x in band[n] if grp(x) == 'shell']
    if not sh: continue
    base = w(sh)
    be = OUR_PPM - base
    breakeven.append([n, str(len(sh)), nf(base), nf(be),
                      f'{be * OUR_A / 1e6:.1f} млн ₽'.replace('.', ',')])

# ── упоминания рассрочки и ипотеки ─────────────────────────────────────────
RASS = re.compile(r'рассроч', re.I)
IPO  = re.compile(r'ипотек|субсидир|ставк', re.I)
terms = [[n, str(len(R[n])),
          str(sum(1 for x in R[n] if RASS.search(x['desc']))),
          str(sum(1 for x in R[n] if IPO.search(x['desc'])))] for n in ORDER]

stats = {
    'total': tot, 'band': sum(len(v) for v in band.values()),
    'budget': sum(len(v) for v in budget.values()),
    'both': sum(len(v) for v in both.values()),
    'noBand': [n for n in ORDER if not band[n]],
    'smallShare': sum(len(v) for v in band.values()) / tot,
    'breakevenMax': max((OUR_PPM - w([x for x in band[n] if grp(x) == 'shell']))
                        for n in QUART if [x for x in band[n] if grp(x) == 'shell']),
}

K = json.load(open('zl/zl_tables.json'))
K['market'], K['breakeven'], K['terms'], K['marketStats'] = rows, breakeven, terms, stats
json.dump(K, open('zl/zl_tables.json', 'w'), ensure_ascii=False, indent=1)

for r in rows: print(' | '.join(r))
print(f"\nквартир 65–115 м² во всей локации: {stats['band']} из {tot} "
      f"({stats['smallShare']:.0%}); в бюджете 240–320 млн ₽: {stats['budget']}; "
      f"и то и другое: {stats['both']}")
print('нет квартир нашего размера вообще:', ', '.join(stats['noBand']))
print('\nточка безразличия — при какой цене ремонта покупателю выгоднее наш лот, чем чужой бетон:')
for r in breakeven: print('   ', ' | '.join(r))
print('\nупоминания рассрочки / ипотеки в текстах объявлений:')
for r in terms: print('   ', ' | '.join(r))
