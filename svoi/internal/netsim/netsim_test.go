package netsim

import (
	"net"
	"net/netip"
	"os"
	"testing"
	"time"
)

func ip(s string) netip.Addr { return netip.MustParseAddr(s) }

func udp(ap netip.AddrPort) *net.UDPAddr { return net.UDPAddrFromAddrPort(ap) }

func ap(s string) netip.AddrPort { return netip.MustParseAddrPort(s) }

// recv returns the next packet or fails after a short wait; with expectNone it
// asserts that nothing arrives.
func recv(t *testing.T, s *Socket, wait time.Duration) (string, netip.AddrPort, bool) {
	t.Helper()
	_ = s.SetReadDeadline(time.Now().Add(wait))
	buf := make([]byte, 2048)
	n, from, err := s.ReadFrom(buf)
	if err != nil {
		if os.IsTimeout(err) {
			return "", netip.AddrPort{}, false
		}
		t.Fatal(err)
	}
	return string(buf[:n]), from.(*net.UDPAddr).AddrPort(), true
}

type lab struct {
	net  *Network
	pub1 *Socket // public server 1 :3478
	pub2 *Socket // public server 2 :3478 (different IP)
	pub3 *Socket // public server 1, other port
	in   *Socket // host behind the NAT :5000
	nat  *NAT
}

func newLab(t *testing.T, cfg NATConfig) *lab {
	t.Helper()
	n := New()
	inet := n.Internet()
	h1 := inet.NewHost(ip("198.51.100.1"))
	h2 := inet.NewHost(ip("198.51.100.2"))
	l := &lab{net: n}
	var err error
	if l.pub1, err = h1.ListenPacket(3478); err != nil {
		t.Fatal(err)
	}
	if l.pub3, err = h1.ListenPacket(3479); err != nil {
		t.Fatal(err)
	}
	if l.pub2, err = h2.ListenPacket(3478); err != nil {
		t.Fatal(err)
	}
	l.nat = inet.NewNAT(ip("203.0.113.1"), cfg)
	host := l.nat.Inner().NewHost(ip("192.168.1.10"))
	if l.in, err = host.ListenPacket(5000); err != nil {
		t.Fatal(err)
	}
	return l
}

// externalAs sends one packet from the inside socket to dst and returns the
// source address the receiver observed.
func (l *lab) externalAs(t *testing.T, to *Socket) netip.AddrPort {
	t.Helper()
	dst := to.LocalAddr().(*net.UDPAddr)
	if _, err := l.in.WriteTo([]byte("hi"), dst); err != nil {
		t.Fatal(err)
	}
	_, from, ok := recv(t, to, time.Second)
	if !ok {
		t.Fatalf("packet to %v was not delivered", dst)
	}
	return from
}

func TestLANDeliveryAndNoRoute(t *testing.T) {
	n := New()
	a := n.Internet().NewHost(ip("10.0.0.1"))
	b := n.Internet().NewHost(ip("10.0.0.2"))
	sa, _ := a.ListenPacket(1000)
	sb, _ := b.ListenPacket(2000)
	if _, err := sa.WriteTo([]byte("ping"), udp(ap("10.0.0.2:2000"))); err != nil {
		t.Fatal(err)
	}
	got, from, ok := recv(t, sb, time.Second)
	if !ok || got != "ping" || from != ap("10.0.0.1:1000") {
		t.Fatalf("got %q from %v ok=%v", got, from, ok)
	}
	// Unroutable destination is silently dropped.
	var dropped string
	n.Tap(func(e Event) { dropped = e.Dropped })
	sa.WriteTo([]byte("x"), udp(ap("172.16.0.1:9")))
	if dropped != "no route" {
		t.Fatalf("expected no route drop, got %q", dropped)
	}
	// Port already in use.
	if _, err := a.ListenPacket(1000); err == nil {
		t.Fatal("duplicate bind accepted")
	}
}

func TestMappingBehaviour(t *testing.T) {
	cases := []struct {
		name         string
		cfg          NATConfig
		sameForDiffs bool // same external endpoint for different destinations
	}{
		{"full-cone", FullCone, true},
		{"restricted", RestrictedCone, true},
		{"port-restricted", PortRestricted, true},
		{"symmetric", Symmetric, false},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			l := newLab(t, tc.cfg)
			e1 := l.externalAs(t, l.pub1)
			e2 := l.externalAs(t, l.pub2)
			e3 := l.externalAs(t, l.pub3)
			if e1.Addr() != ip("203.0.113.1") {
				t.Fatalf("source not translated: %v", e1)
			}
			if tc.sameForDiffs {
				if e1 != e2 || e1 != e3 {
					t.Fatalf("endpoint-independent mapping expected: %v %v %v", e1, e2, e3)
				}
				if e1.Port() != 5000 {
					t.Fatalf("expected port preservation, got %d", e1.Port())
				}
			} else if e1 == e2 || e1 == e3 || e2 == e3 {
				t.Fatalf("symmetric NAT must allocate per destination: %v %v %v", e1, e2, e3)
			}
		})
	}
}

func TestFiltering(t *testing.T) {
	type result struct{ fromContacted, fromSameIPOtherPort, fromStranger bool }
	want := map[string]result{
		"full-cone":       {true, true, true},
		"restricted":      {true, true, false},
		"port-restricted": {true, false, false},
		"symmetric":       {true, false, false},
	}
	cfgs := map[string]NATConfig{"full-cone": FullCone, "restricted": RestrictedCone, "port-restricted": PortRestricted, "symmetric": Symmetric}
	for name, cfg := range cfgs {
		t.Run(name, func(t *testing.T) {
			l := newLab(t, cfg)
			ext := l.externalAs(t, l.pub1) // inside contacts pub1:3478 only
			target := udp(ext)

			l.pub1.WriteTo([]byte("a"), target)
			_, _, gotA := recv(t, l.in, 100*time.Millisecond)
			l.pub3.WriteTo([]byte("b"), target) // same IP, other port
			_, _, gotB := recv(t, l.in, 100*time.Millisecond)
			l.pub2.WriteTo([]byte("c"), target) // stranger
			_, _, gotC := recv(t, l.in, 100*time.Millisecond)
			got := result{gotA, gotB, gotC}
			if got != want[name] {
				t.Fatalf("filtering result %+v, want %+v", got, want[name])
			}
		})
	}
}

// The classic hole punch: two hosts behind port-restricted NATs learn each
// other's public address out of band and send to each other at the same time.
func TestHolePunchBetweenPortRestrictedNATs(t *testing.T) {
	n := New()
	inet := n.Internet()
	natA := inet.NewNAT(ip("203.0.113.1"), PortRestricted)
	natB := inet.NewNAT(ip("203.0.113.2"), PortRestricted)
	a, _ := natA.Inner().NewHost(ip("192.168.1.2")).ListenPacket(4000)
	b, _ := natB.Inner().NewHost(ip("192.168.7.2")).ListenPacket(4000)
	srv, _ := inet.NewHost(ip("198.51.100.1")).ListenPacket(3478)

	// Both ask a public server for their reflexive address.
	a.WriteTo([]byte("whoami"), udp(ap("198.51.100.1:3478")))
	_, aExt, _ := recv(t, srv, time.Second)
	b.WriteTo([]byte("whoami"), udp(ap("198.51.100.1:3478")))
	_, bExt, _ := recv(t, srv, time.Second)

	// First packet from A is dropped by B's NAT (no mapping towards A yet).
	a.WriteTo([]byte("a->b"), udp(bExt))
	if _, _, ok := recv(t, b, 100*time.Millisecond); ok {
		t.Fatal("unsolicited packet should be filtered")
	}
	// B sends towards A: this opens B's side and A's first packet's mapping allows it.
	b.WriteTo([]byte("b->a"), udp(aExt))
	if got, _, ok := recv(t, a, time.Second); !ok || got != "b->a" {
		t.Fatalf("B's packet should now reach A (got %q ok=%v)", got, ok)
	}
	// And now A can reach B too.
	a.WriteTo([]byte("a->b 2"), udp(bExt))
	if got, from, ok := recv(t, b, time.Second); !ok || got != "a->b 2" || from != aExt {
		t.Fatalf("hole not open in both directions: %q %v %v", got, from, ok)
	}
}

func TestSymmetricNATBreaksPunchingToPortRestricted(t *testing.T) {
	n := New()
	inet := n.Internet()
	natA := inet.NewNAT(ip("203.0.113.1"), Symmetric)
	natB := inet.NewNAT(ip("203.0.113.2"), PortRestricted)
	a, _ := natA.Inner().NewHost(ip("192.168.1.2")).ListenPacket(4000)
	b, _ := natB.Inner().NewHost(ip("192.168.7.2")).ListenPacket(4000)
	srv, _ := inet.NewHost(ip("198.51.100.1")).ListenPacket(3478)

	a.WriteTo([]byte("whoami"), udp(ap("198.51.100.1:3478")))
	_, aSeenBySrv, _ := recv(t, srv, time.Second)
	b.WriteTo([]byte("whoami"), udp(ap("198.51.100.1:3478")))
	_, bExt, _ := recv(t, srv, time.Second)

	// B punches towards the address A showed to the server...
	b.WriteTo([]byte("b->a"), udp(aSeenBySrv))
	// ...but A's NAT creates a *new* mapping when A talks to B, so B's packets
	// (aimed at the old port) never match and A's packets are filtered at B.
	a.WriteTo([]byte("a->b"), udp(bExt))
	if _, _, ok := recv(t, b, 150*time.Millisecond); ok {
		t.Fatal("symmetric NAT should have defeated simple hole punching")
	}
	if _, _, ok := recv(t, a, 150*time.Millisecond); ok {
		t.Fatal("packet reached a symmetric NAT on a stale port")
	}
}

func TestHairpin(t *testing.T) {
	for _, hairpin := range []bool{true, false} {
		cfg := FullCone
		cfg.Hairpin = hairpin
		n := New()
		nat := n.Internet().NewNAT(ip("203.0.113.1"), cfg)
		a, _ := nat.Inner().NewHost(ip("192.168.1.2")).ListenPacket(4000)
		b, _ := nat.Inner().NewHost(ip("192.168.1.3")).ListenPacket(4000)
		srv, _ := n.Internet().NewHost(ip("198.51.100.1")).ListenPacket(3478)

		b.WriteTo([]byte("x"), udp(ap("198.51.100.1:3478")))
		_, bExt, _ := recv(t, srv, time.Second)
		a.WriteTo([]byte("hairpin"), udp(bExt))
		_, _, ok := recv(t, b, 150*time.Millisecond)
		if ok != hairpin {
			t.Fatalf("hairpin=%v: delivered=%v", hairpin, ok)
		}
	}
}

func TestMappingExpiryFlushAndConfigChange(t *testing.T) {
	cfg := FullCone
	cfg.Timeout = 150 * time.Millisecond
	l := newLab(t, cfg)
	ext := l.externalAs(t, l.pub1)
	l.pub1.WriteTo([]byte("ok"), udp(ext))
	if _, _, ok := recv(t, l.in, 100*time.Millisecond); !ok {
		t.Fatal("fresh mapping should accept inbound")
	}
	time.Sleep(200 * time.Millisecond)
	l.pub1.WriteTo([]byte("late"), udp(ext))
	if _, _, ok := recv(t, l.in, 100*time.Millisecond); ok {
		t.Fatal("expired mapping accepted inbound")
	}

	l2 := newLab(t, FullCone)
	ext2 := l2.externalAs(t, l2.pub1)
	l2.nat.Flush()
	l2.pub1.WriteTo([]byte("x"), udp(ext2))
	if _, _, ok := recv(t, l2.in, 100*time.Millisecond); ok {
		t.Fatal("flushed NAT accepted inbound")
	}
	l2.nat.SetConfig(Symmetric)
	e := l2.externalAs(t, l2.pub1)
	e2 := l2.externalAs(t, l2.pub2)
	if e == e2 {
		t.Fatal("config change to symmetric not applied")
	}
}

func TestLossBlockLatencyAndDeadlines(t *testing.T) {
	n := New()
	a, _ := n.Internet().NewHost(ip("10.0.0.1")).ListenPacket(1)
	b, _ := n.Internet().NewHost(ip("10.0.0.2")).ListenPacket(2)

	n.SetLatency(60 * time.Millisecond)
	start := time.Now()
	a.WriteTo([]byte("slow"), udp(ap("10.0.0.2:2")))
	if _, _, ok := recv(t, b, time.Second); !ok || time.Since(start) < 50*time.Millisecond {
		t.Fatalf("latency not applied (took %v)", time.Since(start))
	}
	n.SetLatency(0)

	n.Block(ip("10.0.0.1"), ip("10.0.0.2"), true)
	a.WriteTo([]byte("x"), udp(ap("10.0.0.2:2")))
	if _, _, ok := recv(t, b, 80*time.Millisecond); ok {
		t.Fatal("blocked link delivered")
	}
	n.Block(ip("10.0.0.1"), ip("10.0.0.2"), false)
	a.WriteTo([]byte("y"), udp(ap("10.0.0.2:2")))
	if _, _, ok := recv(t, b, time.Second); !ok {
		t.Fatal("unblocked link did not deliver")
	}

	n.SetLoss(1)
	a.WriteTo([]byte("z"), udp(ap("10.0.0.2:2")))
	if _, _, ok := recv(t, b, 80*time.Millisecond); ok {
		t.Fatal("100% loss delivered")
	}
	n.SetLoss(0)

	// A blocked reader is released by changing the deadline, and by Close.
	done := make(chan error, 1)
	go func() {
		buf := make([]byte, 10)
		_, _, err := b.ReadFrom(buf)
		done <- err
	}()
	time.Sleep(30 * time.Millisecond)
	_ = b.SetReadDeadline(time.Now().Add(-time.Second))
	if err := <-done; !os.IsTimeout(err) {
		t.Fatalf("expected timeout, got %v", err)
	}
	_ = b.SetReadDeadline(time.Time{})
	go func() {
		buf := make([]byte, 10)
		_, _, err := b.ReadFrom(buf)
		done <- err
	}()
	time.Sleep(30 * time.Millisecond)
	b.Close()
	if err := <-done; err != net.ErrClosed {
		t.Fatalf("expected ErrClosed, got %v", err)
	}
}
