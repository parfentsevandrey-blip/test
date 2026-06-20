#!/usr/bin/env node
/* =========================================================
   watch-cinemagraph.js
   Watches the cinemagraph source files and regenerates the
   self-contained courtyard-cinemagraph.html on every change
   (debounced). No dependencies — just Node's fs.watch.

   Usage:  node watch-cinemagraph.js
   ========================================================= */
"use strict";

const { spawn } = require("child_process");
const fs = require("fs");
const path = require("path");

const root = __dirname;
const SOURCES = ["js/cinemagraph.js", "courtyard.html", "build-cinemagraph.js"];
const bases = new Set(SOURCES.map((p) => path.basename(p)));
const dirs = new Set(SOURCES.map((p) => path.dirname(path.join(root, p))));

let timer = null;
let building = false;
let pending = false;

function build(reason) {
  if (building) { pending = true; return; }
  building = true;
  const stamp = new Date().toLocaleTimeString();
  const child = spawn("node", ["build-cinemagraph.js"], { cwd: root });
  let out = "";
  child.stdout.on("data", (d) => (out += d));
  child.stderr.on("data", (d) => (out += d));
  child.on("close", (code) => {
    building = false;
    process.stdout.write(`[${stamp}] ${reason} → ${out.trim() || "exit " + code}\n`);
    if (pending) { pending = false; build("queued change"); }
  });
}

function schedule(reason) {
  clearTimeout(timer);
  timer = setTimeout(() => build(reason), 200);
}

for (const dir of dirs) {
  fs.watch(dir, (_evt, fname) => {
    if (fname && bases.has(fname)) schedule(`${fname} changed`);
  });
}

process.stdout.write(
  `Watching ${SOURCES.join(", ")}\n` +
    `→ rebuilds courtyard-cinemagraph.html on every change. Ctrl-C to stop.\n`
);
build("initial build");
