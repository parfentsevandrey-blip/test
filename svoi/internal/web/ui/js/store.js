// Tiny global store: one mutable state object, immutable updates of its
// fields, batched notifications and a selector hook. Plus a small event bus
// for SSE events that views react to (mail, chat, …).
import { useLayoutEffect, useReducer, useRef } from "../vendor/preact-htm.js";

export const state = {
  booted: false,        // first /api/state finished (successfully or not)
  conn: "connecting",   // SSE link to the local node: connecting | online | offline
  authError: false,     // 401 from the node → full-screen "session expired"
  signedOut: false,     // the user signed out here (the same screen says "you signed out")
  loadError: null,      // first /api/state failed for another reason
  serverOffset: 0,      // node clock − browser clock, seconds
  version: "",
  configured: true,
  removed: null,        // {meshName, at} while outside any mesh because an admin removed this device
  self: null,
  peers: [],
  transfers: [],
  counters: { mail: 0, chat: 0, offers: 0 },
  invites: [],
  nearby: { visible: true, devices: [], join: { state: "idle" }, requests: [] }, // devices around that can add this one; its own request; requests of others (admin)
  nearbyAsk: null,      // the id of the request whose dialog is open on this (admin) device
  settings: null,
  shares: null,         // local shares (null = not loaded yet)
  forwards: null,       // local forwards (null = not loaded yet)
  toasts: [],
  dialogs: [],
  help: false,          // the «Как это работает?» sheet is open
  lang: "ru",
  theme: "auto",
  skin: "classic",     // look of the surfaces: "glass" | "classic" (css/glass.css)
};

const listeners = new Set();
let queued = false;

function flush() {
  queued = false;
  for (const fn of Array.from(listeners)) {
    try { fn(); } catch (e) { console.error(e); }
  }
}

// How many `nearby` events the node has sent. A snapshot (the answer to a request, the state) that was on its way while
// an event came is older than that event and must not undo it: the removal of a request that was just answered, for one.
let nearbyClock = 0;
export const nearbyEvents = () => nearbyClock;
export const noteNearbyEvent = () => ++nearbyClock;

/** What GET api/nearby / the `nearby` event / `state.nearby` carry, with the gaps filled in (an older node says nothing). */
export function nearbyOf(d) {
  const v = d && typeof d === "object" ? d : {};
  return {
    visible: v.visible !== false,
    devices: Array.isArray(v.devices) ? v.devices : [],
    join: v.join && typeof v.join === "object" && v.join.state ? v.join : { state: "idle" },
    requests: Array.isArray(v.requests) ? v.requests : [],
  };
}

/** Shallow-merge a patch (or the result of fn(state)) into the state. */
export function setState(patch) {
  const p = typeof patch === "function" ? patch(state) : patch;
  if (!p) return;
  Object.assign(state, p);
  if (!queued) {
    queued = true;
    queueMicrotask(flush);
  }
}

export function subscribe(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

function shallowEqual(a, b) {
  if (Object.is(a, b)) return true;
  if (!a || !b || typeof a !== "object" || typeof b !== "object") return false;
  if (Array.isArray(a) !== Array.isArray(b)) return false;
  const ka = Object.keys(a), kb = Object.keys(b);
  if (ka.length !== kb.length) return false;
  for (const k of ka) if (!Object.is(a[k], b[k])) return false;
  return true;
}

/** Re-renders the component when the selected slice changes (shallow compare). */
export function useStore(selector) {
  const [, force] = useReducer((x) => x + 1, 0);
  const sel = useRef(selector);
  sel.current = selector;
  const value = selector(state);
  const last = useRef(value);
  last.current = value;
  // Subscribe in a layout effect (synchronously after the commit) and re-check
  // right away: a passive effect runs a frame later, and an update landing in
  // between (e.g. a fast first /api/state) would otherwise be lost for good.
  useLayoutEffect(() => {
    const check = () => {
      if (!shallowEqual(sel.current(state), last.current)) force();
    };
    const off = subscribe(check);
    check();
    return off;
  }, []);
  return value;
}

// ---------- event bus ----------
const bus = new Map();

export function on(type, fn) {
  if (!bus.has(type)) bus.set(type, new Set());
  bus.get(type).add(fn);
  return () => bus.get(type).delete(fn);
}

export function emit(type, data) {
  const set = bus.get(type);
  if (!set) return;
  for (const fn of Array.from(set)) {
    try { fn(data); } catch (e) { console.error(e); }
  }
}

/** Subscribe to a bus event for the lifetime of a component. */
export function useEvent(type, fn) {
  const ref = useRef(fn);
  ref.current = fn;
  useLayoutEffect(() => on(type, (d) => ref.current(d)), [type]);
}

// ---------- helpers for common slices ----------
export function upsertTransfer(tr) {
  setState((s) => {
    const list = s.transfers.slice();
    const i = list.findIndex((x) => x.id === tr.id);
    if (i >= 0) list[i] = { ...list[i], ...tr };
    else list.unshift(tr);
    return { transfers: list };
  });
}

// How far a transfer has got. Live events always win (they are the truth, in order); the answer to a
// request is a snapshot taken when it was served and may be read after the events that followed it.
const TRANSFER_RANK = { offered: 0, queued: 1, active: 2, done: 3, failed: 3, declined: 3, canceled: 3 };

/**
 * Merge the answer to a request (send, accept, cancel, retry) into the list without ever taking a transfer
 * back to an earlier state: a small file can be done before the answer reaches the page, and the stale
 * answer would otherwise leave it «sending» for good. (A retry shows up through its own live event.)
 */
export function mergeTransferAnswer(tr) {
  setState((s) => {
    const i = s.transfers.findIndex((x) => x.id === tr.id);
    if (i < 0) return { transfers: [tr, ...s.transfers] };
    if ((TRANSFER_RANK[tr.state] ?? 0) < (TRANSFER_RANK[s.transfers[i].state] ?? 0)) return null;
    const list = s.transfers.slice();
    list[i] = { ...list[i], ...tr };
    return { transfers: list };
  });
}

export function removeTransfer(id) {
  setState((s) => ({ transfers: s.transfers.filter((x) => x.id !== id) }));
}

export function peerById(id) {
  if (!id) return null;
  if (state.self && (id === "self" || id === state.self.id)) return null;
  return state.peers.find((p) => p.id === id) || null;
}

/** Node "now" in Unix seconds (corrected by the clock offset seen in `hello`). */
export function nowSec() {
  return Math.floor(Date.now() / 1000) + (state.serverOffset || 0);
}
