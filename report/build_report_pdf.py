#!/usr/bin/env python3
"""
build_report_pdf.py — VECTOR PDF renderer (ReportLab) for the listing report.

Same JSON content model as build_report.py, but the output is a true vector PDF:
text is selectable/searchable and crisp at any zoom. Only the maps and listing
photos are raster (they are inherently bitmaps); everything else — headings,
rules, tables, body copy — is vector.

Tuned to match the reference report: navy headings, gold rules, gray two-column
spec table, cover with letter-spaced title between two gold rules.

Usage:
    python3 build_report_pdf.py content_example.json -o report.pdf
"""

from __future__ import annotations

import argparse
import glob as globmod
import json
import os
import sys
import tempfile
from typing import List, Optional

from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.lib.enums import TA_CENTER, TA_JUSTIFY
from reportlab.lib.colors import Color
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate, PageTemplate, Frame, Paragraph, Spacer, Table, TableStyle,
    Image, PageBreak, KeepTogether, Flowable,
)
from reportlab.lib.styles import ParagraphStyle

# Reuse the keyless OSM map + geocoder.
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "funda_a4"))
from make_sheet import osm_static_map, geocode, load_photo  # noqa: E402
from PIL import Image as PILImage  # noqa: E402

# --------------------------------------------------------------------------
# Palette & fonts
# --------------------------------------------------------------------------

NAVY = Color(31 / 255, 59 / 255, 95 / 255)
GOLD = Color(176 / 255, 141 / 255, 87 / 255)
DARK = Color(38 / 255, 38 / 255, 40 / 255)
GRAY = Color(96 / 255, 96 / 255, 100 / 255)
ROW_BG = Color(244 / 255, 244 / 255, 247 / 255)
LINE = Color(208 / 255, 208 / 255, 214 / 255)

FONT_DIR = "/usr/share/fonts/truetype/dejavu"
pdfmetrics.registerFont(TTFont("DJ", f"{FONT_DIR}/DejaVuSans.ttf"))
pdfmetrics.registerFont(TTFont("DJ-B", f"{FONT_DIR}/DejaVuSans-Bold.ttf"))
pdfmetrics.registerFont(TTFont("DJ-I", f"{FONT_DIR}/DejaVuSerif.ttf"))

PAGE_W, PAGE_H = A4
MARGIN = 18 * mm
CONTENT_W = PAGE_W - 2 * MARGIN

# --------------------------------------------------------------------------
# Paragraph styles
# --------------------------------------------------------------------------

S = {
    "title":   ParagraphStyle("title", fontName="DJ-B", fontSize=17, leading=21, textColor=NAVY, spaceAfter=2),
    "district": ParagraphStyle("district", fontName="DJ", fontSize=9, leading=12, textColor=GOLD, spaceAfter=2),
    "sechead": ParagraphStyle("sechead", fontName="DJ-B", fontSize=10.5, leading=13, textColor=NAVY,
                              spaceBefore=8, spaceAfter=2),
    "subhead": ParagraphStyle("subhead", fontName="DJ-B", fontSize=9, leading=12, textColor=GOLD, spaceAfter=3),
    "body":    ParagraphStyle("body", fontName="DJ", fontSize=8.7, leading=12.4, textColor=DARK,
                              alignment=TA_JUSTIFY, spaceAfter=4),
    "bullet":  ParagraphStyle("bullet", fontName="DJ", fontSize=8.7, leading=12.2, textColor=DARK,
                              leftIndent=12, bulletIndent=2, spaceAfter=1.5),
    "lab":     ParagraphStyle("lab", fontName="DJ-B", fontSize=8.6, leading=11, textColor=GRAY),
    "val":     ParagraphStyle("val", fontName="DJ", fontSize=8.6, leading=11, textColor=DARK),
    "caption": ParagraphStyle("caption", fontName="DJ-B", fontSize=9, leading=11, textColor=NAVY, spaceAfter=3),
}


# --------------------------------------------------------------------------
# Custom flowables
# --------------------------------------------------------------------------

class GoldRule(Flowable):
    """A horizontal gold rule of given width fraction."""
    def __init__(self, width, thickness=1.1, color=GOLD, space_before=2, space_after=6):
        super().__init__()
        self._w, self._t, self._c = width, thickness, color
        self.space_before, self.space_after = space_before, space_after

    def wrap(self, aw, ah):
        return (self._w, self._t + self.space_before + self.space_after)

    def draw(self):
        self.canv.setStrokeColor(self._c)
        self.canv.setLineWidth(self._t)
        y = self.space_after
        self.canv.line(0, y, self._w, y)


# --------------------------------------------------------------------------
# Image helpers
# --------------------------------------------------------------------------

_TMP = tempfile.mkdtemp(prefix="report_pdf_")


def _save_fit(img: PILImage.Image, w_px: int, h_px: int, name: str) -> str:
    ratio = max(w_px / img.width, h_px / img.height)
    img = img.resize((max(1, int(img.width * ratio)), max(1, int(img.height * ratio))), PILImage.LANCZOS)
    left = (img.width - w_px) // 2
    top = (img.height - h_px) // 2
    img = img.crop((left, top, left + w_px, top + h_px))
    path = os.path.join(_TMP, name)
    img.convert("RGB").save(path, "JPEG", quality=88)
    return path


def framed_image(pil: Optional[PILImage.Image], w_pt: float, h_pt: float, tag: str, idx: str):
    """Return [caption Paragraph, Image] sized to w_pt x h_pt with a thin border."""
    w_px, h_px = int(w_pt / 72 * 150), int(h_pt / 72 * 150)
    if pil is None:
        # placeholder
        ph = PILImage.new("RGB", (w_px, h_px), (245, 245, 245))
        pil = ph
    path = _save_fit(pil, w_px, h_px, f"img_{idx}.jpg")
    im = Image(path, width=w_pt, height=h_pt)
    im.hAlign = "LEFT"
    cap = Paragraph(tag, S["caption"]) if tag else Spacer(0, 0)
    box = Table([[im]], colWidths=[w_pt], rowHeights=[h_pt])
    box.setStyle(TableStyle([
        ("BOX", (0, 0), (-1, -1), 0.7, LINE),
        ("LEFTPADDING", (0, 0), (-1, -1), 0),
        ("RIGHTPADDING", (0, 0), (-1, -1), 0),
        ("TOPPADDING", (0, 0), (-1, -1), 0),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 0),
    ]))
    return [cap, box]


# --------------------------------------------------------------------------
# Content builders
# --------------------------------------------------------------------------

def spec_table(specs: List[List[str]]):
    lab_w = CONTENT_W * 0.33
    val_w = CONTENT_W - lab_w
    rows = [[Paragraph(l, S["lab"]), Paragraph(v, S["val"])] for l, v in specs]
    t = Table(rows, colWidths=[lab_w, val_w])
    style = [
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("LEFTPADDING", (0, 0), (-1, -1), 7),
        ("RIGHTPADDING", (0, 0), (-1, -1), 7),
        ("TOPPADDING", (0, 0), (-1, -1), 3.5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 3.5),
        ("LINEBELOW", (0, 0), (-1, -1), 0.4, LINE),
    ]
    for i in range(len(rows)):
        if i % 2 == 0:
            style.append(("BACKGROUND", (0, i), (-1, i), ROW_BG))
    t.setStyle(TableStyle(style))
    return t


def section_flow(sec: dict) -> List:
    out: List = [Paragraph(sec["heading"], S["sechead"]), GoldRule(70, space_after=4)]
    if sec.get("subheading"):
        out.append(Paragraph(sec["subheading"], S["subhead"]))
    for p in sec.get("paragraphs", []):
        out.append(Paragraph(p, S["body"]))
    for b in sec.get("bullets", []):
        out.append(Paragraph(b, S["bullet"], bulletText="•"))
    out.append(Spacer(0, 2))
    return out


def resolve_photos(obj: dict, base: str) -> List[PILImage.Image]:
    spec = obj.get("photos", [])
    paths: List[str] = []
    if isinstance(spec, str) and spec.startswith("glob:"):
        paths = sorted(globmod.glob(os.path.join(base, spec[5:])))
    elif isinstance(spec, list):
        paths = [p if os.path.isabs(p) else os.path.join(base, p) for p in spec]
    imgs = []
    for p in paths:
        try:
            imgs.append(PILImage.open(p).convert("RGB"))
        except Exception:
            im = load_photo(None, p)
            if im:
                imgs.append(im)
    return imgs


# --------------------------------------------------------------------------
# Cover (drawn on the first page via canvas)
# --------------------------------------------------------------------------

def make_cover(title: str, subtitle: str, items: List[dict]):
    def draw(canv, doc):
        canv.saveState()
        cy = PAGE_H * 0.66
        # letter-spaced, auto-fit title
        spaced = " ".join(list(title))
        size = 26
        while size > 10 and pdfmetrics.stringWidth(spaced, "DJ-B", size) > CONTENT_W:
            size -= 1
        canv.setStrokeColor(GOLD)
        canv.setLineWidth(1.2)
        canv.line(MARGIN, cy + 22, PAGE_W - MARGIN, cy + 22)
        canv.setFillColor(NAVY)
        canv.setFont("DJ-B", size)
        canv.drawCentredString(PAGE_W / 2, cy, spaced)
        canv.setFillColor(GRAY)
        canv.setFont("DJ-I", 14)
        canv.drawCentredString(PAGE_W / 2, cy - 22, subtitle)
        canv.setStrokeColor(GOLD)
        canv.line(MARGIN, cy - 34, PAGE_W - MARGIN, cy - 34)

        canv.setFillColor(GOLD)
        canv.setFont("DJ", 10.5)
        ly = cy - 78
        canv.drawCentredString(PAGE_W / 2, ly, "В подборку входят:")
        ly -= 28
        for i, it in enumerate(items, 1):
            addr = it["address"].split(",")[0]
            city = it["address"].split(",")[-1].strip()
            price = it.get("price_label", "")
            num = f"{i}. "
            seg = [(num, "DJ-B", 11.5, GOLD), (f"{addr}, ", "DJ-B", 11.5, NAVY),
                   (f"{city} — {price}", "DJ", 11.5, DARK)]
            total = sum(pdfmetrics.stringWidth(t, f, s) for t, f, s, _ in seg)
            x = (PAGE_W - total) / 2
            for t, f, s, c in seg:
                canv.setFillColor(c)
                canv.setFont(f, s)
                canv.drawString(x, ly, t)
                x += pdfmetrics.stringWidth(t, f, s)
            ly -= 20
        canv.restoreState()
    return draw


# --------------------------------------------------------------------------
# Build
# --------------------------------------------------------------------------

def build(content: dict, base: str, out: str):
    doc = BaseDocTemplate(out, pagesize=A4,
                          leftMargin=MARGIN, rightMargin=MARGIN,
                          topMargin=MARGIN, bottomMargin=MARGIN,
                          title=content.get("title", "Отчёт"))
    frame = Frame(MARGIN, MARGIN, CONTENT_W, PAGE_H - 2 * MARGIN, id="main",
                  leftPadding=0, rightPadding=0, topPadding=0, bottomPadding=0)
    cover_draw = make_cover(content.get("title", "ОБЪЕКТЫ НЕДВИЖИМОСТИ"),
                            content.get("subtitle", ""), content["objects"])
    doc.addPageTemplates([
        PageTemplate(id="cover", frames=[frame], onPage=cover_draw),
        PageTemplate(id="body", frames=[frame]),
    ])

    story: List = [PageBreak()]  # leave page 1 for the cover canvas, switch to body
    from reportlab.platypus.doctemplate import NextPageTemplate
    story = [NextPageTemplate("body"), PageBreak()]

    avail_h = PAGE_H - 2 * MARGIN
    gap = 6 * mm

    for obj in content["objects"]:
        # ---- text page(s) ----
        story.append(Paragraph(obj["address"], S["title"]))
        story.append(Paragraph(obj.get("district", ""), S["district"]))
        story.append(GoldRule(CONTENT_W, thickness=1.2, space_after=6))
        if obj.get("specs"):
            story.append(spec_table(obj["specs"]))
            story.append(Spacer(0, 4))
        for sec in obj.get("sections", []):
            flow = section_flow(sec)
            # keep heading with its first lines
            story.append(KeepTogether(flow[:3]))
            for f in flow[3:]:
                story.append(f)

        # ---- map ----
        lat, lon = obj.get("lat"), obj.get("lon")
        if lat is None or lon is None:
            lat, lon = geocode(obj["address"] + ", Netherlands")
        zoom = obj.get("map_zoom", 12)
        print(f"  map: {obj['address']} @ {lat:.4f},{lon:.4f} z{zoom}")
        map_pil = osm_static_map(lat, lon, zoom, 1200, 620)

        photos = resolve_photos(obj, base)
        main = photos[0] if photos else None

        # ---- map + main photo page ----
        story.append(PageBreak())
        # reserve room for the two captions + gap so both images stay on one page
        imgs_h = avail_h - gap - 42
        top_h = imgs_h * 0.45
        bot_h = imgs_h * 0.55
        cap_m, box_m = framed_image(map_pil, CONTENT_W, top_h, "Локация", f"{id(obj)}_map")
        cap_p, box_p = framed_image(main, CONTENT_W, bot_h, "Объект", f"{id(obj)}_main")
        story += [cap_m, box_m, Spacer(0, gap), cap_p, box_p]

        # ---- gallery (2 per page) ----
        rest = photos[1:]
        for i in range(0, len(rest), 2):
            story.append(PageBreak())
            cell_h = (avail_h - gap - 6) / 2
            c1, b1 = framed_image(rest[i], CONTENT_W, cell_h, "", f"{id(obj)}_g{i}")
            story += [b1, Spacer(0, gap)]
            if i + 1 < len(rest):
                c2, b2 = framed_image(rest[i + 1], CONTENT_W, cell_h, "", f"{id(obj)}_g{i+1}")
                story += [b2]

        if obj is not content["objects"][-1]:
            story.append(PageBreak())

    doc.build(story)
    print(f"\nSaved {out}")


def main() -> int:
    ap = argparse.ArgumentParser(description="Render the listing report as a vector PDF (ReportLab).")
    ap.add_argument("content")
    ap.add_argument("-o", "--out", default="report_vector.pdf")
    args = ap.parse_args()
    with open(args.content, encoding="utf-8") as fh:
        content = json.load(fh)
    base = os.path.dirname(os.path.abspath(args.content))
    build(content, base, args.out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
