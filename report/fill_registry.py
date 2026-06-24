#!/usr/bin/env python3
"""
fill_registry.py — enrich a commercial-registry .docx:

1) Object photo pages: after each "Вставьте фотографии объекта ниже" anchor,
   insert the Google map (map_i.jpg) + main Funda photo (photo_i.jpg); the
   instruction line is removed. Images only, no captions.

2) Zone pages (Part 3): after each "Объекты реестра в этой зоне" anchor, add a
   fresh page with the zone's Google location map (zmap_k.jpg) + an aerial photo
   of the zone (zaer_k.jpg).

Everything else in the document is left untouched.
"""
import sys, os
from docx import Document
from docx.shared import Cm, Pt
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.oxml import OxmlElement
from docx.text.paragraph import Paragraph
from PIL import Image

SRC, OUT, ASSETS = sys.argv[1], sys.argv[2], sys.argv[3]
MAX_W = 17.4
PH_ANCHOR = "Вставьте фотографии объекта ниже"
ZONE_ANCHOR = "Объекты реестра в этой зоне"


def insert_after(par):
    new_p = OxmlElement("w:p")
    par._p.addnext(new_p)
    return Paragraph(new_p, par._parent)


def set_image(p, path, max_w, max_h, space_after=6):
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(space_after)
    p.paragraph_format.keep_together = True
    w, h = Image.open(path).size
    tw = max_w; th = tw * h / w
    if th > max_h:
        th = max_h; tw = th * w / h
    p.add_run().add_picture(path, width=Cm(tw), height=Cm(th))
    return p


def page_break(p):
    r = p.add_run()
    br = OxmlElement("w:br"); br.set(qn("w:type"), "page")
    r._r.append(br)
    return p


def main():
    doc = Document(SRC)

    # ---- 1) object photo pages ----
    ph_anchors = [p for p in doc.paragraphs if p.text.strip().startswith(PH_ANCHOR)]
    for i, anchor in enumerate(ph_anchors, 1):
        cur = anchor
        mp = os.path.join(ASSETS, f"map_{i}.jpg")
        ph = os.path.join(ASSETS, f"photo_{i}.jpg")
        if os.path.exists(mp):
            cur = set_image(insert_after(cur), mp, MAX_W, 9.6)
        if os.path.exists(ph):
            cur = set_image(insert_after(cur), ph, MAX_W, 10.2)
        anchor._p.getparent().remove(anchor._p)
        print(f"obj{i}: map={'+' if os.path.exists(mp) else '-'} photo={'+' if os.path.exists(ph) else '-'}")

    # ---- 2) zone pages ----
    zone_anchors = [p for p in doc.paragraphs if p.text.strip().startswith(ZONE_ANCHOR)]
    for k, anchor in enumerate(zone_anchors, 1):
        zmap = os.path.join(ASSETS, f"zmap_{k}.jpg")
        zaer = os.path.join(ASSETS, f"zaer_{k}.jpg")
        cur = page_break(insert_after(anchor))          # start the media on a new page
        if os.path.exists(zmap):
            cur = set_image(insert_after(cur), zmap, MAX_W, 9.6)
        if os.path.exists(zaer):
            cur = set_image(insert_after(cur), zaer, MAX_W, 10.8)
        page_break(insert_after(cur))                   # next zone starts fresh
        print(f"zone{k}: map={'+' if os.path.exists(zmap) else '-'} aerial={'+' if os.path.exists(zaer) else '-'}")

    doc.save(OUT)
    print("saved", OUT)


if __name__ == "__main__":
    main()
