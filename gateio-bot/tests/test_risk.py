from __future__ import annotations

from decimal import Decimal

import pytest

from gatebot.risk import RiskLimits, RiskManager
from gatebot.strategies.base import StrategyContext
from gatebot.types import Balance, Decision, DesiredOrder, Position, Side
from tests.conftest import make_candles


def ctx(
    spec,
    *,
    price="100",
    position=None,
    equity="1000",
    quote="1000",
    base="0",
    now=1_700_000_000,
) -> StrategyContext:
    return StrategyContext(
        symbol="BTC_USDT",
        spec=spec,
        candles=make_candles([100, 101, 102]),
        price=Decimal(price),
        position=position or Position("BTC_USDT"),
        balances={
            "USDT": Balance("USDT", Decimal(quote)),
            "BTC": Balance("BTC", Decimal(base)),
        },
        equity=Decimal(equity),
        now=now,
    )


def long(amount="1", entry="100") -> Position:
    pos = Position("BTC_USDT", amount=Decimal(amount), avg_entry=Decimal(entry))
    pos.opened_ts = 1
    return pos


def test_stop_loss_forces_exit(spec):
    rm = RiskManager(RiskLimits(stop_loss_pct=Decimal("0.03")))
    verdict = rm.review(ctx(spec, price="96", position=long()), Decision())
    assert verdict.decision.flatten
    assert "стоп-лосс" in verdict.reasons[0]


def test_stop_loss_not_triggered_above_threshold(spec):
    rm = RiskManager(RiskLimits(stop_loss_pct=Decimal("0.03")))
    assert not rm.review(ctx(spec, price="98", position=long()), Decision()).decision.flatten


def test_take_profit_forces_exit(spec):
    rm = RiskManager(RiskLimits(take_profit_pct=Decimal("0.05")))
    verdict = rm.review(ctx(spec, price="106", position=long()), Decision())
    assert verdict.decision.flatten
    assert "тейк-профит" in verdict.reasons[0]


def test_trailing_stop_tracks_peak(spec):
    rm = RiskManager(RiskLimits(trailing_stop_pct=Decimal("0.02")))
    pos = long()
    # Растём до 120 — фиксируем максимум.
    assert not rm.review(ctx(spec, price="120", position=pos), Decision()).decision.flatten
    # Откат на 2.5% от максимума -> выход.
    verdict = rm.review(ctx(spec, price="117", position=pos), Decision())
    assert verdict.decision.flatten
    assert "трейлинг" in verdict.reasons[0]


def test_trailing_stop_ignored_below_entry(spec):
    """Ниже входа работает стоп-лосс, дублировать его трейлингом не нужно."""
    rm = RiskManager(RiskLimits(trailing_stop_pct=Decimal("0.02")))
    pos = long(entry="100")
    rm.review(ctx(spec, price="99", position=pos), Decision())
    assert not rm.review(ctx(spec, price="96", position=pos), Decision()).decision.flatten


def test_daily_loss_halts_trading(spec):
    rm = RiskManager(RiskLimits(max_daily_loss_pct=Decimal("0.05")))
    rm.review(ctx(spec, equity="1000"), Decision())  # фиксируем стартовый капитал дня
    verdict = rm.review(ctx(spec, equity="940"), Decision())
    assert rm.state.halted
    assert "дневной убыток" in verdict.reasons[0]

    # Остановка липкая: даже при восстановлении капитала торговля не возобновляется.
    after = rm.review(
        ctx(spec, equity="1100"),
        Decision(desired=[DesiredOrder("x", Side.BUY, Decimal(1), Decimal(90))]),
    )
    assert after.decision.desired == []


def test_resume_clears_halt(spec):
    rm = RiskManager(RiskLimits(max_daily_loss_pct=Decimal("0.05")))
    rm.review(ctx(spec, equity="1000"), Decision())
    rm.review(ctx(spec, equity="900"), Decision())
    assert rm.state.halted
    rm.resume()
    assert not rm.state.halted


def test_drawdown_halts_trading(spec):
    rm = RiskManager(RiskLimits(max_drawdown_pct=Decimal("0.1")))
    rm.review(ctx(spec, equity="1000"), Decision())
    rm.review(ctx(spec, equity="1200"), Decision())  # новый пик
    verdict = rm.review(ctx(spec, equity="1000"), Decision())
    assert rm.state.halted
    assert "просадка" in verdict.reasons[0]


def test_day_rollover_resets_daily_baseline(spec):
    rm = RiskManager(RiskLimits(max_daily_loss_pct=Decimal("0.05")))
    rm.review(ctx(spec, equity="1000", now=1_700_000_000), Decision())
    # Следующие сутки: базой становится текущий капитал, лимит не срабатывает.
    rm.review(ctx(spec, equity="900", now=1_700_000_000 + 86_400 * 2), Decision())
    assert not rm.state.halted


def test_position_cap_trims_buy_orders(spec):
    rm = RiskManager(RiskLimits(max_position_quote=Decimal(150)))
    decision = Decision(
        desired=[
            DesiredOrder("b1", Side.BUY, Decimal(1), Decimal(100)),
            DesiredOrder("b2", Side.BUY, Decimal(1), Decimal(100)),
        ]
    )
    verdict = rm.review(ctx(spec), decision)
    amounts = [o.amount for o in verdict.decision.desired]
    assert amounts[0] == Decimal(1)
    assert amounts[1] == Decimal("0.5")  # урезан до остатка лимита


def test_position_cap_drops_buys_when_full(spec):
    rm = RiskManager(RiskLimits(max_position_quote=Decimal(100)))
    decision = Decision(
        desired=[
            DesiredOrder("b", Side.BUY, Decimal(1), Decimal(100)),
            DesiredOrder("s", Side.SELL, Decimal(1), Decimal(120)),
        ]
    )
    verdict = rm.review(ctx(spec, position=long(amount="1", entry="100")), decision)
    sides = [o.side for o in verdict.decision.desired]
    assert sides == [Side.SELL]  # продажи всегда разрешены


def test_position_cap_uses_quote_amount_for_market_orders(spec):
    rm = RiskManager(RiskLimits(max_position_quote=Decimal(50)))
    decision = Decision(desired=[DesiredOrder("m", Side.BUY, Decimal(200), None)])
    verdict = rm.review(ctx(spec), decision)
    assert verdict.decision.desired[0].amount == Decimal(50)


def test_max_open_orders_truncates(spec):
    rm = RiskManager(RiskLimits(max_open_orders=2))
    decision = Decision(
        desired=[DesiredOrder(f"b{i}", Side.BUY, Decimal("0.01"), Decimal(90)) for i in range(5)]
    )
    verdict = rm.review(ctx(spec), decision)
    assert len(verdict.decision.desired) == 2


def test_limits_from_dict_treats_zero_as_disabled():
    limits = RiskLimits.from_dict({"stop_loss_pct": 0, "take_profit_pct": "0.05"})
    assert limits.stop_loss_pct is None
    assert limits.take_profit_pct == Decimal("0.05")
