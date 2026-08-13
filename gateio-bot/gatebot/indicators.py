"""Индикаторы на чистом Python — без numpy/pandas.

Каждая функция возвращает список той же длины, что и вход; позиции, где
значение ещё не определено (недостаточно истории), содержат `None`. Такой
контракт позволяет стратегии просто взять `values[-1]` и проверить на `None`,
не считая смещения вручную.
"""

from __future__ import annotations

from typing import Optional, Sequence

from .types import Candle

Series = list[Optional[float]]


def closes(candles: Sequence[Candle]) -> list[float]:
    return [float(c.close) for c in candles]


def sma(values: Sequence[float], period: int) -> Series:
    if period <= 0:
        raise ValueError("period должен быть > 0")
    out: Series = [None] * len(values)
    window = 0.0
    for i, v in enumerate(values):
        window += v
        if i >= period:
            window -= values[i - period]
        if i >= period - 1:
            out[i] = window / period
    return out


def ema(values: Sequence[float], period: int) -> Series:
    if period <= 0:
        raise ValueError("period должен быть > 0")
    out: Series = [None] * len(values)
    if len(values) < period:
        return out
    k = 2.0 / (period + 1)
    # Стартуем от SMA первых `period` значений — так делает большинство
    # терминалов, иначе первые десятки баров сильно смещены.
    prev = sum(values[:period]) / period
    out[period - 1] = prev
    for i in range(period, len(values)):
        prev = values[i] * k + prev * (1 - k)
        out[i] = prev
    return out


def rsi(values: Sequence[float], period: int = 14) -> Series:
    """RSI по Уайлдеру (сглаживание, а не простое среднее)."""
    out: Series = [None] * len(values)
    if len(values) <= period:
        return out
    gains = 0.0
    losses = 0.0
    for i in range(1, period + 1):
        delta = values[i] - values[i - 1]
        gains += max(delta, 0.0)
        losses += max(-delta, 0.0)
    avg_gain = gains / period
    avg_loss = losses / period
    out[period] = _rsi_value(avg_gain, avg_loss)
    for i in range(period + 1, len(values)):
        delta = values[i] - values[i - 1]
        avg_gain = (avg_gain * (period - 1) + max(delta, 0.0)) / period
        avg_loss = (avg_loss * (period - 1) + max(-delta, 0.0)) / period
        out[i] = _rsi_value(avg_gain, avg_loss)
    return out


def _rsi_value(avg_gain: float, avg_loss: float) -> float:
    if avg_loss == 0:
        return 100.0 if avg_gain > 0 else 50.0
    rs = avg_gain / avg_loss
    return 100.0 - 100.0 / (1.0 + rs)


def atr(candles: Sequence[Candle], period: int = 14) -> Series:
    """Average True Range — используется для стопов, привязанных к волатильности."""
    out: Series = [None] * len(candles)
    if len(candles) <= period:
        return out
    trs: list[float] = [float(candles[0].high - candles[0].low)]
    for i in range(1, len(candles)):
        c = candles[i]
        prev_close = float(candles[i - 1].close)
        trs.append(
            max(
                float(c.high - c.low),
                abs(float(c.high) - prev_close),
                abs(float(c.low) - prev_close),
            )
        )
    prev = sum(trs[1 : period + 1]) / period
    out[period] = prev
    for i in range(period + 1, len(candles)):
        prev = (prev * (period - 1) + trs[i]) / period
        out[i] = prev
    return out


def crossed_up(fast: Series, slow: Series, i: int = -1) -> bool:
    """Быстрая линия пересекла медленную снизу вверх на баре `i`."""
    return _cross(fast, slow, i, up=True)


def crossed_down(fast: Series, slow: Series, i: int = -1) -> bool:
    return _cross(fast, slow, i, up=False)


def _cross(fast: Series, slow: Series, i: int, up: bool) -> bool:
    if len(fast) < 2 or len(slow) < 2:
        return False
    i = i if i >= 0 else len(fast) + i
    if i < 1:
        return False
    a0, a1 = fast[i - 1], fast[i]
    b0, b1 = slow[i - 1], slow[i]
    if None in (a0, a1, b0, b1):
        return False
    if up:
        return a0 <= b0 and a1 > b1  # type: ignore[operator]
    return a0 >= b0 and a1 < b1  # type: ignore[operator]
