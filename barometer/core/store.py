"""
Хранилище (SQLite).

Хранит:
  * items            — собранные новости/посты и извлечённые сигналы;
  * deepstate_snaps  — суточные снимки площади оккупации;
  * readings         — история показаний барометра (для графика и прогноза);
  * source_status    — статус каждого источника (ок/ошибка/сэмпл).

Соединение открывается на каждую операцию — это безопасно для нескольких
потоков (Flask + фоновый планировщик) при небольшом объёме данных.
"""

from __future__ import annotations

import json
import os
import sqlite3
import threading
from contextlib import contextmanager
from typing import Any, Iterable

import config  # корень проекта (barometer/) находится в sys.path — см. app.py

_WRITE_LOCK = threading.Lock()


@contextmanager
def _conn():
    os.makedirs(os.path.dirname(config.DB_PATH), exist_ok=True)
    con = sqlite3.connect(config.DB_PATH, timeout=30)
    con.row_factory = sqlite3.Row
    con.execute("PRAGMA journal_mode=WAL")
    try:
        yield con
        con.commit()
    finally:
        con.close()


def init_db() -> None:
    with _conn() as con:
        con.executescript(
            """
            CREATE TABLE IF NOT EXISTS items (
                id          TEXT PRIMARY KEY,
                source_id   TEXT,
                source_name TEXT,
                stream      TEXT,
                lang        TEXT,
                title       TEXT,
                summary     TEXT,
                url         TEXT,
                published   TEXT,
                fetched_at  TEXT,
                relevant    INTEGER DEFAULT 0,
                signals     TEXT,
                source_weight REAL DEFAULT 1.0
            );
            CREATE INDEX IF NOT EXISTS idx_items_pub ON items(published);
            CREATE INDEX IF NOT EXISTS idx_items_rel ON items(relevant);

            CREATE TABLE IF NOT EXISTS deepstate_snaps (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                taken_at      TEXT,
                occupied_km2  REAL,
                unknown_km2   REAL,
                occupied_polys INTEGER,
                delta_km2     REAL,
                status        TEXT
            );

            CREATE TABLE IF NOT EXISTS readings (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                taken_at      TEXT,
                barometer     REAL,
                llm_barometer REAL,
                final_barometer REAL,
                velocity      REAL,
                predicted_date TEXT,
                confidence    REAL,
                components    TEXT,
                drivers       TEXT,
                sources       TEXT
            );

            CREATE TABLE IF NOT EXISTS source_status (
                source_id  TEXT PRIMARY KEY,
                name       TEXT,
                stream     TEXT,
                mode       TEXT,
                last_ok    TEXT,
                last_error TEXT,
                items_count INTEGER DEFAULT 0
            );
            """
        )


# --------------------------------------------------------------------------- #
#  items                                                                       #
# --------------------------------------------------------------------------- #
def upsert_items(items: Iterable[dict]) -> int:
    """Вставляет новые items (по PK id). Возвращает число новых записей."""
    new = 0
    with _WRITE_LOCK, _conn() as con:
        for it in items:
            cur = con.execute("SELECT 1 FROM items WHERE id = ?", (it["id"],))
            exists = cur.fetchone() is not None
            con.execute(
                """
                INSERT INTO items
                    (id, source_id, source_name, stream, lang, title, summary, url,
                     published, fetched_at, relevant, signals, source_weight)
                VALUES (:id,:source_id,:source_name,:stream,:lang,:title,:summary,:url,
                        :published,:fetched_at,:relevant,:signals,:source_weight)
                ON CONFLICT(id) DO UPDATE SET
                    relevant=excluded.relevant,
                    signals=excluded.signals
                """,
                {
                    **it,
                    "signals": json.dumps(it.get("signals", []), ensure_ascii=False),
                },
            )
            if not exists:
                new += 1
    return new


def recent_items(within_days: float, only_relevant: bool = True, limit: int = 5000) -> list[dict]:
    q = (
        "SELECT * FROM items WHERE published >= datetime('now', ?) "
        + ("AND relevant = 1 " if only_relevant else "")
        + "ORDER BY published DESC LIMIT ?"
    )
    with _conn() as con:
        rows = con.execute(q, (f"-{within_days} days", limit)).fetchall()
    out = []
    for r in rows:
        d = dict(r)
        try:
            d["signals"] = json.loads(d.get("signals") or "[]")
        except Exception:
            d["signals"] = []
        out.append(d)
    return out


# --------------------------------------------------------------------------- #
#  deepstate                                                                   #
# --------------------------------------------------------------------------- #
def last_deepstate() -> dict | None:
    with _conn() as con:
        r = con.execute(
            "SELECT * FROM deepstate_snaps ORDER BY id DESC LIMIT 1"
        ).fetchone()
    return dict(r) if r else None


def add_deepstate(snap: dict) -> dict:
    """Сохраняет снимок, вычислив delta к предыдущему успешному снимку."""
    prev = None
    with _conn() as con:
        r = con.execute(
            "SELECT occupied_km2 FROM deepstate_snaps WHERE status='ok' "
            "ORDER BY id DESC LIMIT 1"
        ).fetchone()
        prev = r["occupied_km2"] if r else None
    delta = None
    if snap.get("status") == "ok" and prev is not None:
        delta = round(snap["occupied_km2"] - prev, 2)
    snap = {**snap, "delta_km2": delta}
    with _WRITE_LOCK, _conn() as con:
        con.execute(
            """INSERT INTO deepstate_snaps
               (taken_at, occupied_km2, unknown_km2, occupied_polys, delta_km2, status)
               VALUES (:taken_at,:occupied_km2,:unknown_km2,:occupied_polys,:delta_km2,:status)""",
            {
                "taken_at": snap.get("taken_at"),
                "occupied_km2": snap.get("occupied_km2"),
                "unknown_km2": snap.get("unknown_km2"),
                "occupied_polys": snap.get("occupied_polys"),
                "delta_km2": delta,
                "status": snap.get("status"),
            },
        )
    return snap


# --------------------------------------------------------------------------- #
#  readings                                                                    #
# --------------------------------------------------------------------------- #
def add_reading(reading: dict) -> None:
    with _WRITE_LOCK, _conn() as con:
        con.execute(
            """INSERT INTO readings
               (taken_at, barometer, llm_barometer, final_barometer, velocity,
                predicted_date, confidence, components, drivers, sources)
               VALUES (:taken_at,:barometer,:llm_barometer,:final_barometer,:velocity,
                       :predicted_date,:confidence,:components,:drivers,:sources)""",
            {
                "taken_at": reading["taken_at"],
                "barometer": reading["barometer"],
                "llm_barometer": reading.get("llm_barometer"),
                "final_barometer": reading["final_barometer"],
                "velocity": reading.get("velocity"),
                "predicted_date": reading.get("predicted_date"),
                "confidence": reading.get("confidence"),
                "components": json.dumps(reading.get("components", {}), ensure_ascii=False),
                "drivers": json.dumps(reading.get("drivers", []), ensure_ascii=False),
                "sources": json.dumps(reading.get("sources", []), ensure_ascii=False),
            },
        )


def last_reading() -> dict | None:
    with _conn() as con:
        r = con.execute("SELECT * FROM readings ORDER BY id DESC LIMIT 1").fetchone()
    return _decode_reading(r) if r else None


def reading_history(limit: int = 180) -> list[dict]:
    with _conn() as con:
        rows = con.execute(
            "SELECT * FROM readings ORDER BY id DESC LIMIT ?", (limit,)
        ).fetchall()
    return [_decode_reading(r) for r in reversed(rows)]


def _decode_reading(r: sqlite3.Row) -> dict:
    d = dict(r)
    for k in ("components", "drivers", "sources"):
        try:
            d[k] = json.loads(d.get(k) or ("[]" if k != "components" else "{}"))
        except Exception:
            d[k] = [] if k != "components" else {}
    return d


# --------------------------------------------------------------------------- #
#  source_status                                                               #
# --------------------------------------------------------------------------- #
def set_source_status(status: dict) -> None:
    with _WRITE_LOCK, _conn() as con:
        con.execute(
            """INSERT INTO source_status (source_id, name, stream, mode, last_ok, last_error, items_count)
               VALUES (:source_id,:name,:stream,:mode,:last_ok,:last_error,:items_count)
               ON CONFLICT(source_id) DO UPDATE SET
                   name=excluded.name, stream=excluded.stream, mode=excluded.mode,
                   last_ok=COALESCE(excluded.last_ok, source_status.last_ok),
                   last_error=excluded.last_error, items_count=excluded.items_count""",
            {
                "source_id": status["source_id"],
                "name": status.get("name"),
                "stream": status.get("stream"),
                "mode": status.get("mode"),
                "last_ok": status.get("last_ok"),
                "last_error": status.get("last_error"),
                "items_count": status.get("items_count", 0),
            },
        )


def all_source_status() -> list[dict]:
    with _conn() as con:
        rows = con.execute("SELECT * FROM source_status ORDER BY stream, name").fetchall()
    return [dict(r) for r in rows]
