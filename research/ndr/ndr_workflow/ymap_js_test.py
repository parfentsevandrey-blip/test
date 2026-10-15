from playwright.sync_api import sync_playwright
with sync_playwright() as p:
    b=p.chromium.launch(channel="chrome", headless=False, args=["--use-gl=angle","--use-angle=swiftshader","--enable-unsafe-swiftshader"])
    pg=b.new_page(viewport={"width":1200,"height":800}, locale="ru-RU")
    msgs=[]; pg.on("console", lambda m: msgs.append(m.text))
    pg.goto("http://127.0.0.1:8811/test.html")
    try: pg.wait_for_function("window.READY===true", timeout=30000); print("ready")
    except Exception as e: print("not ready", e)
    pg.wait_for_timeout(3000)
    pg.screenshot(path="/tmp/claude-0/-home-user-test/ea57c60a-b461-5b01-9fed-bf21bf3c9920/scratchpad/report/maps/js_test.png")
    print(msgs[:5]); b.close()
