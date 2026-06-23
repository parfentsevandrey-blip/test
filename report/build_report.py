#!/usr/bin/env python3
"""
build_report.py — render a multi-page A4 real-estate report PDF from a JSON
content model, in the house style (navy headings, gold rules, gray spec table).

Document structure (per the reference report):

    cover                       title + gold rules + numbered object list
    per object:
        text page(s)            title, district line, spec table, prose sections
                                (auto-paginated — long objects flow onto more pages)
        map + main photo page   OSM overview map (marker, towns visible) + facade
        gallery pages           two listing photos per page

The map is rendered keylessly by stitching OpenStreetMap tiles (reused from
funda_a4/make_sheet.py). Coordinates come from the JSON, or are geocoded from
the address via Nominatim when absent.

Usage:
    python3 build_report.py content_example.json -o report.pdf
"""

from __future__ import annotations

import argparse
import glob as globmod
import json
import os
import sys
from typing import List, Optional, Tuple

from PIL import Image, ImageDraw, ImageFont

# Reuse the keyless OSM map + geocoder from the sibling tool.
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "funda_a4"))
from make_sheet import osm_static_map, geocode, load_photo  # noqa: E402

# --------------------------------------------------------------------------
# Page geometry & palette
# --------------------------------------------------------------------------

DPI = 150
PW = int(8.27 * DPI)    # 1240
PH = int(11.69 * DPI)   # 1754
MARGIN = 64
CW = PW - 2 * MARGIN    # content width
BOTTOM = PH - MARGIN

NAVY = (31, 59, 95)
GOLD = (176, 141, 87)
DARK = (38, 38, 40)
GRAY = (96, 96, 100)
ROW_BG = (244, 244, 247)
LINE = (208, 208, 214)

FONT_DIR = "/usr/share/fonts/truetype/dejavu"


def _f(name: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(os.path.join(FONT_DIR, name), size)


def reg(s):  return _f("DejaVuSans.ttf", s)
def bold(s): return _f("DejaVuSans-Bold.ttf", s)
def ital(s): return _f("DejaVuSerif.ttf", s)


# --------------------------------------------------------------------------
# Document with automatic pagination
# --------------------------------------------------------------------------

class Doc:
    def __init__(self):
        self.pages: List[Image.Image] = []
        self.img: Optional[Image.Image] = None
        self.d: Optional[ImageDraw.ImageDraw] = None
        self.y = 0

    def new_page(self) -> None:
        self.img = Image.new("RGB", (PW, PH), "white")
        self.d = ImageDraw.Draw(self.img)
        self.pages.append(self.img)
        self.y = MARGIN

    def ensure(self, h: int) -> None:
        if self.img is None or self.y + h > BOTTOM:
            self.new_page()

    def save(self, path: str) -> None:
        first, rest = self.pages[0], self.pages[1:]
        first.save(path, "PDF", resolution=DPI, save_all=True, append_images=rest)


# --------------------------------------------------------------------------
# Text helpers
# --------------------------------------------------------------------------

def wrap(d, text, font, max_w) -> List[str]:
    """Word-wrap, with hard splitting of over-long tokens (e.g. URLs)."""
    out: List[str] = []
    for raw_line in text.split("\n"):
        words = raw_line.split(" ")
        cur = ""
        for w in words:
            trial = (cur + " " + w).strip()
            if d.textlength(trial, font=font) <= max_w:
                cur = trial
                continue
            if cur:
                out.append(cur)
            # token itself too long -> char split
            while d.textlength(w, font=font) > max_w:
                i = 1
                while i <= len(w) and d.textlength(w[:i], font=font) <= max_w:
                    i += 1
                out.append(w[: i - 1])
                w = w[i - 1:]
            cur = w
        out.append(cur)
    return out


def draw_para(doc: Doc, text, font, color, max_w, lh, x=MARGIN, space_after=8):
    for line in wrap(doc.d, text, font, max_w):
        doc.ensure(lh)
        doc.d.text((x, doc.y), line, font=font, fill=color)
        doc.y += lh
    doc.y += space_after


def draw_bullet(doc: Doc, text, font, color, lh):
    bx = MARGIN + 6
    tx = MARGIN + 24
    lines = wrap(doc.d, text, font, CW - 24)
    for i, line in enumerate(lines):
        doc.ensure(lh)
        if i == 0:
            doc.d.ellipse([bx, doc.y + lh // 2 - 2, bx + 4, doc.y + lh // 2 + 2], fill=GOLD)
        doc.d.text((tx, doc.y), line, font=font, fill=color)
        doc.y += lh


# --------------------------------------------------------------------------
# Building blocks
# --------------------------------------------------------------------------

def cover(doc: Doc, title: str, subtitle: str, items: List[dict]):
    doc.new_page()
    cy = int(PH * 0.30)
    f_sub = ital(22)
    # gold rules around the title
    doc.d.line([(MARGIN, cy - 28), (PW - MARGIN, cy - 28)], fill=GOLD, width=2)
    # letter-spaced title, centered; auto-shrink to fit the content width
    spaced = " ".join(list(title))
    size = 40
    while size > 16:
        f_title = bold(size)
        if doc.d.textlength(spaced, font=f_title) <= CW:
            break
        size -= 1
    tw = doc.d.textlength(spaced, font=f_title)
    doc.d.text(((PW - tw) / 2, cy), spaced, font=f_title, fill=NAVY)
    sw = doc.d.textlength(subtitle, font=f_sub)
    doc.d.text(((PW - sw) / 2, cy + 56), subtitle, font=f_sub, fill=GRAY)
    doc.d.line([(MARGIN, cy + 100), (PW - MARGIN, cy + 100)], fill=GOLD, width=2)

    # numbered list
    ly = cy + 190
    f_lead = reg(18)
    f_num = bold(18)
    head = "В подборку входят:"
    hw = doc.d.textlength(head, font=reg(16))
    doc.d.text(((PW - hw) / 2, ly), head, font=reg(16), fill=GOLD)
    ly += 44
    for i, it in enumerate(items, 1):
        addr = it["address"].split(",")[0]
        city = it["address"].split(",")[-1].strip()
        price = it.get("price_label", "")
        seg = [(f"{i}. ", f_num, GOLD), (f"{addr}, ", f_num, NAVY),
               (f"{city} — {price}", f_lead, DARK)]
        total = sum(doc.d.textlength(t, font=f) for t, f, _ in seg)
        x = (PW - total) / 2
        for t, f, c in seg:
            doc.d.text((x, ly), t, font=f, fill=c)
            x += doc.d.textlength(t, font=f)
        ly += 34


def title_block(doc: Doc, obj: dict):
    doc.ensure(90)
    doc.d.text((MARGIN, doc.y), obj["address"], font=bold(26), fill=NAVY)
    doc.y += 36
    doc.d.text((MARGIN, doc.y), obj.get("district", ""), font=reg(13), fill=GOLD)
    doc.y += 22
    doc.d.line([(MARGIN, doc.y), (PW - MARGIN, doc.y)], fill=GOLD, width=2)
    doc.y += 16


def spec_table(doc: Doc, specs: List[List[str]]):
    f_lab = bold(12)
    f_val = reg(12)
    lab_w = int(CW * 0.34)
    val_w = CW - lab_w - 24
    lh = 18
    for idx, (label, value) in enumerate(specs):
        vlines = wrap(doc.d, value, f_val, val_w)
        row_h = max(lh + 10, lh * len(vlines) + 10)
        doc.ensure(row_h)
        if idx % 2 == 0:
            doc.d.rectangle([MARGIN, doc.y, PW - MARGIN, doc.y + row_h], fill=ROW_BG)
        doc.d.text((MARGIN + 10, doc.y + 5), label, font=f_lab, fill=GRAY)
        vy = doc.y + 5
        for ln in vlines:
            doc.d.text((MARGIN + lab_w + 10, vy), ln, font=f_val, fill=DARK)
            vy += lh
        doc.y += row_h
    doc.y += 14


def section(doc: Doc, sec: dict):
    # heading
    doc.ensure(40)
    doc.y += 6
    doc.d.text((MARGIN, doc.y), sec["heading"], font=bold(14), fill=NAVY)
    doc.y += 22
    doc.d.line([(MARGIN, doc.y), (MARGIN + 70, doc.y)], fill=GOLD, width=2)
    doc.y += 12
    if sec.get("subheading"):
        draw_para(doc, sec["subheading"], bold(12), GOLD, CW, 18, space_after=6)
    for p in sec.get("paragraphs", []):
        draw_para(doc, p, reg(11), DARK, CW, 17, space_after=8)
    for b in sec.get("bullets", []):
        draw_bullet(doc, b, reg(11), DARK, 17)
    if sec.get("bullets"):
        doc.y += 6


# --------------------------------------------------------------------------
# Image pages
# --------------------------------------------------------------------------

def _fit(img: Image.Image, w: int, h: int) -> Image.Image:
    ratio = max(w / img.width, h / img.height)
    img = img.resize((max(1, int(img.width * ratio)), max(1, int(img.height * ratio))), Image.LANCZOS)
    left = (img.width - w) // 2
    top = (img.height - h) // 2
    return img.crop((left, top, left + w, top + h))


def _framed(doc: Doc, img: Optional[Image.Image], x, y, w, h, tag=""):
    if img is not None:
        doc.img.paste(_fit(img, w, h), (x, y))
    else:
        doc.d.rectangle([x, y, x + w, y + h], fill=(245, 245, 245))
        doc.d.text((x + w // 2, y + h // 2), "нет фото", font=reg(18),
                   fill=(160, 160, 160), anchor="mm")
    doc.d.rectangle([x, y, x + w, y + h], outline=LINE, width=2)
    if tag:
        doc.d.text((x + 8, y + 8), tag, font=bold(15), fill="white",
                   stroke_width=2, stroke_fill=(0, 0, 0))


def map_photo_page(doc: Doc, map_img: Image.Image, photo: Optional[Image.Image]):
    doc.new_page()
    gap = 24
    avail = PH - 2 * MARGIN - gap
    top_h = int(avail * 0.46)
    bot_h = avail - top_h
    _framed(doc, map_img, MARGIN, MARGIN, CW, top_h, "Локация")
    _framed(doc, photo, MARGIN, MARGIN + top_h + gap, CW, bot_h, "Объект")


def gallery_pages(doc: Doc, photos: List[Image.Image]):
    gap = 24
    avail = PH - 2 * MARGIN - gap
    cell_h = avail // 2
    for i in range(0, len(photos), 2):
        doc.new_page()
        _framed(doc, photos[i], MARGIN, MARGIN, CW, cell_h)
        if i + 1 < len(photos):
            _framed(doc, photos[i + 1], MARGIN, MARGIN + cell_h + gap, CW, cell_h)


# --------------------------------------------------------------------------
# Orchestration
# --------------------------------------------------------------------------

def resolve_photos(obj: dict, base: str) -> List[Image.Image]:
    spec = obj.get("photos", [])
    paths: List[str] = []
    if isinstance(spec, str) and spec.startswith("glob:"):
        paths = sorted(globmod.glob(os.path.join(base, spec[5:])))
    elif isinstance(spec, list):
        paths = [p if os.path.isabs(p) else os.path.join(base, p) for p in spec]
    imgs = []
    for p in paths:
        try:
            imgs.append(Image.open(p).convert("RGB"))
        except Exception:
            # treat as URL
            im = load_photo(None, p)
            if im:
                imgs.append(im)
    return imgs


def build(content: dict, base: str) -> Doc:
    doc = Doc()
    cover(doc, content.get("title", "ОБЪЕКТЫ НЕДВИЖИМОСТИ"),
          content.get("subtitle", ""), content["objects"])

    for obj in content["objects"]:
        # ---- text page(s) ----
        doc.new_page()
        title_block(doc, obj)
        if obj.get("specs"):
            spec_table(doc, obj["specs"])
        for sec in obj.get("sections", []):
            section(doc, sec)

        # ---- coordinates + map ----
        lat, lon = obj.get("lat"), obj.get("lon")
        if lat is None or lon is None:
            lat, lon = geocode(obj["address"] + ", Netherlands")
        zoom = obj.get("map_zoom", 12)
        print(f"  map: {obj['address']} @ {lat:.4f},{lon:.4f} z{zoom}")
        map_img = osm_static_map(lat, lon, zoom, 1120, 560)

        # ---- photos ----
        photos = resolve_photos(obj, base)
        main = photos[0] if photos else None
        map_photo_page(doc, map_img, main)
        gallery_pages(doc, photos[1:])

    return doc


def main() -> int:
    ap = argparse.ArgumentParser(description="Render a real-estate report PDF from JSON.")
    ap.add_argument("content", help="Path to content JSON.")
    ap.add_argument("-o", "--out", default="report.pdf", help="Output PDF.")
    args = ap.parse_args()

    with open(args.content, encoding="utf-8") as fh:
        content = json.load(fh)
    base = os.path.dirname(os.path.abspath(args.content))

    doc = build(content, base)
    doc.save(args.out)
    print(f"\nSaved {args.out}  ({len(doc.pages)} pages)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
