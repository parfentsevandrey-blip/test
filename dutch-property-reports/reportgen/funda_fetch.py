"""Снятие страницы funda и funda in business через reader-прокси.

Обе площадки закрыты анти-бот стеной: прямой ``GET``, WebFetch, headless- и
даже headful-Chromium под Xvfb получают страницу проверки («Je bent bijna op de
pagina die je zoekt»). Проходит только reader-прокси ``r.jina.ai``: он рендерит
страницу через браузерную ферму и отдаёт результат Markdown'ом или HTML.

Чего прокси **не** отдаёт: на funda in business описание обрывается на
«Het object …» с кнопкой «Lees de volledige omschrijving» — остаток подгружает
скрипт, и ни один из адресов (``/print/``, ``/omschrijving/``, английская
версия) полного текста не даёт. А в этом остатке лежит самое нужное: годовая
аренда, кадастровые номера, срок энергетической метки и условия сделки.

За полным текстом ходит ``full_description()`` — настоящий Chromium через
Playwright. Стену он проходит, и решает это **одна строка**: переход с
``wait_until="networkidle"``. С ``domcontentloaded`` страница снимается до того,
как отработает сенсор Akamai, и в руках остаётся «Je bent bijna op de pagina die
je zoekt»; с ожиданием тишины в сети проверка успевает пройти сама. Дальше
нажимается кнопка раскрытия, и описание читается целиком.

Галерея снимается по-разному на двух площадках, и это главное, ради чего
модуль существует.

* **funda.nl (жильё).** Кадры объявления лежат на странице ``/media/fotos``, и
  брать их надо **по ссылкам самого объявления** ``…/media/foto/<N>``, а не по
  общей папке ``valentina_media/<a>/<b>/``. Папка ненадёжна: кадры одного
  объявления разъезжаются по двум соседним папкам, а в ту же папку попадают
  снимки чужих листингов из блока «похожие объекты». На Mariaplaats 10 отбор по
  самой полной папке дал 34 кадра, из которых треть была от чужого дома;
  привязка по ссылкам даёт ровно 49 своих, по порядку.
* **funda in business.** Полный список кадров лежит в блоке ``ld+json`` типа
  ``Product`` — его и берём; разметка страницы идёт запасным путём.

Использование:

    python -m reportgen.funda_fetch <url> [<url> ...] [--html-dir raw]
"""

from __future__ import annotations

import argparse
import html as html_mod
import json
import logging
import re
import sys
import time
from collections import Counter, OrderedDict
from pathlib import Path

import requests

log = logging.getLogger(__name__)

JINA = "https://r.jina.ai/"
UA = (
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
)
WALL = "Je bent bijna op de pagina die je zoekt"
TIMEOUT = 240

RESIDENTIAL_SIZE = "?options=width=2160"
BUSINESS_SIZE = "_1440x960.jpg"


def fetch(url: str, *, as_html: bool = False, tries: int = 3) -> str:
    """Страница через reader-прокси; ``as_html`` — отрисованный HTML.

    Прокси иногда отвечает 401 «bad IP reputation»: общий исходящий адрес
    попадает в немилость и через несколько минут выходит из неё сам, поэтому
    попытки разнесены по времени.
    """
    headers = {"User-Agent": UA}
    if as_html:
        headers["X-Return-Format"] = "html"
    last: Exception | None = None
    for attempt in range(tries):
        try:
            resp = requests.get(JINA + url, headers=headers, timeout=TIMEOUT)
            if resp.status_code == 200 and WALL not in resp.text:
                return resp.text
            last = RuntimeError(f"HTTP {resp.status_code}, стена или отказ прокси")
        except requests.RequestException as exc:
            last = exc
        log.warning("попытка %d не прошла: %s", attempt + 1, last)
        time.sleep(5 * 2**attempt)
    raise last or RuntimeError("не удалось снять страницу")


# --------------------------------------------------------------------------
# галерея
# --------------------------------------------------------------------------
def _listing_slug(url: str) -> str | None:
    """Хвост адреса объявления funda.nl: ``huis-mariaplaats-10/44426549``."""
    match = re.search(r"/detail/[^/]+/[^/]+/([^/]+/\d+)/?", url)
    return match.group(1) if match else None


def residential_gallery(url: str, html: str | None = None) -> list[str]:
    """Кадры объявления funda.nl — по ссылкам ``…/media/foto/<N>``."""
    slug = _listing_slug(url)
    if not slug:
        raise ValueError(f"не похоже на адрес объявления funda.nl: {url}")
    if html is None:
        html = fetch(url.rstrip("/") + "/media/fotos/", as_html=True)
    pattern = re.compile(
        r'href="(/detail/[^"]*?' + re.escape(slug) + r'/media/foto/(\d+))"(.{0,4000}?)</a>',
        re.S,
    )
    found: dict[int, str] = {}
    for match in pattern.finditer(html):
        number = int(match.group(2))
        if number in found:
            continue
        ids = re.findall(r"cloud\.funda\.nl/valentina_media/(\d+)/(\d+)/(\d+)\.jpg",
                         match.group(3))
        if ids:
            a, b, c = ids[0]
            found[number] = (f"https://cloud.funda.nl/valentina_media/"
                             f"{a}/{b}/{c}.jpg{RESIDENTIAL_SIZE}")
    return [found[key] for key in sorted(found)]


def business_gallery(html: str) -> list[str]:
    """Кадры объявления funda in business — из блока ``ld+json``.

    Запасной путь — разметка страницы: там кадры объявления и «похожих
    объектов» лежат вперемешку, поэтому берётся папка с наибольшим числом
    разных идентификаторов. Путь именно запасной: на листинге с четырьмя
    кадрами самой полной папкой оказывается чужая.
    """
    urls: list[str] = []
    for block in re.findall(
        r'<script[^>]*type="application/ld\+json"[^>]*>(.*?)</script>', html, re.S
    ):
        try:
            data = json.loads(html_mod.unescape(block))
        except ValueError:
            continue
        photos = data.get("photo") if isinstance(data, dict) else None
        if photos:
            urls = [p["contentUrl"] for p in photos if "contentUrl" in p]

    refs = re.findall(r"valentina_media/(\d+)/(\d+)/(\d+)_\d+x\d+\.jpg", html)
    if urls:
        # в ld+json лежат только кадры превью — остальные добираются из
        # разметки, но лишь из тех папок, которые ld+json назвал своими
        own = {tuple(m.groups()[:2]) for m in
               (re.search(r"valentina_media/(\d+)/(\d+)/", u) for u in urls) if m}
        for a, b, ident in refs:
            if (a, b) not in own:
                continue
            full = f"https://cloud.funda.nl/valentina_media/{a}/{b}/{ident}{BUSINESS_SIZE}"
            if full not in urls:
                urls.append(full)
        return urls

    if not refs:
        return []
    folder = Counter((a, b) for a, b, _ in refs).most_common(1)[0][0]
    ids: OrderedDict[str, bool] = OrderedDict()
    for a, b, ident in refs:
        if (a, b) == folder:
            ids[ident] = True
    a, b = folder
    return [f"https://cloud.funda.nl/valentina_media/{a}/{b}/{i}{BUSINESS_SIZE}"
            for i in ids]


def gallery(url: str) -> list[str]:
    """Кадры объявления по его адресу — площадка определяется по домену."""
    if "fundainbusiness.nl" in url:
        return business_gallery(fetch(url, as_html=True))
    return residential_gallery(url)


# --------------------------------------------------------------------------
# характеристики
# --------------------------------------------------------------------------
def plain_text(html: str) -> str:
    """Текст страницы без разметки — по нему читается таблица «Kenmerken»."""
    body = re.sub(r"<script.*?</script>", "", html, flags=re.S)
    body = re.sub(r"<style.*?</style>", "", body, flags=re.S)
    body = html_mod.unescape(re.sub(r"<[^>]+>", "\n", body))
    return "\n".join(line.strip() for line in body.split("\n") if line.strip())


CHROMIUM = "/opt/pw-browsers/chromium-1194/chrome-linux/chrome"
PROXY_CA = "/root/.ccr/agent-proxy-ca.crt"
EXPAND = (
    "[data-object-description-expand-handle]",
    "button:has-text('volledige omschrijving')",
    "button:has-text('full description')",
)
CONSENT = (
    "#didomi-notice-agree-button",
    "button:has-text('Alles accepteren')",
    "button:has-text('Agree and close')",
)


def _spki_pin(path: str = PROXY_CA) -> str | None:
    """Отпечаток открытого ключа CA агентского прокси для Chromium.

    Прокси перешифровывает TLS, и браузер без этого отпечатка ругается на
    самоподписанный сертификат. Пин прикалывает ровно этот CA — проверка
    сертификатов остаётся включённой.
    """
    import base64
    import subprocess

    if not Path(path).exists():
        return None
    try:
        der = subprocess.run(["openssl", "x509", "-in", path, "-pubkey", "-noout"],
                             capture_output=True, check=True).stdout
        pub = subprocess.run(["openssl", "pkey", "-pubin", "-outform", "der"],
                             input=der, capture_output=True, check=True).stdout
        sha = subprocess.run(["openssl", "dgst", "-sha256", "-binary"],
                             input=pub, capture_output=True, check=True).stdout
    except (OSError, subprocess.CalledProcessError) as exc:
        log.warning("не посчитать отпечаток CA прокси: %s", exc)
        return None
    return base64.b64encode(sha).decode()


def browser_html(url: str, *, timeout: int = 180_000) -> str:
    """Страница настоящим браузером, с раскрытым описанием.

    Запускать под Xvfb: ``xvfb-run -a python -m reportgen.funda_fetch …``.
    Головной режим держится намеренно — headless стену не проходит.
    """
    import os

    from playwright.sync_api import sync_playwright

    args = []
    pin = _spki_pin()
    if pin:
        args.append(f"--ignore-certificate-errors-spki-list={pin}")
    with sync_playwright() as play:
        browser = play.chromium.launch(
            headless=False,
            executable_path=CHROMIUM if Path(CHROMIUM).exists() else None,
            proxy={"server": os.environ["HTTPS_PROXY"]} if os.environ.get("HTTPS_PROXY") else None,
            args=args,
        )
        context = browser.new_context(locale="nl-NL", timezone_id="Europe/Amsterdam",
                                      viewport={"width": 1440, "height": 1200})
        page = context.new_page()
        # именно networkidle: сенсор Akamai должен успеть отработать
        page.goto(url, wait_until="networkidle", timeout=timeout)
        page.wait_for_timeout(4000)
        for group, once in ((CONSENT, True), (EXPAND, False)):
            for selector in group:
                try:
                    page.click(selector, timeout=5000)
                    page.wait_for_timeout(1500)
                    if once:
                        break
                except Exception:          # кнопки может не быть — это не сбой
                    continue
        page.wait_for_timeout(2000)
        if WALL in page.title():
            raise RuntimeError("браузер тоже получил страницу проверки")
        html = page.content()
        context.close()
        browser.close()
        return html


def full_description(url: str, html: str | None = None) -> str:
    """Полный текст блока «Omschrijving» — тот, что прокси обрезает."""
    if html is None:
        html = browser_html(url)
    text = plain_text(html)
    start = text.find("Omschrijving")
    if start < 0:
        return ""
    for marker in ("\nKenmerken", "\nFeatures", "Lees de volledige omschrijving"):
        end = text.find(marker, start + 1)
        if end > start:
            return text[start:end].strip()
    return text[start:start + 12000].strip()


def features(html: str) -> str:
    """Блок характеристик: от «Overdracht» до сведений о районе."""
    text = plain_text(html)
    start = text.find("Overdracht")
    if start < 0:
        return ""
    for marker in ("Ontdek de buurt", "Discover the neighborhood", "Bekijk alle kenmerken"):
        end = text.find(marker, start)
        if end > start:
            return text[start:end]
    return text[start:start + 4000]


# --------------------------------------------------------------------------
# точка входа
# --------------------------------------------------------------------------
def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("urls", nargs="+", help="адреса объявлений funda")
    parser.add_argument("--html-dir", type=Path,
                        help="куда складывать снятые страницы")
    parser.add_argument("--browser", action="store_true",
                        help="снять настоящим браузером и показать полное описание "
                             "(запускать под xvfb-run)")
    args = parser.parse_args(argv)

    logging.basicConfig(level=logging.INFO, format="%(message)s")
    for url in args.urls:
        page = browser_html(url) if args.browser else fetch(url, as_html=True)
        name = re.sub(r"\W+", "-", url).strip("-")[-80:]
        if args.html_dir:
            args.html_dir.mkdir(parents=True, exist_ok=True)
            (args.html_dir / f"{name}.html").write_text(page, encoding="utf-8")
        print(f"\n=== {url}")
        if args.browser:
            print(full_description(url, page) or "описание не нашлось")
            print()
        print(features(page) or "характеристики не нашлись")
        photos = business_gallery(page) if "fundainbusiness.nl" in url else gallery(url)
        print(f"\nкадров: {len(photos)}")
        for photo in photos:
            print(" ", photo)
    return 0


if __name__ == "__main__":
    sys.exit(main())
