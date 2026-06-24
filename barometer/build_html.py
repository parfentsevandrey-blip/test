"""
Сборка автономного браузерного «Барометра» в ОДИН HTML-файл.

Берёт конфигурацию из config.py (лексикон, веса, параметры) и свежие новости
(один прогон конвейера), внедряет их вместе с движком web/engine.js и стилями
в web/template.html → пишет barometer.html.

    python3 build_html.py

Результат (barometer.html) открывается двойным кликом: считает барометр прямо
в браузере, DeepState тянет напрямую, RSS/Telegram — через CORS-прокси.
"""

from __future__ import annotations

import json
import os
import sys
from datetime import datetime, timezone

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import config  # noqa: E402
from core import pipeline, store, scoring  # noqa: E402


def _seed_items(limit: int = 400) -> list[dict]:
    rows = store.recent_items(config.WINDOW_DAYS, only_relevant=True, limit=limit)
    seed = []
    for r in rows:
        seed.append({
            "source_name": r.get("source_name", ""),
            "stream": r.get("stream", "media"),
            "lang": r.get("lang", "ru"),
            "title": r.get("title", ""),
            "summary": (r.get("summary", "") or "")[:600],
            "url": r.get("url", ""),
            "published": r.get("published", ""),
            "source_weight": r.get("source_weight", 1.0),
        })
    return seed


def _config_blob() -> dict:
    return {
        "categories": {
            k: {"label": v["label"], "weight": v["weight"], "scale": v["scale"]}
            for k, v in config.SIGNAL_CATEGORIES.items()
        },
        "params": {
            "K": config.SCORE_K, "B": config.SCORE_B, "halflife": config.HALFLIFE_DAYS,
            "windowDays": config.WINDOW_DAYS, "deepstateScale": config.DEEPSTATE_SCALE_KM2,
            "announced": config.ANNOUNCED_THRESHOLD, "forecast": config.FORECAST_THRESHOLD,
        },
        "lexicon": [[c, p, w, t] for (c, p, w, t) in config.LEXICON],
        "relevanceHints": list(config.RELEVANCE_HINTS),
        "rssSources": config.RSS_SOURCES,
        "telegramChannels": config.TELEGRAM_CHANNELS,
        "deepstateUrl": config.DEEPSTATE_LAST_URL,
        "streamLabels": scoring.STREAM_LABELS,
        "dataUrl": config.DATA_URL,
    }


def _safe_json(obj) -> str:
    # Экранируем закрывающие теги, чтобы не разорвать <script>.
    return json.dumps(obj, ensure_ascii=False).replace("</", "<\\/")


def build() -> str:
    print("→ Один прогон конвейера (сбор + анализ) для стартовых данных…")
    pipeline.run_pipeline(use_llm=False)
    seed = _seed_items()
    cfg = _config_blob()

    css = open(os.path.join(HERE, "static/css/barometer.css"), encoding="utf-8").read()
    engine = open(os.path.join(HERE, "web/engine.js"), encoding="utf-8").read()
    tpl = open(os.path.join(HERE, "web/template.html"), encoding="utf-8").read()

    build_at = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
    data_js = (
        f"window.CONFIG = {_safe_json(cfg)};\n"
        f"window.SEED = {_safe_json(seed)};\n"
        f"window.BUILD_AT = {json.dumps(build_at)};"
    )

    html = (tpl
            .replace("/*__CSS__*/", css)
            .replace("/*__DATA__*/", data_js)
            .replace("/*__ENGINE__*/", engine))

    out = os.path.join(HERE, "barometer.html")
    with open(out, "w", encoding="utf-8") as f:
        f.write(html)

    size_kb = round(os.path.getsize(out) / 1024)
    print(f"✓ Готово: {out}  ({size_kb} КБ, стартовых новостей: {len(seed)})")
    print("  Откройте файл двойным кликом в браузере.")
    return out


if __name__ == "__main__":
    build()
