// Shared by the smoke test: isolated profiles, launching the app (from the source tree or a
// packaged build), a second node to talk to, small waiting helpers.
import { spawn } from 'node:child_process';
import { createRequire } from 'node:module';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
export const appDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
export const exe = process.platform === 'win32' ? 'svoi.exe' : 'svoi';
const key = `${process.platform === 'win32' ? 'win' : process.platform === 'darwin' ? 'mac' : 'linux'}-${process.arch}`;
export const coreBinary = () => process.env.SVOI_CORE || path.join(appDir, 'bin', key, exe);

/** Where this test run keeps everything (nothing touches the real profile). */
export function sandboxDirs(prefix = 'svoi-desktop-test-') {
  const base = fs.mkdtempSync(path.join(os.tmpdir(), prefix));
  const d = { base, userData: path.join(base, 'profile'), data: path.join(base, 'data'), home: path.join(base, 'home'), downloads: path.join(base, 'home', 'Downloads') };
  fs.mkdirSync(d.home, { recursive: true });
  return d;
}

/** The app: SVOI_APP_EXE = a packaged build, else the source tree run with the installed Electron. */
export function launchOptions(dirs, extraEnv = {}) {
  const env = {
    ...process.env,
    SVOI_DESKTOP_TEST: '1',
    SVOI_DESKTOP_USERDATA: dirs.userData,
    SVOI_DIR: dirs.data,
    // a packaged build must find the program it carries; only the source tree needs to be told where it is
    ...(process.env.SVOI_APP_EXE ? {} : { SVOI_CORE: coreBinary() }),
    SVOI_CORE_ARGS: JSON.stringify(['--no-stun', '--no-portmap', '--loopback']),
    // (Windows keeps its real profile: pointing USERPROFILE somewhere else stalls Electron's start there.
    // Downloads then land in the real Downloads folder, and the test removes what it saved.)
    ...(process.platform === 'win32' ? {} : { HOME: dirs.home, XDG_CONFIG_HOME: path.join(dirs.home, '.config'), XDG_DOWNLOAD_DIR: dirs.downloads }),
    ...extraEnv,
  };
  delete env.ELECTRON_RUN_AS_NODE;
  const sandboxOff = process.platform === 'linux' ? ['--no-sandbox'] : [];
  const packaged = process.env.SVOI_APP_EXE;
  return packaged ? { executablePath: packaged, args: sandboxOff, env } : { executablePath: require('electron'), args: [appDir, ...sandboxOff], env };
}

/** A second `svoi` node (another device) in its own directory. */
export async function startNode(name, base) {
  const data = path.join(base, 'node-' + name);
  fs.mkdirSync(data, { recursive: true });
  const log = fs.openSync(path.join(base, `node-${name}.log`), 'a');
  const child = spawn(coreBinary(), ['up', '--no-browser', '--no-stun', '--no-portmap', '--loopback', '--ui', '127.0.0.1:0', '--exit-when-stdin-closes', '--dir', data], { stdio: ['pipe', log, log], windowsHide: true, env: { ...process.env, SVOI_DIR: data } });
  child.stdin.on('error', () => {});
  const node = { name, data, child };
  await waitFor(() => fs.existsSync(path.join(data, 'ui.addr')) && fs.existsSync(path.join(data, 'ui.token')), 20000, `node ${name} to start`);
  node.origin = 'http://' + fs.readFileSync(path.join(data, 'ui.addr'), 'utf8').trim();
  node.token = fs.readFileSync(path.join(data, 'ui.token'), 'utf8').trim();
  node.api = async (method, p, body) => {
    const res = await fetch(node.origin + p, { method, headers: { Authorization: 'Bearer ' + node.token, 'X-Svoi': '1', ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) }, body: body === undefined ? undefined : JSON.stringify(body) });
    const text = await res.text();
    if (!res.ok) throw new Error(`${method} ${p} → ${res.status} ${text.slice(0, 200)}`);
    return text ? JSON.parse(text) : null;
  };
  node.stop = async () => {
    try {
      child.stdin.end();
    } catch {
      /* gone */
    }
    await Promise.race([new Promise((r) => child.once('exit', r)), sleep(8000)]);
    if (child.exitCode === null) child.kill();
  };
  return node;
}

export async function waitFor(fn, ms, what) {
  const end = Date.now() + ms;
  let last;
  while (Date.now() < end) {
    try {
      const v = await fn();
      if (v) return v;
    } catch (e) {
      last = e;
    }
    await sleep(150);
  }
  throw new Error(`timed out waiting for ${what}${last ? ': ' + last.message : ''}`);
}

export function alive(pid) {
  try {
    process.kill(pid, 0);
    return true;
  } catch (e) {
    return e.code === 'EPERM';
  }
}
