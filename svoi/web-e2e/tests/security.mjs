import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { group, test, assert, eq, until, open, tid } from "../lib.mjs";

// What the hardening of the backend looks like from the browser.

group("shares that would expose the device's keys", () => {
  test("a folder holding the device's own keys cannot be shared, and the dialog says why", async ({ browser, demo, dev }) => {
    const dataDir = path.join(demo.dir, "laptop", "data");
    assert(fs.existsSync(path.join(dataDir, "device.key")), "the demo's data dir holds the device key: " + dataDir);
    const page = await open(browser, dev.laptop, { hash: "files/shares" });
    for (const target of [dataDir, path.dirname(dataDir), path.join(dataDir, "blobs")]) {
      await tid(page, "share-add").click();
      await tid(page, "share-path").fill(target);
      await tid(page, "share-name").fill("Ключи");
      await tid(page, "share-save").click();
      await tid(page, "share-error").waitFor();
      assert(/ключ/i.test(await tid(page, "share-error").innerText()), `the dialog explains the refusal for ${target}`);
      await page.keyboard.press("Escape");
    }
    eq((await dev.laptop.api("GET", "/api/shares")).length, 0, "nothing was saved");
    eq(page.problems.filter((p) => !/400/.test(p)), [], "console / network problems");
  });

  test("a share that has become a way to the keys is marked and not served", async ({ browser, demo, dev }) => {
    const dataDir = path.join(demo.dir, "laptop", "data");
    const lap = (await dev.laptop.api("GET", "/api/state")).self;
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), "themesh-e2e-swap-"));
    fs.writeFileSync(path.join(dir, "innocent.txt"), "just a file");
    const sh = await dev.laptop.api("POST", "/api/shares", { name: "Невинная", path: dir, mode: "ro", allow: ["*"] });
    const listing = () => dev.nas.api("GET", `/api/peers/${lap.id}/fs?share=${sh.id}&path=/`, undefined, { raw: true });
    eq((await listing()).status, 200, "the share works at first");
    // later the folder is swapped for a link to the data directory
    fs.rmSync(dir, { recursive: true });
    fs.symlinkSync(dataDir, dir);
    try {
      await until(async () => (await dev.laptop.api("GET", "/api/shares")).find((s) => s.id === sh.id)?.blocked === true, 5000, "the backend to mark the share as blocked");
      const status = (await listing()).status;
      assert(status !== 200, "a blocked share must not be listed to other devices, got " + status);
      const page = await open(browser, dev.laptop, { hash: "files/shares" });
      const row = page.locator(`[data-testid="share-row"][data-id="${sh.id}"]`);
      await row.waitFor();
      eq(await row.getAttribute("data-blocked"), "true", "the row is flagged");
      await row.locator('[data-testid="share-blocked"]').waitFor();
      eq(await row.locator('button[aria-label*="Открыть"], a[aria-label*="Открыть"]').count(), 0, "no way to browse a blocked share from here");
      eq(page.problems.filter((p) => !/403|404/.test(p)), [], "console / network problems");
    } finally {
      fs.rmSync(dir, { force: true });
      await dev.laptop.api("DELETE", `/api/shares/${sh.id}`).catch(() => {});
    }
  });
});

group("the inviter says whose device it is", () => {
  test("the owner typed in the dialog goes into the invitation and into the new device", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { hash: "devices" });
    await tid(page, "add-device").click();
    eq(await tid(page, "invite-owner").inputValue(), "Андрей", "prefilled with this device's owner");
    await tid(page, "invite-owner").fill("Мама");
    await tid(page, "invite-create").click();
    await tid(page, "invite-qr").locator("img").waitFor();
    const [inv] = await dev.laptop.api("GET", "/api/invites");
    eq(inv.owner, "Мама", "the backend recorded the owner chosen in the dialog");
    assert((await tid(page, "invite-for").innerText()).includes("Мама"), "the dialog says whom the invitation is for");
    await page.keyboard.press("Escape");
    const row = tid(page, "invite-row");
    await row.waitFor();
    eq(await row.getAttribute("data-owner"), "Мама", "listed with its owner");
    await dev.laptop.api("DELETE", `/api/invites/${inv.id}`);
  });
});

group("large attachments need the user's consent", () => {
  test("a 26 MB attachment waits for a click, then starts downloading", async ({ browser, dev }) => {
    const lap = (await dev.laptop.api("GET", "/api/state")).self;
    const big = new Uint8Array(26 * 1024 * 1024).fill(7);
    const blob = await dev["home-server"].api("POST", "/api/blobs?name=big-video.bin&mime=application%2Foctet-stream", big, { headers: { "Content-Type": "application/octet-stream" } });
    const sent = await dev["home-server"].api("POST", "/api/mail", { to: [lap.id], subject: "Большое вложение", body: "Это больше 25 МБ.", attachments: [blob.id] });
    const msg = await until(async () => (await dev.laptop.api("GET", "/api/mail?folder=inbox")).items.find((m) => m.id === sent.id), 20000, "the message to arrive");
    const att = async () => (await dev.laptop.api("GET", `/api/mail/${msg.id}`)).attachments[0];

    // It is announced but not downloaded on its own.
    const first = await until(att, 10000, "the attachment to be listed");
    eq([first.state, first.needsConsent], ["remote", true], "announced, waiting for consent");
    await new Promise((r) => setTimeout(r, 2500));
    eq((await att()).state, "remote", "still not downloaded after a while");

    const page = await open(browser, dev.laptop, { hash: `mail/inbox/${msg.id}` });
    const card = page.locator('[data-testid="mail-attachment"]').first();
    await card.waitFor();
    eq(await card.getAttribute("data-state"), "remote", "the UI shows it as not downloaded");
    const fetchBtn = card.locator('[data-testid="attachment-fetch"]');
    assert(/26|МБ|MB/.test(await fetchBtn.innerText()), "the button names the size: " + (await fetchBtn.innerText()));
    await fetchBtn.click();
    await until(async () => ["fetching", "ready"].includes((await att()).state), 10000, "the download to start on the node");
    await page.waitForFunction(() => ["fetching", "ready"].includes(document.querySelector('[data-testid="mail-attachment"]')?.getAttribute("data-state")), null, { timeout: 10000 });
    assert((await card.locator('[data-testid="attachment-fetch"]').count()) === 0, "the consent button is gone once the download runs");
    eq(page.problems, [], "console / network problems");
  });
});
