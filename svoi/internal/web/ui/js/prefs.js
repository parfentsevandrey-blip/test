// Client-side preferences (language, theme, look, effects, small view settings) in
// localStorage. Keys must match js/boot.js.
import { setLang } from "./i18n.js";
import { setState } from "./store.js";

const K_THEME = "themesh.theme";
const K_LANG = "themesh.lang";
const K_SKIN = "themesh.skin";
const K_FX = "themesh.fx";
const K_HAPTICS = "themesh.haptics";

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
const mqCalm = window.matchMedia ? matchMedia("(prefers-reduced-motion: reduce)") : null;

// "auto" | "light" | "dark" | "evening". In the "rosa" look "auto" is the real sky (see js/sky-palette.js) and "evening" is
// the blue hour; elsewhere "auto" follows the system and "evening" is the dark theme.
export function themePref() { return raw(K_THEME, "auto"); }
export function langPref() { return raw(K_LANG, "auto"); }
// "auto" | "rosa" | "glass" | "classic": the look of the surfaces (css/rosa.css over css/glass.css, or css/glass.css alone);
// auto means the one the app starts with (rosa in the phone app, glass in the Mac app, classic elsewhere, see js/boot.js).
export function skinPref() { return raw(K_SKIN, "auto"); }
// "auto" | "full" | "calm" | "still": how much the page moves (the "rosa" look; see js/rosa.js).
export function fxPref() { return raw(K_FX, "auto"); }
export function hapticsOn() { return raw(K_HAPTICS, "on") !== "off"; }

const root = () => document.documentElement;
export const isRosa = () => root().getAttribute("data-look") === "rosa";

function resolveSkin() {
  const r = root();
  const pref = skinPref();
  const asked = r.getAttribute("data-skin-url"); // "?skin=" in the address (tests, screenshots): wins until a style is picked
  let skin = asked || (pref === "rosa" || pref === "glass" || pref === "classic" ? pref : r.getAttribute("data-skin-default") || "classic");
  if (skin === "rosa" && !window.themeshSky) skin = "glass";
  return skin;
}

// The phone app paints the system bars (and the backdrop behind a see-through page) to match the page: tell it the look
// (js/boot.js does at start). In the "rosa" look it also gets the colours of the sky.
function tellShell() {
  try {
    const r = root();
    const shell = window.themeshShell;
    if (r.getAttribute("data-shell") !== "android" || !shell || typeof shell.look !== "function") return;
    const rosa = isRosa();
    shell.look(r.getAttribute("data-theme"), rosa ? "rosa" : r.getAttribute("data-skin"));
    const sky = window.themeshSky;
    if (rosa && sky && sky.last && typeof shell.sky === "function") shell.sky(sky.last.vars["--sky-top"], sky.last.vars["--sky-bottom"], sky.last.palette.isLight);
  } catch { /* the app is not there */ }
}

let fade = 0;
/** Moves the custom properties of the sky from one picture to the other (the sky "melts" when the mood is changed). */
function crossfade(from, to, halfway) {
  const sky = window.themeshSky;
  cancelAnimationFrame(fade);
  const r = root();
  let flipped = false;
  const flip = () => { if (!flipped) { flipped = true; halfway(); } };
  if (!sky || !from || fx() === "still") {
    // no gradual change (the person asked for no motion): the new sky at once
    for (const k in to) r.style.setProperty(k, to[k]);
    flip();
    return;
  }
  const t0 = performance.now();
  const step = (now) => {
    const t = Math.min(1, (now - t0) / 700);
    const v = sky.tween(from, to, t * t * (3 - 2 * t));
    for (const k in v) r.style.setProperty(k, v[k]);
    if (t >= 0.5) flip();
    if (t < 1) fade = requestAnimationFrame(step);
  };
  fade = requestAnimationFrame(step);
}
const fx = () => root().getAttribute("data-fx");

/**
 * Puts the look on the page: the skin ("rosa" is a layer over "glass": both attributes), and the theme, which in the
 * "rosa" look comes from the sky. `smooth`: the person changed something, so the sky changes gradually.
 */
function applyLook(smooth) {
  const r = root();
  const sky = window.themeshSky;
  const skin = resolveSkin();
  const pref = themePref();
  const before = smooth && isRosa() && sky && sky.last ? sky.last.vars : null;
  const was = r.getAttribute("data-theme");
  r.setAttribute("data-skin", skin === "classic" ? "classic" : "glass");
  let theme;
  if (skin === "rosa") {
    r.setAttribute("data-look", "rosa");
    const fixed = r.getAttribute("data-sky-fixed");
    const lit = sky.apply(r, pref, fixed === null ? undefined : Number(fixed));
    theme = lit.theme;
    if (before) {
      // the colours fade over the old picture; the type (dark on pale, light on vivid) changes halfway
      for (const k in before) r.style.setProperty(k, before[k]);
      r.setAttribute("data-theme", was);
      crossfade(before, lit.vars, () => r.setAttribute("data-theme", theme));
    }
  } else {
    if (r.hasAttribute("data-look")) {
      r.removeAttribute("data-look");
      if (sky) sky.clear(r);
    }
    theme = pref === "light" ? "light" : pref === "dark" || pref === "evening" ? "dark" : mqLight && mqLight.matches ? "light" : "dark";
    r.setAttribute("data-theme", theme);
  }
  let color = theme === "light" ? "#f5f3ee" : "#0d1012";
  if (skin === "rosa") color = sky.css(sky.last.palette.zenith);
  document.querySelectorAll('meta[name="theme-color"]').forEach((m) => { m.setAttribute("content", color); m.removeAttribute("media"); });
  // The phone app paints the backdrop of the glass look behind a see-through page (css/glass.css 8b); the page of Роса paints its own sky
  try {
    if (r.getAttribute("data-shell") === "android" && window.themeshShell && typeof window.themeshShell.look === "function") {
      if (skin === "rosa") r.removeAttribute("data-native-backdrop");
      else r.setAttribute("data-native-backdrop", "");
    }
  } catch { /* no bridge */ }
  setState({ theme, skin, appearance: r.getAttribute("data-appearance") || "" });
  tellShell();
}

export function setSkinPref(v) {
  setRaw(K_SKIN, v);
  root().removeAttribute("data-skin-url");
  applyLook(true);
}

export function setThemePref(v) {
  setRaw(K_THEME, v);
  applyLook(true);
}

/** "auto" is what the window can do (fx= in the user agent, see js/boot.js), else full motion, unless the system asks for less;
 *  "full", "calm" and "still" are the person's choice. */
function resolveFx(pref) {
  if (pref === "full" || pref === "calm" || pref === "still") return pref;
  return mqCalm && mqCalm.matches ? "still" : root().getAttribute("data-fx-default") || "full";
}

function applyFx() {
  root().setAttribute("data-fx", resolveFx(fxPref()));
  window.dispatchEvent(new window.Event("themesh-fx"));
}

export function setFxPref(v) {
  setRaw(K_FX, v);
  applyFx();
}

export function setHapticsPref(on) {
  setRaw(K_HAPTICS, on ? "on" : "off");
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
  applyLook(false);
  root().setAttribute("data-fx", resolveFx(fxPref()));
  if (mqLight && mqLight.addEventListener) {
    mqLight.addEventListener("change", () => { if (!isRosa() && themePref() === "auto") applyLook(false); });
  }
  if (mqCalm && mqCalm.addEventListener) {
    mqCalm.addEventListener("change", () => { if (fxPref() === "auto") applyFx(); });
  }
  // the real sky moves: a new minute, a new colour (the sun rises about a quarter of a degree a minute)
  const tick = () => { if (isRosa() && themePref() === "auto" && root().getAttribute("data-sky-fixed") === null) applyLook(false); };
  setInterval(tick, 60000);
  document.addEventListener("visibilitychange", () => { if (!document.hidden) tick(); });
}
