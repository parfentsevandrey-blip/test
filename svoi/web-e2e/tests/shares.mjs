import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { group, test, assert, eq, until, open, nav, tid } from "../lib.mjs";

const peerByName = async (dev, name) => (await dev.laptop.api("GET", "/api/state")).peers.find((p) => p.deviceName === name);

group("shares of this device", () => {
  test("a folder is shared read-only, then read-write, then only with one device — and others see exactly that", async ({ browser, dev }) => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), "themesh-e2e-share-"));
    fs.writeFileSync(path.join(dir, "hello.txt"), "привет из ноутбука");
    fs.mkdirSync(path.join(dir, "sub"));
    const lap = (await dev.laptop.api("GET", "/api/state")).self;
    const phone = await peerByName(dev, "phone");
    const page = await open(browser, dev.laptop, { hash: "files/shares" });

    // pick the folder with the picker (breadcrumbs → /tmp → our folder)
    await tid(page, "share-add").click();
    await tid(page, "share-pick").click();
    await tid(page, "folder-picker").waitFor();
    await page.locator('[data-testid="folder-picker"] .picker__crumbs button').first().click(); // "/"
    await page.locator('[data-testid="picker-item"][data-name="tmp"]').click();
    await page.locator(`[data-testid="picker-item"][data-name="${path.basename(dir)}"]`).click();
    await page.locator('[data-testid="picker-item"][data-name="sub"]').waitFor(); // we are inside it
    await tid(page, "picker-choose").click();
    eq(await tid(page, "share-path").inputValue(), dir, "picked path");
    eq(await tid(page, "share-name").inputValue(), path.basename(dir), "the name is suggested from the folder");
    await tid(page, "share-name").fill("Общая папка");
    await tid(page, "share-save").click();
    const row = page.locator('[data-testid="share-row"][data-name="Общая папка"]');
    await row.waitFor();
    const id = await row.getAttribute("data-id");

    // the NAS sees it (read-only) and can read
    const seen = await until(async () => (await dev.nas.api("GET", `/api/peers/${lap.id}/shares`)).find((s) => s.id === id), 10000, "the NAS to see the share");
    eq(seen.mode, "ro", "mode as seen by the NAS");
    const ls = await dev.nas.api("GET", `/api/peers/${lap.id}/fs?share=${id}&path=/`);
    eq(ls.entries.map((e) => e.name).sort(), ["hello.txt", "sub"], "listing");
    eq(ls.canWrite, false, "canWrite for a read-only share");
    const put = (name, body) => dev.nas.api("PUT", `/api/peers/${lap.id}/file?share=${id}&path=/${name}`, new TextEncoder().encode(body), { raw: true });
    eq((await put("from-nas.txt", "x")).status, 403, "write to a read-only share");

    // make it read-write through the edit dialog
    await row.locator('button[aria-label="Изменить"]').click();
    await page.getByRole("radio", { name: /Чтение и запись|Запись/ }).click();
    await tid(page, "share-save").click();
    await until(async () => (await dev.nas.api("GET", `/api/peers/${lap.id}/shares`)).find((s) => s.id === id)?.mode === "rw", 10000, "the NAS to see mode rw");
    const w = await put("from-nas.txt", "записано с NAS");
    eq(w.status, 200, "write to a read-write share");
    eq(fs.readFileSync(path.join(dir, "from-nas.txt"), "utf8"), "записано с NAS", "the file really is on this device's disk");

    // grant it to the phone only
    await row.locator('button[aria-label="Изменить"]').click();
    await page.getByRole("radio", { name: /Выбранным|Выбран/ }).click();
    await page.locator('[data-testid="device-chip"][data-name="phone"]').click();
    await tid(page, "share-save").click();
    await until(async () => !(await dev.nas.api("GET", `/api/peers/${lap.id}/shares`)).some((s) => s.id === id), 10000, "the NAS to lose access");
    assert((await dev.phone.api("GET", `/api/peers/${lap.id}/shares`)).some((s) => s.id === id), "the phone keeps access");
    const again = (await put("again.txt", "x")).status;
    assert(again === 403 || again === 404, "the NAS can no longer write, got " + again);
    const denied = await dev.nas.api("GET", `/api/peers/${lap.id}/fs?share=${id}&path=/`, undefined, { raw: true });
    assert(denied.status === 403 || denied.status === 404, "the NAS can no longer list: " + denied.status);

    // delete it
    await row.locator('button[aria-label="Удалить"]').click();
    await tid(page, "confirm-ok").click();
    await row.waitFor({ state: "detached" });
    await until(async () => !(await dev.phone.api("GET", `/api/peers/${lap.id}/shares`)).some((s) => s.id === id), 10000, "the share to disappear everywhere");
    assert(fs.existsSync(path.join(dir, "hello.txt")), "deleting a share does not delete the folder");
    eq(page.problems.filter((p) => !/403/.test(p)), [], "console / network problems");
  });

  test("sharing a folder that does not exist is refused with a clear message", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { hash: "files/shares" });
    await tid(page, "share-add").click();
    await tid(page, "share-path").fill("/definitely/not/here");
    await tid(page, "share-name").fill("Призрак");
    await tid(page, "share-save").click();
    await page.getByText(/Такой папки нет на устройстве/).first().waitFor();
    eq((await dev.laptop.api("GET", "/api/shares")).length, 0, "nothing was saved");
    eq(page.problems.filter((p) => !/400/.test(p)), [], "console / network problems");
  });
});
