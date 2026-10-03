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
  test("every main screen fits a 390px wide screen and the bottom tab bar navigates", async ({ browser, dev }) => {
    const nas = await peerByName(dev, "nas");
    const page = await open(browser, dev.laptop, { ...M });
    await tid(page, "tab-home").waitFor();
    for (const hash of ["home", "devices", "files/send", "files/browse", `files/browse/${nas.id}`, "files/shares", "mail/inbox", "chat", "services", "settings", "settings/network", "more"]) {
      await nav(page, dev.laptop, hash);
      await page.waitForTimeout(500);
      const o = await overflow(page);
      assert(!o, `horizontal overflow on #/${hash}: ${JSON.stringify(o)}`);
      assert(await tid(page, "tab-home").isVisible(), `the tab bar is visible on #/${hash}`);
    }
    // five tabs: Home took the place of Devices, which is on Home and in «Ещё»
    eq(await page.$$eval('[data-testid^="tab-"]', (els) => els.map((e) => e.dataset.testid)), ["tab-home", "tab-files", "tab-mail", "tab-chat", "tab-more"], "the tab bar");
    // tab bar navigation works with taps
    await tid(page, "tab-chat").tap();
    await page.waitForFunction(() => location.hash.startsWith("#/chat"));
    await tid(page, "tab-mail").tap();
    await page.waitForFunction(() => location.hash.startsWith("#/mail"));
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

  test("tap targets of the tab bar are comfortably large", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { ...M });
    const sizes = await page.$$eval('[data-testid^="tab-"]', (els) => els.map((e) => { const r = e.getBoundingClientRect(); return [e.dataset.testid, Math.round(r.width), Math.round(r.height)]; }));
    assert(sizes.length >= 4, "tabs: " + JSON.stringify(sizes));
    for (const [id, w, h] of sizes) assert(w >= 44 && h >= 44, `${id} is ${w}x${h}px, WCAG wants ≥ 44px`);
    eq(page.problems, [], "console / network problems");
  });
});
