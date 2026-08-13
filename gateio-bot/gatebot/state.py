"""Сохранение состояния между запусками.

Без этого перезапуск бота обнулял бы среднюю цену входа (и, значит, стоп-лосс),
а также снимал бы аварийную остановку — ровно в тот момент, когда её снимать
нельзя. Пишем через временный файл и `os.replace`, чтобы падение посреди записи
не оставило битый JSON.
"""

from __future__ import annotations

import json
import logging
import os
from decimal import Decimal
from pathlib import Path
from typing import Any, Optional

from .risk import RiskState
from .types import Position

log = logging.getLogger(__name__)

SCHEMA_VERSION = 1


class StateStore:
    def __init__(self, path: str | Path):
        self.path = Path(path)

    def load(self, symbol: str) -> tuple[Position, RiskState, int]:
        """Вернуть (позиция, состояние риска, ts последней обработанной свечи)."""
        if not self.path.is_file():
            return Position(symbol=symbol), RiskState(), 0
        try:
            raw = json.loads(self.path.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError) as exc:
            log.error("Не удалось прочитать состояние %s: %s — стартуем с нуля", self.path, exc)
            return Position(symbol=symbol), RiskState(), 0

        if raw.get("version") != SCHEMA_VERSION:
            log.warning("Состояние другой версии (%s) — игнорируем", raw.get("version"))
            return Position(symbol=symbol), RiskState(), 0

        if raw.get("symbol") != symbol:
            log.warning(
                "Состояние относится к паре %s, а бот запущен на %s — стартуем с нуля",
                raw.get("symbol"),
                symbol,
            )
            return Position(symbol=symbol), RiskState(), 0

        position = _position_from(raw.get("position") or {}, symbol)
        risk = _risk_from(raw.get("risk") or {})
        return position, risk, int(raw.get("last_candle_ts") or 0)

    def save(
        self,
        symbol: str,
        position: Position,
        risk: RiskState,
        last_candle_ts: int,
        extra: Optional[dict[str, Any]] = None,
    ) -> None:
        payload = {
            "version": SCHEMA_VERSION,
            "symbol": symbol,
            "last_candle_ts": last_candle_ts,
            "position": {
                "amount": str(position.amount),
                "avg_entry": str(position.avg_entry),
                "realized_pnl": str(position.realized_pnl),
                "fees_paid": str(position.fees_paid),
                "opened_ts": position.opened_ts,
            },
            "risk": {
                "peak_equity": str(risk.peak_equity),
                "day_start_equity": str(risk.day_start_equity),
                "day_key": risk.day_key,
                "position_peak": str(risk.position_peak),
                "halted": risk.halted,
                "halt_reason": risk.halt_reason,
            },
            **(extra or {}),
        }
        self.path.parent.mkdir(parents=True, exist_ok=True)
        tmp = self.path.with_suffix(self.path.suffix + ".tmp")
        tmp.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
        os.replace(tmp, self.path)


def _position_from(raw: dict[str, Any], symbol: str) -> Position:
    return Position(
        symbol=symbol,
        amount=Decimal(str(raw.get("amount", 0))),
        avg_entry=Decimal(str(raw.get("avg_entry", 0))),
        realized_pnl=Decimal(str(raw.get("realized_pnl", 0))),
        fees_paid=Decimal(str(raw.get("fees_paid", 0))),
        opened_ts=int(raw.get("opened_ts", 0)),
    )


def _risk_from(raw: dict[str, Any]) -> RiskState:
    return RiskState(
        peak_equity=Decimal(str(raw.get("peak_equity", 0))),
        day_start_equity=Decimal(str(raw.get("day_start_equity", 0))),
        day_key=str(raw.get("day_key", "")),
        position_peak=Decimal(str(raw.get("position_peak", 0))),
        halted=bool(raw.get("halted", False)),
        halt_reason=str(raw.get("halt_reason", "")),
    )
