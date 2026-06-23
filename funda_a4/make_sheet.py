#!/usr/bin/env python3
"""
funda_a4 — build an A4 fact sheet for a Funda (in Business) listing.

Layout of the produced A4 page:

    +--------------------------------------------------+
    |  <address title>                                 |
    |  +--------------------------------------------+  |
    |  |  OVERVIEW MAP (marker + nearby towns)      |  |  <- top half
    |  +--------------------------------------------+  |
    |  +--------------------------------------------+  |
    |  |  OBJECT PHOTO (from the listing)           |  |  <- bottom half
    |  +--------------------------------------------+  |
    |  price / area / source url                       |
    +--------------------------------------------------+

Why this design (reverse-engineering notes)
-------------------------------------------
Two hard constraints drove the architecture:

1. Funda is behind an anti-bot wall (DataDome-style CAPTCHA: pages return
   "Je bent bijna op de pagina die je zoekt"). A plain HTTP GET therefore
   never yields the listing photos. We solve this with a *fallback chain*
   for the photo (local file -> explicit URL -> broker mirror -> funda) so
   the tool keeps working when funda blocks us.

2. Google Static Maps needs an API key and billing. We make it optional:
   if GOOGLE_MAPS_API_KEY is set we use it (town labels are crisp), otherwise
   we stitch OpenStreetMap tiles ourselves — no key, no paid API, and it
   works in restricted/whitelisted networks where tile.openstreetmap.org is
   reachable.

Everything degrades gracefully: missing photo -> placeholder, missing
coordinates -> geocode via Nominatim, missing network -> clear error.
"""

from __future__ import annotations

import argparse
import io
import math
import os
import re
import sys
import time
from dataclasses import dataclass
from typing import Optional, Tuple

import requests
from PIL import Image, ImageDraw, ImageFont

# --------------------------------------------------------------------------
# Constants
# --------------------------------------------------------------------------

UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"
)
# Polite, identifiable UA for OSM/Nominatim (their usage policy requires it).
OSM_UA = "funda-a4-tool/1.0 (https://github.com/; contact: local)"

TILE = 256  # OSM tile size in px
A4_DPI = 150
A4_W = int(8.27 * A4_DPI)   # 1240 px
A4_H = int(11.69 * A4_DPI)  # 1754 px

NOMINATIM = "https://nominatim.openstreetmap.org/search"
OSM_TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
GOOGLE_STATIC = "https://maps.googleapis.com/maps/api/staticmap"


# --------------------------------------------------------------------------
# HTTP helper with retries
# --------------------------------------------------------------------------

def http_get(url: str, *, ua: str = UA, timeout: int = 25, params: dict | None = None,
             tries: int = 4) -> requests.Response:
    last = None
    for i in range(tries):
        try:
            r = requests.get(url, headers={"User-Agent": ua}, params=params, timeout=timeout)
            if r.status_code == 200:
                return r
            last = RuntimeError(f"HTTP {r.status_code} for {r.url}")
        except requests.RequestException as e:
            last = e
        time.sleep(2 ** i)  # 1,2,4,8s backoff
    raise last or RuntimeError(f"failed: {url}")


# --------------------------------------------------------------------------
# Web Mercator math
# --------------------------------------------------------------------------

def lonlat_to_global_px(lat: float, lon: float, zoom: int) -> Tuple[float, float]:
    """Convert lat/lon to global pixel coordinates at a given zoom."""
    n = 2 ** zoom
    x = (lon + 180.0) / 360.0 * n * TILE
    lat_rad = math.radians(lat)
    y = (1.0 - math.log(math.tan(lat_rad) + 1.0 / math.cos(lat_rad)) / math.pi) / 2.0 * n * TILE
    return x, y


# --------------------------------------------------------------------------
# Geocoding
# --------------------------------------------------------------------------

def geocode(query: str) -> Tuple[float, float]:
    r = http_get(NOMINATIM, ua=OSM_UA,
                 params={"format": "json", "q": query, "limit": 1})
    data = r.json()
    if not data:
        raise RuntimeError(f"geocode: no result for {query!r}")
    return float(data[0]["lat"]), float(data[0]["lon"])


# --------------------------------------------------------------------------
# Static map: OpenStreetMap tile stitcher (keyless)
# --------------------------------------------------------------------------

def osm_static_map(lat: float, lon: float, zoom: int, w: int, h: int) -> Image.Image:
    """Stitch OSM tiles into a w*h image centered on lat/lon with a marker."""
    cx, cy = lonlat_to_global_px(lat, lon, zoom)
    left = cx - w / 2
    top = cy - h / 2

    x0 = int(math.floor(left / TILE))
    y0 = int(math.floor(top / TILE))
    x1 = int(math.floor((left + w) / TILE))
    y1 = int(math.floor((top + h) / TILE))

    canvas = Image.new("RGB", (w, h), (220, 220, 220))
    n = 2 ** zoom
    for tx in range(x0, x1 + 1):
        for ty in range(y0, y1 + 1):
            if not (0 <= ty < n):
                continue
            wrap_x = tx % n  # horizontal wrap-around
            url = OSM_TILE_URL.format(z=zoom, x=wrap_x, y=ty)
            try:
                tile = Image.open(io.BytesIO(http_get(url, ua=OSM_UA).content)).convert("RGB")
            except Exception:
                tile = Image.new("RGB", (TILE, TILE), (235, 235, 235))
            px = int(tx * TILE - left)
            py = int(ty * TILE - top)
            canvas.paste(tile, (px, py))

    draw_marker(canvas, w // 2, h // 2)
    _attribution(canvas, "© OpenStreetMap contributors")
    return canvas


def google_static_map(lat: float, lon: float, zoom: int, w: int, h: int, key: str) -> Image.Image:
    """Google Static Maps (needs API key). Town labels are crisper than OSM."""
    # Google caps free static size at 640x640 (1280 with scale=2).
    scale = 2
    gw, gh = min(w // scale, 640), min(h // scale, 640)
    params = {
        "center": f"{lat},{lon}",
        "zoom": zoom,
        "size": f"{gw}x{gh}",
        "scale": scale,
        "maptype": "roadmap",
        "markers": f"color:red|{lat},{lon}",
        "key": key,
    }
    r = http_get(GOOGLE_STATIC, params=params)
    img = Image.open(io.BytesIO(r.content)).convert("RGB")
    return img.resize((w, h), Image.LANCZOS)


def draw_marker(img: Image.Image, x: int, y: int) -> None:
    """Draw a classic teardrop pin centered at (x, y)."""
    d = ImageDraw.Draw(img)
    r = 14
    red = (220, 30, 30)
    # pin head
    d.ellipse([x - r, y - 2 * r, x + r, y], fill=red, outline=(120, 0, 0), width=2)
    # pin tip (triangle)
    d.polygon([(x - r + 3, y - r // 2), (x + r - 3, y - r // 2), (x, y + r)], fill=red)
    # white center dot
    d.ellipse([x - 5, y - r - 5, x + 5, y - r + 5], fill="white")


def _attribution(img: Image.Image, text: str) -> None:
    d = ImageDraw.Draw(img)
    font = _font(12)
    tw = d.textlength(text, font=font)
    pad = 4
    d.rectangle([img.width - tw - 2 * pad, img.height - 18,
                 img.width, img.height], fill=(255, 255, 255))
    d.text((img.width - tw - pad, img.height - 16), text, fill=(60, 60, 60), font=font)


# --------------------------------------------------------------------------
# Photo acquisition (fallback chain)
# --------------------------------------------------------------------------

def load_photo(photo: Optional[str], photo_url: Optional[str]) -> Optional[Image.Image]:
    """Try local file, then explicit URL. Returns None if nothing worked."""
    if photo and os.path.exists(photo):
        return Image.open(photo).convert("RGB")
    if photo_url:
        try:
            r = http_get(photo_url, timeout=30)
            return Image.open(io.BytesIO(r.content)).convert("RGB")
        except Exception as e:
            print(f"  ! photo_url failed: {e}", file=sys.stderr)
    return None


def scrape_funda_image(url: str) -> Optional[str]:
    """Best-effort scrape of og:image / cloud.funda.nl from a funda page.

    Returns None when funda serves its anti-bot CAPTCHA page (the common case).
    """
    try:
        html = http_get(url).text
    except Exception:
        return None
    if "Je bent bijna op de pagina" in html or "captcha" in html.lower():
        return None  # blocked by anti-bot wall
    m = re.search(r'<meta[^>]+property=["\']og:image["\'][^>]+content=["\']([^"\']+)', html, re.I)
    if m:
        return m.group(1)
    m = re.search(r'https?://[a-z0-9.\-]*cloud\.funda\.nl[^"\'\s)]+', html, re.I)
    return m.group(0) if m else None


# --------------------------------------------------------------------------
# Compositing the A4 sheet
# --------------------------------------------------------------------------

def _font(size: int) -> ImageFont.FreeTypeFont:
    for path in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/Library/Fonts/Arial.ttf",
    ):
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except Exception:
                pass
    return ImageFont.load_default()


def _fit(img: Image.Image, box_w: int, box_h: int) -> Image.Image:
    """Resize+crop (cover) an image to fill box_w x box_h."""
    ratio = max(box_w / img.width, box_h / img.height)
    img = img.resize((max(1, int(img.width * ratio)), max(1, int(img.height * ratio))), Image.LANCZOS)
    left = (img.width - box_w) // 2
    top = (img.height - box_h) // 2
    return img.crop((left, top, left + box_w, top + box_h))


@dataclass
class Sheet:
    address: str
    source_url: str
    subtitle: str = ""
    footer: str = ""


def compose_a4(sheet: Sheet, map_img: Image.Image, photo: Optional[Image.Image]) -> Image.Image:
    page = Image.new("RGB", (A4_W, A4_H), "white")
    d = ImageDraw.Draw(page)
    m = 50  # margin
    cw = A4_W - 2 * m  # content width

    # --- Title ---
    d.text((m, m), sheet.address, fill=(20, 20, 20), font=_font(34))
    y = m + 50
    if sheet.subtitle:
        d.text((m, y), sheet.subtitle, fill=(90, 90, 90), font=_font(20))
        y += 34

    # --- Map (top block) ---
    map_h = 620
    map_box = _fit(map_img, cw, map_h)
    page.paste(map_box, (m, y))
    d.rectangle([m, y, m + cw, y + map_h], outline=(180, 180, 180), width=2)
    d.text((m + 6, y + 6), "Locatie / Location", fill=(255, 255, 255),
           font=_font(18), stroke_width=2, stroke_fill=(0, 0, 0))
    y += map_h + 24

    # --- Photo (bottom block) ---
    photo_h = A4_H - y - m - 60
    if photo is not None:
        ph = _fit(photo, cw, photo_h)
        page.paste(ph, (m, y))
    else:
        d.rectangle([m, y, m + cw, y + photo_h], fill=(245, 245, 245))
        msg = "Foto niet beschikbaar — voer --photo of --photo-url in"
        d.text((m + cw // 2, y + photo_h // 2), msg, fill=(150, 150, 150),
               font=_font(20), anchor="mm")
    d.rectangle([m, y, m + cw, y + photo_h], outline=(180, 180, 180), width=2)
    d.text((m + 6, y + 6), "Object", fill=(255, 255, 255),
           font=_font(18), stroke_width=2, stroke_fill=(0, 0, 0))
    y += photo_h + 14

    # --- Footer ---
    if sheet.footer:
        d.text((m, y), sheet.footer, fill=(60, 60, 60), font=_font(16))
        y += 22
    d.text((m, y), f"Bron: {sheet.source_url}", fill=(110, 110, 130), font=_font(13))
    return page


# --------------------------------------------------------------------------
# CLI
# --------------------------------------------------------------------------

def main() -> int:
    ap = argparse.ArgumentParser(description="Build an A4 sheet (map + object photo) for a Funda listing.")
    ap.add_argument("--url", required=True, help="Funda listing URL (recorded as source).")
    ap.add_argument("--address", help="Address to geocode, e.g. 'Prinsestraat 118, Den Haag'.")
    ap.add_argument("--lat", type=float, help="Latitude (skips geocoding).")
    ap.add_argument("--lon", type=float, help="Longitude (skips geocoding).")
    ap.add_argument("--zoom", type=int, default=11,
                    help="Overview zoom; lower = more towns visible (default 11).")
    ap.add_argument("--photo", help="Local image file of the object.")
    ap.add_argument("--photo-url", help="Direct URL to the object photo (e.g. broker CDN).")
    ap.add_argument("--subtitle", default="", help="Optional subtitle line.")
    ap.add_argument("--footer", default="", help="Optional footer line (price / area).")
    ap.add_argument("-o", "--out", default="funda_sheet.pdf",
                    help="Output file (.pdf or .png).")
    args = ap.parse_args()

    # 1) coordinates
    if args.lat is not None and args.lon is not None:
        lat, lon = args.lat, args.lon
    else:
        q = args.address or args.url
        print(f"[1/3] Geocoding {q!r} ...")
        lat, lon = geocode(q)
    print(f"      -> {lat:.5f}, {lon:.5f}")

    # 2) map
    print(f"[2/3] Building overview map (zoom={args.zoom}) ...")
    key = os.environ.get("GOOGLE_MAPS_API_KEY")
    map_w, map_h = 1140, 760
    if key:
        try:
            map_img = google_static_map(lat, lon, args.zoom, map_w, map_h, key)
            print("      -> Google Static Maps")
        except Exception as e:
            print(f"      ! Google failed ({e}); falling back to OSM")
            map_img = osm_static_map(lat, lon, args.zoom, map_w, map_h)
    else:
        map_img = osm_static_map(lat, lon, args.zoom, map_w, map_h)
        print("      -> OpenStreetMap (keyless)")

    # 3) photo (fallback chain)
    print("[3/3] Acquiring object photo ...")
    photo = load_photo(args.photo, args.photo_url)
    if photo is None:
        scraped = scrape_funda_image(args.url)
        if scraped:
            photo = load_photo(None, scraped)
    if photo is None:
        print("      ! no photo (funda is likely behind anti-bot CAPTCHA);"
              " pass --photo or --photo-url")
    else:
        print(f"      -> photo {photo.size}")

    addr = args.address or "Funda object"
    sheet = Sheet(address=addr, source_url=args.url,
                  subtitle=args.subtitle, footer=args.footer)
    page = compose_a4(sheet, map_img, photo)

    out = args.out
    if out.lower().endswith(".pdf"):
        page.save(out, "PDF", resolution=A4_DPI)
    else:
        page.save(out)
    print(f"\nSaved {out}  ({page.width}x{page.height}px @ {A4_DPI}dpi)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
