import { group, test, assert, eq, until, open, tid, startNode, freePort } from "../lib.mjs";

// How a person gets into the interface: with a one-time link. The master token
// (what the command line uses) must never reach the browser.
const navVisible = (page) =>
  page.waitForFunction(() => [...document.querySelectorAll('[data-testid="nav-devices"], [data-testid="tab-devices"]')].some((e) => e.getClientRects().length > 0));

group("signing in", () => {
  test("a sign-in link works exactly once and leaves no secret in the page", async ({ browser, dev }) => {
    const lap = dev.laptop;
    const { code, url, expiresIn, singleUse } = await lap.api("POST", "/api/login/code", {});
    assert(url.endsWith(`/?t=${code}`) && expiresIn === 600 && singleUse === true, `login link description: ${url} ${expiresIn} ${singleUse}`);
    assert(code !== lap.token && /^[0-9a-f]{48}$/.test(code), "the code is a fresh random value, not the token");

    const ctx1 = await browser.newContext();
    const p1 = await ctx1.newPage();
    await p1.goto(`${lap.origin}/?t=${code}`);
    await navVisible(p1);
    assert(!p1.url().includes("t="), "the code was removed from the address bar: " + p1.url());

    // The cookie is a session: HttpOnly, SameSite=Strict, not the token, invisible to scripts.
    const cookie = (await ctx1.cookies()).find((c) => c.name.startsWith("svoi_session"));
    assert(cookie, "a session cookie was set");
    assert(cookie.httpOnly && cookie.sameSite === "Strict", `cookie flags: ${JSON.stringify(cookie)}`);
    assert(cookie.value.length === 64 && cookie.value !== lap.token && cookie.value !== code, "the cookie is its own random session id");
    eq(await p1.evaluate(() => document.cookie), "", "scripts cannot see the cookie");

    // Nothing the page can reach contains the master token or the session id.
    const dump = await p1.evaluate(() =>
      JSON.stringify({
        html: document.documentElement.outerHTML,
        local: { ...localStorage },
        session: { ...sessionStorage },
        name: window.name,
        urls: performance.getEntries().map((e) => e.name),
        href: location.href,
      }),
    );
    assert(!dump.includes(lap.token), "the master token is somewhere in the page");
    assert(!dump.includes(cookie.value), "the session id is somewhere in the page");

    // Using the same link again does nothing but explain.
    const ctx2 = await browser.newContext();
    const p2 = await ctx2.newPage();
    await p2.goto(`${lap.origin}/?t=${code}`);
    await tid(p2, "unauthorized").waitFor();
    eq(await tid(p2, "unauthorized").getAttribute("data-reason"), "link", "the screen says the link was used");
    assert((await ctx2.cookies()).every((c) => !c.name.startsWith("svoi_session")), "a used link produced a session");
    // The master token is no sign-in link either.
    await p2.goto(`${lap.origin}/?t=${lap.token}`);
    await tid(p2, "unauthorized").waitFor();
    assert((await ctx2.cookies()).every((c) => !c.name.startsWith("svoi_session")), "the master token produced a session");
    await ctx2.close();

    // The first browser carries on, also after a reload.
    await p1.reload();
    await navVisible(p1);
    await ctx1.close();
  });

  test("signing out in the interface ends that session, and a new link signs back in", async ({ browser, dev }) => {
    const lap = dev.laptop;
    const page = await open(browser, lap, { hash: "settings", allow: [/HTTP 401/] });
    const ctx = page.context();
    await tid(page, "logout").click();
    await tid(page, "confirm-ok").click();
    await tid(page, "unauthorized").waitFor();
    eq(await tid(page, "unauthorized").getAttribute("data-reason"), "signed-out", "the screen says we signed out");
    // The old cookie is dead on the server too, not just forgotten by the page.
    const dead = (await ctx.cookies()).find((c) => c.name.startsWith("svoi_session"));
    if (dead) {
      const other = await browser.newContext();
      await other.addCookies([dead]);
      const res = await other.request.get(`${lap.origin}/api/state`);
      eq(res.status(), 401, "the signed-out session still works on the server");
      await other.close();
    }
    const again = await open(browser, lap);
    eq((await again.evaluate(() => fetch("api/state").then((r) => r.status))), 200, "a new sign-in works");
  });

  test("`svoi signout` ends every browser session at once", async ({ browser, dev }) => {
    const lap = dev.laptop;
    const a = await open(browser, lap);
    const b = await open(browser, lap, { w: 390, h: 800, mobile: true, allow: [/HTTP 401/, /requestfailed/] });
    eq(await a.evaluate(() => fetch("api/state").then((r) => r.status)), 200);
    eq(await b.evaluate(() => fetch("api/state").then((r) => r.status)), 200);
    await lap.api("POST", "/api/logout?all=1", {}); // what the command line does
    for (const page of [a, b]) {
      await until(async () => (await page.evaluate(() => fetch("api/state").then((r) => r.status))) === 401, 5000, "the session to stop working");
      await page.reload();
      await tid(page, "unauthorized").waitFor();
    }
    // The command line itself is unaffected and can sign a browser in again.
    eq((await lap.api("GET", "/api/state")).self.name, "laptop");
    const c = await open(browser, lap, { allow: [] });
    eq(await c.evaluate(() => fetch("api/state").then((r) => r.status)), 200);
  });

  test("signing in to another device on the same machine does not sign the first one out", async ({ browser, dev }) => {
    // One browser, three devices on localhost ports: browsers share cookies between ports.
    const ctx = await browser.newContext();
    const devices = [dev.laptop, dev.phone, dev.nas];
    for (const d of devices) {
      const { code } = await d.api("POST", "/api/login/code", {});
      const page = await ctx.newPage();
      await page.goto(`${d.origin}/?t=${code}`);
      await navVisible(page);
    }
    const names = (await ctx.cookies()).map((c) => c.name).filter((n) => n.startsWith("svoi_session"));
    eq(new Set(names).size, 3, "each device keeps its own session cookie");
    for (const d of devices) eq((await ctx.request.get(`${d.origin}/api/state`)).status(), 200, `${d.name} is still signed in`);
    await ctx.close();
  });
});

group("restarting a node", { noDemo: true }, () => {
  test("an open tab keeps working after the node restarts, without signing in again", async ({ browser }) => {
    const port = await freePort();
    const node = await startNode({ name: "phoenix", init: true, mesh: "Феникс", owner: "Анна", port });
    const page = await open(browser, node, { allow: [/requestfailed/, /HTTP 5\d\d/, /console\.error/] });
    await tid(page, "self-card").waitFor();
    await node.restart();
    eq(node.origin.endsWith(":" + port), true, "the node came back on the same port");
    // The session is stored on disk, so the same cookie is still good...
    eq(await page.evaluate(() => fetch("api/state").then((r) => r.status)), 200, "the old session works after the restart");
    // ...and the open tab recovers on its own: its live stream reconnects, so a change made now shows up
    // without any reload (a pending invitation appears in the list).
    await node.api("POST", "/api/invites", { admin: false, owner: "Анна" });
    await tid(page, "invite-row").waitFor({ timeout: 30000 });
    await page.reload();
    await tid(page, "self-card").waitFor();
    eq(await tid(page, "unauthorized").count(), 0, "no sign-in screen appeared");
    assert((await node.api("GET", "/api/state")).self.name === "phoenix", "the node kept its identity");
  });
});
