"""Typed configuration loaded from TOML (stdlib ``tomllib``) + environment.

Precedence: defaults < TOML file < ``USDT_AGENT_*`` environment variables <
explicit CLI flags. Secrets are *only* ever read from the environment — the
TOML file is meant to be committed, so it must never hold a key.
"""

from __future__ import annotations

import os
import tomllib
from dataclasses import dataclass, field, fields, is_dataclass
from pathlib import Path
from typing import Any


class ConfigError(ValueError):
    pass


@dataclass(slots=True)
class RiskConfig:
    """Hard limits. The governor refuses to trade outside these, always."""

    max_deployed_fraction: float = 0.60  # of equity, across all strategies
    max_strategy_fraction: float = 0.25  # of equity, per strategy
    max_venue_fraction: float = 0.35  # of equity, per venue
    max_open_trades: int = 12
    min_ticket_usdt: float = 25.0
    max_ticket_usdt: float = 2_500.0
    daily_loss_limit_fraction: float = 0.02  # halt for the day past this
    max_drawdown_fraction: float = 0.08  # hard kill-switch from equity high-water
    max_data_age_s: float = 90.0  # stale data => no new trades
    min_edge_bps: float = 1.5  # ignore ideas thinner than this after costs
    cooldown_after_loss_s: float = 300.0  # per-strategy penalty box


@dataclass(slots=True)
class AllocatorConfig:
    """Thompson-sampling bandit over strategies."""

    prior_mean_bps: float = 0.0
    prior_stdev_bps: float = 8.0
    observation_stdev_bps: float = 12.0
    memory_halflife_trades: float = 40.0  # older results decay away
    exploration_floor: float = 0.05  # min share kept for exploration
    min_trades_for_promotion: int = 30
    promotion_p_value: float = 0.05


@dataclass(slots=True)
class ExecutionConfig:
    taker_fee_bps: float = 5.0  # 0.05 % — plain Binance/Bybit spot taker
    maker_fee_bps: float = 1.0
    slippage_model: str = "depth"  # "depth" | "fixed" | "none"
    fixed_slippage_bps: float = 2.0
    impact_coefficient: float = 0.35  # slippage grows with notional/depth
    latency_ms: float = 250.0
    reject_probability: float = 0.01  # paper broker realism
    adverse_selection_bps: float = 0.4  # the tax reality charges on paper edges


@dataclass(slots=True)
class StrategyConfig:
    enabled: bool = True
    weight: float = 1.0
    params: dict[str, Any] = field(default_factory=dict)


@dataclass(slots=True)
class EarnConfig:
    """The earning half: acquiring USDT rather than deploying it.

    ``wallet`` holds *receiving addresses only*. There is deliberately no field
    anywhere in this project for a private key or a seed phrase.
    """

    enabled_channels: tuple[str, ...] = ("bounties", "services", "affiliate", "passive")
    max_open_orders: int = 5
    min_rate_usdt_per_hour: float = 10.0
    lookback_blocks: int = 2_000
    require_approval: bool = True   # never claim work on your behalf unattended
    serve_host: str = "127.0.0.1"
    serve_port: int = 8402
    wallet: dict[str, str] = field(default_factory=dict)
    channels: dict[str, Any] = field(default_factory=dict)

    def channel_params(self) -> dict[str, dict]:
        out: dict[str, dict] = {}
        for name, block in (self.channels or {}).items():
            out[name] = dict((block or {}).get("params", {}) or {})
        return out

    def enabled(self) -> tuple[str, ...]:
        explicit = {
            name for name, block in (self.channels or {}).items()
            if (block or {}).get("enabled") is False
        }
        return tuple(c for c in self.enabled_channels if c not in explicit)


@dataclass(slots=True)
class AgentConfig:
    mode: str = "paper"  # "paper" | "live"
    starting_equity_usdt: float = 1_000.0
    interval_s: float = 30.0
    max_cycles: int = 0  # 0 == run forever
    data_source: str = "auto"  # "auto" | "live" | "synthetic"
    venues: tuple[str, ...] = ("binance", "bybit", "okx")
    symbols: tuple[str, ...] = (
        "BTC/USDT", "ETH/USDT", "SOL/USDT", "BNB/USDT", "XRP/USDT",
        "USDC/USDT",           # the grid strategy's pegged pair
        "ETH/BTC", "SOL/ETH", "BNB/BTC",  # crosses, so triangular loops exist
    )
    db_path: str = "data/agent.db"
    log_level: str = "INFO"
    seed: int = 7
    treasury_sweep_fraction: float = 0.25  # of profits, parked out of harm's way
    risk: RiskConfig = field(default_factory=RiskConfig)
    allocator: AllocatorConfig = field(default_factory=AllocatorConfig)
    execution: ExecutionConfig = field(default_factory=ExecutionConfig)
    earn: EarnConfig = field(default_factory=EarnConfig)
    strategies: dict[str, StrategyConfig] = field(default_factory=dict)
    notify_webhook: str = ""

    # -- derived helpers -------------------------------------------------
    @property
    def is_live(self) -> bool:
        return self.mode == "live"

    def strategy(self, name: str) -> StrategyConfig:
        return self.strategies.get(name, StrategyConfig())

    def enabled_strategies(self) -> list[str]:
        return [n for n, c in self.strategies.items() if c.enabled]

    def validate(self) -> None:
        r = self.risk
        if self.mode not in ("paper", "live"):
            raise ConfigError(f"mode must be 'paper' or 'live', got {self.mode!r}")
        if self.data_source not in ("auto", "live", "synthetic"):
            raise ConfigError(f"bad data_source {self.data_source!r}")
        if self.starting_equity_usdt <= 0:
            raise ConfigError("starting_equity_usdt must be positive")
        if not 0 < r.max_deployed_fraction <= 1:
            raise ConfigError("risk.max_deployed_fraction must be in (0, 1]")
        if r.max_strategy_fraction > r.max_deployed_fraction:
            raise ConfigError("risk.max_strategy_fraction cannot exceed max_deployed_fraction")
        if r.min_ticket_usdt > r.max_ticket_usdt:
            raise ConfigError("risk.min_ticket_usdt cannot exceed max_ticket_usdt")
        if r.max_drawdown_fraction <= 0 or r.max_drawdown_fraction >= 1:
            raise ConfigError("risk.max_drawdown_fraction must be in (0, 1)")
        if self.interval_s <= 0:
            raise ConfigError("interval_s must be positive")
        if not self.symbols:
            raise ConfigError("at least one symbol is required")
        if self.mode == "live" and not self.enabled_strategies():
            raise ConfigError("live mode with no enabled strategies")


# --------------------------------------------------------------------------
# Loading
# --------------------------------------------------------------------------

DEFAULT_STRATEGIES: dict[str, dict[str, Any]] = {
    "funding_carry": {"enabled": True, "weight": 1.0, "params": {"min_apr": 0.05}},
    "cross_venue": {"enabled": True, "weight": 1.0, "params": {"min_edge_bps": 3.0}},
    "triangular": {"enabled": True, "weight": 0.8, "params": {"min_edge_bps": 2.0}},
    "stable_yield": {"enabled": True, "weight": 1.0, "params": {"min_apy": 0.04, "max_risk": 0.5}},
    "grid": {"enabled": True, "weight": 0.6, "params": {"levels": 6, "band_bps": 25.0}},
}


def _coerce(target_type: Any, value: Any) -> Any:
    if target_type is tuple or getattr(target_type, "__origin__", None) is tuple:
        return tuple(value)
    if target_type is float:
        return float(value)
    if target_type is int:
        return int(value)
    if target_type is bool:
        if isinstance(value, str):
            return value.strip().lower() in ("1", "true", "yes", "on")
        return bool(value)
    return value


def _apply(obj: Any, data: dict[str, Any], path: str = "") -> None:
    """Recursively overlay a dict onto a dataclass instance, type-coercing."""
    known = {f.name: f for f in fields(obj)}
    for key, value in data.items():
        if key not in known:
            raise ConfigError(f"unknown config key {path + key!r}")
        f = known[key]
        current = getattr(obj, f.name)
        if is_dataclass(current) and isinstance(value, dict):
            _apply(current, value, f"{path}{key}.")
        else:
            setattr(obj, f.name, _coerce(f.type if not isinstance(f.type, str) else type(current), value))


def _env_overrides(cfg: AgentConfig) -> None:
    """``USDT_AGENT_RISK__MAX_OPEN_TRADES=4`` style overrides (``__`` nests)."""
    prefix = "USDT_AGENT_"
    for env_key, raw in os.environ.items():
        if not env_key.startswith(prefix) or env_key.endswith(("_KEY", "_SECRET", "_TOKEN")):
            continue
        parts = env_key[len(prefix) :].lower().split("__")
        target: Any = cfg
        for part in parts[:-1]:
            if not is_dataclass(target) or not hasattr(target, part):
                target = None
                break
            target = getattr(target, part)
        leaf = parts[-1]
        if target is None or not is_dataclass(target) or not hasattr(target, leaf):
            continue
        current = getattr(target, leaf)
        try:
            if isinstance(current, tuple):
                setattr(target, leaf, tuple(x.strip() for x in raw.split(",") if x.strip()))
            else:
                setattr(target, leaf, _coerce(type(current), raw))
        except (TypeError, ValueError) as e:
            raise ConfigError(f"bad value for {env_key}: {raw!r} ({e})") from e


def load_config(path: str | Path | None = None, **overrides: Any) -> AgentConfig:
    """Build an :class:`AgentConfig` from a TOML file, env vars and kwargs."""
    cfg = AgentConfig()
    cfg.strategies = {n: StrategyConfig(**v) for n, v in DEFAULT_STRATEGIES.items()}  # type: ignore[arg-type]

    if path:
        p = Path(path)
        if not p.exists():
            raise ConfigError(f"config file not found: {p}")
        with p.open("rb") as fh:
            data = tomllib.load(fh)
        strategies = data.pop("strategies", {}) or {}
        _apply(cfg, data)
        for name, sdata in strategies.items():
            base = cfg.strategies.setdefault(name, StrategyConfig())
            base.enabled = bool(sdata.get("enabled", base.enabled))
            base.weight = float(sdata.get("weight", base.weight))
            base.params = {**base.params, **(sdata.get("params", {}) or {})}

    _env_overrides(cfg)

    for key, value in overrides.items():
        if value is None:
            continue
        if not hasattr(cfg, key):
            raise ConfigError(f"unknown override {key!r}")
        setattr(cfg, key, _coerce(type(getattr(cfg, key)), value))

    cfg.validate()
    return cfg


def api_credentials(venue: str) -> tuple[str, str]:
    """Read live-trading keys for a venue from the environment only.

    e.g. ``BINANCE_API_KEY`` / ``BINANCE_API_SECRET``.
    """
    v = venue.upper()
    return os.environ.get(f"{v}_API_KEY", ""), os.environ.get(f"{v}_API_SECRET", "")
