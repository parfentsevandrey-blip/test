import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { group, test, assert, eq, until, open, tid, startNode } from "../lib.mjs";

// "Nearby" with two real programs and the real interface (no mock): a device with the app finds another one that can add it,
// shows it in a list, connects in one tap, and both people confirm the same six digits. The two programs run on this
// machine the way the phone app runs its program: with the interfaces hidden (Android 11+ does not let a program list
// them), the networks come from a file, and the program says what system it runs on. Both share one address and one
// beacon port, so what is checked is the path "by address" that a phone takes.
const ANDROID = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36 TheMeshAndroid/0.1.0 (android; skin=glass)";

/** The first network this machine has (a program that may not list its interfaces learns it from a file like this one). */
function networkFile() {
  for (const list of Object.values(os.networkInterfaces())) {
    for (const a of list || []) {
      if (a.family === "IPv4" && !a.internal && a.cidr) {
        const file = path.join(fs.mkdtempSync(path.join(os.tmpdir(), "themesh-e2e-net-")), "local-addrs.txt");
        fs.writeFileSync(file, a.cidr + "\n");
        return file;
      }
    }
  }
  throw new Error("this machine has no network (an interface with an IPv4 address that is not loopback): devices nearby need one");
}

const phoneEnv = (file) => ({ THEMESH_HIDE_INTERFACES: "1", THEMESH_LOCAL_ADDRS_FILE: file, THEMESH_PLATFORM: "android/arm64" });
const macEnv = (file) => ({ THEMESH_HIDE_INTERFACES: "1", THEMESH_LOCAL_ADDRS_FILE: file, THEMESH_PLATFORM: "darwin/arm64" });
const digits = (el) => el.getAttribute("data-code");
const SLOW = { timeout: 45000 };

group("nearby (real processes)", { noDemo: true }, () => {
  test("a phone that is in no mesh finds a Mac nearby by itself, connects in one tap, and both sides confirm the same six digits", async ({ browser }) => {
    const file = networkFile();
    const mac = await startNode({ name: "mac", init: true, mesh: "Дом Мака", owner: "Андрей", env: macEnv(file) });
    const phone = await startNode({ name: "pixel", offerName: "Pixel 8", env: phoneEnv(file) });
    const page = await open(browser, phone, { mobile: true, w: 390, h: 844, userAgent: ANDROID });
    await tid(page, "page-onboarding").waitFor();

    // the Mac appears in the list by itself, with the name of its mesh
    const row = page.locator('[data-testid="nearby-device"][data-name="mac"]');
    await row.waitFor(SLOW);
    assert(/Дом Мака/.test(await row.innerText()), "the row names the Mac's mesh: " + (await row.innerText()));
    eq(await tid(page, "nearby").getAttribute("data-found"), "1", "one device nearby");

    // one tap: the six digits appear, and the Mac sees the same ones
    await tid(page, "nearby-connect").click();
    const join = page.locator('[data-testid="nearby-join"][data-state="waiting"]');
    await join.waitFor(SLOW);
    const code = await digits(page.locator('[data-testid="nearby-code"] .nearby-code__digits'));
    assert(/^\d{6}$/.test(code), "six digits: " + code);
    const ask = await until(async () => (await mac.api("GET", "/api/nearby")).requests[0], 20000, "the Mac to see the request");
    eq([ask.code, ask.confirmed, ask.name], [code, false, "pixel-8"], "the Mac sees the same digits, nobody has confirmed there yet");

    // the person at the phone says "the same" and waits for the Mac's person
    await tid(page, "nearby-match").click();
    await page.locator('[data-testid="nearby-join"][data-state="confirmed"]').waitFor();
    await until(async () => (await mac.api("GET", "/api/nearby")).requests[0].confirmed, 20000, "the Mac to learn that the phone confirmed");

    // the person at the Mac allows: the phone is in the mesh, an ordinary device
    await mac.api("POST", `/api/nearby/requests/${ask.id}`, { approve: true, owner: "Андрей" });
    await tid(page, "page-home").waitFor(SLOW);
    const st = await phone.api("GET", "/api/state");
    eq([st.configured, st.self.meshName, st.self.admin, st.self.owner, st.self.name], [true, "Дом Мака", false, "Андрей", "pixel-8"], "what the phone became");
    await until(async () => (await phone.api("GET", "/api/state")).peers.some((p) => p.name === "mac" && p.online), 30000, "the phone to be connected to the Mac");
    const seen = (await mac.api("GET", "/api/state")).peers.find((p) => p.name === "pixel-8");
    assert(seen, "the Mac lists the phone");
    eq([seen.os, seen.admin], ["android", false], "the Mac knows it is an Android phone and an ordinary device");
    eq(page.problems, [], "console / network problems");
  });

  test("a new Mac finds a phone that has a mesh; the phone opens a window with the same six digits, and its owner allows", async ({ browser }) => {
    const file = networkFile();
    const phone = await startNode({ name: "pixel", init: true, mesh: "Дом", owner: "Андрей", env: phoneEnv(file) });
    const mac = await startNode({ name: "mac", offerName: "mac", env: macEnv(file) });
    const page = await open(browser, phone, { mobile: true, w: 390, h: 844, userAgent: ANDROID });
    await tid(page, "page-home").waitFor();

    // the Mac (it has no mesh) finds the phone
    const found = await until(async () => (await mac.api("GET", "/api/nearby")).devices.find((d) => d.name === "pixel"), 45000, "the Mac to find the phone");
    eq([found.meshName, found.os], ["Дом", "android"], "what the Mac learns about the phone");
    await mac.api("POST", "/api/nearby/connect", { id: found.id, deviceName: "mac" });
    const waiting = await until(async () => { const j = (await mac.api("GET", "/api/nearby")).join; return j.state === "waiting" && j; }, 30000, "the Mac to show its six digits");

    // the phone opens a window by itself, with the same digits and the Mac's name
    const ask = tid(page, "nearby-ask");
    await ask.waitFor(SLOW);
    eq(await digits(page.locator('[data-testid="nearby-ask-code"] .nearby-code__digits')), waiting.code, "the same six digits at both devices");
    eq((await tid(page, "nearby-ask-name").innerText()).trim(), "mac", "the window names the device");
    eq(await tid(page, "nearby-ask-state").getAttribute("data-confirmed"), "false", "nobody at the Mac has confirmed yet");

    // the person at the Mac says "the same": the phone's window learns it; the owner allows
    await mac.api("POST", "/api/nearby/confirm", {});
    await page.locator('[data-testid="nearby-ask-state"][data-confirmed="true"]').waitFor();
    await tid(page, "nearby-allow").click();
    await until(async () => (await mac.api("GET", "/api/nearby")).join.state === "joined", 30000, "the Mac to be added");

    const st = await phone.api("GET", "/api/state");
    const m = st.peers.find((p) => p.name === "mac");
    assert(m, "the phone lists the Mac");
    eq(m.admin, false, "an ordinary device, never an administrator");
    await until(async () => (await phone.api("GET", "/api/state")).peers.some((p) => p.name === "mac" && p.online), 30000, "the phone to be connected to the Mac");
    eq((await mac.api("GET", "/api/state")).self.meshName, "Дом", "the Mac is in the phone's mesh");
    await tid(page, "nearby-ask").waitFor({ state: "detached" });
    eq(page.problems, [], "console / network problems");
  });

  test("a refusal reaches the new device, and the request is gone from the phone", async ({ browser }) => {
    const file = networkFile();
    const phone = await startNode({ name: "pixel", init: true, mesh: "Дом", owner: "Андрей", env: phoneEnv(file) });
    const mac = await startNode({ name: "mac", offerName: "mac", env: macEnv(file) });
    const page = await open(browser, phone, { mobile: true, w: 390, h: 844, userAgent: ANDROID });
    await tid(page, "page-home").waitFor();
    const found = await until(async () => (await mac.api("GET", "/api/nearby")).devices.find((d) => d.name === "pixel"), 45000, "the Mac to find the phone");
    await mac.api("POST", "/api/nearby/connect", { id: found.id, deviceName: "mac" });
    await tid(page, "nearby-ask").waitFor(SLOW);
    await tid(page, "nearby-deny").click();
    const j = await until(async () => { const v = (await mac.api("GET", "/api/nearby")).join; return v.state === "denied" && v; }, 30000, "the Mac to be told no");
    eq(j.state, "denied", "the new device learns that it was refused");
    await tid(page, "nearby-ask").waitFor({ state: "detached" });
    eq((await phone.api("GET", "/api/nearby")).requests, [], "the phone keeps no request");
    eq((await mac.api("GET", "/api/state")).configured, false, "the new device is still in no mesh");
    eq(page.problems, [], "console / network problems");
  });
});
