#!/usr/bin/env node
/* Full offline-unpack simulation of the built bundle — mirrors the
   in-page boot script (base64 → gunzip → uuid→blob substitution) and
   asserts the result is internally consistent. No browser needed. */
"use strict";
const fs = require("fs");
const path = require("path");
const zlib = require("zlib");
const { execSync } = require("child_process");

const OUT = path.join(__dirname, process.argv[2] || "kutuzovsky-xii-cinematic.html");
const html = fs.readFileSync(OUT, "utf8");
let fail = 0;
const ok = (c, m) => { console.log((c ? "  ok  " : "FAIL  ") + m); if(!c) fail++; };

function carve(tag){
  const open = `<script type="__bundler/${tag}">`;
  const i = html.indexOf(open), s = i + open.length, e = html.indexOf("</script>", s);
  return html.slice(s, e).trim();
}
const manifest = JSON.parse(carve("manifest"));
let template   = JSON.parse(carve("template"));
console.log(`\nbundle: ${Object.keys(manifest).length} assets, template ${(template.length/1024).toFixed(0)} KB\n`);

// 1) decode every asset exactly as the browser would
const blob = {};
let jsCount = 0, imgCount = 0, fontCount = 0, blobN = 0;
const tmp = path.join(require("os").tmpdir(), "kx-js");
fs.mkdirSync(tmp, { recursive:true });
for(const uuid of Object.keys(manifest)){
  const e = manifest[uuid];
  let bytes;
  try{
    bytes = Buffer.from(e.data, "base64");
    if(e.compressed) bytes = zlib.gunzipSync(bytes);
  }catch(err){ ok(false, `decode ${uuid}: ${err.message}`); continue; }
  blob[uuid] = "blob:kxobj-" + (blobN++);   // placeholder must NOT embed the uuid
  if(e.mime === "application/javascript"){
    jsCount++;
    const f = path.join(tmp, uuid + ".js");
    fs.writeFileSync(f, bytes);
    try{ execSync(`node --check "${f}"`, { stdio:"pipe" }); }
    catch(err){ ok(false, `node --check ${uuid}: ${err.stderr||err.message}`); }
  } else if(e.mime.startsWith("image")) imgCount++;
  else if(e.mime.startsWith("font")) fontCount++;
}
ok(jsCount === 9, `JS assets = ${jsCount} (expect 9)`);
ok(imgCount === 18, `image assets = ${imgCount} (expect 18)`);
ok(fontCount === 20, `font assets = ${fontCount} (expect 20)`);

// 2) substitute uuid → blob across the template (browser does the same)
let resolved = template;
for(const uuid of Object.keys(manifest)) resolved = resolved.split(uuid).join(blob[uuid]);

// every manifest key must be fully substituted (nothing left dangling)
let dangling = 0;
for(const uuid of Object.keys(manifest)) if(resolved.includes(uuid)) dangling++;
ok(dangling === 0, `no unresolved uuids remain (${dangling} dangling)`);

// 3) script load order (dependencies first)
const order = [...resolved.matchAll(/<script src="(blob:kxobj-\d+)"><\/script>/g)].map(m => m[1]);
const idx = u => order.indexOf(blob[u]);
function uuidOfModule(snippet){
  for(const uuid of Object.keys(manifest)){
    const e = manifest[uuid]; if(e.mime !== "application/javascript") continue;
    let b = Buffer.from(e.data,"base64"); if(e.compressed) b = zlib.gunzipSync(b);
    if(b.toString("utf8").includes(snippet)) return uuid;
  }
  return null;
}
const THREE   = uuidOfModule('THREE.REVISION') || uuidOfModule('Three.js Authors') || "";
const engine  = uuidOfModule('cinematic atmosphere');
ok(order.length === 9, `script tags in template = ${order.length} (expect 9)`);
ok(THREE && idx(THREE) < idx(engine), "three.js loads before engine3d");
ok(idx(engine) >= 0, "engine3d present");
[["polish",'polish.js'], ["intro",'"the open"'], ["reveal-fx",'cinematic reveals'],
 ["lightstory",'the light follows you'], ["audio",'generative ambient sound']
].forEach(([n,snip])=> ok(idx(uuidOfModule(snip)) >= 0, n+" present & wired"));

// 4) template edits applied
ok(!resolved.includes("canvas.ascii"), "dead ascii css removed");
ok(resolved.includes(".statement{"), ".statement styles present (used by «Уникальность» section)");
ok(!resolved.includes("--ease-grace"), "unused --ease-grace var removed");
ok(!resolved.includes("--scrim"), "unused --scrim var removed");
ok(!resolved.includes(".hero-gl"), "hero stays reverted (no hero-gl)");
ok(!resolved.includes(".mat-preview"), "no material popover");
ok(!resolved.includes(".gl-depth"), "no gallery overlay");
ok(resolved.includes("planSweep"), "css plan sweep present");
ok(resolved.includes(".cine-vignette"), "vignette present");
// new cinematic layer present
ok(resolved.includes('id="kx-intro"') && resolved.includes("kx-intro-word"), "intro title card present");
ok(resolved.includes('class="kx-shaft"'), "drifting light shaft present");
ok(resolved.includes("kx-grade") && resolved.includes("kx-spot"), "section grade + reading spot present");
ok(resolved.includes("kx-aperture") && resolved.includes("kx-focus"), "aperture + focus-pull reveal css present");
ok(resolved.includes(".snd-switch"), "ambient-sound toggle css present");
// liquid glass
ok(resolved.includes('id="kx-liquid"') && resolved.includes("feDisplacementMap"), "liquid-glass refraction filter present");
ok((resolved.match(/feDisplacementMap/g)||[]).length >= 3 && resolved.includes("feComposite"), "chromatic dispersion (RGB split) present");
ok(resolved.includes("--lg-iris") && resolved.includes("conic-gradient"), "iridescent (mother-of-pearl) rim present");
ok(resolved.includes("@keyframes kxIris") && resolved.includes("kxSheen"), "living shimmer (hue-cycle + drift) present");
ok(resolved.includes("url(#kx-liquid)"), "refraction wired into backdrop-filter");
ok(resolved.includes("15 — Уникальность"), "section 15 = Уникальность intact");
ok(resolved.includes("16 — Документация"), "section 16 = Документация intact");
ok(!resolved.includes("15 — Документация"), "no duplicate section 15");
ok((resolved.match(/15 —/g)||[]).length === 1, "exactly one section 15");

// 5) structural sanity of the unpacked document
const balanced = (a, b) => (resolved.split(a).length - 1) === (resolved.split(b).length - 1);
ok((resolved.match(/<body[\s>]/)||[]).length >= 1 && resolved.includes("</body>"), "has <body>…</body>");
ok((resolved.match(/<canvas id="ambient">/)||[]).length === 1, "ambient canvas present");
fs.writeFileSync(path.join(require("os").tmpdir(), "kx-unpacked.html"), resolved);
console.log("\nunpacked preview → " + path.join(require("os").tmpdir(), "kx-unpacked.html"));

console.log(fail ? `\n${fail} CHECK(S) FAILED\n` : "\nALL CHECKS PASSED\n");
process.exit(fail ? 1 : 0);
