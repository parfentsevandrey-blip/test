package magic

import (
	"net/netip"
	"sort"
	"sync"
	"sync/atomic"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
)

// selfState tracks what we know about our own reachability: interface
// addresses, what STUN servers saw, and what other members saw.
type selfState struct {
	mu sync.Mutex

	local     []netip.Addr
	stun      map[string]stunResult
	stunTx    map[stunTxID]string
	resolved  map[string]resolvedEntry
	observed  map[netip.AddrPort]*obsInfo
	mapped    netip.AddrPort // what the router forwards to us (zero: nothing)
	lastEps   []Endpoint
	lastCheck time.Time

	stateDirty   atomic.Bool
	relayedPkts  atomic.Uint64
	relayedBytes atomic.Uint64
}

type stunResult struct {
	ap netip.AddrPort
	at time.Time
}

type resolvedEntry struct {
	addrs []netip.AddrPort
	at    time.Time
}

type obsInfo struct {
	last      time.Time
	reporters map[identity.ID]time.Time
}

func (s *selfState) init() {
	s.stun = map[string]stunResult{}
	s.stunTx = map[stunTxID]string{}
	s.resolved = map[string]resolvedEntry{}
	s.observed = map[netip.AddrPort]*obsInfo{}
}

const (
	observedTTL = 3 * time.Minute
	maxObserved = 32 // addresses remembered at a time
)

// noteObserved records that peer `by` saw our packets coming from ap.
func (c *Conn) noteObserved(ap netip.AddrPort, by identity.ID) {
	ap = normalize(ap)
	if !usableCandidate(ap) {
		return
	}
	s := &c.self
	s.mu.Lock()
	s.recordObservedLocked(ap, by, time.Now())
	s.mu.Unlock()
	c.refreshEndpoints()
}

// recordObservedLocked notes that `by` saw us at ap and keeps the table small: a
// member that reports endless made-up addresses must not make it grow for ever.
func (s *selfState) recordObservedLocked(ap netip.AddrPort, by identity.ID, now time.Time) {
	o := s.observed[ap]
	if o == nil {
		if len(s.observed) >= maxObserved {
			s.evictObservedLocked(now)
		}
		o = &obsInfo{reporters: map[identity.ID]time.Time{}}
		s.observed[ap] = o
	}
	o.last = now
	o.reporters[by] = now
}

// evictObservedLocked drops what has gone stale and, if that is not enough, the
// address vouched for by the fewest members (then the oldest): a real address
// is seen by several peers, a made-up one by one.
func (s *selfState) evictObservedLocked(now time.Time) {
	for ap, o := range s.observed {
		if now.Sub(o.last) > observedTTL {
			delete(s.observed, ap)
		}
	}
	for len(s.observed) >= maxObserved {
		var victim netip.AddrPort
		var vv int
		var vl time.Time
		first := true
		for ap, o := range s.observed {
			votes := 0
			for _, t := range o.reporters {
				if now.Sub(t) <= observedTTL {
					votes++
				}
			}
			if first || votes < vv || (votes == vv && o.last.Before(vl)) {
				victim, vv, vl, first = ap, votes, o.last, false
			}
		}
		delete(s.observed, victim)
	}
}

// Endpoints returns our current reachable-at addresses, best first.
func (c *Conn) Endpoints() []Endpoint {
	s := &c.self
	s.mu.Lock()
	defer s.mu.Unlock()
	return c.endpointsLocked(time.Now())
}

func (c *Conn) endpointsLocked(now time.Time) []Endpoint {
	s := &c.self
	var out []Endpoint
	seen := map[netip.AddrPort]bool{}
	add := func(ap netip.AddrPort, k EndpointKind) {
		ap = normalize(ap)
		if !ap.IsValid() || seen[ap] {
			return
		}
		seen[ap] = true
		out = append(out, Endpoint{Addr: ap, Kind: k})
	}
	// An address the router forwards to us is the best one there is: it works for
	// anybody, no hole punching needed.
	if s.mapped.IsValid() {
		add(s.mapped, EPMapped)
	}
	// Public endpoints first: they are the ones remote peers can use.
	type scored struct {
		ap    netip.AddrPort
		votes int
		last  time.Time
	}
	var obs []scored
	for ap, o := range s.observed {
		if now.Sub(o.last) > observedTTL {
			continue
		}
		votes := 0
		for _, t := range o.reporters {
			if now.Sub(t) <= observedTTL {
				votes++
			}
		}
		obs = append(obs, scored{ap, votes, o.last})
	}
	sort.Slice(obs, func(i, j int) bool {
		if obs[i].votes != obs[j].votes {
			return obs[i].votes > obs[j].votes
		}
		return obs[i].last.After(obs[j].last)
	})
	for i, o := range obs {
		if i >= 4 {
			break
		}
		add(o.ap, EPObserved)
	}
	var stuns []netip.AddrPort
	for _, r := range s.stun {
		if now.Sub(r.at) <= observedTTL {
			stuns = append(stuns, r.ap)
		}
	}
	sort.Slice(stuns, func(i, j int) bool { return stuns[i].String() < stuns[j].String() })
	for _, ap := range stuns {
		add(ap, EPSTUN)
	}
	for _, a := range s.local {
		add(netip.AddrPortFrom(a, c.port), EPLocal)
	}
	return out
}

// NATReport summarises what we have learned about our NAT situation.
type NATReport struct {
	// MappingVaries is true when different peers/servers saw different external
	// ports for the same socket: a symmetric (hard) NAT, which defeats hole punching.
	MappingVaries bool
	// Public lists our public endpoints (observed by peers or STUN).
	Public []netip.AddrPort
	// HasIPv6 is true if we have a global IPv6 address.
	HasIPv6 bool
	// STUNWorks is true if at least one STUN server answered recently.
	STUNWorks bool
	// Mapped is the public address the router forwards to us (UPnP / NAT-PMP), if any.
	Mapped netip.AddrPort
}

// NAT returns the current NAT diagnosis.
func (c *Conn) NAT() NATReport {
	now := time.Now()
	s := &c.self
	s.mu.Lock()
	defer s.mu.Unlock()
	var rep NATReport
	rep.Mapped = s.mapped
	portsByIP := map[netip.Addr]map[uint16]bool{}
	note := func(ap netip.AddrPort) {
		ip := ap.Addr()
		if portsByIP[ip] == nil {
			portsByIP[ip] = map[uint16]bool{}
		}
		portsByIP[ip][ap.Port()] = true
	}
	for ap, o := range s.observed {
		if now.Sub(o.last) <= time.Minute && !isPrivateAddr(ap.Addr()) {
			note(ap)
			rep.Public = append(rep.Public, ap)
		}
	}
	for _, r := range s.stun {
		if now.Sub(r.at) <= time.Minute {
			rep.STUNWorks = true
			if isPrivateAddr(r.ap.Addr()) {
				continue
			}
			note(r.ap)
			if !contains(rep.Public, r.ap) {
				rep.Public = append(rep.Public, r.ap)
			}
		}
	}
	for _, ports := range portsByIP {
		if len(ports) > 1 {
			rep.MappingVaries = true
		}
	}
	for _, a := range s.local {
		if a.Is6() && !a.IsPrivate() {
			rep.HasIPv6 = true
		}
	}
	sort.Slice(rep.Public, func(i, j int) bool { return rep.Public[i].String() < rep.Public[j].String() })
	return rep
}

func contains(list []netip.AddrPort, a netip.AddrPort) bool {
	for _, x := range list {
		if x == a {
			return true
		}
	}
	return false
}

// refreshEndpoints recomputes our endpoint list and, if it changed, notifies
// the owner and schedules a peer-state broadcast.
func (c *Conn) refreshEndpoints() {
	s := &c.self
	s.mu.Lock()
	eps := c.endpointsLocked(time.Now())
	changed := len(eps) != len(s.lastEps)
	if !changed {
		for i := range eps {
			if eps[i] != s.lastEps[i] {
				changed = true
				break
			}
		}
	}
	if changed {
		s.lastEps = eps
	}
	s.mu.Unlock()
	if changed {
		c.markStateDirty()
		if c.cfg.OnEndpoints != nil {
			c.cfg.OnEndpoints(eps)
		}
	}
}

func sameAddrs(a, b []netip.Addr) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}

// endpointLoop watches for network changes and runs STUN.
func (c *Conn) endpointLoop() {
	defer c.wg.Done()
	t := &c.cfg.Timing
	check := func(force bool) {
		addrs := c.cfg.LocalAddrs()
		sort.Slice(addrs, func(i, j int) bool { return addrs[i].Less(addrs[j]) })
		s := &c.self
		s.mu.Lock()
		changed := !sameAddrs(addrs, s.local)
		s.local = addrs
		s.mu.Unlock()
		c.refreshEndpoints()
		if changed && c.portmap != nil {
			c.portmap.Poke() // a new network: look for its router now
		}
		if changed || force {
			if changed {
				// The network changed under us: existing mappings may be dead.
				for _, p := range c.allPeers() {
					p.mu.Lock()
					p.nextProbe = time.Now()
					p.mu.Unlock()
				}
				c.Kick()
			}
			c.runSTUN()
		}
	}
	check(true)
	netTick := time.NewTicker(t.NetCheckEvery)
	stunTick := time.NewTicker(t.STUNEvery)
	defer netTick.Stop()
	defer stunTick.Stop()
	for {
		select {
		case <-netTick.C:
			check(false)
		case <-stunTick.C:
			c.runSTUN()
		case <-c.ctx.Done():
			return
		}
	}
}

// runSTUN sends a binding request to every configured server. Each server is
// handled in its own goroutine so a slow or broken DNS never stalls the loop.
func (c *Conn) runSTUN() {
	for _, server := range c.cfg.STUN {
		go func(server string) {
			for _, ap := range c.resolveSTUNServer(server) {
				tx, req := newStunRequest()
				c.self.mu.Lock()
				if len(c.self.stunTx) > 64 {
					c.self.stunTx = map[stunTxID]string{}
				}
				c.self.stunTx[tx] = server + "|" + ap.String()
				c.self.mu.Unlock()
				_, _ = c.sock.WriteToAddrPort(req, ap)
			}
		}(server)
	}
}

func (c *Conn) resolveSTUNServer(server string) []netip.AddrPort {
	s := &c.self
	s.mu.Lock()
	e, ok := s.resolved[server]
	s.mu.Unlock()
	if ok && time.Since(e.at) < 10*time.Minute {
		return e.addrs
	}
	addrs, err := c.cfg.ResolveSTUN(server)
	if err != nil {
		c.cfg.Logf("magic: cannot resolve STUN server %s: %v", server, err)
		return e.addrs // stale is better than nothing
	}
	s.mu.Lock()
	s.resolved[server] = resolvedEntry{addrs: addrs, at: time.Now()}
	s.mu.Unlock()
	return addrs
}

func (c *Conn) handleSTUN(pkt []byte, from netip.AddrPort) {
	tx, ap, err := parseStunResponse(pkt)
	if err != nil {
		return
	}
	s := &c.self
	s.mu.Lock()
	key, ok := s.stunTx[tx]
	if ok {
		delete(s.stunTx, tx)
		// key is "server|ip:port"; remember the result per server address so two
		// servers disagreeing on our port reveals a symmetric NAT.
		s.stun[key] = stunResult{ap: normalize(ap), at: time.Now()}
	}
	s.mu.Unlock()
	if ok {
		c.refreshEndpoints()
	}
	_ = from
}

// NetCheck re-runs address discovery now: STUN, interface scan and a fresh
// round of probes to every peer. Used by the "check network" button.
func (c *Conn) NetCheck() {
	addrs := c.cfg.LocalAddrs()
	sort.Slice(addrs, func(i, j int) bool { return addrs[i].Less(addrs[j]) })
	c.self.mu.Lock()
	c.self.local = addrs
	c.self.mu.Unlock()
	c.refreshEndpoints()
	if c.portmap != nil {
		c.portmap.Poke()
	}
	go c.runSTUN()
	for _, p := range c.allPeers() {
		p.mu.Lock()
		p.nextProbe = time.Now()
		p.lastFullProbe = time.Time{}
		p.mu.Unlock()
	}
	c.Kick()
}
