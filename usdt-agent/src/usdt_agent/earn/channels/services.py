"""Selling a service for USDT — the only channel that earns while you sleep.

The agent runs a priced micro-service and settles payments **directly on-chain**,
with no payment processor, no merchant account and no custody: a customer sends
USDT to your address, the agent sees the transfer, and the service is delivered.

The mechanism that makes keyless settlement work is the **unique amount**. Every
invoice adds a small, deterministic number of cents to the price, so
`5.0041 USDT` identifies exactly one invoice on a shared address. This is how
payment matching is done without a per-customer address (which would need key
derivation) and without a processor (which would need custody and KYC).

Flow:

    catalogue → invoice (price + unique cents + address) → HTTP 402
      → customer pays on-chain → collector sees transfer → invoice settled
      → deliverable released

What this module does not do: hold funds, issue refunds, or sign anything. It
watches an address you control and releases a deliverable when money arrives.
"""

from __future__ import annotations

import hashlib
import json
import logging
import time
from dataclasses import dataclass, field
from typing import Any

from ..base import Channel
from ..models import Autonomy, Gig, OrderStatus, Payout, PayoutStatus, Requirement, WorkOrder

log = logging.getLogger(__name__)

#: Invoices expire so an abandoned unique-amount slot can be reused.
DEFAULT_TTL_S = 6 * 3600.0


@dataclass(slots=True)
class ServiceOffer:
    """One thing the agent sells."""

    sku: str
    title: str
    price_usdt: float
    description: str = ""
    delivery: str = "digital"
    est_minutes: float = 5.0

    def to_dict(self) -> dict[str, Any]:
        return {
            "sku": self.sku, "title": self.title, "price_usdt": self.price_usdt,
            "description": self.description, "delivery": self.delivery,
        }


@dataclass(slots=True)
class Invoice:
    """A payment request bound to a unique amount."""

    invoice_id: str
    sku: str
    chain: str
    address: str
    base_price: float
    amount_usdt: float          # base price + unique cents — pay exactly this
    created_ts: float = field(default_factory=time.time)
    expires_ts: float = 0.0
    customer_ref: str = ""
    status: str = "unpaid"
    tx_hash: str = ""

    @property
    def is_expired(self) -> bool:
        return self.expires_ts > 0 and self.expires_ts < time.time()

    def to_dict(self) -> dict[str, Any]:
        return {
            "invoice_id": self.invoice_id, "sku": self.sku, "chain": self.chain,
            "pay_to": self.address, "pay_exactly_usdt": round(self.amount_usdt, 6),
            "status": self.status, "expires_ts": self.expires_ts,
            "note": "send exactly this amount — the cents identify your invoice",
        }


class ServiceChannel(Channel):
    """Sells a catalogue of digital services, settled on-chain."""

    name = "services"
    description = "Sells a priced micro-service for USDT, settled directly on-chain"
    autonomy = Autonomy.AUTO  # invoicing, matching and delivery need no human
    capital_required_usdt = 0.0
    typical_lag_days = 0.0

    @staticmethod
    def defaults() -> dict[str, Any]:
        return {
            "preferred_chain": "tron",   # cheapest for small payments
            "invoice_ttl_s": DEFAULT_TTL_S,
            "unique_cents_range": 500,   # 0.0000–0.0499 of entropy per invoice
            "catalogue": [
                {"sku": "market-report", "title": "Stablecoin yield report",
                 "price_usdt": 5.0, "est_minutes": 2.0,
                 "description": "Current risk-adjusted stablecoin yields across chains, ranked."},
                {"sku": "arb-scan", "title": "Cross-venue spread scan",
                 "price_usdt": 3.0, "est_minutes": 1.0,
                 "description": "Live cross-exchange spreads for the majors, net of fees."},
            ],
        }

    def __init__(self, cfg, wallet, params=None) -> None:  # type: ignore[no-untyped-def]
        super().__init__(cfg, wallet, params)
        self.offers: dict[str, ServiceOffer] = {}
        for raw in self.params.get("catalogue") or []:
            offer = ServiceOffer(
                sku=str(raw["sku"]), title=str(raw.get("title", raw["sku"])),
                price_usdt=float(raw.get("price_usdt", 0.0)),
                description=str(raw.get("description", "")),
                est_minutes=float(raw.get("est_minutes", 5.0)),
            )
            self.offers[offer.sku] = offer
        self.invoices: dict[str, Invoice] = {}

    def requirements(self) -> list[Requirement]:
        return [
            Requirement(
                "wallet",
                "A receiving address — customers pay it directly, the agent only watches it",
                Autonomy.MANUAL,
                how_to="export USDT_WALLET_TRON=T... (Tron is cheapest for small payments)",
            ),
        ]

    # -- invoicing -------------------------------------------------------
    def _unique_cents(self, seed: str) -> float:
        """Deterministic sub-cent entropy that identifies one invoice."""
        digest = hashlib.sha256(seed.encode()).digest()
        span = max(1, int(self.params["unique_cents_range"]))
        return (int.from_bytes(digest[:4], "big") % span) / 10_000.0

    def create_invoice(self, sku: str, customer_ref: str = "") -> Invoice:
        offer = self.offers.get(sku)
        if offer is None:
            raise KeyError(f"unknown sku {sku!r}")
        chain, address = self.receiving_address()
        if not address:
            raise RuntimeError("no receiving address configured — set USDT_WALLET_<CHAIN>")

        seed = f"{sku}|{customer_ref}|{time.time():.6f}"
        invoice_id = hashlib.sha256(seed.encode()).hexdigest()[:16]
        amount = round(offer.price_usdt + self._unique_cents(invoice_id), 6)

        # Never issue two live invoices for the same amount: the whole matching
        # scheme rests on that amount being unambiguous.
        live = {
            inv.amount_usdt for inv in self.invoices.values()
            if inv.status == "unpaid" and not inv.is_expired
        }
        bump = 0
        while amount in live and bump < 50:
            bump += 1
            amount = round(amount + 0.0001, 6)

        invoice = Invoice(
            invoice_id=invoice_id, sku=sku, chain=chain, address=address,
            base_price=offer.price_usdt, amount_usdt=amount,
            expires_ts=time.time() + float(self.params["invoice_ttl_s"]),
            customer_ref=customer_ref,
        )
        self.invoices[invoice_id] = invoice
        log.info("invoice %s: %s for %.6f USDT on %s", invoice_id, sku, amount, chain)
        return invoice

    def match_payment(self, amount_usdt: float, tolerance: float = 0.00005) -> Invoice | None:
        """Find the unpaid invoice whose unique amount matches a transfer."""
        for invoice in self.invoices.values():
            if invoice.status != "unpaid" or invoice.is_expired:
                continue
            if abs(invoice.amount_usdt - amount_usdt) <= tolerance:
                return invoice
        return None

    def settle(self, invoice: Invoice, tx_hash: str) -> str:
        """Mark an invoice paid and return the deliverable."""
        invoice.status = "paid"
        invoice.tx_hash = tx_hash
        return self.deliver(invoice)

    def deliver(self, invoice: Invoice) -> str:
        """Produce what the customer bought.

        Kept intentionally simple and synchronous. Wire real handlers here — the
        payment side above does not care what the product is.
        """
        offer = self.offers.get(invoice.sku)
        if offer is None:
            return "unknown product"
        return json.dumps({
            "invoice": invoice.invoice_id,
            "sku": invoice.sku,
            "title": offer.title,
            "delivered_ts": time.time(),
            "content": f"{offer.title}: generated on demand. {offer.description}",
        }, indent=2)

    # -- Channel interface -----------------------------------------------
    def discover(self) -> list[Gig]:
        """Each unpaid, unexpired invoice is a gig worth exactly its price."""
        out: list[Gig] = []
        for invoice in self.invoices.values():
            if invoice.status != "unpaid" or invoice.is_expired:
                continue
            offer = self.offers.get(invoice.sku)
            out.append(Gig(
                channel=self.name,
                external_id=invoice.invoice_id,
                title=f"invoice {invoice.invoice_id} — {invoice.sku}",
                reward_usdt=invoice.amount_usdt,
                effort_hours=max(0.01, (offer.est_minutes if offer else 5.0) / 60.0),
                # An issued invoice is not a sale. Most quotes never convert.
                payout_probability=0.25,
                deadline_ts=invoice.expires_ts,
                source="invoice",
                meta={"sku": invoice.sku, "chain": invoice.chain,
                      "amount": invoice.amount_usdt, "customer": invoice.customer_ref},
            ))
        return out

    def plan(self, gig: Gig) -> WorkOrder:
        return WorkOrder(
            gig_id=gig.id, channel=self.name, title=gig.title,
            plan=("await the exact on-chain amount", "verify the transfer", "release the deliverable"),
            status=OrderStatus.SUBMITTED,   # nothing for a human to approve
            reward_usdt=gig.reward_usdt,
            estimated_hours=gig.effort_hours,
            autonomy=Autonomy.AUTO,
            meta=dict(gig.meta),
        )

    def expected_payouts(self) -> list[Payout]:
        out: list[Payout] = []
        for invoice in self.invoices.values():
            if invoice.status != "unpaid" or invoice.is_expired:
                continue
            out.append(Payout(
                channel=self.name,
                amount_usdt=invoice.amount_usdt,
                status=PayoutStatus.EXPECTED,
                chain=invoice.chain,
                address=invoice.address,
                expected_by_ts=invoice.expires_ts,
                memo=f"invoice {invoice.invoice_id}",
                meta={"invoice_id": invoice.invoice_id, "sku": invoice.sku},
            ))
        return out

    def catalogue(self) -> list[dict[str, Any]]:
        return [o.to_dict() for o in self.offers.values()]
