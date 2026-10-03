'use strict';
// One short sentence about the state of the network, for the tray tooltip and menu.

/**
 * @param {object} t        the shell's texts
 * @param {string} coreStatus  idle | starting | running | failed | stopped
 * @param {{self: object|null, peers: object[]}} state
 */
function statusText(t, coreStatus, state) {
  if (coreStatus === 'starting' || coreStatus === 'idle') return t.statusStarting;
  if (coreStatus !== 'running') return t.statusStopped;
  const self = state && state.self;
  if (!self || self.configured === false) return t.statusNoNetwork;
  const peers = (state && state.peers) || [];
  if (!peers.length) return t.statusAlone;
  const online = peers.filter((p) => p.online).length + 1;
  return t.statusOnline(online, peers.length + 1);
}

module.exports = { statusText };
