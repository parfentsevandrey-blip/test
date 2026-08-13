"""gatebot — торговый бот для спотового рынка Gate.io."""

from .config import Config, load_config
from .engine import TradingEngine
from .risk import RiskLimits, RiskManager
from .strategies import build_strategy

__version__ = "0.1.0"

__all__ = [
    "Config",
    "load_config",
    "TradingEngine",
    "RiskLimits",
    "RiskManager",
    "build_strategy",
    "__version__",
]
