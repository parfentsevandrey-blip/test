import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { group, test, assert, eq, until, open, tid, startNode, stopNodes } from "../lib.mjs";

// "Nearby" with two real programs and the real interface (no mock): every device with the app open is listed — one in a
// mesh that can add this one, and one that is in no mesh yet — and one tap connects: asking to be added (the person there
// says yes), inviting (the person there accepts), or two new devices making a mesh together. Nobody types or scans a code;
// both screens only show the same six digits. The two programs run on this machine the way the phone app runs its
// program: with the interfaces hidden (Android 11+ does not let a program list them), the networks come from a file, and
// the program says what system it runs on. Both share one address and one beacon port, so what is checked is the path
// "by address" that a phone takes (and an invitation, which is meant for one device, reaches it there too).
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

// (the programs of one test must be gone when the next starts: a phone and a Mac left over from another test are devices nearby too,
// with the same names, and "the first device named pixel" would be one of them — a request to a program no page is looking at)
group("nearby (real processes)", { noDemo: true }, () => {
  test("a phone that is in no mesh finds a Mac nearby by itself, connects in one tap, and the person at the Mac allows", async ({ browser }) => {
    await stopNodes();
    const file = networkFile();
    const mac = await startNode({ name: "mac", init: true, mesh: "Дом Мака", owner: "Андрей", env: macEnv(file) });
    const phone = await startNode({ name: "pixel", offerName: "Pixel 8", env: phoneEnv(file) });
    const page = await open(browser, phone, { mobile: true, w: 390, h: 844, userAgent: ANDROID });
    await tid(page, "page-onboarding").waitFor();

    // the Mac appears in the list by itself, with the name of its mesh
    const row = page.locator('[data-testid="nearby-device"][data-name="mac"]');
    await row.waitFor(SLOW);
    assert(/Дом Мака/.test(await row.innerText()), "the row names the Mac's mesh: " + (await row.innerText()));
    eq(await row.getAttribute("data-kind"), "mesh", "a device that can add this one");

    // one tap: nothing more to confirm on the phone; both screens show the same six digits
    await row.getByTestId("nearby-connect").click();
    const join = page.locator('[data-testid="nearby-join"][data-state="confirmed"]');
    await join.waitFor(SLOW);
    eq(await page.locator('[data-testid="nearby-match"]').count(), 0, "no «they match» to press");
    const code = await digits(page.locator('[data-testid="nearby-code"] .nearby-code__digits'));
    assert(/^\d{6}$/.test(code), "six digits: " + code);
    const ask = await until(async () => (await mac.api("GET", "/api/nearby")).requests[0], 20000, "the Mac to see the request");
    eq([ask.code, ask.confirmed, ask.name], [code, true, "pixel-8"], "the Mac sees the same digits, and the phone goes on by itself");

    // the person at the Mac allows: the phone is in the mesh, an ordinary device
    await mac.api("POST", `/api/nearby/requests/${ask.id}`, { approve: true, owner: "Андрей" });
    await tid(page, "page-home").waitFor(SLOW);
    const st = await phone.api("GET", "/api/state");
    eq([st.configured, st.self.meshName, st.self.admin, st.self.owner, st.self.name], [true, "Дом Мака", false, "Андрей", "pixel-8"], "what the phone became");
    await until(async () => (await phone.api("GET", "/api/state")).peers.some((p) => p.name === "mac" && p.online), 30000, "the phone to be connected to the Mac");
    const seen = (await mac.api("GET", "/api/state")).peers.find((p) => p.name === "pixel-8");
    assert(seen, "the Mac lists the phone");
    eq([seen.os, seen.admin], ["android", false], "the Mac knows it is an Android phone and an ordinary device");
    // the phone's overview: the Mac is a drive of the file manager
    await page.locator('[data-testid="home-device"][data-name="mac"]').waitFor(SLOW);
    eq(page.problems, [], "console / network problems");
  });

  test("a phone with a mesh sees a new Mac nearby and adds it in one tap; the Mac accepts the invitation", async ({ browser }) => {
    await stopNodes();
    const file = networkFile();
    const phone = await startNode({ name: "pixel", init: true, mesh: "Дом", owner: "Андрей", env: phoneEnv(file) });
    const mac = await startNode({ name: "mac", offerName: "mac", env: macEnv(file) });
    const page = await open(browser, phone, { mobile: true, w: 390, h: 844, userAgent: ANDROID });
    await tid(page, "page-home").waitFor();
    // the person at the Mac looks at its list: only then does a device that is in no mesh show its name nearby
    await mac.api("POST", "/api/nearby/look", { name: "mac" });
    const row = page.locator('[data-testid="nearby-device"][data-name="mac"]');
    await row.waitFor(SLOW);
    eq(await row.getAttribute("data-kind"), "new", "a device that is in no mesh");
    assert(/ещё не в сети/.test(await row.innerText()), "the row says so: " + (await row.innerText()));
    await row.getByTestId("nearby-connect").click();
    await row.locator('[data-testid="nearby-sent"][data-state="sent"]').waitFor();

    // the Mac gets the invitation, names the mesh, and its person accepts
    const inv = await until(async () => (await mac.api("GET", "/api/nearby")).invites.find((i) => i.name === "pixel"), 30000, "the invitation to reach the Mac");
    eq([inv.kind, inv.meshName], ["invite", "Дом"], "an invitation to the phone's mesh");
    await mac.api("POST", `/api/nearby/invites/${inv.id}`, { accept: true, deviceName: "mac" });
    await until(async () => (await mac.api("GET", "/api/nearby")).join.state === "joined", 30000, "the Mac to be added");
    eq((await phone.api("GET", "/api/nearby")).requests, [], "nobody at the phone was asked: its person invited the Mac");
    await page.locator('[data-testid="nearby-sent"][data-state="joined"]').waitFor(SLOW);
    const m = await until(async () => (await phone.api("GET", "/api/state")).peers.find((p) => p.name === "mac"), 20000, "the phone to list the Mac");
    eq(m.admin, false, "an ordinary device, never an administrator");
    await until(async () => (await phone.api("GET", "/api/state")).peers.some((p) => p.name === "mac" && p.online), 30000, "the phone to be connected to the Mac");
    await page.locator('[data-testid="home-device"][data-name="mac"]').waitFor(SLOW);
    eq(page.problems, [], "console / network problems");
  });

  test("two devices that are in no mesh: the phone asks in one tap, the Mac accepts and makes a mesh, the phone joins it by itself", async ({ browser }) => {
    await stopNodes();
    const file = networkFile();
    const phone = await startNode({ name: "pixel", offerName: "Pixel 8", env: phoneEnv(file) });
    const mac = await startNode({ name: "mac", offerName: "mac", env: macEnv(file) });
    const page = await open(browser, phone, { mobile: true, w: 390, h: 844, userAgent: ANDROID });
    await tid(page, "page-onboarding").waitFor();
    await mac.api("POST", "/api/nearby/look", { name: "mac" });
    const row = page.locator('[data-testid="nearby-device"][data-name="mac"]');
    await row.waitFor(SLOW);
    eq(await row.getAttribute("data-kind"), "new", "the Mac is in no mesh either");
    await row.getByTestId("nearby-connect").click();
    await row.locator('[data-testid="nearby-sent"][data-state="sent"]').waitFor();
    const inv = await until(async () => (await mac.api("GET", "/api/nearby")).invites.find((i) => i.name === "pixel-8"), 30000, "the Mac to be asked");
    eq([inv.kind, inv.meshName || ""], ["pair", ""], "asked to make a mesh together");
    await mac.api("POST", `/api/nearby/invites/${inv.id}`, { accept: true, meshName: "Дача", deviceName: "mac" });
    await tid(page, "page-home").waitFor(SLOW);
    const st = await phone.api("GET", "/api/state");
    eq([st.self.meshName, st.self.admin], ["Дача", false], "the phone joined the mesh the Mac made");
    eq((await mac.api("GET", "/api/state")).self.admin, true, "the Mac made it: it is its administrator");
    await until(async () => (await phone.api("GET", "/api/state")).peers.some((p) => p.name === "mac" && p.online), 30000, "the two to be connected");
    eq(page.problems, [], "console / network problems");
  });

  test("an invitation reaches the phone's start screen; one tap accepts it", async ({ browser }) => {
    await stopNodes();
    const file = networkFile();
    const mac = await startNode({ name: "mac", init: true, mesh: "Дом Мака", owner: "Андрей", env: macEnv(file) });
    const phone = await startNode({ name: "pixel", offerName: "Pixel 8", env: phoneEnv(file) });
    const page = await open(browser, phone, { mobile: true, w: 390, h: 844, userAgent: ANDROID });
    await tid(page, "page-onboarding").waitFor(); // (the start screen says that the person looks: the phone shows its name nearby)
    const found = await until(async () => (await mac.api("GET", "/api/nearby")).devices.find((d) => d.name === "pixel-8"), 45000, "the Mac to see the phone");
    eq(found.kind, "new", "the phone is in no mesh");
    await mac.api("POST", "/api/nearby/connect", { id: found.id });
    const inv = page.locator('[data-testid="nearby-invite"][data-name="mac"]');
    await inv.waitFor(SLOW);
    assert(/приглашает вас в сеть «Дом Мака»/.test(await inv.innerText()), "the invitation names the mesh: " + (await inv.innerText()));
    await inv.getByTestId("nearby-accept").click();
    await tid(page, "page-home").waitFor(SLOW);
    eq((await phone.api("GET", "/api/state")).self.meshName, "Дом Мака", "the phone is in the Mac's mesh");
    eq((await mac.api("GET", "/api/nearby")).requests, [], "nobody at the Mac was asked: its person invited the phone");
    eq(page.problems, [], "console / network problems");
  });

  test("a new Mac asks a phone that has a mesh; the phone opens a window with the same six digits, and its owner allows", async ({ browser }) => {
    await stopNodes();
    const file = networkFile();
    const phone = await startNode({ name: "pixel", init: true, mesh: "Дом", owner: "Андрей", env: phoneEnv(file) });
    const mac = await startNode({ name: "mac", offerName: "mac", env: macEnv(file) });
    const page = await open(browser, phone, { mobile: true, w: 390, h: 844, userAgent: ANDROID });
    await tid(page, "page-home").waitFor();

    // the Mac (it has no mesh) finds the phone
    const found = await until(async () => (await mac.api("GET", "/api/nearby")).devices.find((d) => d.name === "pixel"), 45000, "the Mac to find the phone");
    eq([found.meshName, found.os, found.kind], ["Дом", "android", "mesh"], "what the Mac learns about the phone");
    await mac.api("POST", "/api/nearby/connect", { id: found.id, deviceName: "mac" });
    const waiting = await until(async () => { const j = (await mac.api("GET", "/api/nearby")).join; return j.code && j; }, 30000, "the Mac to show its six digits");

    // the phone opens a window by itself, with the same digits and the Mac's name
    const ask = tid(page, "nearby-ask");
    await ask.waitFor(SLOW);
    eq(await digits(page.locator('[data-testid="nearby-ask-code"] .nearby-code__digits')), waiting.code, "the same six digits at both devices");
    eq((await tid(page, "nearby-ask-name").innerText()).trim(), "mac", "the window names the device");
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
    eq((await phone.api("GET", "/api/nearby")).requests, [], "the node forgot the request");
    await page.locator('[data-testid="home-att-nearby"]').waitFor({ state: "detached" });
    eq(page.problems, [], "console / network problems");
  });

  test("a refusal reaches the new device, and the request is gone from the phone", async ({ browser }) => {
    await stopNodes();
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
    await page.locator('[data-testid="home-att-nearby"]').waitFor({ state: "detached" });
    eq((await phone.api("GET", "/api/nearby")).requests, [], "the phone keeps no request");
    eq((await mac.api("GET", "/api/state")).configured, false, "the new device is still in no mesh");
    eq(page.problems, [], "console / network problems");
  });
});
