import crypto from "node:crypto";
import { group, test, assert, eq, until, open, nav, tid } from "../lib.mjs";

const peerByName = async (dev, name) => (await dev.laptop.api("GET", "/api/state")).peers.find((p) => p.deviceName === name);
const file = (name, data, mimeType = "application/octet-stream") => ({ name, mimeType, buffer: Buffer.from(data) });

group("mail", () => {
  test("the inbox lists the messages; reading one marks it read and updates the badge", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { hash: "mail/inbox" });
    await page.waitForFunction(() => document.querySelectorAll('[data-testid="mail-item"]').length === 3);
    eq(await page.$$eval('[data-testid="mail-item"]', (e) => e.map((x) => x.dataset.unread)), ["true", "true", "true"], "all unread");
    eq((await dev.laptop.api("GET", "/api/state")).counters.mail, 3, "unread counter on the backend");
    await page.locator('[data-testid="mail-item"]', { hasText: "Продление домена" }).click();
    const reader = tid(page, "mail-reader");
    await reader.waitFor();
    assert(/home\.example оплачен до 2027/.test(await reader.innerText()), "the body is shown");
    assert(/home-server/.test(await reader.innerText()), "the sender is shown");
    await until(async () => (await dev.laptop.api("GET", "/api/state")).counters.mail === 2, 5000, "the unread counter to drop");
    await page.locator('[data-testid="mail-item"][data-unread="false"]').first().waitFor();
    eq(page.problems, [], "console / network problems");
  });

  test("an attachment stored on another device is fetched and can be previewed and downloaded", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { hash: "mail/inbox" });
    await page.locator('[data-testid="mail-item"]', { hasText: "Отчёт резервного копирования" }).click();
    const reader = tid(page, "mail-reader");
    await reader.waitFor();
    await reader.getByText("backup-report.txt").first().waitFor();
    // the blob is pulled from the NAS on demand; wait until it is ready, then download it through the UI
    const link = reader.locator('a[download="backup-report.txt"]');
    await link.waitFor({ timeout: 15000 });
    const [dl] = await Promise.all([page.waitForEvent("download"), link.click()]);
    const text = (await import("node:fs")).readFileSync(await dl.path(), "utf8");
    assert(/Резервное копирование завершено/.test(text) && /1 284 файла/.test(text), "downloaded attachment content: " + text.slice(0, 80));
    // the preview opens the text in place
    await reader.locator('button[aria-label*="backup-report.txt"]').first().click();
    await page.locator(".pv__text pre").waitFor();
    assert(/Ошибок: 0/.test(await page.locator(".pv__text pre").innerText()), "text preview of the attachment");
    eq(page.problems, [], "console / network problems");
  });

  test("replying sends a signed message that the other device really receives", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { hash: "mail/inbox" });
    await page.locator('[data-testid="mail-item"]', { hasText: "Список покупок" }).click();
    await tid(page, "mail-reply").click();
    await tid(page, "compose-body").waitFor();
    // the original message is fetched asynchronously after the form appears
    await page.waitForFunction(() => /Re: Список покупок/i.test(document.querySelector('[data-testid="compose-subject"]').value), null, { timeout: 8000 });
    const answer = "Куплю всё по списку, " + crypto.randomBytes(3).toString("hex");
    await page.waitForFunction(() => /пишет/.test(document.querySelector('[data-testid="compose-body"]').value)); // the quoted original is in
    await tid(page, "compose-body").click();
    await page.keyboard.press("Control+Home");
    await page.keyboard.type(answer + "\n\n");
    await tid(page, "compose-send").click();
    await tid(page, "compose-send").waitFor({ state: "detached" });
    // the phone got it; the signature was verified there (the message carries our identity)
    const got = await until(async () => {
      const l = await dev.phone.api("GET", "/api/mail?folder=inbox&q=" + encodeURIComponent(answer));
      return l.items[0];
    }, 15000, "the phone to receive the reply");
    const full = await dev.phone.api("GET", `/api/mail/${got.id}`);
    assert(full.body.includes(answer), "body arrived intact");
    eq(full.from.name, "laptop", "sender identity");
    // the sent folder shows the delivery state
    await nav(page, dev.laptop, "mail/sent");
    await page.locator('[data-testid="mail-item"]', { hasText: "Список покупок" }).click();
    await page.locator('[data-testid="mail-recipient"][data-state="delivered"]').waitFor();
    eq(page.problems, [], "console / network problems");
  });

  test("a new message with an attachment goes to two devices; search, trash and delete work", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { hash: "mail/inbox" });
    await tid(page, "mail-compose").first().click();
    await tid(page, "compose-subject").waitFor();
    await page.locator('[data-testid="device-chip"][data-name="nas"]').click();
    await page.locator('[data-testid="device-chip"][data-name="home-server"]').click();
    await tid(page, "compose-subject").fill("Квартальный отчёт");
    await tid(page, "compose-body").fill("Во вложении цифры за квартал.");
    const payload = crypto.randomBytes(200 * 1024);
    await tid(page, "compose-files").setInputFiles(file("цифры.bin", payload));
    await page.locator('[data-testid="attachment"][data-status="ready"], [data-testid="attachment"][data-status="done"]').first().waitFor();
    await tid(page, "compose-send").click();
    await tid(page, "compose-send").waitFor({ state: "detached" });
    for (const name of ["nas", "home-server"]) {
      const msg = await until(async () => (await dev[name].api("GET", "/api/mail?folder=inbox&q=" + encodeURIComponent("Квартальный"))).items[0], 15000, `${name} to receive the message`);
      const full = await dev[name].api("GET", `/api/mail/${msg.id}`);
      eq(full.attachments.length, 1, `${name}: attachments`);
      // the attachment is fetched from the laptop and verified by its hash
      const bytes = await until(async () => {
        const r = await dev[name].api("GET", `/api/mail/${msg.id}/attachments/0`, undefined, { raw: true });
        return r.status === 200 ? Buffer.from(await r.arrayBuffer()) : null;
      }, 20000, `${name} to fetch the attachment`);
      eq(crypto.createHash("sha256").update(bytes).digest("hex"), crypto.createHash("sha256").update(payload).digest("hex"), `${name}: attachment bytes`);
    }
    // search finds it in the sent folder
    await nav(page, dev.laptop, "mail/sent");
    await tid(page, "mail-search").fill("Квартальный");
    await page.waitForFunction(() => document.querySelectorAll('[data-testid="mail-item"]').length === 1);
    // trash, then delete for good
    await page.locator('[data-testid="mail-item"]').first().click();
    await tid(page, "mail-trash").click();
    await page.waitForFunction(() => document.querySelectorAll('[data-testid="mail-item"]').length === 0);
    await nav(page, dev.laptop, "mail/trash");
    await page.locator('[data-testid="mail-item"]', { hasText: "Квартальный" }).click();
    await page.getByRole("button", { name: /навсегда/i }).click(); // in the trash: delete forever, after a confirmation
    await tid(page, "confirm-ok").click();
    await until(async () => (await dev.laptop.api("GET", "/api/mail?folder=trash")).total === 0, 5000, "the message to be deleted");
    eq(page.problems, [], "console / network problems");
  });
});
