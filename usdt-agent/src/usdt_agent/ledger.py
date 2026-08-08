"""Tamper-evident SQLite ledger.

Every state change the agent makes is appended to a **hash-chained journal**:
each row stores the SHA-256 of ``prev_hash || canonical_json(payload)``. If any
historical row is edited or removed, ``verify()`` reports the exact index where
the chain breaks. This matters more than it sounds: the single most common way
an "it makes money" bot lies to you is by quietly rewriting its own history.

The relational tables (``trades``, ``fills``, ``equity``) are *derived* views
for querying; the journal is the source of truth.
"""

from __future__ import annotations

import hashlib
import json
import sqlite3
import time
from collections.abc import Iterator
from contextlib import contextmanager
from dataclasses import asdict
from pathlib import Path
from typing import Any

from .models import Fill, Trade, TradeStatus

GENESIS = "0" * 64

SCHEMA = """
CREATE TABLE IF NOT EXISTS journal (
    seq        INTEGER PRIMARY KEY AUTOINCREMENT,
    ts         REAL NOT NULL,
    kind       TEXT NOT NULL,
    payload    TEXT NOT NULL,
    prev_hash  TEXT NOT NULL,
    hash       TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS trades (
    id            TEXT PRIMARY KEY,
    strategy      TEXT NOT NULL,
    label         TEXT NOT NULL,
    notional      REAL NOT NULL,
    opened_ts     REAL NOT NULL,
    closed_ts     REAL,
    status        TEXT NOT NULL,
    entry_cost    REAL NOT NULL DEFAULT 0,
    exit_cost     REAL NOT NULL DEFAULT 0,
    accrued       REAL NOT NULL DEFAULT 0,
    realized_pnl  REAL NOT NULL DEFAULT 0,
    return_bps    REAL NOT NULL DEFAULT 0,
    expected_edge_bps REAL NOT NULL DEFAULT 0,
    meta          TEXT NOT NULL DEFAULT '{}'
);
CREATE INDEX IF NOT EXISTS idx_trades_strategy ON trades(strategy, closed_ts);
CREATE TABLE IF NOT EXISTS fills (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    trade_id  TEXT NOT NULL,
    ts        REAL NOT NULL,
    venue     TEXT NOT NULL,
    symbol    TEXT NOT NULL,
    side      TEXT NOT NULL,
    price     REAL NOT NULL,
    qty       REAL NOT NULL,
    fee       REAL NOT NULL,
    slippage  REAL NOT NULL,
    ok        INTEGER NOT NULL DEFAULT 1,
    reason    TEXT NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS idx_fills_trade ON fills(trade_id);
CREATE TABLE IF NOT EXISTS equity (
    ts        REAL PRIMARY KEY,
    equity    REAL NOT NULL,
    deployed  REAL NOT NULL,
    treasury  REAL NOT NULL DEFAULT 0,
    open_trades INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS state (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
"""


def canonical(payload: Any) -> str:
    return json.dumps(payload, sort_keys=True, separators=(",", ":"), default=str)


def chain_hash(prev_hash: str, kind: str, ts: float, payload: Any) -> str:
    blob = f"{prev_hash}|{kind}|{ts:.6f}|{canonical(payload)}".encode()
    return hashlib.sha256(blob).hexdigest()


class Ledger:
    """Append-only store for trades, fills, equity and arbitrary events."""

    def __init__(self, path: str | Path = "data/agent.db") -> None:
        self.path = Path(path)
        if str(self.path) != ":memory:":
            self.path.parent.mkdir(parents=True, exist_ok=True)
        self.conn = sqlite3.connect(str(self.path), check_same_thread=False)
        self.conn.row_factory = sqlite3.Row
        self.conn.execute("PRAGMA journal_mode=WAL")
        self.conn.execute("PRAGMA synchronous=NORMAL")
        self.conn.executescript(SCHEMA)
        self.conn.commit()

    # -- lifecycle -------------------------------------------------------
    def close(self) -> None:
        self.conn.commit()
        self.conn.close()

    def __enter__(self) -> Ledger:
        return self

    def __exit__(self, *exc: Any) -> None:
        self.close()

    @contextmanager
    def tx(self) -> Iterator[sqlite3.Connection]:
        try:
            yield self.conn
            self.conn.commit()
        except Exception:
            self.conn.rollback()
            raise

    # -- journal ---------------------------------------------------------
    def head_hash(self) -> str:
        row = self.conn.execute("SELECT hash FROM journal ORDER BY seq DESC LIMIT 1").fetchone()
        return row["hash"] if row else GENESIS

    def append(self, kind: str, payload: Any, ts: float | None = None) -> str:
        ts = time.time() if ts is None else ts
        prev = self.head_hash()
        h = chain_hash(prev, kind, ts, payload)
        with self.tx() as c:
            c.execute(
                "INSERT INTO journal (ts, kind, payload, prev_hash, hash) VALUES (?,?,?,?,?)",
                (ts, kind, canonical(payload), prev, h),
            )
        return h

    def verify(self) -> tuple[bool, str]:
        """Walk the chain. Returns ``(ok, message)``."""
        prev = GENESIS
        n = 0
        for row in self.conn.execute("SELECT * FROM journal ORDER BY seq ASC"):
            expect = chain_hash(prev, row["kind"], row["ts"], json.loads(row["payload"]))
            if row["prev_hash"] != prev:
                return False, f"journal seq={row['seq']}: prev_hash mismatch (chain cut)"
            if row["hash"] != expect:
                return False, f"journal seq={row['seq']}: payload was modified"
            prev = row["hash"]
            n += 1
        return True, f"journal intact: {n} entries, head={prev[:12]}"

    def events(self, kind: str | None = None, limit: int = 100) -> list[dict[str, Any]]:
        sql = "SELECT * FROM journal"
        args: tuple[Any, ...] = ()
        if kind:
            sql += " WHERE kind = ?"
            args = (kind,)
        sql += " ORDER BY seq DESC LIMIT ?"
        rows = self.conn.execute(sql, (*args, limit)).fetchall()
        return [{**dict(r), "payload": json.loads(r["payload"])} for r in rows]

    # -- trades ----------------------------------------------------------
    def record_trade(self, trade: Trade) -> None:
        meta = canonical(trade.meta)
        with self.tx() as c:
            c.execute(
                """INSERT INTO trades (id, strategy, label, notional, opened_ts, closed_ts, status,
                                       entry_cost, exit_cost, accrued, realized_pnl, return_bps,
                                       expected_edge_bps, meta)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                   ON CONFLICT(id) DO UPDATE SET
                       closed_ts=excluded.closed_ts, status=excluded.status,
                       entry_cost=excluded.entry_cost, exit_cost=excluded.exit_cost,
                       accrued=excluded.accrued, realized_pnl=excluded.realized_pnl,
                       return_bps=excluded.return_bps, meta=excluded.meta""",
                (
                    trade.id, trade.strategy, trade.label, trade.notional, trade.opened_ts,
                    trade.closed_ts, trade.status.value, trade.entry_cost, trade.exit_cost,
                    trade.accrued, trade.realized_pnl, trade.return_bps,
                    trade.expected_edge_bps, meta,
                ),
            )
        self.append(
            "trade_open" if trade.is_open else "trade_close",
            {
                "id": trade.id, "strategy": trade.strategy, "label": trade.label,
                "notional": round(trade.notional, 6), "pnl": round(trade.realized_pnl, 6),
                "return_bps": round(trade.return_bps, 4), "status": trade.status.value,
            },
        )

    def record_fills(self, trade_id: str, fills: list[Fill]) -> None:
        if not fills:
            return
        with self.tx() as c:
            c.executemany(
                """INSERT INTO fills (trade_id, ts, venue, symbol, side, price, qty, fee, slippage, ok, reason)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
                [
                    (
                        trade_id, f.ts, f.order.venue, f.order.symbol, f.order.side.value,
                        f.price, f.qty, f.fee_usdt, f.slippage_usdt, int(f.ok), f.reason,
                    )
                    for f in fills
                ],
            )

    def open_trades(self) -> list[dict[str, Any]]:
        rows = self.conn.execute(
            "SELECT * FROM trades WHERE status = ? ORDER BY opened_ts", (TradeStatus.OPEN.value,)
        ).fetchall()
        return [dict(r) for r in rows]

    def closed_returns(self, strategy: str | None = None, limit: int = 500) -> list[float]:
        """Realized per-trade returns in bps, oldest first (bandit + stats input)."""
        sql = "SELECT return_bps FROM trades WHERE status = 'closed'"
        args: tuple[Any, ...] = ()
        if strategy:
            sql += " AND strategy = ?"
            args = (strategy,)
        sql += " ORDER BY closed_ts DESC LIMIT ?"
        rows = self.conn.execute(sql, (*args, limit)).fetchall()
        return [float(r["return_bps"]) for r in reversed(rows)]

    def strategy_stats(self) -> dict[str, dict[str, float]]:
        rows = self.conn.execute(
            """SELECT strategy,
                      COUNT(*)              AS n,
                      SUM(realized_pnl)     AS pnl,
                      AVG(return_bps)       AS avg_bps,
                      SUM(notional)         AS turnover,
                      SUM(CASE WHEN realized_pnl > 0 THEN 1 ELSE 0 END) AS wins
               FROM trades WHERE status = 'closed' GROUP BY strategy"""
        ).fetchall()
        out: dict[str, dict[str, float]] = {}
        for r in rows:
            n = int(r["n"]) or 1
            out[r["strategy"]] = {
                "n": float(r["n"]),
                "pnl": float(r["pnl"] or 0.0),
                "avg_bps": float(r["avg_bps"] or 0.0),
                "turnover": float(r["turnover"] or 0.0),
                "win_rate": float(r["wins"] or 0) / n,
            }
        return out

    def totals(self) -> dict[str, float]:
        r = self.conn.execute(
            """SELECT COUNT(*) AS n, COALESCE(SUM(realized_pnl),0) AS pnl,
                      COALESCE(SUM(entry_cost+exit_cost),0) AS costs,
                      COALESCE(SUM(accrued),0) AS accrued
               FROM trades WHERE status='closed'"""
        ).fetchone()
        return {
            "closed_trades": float(r["n"]),
            "realized_pnl": float(r["pnl"]),
            "costs": float(r["costs"]),
            "gross": float(r["accrued"]),
        }

    # -- equity ----------------------------------------------------------
    def record_equity(self, ts: float, equity: float, deployed: float, treasury: float, open_trades: int) -> None:
        with self.tx() as c:
            c.execute(
                "INSERT OR REPLACE INTO equity (ts, equity, deployed, treasury, open_trades) VALUES (?,?,?,?,?)",
                (ts, equity, deployed, treasury, open_trades),
            )

    def equity_curve(self, limit: int = 2000) -> list[tuple[float, float]]:
        rows = self.conn.execute(
            "SELECT ts, equity FROM equity ORDER BY ts DESC LIMIT ?", (limit,)
        ).fetchall()
        return [(float(r["ts"]), float(r["equity"])) for r in reversed(rows)]

    # -- key/value state -------------------------------------------------
    def set_state(self, key: str, value: Any) -> None:
        with self.tx() as c:
            c.execute(
                "INSERT OR REPLACE INTO state (key, value) VALUES (?, ?)", (key, canonical(value))
            )

    def get_state(self, key: str, default: Any = None) -> Any:
        row = self.conn.execute("SELECT value FROM state WHERE key = ?", (key,)).fetchone()
        return json.loads(row["value"]) if row else default


def trade_to_dict(t: Trade) -> dict[str, Any]:
    d = asdict(t)
    d["status"] = t.status.value
    d["realized_pnl"] = t.realized_pnl
    d["return_bps"] = t.return_bps
    d.pop("fills", None)
    return d
