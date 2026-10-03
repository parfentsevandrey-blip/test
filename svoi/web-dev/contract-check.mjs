#!/usr/bin/env node
// Compares the *shape* of the real backend's JSON with the UI mock server's.
// The UI was written against the mock; whatever the mock returns that the real
// node does not is a place where the interface can break.
//
//   node web-dev/mock-server.mjs --port 8777 --calm &
//   ./themesh demo --no-browser --port 18777 --dir /tmp/demo &
//   node web-dev/contract-check.mjs --real http://127.0.0.1:18777 --token "$(cat /tmp/demo/laptop/data/ui.token)" --mock http://127.0.0.1:8777
//
// Exit code 1 when a field the mock has is missing in the real response or has an
// incompatible type.

const args = Object.fromEntries(process.argv.slice(2).reduce((a, v, i, all) => (v.startsWith("--") ? [...a, [v.slice(2), all[i + 1]]] : a), []));
const REAL = args.real || "http://127.0.0.1:18777";
const MOCK = args.mock || "http://127.0.0.1:8777";
const TOKEN = args.token || "";
const verbose = process.argv.includes("--verbose");

async function get(base, path, auth) {
  const r = await fetch(base + path, { headers: auth && TOKEN ? { Authorization: "Bearer " + TOKEN } : {}, redirect: "manual" });
  const text = await r.text();
  let json = null;
  try {
    json = JSON.parse(text);
  } catch {}
  return { status: r.status, json, text };
}

function kind(v) {
  if (v === null) return "null";
  if (Array.isArray(v)) return "array";
  return typeof v;
}

// Collects "path -> set of kinds" for every field, merging array elements.
function shape(v, path = "", out = new Map()) {
  const add = (p, k) => out.set(p, (out.get(p) || new Set()).add(k));
  const k = kind(v);
  add(path || "$", k);
  if (k === "array") for (const el of v) shape(el, path + "[]", out);
  else if (k === "object") for (const [key, val] of Object.entries(v)) shape(val, path + "." + key, out);
  return out;
}

let problems = 0;
let notes = 0;
function compare(name, real, mock) {
  const rs = shape(real.json);
  const ms = shape(mock.json);
  const lines = [];
  for (const [p, mk] of ms) {
    const rk = rs.get(p);
    if (!rk) {
      // A field that only some elements carry (optional) is fine if the real list is empty.
      const parentEmpty = [...rs.keys()].every((q) => !q.startsWith(p.replace(/\.[^.[]+$/, "") + "[]")) && p.includes("[]");
      if (!parentEmpty) lines.push(`  MISSING  ${p}  (mock: ${[...mk].join("|")})`);
      continue;
    }
    const compatible = [...mk].every((k) => rk.has(k) || k === "null" || rk.has("null") || (k === "number" && rk.has("number")));
    if (!compatible) lines.push(`  TYPE     ${p}  mock=${[...mk].join("|")} real=${[...rk].join("|")}`);
  }
  if (verbose) {
    for (const [p, rk] of rs) if (!ms.has(p)) lines.push(`  extra    ${p}  (real: ${[...rk].join("|")})`);
  }
  const bad = lines.filter((l) => !l.startsWith("  extra"));
  problems += bad.length;
  notes += lines.length - bad.length;
  console.log(`${bad.length ? "✗" : "✓"} ${name}${bad.length ? "" : ""}`);
  for (const l of lines) console.log(l);
}

async function both(name, realPath, mockPath = realPath) {
  const [r, m] = await Promise.all([get(REAL, realPath, true), get(MOCK, mockPath, false)]);
  if (r.status >= 300 || m.status >= 300) {
    problems++;
    console.log(`✗ ${name}: real ${r.status} ${r.text.slice(0, 120)} | mock ${m.status}`);
    return null;
  }
  compare(name, r, m);
  return { real: r.json, mock: m.json };
}

const st = await both("GET /api/state", "/api/state");
if (!st) process.exit(1);
const byName = (state, n) => state.peers.find((p) => p.name === n);
const pick = (n) => ({ r: byName(st.real, n), m: byName(st.mock, n) });
const nas = pick("nas");
if (!nas.r || !nas.m) {
  console.log("cannot find a device called 'nas' in both worlds");
  process.exit(1);
}

await both("GET /api/transfers", "/api/transfers");
await both("GET /api/shares", "/api/shares");
await both("GET /api/services", "/api/services");
await both("GET /api/forwards", "/api/forwards");
await both("GET /api/settings", "/api/settings");
await both("GET /api/invites", "/api/invites");
await both("GET /api/diag/logs", "/api/diag/logs?limit=50");
await both("GET /api/chat/threads", "/api/chat/threads");
await both("GET /api/mail (inbox)", "/api/mail?folder=inbox&limit=20");
await both("GET /api/local/fs", "/api/local/fs");

const shares = await both("GET /api/peers/nas/shares", `/api/peers/${nas.r.id}/shares`, `/api/peers/${nas.m.id}/shares`);
if (shares && shares.real.length && shares.mock.length) {
  const rs = shares.real[0];
  const ms = shares.mock[0];
  await both("GET /api/peers/nas/fs", `/api/peers/${nas.r.id}/fs?share=${rs.id}&path=/`, `/api/peers/${nas.m.id}/fs?share=${ms.id}&path=/`);
}
await both("GET /api/peers/nas/services", `/api/peers/${nas.r.id}/services`, `/api/peers/${nas.m.id}/services`);
const ping = await both("GET /api/diag/ping", `/api/diag/ping?peer=${nas.r.id}`, `/api/diag/ping?peer=${nas.m.id}`);

const mail = await get(REAL, "/api/mail?folder=inbox&limit=1", true);
const mmail = await get(MOCK, "/api/mail?folder=inbox&limit=1", false);
if (mail.json?.items?.length && mmail.json?.items?.length) {
  await both("GET /api/mail/:id", `/api/mail/${mail.json.items[0].id}`, `/api/mail/${mmail.json.items[0].id}`);
}
const th = await get(REAL, "/api/chat/threads", true);
const mth = await get(MOCK, "/api/chat/threads", false);
if (th.json?.length && mth.json?.length) {
  await both("GET /api/chat/:peer", `/api/chat/${th.json[0].peer.id}`, `/api/chat/${mth.json[0].peer.id}`);
}
void ping;

console.log(problems ? `\n${problems} problem(s) between the real backend and the mock` : "\nreal backend matches the mock's shapes");
process.exit(problems ? 1 : 0);
