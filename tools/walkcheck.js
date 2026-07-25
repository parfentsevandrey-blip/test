/* Drive the walk mode like a player: enter, walk into things, verify collision
 * actually holds and that WASD moves the camera at a human speed. */
const { chromium } = require('playwright-core');
(async () => {
  const b = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome',
    args: ['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--no-sandbox'] });
  const p = await b.newPage({ viewport: { width: 900, height: 600 } });
  const errs = []; p.on('pageerror', e => errs.push((e.stack || String(e)).split('\n').slice(0,4).join(' | ')));
  p.on('console', m => { if (m.type()==='error' && !/favicon/.test(m.text())) errs.push(m.text()); });
  await p.goto('http://127.0.0.1:8848/room.html', { waitUntil: 'load' });
  await p.waitForFunction(() => document.body.classList.contains('ready'), { timeout: 240000 });

  const frames = (n) => p.evaluate((k) => new Promise(res => {
    let i = 0; const tick = () => (++i >= k ? res(window.__room.walker.pos.toArray().map(v => +v.toFixed(2)))
                                            : requestAnimationFrame(tick));
    requestAnimationFrame(tick);
  }), n);
  const state = () => p.evaluate(() => {
    const w = window.__room.walker;
    return { active: w.active, pos: w.pos.toArray().map(v=>+v.toFixed(2)), yaw: +w.yaw.toFixed(2), eye: +w.eye.toFixed(2) };
  });
  const results = [];
  const ok = (n, c, d='') => results.push(`${c ? 'ok  ' : 'FAIL'} ${n}${c ? '' : '  ' + d}`);

  await p.evaluate(() => window.__room.setWalking(true));
  let s = await state();
  ok('walk mode starts', s.active);

  // hold W for a while and see how far we get
  await p.evaluate(() => { window.__room.walker.keys.add('KeyW'); });
  const before = (await state()).pos;
  await frames(40);
  await p.evaluate(() => { window.__room.walker.keys.clear(); });
  const after = (await state()).pos;
  const moved = Math.hypot(after[0]-before[0], after[2]-before[2]);
  ok('W moves the camera', moved > 0.15, `moved ${moved.toFixed(2)} m`);

  // walk hard into the left wall and confirm we do not pass through it
  await p.evaluate(() => { const w = window.__room.walker;
    w.pos.set(-4.0, w.eye, 2.0); w.yaw = Math.PI / 2; w.keys.add('KeyW'); });
  await frames(80);
  await p.evaluate(() => window.__room.walker.keys.clear());
  const wall = (await state()).pos;
  ok('wall stops you', wall[0] > -5 + 0.2, `x=${wall[0]}`);

  // and straight into the sofa
  await p.evaluate(() => { const w = window.__room.walker;
    w.pos.set(0.15, w.eye, 3.4); w.yaw = 0; w.keys.add('KeyW'); });
  await frames(80);
  await p.evaluate(() => window.__room.walker.keys.clear());
  const sofa = (await state()).pos;
  ok('furniture stops you', sofa[2] > 2.5, `z=${sofa[2]} (sofa front is 2.50)`);

  // crouch
  await p.evaluate(() => window.__room.walker.keys.add('ControlLeft'));
  await frames(30);
  const crouched = (await state()).eye;
  await p.evaluate(() => window.__room.walker.keys.clear());
  ok('crouch lowers the eye', crouched < 1.35, `eye=${crouched}`);

  await p.evaluate(() => window.__room.setWalking(false));
  ok('exits cleanly', !(await p.evaluate(() => window.__room.walker.active)));

  await p.evaluate(() => window.__room.setWalking(true));
  await frames(5);
  await p.screenshot({ path: '/tmp/walk-view.png' });
  await b.close();
  console.log(results.join('\n'));
  console.log('errors:', errs.length ? errs.slice(0,3) : 'none');
  process.exit(results.some(r => r.startsWith('FAIL')) || errs.length ? 1 : 0);
})();
