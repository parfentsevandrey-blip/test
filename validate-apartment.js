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
ok(jsCount === 10, `JS assets = ${jsCount} (expect 10)`);
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
const engine  = uuidOfModule('living atmosphere');
const postfx  = uuidOfModule('KXPostFX');
const glshare = uuidOfModule('window.KXGL =');
const hero    = uuidOfModule('cinematic centrepiece');
const gallery = uuidOfModule('2.5D photographs');
const mats    = uuidOfModule('real materials');
const plan    = uuidOfModule('light-table sweep');
ok(order.length >= 10, `script tags in template = ${order.length}`);
ok(THREE && idx(THREE) < idx(postfx), "three.js loads before postfx");
ok(idx(postfx) < idx(hero), "postfx before hero3d");
ok(idx(glshare) < idx(gallery) && idx(glshare) < idx(mats) && idx(glshare) < idx(plan), "glshared before gallery/materials/plan");
ok(idx(engine) >= 0 && idx(engine) > idx(glshare), "engine3d wired after glshared");
ok(idx(hero) > idx(engine), "hero3d after engine3d");

// 4) template edits applied
ok(!resolved.includes("canvas.ascii"), "dead ascii css removed");
ok(resolved.includes(".hero-gl{"), "hero-gl css present");
ok(resolved.includes(".mat-preview{"), "material-preview css present");
ok(resolved.includes(".gl-depth"), "gallery overlay css present");
ok(resolved.includes(".gl-sweep"), "plan sweep css present");
ok(resolved.includes("15 — Документация") && !resolved.includes("16 — Документация"), "section renumbered 16→15");

// 5) structural sanity of the unpacked document
const balanced = (a, b) => (resolved.split(a).length - 1) === (resolved.split(b).length - 1);
ok((resolved.match(/<body[\s>]/)||[]).length >= 1 && resolved.includes("</body>"), "has <body>…</body>");
ok((resolved.match(/<canvas id="ambient">/)||[]).length === 1, "ambient canvas present");
fs.writeFileSync(path.join(require("os").tmpdir(), "kx-unpacked.html"), resolved);
console.log("\nunpacked preview → " + path.join(require("os").tmpdir(), "kx-unpacked.html"));

console.log(fail ? `\n${fail} CHECK(S) FAILED\n` : "\nALL CHECKS PASSED\n");
process.exit(fail ? 1 : 0);
