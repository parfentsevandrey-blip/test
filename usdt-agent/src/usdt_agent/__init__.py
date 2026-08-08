"""usdt-agent — an autonomous, market-neutral USDT treasury agent.

Five delta-neutral strategies, a Thompson-sampling capital allocator, a risk
governor with veto power, a tamper-evident ledger, and a statistical gate that
withholds live capital from any strategy that has not *proven* an edge.

Paper trading is the default and requires nothing but Python 3.11.
"""

from __future__ import annotations

__version__ = "1.0.0"
__all__ = ["Agent", "AgentConfig", "Ledger", "__version__", "load_config", "main"]


def __getattr__(name: str):  # pragma: no cover - lazy re-exports keep import cheap
    if name == "Agent":
        from .agent import Agent

        return Agent
    if name in ("AgentConfig", "load_config"):
        from . import config

        return getattr(config, name)
    if name == "Ledger":
        from .ledger import Ledger

        return Ledger
    if name == "main":
        from .cli import main

        return main
    raise AttributeError(f"module {__name__!r} has no attribute {name!r}")
