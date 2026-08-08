"""Order execution: paper simulation and gated live trading."""

from .base import Broker, BrokerError
from .live import BinanceLiveBroker, LiveTradingBlocked, build_broker, live_interlocks
from .paper import PaperBroker

__all__ = [
    "BinanceLiveBroker", "Broker", "BrokerError", "LiveTradingBlocked",
    "PaperBroker", "build_broker", "live_interlocks",
]
