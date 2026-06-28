#!/usr/bin/env node
/* Verifies the live re-forge: type a word -> particles re-rasterize + reassemble. */
const path = require("path");
const { chromium } = require("/opt/node22/lib/node_modules/playwright");
const URL = process.argv[2] || "http://127.0.0.1:8123/index.html";
const WORD = process.argv[3] || "HELLO";

(async () => {
  const b = await chromium.launch({
    executablePath: "/opt/pw-browsers/chromium-1194/chrome-linux/chrome",
    args: ["--use-gl=angle", "--use-angle=swiftshader", "--ignore-gpu-blocklist", "--no-sandbox"],
  });
  const p = await b.newPage({ viewport: { width: 1440, height: 900 } });
  const errs = [];
  p.on("pageerror", (e) => errs.push(e.message));
  p.on("console", (m) => { if (m.type() === "error") errs.push(m.text()); });
  await p.goto(URL, { waitUntil: "load", timeout: 25000 });
  await p.waitForTimeout(2500);
  await p.evaluate(() => { window.__pinProgress = 0.9; });
  await p.waitForTimeout(1200);
  const ok = await p.evaluate(() => !!(window.PF && window.PF.scene && window.PF.scene.submitWord));
  await p.evaluate((w) => window.PF.scene.submitWord(w), WORD);
  await p.waitForTimeout(2600);
  await p.screenshot({ path: path.join("tools/shots", "reforge_" + WORD + ".png") });
  const word = await p.evaluate(() => window.PF.scene.formedWord);
  console.log("submitWord:", ok, "| formedWord:", word, "| errors:", errs.length ? errs.join(" | ") : "none");
  await b.close();
  process.exit(0);
})().catch((e) => { console.error("ERR", e.message); process.exit(1); });
