#!/usr/bin/env python3
"""
Серверный сбор данных → web/data.json (для GitHub Action и ручного запуска).

Тянет источники напрямую (без CORS/прокси), считает барометр, делает ИИ-анализ
(если задан ключ провайдера в окружении/секретах) и пишет готовый state в
barometer/web/data.json. Дашборд читает этот файл с raw.githubusercontent
(там есть CORS) — поэтому связь надёжная, без браузерных прокси.

История барометра и площадь фронта переносятся из предыдущего data.json
(SQLite не нужен — всё в JSON, чистые диффы для git).

    python3 refresh_data.py
"""

from __future__ import annotations

import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import config  # noqa: E402
from core import analyze, llm, scoring, sources  # noqa: E402

DATA_PATH = os.path.join(HERE, "web", "data.json")


def _load_prev() -> dict:
    try:
        return json.load(open(DATA_PATH, encoding="utf-8"))
    except Exception:
        return {}


def main() -> None:
    prev = _load_prev()
    prev_hist = prev.get("history", []) if isinstance(prev, dict) else []
    prev_ds = (((prev.get("reading") or {}).get("components") or {}).get("deepstate") or {})
    prev_area = prev_ds.get("occupied_km2")

    collected = sources.collect_all()
    items = analyze.analyze_items(collected["items"])
    statuses = collected["statuses"]
    ds = collected["deepstate"]

    # Суточная дельта оккупации vs предыдущий замер.
    if ds.get("status") == "ok" and prev_area is not None:
        ds["delta_km2"] = round(ds["occupied_km2"] - prev_area, 2)
    else:
        ds["delta_km2"] = None

    ok = sum(1 for s in statuses if s.get("mode") != "error")
    ratio = ok / len(statuses) if statuses else 1.0

    window_items = [it for it in items if it.get("relevant")]
    hist_for_score = [{"taken_at": h["t"], "final_barometer": h["v"]} for h in prev_hist]

    llm_result = llm.analyze(window_items)  # ключ берётся из окружения (секрет Action)
    reading = scoring.compute_reading(
        window_items, ds, hist_for_score, llm=llm_result, sources_ok_ratio=ratio
    )

    new_hist = (prev_hist + [{"t": reading["taken_at"], "v": reading["final_barometer"]}])[-500:]

    feed = []
    for it in sorted(window_items, key=lambda x: x.get("published", ""), reverse=True)[:60]:
        seen, terms = set(), []
        for s in it.get("signals", []):
            if s["term"] not in seen:
                seen.add(s["term"])
                terms.append({"term": s["term"], "polarity": s["polarity"]})
        feed.append({
            "title": it.get("title", ""), "url": it.get("url", ""),
            "source": it.get("source_name", ""), "stream": it.get("stream", "media"),
            "published": it.get("published", ""), "terms": terms[:6],
        })

    state = {
        "status": "ok",
        "generated_at": reading["taken_at"],
        "reading": reading,
        "sources": [{"name": s["name"], "stream": s["stream"], "mode": s["mode"],
                     "items_count": s.get("items_count", 0)} for s in statuses],
        "history": new_hist,
        "feed": feed,
        "config": {"window_days": config.WINDOW_DAYS, "llm_provider": llm.provider()},
    }

    os.makedirs(os.path.dirname(DATA_PATH), exist_ok=True)
    json.dump(state, open(DATA_PATH, "w", encoding="utf-8"), ensure_ascii=False,
              separators=(",", ":"))
    print(f"✓ data.json: барометр {reading['final_barometer']} ({reading['zone']}), "
          f"новостей {len(window_items)}, ИИ={llm.provider() or 'выкл'}, "
          f"источников онлайн {ok}/{len(statuses)}")


if __name__ == "__main__":
    main()
