"""Человеческое объяснение вердикта.

Разделение ответственности принципиальное:
  * pricing.py считает цифры — детерминированно, воспроизводимо, с логом поправок;
  * narrative.py только пересказывает уже посчитанное человеческим языком.

Модель не имеет права изменить рекомендацию: она получает готовый вердикт и пишет
по нему текст. Без ключа ANTHROPIC_API_KEY используется шаблонный fallback, поэтому
бот работает всегда — просто суше.
"""

from __future__ import annotations

import json
import logging
import os
import textwrap

from .models import Action, Verdict

log = logging.getLogger(__name__)

MODEL = os.getenv("ANTHROPIC_MODEL", "claude-opus-5")

SYSTEM = """\
Ты — аналитик отдела продаж премиальной недвижимости Москвы. Тебе дают ГОТОВЫЙ расчёт
по объекту: рыночный коридор, позицию в нём, сигналы спроса и рекомендацию по цене.

Твоя задача — объяснить расчёт менеджеру за 4–6 предложений, так чтобы он мог
пересказать это собственнику.

Жёсткие правила:
- НЕ меняй ни одного числа и не выводи новых. Все цифры бери из переданных данных.
- НЕ придумывай рыночные факты, которых нет во входных данных.
- Не предлагай другую цену, чем рекомендованная.
- Если в данных есть предупреждения (warnings) — обязательно упомяни главное из них.
- Пиши по-деловому, без маркетинговых прилагательных и без эмодзи.
- Начни с сути: что делаем с ценой и почему.
"""


def explain(verdict: Verdict) -> str:
    """Текст объяснения вердикта. При недоступности LLM — шаблон."""
    try:
        import anthropic
    except ImportError:
        log.info("anthropic SDK не установлен — шаблонное объяснение")
        return template_explanation(verdict)

    if not os.getenv("ANTHROPIC_API_KEY"):
        log.info("ANTHROPIC_API_KEY не задан — шаблонное объяснение")
        return template_explanation(verdict)

    try:
        client = anthropic.Anthropic()
        response = client.messages.create(
            model=MODEL,
            max_tokens=1200,
            system=SYSTEM,
            output_config={"effort": "low"},
            messages=[{"role": "user", "content": json.dumps(_payload(verdict), ensure_ascii=False)}],
        )
    except Exception as exc:  # сеть, лимиты, отказ — бот всё равно должен ответить
        log.error("LLM недоступна (%s) — шаблонное объяснение", exc)
        return template_explanation(verdict)

    if response.stop_reason == "refusal":
        log.warning("Модель отказалась отвечать — шаблонное объяснение")
        return template_explanation(verdict)

    text = "".join(b.text for b in response.content if b.type == "text").strip()
    return text or template_explanation(verdict)


def _payload(v: Verdict) -> dict:
    """Компактное представление вердикта для модели — только то, что нужно для пересказа."""
    a = v.apartment
    return {
        "объект": {
            "жк": a.complex_name,
            "адрес": a.address,
            "комнат": a.rooms,
            "площадь_м2": a.area,
            "этаж": f"{a.floor} из {a.floors_total}",
            "отделка": a.finish.value,
            "машино_место_в_лоте": a.has_parking,
            "текущая_цена_руб": a.price,
            "текущая_цена_за_м2": round(a.price_per_sqm),
            "дней_в_экспозиции": a.days_on_market,
            "показов_за_30_дней": a.viewings_30d,
        },
        "рынок": {
            "коридор_за_м2": {
                "p25": round(v.corridor[0]),
                "медиана": round(v.corridor[1]),
                "p75": round(v.corridor[2]),
            },
            "наш_перцентиль": round(v.our_percentile),
            "аналогов_учтено": len(v.comps),
        },
        "рекомендация": {
            "действие": v.action.value,
            "цена_руб": v.recommended_price,
            "изменение_процент": round(v.delta_pct * 100, 1),
            "уверенность": v.confidence,
        },
        "сигналы": v.signals,
        "предупреждения": v.warnings,
        "сценарии": [
            {"название": s.name, "цена_руб": s.price, "ожидаемый_срок_дней": s.expected_days}
            for s in v.scenarios
        ],
    }


def template_explanation(v: Verdict) -> str:
    """Fallback без LLM: тот же смысл, просто суше. Ничего не выдумывает."""
    a = v.apartment
    head = {
        Action.CUT: (
            f"Рекомендуем снизить цену до {v.recommended_price / 1e6:.1f} млн ₽ "
            f"({v.delta_pct:+.1%})."
        ),
        Action.RAISE: (
            f"Есть основание поднять цену до {v.recommended_price / 1e6:.1f} млн ₽ "
            f"({v.delta_pct:+.1%})."
        ),
        Action.HOLD: f"Цену {a.price / 1e6:.1f} млн ₽ рекомендуем держать.",
        Action.MANUAL: "Автоматическая оценка недостаточно надёжна — нужен ручной разбор.",
    }[v.action]

    body = [head, ""]
    body += [f"• {s}" for s in v.signals]
    if v.warnings:
        body += [""] + [f"⚠️ {w}" for w in v.warnings]
    return textwrap.dedent("\n".join(body)).strip()
