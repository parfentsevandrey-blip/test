#!/usr/bin/env node
// Interaction smoke test against the mock server: drives the main flows and
// fails on any console error / uncaught exception.
//
//   NODE_PATH=/opt/node22/lib/node_modules node web-dev/smoke.mjs [--only mail,chat]
//        [--base http://127.0.0.1:18777 --token TOKEN]
//
// With --base the test runs against an already running server (e.g. the real
// node: `svoi demo --no-browser --quiet --port 18777 --dir DIR`, TOKEN = the
// master token in DIR/laptop/data/ui.token) and skips the steps that need
// mock-only hooks. The browser signs in like a person does, with a one-time link.
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { launch, startMock, watch } from "./lib.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));

const argv = process.argv.slice(2);
const external = argv.includes("--base") ? argv[argv.indexOf("--base") + 1].replace(/\/+$/, "") : null;
const token = argv.includes("--token") ? argv[argv.indexOf("--token") + 1] : "";
const problems = [];
const results = [];

const onlyArg = argv.includes("--only") ? argv[argv.indexOf("--only") + 1].split(",") : null;
let failN = 0;
let current = null; // page used by the running step, for failure screenshots
async function step(name, fn) {
  if (onlyArg && !onlyArg.some((o) => name.startsWith(o))) return;
  const t0 = Date.now();
  try {
    await fn();
    results.push(`ok   ${name} (${Date.now() - t0} ms)`);
  } catch (e) {
    const msg = e.message.split("\n").filter((l) => !/^\s*$/.test(l)).slice(0, 3).join(" | ");
    results.push(`FAIL ${name}: ${msg}`);
    problems.push(`${name}: ${msg}`);
    if (current) {
      const file = path.join(here, "screens", `smoke-fail-${++failN}.png`);
      try { fs.mkdirSync(path.dirname(file), { recursive: true }); await current.screenshot({ path: file }); results.push(`     screenshot: ${path.relative(process.cwd(), file)}`); } catch { /* page gone */ }
    }
  }
}

const browser = await launch();
async function newPage(base, label, { mobile = false, lang = "ru" } = {}) {
  const ctx = await browser.newContext({
    viewport: mobile ? { width: 390, height: 844 } : { width: 1360, height: 860 },
    locale: lang === "en" ? "en-GB" : "ru-RU", serviceWorkers: "block", acceptDownloads: true,
  });
  await ctx.addInitScript((lg) => { try { localStorage.setItem("svoi.lang", lg); } catch { /* ignore */ } }, lang);
  const page = await ctx.newPage();
  current = page;
  if (token) {
    // what `svoi url` does: ask the node for a single-use sign-in code with the master token
    const res = await fetch(base + "/api/login/code", { method: "POST", headers: { Authorization: "Bearer " + token, "Content-Type": "application/json" }, body: "{}" });
    const { code } = await res.json();
    await page.goto(base + "/?t=" + encodeURIComponent(code)); // becomes the session cookie
  }
  watch(page, label, problems);
  page.setDefaultTimeout(10000);
  page.base = base;
  page.open = async (hash = "") => {
    await page.goto(base + "/" + hash);
    await page.waitForFunction(() => !document.querySelector(".boot-splash"));
  };
  return page;
}

const mocks = [];
async function mock(args) {
  const m = await startMock(args);
  mocks.push(m);
  return m;
}

// ------------------------------------------------------------- onboarding
if (!external) {
  const onb = await mock(["--calm", "--scenario", "onboarding"]);
  await step("onboarding: create a mesh", async () => {
    const p = await newPage(onb.url, "onb-create");
    await p.open();
    await p.click("[data-testid=onb-create]");
    await p.click("[data-testid=onb-submit]");
    await p.waitForSelector(".field__error"); // mesh name is required
    await p.fill("[data-testid=onb-mesh-name]", "Дача");
    // live preview of the DNS name the node will derive (docs: "Device names")
    await p.fill("[data-testid=onb-device-name]", "Кухонный ноутбук");
    await p.waitForSelector("[data-testid=dns-preview][data-label=kukhonnyy-noutbuk]");
    await p.fill("[data-testid=onb-device-name]", "my laptop");
    await p.waitForSelector("[data-testid=dns-preview][data-label=my-laptop]");
    await p.fill("[data-testid=onb-owner]", "Андрей");
    await p.click("[data-testid=onb-submit]");
    await p.waitForSelector("[data-testid=page-devices]");
    await p.waitForSelector("text=Пока здесь только это устройство");
    const name = await p.textContent(".topbar__name");
    if (name.trim() !== "my-laptop") throw new Error(`device name not normalised: ${name}`);
    await p.context().close();
  });
  await onb.hook("/__mock/reset");
  await step("onboarding: join — bad code, then a good one", async () => {
    const p = await newPage(onb.url, "onb-join");
    await p.open();
    await p.click("[data-testid=onb-join]");
    if (await p.$("[data-testid=onb-owner]")) throw new Error("the join form must not ask for an owner (the inviter sets it)");
    await p.fill("[data-testid=onb-code]", "hello");
    await p.click("[data-testid=onb-submit]");
    await p.waitForSelector("text=Это не похоже на код приглашения");
    await p.fill("[data-testid=onb-code]", "SVOI1-EXPIRED-AAAAAAAA-BBBBBBBB-CCCCCCCC-DDDDDDDD-EEEEEEEE");
    await p.click("[data-testid=onb-submit]");
    await p.waitForSelector("[data-testid=onb-progress]");
    await p.waitForSelector("[data-testid=onb-error]", { timeout: 15000 });
    await p.fill("[data-testid=onb-code]", "svoi1-aeawvqfq-ghijklmn-opqrstuv-wxyz2345-67abcdef-ghijklmn");
    const sent = p.waitForRequest((r) => r.url().endsWith("/api/mesh/join"));
    await p.click("[data-testid=onb-submit]");
    const body = JSON.parse((await sent).postData() || "{}");
    if ("owner" in body || !body.invite || !body.deviceName) throw new Error("join body should be {invite, deviceName}: " + JSON.stringify(body));
    await p.waitForSelector("[data-testid=page-devices]", { timeout: 15000 });
    await p.waitForSelector("[data-testid=device-card]");
    await p.context().close();
  });
}

// ------------------------------------------------------------- main flows
const srv = external ? null : await mock([]);
const base = external || srv.url;
const state = await (await fetch(base + "/api/state", { headers: token ? { Authorization: "Bearer " + token } : {} })).json();
const peer = (n) => state.peers.find((p) => p.deviceName === n || p.name === n);
const NAS = peer("nas");
const page = await newPage(base, "main");

await step("devices: topology, drawer, ping", async () => {
  await page.open("#/devices");
  await page.waitForSelector("[data-testid=topo-node]");
  const nodes = await page.$$("[data-testid=topo-node]");
  if (nodes.length !== state.peers.length) throw new Error(`expected ${state.peers.length} nodes, got ${nodes.length}`);
  await page.click(`[data-testid=topo-node][data-id="${NAS.id}"]`);
  await page.waitForSelector("[data-testid=device-drawer]");
  await page.click("[data-testid=device-ping]");
  await page.waitForFunction(() => /\d/.test(document.querySelector("[data-testid=device-ping-result]").textContent));
  await page.keyboard.press("Escape");
  await page.waitForSelector("[data-testid=device-drawer]", { state: "detached" });
});

await step("devices: add device → QR → device joins", async () => {
  await page.click("[data-testid=add-device]");
  // the inviter decides whose device it is (prefilled with this device's owner)
  const prefilled = await page.inputValue("[data-testid=invite-owner]");
  if (state.self.owner && prefilled !== state.self.owner) throw new Error(`owner not prefilled: "${prefilled}"`);
  await page.fill("[data-testid=invite-owner]", "Анна");
  const sent = page.waitForRequest((r) => r.url().endsWith("/api/invites") && r.method() === "POST");
  await page.click("[data-testid=invite-create]");
  const body = JSON.parse((await sent).postData() || "{}");
  if (body.owner !== "Анна") throw new Error("owner not sent with the invite: " + JSON.stringify(body));
  await page.waitForSelector("[data-testid=invite-qr] img");
  await page.waitForSelector("[data-testid=invite-for]:has-text('Анна')");
  const code = (await page.textContent("[data-testid=invite-code]")).trim();
  if (!code.startsWith("SVOI1-")) throw new Error("bad invite code " + code);
  if (srv) {
    await page.click(".modal__foot .btn--secondary"); // hide: the pending invite is listed with its owner
    await page.waitForSelector("[data-testid=invite-row][data-owner='Анна']");
    await page.click("[data-testid=add-device]");
    await page.click("[data-testid=invite-create]");
    await page.waitForSelector("[data-testid=invite-qr] img");
    await srv.hook("/__mock/join?name=tablet");
    await page.waitForSelector("[data-testid=invite-done]", { timeout: 10000 });
    await page.waitForSelector("[data-testid=invite-done] [data-testid=invite-for]:has-text('Андрей')");
    await page.click(".modal__foot .btn--primary"); // open the new device
    await page.waitForSelector("[data-testid=device-drawer]");
    await page.keyboard.press("Escape");
  } else {
    await page.keyboard.press("Escape");
  }
});

await step("devices: manage — admin note, promote warning, rename preview, revoke warning", async () => {
  const admin = state.peers.find((p) => p.admin);
  const regular = state.peers.find((p) => !p.admin && p.online);
  if (!state.self.admin || !admin || !regular) return; // needs an admin self, an admin peer and an online member
  await page.open("#/devices/" + encodeURIComponent(admin.id));
  await page.waitForSelector("[data-testid=manage-admin-note]");
  if (await page.$("[data-testid=manage-promote]")) throw new Error("an admin must not offer promote/demote");
  await page.click("[data-testid=manage-revoke]");
  await page.waitForSelector("[data-testid=revoke-admin-warning]");
  if (!(await page.isDisabled("[data-testid=confirm-ok]"))) throw new Error("revoke must need the typed name");
  await page.click("[data-testid=confirm-cancel]");
  await page.open("#/devices/" + encodeURIComponent(regular.id));
  await page.click("[data-testid=manage-promote]");
  await page.waitForSelector("[data-testid=confirm-ok]");
  await page.click("[data-testid=confirm-cancel]");
  await page.click("[data-testid=manage-revoke]");
  await page.waitForSelector("[data-testid=confirm-input]");
  if (await page.$("[data-testid=revoke-admin-warning]")) throw new Error("admin warning shown for a regular member");
  await page.click("[data-testid=confirm-cancel]");
  await page.click("[data-testid=manage-rename]");
  await page.fill("[data-testid=prompt-input]", "Мой NAS (дача)");
  await page.waitForSelector("[data-testid=dns-preview][data-label=moy-nas-dacha]");
  await page.click("[data-testid=prompt-ok]");
  await page.waitForSelector(".modal .field__error"); // parentheses are still refused by the form
  if (srv) {
    await page.fill("[data-testid=prompt-input]", "Кухонный ноутбук");
    await page.waitForSelector("[data-testid=dns-preview][data-label=kukhonnyy-noutbuk]");
    await page.click("[data-testid=prompt-ok]");
    await page.waitForSelector("[data-testid=device-drawer] .drawer__title:has-text('kukhonnyy-noutbuk')");
    await srv.hook("/__mock/reset");
  } else {
    await page.keyboard.press("Escape");
  }
  await page.keyboard.press("Escape");
  await page.waitForSelector("[data-testid=device-drawer]", { state: "detached" });
});

await step("files: send a file to nas and watch it get delivered", async () => {
  await page.open("#/files/send");
  await page.click(`[data-testid=device-chip][data-id="${NAS.id}"]`);
  await page.setInputFiles("[data-testid=dropzone-input]", { name: "smoke-test.txt", mimeType: "text/plain", buffer: Buffer.from("hello from the smoke test\n".repeat(2000)) });
  await page.waitForSelector("[data-testid=staged-file]");
  await page.click("[data-testid=send-submit]");
  await page.waitForSelector("[data-testid=staged-file]", { state: "detached", timeout: 10000 });
  await page.waitForSelector("[data-testid=transfer][data-dir=out]:has-text('smoke-test.txt')");
  if (srv) await page.waitForSelector("[data-testid=transfer][data-state=done]:has-text('smoke-test.txt')", { timeout: 20000 });
});

if (srv) {
  await step("files: incoming offers — accept from the banner and from the list", async () => {
    await page.open("#/devices");
    // the seeded offer is the only one: the banner shows inline buttons
    await page.waitForSelector("[data-testid=offers-banner] [data-testid=offer-accept]");
    await page.click("[data-testid=offer-accept]");
    await page.waitForSelector("[data-testid=offers-banner]", { state: "detached", timeout: 10000 });
    const offer = await srv.hook("/__mock/offer?from=phone");
    await page.waitForSelector("[data-testid=offers-banner]");
    await page.waitForSelector("[data-testid=toast]");
    await page.open("#/files/send");
    await page.click(`[data-testid=transfer][data-id="${offer.id}"] [data-testid=transfer-accept]`);
    await page.waitForSelector(`[data-testid=transfer][data-id="${offer.id}"][data-state=done]`, { timeout: 20000 });
    const open = await page.$(`[data-testid=transfer][data-id="${offer.id}"] [data-testid=transfer-open]`);
    if (open) {
      await open.click();
      await page.waitForSelector("[data-testid=preview]");
      await page.keyboard.press("Escape");
      await page.waitForSelector("[data-testid=preview]", { state: "detached" });
    }
  });
}

await step("files: browse nas photos, grid, preview with next", async () => {
  await page.open(`#/files/browse/${NAS.id}`);
  await page.click("[data-testid=share-card][data-id=sh_photo]");
  await page.click("[data-testid=file-row][data-name='2025'] a");
  await page.click("[data-testid=file-row][data-name='Байкал'] a");
  await page.waitForSelector("[data-testid=file-row][data-dir=false]");
  await page.click("[data-testid=view-grid]");
  await page.waitForSelector("[data-testid=file-tile] img");
  await page.click("[data-testid=file-tile] >> nth=0 >> .ftile__open");
  await page.waitForSelector("[data-testid=preview] .pv__img.is-loaded");
  const first = await page.textContent("[data-testid=preview-name]");
  await page.click("[data-testid=preview-next]");
  await page.waitForFunction((f) => document.querySelector("[data-testid=preview-name]").textContent !== f, first);
  await page.keyboard.press("ArrowRight");
  await page.keyboard.press("Escape");
  await page.waitForSelector("[data-testid=preview]", { state: "detached" });
  await page.click("[data-testid=view-list]");
});

await step("files: text, pdf and video previews", async () => {
  await page.open(`#/files/browse/${NAS.id}/sh_docs`);
  await page.click("[data-testid=file-row][data-name='Заметки.txt'] button");
  await page.waitForSelector("[data-testid=preview] .pv__text pre:has-text('Wi-Fi')");
  await page.keyboard.press("Escape");
  await page.click("[data-testid=file-row][data-name='Договор аренды гаража.pdf'] button");
  await page.waitForSelector("[data-testid=preview] iframe.pv__pdf");
  await page.keyboard.press("Escape");
  await page.click("[data-testid=file-row][data-name='test-page.html'] button");
  await page.waitForSelector("[data-testid=preview] .pv__text pre:has-text('<script>')"); // shown as text, never rendered
  await page.keyboard.press("Escape");
  await page.open(`#/files/browse/${NAS.id}/sh_video`);
  await page.click("[data-testid=file-row][data-name='Отпуск 2024.webm'] button");
  await page.waitForSelector("[data-testid=preview] video");
  await page.waitForFunction(() => document.querySelector("[data-testid=preview] video").readyState >= 1, null, { timeout: 10000 });
  await page.keyboard.press("Escape");
});

await step("files: read-only share has no upload", async () => {
  await page.open(`#/files/browse/${NAS.id}/sh_video`);
  await page.waitForSelector("[data-testid=read-only]");
  if (await page.$("[data-testid=upload-button]")) throw new Error("upload offered on a read-only share");
});

await step("files: upload, overwrite on 409, new folder, rename, delete", async () => {
  await page.open(`#/files/browse/${NAS.id}/sh_photo/${encodeURIComponent("Разобрать позже")}`);
  await page.waitForSelector("text=Папка пуста");
  const file = { name: "upload.txt", mimeType: "text/plain", buffer: Buffer.from("v1") };
  await page.setInputFiles("[data-testid=upload-input]", file);
  await page.waitForSelector("[data-testid=file-row][data-name='upload.txt']");
  await page.setInputFiles("[data-testid=upload-input]", { ...file, buffer: Buffer.from("version two") });
  await page.click("[data-testid=confirm-ok]"); // "Заменить"
  await page.waitForSelector("[data-testid=upload-item][data-status=done]");
  await page.click("[data-testid=new-folder]");
  await page.fill("[data-testid=prompt-input]", "Новая папка");
  await page.click("[data-testid=prompt-ok]");
  await page.waitForSelector("[data-testid=file-row][data-name='Новая папка']");
  await page.click("[data-testid=file-row][data-name='upload.txt'] .frow__act button[aria-haspopup]");
  await page.click(".menu__item:has-text('Переименовать')");
  // the base name ("upload") is pre-selected: typing replaces just that part
  await page.waitForFunction(() => document.activeElement && document.activeElement.dataset.testid === "prompt-input");
  await page.keyboard.type("renamed");
  const v = await page.inputValue("[data-testid=prompt-input]");
  if (v !== "renamed.txt") throw new Error("rename prefill/selection wrong: " + v);
  await page.click("[data-testid=prompt-ok]");
  await page.waitForSelector("[data-testid=file-row][data-name='renamed.txt']");
  await page.click("[data-testid=file-row][data-name='renamed.txt'] .frow__act button[aria-haspopup]");
  await page.click(".menu__item.is-danger");
  await page.click("[data-testid=confirm-ok]");
  await page.waitForSelector("[data-testid=file-row][data-name='renamed.txt']", { state: "detached" });
});

await step("dialogs: keyboard — focus in, Tab trap, Escape, focus back to opener", async () => {
  const active = () => page.evaluate(() => document.activeElement && (document.activeElement.dataset.testid || document.activeElement.tagName));
  const inDialog = () => page.evaluate(() => !!(document.activeElement && document.activeElement.closest("[role=dialog]")));
  await page.open(`#/files/browse/${NAS.id}/sh_photo/${encodeURIComponent("Разобрать позже")}`);
  await page.click("[data-testid=new-folder]");
  await page.waitForFunction(() => document.activeElement && document.activeElement.dataset.testid === "prompt-input");
  await page.keyboard.type("Папка 1", { delay: 5 });
  if ((await page.inputValue("[data-testid=prompt-input]")) !== "Папка 1") throw new Error("typed characters lost");
  for (let i = 0; i < 8; i++) await page.keyboard.press("Tab");
  if (!(await inDialog())) throw new Error("Tab escaped the dialog");
  await page.keyboard.press("Escape");
  await page.waitForSelector("[data-testid=prompt-input]", { state: "detached" });
  await page.waitForFunction(() => document.activeElement && document.activeElement.dataset.testid === "new-folder");
  await page.open("#/devices");
  await page.click("[data-testid=add-device]");
  await page.waitForSelector("[data-testid=invite-create]");
  if (!(await inDialog())) throw new Error("focus not moved into the invite dialog: " + (await active()));
  await page.keyboard.press("Escape");
  await page.waitForFunction(() => document.activeElement && document.activeElement.dataset.testid === "add-device");
});

await step("files: share a folder via the folder picker", async () => {
  await page.open("#/files/shares");
  await page.click("[data-testid=share-add]");
  await page.click("[data-testid=share-pick]");
  await page.waitForSelector("[data-testid=folder-picker] [data-testid=picker-item]");
  await page.click("[data-testid=picker-item][data-name=Pictures]");
  await page.click("[data-testid=picker-item][data-name='2025']");
  await page.click("[data-testid=picker-choose]");
  const name = await page.inputValue("[data-testid=share-name]");
  if (name !== "2025") throw new Error("name not prefilled from folder: " + name);
  await page.fill("[data-testid=share-name]", "Фото 2025");
  await page.click("[data-testid=share-save]");
  await page.waitForSelector("[data-testid=share-row][data-name='Фото 2025']");
});

if (srv) {
  await step("files: a share holding svoi's keys is blocked and refused", async () => {
    await page.open("#/files/shares");
    await page.waitForSelector("[data-testid=share-row][data-id=sh_home][data-blocked=true] [data-testid=share-blocked]");
    await page.click("[data-testid=share-add]");
    await page.fill("[data-testid=share-path]", "/home/andrey");
    await page.fill("[data-testid=share-name]", "Дом");
    await page.click("[data-testid=share-save]");
    await page.waitForSelector("[data-testid=share-error]:has-text('keys')"); // the node's own message is shown
    await page.keyboard.press("Escape");
    await page.waitForSelector("[data-testid=share-error]", { state: "detached" });
  });
}

await step("mail: compose with attachment, see it in Sent with delivery", async () => {
  await page.open("#/mail/inbox");
  await page.waitForSelector("[data-testid=mail-item]");
  await page.click("[data-testid=mail-compose] >> visible=true");
  await page.waitForSelector("[data-testid=compose]");
  await page.click(`[data-testid=compose] [data-testid=device-chip][data-id="${NAS.id}"]`);
  await page.fill("[data-testid=compose-subject]", "Smoke test");
  await page.fill("[data-testid=compose-body]", "Body with a link https://example.org/x and a newline\nsecond line");
  await page.setInputFiles("[data-testid=compose-files]", { name: "note.txt", mimeType: "text/plain", buffer: Buffer.from("attachment") });
  await page.waitForSelector("[data-testid=attachment][data-status=ready]");
  await page.click("[data-testid=compose-send]");
  await page.waitForSelector("[data-testid=compose]", { state: "detached" });
  await page.click("[data-testid=mail-folder-sent] >> visible=true");
  await page.click("[data-testid=mail-item]:has-text('Smoke test') a");
  await page.waitForSelector("[data-testid=mail-reader] a[href='https://example.org/x'][rel~=noopener]");
  if (srv) await page.waitForSelector("[data-testid=mail-recipient][data-state=delivered]", { timeout: 15000 });
});

if (srv) {
  await step("mail & chat: a large attachment needs consent, a failed one retries", async () => {
    await page.open("#/mail/inbox");
    await page.click("[data-testid=mail-item]:has-text('Фото с юбилея') a");
    const video = "[data-testid=mail-attachment]:has-text('Видео с юбилея')";
    await page.waitForSelector(`${video}[data-state=remote] [data-testid=attachment-fetch]`);
    const label = await page.textContent(`${video} [data-testid=attachment-fetch]`);
    if (!/31\s*МБ/.test(label)) throw new Error("size missing on the consent button: " + label);
    await page.click(`${video} [data-testid=attachment-fetch]`);
    await page.waitForSelector(`${video}[data-state=fetching]`, { timeout: 1500 }); // at once: no event at the start
    await page.waitForSelector(`${video}[data-state=ready]`, { timeout: 15000 }); // the end event → re-read
    await page.open("#/mail/inbox");
    await page.click("[data-testid=mail-item]:has-text('Скриншот ошибки') a");
    await page.click("[data-testid=mail-attachment][data-state=failed] [data-testid=attachment-retry]");
    await page.waitForSelector("[data-testid=mail-attachment][data-state=ready]", { timeout: 15000 });
    const dad = state.peers.find((x) => x.deviceName === "dad-pc");
    await page.open("#/chat/" + encodeURIComponent(dad.id));
    const clip = "[data-testid=chat-attachment]:has-text('Юбилей.mp4')";
    await page.click(`${clip}[data-state=remote] [data-testid=attachment-fetch]`);
    await page.waitForSelector(`${clip}[data-state=fetching]`, { timeout: 1500 });
    await page.waitForSelector(`${clip}[data-state=ready]`, { timeout: 15000 }); // the chat event carries the message
  });
}

await step("mail: open, reply prefill, trash", async () => {
  await page.open("#/mail/inbox");
  await page.click("[data-testid=mail-item] >> nth=1 >> a");
  await page.waitForSelector("[data-testid=mail-reader]");
  await page.click("[data-testid=mail-reply]");
  await page.waitForSelector("[data-testid=compose]");
  await page.waitForFunction(() => /^Re: /.test(document.querySelector("[data-testid=compose-subject]").value));
  await page.keyboard.press("Escape"); // the quoted reply is not "dirty": closes right away
  await page.waitForSelector("[data-testid=compose]", { state: "detached" });
  await page.click("[data-testid=mail-reply]");
  await page.waitForFunction(() => document.activeElement && document.activeElement.dataset.testid === "compose-body");
  await page.waitForFunction(() => /пишет:/.test(document.querySelector("[data-testid=compose-body]").value));
  await page.keyboard.type("Спасибо!");
  const body = await page.inputValue("[data-testid=compose-body]");
  if (!body.startsWith("Спасибо!")) throw new Error("reply should start above the quote: " + body.slice(0, 40));
  await page.keyboard.press("Escape"); // now there is a draft → confirm discarding
  await page.click("[data-testid=confirm-ok]");
  await page.waitForSelector("[data-testid=compose]", { state: "detached" });
  await page.click("[data-testid=mail-trash]");
  await page.waitForSelector("[data-testid=toast]:has-text('корзин')");
});

await step("chat: send a message with Enter and see it delivered", async () => {
  const dad = peer("dad-pc");
  await page.open(`#/chat/${dad.id}`);
  await page.waitForSelector("[data-testid=bubble]");
  await page.fill("[data-testid=chat-input]", "Привет из смоук-теста");
  await page.keyboard.press("Enter");
  await page.waitForSelector("[data-testid=bubble][data-mine=true]:has-text('смоук-теста')");
  if (srv) await page.waitForSelector("[data-testid=bubble][data-mine=true][data-state=delivered]:has-text('смоук-теста')", { timeout: 10000 });
  if (srv) {
    await srv.hook("/__mock/chat?from=dad-pc&text=" + encodeURIComponent("Ответ из мока"));
    await page.waitForSelector("[data-testid=bubble][data-mine=false]:has-text('Ответ из мока')");
  }
});

await step("services: connect a forward and disconnect it", async () => {
  await page.open("#/services");
  const card = `[data-testid=service-card][data-service=jellyfin]`;
  await page.click(`${card} [data-testid=service-connect]`);
  await page.waitForSelector(`${card} [data-testid=forward-addr]`);
  await page.waitForSelector(`${card} a:has-text('Открыть в браузере')`);
  await page.click(`${card} [data-testid=service-disconnect]`);
  await page.waitForSelector(`${card}[data-forwarded=false]`);
});

await step("services: publish a service", async () => {
  await page.click("[data-testid=service-publish]");
  await page.fill("[data-testid=service-name]", "grafana-dev");
  await page.fill("[data-testid=service-addr]", "127.0.0.1:3001");
  await page.click("[data-testid=service-save]");
  await page.waitForSelector("[data-testid=published-service][data-name=grafana-dev]");
});

await step("settings: relay toggle, TUN on/off, language and theme", async () => {
  await page.open("#/settings");
  await page.click("[data-testid=leave-mesh]"); // the confirmation says what gets reset
  await page.waitForSelector(".modal .confirm-text:has-text('Почта и история чатов останутся')");
  await page.click("[data-testid=confirm-cancel]");
  await page.click("[data-testid=setting-relay]");
  await page.waitForSelector("[data-testid=toast]:has-text('Сохранено')");
  if (await page.$("[data-testid=tun-section]")) {
    await page.click("[data-testid=tun-enabled]");
    await page.waitForSelector("[data-testid=tun-state][data-state=running]");
    if (srv) {
      await page.click("[data-testid=tun-enabled]");
      await page.waitForSelector("[data-testid=tun-state][data-state=off]");
      await srv.hook("/__mock/tun?error=1");
      await page.click("[data-testid=tun-enabled]");
      await page.waitForSelector("[data-testid=tun-state][data-state=error]");
      await page.waitForSelector("[data-testid=tun-section] .callout--err:has-text('permission denied')");
      await srv.hook("/__mock/tun?error=0");
      await page.click("[data-testid=tun-enabled]"); // back off
      await page.waitForSelector("[data-testid=tun-state][data-state=off]");
    }
  }
  await page.click("[data-testid=lang-en]");
  await page.waitForSelector("[data-testid=nav-devices]:has-text('Devices')");
  await page.click("[data-testid=lang-ru]");
  await page.waitForSelector("[data-testid=nav-devices]:has-text('Устройства')");
  await page.click("[data-testid=theme-light]");
  if ((await page.getAttribute("html", "data-theme")) !== "light") throw new Error("theme not applied");
  await page.click("[data-testid=theme-dark]");
});

if (srv) {
  await step("global: offline banner and recovery", async () => {
    await page.open("#/devices");
    await srv.hook("/__mock/drop?for=3");
    await page.waitForSelector("[data-testid=offline-banner]", { timeout: 8000 });
    await page.waitForSelector("[data-testid=offline-banner]", { state: "detached", timeout: 20000 });
  });

  await step("mobile: tab bar, drill-down chat and back", async () => {
    const m = await newPage(base, "mobile", { mobile: true });
    await m.open("#/chat");
    await m.click("[data-testid=thread] >> nth=0");
    await m.waitForSelector("[data-testid=conversation]");
    if (await m.isVisible(".tabbar")) throw new Error("tab bar should hide inside a conversation");
    await m.click(".conv__head button[aria-label='Назад']");
    await m.waitForSelector("[data-testid=thread]");
    await m.click("[data-testid=tab-more]");
    await m.waitForSelector(".more-row");
    await m.context().close();
  });

  const rm = await mock(["--calm"]);
  await step("removed by an admin → onboarding with a notice, then join again", async () => {
    const p = await newPage(rm.url, "removed");
    await p.open("#/settings/network");
    await p.waitForFunction(() => window.__svoi.state.conn === "online" && window.__svoi.state.peers.length > 0);
    await rm.hook("/__mock/removed");
    await p.waitForSelector("[data-testid=removed-notice]:has-text('Дом')", { timeout: 8000 });
    if ((await p.evaluate(() => location.hash)) !== "#/") throw new Error("route not reset to #/");
    if (await p.$("[data-testid=toast]:has-text('removed')")) throw new Error("raw 'removed' notify shown as a toast");
    await p.click("[data-testid=onb-join]");
    await p.fill("[data-testid=onb-code]", "SVOI1-AEAWVQFQ-GHIJKLMN-OPQRSTUV-WXYZ2345-67ABCDEF-GHIJKLMN");
    await p.click("[data-testid=onb-submit]");
    await p.waitForSelector("[data-testid=page-devices]", { timeout: 15000 });
    if (await p.$("[data-testid=removed-notice]")) throw new Error("notice still shown after joining");
    await p.context().close();
  });

  const auth = await mock(["--calm", "--auth"]);
  await step("auth: expired screen, one-time link, used link, sign out", async () => {
    const p = await newPage(auth.url, "auth");
    await p.open();
    await p.waitForSelector("[data-testid=unauthorized][data-reason=expired]");
    const { url } = await auth.hook("/__mock/login"); // what `svoi url` prints
    await p.goto(auth.url + url);
    await p.waitForSelector("[data-testid=page-devices]");
    if ((await p.evaluate(() => location.search)).includes("t=")) throw new Error("login code left in the address bar");
    // the same link again, in a fresh browser: used up
    const q = await newPage(auth.url, "auth-reuse");
    await q.goto(auth.url + url);
    await q.waitForSelector("[data-testid=unauthorized][data-reason=link]");
    if ((await q.evaluate(() => location.search)).includes("t=")) throw new Error("dead code not removed from the address bar");
    await q.context().close();
    // sign out from Settings
    await p.open("#/settings");
    await p.click("[data-testid=logout]");
    await p.click("[data-testid=confirm-ok]");
    await p.waitForSelector("[data-testid=unauthorized][data-reason=signed-out]");
    await p.click("[data-testid=auth-retry]");
    await p.waitForSelector("[data-testid=unauthorized][data-reason=signed-out]"); // still signed out
    const st = await p.evaluate(() => fetch("api/state").then((r) => r.status));
    if (st !== 401) throw new Error("session still valid after sign-out: " + st);
    await p.context().close();
  });
}

await browser.close();
for (const m of mocks) await m.stop();
console.log(results.join("\n"));
const consoleProblems = problems.filter((x) => x.startsWith("["));
console.log(`\n${results.filter((r) => r.startsWith("ok")).length}/${results.length} steps passed, ${consoleProblems.length} console problem(s)`);
if (problems.length) {
  console.log("\nproblems:\n  " + problems.join("\n  "));
  process.exit(1);
}
