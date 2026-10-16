import json, sys, time
import importlib.util
from pathlib import Path
from playwright.sync_api import sync_playwright
spec = importlib.util.spec_from_file_location("clientpolicy", Path(__file__).resolve().parent / "lib" / "clientpolicy.py")
cp = importlib.util.module_from_spec(spec); spec.loader.exec_module(cp)
url = sys.argv[1]
cp.check(url, "headless")                     # L28, L29, L32
reqs = []
with sync_playwright() as p:
    b = p.chromium.launch(headless=True)
    ctx = b.new_context(locale="ru-RU", viewport={"width": 1600, "height": 1000})
    cp.shield(ctx, "headless", print)         # L37
    pg = ctx.new_page()
    def on_req(r):
        if r.resource_type in ("xhr", "fetch"):
            reqs.append({"url": r.url, "method": r.method, "post": (r.post_data or "")[:3000]})
    pg.on("request", on_req)
    pg.goto(url, wait_until="domcontentloaded", timeout=90000)
    time.sleep(6)
    # open the "Квартиры" tab / first room row
    for sel in ["text=Квартиры", "text=/\\d+ предложени/"]:
        try:
            pg.locator(sel).first.click(timeout=8000); time.sleep(5)
        except Exception as e:
            print("click fail", sel, str(e)[:100])
    pg.screenshot(path="raw/probe.png")
    b.close()
for r in reqs:
    if "cian" in r["url"] and not any(x in r["url"] for x in ("telemetry", "metrics", "log", "ab-experiments", "favorites")):
        print(r["method"], r["url"][:200]); 
        if r["post"]: print("   ", r["post"][:800])
