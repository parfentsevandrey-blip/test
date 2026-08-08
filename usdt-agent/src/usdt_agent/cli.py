"""Command-line interface.

    usdt-agent run          # the loop (paper by default)
    usdt-agent scan         # one-shot: what is on the table right now
    usdt-agent backtest     # fast replay against the simulator
    usdt-agent report       # markdown/json report from the ledger
    usdt-agent verify       # check the ledger hash chain
    usdt-agent doctor       # connectivity + config + live-interlock check
    usdt-agent strategies   # what the agent knows how to do
"""

from __future__ import annotations

import argparse
import json
import logging
import sys
import time
from pathlib import Path

from .agent import Agent
from .config import AgentConfig, ConfigError, load_config
from .dashboard import Dashboard, paint, supports_color
from .execution import PaperBroker, build_broker, live_interlocks
from .execution.live import CONFIRM_ENV, CONFIRM_VALUE, LiveTradingBlocked
from .feeds import CompositeFeed, SyntheticFeed, build_feed, build_venue_feeds
from .feeds.defillama import DefiLlamaFeed
from .ledger import Ledger
from .notify import Notifier
from .report import build_report, to_json, to_markdown
from .strategies import REGISTRY, build_strategies

log = logging.getLogger("usdt_agent")

BOLD_SEQ = "\033[1m"
RED_SEQ = "\033[31m"
GREEN_SEQ = "\033[32m"
YELLOW_SEQ = "\033[33m"
CYAN_SEQ = "\033[36m"
GREY_SEQ = "\033[90m"

BANNER = r"""
   __  ______ ___  ______     ___   _____________  ________
  / / / / __// _ \/_  __/    / _ | / ___/ __/ _ \/_  __/ _ \
 / /_/ /\ \ / // / / /      / __ |/ (_ / _// // / / / / , _/
 \____/___//____/ /_/      /_/ |_|\___/___/____/ /_/ /_/|_|
"""


def setup_logging(level: str, quiet: bool = False) -> None:
    logging.basicConfig(
        level=getattr(logging, level.upper(), logging.INFO) if not quiet else logging.WARNING,
        format="%(asctime)s %(levelname)-7s %(name)s: %(message)s",
        datefmt="%H:%M:%S",
        stream=sys.stderr,
    )


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        prog="usdt-agent",
        description="An autonomous, market-neutral USDT treasury agent. Paper by default.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "Paper mode simulates fills against real market data and costs money "
            "only in electricity. Live mode requires four separate interlocks; run "
            "`usdt-agent doctor` to see which ones you are missing."
        ),
    )
    p.add_argument("-c", "--config", help="path to a TOML config file")
    p.add_argument("--db", help="ledger path (default: from config)")
    p.add_argument("--log-level", default=None, help="DEBUG/INFO/WARNING/ERROR")
    p.add_argument("-q", "--quiet", action="store_true", help="log warnings and above only")
    sub = p.add_subparsers(dest="command", required=True)

    run = sub.add_parser("run", help="run the agent loop")
    run.add_argument("--cycles", type=int, default=None, help="stop after N cycles (0 = forever)")
    run.add_argument("--interval", type=float, default=None, help="seconds between cycles")
    run.add_argument("--equity", type=float, default=None, help="starting equity in USDT")
    run.add_argument("--source", choices=("auto", "live", "synthetic"), default=None)
    run.add_argument("--live", action="store_true", help="trade real funds (requires interlocks)")
    run.add_argument("--dry-run", action="store_true", default=True,
                     help="live mode: log orders instead of sending them (default)")
    run.add_argument("--arm", action="store_true",
                     help="live mode: actually send orders (disables --dry-run)")
    run.add_argument("--max-order", type=float, default=100.0,
                     help="live mode: hard per-order ceiling in USDT")
    run.add_argument("--no-dashboard", action="store_true", help="plain log output")
    run.add_argument("--liquidate-on-exit", action="store_true",
                     help="close every open position before quitting")

    scan = sub.add_parser("scan", help="show what the strategies can see right now")
    scan.add_argument("--source", choices=("auto", "live", "synthetic"), default=None)
    scan.add_argument("--top", type=int, default=15)
    scan.add_argument("--json", action="store_true")

    bt = sub.add_parser("backtest", help="replay the simulator at full speed")
    bt.add_argument("--cycles", type=int, default=500)
    bt.add_argument("--step", type=float, default=300.0, help="simulated seconds per cycle")
    bt.add_argument("--seed", type=int, default=None)
    bt.add_argument("--equity", type=float, default=None)
    bt.add_argument("--json", action="store_true")

    rep = sub.add_parser("report", help="build a report from the ledger")
    rep.add_argument("-o", "--output", help="write to a file instead of stdout")
    rep.add_argument("--json", action="store_true")

    sub.add_parser("verify", help="verify the ledger hash chain")
    sub.add_parser("doctor", help="check data sources, config and live interlocks")
    sub.add_parser("strategies", help="list the available strategies")
    return p


# --------------------------------------------------------------------------
# Commands
# --------------------------------------------------------------------------


def cmd_run(args: argparse.Namespace, cfg: AgentConfig) -> int:
    if args.live:
        cfg.mode = "live"
    if args.cycles is not None:
        cfg.max_cycles = args.cycles
    if args.interval is not None:
        cfg.interval_s = args.interval
    if args.equity is not None:
        cfg.starting_equity_usdt = args.equity
    if args.source is not None:
        cfg.data_source = args.source
    cfg.validate()

    dry_run = not args.arm
    if cfg.is_live:
        problems = live_interlocks(("binance",))
        if problems:
            print(paint("live trading is blocked:", "\033[31m", supports_color()), file=sys.stderr)
            for pr in problems:
                print(f"  · {pr}", file=sys.stderr)
            print(f"\nSet {CONFIRM_ENV}={CONFIRM_VALUE} and export your API keys, "
                  f"or drop --live to keep paper trading.", file=sys.stderr)
            return 2
        if not dry_run:
            print(paint("\n!!  LIVE MODE ARMED — real orders, real money  !!\n", "\033[1;31m",
                        supports_color()), file=sys.stderr)

    feed, source = build_feed(cfg)
    try:
        broker = build_broker(
            cfg.execution, live=cfg.is_live, venues=cfg.venues, seed=cfg.seed,
            max_order_usdt=args.max_order, dry_run=dry_run,
        )
    except LiveTradingBlocked as e:
        print(f"error: {e}", file=sys.stderr)
        return 2

    strategies = build_strategies(cfg)
    if not strategies:
        print("error: no strategies enabled", file=sys.stderr)
        return 2

    ledger = Ledger(cfg.db_path)
    agent = Agent(cfg, feed, broker, strategies, ledger, data_source=source)
    notifier = Notifier(cfg.notify_webhook)

    dash = Dashboard(enabled=not args.no_dashboard)
    if dash.enabled:
        print(paint(BANNER, "\033[36m", supports_color()))

    try:
        agent.run(on_cycle=dash.render if dash.enabled else None)
    except KeyboardInterrupt:
        pass
    finally:
        dash.finish()
        if args.liquidate_on_exit and agent.open_trades:
            log.info("liquidating %d open positions", len(agent.open_trades))
            agent.liquidate()
        summary = agent.summary()
        if agent.risk.halted:
            notifier.halted(agent.risk.halt_reason, agent.equity)
        else:
            notifier.daily(summary)
        print()
        print(to_markdown(build_report(ledger, summary)))
        ledger.close()
    return 0


def cmd_scan(args: argparse.Namespace, cfg: AgentConfig) -> int:
    if args.source is not None:
        cfg.data_source = args.source
    feed, source = build_feed(cfg)
    strategies = build_strategies(cfg)
    snapshot = feed.snapshot(cfg.symbols)

    opportunities = []
    for name, s in strategies.items():
        try:
            opportunities.extend(s.scan(snapshot))
        except Exception as e:
            log.warning("scan failed for %s: %s", name, e)
    opportunities.sort(key=lambda o: o.score, reverse=True)
    top = opportunities[: args.top]

    if args.json:
        print(json.dumps(
            [{"strategy": o.strategy, "label": o.label, "edge_bps": round(o.edge_bps, 3),
              "expected_apr": round(o.expected_apr, 5), "capacity_usdt": round(o.capacity_usdt, 2),
              "horizon_s": o.horizon_s, "confidence": round(o.confidence, 3),
              "venues": list(o.venues)} for o in top],
            indent=2,
        ))
        return 0

    color = supports_color()
    print(f"\n  source: {source}   quotes: {len(snapshot.quotes)}   "
          f"funding: {len(snapshot.funding)}   pools: {len(snapshot.pools)}")
    if snapshot.errors:
        print(paint(f"  feed errors: {'; '.join(snapshot.errors)[:180]}", "\033[33m", color))
    print()
    if not top:
        print("  nothing clears the cost threshold right now — which is the normal state.\n")
        print("  (that is the honest answer: after fees, most quoted 'spreads' are not trades)")
        return 0

    print(f"  {'strategy':<15} {'edge':>8} {'APR':>9} {'capacity':>12} {'conf':>6}  opportunity")
    print("  " + "─" * 86)
    for o in top:
        print(f"  {o.strategy:<15} {o.edge_bps:7.2f}b {o.expected_apr:8.2%} "
              f"{o.capacity_usdt:11,.0f} {o.confidence:6.0%}  {o.label}")
    print(paint(
        "\n  ranked by log(1+APR) x confidence — a reliable 8 % beats one implausible 400 %.",
        GREY_SEQ, color))
    print(paint(
        "  'edge' is already net of fees, half-spread, impact and adverse selection.\n",
        GREY_SEQ, color))
    return 0


def cmd_backtest(args: argparse.Namespace, cfg: AgentConfig) -> int:
    cfg.data_source = "synthetic"
    cfg.interval_s = 0.0001
    cfg.max_cycles = args.cycles
    if args.seed is not None:
        cfg.seed = args.seed
    if args.equity is not None:
        cfg.starting_equity_usdt = args.equity

    synthetic = SyntheticFeed(venues=cfg.venues, seed=cfg.seed, step_s=args.step)
    feed = CompositeFeed([synthetic])
    broker = PaperBroker(cfg.execution, seed=cfg.seed)
    ledger = Ledger(":memory:")
    agent = Agent(cfg, feed, broker, build_strategies(cfg), ledger, data_source="synthetic")

    started = time.time()
    agent.run(max_cycles=args.cycles)
    agent.liquidate()
    elapsed = time.time() - started

    summary = agent.summary()
    summary["simulated_days"] = round(args.cycles * args.step / 86_400.0, 2)
    summary["wall_seconds"] = round(elapsed, 2)
    report = build_report(ledger, summary)

    if args.json:
        print(to_json(report))
    else:
        print(to_markdown(report))
        print(f"\n_simulated {summary['simulated_days']} days in {elapsed:.1f}s of wall clock._")
    ledger.close()
    return 0


def cmd_report(args: argparse.Namespace, cfg: AgentConfig) -> int:
    ledger = Ledger(cfg.db_path)
    report = build_report(ledger, {"mode": cfg.mode, "data_source": "ledger"})
    text = to_json(report) if args.json else to_markdown(report)
    if args.output:
        Path(args.output).write_text(text, encoding="utf-8")
        print(f"wrote {args.output}")
    else:
        print(text)
    ledger.close()
    return 0


def cmd_verify(args: argparse.Namespace, cfg: AgentConfig) -> int:
    ledger = Ledger(cfg.db_path)
    ok, message = ledger.verify()
    color = supports_color()
    print(paint(("✓ " if ok else "✗ ") + message, "\033[32m" if ok else "\033[31m", color))
    ledger.close()
    return 0 if ok else 1


def cmd_doctor(args: argparse.Namespace, cfg: AgentConfig) -> int:
    color = supports_color()
    ok = lambda t: paint("  ✓ " + t, "\033[32m", color)  # noqa: E731
    warn = lambda t: paint("  ! " + t, "\033[33m", color)  # noqa: E731
    bad = lambda t: paint("  ✗ " + t, "\033[31m", color)  # noqa: E731

    print("\nconfig")
    try:
        cfg.validate()
        print(ok(f"valid — mode={cfg.mode}, equity={cfg.starting_equity_usdt:,.0f} USDT, "
                 f"{len(cfg.enabled_strategies())} strategies enabled"))
    except ConfigError as e:
        print(bad(f"invalid: {e}"))
        return 1

    print("\nmarket data")
    reachable = 0
    for feed in build_venue_feeds(cfg.venues):
        started = time.time()
        try:
            quotes, funding, _ = feed.fetch(cfg.symbols)
            ms = (time.time() - started) * 1000
            if quotes:
                reachable += 1
                print(ok(f"{feed.name:<10} {len(quotes)} quotes, {len(funding)} funding rates ({ms:.0f} ms)"))
            else:
                print(warn(f"{feed.name:<10} reachable but returned nothing for your symbols"))
        except Exception as e:
            print(bad(f"{feed.name:<10} {str(e)[:90]}"))
    try:
        _, _, pools = DefiLlamaFeed().fetch(cfg.symbols)
        print(ok(f"defillama  {len(pools)} stablecoin pools pass the risk filter")
              if pools else warn("defillama  reachable, no pools passed the filter"))
    except Exception as e:
        print(bad(f"defillama  {str(e)[:90]}"))

    if reachable == 0:
        print(warn("no venue reachable — the agent will fall back to the simulator"))

    print("\nledger")
    try:
        ledger = Ledger(cfg.db_path)
        chain_ok, msg = ledger.verify()
        print(ok(msg) if chain_ok else bad(msg))
        totals = ledger.totals()
        print(ok(f"{int(totals['closed_trades'])} closed trades, "
                 f"realized PnL {totals['realized_pnl']:+,.4f} USDT"))
        ledger.close()
    except Exception as e:
        print(bad(f"ledger unusable: {e}"))

    print("\nlive-trading interlocks")
    problems = live_interlocks(cfg.venues)
    if not problems:
        print(ok("all interlocks satisfied — --live --arm would send real orders"))
    else:
        for p in problems:
            print(warn(p))
        print(paint("  (this is the safe state; paper mode needs none of them)",
                    "\033[90m", color))

    print("\nnotifications")
    n = Notifier(cfg.notify_webhook)
    print(ok("configured") if n.enabled else warn("none configured (set USDT_AGENT_WEBHOOK "
                                                  "or TELEGRAM_BOT_TOKEN/TELEGRAM_CHAT_ID)"))
    print()
    return 0


def cmd_strategies(args: argparse.Namespace, cfg: AgentConfig) -> int:
    color = supports_color()
    print()
    for name, cls in REGISTRY.items():
        scfg = cfg.strategy(name)
        state = paint("enabled", "\033[32m", color) if scfg.enabled else paint("disabled", "\033[90m", color)
        title = paint(name, BOLD_SEQ, color)
        pad = " " * max(0, 22 - len(name))
        print(f"  {title}{pad} {state}")
        print(f"      {cls.description}")
        defaults = cls.defaults()
        if defaults:
            merged = {**defaults, **scfg.params}
            print(paint(f"      params: {json.dumps(merged, default=str)}", "\033[90m", color))
        print()
    return 0


COMMANDS = {
    "run": cmd_run,
    "scan": cmd_scan,
    "backtest": cmd_backtest,
    "report": cmd_report,
    "verify": cmd_verify,
    "doctor": cmd_doctor,
    "strategies": cmd_strategies,
}


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        cfg = load_config(args.config)
    except ConfigError as e:
        print(f"config error: {e}", file=sys.stderr)
        return 2
    if args.db:
        cfg.db_path = args.db
    setup_logging(args.log_level or cfg.log_level, args.quiet)
    try:
        return COMMANDS[args.command](args, cfg)
    except KeyboardInterrupt:
        print("\ninterrupted", file=sys.stderr)
        return 130


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
