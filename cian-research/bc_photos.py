#!/usr/bin/env python3
"""Фото ключевых бизнес-центров (по 4 из объявления с наибольшим числом фото) и листы для визуальной оценки."""
import asyncio, io, json, os, sys
from PIL import Image, ImageDraw, ImageFont

OUT = 'commercial-data/bcphotos'
SEL = {
 'yard': ['Магистраль Плаза', 'Линкор', '1 Zhukov (1 Жуков)', 'Ярд (The Yard)', 'Минерал Беговая', 'Бега', 'Строение 3', 'Интерьер',
          'Строение 1|Хорошёвский район', '11с1|4-я Магистральная', 'на ул. Зорге, 1', 'Корпус 13', 'Маршала Жукова 4',
          'TOP TOWER', 'ASPACE Хорошевская', 'Эир', 'Аэродом', 'ARCUS III (АРКУС III)'],
 'imagine': ['Строение 4', 'Строение 9', 'Строение 1|Каширское', 'Строение 12', 'Варшавская Плаза', 'Imagine Plaza (Имеджин Плаза)', 'Гвоздь 2',
             'W-Plaza (В Плаза)', 'W-Plaza II', 'Danilov Plaza (Данилов Плаза)', 'Новоданиловский Дом', 'Nagatino i-land (Нагатино-Айленд)',
             'Строение 16', 'КНОП', 'Чайка Плаза 10', 'на ул. Нагатинская, 3А', 'Ломоносов',
             'Newton Plaza (Ньютон Плаза)', 'DM Tower (ДМ Тауэр)', 'ЭОН', 'Technopark Plaza (Технопарк Плаза)'],
}


def pick(cl, key, rows):
    out = []
    for spec in SEL[key]:
        name, _, addr = spec.partition('|')
        cands = []
        for b in cl[key]['bcs'].values():
            if (b['name'] or '').startswith(name[:20]) and (not addr or addr in (b['address'] or '')):
                cands.append(b)
        if not cands:
            print('нет БЦ', spec); continue
        b = max(cands, key=lambda b: len(b['sale']) + len(b['rent']))
        ids = b['sale'] + b['rent']
        best = max(ids, key=lambda i: len(rows[i]['photos']))
        out.append(dict(spec=spec, bc=b, offer=best, photos=rows[best]['photos'][:4]))
    return out


async def download(todo):
    from scrapling.fetchers import AsyncStealthySession
    async with AsyncStealthySession(headless=True, max_pages=4) as s:
        async def one(path, u):
            if os.path.exists(path):
                return
            for _ in range(2):
                try:
                    r = await s.fetch(u)
                    if r.status == 200 and len(r.body) > 1000:
                        im = Image.open(io.BytesIO(r.body)).convert('RGB'); im.thumbnail((700, 700)); im.save(path, 'JPEG', quality=80)
                        return
                except Exception:
                    await asyncio.sleep(2)
        for k in range(0, len(todo), 16):
            await asyncio.gather(*(one(*t) for t in todo[k:k + 16]))


def sheets(sel, key):
    W, H, per = 260, 190, 6
    font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 15)
    for s in range(0, len(sel), per):
        part = sel[s:s + per]
        img = Image.new('RGB', (W * 4 + 10, (H + 24) * len(part)), 'white')
        d = ImageDraw.Draw(img)
        for i, x in enumerate(part):
            b = x['bc']
            y = i * (H + 24)
            d.text((4, y + 3), f"{s+i+1}. {b['name']} | {b['cls']} | {b['year']} | эт {b['floors']} | м.{b['metro']} {b['walk']} мин | {(b['address'] or '').replace('Россия, ','').replace('Москва, ','')[:40]}", fill='black', font=font)
            for k in range(4):
                p = f"{OUT}/{x['offer']}_{k}.jpg"
                if os.path.exists(p):
                    im = Image.open(p); im.thumbnail((W - 4, H - 4)); img.paste(im, (k * W, y + 22))
        img.save(f'{OUT}/sheet_{key}_{s // per + 1}.jpg', quality=72)


if __name__ == '__main__':
    cl = json.load(open('commercial-data/clusters.json'))
    rows = {r['id']: r for r in json.load(open('commercial-data/offers.json'))}
    os.makedirs(OUT, exist_ok=True)
    allsel = {}
    todo = []
    for key in SEL:
        sel = pick(cl, key, rows)
        allsel[key] = [dict(spec=x['spec'], offer=x['offer'], bc_key=str(x['bc'].get('url') or x['bc']['name'])) for x in sel]
        for x in sel:
            todo += [(f"{OUT}/{x['offer']}_{k}.jpg", u) for k, u in enumerate(x['photos'])]
        allsel[key + '_full'] = sel
    asyncio.run(download(todo))
    for key in SEL:
        sheets(allsel[key + '_full'], key)
    json.dump({k: v for k, v in allsel.items() if not k.endswith('_full')}, open(f'{OUT}/selection.json', 'w'), ensure_ascii=False, indent=1)
    print('ok', len(todo))
