"""Тесты клиента Gate.io — без обращений к сети."""

from __future__ import annotations

import hashlib
import hmac
import json
from decimal import Decimal

import pytest

from gatebot.exchange.base import ExchangeError, InsufficientBalance
from gatebot.exchange.gateio import GateIOClient
from gatebot.types import Order, OrderStatus, OrderType, Side


class FakeResponse:
    def __init__(self, status_code=200, payload=None, text=""):
        self.status_code = status_code
        self._payload = payload
        self.content = b"x" if payload is not None else b""
        self.text = text

    def json(self):
        if self._payload is None:
            raise ValueError("нет тела")
        return self._payload


class FakeSession:
    """Записывает запросы и отдаёт заранее подготовленные ответы."""

    def __init__(self, responses):
        self.responses = list(responses)
        self.calls = []
        self.headers = {}

    def request(self, method, url, params=None, data=None, headers=None, timeout=None):
        self.calls.append(
            {"method": method, "url": url, "params": params, "data": data, "headers": headers}
        )
        return self.responses.pop(0)


@pytest.fixture
def client():
    return GateIOClient("key123", "secret456", max_retries=2)


def test_signature_matches_gateio_spec(client):
    headers = client._sign("GET", "/api/v4/spot/accounts", "", "")
    ts = headers["Timestamp"]
    expected_message = f"GET\n/api/v4/spot/accounts\n\n{hashlib.sha512(b'').hexdigest()}\n{ts}"
    expected = hmac.new(b"secret456", expected_message.encode(), hashlib.sha512).hexdigest()
    assert headers["SIGN"] == expected
    assert headers["KEY"] == "key123"


def test_signature_includes_body_hash(client):
    body = json.dumps({"amount": "1"})
    headers = client._sign("POST", "/api/v4/spot/orders", "", body)
    ts = headers["Timestamp"]
    message = (
        f"POST\n/api/v4/spot/orders\n\n{hashlib.sha512(body.encode()).hexdigest()}\n{ts}"
    )
    assert headers["SIGN"] == hmac.new(b"secret456", message.encode(), hashlib.sha512).hexdigest()


def test_private_call_without_keys_fails():
    with pytest.raises(ExchangeError, match="GATEIO_API_KEY"):
        GateIOClient().get_balances()


def test_candles_parse_gateio_field_order(client):
    # [ts, quote_volume, close, high, low, open, base_volume, closed]
    client._session = FakeSession(
        [FakeResponse(200, [["1700000000", "500", "102", "105", "99", "100", "5", "true"]])]
    )
    candle = client.get_candles("BTC_USDT", "1h", 1)[0]
    assert (candle.open, candle.high, candle.low, candle.close) == (
        Decimal(100),
        Decimal(105),
        Decimal(99),
        Decimal(102),
    )
    assert candle.volume == Decimal(5)


def test_candles_sorted_ascending(client):
    rows = [
        ["1700003600", "1", "2", "3", "1", "2", "1", "true"],
        ["1700000000", "1", "2", "3", "1", "2", "1", "true"],
    ]
    client._session = FakeSession([FakeResponse(200, rows)])
    candles = client.get_candles("BTC_USDT", "1h", 2)
    assert [c.ts for c in candles] == [1700000000, 1700003600]


def test_pair_spec_maps_precision_fields(client):
    client._session = FakeSession(
        [
            FakeResponse(
                200,
                {
                    "id": "BTC_USDT",
                    "base": "BTC",
                    "quote": "USDT",
                    "amount_precision": 6,
                    "precision": 2,
                    "min_base_amount": "0.0001",
                    "min_quote_amount": "3",
                    "trade_status": "tradable",
                },
            )
        ]
    )
    spec = client.get_pair_spec("BTC_USDT")
    assert spec.amount_precision == 6
    assert spec.price_precision == 2
    assert spec.min_quote_amount == Decimal(3)


def test_pair_spec_is_cached(client):
    payload = {
        "id": "BTC_USDT", "base": "BTC", "quote": "USDT",
        "amount_precision": 6, "precision": 2, "trade_status": "tradable",
    }
    client._session = FakeSession([FakeResponse(200, payload)])
    client.get_pair_spec("BTC_USDT")
    client.get_pair_spec("BTC_USDT")  # второго запроса быть не должно
    assert len(client._session.calls) == 1


def test_untradable_pair_rejected(client):
    client._session = FakeSession(
        [
            FakeResponse(
                200,
                {"id": "X_USDT", "base": "X", "quote": "USDT", "amount_precision": 2,
                 "precision": 2, "trade_status": "untradable"},
            )
        ]
    )
    with pytest.raises(ExchangeError, match="недоступна"):
        client.get_pair_spec("X_USDT")


def test_order_parsing_computes_fill_price(client):
    row = {
        "id": "12345",
        "text": "t-grid-b1",
        "currency_pair": "BTC_USDT",
        "side": "buy",
        "type": "limit",
        "status": "closed",
        "amount": "2",
        "left": "0",
        "filled_total": "180",
        "price": "90",
        "fee": "0.004",
        "fee_currency": "BTC",
        "create_time": "1700000000",
    }
    order = client._to_order(row)
    assert order.client_id == "grid-b1"  # префикс t- снят
    assert order.status is OrderStatus.FILLED
    assert order.filled == Decimal(2)
    assert order.avg_price == Decimal(90)
    assert order.fee_currency == "BTC"


def test_partially_filled_cancelled_order_counts_as_filled(client):
    """Частично исполненный и снятый ордер: объём прошёл, его нельзя терять."""
    order = client._to_order(
        {"id": "1", "status": "cancelled", "amount": "2", "left": "1",
         "filled_total": "90", "side": "buy", "type": "limit", "price": "90"}
    )
    assert order.status is OrderStatus.FILLED
    assert order.filled == Decimal(1)


def test_client_text_is_sanitized_and_prefixed():
    assert GateIOClient._client_text("grid-b1") == "t-grid-b1"
    assert GateIOClient._client_text("a b/c") == "t-a-b-c"
    long_id = "x" * 50
    text = GateIOClient._client_text(long_id)
    assert text.startswith("t-") and len(text) - 2 <= 28


def test_limit_order_body(client):
    client._session = FakeSession(
        [FakeResponse(200, {"id": "9", "status": "open", "amount": "1", "left": "1",
                            "side": "buy", "type": "limit", "price": "90"})]
    )
    client.place_order(
        Order("cid", "BTC_USDT", Side.BUY, OrderType.LIMIT, Decimal(1), Decimal(90))
    )
    body = json.loads(client._session.calls[0]["data"])
    assert body["time_in_force"] == "gtc"
    assert body["price"] == "90"
    assert body["account"] == "spot"


def test_market_order_uses_ioc(client):
    client._session = FakeSession(
        [FakeResponse(200, {"id": "9", "status": "closed", "amount": "100", "left": "0",
                            "side": "buy", "type": "market", "filled_total": "100"})]
    )
    client.place_order(Order("cid", "BTC_USDT", Side.BUY, OrderType.MARKET, Decimal(100)))
    body = json.loads(client._session.calls[0]["data"])
    assert body["time_in_force"] == "ioc"
    assert "price" not in body


def test_limit_order_without_price_rejected(client):
    with pytest.raises(ExchangeError, match="без цены"):
        client.place_order(Order("cid", "BTC_USDT", Side.BUY, OrderType.LIMIT, Decimal(1)))


def test_balance_error_is_typed(client):
    client._session = FakeSession(
        [FakeResponse(400, {"label": "BALANCE_NOT_ENOUGH", "message": "не хватает средств"})]
    )
    with pytest.raises(InsufficientBalance):
        client.get_balances()


def test_rate_limit_is_retried(client):
    client._session = FakeSession(
        [
            FakeResponse(429, {"label": "TOO_MANY_REQUESTS", "message": "slow down"}),
            FakeResponse(200, [{"currency": "USDT", "available": "100", "locked": "0"}]),
        ]
    )
    balances = client.get_balances()
    assert balances["USDT"].available == Decimal(100)
    assert len(client._session.calls) == 2


def test_auth_error_is_not_retried(client):
    client._session = FakeSession(
        [FakeResponse(401, {"label": "INVALID_KEY", "message": "bad key"})]
    )
    with pytest.raises(ExchangeError, match="INVALID_KEY"):
        client.get_balances()
    assert len(client._session.calls) == 1


def test_cancel_ignores_already_closed_order(client):
    client._session = FakeSession(
        [FakeResponse(404, {"label": "ORDER_NOT_FOUND", "message": "not found"})]
    )
    client.cancel_order("BTC_USDT", "42")  # не должно бросать


def test_query_string_used_in_signature(client):
    """Подпись обязана включать query — иначе биржа вернёт INVALID_SIGNATURE."""
    client._session = FakeSession([FakeResponse(200, [])])
    client.get_open_orders("BTC_USDT")
    call = client._session.calls[0]
    assert call["params"]["currency_pair"] == "BTC_USDT"
    assert "SIGN" in call["headers"]
