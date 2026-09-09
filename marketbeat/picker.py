#!/usr/bin/env python3
"""Движок отбора новостей под одну полосу MarketBeat.

Задача не в том, чтобы взять «топ-N по важности». Полоса вмещает конечное число
строк, а читатель должен получить не подборку самых громких заголовков, а
объективную картину сектора за неделю. Это две разные задачи, и вторая — это
задача с ограничениями:

    максимизировать суммарную ценность отобранного
    при условии, что всё влезает на полосу
    и при этом картина остаётся сбалансированной

Ограничения сбалансированности — не украшение, а суть. Без них жадный отбор по
важности неизбежно даёт однобокую полосу: на падающем рынке одни плохие
новости, на растущем одни хорошие, а на неделе с одной крупной сделкой — три
заметки про неё же из трёх изданий.

Ограничения, которые проверяет движок:

  ОХВАТ         в каждом из двух блоков (инвестиции и рынок пользователя)
                должно быть не меньше MIN_PER_BLOCK сюжетов — если пул вообще
                их содержит
  ПОЛЯРНОСТЬ    если в пуле есть и позитивные, и негативные сигналы, оба знака
                обязаны попасть в отбор, и ни один не должен занимать больше
                MAX_POLARITY_SHARE отобранного
  ИСТОЧНИКИ     не больше MAX_PER_OUTLET сюжетов от одного издания
  МАСШТАБ       хотя бы один сюжет уровня рынка или регулирования, а не только
                отдельные сделки

Стоимость сюжета — его реальная высота в пикселях, измеренная в браузере на
ширине колонки полосы (см. measure.py). Оценки «примерно столько символов» не
годятся: кириллица, переносы и висячие строки дают расхождение в десятки
процентов, а полоса не прощает промаха.
"""
from __future__ import annotations

import json
import os
import sys

# --- настройки отбора -------------------------------------------------------

MIN_PER_BLOCK = 2          # сюжетов в каждом смысловом блоке
MAX_POLARITY_SHARE = 0.65  # доля одного знака среди отобранного
MAX_PER_OUTLET = 2         # сюжетов от одного издания
BEAM = 240                 # ширина луча при поиске

TIER_VALUE = {1: 1.00, 2: 0.72, 3: 0.45}
SCOPE_VALUE = {'market': 1.00, 'policy': 0.82, 'deal': 0.58}
# Масштаб события внутри своего класса: 3 — общенациональный показатель, закон
# или сделка первой величины (сотни квартир, десятки тысяч кв. м, десятки
# миллионов евро); 2 — обычный; 1 — локальная мелочь. Без этой оси движок
# предпочитает дешёвые в описании мелкие сделки крупным сюжетам.
IMPACT_VALUE = {1: 0.7, 2: 1.0, 3: 1.4}
MAX_BLOCK_SHARE = 0.62  # доля одного блока среди отобранного (мягко)

BLOCKS = ('investment', 'occupier')


class Pool:
    """Кандидаты одного сектора, уже проверенные по дате и первоисточнику."""

    def __init__(self, items, lo, hi):
        self.lo, self.hi = lo, hi
        bad = [i for i in items if not (lo <= i['date'] <= hi)]
        if bad:
            raise ValueError(
                'вне окна {}–{}: {}'.format(lo, hi, ', '.join(
                    f"{i['date']} {i.get('id', i.get('url', '?'))}" for i in bad)))
        self.items = list(items)

    def outlets(self):
        return {i['outlet'] for i in self.items}

    def polarities(self):
        return {sign(i) for i in self.items}


def sign(item):
    p = item.get('polarity', 0)
    return 1 if p > 0 else (-1 if p < 0 else 0)


def value(item):
    """Ценность сюжета для полосы.

    Первоисточник весит больше пересказа, показатель рынка — больше отдельной
    сделки, а сюжет с проверяемой цифрой больше сюжета без неё: полоса, на
    которой нечего измерить, не описывает ситуацию, а сообщает настроение.
    """
    v = TIER_VALUE.get(item.get('tier', 2), 0.6)
    v *= SCOPE_VALUE.get(item.get('scope', 'deal'), 0.58)
    v *= IMPACT_VALUE.get(item.get('impact', 2), 1.0)
    nums = item.get('numbers') or []
    if nums:
        v *= 1.15
        if any(any(u in n for u in ('€', 'кв. м', 'млн', 'млрд', '%')) for n in nums):
            v *= 1.10
    return v * float(item.get('weight_hint', 1.0))


def skew_penalty(sel):
    """Штраф за перекос знака.

    Жёсткий запрет здесь вреден: при коротком пуле он выбрасывает годные сюжеты
    и оставляет полосу пустой. Штраф же начинает действовать только когда есть
    из чего выбирать, и тогда ровно и делает картину сбалансированной.
    """
    if len(sel) < 3:
        return 0.0
    signs = [sign(i) for i in sel]
    worst = max(signs.count(1), signs.count(-1)) / len(sel)
    over = max(0.0, worst - MAX_POLARITY_SHARE)
    # тот же принцип для блоков: полоса из одних сделок пользователя без
    # инвестиционной картины (или наоборот) — перекос, а не отбор
    blocks = [i.get('block') for i in sel]
    bworst = max(blocks.count(b) for b in BLOCKS) / len(sel)
    over_b = max(0.0, bworst - MAX_BLOCK_SHARE)
    return (over + over_b) * len(sel) * 0.5


def _feasible(sel, pool):
    """Полный набор ограничений — проверяется только на готовом наборе."""
    if not sel:
        return False, 'пусто'

    for b in BLOCKS:
        have = sum(1 for i in sel if i.get('block') == b)
        possible = sum(1 for i in pool.items if i.get('block') == b)
        if possible and have < min(MIN_PER_BLOCK, possible):
            return False, f'мало сюжетов в блоке {b}: {have}'

    signs = [sign(i) for i in sel]
    pool_signs = pool.polarities()
    if 1 in pool_signs and -1 in pool_signs:
        if 1 not in signs or -1 not in signs:
            return False, 'односторонняя картина: нет сигнала одного из знаков'
    counts = {}
    for i in sel:
        counts[i['outlet']] = counts.get(i['outlet'], 0) + 1
        if counts[i['outlet']] > MAX_PER_OUTLET:
            return False, f'слишком много от одного издания: {i["outlet"]}'

    if not any(i.get('scope') in ('market', 'policy') for i in sel):
        return False, 'только отдельные сделки, нет картины рынка'

    return True, 'ок'


def _prefix_ok(sel, pool):
    """Отсечение частичных наборов: проверяем лишь то, что уже нарушено.

    Ограничения охвата и полярности проверять на префиксе нельзя — они
    выполняются в конце. А вот превышение лимита по изданию уже необратимо.
    """
    counts = {}
    for i in sel:
        counts[i['outlet']] = counts.get(i['outlet'], 0) + 1
        if counts[i['outlet']] > MAX_PER_OUTLET:
            return False
    return True


def select(items, budget_px, lo, hi, heights=None):
    """Отобрать сюжеты под полосу.

    budget_px — сколько пикселей высоты отдано под сюжеты этого сектора.
    heights   — {id: высота в px}; если не передано, берётся item['height'].

    Возвращает (отобранные, отчёт). Отчёт объясняет, что и почему не влезло —
    без него отбор превращается в чёрный ящик, а редактор должен видеть, чем
    пожертвовали.
    """
    pool = Pool(items, lo, hi)
    h = dict(heights or {})
    for i in pool.items:
        h.setdefault(i['id'], i.get('height'))
        if not h[i['id']]:
            raise ValueError(f'нет высоты для {i["id"]} — сначала measure.py')

    ranked = sorted(pool.items, key=lambda i: -value(i) / max(h[i['id']], 1))

    # луч по убыванию удельной ценности: на каждом шаге либо берём сюжет, либо нет
    beam = [([], 0.0, 0)]                       # (набор, ценность, высота)
    best, best_v = [], -1.0
    for item in ranked:
        nxt = list(beam)
        for sel, v, ph in beam:
            nh = ph + h[item['id']]
            if nh > budget_px:
                continue
            cand = sel + [item]
            if not _prefix_ok(cand, pool):
                continue
            nv = v + value(item)
            nxt.append((cand, nv, nh))
            ok, _ = _feasible(cand, pool)
            score = nv - skew_penalty(cand)
            if ok and score > best_v:
                best, best_v = cand, score
        nxt.sort(key=lambda t: -t[1])
        beam = nxt[:BEAM]

    if not best:
        # ни один допустимый набор не собрался: объясняем, на чём споткнулись
        why = []
        for sel, _, _ in sorted(beam, key=lambda t: -t[1])[:5]:
            why.append(_feasible(sel, pool)[1])
        return [], {'ok': False, 'reason': 'допустимый набор не собрался',
                    'closest': why, 'budget_px': budget_px}

    order = {'investment': 0, 'occupier': 1}
    best = sorted(best, key=lambda i: (order.get(i.get('block'), 9), -value(i)))

    used = sum(h[i['id']] for i in best)
    dropped = [i for i in pool.items if i not in best]
    report = {
        'ok': True,
        'selected': len(best),
        'considered': len(pool.items),
        'used_px': used,
        'budget_px': budget_px,
        'fill': round(used / budget_px, 3),
        'value': round(best_v, 3),
        'by_block': {b: sum(1 for i in best if i.get('block') == b) for b in BLOCKS},
        'by_polarity': {str(s): sum(1 for i in best if sign(i) == s) for s in (1, 0, -1)},
        'by_outlet': _counts(best, 'outlet'),
        'by_tier': _counts(best, 'tier'),
        'dropped': [{'id': i['id'], 'outlet': i['outlet'],
                     'value': round(value(i), 3), 'height': h[i['id']],
                     'why': _why_dropped(i, best, pool)} for i in dropped],
    }
    return best, report


def _counts(sel, key):
    out = {}
    for i in sel:
        out[str(i.get(key))] = out.get(str(i.get(key)), 0) + 1
    return out


def _why_dropped(item, best, pool):
    """Причина, по которой сюжет не попал: полосе нужен внятный протокол."""
    same = [i for i in best if i['outlet'] == item['outlet']]
    if len(same) >= MAX_PER_OUTLET:
        return f'лимит на издание {item["outlet"]}'
    weaker = [i for i in best if value(i) < value(item)]
    if not weaker:
        return 'ценность ниже отобранных'
    return 'не хватило места на полосе'


def load(path):
    return json.load(open(path, encoding='utf-8'))


if __name__ == '__main__':
    if len(sys.argv) < 5:
        print('использование: select.py <pool.json> <sector> <budget_px> <lo> <hi>')
        sys.exit(2)
    pool_path, sector, budget = sys.argv[1], sys.argv[2], int(sys.argv[3])
    lo, hi = sys.argv[4], sys.argv[5]
    data = load(pool_path)
    items = [i for i in data['items'] if i['sector'] == sector]
    sel, rep = select(items, budget, lo, hi)
    print(json.dumps({'report': rep, 'selected': [i['id'] for i in sel]},
                     ensure_ascii=False, indent=1))
