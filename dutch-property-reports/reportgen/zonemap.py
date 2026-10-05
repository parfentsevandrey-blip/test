"""Листы A3 с промзонами: подробная карта Google с контурами зон и колонка
описаний справа.

    python -m reportgen.zonemap fetch data/zonemap-2026-10-01-ai.json
    python -m reportgen.zonemap build data/zonemap-2026-10-01-ai.json

`fetch` скачивает контуры и кладёт их рядом со спецификацией, в
`data/zones/<лист>.geojson`, чтобы лист собирался без сети к реестрам:

- `ibis` — реестр бизнес-парков IBIS, номер RIN; берётся последний год;
- `cbs` — квартал CBS по коду (`BU…`), через WFS PDOK;
- `osm` — контур из OpenStreetMap по номеру way: центры обработки данных,
  заводы и площадки компаний, которых в реестрах нет.

`build` собирает листы: карта слева, справа шапка листа и описания зон.
Зоны нумеруются на карте тем же номером, что и в колонке. На выходе один
многостраничный PDF формата A3 и PNG каждого листа.
"""

from __future__ import annotations

import json
import math
import sys
import time
from pathlib import Path

import requests
from PIL import Image, ImageDraw
from pyproj import Geod
from shapely.geometry import MultiPolygon, Polygon, mapping, shape
from shapely.ops import unary_union

from reportgen.poster import (
    DPI, INK, MM, OCHRE, PAGE_MM, PAPER, PT, SOFT, WHITE, MapFrame, _draw_lines, _font,
    _marker, _px, _rgb, _to_pdf, _wrap,
)

ROOT = Path(__file__).resolve().parent.parent
IBIS_URL = ("https://services.arcgis.com/nSZVuSZjHpEZZbRo/arcgis/rest/services/"
            "IBIS_Bedrijventerreinen_historie/FeatureServer/0/query")
CBS_URL = "https://service.pdok.nl/cbs/wijkenbuurten/2024/wfs/v1_0"
OVERPASS_URL = "https://maps.mail.ru/osm/tools/overpass/api/interpreter"
UA = {"User-Agent": "dutch-property-reports/1.0"}

FRAME_MM = 8.0
PANEL_W_MM = 122.0
PANEL_PAD_MM = 7.0
GEOD = Geod(ellps="WGS84")


# --------------------------------------------------------------------------
# контуры
# --------------------------------------------------------------------------
def _ibis(rin: str):
    resp = requests.get(IBIS_URL, params={
        "where": f"RIN_NUMMER='{rin}'", "outFields": "RIN_NUMMER,JAAR,PLAN_NAAM",
        "returnGeometry": "true", "outSR": 4326, "f": "geojson"}, timeout=120)
    feats = resp.json()["features"]
    latest = max(feats, key=lambda f: f["properties"]["JAAR"] or 0)
    return shape(latest["geometry"]), latest["properties"]["PLAN_NAAM"]


def _cbs(code: str):
    flt = ('<Filter xmlns="http://www.opengis.net/fes/2.0"><PropertyIsEqualTo>'
           f'<ValueReference>buurtcode</ValueReference><Literal>{code}</Literal>'
           '</PropertyIsEqualTo></Filter>')
    resp = requests.get(CBS_URL, params={
        "service": "WFS", "version": "2.0.0", "request": "GetFeature",
        "typeNames": "wijkenbuurten:buurten", "outputFormat": "application/json",
        "srsName": "EPSG:4326", "filter": flt}, timeout=120)
    feat = resp.json()["features"][0]
    return shape(feat["geometry"]), feat["properties"]["buurtnaam"]


def _osm(ways: list[int]) -> dict[int, tuple]:
    query = f'[out:json][timeout:120];way(id:{",".join(map(str, ways))});out tags geom;'
    for attempt in range(6):
        try:
            data = requests.post(OVERPASS_URL, data={"data": query}, timeout=200).json()
            break
        except Exception:                              # зеркало отвечает 504 под нагрузкой
            time.sleep(8 * (attempt + 1))
    else:
        raise RuntimeError("Overpass не ответил")
    out = {}
    for el in data["elements"]:
        poly = Polygon([(p["lon"], p["lat"]) for p in el["geometry"]])
        out[el["id"]] = (poly if poly.is_valid else poly.buffer(0), el["tags"].get("name", ""))
    return out


def _resolve(refs: list[dict], osm_cache: dict) -> tuple:
    parts, names = [], []
    for ref in refs:
        if "ibis" in ref:
            geom, name = _ibis(str(ref["ibis"]))
        elif "cbs" in ref:
            geom, name = _cbs(ref["cbs"])
        else:
            geom, name = osm_cache[ref["osm"]]
        parts.append(geom)
        names.append(name)
    return unary_union(parts), names


def fetch(spec_path: Path) -> None:
    spec = json.loads(spec_path.read_text(encoding="utf-8"))
    out_dir = spec_path.parent / "zones"
    out_dir.mkdir(exist_ok=True)
    for sheet in spec["sheets"]:
        items = [e for e in sheet["entries"] if "geom" in e] + sheet.get("sites", [])
        ways = [r["osm"] for item in items for r in item["geom"] if "osm" in r]
        osm_cache = _osm(ways) if ways else {}
        feats = []
        for item in items:
            geom, names = _resolve(item["geom"], osm_cache)
            key = item.get("n", item.get("label"))
            feats.append({"type": "Feature", "geometry": mapping(geom),
                          "properties": {"key": key, "source_names": names,
                                         "ha": round(_hectares(geom), 1)}})
            print(f"{sheet['key']}: {key} ← {', '.join(names)} ({_hectares(geom):.0f} га)")
        (out_dir / f"{sheet['key']}.geojson").write_text(
            json.dumps({"type": "FeatureCollection", "features": feats}, ensure_ascii=False),
            encoding="utf-8")


def _area_text(ha: float) -> str:
    """Площадь контура для подписи: крупные — до десятков, мелкие — до пятёрок."""
    value = round(ha, -1) if ha >= 100 else max(5, 5 * round(ha / 5))
    return f"≈ {int(value):,} га".replace(",", ".")


def _hectares(geom) -> float:
    return abs(GEOD.geometry_area_perimeter(geom)[0]) / 1e4


# --------------------------------------------------------------------------
# рисование контуров
# --------------------------------------------------------------------------
def _polygons(geom):
    if isinstance(geom, Polygon):
        return [geom]
    if isinstance(geom, MultiPolygon):
        return list(geom.geoms)
    return [g for g in getattr(geom, "geoms", []) if isinstance(g, Polygon)]


def _ring(frame: MapFrame, coords) -> list[tuple[float, float]]:
    return [frame.to_page(lat, lon) for lon, lat in coords]


def _dashed(draw, points, color, width, dash, gap) -> None:
    """Пунктир по ломаной: у PIL штатного пунктира нет. Фаза периода
    переносится через вершины, поэтому рисунок штрихов не сбивается."""
    period = dash + gap
    phase = 0.0
    for (x0, y0), (x1, y1) in zip(points, points[1:]):
        seg = math.hypot(x1 - x0, y1 - y0)
        t = 0.0
        while seg - t > 1e-6:
            run = min((dash - phase) if phase < dash else (period - phase), seg - t)
            if phase < dash:
                a, b = t / seg, (t + run) / seg
                draw.line((x0 + (x1 - x0) * a, y0 + (y1 - y0) * a,
                           x0 + (x1 - x0) * b, y0 + (y1 - y0) * b), fill=color, width=width)
            t += run
            phase = (phase + run) % period


def _paint(page: Image.Image, frame: MapFrame, geom, style: dict) -> None:
    """Заливка с прозрачностью и контур с белой подложкой. Работает в рамке
    контура, а не во весь лист: лист 300 dpi весит 50 мегапикселей."""
    rings = [(_ring(frame, poly.exterior.coords), [_ring(frame, h.coords) for h in poly.interiors])
             for poly in _polygons(geom)]
    xs = [x for ext, _ in rings for x, _ in ext]
    ys = [y for ext, _ in rings for _, y in ext]
    pad = _px(2)
    x0, y0 = max(0, int(min(xs)) - pad), max(0, int(min(ys)) - pad)
    x1, y1 = min(page.width, int(max(xs)) + pad), min(page.height, int(max(ys)) + pad)
    if x1 <= x0 or y1 <= y0:
        return
    shift = lambda pts: [(x - x0, y - y0) for x, y in pts]
    mask = Image.new("L", (x1 - x0, y1 - y0), 0)
    mdraw = ImageDraw.Draw(mask)
    for ext, holes in rings:
        mdraw.polygon(shift(ext), fill=style["alpha"])
        for hole in holes:
            mdraw.polygon(shift(hole), fill=0)
    page.paste(Image.new("RGB", mask.size, _rgb(style["fill"])), (x0, y0), mask)

    draw = ImageDraw.Draw(page)
    outline = _rgb(style["outline"])
    halo, line = _px(style.get("halo_mm", 1.3)), _px(style.get("line_mm", 0.55))
    for ext, holes in rings:
        for pts in [ext, *holes]:
            draw.line(pts, fill=WHITE, width=halo, joint="curve")
            if style.get("dashed"):
                _dashed(draw, pts, outline, line, _px(2.2), _px(1.3))
            else:
                draw.line(pts, fill=outline, width=line, joint="curve")


def _label(draw, xy, text, font, color, side="right", gap_mm=2.0) -> tuple:
    x, y = xy
    gap = _px(gap_mm)
    anchor, pos = {
        "right": ("lm", (x + gap, y)), "left": ("rm", (x - gap, y)),
        "above": ("md", (x, y - gap)), "below": ("ma", (x, y + gap)),
    }[side]
    draw.text(pos, text, font=font, fill=color, anchor=anchor,
              stroke_width=_px(0.55), stroke_fill=WHITE)
    return draw.textbbox(pos, text, font=font, anchor=anchor)


def _scale_bar(page, frame: MapFrame, lat: float, at: tuple[int, int], font) -> None:
    metres_per_px = 40075016.7 * math.cos(math.radians(lat)) / (frame.tile * 2**frame.zoom * frame.k)
    target = _px(40) * metres_per_px
    nice = max(v for v in (500, 1000, 2000, 3000, 5000, 10000) if v <= target)
    length = nice / metres_per_px
    x, y = at
    draw = ImageDraw.Draw(page)
    pad = _px(2.5)
    label = f"{nice // 1000} км" if nice >= 1000 else f"{nice} м"
    box = (x - pad, y - _px(7.5), x + length + pad, y + pad)
    draw.rectangle(box, fill=(*WHITE,))
    h = _px(1.6)
    half = length / 2
    draw.rectangle((x, y - h, x + half, y), fill=INK)
    draw.rectangle((x + half, y - h, x + length, y), outline=INK, width=2, fill=WHITE)
    draw.text((x, y - h - _px(1)), "0", font=font, fill=INK, anchor="ld")
    draw.text((x + length, y - h - _px(1)), label, font=font, fill=INK, anchor="rd")


# --------------------------------------------------------------------------
# колонка описаний
# --------------------------------------------------------------------------
class PanelFonts:
    def __init__(self, scale: float = 1.0):
        s = scale
        self.eyebrow = _font("SourceSans3-SemiBold", 8.5)
        self.title = _font("SourceSerif4-Light", 23)
        self.lead = _font("SourceSerif4-Italic", 10.5 * s)
        self.name = _font("SourceSerif4-Regular", 13 * s)
        self.meta = _font("SourceSans3-Regular", 8 * s)
        self.body = _font("SourceSans3-Regular", 9 * s)
        self.body_bold = _font("SourceSans3-SemiBold", 9 * s)
        self.near = _font("SourceSans3-Regular", 8 * s)
        self.near_bold = _font("SourceSans3-SemiBold", 8 * s)
        self.badge = _font("SourceSans3-SemiBold", 8.5)
        self.map_badge = _font("SourceSans3-SemiBold", 10)
        self.map_label = _font("SourceSans3-SemiBold", 9.5)
        self.site_label = _font("SourceSans3-SemiBold", 8.5)
        self.legend = _font("SourceSans3-Regular", 8)
        self.small = _font("SourceSans3-Regular", 7.2)
        self.scale = s


def _panel_layout(sheet: dict, kinds: dict, f: PanelFonts, x: int, y: int, w: int,
                  draw: ImageDraw.ImageDraw | None) -> int:
    """Раскладка колонки: при `draw=None` только меряет высоту."""
    s = f.scale
    inner = w
    if draw:
        draw.text((x, y), sheet["eyebrow"].upper(), font=f.eyebrow, fill=OCHRE)
    y += round(11 * PT) + _px(2)
    for line in sheet["title"].split("\n"):
        if draw:
            draw.text((x, y), line, font=f.title, fill=INK)
        y += round(26 * PT)
    y += _px(1.5)
    lead = _wrap(sheet["lead"], f.lead, f.lead, inner)
    if draw:
        _draw_lines(draw, lead, x, y, f.lead, f.lead, SOFT, round(14 * s * PT))
    y += round(14 * s * PT) * len(lead) + _px(4)
    if draw:
        draw.line((x, y, x + inner, y), fill=OCHRE, width=_px(0.35))
    y += _px(4.5)

    for entry in sheet["entries"]:
        r = _px(3.0)
        indent = 2 * r + _px(3)
        if draw:
            color = _rgb(kinds[entry["kind"]]["badge"])
            _marker(draw, x + r, y + r + _px(0.6), r, color, str(entry["n"]), f.badge)
        name_lines = _wrap(entry["name"], f.name, f.name, inner - indent)
        lead_name = round(15.5 * s * PT)
        if draw:
            _draw_lines(draw, name_lines, x + indent, y - _px(0.6), f.name, f.name, INK, lead_name)
        y += lead_name * len(name_lines)
        if draw:
            draw.text((x + indent, y), entry["meta_text"], font=f.meta, fill=OCHRE)
        y += round(11 * s * PT)
        body = _wrap(entry["body"], f.body, f.body_bold, inner - indent)
        lead_body = round(12 * s * PT)
        if draw:
            _draw_lines(draw, body, x + indent, y, f.body, f.body_bold, INK, lead_body)
        y += lead_body * len(body)
        if entry.get("near"):
            near = _wrap(f"**В пути:** {entry['near']}", f.near, f.near_bold, inner - indent)
            lead_near = round(10.8 * s * PT)
            y += _px(0.8)
            if draw:
                _draw_lines(draw, near, x + indent, y, f.near, f.near_bold, SOFT, lead_near)
            y += lead_near * len(near)
        y += _px(5.0 * s)
    return y


def _legend(page, kinds: dict, spec: dict, f: PanelFonts, corner: str, box) -> None:
    """Условные знаки и источники — плашкой в углу карты."""
    w = _px(92)
    pad = _px(3.5)
    note = _wrap(spec["legend_note"], f.small, f.small, w - 2 * pad)
    src = _wrap(spec["sources"], f.small, f.small, w - 2 * pad)
    row = _px(5.5)
    lead = round(9.4 * PT)
    h = 2 * pad + row * len(kinds) + _px(1.5) + lead * (len(note) + len(src)) + _px(2.5)
    x0, y0, x1, y1 = box
    m = _px(5)
    x = x0 + m if corner.endswith("l") else x1 - m - w
    y = y0 + m if corner.startswith("t") else y1 - m - h
    draw = ImageDraw.Draw(page, "RGBA")
    draw.rectangle((x, y, x + w, y + h), fill=(*PAPER, 240), outline=(0xD9, 0xD3, 0xC4), width=2)
    cy = y + pad
    for kind in kinds.values():
        if kind.get("dot"):
            _marker(draw, x + pad + _px(3.5), cy + _px(2.5), _px(1.7), _rgb(kind["fill"]), None, None)
        else:
            sw = (x + pad, cy + _px(0.6), x + pad + _px(7), cy + _px(4.4))
            fill = (*_rgb(kind["fill"]), max(kind["alpha"], 90))
            draw.rectangle(sw, fill=fill, outline=_rgb(kind["outline"]), width=_px(0.4))
        draw.text((x + pad + _px(9.5), cy + _px(2.5)), kind["label"], font=f.legend, fill=INK, anchor="lm")
        cy += row
    cy += _px(1.5)
    cy = _draw_lines(draw, note, x + pad, cy, f.small, f.small, INK, lead)
    cy += _px(1.2)
    draw.line((x + pad, cy, x + w - pad, cy), fill=(0xD9, 0xD3, 0xC4), width=2)
    cy += _px(1.3)
    _draw_lines(draw, src, x + pad, cy, f.small, f.small, SOFT, lead)


# --------------------------------------------------------------------------
# лист
# --------------------------------------------------------------------------
def _map_image(map_spec: dict, size: tuple[int, int], sheet: dict, geo: dict, kinds: dict,
               f: "PanelFonts") -> tuple[Image.Image, MapFrame]:
    """Карта с контурами, номерами и подписями — отдельной картинкой, чтобы
    всё нарисованное обрезалось по её рамке (это нужно и для врезки)."""
    frame = MapFrame(map_spec, (0, 0, *size))
    print(f"{sheet['key']}: масштаб подписей ×{frame.scale * frame.k:.2f}", file=sys.stderr)
    img = frame.render()

    # зоны: сначала крупные, чтобы мелкие лежали сверху; у точечной записи контура нет
    zoned = [e for e in sheet["entries"] if e["n"] in geo]
    for entry in sorted(zoned, key=lambda e: -geo[e["n"]].area):
        _paint(img, frame, geo[entry["n"]], kinds[entry["kind"]])
    for site in sheet.get("sites", []):
        _paint(img, frame, geo[site["label"]], kinds["site"])

    inside = lambda x, y: _px(4) < x < size[0] - _px(4) and _px(4) < y < size[1] - _px(4)
    draw = ImageDraw.Draw(img)
    for site in sheet.get("sites", []):
        g = geo[site["label"]]
        lat, lon = site.get("at") or (g.representative_point().y, g.representative_point().x)
        px, py = frame.to_page(lat, lon)
        if inside(px, py) and not site.get("hide_label"):
            _label(draw, (px, py), site["label"], f.site_label, INK, site.get("side", "right"), 1.5)
    # точки компаний: офисы, склады, отдельные здания
    dot = kinds.get("point", {})
    for point in sheet.get("points", []):
        px, py = frame.to_page(*point["at"])
        if not inside(px, py):
            continue
        r = _px(1.7)
        _marker(draw, px, py, r, _rgb(dot.get("fill", "16233A")), None, None)
        if not point.get("hide_label"):
            _label(draw, (px, py), point["label"], f.site_label, INK, point.get("side", "right"), 2.6)
    for entry in sheet["entries"]:
        if entry.get("at") or entry.get("point"):
            lat, lon = entry.get("at") or entry["point"]
        else:
            g = geo[entry["n"]]
            lat, lon = g.representative_point().y, g.representative_point().x
        px, py = frame.to_page(lat, lon)
        if not inside(px, py):
            continue
        r = _px(3.4)
        _marker(draw, px, py, r, _rgb(kinds[entry["kind"]]["badge"]), str(entry["n"]), f.map_badge)
        if entry.get("hide_label"):
            continue
        side = entry.get("side", "right")
        dx, dy = {"right": (1, 0), "left": (-1, 0), "above": (0, -1), "below": (0, 1)}[side]
        reach = r + _px(0.6)
        _label(draw, (px + dx * reach, py + dy * reach), entry["short"], f.map_label, INK, side, 1.2)
    return img, frame


def render_sheet(spec: dict, sheet: dict, geo_dir: Path) -> Image.Image:
    W, H = _px(PAGE_MM[0]), _px(PAGE_MM[1])
    frame_px = _px(FRAME_MM)
    panel_w = _px(sheet.get("panel_mm", PANEL_W_MM))
    map_box = (frame_px, frame_px, W - frame_px - panel_w, H - frame_px)
    kinds = spec["kinds"]
    page = Image.new("RGB", (W, H), WHITE)

    geo = {f["properties"]["key"]: shape(f["geometry"])
           for f in json.loads((geo_dir / f"{sheet['key']}.geojson").read_text())["features"]}
    for entry in sheet["entries"]:
        area = _area_text(_hectares(geo[entry["n"]])) if entry["n"] in geo else ""
        entry["meta_text"] = entry["meta"].replace("{ha}", area)

    f = PanelFonts()
    size = (map_box[2] - map_box[0], map_box[3] - map_box[1])
    img, mf = _map_image(sheet["map"], size, sheet, geo, kinds, f)
    page.paste(img, map_box[:2])
    draw = ImageDraw.Draw(page)
    draw.rectangle(map_box, outline=INK, width=max(2, round(0.25 * MM)))

    # врезка: второй участок другим масштабом, с подписью сверху
    inset = sheet.get("inset")
    if inset:
        ix, iy, iw, ih = (_px(v) for v in inset["box"])
        ix, iy = map_box[0] + ix, map_box[1] + iy
        cap_h = _px(7)
        sub, _ = _map_image(inset["map"], (iw, ih - cap_h), sheet, geo, kinds, f)
        shadow = Image.new("RGB", (iw, ih), (0x8A, 0x8F, 0x98))
        page.paste(shadow, (ix + _px(0.8), iy + _px(0.8)))
        page.paste(sub, (ix, iy + cap_h))
        draw = ImageDraw.Draw(page)
        draw.rectangle((ix, iy, ix + iw, iy + cap_h), fill=PAPER)
        draw.text((ix + _px(3), iy + cap_h // 2), inset["caption"], font=f.legend, fill=INK, anchor="lm")
        draw.rectangle((ix, iy, ix + iw, iy + ih), outline=INK, width=max(2, round(0.3 * MM)))

    # масштабная линейка и условные знаки
    lat_mid = sum(sheet["map"]["lat_range"]) / 2
    corner = sheet.get("legend", "bl")
    bar_corner = sheet.get("scale_bar", "br" if corner != "br" else "bl")
    bx = map_box[0] + _px(8) if bar_corner.endswith("l") else map_box[2] - _px(50)
    by = map_box[3] - _px(7) if bar_corner.startswith("b") else map_box[1] + _px(14)
    _scale_bar(page, mf, lat_mid, (bx, by), f.legend)
    _legend(page, kinds, spec, f, corner, map_box)

    # колонка: подбор кегля, чтобы всё встало по высоте
    px0 = map_box[2] + _px(PANEL_PAD_MM)
    pw = W - frame_px - px0 - _px(1)
    top, bottom = frame_px + _px(PANEL_PAD_MM), H - frame_px - _px(4)
    for scale in (1.15, 1.1, 1.05, 1.0, 0.96, 0.92, 0.88, 0.84):
        f = PanelFonts(scale)
        if top + _panel_layout(sheet, kinds, f, px0, 0, pw, None) <= bottom:
            break
    else:
        print(f"{sheet['key']}: колонка не помещается даже при уменьшении кегля", file=sys.stderr)
    used = _panel_layout(sheet, kinds, f, px0, top, pw, ImageDraw.Draw(page))
    print(f"{sheet['key']}: колонка {(used - top) / MM:.0f} из {(bottom - top) / MM:.0f} мм, "
          f"кегль ×{f.scale}", file=sys.stderr)
    return page


def build(spec_path: Path) -> list[Path]:
    spec = json.loads(spec_path.read_text(encoding="utf-8"))
    geo_dir = spec_path.parent / "zones"
    out_dir = ROOT / spec.get("output_dir", "output")
    out_dir.mkdir(parents=True, exist_ok=True)
    pages, written = [], []
    for i, sheet in enumerate(spec["sheets"], 1):
        page = render_sheet(spec, sheet, geo_dir)
        png = out_dir / f"{spec['filename']} — лист {i}.png"
        page.save(png, dpi=(DPI, DPI), optimize=True)
        written.append(png)
        pages.append(page)
    pdf = out_dir / f"{spec['filename']}.pdf"
    _to_pdf(pages, pdf, quality=spec.get("jpeg_quality", 90))
    return [pdf, *written]


if __name__ == "__main__":
    command, *paths = sys.argv[1:]
    for arg in paths:
        if command == "fetch":
            fetch(Path(arg))
        else:
            for result in build(Path(arg)):
                print(result)
