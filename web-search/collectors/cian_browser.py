"""Запросы к Циану изнутри открытой вкладки www.cian.ru -- как это делает сам сайт.

Транспорт перенесён из рабочего проекта «Описание квартиры» (views_fetcher.js): один постоянный профиль
Chromium, сначала открывается главная, затем API-запросы уходят через fetch(..., credentials: 'include')
из вкладки с её cookie. Темп: страницы 9-16 с, API 2.5-4.5 с. Первая капча / 403 / 429 / HTML вместо
JSON -- полная остановка (StopCaptcha), без повторов и обходов. Ответы кэшируются вызывающим кодом.
"""
import json, os, random, re, time

from playwright.sync_api import sync_playwright

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.environ["PLAYWRIGHT_BROWSERS_PATH"] = os.path.join(ROOT, ".browsers")  # Chromium под нашу версию Playwright
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
CAPTCHA_RE = re.compile(r"<title>\s*Captcha|Я не робот|Вы точно не робот|captcha", re.I)


class StopCaptcha(Exception):
    pass


class CianBrowser:
    def __init__(self, profile=os.path.join(ROOT, "data", "cian_profile"), api_gap=(2.5, 4.5), page_gap=(9, 16),
                 home="https://www.cian.ru/robots.txt"):
        self.profile, self.api_gap, self.page_gap, self.home = profile, api_gap, page_gap, home
        self.last_api = self.last_page = 0.0
        self.on_cian = False
        self.n_api = 0

    def __enter__(self):
        self.pw = sync_playwright().start()
        proxy = os.environ.get("HTTPS_PROXY")
        self.ctx = self.pw.chromium.launch_persistent_context(
            self.profile, headless=True, channel="chromium", proxy={"server": proxy} if proxy else None,
            args=["--disable-blink-features=AutomationControlled"], locale="ru-RU", timezone_id="Europe/Moscow",
            viewport={"width": 1440, "height": 900}, user_agent=UA, ignore_https_errors=bool(proxy))
        self.page = self.ctx.pages[0] if self.ctx.pages else self.ctx.new_page()
        return self

    def __exit__(self, *a):
        self.ctx.close()
        self.pw.stop()

    def _wait(self, last, gap):
        w = last + random.uniform(*gap) - time.time()
        if w > 0:
            time.sleep(w)

    def open(self, url):
        """Открыть страницу как человек; вернуть HTML."""
        if self.last_page:
            self._wait(self.last_page, self.page_gap)
        self.last_page = time.time()
        r = self.page.goto(url, wait_until="domcontentloaded", timeout=60000)
        self.page.wait_for_timeout(1500)
        html = self.page.content()
        text = self.page.evaluate("() => document.body ? document.body.innerText.slice(0, 3000) : ''")
        status = r.status if r else 0
        if CAPTCHA_RE.search(html[:5000]) or CAPTCHA_RE.search(text) or status in (403, 429):
            raise StopCaptcha(f"page {url}: status {status}")
        self.on_cian = 200 <= status < 400
        self.last_api = time.time()
        return html

    def api(self, url, body=None):
        """JSON-запрос из вкладки Циана. body -> POST JSON, иначе GET."""
        if not self.on_cian:
            self.open(self.home)
        self._wait(self.last_api, self.api_gap)
        self.last_api = time.time()
        r = self.page.evaluate("""async ([u, b]) => {
            try {
              const ac = new AbortController(); const tm = setTimeout(() => ac.abort(), 30000);
              const x = await fetch(u, b ? {method: 'POST', credentials: 'include', headers: {'content-type': 'application/json'},
                                            body: JSON.stringify(b), signal: ac.signal} : {credentials: 'include', signal: ac.signal});
              clearTimeout(tm);
              return {status: x.status, body: await x.text()};
            } catch (e) { return {status: 0, body: '', error: String(e).slice(0, 200)}; }
        }""", [url, body])
        self.n_api += 1
        try:
            data = json.loads(r["body"])
        except ValueError:
            data = None
        if r["status"] in (403, 429) or (data is None and CAPTCHA_RE.search(r["body"][:5000] or "")):
            raise StopCaptcha(f"api {url}: status {r['status']}")
        if data is None:
            raise RuntimeError(f"api {url}: status {r['status']} {r.get('error') or r['body'][:200]}")
        return r["status"], data


HIST = "https://api.cian.ru/valuation-offer-history/v4/get-house-offer-history-desktop/"
STAT = "https://api.cian.ru/offer-card/v1/get-offer-card-statistic/"


def house_history(b, offer_id, page=1, per_page=50):
    return b.api(HIST, {"offerId": offer_id, "resultsOnPage": per_page, "page": page, "dealType": "sale"})[1]


def offer_stats(b, offer_id, created):
    return b.api(f"{STAT}?offerId={offer_id}&offerCreationDate={str(created)[:10]}")[1]
