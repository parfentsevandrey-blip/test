"""A live terminal dashboard in pure ANSI — no curses, no dependencies.

Renders after every cycle by redrawing in place. Degrades to plain log lines
when stdout is not a TTY (a pipe, a systemd journal, a CI log), so the same
binary is usable interactively and as a daemon.
"""

from __future__ import annotations

import os
import shutil
import sys
from typing import TYPE_CHECKING

if TYPE_CHECKING:  # pragma: no cover
    from .agent import Agent, CycleReport

RESET = "\033[0m"
BOLD = "\033[1m"
DIM = "\033[2m"
RED = "\033[31m"
GREEN = "\033[32m"
YELLOW = "\033[33m"
BLUE = "\033[34m"
CYAN = "\033[36m"
GREY = "\033[90m"

SPARK = "▁▂▃▄▅▆▇█"


def supports_color() -> bool:
    if os.environ.get("NO_COLOR"):
        return False
    return sys.stdout.isatty() and os.environ.get("TERM", "") not in ("", "dumb")


def paint(text: str, color: str, enabled: bool = True) -> str:
    return f"{color}{text}{RESET}" if enabled and color else text


def money(value: float, enabled: bool = True) -> str:
    color = GREEN if value > 0 else (RED if value < 0 else GREY)
    return paint(f"{value:+,.4f}", color, enabled)


def sparkline(values: list[float], width: int = 40) -> str:
    if len(values) < 2:
        return ""
    data = values[-width:]
    lo, hi = min(data), max(data)
    if hi - lo < 1e-12:
        return SPARK[len(SPARK) // 2] * len(data)
    return "".join(SPARK[min(len(SPARK) - 1, int((v - lo) / (hi - lo) * (len(SPARK) - 1)))] for v in data)


def bar(fraction: float, width: int = 18) -> str:
    filled = max(0, min(width, round(fraction * width)))
    return "█" * filled + "·" * (width - filled)


class Dashboard:
    """Redraws the agent's state in place after each cycle."""

    def __init__(self, enabled: bool = True) -> None:
        self.color = supports_color()
        self.tty = sys.stdout.isatty()
        self.enabled = enabled and self.tty
        self.lines_drawn = 0

    def _c(self, text: str, color: str) -> str:
        return paint(text, color, self.color)

    def render(self, report: CycleReport, agent: Agent) -> None:
        if not self.enabled:
            return
        width = shutil.get_terminal_size((100, 30)).columns
        out: list[str] = []
        cfg = agent.cfg
        start = cfg.starting_equity_usdt
        pnl = report.equity - start
        pnl_pct = (pnl / start * 100.0) if start else 0.0

        mode_color = RED if cfg.is_live else CYAN
        title = f" USDT AGENT  ·  {cfg.mode.upper()}  ·  {agent.data_source} data  ·  cycle {report.cycle} "
        out.append(self._c(title.center(width, "─"), mode_color + BOLD))

        equity_line = (
            f" equity {self._c(f'{report.equity:,.2f}', BOLD)} USDT   "
            f"pnl {money(pnl, self.color)} ({pnl_pct:+.3f}%)   "
            f"cash {report.cash:,.2f}   deployed {report.deployed:,.2f}   "
            f"treasury {self._c(f'{report.treasury:,.2f}', YELLOW)}"
        )
        out.append(equity_line)

        curve = [h.equity for h in agent.history[-width + 12 :]] or [report.equity]
        out.append(f" {self._c(sparkline(curve, max(10, width - 12)), BLUE)}")

        risk = agent.risk.status(report.equity)
        dd = float(risk["drawdown"])
        dd_color = RED if dd > cfg.risk.max_drawdown_fraction * 0.6 else GREY
        status = "HALTED" if risk["halted"] else ("DAY-STOP" if risk["day_halted"] else "running")
        out.append(
            f" risk  {self._c(status, RED if risk['halted'] else GREEN)}   "
            f"drawdown {self._c(f'{dd:.2%}', dd_color)}/{cfg.risk.max_drawdown_fraction:.0%}   "
            f"open {len(agent.open_trades)}/{cfg.risk.max_open_trades}   "
            f"opps {report.opportunities}   +{report.opened}/-{report.closed}"
        )

        out.append(self._c(" strategy        alloc              n    mean bps   posterior   edge", GREY))
        weights = agent.allocator.weights({n: cfg.strategy(n).weight for n in agent.strategies})
        stats = agent.ledger.strategy_stats()
        for name in sorted(agent.strategies):
            w = weights.get(name, 0.0)
            st = stats.get(name, {})
            post = agent.allocator.arms[name].mean if name in agent.allocator.arms else 0.0
            verdict = agent.allocator.verdicts.get(name)
            tag = verdict.label if verdict else "—"
            tag_color = GREEN if verdict and verdict.proven else (YELLOW if tag == "unproven" else GREY)
            mean_bps = st.get("avg_bps", 0.0)
            out.append(
                f" {name:<15} {self._c(bar(w), CYAN)} {w:5.1%}  "
                f"{int(st.get('n', 0)):4d}   {money(mean_bps, self.color):>10}   "
                f"{post:+7.2f}   {self._c(tag, tag_color)}"
            )

        if agent.open_trades:
            out.append(self._c(" open positions", GREY))
            for t in agent.open_trades[:6]:
                out.append(
                    f"   {t.strategy:<14} {t.label[: max(20, width - 60)]:<{max(20, width - 60)}} "
                    f"{t.notional:9,.2f}  {money(t.accrued, self.color):>10}  {t.age_s / 60:5.1f}m"
                )
            if len(agent.open_trades) > 6:
                out.append(self._c(f"   … and {len(agent.open_trades) - 6} more", GREY))

        if report.errors:
            out.append(self._c(" feed: " + "; ".join(report.errors)[: width - 8], YELLOW))
        if report.refusals:
            out.append(self._c(" refused: " + ", ".join(report.refusals)[: width - 12], GREY))

        self._paint(out, width)

    def _paint(self, lines: list[str], width: int) -> None:
        if self.lines_drawn:
            sys.stdout.write(f"\033[{self.lines_drawn}A")
        for line in lines:
            sys.stdout.write("\033[2K" + line[: width + 200] + "\n")
        self.lines_drawn = len(lines)
        sys.stdout.flush()

    def finish(self) -> None:
        if self.enabled:
            sys.stdout.write("\n")
            sys.stdout.flush()
        self.lines_drawn = 0
