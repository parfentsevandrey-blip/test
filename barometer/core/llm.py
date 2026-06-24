"""
Опциональный ИИ-анализ новостей (движок аналитики).

Поддерживает двух провайдеров; выбор автоматический (или через
BAROMETER_LLM_PROVIDER = gemini | anthropic):

  * Gemini   — бесплатный тариф Google, ключ GEMINI_API_KEY (или GOOGLE_API_KEY).
               Модель BAROMETER_GEMINI_MODEL (по умолчанию gemini-2.5-flash).
  * Claude   — ключ ANTHROPIC_API_KEY, модель BAROMETER_LLM_MODEL.

На вход — самые релевантные свежие новости; на выход — JSON-оценка
вероятности мобилизации (0–100), обоснование и ожидаемое окно. Итог
смешивается с правиловым барометром в scoring.py.

Если ключей нет или вызов не удался — возвращается None, и используется
только правиловый расчёт (приложение остаётся полностью рабочим).
"""

from __future__ import annotations

import json
import os
import re

import requests

import config

CLAUDE_MODEL = os.environ.get("BAROMETER_LLM_MODEL", "claude-sonnet-4-6")
GEMINI_MODEL = os.environ.get("BAROMETER_GEMINI_MODEL", "gemini-2.5-flash")

_PROMPT = """Ты — аналитик OSINT. На основе подборки заголовков и фрагментов новостей
из независимых российских СМИ, Telegram и западных военных аналитиков оцени
вероятность объявления НОВОЙ волны мобилизации в России в ближайшие месяцы.

Верни СТРОГО JSON без пояснений вокруг:
{
  "score": <целое 0-100, где 0 — мобилизации не будет, 100 — она объявлена/идёт>,
  "expected_window": "<короткая оценка срока, напр. '1–3 месяца' или 'не просматривается'>",
  "rationale": "<2-3 предложения по-русски: ключевые сигналы за и против>"
}

Новости:
"""


# --------------------------------------------------------------------------- #
#  Выбор провайдера                                                            #
# --------------------------------------------------------------------------- #
def _gemini_key() -> str:
    return (os.environ.get("GEMINI_API_KEY") or os.environ.get("GOOGLE_API_KEY") or "").strip()


def provider() -> str | None:
    forced = os.environ.get("BAROMETER_LLM_PROVIDER", "").strip().lower()
    has_gem = bool(_gemini_key())
    has_claude = bool(os.environ.get("ANTHROPIC_API_KEY"))
    if forced == "gemini":
        return "gemini" if has_gem else None
    if forced == "anthropic":
        return "anthropic" if has_claude else None
    if has_gem:
        return "gemini"
    if has_claude:
        return "anthropic"
    return None


def is_enabled() -> bool:
    return provider() is not None


def analyze(items: list[dict], max_items: int = 40) -> dict | None:
    """Единая точка входа: диспетчеризует к выбранному провайдеру."""
    p = provider()
    if p == "gemini":
        return analyze_with_gemini(items, max_items)
    if p == "anthropic":
        return analyze_with_claude(items, max_items)
    return None


def _pick(items: list[dict], max_items: int) -> list[dict]:
    picked = [it for it in items if it.get("relevant")][:max_items]
    if not picked:
        picked = items[:max_items]
    return picked


def _lines(items: list[dict]) -> str:
    out = []
    for it in items:
        when = (it.get("published") or "")[:10]
        out.append(f"- [{when}] ({it.get('source_name', '')}) {it.get('title', '')}")
    return "\n".join(out)


# --------------------------------------------------------------------------- #
#  Gemini (бесплатно, через REST + requests)                                  #
# --------------------------------------------------------------------------- #
def analyze_with_gemini(items: list[dict], max_items: int = 40) -> dict | None:
    key = _gemini_key()
    if not key:
        return None
    picked = _pick(items, max_items)
    if not picked:
        return None
    body = {
        "contents": [{"parts": [{"text": _PROMPT + _lines(picked)}]}],
        "generationConfig": {"temperature": 0.2, "maxOutputTokens": 700,
                             "responseMimeType": "application/json"},
    }
    url = (f"https://generativelanguage.googleapis.com/v1beta/models/"
           f"{GEMINI_MODEL}:generateContent?key={key}")
    try:
        r = requests.post(url, json=body, timeout=30)  # requests учитывает CA/прокси
        r.raise_for_status()
        data = r.json()
        cands = data.get("candidates") or []
        text = ""
        if cands:
            parts = (cands[0].get("content") or {}).get("parts") or []
            text = "".join(p.get("text", "") for p in parts)
        d = _extract_json(text)
        if not d:
            return None
        return {
            "score": max(0, min(100, int(round(float(d.get("score", 0)))))),
            "expected_window": str(d.get("expected_window", ""))[:120],
            "rationale": str(d.get("rationale", ""))[:600],
            "model": GEMINI_MODEL,
        }
    except Exception:
        return None


# --------------------------------------------------------------------------- #
#  Claude                                                                      #
# --------------------------------------------------------------------------- #
def _http_client():
    """httpx-клиент с нужным CA (для прокси с перехватом TLS); иначе None."""
    ca = os.environ.get("SSL_CERT_FILE") or os.environ.get("REQUESTS_CA_BUNDLE")
    if ca and os.path.exists(ca):
        try:
            import httpx

            return httpx.Client(verify=ca, trust_env=True)
        except Exception:
            return None
    return None


def analyze_with_claude(items: list[dict], max_items: int = 40) -> dict | None:
    if not os.environ.get("ANTHROPIC_API_KEY"):
        return None
    try:
        import anthropic
    except Exception:
        return None
    picked = _pick(items, max_items)
    if not picked:
        return None
    try:
        client = anthropic.Anthropic(http_client=_http_client())
        msg = client.messages.create(
            model=CLAUDE_MODEL,
            max_tokens=600,
            messages=[{"role": "user", "content": _PROMPT + _lines(picked)}],
        )
        text = "".join(b.text for b in msg.content if getattr(b, "type", "") == "text")
        d = _extract_json(text)
        if not d:
            return None
        return {
            "score": max(0, min(100, int(round(float(d.get("score", 0)))))),
            "expected_window": str(d.get("expected_window", ""))[:120],
            "rationale": str(d.get("rationale", ""))[:600],
            "model": CLAUDE_MODEL,
        }
    except Exception:
        return None


def _extract_json(text: str) -> dict | None:
    m = re.search(r"\{.*\}", text, re.DOTALL)
    if not m:
        return None
    try:
        return json.loads(m.group(0))
    except Exception:
        return None
