package mesh

import (
	"net"
	"net/netip"
	"sync"
	"time"
)

// Join handshakes come from strangers by definition, so the only thing known
// about one is its network address (QUIC Retry has already shown that it is
// real). Each address gets its own small budget: somebody hammering the port
// cannot use up the budget of the person who is holding the invitation. A global
// ceiling still protects the CPU against many addresses at once.
const (
	joinPerAddrRate  = 0.5 // sustained handshakes per second from one address
	joinPerAddrBurst = 4
	joinGlobalRate   = 20
	joinGlobalBurst  = 40
	joinMaxTracked   = 1024 // addresses remembered at a time
	joinForgetAfter  = 30 * time.Second
)

type joinLimiter struct {
	mu        sync.Mutex
	global    tokenGate
	per       map[netip.Addr]*addrGate
	lastSweep time.Time
}

type addrGate struct {
	gate tokenGate
	last time.Time
}

func newJoinLimiter() *joinLimiter {
	return &joinLimiter{global: newTokenGate(joinGlobalRate, joinGlobalBurst), per: map[netip.Addr]*addrGate{}}
}

// allow reports whether a join handshake from addr may proceed.
func (l *joinLimiter) allow(addr netip.Addr) bool {
	addr = addr.Unmap()
	if addr.IsValid() {
		now := time.Now()
		l.mu.Lock()
		g := l.per[addr]
		if g == nil {
			if len(l.per) >= joinMaxTracked && now.Sub(l.lastSweep) >= time.Second {
				l.lastSweep = now // (a full table is scanned at most once a second)
				l.forgetIdleLocked(now)
			}
			if len(l.per) < joinMaxTracked {
				g = &addrGate{gate: newTokenGate(joinPerAddrRate, joinPerAddrBurst)}
				l.per[addr] = g
			} // else: too many strangers at once; only the global ceiling applies
		}
		if g != nil {
			g.last = now
			if !g.gate.allow() {
				l.mu.Unlock()
				return false
			}
		}
		l.mu.Unlock()
	}
	return l.global.allow()
}

func (l *joinLimiter) forgetIdleLocked(now time.Time) {
	for a, g := range l.per {
		if now.Sub(g.last) > joinForgetAfter {
			delete(l.per, a)
		}
	}
}

// remoteAddr extracts the peer's IP from a net.Addr (UDP or anything printable).
func remoteAddr(a net.Addr) netip.Addr {
	if a == nil {
		return netip.Addr{}
	}
	if u, ok := a.(*net.UDPAddr); ok {
		ip, _ := netip.AddrFromSlice(u.IP)
		return ip.Unmap()
	}
	if ap, err := netip.ParseAddrPort(a.String()); err == nil {
		return ap.Addr().Unmap()
	}
	return netip.Addr{}
}
