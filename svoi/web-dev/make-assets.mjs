#!/usr/bin/env node
// Regenerates binary assets with headless Chromium (Playwright):
//   internal/web/ui/icons/icon-192.png, icon-512.png, maskable-512.png, apple-touch-icon.png
//   web-dev/fixtures/sample.webm   (short VP8 clip the mock server serves for video previews)
//
//   NODE_PATH=/opt/node22/lib/node_modules node web-dev/make-assets.mjs
import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const require = createRequire(import.meta.url);
let chromium;
try { ({ chromium } = require("playwright")); }
catch { ({ chromium } = require("/opt/node22/lib/node_modules/playwright")); }

const here = path.dirname(fileURLToPath(import.meta.url));
const ICONS = path.resolve(here, "../internal/web/ui/icons");
const FIXTURES = path.resolve(here, "fixtures");
const EXE = process.env.CHROMIUM || "/opt/pw-browsers/chromium-1194/chrome-linux/chrome";

const browser = await chromium.launch({ executablePath: EXE, args: ["--no-sandbox"] });

async function renderSvg(svgFile, size, out) {
  const page = await browser.newPage({ viewport: { width: size, height: size }, deviceScaleFactor: 1 });
  const svg = fs.readFileSync(path.join(ICONS, svgFile), "utf8");
  await page.setContent(`<!doctype html><style>html,body{margin:0;background:transparent}svg{display:block;width:${size}px;height:${size}px}</style>${svg}`);
  await page.screenshot({ path: path.join(ICONS, out), omitBackground: true, clip: { x: 0, y: 0, width: size, height: size } });
  await page.close();
  console.log("wrote", out);
}

await renderSvg("icon.svg", 192, "icon-192.png");
await renderSvg("icon.svg", 512, "icon-512.png");
await renderSvg("maskable.svg", 512, "maskable-512.png");
await renderSvg("maskable.svg", 180, "apple-touch-icon.png");

// A 4-second animated clip recorded from a canvas.
fs.mkdirSync(FIXTURES, { recursive: true });
const tmp = fs.mkdtempSync(path.join(FIXTURES, ".rec-"));
const ctx = await browser.newContext({ viewport: { width: 640, height: 360 }, recordVideo: { dir: tmp, size: { width: 640, height: 360 } } });
const page = await ctx.newPage();
await page.setContent(`<!doctype html><style>html,body{margin:0;background:#0f1714;overflow:hidden}</style>
<canvas id="c" width="640" height="360"></canvas>
<script>
const c = document.getElementById("c"), g = c.getContext("2d");
const nodes = [[320,110],[210,260],[430,260]];
const t0 = performance.now();
function frame(now) {
  const t = (now - t0) / 1000;
  const sky = g.createLinearGradient(0, 0, 0, 360);
  sky.addColorStop(0, "hsl(" + (200 + 30 * Math.sin(t / 2)) + " 55% 22%)");
  sky.addColorStop(1, "#0f1714");
  g.fillStyle = sky; g.fillRect(0, 0, 640, 360);
  g.lineWidth = 6; g.strokeStyle = "rgba(79,224,176,.5)"; g.lineJoin = "round";
  g.beginPath(); g.moveTo(...nodes[0]); g.lineTo(...nodes[1]); g.lineTo(...nodes[2]); g.closePath(); g.stroke();
  nodes.forEach(([x, y], i) => {
    const r = 22 + 4 * Math.sin(t * 3 + i * 2);
    g.fillStyle = "#4fe0b0"; g.beginPath(); g.arc(x, y, r, 0, Math.PI * 2); g.fill();
  });
  const k = (t * 0.6) % 1, a = nodes[Math.floor(t * 0.6) % 3], b = nodes[(Math.floor(t * 0.6) + 1) % 3];
  g.fillStyle = "#fff"; g.beginPath(); g.arc(a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, 7, 0, Math.PI * 2); g.fill();
  g.fillStyle = "rgba(255,255,255,.85)"; g.font = "600 22px system-ui, sans-serif"; g.fillText("themesh · " + t.toFixed(1) + " s", 24, 336);
  requestAnimationFrame(frame);
}
requestAnimationFrame(frame);
</script>`);
await page.waitForTimeout(4200);
const video = page.video();
await ctx.close();
const src = await video.path();
fs.copyFileSync(src, path.join(FIXTURES, "sample.webm"));
fs.rmSync(tmp, { recursive: true, force: true });
console.log("wrote fixtures/sample.webm", fs.statSync(path.join(FIXTURES, "sample.webm")).size, "bytes");
await browser.close();
