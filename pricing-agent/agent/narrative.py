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
from typing import TYPE_CHECKING

from .models import Action, Verdict

if TYPE_CHECKING:  # только для типов — иначе получится цикл импорта
    from .lotreport import LotReport

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


def _ask(system: str, payload: dict, fallback: str) -> str:
    """Один вызов модели с пересказом готового расчёта. Любой сбой → fallback.

    Бот обязан ответить всегда: недоступность LLM не должна оставлять менеджера
    без цены, поэтому здесь нет ни одного пути, который бросает исключение наружу.
    """
    try:
        import anthropic
    except ImportError:
        log.info("anthropic SDK не установлен — шаблонное объяснение")
        return fallback

    if not os.getenv("ANTHROPIC_API_KEY"):
        log.info("ANTHROPIC_API_KEY не задан — шаблонное объяснение")
        return fallback

    try:
        client = anthropic.Anthropic()
        response = client.messages.create(
            model=MODEL,
            max_tokens=1200,
            system=system,
            output_config={"effort": "low"},
            messages=[{"role": "user", "content": json.dumps(payload, ensure_ascii=False)}],
        )
    except Exception as exc:  # сеть, лимиты, отказ — бот всё равно должен ответить
        log.error("LLM недоступна (%s) — шаблонное объяснение", exc)
        return fallback

    if response.stop_reason == "refusal":
        log.warning("Модель отказалась отвечать — шаблонное объяснение")
        return fallback

    text = "".join(b.text for b in response.content if b.type == "text").strip()
    return text or fallback


def explain(verdict: Verdict) -> str:
    """Текст объяснения вердикта. При недоступности LLM — шаблон."""
    return _ask(SYSTEM, _payload(verdict), template_explanation(verdict))


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


SYSTEM_PRICE = """\
Ты — аналитик отдела продаж премиальной недвижимости Москвы. Тебе дают ГОТОВЫЙ разбор
лота: позицию внутри дома, приведение цен соседей к нашему этажу, бюджет въезда
(цена плюс доводка), сравнение с проектами локации и уже рассчитанную рекомендованную
цену с перечнем причин.

Задача — объяснить менеджеру за 4–6 предложений ровно две вещи: какую цену ставим и
почему именно её. Ничего больше наружу не идёт.

Жёсткие правила:
- НЕ меняй ни одного числа и не выводи новых. Все цифры бери из переданных данных.
- НЕ предлагай другую цену, чем рекомендованная, и не давай «вилку от себя».
- НЕ придумывай рыночных фактов, которых нет во входных данных.
- Обязательно назови ограничение, которое определило цену (поле «что_определило_цену»).
- Если лот без отделки — объясни разницу между ценой в объявлении и бюджетом въезда:
  покупатель сравнивает второе.
- Пиши по-деловому, без маркетинговых прилагательных и без эмодзи.
- Начни с сути: цена и действие.
"""

_BINDING = {
    "цель": "конкурентная цель по бюджету въезда в локации",
    "дом": "уровень соседей по дому, приведённый к нашему этажу",
    "пол": "нижняя граница по ценам соседей в доме, приведённым к нашему этажу",
    "шаг": "ограничитель шага снижения за один пересмотр",
    "уже конкурентна": "цена уже ниже всех найденных ориентиров — снижать не от чего",
    "нет ориентира": (
        "ни в доме, ни в локации не нашлось ориентира ниже нашей цены — "
        "снижать её не от чего"
    ),
    "нет данных": "выгрузки по ЖК нет, цена не проверена",
}


def explain_price(report: "LotReport") -> str:
    """Обоснование рекомендованной цены. Единственный текст, который видит менеджер."""
    return _ask(SYSTEM_PRICE, price_payload(report), template_price(report))


def price_payload(r: "LotReport") -> dict:
    """Готовый расчёт для пересказа. Модель получает выводы, а не сырьё."""
    a = r.apartment
    rec = r.recommendation
    return {
        "объект": {
            "жк": a.complex_name.strip(),
            "адрес": a.address,
            "комнат": a.rooms,
            "площадь_м2": a.area,
            "этаж": f"{a.floor} из {a.floors_total}",
            "отделка": a.finish.value,
            "текущая_цена_руб": a.price,
            "текущая_цена_за_м2": round(a.price_per_sqm),
            "дней_в_экспозиции": a.days_on_market,
        },
        "внутри_дома": {
            "лотов_в_экспозиции": r.house_lots,
            "наше_место_по_цене_метра": r.rank_in_house,
            "сопоставимых_по_метражу": len(r.peers),
            "паритет_с_медианой_соседей_процент": (
                round(r.parity_gap * 100, 2) if r.parity_gap is not None else None
            ),
            "надбавка_за_этаж": r.floor_premium.summary if r.floor_premium else None,
        },
        "бюджет_въезда": {
            "доводка_руб_за_м2": r.finishing_cost,
            "бюджет_въезда_руб": r.move_in,
            "метр_готовой_квартиры": round(r.move_in_ppsm),
        },
        "локация": {
            "проектов_в_сравнении": len(r.location),
            "наше_место_по_метру_готовой": r.location_rank,
            "готовых_лотов_дешевле_нашего_бюджета": sum(
                1 for x in r.alternatives if x.ready and x.budget_delta < 0
            ),
            "ближайшие_альтернативы": [
                {
                    "проект": x.project.name if x.project else "—",
                    "площадь_м2": x.comp.area,
                    "бюджет_въезда_руб": x.move_in,
                    "разница_с_нашим_руб": x.budget_delta,
                    "ключи_на_руках": x.ready,
                }
                for x in r.alternatives[:4]
            ],
        },
        "рекомендация": {
            "цена_руб": rec.price,
            "изменение_процент": round(rec.delta_pct * 100, 1),
            "коридор_торга_руб": list(rec.corridor),
            "бюджет_въезда_после_снижения_руб": rec.move_in,
            "уверенность": rec.confidence,
            "что_определило_цену": _BINDING.get(rec.binding, rec.binding),
        },
        "причины": rec.reasons,
        "оговорки": rec.caveats,
    }


def template_price(r: "LotReport") -> str:
    """Fallback без LLM: та же рекомендация, просто суше. Ничего не выдумывает."""
    rec = r.recommendation
    body = [rec.headline, ""]
    body += [f"• {x}" for x in rec.reasons]
    if rec.caveats:
        body += [""] + [f"⚠️ {x}" for x in rec.caveats]
    return "\n".join(body)


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
