#!/usr/bin/env node
// Interaction smoke test against the mock server: drives the main flows and
// fails on any console error / uncaught exception.
//
//   NODE_PATH=/opt/node22/lib/node_modules node web-dev/smoke.mjs [--only mail,chat]
//        [--base http://127.0.0.1:18777 --token TOKEN]
//
// With --base the test runs against an already running server (e.g. the real
// node: `themesh demo --no-browser --quiet --port 18777 --dir DIR`, TOKEN = the
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
  await ctx.addInitScript((lg) => { try { localStorage.setItem("themesh.lang", lg); } catch { /* ignore */ } }, lang);
  const page = await ctx.newPage();
  current = page;
  if (token) {
    // what `themesh url` does: ask the node for a single-use sign-in code with the master token
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

/** page.waitForRequest whose rejection cannot get lost when the step fails before it is awaited. */
function expectRequest(page, pred) {
  const pending = page.waitForRequest(pred);
  pending.catch(() => {});
  return pending;
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
    // a new mesh lands on Home: "you're alone — add a second device" and the first steps
    await p.waitForSelector("[data-testid=page-home]");
    await p.waitForSelector("[data-testid=home-status][data-state=alone]:has-text('Вы пока одни в сети')");
    await p.waitForSelector("[data-testid=home-start-checklist] [data-step=add][data-done=false]");
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
    await p.fill("[data-testid=onb-code]", "MESH1-EXPIRED-AAAAAAAA-BBBBBBBB-CCCCCCCC-DDDDDDDD-EEEEEEEE");
    await p.click("[data-testid=onb-submit]");
    await p.waitForSelector("[data-testid=onb-progress]");
    await p.waitForSelector("[data-testid=onb-error]", { timeout: 15000 });
    await p.fill("[data-testid=onb-code]", "mesh1-aeawvqfq-ghijklmn-opqrstuv-wxyz2345-67abcdef-ghijklmn");
    const sent = p.waitForRequest((r) => r.url().endsWith("/api/mesh/join"));
    await p.click("[data-testid=onb-submit]");
    const body = JSON.parse((await sent).postData() || "{}");
    if ("owner" in body || !body.invite || !body.deviceName) throw new Error("join body should be {invite, deviceName}: " + JSON.stringify(body));
    await p.waitForSelector("[data-testid=page-home]", { timeout: 15000 });
    await p.waitForSelector("[data-testid=home-device]");
    await p.context().close();
  });
  await onb.hook("/__mock/reset");
  await step("nearby: the devices around show up by themselves; one tap, the same digits on both screens, joined", async () => {
    const p = await newPage(onb.url, "nearby-join");
    await p.open();
    // nobody around: the app says that it is looking, and the two other ways in are still there
    const sec = "[data-testid=nearby]";
    await p.waitForSelector(`${sec}[data-found="0"]:has-text('Ищем устройства с The Mesh рядом')`);
    await p.waitForSelector("[data-testid=onb-create]");
    await p.waitForSelector("[data-testid=onb-join]");
    // a Mac with the app shows up in the list without anybody doing anything
    await onb.hook("/__mock/nearby?add=macbook-andrey&os=darwin&mesh=Дом");
    const row = "[data-testid=nearby-device][data-name=macbook-andrey]";
    await p.waitForSelector(`${sec}[data-found="1"] ${row}:has-text('сеть «Дом»'):has-text('macOS')`);
    await p.waitForSelector(`${sec}:has-text('Найдено рядом: 1 устройство')`);
    // it can be named differently before asking; the real DNS name is previewed
    if ((await p.textContent("[data-testid=nearby-name]")).trim() !== "work-laptop") throw new Error("the offered name is not the node's own");
    await p.click("[data-testid=nearby-rename]");
    await p.fill("[data-testid=nearby-name-input]", "Мой ноутбук");
    await p.waitForSelector("[data-testid=dns-preview][data-label=moy-noutbuk]");
    await p.keyboard.press("Enter");
    await p.waitForSelector("[data-testid=nearby-name]:has-text('Мой-ноутбук')"); // the name keeps its letters; only the address in the network is Latin
    const sent = expectRequest(p, (r) => r.url().endsWith("/api/nearby/connect"));
    await p.click(`${row} [data-testid=nearby-connect]`);
    const body = JSON.parse((await sent).postData() || "{}");
    if (!body.id || !/^[\p{L}\p{N}][\p{L}\p{N}._-]*$/u.test(body.deviceName || "")) throw new Error("connect body should be {id, deviceName}: " + JSON.stringify(body));
    // the start screen gives way to the request: connecting → the digits to compare
    const join = "[data-testid=nearby-join]";
    await p.waitForSelector(`${join}[data-state=connecting]:has-text('Соединяемся с «macbook-andrey»')`);
    await p.waitForSelector(`${join}[data-state=waiting]`);
    const code = await p.getAttribute("[data-testid=nearby-code] .nearby-code__digits", "data-code");
    if (!/^\d{6}$/.test(code)) throw new Error("the digits to compare: " + code);
    if (!(await p.textContent("[data-testid=nearby-code]")).replace(/\s/g, "").includes(code)) throw new Error("the digits are not on the screen");
    if (await p.$("[data-testid=onb-create]")) throw new Error("the start choices must give way while a request runs");
    await p.click("[data-testid=nearby-match]");
    await p.waitForSelector(`${join}[data-state=confirmed]:has-text('Ждём разрешения')`);
    // the other person says yes → this device is in the mesh, with the usual welcome
    await p.waitForSelector("[data-testid=page-home]", { timeout: 15000 });
    await p.waitForSelector("[data-testid=toast]:has-text('Вы в сети')");
    await p.waitForSelector("[data-testid=home-device]");
    if ((await p.textContent(".topbar__name")).trim() !== "moy-noutbuk") throw new Error("the device took another name: " + (await p.textContent(".topbar__name")));
    await p.context().close();
  });

  await onb.hook("/__mock/reset");
  await step("nearby: cancel, a refusal, a device that does not answer, a device that leaves the list", async () => {
    const p = await newPage(onb.url, "nearby-fail");
    await p.open();
    const sec = "[data-testid=nearby]";
    await onb.hook("/__mock/nearby?add=macbook-andrey&os=darwin");
    await onb.hook("/__mock/nearby?add=denied-pc&os=windows&mesh=Работа");
    await onb.hook("/__mock/nearby?add=offline-nas&os=linux");
    await p.waitForSelector(`${sec} [data-testid=nearby-device]:nth-child(3)`);
    await p.waitForSelector(`${sec}:has-text('Найдено рядом: 3 устройства')`);
    const join = "[data-testid=nearby-join]";
    // cancel while the digits are on the screen: back to the list, and asking again works
    await p.click("[data-testid=nearby-device][data-name=macbook-andrey] [data-testid=nearby-connect]");
    await p.waitForSelector(`${join}[data-state=waiting]`);
    await p.click("[data-testid=nearby-cancel]");
    await p.waitForSelector(`${sec}[data-found="3"]`);
    await p.click("[data-testid=nearby-device][data-name=macbook-andrey] [data-testid=nearby-connect]");
    await p.waitForSelector(`${join}[data-state=waiting]`);
    await p.click("[data-testid=nearby-cancel]");
    await p.waitForSelector(sec);
    // the person at the other device says no
    await p.click("[data-testid=nearby-device][data-name=denied-pc] [data-testid=nearby-connect]");
    await p.waitForSelector(`${join}[data-state=waiting]`);
    await p.click("[data-testid=nearby-match]");
    await p.waitForSelector(`${join}[data-state=denied][data-reason=denied] [data-testid=nearby-error]:has-text('«denied-pc» не добавил это устройство')`);
    await p.click("[data-testid=nearby-back]");
    await p.waitForSelector(`${sec}[data-found="3"]`);
    // a device that does not answer: the things to check, in words
    await p.click("[data-testid=nearby-device][data-name=offline-nas] [data-testid=nearby-connect]");
    await p.waitForSelector(`${join}[data-state=failed][data-reason=offline] [data-testid=nearby-reach-help]:has-text('изоляция клиентов')`, { timeout: 10000 });
    const text = await p.textContent(join);
    for (const re of [/\bQUIC\b/, /\bNAT\b/, /timeout/i]) if (re.test(text.replace(/mesh: cannot reach[^]*$/, ""))) throw new Error("jargon in the advice: " + re);
    // it leaves the list while the person looks at the advice: "again" is not offered, the way back is
    await onb.hook("/__mock/nearby?remove=offline-nas");
    await p.waitForSelector("[data-testid=nearby-again]", { state: "detached" });
    await p.click("[data-testid=nearby-back]");
    await p.waitForSelector(`${sec}[data-found="2"]`);
    await onb.hook("/__mock/nearby?clear=1");
    await p.waitForSelector(`${sec}[data-found="0"]`);
    // the system forbids looking around: the section says so and says what to do (instead of "looking…" for ever)
    await onb.hook("/__mock/lan?state=blocked&os=darwin");
    await p.waitForSelector(`${sec}:has-text('macOS не пускает The Mesh в домашнюю сеть')`);
    if (!/Локальная сеть/.test(await p.textContent(sec))) throw new Error("the section does not say where to switch the permission on");
    await p.context().close();
  });
  await onb.hook("/__mock/reset");
}

// ------------------------------------------------------------- main flows
const srv = external ? null : await mock([]);
const base = external || srv.url;
const state = await (await fetch(base + "/api/state", { headers: token ? { Authorization: "Bearer " + token } : {} })).json();
const peer = (n) => state.peers.find((p) => p.deviceName === n || p.name === n);
const NAS = peer("nas");
const page = await newPage(base, "main");
const PHONE = peer("phone");
const hashIs = (re) => page.waitForFunction((src) => new RegExp(src).test(location.hash), re.source);

await step("home: the landing page says how the network is in words", async () => {
  await page.open();
  await page.waitForSelector("[data-testid=page-home]");
  await page.waitForSelector("[data-testid=nav-home][aria-current=page]");
  // devices that have been away for days are mentioned calmly; the network itself is fine
  const off = state.peers.filter((p) => !p.online).map((p) => p.name);
  const status = page.locator("[data-testid=home-status]");
  await status.waitFor();
  const st = await status.getAttribute("data-state");
  const text = await status.innerText();
  const n = state.peers.length - off.length + 1, total = state.peers.length + 1;
  if (st !== "ok" || !text.includes(`Всё в порядке — ${n} из ${total} устройств на связи`)) throw new Error(`status: [${st}] ${text}`);
  if (!off.every((x) => text.includes(x))) throw new Error(`the long-absent devices ${off} should be mentioned: ${text}`);
  const main = await page.textContent("main");
  for (const re of [/\bNAT\b/, /ретранслят/i, /IPv[46]/, /\d\s?мс(?![а-яё])/i, /\bID\b/]) if (re.test(main)) throw new Error(`jargon on Home: ${re}`);
  if (srv) {
    // a device drops off just now → the sentence names it; it comes back → all good again
    await srv.hook("/__mock/peer?name=nas&online=0");
    await page.waitForSelector("[data-testid=home-status][data-state=partial]:has-text('Часть устройств не в сети: nas')");
    await page.waitForSelector(`[data-testid=home-device][data-peer="${NAS.id}"][data-online=false]:has-text('Не в сети')`);
    await srv.hook("/__mock/peer?name=nas&online=1");
    await page.waitForSelector("[data-testid=home-status][data-state=ok]");
  }
  // every device has a card with a plain status line and three labelled buttons
  const cards = await page.$$("[data-testid=home-device]");
  if (cards.length !== state.peers.length) throw new Error(`expected ${state.peers.length} device cards, got ${cards.length}`);
  const nas = `[data-testid=home-device][data-peer="${NAS.id}"]`;
  await page.waitForSelector(`${nas} [data-testid=conn-line][data-state=on]`);
  if (PHONE && PHONE.path === "relay") await page.waitForSelector(`[data-testid=home-device][data-peer="${PHONE.id}"] [data-testid=conn-line]:has-text('через')`);
  if ((await page.textContent(`${nas} [data-testid=device-act-send]`)).trim() !== "Отправить файл") throw new Error("send button label");
});

await step("home: what needs attention — accept an offer right there, links to mail and chat", async () => {
  await page.open("#/home");
  await page.waitForSelector("[data-testid=home-attention]");
  if (await page.$("[data-testid=offers-banner]")) throw new Error("Home lists offers itself; the banner must not repeat them");
  if (state.counters.mail) await page.waitForSelector("[data-testid=home-att-mail] a[href='#/mail/inbox']");
  if (state.counters.chat) await page.waitForSelector("[data-testid=home-att-chat] a[href='#/chat']");
  if (srv) {
    await page.waitForSelector("[data-testid=home-att-invite]"); // the mock has a pending invitation
    const offer = await srv.hook("/__mock/offer?from=phone&name=" + encodeURIComponent("Фото с дачи.jpg"));
    const row = `[data-testid=home-offer][data-id="${offer.id}"]`;
    await page.waitForSelector(`${row}:has-text('Фото с дачи.jpg')`);
    await page.click(`${row} [data-testid=offer-accept]`);
    await page.waitForSelector(row, { state: "detached", timeout: 10000 });
    await page.waitForSelector("[data-testid=toast]:has-text('Принимаем')");
    const tr = (await srv.get("/api/transfers")).find((x) => x.id === offer.id);
    if (!tr || tr.state === "offered") throw new Error("the offer was not accepted: " + JSON.stringify(tr && tr.state));
  }
});

await step("home: the four actions lead to the real flows", async () => {
  await page.open("#/home");
  // more than one device: «Кому отправить файл?»
  await page.click("[data-testid=home-action-send]");
  await page.waitForSelector("[data-testid=device-pick] .modal__title:has-text('Кому отправить файл?')");
  await page.click(`[data-testid=device-pick-item][data-peer="${NAS.id}"]`);
  await hashIs(/^#\/files\/send\?to=/);
  await page.waitForSelector(`[data-testid=device-chip][data-id="${NAS.id}"][aria-pressed=true]`);
  await page.open("#/home");
  await page.click("[data-testid=home-action-chat]");
  await page.waitForSelector("[data-testid=device-pick] .modal__title:has-text('Кому написать?')");
  await page.click(`[data-testid=device-pick-item][data-peer="${NAS.id}"]`);
  await page.waitForSelector("[data-testid=conversation]");
  await page.open("#/home");
  await page.click("[data-testid=home-action-files]");
  await hashIs(/^#\/files\/browse$/);
  await page.waitForSelector("[data-testid=browse-device]");
  await page.open("#/home");
  await page.click("[data-testid=home-action-add]");
  await page.waitForSelector("[data-testid=invite-create]");
  await page.keyboard.press("Escape");
  await page.waitForSelector("[data-testid=invite-create]", { state: "detached" });
});

await step("home: device buttons, an unavailable one says why, the card opens the drawer", async () => {
  await page.open("#/home");
  const nas = `[data-testid=home-device][data-peer="${NAS.id}"]`;
  await page.click(`${nas} [data-testid=device-act-files]`);
  await hashIs(new RegExp("^#/files/browse/" + NAS.id + "$"));
  await page.waitForSelector("[data-testid=share-card]");
  await page.open("#/home");
  await page.click(`${nas} [data-testid=device-act-send]`);
  await hashIs(new RegExp("^#/files/send\\?to=" + NAS.id));
  await page.open("#/home");
  await page.click(`${nas} [data-testid=device-act-chat]`);
  await hashIs(new RegExp("^#/chat/" + NAS.id + "$"));
  const offline = state.peers.find((p) => !p.online);
  if (offline) {
    const btn = `[data-testid=home-device][data-peer="${offline.id}"] [data-testid=device-act-files]`;
    await page.open("#/home");
    if ((await page.getAttribute(btn, "aria-disabled")) !== "true") throw new Error("files of an offline device should be unavailable");
    if (!/не в сети/.test(await page.getAttribute(btn, "title"))) throw new Error("the tooltip should say why");
    await page.click(btn, { force: true }); // aria-disabled: still clickable, it explains instead of navigating
    await page.waitForSelector("[data-testid=toast]:has-text('не в сети')");
    if (!/#\/home$/.test(await page.evaluate(() => location.hash))) throw new Error("an unavailable button must not navigate");
    const send = `[data-testid=home-device][data-peer="${offline.id}"] [data-testid=device-act-send]`;
    if (!/дождётся/.test(await page.getAttribute(send, "title"))) throw new Error("sending to an offline device should say it waits");
  }
  // the card itself opens the device drawer (on Home, at #/home/<id>); technical details are folded away
  await page.click(`${nas} .hdev__name a`);
  await page.waitForSelector("[data-testid=device-drawer]");
  await hashIs(new RegExp("^#/home/" + NAS.id + "$"));
  const tech = "[data-testid=device-drawer] [data-testid=tech-details]";
  if (await page.$eval(tech, (d) => d.open)) throw new Error("technical details should start folded");
  if (await page.isVisible(`${tech} :text('IPv4')`)) throw new Error("IPv4 visible before unfolding");
  await page.click(`${tech} summary`);
  await page.waitForSelector(`${tech} :text('IPv4')`);
  await page.keyboard.press("Escape");
  await page.waitForSelector("[data-testid=device-drawer]", { state: "detached" });
  await hashIs(/^#\/home$/);
});

await step("home: «Как это работает?» from the top bar and from the status", async () => {
  await page.open("#/home");
  await page.click("[data-testid=help-button]");
  await page.waitForSelector("[data-testid=help-sheet]");
  const n = await page.$$eval("[data-testid=help-sheet] .help__point", (els) => els.length);
  if (n !== 4) throw new Error("the sheet should explain 4 things, has " + n);
  for (const words of ["ключ", "одноразовый код", "напрямую", "подождут"]) {
    if (!(await page.textContent("[data-testid=help-sheet]")).includes(words)) throw new Error("help sheet misses: " + words);
  }
  await page.waitForSelector("[data-testid=help-sheet] .help__dots");
  await page.keyboard.press("Escape");
  await page.waitForSelector("[data-testid=help-sheet]", { state: "detached" });
  await page.waitForFunction(() => document.activeElement && document.activeElement.dataset.testid === "help-button");
  await page.click("[data-testid=home-help]");
  await page.waitForSelector("[data-testid=help-sheet]");
  await page.keyboard.press("Escape");
  // no NAT chip in the top bar any more; the details live in Settings → Сеть
  if (await page.$(".topbar [data-testid=nat-chip]")) throw new Error("NAT chip still in the top bar");
  await page.open("#/settings/network");
  await page.waitForSelector("[data-testid=settings-network] [data-testid=nat-chip][data-difficulty]");
  await page.open("#/devices");
  await page.waitForSelector("[data-testid=self-card] [data-testid=tech-details]");
});

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
  if (!code.startsWith("MESH1-")) throw new Error("bad invite code " + code);
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
    const offer = await srv.hook("/__mock/offer?from=phone&name=" + encodeURIComponent("План ремонта.pdf")); // small: done in a few seconds over the relay
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
  await step("files: a share holding themesh's keys is blocked and refused", async () => {
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
  await step("settings: router port mapping — switch and its four states", async () => {
    await srv.hook("/__mock/portmap?state=mapped");
    await page.open("#/settings/network");
    const sw = "[data-testid=setting-portmap]";
    const pm = "[data-testid=portmap-status]";
    await page.waitForSelector(`${sw}[aria-checked=true]`);
    await page.waitForSelector(`${pm}[data-state=mapped]:has-text('203.0.113.5:41710')`);
    if (!/UPnP/.test(await page.textContent(pm))) throw new Error("protocol missing in the mapped status");
    await page.waitForSelector(".eplist [data-kind=mapped]:has-text('открыт на роутере')");
    const words = { searching: "Ищу роутер", unavailable: "Роутер не ответил", private: "не публичный адрес", mapped: "Порт открыт на роутере" };
    for (const [st, text] of Object.entries(words)) {
      await srv.hook("/__mock/portmap?state=" + st); // arrives as a `self` event
      await page.waitForSelector(`${pm}[data-state=${st}]:has-text('${text}')`);
    }
    const sent = page.waitForRequest((r) => r.url().endsWith("/api/settings") && r.method() === "PUT");
    await page.click(sw);
    const body = JSON.parse((await sent).postData() || "{}");
    if (body.portMap !== false) throw new Error("expected PUT {portMap:false}, got " + JSON.stringify(body));
    await page.waitForSelector(pm, { state: "detached" });
    await page.click(sw);
    await page.waitForSelector(`${pm}[data-state=searching]`);
    await page.waitForSelector(`${pm}[data-state=mapped]`, { timeout: 8000 });
  });

  await step("lan: discovery on the home network — what Settings says, and the notice on Home when the system refuses", async () => {
    await srv.hook("/__mock/lan?state=ok");
    await page.open("#/settings/network");
    const st = "[data-testid=lan-status]";
    await page.waitForSelector(`${st}[data-state=ok]:has-text('192.168.1.23/24')`);
    await page.waitForSelector("[data-testid=setting-lan][aria-checked=true]");
    await srv.hook("/__mock/lan?state=no-network");
    await page.waitForSelector(`${st}[data-state=no-network]:has-text('Нет подключения к Wi')`);
    // a refusal by the system: a notice on Home that names the way out (on a Mac, the permission to switch on)
    await srv.hook("/__mock/lan?state=blocked&os=darwin");
    await page.waitForSelector(`${st}[data-state=blocked]:has-text('Локальная сеть')`);
    await page.open("#/home");
    await page.waitForSelector("[data-testid=home-att-lan]:has-text('macOS не пускает The Mesh в домашнюю сеть')");
    if (!/Конфиденциальность и безопасность → Локальная сеть/.test(await page.textContent("[data-testid=home-att-lan]"))) throw new Error("the notice does not say where to switch it on");
    await srv.hook("/__mock/lan?state=blocked&os=linux");
    await page.waitForSelector("[data-testid=home-att-lan]:has-text('Системе нельзя искать устройства в домашней сети')");
    await srv.hook("/__mock/lan?state=failed");
    await page.waitForSelector("[data-testid=home-att-lan]:has-text('Поиск устройств в домашней сети не работает')");
    await page.waitForSelector("[data-testid=home-att-lan]:has-text('network is unreachable')");
    // ... and it goes away when the discovery works again; an absent Wi-Fi is not a problem to nag about
    await srv.hook("/__mock/lan?state=no-network");
    await page.waitForSelector("[data-testid=home-att-lan]", { state: "detached" });
    await srv.hook("/__mock/lan?state=ok");
  });

  await step("nearby: a device asks to be added — the dialog opens by itself, the digits, whose device, allow or decline", async () => {
    await srv.hook("/__mock/nearby?clear=1");
    await page.open("#/home");
    const waiting = async () => Number(((await page.title()).match(/^\((\d+)\)/) || [0, 0])[1]);
    await page.waitForSelector("[data-testid=home-status]");
    await page.waitForFunction(() => /^\(\d+\)/.test(document.title)); // unread mail, chat and offers are counted in the tab already
    const before = await waiting();
    await srv.hook("/__mock/nearby?request=pixel&os=android");
    const ask = "[data-testid=nearby-ask]";
    await page.waitForSelector(`${ask}:has-text('pixel')`);
    const code = await page.getAttribute("[data-testid=nearby-ask-code] .nearby-code__digits", "data-code");
    if (!/^\d{6}$/.test(code)) throw new Error("the digits: " + code);
    // the person at the new device confirms a moment later: the dialog says so
    await page.waitForSelector("[data-testid=nearby-ask-state][data-confirmed=true]", { timeout: 6000 });
    if ((await waiting()) !== before + 1) throw new Error(`the tab does not count the waiting request: ${before} before, title "${await page.title()}"`);
    // closed without an answer: nothing is lost, the request waits on Home
    await page.keyboard.press("Escape");
    await page.waitForSelector(ask, { state: "detached" });
    await page.waitForSelector("[data-testid=home-att-nearby]:has-text('«pixel» просит добавить его в сеть')");
    await page.waitForSelector(`[data-testid=home-att-nearby]:has-text('${code.slice(0, 3)} ${code.slice(3)}')`);
    await page.click("[data-testid=home-att-nearby-open]");
    await page.waitForSelector(ask);
    // whose device it is: the person of this one by default, and can be changed
    const owner = await page.inputValue("[data-testid=nearby-ask-owner]");
    if (owner !== state.self.owner) throw new Error(`owner offered: ${owner}, this device's: ${state.self.owner}`);
    await page.fill("[data-testid=nearby-ask-owner]", "Мария");
    const sent = expectRequest(page, (r) => /\/api\/nearby\/requests\//.test(r.url()));
    await page.click("[data-testid=nearby-allow]");
    const body = JSON.parse((await sent).postData() || "{}");
    if (body.approve !== true || body.owner !== "Мария") throw new Error("answer body: " + JSON.stringify(body));
    await page.waitForSelector("[data-testid=toast]:has-text('Разрешено: «pixel» появится в списке')");
    await page.waitForSelector("[data-testid=home-att-nearby]", { state: "detached" });
    await page.waitForSelector("[data-testid=home-device][data-name=pixel]", { timeout: 8000 });
    // a second one is declined; a request that is old is gone for good
    await srv.hook("/__mock/nearby?request=intruder&os=windows&confirmed=0");
    await page.waitForSelector(`${ask}:has-text('intruder')`);
    await page.waitForSelector("[data-testid=nearby-ask-state][data-confirmed=false]");
    const no = expectRequest(page, (r) => /\/api\/nearby\/requests\//.test(r.url()));
    await page.click("[data-testid=nearby-deny]");
    if (JSON.parse((await no).postData() || "{}").approve !== false) throw new Error("decline must send approve:false");
    await page.waitForSelector(ask, { state: "detached" });
    await page.waitForSelector("[data-testid=home-att-nearby]", { state: "detached" });
    // two at once: one dialog at a time, the next one right after
    await srv.hook("/__mock/nearby?request=first&os=linux");
    await srv.hook("/__mock/nearby?request=second&os=android");
    await page.waitForSelector(`${ask}:has-text('first')`);
    await page.keyboard.press("Escape");
    await page.waitForSelector(`${ask}:has-text('second')`);
    await page.keyboard.press("Escape");
    await page.waitForSelector(ask, { state: "detached" });
    if ((await page.$$("[data-testid=home-att-nearby]")).length !== 2) throw new Error("both requests should wait on Home");
    await srv.hook("/__mock/nearby?clear=1");
    await page.waitForSelector("[data-testid=home-att-nearby]", { state: "detached" });
  });

  await step("nearby: the switch in Settings, and the hint when adding a device by an invitation", async () => {
    await page.open("#/settings/network");
    const sw = "[data-testid=setting-nearby]";
    await page.waitForSelector(`${sw}[aria-checked=true]`);
    const sent = expectRequest(page, (r) => r.url().endsWith("/api/settings") && r.method() === "PUT");
    await page.click(sw);
    const body = JSON.parse((await sent).postData() || "{}");
    if (body.nearby !== false) throw new Error("expected PUT {nearby:false}, got " + JSON.stringify(body));
    await page.waitForSelector(`${sw}[aria-checked=false]`);
    await page.click(sw);
    await page.waitForSelector(`${sw}[aria-checked=true]`);
    await page.open("#/home?add=1");
    await page.waitForSelector("[data-testid=add-nearby-note]:has-text('Если новое устройство рядом, код не нужен')");
    await page.keyboard.press("Escape");
  });

  await step("global: offline banner and recovery", async () => {
    await page.open("#/devices");
    await srv.hook("/__mock/drop?for=3");
    await page.waitForSelector("[data-testid=offline-banner]", { timeout: 8000 });
    await page.waitForSelector("[data-testid=offline-banner]", { state: "detached", timeout: 20000 });
  });

  const fresh = await mock(["--calm", "--scenario", "empty"]);
  await step("home: getting started — first steps, nothing to send to yet, dismiss for good", async () => {
    const p = await newPage(fresh.url, "start");
    // empty states elsewhere send you to Home's «Добавить устройство» (#/home?add=1)
    await p.open("#/chat");
    await p.click(".chat__list .empty a:has-text('Добавить устройство')");
    await p.waitForSelector("[data-testid=invite-create]");
    if ((await p.evaluate(() => location.hash)) !== "#/home") throw new Error("?add=1 should be dropped from the address");
    await p.keyboard.press("Escape");
    await p.waitForSelector("[data-testid=invite-create]", { state: "detached" });
    await p.waitForSelector("[data-testid=home-status][data-state=alone]");
    const list = "[data-testid=home-start-checklist]";
    await p.waitForSelector(`${list} [data-step=add][data-done=false]`);
    // sending needs somebody to send to: say so and offer to add a device
    await p.click("[data-testid=home-action-send]");
    await p.waitForSelector("[data-testid=toast]:has-text('Сначала добавьте второе устройство')");
    await p.waitForSelector("[data-testid=invite-create]");
    await p.keyboard.press("Escape");
    // a second device joins → step 1 is done, the status turns green, «Отправить» goes straight to it
    await fetch(fresh.url + "/api/invites", { method: "POST", headers: { Authorization: "Bearer dev", "Content-Type": "application/json" }, body: JSON.stringify({ admin: false, owner: "Андрей" }) });
    await fresh.hook("/__mock/join?name=phone");
    await p.waitForSelector(`${list} [data-step=add][data-done=true]`, { timeout: 10000 });
    await p.waitForSelector("[data-testid=home-status][data-state=ok]:has-text('2 из 2')");
    const href = await p.getAttribute("[data-testid=home-action-send]", "href");
    if (!/^#\/files\/send\?to=/.test(href || "")) throw new Error("with one other device «Отправить файл» should go straight to it: " + href);
    // looking at the other device's folders ticks step 2 (remembered in this browser)
    const other = (await fresh.get("/api/state")).peers[0];
    await p.open(`#/files/browse/${other.id}`);
    await p.waitForSelector("[data-testid=share-card], .empty");
    await p.open("#/home");
    await p.waitForSelector(`${list} [data-step=browse][data-done=true]`);
    await p.click("[data-testid=home-start-dismiss]");
    await p.waitForSelector(list, { state: "detached" });
    await p.reload();
    await p.waitForSelector("[data-testid=home-status]");
    if (await p.$(list)) throw new Error("the dismissed checklist came back after a reload");
    await p.context().close();
  });

  await step("mobile: tab bar, drill-down chat and back", async () => {
    const m = await newPage(base, "mobile", { mobile: true });
    // Home on a phone: one column, no sideways scroll, finger-sized buttons
    await m.open();
    await m.waitForSelector("[data-testid=tab-home][aria-current=page]");
    if (await m.$("[data-testid=tab-devices]")) throw new Error("five tabs at most: Devices lives on Home and in «Ещё»");
    if (await m.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1)) throw new Error("Home scrolls sideways at 390px");
    const small = await m.$$eval(".home-act, .dact__btn, .home-status__help, [data-testid=help-button], .home-att__row--link", (els) =>
      els.filter((e) => e.getClientRects().length).map((e) => [e.className, Math.round(e.getBoundingClientRect().width), Math.round(e.getBoundingClientRect().height)]).filter(([, w, h]) => w < 44 || h < 44));
    if (small.length) throw new Error("tap targets under 44px: " + JSON.stringify(small.slice(0, 3)));
    const labels = await m.$$eval(".dact__label", (els) => els.filter((e) => e.getClientRects().length).length);
    if (labels) throw new Error("device buttons should be icons with an accessible name on a phone");
    // the full device list is one tap away in «Ещё»
    await m.click("[data-testid=tab-more]");
    await m.click("[data-testid=more-devices]");
    await m.waitForSelector("[data-testid=page-devices] [data-testid=device-card]");
    if ((await m.getAttribute("[data-testid=tab-home]", "aria-current")) !== "page") throw new Error("the Devices page belongs to the Home tab");
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
    await p.waitForFunction(() => window.__themesh.state.conn === "online" && window.__themesh.state.peers.length > 0);
    await rm.hook("/__mock/removed");
    await p.waitForSelector("[data-testid=removed-notice]:has-text('Дом')", { timeout: 8000 });
    if ((await p.evaluate(() => location.hash)) !== "#/") throw new Error("route not reset to #/");
    if (await p.$("[data-testid=toast]:has-text('removed')")) throw new Error("raw 'removed' notify shown as a toast");
    await p.click("[data-testid=onb-join]");
    await p.fill("[data-testid=onb-code]", "MESH1-AEAWVQFQ-GHIJKLMN-OPQRSTUV-WXYZ2345-67ABCDEF-GHIJKLMN");
    await p.click("[data-testid=onb-submit]");
    await p.waitForSelector("[data-testid=page-home]", { timeout: 15000 });
    if (await p.$("[data-testid=removed-notice]")) throw new Error("notice still shown after joining");
    await p.context().close();
  });

  const auth = await mock(["--calm", "--auth"]);
  await step("auth: expired screen, one-time link, used link, sign out", async () => {
    const p = await newPage(auth.url, "auth");
    await p.open();
    await p.waitForSelector("[data-testid=unauthorized][data-reason=expired]");
    const { url } = await auth.hook("/__mock/login"); // what `themesh url` prints
    await p.goto(auth.url + url);
    await p.waitForSelector("[data-testid=page-home]");
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
