"""Обзорная карта объекта: геокодирование + сборка растровой карты из тайлов.

Карта собирается из тайлов CARTO Voyager (OpenStreetMap), поверх ставится
красная метка. Ключи API не нужны. В шаблоне на этом месте стоял скриншот
Google Maps — визуально результат эквивалентен.
"""

from __future__ import annotations

import logging
import math
from concurrent.futures import ThreadPoolExecutor
from io import BytesIO
from pathlib import Path

import requests
from PIL import Image, ImageDraw, ImageFont

log = logging.getLogger(__name__)

TILE_URL = "https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png"
TILE_FALLBACK = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
NOMINATIM = "https://nominatim.openstreetmap.org/search"
TILE_SIZE = 256
UA = "dutch-property-reports/1.0 (report generator)"
ATTRIBUTION = "© OpenStreetMap contributors  © CARTO"


def geocode(query: str) -> tuple[float, float]:
    """Возвращает (lat, lon) для адреса. Используется, если координаты не заданы."""
    resp = requests.get(
        NOMINATIM,
        params={"q": query, "format": "json", "limit": 1},
        headers={"User-Agent": UA},
        timeout=30,
    )
    resp.raise_for_status()
    data = resp.json()
    if not data:
        raise ValueError(f"не удалось геокодировать адрес: {query}")
    return float(data[0]["lat"]), float(data[0]["lon"])


def _deg2num(lat: float, lon: float, zoom: int) -> tuple[float, float]:
    lat_rad = math.radians(lat)
    n = 2.0**zoom
    x = (lon + 180.0) / 360.0 * n
    y = (1.0 - math.asinh(math.tan(lat_rad)) / math.pi) / 2.0 * n
    return x, y


def _tile(z: int, x: int, y: int, retina: bool) -> Image.Image:
    n = 2**z
    if not (0 <= y < n):
        return Image.new("RGB", (TILE_SIZE, TILE_SIZE), "white")
    x %= n
    sub = "abc"[(x + y) % 3]
    url = TILE_URL.format(s=sub, z=z, x=x, y=y, r="@2x" if retina else "")
    try:
        resp = requests.get(url, headers={"User-Agent": UA}, timeout=30)
        resp.raise_for_status()
    except Exception:
        resp = requests.get(
            TILE_FALLBACK.format(z=z, x=x, y=y), headers={"User-Agent": UA}, timeout=30
        )
        resp.raise_for_status()
    return Image.open(BytesIO(resp.content)).convert("RGB")


def _pin(draw: ImageDraw.ImageDraw, x: int, y: int, scale: int) -> None:
    """Рисует красную каплевидную метку с остриём в точке (x, y)."""
    r = 11 * scale
    h = 30 * scale
    red, dark = (219, 68, 55), (150, 30, 25)
    cx, cy = x, y - h + r
    draw.polygon(
        [(cx - r * 0.72, cy + r * 0.55), (cx + r * 0.72, cy + r * 0.55), (cx, y)],
        fill=red,
    )
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=red, outline=dark, width=scale)
    ir = r * 0.42
    draw.ellipse([cx - ir, cy - ir, cx + ir, cy + ir], fill="white")


def render(
    lat: float,
    lon: float,
    dest: Path,
    zoom: int = 13,
    width: int = 1200,
    height: int = 660,
    retina: bool = True,
) -> Path:
    """Собирает карту с меткой в центре и сохраняет PNG по пути ``dest``."""
    if dest.exists() and dest.stat().st_size > 0:
        return dest
    dest.parent.mkdir(parents=True, exist_ok=True)

    scale = 2 if retina else 1
    px_tile = TILE_SIZE * scale
    out_w, out_h = width * scale, height * scale

    cx, cy = _deg2num(lat, lon, zoom)
    # координаты левого верхнего угла картинки в пикселях мировой сетки
    origin_x = cx * px_tile - out_w / 2
    origin_y = cy * px_tile - out_h / 2
    x0, y0 = math.floor(origin_x / px_tile), math.floor(origin_y / px_tile)
    x1 = math.floor((origin_x + out_w) / px_tile)
    y1 = math.floor((origin_y + out_h) / px_tile)

    coords = [(x, y) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1)]
    log.info("карта %.5f,%.5f zoom=%d — %d тайлов", lat, lon, zoom, len(coords))
    with ThreadPoolExecutor(8) as pool:
        tiles = list(pool.map(lambda c: _tile(zoom, c[0], c[1], retina), coords))

    canvas = Image.new("RGB", (out_w, out_h), "white")
    for (tx, ty), img in zip(coords, tiles):
        if img.size != (px_tile, px_tile):
            img = img.resize((px_tile, px_tile))
        canvas.paste(img, (int(tx * px_tile - origin_x), int(ty * px_tile - origin_y)))

    draw = ImageDraw.Draw(canvas, "RGBA")
    _pin(draw, out_w // 2, out_h // 2, scale)

    try:
        font = ImageFont.truetype(
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 11 * scale
        )
    except OSError:
        font = ImageFont.load_default()
    tw = draw.textlength(ATTRIBUTION, font=font)
    pad = 4 * scale
    draw.rectangle(
        [out_w - tw - 3 * pad, out_h - 16 * scale - pad, out_w, out_h],
        fill=(255, 255, 255, 190),
    )
    draw.text(
        (out_w - tw - pad, out_h - 14 * scale - pad),
        ATTRIBUTION,
        fill=(70, 70, 70),
        font=font,
    )

    canvas.save(dest, "PNG")
    return dest
