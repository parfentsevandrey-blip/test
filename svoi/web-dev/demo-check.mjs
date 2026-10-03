#!/usr/bin/env node
// Checks the live demo build (web-dev/make-demo.mjs) in a real browser, the way a
// person would use it: pictures, music, video, PDF, downloads, uploads, live events,
// the guide panel. Fails on any console error.
//
//   node web-dev/make-demo.mjs && NODE_PATH=/opt/node22/lib/node_modules node web-dev/demo-check.mjs
//        [--dir web-dev/demo-dist] [--csp "default-src 'self'; …"] [--shots DIR]
//
// --csp serves every page with that Content-Security-Policy, to see whether the demo
// survives a strict host (data: and blob: URLs for pictures and media, no inline script…).
import fs from "node:fs";
import http from "node:http";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { launch, watch } from "./lib.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const argv = process.argv.slice(2);
const opt = (n, d) => (argv.includes("--" + n) ? argv[argv.indexOf("--" + n) + 1] : d);
const DIR = path.resolve(opt("dir", path.join(here, "demo-dist")));
const CSP = opt("csp", "");
const SHOTS = opt("shots", "");
if (SHOTS) fs.mkdirSync(SHOTS, { recursive: true });

const MIME = { ".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8", ".css": "text/css; charset=utf-8", ".svg": "image/svg+xml", ".png": "image/png" };
// The artifact host wraps the page (a fragment: no <html>/<head>/<body>) in its own skeleton;
// this reproduces what its contract describes so the page is checked the way it will run.
const SKELETON = (page) => `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover"><style>:root{color-scheme:light;padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px)}body{margin:0;font:14px system-ui,sans-serif;background:#faf9f7;color:#1a1a1a}img{max-width:100%}[hidden]{display:none!important}</style></head><body>${page}</body></html>`;
const server = http.createServer((req, res) => {
  const u = new URL(req.url, "http://x");
  let f = path.join(DIR, decodeURIComponent(u.pathname));
  if (u.pathname.endsWith("/")) f = path.join(f, "index.html");
  if (!f.startsWith(DIR) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { res.writeHead(404); res.end("not found"); return; }
  const h = { "Content-Type": MIME[path.extname(f)] || "application/octet-stream" };
  if (CSP) h["Content-Security-Policy"] = CSP;
  res.writeHead(200, h);
  if (path.basename(f) === "index.html" && !argv.includes("--no-wrap")) { res.end(SKELETON(fs.readFileSync(f, "utf8"))); return; }
  fs.createReadStream(f).pipe(res);
});
await new Promise((r) => server.listen(0, "127.0.0.1", r));
const BASE = `http://127.0.0.1:${server.address().port}`;

const problems = [];
const results = [];
async function step(name, fn) {
  const t0 = Date.now();
  try { await fn(); results.push(`ok   ${name} (${Date.now() - t0} ms)`); }
  catch (e) { const m = e.message.split("\n").filter(Boolean).slice(0, 3).join(" | "); results.push(`FAIL ${name}: ${m}`); problems.push(`${name}: ${m}`); }
}

const browser = await launch();
const ctx = await browser.newContext({ viewport: { width: 1360, height: 860 }, locale: "ru-RU", serviceWorkers: "block", acceptDownloads: true });
await ctx.addInitScript(() => { try { localStorage.setItem("themesh.lang", "ru"); } catch { /* ignore */ } });
const page = await ctx.newPage();
watch(page, "demo", problems);
// Under a strict CSP the browser reports violations as console errors: they are failures here.
page.on("console", (m) => { if (CSP && /Content Security Policy/i.test(m.text())) problems.push("[csp] " + m.text()); });
const shot = async (name) => { if (SHOTS) await page.screenshot({ path: path.join(SHOTS, name + ".png") }); };
const hook = (p) => page.evaluate((u) => fetch(u, { method: "POST", headers: { "X-Themesh": "1" } }).then((r) => r.json().catch(() => null)), p);
const go = async (hash) => { await page.evaluate((h) => { location.hash = h; }, hash); await page.waitForTimeout(150); };

await step("the page starts on the Home screen, in plain words", async () => {
  await page.goto(BASE + "/?calm=1&latency=0");
  await page.waitForSelector("[data-testid=page-home]", { timeout: 15000 });
  await page.waitForSelector("[data-testid=home-status]");
  const text = await page.innerText("[data-testid=page-home]");
  if (!/Всё в порядке/.test(text)) throw new Error("no status sentence: " + text.slice(0, 120));
  for (const word of ["NAT", "ретранслятор", "IPv4"]) if (text.includes(word)) throw new Error(`the Home screen says "${word}"`);
  const n = await page.locator("[data-testid=home-device]").count();
  if (n < 5) throw new Error("only " + n + " devices on Home");
  for (const a of ["send", "chat", "files", "add"]) await page.locator(`[data-testid=home-action-${a}]`).waitFor();
  await shot("home");
});

await step("the devices page still shows the network, with the details folded away", async () => {
  await go("#/devices");
  await page.waitForFunction(() => /home-server/.test(document.body.innerText) && /nas/.test(document.body.innerText), null, { timeout: 10000 });
  if (await page.locator("[data-testid=tech-details]").count() < 1) throw new Error("no folded technical details");
  await shot("devices");
});

await step("the demo guide opens and says what this is", async () => {
  await page.locator("#themesh-demo-guide").getByRole("button", { name: /Что здесь можно сделать/ }).click();
  await page.locator("#themesh-demo-guide").getByText("Это макет, а не сама программа The Mesh").waitFor();
  await shot("guide");
  await page.locator("#themesh-demo-guide").getByRole("button", { name: /Скрыть подсказки/ }).click();
});

await step("photos of the NAS: thumbnails really load (data: URLs), the viewer pages through them", async () => {
  await go("#/home");
  await page.click("[data-testid=home-action-files]");
  const nas = await page.evaluate(() => fetch("api/state").then((r) => r.json()).then((s) => s.peers.find((p) => p.deviceName === "nas").id));
  await go(`#/files/browse/${nas}`);
  await page.click("[data-testid=share-card][data-id=sh_photo]");
  await page.click("[data-testid=file-row][data-name='2025'] a");
  await page.click("[data-testid=file-row][data-name='Байкал'] a");
  await page.click("[data-testid=view-grid]");
  await page.waitForFunction(() => { const i = [...document.querySelectorAll("[data-testid=file-tile] img")]; return i.length >= 3 && i.every((x) => x.complete && x.naturalWidth > 0); }, null, { timeout: 15000 });
  const kinds = await page.evaluate(() => [...document.querySelectorAll("[data-testid=file-tile] img")].map((i) => i.src.slice(0, 5)));
  if (!kinds.every((k) => k === "data:")) throw new Error("thumbnails are not data: URLs: " + kinds.join(","));
  await shot("photos");
  await page.click("[data-testid=file-tile] >> nth=0 >> .ftile__open");
  await page.waitForSelector("[data-testid=preview] .pv__img.is-loaded", { timeout: 10000 });
  const first = await page.textContent("[data-testid=preview-name]");
  await page.keyboard.press("ArrowRight");
  await page.waitForFunction((f) => document.querySelector("[data-testid=preview-name]").textContent !== f, first);
  await page.keyboard.press("Escape");
  await page.click("[data-testid=view-list]"); // the choice is remembered: the next steps read rows
});

await step("documents: text, PDF and video open in place", async () => {
  const nas = await page.evaluate(() => fetch("api/state").then((r) => r.json()).then((s) => s.peers.find((p) => p.deviceName === "nas").id));
  await go(`#/files/browse/${nas}/sh_docs`);
  await page.click("[data-testid=file-row][data-name='Заметки.txt'] button");
  await page.waitForSelector("[data-testid=preview] .pv__text pre:has-text('Wi-Fi')");
  await page.keyboard.press("Escape");
  await page.click("[data-testid=file-row][data-name='Договор аренды гаража.pdf'] button");
  await page.waitForSelector("[data-testid=preview] iframe.pv__pdf");
  const pdf = await page.getAttribute("[data-testid=preview] iframe.pv__pdf", "src");
  if (!/^blob:/.test(pdf || "")) await page.waitForFunction(() => /^blob:/.test(document.querySelector("[data-testid=preview] iframe.pv__pdf").src), null, { timeout: 5000 });
  await page.keyboard.press("Escape");
  await go(`#/files/browse/${nas}/sh_video`);
  await page.click("[data-testid=file-row][data-name='Отпуск 2024.webm'] button");
  await page.waitForSelector("[data-testid=preview] video");
  await page.waitForFunction(() => document.querySelector("[data-testid=preview] video").readyState >= 1, null, { timeout: 10000 });
  await page.keyboard.press("Escape");
});

await step("a download button explains that the demo does not save files (the host blocks downloads)", async () => {
  const nas = await page.evaluate(() => fetch("api/state").then((r) => r.json()).then((s) => s.peers.find((p) => p.deviceName === "nas").id));
  await go(`#/files/browse/${nas}/sh_docs`);
  let downloaded = false;
  page.once("download", () => { downloaded = true; });
  await page.click("[data-testid=file-row][data-name='test-page.html'] a.frow__dl");
  await page.locator("#themesh-demo-guide").getByText("В демо файлы не скачиваются").waitFor({ timeout: 5000 });
  if (downloaded) throw new Error("a download started although the host blocks them");
});

await step("a file can be sent to another device (upload with progress)", async () => {
  await go("#/files/send");
  const nas = await page.evaluate(() => fetch("api/state").then((r) => r.json()).then((s) => s.peers.find((p) => p.deviceName === "nas").id));
  await page.click(`[data-testid=device-chip][data-id="${nas}"]`);
  await page.setInputFiles("[data-testid=dropzone-input]", { name: "demo-check.txt", mimeType: "text/plain", buffer: Buffer.from("hello from the demo check\n".repeat(2000)) });
  await page.waitForSelector("[data-testid=staged-file]");
  await page.click("[data-testid=send-submit]");
  await page.waitForSelector("[data-testid=transfer][data-dir=out]:has-text('demo-check.txt')", { timeout: 10000 });
  await page.waitForSelector("[data-testid=transfer][data-state=done]:has-text('demo-check.txt')", { timeout: 30000 });
});

await step("live events: a file offer, a chat message and a letter arrive by themselves", async () => {
  await go("#/home");
  await page.waitForSelector("[data-testid=home-attention]");
  const before = await page.locator("[data-testid=home-offer]").count();
  await hook("/__mock/offer?from=phone&name=" + encodeURIComponent("Фото с дачи.jpg"));
  await page.waitForFunction((n) => document.querySelectorAll("[data-testid=home-offer]").length > n, before, { timeout: 10000 });
  await page.waitForSelector("[data-testid=home-offer]:has-text('Фото с дачи.jpg') [data-testid=offer-accept]");
  await shot("offer");
  await page.click("[data-testid=home-offer]:has-text('Фото с дачи.jpg') [data-testid=offer-accept]");
  await hook("/__mock/chat?from=dad-pc&text=" + encodeURIComponent("Привет!"));
  await page.waitForSelector("[data-testid=toast]", { timeout: 10000 });
  await hook("/__mock/mail?from=nas");
  await page.waitForFunction(() => /Почта/.test(document.body.innerText), null, { timeout: 5000 });
});

await step("mail and chat work", async () => {
  await go("#/mail");
  await page.waitForSelector("[data-testid=mail-item]", { timeout: 10000 });
  await shot("mail");
  await page.click("[data-testid=mail-item] >> nth=0");
  await page.waitForSelector("[data-testid=mail-reader]", { timeout: 10000 });
  await go("#/chat");
  await page.waitForSelector("[data-testid=thread]", { timeout: 10000 });
  await page.click("[data-testid=thread] >> nth=0");
  await page.fill("[data-testid=chat-input]", "Проверка связи");
  await page.keyboard.press("Enter");
  await page.waitForSelector("[data-testid=bubble]:has-text('Проверка связи')", { timeout: 10000 });
  await shot("chat");
});

await step("settings and the theme", async () => {
  await go("#/settings/network");
  await page.waitForSelector("[data-testid=setting-relay]", { timeout: 10000 });
  await shot("settings");
  await page.click("[data-testid=theme-light]");
  await page.waitForFunction(() => document.documentElement.dataset.theme === "light");
  await shot("light");
  await page.click("[data-testid=theme-dark]");
});

await step("the first run can be shown and left again from the guide (no query string needed)", async () => {
  const guide = () => page.locator("#themesh-demo-guide");
  await guide().getByRole("button", { name: /Что здесь можно сделать/ }).click();
  await guide().getByRole("button", { name: /Показать первый запуск/ }).click();
  await page.waitForFunction(() => /Создать свою сеть/.test(document.body.innerText), null, { timeout: 15000 });
  await shot("first-run");
  await guide().getByRole("button", { name: /Что здесь можно сделать/ }).click();
  await guide().getByRole("button", { name: /Вернуть готовую сеть/ }).click();
  await page.waitForSelector("[data-testid=page-home]", { timeout: 15000 });
});

await browser.close();
server.close();
console.log(results.join("\n"));
if (problems.length) { console.log("\nproblems:\n" + [...new Set(problems)].map((p) => " - " + p).join("\n")); process.exit(1); }
console.log(`\n${results.length}/${results.length} steps passed, 0 console problem(s)${CSP ? " (under a strict CSP)" : ""}`);
