"""Поиск арбитражных возможностей в два этапа.

Этап 1 — отбор. Один запрос `/spot/tickers` отдаёт верх стакана по всем ~2200
парам. По ним считается грубая оценка цикла и отбираются кандидаты.

Этап 2 — проверка. Для кандидатов запрашиваются живые стаканы, и цикл считается
честно: проходом по уровням, с комиссией на каждом звене и проверкой минимальных
объёмов биржи.

Два этапа нужны потому, что тикеры кешируются и заметно отстают от стакана:
на BTC_USDT ticker.lowest_ask регулярно оказывается ниже реального лучшего ask.
Торговать по таким данным — гарантированный убыток, а вот сузить 300 циклов до
десятка кандидатов они годятся.
"""

from __future__ import annotations

import logging
import time
from dataclasses import dataclass, field
from decimal import Decimal
from typing import Iterable, Optional, Sequence

from ..exchange.base import Exchange, ExchangeError
from ..types import OrderBook, PairSpec, Side, Ticker
from .triangle import Opportunity, Triangle, best_size, check_minimums, find_triangles

log = logging.getLogger(__name__)

# Ликвидные валюты, через которые вообще имеет смысл строить циклы.
DEFAULT_MIDDLE = ("BTC", "ETH", "USDC")


@dataclass
class ScanResult:
    opportunities: list[Opportunity] = field(default_factory=list)
    triangles: int = 0
    screened: int = 0
    verified: int = 0
    rejected: dict[str, str] = field(default_factory=dict)
    elapsed: float = 0.0
    best_screen_pct: Optional[Decimal] = None

    @property
    def best(self) -> Optional[Opportunity]:
        return self.opportunities[0] if self.opportunities else None


class TriangleScanner:
    def __init__(
        self,
        exchange: Exchange,
        *,
        base: str = "USDT",
        fee: Decimal = Decimal("0.002"),
        min_quote_volume: Decimal = Decimal(100_000),
        allowed_middle: Optional[Iterable[str]] = DEFAULT_MIDDLE,
        max_verify: int = 8,
        book_depth: int = 20,
    ):
        self.exchange = exchange
        self.base = base
        self.fee = fee
        self.min_quote_volume = min_quote_volume
        self.allowed_middle = allowed_middle
        self.max_verify = max_verify
        self.book_depth = book_depth

        self.specs: dict[str, PairSpec] = {}
        self.triangles: list[Triangle] = []

    # ------------------------------------------------------------- Вселенная

    def load_universe(self) -> int:
        """Скачать список пар и построить все треугольники. Достаточно раз в час."""
        pairs = self.exchange.get_all_pairs()
        self.specs = {p.symbol: p for p in pairs}
        self.triangles = find_triangles(
            pairs, base=self.base, allowed_middle=self.allowed_middle
        )
        log.info(
            "Пар: %s, треугольников с базой %s: %s",
            len(pairs),
            self.base,
            len(self.triangles),
        )
        return len(self.triangles)

    # ----------------------------------------------------------------- Отбор

    def screen(self, tickers: dict[str, Ticker]) -> list[tuple[Decimal, Triangle]]:
        """Грубая оценка всех циклов по верху стакана из тикеров."""
        scored: list[tuple[Decimal, Triangle]] = []
        for triangle in self.triangles:
            if not self._liquid_enough(triangle, tickers):
                continue
            profit = self._approx_profit(triangle, tickers)
            if profit is not None:
                scored.append((profit, triangle))
        scored.sort(key=lambda x: x[0], reverse=True)
        return scored

    def _liquid_enough(self, triangle: Triangle, tickers: dict[str, Ticker]) -> bool:
        for symbol in triangle.symbols:
            ticker = tickers.get(symbol)
            if ticker is None or ticker.quote_volume < self.min_quote_volume:
                return False
        return True

    def _approx_profit(
        self, triangle: Triangle, tickers: dict[str, Ticker]
    ) -> Optional[Decimal]:
        """Доходность цикла по лучшим ценам, без учёта объёма.

        Оценка заведомо оптимистична: она предполагает, что на лучшей цене
        стоит сколько угодно объёма. Служит только фильтром.
        """
        amount = Decimal(1)
        for leg in triangle.legs:
            ticker = tickers.get(leg.symbol)
            if ticker is None or ticker.ask <= 0 or ticker.bid <= 0:
                return None
            amount = amount / ticker.ask if leg.side is Side.BUY else amount * ticker.bid
            amount *= 1 - self.fee
        return amount - 1

    # -------------------------------------------------------------- Проверка

    def verify(
        self, triangles: Sequence[Triangle], amount: Decimal
    ) -> tuple[list[Opportunity], dict[str, str]]:
        """Пересчитать кандидатов по живым стаканам."""
        books = self._fetch_books(triangles)
        found: list[Opportunity] = []
        rejected: dict[str, str] = {}

        for triangle in triangles:
            opportunity = best_size(triangle, books, amount, self.fee)
            if opportunity is None:
                rejected[triangle.path] = "нет стакана или нулевая глубина"
                continue
            if not opportunity.is_profitable:
                rejected[triangle.path] = f"убыток по стакану {opportunity.profit_pct:+.4%}"
                continue
            problem = check_minimums(triangle, self.specs, opportunity)
            if problem:
                rejected[triangle.path] = problem
                continue
            found.append(opportunity)

        found.sort(key=lambda o: o.profit_pct, reverse=True)
        return found, rejected

    def _fetch_books(self, triangles: Sequence[Triangle]) -> dict[str, OrderBook]:
        """Стаканы для всех пар кандидатов; пары дедуплицируются.

        Свежесть здесь важнее полноты: чем дольше собираем стаканы, тем больше
        шанс, что первый из них устареет к моменту расчёта.
        """
        symbols = {symbol for t in triangles for symbol in t.symbols}
        books: dict[str, OrderBook] = {}
        for symbol in symbols:
            try:
                books[symbol] = self.exchange.get_order_book(symbol, self.book_depth)
            except ExchangeError as exc:
                log.warning("Стакан %s недоступен: %s", symbol, exc)
        return books

    # ------------------------------------------------------------------ Скан

    def scan(self, amount: Decimal) -> ScanResult:
        started = time.time()
        if not self.triangles:
            self.load_universe()

        tickers = self.exchange.get_all_tickers()
        scored = self.screen(tickers)
        candidates = [t for profit, t in scored[: self.max_verify] if profit > -self.fee]

        opportunities, rejected = ([], {})
        if candidates:
            opportunities, rejected = self.verify(candidates, amount)

        return ScanResult(
            opportunities=opportunities,
            triangles=len(self.triangles),
            screened=len(scored),
            verified=len(candidates),
            rejected=rejected,
            elapsed=time.time() - started,
            best_screen_pct=scored[0][0] if scored else None,
        )
