/* usdt-agent operator console.
 *
 * Two invariants this file exists to protect:
 *
 * 1. Confirmed on-chain income and expected pipeline value are rendered by
 *    different code paths into different visual containers, and the pipeline
 *    always carries a "not income yet" tag. There is deliberately no helper
 *    that can sum them together.
 * 2. Everything the server hands us originated with a third party — gig
 *    titles, approval details, probe output, error strings. It reaches the DOM
 *    only through textContent or attribute setters, never as markup, and URLs
 *    are re-parsed and restricted to http/https before becoming an href.
 *
 * No framework, no build step, no network dependency beyond the agent itself.
 */

(function () {
  "use strict";

  var POLL_MS = 10000;
  var TAB_KEY = "usdt-agent.tab";
  var TABS = ["overview", "gigs", "approvals", "storefront", "trading", "ledger", "discovery"];

  var state = {
    token: "",
    active: "overview",
    lastOk: 0,
    offline: false,
    channels: [],
    gigs: [],
    gigSort: { key: "usdt_per_hour", dir: -1 },
  };

  // ---------------------------------------------------------------- storage

  function lsGet(key) {
    try { return window.localStorage.getItem(key) || ""; } catch (e) { return ""; }
  }

  function lsSet(key, value) {
    try { window.localStorage.setItem(key, value); } catch (e) { /* private mode */ }
  }

  // ------------------------------------------------------------ dom helpers

  function $(id) { return document.getElementById(id); }

  function el(tag, opts, kids) {
    var node = document.createElement(tag);
    opts = opts || {};
    if (opts.class) { node.className = opts.class; }
    if (opts.text !== undefined && opts.text !== null) { node.textContent = String(opts.text); }
    if (opts.title) { node.title = String(opts.title); }
    if (opts.attrs) {
      Object.keys(opts.attrs).forEach(function (k) { node.setAttribute(k, String(opts.attrs[k])); });
    }
    if (opts.data) {
      Object.keys(opts.data).forEach(function (k) { node.dataset[k] = String(opts.data[k]); });
    }
    if (opts.on) {
      Object.keys(opts.on).forEach(function (k) { node.addEventListener(k, opts.on[k]); });
    }
    (kids || []).forEach(function (kid) {
      if (kid) { node.appendChild(kid); }
    });
    return node;
  }

  function clear(node) { if (node) { node.replaceChildren(); } }

  function empty(message) { return el("p", { class: "empty", text: message }); }

  /* Only absolute http(s) URLs may become an href: a gig title is attacker
     controlled and so is the link beside it. */
  function safeUrl(raw) {
    if (typeof raw !== "string" || !raw) { return ""; }
    try {
      var u = new URL(raw);
      return (u.protocol === "http:" || u.protocol === "https:") ? u.href : "";
    } catch (e) { return ""; }
  }

  function link(url, label) {
    var href = safeUrl(url);
    if (!href) { return el("span", { text: label }); }
    return el("a", {
      text: label,
      attrs: { href: href, target: "_blank", rel: "noopener noreferrer nofollow" },
      title: href,
    });
  }

  // ------------------------------------------------------------ formatting

  function num(v) { var x = Number(v); return isFinite(x) ? x : 0; }

  function money(v, dp) {
    dp = dp === undefined ? 2 : dp;
    return num(v).toLocaleString(undefined, { minimumFractionDigits: dp, maximumFractionDigits: dp });
  }

  /* Invoice amounts must be shown to the cent that identifies them. */
  function exact(v) { return String(Number(num(v).toFixed(6))); }

  function pct(v, dp) {
    dp = dp === undefined ? 0 : dp;
    return (num(v) * 100).toFixed(dp) + "%";
  }

  function count(v) { return String(Math.round(num(v))); }

  function whenLocal(ts) {
    var t = num(ts);
    if (t <= 0) { return "—"; }
    var d = new Date(t * 1000);
    return d.toLocaleString(undefined, {
      year: "numeric", month: "2-digit", day: "2-digit",
      hour: "2-digit", minute: "2-digit", second: "2-digit",
    });
  }

  function duration(seconds) {
    var s = Math.max(0, Math.floor(seconds));
    var d = Math.floor(s / 86400);
    var h = Math.floor((s % 86400) / 3600);
    var m = Math.floor((s % 3600) / 60);
    if (d > 0) { return d + " d " + h + " h"; }
    if (h > 0) { return h + " h " + String(m).padStart(2, "0") + " m"; }
    if (m > 0) { return m + " m " + String(s % 60).padStart(2, "0") + " s"; }
    return s + " s";
  }

  function ago(ts) {
    var t = num(ts);
    if (t <= 0) { return "never"; }
    return duration(Date.now() / 1000 - t) + " ago";
  }

  // ------------------------------------------------------------------- api

  function ApiError(status, message) {
    this.name = "ApiError";
    this.status = status;
    this.message = message || ("HTTP " + status);
  }
  ApiError.prototype = Object.create(Error.prototype);

  function api(path, options) {
    options = options || {};
    // No token header by default: the session rides in an HttpOnly cookie the
    // server set when the launch link was opened, so this page never holds a
    // secret that an XSS could read. The header is only used by the manual
    // escape hatch below, for someone who pasted a token by hand.
    var init = {
      method: options.method || "GET",
      headers: { "Accept": "application/json" },
      cache: "no-store",
      credentials: "same-origin",
    };
    if (state.token) { init.headers["X-Agent-Token"] = state.token; }
    if (init.method !== "GET") {
      init.headers["Content-Type"] = "application/json";
      init.body = JSON.stringify(options.body || {});
    }
    return fetch(path, init).then(function (res) {
      return res.text().then(function (raw) {
        var data = {};
        if (raw) {
          try { data = JSON.parse(raw); } catch (e) { data = { error: raw.slice(0, 300) }; }
        }
        if (res.status === 401) {
          showTokenBanner(true);
          throw new ApiError(401, "unauthorized — the token is wrong or missing");
        }
        if (!res.ok) {
          throw new ApiError(res.status, String(data && data.error ? data.error : "HTTP " + res.status));
        }
        showTokenBanner(false);
        return data;
      });
    });
  }

  // ---------------------------------------------------------------- toasts

  function toast(message, kind) {
    var host = $("toasts");
    var node = el("div", { class: "toast " + (kind || ""), text: message });
    host.appendChild(node);
    window.setTimeout(function () {
      if (node.parentNode) { node.parentNode.removeChild(node); }
    }, kind === "bad" ? 12000 : 6000);
    while (host.childElementCount > 5) { host.removeChild(host.firstElementChild); }
  }

  function failed(prefix) {
    return function (err) {
      var msg = err && err.message ? err.message : String(err);
      toast(prefix + ": " + msg, "bad");
    };
  }

  // ----------------------------------------------------------- copy button

  function copyText(text) {
    if (navigator.clipboard && window.isSecureContext) {
      return navigator.clipboard.writeText(text);
    }
    return new Promise(function (resolve, reject) {
      var ta = document.createElement("textarea");
      ta.value = text;
      ta.setAttribute("readonly", "");
      ta.style.position = "fixed";
      ta.style.opacity = "0";
      document.body.appendChild(ta);
      ta.select();
      var ok = false;
      try { ok = document.execCommand("copy"); } catch (e) { ok = false; }
      document.body.removeChild(ta);
      if (ok) { resolve(); } else { reject(new Error("clipboard unavailable")); }
    });
  }

  function copyButton(text, label) {
    return el("button", {
      class: "btn btn-ghost btn-sm",
      text: label || "Copy",
      attrs: { type: "button", "aria-label": "Copy to clipboard" },
      on: {
        click: function (ev) {
          var btn = ev.currentTarget;
          copyText(text).then(function () {
            btn.textContent = "Copied";
            window.setTimeout(function () { btn.textContent = label || "Copy"; }, 1400);
          }, function () {
            toast("Could not reach the clipboard — select the text manually.", "warn");
          });
        },
      },
    });
  }

  // --------------------------------------------------------------- banners

  function showTokenBanner(show) {
    var banner = $("banner-token");
    if (show) { banner.removeAttribute("hidden"); } else { banner.setAttribute("hidden", ""); }
  }

  function setOffline(isOffline, detail) {
    var changed = state.offline !== isOffline;
    state.offline = isOffline;
    var banner = $("banner-offline");
    if (isOffline) {
      $("banner-offline-detail").textContent = detail ||
        "The agent is not answering. Retrying every 10 s.";
      banner.removeAttribute("hidden");
      if (changed) { toast("Connection lost — figures below are stale.", "bad"); }
    } else {
      banner.setAttribute("hidden", "");
      if (changed) { toast("Reconnected.", "ok"); }
    }
    // The banner scrolls away; the pinned header must show staleness too.
    $("stamp").classList.toggle("stale", isOffline);
  }

  // ------------------------------------------------------------------ tabs

  function selectTab(name, focus) {
    if (TABS.indexOf(name) === -1) { name = "overview"; }
    state.active = name;
    lsSet(TAB_KEY, name);
    TABS.forEach(function (tab) {
      var button = $("tab-" + tab);
      var panel = $("panel-" + tab);
      var on = tab === name;
      button.setAttribute("aria-selected", on ? "true" : "false");
      button.tabIndex = on ? 0 : -1;
      if (on) { panel.removeAttribute("hidden"); } else { panel.setAttribute("hidden", ""); }
    });
    if (focus) { $("tab-" + name).focus(); }
    loadTab(name);
  }

  function loadTab(name) {
    if (name === "gigs") { loadGigs(); }
    if (name === "approvals") { loadApprovals(); }
    if (name === "storefront") { loadStorefront(); }
    if (name === "trading") { loadTrading(); }
    if (name === "ledger") { loadLedger(); }
    if (name === "discovery") { loadDiscovery(); }
  }

  function wireTabs() {
    var list = $("tablist");
    list.addEventListener("click", function (ev) {
      var button = ev.target.closest ? ev.target.closest("[data-tab]") : null;
      if (button) { selectTab(button.dataset.tab, false); }
    });
    list.addEventListener("keydown", function (ev) {
      var index = TABS.indexOf(state.active);
      var next = null;
      if (ev.key === "ArrowRight") { next = TABS[(index + 1) % TABS.length]; }
      if (ev.key === "ArrowLeft") { next = TABS[(index - 1 + TABS.length) % TABS.length]; }
      if (ev.key === "Home") { next = TABS[0]; }
      if (ev.key === "End") { next = TABS[TABS.length - 1]; }
      if (next) { ev.preventDefault(); selectTab(next, true); }
    });
  }

  // ============================================================== OVERVIEW

  function countBox(label, value) {
    return el("div", { class: "count" }, [
      el("span", { class: "count-label", text: label }),
      el("span", { class: "count-value", text: value }),
    ]);
  }

  function chip(text, kind, title) {
    return el("span", { class: "chip " + (kind || ""), text: text, title: title || "" });
  }

  function refreshState() {
    return api("/api/state").then(function (data) {
      setOffline(false);
      state.lastOk = Date.now() / 1000;
      renderState(data);
      return data;
    }, function (err) {
      if (err && err.status === 401) {
        setOffline(false);
      } else {
        setOffline(true, err && err.message ? String(err.message) : "unreachable");
      }
      throw err;
    });
  }

  function renderState(data) {
    var treasury = data.treasury || {};
    $("fig-confirmed").textContent = money(treasury.confirmed_usdt) + " USDT";
    $("fig-expected").textContent = money(treasury.expected_usdt) + " USDT";
    $("fig-hours").textContent = num(treasury.hours_spent).toFixed(1);

    var counts = data.counts || {};
    var host = $("counts");
    clear(host);
    host.appendChild(countBox("Gigs known", count(counts.gigs)));
    host.appendChild(countBox("Open orders", count(counts.open_orders)));
    host.appendChild(countBox("Pending approvals", count(counts.pending_approvals)));
    host.appendChild(countBox("Transfers seen", count(counts.transfers_seen)));

    var pip = $("pip-approvals");
    var pending = num(counts.pending_approvals);
    pip.textContent = count(pending);
    if (pending > 0) { pip.removeAttribute("hidden"); } else { pip.setAttribute("hidden", ""); }

    renderLadder(data.ladder || {});
    renderWallet(data.wallet || {});
    renderChannels(data.channels || {});
    renderIntegrity($("overview-integrity"), data.ledger || {});

    state.channels = Object.keys(data.channels || {}).sort();
    syncChannelFilter();
    stampNow(data.generated_at);
  }

  function stampNow(generatedAt) {
    var when = num(generatedAt) > 0 ? num(generatedAt) : state.lastOk;
    $("stamp").textContent = "updated " + ago(when);
    $("stamp").dataset.at = String(when);
    $("foot-stamp").textContent = "server time " + whenLocal(when);
  }

  function renderLadder(ladder) {
    var stages = Array.isArray(ladder.stages) ? ladder.stages : [];
    $("ladder-progress").textContent =
      count(ladder.completed) + " / " + count(ladder.total) + " · now: " + String(ladder.stage || "—");

    var host = $("stepper");
    clear(host);
    if (!stages.length) { host.appendChild(empty("No ladder assessment yet.")); return; }

    var currentSeen = false;
    stages.forEach(function (stage) {
      var done = !!stage.done;
      var current = !done && !currentSeen;
      if (current) { currentSeen = true; }

      var rail = el("div", { class: "step-rail" }, [
        el("span", { class: "step-dot" }),
        el("span", { class: "step-line" }),
      ]);

      var body = el("div", { class: "step-body" }, [
        el("div", { class: "step-title", text: String(stage.title || stage.key || "") }),
        el("div", { class: "step-key", text: String(stage.key || "") + (done ? " · done" : (current ? " · current" : " · pending")) }),
      ]);

      if (current && Array.isArray(stage.actions) && stage.actions.length) {
        var list = el("ul", { class: "actions" });
        stage.actions.forEach(function (action) {
          var line = String(action);
          list.appendChild(el("li", { class: "action" }, [
            el("code", { text: line }),
            copyButton(line),
          ]));
        });
        body.appendChild(list);
      }

      host.appendChild(el("li", {
        class: "step " + (done ? "done" : (current ? "current" : "pending")),
      }, [rail, body]));
    });
  }

  function renderWallet(wallet) {
    var addresses = wallet.addresses || {};
    var balances = wallet.balances || {};
    var errors = wallet.errors || {};
    $("wallet-total").textContent = money(wallet.total_usdt) + " USDT on-chain balance";

    var host = $("wallet-list");
    clear(host);

    var chains = Object.keys(addresses);
    Object.keys(balances).forEach(function (c) { if (chains.indexOf(c) === -1) { chains.push(c); } });
    Object.keys(errors).forEach(function (c) { if (chains.indexOf(c) === -1) { chains.push(c); } });

    if (!chains.length) {
      host.appendChild(empty("No wallet address configured. Set USDT_WALLET_TRON=T… and restart."));
      return;
    }

    chains.sort().forEach(function (chain) {
      var address = String(addresses[chain] || "");
      var row = el("div", { class: "wallet-row" }, [
        el("span", { class: "wallet-chain", text: chain }),
        el("span", { class: "wallet-addr", text: address || "no address", title: address }),
      ]);
      if (address) { row.appendChild(copyButton(address)); }
      if (errors[chain]) {
        row.appendChild(chip("unreachable", "chip-quiet", String(errors[chain])));
      }
      row.appendChild(el("span", {
        class: "wallet-balance",
        text: balances[chain] === undefined ? "—" : money(balances[chain], 4),
      }));
      host.appendChild(row);
    });
  }

  var VERDICT_CHIP = {
    proven: "chip-ok",
    promising: "chip-info",
    unproven: "chip-warn",
    untried: "chip-quiet",
  };

  var AUTONOMY_CHIP = { auto: "chip-ok", assisted: "chip-info", manual: "chip-quiet" };

  function renderChannels(channels) {
    var host = $("channel-cards");
    clear(host);
    var names = Object.keys(channels).sort();
    if (!names.length) { host.appendChild(empty("No channels configured.")); return; }

    names.forEach(function (name) {
      var ch = channels[name] || {};
      var cal = ch.calibration || {};
      var verdict = String(cal.verdict || "untried");
      var autonomy = String(ch.autonomy || "assisted");

      var head = el("div", { class: "channel-head" }, [
        el("span", { class: "channel-name", text: String(ch.name || name) }),
        chip(autonomy, AUTONOMY_CHIP[autonomy] || "chip-quiet", "how far this channel runs unattended"),
        ch.ready ? chip("ready", "chip-ok") : chip("blocked", "chip-warn"),
        chip(verdict, VERDICT_CHIP[verdict] || "chip-quiet",
          "proven requires 3+ confirmed payouts; a pipeline alone never proves a channel"),
      ]);

      var card = el("div", { class: "channel" }, [
        head,
        el("p", { class: "channel-desc", text: String(ch.description || "") }),
        el("div", { class: "money-pair" }, [
          el("div", { class: "money-box confirmed" }, [
            el("span", { class: "money-label", text: "confirmed" }),
            el("span", { class: "money-value", text: money(ch.confirmed_usdt) }),
          ]),
          el("div", { class: "money-box expected" }, [
            el("span", { class: "money-label", text: "expected · not income yet" }),
            el("span", { class: "money-value", text: money(ch.expected_usdt) }),
          ]),
        ]),
        el("div", { class: "channel-meta" }, [
          chip("orders " + count(cal.orders), "chip-quiet"),
          chip("paid " + count(cal.paid), "chip-quiet"),
          chip("conversion " + pct(cal.conversion, 1), "chip-quiet"),
          chip("realized " + money(cal.realized_usdt_per_hour) + " USDT/h", "chip-quiet",
            "measured, from completed orders only"),
          chip("effort × " + num(cal.effort_calibration).toFixed(2), "chip-quiet",
            "actual ÷ estimated hours; above 1 means the estimates were optimistic"),
          chip("capital " + money(ch.capital_required_usdt) + " USDT", "chip-quiet"),
          chip("lag " + num(ch.typical_lag_days).toFixed(0) + " d", "chip-quiet"),
        ]),
      ]);

      var blockers = Array.isArray(ch.blockers) ? ch.blockers : [];
      if (blockers.length) {
        var list = el("ul", { class: "blockers" });
        blockers.forEach(function (b) { list.appendChild(el("li", { text: String(b) })); });
        card.appendChild(list);
      }

      var reqs = Array.isArray(ch.requirements) ? ch.requirements : [];
      if (reqs.length) {
        var ul = el("ul");
        reqs.forEach(function (r) { ul.appendChild(el("li", { text: String(r) })); });
        card.appendChild(el("details", { class: "reqs" }, [
          el("summary", { text: "requirements (" + reqs.length + ")" }),
          ul,
        ]));
      }

      host.appendChild(card);
    });
  }

  function renderIntegrity(host, integrity) {
    clear(host);
    var ok = !!integrity.ok;
    host.className = "integrity " + (ok ? "ok" : "bad");
    host.appendChild(el("strong", { text: ok ? "Journal intact" : "JOURNAL FAILED VERIFICATION" }));
    host.appendChild(el("span", { class: "msg", text: String(integrity.message || "") }));
  }

  // ================================================================== GIGS

  function syncChannelFilter() {
    var select = $("gig-channel");
    var current = select.value;
    var wanted = [""].concat(state.channels);
    var have = Array.prototype.map.call(select.options, function (o) { return o.value; });
    if (have.join("|") === wanted.join("|")) { return; }
    clear(select);
    select.appendChild(el("option", { text: "all", attrs: { value: "" } }));
    state.channels.forEach(function (name) {
      select.appendChild(el("option", { text: name, attrs: { value: name } }));
    });
    select.value = wanted.indexOf(current) === -1 ? "" : current;
  }

  function loadGigs() {
    var limit = encodeURIComponent($("gig-limit").value || "50");
    var channel = encodeURIComponent($("gig-channel").value || "");
    return api("/api/gigs?limit=" + limit + "&channel=" + channel).then(function (data) {
      state.gigs = Array.isArray(data.gigs) ? data.gigs : [];
      renderGigErrors(data.errors || {});
      renderGigs();
    }, failed("Gigs"));
  }

  function renderGigErrors(errors) {
    var host = $("gig-errors");
    clear(host);
    Object.keys(errors).forEach(function (key) {
      host.appendChild(el("div", {
        class: "error-line",
        text: key + ": " + String(errors[key]),
      }));
    });
  }

  function sortedGigs() {
    var key = state.gigSort.key;
    var dir = state.gigSort.dir;
    return state.gigs.slice().sort(function (a, b) {
      var av = a[key];
      var bv = b[key];
      if (typeof av === "string" || typeof bv === "string") {
        return String(av || "").localeCompare(String(bv || "")) * dir;
      }
      return (num(av) - num(bv)) * dir;
    });
  }

  function renderGigs() {
    var body = $("gig-body");
    clear(body);
    var rows = sortedGigs();
    if (!rows.length) {
      body.appendChild(el("tr", {}, [
        el("td", { attrs: { colspan: "7" } }, [empty("No gigs discovered yet. Run a cycle, or check the Discovery tab.")]),
      ]));
      return;
    }

    rows.forEach(function (gig) {
      var titleCell = el("td", { class: "cell-title" }, [
        link(gig.url, String(gig.title || "(untitled)")),
      ]);
      var deadline = num(gig.deadline_ts);
      if (deadline > 0) {
        titleCell.appendChild(el("span", {
          class: "cell-note",
          text: "deadline " + whenLocal(deadline),
        }));
      }
      if (gig.source) {
        titleCell.appendChild(el("span", { class: "cell-note", text: "source: " + String(gig.source) }));
      }

      var take = el("button", {
        class: "btn btn-take",
        text: "Take",
        attrs: { type: "button" },
        on: { click: function (ev) { takeGig(String(gig.id), ev.currentTarget); } },
      });

      body.appendChild(el("tr", {}, [
        el("td", {}, [chip(String(gig.channel || "?"), "chip-quiet")]),
        titleCell,
        el("td", { class: "right num", text: money(gig.reward_usdt) }),
        el("td", { class: "right num", text: num(gig.effort_hours).toFixed(1) }),
        el("td", { class: "right num", text: pct(gig.payout_probability) }),
        el("td", { class: "right num", text: money(gig.usdt_per_hour) }),
        el("td", { class: "right" }, [take]),
      ]));
    });
  }

  function takeGig(gigId, button) {
    button.disabled = true;
    api("/api/gigs/" + encodeURIComponent(gigId) + "/take", { method: "POST" }).then(function (data) {
      var order = data.order || {};
      toast("Order drafted: " + String(order.title || gigId) + " · " + String(order.status || ""), "ok");
      if (data.approval_id) {
        toast("Waiting for your approval before anything is sent.", "warn");
      }
      return Promise.all([loadGigs(), refreshState().catch(function () {})]);
    }, function (err) {
      button.disabled = false;
      failed("Take")(err);
    });
  }

  function wireGigs() {
    $("gig-refresh").addEventListener("click", loadGigs);
    $("gig-channel").addEventListener("change", loadGigs);
    $("gig-limit").addEventListener("change", loadGigs);

    $("gig-table").querySelectorAll(".th-sort").forEach(function (button) {
      button.addEventListener("click", function () {
        var key = button.dataset.sort;
        if (state.gigSort.key === key) {
          state.gigSort.dir = -state.gigSort.dir;
        } else {
          state.gigSort.key = key;
          state.gigSort.dir = (key === "channel" || key === "title") ? 1 : -1;
        }
        $("gig-table").querySelectorAll("th[aria-sort]").forEach(function (th) {
          th.setAttribute("aria-sort", "none");
        });
        button.closest("th").setAttribute(
          "aria-sort", state.gigSort.dir === 1 ? "ascending" : "descending");
        renderGigs();
      });
    });
  }

  // ============================================================= APPROVALS

  function loadApprovals() {
    return api("/api/approvals").then(function (data) {
      renderApprovals(Array.isArray(data.approvals) ? data.approvals : []);
    }, failed("Approvals"));
  }

  function renderApprovals(rows) {
    var host = $("approval-list");
    clear(host);
    if (!rows.length) {
      host.appendChild(empty("Nothing waiting on you. The agent stops here before it speaks to anyone."));
      return;
    }

    rows.forEach(function (row) {
      var id = String(row.id || "");
      var note = el("input", {
        class: "input grow",
        attrs: {
          type: "text", placeholder: "note (optional, recorded in the journal)",
          "aria-label": "Decision note", maxlength: "300",
        },
      });

      var approve = el("button", {
        class: "btn btn-big btn-approve", text: "Approve", attrs: { type: "button" },
      });
      var reject = el("button", {
        class: "btn btn-big btn-reject", text: "Reject", attrs: { type: "button" },
      });

      function decide(approved) {
        approve.disabled = true;
        reject.disabled = true;
        api("/api/approvals/" + encodeURIComponent(id) + "/decide", {
          method: "POST",
          body: { approved: approved, note: note.value.slice(0, 300) },
        }).then(function () {
          toast((approved ? "Approved" : "Rejected") + ": " + String(row.title || id), approved ? "ok" : "");
          return Promise.all([loadApprovals(), refreshState().catch(function () {})]);
        }, function (err) {
          approve.disabled = false;
          reject.disabled = false;
          failed("Decision")(err);
        });
      }

      approve.addEventListener("click", function () { decide(true); });
      reject.addEventListener("click", function () { decide(false); });

      host.appendChild(el("article", { class: "approval" }, [
        el("div", { class: "approval-head" }, [
          el("span", { class: "approval-title", text: String(row.title || "(untitled)") }),
          chip(String(row.kind || "approval"), "chip-quiet"),
          row.channel ? chip(String(row.channel), "chip-quiet") : null,
          el("span", { class: "stamp", text: whenLocal(row.created_ts) }),
        ]),
        el("pre", { class: "approval-detail", text: String(row.detail || "(no detail supplied)") }),
        el("div", { class: "approval-actions" }, [note, approve, reject]),
      ]));
    });
  }

  // ============================================================ STOREFRONT

  function loadStorefront() {
    var a = api("/api/catalogue").then(renderCatalogue, failed("Catalogue"));
    var b = api("/api/invoices").then(function (data) {
      renderInvoices(Array.isArray(data.invoices) ? data.invoices : []);
    }, failed("Invoices"));
    return Promise.all([a, b]);
  }

  function renderCatalogue(data) {
    var receiving = data.receiving || {};
    var host = $("receiving");
    clear(host);
    var address = String(receiving.address || "");
    if (!address) {
      host.appendChild(chip("no receiving address", "chip-bad",
        "set USDT_WALLET_TRON (or another chain) and restart the agent"));
    } else {
      host.appendChild(chip(String(receiving.chain || "?"), "chip-info"));
      host.appendChild(el("span", { class: "wallet-addr", text: address, title: address }));
      host.appendChild(copyButton(address, "Copy address"));
    }

    var grid = $("catalogue");
    clear(grid);
    var offers = Array.isArray(data.catalogue) ? data.catalogue : [];
    if (!offers.length) { grid.appendChild(empty("The catalogue is empty.")); return; }

    offers.forEach(function (offer) {
      var sku = String(offer.sku || "");
      var ref = el("input", {
        class: "input",
        attrs: { type: "text", placeholder: "customer ref (optional)", "aria-label": "Customer reference for " + sku, maxlength: "64" },
      });
      var button = el("button", {
        class: "btn btn-primary", text: "Create invoice", attrs: { type: "button" },
      });
      button.addEventListener("click", function () {
        button.disabled = true;
        api("/api/invoices", { method: "POST", body: { sku: sku, ref: ref.value.slice(0, 64) } })
          .then(function (res) {
            var inv = res.invoice || {};
            button.disabled = false;
            ref.value = "";
            toast("Invoice " + String(inv.invoice_id || "") + " — pay exactly " +
              exact(inv.pay_exactly_usdt) + " USDT", "ok");
            return loadStorefront();
          }, function (err) {
            button.disabled = false;
            failed("Invoice")(err);
          });
      });

      grid.appendChild(el("div", { class: "offer" }, [
        el("div", { class: "channel-head" }, [
          el("span", { class: "channel-name", text: String(offer.title || sku) }),
          chip(sku, "chip-quiet"),
        ]),
        el("span", { class: "offer-price num", text: money(offer.price_usdt) + " USDT" }),
        el("p", { class: "offer-desc", text: String(offer.description || "") }),
        offer.delivery ? chip(String(offer.delivery), "chip-quiet") : null,
        ref,
        button,
      ]));
    });
  }

  function renderInvoices(rows) {
    var host = $("invoices");
    clear(host);
    if (!rows.length) {
      host.appendChild(empty("No invoices issued in this session."));
      return;
    }

    rows.slice().reverse().forEach(function (inv) {
      var paid = String(inv.status || "") === "paid";
      var amount = exact(inv.pay_exactly_usdt);
      var address = String(inv.pay_to || "");

      var card = el("article", { class: "invoice " + (paid ? "paid" : "") }, [
        el("div", { class: "invoice-head" }, [
          el("span", { class: "invoice-id", text: String(inv.invoice_id || "") }),
          chip(String(inv.sku || ""), "chip-quiet"),
          paid ? chip("paid · confirmed on-chain", "chip-ok") : chip("unpaid · not income yet", "chip-expected"),
          el("span", { class: "chip chip-quiet", data: { expires: String(num(inv.expires_ts)) }, text: "…" }),
        ]),
        el("div", { class: "copyfield" }, [
          el("span", { class: "copyvalue num", text: amount + " USDT" }),
          copyButton(amount, "Copy amount"),
        ]),
        el("div", { class: "copyfield" }, [
          el("span", { class: "copyvalue addr", text: address || "no address" }),
          address ? copyButton(address, "Copy address") : null,
        ]),
        el("div", { class: "invoice-meta" }, [
          chip(String(inv.chain || "?"), "chip-info"),
          el("span", { class: "stamp", text: String(inv.note || "") }),
        ]),
      ]);
      host.appendChild(card);
    });
    tickCountdowns();
  }

  // =============================================================== TRADING

  var SVG_NS = "http://www.w3.org/2000/svg";

  function svg(tag, attrs) {
    var node = document.createElementNS(SVG_NS, tag);
    Object.keys(attrs || {}).forEach(function (k) { node.setAttribute(k, String(attrs[k])); });
    return node;
  }

  function sparkline(points) {
    var W = 1000;
    var H = 140;
    var PAD = 10;
    var root = svg("svg", {
      viewBox: "0 0 " + W + " " + H,
      preserveAspectRatio: "none",
      role: "img",
      "aria-label": "Equity curve, " + points.length + " samples",
    });

    var values = points.map(function (p) { return num(p[1]); });
    var lo = Math.min.apply(null, values);
    var hi = Math.max.apply(null, values);
    var span = hi - lo;
    var flat = span < 1e-9;

    function x(i) { return points.length < 2 ? W / 2 : (i / (points.length - 1)) * W; }
    function y(v) { return flat ? H / 2 : H - PAD - ((v - lo) / span) * (H - 2 * PAD); }

    var d = "";
    values.forEach(function (v, i) { d += (i ? " L " : "M ") + x(i).toFixed(2) + " " + y(v).toFixed(2); });

    root.appendChild(svg("path", {
      d: d + " L " + x(values.length - 1).toFixed(2) + " " + H + " L " + x(0).toFixed(2) + " " + H + " Z",
      fill: "var(--info-soft)", stroke: "none",
    }));
    root.appendChild(svg("line", {
      x1: 0, x2: W, y1: y(values[0]).toFixed(2), y2: y(values[0]).toFixed(2),
      stroke: "var(--line-strong)", "stroke-width": "1",
      "stroke-dasharray": "4 4", "vector-effect": "non-scaling-stroke",
    }));
    root.appendChild(svg("path", {
      d: d, fill: "none", stroke: "var(--info)", "stroke-width": "2",
      "stroke-linejoin": "round", "stroke-linecap": "round",
      "vector-effect": "non-scaling-stroke",
    }));
    root.appendChild(svg("line", {
      x1: x(values.length - 1).toFixed(2), x2: x(values.length - 1).toFixed(2),
      y1: (y(values[values.length - 1]) - 6).toFixed(2),
      y2: (y(values[values.length - 1]) + 6).toFixed(2),
      stroke: "var(--info)", "stroke-width": "2", "vector-effect": "non-scaling-stroke",
    }));
    return { node: root, lo: lo, hi: hi };
  }

  function loadTrading() {
    return api("/api/trading").then(function (data) {
      renderEquity(Array.isArray(data.equity) ? data.equity : []);
      renderTotals(data.totals || {});
      renderStrategies(data.strategies || {});
    }, failed("Trading"));
  }

  function renderEquity(points) {
    var host = $("spark");
    clear(host);
    var clean = points.filter(function (p) { return Array.isArray(p) && p.length >= 2; });

    if (clean.length < 2) {
      host.appendChild(empty("Not enough equity history to draw a curve yet."));
      $("spark-hi").textContent = "—";
      $("spark-lo").textContent = "—";
      $("spark-from").textContent = "";
      $("spark-to").textContent = "";
      return;
    }

    // Keep the DOM small on long histories; the shape survives decimation.
    var step = Math.ceil(clean.length / 1200);
    var sampled = step > 1 ? clean.filter(function (_, i) { return i % step === 0; }) : clean;
    if (sampled[sampled.length - 1] !== clean[clean.length - 1]) {
      sampled.push(clean[clean.length - 1]);
    }

    var drawn = sparkline(sampled);
    host.appendChild(drawn.node);
    $("spark-hi").textContent = money(drawn.hi);
    $("spark-lo").textContent = money(drawn.lo);
    $("spark-from").textContent = whenLocal(clean[0][0]);
    $("spark-to").textContent = whenLocal(clean[clean.length - 1][0]) +
      " · " + money(clean[clean.length - 1][1]) + " USDT";
  }

  function renderTotals(totals) {
    var host = $("trading-totals");
    clear(host);
    host.appendChild(countBox("Closed trades", count(totals.closed_trades)));
    host.appendChild(countBox("Realized P&L", money(totals.realized_pnl, 4)));
    host.appendChild(countBox("Costs", money(totals.costs, 4)));
    host.appendChild(countBox("Gross accrued", money(totals.gross, 4)));
  }

  function renderStrategies(stats) {
    var body = $("strategy-body");
    clear(body);
    var names = Object.keys(stats).sort();
    if (!names.length) {
      body.appendChild(el("tr", {}, [
        el("td", { attrs: { colspan: "6" } }, [empty("No closed trades yet.")]),
      ]));
      return;
    }
    names.forEach(function (name) {
      var s = stats[name] || {};
      body.appendChild(el("tr", {}, [
        el("td", { class: "mono", text: name }),
        el("td", { class: "right num", text: count(s.n) }),
        el("td", { class: "right num", text: money(s.pnl, 4) }),
        el("td", { class: "right num", text: num(s.avg_bps).toFixed(2) }),
        el("td", { class: "right num", text: pct(s.win_rate, 1) }),
        el("td", { class: "right num", text: money(s.turnover) }),
      ]));
    });
  }

  // ================================================================ LEDGER

  function loadLedger() {
    var limit = encodeURIComponent($("ledger-limit").value || "50");
    return api("/api/ledger?limit=" + limit).then(function (data) {
      renderIntegrity($("ledger-integrity"), data.integrity || {});
      renderLedgerRows(Array.isArray(data.events) ? data.events : []);
    }, failed("Ledger"));
  }

  function renderLedgerRows(events) {
    var body = $("ledger-body");
    clear(body);
    if (!events.length) {
      body.appendChild(el("tr", {}, [
        el("td", { attrs: { colspan: "4" } }, [empty("The journal is empty.")]),
      ]));
      return;
    }
    events.forEach(function (event) {
      var kind = String(event.kind || "");
      var extra = kind === "income" ? " income" : (kind.indexOf("error") !== -1 ? " error" : "");
      var payload;
      try {
        payload = JSON.stringify(event.payload, null, 2);
      } catch (e) {
        payload = String(event.payload);
      }
      body.appendChild(el("tr", {}, [
        el("td", { class: "num", text: count(event.seq) }),
        el("td", { class: "num", text: whenLocal(event.ts) }),
        el("td", {}, [el("span", { class: "kind" + extra, text: kind })]),
        el("td", {}, [el("pre", { class: "json", text: payload === undefined ? "" : payload })]),
      ]));
    });
  }

  // ============================================================= DISCOVERY

  /* The probe payload is defined by the server and may grow fields. Render the
     ones we understand, then dump the rest as inert key/value text rather than
     silently hiding information the operator may need. */
  var PROBE_TITLE_KEYS = ["name", "source", "label", "title", "url", "host"];
  var PROBE_OK_KEYS = ["ok", "healthy", "reachable", "alive", "up"];
  var PROBE_SHOWN = PROBE_TITLE_KEYS.concat(PROBE_OK_KEYS, ["error", "url"]);

  function probeCard(probe) {
    var title = "";
    for (var i = 0; i < PROBE_TITLE_KEYS.length; i++) {
      if (probe[PROBE_TITLE_KEYS[i]]) { title = String(probe[PROBE_TITLE_KEYS[i]]); break; }
    }

    var health = null;
    for (var j = 0; j < PROBE_OK_KEYS.length; j++) {
      if (typeof probe[PROBE_OK_KEYS[j]] === "boolean") {
        health = probe[PROBE_OK_KEYS[j]];
        break;
      }
    }

    var head = el("div", { class: "source-head" }, [
      el("span", { class: "channel-name", text: title || "(unnamed source)" }),
      health === null ? chip("unknown", "chip-quiet")
        : (health ? chip("reachable", "chip-ok") : chip("unreachable", "chip-warn")),
    ]);

    var url = safeUrl(probe.url);
    if (url && title !== String(probe.url)) { head.appendChild(link(url, url)); }
    else if (!url && probe.url) {
      // Not http(s): show it as text so the operator can see what was rejected.
      head.appendChild(el("span", { class: "source-url", text: String(probe.url) }));
    }

    var card = el("article", { class: "source" }, [head]);

    if (probe.error) {
      card.appendChild(el("div", { class: "error-line", text: String(probe.error) }));
    }

    var dl = el("dl", { class: "kv" });
    Object.keys(probe).forEach(function (key) {
      if (PROBE_SHOWN.indexOf(key) !== -1) { return; }
      var value = probe[key];
      var rendered;
      if (value === null || value === undefined) {
        rendered = "—";
      } else if (typeof value === "object") {
        try { rendered = JSON.stringify(value); } catch (e) { rendered = "[unserialisable]"; }
      } else if (typeof value === "number" && /_ts$/.test(key)) {
        rendered = whenLocal(value);
      } else {
        rendered = String(value);
      }
      dl.appendChild(el("dt", { text: key }));
      dl.appendChild(el("dd", { text: rendered.slice(0, 2000) }));
    });
    if (dl.childElementCount) { card.appendChild(dl); }
    return card;
  }

  function loadDiscovery() {
    return api("/api/discovery").then(function (data) {
      var host = $("source-list");
      clear(host);
      // The registry is optional: the server answers 200 with an explanation
      // rather than failing the page when it is not installed.
      if (data.error) {
        host.appendChild(el("div", { class: "error-line", text: String(data.error) }));
      }
      var sources = Array.isArray(data.sources) ? data.sources : [];
      if (!sources.length) {
        host.appendChild(empty("No sources reported."));
        return;
      }
      sources.forEach(function (probe) {
        host.appendChild(probeCard(probe && typeof probe === "object" ? probe : { name: String(probe) }));
      });
    }, failed("Discovery"));
  }

  function wireProbe() {
    $("probe-form").addEventListener("submit", function (ev) {
      ev.preventDefault();
      var input = $("probe-url");
      var button = $("probe-submit");
      var url = input.value.trim();
      if (!url) { return; }
      button.disabled = true;
      api("/api/discovery/probe", { method: "POST", body: { url: url } }).then(function (data) {
        button.disabled = false;
        var host = $("probe-result");
        clear(host);
        var probe = data.probe && typeof data.probe === "object" ? data.probe : {};
        host.appendChild(probeCard(probe));
      }, function (err) {
        button.disabled = false;
        var host = $("probe-result");
        clear(host);
        host.appendChild(el("div", { class: "error-line", text: String(err.message || err) }));
      });
    });
  }

  // =============================================================== COLLECT

  function wireCollect() {
    $("btn-collect").addEventListener("click", function () {
      var button = $("btn-collect");
      button.disabled = true;
      button.textContent = "Collecting…";
      api("/api/collect", { method: "POST" }).then(function (res) {
        button.disabled = false;
        button.textContent = "Collect";
        var found = num(res.confirmed_usdt);
        if (found > 0) {
          toast("+" + money(found, 4) + " USDT confirmed · " + count(res.new_transfers) +
            " transfers · " + count(res.matched) + " matched", "ok");
        } else {
          toast("No new money on-chain. Treasury unchanged at " +
            money(res.treasury_usdt) + " USDT.");
        }
        if (num(res.unattributed_usdt) > 0) {
          toast("Unattributed: " + money(res.unattributed_usdt, 4) +
            " USDT arrived without a matching expectation.", "warn");
        }
        if (num(res.expired) > 0) {
          toast(count(res.expired) + " overdue expectation(s) expired — never counted as income.", "warn");
        }
        Object.keys(res.baselined || {}).forEach(function (chain) {
          toast("Baselined " + chain + " at " + money(res.baselined[chain], 4) +
            " USDT — pre-existing funds are not earnings.", "warn");
        });
        Object.keys(res.errors || {}).forEach(function (chain) {
          toast(chain + ": " + String(res.errors[chain]), "bad");
        });
        return refreshState().catch(function () {});
      }, function (err) {
        button.disabled = false;
        button.textContent = "Collect";
        failed("Collect")(err);
      });
    });
  }

  // ========================================================= TICK / POLLING

  function tickCountdowns() {
    var now = Date.now() / 1000;
    document.querySelectorAll("[data-expires]").forEach(function (node) {
      var expires = num(node.dataset.expires);
      if (expires <= 0) { node.textContent = "no expiry"; return; }
      var left = expires - now;
      node.textContent = left <= 0 ? "expired" : "expires in " + duration(left);
      node.classList.toggle("chip-warn", left <= 0);
    });
  }

  function tickStamp() {
    var at = num($("stamp").dataset.at);
    if (at > 0) { $("stamp").textContent = "updated " + ago(at); }
  }

  function poll() {
    if (document.hidden) { return; }
    refreshState().catch(function () { /* banner already reflects it */ });
  }

  // ================================================================== BOOT

  function forgetLegacyToken() {
    // Earlier builds persisted the token here. Clear it on load so upgrading
    // removes the secret rather than leaving it sitting in the browser.
    try { window.localStorage.removeItem("usdt-agent.token"); } catch (e) { /* ignore */ }
  }

  function resolveToken() {
    // Deliberately returns nothing. The server no longer puts a token in the
    // markup — that made it readable by `curl /` from any local process — and
    // we do not persist one to localStorage, where an XSS could lift it. The
    // normal path is the HttpOnly cookie; a hand-typed token stays in memory
    // for this tab only.
    return "";
  }

  function wireToken() {
    $("token-form").addEventListener("submit", function (ev) {
      ev.preventDefault();
      var value = $("token-input").value.trim();
      if (!value) { return; }
      state.token = value;  // in-memory only, gone when the tab closes
      $("token-input").value = "";
      showTokenBanner(false);
      refreshState().then(function () {
        toast("Token accepted.", "ok");
        loadTab(state.active);
      }).catch(function () {});
    });
    $("banner-offline").querySelector('[data-action="retry"]').addEventListener("click", poll);
  }

  function boot() {
    forgetLegacyToken();
    state.token = resolveToken();
    if (!state.token) { showTokenBanner(true); }

    wireTabs();
    wireGigs();
    wireCollect();
    wireProbe();
    wireToken();

    $("approval-refresh").addEventListener("click", loadApprovals);
    $("store-refresh").addEventListener("click", loadStorefront);
    $("trading-refresh").addEventListener("click", loadTrading);
    $("ledger-refresh").addEventListener("click", loadLedger);
    $("ledger-limit").addEventListener("change", loadLedger);
    $("discovery-refresh").addEventListener("click", loadDiscovery);

    selectTab(lsGet(TAB_KEY) || "overview", false);

    refreshState().catch(function () {});
    window.setInterval(poll, POLL_MS);
    window.setInterval(function () { tickStamp(); tickCountdowns(); }, 1000);

    document.addEventListener("visibilitychange", function () {
      if (!document.hidden) { poll(); }
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot);
  } else {
    boot();
  }
})();
