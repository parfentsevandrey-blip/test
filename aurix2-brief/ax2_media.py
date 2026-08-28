"""Визуализации двух московских проектов AURIX с сайта застройщика.

Страница проекта на aurix-development.ru — это Nuxt-приложение, и в её HTML
попадает preload-бандл с картинками всех проектов сразу. Поэтому берутся
только те изображения, что стоят в видимой разметке: из HTML вырезаются
все <script>, а дальше собираются <img src> и <source srcset>.

Хвост списка — блок «Другие дома AURIX» с превью соседних проектов;
он отбрасывается по стоп-списку имён файлов.

Проверено 26.08.2026.
"""
import urllib.request, gzip, re, io, os
from urllib.parse import urljoin
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, 'src', 'img')
UA = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 '
                    '(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
      'Accept': '*/*', 'Accept-Encoding': 'gzip, deflate'}

PAGES = {
    'arbat': 'https://aurix-development.ru/projects/arbat-2',
    'omega': 'https://aurix-development.ru/projects/omega-residence',
}
# превью соседних проектов в подвале + служебная графика
SKIP = ('star', 'logo', 'prev_', '_prev', 'malaya_polyanka', 'vid_7', 'Vid18',
        'preview_mob', 'vybrat_', 'menu_image', 'location.png', 'render_params')


def fetch(url, ref=None):
    h = dict(UA)
    if ref:
        h['Referer'] = ref
    r = urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=45)
    b = r.read()
    if r.headers.get('Content-Encoding') == 'gzip':
        b = gzip.decompress(b)
    return b


def page_images(html, base):
    body = re.sub(r'<script.*?</script>', '', html, flags=re.S)
    urls = re.findall(r'<img[^>]+(?:src|data-src)="([^"]+\.(?:jpg|jpeg|png|webp))"', body, re.I)
    urls += [m.split()[0] for m in re.findall(r'<source[^>]+srcset="([^"]+)"', body, re.I)]
    out = []
    for u in urls:
        if any(s in u for s in SKIP) or u in out:
            continue
        out.append(urljoin(base, u))
    return out


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for key, page in PAGES.items():
        html = fetch(page).decode('utf-8', 'replace')
        open(os.path.join(HERE, 'src', f'ax_{key}.html'), 'w').write(html)
        urls = page_images(html, page)
        print(f'== {key}: {len(urls)} изображений в разметке')
        n = 0
        for u in urls:
            try:
                im = Image.open(io.BytesIO(fetch(u, page)))
            except Exception as e:
                print('   ', u.rsplit('/', 1)[-1], '—', e)
                continue
            if im.width < 700:
                continue
            dst = os.path.join(OUT, f'{key}_{n}.jpg')
            im.convert('RGB').save(dst, 'JPEG', quality=92, subsampling=0, optimize=True)
            print(f'   {key}_{n:<2d} {str(im.size):14s} {u.rsplit("/", 1)[-1][:34]}')
            n += 1
