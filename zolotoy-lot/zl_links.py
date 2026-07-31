"""Разбор выгрузок Циан по локации ЖК «Золотой» + дедупликация."""
import re
import xml.etree.ElementTree as ET

NS = '{urn:schemas-microsoft-com:office:spreadsheet}'
BASE = '/root/.claude/uploads/f10b7ab2-1385-5931-8285-d55741d60955/'
FILES = {
    'Золотой':            '5c1ce694-cian_____________________20260731_14_____.xls',
    'Клубный дом DUO':    '22c6e284-cian___________duo____20260731_31_____.xls',
    'Софийский':          'af517b70-cian___________20260731_15_____.xls',
    'Резиденция 1864':    '04d2746a-cian___________1864_20260731_10_____.xls',
    'BALCHUG VIEWPOINT':  '7e08f483-cian_balchugviewpoint_______________20260731_8_____.xls',
    'Дом Лаврушинский':   'a105b2ad-cian_________________20260731_114_____.xls',
    'Клубный дом Космо 4/22': '6f39b6e5-cian________________422_20260731_28_____.xls',
    'Русские Сезоны':     '9a890e94-cian_______________20260731_10_____.xls',
}

def rows(path):
    for ws in ET.parse(path).getroot().findall(f'{NS}Worksheet'):
        if not ws.get(f'{NS}Name', '').startswith('Все_лоты'): continue
        out = []
        for row in ws.find(f'{NS}Table').findall(f'{NS}Row'):
            cells, href, idx = [], None, 0
            for c in row.findall(f'{NS}Cell'):
                ix = c.get(f'{NS}Index')
                if ix:
                    ix = int(ix) - 1
                    while idx < ix: cells.append(''); idx += 1
                if c.get(f'{NS}HRef'): href = c.get(f'{NS}HRef')
                dt = c.find(f'{NS}Data')
                cells.append('' if dt is None else ''.join(dt.itertext())); idx += 1
            out.append((cells, href))
        return out
    return []

# Лоты, исключённые из анализа
EXCLUDE = set()

flno = lambda x: int(re.match(r'(\d+)', x['floor']).group(1)) if re.match(r'(\d+)', x['floor'] or '') else None

def dedupe(lots):
    """Одну квартиру выставляют несколько агентств — схлопываем по
    (площадь, номер этажа, цена). Этажность дома в ключ НЕ входит: внутри
    HIGH LIFE один и тот же корпус К1 был подписан и «/32», и «/47», и «/48»;
    в элитных домах Замоскворечья та же болезнь.
    Совпадение только по этажу и цене дублем НЕ считается: у застройщика
    это разные лоты, часть с террасами или в двух уровнях."""
    uniq = {}
    for x in lots:
        k = (round(x['area'], 1), flno(x), x['price'])
        cur = uniq.get(k)
        if cur is None or (not cur['fin'] and x['fin']): uniq[k] = x
    return list(uniq.values())

def lots(name):
    rs = rows(BASE + FILES[name])
    hi = next(i for i, (c, _) in enumerate(rs) if c and c[0] == '№')
    H = {n: i for i, n in enumerate(rs[hi][0])}
    g = lambda c, k: c[H[k]] if k in H and H[k] < len(c) else ''
    out = []
    for c, href in rs[hi + 1:]:
        if not g(c, 'Площадь, м²'): continue
        try:
            out.append(dict(cat=g(c, 'Категория'), area=float(g(c, 'Площадь, м²')), floor=g(c, 'Этаж'),
                            corp=g(c, 'Корпус / секция'), price=int(g(c, 'Цена, ₽')), ppm=int(g(c, 'Цена за м², ₽')),
                            fin=g(c, 'Отделка/ремонт'), seller=g(c, 'Тип продавца'), year=g(c, 'Год дома'),
                            url=(href or '').split('?')[0]))
        except ValueError: pass
    return dedupe([x for x in out if x['url'] not in EXCLUDE])

if __name__ == '__main__':
    import json, collections
    all = {n: lots(n) for n in FILES}
    json.dump(all, open('zl/zl_linked.json', 'w'), ensure_ascii=False)
    w = lambda g: sum(x['price'] for x in g) / sum(x['area'] for x in g)
    for n, v in all.items():
        ar = [x['area'] for x in v]; pr = [x['price'] / 1e6 for x in v]
        yr = collections.Counter(x['year'] for x in v).most_common(3)
        print(f"{n:24s} n={len(v):4d}  {min(ar):5.1f}–{max(ar):6.1f} м²  {min(pr):5.1f}–{max(pr):6.1f} млн  Ø {w(v):>9,.0f} ₽/м²".replace(',', ' '))
        print(f"{'':24s}   отделка: {collections.Counter(x['fin'] or '—' for x in v).most_common()}")
        print(f"{'':24s}   продавец: {collections.Counter(x['seller'] or '—' for x in v).most_common()}   год: {yr}")
