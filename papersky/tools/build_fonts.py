#!/usr/bin/env python3
"""
Builds the serif faces of the Papersky design language (DESIGN_DOCTRINE §6) from Cormorant
Garamond (SIL Open Font License), as published in the google/fonts repository.

What it does to the upstream variable fonts:
  * subsets them to Latin, Latin Extended-A, Cyrillic and the punctuation the app uses, keeping
    every OpenType feature and the weight axis;
  * makes lining figures the default (the digits point at the glyphs 'lnum' would pick), so
    numbers stand upright everywhere — Compose, Canvas, and RemoteViews in widgets, which cannot
    switch features on;
  * sets the degree sign with the font's finer ring, sitting at cap height: Cormorant's own
    degree is a heavy superscript "o" that shouts next to light figures.

Run from the project root (needs network and fonttools):  python3 tools/build_fonts.py
"""
import os
import urllib.request

from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app/src/main/res/font")
BASE = "https://raw.githubusercontent.com/google/fonts/main/ofl/cormorantgaramond/"
SOURCES = {
    "serif.ttf": "CormorantGaramond%5Bwght%5D.ttf",
    "serif_italic.ttf": "CormorantGaramond-Italic%5Bwght%5D.ttf",
}
UNICODES = (
    "U+0020-007E,U+00A0-00FF,U+0100-017F,U+0192,U+02C6,U+02DA,U+02DC,U+0400-045F,U+0490-0491,"
    "U+2010-2027,U+2030-203A,U+2044,U+20AC,U+20BD,U+2116,U+2122,U+2190-2193,U+2212,U+2215,"
    "U+2248,U+2260,U+2264,U+2265,U+25CF"
)


def single_substitutions(font, tag):
    gsub = font["GSUB"].table
    mapping = {}
    for record in gsub.FeatureList.FeatureRecord:
        if record.FeatureTag != tag:
            continue
        for index in record.Feature.LookupListIndex:
            for table in gsub.LookupList.Lookup[index].SubTable:
                table = getattr(table, "ExtSubTable", table)
                mapping.update(getattr(table, "mapping", {}))
    return mapping


def build(source, target):
    path = os.path.join(OUT, target + ".src")
    urllib.request.urlretrieve(BASE + source, path)
    font = TTFont(path)
    options = subset.Options()
    options.layout_features = ["*"]
    options.hinting = False
    options.name_IDs = ["*"]
    options.notdef_outline = True
    subsetter = subset.Subsetter(options)
    subsetter.populate(unicodes=subset.parse_unicodes(UNICODES))
    subsetter.subset(font)

    lining = single_substitutions(font, "lnum")
    for table in font["cmap"].tables:
        for cp in range(0x30, 0x3A):
            glyph = table.cmap.get(cp)
            if glyph in lining:
                table.cmap[cp] = lining[glyph]
        if 0x02DA in table.cmap and 0x00B0 in table.cmap:
            table.cmap[0x00B0] = table.cmap[0x02DA]
    font.save(os.path.join(OUT, target))
    os.remove(path)
    print(target, os.path.getsize(os.path.join(OUT, target)), "bytes")


if __name__ == "__main__":
    for target, source in SOURCES.items():
        build(source, target)
