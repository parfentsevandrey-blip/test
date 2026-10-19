#!/usr/bin/env python3
"""«Кло 17» и «Люче»: все лоты на ЦИАН — изменения цены, срок экспозиции, снятые лоты; где застройщик держит цену, где режет.

Данные: clos17-data/jk.json, cards.json (активные), history_cards.json (активные и снятые — clos17_history.py),
clos17-data/our/deal.json (наша сделка — точка реальной цены).
python3 clos17_breakdown.py → reports/private/Кло17_Люче_история_цен.xlsx, .pdf
"""
import datetime as dt
import json
import os
import statistics as st
from html import escape

import stromynka_report as R
from stromynka_report import m

D = 'clos17-data'
OUT = 'reports/private'
XLSX, PDF, HTML = f'{OUT}/Кло17_Люче_история_цен.xlsx', f'{OUT}/Кло17_Люче_история_цен.pdf', f'{OUT}/clos17_hist.html'
TODAY = dt.date(2026, 10, 19)
DATE = '19 октября 2026 г.'
JK = {'Кло 17': 'Кло 17', 'Фамильный дом Люче': 'Люче'}
UNITS = {'Кло 17': 28, 'Люче': 43}            # по описаниям застройщика на ЦИАН: 26 квартир + 2 пентхауса; 43 квартиры


def d(s):
    return dt.date.fromisoformat(s[:10]) if s else None


def lots():
    H = json.load(open(f'{D}/history_cards.json')) if os.path.exists(f'{D}/history_cards.json') else {}
    J = json.load(open(f'{D}/jk.json'))
    C = json.load(open(f'{D}/cards.json'))
    rows = {}
    for k, x in J.items():
        c = C.get(k) or {}
        rows[k] = dict(id=int(k), jk=JK[x['jk']], status='published', area=x['area'], floor=x['floor'], rooms=x['rooms'],
                       price=x['price'], developer=x['developer'], created=d(c.get('created') or x['created']), archived=None,
                       views=(c.get('stats') or {}).get('totalViewsFormattedString'), pc=[tuple(p) for p in c.get('price_changes') or []])
    for k, h in H.items():
        if h.get('jk') not in JK:
            continue
        r = rows.get(k) or dict(id=int(k), jk=JK[h['jk']])
        r.update(status=h.get('status'), area=h.get('area') or r.get('area'), floor=h.get('floor') or r.get('floor'),
                 rooms=h.get('rooms') or r.get('rooms'), price=h.get('price') or r.get('price'),
                 developer=h.get('developer') if h.get('developer') is not None else r.get('developer'),
                 created=d(h.get('created')) or r.get('created'), archived=d(h.get('archived')),
                 views=h.get('views') or r.get('views'), pc=[tuple(p) for p in h.get('price_changes') or []] or r.get('pc', []))
        rows[k] = r
    out = []
    for r in rows.values():
        pc = sorted(p for p in r['pc'] if p[1])
        first = pc[0][1] if pc else r['price']
        last = r['price'] or (pc[-1][1] if pc else None)
        active = r['status'] == 'published'
        end = TODAY if active else (r['archived'] or TODAY)
        r.update(first=first, last=last, change=(last / first - 1) if first and last else 0,
                 change_date=pc[-1][0] if len(pc) > 1 else None, n_changes=max(0, len(pc) - 1), active=active,
                 days=(end - r['created']).days if r.get('created') else None, ppsqm=last / r['area'] if last and r.get('area') else None)
        r['kind'] = ('пентхаус' if (r['area'] or 0) > 300 else
                     '1 спальня' if r['rooms'] == 1 else '2 спальни' if r['rooms'] == 2 else '3 спальни' if r['rooms'] == 3 else '4 спальни')
        out.append(r)
    return sorted(out, key=lambda r: (r['jk'], not r['active'], r['floor'] or 0, r['area'] or 0))


def summary(rows):
    res = {}
    for jk in ('Кло 17', 'Люче'):
        v = [r for r in rows if r['jk'] == jk]
        act = [r for r in v if r['active'] and r['developer']]
        gone = [r for r in v if not r['active']]
        res[jk] = dict(units=UNITS[jk], active=len(act), assign=len([r for r in v if r['active'] and not r['developer']]),
                       gone=len(gone), cut=[r for r in v if r['change'] < -0.03], up=[r for r in v if r['change'] > 0.005],
                       flat=[r for r in act if abs(r['change']) <= 0.005],
                       days_active=st.median([r['days'] for r in act if r['days'] is not None]) if act else None,
                       days_gone=st.median([r['days'] for r in gone if r['days'] is not None]) if gone else None)
    return res


if __name__ == '__main__':
    rows = lots()
    for r in rows:
        print(r['jk'], r['id'], r['status'], r['kind'], r['area'], r['floor'], round((r['first'] or 0) / 1e6, 1), '→',
              round((r['last'] or 0) / 1e6, 1), f"{100 * r['change']:+.1f}%", r['change_date'], r['created'], r['archived'], r['days'], 'дн')
    for jk, s in summary(rows).items():
        print(jk, {k: (len(v) if isinstance(v, list) else v) for k, v in s.items()})
