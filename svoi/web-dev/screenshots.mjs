#!/usr/bin/env node
// Walks every screen/state of the UI in dark and light themes at desktop
// (1440×900) and phone (390×844) sizes, saving PNGs to web-dev/screens/.
//
//   NODE_PATH=/opt/node22/lib/node_modules node web-dev/screenshots.mjs [--only devices,mail] [--lang en] [--out dir]
//
// Starts its own mock servers (calm mode, random ports). Exits non-zero if any
// page logged a console error or threw.
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { launch, sleep, startMock, watch } from "./lib.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const argv = process.argv.slice(2);
const OUT = argv.includes("--out") ? path.resolve(argv[argv.indexOf("--out") + 1]) : path.join(here, "screens");
const only = argv.includes("--only") ? argv[argv.indexOf("--only") + 1].split(",") : null;
const lang = argv.includes("--lang") ? argv[argv.indexOf("--lang") + 1] : "ru";
fs.mkdirSync(OUT, { recursive: true });

const VIEWPORTS = { desktop: { width: 1440, height: 900 }, mobile: { width: 390, height: 844 } };
const THEMES = ["dark", "light"];
const enc = (...parts) => "#/" + parts.map((p) => encodeURIComponent(p)).join("/");
/** Wait until the page's live link (SSE) is up, so events triggered by hooks reach it. */
const live = (p) => p.waitForFunction(() => window.__svoi && window.__svoi.state.conn === "online", null, { timeout: 8000 });

const servers = {
  full: await startMock(["--calm"]),
  scratch: await startMock(["--calm"]),
  onboarding: await startMock(["--calm", "--scenario", "onboarding"]),
  empty: await startMock(["--calm", "--scenario", "empty"]),
  auth: await startMock(["--calm", "--auth"]),
};
const st = await servers.full.get("/api/state");
const id = (name) => (st.peers.find((p) => p.deviceName === name || p.name === name) || {}).id;
const NAS = id("nas"), PHONE = id("phone"), TABLET = id("old-tablet"), HOME = id("home-server");
await sleep(2500); // let the mock pre-generate its fake photos

/** Each shot: name, server, hash, optional `run(page)`, `only` (desktop|mobile), `full` (full page). */
const SHOTS = [
  { name: "onboarding", server: "onboarding", hash: "", wait: ".onb-choice" },
  { name: "onboarding-create", server: "onboarding", hash: "", run: async (p) => { await p.click(".onb-choice >> nth=0"); await p.waitForSelector(".onb-form"); } },
  { name: "onboarding-join-error", server: "onboarding", hash: "", run: async (p) => {
    await p.click(".onb-choice >> nth=1");
    await p.fill(".onb-code", "SVOI1-EXPIRED-AAAAAAAA-BBBBBBBB-CCCCCCCC-DDDDDDDD-EEEEEEEE");
    await p.click(".onb-form button[type=submit]");
    await p.waitForSelector(".callout--err", { timeout: 9000 });
  } },
  { name: "onboarding-joining", server: "onboarding", hash: "", run: async (p, srv) => {
    await p.click(".onb-choice >> nth=1");
    await p.fill(".onb-code", "SVOI1-TIMEOUT-AAAAAAAA-BBBBBBBB-CCCCCCCC-DDDDDDDD-EEEEEEEE");
    await p.click(".onb-form button[type=submit]");
    await p.waitForSelector(".onb-progress");
    await sleep(1200);
  } },
  { name: "onboarding-removed", server: "scratch", hash: "#/devices", reset: true, run: async (p, srv) => {
    await p.waitForSelector(".topo__svg");
    await live(p);
    await srv.hook("/__mock/removed");
    await p.waitForSelector("[data-testid=removed-notice]", { timeout: 8000 });
    await p.click("[data-testid=onb-join]");
    await p.waitForSelector("[data-testid=dns-preview]");
    await sleep(300);
  } },
  { name: "unauthorized", server: "auth", hash: "", wait: ".fullscreen__title" },
  { name: "unauthorized-link", server: "auth", hash: "", only: "mobile", run: async (p, srv) => {
    const { url } = await srv.hook("/__mock/login");
    await fetch(srv.url + url, { redirect: "manual" }); // used up elsewhere first (no shared cookies)
    await p.goto(srv.url + url);
    await p.waitForSelector("[data-testid=unauthorized][data-reason=link]");
  } },
  { name: "signed-out", server: "scratch", hash: "#/settings", reset: true, only: "desktop", run: async (p) => {
    await p.click("[data-testid=logout]");
    await p.waitForSelector("[data-testid=confirm-ok]");
    await p.click("[data-testid=confirm-ok]");
    await p.waitForSelector("[data-testid=unauthorized][data-reason=signed-out]");
  } },
  { name: "devices", server: "full", hash: "#/devices", wait: ".topo__svg", full: true },
  { name: "devices-empty", server: "empty", hash: "#/devices", wait: ".topo__svg" },
  { name: "device-drawer-nas", server: "full", hash: enc("devices", NAS), wait: ".drawer" },
  { name: "device-drawer-phone", server: "full", hash: enc("devices", PHONE), wait: ".drawer", only: "desktop" },
  { name: "device-drawer-offline", server: "full", hash: enc("devices", TABLET), wait: ".drawer", only: "mobile" },
  { name: "device-drawer-admin", server: "full", hash: enc("devices", HOME), only: "desktop", run: async (p) => {
    await p.waitForSelector("[data-testid=manage-admin-note]");
    await p.evaluate(() => document.querySelector("[data-testid=manage-admin-note]").scrollIntoView({ block: "center" }));
    await sleep(200);
  } },
  { name: "rename-prompt", server: "scratch", hash: enc("devices", NAS), run: async (p) => {
    await p.waitForSelector("[data-testid=manage-rename]");
    await p.click("[data-testid=manage-rename]");
    await p.fill("[data-testid=prompt-input]", "Кухонный ноутбук");
    await p.waitForSelector("[data-testid=dns-preview][data-label=kukhonnyy-noutbuk]");
  } },
  { name: "confirm-revoke-admin", server: "scratch", hash: enc("devices", HOME), run: async (p) => {
    await p.waitForSelector("[data-testid=manage-revoke]");
    await p.click("[data-testid=manage-revoke]");
    await p.waitForSelector("[data-testid=revoke-admin-warning]");
  } },
  { name: "add-device", server: "scratch", hash: "#/devices", run: async (p) => { await p.click(".page-head__actions .btn--primary"); await p.waitForSelector(".radio-card"); } },
  { name: "add-device-qr", server: "scratch", hash: "#/devices", run: async (p) => {
    await p.click(".page-head__actions .btn--primary");
    await p.click(".modal__foot .btn--primary");
    await p.waitForSelector(".qr img");
    await sleep(300);
  } },
  { name: "add-device-done", server: "scratch", hash: "#/devices", reset: true, run: async (p, srv) => {
    await p.click(".page-head__actions .btn--primary");
    await p.click(".modal__foot .btn--primary");
    await p.waitForSelector(".qr img");
    await live(p);
    await srv.hook("/__mock/join?name=tablet");
    await p.waitForSelector(".add-done", { timeout: 8000 });
  } },
  { name: "confirm-revoke", server: "scratch", hash: enc("devices", NAS), only: "desktop", run: async (p) => {
    await p.waitForSelector(".drawer");
    await p.click(".ddr-link.is-danger");
    await p.waitForSelector(".modal--danger, .modal .modal__icon--danger");
    await p.fill(".modal input.input", "nas");
  } },
  { name: "files-send", server: "full", hash: "#/files/send", wait: ".trlist", full: true },
  { name: "files-send-staged", server: "full", hash: enc("files", "send") + "?to=" + NAS, run: async (p) => {
    await p.setInputFiles(".dropzone input[type=file]", [
      { name: "Отпуск — план.pdf", mimeType: "application/pdf", buffer: Buffer.alloc(184_000, 1) },
      { name: "IMG_0412.jpg", mimeType: "image/jpeg", buffer: Buffer.alloc(2_400_000, 2) },
    ]);
    await p.waitForSelector(".staged__item");
  } },
  { name: "browse-devices", server: "full", hash: "#/files/browse", wait: ".bdev" },
  { name: "browse-shares", server: "full", hash: enc("files", "browse", NAS), wait: ".scard" },
  { name: "browse-list", server: "full", hash: enc("files", "browse", NAS, "sh_docs"), wait: ".frow:not(.frow--head):not(.frow--skel)" },
  { name: "browse-grid", server: "full", hash: enc("files", "browse", NAS, "sh_photo", "2024", "Лето на даче"), run: async (p) => {
    await p.waitForSelector(".frow:not(.frow--head):not(.frow--skel)");
    await p.click(".ftool [role=radio] >> nth=1");
    await p.waitForSelector(".ftile img");
    await sleep(800);
  }, after: async (p) => { await p.evaluate(() => localStorage.setItem("svoi.files.view", JSON.stringify("list"))); } },
  { name: "preview-image", server: "full", hash: enc("files", "browse", NAS, "sh_photo", "2025", "Байкал"), run: async (p) => {
    await p.waitForSelector(".frow__link >> nth=0");
    await p.click("button.frow__link >> nth=0");
    await p.waitForSelector(".pv__img.is-loaded", { timeout: 8000 });
  } },
  { name: "preview-video", server: "full", hash: enc("files", "browse", NAS, "sh_video"), run: async (p) => {
    await p.waitForSelector("button.frow__link");
    await p.click("button.frow__link:has-text('Отпуск 2024')");
    await p.waitForSelector(".pv__video");
    await sleep(1200);
  } },
  { name: "preview-pdf", server: "full", hash: enc("files", "browse", NAS, "sh_docs", "Квитанции"), only: "desktop", run: async (p) => {
    await p.waitForSelector("button.frow__link");
    await p.click("button.frow__link >> nth=0");
    await p.waitForSelector(".pv__pdf");
    await sleep(1500);
  } },
  { name: "preview-text", server: "full", hash: enc("files", "browse", NAS, "sh_docs", "Рецепты"), run: async (p) => {
    await p.waitForSelector("button.frow__link");
    await p.click("button.frow__link >> nth=0");
    await p.waitForSelector(".pv__text pre");
  } },
  { name: "preview-audio", server: "full", hash: enc("files", "browse", NAS, "sh_video", "Музыка"), only: "mobile", run: async (p) => {
    await p.waitForSelector("button.frow__link");
    await p.click("button.frow__link >> nth=0");
    await p.waitForSelector(".pv__audio audio");
  } },
  { name: "browse-offline", server: "full", hash: enc("files", "browse", TABLET), wait: ".empty" },
  { name: "my-folders", server: "full", hash: "#/files/shares", wait: ".share-row" },
  { name: "share-error", server: "full", hash: "#/files/shares", run: async (p) => {
    await p.waitForSelector(".share-row");
    await p.click("[data-testid=share-add]");
    await p.fill("[data-testid=share-path]", "/home/andrey");
    await p.fill("[data-testid=share-name]", "Дом");
    await p.click("[data-testid=share-save]");
    await p.waitForSelector("[data-testid=share-error]");
  } },
  { name: "share-dialog-picker", server: "full", hash: "#/files/shares", run: async (p) => {
    await p.waitForSelector(".share-row");
    await p.click(".shares__bar .btn--primary");
    await p.waitForSelector(".modal .input-group .btn");
    await p.click(".modal .input-group .btn");
    await p.waitForSelector(".picker__item:not(:has(.skeleton))");
    await sleep(300);
  } },
  { name: "mail", server: "full", hash: "#/mail/inbox", wait: ".mitem__link", run: async (p, srv, vp) => {
    await p.waitForSelector(".mitem__link");
    if (vp === "desktop") { await p.click(".mitem__link >> nth=1"); await p.waitForSelector(".reader__body"); }
  } },
  { name: "mail-open", server: "full", hash: "#/mail/inbox", only: "mobile", run: async (p) => {
    await p.click(".mitem__link >> nth=1");
    await p.waitForSelector(".reader__body");
  } },
  { name: "mail-attachments", server: "scratch", hash: "#/mail/inbox", reset: true, run: async (p) => {
    await p.click("[data-testid=mail-item]:has-text('Фото с юбилея') a");
    await p.waitForSelector("[data-testid=attachment-fetch]");
    await p.click("[data-testid=attachment-fetch]"); // → fetching at once
    await p.waitForSelector("[data-testid=mail-attachment][data-state=fetching]:has-text('Видео')");
    await p.evaluate(() => document.querySelector(".reader__atts").scrollIntoView({ block: "center" }));
    await sleep(300);
  } },
  { name: "mail-attachment-failed", server: "full", hash: "#/mail/inbox", only: "desktop", run: async (p) => {
    await p.click("[data-testid=mail-item]:has-text('Скриншот ошибки') a");
    await p.waitForSelector("[data-testid=attachment-retry]");
  } },
  { name: "mail-sent", server: "full", hash: "#/mail/sent", run: async (p) => {
    await p.click(".mitem__link >> nth=1");
    await p.waitForSelector(".reader__delivery");
  } },
  { name: "mail-compose", server: "full", hash: "#/mail/compose?to=" + NAS, run: async (p) => {
    await p.waitForSelector(".compose__form");
    await p.fill("#c-subj", "Фото с Байкала");
    await p.fill(".compose__body", "Привет! Закинул фотографии на NAS, посмотри, когда будет время.\n\nhttps://example.org/baikal");
    await p.setInputFiles(".compose__form input[type=file]", [{ name: "Маршрут.pdf", mimeType: "application/pdf", buffer: Buffer.alloc(96_000, 3) }]);
    await sleep(600);
  } },
  { name: "mail-reply", server: "full", hash: "#/mail/inbox", only: "desktop", run: async (p) => {
    await p.click(".mitem__link >> nth=1");
    await p.waitForSelector(".reader__tools .btn");
    await p.click(".reader__tools .btn >> nth=0");
    await p.waitForSelector(".compose__body");
    await sleep(500);
  } },
  { name: "chat", server: "full", hash: "#/chat", wait: ".thread", run: async (p, srv, vp) => {
    await p.waitForSelector(".thread");
    if (vp === "desktop") { await p.click(".thread >> nth=0"); await p.waitForSelector(".bubble"); await sleep(600); }
  } },
  { name: "chat-thread", server: "full", hash: "#/chat", only: "mobile", run: async (p) => {
    await p.click(".thread >> nth=0");
    await p.waitForSelector(".bubble");
    await sleep(600);
  } },
  { name: "chat-consent", server: "full", hash: "#/chat", run: async (p) => {
    await p.click(".thread:has-text('Компьютер папы')");
    await p.waitForSelector("[data-testid=chat-attachment] [data-testid=attachment-fetch]");
    await sleep(500);
  } },
  { name: "chat-offline-peer", server: "full", hash: "#/chat", run: async (p) => {
    await p.click(".thread:has-text('mom-laptop')");
    await p.waitForSelector(".conv__offline");
    await sleep(300);
  } },
  { name: "services", server: "full", hash: "#/services", wait: ".svc", full: true },
  { name: "service-publish", server: "full", hash: "#/services", run: async (p) => {
    await p.waitForSelector(".pub__bar .btn");
    await p.click(".pub__bar .btn--secondary");
    await p.waitForSelector(".presets");
  } },
  { name: "settings", server: "full", hash: "#/settings", wait: ".set-sec", full: true },
  { name: "settings-network", server: "full", hash: "#/settings/network", wait: ".nat", only: "mobile" },
  { name: "settings-remote", server: "full", hash: "#/settings?d=" + HOME, wait: ".nat", only: "desktop" },
  { name: "settings-logs", server: "full", hash: "#/settings/about", run: async (p) => {
    await p.waitForSelector(".logs-card .btn");
    await p.click(".logs-card .btn");
    await p.waitForSelector(".logline");
    await p.evaluate(() => document.querySelector(".logs-card").scrollIntoView({ block: "center" }));
    await sleep(300);
  } },
  { name: "settings-tun-running", server: "scratch", hash: "#/settings/tun", reset: true, run: async (p) => {
    await p.waitForSelector("[data-testid=tun-enabled]");
    await p.click("[data-testid=tun-enabled]");
    await p.waitForSelector("[data-testid=tun-state][data-state=running]");
    await p.evaluate(() => document.querySelector("[data-testid=tun-section]").scrollIntoView({ block: "center" }));
    await sleep(300);
  } },
  { name: "settings-tun-error", server: "scratch", hash: "#/settings/tun", reset: true, run: async (p, srv) => {
    await srv.hook("/__mock/tun?error=1");
    await p.waitForSelector("[data-testid=tun-enabled]");
    await p.click("[data-testid=tun-enabled]");
    await p.waitForSelector("[data-testid=tun-state][data-state=error]");
    await p.evaluate(() => document.querySelector("[data-testid=tun-section]").scrollIntoView({ block: "center" }));
    await sleep(300);
  }, after: async (p) => { await servers.scratch.hook("/__mock/tun?error=0"); } },
  { name: "leave-confirm", server: "full", hash: "#/settings", only: "desktop", run: async (p) => {
    await p.click("[data-testid=leave-mesh]");
    await p.waitForSelector(".modal .confirm-text");
  } },
  { name: "more", server: "full", hash: "#/more", wait: ".more-row", only: "mobile" },
  { name: "offline-banner", server: "scratch", hash: "#/devices", reset: true, run: async (p, srv) => {
    await p.waitForSelector(".topo__svg");
    await live(p);
    await srv.hook("/__mock/drop?for=60");
    await p.waitForSelector(".gbanner--offline", { timeout: 8000 });
  } },
  { name: "incoming-offer-toast", server: "scratch", hash: "#/devices", reset: true, run: async (p, srv) => {
    await p.waitForSelector(".topo__svg");
    await live(p);
    await srv.hook("/__mock/offer?from=phone");
    await p.waitForSelector(".toast");
    await sleep(400);
  } },
];

const browser = await launch();
const problems = [];
let count = 0;
const t0 = Date.now();
for (const [vp, size] of Object.entries(VIEWPORTS)) {
  for (const theme of THEMES) {
    const ctx = await browser.newContext({
      viewport: size, deviceScaleFactor: vp === "mobile" ? 2 : 1, colorScheme: theme, reducedMotion: "reduce",
      locale: lang === "en" ? "en-GB" : "ru-RU", hasTouch: vp === "mobile", isMobile: vp === "mobile",
      serviceWorkers: "block",
    });
    await ctx.addInitScript(([th, lg]) => {
      try { localStorage.setItem("svoi.theme", th); localStorage.setItem("svoi.lang", lg); } catch { /* ignore */ }
    }, [theme, lang]);
    for (const shot of SHOTS) {
      if (shot.only && shot.only !== vp) continue;
      if (only && !only.some((o) => shot.name.startsWith(o))) continue;
      const srv = servers[shot.server];
      const page = await ctx.newPage();
      const label = `${vp}-${theme}-${shot.name}`;
      watch(page, label, problems);
      try {
        await page.goto(srv.url + "/" + (shot.hash || ""), { waitUntil: "domcontentloaded" });
        await page.waitForFunction(() => !document.querySelector(".boot-splash"), null, { timeout: 8000 });
        if (shot.wait) await page.waitForSelector(shot.wait, { timeout: 8000 });
        if (shot.run) await shot.run(page, srv, vp);
        await sleep(350);
        const file = path.join(OUT, `${vp}-${theme}-${shot.name}.png`);
        await page.screenshot({ path: file, fullPage: !!shot.full });
        count++;
        if (shot.after) await shot.after(page);
      } catch (e) {
        problems.push(`[${label}] FAILED: ${e.message.split("\n")[0]}`);
      }
      await page.close();
      if (shot.reset || shot.server === "onboarding") await srv.hook("/__mock/reset");
      if (shot.name === "offline-banner") await srv.hook("/__mock/drop?for=0");
    }
    await ctx.close();
  }
}
await browser.close();
for (const s of Object.values(servers)) await s.stop();
console.log(`${count} screenshots in ${((Date.now() - t0) / 1000).toFixed(1)} s → ${path.relative(process.cwd(), OUT)}/`);
if (problems.length) {
  console.log(`\n${problems.length} problem(s):\n  ` + problems.join("\n  "));
  process.exit(1);
}
console.log("no console errors");
