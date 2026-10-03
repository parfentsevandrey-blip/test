#!/usr/bin/env node
// Builds a LIVE DEMO of the web interface that needs no server: the real UI files plus
// web-dev/mock-server.mjs running inside the page (see web-dev/demo/*).
//
//   node web-dev/make-demo.mjs [--out web-dev/demo-dist]
//   npx serve web-dev/demo-dist        # or any static file server; ?scenario=onboarding|full|empty
//
// The first run installs esbuild and a few small browser polyfills into
// web-dev/.demo-cache (git-ignored). Everything else is plain files.
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath, pathToFileURL } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const UI = path.resolve(here, "../internal/web/ui");
const CACHE = path.join(here, ".demo-cache");
const args = process.argv.slice(2);
const OUT = path.resolve(args.includes("--out") ? args[args.indexOf("--out") + 1] : path.join(here, "demo-dist"));

if (!fs.existsSync(path.join(CACHE, "node_modules", "esbuild"))) {
  console.log("installing build dependencies into web-dev/.demo-cache …");
  fs.mkdirSync(CACHE, { recursive: true });
  fs.writeFileSync(path.join(CACHE, "package.json"), JSON.stringify({ name: "themesh-demo-build", private: true, version: "0.0.0" }));
  execFileSync("npm", ["i", "--no-audit", "--no-fund", "--silent", "esbuild", "@noble/hashes", "buffer", "path-browserify", "fflate"], { cwd: CACHE, stdio: "inherit" });
}
const esbuild = await import(pathToFileURL(path.join(CACHE, "node_modules", "esbuild", "lib", "main.js")).href);

fs.rmSync(OUT, { recursive: true, force: true });
fs.mkdirSync(path.join(OUT, "demo"), { recursive: true });

// 1. the mock server for the browser
const shim = (n) => path.join(here, "demo", "shims", n);
const sample = fs.readFileSync(path.join(here, "fixtures", "sample.webm")).toString("base64");
const alias = {};
for (const [mod, file] of Object.entries({ http: "http.js", fs: "fs.js", path: "path.js", url: "url.js", zlib: "zlib.js", crypto: "crypto.js", stream: "stream.js" })) {
  alias["node:" + mod] = shim(file);
  alias[mod] = shim(file);
}
await esbuild.build({
  entryPoints: [path.join(here, "demo", "entry.js")],
  bundle: true,
  format: "iife",
  platform: "browser",
  target: "es2020",
  minify: true,
  legalComments: "none",
  outfile: path.join(OUT, "demo", "mock.js"),
  alias,
  nodePaths: [path.join(CACHE, "node_modules")],
  inject: [shim("globals.js")],
  define: { "import.meta.url": JSON.stringify("file:///web-dev/mock-server.mjs") },
  plugins: [{
    name: "sample-video",
    setup(b) {
      b.onResolve({ filter: /sample-webm\.js$/ }, () => ({ path: "sample-webm", namespace: "gen" }));
      b.onLoad({ filter: /.*/, namespace: "gen" }, () => ({ contents: `export default ${JSON.stringify(sample)};`, loader: "js" }));
    },
  }],
  logLevel: "warning",
});

// 2. the interface itself, unchanged (no service worker or manifest: a demo is not installed)
const skip = new Set(["sw.js", "manifest.webmanifest"]);
(function copy(from, to) {
  fs.mkdirSync(to, { recursive: true });
  for (const e of fs.readdirSync(from, { withFileTypes: true })) {
    if (skip.has(e.name)) continue;
    const f = path.join(from, e.name), t = path.join(to, e.name);
    if (e.isDirectory()) copy(f, t);
    else if (e.name !== "index.html") fs.copyFileSync(f, t);
  }
})(UI, OUT);

// 3. index.html as a FRAGMENT: the artifact host wraps the page in its own document skeleton,
//    so the file carries no <html>/<head>/<body>; theme and language are set by js/boot.js.
const src = fs.readFileSync(path.join(UI, "index.html"), "utf8");
const body = /<body>([\s\S]*)<\/body>/.exec(src);
if (!body) throw new Error("index.html changed: no <body>");
const sheets = [...src.matchAll(/<link rel="stylesheet"[^>]*>/g)].map((m) => "  " + m[0]);
const html = `<title>The Mesh</title>
${sheets.join("\n")}
<script src="demo/mock.js"></script>
<script src="js/boot.js"></script>
<script type="module" src="js/app.js"></script>
${body[1].replace(/<noscript>[\s\S]*?<\/noscript>/, "").trim()}
`;
fs.writeFileSync(path.join(OUT, "index.html"), html);

const files = [];
(function walk(d) { for (const e of fs.readdirSync(d, { withFileTypes: true })) { const f = path.join(d, e.name); e.isDirectory() ? walk(f) : files.push(f); } })(OUT);
const bytes = files.reduce((n, f) => n + fs.statSync(f).size, 0);
console.log(`demo built: ${files.length} files, ${(bytes / 1024).toFixed(0)} KB → ${path.relative(process.cwd(), OUT) || OUT}`);
