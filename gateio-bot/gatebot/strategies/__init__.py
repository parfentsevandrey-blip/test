"""Реестр стратегий: имя из конфига -> класс."""

from __future__ import annotations

from typing import Any, Type

from .base import Strategy, StrategyContext
from .ema_cross import EmaCrossStrategy
from .grid import GridStrategy
from .rsi_reversion import RsiReversionStrategy

REGISTRY: dict[str, Type[Strategy]] = {
    GridStrategy.key: GridStrategy,
    EmaCrossStrategy.key: EmaCrossStrategy,
    RsiReversionStrategy.key: RsiReversionStrategy,
}


def build_strategy(name: str, params: dict[str, Any] | None = None) -> Strategy:
    if name not in REGISTRY:
        known = ", ".join(sorted(REGISTRY))
        raise KeyError(f"Неизвестная стратегия '{name}'. Доступны: {known}")
    return REGISTRY[name](**(params or {}))


__all__ = [
    "REGISTRY",
    "build_strategy",
    "Strategy",
    "StrategyContext",
    "GridStrategy",
    "EmaCrossStrategy",
    "RsiReversionStrategy",
]
