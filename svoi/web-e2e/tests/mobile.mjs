import { group, test, assert, eq, until, open, nav, tid } from "../lib.mjs";

const peerByName = async (dev, name) => (await dev.laptop.api("GET", "/api/state")).peers.find((p) => p.deviceName === name);
const M = { w: 390, h: 844, mobile: true };

// The widest offender, to make an overflow failure readable.
const overflow = (page) =>
  page.evaluate(() => {
    const vw = document.documentElement.clientWidth;
    if (document.documentElement.scrollWidth <= vw + 1) return null;
    let worst = null;
    for (const el of document.querySelectorAll("body *")) {
      const r = el.getBoundingClientRect();
      if (r.width > 0 && r.right > vw + 1 && (!worst || r.right > worst.right)) worst = { tag: el.tagName, cls: String(el.className).slice(0, 60), right: Math.round(r.right), vw };
    }
    return worst || { vw, scrollWidth: document.documentElement.scrollWidth };
  });

group("phone-sized screen", () => {
  test("every place fits a 390px wide screen; the bar on top names it and leads up; the overview leads everywhere", async ({ browser, dev }) => {
    const nas = await peerByName(dev, "nas");
    const page = await open(browser, dev.laptop, { ...M });
    await tid(page, "page-home").waitFor();
    eq(await page.locator('[data-testid^="tab-"]').count(), 0, "no tab bar: the overview is the way to everything");
    for (const hash of ["home", "devices", "nearby", "files/send", "files/browse", `files/browse/${nas.id}`, "files/shares", "mail/inbox", "chat", "services", "settings", "settings/network"]) {
      await nav(page, dev.laptop, hash);
      await page.waitForTimeout(500);
      const o = await overflow(page);
      assert(!o, `horizontal overflow on #/${hash}: ${JSON.stringify(o)}`);
      assert(await page.locator(".fm-bar").isVisible(), `the bar is on top on #/${hash}`);
      const here = (await page.locator(".fm-title__here").innerText()).trim();
      assert(here.length > 0, `the bar names the place on #/${hash}`);
      if (hash !== "home") assert(await tid(page, "fm-up").isVisible(), `a way up on #/${hash}`);
    }
    eq((await page.locator(".fm-title__here").innerText()).trim(), "Сеть", "the settings' network section is named");
    // the phone's old «Ещё» leads to the overview
    await nav(page, dev.laptop, "more");
    await tid(page, "page-home").waitFor();
    // from the overview everything is a tap away, and the way up comes back
    await tid(page, "folder-chat").tap();
    await page.waitForFunction(() => location.hash.startsWith("#/chat"));
    await tid(page, "fm-up").tap();
    await page.waitForFunction(() => location.hash === "#/home");
    await tid(page, "folder-mail").tap();
    await page.waitForFunction(() => location.hash.startsWith("#/mail"));
    await tid(page, "fm-up").tap();
    await page.locator(`[data-testid="home-device"][data-peer="${nas.id}"] [data-testid="device-act-files"]`).tap();
    await tid(page, "drive-head").waitFor();
    eq((await page.locator(".fm-title__here").innerText()).trim(), "nas", "the drive is named");
    await tid(page, "share-card").first().tap();
    await tid(page, "file-row").first().waitFor();
    eq((await page.locator(".fm-title__up").innerText()).trim(), "nas", "a folder of the drive: the way up is the drive");
    eq(page.problems, [], "console / network problems");
  });

  test("the device list, a conversation and a mail can be used with one thumb", async ({ browser, dev }) => {
    const phone = await peerByName(dev, "phone");
    const page = await open(browser, dev.laptop, { ...M, hash: "chat" });
    // list → conversation → back
    await page.locator(`[data-testid="thread"][data-peer="${phone.id}"]`).tap();
    await tid(page, "conversation").waitFor();
    assert(!(await page.locator('[data-testid="thread"]').first().isVisible()), "on a narrow screen the conversation replaces the list");
    const text = "с телефона-тест";
    await tid(page, "chat-input").tap();
    await page.keyboard.type(text);
    await tid(page, "chat-send").tap();
    await page.locator('[data-testid="bubble"][data-mine="true"]', { hasText: text }).waitFor();
    await page.goBack();
    await page.locator('[data-testid="thread"]').first().waitFor();
    // mail: list → reader → back
    await nav(page, dev.laptop, "mail/inbox");
    await page.locator('[data-testid="mail-item"]').first().tap();
    await tid(page, "mail-reader").waitFor();
    const o = await overflow(page);
    assert(!o, "the reader fits the screen: " + JSON.stringify(o));
    await page.getByRole("button", { name: /Назад/ }).first().tap();
    await page.locator('[data-testid="mail-item"]').first().waitFor();
    eq(page.problems, [], "console / network problems");
  });

  test("tap targets of the overview and of the bar are comfortably large", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { ...M });
    await tid(page, "page-home").waitFor();
    const sizes = await page.$$eval('.fm-folder, .dact__btn, [data-testid="fm-settings"], [data-testid="help-button"], [data-testid="nearby-connect"]', (els) =>
      els.filter((e) => e.getClientRects().length).map((e) => { const r = e.getBoundingClientRect(); return [e.dataset.testid || e.className, Math.round(r.width), Math.round(r.height)]; }));
    assert(sizes.length >= 8, "targets: " + JSON.stringify(sizes));
    for (const [id, w, h] of sizes) assert(w >= 40 && h >= 40, `${id} is ${w}x${h}px, too small for a finger`);
    eq(page.problems, [], "console / network problems");
  });
});
