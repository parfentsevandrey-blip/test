// The motion of the "Роса" look (css/rosa.css says how everything looks; this file makes it move):
//   arrival   panes rise into place one after another when a screen opens, and catch a wave of light on the way;
//   touch     the glass lights up from the point of a finger and follows it;
//   depth     a pane that slides up under the top bar tips back and shrinks a little;
//   lens      the tab bar carries a drop of glass: it flows to the tab that opens, and lifts under a finger and follows it
//             across the tabs (a tick at each);
//   haptics   a soft tick on presses and on the lens (the phone app's bridge, `themeshShell.haptic`, or the vibrator).
// Everything here sits on html[data-look="rosa"] and html[data-fx]: "full" does all of it, "calm" only fades panes in,
// "still" nothing. It touches the DOM that Preact draws only through data-* attributes and custom properties, never classes
// (a re-render rewrites class names), and does nothing at all in the other looks.
import { hapticsOn } from "./prefs.js";

const root = document.documentElement;
// The panes that arrive, light up and tip back (the same list as the panes of css/glass.css, without the big sheets of Mail and Chat)
const PANES = ".card, .home-card, .home-act, .hdev, .dcard, .onb-choice, .onb-form, .nearby, .flist, .svc-group, .more-self";
// What arrives without being a pane of glass
const PLAIN = ".page-head, .home-status, .home-sec__head, .home-actions__grid > *";
const STEP = 70;       // ms between two panes of a cascade
const WINDOW = 700;    // ms after the first pane of a screen in which its panes still count as arriving
const SLOP = 10;       // px: a finger that has moved so far is scrolling, not pressing

const rosa = () => root.getAttribute("data-look") === "rosa";
const fx = () => root.getAttribute("data-fx") || "full";
const clamp = (x, lo, hi) => (x < lo ? lo : x > hi ? hi : x);

// ------------------------------------------------------------------ haptics
export function haptic(kind) {
  if (!rosa() || !hapticsOn()) return;
  try {
    const shell = window.themeshShell;
    if (shell && typeof shell.haptic === "function") shell.haptic(kind);
    else if (navigator.vibrate && kind !== "press") navigator.vibrate(kind === "tick" ? 6 : 10);
  } catch { /* no vibrator */ }
}

// ------------------------------------------------------------------ arrival
let openedAt = null;   // when the first pane of this screen showed up
let order = 0;         // how many have arrived in this screen's cascade
let watching = false;  // the screen is new: its first panes are still to come

function newScreen() {
  watching = true;
  openedAt = null;
  order = 0;
}

function arrive(panes) {
  const now = performance.now();
  for (const el of panes) {
    if (el.hasAttribute("data-rz")) continue;
    el.setAttribute("data-rz", "");
    if (!watching || fx() === "still") continue;
    if (openedAt === null) openedAt = now;
    if (now - openedAt > WINDOW) { watching = false; continue; }
    const r = el.getBoundingClientRect();
    if (r.bottom < 0 || r.top > window.innerHeight) continue; // below the fold: it appears when scrolled to, without ceremony
    el.style.setProperty("--rz-delay", Math.min(order, 9) * STEP + "ms");
    order++;
    el.setAttribute("data-rz-in", "");
    el.addEventListener("animationend", (e) => { if (e.target === el && e.animationName === "rz-sweep") { el.removeAttribute("data-rz-in"); el.style.removeProperty("--rz-delay"); } }, { once: false });
    // (calm: only a fade, which ends sooner than the sweep would; the attribute is taken off after it too)
    if (fx() === "calm") setTimeout(() => { el.removeAttribute("data-rz-in"); el.style.removeProperty("--rz-delay"); }, 600 + order * STEP);
  }
}

/** Panes that have just come into the page start their arrival before the next frame is drawn: no frame shows them settled first. */
function scanNow() {
  if (!rosa()) return;
  const all = [...document.querySelectorAll(PANES + ", " + PLAIN)];
  const fresh = all.filter((el) => !el.hasAttribute("data-rz"));
  // a screen that replaced the old one without a change of the address (the first screen after the network was made, say)
  if (fresh.length && fresh.length === all.length) newScreen();
  arrive(fresh);
  lensSetup();
}

let queued = false;
function scan() {
  scanNow();
  if (queued) return;
  queued = true;
  requestAnimationFrame(() => {
    queued = false;
    if (!rosa()) return;
    lensPlace();
    reach();
  });
}

// ------------------------------------------------------------------ touch: the light under a finger
let lit = null, litAt = null, litTimer = 0;

function touchMove(e) {
  if (!lit) return;
  const r = lit.getBoundingClientRect();
  lit.style.setProperty("--tx", e.clientX - r.left + "px");
  lit.style.setProperty("--ty", e.clientY - r.top + "px");
  if (litAt && Math.hypot(e.clientX - litAt.x, e.clientY - litAt.y) > SLOP * 2.5) touchEnd();
}

function touchEnd() {
  clearTimeout(litTimer);
  if (lit) lit.removeAttribute("data-touched");
  lit = null;
  litAt = null;
}

function touchStart(e) {
  if (!rosa() || fx() !== "full" || (e.pointerType === "mouse" && e.button !== 0)) return;
  const el = e.target.closest && e.target.closest(PANES);
  if (!el) return;
  const r = el.getBoundingClientRect();
  el.style.setProperty("--tx", e.clientX - r.left + "px");
  el.style.setProperty("--ty", e.clientY - r.top + "px");
  litAt = { x: e.clientX, y: e.clientY };
  // a scroll that starts right away never lights up
  litTimer = setTimeout(() => { lit = el; el.setAttribute("data-touched", ""); }, 70);
}

// ------------------------------------------------------------------ depth: panes tip back as they slide up under the top bar
let reaching = false;
let tipped = new Set();

function reach() {
  if (reaching) return;
  reaching = true;
  requestAnimationFrame(() => {
    reaching = false;
    if (!rosa()) return;
    const bar = document.querySelector(".topbar");
    const edge = bar ? bar.getBoundingClientRect().bottom : 0;
    const on = fx() === "full";
    const now = new Set();
    const writes = [];
    if (on && edge > 0) {
      for (const el of document.querySelectorAll(PANES)) {
        const r = el.getBoundingClientRect();
        if (r.top >= edge || r.bottom <= 0 || r.height < 24) continue;
        const gone = clamp((edge - r.top) / (r.height * 0.9), 0, 1);
        if (gone > 0.004) { writes.push([el, gone]); now.add(el); }
      }
    }
    for (const [el, gone] of writes) {
      el.style.setProperty("--rz-gone", gone.toFixed(3));
      el.setAttribute("data-receding", "");
    }
    for (const el of tipped) {
      if (!now.has(el)) { el.removeAttribute("data-receding"); el.style.removeProperty("--rz-gone"); }
    }
    tipped = now;
  });
}

// ------------------------------------------------------------------ the lens of the tab bar
function bar() { return document.querySelector(".tabbar"); }

function lensPlace() {
  const nav = bar();
  if (!nav || nav.hasAttribute("data-dragging")) return;
  const on = nav.querySelector(".tabbar__item.is-active");
  if (!on || !nav.offsetWidth) return;
  nav.style.setProperty("--lens-x", on.offsetLeft + "px");
  nav.style.setProperty("--lens-w", on.offsetWidth + "px");
  if (!nav.hasAttribute("data-ready")) requestAnimationFrame(() => nav.setAttribute("data-ready", "")); // the first placement is not animated
}

let drag = null;

function lensItems(nav) { return [...nav.querySelectorAll(".tabbar__item")]; }

function lensDown(e) {
  if (!rosa() || (e.pointerType === "mouse" && e.button !== 0)) return;
  const nav = e.currentTarget;
  if (!nav.offsetWidth) return;
  drag = { nav, x0: e.clientX, id: e.pointerId, moved: false, under: null, items: lensItems(nav) };
  try { nav.setPointerCapture(e.pointerId); } catch { /* the pointer is gone */ }
  nav.setAttribute("data-lifted", "");
  haptic("press");
  lensFollow(e.clientX);
}

function lensFollow(clientX) {
  const { nav, items } = drag;
  const nr = nav.getBoundingClientRect();
  const cur = nav.querySelector(".tabbar__item.is-active");
  const w = cur ? cur.offsetWidth : nr.width / items.length;
  const lo = items[0].offsetLeft, hi = items[items.length - 1].offsetLeft;
  const x = clamp(clientX - nr.left - w / 2, lo, hi);
  nav.setAttribute("data-dragging", "");
  nav.style.setProperty("--lens-x", x + "px");
  nav.style.setProperty("--lens-w", w + "px");
  const mid = x + w / 2;
  let under = items.find((it) => mid >= it.offsetLeft && mid < it.offsetLeft + it.offsetWidth) || items[items.length - 1];
  if (under !== drag.under) {
    if (drag.under) drag.under.removeAttribute("data-under");
    under.setAttribute("data-under", "");
    if (drag.under) haptic("tick");
    drag.under = under;
  }
}

function lensMove(e) {
  if (!drag || e.pointerId !== drag.id) return;
  if (!drag.moved && Math.abs(e.clientX - drag.x0) > SLOP) drag.moved = true;
  lensFollow(e.clientX);
}

function lensUp(e) {
  if (!drag || e.pointerId !== drag.id) return;
  const { nav, under, items } = drag;
  const cancelled = e.type === "pointercancel";
  nav.removeAttribute("data-lifted");
  nav.removeAttribute("data-dragging");
  items.forEach((it) => it.removeAttribute("data-under"));
  drag = null;
  if (cancelled || !under) { lensPlace(); return; }
  // the lens settles on the tab under the finger at once (the tab itself opens a moment later, when the router has run)
  nav.style.setProperty("--lens-x", under.offsetLeft + "px");
  nav.style.setProperty("--lens-w", under.offsetWidth + "px");
  const href = under.getAttribute("href");
  haptic("select");
  if (href && href !== location.hash) location.hash = href;
}

function lensSetup() {
  const nav = bar();
  if (!nav || nav.hasAttribute("data-lens")) return;
  nav.setAttribute("data-lens", "");
  nav.addEventListener("pointerdown", lensDown);
  nav.addEventListener("pointermove", lensMove);
  nav.addEventListener("pointerup", lensUp);
  nav.addEventListener("pointercancel", lensUp);
  nav.addEventListener("dragstart", (e) => e.preventDefault());
  // a click that follows a drag is not another tap: the lens already went there (and a keyboard "click" still works)
  nav.addEventListener("click", (e) => { if (rosa() && e.detail > 0) e.preventDefault(); }, true);
}

// ------------------------------------------------------------------ a screen that cannot keep up gets calmer
// The seconds after a screen has been drawn are watched. A screen that shows fewer than 24 frames a second (or misses more than a third of
// them) is stepped down from "full" to "calm" (no sweeping, tilting, twinkling), and if even that is not smooth (under 14 frames a second:
// a phone with a software renderer, an emulator) to "still". What the person chose in Settings → Effects is never overruled.
let stage = 0;       // 0: waiting to judge "full"; 1: stepped down to "calm" by us, waiting to judge that; 2: done
let judging = false;
function judge() {
  if (stage >= 2 || judging) return;
  if (root.getAttribute("data-fx") !== (stage === 0 ? "full" : "calm")) { stage = 2; return; } // somebody else decided
  try { const p = localStorage.getItem("themesh.fx"); if (p && p !== "auto") { stage = 2; return; } } catch { /* storage may be disabled */ }
  judging = true;
  const t0 = performance.now();
  let last = t0, slow = 0, frames = 0;
  const tick = (now) => {
    frames++;
    if (now - last > 34) slow++;
    last = now;
    const spent = now - t0;
    if (spent < 2500) { requestAnimationFrame(tick); return; }
    judging = false;
    if (document.hidden) return; // nothing was drawn while the page was out of sight: nothing is known, the next screen is judged
    const fps = frames * 1000 / spent;
    if (stage === 0) {
      if (fps < 24 || (frames > 20 && slow / frames > 0.35)) { root.setAttribute("data-fx", "calm"); stage = 1; setTimeout(judge, 600); } else stage = 2;
    } else {
      if (fps < 14) root.setAttribute("data-fx", "still");
      stage = 2;
    }
  };
  requestAnimationFrame(tick);
}

// ------------------------------------------------------------------ start
let started = false;
export function startRosa() {
  if (started) return;
  started = true;
  window.addEventListener("hashchange", () => { newScreen(); scan(); });
  newScreen();
  new window.MutationObserver(() => { scan(); judgeSoon(); }).observe(document.getElementById("app") || document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ["class"] });
  // the look can be switched while the page is open
  new window.MutationObserver(() => { if (rosa()) { newScreen(); scan(); } else tipped.forEach((el) => { el.removeAttribute("data-receding"); el.style.removeProperty("--rz-gone"); }); })
    .observe(root, { attributes: true, attributeFilter: ["data-look", "data-fx"] });
  window.addEventListener("scroll", reach, { passive: true });
  window.addEventListener("resize", () => { lensPlace(); reach(); });
  window.addEventListener("themesh-fx", reach);
  document.addEventListener("pointerdown", touchStart, { passive: true, capture: true });
  document.addEventListener("pointermove", touchMove, { passive: true, capture: true });
  document.addEventListener("pointerup", touchEnd, { passive: true, capture: true });
  document.addEventListener("pointercancel", touchEnd, { passive: true, capture: true });
  document.addEventListener("scroll", touchEnd, { passive: true, capture: true });
  // haptic ticks on presses of buttons
  document.addEventListener("pointerdown", (e) => {
    if (rosa() && e.target.closest && e.target.closest(".btn, .icon-btn, .seg__opt, .switch, .skypick__opt") && !e.target.closest(".tabbar")) haptic("press");
  }, { passive: true, capture: true });
  scan();
}

let judgeTimer = 0;
function judgeSoon() {
  clearTimeout(judgeTimer);
  judgeTimer = setTimeout(judge, 900);
}
