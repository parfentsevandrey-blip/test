package magic

import (
	"crypto/rand"
	"net/netip"
	"time"

	"golang.org/x/crypto/nacl/box"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
)

// Disco ("discovery") messages are tiny authenticated packets members exchange
// to find a working path to each other. They are sealed with a NaCl box between
// the two device keys, so a relay forwarding them can neither read nor forge
// them, and strangers who scan our UDP port get no answer at all.

// sealDisco builds a complete disco packet addressed to peer p.
func (c *Conn) sealDisco(p *peer, payload []byte) []byte {
	var nonce [24]byte
	_, _ = rand.Read(nonce[:])
	out := make([]byte, 0, discoHeaderLen+len(payload)+box.Overhead)
	out = append(out, typeDisco)
	out = append(out, c.dev.ID[:]...)
	out = append(out, nonce[:]...)
	return box.SealAfterPrecomputation(out, payload, &nonce, &p.shared)
}

// sendDisco sends a disco payload to p, either straight to a physical address
// or wrapped for a relay member.
func (c *Conn) sendDisco(p *peer, payload []byte, to netip.AddrPort, via *peer) {
	pkt := c.sealDisco(p, payload)
	if via == nil {
		if to.IsValid() {
			_, _ = c.sock.WriteToAddrPort(pkt, to)
		}
		return
	}
	buf := make([]byte, 0, relayHeader+len(pkt))
	c.sendRelayed(via, p, pkt, buf)
}

// sendPing probes one candidate address of p. withCert attaches our member
// certificate so a peer that has never heard of us can verify and accept us.
func (c *Conn) sendPing(p *peer, to netip.AddrPort, via *peer) {
	var tx [12]byte
	_, _ = rand.Read(tx[:])
	now := time.Now()
	rec := pingRec{to: to, sent: now}
	if via != nil {
		rec.via = via.id
	}
	p.addPending(tx, rec)

	p.mu.Lock()
	needCert := !p.heard
	// A node that has just started has not heard the peer's state yet (which
	// members it can reach directly, whether it relays): ask for it right away
	// instead of waiting for the next periodic gossip.
	wantState := p.theirStateAt.IsZero() || now.Sub(p.theirStateAt) > 2*c.cfg.Timing.StateEvery
	p.mu.Unlock()

	w := wbuf{}
	w.u8(msgPing)
	w.bytes(tx[:])
	var cert []byte
	if needCert && c.cfg.SelfCert != nil {
		cert = c.cfg.SelfCert()
	}
	var flags byte
	if len(cert) > 0 && len(cert) < 1000 {
		flags |= pingFlagCert
	}
	if wantState {
		flags |= pingFlagWantState
	}
	w.u8(flags)
	if flags&pingFlagCert != 0 {
		w.u16(uint16(len(cert)))
		w.bytes(cert)
	}
	c.sendDisco(p, w.b, to, via)
}

// sendCallMeMaybe asks p (through relay) to start probing our endpoints.
func (c *Conn) sendCallMeMaybe(p *peer, via *peer) {
	w := wbuf{}
	w.u8(msgCallMeMaybe)
	p.mu.Lock()
	needCert := !p.heard
	p.mu.Unlock()
	var cert []byte
	if needCert && c.cfg.SelfCert != nil {
		cert = c.cfg.SelfCert()
	}
	if len(cert) > 0 && len(cert) < 1000 {
		w.u8(1)
		w.u16(uint16(len(cert)))
		w.bytes(cert)
	} else {
		w.u8(0)
	}
	eps := c.Endpoints()
	list := make([]netip.AddrPort, 0, len(eps))
	for _, e := range eps {
		list = append(list, e.Addr)
	}
	w.aps(list)
	c.sendDisco(p, w.b, netip.AddrPort{}, via)
}

// sendPeerState gossips our endpoints, relay willingness and the set of peers
// we have direct paths to. Receivers use it to choose relays and to learn
// where to reach us.
func (c *Conn) sendPeerState(p *peer, to netip.AddrPort) {
	w := wbuf{}
	w.u8(msgPeerState)
	var flags byte
	if c.cfg.AllowRelay() {
		flags |= 1
	}
	w.u8(flags)
	eps := c.Endpoints()
	list := make([]netip.AddrPort, 0, len(eps))
	for _, e := range eps {
		list = append(list, e.Addr)
	}
	w.aps(list)

	now := time.Now()
	var direct [][8]byte
	for _, q := range c.allPeers() {
		q.mu.Lock()
		if _, ok := q.directLocked(now, c.cfg.Timing.PathTTL); ok {
			direct = append(direct, q.r8)
		}
		q.mu.Unlock()
	}
	if len(direct) > 200 {
		direct = direct[:200]
	}
	c.cfg.Logf("magic: sending peer-state to %s: direct=%d", p.id.Short(), len(direct))
	w.u8(byte(len(direct)))
	for _, d := range direct {
		w.bytes(d[:])
	}
	c.sendDisco(p, w.b, to, nil)
}

// handleDisco processes a disco packet. via is the relay it arrived through
// (nil if it came straight from `from`).
func (c *Conn) handleDisco(pkt []byte, from netip.AddrPort, via *peer) {
	if len(pkt) < discoHeaderLen+box.Overhead {
		return
	}
	var sender identity.ID
	copy(sender[:], pkt[1:33])
	if sender == c.dev.ID {
		return
	}
	var nonce [24]byte
	copy(nonce[:], pkt[33:57])

	p := c.peerByID(sender)
	var shared [32]byte
	if p != nil {
		shared = p.shared
	} else {
		// A stranger (or a member we have not met yet). Opening costs a curve
		// operation, so rate-limit work on behalf of unknown senders.
		if !c.unknownTokens.allow() {
			return
		}
		pub, err := identity.IDToX25519(sender)
		if err != nil {
			return
		}
		box.Precompute(&shared, &pub, &c.xpriv)
	}
	payload, ok := box.OpenAfterPrecomputation(nil, pkt[discoHeaderLen:], &nonce, &shared)
	if !ok || len(payload) == 0 {
		return
	}
	r := rbuf{b: payload[1:]}
	kind := payload[0]

	// Unknown sender: only acceptable if it carries a certificate we can verify.
	if p == nil {
		var cert []byte
		switch kind {
		case msgPing:
			r.take(12)
			if r.u8()&1 != 0 {
				cert = r.take(int(r.u16()))
			}
		case msgCallMeMaybe:
			if r.u8()&1 != 0 {
				cert = r.take(int(r.u16()))
			}
		default:
			return
		}
		if r.err != nil || len(cert) == 0 || c.cfg.AcceptUnknown == nil || !c.cfg.AcceptUnknown(sender, cert) {
			return
		}
		if p = c.peerByID(sender); p == nil {
			return
		}
		r = rbuf{b: payload[1:]} // re-parse from the start below
	}

	now := time.Now()
	p.mu.Lock()
	p.lastHeard = now
	firstContact := !p.heard
	p.heard = true
	p.mu.Unlock()
	_ = firstContact

	switch kind {
	case msgPing:
		c.onPing(p, &r, from, via)
	case msgPong:
		c.onPong(p, &r, from, via)
	case msgCallMeMaybe:
		c.onCallMeMaybe(p, &r, via)
	case msgPeerState:
		c.onPeerState(p, &r)
	}
}

func (c *Conn) onPing(p *peer, r *rbuf, from netip.AddrPort, via *peer) {
	var tx [12]byte
	copy(tx[:], r.take(12))
	flags := r.u8()
	if flags&pingFlagCert != 0 { // skip a certificate we may already have processed
		r.take(int(r.u16()))
	}
	if r.err != nil {
		return
	}
	w := wbuf{}
	w.u8(msgPong)
	w.bytes(tx[:])
	if via == nil {
		w.ap(from)
		c.sendDisco(p, w.b, from, nil)
		// Hole punching reflex: a verified packet from `from` means that path is
		// worth probing in the other direction too.
		p.mu.Lock()
		_, known := p.cands[from]
		p.mu.Unlock()
		if !known && usableCandidate(from) {
			p.addCandidates([]netip.AddrPort{from}, SrcObserved)
		}
		now := time.Now()
		p.mu.Lock()
		cd := p.cands[from]
		needs := cd != nil && now.Sub(cd.lastPing) > 300*time.Millisecond && now.Sub(cd.lastPong) > c.cfg.Timing.KeepAlive
		if needs {
			cd.lastPing = now
		}
		p.mu.Unlock()
		if needs {
			c.sendPing(p, from, nil)
		}
		if flags&pingFlagWantState != 0 {
			p.mu.Lock()
			give := time.Since(p.lastStateSent) > 500*time.Millisecond
			if give {
				p.lastStateSent = time.Now()
			}
			p.mu.Unlock()
			if give {
				c.sendPeerState(p, from)
			}
		}
	} else {
		w.ap(netip.AddrPort{})
		c.sendDisco(p, w.b, netip.AddrPort{}, via)
	}
}

func (c *Conn) onPong(p *peer, r *rbuf, from netip.AddrPort, via *peer) {
	var tx [12]byte
	copy(tx[:], r.take(12))
	observed := r.ap()
	if r.err != nil {
		return
	}
	rec, ok := p.takePending(tx)
	if !ok {
		return
	}
	now := time.Now()
	rtt := now.Sub(rec.sent)
	if via != nil {
		if rec.via != via.id {
			return
		}
		p.mu.Lock()
		p.relayOK = now
		p.relayRTT = rtt
		p.mu.Unlock()
		return
	}
	if rec.via != (identity.ID{}) || rec.to != from {
		return // reply from an address we did not ping: ignore
	}
	p.mu.Lock()
	switched := p.confirmLocked(from, rtt, now)
	p.mu.Unlock()
	if observed.IsValid() {
		c.noteObserved(observed, p.id)
	}
	if switched {
		c.markStateDirty()
		c.recomputeRelays()
		c.notifyPath(p)
	}
}

func (c *Conn) onCallMeMaybe(p *peer, r *rbuf, via *peer) {
	if r.u8()&1 != 0 {
		r.take(int(r.u16()))
	}
	eps := r.aps()
	if r.err != nil {
		return
	}
	if p.addCandidates(eps, SrcGossip) && c.cfg.OnPeerEndpoints != nil {
		c.cfg.OnPeerEndpoints(p.id, eps)
	}
	// Start probing right away: our packets open our NAT towards them while
	// theirs (triggered by our own call-me-maybe) open theirs.
	p.mu.Lock()
	p.wantUntil = time.Now().Add(c.cfg.Timing.WantFor)
	p.nextProbe = time.Now()
	reply := via != nil && time.Since(p.lastCMM) > c.cfg.Timing.CallMeMaybe
	if reply {
		p.lastCMM = time.Now()
	}
	p.mu.Unlock()
	if reply {
		c.sendCallMeMaybe(p, via)
	}
	c.Kick()
}

func (c *Conn) onPeerState(p *peer, r *rbuf) {
	flags := r.u8()
	eps := r.aps()
	n := int(r.u8())
	direct := make(map[[8]byte]bool, n)
	for i := 0; i < n && r.err == nil; i++ {
		var d [8]byte
		copy(d[:], r.take(8))
		direct[d] = true
	}
	if r.err != nil {
		return
	}
	p.mu.Lock()
	p.theirCanRelay = flags&1 != 0
	p.theirDirect = direct
	p.theirStateAt = time.Now()
	p.mu.Unlock()
	c.cfg.Logf("magic: peer-state from %s: canRelay=%v direct=%d endpoints=%d", p.id.Short(), flags&1 != 0, len(direct), len(eps))
	if p.addCandidates(eps, SrcGossip) && c.cfg.OnPeerEndpoints != nil {
		c.cfg.OnPeerEndpoints(p.id, eps)
	}
	c.recomputeRelays()
}
