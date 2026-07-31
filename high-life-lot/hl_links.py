"""Разбор выгрузок Циан по локации ЖК HIGH LIFE + дедупликация."""
import re
import xml.etree.ElementTree as ET

NS = '{urn:schemas-microsoft-com:office:spreadsheet}'
BASE = '/root/.claude/uploads/f10b7ab2-1385-5931-8285-d55741d60955/'
FILES = {
    'HIGH LIFE':            'b9956059-cian_highlife________20260730_289_____.xls',
    'Павелецкая от Гранель': 'e0c8a8c1-cian_____________________20260730_212______1.xls',
    'Воксхолл':             '679e0810-cian__________20260730_95_____.xls',
    'Клубный дом ОПУС':     'cfe0e138-cian________________20260730_91_____.xls',
    'ЛОТ от Аквилон':       '98bbfb50-cian______________20260730_84_____.xls',
    'Эра':                  '3c264d18-cian_____20260730_326_____.xls',
    'Левел Павелецкая Сити': '277b3372-cian_____________________20260730_169_____.xls',
    'ЖК «А»':               '9c01fb54-cian___20260730_65_____.xls',
    'Левел Павелецкая':     '904b702c-cian_________________20260730_5_____.xls',
    'Монблан':              '6709a528-cian_________20260730_88_____.xls',
    'Стремянный 2':         '5c266f80-cian___________2_20260730_15_____.xls',
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
EXCLUDE = {
    # Одна и та же квартира выставлена дважды с разной площадью в шапке —
    # тексты объявлений совпадают дословно, цена и этаж одинаковые.
    'https://www.cian.ru/sale/flat/331035960/',   # 119,0 м² = 120,0 м² эт. 12, 116,5 млн
    'https://www.cian.ru/sale/flat/325826914/',   # 130,0 м² = 130,4 м² эт. 17, 120,0 млн
    'https://www.cian.ru/sale/flat/329065328/',   # 122,4 м² эт. 5, 113,5 млн — дубль 329178951
}

flno = lambda x: int(re.match(r'(\d+)', x['floor']).group(1)) if re.match(r'(\d+)', x['floor'] or '') else None

def dedupe(lots):
    """Одну квартиру выставляют несколько агентств — схлопываем по
    (площадь, номер этажа, цена). Этажность дома в ключ НЕ входит: внутри
    HIGH LIFE один и тот же корпус К1 подписан и «/32», и «/47», и «/48».
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
    json.dump(all, open('hl/hl_linked.json', 'w'), ensure_ascii=False)
    w = lambda g: sum(x['price'] for x in g) / sum(x['area'] for x in g)
    for n, v in all.items():
        ar = [x['area'] for x in v]; pr = [x['price'] / 1e6 for x in v]
        yr = collections.Counter(x['year'] for x in v).most_common(3)
        print(f"{n:24s} n={len(v):4d}  {min(ar):5.1f}–{max(ar):6.1f} м²  {min(pr):5.1f}–{max(pr):6.1f} млн  Ø {w(v):>9,.0f} ₽/м²".replace(',', ' '))
        print(f"{'':24s}   отделка: {collections.Counter(x['fin'] or '—' for x in v).most_common()}")
        print(f"{'':24s}   продавец: {collections.Counter(x['seller'] or '—' for x in v).most_common()}   год: {yr}")
