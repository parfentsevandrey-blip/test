"""Исполнение арбитражного цикла.

Главный риск здесь — не убыток от неверного расчёта, а «зависшее звено»: первая
сделка прошла, вторая нет, и вместо замкнутого цикла остаётся открытая позиция в
случайной монете. Поэтому:

* каждое следующее звено считается от **фактически полученного** объёма, а не от
  запланированного;
* перед звеном объём ограничивается реальным балансом (комиссия могла быть
  списана с получаемой валюты);
* при обрыве на любом шаге исполняется откат — то, что успели купить, продаётся
  обратно в базовую валюту;
* серия неудач подряд останавливает бота: если откаты идут один за другим, значит
  расчёт расходится с реальностью, и продолжать нельзя.

Ордера — рыночные IOC: цикл имеет смысл, только пока расхождение живо, а
лимитная заявка может просто не исполниться и оставить нас в середине цепочки.
"""

from __future__ import annotations

import logging
import time
from dataclasses import dataclass, field
from decimal import Decimal
from typing import Optional

from ..exchange.base import Exchange, ExchangeError
from ..types import Order, OrderType, PairSpec, Side
from .triangle import Leg, Opportunity, Triangle

log = logging.getLogger(__name__)

# Точность суммы в валюте котировки для рыночной покупки.
_QUOTE_STEP = Decimal("0.00000001")


@dataclass
class LegResult:
    leg: Leg
    ok: bool
    sent: Decimal = Decimal(0)
    received: Decimal = Decimal(0)
    price: Decimal = Decimal(0)
    error: str = ""


@dataclass
class ExecutionResult:
    triangle: Triangle
    start_amount: Decimal
    end_amount: Decimal = Decimal(0)
    expected_pct: Decimal = Decimal(0)
    legs: list[LegResult] = field(default_factory=list)
    ok: bool = False
    unwound: bool = False
    stuck_currency: str = ""
    stuck_amount: Decimal = Decimal(0)
    error: str = ""
    dry_run: bool = False

    @property
    def realized_pct(self) -> Decimal:
        if self.start_amount <= 0:
            return Decimal(0)
        return (self.end_amount - self.start_amount) / self.start_amount

    def summary(self) -> str:
        head = "СУХОЙ ПРОГОН" if self.dry_run else ("успех" if self.ok else "неудача")
        line = (
            f"[{head}] {self.triangle.path}: ожидалось {self.expected_pct:+.4%}, "
            f"получено {self.realized_pct:+.4%} "
            f"({self.start_amount:.4f} -> {self.end_amount:.4f} {self.triangle.base})"
        )
        if self.stuck_currency:
            line += f"\n  ЗАВИСЛО: {self.stuck_amount} {self.stuck_currency}"
        if self.error:
            line += f"\n  причина: {self.error}"
        return line


class TriangleExecutor:
    def __init__(
        self,
        exchange: Exchange,
        specs: dict[str, PairSpec],
        *,
        base: str = "USDT",
        min_profit_pct: Decimal = Decimal("0.002"),
        max_failures: int = 3,
        dry_run: bool = True,
    ):
        self.exchange = exchange
        self.specs = specs
        self.base = base
        self.min_profit_pct = min_profit_pct
        self.max_failures = max_failures
        self.dry_run = dry_run

        self.failures = 0
        self.halted = False
        self.halt_reason = ""
        self.history: list[ExecutionResult] = []

    # ------------------------------------------------------------- Исполнение

    def execute(self, opportunity: Opportunity) -> ExecutionResult:
        triangle = opportunity.triangle
        result = ExecutionResult(
            triangle=triangle,
            start_amount=opportunity.start_amount,
            expected_pct=opportunity.profit_pct,
            dry_run=self.dry_run,
        )

        blocker = self._preflight(opportunity)
        if blocker:
            result.error = blocker
            return result

        if self.dry_run:
            result.end_amount = opportunity.end_amount
            result.ok = True
            log.info("Сухой прогон: %s", opportunity)
            return result

        amount = opportunity.start_amount
        holding = self.base

        for index, leg in enumerate(triangle.legs):
            try:
                leg_result = self._trade(leg, amount)
            except ExchangeError as exc:
                leg_result = LegResult(leg=leg, ok=False, error=str(exc))

            result.legs.append(leg_result)

            if not leg_result.ok or leg_result.received <= 0:
                result.error = f"звено {index + 1} ({leg}): {leg_result.error or 'нет исполнения'}"
                log.error("Цикл оборван: %s", result.error)
                self._handle_failure(result, holding, amount)
                return result

            amount = leg_result.received
            holding = leg.to_cur

            # Комиссия могла уйти из получаемой валюты — сверяемся с балансом,
            # чтобы следующее звено не попыталось продать больше, чем есть.
            if index < len(triangle.legs) - 1:
                available = self._available(holding)
                if available is not None and available < amount:
                    log.debug(
                        "Баланс %s меньше расчётного (%s < %s) — идём от факта",
                        holding, available, amount,
                    )
                    amount = available

        result.end_amount = amount
        result.ok = True
        self.failures = 0
        self.history.append(result)
        log.info("%s", result.summary())
        return result

    def _preflight(self, opportunity: Opportunity) -> str:
        """Последние проверки перед отправкой ордеров."""
        if self.halted:
            return f"остановлен: {self.halt_reason}"
        # Глубину проверяем раньше прибыли: у цикла, упёршегося в конец стакана,
        # само число прибыли посчитано на недоисполненном объёме и ничего не
        # значит — сообщать «прибыль -89%» вместо «не хватило стакана» вредно.
        if opportunity.depth_limited:
            return "объём упирается в конец видимого стакана"
        if opportunity.profit_pct < self.min_profit_pct:
            return (
                f"прибыль {opportunity.profit_pct:+.4%} ниже порога "
                f"{self.min_profit_pct:+.4%}"
            )
        if not self.dry_run:
            available = self._available(self.base)
            if available is not None and available < opportunity.start_amount:
                return (
                    f"не хватает {self.base}: нужно {opportunity.start_amount}, "
                    f"есть {available}"
                )
        return ""

    def _trade(self, leg: Leg, amount: Decimal) -> LegResult:
        spec = self.specs.get(leg.symbol)
        if spec is None:
            return LegResult(leg=leg, ok=False, error=f"нет параметров пары {leg.symbol}")

        if leg.side is Side.BUY:
            # Рыночная покупка на Gate.io задаётся суммой в валюте котировки.
            send = amount.quantize(_QUOTE_STEP)
            if spec.min_quote_amount and send < spec.min_quote_amount:
                return LegResult(
                    leg=leg, ok=False,
                    error=f"сумма {send} меньше минимума {spec.min_quote_amount} {spec.quote}",
                )
        else:
            send = spec.quantize_amount(amount)
            if spec.min_base_amount and send < spec.min_base_amount:
                return LegResult(
                    leg=leg, ok=False,
                    error=f"объём {send} меньше минимума {spec.min_base_amount} {spec.base}",
                )

        order = Order(
            client_id=f"arb-{leg.from_cur}-{leg.to_cur}",
            symbol=leg.symbol,
            side=leg.side,
            type=OrderType.MARKET,
            amount=send,
        )
        placed = self.exchange.place_order(order)

        # `filled` всегда в базовой валюте пары; получаем либо её, либо
        # выручку в котировке — смотря куда идёт звено.
        if leg.side is Side.BUY:
            received = placed.filled
        else:
            received = placed.filled * placed.avg_price

        received -= self._fee_in(leg.to_cur, placed)
        log.info(
            "Звено %s: отправлено %s %s, получено %s %s по %s",
            leg, send, leg.from_cur, received, leg.to_cur, placed.avg_price,
        )
        return LegResult(
            leg=leg,
            ok=received > 0,
            sent=send,
            received=max(received, Decimal(0)),
            price=placed.avg_price,
            error="" if received > 0 else "нулевое исполнение",
        )

    @staticmethod
    def _fee_in(currency: str, order: Order) -> Decimal:
        """Комиссия, если биржа списала её именно из получаемой валюты."""
        return order.fee if order.fee > 0 and order.fee_currency == currency else Decimal(0)

    def _available(self, currency: str) -> Optional[Decimal]:
        try:
            balances = self.exchange.get_balances()
        except ExchangeError as exc:
            log.warning("Баланс недоступен: %s", exc)
            return None
        balance = balances.get(currency)
        return balance.available if balance else Decimal(0)

    # ------------------------------------------------------------------ Откат

    def _handle_failure(
        self, result: ExecutionResult, holding: str, amount: Decimal
    ) -> None:
        self.failures += 1
        if holding != self.base and amount > 0:
            result.unwound = self._unwind(result, holding, amount)
        else:
            result.end_amount = amount

        if self.failures >= self.max_failures:
            self.halted = True
            self.halt_reason = f"{self.failures} неудачных цикла подряд"
            log.error("АРБИТРАЖ ОСТАНОВЛЕН: %s", self.halt_reason)
        self.history.append(result)

    def _unwind(self, result: ExecutionResult, currency: str, amount: Decimal) -> bool:
        """Вернуть зависший остаток в базовую валюту напрямую."""
        symbol = self._direct_symbol(currency)
        if symbol is None:
            result.stuck_currency = currency
            result.stuck_amount = amount
            log.error(
                "Откат невозможен: нет прямой пары %s/%s. Осталось %s %s — закройте вручную.",
                currency, self.base, amount, currency,
            )
            return False

        spec = self.specs[symbol]
        leg = Leg(symbol, Side.SELL if spec.base == currency else Side.BUY, currency, self.base)
        log.warning("Откат: возвращаем %s %s в %s через %s", amount, currency, self.base, symbol)
        try:
            unwind_result = self._trade(leg, amount)
        except ExchangeError as exc:
            result.stuck_currency = currency
            result.stuck_amount = amount
            log.error("Откат не удался (%s). Осталось %s %s — закройте вручную.",
                      exc, amount, currency)
            return False

        if not unwind_result.ok:
            result.stuck_currency = currency
            result.stuck_amount = amount
            log.error("Откат не исполнился: %s. Осталось %s %s — закройте вручную.",
                      unwind_result.error, amount, currency)
            return False

        result.end_amount = unwind_result.received
        result.legs.append(unwind_result)
        log.warning("Откат выполнен: вернули %s %s", unwind_result.received, self.base)
        return True

    def _direct_symbol(self, currency: str) -> Optional[str]:
        for candidate in (f"{currency}_{self.base}", f"{self.base}_{currency}"):
            if candidate in self.specs:
                return candidate
        return None

    def resume(self) -> None:
        self.halted = False
        self.halt_reason = ""
        self.failures = 0
