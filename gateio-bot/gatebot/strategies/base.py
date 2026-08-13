"""Контракт стратегии.

Стратегия ничего не размещает и не отменяет сама. На каждом тике она получает
срез рынка и возвращает **полный желаемый набор ордеров**. Разницу между
желаемым и фактическим состоянием вычисляет движок (`engine.reconcile`).

Декларативная модель выбрана специально: она одинаково хорошо описывает и
сеточного бота (лестница из 20 лимиток), и сигнальную стратегию (один ордер на
вход), и «ничего не делать» (пустой список — снять все ордера).
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass
from decimal import Decimal
from typing import Any, Sequence

from ..types import Balance, Candle, Decision, PairSpec, Position


@dataclass
class StrategyContext:
    """Срез рынка и счёта на момент принятия решения."""

    symbol: str
    spec: PairSpec
    candles: Sequence[Candle]
    price: Decimal
    position: Position
    balances: dict[str, Balance]
    equity: Decimal
    now: int

    @property
    def quote_free(self) -> Decimal:
        bal = self.balances.get(self.spec.quote)
        return bal.available if bal else Decimal(0)

    @property
    def base_free(self) -> Decimal:
        bal = self.balances.get(self.spec.base)
        return bal.available if bal else Decimal(0)


class Strategy(ABC):
    """Базовый класс. Параметры приходят из секции `strategy.params` конфига."""

    #: Имя для реестра и конфига.
    key: str = ""
    #: Сколько свечей нужно для расчёта. Движок не вызовет стратегию, пока их меньше.
    warmup: int = 1

    def __init__(self, **params: Any):
        self.params = params

    @abstractmethod
    def decide(self, ctx: StrategyContext) -> Decision:
        """Вернуть желаемое состояние ордеров для текущего среза рынка."""

    def describe(self) -> str:
        joined = ", ".join(f"{k}={v}" for k, v in sorted(self.params.items()))
        return f"{self.key}({joined})"

    # ------------------------------------------------------------- Утилиты

    @staticmethod
    def _param(params: dict[str, Any], name: str, default: Any) -> Any:
        value = params.get(name, default)
        if isinstance(default, Decimal):
            return Decimal(str(value))
        if isinstance(default, bool):
            return bool(value)
        if isinstance(default, int) and not isinstance(value, bool):
            return int(value)
        return value
