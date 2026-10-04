#!/usr/bin/env node
// Contrast audit of the interface as it is really drawn.
//
//   NODE_PATH=/opt/node22/lib/node_modules node web-dev/check-contrast.mjs [--skin glass|glass-vibrancy|classic|rosa] [--routes home,mail] [--sizes 1280x800,480x820] [--min 4.5] [--skies auto@25,light] [--dump dir] [--list]
//   (--dump saves the picture of every screen that falls short — the screen as it is without its text, the thing the numbers are made from)
//
//   rosa is the phone app's look: a living sky, so instead of the light and the dark theme it is judged under several skies
//   (--skies: "auto@25" is the real sky with the sun 25 degrees up, "light" and "evening" and "dark" are the fixed moods of it;
//   the default is the whole day: noon, the golden hour, sunset, the blue hour and night, and the three moods).
//
//   glass-vibrancy is the Mac app's window, which is see-through to the system material: its page has no background of its own, so
//   it is judged over a dark-ish and a light-ish stand-in for the material in each theme (the worst cases of what a wallpaper
//   under the window can make of it).
//
// Token tables cannot tell the contrast of text on glass: what is behind a translucent panel is the backdrop, the panel's tint
// and whatever scrolls under it. So this looks at pixels: for every piece of text that is really visible on a screen it takes
// the colour of the text (with its transparency) and the colours the page actually has behind it (a screenshot taken with the
// text made transparent), and works out the WCAG contrast ratio. Normal text needs 4.5:1, large text (24 px, or 18.7 px
// bold) 3:1. The worst 5 % of the pixels behind a piece of text count, not the average.
//
// Exits non-zero if text falls short, so it can guard a change of the look.
import fs from "node:fs";
import path from "node:path";
import { launch, sleep, startMock, watch } from "./lib.mjs";

const argv = process.argv.slice(2);
const arg = (name, def) => (argv.includes("--" + name) ? argv[argv.indexOf("--" + name) + 1] : def);
const mode = arg("skin", "glass");
const skin = mode === "classic" ? "classic" : mode === "rosa" ? "rosa" : "glass";
const SKIES = arg("skies", "auto@60,auto@25,auto@6,auto@0,auto@-6,auto@-16,light,evening,dark").split(",");
const vibrancy = mode === "glass-vibrancy";
const UNDERLAYS = { light: ["#9aa4aa", "#eef2f4"], dark: ["#5a656d", "#0d1114"] }; // stand-ins for the system material, from the dullest it gets to the brightest
const MIN = Number(arg("min", "4.5"));
const LARGE_MIN = Math.min(MIN, 3);
const sizes = arg("sizes", skin === "rosa" ? "360x780,412x860,900x700" : "1280x800,900x700,480x820").split(",").map((s) => s.split("x").map(Number));
const ROUTES = {
  home: "#/home",
  devices: "#/devices",
  "files-send": "#/files/send",
  "files-browse": "#/files/browse",
  "files-shares": "#/files/shares",
  mail: "#/mail/inbox",
  chat: "#/chat",
  services: "#/services",
  settings: "#/settings",
  more: "#/more",
  // the first screen of a device that is not in a mesh yet, with devices nearby; the request to a device nearby; and the same request
  // as it looks to the administrator that is asked (they need a mock of their own: a world without a mesh)
  "nearby-list": { hash: "", world: "onboarding", ready: ".nearby__item", setup: async (m) => { await m.hook("/__mock/reset"); await m.hook("/__mock/nearby?add=macbook-andrey&os=darwin&mesh=Дом"); await m.hook("/__mock/nearby?add=pixel-8&os=android&mesh=Дом"); } },
  "nearby-code": { hash: "", world: "onboarding", ready: "[data-testid=nearby-code]", setup: async (m) => { await m.hook("/__mock/reset"); await m.hook("/__mock/nearby?add=macbook-andrey&os=darwin&mesh=Дом"); await m.hook("/__mock/nearby?hold=1"); },
    after: async (page) => { await page.click("[data-testid=nearby-connect]"); await page.waitForSelector("[data-testid=nearby-join][data-state=waiting]"); await sleep(300); } },
  "nearby-ask": { hash: "#/home", world: "full", ready: "[data-testid=nearby-ask-state][data-confirmed=true]", setup: async (m) => { await m.hook("/__mock/nearby?clear=1"); await m.hook("/__mock/nearby?request=pixel-8&os=android"); } },
};
const wanted = arg("routes", Object.keys(ROUTES).join(",")).split(",");
if (argv.includes("--list")) {
  console.log(Object.keys(ROUTES).join("\n"));
  process.exit(0);
}

const srv = await startMock(["--calm"]);
const worlds = { full: srv, onboarding: null };
if (wanted.some((n) => ROUTES[n] && ROUTES[n].world === "onboarding")) worlds.onboarding = await startMock(["--calm", "--scenario", "onboarding"]);
await sleep(1500);
const browser = await launch();
const checker = await browser.newPage(); // a blank page whose canvas reads the pixels
const problems = [];

/** Pieces of visible text with their boxes and colours. `dissolve`: how far above the tab bar the page dissolves into the sky (the "rosa" look) */
function collectRuns(dissolve) {
  const out = [];
  // what scrolls under the top bar fades out on purpose (and under the tab bar of a phone it is covered): not judged there
  const top = document.querySelector(".topbar");
  const fade = top ? top.getBoundingClientRect().bottom + 30 : 0;
  const tab = document.querySelector(".tabbar");
  const tabTop = tab && getComputedStyle(tab).display !== "none" ? tab.getBoundingClientRect().top : Infinity;
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  while (walker.nextNode()) {
    const node = walker.currentNode;
    const text = node.nodeValue.replace(/\s+/g, " ").trim();
    if (!text) continue;
    if (/\p{Extended_Pictographic}/u.test(text)) continue; // colour emoji ignore the text colour: they cannot be taken out of the picture
    const el = node.parentElement;
    if (!el || ["SCRIPT", "STYLE", "NOSCRIPT"].includes(el.tagName)) continue;
    const cs = getComputedStyle(el);
    if (cs.visibility === "hidden" || cs.display === "none") continue;
    if (el.closest(":disabled, [aria-disabled='true'], .is-disabled, [disabled]")) continue; // inactive controls are exempt from WCAG
    if (el.closest(".home-status__num")) continue; // the big numeral of Home is a picture made of letters: the sentence under it says the same
    const closed = el.closest("details:not([open])");
    if (closed && !el.closest("summary")) continue; // folded away: not on screen
    let op = 1;
    for (let e = el; e; e = e.parentElement) op *= Number(getComputedStyle(e).opacity);
    if (op < 0.05) continue;
    const range = document.createRange();
    range.selectNodeContents(node);
    const rects = [...range.getClientRects()].filter((r) => r.width > 2 && r.height > 4);
    for (const r of rects) {
      let x0 = Math.max(0, r.left), y0 = Math.max(0, r.top), x1 = Math.min(innerWidth, r.right), y1 = Math.min(innerHeight, r.bottom);
      // only what is not cut off by a scrolling or clipping parent is on screen
      for (let p = el.parentElement; p && p !== document.documentElement; p = p.parentElement) {
        const ps = getComputedStyle(p);
        if (ps.overflowX === "visible" && ps.overflowY === "visible") continue;
        const pr = p.getBoundingClientRect();
        x0 = Math.max(x0, pr.left); y0 = Math.max(y0, pr.top); x1 = Math.min(x1, pr.right); y1 = Math.min(y1, pr.bottom);
      }
      if (x1 - x0 < 3 || y1 - y0 < 4) continue;
      if (!el.closest(".topbar") && y0 < fade && top && getComputedStyle(top).position === "sticky" && window.scrollY > 0) continue;
      if (y1 > tabTop - 2 - (dissolve || 0) && !el.closest(".tabbar")) continue; // (covered by the tab bar, or in the band where the page fades into the sky above it)
      // text that is covered by something else (a modal, a toast, the part of a list under the bar) is judged where it shows
      const hit = document.elementFromPoint((x0 + x1) / 2, (y0 + y1) / 2);
      if (!hit || !(hit === el || el.contains(hit) || hit.contains(el))) continue;
      const chain = [];
      for (let e = el, n = 0; e && e !== document.body && n < 3; e = e.parentElement, n++) chain.push(e.tagName.toLowerCase() + (typeof e.className === "string" && e.className.trim() ? "." + e.className.trim().split(/\s+/)[0] : ""));
      out.push({ text: text.slice(0, 48), x: x0, y: y0, w: x1 - x0, h: y1 - y0, color: el.closest("svg") ? cs.fill : cs.color, op, size: parseFloat(cs.fontSize), weight: Number(cs.fontWeight) || 400, tag: chain.join(" < ") });
    }
  }
  return out;
}

/** In the blank page: contrast of each run against the pixels of the text-free screenshot. */
async function judge(pngB64, runs, dpr) {
  return checker.evaluate(async ([b64, runs, dpr, MIN, LARGE_MIN]) => {
    const img = new Image();
    img.src = "data:image/png;base64," + b64;
    await img.decode();
    const c = document.createElement("canvas");
    c.width = img.width;
    c.height = img.height;
    const g = c.getContext("2d", { willReadFrequently: true });
    g.drawImage(img, 0, 0);
    const parse = (s) => {
      let m = /^rgba?\(([^)]+)\)/.exec(s);
      if (m) {
        const p = m[1].split(/[,\s/]+/).filter(Boolean).map(Number);
        return { r: p[0], g: p[1], b: p[2], a: p.length > 3 ? p[3] : 1 };
      }
      m = /^color\(srgb ([^)]+)\)/.exec(s); // what colour-mix() results are serialised as
      if (m) {
        const p = m[1].split(/[\s/]+/).filter(Boolean).map(Number);
        return { r: p[0] * 255, g: p[1] * 255, b: p[2] * 255, a: p.length > 3 ? p[3] : 1 };
      }
      throw new Error("cannot read the colour " + s);
    };
    const lin = (v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); };
    const lum = (r, g2, b) => 0.2126 * lin(r) + 0.7152 * lin(g2) + 0.0722 * lin(b);
    const bad = [];
    let worstOverall = 99;
    for (const run of runs) {
      const fg = parse(run.color);
      const alpha = fg.a * run.op;
      // the box of a line is taller than its letters (the ascender and the descender leave the top and the bottom rows empty): a bright rim of
      // the pill the text stands in must not be taken for what is behind a letter
      const trim = run.h > 10 ? run.h * 0.14 : 0;
      const x = Math.floor(run.x * dpr), y = Math.floor((run.y + trim) * dpr), w = Math.max(1, Math.floor(run.w * dpr)), h = Math.max(1, Math.floor((run.h - 2 * trim) * dpr));
      const d = g.getImageData(x, y, Math.min(w, img.width - x), Math.min(h, img.height - y)).data;
      const ratios = [];
      for (let i = 0; i < d.length; i += 4) {
        const br = d[i], bg = d[i + 1], bb = d[i + 2];
        const r = alpha * fg.r + (1 - alpha) * br, gg = alpha * fg.g + (1 - alpha) * bg, b = alpha * fg.b + (1 - alpha) * bb;
        const l1 = lum(r, gg, b), l2 = lum(br, bg, bb);
        ratios.push((Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05));
      }
      ratios.sort((a, b) => a - b);
      const ratio = ratios[Math.floor(ratios.length * 0.05)] ?? ratios[0];
      const large = run.size >= 24 || (run.size >= 18.66 && run.weight >= 700);
      const need = large ? LARGE_MIN : MIN;
      worstOverall = Math.min(worstOverall, ratio / need);
      if (ratio < need) bad.push({ text: run.text, tag: run.tag, ratio: Math.round(ratio * 100) / 100, need, size: run.size, color: run.color, at: `${Math.round(run.x)},${Math.round(run.y)} ${Math.round(run.w)}x${Math.round(run.h)}` });
    }
    return { bad, count: runs.length };
  }, [pngB64, runs, dpr, MIN, LARGE_MIN]);
}

let total = 0;
let failures = 0;
const summary = [];
for (const theme of skin === "rosa" ? SKIES : ["light", "dark"]) {
  const [themePref, sky] = theme.split("@");
  for (const [w, h] of sizes) {
    const ctx = await browser.newContext({
      viewport: { width: w, height: h }, colorScheme: themePref === "light" ? "light" : "dark", reducedMotion: "reduce",
      ...(skin === "rosa" ? { isMobile: w < 500, hasTouch: w < 500 } : {}),
      userAgent: skin === "glass" ? `Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 TheMeshDesktop/0.1.0 (mac; skin=glass${vibrancy ? "; vibrancy" : ""}; inset)`
        : skin === "rosa" ? "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36 TheMeshAndroid/0.1.0 (android; skin=rosa)" : undefined,
    });
    await ctx.addInitScript((th) => { try { localStorage.setItem("themesh.theme", th); localStorage.setItem("themesh.lang", "ru"); } catch {} }, themePref);
    for (const under of vibrancy ? UNDERLAYS[theme] : [null]) {
      for (const name of wanted) {
        const route = typeof ROUTES[name] === "string" ? { hash: ROUTES[name], world: "full" } : ROUTES[name];
        if (!route) { console.log("unknown route", name); continue; }
        const hash = route.hash;
        const mock = worlds[route.world || "full"];
        if (route.setup) await route.setup(mock);
        // (a page that navigates by itself while it is looked at — a reload after an update, say — is looked at again)
        let res = null;
        for (let attempt = 1; !res; attempt++) {
          const page = await ctx.newPage();
          watch(page, `${theme}-${w}x${h}-${name}`, problems);
          try {
            await page.goto(`${mock.url}/?skin=${skin}${sky ? "&sky=" + sky : ""}${hash}`, { waitUntil: "domcontentloaded" });
            await page.waitForSelector(route.ready || ".shell", { timeout: 10000 }).catch(() => {});
            await sleep(900);
            await page.evaluate(() => document.fonts && document.fonts.ready); // (a face that arrives later moves the text between looking at it and taking the picture)
            if (route.after) await route.after(page);
            if (name === "mail") { await page.click(".mitem__link >> nth=0").catch(() => {}); await sleep(300); }
            if (name === "chat") { await page.click(".thread >> nth=0").catch(() => {}); await sleep(300); }
            if (under) await page.addStyleTag({ content: `html { background: ${under} !important; }` });
            const runs = await page.evaluate(collectRuns, skin === "rosa" ? 44 : 0);
            await page.addStyleTag({ content: "*, *::before, *::after { color: transparent !important; -webkit-text-fill-color: transparent !important; text-shadow: none !important; caret-color: transparent !important; } svg text, svg tspan { fill: transparent !important; } ::placeholder { color: transparent !important; }" });
            await sleep(150);
            const png = await page.screenshot();
            res = await judge(png.toString("base64"), runs, 1);
            if (res.bad.length && arg("dump", "")) {
              fs.mkdirSync(arg("dump", ""), { recursive: true });
              fs.writeFileSync(path.join(arg("dump", ""), `${theme.replace("@", "_")}-${w}x${h}-${name}.png`), png);
            }
          } catch (e) {
            if (attempt >= 3 || !/context was destroyed|navigation/i.test(String(e.message))) throw e;
            console.log(`  (${theme} ${w}x${h} ${name}: the page moved on by itself, looking again)`);
          } finally {
            await page.close();
          }
        }
        total += res.count;
        failures += res.bad.length;
        const label = `${theme} ${w}x${h} ${name}${under ? " over " + under : ""}`;
        if (res.bad.length) {
          console.log(`✗ ${label}: ${res.bad.length} of ${res.count} pieces of text fall short`);
          for (const b of res.bad.slice(0, 8)) console.log(`    ${b.ratio}:1 (needs ${b.need}) «${b.text}» ${b.tag} ${b.size}px ${b.color} at ${b.at}`);
          if (res.bad.length > 8) console.log(`    … and ${res.bad.length - 8} more`);
        } else {
          summary.push(label);
        }
      }
    }
    await ctx.close();
  }
}
await browser.close();
await srv.stop();
if (worlds.onboarding) await worlds.onboarding.stop();
console.log(`\n${mode}: ${total} pieces of text looked at, ${failures} below the WCAG AA limit (${MIN}:1, large text ${LARGE_MIN}:1); ${summary.length} screens clean`);
if (problems.length) console.log("page problems:\n" + problems.join("\n"));
process.exit(failures || problems.length ? 1 : 0);
