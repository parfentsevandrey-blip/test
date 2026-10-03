import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import crypto from "node:crypto";
import { group, test, assert, eq, until, open, nav, tid } from "../lib.mjs";

const peerByName = async (dev, name) => (await dev.laptop.api("GET", "/api/state")).peers.find((p) => p.deviceName === name);
// In-memory file for setInputFiles (Chromium resolves on-disk paths with the process
// locale, which breaks Cyrillic names under LANG=C; payloads keep the name intact).
const tmp = (name, data) => ({ name, mimeType: "application/octet-stream", buffer: Buffer.from(data) });
const sha = (b) => crypto.createHash("sha256").update(b).digest("hex");
const shareByName = async (dev, nasId, name) => (await dev.laptop.api("GET", `/api/peers/${nasId}/shares`)).find((s) => s.name === name);

group("files: browsing a NAS", () => {
  test("shares of another device are listed with their access mode", async ({ browser, dev }) => {
    const nas = await peerByName(dev, "nas");
    const page = await open(browser, dev.laptop, { hash: `files/browse/${nas.id}` });
    await page.waitForFunction(() => document.querySelectorAll('[data-testid="share-card"]').length === 3);
    const shares = await page.$$eval('[data-testid="share-card"]', (els) => els.map((e) => [e.innerText.split("\n")[0].trim(), e.dataset.mode]));
    eq(shares.sort(), [["Документы", "rw"], ["Музыка", "ro"], ["Фото", "ro"]], "shares and modes");
    eq(page.problems, [], "console / network problems");
  });

  test("photo folders show real thumbnails, the viewer pages through them", async ({ browser, dev }) => {
    const nas = await peerByName(dev, "nas");
    const photos = await shareByName(dev, nas.id, "Фото");
    const page = await open(browser, dev.laptop, { hash: `files/browse/${nas.id}/${photos.id}/${encodeURIComponent("Отпуск 2024")}` });
    await page.waitForFunction(() => document.querySelectorAll('[data-testid="file-row"]').length === 6);
    await tid(page, "view-grid").click();
    await page.waitForFunction(() => {
      const imgs = [...document.querySelectorAll("img.thumb")];
      return imgs.length === 6 && imgs.every((i) => i.complete && i.naturalWidth > 0);
    });
    // they are thumbnails (small JPEGs), not the 960x640 originals
    const dims = await page.$$eval("img.thumb", (imgs) => imgs.map((i) => [i.naturalWidth, i.currentSrc.includes("/thumb?")]));
    assert(dims.every(([w, viaThumb]) => w <= 256 && viaThumb), "thumbnails should come from /thumb and be ≤256px wide: " + JSON.stringify(dims));
    // viewer
    await page.locator('[data-testid="file-tile"] .ftile__open').first().click();
    const first = await tid(page, "preview-name").innerText();
    await page.locator("img.pv__img.is-loaded").waitFor();
    const nat = await page.$eval("img.pv__img", (i) => [i.naturalWidth, i.naturalHeight]);
    eq(nat, [960, 640], "the viewer shows the original");
    await tid(page, "preview-next").click();
    await page.waitForFunction((n) => document.querySelector('[data-testid="preview-name"]').innerText !== n, first);
    await page.waitForTimeout(100); // the key handler is re-registered by an effect after each render
    await page.keyboard.press("ArrowLeft");
    await page.waitForFunction((n) => document.querySelector('[data-testid="preview-name"]').innerText === n, first);
    await page.keyboard.press("Escape");
    await tid(page, "preview-name").waitFor({ state: "detached" });
    eq(page.problems, [], "console / network problems");
  });

  test("music plays (range requests) and documents preview in place", async ({ browser, dev }) => {
    const nas = await peerByName(dev, "nas");
    const music = await shareByName(dev, nas.id, "Музыка");
    const docs = await shareByName(dev, nas.id, "Документы");
    // headless Chromium has no PDF viewer: navigating the blob: iframe ends as ERR_ABORTED (a download), which is not a bug
    const page = await open(browser, dev.laptop, { hash: `files/browse/${nas.id}/${music.id}/${encodeURIComponent("Плейлист")}`, allow: [/blob:.*ERR_ABORTED/] });
    await page.locator('[data-testid="file-row"] button.frow__link').first().click();
    const audio = page.locator("audio");
    await audio.waitFor();
    await page.waitForFunction(() => { const a = document.querySelector("audio"); return a && a.readyState >= 1 && a.duration > 5 && a.duration < 7; });
    // seeking works: the server answers byte ranges
    await page.evaluate(async () => { const a = document.querySelector("audio"); a.currentTime = 3; await new Promise((r) => a.addEventListener("seeked", r, { once: true })); });
    assert((await audio.evaluate((a) => a.currentTime)) >= 2.9, "seeking in the audio");
    await page.keyboard.press("Escape");

    await nav(page, dev.laptop, `files/browse/${nas.id}/${docs.id}`);
    await page.locator('[data-testid="file-row"][data-name="Заметки.txt"] button.frow__link').click();
    await page.locator(".pv__text pre").waitFor();
    assert(/молоко/.test(await page.locator(".pv__text pre").innerText()), "text preview shows the file");
    await page.keyboard.press("Escape");
    await page.locator('[data-testid="file-row"][data-name="Договор.pdf"] button.frow__link').click();
    const src = await page.locator("iframe.pv__pdf").getAttribute("src");
    assert(src && src.startsWith("blob:"), "the PDF is shown from a blob (never navigated on the UI origin): " + src);
    eq(page.problems, [], "console / network problems");
  });

  test("a read-only share offers no upload, the filter narrows the list", async ({ browser, dev }) => {
    const nas = await peerByName(dev, "nas");
    const photos = await shareByName(dev, nas.id, "Фото");
    const page = await open(browser, dev.laptop, { hash: `files/browse/${nas.id}/${photos.id}/${encodeURIComponent("Дача")}` });
    await page.waitForFunction(() => document.querySelectorAll('[data-testid="file-row"]').length === 4);
    await tid(page, "read-only").waitFor();
    eq(await tid(page, "upload-button").count(), 0, "upload button on a read-only share");
    await tid(page, "file-filter").fill("яблон");
    await page.waitForFunction(() => document.querySelectorAll('[data-testid="file-row"]').length === 1);
    assert(/Яблоня/.test(await tid(page, "file-row").innerText()), "filtered row");
    // the server refuses writes as well, not just the UI
    const res = await dev.laptop.api("PUT", `/api/peers/${nas.id}/file?share=${photos.id}&path=/x.txt`, new TextEncoder().encode("x"), { raw: true });
    eq(res.status, 403, "PUT into a read-only share");
    eq(page.problems.filter((p) => !/403/.test(p)), [], "console / network problems");
  });

  test("ranges and download headers are correct end to end", async ({ dev }) => {
    const nas = await peerByName(dev, "nas");
    const docs = await shareByName(dev, nas.id, "Документы");
    const url = `/api/peers/${nas.id}/file?share=${docs.id}&path=${encodeURIComponent("/Бюджет.csv")}`;
    const full = Buffer.from(await (await dev.laptop.api("GET", url, undefined, { raw: true })).arrayBuffer());
    assert(/январь/.test(full.toString()), "CSV content");
    const r = await dev.laptop.api("GET", url, undefined, { raw: true, headers: { Range: "bytes=6-11" } });
    eq(r.status, 206, "range status");
    eq(Buffer.from(await r.arrayBuffer()).toString(), full.subarray(6, 12).toString(), "range body");
    assert(/^bytes 6-11\/\d+$/.test(r.headers.get("content-range")), "Content-Range: " + r.headers.get("content-range"));
    const dl = await dev.laptop.api("GET", url + "&dl=1", undefined, { raw: true });
    assert(/^attachment/.test(dl.headers.get("content-disposition")), "attachment disposition");
    assert(/filename\*=UTF-8''/i.test(dl.headers.get("content-disposition")), "non-ASCII file names are encoded: " + dl.headers.get("content-disposition"));
    await dl.arrayBuffer();
  });
});

group("files: writing to a share", () => {
  test("folders and files can be created, replaced, renamed and deleted on another device", async ({ browser, dev }) => {
    const nas = await peerByName(dev, "nas");
    const docs = await shareByName(dev, nas.id, "Документы");
    const page = await open(browser, dev.laptop, { hash: `files/browse/${nas.id}/${docs.id}` });
    await page.waitForFunction(() => document.querySelectorAll('[data-testid="file-row"]').length === 3);
    // new folder
    await tid(page, "new-folder").click();
    await tid(page, "prompt-input").fill("Архив 2024");
    await tid(page, "prompt-ok").click();
    await page.locator('[data-testid="file-row"][data-name="Архив 2024"]').waitFor();
    // upload (through the UI's file input)
    const body = crypto.randomBytes(300 * 1024);
    await tid(page, "upload-input").setInputFiles(tmp("отчёт.bin", body));
    await page.locator('[data-testid="upload-item"][data-status="done"]').waitFor();
    await page.locator('[data-testid="file-row"][data-name="отчёт.bin"]').waitFor();
    const remote = async (p) => Buffer.from(await (await dev.nas.api("GET", `/api/peers/self/file?share=${docs.id}&path=${encodeURIComponent(p)}`, undefined, { raw: true })).arrayBuffer());
    eq(sha(await remote("/отчёт.bin")), sha(body), "uploaded bytes arrived intact on the NAS");
    // uploading the same name asks before replacing
    const body2 = crypto.randomBytes(1024);
    await tid(page, "upload-input").setInputFiles(tmp("отчёт.bin", body2));
    await tid(page, "confirm-ok").click();
    await until(async () => sha(await remote("/отчёт.bin")) === sha(body2), 8000, "the file to be replaced");
    // rename through the row menu
    const row = page.locator('[data-testid="file-row"][data-name="отчёт.bin"]');
    await row.locator('button[aria-label*="отчёт.bin"]').last().click();
    await page.getByRole("menuitem").filter({ hasText: /Переименовать/ }).click();
    // the dialog pre-selects only the base name (so typing keeps the extension); select everything to replace it all
    await tid(page, "prompt-input").click();
    await page.keyboard.press("Control+A");
    await page.keyboard.type("итог.bin");
    await tid(page, "prompt-ok").click();
    await page.locator('[data-testid="file-row"][data-name="итог.bin"]').waitFor();
    eq(sha(await remote("/итог.bin")), sha(body2), "renamed file content");
    // delete
    await page.locator('[data-testid="file-row"][data-name="итог.bin"] button[aria-label*="итог.bin"]').last().click();
    await page.getByRole("menuitem").filter({ hasText: /Удалить/ }).click();
    await tid(page, "confirm-ok").click();
    await page.locator('[data-testid="file-row"][data-name="итог.bin"]').waitFor({ state: "detached" });
    const listing = await dev.nas.api("GET", `/api/peers/self/fs?share=${docs.id}&path=/`);
    assert(!listing.entries.some((e) => e.name === "итог.bin"), "deleted on the NAS too");
    eq(page.problems.filter((p) => !/409/.test(p)), [], "console / network problems");
  });

  test("path tricks cannot leave the shared folder", async ({ dev }) => {
    const nas = await peerByName(dev, "nas");
    const docs = await shareByName(dev, nas.id, "Документы");
    const local = (await dev.nas.api("GET", "/api/shares")).find((s) => s.id === docs.id);
    for (const p of ["/../../../../etc/passwd", "/..%2f..%2fetc/passwd", "/sub/../../x", "//etc/passwd"]) {
      const r = await dev.laptop.api("GET", `/api/peers/${nas.id}/file?share=${docs.id}&path=${p}`, undefined, { raw: true });
      assert(!/root:/.test(await r.text()), `path ${p} leaked /etc/passwd`);
    }
    // a write with ".." must never create anything next to the shared folder
    await dev.laptop.api("PUT", `/api/peers/${nas.id}/file?share=${docs.id}&path=/../escape.txt`, new TextEncoder().encode("x"), { raw: true });
    await dev.laptop.api("PUT", `/api/peers/${nas.id}/file?share=${docs.id}&path=${encodeURIComponent("/../../escape2.txt")}`, new TextEncoder().encode("x"), { raw: true });
    const parent = await dev.nas.api("GET", "/api/local/fs?path=" + encodeURIComponent(path.dirname(local.path)));
    assert(parent.entries.every((e) => !e.isDir || true), "listing works");
    assert(!fs.existsSync(path.join(path.dirname(local.path), "escape.txt")), "escape.txt was created outside the share");
    assert(!fs.existsSync(path.join(path.dirname(path.dirname(local.path)), "escape2.txt")), "escape2.txt was created outside the share");
    // and symlinks pointing out of the share are not followed
    fs.symlinkSync("/etc", path.join(local.path, "etc-link"));
    const r = await dev.laptop.api("GET", `/api/peers/${nas.id}/file?share=${docs.id}&path=${encodeURIComponent("/etc-link/passwd")}`, undefined, { raw: true });
    assert(!/root:/.test(await r.text()) && r.status >= 400, "a symlink leading out of the share was followed (" + r.status + ")");
    fs.unlinkSync(path.join(local.path, "etc-link"));
  });
});

group("files: sending and receiving", () => {
  test("a file sent to another device of the same owner arrives without asking", async ({ browser, dev }) => {
    const hs = await peerByName(dev, "home-server");
    const page = await open(browser, dev.laptop, { hash: "files/send" });
    const data = crypto.randomBytes(700 * 1024 + 17);
    const f = tmp("снимок.dat", data);
    await page.locator(`[data-testid="device-chip"][data-name="home-server"]`).click();
    await tid(page, "dropzone-input").setInputFiles(f);
    await tid(page, "staged-file").waitFor();
    await tid(page, "send-submit").click();
    const done = await until(async () => {
      const list = await dev["home-server"].api("GET", "/api/transfers");
      return list.find((t) => t.name === "снимок.dat" && t.dir === "in" && t.state === "done");
    }, 20000, "home-server to receive the file");
    eq(sha(fs.readFileSync(done.path)), sha(data), "received bytes");
    // the sender's list shows it as done too
    await page.locator('[data-testid="transfer"][data-dir="out"][data-state="done"]').first().waitFor();
    eq(page.problems, [], "console / network problems");
  });

  // The same race as in the chat: a small file is done before the page reads the answer to its own request.
  test("a file that is delivered before the page has read the answer to «send» is not shown as still sending", async ({ browser, dev }) => {
    const hs = await peerByName(dev, "home-server");
    const page = await open(browser, dev.laptop, { hash: `files/send?to=${hs.id}` });
    let release;
    const held = new Promise((r) => (release = r));
    await page.route(/\/api\/transfers\?/, async (route) => {
      if (route.request().method() !== "POST") return route.continue();
      const answer = await route.fetch();
      await held;
      await route.fulfill({ response: answer });
    });
    const name = "быстрый-" + crypto.randomBytes(3).toString("hex") + ".bin";
    const data = crypto.randomBytes(20 * 1024);
    await tid(page, "dropzone-input").setInputFiles(tmp(name, data));
    await tid(page, "staged-file").waitFor();
    await tid(page, "send-submit").click();
    const done = await until(async () => (await dev["home-server"].api("GET", "/api/transfers")).find((t) => t.name === name && t.dir === "in" && t.state === "done"), 20000, "home-server to receive the file");
    eq(sha(fs.readFileSync(done.path)), sha(data), "received bytes");
    const row = page.locator('[data-testid="transfer"][data-dir="out"]', { hasText: name });
    await page.locator('[data-testid="transfer"][data-dir="out"][data-state="done"]', { hasText: name }).waitFor({ timeout: 15000 }); // from the live events alone
    release();
    await page.waitForTimeout(700);
    eq(await row.getAttribute("data-state"), "done", "the late answer did not take the transfer back to an earlier state");
    eq(await row.count(), 1, "and it is listed once");
    eq(page.problems, [], "console / network problems");
  });

  test("a file for another person's device waits for their decision, then flows", async ({ browser, dev }) => {
    const phone = await peerByName(dev, "phone");
    const page = await open(browser, dev.laptop, { hash: `files/send?to=${phone.id}` });
    const data = crypto.randomBytes(120 * 1024);
    await tid(page, "dropzone-input").setInputFiles(tmp("для-анны.bin", data));
    await tid(page, "staged-file").waitFor();
    await tid(page, "send-submit").click();
    // on the phone it is an offer that has to be accepted (Анна ≠ Андрей)
    const offer = await until(async () => (await dev.phone.api("GET", "/api/transfers")).find((t) => t.name === "для-анны.bin" && t.dir === "in" && t.state === "offered"), 15000, "the offer to reach the phone");
    await page.locator('[data-testid="transfer"][data-dir="out"][data-state="offered"]').first().waitFor();
    await dev.phone.api("POST", `/api/transfers/${offer.id}/accept`, {});
    const done = await until(async () => (await dev.phone.api("GET", "/api/transfers")).find((t) => t.id === offer.id && t.state === "done"), 20000, "the transfer to finish");
    eq(sha(fs.readFileSync(done.path)), sha(data), "bytes on the phone");
    await page.locator('[data-testid="transfer"][data-dir="out"][data-state="done"]').first().waitFor();
    eq(page.problems, [], "console / network problems");
  });

  test("an incoming offer shows a banner and can be accepted", async ({ browser, dev }) => {
    // the banner is on every page but Home (which lists offers itself, see home.mjs) and Files → Send
    const page = await open(browser, dev.laptop, { hash: "devices" });
    await tid(page, "offers-banner").waitFor();
    assert(/IMG_20241005_dacha\.png/.test(await tid(page, "offers-banner").innerText()), "the banner names the file");
    await tid(page, "offer-accept").click();
    await tid(page, "offers-banner").waitFor({ state: "detached" });
    const done = await until(async () => (await dev.laptop.api("GET", "/api/transfers")).find((t) => /dacha/.test(t.name) && t.state === "done"), 20000, "the offer to be received");
    const bytes = fs.readFileSync(done.path);
    eq([...bytes.subarray(0, 4)], [0x89, 0x50, 0x4e, 0x47], "a PNG was saved");
    // and it can be opened from the transfers list
    await nav(page, dev.laptop, "files/send");
    await page.locator('[data-testid="transfer"][data-dir="in"][data-state="done"] [data-testid="transfer-open"]').first().click();
    await page.locator("img.pv__img.is-loaded").waitFor();
    eq(page.problems, [], "console / network problems");
  });

  test("declining an offer tells the sender", async ({ browser, dev }) => {
    const lap = await dev.laptop.api("GET", "/api/state");
    const png = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNgYGD4DwABBAEAHnOcgAAAAABJRU5ErkJggg==", "base64");
    const sent = await dev.phone.api("POST", `/api/transfers?to=${lap.self.id}&name=spam.png&mime=image/png`, new Uint8Array(png), { headers: { "Content-Type": "application/octet-stream" } });
    const id = sent.transfers[0].id;
    const page = await open(browser, dev.laptop, { hash: "devices" });
    await page.locator('[data-testid="offers-banner"]', { hasText: "spam.png" }).waitFor();
    await page.locator('[data-testid="offers-banner"]', { hasText: "spam.png" }).locator('[data-testid="offer-decline"]').click();
    await until(async () => (await dev.phone.api("GET", "/api/transfers")).find((t) => t.id === id && t.state === "declined"), 10000, "the sender to see the refusal");
    eq(page.problems, [], "console / network problems");
  });
});
