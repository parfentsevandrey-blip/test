#!/usr/bin/env node
/* Reusable headless render-test for the WebGL site.
   Loads a URL in headless Chromium (SwiftShader WebGL2), captures all
   console output + page/WebGL errors, then screenshots the scene at a few
   scroll positions so the cinematic choreography can be eyeballed.

   Usage: node tools/rendertest.js <url> <outDir> [scrollStops=0,0.33,0.66,1]
*/
const path = require("path");
const { chromium } = require("/opt/node22/lib/node_modules/playwright");

const url = process.argv[2] || "http://localhost:8123/index.html";
const outDir = process.argv[3] || path.join(process.cwd(), "tools/shots");
const stops = (process.argv[4] || "0,0.33,0.66,1").split(",").map(Number);

(async () => {
  require("fs").mkdirSync(outDir, { recursive: true });
  const browser = await chromium.launch({
    executablePath: "/opt/pw-browsers/chromium-1194/chrome-linux/chrome",
    args: ["--use-gl=angle","--use-angle=swiftshader","--ignore-gpu-blocklist","--enable-webgl","--no-sandbox"],
  });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1 });
  const logs = [];
  page.on("console", (m) => logs.push(`[${m.type()}] ${m.text()}`));
  page.on("pageerror", (e) => logs.push(`[pageerror] ${e.message}`));
  page.on("requestfailed", (r) => logs.push(`[reqfail] ${r.url()} ${r.failure()?.errorText}`));

  await page.goto(url, { waitUntil: "load", timeout: 30000 }).catch((e) => logs.push(`[goto] ${e.message}`));
  // let the scene warm up / preloader clear
  await page.waitForTimeout(3500);

  const docH = await page.evaluate(() => Math.max(document.body.scrollHeight, document.documentElement.scrollHeight));
  const vh = 900;
  for (let i = 0; i < stops.length; i++) {
    const t = Math.max(0, Math.min(1, stops[i]));
    await page.evaluate((y) => window.scrollTo({ top: y, behavior: "instant" }), Math.round(t * (docH - vh)));
    await page.waitForTimeout(1400);
    await page.screenshot({ path: path.join(outDir, `shot_${String(Math.round(t * 100)).padStart(3, "0")}.png`) });
  }

  // sample a perf metric if the page exposes one
  const diag = await page.evaluate(() => ({
    fps: window.__fps || null,
    particles: window.__particles || null,
    webglLost: window.__webglLost || false,
    title: document.title,
  }));

  await browser.close();
  const errors = logs.filter((l) => /error|pageerror|reqfail|fail|undefined is not|cannot read|shader|glsl|compile/i.test(l));
  console.log("=== DIAG ===", JSON.stringify(diag));
  console.log("=== CONSOLE (" + logs.length + " lines) ===");
  console.log(logs.join("\n") || "(none)");
  console.log("=== SUSPECT ERRORS (" + errors.length + ") ===");
  console.log(errors.join("\n") || "(none)");
  console.log("=== SHOTS in " + outDir + " ===");
})().catch((e) => { console.error("HARNESS_ERR", e.stack || e.message); process.exit(1); });
