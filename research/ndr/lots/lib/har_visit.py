"""Visit one allowed page in a real browser like a visitor and save the session as HAR (for REA's network-capture analysis)."""
import sys
import importlib.util
from pathlib import Path
from patchright.sync_api import sync_playwright
spec = importlib.util.spec_from_file_location("clientpolicy", Path(__file__).resolve().parent / "clientpolicy.py")
cp = importlib.util.module_from_spec(spec); spec.loader.exec_module(cp)
url, out = sys.argv[1], sys.argv[2]
cp.check(url, "headless")                     # L28, L29, L32: protected or paused sites are refused before a browser starts
wait = int(sys.argv[3]) if len(sys.argv) > 3 else 8000
with sync_playwright() as p:
    b = p.chromium.launch(headless=True)
    ctx = b.new_context(locale="ru-RU", record_har_path=out, record_har_content="embed")
    cp.shield(ctx, "headless", print)         # L37: no request to a protected host from inside the page
    pg = ctx.new_page()
    pg.goto(url, wait_until="domcontentloaded", timeout=90000)
    pg.wait_for_timeout(wait)
    pg.mouse.wheel(0, 3000); pg.wait_for_timeout(2000)
    ctx.close(); b.close()
