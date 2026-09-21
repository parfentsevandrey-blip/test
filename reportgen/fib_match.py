#!/usr/bin/env python3
"""Отбор по трём признакам, которых у funda нет отдельными полями.

    python3 -m reportgen.fib_match --in fib/objects.json --top 12

Задача: угловой дом, коммерция на первом этаже, квартиры сверху. Ни один из
трёх признаков не является фильтром funda in business — там фильтруется город,
категория и цена. Значит, признаки читаются из текста объявления, и это чтение
прозы, а не поле: результат — кандидаты на осмотр, а не готовый ответ.

Поэтому скрипт не ставит вердикт «подходит». Он раскладывает совпадения по трём
признакам и печатает, ЧЕМ именно сработал каждый лот, чтобы дальше смотреть
глазами: угловое положение видно на фасадном кадре, «bovenwoning» в тексте
может оказаться одной квартирой над магазином, а может — четырьмя.
"""
import argparse, json, re, sys

# Признак 1: угловое положение. hoekpand — прямое название, остальное — контекст.
CORNER = [r"\bhoekpand\b", r"\bhoeklocatie\b", r"\bhoekligging\b", r"\bop de hoek\b",
          r"\bhoekwinkel\b", r"\bhoekperceel\b", r"\bhoekgelegen\b", r"\bhoekobject\b",
          r"\bhoekpositie\b", r"\bdubbele hoek\b"]

# Признак 2: коммерция внизу
COMMERCIAL = [r"\bwinkelruimte\b", r"\bwinkelpand\b", r"\bbedrijfsruimte\b",
              r"\bhorecaruimte\b", r"\bhorecagelegenheid\b", r"\bcasco winkel\b",
              r"\bkantoorruimte op de begane grond\b", r"\bwinkel op de begane grond\b",
              r"\bcommerci[eë]le ruimte\b", r"\bplint\b"]
GROUND = [r"\bbegane grond\b", r"\bparterre\b", r"\bstraatniveau\b", r"\bplint\b"]

# Признак 3: жильё сверху
UPSTAIRS = [r"\bbovenwoning(?:en)?\b", r"\bbovengelegen woning(?:en)?\b",
            r"\bappartement(?:en)?\s+(?:op|boven)\b", r"\bwoonlagen\b",
            r"\bwoningen\s+op\s+de\s+(?:eerste|tweede|verdieping)\b",
            r"\bbovenliggende\s+(?:woning|appartement)", r"\bwoon-winkelpand\b",
            r"\bwinkel-woonhuis\b", r"\bwinkel\s*/\s*woonpand\b",
            r"\bmet\s+bovenwoning", r"\bverhuurde\s+appartementen\b"]

# Хорошие приметы доходного дома целиком, а не одного помещения в нём
WHOLE = [r"\bgeheel\s+pand\b", r"\bhet\s+gehele\s+pand\b", r"\bcompleet\s+pand\b",
         r"\bbeleggingspand\b", r"\bbeleggingsobject\b", r"\bgemengd\b",
         r"\bmix\s*van\s*wonen\b", r"\bmeerdere\s+units\b"]

# То, что почти наверняка НЕ подходит: продаётся одна секция, а не дом
NEGATIVE = [r"\bappartementsrecht\b.*\bwinkelruimte\b", r"\balleen\s+de\s+winkelruimte\b",
            r"\buitsluitend\s+de\s+bedrijfsruimte\b"]

# Продажа или аренда. «te huur» в тексте не годится признаком: в объявлении о
# продаже доходного дома оно стоит про арендаторов. Отличает цена: у продажи
# «vraagprijs / koopsom / k.k. / v.o.n.», у аренды «huurprijs … per jaar».
SALE = [r"\bvraagprijs\b", r"\bkoopsom\b", r"\bkoopprijs\b", r"\bk\.k\.", r"\bv\.o\.n\."]
RENT = [r"\bhuurprijs\b", r"\bhuurprijzen\b"]


def hits(text, patterns):
    out = []
    for p in patterns:
        m = re.search(p, text, re.I)
        if m:
            out.append(m.group(0).lower())
    return out


def context(text, word, span=110):
    m = re.search(re.escape(word), text, re.I)
    if not m:
        return ""
    a = max(0, m.start() - span)
    return "…" + text[a:m.end() + span].strip() + "…"


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--in", dest="src", default="fib/objects.json")
    ap.add_argument("--top", type=int, default=12)
    ap.add_argument("--out", default="fib/matched.json")
    a = ap.parse_args()

    R = json.load(open(a.src, encoding="utf-8"))
    scored = []
    for r in R:
        t = (r.get("text") or "") + " " + (r.get("title") or "")
        c, com, gr, up = (hits(t, CORNER), hits(t, COMMERCIAL),
                          hits(t, GROUND), hits(t, UPSTAIRS))
        whole, neg = hits(t, WHOLE), hits(t, NEGATIVE)
        sale, rent = hits(t, SALE), hits(t, RENT)
        deal = "продажа" if sale else ("аренда" if rent else "не указано")
        # три признака равноценны: без любого из них это не тот объект
        score = (2 if c else 0) + (1 if com and gr else 0.5 if com else 0) \
            + (2 if up else 0) + (0.5 if whole else 0) - (1 if neg else 0)
        scored.append({**{k: r.get(k) for k in
                          ("id", "url", "address", "city", "price", "category")},
                       "score": score, "deal": deal, "corner": c, "commercial": com,
                       "ground": gr, "upstairs": up, "whole": whole, "negative": neg,
                       "photos": len(r.get("media_ids") or [])})
    scored.sort(key=lambda x: (-x["score"], x.get("city") or ""))
    json.dump(scored, open(a.out, "w", encoding="utf-8"), indent=1, ensure_ascii=False)

    full = [s for s in scored if s["corner"] and s["commercial"] and s["upstairs"]]
    print(f"объектов {len(scored)}; со ВСЕМИ тремя признаками в тексте: {len(full)}")
    by_city = {}
    for s in scored[:a.top]:
        by_city.setdefault(s.get("city") or "—", []).append(s)
    for s in scored[:a.top]:
        print(f"\n{s['score']:>4}  {s.get('city','—'):10s} {(s.get('address') or '')[:38]:38s} "
              f"{(str(s.get('price')) or '?'):>9} €  {s['deal']:10s} фото {s['photos']}")
        print(f"      {s['url']}")
        for label, v in (("угол", s["corner"]), ("коммерция", s["commercial"]),
                         ("низ", s["ground"]), ("жильё сверху", s["upstairs"]),
                         ("дом целиком", s["whole"]), ("против", s["negative"])):
            if v:
                print(f"      {label:14s} {', '.join(v[:4])}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
