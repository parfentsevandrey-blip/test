#!/usr/bin/env node
/* =========================================================
   build-standalone.js
   Bundles the ZENITH site into a single self-contained HTML
   file (zenith-residence.html): CSS, JS, and the full
   Three.js module graph (core + post-processing addons) are
   all inlined.

   The 3D code is an ES-module graph. Browsers won't load ES
   modules from file://, so every module is embedded as text
   and the graph is reconstructed at runtime with Blob URLs:
   each module's import specifiers are rewritten to the Blob
   URLs of its dependencies, created in dependency order. The
   result imports cleanly even when opened directly from disk.

   Usage:  node build-standalone.js
   ========================================================= */
"use strict";

const fs = require("fs");
const path = require("path");
const root = __dirname;
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");

/* ---------- module graph ---------- */
// Canonical ids: "three", "scene", or "addons/<path under js/vendor/three/addons>"
const fileForId = (id) => {
  if (id === "three") return "js/vendor/three/three.module.js";
  if (id === "scene") return "js/scene.js";
  if (id.startsWith("addons/")) return "js/vendor/three/" + id;
  throw new Error("unknown module id: " + id);
};
const resolveSpec = (spec, fromId) => {
  if (spec === "three") return "three";
  if (spec.startsWith("three/addons/")) return "addons/" + spec.slice("three/addons/".length);
  if (spec.startsWith(".")) {
    if (!fromId.startsWith("addons/")) throw new Error(`relative import "${spec}" from ${fromId}`);
    const fromRel = fromId.slice("addons/".length);
    const rel = path.posix.normalize(path.posix.join(path.posix.dirname(fromRel), spec));
    return "addons/" + rel;
  }
  return null; // any other bare specifier is ignored (none expected in this graph)
};

const modules = new Map();
const crawl = (id) => {
  if (modules.has(id)) return;
  const code = read(fileForId(id));
  const deps = [];
  // three.module.js is a self-contained leaf — don't scan 1.2MB for false matches
  if (id !== "three") {
    const specs = new Set(
      [...code.matchAll(/(?:from|import)\s*\(?\s*['"]([^'"]+)['"]/g)].map((m) => m[1])
    );
    for (const spec of specs) {
      const depId = resolveSpec(spec, id);
      if (depId) deps.push({ spec, id: depId });
    }
  }
  modules.set(id, { id, code, deps });
  for (const d of deps) crawl(d.id);
};
crawl("scene");

// topological order (dependencies before dependents)
const order = [];
const seen = new Set();
const visit = (id) => {
  if (seen.has(id)) return;
  seen.add(id);
  for (const d of modules.get(id).deps) visit(d.id);
  order.push(id);
};
visit("scene");

/* ---------- safety guards ---------- */
const guard = (name, src) => {
  if (/<\/script/i.test(src)) throw new Error(`Cannot inline ${name}: contains </script`);
  if (/<!--/.test(src)) throw new Error(`Cannot inline ${name}: contains <!--`);
};
for (const id of order) guard(id, modules.get(id).code);
const css = read("css/styles.css");
if (/<\/style/i.test(css)) throw new Error("styles.css contains </style");
const mainJs = read("js/main.js");
guard("main.js", mainJs);

/* ---------- manifest + dom ids ---------- */
const domId = new Map();
order.forEach((id, i) => domId.set(id, "zm" + i));
const manifest = {
  entry: domId.get("scene"),
  order: order.map((id) => ({
    domId: domId.get(id),
    deps: Object.fromEntries(modules.get(id).deps.map((d) => [d.spec, domId.get(d.id)])),
  })),
};

/* ---------- runtime bootstrap (stringified into the page) ---------- */
function bootstrapFn() {
  var reg = JSON.parse(document.getElementById("zen-manifest").textContent);
  var esc = function (s) { return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"); };
  var url = {};
  reg.order.forEach(function (m) {
    var code = document.getElementById(m.domId).textContent;
    Object.keys(m.deps).forEach(function (spec) {
      var b = url[m.deps[spec]];
      var e = esc(spec);
      code = code.replace(new RegExp("(from\\s*)(['\"])" + e + "\\2", "g"), "$1$2" + b + "$2");
      code = code.replace(new RegExp("(import\\s*)(['\"])" + e + "\\2", "g"), "$1$2" + b + "$2");
      code = code.replace(new RegExp("(import\\s*\\(\\s*)(['\"])" + e + "\\2", "g"), "$1$2" + b + "$2");
    });
    url[m.domId] = URL.createObjectURL(new Blob([code], { type: "text/javascript" }));
  });
  import(url[reg.entry]).catch(function (err) {
    console.warn("ZENITH scene failed to load:", err);
    window.dispatchEvent(new Event("scene:ready"));
  });
}

/* ---------- assemble the page ---------- */
// (function replacements so `$` sequences in inlined source are inserted verbatim)
let html = read("index.html");
html = html.replace('  <link rel="stylesheet" href="css/styles.css" />', () => `  <style>\n${css}\n  </style>`);
html = html.replace(/  <!-- Three\.js[\s\S]*?<\/script>\n/, () => "  <!-- Three.js + scene embedded below; loaded via Blob URLs -->\n");

const blocks = order
  .map((id) => `  <script type="text/plain" id="${domId.get(id)}">${modules.get(id).code}</script>`)
  .join("\n");

const loader =
`  <!-- ===== Embedded application (self-contained) ===== -->
${blocks}
  <script type="application/json" id="zen-manifest">${JSON.stringify(manifest)}</script>
  <script type="module">(${bootstrapFn.toString()})();</script>
  <script>\n${mainJs}\n  </script>`;

html = html.replace(
  /  <script type="module" src="js\/scene\.js"><\/script>\n  <script src="js\/main\.js" defer><\/script>/,
  () => loader
);

// inline local images (gallery photos) as data URIs
html = html.replace(/src="(img\/[^"]+)"/g, (_m, p) => {
  const buf = fs.readFileSync(path.join(root, p));
  const ext = path.extname(p).slice(1).toLowerCase();
  const mime = ext === "jpg" ? "jpeg" : ext;
  return `src="data:image/${mime};base64,${buf.toString("base64")}"`;
});

// inline the HDRI environment (referenced by string in scene.js) as a data URI
{
  const hdr = "img/env_dusk.hdr";
  if (html.includes(hdr)) {
    const buf = fs.readFileSync(path.join(root, hdr));
    const uri = `data:application/octet-stream;base64,${buf.toString("base64")}`;
    html = html.replace(new RegExp(hdr.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "g"), () => uri);
  }
}

// sanity: nothing left pointing at external app files
["href=\"css/styles.css\"", "src=\"js/scene.js\"", "src=\"js/main.js\"", "type=\"importmap\""].forEach((s) => {
  if (html.includes(s)) throw new Error("Build incomplete; not inlined: " + s);
});

const out = "kutuzovsky-12.html";
fs.writeFileSync(path.join(root, out), html, "utf8");
const kb = (n) => (n / 1024).toFixed(0) + " KB";
console.log(`Wrote ${out} (${kb(Buffer.byteLength(html))}) — inlined ${order.length} JS modules + CSS + UI script.`);
