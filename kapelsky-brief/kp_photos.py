# -*- coding: utf-8 -*-
"""Фотографии квартир с ремонтом из объявлений вторички.

Отбираются лоты когорты, у которых в карточке стоит дизайнерский ремонт.
С каждого объявления скачивается несколько кадров: первый кадр часто отдан
фасаду или плану, поэтому нужный интерьер выбирается глазами по контактному
листу, а не по индексу.

    python3 kp_photos.py            # скачать кадры в photos/
    python3 kp_photos.py sheet      # собрать контактные листы в photos/_sheet*.jpg
"""
import json, os, sys, urllib.request
import concurrent.futures as cf

HERE = os.path.dirname(os.path.abspath(__file__))
CIAN = os.path.join(HERE, 'cian')
OUT = os.path.join(HERE, 'photos')
TAKE = [0, 1, 2, 3, 4, 5]        # какие кадры объявления тянуть
UA = ('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 '
      '(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36')


def lots():
    """Лоты когорты с дизайнерским ремонтом, вместе со ссылками на фото."""
    cards = {}
    for f in ('resale_cards.json', 'mod_cards.json'):
        p = os.path.join(CIAN, f)
        if os.path.exists(p):
            for c in json.load(open(p, encoding='utf-8')):
                if c.get('id'):
                    cards[c['id']] = c
    raw = {}
    for f in ('mesh-resale.json', 'metro-resale.json'):
        for l in json.load(open(os.path.join(CIAN, f), encoding='utf-8'))['lots']:
            raw[l['id']] = l
    coh = {l['id']: l for l in
           json.load(open(os.path.join(CIAN, 'cohort.json'), encoding='utf-8'))['lots']}

    out = []
    for i, c in cards.items():
        if c.get('repairType') != 'design':
            continue
        l, k = raw.get(i), coh.get(i)
        if not l or not k or not l.get('photos'):
            continue
        out.append({
            'id': i, 'complex': l.get('complex') or l.get('street'),
            'area': l['totalArea'], 'floor': l.get('floorNumber') or k.get('floor'),
            'floors': l.get('floorsCount') or k.get('floors'),
            'price': l['priceRub'], 'ppm': k['ppm'], 'year': k.get('year'),
            'dist': k['dist'], 'url': l.get('url'), 'photos': l['photos'],
        })
    out.sort(key=lambda r: -r['ppm'])
    return out


def fetch(args):
    url, dst = args
    if os.path.exists(dst):
        return dst
    try:
        req = urllib.request.Request(url, headers={'User-Agent': UA,
                                                   'Referer': 'https://www.cian.ru/'})
        open(dst, 'wb').write(urllib.request.urlopen(req, timeout=40).read())
        return dst
    except Exception as e:
        print(f'  {os.path.basename(dst)} — {e}')
        return None


def download(ls):
    os.makedirs(OUT, exist_ok=True)
    jobs = []
    for l in ls:
        for n in TAKE:
            if n < len(l['photos']):
                jobs.append((l['photos'][n], os.path.join(OUT, f"{l['id']}_{n}.jpg")))
    with cf.ThreadPoolExecutor(8) as ex:
        got = [x for x in ex.map(fetch, jobs) if x]
    print(f'кадров скачано {len(got)} из {len(jobs)}')


def sheets(ls):
    """Контактный лист по каждому лоту: подписан id и номер кадра."""
    from PIL import Image, ImageDraw, ImageFont
    F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 15)
    FB = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 13)
    per, cell = 6, 260
    pages = [ls[i:i + 6] for i in range(0, len(ls), 6)]
    for pi, page in enumerate(pages):
        sheet = Image.new('RGB', (per * cell, len(page) * (cell + 24)), 'white')
        dr = ImageDraw.Draw(sheet)
        for ri, l in enumerate(page):
            y = ri * (cell + 24)
            dr.text((4, y + cell + 4),
                    f"{l['id']}  {l['complex']}  {l['area']} м²  {l['ppm'] // 1000} тыс/м²",
                    fill='black', font=F)
            for ci, n in enumerate(TAKE):
                p = os.path.join(OUT, f"{l['id']}_{n}.jpg")
                if not os.path.exists(p):
                    continue
                im = Image.open(p).convert('RGB')
                im.thumbnail((cell - 6, cell - 6))
                sheet.paste(im, (ci * cell + 3, y + 3))
                dr.rectangle([ci * cell + 3, y + 3, ci * cell + 26, y + 20], fill='black')
                dr.text((ci * cell + 8, y + 4), str(n), fill='white', font=FB)
        out = os.path.join(OUT, f'_sheet{pi}.jpg')
        sheet.save(out, quality=78)
        print(out, sheet.size)


if __name__ == '__main__':
    ls = lots()
    print(f'лотов с дизайнерским ремонтом: {len(ls)}')
    if len(sys.argv) > 1 and sys.argv[1] == 'sheet':
        sheets(ls)
    else:
        download(ls)
        json.dump(ls, open(os.path.join(CIAN, 'design_lots.json'), 'w', encoding='utf-8'),
                  ensure_ascii=False, indent=1)
        print('-> cian/design_lots.json')
