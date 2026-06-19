#!/usr/bin/env node
/* =========================================================
   build-standalone.js
   Bundles the ZENITH site into a single, self-contained HTML
   file (zenith-residence.html) with CSS, JS and the full
   Three.js library inlined.

   The 3D code uses ES modules, so Three.js + the scene module
   are embedded as raw text and loaded at runtime via Blob URLs.
   That approach works even when the file is opened directly
   from disk (file://), where external module scripts would be
   blocked by the browser's module CORS rules.

   Usage:  node build-standalone.js
   ========================================================= */
"use strict";

const fs = require("fs");
const path = require("path");

const root = __dirname;
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");

const css = read("css/styles.css");
const mainJs = read("js/main.js");
const sceneJs = read("js/scene.js");
const threeJs = read("js/vendor/three/three.module.js");
const roomJs = read("js/vendor/three/addons/environments/RoomEnvironment.js");

// Guard: a literal </script> inside an embedded block would close the host
// <script> early. None expected (checked at build time), but fail loudly.
const guard = (name, src) => {
  if (/<\/script/i.test(src)) {
    throw new Error(`Refusing to inline ${name}: contains a </script sequence.`);
  }
};
[["main.js", mainJs], ["scene.js", sceneJs], ["three.module.js", threeJs], ["RoomEnvironment.js", roomJs]].forEach(
  ([n, s]) => guard(n, s)
);
if (/<\/style/i.test(css)) throw new Error("styles.css contains a </style sequence.");

let html = read("index.html");

// 1) Google Fonts: keep as an online enhancement, but make it non-blocking and
//    harmless offline (the CSS already declares system serif/sans fallbacks).
//    Nothing to change — the <link> degrades gracefully when offline.

// NOTE: replacements use a *function* so that `$` sequences in the inlined
// source (e.g. Three.js contains the string '$', and `$'`/`$&`/`$1` are
// special replacement patterns) are inserted literally rather than expanded.

// 2) Inline the stylesheet.
html = html.replace(
  '  <link rel="stylesheet" href="css/styles.css" />',
  () => `  <style>\n${css}\n  </style>`
);

// 3) Drop the import map — the standalone build resolves Three.js via Blob URLs.
html = html.replace(
  /  <!-- Three\.js r160[\s\S]*?<\/script>\n/,
  () => "  <!-- Three.js + scene are embedded below and loaded via Blob URLs -->\n"
);

// 4) Replace the two bottom script tags with the embedded, self-contained
//    loader. scene.js is kept byte-for-byte; only its dynamic-import
//    specifiers are rewritten to the runtime Blob URLs.
const loader = `  <!-- ===== Embedded application (self-contained) ===== -->
  <script type="text/plain" id="zen-three">${threeJs}</script>
  <script type="text/plain" id="zen-room">${roomJs}</script>
  <script type="text/plain" id="zen-scene">${sceneJs}</script>

  <script type="module">
    // Build Blob URLs for the embedded modules so they import cleanly,
    // including from file:// where external module scripts are blocked.
    const mk = (txt) => URL.createObjectURL(new Blob([txt], { type: "text/javascript" }));
    const txt = (id) => document.getElementById(id).textContent;

    const threeURL = mk(txt("zen-three"));
    const roomURL = mk(txt("zen-room").replace(/from\\s*["']three["']/g, \`from "\${threeURL}"\`));
    const sceneSrc = txt("zen-scene")
      .replace(/await import\\(\\s*["']three["']\\s*\\)/, \`await import("\${threeURL}")\`)
      .replace(/await import\\(\\s*["']three\\/addons\\/environments\\/RoomEnvironment\\.js["']\\s*\\)/, \`await import("\${roomURL}")\`);
    await import(mk(sceneSrc));
  </script>

  <script>\n${mainJs}\n  </script>`;

html = html.replace(
  /  <script type="module" src="js\/scene\.js"><\/script>\n  <script src="js\/main\.js" defer><\/script>/,
  () => loader
);

// Sanity: ensure all placeholders were actually replaced.
const mustBeGone = ['href="css/styles.css"', 'src="js/scene.js"', 'src="js/main.js"', 'type="importmap"'];
const leftovers = mustBeGone.filter((s) => html.includes(s));
if (leftovers.length) {
  throw new Error("Build incomplete; these references were not inlined:\n  " + leftovers.join("\n  "));
}

const out = "zenith-residence.html";
fs.writeFileSync(path.join(root, out), html, "utf8");

const kb = (n) => (n / 1024).toFixed(0) + " KB";
console.log(`Wrote ${out} (${kb(Buffer.byteLength(html))})`);
console.log(`  inlined: styles.css, main.js, scene.js, three.module.js (${kb(Buffer.byteLength(threeJs))}), RoomEnvironment.js`);
