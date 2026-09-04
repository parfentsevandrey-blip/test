# -*- coding: utf-8 -*-
"""Кадры из объявлений «Кло 17» и «Фамильного дома Люче».

Оба дома продаёт застройщик, поэтому в объявлениях лежат официальные
визуализации, интерьеры и планировки. Кадры повторяются от лота к лоту,
дубли отсеиваются по имени файла в CDN.

    python3 ac_photos.py          # скачать кадры в photos/
    python3 ac_photos.py sheet    # контактные листы photos/_sheet_*.jpg
"""
import json, os, sys, urllib.request
import concurrent.futures as cf
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, 'photos')
UA = ('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 '
      '(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36')
HOUSES = [('clos17', 'clos17.json'), ('luce', 'luce.json')]


def shots(file):
    """Все уникальные кадры дома в порядке появления."""
    seen, out = set(), []
    for l in json.load(open(os.path.join(HERE, file), encoding='utf-8'))['lots']:
        for url in l.get('photos') or []:
            name = url.rsplit('/', 1)[-1].split('-')[0]
            if name in seen:
                continue
            seen.add(name)
            out.append((name, url, l['id']))
    return out


def fetch(args):
    url, dst = args
    if os.path.exists(dst) and os.path.getsize(dst) > 8000:
        return dst
    req = urllib.request.Request(url, headers={'User-Agent': UA})
    try:
        data = urllib.request.urlopen(req, timeout=40).read()
    except Exception as e:
        print(f'  ! {url} — {e}')
        return None
    with open(dst, 'wb') as f:
        f.write(data)
    try:
        Image.open(dst).verify()
    except Exception:
        os.remove(dst)
        print(f'  ! битый файл {dst}')
        return None
    return dst


def sheet(tag, files, cols=6, cell=300):
    """Контактный лист: выбирать кадры для справки удобнее глазами."""
    rows = (len(files) + cols - 1) // cols
    sh = Image.new('RGB', (cols * cell, rows * (cell + 26)), 'white')
    for i, f in enumerate(files):
        im = Image.open(f).convert('RGB')
        im.thumbnail((cell, cell))
        x, y = (i % cols) * cell, (i // cols) * (cell + 26)
        sh.paste(im, (x + (cell - im.width) // 2, y + (cell - im.height) // 2))
    path = os.path.join(OUT, f'_sheet_{tag}.jpg')
    sh.save(path, quality=88)
    print(f'-> {path}  ({len(files)} кадров)')


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for tag, file in HOUSES:
        sh = shots(file)
        jobs = [(url, os.path.join(OUT, f'{tag}_{i:02d}.jpg'))
                for i, (_, url, _) in enumerate(sh)]
        if sys.argv[1:2] != ['sheet']:
            print(f'{tag}: кадров {len(jobs)}')
            with cf.ThreadPoolExecutor(8) as ex:
                list(ex.map(fetch, jobs))
        got = [d for _, d in jobs if os.path.exists(d)]
        print(f'{tag}: скачано {len(got)}')
        if sys.argv[1:2] == ['sheet']:
            sheet(tag, got)
