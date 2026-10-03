// Shared helpers of the end-to-end suite: Playwright loading, the demo lifecycle,
// a tiny test registry and page helpers. See run.mjs.
import { spawn, execFileSync } from "node:child_process";
import { createRequire } from "node:module";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import net from "node:net";
import { fileURLToPath } from "node:url";

export const here = path.dirname(fileURLToPath(import.meta.url));
export const root = path.resolve(here, "..");
export const config = { bin: path.join(root, "svoi"), shots: path.join(os.tmpdir(), "svoi-e2e-shots"), headed: false };

// ------------------------------------------------------------------ playwright
function loadPlaywright() {
  const reqs = [createRequire(import.meta.url)];
  try {
    reqs.push(createRequire(path.join(execFileSync("npm", ["root", "-g"], { encoding: "utf8" }).trim(), "x.js")));
  } catch {}
  reqs.push(createRequire("/opt/node22/lib/node_modules/x.js"));
  for (const r of reqs) {
    try {
      return r("playwright");
    } catch {}
  }
  throw new Error("Playwright is not installed (npm i -D playwright)");
}
export const { chromium } = loadPlaywright();

// ------------------------------------------------------------------ demo lifecycle
function freePorts(n) {
  return new Promise((resolve, reject) => {
    const tryBase = (base, left) => {
      if (left === 0) return reject(new Error("no free ports"));
      const servers = [];
      let ok = 0;
      for (let i = 0; i < 4; i++) {
        const s = net.createServer();
        s.once("error", () => {
          servers.forEach((x) => x.close());
          tryBase(base + 20, left - 1);
        });
        s.listen(base + i, "127.0.0.1", () => {
          if (++ok === 4) {
            servers.forEach((x) => x.close());
            setTimeout(() => resolve(base), 50);
          }
        });
        servers.push(s);
      }
    };
    tryBase(20000 + Math.floor(Math.random() * 20000), 50);
  });
}

export async function startDemo({ quiet = true } = {}) {
  const base = await freePorts(4);
  const proc = spawn(config.bin, ["demo", "--no-browser", "--port", String(base), ...(quiet ? ["--quiet"] : [])], { stdio: ["ignore", "pipe", "pipe"] });
  let out = "";
  const devices = {};
  const ready = new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("demo did not start:\n" + out)), 60000);
    const onData = (d) => {
      out += d;
      for (const m of String(out).matchAll(/(laptop|phone|nas|home-server)\s+(http:\/\/127\.0\.0\.1:(\d+))\/\?t=([0-9a-f]+)/g)) {
        if (devices[m[1]]) continue; // later output re-matches the same lines; keep the first object (it gets .api below)
        devices[m[1]] = { name: m[1], origin: m[2], port: +m[3], token: m[4], url: `${m[2]}/?t=${m[4]}` };
        devices[m[1]].api = (method, p, body, o = {}) => api(devices[m[1]], method, p, body, o);
      }
      if (Object.keys(devices).length === 4 && /Ctrl\+C/.test(out)) {
        clearTimeout(timer);
        resolve();
      }
    };
    proc.stdout.on("data", onData);
    proc.stderr.on("data", onData);
    proc.on("exit", (c) => reject(new Error(`demo exited (${c}):\n${out}`)));
  });
  await ready;
  proc.removeAllListeners("exit");
  const demo = { devices, proc, log: () => out };
  // wait until the laptop sees everybody and the seeded content has settled
  const lap = devices.laptop;
  await until(async () => {
    const st = await lap.api("GET", "/api/state");
    return st.peers.length === 3 && st.peers.every((p) => p.online) && st.counters.mail >= 3 && st.counters.chat >= 3 && st.counters.offers >= 1;
  }, 30000, "the demo mesh to settle");
  demo.stop = async () => {
    proc.kill("SIGINT");
    await new Promise((r) => {
      const t = setTimeout(() => (proc.kill("SIGKILL"), r()), 8000);
      proc.once("exit", () => (clearTimeout(t), r()));
    });
  };
  return demo;
}

// A real `svoi up` process with its own data directory (loopback only), optionally
// already part of a mesh of its own (via `svoi init`). Used where the demo does not
// fit: onboarding screens and anything that needs an unconfigured device.
export async function startNode({ name, init = false, mesh = "Тест", owner = "" } = {}) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "svoi-e2e-node-"));
  const env = { ...process.env, SVOI_DIR: path.join(dir, "data"), HOME: path.join(dir, "home"), XDG_CONFIG_HOME: path.join(dir, "home", ".config"), DISPLAY: "", WAYLAND_DISPLAY: "" };
  fs.mkdirSync(env.HOME, { recursive: true });
  if (init) execFileSync(config.bin, ["init", "--mesh", mesh, "--name", name, "--owner", owner], { env, stdio: "pipe" });
  const log = fs.openSync(path.join(dir, "node.log"), "w");
  const proc = spawn(config.bin, ["up", "--no-browser", "--no-stun", "--loopback", "--ui", "127.0.0.1:0"], { env, stdio: ["ignore", log, log], detached: true });
  const addrFile = path.join(env.SVOI_DIR, "ui.addr");
  const tokFile = path.join(env.SVOI_DIR, "ui.token");
  await until(async () => fs.existsSync(addrFile) && fs.existsSync(tokFile), 20000, `${name || "node"} to start`);
  const origin = "http://" + fs.readFileSync(addrFile, "utf8").trim();
  const token = fs.readFileSync(tokFile, "utf8").trim();
  const node = { name, dir, origin, token, url: `${origin}/?t=${token}`, proc, log: () => fs.readFileSync(path.join(dir, "node.log"), "utf8") };
  node.api = (method, p, body, o = {}) => api(node, method, p, body, o);
  node.stop = async () => {
    try { process.kill(-proc.pid, "SIGINT"); } catch {}
    await new Promise((r) => { const t = setTimeout(() => { try { process.kill(-proc.pid, "SIGKILL"); } catch {} r(); }, 8000); proc.once("exit", () => { clearTimeout(t); r(); }); });
  };
  nodes.push(node);
  return node;
}
const nodes = [];
export async function stopNodes() { while (nodes.length) await nodes.pop().stop(); }

export async function api(dev, method, p, body, { raw = false, headers = {} } = {}) {
  const res = await fetch(dev.origin + p, {
    method,
    headers: { Authorization: "Bearer " + dev.token, "X-Svoi": "1", ...(body !== undefined && !(body instanceof Uint8Array) ? { "Content-Type": "application/json" } : {}), ...headers },
    body: body === undefined ? undefined : body instanceof Uint8Array ? body : JSON.stringify(body),
  });
  if (raw) return res;
  const text = await res.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {}
  if (!res.ok) throw Object.assign(new Error(`${method} ${p} → ${res.status} ${text.slice(0, 200)}`), { status: res.status, body: json });
  return json;
}

export async function until(fn, ms, what) {
  const end = Date.now() + ms;
  let last;
  while (Date.now() < end) {
    try {
      const v = await fn();
      if (v) return v;
    } catch (e) {
      last = e;
    }
    await new Promise((r) => setTimeout(r, 150));
  }
  throw new Error(`timed out waiting for ${what}${last ? ": " + last.message : ""}`);
}

// ------------------------------------------------------------------ tiny test framework
export const tests = [];
let currentGroup = null;
export function group(name, opts, fn) {
  if (typeof opts === "function") [fn, opts] = [opts, {}];
  currentGroup = { name, opts, demo: null, starting: null };
  fn();
  currentGroup = null;
}
export function test(name, fn, opts = {}) {
  tests.push({ group: currentGroup, name: `${currentGroup.name}: ${name}`, fn, opts });
}

export function assert(cond, msg) {
  if (!cond) throw new Error("assertion failed: " + msg);
}
export function eq(a, b, msg) {
  if (JSON.stringify(a) !== JSON.stringify(b)) throw new Error(`${msg || "values differ"}: got ${JSON.stringify(a)}, want ${JSON.stringify(b)}`);
}

export async function open(browser, dev, { w = 1280, h = 800, lang = "ru", theme = "dark", hash = "", allow = [], mobile = false } = {}) {
  const ctx = await browser.newContext({
    viewport: { width: w, height: h },
    colorScheme: theme,
    locale: lang === "en" ? "en-US" : "ru-RU",
    acceptDownloads: true,
    hasTouch: mobile,
    isMobile: mobile,
    reducedMotion: "reduce",
  });
  await ctx.addInitScript(([th, lg]) => {
    try {
      if (!localStorage.getItem("svoi.lang")) localStorage.setItem("svoi.lang", lg);
      if (!localStorage.getItem("svoi.theme")) localStorage.setItem("svoi.theme", th);
    } catch {}
  }, [theme, lang]);
  const page = await ctx.newPage();
  page.setDefaultTimeout(8000);
  page.problems = [];
  const bad = (s) => !allow.some((re) => re.test(s)) && page.problems.push(s);
  page.on("console", (m) => (m.type() === "error" || m.type() === "warning") && bad(`console.${m.type()}: ${m.text()}`));
  page.on("pageerror", (e) => bad("pageerror: " + e.message));
  page.on("requestfailed", (r) => !/\/api\/events/.test(r.url()) && bad(`requestfailed: ${r.method()} ${r.url()} ${r.failure() && r.failure().errorText}`));
  page.on("response", (r) => r.status() >= 400 && /\/api\//.test(r.url()) && bad(`HTTP ${r.status()} ${r.request().method()} ${r.url().replace(dev.origin, "")}`));
  await page.goto(dev.url, { waitUntil: "load" }); // token handshake sets the session cookie
  // (the side navigation exists but is hidden on a narrow screen, the tab bar the other way round)
  await page.waitForFunction(() => [...document.querySelectorAll('[data-testid="nav-devices"], [data-testid="tab-devices"], [data-testid="page-onboarding"]')].some((e) => e.getClientRects().length > 0));
  if (hash) await nav(page, dev, hash);
  return page;
}
export async function nav(page, dev, hash) {
  await page.goto(`${dev.origin}/#/${hash.replace(/^#?\/?/, "")}`);
  await page.waitForTimeout(120);
}
export const tid = (page, id) => page.getByTestId(id);

