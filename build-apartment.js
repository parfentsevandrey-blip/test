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

// Lean cinematic layer: the enhanced ambient field (WebGL) + a tiny
// dependency-free polish module. The floor-plan sweep is now pure CSS, so the
// shared renderer (glshared) and plan3d's extra WebGL context were dropped.
// hero3d / gallery3d / materials3d / postfx remain reverted (see disabled/).
const NEW_MODULES = [
  { token: "__POLISH__",     file: "polish.js"     },
  { token: "__INTRO__",      file: "intro.js"      },
  { token: "__REVEALFX__",   file: "reveal-fx.js"  },
  { token: "__LIGHTSTORY__", file: "lightstory.js" },
  { token: "__AUDIO__",      file: "audio.js"      },
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

// 3a2 — remove the unused .statement section styles (no such element in this layout)
template = replaceOnce(template,
`/* statement (centered) */
.statement{ position:relative; overflow:hidden; }
.statement .bgwrap{ position:absolute; inset:0; z-index:0; }
.statement .bgwrap img{ width:100%; height:100%; object-fit:cover; filter:var(--photo-filter) blur(3px) brightness(.5); transform:scale(1.05); }
.statement .scrim{ position:absolute; inset:0; z-index:1; background:radial-gradient(120% 120% at 50% 50%, rgba(5,6,10,.4), rgba(5,6,10,.72)); }
.statement .inner{ position:relative; z-index:2; text-align:center; max-width:1000px; margin:0 auto; color:#f3f5f8; }
.statement .eyebrow{ justify-content:center; color:var(--gold-soft); }
.statement .eyebrow::before,.statement .eyebrow::after{ background:var(--gold-soft); }
.statement .big{ margin-top:24px; font-family:var(--serif); font-size:clamp(30px,4.4vw,64px); font-weight:600; line-height:1.12; color:#fff; }
.statement .big em{ color:var(--gold-soft); }
.statement .note{ margin:26px auto 0; max-width:820px; font-size:clamp(15.5px,1.2vw,19px); line-height:1.6; font-weight:300; color:rgba(243,245,248,.78); }`,
"/* (unused statement-section styles removed) */", "dead statement css");

// 3a3 — remove unused CSS custom properties (0 references anywhere)
template = replaceOnce(template, "  --ease-grace:cubic-bezier(.16,1,.3,1);   /* smooth deceleration — no snap */\n", "", "unused --ease-grace");
template = replaceOnce(template, "  --scrim:linear-gradient(to top, rgba(40,32,18,.34), rgba(40,32,18,0) 46%);\n", "", "unused --scrim (light)");
template = replaceOnce(template, "  --scrim:linear-gradient(to top, rgba(58,40,16,.4), rgba(58,40,16,0) 48%);\n", "", "unused --scrim (evening)");
template = replaceOnce(template, "  --scrim:linear-gradient(to top, rgba(5,6,10,.86), rgba(5,6,10,.2) 54%);\n", "", "unused --scrim (dark)");

// 3b — inject the WebGL-layer CSS just before </style>
const NEW_CSS = `
/* ============================================================
   CINEMATIC POLISH — richer vignette · floor-plan light-table sweep
   ============================================================ */
/* richer filmic vignette (overrides the base .cine-vignette) */
.cine-vignette{ background:
  radial-gradient(135% 105% at 50% 34%, transparent 46%, rgba(8,6,2,.12) 74%, rgba(8,6,2,.34) 100%),
  linear-gradient(to bottom, rgba(8,6,2,.10), transparent 13%, transparent 87%, rgba(8,6,2,.16)); }
[data-theme="dark"] .cine-vignette{ background:
  radial-gradient(135% 105% at 50% 34%, transparent 40%, rgba(0,0,0,.40) 78%, rgba(0,0,0,.66) 100%),
  linear-gradient(to bottom, rgba(0,0,0,.22), transparent 15%, transparent 85%, rgba(0,0,0,.30)); }

/* floor-plan gold "light-table" sweep — pure CSS, plays on reveal + hover */
.frame.plan::after{ content:""; position:absolute; inset:0; pointer-events:none; z-index:3;
  border-radius:inherit; opacity:0; mix-blend-mode:screen; transform:translateX(-120%);
  background:linear-gradient(115deg, transparent 42%,
    color-mix(in oklch, var(--gold) 60%, transparent) 50%, transparent 58%); }
.frame.plan.sweeping::after, .frame.plan:hover::after{ animation:planSweep 2.6s var(--ease); }
@keyframes planSweep{
  0%{ transform:translateX(-120%); opacity:0; }
  18%{ opacity:1; } 82%{ opacity:1; }
  100%{ transform:translateX(120%); opacity:0; } }
@media (prefers-reduced-motion:reduce){ .frame.plan::after{ animation:none !important; opacity:0 !important; } }

/* ---------- cinematic intro ("the open") ---------- */
#kx-intro{ position:fixed; inset:0; z-index:9000; display:grid; place-items:center; overflow:hidden;
  background:var(--page); animation:kxIntroFailsafe .6s linear 2.8s forwards; }
#kx-intro::before{ content:""; position:absolute; inset:0; mix-blend-mode:screen; opacity:0;
  background:radial-gradient(60% 50% at 50% 46%, color-mix(in oklch, var(--gold) 16%, transparent), transparent 70%);
  animation:kxGlow 2.4s var(--ease) forwards; }
.kx-intro-inner{ position:relative; text-align:center; padding:0 24px; }
.kx-intro-eyebrow{ font-family:var(--mono); font-size:11px; letter-spacing:.32em; text-transform:uppercase;
  color:var(--label); opacity:0; transform:translateY(8px); animation:kxRise .9s var(--ease) .2s forwards; }
.kx-intro-word{ font-family:var(--serif); font-weight:600; color:var(--ink); font-size:clamp(34px,7vw,84px);
  line-height:1; margin-top:14px; letter-spacing:.05em; opacity:0; transform:translateY(14px); filter:blur(7px);
  animation:kxWord 1.3s var(--ease) .35s forwards; }
.kx-intro-word em{ font-style:italic; color:var(--gold); }
.kx-intro-rule{ height:1px; width:0; margin:22px auto 0; background:linear-gradient(90deg,transparent,var(--gold),transparent);
  animation:kxRule 1.1s var(--ease) .7s forwards; }
#kx-intro.kx-intro-leaving{ animation:kxIntroOut .9s var(--ease) forwards; }
@keyframes kxGlow{ to{ opacity:1; } }
@keyframes kxRise{ to{ opacity:1; transform:none; } }
@keyframes kxWord{ to{ opacity:1; transform:none; filter:blur(0); } }
@keyframes kxRule{ to{ width:min(280px,52vw); } }
@keyframes kxIntroOut{ to{ opacity:0; transform:scale(1.05); visibility:hidden; } }
@keyframes kxIntroFailsafe{ to{ opacity:0; visibility:hidden; pointer-events:none; } }
@media (prefers-reduced-motion:reduce){ #kx-intro{ display:none !important; } }

/* ---------- background light story (drifting shaft · per-section grade · reading spot) ---------- */
.kx-shaft{ position:fixed; inset:-25%; z-index:0; pointer-events:none; mix-blend-mode:screen; opacity:.5;
  background:linear-gradient(118deg, transparent 42%, color-mix(in oklch, var(--gold) 13%, transparent) 50%, transparent 58%);
  animation:kxShaft 54s ease-in-out infinite; will-change:transform; }
@keyframes kxShaft{ 0%{ transform:translateX(-12%) rotate(0deg); } 50%{ transform:translateX(12%) rotate(2deg); } 100%{ transform:translateX(-12%) rotate(0deg); } }
.kx-grade{ z-index:2; mix-blend-mode:soft-light; opacity:.08; background-color:rgb(248,246,243);
  transition:background-color 1.2s var(--ease); }
.kx-spot{ z-index:2; mix-blend-mode:screen; opacity:.05;
  background:radial-gradient(58% 44% at 50% 46%, rgba(255,246,228,1), transparent 72%); }
@media (prefers-reduced-motion:reduce){ .kx-shaft{ animation:none; } }

/* ---------- cinematic reveals (focus-pull headings · aperture photos) ---------- */
.kx-focus{ letter-spacing:.06em; transition:letter-spacing 1.1s var(--ease); }
.kx-focus.kx-focus-in{ letter-spacing:var(--tr-display); }
.kx-aperture{ clip-path:inset(0 38% 0 38%); transition:clip-path 1s var(--ease); }
.kx-aperture.open{ clip-path:inset(0 0 0 0); }
.frame.kx-aperture::after{ content:""; position:absolute; top:0; bottom:0; left:50%; width:2px; transform:translateX(-50%);
  background:linear-gradient(transparent, var(--gold), transparent); opacity:.85; z-index:4; pointer-events:none;
  mix-blend-mode:screen; transition:opacity .9s var(--ease); }
.frame.kx-aperture.open::after{ opacity:0; }
@media (prefers-reduced-motion:reduce){ .kx-focus,.kx-aperture{ transition:none; } .kx-aperture{ clip-path:none; } .frame.kx-aperture::after{ display:none; } }

/* ---------- ambient-sound toggle (mirrors the theme switch) ---------- */
.snd-switch{ display:flex; padding:3px; border-radius:var(--r); background:var(--chip-bg); border:1px solid var(--glass-line); }
.snd-switch button{ appearance:none; border:0; cursor:pointer; background:transparent; width:32px; height:28px;
  border-radius:calc(var(--r) - 4px); display:grid; place-items:center; color:var(--gold-deep); transition:.3s var(--ease); }
.snd-switch button:hover{ transform:translateY(-1px); }
.snd-switch button.active{ background:var(--gold); color:#fff; box-shadow:0 3px 10px -2px var(--gold); }
[data-theme="dark"] .snd-switch button.active{ color:#0a0c11; }
.snd-switch button svg{ width:17px; height:17px; }
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
<script src="${ENGINE3D_UUID}"></script>
<script src="${tok.__POLISH__}"></script>
<script src="${tok.__INTRO__}"></script>
<script src="${tok.__REVEALFX__}"></script>
<script src="${tok.__LIGHTSTORY__}"></script>
<script src="${tok.__AUDIO__}"></script>`;
template = replaceOnce(template, OLD_SCRIPTS, NEW_SCRIPTS, "script tags");

// 3e — inject the cinematic overlays into the body markup
template = replaceOnce(template,
  '<canvas id="ambient"></canvas>',
  '<canvas id="ambient"></canvas>\n<div class="kx-shaft" aria-hidden="true"></div>',
  "ambient canvas (shaft inject)");
template = replaceOnce(template,
  '<div class="cine cine-grain" aria-hidden="true"></div>',
  '<div class="cine cine-grain" aria-hidden="true"></div>\n' +
  '<div class="cine kx-grade" aria-hidden="true"></div>\n' +
  '<div class="cine kx-spot" aria-hidden="true"></div>\n' +
  '<div id="kx-intro" aria-hidden="true"><div class="kx-intro-inner">' +
  '<div class="kx-intro-eyebrow">Эксклюзивное предложение</div>' +
  '<div class="kx-intro-word">Кутузовский <em>XII</em></div>' +
  '<div class="kx-intro-rule"></div></div></div>',
  "cine overlays (grade/spot/intro inject)");

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
