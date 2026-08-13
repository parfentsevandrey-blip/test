from __future__ import annotations

import os
from decimal import Decimal

import pytest
import yaml

from gatebot.config import Config, from_dict, interval_seconds, load_config, load_env
from gatebot.risk import RiskState
from gatebot.state import StateStore
from gatebot.types import Position


# ------------------------------------------------------------------- конфиг


def test_defaults_are_paper_mode():
    cfg = Config()
    assert cfg.mode == "paper"
    assert not cfg.is_live


def test_from_dict_parses_sections():
    cfg = from_dict(
        {
            "symbol": "eth_usdt",
            "interval": "15m",
            "paper": {"initial_quote": 500},
            "strategy": {"name": "grid", "params": {"levels": 8}},
            "risk": {"stop_loss_pct": 0.02, "max_open_orders": 6},
            "exchange": {"maker_fee": 0.001},
        }
    )
    assert cfg.symbol == "ETH_USDT"  # приводится к верхнему регистру
    assert cfg.initial_quote == Decimal(500)
    assert cfg.strategy.params["levels"] == 8
    assert cfg.risk.stop_loss_pct == Decimal("0.02")
    assert cfg.risk.max_open_orders == 6
    assert cfg.exchange.maker_fee == Decimal("0.001")


def test_invalid_symbol_rejected():
    with pytest.raises(ValueError, match="BASE_QUOTE"):
        from_dict({"symbol": "BTCUSDT"})


def test_invalid_interval_rejected():
    with pytest.raises(ValueError, match="interval"):
        from_dict({"interval": "3h"})


def test_live_mode_requires_keys(monkeypatch):
    monkeypatch.delenv("GATEIO_API_KEY", raising=False)
    monkeypatch.delenv("GATEIO_API_SECRET", raising=False)
    with pytest.raises(ValueError, match="GATEIO_API_KEY"):
        from_dict({"mode": "live"})


def test_live_mode_accepted_with_keys(monkeypatch):
    monkeypatch.setenv("GATEIO_API_KEY", "k")
    monkeypatch.setenv("GATEIO_API_SECRET", "s")
    assert from_dict({"mode": "live"}).is_live


def test_example_config_is_valid(tmp_path):
    """Файл из репозитория обязан грузиться без правок."""
    import pathlib

    example = pathlib.Path(__file__).resolve().parents[1] / "config.example.yaml"
    raw = yaml.safe_load(example.read_text(encoding="utf-8"))
    cfg = from_dict(raw)
    assert cfg.strategy.name in ("grid", "ema_cross", "rsi_reversion")


def test_load_config_missing_file(tmp_path):
    with pytest.raises(FileNotFoundError):
        load_config(tmp_path / "нет-такого.yaml")


def test_interval_seconds():
    assert interval_seconds("1m") == 60
    assert interval_seconds("4h") == 14400
    assert interval_seconds("1d") == 86400
    with pytest.raises(ValueError):
        interval_seconds("2h")


def test_load_env_does_not_override_existing(tmp_path, monkeypatch):
    env = tmp_path / ".env"
    env.write_text('GATEIO_API_KEY="from-file"\nOTHER=value\n', encoding="utf-8")
    monkeypatch.setenv("GATEIO_API_KEY", "from-shell")
    monkeypatch.delenv("OTHER", raising=False)
    load_env(env)
    assert os.environ["GATEIO_API_KEY"] == "from-shell"
    assert os.environ["OTHER"] == "value"


def test_load_env_ignores_comments(tmp_path, monkeypatch):
    env = tmp_path / ".env"
    env.write_text("# комментарий\n\nA=1\nмусор без равно\n", encoding="utf-8")
    monkeypatch.delenv("A", raising=False)
    load_env(env)
    assert os.environ["A"] == "1"


# ----------------------------------------------------------------- состояние


def test_state_roundtrip(tmp_path):
    store = StateStore(tmp_path / "state.json")
    position = Position("BTC_USDT", amount=Decimal("0.5"), avg_entry=Decimal("61000"))
    position.realized_pnl = Decimal("12.34")
    risk = RiskState(peak_equity=Decimal(1200), halted=True, halt_reason="просадка")

    store.save("BTC_USDT", position, risk, 1_700_000_000)
    loaded_pos, loaded_risk, ts = store.load("BTC_USDT")

    assert loaded_pos.amount == Decimal("0.5")
    assert loaded_pos.avg_entry == Decimal("61000")
    assert loaded_pos.realized_pnl == Decimal("12.34")
    assert loaded_risk.halted and loaded_risk.halt_reason == "просадка"
    assert ts == 1_700_000_000


def test_state_missing_file_returns_defaults(tmp_path):
    position, risk, ts = StateStore(tmp_path / "нет.json").load("BTC_USDT")
    assert position.amount == 0 and not risk.halted and ts == 0


def test_state_for_other_symbol_is_ignored(tmp_path):
    """Состояние по BTC не должно применяться к ETH — иначе стоп встанет не там."""
    store = StateStore(tmp_path / "state.json")
    store.save("BTC_USDT", Position("BTC_USDT", amount=Decimal(1)), RiskState(), 1)
    position, _, ts = store.load("ETH_USDT")
    assert position.amount == 0 and ts == 0


def test_corrupted_state_does_not_crash(tmp_path):
    path = tmp_path / "state.json"
    path.write_text("{битый json", encoding="utf-8")
    position, risk, ts = StateStore(path).load("BTC_USDT")
    assert position.amount == 0 and not risk.halted


def test_state_write_is_atomic(tmp_path):
    """После записи временных файлов остаться не должно."""
    store = StateStore(tmp_path / "sub" / "state.json")
    store.save("BTC_USDT", Position("BTC_USDT"), RiskState(), 0)
    assert store.path.is_file()
    assert list(store.path.parent.glob("*.tmp")) == []
