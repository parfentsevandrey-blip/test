/* Audit every control in the scene UI.
 *
 *   node tools/uicheck.js [url]
 *
 * For each control: record state, actuate it the way a user would (a real
 * click / input event), let a few frames pass, then assert the state actually
 * changed. Also records main-thread long tasks caused by each action, since a
 * control that "works" but freezes the page for 400 ms is still broken.
 */
const { chromium } = require('playwright-core');

const URL_ = process.argv[2] || 'http://127.0.0.1:8848/room.html';

(async () => {
  const browser = await chromium.launch({
    executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome',
    args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--no-sandbox'],
  });
  const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });
  const errs = [];
  page.on('pageerror', (e) => errs.push('PAGEERROR: ' + (e.stack || e.message)));
  page.on('console', (m) => { if (m.type() === 'error' && !/favicon/.test(m.text())) errs.push('CONSOLE: ' + m.text()); });

  await page.goto(URL_, { waitUntil: 'load', timeout: 60000 });
  await page.waitForFunction(() => document.body.classList.contains('ready'), { timeout: 240000 });

  await page.evaluate(() => {
    window.__lt = [];
    try {
      new PerformanceObserver((l) => { for (const e of l.getEntries()) window.__lt.push(Math.round(e.duration)); })
        .observe({ entryTypes: ['longtask'] });
    } catch (e) { /* longtask unsupported */ }
  });

  const frames = () => page.evaluate(() => window.__room.stats().frame);
  const waitFrames = async (n = 4) => {
    const f0 = await frames();
    await page.waitForFunction((f) => window.__room.stats().frame > f, f0, { timeout: 120000 });
    for (let i = 1; i < n; i++) {
      const f = await frames();
      await page.waitForFunction((x) => window.__room.stats().frame > x, f, { timeout: 120000 });
    }
  };
  const takeLT = () => page.evaluate(() => { const a = window.__lt.slice(); window.__lt.length = 0; return a; });
  const snap = () => page.evaluate(() => {
    const r = window.__room;
    return {
      q: r.CFG.quality, lamps: r.CFG.lamps, drift: r.CFG.drift,
      fire: +r.CFG.fire.toFixed(3), rain: +r.CFG.rain.toFixed(3), warm: +r.CFG.warm.toFixed(3),
      uFire: +r.U.fire.value.toFixed(3), uRain: +r.U.rain.value.toFixed(3),
      uWarm: +r.post.composite.uniforms.uWarm.value.toFixed(3),
      lampI: +r.lights.lamp.intensity.toFixed(2),
      qPressed: [...document.querySelectorAll('#segQual button')].map((b) => b.getAttribute('aria-pressed')),
      driftPressed: [...document.querySelectorAll('#segDrift button')].map((b) => b.getAttribute('aria-pressed')),
      lampsPressed: document.getElementById('btnLamps').getAttribute('aria-pressed'),
      sheetOpen: document.getElementById('sheet').classList.contains('open'),
      res: +r.CFG.res.toFixed(2),
      commitMs: r.stats().commitMs,
      reflFloor: +r.reflectiveFloor.uniforms.uReflAmt.value.toFixed(2),
      reflWin: +r.glassMaterials[0].uniforms.uReflAmt.value.toFixed(2),
    };
  });

  const results = [];
  const check = async (name, action, expect) => {
    await takeLT();
    const before = await snap();
    await action();
    await waitFrames(5);
    const after = await snap();
    const lt = await takeLT();
    const problem = expect(before, after);
    results.push({
      name, ok: !problem, problem: problem || null,
      longTasksMs: lt.sort((a, b) => b - a).slice(0, 3),
      worstBlockMs: lt.length ? Math.max(...lt) : 0,
    });
  };

  const setRange = (id, val) => page.evaluate(([i, v]) => {
    const el = document.getElementById(i);
    el.value = String(v);
    el.dispatchEvent(new Event('input', { bubbles: true }));
  }, [id, val]);

  // ---- does the auto-quality loop move the setting under the user? ----
  const qStart = (await snap()).q;
  await page.waitForTimeout(9000);
  const qDrift = (await snap()).q;
  results.push({
    name: 'quality stays put while untouched',
    ok: qStart === qDrift,
    problem: qStart === qDrift ? null : `auto-quality moved ${qStart} -> ${qDrift} on its own`,
    longTasksMs: [], worstBlockMs: 0,
  });

  await check('Свет (lamps off)', () => page.click('#btnLamps'),
    (b, a) => (a.lamps !== false ? 'CFG.lamps not false' :
      a.lampsPressed !== 'false' ? 'aria-pressed not updated' :
      a.lampI >= b.lampI ? `lamp intensity did not fall (${b.lampI} -> ${a.lampI})` : null));

  await check('Свет (lamps on)', () => page.click('#btnLamps'),
    (b, a) => (a.lamps !== true ? 'CFG.lamps not true' :
      a.lampI <= b.lampI ? `lamp intensity did not rise (${b.lampI} -> ${a.lampI})` : null));

  await check('открыть настройки', () => page.click('#btnSheet'),
    (b, a) => (a.sheetOpen ? null : 'sheet did not open'));

  await check('Огонь → 20%', () => setRange('sFire', 20),
    (b, a) => (Math.abs(a.fire - 0.2) > 1e-6 ? 'CFG.fire wrong' :
      Math.abs(a.uFire - 0.2) > 1e-6 ? 'U.fire uniform not updated' : null));

  await check('Огонь → 120%', () => setRange('sFire', 120),
    (b, a) => (Math.abs(a.uFire - 1.2) > 1e-6 ? 'U.fire uniform not updated' : null));

  await check('Дождь → 0%', () => setRange('sRain', 0),
    (b, a) => (Math.abs(a.uRain) > 1e-6 ? 'U.rain uniform not updated' : null));

  await check('Дождь → 140%', () => setRange('sRain', 140),
    (b, a) => (Math.abs(a.uRain - 1.4) > 1e-6 ? 'U.rain uniform not updated' : null));

  await check('Теплота → 0%', () => setRange('sWarm', 0),
    (b, a) => (Math.abs(a.uWarm) > 1e-6 ? 'uWarm uniform not updated' : null));

  await check('Теплота → 100%', () => setRange('sWarm', 100),
    (b, a) => (Math.abs(a.uWarm - 1) > 1e-6 ? 'uWarm uniform not updated' : null));

  await check('Качество → низкое', () => page.click('#segQual button[data-q="0"]'),
    (b, a) => (a.q !== 0 ? 'quality not 0' :
      a.qPressed[0] !== 'true' ? 'aria-pressed not on low' :
      a.reflFloor !== 0 ? 'floor reflection not disabled at low' : null));

  await check('Качество → высокое', () => page.click('#segQual button[data-q="2"]'),
    (b, a) => (a.q !== 2 ? 'quality not 2' :
      a.reflWin === 0 ? 'window reflection not enabled at high' : null));

  await check('Качество → среднее', () => page.click('#segQual button[data-q="1"]'),
    (b, a) => (a.q !== 1 ? 'quality not 1' :
      a.reflWin !== 0 ? 'window reflection still on at medium' :
      a.reflFloor === 0 ? 'floor reflection off at medium' : null));

  await check('Разрешение → 50%', () => page.evaluate(() => {
    const el = document.getElementById('sRes');
    el.value = '50';
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }), (b, a) => (Math.abs(a.res - 0.5) > 1e-6 ? `CFG.res is ${a.res}` : null));

  await check('Разрешение → 100%', () => page.evaluate(() => {
    const el = document.getElementById('sRes');
    el.value = '100';
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }), (b, a) => (Math.abs(a.res - 1) > 1e-6 ? `CFG.res is ${a.res}` : null));

  await check('смена качества ничего не выделяет', () => page.click('#segQual button[data-q="2"]'),
    (b, a) => (a.commitMs > 5 ? `quality switch took ${a.commitMs} ms of JS` : null));

  await check('Камера → Статично', () => page.click('#segDrift button[data-drift="0"]'),
    (b, a) => (a.drift !== false ? 'CFG.drift not false' :
      a.driftPressed[1] !== 'true' ? 'aria-pressed not on static' : null));

  await check('Камера → Плавный дрейф', () => page.click('#segDrift button[data-drift="1"]'),
    (b, a) => (a.drift !== true ? 'CFG.drift not true' : null));

  // quality must survive a subsequent auto-quality window
  const qUser = (await snap()).q;
  await page.waitForTimeout(9000);
  const qLater = (await snap()).q;
  results.push({
    name: 'user quality choice is respected',
    ok: qUser === qLater,
    problem: qUser === qLater ? null : `auto-quality overrode the user: ${qUser} -> ${qLater}`,
    longTasksMs: [], worstBlockMs: 0,
  });

  const failed = results.filter((r) => !r.ok);
  console.log(JSON.stringify({
    failed: failed.length,
    worstBlockMs: Math.max(0, ...results.map((r) => r.worstBlockMs)),
    results, errs,
  }, null, 1));
  await browser.close();
  process.exit(failed.length || errs.length ? 1 : 0);
})();
