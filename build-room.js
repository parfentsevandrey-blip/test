#!/usr/bin/env node
/* =========================================================
   build-room.js
   Bundles room.html + js/room*.js + the Three.js module graph
   into one self-contained file (cozy-room.html) that opens
   straight from disk — no server, no network.

   Browsers refuse to load ES modules over file://, so every
   module is embedded as inert text and the graph is rebuilt at
   runtime with Blob URLs: each module's import specifiers are
   rewritten to its dependencies' Blob URLs, created in
   dependency order.

   Usage:  node build-room.js
   ========================================================= */
"use strict";

const fs = require("fs");
const path = require("path");
const root = __dirname;
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");

const ENTRY = "js/room-app.js";
const THREE = "js/vendor/three/three.module.js";

/* ---------- module graph ---------- */
// ids are repo-relative paths; "three" is the one bare specifier we resolve
const resolveSpec = (spec, fromId) => {
  if (spec === "three") return THREE;
  if (spec.startsWith(".")) return path.posix.normalize(path.posix.join(path.posix.dirname(fromId), spec));
  return null; // any other bare specifier is left alone (none expected)
};

const modules = new Map();
const crawl = (id) => {
  if (modules.has(id)) return;
  const code = read(id);
  const deps = [];
  // three.module.js is a self-contained leaf — don't scan 1.3 MB for false matches
  if (id !== THREE) {
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
crawl(ENTRY);

// topological order (dependencies before dependents)
const order = [];
const seen = new Set(), stack = new Set();
const visit = (id) => {
  if (seen.has(id)) return;
  if (stack.has(id)) throw new Error("import cycle through " + id);
  stack.add(id);
  for (const d of modules.get(id).deps) visit(d.id);
  stack.delete(id);
  seen.add(id);
  order.push(id);
};
visit(ENTRY);

/* ---------- safety guards ---------- */
for (const id of order) {
  const src = modules.get(id).code;
  if (/<\/script/i.test(src)) throw new Error(`Cannot inline ${id}: contains </script`);
  if (/<!--/.test(src)) throw new Error(`Cannot inline ${id}: contains <!--`);
}

/* ---------- manifest + dom ids ---------- */
const domId = new Map();
order.forEach((id, i) => domId.set(id, "rm" + i));
const manifest = {
  entry: domId.get(ENTRY),
  order: order.map((id) => ({
    domId: domId.get(id),
    deps: Object.fromEntries(modules.get(id).deps.map((d) => [d.spec, domId.get(d.id)])),
  })),
};

/* ---------- runtime bootstrap (stringified into the page) ---------- */
function bootstrapFn() {
  var reg = JSON.parse(document.getElementById("room-manifest").textContent);
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
    console.error("Сцена не загрузилась:", err);
    var f = document.getElementById("fallback");
    if (f) f.classList.add("show");
    var b = document.getElementById("boot");
    if (b) b.classList.add("done");
  });
}

/* ---------- assemble the page ---------- */
let html = read("room.html");

// drop the importmap — nothing resolves bare specifiers any more
html = html.replace(/<script type="importmap">[\s\S]*?<\/script>\n/, "");

const blocks = order
  .map((id) => `<script type="text/plain" id="${domId.get(id)}">${modules.get(id).code}</script>`)
  .join("\n");

const loader =
`<!-- ===== Embedded application (self-contained, works from file://) ===== -->
${blocks}
<script type="application/json" id="room-manifest">${JSON.stringify(manifest)}</script>
<script type="module">(${bootstrapFn.toString()})();</script>`;

const tag = '<script type="module" src="./js/room-app.js"></script>';
if (!html.includes(tag)) throw new Error("entry script tag not found in room.html");
html = html.replace(tag, () => loader);

// sanity: nothing left pointing at external app files
['src="./js/', 'type="importmap"'].forEach((s) => {
  if (html.includes(s)) throw new Error("Build incomplete; not inlined: " + s);
});

const out = "cozy-room.html";
fs.writeFileSync(path.join(root, out), html, "utf8");
const kb = (n) => (n / 1024).toFixed(0) + " KB";
console.log(
  `Wrote ${out} (${kb(Buffer.byteLength(html))}) — inlined ${order.length} modules:\n  ` +
  order.map((id) => `${id} (${kb(Buffer.byteLength(modules.get(id).code))})`).join("\n  ")
);
