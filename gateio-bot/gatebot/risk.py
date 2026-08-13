"""Риск-менеджмент — единственное место, где решение стратегии может быть
отменено или урезано.

Правила намеренно отделены от стратегий: стоп-лосс должен работать одинаково,
какая бы логика входа ни стояла сверху, и его нельзя «случайно забыть» в новой
стратегии.

Порядок проверок:
1. Аварийная остановка (дневной убыток / просадка) — жёстче всего, закрывает
   позицию и запрещает новые входы до перезапуска.
2. Защита позиции (стоп-лосс, тейк-профит, трейлинг) — может форсировать выход.
3. Ограничение размера — урезает желаемые покупки до допустимого объёма.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from decimal import Decimal
from typing import Optional

from .strategies.base import StrategyContext
from .types import Decision, DesiredOrder, Side

log = logging.getLogger(__name__)


@dataclass
class RiskLimits:
    """Все пороги — доли единицы: 0.02 == 2%. 0 или None выключает правило."""

    stop_loss_pct: Optional[Decimal] = None
    take_profit_pct: Optional[Decimal] = None
    trailing_stop_pct: Optional[Decimal] = None
    max_position_quote: Optional[Decimal] = None
    max_position_pct: Optional[Decimal] = None
    max_daily_loss_pct: Optional[Decimal] = None
    max_drawdown_pct: Optional[Decimal] = None
    max_open_orders: int = 20

    @classmethod
    def from_dict(cls, raw: dict) -> "RiskLimits":
        def dec(key: str) -> Optional[Decimal]:
            value = raw.get(key)
            if value in (None, "", 0, "0"):
                return None
            return Decimal(str(value))

        return cls(
            stop_loss_pct=dec("stop_loss_pct"),
            take_profit_pct=dec("take_profit_pct"),
            trailing_stop_pct=dec("trailing_stop_pct"),
            max_position_quote=dec("max_position_quote"),
            max_position_pct=dec("max_position_pct"),
            max_daily_loss_pct=dec("max_daily_loss_pct"),
            max_drawdown_pct=dec("max_drawdown_pct"),
            max_open_orders=int(raw.get("max_open_orders", 20)),
        )


@dataclass
class RiskState:
    """Переживает перезапуск бота через `state.py`."""

    peak_equity: Decimal = Decimal(0)
    day_start_equity: Decimal = Decimal(0)
    day_key: str = ""
    position_peak: Decimal = Decimal(0)
    halted: bool = False
    halt_reason: str = ""


@dataclass
class RiskVerdict:
    decision: Decision
    reasons: list[str] = field(default_factory=list)


class RiskManager:
    def __init__(self, limits: RiskLimits, state: Optional[RiskState] = None):
        self.limits = limits
        self.state = state or RiskState()

    # ---------------------------------------------------------------- Проверки

    def review(self, ctx: StrategyContext, decision: Decision) -> RiskVerdict:
        reasons: list[str] = []
        self._track_equity(ctx)

        halt = self._check_halt(ctx)
        if halt:
            if not self.state.halted:
                log.error("АВАРИЙНАЯ ОСТАНОВКА: %s", halt)
            self.state.halted = True
            self.state.halt_reason = halt
            # Позицию закрываем, новые ордера не выставляем.
            return RiskVerdict(Decision(flatten=ctx.position.is_open, note=halt), [halt])

        exit_reason = self._check_position_exit(ctx)
        if exit_reason:
            return RiskVerdict(Decision(flatten=True, note=exit_reason), [exit_reason])

        capped = self._cap_exposure(ctx, decision, reasons)

        if len(capped.desired) > self.limits.max_open_orders:
            reasons.append(
                f"ордеров {len(capped.desired)} > лимита {self.limits.max_open_orders} — обрезано"
            )
            capped.desired = capped.desired[: self.limits.max_open_orders]

        return RiskVerdict(capped, reasons)

    def _track_equity(self, ctx: StrategyContext) -> None:
        st = self.state
        if ctx.equity > st.peak_equity:
            st.peak_equity = ctx.equity

        day = _day_key(ctx.now)
        if st.day_key != day:
            st.day_key = day
            st.day_start_equity = ctx.equity

        if ctx.position.is_open:
            st.position_peak = max(st.position_peak, ctx.price)
        else:
            st.position_peak = Decimal(0)

    def _check_halt(self, ctx: StrategyContext) -> str:
        if self.state.halted:
            return self.state.halt_reason

        lim = self.limits
        st = self.state
        if lim.max_daily_loss_pct and st.day_start_equity > 0:
            loss = (st.day_start_equity - ctx.equity) / st.day_start_equity
            if loss >= lim.max_daily_loss_pct:
                return (
                    f"дневной убыток {loss:.2%} достиг лимита {lim.max_daily_loss_pct:.2%}"
                )

        if lim.max_drawdown_pct and st.peak_equity > 0:
            dd = (st.peak_equity - ctx.equity) / st.peak_equity
            if dd >= lim.max_drawdown_pct:
                return f"просадка {dd:.2%} достигла лимита {lim.max_drawdown_pct:.2%}"

        return ""

    def _check_position_exit(self, ctx: StrategyContext) -> str:
        pos = ctx.position
        if not pos.is_open or pos.avg_entry <= 0:
            return ""

        lim = self.limits
        change = (ctx.price - pos.avg_entry) / pos.avg_entry

        if lim.stop_loss_pct and change <= -lim.stop_loss_pct:
            return f"стоп-лосс: {change:.2%} от входа {pos.avg_entry}"

        if lim.take_profit_pct and change >= lim.take_profit_pct:
            return f"тейк-профит: {change:.2%} от входа {pos.avg_entry}"

        if lim.trailing_stop_pct and self.state.position_peak > 0:
            drop = (self.state.position_peak - ctx.price) / self.state.position_peak
            # Трейлинг включаем только выше входа, иначе он дублирует стоп-лосс.
            if drop >= lim.trailing_stop_pct and self.state.position_peak > pos.avg_entry:
                return (
                    f"трейлинг-стоп: -{drop:.2%} от максимума {self.state.position_peak}"
                )

        return ""

    def _cap_exposure(
        self, ctx: StrategyContext, decision: Decision, reasons: list[str]
    ) -> Decision:
        """Не дать стратегии набрать позицию больше разрешённой."""
        lim = self.limits
        caps = [c for c in (lim.max_position_quote,
                            ctx.equity * lim.max_position_pct if lim.max_position_pct else None)
                if c is not None]
        if not caps or decision.flatten:
            return decision

        cap = min(caps)
        current = ctx.position.amount * ctx.price
        room = cap - current
        if room <= 0:
            kept = [o for o in decision.desired if o.side is Side.SELL]
            if len(kept) != len(decision.desired):
                reasons.append(f"позиция {current:.2f} достигла лимита {cap:.2f} — покупки сняты")
            return Decision(desired=kept, flatten=False, note=decision.note)

        trimmed: list[DesiredOrder] = []
        for order in decision.desired:
            if order.side is Side.SELL:
                trimmed.append(order)
                continue
            # У рыночной покупки amount — это сумма в котировке, у лимитной —
            # объём базовой валюты; приводим к общей мере, чтобы сравнить с room.
            value = order.amount if order.price is None else order.amount * order.price
            if value <= room:
                room -= value
                trimmed.append(order)
                continue
            if room <= 0:
                continue
            scaled = room if order.price is None else ctx.spec.quantize_amount(room / order.price)
            if order.price is not None and not ctx.spec.is_tradable(scaled, order.price):
                continue
            reasons.append(f"{order.client_id} урезан до {scaled} по лимиту позиции")
            trimmed.append(
                DesiredOrder(order.client_id, order.side, scaled, order.price)
            )
            room = Decimal(0)

        return Decision(desired=trimmed, flatten=False, note=decision.note)

    def resume(self) -> None:
        """Снять аварийную остановку вручную (команда `reset`)."""
        self.state.halted = False
        self.state.halt_reason = ""


def _day_key(ts: int) -> str:
    import datetime as _dt

    return _dt.datetime.fromtimestamp(ts, _dt.timezone.utc).strftime("%Y-%m-%d")
