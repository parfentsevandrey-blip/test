#!/usr/bin/env python3
"""Стоит ли квартира своих денег: цена м² против уровня ремонта (слепая оценка по фото) с учётом локации,
типа (квартира/апартаменты), панорамного вида, башни и возраста дома.

Вход: flat-data/blind_ratings.json (две независимые слепые оценки на квартиру), offers/cards/reviews.
python3 flat_value.py → flat-data/value.json, reports/kutuzovsky12_value.png
"""
import json
import math

import numpy as np

import flat_report as fr

OUT = 'flat-data/value.json'
PNG = 'reports/kutuzovsky12_value.png'
DISTRICTS = ['Пресненский', 'Хамовники', 'центр']          # база — Дорогомилово
CENTER = {'Арбат', 'Якиманка', 'Замоскворечье', 'Тверской', 'Таганский', 'Басманный', 'Мещанский', 'Красносельский'}


def district(r):
    return 'центр' if r['raion'] in CENTER else r['raion']


def features(r, renov):
    d = district(r)
    rv = r['review'] or {}
    return [1.0, renov, float(r['apart'])] + [float(d == k) for k in DISTRICTS] + [
        float('панорама' in (rv.get('view') or '')), float((r['floors'] or 0) >= 30), float((r['year'] or 2026) >= 2018)]


NAMES = ['константа', 'ремонт, балл', 'апартаменты', 'Пресненский', 'Хамовники', 'другой центр', 'панорамный вид',
         'башня 30+ этажей', 'дом 2018+ г.']


def ols(X, y):
    X, y = np.asarray(X), np.asarray(y)
    beta, *_ = np.linalg.lstsq(X, y, rcond=None)
    res = y - X @ beta
    n, k = X.shape
    s2 = res @ res / (n - k)
    xtx = np.linalg.pinv(X.T @ X)     # pinv: в подвыборках часть признаков постоянна
    # робастные (HC1) стандартные ошибки: разброс цен неоднороден по сегментам
    meat = X.T @ (X * (res ** 2)[:, None])
    se = np.sqrt(np.abs(np.diag(xtx @ meat @ xtx)) * n / (n - k))
    r2 = 1 - res @ res / ((y - y.mean()) @ (y - y.mean()))
    return beta, se, math.sqrt(s2), r2, xtx


def main():
    rows = {r['id']: r for r in fr.load()}
    ratings = json.load(open('flat-data/blind_ratings.json'))
    data = []
    for cid, g in ratings.items():
        r = rows.get(int(cid))
        if r and g.get('overall') is not None:
            data.append((r, g['overall']))
    our = rows[fr.OUR]
    comps = [(r, s) for r, s in data if r['id'] != fr.OUR]
    X = [features(r, s) for r, s in comps]
    y = [math.log(r['ppsqm'] / 1e6) for r, s in comps]
    beta, se, sigma, r2, xtx = ols(X, y)
    s_our = ratings[str(fr.OUR)]['overall']
    x0 = np.array(features(our, s_our))
    pred = math.exp(x0 @ beta)
    se_fit = math.sqrt(x0 @ xtx @ x0) * sigma
    se_pred = math.sqrt(sigma ** 2 + se_fit ** 2)
    out = dict(
        n=len(comps), r2=r2, sigma=sigma, our_score=s_our,
        coef={nm: dict(b=float(b), se=float(s), pct=100 * (math.exp(b) - 1)) for nm, b, s in zip(NAMES, beta, se)},
        pred_ppsqm=pred, pred_price=pred * our['area'],
        pred_lo=math.exp(x0 @ beta - se_fit * 1.96) * our['area'], pred_hi=math.exp(x0 @ beta + se_fit * 1.96) * our['area'],
        band_lo=math.exp(x0 @ beta - se_pred) * our['area'], band_hi=math.exp(x0 @ beta + se_pred) * our['area'],
        ask=our['price'], ask_ppsqm=our['ppsqm'] / 1e6,
        resid_pct=100 * (our['ppsqm'] / 1e6 / pred - 1))
    # остатки аналогов: кто дороже/дешевле своего ремонта и локации
    resid = sorted(((r['ppsqm'] / 1e6) / math.exp(np.array(features(r, s)) @ beta) - 1, r['id']) for r, s in comps)
    out['share_more_expensive'] = sum(1 for d, _ in resid if d > out['resid_pct'] / 100) / len(resid)
    json.dump(out, open(OUT, 'w'), ensure_ascii=False, indent=1)
    return out, comps, beta, s_our


def predict(r, s, beta):
    return math.exp(np.array(features(r, s)) @ beta)


def variants():
    """Проверка устойчивости: та же модель на разных подвыборках и с другими признаками."""
    rows = {r['id']: r for r in fr.load()}
    ratings = json.load(open('flat-data/blind_ratings.json'))
    data = [(rows[int(k)], v['overall']) for k, v in ratings.items() if v['overall'] is not None]
    our, s_our = rows[fr.OUR], ratings[str(fr.OUR)]['overall']
    old = {r['id']: (r['review'] or {}).get('score') for r in rows.values()}
    with_area = lambda r, s: features(r, s) + [math.log(r['area'])]
    specs = [('все аналоги', data, features),
             ('только квартиры (без апартаментов)', [(r, s) for r, s in data if not r['apart']], features),
             ('только Дорогомилово и Пресня', [(r, s) for r, s in data if r['raion'] in ('Дорогомилово', 'Пресненский')], features),
             ('без Хамовников', [(r, s) for r, s in data if r['raion'] != 'Хамовники'], features),
             ('с учётом площади', data, with_area),
             ('первичные оценки (эталон = 8)', [(r, old[r['id']]) for r, s in data if old[r['id']] is not None], features)]
    out = []
    for name, sub, feat in specs:
        comps = [(r, s) for r, s in sub if r['id'] != fr.OUR]
        beta, se, sigma, r2, xtx = ols([feat(r, s) for r, s in comps], [math.log(r['ppsqm'] / 1e6) for r, s in comps])
        s0 = old[fr.OUR] if name.startswith('первичные') else s_our
        p = math.exp(np.array(feat(our, s0)) @ beta)
        out.append(dict(name=name, n=len(comps), r2=r2, point=100 * (math.exp(beta[1]) - 1), price=p * our['area'],
                        over=100 * (our['ppsqm'] / 1e6 / p - 1)))
    return out


def merge_blind(codes_map, files):
    """Две слепые оценки на квартиру → среднее; расхождение ≥ 2 баллов помечается для ручной проверки."""
    by = {}
    for f in files:
        for line in open(f):
            if line.strip():
                r = json.loads(line)
                by.setdefault(r['code'], []).append(r)
    res = {}
    for code, rs in by.items():
        vals = [r['overall'] for r in rs if r.get('overall') is not None]
        sub = {k: round(float(np.mean([r[k] for r in rs if r.get(k) is not None])), 1)
               if any(r.get(k) is not None for r in rs) else None for k in ('materials', 'design', 'execution', 'completeness')}
        res[str(codes_map[code])] = dict(
            code=code, overall=round(float(np.mean(vals)), 2) if vals else None, raters=[r.get('overall') for r in rs],
            spread=(max(vals) - min(vals)) if len(vals) > 1 else None, **sub,
            renders=[r.get('renders') for r in rs], confidence=[r.get('confidence') for r in rs],
            style=rs[0].get('style'), notes=[r.get('notes') for r in rs])
    return res


def chart(comps, beta, s_our, out):
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    from matplotlib import font_manager
    for f in font_manager.findSystemFonts():
        if 'Carlito' in f:
            font_manager.fontManager.addfont(f)
    plt.rcParams.update({'font.family': 'Carlito', 'font.size': 10})
    ink, ink2, grid, surf = '#0b0b0b', '#52514e', '#e4e3df', '#fcfcfb'
    flat_c, apt_c = '#2a78d6', '#eb6834'
    fig, ax = plt.subplots(figsize=(7.2, 4.3), dpi=200)
    fig.patch.set_facecolor(surf)
    ax.set_facecolor(surf)
    rng = np.random.default_rng(7)
    for apart, col, lab in ((False, flat_c, 'квартиры'), (True, apt_c, 'апартаменты')):
        pts = [(s + rng.uniform(-0.12, 0.12), r['ppsqm'] / 1e6) for r, s in comps if r['apart'] == apart]
        ax.scatter([p[0] for p in pts], [p[1] for p in pts], s=34, color=col, alpha=0.85, edgecolor=surf, linewidth=1,
                   label=lab, zorder=3)
    xs = np.linspace(1, 10, 50)
    like = [math.exp(beta[0] + beta[1] * x + beta[8]) for x in xs]       # квартира, Дорогомилово, без панорамы, дом 2018+
    ax.plot(xs, like, color=ink2, linewidth=1.6, zorder=2, label='модель: квартира в Дорогомилове, дом 2018+, без панорамы')
    ax.scatter([s_our], [out['ask_ppsqm']], s=110, color=ink, edgecolor=surf, linewidth=1.5, zorder=5)
    box = dict(boxstyle='round,pad=0.25', facecolor=surf, edgecolor='none')
    ax.annotate(f"ваша цена: 145 млн = {out['ask_ppsqm']:.2f} млн/м²".replace('.', ','), (s_our, out['ask_ppsqm']),
                xytext=(9.95, 2.32), ha='right', fontsize=9.5, color=ink, bbox=box,
                arrowprops=dict(arrowstyle='-', color=ink2, linewidth=0.8, shrinkB=6))
    off = (140 - 10) / 95
    ax.scatter([s_our], [off], s=90, facecolor=surf, edgecolor=ink, linewidth=1.6, zorder=5)
    ax.annotate(f"предложение: 140 млн минус\nмашиноместо ~10 млн = {off:.2f} млн/м²".replace('.', ','), (s_our, off),
                xytext=(9.97, 0.70), ha='right', fontsize=9.5, color=ink, bbox=box,
                arrowprops=dict(arrowstyle='-', color=ink2, linewidth=0.8, shrinkB=6))
    ax.set_xlabel('Ремонт по фото, балл (слепая оценка, среднее двух оценщиков)', color=ink2)
    ax.set_ylabel('Цена в объявлении, млн руб. за м²', color=ink2)
    ax.set_xlim(0.5, 10)
    ax.grid(axis='y', color=grid, linewidth=0.8)
    ax.set_axisbelow(True)
    for s in ('top', 'right'):
        ax.spines[s].set_visible(False)
    for s in ('left', 'bottom'):
        ax.spines[s].set_color(grid)
    ax.tick_params(colors=ink2)
    ax.yaxis.set_major_locator(matplotlib.ticker.MultipleLocator(0.2))
    ax.set_ylim(0.66, 2.9)
    ax.yaxis.set_major_formatter(matplotlib.ticker.FuncFormatter(lambda v, _: f'{v:.1f}'.replace('.', ',')))
    ax.legend(loc='upper left', frameon=False, fontsize=9, labelcolor=ink2)
    fig.tight_layout()
    fig.savefig(PNG, facecolor=surf)


if __name__ == '__main__':
    import sys
    if len(sys.argv) > 1 and sys.argv[1] == 'merge':
        import glob
        mp = json.load(open('flat-data/blind_map.json'))
        res = merge_blind(mp, sorted(glob.glob('flat-data/blind/rate_*.jsonl')))
        json.dump(res, open('flat-data/blind_ratings.json', 'w'), ensure_ascii=False, indent=1)
        print('оценено', sum(1 for v in res.values() if v['overall'] is not None), 'из', len(res))
        sys.exit()
    out, comps, beta, s_our = main()
    chart(comps, beta, s_our, out)
    print(json.dumps({k: v for k, v in out.items() if k != 'coef'}, ensure_ascii=False, indent=1))
    for k, v in out['coef'].items():
        print(f"{k:18} {v['b']:+.3f} ± {v['se']:.3f}  ({v['pct']:+.1f}%)")
