from __future__ import annotations

import pytest

from gatebot.indicators import atr, crossed_down, crossed_up, ema, rsi, sma
from tests.conftest import make_candles


def test_sma_warmup_and_value():
    values = [1, 2, 3, 4, 5]
    result = sma(values, 3)
    assert result[:2] == [None, None]
    assert result[2] == pytest.approx(2.0)
    assert result[4] == pytest.approx(4.0)


def test_sma_length_matches_input():
    assert len(sma([1, 2, 3], 5)) == 3
    assert all(v is None for v in sma([1, 2, 3], 5))


def test_ema_seeds_from_sma():
    values = [float(i) for i in range(1, 11)]
    result = ema(values, 5)
    assert result[3] is None
    assert result[4] == pytest.approx(3.0)  # SMA(1..5)
    # k = 2/6, следующее = 6*k + 3*(1-k)
    assert result[5] == pytest.approx(6 * (2 / 6) + 3 * (1 - 2 / 6))


def test_ema_on_flat_series_equals_price():
    result = ema([50.0] * 30, 10)
    assert result[-1] == pytest.approx(50.0)


def test_rsi_bounds_and_extremes():
    rising = [float(i) for i in range(1, 40)]
    assert rsi(rising, 14)[-1] == pytest.approx(100.0)

    falling = [float(i) for i in range(40, 1, -1)]
    assert falling and rsi(falling, 14)[-1] == pytest.approx(0.0)

    for value in rsi([100.0, 101, 99, 102, 98] * 10, 14):
        assert value is None or 0 <= value <= 100


def test_rsi_flat_series_is_neutral():
    # Ни роста, ни падения — деления на ноль быть не должно.
    assert rsi([100.0] * 30, 14)[-1] == pytest.approx(50.0)


def test_cross_detection():
    fast = [1.0, 2.0, 3.0, 2.0, 1.0]
    slow = [2.0, 2.0, 2.0, 2.0, 2.0]
    assert crossed_up(fast, slow, 2)
    assert not crossed_up(fast, slow, 1)
    assert crossed_down(fast, slow, 4)


def test_cross_ignores_none_values():
    assert not crossed_up([None, 1.0], [None, 0.5])
    assert not crossed_down([None, None], [None, None])


def test_atr_is_positive_and_warms_up():
    candles = make_candles([100, 102, 101, 105, 103, 107, 106] * 5)
    values = atr(candles, 14)
    assert values[13] is None
    assert values[-1] is not None and values[-1] > 0


def test_period_validation():
    with pytest.raises(ValueError):
        sma([1, 2, 3], 0)
    with pytest.raises(ValueError):
        ema([1, 2, 3], -1)
