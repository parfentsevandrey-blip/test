// Small shared helpers: class names, device/file heuristics, linkify, clipboard.
import { html } from "../vendor/preact-htm.js";

export function cx(...args) {
  const out = [];
  for (const a of args) {
    if (!a) continue;
    if (typeof a === "string") out.push(a);
    else if (typeof a === "object") for (const [k, v] of Object.entries(a)) if (v) out.push(k);
  }
  return out.join(" ");
}

/** Guess what kind of device this is: laptop | desktop | phone | tablet | server | nas. */
export function deviceKind(dev) {
  if (!dev) return "laptop";
  const name = `${dev.deviceName || ""} ${dev.name || ""}`.toLowerCase();
  const os = (dev.os || "").toLowerCase();
  if (/\b(nas|synology|qnap|truenas|unraid|storage|хранилищ|диск)/.test(name)) return "nas";
  if (/(ipad|tablet|планшет|tab\b)/.test(name)) return "tablet";
  if (os === "android" || os === "ios" || /(phone|iphone|pixel|galaxy|телефон|смартфон|mobile)/.test(name)) return "phone";
  if (/(server|сервер|srv|homelab|raspberry|\bpi\b|-pi\b|pi-|router|vps|nuc)/.test(name)) return "server";
  if (/(laptop|notebook|ноут|macbook|thinkpad|book)/.test(name)) return "laptop";
  if (/(desktop|\bpc\b|-pc\b|pc-|imac|mac-?mini|workstation|компьютер|комп)/.test(name)) return "desktop";
  if (os === "freebsd" || os === "openbsd" || os === "netbsd") return "server";
  if (os === "linux" && /arm/.test(dev.arch || "")) return "server";
  if (os === "windows") return "desktop";
  return "laptop";
}

export const kindIcon = {
  laptop: "laptop", desktop: "monitor", phone: "phone", tablet: "tablet", server: "server", nas: "nas",
};

const EXT = {
  image: ["jpg", "jpeg", "png", "gif", "webp", "avif", "bmp", "svg", "heic", "heif", "ico", "tif", "tiff"],
  video: ["mp4", "m4v", "webm", "mov", "mkv", "avi", "ogv", "3gp", "ts"],
  audio: ["mp3", "wav", "ogg", "oga", "flac", "m4a", "aac", "opus", "weba"],
  pdf: ["pdf"],
  text: ["txt", "md", "markdown", "log", "csv", "tsv", "rtf", "nfo", "ini", "conf", "cfg", "env"],
  code: ["json", "js", "mjs", "ts", "go", "py", "rs", "c", "h", "cpp", "java", "kt", "sh", "yml", "yaml", "toml", "xml", "html", "htm", "css", "sql", "rb", "php", "swift", "lua"],
  archive: ["zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "zst", "iso", "dmg"],
};

export function extOf(name = "") {
  const i = name.lastIndexOf(".");
  return i > 0 ? name.slice(i + 1).toLowerCase() : "";
}

/** File category used for icons, colours and preview decisions. */
export function fileKind(name, mime = "", isDir = false) {
  if (isDir) return "folder";
  const e = extOf(name);
  for (const [k, list] of Object.entries(EXT)) if (list.includes(e)) return k;
  const m = (mime || "").toLowerCase();
  if (m.startsWith("image/")) return "image";
  if (m.startsWith("video/")) return "video";
  if (m.startsWith("audio/")) return "audio";
  if (m === "application/pdf") return "pdf";
  if (m === "application/json" || m.includes("xml") || m.includes("javascript")) return "code";
  if (m.startsWith("text/")) return "text";
  if (m.includes("zip") || m.includes("compressed") || m.includes("tar")) return "archive";
  return "other";
}

/** Which inline preview to use, or null. */
export function previewKind(name, mime) {
  const k = fileKind(name, mime);
  const e = extOf(name);
  if (k === "image") return ["heic", "heif", "tif", "tiff"].includes(e) ? null : "image";
  if (k === "video") return "video"; // unsupported codecs fall back to a download hint
  if (k === "audio") return "audio";
  if (k === "pdf") return "pdf";
  if (k === "text" || k === "code") return "text";
  return null;
}

export function guessMime(name) {
  const e = extOf(name);
  const map = {
    jpg: "image/jpeg", jpeg: "image/jpeg", png: "image/png", gif: "image/gif", webp: "image/webp", svg: "image/svg+xml",
    mp4: "video/mp4", webm: "video/webm", mov: "video/quicktime", mp3: "audio/mpeg", wav: "audio/wav", ogg: "audio/ogg",
    pdf: "application/pdf", txt: "text/plain", md: "text/markdown", json: "application/json", zip: "application/zip",
  };
  return map[e] || "application/octet-stream";
}

const URL_RE = /\bhttps?:\/\/[^\s<>"'«»]+/gi;

/**
 * Split plain text into strings and safe <a> elements for http(s) URLs.
 * Text stays text (Preact escapes it); only the href is taken from the match and
 * it is validated with the URL parser.
 */
export function linkify(text) {
  if (!text) return [];
  const out = [];
  let last = 0;
  for (const m of text.matchAll(URL_RE)) {
    let url = m[0];
    // Do not swallow trailing punctuation that usually ends a sentence.
    const trail = url.match(/[).,;:!?\]}]+$/);
    if (trail) {
      let cut = trail[0];
      // keep a closing paren when the URL itself contains an opening one
      if (cut.startsWith(")") && url.includes("(")) cut = cut.slice(1);
      url = url.slice(0, url.length - cut.length);
    }
    const idx = m.index;
    let ok = false;
    try {
      const u = new URL(url);
      ok = u.protocol === "http:" || u.protocol === "https:";
    } catch { ok = false; }
    if (!ok) continue;
    if (idx > last) out.push(text.slice(last, idx));
    out.push(html`<a href=${url} target="_blank" rel="noopener noreferrer">${url}</a>`);
    last = idx + url.length;
  }
  if (last < text.length) out.push(text.slice(last));
  return out;
}

export async function copyText(text) {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch { /* fall back */ }
  const ta = document.createElement("textarea");
  ta.value = text;
  ta.setAttribute("readonly", "");
  ta.style.position = "fixed";
  ta.style.opacity = "0";
  document.body.appendChild(ta);
  ta.select();
  let ok = false;
  try { ok = document.execCommand("copy"); } catch { ok = false; }
  ta.remove();
  return ok;
}

export function sortPeers(peers) {
  return peers.slice().sort((a, b) => {
    if (a.online !== b.online) return a.online ? -1 : 1;
    return (a.name || "").localeCompare(b.name || "", undefined, { sensitivity: "base", numeric: true });
  });
}

export function debounce(fn, ms) {
  let tm;
  return (...a) => { clearTimeout(tm); tm = setTimeout(() => fn(...a), ms); };
}

export function joinPath(dir, name) {
  if (!dir || dir === "/") return "/" + name;
  return dir.replace(/\/+$/, "") + "/" + name;
}

export function parentPath(p) {
  if (!p || p === "/") return "/";
  const s = p.replace(/\/+$/, "");
  const i = s.lastIndexOf("/");
  return i <= 0 ? "/" : s.slice(0, i);
}

export function baseName(p) {
  const s = (p || "").replace(/\/+$/, "");
  return s.slice(s.lastIndexOf("/") + 1);
}

/** Lightweight unique id for client-side keys. */
let uidN = 0;
export function uid(prefix = "u") {
  uidN++;
  return `${prefix}${Date.now().toString(36)}${uidN.toString(36)}`;
}

/** Ports whose services are usually opened in a browser. */
export const WEB_PORTS = new Set([80, 443, 3000, 3001, 5000, 5001, 5173, 8000, 8008, 8080, 8081, 8088, 8096, 8123, 8443, 8888, 8989, 9000, 9090, 9091, 32400, 7878, 8384, 8920, 2283, 3001, 9443]);
export const HTTPS_PORTS = new Set([443, 5001, 8443, 8920, 9443]);

/** Icon name for a service by its port / name. */
export function serviceIcon(svc) {
  const n = (svc.name || "").toLowerCase();
  const p = svc.port || 0;
  if (p === 22 || /ssh/.test(n)) return "terminal";
  if (p === 3389 || p === 5900 || /rdp|vnc|desktop/.test(n)) return "monitor";
  if ([8096, 32400, 8920].includes(p) || /jellyfin|plex|emby|media|кино/.test(n)) return "film";
  if (p === 445 || p === 139 || /smb|samba|nfs/.test(n)) return "folder";
  if ([5432, 3306, 6379, 27017].includes(p) || /sql|redis|mongo|db/.test(n)) return "database";
  if (p === 8123 || /home ?assistant|hass/.test(n)) return "home";
  if (WEB_PORTS.has(p)) return "globe";
  return "plug";
}

/** Split "127.0.0.1:2222" → { host, port }. Handles [v6]:port. */
export function splitHostPort(addr = "") {
  const m = addr.match(/^\[(.*)\]:(\d+)$/) || addr.match(/^(.*):(\d+)$/);
  if (!m) return { host: addr, port: 0 };
  return { host: m[1], port: Number(m[2]) };
}

export function isValidHostPort(addr) {
  const { host, port } = splitHostPort((addr || "").trim());
  return !!host && port > 0 && port < 65536;
}

export function pick(obj, keys) {
  const o = {};
  for (const k of keys) if (obj[k] !== undefined) o[k] = obj[k];
  return o;
}

/** Tone used for NAT difficulty chips. */
export function natTone(d) {
  return d === "open" || d === "easy" ? "ok" : d === "hard" ? "warn" : "muted";
}

// ---------- device names ----------
/** What device-name forms accept, checked after whitespace → "-" (see normalizeDeviceName). */
export const DEVICE_NAME_RE = /^[\p{L}\p{N}][\p{L}\p{N}._-]{0,62}$/u;

export function normalizeDeviceName(v) {
  return String(v || "").trim().replace(/\s+/g, "-");
}

// Cyrillic → Latin, the same table the node uses (docs/UI-API.md → "Device names").
const TRANSLIT = new Map(Object.entries({
  а: "a", б: "b", в: "v", г: "g", д: "d", е: "e", ё: "yo", ж: "zh", з: "z", и: "i", й: "y",
  к: "k", л: "l", м: "m", н: "n", о: "o", п: "p", р: "r", с: "s", т: "t", у: "u", ф: "f",
  х: "kh", ц: "ts", ч: "ch", ш: "sh", щ: "shch", ъ: "", ы: "y", ь: "", э: "e", ю: "yu", я: "ya",
  і: "i", ї: "yi", є: "ye", ґ: "g", ў: "u",
}));

/**
 * The DNS label the node makes of a typed device name: lowercase, NFC,
 * transliterate, strip accents, anything else → "-" (collapsed, trimmed),
 * at most 32 chars, "device" if nothing is left. «Кухонный ноутбук» →
 * "kukhonnyy-noutbuk" (reachable as kukhonnyy-noutbuk.svoi). A preview only:
 * the node's answer wins (it also adds -2, -3… on collisions). Mirrors
 * SanitizeName in internal/identity/authority.go.
 */
export function dnsLabel(input) {
  let out = "";
  for (const ch of String(input || "").toLowerCase().normalize("NFC")) {
    const tr = TRANSLIT.get(ch);
    if (tr !== undefined) { out += tr; continue; }
    const base = ch.normalize("NFD").replace(/\p{Mn}+/gu, "");
    if (!base) continue; // a lone accent
    out += /^[a-z0-9]+$/.test(base) ? base : "-";
  }
  out = out.replace(/-{2,}/g, "-").replace(/^-+|-+$/g, "");
  if (out.length > 32) out = out.slice(0, 32).replace(/-+$/, "");
  return out || "device";
}

/** Like UniqueName on the node: append -2, -3… (within 32 chars) while `taken` has the label. */
export function uniqueLabel(label, taken) {
  const has = (x) => (taken instanceof Set ? taken.has(x) : (taken || []).includes(x));
  if (!has(label)) return label;
  for (let i = 2; ; i++) {
    const suffix = `-${i}`;
    const cand = (label.length + suffix.length > 32 ? label.slice(0, 32 - suffix.length) : label) + suffix;
    if (!has(cand)) return cand;
  }
}
