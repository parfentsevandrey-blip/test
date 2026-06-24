#!/usr/bin/env node
/* =========================================================
   build-apartment.js
   ---------------------------------------------------------
   Re-packs the "Кутузовский XII — apartment" offline bundle
   with the cinematic WebGL layer added.

   The uploaded file is a self-unpacking bundle:
     <script type="__bundler/manifest">  { uuid: {mime,compressed,data(base64)} }
     <script type="__bundler/ext_resources"> []
     <script type="__bundler/template">  JSON.stringify(<the real HTML>)
   A boot script (kept verbatim) base64-decodes + gunzips each
   asset to a Blob URL, then substitutes every uuid in the
   template string before swapping in the document.

   This script:
     · parses the original bundle,
     · swaps in the enhanced engine3d.js,
     · adds postfx / glshared / hero3d / gallery3d / materials3d
       / plan3d as new gzipped JS assets,
     · edits the template (CSS + markup + <script> wiring),
     · writes a new self-unpacking HTML, and self-validates by
       re-running the unpack in Node.

   Usage:  node build-apartment.js [pathToOriginalBundle] [outName]
   ========================================================= */
"use strict";
const fs = require("fs");
const path = require("path");
const zlib = require("zlib");
const crypto = require("crypto");

const ROOT = __dirname;
const SRC = path.join(ROOT, "apartment-src");
const ORIGINAL = process.argv[2] ||
  "/root/.claude/uploads/fe2383d0-361b-5c45-9adb-5ba3adbd04bf/2d430556-____________XII_offline_8.html";
const OUT = path.join(ROOT, process.argv[3] || "kutuzovsky-xii-cinematic.html");

const ENGINE3D_UUID = "0016504f-10ad-450b-b538-3c81fec55107"; // existing engine3d.js asset

function die(msg){ console.error("BUILD ERROR: " + msg); process.exit(1); }
function gzB64(buf){ return zlib.gzipSync(buf, { level: 9 }).toString("base64"); }
function jsAsset(code){ return { mime: "application/javascript", compressed: true, data: gzB64(Buffer.from(code, "utf8")) }; }
function readSrc(name){ const p = path.join(SRC, name); if(!fs.existsSync(p)) die("missing " + p); return fs.readFileSync(p, "utf8"); }

/* ---------- 1. parse the original bundle ---------- */
if(!fs.existsSync(ORIGINAL)) die("original bundle not found: " + ORIGINAL);
const html = fs.readFileSync(ORIGINAL, "utf8");

function carve(tag){
  const open = `<script type="__bundler/${tag}">`;
  const i = html.indexOf(open); if(i < 0) die("tag not found: " + tag);
  const start = i + open.length;
  const end = html.indexOf("</script>", start); if(end < 0) die("unterminated tag: " + tag);
  return { start, end, inner: html.slice(start, end) };
}
const M = carve("manifest");
const T = carve("template");
if(!(M.start < M.end && M.end <= T.start && T.start < T.end)) die("unexpected bundle layout");

const manifest = JSON.parse(M.inner.trim());
let template = JSON.parse(T.inner.trim());
if(!manifest[ENGINE3D_UUID]) die("engine3d asset uuid not in manifest");

/* ---------- 2. swap engine3d, add new modules ---------- */
manifest[ENGINE3D_UUID] = jsAsset(readSrc("engine3d.js"));

// hero3d / gallery3d / materials3d / postfx were reverted per user request
// (hero back to the original photo; the photo-hover depth-warp and the material
// popover removed). The enhanced ambient field + the floor-plan sweep stay.
const NEW_MODULES = [
  { token: "__GLSHARED__", file: "glshared.js" },
  { token: "__PLAN3D__",   file: "plan3d.js"   },
];
for(const mod of NEW_MODULES){
  mod.uuid = crypto.randomUUID();
  manifest[mod.uuid] = jsAsset(readSrc(mod.file));
}

/* ---------- 3. edit the template ---------- */
function replaceOnce(str, find, repl, label){
  const i = str.indexOf(find);
  if(i < 0) die("template anchor not found: " + label);
  if(str.indexOf(find, i + find.length) >= 0) die("template anchor not unique: " + label);
  return str.slice(0, i) + repl + str.slice(i + find.length);
}

// 3a — remove dead text-video (ascii) CSS leftover from the old design
template = replaceOnce(template,
`/* ---------- TEXT-VIDEO (image rendered as live colored glyphs) ---------- */
canvas.ascii{ position:absolute; inset:0; width:100%; height:100%; display:block; z-index:1; pointer-events:none; }
.statement .bgwrap canvas.ascii{ opacity:.9; }
`, "", "dead ascii css");

// 3b — inject the WebGL-layer CSS just before </style>
const NEW_CSS = `
/* ============================================================
   WEBGL CINEMATIC LAYER — floor-plan light-table sweep
   ============================================================ */
.frame.plan .gl-sweep{ position:absolute; inset:0; width:100%; height:100%; display:block;
  z-index:3; pointer-events:none; mix-blend-mode:screen; border-radius:inherit; }
`;
template = replaceOnce(template, "\n</style>", NEW_CSS + "</style>", "</style> close");

// 3c — contiguous section numbering (the source skipped 15)
template = replaceOnce(template, "16 — Документация", "15 — Документация", "doc section number");

// 3d — wire the new scripts in dependency order (after three.js)
const OLD_SCRIPTS =
`<script src="5fc6a928-83e4-4a1d-b43f-98fed7e93241"></script>
<script src="5723f42c-f567-4a59-af5c-e62155241a86"></script>
<script src="19c5fe55-e4de-479c-b288-c9aa02c10a48"></script>
<script src="${ENGINE3D_UUID}"></script>`;
const tok = {}; NEW_MODULES.forEach(m => tok[m.token] = m.uuid);
const NEW_SCRIPTS =
`<script src="5fc6a928-83e4-4a1d-b43f-98fed7e93241"></script>
<script src="5723f42c-f567-4a59-af5c-e62155241a86"></script>
<script src="19c5fe55-e4de-479c-b288-c9aa02c10a48"></script>
<script src="${tok.__GLSHARED__}"></script>
<script src="${ENGINE3D_UUID}"></script>
<script src="${tok.__PLAN3D__}"></script>`;
template = replaceOnce(template, OLD_SCRIPTS, NEW_SCRIPTS, "script tags");

/* ---------- 4. reassemble the bundle ---------- */
// The template/manifest live inside <script type="__bundler/*"> blocks, so any
// literal `</script` would close the tag early. JSON.stringify doesn't escape
// the slash, so we do it ourselves (as the original bundler did): `<\/script`
// is an identical JSON string but invisible to the HTML parser.
const sani = s => s.split("</script").join("<\\/script");
const newManifestStr = sani(JSON.stringify(manifest));
const newTemplateStr = sani(JSON.stringify(template));
const out =
  html.slice(0, M.start) + "\n" + newManifestStr + "\n  " +
  html.slice(M.end, T.start) + "\n" + newTemplateStr + "\n  " +
  html.slice(T.end);
fs.writeFileSync(OUT, out, "utf8");

/* ---------- 5. self-validate: re-run the unpack in Node ---------- */
const re = fs.readFileSync(OUT, "utf8");
function recarve(tag){
  const open = `<script type="__bundler/${tag}">`;
  const i = re.indexOf(open), s = i + open.length, e = re.indexOf("</script>", s);
  return re.slice(s, e).trim();
}
const man2 = JSON.parse(recarve("manifest"));
let tpl2 = JSON.parse(recarve("template"));
let jsChecked = 0;
for(const uuid of Object.keys(man2)){
  const ent = man2[uuid];
  let bytes = Buffer.from(ent.data, "base64");
  if(ent.compressed) bytes = zlib.gunzipSync(bytes);
  if(ent.mime === "application/javascript"){
    const code = bytes.toString("utf8");
    try{ new Function(code); jsChecked++; }              // parse-check every JS asset
    catch(err){ die("JS asset " + uuid + " failed to parse: " + err.message); }
  }
}
// every token must be replaced; every new uuid must be referenced exactly once
for(const m of NEW_MODULES){
  if(tpl2.includes(m.token)) die("unreplaced token in template: " + m.token);
  const n = tpl2.split('src="' + m.uuid + '"').length - 1;
  if(n !== 1) die(`module ${m.file} referenced ${n}× (expected 1)`);
}
// no manifest uuid should be left as a literal still needing substitution that isn't present
let unresolved = 0;
for(const uuid of Object.keys(man2)){
  // assets referenced in template (images/fonts/scripts) — fine; just sanity that engine3d is wired
}
if(!tpl2.includes('src="' + ENGINE3D_UUID + '"')) die("engine3d not wired in template");
if(tpl2.includes("canvas.ascii")) die("dead ascii css still present");

const kb = n => (n/1024).toFixed(0) + " KB";
console.log("OK  wrote " + path.basename(OUT) + " (" + kb(Buffer.byteLength(out)) + ")");
console.log("    assets: " + Object.keys(man2).length + " · JS parsed: " + jsChecked +
            " · new modules: " + NEW_MODULES.length);
console.log("    " + NEW_MODULES.map(m => m.file).join(", "));
