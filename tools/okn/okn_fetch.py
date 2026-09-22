"""Забор пятнадцати карточек ОКН по Москве с наследие.дом.рф.

Сайт стоит за JS-челленджем, поэтому нужен настоящий браузер с прогревом,
а Chromium через прокси обязан идти с потолком TLS 1.2 — иначе
ERR_CONNECTION_RESET (см. docs/cian/README.md).
"""
import asyncio, json, os
from playwright.async_api import async_playwright
import extract

BASE = "https://xn--80aicbopm7a.xn--d1aqf.xn--p1ai"
OUT = os.path.dirname(os.path.abspath(__file__))
PAGES = os.path.join(OUT, "pages")
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36")

KNOWN = {
    1343: "/объект-главный-дом-усадьбы-19-город-москва-1343/",
    1344: "/объект-флигель-усадьбы-19-город-москва-1344/",
    3529: "/объект-каретный-двор-город-москва-3529/",
    3532: "/объект-дом-управляющего-город-москва-3532/",
    4100: "/объект-западный-служебный-корпус-город-москва-4100/",
    4098: "/объект-южный-служебный-корпус-город-москва-4098/",
    4101: "/объект-хозяйственный-корпус-с-конюшней-город-москва-4101/",
    4248: "/объект-руины-хозяйственной-постройки-город-москва-4248/",
    4103: "/объект-усадьба-щапово-город-москва-4103/",
    4097: "/объект-усадьба-в-с-нарышкина-п-н-ивашева-комплекс-александровской-"
          "полицейской-больницы-город-москва-4097/",
    3764: "/объект-усадьба-город-москва-3764/",
    1498: "/объект-здание-бывшей-александровской-больницы-город-москва-1498/",
    4336: "/объект-ограда-со-сторожкой-и-пилонами-ворот-город-москва-4336/",
    3530: "/объект-кузница-город-москва-3530/",
    3531: "/объект-склад-столовых-продуктов-город-москва-3531/",
}


async def main():
    os.makedirs(PAGES, exist_ok=True)
    listing = json.load(open(f"{OUT}/api_objects.json"))["payload"]
    ids = [o["externalId"] for o in listing]
    print(f"объектов в выдаче: {len(ids)}")

    async with async_playwright() as pw:
        b = await pw.chromium.launch(
            executable_path="/opt/pw-browsers/chromium-1194/chrome-linux/chrome",
            args=["--no-sandbox", "--disable-dev-shm-usage", "--ssl-version-max=tls1.2",
                  "--disable-blink-features=AutomationControlled"],
            proxy={"server": os.environ["HTTPS_PROXY"]})
        c = await b.new_context(locale="ru-RU", timezone_id="Europe/Moscow",
                                viewport={"width": 1440, "height": 1000}, user_agent=UA)
        p = await c.new_page()
        await p.goto(BASE + "/", wait_until="domcontentloaded", timeout=90000)
        for _ in range(30):
            await p.wait_for_timeout(2000)
            if len(await p.evaluate("document.body?document.body.innerText:''")) > 500:
                break
        print("прогрев ок")

        data = {}
        for n, oid in enumerate(ids, 1):
            path = KNOWN[oid]
            url = BASE + path
            try:
                r = await p.goto(url, wait_until="domcontentloaded", timeout=90000)
                st = r.status if r else 0
                for _ in range(25):
                    await p.wait_for_timeout(1500)
                    t = await p.evaluate("document.body?document.body.innerText:''")
                    if len(t) > 1500:
                        break
                # раскрыть историю и вкладки характеристик
                for lbl in ["Показать полностью", "Общие характеристики", "Документы"]:
                    try:
                        el = p.get_by_text(lbl, exact=False).first
                        if await el.count():
                            await el.click(timeout=3500)
                            await p.wait_for_timeout(1200)
                    except Exception:
                        pass
                html = await p.content()
                text = await p.evaluate("document.body.innerText")
                open(f"{PAGES}/{oid}.html", "w").write(html)
                open(f"{PAGES}/{oid}.txt", "w").write(text)
                d = extract.parse_page(html)
                if d:
                    data[oid] = d
                print(f"{n:2}/15  {oid}  http={st}  json={'ok' if d else 'FAIL'}  "
                      f"{(d or {}).get('name','')[:46]}", flush=True)
            except Exception as e:
                print(f"{n:2}/15  {oid}  ОШИБКА: {str(e)[:110]}", flush=True)

        json.dump(data, open(f"{OUT}/okn_objects.json", "w"),
                  ensure_ascii=False, indent=1)
        print(f"\nсобрано карточек: {len(data)}")
        await b.close()


asyncio.run(main())
