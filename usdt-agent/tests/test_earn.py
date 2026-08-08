"""Tests for the earning half: channels, wallet, collector, ladder, orchestrator."""

from __future__ import annotations

import json
import sys
import time
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from usdt_agent.config import load_config
from usdt_agent.earn import build_channels, setup_checklist
from usdt_agent.earn.base import estimate_effort_hours, payout_probability
from usdt_agent.earn.bootstrap import assess_ladder
from usdt_agent.earn.channels.affiliate import AffiliateChannel
from usdt_agent.earn.channels.bounties import BountyChannel
from usdt_agent.earn.channels.passive import PassiveYieldChannel
from usdt_agent.earn.channels.services import ServiceChannel
from usdt_agent.earn.collector import Collector
from usdt_agent.earn.models import (
    Autonomy,
    Gig,
    OnChainTransfer,
    OrderStatus,
    Payout,
    PayoutStatus,
    parse_reward,
)
from usdt_agent.earn.orchestrator import EarningAgent
from usdt_agent.earn.store import EarnStore
from usdt_agent.earn.wallet import Wallet, _pad_address
from usdt_agent.ledger import Ledger


class FakeWallet(Wallet):
    """A wallet whose chain state is scripted, for deterministic reconciliation."""

    def __init__(self, balances=None, transfers=None, failing=()):  # type: ignore[no-untyped-def]
        super().__init__(addresses={c: f"addr-{c}" for c in (balances or {"tron": 0.0})})
        self._balances = dict(balances or {"tron": 0.0})
        self._transfers = dict(transfers or {})
        self._failing = set(failing)

    def balance(self, chain: str) -> float:
        if chain in self._failing:
            raise RuntimeError("rpc down")
        return self._balances.get(chain, 0.0)

    def transfers(self, chain: str, lookback_blocks: int = 2000):  # type: ignore[no-untyped-def]
        return list(self._transfers.get(chain, []))

    def credit(self, chain: str, transfer: OnChainTransfer) -> None:
        self._balances[chain] = self._balances.get(chain, 0.0) + transfer.amount_usdt
        self._transfers.setdefault(chain, []).append(transfer)


def transfer(amount: float, tx: str, chain: str = "tron", sender: str = "0xpayer") -> OnChainTransfer:
    return OnChainTransfer(
        chain=chain, to_address=f"addr-{chain}", from_address=sender,
        amount_usdt=amount, tx_hash=tx, block=1, ts=time.time(),
    )


class TestParsing(unittest.TestCase):
    def test_parse_reward_formats(self) -> None:
        self.assertEqual(parse_reward("Fix the parser ($500)"), 500.0)
        self.assertEqual(parse_reward("bounty: 1,250 USDT"), 1250.0)
        self.assertEqual(parse_reward("pays 75.5 usdc for this"), 75.5)
        self.assertEqual(parse_reward("$2,000 reward"), 2000.0)

    def test_parse_reward_is_conservative(self) -> None:
        self.assertEqual(parse_reward(""), 0.0)
        self.assertEqual(parse_reward("no money here"), 0.0)
        # Bare years/issue numbers must not be read as money.
        self.assertEqual(parse_reward("issue 4711 opened in 2024"), 0.0)

    def test_parse_reward_picks_the_largest_plausible(self) -> None:
        self.assertEqual(parse_reward("$50 now, $300 on merge"), 300.0)

    def test_effort_estimates_are_ordered(self) -> None:
        easy = estimate_effort_hours("fix typo in docs", ("good first issue",))
        medium = estimate_effort_hours("implement pagination for the API")
        hard = estimate_effort_hours("security audit of the signer", ("security",))
        self.assertLess(easy, medium)
        self.assertLess(medium, hard)

    def test_payout_probability_is_pessimistic(self) -> None:
        base = payout_probability(has_explicit_reward=True)
        self.assertLess(base, 0.7)
        self.assertLess(payout_probability(has_explicit_reward=False), base)
        # Competition and staleness both cut the odds.
        self.assertLess(payout_probability(has_explicit_reward=True, assignees=1), base)
        self.assertLess(payout_probability(has_explicit_reward=True, participants=5), base)
        self.assertLess(payout_probability(has_explicit_reward=True, age_days=365), base)
        self.assertGreaterEqual(payout_probability(has_explicit_reward=False, assignees=9), 0.01)


class TestGig(unittest.TestCase):
    def test_expected_value_discounts_the_odds(self) -> None:
        g = Gig("c", "1", "t", reward_usdt=1000.0, effort_hours=10.0, payout_probability=0.2)
        self.assertAlmostEqual(g.expected_usdt, 200.0)
        self.assertAlmostEqual(g.usdt_per_hour, 20.0)

    def test_a_certain_small_gig_beats_a_lottery_ticket(self) -> None:
        lottery = Gig("c", "1", "big", reward_usdt=5000.0, effort_hours=40.0, payout_probability=0.03)
        solid = Gig("c", "2", "small", reward_usdt=120.0, effort_hours=2.0, payout_probability=0.8)
        self.assertGreater(solid.score(), lottery.score())

    def test_expired_gigs_score_zero(self) -> None:
        g = Gig("c", "1", "t", reward_usdt=500.0, effort_hours=1.0,
                payout_probability=0.9, deadline_ts=time.time() - 10)
        self.assertTrue(g.is_expired)
        self.assertEqual(g.score(), 0.0)

    def test_floor_rate_filters(self) -> None:
        g = Gig("c", "1", "t", reward_usdt=10.0, effort_hours=10.0, payout_probability=1.0)
        self.assertEqual(g.score(min_rate=15.0), 0.0)
        self.assertGreater(g.score(min_rate=0.5), 0.0)

    def test_id_is_stable_and_distinct(self) -> None:
        a = Gig("c", "abc", "t", source="github")
        b = Gig("c", "abc", "different title", source="github")
        c = Gig("c", "xyz", "t", source="github")
        self.assertEqual(a.id, b.id)
        self.assertNotEqual(a.id, c.id)


class TestWalletParsing(unittest.TestCase):
    def test_address_padding(self) -> None:
        padded = _pad_address("0x28C6c06298d514Db089934071355E5743bf21d60")
        self.assertEqual(len(padded), 64)
        self.assertTrue(padded.endswith("28c6c06298d514db089934071355e5743bf21d60"))

    def test_evm_transfers_parse(self) -> None:
        w = Wallet(addresses={"ethereum": "0x" + "11" * 20})
        logs = [{
            "topics": [
                "0xddf252ad", "0x" + "00" * 12 + "22" * 20, "0x" + "00" * 12 + "11" * 20,
            ],
            "data": hex(5_000_000),  # 5.0 USDT at 6 decimals
            "transactionHash": "0xabc",
            "blockNumber": "0x10",
        }]
        with mock.patch.object(Wallet, "_rpc", side_effect=["0x100", logs]):
            out = w.transfers("ethereum")
        self.assertEqual(len(out), 1)
        self.assertAlmostEqual(out[0].amount_usdt, 5.0)
        self.assertEqual(out[0].tx_hash, "0xabc")

    def test_tron_ignores_other_tokens_and_honours_decimals(self) -> None:
        """The 18-decimal impostor bug: a scam token must not become billions."""
        w = Wallet(addresses={"tron": "Tme"})
        payload = {"data": [
            {  # real USDT, 6 decimals
                "token_info": {"address": "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", "decimals": 6},
                "to": "Tme", "from": "Tpayer", "value": "7000000",
                "transaction_id": "real", "block_timestamp": 1,
            },
            {  # impostor token with 18 decimals — must be dropped entirely
                "token_info": {"address": "TSCAMSCAMSCAM", "decimals": 18},
                "to": "Tme", "from": "Tscam", "value": "9" * 24,
                "transaction_id": "scam", "block_timestamp": 2,
            },
            {  # right token, but addressed to somebody else
                "token_info": {"address": "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", "decimals": 6},
                "to": "Tsomeone", "from": "Tx", "value": "5000000",
                "transaction_id": "other", "block_timestamp": 3,
            },
        ]}
        with mock.patch("usdt_agent.http.get_json", return_value=payload):
            out = w.transfers("tron")
        self.assertEqual([t.tx_hash for t in out], ["real"])
        self.assertAlmostEqual(out[0].amount_usdt, 7.0)

    def test_balances_survive_a_dead_rpc(self) -> None:
        w = Wallet(addresses={"ethereum": "0x1", "bsc": "0x2"})
        with mock.patch.object(Wallet, "balance", side_effect=[RuntimeError("down"), 12.5]):
            balances, errors = w.balances()
        self.assertEqual(balances, {"bsc": 12.5})
        self.assertIn("ethereum", errors)

    def test_transfers_never_raise(self) -> None:
        w = Wallet(addresses={"ethereum": "0x1"})
        with mock.patch.object(Wallet, "_rpc", side_effect=RuntimeError("no logs for you")):
            self.assertEqual(w.transfers("ethereum"), [])

    def test_unknown_chain_is_ignored(self) -> None:
        w = Wallet(addresses={"dogecoin": "D123"})
        self.assertEqual(w.chains(), [])
        self.assertEqual(w.transfers("dogecoin"), [])


class TestStore(unittest.TestCase):
    def setUp(self) -> None:
        self.ledger = Ledger(":memory:")
        self.store = EarnStore(self.ledger)

    def tearDown(self) -> None:
        self.ledger.close()

    def test_gig_upsert_counts_only_new(self) -> None:
        gigs = [Gig("bounties", "1", "a", reward_usdt=100.0), Gig("bounties", "2", "b")]
        self.assertEqual(self.store.upsert_gigs(gigs), 2)
        self.assertEqual(self.store.upsert_gigs(gigs), 0)
        self.assertEqual(len(self.store.top_gigs()), 2)

    def test_only_confirmed_payouts_are_income(self) -> None:
        self.store.save_payout(Payout("bounties", 500.0, PayoutStatus.EXPECTED))
        self.store.save_payout(Payout("bounties", 40.0, PayoutStatus.CONFIRMED, tx_hash="0x1"))
        self.assertAlmostEqual(self.store.confirmed_total(), 40.0)
        self.assertAlmostEqual(self.store.expected_total(), 500.0)

    def test_confirmed_payouts_enter_the_journal(self) -> None:
        self.store.save_payout(Payout("bounties", 500.0, PayoutStatus.EXPECTED))
        self.assertEqual(len(self.ledger.events(kind="income")), 0)
        self.store.save_payout(Payout("bounties", 40.0, PayoutStatus.CONFIRMED, tx_hash="0x1"))
        self.assertEqual(len(self.ledger.events(kind="income")), 1)
        self.assertTrue(self.ledger.verify()[0])

    def test_transfer_dedup(self) -> None:
        t = transfer(5.0, "0xdead")
        self.assertTrue(self.store.record_transfer(t))
        self.assertFalse(self.store.record_transfer(t))

    def test_approval_flow(self) -> None:
        approval_id = self.store.request_approval("work_order", "claim a bounty", subject_id="o1")
        self.assertEqual(len(self.store.pending_approvals()), 1)
        self.assertTrue(self.store.decide(approval_id, True, "go"))
        self.assertEqual(len(self.store.pending_approvals()), 0)
        self.assertFalse(self.store.decide(approval_id, True))  # not pending twice

    def test_effort_accumulates(self) -> None:
        self.store.log_effort("bounties", 2.5)
        self.store.log_effort("bounties", 1.5)
        self.store.log_effort("services", 0.5)
        self.assertAlmostEqual(self.store.hours_spent("bounties"), 4.0)
        self.assertAlmostEqual(self.store.hours_spent(), 4.5)

    def test_balance_marks(self) -> None:
        self.assertIsNone(self.store.last_balance("tron"))
        self.store.mark_balance("tron", 10.0, ts=1.0)
        self.store.mark_balance("tron", 12.0, ts=2.0)
        self.assertAlmostEqual(self.store.last_balance("tron"), 12.0)


class TestCollector(unittest.TestCase):
    def setUp(self) -> None:
        self.ledger = Ledger(":memory:")
        self.store = EarnStore(self.ledger)

    def tearDown(self) -> None:
        self.ledger.close()

    def test_first_pass_baselines_instead_of_booking_history(self) -> None:
        """A pre-funded wallet must not report its balance as agent earnings."""
        wallet = FakeWallet({"tron": 500.0}, {"tron": [transfer(500.0, "0xold")]})
        result = Collector(wallet, self.store).collect()
        self.assertEqual(result.confirmed_usdt, 0.0)
        self.assertIn("tron", result.baselined)
        self.assertAlmostEqual(self.store.confirmed_total(), 0.0)

    def test_money_arriving_after_the_baseline_is_income(self) -> None:
        wallet = FakeWallet({"tron": 500.0}, {"tron": [transfer(500.0, "0xold")]})
        collector = Collector(wallet, self.store)
        collector.collect()                       # baseline
        wallet.credit("tron", transfer(42.0, "0xnew"))
        result = collector.collect()
        self.assertAlmostEqual(result.confirmed_usdt, 42.0)
        self.assertAlmostEqual(self.store.confirmed_total(), 42.0)

    def test_the_same_transfer_is_never_booked_twice(self) -> None:
        wallet = FakeWallet({"tron": 0.0}, {"tron": []})
        collector = Collector(wallet, self.store)
        collector.collect()
        wallet.credit("tron", transfer(10.0, "0xonce"))
        self.assertAlmostEqual(collector.collect().confirmed_usdt, 10.0)
        self.assertAlmostEqual(collector.collect().confirmed_usdt, 0.0)
        self.assertAlmostEqual(self.store.confirmed_total(), 10.0)

    def test_dust_is_ignored(self) -> None:
        wallet = FakeWallet({"tron": 0.0}, {"tron": []})
        collector = Collector(wallet, self.store)
        collector.collect()
        wallet.credit("tron", transfer(0.000001, "0xdust"))
        self.assertAlmostEqual(collector.collect().confirmed_usdt, 0.0)

    def test_expected_payout_is_matched_and_upgraded(self) -> None:
        wallet = FakeWallet({"tron": 0.0}, {"tron": []})
        collector = Collector(wallet, self.store)
        collector.collect()
        self.store.save_payout(Payout("bounties", 250.0, PayoutStatus.EXPECTED,
                                      chain="tron", memo="bounty #12"))
        wallet.credit("tron", transfer(250.0, "0xpaid"))
        result = collector.collect()
        self.assertEqual(result.matched, 1)
        self.assertAlmostEqual(self.store.confirmed_total("bounties"), 250.0)
        self.assertAlmostEqual(self.store.expected_total(), 0.0)

    def test_unmatched_money_is_still_money(self) -> None:
        wallet = FakeWallet({"tron": 0.0}, {"tron": []})
        collector = Collector(wallet, self.store)
        collector.collect()
        wallet.credit("tron", transfer(17.0, "0xmystery"))
        result = collector.collect()
        self.assertAlmostEqual(result.unattributed_usdt, 17.0)
        self.assertAlmostEqual(self.store.confirmed_total("unattributed"), 17.0)

    def test_balance_delta_fallback_when_logs_are_unavailable(self) -> None:
        wallet = FakeWallet({"tron": 100.0}, {"tron": []})
        collector = Collector(wallet, self.store)
        collector.collect()               # baseline at 100
        wallet._balances["tron"] = 130.0  # money arrived, logs still unavailable
        result = collector.collect()
        self.assertAlmostEqual(result.delta_detected.get("tron", 0.0), 30.0)
        self.assertAlmostEqual(self.store.confirmed_total(), 30.0)

    def test_a_falling_balance_books_nothing(self) -> None:
        wallet = FakeWallet({"tron": 100.0}, {"tron": []})
        collector = Collector(wallet, self.store)
        collector.collect()
        wallet._balances["tron"] = 60.0
        self.assertAlmostEqual(collector.collect().confirmed_usdt, 0.0)

    def test_dead_chain_is_reported_not_fatal(self) -> None:
        wallet = FakeWallet({"tron": 5.0, "bsc": 5.0}, failing=("bsc",))
        result = Collector(wallet, self.store).collect()
        self.assertIn("bsc", result.errors)
        self.assertIn("tron", result.baselined)

    def test_overdue_expectations_expire(self) -> None:
        wallet = FakeWallet({"tron": 0.0}, {"tron": []})
        self.store.save_payout(Payout("affiliate", 30.0, PayoutStatus.EXPECTED,
                                      expected_by_ts=time.time() - 100))
        result = Collector(wallet, self.store).collect()
        self.assertEqual(result.expired, 1)
        self.assertAlmostEqual(self.store.expected_total(), 0.0)

    def test_register_expectations_is_idempotent(self) -> None:
        collector = Collector(FakeWallet(), self.store)
        payouts = [Payout("affiliate", 10.0, PayoutStatus.EXPECTED, memo="m")]
        self.assertEqual(collector.register_expectations(payouts), 1)
        self.assertEqual(collector.register_expectations(payouts), 0)


class TestServiceChannel(unittest.TestCase):
    def setUp(self) -> None:
        self.cfg = load_config()
        self.wallet = FakeWallet({"tron": 0.0})
        self.channel = ServiceChannel(self.cfg, self.wallet, {"preferred_chain": "tron"})

    def test_is_fully_autonomous(self) -> None:
        self.assertIs(self.channel.autonomy, Autonomy.AUTO)
        self.assertEqual(self.channel.capital_required_usdt, 0.0)

    def test_invoice_amounts_are_unique(self) -> None:
        amounts = {self.channel.create_invoice("market-report").amount_usdt for _ in range(25)}
        self.assertEqual(len(amounts), 25)

    def test_invoice_amount_exceeds_list_price(self) -> None:
        invoice = self.channel.create_invoice("market-report")
        self.assertGreaterEqual(invoice.amount_usdt, invoice.base_price)
        self.assertLess(invoice.amount_usdt - invoice.base_price, 0.06)

    def test_payment_matching_by_unique_amount(self) -> None:
        a = self.channel.create_invoice("market-report")
        b = self.channel.create_invoice("arb-scan")
        self.assertIs(self.channel.match_payment(a.amount_usdt), a)
        self.assertIs(self.channel.match_payment(b.amount_usdt), b)
        self.assertIsNone(self.channel.match_payment(999.0))

    def test_settle_releases_the_deliverable(self) -> None:
        invoice = self.channel.create_invoice("market-report")
        payload = json.loads(self.channel.settle(invoice, "0xtx"))
        self.assertEqual(invoice.status, "paid")
        self.assertEqual(payload["sku"], "market-report")

    def test_paid_invoice_stops_matching(self) -> None:
        invoice = self.channel.create_invoice("market-report")
        self.channel.settle(invoice, "0xtx")
        self.assertIsNone(self.channel.match_payment(invoice.amount_usdt))

    def test_unknown_sku_rejected(self) -> None:
        with self.assertRaises(KeyError):
            self.channel.create_invoice("nope")

    def test_no_address_means_no_invoice(self) -> None:
        channel = ServiceChannel(self.cfg, Wallet(addresses={}))
        with self.assertRaises(RuntimeError):
            channel.create_invoice("market-report")

    def test_expected_payouts_track_open_invoices(self) -> None:
        self.channel.create_invoice("market-report")
        self.assertEqual(len(self.channel.expected_payouts()), 1)
        self.assertEqual(self.channel.expected_payouts()[0].status, PayoutStatus.EXPECTED)

    def test_end_to_end_settlement_via_collector(self) -> None:
        ledger = Ledger(":memory:")
        store = EarnStore(ledger)
        collector = Collector(self.wallet, store, service_channel=self.channel)
        collector.collect()  # baseline

        invoice = self.channel.create_invoice("arb-scan", "customer-7")
        collector.register_expectations(self.channel.expected_payouts())
        self.wallet.credit("tron", transfer(invoice.amount_usdt, "0xcustomer"))

        result = collector.collect()
        self.assertAlmostEqual(result.confirmed_usdt, invoice.amount_usdt)
        self.assertEqual(invoice.status, "paid")
        self.assertAlmostEqual(store.confirmed_total("services"), invoice.amount_usdt)
        ledger.close()


class TestBountyChannel(unittest.TestCase):
    def setUp(self) -> None:
        self.cfg = load_config()
        self.channel = BountyChannel(self.cfg, FakeWallet({"tron": 0.0}))

    def _issue(self, **kw):  # type: ignore[no-untyped-def]
        base = {
            "id": 1, "title": "Fix the retry loop ($400)", "body": "details " * 50,
            "labels": [{"name": "💎 Bounty"}], "assignees": [], "comments": 2,
            "html_url": "https://github.com/acme/repo/issues/7",
            "created_at": "2026-08-01T00:00:00Z",
        }
        base.update(kw)
        return base

    def test_needs_a_wallet(self) -> None:
        blocked = BountyChannel(self.cfg, Wallet(addresses={}))
        ready, blockers = blocked.ready()
        self.assertFalse(ready)
        self.assertTrue(any("wallet" in b for b in blockers))

    def test_github_token_is_optional(self) -> None:
        ready, _ = self.channel.ready()
        self.assertTrue(ready)

    def test_issue_becomes_a_priced_gig(self) -> None:
        gig = self.channel._to_gig(self._issue())
        self.assertIsNotNone(gig)
        assert gig is not None
        self.assertAlmostEqual(gig.reward_usdt, 400.0)
        self.assertGreater(gig.effort_hours, 0)
        self.assertLess(gig.payout_probability, 1.0)
        self.assertEqual(gig.meta["repo"], "acme/repo")
        self.assertTrue(gig.meta["effort_is_estimated"])

    def test_cheap_issues_are_dropped(self) -> None:
        self.assertIsNone(self.channel._to_gig(self._issue(title="tiny fix ($3)")))

    def test_unpriced_issues_are_dropped(self) -> None:
        self.assertIsNone(self.channel._to_gig(
            self._issue(title="please fix", body="no money mentioned", labels=[])
        ))

    def test_assigned_issues_are_skipped(self) -> None:
        self.assertIsNone(self.channel._to_gig(self._issue(assignees=[{"login": "someone"}])))

    def test_discover_survives_a_dead_api(self) -> None:
        with mock.patch.object(BountyChannel, "_search", side_effect=RuntimeError("403")):
            self.assertEqual(self.channel.discover(), [])

    def test_discover_ranks_and_filters(self) -> None:
        issues = [
            self._issue(id=1, title="Big rewrite ($5000)", body="refactor " * 200),
            self._issue(id=2, title="Quick doc fix ($150)", body="typo",
                        labels=[{"name": "💎 Bounty"}, {"name": "good first issue"}]),
        ]
        with mock.patch.object(BountyChannel, "_search", return_value=issues):
            gigs = self.channel.discover()
        self.assertTrue(gigs)
        # Highest USDT/hour first, not highest headline reward.
        self.assertEqual(gigs[0].usdt_per_hour, max(g.usdt_per_hour for g in gigs))

    def test_never_claims_work_by_itself(self) -> None:
        gig = self.channel._to_gig(self._issue())
        assert gig is not None
        order = self.channel.execute(self.channel.plan(gig))
        self.assertIs(order.status, OrderStatus.AWAITING_APPROVAL)
        self.assertIs(self.channel.autonomy, Autonomy.ASSISTED)


class TestAffiliateChannel(unittest.TestCase):
    def setUp(self) -> None:
        self.cfg = load_config()

    def test_blocked_without_declared_programs(self) -> None:
        ch = AffiliateChannel(self.cfg, FakeWallet({"tron": 0.0}))
        ready, blockers = ch.ready()
        self.assertFalse(ready)
        self.assertTrue(any("programs" in b for b in blockers))

    def test_programs_below_their_threshold_are_not_expected_to_pay(self) -> None:
        ch = AffiliateChannel(self.cfg, FakeWallet({"tron": 0.0}), {"programs": [
            {"name": "small", "expected_monthly_usdt": 3.0, "min_payout_usdt": 50.0, "payout_day": 5},
        ]})
        gigs = ch.discover()
        self.assertTrue(gigs)
        self.assertLess(gigs[0].payout_probability, 0.1)
        self.assertEqual(ch.expected_payouts(), [])

    def test_qualifying_program_creates_an_expectation(self) -> None:
        ch = AffiliateChannel(self.cfg, FakeWallet({"tron": 0.0}), {"programs": [
            {"name": "big", "expected_monthly_usdt": 120.0, "min_payout_usdt": 50.0, "payout_day": 5},
        ]})
        payouts = ch.expected_payouts()
        self.assertEqual(len(payouts), 1)
        self.assertIs(payouts[0].status, PayoutStatus.EXPECTED)
        self.assertGreater(payouts[0].expected_by_ts, time.time())


class TestPassiveChannel(unittest.TestCase):
    def test_stays_off_below_the_capital_floor(self) -> None:
        ch = PassiveYieldChannel(load_config(), FakeWallet({"tron": 0.0}), {"min_deploy_usdt": 200.0})
        ch.set_treasury(12.0)
        ready, blockers = ch.ready()
        self.assertFalse(ready)
        self.assertTrue(any("confirmed earnings" in b for b in blockers))
        self.assertEqual(ch.discover(), [])

    def test_unlocks_once_the_treasury_clears_the_floor(self) -> None:
        ch = PassiveYieldChannel(load_config(), FakeWallet({"tron": 0.0}), {"min_deploy_usdt": 200.0})
        ch.set_treasury(250.0)
        self.assertTrue(ch.ready()[0])


class TestLadder(unittest.TestCase):
    def setUp(self) -> None:
        self.cfg = load_config()
        self.ledger = Ledger(":memory:")
        self.store = EarnStore(self.ledger)

    def tearDown(self) -> None:
        self.ledger.close()

    def test_starts_at_the_wallet_stage(self) -> None:
        channels = build_channels(self.cfg, Wallet(addresses={}))
        ladder = assess_ladder(channels, self.store, 0.0)
        self.assertEqual(ladder.current.key, "wallet")
        self.assertTrue(ladder.next_actions())

    def test_wallet_completes_the_first_stage(self) -> None:
        channels = build_channels(self.cfg, FakeWallet({"tron": 0.0}))
        ladder = assess_ladder(channels, self.store, 0.0)
        self.assertNotEqual(ladder.current.key, "wallet")

    def test_capital_stage_needs_real_money(self) -> None:
        channels = build_channels(self.cfg, FakeWallet({"tron": 0.0}))
        self.store.save_payout(Payout("bounties", 40.0, PayoutStatus.CONFIRMED, tx_hash="0x1"))
        ladder = assess_ladder(channels, self.store, 40.0, min_deploy_usdt=200.0)
        self.assertEqual(ladder.current.key, "capital")
        self.assertTrue(any("160.00" in a for a in ladder.next_actions()))

    def test_checklist_marks_who_must_act(self) -> None:
        channels = build_channels(self.cfg, Wallet(addresses={}))
        checklist = setup_checklist(channels)
        self.assertTrue(checklist)
        self.assertTrue(any(item["who"] == "you" for item in checklist))
        self.assertTrue(all("description" in item for item in checklist))


class TestEarningAgent(unittest.TestCase):
    def build(self, wallet=None, **params):  # type: ignore[no-untyped-def]
        cfg = load_config()
        wallet = wallet or FakeWallet({"tron": 0.0})
        channels = build_channels(cfg, wallet, params or None, ("bounties", "services"))
        ledger = Ledger(":memory:")
        return EarningAgent(cfg, channels, wallet, ledger, min_rate_usdt_per_hour=1.0)

    def test_treasury_starts_empty_and_only_grows_on_chain(self) -> None:
        agent = self.build()
        self.assertAlmostEqual(agent.treasury_usdt, 0.0)
        agent.step()
        self.assertAlmostEqual(agent.treasury_usdt, 0.0)
        agent.ledger.close()

    def test_cycle_creates_orders_and_queues_approvals(self) -> None:
        agent = self.build()
        issue = {
            "id": 5, "title": "Add a retry ($600)", "body": "x" * 200,
            "labels": [{"name": "💎 Bounty"}], "assignees": [], "comments": 0,
            "html_url": "https://github.com/a/b/issues/5", "created_at": "2026-08-01T00:00:00Z",
        }
        with mock.patch.object(BountyChannel, "_search", return_value=[issue]):
            report = agent.step()
        self.assertGreaterEqual(report.gigs_found, 1)
        self.assertGreaterEqual(report.orders_created, 1)
        # Anything touching a third party stops at the approval queue.
        self.assertGreaterEqual(report.approvals_pending, 1)
        agent.ledger.close()

    def test_a_broken_channel_does_not_stop_the_cycle(self) -> None:
        agent = self.build()
        with mock.patch.object(BountyChannel, "discover", side_effect=RuntimeError("boom")):
            report = agent.step()
        self.assertIn("bounties", report.errors)
        self.assertTrue(agent.ledger.events(kind="earn_error"))
        agent.ledger.close()

    def test_calibration_reports_unproven_until_paid(self) -> None:
        agent = self.build()
        issue = {
            "id": 6, "title": "Task ($300)", "body": "y" * 100,
            "labels": [{"name": "bounty"}], "assignees": [], "comments": 0,
            "html_url": "https://github.com/a/b/issues/6", "created_at": "2026-08-01T00:00:00Z",
        }
        with mock.patch.object(BountyChannel, "_search", return_value=[issue]):
            agent.step()
        calibration = agent.calibration()
        self.assertEqual(calibration["bounties"].label, "unproven")
        self.assertFalse(calibration["bounties"].proven)
        agent.ledger.close()

    def test_order_cap_is_respected(self) -> None:
        agent = self.build()
        agent.max_open_orders = 2
        issues = [{
            "id": i, "title": f"Task {i} ($300)", "body": "y" * 100,
            "labels": [{"name": "bounty"}], "assignees": [], "comments": 0,
            "html_url": f"https://github.com/a/b/issues/{i}",
            "created_at": "2026-08-01T00:00:00Z",
        } for i in range(10)]
        with mock.patch.object(BountyChannel, "_search", return_value=issues):
            agent.step()
        self.assertLessEqual(len(agent.store.open_orders()), 2)
        agent.ledger.close()

    def test_summary_shape_and_ledger_integrity(self) -> None:
        agent = self.build()
        agent.step()
        summary = agent.summary()
        for key in ("treasury_usdt", "wallet", "ladder", "channels", "ready_channels"):
            self.assertIn(key, summary)
        self.assertTrue(agent.ledger.verify()[0])
        agent.ledger.close()

    def test_passive_channel_sees_the_real_treasury(self) -> None:
        cfg = load_config()
        wallet = FakeWallet({"tron": 0.0})
        channels = build_channels(cfg, wallet, None, ("services", "passive"))
        ledger = Ledger(":memory:")
        agent = EarningAgent(cfg, channels, wallet, ledger)
        self.assertNotIn("passive", agent.ready_channels())
        agent.store.save_payout(Payout("bounties", 500.0, PayoutStatus.CONFIRMED, tx_hash="0x9"))
        self.assertIn("passive", agent.ready_channels())
        ledger.close()


class RecordingNotifier:
    """A notifier that keeps what it was told instead of sending it."""

    def __init__(self, enabled: bool = True) -> None:
        self.enabled = enabled
        self.sent: list[tuple[str, str]] = []

    def send(self, text: str, key: str = "default", force: bool = False) -> bool:
        self.sent.append((key, text))
        return True

    def keys(self) -> list[str]:
        return [k for k, _ in self.sent]


class TestAnnouncements(unittest.TestCase):
    """An unattended loop that cannot reach you is a loop that watches bounties
    expire. These check it reaches you for the three time-sensitive things."""

    def build(self, notifier=None, wallet=None):  # type: ignore[no-untyped-def]
        cfg = load_config()
        cfg.earn.notify_min_usdt_per_hour = 60.0
        wallet = wallet or FakeWallet({"tron": 0.0})
        channels = build_channels(cfg, wallet, None, ("bounties", "services"))
        ledger = Ledger(":memory:")
        agent = EarningAgent(cfg, channels, wallet, ledger,
                             min_rate_usdt_per_hour=1.0, notifier=notifier)
        return agent

    def test_confirmed_income_is_announced(self) -> None:
        note = RecordingNotifier()
        wallet = FakeWallet({"tron": 0.0}, {"tron": []})
        agent = self.build(note, wallet)
        agent.collect()                       # baseline
        wallet.credit("tron", transfer(75.0, "0xpaid"))
        with mock.patch.object(BountyChannel, "_search", return_value=[]):
            agent.step()
        self.assertIn("income", note.keys())
        self.assertTrue(any("75" in text for key, text in note.sent if key == "income"))
        agent.ledger.close()

    def test_a_waiting_decision_is_announced_once(self) -> None:
        note = RecordingNotifier()
        agent = self.build(note)
        issue = {
            "id": 11, "title": "Add retry budget ($900)", "body": "x" * 100,
            "labels": [{"name": "bounty"}], "assignees": [], "comments": 0,
            "html_url": "https://github.com/a/b/issues/11",
            "created_at": "2026-08-01T00:00:00Z",
        }
        with mock.patch.object(BountyChannel, "_search", return_value=[issue]):
            agent.step()
            first = note.keys().count("approvals")
            agent.step()
        self.assertEqual(first, 1)
        # The same pending approval must not be announced on every cycle.
        self.assertEqual(note.keys().count("approvals"), 1)
        agent.ledger.close()

    def test_a_high_rate_gig_is_announced(self) -> None:
        note = RecordingNotifier()
        agent = self.build(note)
        issue = {
            "id": 12, "title": "One-line config fix ($800)", "body": "trivial",
            "labels": [{"name": "bounty"}, {"name": "good first issue"}],
            "assignees": [], "comments": 0,
            "html_url": "https://github.com/a/b/issues/12",
            "created_at": "2026-08-01T00:00:00Z",
        }
        with mock.patch.object(BountyChannel, "_search", return_value=[issue]):
            agent.step()
        self.assertIn("hot_gig", note.keys())
        agent.ledger.close()

    def test_a_thin_gig_is_not_announced(self) -> None:
        note = RecordingNotifier()
        agent = self.build(note)
        issue = {
            "id": 13, "title": "Rewrite the storage layer ($300)", "body": "refactor " * 300,
            "labels": [{"name": "bounty"}], "assignees": [], "comments": 0,
            "html_url": "https://github.com/a/b/issues/13",
            "created_at": "2026-08-01T00:00:00Z",
        }
        with mock.patch.object(BountyChannel, "_search", return_value=[issue]):
            agent.step()
        self.assertNotIn("hot_gig", note.keys())
        agent.ledger.close()

    def test_nothing_is_sent_when_notifications_are_off(self) -> None:
        note = RecordingNotifier(enabled=False)
        agent = self.build(note)
        with mock.patch.object(BountyChannel, "_search", return_value=[]):
            agent.step()
        self.assertEqual(note.sent, [])
        agent.ledger.close()

    def test_cycles_zero_means_until_stopped_not_once(self) -> None:
        """A daemon flag that silently meant 'once' would leave the operator
        thinking the agent was watching when it had already exited."""
        agent = self.build(RecordingNotifier(enabled=False))
        calls = {"n": 0}
        original = agent.step

        def counted():  # type: ignore[no-untyped-def]
            calls["n"] += 1
            if calls["n"] >= 4:
                raise KeyboardInterrupt
            return original()

        agent.step = counted  # type: ignore[method-assign]
        with mock.patch.object(BountyChannel, "_search", return_value=[]):
            agent.run(cycles=0)
        self.assertEqual(calls["n"], 4)
        agent.ledger.close()

    def test_a_finite_cycle_count_is_respected(self) -> None:
        agent = self.build(RecordingNotifier(enabled=False))
        with mock.patch.object(BountyChannel, "_search", return_value=[]):
            agent.run(cycles=3)
        self.assertEqual(agent.cycle, 3)
        agent.ledger.close()


class TestEarnConfig(unittest.TestCase):
    def test_defaults(self) -> None:
        cfg = load_config()
        self.assertIn("bounties", cfg.earn.enabled())
        self.assertEqual(cfg.earn.wallet, {})
        self.assertGreater(cfg.earn.max_open_orders, 0)

    def test_toml_channel_params(self) -> None:
        import tempfile

        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / "c.toml"
            p.write_text(
                "[earn]\nmax_open_orders = 2\nmin_rate_usdt_per_hour = 40.0\n"
                '[earn.wallet]\ntron = "TXYZ"\n'
                "[earn.channels.affiliate]\nenabled = false\n"
                "[earn.channels.bounties.params]\nmin_reward_usdt = 100.0\n",
                encoding="utf-8",
            )
            cfg = load_config(p)
            self.assertEqual(cfg.earn.max_open_orders, 2)
            self.assertEqual(cfg.earn.wallet["tron"], "TXYZ")
            self.assertNotIn("affiliate", cfg.earn.enabled())
            self.assertAlmostEqual(cfg.earn.channel_params()["bounties"]["min_reward_usdt"], 100.0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
