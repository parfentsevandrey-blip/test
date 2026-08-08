"""Tests for feeds, execution, strategies and the end-to-end agent loop."""

from __future__ import annotations

import os
import sys
import time
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from usdt_agent.agent import Agent
from usdt_agent.config import ExecutionConfig, load_config
from usdt_agent.execution import LiveTradingBlocked, PaperBroker, build_broker, live_interlocks
from usdt_agent.execution.live import CONFIRM_ENV, CONFIRM_VALUE
from usdt_agent.feeds import CompositeFeed, SyntheticFeed
from usdt_agent.feeds.base import Feed, from_venue_symbol, to_venue_symbol
from usdt_agent.ledger import Ledger
from usdt_agent.models import (
    BPS,
    FundingRate,
    Instrument,
    MarketSnapshot,
    Order,
    Quote,
    Side,
    Trade,
    YieldPool,
)
from usdt_agent.strategies import build_strategies
from usdt_agent.strategies.base import leg_cost_bps, round_trip_cost_bps
from usdt_agent.strategies.cross_venue import CrossVenueArbStrategy
from usdt_agent.strategies.funding_carry import FundingCarryStrategy
from usdt_agent.strategies.grid import GridStrategy
from usdt_agent.strategies.stable_yield import StableYieldStrategy
from usdt_agent.strategies.triangular import TriangularArbStrategy


def snapshot_of(quotes, funding=(), pools=(), ts=None) -> MarketSnapshot:
    return MarketSnapshot(
        ts=ts if ts is not None else time.time(),
        quotes={(q.venue, q.symbol): q for q in quotes},
        funding={(f.venue, f.symbol): f for f in funding},
        pools=tuple(pools),
    )


def spread_quote(venue: str, symbol: str, mid: float, spread_bps: float = 2.0,
                 depth_usdt: float = 500_000.0) -> Quote:
    half = mid * spread_bps * BPS / 2.0
    size = depth_usdt / mid
    return Quote(venue, symbol, mid - half, mid + half, size, size)


class TestFeeds(unittest.TestCase):
    def test_symbol_conversions(self) -> None:
        self.assertEqual(to_venue_symbol("BTC/USDT"), "BTCUSDT")
        self.assertEqual(to_venue_symbol("BTC/USDT", "-"), "BTC-USDT")
        self.assertEqual(from_venue_symbol("BTCUSDT"), "BTC/USDT")
        self.assertEqual(from_venue_symbol("BTC-USDT"), "BTC/USDT")
        self.assertIsNone(from_venue_symbol("BTCEUR"))
        self.assertIsNone(from_venue_symbol("USDT"))

    def test_synthetic_is_deterministic(self) -> None:
        symbols = ("BTC/USDT", "ETH/USDT", "ETH/BTC")
        a = SyntheticFeed(seed=11).fetch(symbols)
        b = SyntheticFeed(seed=11).fetch(symbols)
        self.assertEqual([q.bid for q in a[0]], [q.bid for q in b[0]])
        c = SyntheticFeed(seed=12).fetch(symbols)
        self.assertNotEqual([q.bid for q in a[0]], [q.bid for q in c[0]])

    def test_synthetic_quotes_are_sane(self) -> None:
        quotes, funding, pools = SyntheticFeed(seed=3).fetch(("BTC/USDT", "USDC/USDT", "ETH/BTC"))
        self.assertTrue(quotes and pools)
        for q in quotes:
            self.assertGreater(q.bid, 0)
            self.assertGreater(q.ask, q.bid)
            self.assertGreater(q.spread_bps, 0)
        # A pegged pair must stay pegged.
        for q in quotes:
            if q.symbol == "USDC/USDT":
                self.assertLess(abs(q.mid - 1.0), 0.05)
        # Crosses carry no funding; USDT perps do.
        self.assertTrue(all(f.symbol != "ETH/BTC" for f in funding))

    def test_synthetic_cross_is_near_arbitrage_free(self) -> None:
        feed = SyntheticFeed(seed=5, venues=("binance",))
        feed.advance(50)
        quotes, _, _ = feed.fetch(("BTC/USDT", "ETH/USDT", "ETH/BTC"))
        book = {q.symbol: q.mid for q in quotes}
        implied = book["ETH/USDT"] / book["BTC/USDT"]
        # The listed cross must track the implied ratio to within a few dozen bps.
        self.assertLess(abs(book["ETH/BTC"] / implied - 1.0) / BPS, 200.0)

    def test_composite_survives_a_broken_feed(self) -> None:
        class Exploding(Feed):
            name = "boom"

            def fetch(self, symbols):  # type: ignore[no-untyped-def]
                raise RuntimeError("venue on fire")

        composite = CompositeFeed([Exploding(), SyntheticFeed(seed=1)])
        snap = composite.snapshot(("BTC/USDT",))
        self.assertTrue(snap.quotes)
        self.assertTrue(any("boom" in e for e in snap.errors))

    def test_composite_clock_follows_the_data(self) -> None:
        feed = SyntheticFeed(seed=1, step_s=3600.0)
        composite = CompositeFeed([feed])
        first = composite.snapshot(("BTC/USDT",))
        second = composite.snapshot(("BTC/USDT",))
        self.assertAlmostEqual(second.ts - first.ts, 3600.0, places=3)

    def test_empty_composite(self) -> None:
        snap = CompositeFeed([]).snapshot(("BTC/USDT",))
        self.assertTrue(snap.is_empty)
        self.assertTrue(snap.errors)


class TestPaperBroker(unittest.TestCase):
    def setUp(self) -> None:
        self.cfg = ExecutionConfig(reject_probability=0.0)
        self.broker = PaperBroker(self.cfg, seed=1)
        self.quote = spread_quote("binance", "BTC/USDT", 100.0, spread_bps=4.0)
        self.snap = snapshot_of([self.quote])

    def test_buy_pays_above_mid_and_sell_below(self) -> None:
        buy = self.broker.execute((Order("binance", "BTC/USDT", Side.BUY, 1000.0),), self.snap)[0]
        sell = self.broker.execute((Order("binance", "BTC/USDT", Side.SELL, 1000.0),), self.snap)[0]
        self.assertGreater(buy.price, self.quote.mid)
        self.assertLess(sell.price, self.quote.mid)
        self.assertGreater(buy.fee_usdt, 0)
        self.assertGreater(buy.slippage_usdt, 0)

    def test_fee_matches_configured_rate(self) -> None:
        fill = self.broker.execute((Order("binance", "BTC/USDT", Side.BUY, 1000.0),), self.snap)[0]
        self.assertAlmostEqual(fill.fee_usdt, 1000.0 * self.cfg.taker_fee_bps * BPS, places=9)

    def test_maker_is_cheaper_than_taker(self) -> None:
        taker = self.broker.execute((Order("binance", "BTC/USDT", Side.BUY, 1000.0),), self.snap)[0]
        maker = self.broker.execute(
            (Order("binance", "BTC/USDT", Side.BUY, 1000.0, post_only=True),), self.snap
        )[0]
        self.assertLess(maker.fee_usdt, taker.fee_usdt)

    def test_slippage_grows_with_size(self) -> None:
        small = self.broker.execute((Order("binance", "BTC/USDT", Side.BUY, 100.0),), self.snap)[0]
        big = self.broker.execute((Order("binance", "BTC/USDT", Side.BUY, 400_000.0),), self.snap)[0]
        self.assertGreater(big.slippage_usdt / big.notional, small.slippage_usdt / small.notional)

    def test_missing_quote_is_rejected_not_crashed(self) -> None:
        fill = self.broker.execute((Order("kraken", "BTC/USDT", Side.BUY, 100.0),), self.snap)[0]
        self.assertFalse(fill.ok)
        self.assertIn("no quote", fill.reason)
        self.assertFalse(self.broker.all_ok([fill]))

    def test_zero_notional_rejected(self) -> None:
        fill = self.broker.execute((Order("binance", "BTC/USDT", Side.BUY, 0.0),), self.snap)[0]
        self.assertFalse(fill.ok)

    def test_pool_deposit_has_no_spread(self) -> None:
        order = Order("aave@Ethereum", "USDT", Side.BUY, 1000.0, Instrument.POOL)
        fill = self.broker.execute((order,), self.snap)[0]
        self.assertTrue(fill.ok)
        self.assertEqual(fill.slippage_usdt, 0.0)

    def test_rejections_are_reported(self) -> None:
        broker = PaperBroker(ExecutionConfig(reject_probability=1.0), seed=1)
        fill = broker.execute((Order("binance", "BTC/USDT", Side.BUY, 100.0),), self.snap)[0]
        self.assertFalse(fill.ok)
        self.assertEqual(broker.stats()["reject_rate"], 1.0)

    def test_one_fill_per_order(self) -> None:
        orders = tuple(Order("binance", "BTC/USDT", Side.BUY, 100.0) for _ in range(5))
        self.assertEqual(len(self.broker.execute(orders, self.snap)), 5)


class TestCostModel(unittest.TestCase):
    def test_cost_rises_without_a_quote(self) -> None:
        cfg = ExecutionConfig()
        q = spread_quote("binance", "BTC/USDT", 100.0, 2.0)
        self.assertLess(leg_cost_bps(cfg, q), leg_cost_bps(cfg, None))

    def test_round_trip_is_twice_one_way(self) -> None:
        cfg = ExecutionConfig()
        q = spread_quote("binance", "BTC/USDT", 100.0, 2.0)
        self.assertAlmostEqual(round_trip_cost_bps(cfg, [q]), 2 * leg_cost_bps(cfg, q), places=9)


class TestStrategies(unittest.TestCase):
    def setUp(self) -> None:
        self.cfg = load_config()

    def test_cross_venue_finds_a_real_dislocation(self) -> None:
        s = CrossVenueArbStrategy(self.cfg)
        snap = snapshot_of([
            spread_quote("binance", "BTC/USDT", 100.0, 1.0),
            spread_quote("bybit", "BTC/USDT", 100.6, 1.0),  # 60 bps rich
        ])
        opps = s.scan(snap)
        self.assertTrue(opps)
        o = opps[0]
        self.assertEqual(o.strategy, "cross_venue")
        self.assertEqual({leg.venue for leg in o.legs}, {"binance", "bybit"})
        buy = next(leg for leg in o.legs if leg.side is Side.BUY)
        self.assertEqual(buy.venue, "binance")  # buys the cheap venue
        self.assertGreater(o.edge_bps, 0)
        self.assertLess(o.edge_bps, 60.0)  # costs must have been subtracted

    def test_cross_venue_ignores_a_dislocation_smaller_than_costs(self) -> None:
        s = CrossVenueArbStrategy(self.cfg)
        snap = snapshot_of([
            spread_quote("binance", "BTC/USDT", 100.0, 1.0),
            spread_quote("bybit", "BTC/USDT", 100.01, 1.0),  # 1 bp
        ])
        self.assertEqual(s.scan(snap), [])

    def test_cross_venue_books_the_spread_once(self) -> None:
        s = CrossVenueArbStrategy(self.cfg)
        snap = snapshot_of([
            spread_quote("binance", "BTC/USDT", 100.0, 1.0),
            spread_quote("bybit", "BTC/USDT", 100.6, 1.0),
        ])
        opp = s.scan(snap)[0]
        trade = Trade("cross_venue", opp.label, 1000.0, snap.ts, meta=dict(opp.meta))
        first = s.mark(trade, snap, 1.0)
        trade.accrued = first
        self.assertGreater(first, 0.0)
        self.assertAlmostEqual(s.mark(trade, snap, 1.0), first)  # never re-marked
        self.assertTrue(s.should_close(trade, snap))

    def test_funding_carry_requires_carry_to_beat_costs(self) -> None:
        s = FundingCarryStrategy(self.cfg)
        quotes = [spread_quote("binance", "BTC/USDT", 100.0, 1.0)]
        rich = snapshot_of(quotes, [FundingRate("binance", "BTC/USDT", 0.0009, 8.0, 100.0)])
        poor = snapshot_of(quotes, [FundingRate("binance", "BTC/USDT", 0.000002, 8.0, 100.0)])
        self.assertTrue(s.scan(rich))
        self.assertEqual(s.scan(poor), [])

    def test_funding_carry_skips_negative_funding(self) -> None:
        s = FundingCarryStrategy(self.cfg)
        snap = snapshot_of(
            [spread_quote("binance", "BTC/USDT", 100.0, 1.0)],
            [FundingRate("binance", "BTC/USDT", -0.0009, 8.0, 100.0)],
        )
        self.assertEqual(s.scan(snap), [])

    def test_funding_carry_is_delta_neutral(self) -> None:
        s = FundingCarryStrategy(self.cfg)
        snap = snapshot_of(
            [spread_quote("binance", "BTC/USDT", 100.0, 1.0)],
            [FundingRate("binance", "BTC/USDT", 0.0012, 8.0, 100.0)],
        )
        legs = s.scan(snap)[0].legs
        self.assertEqual(len(legs), 2)
        self.assertEqual({leg.side for leg in legs}, {Side.BUY, Side.SELL})
        self.assertEqual({leg.instrument for leg in legs}, {Instrument.SPOT, Instrument.PERP})
        self.assertAlmostEqual(legs[0].notional, legs[1].notional)

    def test_funding_carry_accrues_over_time(self) -> None:
        s = FundingCarryStrategy(self.cfg)
        fr = FundingRate("binance", "BTC/USDT", 0.0012, 8.0, 100.0)
        snap = snapshot_of([spread_quote("binance", "BTC/USDT", 100.0, 1.0)], [fr])
        opp = s.scan(snap)[0]
        trade = Trade("funding_carry", opp.label, 1000.0, snap.ts, meta=dict(opp.meta))
        trade.accrued = s.mark(trade, snap, 8 * 3600.0)
        self.assertAlmostEqual(trade.accrued, 1000.0 * 0.0012, places=6)

    def test_funding_carry_tolerates_a_single_negative_print(self) -> None:
        s = FundingCarryStrategy(self.cfg)
        good = FundingRate("binance", "BTC/USDT", 0.0012, 8.0, 100.0)
        snap = snapshot_of([spread_quote("binance", "BTC/USDT", 100.0, 1.0)], [good])
        opp = s.scan(snap)[0]
        trade = Trade("funding_carry", opp.label, 1000.0, snap.ts,
                      horizon_s=opp.horizon_s, meta=dict(opp.meta))
        bad = snapshot_of(
            [spread_quote("binance", "BTC/USDT", 100.0, 1.0)],
            [FundingRate("binance", "BTC/USDT", -0.0002, 8.0, 100.0)],
            ts=snap.ts,
        )
        self.assertFalse(s.should_close(trade, bad))  # one print is noise
        for _ in range(int(s.params["negative_streak_exit"])):
            last = s.should_close(trade, bad)
        self.assertTrue(last)
        self.assertIn("negative", last.reason)

    def test_triangular_needs_a_listed_cross(self) -> None:
        s = TriangularArbStrategy(self.cfg)
        no_cross = snapshot_of([
            spread_quote("binance", "BTC/USDT", 100.0, 1.0),
            spread_quote("binance", "ETH/USDT", 10.0, 1.0),
        ])
        self.assertEqual(s.scan(no_cross), [])

    def test_triangular_finds_a_dislocated_loop(self) -> None:
        s = TriangularArbStrategy(self.cfg)
        # Implied ETH/BTC = 10/100 = 0.1; list it 1 % rich.
        snap = snapshot_of([
            spread_quote("binance", "BTC/USDT", 100.0, 0.5),
            spread_quote("binance", "ETH/USDT", 10.0, 0.5),
            spread_quote("binance", "ETH/BTC", 0.101, 0.5),
        ])
        opps = s.scan(snap)
        self.assertTrue(opps)
        self.assertEqual(len(opps[0].legs), 3)
        self.assertTrue(all(leg.venue == "binance" for leg in opps[0].legs))
        self.assertEqual(s.close_orders(Trade("triangular", "l", 1.0, snap.ts), snap), ())

    def test_triangular_ignores_a_consistent_triangle(self) -> None:
        s = TriangularArbStrategy(self.cfg)
        snap = snapshot_of([
            spread_quote("binance", "BTC/USDT", 100.0, 0.5),
            spread_quote("binance", "ETH/USDT", 10.0, 0.5),
            spread_quote("binance", "ETH/BTC", 0.1, 0.5),
        ])
        self.assertEqual(s.scan(snap), [])

    def test_stable_yield_ranks_by_risk_adjusted_apy(self) -> None:
        s = StableYieldStrategy(self.cfg)
        pools = [
            YieldPool("aave-v3", "Ethereum", "USDT", 0.06, 0.0, 900e6, pool_id="safe"),
            YieldPool("degen", "Base", "USDT", 0.02, 0.20, 6e6, pool_id="farm"),
        ]
        opps = s.scan(snapshot_of([], pools=pools))
        self.assertTrue(opps)
        self.assertEqual(opps[0].meta["pool_id"], "safe")
        self.assertEqual(opps[0].legs[0].instrument, Instrument.POOL)

    def test_stable_yield_accrues_interest(self) -> None:
        s = StableYieldStrategy(self.cfg)
        pool = YieldPool("aave-v3", "Ethereum", "USDT", 0.10, 0.0, 900e6, pool_id="p")
        snap = snapshot_of([], pools=[pool])
        trade = Trade("stable_yield", "l", 1000.0, snap.ts, meta={"pool_id": "p", "apy": 0.10})
        year = 365.0 * 24 * 3600
        self.assertAlmostEqual(s.mark(trade, snap, year), 100.0, places=6)

    def test_stable_yield_exits_on_apy_collapse(self) -> None:
        s = StableYieldStrategy(self.cfg)
        snap = snapshot_of([], pools=[YieldPool("a", "E", "USDT", 0.01, 0.0, 900e6, pool_id="p")])
        trade = Trade("stable_yield", "l", 1000.0, snap.ts,
                      horizon_s=1e9, meta={"pool_id": "p", "apy": 0.10})
        sig = s.should_close(trade, snap)
        self.assertTrue(sig)
        self.assertIn("APY collapsed", sig.reason)

    def test_grid_buys_dips_only(self) -> None:
        s = GridStrategy(self.cfg)
        for _ in range(20):
            s.scan(snapshot_of([spread_quote("binance", "USDC/USDT", 1.0, 0.5)]))
        rip = s.scan(snapshot_of([spread_quote("binance", "USDC/USDT", 1.006, 0.5)]))
        self.assertEqual(rip, [])
        dip = s.scan(snapshot_of([spread_quote("binance", "USDC/USDT", 0.9955, 0.5)]))
        self.assertTrue(dip)
        self.assertEqual(dip[0].legs[0].side, Side.BUY)

    def test_grid_marks_to_market_and_can_lose(self) -> None:
        s = GridStrategy(self.cfg)
        snap = snapshot_of([spread_quote("binance", "USDC/USDT", 0.99, 0.5)])
        trade = Trade("grid", "l", 1000.0, snap.ts,
                      meta={"venue": "binance", "symbol": "USDC/USDT", "entry_mid": 1.0})
        self.assertAlmostEqual(s.mark(trade, snap, 1.0), -10.0, places=6)
        sig = s.should_close(trade, snap)
        self.assertTrue(sig)
        self.assertIn("stop", sig.reason)

    def test_grid_needs_history_before_trading(self) -> None:
        s = GridStrategy(self.cfg)
        self.assertEqual(s.scan(snapshot_of([spread_quote("binance", "USDC/USDT", 0.99, 0.5)])), [])

    def test_every_strategy_survives_an_empty_snapshot(self) -> None:
        empty = MarketSnapshot(ts=time.time())
        for name, s in build_strategies(self.cfg).items():
            with self.subTest(strategy=name):
                self.assertEqual(s.scan(empty), [])


class TestLiveInterlocks(unittest.TestCase):
    def test_paper_broker_needs_nothing(self) -> None:
        broker = build_broker(ExecutionConfig(), live=False, venues=("binance",))
        self.assertFalse(broker.is_live)

    def test_live_blocked_without_confirmation(self) -> None:
        with mock.patch.dict(os.environ, {}, clear=True):
            problems = live_interlocks(("binance",))
            self.assertTrue(any(CONFIRM_ENV in p for p in problems))
            with self.assertRaises(LiveTradingBlocked):
                build_broker(ExecutionConfig(), live=True, venues=("binance",))

    def test_live_blocked_without_keys(self) -> None:
        with mock.patch.dict(os.environ, {CONFIRM_ENV: CONFIRM_VALUE}, clear=True):
            problems = live_interlocks(("binance",))
            self.assertTrue(any("API_KEY" in p for p in problems))

    def test_all_interlocks_satisfied(self) -> None:
        env = {
            CONFIRM_ENV: CONFIRM_VALUE,
            "BINANCE_API_KEY": "k",
            "BINANCE_API_SECRET": "s",
        }
        with mock.patch.dict(os.environ, env, clear=True):
            self.assertEqual(live_interlocks(("binance",)), [])

    def test_live_broker_refuses_non_spot_and_oversized_orders(self) -> None:
        env = {CONFIRM_ENV: CONFIRM_VALUE, "BINANCE_API_KEY": "k", "BINANCE_API_SECRET": "s"}
        with mock.patch.dict(os.environ, env, clear=True):
            broker = build_broker(
                ExecutionConfig(), live=True, venues=("binance",), max_order_usdt=50.0
            )
            snap = snapshot_of([spread_quote("binance", "BTC/USDT", 100.0)])
            perp = broker.execute((Order("binance", "BTC/USDT", Side.BUY, 10.0, Instrument.PERP),), snap)[0]
            self.assertFalse(perp.ok)
            big = broker.execute((Order("binance", "BTC/USDT", Side.BUY, 500.0),), snap)[0]
            self.assertFalse(big.ok)
            other = broker.execute((Order("okx", "BTC/USDT", Side.BUY, 10.0),), snap)[0]
            self.assertFalse(other.ok)

    def test_live_broker_dry_run_sends_nothing(self) -> None:
        env = {CONFIRM_ENV: CONFIRM_VALUE, "BINANCE_API_KEY": "k", "BINANCE_API_SECRET": "s"}
        with mock.patch.dict(os.environ, env, clear=True):
            broker = build_broker(ExecutionConfig(), live=True, venues=("binance",), dry_run=True)
            snap = snapshot_of([spread_quote("binance", "BTC/USDT", 100.0)])
            with mock.patch("usdt_agent.http.request") as request:
                fill = broker.execute((Order("binance", "BTC/USDT", Side.BUY, 10.0),), snap)[0]
                request.assert_not_called()
            self.assertTrue(fill.ok)
            self.assertEqual(fill.reason, "dry-run")


class TestAgentLoop(unittest.TestCase):
    def build(self, cycles: int = 60, seed: int = 3, equity: float = 1000.0) -> Agent:
        cfg = load_config(
            None, data_source="synthetic", interval_s=0.0001,
            max_cycles=cycles, seed=seed, starting_equity_usdt=equity,
        )
        feed = CompositeFeed([SyntheticFeed(venues=cfg.venues, seed=seed, step_s=900.0)])
        return Agent(
            cfg, feed, PaperBroker(cfg.execution, seed=seed),
            build_strategies(cfg), Ledger(":memory:"), data_source="synthetic",
        )

    def test_accounting_identity_holds(self) -> None:
        """equity == start + realized PnL + (unrealized − entry costs) on open."""
        agent = self.build()
        agent.run()
        closed_pnl = agent.ledger.totals()["realized_pnl"]
        open_component = sum(t.accrued - t.entry_cost for t in agent.open_trades)
        expected = agent.cfg.starting_equity_usdt + closed_pnl + open_component
        self.assertAlmostEqual(agent.equity, expected, places=6)
        agent.ledger.close()

    def test_equity_decomposition(self) -> None:
        agent = self.build()
        agent.run()
        self.assertAlmostEqual(
            agent.equity,
            agent.cash + agent.treasury + agent.deployed + agent.unrealized,
            places=9,
        )
        agent.ledger.close()

    def test_run_is_reproducible(self) -> None:
        a, b = self.build(seed=8), self.build(seed=8)
        a.run()
        b.run()
        self.assertAlmostEqual(a.equity, b.equity, places=9)
        self.assertEqual(a.ledger.totals()["closed_trades"], b.ledger.totals()["closed_trades"])
        a.ledger.close()
        b.ledger.close()

    def test_ledger_stays_intact_after_a_run(self) -> None:
        agent = self.build()
        agent.run()
        ok, msg = agent.ledger.verify()
        self.assertTrue(ok, msg)
        agent.ledger.close()

    def test_liquidate_closes_everything(self) -> None:
        agent = self.build()
        agent.run()
        agent.liquidate()
        self.assertEqual(agent.open_trades, [])
        self.assertAlmostEqual(agent.deployed, 0.0)
        self.assertEqual(len(agent.ledger.open_trades()), 0)
        agent.ledger.close()

    def test_risk_caps_are_never_breached(self) -> None:
        agent = self.build(cycles=120)
        limit = agent.cfg.risk.max_deployed_fraction

        def check(report, ag):  # type: ignore[no-untyped-def]
            self.assertLessEqual(len(ag.open_trades), ag.cfg.risk.max_open_trades)
            self.assertLessEqual(ag.deployed, limit * max(ag.equity, 1e-9) + 1e-6)
            for t in ag.open_trades:
                self.assertLessEqual(t.notional, ag.cfg.risk.max_ticket_usdt + 1e-9)
                self.assertGreaterEqual(t.notional, ag.cfg.risk.min_ticket_usdt - 1e-9)

        agent.run(on_cycle=check)
        agent.ledger.close()

    def test_live_mode_withholds_capital_until_proven(self) -> None:
        agent = self.build(cycles=5)
        agent.cfg.mode = "live"
        agent.allocator.verdicts = {}
        self.assertFalse(agent._eligible("cross_venue"))
        agent.allocator.reassess(seed=1)
        # With no trade history nothing can be proven, so nothing is eligible.
        self.assertFalse(any(agent._eligible(n) for n in agent.strategies))
        agent.ledger.close()

    def test_paper_mode_allows_every_strategy(self) -> None:
        agent = self.build(cycles=1)
        self.assertTrue(all(agent._eligible(n) for n in agent.strategies))
        agent.ledger.close()

    def test_halt_stops_the_loop(self) -> None:
        agent = self.build(cycles=200)
        original = agent.step

        def step_then_halt():  # type: ignore[no-untyped-def]
            report = original()
            if agent.cycle >= 3:
                agent.risk.halt("test halt")
            return report

        agent.step = step_then_halt  # type: ignore[method-assign]
        agent.run()
        self.assertTrue(agent.risk.halted)
        self.assertLessEqual(agent.cycle, 4)
        agent.ledger.close()

    def test_a_failing_strategy_does_not_kill_the_run(self) -> None:
        agent = self.build(cycles=6)

        class Broken:
            def scan(self, snapshot):  # type: ignore[no-untyped-def]
                raise RuntimeError("scanner exploded")

        agent.strategies["broken"] = Broken()  # type: ignore[assignment]
        agent.run()
        self.assertGreater(agent.cycle, 0)
        self.assertTrue(agent.ledger.events(kind="scan_error"))
        agent.ledger.close()

    def test_summary_shape(self) -> None:
        agent = self.build(cycles=20)
        agent.run()
        s = agent.summary()
        for key in ("mode", "equity", "pnl", "risk", "strategies", "verdicts", "posteriors"):
            self.assertIn(key, s)
        agent.ledger.close()


if __name__ == "__main__":
    unittest.main(verbosity=2)
