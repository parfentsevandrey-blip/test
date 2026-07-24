/* Verify cozy-room.html works straight off disk (file://) */
const { chromium } = require('playwright-core');
const path = require('path');
(async () => {
  const b = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome',
    args: ['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--no-sandbox'] });
  const p = await b.newPage({ viewport: { width: 900, height: 560 } });
  const errs = [];
  p.on('pageerror', e => errs.push('PAGEERROR: ' + (e.stack || e.message)));
  p.on('console', m => { if (m.type() === 'error') errs.push('C: ' + m.text()); });
  await p.goto('file://' + path.resolve(__dirname, '..', 'cozy-room.html'), { waitUntil: 'load' });
  let ok = true;
  try { await p.waitForFunction(() => document.body.classList.contains('ready'), { timeout: 180000 }); }
  catch (e) { ok = false; errs.push('TIMEOUT waiting for ready'); }
  const f0 = await p.evaluate(() => window.__room ? window.__room.stats().frame : -1);
  if (f0 >= 0) await p.waitForFunction((f) => window.__room.stats().frame > f + 5, f0, { timeout: 180000 });
  await p.screenshot({ path: '/tmp/filecheck.png' });
  console.log(JSON.stringify({ ok, frames: await p.evaluate(() => window.__room && window.__room.stats()), errs }, null, 1));
  await b.close();
  process.exit(ok && !errs.length ? 0 : 1);
})();
