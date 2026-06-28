#!/usr/bin/env node
/* =========================================================
   build-text-video.js
   Emits text-video.html: a self-contained page that plays
   "video" with NO <video> and NO visible <img> — every frame
   is rebuilt as colored text characters in a <pre>. The source
   frames are real Кутузовский 12 photos, animated (Ken-Burns +
   crossfade) so it reads as footage while remaining pure text.

   Images are inlined as data URIs so the file is double-click
   runnable (and so the sampling canvas isn't tainted on file://).

   Usage:  node build-text-video.js
   ========================================================= */
"use strict";
const fs = require("fs");
const path = require("path");
const root = __dirname;

const frames = ["img/k12-aerial-sunset.jpg", "img/k12-facade-detail.jpg", "img/k12-courtyard.jpg"];
const dataUris = frames.map((p) => {
  const b = fs.readFileSync(path.join(root, p));
  return `data:image/jpeg;base64,${b.toString("base64")}`;
});

const html = `<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<title>Видео из текста · Кутузовский 12</title>
<style>
  :root { --gold:#c9a35e; --ink:#eef0f4; }
  * { margin:0; padding:0; box-sizing:border-box; }
  html,body { height:100%; background:#05070b; color:var(--ink);
    font-family:"Manrope",system-ui,sans-serif; overflow:hidden; }
  .stage { position:fixed; inset:0; display:grid; place-items:center; }
  /* the "video" is literally this block of characters */
  #screen {
    font-family:"SFMono-Regular",Menlo,Consolas,"Liberation Mono",monospace;
    font-size:8px; line-height:8px; letter-spacing:0; white-space:pre;
    background:#04060a;
    text-shadow:0 0 1px rgba(0,0,0,.4);
    transform:translateZ(0);
    transition:font-size .5s ease, line-height .5s ease, letter-spacing .5s ease;
    user-select:text;
    box-shadow:0 30px 120px -30px rgba(0,0,0,.9);
    outline:1px solid rgba(201,163,94,.18);
  }
  body.reveal #screen { font-size:13px; line-height:14px; letter-spacing:2px; }
  .vignette { position:fixed; inset:0; pointer-events:none;
    background:radial-gradient(120% 90% at 50% 50%, transparent 55%, rgba(4,6,11,.85)); }
  .scan { position:fixed; inset:0; pointer-events:none; opacity:.5;
    background:repeating-linear-gradient(0deg, rgba(0,0,0,0) 0 2px, rgba(0,0,0,.22) 2px 3px); }
  .hud { position:fixed; left:24px; top:22px; z-index:5; max-width:30rem; }
  .hud .eyebrow { font-size:.62rem; letter-spacing:.34em; text-transform:uppercase; color:var(--gold); }
  .hud h1 { font-family:"Cormorant Garamond",Georgia,serif; font-weight:500;
    font-size:clamp(1.6rem,4vw,2.6rem); line-height:1.05; margin:.5rem 0 .6rem; }
  .hud p { font-size:.85rem; color:#9aa1b1; font-weight:300; max-width:26rem; }
  .hud code { color:var(--gold); }
  .bar { position:fixed; left:24px; bottom:22px; z-index:5; display:flex; gap:.6rem; align-items:center; }
  button { font:inherit; font-size:.72rem; letter-spacing:.12em; text-transform:uppercase;
    color:var(--ink); background:rgba(255,255,255,.04); border:1px solid rgba(201,163,94,.3);
    padding:.6em 1.1em; border-radius:2px; cursor:pointer; transition:.3s; }
  button:hover { border-color:var(--gold); color:var(--gold); }
  .tag { position:fixed; right:24px; top:22px; z-index:5; display:flex; align-items:center; gap:.5rem;
    font-size:.62rem; letter-spacing:.2em; text-transform:uppercase; color:#9aa1b1; }
  .dot { width:8px; height:8px; border-radius:50%; background:#e0483a; box-shadow:0 0 8px #e0483a;
    animation:blink 1.4s steps(1) infinite; }
  @keyframes blink { 50% { opacity:.2; } }
  @media (max-width:640px){ #screen{ font-size:5px; line-height:5px; } .hud{max-width:16rem} }
</style>
</head>
<body>
  <div class="stage"><pre id="screen" aria-label="Видео, собранное из текстовых символов"></pre></div>
  <div class="vignette"></div>
  <div class="scan"></div>

  <div class="hud">
    <div class="eyebrow">Кутузовский 12 · эксперимент</div>
    <h1>Это не видео.<br>Это текст.</h1>
    <p>На экране нет ни <code>&lt;video&gt;</code>, ни <code>&lt;img&gt;</code> — только
      цветные символы, которые JavaScript перерисовывает каждый кадр. Выделите «кадр» мышью — это
      настоящий текст.</p>
  </div>

  <div class="tag"><span class="dot"></span> live · <span id="fps">–</span> fps</div>

  <div class="bar">
    <button id="reveal">Показать, что это текст</button>
    <button id="play">Пауза</button>
  </div>

<script>
const SRC = ${JSON.stringify(dataUris)};
const RAMP = " .'\`:,-~+=*coaehx%#WM@";  // luminance -> glyph (dark to light)
const COLS = window.innerWidth < 640 ? 110 : 168;

const screen = document.getElementById('screen');
const fpsEl = document.getElementById('fps');
const sample = document.createElement('canvas');
const sctx = sample.getContext('2d', { willReadFrequently: true });

let ROWS = 60, imgs = [], ready = false;

Promise.all(SRC.map(s => new Promise((res) => {
  const im = new Image(); im.onload = () => res(im); im.onerror = () => res(null); im.src = s;
}))).then(list => {
  imgs = list.filter(Boolean);
  if (!imgs.length) { screen.textContent = '— нет источника —'; return; }
  const a = imgs[0].naturalHeight / imgs[0].naturalWidth;
  ROWS = Math.max(20, Math.round(COLS * a * 0.52));   // monospace cell ~0.52 aspect
  sample.width = COLS; sample.height = ROWS;
  ready = true;
  requestAnimationFrame(loop);
});

// Ken-Burns crop for image i at progress p (0..1)
function drawKenBurns(img, p, alpha) {
  const z = 1.12 - 0.12 * p;                  // slow zoom out
  const sw = img.width / z, sh = img.height / z;
  const sx = (img.width - sw) * (0.3 + 0.4 * p);
  const sy = (img.height - sh) * (0.6 - 0.3 * p);
  sctx.globalAlpha = alpha;
  sctx.drawImage(img, sx, sy, sw, sh, 0, 0, COLS, ROWS);
}

let playing = true, t0 = performance.now(), frameAcc = 0, frames = 0, lastFps = t0;
const PERIOD = 6200;        // ms per shot
const FADE = 1200;          // crossfade ms

function loop(now) {
  if (!ready) return;
  if (playing) {
    const cycle = imgs.length * PERIOD;
    const tt = (now - t0) % cycle;
    const idx = Math.floor(tt / PERIOD);
    const local = tt - idx * PERIOD;
    const p = local / PERIOD;

    sctx.globalAlpha = 1; sctx.clearRect(0, 0, COLS, ROWS);
    drawKenBurns(imgs[idx], p, 1);
    if (local > PERIOD - FADE) {                 // crossfade into the next shot
      const nxt = (idx + 1) % imgs.length;
      drawKenBurns(imgs[nxt], 0, (local - (PERIOD - FADE)) / FADE);
    }

    const data = sctx.getImageData(0, 0, COLS, ROWS).data;
    let out = '', run = '', cr = -1, cg = -1, cb = -1;
    for (let y = 0; y < ROWS; y++) {
      for (let x = 0; x < COLS; x++) {
        const o = (y * COLS + x) * 4;
        let r = data[o], g = data[o + 1], b = data[o + 2];
        const lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
        // gamma-lift the luminance so dark footage stays readable as glyphs
        const cl = Math.pow(lum, 0.78);
        const ch = RAMP[Math.min(RAMP.length - 1, (cl * RAMP.length) | 0)];
        // saturation + brightness/gamma lift for a richer picture
        const sat = 0.42, add = 10;
        r = (255 * Math.pow(Math.min(1, (r + (r - lum * 255) * sat + add) / 255), 0.8)) | 0;
        g = (255 * Math.pow(Math.min(1, (g + (g - lum * 255) * sat + add) / 255), 0.8)) | 0;
        b = (255 * Math.pow(Math.min(1, (b + (b - lum * 255) * sat + add) / 255), 0.8)) | 0;
        const qr = r & 0xF0, qg = g & 0xF0, qb = b & 0xF0;   // quantize to group runs
        if (qr !== cr || qg !== cg || qb !== cb) {
          if (run) out += '<span style="color:rgb(' + cr + ',' + cg + ',' + cb + ')">' + run + '</span>';
          run = ''; cr = qr; cg = qg; cb = qb;
        }
        run += ch === '<' ? '&lt;' : ch;
      }
      run += '\\n';
    }
    if (run) out += '<span style="color:rgb(' + cr + ',' + cg + ',' + cb + ')">' + run + '</span>';
    screen.innerHTML = out;

    frames++;
    if (now - lastFps > 500) { fpsEl.textContent = Math.round(frames * 1000 / (now - lastFps)); frames = 0; lastFps = now; }
  }
  requestAnimationFrame(loop);
}

document.getElementById('reveal').onclick = (e) => {
  document.body.classList.toggle('reveal');
  e.target.textContent = document.body.classList.contains('reveal') ? 'Свернуть' : 'Показать, что это текст';
};
document.getElementById('play').onclick = (e) => { playing = !playing; e.target.textContent = playing ? 'Пауза' : 'Играть'; };
</script>
</body>
</html>
`;

fs.writeFileSync(path.join(root, "text-video.html"), html, "utf8");
console.log(`Wrote text-video.html (${(Buffer.byteLength(html) / 1024).toFixed(0)} KB) — ${dataUris.length} source frames inlined.`);
