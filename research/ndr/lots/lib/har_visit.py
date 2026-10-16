"""Visit one allowed page in a real browser like a visitor and save the session as HAR (for REA's network-capture analysis)."""
import sys
from patchright.sync_api import sync_playwright
url, out = sys.argv[1], sys.argv[2]
wait = int(sys.argv[3]) if len(sys.argv) > 3 else 8000
with sync_playwright() as p:
    b = p.chromium.launch(headless=True)
    ctx = b.new_context(locale="ru-RU", record_har_path=out, record_har_content="embed")
    pg = ctx.new_page()
    pg.goto(url, wait_until="domcontentloaded", timeout=90000)
    pg.wait_for_timeout(wait)
    pg.mouse.wheel(0, 3000); pg.wait_for_timeout(2000)
    ctx.close(); b.close()
