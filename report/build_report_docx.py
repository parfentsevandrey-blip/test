#!/usr/bin/env python3
"""
build_report_docx.py — render the listing report as an EDITABLE Word (.docx).

Same JSON content model as the PDF renderers, but the output opens in Microsoft
Word / LibreOffice as fully editable text: real paragraphs, a real spec table,
real inline images. Headings are navy, rules/accents gold, the spec table has
alternating row shading — matching the reference report.

Maps use the REAL Google map (reverse-engineered keyless tiles from
mt{0-3}.google.com/vt — see funda_a4/make_sheet.py:google_tiles_map), with an
automatic fallback to OpenStreetMap. The full photo gallery is included.

Usage:
    python3 build_report_docx.py content_utrecht.json -o report.docx
"""

from __future__ import annotations

import argparse
import glob as globmod
import json
import os
import sys
import tempfile
from typing import List, Optional

from docx import Document
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.oxml import OxmlElement
from PIL import Image as PILImage

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "funda_a4"))
from make_sheet import google_tiles_map, osm_static_map, geocode  # noqa: E402

# ---- palette ----
NAVY = RGBColor(0x1F, 0x3B, 0x5F)
GOLD = RGBColor(0xB0, 0x8D, 0x57)
DARK = RGBColor(0x26, 0x26, 0x28)
GRAY = RGBColor(0x60, 0x60, 0x64)
GOLD_HEX = "B08D57"
ROW_HEX = "F4F4F7"
FONT = "Calibri"          # ships with Word; full Cyrillic coverage

_TMP = tempfile.mkdtemp(prefix="report_docx_")

# content box for A4 with 1.8cm margins
BOX_W = 17.4
MAP_H = 8.0
MAIN_PHOTO_H = 13.0
GALLERY_H = 10.6


# --------------------------------------------------------------------------
# low-level docx helpers
# --------------------------------------------------------------------------

def _shade(cell, hex_fill):
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:fill"), hex_fill)
    cell._tc.get_or_add_tcPr().append(shd)


def _bottom_border(paragraph, color=GOLD_HEX, sz=10, space=2):
    pPr = paragraph._p.get_or_add_pPr()
    pbdr = OxmlElement("w:pBdr")
    b = OxmlElement("w:bottom")
    b.set(qn("w:val"), "single"); b.set(qn("w:sz"), str(sz))
    b.set(qn("w:space"), str(space)); b.set(qn("w:color"), color)
    pbdr.append(b)
    pPr.append(pbdr)


def _char_spacing(run, twips):
    sp = OxmlElement("w:spacing")
    sp.set(qn("w:val"), str(twips))
    run._r.get_or_add_rPr().append(sp)


def _run(p, text, size, color, bold=False, italic=False):
    r = p.add_run(text)
    r.font.name = FONT
    r.font.size = Pt(size)
    r.font.color.rgb = color
    r.bold = bold
    r.italic = italic
    return r


def _para(doc, space_before=0, space_after=4, align=None):
    p = doc.add_paragraph()
    pf = p.paragraph_format
    pf.space_before = Pt(space_before)
    pf.space_after = Pt(space_after)
    pf.line_spacing = 1.06
    if align is not None:
        p.alignment = align
    return p


def _image(doc, path, max_w, max_h, center=True):
    w, h = PILImage.open(path).size
    tw = max_w
    th = tw * h / w
    if th > max_h:
        th = max_h
        tw = th * w / h
    p = _para(doc, space_after=2)
    if center:
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.add_run().add_picture(path, width=Cm(tw), height=Cm(th))
    return p


# --------------------------------------------------------------------------
# content sections
# --------------------------------------------------------------------------

def resolve_photos(obj, base) -> List[str]:
    spec = obj.get("photos", [])
    if isinstance(spec, str) and spec.startswith("glob:"):
        paths = sorted(globmod.glob(os.path.join(base, spec[5:])))
    elif isinstance(spec, list):
        paths = [p if os.path.isabs(p) else os.path.join(base, p) for p in spec]
    else:
        paths = []
    return [p for p in paths if os.path.exists(p)]


def cover(doc, content):
    for _ in range(8):
        _para(doc, space_after=0)
    top = _para(doc, space_after=0); _bottom_border(top, GOLD_HEX, sz=12)
    t = _para(doc, space_before=10, space_after=2, align=WD_ALIGN_PARAGRAPH.CENTER)
    r = _run(t, content.get("title", "ОБЪЕКТЫ НЕДВИЖИМОСТИ"), 26, NAVY, bold=True)
    _char_spacing(r, 60)
    s = _para(doc, space_after=8, align=WD_ALIGN_PARAGRAPH.CENTER)
    _run(s, content.get("subtitle", ""), 14, GRAY, italic=True)
    _bottom_border(s, GOLD_HEX, sz=12)

    h = _para(doc, space_before=18, space_after=8, align=WD_ALIGN_PARAGRAPH.CENTER)
    _run(h, "В подборку входят:", 11, GOLD)
    for i, it in enumerate(content["objects"], 1):
        addr = it["address"].split(",")[0]
        city = it["address"].split(",")[-1].strip()
        line = _para(doc, space_after=3, align=WD_ALIGN_PARAGRAPH.CENTER)
        _run(line, f"{i}. ", 12, GOLD, bold=True)
        _run(line, f"{addr}, ", 12, NAVY, bold=True)
        _run(line, f"{city} — {it.get('price_label','')}", 12, DARK)


def spec_table(doc, specs):
    table = doc.add_table(rows=len(specs), cols=2)
    table.autofit = False
    for i, (label, value) in enumerate(specs):
        c0, c1 = table.rows[i].cells
        c0.width = Cm(5.8); c1.width = Cm(BOX_W - 5.8)
        p0 = c0.paragraphs[0]; p0.paragraph_format.space_after = Pt(1)
        _run(p0, label, 9, GRAY, bold=True)
        p1 = c1.paragraphs[0]; p1.paragraph_format.space_after = Pt(1)
        _run(p1, value, 9, DARK)
        if i % 2 == 0:
            _shade(c0, ROW_HEX); _shade(c1, ROW_HEX)


def section(doc, sec):
    h = _para(doc, space_before=8, space_after=2)
    _run(h, sec["heading"], 11, NAVY, bold=True)
    _bottom_border(h, GOLD_HEX, sz=8)
    if sec.get("subheading"):
        sp = _para(doc, space_after=3)
        _run(sp, sec["subheading"], 9.5, GOLD, bold=True)
    for para in sec.get("paragraphs", []):
        p = _para(doc, space_after=5, align=WD_ALIGN_PARAGRAPH.JUSTIFY)
        _run(p, para, 9, DARK)
    for b in sec.get("bullets", []):
        p = _para(doc, space_after=1.5)
        p.paragraph_format.left_indent = Cm(0.5)
        _run(p, "•  ", 9, GOLD, bold=True)
        _run(p, b, 9, DARK)


def caption(doc, text):
    p = _para(doc, space_before=2, space_after=2)
    _run(p, text, 9.5, NAVY, bold=True)


# --------------------------------------------------------------------------
# build
# --------------------------------------------------------------------------

def make_map(obj, idx):
    lat, lon = obj.get("lat"), obj.get("lon")
    if lat is None or lon is None:
        lat, lon = geocode(obj["address"] + ", Netherlands")
    zoom = obj.get("map_zoom", 13)
    try:
        img = google_tiles_map(lat, lon, zoom, 1280, 600)
        print(f"  map(google): {obj['address']} z{zoom}")
    except Exception as e:
        print(f"  ! google map failed ({e}); OSM fallback")
        img = osm_static_map(lat, lon, zoom, 1280, 600)
    path = os.path.join(_TMP, f"map_{idx}.jpg")
    img.save(path, "JPEG", quality=90)
    return path


def build(content, base, out):
    doc = Document()
    sec = doc.sections[0]
    sec.page_height = Cm(29.7); sec.page_width = Cm(21.0)
    for m in ("top_margin", "bottom_margin", "left_margin", "right_margin"):
        setattr(sec, m, Cm(1.8))
    style = doc.styles["Normal"]
    style.font.name = FONT
    style.font.size = Pt(9)

    cover(doc, content)

    for idx, obj in enumerate(content["objects"]):
        doc.add_page_break()
        # title
        t = _para(doc, space_after=1)
        _run(t, obj["address"], 16, NAVY, bold=True)
        d = _para(doc, space_after=3)
        _run(d, obj.get("district", ""), 9, GOLD)
        _bottom_border(d, GOLD_HEX, sz=12)
        # specs
        if obj.get("specs"):
            spec_table(doc, obj["specs"])
            _para(doc, space_after=2)
        # sections
        for s in obj.get("sections", []):
            section(doc, s)

        # map + main photo
        photos = resolve_photos(obj, base)
        doc.add_page_break()
        caption(doc, "Локация")
        _image(doc, make_map(obj, idx), BOX_W, MAP_H)
        if photos:
            caption(doc, "Объект")
            _image(doc, photos[0], BOX_W, MAIN_PHOTO_H)

        # gallery — full set, 2 per page
        rest = photos[1:]
        for i in range(0, len(rest), 2):
            doc.add_page_break()
            _image(doc, rest[i], BOX_W, GALLERY_H)
            if i + 1 < len(rest):
                _image(doc, rest[i + 1], BOX_W, GALLERY_H)

    doc.save(out)
    print(f"\nSaved {out}")


def main() -> int:
    ap = argparse.ArgumentParser(description="Render the listing report as an editable Word .docx.")
    ap.add_argument("content")
    ap.add_argument("-o", "--out", default="report.docx")
    args = ap.parse_args()
    with open(args.content, encoding="utf-8") as fh:
        content = json.load(fh)
    base = os.path.dirname(os.path.abspath(args.content))
    build(content, base, args.out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
