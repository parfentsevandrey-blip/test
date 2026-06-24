"""
Опциональный глубокий анализ через Claude API.

Включается, если задан ANTHROPIC_API_KEY. На вход — самые релевантные свежие
новости; на выход — JSON-оценка вероятности мобилизации (0–100), краткое
обоснование и ожидаемое окно. Итог смешивается с правиловым барометром.

Если ключа нет или вызов не удался — возвращается None, и используется
только правиловый расчёт (приложение остаётся полностью рабочим).
"""

from __future__ import annotations

import json
import os
import re

DEFAULT_MODEL = os.environ.get("BAROMETER_LLM_MODEL", "claude-sonnet-4-6")

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


def is_enabled() -> bool:
    return bool(os.environ.get("ANTHROPIC_API_KEY"))


def _http_client():
    """Возвращает httpx-клиент с нужным CA, если в окружении задан bundle.

    В обычной среде CA-переменные не заданы → возвращаем None (SDK сам выберет
    certifi). В среде с перехватом TLS (корпоративный прокси) httpx по умолчанию
    игнорирует SSL_CERT_FILE, поэтому передаём verify явно.
    """
    ca = os.environ.get("SSL_CERT_FILE") or os.environ.get("REQUESTS_CA_BUNDLE")
    if ca and os.path.exists(ca):
        try:
            import httpx

            return httpx.Client(verify=ca, trust_env=True)
        except Exception:
            return None
    return None


def analyze_with_claude(items: list[dict], max_items: int = 40) -> dict | None:
    if not is_enabled():
        return None
    try:
        import anthropic  # импорт здесь, чтобы зависимость была мягкой
    except Exception:
        return None

    # Берём самые свежие релевантные новости с сигналами.
    picked = [it for it in items if it.get("relevant")][:max_items]
    if not picked:
        picked = items[:max_items]
    if not picked:
        return None

    lines = []
    for it in picked:
        when = (it.get("published") or "")[:10]
        lines.append(f"- [{when}] ({it.get('source_name', '')}) {it.get('title', '')}")
    prompt = _PROMPT + "\n".join(lines)

    try:
        client = anthropic.Anthropic(http_client=_http_client())
        msg = client.messages.create(
            model=DEFAULT_MODEL,
            max_tokens=600,
            messages=[{"role": "user", "content": prompt}],
        )
        text = "".join(
            block.text for block in msg.content if getattr(block, "type", "") == "text"
        )
        data = _extract_json(text)
        if not data:
            return None
        score = max(0, min(100, int(round(float(data.get("score", 0))))))
        return {
            "score": score,
            "expected_window": str(data.get("expected_window", ""))[:120],
            "rationale": str(data.get("rationale", ""))[:600],
            "model": DEFAULT_MODEL,
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
