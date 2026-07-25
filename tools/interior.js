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
    { name: 'living', eye: [2.2, 1.55, 2.3], tgt: [-2.4, 0.95, -1.6] },
    { name: 'sofa', eye: [-1.9, 1.30, -1.1], tgt: [0.3, 0.55, 1.9] },
    { name: 'fire', eye: [-1.4, 1.25, 0.9], tgt: [-4.6, 1.00, -0.6] },
    { name: 'kitchen', eye: [4.9, 1.55, -0.1], tgt: [9.2, 1.00, -2.4] },
    { name: 'island', eye: [5.6, 1.45, -3.2], tgt: [9.4, 0.95, -0.2] },
    { name: 'bedroom', eye: [9.5, 1.45, 4.6], tgt: [5.2, 0.75, 1.6] },
    { name: 'hall', eye: [3.4, 1.50, 3.6], tgt: [-4.0, 1.10, 5.6] },
    { name: 'open', eye: [-3.6, 1.60, -2.6], tgt: [9.0, 1.10, -0.4] },
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
  await page.evaluate(() => {
    document.getElementById('ui').style.display = 'none';
    window.__room.dof.aperture = 0;      // judging form, not focus
  });

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
