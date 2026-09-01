"""Скачивание визуализаций с официального сайта проекта.

kapelsky5.ru собран на Next.js. Пути к картинкам лежат прямо в HTML — и в виде
обычных `/img/home/...webp`, и внутри параметра url у оптимизатора
`/_next/image?url=...`. Поэтому галерея снимается без запуска браузера:
достаточно распарсить разметку главной страницы.

В репозитории лежат только те файлы, которые реально попали в вёрстку
(список — в kp_renders.py). Полная галерея — 66 файлов, около 14 МБ.
"""
import os, re, sys, urllib.parse, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, 'src', 'img')
SITE = 'https://www.kapelsky5.ru'
UA = ('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 '
      '(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36')


def get(url):
    req = urllib.request.Request(url, headers={'User-Agent': UA, 'Referer': SITE + '/'})
    return urllib.request.urlopen(req, timeout=40).read()


def paths(html):
    out = set(re.findall(r'/img/[A-Za-z0-9_/.\-]+\.(?:webp|jpg|jpeg|png)', html))
    for m in re.findall(r'url=(https%3A%2F%2Fkapelsky5\.ru[^&"]+)', html):
        out.add(urllib.parse.unquote(m).replace('https://kapelsky5.ru', ''))
    return sorted(p for p in out
                  if 'favicon' not in p and '/line' not in p and 'logo' not in p)


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    html = get(SITE + '/').decode('utf-8', 'replace')
    found = paths(html)
    print(f'в разметке {len(found)} картинок')
    for path in found:
        name = path.replace('/img/', '').replace('/', '_')
        dst = os.path.join(OUT, name)
        if os.path.exists(dst):
            continue
        try:
            data = get(SITE + path)
        except Exception as e:
            print(f'{name:34s} — {e}')
            continue
        open(dst, 'wb').write(data)
        print(f'{name:34s} {len(data) // 1024:5d} КБ')
