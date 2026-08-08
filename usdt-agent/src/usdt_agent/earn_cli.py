"""``usdt-agent earn ...`` — the command surface for the earning half."""

from __future__ import annotations

import argparse
import json
import logging
import sys
import time
from typing import Any

from .config import AgentConfig
from .dashboard import paint, supports_color
from .earn import (
    CHAINS,
    RECOMMENDED_CHAINS,
    EarningAgent,
    build_channels,
    setup_checklist,
    wallet_from_env_or_config,
)
from .earn.models import OrderStatus
from .ledger import Ledger

log = logging.getLogger("usdt_agent.earn")

BOLD = "\033[1m"
RED = "\033[31m"
GREEN = "\033[32m"
YELLOW = "\033[33m"
CYAN = "\033[36m"
GREY = "\033[90m"


def add_earn_parser(sub: argparse._SubParsersAction) -> None:
    """Register the ``earn`` command group on the main parser."""
    earn = sub.add_parser("earn", help="acquire USDT online (bounties, services, referrals)")
    ops = earn.add_subparsers(dest="earn_command", required=True)

    ops.add_parser("setup", help="what to do first, given you have nothing yet")
    ops.add_parser("channels", help="channel readiness, autonomy and blockers")
    ops.add_parser("wallet", help="read on-chain USDT balances (watch-only)")

    scan = ops.add_parser("scan", help="find and rank gigs by USDT per hour")
    scan.add_argument("--top", type=int, default=20)
    scan.add_argument("--channel", default=None)
    scan.add_argument("--json", action="store_true")

    run = ops.add_parser("run", help="run the earning loop")
    run.add_argument("--cycles", type=int, default=1,
                     help="0 = run until stopped (what a daemon wants)")
    run.add_argument("--interval", type=float, default=300.0,
                     help="seconds between cycles")

    ops.add_parser("collect", help="reconcile the wallet — book only what arrived on-chain")

    take = ops.add_parser("take", help="draft a work order for one gig")
    take.add_argument("gig_id")

    ops.add_parser("approvals", help="list decisions waiting for you")
    approve = ops.add_parser("approve", help="approve a queued action")
    approve.add_argument("approval_id")
    approve.add_argument("--note", default="")
    reject = ops.add_parser("reject", help="reject a queued action")
    reject.add_argument("approval_id")
    reject.add_argument("--note", default="")

    hours = ops.add_parser("log-hours", help="record real effort against an order")
    hours.add_argument("order_id")
    hours.add_argument("hours", type=float)
    hours.add_argument("--note", default="")

    invoice = ops.add_parser("invoice", help="create a payable invoice for a catalogue item")
    invoice.add_argument("sku")
    invoice.add_argument("--ref", default="")

    serve = ops.add_parser("serve", help="run the paid storefront (HTTP 402 + on-chain settlement)")
    serve.add_argument("--host", default=None)
    serve.add_argument("--port", type=int, default=None)

    report = ops.add_parser("report", help="earnings report: expected versus confirmed")
    report.add_argument("--json", action="store_true")


# --------------------------------------------------------------------------
# Wiring
# --------------------------------------------------------------------------


def _build(cfg: AgentConfig) -> tuple[EarningAgent, Ledger]:
    wallet = wallet_from_env_or_config(cfg.earn.wallet)
    channels = build_channels(cfg, wallet, cfg.earn.channel_params(), cfg.earn.enabled())
    ledger = Ledger(cfg.db_path)
    agent = EarningAgent(
        cfg, channels, wallet, ledger,
        max_open_orders=cfg.earn.max_open_orders,
        min_rate_usdt_per_hour=cfg.earn.min_rate_usdt_per_hour,
    )
    agent.collector.lookback_blocks = cfg.earn.lookback_blocks
    return agent, ledger


def _c(text: str, color: str) -> str:
    return paint(text, color, supports_color())


# --------------------------------------------------------------------------
# Commands
# --------------------------------------------------------------------------


def cmd_setup(args: argparse.Namespace, cfg: AgentConfig) -> int:
    agent, ledger = _build(cfg)
    ladder = agent.ladder()

    print()
    print(_c("  The ladder from zero", BOLD))
    print(_c("  Yield needs capital, selling needs customers, referrals need traffic.", GREY))
    print(_c("  Paid work needs only a skill and an address — so it goes first.", GREY))
    print()
    for stage in ladder.stages:
        mark = _c("✓", GREEN) if stage.done else _c("·", YELLOW)
        title = stage.title if stage.done else _c(stage.title, BOLD)
        print(f"  {mark} {title}")
        if not stage.done:
            print(_c(f"      {stage.rationale}", GREY))
    print()

    current = ladder.current
    if current is None:
        print(_c("  Every stage complete — the loop is self-funding.", GREEN))
    else:
        print(_c(f"  Next: {current.title}", BOLD))
        for action in current.actions:
            print(f"    {action}")
    print()

    checklist = setup_checklist(agent.channels)
    todo = [c for c in checklist if not c["satisfied"] and not c["optional"]]
    if todo:
        print(_c("  One-time setup still outstanding", BOLD))
        for item in todo:
            who = _c("you", YELLOW) if item["who"] == "you" else _c("agent", CYAN)
            print(f"    [{who}] {item['key']} — {item['description']}")
            if item["how_to"]:
                print(_c(f"           {item['how_to']}", GREY))
        print()

    print(_c(f"  Confirmed on-chain so far: {ladder.confirmed_usdt:,.4f} USDT", BOLD))
    print()
    ledger.close()
    return 0


def cmd_channels(args: argparse.Namespace, cfg: AgentConfig) -> int:
    agent, ledger = _build(cfg)
    print()
    for name, channel in agent.channels.items():
        info = channel.info()
        state = _c("ready", GREEN) if info["ready"] else _c("blocked", YELLOW)
        autonomy = {
            "auto": _c("auto", GREEN),
            "assisted": _c("needs your approval", YELLOW),
            "manual": _c("manual", RED),
        }[info["autonomy"]]
        capital = info["capital_required_usdt"]
        print(f"  {_c(name, BOLD)}  {state}  ·  {autonomy}")
        print(f"      {info['description']}")
        print(_c(
            f"      capital needed: {capital:,.0f} USDT · typical lag: {info['typical_lag_days']:.0f} days"
            f" · confirmed: {agent.store.confirmed_total(name):,.4f} USDT", GREY))
        for blocker in info["blockers"]:
            print(_c(f"      ✗ {blocker}", YELLOW))
        print()
    ledger.close()
    return 0


def cmd_wallet(args: argparse.Namespace, cfg: AgentConfig) -> int:
    wallet = wallet_from_env_or_config(cfg.earn.wallet)
    color = supports_color()
    print()
    if not wallet.addresses:
        print(_c("  No receiving address configured.", YELLOW))
        print("  The agent is watch-only — it never sees a private key. Set one of:")
        for chain in RECOMMENDED_CHAINS:
            print(_c(f"    export USDT_WALLET_{chain.upper()}=<your address>", GREY))
        print(_c(f"\n  Supported chains: {', '.join(CHAINS)}\n", GREY))
        return 1

    status = wallet.status()
    print(_c("  Watch-only USDT balances", BOLD))
    for chain, address in wallet.addresses.items():
        balance = status["balances"].get(chain)
        error = status["errors"].get(chain)
        short = f"{address[:10]}…{address[-6:]}" if len(address) > 20 else address
        if error:
            print(f"    {chain:<10} {short:<20} {paint('unreachable', YELLOW, color)}  {error[:50]}")
        else:
            print(f"    {chain:<10} {short:<20} {balance:>16,.6f} USDT")
    print()
    print(f"  {_c('total', BOLD)}: {status['total_usdt']:,.6f} USDT")
    print(_c("  (balances are read from public RPCs; no key is used or stored)", GREY))
    print()
    return 0


def cmd_scan(args: argparse.Namespace, cfg: AgentConfig) -> int:
    agent, ledger = _build(cfg)
    gigs, errors = agent.discover()
    agent.store.upsert_gigs(gigs)
    if args.channel:
        gigs = [g for g in gigs if g.channel == args.channel]
    top = gigs[: args.top]

    if args.json:
        print(json.dumps([{
            "id": g.id, "channel": g.channel, "title": g.title, "url": g.url,
            "reward_usdt": g.reward_usdt, "effort_hours": g.effort_hours,
            "payout_probability": round(g.payout_probability, 3),
            "expected_usdt": round(g.expected_usdt, 2),
            "usdt_per_hour": round(g.usdt_per_hour, 2),
        } for g in top], indent=2))
        ledger.close()
        return 0

    print()
    ready = sorted(agent.ready_channels())
    print(f"  ready channels: {', '.join(ready) if ready else _c('none', YELLOW)}")
    for name, err in errors.items():
        print(_c(f"  {name}: {err[:100]}", YELLOW))
    print()

    if not top:
        print(_c("  Nothing on the board that clears your floor right now.", YELLOW))
        print(_c("  That is a normal result, not a failure — run `usdt-agent earn setup`", GREY))
        print(_c("  if channels are blocked, or lower earn.min_rate_usdt_per_hour.", GREY))
        print()
        ledger.close()
        return 0

    print(f"  {'id':<18}{'channel':<12}{'reward':>9}{'est.h':>7}{'odds':>6}{'USDT/h':>9}  title")
    print("  " + "─" * 100)
    for g in top:
        print(f"  {g.id:<18}{g.channel:<12}{g.reward_usdt:>9,.0f}{g.effort_hours:>7.1f}"
              f"{g.payout_probability:>6.0%}{g.usdt_per_hour:>9,.0f}  {g.title[:44]}")
    print()
    print(_c("  USDT/h already discounts the odds of being paid. Effort is an estimate,", GREY))
    print(_c("  not a measurement — `earn log-hours` replaces it with the real number.", GREY))
    print(_c("  Take one with: usdt-agent earn take <id>", GREY))
    print()
    ledger.close()
    return 0


def cmd_run(args: argparse.Namespace, cfg: AgentConfig) -> int:
    agent, ledger = _build(cfg)
    try:
        agent.run(cycles=args.cycles, interval_s=args.interval)
    except KeyboardInterrupt:
        print("\ninterrupted", file=sys.stderr)
    finally:
        _print_report(agent)
        ledger.close()
    return 0


def cmd_collect(args: argparse.Namespace, cfg: AgentConfig) -> int:
    agent, ledger = _build(cfg)
    agent.register_expectations()
    result = agent.collect()

    print()
    print(_c("  Reconciling the wallet against outstanding expectations", BOLD))
    for chain, balance in result.balances.items():
        print(f"    {chain:<10} {balance:>16,.6f} USDT")
    for chain, err in result.errors.items():
        print(_c(f"    {chain:<10} unreachable: {err[:70]}", YELLOW))
    print()
    if result.found_money:
        print(_c(f"  +{result.confirmed_usdt:,.6f} USDT confirmed", GREEN))
        print(f"    new transfers : {result.new_transfers}")
        print(f"    matched       : {result.matched}")
        if result.unattributed_usdt:
            print(f"    unattributed  : {result.unattributed_usdt:,.6f} USDT")
        for chain, delta in result.delta_detected.items():
            print(_c(f"    {chain}: +{delta:,.6f} USDT detected via balance delta", GREY))
    else:
        print(_c("  Nothing new arrived.", GREY))
    if result.expired:
        print(_c(f"  {result.expired} expectation(s) expired unpaid", YELLOW))
    print()
    print(f"  treasury (confirmed only): {_c(f'{agent.treasury_usdt:,.6f} USDT', BOLD)}")
    print(_c(f"  still expected (not income): {agent.store.expected_total():,.6f} USDT", GREY))
    print()
    ledger.close()
    return 0


def cmd_take(args: argparse.Namespace, cfg: AgentConfig) -> int:
    agent, ledger = _build(cfg)
    row = agent.store.conn.execute("SELECT * FROM gigs WHERE id = ?", (args.gig_id,)).fetchone()
    if row is None:
        print(f"unknown gig {args.gig_id!r} — run `usdt-agent earn scan` first", file=sys.stderr)
        ledger.close()
        return 1

    channel = agent.channels.get(row["channel"])
    if channel is None:
        print(f"channel {row['channel']!r} is not enabled", file=sys.stderr)
        ledger.close()
        return 1

    from .earn.models import Gig

    gig = Gig(
        channel=row["channel"], external_id=row["external_id"], title=row["title"],
        url=row["url"], reward_usdt=row["reward_usdt"], effort_hours=row["effort_hours"],
        payout_probability=row["payout_probability"], deadline_ts=row["deadline_ts"],
        source=row["source"], meta=json.loads(row["meta"] or "{}"),
    )
    order = channel.execute(channel.plan(gig))
    agent.store.save_order(order)

    print()
    print(_c(f"  {order.title}", BOLD))
    print(f"  {gig.url}")
    print(f"  reward {gig.reward_usdt:,.2f} USDT · est {gig.effort_hours:.1f} h · "
          f"{gig.usdt_per_hour:,.0f} USDT/h · odds {gig.payout_probability:.0%}")
    print()
    print(_c("  plan", BOLD))
    for i, step in enumerate(order.plan, 1):
        print(f"    {i}. {step}")
    print()
    if order.status is OrderStatus.AWAITING_APPROVAL:
        approval_id = agent.store.request_approval(
            kind="work_order", title=f"[{gig.channel}] {gig.title[:80]}",
            detail=gig.url, subject_id=order.id, channel=gig.channel,
        )
        print(_c(f"  Queued for your approval: {approval_id}", YELLOW))
        print(_c("  The agent will not claim work in your name unattended.", GREY))
        print(f"  Approve with: usdt-agent earn approve {approval_id}")
    print(_c(f"\n  order id: {order.id}   (log real time with `earn log-hours {order.id} <hours>`)", GREY))
    print()
    ledger.close()
    return 0


def cmd_approvals(args: argparse.Namespace, cfg: AgentConfig) -> int:
    agent, ledger = _build(cfg)
    pending = agent.store.pending_approvals()
    print()
    if not pending:
        print(_c("  Nothing waiting on you.", GREY))
        print()
        ledger.close()
        return 0
    for row in pending:
        age = (time.time() - row["created_ts"]) / 3600.0
        print(f"  {_c(row['id'], BOLD)}  [{row['channel']}]  {row['title']}")
        print(_c(f"      queued {age:.1f} h ago · {row['kind']}", GREY))
        for line in (row["detail"] or "").splitlines():
            print(f"      {line}")
        print()
    print(_c("  usdt-agent earn approve <id>   |   usdt-agent earn reject <id>", GREY))
    print()
    ledger.close()
    return 0


def _decide(args: argparse.Namespace, cfg: AgentConfig, approved: bool) -> int:
    agent, ledger = _build(cfg)
    ok = agent.store.decide(args.approval_id, approved, args.note)
    if not ok:
        print(f"no pending approval {args.approval_id!r}", file=sys.stderr)
        ledger.close()
        return 1
    row = agent.store.conn.execute(
        "SELECT subject_id FROM approvals WHERE id = ?", (args.approval_id,)
    ).fetchone()
    if row and row["subject_id"]:
        agent.store.set_order_status(
            row["subject_id"],
            OrderStatus.SUBMITTED if approved else OrderStatus.ABANDONED,
            args.note,
        )
    print(_c("approved" if approved else "rejected", GREEN if approved else YELLOW), args.approval_id)
    ledger.close()
    return 0


def cmd_approve(args: argparse.Namespace, cfg: AgentConfig) -> int:
    return _decide(args, cfg, True)


def cmd_reject(args: argparse.Namespace, cfg: AgentConfig) -> int:
    return _decide(args, cfg, False)


def cmd_log_hours(args: argparse.Namespace, cfg: AgentConfig) -> int:
    agent, ledger = _build(cfg)
    row = agent.store.conn.execute(
        "SELECT * FROM orders WHERE id = ?", (args.order_id,)
    ).fetchone()
    if row is None:
        print(f"unknown order {args.order_id!r}", file=sys.stderr)
        ledger.close()
        return 1
    with agent.store.ledger.tx() as c:
        c.execute("UPDATE orders SET actual_hours = actual_hours + ?, updated_ts = ? WHERE id = ?",
                  (args.hours, time.time(), args.order_id))
    agent.store.log_effort(row["channel"], args.hours, args.order_id, "human", args.note)
    total = agent.store.conn.execute(
        "SELECT actual_hours FROM orders WHERE id = ?", (args.order_id,)
    ).fetchone()["actual_hours"]
    print(f"logged {args.hours:.2f} h (total {total:.2f} h) against {row['title'][:60]}")
    ledger.close()
    return 0


def cmd_invoice(args: argparse.Namespace, cfg: AgentConfig) -> int:
    agent, ledger = _build(cfg)
    channel = agent.channels.get("services")
    if channel is None:
        print("the services channel is not enabled", file=sys.stderr)
        ledger.close()
        return 1
    try:
        invoice = channel.create_invoice(args.sku, args.ref)
    except (KeyError, RuntimeError) as e:
        print(f"error: {e}", file=sys.stderr)
        print("catalogue:", ", ".join(o["sku"] for o in channel.catalogue()), file=sys.stderr)
        ledger.close()
        return 1

    agent.register_expectations()
    print()
    print(_c(f"  invoice {invoice.invoice_id}", BOLD))
    print(f"    product : {invoice.sku}")
    print(f"    chain   : {invoice.chain}")
    print(f"    pay to  : {_c(invoice.address, BOLD)}")
    print(f"    amount  : {_c(f'{invoice.amount_usdt:.6f} USDT', GREEN)}  "
          f"{_c('(exactly — the cents identify this invoice)', GREY)}")
    print(f"    expires : {time.strftime('%Y-%m-%d %H:%M UTC', time.gmtime(invoice.expires_ts))}")
    print()
    print(_c("  Settlement is on-chain and keyless: `usdt-agent earn collect` sees the", GREY))
    print(_c("  transfer, matches the unique amount, and releases the deliverable.", GREY))
    print()
    ledger.close()
    return 0


def cmd_serve(args: argparse.Namespace, cfg: AgentConfig) -> int:
    from .earn.server import serve

    agent, ledger = _build(cfg)
    channel = agent.channels.get("services")
    if channel is None:
        print("the services channel is not enabled", file=sys.stderr)
        ledger.close()
        return 1
    chain, address = channel.receiving_address()
    if not address:
        print("no receiving address — set USDT_WALLET_<CHAIN> first", file=sys.stderr)
        ledger.close()
        return 1

    host = args.host or cfg.earn.serve_host
    port = args.port or cfg.earn.serve_port
    httpd = serve(channel, agent.collector, host, port)
    print()
    print(_c(f"  storefront listening on http://{host}:{port}", BOLD))
    print(f"    catalogue : GET  http://{host}:{port}/")
    print(f"    buy       : POST http://{host}:{port}/order/<sku>")
    print(f"    payments  : {address} ({chain})")
    print(_c("    bound to localhost by default — put a TLS proxy in front before exposing it", GREY))
    print(_c("    Ctrl-C to stop\n", GREY))
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nstopping")
    finally:
        httpd.shutdown()
        ledger.close()
    return 0


def _print_report(agent: EarningAgent) -> None:
    summary = agent.summary()
    confirmed = _c(f"{summary['treasury_usdt']:,.6f} USDT", GREEN)
    expected = summary["expected_usdt"]
    hours = summary["store"]["hours_spent"]
    print()
    print(_c("  Earnings", BOLD))
    print(f"    confirmed on-chain : {confirmed}")
    print(f"    still expected     : {expected:,.6f} USDT {_c('(not income)', GREY)}")
    print(f"    hours logged       : {hours:,.2f}")
    print()
    print(_c("  channel        verdict      orders  paid   confirmed USDT   USDT/h", GREY))
    for name, data in summary["channels"].items():
        cal = data["calibration"]
        verdict_color = {"proven": GREEN, "promising": CYAN, "unproven": YELLOW, "untried": GREY}
        print(f"    {name:<13} {_c(cal['verdict'], verdict_color.get(cal['verdict'], GREY)):<20}"
              f"{cal['orders']:>4}{cal['paid']:>6}{data['confirmed_usdt']:>16,.4f}"
              f"{cal['realized_usdt_per_hour']:>9,.1f}")
    print()
    ladder = summary["ladder"]
    print(f"  stage {ladder['completed']}/{ladder['total']}: {_c(ladder['stage'], BOLD)}")
    print()


def cmd_report(args: argparse.Namespace, cfg: AgentConfig) -> int:
    agent, ledger = _build(cfg)
    if args.json:
        print(json.dumps(agent.summary(), indent=2, default=str))
    else:
        _print_report(agent)
        ok, msg = ledger.verify()
        print(_c(("  ✓ " if ok else "  ✗ ") + msg, GREEN if ok else RED))
        print()
    ledger.close()
    return 0


EARN_COMMANDS: dict[str, Any] = {
    "setup": cmd_setup,
    "channels": cmd_channels,
    "wallet": cmd_wallet,
    "scan": cmd_scan,
    "run": cmd_run,
    "collect": cmd_collect,
    "take": cmd_take,
    "approvals": cmd_approvals,
    "approve": cmd_approve,
    "reject": cmd_reject,
    "log-hours": cmd_log_hours,
    "invoice": cmd_invoice,
    "serve": cmd_serve,
    "report": cmd_report,
}


def dispatch(args: argparse.Namespace, cfg: AgentConfig) -> int:
    handler = EARN_COMMANDS.get(args.earn_command)
    if handler is None:
        print(f"unknown earn command {args.earn_command!r}", file=sys.stderr)
        return 2
    return handler(args, cfg)
