// Reusable hooks.
import { useCallback, useEffect, useRef, useState } from "../vendor/preact-htm.js";

/** Re-render every `ms` milliseconds; returns Date.now(). Pauses while the tab is hidden. */
export function useNow(ms = 1000) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    let id = setInterval(() => { if (document.visibilityState !== "hidden") setNow(Date.now()); }, ms);
    return () => clearInterval(id);
  }, [ms]);
  return now;
}

/**
 * Load data with an async function. Re-runs when deps change, aborts stale runs.
 * Returns { data, error, loading, reload, setData }.
 */
export function useAsync(fn, deps = []) {
  const [st, setSt] = useState({ data: undefined, error: null, loading: true });
  const seq = useRef(0);
  const fnRef = useRef(fn);
  fnRef.current = fn;
  const run = useCallback((silent = false) => {
    const my = ++seq.current;
    const ac = new AbortController();
    if (!silent) setSt((s) => ({ ...s, loading: true, error: null }));
    Promise.resolve()
      .then(() => fnRef.current(ac.signal))
      .then((data) => { if (my === seq.current) setSt({ data, error: null, loading: false }); })
      .catch((error) => {
        if (error && error.name === "AbortError") return;
        if (my === seq.current) setSt((s) => ({ data: silent ? s.data : undefined, error, loading: false }));
      });
    return () => ac.abort();
  }, []);
  useEffect(() => run(false), deps);
  const setData = useCallback((upd) => setSt((s) => ({ ...s, data: typeof upd === "function" ? upd(s.data) : upd })), []);
  return { ...st, reload: run, setData };
}

/** Match a media query, e.g. useMedia("(max-width: 759px)"). */
export function useMedia(query) {
  const get = () => (window.matchMedia ? matchMedia(query).matches : false);
  const [m, setM] = useState(get);
  useEffect(() => {
    const mq = matchMedia(query);
    const fn = () => setM(mq.matches);
    mq.addEventListener ? mq.addEventListener("change", fn) : mq.addListener(fn);
    fn();
    return () => (mq.removeEventListener ? mq.removeEventListener("change", fn) : mq.removeListener(fn));
  }, [query]);
  return m;
}

export const MOBILE_QUERY = "(max-width: 759px)";
export function useIsMobile() {
  return useMedia(MOBILE_QUERY);
}

/** Interval that only runs while `active` and the page is visible. */
export function useInterval(fn, ms, active = true) {
  const ref = useRef(fn);
  ref.current = fn;
  useEffect(() => {
    if (!active || !ms) return undefined;
    const id = setInterval(() => { if (document.visibilityState !== "hidden") ref.current(); }, ms);
    return () => clearInterval(id);
  }, [ms, active]);
}

/** Window-level event listener bound to the component lifetime. */
export function useWindowEvent(type, fn, active = true) {
  const ref = useRef(fn);
  ref.current = fn;
  useEffect(() => {
    if (!active) return undefined;
    const h = (e) => ref.current(e);
    window.addEventListener(type, h);
    return () => window.removeEventListener(type, h);
  }, [type, active]);
}

/** Persisted (localStorage) state for small per-view preferences. */
export function usePersistent(key, initial) {
  const [v, setV] = useState(() => {
    try {
      const s = localStorage.getItem(key);
      return s === null ? initial : JSON.parse(s);
    } catch { return initial; }
  });
  const set = useCallback((nv) => {
    setV((old) => {
      const val = typeof nv === "function" ? nv(old) : nv;
      try { localStorage.setItem(key, JSON.stringify(val)); } catch { /* ignore */ }
      return val;
    });
  }, [key]);
  return [v, set];
}

/** Keep the previous non-undefined value (avoids flashing empty states while reloading). */
export function useLatest(value) {
  const ref = useRef(value);
  if (value !== undefined) ref.current = value;
  return ref.current;
}
