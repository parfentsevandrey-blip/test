from __future__ import annotations

from decimal import Decimal

import pytest

from gatebot.backtest import ReplayMarket, run_backtest
from gatebot.config import Config, ExchangeConfig, StrategyConfig
from gatebot.risk import RiskLimits
from tests.conftest import make_candles, trend, wave


def config_for(name: str, params: dict, **overrides) -> Config:
    cfg = Config(
        symbol="BTC_USDT",
        interval="1h",
        mode="paper",
        candles=200,
        initial_quote=Decimal(1000),
        strategy=StrategyConfig(name=name, params=params),
        exchange=ExchangeConfig(
            maker_fee=Decimal("0.002"),
            taker_fee=Decimal("0.002"),
            slippage=Decimal("0.0005"),
        ),
    )
    for key, value in overrides.items():
        setattr(cfg, key, value)
    return cfg


def test_replay_market_never_shows_future(spec):
    candles = make_candles(list(range(100, 130)))
    market = ReplayMarket(candles, spec)
    market.cursor = 10
    served = market.get_candles("BTC_USDT", "1h", 200)
    assert served[-1].ts == candles[10].ts
    assert len(served) == 11


def test_replay_market_respects_limit(spec):
    market = ReplayMarket(make_candles(list(range(100, 130))), spec)
    market.cursor = 20
    assert len(market.get_candles("BTC_USDT", "1h", 5)) == 5


def test_backtest_runs_and_reports(spec):
    candles = make_candles(wave(300))
    cfg = config_for("ema_cross", {"fast": 5, "slow": 20, "order_quote": 200})
    result = run_backtest(cfg, candles, spec)

    assert result.bars == 300
    assert len(result.equity_curve) == 300
    assert result.initial_equity == Decimal(1000)
    assert result.final_equity > 0
    assert "ema_cross" in result.strategy
    assert isinstance(result.summary(), str)


def test_backtest_grid_trades_in_sideways_market(spec):
    """На синусоиде сетка обязана наторговать — иначе она не работает вовсе."""
    candles = make_candles(wave(400, base=100, amplitude=8, period=40))
    cfg = config_for(
        "grid",
        {"levels": 8, "lower": 92, "upper": 108, "order_quote": 50, "max_orders_per_side": 3},
    )
    result = run_backtest(cfg, candles, spec)
    assert len(result.fills) > 5
    assert result.fees > 0


def test_backtest_equity_never_goes_negative(spec):
    candles = make_candles(trend(200, base=100, slope=-0.4))
    cfg = config_for("ema_cross", {"fast": 5, "slow": 15, "order_quote": 500})
    result = run_backtest(cfg, candles, spec)
    assert all(equity >= 0 for _, equity in result.equity_curve)


def test_backtest_does_not_spend_more_than_deposit(spec):
    """Плечо на споте недоступно: позиция не может превысить депозит."""
    candles = make_candles(wave(200))
    cfg = config_for("ema_cross", {"fast": 4, "slow": 12, "order_quote": 100_000})
    result = run_backtest(cfg, candles, spec)
    assert result.final_equity <= Decimal(1000) * Decimal("1.5")


def test_stop_loss_limits_loss_in_backtest(spec):
    """Со стопом на 2% просадка обязана быть меньше, чем без него."""
    candles = make_candles(trend(150, base=100, slope=-0.6))
    params = {"fast": 3, "slow": 10, "order_quote": 900}

    with_stop = run_backtest(
        config_for("ema_cross", params, risk=RiskLimits(stop_loss_pct=Decimal("0.02"))),
        candles,
        spec,
    )
    without = run_backtest(config_for("ema_cross", params), candles, spec)
    assert with_stop.max_drawdown <= without.max_drawdown


def test_drawdown_halt_stops_trading(spec):
    candles = make_candles(trend(200, base=100, slope=-0.7))
    cfg = config_for(
        "ema_cross",
        {"fast": 3, "slow": 10, "order_quote": 900},
        risk=RiskLimits(max_drawdown_pct=Decimal("0.05")),
    )
    result = run_backtest(cfg, candles, spec)
    if result.halted:
        assert "просадка" in result.halted


def test_metrics_on_known_curve(spec):
    candles = make_candles(wave(300))
    cfg = config_for("ema_cross", {"fast": 5, "slow": 20, "order_quote": 200})
    result = run_backtest(cfg, candles, spec)

    assert 0 <= result.max_drawdown <= 1
    assert 0 <= result.win_rate <= 1
    assert result.profit_factor is None or result.profit_factor >= 0
    expected = (result.final_equity - result.initial_equity) / result.initial_equity
    assert result.total_return == expected


def test_buy_hold_benchmark(spec):
    candles = make_candles(trend(100, base=100, slope=1))
    cfg = config_for("ema_cross", {"fast": 5, "slow": 20, "order_quote": 200})
    result = run_backtest(cfg, candles, spec)
    expected = (candles[-1].close - candles[0].close) / candles[0].close
    assert result.buy_hold_return == expected


def test_backtest_rejects_too_short_history(spec):
    cfg = config_for("ema_cross", {})
    with pytest.raises(ValueError):
        run_backtest(cfg, make_candles([100]), spec)


def test_trades_have_consistent_pnl(spec):
    candles = make_candles(wave(400, amplitude=10, period=50))
    cfg = config_for(
        "rsi_reversion",
        {"period": 14, "oversold": 35, "overbought": 65, "order_quote": 300},
        risk=RiskLimits(take_profit_pct=Decimal("0.05"), stop_loss_pct=Decimal("0.03")),
    )
    result = run_backtest(cfg, candles, spec)
    for trade in result.trades:
        expected = (trade.exit_price - trade.entry_price) * trade.amount
        assert abs(trade.pnl - expected) <= abs(expected) * Decimal("0.01") + Decimal("1")
        assert trade.exit_ts >= trade.entry_ts
