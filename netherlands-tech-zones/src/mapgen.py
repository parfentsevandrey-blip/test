"""Render Google-Maps-style location images by stitching public map tiles.

Satellite (ESRI World Imagery) + place-label overlay + marker pin, and a clean
street-map variant (CartoDB Voyager). Everything from free, key-less tile APIs.
"""
import math
import io
import os
import time
import requests
from PIL import Image, ImageDraw, ImageFont

TILE = 256
HDR = {"User-Agent": "nl-tech-zones-research/1.0 (industrial-zone analysis; contact analyst)"}

SAT = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
# Transparent hybrid reference layer (roads + place names) to overlay on satellite
REF = "https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Transportation/MapServer/tile/{z}/{y}/{x}"
REF2 = "https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}"
VOYAGER = "https://a.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png"


def _deg2num(lat, lon, z):
    lat_r = math.radians(lat)
    n = 2.0 ** z
    x = (lon + 180.0) / 360.0 * n
    y = (1.0 - math.asinh(math.tan(lat_r)) / math.pi) / 2.0 * n
    return x, y


def _url_variants(url):
    """Yield the url plus subdomain-rotated variants for load-balanced CDNs."""
    variants = [url]
    if "basemaps.cartocdn.com" in url:
        for s in ("b", "c", "d"):
            variants.append(url.replace("//a.basemaps", f"//{s}.basemaps"))
    if "server.arcgisonline.com" in url:
        for s in ("server", "services"):
            variants.append(url.replace("//server.arcgisonline", f"//{s}.arcgisonline"))
    return variants


def _fetch(url, tries=6):
    last = None
    variants = _url_variants(url)
    for i in range(tries):
        u = variants[i % len(variants)]
        try:
            r = requests.get(u, headers=HDR, timeout=30)
            if r.status_code == 200 and len(r.content) > 200:
                return Image.open(io.BytesIO(r.content)).convert("RGBA")
            last = f"HTTP {r.status_code}"
        except Exception as e:  # noqa
            last = str(e)
        time.sleep(0.8 * (i + 1))
    raise RuntimeError(f"tile fetch failed {url}: {last}")


def _stitch(tmpl, z, cx, cy, w, h, fallback=(226, 232, 240, 255)):
    """Stitch tiles into a w x h image centered on fractional tile (cx, cy)."""
    px_c = cx * TILE
    py_c = cy * TILE
    left = px_c - w / 2
    top = py_c - h / 2
    x0 = int(math.floor(left / TILE))
    y0 = int(math.floor(top / TILE))
    x1 = int(math.floor((left + w) / TILE))
    y1 = int(math.floor((top + h) / TILE))
    canvas = Image.new("RGBA", ((x1 - x0 + 1) * TILE, (y1 - y0 + 1) * TILE))
    n = 2 ** z
    for tx in range(x0, x1 + 1):
        for ty in range(y0, y1 + 1):
            txw = tx % n
            if ty < 0 or ty >= n:
                continue
            url = tmpl.format(z=z, x=txw, y=ty)
            try:
                tile = _fetch(url)
            except Exception:
                tile = Image.new("RGBA", (TILE, TILE), fallback)
            canvas.paste(tile, ((tx - x0) * TILE, (ty - y0) * TILE))
    ox = int(left - x0 * TILE)
    oy = int(top - y0 * TILE)
    return canvas.crop((ox, oy, ox + w, oy + h))


def _font(size, bold=False):
    paths = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]
    for p in paths:
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def _draw_marker(img, w, h, color=(234, 67, 53)):
    d = ImageDraw.Draw(img)
    cx, cy = w // 2, h // 2
    # teardrop pin
    r = 15
    top = cy - 46
    d.ellipse([cx - r, top - r, cx + r, top + r], fill=color, outline=(255, 255, 255, 255), width=3)
    d.polygon([(cx - 9, top + 8), (cx + 9, top + 8), (cx, cy - 2)], fill=color)
    d.ellipse([cx - 6, top - 6, cx + 6, top + 6], fill=(255, 255, 255, 255))
    # ground shadow
    d.ellipse([cx - 7, cy - 4, cx + 7, cy + 2], fill=(0, 0, 0, 90))


def _attribution(img, w, h, text):
    d = ImageDraw.Draw(img)
    f = _font(13)
    tw = d.textlength(text, font=f)
    d.rectangle([w - tw - 12, h - 22, w, h], fill=(0, 0, 0, 120))
    d.text((w - tw - 6, h - 19), text, font=f, fill=(255, 255, 255, 230))


def _caption_bar(img, w, title, subtitle):
    d = ImageDraw.Draw(img)
    bar = Image.new("RGBA", (w, 56), (0, 0, 0, 0))
    bd = ImageDraw.Draw(bar)
    bd.rectangle([0, 0, w, 56], fill=(17, 24, 39, 205))
    img.alpha_composite(bar, (0, 0))
    d.text((16, 8), title, font=_font(20, bold=True), fill=(255, 255, 255, 255))
    if subtitle:
        d.text((16, 33), subtitle, font=_font(13), fill=(203, 213, 225, 255))


def satellite(lat, lon, out, z=15, w=1120, h=680, title=None, subtitle=None, labels=True):
    cx, cy = _deg2num(lat, lon, z)
    base = _stitch(SAT, z, cx, cy, w, h, fallback=(58, 66, 58, 255))
    if labels:
        try:
            base.alpha_composite(_stitch(REF2, z, cx, cy, w, h, fallback=(0, 0, 0, 0)))
        except Exception:
            pass
        try:
            base.alpha_composite(_stitch(REF, z, cx, cy, w, h, fallback=(0, 0, 0, 0)))
        except Exception:
            pass
    _draw_marker(base, w, h)
    _attribution(base, w, h, "Imagery © Esri, Maxar, Earthstar Geographics")
    if title:
        _caption_bar(base, w, title, subtitle)
    base.convert("RGB").save(out, "JPEG", quality=88)
    return out


def streetmap(lat, lon, out, z=12, w=1120, h=520, title=None, subtitle=None):
    cx, cy = _deg2num(lat, lon, z)
    base = _stitch(VOYAGER, z, cx, cy, w, h)
    _draw_marker(base, w, h)
    _attribution(base, w, h, "© OpenStreetMap contributors © CARTO")
    if title:
        _caption_bar(base, w, title, subtitle)
    base.convert("RGB").save(out, "JPEG", quality=88)
    return out


def _num2px_in_view(lat, lon, z, cx, cy, w, h):
    x, y = _deg2num(lat, lon, z)
    px = (x - cx) * TILE + w / 2
    py = (y - cy) * TILE + h / 2
    return px, py


PIN_PALETTE = {
    "chips": (234, 88, 12),      # orange
    "datacenter": (37, 99, 235),  # blue
    "ai": (147, 51, 234),         # purple
    "research": (5, 150, 105),    # green
    "mixed": (219, 39, 119),      # pink
}


def national_map(markers, out, center=(52.15, 5.35), z=8, w=1120, h=1360,
                 title=None, subtitle=None, base="voyager"):
    """markers: list of dict(lat, lon, label, kind, num). Clean overview map."""
    cx, cy = _deg2num(*center, z)
    tmpl = VOYAGER if base == "voyager" else SAT
    img = _stitch(tmpl, z, cx, cy, w, h, fallback=(202, 224, 236, 255))
    if base != "voyager":
        try:
            img.alpha_composite(_stitch(REF, z, cx, cy, w, h, fallback=(0, 0, 0, 0)))
        except Exception:
            pass
    d = ImageDraw.Draw(img)
    fsm = _font(14, bold=True)
    fnum = _font(15, bold=True)
    # draw pins
    for m in markers:
        px, py = _num2px_in_view(m["lat"], m["lon"], z, cx, cy, w, h)
        col = PIN_PALETTE.get(m.get("kind", "mixed"), PIN_PALETTE["mixed"])
        r = 16
        top = py - 30
        d.ellipse([px - r, top - r, px + r, top + r], fill=col, outline=(255, 255, 255), width=3)
        d.polygon([(px - 10, top + 9), (px + 10, top + 9), (px, py + 2)], fill=col)
        num = str(m.get("num", ""))
        tw = d.textlength(num, font=fnum)
        d.text((px - tw / 2, top - 9), num, font=fnum, fill=(255, 255, 255))
    # draw labels with collision-avoided offset boxes
    placed = []
    for m in markers:
        px, py = _num2px_in_view(m["lat"], m["lon"], z, cx, cy, w, h)
        label = f'{m.get("num","")}. {m["label"]}'
        tw = d.textlength(label, font=fsm)
        bx, by = px + 20, py - 44
        if m.get("side") == "left":
            bx = px - 24 - tw - 12
        for _ in range(6):
            clash = any(abs(by - p) < 26 and abs(bx - q) < tw for q, p in placed)
            if not clash:
                break
            by += 28
        placed.append((bx, by))
        d.rounded_rectangle([bx - 6, by - 4, bx + tw + 8, by + 20], radius=6,
                            fill=(255, 255, 255, 235), outline=(120, 120, 120))
        col = PIN_PALETTE.get(m.get("kind", "mixed"), PIN_PALETTE["mixed"])
        d.text((bx, by), label, font=fsm, fill=col)
    _attribution(img, w, h, "© OpenStreetMap contributors © CARTO")
    if title:
        _caption_bar(img, w, title, subtitle)
    img.convert("RGB").save(out, "JPEG", quality=90)
    return out


if __name__ == "__main__":
    os.makedirs("imgtest", exist_ok=True)
    satellite(51.41056, 5.42622, "imgtest/asml_sat.jpg", z=15,
              title="ASML — Велдховен (De Run)", subtitle="Brainport Eindhoven · Noord-Brabant")
    streetmap(51.41056, 5.42622, "imgtest/asml_map.jpg", z=11,
              title="Расположение: Велдховен", subtitle="Юг Нидерландов")
    print("done", os.path.getsize("imgtest/asml_sat.jpg"), os.path.getsize("imgtest/asml_map.jpg"))
