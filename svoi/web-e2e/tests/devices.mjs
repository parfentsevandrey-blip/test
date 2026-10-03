import { group, test, assert, eq, until, open, nav, tid, api } from "../lib.mjs";

group("devices", () => {
  test("overview shows this device, the topology and every other device", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { hash: "devices" });
    await tid(page, "self-card").waitFor();
    await tid(page, "topology").waitFor();
    await page.waitForFunction(() => document.querySelectorAll('[data-testid="device-card"]').length === 3);
    const cards = await page.$$eval('[data-testid="device-card"]', (els) => els.map((e) => ({ name: e.dataset.name, online: e.dataset.online })));
    eq(cards.map((c) => c.name).sort(), ["home-server", "nas", "phone"], "device cards");
    assert(cards.every((c) => c.online === "true"), "all devices should be online");
    eq(await tid(page, "topo-node").count(), 3, "topology nodes (peers)");
    // the phone is behind a carrier-grade (symmetric) NAT: its traffic is relayed
    const phone = page.locator('[data-testid="device-card"][data-name="phone"]');
    assert(/home-server/.test(await phone.innerText()), "the phone's path should mention the relay (home-server)");
    // counters in the navigation: 3 mails, 3+ chats, 1 offer
    await page.waitForFunction(() => /\b3\b/.test(document.querySelector('[data-testid="nav-mail"]').innerText));
    eq(page.problems, [], "console / network problems");
  });

  test("a device can be pinged from its drawer", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { hash: "devices" });
    await page.locator('[data-testid="device-card"][data-name="nas"] a.stretched').click();
    await tid(page, "device-drawer").waitFor();
    await tid(page, "device-ping").click();
    await page.waitForFunction(() => /\d/.test(document.querySelector('[data-testid="device-ping-result"]').innerText), null, { timeout: 8000 });
    eq(page.problems, [], "console / network problems");
  });

  test("an invitation with a QR code can be created, is listed and can be cancelled", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { hash: "devices" });
    await tid(page, "add-device").click();
    await tid(page, "invite-create").click();
    await tid(page, "invite-qr").locator("img").waitFor();
    const code = (await tid(page, "invite-code").innerText()).trim();
    assert(/^SVOI1-[A-Z0-9-]+$/.test(code), "invitation code format: " + code);
    const src = await tid(page, "invite-qr").locator("img").getAttribute("src");
    assert(/^(data:image\/svg|blob:|.*\/api\/)/.test(src), "QR image source: " + src.slice(0, 40));
    await tid(page, "invite-waiting").waitFor();
    // the backend knows about it
    const invites = await dev.laptop.api("GET", "/api/invites");
    eq(invites.length, 1, "pending invitations");
    eq(invites[0].code, code, "the code shown is the backend's");
    await page.keyboard.press("Escape");
    // listed on the devices page; cancel it
    await tid(page, "invite-row").waitFor();
    await page.locator('[data-testid="invite-row"] button[aria-label]').last().click();
    await tid(page, "confirm-ok").click();
    await until(async () => (await dev.laptop.api("GET", "/api/invites")).length === 0, 5000, "the invitation to be cancelled");
    eq(page.problems, [], "console / network problems");
  });

  test("a device that was renamed locally shows its alias everywhere", async ({ browser, dev }) => {
    const nas = (await dev.laptop.api("GET", "/api/state")).peers.find((p) => p.deviceName === "nas");
    await dev.laptop.api("POST", `/api/peers/${nas.id}/alias`, { alias: "Домашний NAS" });
    const page = await open(browser, dev.laptop, { hash: "devices" });
    await page.waitForFunction(() => [...document.querySelectorAll('[data-testid="device-card"]')].some((e) => /Домашний NAS/.test(e.innerText)));
    await dev.laptop.api("POST", `/api/peers/${nas.id}/alias`, { alias: "" });
    await page.waitForFunction(() => ![...document.querySelectorAll('[data-testid="device-card"]')].some((e) => /Домашний NAS/.test(e.innerText)));
    eq(page.problems, [], "console / network problems");
  });
});
