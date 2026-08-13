from __future__ import annotations

from decimal import Decimal

import pytest

from gatebot.strategies import build_strategy
from gatebot.strategies.base import StrategyContext
from gatebot.strategies.ema_cross import EmaCrossStrategy
from gatebot.strategies.grid import GridStrategy
from gatebot.strategies.rsi_reversion import RsiReversionStrategy
from gatebot.types import Balance, Position, Side
from tests.conftest import make_candles, trend, wave


def ctx(spec, candles, *, price=None, position=None, quote="1000", base="0"):
    price = Decimal(str(price)) if price is not None else candles[-1].close
    return StrategyContext(
        symbol="BTC_USDT",
        spec=spec,
        candles=candles,
        price=price,
        position=position or Position("BTC_USDT"),
        balances={
            "USDT": Balance("USDT", Decimal(quote)),
            "BTC": Balance("BTC", Decimal(base)),
        },
        equity=Decimal(quote),
        now=candles[-1].ts,
    )


# --------------------------------------------------------------------- grid


def test_grid_places_buys_below_and_sells_above(spec):
    strategy = GridStrategy(levels=5, lower=90, upper=110, order_quote=20, max_orders_per_side=5)
    decision = strategy.decide(ctx(spec, make_candles([100]), price=100, base="1"))
    buys = [o for o in decision.desired if o.side is Side.BUY]
    sells = [o for o in decision.desired if o.side is Side.SELL]
    assert buys and sells
    assert all(o.price < 100 for o in buys)
    assert all(o.price > 100 for o in sells)


def test_grid_without_inventory_places_no_sells(spec):
    """Продавать нечего — на споте шорта нет."""
    strategy = GridStrategy(levels=5, lower=90, upper=110, order_quote=20)
    decision = strategy.decide(ctx(spec, make_candles([100]), price=100, base="0"))
    assert all(o.side is Side.BUY for o in decision.desired)


def test_grid_limits_buys_by_available_quote(spec):
    strategy = GridStrategy(levels=10, lower=50, upper=150, order_quote=40, max_orders_per_side=10)
    decision = strategy.decide(ctx(spec, make_candles([100]), price=100, quote="100"))
    spent = sum(o.amount * o.price for o in decision.desired if o.side is Side.BUY)
    assert spent <= Decimal(100)


def test_grid_respects_max_orders_per_side(spec):
    strategy = GridStrategy(levels=20, lower=50, upper=150, order_quote=5, max_orders_per_side=3)
    decision = strategy.decide(ctx(spec, make_candles([100]), price=100, base="10"))
    assert len([o for o in decision.desired if o.side is Side.BUY]) <= 3
    assert len([o for o in decision.desired if o.side is Side.SELL]) <= 3


def test_grid_stops_outside_range(spec):
    strategy = GridStrategy(levels=5, lower=90, upper=110, order_quote=20)
    decision = strategy.decide(ctx(spec, make_candles([200]), price=200))
    assert decision.desired == []
    assert not decision.flatten
    assert "вне коридора" in decision.note


def test_grid_exits_on_break_when_configured(spec):
    strategy = GridStrategy(levels=5, lower=90, upper=110, order_quote=20, exit_on_break=True)
    decision = strategy.decide(ctx(spec, make_candles([80]), price=80))
    assert decision.flatten


def test_grid_is_fixed_after_first_call(spec):
    """Сетка не должна ездить за ценой — иначе ордера переставляются вечно."""
    strategy = GridStrategy(levels=5, range_pct=Decimal("0.1"), order_quote=20)
    strategy.decide(ctx(spec, make_candles([100]), price=100))
    first = list(strategy._grid or [])
    strategy.decide(ctx(spec, make_candles([105]), price=105))
    assert strategy._grid == first


def test_grid_geometric_spacing_is_proportional(spec):
    strategy = GridStrategy(levels=4, lower=100, upper=800, geometric=True, order_quote=20)
    grid = strategy._build_grid(Decimal(400))
    ratios = [grid[i + 1] / grid[i] for i in range(len(grid) - 1)]
    assert all(abs(r - ratios[0]) < Decimal("0.0001") for r in ratios)


def test_grid_validates_params():
    with pytest.raises(ValueError):
        GridStrategy(levels=1)
    with pytest.raises(ValueError):
        GridStrategy(levels=5, lower=100, upper=50)


# ---------------------------------------------------------------- ema_cross


def test_ema_cross_enters_on_golden_cross(spec):
    # Падение, затем разворот вверх — быстрая EMA пересекает медленную.
    prices = trend(60, base=100, slope=-1) + trend(40, base=40, slope=2)
    strategy = EmaCrossStrategy(fast=5, slow=20, order_quote=100)
    decision = None
    for i in range(strategy.warmup, len(prices)):
        decision = strategy.decide(ctx(spec, make_candles(prices[: i + 1])))
        if decision.desired:
            break
    assert decision and decision.desired
    assert decision.desired[0].side is Side.BUY
    assert decision.desired[0].price is None  # рыночный вход


def test_ema_cross_exits_on_death_cross(spec):
    prices = trend(60, base=40, slope=2) + trend(40, base=160, slope=-3)
    strategy = EmaCrossStrategy(fast=5, slow=20, order_quote=100)
    position = Position("BTC_USDT", amount=Decimal(1), avg_entry=Decimal(100))
    flattened = False
    for i in range(strategy.warmup, len(prices)):
        if strategy.decide(ctx(spec, make_candles(prices[: i + 1]), position=position)).flatten:
            flattened = True
            break
    assert flattened


def test_ema_cross_respects_available_balance(spec):
    prices = trend(60, base=100, slope=-1) + trend(40, base=40, slope=2)
    strategy = EmaCrossStrategy(fast=5, slow=20, order_quote=500)
    for i in range(strategy.warmup, len(prices)):
        decision = strategy.decide(ctx(spec, make_candles(prices[: i + 1]), quote="30"))
        if decision.desired:
            assert decision.desired[0].amount == Decimal(30)
            return
    pytest.skip("сигнал не появился на этих данных")


def test_ema_cross_validates_periods():
    with pytest.raises(ValueError):
        EmaCrossStrategy(fast=30, slow=10)


def test_ema_cross_warmup_covers_slow_period():
    assert EmaCrossStrategy(fast=12, slow=26).warmup >= 26
    assert EmaCrossStrategy(fast=12, slow=26, trend=200).warmup >= 200


# ------------------------------------------------------------ rsi_reversion


def test_rsi_buys_on_exit_from_oversold(spec):
    prices = trend(40, base=100, slope=-2) + [20, 26, 32, 38]
    strategy = RsiReversionStrategy(period=14, oversold=30, order_quote=100)
    decision = None
    for i in range(strategy.warmup, len(prices)):
        decision = strategy.decide(ctx(spec, make_candles(prices[: i + 1])))
        if decision.desired:
            break
    assert decision and decision.desired
    assert decision.desired[0].side is Side.BUY


def test_rsi_does_not_buy_while_still_falling(spec):
    """Пока RSI остаётся ниже порога, входа быть не должно — это «ловля ножа»."""
    prices = trend(60, base=200, slope=-3)
    strategy = RsiReversionStrategy(period=14, oversold=30, order_quote=100)
    for i in range(strategy.warmup, len(prices)):
        assert strategy.decide(ctx(spec, make_candles(prices[: i + 1]))).desired == []


def test_rsi_takes_profit_when_overbought(spec):
    prices = trend(60, base=50, slope=3)
    strategy = RsiReversionStrategy(period=14, overbought=70, order_quote=100)
    position = Position("BTC_USDT", amount=Decimal(1), avg_entry=Decimal(50))
    assert strategy.decide(ctx(spec, make_candles(prices), position=position)).flatten


def test_rsi_validates_thresholds():
    with pytest.raises(ValueError):
        RsiReversionStrategy(oversold=80, overbought=20)


# ------------------------------------------------------------------ реестр


def test_registry_builds_known_strategies():
    assert isinstance(build_strategy("grid", {"levels": 5}), GridStrategy)
    assert isinstance(build_strategy("ema_cross"), EmaCrossStrategy)
    assert isinstance(build_strategy("rsi_reversion"), RsiReversionStrategy)


def test_registry_rejects_unknown():
    with pytest.raises(KeyError, match="Неизвестная стратегия"):
        build_strategy("moon_phase")


def test_describe_lists_params():
    assert "levels=5" in build_strategy("grid", {"levels": 5}).describe()
