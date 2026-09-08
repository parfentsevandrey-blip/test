import asyncio, os, re
from playwright.async_api import async_playwright

BASE = "https://xn--e1adnd0h.xn--d1aqf.xn--p1ai"
URLS = [
    f"{BASE}/auctions/617599/",
    f"{BASE}/auctions/620551/",
    f"{BASE}/auctions/622000/",
    f"{BASE}/auctions/618406/",
    f"{BASE}/auctions/planned/585155/",
    f"{BASE}/auctions/planned/581087/",
    f"{BASE}/auctions/planned/593909/",
    f"{BASE}/auctions/planned/602837/",
]

OUT = os.path.dirname(os.path.abspath(__file__))
PROXY = os.environ.get("HTTPS_PROXY", "http://127.0.0.1:33179")
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36")


async def main():
    async with async_playwright() as pw:
        browser = await pw.chromium.launch(
            executable_path="/opt/pw-browsers/chromium-1194/chrome-linux/chrome",
            headless=True,
            # TLS 1.3 ClientHello (post-quantum key share) is severed by the proxy
            # middlebox -> ERR_CONNECTION_RESET. Cap at 1.2. See docs/cian/README.md.
            args=["--no-sandbox", "--disable-dev-shm-usage",
                  "--ssl-version-max=tls1.2",
                  "--disable-blink-features=AutomationControlled"],
            proxy={"server": PROXY},
        )
        ctx = await browser.new_context(
            locale="ru-RU", timezone_id="Europe/Moscow",
            viewport={"width": 1440, "height": 950}, user_agent=UA,
        )
        page = await ctx.new_page()

        # Warm-up: land on the site root so the ServicePipe JS challenge sets cookies.
        print("warm-up...", flush=True)
        try:
            await page.goto(BASE + "/", wait_until="domcontentloaded", timeout=90000)
            for _ in range(30):
                await page.wait_for_timeout(2000)
                t = await page.evaluate("document.body ? document.body.innerText : ''")
                if len(t) > 500:
                    break
            print(f"  warm-up text={len(t)}", flush=True)
        except Exception as e:
            print(f"  warm-up ERROR: {str(e)[:200]}", flush=True)

        for url in URLS:
            lot = re.sub(r"[^0-9]", "", url.rstrip("/").split("/")[-1])
            print(f"\n=== {url}", flush=True)
            try:
                await page.goto(url, wait_until="domcontentloaded", timeout=90000)
                text = ""
                for _ in range(30):
                    await page.wait_for_timeout(2000)
                    text = await page.evaluate("document.body ? document.body.innerText : ''")
                    if len(text) > 800:
                        await page.wait_for_timeout(2500)
                        text = await page.evaluate("document.body.innerText")
                        break
                html = await page.content()
                open(f"{OUT}/lot_{lot}.html", "w").write(html)
                open(f"{OUT}/lot_{lot}.txt", "w").write(text)
                print(f"  html={len(html)} text={len(text)}", flush=True)
                print("  " + text[:300].replace("\n", " | "), flush=True)
            except Exception as e:
                print(f"  ERROR: {str(e)[:200]}", flush=True)

        await browser.close()


asyncio.run(main())
