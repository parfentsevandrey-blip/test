"""Выгрузка поквартирных данных AURIX с сайта застройщика.

Список лотов отдаётся сервером прямо в HTML страницы подбора
https://aurix-development.ru/vybor-po-parametram/kvartiry и размечен
микроданными schema.org/Apartment, поэтому парсится без JavaScript.

Из каждой карточки берутся: проект, номер лота, комнатность, корпус, этаж
и этажность, площадь, цена, цена за метр и ссылка на карточку. Планировка
лежит на самой карточке лота — её забирает ax2_plans.py.

Проверено 26.08.2026.
"""
import urllib.request, gzip, re, json, os

HERE = os.path.dirname(os.path.abspath(__file__))
LIST_URL = 'https://aurix-development.ru/vybor-po-parametram/kvartiry'
BASE = 'https://aurix-development.ru'
UA = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 '
                    '(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
      'Accept': 'text/html,application/xhtml+xml',
      'Accept-Language': 'ru-RU,ru;q=0.9', 'Accept-Encoding': 'gzip, deflate'}


def fetch(url):
    r = urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=90)
    b = r.read()
    if r.headers.get('Content-Encoding') == 'gzip':
        b = gzip.decompress(b)
    return b.decode('utf-8', 'replace')


num = lambda s: float(re.sub(r'[^\d,.]', '', s).replace(',', '.') or 0)
intx = lambda s: int(re.sub(r'\D', '', s) or 0)


def parse(html):
    out = []
    for art in re.findall(r'<article[^>]*schema\.org/Apartment[^>]*>(.*?)</article>', html, re.S):
        g = lambda pat, d='': (re.search(pat, art, re.S).group(1) if re.search(pat, art, re.S) else d)
        proj = g(r'<span itemprop="name">([^<]+)</span>')
        if not proj:
            continue
        href = g(r'<a href="(/vybor-po-parametram/kvartiry/[^"]+)"')
        out.append({
            'project': proj.strip(),
            'rooms': intx(g(r'itemprop="numberOfRooms"[^>]*>(\d+)<')),
            'number': g(r'itemprop="apartmentNumber">([^<]+)<').strip(),
            'building': g(r'itemprop="buildingNumber"[^>]*>([^<]+)<').strip(),
            'floor': intx(g(r'itemprop="floorLevel">(\d+)<')),
            'floors': intx(g(r'itemprop="numberOfFloors">(\d+)<')),
            'area': num(g(r'itemprop="value">([\d,]+)<')),
            'price': intx(g(r'itemprop="price" content="([\d\s ]+)"')),
            'ppm': intx(g(r'aria-label="Цена за квадратный метр: ([\d\s ]+) ₽"')),
            'plan': 'Планировка недоступна' not in art,
            'id': g(r'itemprop="identifier" content="(\d+)"'),
            'url': BASE + href if href else '',
        })
    return out


if __name__ == '__main__':
    html = fetch(LIST_URL)
    open(os.path.join(HERE, 'src', 'aurix_flats.html'), 'w').write(html)
    lots = parse(html)
    json.dump(lots, open(os.path.join(HERE, 'ax2_lots.json'), 'w'), ensure_ascii=False, indent=1)

    import collections
    by = collections.Counter(x['project'] for x in lots)
    print(f'всего лотов в выдаче: {len(lots)}')
    for k, v in by.most_common():
        g = [x for x in lots if x['project'] == k]
        pl = sum(1 for x in g if x['plan'])
        print(f'  {k:20s} {v:3d} лотов · планировок {pl:3d} · '
              f'{min(x["area"] for x in g):.1f}–{max(x["area"] for x in g):.1f} м² · '
              f'{min(x["price"] for x in g) / 1e6:.1f}–{max(x["price"] for x in g) / 1e6:.1f} млн ₽')
