'use strict';
// macOS (15 and later) lets a program use the local network only with the person's consent ("Local Network" in
// Privacy & Security). Without it the node cannot announce itself to the other devices on the home Wi-Fi: its
// sends fail, and it says so in its state (self.lan.problem === 'blocked'). The shell then tells the person, once
// in a while and in plain words, and opens the right page of System Settings.

const SETTINGS_URL = 'x-apple.systempreferences:com.apple.preference.security?Privacy_LocalNetwork';
// The system asks its own question the first time a program touches the local network, and the node says
// "blocked" until it is answered: give the person time to answer it before adding a question of ours.
const SETTLE_MS = 45_000;
// After "Later" do not ask again for three days (the Home screen keeps showing the notice meanwhile).
const REASK_MS = 3 * 24 * 3600 * 1000;

class LocalNetworkWatch {
  constructor(platform = process.platform) {
    this.platform = platform;
    this.since = 0; // when the node was first seen reporting "blocked" (0: it is not)
  }

  /**
   * @param {{self?: {lan?: {problem?: string}}}|null} state  what the node reports
   * @param {number} askedAt  when the person was last asked (0: never)
   * @param {number} now
   * @returns {{action: 'none'|'wait'|'ask', ms?: number}}  'wait': look again after ms
   */
  check(state, askedAt, now) {
    const lan = state && state.self && state.self.lan;
    if (this.platform !== 'darwin' || !lan || lan.problem !== 'blocked') {
      this.since = 0;
      return { action: 'none' };
    }
    if (askedAt && now - askedAt < REASK_MS) return { action: 'none' };
    if (!this.since) this.since = now;
    const wait = this.since + SETTLE_MS - now;
    return wait > 0 ? { action: 'wait', ms: wait } : { action: 'ask' };
  }

  asked() {
    this.since = 0;
  }
}

module.exports = { LocalNetworkWatch, SETTINGS_URL, SETTLE_MS, REASK_MS };
