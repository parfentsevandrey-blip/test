#!/usr/bin/env python3
"""
fill_registry.py — insert a Google-map screenshot + the main Funda photo onto
each object's (empty) photo page in an existing registry .docx.

Finds every "Вставьте фотографии объекта ниже" anchor (one per object, in order)
and inserts, right after it, the Google map then the main photo (images only, no
captions), then removes the instruction line itself. Everything else untouched.
"""
import sys, os
from docx import Document
from docx.shared import Cm, Pt
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.text.paragraph import Paragraph
from PIL import Image

SRC, OUT, ASSETS = sys.argv[1], sys.argv[2], sys.argv[3]
MAX_W = 17.4
MAP_H = 9.6
PHOTO_H = 10.2
ANCHOR = "Вставьте фотографии объекта ниже"


def insert_after(par):
    new_p = OxmlElement("w:p")
    par._p.addnext(new_p)
    return Paragraph(new_p, par._parent)


def set_image(p, path, max_w, max_h):
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(6)
    p.paragraph_format.keep_together = True
    w, h = Image.open(path).size
    tw = max_w; th = tw * h / w
    if th > max_h:
        th = max_h; tw = th * w / h
    p.add_run().add_picture(path, width=Cm(tw), height=Cm(th))
    return p


def main():
    doc = Document(SRC)
    anchors = [p for p in doc.paragraphs if p.text.strip().startswith(ANCHOR)]
    print(f"anchors found: {len(anchors)}")
    for i, anchor in enumerate(anchors, 1):
        cur = anchor
        map_path = os.path.join(ASSETS, f"map_{i}.jpg")
        photo_path = os.path.join(ASSETS, f"photo_{i}.jpg")
        if os.path.exists(map_path):
            cur = set_image(insert_after(cur), map_path, MAX_W, MAP_H)
        if os.path.exists(photo_path):
            cur = set_image(insert_after(cur), photo_path, MAX_W, PHOTO_H)
        # remove the "Вставьте фотографии объекта ниже" instruction line
        anchor._p.getparent().remove(anchor._p)
        print(f"[{i}] map={'+' if os.path.exists(map_path) else '-'} "
              f"photo={'+' if os.path.exists(photo_path) else '-'}")
    doc.save(OUT)
    print("saved", OUT)


if __name__ == "__main__":
    main()
