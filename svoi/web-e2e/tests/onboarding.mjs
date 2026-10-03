import os from "node:os";
import { group, test, assert, eq, until, open, nav, tid, startNode } from "../lib.mjs";

group("onboarding (real processes)", { noDemo: true }, () => {
  test("a fresh device offers to create or join; creating a mesh validates and lands on Home", async ({ browser }) => {
    const node = await startNode({ name: "fresh" });
    const page = await open(browser, node);
    await tid(page, "page-onboarding").waitFor();
    eq((await node.api("GET", "/api/state")).configured, false, "backend: not configured");
    await tid(page, "onb-create").click();
    // validation: the mesh name is required
    await tid(page, "onb-submit").click();
    await page.getByText(/Заполните это поле|обязательн/i).first().waitFor();
    // an invalid device name is explained
    await tid(page, "onb-mesh-name").fill("Моя семья");
    await tid(page, "onb-device-name").fill("плохое имя!");
    await tid(page, "onb-submit").click();
    assert(await page.locator('[aria-invalid="true"]').count() > 0, "an invalid name is flagged");
    await tid(page, "onb-device-name").fill("Кухонный ноутбук");
    await tid(page, "onb-owner").fill("Мария");
    await tid(page, "onb-submit").click();
    // Home, in plain words: alone in the new network, with the first steps to take
    await tid(page, "page-home").waitFor();
    await page.locator('[data-testid="home-status"][data-state="alone"]').waitFor();
    assert(/добавьте второе устройство/.test(await tid(page, "home-status").innerText()), "the status says what to do next");
    await page.locator('[data-testid="home-start-checklist"] [data-step="add"][data-done="false"]').waitFor();
    const st = await node.api("GET", "/api/state");
    eq([st.configured, st.self.meshName, st.self.name, st.self.owner, st.self.admin], [true, "Моя семья", "kukhonnyy-noutbuk", "Мария", true], "what was created");
    eq(page.problems.filter((p) => !/400/.test(p)), [], "console / network problems");
  });

  test("the name field offers the name the device will really take (the --name it was started with); leaving it alone keeps that name", async ({ browser }) => {
    // what the phone app does: it starts the program with the phone's own name
    const node = await startNode({ name: "offered", offerName: "Кухонный Pixel 8" });
    const page = await open(browser, node);
    await tid(page, "page-onboarding").waitFor();
    eq((await node.api("GET", "/api/state")).self.defaultName, "kukhonnyy-pixel-8", "backend: the name it offers, as a valid device name");
    await tid(page, "onb-create").click();
    eq(await tid(page, "onb-device-name").getAttribute("placeholder"), "kukhonnyy-pixel-8", "create: the field offers it");
    eq(await tid(page, "dns-preview").getAttribute("data-label"), "kukhonnyy-pixel-8", "create: the preview shows the address it gives");
    await tid(page, "onb-mesh-name").fill("Дом");
    await tid(page, "onb-submit").click();
    await tid(page, "page-home").waitFor();
    const st = await node.api("GET", "/api/state");
    eq([st.self.name, st.self.defaultName], ["kukhonnyy-pixel-8", undefined], "the device took the name it offered, and offers none any more");
    // the join form offers the same
    const other = await startNode({ name: "offered-join", offerName: "Nokia 3310" });
    const p2 = await open(browser, other);
    await tid(p2, "onb-join").click();
    eq(await tid(p2, "onb-device-name").getAttribute("placeholder"), "nokia-3310", "join: the field offers it");
    // a device that was started without a name offers its host name, not a made-up one
    const plain = await startNode({ name: "plain" });
    const p3 = await open(browser, plain);
    await tid(p3, "onb-create").click();
    const offered = (await plain.api("GET", "/api/state")).self.defaultName;
    assert(/^[a-z0-9]([a-z0-9-]{0,30}[a-z0-9])?$/.test(offered || ""), `a plain start offers a valid device name (${offered})`);
    const host = os.hostname().split(".")[0];
    if (/^[A-Za-z0-9-]+$/.test(host)) eq(offered, host.toLowerCase().slice(0, 32).replace(/-+$/, ""), "a plain start offers the host name");
    eq(await tid(p3, "onb-device-name").getAttribute("placeholder"), offered, "the field offers what the backend says");
  });

  test("joining with an invitation code works, bad codes are explained, the invitation is single-use", async ({ browser }) => {
    const alpha = await startNode({ name: "alpha", init: true, mesh: "Дом", owner: "Андрей" });
    const beta = await startNode({ name: "beta-fresh" });
    const gamma = await startNode({ name: "gamma-fresh" });
    const page = await open(browser, beta);
    await tid(page, "onb-join").click();
    // a malformed code is rejected before anything is sent
    await tid(page, "onb-code").fill("hello world");
    await tid(page, "onb-submit").click();
    await page.getByText(/MESH1|код/i).first().waitFor();
    // a well-formed but unknown code fails with an explanation
    await tid(page, "onb-code").fill("MESH1-AEAWVQFQ-AAAAAAAA");
    await tid(page, "onb-submit").click();
    await tid(page, "onb-error").waitFor({ timeout: 40000 });
    // the real one: whose device it is was decided by the inviter, so the form does not ask
    const inv = await alpha.api("POST", "/api/invites", { admin: false, owner: "Мария" });
    eq(inv.owner, "Мария", "the invitation carries the owner the inviter named");
    eq(await tid(page, "onb-owner").count(), 0, "joining asks for no owner");
    await tid(page, "onb-code").fill(inv.code);
    await tid(page, "onb-device-name").fill("beta");
    await tid(page, "onb-submit").click();
    await tid(page, "page-home").waitFor({ timeout: 40000 });
    await page.waitForFunction(() => document.querySelectorAll('[data-testid="home-device"]').length === 1, null, { timeout: 20000 });
    eq(await page.locator('[data-testid="home-device"]').first().getAttribute("data-name"), "alpha", "alpha is listed");
    eq((await beta.api("GET", "/api/state")).self.owner, "Мария", "the new device belongs to the person the inviter named, not to whoever typed");
    await until(async () => (await alpha.api("GET", "/api/state")).peers.some((p) => p.deviceName === "beta"), 20000, "alpha to see beta");
    // the same invitation cannot be used again
    const p2 = await open(browser, gamma);
    await tid(p2, "onb-join").click();
    await tid(p2, "onb-code").fill(inv.code);
    await tid(p2, "onb-submit").click();
    await tid(p2, "onb-error").waitFor({ timeout: 40000 });
    eq((await gamma.api("GET", "/api/state")).configured, false, "the second device did not get in");
  });
});
