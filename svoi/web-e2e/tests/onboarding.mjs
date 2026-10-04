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

  // The phone app puts `window.themeshApp` into its window and answers with a `themesh-scan` event (docs/UI-API.md → "Scanning an invitation").
  const phoneApp = () => {
    window.__scanCalls = 0;
    window.themeshApp = { canScan: () => true, scanInvite: () => { window.__scanCalls += 1; } };
  };
  const camera = (page, detail) => page.evaluate((d) => window.dispatchEvent(new CustomEvent("themesh-scan", { detail: d })), detail);

  test("the join form offers «Сканировать QR-код» only in the phone app, and an invitation its camera read joins at once", async ({ browser }) => {
    const alpha = await startNode({ name: "alpha-scan", init: true, mesh: "Дом", owner: "Андрей" });
    const beta = await startNode({ name: "beta-scan", offerName: "Pixel 8" });
    // in a browser (and in the desktop app) there is no camera button
    const plain = await open(browser, beta);
    await tid(plain, "onb-join").click();
    await tid(plain, "onb-code").waitFor();
    eq(await tid(plain, "onb-scan").count(), 0, "no scan button without the phone app");
    // in the phone app there is, and it asks the app for the camera
    const page = await open(browser, beta, { init: phoneApp });
    await tid(page, "onb-join").click();
    await tid(page, "onb-scan").click();
    eq(await page.evaluate(() => window.__scanCalls), 1, "the button opens the app's camera screen");
    // what the camera brings back that is no invitation, or no camera at all, is said in a word and changes nothing
    await camera(page, { text: "https://example.com/" });
    await page.getByText("Это QR-код не от The Mesh").waitFor();
    await camera(page, { error: "denied" });
    await page.getByText(/Нет доступа к камере/).waitFor();
    await camera(page, { error: "cancelled" });
    eq(await tid(page, "onb-progress").count(), 0, "nothing started");
    eq((await beta.api("GET", "/api/state")).configured, false, "still outside any mesh");
    // an invitation as the Mac's QR carries it (no dashes) is used at once, with the name the device offers
    const inv = await alpha.api("POST", "/api/invites", { admin: false, owner: "Мария" });
    const qrText = "MESH1-" + inv.code.replace(/^MESH1-/, "").replace(/-/g, "");
    assert(!/-/.test(qrText.slice(6)), "the QR text is the code without dashes");
    await camera(page, { text: qrText });
    await tid(page, "page-home").waitFor({ timeout: 40000 });
    const st = await beta.api("GET", "/api/state");
    eq([st.configured, st.self.meshName, st.self.name, st.self.owner], [true, "Дом", "pixel-8", "Мария"], "joined through the code the camera read");
    await until(async () => (await alpha.api("GET", "/api/state")).peers.some((p) => p.deviceName === "pixel-8"), 20000, "alpha to see the phone");
    eq(page.problems, [], "console / network problems");
  });

  test("a device that does not answer is not called a bad code: the form says what to check, and says it before the code is blamed", async ({ browser }) => {
    const alpha = await startNode({ name: "alpha-gone", init: true, mesh: "Дом", owner: "Андрей" });
    const beta = await startNode({ name: "beta-gone" });
    const inv = await alpha.api("POST", "/api/invites", { admin: false, owner: "Мария" });
    await alpha.stop(); // its address is in the invitation, and nobody listens there any more
    const page = await open(browser, beta, { allow: [/HTTP 50\d/, /Failed to load resource/] });
    await tid(page, "onb-join").click();
    await tid(page, "onb-code").fill(inv.code);
    await tid(page, "onb-submit").click();
    await tid(page, "onb-error").waitFor({ timeout: 60000 });
    eq(await tid(page, "onb-error").getAttribute("data-code"), "offline", "the API says the inviting device is unreachable");
    await tid(page, "onb-reach-help").waitFor();
    const text = await tid(page, "onb-error").innerText();
    assert(/в одной сети Wi-Fi/.test(text) && /QR-код/.test(text), "the checklist names the usual causes and the QR");
    assert(!/Код не подошёл|не код приглашения/.test(text), "the code is not blamed");
    assert(/tried 127\.0\.0\.1:\d+/.test(text), "the technical line says which addresses were tried");
    // an expired invitation, as the node says it, is told apart as well
    await page.route("**/api/mesh/join", (route) => route.fulfill({
      status: 410, contentType: "application/json",
      body: JSON.stringify({ error: { code: "expired", message: "mesh: this invitation has expired; ask for a new one (if it should still be valid, check the date and time on this device)" } }),
    }));
    await tid(page, "onb-submit").click();
    await page.getByText(/Приглашение истекло/).waitFor({ timeout: 20000 });
    eq(await tid(page, "onb-error").getAttribute("data-code"), "expired", "expired is its own code");
    eq((await beta.api("GET", "/api/state")).configured, false, "still outside any mesh");
  });

  test("the invitation's QR is large and can be shown larger; Escape closes only the large one", async ({ browser }) => {
    const alpha = await startNode({ name: "alpha-qr", init: true, mesh: "Дом", owner: "Андрей" });
    const page = await open(browser, alpha, { w: 1280, h: 900 });
    await tid(page, "home-action-add").click();
    await tid(page, "invite-create").click();
    await tid(page, "invite-qr").locator("img").waitFor();
    const box = await tid(page, "invite-qr").boundingBox();
    assert(box.width >= 300 && Math.abs(box.width - box.height) < 1, `the QR is ${Math.round(box.width)}×${Math.round(box.height)} px: large enough and square`);
    const code = (await tid(page, "invite-code").innerText()).trim();
    assert(/^MESH1-[A-Z2-7]+(-[A-Z2-7]+)+$/.test(code), "the code shown for copying stays grouped");
    // the QR is the one of the code without dashes: ask the node, which draws it
    const [shown] = (await alpha.api("GET", "/api/invites")).filter((i) => i.code === code);
    assert(shown && shown.qrSvg.startsWith("<svg"), "the invitation list carries the QR");
    assert(shown.endpoints.length > 0 && shown.endpoints.length <= 6, `the invitation lists the addresses it carries: ${shown.endpoints}`);
    const listed = await tid(page, "invite-addrs").textContent();
    assert(shown.endpoints.every((e) => listed.includes(e)), "the dialog shows them in its technical details");
    await tid(page, "invite-qr-enlarge").click();
    await tid(page, "invite-qr-big").locator("img").waitFor();
    const big = await tid(page, "invite-qr-big").boundingBox();
    assert(big.width >= 440 && big.width > box.width, `the large QR is ${Math.round(big.width)} px`);
    await page.keyboard.press("Escape");
    await tid(page, "invite-qr-big").waitFor({ state: "detached" });
    await tid(page, "invite-modal").waitFor();
    eq(await tid(page, "invite-code").count(), 1, "the invitation dialog is still there");
  });

  test("«Добавить устройство» in the desktop app's menu opens the invitation dialog through #/devices?add=1, once", async ({ browser }) => {
    const alpha = await startNode({ name: "alpha-menu", init: true, mesh: "Дом", owner: "Андрей" });
    const page = await open(browser, alpha);
    await nav(page, alpha, "devices?add=1");
    await tid(page, "invite-create").waitFor();
    await until(async () => /#\/devices$/.test(page.url()), 3000, "the address to be cleaned");
    await page.keyboard.press("Escape");
    await tid(page, "invite-create").waitFor({ state: "detached" });
    await page.reload();
    await tid(page, "add-device").waitFor();
    eq(await tid(page, "invite-create").count(), 0, "a reload does not open it again");
  });
});
