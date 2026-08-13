"""Возврат к среднему по RSI.

Покупаем, когда RSI выходит из зоны перепроданности (пересекает `oversold`
снизу вверх — то есть падение уже выдыхается), закрываем в зоне перекупленности.
Ждать именно выхода, а не просто «RSI < 30», принципиально: в затяжном падении
RSI держится ниже 30 неделями, и вход «по факту низкого RSI» ловит нож.
"""

from __future__ import annotations

from decimal import Decimal
from typing import Any

from ..indicators import closes, ema, rsi
from ..types import Decision, DesiredOrder, Side
from .base import Strategy, StrategyContext


class RsiReversionStrategy(Strategy):
    """Возврат к среднему: покупка на выходе RSI из перепроданности."""

    key = "rsi_reversion"

    def __init__(self, **params: Any):
        super().__init__(**params)
        p = self._param
        self.period: int = p(params, "period", 14)
        self.oversold: float = float(params.get("oversold", 30))
        self.overbought: float = float(params.get("overbought", 70))
        self.trend: int = p(params, "trend", 0)
        self.order_quote: Decimal = p(params, "order_quote", Decimal("50"))
        self.equity_pct: Decimal = p(params, "equity_pct", Decimal("0"))

        if not 0 < self.oversold < self.overbought < 100:
            raise ValueError("Должно выполняться 0 < oversold < overbought < 100")
        self.warmup = max(self.period + 2, self.trend + 2)

    def decide(self, ctx: StrategyContext) -> Decision:
        prices = closes(ctx.candles)
        values = rsi(prices, self.period)
        if len(values) < 2 or values[-1] is None or values[-2] is None:
            return Decision(note="RSI ещё не рассчитан")

        prev, now = values[-2], values[-1]
        assert prev is not None and now is not None

        if ctx.position.is_open:
            if now >= self.overbought:
                return Decision(flatten=True, note=f"RSI {now:.1f} — фиксируем прибыль")
            return Decision(note=f"в позиции, RSI {now:.1f}")

        if not (prev <= self.oversold < now):
            return Decision(note=f"RSI {now:.1f} — сигнала нет")

        if self.trend:
            trend_line = ema(prices, self.trend)[-1]
            if trend_line is None or prices[-1] < trend_line:
                return Decision(note="сигнал отклонён фильтром тренда")

        amount = self._entry_amount(ctx)
        if amount <= 0:
            return Decision(note="недостаточно средств для входа")
        return Decision(
            desired=[DesiredOrder(client_id="rsi-entry", side=Side.BUY, amount=amount)],
            note=f"RSI вышел из перепроданности ({prev:.1f} -> {now:.1f}) — покупаем",
        )

    def _entry_amount(self, ctx: StrategyContext) -> Decimal:
        budget = ctx.equity * self.equity_pct if self.equity_pct > 0 else self.order_quote
        budget = min(budget, ctx.quote_free)
        if budget < ctx.spec.min_quote_amount:
            return Decimal(0)
        return budget.quantize(Decimal("0.00000001"))
