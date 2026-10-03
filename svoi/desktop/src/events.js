'use strict';
// A live connection to the node's event stream (/api/events), the same one the interface uses.
// The shell needs it for the tray status and for system notifications while the window is closed.
const { EventEmitter } = require('node:events');
const log = require('./log');

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** Parse a text/event-stream body into { event, data } messages. */
async function* parseSSE(body) {
  const dec = new TextDecoder();
  let buf = '';
  for await (const chunk of body) {
    buf += dec.decode(chunk, { stream: true }).replace(/\r\n/g, '\n');
    for (;;) {
      const i = buf.indexOf('\n\n');
      if (i < 0) break;
      const raw = buf.slice(0, i);
      buf = buf.slice(i + 2);
      let event = 'message';
      const data = [];
      for (const line of raw.split('\n')) {
        if (!line || line.startsWith(':')) continue;
        if (line.startsWith('event:')) event = line.slice(6).trim();
        else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''));
      }
      if (data.length) yield { event, data: data.join('\n') };
    }
  }
}

class Watcher extends EventEmitter {
  /** @param {import('./core').Core} core */
  constructor(core) {
    super();
    this.core = core;
    this.state = { self: null, peers: [], counters: { mail: 0, chat: 0, offers: 0 } };
    this.stopped = true;
    this.ctl = null;
    this.connected = false;
  }

  start() {
    if (!this.stopped) return;
    this.stopped = false;
    this.run().catch((e) => log.error('events: loop ended', e));
  }

  stop() {
    this.stopped = true;
    if (this.ctl) this.ctl.abort();
  }

  async run() {
    let delay = 500;
    while (!this.stopped) {
      try {
        this.ctl = new AbortController();
        const res = await fetch(this.core.origin + '/api/events', {
          headers: { Authorization: 'Bearer ' + this.core.token, Accept: 'text/event-stream' },
          redirect: 'error',
          signal: this.ctl.signal,
        });
        if (!res.ok) throw new Error('HTTP ' + res.status);
        // Like the interface: take a full snapshot first, then follow the changes.
        try {
          const s = await this.core.api('GET', '/api/state');
          this.state.self = s.self || null;
          this.state.peers = Array.isArray(s.peers) ? s.peers : [];
          this.state.counters = { mail: 0, chat: 0, offers: 0, ...(s.counters || {}) };
          this.emit('state', this.state);
        } catch (e) {
          log.warn('events: cannot read the state', e.message);
        }
        this.connected = true;
        delay = 500;
        for await (const m of parseSSE(res.body)) this.dispatch(m);
      } catch (e) {
        if (this.stopped) return;
        log.warn('events: connection lost:', e.message || e);
      }
      this.connected = false;
      this.emit('disconnected');
      await sleep(delay);
      delay = Math.min(delay * 2, 15000);
    }
  }

  dispatch({ event, data }) {
    let d;
    try {
      d = JSON.parse(data);
    } catch {
      return;
    }
    switch (event) {
      case 'self':
        this.state.self = d;
        this.emit('state', this.state);
        break;
      case 'peers':
        if (Array.isArray(d)) {
          this.state.peers = d;
          this.emit('state', this.state);
        }
        break;
      case 'counters':
        this.state.counters = { ...this.state.counters, ...d };
        this.emit('state', this.state);
        break;
      default:
        this.emit(event, d);
    }
  }
}

module.exports = { Watcher, parseSSE };
