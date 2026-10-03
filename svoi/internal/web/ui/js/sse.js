// Live link to the local node: one EventSource on api/events with manual
// reconnect + exponential backoff, and a full GET api/state on every
// (re)connect so nothing missed while disconnected is lost.
import { get } from "./api.js";
import { emit, removeTransfer, setState, state, upsertTransfer } from "./store.js";
import { toast } from "./components/toast.js";

let es = null;
let retryTimer = null;
let offlineTimer = null;
let attempt = 0;
let refreshSeq = 0;

/** Reload the whole state snapshot. Safe to call any time. */
export async function refreshState() {
  const seq = ++refreshSeq;
  try {
    const s = await get("state");
    if (seq !== refreshSeq) return;
    setState({
      booted: true,
      loadError: null,
      authError: false,
      version: s.version || (s.self && s.self.version) || "",
      configured: s.configured !== false,
      self: s.self || null,
      peers: Array.isArray(s.peers) ? s.peers : [],
      transfers: Array.isArray(s.transfers) ? s.transfers : [],
      counters: { mail: 0, chat: 0, offers: 0, ...(s.counters || {}) },
      invites: Array.isArray(s.invites) ? s.invites : [],
      settings: s.settings || null,
    });
    emit("refreshed", s);
  } catch (e) {
    if (seq !== refreshSeq) return;
    if (e.code === "unauthorized") setState({ booted: true, authError: true });
    else setState({ booted: true, loadError: state.self ? null : e });
  }
}

function parse(ev) {
  try { return JSON.parse(ev.data); } catch { return null; }
}

const handlers = {
  hello(d) {
    if (d && d.serverTime) setState({ serverOffset: Math.round(d.serverTime - Date.now() / 1000) });
  },
  self(d) { if (d) setState({ self: d, configured: d.configured !== false }); },
  peers(d) { if (Array.isArray(d)) setState({ peers: d }); },
  transfer(d) { if (d && d.id) upsertTransfer(d); },
  "transfer.removed"(d) { if (d && d.id) removeTransfer(d.id); },
  mail(d) { emit("mail", d); },
  chat(d) { emit("chat", d); },
  counters(d) { if (d) setState({ counters: { ...state.counters, ...d } }); },
  notify(d) {
    if (!d) return;
    toast({ level: d.level || "info", title: d.title || "", text: d.text || "", link: d.link || null });
  },
  invites(d) { if (Array.isArray(d)) setState({ invites: d }); },
  shares(d) { if (Array.isArray(d)) { setState({ shares: d }); emit("shares", d); } },
  forwards(d) { if (Array.isArray(d)) { setState({ forwards: d }); emit("forwards", d); } },
};

function scheduleReconnect() {
  clearTimeout(retryTimer);
  // 0.5s, 1s, 2s, 4s … capped at 15s, with ±20% jitter.
  const base = Math.min(15000, 500 * 2 ** Math.min(attempt, 5));
  const delay = Math.round(base * (0.8 + Math.random() * 0.4));
  attempt++;
  setState({ nextRetryAt: Date.now() + delay });
  retryTimer = setTimeout(connect, delay);
}

export function connect() {
  clearTimeout(retryTimer);
  if (es) { es.close(); es = null; }
  if (state.conn !== "online") setState({ conn: state.conn === "offline" ? "offline" : "connecting" });
  const src = new EventSource("api/events");
  es = src;
  src.onopen = () => {
    if (es !== src) return;
    attempt = 0;
    clearTimeout(offlineTimer);
    setState({ conn: "online", nextRetryAt: 0 });
    refreshState();
  };
  src.onerror = () => {
    if (es !== src) return;
    src.close();
    es = null;
    // Show the banner only if the link stays down for a moment (avoids flashes).
    clearTimeout(offlineTimer);
    offlineTimer = setTimeout(() => { if (!es || es.readyState !== 1) setState({ conn: "offline" }); }, 1200);
    // The error may be a 401 (EventSource cannot tell us) — the state call can.
    refreshState();
    scheduleReconnect();
  };
  for (const [type, fn] of Object.entries(handlers)) {
    src.addEventListener(type, (ev) => { if (es === src) fn(parse(ev)); });
  }
}

/** Reconnect immediately (e.g. "Retry now" button, tab became visible). */
export function reconnectNow() {
  attempt = 0;
  connect();
}

export function startLive() {
  refreshState();
  connect();
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible" && (!es || es.readyState === 2)) reconnectNow();
  });
  window.addEventListener("online", () => { if (!es || es.readyState === 2) reconnectNow(); });
}
