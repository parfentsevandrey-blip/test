/* Render close-ups of real surfaces inside the scene, at the distance and
 * lighting a viewer actually sees them at.  node tools/closeup.js [outDir] */
const { chromium } = require('playwright-core');
const path = require('path'); const fs = require('fs');
const OUT = process.argv[2] || '/tmp/closeup';
fs.mkdirSync(OUT, { recursive: true });

const SHOTS = [
  { n: 'floor',    t: [0.2, 0.02, 0.2],   d: 1.5, th: 0.6,  ph: 2.25 },
  { n: 'sofa',     t: [0.15, 0.55, 1.5],  d: 1.6, th: 0.25, ph: 1.62 },
  { n: 'armchair', t: [-2.55, 0.5, 1.30], d: 1.5, th: 1.1,  ph: 1.62 },
  { n: 'rug',      t: [-0.5, 0.03, 0.35], d: 1.2, th: 0.5,  ph: 2.15 },
  { n: 'stone',    t: [-4.7, 1.7, -0.6],  d: 1.4, th: 1.45, ph: 1.60 },
  { n: 'wall',     t: [4.9, 1.6, 2.5],    d: 1.5, th: -1.4, ph: 1.58 },
  { n: 'table',    t: [-0.55, 0.42, 0.3], d: 1.1, th: 0.4,  ph: 2.05 },
  { n: 'shelf',    t: [4.7, 1.3, 1.9],    d: 1.5, th: -1.3, ph: 1.60 },
];

(async () => {
  const b = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome',
    args: ['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--no-sandbox'] });
  const p = await b.newPage({ viewport: { width: 900, height: 620 } });
  const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.goto('http://127.0.0.1:8848/room.html', { waitUntil: 'load' });
  await p.waitForFunction(() => document.body.classList.contains('ready'), { timeout: 240000 });
  await p.evaluate(() => { document.getElementById('ui').style.display = 'none'; });

  for (const s of SHOTS) {
    await p.evaluate((v) => {
      const c = window.__room.cam, T = window.__room.THREE;
      c.tween = null;
      c.gTarget.set(v.t[0], v.t[1], v.t[2]); c.target.copy(c.gTarget);
      c.gDist = c.dist = v.d; c.gTheta = c.theta = v.th; c.gPhi = c.phi = v.ph;
    }, s);
    const f0 = await p.evaluate(() => window.__room.stats().frame);
    await p.waitForFunction((f) => window.__room.stats().frame > f + 4, f0, { timeout: 180000 });
    await p.screenshot({ path: path.join(OUT, s.n + '.png') });
  }
  console.log(JSON.stringify({ errs, out: OUT }));
  await b.close();
})();
