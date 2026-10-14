#!/usr/bin/env python3
"""Профили бизнес-центров со страниц ЦИАН (паук Scrapling + HTTP-клиент с отпечатком Chrome, см. ANTIBOT.md).

python3 bc_pages.py  → commercial-data/bc_pages.json (класс, площадь, год, этажность, парковка, ставки, доступ, лифты,
                        инфраструктура, инженерия, метро с временем пешком, фото здания)
"""
import json
import os
import re

from scrapling.fetchers import FetcherSession
from scrapling.spiders import Request, Spider

OUT = 'commercial-data/bc_pages.json'
LABELS = ['Класс', 'Общая площадь', 'Год постройки', 'Этажность', 'Парковка', 'Арендная ставка', 'Стоимость продажи', 'Цена продажи',
          'Доступ в здание', 'Вход', 'Лифт', 'Инфраструктура', 'Технические характеристики', 'Стены и несущие конструкции',
          'Вентиляция', 'Кондиционирование', 'Отопление', 'Пожаротушение', 'Электроснабжение', 'Система бесперебойного питания',
          'Категория электронадежности', 'Сообщить об ошибке', 'Предложения']


def text_lines(h):
    t = re.sub(r'<script[^>]*>.*?</script>', ' ', h, flags=re.S)
    t = re.sub(r'<style[^>]*>.*?</style>', ' ', t, flags=re.S)
    t = re.sub(r'<[^>]+>', '\n', t)
    return [x.strip() for x in t.split('\n') if x.strip()]


def section(lines, start, stops):
    try:
        i = lines.index(start)
    except ValueError:
        return []
    out = []
    for x in lines[i + 1:]:
        if x == start and not out:
            continue
        if x in stops:
            break
        out.append(x)
    return out


def parse(h):
    L = text_lines(h)
    first = {}
    for lab in ('Класс', 'Общая площадь', 'Год постройки', 'Этажность', 'Парковка', 'Арендная ставка'):
        v = section(L, lab, LABELS)
        if v:
            first[lab] = ' '.join(v[:6])
    d = dict(
        cls=first.get('Класс'), total_area=first.get('Общая площадь'), year=first.get('Год постройки'), floors=first.get('Этажность'),
        parking=first.get('Парковка'), rent_range=first.get('Арендная ставка'),
        access=' '.join(section(L, 'Доступ в здание', LABELS)[:3]), entrance=' '.join(section(L, 'Вход', LABELS)[:3]),
        lifts=' '.join(section(L, 'Лифт', LABELS)[:4]),
        infra=section(L, 'Инфраструктура', LABELS)[:20],
        tech={lab: ' '.join(section(L, lab, LABELS)[:3]).split(' ЦИАН')[0] for lab in ('Стены и несущие конструкции', 'Вентиляция', 'Кондиционирование',
                                                                     'Отопление', 'Пожаротушение', 'Категория электронадежности')})
    m = re.search(r'<h1[^>]*>(.*?)</h1>', h, re.S)
    d['title'] = re.sub(r'<[^>]+>', '', m.group(1)).strip() if m else None
    metro = []
    for mm in re.finditer(r'"transportType":"([^"]+)","calcTimeWalk":(\d+),"time":(\d+),[^}]*?"name":"([^"]+)"', h):
        metro.append(dict(name=mm.group(4), walk=int(mm.group(2)), time=int(mm.group(3)), type=mm.group(1)))
    seen, md = set(), []
    for x in metro:
        if x['name'] not in seen:
            seen.add(x['name'])
            md.append(x)
    d['metro'] = md[:4]
    photos = []
    m = re.search(r'"photos":\[(.*?)\]', h)
    for u in re.findall(r'"fullUrl":"([^"]+)"', m.group(1) if m else ''):
        if u not in photos:
            photos.append(u)
    d['photos'] = photos[:8]
    m = re.search(r'"coordinates":\{"lat":([\d.]+),"lng":([\d.]+)\}', h)
    d['lat'], d['lng'] = (float(m.group(1)), float(m.group(2))) if m else (None, None)
    desc = re.search(r'"description":"(.{40,3000}?)"', h)
    m = re.search(r'"types":\[([^\]]*)\]', h)
    d['types'] = re.findall(r'"([^"]+)"', m.group(1)) if m else []
    d['description'] = desc.group(1).encode().decode('unicode_escape', 'ignore')[:1500] if desc else None
    return d


BLOCK_MARKS = ('подозрительный трафик', 'captcha', 'Капча')


class BCPages(Spider):
    """Страницы БЦ по методике ANTIBOT.md: лёгкий HTTP-клиент Scrapling с отпечатком desktop Chrome (данные БЦ есть
    в серверном HTML — браузер с десятками фоновых запросов не нужен), одна сессия, один поток, пауза ~2 с,
    AutoThrottle (удваивает паузу или ждёт Retry-After при блокировке) и повтор заблокированных запросов."""
    name = 'cian_bc_pages'
    concurrent_requests = 1
    download_delay = 2.0
    autothrottle_enabled = True
    autothrottle_start_delay = 2.0
    autothrottle_max_delay = 180.0
    max_blocked_retries = 6
    logging_level = 20

    def __init__(self, urls, out):
        self.urls, self.out = urls, out
        super().__init__()

    def configure_sessions(self, manager):
        manager.add('cian', FetcherSession(impersonate='chrome', retries=2, timeout=40))

    async def start_requests(self):
        for u in self.urls:
            yield Request(u, sid='cian')

    async def is_blocked(self, response):
        if response.status in (401, 403, 407, 429, 444, 500, 502, 503, 504):
            return True
        return any(m in response.html_content[:300000] for m in BLOCK_MARKS) and 'cf-bsc-detail' not in response.html_content

    async def parse(self, response):
        d = parse(response.html_content)
        d.update(url=response.url, status=response.status)
        yield d

    async def on_scraped_item(self, item):
        self.out[item['url']] = item
        json.dump(self.out, open(OUT, 'w'), ensure_ascii=False, indent=1)
        self.logger.info(f"{sum(1 for v in self.out.values() if v.get('title'))} профилей | {item.get('title')} | {item.get('cls')}")
        return item


if __name__ == '__main__':
    cl = json.load(open('commercial-data/clusters.json'))
    urls = {}
    for key, res in cl.items():
        for b in res['bcs'].values():
            if b.get('url'):
                urls[b['url']] = b['url']
    out = json.load(open(OUT)) if os.path.exists(OUT) else {}
    todo = [u for u in sorted(urls) if not out.get(u, {}).get('title')]
    print('к загрузке:', len(todo), 'из', len(urls))
    res = BCPages(todo, out).start()
    print('профилей:', sum(1 for v in out.values() if v.get('title')), 'из', len(urls), '| блокировок:', res.stats.blocked_requests_count)
