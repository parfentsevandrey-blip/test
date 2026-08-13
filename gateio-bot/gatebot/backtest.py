"""Бэктест на исторических свечах.

Принципиальный момент: прогоняется **тот же** `TradingEngine`, что и в живой
торговле, — подменяются только источник данных (курсор по истории) и часы.
Если бы бэктест имел собственную логику исполнения, он проверял бы не бота, а
свою копию бота, и расхождение вылезло бы уже на реальных деньгах.

Заглядывания в будущее нет по построению: на баре i стратегия видит свечи
0..i, а её ордера сводятся с рынком начиная с бара i+1.
"""

from __future__ import annotations

import logging
import math
from dataclasses import dataclass, field
from decimal import Decimal
from typing import Optional, Sequence

from .config import Config, interval_seconds
from .exchange.base import Exchange
from .exchange.paper import PaperExchange
from .risk import RiskManager
from .strategies import build_strategy
from .types import Balance, Candle, Fill, Order, PairSpec, Side

log = logging.getLogger(__name__)


class ReplayMarket(Exchange):
    """Источник данных, отдающий историю по курсору."""

    name = "replay"

    def __init__(self, candles: Sequence[Candle], spec: PairSpec):
        self.candles = list(candles)
        self.spec = spec
        self.cursor = 0

    def get_pair_spec(self, symbol: str) -> PairSpec:
        return self.spec

    def get_candles(self, symbol: str, interval: str, limit: int = 200) -> list[Candle]:
        return self.candles[max(0, self.cursor + 1 - limit) : self.cursor + 1]

    def get_price(self, symbol: str) -> Decimal:
        return self.candles[self.cursor].close

    # Счёт живёт в PaperExchange — сюда обращений быть не должно.
    def get_balances(self) -> dict[str, Balance]:
        raise NotImplementedError

    def get_open_orders(self, symbol: str) -> list[Order]:
        raise NotImplementedError

    def get_order(self, symbol: str, order_id: str) -> Order:
        raise NotImplementedError

    def place_order(self, order: Order) -> Order:
        raise NotImplementedError

    def cancel_order(self, symbol: str, order_id: str) -> None:
        raise NotImplementedError


@dataclass
class Trade:
    """Завершённая сделка: вход усреднён, выход — по факту продажи."""

    entry_ts: int
    exit_ts: int
    entry_price: Decimal
    exit_price: Decimal
    amount: Decimal
    pnl: Decimal

    @property
    def is_win(self) -> bool:
        return self.pnl > 0


@dataclass
class BacktestResult:
    symbol: str
    interval: str
    strategy: str
    bars: int
    start_ts: int
    end_ts: int
    initial_equity: Decimal
    final_equity: Decimal
    equity_curve: list[tuple[int, Decimal]] = field(default_factory=list)
    trades: list[Trade] = field(default_factory=list)
    fills: list[Fill] = field(default_factory=list)
    fees: Decimal = Decimal(0)
    buy_hold_return: Decimal = Decimal(0)
    halted: str = ""

    @property
    def total_return(self) -> Decimal:
        if self.initial_equity <= 0:
            return Decimal(0)
        return (self.final_equity - self.initial_equity) / self.initial_equity

    @property
    def max_drawdown(self) -> Decimal:
        peak = Decimal(0)
        worst = Decimal(0)
        for _, equity in self.equity_curve:
            peak = max(peak, equity)
            if peak > 0:
                worst = max(worst, (peak - equity) / peak)
        return worst

    @property
    def win_rate(self) -> Decimal:
        if not self.trades:
            return Decimal(0)
        wins = sum(1 for t in self.trades if t.is_win)
        return Decimal(wins) / Decimal(len(self.trades))

    @property
    def profit_factor(self) -> Optional[Decimal]:
        gains = sum((t.pnl for t in self.trades if t.pnl > 0), Decimal(0))
        losses = -sum((t.pnl for t in self.trades if t.pnl < 0), Decimal(0))
        if losses == 0:
            return None  # убыточных сделок не было — фактор не определён
        return gains / losses

    def sharpe(self, periods_per_year: float) -> float:
        """Sharpe по доходностям бар-к-бару, безрисковая ставка = 0."""
        curve = [float(e) for _, e in self.equity_curve]
        rets = [
            (curve[i] - curve[i - 1]) / curve[i - 1]
            for i in range(1, len(curve))
            if curve[i - 1] > 0
        ]
        if len(rets) < 2:
            return 0.0
        mean = sum(rets) / len(rets)
        var = sum((r - mean) ** 2 for r in rets) / (len(rets) - 1)
        if var <= 0:
            return 0.0
        return mean / math.sqrt(var) * math.sqrt(periods_per_year)

    def summary(self) -> str:
        import datetime as dt

        def when(ts: int) -> str:
            return dt.datetime.fromtimestamp(ts, dt.timezone.utc).strftime("%Y-%m-%d")

        pf = self.profit_factor
        year = 365 * 24 * 3600 / interval_seconds(self.interval)
        lines = [
            f"Пара:              {self.symbol} ({self.interval})",
            f"Стратегия:         {self.strategy}",
            f"Период:            {when(self.start_ts)} — {when(self.end_ts)} ({self.bars} баров)",
            "",
            f"Старт:             {self.initial_equity:.2f}",
            f"Финиш:             {self.final_equity:.2f}",
            f"Доходность:        {self.total_return:+.2%}",
            f"Купить и держать:  {self.buy_hold_return:+.2%}",
            f"Макс. просадка:    {self.max_drawdown:.2%}",
            f"Sharpe:            {self.sharpe(year):.2f}",
            "",
            f"Сделок:            {len(self.trades)}",
            f"Прибыльных:        {self.win_rate:.1%}",
            f"Профит-фактор:     {'—' if pf is None else f'{pf:.2f}'}",
            f"Комиссии:          {self.fees:.2f}",
        ]
        if self.halted:
            lines += ["", f"Аварийная остановка: {self.halted}"]
        return "\n".join(lines)


def run_backtest(
    config: Config,
    candles: Sequence[Candle],
    spec: PairSpec,
    *,
    progress_every: int = 0,
) -> BacktestResult:
    from .engine import TradingEngine  # локальный импорт: circular

    if len(candles) < 2:
        raise ValueError("Для бэктеста нужно минимум 2 свечи")

    market = ReplayMarket(candles, spec)
    paper = PaperExchange(
        market,
        quote_currency=spec.quote,
        initial_quote=config.initial_quote,
        maker_fee=config.exchange.maker_fee,
        taker_fee=config.exchange.taker_fee,
        slippage=config.exchange.slippage,
    )
    strategy = build_strategy(config.strategy.name, config.strategy.params)
    risk = RiskManager(config.risk)
    span = interval_seconds(config.interval)

    # Часы всегда указывают на момент закрытия текущей свечи: движок считает
    # её закрытой, а следующую ещё не видит.
    engine = TradingEngine(
        config=config,
        exchange=paper,
        strategy=strategy,
        risk=risk,
        store=None,
        clock=lambda: float(market.candles[market.cursor].ts + span),
    )

    result = BacktestResult(
        symbol=config.symbol,
        interval=config.interval,
        strategy=strategy.describe(),
        bars=len(candles),
        start_ts=candles[0].ts,
        end_ts=candles[-1].ts,
        initial_equity=config.initial_quote,
        final_equity=config.initial_quote,
    )

    seen_fills = 0
    open_entry: Optional[tuple[int, Decimal, Decimal]] = None  # ts, цена, объём

    # Движок логирует каждый ордер; на тысячах баров это стена текста, за
    # которой не видно самого отчёта. При -v (DEBUG) оставляем всё как есть.
    engine_log = logging.getLogger("gatebot.engine")
    previous_level = engine_log.level
    if engine_log.getEffectiveLevel() > logging.DEBUG:
        engine_log.setLevel(logging.WARNING)

    try:
        for i in range(len(candles)):
            market.cursor = i
            engine.tick()

            # Новые исполнения -> сделки для статистики.
            for fill in paper.fills[seen_fills:]:
                result.fees += fill.fee
                if fill.side is Side.BUY:
                    if open_entry is None:
                        open_entry = (candles[i].ts, fill.price, fill.amount)
                    else:
                        ts, price, amount = open_entry
                        total = price * amount + fill.price * fill.amount
                        amount += fill.amount
                        open_entry = (ts, total / amount, amount)
                elif open_entry is not None:
                    ts, price, amount = open_entry
                    closed = min(fill.amount, amount)
                    result.trades.append(
                        Trade(
                            entry_ts=ts,
                            exit_ts=candles[i].ts,
                            entry_price=price,
                            exit_price=fill.price,
                            amount=closed,
                            pnl=(fill.price - price) * closed - fill.fee,
                        )
                    )
                    amount -= closed
                    open_entry = None if amount <= 0 else (ts, price, amount)
            seen_fills = len(paper.fills)

            equity = paper.equity(config.symbol, candles[i].close)
            result.equity_curve.append((candles[i].ts, equity))

            if progress_every and i and i % progress_every == 0:
                log.info("Бэктест: %s/%s баров, счёт %.2f", i, len(candles), equity)
    finally:
        engine_log.setLevel(previous_level)

    result.fills = list(paper.fills)
    result.final_equity = result.equity_curve[-1][1]
    result.buy_hold_return = (candles[-1].close - candles[0].close) / candles[0].close
    if risk.state.halted:
        result.halted = risk.state.halt_reason
    return result
