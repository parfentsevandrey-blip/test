/* Photographs the room itself from natural viewing distances, with the UI
   hidden — for judging the shape of the furniture rather than its texture.
   tools/closeup.js gets close enough to read a weave; this gets far enough
   to see that a cushion is a box.

   usage: node tools/interior.js [outDir]
          node tools/interior.js '' 'x,y,z tx,ty,tz'          one custom shot */
const { chromium } = require('playwright-core');
const path = require('path');
const fs = require('fs');

const OUT = process.argv[2] || '/tmp/interior';
const CUSTOM = process.argv[3];
fs.mkdirSync(OUT, { recursive: true });

const SHOTS = CUSTOM
  ? [{ name: 'custom', ...parse(CUSTOM) }]
  : [
    { name: 'sofa', eye: [-1.9, 1.30, -1.1], tgt: [0.3, 0.55, 1.9] },
    { name: 'sofa-end', eye: [2.6, 1.05, 0.4], tgt: [-0.4, 0.55, 2.0] },
    { name: 'armchair', eye: [-1.1, 1.15, -0.4], tgt: [-2.6, 0.55, 1.3] },
    { name: 'table', eye: [0.7, 1.00, 1.5], tgt: [-0.6, 0.40, 0.3] },
    { name: 'fire', eye: [-1.4, 1.25, 0.9], tgt: [-4.6, 1.00, -0.6] },
    { name: 'shelf', eye: [1.4, 1.35, 1.0], tgt: [4.7, 1.20, 2.0] },
    { name: 'wide', eye: [3.9, 1.65, 3.4], tgt: [-1.6, 0.95, -0.8] },
  ];

function parse(s) {
  const [a, b] = s.trim().split(/\s+/);
  return { eye: a.split(',').map(Number), tgt: b.split(',').map(Number) };
}

(async () => {
  const browser = await chromium.launch({
    executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome',
    args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader',
           '--disable-gpu-sandbox', '--no-sandbox', '--ignore-gpu-blocklist'],
  });
  const page = await browser.newPage({ viewport: { width: 1000, height: 680 }, deviceScaleFactor: 1 });
  const errors = [];
  page.on('pageerror', (e) => errors.push('PAGEERROR: ' + (e.stack || e.message)));
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });

  await page.goto('http://127.0.0.1:8848/room.html', { waitUntil: 'load', timeout: 60000 });
  await page.waitForFunction(() => document.body.classList.contains('ready'), { timeout: 240000 });
  await page.evaluate(() => { document.getElementById('ui').style.display = 'none'; });

  const counts = await page.evaluate(() => {
    let meshes = 0, tris = 0;
    window.__room.roomScene.traverse((o) => {
      if (!o.isMesh || !o.geometry) return;
      meshes++;
      const g = o.geometry;
      tris += (g.index ? g.index.count : g.attributes.position.count) / 3;
    });
    return { meshes, tris: Math.round(tris) };
  });

  for (const s of SHOTS) {
    await page.evaluate((sh) => { window.__room.__lockCamera = { eye: sh.eye, tgt: sh.tgt }; }, s);
    const f0 = await page.evaluate(() => window.__room.stats().frame);
    await page.waitForFunction((f) => window.__room.stats().frame > f + 5, f0, { timeout: 240000 });
    await page.screenshot({ path: path.join(OUT, `${s.name}.png`) });
  }

  console.log(JSON.stringify({ counts, errors, out: OUT }, null, 1));
  await browser.close();
  process.exit(errors.length ? 1 : 0);
})();
