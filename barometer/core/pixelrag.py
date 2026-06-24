"""
PixelRAG — лёгкая интеграция через реверс-инжиниринг открытого hosted API.

Тяжёлую часть PixelRAG (рендер в скриншоты + vision-модель Qwen3-VL + FAISS,
нужен GPU) НЕ устанавливаем. Вместо этого дёргаем их открытый поисковый
эндпоинт напрямую:

    POST https://api.pixelrag.ai/search   (без ключа)
    {"queries":[{"text": "..."}], "n_docs": N}
    → {"results":[{"hits":[{"score":..., "url":"<Wikipedia_article>", ...}]}]}

Поле `url` — название статьи Wikipedia. Берём топ статей по теме барометра как
справочный «контекст ситуации» (visual RAG по 8.28M страниц Wikipedia).
Это фоновая энциклопедическая справка, а не живые сигналы. Лёгкий HTTP-вызов;
при любой ошибке возвращаем [] и пайплайн не ломается.
"""

from __future__ import annotations

import requests

API_URL = "https://api.pixelrag.ai/search"


def fetch_context(query: str, n: int = 8) -> list[dict]:
    try:
        r = requests.post(API_URL, json={"queries": [{"text": query}], "n_docs": n}, timeout=20)
        r.raise_for_status()
        results = r.json().get("results") or []
        hits = (results[0].get("hits") if results else []) or []
    except Exception:
        return []
    out, seen = [], set()
    for h in hits:
        u = (h.get("url") or "").strip()
        if not u or u in seen:
            continue
        seen.add(u)
        out.append({
            "title": u.replace("_", " "),
            "url": "https://en.wikipedia.org/wiki/" + u,
            "score": round(float(h.get("score", 0)), 3),
        })
        if len(out) >= 6:
            break
    return out
