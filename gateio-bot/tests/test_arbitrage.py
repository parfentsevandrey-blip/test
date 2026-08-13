from __future__ import annotations

from decimal import Decimal

import pytest

from gatebot.arbitrage.executor import TriangleExecutor
from gatebot.arbitrage.scanner import TriangleScanner
from gatebot.arbitrage.triangle import (
    Leg,
    Triangle,
    best_size,
    check_minimums,
    evaluate,
    find_triangles,
    make_leg,
)
from gatebot.exchange.base import Exchange, ExchangeError
from gatebot.types import (
    Balance,
    BookLevel,
    Order,
    OrderBook,
    OrderStatus,
    OrderType,
    PairSpec,
    Side,
    Ticker,
)


def spec_for(symbol: str, base: str, quote: str, **kw) -> PairSpec:
    return PairSpec(
        symbol=symbol,
        base=base,
        quote=quote,
        amount_precision=kw.get("amount_precision", 6),
        price_precision=kw.get("price_precision", 2),
        min_base_amount=Decimal(str(kw.get("min_base", 0))),
        min_quote_amount=Decimal(str(kw.get("min_quote", 0))),
    )


PAIRS = [
    spec_for("BTC_USDT", "BTC", "USDT"),
    spec_for("ETH_USDT", "ETH", "USDT"),
    spec_for("ETH_BTC", "ETH", "BTC", price_precision=6),
    spec_for("DOGE_USDT", "DOGE", "USDT"),
    spec_for("SOL_USDT", "SOL", "USDT"),  # без промежуточной пары — цикла не даст
]
SPECS = {p.symbol: p for p in PAIRS}


def book(symbol: str, asks, bids) -> OrderBook:
    return OrderBook(
        symbol=symbol,
        asks=[BookLevel(Decimal(str(p)), Decimal(str(a))) for p, a in asks],
        bids=[BookLevel(Decimal(str(p)), Decimal(str(a))) for p, a in bids],
    )


# ------------------------------------------------------------------- стакан


def test_buy_with_walks_single_level():
    b = book("X", [(100, 10)], [])
    got, spent = b.buy_with(Decimal(500))
    assert got == Decimal(5)
    assert spent == Decimal(500)


def test_buy_with_walks_multiple_levels():
    b = book("X", [(100, 1), (110, 10)], [])
    got, spent = b.buy_with(Decimal(210))  # 100 на первом уровне + 110 на втором
    assert got == Decimal(2)
    assert spent == Decimal(210)


def test_buy_with_reports_insufficient_depth():
    b = book("X", [(100, 1)], [])
    got, spent = b.buy_with(Decimal(500))
    assert got == Decimal(1)
    assert spent == Decimal(100)  # потрачено меньше запрошенного


def test_sell_amount_walks_bids():
    b = book("X", [], [(100, 1), (90, 10)])
    got, sold = b.sell_amount(Decimal(2))
    assert got == Decimal(190)  # 1 по 100 + 1 по 90
    assert sold == Decimal(2)


def test_sell_amount_reports_insufficient_depth():
    b = book("X", [], [(100, 1)])
    got, sold = b.sell_amount(Decimal(5))
    assert got == Decimal(100)
    assert sold == Decimal(1)


def test_best_prices():
    b = book("X", [(101, 1)], [(99, 1)])
    assert b.best_ask == Decimal(101)
    assert b.best_bid == Decimal(99)
    assert OrderBook("X").best_ask is None


# -------------------------------------------------------------- построение


def test_make_leg_detects_direction():
    pair = spec_for("BTC_USDT", "BTC", "USDT")
    assert make_leg(pair, "USDT", "BTC").side is Side.BUY
    assert make_leg(pair, "BTC", "USDT").side is Side.SELL
    assert make_leg(pair, "ETH", "USDT") is None


def test_find_triangles_builds_both_directions():
    triangles = find_triangles(PAIRS, base="USDT", allowed_middle=None)
    paths = {t.path for t in triangles}
    assert "USDT -> BTC -> ETH -> USDT" in paths
    assert "USDT -> ETH -> BTC -> USDT" in paths


def test_find_triangles_skips_currencies_without_middle_pair():
    """SOL торгуется только к USDT — замкнуть цикл через него нельзя."""
    triangles = find_triangles(PAIRS, base="USDT", allowed_middle=None)
    assert all("SOL" not in t.path for t in triangles)


def test_find_triangles_respects_allowed_middle():
    pairs = PAIRS + [spec_for("DOGE_ETH", "DOGE", "ETH")]
    only_btc = find_triangles(pairs, base="USDT", allowed_middle=["BTC"])
    assert all("DOGE" not in t.path or "BTC" in t.path for t in only_btc)
    with_eth = find_triangles(pairs, base="USDT", allowed_middle=["ETH"])
    assert any("DOGE" in t.path for t in with_eth)


def test_find_triangles_deduplicates():
    triangles = find_triangles(PAIRS, base="USDT", allowed_middle=None)
    assert len({t.key for t in triangles}) == len(triangles)


def test_triangle_legs_chain_correctly():
    triangle = find_triangles(PAIRS, base="USDT", allowed_middle=None)[0]
    assert triangle.legs[0].from_cur == "USDT"
    assert triangle.legs[-1].to_cur == "USDT"
    for a, b in zip(triangle.legs, triangle.legs[1:]):
        assert a.to_cur == b.from_cur


# ------------------------------------------------------------------ расчёт


def _triangle(path: str) -> Triangle:
    for t in find_triangles(PAIRS, base="USDT", allowed_middle=None):
        if t.path == path:
            return t
    raise AssertionError(f"нет цикла {path}")


def test_evaluate_breaks_even_on_consistent_prices():
    """Идеально согласованные курсы без комиссии дают ровно ноль."""
    books = {
        "BTC_USDT": book("BTC_USDT", [(100, 1000)], [(100, 1000)]),
        "ETH_BTC": book("ETH_BTC", [("0.1", 10000)], [("0.1", 10000)]),
        "ETH_USDT": book("ETH_USDT", [(10, 10000)], [(10, 10000)]),
    }
    result = evaluate(_triangle("USDT -> BTC -> ETH -> USDT"), books, Decimal(100), Decimal(0))
    assert result is not None
    assert abs(result.profit_pct) < Decimal("0.000001")


def test_evaluate_detects_real_mispricing():
    # ETH стоит 10 USDT напрямую, но через BTC выходит 0.1*100 = 10 -> поднимаем
    # цену продажи ETH до 11, появляется расхождение.
    books = {
        "BTC_USDT": book("BTC_USDT", [(100, 1000)], [(100, 1000)]),
        "ETH_BTC": book("ETH_BTC", [("0.1", 10000)], [("0.1", 10000)]),
        "ETH_USDT": book("ETH_USDT", [(11, 10000)], [(11, 10000)]),
    }
    result = evaluate(_triangle("USDT -> BTC -> ETH -> USDT"), books, Decimal(100), Decimal(0))
    assert result is not None
    assert result.profit_pct == pytest.approx(Decimal("0.1"), abs=Decimal("0.0001"))


def test_evaluate_applies_fee_three_times():
    books = {
        "BTC_USDT": book("BTC_USDT", [(100, 1000)], [(100, 1000)]),
        "ETH_BTC": book("ETH_BTC", [("0.1", 10000)], [("0.1", 10000)]),
        "ETH_USDT": book("ETH_USDT", [(10, 10000)], [(10, 10000)]),
    }
    result = evaluate(
        _triangle("USDT -> BTC -> ETH -> USDT"), books, Decimal(100), Decimal("0.002")
    )
    assert result is not None
    # (1 - 0.002)^3 - 1 ≈ -0.599%
    assert result.profit_pct == pytest.approx(Decimal("-0.005988"), abs=Decimal("0.00001"))


def test_evaluate_marks_depth_limited():
    books = {
        "BTC_USDT": book("BTC_USDT", [(100, "0.1")], [(100, 1000)]),  # всего 10 USDT
        "ETH_BTC": book("ETH_BTC", [("0.1", 10000)], [("0.1", 10000)]),
        "ETH_USDT": book("ETH_USDT", [(10, 10000)], [(10, 10000)]),
    }
    result = evaluate(_triangle("USDT -> BTC -> ETH -> USDT"), books, Decimal(100), Decimal(0))
    assert result is not None and result.depth_limited


def test_evaluate_returns_none_without_book():
    triangle = _triangle("USDT -> BTC -> ETH -> USDT")
    assert evaluate(triangle, {}, Decimal(100), Decimal(0)) is None


def test_deep_order_gets_worse_average_price():
    """Крупная заявка съедает стакан и должна давать худший результат."""
    books = {
        "BTC_USDT": book("BTC_USDT", [(100, 1), (200, 1000)], [(100, 1000)]),
        "ETH_BTC": book("ETH_BTC", [("0.1", 10000)], [("0.1", 10000)]),
        "ETH_USDT": book("ETH_USDT", [(11, 10000)], [(11, 10000)]),
    }
    triangle = _triangle("USDT -> BTC -> ETH -> USDT")
    small = evaluate(triangle, books, Decimal(50), Decimal(0))
    large = evaluate(triangle, books, Decimal(5000), Decimal(0))
    assert small and large and small.profit_pct > large.profit_pct


def test_best_size_finds_fitting_amount():
    books = {
        "BTC_USDT": book("BTC_USDT", [(100, "0.5")], [(100, 1000)]),  # максимум 50 USDT
        "ETH_BTC": book("ETH_BTC", [("0.1", 10000)], [("0.1", 10000)]),
        "ETH_USDT": book("ETH_USDT", [(11, 10000)], [(11, 10000)]),
    }
    result = best_size(_triangle("USDT -> BTC -> ETH -> USDT"), books, Decimal(100), Decimal(0))
    assert result is not None
    assert not result.depth_limited
    assert result.start_amount <= Decimal(50)


def test_best_size_returns_full_when_depth_is_enough():
    books = {
        "BTC_USDT": book("BTC_USDT", [(100, 1000)], [(100, 1000)]),
        "ETH_BTC": book("ETH_BTC", [("0.1", 10000)], [("0.1", 10000)]),
        "ETH_USDT": book("ETH_USDT", [(11, 10000)], [(11, 10000)]),
    }
    result = best_size(_triangle("USDT -> BTC -> ETH -> USDT"), books, Decimal(100), Decimal(0))
    assert result is not None and result.start_amount == Decimal(100)


def test_check_minimums_rejects_small_cycle():
    specs = dict(SPECS)
    specs["ETH_BTC"] = spec_for("ETH_BTC", "ETH", "BTC", min_base=2)  # цикл даёт лишь 1 ETH
    books = {
        "BTC_USDT": book("BTC_USDT", [(100, 1000)], [(100, 1000)]),
        "ETH_BTC": book("ETH_BTC", [("0.1", 10000)], [("0.1", 10000)]),
        "ETH_USDT": book("ETH_USDT", [(11, 10000)], [(11, 10000)]),
    }
    triangle = _triangle("USDT -> BTC -> ETH -> USDT")
    opportunity = evaluate(triangle, books, Decimal(10), Decimal(0))
    assert "меньше минимума" in check_minimums(triangle, specs, opportunity)


def test_check_minimums_passes_for_adequate_size():
    books = {
        "BTC_USDT": book("BTC_USDT", [(100, 1000)], [(100, 1000)]),
        "ETH_BTC": book("ETH_BTC", [("0.1", 10000)], [("0.1", 10000)]),
        "ETH_USDT": book("ETH_USDT", [(11, 10000)], [(11, 10000)]),
    }
    triangle = _triangle("USDT -> BTC -> ETH -> USDT")
    opportunity = evaluate(triangle, books, Decimal(1000), Decimal(0))
    assert check_minimums(triangle, SPECS, opportunity) == ""


# ----------------------------------------------------------------- сканер


class FakeArbExchange(Exchange):
    def __init__(self, books, tickers=None, pairs=PAIRS):
        self.books = books
        self.tickers = tickers or {}
        self.pairs = pairs
        self.balances = {"USDT": Balance("USDT", Decimal(10_000))}
        self.placed: list[Order] = []
        self.fail_on: set[str] = set()
        self.book_calls = 0

    def get_pair_spec(self, symbol): return SPECS[symbol]
    def get_all_pairs(self): return self.pairs
    def get_all_tickers(self): return self.tickers
    def get_candles(self, s, i, limit=200): return []
    def get_price(self, symbol): return Decimal(1)
    def get_balances(self): return self.balances
    def get_open_orders(self, symbol): return []
    def get_order(self, symbol, oid): raise NotImplementedError
    def cancel_order(self, symbol, oid): pass

    def get_order_book(self, symbol, limit=20):
        self.book_calls += 1
        if symbol not in self.books:
            raise ExchangeError(f"нет стакана {symbol}")
        return self.books[symbol]

    def _credit(self, currency: str, amount: Decimal) -> None:
        balance = self.balances.setdefault(currency, Balance(currency, Decimal(0)))
        balance.available += amount

    def place_order(self, order):
        if order.symbol in self.fail_on:
            raise ExchangeError(f"отказ по {order.symbol}", label="BALANCE_NOT_ENOUGH")
        spec = SPECS[order.symbol]
        b = self.books[order.symbol]
        if order.side is Side.BUY:
            # Рыночная покупка задаётся суммой в котировке — как на Gate.io.
            got, _ = b.buy_with(order.amount)
            order.filled, order.avg_price = got, order.amount / got
            self._credit(spec.quote, -order.amount)
            self._credit(spec.base, got)
        else:
            got, _ = b.sell_amount(order.amount)
            order.filled, order.avg_price = order.amount, got / order.amount
            self._credit(spec.base, -order.amount)
            self._credit(spec.quote, got)
        order.order_id = f"fake-{len(self.placed)}"
        order.status = OrderStatus.FILLED
        self.placed.append(order)
        return order


def ticker(symbol, bid, ask, volume=1_000_000):
    return Ticker(symbol, Decimal(str(bid)), Decimal(str(bid)), Decimal(str(ask)), Decimal(volume))


def test_screen_ranks_by_approximate_profit():
    tickers = {
        "BTC_USDT": ticker("BTC_USDT", 100, 100),
        "ETH_BTC": ticker("ETH_BTC", "0.1", "0.1"),
        "ETH_USDT": ticker("ETH_USDT", 11, 11),
    }
    scanner = TriangleScanner(FakeArbExchange({}, tickers), fee=Decimal(0))
    scanner.load_universe()
    scored = scanner.screen(tickers)
    assert scored
    assert scored[0][1].path == "USDT -> BTC -> ETH -> USDT"
    assert scored[0][0] > 0


def test_screen_filters_illiquid_pairs():
    tickers = {
        "BTC_USDT": ticker("BTC_USDT", 100, 100, volume=10),
        "ETH_BTC": ticker("ETH_BTC", "0.1", "0.1", volume=10),
        "ETH_USDT": ticker("ETH_USDT", 11, 11, volume=10),
    }
    scanner = TriangleScanner(
        FakeArbExchange({}, tickers), fee=Decimal(0), min_quote_volume=Decimal(100_000)
    )
    scanner.load_universe()
    assert scanner.screen(tickers) == []


def test_verify_rejects_unprofitable_after_fees():
    books = {
        "BTC_USDT": book("BTC_USDT", [(100, 1000)], [(100, 1000)]),
        "ETH_BTC": book("ETH_BTC", [("0.1", 10000)], [("0.1", 10000)]),
        "ETH_USDT": book("ETH_USDT", [(10, 10000)], [(10, 10000)]),
    }
    exchange = FakeArbExchange(books)
    scanner = TriangleScanner(exchange, fee=Decimal("0.002"))
    scanner.load_universe()
    triangle = _triangle("USDT -> BTC -> ETH -> USDT")
    found, rejected = scanner.verify([triangle], Decimal(100))
    assert found == []
    assert "убыток" in rejected[triangle.path]


def test_verify_deduplicates_book_requests():
    """Два цикла на одних парах не должны дёргать стакан шесть раз."""
    books = {
        "BTC_USDT": book("BTC_USDT", [(100, 1000)], [(100, 1000)]),
        "ETH_BTC": book("ETH_BTC", [("0.1", 10000)], [("0.1", 10000)]),
        "ETH_USDT": book("ETH_USDT", [(11, 10000)], [(11, 10000)]),
    }
    exchange = FakeArbExchange(books)
    scanner = TriangleScanner(exchange, fee=Decimal(0))
    scanner.load_universe()
    both = [_triangle("USDT -> BTC -> ETH -> USDT"), _triangle("USDT -> ETH -> BTC -> USDT")]
    scanner.verify(both, Decimal(100))
    assert exchange.book_calls == 3


# --------------------------------------------------------------- исполнение


PROFITABLE_BOOKS = {
    "BTC_USDT": book("BTC_USDT", [(100, 1000)], [(100, 1000)]),
    "ETH_BTC": book("ETH_BTC", [("0.1", 10000)], [("0.1", 10000)]),
    "ETH_USDT": book("ETH_USDT", [(11, 10000)], [(11, 10000)]),
}


def _opportunity(amount=Decimal(100), fee=Decimal(0)):
    return evaluate(_triangle("USDT -> BTC -> ETH -> USDT"), PROFITABLE_BOOKS, amount, fee)


def test_dry_run_places_no_orders():
    exchange = FakeArbExchange(PROFITABLE_BOOKS)
    executor = TriangleExecutor(exchange, SPECS, dry_run=True)
    result = executor.execute(_opportunity())
    assert result.ok and result.dry_run
    assert exchange.placed == []


def test_execution_sends_three_orders():
    exchange = FakeArbExchange(PROFITABLE_BOOKS)
    executor = TriangleExecutor(exchange, SPECS, dry_run=False, min_profit_pct=Decimal("0.01"))
    result = executor.execute(_opportunity())
    assert result.ok
    assert len(exchange.placed) == 3
    assert all(o.type is OrderType.MARKET for o in exchange.placed)
    assert result.end_amount > result.start_amount


def test_execution_below_threshold_is_skipped():
    exchange = FakeArbExchange(PROFITABLE_BOOKS)
    executor = TriangleExecutor(exchange, SPECS, dry_run=False, min_profit_pct=Decimal("0.5"))
    result = executor.execute(_opportunity())
    assert not result.ok
    assert "ниже порога" in result.error
    assert exchange.placed == []


def test_depth_limited_opportunity_is_skipped():
    books = dict(PROFITABLE_BOOKS)
    books["BTC_USDT"] = book("BTC_USDT", [(100, "0.1")], [(100, 1000)])
    exchange = FakeArbExchange(books)
    opportunity = evaluate(_triangle("USDT -> BTC -> ETH -> USDT"), books, Decimal(100), Decimal(0))
    executor = TriangleExecutor(exchange, SPECS, dry_run=False, min_profit_pct=Decimal(0))
    result = executor.execute(opportunity)
    assert not result.ok and "стакан" in result.error


def test_failed_second_leg_triggers_unwind():
    """Ключевой сценарий: BTC куплен, вторая сделка отказала — BTC надо вернуть."""
    exchange = FakeArbExchange(PROFITABLE_BOOKS)
    exchange.fail_on = {"ETH_BTC"}
    executor = TriangleExecutor(exchange, SPECS, dry_run=False, min_profit_pct=Decimal("0.01"))
    result = executor.execute(_opportunity())

    assert not result.ok
    assert result.unwound
    assert not result.stuck_currency
    # Первая покупка + откатная продажа BTC обратно в USDT.
    assert [o.symbol for o in exchange.placed] == ["BTC_USDT", "BTC_USDT"]
    assert exchange.placed[-1].side is Side.SELL


def test_failed_first_leg_needs_no_unwind():
    exchange = FakeArbExchange(PROFITABLE_BOOKS)
    exchange.fail_on = {"BTC_USDT"}
    executor = TriangleExecutor(exchange, SPECS, dry_run=False, min_profit_pct=Decimal("0.01"))
    result = executor.execute(_opportunity())
    assert not result.ok and not result.unwound and not result.stuck_currency


def test_stuck_position_reported_when_unwind_impossible():
    """Нет прямой пары для отката — бот обязан громко сообщить, а не молчать."""
    exchange = FakeArbExchange(PROFITABLE_BOOKS)
    exchange.fail_on = {"ETH_BTC"}
    # Убираем возможность отката: подменяем поиск прямой пары.
    executor = TriangleExecutor(exchange, SPECS, dry_run=False, min_profit_pct=Decimal("0.01"))
    executor._direct_symbol = lambda currency: None
    result = executor.execute(_opportunity())
    assert result.stuck_currency == "BTC"
    assert result.stuck_amount > 0


def test_halts_after_consecutive_failures():
    exchange = FakeArbExchange(PROFITABLE_BOOKS)
    exchange.fail_on = {"BTC_USDT"}
    executor = TriangleExecutor(
        exchange, SPECS, dry_run=False, min_profit_pct=Decimal("0.01"), max_failures=2
    )
    executor.execute(_opportunity())
    assert not executor.halted
    executor.execute(_opportunity())
    assert executor.halted

    blocked = executor.execute(_opportunity())
    assert "остановлен" in blocked.error

    executor.resume()
    assert not executor.halted


def test_successful_cycle_resets_failure_counter():
    exchange = FakeArbExchange(PROFITABLE_BOOKS)
    executor = TriangleExecutor(exchange, SPECS, dry_run=False, min_profit_pct=Decimal("0.01"))
    executor.failures = 1
    executor.execute(_opportunity())
    assert executor.failures == 0


def test_insufficient_base_balance_blocks_execution():
    exchange = FakeArbExchange(PROFITABLE_BOOKS)
    exchange.balances = {"USDT": Balance("USDT", Decimal(5))}
    executor = TriangleExecutor(exchange, SPECS, dry_run=False, min_profit_pct=Decimal("0.01"))
    result = executor.execute(_opportunity())
    assert not result.ok and "не хватает" in result.error
    assert exchange.placed == []


def test_sell_leg_below_minimum_is_rejected_and_reported():
    """Третье звено не проходит по минимуму — и откат тоже невозможен.

    Откат с последнего звена — это ровно та же сделка, что и само звено
    (ETH -> USDT), поэтому если она не проходит по минимуму, то не пройдёт и при
    откате. Позиция честно остаётся висеть, и бот обязан сказать об этом прямо,
    а не отрапортовать об успехе.

    Минимальный объём ПОКУПКИ executor заранее не проверяет: рыночная покупка
    задаётся суммой, а не количеством базовой валюты. Это работа сканера —
    см. `check_minimums`.
    """
    specs = dict(SPECS)
    specs["ETH_USDT"] = spec_for("ETH_USDT", "ETH", "USDT", min_base=1000)
    exchange = FakeArbExchange(PROFITABLE_BOOKS)
    executor = TriangleExecutor(exchange, specs, dry_run=False, min_profit_pct=Decimal("0.01"))
    result = executor.execute(_opportunity())

    assert not result.ok
    assert "меньше минимума" in result.error
    assert not result.unwound
    assert result.stuck_currency == "ETH"
    assert result.stuck_amount > 0
    # Успели пройти только два звена, третье не отправлялось.
    assert [o.symbol for o in exchange.placed] == ["BTC_USDT", "ETH_BTC"]
