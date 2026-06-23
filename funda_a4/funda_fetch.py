#!/usr/bin/env python3
"""
funda_fetch.py — pull a Funda (in Business) listing despite its anti-bot wall.

Funda serves a DataDome CAPTCHA ("Je bent bijna op de pagina die je zoekt") to
plain HTTP clients, headless browsers and even server-side fetchers like Google
Translate. The working bypass discovered here is the **r.jina.ai reader proxy**:
it renders the page through a real browser farm that clears the challenge and
returns clean Markdown — including the spec table and the cloud.funda.nl photo
URLs. The full-size photos themselves download directly from cloud.funda.nl when
a funda Referer header is sent.

This script:
  1. fetches  https://r.jina.ai/<funda_url>
  2. parses address, price, and the "Features" spec fields
  3. collects the object's gallery photos (the *_1440x960 images) and downloads them
  4. emits a partial content JSON (facts + local photo paths)

The descriptive prose ("О городе и районе", "Описание объекта", ...) is written
separately; this tool only automates fact + photo extraction.

Usage:
    python3 funda_fetch.py <funda_url> [<funda_url> ...] --out-dir assets --json facts.json
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time

import requests

JINA = "https://r.jina.ai/"
REF = "https://www.fundainbusiness.nl/"
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36"

# spec fields we try to read out of the "## Features" block
FIELD_PATTERNS = {
    "price":      r"Asking price\s*€\s*([\d.,]+)",
    "year":       r"Year of construction\s*([0-9]{4})",
    "energy":     r"Energy label\s*([A-G][+]*)",
    "main_use":   r"Main use\s*([A-Za-z ,/()-]+?)(?:Alternative|Building type|$)",
    "build_type": r"Building type\s*([A-Za-z ]+?)(?:Year of|$)",
    "area":       r"\bArea\s*([\d.,]+)\s*m",
    "sales_floor": r"Sales floor area\s*([\d.,]+)\s*m",
    "front":      r"Front width\s*([\d.,]+)\s*m",
    "plot":       r"Plot size\s*([\d.,]+)\s*m",
    "acceptance": r"Acceptance\s*([A-Za-z ]+?)(?:###|Main use|$)",
    "rent_income": r"Gross rental income\s*€\s*([\d.,]+)",
}


def jina_fetch(url: str, as_html: bool = False, tries: int = 3) -> str:
    """Fetch a Funda URL through the r.jina.ai reader (clears DataDome).

    as_html=False -> clean Markdown (best for spec parsing).
    as_html=True  -> rendered HTML (contains the FULL photo gallery, not just
                     the few images the Markdown view captures).
    """
    headers = {"User-Agent": UA}
    if as_html:
        headers["X-Return-Format"] = "html"
    last = None
    for i in range(tries):
        try:
            r = requests.get(JINA + url, headers=headers, timeout=120)
            if r.status_code == 200 and "Je bent bijna" not in r.text:
                return r.text
            last = RuntimeError(f"HTTP {r.status_code} / blocked")
        except requests.RequestException as e:
            last = e
        time.sleep(2 ** i)
    raise last or RuntimeError("jina fetch failed")


def fetch_markdown(url: str) -> str:  # back-compat alias
    return jina_fetch(url, as_html=False)


def gallery_from_html(html: str) -> list:
    """Extract the full object gallery from rendered HTML.

    Funda media live at cloud.funda.nl/valentina_media/<a>/<b>/<id>_<size>.jpg.
    The listing's own photos all share one <a>/<b> folder; "similar listings"
    use other folders. So we pick the folder with the most distinct IDs and
    return its photos (ordered) at full 1440x960 size.
    """
    refs = re.findall(r"valentina_media/(\d+)/(\d+)/(\d+)", html)
    if not refs:
        return []
    from collections import Counter, OrderedDict
    folders = Counter((a, b) for a, b, _ in refs)
    main = folders.most_common(1)[0][0]
    ids = OrderedDict()
    for a, b, i in refs:
        if (a, b) == main:
            ids[i] = True
    a, b = main
    return [f"https://cloud.funda.nl/valentina_media/{a}/{b}/{i}_1440x960.jpg" for i in ids]


def parse(md: str, url: str) -> dict:
    out = {"source_url": url}

    # address: first markdown H1 like "# Amsterdamsestraatweg 109 3513 AC Utrecht"
    m = re.search(r"^#\s+(.+?)\s+([0-9]{4}\s?[A-Z]{2})\s+([A-Za-z'\- ]+)\s*$", md, re.M)
    if m:
        out["street"] = m.group(1).strip()
        out["postal"] = m.group(2).strip()
        out["city"] = m.group(3).strip()
        out["address"] = f"{out['street']}, {out['city']}"

    # neighborhood: line after "## Surroundings"
    m = re.search(r"##\s*Surroundings\s*\n+\s*([^\n*]+?),\s*([A-Za-z'\- ]+)\n", md)
    if m:
        out["neighborhood"] = m.group(1).strip()

    # socio-economic class
    m = re.search(r"Socio-economic classification\s*([A-G][0-9])", md)
    if m:
        out["socio"] = m.group(1)

    # price headline
    m = re.search(r"\*\*€\s*([\d.,]+)\s*k\.k\.\*\*", md)
    if m:
        out["price_label"] = "€ " + m.group(1).replace(",", ".")

    # feature fields
    feats = {}
    for key, pat in FIELD_PATTERNS.items():
        mm = re.search(pat, md)
        if mm:
            feats[key] = mm.group(1).strip()
    out["features"] = feats

    # description (Dutch) up to the "Read the full description" marker
    m = re.search(r"##\s*Description\s*\n+(.+?)(?:Read the full description|##)", md, re.S)
    if m:
        out["description_nl"] = re.sub(r"\s+", " ", m.group(1)).strip()

    return out


def download_photos(urls, out_dir: str, prefix: str, max_photos: int = 0) -> list:
    os.makedirs(out_dir, exist_ok=True)
    if max_photos and len(urls) > max_photos:
        # keep the first (facade) + an evenly spaced sample across the gallery
        idx = sorted({0} | {round(i * (len(urls) - 1) / (max_photos - 1)) for i in range(max_photos)})
        urls = [urls[i] for i in idx][:max_photos]
    paths = []
    for i, u in enumerate(urls, 1):
        dest = os.path.join(out_dir, f"{prefix}_p{i:02d}.jpg")
        try:
            r = requests.get(u, headers={"User-Agent": UA, "Referer": REF}, timeout=30)
            if r.status_code == 200 and len(r.content) > 1000:
                with open(dest, "wb") as fh:
                    fh.write(r.content)
                paths.append(dest)
        except requests.RequestException:
            pass
    return paths


def main() -> int:
    ap = argparse.ArgumentParser(description="Fetch Funda listing facts + photos via the jina reader bypass.")
    ap.add_argument("urls", nargs="+")
    ap.add_argument("--out-dir", default="assets")
    ap.add_argument("--json", default="facts.json")
    ap.add_argument("--max-photos", type=int, default=0,
                    help="Cap downloaded photos per object (0 = all); samples evenly across the gallery.")
    args = ap.parse_args()

    results = []
    for n, url in enumerate(args.urls, 1):
        print(f"[{n}/{len(args.urls)}] {url}")
        data = parse(jina_fetch(url, as_html=False), url)   # specs from Markdown
        gallery = gallery_from_html(jina_fetch(url, as_html=True))  # full photo set
        prefix = f"obj{n}"
        data["photos"] = download_photos(gallery, args.out_dir, prefix, args.max_photos)
        print(f"    {data.get('address','?')} | {data.get('price_label','?')} | "
              f"{len(data['photos'])} photos | year {data['features'].get('year','?')} "
              f"energy {data['features'].get('energy','?')}")
        results.append(data)

    with open(args.json, "w", encoding="utf-8") as fh:
        json.dump(results, fh, ensure_ascii=False, indent=2)
    print(f"\nWrote {args.json} ({len(results)} objects); photos in {args.out_dir}/")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
