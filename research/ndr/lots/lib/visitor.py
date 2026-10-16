"""Browse a developer's catalog like a visitor, in a real browser (patchright Chromium, the engine of Scrapling's browser
fetchers): open a page that robots.txt allows, follow the site's own link to the project's flats, press «show more» /
«next page» with human pauses, and keep the JSON the page itself receives. We never request the site's API ourselves.
A page that shows a captcha stops the visit (no captcha solving)."""
import json
import random
import re
import time


class Challenge(Exception):
    pass


def visit(start_url, link_text, more_text, api_part, ok_marker, sort_clicks=(), max_clicks=200, gap=(20, 40), log=print):
    from patchright.sync_api import sync_playwright
    got = []
    with sync_playwright() as p:
        b = p.chromium.launch(headless=True)
        pg = b.new_page(locale="ru-RU", timezone_id="Europe/Moscow")

        first = []                                       # responses before the first click (the page's own initial load)

        def keep(r):
            if re.search(api_part, r.url) and r.status in (200, 201) and not clicked:
                first.append(r)
        clicked = []
        pg.on("response", keep)
        try:
            pg.goto(start_url, wait_until="domcontentloaded", timeout=90000)
            pg.wait_for_timeout(5000)
            if ok_marker not in pg.content():               # a challenge page instead of the site: stop, do not solve
                raise Challenge(start_url)
            if link_text:                                   # the site's own link / button to the project's flats
                time.sleep(random.uniform(*gap) / 2)
                if link_text.startswith("css:"):           # a link hidden in a menu: click it the way the menu would
                    pg.evaluate("(css) => document.querySelector(css).click()", link_text[4:])
                else:
                    pg.get_by_text(re.compile(link_text)).first.click(timeout=30000)
                pg.wait_for_timeout(8000)
            pg.wait_for_timeout(3000)
            for r in first:
                try:
                    got.append(r.json())
                except Exception as e:
                    log("visitor", "initial response unreadable", r.url[-80:], str(e)[:80])
            clicked.append(1)
            if sort_clicks:                                  # a stable order (e.g. «Сначала дешевле») so pages do not overlap
                label = sort_clicks[-1]
                sel = pg.locator("select", has=pg.locator("option", has_text=label)).first
                with pg.expect_response(lambda r: re.search(api_part, r.url), timeout=45000) as resp:
                    sel.select_option(label=label, force=True)
                got.clear()
                got.append(resp.value.json())
                log("visitor", "sorted:", label, resp.value.url[-80:])
                pg.wait_for_timeout(3000)
            clicks = 0
            while more_text and clicks < max_clicks:
                pg.mouse.wheel(0, 30000)
                pg.wait_for_timeout(1500)
                btn = pg.locator("button:visible, a:visible", has_text=re.compile(more_text, re.I)).first
                if not btn.count():
                    break
                label = btn.text_content().strip()[:60]
                try:
                    with pg.expect_response(lambda r: re.search(api_part, r.url), timeout=45000) as resp:
                        btn.scroll_into_view_if_needed(timeout=10000)
                        btn.click(timeout=15000)              # the page loads the next portion and shows it
                    got.append(resp.value.json())
                    log("visitor", "click", clicks + 1, label, resp.value.url[-60:])
                except Exception:
                    log("visitor", start_url, "no response after click", clicks, label)
                    break
                clicks += 1
                if clicks % 10 == 0:
                    log("visitor", start_url, "clicks", clicks, label)
                time.sleep(random.uniform(*gap))
        finally:
            b.close()
    return got
