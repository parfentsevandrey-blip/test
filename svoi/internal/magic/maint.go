package magic

import (
	"net/netip"
	"time"
)

// maintLoop drives probing, keepalives, path expiry and state gossip. It is
// deliberately a single polling loop: the work per tick is tiny (a handful of
// peers) and the logic stays easy to reason about.
func (c *Conn) maintLoop() {
	defer c.wg.Done()
	t := time.NewTicker(c.cfg.Timing.Tick)
	defer t.Stop()
	var lastState time.Time
	for {
		select {
		case <-t.C:
		case <-c.kick:
		case <-c.ctx.Done():
			return
		}
		now := time.Now()
		for _, p := range c.allPeers() {
			c.maintainPeer(p, now)
		}
		// Relay choices depend on paths and gossip that change over time.
		c.recomputeRelays()

		// Gossip our state when it changed (debounced to once a second) and
		// otherwise every StateEvery.
		dirty := c.self.stateDirty.Load()
		if (dirty && now.Sub(lastState) >= time.Second) || now.Sub(lastState) >= c.cfg.Timing.StateEvery {
			c.self.stateDirty.Store(false)
			lastState = now
			c.broadcastState()
		}
	}
}

func (c *Conn) maintainPeer(p *peer, now time.Time) {
	t := &c.cfg.Timing
	p.mu.Lock()
	expired := false
	if p.direct.IsValid() && now.Sub(p.directPong) > t.PathTTL {
		p.direct = netip.AddrPort{}
		expired = true
	}
	_, dvalid := p.directLocked(now, t.PathTTL)
	want := now.Before(p.wantUntil)
	var doProbe, doKeepalive bool
	if !dvalid {
		interval := t.ProbeSlow
		if want {
			interval = t.ProbeFast
		}
		if !now.Before(p.nextProbe) {
			doProbe = true
			p.nextProbe = now.Add(interval)
		}
	} else {
		ka := t.KeepAliveIdle
		if want {
			ka = t.KeepAlive
		}
		if now.Sub(p.lastKeepalive) >= ka {
			doKeepalive = true
			p.lastKeepalive = now
		}
		// Now and then look again at every candidate: a LAN path may have appeared.
		if now.Sub(p.lastFullProbe) >= 30*time.Second {
			doProbe = true
			p.lastFullProbe = now
		}
	}
	direct := p.direct
	p.mu.Unlock()

	if expired {
		c.markStateDirty()
		c.notifyPath(p)
	}
	if doKeepalive && direct.IsValid() {
		c.sendPing(p, direct, nil)
	}
	if doProbe {
		c.probe(p, now)
	}
}

// probe pings every candidate of p and, when there is no direct path yet but a
// relay exists, also asks the peer through the relay to probe us back.
func (c *Conn) probe(p *peer, now time.Time) {
	cands := p.candidateList()
	for _, ap := range cands {
		p.mu.Lock()
		cd := p.cands[ap]
		ok := cd != nil && now.Sub(cd.lastPing) >= 300*time.Millisecond
		if ok {
			cd.lastPing = now
		}
		p.mu.Unlock()
		if ok {
			c.sendPing(p, ap, nil)
		}
	}

	p.mu.Lock()
	_, dvalid := p.directLocked(now, c.cfg.Timing.PathTTL)
	relay := p.relayVia
	doCMM := !dvalid && relay != nil && now.Sub(p.lastCMM) >= c.cfg.Timing.CallMeMaybe
	if doCMM {
		p.lastCMM = now
	}
	p.mu.Unlock()
	if doCMM {
		c.sendCallMeMaybe(p, relay)
		c.sendPing(p, netip.AddrPort{}, relay) // measures the relayed path
	}
}

func (c *Conn) markStateDirty() { c.self.stateDirty.Store(true) }

// StateChanged tells magic that something peers learn from our state gossip (such
// as whether we relay) changed, so it is sent again right away.
func (c *Conn) StateChanged() {
	c.markStateDirty()
	c.Kick()
}

// broadcastState sends our peer-state to every peer we have a direct path to.
func (c *Conn) broadcastState() {
	now := time.Now()
	for _, p := range c.allPeers() {
		p.mu.Lock()
		addr, ok := p.directLocked(now, c.cfg.Timing.PathTTL)
		p.mu.Unlock()
		if ok {
			c.sendPeerState(p, addr)
		}
	}
}
