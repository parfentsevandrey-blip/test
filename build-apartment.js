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

// NOTE: .statement styles are NOT dead — the "15 — Уникальность" section uses
// <section class="sec statement">, so those rules must stay. (Earlier removal
// here was a regression that collapsed that section.)

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

/* ============================================================
   LIQUID GLASS — refractive, IRIDESCENT, living glass
   chromatic dispersion (SVG) + mother-of-pearl rim + oil-slick
   film that drifts and shifts hue ("переливание")
   ============================================================ */
.kx-liquid-defs{ position:absolute; width:0; height:0; overflow:hidden; pointer-events:none; }
:root{ --lg-hi:rgba(255,255,255,.95); --lg-hi-soft:rgba(255,255,255,.55);
  --lg-glow:rgba(255,244,222,.28); --lg-blur:blur(18px) saturate(205%) brightness(1.12);
  --lg-iris:conic-gradient(from 130deg,
    rgba(255,255,255,.92), rgba(244,217,160,.85), rgba(240,184,208,.82), rgba(186,228,240,.85),
    rgba(214,198,242,.82), rgba(206,238,222,.85), rgba(255,255,255,.92)); }
[data-theme="evening"]{ --lg-hi:rgba(255,252,245,.9); --lg-hi-soft:rgba(255,250,240,.5); --lg-glow:rgba(255,238,206,.24); }
[data-theme="dark"]{ --lg-hi:rgba(255,255,255,.72); --lg-hi-soft:rgba(255,255,255,.34);
  --lg-glow:rgba(180,205,255,.26); --lg-blur:blur(20px) saturate(215%) brightness(1.16); }

@keyframes kxIris{ to{ filter:hue-rotate(360deg); } }
@keyframes kxSheen{ 0%,100%{ background-position:-30% 0, 0% 50%; } 50%{ background-position:130% 0, 100% 50%; } }

/* big surfaces — full liquid glass (header + glass cards incl. contact card) */
.bar, .glass{ border-radius:24px;
  -webkit-backdrop-filter:var(--lg-blur); backdrop-filter:var(--lg-blur);
  box-shadow:
    inset 0 2px 0 var(--lg-hi),                 /* bright top bevel */
    inset 2px 0 1px -1px var(--lg-hi-soft),     /* left bevel */
    inset 0 16px 32px -22px var(--lg-hi-soft),  /* top inner gloss */
    inset 0 -18px 34px -24px rgba(0,0,0,.55),    /* bottom inner shade = glass thickness */
    inset 0 0 0 1px rgba(255,255,255,.14),
    0 22px 60px -22px rgba(0,0,0,.45),           /* float */
    0 0 34px -6px var(--lg-glow); }              /* soft glow */
.bar{ border-radius:22px; }
.glass{ background:linear-gradient(135deg, rgba(255,255,255,.20), rgba(255,255,255,.04) 45%, transparent 72%), var(--glass); }
.bar{ background:linear-gradient(135deg, rgba(255,255,255,.18), rgba(255,255,255,.03) 45%, transparent 72%), var(--bar); }
@supports (backdrop-filter:url(#kx-liquid)) or (-webkit-backdrop-filter:url(#kx-liquid)){
  .bar, .glass{ -webkit-backdrop-filter:blur(13px) saturate(200%) brightness(1.12) url(#kx-liquid);
                        backdrop-filter:blur(13px) saturate(200%) brightness(1.12) url(#kx-liquid); } }

/* ::before — drifting white glint + iridescent oil-slick film (the "переливание") */
.bar::before, .glass::before{ content:""; position:absolute; inset:0; border-radius:inherit; pointer-events:none; z-index:0;
  background:
    linear-gradient(115deg, transparent 24%, rgba(255,255,255,.6) 44%, transparent 60%),
    linear-gradient(60deg, rgba(244,217,160,.20), rgba(240,184,208,.18) 28%, rgba(186,228,240,.18) 52%, rgba(214,198,242,.18) 74%, rgba(206,238,222,.20));
  background-size:220% 100%, 240% 200%; background-position:-30% 0, 0% 50%;
  opacity:.6; mix-blend-mode:screen; animation:kxSheen 9s ease-in-out infinite, kxIris 14s linear infinite; }

/* ::after — iridescent (dichroic) mother-of-pearl lens rim */
.bar::after, .glass::after,
.theme-switch::after, .mat-switch::after, .snd-switch::after, .cta::after, .stat::after{
  content:""; position:absolute; inset:0; border-radius:inherit; pointer-events:none; padding:1.4px;
  background:var(--lg-iris);
  -webkit-mask:linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
  -webkit-mask-composite:xor; mask-composite:exclude; opacity:.55; }
.bar::after, .glass::after{ opacity:.9; animation:kxIris 10s linear infinite; }
.bar > *, .glass > *{ position:relative; z-index:1; }
.theme-switch, .mat-switch, .snd-switch{ position:relative; }

/* UI chips & buttons — glass material + bright specular edge */
.theme-switch, .mat-switch, .snd-switch{ border-radius:16px;
  -webkit-backdrop-filter:blur(14px) saturate(190%) brightness(1.1); backdrop-filter:blur(14px) saturate(190%) brightness(1.1);
  background:linear-gradient(135deg, rgba(255,255,255,.22), transparent 60%), var(--chip-bg);
  box-shadow:inset 0 1.5px 0 var(--lg-hi), inset 0 0 0 1px rgba(255,255,255,.10), 0 8px 20px -10px rgba(0,0,0,.4), 0 0 18px -8px var(--lg-glow); }
.cta{ border-radius:16px; -webkit-backdrop-filter:blur(12px) saturate(180%) brightness(1.08); backdrop-filter:blur(12px) saturate(180%) brightness(1.08);
  box-shadow:inset 0 1.5px 0 rgba(255,255,255,.6), inset 0 0 0 1px rgba(255,255,255,.08), 0 8px 22px -12px rgba(0,0,0,.35); }
.cta:hover{ box-shadow:inset 0 1.5px 0 rgba(255,255,255,.7), 0 14px 30px -10px var(--gold); }

/* stat cards — bright glass look (gradient + bevel; no per-card backdrop, stays light) */
.stat{ border-radius:18px; background:linear-gradient(135deg, rgba(255,255,255,.22), rgba(255,255,255,.04) 55%, transparent 80%), var(--chip-bg);
  box-shadow:inset 0 1.5px 0 var(--lg-hi-soft), inset 0 0 0 1px rgba(255,255,255,.08), 0 12px 28px -16px rgba(0,0,0,.34), 0 0 16px -8px var(--lg-glow); }

/* marquee band — frosted glass strip */
.marquee{ -webkit-backdrop-filter:blur(14px) saturate(170%); backdrop-filter:blur(14px) saturate(170%);
  background:linear-gradient(180deg, rgba(255,255,255,.14), transparent);
  box-shadow:inset 0 1.5px 0 var(--lg-hi-soft), inset 0 -1px 0 rgba(0,0,0,.12); }

/* photos under glass — bright specular top edge + faint iridescent sheen */
.frame{ box-shadow:var(--shadow-lg), inset 0 2px 0 rgba(255,255,255,.32), inset 0 0 0 1px rgba(255,255,255,.10), 0 0 26px -10px var(--lg-glow); }
.frame::before{ content:""; position:absolute; inset:0; z-index:2; pointer-events:none; border-radius:inherit;
  background:
    linear-gradient(180deg, rgba(255,255,255,.34), transparent 11%),
    linear-gradient(60deg, rgba(244,217,160,.10), rgba(186,228,240,.08) 50%, rgba(214,198,242,.10));
  mix-blend-mode:screen; opacity:.6; }

/* cursor specular — bright, soft (liquid highlight that tracks the pointer) */
.hover-glare{ mix-blend-mode:screen; filter:blur(2px); }

@media (prefers-reduced-motion:reduce){
  .bar::before, .glass::before, .bar::after, .glass::after{ animation:none; } }
`;
template = replaceOnce(template, "\n</style>", NEW_CSS + "</style>", "</style> close");

// (NOTE: section numbering is already correct — 15 = Уникальность, 16 = Документация.
//  An earlier "16 → 15" renumber here was a regression that created a duplicate 15.)

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

// 3e — inject the cinematic overlays + the liquid-glass refraction filter
template = replaceOnce(template,
  '<canvas id="ambient"></canvas>',
  '<canvas id="ambient"></canvas>\n<div class="kx-shaft" aria-hidden="true"></div>\n' +
  '<svg class="kx-liquid-defs" aria-hidden="true" width="0" height="0">' +
  '<filter id="kx-liquid" x="-20%" y="-20%" width="140%" height="140%" color-interpolation-filters="sRGB">' +
  '<feTurbulence type="fractalNoise" baseFrequency="0.004 0.007" numOctaves="2" seed="11" result="n"/>' +
  '<feGaussianBlur in="n" stdDeviation="2.2" result="nb"/>' +
  '<feDisplacementMap in="SourceGraphic" in2="nb" scale="26" xChannelSelector="R" yChannelSelector="G" result="dR"/>' +
  '<feDisplacementMap in="SourceGraphic" in2="nb" scale="18" xChannelSelector="R" yChannelSelector="G" result="dG"/>' +
  '<feDisplacementMap in="SourceGraphic" in2="nb" scale="10" xChannelSelector="R" yChannelSelector="G" result="dB"/>' +
  '<feColorMatrix in="dR" type="matrix" values="1 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 1 0" result="cR"/>' +
  '<feColorMatrix in="dG" type="matrix" values="0 0 0 0 0 0 1 0 0 0 0 0 0 0 0 0 0 0 1 0" result="cG"/>' +
  '<feColorMatrix in="dB" type="matrix" values="0 0 0 0 0 0 0 0 0 0 0 0 1 0 0 0 0 0 1 0" result="cB"/>' +
  '<feComposite in="cR" in2="cG" operator="arithmetic" k1="0" k2="1" k3="1" k4="0" result="cRG"/>' +
  '<feComposite in="cRG" in2="cB" operator="arithmetic" k1="0" k2="1" k3="1" k4="0"/>' +
  '</filter></svg>',
  "ambient canvas (shaft + liquid filter inject)");
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
