#!/usr/bin/env python3
"""Assemble the dashboard into one double-clickable HTML file.

The server-backed dashboard needs a running agent on loopback, which is exactly
what you cannot hand somebody in a link. This bundles the *real* page — the same
markup, the same stylesheet, the same ``app.js``, byte for byte — and puts a
small in-page router where ``fetch`` used to be, seeded with a plausible run.

Two rules make this honest rather than a mock-up:

* the UI code is not modified, so what you click is what ships;
* the numbers are visibly, permanently labelled as sample data, because a
  project whose whole premise is "nothing counts until it is confirmed on-chain"
  cannot hand out a page where invented figures look like income.

Usage:
    python3 tools/build_standalone_dashboard.py [output.html]
    python3 tools/build_standalone_dashboard.py --artifact [output.html]

``--artifact`` emits the same page as a document *fragment* for hosts that supply
their own ``<html>``/``<head>``/``<body>`` skeleton, and rewires the theming: such
a host stamps ``data-theme`` on the root when the viewer picks a mode explicitly,
which a bare ``prefers-color-scheme`` query would ignore.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STATIC = ROOT / "src" / "usdt_agent" / "web" / "static"
DEFAULT_OUT = ROOT / "docs" / "dashboard.html"

# --------------------------------------------------------------------------
# The seeded run
# --------------------------------------------------------------------------
# A believable third week: a couple of bounties paid, one invoice settled, one
# claim waiting on a human, and a treasury still short of the yield floor.

NOW = 1_770_000_000.0
HOUR = 3600.0
DAY = 86_400.0

CHANNELS = {
    "bounties": {
        "name": "bounties",
        "description": "Bounty-labelled issues and crypto-paying tasks, ranked by USDT/hour",
        "autonomy": "assisted", "capital_required_usdt": 0.0, "typical_lag_days": 14.0,
        "ready": True, "blockers": [], "requirements": ["GITHUB_TOKEN (optional)", "wallet (required)"],
        "confirmed_usdt": 620.0, "expected_usdt": 450.0,
        "calibration": {"orders": 5, "paid": 3, "conversion": 0.6,
                        "realized_usdt_per_hour": 41.33, "effort_calibration": 1.36,
                        "verdict": "proven"},
    },
    "services": {
        "name": "services",
        "description": "Sells a priced micro-service for USDT, settled directly on-chain",
        "autonomy": "auto", "capital_required_usdt": 0.0, "typical_lag_days": 0.0,
        "ready": True, "blockers": [], "requirements": ["wallet (required)"],
        "confirmed_usdt": 20.13, "expected_usdt": 5.02,
        "calibration": {"orders": 4, "paid": 4, "conversion": 1.0,
                        "realized_usdt_per_hour": 120.78, "effort_calibration": 1.0,
                        "verdict": "proven"},
    },
    "affiliate": {
        "name": "affiliate",
        "description": "Tracks referral/affiliate programs and proves payouts on-chain",
        "autonomy": "assisted", "capital_required_usdt": 0.0, "typical_lag_days": 30.0,
        "ready": False,
        "blockers": ["affiliate.programs: At least one referral program declared in the config"],
        "requirements": ["wallet (required)", "affiliate.programs (required)"],
        "confirmed_usdt": 0.0, "expected_usdt": 0.0,
        "calibration": {"orders": 0, "paid": 0, "conversion": 0.0,
                        "realized_usdt_per_hour": 0.0, "effort_calibration": 0.0,
                        "verdict": "untried"},
    },
    "passive": {
        "name": "passive",
        "description": "Deploys confirmed treasury USDT into stablecoin yield (needs capital)",
        "autonomy": "assisted", "capital_required_usdt": 200.0, "typical_lag_days": 1.0,
        "ready": True, "blockers": [],
        "requirements": ["wallet (required)", "treasury.capital (required)"],
        "confirmed_usdt": 0.0, "expected_usdt": 0.0,
        "calibration": {"orders": 1, "paid": 0, "conversion": 0.0,
                        "realized_usdt_per_hour": 0.0, "effort_calibration": 0.0,
                        "verdict": "unproven"},
    },
}

LADDER = {
    "stage": "compound", "completed": 5, "total": 6,
    "stages": [
        {"key": "wallet", "title": "Have somewhere to be paid", "done": True, "actions": []},
        {"key": "channel", "title": "Get at least one channel out of the blocked state",
         "done": True, "actions": []},
        {"key": "pipeline", "title": "Put real work in flight", "done": True, "actions": []},
        {"key": "first_payout", "title": "Confirm the first payout on-chain", "done": True,
         "actions": []},
        {"key": "capital", "title": "Accumulate 200 USDT so yield is worth switching on",
         "done": True, "actions": []},
        {"key": "compound", "title": "Let earned capital work alongside earned income",
         "done": False,
         "actions": ["usdt-agent earn run       # earning loop",
                     "usdt-agent run            # yield/trading loop, paper first"]},
    ],
}

GIGS = [
    {"id": "a3f19c2b7d4e5061", "channel": "bounties", "source": "github",
     "external_id": "412", "title": "Fix flaky retry loop in the scheduler",
     "url": "https://github.com/acme/core/issues/412", "reward_usdt": 450.0,
     "effort_hours": 4.0, "payout_probability": 0.62, "usdt_per_hour": 69.75,
     "deadline_ts": 0, "discovered_ts": NOW - 4 * HOUR, "meta": {"repo": "acme/core"}},
    {"id": "b7c2e91a4f60d385", "channel": "bounties", "source": "github",
     "external_id": "88", "title": "Add pagination to the public REST API",
     "url": "https://github.com/acme/api/issues/88", "reward_usdt": 180.0,
     "effort_hours": 2.5, "payout_probability": 0.71, "usdt_per_hour": 51.12,
     "deadline_ts": 0, "discovered_ts": NOW - 9 * HOUR, "meta": {"repo": "acme/api"}},
    {"id": "c1d8054e6a2b9f37", "channel": "bounties", "source": "github",
     "external_id": "1204", "title": "Port the CLI config loader to tomllib",
     "url": "https://github.com/acme/tools/issues/1204", "reward_usdt": 120.0,
     "effort_hours": 1.5, "payout_probability": 0.68, "usdt_per_hour": 54.40,
     "deadline_ts": 0, "discovered_ts": NOW - 20 * HOUR, "meta": {"repo": "acme/tools"}},
    {"id": "d5e4b83f210c6a79", "channel": "bounties", "source": "github",
     "external_id": "9", "title": "Audit the signature verification path",
     "url": "https://github.com/acme/sec/issues/9", "reward_usdt": 2500.0,
     "effort_hours": 16.0, "payout_probability": 0.18, "usdt_per_hour": 28.13,
     "deadline_ts": NOW + 11 * DAY, "discovered_ts": NOW - 2 * DAY,
     "meta": {"repo": "acme/sec"}},
    {"id": "e9a07c1b35d2f846", "channel": "bounties", "source": "github",
     "external_id": "57", "title": "Rewrite the storage layer for multi-tenant use",
     "url": "https://github.com/acme/db/issues/57", "reward_usdt": 3200.0,
     "effort_hours": 40.0, "payout_probability": 0.22, "usdt_per_hour": 17.60,
     "deadline_ts": 0, "discovered_ts": NOW - 3 * DAY, "meta": {"repo": "acme/db"}},
]

APPROVALS = [
    {"id": "7f2c9d1e40b6a583", "kind": "work_order", "subject_id": "ord-88",
     "channel": "bounties", "status": "pending", "created_ts": NOW - 2 * HOUR,
     "title": "[bounties] Add pagination to the public REST API",
     "detail": ("https://github.com/acme/api/issues/88\n"
                "reward 180.00 USDT · est 2.5 h · 51 USDT/h · payout odds 71%\n"
                "plan:\n"
                "  - read the issue and acme/api's CONTRIBUTING guide\n"
                "  - confirm the bounty is unclaimed and comment to claim it\n"
                "  - reproduce the problem locally, write a failing test\n"
                "  - implement the fix and make the test pass\n"
                "  - open a PR referencing the issue, link the bounty\n"
                "  - expect payment to tron after merge")},
]

CATALOGUE = [
    {"sku": "market-report", "title": "Stablecoin yield report", "price_usdt": 5.0,
     "description": "Current risk-adjusted stablecoin yields across chains, ranked."},
    {"sku": "arb-scan", "title": "Cross-venue spread scan", "price_usdt": 3.0,
     "description": "Live cross-exchange spreads for the majors, net of fees."},
]

INVOICES = [
    {"invoice_id": "441d21535502a998", "sku": "market-report", "chain": "tron",
     "pay_to": "TMuA6YqfCeX8EhbfYEg5y7S4DqzSJireY9", "pay_exactly_usdt": 5.0214,
     "status": "unpaid", "expires_ts": NOW + 4 * HOUR,
     "note": "send exactly this amount — the cents identify your invoice"},
    {"invoice_id": "9b0e77a1c3d45e02", "sku": "arb-scan", "chain": "tron",
     "pay_to": "TMuA6YqfCeX8EhbfYEg5y7S4DqzSJireY9", "pay_exactly_usdt": 3.0091,
     "status": "paid", "expires_ts": NOW - 20 * HOUR,
     "note": "send exactly this amount — the cents identify your invoice"},
]

LEDGER_EVENTS = [
    {"seq": 214, "ts": NOW - 3 * HOUR, "kind": "income",
     "payload": {"channel": "bounties", "amount_usdt": 450.0, "chain": "tron",
                 "tx": "8b5e5f9a99d65c0b3c17f2a4", "gig": "a3f19c2b7d4e5061"}},
    {"seq": 213, "ts": NOW - 3 * HOUR, "kind": "approval",
     "payload": {"id": "5c1a...", "approved": True, "note": "looks tractable"}},
    {"seq": 211, "ts": NOW - 2 * DAY, "kind": "income",
     "payload": {"channel": "services", "amount_usdt": 3.0091, "chain": "tron",
                 "tx": "d41c9a7e0b62f5138ea4", "gig": ""}},
    {"seq": 208, "ts": NOW - 5 * DAY, "kind": "income",
     "payload": {"channel": "bounties", "amount_usdt": 120.0, "chain": "tron",
                 "tx": "0f7742ab91c3de5608bb", "gig": "c1d8054e6a2b9f37"}},
    {"seq": 202, "ts": NOW - 9 * DAY, "kind": "earn_baseline",
     "payload": {"chain": "tron", "opening_balance": 3.1, "transfers_ignored": 50}},
    {"seq": 201, "ts": NOW - 9 * DAY, "kind": "earn_start",
     "payload": {"channels": ["affiliate", "bounties", "passive", "services"],
                 "chains": ["tron"]}},
]

SOURCES = [
    {"url": "https://console.algora.io/api/bounties", "host": "algora.io",
     "status": "ok", "http_status": 200, "content_type": "application/json",
     "item_count": 48, "robots_allowed": True, "elapsed_ms": 412,
     "probed_ts": NOW - 6 * HOUR,
     "suggested_mapping": {"external_id": "$.items[].id", "title": "$.items[].task.title",
                           "url": "$.items[].task.url", "reward_usdt": "$.items[].reward.amount",
                           "confidence": 0.86},
     "schema": {"$.items[].id": "str", "$.items[].task.title": "str",
                "$.items[].reward.amount": "int"},
     "sample": {"id": "bty_2f…", "task": {"title": "Add retry budget"},
                "reward": {"amount": 25000}},
     "notes": ["robots.txt fetched and parsed", "algora.io resolves to 76.76.21.21",
               "socket pinned to 76.76.21.21"]},
    {"url": "https://example-board.test/api/v1/tasks", "host": "example-board.test",
     "status": "blocked", "http_status": 403, "content_type": "text/html",
     "item_count": 0, "robots_allowed": True, "elapsed_ms": 233,
     "probed_ts": NOW - 5 * HOUR, "suggested_mapping": {}, "schema": {}, "sample": None,
     "notes": ["HTTP 403 — an access control said no", "not retried, and not worked around",
               "host marked blocked for the rest of this run"]},
    {"url": "https://gitcoin.co/api/v0.1/bounties/", "host": "gitcoin.co",
     "status": "disallowed", "http_status": 0, "content_type": "",
     "item_count": 0, "robots_allowed": False, "elapsed_ms": 96,
     "probed_ts": NOW - 5 * HOUR, "suggested_mapping": {}, "schema": {}, "sample": None,
     "notes": ["robots.txt disallows /api/ for usdt-agent", "the target was never requested"]},
]

EQUITY = [[NOW - (30 - i) * DAY, round(1000 + 36 * (i / 30) ** 0.9 + 3.5 * ((i * 7) % 11 - 5) / 5, 4)]
          for i in range(31)]

STATE = {
    "generated_at": NOW,
    "treasury": {"confirmed_usdt": 640.13, "expected_usdt": 455.02, "hours_spent": 15.0},
    "wallet": {
        "addresses": {"tron": "TMuA6YqfCeX8EhbfYEg5y7S4DqzSJireY9",
                      "bsc": "0x8894E0a0c962CB723c1976a4421c95949bE2D4E3"},
        "balances": {"tron": 643.23, "bsc": 0.0},
        "total_usdt": 643.23,
        "errors": {"bsc": "all RPCs failed for bsc: timed out"},
    },
    "ladder": LADDER,
    "channels": CHANNELS,
    "counts": {"gigs": len(GIGS), "open_orders": 2, "pending_approvals": len(APPROVALS),
               "transfers_seen": 6},
    "ledger": {"ok": True, "message": "journal intact: 214 entries, head=c969e094a9e2"},
}

TRADING = {
    "strategies": {
        "cross_venue": {"n": 99.0, "pnl": 17.7792, "avg_bps": 11.22, "turnover": 158_400.0,
                        "win_rate": 0.98},
        "triangular": {"n": 145.0, "pnl": 20.7873, "avg_bps": 10.80, "turnover": 192_300.0,
                       "win_rate": 0.97},
        "stable_yield": {"n": 3.0, "pnl": 0.1889, "avg_bps": 7.29, "turnover": 2_590.0,
                         "win_rate": 1.0},
        "grid": {"n": 12.0, "pnl": -0.6426, "avg_bps": -12.38, "turnover": 5_190.0,
                 "win_rate": 0.5},
        "funding_carry": {"n": 29.0, "pnl": -1.6379, "avg_bps": -18.03, "turnover": 9_080.0,
                          "win_rate": 0.03},
    },
    "equity": EQUITY,
    "totals": {"closed_trades": 288.0, "realized_pnl": 36.4749, "costs": 84.1702,
               "gross": 120.6451},
}

SEED = {
    "state": STATE, "gigs": GIGS, "approvals": APPROVALS, "catalogue": CATALOGUE,
    "invoices": INVOICES, "events": LEDGER_EVENTS, "sources": SOURCES, "trading": TRADING,
    "receiving": {"chain": "tron", "address": "TMuA6YqfCeX8EhbfYEg5y7S4DqzSJireY9"},
}

# --------------------------------------------------------------------------
# The in-page API
# --------------------------------------------------------------------------

MOCK_JS = """
/* ------------------------------------------------------------------------
 * Offline API for the standalone build.
 *
 * app.js below is the shipped file, unmodified — it still calls fetch() and
 * still expects the documented contract. Only the wire is replaced. Writes
 * mutate this in-memory copy, so Take / Approve / Create invoice / Collect
 * behave the way they do against a live agent.
 * --------------------------------------------------------------------- */
(function () {
  "use strict";
  var D = window.__DEMO__;
  var drift = Date.now() / 1000 - D.state.generated_at;   // present the seed as "now"

  function shift(ts) { return ts ? ts + drift : ts; }

  D.state.generated_at += drift;
  D.gigs.forEach(function (g) {
    g.discovered_ts = shift(g.discovered_ts);
    g.deadline_ts = shift(g.deadline_ts);
  });
  D.approvals.forEach(function (a) { a.created_ts = shift(a.created_ts); });
  D.invoices.forEach(function (i) { i.expires_ts = shift(i.expires_ts); });
  D.events.forEach(function (e) { e.ts = shift(e.ts); });
  D.sources.forEach(function (s) { s.probed_ts = shift(s.probed_ts); });
  D.trading.equity = D.trading.equity.map(function (p) { return [shift(p[0]), p[1]]; });

  function ok(payload) {
    return Promise.resolve(new Response(JSON.stringify(payload), {
      status: 200, headers: { "Content-Type": "application/json" },
    }));
  }
  function fail(status, message) {
    return Promise.resolve(new Response(JSON.stringify({ error: message }), {
      status: status, headers: { "Content-Type": "application/json" },
    }));
  }

  function uid() {
    var s = "";
    for (var i = 0; i < 16; i++) { s += "0123456789abcdef"[(Math.random() * 16) | 0]; }
    return s;
  }

  function route(path, method, body) {
    var url = new URL(path, "http://demo.local");
    var p = url.pathname;
    var q = url.searchParams;

    if (p === "/api/state") {
      D.state.generated_at = Date.now() / 1000;
      D.state.counts.gigs = D.gigs.length;
      D.state.counts.pending_approvals = D.approvals.filter(function (a) {
        return a.status === "pending";
      }).length;
      return ok(D.state);
    }
    if (p === "/api/gigs" && method === "GET") {
      var channel = q.get("channel") || "";
      var limit = parseInt(q.get("limit") || "50", 10);
      var rows = D.gigs.filter(function (g) { return !channel || g.channel === channel; });
      return ok({ gigs: rows.slice(0, limit), errors: {} });
    }
    if (method === "POST" && /^\\/api\\/gigs\\/[^/]+\\/take$/.test(p)) {
      var gigId = p.split("/")[3];
      var gig = D.gigs.filter(function (g) { return g.id === gigId; })[0];
      if (!gig) { return fail(404, "unknown gig"); }
      var approvalId = uid();
      D.approvals.unshift({
        id: approvalId, kind: "work_order", subject_id: "ord-" + gigId.slice(0, 6),
        channel: gig.channel, status: "pending", created_ts: Date.now() / 1000,
        title: "[" + gig.channel + "] " + gig.title,
        detail: gig.url + "\\nreward " + gig.reward_usdt.toFixed(2) + " USDT · est "
                + gig.effort_hours.toFixed(1) + " h · " + Math.round(gig.usdt_per_hour)
                + " USDT/h · payout odds " + Math.round(gig.payout_probability * 100) + "%"
                + "\\nplan:\\n  - claim the bounty\\n  - write a failing test\\n"
                + "  - implement and open a PR\\n  - await payment on-chain",
      });
      D.state.counts.open_orders += 1;
      return ok({
        order: {
          id: "ord-" + gigId.slice(0, 6), title: gig.title,
          plan: ["claim the bounty", "write a failing test", "implement and open a PR",
                 "await payment on-chain"],
          status: "awaiting_approval", reward_usdt: gig.reward_usdt,
          estimated_hours: gig.effort_hours, autonomy: "assisted",
        },
        approval_id: approvalId,
      });
    }
    if (p === "/api/approvals" && method === "GET") {
      return ok({ approvals: D.approvals.filter(function (a) { return a.status === "pending"; }) });
    }
    if (method === "POST" && /^\\/api\\/approvals\\/[^/]+\\/decide$/.test(p)) {
      var id = p.split("/")[3];
      var found = D.approvals.filter(function (a) {
        return a.id === id && a.status === "pending";
      })[0];
      if (!found) { return fail(404, "unknown or already decided"); }
      found.status = body && body.approved ? "approved" : "rejected";
      return ok({ ok: true, approved: !!(body && body.approved) });
    }
    if (p === "/api/catalogue") { return ok({ catalogue: D.catalogue, receiving: D.receiving }); }
    if (p === "/api/invoices" && method === "GET") { return ok({ invoices: D.invoices }); }
    if (p === "/api/invoices" && method === "POST") {
      var sku = body && body.sku;
      var offer = D.catalogue.filter(function (c) { return c.sku === sku; })[0];
      if (!offer) { return fail(404, "unknown sku"); }
      var cents = Math.floor(Math.random() * 500) / 10000;
      var invoice = {
        invoice_id: uid(), sku: sku, chain: D.receiving.chain,
        pay_to: D.receiving.address,
        pay_exactly_usdt: Math.round((offer.price_usdt + cents) * 10000) / 10000,
        status: "unpaid", expires_ts: Date.now() / 1000 + 6 * 3600,
        note: "send exactly this amount — the cents identify your invoice",
      };
      D.invoices.unshift(invoice);
      D.state.treasury.expected_usdt += invoice.pay_exactly_usdt;
      D.state.channels.services.expected_usdt += invoice.pay_exactly_usdt;
      return ok({ invoice: invoice });
    }
    if (p === "/api/collect" && method === "POST") {
      // An honest demo: reconciliation against a chain with nothing new on it
      // returns zero. That is the ordinary result, and the UI should show it.
      return ok({
        confirmed_usdt: 0.0, new_transfers: 0, matched: 0, unattributed_usdt: 0.0,
        expired: 0, delta_detected: {}, balances: D.state.wallet.balances,
        errors: D.state.wallet.errors, baselined: {},
        treasury_usdt: D.state.treasury.confirmed_usdt,
      });
    }
    if (p === "/api/trading") { return ok(D.trading); }
    if (p === "/api/ledger") {
      var n = parseInt(q.get("limit") || "50", 10);
      return ok({ events: D.events.slice(0, n), integrity: D.state.ledger });
    }
    if (p === "/api/discovery") { return ok({ sources: D.sources }); }
    if (p === "/api/discovery/probe" && method === "POST") {
      var target = (body && body.url) || "";
      var host = "";
      try { host = new URL(target).hostname; } catch (e) { return fail(400, "malformed URL"); }
      var isPrivate = /^(127\\.|10\\.|192\\.168\\.|169\\.254\\.|\\[?::1)/.test(host);
      var probe = {
        url: target, host: host,
        status: isPrivate ? "refused" : "unreachable",
        http_status: 0, content_type: "", item_count: 0,
        robots_allowed: !isPrivate, elapsed_ms: 8,
        suggested_mapping: {}, schema: {}, sample: null,
        probed_ts: Date.now() / 1000,
        notes: isPrivate
          ? ["SSRF guard: " + host + " resolves to a private address", "nothing was sent"]
          : ["this standalone build has no network — a live agent would probe it now"],
      };
      D.sources.unshift(probe);
      return ok({ probe: probe });
    }
    return fail(404, "not found");
  }

  window.fetch = function (path, init) {
    init = init || {};
    var method = (init.method || "GET").toUpperCase();
    var body = null;
    if (init.body) { try { body = JSON.parse(init.body); } catch (e) { body = null; } }
    // A little latency, so buttons show their pending state as they really do.
    return new Promise(function (resolve) {
      setTimeout(function () { resolve(route(String(path), method, body)); }, 90);
    });
  };
})();
"""

DEMO_BANNER = """<div class="demo-banner" role="note">
  <strong>Sample data.</strong>
  This is the real dashboard with a seeded run behind it, so every tab and button works —
  but no agent is running and none of these figures are anyone's money.
  Run <code>usdt-agent web</code> to point it at a live agent.
</div>
"""

DEMO_CSS = """
/* ---- standalone build only ------------------------------------------- */
/* No server, so no session to negotiate: the token banner has nothing to do. */
#banner-token { display: none !important; }

.demo-banner {
  position: sticky; top: 0; z-index: 60;
  display: flex; flex-wrap: wrap; gap: .5ch;
  align-items: baseline; justify-content: center;
  padding: .55rem 1rem;
  background: var(--warn-soft);
  border-bottom: 1px solid var(--warn);
  color: var(--text);
  font-size: .82rem; line-height: 1.45;
  text-align: center;
}
.demo-banner strong { color: var(--warn); letter-spacing: .02em; }
.demo-banner code {
  font-family: var(--mono, ui-monospace, monospace);
  background: var(--bg-sunken); border: 1px solid var(--line);
  border-radius: 4px; padding: .05rem .4rem;
}
@media (max-width: 640px) { .demo-banner { font-size: .76rem; } }
"""


def theme_for_stamped_host(css: str) -> str:
    """Make the light palette respond to an explicit ``data-theme`` choice.

    The stylesheet is dark-first: ``:root`` carries the dark tokens and one
    media query carries the light ones. A host that stamps ``data-theme`` has
    three states, not two, so the media query is narrowed to lose against an
    explicit dark choice, and the light tokens are repeated behind
    ``[data-theme="light"]`` so the toggle wins against a dark OS.
    """
    light = re.search(
        r"@media \(prefers-color-scheme: light\) \{\s*:root \{(.*?)\n  \}\n\}",
        css, re.S,
    )
    if light is None:
        raise SystemExit("could not find the light palette block in styles.css")

    css = css.replace(
        "@media (prefers-color-scheme: light) {\n  :root {",
        '@media (prefers-color-scheme: light) {\n  :root:not([data-theme="dark"]) {',
        1,
    )
    css = css.replace(
        "@media (prefers-color-scheme: light) { .pip { color: #fff; } }",
        '@media (prefers-color-scheme: light) { :root:not([data-theme="dark"]) .pip { color: #fff; } }\n'
        ':root[data-theme="light"] .pip { color: #fff; }',
        1,
    )
    return css + (
        "\n/* An explicit light choice must beat a dark OS preference. */\n"
        ':root[data-theme="light"] {' + light.group(1) + "\n}\n"
    )


def build(out_path: Path, fragment: bool = False) -> Path:
    markup = (STATIC / "index.html").read_text(encoding="utf-8")
    css = (STATIC / "styles.css").read_text(encoding="utf-8")
    app = (STATIC / "app.js").read_text(encoding="utf-8")

    if '<link rel="stylesheet" href="/static/styles.css">' not in markup:
        raise SystemExit("index.html no longer links styles.css the expected way")
    if '<script src="/static/app.js" defer></script>' not in markup:
        raise SystemExit("index.html no longer loads app.js the expected way")

    if fragment:
        css = theme_for_stamped_host(css)
    markup = markup.replace(
        '<link rel="stylesheet" href="/static/styles.css">',
        f"<style>\n{css}\n{DEMO_CSS}</style>",
    )
    seed = json.dumps(SEED, separators=(",", ":"))
    markup = markup.replace(
        '<script src="/static/app.js" defer></script>',
        f"<script>window.__DEMO__ = {seed};</script>\n"
        f"<script>{MOCK_JS}</script>\n"
        f"<script>\n{app}\n</script>",
    )
    markup = markup.replace("<body>", "<body>\n" + DEMO_BANNER, 1)
    markup = markup.replace(
        "<title>usdt-agent</title>",
        "<title>usdt-agent — operator dashboard</title>", 1)

    if fragment:
        # The host supplies the document; hand it only what goes inside.
        markup = re.sub(r"<!doctype html>\s*", "", markup, flags=re.IGNORECASE)
        markup = re.sub(r"</?(?:html|head|body)\b[^>]*>", "", markup, flags=re.IGNORECASE)
        markup = re.sub(r'<meta\s+name="agent-token"[^>]*>\s*', "", markup, flags=re.IGNORECASE)
        markup = re.sub(r'<meta\s+charset[^>]*>\s*', "", markup, flags=re.IGNORECASE)
        markup = re.sub(r'<meta\s+name="viewport"[^>]*>\s*', "", markup, flags=re.IGNORECASE)
        markup = markup.strip() + "\n"

    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(markup, encoding="utf-8")
    return out_path


if __name__ == "__main__":
    args = sys.argv[1:]
    fragment = "--artifact" in args
    args = [a for a in args if a != "--artifact"]
    target = Path(args[0]) if args else DEFAULT_OUT
    written = build(target, fragment=fragment)
    print(f"{written}  ({written.stat().st_size / 1024:.0f} KiB)")
