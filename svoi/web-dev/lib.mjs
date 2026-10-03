// Shared helpers for the dev scripts: start mock servers, launch Chromium,
// collect console problems.
import { spawn } from "node:child_process";
import net from "node:net";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);

export function loadPlaywright() {
  const tries = ["playwright", "/opt/node22/lib/node_modules/playwright"];
  for (const t of tries) {
    try { return require(t); } catch { /* next */ }
  }
  throw new Error("playwright not found (set NODE_PATH to the global node_modules)");
}

export const CHROMIUM = process.env.CHROMIUM || "/opt/pw-browsers/chromium-1194/chrome-linux/chrome";

function freePort() {
  return new Promise((resolve, reject) => {
    const s = net.createServer();
    s.listen(0, "127.0.0.1", () => { const p = s.address().port; s.close(() => resolve(p)); });
    s.on("error", reject);
  });
}

/** Start web-dev/mock-server.mjs with extra args; resolves { url, port, stop(), get(path), hook(path) }. */
export async function startMock(args = []) {
  const port = await freePort();
  const child = spawn(process.execPath, [path.join(here, "mock-server.mjs"), "--port", String(port), "--latency", "0", ...args], { stdio: ["ignore", "pipe", "pipe"] });
  let out = "";
  child.stdout.on("data", (d) => { out += d; });
  child.stderr.on("data", (d) => { out += d; process.stderr.write(`[mock ${port}] ${d}`); });
  const url = `http://127.0.0.1:${port}`;
  for (let i = 0; i < 100; i++) {
    try { const r = await fetch(url + "/api/state", { headers: args.includes("--auth") ? {} : {} }); if (r.status) break; } catch { /* not yet */ }
    await new Promise((r) => setTimeout(r, 100));
  }
  const api = async (p, init) => {
    const r = await fetch(url + p, init);
    const ct = r.headers.get("content-type") || "";
    return ct.includes("json") ? r.json() : r.text();
  };
  return {
    url, port, child,
    get: (p) => api(p),
    hook: (p) => api(p, { method: "POST" }),
    stop: () => new Promise((resolve) => { child.once("exit", resolve); child.kill(); setTimeout(resolve, 1500); }),
    log: () => out,
  };
}

export async function launch() {
  const { chromium } = loadPlaywright();
  return chromium.launch({ executablePath: CHROMIUM, args: ["--no-sandbox", "--no-proxy-server"] });
}

/** Attach console/pageerror collectors to a page. */
export function watch(page, label, problems) {
  page.on("console", (m) => {
    if (m.type() !== "error") return;
    const text = m.text();
    // Expected: HTTP errors the UI handles on purpose (offline device, 404 thumbs, 409, 401…).
    if (/Failed to load resource: the server responded with a status of (400|401|403|404|409|412|502|503)/.test(text)) return;
    if (/net::ERR_INCOMPLETE_CHUNKED_ENCODING|net::ERR_CONNECTION_REFUSED|ERR_EMPTY_RESPONSE/.test(text)) return;
    problems.push(`[${label}] console.error: ${text}`);
  });
  page.on("pageerror", (e) => problems.push(`[${label}] pageerror: ${e.message}`));
}

export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
