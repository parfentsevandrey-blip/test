"""Поправки сравнительного подхода.

Каждый аналог приводится к характеристикам оцениваемой квартиры, после чего сравниваются
уже сопоставимые ₽/м². Все коэффициенты вынесены в CONFIG: это не «магия модели», а
допущения оценщика, которые нужно защищать перед клиентом и калибровать по своим сделкам.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field

from .models import Adjustment, AdjustedComp, Apartment, Comp, Finish


@dataclass
class PricingConfig:
    """Настройки движка. Меняются без правки кода — по мере накопления своих сделок."""

    # Этажная поправка: во сколько раз дороже верхний этаж относительно первого.
    # 0.10 = верхний этаж +10% к первому, линейно по относительной высоте.
    floor_premium: float = 0.10

    # Эластичность ₽/м² по площади: чем крупнее лот, тем дешевле метр.
    # adj = -elasticity * ln(S_объекта / S_аналога).
    area_elasticity: float = 0.12

    # Вклад отделки в ₽/м², тыс. ₽. Разница тиров и есть поправка.
    finish_value_per_sqm: dict[Finish, float] = field(
        default_factory=lambda: {
            Finish.NONE: 0,
            Finish.WHITEBOX: 60_000,
            Finish.DEVELOPER: 120_000,
            Finish.DESIGNER: 300_000,
            Finish.DELUXE: 550_000,
        }
    )

    # Стоимость машино-места, ₽. Дефолт откалиброван по самому реестру:
    # Кутузовский XII — 158 млн с местом против 152 млн без = 6 млн.
    parking_value: int = 6_000_000
    parking_value_by_complex: dict[str, int] = field(
        default_factory=lambda: {
            "Золотой жилой квартал": 15_000_000,   # Софийская наб., подземный паркинг в центре
            "Кутузовский XII": 6_000_000,
        }
    )

    # Типичный торг в премиум-сегменте: на сколько цена сделки ниже цены экспозиции.
    # Нужен, чтобы сопоставлять закрытые сделки с текущими объявлениями.
    bargain_discount: float = 0.08

    # Веса аналогов
    weight_same_complex: float = 1.0
    weight_same_class_nearby: float = 0.6
    weight_far: float = 0.3
    weight_closed_deal_bonus: float = 1.3
    recency_halflife_days: float = 90.0
    area_similarity_k: float = 3.0

    def parking_price(self, complex_name: str) -> int:
        return self.parking_value_by_complex.get(complex_name, self.parking_value)


DEFAULT_CONFIG = PricingConfig()


def adjust_comp(
    subject: Apartment, comp: Comp, cfg: PricingConfig = DEFAULT_CONFIG
) -> AdjustedComp:
    """Приводит аналог к характеристикам оцениваемой квартиры.

    Работаем в пространстве цен экспозиции: закрытые сделки поднимаются на величину
    типичного торга, чтобы не смешивать «сколько просят» и «за сколько купили».
    """
    adjustments: list[Adjustment] = []

    # 1. Машино-место — абсолютная величина, снимается с цены лота до перевода в ₽/м².
    price = float(comp.price)
    if comp.has_parking != subject.has_parking:
        parking = cfg.parking_price(comp.complex_name)
        delta = -parking if comp.has_parking else +parking
        price += delta
        adjustments.append(
            Adjustment(
                name="машино-место",
                pct=delta / comp.price,
                explanation=(
                    f"у аналога место {'входит' if comp.has_parking else 'не входит'} в лот, "
                    f"у нас — наоборот: {delta / 1e6:+.1f} млн ₽"
                ),
            )
        )

    ppsm = price / comp.area

    # 2. Этаж — по относительной высоте, чтобы сравнивать дома разной этажности.
    floor_delta = subject.floor_ratio - _floor_ratio(comp)
    if abs(floor_delta) > 1e-9:
        pct = cfg.floor_premium * floor_delta
        ppsm *= 1 + pct
        adjustments.append(
            Adjustment(
                name="этаж",
                pct=pct,
                explanation=(
                    f"{comp.floor}/{comp.floors_total} → {subject.floor}/{subject.floors_total}: "
                    f"{pct:+.1%}"
                ),
            )
        )

    # 3. Площадь — крупный лот стоит дешевле в пересчёте на метр.
    if abs(subject.area - comp.area) > 0.5:
        pct = -cfg.area_elasticity * math.log(subject.area / comp.area)
        ppsm *= 1 + pct
        adjustments.append(
            Adjustment(
                name="площадь",
                pct=pct,
                explanation=f"{comp.area:g} → {subject.area:g} м²: {pct:+.1%}",
            )
        )

    # 4. Отделка — разница вклада в ₽/м².
    finish_delta = (
        cfg.finish_value_per_sqm[subject.finish] - cfg.finish_value_per_sqm[comp.finish]
    )
    if finish_delta:
        pct = finish_delta / ppsm
        ppsm += finish_delta
        adjustments.append(
            Adjustment(
                name="отделка",
                pct=pct,
                explanation=(
                    f"{comp.finish.value} → {subject.finish.value}: "
                    f"{finish_delta / 1000:+.0f} тыс ₽/м²"
                ),
            )
        )

    # 5. Сделка → экспозиция: возвращаем торг, чтобы сравнивать с ценами предложения.
    if comp.is_closed_deal:
        pct = 1 / (1 - cfg.bargain_discount) - 1
        ppsm *= 1 + pct
        adjustments.append(
            Adjustment(
                name="сделка → экспозиция",
                pct=pct,
                explanation=f"цена сделки приведена к цене предложения (+{pct:.1%} типичного торга)",
            )
        )

    return AdjustedComp(
        comp=comp,
        adjustments=adjustments,
        adjusted_price_per_sqm=ppsm,
        weight=comp_weight(subject, comp, cfg),
    )


def comp_weight(subject: Apartment, comp: Comp, cfg: PricingConfig = DEFAULT_CONFIG) -> float:
    """Вес аналога: чем он ближе, свежее и похожее по размеру — тем больше влияет."""
    if comp.same_complex:
        w = cfg.weight_same_complex
    elif comp.distance_km <= 1.5:
        w = cfg.weight_same_class_nearby
    else:
        w = cfg.weight_far

    if comp.is_closed_deal:
        w *= cfg.weight_closed_deal_bonus

    if comp.observed_at is not None:
        from datetime import date

        age = max((date.today() - comp.observed_at).days, 0)
        w *= 0.5 ** (age / cfg.recency_halflife_days)

    # Сильно другой метраж — другой покупатель, даже в том же доме.
    w *= math.exp(-cfg.area_similarity_k * abs(math.log(subject.area / comp.area)))
    return w


def _floor_ratio(comp: Comp) -> float:
    if comp.floors_total <= 1:
        return 0.5
    return (comp.floor - 1) / (comp.floors_total - 1)
