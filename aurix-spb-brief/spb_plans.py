"""Карточки лотов AURIX: планировки и дополнительные параметры.

На странице каждого лота лежит payload Nuxt, где планировки размечены тремя
ключами:

  furnlayouturl — планировка с мебелью (тот вид, что показан на сайте первым)
  layouturl     — та же планировка без мебели
  planurl       — положение квартиры на этаже

Скрипт обходит лоты двух московских проектов, забирает эти ссылки и качает
планировку с мебелью — она идёт в карточки справки.

Проверено 26.08.2026.
"""
import urllib.request, gzip, re, json, os, io, time

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECTS = ('ЛДМ', 'Мариинка Делюкс')
SLUG = {'ЛДМ': 'ldm', 'Мариинка Делюкс': 'mar'}
UA = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 '
                    '(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
      'Accept': 'text/html,image/*,*/*', 'Accept-Encoding': 'gzip, deflate'}


def fetch(url, binary=False):
    r = urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=90)
    b = r.read()
    if r.headers.get('Content-Encoding') == 'gzip':
        b = gzip.decompress(b)
    return b if binary else b.decode('utf-8', 'replace')


def plans_of(html):
    """Возвращает {'С мебелью': url, 'Без мебели': url, 'На этаже': url}."""
    out = {}
    for m in re.finditer(r'"(https://imgs\.etalongroup\.ru/[^"]+)","([^"]{3,20})"', html):
        out[m.group(2)] = m.group(1)
    return out


def extras_of(html):
    """Свободные параметры карточки: отделка, мебель, вид и т. п."""
    t = re.sub(r'<(script|style)[^>]*>.*?</\1>', '', html, flags=re.S)
    t = re.sub(r'<[^>]+>', ' ', t)
    t = re.sub(r'\s+', ' ', t)
    sec = re.search(r'Секция\s*([^\s]*)\s', t)
    return {'section': (sec.group(1) if sec else '').strip()}


if __name__ == '__main__':
    os.makedirs(os.path.join(HERE, 'plans'), exist_ok=True)
    lots = json.load(open(os.path.join(HERE, 'spb_lots.json')))
    sel = [x for x in lots if x['project'] in PROJECTS]
    print(f'лотов к обходу: {len(sel)}')
    for i, x in enumerate(sel, 1):
        try:
            html = fetch(x['url'])
        except Exception as e:
            print(f'  {x["project"]} №{x["number"]} — {e}')
            continue
        pl = plans_of(html)
        x['plans'] = pl
        x.update(extras_of(html))
        key = f"{SLUG[x['project']]}_{x['number']}"
        got = ''
        for label in ('С мебелью', 'Без мебели'):
            if label in pl:
                dst = os.path.join(HERE, 'plans', key + '.png')
                open(dst, 'wb').write(fetch(pl[label], binary=True))
                x['planFile'] = os.path.basename(dst)
                got = label
                break
        print(f'  {i:2d}/{len(sel)} {x["project"]:18s} №{x["number"]:>5s} '
              f'{x["area"]:6.1f} м²  планировок {len(pl)}  скачано: {got or "нет"}')
        time.sleep(0.15)
    json.dump(sel, open(os.path.join(HERE, 'spb_flats.json'), 'w'), ensure_ascii=False, indent=1)
    print('записано ax2_msk.json')
