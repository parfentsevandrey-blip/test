#!/usr/bin/env python3
"""
build_report_pdf.py — VECTOR PDF renderer (ReportLab) for the listing report.

Shares the JSON content model and the advanced layout ideas of
build_report_docx.py: real keyless Google maps, vertically-justified image
packing (full pages, no big bottom gap), big photos, and a per-object page
budget. Text is selectable/searchable; only maps and photos are raster.

This renderer also doubles as a faithful visual preview of the Word output.

Usage:
    python3 build_report_pdf.py content_utrecht.json -o report.pdf --max-pages 7
"""

from __future__ import annotations

import argparse
import glob as globmod
import json
import math
import os
import sys
import tempfile
from typing import List

from reportlab.lib.pagesizes import A4
from reportlab.lib.units import cm
from reportlab.lib.enums import TA_JUSTIFY
from reportlab.lib.colors import Color
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate, PageTemplate, Frame, Paragraph, Spacer, Table, TableStyle,
    Image, PageBreak, KeepTogether, Flowable, NextPageTemplate,
)
from reportlab.lib.styles import ParagraphStyle
from PIL import Image as PILImage

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "funda_a4"))
from make_sheet import google_tiles_map, osm_static_map, geocode  # noqa: E402

# ---- palette ----
NAVY = Color(0x1B / 255, 0x33 / 255, 0x57 / 255)
GOLD = Color(0xB0 / 255, 0x8D / 255, 0x57 / 255)
DARK = Color(0x2B / 255, 0x2B / 255, 0x2E / 255)
GRAY = Color(0x5C / 255, 0x5C / 255, 0x62 / 255)
ROW_BG = Color(0xF3 / 255, 0xF1 / 255, 0xEC / 255)
LINE = Color(0xCF / 255, 0xC9 / 255, 0xBE / 255)

FD = "/usr/share/fonts/truetype/dejavu"
pdfmetrics.registerFont(TTFont("DJ", f"{FD}/DejaVuSans.ttf"))
pdfmetrics.registerFont(TTFont("DJ-B", f"{FD}/DejaVuSans-Bold.ttf"))
pdfmetrics.registerFont(TTFont("DJ-S", f"{FD}/DejaVuSerif.ttf"))
pdfmetrics.registerFont(TTFont("DJ-SB", f"{FD}/DejaVuSerif-Bold.ttf"))

PAGE_W, PAGE_H = A4
ML = 1.6 * cm
MTB = 1.4 * cm
CONTENT_W = PAGE_W - 2 * ML
USABLE_H = PAGE_H - 2 * MTB - 14   # minus a little for footer
_TMP = tempfile.mkdtemp(prefix="rep_pdf_")

S = {
    "title":    ParagraphStyle("t", fontName="DJ-SB", fontSize=16.5, leading=20, textColor=NAVY, spaceAfter=1),
    "district": ParagraphStyle("d", fontName="DJ-B", fontSize=9.5, leading=12, textColor=GOLD, spaceAfter=3),
    "sechead":  ParagraphStyle("sh", fontName="DJ-B", fontSize=10.5, leading=13, textColor=NAVY, spaceBefore=8, spaceAfter=2),
    "subhead":  ParagraphStyle("sub", fontName="DJ-B", fontSize=9.3, leading=12, textColor=GOLD, spaceAfter=3),
    "body":     ParagraphStyle("b", fontName="DJ", fontSize=9, leading=12.6, textColor=DARK, alignment=TA_JUSTIFY, spaceAfter=4),
    "bullet":   ParagraphStyle("bl", fontName="DJ", fontSize=9, leading=12.2, textColor=DARK, leftIndent=14, bulletIndent=3, spaceAfter=1.5),
    "lab":      ParagraphStyle("l", fontName="DJ-B", fontSize=8.7, leading=11, textColor=GRAY),
    "val":      ParagraphStyle("v", fontName="DJ", fontSize=8.7, leading=11, textColor=DARK),
    "cap":      ParagraphStyle("c", fontName="DJ-B", fontSize=9.5, leading=12, textColor=GOLD, spaceAfter=3),
}


class GoldRule(Flowable):
    def __init__(self, width, thickness=1.1, color=GOLD, sa=6):
        super().__init__(); self._w, self._t, self._c, self.sa = width, thickness, color, sa

    def wrap(self, aw, ah):
        return (self._w, self._t + self.sa)

    def draw(self):
        self.canv.setStrokeColor(self._c); self.canv.setLineWidth(self._t)
        self.canv.line(0, self.sa, self._w, self.sa)


# ---- images ----
def disp_size(path):
    """Display size in points at full content width, capped to ~one page."""
    w, h = PILImage.open(path).size
    dw, dh = CONTENT_W, CONTENT_W * h / w
    cap = USABLE_H * 0.94
    if dh > cap:
        dh = cap; dw = dh * w / h
    return dw, dh


def pack_pages(paths, usable_h, gap_min=14):
    pages, cur, cur_h = [], [], 0.0
    for p in paths:
        dw, dh = disp_size(p)
        add = dh + (gap_min if cur else 0)
        if cur and cur_h + add > usable_h:
            pages.append(cur); cur, cur_h = [], 0.0; add = dh
        cur.append((p, dw, dh)); cur_h += add
    if cur:
        pages.append(cur)
    return pages


def fit_to_budget(paths, budget, usable_h):
    if budget <= 0:
        return []
    chosen = paths
    while len(pack_pages(chosen, usable_h)) > budget and len(chosen) > 1:
        n = len(chosen)
        over = len(pack_pages(chosen, usable_h)) - budget
        k = max(1, n - over)
        idx = sorted({0} | {round(i * (n - 1) / (k - 1)) for i in range(k)}) if k > 1 else [0]
        chosen = [chosen[i] for i in idx]
        if k == n:
            break
    return chosen


def framed(path, dw, dh):
    im = Image(path, width=dw, height=dh); im.hAlign = "CENTER"
    box = Table([[im]], colWidths=[dw], rowHeights=[dh])
    box.setStyle(TableStyle([("BOX", (0, 0), (-1, -1), 0.7, LINE),
                             ("LEFTPADDING", (0, 0), (-1, -1), 0), ("RIGHTPADDING", (0, 0), (-1, -1), 0),
                             ("TOPPADDING", (0, 0), (-1, -1), 0), ("BOTTOMPADDING", (0, 0), (-1, -1), 0)]))
    box.hAlign = "CENTER"
    return box


IMG_GAP = 10


def image_block(path, caption=None):
    """A caption+image kept together so the image never splits across pages."""
    dw, dh = disp_size(path)
    parts = []
    if caption:
        parts.append(Paragraph(caption.upper(), S["cap"]))
        parts.append(Spacer(0, 1))
    parts.append(framed(path, dw, dh))
    return KeepTogether(parts), dh


def budget_photos(text_h, map_path, main, gallery, max_pages):
    """Trim the gallery (evenly) so the object's continuous flow fits max_pages."""
    base_h = text_h + disp_size(map_path)[1] + 2 * IMG_GAP
    if main:
        base_h += disp_size(main)[1] + IMG_GAP
    cap_h = (max_pages - 0.15) * USABLE_H
    chosen = list(gallery)
    while chosen:
        h = base_h + sum(disp_size(p)[1] + IMG_GAP for p in chosen)
        if h <= cap_h:
            break
        n = len(chosen)
        over_pages = (h - cap_h) / USABLE_H
        # drop a few, evenly
        k = max(1, n - max(1, math.ceil(over_pages * 2)))
        if k >= n:
            k = n - 1
        idx = sorted({round(i * (n - 1) / (k - 1)) for i in range(k)}) if k > 1 else [0]
        chosen = [chosen[i] for i in idx]
    return chosen


# ---- text height estimate (budget) ----
def est_lines(t, cpl):
    return max(1, math.ceil(len(t) / cpl))


def estimate_text_height(obj):
    h = 1.7 * cm
    for _, v in obj.get("specs", []):
        h += (0.42 + 0.40 * (est_lines(v, 80) - 1)) * cm
    for sec in obj.get("sections", []):
        h += 0.8 * cm
        if sec.get("subheading"):
            h += 0.5 * cm
        for p in sec.get("paragraphs", []):
            h += est_lines(p, 104) * 0.45 * cm
        for b in sec.get("bullets", []):
            h += est_lines(b, 96) * 0.44 * cm
    return h


def resolve_photos(obj, base):
    spec = obj.get("photos", [])
    if isinstance(spec, str) and spec.startswith("glob:"):
        paths = sorted(globmod.glob(os.path.join(base, spec[5:])))
    elif isinstance(spec, list):
        paths = [p if os.path.isabs(p) else os.path.join(base, p) for p in spec]
    else:
        paths = []
    return [p for p in paths if os.path.exists(p)]


def spec_table(specs):
    lab_w = CONTENT_W * 0.32
    rows = [[Paragraph(l, S["lab"]), Paragraph(v, S["val"])] for l, v in specs]
    t = Table(rows, colWidths=[lab_w, CONTENT_W - lab_w])
    style = [("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
             ("LEFTPADDING", (0, 0), (-1, -1), 8), ("RIGHTPADDING", (0, 0), (-1, -1), 8),
             ("TOPPADDING", (0, 0), (-1, -1), 3.5), ("BOTTOMPADDING", (0, 0), (-1, -1), 3.5),
             ("LINEBELOW", (0, 0), (-1, -1), 0.4, LINE)]
    for i in range(len(rows)):
        if i % 2 == 0:
            style.append(("BACKGROUND", (0, i), (-1, i), ROW_BG))
    t.setStyle(TableStyle(style))
    return t


def section_flow(sec):
    out = [Paragraph(sec["heading"], S["sechead"]), GoldRule(70, sa=4)]
    if sec.get("subheading"):
        out.append(Paragraph(sec["subheading"], S["subhead"]))
    for p in sec.get("paragraphs", []):
        out.append(Paragraph(p, S["body"]))
    for b in sec.get("bullets", []):
        out.append(Paragraph(b, S["bullet"], bulletText="•"))
    out.append(Spacer(0, 2))
    return out


def make_map(obj, idx):
    lat, lon = obj.get("lat"), obj.get("lon")
    if lat is None or lon is None:
        lat, lon = geocode(obj["address"] + ", Netherlands")
    z = obj.get("map_zoom", 13)
    try:
        img = google_tiles_map(lat, lon, z, 1400, 600); print(f"  map(google): {obj['address']} z{z}")
    except Exception as e:
        print(f"  ! google failed ({e}); OSM"); img = osm_static_map(lat, lon, z, 1400, 600)
    path = os.path.join(_TMP, f"m{idx}.jpg"); img.save(path, "JPEG", quality=90)
    return path


def make_cover(title, subtitle, items, footer):
    def draw(canv, doc):
        canv.saveState()
        cy = PAGE_H * 0.64
        spaced = " ".join(list(title)); size = 26
        while size > 10 and pdfmetrics.stringWidth(spaced, "DJ-SB", size) > CONTENT_W:
            size -= 1
        canv.setStrokeColor(GOLD); canv.setLineWidth(1.3)
        canv.line(ML, cy + 26, PAGE_W - ML, cy + 26)
        canv.setFillColor(NAVY); canv.setFont("DJ-SB", size)
        canv.drawCentredString(PAGE_W / 2, cy, spaced)
        canv.setFillColor(GRAY); canv.setFont("DJ-S", 14)
        canv.drawCentredString(PAGE_W / 2, cy - 24, subtitle)
        canv.setStrokeColor(GOLD); canv.line(ML, cy - 38, PAGE_W - ML, cy - 38)
        canv.setFillColor(GOLD); canv.setFont("DJ-B", 11)
        canv.drawCentredString(PAGE_W / 2, cy - 86, "В  П О Д Б О Р К У   В Х О Д Я Т")
        ly = cy - 116
        for i, it in enumerate(items, 1):
            addr = it["address"].split(",")[0]; city = it["address"].split(",")[-1].strip()
            seg = [(f"{i}.  ", "DJ-SB", 13, GOLD), (addr, "DJ-SB", 13, NAVY),
                   (f"   {city} — {it.get('price_label','')}", "DJ-S", 13, DARK)]
            tot = sum(pdfmetrics.stringWidth(t, f, s) for t, f, s, _ in seg)
            x = (PAGE_W - tot) / 2
            for t, f, s, c in seg:
                canv.setFillColor(c); canv.setFont(f, s); canv.drawString(x, ly, t)
                x += pdfmetrics.stringWidth(t, f, s)
            ly -= 22
        canv.restoreState()
    return draw


def footer_painter(footer_text):
    def paint(canv, doc):
        canv.saveState()
        canv.setFont("DJ-S", 8); canv.setFillColor(GRAY)
        canv.drawCentredString(PAGE_W / 2, MTB * 0.5, footer_text)
        canv.restoreState()
    return paint


def build(content, base, out, max_pages):
    footer = f"{content.get('title','')}  ·  {content.get('subtitle','')}"
    doc = BaseDocTemplate(out, pagesize=A4, leftMargin=ML, rightMargin=ML,
                          topMargin=MTB, bottomMargin=MTB, title=content.get("title", ""))
    frame = Frame(ML, MTB, CONTENT_W, PAGE_H - 2 * MTB, id="m",
                  leftPadding=0, rightPadding=0, topPadding=0, bottomPadding=0)
    doc.addPageTemplates([
        PageTemplate(id="cover", frames=[frame],
                     onPage=make_cover(content.get("title", "ОБЪЕКТЫ НЕДВИЖИМОСТИ"),
                                       content.get("subtitle", ""), content["objects"], footer)),
        PageTemplate(id="body", frames=[frame], onPage=footer_painter(footer)),
    ])

    story = [NextPageTemplate("body"), PageBreak()]
    for idx, obj in enumerate(content["objects"]):
        story.append(Paragraph(obj["address"], S["title"]))
        story.append(Paragraph(obj.get("district", ""), S["district"]))
        story.append(GoldRule(CONTENT_W, thickness=1.2, sa=6))
        if obj.get("specs"):
            story.append(spec_table(obj["specs"])); story.append(Spacer(0, 4))
        for sec in obj.get("sections", []):
            f = section_flow(sec)
            story.append(KeepTogether(f[:3]))
            story += f[3:]

        # ---- images flow continuously right after the text (fills page bottoms) ----
        photos = resolve_photos(obj, base)
        text_h = estimate_text_height(obj)
        mp = make_map(obj, idx)
        main = photos[0] if photos else None
        gallery = budget_photos(text_h, mp, main, photos[1:], max_pages)

        story.append(Spacer(0, 8))
        blk, _ = image_block(mp, "Локация"); story.append(blk)
        if main:
            story.append(Spacer(0, IMG_GAP)); blk, _ = image_block(main, "Объект"); story.append(blk)
        for g in gallery:
            story.append(Spacer(0, IMG_GAP)); blk, _ = image_block(g); story.append(blk)

        if obj is not content["objects"][-1]:
            story.append(PageBreak())

    doc.build(story)
    print(f"\nSaved {out}")


def main():
    ap = argparse.ArgumentParser(description="Render the listing report as a vector PDF.")
    ap.add_argument("content"); ap.add_argument("-o", "--out", default="report.pdf")
    ap.add_argument("--max-pages", type=int, default=7)
    args = ap.parse_args()
    with open(args.content, encoding="utf-8") as fh:
        content = json.load(fh)
    build(content, os.path.dirname(os.path.abspath(args.content)), args.out, args.max_pages)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
