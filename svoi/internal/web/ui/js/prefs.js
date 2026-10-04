// Client-side preferences (language, theme, small view settings) in
// localStorage. Keys must match js/boot.js.
import { setLang } from "./i18n.js";
import { setState } from "./store.js";

const K_THEME = "themesh.theme";
const K_LANG = "themesh.lang";
const K_SKIN = "themesh.skin";

export function load(key, fallback) {
  try {
    const v = localStorage.getItem(key);
    return v === null ? fallback : JSON.parse(v);
  } catch { return fallback; }
}

export function save(key, value) {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* ignore */ }
}

// «Начало работы» on Home: steps that leave no trace on the node (looking at
// another device's folders, having sent something) are remembered per browser.
// `name`: "browsed" | "sent" | "startDismissed".
export function startFlag(name) {
  return load("themesh.home." + name, false) === true;
}

export function setStartFlag(name) {
  if (!startFlag(name)) save("themesh.home." + name, true);
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
// "auto" | "glass" | "classic": the skin is the look of the surfaces (css/glass.css); auto means the one the
// app starts with (glass in the Mac app, classic elsewhere, see js/boot.js).
export function skinPref() { return raw(K_SKIN, "auto"); }

function resolveTheme(pref) {
  if (pref === "light" || pref === "dark") return pref;
  return mqLight && mqLight.matches ? "light" : "dark";
}

// The phone app paints the system bars and the backdrop behind the page to match it: tell it the look (js/boot.js does at start).
function tellShell() {
  try {
    const root = document.documentElement;
    if (root.hasAttribute("data-native-backdrop")) window.themeshShell.look(root.getAttribute("data-theme"), root.getAttribute("data-skin"));
  } catch { /* the app is not there */ }
}

function applyTheme() {
  const th = resolveTheme(themePref());
  document.documentElement.setAttribute("data-theme", th);
  const meta = document.querySelectorAll('meta[name="theme-color"]');
  const color = th === "light" ? "#f5f3ee" : "#0d1012";
  meta.forEach((m) => { m.setAttribute("content", color); m.removeAttribute("media"); });
  setState({ theme: th });
  tellShell();
}

function applySkin() {
  const root = document.documentElement;
  const pref = skinPref();
  const asked = root.getAttribute("data-skin-url"); // "?skin=" in the address (tests, screenshots): wins until a style is picked
  const skin = asked || (pref === "glass" || pref === "classic" ? pref : root.getAttribute("data-skin-default") || "classic");
  root.setAttribute("data-skin", skin);
  setState({ skin });
  tellShell();
}

export function setSkinPref(v) {
  setRaw(K_SKIN, v);
  document.documentElement.removeAttribute("data-skin-url");
  applySkin();
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
  applySkin();
  if (mqLight && mqLight.addEventListener) {
    mqLight.addEventListener("change", () => { if (themePref() === "auto") applyTheme(); });
  }
}
