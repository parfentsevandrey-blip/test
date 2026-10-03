package magic

import (
	"net/netip"
	"sync"
	"time"
)

// netGate limits what strangers can make a node do: a budget per source network
// (a /24, or a /64 for IPv6) and an overall one. One network that floods only
// uses up its own; a flood from very many networks is held to the overall budget.
//
// It guards the two paths open to strangers: the packets of joiners that are not
// members yet (a flood of made-up QUIC Initials would otherwise cost a handshake's
// worth of work each) and the disco packets of senders we do not know (each costs a
// curve operation to open). Members use the authenticated path and are never
// affected: under attack it is joining and first contact that suffer, not the
// traffic of the mesh.
type netGate struct {
	mu        sync.Mutex
	global    tokenBucket
	perRate   float64
	perBurst  float64
	per       map[netip.Prefix]*netBucket
	lastSweep time.Time
}

// anonGate is the netGate of joiners' packets.
type anonGate = netGate

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

	unknownPerNetRate  = 10 // disco packets per second from one network, from senders we do not know
	unknownPerNetBurst = 20
	anonForgetAfter    = 30 * time.Second
)

func newAnonGate() *anonGate {
	return newNetGate(anonPerNetRate, anonPerNetBurst, anonGlobalRate, anonGlobalBurst)
}

func newNetGate(perRate, perBurst, globalRate, globalBurst float64) *netGate {
	return &netGate{global: newTokenBucket(globalRate, globalBurst), perRate: perRate, perBurst: perBurst, per: map[netip.Prefix]*netBucket{}}
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
func (g *netGate) allow(from netip.Addr) bool {
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
			e = &netBucket{b: newTokenBucket(g.perRate, g.perBurst)}
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
