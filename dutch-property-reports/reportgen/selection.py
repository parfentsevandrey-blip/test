"""Привязка объекта к выбранным территориям — основа полосы «Почему выбран».

    python -m reportgen.selection "Hurksestraat 13, Eindhoven"
    python -m reportgen.selection --latlon 51.4277 5.4380

Заказчик выбрал часть листов из файла с картами промзон
(`data/selection-<дата>.json`) и отдельно Роттердам целиком. Для каждого
объекта модуль отвечает на три вопроса: на какой выбранной территории он
стоит, в какой промзоне листа (или как далеко от ближайшей), и что рядом из
того, ради чего территорию выбирали, — центры обработки данных, заводы,
поставщики, офисы компаний — с расстоянием и временем в пути.

Текст полосы пишется по этим фактам вручную; карта полосы строится здесь же,
теми же контурами и знаками, что на листах файла.
"""

from __future__ import annotations

import json
import math
import re
import sys
import time
from pathlib import Path

import requests
from PIL import ImageDraw
from pyproj import Transformer
from shapely.geometry import Point, shape
from shapely.ops import transform

from reportgen import maps, zonemap
from reportgen.poster import MM, PAGE_MM, INK, MapFrame, _px, _world

ROOT = Path(__file__).resolve().parent.parent
SELECTION = ROOT / "data" / "selection-2026-10-05.json"
PDOK = "https://api.pdok.nl/bzk/locatieserver/search/v3_1/free"
OSRM = "https://router.project-osrm.org/route/v1/driving/{a};{b}?overview=false"
TO_RD = Transformer.from_crs("EPSG:4326", "EPSG:28992", always_xy=True).transform


# --------------------------------------------------------------------------
# данные
# --------------------------------------------------------------------------
def load(path: Path = SELECTION) -> tuple[dict, dict, dict]:
    """Выбор заказчика, спецификация файла с картами и контуры листов."""
    selection = json.loads(path.read_text(encoding="utf-8"))
    spec = json.loads((ROOT / selection["maps_spec"]).read_text(encoding="utf-8"))
    sheets = {s["key"]: s for s in spec["sheets"]}
    geo = {}
    for territory in selection["territories"]:
        file = territory.get("whole") or f"data/zones/{territory['key']}.geojson"
        feats = json.loads((ROOT / file).read_text(encoding="utf-8"))["features"]
        geo[territory["key"]] = {f["properties"]["key"]: shape(f["geometry"]) for f in feats}
    return selection, spec, {"sheets": sheets, "geo": geo}


def geocode(address: str) -> tuple[float, float, str]:
    resp = requests.get(PDOK, params={"q": address, "rows": 1,
                                      "fl": "weergavenaam,centroide_ll,type"}, timeout=30)
    doc = resp.json()["response"]["docs"][0]
    lon, lat = map(float, re.match(r"POINT\(([\d.]+) ([\d.]+)\)", doc["centroide_ll"]).groups())
    return lat, lon, f"{doc['weergavenaam']} ({doc['type']})"


def drive(a: tuple[float, float], b: tuple[float, float]) -> tuple[float, float] | None:
    """Путь на машине: километры и минуты по дорогам (OSRM)."""
    url = OSRM.format(a=f"{a[1]},{a[0]}", b=f"{b[1]},{b[0]}")
    for attempt in range(4):
        try:
            route = requests.get(url, timeout=30).json()["routes"][0]
            return route["distance"] / 1000, route["duration"] / 60
        except Exception:
            time.sleep(2 * (attempt + 1))
    return None


def _metres(geom_a, geom_b) -> float:
    return transform(TO_RD, geom_a).distance(transform(TO_RD, geom_b))


# --------------------------------------------------------------------------
# кадры листов: объект на территории, если попадает в карту листа или врезку
# --------------------------------------------------------------------------
def _frame_bounds(map_spec: dict, size_px: tuple[int, int]) -> tuple[float, float, float, float]:
    frame = MapFrame(map_spec, (0, 0, *size_px))
    n = frame.tile * 2**frame.zoom

    def unproject(x, y):
        wx = frame.origin[0] + x / frame.k
        wy = frame.origin[1] + y / frame.k
        lon = wx / n * 360.0 - 180.0
        lat = math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * wy / n))))
        return lat, lon

    top, left = unproject(0, 0)
    bottom, right = unproject(*size_px)
    return bottom, left, top, right


def sheet_frames(sheet: dict) -> list[tuple[float, float, float, float]]:
    W, H = _px(PAGE_MM[0]), _px(PAGE_MM[1])
    frame = _px(zonemap.FRAME_MM)
    panel = _px(sheet.get("panel_mm", zonemap.PANEL_W_MM))
    size = (W - 2 * frame - panel, H - 2 * frame)
    frames = [_frame_bounds(sheet["map"], size)]
    if sheet.get("inset"):
        _, _, iw, ih = (_px(v) for v in sheet["inset"]["box"])
        frames.append(_frame_bounds(sheet["inset"]["map"], (iw, ih - _px(7))))
    return frames


# --------------------------------------------------------------------------
# привязка
# --------------------------------------------------------------------------
def _anchors(sheet: dict, geo: dict) -> list[tuple[str, Point]]:
    """То, ради чего выбирали территорию: площадки, офисы, точечные записи."""
    out = []
    for site in sheet.get("sites", []):
        g = geo.get(site["label"])
        if g is not None:
            out.append((site["label"], g.representative_point()))
    for point in sheet.get("points", []):
        out.append((point["label"], Point(point["at"][1], point["at"][0])))
    for entry in sheet["entries"]:
        if entry.get("point"):
            out.append((entry["short"], Point(entry["point"][1], entry["point"][0])))
    return out


def locate(lat: float, lon: float, *, with_drive: bool = True) -> dict:
    selection, spec, data = load()
    here = Point(lon, lat)
    result = {"lat": lat, "lon": lon, "territories": []}
    for territory in selection["territories"]:
        key = territory["key"]
        geo = data["geo"][key]
        if territory.get("whole"):
            inside = any(g.contains(here) for g in geo.values())
            if inside:
                result["territories"].append({"key": key, "title": territory["title"],
                                              "whole": True, "about": territory.get("about", "")})
            continue
        sheet = data["sheets"][key]
        on_sheet = any(s <= lat <= n and w <= lon <= e for s, w, n, e in sheet_frames(sheet))
        zones = []
        for entry in sheet["entries"]:
            g = geo.get(entry["n"])
            if g is None:
                continue
            zones.append({"n": entry["n"], "name": entry["name"], "kind": entry["kind"],
                          "inside": g.contains(here), "metres": round(_metres(here, g)),
                          "body": entry["body"]})
        zones.sort(key=lambda z: z["metres"])
        if not on_sheet and not any(z["inside"] for z in zones):
            continue
        anchors = sorted(((label, round(_metres(here, p)), p) for label, p in _anchors(sheet, geo)),
                         key=lambda a: a[1])[:4]
        near = []
        for label, metres, p in anchors:
            row = {"label": label, "metres": metres}
            if with_drive:
                route = drive((lat, lon), (p.y, p.x))
                if route:
                    row["km"], row["min"] = round(route[0], 1), round(route[1])
            near.append(row)
        result["territories"].append({
            "key": key, "title": territory["title"], "eyebrow": sheet["eyebrow"],
            "zone": zones[0] if zones else None, "zones": zones[:3], "near": near})
    return result


def describe(result: dict) -> str:
    """Сводка привязки для черновика полосы «Почему выбран»."""
    lines = [f"Объект: {result['lat']:.5f}, {result['lon']:.5f}"]
    if not result["territories"]:
        lines.append("ВНЕ ВЫБРАННЫХ ТЕРРИТОРИЙ — сообщить заказчику, обоснование не подгонять.")
    for t in result["territories"]:
        if t.get("whole"):
            lines.append(f"Территория: {t['title']} — {t['about']}")
            continue
        lines.append(f"Территория: {t['title']} ({t['eyebrow']})")
        for z in t["zones"]:
            where = "внутри контура" if z["inside"] else f"{z['metres'] / 1000:.1f} км до контура"
            lines.append(f"  зона {z['n']} «{z['name']}»: {where}")
        for a in t["near"]:
            road = f", {a['km']} км и {a['min']} мин по дорогам" if "km" in a else ""
            lines.append(f"  рядом: {a['label']} — {a['metres'] / 1000:.1f} км по прямой{road}")
    return "\n".join(lines)


# --------------------------------------------------------------------------
# карта полосы
# --------------------------------------------------------------------------
def why_map(obj: dict, why: dict, size_px: tuple[int, int], dest: Path) -> Path:
    """Карта полосы «Почему выбран»: контуры выбранной территории, её знаки и
    метка объекта. Кадр охватывает объект, его зону и ближайшую площадку."""
    selection, spec, data = load()
    key = why["territory"]
    lat, lon = obj["map"]["lat"], obj["map"]["lon"]
    geo = data["geo"][key]
    territory = next(t for t in selection["territories"] if t["key"] == key)

    if territory.get("whole"):
        sheet = {"key": key, "entries": [], "sites": [], "points": []}
        focus = [(lat, lon)]
        span_m = why.get("span_m", 6000)
    else:
        sheet = data["sheets"][key]
        focus = [(lat, lon)]
        for name in why.get("focus", []):
            for label, p in _anchors(sheet, geo):
                if label == name:
                    focus.append((p.y, p.x))
            for entry in sheet["entries"]:
                if entry["short"] == name and entry["n"] in geo:
                    b = geo[entry["n"]].bounds
                    focus += [(b[1], b[0]), (b[3], b[2])]
        span_m = why.get("span_m", 2500)

    # кадр: все точки фокуса с полями, не уже span_m по короткой стороне
    zoom, scale = 13, 4
    tile = 256 * scale
    xs, ys = zip(*(_world(la, lo, zoom, tile) for la, lo in focus))
    # булавка рисуется над точкой: сверху кадру нужен запас на её высоту
    ox, oy = _world(lat, lon, zoom, tile)
    ys = ys + (oy - (max(ys) - min(ys) + 1) * 0.12 - 60,)
    xs = xs + (ox,)
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    w, h = size_px
    metres_per_world = 40075016.7 * math.cos(math.radians(lat)) / (tile * 2**zoom)
    need = max((max(xs) - min(xs)) * 1.35, (max(ys) - min(ys)) * 1.35 * w / h,
               span_m / metres_per_world * w / min(w, h))
    k = w / need                                  # пиксели кадра на мировой пиксель
    while scale * k < 1.6 and zoom > 9:           # подписи Google не мельче ×1,6
        zoom -= 1
        k *= 2
        cx, cy = cx / 2, cy / 2
    while scale * k > 4.0 and zoom < 16:
        zoom += 1
        k /= 2
        cx, cy = cx * 2, cy * 2
    tile_n = tile * 2**zoom

    def lat_of(y):
        return math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * y / tile_n))))

    half = h / 2 / k
    map_spec = {"zoom": zoom, "tile_scale": scale, "language": "ru",
                "lat_range": [lat_of(cy + half), lat_of(cy - half)],
                "center_lon": cx / tile_n * 360.0 - 180.0}

    fonts = zonemap.PanelFonts()
    img, frame = zonemap._map_image(map_spec, (w, h), sheet, geo, spec["kinds"], fonts)
    draw = ImageDraw.Draw(img)
    if territory.get("whole"):
        style = {"fill": "16233A", "alpha": 0, "outline": "16233A", "dashed": True}
        for g in geo.values():
            zonemap._paint(img, frame, g, style)
        draw = ImageDraw.Draw(img)
    px, py = frame.to_page(lat, lon)
    maps._pin(draw, px, py, 3)
    zonemap._label(draw, (px, py - _px(9.5)), "Объект", fonts.map_label, INK, "right", 3.0)
    dest.parent.mkdir(parents=True, exist_ok=True)
    img.save(dest)
    return dest


if __name__ == "__main__":
    args = sys.argv[1:]
    if args and args[0] == "--latlon":
        lat, lon = float(args[1]), float(args[2])
    else:
        lat, lon, found = geocode(" ".join(args))
        print("Адрес:", found)
    print(describe(locate(lat, lon)))
