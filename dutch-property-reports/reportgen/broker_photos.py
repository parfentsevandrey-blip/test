"""Полная галерея объекта со страницы брокера.

funda отдаёт в HTML только первые пять кадров: остальные подгружает скрипт,
а сам сайт закрыт Akamai — ни curl, ни headless-браузер до него не доходят.
Зато те же объекты лежат на собственных сайтах брокеров и на порталах вроде
bedrijfspand.com, где галерея выложена целиком и без защиты.

Модуль вытаскивает оттуда полный список кадров. Он намеренно устроен просто:
собирает все ссылки на изображения, отбрасывает служебные (логотипы, иконки),
приводит масштабированные варианты к оригиналу и оставляет по одному файлу на
кадр. Разметка у брокеров разная, поэтому надёжнее фильтровать результат, чем
описывать каждый шаблон.

    python -m reportgen.broker_photos https://www.bijabram.nl/property/...
"""

from __future__ import annotations

import logging
import re
import sys
from urllib.parse import urljoin, urlparse

import requests

log = logging.getLogger(__name__)

UA = (
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
)

IMAGE_ATTR = re.compile(
    r"""(?:src|href|data-src|data-lazy|data-large_image|content)\s*=\s*"""
    r"""["']([^"']+\.(?:jpe?g|png|webp)(?:\.webp)?)["']""",
    re.IGNORECASE,
)

# Служебная графика: логотипы, иконки, аватары, баннеры соцсетей
JUNK = re.compile(
    r"logo|icon|sprite|placeholder|avatar|favicon|banner|header|footer|"
    r"mstile|apple-touch|/wp-content/uploads/\d{6,}_|"
    r"^data:|/wp-content/themes/|/plugins/",
    re.IGNORECASE,
)

# Хвосты вида -1024x768, _945x625_fit, _1024x768_canvas — варианты одного кадра
VARIANT = re.compile(r"[-_](\d{2,4})x(\d{2,4})(_[a-z]+)?(?=\.[a-z]+)", re.IGNORECASE)


def _key(url: str) -> str:
    """Ключ кадра: адрес без размерного хвоста."""
    return VARIANT.sub("", url)


def _area(url: str) -> int:
    """Площадь варианта в пикселях; оригинал без хвоста считается крупнейшим."""
    match = VARIANT.search(url)
    if not match:
        return 10**9
    return int(match.group(1)) * int(match.group(2))


def _exists(url: str, timeout: int = 20) -> bool:
    try:
        head = requests.head(url, headers={"User-Agent": UA}, timeout=timeout,
                             allow_redirects=True)
        return head.status_code == 200 and int(head.headers.get("content-length", 1)) > 20000
    except requests.RequestException:
        return False


def fetch(page_url: str, *, timeout: int = 60, prefer_original: bool = True) -> list[str]:
    """Все кадры объекта со страницы брокера, крупнейшим вариантом каждый.

    Масштабированные варианты в разметке иногда есть, а оригинала нет — тогда
    он всё равно обычно лежит рядом по адресу без размерного хвоста, поэтому
    адрес оригинала проверяется запросом.
    """
    response = requests.get(page_url, headers={"User-Agent": UA}, timeout=timeout)
    response.raise_for_status()
    html = response.text

    best: dict[str, str] = {}
    for raw in IMAGE_ATTR.findall(html):
        url = urljoin(page_url, raw)
        if JUNK.search(url):
            continue
        key = _key(url)
        if key not in best or _area(url) > _area(best[key]):
            best[key] = url

    # порядок съёмки — это порядок в разметке, его и сохраняем
    order = {url: html.find(urlparse(url).path.rsplit("/", 1)[-1]) for url in best.values()}
    photos = sorted(best.values(), key=lambda url: order.get(url, 0))
    if prefer_original:
        photos = [_key(url) if _area(url) < 10**9 and _exists(_key(url)) else url
                  for url in photos]
    log.info("%s: кадров %d", page_url, len(photos))
    return photos


def main(argv: list[str]) -> int:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    if not argv:
        print(__doc__)
        return 2
    for url in argv:
        for photo in fetch(url):
            print(photo)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
