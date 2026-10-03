// Tiny global store: one mutable state object, immutable updates of its
// fields, batched notifications and a selector hook. Plus a small event bus
// for SSE events that views react to (mail, chat, …).
import { useEffect, useReducer, useRef } from "../vendor/preact-htm.js";

export const state = {
  booted: false,        // first /api/state finished (successfully or not)
  conn: "connecting",   // SSE link to the local node: connecting | online | offline
  authError: false,     // 401 from the node → full-screen "session expired"
  loadError: null,      // first /api/state failed for another reason
  serverOffset: 0,      // node clock − browser clock, seconds
  version: "",
  configured: true,
  self: null,
  peers: [],
  transfers: [],
  counters: { mail: 0, chat: 0, offers: 0 },
  invites: [],
  settings: null,
  shares: null,         // local shares (null = not loaded yet)
  forwards: null,       // local forwards (null = not loaded yet)
  toasts: [],
  dialogs: [],
  lang: "ru",
  theme: "auto",
};

const listeners = new Set();
let queued = false;

function flush() {
  queued = false;
  for (const fn of Array.from(listeners)) {
    try { fn(); } catch (e) { console.error(e); }
  }
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
  useEffect(() => subscribe(() => {
    const next = sel.current(state);
    if (!shallowEqual(next, last.current)) force();
  }), []);
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
  useEffect(() => on(type, (d) => ref.current(d)), [type]);
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
