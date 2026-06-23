#!/usr/bin/env python3
"""
gen_prose_free.py — build report prose WITHOUT any API key (the creative answer
to "what about the API-key problem").

The original sample's descriptions are essentially: (a) a city/district intro,
(b) the broker's own object description, (c) fact-derived bullet lists. None of
that strictly needs a paid LLM — so this module assembles them from free,
keyless sources discovered via reverse-engineering:

  * "О ГОРОДЕ И РАЙОНЕ"  -> Wikipedia (ru) REST summary of the city + a templated
                           district line from the funda neighborhood/socio facts.
  * "ОПИСАНИЕ ОБЪЕКТА"   -> the listing's own Dutch description (funda_fetch.py)
                           machine-translated nl->ru via the free MyMemory API
                           (Google Translate is IP-blocked from datacenters).
  * spec table + bullets -> deterministic templates over the parsed facts.

Output is the same content.json the renderers consume. Quality is "good machine
draft" — for publication-grade Russian, refine in-session or via gen_prose.py
(Claude API). No key, no billing, fully autonomous.

Usage:
    python3 gen_prose_free.py facts.json --photos-glob "assets/obj{n}_p*.jpg" -o content.json
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import time
from typing import List, Optional

import requests

UA = "funda-report/1.0 (+https://example.org)"
MYMEMORY = "https://api.mymemory.translated.net/get"
WIKI_SEARCH = "https://ru.wikipedia.org/w/api.php"
WIKI_SUMMARY = "https://ru.wikipedia.org/api/rest_v1/page/summary/"


# --------------------------------------------------------------------------
# free translation (nl -> ru), chunked to respect MyMemory's length limit
# --------------------------------------------------------------------------

def _sentences(text: str) -> List[str]:
    parts = re.split(r"(?<=[.!?])\s+", text.strip())
    out, buf = [], ""
    for s in parts:
        if len(buf) + len(s) < 480:
            buf = (buf + " " + s).strip()
        else:
            if buf:
                out.append(buf)
            buf = s
    if buf:
        out.append(buf)
    return out


def translate(text: str, src="nl", tgt="ru") -> str:
    if not text:
        return ""
    chunks = []
    for chunk in _sentences(text):
        try:
            r = requests.get(MYMEMORY, params={"q": chunk, "langpair": f"{src}|{tgt}"},
                             headers={"User-Agent": UA}, timeout=25)
            t = r.json().get("responseData", {}).get("translatedText", "")
            chunks.append(t or chunk)
        except Exception:
            chunks.append(chunk)
        time.sleep(0.4)
    out = " ".join(chunks)
    # tidy common MT artefacts
    out = re.sub(r"\s+([,.;:])", r"\1", out)
    out = re.sub(r"\s{2,}", " ", out).strip()
    return out


# --------------------------------------------------------------------------
# free city background (Wikipedia ru)
# --------------------------------------------------------------------------

def city_intro(city: str) -> str:
    title = city
    try:
        r = requests.get(WIKI_SEARCH, params={"action": "query", "list": "search",
                         "srsearch": city, "format": "json", "srlimit": 1},
                         headers={"User-Agent": UA}, timeout=20)
        hits = r.json().get("query", {}).get("search", [])
        if hits:
            title = hits[0]["title"]
    except Exception:
        pass
    try:
        r = requests.get(WIKI_SUMMARY + requests.utils.quote(title),
                         headers={"User-Agent": UA}, timeout=20)
        extract = r.json().get("extract", "")
        # keep first 2-3 sentences
        return " ".join(_split_sent(extract)[:3])
    except Exception:
        return ""


def _split_sent(text):
    return [s for s in re.split(r"(?<=[.!?])\s+", text.strip()) if s]


# --------------------------------------------------------------------------
# fact -> content object
# --------------------------------------------------------------------------

def _num(s) -> Optional[float]:
    if not s:
        return None
    m = re.search(r"[\d.,]+", str(s))
    if not m:
        return None
    raw = m.group(0)
    # strip thousands separators (both '.' and ',' before groups of 3 digits)
    raw = re.sub(r"[.,](?=\d{3}(\D|$))", "", raw)
    # any remaining separator is a decimal point
    raw = raw.replace(",", ".")
    try:
        return float(raw)
    except ValueError:
        return None


def build_object(facts: dict, photos_glob: str) -> dict:
    f = facts.get("features", {})
    city = facts.get("city", "")
    addr = facts.get("address", "")
    price = facts.get("price_label", "")

    # price per m2
    ppm = ""
    p, area = _num(price), _num(f.get("area"))
    if p and area:
        ppm = f"≈ € {round(p / area):,}/м²".replace(",", ".")

    specs = [["Цена", price + " k.k." if price and "k.k" not in price else price]]
    if f.get("area"):
        specs.append(["Общая площадь", f"≈ {f['area']} м²"])
    if ppm:
        specs.append(["Цена за м²", ppm])
    if f.get("plot"):
        specs.append(["Площадь участка", f"{f['plot']} м²"])
    if f.get("main_use"):
        specs.append(["Тип объекта", f["main_use"]])
    if f.get("energy"):
        specs.append(["Энергетический класс", f["energy"]])
    if f.get("year"):
        specs.append(["Год постройки", f["year"]])
    if f.get("front"):
        specs.append(["Ширина фасада", f"{f['front']} м"])
    if f.get("acceptance"):
        specs.append(["Передача", f["acceptance"]])
    if facts.get("source_url"):
        specs.append(["Ссылка", facts["source_url"]])

    # sections
    district = facts.get("neighborhood", "")
    socio = facts.get("socio", "")
    intro = city_intro(city)
    geo_paras = [p for p in [intro] if p]
    loc_line = f"Объект расположен в районе {district}." if district else ""
    if socio:
        loc_line += f" Социально-экономическая классификация района: {socio}."
    if loc_line:
        geo_paras.append(loc_line.strip())

    desc_ru = translate(facts.get("description_nl", ""))
    sections = [
        {"heading": "О ГОРОДЕ И РАЙОНЕ",
         "subheading": f"📍 {city}" + (f" — {district}" if district else ""),
         "paragraphs": geo_paras or ["—"]},
        {"heading": "ОПИСАНИЕ ОБЪЕКТА", "paragraphs": [desc_ru or "—"]},
    ]

    tech = []
    for label, key in [("Год постройки", "year"), ("Основное назначение", "main_use"),
                       ("Энергетический класс", "energy"), ("Общая площадь", "area"),
                       ("Ширина фасада", "front")]:
        if f.get(key):
            val = f[key] + (" м²" if key == "area" else " м" if key == "front" else "")
            tech.append(f"{label}: {val}")
    if tech:
        sections.append({"heading": "ТЕХНИЧЕСКИЕ ХАРАКТЕРИСТИКИ", "bullets": tech})

    if f.get("rent_income"):
        rent = _num(f["rent_income"])
        yld = f"≈ {round(rent / p * 100, 1)}% от цены" if (rent and p) else ""
        bullets = [f"Брутто-арендный доход: € {f['rent_income']} в год"]
        if yld:
            bullets.append(f"Валовая начальная доходность: {yld}")
        sections.append({"heading": "АРЕНДНЫЙ ДОХОД", "bullets": bullets})

    return {
        "address": addr,
        "price_label": price,
        "district": " • ".join(x for x in [facts.get("postal", ""), district] if x),
        "map_zoom": 13,
        "source_url": facts.get("source_url", ""),
        "lat": facts.get("lat"), "lon": facts.get("lon"),
        "photos": "glob:" + photos_glob,
        "specs": specs,
        "sections": sections,
    }


def main() -> int:
    ap = argparse.ArgumentParser(description="Keyless report prose (MyMemory + Wikipedia + templates).")
    ap.add_argument("facts")
    ap.add_argument("--title", default="ОБЪЕКТЫ НЕДВИЖИМОСТИ")
    ap.add_argument("--subtitle", default="Нидерланды")
    ap.add_argument("--photos-glob", default="assets/obj{n}_p*.jpg")
    ap.add_argument("-o", "--out", default="content.json")
    args = ap.parse_args()

    facts_list = json.load(open(args.facts, encoding="utf-8"))
    if isinstance(facts_list, dict):
        facts_list = [facts_list]
    objects = []
    for n, facts in enumerate(facts_list, 1):
        print(f"[{n}/{len(facts_list)}] prose (keyless) for {facts.get('address','?')} ...")
        objects.append(build_object(facts, args.photos_glob.replace("{n}", str(n))))
    content = {"title": args.title, "subtitle": args.subtitle, "objects": objects}
    json.dump(content, open(args.out, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print(f"\nWrote {args.out} ({len(objects)} objects).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
