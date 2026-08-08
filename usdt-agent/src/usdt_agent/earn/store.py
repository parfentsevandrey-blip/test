"""Persistence for the earning side, on the same tamper-evident ledger.

Reuses :class:`~usdt_agent.ledger.Ledger` — one SQLite file, one hash-chained
journal — so earned USDT and deployed USDT share a single audit trail. Money
arriving from a bounty and money made by the funding-carry strategy land in the
same book, which is the only way the treasury number can mean anything.

Every **confirmed** payout is appended to the journal. Expected and pending
payouts are not: they are somebody else's intention, and intentions do not
belong in an audit trail of income.
"""

from __future__ import annotations

import logging
import time
from typing import Any

from ..ledger import Ledger, canonical
from .models import (
    Autonomy,
    ChannelReport,
    Gig,
    OnChainTransfer,
    OrderStatus,
    Payout,
    PayoutStatus,
    WorkOrder,
)

log = logging.getLogger(__name__)

EARN_SCHEMA = """
CREATE TABLE IF NOT EXISTS gigs (
    id          TEXT PRIMARY KEY,
    channel     TEXT NOT NULL,
    source      TEXT NOT NULL DEFAULT '',
    external_id TEXT NOT NULL DEFAULT '',
    title       TEXT NOT NULL,
    url         TEXT NOT NULL DEFAULT '',
    reward_usdt REAL NOT NULL DEFAULT 0,
    effort_hours REAL NOT NULL DEFAULT 0,
    payout_probability REAL NOT NULL DEFAULT 0,
    usdt_per_hour REAL NOT NULL DEFAULT 0,
    deadline_ts REAL NOT NULL DEFAULT 0,
    discovered_ts REAL NOT NULL,
    meta        TEXT NOT NULL DEFAULT '{}'
);
CREATE INDEX IF NOT EXISTS idx_gigs_channel ON gigs(channel, usdt_per_hour);

CREATE TABLE IF NOT EXISTS orders (
    id          TEXT PRIMARY KEY,
    gig_id      TEXT NOT NULL,
    channel     TEXT NOT NULL,
    title       TEXT NOT NULL,
    status      TEXT NOT NULL,
    autonomy    TEXT NOT NULL DEFAULT 'assisted',
    reward_usdt REAL NOT NULL DEFAULT 0,
    estimated_hours REAL NOT NULL DEFAULT 0,
    actual_hours REAL NOT NULL DEFAULT 0,
    plan        TEXT NOT NULL DEFAULT '[]',
    deliverable TEXT NOT NULL DEFAULT '',
    notes       TEXT NOT NULL DEFAULT '',
    created_ts  REAL NOT NULL,
    updated_ts  REAL NOT NULL,
    meta        TEXT NOT NULL DEFAULT '{}'
);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status, channel);

CREATE TABLE IF NOT EXISTS payouts (
    id          TEXT PRIMARY KEY,
    channel     TEXT NOT NULL,
    gig_id      TEXT NOT NULL DEFAULT '',
    order_id    TEXT NOT NULL DEFAULT '',
    amount_usdt REAL NOT NULL,
    status      TEXT NOT NULL,
    chain       TEXT NOT NULL DEFAULT '',
    address     TEXT NOT NULL DEFAULT '',
    tx_hash     TEXT NOT NULL DEFAULT '',
    confirmations INTEGER NOT NULL DEFAULT 0,
    expected_by_ts REAL NOT NULL DEFAULT 0,
    created_ts  REAL NOT NULL,
    confirmed_ts REAL NOT NULL DEFAULT 0,
    memo        TEXT NOT NULL DEFAULT '',
    meta        TEXT NOT NULL DEFAULT '{}'
);
CREATE INDEX IF NOT EXISTS idx_payouts_status ON payouts(status, channel);

CREATE TABLE IF NOT EXISTS seen_transfers (
    key         TEXT PRIMARY KEY,
    chain       TEXT NOT NULL,
    tx_hash     TEXT NOT NULL,
    from_address TEXT NOT NULL DEFAULT '',
    amount_usdt REAL NOT NULL,
    block       INTEGER NOT NULL DEFAULT 0,
    seen_ts     REAL NOT NULL,
    matched_payout TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS balance_marks (
    chain       TEXT NOT NULL,
    ts          REAL NOT NULL,
    balance     REAL NOT NULL,
    PRIMARY KEY (chain, ts)
);

CREATE TABLE IF NOT EXISTS approvals (
    id          TEXT PRIMARY KEY,
    kind        TEXT NOT NULL,
    subject_id  TEXT NOT NULL DEFAULT '',
    channel     TEXT NOT NULL DEFAULT '',
    title       TEXT NOT NULL,
    detail      TEXT NOT NULL DEFAULT '',
    status      TEXT NOT NULL DEFAULT 'pending',
    created_ts  REAL NOT NULL,
    decided_ts  REAL NOT NULL DEFAULT 0,
    decision_note TEXT NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS idx_approvals_status ON approvals(status);

CREATE TABLE IF NOT EXISTS effort (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    channel     TEXT NOT NULL,
    order_id    TEXT NOT NULL DEFAULT '',
    hours       REAL NOT NULL,
    actor       TEXT NOT NULL DEFAULT 'agent',
    ts          REAL NOT NULL,
    note        TEXT NOT NULL DEFAULT ''
);
"""


class EarnStore:
    """Earning-side tables layered on a shared :class:`Ledger`."""

    def __init__(self, ledger: Ledger) -> None:
        self.ledger = ledger
        ledger.conn.executescript(EARN_SCHEMA)
        ledger.conn.commit()

    @property
    def conn(self):  # type: ignore[no-untyped-def]
        return self.ledger.conn

    # -- gigs ------------------------------------------------------------
    def upsert_gigs(self, gigs: list[Gig]) -> int:
        """Store discovered gigs. Returns how many were genuinely new."""
        if not gigs:
            return 0
        existing = {r["id"] for r in self.conn.execute("SELECT id FROM gigs")}
        new = 0
        with self.ledger.tx() as c:
            for g in gigs:
                if g.id not in existing:
                    new += 1
                c.execute(
                    """INSERT INTO gigs (id, channel, source, external_id, title, url,
                            reward_usdt, effort_hours, payout_probability, usdt_per_hour,
                            deadline_ts, discovered_ts, meta)
                       VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                       ON CONFLICT(id) DO UPDATE SET
                           reward_usdt=excluded.reward_usdt,
                           effort_hours=excluded.effort_hours,
                           payout_probability=excluded.payout_probability,
                           usdt_per_hour=excluded.usdt_per_hour,
                           deadline_ts=excluded.deadline_ts,
                           meta=excluded.meta""",
                    (g.id, g.channel, g.source, g.external_id, g.title, g.url,
                     g.reward_usdt, g.effort_hours, g.payout_probability, g.usdt_per_hour,
                     g.deadline_ts, g.discovered_ts, canonical(g.meta)),
                )
        return new

    def top_gigs(self, limit: int = 20, channel: str | None = None) -> list[dict[str, Any]]:
        sql = "SELECT * FROM gigs WHERE (deadline_ts = 0 OR deadline_ts > ?)"
        args: list[Any] = [time.time()]
        if channel:
            sql += " AND channel = ?"
            args.append(channel)
        sql += " ORDER BY usdt_per_hour DESC LIMIT ?"
        args.append(limit)
        return [dict(r) for r in self.conn.execute(sql, args)]

    def has_order_for(self, gig_id: str) -> bool:
        row = self.conn.execute("SELECT 1 FROM orders WHERE gig_id = ?", (gig_id,)).fetchone()
        return row is not None

    # -- orders ----------------------------------------------------------
    def save_order(self, order: WorkOrder) -> None:
        order.updated_ts = time.time()
        with self.ledger.tx() as c:
            c.execute(
                """INSERT INTO orders (id, gig_id, channel, title, status, autonomy,
                        reward_usdt, estimated_hours, actual_hours, plan, deliverable,
                        notes, created_ts, updated_ts, meta)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                   ON CONFLICT(id) DO UPDATE SET
                       status=excluded.status, actual_hours=excluded.actual_hours,
                       deliverable=excluded.deliverable, notes=excluded.notes,
                       updated_ts=excluded.updated_ts, meta=excluded.meta""",
                (order.id, order.gig_id, order.channel, order.title, order.status.value,
                 order.autonomy.value, order.reward_usdt, order.estimated_hours,
                 order.actual_hours, canonical(list(order.plan)), order.deliverable,
                 order.notes, order.created_ts, order.updated_ts, canonical(order.meta)),
            )

    def open_orders(self, channel: str | None = None) -> list[dict[str, Any]]:
        closed = (OrderStatus.PAID.value, OrderStatus.REJECTED.value, OrderStatus.ABANDONED.value)
        sql = f"SELECT * FROM orders WHERE status NOT IN ({','.join('?' * len(closed))})"
        args: list[Any] = list(closed)
        if channel:
            sql += " AND channel = ?"
            args.append(channel)
        return [dict(r) for r in self.conn.execute(sql + " ORDER BY created_ts DESC", args)]

    def set_order_status(self, order_id: str, status: OrderStatus, note: str = "") -> None:
        with self.ledger.tx() as c:
            c.execute(
                "UPDATE orders SET status = ?, updated_ts = ?, notes = COALESCE(NULLIF(?, ''), notes) "
                "WHERE id = ?",
                (status.value, time.time(), note, order_id),
            )

    # -- payouts ---------------------------------------------------------
    def save_payout(self, payout: Payout) -> None:
        with self.ledger.tx() as c:
            c.execute(
                """INSERT INTO payouts (id, channel, gig_id, order_id, amount_usdt, status,
                        chain, address, tx_hash, confirmations, expected_by_ts, created_ts,
                        confirmed_ts, memo, meta)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                   ON CONFLICT(id) DO UPDATE SET
                       status=excluded.status, chain=excluded.chain, tx_hash=excluded.tx_hash,
                       confirmations=excluded.confirmations, confirmed_ts=excluded.confirmed_ts,
                       amount_usdt=excluded.amount_usdt, meta=excluded.meta""",
                (payout.id, payout.channel, payout.gig_id, payout.order_id, payout.amount_usdt,
                 payout.status.value, payout.chain, payout.address, payout.tx_hash,
                 payout.confirmations, payout.expected_by_ts, payout.created_ts,
                 payout.confirmed_ts, payout.memo, canonical(payout.meta)),
            )
        # Only real, confirmed money enters the audit trail.
        if payout.status is PayoutStatus.CONFIRMED:
            self.ledger.append("income", {
                "channel": payout.channel, "amount_usdt": round(payout.amount_usdt, 6),
                "chain": payout.chain, "tx": payout.tx_hash, "gig": payout.gig_id,
            })

    def payouts(self, status: PayoutStatus | None = None, channel: str | None = None) -> list[dict[str, Any]]:
        sql, args = "SELECT * FROM payouts WHERE 1=1", []
        if status:
            sql += " AND status = ?"
            args.append(status.value)
        if channel:
            sql += " AND channel = ?"
            args.append(channel)
        return [dict(r) for r in self.conn.execute(sql + " ORDER BY created_ts DESC", args)]

    def confirmed_total(self, channel: str | None = None) -> float:
        sql = "SELECT COALESCE(SUM(amount_usdt), 0) AS t FROM payouts WHERE status = 'confirmed'"
        args: list[Any] = []
        if channel:
            sql += " AND channel = ?"
            args.append(channel)
        return float(self.conn.execute(sql, args).fetchone()["t"])

    def expected_total(self, channel: str | None = None) -> float:
        sql = "SELECT COALESCE(SUM(amount_usdt), 0) AS t FROM payouts WHERE status IN ('expected','pending')"
        args: list[Any] = []
        if channel:
            sql += " AND channel = ?"
            args.append(channel)
        return float(self.conn.execute(sql, args).fetchone()["t"])

    def confirmed_amounts(self, channel: str | None = None, limit: int = 500) -> list[float]:
        """Realized payout sizes, oldest first — the statistical gate's input."""
        sql = "SELECT amount_usdt FROM payouts WHERE status = 'confirmed'"
        args: list[Any] = []
        if channel:
            sql += " AND channel = ?"
            args.append(channel)
        sql += " ORDER BY confirmed_ts DESC LIMIT ?"
        args.append(limit)
        return [float(r["amount_usdt"]) for r in reversed(self.conn.execute(sql, args).fetchall())]

    # -- on-chain transfers ----------------------------------------------
    def record_transfer(self, transfer: OnChainTransfer, matched_payout: str = "") -> bool:
        """Store a transfer. Returns ``False`` if it had already been seen."""
        row = self.conn.execute("SELECT 1 FROM seen_transfers WHERE key = ?", (transfer.key,)).fetchone()
        if row is not None:
            return False
        with self.ledger.tx() as c:
            c.execute(
                """INSERT INTO seen_transfers (key, chain, tx_hash, from_address, amount_usdt,
                        block, seen_ts, matched_payout) VALUES (?,?,?,?,?,?,?,?)""",
                (transfer.key, transfer.chain, transfer.tx_hash, transfer.from_address,
                 transfer.amount_usdt, transfer.block, time.time(), matched_payout),
            )
        return True

    def seen_transfer(self, key: str) -> bool:
        return self.conn.execute(
            "SELECT 1 FROM seen_transfers WHERE key = ?", (key,)
        ).fetchone() is not None

    def mark_balance(self, chain: str, balance: float, ts: float | None = None) -> None:
        with self.ledger.tx() as c:
            c.execute("INSERT OR REPLACE INTO balance_marks (chain, ts, balance) VALUES (?,?,?)",
                      (chain, ts or time.time(), balance))

    def last_balance(self, chain: str) -> float | None:
        row = self.conn.execute(
            "SELECT balance FROM balance_marks WHERE chain = ? ORDER BY ts DESC LIMIT 1", (chain,)
        ).fetchone()
        return float(row["balance"]) if row else None

    # -- approvals -------------------------------------------------------
    def request_approval(
        self, kind: str, title: str, detail: str = "", subject_id: str = "", channel: str = ""
    ) -> str:
        import hashlib

        approval_id = hashlib.sha256(f"{kind}|{subject_id}|{title}".encode()).hexdigest()[:16]
        with self.ledger.tx() as c:
            c.execute(
                """INSERT INTO approvals (id, kind, subject_id, channel, title, detail,
                        status, created_ts) VALUES (?,?,?,?,?,?,'pending',?)
                   ON CONFLICT(id) DO NOTHING""",
                (approval_id, kind, subject_id, channel, title, detail, time.time()),
            )
        return approval_id

    def pending_approvals(self) -> list[dict[str, Any]]:
        return [dict(r) for r in self.conn.execute(
            "SELECT * FROM approvals WHERE status = 'pending' ORDER BY created_ts"
        )]

    def decide(self, approval_id: str, approved: bool, note: str = "") -> bool:
        row = self.conn.execute(
            "SELECT 1 FROM approvals WHERE id = ? AND status = 'pending'", (approval_id,)
        ).fetchone()
        if row is None:
            return False
        with self.ledger.tx() as c:
            c.execute(
                "UPDATE approvals SET status = ?, decided_ts = ?, decision_note = ? WHERE id = ?",
                ("approved" if approved else "rejected", time.time(), note, approval_id),
            )
        self.ledger.append("approval", {"id": approval_id, "approved": approved, "note": note})
        return True

    # -- effort ----------------------------------------------------------
    def log_effort(self, channel: str, hours: float, order_id: str = "",
                   actor: str = "agent", note: str = "") -> None:
        with self.ledger.tx() as c:
            c.execute(
                "INSERT INTO effort (channel, order_id, hours, actor, ts, note) VALUES (?,?,?,?,?,?)",
                (channel, order_id, hours, actor, time.time(), note),
            )

    def hours_spent(self, channel: str | None = None) -> float:
        sql = "SELECT COALESCE(SUM(hours), 0) AS h FROM effort"
        args: list[Any] = []
        if channel:
            sql += " WHERE channel = ?"
            args.append(channel)
        return float(self.conn.execute(sql, args).fetchone()["h"])

    # -- reporting -------------------------------------------------------
    def channel_report(self, channel: str, ready: bool, blockers: tuple[str, ...] = ()) -> ChannelReport:
        gigs = int(self.conn.execute(
            "SELECT COUNT(*) AS n FROM gigs WHERE channel = ?", (channel,)
        ).fetchone()["n"])
        return ChannelReport(
            channel=channel,
            ready=ready,
            blockers=blockers,
            gigs_found=gigs,
            orders_open=len(self.open_orders(channel)),
            expected_usdt=self.expected_total(channel),
            confirmed_usdt=self.confirmed_total(channel),
            hours_spent=self.hours_spent(channel),
        )

    def summary(self) -> dict[str, Any]:
        return {
            "confirmed_usdt": round(self.confirmed_total(), 6),
            "expected_usdt": round(self.expected_total(), 6),
            "hours_spent": round(self.hours_spent(), 3),
            "gigs": int(self.conn.execute("SELECT COUNT(*) AS n FROM gigs").fetchone()["n"]),
            "open_orders": len(self.open_orders()),
            "pending_approvals": len(self.pending_approvals()),
            "transfers_seen": int(
                self.conn.execute("SELECT COUNT(*) AS n FROM seen_transfers").fetchone()["n"]
            ),
        }


def autonomy_of(raw: str) -> Autonomy:
    try:
        return Autonomy(raw)
    except ValueError:
        return Autonomy.ASSISTED
