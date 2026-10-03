package magic

import (
	"bytes"
	"errors"
	"net"
	"net/netip"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/netsim"
)

func ip(s string) netip.Addr { return netip.MustParseAddr(s) }

var fastTiming = Timing{
	Tick:          40 * time.Millisecond,
	ProbeFast:     150 * time.Millisecond,
	ProbeSlow:     time.Second,
	KeepAlive:     300 * time.Millisecond,
	KeepAliveIdle: time.Second,
	PathTTL:       1500 * time.Millisecond,
	WantFor:       30 * time.Second,
	CallMeMaybe:   250 * time.Millisecond,
	STUNEvery:     time.Second,
	NetCheckEvery: 200 * time.Millisecond,
	StateEvery:    time.Second,
}

type node struct {
	t     *testing.T
	dev   *identity.Device
	host  *netsim.Host
	conn  *Conn
	port  uint16
	paths atomic.Int64 // number of OnPath callbacks
}

type nodeOpts struct {
	port       uint16
	noRelay    bool
	selfCert   []byte
	acceptCert func(id identity.ID, cert []byte) bool
	stun       []string
	tuneTiming func(*Timing)
	localAddrs []netip.Addr
	logf       bool
}

func newNode(t *testing.T, host *netsim.Host, o nodeOpts) *node {
	t.Helper()
	n := &node{t: t, dev: identity.GenerateDevice(), host: host}
	if o.port == 0 {
		o.port = 41000
	}
	n.port = o.port
	timing := fastTiming
	if o.tuneTiming != nil {
		o.tuneTiming(&timing)
	}
	cfg := Config{
		Device: n.dev,
		Port:   int(o.port),
		Listen: func(port int) (net.PacketConn, error) { return host.ListenPacket(uint16(port)) },
		LocalAddrs: func() []netip.Addr {
			if o.localAddrs != nil {
				return o.localAddrs
			}
			return host.Addrs()
		},
		STUN:        o.stun,
		ResolveSTUN: func(s string) ([]netip.AddrPort, error) { return []netip.AddrPort{netip.MustParseAddrPort(s)}, nil },
		AllowRelay:  func() bool { return !o.noRelay },
		OnPath:      func(identity.ID, PathInfo) { n.paths.Add(1) },
		Timing:      timing,
	}
	if o.selfCert != nil {
		cert := o.selfCert
		cfg.SelfCert = func() []byte { return cert }
	}
	if o.acceptCert != nil {
		cfg.AcceptUnknown = o.acceptCert
	}
	if o.logf {
		cfg.Logf = t.Logf
	}
	c, err := New(cfg)
	if err != nil {
		t.Fatal(err)
	}
	n.conn = c
	t.Cleanup(func() { c.Close() })
	return n
}

// meet registers a and b as peers of each other.
func meet(t *testing.T, nodes ...*node) {
	t.Helper()
	for _, a := range nodes {
		for _, b := range nodes {
			if a != b {
				if err := a.conn.AddPeer(b.dev.ID); err != nil {
					t.Fatal(err)
				}
			}
		}
	}
}

func (n *node) addr(public netip.Addr) netip.AddrPort { return netip.AddrPortFrom(public, n.port) }

func waitFor(t *testing.T, d time.Duration, what string, cond func() bool) {
	t.Helper()
	deadline := time.Now().Add(d)
	for time.Now().Before(deadline) {
		if cond() {
			return
		}
		time.Sleep(20 * time.Millisecond)
	}
	t.Fatalf("timed out after %v waiting for: %s", d, what)
}

// send writes one payload from a to b and expects it to arrive at b.
func send(t *testing.T, a, b *node, payload string) {
	t.Helper()
	va, _ := b.conn.VirtualAddr(a.dev.ID)
	vb, _ := a.conn.VirtualAddr(b.dev.ID)
	if _, err := a.conn.WriteTo([]byte(payload), vb); err != nil {
		t.Fatal(err)
	}
	buf := make([]byte, 2048)
	_ = b.conn.SetReadDeadline(time.Now().Add(2 * time.Second))
	n, from, err := b.conn.ReadFrom(buf)
	if err != nil {
		t.Fatalf("%s did not receive %q from %s: %v", b.dev.ID.Short(), payload, a.dev.ID.Short(), err)
	}
	if string(buf[:n]) != payload {
		t.Fatalf("got %q want %q", buf[:n], payload)
	}
	if from.String() != va.String() {
		t.Fatalf("packet attributed to %v, want %v", from, va)
	}
}

// sendUntil retries until delivery (the first packets may be dropped while a
// path is being established, exactly as QUIC's retransmissions would cover).
func sendUntil(t *testing.T, a, b *node, payload string, d time.Duration) {
	t.Helper()
	vb, _ := a.conn.VirtualAddr(b.dev.ID)
	deadline := time.Now().Add(d)
	buf := make([]byte, 2048)
	for time.Now().Before(deadline) {
		_, _ = a.conn.WriteTo([]byte(payload), vb)
		_ = b.conn.SetReadDeadline(time.Now().Add(150 * time.Millisecond))
		n, _, err := b.conn.ReadFrom(buf)
		if err == nil && string(buf[:n]) == payload {
			return
		}
	}
	t.Fatalf("payload %q from %s never reached %s", payload, a.dev.ID.Short(), b.dev.ID.Short())
}

func drain(n *node) {
	buf := make([]byte, 2048)
	for {
		_ = n.conn.SetReadDeadline(time.Now().Add(30 * time.Millisecond))
		if _, _, err := n.conn.ReadFrom(buf); err != nil {
			return
		}
	}
}

func TestDirectPathBetweenPublicHosts(t *testing.T) {
	nw := netsim.New()
	a := newNode(t, nw.Internet().NewHost(ip("198.51.100.1")), nodeOpts{})
	b := newNode(t, nw.Internet().NewHost(ip("198.51.100.2")), nodeOpts{})
	meet(t, a, b)
	a.conn.AddCandidates(b.dev.ID, []netip.AddrPort{b.addr(ip("198.51.100.2"))}, SrcInvite)
	a.conn.Poke(b.dev.ID)

	waitFor(t, 3*time.Second, "direct path a->b", func() bool { return a.conn.PathInfo(b.dev.ID).Kind == PathDirect })
	// B learned A from the ping and probed back, so it converges too.
	waitFor(t, 3*time.Second, "direct path b->a", func() bool { return b.conn.PathInfo(a.dev.ID).Kind == PathDirect })

	send(t, a, b, "hello b")
	send(t, b, a, "hello a")
	if s := a.conn.Stats(b.dev.ID); s.TxDirect == 0 || s.TxRelay != 0 {
		t.Fatalf("unexpected stats %+v", s)
	}
}

func TestPrivateAddressesAreClassifiedAsLAN(t *testing.T) {
	nw := netsim.New()
	lan := nw.Internet() // pretend the whole thing is one LAN
	a := newNode(t, lan.NewHost(ip("192.168.1.2")), nodeOpts{})
	b := newNode(t, lan.NewHost(ip("192.168.1.3")), nodeOpts{})
	meet(t, a, b)
	a.conn.AddCandidates(b.dev.ID, []netip.AddrPort{b.addr(ip("192.168.1.3"))}, SrcLAN)
	a.conn.Poke(b.dev.ID)
	waitFor(t, 3*time.Second, "lan path", func() bool { return a.conn.PathInfo(b.dev.ID).Kind == PathLAN })
	if rtt := a.conn.PathInfo(b.dev.ID).RTT; rtt <= 0 {
		t.Fatalf("rtt not measured: %v", rtt)
	}
}

// topology: two devices behind their own NATs plus a public member (the
// "anchor") that both reached first.
type natTopo struct {
	net          *netsim.Network
	natA, natB   *netsim.NAT
	a, b, anchor *node
}

func newNATTopo(t *testing.T, cfgA, cfgB netsim.NATConfig, anchorOpts nodeOpts) *natTopo {
	t.Helper()
	nw := netsim.New()
	inet := nw.Internet()
	tp := &natTopo{net: nw}
	tp.natA = inet.NewNAT(ip("203.0.113.1"), cfgA)
	tp.natB = inet.NewNAT(ip("203.0.113.2"), cfgB)
	tp.anchor = newNode(t, inet.NewHost(ip("203.0.113.100")), anchorOpts)
	tp.a = newNode(t, tp.natA.Inner().NewHost(ip("192.168.1.2")), nodeOpts{})
	tp.b = newNode(t, tp.natB.Inner().NewHost(ip("192.168.7.2")), nodeOpts{})
	meet(t, tp.a, tp.b, tp.anchor)
	// A and B know only the anchor's public endpoint (as from an invite).
	for _, n := range []*node{tp.a, tp.b} {
		n.conn.AddCandidates(tp.anchor.dev.ID, []netip.AddrPort{tp.anchor.addr(ip("203.0.113.100"))}, SrcInvite)
		n.conn.Poke(tp.anchor.dev.ID)
	}
	waitFor(t, 3*time.Second, "a reaches anchor", func() bool { return tp.a.conn.PathInfo(tp.anchor.dev.ID).Kind == PathDirect })
	waitFor(t, 3*time.Second, "b reaches anchor", func() bool { return tp.b.conn.PathInfo(tp.anchor.dev.ID).Kind == PathDirect })
	return tp
}

func TestHolePunchingThroughAnchorForAllConePairs(t *testing.T) {
	cones := map[string]netsim.NATConfig{
		"full-cone":       netsim.FullCone,
		"restricted":      netsim.RestrictedCone,
		"port-restricted": netsim.PortRestricted,
	}
	for na, ca := range cones {
		for nb, cb := range cones {
			t.Run(na+"+"+nb, func(t *testing.T) {
				tp := newNATTopo(t, ca, cb, nodeOpts{})
				// They have never exchanged endpoints; the anchor announces itself as
				// a relay for both, and a call-me-maybe through it starts the punch.
				tp.a.conn.Poke(tp.b.dev.ID)
				tp.b.conn.Poke(tp.a.dev.ID)
				waitFor(t, 8*time.Second, "direct a<->b", func() bool {
					return tp.a.conn.PathInfo(tp.b.dev.ID).Kind == PathDirect &&
						tp.b.conn.PathInfo(tp.a.dev.ID).Kind == PathDirect
				})
				before, _ := tp.anchor.conn.RelayStats()
				send(t, tp.a, tp.b, "punched")
				send(t, tp.b, tp.a, "through")
				after, _ := tp.anchor.conn.RelayStats()
				if after != before {
					t.Fatalf("traffic still went through the relay (%d -> %d packets)", before, after)
				}
				if s := tp.a.conn.Stats(tp.b.dev.ID); s.TxDirect == 0 {
					t.Fatalf("no direct tx recorded: %+v", s)
				}
			})
		}
	}
}

func TestRelayCarriesTrafficWhileDirectPathIsImpossible(t *testing.T) {
	// A symmetric NAT opposite a port-restricted one cannot be punched.
	tp := newNATTopo(t, netsim.Symmetric, netsim.PortRestricted, nodeOpts{})
	tp.a.conn.Poke(tp.b.dev.ID)
	tp.b.conn.Poke(tp.a.dev.ID)

	waitFor(t, 8*time.Second, "relay path a->b", func() bool { return tp.a.conn.PathInfo(tp.b.dev.ID).Kind == PathRelay })
	waitFor(t, 8*time.Second, "relay path b->a", func() bool { return tp.b.conn.PathInfo(tp.a.dev.ID).Kind == PathRelay })
	if rv := tp.a.conn.PathInfo(tp.b.dev.ID).Relay; rv != tp.anchor.dev.ID {
		t.Fatalf("relay is %s, want the anchor", rv.Short())
	}
	sendUntil(t, tp.a, tp.b, "via relay 1", 5*time.Second)
	sendUntil(t, tp.b, tp.a, "via relay 2", 5*time.Second)

	pk, by := tp.anchor.conn.RelayStats()
	if pk == 0 || by == 0 {
		t.Fatal("anchor did not forward anything")
	}
	if s := tp.a.conn.Stats(tp.b.dev.ID); s.TxRelay == 0 || s.TxDirect != 0 {
		t.Fatalf("expected relay-only traffic: %+v", s)
	}
	// And it must stay a relay: a hard NAT does not suddenly become punchable.
	time.Sleep(1500 * time.Millisecond)
	if k := tp.a.conn.PathInfo(tp.b.dev.ID).Kind; k != PathRelay {
		t.Fatalf("path flapped to %v", k)
	}
	if nat := tp.a.conn.NAT(); !nat.MappingVaries && len(nat.Public) > 0 {
		t.Logf("note: symmetric NAT not yet diagnosed from a single reporter: %+v", nat)
	}
}

func TestBothSymmetricStillConnectsViaRelay(t *testing.T) {
	tp := newNATTopo(t, netsim.Symmetric, netsim.Symmetric, nodeOpts{})
	tp.a.conn.Poke(tp.b.dev.ID)
	waitFor(t, 8*time.Second, "relay path", func() bool { return tp.a.conn.PathInfo(tp.b.dev.ID).Kind == PathRelay })
	sendUntil(t, tp.a, tp.b, "hard nat to hard nat", 5*time.Second)
}

func TestNoRelayNoPath(t *testing.T) {
	tp := newNATTopo(t, netsim.Symmetric, netsim.PortRestricted, nodeOpts{noRelay: true})
	tp.a.conn.Poke(tp.b.dev.ID)
	time.Sleep(2500 * time.Millisecond)
	if k := tp.a.conn.PathInfo(tp.b.dev.ID).Kind; k != PathNone {
		t.Fatalf("expected no path, got %v", k)
	}
	// Writing must not fail or block; the packet is just dropped.
	vb, _ := tp.a.conn.VirtualAddr(tp.b.dev.ID)
	if _, err := tp.a.conn.WriteTo([]byte("into the void"), vb); err != nil {
		t.Fatalf("write to unreachable peer returned %v", err)
	}
}

func TestFailoverToRelayAndBackToDirect(t *testing.T) {
	tp := newNATTopo(t, netsim.PortRestricted, netsim.PortRestricted, nodeOpts{})
	tp.a.conn.Poke(tp.b.dev.ID)
	tp.b.conn.Poke(tp.a.dev.ID)
	waitFor(t, 8*time.Second, "direct", func() bool { return tp.a.conn.PathInfo(tp.b.dev.ID).Kind == PathDirect })

	// Cut the direct link between the two NATs; the anchor stays reachable.
	tp.net.Block(tp.natA.PublicIP(), tp.natB.PublicIP(), true)
	waitFor(t, 8*time.Second, "failover to relay", func() bool { return tp.a.conn.PathInfo(tp.b.dev.ID).Kind == PathRelay })
	sendUntil(t, tp.a, tp.b, "while cut", 5*time.Second)

	tp.net.Block(tp.natA.PublicIP(), tp.natB.PublicIP(), false)
	waitFor(t, 15*time.Second, "direct again", func() bool { return tp.a.conn.PathInfo(tp.b.dev.ID).Kind == PathDirect })
	drain(tp.b)
	send(t, tp.a, tp.b, "healed")
}

func TestUnknownMemberIsAcceptedViaCertificate(t *testing.T) {
	nw := netsim.New()
	inet := nw.Internet()
	cert := []byte("pretend-this-is-a-member-certificate")
	var accepted atomic.Int64
	var b *node
	b = newNode(t, inet.NewHost(ip("198.51.100.2")), nodeOpts{
		acceptCert: func(id identity.ID, c []byte) bool {
			if !bytes.Equal(c, cert) {
				return false
			}
			accepted.Add(1)
			_ = b.conn.AddPeer(id)
			return true
		},
	})
	a := newNode(t, inet.NewHost(ip("198.51.100.1")), nodeOpts{selfCert: cert})
	// B does not know A; A knows B.
	if err := a.conn.AddPeer(b.dev.ID); err != nil {
		t.Fatal(err)
	}
	a.conn.AddCandidates(b.dev.ID, []netip.AddrPort{b.addr(ip("198.51.100.2"))}, SrcInvite)
	a.conn.Poke(b.dev.ID)
	waitFor(t, 3*time.Second, "a reaches b", func() bool { return a.conn.PathInfo(b.dev.ID).Kind == PathDirect })
	if accepted.Load() == 0 {
		t.Fatal("AcceptUnknown was never consulted")
	}
	waitFor(t, 3*time.Second, "b learns a", func() bool { return b.conn.PathInfo(a.dev.ID).Kind == PathDirect })
	send(t, a, b, "welcome")
}

func TestUnknownMemberWithBadCertificateGetsNoAnswer(t *testing.T) {
	nw := netsim.New()
	inet := nw.Internet()
	b := newNode(t, inet.NewHost(ip("198.51.100.2")), nodeOpts{
		acceptCert: func(identity.ID, []byte) bool { return false },
	})
	a := newNode(t, inet.NewHost(ip("198.51.100.1")), nodeOpts{selfCert: []byte("forged")})
	_ = a.conn.AddPeer(b.dev.ID)
	a.conn.AddCandidates(b.dev.ID, []netip.AddrPort{b.addr(ip("198.51.100.2"))}, SrcInvite)
	a.conn.Poke(b.dev.ID)
	time.Sleep(1200 * time.Millisecond)
	if k := a.conn.PathInfo(b.dev.ID).Kind; k != PathNone {
		t.Fatalf("path established with an unaccepted peer: %v", k)
	}
	if got := b.conn.PathInfo(a.dev.ID); got.Kind != PathNone {
		t.Fatalf("b registered the stranger: %+v", got)
	}
}

func TestStrangersAndGarbageAreIgnored(t *testing.T) {
	nw := netsim.New()
	inet := nw.Internet()
	b := newNode(t, inet.NewHost(ip("198.51.100.2")), nodeOpts{})
	evil, _ := inet.NewHost(ip("198.51.100.66")).ListenPacket(5555)
	dst := net2addr("198.51.100.2:41000")

	garbage := [][]byte{
		{},
		{0x00},
		{typeData},
		{typeData, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10},
		{typeDisco, 1, 2, 3},
		append([]byte{typeDisco}, bytes.Repeat([]byte{7}, 200)...),
		append([]byte{typeRelayOut}, bytes.Repeat([]byte{9}, 100)...),
		append([]byte{typeRelayIn}, bytes.Repeat([]byte{9}, 100)...),
		append([]byte{typeAnon}, []byte("quic?")...),
		bytes.Repeat([]byte{0xFF}, 1500),
	}
	for _, g := range garbage {
		evil.WriteTo(g, dst)
	}
	time.Sleep(200 * time.Millisecond)
	_ = b.conn.SetReadDeadline(time.Now().Add(100 * time.Millisecond))
	if n, addr, err := b.conn.ReadFrom(make([]byte, 2048)); err == nil {
		t.Fatalf("garbage was passed up to QUIC: %d bytes from %v", n, addr)
	}
}

func net2addr(s string) *net.UDPAddr { return net.UDPAddrFromAddrPort(netip.MustParseAddrPort(s)) }

func TestAnonymousTrafficOnlyWhenEnabled(t *testing.T) {
	nw := netsim.New()
	inet := nw.Internet()
	b := newNode(t, inet.NewHost(ip("198.51.100.2")), nodeOpts{})
	a := newNode(t, inet.NewHost(ip("198.51.100.1")), nodeOpts{})

	target := net2addr("198.51.100.2:41000")
	a.conn.SetAnonymous(true)
	// b is not listening for anonymous traffic yet: dropped.
	a.conn.WriteTo([]byte("join me"), target)
	_ = b.conn.SetReadDeadline(time.Now().Add(150 * time.Millisecond))
	if _, _, err := b.conn.ReadFrom(make([]byte, 100)); err == nil {
		t.Fatal("anonymous packet accepted while not expecting joiners")
	}
	b.conn.SetAnonymous(true)
	a.conn.WriteTo([]byte("join me"), target)
	_ = b.conn.SetReadDeadline(time.Now().Add(time.Second))
	buf := make([]byte, 100)
	n, from, err := b.conn.ReadFrom(buf)
	if err != nil || string(buf[:n]) != "join me" {
		t.Fatalf("anonymous packet lost: %v %q", err, buf[:n])
	}
	// The reply goes straight back to the real address.
	if _, err := b.conn.WriteTo([]byte("welcome"), from); err != nil {
		t.Fatal(err)
	}
	_ = a.conn.SetReadDeadline(time.Now().Add(time.Second))
	n, _, err = a.conn.ReadFrom(buf)
	if err != nil || string(buf[:n]) != "welcome" {
		t.Fatalf("anonymous reply lost: %v %q", err, buf[:n])
	}
	// And when disabled a sender writing to strangers sends nothing.
	b.conn.SetAnonymous(false)
	a.conn.SetAnonymous(false)
	a.conn.WriteTo([]byte("x"), target)
	_ = b.conn.SetReadDeadline(time.Now().Add(100 * time.Millisecond))
	if _, _, err := b.conn.ReadFrom(buf); err == nil {
		t.Fatal("packet leaked with anonymous mode off")
	}
}

func TestRelayRejectsForgedAndForeignEnvelopes(t *testing.T) {
	tp := newNATTopo(t, netsim.PortRestricted, netsim.PortRestricted, nodeOpts{})
	// Craft a relay-out packet claiming to be from A, with a bad MAC, from a stranger socket.
	evil, _ := tp.net.Internet().NewHost(ip("198.51.100.66")).ListenPacket(6666)
	ar8, br8 := tp.a.dev.ID.Route8(), tp.b.dev.ID.Route8()
	var forged []byte
	forged = append(forged, typeRelayOut)
	forged = append(forged, ar8[:]...)
	forged = append(forged, br8[:]...)
	forged = append(forged, bytes.Repeat([]byte{1}, 8)...) // wrong MAC
	forged = append(forged, typeData)
	forged = append(forged, ar8[:]...)
	forged = append(forged, []byte("spoofed")...)
	before, _ := tp.anchor.conn.RelayStats()
	evil.WriteTo(forged, net2addr("203.0.113.100:41000"))
	time.Sleep(200 * time.Millisecond)
	after, _ := tp.anchor.conn.RelayStats()
	if after != before {
		t.Fatal("anchor relayed a packet with an invalid MAC")
	}
	drain(tp.b)
}

func TestSTUNLearnsPublicEndpointAndDetectsSymmetricNAT(t *testing.T) {
	for _, tc := range []struct {
		name      string
		cfg       netsim.NATConfig
		symmetric bool
	}{
		{"port-restricted", netsim.PortRestricted, false},
		{"symmetric", netsim.Symmetric, true},
	} {
		t.Run(tc.name, func(t *testing.T) {
			nw := netsim.New()
			inet := nw.Internet()
			var servers []string
			for i, addr := range []string{"198.51.100.10", "198.51.100.11"} {
				s, err := inet.NewHost(ip(addr)).ListenPacket(3478)
				if err != nil {
					t.Fatal(err)
				}
				servers = append(servers, addr+":3478")
				go func(s *netsim.Socket, i int) {
					buf := make([]byte, 1500)
					for {
						n, from, err := s.ReadFrom(buf)
						if err != nil {
							return
						}
						if !looksLikeSTUN(buf[:n]) {
							continue
						}
						resp := buildStunResponse(buf[:n], from.(*net.UDPAddr).AddrPort())
						s.WriteTo(resp, from)
					}
				}(s, i)
			}
			nat := inet.NewNAT(ip("203.0.113.1"), tc.cfg)
			a := newNode(t, nat.Inner().NewHost(ip("192.168.1.2")), nodeOpts{stun: servers})

			waitFor(t, 4*time.Second, "stun result", func() bool { return a.conn.NAT().STUNWorks })
			var public []Endpoint
			waitFor(t, 4*time.Second, "public endpoints", func() bool {
				public = nil
				for _, e := range a.conn.Endpoints() {
					if e.Kind == EPSTUN {
						public = append(public, e)
					}
				}
				return len(public) >= 1
			})
			for _, e := range public {
				if e.Addr.Addr() != ip("203.0.113.1") {
					t.Fatalf("STUN endpoint %v is not the NAT's public address", e.Addr)
				}
			}
			if tc.symmetric {
				waitFor(t, 4*time.Second, "symmetric NAT diagnosed", func() bool { return a.conn.NAT().MappingVaries })
			} else if a.conn.NAT().MappingVaries {
				t.Fatalf("endpoint-independent NAT misdiagnosed as symmetric: %+v", a.conn.NAT())
			}
		})
	}
}

func TestPathCallbackFiresOnChanges(t *testing.T) {
	nw := netsim.New()
	a := newNode(t, nw.Internet().NewHost(ip("198.51.100.1")), nodeOpts{})
	b := newNode(t, nw.Internet().NewHost(ip("198.51.100.2")), nodeOpts{})
	meet(t, a, b)
	a.conn.AddCandidates(b.dev.ID, []netip.AddrPort{b.addr(ip("198.51.100.2"))}, SrcInvite)
	a.conn.Poke(b.dev.ID)
	waitFor(t, 3*time.Second, "path", func() bool { return a.conn.PathInfo(b.dev.ID).Kind == PathDirect })
	waitFor(t, time.Second, "callback", func() bool { return a.paths.Load() >= 1 })

	// Cut the link: the path must expire and be reported.
	nw.Block(ip("198.51.100.1"), ip("198.51.100.2"), true)
	waitFor(t, 6*time.Second, "path expiry", func() bool { return a.conn.PathInfo(b.dev.ID).Kind == PathNone })
}

func TestRemovePeerStopsTraffic(t *testing.T) {
	nw := netsim.New()
	a := newNode(t, nw.Internet().NewHost(ip("198.51.100.1")), nodeOpts{})
	b := newNode(t, nw.Internet().NewHost(ip("198.51.100.2")), nodeOpts{})
	meet(t, a, b)
	a.conn.AddCandidates(b.dev.ID, []netip.AddrPort{b.addr(ip("198.51.100.2"))}, SrcInvite)
	a.conn.Poke(b.dev.ID)
	waitFor(t, 3*time.Second, "path", func() bool { return a.conn.PathInfo(b.dev.ID).Kind == PathDirect })
	send(t, a, b, "before")

	b.conn.RemovePeer(a.dev.ID) // b revokes a
	va, _ := a.conn.VirtualAddr(b.dev.ID)
	a.conn.WriteTo([]byte("after"), va)
	_ = b.conn.SetReadDeadline(time.Now().Add(200 * time.Millisecond))
	if _, _, err := b.conn.ReadFrom(make([]byte, 100)); err == nil {
		t.Fatal("traffic from a removed peer was accepted")
	}
	if _, ok := b.conn.VirtualAddr(a.dev.ID); ok {
		t.Fatal("removed peer still has a virtual address")
	}
}

func TestConcurrentSendersAreSafe(t *testing.T) {
	nw := netsim.New()
	a := newNode(t, nw.Internet().NewHost(ip("198.51.100.1")), nodeOpts{})
	b := newNode(t, nw.Internet().NewHost(ip("198.51.100.2")), nodeOpts{})
	meet(t, a, b)
	a.conn.AddCandidates(b.dev.ID, []netip.AddrPort{b.addr(ip("198.51.100.2"))}, SrcInvite)
	a.conn.Poke(b.dev.ID)
	waitFor(t, 3*time.Second, "path", func() bool { return a.conn.PathInfo(b.dev.ID).Kind == PathDirect })

	vb, _ := a.conn.VirtualAddr(b.dev.ID)
	var wg sync.WaitGroup
	var got atomic.Int64
	done := make(chan struct{})
	go func() {
		buf := make([]byte, 2048)
		for {
			select {
			case <-done:
				return
			default:
			}
			_ = b.conn.SetReadDeadline(time.Now().Add(50 * time.Millisecond))
			if _, _, err := b.conn.ReadFrom(buf); err == nil {
				got.Add(1)
			}
		}
	}()
	for g := 0; g < 8; g++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for i := 0; i < 200; i++ {
				if _, err := a.conn.WriteTo(bytes.Repeat([]byte{byte(i)}, 1200), vb); err != nil {
					t.Error(err)
					return
				}
			}
		}()
	}
	wg.Wait()
	time.Sleep(300 * time.Millisecond)
	close(done)
	if got.Load() < 1000 {
		t.Fatalf("only %d of 1600 packets arrived on a lossless network", got.Load())
	}
}

func TestClosedConnReturnsErrors(t *testing.T) {
	nw := netsim.New()
	a := newNode(t, nw.Internet().NewHost(ip("198.51.100.1")), nodeOpts{})
	a.conn.Close()
	if _, _, err := a.conn.ReadFrom(make([]byte, 10)); !errors.Is(err, netErrClosed) {
		t.Fatalf("ReadFrom after close: %v", err)
	}
	if _, err := a.conn.WriteTo([]byte("x"), net2addr("1.2.3.4:5")); !errors.Is(err, netErrClosed) {
		t.Fatalf("WriteTo after close: %v", err)
	}
	if err := a.conn.Close(); err != nil {
		t.Fatalf("second Close: %v", err)
	}
}

var netErrClosed = net.ErrClosed
