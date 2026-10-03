#!/usr/bin/env node
// themesh — UI mock server (Node 22, zero dependencies).
//
//   node web-dev/mock-server.mjs [--port 8777] [--host 127.0.0.1]
//        [--scenario full|empty|onboarding] [--calm] [--auth] [--latency 40]
//
// Serves internal/web/ui/ as a SPA and implements every endpoint of
// docs/UI-API.md against an in-memory fake mesh with live SSE updates.
//   --calm      no random background events and no jitter (deterministic screenshots)
//   --auth      require sign-in: open the one-time /?t=<code> link printed at start
//               (or from POST /__mock/login); scripts may use "Authorization: Bearer dev"
//   --latency   artificial delay for API responses, ms
//   --tun-error enabling the TUN interface fails with "permission denied" (to see the error state)
//
// Test hooks (not part of the real API), GET or POST:
//   /__mock/offer?from=phone[&name=…] incoming file offer (.jpg/.pdf/.wav are small, others 18 MB)
//   /__mock/chat?from=dad-pc&text=… incoming chat message
//   /__mock/mail?from=nas           incoming mail
//   /__mock/join[?name=tablet]      consume the newest invite → a new device joins
//   /__mock/drop?for=5              drop SSE clients and refuse reconnects for N s
//   /__mock/peer?name=nas&online=0  toggle a device online/offline
//   /__mock/auth?on=1               require sign-in from now on (on=0 to disable)
//   /__mock/login                   a fresh one-time sign-in link {code, url: "/?t=…"}
//   /__mock/tun?error=1             make enabling TUN fail (error=0: succeed again)
//   /__mock/removed                 an admin removed this device: back to onboarding with `removed`
//   /__mock/sw?bump=1               pretend a new binary: sw.js gets a new VERSION (as after an upgrade)
//   /__mock/portmap?state=mapped    router port mapping: mapped|searching|unavailable|private
//   /__mock/reset                   rebuild the world from the scenario

import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import crypto from "node:crypto";
import { Readable } from "node:stream";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const UI_DIR = path.resolve(__dirname, "../internal/web/ui");
const FIXTURES = path.resolve(__dirname, "fixtures");

// ---------------------------------------------------------------- args
function parseArgs(argv) {
  const o = { port: 8777, host: "127.0.0.1", scenario: "full", calm: false, auth: false, latency: 40, quiet: false, tunError: false };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    const next = () => argv[++i];
    if (a === "--port") o.port = Number(next());
    else if (a === "--host") o.host = next();
    else if (a === "--scenario") o.scenario = next();
    else if (a === "--calm") o.calm = true;
    else if (a === "--auth") o.auth = true;
    else if (a === "--latency") o.latency = Number(next());
    else if (a === "--quiet") o.quiet = true;
    else if (a === "--tun-error") o.tunError = true;
    else if (a.startsWith("--port=")) o.port = Number(a.slice(7));
    else if (a.startsWith("--scenario=")) o.scenario = a.slice(11);
    else if (a === "-h" || a === "--help") { console.log(fs.readFileSync(fileURLToPath(import.meta.url), "utf8").split("\n").slice(1, 24).join("\n")); process.exit(0); }
  }
  if (!["full", "empty", "onboarding"].includes(o.scenario)) throw new Error("unknown scenario " + o.scenario);
  return o;
}
const opts = parseArgs(process.argv.slice(2));
// Sign-in like the node: the master token is for scripts only (Authorization:
// Bearer dev); a browser signs in with a one-time login code (/?t=<code>, 10 min)
// that becomes a 14-day sliding session cookie.
const MASTER = "dev";
const CODE_TTL = 600, SESSION_TTL = 14 * 86400;
const loginCodes = new Map(); // code → expiry (s)
const sessions = new Map();   // session id → expiry (s)
let authRequired = opts.auth;
let tunFails = opts.tunError;

// ---------------------------------------------------------------- helpers
const now = () => Math.floor(Date.now() / 1000);
const B32 = "abcdefghijklmnopqrstuvwxyz234567";
function base32(buf) {
  let bits = 0, val = 0, out = "";
  for (const b of buf) {
    val = (val << 8) | b; bits += 8;
    while (bits >= 5) { out += B32[(val >>> (bits - 5)) & 31]; bits -= 5; }
  }
  if (bits > 0) out += B32[(val << (5 - bits)) & 31];
  return out;
}
const devId = (name) => base32(crypto.createHash("sha256").update("themesh-mock/" + name).digest()).slice(0, 52);
const rid = (p) => p + crypto.randomBytes(6).toString("hex");
const sha = (buf) => crypto.createHash("sha256").update(buf).digest("hex");
function seeded(seed) {
  let s = typeof seed === "number" ? seed >>> 0 : [...String(seed)].reduce((a, c) => (a * 31 + c.charCodeAt(0)) >>> 0, 2166136261);
  return () => {
    s = (s + 0x6d2b79f5) >>> 0;
    let t = s;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const clone = (x) => JSON.parse(JSON.stringify(x));

// ---------------------------------------------------------------- generated content
const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();
function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function pngChunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, "ascii"), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
function encodePNG(w, h, rgb) {
  const raw = Buffer.alloc((w * 3 + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (w * 3 + 1)] = 0;
    rgb.copy ? rgb.copy(raw, y * (w * 3 + 1) + 1, y * w * 3, (y + 1) * w * 3)
      : raw.set(rgb.subarray(y * w * 3, (y + 1) * w * 3), y * (w * 3 + 1) + 1);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 2; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    pngChunk("IHDR", ihdr),
    pngChunk("IDAT", zlib.deflateSync(raw, { level: 6 })),
    pngChunk("IEND", Buffer.alloc(0)),
  ]);
}

const PALETTES = [
  { sky: [[255, 183, 120], [120, 80, 160]], sun: [255, 236, 190], hills: [[96, 62, 120], [62, 40, 92], [36, 24, 58]], water: true },   // sunset
  { sky: [[150, 205, 245], [228, 240, 248]], sun: [255, 250, 220], hills: [[120, 170, 110], [70, 125, 80], [40, 90, 60]], water: false }, // summer day
  { sky: [[18, 28, 60], [52, 70, 120]], sun: [235, 235, 220], hills: [[30, 40, 70], [20, 28, 52], [10, 16, 32]], water: true, stars: true }, // night
  { sky: [[255, 214, 170], [250, 240, 220]], sun: [255, 245, 225], hills: [[180, 160, 150], [140, 120, 120], [100, 84, 90]], water: true }, // misty morning
  { sky: [[120, 180, 230], [200, 225, 245]], sun: [255, 255, 240], hills: [[235, 240, 248], [170, 190, 210], [90, 110, 140]], water: false, snow: true }, // winter
];

/** A procedural "photo": sky gradient, sun, layered ridges, optional lake. */
function genScene(seed, w, h) {
  const rnd = seeded(seed);
  const pal = PALETTES[Math.floor(rnd() * PALETTES.length)];
  const rgb = Buffer.alloc(w * h * 3);
  const sunX = (0.2 + rnd() * 0.6) * w, sunY = (0.18 + rnd() * 0.25) * h, sunR = (0.05 + rnd() * 0.04) * w;
  const horizon = h * (0.55 + rnd() * 0.12);
  // Ridge heights are precomputed per column (the per-pixel loop stays cheap).
  const layers = pal.hills.map((col, i) => {
    const base = horizon - h * (0.22 - i * 0.07);
    const f = [rnd() * 3 + 1, rnd() * 7 + 3, rnd() * 17 + 9];
    const ph = [rnd() * 6.28, rnd() * 6.28, rnd() * 6.28];
    const amp = [h * (0.08 - i * 0.015), h * 0.03, h * 0.012];
    const ridge = new Float32Array(w);
    for (let x = 0; x < w; x++) {
      ridge[x] = base + amp[0] * Math.sin((x / w) * f[0] + ph[0]) + amp[1] * Math.sin((x / w) * f[1] * 3.1 + ph[1]) + amp[2] * Math.sin((x / w) * f[2] * 6 + ph[2]);
    }
    return { col, ridge, k: 0.06 * (pal.hills.length - i) };
  });
  const stars = new Set();
  if (pal.stars) for (let i = 0; i < Math.round((w * h) / 2500); i++) stars.add(Math.round(rnd() * horizon * 0.8) * w + Math.round(rnd() * w));
  const noise = seeded(seed + 7);
  const [s0, s1] = pal.sky, sun = pal.sun;
  const glowR = w * 0.5;
  for (let y = 0; y < h; y++) {
    const water = pal.water && y > horizon;
    for (let x = 0; x < w; x++) {
      const yy = water ? horizon - (y - horizon) * 1.1 - Math.sin(y * 0.9 + x * 0.02) * 1.6 : y;
      const tt = yy <= 0 ? 0 : yy >= horizon ? 1 : yy / horizon;
      let r = s1[0] + (s0[0] - s1[0]) * (1 - tt), g = s1[1] + (s0[1] - s1[1]) * (1 - tt), b = s1[2] + (s0[2] - s1[2]) * (1 - tt);
      const dx = x - sunX, dy = yy - sunY;
      const d = Math.sqrt(dx * dx + dy * dy);
      const k = d < sunR ? 0.95 : Math.max(0, 0.45 - (d - sunR) / glowR);
      if (k > 0) { r += (sun[0] - r) * k; g += (sun[1] - g) * k; b += (sun[2] - b) * k; }
      if (stars.size && stars.has(Math.round(yy) * w + x)) { r = 240; g = 240; b = 255; }
      for (let i = 0; i < layers.length; i++) {
        const L = layers[i];
        if (yy > L.ridge[x]) {
          let c = L.col;
          if (pal.snow && i === 0 && yy < L.ridge[x] + h * 0.03) c = SNOW;
          // mostly the hill colour, with a little haze from what is behind it
          r = c[0] + (r - c[0]) * L.k; g = c[1] + (g - c[1]) * L.k; b = c[2] + (b - c[2]) * L.k;
        }
      }
      if (water) { r += (20 - r) * 0.25; g += (40 - g) * 0.25; b += (70 - b) * 0.25; }
      const n = (noise() - 0.5) * 10;
      const o = (y * w + x) * 3;
      rgb[o] = r + n < 0 ? 0 : r + n > 255 ? 255 : r + n;
      rgb[o + 1] = g + n < 0 ? 0 : g + n > 255 ? 255 : g + n;
      rgb[o + 2] = b + n < 0 ? 0 : b + n > 255 ? 255 : b + n;
    }
  }
  return encodePNG(w, h, rgb);
}
const SNOW = [250, 252, 255];

function genSvgArt(seed, title) {
  const rnd = seeded(seed);
  const hue = Math.floor(rnd() * 360);
  let shapes = "";
  for (let i = 0; i < 9; i++) {
    const r = 60 + rnd() * 220, x = rnd() * 1200, y = rnd() * 800;
    shapes += `<circle cx="${x.toFixed(0)}" cy="${y.toFixed(0)}" r="${r.toFixed(0)}" fill="hsl(${(hue + i * 23) % 360} 60% ${45 + rnd() * 25}%)" opacity="${(0.35 + rnd() * 0.4).toFixed(2)}"/>`;
  }
  return Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 800" width="1200" height="800">
<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="hsl(${hue} 45% 22%)"/><stop offset="1" stop-color="hsl(${(hue + 60) % 360} 50% 12%)"/></linearGradient></defs>
<rect width="1200" height="800" fill="url(#g)"/>${shapes}
<text x="60" y="740" font-family="system-ui,sans-serif" font-size="44" fill="rgba(255,255,255,.85)">${title.replace(/[<&>]/g, "")}</text></svg>`);
}

const NOTE = { C4: 261.63, D4: 293.66, E4: 329.63, F4: 349.23, G4: 392, A4: 440, B4: 493.88, C5: 523.25, D5: 587.33, E5: 659.25, G3: 196, A3: 220 };
function genWav(melody, bpm = 96) {
  const rate = 22050;
  const beat = 60 / bpm;
  const total = melody.reduce((a, [, d]) => a + d * beat, 0) + 0.3;
  const n = Math.floor(total * rate);
  const data = Buffer.alloc(n * 2);
  let t0 = 0;
  for (const [note, dur] of melody) {
    const f = NOTE[note] || 0;
    const len = dur * beat;
    const s0 = Math.floor(t0 * rate), s1 = Math.min(n, Math.floor((t0 + len) * rate));
    for (let s = s0; s < s1; s++) {
      const tt = (s - s0) / rate;
      const env = Math.min(1, tt * 40) * Math.exp(-tt * 2.6);
      const v = f ? (Math.sin(2 * Math.PI * f * tt) * 0.7 + Math.sin(4 * Math.PI * f * tt) * 0.2 + Math.sin(6 * Math.PI * f * tt) * 0.08) * env : 0;
      const cur = data.readInt16LE(s * 2);
      data.writeInt16LE(Math.max(-32767, Math.min(32767, cur + Math.round(v * 12000))), s * 2);
    }
    t0 += len;
  }
  const hdr = Buffer.alloc(44);
  hdr.write("RIFF", 0); hdr.writeUInt32LE(36 + data.length, 4); hdr.write("WAVE", 8);
  hdr.write("fmt ", 12); hdr.writeUInt32LE(16, 16); hdr.writeUInt16LE(1, 20); hdr.writeUInt16LE(1, 22);
  hdr.writeUInt32LE(rate, 24); hdr.writeUInt32LE(rate * 2, 28); hdr.writeUInt16LE(2, 32); hdr.writeUInt16LE(16, 34);
  hdr.write("data", 36); hdr.writeUInt32LE(data.length, 40);
  return Buffer.concat([hdr, data]);
}
const LULLABY = [["E4", 1], ["G4", 1], ["E4", 1], ["C4", 1], ["D4", 1], ["E4", 1], ["D4", 2], ["E4", 1], ["G4", 1], ["A4", 1], ["G4", 1], ["E4", 1], ["D4", 1], ["C4", 2], ["G3", 1], ["C4", 1], ["E4", 1], ["D4", 1], ["C4", 3]];
const TONE = [["A4", 6]];
const MARCH = [["C4", .5], ["E4", .5], ["G4", .5], ["C5", 1], ["G4", .5], ["C5", 2], ["A4", .5], ["G4", .5], ["E4", .5], ["G4", 1], ["E4", .5], ["C4", 2]];

function pdfEsc(s) { return s.replace(/[\\()]/g, (m) => "\\" + m).replace(/[^\x20-\x7e]/g, "?"); }
function genPdf(title, lines) {
  const content = [
    "BT /F2 22 Tf 72 760 Td (" + pdfEsc(title) + ") Tj ET",
    "0.82 0.85 0.84 RG 1 w 72 742 m 523 742 l S",
    ...lines.map((l, i) => `BT /F1 12 Tf 72 ${712 - i * 20} Td (${pdfEsc(l)}) Tj ET`),
    "BT /F1 9 Tf 72 60 Td (Generated by the themesh UI mock server) Tj ET",
  ].join("\n");
  const objs = [
    "<< /Type /Catalog /Pages 2 0 R >>",
    "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>",
    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>",
    `<< /Length ${Buffer.byteLength(content)} >>\nstream\n${content}\nendstream`,
  ];
  let out = "%PDF-1.4\n%\xe2\xe3\xcf\xd3\n";
  const offs = [];
  objs.forEach((o, i) => { offs.push(Buffer.byteLength(out, "latin1")); out += `${i + 1} 0 obj\n${o}\nendobj\n`; });
  const xref = Buffer.byteLength(out, "latin1");
  out += `xref\n0 ${objs.length + 1}\n0000000000 65535 f \n` + offs.map((o) => String(o).padStart(10, "0") + " 00000 n \n").join("");
  out += `trailer\n<< /Size ${objs.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(out, "latin1");
}

/** Deterministic pseudo-random bytes stream for big fake files. */
function patternStream(seed, start, end) {
  const rnd = seeded(seed);
  let pos = start;
  return new Readable({
    read() {
      if (pos > end) { this.push(null); return; }
      const n = Math.min(65536, end - pos + 1);
      const b = Buffer.alloc(n);
      for (let i = 0; i < n; i++) b[i] = (rnd() * 256) | 0;
      pos += n;
      this.push(b);
    },
  });
}

let sampleVideo = null;
function videoFixture() {
  if (sampleVideo) return sampleVideo;
  try { sampleVideo = fs.readFileSync(path.join(FIXTURES, "sample.webm")); }
  catch { sampleVideo = Buffer.from("not a real video (run web-dev/make-assets.mjs)"); }
  return sampleVideo;
}

/** Fake QR: finder patterns + hashed modules. Looks right, does not scan. */
function fakeQrSvg(text) {
  const N = 33, q = 4;
  const m = Array.from({ length: N }, () => new Array(N).fill(0));
  const finder = (ox, oy) => {
    for (let y = -1; y <= 7; y++) for (let x = -1; x <= 7; x++) {
      const X = ox + x, Y = oy + y;
      if (X < 0 || Y < 0 || X >= N || Y >= N) continue;
      const inRing = x >= 0 && x <= 6 && y >= 0 && y <= 6 && (x === 0 || x === 6 || y === 0 || y === 6);
      const inCore = x >= 2 && x <= 4 && y >= 2 && y <= 4;
      m[Y][X] = inRing || inCore ? 1 : 2;
    }
  };
  finder(0, 0); finder(N - 7, 0); finder(0, N - 7);
  for (let i = 8; i < N - 8; i++) { m[6][i] = i % 2 ? 2 : 1; m[i][6] = i % 2 ? 2 : 1; }
  let h = crypto.createHash("sha512").update(text).digest();
  let bit = 0;
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    if (m[y][x]) continue;
    if (bit >= h.length * 8) { h = crypto.createHash("sha512").update(h).digest(); bit = 0; }
    m[y][x] = (h[bit >> 3] >> (bit & 7)) & 1 ? 1 : 2;
    bit++;
  }
  let d = "";
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) if (m[y][x] === 1) d += `M${x + q} ${y + q}h1v1h-1z`;
  const S = N + q * 2;
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${S} ${S}" shape-rendering="crispEdges"><rect width="${S}" height="${S}" fill="#fff"/><path d="${d}" fill="#111"/></svg>`;
}

function inviteCode(admin) {
  const bytes = crypto.randomBytes(110);
  bytes[0] = 1; bytes[1] = admin ? 1 : 0;
  const enc = base32(bytes).toUpperCase();
  const groups = enc.match(/.{1,8}/g);
  return "MESH1-" + groups.join("-");
}

// ---------------------------------------------------------------- virtual file trees
// node: { dir: true, children: {name: node}, mtime } | { file: true, size, mtime, mime, kind, ... , data? }
function dir(children = {}, mtime = now() - 86400 * 30) { return { dir: true, children, mtime }; }
function file(kind, opt = {}) { return { file: true, kind, mtime: opt.mtime || now() - Math.floor(Math.random() * 86400 * 60), ...opt }; }

const MIME = {
  jpg: "image/jpeg", jpeg: "image/jpeg", png: "image/png", svg: "image/svg+xml", gif: "image/gif", webp: "image/webp",
  webm: "video/webm", mp4: "video/mp4", mkv: "video/x-matroska", mov: "video/quicktime",
  wav: "audio/wav", mp3: "audio/mpeg", flac: "audio/flac",
  pdf: "application/pdf", txt: "text/plain; charset=utf-8", md: "text/markdown; charset=utf-8", log: "text/plain; charset=utf-8",
  csv: "text/csv; charset=utf-8", json: "application/json", html: "text/html; charset=utf-8", zip: "application/zip",
  gz: "application/gzip", tar: "application/x-tar", iso: "application/x-iso9660-image", gpx: "application/gpx+xml",
  docx: "application/vnd.openxmlformats-officedocument.wordprocessingml.document", xlsx: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
};
const mimeOf = (name) => MIME[(name.split(".").pop() || "").toLowerCase()] || "application/octet-stream";

const contentCache = new Map();
/** Materialise a file node's bytes (generated once, cached). Returns Buffer or null (= pattern stream). */
function fileBytes(key, node, name) {
  if (node.data) return node.data;
  if (node.kind === "big") return null;
  if (contentCache.has(key)) return contentCache.get(key);
  let b;
  switch (node.kind) {
    case "photo": b = genScene(node.seed ?? key, node.w || 960, node.h || 640); break;
    case "svg": b = genSvgArt(node.seed ?? key, name.replace(/\.svg$/, "")); break;
    case "wav": b = genWav(node.melody || LULLABY, node.bpm || 96); break;
    case "video": b = videoFixture(); break;
    case "pdf": b = genPdf(node.title || name, node.lines || ["Mock document."]); break;
    case "text": b = Buffer.from(node.text || ""); break;
    default: b = Buffer.from("");
  }
  contentCache.set(key, b);
  return b;
}
function nodeSize(key, node, name) {
  if (node.kind === "big") return node.size;
  return fileBytes(key, node, name).length;
}

function photos(prefix, n, startNum, baseTime, seedBase) {
  const o = {};
  for (let i = 0; i < n; i++) {
    const num = startNum + i * 3;
    o[`${prefix}${num}.jpg`] = file("photo", { seed: seedBase + i, mtime: baseTime + i * 1800 });
  }
  return o;
}

const RECIPE_BORSCH = `# Борщ

Рецепт от бабушки, на 6 порций.

## Ингредиенты
- говядина на кости — 700 г
- свёкла — 2 шт.
- капуста — ¼ кочана
- картофель — 3 шт.
- морковь, лук — по 1 шт.
- томатная паста — 2 ст. л.

## Как готовить
1. Варить бульон 1,5 часа, снимая пену.
2. Свёклу натереть и потушить с томатной пастой и ложкой уксуса.
3. Добавить в бульон картофель, через 10 минут — капусту.
4. Заправить зажаркой и свёклой, дать постоять 20 минут.

Подавать со сметаной и чесночными пампушками.
`;
const RECIPE_SYRNIKI = `# Сырники

- творог 5 % — 400 г
- яйцо — 1 шт.
- мука — 3 ст. л. и ещё немного для обвалки
- сахар — 2 ст. л., щепотка соли, ваниль

Смешать, сформировать шайбы, обвалять в муке и жарить на среднем огне по 3 минуты с каждой стороны.
`;
const NOTES_TXT = `Пароль от Wi-Fi на даче — на холодильнике :)
Счётчик воды: поверка до 12.2026
Позвонить в управляющую компанию насчёт домофона
Купить: фильтры для воды, лампочки E27 ×4, батарейки AA

Ссылки:
https://www.gosuslugi.ru
https://github.com/
`;
const CONFIG_JSON = JSON.stringify({ backup: { schedule: "0 3 * * *", keep: { daily: 7, weekly: 4, monthly: 6 }, targets: ["/volume1/photo", "/volume1/docs"] }, notify: { mail: true, chat: true }, version: 3 }, null, 2) + "\n";
const BACKUP_LOG = Array.from({ length: 40 }, (_, i) => {
  const d = new Date(Date.now() - (40 - i) * 86400000);
  return `${d.toISOString().slice(0, 10)} 03:00:0${i % 10} INFO backup started\n${d.toISOString().slice(0, 10)} 03:0${(i % 5) + 4}:12 INFO backup finished: ${(11 + (i % 7) * 0.3).toFixed(1)} GB, ${3700 + i * 3} files`;
}).join("\n") + "\n";
const EXPENSES_CSV = "Дата;Категория;Сумма;Комментарий\n2025-09-01;Продукты;4 820;Пятёрочка\n2025-09-03;Коммуналка;6 240;сентябрь\n2025-09-07;Транспорт;1 500;Тройка\n2025-09-12;Дача;12 300;доски, саморезы\n2025-09-20;Продукты;3 990;рынок\n";
const README_HTML = `<!doctype html><title>test</title><h1>Hello</h1><script>alert("this must never run in the UI")</script>\n`;

function buildTrees() {
  const T = now();
  return {
    nas: {
      sh_photo: dir({
        "2024": dir({
          "Лето на даче": dir({ ...photos("IMG_", 9, 2041, T - 86400 * 400, 11), "Закат над рекой.webm": file("video", { mtime: T - 86400 * 390 }) }, T - 86400 * 390),
          "Новый год": dir(photos("IMG_", 4, 5102, T - 86400 * 270, 31), T - 86400 * 270),
        }, T - 86400 * 270),
        "2025": dir({
          "Байкал": dir(photos("DSC0", 7, 1180, T - 86400 * 60, 51), T - 86400 * 58),
          "Юбилей бабушки": dir(photos("IMG_", 5, 8810, T - 86400 * 9, 71), T - 86400 * 9),
        }, T - 86400 * 9),
        "Обои": dir({ "Горы.svg": file("svg", { seed: 3 }), "Волна.svg": file("svg", { seed: 8 }), "Сетка.svg": file("svg", { seed: 21 }) }, T - 86400 * 120),
        "Разобрать позже": dir({}, T - 86400 * 2),
        "Семейное фото.jpg": file("photo", { seed: 99, w: 1200, h: 800, mtime: T - 86400 * 15 }),
      }, T - 86400 * 2),
      sh_docs: dir({
        "Квитанции": dir({
          "Электричество — сентябрь 2025.pdf": file("pdf", { title: "Electricity bill - September 2025", lines: ["Account: 4401-2290-17", "Period: 01.09.2025 - 30.09.2025", "Day tariff: 182 kWh x 6.43 = 1 170.26 RUB", "Night tariff: 96 kWh x 2.95 = 283.20 RUB", "", "Total due: 1 453.46 RUB", "Pay before: 25.10.2025"], mtime: T - 86400 * 3 }),
          "Вода — сентябрь 2025.pdf": file("pdf", { title: "Water bill - September 2025", lines: ["Cold water: 4.2 m3", "Hot water: 2.9 m3", "Total due: 1 288.40 RUB"], mtime: T - 86400 * 3 }),
          "Интернет — 2025.pdf": file("pdf", { title: "Internet - annual invoice 2025", lines: ["Plan: Home 500", "12 months x 650 RUB", "Total: 7 800 RUB"], mtime: T - 86400 * 200 }),
        }, T - 86400 * 3),
        "Рецепты": dir({ "Борщ.md": file("text", { text: RECIPE_BORSCH }), "Сырники.md": file("text", { text: RECIPE_SYRNIKI }) }, T - 86400 * 40),
        "Договор аренды гаража.pdf": file("pdf", { title: "Garage lease agreement", lines: ["Parties: A. Petrov, Garage cooperative No. 12", "Term: 12 months", "Monthly rent: 3 000 RUB"], mtime: T - 86400 * 300 }),
        "Заметки.txt": file("text", { text: NOTES_TXT, mtime: T - 3600 * 5 }),
        "backup-config.json": file("text", { text: CONFIG_JSON, mtime: T - 86400 * 12 }),
        "Отчёт о бэкапе.log": file("text", { text: BACKUP_LOG, mtime: T - 3600 * 9 }),
        "Расходы.csv": file("text", { text: EXPENSES_CSV, mtime: T - 86400 * 4 }),
        "Паспорта (скан).zip": file("big", { size: 14_336_211, mtime: T - 86400 * 500 }),
        "test-page.html": file("text", { text: README_HTML, mtime: T - 86400 * 70 }),
      }, T - 3600 * 5),
      sh_video: dir({
        "Отпуск 2024.webm": file("video", { mtime: T - 86400 * 330 }),
        "Мультики": dir({ "Ну, погоди! — выпуск 1.mkv": file("big", { size: 734_003_200, mtime: T - 86400 * 800 }), "Простоквашино.webm": file("video", { mtime: T - 86400 * 700 }) }, T - 86400 * 700),
        "Музыка": dir({ "Колыбельная.wav": file("wav", { melody: LULLABY, bpm: 92 }), "Марш.wav": file("wav", { melody: MARCH, bpm: 120 }), "Тон 440 Гц.wav": file("wav", { melody: TONE, bpm: 60 }) }, T - 86400 * 100),
      }, T - 86400 * 100),
    },
    "home-server": {
      sh_media: dir({
        "Фильмы": dir({ "Москва слезам не верит (1979).mkv": file("big", { size: 4_402_341_888 }), "Ирония судьбы (1975).mkv": file("big", { size: 3_701_221_376 }), "Трейлер.webm": file("video") }),
        "Музыка": dir({ "Марш.wav": file("wav", { melody: MARCH, bpm: 120 }) }),
        "Постеры": dir(photos("poster_", 3, 1, T - 86400 * 20, 140)),
      }),
      sh_backups: dir({
        "nas-2025-10-01.tar.gz": file("big", { size: 13_312_000_000, mtime: T - 86400 * 2 }),
        "nas-2025-10-02.tar.gz": file("big", { size: 13_316_400_000, mtime: T - 86400 }),
        "laptop-2025-09-28.tar.gz": file("big", { size: 48_221_000_000, mtime: T - 86400 * 5 }),
        "README.txt": file("text", { text: "Автоматические резервные копии. Хранятся 7 дней.\n" }),
      }),
    },
    phone: {
      sh_camera: dir({ ...photos("IMG_20251003_", 6, 141205, T - 86400, 200), "VID_20251001_183012.webm": file("video", { mtime: T - 86400 * 2 }) }),
    },
    "dad-pc": {
      sh_shared: dir({ "Юбилей — список гостей.txt": file("text", { text: "Тётя Валя\nДядя Коля с Ирой\nСоседи — Петровы\nАндрей с семьёй\n" }), "Фото с юбилея": dir(photos("DSC_", 4, 4410, T - 86400 * 10, 300)) }),
    },
    laptop: {
      sh_docs: dir({
        "Резюме.pdf": file("pdf", { title: "Andrey Petrov - CV", lines: ["Software engineer", "Go, distributed systems, networking"] }),
        "Список дел.md": file("text", { text: "# На неделю\n\n- [x] Поменять масло\n- [ ] Записаться к стоматологу\n- [ ] Разобрать фото с Байкала\n" }),
        "Фото паспорта.jpg": file("photo", { seed: 404 }),
        "Проекты": dir({ "themesh-ideas.txt": file("text", { text: "Идея: показывать схему сети прямо на главном экране.\n" }) }),
      }),
      sh_drop: dir({
        "IMG_20251003_141205.jpg": file("photo", { seed: 200 }),
        "Маршрут.gpx": file("text", { text: '<?xml version="1.0"?><gpx version="1.1"></gpx>\n' }),
      }),
      sh_old: dir({}),
    },
  };
}

// ---------------------------------------------------------------- local folder trees (folder picker)
function laptopTree() {
  return {
    sep: "/", home: "/home/andrey", roots: ["/"],
    tree: {
      "": ["bin", "etc", "home", "media", "mnt", "opt", "srv", "usr", "var"],
      "/home": ["andrey"],
      "/home/andrey": ["Desktop", "Documents", "Downloads", "Music", "Pictures", "Projects", "Public", "Videos"],
      "/home/andrey/Documents": ["Архив", "Налоги", "Работа"],
      "/home/andrey/Downloads": ["The Mesh"],
      "/home/andrey/Pictures": ["2024", "2025", "Скриншоты"],
      "/home/andrey/Projects": ["themesh", "dotfiles", "garden-sensors"],
      "/home/andrey/Videos": [], "/home/andrey/Music": ["Плейлисты"], "/home/andrey/Desktop": [], "/home/andrey/Public": [],
      "/media": ["andrey"], "/media/andrey": ["BACKUP"], "/media/andrey/BACKUP": ["Фото", "Старое"],
    },
  };
}
function linuxTree(user, extra = {}) {
  return {
    sep: "/", home: `/home/${user}`, roots: ["/"],
    tree: {
      "": ["bin", "etc", "home", "mnt", "opt", "srv", "var", "volume1"],
      "/home": [user], [`/home/${user}`]: ["Загрузки", "docker", "scripts"],
      "/srv": ["media", "backups", "www"], "/srv/media": ["Фильмы", "Музыка", "Постеры"],
      "/mnt": ["usb", "disk2"], "/var": ["log", "lib"],
      "/volume1": ["photo", "docs", "video", "incoming", "homes"],
      ...extra,
    },
  };
}
function winTree() {
  return {
    sep: "\\", home: "C:\\Users\\Папа", roots: ["C:\\", "D:\\"],
    tree: {
      "C:\\": ["Program Files", "Users", "Windows"], "C:\\Users": ["Папа", "Public"],
      "C:\\Users\\Папа": ["Desktop", "Documents", "Downloads", "Pictures", "Общая"],
      "C:\\Users\\Папа\\Pictures": ["Юбилей", "Дача"], "D:\\": ["Фото", "Фильмы", "Резервные копии"],
    },
  };
}

// ---------------------------------------------------------------- world
const DEVICE_DEFS = [
  { key: "laptop", name: "laptop", owner: "Андрей", os: "linux", arch: "amd64", ip4: "100.64.0.1", ip6: "fd7a:5f3c:9e21::1" },
  { key: "phone", name: "phone", owner: "Андрей", os: "android", arch: "arm64", ip4: "100.64.0.2", ip6: "fd7a:5f3c:9e21::2", online: true, path: "relay", relayVia: "home-server", rttMs: 48.2, uptime: 86400 * 5 + 3600 * 3, shares: 1,
    services: [], nat: "hard", endpoints: ["100.84.17.203:41022"] },
  { key: "home-server", name: "home-server", owner: "Андрей", os: "linux", arch: "amd64", ip4: "100.64.0.3", ip6: "fd7a:5f3c:9e21::3", online: true, path: "direct", rttMs: 18.4, admin: true, uptime: 86400 * 41 + 3600 * 7, shares: 2,
    addr: "198.51.100.20:41710", endpoints: ["198.51.100.20:41710", "10.0.0.2:41710"], nat: "open",
    services: [{ name: "ssh", port: 22, description: "SSH" }, { name: "jellyfin", port: 8096, description: "Медиатека Jellyfin" }, { name: "grafana", port: 3000, description: "Графики и мониторинг" }] },
  { key: "nas", name: "nas", owner: "Андрей", os: "linux", arch: "arm64", ip4: "100.64.0.7", ip6: "fd7a:5f3c:9e21::7", online: true, path: "lan", rttMs: 0.9, uptime: 86400 * 12 + 3600 * 2, shares: 3,
    addr: "192.168.1.9:41710", endpoints: ["192.168.1.9:41710", "203.0.113.5:41711"], nat: "easy",
    services: [{ name: "ssh", port: 22, description: "SSH" }, { name: "nas-panel", port: 5000, description: "Веб-панель NAS" }] },
  { key: "dad-pc", name: "Компьютер папы", deviceName: "dad-pc", alias: "Компьютер папы", owner: "Папа", os: "windows", arch: "amd64", ip4: "100.64.0.9", ip6: "fd7a:5f3c:9e21::9", online: true, path: "direct", rttMs: 35.7, version: "0.0.9", uptime: 3600 * 6, shares: 1,
    addr: "91.122.40.18:51022", endpoints: ["91.122.40.18:51022", "192.168.0.104:41710"], nat: "easy", services: [], clockSkewMs: 3400 },
  { key: "mom-laptop", name: "mom-laptop", owner: "Мама", os: "windows", arch: "amd64", ip4: "100.64.0.10", ip6: "fd7a:5f3c:9e21::a", online: false, lastSeenAgo: 86400 * 2 + 3600 * 5, shares: 0, services: [] },
  { key: "old-tablet", name: "old-tablet", owner: "Андрей", os: "ios", arch: "arm64", ip4: "100.64.0.12", ip6: "fd7a:5f3c:9e21::c", online: false, lastSeenAgo: 86400 * 23, shares: 0, services: [],
    lastError: "handshake timeout: no reply from 3 endpoints" },
];

const W = {}; // the world
const ids = {}; // key → id

// Device names are DNS labels: same rules as SanitizeName / UniqueName in
// internal/identity/authority.go (Cyrillic transliterated, accents stripped…).
const TRANSLIT = new Map(Object.entries({
  а: "a", б: "b", в: "v", г: "g", д: "d", е: "e", ё: "yo", ж: "zh", з: "z", и: "i", й: "y", к: "k", л: "l", м: "m",
  н: "n", о: "o", п: "p", р: "r", с: "s", т: "t", у: "u", ф: "f", х: "kh", ц: "ts", ч: "ch", ш: "sh", щ: "shch",
  ъ: "", ы: "y", ь: "", э: "e", ю: "yu", я: "ya", і: "i", ї: "yi", є: "ye", ґ: "g", ў: "u",
}));
function sanitizeName(input) {
  let out = "";
  for (const ch of String(input || "").trim().normalize("NFC").toLowerCase()) {
    const tr = TRANSLIT.get(ch);
    if (tr !== undefined) { out += tr; continue; }
    const base = ch.normalize("NFD").replace(/\p{Mn}+/gu, "");
    if (base) out += /^[a-z0-9]+$/.test(base) ? base : "-";
  }
  out = out.replace(/-{2,}/g, "-").replace(/^-+|-+$/g, "");
  if (out.length > 32) out = out.slice(0, 32).replace(/^-+|-+$/g, "");
  return out || "device";
}
function uniqueName(name, taken) {
  if (!taken.has(name)) return name;
  for (let i = 2; ; i++) {
    const suffix = `-${i}`;
    const cand = (name.length + suffix.length > 32 ? name.slice(0, 32 - suffix.length) : name) + suffix;
    if (!taken.has(cand)) return cand;
  }
}

function makeSelf(def, extra = {}) {
  return {
    id: devId(def.key), short: devId(def.key).slice(0, 8), name: def.name, owner: def.owner,
    ip4: def.ip4, ip6: def.ip6, admin: true, meshId: "k3j4h5g6f7d8", meshName: "Дом", udpPort: 41710,
    endpoints: [{ addr: "192.168.1.23:41710", kind: "local" }, { addr: "10.211.55.2:41710", kind: "local" }, { addr: "203.0.113.5:41710", kind: "stun" }, { addr: "203.0.113.5:41710", kind: "observed" }],
    nat: { mappingVaries: false, public: ["203.0.113.5:41710"], hasIPv6: false, stun: true, difficulty: "easy" },
    version: "0.1.0", os: def.os, arch: def.arch, started: now() - 3 * 3600 - 1260, configured: true,
    relay: true, relayed: { packets: 18342, bytes: 21_734_112 },
    ...extra,
  };
}

function makePeer(def) {
  const id = devId(def.key);
  const T = now();
  return {
    id, short: id.slice(0, 8), name: def.name, deviceName: def.deviceName || def.name, alias: def.alias || "", owner: def.owner,
    ip4: def.ip4, ip6: def.ip6, admin: !!def.admin, online: !!def.online, path: def.online ? def.path : "none",
    relayVia: def.online && def.path === "relay" ? def.relayVia : "", rttMs: def.online ? def.rttMs : 0, addr: def.online ? def.addr || "" : "",
    lastSeen: def.online ? T : T - (def.lastSeenAgo || 3600), connectedAt: def.online ? T - Math.floor((def.uptime || 7200) * 0.7) : 0,
    os: def.os, arch: def.arch, version: def.version || "0.1.0", caps: ["files", "mail", "chat", "tunnel", "relay"],
    uptime: def.online ? def.uptime || 0 : 0, shares: def.shares || 0, services: clone(def.services || []),
    txBytes: Math.floor(1e6 + Math.random() * 3e8), rxBytes: Math.floor(1e6 + Math.random() * 9e8),
    txRelay: def.path === "relay" ? 12_400_211 : 0, rxRelay: def.path === "relay" ? 31_288_001 : 0,
    lastError: def.lastError || "", endpoints: def.endpoints || [], clockSkewMs: def.clockSkewMs || (def.online ? 12 : 0),
  };
}

function remoteSelf(p, def) {
  const diff = def.nat || "easy";
  return {
    id: p.id, short: p.short, name: p.deviceName, owner: p.owner, ip4: p.ip4, ip6: p.ip6, admin: p.admin, meshId: "k3j4h5g6f7d8", meshName: "Дом",
    udpPort: 41710, endpoints: (p.endpoints || []).map((a, i) => ({ addr: a, kind: i === 0 && diff !== "hard" ? "observed" : "local" })),
    nat: { mappingVaries: diff === "hard", public: p.endpoints.slice(0, 1), hasIPv6: diff === "open", stun: true, difficulty: diff },
    version: p.version, os: p.os, arch: p.arch, started: now() - (p.uptime || 3600), configured: true, relay: true, relayed: { packets: 0, bytes: 0 },
  };
}

const defaultTun = (o = {}) => ({ enabled: false, manageHosts: true, state: "off", name: "themesh0", error: "", supported: true, txPackets: 0, rxPackets: 0, dropped: 0, ...o });
const defaultSettings = (o = {}) => ({
  downloadDir: "/home/andrey/Downloads/The Mesh", autoAccept: "own", autoAcceptMaxMB: 500, relay: true,
  stunEnabled: true, stunServers: ["stun.l.google.com:19302", "stun.cloudflare.com:3478"], udpPort: 41710, lan: true, portMap: true,
  socks: { enabled: false, listen: "127.0.0.1:1080" }, tun: defaultTun(), restartRequired: false, ...o,
});

// Router port mapping (UPnP / NAT-PMP): Self.portmap while Settings.portMap is on.
// A mapped port is offered first (endpoint kind "mapped") and makes the device "open".
const PM_STATES = ["mapped", "searching", "unavailable", "private"];
const PM_DEFAULT = { laptop: "mapped", nas: "mapped", "home-server": "unavailable", phone: "private", "dad-pc": "unavailable", "mom-laptop": "searching", "old-tablet": "searching" };
function portmapFor(key, state) {
  const ext = { laptop: "203.0.113.5:41710", nas: "203.0.113.5:41711" }[key] || "203.0.113.5:41712";
  if (state === "mapped") return { state, protocol: key === "nas" ? "natpmp" : "upnp", external: ext, gateway: "192.168.1.1", error: "" };
  if (state === "searching") return { state, gateway: "", error: "" };
  if (state === "private") return { state, gateway: "192.168.1.1", error: "the router's external address 100.72.14.9 is not public (carrier-grade NAT)" };
  return { state: "unavailable", gateway: "", error: "no UPnP IGD or NAT-PMP gateway answered" };
}
/** Put device `key` into port-mapping `state` (null = switched off). */
function applyPortmap(key, self, state) {
  if (!self || !self.nat) return;
  self.endpoints = (self.endpoints || []).filter((e) => e.kind !== "mapped");
  self.nat.difficulty = (W.natBase && W.natBase[key]) || "easy";
  if (!state) { delete self.portmap; return; }
  self.portmap = portmapFor(key, state);
  if (state === "mapped") { self.endpoints.unshift({ addr: self.portmap.external, kind: "mapped" }); self.nat.difficulty = "open"; }
}

function addLog(level, msg, dev = "laptop") {
  const d = W.devLogs[dev] || (W.devLogs[dev] = []);
  d.push({ ts: now(), level, msg });
  if (d.length > 600) d.splice(0, d.length - 600);
}

function seedLogs(dev, lines) {
  const T = now();
  W.devLogs[dev] = lines.map((l, i) => ({ ts: T - (lines.length - i) * 37, level: l[0], msg: l[1] }));
}

function buildWorld(scenario) {
  for (const k of Object.keys(W)) delete W[k];
  for (const d of DEVICE_DEFS) ids[d.key] = devId(d.key);
  W.configured = scenario !== "onboarding";
  W.devLogs = {};
  W.trees = buildTrees();
  W.blobs = new Map();
  W.invites = [];
  W.transfers = [];
  W.mail = [];
  W.chat = new Map();
  W.forwards = [];
  W.portSeq = 2222;
  const selfDef = DEVICE_DEFS[0];
  if (scenario === "onboarding") {
    W.self = { id: devId("laptop"), short: devId("laptop").slice(0, 8), version: "0.1.0", os: "linux", arch: "amd64", configured: false };
    W.peers = [];
    W.settings = defaultSettings();
    W.shares = []; W.services = [];
    W.remote = {};
    seedLogs("laptop", [["info", "themesh 0.1.0 starting (linux/amd64)"], ["info", "device key loaded: " + devId("laptop").slice(0, 8)], ["info", "not a member of any mesh yet — open the UI to create or join one"]]);
    return;
  }
  W.self = makeSelf(selfDef);
  W.settings = defaultSettings();
  W.natBase = { laptop: "easy" };
  W.pmTarget = {}; // the state a switched-on mapping settles in (default PM_DEFAULT)
  applyPortmap("laptop", W.self, PM_DEFAULT.laptop);
  W.shares = [
    { id: "sh_docs", name: "Документы", path: "/home/andrey/Documents", mode: "ro", allow: ["*"], exists: true },
    { id: "sh_drop", name: "Входящие", path: "/home/andrey/Downloads/The Mesh", mode: "rw", allow: [ids.phone, ids.nas], exists: true },
    { id: "sh_old", name: "Старый проект", path: "/media/andrey/OLD-DISK/project", mode: "ro", allow: ["*"], exists: false },
    // saved before the node refused such folders: it holds ~/.config/themesh, so it is not served
    { id: "sh_home", name: "Домашняя папка", path: "/home/andrey", mode: "ro", allow: [ids.phone], exists: true },
  ];
  W.services = [
    { id: "sv_ssh", name: "ssh", addr: "127.0.0.1:22", description: "SSH на ноутбуке", allow: [ids["home-server"]] },
    { id: "sv_vite", name: "notes-dev", addr: "127.0.0.1:5173", description: "Черновик сайта заметок", allow: ["*"] },
  ];
  W.localfs = { laptop: laptopTree(), nas: linuxTree("admin", { "/volume1/photo": ["2024", "2025", "Обои"], "/volume1/docs": ["Квитанции", "Рецепты"], "/volume1/video": ["Мультики", "Музыка"] }), "home-server": linuxTree("andrey"), phone: { sep: "/", home: "/storage/emulated/0", roots: ["/storage/emulated/0"], tree: { "/storage/emulated/0": ["DCIM", "Download", "Documents", "Pictures"], "/storage/emulated/0/DCIM": ["Camera", "Screenshots"] } }, "dad-pc": winTree() };

  seedLogs("laptop", [
    ["info", "themesh 0.1.0 starting (linux/amd64)"], ["info", "mesh «Дом» (k3j4h5g6f7d8), 7 members, this device is admin"],
    ["info", "udp listening on 0.0.0.0:41710"], ["info", "stun stun.l.google.com:19302 → 203.0.113.5:41710"],
    ["info", "portmap: upnp mapped 203.0.113.5:41710 → 192.168.1.23:41710 (gateway 192.168.1.1)"],
    ["info", "nat: mapping stable across servers → difficulty easy"], ["info", "lan: discovered nas at 192.168.1.9:41710"],
    ["info", "peer nas: handshake ok via 192.168.1.9:41710 (lan, 0.9 ms)"], ["info", "peer home-server: handshake ok via 198.51.100.20:41710 (direct)"],
    ["warn", "peer phone: direct probes failed (symmetric nat), using relay home-server"], ["info", "peer dad-pc: hole punched 91.122.40.18:51022 (direct)"],
    ["warn", "peer dad-pc: clock skew 3.4 s"], ["debug", "relay: forwarded 120 packets for phone ↔ nas"],
    ["warn", "peer old-tablet: handshake timeout: no reply from 3 endpoints"], ["info", "mail: delivered m_set1 to nas, home-server"],
    ["info", "transfer t_pres: sending «Презентация.pdf» to nas"], ["error", "transfer t_bak: connection reset by peer (home-server)"],
  ]);
  seedLogs("nas", [["info", "themesh 0.1.0 starting (linux/arm64)"], ["info", "share «Фото» → /volume1/photo (rw)"], ["info", "backup finished: 12.4 GB"], ["warn", "disk /volume1 85% full"]]);
  seedLogs("home-server", [["info", "themesh 0.1.0 starting (linux/amd64)"], ["info", "public address 198.51.100.20:41710 (open)"], ["info", "relaying for phone (31 MB today)"]]);
  seedLogs("phone", [["info", "themesh 0.1.0 starting (android/arm64)"], ["warn", "nat: mapping varies per destination → difficulty hard"], ["info", "using relay home-server"]]);
  seedLogs("dad-pc", [["info", "themesh 0.0.9 starting (windows/amd64)"], ["warn", "update available: 0.1.0"]]);

  if (scenario === "empty") {
    W.peers = [];
    W.shares = [];
    W.services = [];
    W.remote = {};
    W.self.relayed = { packets: 0, bytes: 0 };
    return;
  }

  W.peers = DEVICE_DEFS.slice(1).map(makePeer);
  W.remote = {};
  for (const d of DEVICE_DEFS.slice(1)) {
    const p = W.peers.find((x) => x.id === ids[d.key]);
    const linux = d.os === "linux";
    W.remote[d.key] = { self: remoteSelf(p, d), settings: defaultSettings({
      downloadDir: d.key === "dad-pc" ? "C:\\Users\\Папа\\Downloads\\The Mesh" : d.key === "phone" ? "/storage/emulated/0/Download/The Mesh" : "/srv/themesh/incoming",
      relay: d.key !== "phone", autoAccept: d.key === "nas" ? "all" : "own",
      tun: defaultTun(linux ? { enabled: true, state: "running", txPackets: 182_311, rxPackets: 240_877, dropped: 12 } : { supported: false }),
    }) };
    W.natBase[d.key] = d.nat || "easy";
    applyPortmap(d.key, W.remote[d.key].self, PM_DEFAULT[d.key]);
  }
  W.remote.nas.shares = [
    { id: "sh_photo", name: "Фото", path: "/volume1/photo", mode: "rw", allow: ["*"], exists: true },
    { id: "sh_docs", name: "Документы", path: "/volume1/docs", mode: "rw", allow: [ids.laptop, ids.phone], exists: true },
    { id: "sh_video", name: "Видео", path: "/volume1/video", mode: "ro", allow: ["*"], exists: true },
  ];
  W.remote.nas.services = [
    { id: "sv_ssh", name: "ssh", addr: "127.0.0.1:22", description: "SSH", allow: ["*"] },
    { id: "sv_panel", name: "nas-panel", addr: "127.0.0.1:5000", description: "Веб-панель NAS", allow: [ids.laptop] },
  ];
  W.remote["home-server"].shares = [
    { id: "sh_media", name: "Медиа", path: "/srv/media", mode: "ro", allow: ["*"], exists: true },
    { id: "sh_backups", name: "Бэкапы", path: "/srv/backups", mode: "ro", allow: [ids.laptop], exists: true },
  ];
  W.remote["home-server"].services = [
    { id: "sv_ssh", name: "ssh", addr: "127.0.0.1:22", description: "SSH", allow: [ids.laptop] },
    { id: "sv_jf", name: "jellyfin", addr: "127.0.0.1:8096", description: "Медиатека Jellyfin", allow: ["*"] },
    { id: "sv_gr", name: "grafana", addr: "127.0.0.1:3000", description: "Графики и мониторинг", allow: [ids.laptop] },
  ];
  W.remote.phone.shares = [{ id: "sh_camera", name: "Камера", path: "/storage/emulated/0/DCIM/Camera", mode: "ro", allow: ["*"], exists: true }];
  W.remote.phone.services = [];
  W.remote["dad-pc"].shares = [{ id: "sh_shared", name: "Общая", path: "C:\\Users\\Папа\\Общая", mode: "ro", allow: ["*"], exists: true }];
  W.remote["dad-pc"].services = [];
  W.remote["mom-laptop"].shares = [];
  W.remote["mom-laptop"].services = [];
  W.remote["old-tablet"].shares = [];
  W.remote["old-tablet"].services = [];
  // keep the peers' share counters in sync with the remote configs
  for (const p of W.peers) {
    const k = keyOf(p.id);
    if (W.remote[k]) p.shares = (W.remote[k].shares || []).filter((s) => s.allow.includes("*") || s.allow.includes(ids.laptop)).length;
  }

  seedTransfers();
  seedMail();
  seedChat();
  W.invites = [makeInvite(false, 47, "Анна")];
  W.forwards = [{ id: "fw_ssh", peer: ids["home-server"], peerName: "home-server", service: "ssh", listen: "127.0.0.1:2222", state: "listening", error: "", conns: 1 }];
}

function keyOf(id) {
  for (const [k, v] of Object.entries(ids)) if (v === id) return k;
  return null;
}
function peerByAny(x) {
  if (!x) return null;
  return W.peers.find((p) => p.id === x || p.deviceName === x || p.name === x || keyOf(p.id) === x) || null;
}
function nameOf(id) {
  if (id === W.self.id) return W.self.name;
  const p = W.peers.find((x) => x.id === id);
  return p ? p.name : id.slice(0, 8);
}

function makeInvite(admin, ttlMinutes, owner) {
  const code = inviteCode(admin);
  return { id: rid("inv_"), code, admin, owner: owner || W.self.owner || "", created: now() - 60 * 13, expires: now() + ttlMinutes * 60, qrSvg: fakeQrSvg(code) };
}
/** Like SanitizeOwner: blanks collapse, at most 64 characters; empty → the inviter's own owner. */
function cleanOwner(v) {
  return Array.from(String(v ?? "").replace(/[\u0000-\u001f\u007f]/g, "").replace(/\s+/g, " ").trim()).slice(0, 64).join("").trim();
}

// ---------------------------------------------------------------- transfers
function seedTransfers() {
  const T = now();
  const tr = (o) => ({ id: rid("t_"), mime: "application/octet-stream", speed: 0, error: "", created: T - 600, updated: T - 60, finished: null, done: 0, seed: true, ...o });
  W.transfers = [
    tr({ id: "t_offer", dir: "in", peer: ids["dad-pc"], peerName: "Компьютер папы", name: "Сканы документов.pdf", size: 2_411_920, mime: "application/pdf", state: "offered", created: T - 240, updated: T - 240, gen: { kind: "pdf", title: "Scanned documents" } }),
    tr({ id: "t_pres", dir: "out", peer: ids.nas, peerName: "nas", name: "Презентация.pdf", size: 8_808_038, done: 3_970_000, mime: "application/pdf", state: "active", speed: 2_350_000, created: T - 30 }),
    tr({ id: "t_mom", dir: "out", peer: ids["mom-laptop"], peerName: "mom-laptop", name: "Фото для мамы.zip", size: 125_829_120, mime: "application/zip", state: "queued", created: T - 3600 * 20, updated: T - 3600 * 20 }),
    tr({ id: "t_img", dir: "in", peer: ids.phone, peerName: "phone", name: "IMG_20251003_141205.jpg", size: 0, mime: "image/jpeg", state: "done", created: T - 7300, updated: T - 7200, finished: T - 7200, path: "/home/andrey/Downloads/The Mesh/IMG_20251003_141205.jpg", gen: { kind: "photo", seed: 200 } }),
    tr({ id: "t_bak", dir: "out", peer: ids["home-server"], peerName: "home-server", name: "notes-backup.tar", size: 52_428_800, done: 18_874_368, mime: "application/x-tar", state: "failed", error: "connection reset by peer", created: T - 5400, updated: T - 5300 }),
    tr({ id: "t_gpx", dir: "out", peer: ids.phone, peerName: "phone", name: "Маршрут.gpx", size: 48_211, done: 48_211, mime: "application/gpx+xml", state: "done", created: T - 86400, updated: T - 86300, finished: T - 86300 }),
    tr({ id: "t_dec", dir: "in", peer: ids.nas, peerName: "nas", name: "debug-dump.bin", size: 734_003, mime: "application/octet-stream", state: "declined", created: T - 86400 * 2, updated: T - 86400 * 2 }),
  ];
  const img = W.transfers.find((x) => x.id === "t_img");
  img.size = img.done = fileBytes("tr/t_img", { kind: "photo", seed: 200 }, img.name).length;
}

const publicTransfer = (t) => {
  const { gen, data, timers, seed, waitingSince, offeredAt, ...rest } = t;
  return rest;
};
function emitTransfer(t) {
  t.updated = now();
  broadcast("transfer", publicTransfer(t));
}
function offersCount() { return W.transfers.filter((t) => t.dir === "in" && t.state === "offered").length; }

// ---------------------------------------------------------------- mail
function seedMail() {
  const T = now();
  const me = { id: ids.laptop, name: "laptop" };
  const from = (k) => ({ id: ids[k], name: W.peers.find((p) => p.id === ids[k]).name });
  const to = (k, state, at) => ({ id: ids[k], name: W.peers.find((p) => p.id === ids[k]).name, state, at });
  const att = (name, kind, st = "ready", extra = {}) => {
    const node = { kind, ...extra };
    const bytes = kind === "big" ? null : fileBytes("att/" + name, node, name);
    const size = bytes ? bytes.length : extra.size;
    return { name, size, mime: mimeOf(name), sha256: bytes ? sha(bytes) : sha(Buffer.from(name)), state: st, got: st === "ready" ? size : st === "fetching" ? Math.floor(size * 0.4) : 0, node };
  };
  let n = 0;
  const m = (o) => ({ id: "m_" + (++n).toString(36).padStart(4, "0"), kind: "mail", thread: "th_" + n, starred: false, unread: false, inReplyTo: null, attachments: [], to: [{ ...me, state: "delivered", at: o.ts }], ...o });
  W.mail = [
    m({ folder: "inbox", from: from("home-server"), subject: "Резервное копирование завершено", ts: T - 1500, unread: true,
      body: "Ночной бэкап прошёл успешно.\n\nСкопировано: 12,4 ГБ (3 812 файлов)\nДлительность: 6 мин 14 с\nСледующий запуск: сегодня в 03:00\n\nПодробный журнал — во вложении. Панель мониторинга: http://100.64.0.3:3000/d/backup",
      attachments: [att("backup-2025-10-03.log", "text", "ready", { text: BACKUP_LOG })] }),
    m({ folder: "inbox", from: from("dad-pc"), subject: "Фото с юбилея", ts: T - 3600 * 3, unread: true, starred: true,
      body: "Андрей, привет!\n\nСкинул фотки с юбилея бабушки, посмотри. Остальные выложу на NAS в папку «2025».\nВидео тоже приложил, но оно большое — скачай, когда будет удобно.\n\nПапа",
      attachments: [att("DSC_4410.jpg", "photo", "ready", { seed: 300 }), att("DSC_4413.jpg", "photo", "ready", { seed: 301 }), att("DSC_4416.jpg", "photo", "fetching", { seed: 302 }), att("Видео с юбилея.mp4", "big", "remote", { size: 32_400_000 })] }),
    m({ folder: "inbox", from: from("phone"), subject: "Ссылки на потом", ts: T - 3600 * 7,
      body: "Почитать в выходные:\nhttps://go.dev/doc/effective_go\nhttps://ru.wikipedia.org/wiki/NAT\nhttps://tailscale.com/blog/how-nat-traversal-works\n\nИ не забыть про (https://example.org/скобки) в конце." }),
    m({ folder: "inbox", from: from("nas"), subject: "Диск заполнен на 85 %", ts: T - 86400 - 3600 * 2,
      body: "Том /volume1 заполнен на 85 % (3,4 из 4,0 ТБ).\n\nБольше всего места занимают:\n  Видео — 1,9 ТБ\n  Фото — 1,1 ТБ\n  Резервные копии — 310 ГБ\n\nСовет: удалите старые копии или добавьте диск." }),
    m({ folder: "inbox", from: from("mom-laptop"), subject: "Рецепт пирога", ts: T - 86400 * 2 - 3600 * 4,
      body: "Сынок, вот рецепт, как обещала. Тесто лучше делать накануне.\n\nЦелую, мама",
      attachments: [att("Пирог с капустой.pdf", "pdf", "remote", { title: "Cabbage pie", lines: ["Flour 500 g", "Butter 200 g", "Cabbage 1 kg"] })] }),
    m({ folder: "inbox", from: from("dad-pc"), subject: "Re: Как подключить ноутбук", ts: T - 86400 * 3, inReplyTo: null,
      body: "Получилось! Ноутбук теперь в списке. Спасибо.\n\n> Запусти themesh и выбери «Подключиться по приглашению»." }),
    m({ folder: "inbox", from: from("home-server"), subject: "Температура процессора 78 °C", ts: T - 86400 * 4,
      body: "Последний час процессор home-server работает на 78 °C (порог 75 °C).\nПроверьте вентилятор и пыль в корпусе." }),
    m({ folder: "inbox", from: from("phone"), subject: "Скриншот ошибки", ts: T - 86400 * 6,
      body: "Вот что показывает приложение банка при входе.", attachments: [att("Screenshot_20250927.png", "photo", "failed", { seed: 77, w: 540, h: 960 })] }),
  ];
  // Older daily reports to make paging meaningful.
  for (let i = 0; i < 64; i++) {
    const ts = T - 86400 * (7 + i) - 3600 * 3;
    W.mail.push(m({ folder: "inbox", from: from(i % 3 === 0 ? "nas" : "home-server"), subject: i % 3 === 0 ? "Отчёт NAS за неделю" : "Ежедневный отчёт home-server",
      ts, body: i % 3 === 0 ? `Свободно: ${(0.9 - i * 0.004).toFixed(2)} ТБ\nОшибок дисков: 0\nСредняя температура: 41 °C` : `Аптайм: ${41 - i} дн.\nНагрузка: 0,${12 + (i % 7)}\nБэкап: успешно (${(12 + (i % 5) * 0.1).toFixed(1)} ГБ)`,
      attachments: i % 9 === 0 ? [att(`report-${i}.csv`, "text", "ready", { text: "metric;value\ncpu;12\n" })] : [] }));
  }
  const sent = (o) => m({ folder: "sent", from: me, unread: false, ...o });
  W.mail.push(
    sent({ to: [to("nas", "delivered", T - 3600 * 26), to("home-server", "delivered", T - 3600 * 26)], subject: "Настройки бэкапа", ts: T - 3600 * 26,
      body: "Поменял расписание: теперь бэкап в 03:00, храним 7 дневных и 4 недельные копии.\nКонфиг во вложении.", attachments: [att("backup-config.json", "text", "ready", { text: CONFIG_JSON })] }),
    sent({ to: [to("mom-laptop", "queued", 0)], subject: "Фото внуков", ts: T - 3600 * 20,
      body: "Мама, привет! Вот фотографии с прогулки. Как включишь ноутбук — они придут сами.", attachments: [att("Прогулка 1.jpg", "photo", "ready", { seed: 500 }), att("Прогулка 2.jpg", "photo", "ready", { seed: 501 })] }),
    sent({ to: [to("dad-pc", "delivered", T - 86400 * 3 - 4000)], subject: "Как подключить ноутбук", ts: T - 86400 * 3 - 4100,
      body: "Папа, всё просто:\n1. Скачай themesh и запусти.\n2. Выбери «Подключиться по приглашению».\n3. Вставь код, который я пришлю в чат.\n\nИнструкция с картинками: https://example.org/themesh/join" }),
    sent({ to: [to("old-tablet", "failed", T - 86400 * 9)], subject: "Тест", ts: T - 86400 * 9, body: "Проверка доставки." }),
    sent({ to: [to("phone", "sent", T - 300)], subject: "Список покупок", ts: T - 320, body: "Молоко, хлеб, яйца, сыр, яблоки.\nИ корм коту!" }),
  );
  const trash = (o) => m({ folder: "trash", ...o });
  W.mail.push(
    trash({ from: from("home-server"), subject: "Ежедневный отчёт home-server", ts: T - 86400 * 80, body: "Аптайм: 1 дн." }),
    trash({ from: from("nas"), subject: "Обновление прошивки установлено", ts: T - 86400 * 95, body: "Версия 7.2.1 установлена. Перезагрузка выполнена." }),
  );
}

function mailSummary(x) {
  return {
    id: x.id, kind: "mail", folder: x.folder, from: x.from, to: x.to.map(({ qAt, ...r }) => r), subject: x.subject,
    snippet: (x.body || "").replace(/\s+/g, " ").slice(0, 140), ts: x.ts, unread: x.unread,
    attachments: x.attachments.length, thread: x.thread, starred: x.starred,
  };
}
function mailFull(x) {
  return { ...mailSummary(x), body: x.body, inReplyTo: x.inReplyTo, attachments: x.attachments.map(attPublic) };
}

// Received attachments over 25 MB are not fetched on their own: `needsConsent`
// (only when true) until the user asks (POST …/fetch), like the node.
const MAX_AUTO_FETCH = 25 << 20;
function attPublic({ node, data, want, timer, ...a }) {
  return a.state === "remote" && a.size > MAX_AUTO_FETCH && !want ? { ...a, needsConsent: true } : a;
}
/** POST …/fetch: queued (back to remote), then fetching with progress, then ready; `done` sends the end event. */
function fetchAttachment(a, done) {
  if (a.state !== "remote" && a.state !== "failed") return; // ready / already fetching: nothing to do
  a.want = true; a.state = "remote"; a.got = 0;
  clearInterval(a.timer);
  let k = 0;
  setTimeout(() => {
    a.state = "fetching";
    a.timer = setInterval(() => {
      k++;
      a.got = Math.min(a.size, Math.round((a.size * k) / 8));
      if (k < 8) return;
      clearInterval(a.timer); a.timer = null;
      a.state = "ready"; a.got = a.size;
      done();
    }, 450);
  }, 500);
}
/** Bytes of a received attachment, refusing the ones that are not here yet. */
function sendAttachment(req, res, a, prefix, q) {
  if (a.state === "remote") throw E.notfound("attachment has not been downloaded yet");
  if (a.state === "fetching") throw E.exists("attachment is still downloading");
  if (a.state === "failed") throw E.notfound("attachment could not be fetched");
  const mime = a.node && a.node.kind === "photo" ? "image/png" : a.mime;
  if (a.node && a.node.kind === "big") return sendBytes(req, res, { size: a.size, seed: 11, mime, name: a.name, dl: q.get("dl") === "1" });
  const buf = (a.node && a.node.data) || fileBytes(prefix + a.name, a.node, a.name);
  sendBytes(req, res, { buf, mime, name: a.name, dl: q.get("dl") === "1" });
}
function mailCounters() { return W.mail.filter((m) => m.folder === "inbox" && m.unread).length; }

// ---------------------------------------------------------------- chat
function seedChat() {
  const T = now();
  let n = 0;
  const msg = (peerKey, mine, text, ago, state = "delivered", attachments = []) => ({
    id: "c_" + (++n).toString(36).padStart(4, "0"), from: mine ? ids.laptop : ids[peerKey], to: mine ? ids[peerKey] : ids.laptop,
    mine, text, ts: T - ago, state, attachments, read: true,
  });
  const att = (name, kind, extra = {}) => {
    const node = { kind, ...extra };
    const b = fileBytes("chat/" + name, node, name);
    return { name, size: b.length, mime: mimeOf(name), sha256: sha(b), state: "ready", node };
  };
  W.chat.set(ids["dad-pc"], [
    msg("dad-pc", false, "Андрей, привет! Программа заработала 👍", 86400 * 2 + 4000),
    msg("dad-pc", true, "Отлично! Теперь можно кидать фото прямо на NAS", 86400 * 2 + 3700),
    msg("dad-pc", false, "А куда именно? Там папки какие-то", 86400 * 2 + 3500),
    msg("dad-pc", true, "Файлы → Обзор → nas → Фото. Папка 2025, создай там «Юбилей»", 86400 * 2 + 3300),
    msg("dad-pc", false, "Понял, вечером попробую", 86400 * 2 + 3000),
    msg("dad-pc", true, "Вот так выглядит", 86400 + 7200, "delivered", [att("Скриншот.png", "photo", { seed: 900, w: 640, h: 400 })]),
    msg("dad-pc", false, "Всё загрузил, проверь", 3600 * 3 + 120),
    msg("dad-pc", true, "Вижу, спасибо! Бабушка там отлично получилась", 3600 * 2),
    msg("dad-pc", false, "Вот видео, оно большое", 600, "delivered", [{ name: "Юбилей.mp4", size: 48_234_496, mime: "video/mp4", sha256: sha(Buffer.from("Юбилей.mp4")), state: "remote", node: { kind: "big" } }]),
    msg("dad-pc", false, "Ок, вечером позвоню", 240),
  ]);
  W.chat.get(ids["dad-pc"]).slice(-2).forEach((x) => { x.read = false; });
  W.chat.set(ids.phone, [
    msg("phone", false, "https://github.com/quic-go/quic-go/issues/4512", 86400 * 5),
    msg("phone", false, "Код от калитки на даче: 4521", 86400 * 3),
    msg("phone", true, "Скинь фото счётчика", 3600 * 5),
    msg("phone", false, "", 3600 * 4 + 1800, "delivered", [att("Счётчик.jpg", "photo", { seed: 910, w: 600, h: 800 })]),
    msg("phone", true, "Спасибо!", 3600 * 4),
  ]);
  W.chat.set(ids["home-server"], [
    msg("home-server", false, "Бэкап завершён ✅ 12,4 ГБ за 6 мин", 86400 + 3600 * 3),
    msg("home-server", false, "⚠️ Температура CPU 78 °C", 3600 * 9),
    msg("home-server", false, "Бэкап завершён ✅ 12,5 ГБ за 6 мин", 1500),
  ]);
  W.chat.get(ids["home-server"]).slice(-1).forEach((x) => { x.read = false; });
  W.chat.set(ids["mom-laptop"], [
    msg("mom-laptop", false, "Андрюша, как открыть фото, которые ты прислал?", 86400 * 2 + 9000),
    msg("mom-laptop", true, "Мама, они в папке «Загрузки → The Mesh». Сейчас пришлю ещё", 86400 * 2 + 8000, "delivered"),
    msg("mom-laptop", true, "Посмотри, как включишь ноутбук 🙂", 3600 * 20, "queued"),
  ]);
}

function chatUnread() {
  let n = 0;
  for (const list of W.chat.values()) n += list.filter((m) => !m.mine && !m.read).length;
  return n;
}
const publicChat = (m) => {
  const { read, stAt, ...rest } = m;
  return { ...rest, attachments: (m.attachments || []).map(attPublic) };
};
function threads() {
  const out = [];
  for (const [peer, list] of W.chat.entries()) {
    if (!list.length) continue;
    const last = list[list.length - 1];
    const p = W.peers.find((x) => x.id === peer);
    out.push({
      peer: { id: peer, name: p ? p.name : peer.slice(0, 8), online: p ? p.online : false },
      last: { text: last.text || "", ts: last.ts, from: last.from, state: last.state },
      unread: list.filter((m) => !m.mine && !m.read).length,
    });
  }
  return out.sort((a, b) => b.last.ts - a.last.ts);
}

// ---------------------------------------------------------------- SSE
const clients = new Set();
let refuseSseUntil = 0;
function broadcast(type, data) {
  const msg = `event: ${type}\ndata: ${JSON.stringify(data)}\n\n`;
  for (const c of clients) c.write(msg);
}
function counters() { return { mail: mailCounters(), chat: chatUnread(), offers: offersCount() }; }
function emitCounters() { broadcast("counters", counters()); }
function emitPeers() { broadcast("peers", W.peers); }
function emitInvites() { broadcast("invites", W.invites); }

setInterval(() => { for (const c of clients) c.write(": ping\n\n"); }, 15000);

// ---------------------------------------------------------------- simulation
function speedFor(peerId) {
  const p = W.peers.find((x) => x.id === peerId);
  if (!p) return 1_000_000;
  const base = p.path === "lan" ? 11_000_000 : p.path === "relay" ? 900_000 : 3_200_000;
  return Math.round(base * (opts.calm ? 1 : 0.75 + Math.random() * 0.5));
}

function tick() {
  const T = now();
  let offersChanged = false;
  for (const t of W.transfers) {
    if (opts.calm && t.seed) continue; // keep the seeded snapshot stable for screenshots
    const peer = W.peers.find((p) => p.id === t.peer);
    const online = !!(peer && peer.online);
    if (t.state === "active") {
      if (!online) { t.state = "queued"; t.speed = 0; emitTransfer(t); continue; }
      t.speed = speedFor(t.peer);
      t.done = Math.min(t.size, t.done + Math.round(t.speed / 2));
      if (t.done >= t.size) {
        t.state = "done"; t.speed = 0; t.finished = T;
        if (t.dir === "in") t.path = path.posix.join(W.settings.downloadDir, t.name);
        addLog("info", `transfer ${t.id}: «${t.name}» ${t.dir === "in" ? "received from" : "delivered to"} ${t.peerName}`);
        if (t.dir === "in") broadcast("notify", { level: "success", title: "Файл получен", text: `«${t.name}» от ${t.peerName}`, link: "#/files/send" });
      }
      emitTransfer(t);
    } else if (t.state === "queued" && online && t.dir === "out" && !t.waitingSince) {
      t.waitingSince = Date.now();
    } else if (t.state === "queued" && online && t.dir === "out" && Date.now() - t.waitingSince > 1000) {
      t.waitingSince = 0;
      t.state = "offered"; t.offeredAt = Date.now(); emitTransfer(t);
    } else if (t.state === "offered" && t.dir === "out" && online && Date.now() - (t.offeredAt || 0) > (peer.owner === W.self.owner ? 1500 : 4000)) {
      t.state = "active"; t.speed = speedFor(t.peer); emitTransfer(t);
    } else if (t.state === "queued" && t.dir === "in" && online) {
      t.state = "active"; t.speed = speedFor(t.peer); emitTransfer(t);
    }
  }
  // mail delivery
  for (const m of W.mail) {
    if (m.folder !== "sent") continue;
    if (opts.calm && !m.to.some((r) => r.qAt)) continue;
    let changed = false;
    for (const r of m.to) {
      const p = W.peers.find((x) => x.id === r.id);
      if (!p || !p.online) continue;
      if (r.state === "queued" && T - (r.qAt || 0) >= 1) { r.state = "sent"; r.at = T; r.qAt = T; changed = true; }
      else if (r.state === "sent" && T - (r.qAt || r.at || 0) >= 2) { r.state = "delivered"; r.at = T; changed = true; }
    }
    if (changed) broadcast("mail", { id: m.id, folder: m.folder, unread: m.unread });
  }
  // chat delivery
  for (const [peer, list] of W.chat.entries()) {
    const p = W.peers.find((x) => x.id === peer);
    if (!p || !p.online) continue;
    for (const c of list) {
      if (!c.mine) continue;
      if (c.state === "queued" && Date.now() - (c.stAt || 0) > 700) { c.state = "sent"; c.stAt = Date.now(); broadcast("chat", { ...publicChat(c), peer }); }
      else if (c.state === "sent" && Date.now() - (c.stAt || 0) > 1600) { c.state = "delivered"; broadcast("chat", { ...publicChat(c), peer }); }
    }
  }
  // live TUN counters
  for (const st of [W.settings, ...Object.values(W.remote || {}).map((r) => r.settings)]) {
    const tun = st && st.tun;
    if (!tun || tun.state !== "running") continue;
    tun.txPackets += 3 + Math.floor(Math.random() * 40);
    tun.rxPackets += 4 + Math.floor(Math.random() * 55);
    if (Math.random() < 0.03) tun.dropped += 1;
  }
  // invites expiry
  const before = W.invites.length;
  W.invites = W.invites.filter((i) => i.expires > T);
  if (W.invites.length !== before) emitInvites();
  if (offersChanged) emitCounters();
}
setInterval(() => { if (W.configured) tick(); }, 500);

// Gentle background life (disabled with --calm).
const DAD_LINES = ["Ты где сейчас?", "Посмотри фото на NAS, я ещё добавил", "Не могу найти, куда сохраняются файлы", "Спасибо, всё работает!", "Позвони, как освободишься"];
const SERVER_LINES = ["Бэкап завершён ✅ 12,6 ГБ за 6 мин", "Обновления установлены, перезагрузка не нужна", "⚠️ Свободно меньше 10 % на /srv"];
let bgN = 0;
function background() {
  if (opts.calm || !W.configured || !W.peers.length) return;
  bgN++;
  // RTT jitter for online peers
  for (const p of W.peers) if (p.online && p.rttMs) p.rttMs = Math.max(0.3, +(p.rttMs * (0.9 + Math.random() * 0.2)).toFixed(1));
  emitPeers();
  if (bgN % 9 === 0) incomingChat(ids["dad-pc"], DAD_LINES[Math.floor(Math.random() * DAD_LINES.length)]);
  if (bgN % 13 === 0) incomingChat(ids["home-server"], SERVER_LINES[Math.floor(Math.random() * SERVER_LINES.length)]);
  if (bgN % 23 === 0) incomingMail("nas");
  if (bgN % 31 === 0) incomingOffer("phone");
  if (bgN % 4 === 0) addLog(["info", "debug", "info", "warn"][bgN % 4], ["keepalive: 5 peers ok", "relay: forwarded 64 packets for phone", "stun: mapping unchanged 203.0.113.5:41710", "peer phone: direct probe failed, staying on relay"][bgN % 4]);
}
setInterval(background, 5000);

function incomingChat(peerId, text) {
  if (!W.chat.has(peerId)) W.chat.set(peerId, []);
  const m = { id: rid("c_"), from: peerId, to: W.self.id, mine: false, text, ts: now(), state: "delivered", attachments: [], read: false };
  W.chat.get(peerId).push(m);
  broadcast("chat", { ...publicChat(m), peer: peerId });
  emitCounters();
  addLog("info", `chat: message from ${nameOf(peerId)}`);
}
function incomingMail(fromKey) {
  const p = peerByAny(fromKey) || W.peers[0];
  const m = { id: rid("m_"), kind: "mail", folder: "inbox", from: { id: p.id, name: p.name }, to: [{ id: W.self.id, name: W.self.name, state: "delivered", at: now() }],
    subject: "Новое письмо от " + p.name, body: "Это тестовое письмо от mock-сервера.\nВремя: " + new Date().toLocaleString("ru-RU"), ts: now(), unread: true, attachments: [], thread: rid("th_"), starred: false, inReplyTo: null };
  W.mail.unshift(m);
  broadcast("mail", { id: m.id, folder: "inbox", unread: true });
  emitCounters();
  broadcast("notify", { level: "info", title: "Новое письмо", text: `${p.name}: ${m.subject}`, link: "#/mail/inbox/" + m.id });
}
function incomingOffer(fromKey, wanted) {
  const p = peerByAny(fromKey) || W.peers[0];
  const names = ["IMG_20251003_190144.jpg", "Документы.zip", "Голосовое.wav", "План ремонта.pdf"];
  const name = wanted || names[Math.floor(Math.random() * names.length)];
  const kind = name.endsWith(".jpg") ? "photo" : name.endsWith(".pdf") ? "pdf" : name.endsWith(".wav") ? "wav" : "big";
  const t = { id: rid("t_"), dir: "in", peer: p.id, peerName: p.name, name, size: kind === "big" ? 18_221_331 : 0, done: 0, mime: mimeOf(name), state: "offered", speed: 0, error: "", created: now(), updated: now(), finished: null, gen: { kind, seed: 777, size: 18_221_331 } };
  if (kind !== "big") t.size = fileBytes("tr/" + t.id, t.gen, name).length;
  W.transfers.unshift(t);
  emitTransfer(t);
  emitCounters();
  broadcast("notify", { level: "info", title: "Входящий файл", text: `${p.name} хочет отправить «${name}»`, link: "#/files/send" });
  return t;
}

// ---------------------------------------------------------------- HTTP plumbing
class HttpError extends Error {
  constructor(status, code, message) { super(message); this.status = status; this.code = code; }
}
const E = {
  invalid: (m) => new HttpError(400, "invalid", m || "invalid request"),
  denied: (m) => new HttpError(403, "denied", m || "permission denied"),
  notfound: (m) => new HttpError(404, "notfound", m || "not found"),
  exists: (m) => new HttpError(409, "exists", m || "already exists"),
  offline: (m) => new HttpError(502, "offline", m || "device is not connected"),
  notconfigured: () => new HttpError(412, "notconfigured", "this device is not a member of a mesh"),
  toolarge: () => new HttpError(413, "toolarge", "too large"),
  busy: (m) => new HttpError(503, "busy", m || "busy"),
  unsupported: (m) => new HttpError(501, "unsupported", m || "not supported"),
};

function sendJSON(res, status, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(status, { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store", "Content-Length": Buffer.byteLength(body) });
  res.end(body);
}
const ok = (res, extra = {}) => sendJSON(res, 200, { ok: true, ...extra });

function readBody(req, limit = 2 * 1024 * 1024 * 1024) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on("data", (c) => {
      size += c.length;
      if (size > limit) { reject(E.toolarge()); req.destroy(); return; }
      chunks.push(c);
    });
    req.on("end", () => resolve(Buffer.concat(chunks)));
    req.on("error", reject);
  });
}
async function readJSON(req) {
  const b = await readBody(req, 4 * 1024 * 1024);
  if (!b.length) return {};
  try { return JSON.parse(b.toString("utf8")); } catch { throw E.invalid("body is not valid JSON"); }
}

/** Serve bytes (Buffer or pattern) with Range support. */
function sendBytes(req, res, { buf, size, seed, mime, name, dl }) {
  const total = buf ? buf.length : size;
  const headers = {
    "Content-Type": mime || "application/octet-stream",
    "Accept-Ranges": "bytes",
    "Cache-Control": "private, max-age=60",
    "X-Content-Type-Options": "nosniff",
  };
  // Never let user files render as active content in the UI origin.
  if (/html|xml|svg/.test(mime || "") && !/^image\/svg/.test(mime)) headers["Content-Security-Policy"] = "sandbox";
  if (/^image\/svg/.test(mime || "")) headers["Content-Security-Policy"] = "sandbox; default-src 'none'; style-src 'unsafe-inline'";
  if (dl || /html/.test(mime || "")) headers["Content-Disposition"] = `${dl ? "attachment" : "inline"}; filename*=UTF-8''${encodeURIComponent(name || "file")}`;
  const range = req.headers.range;
  let start = 0, end = total - 1, status = 200;
  if (range) {
    const m = /^bytes=(\d*)-(\d*)$/.exec(range);
    if (!m || (m[1] === "" && m[2] === "")) { res.writeHead(416, { "Content-Range": `bytes */${total}` }); res.end(); return; }
    if (m[1] === "") { start = Math.max(0, total - Number(m[2])); }
    else { start = Number(m[1]); if (m[2] !== "") end = Math.min(total - 1, Number(m[2])); }
    if (start >= total || start > end) { res.writeHead(416, { "Content-Range": `bytes */${total}` }); res.end(); return; }
    status = 206;
    headers["Content-Range"] = `bytes ${start}-${end}/${total}`;
  }
  headers["Content-Length"] = total === 0 ? 0 : end - start + 1;
  res.writeHead(status, headers);
  if (req.method === "HEAD" || total === 0) { res.end(); return; }
  if (buf) res.end(buf.subarray(start, end + 1));
  else patternStream(seed || 1, start, end).pipe(res);
}

// ---------------------------------------------------------------- tree access
function treeFor(devKey, shareId) {
  const t = W.trees[devKey];
  return t ? t[shareId] : null;
}
function splitPath(p) { return (p || "/").split("/").filter(Boolean); }
function walk(root, p) {
  let node = root;
  for (const seg of splitPath(p)) {
    if (!node || !node.dir) return null;
    node = node.children[seg];
  }
  return node || null;
}
function sharesOf(devKey) {
  if (devKey === "laptop") return W.shares;
  return (W.remote[devKey] && W.remote[devKey].shares) || [];
}
function shareVisibleToMe(share) { return share.allow.includes("*") || share.allow.includes(W.self.id); }

// Where each device keeps its keys and settings: a share may not contain that
// folder or lie inside it (files.CheckShareRoot on the node).
const DATA_DIRS = {
  laptop: "/home/andrey/.config/themesh", nas: "/var/lib/themesh", "home-server": "/var/lib/themesh",
  phone: "/data/user/0/app.themesh.mobile/files", "dad-pc": "C:\\Users\\Папа\\AppData\\Roaming\\themesh",
};
const PROTECTED_MSG = "this folder contains (or lies inside) the folder where themesh keeps its keys and settings. Choose a folder that does not contain it";
function protectedShare(devKey, p) {
  const dir = DATA_DIRS[devKey] || (devKey && devKey.startsWith("new-") ? "/var/lib/themesh" : null);
  if (!dir || !p) return false;
  const norm = (x) => String(x).replace(/\\/g, "/").replace(/\/+$/, "");
  const a = norm(p), b = norm(dir);
  const within = (child, parent) => parent === "" || child === parent || child.startsWith(parent + "/");
  return within(a, b) || within(b, a);
}
/** A share as GET /api/shares shows it: `blocked` only when true. */
function shareView(devKey, sh) {
  return protectedShare(devKey, sh.path) ? { ...sh, blocked: true } : sh;
}

/** Resolve :id of /api/peers/:id/... → { key, peer|null (null = self) } with offline checks. */
function resolvePeer(id) {
  if (id === "self" || id === W.self.id) return { key: "laptop", peer: null };
  const p = W.peers.find((x) => x.id === id);
  if (!p) throw E.notfound("unknown device");
  if (!p.online) throw E.offline(`${p.name} is not connected`);
  return { key: keyOf(p.id), peer: p };
}
function getShare(key, shareId, self) {
  const s = sharesOf(key).find((x) => x.id === shareId);
  if (!s || (!self && !shareVisibleToMe(s)) || protectedShare(key, s.path)) throw E.notfound("no such shared folder");
  if (s.exists === false) throw E.notfound("shared folder is missing on disk");
  let root = treeFor(key, shareId);
  if (!root) { root = dir({}); W.trees[key] = W.trees[key] || {}; W.trees[key][shareId] = root; }
  return { s, root };
}
function cleanPath(p) {
  const parts = splitPath(p || "/");
  if (parts.some((x) => x === ".." || x === ".")) throw E.invalid("bad path");
  return "/" + parts.join("/");
}

// ---------------------------------------------------------------- local fs (folder picker)
function localFsList(devKey, reqPath) {
  const L = W.localfs[devKey];
  if (!L) throw E.notfound("no filesystem");
  const p = reqPath || L.home;
  const isWin = L.sep === "\\";
  const norm = isWin ? p.replace(/\//g, "\\") : p;
  let key = norm === "/" ? "" : norm.replace(/[\\/]+$/, "");
  if (isWin && /^[A-Z]:$/i.test(key)) key += "\\";
  const entries = L.tree[key];
  if (entries === undefined) {
    // Unknown but plausible folder: report empty if its parent lists it.
    const parent = isWin ? key.slice(0, key.lastIndexOf("\\")) || key : key.slice(0, key.lastIndexOf("/"));
    const parentKey = isWin && /^[A-Z]:$/i.test(parent) ? parent + "\\" : parent;
    const name = key.slice(key.lastIndexOf(L.sep) + 1);
    if (!(L.tree[parentKey] || []).includes(name)) throw E.notfound("no such folder");
  }
  let parent = null;
  if (isWin) {
    if (!/^[A-Z]:\\$/i.test(key)) { const i = key.lastIndexOf("\\"); parent = key.slice(0, i); if (/^[A-Z]:$/i.test(parent)) parent += "\\"; }
  } else if (key !== "") {
    const i = key.lastIndexOf("/"); parent = i <= 0 ? "/" : key.slice(0, i);
  }
  return { path: key === "" ? "/" : key, parent, home: L.home, sep: L.sep, roots: L.roots, entries: (entries || []).map((name) => ({ name, isDir: true })) };
}
function localFolderExists(devKey, p) {
  try { localFsList(devKey, p); return true; } catch { return false; }
}

// ---------------------------------------------------------------- routes
const routes = [];
function route(method, pattern, handler) {
  const keys = [];
  const re = new RegExp("^" + pattern.replace(/:(\w+)/g, (_, k) => { keys.push(k); return "([^/]+)"; }) + "$");
  routes.push({ method, re, keys, handler });
}

function requireConfigured() { if (!W.configured) throw E.notconfigured(); }

/** PUT {"tun": {enabled?, manageHosts?}}: start/stop the (fake) interface; failures are reported in-band. */
function applyTun(tun, patch, devKey) {
  if (patch.manageHosts !== undefined) tun.manageHosts = !!patch.manageHosts;
  if (patch.enabled === undefined) return;
  tun.enabled = !!patch.enabled;
  if (!tun.enabled) {
    Object.assign(tun, { state: "off", error: "", txPackets: 0, rxPackets: 0, dropped: 0 });
    addLog("info", "tun: themesh0 removed", devKey);
  } else if (!tun.supported) {
    Object.assign(tun, { state: "error", error: "virtual interfaces are not supported on this platform" });
  } else if (tunFails) {
    Object.assign(tun, { state: "error", error: "permission denied — run as root (or grant CAP_NET_ADMIN)" });
    addLog("error", "tun: open /dev/net/tun: permission denied", devKey);
  } else {
    Object.assign(tun, { state: "running", error: "", name: "themesh0" });
    addLog("info", `tun: themesh0 up (100.64.0.0/10, fd7a:5f3c:9e21::/48)${tun.manageHosts ? ", /etc/hosts updated" : ""}`, devKey);
  }
}

function statePayload() {
  return {
    version: "0.1.0", configured: W.configured, self: W.self, peers: W.peers,
    transfers: W.transfers.slice(0, 60).map(publicTransfer), counters: W.configured ? counters() : { mail: 0, chat: 0, offers: 0 },
    invites: W.invites, settings: W.settings,
    ...(!W.configured && W.removed ? { removed: W.removed } : {}),
  };
}

route("GET", "/api/state", (req, res) => sendJSON(res, 200, statePayload()));

route("GET", "/api/events", (req, res) => {
  if (Date.now() < refuseSseUntil) { res.writeHead(503, { "Content-Type": "text/plain" }); res.end("busy"); return; }
  res.writeHead(200, { "Content-Type": "text/event-stream; charset=utf-8", "Cache-Control": "no-store", Connection: "keep-alive", "X-Accel-Buffering": "no" });
  res.write(`retry: 2000\n\n`);
  res.write(`event: hello\ndata: ${JSON.stringify({ serverTime: now() })}\n\n`);
  clients.add(res);
  req.on("close", () => clients.delete(res));
});

route("POST", "/api/mesh/create", async (req, res) => {
  const b = await readJSON(req);
  if (W.configured) throw E.invalid("already a member of a mesh");
  if (!b.meshName || !b.deviceName) throw E.invalid("meshName and deviceName are required");
  buildWorld("empty");
  W.self.name = sanitizeName(b.deviceName); W.self.owner = b.owner || ""; W.self.meshName = b.meshName;
  W.self.meshId = base32(crypto.randomBytes(8)).slice(0, 12);
  W.peers = [];
  addLog("info", `created mesh «${b.meshName}»`);
  broadcast("self", W.self);
  ok(res);
});

route("POST", "/api/mesh/join", async (req, res) => {
  const b = await readJSON(req);
  if (W.configured) throw E.invalid("already a member of a mesh");
  const code = String(b.invite || "").replace(/\s+/g, "").toUpperCase();
  if (!code.startsWith("MESH1-") || code.length < 40) throw E.invalid("not an invite code (it must start with MESH1-)");
  if (!b.deviceName) throw E.invalid("deviceName is required");
  // Simulated rendezvous: takes a few seconds. Codes containing "EXPIRED"/"TIMEOUT" fail.
  await new Promise((r) => setTimeout(r, code.includes("TIMEOUT") ? 25000 : 3500));
  if (code.includes("EXPIRED")) throw E.invalid("invite expired");
  if (code.includes("TIMEOUT")) throw new HttpError(503, "busy", "could not reach the inviting device (timeout after 25 s)");
  buildWorld("full");
  W.self.name = uniqueName(sanitizeName(b.deviceName), new Set(W.peers.map((x) => x.deviceName)));
  W.self.admin = false; // the owner came with the invitation; an `owner` in the request is ignored
  addLog("info", "joined mesh «Дом»");
  ok(res);
});

/** Forget the mesh like the node: shares, services, forwards and TUN go; mail and chat history stay. */
function forgetMesh() {
  const { mail, chat } = W;
  buildWorld("onboarding");
  W.mail = mail; W.chat = chat;
}
route("POST", "/api/mesh/leave", async (req, res) => {
  await readJSON(req);
  requireConfigured();
  forgetMesh();
  broadcast("self", W.self);
  broadcast("peers", []);
  ok(res);
});

route("POST", "/api/netcheck", async (req, res) => {
  await readJSON(req);
  requireConfigured();
  await new Promise((r) => setTimeout(r, 1600));
  W.self.nat = { ...W.self.nat, stun: W.settings.stunEnabled, difficulty: W.settings.stunEnabled ? "easy" : "unknown" };
  W.self.endpoints = W.self.endpoints.map((e) => ({ ...e }));
  addLog("info", "netcheck: stun ok, mapping stable, difficulty " + W.self.nat.difficulty);
  broadcast("self", W.self);
  sendJSON(res, 200, { self: W.self });
});

route("GET", "/api/diag/logs", (req, res, p, q) => {
  const limit = Math.min(1000, Number(q.get("limit")) || 200);
  sendJSON(res, 200, { lines: (W.devLogs.laptop || []).slice(-limit) });
});

route("GET", "/api/diag/ping", async (req, res, p, q) => {
  requireConfigured();
  const peer = W.peers.find((x) => x.id === q.get("peer"));
  if (!peer) throw E.notfound("unknown device");
  if (!peer.online) throw E.offline();
  await new Promise((r) => setTimeout(r, Math.min(900, peer.rttMs * 2 + 120)));
  sendJSON(res, 200, { ms: +(peer.rttMs * (0.85 + Math.random() * 0.3)).toFixed(1) });
});

// ---- sign-in (the browser only ever calls logout)
route("POST", "/api/login/code", async (req, res) => {
  await readJSON(req);
  if (!bearerOk(req)) throw E.denied("login links are issued to the command line only (themesh url / themesh open)");
  const code = newLoginCode();
  sendJSON(res, 200, { code, url: `http://${req.headers.host}/?t=${code}`, expiresIn: CODE_TTL, singleUse: true, sessionTtl: SESSION_TTL });
});
route("POST", "/api/logout", async (req, res, p, q) => {
  await readJSON(req);
  if (q.get("all") === "1") {
    if (!bearerOk(req)) throw E.denied("signing out everywhere is for the command line only");
    sessions.clear(); loginCodes.clear();
  } else {
    sessions.delete(parseCookies(req.headers.cookie).themesh_session || "");
  }
  res.setHeader("Set-Cookie", sessionCookie("", 0));
  ok(res);
});

// ---- invites & devices
route("GET", "/api/invites", (req, res) => sendJSON(res, 200, W.invites));
route("POST", "/api/invites", async (req, res) => {
  const b = await readJSON(req);
  requireConfigured();
  if (!W.self.admin) throw E.denied("only admins can invite");
  const ttl = Math.max(1, Math.min(7 * 24 * 60, Number(b.ttlMinutes) || 30));
  const inv = makeInvite(!!b.admin, ttl, cleanOwner(b.owner));
  inv.created = now();
  W.invites.push(inv);
  emitInvites();
  addLog("info", `invite created (${b.admin ? "admin" : "member"}, ${ttl} min)`);
  sendJSON(res, 200, inv);
  if (!opts.calm) setTimeout(() => { if (W.invites.find((x) => x.id === inv.id)) joinViaInvite(inv, "tablet"); }, 20000);
});
route("DELETE", "/api/invites/:id", (req, res, p) => {
  const i = W.invites.findIndex((x) => x.id === p.id);
  if (i < 0) throw E.notfound("no such invite");
  W.invites.splice(i, 1);
  emitInvites();
  ok(res);
});

function joinViaInvite(inv, name) {
  W.invites = W.invites.filter((x) => x.id !== inv.id);
  emitInvites();
  const n = uniqueName(sanitizeName(name), new Set([W.self.name, ...W.peers.map((p) => p.deviceName)]));
  const key = "new-" + n;
  ids[key] = devId(key + Date.now());
  const def = { key, name: n, owner: inv.owner || W.self.owner, os: "android", arch: "arm64", ip4: `100.64.0.${20 + W.peers.length}`, ip6: `fd7a:5f3c:9e21::${(20 + W.peers.length).toString(16)}`, online: true, path: "lan", rttMs: 2.4, uptime: 30, shares: 0, services: [], admin: inv.admin, addr: "192.168.1.44:41710", endpoints: ["192.168.1.44:41710"] };
  const p = makePeer(def);
  p.id = ids[key]; p.short = p.id.slice(0, 8); p.txBytes = 2048; p.rxBytes = 4096;
  W.peers.push(p);
  W.remote[key] = { self: remoteSelf(p, def), settings: defaultSettings(), shares: [], services: [] };
  W.localfs[key] = linuxTree("user");
  setTimeout(() => {
    emitPeers();
    broadcast("notify", { level: "success", title: "Новое устройство", text: `«${n}» подключилось к сети`, link: "#/devices/" + p.id });
    addLog("info", `peer ${n} joined via invite`);
  }, 400);
  return p;
}

route("POST", "/api/peers/:id/alias", async (req, res, p) => {
  const b = await readJSON(req);
  const peer = W.peers.find((x) => x.id === p.id);
  if (!peer) throw E.notfound("unknown device");
  peer.alias = String(b.alias || "").trim().slice(0, 64);
  peer.name = peer.alias || peer.deviceName;
  emitPeers();
  ok(res);
});
route("POST", "/api/peers/:id/revoke", async (req, res, p) => {
  await readJSON(req);
  if (!W.self.admin) throw E.denied("admins only");
  const i = W.peers.findIndex((x) => x.id === p.id);
  if (i < 0) throw E.notfound("unknown device");
  const [gone] = W.peers.splice(i, 1);
  emitPeers();
  addLog("warn", `device ${gone.deviceName} revoked`);
  ok(res);
});
route("POST", "/api/peers/:id/rename", async (req, res, p) => {
  const b = await readJSON(req);
  if (!W.self.admin) throw E.denied("admins only");
  const peer = W.peers.find((x) => x.id === p.id);
  if (!peer) throw E.notfound("unknown device");
  if (!String(b.name || "").trim()) throw E.invalid("name is required");
  const name = uniqueName(sanitizeName(b.name), new Set([W.self.name, ...W.peers.filter((x) => x.id !== peer.id).map((x) => x.deviceName)]));
  peer.deviceName = name;
  peer.name = peer.alias || name;
  emitPeers();
  ok(res);
});
route("POST", "/api/peers/:id/admin", async (req, res, p) => {
  const b = await readJSON(req);
  if (!W.self.admin) throw E.denied("admins only");
  const peer = W.peers.find((x) => x.id === p.id);
  if (!peer) throw E.notfound("unknown device");
  if (!b.admin) throw E.unsupported("administrator rights cannot be taken back once granted (the device already holds the mesh key); remove the device and add it again as a regular one");
  if (!peer.online) throw E.offline("the device must be online to receive the mesh key");
  peer.admin = true;
  emitPeers();
  ok(res);
});

// ---- files: browsing
route("GET", "/api/peers/:id/shares", (req, res, p) => {
  requireConfigured();
  const { key, peer } = resolvePeer(p.id);
  // a share that would expose the device's keys is not served at all
  const list = sharesOf(key).filter((s) => (!peer || shareVisibleToMe(s)) && !protectedShare(key, s.path));
  sendJSON(res, 200, list.map((s) => ({ id: s.id, name: s.name, mode: s.mode })));
});
route("GET", "/api/peers/:id/fs", (req, res, p, q) => {
  requireConfigured();
  const { key, peer } = resolvePeer(p.id);
  const { s, root } = getShare(key, q.get("share"), !peer);
  const pp = cleanPath(q.get("path"));
  const node = walk(root, pp);
  if (!node) throw E.notfound("no such folder");
  if (!node.dir) throw E.invalid("not a folder");
  const entries = Object.entries(node.children).map(([name, n]) => n.dir
    ? { name, isDir: true, size: 0, mtime: n.mtime, mime: "" }
    : { name, isDir: false, size: nodeSize(`${key}/${s.id}${pp === "/" ? "" : pp}/${name}`, n, name), mtime: n.mtime, mime: mimeOf(name) });
  // shuffle a bit: the contract says the order is not guaranteed
  entries.sort((a, b) => (a.name.length % 3) - (b.name.length % 3));
  sendJSON(res, 200, { path: pp, canWrite: s.mode === "rw", entries });
});
route("GET", "/api/peers/:id/file", (req, res, p, q) => {
  requireConfigured();
  const { key, peer } = resolvePeer(p.id);
  const { s, root } = getShare(key, q.get("share"), !peer);
  const pp = cleanPath(q.get("path"));
  const node = walk(root, pp);
  if (!node || node.dir) throw E.notfound("no such file");
  const name = pp.split("/").pop();
  const fk = `${key}/${s.id}${pp}`;
  const buf = fileBytes(fk, node, name);
  const mime = node.kind === "photo" && node.data === undefined ? "image/png" : mimeOf(name);
  sendBytes(req, res, { buf, size: node.size, seed: fk.length, mime, name, dl: q.get("dl") === "1" });
});
route("GET", "/api/peers/:id/thumb", (req, res, p, q) => {
  requireConfigured();
  const { key, peer } = resolvePeer(p.id);
  const { s, root } = getShare(key, q.get("share"), !peer);
  const pp = cleanPath(q.get("path"));
  const node = walk(root, pp);
  if (!node || node.dir) throw E.notfound("no such file");
  if (node.kind !== "photo") throw E.notfound("not an image");
  const w = Math.max(64, Math.min(512, Number(q.get("w")) || 256));
  const ck = `thumb/${key}/${s.id}${pp}/${w}`;
  let b = contentCache.get(ck);
  if (!b) {
    const ow = node.w || 960, oh = node.h || 640;
    b = genScene(node.seed ?? `${key}/${s.id}${pp}`, w, Math.round((w * oh) / ow));
    contentCache.set(ck, b);
  }
  // The real node returns JPEG; the mock returns PNG (no JPEG encoder here).
  sendBytes(req, res, { buf: b, mime: "image/png", name: "thumb.png" });
});
route("PUT", "/api/peers/:id/file", async (req, res, p, q) => {
  requireConfigured();
  const { key, peer } = resolvePeer(p.id);
  const { s, root } = getShare(key, q.get("share"), !peer);
  if (s.mode !== "rw") { req.resume(); throw E.denied("share is read-only"); }
  const pp = cleanPath(q.get("path"));
  const parts = splitPath(pp);
  const name = parts.pop();
  if (!name) throw E.invalid("missing file name");
  const parent = walk(root, "/" + parts.join("/"));
  if (!parent || !parent.dir) throw E.notfound("no such folder");
  if (parent.children[name] && q.get("overwrite") !== "1") { req.resume(); throw E.exists("file already exists"); }
  const body = await readBody(req);
  parent.children[name] = { file: true, kind: "upload", data: body, mtime: now() };
  contentCache.delete(`${key}/${s.id}${pp}`);
  addLog("info", `upload «${name}» (${body.length} bytes) to ${key}:${s.name}`);
  ok(res, { size: body.length });
});
route("POST", "/api/peers/:id/fs", async (req, res, p) => {
  const b = await readJSON(req);
  requireConfigured();
  const { key, peer } = resolvePeer(p.id);
  const { s, root } = getShare(key, b.share, !peer);
  if (s.mode !== "rw") throw E.denied("share is read-only");
  const pp = cleanPath(b.path);
  const parts = splitPath(pp);
  const name = parts.pop();
  if (!name) throw E.invalid("cannot change the share root");
  const parent = walk(root, "/" + parts.join("/"));
  if (!parent || !parent.dir) throw E.notfound("no such folder");
  if (b.op === "mkdir") {
    if (parent.children[name]) throw E.exists("already exists");
    parent.children[name] = dir({}, now());
  } else if (b.op === "rename") {
    if (!parent.children[name]) throw E.notfound("no such file");
    const to = String(b.to || "").trim();
    if (!to || to.includes("/") || to === "." || to === "..") throw E.invalid("invalid name");
    if (parent.children[to]) throw E.exists("a file with this name already exists");
    parent.children[to] = parent.children[name];
    delete parent.children[name];
  } else if (b.op === "delete") {
    if (!parent.children[name]) throw E.notfound("no such file");
    delete parent.children[name];
  } else throw E.invalid("unknown op");
  ok(res);
});

// ---- transfers
route("GET", "/api/transfers", (req, res) => sendJSON(res, 200, W.transfers.map(publicTransfer)));
route("POST", "/api/transfers", async (req, res, p, q) => {
  requireConfigured();
  const to = (q.get("to") || "").split(",").filter(Boolean);
  const name = q.get("name");
  if (!to.length || !name) { req.resume(); throw E.invalid("to and name are required"); }
  for (const id of to) if (!W.peers.find((x) => x.id === id)) { req.resume(); throw E.notfound("unknown device " + id.slice(0, 8)); }
  const body = await readBody(req);
  const out = to.map((id) => {
    const peer = W.peers.find((x) => x.id === id);
    const t = { id: rid("t_"), dir: "out", peer: id, peerName: peer.name, name, size: body.length, done: 0, mime: q.get("mime") || mimeOf(name), state: "queued", speed: 0, error: "", created: now(), updated: now(), finished: null, data: body };
    W.transfers.unshift(t);
    emitTransfer(t);
    return publicTransfer(t);
  });
  addLog("info", `transfer: «${name}» (${body.length} bytes) queued for ${to.map(nameOf).join(", ")}`);
  sendJSON(res, 200, { transfers: out });
});
function getTransfer(id) {
  const t = W.transfers.find((x) => x.id === id);
  if (!t) throw E.notfound("no such transfer");
  return t;
}
route("POST", "/api/transfers/:id/accept", async (req, res, p) => {
  await readJSON(req);
  const t = getTransfer(p.id);
  if (t.dir !== "in" || t.state !== "offered") throw E.invalid("nothing to accept");
  const peer = W.peers.find((x) => x.id === t.peer);
  t.state = peer && peer.online ? "active" : "queued";
  t.speed = t.state === "active" ? speedFor(t.peer) : 0;
  emitTransfer(t); emitCounters();
  sendJSON(res, 200, publicTransfer(t));
});
route("POST", "/api/transfers/:id/decline", async (req, res, p) => {
  await readJSON(req);
  const t = getTransfer(p.id);
  if (t.dir !== "in" || t.state !== "offered") throw E.invalid("nothing to decline");
  t.state = "declined"; t.finished = now();
  emitTransfer(t); emitCounters();
  sendJSON(res, 200, publicTransfer(t));
});
route("POST", "/api/transfers/:id/cancel", async (req, res, p) => {
  await readJSON(req);
  const t = getTransfer(p.id);
  if (!["queued", "offered", "active"].includes(t.state)) throw E.invalid("transfer is finished");
  t.state = "canceled"; t.speed = 0; t.finished = now();
  emitTransfer(t); emitCounters();
  sendJSON(res, 200, publicTransfer(t));
});
route("POST", "/api/transfers/:id/retry", async (req, res, p) => {
  await readJSON(req);
  const t = getTransfer(p.id);
  if (!["failed", "canceled"].includes(t.state)) throw E.invalid("only failed or canceled transfers can be retried");
  t.state = "queued"; t.error = ""; t.done = 0; t.finished = null;
  emitTransfer(t);
  sendJSON(res, 200, publicTransfer(t));
});
route("DELETE", "/api/transfers/:id", (req, res, p) => {
  const t = getTransfer(p.id);
  if (["queued", "offered", "active"].includes(t.state)) throw E.invalid("cancel the transfer first");
  W.transfers = W.transfers.filter((x) => x.id !== t.id);
  broadcast("transfer.removed", { id: t.id });
  ok(res);
});
route("GET", "/api/transfers/:id/file", (req, res, p, q) => {
  const t = getTransfer(p.id);
  if (t.dir !== "in" || t.state !== "done") throw E.notfound("file is not available");
  const buf = t.data || (t.gen && t.gen.kind !== "big" ? fileBytes("tr/" + t.id, t.gen, t.name) : null);
  sendBytes(req, res, { buf, size: t.size, seed: 5, mime: t.gen && t.gen.kind === "photo" ? "image/png" : t.mime, name: t.name, dl: q.get("dl") === "1" });
});

// ---- shares (local or managed)
function cfgTarget(devKey) {
  if (devKey === "laptop") return { shares: W.shares, services: W.services, settings: W.settings, self: W.self };
  return W.remote[devKey];
}
function sharesHandlers(prefix, getKey) {
  route("GET", prefix + "/shares", (req, res, p) => { const key = getKey(p); sendJSON(res, 200, cfgTarget(key).shares.map((sh) => shareView(key, sh))); });
  route("POST", prefix + "/shares", async (req, res, p) => {
    const key = getKey(p);
    const b = await readJSON(req);
    const c = cfgTarget(key);
    if (!b.name || !b.path) throw E.invalid("name and path are required");
    if (!localFolderExists(key, b.path)) throw E.invalid("this folder does not exist on the device");
    if (protectedShare(key, b.path)) throw E.invalid(PROTECTED_MSG);
    if (c.shares.some((s) => s.name === b.name)) throw E.exists("a share with this name already exists");
    const sh = { id: rid("sh_"), name: String(b.name), path: String(b.path), mode: b.mode === "rw" ? "rw" : "ro", allow: Array.isArray(b.allow) && b.allow.length ? b.allow : ["*"], exists: true };
    c.shares.push(sh);
    if (key === "laptop") broadcast("shares", W.shares.map((x) => shareView(key, x)));
    sendJSON(res, 200, shareView(key, sh));
  });
  route("PUT", prefix + "/shares/:sid", async (req, res, p) => {
    const key = getKey(p);
    const b = await readJSON(req);
    const c = cfgTarget(key);
    const sh = c.shares.find((s) => s.id === p.sid);
    if (!sh) throw E.notfound("no such share");
    if (b.path !== undefined && b.path !== sh.path && !localFolderExists(key, b.path)) throw E.invalid("this folder does not exist on the device");
    if (protectedShare(key, b.path !== undefined ? b.path : sh.path)) throw E.invalid(PROTECTED_MSG);
    if (b.name !== undefined) sh.name = String(b.name);
    if (b.path !== undefined) { sh.path = String(b.path); sh.exists = true; }
    if (b.mode !== undefined) sh.mode = b.mode === "rw" ? "rw" : "ro";
    if (b.allow !== undefined) sh.allow = Array.isArray(b.allow) && b.allow.length ? b.allow : ["*"];
    if (key === "laptop") broadcast("shares", W.shares.map((x) => shareView(key, x)));
    sendJSON(res, 200, shareView(key, sh));
  });
  route("DELETE", prefix + "/shares/:sid", (req, res, p) => {
    const key = getKey(p);
    const c = cfgTarget(key);
    const i = c.shares.findIndex((s) => s.id === p.sid);
    if (i < 0) throw E.notfound("no such share");
    c.shares.splice(i, 1);
    if (key === "laptop") broadcast("shares", W.shares.map((x) => shareView(key, x)));
    ok(res);
  });
  route("GET", prefix + "/services", (req, res, p) => sendJSON(res, 200, cfgTarget(getKey(p)).services));
  route("POST", prefix + "/services", async (req, res, p) => {
    const key = getKey(p);
    const b = await readJSON(req);
    const c = cfgTarget(key);
    if (!b.name || !/^[^\s:]+:\d{1,5}$/.test(b.addr || "")) throw E.invalid("name and addr (host:port) are required");
    if (c.services.some((s) => s.name === b.name)) throw E.exists("a service with this name already exists");
    const sv = { id: rid("sv_"), name: String(b.name), addr: String(b.addr), description: String(b.description || ""), allow: Array.isArray(b.allow) && b.allow.length ? b.allow : ["*"] };
    c.services.push(sv);
    sendJSON(res, 200, sv);
  });
  route("PUT", prefix + "/services/:sid", async (req, res, p) => {
    const key = getKey(p);
    const b = await readJSON(req);
    const sv = cfgTarget(key).services.find((s) => s.id === p.sid);
    if (!sv) throw E.notfound("no such service");
    if (b.addr !== undefined && !/^[^\s:]+:\d{1,5}$/.test(b.addr)) throw E.invalid("addr must be host:port");
    for (const k of ["name", "addr", "description"]) if (b[k] !== undefined) sv[k] = String(b[k]);
    if (b.allow !== undefined) sv.allow = Array.isArray(b.allow) && b.allow.length ? b.allow : ["*"];
    sendJSON(res, 200, sv);
  });
  route("DELETE", prefix + "/services/:sid", (req, res, p) => {
    const c = cfgTarget(getKey(p));
    const i = c.services.findIndex((s) => s.id === p.sid);
    if (i < 0) throw E.notfound("no such service");
    c.services.splice(i, 1);
    ok(res);
  });
  route("GET", prefix + "/settings", (req, res, p) => sendJSON(res, 200, cfgTarget(getKey(p)).settings));
  route("PUT", prefix + "/settings", async (req, res, p) => {
    const key = getKey(p);
    const b = await readJSON(req);
    const c = cfgTarget(key);
    const s = c.settings;
    const restartKeys = ["udpPort", "lan", "socks"];
    for (const k of Object.keys(b)) {
      if (!(k in s) || k === "restartRequired") continue;
      if (k === "tun") { applyTun(s.tun, b.tun || {}, key); continue; }
      if (k === "udpPort" && !(Number.isInteger(b[k]) && b[k] >= 0 && b[k] < 65536)) throw E.invalid("udpPort must be 0..65535");
      if (k === "autoAccept" && !["own", "all", "ask"].includes(b[k])) throw E.invalid("autoAccept must be own|all|ask");
      if (k === "stunServers" && (!Array.isArray(b[k]) || b[k].some((x) => !/^[^\s:]+:\d{1,5}$/.test(x)))) throw E.invalid("stunServers must be host:port");
      if (k === "portMap" && typeof b[k] !== "boolean") throw E.invalid("portMap must be true or false");
      if (restartKeys.includes(k) && JSON.stringify(s[k]) !== JSON.stringify(b[k])) s.restartRequired = true;
      s[k] = b[k];
    }
    if (key === "laptop") {
      W.self.relay = s.relay;
      if (b.stunEnabled !== undefined) { W.self.nat.stun = s.stunEnabled; broadcast("self", W.self); }
    }
    if (b.portMap !== undefined) {
      // the node restarts its network layer: off removes the mapping, on looks for the router again
      applyPortmap(key, c.self, s.portMap ? "searching" : null);
      if (s.portMap) {
        setTimeout(() => {
          const cur = cfgTarget(key);
          if (!cur || !cur.settings.portMap || !cur.self || !cur.self.nat) return;
          applyPortmap(key, cur.self, (W.pmTarget || {})[key] || PM_DEFAULT[key] || "mapped");
          if (key === "laptop") { addLog("info", "portmap: " + cur.self.portmap.state); broadcast("self", W.self); }
        }, 1500);
      }
      if (key === "laptop") broadcast("self", W.self);
    }
    sendJSON(res, 200, s);
  });
  route("GET", prefix + "/local/fs", (req, res, p, q) => sendJSON(res, 200, localFsList(getKey(p), q.get("path"))));
  route("GET", prefix + "/diag/logs", (req, res, p, q) => {
    const limit = Math.min(1000, Number(q.get("limit")) || 200);
    sendJSON(res, 200, { lines: (W.devLogs[getKey(p)] || []).slice(-limit) });
  });
}
sharesHandlers("/api", () => "laptop");
sharesHandlers("/api/d/:pid", (p) => {
  requireConfigured();
  if (!W.self.admin) throw E.denied("only admins can manage other devices");
  if (p.pid === "self" || p.pid === W.self.id) return "laptop";
  const peer = W.peers.find((x) => x.id === p.pid);
  if (!peer) throw E.notfound("unknown device");
  if (!peer.online) throw E.offline(`${peer.name} is not connected`);
  return keyOf(peer.id);
});
route("GET", "/api/d/:pid/state", (req, res, p) => {
  if (!W.self.admin) throw E.denied("only admins can manage other devices");
  const peer = W.peers.find((x) => x.id === p.pid);
  if (!peer) throw E.notfound("unknown device");
  if (!peer.online) throw E.offline();
  const r = W.remote[keyOf(peer.id)];
  sendJSON(res, 200, { version: peer.version, configured: true, self: r.self, peers: [], transfers: [], counters: { mail: 0, chat: 0, offers: 0 }, invites: [], settings: r.settings });
});

// ---- mail
route("GET", "/api/mail", (req, res, p, q) => {
  requireConfigured();
  const folder = q.get("folder") || "inbox";
  const query = (q.get("q") || "").toLowerCase().trim();
  const limit = Math.max(1, Math.min(200, Number(q.get("limit")) || 50));
  const before = Number(q.get("before")) || 0;
  let list = W.mail.filter((m) => m.folder === folder);
  if (query) list = list.filter((m) => `${m.subject}\n${m.body}\n${m.from.name}\n${m.to.map((x) => x.name).join(" ")}`.toLowerCase().includes(query));
  list.sort((a, b) => b.ts - a.ts);
  const total = list.length;
  if (before) list = list.filter((m) => m.ts < before);
  sendJSON(res, 200, { items: list.slice(0, limit).map(mailSummary), total, unread: W.mail.filter((m) => m.folder === folder && m.unread).length });
});
function getMail(id) {
  const m = W.mail.find((x) => x.id === id);
  if (!m) throw E.notfound("no such message");
  return m;
}
route("GET", "/api/mail/:id", (req, res, p) => sendJSON(res, 200, mailFull(getMail(p.id))));
route("POST", "/api/mail", async (req, res) => {
  const b = await readJSON(req);
  requireConfigured();
  if (!Array.isArray(b.to) || !b.to.length) throw E.invalid("at least one recipient is required");
  for (const id of b.to) if (!W.peers.find((x) => x.id === id)) throw E.notfound("unknown recipient");
  const atts = (b.attachments || []).map((h) => {
    const bl = W.blobs.get(h);
    if (!bl) throw E.invalid("unknown attachment " + String(h).slice(0, 8));
    return { name: bl.name, size: bl.size, mime: bl.mime, sha256: h, state: "ready", got: bl.size, node: { kind: "upload", data: bl.data } };
  });
  const m = {
    id: rid("m_"), kind: "mail", folder: "sent", from: { id: W.self.id, name: W.self.name },
    to: b.to.map((id) => ({ id, name: nameOf(id), state: "queued", at: 0, qAt: now() })),
    subject: String(b.subject || ""), body: String(b.body || ""), ts: now(), unread: false, attachments: atts,
    thread: b.inReplyTo ? (W.mail.find((x) => x.id === b.inReplyTo) || {}).thread || rid("th_") : rid("th_"), starred: false, inReplyTo: b.inReplyTo || null,
  };
  W.mail.unshift(m);
  broadcast("mail", { id: m.id, folder: "sent", unread: false });
  addLog("info", `mail ${m.id} queued for ${m.to.map((x) => x.name).join(", ")}`);
  sendJSON(res, 200, { id: m.id });
  // Auto-reply from dad-pc to make the inbox feel alive.
  if (!opts.calm && b.to.includes(ids["dad-pc"])) setTimeout(() => incomingChat(ids["dad-pc"], "Получил письмо 👍"), 6000);
});
route("POST", "/api/mail/:id/flags", async (req, res, p) => {
  const b = await readJSON(req);
  const m = getMail(p.id);
  if (b.unread !== undefined) m.unread = !!b.unread;
  if (b.starred !== undefined) m.starred = !!b.starred;
  if (b.folder !== undefined) {
    if (!["inbox", "sent", "trash"].includes(b.folder)) throw E.invalid("bad folder");
    m.prevFolder = m.folder; m.folder = b.folder;
  }
  broadcast("mail", { id: m.id, folder: m.folder, unread: m.unread });
  emitCounters();
  ok(res);
});
route("DELETE", "/api/mail/:id", (req, res, p) => {
  const m = getMail(p.id);
  if (m.folder === "trash") W.mail = W.mail.filter((x) => x.id !== m.id);
  else { m.prevFolder = m.folder; m.folder = "trash"; }
  broadcast("mail", { id: m.id, folder: "trash", unread: false });
  emitCounters();
  ok(res);
});
route("POST", "/api/blobs", async (req, res, p, q) => {
  const name = q.get("name") || "file";
  const body = await readBody(req, 512 * 1024 * 1024);
  const h = sha(body);
  W.blobs.set(h, { name, size: body.length, mime: q.get("mime") || mimeOf(name), data: body });
  sendJSON(res, 200, { id: h, name, size: body.length, mime: q.get("mime") || mimeOf(name) });
});
route("GET", "/api/mail/:id/attachments/:idx", (req, res, p, q) => {
  const a = getMail(p.id).attachments[Number(p.idx)];
  if (!a) throw E.notfound("no such attachment");
  sendAttachment(req, res, a, "att/", q);
});
route("POST", "/api/mail/:id/attachments/:idx/fetch", async (req, res, p) => {
  await readJSON(req);
  const m = getMail(p.id);
  const a = m.attachments[Number(p.idx)];
  if (!a) throw E.notfound("no such attachment");
  // no event now; a `mail` event when it ends
  fetchAttachment(a, () => broadcast("mail", { id: m.id, folder: m.folder, unread: m.unread }));
  ok(res);
});

// ---- chat
route("GET", "/api/chat/threads", (req, res) => { requireConfigured(); sendJSON(res, 200, threads()); });
function findChat(id) {
  for (const [peer, list] of W.chat.entries()) {
    const m = list.find((x) => x.id === id);
    if (m) return { m, peer };
  }
  throw E.notfound("no such message");
}
route("GET", "/api/chat/messages/:id/attachments/:idx", (req, res, p, q) => {
  const a = findChat(p.id).m.attachments[Number(p.idx)];
  if (!a) throw E.notfound("no such attachment");
  sendAttachment(req, res, a, "chat/", q);
});
route("POST", "/api/chat/messages/:id/attachments/:idx/fetch", async (req, res, p) => {
  await readJSON(req);
  const { m, peer } = findChat(p.id);
  const a = m.attachments[Number(p.idx)];
  if (!a) throw E.notfound("no such attachment");
  fetchAttachment(a, () => broadcast("chat", { ...publicChat(m), peer }));
  ok(res);
});
route("GET", "/api/chat/:peer", (req, res, p, q) => {
  requireConfigured();
  if (!W.peers.find((x) => x.id === p.peer)) throw E.notfound("unknown device");
  const limit = Math.max(1, Math.min(200, Number(q.get("limit")) || 50));
  const before = Number(q.get("before")) || 0;
  let list = (W.chat.get(p.peer) || []).slice().sort((a, b) => a.ts - b.ts);
  if (before) list = list.filter((m) => m.ts < before);
  sendJSON(res, 200, { messages: list.slice(-limit).map(publicChat) });
});
route("POST", "/api/chat/:peer", async (req, res, p) => {
  const b = await readJSON(req);
  requireConfigured();
  const peer = W.peers.find((x) => x.id === p.peer);
  if (!peer) throw E.notfound("unknown device");
  if (!String(b.text || "").trim() && !(b.attachments || []).length) throw E.invalid("empty message");
  const atts = (b.attachments || []).map((h) => {
    const bl = W.blobs.get(h);
    if (!bl) throw E.invalid("unknown attachment");
    return { name: bl.name, size: bl.size, mime: bl.mime, sha256: h, state: "ready", node: { kind: "upload", data: bl.data } };
  });
  const m = { id: rid("c_"), from: W.self.id, to: peer.id, mine: true, text: String(b.text || ""), ts: now(), state: "queued", attachments: atts, read: true, stAt: Date.now() };
  if (!W.chat.has(peer.id)) W.chat.set(peer.id, []);
  W.chat.get(peer.id).push(m);
  sendJSON(res, 200, publicChat(m));
  broadcast("chat", { ...publicChat(m), peer: peer.id });
  if (!opts.calm && peer.online && peer.id === ids["dad-pc"]) setTimeout(() => incomingChat(peer.id, ["👍", "Хорошо", "Понял, спасибо!", "Ага"][Math.floor(Math.random() * 4)]), 4500);
});
route("POST", "/api/chat/:peer/read", async (req, res, p) => {
  await readJSON(req);
  for (const m of W.chat.get(p.peer) || []) if (!m.mine) m.read = true;
  emitCounters();
  ok(res);
});

// ---- services & forwards
route("GET", "/api/peers/:id/services", (req, res, p) => {
  requireConfigured();
  const { peer } = resolvePeer(p.id);
  if (!peer) return sendJSON(res, 200, W.services.map((s) => ({ name: s.name, port: Number(s.addr.split(":").pop()), description: s.description })));
  sendJSON(res, 200, peer.services);
});
route("GET", "/api/forwards", (req, res) => sendJSON(res, 200, W.forwards));
route("POST", "/api/forwards", async (req, res) => {
  const b = await readJSON(req);
  requireConfigured();
  const peer = W.peers.find((x) => x.id === b.peer);
  if (!peer) throw E.notfound("unknown device");
  const svc = (peer.services || []).find((s) => s.name === b.service);
  if (!svc) throw E.notfound("no such service on " + peer.name);
  if (W.forwards.some((f) => f.peer === peer.id && f.service === svc.name)) throw E.exists("already forwarded");
  let listen = String(b.listen || "127.0.0.1:0");
  let [host, port] = [listen.slice(0, listen.lastIndexOf(":")), Number(listen.slice(listen.lastIndexOf(":") + 1))];
  if (!port) {
    const preferred = svc.port === 22 ? 2222 : svc.port < 1024 ? 8000 + svc.port : svc.port;
    port = preferred;
    while (W.forwards.some((f) => f.listen.endsWith(":" + port))) port++;
  } else if (W.forwards.some((f) => f.listen === `${host}:${port}`)) throw E.exists("port is already in use");
  const fw = { id: rid("fw_"), peer: peer.id, peerName: peer.name, service: svc.name, listen: `${host || "127.0.0.1"}:${port}`, state: peer.online ? "listening" : "error", error: peer.online ? "" : "device is offline", conns: 0 };
  W.forwards.push(fw);
  broadcast("forwards", W.forwards);
  addLog("info", `forward ${fw.listen} → ${peer.name}:${svc.name}`);
  sendJSON(res, 200, fw);
});
route("DELETE", "/api/forwards/:id", (req, res, p) => {
  const i = W.forwards.findIndex((f) => f.id === p.id);
  if (i < 0) throw E.notfound("no such forward");
  W.forwards.splice(i, 1);
  broadcast("forwards", W.forwards);
  ok(res);
});

// ---- mock hooks
route("ANY", "/__mock/offer", (req, res, p, q) => { const t = incomingOffer(q.get("from") || "phone", q.get("name") || ""); sendJSON(res, 200, publicTransfer(t)); });
route("ANY", "/__mock/chat", (req, res, p, q) => {
  const peer = peerByAny(q.get("from") || "dad-pc");
  incomingChat(peer.id, q.get("text") || "Привет! Это тестовое сообщение.");
  ok(res);
});
route("ANY", "/__mock/mail", (req, res, p, q) => { incomingMail(q.get("from") || "nas"); ok(res); });
route("ANY", "/__mock/join", (req, res, p, q) => {
  const inv = W.invites[W.invites.length - 1];
  if (!inv) throw E.notfound("no pending invite");
  const peer = joinViaInvite(inv, q.get("name") || "tablet");
  sendJSON(res, 200, { id: peer.id, name: peer.name });
});
route("ANY", "/__mock/drop", (req, res, p, q) => {
  refuseSseUntil = Date.now() + (Number(q.get("for")) || 0) * 1000;
  for (const c of clients) c.destroy();
  clients.clear();
  ok(res);
});
route("ANY", "/__mock/peer", (req, res, p, q) => {
  const peer = peerByAny(q.get("name"));
  if (!peer) throw E.notfound("unknown device");
  const on = q.get("online") !== "0";
  peer.online = on;
  peer.path = on ? (keyOf(peer.id) === "phone" ? "relay" : "direct") : "none";
  peer.relayVia = peer.path === "relay" ? "home-server" : "";
  peer.rttMs = on ? peer.rttMs || 20 : 0;
  peer.lastSeen = now();
  emitPeers();
  ok(res);
});
route("ANY", "/__mock/auth", (req, res, p, q) => { authRequired = q.get("on") !== "0"; ok(res, { authRequired }); });
route("ANY", "/__mock/sw", (req, res, p, q) => { if (q.get("bump")) swSalt = String(Date.now()); ok(res, { version: "themesh-ui-" + uiHash() }); });
route("ANY", "/__mock/portmap", (req, res, p, q) => {
  requireConfigured();
  const st = q.get("state") || "mapped";
  if (!PM_STATES.includes(st)) throw E.invalid("state must be " + PM_STATES.join("|"));
  W.settings.portMap = true; // the UI sees the switch itself on its next settings read
  W.pmTarget.laptop = st;
  applyPortmap("laptop", W.self, st);
  broadcast("self", W.self);
  ok(res, { portmap: W.self.portmap });
});
route("ANY", "/__mock/login", (req, res) => { const code = newLoginCode(); sendJSON(res, 200, { code, url: "/?t=" + code }); });
route("ANY", "/__mock/tun", (req, res, p, q) => { tunFails = q.get("error") !== "0"; ok(res, { tunFails }); });
// An admin elsewhere removed this device: it forgets the mesh, takes a fresh
// identity, and tells the UI (warn notify with link "#/", then empty peers).
route("ANY", "/__mock/removed", (req, res) => {
  if (!W.configured) throw E.notconfigured();
  const meshName = W.self.meshName || "Дом";
  forgetMesh();
  const fresh = devId("laptop-" + Date.now());
  W.self.id = fresh; W.self.short = fresh.slice(0, 8);
  W.removed = { meshName, at: now() };
  addLog("warn", `removed from mesh «${meshName}» by an administrator; new device key ${fresh.slice(0, 8)}`);
  broadcast("notify", { level: "warn", title: meshName, text: "removed", link: "#/" });
  broadcast("peers", []);
  ok(res, { removed: W.removed });
});
route("ANY", "/__mock/reset", (req, res) => { buildWorld(opts.scenario); broadcast("self", W.self); emitPeers(); ok(res); });

// ---------------------------------------------------------------- static files
const STATIC_MIME = {
  ".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8", ".mjs": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8", ".svg": "image/svg+xml", ".png": "image/png", ".ico": "image/x-icon",
  ".json": "application/json", ".webmanifest": "application/manifest+json", ".txt": "text/plain; charset=utf-8", ".woff2": "font/woff2",
};
// The CSP we recommend for the real node; the UI must work under it.
const CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; media-src 'self' blob:; connect-src 'self'; frame-src 'self' blob:; worker-src 'self'; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'";

// Like the node: sw.js gets `const VERSION = "themesh-ui-<hash of the UI files>"`, so
// any change to the UI (or /__mock/sw?bump=1) makes browsers install a new worker.
let swSalt = "";
function uiHash() {
  const h = crypto.createHash("sha256").update(swSalt);
  const walk = (dir) => {
    for (const e of fs.readdirSync(dir, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name))) {
      const f = path.join(dir, e.name);
      if (e.isDirectory()) walk(f);
      else h.update(path.relative(UI_DIR, f)).update(fs.readFileSync(f));
    }
  };
  walk(UI_DIR);
  return h.digest("hex").slice(0, 16);
}

function serveStatic(req, res, urlPath) {
  let rel = decodeURIComponent(urlPath).replace(/^\/+/, "");
  if (rel === "") rel = "index.html";
  let file = path.resolve(UI_DIR, rel);
  if (!file.startsWith(UI_DIR + path.sep) && file !== UI_DIR) { res.writeHead(403); res.end(); return; }
  let st = null;
  try { st = fs.statSync(file); } catch { st = null; }
  if (!st || st.isDirectory()) {
    if (path.extname(rel)) { res.writeHead(404, { "Content-Type": "text/plain" }); res.end("not found"); return; }
    file = path.join(UI_DIR, "index.html"); // SPA fallback
  }
  const ext = path.extname(file);
  const headers = {
    "Content-Type": STATIC_MIME[ext] || "application/octet-stream",
    "Cache-Control": "no-cache",
    "X-Content-Type-Options": "nosniff",
    "Referrer-Policy": "no-referrer",
  };
  if (ext === ".html") { headers["Content-Security-Policy"] = CSP; headers["X-Frame-Options"] = "DENY"; }
  if (path.relative(UI_DIR, file) === "sw.js") {
    const body = fs.readFileSync(file, "utf8").replace('const VERSION = "themesh-ui-v1";', `const VERSION = "themesh-ui-${uiHash()}";`);
    res.writeHead(200, headers);
    res.end(req.method === "HEAD" ? undefined : body);
    return;
  }
  res.writeHead(200, headers);
  if (req.method === "HEAD") { res.end(); return; }
  fs.createReadStream(file).pipe(res);
}

// ---------------------------------------------------------------- server
function newLoginCode() {
  const code = crypto.randomBytes(24).toString("hex");
  loginCodes.set(code, now() + CODE_TTL);
  return code;
}
function redeemCode(code) {
  const exp = loginCodes.get(code);
  loginCodes.delete(code); // works once
  if (!exp || now() >= exp) return null;
  const id = crypto.randomBytes(32).toString("hex");
  sessions.set(id, now() + SESSION_TTL);
  return id;
}
function sessionOk(id) {
  const exp = sessions.get(id);
  if (!exp) return false;
  if (now() >= exp) { sessions.delete(id); return false; }
  if (exp - now() < SESSION_TTL / 2) sessions.set(id, now() + SESSION_TTL); // sliding
  return true;
}
const bearerOk = (req) => req.headers.authorization === "Bearer " + MASTER;
const authorized = (req) => bearerOk(req) || sessionOk(parseCookies(req.headers.cookie).themesh_session || "");
const sessionCookie = (id, maxAge) => `themesh_session=${id}; HttpOnly; SameSite=Strict; Path=/; Max-Age=${maxAge}`;
function handshake(res, n) {
  if (n.length < 16 || n.length > 128) return sendJSON(res, 400, { error: { code: "invalid", message: "n must be 16 to 128 characters" } });
  sendJSON(res, 200, { proof: crypto.createHmac("sha256", MASTER).update("themesh-handshake/v1\0" + n).digest("hex") });
}

function parseCookies(h) {
  const o = {};
  for (const part of (h || "").split(";")) { const i = part.indexOf("="); if (i > 0) o[part.slice(0, i).trim()] = part.slice(i + 1).trim(); }
  return o;
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, "http://localhost");
  const pth = url.pathname;
  try {
    // /?t=<one-time code> becomes a session cookie and leaves the URL; a used or
    // expired code is ignored (the page then meets 401s), as the node does.
    if (url.searchParams.get("t") && !pth.startsWith("/api/")) {
      const id = redeemCode(url.searchParams.get("t"));
      if (id) {
        const q = new URLSearchParams(url.searchParams);
        q.delete("t");
        const qs = q.toString();
        res.writeHead(302, { "Set-Cookie": sessionCookie(id, SESSION_TTL), Location: "/" + pth.replace(/^\/+/, "") + (qs ? "?" + qs : "") });
        res.end();
        return;
      }
    }
    const isApi = pth.startsWith("/api/");
    const isMock = pth.startsWith("/__mock/");
    if (!isApi && !isMock) { serveStatic(req, res, pth); return; }
    if (pth === "/api/handshake" && ["GET", "HEAD"].includes(req.method)) { handshake(res, url.searchParams.get("n") || ""); return; }
    if (isApi && authRequired && !authorized(req)) {
      throw new HttpError(401, "unauthorized", "open the link printed by `themesh up` (or run `themesh open`) to sign in");
    }
    if (isApi && !["GET", "HEAD"].includes(req.method) && !req.headers.authorization && req.headers["x-themesh"] !== "1") {
      throw E.denied("missing X-Themesh header");
    }
    const r = routes.find((x) => (x.method === req.method || x.method === "ANY" || (req.method === "HEAD" && x.method === "GET")) && x.re.test(pth));
    if (!r) throw E.notfound("no such endpoint: " + req.method + " " + pth);
    const m = r.re.exec(pth);
    const params = {};
    r.keys.forEach((k, i) => { params[k] = decodeURIComponent(m[i + 1]); });
    if (isApi && opts.latency && pth !== "/api/events") await new Promise((rs) => setTimeout(rs, opts.latency));
    await r.handler(req, res, params, url.searchParams);
  } catch (e) {
    const status = e instanceof HttpError ? e.status : 500;
    const code = e instanceof HttpError ? e.code : "internal";
    if (!(e instanceof HttpError)) console.error(e);
    if (!res.headersSent) sendJSON(res, status, { error: { code, message: e.message || "internal error" } });
    else res.end();
  }
});

/** Generate the fake media in the background so first folder listings are instant. */
function warmUp() {
  const jobs = [];
  const walkTree = (key, node, name) => {
    if (node.dir) { for (const [n, c] of Object.entries(node.children)) walkTree(`${key}/${n}`, c, n); return; }
    if (node.kind && node.kind !== "big" && !node.data) jobs.push([key, node, name]);
  };
  for (const [dev, shares] of Object.entries(W.trees || {})) {
    for (const [sid, root] of Object.entries(shares)) walkTree(`${dev}/${sid}`, root, "");
  }
  const next = () => {
    const j = jobs.shift();
    if (!j) return;
    try { fileBytes(j[0], j[1], j[2]); } catch { /* ignore */ }
    setTimeout(next, 5);
  };
  setTimeout(next, 50);
}

buildWorld(opts.scenario);
server.listen(opts.port, opts.host, () => {
  warmUp();
  const addr = server.address();
  console.log(`themesh mock (${opts.scenario}${opts.calm ? ", calm" : ""}) → http://${opts.host}:${addr.port}/${authRequired ? "?t=" + newLoginCode() + "  (one-time sign-in link; more from POST /__mock/login)" : ""}`);
});
