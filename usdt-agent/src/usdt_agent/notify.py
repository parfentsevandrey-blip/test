"""Outbound notifications for an agent nobody is watching.

Supports a generic JSON webhook (Slack/Discord/n8n all accept one) and Telegram.
Failures are logged and swallowed — a dead webhook must never stop the trading
loop, and must never be retried so hard that it becomes the reason the loop is
late.
"""

from __future__ import annotations

import logging
import os
import time

from . import http

log = logging.getLogger(__name__)


class Notifier:
    """Fire-and-forget alerting with a rate limit."""

    def __init__(self, webhook: str = "", min_interval_s: float = 30.0) -> None:
        self.webhook = webhook or os.environ.get("USDT_AGENT_WEBHOOK", "")
        self.telegram_token = os.environ.get("TELEGRAM_BOT_TOKEN", "")
        self.telegram_chat = os.environ.get("TELEGRAM_CHAT_ID", "")
        self.min_interval_s = min_interval_s
        self._last: dict[str, float] = {}

    @property
    def enabled(self) -> bool:
        return bool(self.webhook or (self.telegram_token and self.telegram_chat))

    def send(self, text: str, key: str = "default", force: bool = False) -> bool:
        if not self.enabled:
            return False
        now = time.time()
        if not force and now - self._last.get(key, 0.0) < self.min_interval_s:
            return False
        self._last[key] = now

        ok = False
        if self.webhook:
            try:
                http.post_json(self.webhook, {"text": text, "content": text}, timeout=6.0, retries=1)
                ok = True
            except Exception as e:
                log.warning("webhook notify failed: %s", e)
        if self.telegram_token and self.telegram_chat:
            try:
                http.post_json(
                    f"https://api.telegram.org/bot{self.telegram_token}/sendMessage",
                    {"chat_id": self.telegram_chat, "text": text, "parse_mode": "Markdown"},
                    timeout=6.0, retries=1,
                )
                ok = True
            except Exception as e:
                log.warning("telegram notify failed: %s", e)
        return ok

    # -- canned messages -------------------------------------------------
    def halted(self, reason: str, equity: float) -> None:
        self.send(f"🛑 *USDT agent halted*\n{reason}\nequity: {equity:,.2f} USDT", "halt", force=True)

    def daily(self, summary: dict) -> None:
        self.send(
            f"📊 *USDT agent daily*\n"
            f"equity: {summary.get('equity', 0):,.2f} USDT "
            f"({summary.get('return_pct', 0):+.3f}%)\n"
            f"closed trades: {int(summary.get('ledger', {}).get('closed_trades', 0))}\n"
            f"treasury: {summary.get('treasury', 0):,.2f} USDT",
            "daily", force=True,
        )
