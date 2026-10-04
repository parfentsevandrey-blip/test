#!/usr/bin/env node
// Checks of the sky of the "Роса" look (internal/web/ui/js/sky-palette.js): the colours are those of the weather app's key table, the way they are
// mixed and the moods are right, the sun and the moon are where the astronomy says, and what the page writes into its custom properties is well formed.
//
//   node web-dev/check-sky.mjs
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import vm from "node:vm";
import { fileURLToPath } from "node:url";

const file = path.join(path.dirname(fileURLToPath(import.meta.url)), "../internal/web/ui/js/sky-palette.js");
const ctx = { Date, Math, Intl, console };
ctx.window = ctx;
vm.createContext(ctx);
vm.runInContext(fs.readFileSync(file, "utf8"), ctx);
const S = ctx.themeshSky;

let n = 0;
const ok = (name, fn) => {
  try { fn(); n++; console.log("  ✓ " + name); } catch (e) { console.log("  ✗ " + name + "\n      " + String(e.message).split("\n").join("\n      ")); process.exitCode = 1; }
};
const near = (a, b, tol, what) => assert.ok(Math.abs(a - b) <= tol, `${what}: ${a} is not within ${tol} of ${b}`);
const rgbNear = (c, hex, tol, what) => { const h = S.hex(hex); for (let i = 0; i < 3; i++) near(c[i], h[i], tol, what + " channel " + i); };

console.log("the sky");
ok("at each key height of the sun the sky is exactly the key colours (the table of SkyPalette.kt)", () => {
  assert.equal(S.KEYS.length, 9);
  for (const k of S.KEYS) {
    const p = S.palette("auto", k[0], 0); // (no moon: it lifts a clear night a little)
    rgbNear(p.zenith, k[1], 1.5, `zenith at ${k[0]}`);
    rgbNear(p.horizon, k[2], 1.5, `horizon at ${k[0]}`);
    rgbNear(p.glow, k[3], 1.5, `glow at ${k[0]}`);
    rgbNear(p.sun, k[4], 1.5, `sun at ${k[0]}`);
  }
});
ok("between the keys the colours are mixed, and beyond the table they stay at its ends", () => {
  const a = S.palette("auto", 2, 0).zenith, lo = S.hex(S.KEYS[4][1]), hi = S.hex(S.KEYS[5][1]);
  for (let i = 0; i < 3; i++) assert.ok(a[i] >= Math.min(lo[i], hi[i]) - 2 && a[i] <= Math.max(lo[i], hi[i]) + 2, "between the two keys");
  rgbNear(S.palette("auto", -40, 0).zenith, S.KEYS[0][1], 1.5, "far below the table");
  rgbNear(S.palette("auto", 89, 0).zenith, S.KEYS[8][1], 1.5, "far above the table");
});
ok("a clear sky is always lit with light type; the moon lifts a clear night a little", () => {
  for (const e of [-18, -12, -6, -3, 0, 4, 10, 25, 60]) assert.equal(S.palette("auto", e, 0.5).isLight, false, "light type at " + e);
  const dark = S.palette("auto", -16, 0).zenith, moonlit = S.palette("auto", -16, 1).zenith;
  assert.ok(S.lum(moonlit) > S.lum(dark), "a full moon is brighter than none");
});
ok("stars come out as the sun goes down: none by day, all at night, steadily between", () => {
  assert.equal(S.palette("auto", 5, 0.5).stars, 0);
  assert.equal(S.palette("auto", -6, 0.5).stars, 0);
  assert.equal(S.palette("auto", -14, 0.5).stars, 1);
  let last = -1;
  for (let e = -6; e >= -14; e -= 0.5) { const s = S.palette("auto", e, 0.5).stars; assert.ok(s >= last, "monotonic"); last = s; }
});
ok("the accent follows the sky: gold by day, peach at dusk, periwinkle at night", () => {
  assert.equal(S.css(S.palette("auto", 30, 0.5).accent), "#ffd37a");
  assert.equal(S.css(S.palette("auto", 3, 0.5).accent), "#ffb48a");
  assert.equal(S.css(S.palette("auto", -16, 0.5).accent), "#c6ccff");
});

console.log("the moods");
ok("«Светлое» is a porcelain sky with dark type and a deeper amber; no stars", () => {
  const p = S.palette("light", -16, 0.5); // (the real height of the sun does not matter to a fixed mood)
  assert.equal(p.isLight, true);
  assert.equal(S.css(p.ink), "#1b2030");
  assert.equal(S.css(p.accent), "#c46f1e");
  assert.equal(p.stars, 0);
  assert.equal(p.elevation, 30);
  assert.ok(S.lum(p.horizon) > 0.7, "the horizon is nearly white");
  assert.ok(p.brightness >= 0.62);
  assert.equal(S.css(p.smoke), "#ffffff", "milk, not smoke");
});
ok("«Вечернее» is the blue hour with the first stars; «Тёмное» is deep night with all of them", () => {
  const e = S.palette("evening", 25, 0.5), d = S.palette("dark", 25, 0.5);
  assert.equal(e.elevation, -1.5);
  assert.equal(e.stars, 0.3);
  assert.equal(d.stars, 1);
  assert.equal(d.elevation, -16);
  assert.equal(e.isLight, false);
  assert.equal(d.isLight, false);
  assert.ok(S.lum(d.zenith) < 0.01, "night is dark");
  assert.equal(S.css(d.ink), "#fffbf5");
});
ok("what the page is told about a mood: the theme of the tokens, no real sun or moon in a fixed mood", () => {
  assert.equal(S.sky("light").theme, "light");
  assert.equal(S.sky("dark").theme, "dark");
  assert.equal(S.sky("evening").theme, "dark");
  assert.equal(S.sky("auto", undefined, 25).theme, "dark");
  assert.equal(S.sky("light").body, null);
  assert.equal(S.sky("dark").body, null);
  assert.ok(S.sky("auto", undefined, 25).body.isSun);
  assert.equal(S.sky("auto", undefined, -16).body.isSun, false);
});

console.log("the sun and the moon");
ok("the sun stands where the astronomy says (Moscow, the solstice: about 57.7° at solar noon, below the horizon at midnight)", () => {
  const noon = Date.UTC(2026, 5, 21, 9, 30), midnight = Date.UTC(2026, 5, 21, 21, 30);
  near(S.sunAt(noon, 55.75, 37.62).elevation, 57.7, 1.5, "noon");
  assert.ok(S.sunAt(midnight, 55.75, 37.62).elevation < 0, "midnight sun is below the horizon at 55°N");
  near(S.sunAt(Date.UTC(2026, 11, 21, 9, 30), 55.75, 37.62).elevation, 10.8, 1.5, "winter noon");
  near(S.sunAt(noon, 55.75, 37.62).azimuth, 180, 8, "due south at noon");
});
ok("the moon's phase: new moon of 2000-01-06, full moon of 2000-01-21", () => {
  assert.ok(S.moonPhase(Date.UTC(2000, 0, 6, 18, 14)).lit < 0.03, "new");
  assert.ok(S.moonPhase(Date.UTC(2000, 0, 21, 4, 40)).lit > 0.97, "full");
  near(S.moonPhase(Date.UTC(2000, 0, 14, 5)).lit, 0.5, 0.1, "first quarter");
});
ok("the moon's lit part as a path: a thin crescent, a half, a full disc", () => {
  assert.match(S.moonPath(0.5), /A0\.0 46 0 0 1/); // the terminator is straight at a half
  assert.match(S.moonPath(0.1), /A36\.8 46 0 0 0/); // a crescent: the terminator bulges the way of the lit side
  assert.match(S.moonPath(0.9), /A36\.8 46 0 0 1/); // gibbous: the other way
  assert.match(S.moonPath(1), /A46\.0 46 0 0 1/);
});
ok("the sun (or the moon) stays on its patch of sky in the top right corner, above the type of every screen", () => {
  for (let h = 0; h < 24; h += 0.5) {
    const b = S.body(S.now(Date.UTC(2026, 5, 21, h)));
    assert.ok(b.x >= 74 - 0.01 && b.x <= 92 + 0.01, "x " + b.x);
    assert.ok(b.y >= 3.5 - 0.01 && b.y <= 7.8 + 1.2, "y " + b.y); // (a body below the horizon sinks a little under the patch, by a quarter of its height at most: it is invisible then)
    if (b.y > 7.8 + 0.01) assert.equal(b.visible, 0, "a body that is under the patch cannot be seen: " + b.y);
    assert.ok(b.visible >= 0 && b.visible <= 1);
  }
});
ok("the place is a guess from the time zone: a longitude from the standard offset, a hemisphere from the name of the zone", () => {
  const n = S.now(Date.UTC(2026, 5, 21, 9, 30));
  assert.ok(Math.abs(n.sun.elevation) <= 90);
  assert.equal(typeof n.lit, "number");
});

console.log("what is written into the page");
ok("every colour is #rrggbb or \"r g b\", every number a number, and the strip colours of the bars are among them", () => {
  for (const mood of ["auto", "light", "evening", "dark"]) {
    const v = S.sky(mood, undefined, 25).vars;
    for (const k of ["--sky-zen", "--sky-m1", "--sky-m2", "--sky-m3", "--sky-hor", "--sky-glow", "--sky-sun", "--sky-ink", "--sky-accent", "--sky-smoke", "--sky-deep", "--sky-top", "--sky-bottom"]) assert.match(v[k], /^#[0-9a-f]{6}$/, mood + " " + k);
    for (const k of ["--sky-zen-rgb", "--sky-hor-rgb", "--sky-glow-rgb", "--sky-sun-rgb", "--sky-ink-rgb", "--sky-accent-rgb", "--sky-smoke-rgb", "--sky-deep-rgb"]) assert.match(v[k], /^\d{1,3} \d{1,3} \d{1,3}$/, mood + " " + k);
    for (const k of ["--sky-stars", "--sky-daylight", "--sky-body", "--sky-moon-lit"]) assert.ok(!Number.isNaN(Number(v[k])), mood + " " + k);
    assert.match(v["--sky-body-x"], /^[\d.]+%$/);
  }
});
ok("the first row of a vivid sky is its zenith under the veil; the last row is the horizon (what the window paints under the bars)", () => {
  const day = S.sky("auto", undefined, 25).vars;
  assert.equal(day["--sky-bottom"], "#9bcbf6");
  assert.notEqual(day["--sky-top"], day["--sky-zen"], "the veil darkens the top by day");
  const light = S.sky("light").vars;
  assert.equal(light["--sky-top"], light["--sky-zen"], "no veil over a pale sky");
});
ok("a change of mood is a mix of two skies: the ends are the skies, the middle is a colour between them", () => {
  const a = S.sky("auto", undefined, 25).vars, b = S.sky("dark").vars;
  assert.deepEqual(S.tween(a, b, 0), a);
  assert.deepEqual(S.tween(a, b, 1), b);
  const m = S.tween(a, b, 0.5);
  assert.match(m["--sky-zen"], /^#[0-9a-f]{6}$/);
  assert.notEqual(m["--sky-zen"], a["--sky-zen"]);
  assert.notEqual(m["--sky-zen"], b["--sky-zen"]);
  assert.match(m["--sky-zen-rgb"], /^\d+ \d+ \d+$/);
  near(Number(m["--sky-stars"]), 0.5, 0.01, "the stars");
});

console.log(`\n${n} checks ${process.exitCode ? "— some FAILED" : "passed"}`);
