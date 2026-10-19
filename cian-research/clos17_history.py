#!/usr/bin/env python3
"""«Кло 17» и «Люче»: карточки всех лотов застройщика, включая снятые, — история цены, даты публикации и снятия.

ЦИАН не отдаёт историю дома для новостроек, поэтому снятые лоты ищем по соседним номерам объявлений:
застройщик выгружает лоты пачкой, и их номера идут подряд вперемешку с чужими объявлениями.
Сбор — паук Scrapling по методике ANTIBOT.md: один поток, пауза 3 с, AutoThrottle, капча SmartCaptcha = блокировка.
python3 clos17_history.py → clos17-data/history_cards.json
"""
import json
import os
import re

from scrapling.fetchers import FetcherSession
from scrapling.spiders import Request, Spider

D = 'clos17-data'
PATH = f'{D}/history_cards.json'
JK = {'Кло 17', 'Фамильный дом Люче'}


def parse_card(h):
    j = h.find('"offerData":')
    if j < 0:
        return None
    od = json.JSONDecoder().raw_decode(h[j + len('"offerData":'):])[0]
    off = od.get('offer') or {}
    geo = off.get('geo') or {}
    nb = off.get('newbuilding') or {}
    jk = nb.get('name') or (geo.get('jk') or {}).get('name') or (od.get('newObject') or {}).get('name')
    bt = off.get('bargainTerms') or {}
    return dict(status=off.get('status'), category=off.get('category'), jk=jk,
                address=', '.join(a.get('name') or '' for a in geo.get('address') or []),
                area=float(off['totalArea']) if off.get('totalArea') else None, floor=off.get('floorNumber'),
                rooms=off.get('roomsCount'), price=bt.get('priceRur') or bt.get('price'), sale_type=bt.get('saleType'),
                created=off.get('creationDate'), archived=off.get('archivedDate'), edited=off.get('editDate'),
                developer=(od.get('company') or {}).get('categoryId') == 2002,
                seller=(od.get('agent') or {}).get('companyName') or (od.get('user') or {}).get('companyName'),
                views=(od.get('stats') or {}).get('totalViewsFormattedString'),
                price_changes=sorted([((c.get('changeTime') or '')[:10], (c.get('priceData') or {}).get('price'))
                                      for c in od.get('priceChanges') or []]))


class Cards(Spider):
    name = 'cian_clos17_cards'
    concurrent_requests = 1
    download_delay = 5.0
    autothrottle_enabled = True
    autothrottle_start_delay = 5.0
    autothrottle_max_delay = 300.0
    max_blocked_retries = 8
    logging_level = 20

    def __init__(self, ids, out):
        self.ids, self.out = ids, out
        super().__init__()

    def configure_sessions(self, manager):
        # без перехода по редиректу: блок — это ответ 302 на /cian-captcha/ или 127.0.0.1, его должен увидеть is_blocked,
        # а не сетевая ошибка (редирект на 127.0.0.1 HTTP-клиент отвергает как SSRF, и паук не включает AutoThrottle)
        manager.add('cian', FetcherSession(impersonate='chrome', stealthy_headers=True, retries=1, timeout=40, follow_redirects=False))

    async def start_requests(self):
        for cid in self.ids:
            yield Request(f'https://www.cian.ru/sale/flat/{cid}/', sid='cian', meta={'id': cid})

    async def is_blocked(self, response):
        if response.status in (401, 403, 407, 429, 444, 500, 502, 503, 504):
            return True
        if response.status in (301, 302, 303, 307):
            loc = response.headers.get('location') or ''
            return 'captcha' in loc or '127.0.0.1' in loc
        return 'cian-captcha' in str(response.url) or 'smartcaptcha' in response.html_content[:200000]

    async def parse(self, response):
        d = parse_card(response.html_content) or {'status': 'нет карточки' if response.status != 404 else 'удалено'}
        d['id'] = (response.meta or {}).get('id') or int(re.search(r'/flat/(\d+)/', str(response.url)).group(1))
        yield d

    async def on_scraped_item(self, item):
        if item.get('id'):
            self.out[str(item['id'])] = item
            json.dump(self.out, open(PATH, 'w'), ensure_ascii=False, indent=1)
            self.logger.info(f"{len(self.out)} | {item['id']} | {item.get('jk')} | {item.get('status')} | {item.get('area')}")
        return item


def main():
    J = json.load(open(f'{D}/jk.json'))
    ids = list(range(321905680, 321905720)) + list(range(321435300, 321435360)) + [int(i) for i in J]
    out = json.load(open(PATH)) if os.path.exists(PATH) else {}
    cards = json.load(open(f'{D}/cards.json'))       # активные лоты уже собраны сегодня (clos17.py cards)
    for k, c in cards.items():
        if k in J and k not in out:
            x = J[k]
            out[k] = dict(status='published', category=x['category'], jk=x['jk'], address=x['address'], area=x['area'],
                          floor=x['floor'], rooms=x['rooms'], price=x['price'], sale_type=x['sale_type'], created=c.get('created'),
                          archived=None, edited=None, developer=x['developer'], seller=c.get('seller'),
                          views=(c.get('stats') or {}).get('totalViewsFormattedString'),
                          price_changes=[tuple(p) for p in c.get('price_changes') or []], id=int(k))
    todo = [i for i in dict.fromkeys(ids) if str(i) not in out]
    print('к загрузке', len(todo))
    res = Cards(todo, out).start()
    ours = {k: v for k, v in out.items() if v.get('jk') in JK}
    print('карточек', len(out), 'из них «Кло 17» и «Люче»', len(ours), '| блокировок', res.stats.blocked_requests_count)


if __name__ == '__main__':
    main()
