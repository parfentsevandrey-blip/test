"""Трендовая стратегия на пересечении двух EMA.

Вход в лонг — когда быстрая EMA пересекает медленную снизу вверх, выход — при
обратном пересечении. Опциональный фильтр тренда (длинная EMA) отсекает сделки
против основного движения: именно они дают большую часть убытков этой схемы на
боковике.

Стопы и тейки живут в `risk.py` — стратегия отвечает только за сигнал.
"""

from __future__ import annotations

from decimal import Decimal
from typing import Any

from ..indicators import closes, crossed_down, crossed_up, ema
from ..types import Decision, DesiredOrder, Side
from .base import Strategy, StrategyContext


class EmaCrossStrategy(Strategy):
    """Пересечение быстрой и медленной EMA — вход по тренду."""

    key = "ema_cross"

    def __init__(self, **params: Any):
        super().__init__(**params)
        p = self._param
        self.fast: int = p(params, "fast", 12)
        self.slow: int = p(params, "slow", 26)
        self.trend: int = p(params, "trend", 0)  # 0 — фильтр выключен
        self.order_quote: Decimal = p(params, "order_quote", Decimal("50"))
        self.equity_pct: Decimal = p(params, "equity_pct", Decimal("0"))

        if self.fast >= self.slow:
            raise ValueError("ema_cross.fast должно быть меньше ema_cross.slow")
        # +2 бара, чтобы было на чём увидеть само пересечение.
        self.warmup = max(self.slow, self.trend) + 2

    def decide(self, ctx: StrategyContext) -> Decision:
        prices = closes(ctx.candles)
        fast = ema(prices, self.fast)
        slow = ema(prices, self.slow)

        if ctx.position.is_open:
            if crossed_down(fast, slow):
                return Decision(flatten=True, note="EMA пересеклись вниз — закрываем")
            return Decision(note="в позиции, сигнала на выход нет")

        if not crossed_up(fast, slow):
            return Decision(note="сигнала на вход нет")

        if self.trend:
            trend_line = ema(prices, self.trend)[-1]
            if trend_line is None or prices[-1] < trend_line:
                return Decision(note="сигнал отклонён фильтром тренда")

        amount = self._entry_amount(ctx)
        if amount <= 0:
            return Decision(note="недостаточно средств для входа")
        return Decision(
            desired=[DesiredOrder(client_id="ema-entry", side=Side.BUY, amount=amount)],
            note="EMA пересеклись вверх — покупаем по рынку",
        )

    def _entry_amount(self, ctx: StrategyContext) -> Decimal:
        """Возвращает сумму в валюте котировки: рыночная покупка на Gate.io
        задаётся именно суммой, а не количеством базовой валюты."""
        budget = ctx.equity * self.equity_pct if self.equity_pct > 0 else self.order_quote
        budget = min(budget, ctx.quote_free)
        if budget < ctx.spec.min_quote_amount:
            return Decimal(0)
        return budget.quantize(Decimal("0.00000001"))
