# -*- coding: utf-8 -*-
"""Выборка лотов по каждому дому и планировки к ним.

Из каждого дома берётся до пяти лотов, растянутых по цене метра: самый дешёвый,
самый дорогой и три между ними. Этого хватает, чтобы показать вилку и средний
метр, не превращая справку в прайс-лист.

Планировку объявления приходится искать: в галерее Циан она лежит без метки,
где-то среди фотографий. Кадры оцениваются по «белизне» — план это почти белый
лист с тонкими линиями, — а окончательный выбор сверяется глазами по
контактному листу.

    python3 kp_plans.py            # выбрать лоты, скачать кадры, оценить
    python3 kp_plans.py sheet      # контактные листы в plans/_sheet*.jpg
"""
import json, os, sys, urllib.request
import concurrent.futures as cf

HERE = os.path.dirname(os.path.abspath(__file__))
CIAN = os.path.join(HERE, 'cian')
OUT = os.path.join(HERE, 'plans')
PER_HOUSE = 5
NOT_A_PLAN = {332546164}   # белый интерьер, который движок принял за чертёж
UA = ('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 '
      '(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36')


def raw_lots():
    out = {}
    for f in ('mesh-new.json', 'metro-new.json', 'mesh-resale.json', 'metro-resale.json'):
        for l in json.load(open(os.path.join(CIAN, f), encoding='utf-8'))['lots']:
            out[l['id']] = l
    return out


def sample():
    """До пяти лотов на дом, растянутых по цене метра."""
    peers = json.load(open(os.path.join(CIAN, 'peers.json'), encoding='utf-8'))
    coh = json.load(open(os.path.join(CIAN, 'cohort.json'), encoding='utf-8'))['lots']
    key = lambda l: l['complex'] or ' '.join(x for x in (l['street'], l['house']) if x)
    raw = raw_lots()
    groups = []
    for r in peers:
        ls = [l for l in coh
              if key(l) == r['name'] and l['source'].endswith(r['kind'])]
        ls.sort(key=lambda l: l['ppm'])
        n = len(ls)
        if n <= PER_HOUSE:
            pick = ls
        else:
            idx = sorted({round(i * (n - 1) / (PER_HOUSE - 1)) for i in range(PER_HOUSE)})
            pick = [ls[i] for i in idx]
        pick.sort(key=lambda l: -l['ppm'])
        groups.append({
            'no': r['no'], 'name': r['name'], 'kind': r['kind'],
            'n': n, 'dist': r['dist'], 'ppmMed': r['ppmMed'],
            'lots': [{**l, 'photos': (raw.get(l['id']) or {}).get('photos') or []}
                     for l in pick],
        })
    return groups


def fetch(args):
    url, dst = args
    if os.path.exists(dst):
        return
    try:
        req = urllib.request.Request(url, headers={'User-Agent': UA,
                                                   'Referer': 'https://www.cian.ru/'})
        open(dst, 'wb').write(urllib.request.urlopen(req, timeout=40).read())
    except Exception as e:
        print(f'  {os.path.basename(dst)} — {e}')


def whiteness(path):
    """Доля почти белых и несочных пикселей: у плана она высокая."""
    from PIL import Image
    im = Image.open(path).convert('RGB').resize((80, 80))
    ok = 0
    for r, g, b in im.getdata():
        if min(r, g, b) > 225 and max(r, g, b) - min(r, g, b) < 22:
            ok += 1
    return ok / 6400


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    groups = sample()
    print(f'домов {len(groups)}, лотов в выборке {sum(len(g["lots"]) for g in groups)}')

    if len(sys.argv) > 1 and sys.argv[1] == 'assets':
        # Планировка вписывается целиком: у части объявлений это не чертёж,
        # а рекламная карточка, где план занимает половину кадра.
        from PIL import Image
        BOX = (360, 300)
        data = json.load(open(os.path.join(CIAN, 'sample.json'), encoding='utf-8'))
        made = 0
        for g in data:
            for l in g['lots']:
                fr = l.get('planFrame')
                if fr is None:
                    continue
                src = os.path.join(OUT, f"{l['id']}_{fr}.jpg")
                if not os.path.exists(src):
                    l['planFrame'] = None
                    continue
                im = Image.open(src).convert('RGB')
                im.thumbnail(BOX, Image.LANCZOS)
                card = Image.new('RGB', BOX, (255, 255, 255))
                card.paste(im, ((BOX[0] - im.width) // 2, (BOX[1] - im.height) // 2))
                card.save(os.path.join(HERE, 'assets', f"plan_{l['id']}.jpg"),
                          'JPEG', quality=88, subsampling=0, optimize=True)
                l['plan'] = f"plan_{l['id']}.jpg"
                made += 1
        json.dump(data, open(os.path.join(CIAN, 'sample.json'), 'w', encoding='utf-8'),
                  ensure_ascii=False, indent=1)
        print(f'планировок в assets: {made}')
        sys.exit()

    if len(sys.argv) > 1 and sys.argv[1] == 'sheet':
        from PIL import Image, ImageDraw, ImageFont
        F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 14)
        FB = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 12)
        rows = [l for g in groups for l in g['lots']]
        per, cell = 12, 150
        pages = [rows[i:i + 8] for i in range(0, len(rows), 8)]
        for pi, page in enumerate(pages):
            sh = Image.new('RGB', (per * cell, len(page) * (cell + 22)), 'white')
            dr = ImageDraw.Draw(sh)
            for ri, l in enumerate(page):
                y = ri * (cell + 22)
                dr.text((4, y + cell + 3),
                        f"{l['id']}  {l['complex'] or l['street']}  {l['area']} м²", fill='black', font=F)
                for ci in range(per):
                    p = os.path.join(OUT, f"{l['id']}_{ci}.jpg")
                    if not os.path.exists(p):
                        continue
                    im = Image.open(p).convert('RGB')
                    im.thumbnail((cell - 4, cell - 4))
                    sh.paste(im, (ci * cell + 2, y + 2))
                    dr.rectangle([ci * cell + 2, y + 2, ci * cell + 22, y + 17], fill='black')
                    dr.text((ci * cell + 6, y + 3), str(ci), fill='white', font=FB)
            sh.save(os.path.join(OUT, f'_sheet{pi}.jpg'), quality=76)
            print(os.path.join(OUT, f'_sheet{pi}.jpg'), sh.size)
        sys.exit()

    jobs = []
    for g in groups:
        for l in g['lots']:
            for n, u in enumerate(l['photos'][:12]):
                jobs.append((u, os.path.join(OUT, f"{l['id']}_{n}.jpg")))
    with cf.ThreadPoolExecutor(8) as ex:
        list(ex.map(fetch, jobs))
    print(f'кадров {len(jobs)}')

    for g in groups:
        for l in g['lots']:
            best, score = None, 0
            for n in range(12):
                p = os.path.join(OUT, f"{l['id']}_{n}.jpg")
                if not os.path.exists(p):
                    continue
                try:
                    w = whiteness(p)
                except Exception:
                    continue
                if w > score:
                    best, score = n, w
            l['planFrame'] = best if (score > 0.35 and l['id'] not in NOT_A_PLAN) else None
            l['planScore'] = round(score, 2)
            l.pop('photos', None)
    json.dump(groups, open(os.path.join(CIAN, 'sample.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    found = sum(1 for g in groups for l in g['lots'] if l['planFrame'] is not None)
    print(f'-> cian/sample.json — планировка найдена у {found} лотов')
