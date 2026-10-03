package magic

import (
	"net"
	"net/netip"
	"sync"
	"testing"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/netsim"
)

// recConn records what a node receives from one source (an on-path observer's copy).
type recConn struct {
	net.PacketConn
	mu   sync.Mutex
	from netip.AddrPort
	data [][]byte
}

func (r *recConn) ReadFrom(p []byte) (int, net.Addr, error) {
	n, a, err := r.PacketConn.ReadFrom(p)
	if err == nil {
		if ua, ok := a.(*net.UDPAddr); ok && ua.AddrPort() == r.from {
			r.mu.Lock()
			r.data = append(r.data, append([]byte(nil), p[:n]...))
			r.mu.Unlock()
		}
	}
	return n, a, err
}

// A packet that was captured on the wire and is sent again, from anywhere (also from a
// spoofed address), must not be answered or acted upon: the box key is static, so
// without a memory of the nonces a sealed packet stays valid for ever.
func TestReplayedDiscoPacketsAreIgnored(t *testing.T) {
	nw := netsim.New()
	hostA := nw.Internet().NewHost(ip("1.1.1.1"))
	hostB := nw.Internet().NewHost(ip("2.2.2.2"))
	hostZ := nw.Internet().NewHost(ip("6.6.6.6")) // the replayer, or the spoofed victim
	a := newNode(t, hostA, nodeOpts{port: 41000})

	bDev := identity.GenerateDevice()
	rec := &recConn{from: netip.AddrPortFrom(ip("1.1.1.1"), 41000)}
	bc, err := New(Config{
		Device: bDev, Port: 41000,
		Listen: func(port int) (net.PacketConn, error) {
			s, err := hostB.ListenPacket(uint16(port))
			if err != nil {
				return nil, err
			}
			rec.PacketConn = s
			return rec, nil
		},
		LocalAddrs: func() []netip.Addr { return hostB.Addrs() },
		Timing:     fastTiming,
	})
	if err != nil {
		t.Fatal(err)
	}
	defer bc.Close()
	if err := a.conn.AddPeer(bDev.ID); err != nil {
		t.Fatal(err)
	}
	if err := bc.AddPeer(a.dev.ID); err != nil {
		t.Fatal(err)
	}
	a.conn.AddCandidates(bDev.ID, []netip.AddrPort{netip.AddrPortFrom(ip("2.2.2.2"), 41000)}, SrcGossip)
	bc.AddCandidates(a.dev.ID, []netip.AddrPort{netip.AddrPortFrom(ip("1.1.1.1"), 41000)}, SrcGossip)
	waitFor(t, 10*time.Second, "direct path", func() bool {
		return a.conn.PathInfo(bDev.ID).Kind == PathDirect && bc.PathInfo(a.dev.ID).Kind == PathDirect
	})
	time.Sleep(700 * time.Millisecond) // a few keepalives, a peer-state

	rec.mu.Lock()
	var discos [][]byte
	for _, p := range rec.data {
		if p[0] == typeDisco {
			discos = append(discos, p)
		}
	}
	rec.mu.Unlock()
	if len(discos) == 0 {
		t.Fatal("captured nothing")
	}

	// Everything Z receives from now on is something B sent it.
	zs, err := hostZ.ListenPacket(7777)
	if err != nil {
		t.Fatal(err)
	}
	defer zs.Close()
	var got int
	var gmu sync.Mutex
	done := make(chan struct{})
	go func() {
		defer close(done)
		buf := make([]byte, 4096)
		for {
			_ = zs.SetReadDeadline(time.Now().Add(2500 * time.Millisecond))
			if _, _, err := zs.ReadFrom(buf); err != nil {
				return
			}
			gmu.Lock()
			got++
			gmu.Unlock()
		}
	}()
	bAddr := net.UDPAddrFromAddrPort(netip.AddrPortFrom(ip("2.2.2.2"), 41000))
	for round := 0; round < 3; round++ {
		for _, d := range discos {
			_, _ = zs.WriteTo(d, bAddr)
		}
		time.Sleep(150 * time.Millisecond)
	}
	// ...and the same copies, each from a different "spoofed" source.
	for i := 0; i < 30; i++ {
		h := nw.Internet().NewHost(netip.AddrFrom4([4]byte{9, 9, 9, byte(i + 1)}))
		s, err := h.ListenPacket(7777)
		if err != nil {
			t.Fatal(err)
		}
		defer s.Close()
		for _, d := range discos {
			_, _ = s.WriteTo(d, bAddr)
		}
	}
	<-done
	pr := bc.peerByID(a.dev.ID)
	pr.mu.Lock()
	var planted []netip.AddrPort
	for ap := range pr.cands {
		if ap.Addr() != ip("1.1.1.1") {
			planted = append(planted, ap)
		}
	}
	pr.mu.Unlock()
	if got != 0 {
		t.Errorf("B sent %d packets to the replayer in answer to %d captured disco packets", got, len(discos))
	}
	if len(planted) != 0 {
		t.Errorf("replayed packets planted candidate addresses: %v", planted)
	}
}

func testPeer(t *testing.T) *peer {
	t.Helper()
	nw := netsim.New()
	n := newNode(t, nw.Internet().NewHost(ip("1.1.1.1")), nodeOpts{port: 41000})
	dev := identity.GenerateDevice()
	if err := n.conn.AddPeer(dev.ID); err != nil {
		t.Fatal(err)
	}
	return n.conn.peerByID(dev.ID)
}

// Addresses taken from the source of a packet can be planted by a replay, so they are
// rationed and never push out what gossip or a verified round trip taught us.
func TestObservedAddressesAreRationed(t *testing.T) {
	p := testPeer(t)
	now := time.Now()
	good := netip.MustParseAddrPort("9.9.9.9:41710")
	p.mu.Lock()
	for i := 0; i < maxCandidates-2; i++ {
		ap := netip.AddrPortFrom(netip.AddrFrom4([4]byte{20, 0, 0, byte(i + 1)}), 41710)
		p.cands[ap] = &cand{ap: ap, src: SrcGossip, added: now.Add(-time.Minute)}
	}
	p.cands[good] = &cand{ap: good, src: SrcGossip, added: now.Add(-time.Minute), lastPong: now}
	p.direct, p.directPong = good, now
	gossip := len(p.cands) - 1
	// A flood: a new address every two seconds (the rate the limiter allows is one per second).
	var added int
	for i := 0; i < 40; i++ {
		ap := netip.AddrPortFrom(netip.AddrFrom4([4]byte{6, 6, byte(i), 1}), 7777)
		if p.learnObservedLocked(ap, now.Add(time.Duration(i)*2*time.Second)) {
			added++
		}
	}
	var observed, gossipLeft int
	for _, c := range p.cands {
		switch c.src {
		case SrcObserved:
			observed++
		case SrcGossip:
			gossipLeft++
		}
	}
	_, goodThere := p.cands[good]
	// Two addresses in the same second: the second one is refused.
	now2 := now.Add(time.Hour)
	first := p.learnObservedLocked(netip.MustParseAddrPort("7.7.7.1:1"), now2)
	second := p.learnObservedLocked(netip.MustParseAddrPort("7.7.7.2:1"), now2.Add(300*time.Millisecond))
	p.mu.Unlock()

	if added != 40 {
		t.Errorf("only %d of 40 addresses spaced two seconds apart were learned", added)
	}
	if observed > maxObservedCands {
		t.Errorf("%d observed addresses kept, the ration is %d", observed, maxObservedCands)
	}
	if gossipLeft < gossip-1 {
		t.Errorf("a flood of observed addresses pushed out gossip: %d of %d left", gossipLeft, gossip)
	}
	if !goodThere {
		t.Error("the confirmed direct path was evicted")
	}
	if !first || second {
		t.Errorf("rate limit: first=%v second=%v, want true false", first, second)
	}
}

// When the table of unanswered pings is full the oldest goes, not all of them: an
// attacker who makes us ping many addresses must not wipe the probes that matter.
func TestPendingPingsDropTheOldestOnly(t *testing.T) {
	p := testPeer(t)
	base := time.Now()
	var txs [][12]byte
	for i := 0; i < 3*maxPending; i++ {
		var tx [12]byte
		tx[0], tx[1] = byte(i>>8), byte(i)
		txs = append(txs, tx)
		p.addPending(tx, pingRec{sent: base.Add(time.Duration(i) * time.Millisecond)})
	}
	p.mu.Lock()
	n := len(p.pending)
	p.mu.Unlock()
	if n != maxPending {
		t.Fatalf("%d pings pending, want exactly %d", n, maxPending)
	}
	if _, ok := p.takePending(txs[len(txs)-1]); !ok {
		t.Error("the newest probe was lost")
	}
	if _, ok := p.takePending(txs[len(txs)-maxPending]); !ok {
		t.Error("a recent probe was lost")
	}
	if _, ok := p.takePending(txs[0]); ok {
		t.Error("the oldest probe survived")
	}
}

func TestNonceSetRemembersTheLastOnes(t *testing.T) {
	var s nonceSet
	mk := func(i int) (n [24]byte) { n[0], n[1] = byte(i>>8), byte(i); return }
	for i := 0; i < 3*discoMemory; i++ {
		s.add(mk(i))
	}
	if len(s.seen) != discoMemory || len(s.ring) != discoMemory {
		t.Fatalf("remembers %d / %d nonces, want %d", len(s.seen), len(s.ring), discoMemory)
	}
	if !s.has(mk(3*discoMemory-1)) || !s.has(mk(2*discoMemory)) {
		t.Error("a recent nonce was forgotten")
	}
	if s.has(mk(0)) || s.has(mk(2*discoMemory-1)) {
		t.Error("an old nonce is still remembered")
	}
	s.add(mk(3*discoMemory - 1)) // adding a known one changes nothing
	if len(s.seen) != discoMemory {
		t.Error("a duplicate grew the set")
	}
}

func TestAnonGate(t *testing.T) {
	g := newAnonGate()
	evil := netip.MustParseAddr("203.0.113.7")
	passed := 0
	for i := 0; i < 5000; i++ {
		if g.allow(evil) {
			passed++
		}
	}
	if passed < anonPerNetBurst || passed > anonPerNetBurst+anonPerNetRate/2 {
		t.Fatalf("one network got %d packets through in an instant, its burst is %d", passed, anonPerNetBurst)
	}
	if !g.allow(netip.MustParseAddr("198.51.100.9")) {
		t.Error("another network was starved by the first")
	}
	if g.allow(netip.MustParseAddr("203.0.113.200")) {
		t.Error("the same /24 has its own budget")
	}
	if g.allow(netip.MustParseAddr("::ffff:203.0.113.8")) {
		t.Error("the mapped form of a throttled address got through")
	}
	// IPv6 is accounted per /64.
	v6a, v6b := netip.MustParseAddr("2001:db8:1:2::1"), netip.MustParseAddr("2001:db8:1:2:ffff::9")
	for i := 0; i < 2000; i++ {
		g.allow(v6a)
	}
	if g.allow(v6b) {
		t.Error("two addresses of one /64 do not share a budget")
	}
	// Many networks cannot make the table grow without bound, nor take more than the global budget.
	h := newAnonGate()
	ok := 0
	for i := 0; i < 20000; i++ {
		if h.allow(netip.AddrFrom4([4]byte{10, byte(i >> 8), byte(i), 1})) {
			ok++
		}
	}
	if len(h.per) > anonMaxNets {
		t.Errorf("%d networks tracked, cap is %d", len(h.per), anonMaxNets)
	}
	if ok > anonGlobalBurst+anonGlobalRate/4 {
		t.Errorf("%d packets from 20000 networks in an instant, the global burst is %d", ok, anonGlobalBurst)
	}
}

func TestIsVirtualAddr(t *testing.T) {
	for s, want := range map[string]bool{
		"fdc7:5e57::1":          true,
		"fdc7:5e57:0:0:1:2:3:4": true,
		"fdc7:5e58::1":          false,
		"198.51.100.1":          false,
		"::ffff:198.51.100.1":   false,
		"2001:db8::1":           false,
	} {
		if got := isVirtualAddr(netip.MustParseAddr(s)); got != want {
			t.Errorf("isVirtualAddr(%s) = %v, want %v", s, got, want)
		}
	}
}
