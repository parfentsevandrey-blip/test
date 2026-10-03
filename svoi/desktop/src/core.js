'use strict';
// The core is the `svoi` program itself, started in the background without a terminal window.
// This module starts it (or attaches to one that already runs on the same data directory), waits
// until its interface answers, proves that what answers really is that node before sending it
// the token, hands out one-time sign-in links and stops it again.
const { spawn } = require('node:child_process');
const crypto = require('node:crypto');
const fs = require('node:fs');
const net = require('node:net');
const path = require('node:path');
const { EventEmitter, once } = require('node:events');
const log = require('./log');

const DEFAULT_PORT = 8777;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** A free loopback port: `preferred` if it is free, else any. */
function freePort(preferred) {
  return new Promise((resolve) => {
    const attempt = (port) => {
      const srv = net.createServer();
      srv.once('error', () => (port ? attempt(0) : resolve(0)));
      srv.listen(port, '127.0.0.1', () => {
        const got = srv.address().port;
        srv.close(() => resolve(got));
      });
    };
    attempt(preferred);
  });
}

/** What a genuine node answers to a handshake nonce (see internal/api/session.go). */
function handshakeProof(token, nonce) {
  return crypto.createHmac('sha256', token).update('svoi-handshake/v1\0' + nonce).digest('hex');
}

/** True only if the thing listening at `origin` knows `token`: the token is never sent to a stranger. */
async function verifyNode(origin, token) {
  try {
    const nonce = crypto.randomBytes(16).toString('hex');
    const res = await fetch(`${origin}/api/handshake?n=${nonce}`, { redirect: 'error', signal: AbortSignal.timeout(3000) });
    if (!res.ok) return false;
    const { proof } = await res.json();
    const want = handshakeProof(token, nonce);
    return typeof proof === 'string' && proof.length === want.length && crypto.timingSafeEqual(Buffer.from(proof), Buffer.from(want));
  } catch {
    return false;
  }
}

function readTrim(file) {
  try {
    return fs.readFileSync(file, 'utf8').trim();
  } catch {
    return '';
  }
}

/**
 * The program's environment. On Windows 11 / Server 2025 the Go runtime crashes now and then while it scans a stack
 * (golang/go#76614, #77955: a thread is preempted while it is inside the kernel); without asynchronous
 * preemption it does not. (The program that ships with the app is built that way already; this covers one
 * that was built without.)
 */
function coreEnv(dataDir, base = process.env, platform = process.platform) {
  const env = { ...base, SVOI_DIR: dataDir };
  if (platform === 'win32' && !/(^|,)asyncpreemptoff=/.test(env.GODEBUG || '')) env.GODEBUG = (env.GODEBUG ? env.GODEBUG + ',' : '') + 'asyncpreemptoff=1';
  return env;
}

class Core extends EventEmitter {
  /**
   * @param {object} o
   * @param {string} o.binary        the svoi executable
   * @param {string} o.dataDir       the node's data directory (keys, mail, settings)
   * @param {string} o.logFile       where the node's own output goes
   * @param {number} [o.preferredPort]
   * @param {string[]} [o.extraArgs] more command-line flags (tests)
   */
  constructor(o) {
    super();
    this.binary = o.binary;
    this.dataDir = o.dataDir;
    this.logFile = o.logFile;
    this.preferredPort = o.preferredPort || DEFAULT_PORT;
    this.extraArgs = o.extraArgs || [];
    this.status = 'idle'; // idle | starting | running | failed | stopped
    this.origin = '';
    this.token = '';
    this.attached = false; // true: a node somebody else started; we never stop it
    this.child = null;
    this.exited = false;
    this.stopping = false;
    this.lastError = '';
    this.version = '';
  }

  setStatus(s, detail) {
    this.status = s;
    if (detail !== undefined) this.lastError = detail;
    this.emit('status', s, detail);
  }

  /** Start (or attach to) the node and resolve once its interface answers. Rejects with a readable Error. */
  async start() {
    this.setStatus('starting', '');
    this.stopping = false;
    this.exited = false;
    fs.mkdirSync(this.dataDir, { recursive: true, mode: 0o700 });
    try {
      if (await this.attachExisting()) {
        this.setStatus('running');
        return;
      }
      await this.spawnOwn();
      this.setStatus('running');
    } catch (e) {
      log.error('core: cannot start', e);
      this.setStatus('failed', e.message || String(e));
      throw e;
    }
  }

  /** A node already runs on this data directory (the command line, say): use it, never stop it. */
  async attachExisting() {
    const addr = readTrim(path.join(this.dataDir, 'ui.addr'));
    const token = readTrim(path.join(this.dataDir, 'ui.token'));
    if (!addr || !token) return false;
    const origin = 'http://' + addr;
    if (!(await verifyNode(origin, token))) return false; // a stale file: nobody listens, or not our node
    this.origin = origin;
    this.token = token;
    this.attached = true;
    log.info('core: attached to the node already running at', origin);
    return true;
  }

  async spawnOwn() {
    if (!fs.existsSync(this.binary)) throw new Error(`Не найден файл программы: ${this.binary}`);
    const port = await freePort(this.preferredPort);
    if (!port) throw new Error('Нет свободного порта для интерфейса');
    const args = ['up', '--no-browser', '--exit-when-stdin-closes', '--ui', `127.0.0.1:${port}`, '--dir', this.dataDir, ...this.extraArgs];
    fs.mkdirSync(path.dirname(this.logFile), { recursive: true });
    try {
      if (fs.statSync(this.logFile).size > 5_000_000) fs.renameSync(this.logFile, this.logFile + '.1');
    } catch {
      /* no log yet */
    }
    const out = fs.openSync(this.logFile, 'a');
    log.info('core: starting', this.binary, args.join(' '));
    // stdin is a pipe we keep open: when this app goes away, however it goes, the pipe closes and so does the node.
    const child = spawn(this.binary, args, { stdio: ['pipe', out, out], windowsHide: true, env: coreEnv(this.dataDir) });
    fs.closeSync(out);
    this.child = child;
    this.exited = false;
    let exitInfo = null;
    child.stdin.on('error', () => {});
    child.on('error', (e) => {
      exitInfo = { error: e };
      log.error('core: cannot run the program', e);
    });
    child.on('exit', (code, signal) => {
      this.exited = true;
      exitInfo = { code, signal };
      log.info('core: exited', code, signal);
      if (!this.stopping && this.status === 'running') {
        this.setStatus('failed', `Программа остановилась (код ${code ?? signal})`);
        this.emit('crashed', { code, signal });
      }
    });

    const origin = `http://127.0.0.1:${port}`;
    const deadline = Date.now() + 40_000;
    for (;;) {
      if (exitInfo) {
        const why = exitInfo.error ? exitInfo.error.message : `код ${exitInfo.code ?? exitInfo.signal}`;
        throw new Error(`Программа не запустилась (${why}). Подробности — в журнале: ${this.logFile}`);
      }
      const addr = readTrim(path.join(this.dataDir, 'ui.addr'));
      const token = readTrim(path.join(this.dataDir, 'ui.token'));
      if (addr === `127.0.0.1:${port}` && token && (await verifyNode(origin, token))) {
        this.origin = origin;
        this.token = token;
        this.attached = false;
        return;
      }
      if (Date.now() > deadline) {
        await this.stop();
        throw new Error(`Программа не ответила за 40 секунд. Подробности — в журнале: ${this.logFile}`);
      }
      await sleep(120);
    }
  }

  /** Call the node's API with the master token (never sent anywhere else, never into a page). */
  async api(method, p, body) {
    const res = await fetch(this.origin + p, {
      method,
      redirect: 'error',
      headers: { Authorization: 'Bearer ' + this.token, 'X-Svoi': '1', ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(15000),
    });
    const text = await res.text();
    if (!res.ok) throw new Error(`${method} ${p} → ${res.status} ${text.slice(0, 200)}`);
    return text ? JSON.parse(text) : null;
  }

  /** A one-time link that signs the window in (valid ten minutes, used up by the first load). */
  async loginURL() {
    const r = await this.api('POST', '/api/login/code', { local: true });
    if (!r || !r.code) throw new Error('the node did not give a sign-in code');
    return `${this.origin}/?t=${encodeURIComponent(r.code)}`;
  }

  /** Stop the node we started: close its stdin (a clean exit), then insist. */
  async stop() {
    this.stopping = true;
    const c = this.child;
    if (c && !this.exited) {
      try {
        c.stdin.end();
      } catch {
        /* already closed */
      }
      await Promise.race([once(c, 'exit').catch(() => {}), sleep(6000)]);
      if (!this.exited) {
        log.warn('core: did not exit by itself, terminating');
        c.kill();
        await Promise.race([once(c, 'exit').catch(() => {}), sleep(3000)]);
      }
      if (!this.exited && process.platform !== 'win32') c.kill('SIGKILL');
    }
    this.child = null;
    if (this.status !== 'failed') this.setStatus('stopped');
  }
}

module.exports = { Core, coreEnv, freePort, handshakeProof, verifyNode, DEFAULT_PORT };
