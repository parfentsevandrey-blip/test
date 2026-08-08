"""Unit tests for the value objects, statistics, ledger, risk and allocator."""

from __future__ import annotations

import random
import sys
import tempfile
import time
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from usdt_agent.allocator import BanditAllocator
from usdt_agent.config import (
    AllocatorConfig,
    ConfigError,
    RiskConfig,
    load_config,
)
from usdt_agent.ledger import Ledger, chain_hash
from usdt_agent.models import (
    BPS,
    FundingRate,
    Instrument,
    Opportunity,
    Order,
    Quote,
    Side,
    Trade,
    TradeStatus,
    YieldPool,
)
from usdt_agent.risk import PortfolioState, RiskGovernor
from usdt_agent.statistics import (
    assess,
    bootstrap_p_value,
    deflated_sharpe,
    max_drawdown,
    norm_cdf,
    norm_ppf,
    sharpe,
)


class TestModels(unittest.TestCase):
    def test_quote_math(self) -> None:
        q = Quote("binance", "BTC/USDT", 100.0, 100.2, 5.0, 4.0)
        self.assertAlmostEqual(q.mid, 100.1)
        self.assertAlmostEqual(q.spread_bps, 0.2 / 100.1 / BPS, places=6)
        self.assertEqual(q.base, "BTC")
        self.assertEqual(q.quote, "USDT")
        self.assertAlmostEqual(q.depth_usdt("buy"), 4.0 * 100.2)
        self.assertAlmostEqual(q.depth_usdt("sell"), 5.0 * 100.0)

    def test_funding_apr(self) -> None:
        fr = FundingRate("binance", "BTC/USDT", 0.0001, 8.0, 68_000.0)
        self.assertAlmostEqual(fr.apr, 0.0001 * (365 * 24 / 8), places=9)

    def test_yield_pool_risk_penalises_reward_heavy_pools(self) -> None:
        safe = YieldPool("aave-v3", "Ethereum", "USDT", 0.05, 0.0, 800e6)
        farmy = YieldPool("degen", "Base", "USDT", 0.02, 0.38, 2e6)
        self.assertLess(safe.risk_score, farmy.risk_score)
        self.assertGreater(safe.risk_adjusted_apy, 0.0)
        self.assertLessEqual(farmy.risk_score, 1.0)

    def test_opportunity_scaling_preserves_leg_ratios(self) -> None:
        opp = Opportunity(
            strategy="s", label="l", edge_bps=10.0, capacity_usdt=1000.0, horizon_s=60.0,
            legs=(
                Order("a", "BTC/USDT", Side.BUY, 100.0),
                Order("b", "BTC/USDT", Side.SELL, 50.0),
            ),
        )
        scaled = opp.scaled_to(400.0)
        self.assertAlmostEqual(scaled[0].notional, 400.0)
        self.assertAlmostEqual(scaled[1].notional, 200.0)
        self.assertEqual(scaled[1].side, Side.SELL)

    def test_opportunity_apr_and_score(self) -> None:
        opp = Opportunity("s", "l", edge_bps=10.0, capacity_usdt=1.0, horizon_s=86_400.0)
        self.assertAlmostEqual(opp.expected_apr, 10.0 * BPS * 365.0, places=6)
        self.assertGreater(opp.score, 0.0)
        self.assertEqual(Opportunity("s", "l", -5.0, 1.0, 60.0).score, 0.0)

    def test_order_flip(self) -> None:
        o = Order("binance", "BTC/USDT", Side.BUY, 100.0, Instrument.PERP)
        f = o.flipped()
        self.assertEqual(f.side, Side.SELL)
        self.assertTrue(f.reduce_only)
        self.assertEqual(f.instrument, Instrument.PERP)

    def test_trade_pnl_identity(self) -> None:
        t = Trade("s", "l", notional=1000.0, opened_ts=time.time() - 10)
        t.accrued, t.entry_cost, t.exit_cost = 5.0, 1.0, 2.0
        self.assertAlmostEqual(t.realized_pnl, 2.0)
        self.assertAlmostEqual(t.return_bps, 2.0 / 1000.0 / BPS)


class TestStatistics(unittest.TestCase):
    def test_norm_roundtrip(self) -> None:
        for p in (0.01, 0.1, 0.5, 0.9, 0.975, 0.999):
            self.assertAlmostEqual(norm_cdf(norm_ppf(p)), p, places=6)
        self.assertAlmostEqual(norm_ppf(0.975), 1.959964, places=4)

    def test_bootstrap_rejects_noise(self) -> None:
        rng = random.Random(1)
        noise = [rng.gauss(0.0, 10.0) for _ in range(200)]
        self.assertGreater(bootstrap_p_value(noise, n_resamples=500, seed=1), 0.05)

    def test_bootstrap_detects_real_edge(self) -> None:
        rng = random.Random(2)
        edge = [rng.gauss(6.0, 3.0) for _ in range(200)]
        self.assertLess(bootstrap_p_value(edge, n_resamples=500, seed=2), 0.05)

    def test_short_series_is_never_proven(self) -> None:
        self.assertEqual(bootstrap_p_value([5.0, 6.0, 7.0]), 1.0)
        v = assess("s", [5.0] * 5, min_trades=30)
        self.assertFalse(v.proven)
        self.assertEqual(v.label, "insufficient")

    def test_multiple_testing_haircut_lowers_confidence(self) -> None:
        rng = random.Random(3)
        returns = [rng.gauss(2.0, 6.0) for _ in range(120)]
        alone = deflated_sharpe(returns, n_trials=1)
        searched = deflated_sharpe(returns, n_trials=200)
        self.assertGreaterEqual(alone, searched)

    def test_assess_gate(self) -> None:
        rng = random.Random(4)
        good = [rng.gauss(8.0, 4.0) for _ in range(120)]
        bad = [rng.gauss(-1.0, 8.0) for _ in range(120)]
        self.assertTrue(assess("g", good, min_trades=30, seed=4).proven)
        self.assertFalse(assess("b", bad, min_trades=30, seed=4).proven)

    def test_sharpe_and_drawdown(self) -> None:
        self.assertEqual(sharpe([1.0]), 0.0)
        self.assertEqual(sharpe([2.0, 2.0, 2.0]), 0.0)  # zero variance
        self.assertAlmostEqual(max_drawdown([100, 120, 90, 130]), 0.25)
        self.assertEqual(max_drawdown([]), 0.0)


class TestLedger(unittest.TestCase):
    def setUp(self) -> None:
        self.ledger = Ledger(":memory:")

    def tearDown(self) -> None:
        self.ledger.close()

    def test_chain_verifies(self) -> None:
        for i in range(5):
            self.ledger.append("test", {"i": i})
        ok, msg = self.ledger.verify()
        self.assertTrue(ok, msg)
        self.assertIn("5 entries", msg)

    def test_chain_detects_tampering(self) -> None:
        for i in range(4):
            self.ledger.append("test", {"i": i})
        self.ledger.conn.execute("UPDATE journal SET payload = ? WHERE seq = 2", ('{"i":999}',))
        self.ledger.conn.commit()
        ok, msg = self.ledger.verify()
        self.assertFalse(ok)
        self.assertIn("modified", msg)

    def test_chain_detects_deletion(self) -> None:
        for i in range(4):
            self.ledger.append("test", {"i": i})
        self.ledger.conn.execute("DELETE FROM journal WHERE seq = 2")
        self.ledger.conn.commit()
        ok, _ = self.ledger.verify()
        self.assertFalse(ok)

    def test_hash_is_order_dependent(self) -> None:
        a = chain_hash("0" * 64, "k", 1.0, {"x": 1})
        b = chain_hash("1" * 64, "k", 1.0, {"x": 1})
        self.assertNotEqual(a, b)

    def test_trade_roundtrip_and_stats(self) -> None:
        t = Trade("cross_venue", "BTC arb", 1000.0, time.time() - 60)
        self.ledger.record_trade(t)
        self.assertEqual(len(self.ledger.open_trades()), 1)

        t.accrued, t.entry_cost, t.exit_cost = 3.0, 1.0, 1.0
        t.status, t.closed_ts = TradeStatus.CLOSED, time.time()
        self.ledger.record_trade(t)
        self.assertEqual(len(self.ledger.open_trades()), 0)

        stats = self.ledger.strategy_stats()
        self.assertAlmostEqual(stats["cross_venue"]["pnl"], 1.0)
        self.assertEqual(stats["cross_venue"]["win_rate"], 1.0)
        self.assertEqual(self.ledger.closed_returns("cross_venue"), [t.return_bps])

    def test_state_kv(self) -> None:
        self.ledger.set_state("k", {"a": 1})
        self.assertEqual(self.ledger.get_state("k"), {"a": 1})
        self.assertIsNone(self.ledger.get_state("missing"))

    def test_persists_to_disk(self) -> None:
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "sub" / "x.db"
            led = Ledger(path)
            led.append("e", {"v": 1})
            led.close()
            again = Ledger(path)
            self.assertTrue(again.verify()[0])
            self.assertEqual(len(again.events()), 1)
            again.close()


class TestRiskGovernor(unittest.TestCase):
    def setUp(self) -> None:
        self.cfg = RiskConfig()
        self.gov = RiskGovernor(self.cfg, 1000.0)
        self.opp = Opportunity(
            "s", "l", edge_bps=20.0, capacity_usdt=10_000.0, horizon_s=60.0,
            legs=(Order("binance", "BTC/USDT", Side.BUY, 100.0),), venues=("binance",),
        )

    def state(self, trades: list[Trade] | None = None, equity: float = 1000.0) -> PortfolioState:
        return PortfolioState(equity=equity, open_trades=trades or [])

    def test_approves_within_limits(self) -> None:
        d = self.gov.approve(self.opp, 200.0, self.state())
        self.assertTrue(d.approved)
        self.assertAlmostEqual(d.notional, 200.0)

    def test_caps_by_strategy_fraction(self) -> None:
        d = self.gov.approve(self.opp, 900.0, self.state())
        self.assertTrue(d.approved)
        self.assertLessEqual(d.notional, self.cfg.max_strategy_fraction * 1000.0 + 1e-9)

    def test_caps_by_venue_fraction(self) -> None:
        self.cfg.max_venue_fraction = 0.10
        d = self.gov.approve(self.opp, 900.0, self.state())
        self.assertLessEqual(d.notional, 100.0 + 1e-9)

    def test_refuses_thin_edge(self) -> None:
        thin = Opportunity("s", "l", edge_bps=0.1, capacity_usdt=1e6, horizon_s=60.0)
        self.assertFalse(self.gov.approve(thin, 200.0, self.state()))

    def test_refuses_stale_data(self) -> None:
        d = self.gov.approve(self.opp, 200.0, self.state(), data_age_s=10_000.0)
        self.assertFalse(d.approved)
        self.assertIn("stale", d.reason)

    def test_refuses_when_too_many_open(self) -> None:
        trades = [Trade("s", "l", 1.0, time.time()) for _ in range(self.cfg.max_open_trades)]
        self.assertFalse(self.gov.approve(self.opp, 200.0, self.state(trades)))

    def test_drawdown_kill_switch(self) -> None:
        self.gov.mark_equity(1000.0)
        self.gov.mark_equity(1000.0 * (1 - self.cfg.max_drawdown_fraction - 0.01))
        self.assertTrue(self.gov.halted)
        self.assertFalse(self.gov.approve(self.opp, 100.0, self.state()))
        self.gov.resume()
        self.assertFalse(self.gov.halted)

    def test_daily_loss_limit_then_new_day(self) -> None:
        now = time.time()
        self.gov.mark_equity(1000.0, now)
        self.gov.mark_equity(1000.0 * (1 - self.cfg.daily_loss_limit_fraction - 0.001), now)
        self.assertTrue(self.gov.day_halted)
        self.assertFalse(self.gov.halted)  # a day-stop is not the kill switch
        self.gov.mark_equity(980.0, now + 86_400 * 2)
        self.assertFalse(self.gov.day_halted)

    def test_cooldown(self) -> None:
        now = time.time()
        self.gov.penalize("s", now)
        self.assertTrue(self.gov.in_cooldown("s", now + 1))
        self.assertFalse(self.gov.in_cooldown("s", now + self.cfg.cooldown_after_loss_s + 1))
        d = self.gov.approve(self.opp, 200.0, self.state(), now=now + 1)
        self.assertFalse(d.approved)

    def test_ticket_floor(self) -> None:
        d = self.gov.approve(self.opp, 1.0, self.state())
        self.assertFalse(d.approved)
        self.assertIn("too small", d.reason)

    def test_portfolio_state_exposure(self) -> None:
        t = Trade("s", "l", 500.0, time.time())
        st = PortfolioState(equity=1000.0, open_trades=[t])
        self.assertAlmostEqual(st.deployed, 500.0)
        self.assertAlmostEqual(st.free, 500.0)
        self.assertAlmostEqual(st.deployed_by_strategy("s"), 500.0)


class TestAllocator(unittest.TestCase):
    def setUp(self) -> None:
        self.cfg = AllocatorConfig()
        self.names = ["a", "b", "c"]
        self.alloc = BanditAllocator(self.cfg, self.names, seed=5)

    def test_weights_normalise(self) -> None:
        w = self.alloc.weights()
        self.assertAlmostEqual(sum(w.values()), 1.0, places=9)
        self.assertEqual(set(w), set(self.names))
        self.assertTrue(all(v >= 0 for v in w.values()))

    def test_learns_the_better_arm(self) -> None:
        for _ in range(120):
            self.alloc.observe("a", 12.0)
            self.alloc.observe("b", -8.0)
        self.assertGreater(self.alloc.arms["a"].mean, self.alloc.arms["b"].mean)
        shares = [self.alloc.weights() for _ in range(60)]
        avg_a = sum(s["a"] for s in shares) / len(shares)
        avg_b = sum(s["b"] for s in shares) / len(shares)
        self.assertGreater(avg_a, avg_b)

    def test_exploration_floor_keeps_every_arm_alive(self) -> None:
        for _ in range(200):
            self.alloc.observe("a", 30.0)
            self.alloc.observe("b", -30.0)
        w = self.alloc.weights()
        self.assertGreater(w["b"], 0.0)

    def test_memory_decays(self) -> None:
        for _ in range(60):
            self.alloc.observe("c", 25.0)
        peak = self.alloc.arms["c"].mean
        for _ in range(60):
            self.alloc.observe("c", -25.0)
        self.assertLess(self.alloc.arms["c"].mean, peak)

    def test_budget_splits_capital(self) -> None:
        budgets = self.alloc.budget(1000.0)
        self.assertAlmostEqual(sum(budgets.values()), 1000.0, places=6)

    def test_warm_start_and_reassess(self) -> None:
        rng = random.Random(9)
        self.alloc.warm_start({"a": [rng.gauss(9.0, 3.0) for _ in range(80)]})
        verdicts = self.alloc.reassess(seed=9)
        self.assertEqual(set(verdicts), set(self.names))
        self.assertTrue(verdicts["a"].proven)
        self.assertFalse(verdicts["b"].proven)
        self.assertIn("a", self.alloc.proven())

    def test_static_weight_zero_is_respected(self) -> None:
        w = self.alloc.weights({"a": 0.0, "b": 1.0, "c": 1.0})
        self.assertLess(w["a"], w["b"] + w["c"])

    def test_unknown_arm_observation_is_ignored(self) -> None:
        self.alloc.observe("nope", 10.0)  # must not raise
        self.assertNotIn("nope", self.alloc.arms)


class TestConfig(unittest.TestCase):
    def test_defaults_validate(self) -> None:
        cfg = load_config()
        cfg.validate()
        self.assertEqual(cfg.mode, "paper")
        self.assertFalse(cfg.is_live)
        self.assertIn("funding_carry", cfg.enabled_strategies())

    def test_overrides(self) -> None:
        cfg = load_config(None, starting_equity_usdt=5000.0, interval_s=5.0)
        self.assertAlmostEqual(cfg.starting_equity_usdt, 5000.0)
        self.assertAlmostEqual(cfg.interval_s, 5.0)

    def test_toml_file(self) -> None:
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / "c.toml"
            p.write_text(
                'mode = "paper"\n'
                "starting_equity_usdt = 250.0\n"
                'venues = ["binance"]\n'
                "[risk]\nmax_open_trades = 3\n"
                "[strategies.grid]\nenabled = false\n"
                "[strategies.grid.params]\nband_bps = 40.0\n",
                encoding="utf-8",
            )
            cfg = load_config(p)
            self.assertAlmostEqual(cfg.starting_equity_usdt, 250.0)
            self.assertEqual(cfg.venues, ("binance",))
            self.assertEqual(cfg.risk.max_open_trades, 3)
            self.assertFalse(cfg.strategy("grid").enabled)
            self.assertAlmostEqual(cfg.strategy("grid").params["band_bps"], 40.0)
            self.assertNotIn("grid", cfg.enabled_strategies())

    def test_bad_values_rejected(self) -> None:
        with self.assertRaises(ConfigError):
            load_config(None, mode="turbo")
        with self.assertRaises(ConfigError):
            load_config(None, starting_equity_usdt=-1.0)
        with self.assertRaises(ConfigError):
            load_config(None, interval_s=0.0)

    def test_unknown_key_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / "c.toml"
            p.write_text("nonsense = 1\n", encoding="utf-8")
            with self.assertRaises(ConfigError):
                load_config(p)

    def test_missing_file_rejected(self) -> None:
        with self.assertRaises(ConfigError):
            load_config("/nonexistent/path/agent.toml")


if __name__ == "__main__":
    unittest.main(verbosity=2)
