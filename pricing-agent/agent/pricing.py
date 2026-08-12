"""Ядро вердикта: коридор рынка, позиция объекта, решение снизить / держать / поднять.

Решение принимает детерминированный код, а не языковая модель. LLM подключается позже и
только пересказывает готовые числа (agent/narrative.py). Причина простая: рекомендация по
цене лота за 280 млн ₽ должна быть воспроизводимой и проверяемой строчка за строчкой.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from .adjustments import DEFAULT_CONFIG, PricingConfig, adjust_comp
from .models import (
    Action,
    AdjustedComp,
    Apartment,
    Comp,
    HouseValuation,
    Scenario,
    Verdict,
)


@dataclass
class DecisionConfig:
    """Пороги принятия решения. Отделены от поправок: это политика продаж, а не оценка."""

    min_comps: float = 3.0             # эффективное число аналогов ниже — ручная оценка
    cut_percentile_trigger: float = 75.0
    cut_hard_trigger: float = 85.0     # верхний дециль рынка — сигнал сам по себе
    raise_percentile_trigger: float = 35.0
    target_percentile_after_cut: float = 60.0
    target_percentile_after_raise: float = 50.0
    slow_market_dom_ratio: float = 1.5  # DOM выше медианы аналогов во столько раз = «висит»

    # Ограничители: агент не должен «раскачивать» цену.
    max_cut_step: float = 0.07
    max_raise_step: float = 0.05
    cooldown_days: int = 21
    no_raise_if_dom_over: int = 90

    # Базовый срок экспозиции, дней, если по аналогам его посчитать нельзя.
    # Ориентир по премиум-сегменту Москвы 2026: 45–60 дней.
    base_days_on_market: int = 55
    exposure_elasticity: float = 0.9   # чувствительность срока продажи к позиции в коридоре


DEFAULT_DECISION = DecisionConfig()


def weighted_percentile(values: list[float], weights: list[float], q: float) -> float:
    """Взвешенный перцентиль, q в 0..100. Линейная интерполяция по накопленному весу."""
    if not values:
        raise ValueError("пустая выборка")
    pairs = sorted(zip(values, weights))
    total = sum(w for _, w in pairs)
    if total <= 0:
        return pairs[len(pairs) // 2][0]

    target = q / 100 * total
    acc = 0.0
    for i, (v, w) in enumerate(pairs):
        prev = acc
        acc += w
        if acc >= target:
            if i == 0 or acc == prev:
                return v
            prev_v = pairs[i - 1][0]
            frac = (target - prev) / (acc - prev)
            return prev_v + (v - prev_v) * frac
    return pairs[-1][0]


def percentile_of(values: list[float], weights: list[float], x: float) -> float:
    """Обратная задача: в каком перцентиле распределения лежит наша цена."""
    total = sum(weights)
    if total <= 0:
        return 50.0
    below = sum(w for v, w in zip(values, weights) if v < x)
    equal = sum(w for v, w in zip(values, weights) if v == x)
    return (below + equal / 2) / total * 100


def _round_step(price: float) -> int:
    return 1_000_000 if price >= 100_000_000 else 500_000


def round_price(price: float) -> int:
    """Округление до «переговорной» цены: премиум не выставляют по 103 748 291 ₽."""
    step = _round_step(price)
    return int(round(price / step) * step)


def round_price_up(price: float) -> int:
    step = _round_step(price)
    return int(-(-price // step) * step)


def round_price_down(price: float) -> int:
    step = _round_step(price)
    return int(price // step * step)


def evaluate(
    apartment: Apartment,
    comps: list[Comp],
    cfg: PricingConfig = DEFAULT_CONFIG,
    decision: DecisionConfig = DEFAULT_DECISION,
    baseline: HouseValuation | None = None,
) -> Verdict:
    """Главная функция. Аналоги → коридор → вердикт с ограничителями и сценариями.

    `baseline` — независимая оценка ₽/м² по дому (у Яндекс Недвижимости это
    ML-калькулятор «Оценка квартиры»). На вердикт она не влияет: расхождение с нашим
    коридором — повод проверить выборку аналогов, а не подвинуть цену.
    """
    adjusted = [adjust_comp(apartment, c, cfg) for c in comps]
    adjusted.sort(key=lambda a: a.weight, reverse=True)

    signals: list[str] = []
    warnings: list[str] = []

    if not adjusted:
        return _manual_verdict(
            apartment, [], ["Аналоги не найдены — рынок не проанализирован."], baseline=baseline
        )

    values = [a.adjusted_price_per_sqm for a in adjusted]
    weights = [a.weight for a in adjusted]
    n_eff = sum(weights)

    p25 = weighted_percentile(values, weights, 25)
    p50 = weighted_percentile(values, weights, 50)
    p75 = weighted_percentile(values, weights, 75)
    our = apartment.price_per_sqm
    pct = percentile_of(values, weights, our)

    same_complex = [a for a in adjusted if a.comp.same_complex]
    signals.append(
        f"Аналогов: {len(adjusted)} (из них {len(same_complex)} в том же ЖК), "
        f"эффективный вес {n_eff:.1f}"
    )
    signals.append(
        f"Коридор рынка: {p25 / 1000:.0f} — {p50 / 1000:.0f} — {p75 / 1000:.0f} тыс ₽/м²; "
        f"наша цена {our / 1000:.0f} тыс ₽/м² → {pct:.0f}-й перцентиль"
    )

    # --- Рыночные сигналы --------------------------------------------------
    doms = [a.comp.days_on_market for a in adjusted if a.comp.days_on_market is not None]
    median_dom = sorted(doms)[len(doms) // 2] if doms else None
    our_dom = apartment.days_on_market

    slow = False
    if median_dom is not None and our_dom is not None:
        slow = our_dom > median_dom * decision.slow_market_dom_ratio
        signals.append(
            f"Экспозиция: наш лот {our_dom} дн., медиана по аналогам {median_dom} дн."
            + (" — висим дольше рынка" if slow else "")
        )
    elif our_dom is not None:
        signals.append(f"Экспозиция: {our_dom} дн. (сравнить не с чем — у аналогов нет дат)")
    else:
        warnings.append(
            "Нет даты выхода в экспозицию — срок продажи в расчёте не участвует. "
            "Это главный недостающий сигнал."
        )

    # Сверка с независимой оценкой по дому. Мы строили коридор по своим аналогам и
    # своим поправкам; если внешняя оценка сильно расходится — скорее всего дело в
    # выборке аналогов, и это надо увидеть до, а не после разговора с собственником.
    if baseline is not None:
        gap = baseline.price_per_sqm / p50 - 1
        signals.append(
            f"Оценка по дому ({baseline.source}): {baseline.price_per_sqm / 1000:.0f} тыс ₽/м² "
            f"против нашей медианы {p50 / 1000:.0f} — расхождение {gap:+.1%}"
        )
        if abs(gap) > 0.15:
            warnings.append(
                f"Наш коридор расходится с оценкой по дому на {gap:+.1%}. "
                "Проверьте выборку аналогов до применения рекомендации: "
                "скорее всего в неё попали лоты другого класса или метража."
            )

    cutters = [a for a in adjusted if (a.comp.price_cut_pct or 0) > 0]
    if cutters:
        share = len(cutters) / len(adjusted)
        avg_cut = sum(a.comp.price_cut_pct or 0 for a in cutters) / len(cutters)
        signals.append(
            f"Снижали цену {len(cutters)} из {len(adjusted)} аналогов "
            f"(в среднем −{avg_cut:.1%}) — рынок торгуется вниз"
        )
        if share >= 0.5:
            slow = True

    demand_weak = apartment.viewings_30d is not None and apartment.viewings_30d <= 1
    if apartment.viewings_30d is not None:
        signals.append(f"Показов за 30 дней: {apartment.viewings_30d}")

    # --- Уверенность -------------------------------------------------------
    confidence = min(1.0, n_eff / 6) * 0.5
    confidence += (sum(a.weight for a in same_complex) / n_eff if n_eff else 0) * 0.3
    confidence += 0.2 if doms else 0.0
    if our_dom is None:
        confidence *= 0.85
    confidence = round(min(confidence, 0.95), 2)

    # --- Решение -----------------------------------------------------------
    if n_eff < decision.min_comps:
        warnings.append(
            f"Эффективное число аналогов {n_eff:.1f} < {decision.min_comps:.0f} — "
            "статистике доверять нельзя, нужен ручной разбор."
        )
        return _manual_verdict(
            apartment, adjusted, warnings, signals, confidence, (p25, p50, p75), pct, baseline
        )

    action = Action.HOLD
    target_ppsm = our
    reason = ""

    if pct >= decision.cut_percentile_trigger and (slow or demand_weak):
        action = Action.CUT
        target_ppsm = weighted_percentile(values, weights, decision.target_percentile_after_cut)
        reason = (
            f"цена в верхней четверти рынка ({pct:.0f}-й перцентиль) при слабом спросе — "
            f"сдвигаем к {decision.target_percentile_after_cut:.0f}-му перцентилю"
        )
    elif pct >= decision.cut_hard_trigger:
        action = Action.CUT
        target_ppsm = weighted_percentile(values, weights, 70)
        reason = (
            f"цена в верхнем дециле рынка ({pct:.0f}-й перцентиль) — даже без данных о спросе "
            "это ограничивает поток обращений"
        )
    elif (
        pct <= decision.raise_percentile_trigger
        and not slow
        and (our_dom is None or our_dom <= decision.no_raise_if_dom_over)
    ):
        action = Action.RAISE
        target_ppsm = weighted_percentile(values, weights, decision.target_percentile_after_raise)
        reason = (
            f"цена в нижней трети рынка ({pct:.0f}-й перцентиль) при нормальной экспозиции — "
            "мы недооценены относительно аналогов"
        )
    elif pct <= decision.raise_percentile_trigger:
        # Цена низкая, но подъём не прошёл фильтр — важно сказать, что именно помешало.
        blockers = []
        if slow:
            blockers.append("лот идёт медленнее рынка")
        if our_dom is not None and our_dom > decision.no_raise_if_dom_over:
            blockers.append(f"экспозиция {our_dom} дн.")
        reason = (
            f"цена в нижней трети рынка ({pct:.0f}-й перцентиль), но поднимать нельзя: "
            + ", ".join(blockers)
            + ". Сначала нужен спрос — работаем с показами, а не с ценой"
        )
    else:
        reason = f"цена внутри рыночного коридора ({pct:.0f}-й перцентиль), резких сигналов нет"

    signals.append(f"Основание: {reason}")

    raw_price = target_ppsm * apartment.area
    price, guard_notes = _apply_guardrails(apartment, raw_price, action, our_dom, decision)
    warnings.extend(guard_notes)

    if price == apartment.price:
        action = Action.HOLD

    delta = price / apartment.price - 1
    scenarios = _scenarios(apartment, values, weights, median_dom, decision)

    return Verdict(
        apartment=apartment,
        action=action,
        recommended_price=price,
        delta_pct=delta,
        corridor=(p25, p50, p75),
        our_percentile=pct,
        confidence=confidence,
        comps=adjusted,
        signals=signals,
        warnings=warnings,
        scenarios=scenarios,
        baseline=baseline,
    )


def _apply_guardrails(
    apartment: Apartment,
    raw_price: float,
    action: Action,
    our_dom: int | None,
    decision: DecisionConfig,
) -> tuple[int, list[str]]:
    """Ограничители: шаг цены, частота изменений, запрет на подъём по «висящему» лоту."""
    notes: list[str] = []
    price = raw_price

    if action is Action.CUT:
        floor_price = apartment.price * (1 - decision.max_cut_step)
        if price < floor_price:
            notes.append(
                f"Рынок допускает снижение сильнее, но шаг ограничен "
                f"{decision.max_cut_step:.0%} за раз — снижаем поэтапно и смотрим на отклик."
            )
            price = floor_price
    elif action is Action.RAISE:
        cap = apartment.price * (1 + decision.max_raise_step)
        if price > cap:
            notes.append(f"Подъём ограничен {decision.max_raise_step:.0%} за шаг.")
            price = cap
        if our_dom is not None and our_dom > decision.no_raise_if_dom_over:
            notes.append(
                f"Подъём отменён: лот в экспозиции {our_dom} дн. "
                f"(> {decision.no_raise_if_dom_over}) — сначала нужен спрос."
            )
            price = apartment.price

    if apartment.last_price_change is not None:
        since = (date.today() - apartment.last_price_change).days
        if since < decision.cooldown_days and action is not Action.HOLD:
            notes.append(
                f"Цену меняли {since} дн. назад (кулдаун {decision.cooldown_days} дн.) — "
                "рекомендация записана, но применять рано: рынок ещё не отреагировал."
            )
            price = apartment.price

    # Округление идёт в сторону ограничителя: иначе «красивая» цена пробивает шаг
    # (127 млн − 7% = 118.11 → 118 млн даёт −7.1% при лимите 7%).
    rounded = round_price(price)
    if action is Action.CUT:
        rounded = max(rounded, round_price_up(apartment.price * (1 - decision.max_cut_step)))
    elif action is Action.RAISE:
        rounded = min(rounded, round_price_down(apartment.price * (1 + decision.max_raise_step)))
    return rounded, notes


def _scenarios(
    apartment: Apartment,
    values: list[float],
    weights: list[float],
    median_dom: int | None,
    decision: DecisionConfig,
) -> list[Scenario]:
    """Три точки «цена ↔ ожидаемый срок продажи».

    Срок — эвристика: T = T_база · exp(k · (перцентиль − 50)/50). Не прогноз, а способ
    показать собственнику цену промедления в днях, а не в процентах.
    """
    base_dom = median_dom or decision.base_days_on_market
    plan = [
        ("Быстрая продажа", 35.0, "выход на сделку за 1–1,5 месяца, цена ниже медианы"),
        ("Базовый", 55.0, "рыночный темп, цена около медианы"),
        ("Максимум цены", 80.0, "верх коридора: держим цену, готовы ждать"),
    ]
    out: list[Scenario] = []
    for name, q, comment in plan:
        ppsm = weighted_percentile(values, weights, q)
        days = base_dom * pow(2.718281828, decision.exposure_elasticity * (q - 50) / 50)
        out.append(
            Scenario(
                name=name,
                price=round_price(ppsm * apartment.area),
                expected_days=int(round(days)),
                comment=comment,
            )
        )
    return out


def _manual_verdict(
    apartment: Apartment,
    adjusted: list[AdjustedComp],
    warnings: list[str],
    signals: list[str] | None = None,
    confidence: float = 0.2,
    corridor: tuple[float, float, float] | None = None,
    pct: float = 50.0,
    baseline: HouseValuation | None = None,
) -> Verdict:
    ppsm = apartment.price_per_sqm
    return Verdict(
        apartment=apartment,
        action=Action.MANUAL,
        recommended_price=apartment.price,
        delta_pct=0.0,
        corridor=corridor or (ppsm * 0.85, ppsm, ppsm * 1.15),
        our_percentile=pct,
        confidence=min(confidence, 0.35),
        comps=adjusted,
        signals=signals or [],
        warnings=warnings,
        scenarios=[],
        baseline=baseline,
    )
