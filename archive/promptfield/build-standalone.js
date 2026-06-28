#!/usr/bin/env node
/* =========================================================
   build-standalone.js
   Bundles the site into ONE self-contained HTML file that
   opens by double-click (no server, no internet): CSS, the UI
   script, and the entire Three.js module graph (core + post-
   processing addons + the scene) are inlined.

   Browsers refuse to load an ES-module graph from file://, so
   every module is embedded as text and the graph is rebuilt at
   runtime with Blob URLs — each module's import specifiers are
   rewritten to the Blob URLs of its dependencies, created in
   dependency order. Same-origin Blob URLs import cleanly from
   disk.

   Usage:  node build-standalone.js
   ========================================================= */
"use strict";

const fs = require("fs");
const path = require("path");
const root = __dirname;
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");

const ENTRY_FILE = "js/scene.js";   // the ES-module entry (the WebGL scene)
const UI_FILE = "js/ui.js";         // the plain (non-module) UI script
const CSS_FILE = "css/app.css";
const HTML_FILE = "index.html";
const OUT_FILE = "promptfield.html";  // self-contained downloadable build

/* ---------- module graph ---------- */
// Canonical ids: "scene", "three", or "addons/<path under js/vendor/three/addons>"
const fileForId = (id) => {
  if (id === "scene") return ENTRY_FILE;
  if (id === "three") return "js/vendor/three/three.module.js";
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
  return null;
};

const modules = new Map();
const crawl = (id) => {
  if (modules.has(id)) return;
  const code = read(fileForId(id));
  const deps = [];
  if (id !== "three") { // three.module.js is a self-contained leaf
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
const css = read(CSS_FILE);
if (/<\/style/i.test(css)) throw new Error("CSS contains </style");
const uiJs = read(UI_FILE);
guard("ui.js", uiJs);

/* ---------- manifest + dom ids ---------- */
const domId = new Map();
order.forEach((id, i) => domId.set(id, "sm" + i));
const manifest = {
  entry: domId.get("scene"),
  order: order.map((id) => ({
    domId: domId.get(id),
    deps: Object.fromEntries(modules.get(id).deps.map((d) => [d.spec, domId.get(d.id)])),
  })),
};

/* ---------- runtime bootstrap (stringified into the page) ---------- */
function bootstrapFn() {
  var reg = JSON.parse(document.getElementById("sf-manifest").textContent);
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
    console.warn("scene failed to load:", err);
    window.dispatchEvent(new Event("scene:ready"));
  });
}

/* ---------- assemble the page ---------- */
let html = read(HTML_FILE);
html = html.replace(`  <link rel="stylesheet" href="${CSS_FILE}" />`, () => `  <style>\n${css}\n  </style>`);
html = html.replace(/  <!-- Three\.js[\s\S]*?<\/script>\n/, () => "  <!-- Three.js + scene embedded below; loaded via Blob URLs -->\n");

const blocks = order
  .map((id) => `  <script type="text/plain" id="${domId.get(id)}">${modules.get(id).code}</script>`)
  .join("\n");

const loader =
`  <!-- ===== Embedded application (self-contained) ===== -->
${blocks}
  <script type="application/json" id="sf-manifest">${JSON.stringify(manifest)}</script>
  <script type="module">(${bootstrapFn.toString()})();</script>
  <script>\n${uiJs}\n  </script>`;

html = html.replace(
  new RegExp(`  <script type="module" src="${ENTRY_FILE.replace(/[.\/]/g, "\\$&")}"><\\/script>\\n  <script src="${UI_FILE.replace(/[.\/]/g, "\\$&")}" defer><\\/script>`),
  () => loader
);

// sanity: nothing left pointing at external app files
[`href="${CSS_FILE}"`, `src="${ENTRY_FILE}"`, `src="${UI_FILE}"`, 'type="importmap"'].forEach((s) => {
  if (html.includes(s)) throw new Error("Build incomplete; not inlined: " + s);
});

fs.writeFileSync(path.join(root, OUT_FILE), html, "utf8");
const kb = (n) => (n / 1024).toFixed(0) + " KB";
console.log(`Wrote ${OUT_FILE} (${kb(Buffer.byteLength(html))}) — inlined ${order.length} JS modules + CSS + UI script.`);
