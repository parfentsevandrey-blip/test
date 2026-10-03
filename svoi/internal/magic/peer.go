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

const (
	maxCandidates = 16
	maxPending    = 128 // pings sent to a peer and not answered yet
)

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
	// dataKey authenticates data packets between us and this peer. QUIC protects
	// the content, but without this anyone who knows a member's public key could
	// feed forged packets to the QUIC stack "from" that member, make us probe
	// arbitrary addresses, or evict real candidate addresses.
	dataKey [32]byte

	lastCandAdd time.Time // when an address seen as a packet's source was last taken as a candidate
	vaddr       *net.UDPAddr
	vap         netip.AddrPort

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
	nonces        nonceSet
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
		// An address learned only from the source of a packet is the cheapest to learn
		// again and the one a replayed packet can plant: it goes first.
		if ao, bo := a.src == SrcObserved, b.src == SrcObserved; ao != bo {
			return ao
		}
		return a.added.Before(b.added)
	}
	return a.lastPong.Before(b.lastPong)
}

// maxObservedCands is how many unconfirmed addresses learned from packet sources a
// peer keeps at a time.
const maxObservedCands = 4

// learnObservedLocked takes from, the source address of a packet that verified as
// ours, as a candidate. Such a packet can be replayed from anywhere, so this is
// limited: one new address a second, and at most maxObservedCands unconfirmed ones at
// a time (the oldest makes room), which leaves the addresses from gossip and the
// confirmed ones alone. It reports whether the address was added.
func (p *peer) learnObservedLocked(from netip.AddrPort, now time.Time) bool {
	from = normalize(from)
	if !usableCandidate(from) {
		return false
	}
	if _, ok := p.cands[from]; ok {
		return false
	}
	if now.Sub(p.lastCandAdd) < time.Second {
		return false
	}
	p.lastCandAdd = now
	var oldest *cand
	n := 0
	for _, c := range p.cands {
		if c.src == SrcObserved && c.lastPong.IsZero() && c.ap != p.direct {
			n++
			if oldest == nil || c.added.Before(oldest.added) {
				oldest = c
			}
		}
	}
	if n >= maxObservedCands {
		delete(p.cands, oldest.ap)
	} else {
		p.evictLocked()
	}
	p.cands[from] = &cand{ap: from, src: SrcObserved, added: now}
	return true
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
		p.learnObservedLocked(from, now)
		if _, ok := p.cands[from]; ok && now.Sub(p.lastRoamProbe) > 2*time.Second {
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
	if len(p.pending) >= maxPending {
		for k, v := range p.pending {
			if rec.sent.Sub(v.sent) > 15*time.Second {
				delete(p.pending, k)
			}
		}
		if len(p.pending) >= maxPending { // still full: the oldest probe is the one given up on
			var oldest [12]byte
			var at time.Time
			first := true
			for k, v := range p.pending {
				if first || v.sent.Before(at) {
					oldest, at, first = k, v.sent, false
				}
			}
			delete(p.pending, oldest)
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

// discoMemory is how many disco nonces are remembered per peer.
const discoMemory = 512

// nonceSet remembers the nonces of the last discoMemory disco packets accepted from a
// peer. A sealed packet that is captured and sent again (from anywhere, also from a
// spoofed address) carries a nonce we have seen and is dropped. Only packets that
// opened correctly are remembered, so the set cannot be filled with junk.
type nonceSet struct {
	seen map[[24]byte]struct{}
	ring [][24]byte
	next int
}

func (s *nonceSet) has(n [24]byte) bool {
	_, ok := s.seen[n]
	return ok
}

func (s *nonceSet) add(n [24]byte) {
	if s.seen == nil {
		s.seen = make(map[[24]byte]struct{}, 64)
	}
	if _, dup := s.seen[n]; dup {
		return
	}
	if len(s.ring) < discoMemory {
		s.ring = append(s.ring, n)
	} else {
		delete(s.seen, s.ring[s.next])
		s.ring[s.next] = n
		s.next = (s.next + 1) % discoMemory
	}
	s.seen[n] = struct{}{}
}

// discoSeen reports whether a disco packet with this nonce was already accepted.
func (p *peer) discoSeen(n [24]byte) bool {
	p.mu.Lock()
	defer p.mu.Unlock()
	return p.nonces.has(n)
}

func (p *peer) discoRemember(n [24]byte) {
	p.mu.Lock()
	p.nonces.add(n)
	p.mu.Unlock()
}
