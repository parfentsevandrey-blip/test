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
  void docH; void vh;
  for (let i = 0; i < stops.length; i++) {
    const t = Math.max(0, Math.min(1, stops[i]));
    // pin progress exactly so the cinematic state is deterministic under software GL
    await page.evaluate((p) => { window.__pinProgress = p; }, t);
    await page.waitForTimeout(1800); // let camera/morph lerp settle to the pinned target
    const prog = await page.evaluate(() => (window.PF && window.PF.progress) || 0);
    // numeric brightness probe (no image needed): mean luminance + blown-white fraction
    const stat = await page.evaluate(() => {
      try {
        const c = document.getElementById("stage");
        const o = document.createElement("canvas"); o.width = 192; o.height = 120;
        const x = o.getContext("2d"); x.drawImage(c, 0, 0, 192, 120);
        const d = x.getImageData(0, 0, 192, 120).data; let sum = 0, white = 0, lit = 0, n = 192 * 120;
        for (let i = 0; i < d.length; i += 4) { const l = (d[i] + d[i + 1] + d[i + 2]) / 3; sum += l; if (l > 232) white++; if (l > 28) lit++; }
        return { meanLum: +(sum / n / 255).toFixed(3), whiteFrac: +(white / n).toFixed(3), litFrac: +(lit / n).toFixed(3) };
      } catch (e) { return { err: String(e).slice(0, 60) }; }
    });
    await page.screenshot({ path: path.join(outDir, `shot_${String(Math.round(t * 100)).padStart(3, "0")}.jpg`), type: "jpeg", quality: 40 });
    logs.push(`[stop] t=${t} prog=${prog.toFixed(2)} meanLum=${stat.meanLum} whiteFrac=${stat.whiteFrac} litFrac=${stat.litFrac}${stat.err ? " err=" + stat.err : ""}`);
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
