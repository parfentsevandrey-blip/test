#!/usr/bin/env node
// Verifies that every t("…")/tn("…")/tx("…") key used in the UI exists in both
// dictionaries, that ru/en have the same keys, and that plural entries have the
// right number of forms (ru: 3, en: 2). Exit code 1 on problems.
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const UI = path.resolve(here, "../internal/web/ui/js");
const ru = (await import(pathToFileURL(path.join(UI, "i18n/ru.js")))).default;
const en = (await import(pathToFileURL(path.join(UI, "i18n/en.js")))).default;

const files = [];
(function walk(d) {
  for (const f of fs.readdirSync(d)) {
    const p = path.join(d, f);
    if (fs.statSync(p).isDirectory()) { if (f !== "i18n") walk(p); }
    else if (f.endsWith(".js") && f !== "i18n.js") files.push(p);
  }
})(UI);

const used = new Map();
const dynamic = [];
for (const f of files) {
  const src = fs.readFileSync(f, "utf8");
  for (const m of src.matchAll(/\bt[nx]?\(\s*"([^"]+)"/g)) used.set(m[1], path.relative(UI, f));
  for (const m of src.matchAll(/\bt[nx]?\(\s*("[^"]*"\s*\+|`)/g)) dynamic.push(`${path.relative(UI, f)}: ${src.slice(m.index, m.index + 60).split("\n")[0]}`);
}
let bad = 0;
for (const [k, f] of used) {
  if (k.endsWith(".")) { // dynamic suffix: t("nat.head." + x) — at least one key must exist
    if (!Object.keys(ru).some((x) => x.startsWith(k))) { console.log(`no ru keys with prefix: ${k}  (${f})`); bad++; }
    continue;
  }
  if (!(k in ru)) { console.log(`missing in ru: ${k}  (${f})`); bad++; }
  if (!(k in en)) { console.log(`missing in en: ${k}  (${f})`); bad++; }
}
for (const k of Object.keys(ru)) if (!(k in en)) { console.log(`ru-only key: ${k}`); bad++; }
for (const k of Object.keys(en)) if (!(k in ru)) { console.log(`en-only key: ${k}`); bad++; }
for (const k of Object.keys(ru)) {
  if (Array.isArray(ru[k]) && ru[k].length !== 3) { console.log(`ru plural needs 3 forms: ${k}`); bad++; }
  if (Array.isArray(en[k]) && en[k].length !== 2) { console.log(`en plural needs 2 forms: ${k}`); bad++; }
  if (Array.isArray(ru[k]) !== Array.isArray(en[k])) { console.log(`plural mismatch: ${k}`); bad++; }
}
// Dynamic keys whose suffixes come from fixed lists in the code.
const DYNAMIC = {
  "set.sec.": ["device", "network", "tun", "files", "interface", "advanced", "about"],
  "nat.chip.": ["open", "easy", "hard", "unknown"], "nat.title.": ["open", "easy", "hard", "unknown"],
  "nat.head.": ["open", "easy", "hard", "unknown"], "nat.text.": ["open", "easy", "hard", "unknown"],
  "nat.means.": ["open", "easy", "hard", "unknown"], "nat.kind.": ["local", "stun", "observed"],
  "nat.kindHint.": ["local", "stun", "observed"], "path.": ["lan", "direct", "relay", "none"],
  "path.long.": ["lan", "direct", "relay", "none"], "mail.st.": ["queued", "sent", "delivered", "failed"],
  "mail.folder.": ["inbox", "sent", "trash"], "mail.empty.": ["inbox", "sent", "trash"], "mail.emptyText.": ["inbox", "sent", "trash"],
  "mail.att.": ["ready", "fetching", "remote", "failed"], "chat.st.": ["queued", "sent", "delivered", "failed"],
  "files.subtitle.": ["send", "browse", "shares"], "logs.lv.": ["all", "info", "warn", "error"],
  "err.": ["unauthorized", "notconfigured", "denied", "notfound", "invalid", "exists", "offline", "busy", "toolarge", "unsupported", "internal", "network", "aborted"],
};
for (const [p, list] of Object.entries(DYNAMIC)) for (const x of list) {
  if (!(p + x in ru)) { console.log(`missing in ru: ${p + x} (dynamic)`); bad++; }
  if (!(p + x in en)) { console.log(`missing in en: ${p + x} (dynamic)`); bad++; }
}
const prefixes = [...used.keys()].filter((k) => k.endsWith("."));
const unused = Object.keys(ru).filter((k) => !used.has(k) && !prefixes.some((p) => k.startsWith(p)));
console.log(`${used.size} keys used statically, ${Object.keys(ru).length} in ru, ${unused.length} not referenced statically (may be dynamic).`);
if (process.argv.includes("--verbose")) { console.log("dynamic lookups:\n  " + dynamic.join("\n  ")); console.log("unreferenced:\n  " + unused.join("\n  ")); }
process.exit(bad ? 1 : 0);
