"""Разбор выгрузок Циан по конкурентам EDEN + дедупликация.

Формат тот же, что и в остальных справках: Excel 2003 XML, лист «Все_лоты»,
ссылка на объявление лежит в атрибуте ss:HRef ячейки.

Дубли схлопываются по (площадь, номер этажа, цена): одну и ту же квартиру
выставляют несколько агентств отдельными строками. Этажность дома в ключ
не входит — она у разных агентств пишется по-разному.
"""
import xml.etree.ElementTree as ET, glob, json, re, os, collections

NS = {'ss': 'urn:schemas-microsoft-com:office:spreadsheet'}
A = '{urn:schemas-microsoft-com:office:spreadsheet}'
SRC = '/root/.claude/uploads/f10b7ab2-1385-5931-8285-d55741d60955/'

FILES = {
    'STELLA DI MOSCA':  '9b231d77-cian_stelladimoscahotelresidences*.xls',
    'Turandot Residences': '58ace80a-cian_turandotresidences*.xls',
    'ф3': '9142502a-cian*6_20260817_32*.xls',
    'ф4': 'be80c355-cian*20260817_26*.xls',
    'ф5': 'f6684f29-cian*17_20260817_17*.xls',
}

num = lambda s: float(re.sub(r'[^\d.,]', '', str(s)).replace(',', '.') or 0)


def sheet_rows(path, name='Все_лоты'):
    root = ET.parse(path).getroot()
    for w in root.findall('ss:Worksheet', NS):
        if w.get(A + 'Name') != name:
            continue
        out = []
        for row in w.findall('.//ss:Row', NS):
            cells, href, idx = [], None, 0
            for c in row.findall('ss:Cell', NS):
                if c.get(A + 'Index'):
                    idx = int(c.get(A + 'Index')) - 1
                    while len(cells) < idx:
                        cells.append('')
                d = c.find('ss:Data', NS)
                cells.append('' if d is None else (d.text or ''))
                if c.get(A + 'HRef') and not href:
                    href = c.get(A + 'HRef')
                idx += 1
            out.append((cells, href))
        return out
    return []


def parse(path):
    rows = sheet_rows(path)
    hdr_i = next(i for i, (c, _) in enumerate(rows) if c and str(c[0]).strip() == '№')
    hdr = [str(x).strip() for x in rows[hdr_i][0]]
    col = lambda *names: next((hdr.index(n) for n in names if n in hdr), None)
    iA = col('Площадь, м²', 'Площадь')
    iP = col('Цена, ₽', 'Цена')
    iF = col('Этаж')
    iFin = col('Отделка/ремонт', 'Отделка')
    iFsrc = col('Источник отделки')
    iS = col('Тип продавца', 'Продавец')
    iR = col('Категория', 'Комнат')
    iD = col('Описание')
    out = []
    for cells, href in rows[hdr_i + 1:]:
        if not cells or not str(cells[0]).strip().isdigit():
            continue
        g = lambda i: (cells[i] if i is not None and i < len(cells) else '')
        area, price = num(g(iA)), num(g(iP))
        if not area or not price:
            continue
        fl = str(g(iF))
        out.append({'area': round(area, 1), 'price': price, 'ppm': price / area,
                    'floor': fl, 'flNum': int(re.match(r'(\d+)', fl).group(1)) if re.match(r'(\d+)', fl) else 0,
                    'rooms': str(g(iR)), 'fin': str(g(iFin)), 'finSrc': str(g(iFsrc)),
                    'seller': str(g(iS)), 'desc': str(g(iD))[:600], 'url': href or ''})
    return out


def dedupe(lots):
    seen, out = {}, []
    for x in lots:
        k = (x['area'], x['flNum'], x['price'])
        if k in seen:
            if not seen[k]['fin'] and x['fin']:
                seen[k].update(fin=x['fin'], url=x['url'] or seen[k]['url'])
            continue
        seen[k] = x
        out.append(x)
    return out


if __name__ == '__main__':
    res, raw_names = {}, {}
    for label, pat in FILES.items():
        hits = glob.glob(SRC + pat)
        if not hits:
            print('НЕ НАЙДЕН файл для', label); continue
        lots = parse(hits[0])
        title = sheet_rows(hits[0])[0][0][0]
        m = re.search(r'ЖК\s*«([^»]+)»', title)
        real = (m.group(1) if m else label).strip()
        raw_names[label] = title[:80]
        d = dedupe(lots)
        res[real] = d
        print(f'{real:34s} {len(lots):3d} объявлений -> {len(d):3d} лотов  '
              f'[{os.path.basename(hits[0])[:24]}]')
    json.dump(res, open('eden/ed_lots.json', 'w'), ensure_ascii=False, indent=1)
    print()
    for k, v in raw_names.items():
        print(' ', k, '->', v)
