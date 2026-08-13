"""Сеточный бот: лестница лимитных ордеров в заданном коридоре.

Идея: разбить диапазон [lower, upper] на уровни, ниже текущей цены держать
покупки, выше — продажи. Каждое колебание внутри коридора даёт покупку внизу и
продажу вверху, разница минус комиссии — прибыль. Стратегия зарабатывает на
боковике и теряет на сильном тренде, поэтому у неё есть `exit_on_break`.

Лестница пересчитывается целиком на каждом тике; движок сам поймёт, какие
ордера уже стоят, какие снять и какие доставить.
"""

from __future__ import annotations

from decimal import Decimal
from typing import Any

from ..types import Decision, DesiredOrder, Side
from .base import Strategy, StrategyContext


class GridStrategy(Strategy):
    """Сетка лимитных ордеров в коридоре — заработок на боковике."""

    key = "grid"
    warmup = 1

    def __init__(self, **params: Any):
        super().__init__(**params)
        p = self._param
        self.levels: int = p(params, "levels", 10)
        self.order_quote: Decimal = p(params, "order_quote", Decimal("20"))
        self.lower: Decimal | None = (
            Decimal(str(params["lower"])) if params.get("lower") is not None else None
        )
        self.upper: Decimal | None = (
            Decimal(str(params["upper"])) if params.get("upper") is not None else None
        )
        # Если границы не заданы, строим коридор ±range_pct вокруг цены запуска.
        self.range_pct: Decimal = p(params, "range_pct", Decimal("0.05"))
        self.geometric: bool = p(params, "geometric", True)
        self.max_orders_per_side: int = p(params, "max_orders_per_side", 5)
        self.exit_on_break: bool = p(params, "exit_on_break", False)
        # Зазор вокруг цены: без него ордер, выставленный вплотную к рынку,
        # исполнится мгновенно как тейкер и съест спред комиссией.
        self.min_gap_pct: Decimal = p(params, "min_gap_pct", Decimal("0.001"))

        if self.levels < 2:
            raise ValueError("grid.levels должно быть >= 2")
        if self.lower is not None and self.upper is not None and self.lower >= self.upper:
            raise ValueError("grid.lower должно быть меньше grid.upper")

        self._grid: list[Decimal] | None = None

    def decide(self, ctx: StrategyContext) -> Decision:
        grid = self._build_grid(ctx.price)
        low, high = grid[0], grid[-1]

        if ctx.price < low or ctx.price > high:
            note = f"цена {ctx.price} вне коридора [{low}, {high}]"
            if self.exit_on_break:
                return Decision(desired=[], flatten=True, note=f"{note} — выходим")
            return Decision(desired=[], note=f"{note} — ждём возврата")

        gap = ctx.price * self.min_gap_pct
        buy_cap = ctx.quote_free
        sell_cap = ctx.base_free
        desired: list[DesiredOrder] = []

        # Покупки: от ближайшего уровня вниз, пока хватает котировочной валюты.
        buys = [p for p in grid if p < ctx.price - gap]
        for i, price in enumerate(sorted(buys, reverse=True)[: self.max_orders_per_side]):
            price = ctx.spec.quantize_price(price)
            amount = ctx.spec.quantize_amount(self.order_quote / price)
            cost = amount * price
            if cost > buy_cap or not ctx.spec.is_tradable(amount, price):
                break
            buy_cap -= cost
            desired.append(
                DesiredOrder(client_id=f"grid-b{i}", side=Side.BUY, amount=amount, price=price)
            )

        # Продажи: только в пределах реально имеющейся базовой валюты.
        sells = [p for p in grid if p > ctx.price + gap]
        for i, price in enumerate(sorted(sells)[: self.max_orders_per_side]):
            price = ctx.spec.quantize_price(price)
            amount = ctx.spec.quantize_amount(min(self.order_quote / price, sell_cap))
            if not ctx.spec.is_tradable(amount, price):
                break
            sell_cap -= amount
            desired.append(
                DesiredOrder(client_id=f"grid-s{i}", side=Side.SELL, amount=amount, price=price)
            )

        return Decision(
            desired=desired,
            note=f"сетка {low}–{high}: {len(buys)} покупок / {len(sells)} продаж возможно",
        )

    def _build_grid(self, price: Decimal) -> list[Decimal]:
        """Уровни считаются один раз: подвижная сетка гоняла бы ордера за ценой."""
        if self._grid is not None:
            return self._grid

        lower = self.lower if self.lower is not None else price * (1 - self.range_pct)
        upper = self.upper if self.upper is not None else price * (1 + self.range_pct)

        if self.geometric:
            # Равные шаги в процентах — уровни внизу гуще, что логичнее для цены.
            ratio = (upper / lower) ** (Decimal(1) / Decimal(self.levels - 1))
            grid = [lower * ratio**i for i in range(self.levels)]
        else:
            step = (upper - lower) / (self.levels - 1)
            grid = [lower + step * i for i in range(self.levels)]

        self._grid = grid
        return grid
