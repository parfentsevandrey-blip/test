"""Загрузка и валидация конфигурации.

Ключи API в конфиг-файл не кладутся никогда — только переменные окружения
`GATEIO_API_KEY` / `GATEIO_API_SECRET` (можно через файл `.env`, он в .gitignore).
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from decimal import Decimal
from pathlib import Path
from typing import Any

import yaml

from .risk import RiskLimits

VALID_INTERVALS = {
    "10s", "1m", "5m", "15m", "30m", "1h", "4h", "8h", "1d", "7d", "30d",
}

_UNIT_SECONDS = {"s": 1, "m": 60, "h": 3600, "d": 86400}


def interval_seconds(interval: str) -> int:
    """'4h' -> 14400. Нужно, чтобы отличить закрытую свечу от текущей."""
    if interval not in VALID_INTERVALS:
        raise ValueError(f"Неизвестный интервал: {interval}")
    return int(interval[:-1]) * _UNIT_SECONDS[interval[-1]]


@dataclass
class ExchangeConfig:
    maker_fee: Decimal = Decimal("0.002")
    taker_fee: Decimal = Decimal("0.002")
    slippage: Decimal = Decimal("0.0005")
    host: str = "https://api.gateio.ws"
    timeout: float = 10.0


@dataclass
class StrategyConfig:
    name: str = "ema_cross"
    params: dict[str, Any] = field(default_factory=dict)


@dataclass
class Config:
    symbol: str = "BTC_USDT"
    interval: str = "1h"
    mode: str = "paper"
    poll_seconds: int = 60
    initial_quote: Decimal = Decimal(1000)
    candles: int = 300
    strategy: StrategyConfig = field(default_factory=StrategyConfig)
    risk: RiskLimits = field(default_factory=RiskLimits)
    exchange: ExchangeConfig = field(default_factory=ExchangeConfig)
    log_level: str = "INFO"
    log_file: str = "logs/bot.log"
    state_file: str = "state/bot-state.json"

    @property
    def is_live(self) -> bool:
        return self.mode == "live"

    def credentials(self) -> tuple[str, str]:
        return os.getenv("GATEIO_API_KEY", ""), os.getenv("GATEIO_API_SECRET", "")

    def validate(self) -> None:
        errors: list[str] = []
        if self.mode not in ("paper", "live"):
            errors.append(f"mode должен быть paper или live, а не '{self.mode}'")
        if "_" not in self.symbol:
            errors.append(f"symbol должен быть в формате BASE_QUOTE, например BTC_USDT (получено '{self.symbol}')")
        if self.interval not in VALID_INTERVALS:
            errors.append(
                f"interval '{self.interval}' не поддерживается Gate.io; "
                f"допустимые: {', '.join(sorted(VALID_INTERVALS))}"
            )
        if self.poll_seconds < 1:
            errors.append("poll_seconds должен быть >= 1")
        if self.candles < 10:
            errors.append("candles должен быть >= 10")
        if self.is_live and not all(self.credentials()):
            errors.append(
                "для mode: live нужны переменные окружения GATEIO_API_KEY и GATEIO_API_SECRET"
            )
        if errors:
            raise ValueError("Ошибки конфигурации:\n  - " + "\n  - ".join(errors))


def load_env(path: str | Path = ".env") -> None:
    """Простейший .env-загрузчик — чтобы не тянуть python-dotenv в зависимости.

    Уже заданные переменные окружения имеют приоритет над файлом.
    """
    file = Path(path)
    if not file.is_file():
        return
    for line in file.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        key = key.strip()
        value = value.strip().strip("'\"")
        if key and key not in os.environ:
            os.environ[key] = value


def load_config(path: str | Path) -> Config:
    file = Path(path)
    if not file.is_file():
        raise FileNotFoundError(f"Конфиг не найден: {file}")
    raw = yaml.safe_load(file.read_text(encoding="utf-8")) or {}
    return from_dict(raw)


def from_dict(raw: dict[str, Any]) -> Config:
    strategy_raw = raw.get("strategy") or {}
    exchange_raw = raw.get("exchange") or {}
    logging_raw = raw.get("logging") or {}
    paper_raw = raw.get("paper") or {}

    cfg = Config(
        symbol=str(raw.get("symbol", "BTC_USDT")).upper(),
        interval=str(raw.get("interval", "1h")),
        mode=str(raw.get("mode", "paper")).lower(),
        poll_seconds=int(raw.get("poll_seconds", 60)),
        candles=int(raw.get("candles", 300)),
        initial_quote=Decimal(str(paper_raw.get("initial_quote", 1000))),
        strategy=StrategyConfig(
            name=str(strategy_raw.get("name", "ema_cross")),
            params=dict(strategy_raw.get("params") or {}),
        ),
        risk=RiskLimits.from_dict(raw.get("risk") or {}),
        exchange=ExchangeConfig(
            maker_fee=Decimal(str(exchange_raw.get("maker_fee", "0.002"))),
            taker_fee=Decimal(str(exchange_raw.get("taker_fee", "0.002"))),
            slippage=Decimal(str(exchange_raw.get("slippage", "0.0005"))),
            host=str(exchange_raw.get("host", "https://api.gateio.ws")),
            timeout=float(exchange_raw.get("timeout", 10.0)),
        ),
        log_level=str(logging_raw.get("level", "INFO")).upper(),
        log_file=str(logging_raw.get("file", "logs/bot.log")),
        state_file=str(raw.get("state_file", "state/bot-state.json")),
    )
    cfg.validate()
    return cfg
