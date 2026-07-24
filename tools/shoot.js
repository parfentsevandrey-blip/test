/* Headless smoke test + screenshots for room.html
   usage: node tools/shoot.js [url] [outDir]                             */
const { chromium } = require('playwright-core');
const path = require('path');
const fs = require('fs');

const URL_ = process.argv[2] || 'http://127.0.0.1:8848/room.html';
const OUT = process.argv[3] || '/tmp/claude-0/-home-user-test/1b2f2efc-b657-5915-96a3-f9c8ed6bcd83/scratchpad/shots';
fs.mkdirSync(OUT, { recursive: true });

(async () => {
  const browser = await chromium.launch({
    executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome',
    args: [
      '--use-gl=angle', '--use-angle=swiftshader',
      '--enable-unsafe-swiftshader', '--disable-gpu-sandbox',
      '--no-sandbox', '--ignore-gpu-blocklist',
    ],
  });
  const page = await browser.newPage({ viewport: { width: 1180, height: 700 }, deviceScaleFactor: 1 });

  const errors = [], warns = [], logs = [];
  page.on('console', (m) => {
    const t = m.type(), x = m.text();
    if (t === 'error') errors.push(x);
    else if (t === 'warning') warns.push(x);
    else logs.push(x);
  });
  page.on('pageerror', (e) => errors.push('PAGEERROR: ' + (e.stack || e.message)));

  await page.goto(URL_, { waitUntil: 'load', timeout: 60000 });
  const FORCEQ = process.env.FORCE_Q;

  // wait for the app to signal ready (body.ready) — SwiftShader is slow
  try {
    await page.waitForFunction(() => document.body.classList.contains('ready'), { timeout: 120000 });
  } catch (e) {
    errors.push('TIMEOUT waiting for body.ready');
  }

  if (FORCEQ != null) {
    await page.evaluate((q) => {
      window.__room.CFG.quality = -1;         // force applyQuality to actually re-apply
      document.querySelector(`#segQual button[data-q="${q}"]`).click();
    }, FORCEQ);
  }
  const views = ['gostinaya', 'kamin', 'okno', 'divan'];
  for (let i = 0; i < views.length; i++) {
    await page.evaluate((n) => window.__room.snapView(n), i);
    // software rendering is ~1 fps — wait for real frames, not wall-clock
    const f0 = await page.evaluate(() => window.__room.stats().frame);
    await page.waitForFunction((f) => window.__room.stats().frame > f + 6, f0, { timeout: 180000 });
    await page.screenshot({ path: path.join(OUT, `${i}-${views[i]}.png`) });
    const c = await page.evaluate(() => {
      const r = window.__room; if (!r) return null;
      const p = r.camera.position, t = r.cam.target;
      return { view: r.cam.gTheta.toFixed(2), pos: [+p.x.toFixed(2), +p.y.toFixed(2), +p.z.toFixed(2)],
               tgt: [+t.x.toFixed(2), +t.y.toFixed(2), +t.z.toFixed(2)], q: r.CFG.quality };
    });
    logs.push(`view ${i}: ${JSON.stringify(c)}`);
  }

  const info = await page.evaluate(() => ({
    ready: document.body.classList.contains('ready'),
    canvas: (() => { const c = document.getElementById('scene'); return c ? [c.width, c.height] : null; })(),
    fallback: document.getElementById('fallback')?.classList.contains('show'),
  }));

  console.log(JSON.stringify({ info, errors: errors.slice(0, 30), warns: warns.slice(0, 15), cams: logs.filter(l => l.startsWith('view ')) }, null, 2));
  await browser.close();
  process.exit(errors.length ? 1 : 0);
})();
