"""Этажная надбавка, измеренная по дому, а не взятая из справочника.

Обычная оценочная практика — брать надбавку за этаж из таблицы («верхний этаж
+10% к первому»). Для конкретного дома это грубо: в одном доме вид с 20-го этажа
стоит дорого, в другом все окна выходят в стену соседнего корпуса.

Если в выгрузке есть лоты застройщика — а это единый прайс-лист, составленный по
одной формуле, — надбавку можно не предполагать, а измерить: подогнать регрессию
ln(₽/м²) по этажу. Логарифм потому, что надбавка мультипликативная: «+0,8% за этаж»,
а не «+6 тыс ₽ за этаж».

R² здесь — не украшение, а условие применения. Если этаж объясняет мало разброса
(в доме есть террасы, двухуровневые, видовые), измеренная надбавка бессмысленна,
и модель честно отказывается в пользу конфига.
"""

from __future__ import annotations

import math
from dataclasses import dataclass

from .models import Comp

MIN_LOTS = 6          # меньше — регрессия неустойчива
MIN_FLOOR_SPREAD = 4  # лоты на двух соседних этажах наклон не определяют
MIN_R2 = 0.30         # ниже — этаж не объясняет цену в этом доме


@dataclass
class FloorPremium:
    """Надбавка за этаж: сколько, по чему измерена и можно ли ей верить."""

    rate: float            # доля на этаж, компаундом: 0.0078 = +0,78%
    r2: float
    lots: int
    measured: bool         # True — измерено по дому, False — взято из конфига
    source: str

    def adjust(self, ppsm: float, from_floor: int, to_floor: int) -> float:
        """Приводит цену метра с этажа from_floor к этажу to_floor."""
        return ppsm * (1 + self.rate) ** (to_floor - from_floor)

    @property
    def summary(self) -> str:
        if not self.measured:
            return f"надбавка за этаж {self.rate:+.2%} (из конфига: {self.source})"
        return (
            f"надбавка за этаж {self.rate:+.2%}, измерена по {self.lots} лотам "
            f"{self.source} (R² = {self.r2:.2f})"
        )


def fit_floor_premium(
    comps: list[Comp],
    *,
    fallback_rate: float,
    prefer_seller: str = "застройщик",
) -> FloorPremium:
    """Подгоняет надбавку за этаж по лотам одного дома.

    Приоритет — лоты застройщика: это единый прайс-лист, где этаж заложен формулой,
    а не мнением семи разных собственников. Если их мало, берётся вся выборка,
    но требования к R² те же.
    """
    developer = [c for c in comps if prefer_seller in (c.seller_type or "").lower()]
    pool, source = (
        (developer, "прайса застройщика")
        if len(developer) >= MIN_LOTS
        else (comps, "экспозиции дома")
    )

    usable = [c for c in pool if c.floor and c.price_per_sqm > 0]
    floors = {c.floor for c in usable}
    if len(usable) < MIN_LOTS or (max(floors) - min(floors) if floors else 0) < MIN_FLOOR_SPREAD:
        return FloorPremium(fallback_rate, 0.0, len(usable), False, "недостаточно лотов")

    xs = [float(c.floor) for c in usable]
    ys = [math.log(c.price_per_sqm) for c in usable]
    slope, r2 = _linear_fit(xs, ys)
    if r2 < MIN_R2:
        return FloorPremium(
            fallback_rate, r2, len(usable), False, f"низкий R² по {source}"
        )

    # slope — прирост ln(цены) на этаж; обратно в долю: e^slope − 1.
    return FloorPremium(math.exp(slope) - 1, r2, len(usable), True, source)


def _linear_fit(xs: list[float], ys: list[float]) -> tuple[float, float]:
    """Метод наименьших квадратов + R². Без numpy: зависимость того не стоит."""
    n = len(xs)
    mx = sum(xs) / n
    my = sum(ys) / n
    sxx = sum((x - mx) ** 2 for x in xs)
    if sxx == 0:
        return 0.0, 0.0
    sxy = sum((x - mx) * (y - my) for x, y in zip(xs, ys))
    slope = sxy / sxx
    intercept = my - slope * mx

    ss_tot = sum((y - my) ** 2 for y in ys)
    ss_res = sum((y - (slope * x + intercept)) ** 2 for x, y in zip(xs, ys))
    r2 = 1 - ss_res / ss_tot if ss_tot else 0.0
    return slope, max(0.0, r2)
