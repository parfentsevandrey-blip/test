package mesh

import (
	"context"
	"net"
	"net/netip"
	"testing"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/netsim"
)

func TestJoinLimiterIsPerAddress(t *testing.T) {
	l := newJoinLimiter()
	evil, friend := netip.MustParseAddr("198.51.100.66"), netip.MustParseAddr("198.51.100.2")
	passed := 0
	for i := 0; i < 50; i++ {
		if l.allow(evil) {
			passed++
		}
	}
	if passed != joinPerAddrBurst {
		t.Fatalf("a hammering address got %d handshakes through, want its burst of %d", passed, joinPerAddrBurst)
	}
	if !l.allow(friend) {
		t.Fatal("another address was starved by the first one")
	}
	// IPv4-mapped IPv6 is the same address.
	if l.allow(netip.MustParseAddr("::ffff:198.51.100.66")) {
		t.Fatal("the mapped form of a throttled address got through")
	}
}

func TestJoinLimiterIsBoundedAndForgets(t *testing.T) {
	l := newJoinLimiter()
	for i := 0; i < 5*joinMaxTracked; i++ {
		a := netip.AddrFrom4([4]byte{10, byte(i >> 16), byte(i >> 8), byte(i)})
		l.allow(a)
	}
	if len(l.per) > joinMaxTracked {
		t.Fatalf("%d addresses tracked, cap is %d", len(l.per), joinMaxTracked)
	}
	// Idle entries are dropped to make room.
	for _, g := range l.per {
		g.last = time.Now().Add(-time.Hour)
	}
	l.allow(netip.MustParseAddr("203.0.113.9")) // (the answer does not matter: the global ceiling is spent)
	if len(l.per) > joinMaxTracked/2 {
		t.Fatalf("idle addresses were not forgotten (%d kept)", len(l.per))
	}
	// An unknown address still has to get past the global ceiling.
	g := newJoinLimiter()
	ok := 0
	for i := 0; i < 1000; i++ {
		if g.allow(netip.AddrFrom4([4]byte{192, 0, byte(i >> 8), byte(i)})) {
			ok++
		}
	}
	if ok > joinGlobalBurst+2 {
		t.Fatalf("%d handshakes from 1000 different addresses in an instant, ceiling is %d", ok, joinGlobalBurst)
	}
}

func TestRemoteAddr(t *testing.T) {
	if a := remoteAddr(&net.UDPAddr{IP: net.ParseIP("198.51.100.7"), Port: 4}); a != netip.MustParseAddr("198.51.100.7") {
		t.Fatalf("udp: %v", a)
	}
	if remoteAddr(nil).IsValid() {
		t.Fatal("nil gave an address")
	}
}

// Somebody hammering the join port must not be able to lock out the person who
// actually holds the invitation (the old limiter was one budget for everybody).
func TestStrangerCannotLockOutTheInvitedDevice(t *testing.T) {
	nw := netsim.New()
	a := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.1")), "alpha", nil)
	evil := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.66")), "evil", nil)
	friend := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.2")), "friend", nil)
	if err := a.CreateMesh("Home", "alpha", "x"); err != nil {
		t.Fatal(err)
	}
	inv, err := a.NewInvite(false, 5*time.Minute)
	if err != nil {
		t.Fatal(err)
	}
	parsed, err := identity.ParseInvite(inv.Code)
	if err != nil {
		t.Fatal(err)
	}
	parsed.Secret[0] ^= 0xFF // right place, wrong secret
	wrong := parsed.Encode()
	for i := 0; i < 15; i++ {
		ctx, cancel := context.WithTimeout(context.Background(), 4*time.Second)
		if err := evil.JoinMesh(ctx, wrong, "evil"); err == nil {
			t.Fatal("joined with a wrong secret")
		}
		cancel()
	}
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	if err := friend.JoinMesh(ctx, inv.Code, "friend"); err != nil {
		t.Fatalf("the invited device was locked out by a stranger's flood: %v", err)
	}
}
