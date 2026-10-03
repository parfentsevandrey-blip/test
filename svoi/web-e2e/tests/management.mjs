import { group, test, assert, eq, until, open, nav, tid } from "../lib.mjs";

const peerByName = async (dev, name) => (await dev.laptop.api("GET", "/api/state")).peers.find((p) => p.deviceName === name);
const manage = (page, text) => page.locator("button.ddr-link", { hasText: text });

group("device management", () => {
  test("an administrator renames a device; everyone sees the new name", async ({ browser, dev }) => {
    const nas = await peerByName(dev, "nas");
    const page = await open(browser, dev.laptop, { hash: `devices/${nas.id}` });
    await tid(page, "device-drawer").waitFor();
    await manage(page, "Переименовать устройство").click();
    await tid(page, "prompt-input").click();
    await page.keyboard.press("Control+A");
    await page.keyboard.type("storage");
    await tid(page, "prompt-ok").click();
    await page.locator('[data-testid="device-card"][data-name="storage"]').waitFor();
    await until(async () => (await dev.nas.api("GET", "/api/state")).self.name === "storage", 10000, "the NAS to take its new name");
    await until(async () => (await dev.phone.api("GET", "/api/state")).peers.some((p) => p.name === "storage"), 20000, "the phone to learn the new name");
    // the name is also its address on the overlay: the certificate was re-issued, not just a label changed
    const fresh = (await dev.laptop.api("GET", "/api/state")).peers.find((p) => p.id === nas.id);
    eq(fresh.deviceName, "storage", "device name in the laptop's state");
    eq(fresh.ip4, nas.ip4, "the overlay address stays");
    eq(page.problems, [], "console / network problems");
  });

  test("a device is made an administrator (with a warning), and can then invite others", async ({ browser, dev }) => {
    const phone = await peerByName(dev, "phone");
    // before: the phone is an ordinary member and may not create invitations
    const before = await dev.phone.api("POST", "/api/invites", { admin: false }, { raw: true });
    eq(before.status, 403, "a member cannot invite");
    const page = await open(browser, dev.laptop, { hash: `devices/${phone.id}` });
    await manage(page, "Сделать администратором").click();
    assert(/ключ|полн|администратор/i.test(await tid(page, "confirm-ok").locator("xpath=ancestor::*[@role='dialog']").innerText()), "the dialog warns about what this means");
    await tid(page, "confirm-ok").click();
    await until(async () => (await dev.phone.api("GET", "/api/state")).self.admin === true, 20000, "the phone to become an administrator");
    const after = await dev.phone.api("POST", "/api/invites", { admin: false });
    assert(/^SVOI1-/.test(after.code), "the new administrator can create invitations");
    // everyone else sees the admin flag on the phone
    await until(async () => (await dev.nas.api("GET", "/api/state")).peers.find((p) => p.deviceName === "phone")?.admin === true, 20000, "the NAS to see the flag");
    eq(page.problems, [], "console / network problems");
  });

  test("a device is removed from the network and the others forget it", async ({ browser, dev }) => {
    const phone = await peerByName(dev, "phone");
    const page = await open(browser, dev.laptop, { hash: `devices/${phone.id}` });
    await manage(page, "Удалить из сети").click();
    // a destructive action: the name must be typed to confirm
    await tid(page, "confirm-input").fill("phone");
    await tid(page, "confirm-ok").click();
    await page.locator('[data-testid="device-card"][data-name="phone"]').waitFor({ state: "detached" });
    await until(async () => !(await dev.laptop.api("GET", "/api/state")).peers.some((p) => p.deviceName === "phone"), 10000, "the laptop to forget the phone");
    await until(async () => !(await dev.nas.api("GET", "/api/state")).peers.some((p) => p.deviceName === "phone"), 30000, "the NAS to forget the phone (revocation propagates)");
    await until(async () => !(await dev["home-server"].api("GET", "/api/state")).peers.some((p) => p.deviceName === "phone"), 30000, "the home server to forget the phone");
    // the removed device was told while its link existed: it forgot the mesh, says why, and has a fresh identity
    const phoneView = await until(async () => {
      const v = await dev.phone.api("GET", "/api/state");
      return v.configured === false ? v : null;
    }, 15000, "the removed phone to leave the mesh by itself");
    eq(phoneView.removed.meshName, "Дом", "the phone knows which network it was removed from");
    assert(phoneView.self.id !== phone.id, "the removed device took a new identity (the old one stays revoked)");
    const lap = (await dev.laptop.api("GET", "/api/state")).self;
    const r = await dev.phone.api("GET", `/api/peers/${lap.id}/shares`, undefined, { raw: true });
    assert(r.status >= 400, "a removed device must not list shares, got " + r.status);
    // it can be invited again: with a new invitation, under its new identity
    // (the home server is the reachable one: the phone sits behind a carrier NAT and the laptop behind a home router)
    const inv = await dev["home-server"].api("POST", "/api/invites", { admin: false });
    await dev.phone.api("POST", "/api/mesh/join", { invite: inv.code, deviceName: "phone", owner: "Анна" });
    await until(async () => (await dev.laptop.api("GET", "/api/state")).peers.some((p) => p.deviceName === "phone" && p.online), 30000, "the phone to be back");
    eq(page.problems.filter((p) => !/50[02]|40[34]/.test(p)), [], "console / network problems");
  });
});
