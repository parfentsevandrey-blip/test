"""Tests for the dashboard's HTTP surface: the auth gate, the contract, the fences.

The web layer is where the project's load-bearing rule meets a browser, so these
tests speak real HTTP to a real server on an ephemeral port rather than calling
handlers directly — a token check that only holds when the handler is invoked by
hand is not a token check.

Nothing here may touch the network. The wallet is empty (or its chain reads are
stubbed) and the JSON transport underneath is replaced by a tripwire, so a test
that reaches for an RPC fails loudly instead of quietly going online.
"""

from __future__ import annotations

import http.client
import json
import os
import re
import sys
import tempfile
import threading
import time
import unittest
from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path
from typing import Any
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from usdt_agent.earn.models import Gig
from usdt_agent.earn.wallet import Wallet
from usdt_agent.web.api import MAX_BODY_BYTES, serve

#: Only the services channel is enabled: every other channel discovers by
#: walking third-party APIs, and this suite must pass with the cable unplugged.
SERVICES_ONLY = """\
[earn]
max_open_orders = 3
min_rate_usdt_per_hour = 1.0

[earn.channels.bounties]
enabled = false

[earn.channels.affiliate]
enabled = false

[earn.channels.passive]
enabled = false
"""

#: A receiving address so invoices can be minted. It is a string, not a wallet:
#: nothing in this project can spend from it, and no RPC is ever asked about it.
WITH_WALLET = SERVICES_ONLY + """
[earn.wallet]
tron = "TTestOnlyAddressNobodyEverFunded"
"""

GET_ROUTES = (
    "/api/state",
    "/api/gigs",
    "/api/gigs?limit=5&channel=services",
    "/api/approvals",
    "/api/catalogue",
    "/api/invoices",
    "/api/trading",
    "/api/ledger",
    "/api/ledger?limit=10",
    "/api/discovery",
)

POST_ROUTES = (
    "/api/collect",
    "/api/invoices",
    "/api/gigs/deadbeef/take",
    "/api/approvals/deadbeef/decide",
    "/api/discovery/probe",
)

STATE_KEYS = ("generated_at", "treasury", "wallet", "ladder", "channels", "counts", "ledger")
TREASURY_KEYS = ("confirmed_usdt", "expected_usdt", "hours_spent")
WALLET_KEYS = ("addresses", "balances", "total_usdt", "errors", "chains_configured")
LADDER_KEYS = ("stage", "completed", "total", "stages")
COUNTS_KEYS = ("gigs", "open_orders", "pending_approvals", "transfers_seen")
CHANNEL_KEYS = (
    "name", "description", "autonomy", "capital_required_usdt", "typical_lag_days",
    "ready", "blockers", "requirements", "confirmed_usdt", "expected_usdt", "calibration",
)
CALIBRATION_KEYS = (
    "orders", "paid", "conversion", "realized_usdt_per_hour", "effort_calibration", "verdict",
)
COLLECT_KEYS = (
    "confirmed_usdt", "new_transfers", "matched", "unattributed_usdt", "expired",
    "delta_detected", "errors", "balances", "baselined", "treasury_usdt",
)


class DashboardCase(unittest.TestCase):
    """A real server on port 0, a temp database, a temp config and no network.

    The agent is built once per class, exactly as :func:`serve` builds it in
    production — a per-test rebuild would throw away the in-memory invoices that
    make the storefront's payment matching work.
    """

    config_toml: str = SERVICES_ONLY

    httpd: Any
    token: str
    port: int
    startup_output: str

    @classmethod
    def setUpClass(cls) -> None:
        tmp = tempfile.TemporaryDirectory()
        cls.addClassCleanup(tmp.cleanup)
        root = Path(tmp.name)
        config_path = root / "agent.toml"
        config_path.write_text(cls.config_toml, encoding="utf-8")

        # Two independent guarantees that no RPC is contacted: the chain reads
        # are stubbed, and the transport beneath them is a tripwire that turns
        # an attempted call into a failure instead of a slow test.
        for patcher in (
            mock.patch.object(Wallet, "balance", return_value=0.0),
            mock.patch.object(Wallet, "transfers", return_value=[]),
            mock.patch("usdt_agent.http.get_json", side_effect=AssertionError("network used")),
            mock.patch("usdt_agent.http.post_json", side_effect=AssertionError("network used")),
        ):
            patcher.start()
            cls.addClassCleanup(patcher.stop)

        # A developer with USDT_WALLET_TRON exported must not turn this suite
        # into a live chain query against their own address.
        env = {
            key: value for key, value in os.environ.items()
            if not key.startswith(("USDT_WALLET_", "USDT_AGENT_"))
        }
        printed = StringIO()
        with mock.patch.dict(os.environ, env, clear=True), redirect_stdout(printed):
            cls.httpd, cls.token = serve(
                config_path=config_path,
                db_path=str(root / "agent.db"),
                host="127.0.0.1",
                port=0,
            )
        cls.startup_output = printed.getvalue()
        cls.port = cls.httpd.server_address[1]

        thread = threading.Thread(target=cls.httpd.serve_forever, daemon=True)
        thread.start()
        cls.addClassCleanup(cls._stop, thread)

    @classmethod
    def _stop(cls, thread: threading.Thread) -> None:
        cls.httpd.shutdown()
        thread.join(timeout=5)
        cls.httpd.server_close()
        cls.httpd.agent_context.close()

    # -- HTTP helpers ----------------------------------------------------
    def call(
        self,
        method: str,
        path: str,
        *,
        body: Any = None,
        token: str | None = None,
        send_token: bool = True,
    ) -> tuple[int, bytes]:
        """One request. ``token=None`` sends the real one; ``send_token=False`` sends none."""
        headers = {"Content-Type": "application/json"}
        if send_token:
            headers["X-Agent-Token"] = self.token if token is None else token
        payload = None if body is None else json.dumps(body).encode()
        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=10)
        try:
            conn.request(method, path, body=payload, headers=headers)
            response = conn.getresponse()
            return response.status, response.read()
        finally:
            conn.close()

    def json_call(self, method: str, path: str, **kwargs: Any) -> tuple[int, Any]:
        status, raw = self.call(method, path, **kwargs)
        return status, json.loads(raw)

    @property
    def ctx(self) -> Any:
        """The context the server itself is using — the same objects, not a copy."""
        return self.httpd.agent_context


class TestServerStartup(DashboardCase):
    def test_binds_an_ephemeral_port_rather_than_the_configured_default(self) -> None:
        self.assertNotEqual(self.port, 0)
        self.assertNotEqual(self.port, 8500)
        self.assertEqual(self.httpd.server_address[0], "127.0.0.1")

    def test_healthz_answers_without_a_token(self) -> None:
        status, payload = self.json_call("GET", "/healthz", send_token=False)
        self.assertEqual(status, 200)
        self.assertIs(payload["ok"], True)
        self.assertIsInstance(payload["ts"], float)
        self.assertLess(abs(payload["ts"] - time.time()), 60.0)

    def test_healthz_stays_public_even_with_a_bad_token(self) -> None:
        """Liveness is not a secret; a wrong token must not turn it into one."""
        status, payload = self.json_call("GET", "/healthz", token="nonsense")
        self.assertEqual(status, 200)
        self.assertIs(payload["ok"], True)

    def test_the_token_is_generated_and_printed_exactly_once(self) -> None:
        self.assertGreaterEqual(len(self.token), 32)  # secrets.token_urlsafe(24)
        self.assertEqual(self.startup_output.count(self.token), 1)

    def test_the_index_page_never_carries_the_token(self) -> None:
        """The original design injected the token into an unauthenticated page,
        so any process on the machine could ``curl /`` and take over the API.
        The markup must now contain no secret at all."""
        status, raw = self.call("GET", "/", send_token=False)
        markup = raw.decode()
        self.assertEqual(status, 200)
        self.assertEqual(markup.count('name="agent-token"'), 1)
        self.assertNotIn(self.token, markup)
        self.assertNotIn("__AGENT_TOKEN__", markup)
        self.assertIn('<meta name="agent-token" content="">', markup)

    def test_the_launch_nonce_buys_an_httponly_session_and_is_then_spent(self) -> None:
        """The browser gets its session from the single-use launch link, not from
        the document — and the cookie is HttpOnly, so page script cannot read it
        even if something manages to run there."""
        nonce = re.search(r"\?k=([\w\-]+)", self.startup_output)
        self.assertIsNotNone(nonce, "serve() must print a launch URL carrying the nonce")
        token = nonce.group(1)

        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=10)
        try:
            conn.request("GET", f"/?k={token}")
            first = conn.getresponse()
            first.read()
            cookie = first.getheader("Set-Cookie") or ""
        finally:
            conn.close()
        self.assertEqual(first.status, 303)
        self.assertIn("usdt_agent_session=", cookie)
        self.assertIn("HttpOnly", cookie)
        self.assertIn("SameSite=Strict", cookie)

        # The cookie authenticates the API without any header.
        session = cookie.split(";", 1)[0]
        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=10)
        try:
            conn.request("GET", "/api/state", headers={"Cookie": session})
            self.assertEqual(conn.getresponse().status, 200)
        finally:
            conn.close()

        # Spent: a replay of the same nonce hands out nothing further.
        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=10)
        try:
            conn.request("GET", f"/?k={token}")
            replay = conn.getresponse()
            replay.read()
            self.assertNotEqual(replay.status, 303)
            self.assertIsNone(replay.getheader("Set-Cookie"))
        finally:
            conn.close()

    def test_a_foreign_host_header_is_refused(self) -> None:
        """DNS rebinding: a remote page can point a name it owns at 127.0.0.1 and
        reach this server same-origin. The Host header is what distinguishes that
        from a genuine local visit."""
        for host in ("evil.example.com", "attacker.test:8500"):
            with self.subTest(host=host):
                conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=10)
                try:
                    conn.request("GET", "/api/state",
                                 headers={"Host": host, "X-Agent-Token": self.token})
                    self.assertEqual(conn.getresponse().status, 421)
                finally:
                    conn.close()

    def test_a_foreign_origin_header_is_refused(self) -> None:
        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=10)
        try:
            conn.request("GET", "/api/state", headers={
                "Origin": "https://evil.example.com",
                "X-Agent-Token": self.token,
            })
            self.assertEqual(conn.getresponse().status, 421)
        finally:
            conn.close()

    def test_an_unknown_non_api_path_is_a_plain_404(self) -> None:
        status, payload = self.json_call("GET", "/nope", send_token=False)
        self.assertEqual(status, 404)
        self.assertEqual(payload, {"error": "not found"})


class TestAuthentication(DashboardCase):
    def test_every_get_route_is_401_without_a_token_and_200_with_one(self) -> None:
        for path in GET_ROUTES:
            with self.subTest(path=path):
                status, payload = self.json_call("GET", path, send_token=False)
                self.assertEqual(status, 401)
                self.assertEqual(payload, {"error": "unauthorized"})

                status, payload = self.json_call("GET", path)
                self.assertEqual(status, 200)
                self.assertIsInstance(payload, dict)

    def test_every_post_route_is_401_without_a_token(self) -> None:
        for path in POST_ROUTES:
            with self.subTest(path=path):
                status, payload = self.json_call("POST", path, body={}, send_token=False)
                self.assertEqual(status, 401)
                self.assertEqual(payload, {"error": "unauthorized"})

    def test_a_wrong_token_of_a_different_length_is_an_indistinguishable_401(self) -> None:
        """No status, body or length may differ by *how* wrong the token was."""
        missing = self.call("GET", "/api/state", send_token=False)
        for wrong in ("", "x", self.token[:-1], self.token + "x", "z" * 512, self.token.upper()):
            with self.subTest(length=len(wrong)):
                self.assertEqual(self.call("GET", "/api/state", token=wrong), missing)

    def test_a_non_ascii_token_header_is_rejected_rather_than_crashing(self) -> None:
        """``hmac.compare_digest`` raises on non-ASCII: that must read as 'wrong'."""
        status, payload = self.json_call("GET", "/api/state", token="tökén" * 8)
        self.assertEqual(status, 401)
        self.assertEqual(payload, {"error": "unauthorized"})

    def test_an_unknown_api_path_is_401_before_it_is_404(self) -> None:
        """An unauthenticated caller learns nothing about the route table."""
        status, payload = self.json_call("GET", "/api/does-not-exist", send_token=False)
        self.assertEqual(status, 401)
        self.assertEqual(payload, {"error": "unauthorized"})

        status, payload = self.json_call("GET", "/api/does-not-exist")
        self.assertEqual(status, 404)
        self.assertEqual(payload, {"error": "not found"})

    def test_an_oversized_body_is_refused_on_its_declared_length(self) -> None:
        """The body is never sent: refusing it must not cost the allocation."""
        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=10)
        try:
            conn.putrequest("POST", "/api/collect")
            conn.putheader("X-Agent-Token", self.token)
            conn.putheader("Content-Type", "application/json")
            conn.putheader("Content-Length", str(MAX_BODY_BYTES + 1))
            conn.endheaders()
            response = conn.getresponse()
            status, payload = response.status, json.loads(response.read())
        finally:
            conn.close()
        self.assertEqual(status, 413)
        self.assertEqual(payload, {"error": "request body too large"})

    def test_a_body_under_the_cap_still_reaches_the_handler(self) -> None:
        """The cap is a ceiling, not a blanket refusal of bodies."""
        note = "n" * 4096
        status, payload = self.json_call(
            "POST", "/api/approvals/deadbeef/decide", body={"approved": True, "note": note}
        )
        self.assertEqual(status, 404)
        self.assertEqual(payload, {"error": "unknown or already decided"})

    def test_a_malformed_body_is_a_400_not_a_500(self) -> None:
        for raw, expected in ((b"{not json", "invalid JSON body"), (b"[1, 2, 3]", "JSON object expected")):
            with self.subTest(body=raw):
                conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=10)
                try:
                    conn.request("POST", "/api/collect", body=raw,
                                 headers={"X-Agent-Token": self.token})
                    response = conn.getresponse()
                    status, payload = response.status, json.loads(response.read())
                finally:
                    conn.close()
                self.assertEqual(status, 400)
                self.assertEqual(payload, {"error": expected})

    def test_no_api_response_body_ever_contains_the_token(self) -> None:
        """The token grants approval and invoicing rights: it leaves in one place
        only — the console at startup — and never rides back out in a payload."""
        probes = [("GET", path, None) for path in ("/healthz", *GET_ROUTES)]
        probes += [
            ("POST", "/api/collect", {}),
            ("POST", "/api/invoices", {"sku": "market-report", "ref": self.token}),
            ("POST", "/api/gigs/nosuchgig/take", {}),
            ("POST", "/api/approvals/nosuch/decide", {"approved": False, "note": self.token}),
            ("POST", "/api/discovery/probe", {"url": self.token}),
        ]
        for method, path, body in probes:
            with self.subTest(route=f"{method} {path}"):
                _, raw = self.call(method, path, body=body)
                self.assertNotIn(self.token.encode(), raw)


class TestStaticFence(DashboardCase):
    def test_a_real_asset_is_served_with_an_explicit_content_type(self) -> None:
        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=10)
        try:
            conn.request("GET", "/static/app.js")
            response = conn.getresponse()
            status = response.status
            content_type = response.headers.get("Content-Type")
            nosniff = response.headers.get("X-Content-Type-Options")
            body = response.read()
        finally:
            conn.close()
        self.assertEqual(status, 200)
        self.assertEqual(content_type, "text/javascript; charset=utf-8")
        self.assertEqual(nosniff, "nosniff")
        self.assertTrue(body)

    def test_path_traversal_is_refused_in_every_encoding(self) -> None:
        for path in (
            "/static/../../../etc/passwd",
            "/static/%2e%2e/%2e%2e/%2e%2e/etc/passwd",
            "/static/%2e%2e%2f%2e%2e%2f%2e%2e%2fetc%2fpasswd",
            "/static/..%2f..%2f..%2fetc%2fpasswd",
            "/static//etc/passwd",
            "/static/....//....//etc/passwd",
            "/static/",
            "/%2e%2e/etc/passwd",
        ):
            with self.subTest(path=path):
                status, raw = self.call("GET", path, send_token=False)
                self.assertEqual(status, 404)
                self.assertNotIn(b"root:", raw)

    def test_the_fence_is_containment_not_a_ban_on_dot_segments(self) -> None:
        """Also the control for the case above: `..` really does reach the server
        unnormalised, so those 404s are the fence refusing, not the client tidying up."""
        status, raw = self.call("GET", "/static/../static/app.js", send_token=False)
        self.assertEqual(status, 200)
        self.assertTrue(raw)

    def test_a_static_asset_never_carries_the_token(self) -> None:
        """The file on disk keeps its placeholder; only the served index is patched."""
        _, raw = self.call("GET", "/static/index.html", send_token=False)
        self.assertIn(b"__AGENT_TOKEN__", raw)
        self.assertNotIn(self.token.encode(), raw)


class TestApiContract(DashboardCase):
    def test_state_has_every_key_in_the_contract(self) -> None:
        status, payload = self.json_call("GET", "/api/state")
        self.assertEqual(status, 200)
        for key in STATE_KEYS:
            self.assertIn(key, payload)
        self.assertIsInstance(payload["generated_at"], float)
        for key in TREASURY_KEYS:
            self.assertIsInstance(payload["treasury"][key], float)
        for key in WALLET_KEYS:
            self.assertIn(key, payload["wallet"])
        for key in LADDER_KEYS:
            self.assertIn(key, payload["ladder"])
        for key in COUNTS_KEYS:
            self.assertIsInstance(payload["counts"][key], int)
        self.assertIsInstance(payload["ledger"]["ok"], bool)
        self.assertIsInstance(payload["ledger"]["message"], str)

        self.assertTrue(payload["channels"], "the services channel should be reported")
        for name, channel in payload["channels"].items():
            with self.subTest(channel=name):
                for key in CHANNEL_KEYS:
                    self.assertIn(key, channel)
                for key in CALIBRATION_KEYS:
                    self.assertIn(key, channel["calibration"])
                self.assertIn(channel["calibration"]["verdict"],
                              ("proven", "promising", "unproven", "untried"))

    def test_a_fresh_ledger_reports_no_confirmed_earnings(self) -> None:
        """Nothing counts as earned until it is confirmed on-chain — and nothing has."""
        _, payload = self.json_call("GET", "/api/state")
        self.assertEqual(payload["treasury"]["confirmed_usdt"], 0.0)
        # Confirmed and expected travel as two fields all the way to the browser:
        # there is deliberately no combined total for the UI to reach for.
        self.assertEqual(set(payload["treasury"]), set(TREASURY_KEYS))

    def test_collect_on_a_fresh_ledger_confirms_nothing(self) -> None:
        status, payload = self.json_call("POST", "/api/collect", body={})
        self.assertEqual(status, 200)
        for key in COLLECT_KEYS:
            self.assertIn(key, payload)
        self.assertEqual(payload["confirmed_usdt"], 0.0)
        self.assertEqual(payload["treasury_usdt"], 0.0)
        self.assertEqual(payload["new_transfers"], 0)
        self.assertEqual(payload["matched"], 0)

    def test_gigs_reports_rows_and_a_per_channel_error_map(self) -> None:
        status, payload = self.json_call("GET", "/api/gigs?limit=5")
        self.assertEqual(status, 200)
        self.assertIsInstance(payload["gigs"], list)
        self.assertIsInstance(payload["errors"], dict)

    def test_taking_an_unknown_gig_is_404(self) -> None:
        status, payload = self.json_call("POST", "/api/gigs/nosuchgig/take", body={})
        self.assertEqual(status, 404)
        self.assertEqual(payload, {"error": "unknown gig"})

    def test_taking_a_stored_gig_returns_the_documented_order_shape(self) -> None:
        gig = Gig(
            channel="services", external_id="test-web-take", title="invoice — market report",
            reward_usdt=5.0, effort_hours=0.1, payout_probability=0.25, source="invoice",
        )
        with self.ctx.lock:
            self.ctx.store.upsert_gigs([gig])

        status, payload = self.json_call("POST", f"/api/gigs/{gig.id}/take", body={})
        self.assertEqual(status, 200)
        order = payload["order"]
        for key in ("id", "title", "plan", "status", "reward_usdt", "estimated_hours", "autonomy"):
            self.assertIn(key, order)
        self.assertIsInstance(order["plan"], list)
        self.assertAlmostEqual(order["reward_usdt"], 5.0)
        # Selling a report needs nobody's permission, so nothing is queued.
        self.assertEqual(order["status"], "submitted")
        self.assertEqual(order["autonomy"], "auto")
        self.assertIsNone(payload["approval_id"])

    def test_an_approval_can_be_decided_once_and_only_once(self) -> None:
        with self.ctx.lock:
            approval_id = self.ctx.store.request_approval(
                "work_order", "[bounties] claim a bounty in your name", subject_id=""
            )
            self.ctx.invalidate_state()

        status, payload = self.json_call("GET", "/api/approvals")
        self.assertEqual(status, 200)
        self.assertIn(approval_id, [row["id"] for row in payload["approvals"]])
        for row in payload["approvals"]:
            for key in ("id", "kind", "subject_id", "channel", "title", "detail", "status",
                        "created_ts"):
                self.assertIn(key, row)

        status, payload = self.json_call(
            "POST", f"/api/approvals/{approval_id}/decide", body={"approved": True, "note": "go"}
        )
        self.assertEqual(status, 200)
        self.assertEqual(payload, {"ok": True, "approved": True})

        # A decided approval is gone from the queue and cannot be decided again.
        _, payload = self.json_call("GET", "/api/approvals")
        self.assertNotIn(approval_id, [row["id"] for row in payload["approvals"]])
        status, payload = self.json_call(
            "POST", f"/api/approvals/{approval_id}/decide", body={"approved": False, "note": ""}
        )
        self.assertEqual(status, 404)
        self.assertEqual(payload, {"error": "unknown or already decided"})

    def test_an_unknown_sku_is_404(self) -> None:
        status, payload = self.json_call("POST", "/api/invoices", body={"sku": "nope", "ref": ""})
        self.assertEqual(status, 404)
        self.assertEqual(payload, {"error": "unknown sku"})

    def test_no_receiving_address_is_503_not_a_bad_request(self) -> None:
        """A missing address is a configuration fact; the customer asked correctly."""
        status, payload = self.json_call(
            "POST", "/api/invoices", body={"sku": "market-report", "ref": "customer-1"}
        )
        self.assertEqual(status, 503)
        self.assertEqual(payload, {"error": "no receiving address"})

    def test_the_catalogue_is_priced_even_without_a_wallet(self) -> None:
        status, payload = self.json_call("GET", "/api/catalogue")
        self.assertEqual(status, 200)
        self.assertTrue(payload["catalogue"])
        for offer in payload["catalogue"]:
            for key in ("sku", "title", "price_usdt", "description"):
                self.assertIn(key, offer)
        self.assertEqual(payload["receiving"], {"chain": "", "address": ""})

    def test_ledger_and_trading_panels_report_an_intact_journal(self) -> None:
        status, payload = self.json_call("GET", "/api/ledger?limit=10")
        self.assertEqual(status, 200)
        self.assertIs(payload["integrity"]["ok"], True)
        self.assertLessEqual(len(payload["events"]), 10)
        for event in payload["events"]:
            self.assertEqual(set(event), {"seq", "ts", "kind", "payload"})

        status, payload = self.json_call("GET", "/api/trading")
        self.assertEqual(status, 200)
        for key in ("strategies", "equity", "totals"):
            self.assertIn(key, payload)
        self.assertIsInstance(payload["equity"], list)

    def test_discovery_lists_candidates_without_probing_them(self) -> None:
        status, payload = self.json_call("GET", "/api/discovery")
        self.assertEqual(status, 200)
        self.assertTrue(payload["sources"])
        for source in payload["sources"]:
            self.assertIn("url", source)
            self.assertIs(source["probed"], False)

    def test_a_probe_of_a_non_http_url_is_refused_before_any_request(self) -> None:
        for url, expected in (("", "url is required"),
                              ("ftp://example.invalid/x", "url must start with http:// or https://")):
            with self.subTest(url=url):
                status, payload = self.json_call("POST", "/api/discovery/probe", body={"url": url})
                self.assertEqual(status, 400)
                self.assertEqual(payload, {"error": expected})


class TestExpectedIsNotIncome(DashboardCase):
    """With a receiving address configured, invoices can be minted — and must not count."""

    config_toml = WITH_WALLET

    def test_an_issued_invoice_is_pipeline_never_income(self) -> None:
        status, payload = self.json_call(
            "POST", "/api/invoices", body={"sku": "market-report", "ref": "customer-1"}
        )
        self.assertEqual(status, 200)
        invoice = payload["invoice"]
        self.assertEqual(set(invoice), {"invoice_id", "sku", "chain", "pay_to", "pay_exactly_usdt",
                                        "status", "expires_ts", "note"})
        self.assertEqual(invoice["status"], "unpaid")
        self.assertEqual(invoice["chain"], "tron")
        self.assertEqual(invoice["pay_to"], "TTestOnlyAddressNobodyEverFunded")
        self.assertGreaterEqual(invoice["pay_exactly_usdt"], 5.0)

        status, payload = self.json_call("GET", "/api/invoices")
        self.assertEqual(status, 200)
        self.assertIn(invoice["invoice_id"], [i["invoice_id"] for i in payload["invoices"]])

        # Collecting registers the invoice as an expectation and reconciles the
        # chain. Nobody paid, so the confirmed total may not move a cent.
        status, payload = self.json_call("POST", "/api/collect", body={})
        self.assertEqual(status, 200)
        self.assertEqual(payload["confirmed_usdt"], 0.0)
        self.assertEqual(payload["treasury_usdt"], 0.0)

        _, state = self.json_call("GET", "/api/state")
        self.assertEqual(state["treasury"]["confirmed_usdt"], 0.0)
        self.assertGreaterEqual(state["treasury"]["expected_usdt"], 5.0)
        services = state["channels"]["services"]
        self.assertEqual(services["confirmed_usdt"], 0.0)
        self.assertGreaterEqual(services["expected_usdt"], 5.0)
        self.assertEqual(services["calibration"]["verdict"], "untried")

    def test_the_catalogue_publishes_the_receiving_address(self) -> None:
        status, payload = self.json_call("GET", "/api/catalogue")
        self.assertEqual(status, 200)
        self.assertEqual(
            payload["receiving"], {"chain": "tron", "address": "TTestOnlyAddressNobodyEverFunded"}
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
