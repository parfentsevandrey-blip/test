import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { group, test, assert, eq, until, open, nav, tid } from "../lib.mjs";

const peerByName = async (dev, name) => (await dev.laptop.api("GET", "/api/state")).peers.find((p) => p.deviceName === name);

group("settings", () => {
  test("switches are saved on the device and survive a reload", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { hash: "settings/network" });
    const sw = tid(page, "setting-relay");
    await sw.waitFor();
    eq(await sw.getAttribute("aria-checked"), "true", "relay is on by default");
    await sw.click();
    await until(async () => (await dev.laptop.api("GET", "/api/settings")).relay === false, 5000, "relay=false on the backend");
    await page.reload();
    await tid(page, "setting-relay").waitFor();
    eq(await tid(page, "setting-relay").getAttribute("aria-checked"), "false", "state after reload");
    await tid(page, "setting-relay").click();
    await until(async () => (await dev.laptop.api("GET", "/api/settings")).relay === true, 5000, "relay back on");
    eq(page.problems, [], "console / network problems");
  });

  test("the router port switch is saved on the device and its status says what the router did", async ({ browser, dev }) => {
    // In the demo the devices live in a simulated network, so there is no router to answer:
    // the mapper starts, finds none, and the interface has to say so in plain words.
    const page = await open(browser, dev.laptop, { hash: "settings/network" });
    const sw = tid(page, "setting-portmap");
    await sw.waitFor();
    eq(await sw.getAttribute("aria-checked"), "false", "the demo devices have it off");
    eq(await tid(page, "portmap-status").count(), 0, "no status while the switch is off");
    await sw.click();
    await until(async () => (await dev.laptop.api("GET", "/api/settings")).portMap === true, 5000, "portMap=true on the backend");
    const status = tid(page, "portmap-status");
    await status.waitFor();
    // the node reports through `self`: searching first, then the answer
    await page.waitForFunction(() => document.querySelector('[data-testid="portmap-status"]')?.dataset.state === "unavailable", null, { timeout: 30000 });
    const text = await status.innerText();
    assert(/Роутер не ответил/.test(text), "the explanation of an unanswered router: " + text);
    const self = (await dev.laptop.api("GET", "/api/state")).self;
    eq(self.portmap?.state, "unavailable", "the backend says the same");
    // it survives a reload, and switching off removes the status again
    await page.reload();
    await tid(page, "setting-portmap").waitFor();
    eq(await tid(page, "setting-portmap").getAttribute("aria-checked"), "true", "state after reload");
    await tid(page, "setting-portmap").click();
    await until(async () => (await dev.laptop.api("GET", "/api/settings")).portMap === false, 5000, "portMap=false on the backend");
    await page.waitForFunction(() => !document.querySelector('[data-testid="portmap-status"]'), null, { timeout: 10000 });
    eq(page.problems, [], "console / network problems");
  });

  test("the auto-accept policy really changes how incoming files are treated", async ({ browser, dev }) => {
    const lap = (await dev.laptop.api("GET", "/api/state")).self;
    const page = await open(browser, dev.laptop, { hash: "settings/files" });
    await page.getByRole("radio").nth(2).check(); // own · all · ask
    await until(async () => (await dev.laptop.api("GET", "/api/settings")).autoAccept === "ask", 5000, "autoAccept=ask");
    // a file from the home server (same owner) used to be taken automatically; now it waits for a decision
    const sent = await dev["home-server"].api("POST", `/api/transfers?to=${lap.id}&name=policy.txt&mime=text/plain`, new TextEncoder().encode("hello"), { headers: { "Content-Type": "application/octet-stream" } });
    const id = sent.transfers[0].id;
    await page.waitForTimeout(1500);
    const tr = (await dev.laptop.api("GET", "/api/transfers")).find((t) => t.id === id);
    eq(tr.state, "offered", "with policy 'ask' even own devices need approval");
    await page.getByRole("radio").nth(0).check();
    await until(async () => (await dev.laptop.api("GET", "/api/settings")).autoAccept === "own", 5000, "autoAccept=own");
    const sent2 = await dev["home-server"].api("POST", `/api/transfers?to=${lap.id}&name=policy2.txt&mime=text/plain`, new TextEncoder().encode("hello again"), { headers: { "Content-Type": "application/octet-stream" } });
    await until(async () => (await dev.laptop.api("GET", "/api/transfers")).find((t) => t.id === sent2.transfers[0].id && t.state === "done"), 15000, "own-device file to be auto-accepted");
    eq(page.problems, [], "console / network problems");
  });

  test("the download folder is validated and saved", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { hash: "settings/files" });
    const input = page.locator("#set-files input.input").first();
    await input.waitFor();
    const original = await input.inputValue();
    await input.fill("");
    await page.getByRole("button", { name: /Сохранить/ }).first().click();
    await page.getByText(/Заполните это поле/).first().waitFor();
    const dir = path.join(path.dirname(original), "Моя папка " + crypto.randomBytes(2).toString("hex"));
    await input.fill(dir);
    await page.getByRole("button", { name: /Сохранить/ }).first().click();
    await until(async () => (await dev.laptop.api("GET", "/api/settings")).downloadDir === dir, 5000, "the new folder on the backend");
    // the next received file lands there
    const lap = (await dev.laptop.api("GET", "/api/state")).self;
    const sent = await dev["home-server"].api("POST", `/api/transfers?to=${lap.id}&name=where.txt&mime=text/plain`, new TextEncoder().encode("here"), { headers: { "Content-Type": "application/octet-stream" } });
    const done = await until(async () => (await dev.laptop.api("GET", "/api/transfers")).find((t) => t.id === sent.transfers[0].id && t.state === "done"), 15000, "the file to arrive");
    eq(path.dirname(done.path), dir, "the file was stored in the new folder");
    assert(fs.readFileSync(done.path, "utf8") === "here", "content");
    eq(page.problems, [], "console / network problems");
  });

  test("language and theme switch instantly and are remembered", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { hash: "settings/interface" });
    await tid(page, "lang-en").click();
    await page.waitForFunction(() => /Devices/.test((document.querySelector('[data-testid="nav-devices"]')?.innerText ?? "")));
    eq(await page.evaluate(() => localStorage.getItem("themesh.lang")), "en", "stored language");
    await tid(page, "theme-light").click();
    await page.waitForFunction(() => document.documentElement.dataset.theme === "light" || document.documentElement.classList.contains("light") || getComputedStyle(document.body).backgroundColor === "rgb(255, 255, 255)" || window.matchMedia("(prefers-color-scheme: light)").matches === false);
    await page.reload();
    await page.waitForFunction(() => /Devices/.test((document.querySelector('[data-testid="nav-devices"]')?.innerText ?? "")));
    const bg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    assert(bg !== "rgb(11, 15, 18)", "the light theme survived the reload (body background " + bg + ")");
    await tid(page, "lang-ru").click();
    await page.waitForFunction(() => /Устройства/.test((document.querySelector('[data-testid="nav-devices"]')?.innerText ?? "")));
    eq(page.problems, [], "console / network problems");
  });

  test("the network section re-runs the NAT check; the interface section shows its state", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { hash: "settings/network" });
    await tid(page, "netcheck").click();
    await page.waitForFunction(() => !document.querySelector('[data-testid="netcheck"]').disabled, null, { timeout: 15000 });
    await tid(page, "nat-chip").first().waitFor();
    await nav(page, dev.laptop, "settings/tun");
    await tid(page, "tun-section").waitFor();
    eq(await tid(page, "tun-state").getAttribute("data-state"), "off", "the virtual interface is off by default");
    eq(page.problems, [], "console / network problems");
  });

  test("an administrator can open another device's settings and change them there", async ({ browser, dev }) => {
    const nas = await peerByName(dev, "nas");
    const page = await open(browser, dev.laptop, { hash: `settings/network?d=${nas.id}` });
    const sw = tid(page, "setting-relay");
    await sw.waitFor();
    await sw.click();
    await until(async () => (await dev.nas.api("GET", "/api/settings")).relay === false, 8000, "the NAS to stop relaying");
    eq((await dev.laptop.api("GET", "/api/settings")).relay, true, "this device is unaffected");
    await sw.click();
    await until(async () => (await dev.nas.api("GET", "/api/settings")).relay === true, 8000, "the NAS to relay again");
    // the page says whose settings these are
    assert(/nas/.test(await page.locator(".settings").innerText()), "the page names the managed device");
    eq(page.problems, [], "console / network problems");
  });

  test("the log viewer shows the node's log", async ({ browser, dev }) => {
    const page = await open(browser, dev.laptop, { hash: "settings/about" });
    await page.getByRole("button", { name: /журнал/i }).first().click();
    await page.waitForFunction(() => document.querySelectorAll(".logline").length > 5, null, { timeout: 10000 });
    const text = await page.locator(".logs__box").innerText();
    assert(/peer|magic|mesh/i.test(text), "the log shows mesh activity: " + text.slice(0, 120));
    // the level filter works
    await page.getByRole("radio", { name: /Ошибки|Error/i }).click();
    await page.waitForFunction(() => document.querySelectorAll(".logline--debug, .logline--info").length === 0);
    eq(page.problems, [], "console / network problems");
  });
});
