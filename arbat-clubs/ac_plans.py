# -*- coding: utf-8 -*-
"""Планировки шести лотов для карточек.

В объявлениях обоих домов второй кадр — планировка лота. Здесь она
скачивается и приводится к общему формату: белое поле 3 : 2, чтобы карточки
в вёрстке стояли ровным рядом.

Отбор — по три лота на дом: самый компактный, средний по площади и самый
крупный из тех, что сейчас в продаже.
"""
import os, time, urllib.request
from PIL import Image

from ac_data import CLOS, LUCE, mln, nf, one

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, 'assets')
UA = ('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 '
      '(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36')
CW, CH = 660, 440           # холст карточки, 3 : 2


def pick(h):
    a = h['byArea']
    return [a[0], a[len(a) // 2], a[-1]]


def plan_url(lot):
    ph = lot.get('photos') or []
    return ph[1] if len(ph) > 1 else (ph[0] if ph else None)


def build(lot, out):
    url = plan_url(lot)
    if not url:
        return False
    raw = os.path.join(ASSETS, '_raw.jpg')
    req = urllib.request.Request(url, headers={'User-Agent': UA})
    for attempt in range(5):        # прокси иногда рвёт соединение на середине
        try:
            data = urllib.request.urlopen(req, timeout=40).read()
            break
        except Exception as e:
            print(f'  повтор {attempt + 1}: {e}')
            time.sleep(2 * (attempt + 1))
    else:
        return False
    with open(raw, 'wb') as f:
        f.write(data)
    im = Image.open(raw).convert('RGB')
    im.thumbnail((CW - 24, CH - 24), Image.LANCZOS)
    canvas = Image.new('RGB', (CW, CH), 'white')
    canvas.paste(im, ((CW - im.width) // 2, (CH - im.height) // 2))
    canvas.save(out, 'JPEG', quality=88, subsampling=0, optimize=True)
    os.remove(raw)
    return True


if __name__ == '__main__':
    os.makedirs(ASSETS, exist_ok=True)
    cards = []
    for h in (CLOS, LUCE):
        for i, lot in enumerate(pick(h)):
            name = f"plan_{h['tag']}_{i}.jpg"
            if not build(lot, os.path.join(ASSETS, name)):
                print(f'  ! у лота {lot["id"]} нет кадров')
                continue
            cards.append({
                'file': name, 'name': h['name'],
                'area': one(lot['totalArea']), 'floor': f"{lot['floor']} из {lot['floors']}",
                'price': mln(lot['priceRub']), 'ppm': nf(round(lot['ppm'], -4)),
                'url': lot['url'],
            })
            print(f"{name}  {lot['totalArea']:6.1f} м²  {mln(lot['priceRub'])} млн ₽")
    import json
    json.dump(cards, open(os.path.join(HERE, 'ac_cards.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    print(f'-> ac_cards.json, карточек {len(cards)}')
