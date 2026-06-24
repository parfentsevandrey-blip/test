"""
Анализ текста новостей (правила/ключевые слова).

Для каждой новости извлекаем сигналы из лексикона (config.LEXICON):
каждый сигнал — это (категория, полярность ±1, вес, найденная фраза).
Полярность +1 толкает барометр к мобилизации, −1 — от неё (опровержения).

Это бесплатный офлайн-слой. Глубокий разбор Claude — в llm.py (опционально).
"""

from __future__ import annotations

import re

import config

_WS_RE = re.compile(r"\s+")


def normalize(s: str | None) -> str:
    s = (s or "").lower().replace("ё", "е").replace("\xa0", " ")
    return _WS_RE.sub(" ", s).strip()


# Предкомпилируем нормализованный лексикон и подсказки релевантности.
_LEXICON = [(cat, pol, w, term, normalize(term)) for (cat, pol, w, term) in config.LEXICON]
_HINTS = [normalize(h) for h in config.RELEVANCE_HINTS]


def is_relevant(norm_text: str) -> bool:
    return any(h in norm_text for h in _HINTS)


def analyze_item(item: dict) -> dict:
    """Заполняет item['signals'] и item['relevant'] на месте и возвращает его."""
    text = normalize(f"{item.get('title', '')} {item.get('summary', '')}")
    signals = []
    for cat, pol, w, term, term_norm in _LEXICON:
        if term_norm and term_norm in text:
            signals.append({"category": cat, "polarity": pol, "weight": w, "term": term})
    item["signals"] = signals
    item["relevant"] = 1 if (signals or is_relevant(text)) else 0
    return item


def analyze_items(items: list[dict]) -> list[dict]:
    return [analyze_item(it) for it in items]
