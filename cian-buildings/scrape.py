"""Download CIAN listing pages.

CIAN blocks datacenter IPs and plain HTTP clients (WAF "bot-detected").
Scrapling's Fetcher (curl_cffi with a real Chrome/Safari TLS fingerprint)
gets through, so it is used here instead of requests/Playwright.

    pip install "scrapling[fetchers]"
    python scrape.py            # ids are read from listings.txt
"""
import os
import random
import re
import sys
import time

from scrapling.fetchers import Fetcher

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "build", "raw")


def offer_ids(path):
    with open(path, encoding="utf-8") as f:
        return list(dict.fromkeys(re.findall(r"/commercial/(\d+)", f.read())))


def fetch(url):
    for _ in range(4):
        try:
            page = Fetcher.get(url, impersonate=random.choice(["chrome", "safari"]),
                               stealthy_headers=True, timeout=40)
            if page.status == 200 and len(page.body) > 100_000:
                return page.body
            print("  bad response", page.status, len(page.body))
        except Exception as e:  # network hiccups, redirects to captcha
            print("  error", str(e)[:80])
        time.sleep(3)
    return None


def main():
    os.makedirs(OUT, exist_ok=True)
    ids = offer_ids(sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "listings.txt"))
    for cid in ids:
        path = os.path.join(OUT, f"{cid}.html")
        if os.path.exists(path):
            continue
        body = fetch(f"https://www.cian.ru/sale/commercial/{cid}/")
        if body:
            with open(path, "wb") as f:
                f.write(body)
            print(cid, "ok")
        else:
            print(cid, "FAILED")
        time.sleep(random.uniform(2, 4))


if __name__ == "__main__":
    main()
