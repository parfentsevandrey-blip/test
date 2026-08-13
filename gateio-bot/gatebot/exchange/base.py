"""Интерфейс биржи. Движок работает только с ним и не знает, живая это торговля
или симуляция — благодаря этому `paper` и `live` гоняют один и тот же код."""

from __future__ import annotations

from abc import ABC, abstractmethod
from decimal import Decimal

from ..types import Balance, Candle, Order, PairSpec


class ExchangeError(RuntimeError):
    """Ошибка биржи. `retryable=True` — есть смысл повторить запрос."""

    def __init__(self, message: str, *, label: str = "", retryable: bool = False):
        super().__init__(message)
        self.label = label
        self.retryable = retryable


class InsufficientBalance(ExchangeError):
    pass


class Exchange(ABC):
    """Минимальный набор операций, нужный боту для спота."""

    name: str = "exchange"

    @abstractmethod
    def get_pair_spec(self, symbol: str) -> PairSpec:
        """Точности и минимальные объёмы пары."""

    @abstractmethod
    def get_candles(self, symbol: str, interval: str, limit: int = 200) -> list[Candle]:
        """Свечи по возрастанию времени. Последняя может быть незакрытой."""

    @abstractmethod
    def get_price(self, symbol: str) -> Decimal:
        """Последняя цена сделки."""

    @abstractmethod
    def get_balances(self) -> dict[str, Balance]:
        """Балансы спот-аккаунта, ключ — код валюты."""

    @abstractmethod
    def get_open_orders(self, symbol: str) -> list[Order]:
        ...

    @abstractmethod
    def get_order(self, symbol: str, order_id: str) -> Order:
        ...

    @abstractmethod
    def place_order(self, order: Order) -> Order:
        """Разместить ордер. Возвращает его же с проставленным `order_id`."""

    @abstractmethod
    def cancel_order(self, symbol: str, order_id: str) -> None:
        ...

    def cancel_all(self, symbol: str) -> None:
        for order in self.get_open_orders(symbol):
            self.cancel_order(symbol, order.order_id)
