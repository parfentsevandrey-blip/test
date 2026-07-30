"""Экспозиция HIGH LIFE и позиция нашего лота внутри квартала.

278 лотов в одном проекте — таблицей их не показать, поэтому:
  towers  — сводка по шести башням;
  ownCmp  — сопоставимая база 110–140 м², уже готовая к заселению,
            приведённая к нашему 8-му этажу;
  shellCmp— она же в бетоне: сколько стоит метр после отделки.
"""
import json, re, sys, os
sys.path.insert(0, os.path.dirname(__file__))
from hl_fin import grp, key

L = json.load(open('hl/hl_linked.json'))
HL = L['HIGH LIFE']
OUR_URL = 'https://www.cian.ru/sale/flat/327976256/'
OUR = next(x for x in HL if x['url'] == OUR_URL)
OUR_FL, LO, HI = 8, 110, 140
FIT = 200_000
T = json.load(open('hl/hl_tables.json'))
GRAD = T['floor']['HIGH LIFE — прайс застройщика']['per']

w  = lambda g: sum(x['price'] for x in g) / sum(x['area'] for x in g)
nf = lambda v: f'{v:,.0f}'.replace(',', ' ')
d1 = lambda v: f'{v:.1f}'.replace('.', ',')
fl = lambda x: int(re.match(r'(\d+)', x['floor']).group(1))
adj = lambda x: x['ppm'] * (1 + GRAD / 100) ** (OUR_FL - fl(x))

# ── сводка по башням ───────────────────────────────────────────────────────
TOWER = {'K1 SOUL TOWER': ('K1 SOUL — наш корпус', '32 эт. · сдан'),
         'K2 FEEL TOWER': ('K2 FEEL', '24 эт. · сдан'),
         'K3 HEART TOWER': ('K3 HEART', '32 эт. · сдан'),
         'K4 SENSE TOWER': ('K4 SENSE', '47 эт. · IV кв. 2027'),
         'K5 MIND TOWER': ('K5 MIND', '26 эт. · IV кв. 2027'),
         'K6 FAITH TOWER': ('K6 FAITH', '39 эт. · IV кв. 2027')}
towers = []
for k, (name, sub) in TOWER.items():
    v = [x for x in HL if x['corp'] == k]
    if not v: continue
    bud = [x for x in v if 86e6 <= x['price'] <= 106e6]
    band = [x for x in v if LO <= x['area'] <= HI]
    towers.append([('▶ ' if k == OUR['corp'] else '') + name, sub, str(len(v)),
                   f"{d1(min(x['area'] for x in v))}–{d1(max(x['area'] for x in v))}",
                   nf(w(v)), str(len(band)), str(len(bud))])
rest = [x for x in HL if x['corp'] not in TOWER]
towers.append(['Без указания корпуса', '—', str(len(rest)),
               f"{d1(min(x['area'] for x in rest))}–{d1(max(x['area'] for x in rest))}",
               nf(w(rest)), str(sum(1 for x in rest if LO <= x['area'] <= HI)),
               str(sum(1 for x in rest if 86e6 <= x['price'] <= 106e6))])
towers.append(['Весь квартал HIGH LIFE', '6 башен', str(len(HL)),
               f"{d1(min(x['area'] for x in HL))}–{d1(max(x['area'] for x in HL))}",
               nf(w(HL)), str(sum(1 for x in HL if LO <= x['area'] <= HI)),
               str(sum(1 for x in HL if 86e6 <= x['price'] <= 106e6))])

# ── сопоставимая база 110–140 м² ───────────────────────────────────────────
band = [x for x in HL if LO <= x['area'] <= HI]
ready = [x for x in band if grp(x) in ('ready', 'wb')]
shell = [x for x in band if grp(x) == 'shell']
FINLAB = {'ready': 'готова', 'wb': 'white box', 'shell': 'бетон'}

ownCmp = []
for x in sorted(ready, key=lambda x: -adj(x)):
    ours = x['url'] == OUR_URL
    ownCmp.append([('▶ ' if ours else '') + d1(x['area']), x['floor'].split('/')[0],
                   d1(x['price'] / 1e6), nf(x['ppm']), f'{OUR_FL - fl(x):+d}',
                   nf(adj(x)), FINLAB[grp(x)], {'text': 'Циан →', 'link': x['url']}])

shellCmp = []
for x in sorted(shell, key=lambda x: -(adj(x) + FIT))[:12]:
    shellCmp.append([d1(x['area']), x['floor'].split('/')[0], d1(x['price'] / 1e6),
                     nf(x['ppm']), nf(adj(x) + FIT),
                     d1((x['price'] + x['area'] * FIT) / 1e6),
                     x['seller'] or '—', {'text': 'Циан →', 'link': x['url']}])

srt = sorted(HL, key=lambda x: -x['ppm'])
stats = {
    'n': len(HL), 'all': w(HL), 'rank': srt.index(OUR) + 1,
    'vs_all': OUR['ppm'] / w(HL) - 1, 'grad': GRAD,
    'band': len(band), 'ready': len(ready), 'shell': len(shell),
    'bandW': w(band), 'readyW': w(ready), 'shellW': w(shell),
    'shellKey': w(shell) + FIT,
    'budget': sum(1 for x in HL if 86e6 <= x['price'] <= 106e6),
    'budgetDev': sum(1 for x in HL if 86e6 <= x['price'] <= 106e6 and x['seller'] == 'Застройщик'),
    'dev': sum(1 for x in HL if x['seller'] == 'Застройщик'),
    'readyRank': sorted(ready, key=lambda x: -adj(x)).index(OUR) + 1,
    'fit': FIT,
}
T['towers'], T['ownCmp'], T['shellCmp'], T['ownStats'] = towers, ownCmp, shellCmp, stats
json.dump(T, open('hl/hl_tables.json', 'w'), ensure_ascii=False, indent=1)

print(f"{stats['n']} лотов, Ø {nf(stats['all'])} ₽/м², наш {stats['rank']}-й ({stats['vs_all']:+.1%})")
print(f"застройщик держит {stats['dev']} лотов; в бюджете 86–106 млн — {stats['budget']}, "
      f"из них у застройщика {stats['budgetDev']}")
print(f"база 110–140 м²: {stats['band']} лотов, готовых {stats['ready']}, в бетоне {stats['shell']}")
print(f"  Ø готовых {nf(stats['readyW'])}   Ø бетона {nf(stats['shellW'])} (+отделка = {nf(stats['shellKey'])})")
print(f"  наш лот {stats['readyRank']}-й из {len(ready)} среди готовых\n")
for r in towers: print(' | '.join(r))
print('\nготовые 110–140 м², приведено к 8 этажу:')
for r in ownCmp: print('  ', ' | '.join(str(c['text'] if isinstance(c, dict) else c) for c in r))
