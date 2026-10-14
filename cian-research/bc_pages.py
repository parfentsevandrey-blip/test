#!/usr/bin/env python3
"""Профили бизнес-центров со страниц ЦИАН (браузер Scrapling: StealthySession — страницы БЦ закрыты антиботом для HTTP-клиента).

python3 bc_pages.py  → commercial-data/bc_pages.json (класс, площадь, год, этажность, парковка, ставки, доступ, лифты,
                        инфраструктура, инженерия, метро с временем пешком, фото здания)
"""
import asyncio
import json
import os
import random
import re

OUT = 'commercial-data/bc_pages.json'
LABELS = ['Класс', 'Общая площадь', 'Год постройки', 'Этажность', 'Парковка', 'Арендная ставка', 'Стоимость продажи', 'Цена продажи',
          'Доступ в здание', 'Вход', 'Лифт', 'Инфраструктура', 'Технические характеристики', 'Стены и несущие конструкции',
          'Вентиляция', 'Кондиционирование', 'Отопление', 'Пожаротушение', 'Электроснабжение', 'Система бесперебойного питания',
          'Категория электронадежности', 'Сообщить об ошибке']


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
        tech={lab: ' '.join(section(L, lab, LABELS)[:3]) for lab in ('Стены и несущие конструкции', 'Вентиляция', 'Кондиционирование',
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


async def one(s, sem, k, key, url, urls, out):
    async with sem:
        for attempt in range(3):
            try:
                # данные БЦ приходят в серверном HTML — ждать полной загрузки сети не нужно
                r = await s.fetch(url, network_idle=False, timeout=90000)
                h = r.html_content if hasattr(r, 'html_content') else r.body.decode('utf-8', 'ignore')
                if r.status == 429 or 'подозрительный трафик' in h[:200000]:
                    out[key] = dict(url=url, error='429')
                    await asyncio.sleep(90)       # антибот притормозил — пауза и повтор
                    continue
                d = parse(h)
                d.update(url=url, status=r.status)
                out[key] = d
                break
            except Exception as e:
                out[key] = dict(url=url, error=str(e)[:200])
                await asyncio.sleep(5)
        json.dump(out, open(OUT, 'w'), ensure_ascii=False, indent=1)
        print(f'{k}/{len(urls)} {out[key].get("title") or url} | {out[key].get("cls")} | {out[key].get("year")}', flush=True)
        await asyncio.sleep(4 + random.random() * 4)


async def run(urls, out):
    from scrapling.fetchers import AsyncStealthySession
    sem = asyncio.Semaphore(1)
    async with AsyncStealthySession(headless=True, max_pages=1) as s:
        await asyncio.gather(*[one(s, sem, k, key, url, urls, out) for k, (key, url) in enumerate(urls, 1)
                               if not (key in out and out[key].get('cls'))])


if __name__ == '__main__':
    cl = json.load(open('commercial-data/clusters.json'))
    urls = {}
    for key, res in cl.items():
        for b in res['bcs'].values():
            if b.get('url'):
                urls[b['url']] = b['url']
    out = json.load(open(OUT)) if os.path.exists(OUT) else {}
    asyncio.run(run(sorted(urls.items()), out))
    print('профилей:', sum(1 for v in out.values() if v.get('cls')), 'из', len(urls))
