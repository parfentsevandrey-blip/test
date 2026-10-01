"""Плакат-карта A3 для печати: Google Maps с пронумерованными метками и
карточками описаний по краям.

    python -m reportgen.poster data/poster-2026-10-01-ai-datacenters.json

Лист — A3 альбомной ориентации, 300 dpi. Карта собирается из тайлов Google
с увеличенным масштабом (`tile_scale`), поэтому подписи городов на печати
читаются, а не превращаются в точки. Карточки стоят слева и справа над морем
и над соседними странами, от каждой к метке идёт выноска. На выходе PDF
точного формата A3 и PNG того же кадра.
"""

from __future__ import annotations

import json
import math
import re
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from io import BytesIO
from pathlib import Path

import requests
from PIL import Image, ImageDraw, ImageFilter, ImageFont

from reportgen import maps

ROOT = Path(__file__).resolve().parent.parent
FONTS = ROOT / "assets" / "fonts"
CACHE = ROOT / ".cache" / "poster-tiles"

DPI = 300
PAGE_MM = (420.0, 297.0)
MM = DPI / 25.4
PT = DPI / 72.0

INK = (0x16, 0x23, 0x3A)
OCHRE = (0x9C, 0x7C, 0x38)
PAPER = (0xF5, 0xF4, 0xF0)
SOFT = (0x4A, 0x55, 0x68)
WHITE = (255, 255, 255)

FRAME_MM = 8.0          # белое поле по краю листа: принтеры навылет не печатают
CARD_W_MM = 96.0
CARD_PAD_MM = 4.6
MARKER_R_MM = 3.7

TILE_URL = "https://mt{s}.google.com/vt/lyrs=m&x={x}&y={y}&z={z}&scale={scale}&hl={hl}"


def _px(mm: float) -> int:
    return round(mm * MM)


def _font(name: str, pt: float) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONTS / f"{name}.ttf"), round(pt * PT))


def _rgb(hex_color: str) -> tuple[int, int, int]:
    return tuple(int(hex_color[i:i + 2], 16) for i in (0, 2, 4))


# --------------------------------------------------------------------------
# карта
# --------------------------------------------------------------------------
def _world(lat: float, lon: float, zoom: int, tile: int) -> tuple[float, float]:
    """Пиксели мировой сетки Меркатора при данном размере тайла."""
    n = tile * 2**zoom
    x = (lon + 180.0) / 360.0 * n
    s = math.sin(math.radians(lat))
    y = (0.5 - math.log((1 + s) / (1 - s)) / (4 * math.pi)) * n
    return x, y


def _tile(z: int, x: int, y: int, scale: int, hl: str, index: int) -> Image.Image:
    CACHE.mkdir(parents=True, exist_ok=True)
    cached = CACHE / f"{hl}-{scale}-{z}-{x}-{y}.png"
    if cached.exists():
        return Image.open(cached).convert("RGB")
    last: Exception | None = None
    for attempt in range(4):
        url = TILE_URL.format(s=(index + attempt) % 4, x=x, y=y, z=z, scale=scale, hl=hl)
        try:
            resp = requests.get(url, headers={"User-Agent": maps.UA}, timeout=30)
            resp.raise_for_status()
            img = Image.open(BytesIO(resp.content)).convert("RGB")
            img.save(cached)
            return img
        except Exception as exc:                       # 403 и сетевые сбои
            last = exc
            time.sleep(0.5 * (attempt + 1))
    raise last


class MapFrame:
    """Кадр карты на листе: проекция координат в пиксели листа."""

    def __init__(self, spec: dict, box: tuple[int, int, int, int]):
        self.zoom = spec.get("zoom", 8)
        self.scale = spec.get("tile_scale", 4)
        self.hl = spec.get("language", "ru")
        self.tile = 256 * self.scale
        self.box = box
        x0, y0, x1, y1 = box
        lat_lo, lat_hi = spec["lat_range"]
        _, top = _world(lat_hi, 0, self.zoom, self.tile)
        _, bottom = _world(lat_lo, 0, self.zoom, self.tile)
        self.k = (y1 - y0) / (bottom - top)            # пиксели листа на пиксель тайла
        cx, _ = _world(0, spec["center_lon"], self.zoom, self.tile)
        self.origin = (cx - (x1 - x0) / 2 / self.k, top)

    def to_page(self, lat: float, lon: float) -> tuple[float, float]:
        wx, wy = _world(lat, lon, self.zoom, self.tile)
        return (self.box[0] + (wx - self.origin[0]) * self.k,
                self.box[1] + (wy - self.origin[1]) * self.k)

    def render(self) -> Image.Image:
        x0, y0, x1, y1 = self.box
        w_world = (x1 - x0) / self.k
        h_world = (y1 - y0) / self.k
        ox, oy = self.origin
        tx0, ty0 = math.floor(ox / self.tile), math.floor(oy / self.tile)
        tx1 = math.floor((ox + w_world) / self.tile)
        ty1 = math.floor((oy + h_world) / self.tile)
        coords = [(x, y) for x in range(tx0, tx1 + 1) for y in range(ty0, ty1 + 1)]
        with ThreadPoolExecutor(6) as pool:
            tiles = list(pool.map(
                lambda item: _tile(self.zoom, item[1][0], item[1][1], self.scale, self.hl, item[0]),
                enumerate(coords)))
        native = Image.new("RGB", ((tx1 - tx0 + 1) * self.tile, (ty1 - ty0 + 1) * self.tile), WHITE)
        for (tx, ty), img in zip(coords, tiles):
            native.paste(img, ((tx - tx0) * self.tile, (ty - ty0) * self.tile))
        crop = (ox - tx0 * self.tile, oy - ty0 * self.tile)
        native = native.crop((round(crop[0]), round(crop[1]),
                              round(crop[0] + w_world), round(crop[1] + h_world)))
        return native.resize((x1 - x0, y1 - y0), Image.LANCZOS)


# --------------------------------------------------------------------------
# набор
# --------------------------------------------------------------------------
NBSP_RULES = (
    (re.compile(r"€\s+(?=\d)"), "€\u00a0"),
    (re.compile(r"(\d)\s+(%|га|млн|млрд|км|минут\w*|м²)"), "\\1\u00a0\\2"),
    (re.compile(r"\s(—)"), "\u00a0\\1"),
    (re.compile(r"(?<=\b[вкисуоаВКИСУОА])\s"), "\u00a0"),
)


def _nbsp(text: str) -> str:
    for pattern, repl in NBSP_RULES:
        text = pattern.sub(repl, text)
    return text


Word = list[tuple[str, bool]]        # слово из кусков: текст и признак выделения


def _words(text: str) -> list[Word]:
    """Слова с выделением: **фрагмент** набирается полужирным.

    Выделение может кончаться посреди слова («**Google**.»), поэтому слово
    собирается из кусков, и точка остаётся прижатой к слову без пробела.
    """
    words: list[Word] = []
    current: Word = []
    for i, part in enumerate(re.split(r"\*\*", _nbsp(text))):
        strong = i % 2 == 1
        for j, chunk in enumerate(re.split(r"([ \t\n]+)", part)):
            if j % 2 == 1:                             # пробел — конец слова
                if current:
                    words.append(current)
                current = []
            elif chunk:
                current.append((chunk, strong))
    if current:
        words.append(current)
    return words


def _width(word: Word, regular, bold) -> float:
    return sum((bold if strong else regular).getlength(piece) for piece, strong in word)


def _wrap(text: str, regular, bold, width: int) -> list[list[Word]]:
    space = regular.getlength(" ")
    lines: list[list[Word]] = [[]]
    used = 0.0
    for word in _words(text):
        w = _width(word, regular, bold)
        if lines[-1] and used + space + w > width:
            lines.append([])
            used = 0.0
        used += (space if lines[-1] else 0) + w
        lines[-1].append(word)
    return lines


def _draw_lines(draw, lines, x, y, regular, bold, color, leading) -> int:
    space = regular.getlength(" ")
    for line in lines:
        cx = x
        for word in line:
            for piece, strong in word:
                font = bold if strong else regular
                draw.text((cx, y), piece, font=font, fill=color)
                cx += font.getlength(piece)
            cx += space
        y += leading
    return y


class Fonts:
    def __init__(self):
        self.kicker = _font("SourceSans3-SemiBold", 7.5)
        self.title = _font("SourceSerif4-Regular", 16.5)
        self.subtitle = _font("SourceSans3-Regular", 8.5)
        self.body = _font("SourceSans3-Regular", 9.6)
        self.body_bold = _font("SourceSans3-SemiBold", 9.6)
        self.near = _font("SourceSans3-Regular", 8.3)
        self.near_bold = _font("SourceSans3-SemiBold", 8.3)
        self.number = _font("SourceSans3-SemiBold", 9.5)
        self.number_small = _font("SourceSans3-SemiBold", 8)
        self.poster_eyebrow = _font("SourceSans3-SemiBold", 9.5)
        self.poster_title = _font("SourceSerif4-Light", 26)
        self.poster_lead = _font("SourceSerif4-Italic", 12)
        self.legend = _font("SourceSans3-Regular", 9)
        self.note = _font("SourceSans3-Regular", 8.3)


def _marker(draw, x, y, r, color, number: str | None, font) -> None:
    ring = max(2, round(0.7 * MM))
    draw.ellipse((x - r - ring, y - r - ring, x + r + ring, y + r + ring), fill=WHITE)
    draw.ellipse((x - r, y - r, x + r, y + r), fill=color)
    if number:
        draw.text((x, y), number, font=font, fill=WHITE, anchor="mm")


# --------------------------------------------------------------------------
# карточки
# --------------------------------------------------------------------------
class Card:
    def __init__(self, point: dict, kind: dict, fonts: Fonts):
        self.point = point
        self.color = _rgb(kind["color"])
        self.f = fonts
        self.w = _px(CARD_W_MM)
        inner = self.w - 2 * _px(CARD_PAD_MM)
        self.title_lines = _wrap(point["title"], fonts.title, fonts.title, inner)
        self.body_lines = _wrap(point["body"], fonts.body, fonts.body_bold, inner)
        near = f"**Рядом:** {point['near']}" if point.get("near") else ""
        self.near_lines = _wrap(near, fonts.near, fonts.near_bold, inner) if near else []
        self.h = self._layout(None, 0, 0)

    def _layout(self, draw, x: int, y: int) -> int:
        f, pad = self.f, _px(CARD_PAD_MM)
        cx, cy = x + pad, y + pad
        r = _px(2.9)
        if draw:
            _marker(draw, cx + r, cy + r, r, self.color, str(self.point["number"]), f.number_small)
            draw.text((cx + 2 * r + _px(2.6), cy + r), self.point["kicker"].upper(),
                      font=f.kicker, fill=OCHRE, anchor="lm")
        cy += 2 * r + _px(2.8)
        lead_title = round(19.5 * PT)
        if draw:
            _draw_lines(draw, self.title_lines, cx, cy, f.title, f.title, INK, lead_title)
        cy += lead_title * len(self.title_lines)
        if self.point.get("subtitle"):
            if draw:
                draw.text((cx, cy), self.point["subtitle"], font=f.subtitle, fill=SOFT)
            cy += round(11.5 * PT)
        cy += _px(2.2)
        lead_body = round(12.8 * PT)
        if draw:
            _draw_lines(draw, self.body_lines, cx, cy, f.body, f.body_bold, INK, lead_body)
        cy += lead_body * len(self.body_lines)
        if self.near_lines:
            cy += _px(2.0)
            if draw:
                draw.line((cx, cy, x + self.w - pad, cy), fill=(0xD9, 0xD3, 0xC4), width=2)
            cy += _px(2.0)
            lead_near = round(11.2 * PT)
            if draw:
                _draw_lines(draw, self.near_lines, cx, cy, f.near, f.near_bold, SOFT, lead_near)
            cy += lead_near * len(self.near_lines)
        return cy + pad - _px(1.0) - y

    def draw(self, page: Image.Image, x: int, y: int) -> None:
        _panel(page, (x, y, x + self.w, y + self.h))
        draw = ImageDraw.Draw(page)
        draw.rectangle((x, y, x + self.w, y + _px(0.9)), fill=self.color)
        self._layout(draw, x, y)


def _panel(page: Image.Image, box: tuple[int, int, int, int]) -> None:
    """Подложка карточки: бумага с мягкой тенью поверх карты."""
    x0, y0, x1, y1 = box
    off, blur = _px(0.8), _px(2.2)
    shadow = Image.new("L", (x1 - x0 + 4 * blur, y1 - y0 + 4 * blur), 0)
    ImageDraw.Draw(shadow).rectangle((2 * blur, 2 * blur, 2 * blur + x1 - x0, 2 * blur + y1 - y0), fill=70)
    shadow = shadow.filter(ImageFilter.GaussianBlur(blur))
    page.paste(Image.new("RGB", shadow.size, (0, 0, 0)), (x0 - 2 * blur + off, y0 - 2 * blur + off), shadow)
    ImageDraw.Draw(page).rectangle(box, fill=PAPER)


def _hook(rect: tuple[int, int, int, int], target: tuple[float, float]) -> tuple[float, float]:
    """Точка на краю карточки, откуда выходит выноска к метке.

    Ближайшая к метке точка периметра, но не ближе 8 мм к углу: выноска из
    самого угла читается как случайная линия, а не как указатель.
    """
    x0, y0, x1, y1 = rect
    tx, ty = target
    keep = _px(8)
    if tx < x0 or tx > x1:                            # метка сбоку
        return (x0 if tx < x0 else x1), min(max(ty, y0 + keep), y1 - keep)
    return min(max(tx, x0 + keep), x1 - keep), (y0 if ty < y0 else y1)


def _overlaps(a, b) -> bool:
    return not (a[2] <= b[0] or b[2] <= a[0] or a[3] <= b[1] or b[3] <= a[1])


# --------------------------------------------------------------------------
# сборка листа
# --------------------------------------------------------------------------
def build(spec_path: Path) -> tuple[Path, Path]:
    """Собирает лист. Карточки, шапка и легенда стоят там, где их поставил
    `card` / `box` в спецификации (миллиметры от левого верхнего угла листа):
    свободные места на карте — море, соседние страны — у каждой страны свои,
    и автоматика расставляет их хуже, чем глаз."""
    spec = json.loads(spec_path.read_text(encoding="utf-8"))
    W, H = _px(PAGE_MM[0]), _px(PAGE_MM[1])
    frame = _px(FRAME_MM)
    box = (frame, frame, W - frame, H - frame)
    fonts = Fonts()
    kinds = spec["kinds"]
    pad = _px(CARD_PAD_MM)

    page = Image.new("RGB", (W, H), WHITE)
    mf = MapFrame(spec["map"], box)
    page.paste(mf.render(), box[:2])
    draw = ImageDraw.Draw(page)
    draw.rectangle(box, outline=INK, width=max(2, round(0.25 * MM)))

    rects: list[tuple[str, tuple[int, int, int, int]]] = []

    # шапка
    hx, hy, hw = (_px(v) for v in spec["header"]["box"])
    title_lead = round(31 * PT)
    title_lines = spec["title"].split("\n")
    lead_lines = _wrap(spec["lead"], fonts.poster_lead, fonts.poster_lead, hw - 2 * pad)
    hh = (pad + round(11 * PT) + _px(2.5) + title_lead * len(title_lines) + _px(2.5)
          + round(15 * PT) * len(lead_lines) + pad)
    rects.append(("шапка", (hx, hy, hx + hw, hy + hh)))

    # легенда: условные знаки, примечание и источники
    lx, ly, lw = (_px(v) for v in spec["legend"]["box"])
    note_lines = _wrap(spec.get("note", ""), fonts.note, fonts.note, lw - 2 * pad)
    src_lines = _wrap(spec.get("sources", ""), fonts.note, fonts.note, lw - 2 * pad)
    note_lead = round(11 * PT)
    lh = (2 * pad + len(kinds) * _px(7) + _px(1.5) + note_lead * len(note_lines)
          + _px(3.5) + note_lead * len(src_lines))
    rects.append(("легенда", (lx, ly, lx + lw, ly + lh)))

    cards = {p["number"]: Card(p, kinds[p["kind"]], fonts) for p in spec["points"]}
    anchors = {p["number"]: mf.to_page(p["lat"], p["lon"]) for p in spec["points"]}
    placed: dict[int, tuple[int, int]] = {}
    for p in spec["points"]:
        x, y = (_px(v) for v in p["card"])
        placed[p["number"]] = (x, y)
        rects.append((f"карточка {p['number']}", (x, y, x + cards[p["number"]].w, y + cards[p["number"]].h)))

    # раскладка проверяется до рисования: наложения и выход за рамку
    inner = (box[0] + _px(3), box[1] + _px(3), box[2] - _px(3), box[3] - _px(3))
    problems = []
    for i, (name, r) in enumerate(rects):
        if r[0] < inner[0] or r[1] < inner[1] or r[2] > inner[2] or r[3] > inner[3]:
            problems.append(f"{name} выходит за рамку: низ {r[3] / MM:.0f} мм, право {r[2] / MM:.0f} мм")
        for other, q in rects[i + 1:]:
            if _overlaps(r, q):
                problems.append(f"{name} налезает на «{other}»")
    for n, (mx, my) in anchors.items():
        for name, r in rects:
            if r[0] - _px(5) < mx < r[2] + _px(5) and r[1] - _px(5) < my < r[3] + _px(5):
                problems.append(f"метка {n} закрыта: {name}")
    for line in problems:
        print("раскладка:", line, file=sys.stderr)
    for name, r in rects:
        print(f"{name}: {r[0] / MM:.0f}–{r[2] / MM:.0f} × {r[1] / MM:.0f}–{r[3] / MM:.0f} мм",
              file=sys.stderr)

    # выноски — под карточками и метками
    r_marker = _px(MARKER_R_MM)
    lines = Image.new("RGBA", page.size, (0, 0, 0, 0))
    ldraw = ImageDraw.Draw(lines)
    for n, (x, y) in placed.items():
        card, (mx, my) = cards[n], anchors[n]
        sx, sy = _hook((x, y, x + card.w, y + card.h), (mx, my))
        dx, dy = mx - sx, my - sy
        dist = math.hypot(dx, dy) or 1
        end = (mx - dx / dist * r_marker, my - dy / dist * r_marker)
        ldraw.line((sx, sy, *end), fill=(*WHITE, 235), width=_px(1.3))
        ldraw.line((sx, sy, *end), fill=(*INK, 255), width=_px(0.45))
        ldraw.ellipse((sx - _px(1.1), sy - _px(1.1), sx + _px(1.1), sy + _px(1.1)), fill=(*INK, 255))
    page.paste(lines, (0, 0), lines)

    # шапка
    _panel(page, rects[0][1])
    draw = ImageDraw.Draw(page)
    draw.rectangle((hx, hy, hx + hw, hy + _px(1.2)), fill=OCHRE)
    y = hy + pad
    draw.text((hx + pad, y), spec["eyebrow"].upper(), font=fonts.poster_eyebrow, fill=OCHRE)
    y += round(11 * PT) + _px(2.5)
    for line in title_lines:
        draw.text((hx + pad, y), line, font=fonts.poster_title, fill=INK)
        y += title_lead
    y += _px(2.5)
    _draw_lines(draw, lead_lines, hx + pad, y, fonts.poster_lead, fonts.poster_lead,
                SOFT, round(15 * PT))

    # легенда
    _panel(page, rects[1][1])
    draw = ImageDraw.Draw(page)
    y = ly + pad
    for kind in kinds.values():
        r = _px(2.6)
        _marker(draw, lx + pad + r, y + r, r, _rgb(kind["color"]), None, None)
        draw.text((lx + pad + 2 * r + _px(3), y + r), kind["label"], font=fonts.legend,
                  fill=INK, anchor="lm")
        y += _px(7)
    y += _px(1.5)
    y = _draw_lines(draw, note_lines, lx + pad, y, fonts.note, fonts.note, INK, note_lead)
    y += _px(1.5)
    draw.line((lx + pad, y, lx + lw - pad, y), fill=(0xD9, 0xD3, 0xC4), width=2)
    y += _px(2)
    _draw_lines(draw, src_lines, lx + pad, y, fonts.note, fonts.note, SOFT, note_lead)

    for n, (x, y) in placed.items():
        cards[n].draw(page, x, y)

    draw = ImageDraw.Draw(page)
    for p in spec["points"]:
        mx, my = anchors[p["number"]]
        _marker(draw, mx, my, r_marker, _rgb(kinds[p["kind"]]["color"]), str(p["number"]), fonts.number)

    out_dir = ROOT / spec.get("output_dir", "output")
    out_dir.mkdir(parents=True, exist_ok=True)
    png = out_dir / f"{spec['filename']}.png"
    pdf = out_dir / f"{spec['filename']}.pdf"
    page.save(png, dpi=(DPI, DPI), optimize=True)
    _to_pdf(page, pdf)
    return pdf, png


def _to_pdf(page: Image.Image, path: Path) -> None:
    """PDF ровно A3: растр 300 dpi во всю страницу, JPEG высокого качества."""
    import pymupdf

    buf = BytesIO()
    page.save(buf, "JPEG", quality=92, subsampling=0, dpi=(DPI, DPI))
    doc = pymupdf.open()
    w_pt, h_pt = PAGE_MM[0] / 25.4 * 72, PAGE_MM[1] / 25.4 * 72
    sheet = doc.new_page(width=w_pt, height=h_pt)
    sheet.insert_image(sheet.rect, stream=buf.getvalue())
    doc.save(path, deflate=True)


if __name__ == "__main__":
    for arg in sys.argv[1:]:
        for result in build(Path(arg)):
            print(result)
