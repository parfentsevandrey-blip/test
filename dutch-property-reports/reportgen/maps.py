"""Обзорная карта объекта на базе Google Maps.

Кадр строится так, чтобы в него всегда попадали и объект, и ближайший
крупный город (масштаб подбирается автоматически по двум точкам).

Источники изображения, по порядку предпочтения:

1. ``google_static`` — официальный Google Maps Static API. Включается, когда
   задан ключ в переменной окружения ``GOOGLE_MAPS_API_KEY``; это штатный
   способ для регулярной работы.
2. ``google_tiles`` — сборка карты из растровых тайлов Google (без ключа).
   Используется по умолчанию, пока ключ не заведён.
"""

from __future__ import annotations

import logging
import math
import os
from concurrent.futures import ThreadPoolExecutor
from io import BytesIO
from pathlib import Path

import requests
from PIL import Image, ImageDraw, ImageFont

log = logging.getLogger(__name__)

GOOGLE_TILE_URL = "https://mt{s}.google.com/vt/lyrs={layer}&x={x}&y={y}&z={z}"
# lyrs=m — схема, lyrs=y — спутник с подписями улиц. Аэрофотоснимок нужен
# затем, чтобы показать сам участок: на схеме застройка и двор неразличимы.
LAYERS = {"roadmap": "m", "aerial": "y"}
GOOGLE_STATIC_URL = "https://maps.googleapis.com/maps/api/staticmap"
NOMINATIM = "https://nominatim.openstreetmap.org/search"
TILE_SIZE = 256
UA = (
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
)
ATTRIBUTION = "Картографические данные © Google"
ZOOM_RANGE = (8, 16)
API_KEY_ENV = "GOOGLE_MAPS_API_KEY"


# --------------------------------------------------------------------------
# геометрия
# --------------------------------------------------------------------------
def _project(lat: float, lon: float, zoom: int) -> tuple[float, float]:
    """Координаты в пикселях мировой сетки Меркатора для заданного масштаба."""
    n = TILE_SIZE * 2**zoom
    x = (lon + 180.0) / 360.0 * n
    sin_lat = math.sin(math.radians(lat))
    y = (0.5 - math.log((1 + sin_lat) / (1 - sin_lat)) / (4 * math.pi)) * n
    return x, y


def fit_zoom(points: list[tuple[float, float]], width: int, height: int, padding=0.78) -> int:
    """Максимальный масштаб, при котором все точки помещаются в кадр."""
    if len(points) < 2:
        return 13
    for zoom in range(ZOOM_RANGE[1], ZOOM_RANGE[0] - 1, -1):
        xs, ys = zip(*(_project(lat, lon, zoom) for lat, lon in points))
        if (max(xs) - min(xs)) <= width * padding and (max(ys) - min(ys)) <= height * padding:
            return zoom
    return ZOOM_RANGE[0]


def _center(points: list[tuple[float, float]], zoom: int) -> tuple[float, float]:
    """Центр кадра (lat, lon) как середина ограничивающего прямоугольника."""
    xs, ys = zip(*(_project(lat, lon, zoom) for lat, lon in points))
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    n = TILE_SIZE * 2**zoom
    lon = cx / n * 360.0 - 180.0
    lat = math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * cy / n))))
    return lat, lon


def geocode(query: str) -> tuple[float, float]:
    """Геокодирование адреса, если координаты не заданы в карточке объекта."""
    resp = requests.get(
        NOMINATIM,
        params={"q": query, "format": "json", "limit": 1},
        headers={"User-Agent": "dutch-property-reports/1.0"},
        timeout=30,
    )
    resp.raise_for_status()
    data = resp.json()
    if not data:
        raise ValueError(f"не удалось геокодировать адрес: {query}")
    return float(data[0]["lat"]), float(data[0]["lon"])


# --------------------------------------------------------------------------
# отрисовка
# --------------------------------------------------------------------------
def _pin(draw: ImageDraw.ImageDraw, x: float, y: float, scale: int) -> None:
    """Красная метка Google-образца с остриём в точке (x, y)."""
    r = 11 * scale
    h = 30 * scale
    red, dark = (219, 68, 55), (150, 30, 25)
    cx, cy = x, y - h + r
    draw.polygon(
        [(cx - r * 0.72, cy + r * 0.55), (cx + r * 0.72, cy + r * 0.55), (cx, y)], fill=red
    )
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=red, outline=dark, width=scale)
    ir = r * 0.42
    draw.ellipse([cx - ir, cy - ir, cx + ir, cy + ir], fill="white")


def _attribution(canvas: Image.Image, scale: int) -> None:
    draw = ImageDraw.Draw(canvas, "RGBA")
    try:
        font = ImageFont.truetype(
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 11 * scale
        )
    except OSError:
        font = ImageFont.load_default()
    width, height = canvas.size
    text_width = draw.textlength(ATTRIBUTION, font=font)
    pad = 4 * scale
    draw.rectangle(
        [width - text_width - 3 * pad, height - 16 * scale - pad, width, height],
        fill=(255, 255, 255, 200),
    )
    draw.text(
        (width - text_width - pad, height - 14 * scale - pad),
        ATTRIBUTION,
        fill=(70, 70, 70),
        font=font,
    )


# --------------------------------------------------------------------------
# провайдеры
# --------------------------------------------------------------------------
def _tile(z: int, x: int, y: int, index: int, layer: str = "m") -> Image.Image:
    n = 2**z
    if not (0 <= y < n):
        return Image.new("RGB", (TILE_SIZE, TILE_SIZE), "white")
    url = GOOGLE_TILE_URL.format(s=index % 4, layer=layer, x=x % n, y=y, z=z)
    resp = requests.get(url, headers={"User-Agent": UA}, timeout=30)
    resp.raise_for_status()
    return Image.open(BytesIO(resp.content)).convert("RGB")


def _render_tiles(
    center: tuple[float, float],
    marker: tuple[float, float],
    zoom: int,
    width: int,
    height: int,
    scale: int,
    layer: str = "m",
) -> Image.Image:
    """Сборка карты из тайлов Google (без API-ключа)."""
    out_w, out_h = width * scale, height * scale
    # тайлы отдаются в размере 256 px, поэтому кадр собирается в 1x и масштабируется
    cx, cy = _project(*center, zoom)
    origin_x, origin_y = cx - width / 2, cy - height / 2
    x0, y0 = math.floor(origin_x / TILE_SIZE), math.floor(origin_y / TILE_SIZE)
    x1 = math.floor((origin_x + width) / TILE_SIZE)
    y1 = math.floor((origin_y + height) / TILE_SIZE)

    coords = [(x, y) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1)]
    log.info("карта Google: zoom=%d, тайлов %d", zoom, len(coords))
    with ThreadPoolExecutor(8) as pool:
        tiles = list(pool.map(
            lambda item: _tile(zoom, item[1][0], item[1][1], item[0], layer),
            enumerate(coords)))

    canvas = Image.new("RGB", (width, height), "white")
    for (tx, ty), img in zip(coords, tiles):
        canvas.paste(img, (int(tx * TILE_SIZE - origin_x), int(ty * TILE_SIZE - origin_y)))

    canvas = canvas.resize((out_w, out_h), Image.LANCZOS)
    mx, my = _project(*marker, zoom)
    _pin(
        ImageDraw.Draw(canvas, "RGBA"),
        (mx - origin_x) * scale,
        (my - origin_y) * scale,
        scale,
    )
    _attribution(canvas, scale)
    return canvas


def _render_static_api(
    center: tuple[float, float],
    marker: tuple[float, float],
    zoom: int,
    width: int,
    height: int,
    api_key: str,
    maptype: str = "roadmap",
) -> Image.Image:
    """Официальный Google Maps Static API (нужен ключ)."""
    params = {
        "center": f"{center[0]:.6f},{center[1]:.6f}",
        "zoom": zoom,
        "size": f"{min(width, 640)}x{min(height, 640)}",
        "scale": 2,
        "maptype": maptype,
        "language": "en",
        "markers": f"color:red|{marker[0]:.6f},{marker[1]:.6f}",
        "key": api_key,
    }
    resp = requests.get(GOOGLE_STATIC_URL, params=params, timeout=40)
    resp.raise_for_status()
    return Image.open(BytesIO(resp.content)).convert("RGB")


# --------------------------------------------------------------------------
# точка входа
# --------------------------------------------------------------------------
def render(
    lat: float,
    lon: float,
    dest: Path,
    *,
    city: tuple[float, float] | None = None,
    zoom: int | None = None,
    width: int = 1200,
    height: int = 660,
    scale: int = 2,
    style: str = "roadmap",
) -> Path:
    """Карта с меткой объекта; при заданном ``city`` город гарантированно в кадре.

    ``style="aerial"`` даёт спутниковый снимок с подписями улиц — им показывают
    сам участок: пятно застройки, двор, подъезды и соседние корпуса, которых
    на схеме не видно.
    """
    if dest.exists() and dest.stat().st_size > 0:
        return dest
    dest.parent.mkdir(parents=True, exist_ok=True)

    points = [(lat, lon)] + ([city] if city else [])
    zoom = zoom or fit_zoom(points, width, height)
    center = _center(points, zoom) if city else (lat, lon)

    api_key = os.environ.get(API_KEY_ENV)
    if api_key:
        canvas = _render_static_api(center, (lat, lon), zoom, width, height, api_key,
                                    maptype="satellite" if style == "aerial" else "roadmap")
    else:
        canvas = _render_tiles(center, (lat, lon), zoom, width, height, scale,
                               LAYERS.get(style, "m"))

    canvas.save(dest, "PNG")
    return dest
