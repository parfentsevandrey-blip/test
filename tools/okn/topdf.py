"""Печатает перечень ОКН в PDF через Chromium.

Шрифты грузятся с Google Fonts, поэтому ждём networkidle: иначе полоса
уезжает на засечный фолбэк и вёрстка едет.
"""
import asyncio, os
from playwright.async_api import async_playwright

OUT = os.path.dirname(os.path.abspath(__file__))
SRC = f"file://{OUT}/okn-moscow.html"
DST = f"{OUT}/okn-moscow.pdf"

FOOT = """
<div style="width:100%;font-family:'PT Sans',Arial,sans-serif;font-size:7.5pt;
     color:#8b939f;padding:0 12mm;display:flex;justify-content:space-between;">
  <span>Объекты культурного наследия Москвы · ДОМ.РФ · 22.09.2026</span>
  <span class="pageNumber"></span>
</div>"""
HEAD = '<div style="display:none"></div>'


async def main():
    async with async_playwright() as pw:
        b = await pw.chromium.launch(
            executable_path="/opt/pw-browsers/chromium-1194/chrome-linux/chrome",
            args=["--no-sandbox", "--disable-dev-shm-usage", "--ssl-version-max=tls1.2"],
            proxy={"server": os.environ["HTTPS_PROXY"]})
        p = await b.new_page(viewport={"width": 1000, "height": 1300})
        await p.goto(SRC, wait_until="load", timeout=180000)
        try:
            await p.wait_for_load_state("networkidle", timeout=60000)
        except Exception:
            pass
        await p.evaluate("document.fonts ? document.fonts.ready : null")
        await p.wait_for_timeout(4000)
        await p.emulate_media(media="print")
        await p.wait_for_timeout(1500)
        await p.pdf(path=DST, format="A4", print_background=True,
                    display_header_footer=True,
                    header_template=HEAD, footer_template=FOOT,
                    margin={"top": "13mm", "bottom": "15mm",
                            "left": "12mm", "right": "12mm"},
                    prefer_css_page_size=True)
        await b.close()
    print(f"{DST}  {os.path.getsize(DST)/1048576:.2f} МБ")


asyncio.run(main())
