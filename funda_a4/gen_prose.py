#!/usr/bin/env python3
"""
gen_prose.py — turn raw Funda facts into a finished report content object,
writing the Russian descriptive prose with the Claude API.

Pipeline position:

    funda_fetch.py  -->  facts.json  -->  gen_prose.py  -->  content.json
                                                              |
                                  build_report_docx.py / build_report_pdf.py

funda_fetch.py extracts hard facts + photos (no LLM). This module adds the
descriptive sections ("О городе и районе", "Описание объекта", "Технические
характеристики", "Расположение и доступность", "Особенности и статус", and for
investment objects "Арендный доход") in the house style, producing the exact
JSON the renderers consume.

Requires the `anthropic` package and the ANTHROPIC_API_KEY environment variable.
Model defaults to Claude Opus 4.8 (best prose); override with ANTHROPIC_MODEL
(e.g. claude-sonnet-4-6 for cheaper/faster, claude-haiku-4-5-20251001).

Usage:
    export ANTHROPIC_API_KEY=sk-ant-...
    python3 gen_prose.py facts.json --photos-glob "assets/obj{n}_p*.jpg" -o content.json
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from typing import List

DEFAULT_MODEL = os.environ.get("ANTHROPIC_MODEL", "claude-opus-4-8")

SYSTEM = (
    "Ты — аналитик коммерческой недвижимости и редактор. По фактам объявления "
    "Funda (in Business) ты пишешь раздел отчёта на русском языке в деловом, "
    "нейтрально-привлекательном стиле, как в профессиональных инвест-подборках. "
    "Не выдумывай цифры: используй только переданные факты, общеизвестные сведения "
    "о городе/районе допустимы. Сохраняй нидерландские топонимы и термины в "
    "оригинале (Hofkwartier, eigen grond, k.k., BVO и т.п.). Отвечай СТРОГО "
    "одним JSON-объектом без пояснений и markdown."
)

# JSON shape we want back (matches the renderers' content model for one object)
SCHEMA_HINT = """
Верни JSON-объект объекта со следующими полями:
{
  "address": "<улица, город>",
  "price_label": "<€ ...>",
  "district": "<индекс • Район ... • ...>",
  "map_zoom": <int 11-14, меньше = виден более широкий охват с соседними городами>,
  "specs": [["Цена","..."],["Общая площадь","..."],["Цена за м²","..."],
            ["Тип объекта","..."],["Энергетический класс","..."],["Год постройки","..."],
            ["Передача","..."],["Брокер","..."],["Ссылка","<source_url>"]],
  "sections": [
    {"heading":"О ГОРОДЕ И РАЙОНЕ","subheading":"📍 <город> — <район>","paragraphs":["...","..."]},
    {"heading":"ОПИСАНИЕ ОБЪЕКТА","paragraphs":["...","..."]},
    {"heading":"ТЕХНИЧЕСКИЕ ХАРАКТЕРИСТИКИ","bullets":["...","..."]},
    {"heading":"РАСПОЛОЖЕНИЕ И ДОСТУПНОСТЬ","bullets":["...","..."]},
    {"heading":"ОСОБЕННОСТИ И СТАТУС","bullets":["...","..."]}
  ]
}
Если есть арендный доход (rent_income) — добавь раздел "АРЕНДНЫЙ ДОХОД" (bullets)
и тип объекта пометь как инвестиционный. Поля specs заполняй из фактов; вычисли
"Цена за м²" если есть цена и площадь. Не добавляй поля photos/lat/lon.
""".strip()


def generate_object(facts: dict, model: str = DEFAULT_MODEL) -> dict:
    try:
        import anthropic
    except ImportError:
        sys.exit("Требуется пакет 'anthropic' (pip install anthropic) и ANTHROPIC_API_KEY.")
    client = anthropic.Anthropic()  # reads ANTHROPIC_API_KEY from env

    user = (
        "ФАКТЫ ОБЪЕКТА (JSON):\n"
        + json.dumps(facts, ensure_ascii=False, indent=2)
        + "\n\n" + SCHEMA_HINT
    )
    msg = client.messages.create(
        model=model,
        max_tokens=4000,
        system=SYSTEM,
        messages=[{"role": "user", "content": user}],
    )
    text = "".join(block.text for block in msg.content if block.type == "text").strip()
    # be tolerant of accidental code fences
    if text.startswith("```"):
        text = text.strip("`")
        text = text[text.find("{"): text.rfind("}") + 1]
    return json.loads(text)


def main() -> int:
    ap = argparse.ArgumentParser(description="Generate report prose from Funda facts via Claude.")
    ap.add_argument("facts", help="facts.json produced by funda_fetch.py (list of objects).")
    ap.add_argument("--title", default="ОБЪЕКТЫ НЕДВИЖИМОСТИ")
    ap.add_argument("--subtitle", default="Нидерланды")
    ap.add_argument("--photos-glob", default="assets/obj{n}_p*.jpg",
                    help="Glob template for each object's photos; {n} is the 1-based index.")
    ap.add_argument("--model", default=DEFAULT_MODEL)
    ap.add_argument("-o", "--out", default="content.json")
    args = ap.parse_args()

    with open(args.facts, encoding="utf-8") as fh:
        facts_list = json.load(fh)
    if isinstance(facts_list, dict):
        facts_list = [facts_list]

    objects: List[dict] = []
    for n, facts in enumerate(facts_list, 1):
        print(f"[{n}/{len(facts_list)}] prose for {facts.get('address','?')} via {args.model} ...")
        obj = generate_object(facts, args.model)
        # carry over machine fields the LLM must not invent
        if facts.get("lat") is not None:
            obj["lat"], obj["lon"] = facts["lat"], facts["lon"]
        obj["source_url"] = facts.get("source_url", obj.get("source_url", ""))
        obj["photos"] = "glob:" + args.photos_glob.replace("{n}", str(n))
        objects.append(obj)

    content = {"title": args.title, "subtitle": args.subtitle, "objects": objects}
    with open(args.out, "w", encoding="utf-8") as fh:
        json.dump(content, fh, ensure_ascii=False, indent=2)
    print(f"\nWrote {args.out} ({len(objects)} objects). "
          f"Render with build_report_docx.py / build_report_pdf.py.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
