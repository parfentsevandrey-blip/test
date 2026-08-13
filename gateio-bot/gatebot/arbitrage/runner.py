"""Цикл арбитража: сканирование, статистика, при необходимости — исполнение.

По умолчанию бот только наблюдает и ведёт статистику. Это не осторожность ради
осторожности: частота реальных возможностей — главный неизвестный параметр, и
узнать его можно только наблюдением на конкретной бирже в конкретное время.
Счётчики (сколько циклов проверено, какое максимальное расхождение видели,
сколько прошло порог) отвечают на вопрос «а есть ли тут вообще что ловить»
до того, как в дело пойдут деньги.
"""

from __future__ import annotations

import logging
import signal
import time
from dataclasses import dataclass, field
from decimal import Decimal
from typing import Optional

from ..exchange.base import Exchange, ExchangeError
from .executor import ExecutionResult, TriangleExecutor
from .scanner import ScanResult, TriangleScanner

log = logging.getLogger(__name__)


@dataclass
class ArbStats:
    scans: int = 0
    errors: int = 0
    profitable_seen: int = 0
    above_threshold: int = 0
    executed: int = 0
    succeeded: int = 0
    best_pct: Optional[Decimal] = None
    best_path: str = ""
    realized: Decimal = Decimal(0)
    started: float = field(default_factory=time.time)

    def observe(self, result: ScanResult, threshold: Decimal) -> None:
        self.scans += 1
        for opportunity in result.opportunities:
            self.profitable_seen += 1
            if opportunity.profit_pct >= threshold:
                self.above_threshold += 1
            if self.best_pct is None or opportunity.profit_pct > self.best_pct:
                self.best_pct = opportunity.profit_pct
                self.best_path = opportunity.triangle.path

    def summary(self, threshold: Decimal) -> str:
        minutes = (time.time() - self.started) / 60
        best = f"{self.best_pct:+.4%} ({self.best_path})" if self.best_pct is not None else "—"
        return (
            f"За {minutes:.1f} мин: сканирований {self.scans}, ошибок {self.errors}\n"
            f"  Прибыльных по стакану: {self.profitable_seen}\n"
            f"  Выше порога {threshold:+.2%}: {self.above_threshold}\n"
            f"  Лучшее расхождение: {best}\n"
            f"  Исполнено: {self.executed} (успешно {self.succeeded}), "
            f"итог {self.realized:+.4f}"
        )


class ArbitrageRunner:
    def __init__(
        self,
        exchange: Exchange,
        scanner: TriangleScanner,
        executor: TriangleExecutor,
        *,
        trade_quote: Decimal = Decimal(100),
        poll_seconds: float = 2.0,
        universe_ttl: int = 3600,
        report_every: int = 30,
    ):
        self.exchange = exchange
        self.scanner = scanner
        self.executor = executor
        self.trade_quote = trade_quote
        self.poll_seconds = poll_seconds
        self.universe_ttl = universe_ttl
        self.report_every = report_every

        self.stats = ArbStats()
        self._running = False
        self._universe_loaded = 0.0

    # ------------------------------------------------------------------ Шаг

    def once(self) -> ScanResult:
        self._refresh_universe()
        result = self.scanner.scan(self.trade_quote)
        self.stats.observe(result, self.executor.min_profit_pct)

        for opportunity in result.opportunities:
            if opportunity.profit_pct < self.executor.min_profit_pct:
                continue
            outcome = self.executor.execute(opportunity)
            self.stats.executed += 1
            if outcome.ok:
                self.stats.succeeded += 1
                self.stats.realized += outcome.end_amount - outcome.start_amount
            else:
                log.warning("%s", outcome.summary())
            # Один цикл за скан: после сделки стаканы уже другие.
            break

        return result

    def _refresh_universe(self) -> None:
        """Список пар меняется редко — перечитываем раз в час."""
        if time.time() - self._universe_loaded > self.universe_ttl:
            self.scanner.load_universe()
            self.executor.specs = self.scanner.specs
            self._universe_loaded = time.time()

    # ----------------------------------------------------------------- Цикл

    def run(self, duration: Optional[float] = None) -> ArbStats:
        self._running = True
        for sig in (signal.SIGINT, signal.SIGTERM):
            signal.signal(sig, self._on_signal)

        deadline = time.time() + duration if duration else None
        mode = "СУХОЙ ПРОГОН" if self.executor.dry_run else "ИСПОЛНЕНИЕ ОРДЕРОВ"
        log.info(
            "Арбитраж запущен (%s): база %s, размер %s, порог %+.3f%%",
            mode,
            self.scanner.base,
            self.trade_quote,
            float(self.executor.min_profit_pct) * 100,
        )

        while self._running:
            started = time.time()
            try:
                result = self.once()
                self._log_scan(result)
            except ExchangeError as exc:
                self.stats.errors += 1
                log.error("Ошибка биржи: %s", exc)
            except Exception:  # noqa: BLE001 — один сбой не должен ронять цикл
                self.stats.errors += 1
                log.exception("Непредвиденная ошибка при сканировании")

            if self.executor.halted:
                log.error("Останавливаемся: %s", self.executor.halt_reason)
                break
            if deadline and time.time() >= deadline:
                break

            self._sleep(max(0.0, self.poll_seconds - (time.time() - started)))

        log.info("\n%s", self.stats.summary(self.executor.min_profit_pct))
        return self.stats

    def _log_scan(self, result: ScanResult) -> None:
        if self.stats.scans % self.report_every == 1 or result.opportunities:
            best = result.best
            if best:
                log.info("Найдено: %s", best)
            else:
                screen = (
                    f"{result.best_screen_pct:+.4%}"
                    if result.best_screen_pct is not None
                    else "—"
                )
                log.info(
                    "Скан %s: циклов %s, проверено %s, возможностей нет "
                    "(лучшая грубая оценка %s, %.2f с)",
                    self.stats.scans,
                    result.triangles,
                    result.verified,
                    screen,
                    result.elapsed,
                )

    def _on_signal(self, signum: int, _frame) -> None:
        log.info("Получен сигнал %s — завершаем", signum)
        self._running = False

    def _sleep(self, seconds: float) -> None:
        deadline = time.time() + seconds
        while self._running and time.time() < deadline:
            time.sleep(min(0.5, deadline - time.time()))
