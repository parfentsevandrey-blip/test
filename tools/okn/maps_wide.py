"""Широкие схематические карты по координатам объектов.

Спутник больше не снимаем — на карточке нужен один крупный кадр схемы.
"""
import asyncio, json, os
from playwright.async_api import async_playwright

OUT = os.path.dirname(os.path.abspath(__file__))
MAPS = os.path.join(OUT, "maps_wide")
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36")
WIDGET = "https://yandex.ru/map-widget/v1/"


async def shoot(p, path, url, tries=3):
    for a in range(tries):
        try:
            await p.goto(url, wait_until="domcontentloaded", timeout=70000)
            await p.wait_for_timeout(10000 if a == 0 else 14000)
            for lbl in ["Allow all", "Принять", "Согласен"]:
                try:
                    el = p.get_by_text(lbl, exact=False).first
                    if await el.count():
                        await el.click(timeout=2500)
                        await p.wait_for_timeout(1500)
                        break
                except Exception:
                    pass
            await p.wait_for_timeout(3000)
            await p.screenshot(path=path)
            if os.path.getsize(path) > 40000:
                return True
        except Exception as e:
            print(f"    попытка {a+1}: {str(e)[:70]}")
    return False


async def main():
    os.makedirs(MAPS, exist_ok=True)
    objs = json.load(open(f"{OUT}/okn_objects.json"))
    async with async_playwright() as pw:
        b = await pw.chromium.launch(
            executable_path="/opt/pw-browsers/chromium-1194/chrome-linux/chrome",
            args=["--no-sandbox", "--disable-dev-shm-usage", "--ssl-version-max=tls1.2",
                  "--disable-blink-features=AutomationControlled"],
            proxy={"server": os.environ["HTTPS_PROXY"]})
        c = await b.new_context(locale="ru-RU", timezone_id="Europe/Moscow",
                                viewport={"width": 1500, "height": 640}, user_agent=UA)
        p = await c.new_page()
        for n, (oid, o) in enumerate(objs.items(), 1):
            lat, lon = o.get("coordinateLatitude"), o.get("coordinateLongitude")
            if lat is None:
                continue
            url = f"{WIDGET}?ll={lon}%2C{lat}&z=16&pt={lon},{lat},pm2rdm"
            ok = await shoot(p, f"{MAPS}/{oid}.png", url)
            print(f"{n:2}/15 {oid} {'ok' if ok else '—'}  {o.get('name','')[:42]}", flush=True)
        await b.close()


asyncio.run(main())
