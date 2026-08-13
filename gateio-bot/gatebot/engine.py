"""Торговый движок: цикл «данные -> стратегия -> риск -> ордера».

Ключевая идея — реконсиляция. Стратегия описывает, какие ордера должны стоять
на бирже; движок сравнивает это с фактом и доводит состояние до желаемого:
лишнее снимает, недостающее выставляет, совпадающее не трогает. Поэтому обрыв
связи или перезапуск не ломают логику — на следующем тике всё сойдётся само.

Учёт позиции ведётся по собственным исполнениям бота, а не по балансу счёта:
на том же аккаунте могут лежать монеты, купленные руками, и бот не должен
считать их своими и продавать.
"""

from __future__ import annotations

import logging
import signal
import time
from dataclasses import dataclass, replace
from decimal import Decimal
from typing import Callable, Optional

from .config import Config, interval_seconds
from .exchange.base import Exchange, ExchangeError
from .risk import RiskManager
from .state import StateStore
from .strategies.base import Strategy, StrategyContext
from .types import (
    Candle,
    Decision,
    DesiredOrder,
    Fill,
    Order,
    OrderType,
    Position,
    Side,
)

log = logging.getLogger(__name__)


@dataclass
class TickReport:
    """Что произошло за один тик — удобно для логов и тестов."""

    ts: int
    price: Decimal
    equity: Decimal
    note: str = ""
    placed: int = 0
    cancelled: int = 0
    fills: list[Fill] = None  # type: ignore[assignment]
    risk_notes: list[str] = None  # type: ignore[assignment]
    skipped: str = ""

    def __post_init__(self) -> None:
        self.fills = self.fills or []
        self.risk_notes = self.risk_notes or []


class TradingEngine:
    def __init__(
        self,
        config: Config,
        exchange: Exchange,
        strategy: Strategy,
        risk: RiskManager,
        store: Optional[StateStore] = None,
        clock: Callable[[], float] = time.time,
    ):
        # Часы вынесены в параметр, чтобы бэктест мог прогнать ровно этот же
        # код на исторических данных, подставив время свечи вместо реального.
        self._now = clock
        self.config = config
        self.exchange = exchange
        self.strategy = strategy
        self.risk = risk
        self.store = store
        self.symbol = config.symbol
        self.spec = exchange.get_pair_spec(config.symbol)
        self.position = Position(symbol=config.symbol)
        self.last_candle_ts = 0
        self._running = False
        # Ордера, которые выставил бот, — только по ним мы ищем исполнения.
        self._tracked: dict[str, Order] = {}
        # Рыночные ордера срабатывают по фронту: пока стратегия просит один и тот
        # же client_id, повторно он не отправляется. Сбрасывается, когда просить
        # перестали. Без этого сигнальная стратегия купила бы на каждом тике.
        self._fired_market: set[str] = set()

        if self.store:
            self.position, self.risk.state, self.last_candle_ts = self.store.load(self.symbol)

    # ------------------------------------------------------------------ Цикл

    def run(self) -> None:
        """Бесконечный цикл до Ctrl+C / SIGTERM."""
        self._running = True
        for sig in (signal.SIGINT, signal.SIGTERM):
            signal.signal(sig, self._on_signal)

        log.info(
            "Старт: %s %s, режим=%s, стратегия=%s",
            self.symbol,
            self.config.interval,
            self.config.mode,
            self.strategy.describe(),
        )
        while self._running:
            started = time.time()
            try:
                report = self.tick()
                self._log_report(report)
            except ExchangeError as exc:
                log.error("Ошибка биржи: %s", exc)
            except Exception:  # noqa: BLE001 — цикл не должен падать из-за одного тика
                log.exception("Непредвиденная ошибка на тике")

            elapsed = time.time() - started
            self._sleep(max(0.0, self.config.poll_seconds - elapsed))

        log.info("Остановлен. Позиция: %s %s", self.position.amount, self.spec.base)

    def _on_signal(self, signum: int, _frame) -> None:
        log.info("Получен сигнал %s — завершаем после текущего тика", signum)
        self._running = False

    def _sleep(self, seconds: float) -> None:
        """Спим короткими шагами, чтобы Ctrl+C срабатывал сразу."""
        deadline = time.time() + seconds
        while self._running and time.time() < deadline:
            time.sleep(min(1.0, deadline - time.time()))

    # ------------------------------------------------------------------- Тик

    def tick(self) -> TickReport:
        candles = self.exchange.get_candles(
            self.symbol, self.config.interval, self.config.candles
        )
        candles = self._closed_only(candles)
        price = self.exchange.get_price(self.symbol)
        now = int(self._now())

        # Сначала подбираем исполнения: позиция должна быть актуальной до того,
        # как стратегия увидит контекст.
        fills = self._collect_fills()

        if len(candles) < self.strategy.warmup:
            return TickReport(
                ts=now,
                price=price,
                equity=self._equity(price),
                fills=fills,
                skipped=f"нужно {self.strategy.warmup} свечей, есть {len(candles)}",
            )

        ctx = self._context(candles, price, now)
        last_ts = candles[-1].ts
        new_bar = last_ts != self.last_candle_ts

        decision = self.strategy.decide(ctx)
        verdict = self.risk.review(ctx, decision)
        decision = verdict.decision

        placed = cancelled = 0
        if decision.flatten:
            cancelled += self._cancel_all()
            placed += self._flatten(price)
        else:
            cancelled, placed = self._reconcile(decision.desired, price)

        self.last_candle_ts = last_ts
        self._persist()

        return TickReport(
            ts=now,
            price=price,
            equity=ctx.equity,
            note=decision.note if new_bar or placed or cancelled else "",
            placed=placed,
            cancelled=cancelled,
            fills=fills,
            risk_notes=verdict.reasons,
        )

    def _closed_only(self, candles: list[Candle]) -> list[Candle]:
        """Отбрасываем текущую (незакрытую) свечу.

        Её high/low/close меняются в реальном времени, и индикатор на ней
        «перерисовывается»: сигнал может появиться и исчезнуть внутри бара.
        """
        if not candles:
            return candles
        span = interval_seconds(self.config.interval)
        now = self._now()
        return [c for c in candles if c.ts + span <= now]

    def _context(self, candles: list[Candle], price: Decimal, now: int) -> StrategyContext:
        balances = self.exchange.get_balances()
        return StrategyContext(
            symbol=self.symbol,
            spec=self.spec,
            candles=candles,
            price=price,
            position=self.position,
            balances=balances,
            equity=self._equity(price, balances),
            now=now,
        )

    def _equity(self, price: Decimal, balances: Optional[dict] = None) -> Decimal:
        """Стоимость счёта в валюте котировки — по торгуемой паре.

        Активы других пар не учитываются: бот отвечает только за свою пару.
        Для чистого учёта держите его на отдельном суб-аккаунте.
        """
        balances = balances if balances is not None else self.exchange.get_balances()
        quote = balances.get(self.spec.quote)
        base = balances.get(self.spec.base)
        total = quote.total if quote else Decimal(0)
        if base:
            total += base.total * price
        return total

    # ------------------------------------------------------------ Исполнения

    def _collect_fills(self) -> list[Fill]:
        """Опросить свои ордера и превратить прирост исполнения в события."""
        fills: list[Fill] = []
        for order_id, tracked in list(self._tracked.items()):
            try:
                current = self.exchange.get_order(self.symbol, order_id)
            except ExchangeError as exc:
                log.warning("Не удалось получить ордер %s: %s", order_id, exc)
                continue

            delta = current.filled - tracked.filled
            if delta > 0:
                fill = self._to_fill(current, delta, tracked.filled)
                fills.append(fill)
                self.position.apply(fill)
                log.info(
                    "Исполнено: %s %s %s @ %s (%s)",
                    fill.side.value,
                    fill.amount,
                    self.spec.base,
                    fill.price,
                    fill.client_id,
                )
            tracked.filled = current.filled
            tracked.avg_price = current.avg_price
            tracked.fee = current.fee
            tracked.fee_currency = current.fee_currency
            if not current.is_open:
                self._tracked.pop(order_id, None)
        return fills

    def _to_fill(self, order: Order, delta: Decimal, already: Decimal) -> Fill:
        price = order.avg_price or order.price or Decimal(0)
        return Fill(
            ts=int(self._now()),
            symbol=self.symbol,
            side=order.side,
            amount=delta,
            price=price,
            fee=self._fee_in_quote(order, delta, price, already),
            client_id=order.client_id,
        )

    def _fee_in_quote(
        self, order: Order, delta: Decimal, price: Decimal, already: Decimal
    ) -> Decimal:
        """Комиссию приводим к валюте котировки.

        Биржа может списать её в базовой валюте (покупка), в котировке (продажа)
        или в GT при включённой скидке. Если валюта комиссии неизвестна, считаем
        по ставке из конфига — заниженная оценка PnL хуже завышенной.
        """
        total_filled = already + delta
        if order.fee > 0 and total_filled > 0:
            share = order.fee * (delta / total_filled)
            if order.fee_currency == self.spec.quote:
                return share
            if order.fee_currency == self.spec.base:
                return share * price
        rate = (
            self.config.exchange.taker_fee
            if order.type is OrderType.MARKET
            else self.config.exchange.maker_fee
        )
        return delta * price * rate

    # ----------------------------------------------------------- Реконсиляция

    def _reconcile(self, desired: list[DesiredOrder], price: Decimal) -> tuple[int, int]:
        """Привести биржу к желаемому состоянию. Возвращает (снято, выставлено)."""
        limits = {d.client_id: d for d in desired if d.price is not None}
        markets = [d for d in desired if d.price is None]

        # Сбрасываем «взвод» рыночных ордеров, которых стратегия больше не просит.
        wanted_market = {d.client_id for d in markets}
        self._fired_market &= wanted_market

        open_orders = self.exchange.get_open_orders(self.symbol)
        live = {o.client_id: o for o in open_orders if o.client_id}

        cancelled = 0
        fresh: set[str] = set()
        for client_id, order in live.items():
            want = limits.get(client_id)
            if want is not None and self._same(order, want):
                fresh.add(client_id)  # уже стоит как надо — не трогаем
            elif self._cancel(order):
                cancelled += 1

        placed = 0
        for client_id, want in limits.items():
            if client_id in fresh:
                continue
            if self._place(want, price):
                placed += 1

        for want in markets:
            if want.client_id in self._fired_market:
                continue
            if self._place(want, price):
                self._fired_market.add(want.client_id)
                placed += 1

        return cancelled, placed

    def _same(self, order: Order, want: DesiredOrder) -> bool:
        """Ордер считается совпадающим только при точном равенстве цены и объёма
        (оба уже приведены к точности пара). Иначе переставляем."""
        if order.side is not want.side or want.price is None or order.price is None:
            return False
        return (
            self.spec.quantize_price(order.price) == self.spec.quantize_price(want.price)
            and self.spec.quantize_amount(order.amount)
            == self.spec.quantize_amount(want.amount)
        )

    def _place(self, want: DesiredOrder, price: Decimal) -> bool:
        order = Order(
            client_id=want.client_id,
            symbol=self.symbol,
            side=want.side,
            type=want.type,
            amount=want.amount,
            price=self.spec.quantize_price(want.price) if want.price is not None else None,
        )

        if order.type is OrderType.LIMIT:
            order.amount = self.spec.quantize_amount(order.amount)
            if not self.spec.is_tradable(order.amount, order.price or price):
                log.debug("Пропуск %s: объём %s ниже минимума", want.client_id, order.amount)
                return False
        elif order.side is Side.BUY:
            # Рыночная покупка на Gate.io измеряется суммой в котировке.
            if order.amount < self.spec.min_quote_amount:
                log.debug("Пропуск %s: сумма %s ниже минимума", want.client_id, order.amount)
                return False
        else:
            order.amount = self.spec.quantize_amount(order.amount)
            if not self.spec.is_tradable(order.amount, price):
                return False

        try:
            result = self.exchange.place_order(order)
        except ExchangeError as exc:
            log.error("Не выставлен %s: %s", want.client_id, exc)
            return False

        result.client_id = want.client_id
        self._track(result)
        log.info(
            "Выставлен %s: %s %s %s @ %s",
            want.client_id,
            result.side.value,
            result.amount,
            self.spec.base if result.type is OrderType.LIMIT else "",
            result.price or "рынок",
        )
        # Рыночный ордер исполняется сразу — учитываем не дожидаясь следующего тика.
        if result.filled > 0:
            fill = self._to_fill(result, result.filled, Decimal(0))
            self.position.apply(fill)
            self._tracked.pop(result.order_id, None)
        return True

    def _track(self, order: Order) -> None:
        """Запомнить СНИМОК ордера, а не сам объект.

        `PaperExchange` (и тесты) отдают ту же самую сущность, что лежит у них
        внутри; если сохранить ссылку, то при исполнении `tracked.filled` и
        `current.filled` изменятся вместе, разница окажется нулевой и сделка
        никогда не попадёт в позицию.
        """
        self._tracked[order.order_id] = replace(order)

    def _cancel(self, order: Order) -> bool:
        try:
            self.exchange.cancel_order(self.symbol, order.order_id)
        except ExchangeError as exc:
            log.warning("Не снят ордер %s: %s", order.order_id, exc)
            return False
        log.info("Снят %s (%s @ %s)", order.client_id, order.side.value, order.price)
        return True

    def _cancel_all(self) -> int:
        cancelled = 0
        for order in self.exchange.get_open_orders(self.symbol):
            if self._cancel(order):
                cancelled += 1
        return cancelled

    def _flatten(self, price: Decimal) -> int:
        """Закрыть позицию по рынку. Продаём только то, что купил бот."""
        amount = self.spec.quantize_amount(self.position.amount)
        if amount <= 0:
            return 0
        # Баланс мог измениться (ручные сделки, комиссия в базовой валюте) —
        # больше, чем есть, продать нельзя.
        balances = self.exchange.get_balances()
        available = balances[self.spec.base].available if self.spec.base in balances else amount
        amount = self.spec.quantize_amount(min(amount, available))
        if not self.spec.is_tradable(amount, price):
            log.warning("Нечего закрывать: доступно %s %s", amount, self.spec.base)
            return 0

        order = Order(
            client_id="flatten",
            symbol=self.symbol,
            side=Side.SELL,
            type=OrderType.MARKET,
            amount=amount,
        )
        try:
            result = self.exchange.place_order(order)
        except ExchangeError as exc:
            log.error("Не удалось закрыть позицию: %s", exc)
            return 0

        result.client_id = "flatten"
        if result.filled > 0:
            self.position.apply(self._to_fill(result, result.filled, Decimal(0)))
        else:
            self._track(result)
        log.info("Позиция закрыта по рынку: %s %s", amount, self.spec.base)
        return 1

    # ------------------------------------------------------------------ Прочее

    def _persist(self) -> None:
        if self.store:
            self.store.save(self.symbol, self.position, self.risk.state, self.last_candle_ts)

    def _log_report(self, report: TickReport) -> None:
        if report.skipped:
            log.info("Пропуск тика: %s", report.skipped)
            return
        for note in report.risk_notes:
            log.warning("Риск: %s", note)
        if report.note:
            log.info("Стратегия: %s", report.note)
        log.info(
            "Цена %s | счёт %.2f | позиция %s @ %s | PnL реализ. %.2f / плав. %.2f",
            report.price,
            report.equity,
            self.position.amount,
            self.position.avg_entry or "-",
            self.position.realized_pnl,
            self.position.unrealized_pnl(report.price),
        )
