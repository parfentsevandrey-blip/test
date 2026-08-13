"""Тесты реконсиляции: главное, чтобы движок не плодил дубли ордеров."""

from __future__ import annotations

import itertools
from decimal import Decimal

import pytest

from gatebot.config import Config, StrategyConfig
from gatebot.engine import TradingEngine
from gatebot.exchange.base import Exchange
from gatebot.risk import RiskLimits, RiskManager
from gatebot.strategies.base import Strategy, StrategyContext
from gatebot.types import (
    Balance,
    Decision,
    DesiredOrder,
    Order,
    OrderStatus,
    OrderType,
    PairSpec,
    Side,
)
from tests.conftest import make_candles


class FakeExchange(Exchange):
    """Биржа-заглушка: считает вызовы, чтобы проверить, что лишнего не делаем."""

    def __init__(self, spec: PairSpec, candles, price=Decimal(100)):
        self.spec = spec
        self.candles = candles
        self.price = price
        self.orders: dict[str, Order] = {}
        self.placed: list[Order] = []
        self.cancelled: list[str] = []
        self.balances = {
            "USDT": Balance("USDT", Decimal(1000)),
            "BTC": Balance("BTC", Decimal(0)),
        }
        self._ids = itertools.count(1)

    def get_pair_spec(self, symbol): return self.spec
    def get_candles(self, symbol, interval, limit=200): return self.candles[-limit:]
    def get_price(self, symbol): return self.price
    def get_balances(self): return self.balances
    def get_open_orders(self, symbol): return [o for o in self.orders.values() if o.is_open]
    def get_order(self, symbol, order_id): return self.orders[order_id]

    def place_order(self, order):
        order.order_id = f"o{next(self._ids)}"
        if order.type is OrderType.MARKET:
            order.status = OrderStatus.FILLED
            order.avg_price = self.price
            order.filled = (
                order.amount / self.price if order.side is Side.BUY else order.amount
            )
        self.orders[order.order_id] = order
        self.placed.append(order)
        return order

    def cancel_order(self, symbol, order_id):
        self.cancelled.append(order_id)
        self.orders[order_id].status = OrderStatus.CANCELLED


class ScriptedStrategy(Strategy):
    """Отдаёт заранее заданные решения, по одному на тик."""

    key = "scripted"
    warmup = 1

    def __init__(self, script):
        super().__init__()
        self.script = list(script)
        self.calls = 0

    def decide(self, ctx: StrategyContext) -> Decision:
        decision = self.script[min(self.calls, len(self.script) - 1)]
        self.calls += 1
        return decision


@pytest.fixture
def config():
    cfg = Config(symbol="BTC_USDT", interval="1h", mode="paper", candles=50)
    cfg.strategy = StrategyConfig(name="scripted", params={})
    return cfg


def engine_for(config, spec, script, candles=None, limits=None):
    candles = candles if candles is not None else make_candles([100] * 30)
    exchange = FakeExchange(spec, candles)
    engine = TradingEngine(
        config=config,
        exchange=exchange,
        strategy=ScriptedStrategy(script),
        risk=RiskManager(limits or RiskLimits()),
        store=None,
        # Часы «после закрытия последней свечи», иначе она отсеется как незакрытая.
        clock=lambda: float(candles[-1].ts + 3600),
    )
    return engine, exchange


def test_places_desired_limit_orders(config, spec):
    decision = Decision(desired=[DesiredOrder("b1", Side.BUY, Decimal("0.1"), Decimal(90))])
    engine, ex = engine_for(config, spec, [decision])
    report = engine.tick()
    assert report.placed == 1
    assert ex.placed[0].price == Decimal(90)


def test_identical_order_is_not_replaced(config, spec):
    """Повторный тик с тем же решением не должен трогать биржу."""
    decision = Decision(desired=[DesiredOrder("b1", Side.BUY, Decimal("0.1"), Decimal(90))])
    engine, ex = engine_for(config, spec, [decision, decision])
    engine.tick()
    engine.tick()
    assert len(ex.placed) == 1
    assert ex.cancelled == []


def test_changed_price_reissues_order(config, spec):
    first = Decision(desired=[DesiredOrder("b1", Side.BUY, Decimal("0.1"), Decimal(90))])
    second = Decision(desired=[DesiredOrder("b1", Side.BUY, Decimal("0.1"), Decimal(95))])
    engine, ex = engine_for(config, spec, [first, second])
    engine.tick()
    engine.tick()
    assert len(ex.placed) == 2
    assert len(ex.cancelled) == 1


def test_dropped_order_is_cancelled(config, spec):
    first = Decision(desired=[DesiredOrder("b1", Side.BUY, Decimal("0.1"), Decimal(90))])
    engine, ex = engine_for(config, spec, [first, Decision(desired=[])])
    engine.tick()
    engine.tick()
    assert len(ex.cancelled) == 1


def test_market_order_fires_once_while_requested(config, spec):
    """Ключевая защита: сигнальная стратегия не должна докупать каждый тик."""
    decision = Decision(desired=[DesiredOrder("entry", Side.BUY, Decimal(100), None)])
    engine, ex = engine_for(config, spec, [decision, decision, decision])
    engine.tick()
    engine.tick()
    engine.tick()
    assert len(ex.placed) == 1


def test_market_order_rearms_after_strategy_stops_asking(config, spec):
    buy = Decision(desired=[DesiredOrder("entry", Side.BUY, Decimal(100), None)])
    idle = Decision(desired=[])
    engine, ex = engine_for(config, spec, [buy, idle, buy])
    engine.tick()
    engine.tick()
    engine.tick()
    assert len(ex.placed) == 2


def test_market_buy_updates_position_immediately(config, spec):
    decision = Decision(desired=[DesiredOrder("entry", Side.BUY, Decimal(100), None)])
    engine, ex = engine_for(config, spec, [decision])
    engine.tick()
    assert engine.position.amount == Decimal(1)  # 100 USDT / цена 100
    assert engine.position.avg_entry == Decimal(100)


def test_flatten_cancels_orders_and_sells_position(config, spec):
    buy = Decision(desired=[DesiredOrder("entry", Side.BUY, Decimal(100), None)])
    park = Decision(desired=[DesiredOrder("b1", Side.BUY, Decimal("0.1"), Decimal(80))])
    engine, ex = engine_for(config, spec, [buy, park, Decision(flatten=True)])
    engine.tick()
    engine.tick()
    ex.balances["BTC"] = Balance("BTC", Decimal(1))
    engine.tick()

    assert engine.position.amount == 0
    assert ex.placed[-1].side is Side.SELL
    assert ex.placed[-1].type is OrderType.MARKET
    assert len(ex.cancelled) == 1  # лимитка снята перед закрытием


def test_flatten_limited_by_available_balance(config, spec):
    """Если на счёте меньше, чем думает бот, продаём только доступное."""
    buy = Decision(desired=[DesiredOrder("entry", Side.BUY, Decimal(100), None)])
    engine, ex = engine_for(config, spec, [buy, Decision(flatten=True)])
    engine.tick()
    ex.balances["BTC"] = Balance("BTC", Decimal("0.4"))
    engine.tick()
    assert ex.placed[-1].amount == Decimal("0.4")


def test_order_below_minimum_is_skipped(config, spec):
    tiny = Decision(desired=[DesiredOrder("b1", Side.BUY, Decimal("0.0000001"), Decimal(90))])
    engine, ex = engine_for(config, spec, [tiny])
    assert engine.tick().placed == 0
    assert ex.placed == []


def test_fills_are_detected_on_next_tick(config, spec):
    decision = Decision(desired=[DesiredOrder("b1", Side.BUY, Decimal("0.1"), Decimal(90))])
    engine, ex = engine_for(config, spec, [decision, decision])
    engine.tick()

    order = ex.placed[0]
    order.filled = Decimal("0.1")
    order.avg_price = Decimal(90)
    order.status = OrderStatus.FILLED

    report = engine.tick()
    assert len(report.fills) == 1
    assert engine.position.amount == Decimal("0.1")
    assert engine.position.avg_entry == Decimal(90)


def test_partial_fill_counted_once(config, spec):
    decision = Decision(desired=[DesiredOrder("b1", Side.BUY, Decimal("0.1"), Decimal(90))])
    engine, ex = engine_for(config, spec, [decision] * 4)
    engine.tick()
    order = ex.placed[0]

    order.filled = Decimal("0.04")
    order.avg_price = Decimal(90)
    engine.tick()
    assert engine.position.amount == Decimal("0.04")

    order.filled = Decimal("0.1")
    engine.tick()
    assert engine.position.amount == Decimal("0.1")  # добавилось 0.06, не 0.1


def test_warmup_skips_tick(config, spec):
    candles = make_candles([100, 101])
    exchange = FakeExchange(spec, candles)
    strategy = ScriptedStrategy([Decision()])
    strategy.warmup = 10
    engine = TradingEngine(
        config=config,
        exchange=exchange,
        strategy=strategy,
        risk=RiskManager(RiskLimits()),
        clock=lambda: float(candles[-1].ts + 3600),
    )
    report = engine.tick()
    assert "нужно 10 свечей" in report.skipped
    assert strategy.calls == 0


def test_unclosed_candle_is_dropped(config, spec):
    candles = make_candles([100] * 30)
    exchange = FakeExchange(spec, candles)
    engine = TradingEngine(
        config=config,
        exchange=exchange,
        strategy=ScriptedStrategy([Decision()]),
        risk=RiskManager(RiskLimits()),
        # Часы внутри последней свечи — она ещё не закрыта.
        clock=lambda: float(candles[-1].ts + 60),
    )
    engine.tick()
    assert engine.last_candle_ts == candles[-2].ts


def test_risk_halt_flattens_and_blocks(config, spec):
    buy = Decision(desired=[DesiredOrder("entry", Side.BUY, Decimal(100), None)])
    engine, ex = engine_for(
        config, spec, [buy, buy], limits=RiskLimits(max_drawdown_pct=Decimal("0.01"))
    )
    engine.tick()
    # Обрушаем счёт: просадка превысит лимит.
    ex.balances["USDT"] = Balance("USDT", Decimal(1))
    ex.balances["BTC"] = Balance("BTC", Decimal(1))
    engine.tick()
    assert engine.risk.state.halted
