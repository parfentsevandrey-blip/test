// End-to-end test of the desktop app itself: a real Electron window, the real `themesh` program as its
// background process, a second real node as "another device". Runs the same on Linux (under Xvfb),
// Windows and macOS.
//
//   node test/smoke.mjs [--shots DIR]          the source tree, run with the installed Electron
//   THEMESH_APP_EXE=/path/to/The Mesh.exe node test/smoke.mjs     a packaged build
import { _electron as electron } from 'playwright-core';
import { spawn } from 'node:child_process';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { alive, appDir, coreBinary, launchOptions, sandboxDirs, sleep, startNode, waitFor } from './helpers.mjs';

const args = process.argv.slice(2);
const shotsDir = args.includes('--shots') ? path.resolve(args[args.indexOf('--shots') + 1]) : null;
if (shotsDir) fs.mkdirSync(shotsDir, { recursive: true });
if (!fs.existsSync(coreBinary())) {
  console.error('no core program at ' + coreBinary() + ' (build it: go build -o desktop/bin/<platform>-<arch>/themesh ./cmd/themesh)');
  process.exit(2);
}

const dirs = sandboxDirs();
const results = [];
let app = null;
let page = null;
let other = null;
const apps = [];

async function step(name, fn) {
  const t0 = Date.now();
  try {
    await fn();
    results.push({ name, ok: true });
    console.log(`  ✓ ${name}  (${((Date.now() - t0) / 1000).toFixed(1)}s)`);
  } catch (e) {
    results.push({ name, ok: false, error: e });
    console.log(`  ✗ ${name}\n      ${String(e && e.stack ? e.stack : e).split('\n').slice(0, 8).join('\n      ')}`);
    throw e; // later steps depend on earlier ones
  }
}

const shot = async (name) => {
  if (!shotsDir || !page) return;
  // the top of the page, with no key focus showing: what a person sees when the window opens
  await page
    .evaluate(() => {
      for (const el of document.querySelectorAll('*')) if (el.scrollTop) el.scrollTop = 0;
      window.scrollTo(0, 0);
      if (document.activeElement && document.activeElement.blur) document.activeElement.blur();
    })
    .catch(() => {});
  await sleep(300);
  await page.screenshot({ path: path.join(shotsDir, name + '.png') }).catch(() => {});
};

const hook = (fn, arg) => app.evaluate(fn, arg);
const onUI = async () => {
  await page.waitForURL(/^http:\/\/127\.0\.0\.1:\d+\//, { timeout: 60000 });
};

async function launch(extraEnv) {
  const o = launchOptions(dirs, extraEnv);
  const a = await electron.launch({ executablePath: o.executablePath, args: o.args, env: o.env });
  apps.push(a);
  return a;
}

async function quit(a) {
  const proc = a.process();
  await a.evaluate(({ app }) => app.quit()).catch(() => {});
  await waitFor(() => proc.exitCode !== null || proc.signalCode !== null, 20000, 'the app to exit');
}

try {
  console.log(`desktop smoke test (${process.platform}/${process.arch}), core ${coreBinary()}`);

  await step('the app starts and shows the interface in its own window, no terminal, no browser', async () => {
    app = await launch();
    page = await app.firstWindow();
    page.on('pageerror', (e) => console.log('      page error:', e.message));
    await onUI();
    await page.getByTestId('page-onboarding').waitFor({ timeout: 60000 });
    assert.equal(await hook(() => global.__themeshTest.status), 'running');
    assert.equal(await hook(() => global.__themeshTest.core.attached), false);
    assert.equal(await hook(() => global.__themeshTest.window().getTitle()), 'The Mesh');
    await shot('desktop-1-first-start');
  });

  await step('the page has no way into the computer: no Node, no shell bridge', async () => {
    const seen = await page.evaluate(() => ({ require: typeof require, process: typeof process, shell: typeof window.themeshShell, electron: typeof window.electron }));
    assert.deepEqual(seen, { require: 'undefined', process: 'undefined', shell: 'undefined', electron: 'undefined' });
  });

  await step('the first start asks nothing and writes its data where the program expects it', async () => {
    assert.ok(fs.existsSync(path.join(dirs.data, 'device.key')), 'the device key is in the data directory');
    assert.ok(fs.existsSync(path.join(dirs.userData, 'logs', 'desktop.log')), 'the shell keeps a log');
    assert.ok(fs.existsSync(path.join(dirs.userData, 'logs', 'themesh.log')), 'the core keeps a log');
    if (process.platform !== 'win32') assert.equal(fs.statSync(path.join(dirs.data, 'device.key')).mode & 0o077, 0, 'the key is private');
  });

  await step('a network is created in the window', async () => {
    await page.getByTestId('onb-create').click();
    await page.getByTestId('onb-mesh-name').fill('Наш дом');
    await page.getByTestId('onb-device-name').fill('laptop');
    await page.getByTestId('onb-owner').fill('Мария');
    await shot('desktop-2-create');
    await page.getByTestId('onb-submit').click();
    await page.getByTestId('page-home').waitFor({ timeout: 30000 });
    await shot('desktop-3-home');
  });

  let corePid = 0;
  await step('closing the window hides it; the device stays on the network', async () => {
    corePid = await hook(() => global.__themeshTest.core.child.pid);
    assert.ok(alive(corePid));
    await hook(() => global.__themeshTest.window().close());
    await waitFor(async () => !(await hook(() => global.__themeshTest.visible())), 5000, 'the window to hide');
    assert.ok(alive(corePid), 'the core keeps running');
    const origin = await hook(() => global.__themeshTest.core.origin);
    const res = await fetch(`${origin}/api/handshake?n=${'a'.repeat(16)}`);
    assert.equal(res.status, 200);
  });

  await step('starting the app a second time only brings the first one back', async () => {
    const o = launchOptions(dirs);
    const second = spawn(o.executablePath, o.args, { env: o.env, stdio: 'ignore', windowsHide: true });
    const code = await new Promise((r) => {
      const timer = setTimeout(() => {
        second.kill();
        r('timeout');
      }, 30000);
      second.once('exit', (c) => {
        clearTimeout(timer);
        r(c);
      });
    });
    assert.equal(code, 0, 'the second copy quits by itself');
    await waitFor(() => hook(() => global.__themeshTest.visible()), 10000, 'the first window to come back');
  });

  await step('a message from another device raises a notification while the window is hidden', async () => {
    other = await startNode('phone', dirs.base);
    const inv = await hook(() => global.__themeshTest.core.api('POST', '/api/invites', { admin: false, ttlMinutes: 15 }));
    await other.api('POST', '/api/mesh/join', { invite: inv.code, deviceName: 'phone' });
    const me = await hook(() => global.__themeshTest.core.api('GET', '/api/state'));
    await waitFor(async () => (await hook(() => global.__themeshTest.core.api('GET', '/api/state'))).peers.some((p) => p.online), 30000, 'the other device to connect');
    await hook(() => global.__themeshTest.window().hide());
    const text = 'Привет с телефона ' + Date.now();
    await other.api('POST', `/api/chat/${me.self.id}`, { text });
    const note = await waitFor(async () => (await hook(() => global.__themeshTest.notes)).find((n) => n.key.startsWith('chat:') && n.body === text), 20000, 'the notification');
    const known = await hook(() => global.__themeshTest.watcher().state.peers.map((p) => `${p.id.slice(0, 8)}=${p.name}`));
    assert.equal(note.title, 'phone', `the notification names the device (peers known to the shell: ${known.join(', ') || 'none'}; note: ${JSON.stringify(note)})`);
    assert.match(note.route, /^#\/chat\//);
    // what the tray sentence is built from
    await waitFor(() => hook(() => global.__themeshTest.watcher().state.peers.length >= 1), 5000, 'the peer list');
  });

  await step('a file offered by another device is announced, and arrives', async () => {
    const me = await hook(() => global.__themeshTest.core.api('GET', '/api/state'));
    const data = Buffer.from('содержимое файла '.repeat(100));
    const res = await fetch(`${other.origin}/api/transfers?to=${me.self.id}&name=${encodeURIComponent('отчёт.txt')}&mime=text/plain`, { method: 'POST', headers: { Authorization: 'Bearer ' + other.token, 'X-Themesh': '1', 'Content-Type': 'application/octet-stream' }, body: data });
    assert.equal(res.status, 200, await res.text());
    const note = await waitFor(async () => (await hook(() => global.__themeshTest.notes)).find((n) => /^(done|offer):/.test(n.key) && /отчёт\.txt/.test(n.body)), 20000, 'the file notification');
    assert.ok(note.title.length > 0);
  });

  await step('the window comes back from the tray and shows the same session', async () => {
    await hook(() => global.__themeshTest.window().show());
    await page.getByTestId('page-home').waitFor({ timeout: 10000 });
    await page.getByTestId('home-device').first().waitFor({ timeout: 20000 });
    await shot('desktop-4-two-devices');
  });

  await step('a file from the interface is saved into Downloads', async () => {
    const name = 'themesh-test-' + Date.now() + '.txt';
    const dl = await hook(({ app }) => app.getPath('downloads'));
    const dest = path.join(dl, name);
    try {
      await page.evaluate((n) => {
        const a = document.createElement('a');
        a.href = URL.createObjectURL(new Blob(['hello'], { type: 'text/plain' }));
        a.download = n;
        document.body.appendChild(a);
        a.click();
      }, name);
      await waitFor(() => fs.existsSync(dest) && fs.readFileSync(dest, 'utf8') === 'hello', 15000, 'the downloaded file');
    } finally {
      fs.rmSync(dest, { force: true });
    }
  });

  await step('if the core dies the app starts it again and signs the window in', async () => {
    const before = await hook(() => global.__themeshTest.core.child.pid);
    process.kill(before, process.platform === 'win32' ? undefined : 'SIGKILL');
    await waitFor(async () => (await hook(() => global.__themeshTest.status)) === 'running' && (await hook(() => global.__themeshTest.core.child.pid)) !== before, 60000, 'the core to come back');
    await page.getByTestId('page-home').waitFor({ timeout: 30000 });
    await page.getByTestId('home-device').first().waitFor({ timeout: 30000 });
  });

  await step('quitting stops the core and keeps the keys', async () => {
    corePid = await hook(() => global.__themeshTest.core.child.pid);
    await quit(app);
    await waitFor(() => !alive(corePid), 15000, 'the core to stop');
    assert.equal(fs.existsSync(path.join(dirs.data, 'ui.addr')), false);
    assert.ok(fs.existsSync(path.join(dirs.data, 'device.key')));
  });

  await step('the next start is the same device in the same network, signed in again', async () => {
    app = await launch();
    page = await app.firstWindow();
    await onUI();
    await page.getByTestId('page-home').waitFor({ timeout: 60000 });
    assert.equal(await page.getByTestId('page-onboarding').count(), 0);
    await shot('desktop-5-after-restart');
  });

  await step('a node that is already running (the command line) is used and survives the app', async () => {
    await quit(app);
    // a node of ours on the very data directory the app uses (what `themesh up` in a terminal would be)
    const log = fs.openSync(path.join(dirs.base, 'external-core.log'), 'a');
    const ext = spawn(coreBinary(), ['up', '--no-browser', '--no-stun', '--no-portmap', '--loopback', '--ui', '127.0.0.1:0', '--exit-when-stdin-closes', '--dir', dirs.data], { stdio: ['pipe', log, log], windowsHide: true, env: { ...process.env, THEMESH_DIR: dirs.data, ...(process.platform === 'win32' ? { GODEBUG: 'asyncpreemptoff=1' } : {}) } });
    ext.stdin.on('error', () => {});
    let extEnd = 'still running';
    ext.on('exit', (code, signal) => {
      extEnd = `ended with code ${code} / signal ${signal} at ${new Date().toISOString()}`;
    });
    try {
      await waitFor(() => fs.existsSync(path.join(dirs.data, 'ui.addr')), 20000, 'the external node');
      app = await launch();
      page = await app.firstWindow();
      await onUI();
      await page.getByTestId('page-home').waitFor({ timeout: 60000 });
      assert.equal(await hook(() => global.__themeshTest.core.attached), true);
      await quit(app);
      let extLog = '';
      try {
        extLog = fs.readFileSync(path.join(dirs.base, 'external-core.log'), 'utf8').split('\n').slice(-40).join('\n');
      } catch {
        /* no log */
      }
      assert.ok(alive(ext.pid), `the app does not stop a node it did not start (the node ${extEnd}; its log:\n${extLog})`);
    } finally {
      try {
        ext.stdin.end();
      } catch {
        /* gone */
      }
      await Promise.race([new Promise((r) => ext.once('exit', r)), sleep(10000)]);
      if (ext.exitCode === null) ext.kill();
    }
  });
} catch (e) {
  console.log('\nstopped at the first failure');
  for (const name of ['desktop.log', 'themesh.log']) {
    const f = path.join(dirs.userData, 'logs', name);
    if (fs.existsSync(f)) {
      const text = fs.readFileSync(f, 'utf8');
      const lines = text.split('\n');
      console.log(`--- ${name} (tail) ---\n` + lines.slice(-25).join('\n'));
      // a program that crashed: where it says so (the tail of its dump is no use without the top)
      const at = lines.map((l, i) => (/^(panic:|fatal error:|runtime:|Exception 0x|SIG[A-Z]+:|WARNING: DATA RACE)/.test(l) ? i : -1)).filter((i) => i >= 0);
      for (const i of at.slice(0, 3)) console.log(`--- ${name}: a crash at line ${i + 1} ---\n` + lines.slice(i, i + 60).join('\n'));
      if (shotsDir) fs.writeFileSync(path.join(shotsDir, `failure-${name}`), text);
    } else console.log(`--- no ${name}: the app never got as far as writing it (profile: ${fs.existsSync(dirs.userData) ? fs.readdirSync(dirs.userData).join(', ') || 'empty' : 'not created'})`);
  }
  if (shotsDir) {
    for (const f of fs.readdirSync(dirs.base).filter((n) => /^(node-.*|external-core)\.log$/.test(n))) fs.copyFileSync(path.join(dirs.base, f), path.join(shotsDir, `failure-${f}`));
  }
} finally {
  if (other) await other.stop().catch(() => {});
  for (const a of apps) await a.close().catch(() => {});
  if (!process.env.THEMESH_KEEP) fs.rmSync(dirs.base, { recursive: true, force: true });
}

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length} of ${results.length} steps passed${failed.length ? '; failed: ' + failed.map((r) => r.name).join('; ') : ''}`);
process.exit(failed.length || !results.length ? 1 : 0);
