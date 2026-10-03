'use strict';
// System notifications for what happens while the window is closed or in the background:
// a file offered to this device, a file that arrived, a chat message, a letter.
// `notificationFor` is a pure function (tested without Electron); `Notifier` shows them.
const log = require('./log');

const clip = (s, n) => {
  const x = String(s || '').replace(/\s+/g, ' ').trim();
  return x.length > n ? x.slice(0, n - 1) + '…' : x;
};

/**
 * What an event deserves, or null.
 * @param {string} kind  'transfer' | 'chat' | 'mail'
 * @param {object} d     the event's data (see docs/UI-API.md)
 * @param {{t: object, peerName: (id:string)=>string|Promise<string>, fetchMail: (id:string)=>Promise<object>}} ctx
 * @returns {Promise<null | {key: string, title: string, body: string, route: string}>}
 */
async function notificationFor(kind, d, ctx) {
  const { t } = ctx;
  if (!d) return null;
  if (kind === 'transfer') {
    if (d.dir !== 'in') return null;
    const peer = d.peerName || (await ctx.peerName(d.peer)) || '';
    if (d.state === 'offered') return { key: `offer:${d.id}`, title: t.offerTitle(peer), body: t.offerBody(d.name), route: '#/home' };
    if (d.state === 'done') return { key: `done:${d.id}`, title: t.receivedTitle, body: t.receivedBody(d.name, peer), route: '#/files/send' };
    return null;
  }
  if (kind === 'chat') {
    if (d.mine !== false || !d.id) return null;
    const peer = (await ctx.peerName(d.peer)) || '';
    const body = clip(d.text, 140) || t.chatAttachment;
    return { key: `chat:${d.id}`, title: peer || 'The Mesh', body, route: `#/chat/${d.peer}` };
  }
  if (kind === 'mail') {
    if (d.folder !== 'inbox' || !d.unread || !d.id) return null;
    let m = null;
    try {
      m = await ctx.fetchMail(d.id);
    } catch {
      /* the letter may be gone already */
    }
    if (!m) return null;
    const from = (m.from && m.from.name) || '';
    return { key: `mail:${d.id}`, title: t.mailTitle(from), body: clip(m.subject, 140), route: '#/mail/inbox' };
  }
  return null;
}

class Notifier {
  /**
   * @param {object} o
   * @param {object} o.t                 the shell's texts
   * @param {import('./core').Core} o.core
   * @param {import('./events').Watcher} o.watcher
   * @param {() => boolean} o.windowActive  true while the person is looking at the window
   * @param {(route: string) => void} o.onClick
   * @param {object} o.Notification      Electron's Notification class
   * @param {string} [o.icon]
   */
  constructor(o) {
    Object.assign(this, o);
    this.seen = new Set();
    this.shown = []; // kept so that a notification is not garbage-collected before it is clicked
  }

  attach() {
    for (const kind of ['transfer', 'chat', 'mail']) this.watcher.on(kind, (d) => this.handle(kind, d).catch((e) => log.warn('notify:', e.message)));
  }

  async handle(kind, d) {
    const n = await notificationFor(kind, d, {
      t: this.t,
      peerName: async (id) => {
        let p = this.watcher.state.peers.find((x) => x.id === id);
        if (!p) {
          // a device that has only just joined can write before the list of devices reaches us
          try {
            const s = await this.core.api('GET', '/api/state');
            if (Array.isArray(s.peers)) this.watcher.state.peers = s.peers;
            p = this.watcher.state.peers.find((x) => x.id === id);
          } catch {
            /* the name is a nicety */
          }
        }
        return p ? p.name : '';
      },
      fetchMail: (id) => this.core.api('GET', `/api/mail/${encodeURIComponent(id)}`),
    });
    if (!n || this.seen.has(n.key)) return;
    this.seen.add(n.key);
    if (this.seen.size > 500) this.seen.delete(this.seen.values().next().value);
    if (this.windowActive()) return; // they are looking at it: the interface shows it itself
    if (this.onNotify) this.onNotify(n); // (tests watch what would be shown)
    if (!this.Notification.isSupported()) return;
    const note = new this.Notification({ title: n.title, body: n.body, icon: this.icon });
    note.on('click', () => this.onClick(n.route));
    note.on('close', () => (this.shown = this.shown.filter((x) => x !== note)));
    this.shown.push(note);
    if (this.shown.length > 20) this.shown.shift();
    note.show();
  }
}

module.exports = { notificationFor, Notifier, clip };
