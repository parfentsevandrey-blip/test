"""Markdown / JSON reporting straight off the ledger.

Deliberately includes the unflattering numbers — costs paid, refusal reasons,
p-values, worst trade — because a report that only shows the equity curve is a
marketing asset, not an operational tool.
"""

from __future__ import annotations

import json
import time
from datetime import UTC, datetime
from typing import Any

from .ledger import Ledger
from .statistics import assess, max_drawdown, summarize


def _ts(ts: float) -> str:
    return datetime.fromtimestamp(ts, tz=UTC).strftime("%Y-%m-%d %H:%M:%S UTC")


def build_report(ledger: Ledger, summary: dict[str, Any] | None = None) -> dict[str, Any]:
    """Collect every number worth reporting into one dict."""
    curve = ledger.equity_curve()
    equity_values = [e for _, e in curve]
    all_returns = ledger.closed_returns()
    per_strategy = ledger.strategy_stats()
    n_trials = max(1, len(per_strategy))

    verdicts = {
        name: assess(name, ledger.closed_returns(name), n_trials=n_trials)
        for name in per_strategy
    }
    ok, chain_msg = ledger.verify()

    return {
        "generated_at": time.time(),
        "summary": summary or {},
        "totals": ledger.totals(),
        "overall": summarize(all_returns, equity_values),
        "max_drawdown": max_drawdown(equity_values) if equity_values else 0.0,
        "equity_points": len(curve),
        "first_equity": equity_values[0] if equity_values else 0.0,
        "last_equity": equity_values[-1] if equity_values else 0.0,
        "strategies": per_strategy,
        "verdicts": {
            k: {
                "n": v.n, "mean_bps": v.mean_bps, "sharpe": v.sharpe,
                "p_value": v.p_value, "deflated_sharpe": v.deflated_sharpe,
                "proven": v.proven, "label": v.label, "reason": v.reason,
            }
            for k, v in verdicts.items()
        },
        "ledger_integrity": {"ok": ok, "message": chain_msg},
        "recent_events": ledger.events(limit=15),
    }


def to_markdown(report: dict[str, Any]) -> str:
    s = report.get("summary") or {}
    totals = report["totals"]
    overall = report["overall"]
    lines: list[str] = []
    add = lines.append

    add("# USDT Agent — run report")
    add("")
    add(f"_generated {_ts(report['generated_at'])}_")
    add("")

    # Only render the Result block for a real run summary. `usdt-agent report`
    # reads the ledger without one, and a table of zeroes reads as "lost it all".
    if "equity" in s:
        pnl = float(s.get("pnl", 0.0))
        add("## Result")
        add("")
        add("| metric | value |")
        add("| --- | ---: |")
        add(f"| mode | `{s.get('mode', '?')}` |")
        add(f"| data source | `{s.get('data_source', '?')}` |")
        add(f"| broker | `{s.get('broker', '?')}` |")
        add(f"| cycles | {s.get('cycles', 0)} |")
        add(f"| starting equity | {float(s.get('starting_equity', 0)):,.2f} USDT |")
        add(f"| final equity | **{float(s.get('equity', 0)):,.2f} USDT** |")
        add(f"| net PnL | **{pnl:+,.4f} USDT** ({float(s.get('return_pct', 0)):+.3f} %) |")
        add(f"| swept to treasury | {float(s.get('treasury', 0)):,.4f} USDT |")
        add(f"| still open | {s.get('open_trades', 0)} positions |")
        add("")

    add("## Trading")
    add("")
    add("| metric | value |")
    add("| --- | ---: |")
    add(f"| closed trades | {int(totals['closed_trades'])} |")
    add(f"| gross accrued | {totals['gross']:+,.4f} USDT |")
    add(f"| costs paid | −{abs(totals['costs']):,.4f} USDT |")
    add(f"| realized PnL | {totals['realized_pnl']:+,.4f} USDT |")
    add(f"| mean return / trade | {overall['mean_bps']:+.2f} bps |")
    add(f"| stdev / trade | {overall['stdev_bps']:.2f} bps |")
    add(f"| win rate | {overall['win_rate']:.1%} |")
    add(f"| best / worst | {overall['best_bps']:+.1f} / {overall['worst_bps']:+.1f} bps |")
    add(f"| max drawdown | {report['max_drawdown']:.2%} |")
    add("")

    if report["strategies"]:
        add("## Per strategy")
        add("")
        add("| strategy | trades | PnL (USDT) | mean bps | win rate | p-value | verdict |")
        add("| --- | ---: | ---: | ---: | ---: | ---: | --- |")
        for name in sorted(report["strategies"], key=lambda k: -report["strategies"][k]["pnl"]):
            st = report["strategies"][name]
            v = report["verdicts"].get(name, {})
            add(
                f"| `{name}` | {int(st['n'])} | {st['pnl']:+,.4f} | {st['avg_bps']:+.2f} | "
                f"{st['win_rate']:.0%} | {v.get('p_value', 1.0):.3f} | "
                f"{'**proven**' if v.get('proven') else v.get('label', '—')} |"
            )
        add("")
        add("> `proven` means the realized returns are statistically distinguishable")
        add("> from zero after a multiple-testing correction — not that they will")
        add("> continue. Only proven strategies receive capital in live mode.")
        add("")

    integrity = report["ledger_integrity"]
    add("## Ledger integrity")
    add("")
    add(f"{'✅' if integrity['ok'] else '❌'} {integrity['message']}")
    add("")

    if s.get("risk"):
        risk = s["risk"]
        add("## Risk")
        add("")
        add(f"- high-water equity: {risk.get('high_water', 0):,.2f} USDT")
        add(f"- drawdown: {float(risk.get('drawdown', 0)):.2%} of a {float(risk.get('drawdown_limit', 0)):.0%} limit")
        add(f"- kill switch: {'**TRIPPED** — ' + str(risk.get('halt_reason')) if risk.get('halted') else 'armed, not tripped'}")
        if risk.get("top_refusals"):
            add(f"- most common refusals: {', '.join(f'{k} ({v})' for k, v in risk['top_refusals'].items())}")
        add("")

    add("---")
    add("")
    add("_Paper results are not a forecast. Fees, latency and adverse selection are")
    add("modelled, but a simulator cannot model a venue halting withdrawals._")
    return "\n".join(lines)


def to_json(report: dict[str, Any]) -> str:
    return json.dumps(report, indent=2, sort_keys=True, default=str)
