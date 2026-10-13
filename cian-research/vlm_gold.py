"""Эталонный набор для сравнения VLM: объявления разного ремонта со ВСЕМИ фото + листы для ручной разметки."""
import asyncio
import json
import os
import random
import sqlite3
import sys

from PIL import Image, ImageDraw

DB = 'market-data/akademicheskiy.sqlite'
OUT = 'market-data/gold'


def main(n_per=3, seed=7):
    from cian_client import client
    con = sqlite3.connect(DB)
    random.seed(seed)
    pick = []
    for rep, k in (('no', n_per), ('cosmetic', n_per), ('euro', n_per), ('design', n_per)):
        ids = [r[0] for r in con.execute("select cian_id from listing where gone_seen is null and repair=? and is_new=0", (rep,))]
        pick += random.sample(ids, min(k, len(ids)))
    nb = [r[0] for r in con.execute("select cian_id from listing where gone_seen is null and is_new=1 and decoration in ('without','preFine')")]
    pick += random.sample(nb, min(2, len(nb)))
    os.makedirs(OUT, exist_ok=True)
    meta = {}
    for cid in pick:
        h = client().page(f'https://www.cian.ru/sale/flat/{cid}/')
        j = h.find('"offerData":')
        off = json.JSONDecoder().raw_decode(h[j + len('"offerData":'):])[0]['offer']
        urls = [p['fullUrl'] for p in off.get('photos') or [] if p.get('fullUrl')]
        r = con.execute('select repair, decoration, rooms, area, build_year from listing where cian_id=?', (cid,)).fetchone()
        meta[cid] = dict(urls=urls, seller_repair=r[0] or r[1], rooms=r[2], area=r[3], year=r[4])
    print('объявлений', len(meta), 'фото всего', sum(len(m['urls']) for m in meta.values()), flush=True)
    asyncio.run(download(meta))
    json.dump(meta, open(f'{OUT}/meta.json', 'w'), ensure_ascii=False, indent=1)
    for cid, m in meta.items():
        sheet(cid, m)


async def download(meta):
    from scrapling.fetchers import AsyncStealthySession
    async with AsyncStealthySession(headless=True, max_pages=4) as s:
        async def one(cid, i, u):
            path = f'{OUT}/{cid}_{i:02d}.jpg'
            if os.path.exists(path):
                return path
            for _ in range(2):
                try:
                    r = await s.fetch(u)
                    if r.status == 200 and len(r.body) > 1000:
                        im = __import__('io').BytesIO(r.body)
                        img = Image.open(im).convert('RGB')
                        img.thumbnail((768, 768))
                        img.save(path, 'JPEG', quality=85)
                        return path
                except Exception:
                    await asyncio.sleep(2)
        for cid, m in meta.items():
            res = await asyncio.gather(*(one(cid, i, u) for i, u in enumerate(m['urls'])))
            m['paths'] = [p for p in res if p]


def sheet(cid, m, W=300, H=220, cols=6):
    paths = m.get('paths') or []
    rows = (len(paths) + cols - 1) // cols or 1
    out = Image.new('RGB', (W * cols, H * rows + 24), 'white')
    d = ImageDraw.Draw(out)
    d.text((6, 6), f'{cid}  {m["rooms"]}-комн {m["area"]} м2  год {m["year"]}  фото {len(paths)}', fill='black')
    for k, p in enumerate(paths):
        im = Image.open(p)
        im.thumbnail((W - 4, H - 4))
        out.paste(im, ((k % cols) * W, 24 + (k // cols) * H))
        d.text(((k % cols) * W + 4, 26 + (k // cols) * H), str(k), fill='red')
    out.save(f'{OUT}/sheet_{cid}.jpg', quality=70)


if __name__ == '__main__':
    main()
