#!/usr/bin/env node
// A REAL run of a release binary, nothing mocked or simulated: two separate `svoi` processes,
// each with its own HOME like two computers, set up and used only through the web interface in a
// real browser — create a network, add the second device with an invitation code, send files,
// chat, send mail, switch one device off and on again. The screenshots are of that real interface;
// the checks compare real bytes (sha256) on the receiving side.
//
//   node web-e2e/tour.mjs --bin /path/to/svoi [--out docs/img/real] [--theme light|dark]
//
// Needs Node 22+ and Playwright with a Chromium (see web-e2e/run.mjs). Exit code 0 only if every check holds.
import crypto from "node:crypto";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import zlib from "node:zlib";
import { spawn } from "node:child_process";
import { chromium, config, root, until, tid, open, nav } from "./lib.mjs";

const args = process.argv.slice(2);
const opt = (name, def) => {
  const i = args.indexOf("--" + name);
  return i >= 0 ? (args[i + 1] && !args[i + 1].startsWith("--") ? args[i + 1] : true) : def;
};
if (opt("bin", false)) config.bin = path.resolve(opt("bin"));
const OUT = path.resolve(opt("out", path.join(root, "docs", "img", "real")));
const THEME = opt("theme", "light");
const DEBUG = !!opt("debug", false); // verbose node logs (shown when something fails)
fs.mkdirSync(OUT, { recursive: true });
if (!fs.existsSync(config.bin)) throw new Error("no binary at " + config.bin + " (use --bin)");

const sha = (b) => crypto.createHash("sha256").update(b).digest("hex");
const checks = [];
const check = (what, ok, detail = "") => {
  checks.push({ what, ok: !!ok, detail });
  console.log(`  ${ok ? "✓" : "✗"} ${what}${detail ? "  — " + detail : ""}`);
};
const step = (s) => console.log("\n" + s);

// ------------------------------------------------------------------ a small real picture
function makePng(w = 960, h = 600) {
  const row = w * 3 + 1;
  const raw = Buffer.alloc(row * h);
  const lerp = (a, b, t) => Math.round(a + (b - a) * t);
  const hy = h * 0.62;
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      let c;
      if (y < hy) {
        const t = y / hy;
        c = [lerp(30, 255, t), lerp(40, 150, t), lerp(110, 70, t)];
      } else {
        const t = (y - hy) / (h - hy);
        c = [lerp(150, 10, t), lerp(80, 20, t), lerp(90, 50, t)];
      }
      if (Math.hypot(x - w * 0.5, y - hy) < h * 0.13) c = [255, 238, 170];
      const o = y * row + 1 + x * 3;
      raw[o] = c[0], raw[o + 1] = c[1], raw[o + 2] = c[2];
    }
  }
  const chunk = (type, data) => {
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length);
    const td = Buffer.concat([Buffer.from(type), data]);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(zlib.crc32(td));
    return Buffer.concat([len, td, crc]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 2; // truecolor
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw, { level: 9 })), chunk("IEND", Buffer.alloc(0))]);
}

// ------------------------------------------------------------------ a real node process
// Started the way a person starts it: `svoi up`, a fresh HOME, nothing else from this shell.
function launch(name, uiAddr) {
  const home = fs.mkdtempSync(path.join(os.tmpdir(), `svoi-tour-${name}-`));
  const env = { PATH: "/usr/bin:/bin", HOME: home, LANG: "C.UTF-8" };
  const data = path.join(home, ".config", "svoi");
  const node = { name, home, data, origin: "http://" + (uiAddr || "127.0.0.1:8777"), token: "", proc: null };
  const spawnIt = () => {
    const log = fs.openSync(path.join(home, "svoi.log"), "a");
    node.proc = spawn(config.bin, ["up", "--no-browser", ...(uiAddr ? ["--ui", uiAddr] : []), ...(DEBUG ? ["--debug"] : [])], { env, stdio: ["ignore", log, log], detached: true });
    return until(async () => (await fetch(node.origin + "/api/handshake?n=" + "0".repeat(16)).then((r) => r.ok, () => false)), 30000, `${name} to start`).then(() => {
      node.token = fs.readFileSync(path.join(data, "ui.token"), "utf8").trim();
    });
  };
  node.start = spawnIt;
  node.api = async (method, p, body, o = {}) => {
    const res = await fetch(node.origin + p, { method, headers: { Authorization: "Bearer " + node.token, "X-Svoi": "1", ...(body !== undefined && !(body instanceof Uint8Array) ? { "Content-Type": "application/json" } : {}), ...(o.headers || {}) }, body: body === undefined ? undefined : body instanceof Uint8Array ? body : JSON.stringify(body) });
    if (o.raw) return res;
    const text = await res.text();
    if (!res.ok) throw new Error(`${method} ${p} → ${res.status} ${text.slice(0, 200)}`);
    return text ? JSON.parse(text) : null;
  };
  node.state = () => node.api("GET", "/api/state");
  node.log = () => fs.readFileSync(path.join(home, "svoi.log"), "utf8");
  node.stop = async () => {
    const p = node.proc;
    if (!p || p.exitCode !== null || p.signalCode !== null) return;
    try { process.kill(-p.pid, "SIGINT"); } catch {}
    await new Promise((r) => { const t = setTimeout(() => { try { process.kill(-p.pid, "SIGKILL"); } catch {} r(); }, 10000); p.once("exit", () => { clearTimeout(t); r(); }); });
  };
  return node;
}

const A = launch("laptop", "");
const B = launch("home-server", "127.0.0.1:8778");
let browser;
const pages = [];
// A screenshot as a person would see it: the page at the top, no stray focus ring, notifications closed.
// Long pages are taken on a taller screen (`h`), not stitched: the side navigation stays one screen high.
const shot = async (page, name, { h } = {}) => {
  const vp = page.viewportSize();
  if (h && vp && vp.height !== h) await page.setViewportSize({ width: vp.width, height: h });
  await page.evaluate(() => {
    document.querySelectorAll('[data-testid="toast"] .toast__close').forEach((b) => b.click());
    if (document.activeElement && document.activeElement !== document.body) document.activeElement.blur();
    window.scrollTo(0, 0);
  });
  await page.waitForTimeout(400);
  await page.screenshot({ path: path.join(OUT, name + ".png") });
  if (h && vp && vp.height !== h) await page.setViewportSize(vp);
  console.log(`  📷 ${name}.png`);
};
const openPage = async (dev, o = {}) => {
  const p = await open(browser, dev, { theme: THEME, ...o });
  pages.push([dev.name, p]);
  return p;
};
const peerOf = async (dev, name) => (await dev.state()).peers.find((p) => p.deviceName === name);

try {
  console.log(`binary: ${config.bin}\nscreenshots: ${OUT}`);
  await A.start();
  await B.start();
  const s0 = await A.state();
  const ver = (s0.self && s0.self.version) || s0.version || "";
  step(`1. Two separate processes started (version ${ver || "?"}), each in its own fresh home directory`);
  check("A serves the interface on the default address", A.origin === "http://127.0.0.1:8777", A.origin);
  check("both start unconfigured", (await A.state()).configured === false && (await B.state()).configured === false);
  check("the private key of each device is created on first start and readable by the owner only", ["device.key", "ui.token"].every((f) => (fs.statSync(path.join(A.data, f)).mode & 0o077) === 0), "mode 0600");

  browser = await chromium.launch({ headless: true, args: ["--no-sandbox", "--no-proxy-server"] });

  step("2. First device: create the network in the browser");
  const pa = await openPage(A, { w: 1280, h: 800 });
  await tid(pa, "page-onboarding").waitFor();
  await shot(pa, "01-first-start", { h: 1040 });
  await tid(pa, "onb-create").click();
  await tid(pa, "onb-mesh-name").fill("Наша семья");
  await tid(pa, "onb-device-name").fill("laptop");
  await tid(pa, "onb-owner").fill("Мария");
  await shot(pa, "02-create-network", { h: 1040 });
  await tid(pa, "onb-submit").click();
  await tid(pa, "page-home").waitFor();
  await pa.locator('[data-testid="home-status"][data-state="alone"]').waitFor();
  await shot(pa, "03-home-alone", { h: 1040 });
  const sa = await A.state();
  check("network created", sa.configured && sa.self.meshName === "Наша семья" && sa.self.admin, `${sa.self.name}, admin`);

  step("3. Add the second device: one-time invitation code");
  await tid(pa, "home-action-add").click();
  await tid(pa, "invite-create").click();
  await tid(pa, "invite-qr").locator("img").waitFor();
  const code = (await tid(pa, "invite-code").innerText()).trim();
  check("the invitation is a one-time code", /^SVOI1-/.test(code), code.slice(0, 14) + "…");
  await shot(pa, "04-invitation");

  step("4. Second device (another process, another home directory): join with the code");
  const pb = await openPage(B, { w: 1280, h: 800 });
  await tid(pb, "page-onboarding").waitFor();
  await tid(pb, "onb-join").click();
  await tid(pb, "onb-code").fill(code);
  await tid(pb, "onb-device-name").fill("home-server");
  await shot(pb, "05-join-with-code", { h: 1040 });
  await tid(pb, "onb-submit").click();
  await tid(pb, "page-home").waitFor({ timeout: 60000 });
  await pb.waitForFunction(() => document.querySelectorAll('[data-testid="home-device"]').length === 1, null, { timeout: 30000 });
  await tid(pa, "invite-done").waitFor({ timeout: 30000 });
  await shot(pa, "06-invitation-used");
  await pa.keyboard.press("Escape");
  await until(async () => (await peerOf(A, "home-server"))?.online && (await peerOf(B, "laptop"))?.online, 30000, "both devices to see each other online");
  const pab = await peerOf(A, "home-server");
  const pba = await peerOf(B, "laptop");
  check("each device sees the other online", pab.online && pba.online);
  check("the link is a direct encrypted one (no relay)", pab.path !== "relay" && pba.path !== "relay", `path=${pab.path}, round trip ${pab.rttMs.toFixed(1)} ms`);
  check("the one-time invitation was used up", (await A.api("GET", "/api/invites")).length === 0, "no open invitations left on the laptop");
  await pa.locator('[data-testid="home-status"][data-state="ok"]').waitFor({ timeout: 20000 });
  await pb.locator('[data-testid="home-status"][data-state="ok"]').waitFor({ timeout: 20000 });
  await shot(pa, "07-home-laptop", { h: 1040 });
  await shot(pb, "08-home-server", { h: 1040 });

  step("5. Send files from the laptop to the home server (the interface, a real picture and 8 MB of random bytes)");
  const picture = makePng();
  const blob = crypto.randomBytes(8 * 1024 * 1024);
  await nav(pa, A, `files/send?to=${pab.id}`);
  await tid(pa, "dropzone-input").setInputFiles([
    { name: "закат.png", mimeType: "image/png", buffer: picture },
    { name: "архив.bin", mimeType: "application/octet-stream", buffer: blob },
  ]);
  await tid(pa, "staged-file").first().waitFor();
  await shot(pa, "09-send-files");
  await tid(pa, "send-submit").click();
  const got = {};
  for (const [name, data] of [["закат.png", picture], ["архив.bin", blob]]) {
    const t = await until(async () => (await B.api("GET", "/api/transfers")).find((x) => x.name === name && x.dir === "in" && x.state === "done"), 60000, `${name} to arrive`);
    got[name] = t;
    check(`${name}: the bytes on the receiving device are identical`, sha(fs.readFileSync(t.path)) === sha(data), `${(data.length / 1048576).toFixed(1)} MB, sha256 ${sha(data).slice(0, 12)}…`);
  }
  await pa.locator('[data-testid="transfer"][data-dir="out"][data-state="done"]').nth(1).waitFor({ timeout: 30000 });
  await shot(pa, "10-files-sent");
  await nav(pb, B, "files/send");
  await pb.locator('[data-testid="transfer"][data-dir="in"][data-state="done"]').nth(1).waitFor({ timeout: 30000 });
  await shot(pb, "11-files-received");

  step("6. Chat in both directions");
  await nav(pb, B, `chat/${pba.id}`);
  await tid(pb, "conversation").waitFor();
  const hello = "Привет! Это настоящий второй процесс — файлы дошли.";
  await tid(pb, "chat-input").click();
  await pb.keyboard.type(hello);
  await pb.keyboard.press("Enter");
  await pb.locator('[data-testid="bubble"][data-mine="true"][data-state="delivered"]', { hasText: "настоящий второй" }).waitFor({ timeout: 20000 });
  await nav(pa, A, `chat/${pab.id}`);
  await pa.locator('[data-testid="bubble"][data-mine="false"]', { hasText: "настоящий второй" }).waitFor({ timeout: 20000 });
  const reply = "Вижу, спасибо! Закат получился красивый.";
  await tid(pa, "chat-input").click();
  await pa.keyboard.type(reply);
  await pa.keyboard.press("Enter");
  await pb.locator('[data-testid="bubble"][data-mine="false"]', { hasText: "Закат получился" }).waitFor({ timeout: 20000 });
  check("the message and the reply were delivered through the interface", true);
  await shot(pa, "12-chat");

  step("7. Mail with an attachment");
  const report = crypto.randomBytes(300 * 1024);
  await nav(pa, A, "mail/inbox");
  await tid(pa, "mail-compose").first().click();
  await tid(pa, "compose-subject").waitFor();
  await pa.locator('[data-testid="device-chip"][data-name="home-server"]').click();
  await tid(pa, "compose-subject").fill("Квартальный отчёт");
  await tid(pa, "compose-body").fill("Добрый день! Цифры за квартал — во вложении.\nМария");
  await tid(pa, "compose-files").setInputFiles({ name: "цифры.bin", mimeType: "application/octet-stream", buffer: report });
  await pa.locator('[data-testid="attachment"][data-status="ready"], [data-testid="attachment"][data-status="done"]').first().waitFor();
  await tid(pa, "compose-send").click();
  await tid(pa, "compose-send").waitFor({ state: "detached" });
  const mail = await until(async () => (await B.api("GET", "/api/mail?folder=inbox&q=" + encodeURIComponent("Квартальный"))).items[0], 30000, "the mail to arrive");
  const bytes = await until(async () => {
    const r = await B.api("GET", `/api/mail/${mail.id}/attachments/0`, undefined, { raw: true });
    return r.status === 200 ? Buffer.from(await r.arrayBuffer()) : null;
  }, 30000, "the attachment");
  check("the mail arrived and its attachment is byte-identical", sha(bytes) === sha(report), `300 KB, sha256 ${sha(report).slice(0, 12)}…`);
  await nav(pb, B, "mail/inbox");
  await pb.locator('[data-testid="mail-item"]', { hasText: "Квартальный" }).click();
  await tid(pb, "mail-reader").waitFor();
  await shot(pb, "13-mail");

  step("8. The home server is switched off; a message written meanwhile waits and arrives when it is back");
  await B.stop();
  check("the process shut down cleanly", /Выход|shutting down/.test(B.log()));
  await nav(pa, A, "home");
  await pa.locator('[data-testid="home-status"]:not([data-state="ok"])').waitFor({ timeout: 60000 });
  const offText = await tid(pa, "home-status").innerText();
  check("the home screen names the device that is off and says its mail will wait", /home-server/.test(offText) && /подождут/.test(offText) && !/для них/.test(offText), offText.replace(/\s+/g, " "));
  await shot(pa, "14-device-switched-off", { h: 1040 });
  const later = "Это сообщение написано, пока сервер был выключен.";
  await nav(pa, A, `chat/${pab.id}`);
  await tid(pa, "chat-input").click();
  await pa.keyboard.type(later);
  await pa.keyboard.press("Enter");
  await pa.locator('[data-testid="bubble"][data-mine="true"]', { hasText: "пока сервер был выключен" }).waitFor();
  const waiting = await pa.locator('[data-testid="bubble"][data-mine="true"]', { hasText: "пока сервер был выключен" }).getAttribute("data-state");
  check("while the device is off the message is kept and not marked delivered", waiting !== "delivered", `state=${waiting}`);
  await shot(pa, "15-message-waits");
  await B.start();
  const bState = await B.state();
  check("after a restart the second device is still a member (identity and network survived)", bState.configured && bState.self.meshName === "Наша семья", bState.self.name);
  await until(async () => (await B.api("GET", `/api/chat/${(await peerOf(B, "laptop")).id}`)).messages.some((m) => m.text === later), 90000, "the waiting message to arrive after the restart");
  check("the waiting message arrived by itself after the device came back", true);
  await pa.locator('[data-testid="bubble"][data-mine="true"][data-state="delivered"]', { hasText: "пока сервер был выключен" }).waitFor({ timeout: 60000 });
  await shot(pa, "16-message-delivered");

  step("9. The same on a phone-sized screen (the home server's view)");
  const ph = await openPage(B, { w: 390, h: 844, mobile: true, hash: "home" });
  await ph.locator('[data-testid="home-status"]').waitFor();
  await shot(ph, "17-phone-home");

  step("10. Shut both down");
  await A.stop();
  await B.stop();
  check("both processes exited cleanly", /Выход|shutting down/.test(A.log()) && !/panic|fatal/i.test(A.log() + B.log()));
} catch (e) {
  check("the tour ran to the end", false, String(e && e.stack || e).split("\n").slice(0, 6).join(" | "));
  for (const [who, pg] of pages) {
    try {
      await pg.screenshot({ path: path.join(OUT, `99-failure-${who}.png`) });
      console.log(`failure view of ${who}: ${pg.url()}\n  bubbles: ` + JSON.stringify(await pg.$$eval('[data-testid="bubble"]', (els) => els.map((x) => [x.dataset.mine, x.dataset.state, x.innerText.slice(0, 60)]))));
    } catch {}
  }
  for (const n of [A, B]) {
    try {
      const st = await n.state();
      console.log(`\n${n.name}: peers = ` + JSON.stringify(st.peers.map((q) => ({ name: q.deviceName, online: q.online, path: q.path, rtt: q.rttMs, lastError: q.lastError, endpoints: q.endpoints }))));
      const other = st.peers[0];
      if (other) console.log(`${n.name}: chat with ${other.deviceName} = ` + JSON.stringify((await n.api("GET", `/api/chat/${other.id}`)).messages.map((m) => ({ text: String(m.text).slice(0, 30), state: m.state, mine: m.mine, err: m.error || m.err, raw: Object.keys(m) }))));
      const logs = await n.api("GET", "/api/diag/logs").catch(() => null);
      if (logs) console.log(`${n.name}: diag logs (tail) = ` + JSON.stringify(logs).slice(-1800));
    } catch (e2) { console.log(`${n.name}: could not collect diagnostics: ${e2.message}`); }
  }
  console.log("\n--- laptop log (tail) ---\n" + (fs.existsSync(path.join(A.home, "svoi.log")) ? A.log().split("\n").slice(-25).join("\n") : ""));
  console.log("\n--- home-server log (tail) ---\n" + (fs.existsSync(path.join(B.home, "svoi.log")) ? B.log().split("\n").slice(-25).join("\n") : ""));
} finally {
  for (const [who, p] of pages) {
    // (a page whose node was switched off on purpose loses its event stream and gets «connection refused»)
    const problems = p.problems.filter((s) => !/\/api\/events|ERR_INCOMPLETE_CHUNKED_ENCODING|ERR_CONNECTION_REFUSED/.test(s));
    const expected = p.problems.length - problems.length;
    if (problems.length) console.log(`\nbrowser problems (${who}):\n  ` + problems.join("\n  "));
    check(`${who}: no unexpected errors in the browser console`, problems.length === 0, expected ? `${expected} from nodes switched off on purpose ignored` : "");
  }
  if (browser) await browser.close();
  await A.stop();
  await B.stop();
}
const bad = checks.filter((c) => !c.ok);
console.log(`\n${checks.length - bad.length} of ${checks.length} checks passed${bad.length ? "; failed: " + bad.map((c) => c.what).join("; ") : ""}`);
process.exit(bad.length ? 1 : 0);
