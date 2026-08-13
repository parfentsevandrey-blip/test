from __future__ import annotations

from decimal import Decimal

from gatebot.types import Fill, Position, Side


def _fill(side: Side, amount: str, price: str, fee: str = "0") -> Fill:
    return Fill(
        ts=0,
        symbol="BTC_USDT",
        side=side,
        amount=Decimal(amount),
        price=Decimal(price),
        fee=Decimal(fee),
    )


def test_position_average_entry_across_buys():
    pos = Position("BTC_USDT")
    pos.apply(_fill(Side.BUY, "1", "100"))
    pos.apply(_fill(Side.BUY, "1", "200"))
    assert pos.amount == Decimal(2)
    assert pos.avg_entry == Decimal(150)


def test_position_realizes_pnl_on_sell():
    pos = Position("BTC_USDT")
    pos.apply(_fill(Side.BUY, "2", "100"))
    pos.apply(_fill(Side.SELL, "1", "150"))
    assert pos.amount == Decimal(1)
    assert pos.realized_pnl == Decimal(50)
    assert pos.avg_entry == Decimal(100)  # средняя не меняется при продаже


def test_position_closes_completely():
    pos = Position("BTC_USDT")
    pos.apply(_fill(Side.BUY, "1", "100"))
    pos.apply(_fill(Side.SELL, "1", "120"))
    assert not pos.is_open
    assert pos.avg_entry == 0
    assert pos.opened_ts == 0


def test_fees_reduce_realized_pnl():
    pos = Position("BTC_USDT")
    pos.apply(_fill(Side.BUY, "1", "100", fee="0.2"))
    pos.apply(_fill(Side.SELL, "1", "110", fee="0.22"))
    assert pos.fees_paid == Decimal("0.42")
    assert pos.realized_pnl == Decimal(10) - Decimal("0.42")


def test_selling_more_than_held_does_not_go_negative():
    """На споте шорта нет: лишний объём в продаже не должен уводить позицию в минус."""
    pos = Position("BTC_USDT")
    pos.apply(_fill(Side.BUY, "1", "100"))
    pos.apply(_fill(Side.SELL, "5", "110"))
    assert pos.amount == 0
    assert pos.realized_pnl == Decimal(10)


def test_unrealized_pnl():
    pos = Position("BTC_USDT")
    pos.apply(_fill(Side.BUY, "2", "100"))
    assert pos.unrealized_pnl(Decimal(110)) == Decimal(20)
    assert Position("BTC_USDT").unrealized_pnl(Decimal(110)) == 0


def test_quantize_amount_rounds_down(spec):
    assert spec.quantize_amount(Decimal("0.1234567")) == Decimal("0.123456")
    assert spec.quantize_price(Decimal("100.129")) == Decimal("100.13")


def test_is_tradable_respects_minimums(spec):
    assert spec.is_tradable(Decimal("0.001"), Decimal("60000"))
    assert not spec.is_tradable(Decimal("0"), Decimal("60000"))
    assert not spec.is_tradable(Decimal("0.000001"), Decimal("60000"))  # < min_base
    assert not spec.is_tradable(Decimal("0.0001"), Decimal("1"))  # < min_quote
