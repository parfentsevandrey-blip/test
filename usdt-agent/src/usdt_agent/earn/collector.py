"""Reconciliation: turning "they said they'd pay" into "the money is here".

This is the only component allowed to declare income, and it does so from one
source of truth — the chain. Two detection paths, used in order:

1. **Transfer enumeration.** Precise: tx hash, sender, exact amount. Each new
   transfer is matched against an outstanding expectation (a service invoice by
   its unique amount, otherwise a payout of the right size) and recorded once,
   deduplicated by ``chain:tx_hash``.

2. **Balance delta.** Used only when a chain refuses to serve logs. If the
   balance is higher than the last mark, the difference is real money and is
   booked as unattributed income. Less informative, never wrong about the total.

Money that arrives without a matching expectation is still money: it is booked
as ``unattributed`` rather than discarded. Expectations that never arrive are
expired on their deadline, so the pipeline cannot quietly accumulate optimism.
"""

from __future__ import annotations

import logging
import time
from dataclasses import dataclass, field

from .models import OnChainTransfer, OrderStatus, Payout, PayoutStatus
from .store import EarnStore
from .wallet import Wallet

log = logging.getLogger(__name__)

#: Ignore sub-cent noise: dust transfers are spam, not revenue.
DUST_USDT = 0.01


@dataclass(slots=True)
class CollectionResult:
    """What one reconciliation pass found."""

    confirmed_usdt: float = 0.0
    new_transfers: int = 0
    matched: int = 0
    unattributed_usdt: float = 0.0
    expired: int = 0
    delta_detected: dict[str, float] = field(default_factory=dict)
    errors: dict[str, str] = field(default_factory=dict)
    balances: dict[str, float] = field(default_factory=dict)
    baselined: dict[str, float] = field(default_factory=dict)

    @property
    def found_money(self) -> bool:
        return self.confirmed_usdt > 0


class Collector:
    """Watches the wallet and reconciles it against outstanding expectations."""

    def __init__(
        self,
        wallet: Wallet,
        store: EarnStore,
        *,
        lookback_blocks: int = 2_000,
        service_channel=None,  # ServiceChannel | None — for unique-amount matching
    ) -> None:
        self.wallet = wallet
        self.store = store
        self.lookback_blocks = lookback_blocks
        self.service_channel = service_channel

    # -- matching --------------------------------------------------------
    def _match_expected(self, transfer: OnChainTransfer) -> dict | None:
        """Find an outstanding expectation this transfer settles.

        Exact-amount matches win; otherwise the closest expectation within 2 %
        on the same chain. Anything looser would mis-attribute revenue between
        channels, which corrupts the per-channel scoreboard.
        """
        candidates = [
            p for p in self.store.payouts(status=PayoutStatus.EXPECTED)
            if not p["chain"] or p["chain"] == transfer.chain
        ]
        if not candidates:
            return None

        exact = [p for p in candidates if abs(p["amount_usdt"] - transfer.amount_usdt) <= 5e-5]
        if exact:
            return exact[0]

        near = [
            p for p in candidates
            if p["amount_usdt"] > 0
            and abs(p["amount_usdt"] - transfer.amount_usdt) / p["amount_usdt"] <= 0.02
        ]
        if near:
            return min(near, key=lambda p: abs(p["amount_usdt"] - transfer.amount_usdt))
        return None

    def _settle_service_invoice(self, transfer: OnChainTransfer) -> str:
        """Release a digital deliverable when its unique amount is paid."""
        if self.service_channel is None:
            return ""
        invoice = self.service_channel.match_payment(transfer.amount_usdt)
        if invoice is None:
            return ""
        deliverable = self.service_channel.settle(invoice, transfer.tx_hash)
        log.info("invoice %s settled by %s", invoice.invoice_id, transfer.tx_hash[:16])
        return deliverable

    # -- the pass --------------------------------------------------------
    def collect(self) -> CollectionResult:
        result = CollectionResult()
        result.expired = self.expire_stale()

        for chain in self.wallet.chains():
            try:
                balance = self.wallet.balance(chain)
                result.balances[chain] = balance
            except Exception as e:
                result.errors[chain] = str(e)[:160]
                log.warning("collector: balance unavailable on %s: %s", chain, str(e)[:100])
                continue

            transfers = self.wallet.transfers(chain, self.lookback_blocks)

            if self.store.last_balance(chain) is None:
                # First sight of this chain. Whatever is already here was not
                # earned by the agent, and the transfer history predates it —
                # booking either would inflate the treasury with someone else's
                # money. Record a baseline and count only what arrives after it.
                for transfer in transfers:
                    self.store.record_transfer(transfer, matched_payout="baseline")
                self.store.mark_balance(chain, balance)
                result.baselined[chain] = balance
                self.store.ledger.append("earn_baseline", {
                    "chain": chain, "opening_balance": round(balance, 6),
                    "transfers_ignored": len(transfers),
                })
                log.info(
                    "collector: baselined %s at %.6f USDT (%d pre-existing transfers ignored)",
                    chain, balance, len(transfers),
                )
                continue

            new = [
                t for t in transfers
                if t.amount_usdt >= DUST_USDT and not self.store.seen_transfer(t.key)
            ]

            for transfer in new:
                result.new_transfers += 1
                result.confirmed_usdt += transfer.amount_usdt
                self._book(transfer, result)

            if not transfers:
                # Path 2: logs unavailable — fall back to the balance delta.
                last = self.store.last_balance(chain)
                if last is not None and balance - last > DUST_USDT:
                    delta = balance - last
                    result.delta_detected[chain] = delta
                    result.confirmed_usdt += delta
                    result.unattributed_usdt += delta
                    self.store.save_payout(Payout(
                        channel="unattributed",
                        amount_usdt=delta,
                        status=PayoutStatus.CONFIRMED,
                        chain=chain,
                        address=self.wallet.addresses.get(chain, ""),
                        confirmed_ts=time.time(),
                        memo="balance increase (transfer log unavailable)",
                        meta={"detection": "balance_delta", "previous": last, "current": balance},
                    ))
                    log.info("collector: +%.6f USDT on %s via balance delta", delta, chain)

            self.store.mark_balance(chain, balance)

        if result.found_money:
            log.info(
                "collector: +%.6f USDT confirmed (%d transfers, %d matched)",
                result.confirmed_usdt, result.new_transfers, result.matched,
            )
        return result

    def _book(self, transfer: OnChainTransfer, result: CollectionResult) -> None:
        """Record one confirmed transfer, attributing it if we can."""
        deliverable = self._settle_service_invoice(transfer)
        expected = None if deliverable else self._match_expected(transfer)

        if deliverable:
            channel, gig_id, order_id = "services", "", ""
            result.matched += 1
            memo = "service invoice settled"
        elif expected is not None:
            channel = expected["channel"]
            gig_id, order_id = expected["gig_id"], expected["order_id"]
            result.matched += 1
            memo = expected["memo"] or "expected payout received"
            # The expectation has been superseded by the real thing.
            self.store.conn.execute(
                "UPDATE payouts SET status = 'confirmed', tx_hash = ?, chain = ?, confirmed_ts = ? "
                "WHERE id = ?",
                (transfer.tx_hash, transfer.chain, time.time(), expected["id"]),
            )
            self.store.conn.commit()
            if order_id:
                self.store.set_order_status(order_id, OrderStatus.PAID, "payment confirmed on-chain")
        else:
            channel, gig_id, order_id = "unattributed", "", ""
            result.unattributed_usdt += transfer.amount_usdt
            memo = f"unmatched transfer from {transfer.from_address[:12] or 'unknown'}"

        payout = Payout(
            channel=channel,
            amount_usdt=transfer.amount_usdt,
            status=PayoutStatus.CONFIRMED,
            gig_id=gig_id,
            order_id=order_id,
            chain=transfer.chain,
            address=transfer.to_address,
            tx_hash=transfer.tx_hash,
            confirmed_ts=time.time(),
            memo=memo,
            meta={
                "from": transfer.from_address,
                "block": transfer.block,
                "explorer": self.wallet.explorer_url(transfer.chain, transfer.tx_hash),
                "deliverable": bool(deliverable),
            },
        )
        # Skip the double-book when an existing expectation row was upgraded.
        if expected is None:
            self.store.save_payout(payout)
        else:
            self.store.ledger.append("income", {
                "channel": channel, "amount_usdt": round(transfer.amount_usdt, 6),
                "chain": transfer.chain, "tx": transfer.tx_hash, "gig": gig_id,
            })
        self.store.record_transfer(transfer, matched_payout=payout.id)

    def expire_stale(self) -> int:
        """Retire expectations that blew through their deadline."""
        now = time.time()
        stale = [
            p for p in self.store.payouts(status=PayoutStatus.EXPECTED)
            if p["expected_by_ts"] and p["expected_by_ts"] < now
        ]
        for row in stale:
            self.store.conn.execute(
                "UPDATE payouts SET status = 'expired' WHERE id = ?", (row["id"],)
            )
        if stale:
            self.store.conn.commit()
            log.info("collector: expired %d overdue expectations", len(stale))
        return len(stale)

    def register_expectations(self, payouts: list[Payout]) -> int:
        """Store fresh expectations, skipping ones already tracked."""
        known = {
            (p["channel"], round(p["amount_usdt"], 6), p["memo"])
            for p in self.store.payouts(status=PayoutStatus.EXPECTED)
        }
        added = 0
        for payout in payouts:
            key = (payout.channel, round(payout.amount_usdt, 6), payout.memo)
            if key in known:
                continue
            self.store.save_payout(payout)
            known.add(key)
            added += 1
        return added
