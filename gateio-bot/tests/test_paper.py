from __future__ import annotations

from decimal import Decimal

import pytest

from gatebot.exchange.base import Exchange, InsufficientBalance
from gatebot.exchange.paper import PaperExchange
from gatebot.types import Balance, Candle, Order, OrderType, PairSpec, Side
from tests.conftest import make_candles


class StubMarket(Exchange):
    """Источник данных без сети."""

    def __init__(self, spec: PairSpec, price: Decimal = Decimal(100)):
        self.spec = spec
        self.price = price
        self.candles: list[Candle] = []

    def get_pair_spec(self, symbol: str) -> PairSpec:
        return self.spec

    def get_candles(self, symbol, interval, limit=200):
        return self.candles[-limit:]

    def get_price(self, symbol):
        return self.price

    def get_balances(self):
        return {}

    def get_open_orders(self, symbol):
        return []

    def get_order(self, symbol, order_id):
        raise NotImplementedError

    def place_order(self, order):
        raise NotImplementedError

    def cancel_order(self, symbol, order_id):
        raise NotImplementedError


@pytest.fixture
def paper(spec):
    return PaperExchange(
        StubMarket(spec),
        quote_currency="USDT",
        initial_quote=Decimal(1000),
        maker_fee=Decimal("0.002"),
        taker_fee=Decimal("0.002"),
        slippage=Decimal("0.001"),
    )


def _limit(side: Side, amount: str, price: str, cid: str = "test") -> Order:
    return Order(
        client_id=cid,
        symbol="BTC_USDT",
        side=side,
        type=OrderType.LIMIT,
        amount=Decimal(amount),
        price=Decimal(price),
    )


def test_limit_buy_locks_quote(paper):
    paper.place_order(_limit(Side.BUY, "1", "90"))
    usdt = paper.get_balances()["USDT"]
    assert usdt.locked == Decimal(90)
    assert usdt.available == Decimal(910)


def test_limit_order_rejected_without_funds(paper):
    with pytest.raises(InsufficientBalance):
        paper.place_order(_limit(Side.BUY, "100", "90"))


def test_limit_buy_fills_only_when_price_passes_level(paper):
    paper.place_order(_limit(Side.BUY, "1", "90"))

    # Свеча не дошла до уровня — исполнения нет.
    paper.feed("BTC_USDT", Candle(0, Decimal(100), Decimal(101), Decimal(91), Decimal(95), Decimal(1)))
    assert paper.fills == []

    fills = paper.feed(
        "BTC_USDT", Candle(1, Decimal(95), Decimal(96), Decimal(85), Decimal(88), Decimal(1))
    )
    assert len(fills) == 1
    assert fills[0].price == Decimal(90)
    assert paper.get_balances()["BTC"].available == Decimal(1)


def test_fee_charged_on_fill(paper):
    paper.place_order(_limit(Side.BUY, "1", "90"))
    fills = paper.feed(
        "BTC_USDT", Candle(1, Decimal(95), Decimal(96), Decimal(85), Decimal(88), Decimal(1))
    )
    assert fills[0].fee == Decimal("0.18")  # 90 * 0.002
    assert paper.get_balances()["USDT"].available == Decimal(1000) - Decimal(90) - Decimal("0.18")


def test_cancel_releases_locked_funds(paper):
    order = paper.place_order(_limit(Side.BUY, "1", "90"))
    paper.cancel_order("BTC_USDT", order.order_id)
    usdt = paper.get_balances()["USDT"]
    assert usdt.locked == 0
    assert usdt.available == Decimal(1000)


def test_market_buy_uses_quote_amount_and_slippage(paper):
    order = Order(
        client_id="m",
        symbol="BTC_USDT",
        side=Side.BUY,
        type=OrderType.MARKET,
        amount=Decimal(100),  # 100 USDT
    )
    paper.get_price("BTC_USDT")  # прогреваем последнюю цену = 100
    paper.place_order(order)
    fill = paper.fills[-1]
    assert fill.price == Decimal("100.10")  # +0.1% проскальзывания
    assert fill.amount == Decimal("0.999000")
    assert paper.get_balances()["BTC"].available == Decimal("0.999000")


def test_market_sell_reduces_base(paper):
    paper.get_price("BTC_USDT")
    paper._balances["BTC"] = Balance("BTC", Decimal(2))
    order = Order(
        client_id="s",
        symbol="BTC_USDT",
        side=Side.SELL,
        type=OrderType.MARKET,
        amount=Decimal(1),
    )
    paper.place_order(order)
    fill = paper.fills[-1]
    assert fill.side is Side.SELL
    assert fill.price == Decimal("99.90")  # проскальзывание против нас
    assert paper.get_balances()["BTC"].available == Decimal(1)


def test_equity_counts_base_and_quote(paper):
    paper.get_price("BTC_USDT")
    paper._balances["BTC"] = Balance("BTC", Decimal(2))
    assert paper.equity("BTC_USDT", Decimal(100)) == Decimal(1200)


def test_open_orders_exclude_filled(paper):
    paper.place_order(_limit(Side.BUY, "1", "90", cid="a"))
    paper.place_order(_limit(Side.BUY, "1", "50", cid="b"))
    paper.feed("BTC_USDT", Candle(1, Decimal(95), Decimal(96), Decimal(85), Decimal(88), Decimal(1)))
    open_ids = [o.client_id for o in paper.get_open_orders("BTC_USDT")]
    assert open_ids == ["b"]


def test_get_candles_triggers_matching(paper, spec):
    paper.place_order(_limit(Side.BUY, "1", "90"))
    paper.market.candles = make_candles([100, 80])
    paper.get_candles("BTC_USDT", "1h", 10)
    assert len(paper.fills) == 1
