// Tests of the shell's logic that need no window: `node --test test/unit.mjs`.
// The core tests run the real `themesh` program (set THEMESH_CORE to its path; otherwise ../bin/<platform>-<arch>/).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import net from 'node:net';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const here = path.dirname(fileURLToPath(import.meta.url));
const { parseSSE } = require('../src/events.js');
const { notificationFor, clip } = require('../src/notify.js');
const { statusText } = require('../src/status.js');
const { safeName, uniquePath } = require('../src/files.js');
const { texts, ru, en } = require('../src/i18n.js');
const { Core, coreEnv, freePort, handshakeProof, verifyNode } = require('../src/core.js');
const log = require('../src/log.js');

const key = `${process.platform === 'win32' ? 'win' : process.platform === 'darwin' ? 'mac' : 'linux'}-${process.arch}`;
const exe = process.platform === 'win32' ? 'themesh.exe' : 'themesh';
const CORE = process.env.THEMESH_CORE || path.join(here, '..', 'bin', key, exe);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function collect(chunks) {
  async function* body() {
    for (const c of chunks) yield Buffer.from(c);
  }
  const out = [];
  for await (const m of parseSSE(body())) out.push(m);
  return out;
}

test('the event stream is parsed across chunk boundaries, CRLF and comments', async () => {
  const msgs = await collect([': hi\n\nevent: peers\ndata: [{"id"', ':"a"}]\n\nevent: chat\r\ndata: {"x":1}\r\n', '\r\nevent: none\n\ndata: lonely\n\n']);
  assert.deepEqual(msgs, [
    { event: 'peers', data: '[{"id":"a"}]' },
    { event: 'chat', data: '{"x":1}' },
    { event: 'message', data: 'lonely' },
  ]);
});

test('a multi-line data field is joined', async () => {
  assert.deepEqual(await collect(['event: e\ndata: a\ndata: b\n\n']), [{ event: 'e', data: 'a\nb' }]);
});

const t = texts('ru-RU');
const ctx = (extra = {}) => ({ t, peerName: (id) => ({ p1: 'телефон', p2: 'nas' })[id] || '', fetchMail: async () => ({ from: { name: 'nas' }, subject: 'Отчёт о резервном копировании' }), ...extra });

test('an offered file becomes a notification, once per transfer', async () => {
  const n = await notificationFor('transfer', { id: 't1', dir: 'in', state: 'offered', name: 'фото.jpg', peer: 'p1', peerName: 'телефон' }, ctx());
  assert.deepEqual(n, { key: 'offer:t1', title: 'телефон', body: 'хочет отправить вам файл «фото.jpg»', route: '#/home' });
});

test('a received file is announced; sent files and progress are not', async () => {
  const done = await notificationFor('transfer', { id: 't1', dir: 'in', state: 'done', name: 'a.bin', peer: 'p2' }, ctx());
  assert.equal(done.key, 'done:t1');
  assert.match(done.body, /«a\.bin» — от устройства nas/);
  assert.equal(await notificationFor('transfer', { id: 't2', dir: 'out', state: 'done', name: 'a' }, ctx()), null);
  assert.equal(await notificationFor('transfer', { id: 't3', dir: 'in', state: 'active', name: 'a' }, ctx()), null);
});

test('chat: only messages from others; long and empty texts are handled', async () => {
  assert.equal(await notificationFor('chat', { id: 'c1', mine: true, text: 'hi', peer: 'p1' }, ctx()), null);
  const n = await notificationFor('chat', { id: 'c2', mine: false, text: 'Ты дома?', peer: 'p1' }, ctx());
  assert.deepEqual(n, { key: 'chat:c2', title: 'телефон', body: 'Ты дома?', route: '#/chat/p1' });
  const long = await notificationFor('chat', { id: 'c3', mine: false, text: 'x'.repeat(500), peer: 'p1' }, ctx());
  assert.ok(long.body.length <= 140 && long.body.endsWith('…'));
  const file = await notificationFor('chat', { id: 'c4', mine: false, text: '', peer: 'p1', attachments: [{}] }, ctx());
  assert.equal(file.body, ru.chatAttachment);
});

test('mail: unread inbox letters only, with the subject looked up', async () => {
  const n = await notificationFor('mail', { id: 'm1', folder: 'inbox', unread: true }, ctx());
  assert.deepEqual(n, { key: 'mail:m1', title: 'Новое письмо от nas', body: 'Отчёт о резервном копировании', route: '#/mail/inbox' });
  assert.equal(await notificationFor('mail', { id: 'm2', folder: 'sent', unread: true }, ctx()), null);
  assert.equal(await notificationFor('mail', { id: 'm3', folder: 'inbox', unread: false }, ctx()), null);
  assert.equal(await notificationFor('mail', { id: 'm4', folder: 'inbox', unread: true }, ctx({ fetchMail: async () => { throw new Error('gone'); } })), null);
});

test('the English texts exist for everything the Russian ones have', () => {
  assert.deepEqual(Object.keys(en).sort(), Object.keys(ru).sort());
  assert.equal(texts('en-US'), en);
  assert.equal(texts('ru'), ru);
  assert.equal(texts('de-DE'), en);
  assert.equal(texts(undefined), en);
  assert.equal(clip('  a   b  ', 10), 'a b');
});

test('the tray sentence says what the network is doing', () => {
  const peers = (...online) => online.map((o, i) => ({ id: String(i), online: o }));
  assert.equal(statusText(ru, 'starting', null), ru.statusStarting);
  assert.equal(statusText(ru, 'failed', null), ru.statusStopped);
  assert.equal(statusText(ru, 'running', { self: { configured: false }, peers: [] }), ru.statusNoNetwork);
  assert.equal(statusText(ru, 'running', { self: { configured: true }, peers: [] }), ru.statusAlone);
  assert.equal(statusText(ru, 'running', { self: { configured: true }, peers: peers(true, false, true) }), 'На связи 3 из 4');
  assert.equal(statusText(en, 'running', { self: { configured: true }, peers: peers(false) }), '1 of 2 online');
});

test('downloaded file names are safe on every system and never overwrite', () => {
  assert.equal(safeName('../../etc/passwd'), 'passwd');
  assert.equal(safeName('a<b>:c|d?.txt'), 'a_b__c_d_.txt');
  assert.equal(safeName('con.txt'), '_con.txt');
  assert.equal(safeName('report. '), 'report');
  assert.equal(safeName(''), 'file');
  assert.equal(safeName('я'.repeat(300)).length, 200);
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'themesh-files-'));
  try {
    assert.equal(uniquePath(dir, 'a.txt'), path.join(dir, 'a.txt'));
    fs.writeFileSync(path.join(dir, 'a.txt'), '1');
    assert.equal(uniquePath(dir, 'a.txt'), path.join(dir, 'a (2).txt'));
    fs.writeFileSync(path.join(dir, 'a (2).txt'), '2');
    assert.equal(uniquePath(dir, 'a.txt'), path.join(dir, 'a (3).txt'));
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('freePort keeps the preferred port when it is free and moves when it is not', async () => {
  const first = await freePort(0);
  assert.ok(first > 0);
  assert.equal(await freePort(first), first);
  const srv = net.createServer();
  await new Promise((r) => srv.listen(first, '127.0.0.1', r));
  const other = await freePort(first);
  assert.ok(other > 0 && other !== first);
  await new Promise((r) => srv.close(r));
});

test('the handshake proof matches the one the Go node computes', () => {
  // The vector comes from api.HandshakeProof("tok", "nnnnnnnnnnnnnnnn") in internal/api/session.go
  // (and, independently, from Python's hmac): key = token, message = "themesh-handshake/v1\0" + nonce.
  assert.equal(handshakeProof('tok', 'n'.repeat(16)), '6dde69f3781328e11fcff588efda0a293ad1b943871f740f2ec19fa7e598d739');
  assert.notEqual(handshakeProof('tok', 'a'.repeat(16)), handshakeProof('tok', 'b'.repeat(16)));
  assert.notEqual(handshakeProof('tok', 'a'.repeat(16)), handshakeProof('other', 'a'.repeat(16)));
});

// ---- the real program ------------------------------------------------------------------------

const haveCore = fs.existsSync(CORE);
const coreTest = haveCore ? test : test.skip;

function sandbox() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'themesh-core-'));
  log.init(path.join(dir, 'logs'));
  return { dir, data: path.join(dir, 'data'), logFile: path.join(dir, 'logs', 'themesh.log') };
}
const mk = (s, extra = {}) => new Core({ binary: CORE, dataDir: s.data, logFile: s.logFile, extraArgs: ['--no-stun', '--no-portmap', '--loopback'], ...extra });

coreTest('the core starts without a terminal, signs a window in, and stops cleanly with its parent', async () => {
  const s = sandbox();
  const core = mk(s);
  try {
    await core.start();
    assert.equal(core.status, 'running');
    assert.equal(core.attached, false);
    assert.ok(await verifyNode(core.origin, core.token));
    assert.equal(await verifyNode(core.origin, 'not-the-token'), false, 'a node that does not know our token is not trusted');
    const url = await core.loginURL();
    assert.match(url, /^http:\/\/127\.0\.0\.1:\d+\/\?t=[0-9a-f]+$/);
    const st = await core.api('GET', '/api/state');
    assert.equal(st.configured, false);
    // the link signs a browser in exactly once
    const first = await fetch(url, { redirect: 'manual' });
    assert.ok(first.status >= 200 && first.status < 400, 'the link works: ' + first.status);
    await core.stop();
    assert.equal(core.status, 'stopped');
    assert.equal(fs.existsSync(path.join(s.data, 'ui.addr')), false, 'a clean exit removes ui.addr');
    assert.ok(fs.existsSync(path.join(s.data, 'device.key')), 'the device key stays');
  } finally {
    await core.stop().catch(() => {});
    fs.rmSync(s.dir, { recursive: true, force: true });
  }
});

coreTest('a node that already runs on the data directory is used, and left running', async () => {
  const s = sandbox();
  const owner = mk(s);
  const guest = mk(s);
  try {
    await owner.start();
    await guest.start();
    assert.equal(guest.attached, true);
    assert.equal(guest.origin, owner.origin);
    assert.ok((await guest.loginURL()).startsWith(owner.origin));
    await guest.stop();
    assert.ok(await verifyNode(owner.origin, owner.token), 'attached means not ours to stop');
  } finally {
    await owner.stop().catch(() => {});
    fs.rmSync(s.dir, { recursive: true, force: true });
  }
});

coreTest('a stale ui.addr from a crashed run does not fool it', async () => {
  const s = sandbox();
  fs.mkdirSync(s.data, { recursive: true });
  fs.writeFileSync(path.join(s.data, 'ui.addr'), '127.0.0.1:9\n');
  fs.writeFileSync(path.join(s.data, 'ui.token'), 'deadbeef'.repeat(6) + '\n');
  const core = mk(s);
  try {
    await core.start();
    assert.equal(core.attached, false);
    assert.notEqual(new URL(core.origin).port, '9');
  } finally {
    await core.stop().catch(() => {});
    fs.rmSync(s.dir, { recursive: true, force: true });
  }
});

coreTest('when the program is missing or cannot start the error says so', async () => {
  const s = sandbox();
  try {
    const missing = new Core({ binary: path.join(s.dir, 'nope', exe), dataDir: s.data, logFile: s.logFile });
    await assert.rejects(missing.start(), /Не найден файл программы/);
    assert.equal(missing.status, 'failed');
    const broken = mk(s, { extraArgs: ['--definitely-not-a-flag'] });
    await assert.rejects(broken.start(), /не запустилась/);
  } finally {
    fs.rmSync(s.dir, { recursive: true, force: true });
  }
});

coreTest('if the core dies by itself the shell is told', async () => {
  const s = sandbox();
  const core = mk(s);
  try {
    await core.start();
    const crashed = new Promise((r) => core.once('crashed', r));
    process.kill(core.child.pid, process.platform === 'win32' ? undefined : 'SIGKILL');
    await Promise.race([crashed, sleep(10000).then(() => assert.fail('no crash event'))]);
    assert.equal(core.status, 'failed');
  } finally {
    await core.stop().catch(() => {});
    fs.rmSync(s.dir, { recursive: true, force: true });
  }
});

// ---- the window's way of loading the interface (Electron itself is replaced by a stand-in) ----

function loadWindowModule() {
  const M = require('node:module');
  const original = M._load;
  const fake = { BrowserWindow: class {}, Menu: {}, Notification: class {}, app: { isPackaged: true, getPath: () => os.tmpdir() }, clipboard: {}, nativeTheme: {}, screen: { getAllDisplays: () => [] }, session: {}, shell: {} };
  M._load = function (request, parent, isMain) {
    return request === 'electron' ? fake : original.call(this, request, parent, isMain);
  };
  try {
    delete require.cache[require.resolve('../src/window.js')];
    return require('../src/window.js');
  } finally {
    M._load = original;
  }
}

function windowWith(loadURL, links) {
  const { MainWindow } = loadWindowModule();
  const mw = new MainWindow({ t: {}, core: { loginURL: async () => `http://127.0.0.1:1/?t=${++links.n}` }, settings: {}, root: '', locale: 'en' });
  mw.win = { webContents: { session: { clearStorageData: async () => {} } }, loadURL };
  return mw;
}

test('the interface is not asked for while the start-up page is loading; an aborted navigation is tried again with a fresh link', async () => {
  const events = [];
  const links = { n: 0 };
  let attempts = 0;
  const mw = windowWith(async (url) => {
    events.push('load ' + url);
    if (++attempts === 1) throw new Error("ERR_ABORTED (-3) loading 'file:///splash.html'");
  }, links);
  let splashDone;
  mw.pageLoading = new Promise((r) => (splashDone = r)).then(() => events.push('splash done'));
  const done = mw.loadUI();
  await sleep(60);
  assert.deepEqual(events, [], 'nothing is loaded while the start-up page is');
  splashDone();
  await done;
  assert.deepEqual(events, ['splash done', 'load http://127.0.0.1:1/?t=1', 'load http://127.0.0.1:1/?t=2']);
});

test('a real failure to load the interface is reported, not retried', async () => {
  const links = { n: 0 };
  let attempts = 0;
  const mw = windowWith(async () => {
    attempts++;
    throw new Error('ERR_CONNECTION_REFUSED (-102)');
  }, links);
  await assert.rejects(mw.loadUI(), /ERR_CONNECTION_REFUSED/);
  assert.equal(attempts, 1);
});

test('an interrupted navigation is retried only a couple of times', async () => {
  const links = { n: 0 };
  let attempts = 0;
  const mw = windowWith(async () => {
    attempts++;
    throw new Error('ERR_ABORTED (-3)');
  }, links);
  await assert.rejects(mw.loadUI(), /ERR_ABORTED/);
  assert.equal(attempts, 3);
});

test('on a Mac the window has no title bar and is see-through; the interface is told in the user agent', () => {
  const { windowLook } = loadWindowModule();
  const look = windowLook({ platform: 'darwin', env: {}, dark: false, reducedTransparency: false, version: '1.2.3' });
  assert.equal(look.glass, true);
  assert.equal(look.vibrant, true);
  assert.equal(look.options.titleBarStyle, 'hiddenInset');
  assert.deepEqual(look.options.trafficLightPosition, { x: 26, y: 22 });
  assert.equal(look.options.vibrancy, 'under-window');
  assert.equal(look.options.visualEffectState, 'followWindow');
  assert.equal(look.options.backgroundColor, '#00000000', 'the web view must not paint over the material');
  assert.equal(look.userAgentToken, 'TheMeshDesktop/1.2.3 (mac; skin=glass; vibrancy; inset)');
});

test('with macOS "Reduce transparency" the title bar stays hidden but nothing is see-through', () => {
  const { windowLook } = loadWindowModule();
  const light = windowLook({ platform: 'darwin', env: {}, dark: false, reducedTransparency: true, version: '1.0.0' });
  assert.equal(light.glass, true);
  assert.equal(light.vibrant, false);
  assert.equal(light.options.titleBarStyle, 'hiddenInset');
  assert.equal(light.options.vibrancy, undefined);
  assert.equal(light.options.backgroundColor, '#f4f2ee');
  assert.equal(windowLook({ platform: 'darwin', env: {}, dark: true, reducedTransparency: true }).options.backgroundColor, '#0d1012');
  assert.equal(light.userAgentToken, 'TheMeshDesktop/1.0.0 (mac; skin=glass; reduced-transparency; inset)');
});

test('THEMESH_DESKTOP_GLASS=0 gives a Mac the ordinary window; Windows and Linux always have it', () => {
  const { windowLook } = loadWindowModule();
  for (const look of [windowLook({ platform: 'darwin', env: { THEMESH_DESKTOP_GLASS: '0' }, version: '1.0.0' }), windowLook({ platform: 'win32', env: {}, version: '1.0.0' }), windowLook({ platform: 'linux', env: {}, version: '1.0.0' })]) {
    assert.equal(look.glass, false);
    assert.equal(look.vibrant, false);
    assert.equal(look.options.titleBarStyle, undefined);
    assert.equal(look.options.vibrancy, undefined);
    assert.notEqual(look.options.backgroundColor, '#00000000');
  }
  assert.equal(windowLook({ platform: 'darwin', env: { THEMESH_DESKTOP_GLASS: '0' }, version: '1.0.0' }).userAgentToken, 'TheMeshDesktop/1.0.0 (mac)');
  assert.equal(windowLook({ platform: 'win32', env: {}, version: '1.0.0' }).userAgentToken, 'TheMeshDesktop/1.0.0 (win)');
  assert.equal(windowLook({ platform: 'linux', env: {}, version: '1.0.0' }).userAgentToken, 'TheMeshDesktop/1.0.0 (linux)');
});

test('on Windows the program runs without asynchronous preemption (a Go runtime bug there); elsewhere nothing is added', () => {
  assert.equal(coreEnv('D:\\data', {}, 'win32').GODEBUG, 'asyncpreemptoff=1');
  assert.equal(coreEnv('D:\\data', { GODEBUG: 'http2client=0' }, 'win32').GODEBUG, 'http2client=0,asyncpreemptoff=1');
  assert.equal(coreEnv('D:\\data', { GODEBUG: 'asyncpreemptoff=0' }, 'win32').GODEBUG, 'asyncpreemptoff=0', 'a choice made by the person is kept');
  assert.equal(coreEnv('/data', {}, 'linux').GODEBUG, undefined);
  assert.equal(coreEnv('/data', { A: '1' }, 'darwin').THEMESH_DIR, '/data');
});
