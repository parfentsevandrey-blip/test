package magic

import (
	"net"
	"net/netip"
	"sort"
	"sync"
	"sync/atomic"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
)

const maxCandidates = 16

// cand is one address a peer might be reachable at.
type cand struct {
	ap       netip.AddrPort
	src      Source
	added    time.Time
	lastPing time.Time
	lastPong time.Time // zero until a verified round trip happened
	rtt      time.Duration
}

type pingRec struct {
	to   netip.AddrPort
	sent time.Time
	via  identity.ID // relay member the ping went through (zero = direct)
}

type pathKey struct {
	kind  PathKind
	addr  netip.AddrPort
	relay identity.ID
}

// peer is the per-member state of the path machinery.
type peer struct {
	c      *Conn
	id     identity.ID
	r8     [8]byte
	xpub   [32]byte
	shared [32]byte // NaCl box precomputed key
	macKey [32]byte // relay envelope MAC key
	vaddr  *net.UDPAddr
	vap    netip.AddrPort

	mu            sync.Mutex
	cands         map[netip.AddrPort]*cand
	direct        netip.AddrPort // verified direct path (zero if none)
	directRTT     time.Duration
	directPong    time.Time // when `direct` was last confirmed
	relayVia      *peer
	relayRTT      time.Duration
	relayOK       time.Time // last verified round trip through the relay
	pending       map[[12]byte]pingRec
	wantUntil     time.Time
	nextProbe     time.Time
	lastKeepalive time.Time
	lastFullProbe time.Time
	lastCMM       time.Time
	lastHeard     time.Time
	lastRx        time.Time
	lastTx        time.Time
	lastRoamProbe time.Time
	lastStateSent time.Time
	heard         bool // we have authenticated something from this peer
	theirDirect   map[[8]byte]bool
	theirCanRelay bool
	theirStateAt  time.Time
	notified      pathKey

	txDirect, rxDirect, txRelay, rxRelay atomic.Uint64
}

// directLocked returns the verified direct address if it is still alive.
func (p *peer) directLocked(now time.Time, ttl time.Duration) (netip.AddrPort, bool) {
	if p.direct.IsValid() && now.Sub(p.directPong) <= ttl {
		return p.direct, true
	}
	return netip.AddrPort{}, false
}

func isPrivateAddr(a netip.Addr) bool {
	a = a.Unmap()
	return a.IsPrivate() || a.IsLoopback() || a.IsLinkLocalUnicast()
}

func (p *peer) pathLocked(now time.Time) PathInfo {
	info := PathInfo{LastHeard: p.lastHeard, Candidates: len(p.cands)}
	if ap, ok := p.directLocked(now, p.c.cfg.Timing.PathTTL); ok {
		info.Addr = ap
		info.RTT = p.directRTT
		if isPrivateAddr(ap.Addr()) {
			info.Kind = PathLAN
		} else {
			info.Kind = PathDirect
		}
		return info
	}
	if p.relayVia != nil {
		info.Kind = PathRelay
		info.Relay = p.relayVia.id
		info.RTT = p.relayRTT
	}
	return info
}

func (p *peer) keyLocked(now time.Time) pathKey {
	pi := p.pathLocked(now)
	return pathKey{kind: pi.Kind, addr: pi.Addr, relay: pi.Relay}
}

// usableCandidate filters out addresses that can never be probed usefully.
func usableCandidate(a netip.AddrPort) bool {
	ip := a.Addr().Unmap()
	if !a.IsValid() || a.Port() == 0 || ip.IsUnspecified() || ip.IsMulticast() || ip.IsLinkLocalUnicast() {
		return false
	}
	if identity.IsOverlayAddr(ip) {
		return false // never probe through our own overlay
	}
	return true
}

// addCandidates merges endpoints; it reports whether anything new was added.
func (p *peer) addCandidates(eps []netip.AddrPort, src Source) bool {
	now := time.Now()
	p.mu.Lock()
	defer p.mu.Unlock()
	added := false
	for _, ep := range eps {
		ep = normalize(ep)
		if !usableCandidate(ep) {
			continue
		}
		if c, ok := p.cands[ep]; ok {
			c.src |= src
			continue
		}
		p.evictLocked()
		p.cands[ep] = &cand{ap: ep, src: src, added: now}
		added = true
	}
	return added
}

// evictLocked makes room for one more candidate by dropping the least useful
// one: never confirmed and oldest first. Verified candidates are kept.
func (p *peer) evictLocked() {
	if len(p.cands) < maxCandidates {
		return
	}
	var victim *cand
	for _, c := range p.cands {
		if p.direct == c.ap {
			continue
		}
		if victim == nil || worse(c, victim) {
			victim = c
		}
	}
	if victim != nil {
		delete(p.cands, victim.ap)
	}
}

func worse(a, b *cand) bool {
	if a.lastPong.IsZero() != b.lastPong.IsZero() {
		return a.lastPong.IsZero()
	}
	if a.lastPong.IsZero() {
		return a.added.Before(b.added)
	}
	return a.lastPong.Before(b.lastPong)
}

// noteDirectSource is called for each data packet; if it came from an address we
// have not verified (the peer's NAT mapping changed, say) we learn it and probe.
func (p *peer) noteDirectSource(from netip.AddrPort) {
	now := time.Now()
	p.mu.Lock()
	if c, ok := p.cands[from]; ok && !c.lastPong.IsZero() {
		p.mu.Unlock()
		return
	}
	if usableCandidate(from) {
		if _, ok := p.cands[from]; !ok {
			p.evictLocked()
			p.cands[from] = &cand{ap: from, src: SrcObserved, added: now}
		}
		if now.Sub(p.lastRoamProbe) > 2*time.Second {
			p.lastRoamProbe = now
			p.nextProbe = now
			p.mu.Unlock()
			p.c.Kick()
			return
		}
	}
	p.mu.Unlock()
}

// confirmLocked records a verified round trip to ap and decides whether it
// becomes the preferred direct path. It reports whether the direct path was
// newly established or changed.
func (p *peer) confirmLocked(ap netip.AddrPort, rtt time.Duration, now time.Time) bool {
	c := p.cands[ap]
	if c == nil {
		if !usableCandidate(ap) {
			return false
		}
		p.evictLocked()
		c = &cand{ap: ap, src: SrcObserved, added: now}
		p.cands[ap] = c
	}
	c.lastPong = now
	c.rtt = rtt
	ttl := p.c.cfg.Timing.PathTTL
	cur, curOK := p.directLocked(now, ttl)
	switch {
	case !curOK:
		// no live path: take it
	case cur == ap:
		p.directPong = now
		p.directRTT = rtt
		return false
	case rtt < p.directRTT*7/10 && p.directRTT-rtt > 2*time.Millisecond:
		// clearly faster (typically LAN versus WAN): switch
	case now.Sub(p.directPong) > p.c.cfg.Timing.KeepAlive*2:
		// the current path has gone quiet; the new one is alive
	default:
		return false
	}
	p.direct = ap
	p.directRTT = rtt
	p.directPong = now
	return true
}

func (p *peer) addPending(tx [12]byte, rec pingRec) {
	p.mu.Lock()
	defer p.mu.Unlock()
	if len(p.pending) >= 128 {
		for k, v := range p.pending {
			if rec.sent.Sub(v.sent) > 15*time.Second {
				delete(p.pending, k)
			}
		}
		if len(p.pending) >= 128 {
			p.pending = map[[12]byte]pingRec{}
		}
	}
	p.pending[tx] = rec
}

func (p *peer) takePending(tx [12]byte) (pingRec, bool) {
	p.mu.Lock()
	defer p.mu.Unlock()
	rec, ok := p.pending[tx]
	if ok {
		delete(p.pending, tx)
	}
	return rec, ok
}

// snapshot of candidates sorted by usefulness (LAN first, then recent).
func (p *peer) candidateList() []netip.AddrPort {
	p.mu.Lock()
	defer p.mu.Unlock()
	list := make([]*cand, 0, len(p.cands))
	for _, c := range p.cands {
		list = append(list, c)
	}
	sort.Slice(list, func(i, j int) bool {
		pi, pj := isPrivateAddr(list[i].ap.Addr()), isPrivateAddr(list[j].ap.Addr())
		if pi != pj {
			return pi
		}
		return list[i].added.After(list[j].added)
	})
	out := make([]netip.AddrPort, len(list))
	for i, c := range list {
		out[i] = c.ap
	}
	return out
}

// ---- relay selection ----

// recomputeRelays picks, for every peer without a direct path, a member that
// announced it can relay and has a direct path to that peer.
func (c *Conn) recomputeRelays() {
	now := time.Now()
	ttl := c.cfg.Timing.PathTTL
	staleState := 3 * c.cfg.Timing.StateEvery
	type rinfo struct {
		p        *peer
		rtt      time.Duration
		canRelay bool
		direct   map[[8]byte]bool
	}
	peers := c.allPeers()
	var relays []rinfo
	for _, r := range peers {
		r.mu.Lock()
		if _, ok := r.directLocked(now, ttl); ok && r.theirCanRelay && now.Sub(r.theirStateAt) < staleState {
			cp := make(map[[8]byte]bool, len(r.theirDirect))
			for k, v := range r.theirDirect {
				cp[k] = v
			}
			relays = append(relays, rinfo{p: r, rtt: r.directRTT, canRelay: true, direct: cp})
		}
		r.mu.Unlock()
	}
	for _, b := range peers {
		var best *peer
		var bestRTT time.Duration
		for _, r := range relays {
			if r.p == b || !r.direct[b.r8] {
				continue
			}
			if best == nil || r.rtt < bestRTT {
				best, bestRTT = r.p, r.rtt
			}
		}
		b.mu.Lock()
		changed := b.relayVia != best
		if changed {
			b.relayVia = best
			b.relayRTT = 2 * bestRTT // rough: two hops, refined by relayed pongs
		}
		b.mu.Unlock()
		if changed {
			if best != nil {
				c.cfg.Logf("magic: relay for %s is now %s (%d relay candidates)", b.id.Short(), best.id.Short(), len(relays))
			} else {
				c.cfg.Logf("magic: no relay for %s any more (%d relay candidates)", b.id.Short(), len(relays))
			}
			c.notifyPath(b)
		}
	}
}

// notifyPath fires OnPath if the effective path changed since last time.
func (c *Conn) notifyPath(p *peer) {
	now := time.Now()
	p.mu.Lock()
	k := p.keyLocked(now)
	if k == p.notified {
		p.mu.Unlock()
		return
	}
	p.notified = k
	info := p.pathLocked(now)
	p.mu.Unlock()
	if c.cfg.OnPath != nil {
		c.cfg.OnPath(p.id, info)
	}
}
