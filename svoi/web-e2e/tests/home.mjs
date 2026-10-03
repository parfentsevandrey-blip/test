import crypto from "node:crypto";
import fs from "node:fs";
import { group, test, assert, eq, until, open, nav, tid, startNode } from "../lib.mjs";

// Home («Главная»): the landing page for people who don't care how the mesh works.
const peerByName = async (dev, name) => (await dev.laptop.api("GET", "/api/state")).peers.find((p) => p.deviceName === name);
const sha = (b) => crypto.createHash("sha256").update(b).digest("hex");
const hashIs = (page, re) => page.waitForFunction((src) => new RegExp(src).test(location.hash), re.source);
// words that have no place on the landing page (they live in Settings → Сеть and «Технические данные»)
const JARGON = [/\bNAT\b/, /ретранслят/i, /IPv[46]/, /\d\s?мс(?![а-яё])/i, /\bSTUN\b/, /\bID\b/];

group("home", () => {
  test("the landing page says in words how the network is, offers four actions and lists every device", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop);
    await tid(page, "page-home").waitFor();
    eq(await tid(page, "nav-home").getAttribute("aria-current"), "page", "Home is the first page");
    const status = tid(page, "home-status");
    eq(await status.getAttribute("data-state"), "ok", "all four demo devices are online");
    assert(/Всё в порядке — 4 из 4 устройств на связи/.test(await status.innerText()), "the status sentence: " + (await status.innerText()));
    for (const a of ["send", "chat", "files", "add"]) await tid(page, "home-action-" + a).waitFor();
    await page.waitForFunction(() => document.querySelectorAll('[data-testid="home-device"]').length === 3);
    // the phone sits behind a carrier NAT: on Home that is just «через home-server»
    const phone = await peerByName(dev, "phone");
    const line = page.locator(`[data-testid="home-device"][data-peer="${phone.id}"] [data-testid="conn-line"]`);
    eq(await line.getAttribute("data-state"), "relay", "the phone's dot");
    assert(/На связи · через home-server/.test(await line.innerText()), "the phone's line: " + (await line.innerText()));
    // what needs attention: the seeded offer, unread mail and chat — and no second copy of the offer in a banner
    await tid(page, "home-attention").waitFor();
    await tid(page, "home-offer").first().waitFor();
    await tid(page, "home-att-mail").waitFor();
    await tid(page, "home-att-chat").waitFor();
    eq(await tid(page, "offers-banner").count(), 0, "no offers banner on Home");
    eq(await tid(page, "home-start-checklist").count(), 0, "no first steps in a network that is already in use");
    const text = await tid(page, "page-home").innerText();
    for (const re of JARGON) assert(!re.test(text), `jargon on Home: ${re}`);
    eq(page.problems, [], "console / network problems");
  });

  test("the action cards and the device buttons open the real flows", async ({ browser, dev }) => {
    const nas = await peerByName(dev, "nas");
    const phone = await peerByName(dev, "phone");
    const page = await open(browser, dev.laptop, { hash: "home" });
    // three other devices: «Кому отправить файл?»
    await tid(page, "home-action-send").click();
    await page.locator('[data-testid="device-pick"] .modal__title', { hasText: "Кому отправить файл?" }).waitFor();
    await page.locator(`[data-testid="device-pick-item"][data-peer="${nas.id}"]`).click();
    await hashIs(page, new RegExp(`^#/files/send\\?to=${nas.id}$`));
    await page.locator(`[data-testid="device-chip"][data-id="${nas.id}"][aria-pressed="true"]`).waitFor();
    await nav(page, dev.laptop, "home");
    await tid(page, "home-action-chat").click();
    await page.locator(`[data-testid="device-pick-item"][data-peer="${phone.id}"]`).click();
    await hashIs(page, new RegExp(`^#/chat/${phone.id}$`));
    await tid(page, "conversation").waitFor();
    await nav(page, dev.laptop, "home");
    await tid(page, "home-action-files").click();
    await hashIs(page, /^#\/files\/browse$/);
    await tid(page, "browse-device").first().waitFor();
    await nav(page, dev.laptop, "home");
    await tid(page, "home-action-add").click();
    await tid(page, "invite-create").waitFor();
    await page.keyboard.press("Escape");
    await tid(page, "invite-create").waitFor({ state: "detached" });
    // the buttons on a device card
    const card = page.locator(`[data-testid="home-device"][data-peer="${nas.id}"]`);
    await card.getByTestId("device-act-files").click();
    await hashIs(page, new RegExp(`^#/files/browse/${nas.id}$`));
    await tid(page, "share-card").first().waitFor(); // the NAS's real shares
    await nav(page, dev.laptop, "home");
    await card.getByTestId("device-act-send").click();
    await hashIs(page, new RegExp(`^#/files/send\\?to=${nas.id}$`));
    await nav(page, dev.laptop, "home");
    await card.getByTestId("device-act-chat").click();
    await hashIs(page, new RegExp(`^#/chat/${nas.id}$`));
    // the card itself opens the device drawer; addresses are folded under «Технические данные»
    await nav(page, dev.laptop, "home");
    await card.locator(".hdev__name a").click();
    await tid(page, "device-drawer").waitFor();
    await hashIs(page, new RegExp(`^#/home/${nas.id}$`));
    const tech = tid(page, "device-drawer").getByTestId("tech-details");
    eq(await tech.evaluate((d) => d.open), false, "technical details start folded");
    await tech.locator("summary").click();
    assert((await tech.innerText()).includes(nas.ip4), "the unfolded details show the NAS's address " + nas.ip4);
    await page.keyboard.press("Escape");
    await hashIs(page, /^#\/home$/);
    eq(page.problems, [], "console / network problems");
  });

  test("Принять on an offer right on Home really receives the file; Отклонить tells the sender", async ({ browser, dev }) => {
    const lap = (await dev.laptop.api("GET", "/api/state")).self;
    const page = await open(browser, dev.laptop, { hash: "home" });
    await tid(page, "home-attention").waitFor();
    // the phone belongs to Анна, the laptop to Андрей: her file is an offer here
    const data = crypto.randomBytes(300 * 1024);
    const sent = await dev.phone.api("POST", `/api/transfers?to=${lap.id}&name=${encodeURIComponent("с дачи.bin")}&mime=application/octet-stream`, new Uint8Array(data), { headers: { "Content-Type": "application/octet-stream" } });
    const sentId = sent.transfers[0].id;
    const row = page.locator('[data-testid="home-offer"]', { hasText: "с дачи.bin" });
    await row.waitFor({ timeout: 15000 });
    await row.getByTestId("offer-accept").click();
    await row.waitFor({ state: "detached", timeout: 10000 });
    await page.locator('[data-testid="toast"]', { hasText: "Принимаем" }).waitFor();
    const done = await until(async () => (await dev.laptop.api("GET", "/api/transfers")).find((t) => t.name === "с дачи.bin" && t.dir === "in" && t.state === "done"), 30000, "the file to arrive");
    eq(sha(fs.readFileSync(done.path)), sha(data), "the bytes on the laptop");
    await until(async () => (await dev.phone.api("GET", "/api/transfers")).find((t) => t.id === sentId && t.state === "done"), 15000, "the phone to see it delivered");
    // and a refusal reaches the sender
    const spam = await dev.phone.api("POST", `/api/transfers?to=${lap.id}&name=spam.txt&mime=text/plain`, new TextEncoder().encode("spam"), { headers: { "Content-Type": "application/octet-stream" } });
    const spamRow = page.locator('[data-testid="home-offer"]', { hasText: "spam.txt" });
    await spamRow.getByTestId("offer-decline").click();
    await spamRow.waitFor({ state: "detached", timeout: 10000 });
    await until(async () => (await dev.phone.api("GET", "/api/transfers")).find((t) => t.id === spam.transfers[0].id && t.state === "declined"), 10000, "the sender to see the refusal");
    eq(page.problems, [], "console / network problems");
  });

  test("«Как это работает?» explains the basics from the top bar and from the status", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { hash: "home" });
    await tid(page, "help-button").click();
    const sheet = tid(page, "help-sheet");
    await sheet.waitFor();
    eq(await sheet.locator(".help__point").count(), 4, "four plain points");
    const text = await sheet.innerText();
    for (const w of ["свой ключ", "одноразовый код", "напрямую", "подождут"]) assert(text.includes(w), "the sheet explains: " + w);
    await page.keyboard.press("Escape");
    await sheet.waitFor({ state: "detached" });
    await page.waitForFunction(() => document.activeElement?.dataset.testid === "help-button");
    await tid(page, "home-help").click();
    await sheet.waitFor();
    await page.keyboard.press("Escape");
    // the NAT type left the top bar for Settings → Сеть
    eq(await page.locator('.topbar [data-testid="nat-chip"]').count(), 0, "no NAT chip in the top bar");
    eq(page.problems, [], "console / network problems");
  });

  // Last in the group: the NAS leaves the network for good.
  test("when a device goes offline the status names it and its card says so", async ({ browser, dev }) => {
    const nas = await peerByName(dev, "nas");
    const page = await open(browser, dev.laptop, { hash: "home" });
    eq(await tid(page, "home-status").getAttribute("data-state"), "ok", "everybody online first");
    await dev.nas.api("POST", "/api/mesh/leave", {}); // stops its network at once: the laptop loses it
    const status = page.locator('[data-testid="home-status"][data-state="partial"]');
    await status.waitFor({ timeout: 30000 });
    const text = await status.innerText();
    assert(/Часть устройств не в сети: nas/.test(text) && /3 из 4 устройств на связи/.test(text), "the status names the device: " + text);
    const card = page.locator(`[data-testid="home-device"][data-peer="${nas.id}"]`);
    eq(await card.getAttribute("data-online"), "false", "the card shows it offline");
    assert(/Не в сети/.test(await card.getByTestId("conn-line").innerText()), "the card's line says offline");
    // its folders can't be opened now — the button says why instead of leading to an error
    const files = card.getByTestId("device-act-files");
    eq(await files.getAttribute("aria-disabled"), "true", "files unavailable");
    assert(/не в сети/.test(await files.getAttribute("title")), "the tooltip says why");
    await files.click({ force: true });
    await page.locator('[data-testid="toast"]', { hasText: "не в сети" }).waitFor();
    await hashIs(page, /^#\/home$/);
    // sending still works: it waits for the device
    assert(/дождётся/.test(await card.getByTestId("device-act-send").getAttribute("title")), "sending waits for it");
    eq(page.problems, [], "console / network problems");
  });
});

group("home of a device alone in its network", { noDemo: true }, () => {
  test("the status says what to do, the first steps guide, and hiding them is remembered", async ({ browser }) => {
    const node = await startNode({ name: "solo", init: true, mesh: "Одна", owner: "Вера" });
    const page = await open(browser, node);
    await tid(page, "page-home").waitFor();
    const status = page.locator('[data-testid="home-status"][data-state="alone"]');
    await status.waitFor();
    assert(/Вы пока одни в сети — добавьте второе устройство/.test(await status.innerText()), "the alone status");
    const list = tid(page, "home-start-checklist");
    await list.locator('[data-step="add"][data-done="false"]').waitFor();
    // nobody to send to yet: the card says so and offers to add a device
    await tid(page, "home-action-send").click();
    await page.locator('[data-testid="toast"]', { hasText: "Сначала добавьте второе устройство" }).waitFor();
    await tid(page, "invite-create").waitFor();
    await page.keyboard.press("Escape");
    await tid(page, "invite-create").waitFor({ state: "detached" });
    // step 1's button opens the same dialog
    await tid(page, "home-start-add").click();
    await tid(page, "invite-create").waitFor();
    await page.keyboard.press("Escape");
    // hide the list: it stays hidden after a reload
    await tid(page, "home-start-dismiss").click();
    await list.waitFor({ state: "detached" });
    await page.reload();
    await tid(page, "home-status").waitFor();
    eq(await tid(page, "home-start-checklist").count(), 0, "the hidden checklist stays hidden");
    eq(page.problems, [], "console / network problems");
  });
});
