// Hash router: "#/files/browse/<dev>/<share>/a/b?x=1" → { parts: [...], query }.
// Every path segment is URI-encoded on its own, so names may contain any
// characters without breaking the route.
import { useEffect, useState } from "../vendor/preact-htm.js";

export function parseHash(hash = location.hash) {
  const h = hash.replace(/^#\/?/, "");
  const qi = h.indexOf("?");
  const p = qi >= 0 ? h.slice(0, qi) : h;
  const q = qi >= 0 ? h.slice(qi + 1) : "";
  const parts = p.split("/").filter(Boolean).map((s) => {
    try { return decodeURIComponent(s); } catch { return s; }
  });
  return { parts, query: new URLSearchParams(q), raw: hash };
}

/** Build a hash href from segments (each encoded) and an optional query object. */
export function href(parts, query) {
  const path = "#/" + parts.filter((x) => x !== undefined && x !== null && x !== "")
    .map((s) => encodeURIComponent(String(s))).join("/");
  if (!query) return path;
  const q = new URLSearchParams();
  for (const [k, v] of Object.entries(query)) if (v !== undefined && v !== null && v !== "") q.set(k, v);
  const qs = q.toString();
  return qs ? `${path}?${qs}` : path;
}

let current = parseHash();
const subs = new Set();
// A trail of visited hashes lets "back" buttons use history.back() when the
// previous entry is ours, and fall back to a parent route otherwise (deep links).
const trail = [location.hash || "#/"];
let replacing = false;

window.addEventListener("hashchange", () => {
  const h = location.hash || "#/";
  if (replacing) {
    trail[trail.length - 1] = h;
    replacing = false;
  } else if (trail.length > 1 && trail[trail.length - 2] === h) {
    trail.pop(); // looks like a back navigation
  } else {
    trail.push(h);
    if (trail.length > 100) trail.shift();
  }
  current = parseHash();
  for (const fn of Array.from(subs)) fn(current);
});

export function go(to, { replace = false } = {}) {
  if (location.hash === to) return;
  if (replace) {
    replacing = true;
    location.replace(to.startsWith("#") ? to : "#" + to);
  } else {
    location.hash = to;
  }
}

/** Return to the previous in-app screen, or to `fallback` when there is none. */
export function back(fallback) {
  if (trail.length > 1) history.back();
  else go(fallback, { replace: true });
}

export function useRoute() {
  const [r, setR] = useState(current);
  useEffect(() => {
    const fn = (x) => setR(x);
    subs.add(fn);
    if (r !== current) setR(current);
    return () => subs.delete(fn);
  }, []);
  return r;
}

export function currentRoute() {
  return current;
}
