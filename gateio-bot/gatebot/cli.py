"""Командный интерфейс бота.

    python -m gatebot backtest --days 90
    python -m gatebot run
    python -m gatebot balance
"""

from __future__ import annotations

import argparse
import csv
import logging
import sys
import time
from decimal import Decimal
from pathlib import Path
from typing import Optional, Sequence

from .arbitrage import ArbitrageRunner, TriangleExecutor, TriangleScanner
from .backtest import BacktestResult, run_backtest
from .config import Config, load_config, load_env
from .engine import TradingEngine
from .exchange.gateio import GateIOClient
from .exchange.paper import PaperExchange
from .logging_setup import setup_logging
from .risk import RiskManager
from .state import StateStore
from .strategies import REGISTRY, build_strategy
from .types import Candle

log = logging.getLogger(__name__)


def build_parser() -> argparse.ArgumentParser:
    # Общие флаги живут в родительском парсере, поэтому работают и до
    # подкоманды, и после неё: `gatebot -c cfg backtest` и
    # `gatebot backtest -c cfg` одинаково допустимы.
    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("-c", "--config", default="config.yaml", help="путь к конфигу")
    common.add_argument("--symbol", help="переопределить пару, например ETH_USDT")
    common.add_argument("--interval", help="переопределить таймфрейм, например 15m")
    common.add_argument("--strategy", help="переопределить стратегию")
    common.add_argument("-v", "--verbose", action="store_true", help="уровень DEBUG")

    parser = argparse.ArgumentParser(
        prog="gatebot",
        description="Торговый бот для спота Gate.io",
        parents=[common],
    )
    sub = parser.add_subparsers(dest="command", required=True)

    def add(name: str, help_text: str) -> argparse.ArgumentParser:
        return sub.add_parser(name, help=help_text, parents=[common])

    run_cmd = add("run", "запустить торговлю (paper или live)")
    run_cmd.add_argument(
        "--live",
        action="store_true",
        help="реальные деньги; требует GATEIO_API_KEY/SECRET и подтверждения",
    )
    run_cmd.add_argument("--yes", action="store_true", help="не спрашивать подтверждение для --live")
    run_cmd.add_argument("--once", action="store_true", help="один тик и выход")

    bt = add("backtest", "прогнать стратегию на истории")
    bt.add_argument("--days", type=int, default=90, help="глубина истории в днях")
    bt.add_argument("--csv", help="взять свечи из CSV вместо биржи")
    bt.add_argument("--save-csv", help="сохранить кривую капитала в CSV")

    arb = add("arb", "треугольный арбитраж: сканировать и (опционально) торговать")
    arb.add_argument("--once", action="store_true", help="один скан и выход")
    arb.add_argument("--duration", type=float, help="сколько секунд наблюдать")
    arb.add_argument("--amount", type=Decimal, help="размер цикла в базовой валюте")
    arb.add_argument("--min-profit", type=Decimal, help="порог прибыли, например 0.002 = 0.2%%")
    arb.add_argument("--triangles", action="store_true", help="показать найденные циклы и выйти")
    arb.add_argument(
        "--execute",
        action="store_true",
        help="реально отправлять ордера; требует ключей и подтверждения",
    )
    arb.add_argument("--yes", action="store_true", help="не спрашивать подтверждение для --execute")

    add("balance", "показать балансы спот-аккаунта")
    add("strategies", "список стратегий")
    add("reset", "снять аварийную остановку и очистить состояние")

    info = add("info", "параметры торговой пары")
    info.add_argument("pair", nargs="?", help="пара (по умолчанию из конфига)")

    return parser


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = build_parser().parse_args(argv)
    load_env()

    try:
        config = _load(args)
    except (FileNotFoundError, ValueError) as exc:
        print(f"Ошибка конфигурации: {exc}", file=sys.stderr)
        return 2

    setup_logging("DEBUG" if args.verbose else config.log_level, config.log_file)

    handlers = {
        "run": cmd_run,
        "backtest": cmd_backtest,
        "arb": cmd_arb,
        "balance": cmd_balance,
        "strategies": cmd_strategies,
        "reset": cmd_reset,
        "info": cmd_info,
    }
    try:
        return handlers[args.command](args, config)
    except KeyboardInterrupt:
        print("\nПрервано.")
        return 130
    except Exception as exc:  # noqa: BLE001
        log.error("%s", exc)
        if args.verbose:
            raise
        return 1


def _load(args: argparse.Namespace) -> Config:
    if Path(args.config).is_file():
        config = load_config(args.config)
    else:
        if args.config != "config.yaml":
            raise FileNotFoundError(f"Конфиг не найден: {args.config}")
        log.warning("Конфиг не найден, используются значения по умолчанию (paper)")
        config = Config()

    if args.symbol:
        config.symbol = args.symbol.upper()
    if args.interval:
        config.interval = args.interval
    if args.strategy:
        config.strategy.name = args.strategy
    if getattr(args, "live", False):
        config.mode = "live"
    config.validate()
    return config


# ------------------------------------------------------------------- Команды


def cmd_run(args: argparse.Namespace, config: Config) -> int:
    client = _client(config)

    if config.is_live:
        if not args.yes and not _confirm_live(config):
            print("Отменено.")
            return 1
        exchange = client
        log.warning("РЕЖИМ LIVE: ордера отправляются на биржу реальными деньгами")
    else:
        exchange = PaperExchange(
            client,
            quote_currency=config.symbol.split("_")[1],
            initial_quote=config.initial_quote,
            maker_fee=config.exchange.maker_fee,
            taker_fee=config.exchange.taker_fee,
            slippage=config.exchange.slippage,
        )
        log.info("Режим PAPER: виртуальный депозит %s", config.initial_quote)

    engine = TradingEngine(
        config=config,
        exchange=exchange,
        strategy=build_strategy(config.strategy.name, config.strategy.params),
        risk=RiskManager(config.risk),
        store=StateStore(config.state_file),
    )

    if engine.risk.state.halted:
        log.error(
            "Бот остановлен аварийно: %s. Снимите блокировку командой `reset`.",
            engine.risk.state.halt_reason,
        )
        return 1

    if args.once:
        engine._log_report(engine.tick())
        return 0

    engine.run()
    return 0


def cmd_backtest(args: argparse.Namespace, config: Config) -> int:
    client = _client(config)
    spec = client.get_pair_spec(config.symbol)

    if args.csv:
        candles = _read_csv(args.csv)
        log.info("Загружено %s свечей из %s", len(candles), args.csv)
    else:
        end = int(time.time())
        start = end - args.days * 86400
        log.info("Качаем историю %s %s за %s дней…", config.symbol, config.interval, args.days)
        candles = client.get_candles_range(config.symbol, config.interval, start, end)
        log.info("Получено %s свечей", len(candles))

    if len(candles) < 2:
        print("Недостаточно данных для бэктеста.", file=sys.stderr)
        return 1

    result = run_backtest(config, candles, spec, progress_every=max(1, len(candles) // 10))
    print()
    print(result.summary())
    print()

    if args.save_csv:
        _write_curve(args.save_csv, result)
        print(f"Кривая капитала сохранена: {args.save_csv}")
    return 0


def cmd_arb(args: argparse.Namespace, config: Config) -> int:
    arb = config.arbitrage
    amount = args.amount or arb.trade_quote
    threshold = args.min_profit if args.min_profit is not None else arb.min_profit_pct

    client = _client(config)
    scanner = TriangleScanner(
        client,
        base=arb.base,
        fee=config.exchange.taker_fee,
        min_quote_volume=arb.min_quote_volume,
        allowed_middle=arb.middle,
        max_verify=arb.max_verify,
        book_depth=arb.book_depth,
    )

    if args.triangles:
        count = scanner.load_universe()
        for triangle in scanner.triangles:
            print(f"  {triangle.path:<34}{' | '.join(str(leg) for leg in triangle.legs)}")
        print(f"\nВсего циклов: {count}")
        return 0

    if args.execute:
        if not all(config.credentials()):
            print("Для --execute нужны GATEIO_API_KEY и GATEIO_API_SECRET.", file=sys.stderr)
            return 2
        if not args.yes and not _confirm_arb(arb, amount, threshold):
            print("Отменено.")
            return 1

    executor = TriangleExecutor(
        client,
        specs={},
        base=arb.base,
        min_profit_pct=threshold,
        max_failures=arb.max_failures,
        dry_run=not args.execute,
    )
    runner = ArbitrageRunner(
        client,
        scanner,
        executor,
        trade_quote=amount,
        poll_seconds=arb.poll_seconds,
    )

    if args.once:
        result = runner.once()
        _print_scan(result, threshold)
        return 0

    runner.run(duration=args.duration)
    return 0


def _print_scan(result, threshold: Decimal) -> None:
    print(f"\nЦиклов в обороте: {result.triangles}")
    print(f"Прошли отбор по тикерам: {result.verified}")
    if result.best_screen_pct is not None:
        print(f"Лучшая грубая оценка:    {result.best_screen_pct:+.4%}")
    print(f"Время скана:             {result.elapsed:.2f} с\n")

    if result.opportunities:
        print("Возможности по живому стакану:")
        for opportunity in result.opportunities:
            mark = "✓" if opportunity.profit_pct >= threshold else " "
            print(f"  {mark} {opportunity}")
    else:
        print("Прибыльных циклов по живому стакану нет.")

    if result.rejected:
        print("\nОтклонено при проверке:")
        for path, reason in list(result.rejected.items())[:10]:
            print(f"  {path:<34}{reason}")


def _confirm_arb(arb, amount: Decimal, threshold: Decimal) -> bool:
    print()
    print("=" * 62)
    print("  ВНИМАНИЕ: арбитраж будет торговать реальными деньгами")
    print(f"  Базовая валюта:  {arb.base}")
    print(f"  Размер цикла:    {amount} {arb.base}")
    print(f"  Порог прибыли:   {threshold:+.3%}")
    print("  Каждый цикл — 3 рыночные сделки подряд. При обрыве в середине")
    print("  бот попытается откатиться, но убыток на откате возможен.")
    print("=" * 62)
    return input("Введите 'РИСКУЮ' для запуска: ").strip().upper() == "РИСКУЮ"


def cmd_balance(args: argparse.Namespace, config: Config) -> int:
    client = _client(config)
    balances = {k: v for k, v in client.get_balances().items() if v.total > 0}
    if not balances:
        print("Пустой спот-аккаунт (или ключи без прав на чтение баланса).")
        return 0
    print(f"{'Валюта':<10}{'Доступно':>18}{'В ордерах':>18}")
    for currency, bal in sorted(balances.items()):
        print(f"{currency:<10}{bal.available:>18}{bal.locked:>18}")
    return 0


def cmd_strategies(args: argparse.Namespace, config: Config) -> int:
    for name, cls in sorted(REGISTRY.items()):
        lines = (cls.__doc__ or "").strip().splitlines()
        print(f"  {name:<16}{lines[0] if lines else ''}")
    return 0


def cmd_reset(args: argparse.Namespace, config: Config) -> int:
    path = Path(config.state_file)
    if path.is_file():
        path.unlink()
        print(f"Состояние удалено: {path}")
    else:
        print("Файла состояния нет — сбрасывать нечего.")
    print("Внимание: позиция на бирже (если она открыта) остаётся как есть.")
    return 0


def cmd_info(args: argparse.Namespace, config: Config) -> int:
    client = _client(config)
    symbol = (args.pair or config.symbol).upper()
    spec = client.get_pair_spec(symbol)
    price = client.get_price(symbol)
    print(f"Пара:               {spec.symbol}")
    print(f"Цена:               {price}")
    print(f"Точность цены:      {spec.price_precision} знаков")
    print(f"Точность объёма:    {spec.amount_precision} знаков")
    print(f"Мин. объём:         {spec.min_base_amount} {spec.base}")
    print(f"Мин. сумма:         {spec.min_quote_amount} {spec.quote}")
    return 0


# ------------------------------------------------------------------ Утилиты


def _client(config: Config) -> GateIOClient:
    key, secret = config.credentials()
    return GateIOClient(
        key,
        secret,
        host=config.exchange.host,
        timeout=config.exchange.timeout,
    )


def _confirm_live(config: Config) -> bool:
    print()
    print("=" * 62)
    print("  ВНИМАНИЕ: запуск на реальных деньгах")
    print(f"  Пара: {config.symbol}   Таймфрейм: {config.interval}")
    print(f"  Стратегия: {config.strategy.name}")
    print(f"  Стоп-лосс: {config.risk.stop_loss_pct or 'НЕ ЗАДАН'}")
    print(f"  Лимит дневного убытка: {config.risk.max_daily_loss_pct or 'НЕ ЗАДАН'}")
    print("=" * 62)
    return input("Введите 'РИСКУЮ' для запуска: ").strip().upper() == "РИСКУЮ"


def _read_csv(path: str) -> list[Candle]:
    """CSV с колонками ts,open,high,low,close,volume (заголовок обязателен)."""
    out: list[Candle] = []
    with open(path, newline="", encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            out.append(
                Candle(
                    ts=int(float(row["ts"])),
                    open=Decimal(row["open"]),
                    high=Decimal(row["high"]),
                    low=Decimal(row["low"]),
                    close=Decimal(row["close"]),
                    volume=Decimal(row.get("volume") or 0),
                )
            )
    out.sort(key=lambda c: c.ts)
    return out


def _write_curve(path: str, result: BacktestResult) -> None:
    with open(path, "w", newline="", encoding="utf-8") as fh:
        writer = csv.writer(fh)
        writer.writerow(["ts", "equity"])
        writer.writerows([[ts, str(equity)] for ts, equity in result.equity_curve])


if __name__ == "__main__":
    raise SystemExit(main())
