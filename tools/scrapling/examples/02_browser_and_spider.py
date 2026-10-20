import os
from scrapling.fetchers import StealthyFetcher, StealthySession
# Path to Chromium; None = the browser installed by `scrapling install`
EXE = os.environ.get("SCRAPLING_CHROMIUM") or None

# 1) JS-rendered page via stealth browser (quotes /js/ is rendered by JavaScript)
page = StealthyFetcher.fetch('https://quotes.toscrape.com/js/', headless=True,
                             executable_path=EXE, wait_selector='.quote')
print("browser status:", page.status, "| JS quotes:", len(page.css('.quote')))

# 2) Background API capture
with StealthySession(headless=True, executable_path=EXE, record_requests=True) as s:
    s.fetch('https://quotes.toscrape.com/scroll', network_idle=True)
    hits = s.network.search(url_pattern=r"/api/quotes", limit=None)
    print("captured API calls:", [(h.url, h.status) for h in hits][:3])
    if hits: print("API json keys:", list(hits[0].json().keys()))

# 3) Spider with pagination + export
from scrapling.spiders import Spider, Response
class Quotes(Spider):
    name = "quotes"; start_urls = ["https://quotes.toscrape.com/"]; concurrent_requests = 5
    async def parse(self, response: Response):
        for q in response.css('.quote'):
            yield {"text": q.css('.text::text').get(), "author": q.css('.author::text').get()}
        nxt = response.css('.next a')
        if nxt and "page/3" not in response.url:
            yield response.follow(nxt[0].attrib['href'])
r = Quotes().start()
out = os.path.join(os.path.dirname(__file__), "quotes.json")
r.items.to_json(out)
print("spider items:", len(r.items), "| saved:", os.path.getsize(out), "bytes")
