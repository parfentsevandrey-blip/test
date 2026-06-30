#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Рендерит sources.md из структурированной таксономии источников (sources_taxonomy.json).
Поддерживает институциональные метаданные: tier (core/important/supplementary),
access (free/freemium/paid/terminal), analyst_use, metrics, region, cadence.
(Совместимо со старым форматом: priority/frequency/language.)

    python3 render_sources.py --taxonomy data/sources_taxonomy.json --out sources.md
"""
import argparse, json, re


def _clean_title(t):
    """Убрать ведущую нумерацию из заголовка категории (рендерер нумерует сам)."""
    return re.sub(r"^\s*\d+[.)]\s*", "", t or "")

SEG_RU = {"residential": "жильё", "commercial": "ритейл",
          "industrial": "индустриал", "macro": "макро", "all": "все"}
TIER_MARK = {"core": "★", "important": "●", "supplementary": "○",
             "ключевой": "★", "важный": "●", "дополнительный": "○"}
ACCESS_RU = {"free": "откр.", "freemium": "freemium", "paid": "платн.", "terminal": "терминал"}


def seg_str(segs):
    if not segs:
        return ""
    if "all" in segs:
        return "все сегменты"
    return ", ".join(SEG_RU.get(s, s) for s in segs)


def render(tax):
    L = []
    L.append("# Источники по рынку недвижимости Нидерландов\n")
    L.append("> База источников уровня **институционального аналитика**: индексы и бенчмарки, "
             "капитал/долг/ставки, рейтинги, первичные раскрытия эмитентов и консенсус, стандарты "
             "и опросы настроений, прогнозные дома, ESG/оценка, суб-секторные дески.\n")
    if tax.get("intro"):
        L.append(tax["intro"].strip() + "\n")
    L.append("**Институциональный тир:** ★ core (опорный) · ● important · ○ supplementary.")
    if tax.get("access_legend"):
        L.append("**Доступ:** " + tax["access_legend"].strip() + "\n")
    else:
        L.append("**Доступ:** откр. (бесплатно) · freemium · платн. (подписка) · терминал "
                 "(Bloomberg/Refinitiv/CoStar и т.п.).\n")

    cats = tax.get("categories", [])
    total = sum(len(c.get("sources", [])) for c in cats)
    L.append(f"_Всего источников: {total} в {len(cats)} категориях._\n")

    L.append("## Содержание")
    for i, c in enumerate(cats, 1):
        L.append(f"{i}. {_clean_title(c['title'])} ({len(c.get('sources', []))})")
    L.append("")

    for i, c in enumerate(cats, 1):
        L.append(f"## {i}. {_clean_title(c['title'])}")
        if c.get("description"):
            L.append(f"_{c['description'].strip()}_\n")
        for s in c.get("sources", []):
            mark = TIER_MARK.get(s.get("tier") or s.get("priority", ""), "")
            name, url = s.get("name", ""), s.get("url", "")
            # бейдж: доступ · регион · частота
            badge = []
            if s.get("access"):
                badge.append(ACCESS_RU.get(s["access"], s["access"]))
            if s.get("region"):
                badge.append(s["region"])
            cad = s.get("cadence") or s.get("frequency")
            if cad:
                badge.append(cad)
            if not s.get("access") and s.get("language"):
                badge.append(s["language"])
            badge_s = f" `[{' · '.join(badge)}]`" if badge else ""
            L.append(f"- {mark} **[{name}]({url})**{badge_s}")
            if s.get("provides"):
                L.append(f"  - что даёт: {s['provides'].strip()}")
            if s.get("analyst_use"):
                L.append(f"  - аналитику: {s['analyst_use'].strip()}")
            extra = []
            if s.get("metrics"):
                extra.append("метрики: " + ", ".join(s["metrics"]))
            if s.get("segments"):
                extra.append("сегменты: " + seg_str(s["segments"]))
            if extra:
                L.append("  - " + "  ·  ".join(extra))
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
