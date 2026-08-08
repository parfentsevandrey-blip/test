"""Broker interface shared by the paper and live implementations."""

from __future__ import annotations

import abc

from ..models import Fill, MarketSnapshot, Order


class Broker(abc.ABC):
    """Turns :class:`Order` objects into :class:`Fill` objects."""

    name: str = "broker"
    is_live: bool = False

    @abc.abstractmethod
    def execute(self, orders: tuple[Order, ...], snapshot: MarketSnapshot) -> list[Fill]:
        """Execute a batch of orders. Always returns one Fill per order.

        A rejected order still yields a Fill with ``ok=False`` and a reason, so
        callers never have to align two lists of different lengths.
        """

    def cost_of(self, fills: list[Fill]) -> float:
        return sum(f.cost_usdt for f in fills if f.ok)

    def all_ok(self, fills: list[Fill]) -> bool:
        return bool(fills) and all(f.ok for f in fills)


class BrokerError(RuntimeError):
    pass
