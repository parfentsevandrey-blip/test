/* Renders the world outside the glass on its own — no room, no rain on the
   window — from a handful of vantage points, so the city can be judged
   without the interior in the way.

   usage: node tools/skyline.js [url] [outDir]
          node tools/skyline.js '' '' 'x,y,z tx,ty,tz'      one custom shot   */
const { chromium } = require('playwright-core');
const path = require('path');
const fs = require('fs');

const URL_ = process.argv[2] || 'http://127.0.0.1:8848/room.html';
const OUT = process.argv[3] || '/tmp/skyline';
const CUSTOM = process.argv[4];
fs.mkdirSync(OUT, { recursive: true });

/* eye and target are metres in room space; the room floor is 150 m up */
const SHOTS = CUSTOM
  ? [{ name: 'custom', ...parseCustom(CUSTOM) }]
  : [
    { name: 'ahead', eye: [0, 1.6, -3], tgt: [0, 1.4, -60] },
    { name: 'down', eye: [0, 1.6, -3], tgt: [4, -40, -60] },
    { name: 'straight-down', eye: [0, 1.6, -3], tgt: [1, -60, -14] },
    { name: 'right', eye: [3, 1.6, -3], tgt: [60, 0, -30] },
    { name: 'up', eye: [0, 1.6, -3], tgt: [0, 40, -60] },
    { name: 'far', eye: [0, 1.6, -3], tgt: [8, -6, -60] },
  ];

function parseCustom(s) {
  const [a, b] = s.trim().split(/\s+/);
  return { eye: a.split(',').map(Number), tgt: b.split(',').map(Number) };
}

(async () => {
  const browser = await chromium.launch({
    executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome',
    args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader',
           '--disable-gpu-sandbox', '--no-sandbox', '--ignore-gpu-blocklist'],
  });
  const page = await browser.newPage({ viewport: { width: 1100, height: 660 }, deviceScaleFactor: 1 });
  const errors = [];
  page.on('pageerror', (e) => errors.push('PAGEERROR: ' + (e.stack || e.message)));
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });

  await page.goto(URL_, { waitUntil: 'load', timeout: 60000 });
  await page.waitForFunction(() => document.body.classList.contains('ready'), { timeout: 180000 });

  const stats = await page.evaluate(() => {
    const p = window.__room.outside.plan;
    const h = p.buildings.map((b) => b.h);
    return {
      buildings: p.buildings.length,
      boxes: p.boxes.length,
      round: p.round.length,
      streets: p.streets.length,
      arterials: p.streets.filter((s) => s.arterial).length,
      cars: window.__room.outside.traffic.geometry.instanceCount,
      tallest: Math.round(Math.max(...h)),
      medianH: Math.round(h.slice().sort((a, b) => a - b)[h.length >> 1]),
      nearest: Math.round(Math.min(...p.buildings.map((b) => Math.hypot(b.x, b.z)))),
    };
  });

  // hide the room and the glass; we want the world, not the reflection of it,
  // and no depth of field trying to focus on furniture that is not there
  await page.evaluate(() => {
    window.__room.roomScene.visible = false;
    window.__room.dof.aperture = 0;
  });

  for (const s of SHOTS) {
    await page.evaluate((sh) => {
      const r = window.__room;
      r.cam.tween = null;
      r.camera.position.set(sh.eye[0], sh.eye[1], sh.eye[2]);
      r.camera.lookAt(sh.tgt[0], sh.tgt[1], sh.tgt[2]);
      r.camera.updateMatrixWorld(true);
      r.__lockCamera = { eye: sh.eye, tgt: sh.tgt };
    }, s);
    const f0 = await page.evaluate(() => window.__room.stats().frame);
    await page.waitForFunction((f) => window.__room.stats().frame > f + 5, f0, { timeout: 240000 });
    await page.screenshot({ path: path.join(OUT, `${s.name}.png`) });
  }

  console.log(JSON.stringify({ stats, errors, out: OUT }, null, 1));
  await browser.close();
  process.exit(errors.length ? 1 : 0);
})();
