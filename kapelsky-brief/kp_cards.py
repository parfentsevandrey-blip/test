# -*- coding: utf-8 -*-
"""Карточки квартир с ремонтом: кадр из объявления плюс параметры лота.

PICK — какой кадр объявления идёт в карточку. Выбирается глазами по
контактным листам (kp_photos.py sheet): первый кадр часто отдан фасаду,
плану или подъезду, а нужен интерьер.

Кадры приводятся к 3 : 2 и кладутся в assets/ под именем card_<id>.jpg.
Параметры карточки уходят в cian/cards.json, оттуда их берёт kp_data.py.
"""
import json, os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
CIAN = os.path.join(HERE, 'cian')
PHOTOS = os.path.join(HERE, 'photos')
ASSETS = os.path.join(HERE, 'assets')
RATIO, WIDE = 3 / 2, 420

#  id объявления: номер кадра
PICK = {
    313842757: 0,   # Zvonarsky Deluxe, 168 м²
    329709487: 1,   # ЦВЕТ32, 218 м²
    317801090: 0,   # ЦВЕТ32, 110 м²
    318591860: 5,   # ЦВЕТ32, 110 м²
    328268181: 2,   # Dialog, 84,1 м²
    332546164: 0,   # Легенда Цветного, 195 м²
    332613094: 0,   # ЦВЕТ32, 56,2 м²
    325722438: 0,   # Легенда Цветного, 230 м²
    331229874: 0,   # Dialog, 121 м²
    326419200: 0,   # Sole Hill, 63,2 м²
    329083499: 0,   # ЦВЕТ32, 58 м²
    330488248: 0,   # Barkli Park, 135,9 м²
    188390059: 3,   # Barkli Park, 154 м²
    332396217: 0,   # Dialog, 38 м²
    332770622: 0,   # Sole Hill, 36,7 м²
    326159686: 0,   # Dialog, 84,4 м²
    330991729: 0,   # Клубный дом Печатников, 85 м²
    326419265: 1,   # Sole Hill, 32 м²
    332137381: 0,   # Мод, 41 м²
    325937542: 0,   # Sole Hill, 137,6 м²
    332866908: 0,   # Sole Hill, 98,7 м²
    332171247: 0,   # Sole Hill, 81 м²
    329570909: 0,   # Мод, 68 м²
    333351088: 3,   # Волга, 93 м²
}

NAME = {
    'Zvonarsky Deluxe (Звонарский Делюкс)': 'Zvonarsky Deluxe',
    'Barkli Park (Баркли Парк)': 'Barkli Park',
    'Sole Hill (Соле Хилл)': 'Sole Hill',
    'Dialog (Диалог)': 'Dialog',
    'Клубный дом ЦВЕТ32': 'ЦВЕТ32',
    'Клубный дом Печатников': 'Печатников',
}

nf = lambda v: f'{v:,.0f}'.replace(',', ' ')
ru = lambda v: f'{v:.1f}'.replace('.', ',')


def crop(im):
    w, h = im.size
    if w / h > RATIO:
        nw = int(h * RATIO)
        return im.crop(((w - nw) // 2, 0, (w - nw) // 2 + nw, h))
    nh = int(w / RATIO)
    return im.crop((0, (h - nh) // 2, w, (h - nh) // 2 + nh))


if __name__ == '__main__':
    os.makedirs(ASSETS, exist_ok=True)
    lots = {l['id']: l for l in
            json.load(open(os.path.join(CIAN, 'design_lots.json'), encoding='utf-8'))}
    cards = []
    for lot_id, frame in PICK.items():
        l = lots.get(lot_id)
        src = os.path.join(PHOTOS, f'{lot_id}_{frame}.jpg')
        if not l or not os.path.exists(src):
            print(f'{lot_id} — нет лота или кадра {frame}')
            continue
        im = crop(Image.open(src).convert('RGB'))
        if im.width > WIDE:
            im = im.resize((WIDE, int(WIDE / RATIO)), Image.LANCZOS)
        out = os.path.join(ASSETS, f'card_{lot_id}.jpg')
        im.save(out, 'JPEG', quality=84, subsampling=0, optimize=True)
        cards.append({
            'file': f'card_{lot_id}.jpg',
            'name': NAME.get(l['complex'], l['complex']),
            'area': ru(l['area']),
            'floor': f"{l['floor']}" + (f" из {l['floors']}" if l['floors'] else ''),
            'price': ru(l['price'] / 1e6),
            'ppm': nf(l['ppm']),
            'ppmNum': l['ppm'],
            'dist': f"{l['dist'] / 1000:.2f}".replace('.', ','),
            'url': l['url'],
        })
        print(f"{l['complex'][:22]:24s} кадр {frame}  {im.size}  {os.path.getsize(out)//1024:4d} КБ")
    cards.sort(key=lambda c: -c['ppmNum'])
    json.dump(cards, open(os.path.join(CIAN, 'cards.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    print(f'-> cian/cards.json — {len(cards)} карточек')
