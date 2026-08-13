"""Бумажная торговля: реальные котировки, виртуальные деньги.

Котировки, свечи и параметры пар берутся с живой биржи через `market`, а ордера,
балансы и исполнения живут только в памяти. Это режим по умолчанию — чтобы
случайный запуск не потратил настоящий депозит.

Модель исполнения намеренно консервативна: лимитный ордер считается исполненным,
только когда цена **прошла** его уровень (строгое неравенство), и всегда по цене
ордера. Реальная биржа иногда даёт лучше, но переоценивать исполнение опаснее,
чем недооценивать.
"""

from __future__ import annotations

import itertools
import logging
from decimal import Decimal
from typing import Optional

from ..types import Balance, Candle, Fill, Order, OrderStatus, OrderType, PairSpec, Side
from .base import Exchange, InsufficientBalance

log = logging.getLogger(__name__)


class PaperExchange(Exchange):
    """Симулятор спота поверх любого источника рыночных данных."""

    name = "paper"

    def __init__(
        self,
        market: Exchange,
        *,
        quote_currency: str = "USDT",
        initial_quote: Decimal = Decimal(1000),
        maker_fee: Decimal = Decimal("0.002"),
        taker_fee: Decimal = Decimal("0.002"),
        slippage: Decimal = Decimal("0.0005"),
    ):
        self.market = market
        self.quote_currency = quote_currency
        self.maker_fee = maker_fee
        self.taker_fee = taker_fee
        self.slippage = slippage
        self._balances: dict[str, Balance] = {
            quote_currency: Balance(quote_currency, initial_quote)
        }
        self._orders: dict[str, Order] = {}
        self._ids = itertools.count(1)
        self.fills: list[Fill] = []
        self._last_price: dict[str, Decimal] = {}

    # ------------------------------------------------------ Рыночные данные

    def get_pair_spec(self, symbol: str) -> PairSpec:
        return self.market.get_pair_spec(symbol)

    def get_candles(self, symbol: str, interval: str, limit: int = 200) -> list[Candle]:
        candles = self.market.get_candles(symbol, interval, limit)
        if candles:
            self._match(symbol, candles[-1])
        return candles

    def get_price(self, symbol: str) -> Decimal:
        price = self.market.get_price(symbol)
        self._last_price[symbol] = price
        return price

    # -------------------------------------------------------------- Аккаунт

    def get_balances(self) -> dict[str, Balance]:
        return {k: Balance(v.currency, v.available, v.locked) for k, v in self._balances.items()}

    def get_open_orders(self, symbol: str) -> list[Order]:
        return [o for o in self._orders.values() if o.symbol == symbol and o.is_open]

    def get_order(self, symbol: str, order_id: str) -> Order:
        order = self._orders.get(order_id)
        if order is None:
            raise InsufficientBalance(f"Нет ордера {order_id}", label="ORDER_NOT_FOUND")
        return order

    def place_order(self, order: Order) -> Order:
        spec = self.get_pair_spec(order.symbol)
        order.order_id = f"paper-{next(self._ids)}"

        if order.type is OrderType.MARKET:
            price = self._reference_price(order.symbol)
            # Проскальзывание всегда против нас.
            fill_price = price * (
                1 + self.slippage if order.side is Side.BUY else 1 - self.slippage
            )
            fill_price = spec.quantize_price(fill_price)
            amount = order.amount
            if order.side is Side.BUY:
                # Как у Gate.io: рыночная покупка задаётся суммой в котировке.
                amount = spec.quantize_amount(order.amount / fill_price)
            self._reserve(order, spec, fill_price, amount)
            self._execute(order, amount, fill_price, self.taker_fee)
            return order

        if order.price is None:
            raise ValueError("Лимитный ордер без цены")
        self._reserve(order, spec, order.price, order.amount)
        self._orders[order.order_id] = order
        log.debug("paper: выставлен %s %s %s @ %s", order.client_id, order.side.value,
                  order.amount, order.price)
        return order

    def cancel_order(self, symbol: str, order_id: str) -> None:
        order = self._orders.get(order_id)
        if order is None or not order.is_open:
            return
        order.status = OrderStatus.CANCELLED
        self._release(order)

    # ------------------------------------------------------------ Исполнение

    def feed(self, symbol: str, candle: Candle) -> list[Fill]:
        """Прогнать свечу через книгу ордеров. Используется бэктестом."""
        before = len(self.fills)
        self._match(symbol, candle)
        return self.fills[before:]

    def _match(self, symbol: str, candle: Candle) -> None:
        self._last_price[symbol] = candle.close
        spec = self.get_pair_spec(symbol)
        for order in list(self._orders.values()):
            if order.symbol != symbol or not order.is_open or order.price is None:
                continue
            hit = (
                candle.low < order.price
                if order.side is Side.BUY
                else candle.high > order.price
            )
            if hit:
                self._execute(order, order.amount, order.price, self.maker_fee, ts=candle.ts)

    def _reference_price(self, symbol: str) -> Decimal:
        if symbol in self._last_price:
            return self._last_price[symbol]
        return self.get_price(symbol)

    def _reserve(self, order: Order, spec: PairSpec, price: Decimal, amount: Decimal) -> None:
        """Заблокировать средства под ордер — как это делает биржа."""
        if order.side is Side.BUY:
            need = amount * price
            bal = self._balance(spec.quote)
            if bal.available < need:
                raise InsufficientBalance(
                    f"Не хватает {spec.quote}: нужно {need}, есть {bal.available}",
                    label="BALANCE_NOT_ENOUGH",
                )
            bal.available -= need
            bal.locked += need
        else:
            bal = self._balance(spec.base)
            if bal.available < amount:
                raise InsufficientBalance(
                    f"Не хватает {spec.base}: нужно {amount}, есть {bal.available}",
                    label="BALANCE_NOT_ENOUGH",
                )
            bal.available -= amount
            bal.locked += amount

    def _release(self, order: Order) -> None:
        """Вернуть заблокированное под неисполненный остаток."""
        spec = self.get_pair_spec(order.symbol)
        left = order.amount - order.filled
        if left <= 0:
            return
        if order.side is Side.BUY and order.price is not None:
            bal = self._balance(spec.quote)
            amount = left * order.price
            bal.locked -= amount
            bal.available += amount
        else:
            bal = self._balance(spec.base)
            bal.locked -= left
            bal.available += left

    def _execute(
        self,
        order: Order,
        amount: Decimal,
        price: Decimal,
        fee_rate: Decimal,
        ts: Optional[int] = None,
    ) -> None:
        spec = self.get_pair_spec(order.symbol)
        quote_value = amount * price
        fee = quote_value * fee_rate

        base = self._balance(spec.base)
        quote = self._balance(spec.quote)
        if order.side is Side.BUY:
            quote.locked -= quote_value
            base.available += amount
        else:
            base.locked -= amount
            quote.available += quote_value - fee

        if order.side is Side.BUY:
            # Комиссию за покупку списываем из котировки: у Gate.io по умолчанию
            # она берётся с полученной базовой валюты, но так учёт PnL нагляднее.
            quote.available -= fee

        order.filled = amount
        order.avg_price = price
        order.fee = fee
        order.fee_currency = spec.quote
        order.status = OrderStatus.FILLED
        fill = Fill(
            ts=ts or 0,
            symbol=order.symbol,
            side=order.side,
            amount=amount,
            price=price,
            fee=fee,
            client_id=order.client_id,
        )
        self.fills.append(fill)
        log.debug("paper: исполнен %s %s %s @ %s", order.client_id, order.side.value, amount, price)

    def _balance(self, currency: str) -> Balance:
        if currency not in self._balances:
            self._balances[currency] = Balance(currency, Decimal(0))
        return self._balances[currency]

    def equity(self, symbol: str, price: Optional[Decimal] = None) -> Decimal:
        """Стоимость счёта в валюте котировки."""
        spec = self.get_pair_spec(symbol)
        price = price or self._reference_price(symbol)
        return self._balance(spec.quote).total + self._balance(spec.base).total * price
