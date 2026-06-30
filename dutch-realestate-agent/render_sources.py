#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Рендерит sources.md из структурированной таксономии источников (sources.json).
Таксономию готовит workflow расширения источников; этот скрипт превращает её
в аккуратный Markdown и (по желанию) в компактный JSON для самого агента.

    python3 render_sources.py --taxonomy data/sources_taxonomy.json --out sources.md

Формат taxonomy:
{
  "intro": "...",
  "categories": [
    {"title": "...", "description": "...",
     "sources": [{"name","url","segments":[],"provides","frequency","language","priority"}]}
  ]
}
"""
import argparse, json

SEG_RU = {"residential": "жильё", "commercial": "ритейл",
          "industrial": "индустриал", "macro": "макро", "all": "все"}
PRIO_MARK = {"ключевой": "★", "важный": "●", "дополнительный": "○"}

def seg_str(segs):
    if not segs: return ""
    if "all" in segs: return "все сегменты"
    return ", ".join(SEG_RU.get(s, s) for s in segs)

def render(tax):
    L = []
    L.append("# Источники по рынку недвижимости Нидерландов\n")
    L.append("> Расширенная таксономия источников для серьёзного еженедельного отчёта.\n")
    if tax.get("intro"):
        L.append(tax["intro"].strip() + "\n")
    L.append("**Обозначения приоритета:** ★ ключевой · ● важный · ○ дополнительный.\n")

    total = sum(len(c.get("sources", [])) for c in tax.get("categories", []))
    L.append(f"_Всего источников: {total} в {len(tax.get('categories', []))} категориях._\n")

    # оглавление
    L.append("## Содержание")
    for i, c in enumerate(tax.get("categories", []), 1):
        anchor = c["title"].lower().replace(" ", "-")
        L.append(f"{i}. {c['title']} ({len(c.get('sources', []))})")
    L.append("")

    for i, c in enumerate(tax.get("categories", []), 1):
        L.append(f"## {i}. {c['title']}")
        if c.get("description"):
            L.append(f"_{c['description'].strip()}_\n")
        for s in c.get("sources", []):
            mark = PRIO_MARK.get(s.get("priority", ""), "")
            name = s.get("name", "")
            url = s.get("url", "")
            bits = []
            if s.get("provides"): bits.append(s["provides"].strip())
            meta = []
            if s.get("segments"): meta.append(seg_str(s["segments"]))
            if s.get("frequency"): meta.append(s["frequency"])
            if s.get("language"): meta.append(s["language"])
            tail = " — " + "; ".join(bits) if bits else ""
            metatail = f" _({' · '.join(meta)})_" if meta else ""
            L.append(f"- {mark} **[{name}]({url})**{tail}{metatail}")
        L.append("")

    return "\n".join(L)

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--taxonomy", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    tax = json.load(open(a.taxonomy, encoding="utf-8"))
    open(a.out, "w", encoding="utf-8").write(render(tax))
    total = sum(len(c.get("sources", [])) for c in tax.get("categories", []))
    print(f"✅ {a.out}: {total} источников в {len(tax.get('categories', []))} категориях")

if __name__ == "__main__":
    main()
