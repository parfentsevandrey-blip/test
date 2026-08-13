"""Базовые типы данных бота.

Все денежные величины — `Decimal`. Float для цен и объёмов не используем нигде,
кроме индикаторов, где важна только скорость и относительная точность.
"""

from __future__ import annotations

import time
from dataclasses import dataclass, field
from decimal import Decimal
from enum import Enum
from typing import Optional


class Side(str, Enum):
    BUY = "buy"
    SELL = "sell"

    @property
    def opposite(self) -> "Side":
        return Side.SELL if self is Side.BUY else Side.BUY

    @property
    def sign(self) -> Decimal:
        """+1 для покупки, -1 для продажи — удобно при пересчёте позиции."""
        return Decimal(1) if self is Side.BUY else Decimal(-1)


class OrderType(str, Enum):
    LIMIT = "limit"
    MARKET = "market"


class OrderStatus(str, Enum):
    OPEN = "open"
    FILLED = "filled"
    CANCELLED = "cancelled"


@dataclass(frozen=True)
class Candle:
    """Одна свеча. `ts` — время открытия свечи, unix-секунды UTC."""

    ts: int
    open: Decimal
    high: Decimal
    low: Decimal
    close: Decimal
    volume: Decimal

    @property
    def typical(self) -> Decimal:
        return (self.high + self.low + self.close) / 3


@dataclass(frozen=True)
class PairSpec:
    """Торговые ограничения пары, взятые из /spot/currency_pairs.

    Без них биржа отклонит ордер с `INVALID_PRECISION` или `TOO_SMALL`, поэтому
    любой ордер перед отправкой прогоняется через `quantize_*`.
    """

    symbol: str
    base: str
    quote: str
    amount_precision: int
    price_precision: int
    min_base_amount: Decimal = Decimal(0)
    min_quote_amount: Decimal = Decimal(0)

    def quantize_price(self, price: Decimal) -> Decimal:
        return price.quantize(Decimal(1).scaleb(-self.price_precision))

    def quantize_amount(self, amount: Decimal) -> Decimal:
        """Округляем объём ВНИЗ: вверх — риск словить отказ по балансу."""
        step = Decimal(1).scaleb(-self.amount_precision)
        return (amount // step) * step

    def is_tradable(self, amount: Decimal, price: Decimal) -> bool:
        if amount <= 0:
            return False
        if self.min_base_amount and amount < self.min_base_amount:
            return False
        if self.min_quote_amount and amount * price < self.min_quote_amount:
            return False
        return True


@dataclass
class Order:
    """Ордер в терминах бота. `client_id` — стабильная логическая метка.

    Реконсилятор сравнивает желаемые и реальные ордера именно по `client_id`,
    поэтому стратегия обязана выдавать один и тот же id для одного и того же
    логического ордера («покупка на 3-м уровне сетки») от тика к тику.
    """

    client_id: str
    symbol: str
    side: Side
    type: OrderType
    amount: Decimal
    price: Optional[Decimal] = None
    order_id: str = ""
    status: OrderStatus = OrderStatus.OPEN
    filled: Decimal = Decimal(0)
    avg_price: Decimal = Decimal(0)
    fee: Decimal = Decimal(0)
    fee_currency: str = ""
    created_ts: int = field(default_factory=lambda: int(time.time()))

    @property
    def is_open(self) -> bool:
        return self.status is OrderStatus.OPEN


@dataclass
class Fill:
    """Факт исполнения. `fee` уже в валюте котировки (USDT)."""

    ts: int
    symbol: str
    side: Side
    amount: Decimal
    price: Decimal
    fee: Decimal
    client_id: str = ""

    @property
    def quote_value(self) -> Decimal:
        return self.amount * self.price


@dataclass
class Position:
    """Спотовая позиция: сколько базовой валюты набрано и по какой средней.

    На споте «шорта» нет, поэтому `amount` всегда >= 0.
    """

    symbol: str
    amount: Decimal = Decimal(0)
    avg_entry: Decimal = Decimal(0)
    realized_pnl: Decimal = Decimal(0)
    fees_paid: Decimal = Decimal(0)
    opened_ts: int = 0

    @property
    def is_open(self) -> bool:
        return self.amount > 0

    def unrealized_pnl(self, price: Decimal) -> Decimal:
        if not self.is_open:
            return Decimal(0)
        return (price - self.avg_entry) * self.amount

    def apply(self, fill: Fill) -> None:
        """Пересчитать позицию по исполнению. Средняя цена — по покупкам."""
        self.fees_paid += fill.fee
        if fill.side is Side.BUY:
            total = self.avg_entry * self.amount + fill.price * fill.amount
            self.amount += fill.amount
            self.avg_entry = total / self.amount if self.amount > 0 else Decimal(0)
            if self.opened_ts == 0:
                self.opened_ts = fill.ts
        else:
            sold = min(fill.amount, self.amount)
            self.realized_pnl += (fill.price - self.avg_entry) * sold
            self.amount -= sold
            if self.amount <= 0:
                self.amount = Decimal(0)
                self.avg_entry = Decimal(0)
                self.opened_ts = 0
        # Комиссия — всегда расход, независимо от направления.
        self.realized_pnl -= fill.fee


@dataclass(frozen=True)
class BookLevel:
    price: Decimal
    amount: Decimal


@dataclass
class OrderBook:
    """Стакан. `asks` по возрастанию цены, `bids` по убыванию.

    Методы «прохода» считают реальную среднюю цену исполнения по объёму: при
    расчёте арбитража нельзя брать лучшую цену как данность — на ней стоит
    ограниченный объём, и заявка крупнее съедает стакан вглубь.
    """

    symbol: str
    asks: list[BookLevel] = field(default_factory=list)
    bids: list[BookLevel] = field(default_factory=list)
    ts: int = 0

    @property
    def best_ask(self) -> Optional[Decimal]:
        return self.asks[0].price if self.asks else None

    @property
    def best_bid(self) -> Optional[Decimal]:
        return self.bids[0].price if self.bids else None

    def buy_with(self, quote_amount: Decimal) -> tuple[Decimal, Decimal]:
        """Потратить `quote_amount` котировочной валюты, покупая по asks.

        Возвращает (сколько базовой купили, сколько котировочной реально ушло).
        Если стакана не хватило, второе число меньше запрошенного.
        """
        left = quote_amount
        got = Decimal(0)
        for level in self.asks:
            cost = level.price * level.amount
            if cost >= left:
                got += left / level.price
                return got, quote_amount
            got += level.amount
            left -= cost
        return got, quote_amount - left

    def sell_amount(self, base_amount: Decimal) -> tuple[Decimal, Decimal]:
        """Продать `base_amount` базовой валюты по bids.

        Возвращает (сколько котировочной получили, сколько базовой реально ушло).
        """
        left = base_amount
        got = Decimal(0)
        for level in self.bids:
            take = level.amount if level.amount < left else left
            got += take * level.price
            left -= take
            if left <= 0:
                return got, base_amount
        return got, base_amount - left


@dataclass(frozen=True)
class Ticker:
    """Снимок верха стакана из /spot/tickers — кешированный, для отбора кандидатов."""

    symbol: str
    last: Decimal
    bid: Decimal
    ask: Decimal
    quote_volume: Decimal = Decimal(0)


@dataclass
class Balance:
    currency: str
    available: Decimal
    locked: Decimal = Decimal(0)

    @property
    def total(self) -> Decimal:
        return self.available + self.locked


@dataclass
class DesiredOrder:
    """Чего хочет стратегия. Движок сам решит, что доставить, а что отменить."""

    client_id: str
    side: Side
    amount: Decimal
    price: Optional[Decimal] = None  # None => рыночный ордер

    @property
    def type(self) -> OrderType:
        return OrderType.MARKET if self.price is None else OrderType.LIMIT


@dataclass
class Decision:
    """Результат работы стратегии за один тик.

    `desired` — полный желаемый набор лимитных ордеров (не дельта!). Всё, чего
    в нём нет, движок снимет. `flatten` перекрывает всё: закрыть позицию по рынку.
    """

    desired: list[DesiredOrder] = field(default_factory=list)
    flatten: bool = False
    note: str = ""
