package magic

import (
	"net/netip"
	"sync"
	"time"
)

// anonGate limits the packets of strangers, that is of joiners that are not
// members yet. A joiner needs a few dozen packets; a flood of made-up QUIC
// Initials would otherwise cost the node a handshake's worth of work each. The
// budget is per source network (a /24, or a /64 for IPv6) and overall. Members
// use the authenticated path and are never affected: under attack it is joining
// that suffers, not the traffic of the mesh.
type anonGate struct {
	mu        sync.Mutex
	global    tokenBucket
	per       map[netip.Prefix]*netBucket
	lastSweep time.Time
}

type netBucket struct {
	b    tokenBucket
	last time.Time
}

const (
	anonPerNetRate  = 300 // packets per second from one source network
	anonPerNetBurst = 600
	anonGlobalRate  = 6000
	anonGlobalBurst = 12000
	anonMaxNets     = 2048
	anonForgetAfter = 30 * time.Second
)

func newAnonGate() *anonGate {
	return &anonGate{global: newTokenBucket(anonGlobalRate, anonGlobalBurst), per: map[netip.Prefix]*netBucket{}}
}

func netOf(a netip.Addr) netip.Prefix {
	a = a.Unmap()
	bits := 24
	if a.Is6() {
		bits = 64
	}
	p, _ := a.Prefix(bits)
	return p
}

// allow reports whether one more packet from from is accepted.
func (g *anonGate) allow(from netip.Addr) bool {
	key := netOf(from)
	now := time.Now()
	g.mu.Lock()
	e := g.per[key]
	if e == nil {
		// Forgetting idle networks walks the table, so it is done at most once a second:
		// a flood from ever new networks must not make every packet pay for a scan.
		if len(g.per) >= anonMaxNets && now.Sub(g.lastSweep) >= time.Second {
			g.lastSweep = now
			for k, v := range g.per {
				if now.Sub(v.last) > anonForgetAfter {
					delete(g.per, k)
				}
			}
		}
		if len(g.per) < anonMaxNets {
			e = &netBucket{b: newTokenBucket(anonPerNetRate, anonPerNetBurst)}
			g.per[key] = e
		} // else: only the overall budget applies
	}
	if e != nil {
		e.last = now
		if !e.b.allow() {
			g.mu.Unlock()
			return false
		}
	}
	g.mu.Unlock()
	return g.global.allow()
}
