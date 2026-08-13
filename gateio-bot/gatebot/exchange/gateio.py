"""Клиент Gate.io API v4 (спот).

Подпись запроса — HMAC-SHA512 по строке
``METHOD\\nURL_PATH\\nQUERY\\nSHA512(BODY)\\nTIMESTAMP``.
Ключи передаются в заголовках KEY / Timestamp / SIGN.
Документация: https://www.gate.io/docs/developers/apiv4/
"""

from __future__ import annotations

import hashlib
import hmac
import json
import logging
import re
import time
from decimal import Decimal
from typing import Any, Optional

import requests

from ..types import (
    Balance,
    BookLevel,
    Candle,
    Order,
    OrderBook,
    OrderStatus,
    OrderType,
    PairSpec,
    Side,
    Ticker,
)
from .base import Exchange, ExchangeError, InsufficientBalance

log = logging.getLogger(__name__)

HOST = "https://api.gateio.ws"
PREFIX = "/api/v4"

# Gate.io требует, чтобы пользовательская метка ордера начиналась с `t-`
# и содержала не более 28 символов после префикса.
_TEXT_MAX = 28
_TEXT_ALLOWED = re.compile(r"[^0-9a-zA-Z_.-]")

# Коды ошибок, при которых повтор запроса бессмысленен.
_FATAL_LABELS = {
    "INVALID_KEY",
    "INVALID_SIGNATURE",
    "FORBIDDEN",
    "READ_ONLY",
    "INVALID_CURRENCY_PAIR",
    "POC_FILL_IMMEDIATELY",
}


class GateIOClient(Exchange):
    """Синхронный REST-клиент. Публичные методы работают и без ключей."""

    name = "gate.io"

    def __init__(
        self,
        api_key: str = "",
        api_secret: str = "",
        *,
        host: str = HOST,
        timeout: float = 10.0,
        max_retries: int = 4,
    ):
        self.api_key = api_key
        self.api_secret = api_secret
        self.host = host.rstrip("/")
        self.timeout = timeout
        self.max_retries = max_retries
        self._session = requests.Session()
        self._session.headers.update(
            {"Accept": "application/json", "Content-Type": "application/json"}
        )
        self._pair_cache: dict[str, PairSpec] = {}

    # ------------------------------------------------------------------ HTTP

    def _sign(self, method: str, path: str, query: str, body: str) -> dict[str, str]:
        ts = str(int(time.time()))
        payload_hash = hashlib.sha512(body.encode()).hexdigest()
        message = f"{method}\n{path}\n{query}\n{payload_hash}\n{ts}"
        sign = hmac.new(
            self.api_secret.encode(), message.encode(), hashlib.sha512
        ).hexdigest()
        return {"KEY": self.api_key, "Timestamp": ts, "SIGN": sign}

    def _request(
        self,
        method: str,
        endpoint: str,
        *,
        params: Optional[dict[str, Any]] = None,
        body: Optional[dict[str, Any]] = None,
        auth: bool = False,
    ) -> Any:
        path = PREFIX + endpoint
        params = {k: v for k, v in (params or {}).items() if v is not None}
        query = "&".join(f"{k}={v}" for k, v in params.items())
        body_str = json.dumps(body) if body is not None else ""

        if auth:
            if not (self.api_key and self.api_secret):
                raise ExchangeError(
                    "Для приватных методов нужны GATEIO_API_KEY и GATEIO_API_SECRET",
                    label="NO_CREDENTIALS",
                )

        last_error: Optional[Exception] = None
        for attempt in range(self.max_retries):
            if attempt:
                # 1с, 2с, 4с — Gate.io отдаёт 429 при превышении лимита частоты.
                time.sleep(2 ** (attempt - 1))
            headers = self._sign(method, path, query, body_str) if auth else {}
            try:
                resp = self._session.request(
                    method,
                    self.host + path,
                    params=params or None,
                    data=body_str or None,
                    headers=headers,
                    timeout=self.timeout,
                )
            except requests.RequestException as exc:
                last_error = ExchangeError(f"Сеть: {exc}", retryable=True)
                log.warning("Сетевая ошибка (%s/%s): %s", attempt + 1, self.max_retries, exc)
                continue

            if resp.status_code < 400:
                return resp.json() if resp.content else None

            error = self._to_error(resp)
            if not error.retryable:
                raise error
            last_error = error
            log.warning(
                "Ошибка биржи (%s/%s): %s", attempt + 1, self.max_retries, error
            )

        raise last_error or ExchangeError("Запрос не удался")

    @staticmethod
    def _to_error(resp: requests.Response) -> ExchangeError:
        try:
            payload = resp.json()
            label = str(payload.get("label", ""))
            message = str(payload.get("message", payload))
        except ValueError:
            label, message = "", resp.text[:300]

        text = f"HTTP {resp.status_code} {label}: {message}".strip()
        if label in ("BALANCE_NOT_ENOUGH", "MARGIN_BALANCE_NOT_ENOUGH"):
            return InsufficientBalance(text, label=label)
        retryable = (
            resp.status_code == 429
            or resp.status_code >= 500
            or label in ("TOO_MANY_REQUESTS", "REQUEST_EXPIRED", "SERVER_ERROR")
        ) and label not in _FATAL_LABELS
        return ExchangeError(text, label=label, retryable=retryable)

    # -------------------------------------------------------------- Публичное

    def get_pair_spec(self, symbol: str) -> PairSpec:
        if symbol in self._pair_cache:
            return self._pair_cache[symbol]
        data = self._request("GET", f"/spot/currency_pairs/{symbol}")
        if data.get("trade_status") not in (None, "tradable"):
            raise ExchangeError(
                f"Пара {symbol} недоступна для торговли "
                f"(trade_status={data.get('trade_status')})",
                label="NOT_TRADABLE",
            )
        spec = PairSpec(
            symbol=data["id"],
            base=data["base"],
            quote=data["quote"],
            amount_precision=int(data["amount_precision"]),
            price_precision=int(data["precision"]),
            min_base_amount=Decimal(str(data.get("min_base_amount") or 0)),
            min_quote_amount=Decimal(str(data.get("min_quote_amount") or 0)),
        )
        self._pair_cache[symbol] = spec
        return spec

    def get_candles(self, symbol: str, interval: str, limit: int = 200) -> list[Candle]:
        rows = self._request(
            "GET",
            "/spot/candlesticks",
            params={"currency_pair": symbol, "interval": interval, "limit": limit},
        )
        # Порядок полей у Gate.io нестандартный:
        # [ts, quote_volume, close, high, low, open, base_volume, window_closed]
        candles = [
            Candle(
                ts=int(r[0]),
                open=Decimal(r[5]),
                high=Decimal(r[3]),
                low=Decimal(r[4]),
                close=Decimal(r[2]),
                volume=Decimal(r[6]),
            )
            for r in rows
        ]
        candles.sort(key=lambda c: c.ts)
        return candles

    def get_candles_range(
        self, symbol: str, interval: str, start: int, end: int
    ) -> list[Candle]:
        """История за период с постраничной догрузкой.

        Gate.io отдаёт не больше 1000 точек за запрос, поэтому идём окнами.
        """
        from ..config import interval_seconds  # локальный импорт: circular

        span = interval_seconds(interval)
        chunk = 999 * span
        out: list[Candle] = []
        seen: set[int] = set()
        cursor = start
        while cursor < end:
            window_end = min(cursor + chunk, end)
            rows = self._request(
                "GET",
                "/spot/candlesticks",
                params={
                    "currency_pair": symbol,
                    "interval": interval,
                    "from": cursor,
                    "to": window_end,
                },
            )
            if not rows:
                break
            for r in rows:
                ts = int(r[0])
                if ts in seen:
                    continue
                seen.add(ts)
                out.append(
                    Candle(
                        ts=ts,
                        open=Decimal(r[5]),
                        high=Decimal(r[3]),
                        low=Decimal(r[4]),
                        close=Decimal(r[2]),
                        volume=Decimal(r[6]),
                    )
                )
            newest = max(int(r[0]) for r in rows)
            if newest <= cursor:
                break  # биржа не двигается дальше — выходим, чтобы не зациклиться
            cursor = newest + span

        out.sort(key=lambda c: c.ts)
        return out

    def get_price(self, symbol: str) -> Decimal:
        data = self._request("GET", "/spot/tickers", params={"currency_pair": symbol})
        if not data:
            raise ExchangeError(f"Нет тикера для {symbol}", label="NO_TICKER")
        return Decimal(data[0]["last"])

    def get_order_book(self, symbol: str, limit: int = 20) -> OrderBook:
        data = self._request(
            "GET", "/spot/order_book", params={"currency_pair": symbol, "limit": limit}
        )
        return OrderBook(
            symbol=symbol,
            asks=[BookLevel(Decimal(p), Decimal(a)) for p, a in data.get("asks", [])],
            bids=[BookLevel(Decimal(p), Decimal(a)) for p, a in data.get("bids", [])],
            ts=int(data.get("current") or 0),
        )

    def get_all_tickers(self) -> dict[str, Ticker]:
        """Верх стакана по всем ~2200 парам одним запросом.

        Данные кешированные и отстают от живого стакана, поэтому годятся только
        для отбора кандидатов, но не для расчёта сделки.
        """
        rows = self._request("GET", "/spot/tickers")
        out: dict[str, Ticker] = {}
        for r in rows:
            bid, ask = r.get("highest_bid"), r.get("lowest_ask")
            if not bid or not ask:
                continue  # пара без встречных заявок — торговать нечем
            out[r["currency_pair"]] = Ticker(
                symbol=r["currency_pair"],
                last=Decimal(r.get("last") or 0),
                bid=Decimal(bid),
                ask=Decimal(ask),
                quote_volume=Decimal(r.get("quote_volume") or 0),
            )
        return out

    def get_all_pairs(self) -> list[PairSpec]:
        rows = self._request("GET", "/spot/currency_pairs")
        out: list[PairSpec] = []
        for r in rows:
            if r.get("trade_status") != "tradable":
                continue
            spec = PairSpec(
                symbol=r["id"],
                base=r["base"],
                quote=r["quote"],
                amount_precision=int(r["amount_precision"]),
                price_precision=int(r["precision"]),
                min_base_amount=Decimal(str(r.get("min_base_amount") or 0)),
                min_quote_amount=Decimal(str(r.get("min_quote_amount") or 0)),
            )
            self._pair_cache[spec.symbol] = spec
            out.append(spec)
        return out

    # -------------------------------------------------------------- Приватное

    def get_balances(self) -> dict[str, Balance]:
        rows = self._request("GET", "/spot/accounts", auth=True)
        return {
            r["currency"]: Balance(
                currency=r["currency"],
                available=Decimal(r["available"]),
                locked=Decimal(r.get("locked") or 0),
            )
            for r in rows
        }

    def get_open_orders(self, symbol: str) -> list[Order]:
        rows = self._request(
            "GET",
            "/spot/orders",
            params={"currency_pair": symbol, "status": "open", "limit": 100},
            auth=True,
        )
        return [self._to_order(r) for r in rows]

    def get_order(self, symbol: str, order_id: str) -> Order:
        row = self._request(
            "GET",
            f"/spot/orders/{order_id}",
            params={"currency_pair": symbol},
            auth=True,
        )
        return self._to_order(row)

    def place_order(self, order: Order) -> Order:
        body: dict[str, Any] = {
            "currency_pair": order.symbol,
            "account": "spot",
            "side": order.side.value,
            "type": order.type.value,
            "text": self._client_text(order.client_id),
        }
        if order.type is OrderType.LIMIT:
            if order.price is None:
                raise ExchangeError("Лимитный ордер без цены", label="NO_PRICE")
            body["price"] = str(order.price)
            body["amount"] = str(order.amount)
            body["time_in_force"] = "gtc"
        else:
            # У Gate.io рыночная покупка задаётся суммой в валюте котировки,
            # а рыночная продажа — объёмом в базовой валюте. Вызывающий код
            # обязан передать `amount` уже в нужной единице (см. engine).
            body["amount"] = str(order.amount)
            body["time_in_force"] = "ioc"

        row = self._request("POST", "/spot/orders", body=body, auth=True)
        placed = self._to_order(row)
        placed.client_id = order.client_id
        return placed

    def cancel_order(self, symbol: str, order_id: str) -> None:
        try:
            self._request(
                "DELETE",
                f"/spot/orders/{order_id}",
                params={"currency_pair": symbol},
                auth=True,
            )
        except ExchangeError as exc:
            # Ордер уже исполнился или снят — это не ошибка для реконсилятора.
            if exc.label in ("ORDER_NOT_FOUND", "ORDER_CLOSED", "ORDER_CANCELLED"):
                log.debug("Ордер %s уже закрыт", order_id)
                return
            raise

    def cancel_all(self, symbol: str) -> None:
        self._request(
            "DELETE", "/spot/orders", params={"currency_pair": symbol}, auth=True
        )

    # ------------------------------------------------------------- Внутреннее

    @staticmethod
    def _client_text(client_id: str) -> str:
        safe = _TEXT_ALLOWED.sub("-", client_id)[:_TEXT_MAX]
        return f"t-{safe or 'order'}"

    @staticmethod
    def _to_order(row: dict[str, Any]) -> Order:
        amount = Decimal(row.get("amount") or 0)
        left = Decimal(row.get("left") or 0)
        filled_quote = Decimal(row.get("filled_total") or 0)
        order_type = OrderType(row.get("type", "limit"))
        side = Side(row.get("side", "buy"))
        avg_deal = Decimal(row["avg_deal_price"]) if row.get("avg_deal_price") else None

        # У рыночной ПОКУПКИ поля amount/left выражены в валюте котировки, а не
        # в базовой: на Gate.io такая заявка задаётся суммой к трате. Считать
        # `amount - left` объёмом купленного здесь нельзя — получится сумма
        # в USDT вместо количества монет.
        if row.get("filled_amount") is not None:
            filled = Decimal(row["filled_amount"])
        elif order_type is OrderType.MARKET and side is Side.BUY:
            filled = filled_quote / avg_deal if avg_deal else Decimal(0)
        else:
            filled = amount - left

        if avg_deal:
            avg = avg_deal
        else:
            avg = filled_quote / filled if filled > 0 else Decimal(0)

        status_map = {
            "open": OrderStatus.OPEN,
            "closed": OrderStatus.FILLED,
            "cancelled": OrderStatus.CANCELLED,
        }
        status = status_map.get(str(row.get("status")), OrderStatus.OPEN)
        # Частично исполненный и снятый ордер биржа помечает как cancelled —
        # для учёта важно, что часть объёма всё-таки прошла.
        if status is OrderStatus.CANCELLED and filled > 0:
            status = OrderStatus.FILLED

        text = str(row.get("text") or "")
        fee = Decimal(row.get("fee") or 0)
        fee_currency = str(row.get("fee_currency") or "")
        return Order(
            client_id=text[2:] if text.startswith("t-") else text,
            symbol=str(row.get("currency_pair") or ""),
            side=side,
            type=order_type,
            amount=amount,
            price=Decimal(row["price"]) if row.get("price") else None,
            order_id=str(row.get("id") or ""),
            status=status,
            filled=filled,
            avg_price=avg,
            fee=fee,
            fee_currency=fee_currency,
            created_ts=int(float(row.get("create_time") or 0)),
        )
