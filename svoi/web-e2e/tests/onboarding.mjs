import { group, test, assert, eq, until, open, nav, tid, startNode } from "../lib.mjs";

group("onboarding (real processes)", { noDemo: true }, () => {
  test("a fresh device offers to create or join; creating a mesh validates and lands on the devices page", async ({ browser }) => {
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
    await tid(page, "self-card").waitFor();
    const st = await node.api("GET", "/api/state");
    eq([st.configured, st.self.meshName, st.self.name, st.self.owner, st.self.admin], [true, "Моя семья", "kukhonnyy-noutbuk", "Мария", true], "what was created");
    eq(page.problems.filter((p) => !/400/.test(p)), [], "console / network problems");
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
    await page.getByText(/SVOI1|код/i).first().waitFor();
    // a well-formed but unknown code fails with an explanation
    await tid(page, "onb-code").fill("SVOI1-AEAWVQFQ-AAAAAAAA");
    await tid(page, "onb-submit").click();
    await tid(page, "onb-error").waitFor({ timeout: 40000 });
    // the real one: whose device it is was decided by the inviter, so the form does not ask
    const inv = await alpha.api("POST", "/api/invites", { admin: false, owner: "Мария" });
    eq(inv.owner, "Мария", "the invitation carries the owner the inviter named");
    eq(await tid(page, "onb-owner").count(), 0, "joining asks for no owner");
    await tid(page, "onb-code").fill(inv.code);
    await tid(page, "onb-device-name").fill("beta");
    await tid(page, "onb-submit").click();
    await tid(page, "self-card").waitFor({ timeout: 40000 });
    await page.waitForFunction(() => document.querySelectorAll('[data-testid="device-card"]').length === 1, null, { timeout: 20000 });
    eq(await page.locator('[data-testid="device-card"]').first().getAttribute("data-name"), "alpha", "alpha is listed");
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
