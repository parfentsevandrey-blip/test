import crypto from "node:crypto";
import { group, test, assert, eq, until, open, nav, tid } from "../lib.mjs";

const peerByName = async (dev, name) => (await dev.laptop.api("GET", "/api/state")).peers.find((p) => p.deviceName === name);
const file = (name, data, mimeType) => ({ name, mimeType, buffer: Buffer.from(data) });
const PNG = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNgYGD4DwABBAEAHnOcgAAAAABJRU5ErkJggg==", "base64");

group("chat", () => {
  test("conversations are listed; opening one shows the history in order and clears the badge", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { hash: "chat" });
    await page.waitForFunction(() => document.querySelectorAll('[data-testid="thread"]').length === 2);
    const phone = await peerByName(dev, "phone");
    await page.locator(`[data-testid="thread"][data-peer="${phone.id}"]`).click();
    await tid(page, "conversation").waitFor();
    await page.waitForFunction(() => document.querySelectorAll('[data-testid="bubble"]').length === 3);
    const texts = await page.$$eval('[data-testid="bubble"]', (b) => b.map((x) => [x.dataset.mine, x.innerText.split("\n")[0]]));
    eq(texts.map((t) => t[0]), ["false", "true", "false"], "who said what, oldest first");
    assert(/дачи/.test(texts[0][1]) && /Ужин/.test(texts[2][1]), "message order: " + JSON.stringify(texts));
    await until(async () => (await dev.laptop.api("GET", "/api/chat/threads")).find((t) => t.peer.id === phone.id).unread === 0, 5000, "the unread count to clear");
    eq(page.problems, [], "console / network problems");
  });

  test("a message is delivered to the other device and a reply appears live without reloading", async ({ browser, dev }) => {
    const phone = await peerByName(dev, "phone");
    const lap = (await dev.laptop.api("GET", "/api/state")).self;
    const page = await open(browser, dev.laptop, { hash: `chat/${phone.id}` });
    await tid(page, "conversation").waitFor();
    const mine = "Привет из теста " + crypto.randomBytes(3).toString("hex");
    await tid(page, "chat-input").click();
    await page.keyboard.type(mine);
    await page.keyboard.press("Enter");
    await page.locator('[data-testid="bubble"][data-mine="true"]', { hasText: mine }).waitFor();
    await until(async () => (await dev.phone.api("GET", `/api/chat/${lap.id}`)).messages.some((m) => m.text === mine), 15000, "the phone to receive the message");
    await page.locator('[data-testid="bubble"][data-mine="true"][data-state="delivered"]', { hasText: mine }).waitFor();
    // now the phone answers; the bubble shows up through the live event stream
    const reply = "Ответ с телефона " + crypto.randomBytes(3).toString("hex");
    await dev.phone.api("POST", `/api/chat/${lap.id}`, { text: reply });
    await page.locator('[data-testid="bubble"][data-mine="false"]', { hasText: reply }).waitFor({ timeout: 10000 });
    eq(page.problems, [], "console / network problems");
  });

  test("a message from a device while you are on another page raises a toast and a badge", async ({ browser, dev }) => {
    const lap = (await dev.laptop.api("GET", "/api/state")).self;
    const page = await open(browser, dev.laptop, { hash: "files/send" });
    const text = "Ты дома? " + crypto.randomBytes(2).toString("hex");
    await dev["home-server"].api("POST", `/api/chat/${lap.id}`, { text });
    await page.locator('[data-testid="toast"]', { hasText: text }).waitFor({ timeout: 10000 });
    await page.waitForFunction(() => /\b[1-9]\d*\b/.test(document.querySelector('[data-testid="nav-chat"]').innerText));
    eq(page.problems, [], "console / network problems");
  });

  // Found by a real two-process run: on a fast local network the live «delivered» event can reach the page before
  // the answer to «send» does, and the answer (the message as it was created) used to take the bubble back to «queued».
  test("a message delivered before the page has read the answer to «send» stays delivered", async ({ browser, dev }) => {
    const phone = await peerByName(dev, "phone");
    const lap = (await dev.laptop.api("GET", "/api/state")).self;
    const page = await open(browser, dev.laptop, { hash: `chat/${phone.id}` });
    await tid(page, "conversation").waitFor();
    let release;
    const held = new Promise((r) => (release = r));
    await page.route(new RegExp(`/api/chat/${phone.id}$`), async (route) => {
      if (route.request().method() !== "POST") return route.continue();
      const answer = await route.fetch(); // the real answer, held back until the events have said «delivered»
      await held;
      await route.fulfill({ response: answer });
    });
    const mine = "Быстрая доставка " + crypto.randomBytes(3).toString("hex");
    await tid(page, "chat-input").click();
    await page.keyboard.type(mine);
    await page.keyboard.press("Enter");
    await until(async () => (await dev.phone.api("GET", `/api/chat/${lap.id}`)).messages.some((m) => m.text === mine), 15000, "the phone to receive the message");
    const bubble = page.locator('[data-testid="bubble"][data-mine="true"]', { hasText: mine });
    await page.locator('[data-testid="bubble"][data-mine="true"][data-state="delivered"]', { hasText: mine }).waitFor({ timeout: 15000 }); // from the live events alone
    release();
    await page.waitForTimeout(700); // the stale answer is read now
    eq(await bubble.getAttribute("data-state"), "delivered", "the late answer did not take the message back to queued");
    eq(await bubble.count(), 1, "and it did not appear twice");
    eq(page.problems, [], "console / network problems");
  });

  test("an image can be sent in the chat and shows up as a picture on both sides", async ({ browser, dev }) => {
    const phone = await peerByName(dev, "phone");
    const lap = (await dev.laptop.api("GET", "/api/state")).self;
    const page = await open(browser, dev.laptop, { hash: `chat/${phone.id}` });
    await tid(page, "conversation").waitFor();
    await tid(page, "chat-files").setInputFiles(file("точка.png", PNG, "image/png"));
    await tid(page, "chat-send").waitFor();
    await page.waitForFunction(() => !document.querySelector('[data-testid="chat-send"]').disabled);
    await tid(page, "chat-send").click();
    const bubble = page.locator('[data-testid="bubble"][data-mine="true"] img');
    await bubble.waitFor();
    await page.waitForFunction(() => { const i = document.querySelector('[data-testid="bubble"][data-mine="true"] img'); return i && i.complete && i.naturalWidth === 1; });
    const msg = await until(async () => (await dev.phone.api("GET", `/api/chat/${lap.id}`)).messages.find((m) => (m.attachments || []).some((a) => a.name === "точка.png")), 15000, "the phone to receive the picture");
    const idx = msg.attachments.findIndex((a) => a.name === "точка.png");
    const bytes = await until(async () => {
      const r = await dev.phone.api("GET", `/api/chat/messages/${msg.id}/attachments/${idx}`, undefined, { raw: true });
      return r.status === 200 ? Buffer.from(await r.arrayBuffer()) : null;
    }, 15000, "the phone to fetch the picture");
    eq(bytes.equals(PNG), true, "picture bytes");
    eq(page.problems, [], "console / network problems");
  });

  test("a new conversation can be started with any device", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { hash: "chat" });
    await tid(page, "chat-new").click();
    await page.getByRole("menuitem").filter({ hasText: "nas" }).click();
    await tid(page, "conversation").waitFor();
    const nas = await peerByName(dev, "nas");
    eq(await tid(page, "conversation").getAttribute("data-peer"), nas.id, "conversation with the NAS");
    eq(page.problems, [], "console / network problems");
  });
});
