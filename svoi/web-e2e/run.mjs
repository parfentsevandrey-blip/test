#!/usr/bin/env node
// End-to-end tests of the web interface against the REAL backend (no mock):
// `svoi demo` brings up four simulated devices (laptop, phone, NAS, home server)
// with the real protocol stack, and a headless Chromium drives the laptop's UI.
// The other devices are poked through their own HTTP APIs to produce incoming
// events (messages, offers) and to check what really arrived.
//
//   make build && node web-e2e/run.mjs [--bin ./svoi] [--only substring] [--shots dir] [--headed] [--list]
//
// Needs Node 22+ and Playwright (`npm i -D playwright` or a global install) with a Chromium.
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { chromium, config, root, startDemo, stopNodes, tests } from "./lib.mjs";

const args = process.argv.slice(2);
const opt = (name, def) => {
  const i = args.indexOf("--" + name);
  return i >= 0 ? (args[i + 1] && !args[i + 1].startsWith("--") ? args[i + 1] : true) : def;
};
const ONLY = String(opt("only", "")).toLowerCase();
if (opt("bin", false)) config.bin = path.resolve(opt("bin"));
if (opt("shots", false)) config.shots = path.resolve(opt("shots"));
config.headed = !!opt("headed", false);
fs.mkdirSync(config.shots, { recursive: true });

for (const f of fs.readdirSync(new URL("./tests/", import.meta.url)).filter((f) => f.endsWith(".mjs")).sort()) {
  await import(new URL("./tests/" + f, import.meta.url));
}
const selected = tests.filter((t) => !ONLY || t.name.toLowerCase().includes(ONLY));
if (opt("list", false)) {
  for (const t of selected) console.log(t.name);
  process.exit(0);
}
if (!fs.existsSync(config.bin)) {
  console.log("building svoi…");
  execFileSync("go", ["build", "-o", config.bin, "./cmd/svoi"], { cwd: root, stdio: "inherit" });
}

const browser = await chromium.launch({ headless: !config.headed, args: ["--no-sandbox", "--no-proxy-server", "--autoplay-policy=no-user-gesture-required"] });
let passed = 0;
const failed = [];
for (const g of [...new Set(selected.map((t) => t.group))]) {
  let demo;
  try {
    demo = g.opts.noDemo ? { devices: {}, stop: async () => {} } : await startDemo({ quiet: g.opts.quiet !== false });
  } catch (e) {
    for (const t of selected.filter((t) => t.group === g)) failed.push([t.name, "could not start the demo: " + e.message]);
    continue;
  }
  console.log(`\n${g.name}`);
  for (const t of selected.filter((t) => t.group === g)) {
    const t0 = Date.now();
    try {
      await t.fn({ demo, dev: demo.devices, browser });
      passed++;
      console.log(`  ✓ ${t.name.replace(g.name + ": ", "")}  (${((Date.now() - t0) / 1000).toFixed(1)}s)`);
    } catch (e) {
      failed.push([t.name, e.stack || String(e)]);
      console.log(`  ✗ ${t.name.replace(g.name + ": ", "")}\n      ${String(e.message).split("\n").join("\n      ")}`);
    }
  }
  await stopNodes();
  await demo.stop();
}
await browser.close();
console.log(`\n${passed} passed, ${failed.length} failed`);
if (failed.length) {
  console.log("\nFailures:");
  for (const [n, e] of failed) console.log(`\n● ${n}\n${e}`);
}
process.exit(failed.length ? 1 : 0);
