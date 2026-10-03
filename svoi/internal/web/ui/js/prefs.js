// Client-side preferences (language, theme, small view settings) in
// localStorage. Keys must match js/boot.js.
import { setLang } from "./i18n.js";
import { setState } from "./store.js";

const K_THEME = "svoi.theme";
const K_LANG = "svoi.lang";

export function load(key, fallback) {
  try {
    const v = localStorage.getItem(key);
    return v === null ? fallback : JSON.parse(v);
  } catch { return fallback; }
}

export function save(key, value) {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* ignore */ }
}

function raw(key, fallback) {
  try { return localStorage.getItem(key) || fallback; } catch { return fallback; }
}

function setRaw(key, v) {
  try { localStorage.setItem(key, v); } catch { /* ignore */ }
}

const mqLight = window.matchMedia ? matchMedia("(prefers-color-scheme: light)") : null;

export function themePref() { return raw(K_THEME, "auto"); }
export function langPref() { return raw(K_LANG, "auto"); }

function resolveTheme(pref) {
  if (pref === "light" || pref === "dark") return pref;
  return mqLight && mqLight.matches ? "light" : "dark";
}

function applyTheme() {
  const th = resolveTheme(themePref());
  document.documentElement.setAttribute("data-theme", th);
  const meta = document.querySelectorAll('meta[name="theme-color"]');
  const color = th === "light" ? "#f5f3ee" : "#0d1012";
  meta.forEach((m) => { m.setAttribute("content", color); m.removeAttribute("media"); });
  setState({ theme: th });
}

export function setThemePref(v) {
  setRaw(K_THEME, v);
  applyTheme();
}

export function detectLang() {
  const nav = (navigator.languages && navigator.languages[0]) || navigator.language || "ru";
  return /^(ru|uk|be|kk)\b/i.test(nav) ? "ru" : "en";
}

function resolveLang(pref) {
  return pref === "ru" || pref === "en" ? pref : detectLang();
}

export function setLangPref(v) {
  setRaw(K_LANG, v);
  const l = resolveLang(v);
  setLang(l);
  setState({ lang: l });
}

export function initPrefs() {
  const l = resolveLang(langPref());
  setLang(l);
  setState({ lang: l });
  applyTheme();
  if (mqLight && mqLight.addEventListener) {
    mqLight.addEventListener("change", () => { if (themePref() === "auto") applyTheme(); });
  }
}
