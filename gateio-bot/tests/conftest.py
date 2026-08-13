from __future__ import annotations

import math
import sys
from decimal import Decimal
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gatebot.types import Candle, PairSpec  # noqa: E402


@pytest.fixture
def spec() -> PairSpec:
    return PairSpec(
        symbol="BTC_USDT",
        base="BTC",
        quote="USDT",
        amount_precision=6,
        price_precision=2,
        min_base_amount=Decimal("0.00001"),
        min_quote_amount=Decimal("1"),
    )


def make_candles(prices, start_ts: int = 1_700_000_000, step: int = 3600) -> list[Candle]:
    """Свечи из списка цен закрытия: high/low = ±0.5% вокруг тела."""
    candles = []
    prev = Decimal(str(prices[0]))
    for i, p in enumerate(prices):
        close = Decimal(str(p))
        high = max(prev, close) * Decimal("1.005")
        low = min(prev, close) * Decimal("0.995")
        candles.append(
            Candle(
                ts=start_ts + i * step,
                open=prev,
                high=high,
                low=low,
                close=close,
                volume=Decimal(1),
            )
        )
        prev = close
    return candles


def wave(n: int, base: float = 100.0, amplitude: float = 5.0, period: int = 20) -> list[float]:
    """Синусоида — рынок в боковике, идеальный полигон для сетки."""
    return [base + amplitude * math.sin(2 * math.pi * i / period) for i in range(n)]


def trend(n: int, base: float = 100.0, slope: float = 0.5) -> list[float]:
    return [base + slope * i for i in range(n)]
