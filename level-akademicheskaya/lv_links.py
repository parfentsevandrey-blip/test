"""Разбор выгрузок Циан по локации ЖК «Левел Академическая» + дедупликация."""
import xml.etree.ElementTree as ET

NS = '{urn:schemas-microsoft-com:office:spreadsheet}'
BASE = '/root/.claude/uploads/f10b7ab2-1385-5931-8285-d55741d60955/'
FILES = {
    'Левел Академическая':   'dbe6fb23-cian____________________20260730_30_____.xls',
    'Файв Тауэрс':           '7a0a6f0f-cian____________20260730_114_____.xls',
    'Вавилова 52':           '856268e7-cian_________52_20260730_7_____.xls',
    'VAVILOVE':              'd6f943b1-cian_vavilove________20260730_4_____.xls',
    'Новочеремушкинская 17': '55e68389-cian___________________17_20260730_7_____.xls',
    'Lunar':                 'c902f60c-cian_lunar______20260730_44______1.xls',
    'Новые Черемушки':       '630a10cd-cian________________20260730_7_____.xls',
    'Вавилов ДОМ':           '35ecb068-cian____________20260730_2_____.xls',
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

def dedupe(lots):
    """Одну квартиру выставляют несколько агентств — схлопываем по
    (площадь, этаж, цена). Совпадение только по этажу и цене дублем НЕ
    считается: у застройщика это разные лоты, часть с террасами или
    в двух уровнях."""
    uniq = {}
    for x in lots:
        k = (round(x['area'], 1), x['floor'], x['price'])
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
    return dedupe(out)

if __name__ == '__main__':
    import json, collections
    all = {n: lots(n) for n in FILES}
    json.dump(all, open('lev/lv_linked.json', 'w'), ensure_ascii=False)
    w = lambda g: sum(x['price'] for x in g) / sum(x['area'] for x in g)
    for n, v in all.items():
        ar = [x['area'] for x in v]; pr = [x['price'] / 1e6 for x in v]
        yr = collections.Counter(x['year'] for x in v).most_common(3)
        print(f"{n:24s} n={len(v):4d}  {min(ar):5.1f}–{max(ar):6.1f} м²  {min(pr):5.1f}–{max(pr):6.1f} млн  Ø {w(v):>9,.0f} ₽/м²".replace(',', ' '))
        print(f"{'':24s}   отделка: {collections.Counter(x['fin'] or '—' for x in v).most_common()}")
        print(f"{'':24s}   продавец: {collections.Counter(x['seller'] or '—' for x in v).most_common()}   год: {yr}")
