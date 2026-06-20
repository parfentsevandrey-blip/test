#!/usr/bin/env node
/* =========================================================
   build-cinemagraph.js
   Bundles the "living photo" cinemagraph (courtyard.html +
   js/cinemagraph.js + the Three.js core + the source photo)
   into ONE self-contained file: courtyard-cinemagraph.html.

   Browsers refuse to load ES modules from file://, so the
   module graph (three + cinemagraph) is embedded as text and
   rebuilt at runtime with Blob URLs — each module's import
   specifiers are rewritten to the Blob URL of its dependency,
   created in dependency order. The photo is inlined as a data
   URI. The result renders when opened directly from disk.

   Usage:  node build-cinemagraph.js
   ========================================================= */
"use strict";

const fs = require("fs");
const path = require("path");
const root = __dirname;
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");

/* ---------- module graph (entry: cinemagraph -> three) ---------- */
const fileForId = (id) => {
  if (id === "three") return "js/vendor/three/three.module.js";
  if (id === "cg") return "js/cinemagraph.js";
  throw new Error("unknown module id: " + id);
};
const resolveSpec = (spec) => (spec === "three" ? "three" : null);

const modules = new Map();
const crawl = (id) => {
  if (modules.has(id)) return;
  const code = read(fileForId(id));
  const deps = [];
  if (id !== "three") {
    const specs = new Set(
      [...code.matchAll(/(?:from|import)\s*\(?\s*['"]([^'"]+)['"]/g)].map((m) => m[1])
    );
    for (const spec of specs) {
      const depId = resolveSpec(spec);
      if (depId) deps.push({ spec, id: depId });
    }
  }
  modules.set(id, { id, code, deps });
  for (const d of deps) crawl(d.id);
};
crawl("cg");

// topological order (dependencies first)
const order = [];
const seen = new Set();
const visit = (id) => {
  if (seen.has(id)) return;
  seen.add(id);
  for (const d of modules.get(id).deps) visit(d.id);
  order.push(id);
};
visit("cg");

/* ---------- safety guards ---------- */
const guard = (name, src) => {
  if (/<\/script/i.test(src)) throw new Error(`Cannot inline ${name}: contains </script`);
  if (/<!--/.test(src)) throw new Error(`Cannot inline ${name}: contains <!--`);
};
for (const id of order) guard(id, modules.get(id).code);

/* ---------- manifest + dom ids ---------- */
const domId = new Map();
order.forEach((id, i) => domId.set(id, "cgm" + i));
const manifest = {
  entry: domId.get("cg"),
  order: order.map((id) => ({
    domId: domId.get(id),
    deps: Object.fromEntries(modules.get(id).deps.map((d) => [d.spec, domId.get(d.id)])),
  })),
};

/* ---------- runtime bootstrap (stringified into the page) ---------- */
function bootstrapFn() {
  var reg = JSON.parse(document.getElementById("cg-manifest").textContent);
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
    console.warn("cinemagraph failed to load:", err);
    document.body.classList.add("cg-nowebgl");
    document.body.classList.add("cg-ready");
  });
}

/* ---------- assemble the page ---------- */
let html = read("courtyard.html");

// drop the import map (we resolve via Blob URLs instead)
html = html.replace(/  <!-- Three\.js[\s\S]*?<\/script>\n/, () =>
  "  <!-- Three.js + cinemagraph embedded below; loaded via Blob URLs -->\n"
);

// inline the source photo as a data URI
html = html.replace(/src="(img\/[^"]+)"/g, (_m, p) => {
  const buf = fs.readFileSync(path.join(root, p));
  const ext = path.extname(p).slice(1).toLowerCase();
  const mime = ext === "jpg" ? "jpeg" : ext;
  return `src="data:image/${mime};base64,${buf.toString("base64")}"`;
});

// replace the module <script src> with the embedded graph + bootstrap
const blocks = order
  .map((id) => `  <script type="text/plain" id="${domId.get(id)}">${modules.get(id).code}</script>`)
  .join("\n");

const loader =
`  <!-- ===== Embedded application (self-contained) ===== -->
${blocks}
  <script type="application/json" id="cg-manifest">${JSON.stringify(manifest)}</script>
  <script type="module">(${bootstrapFn.toString()})();</script>`;

html = html.replace(
  /  <script type="module" src="js\/cinemagraph\.js"><\/script>/,
  () => loader
);

/* ---------- sanity ---------- */
["type=\"importmap\"", "src=\"js/cinemagraph.js\"", "src=\"img/"].forEach((s) => {
  if (html.includes(s)) throw new Error("Build incomplete; not inlined: " + s);
});

const out = "courtyard-cinemagraph.html";
fs.writeFileSync(path.join(root, out), html, "utf8");
const kb = (n) => (n / 1024).toFixed(0) + " KB";
console.log(
  `Wrote ${out} (${kb(Buffer.byteLength(html))}) — inlined ${order.length} JS modules + photo.`
);
