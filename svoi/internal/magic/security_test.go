package magic

import (
	"net"
	"net/netip"
	"testing"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/netsim"
)

// Data packets carry a MAC that only the two devices can compute. Before, an
// off-path stranger who knew a member's public key (it is in every invitation)
// could send "data from that member": it reached the QUIC stack, taught us new
// candidate addresses (evicting real ones) and made us ping arbitrary — spoofable —
// addresses.
func TestForgedDataPacketsAreIgnored(t *testing.T) {
	nw := netsim.New()
	hostA := nw.Internet().NewHost(ip("1.1.1.1"))
	hostX := nw.Internet().NewHost(ip("6.6.6.6"))
	hostV := nw.Internet().NewHost(ip("7.7.7.7")) // an innocent third party
	a := newNode(t, hostA, nodeOpts{port: 41000})
	bDev := identity.GenerateDevice()
	if err := a.conn.AddPeer(bDev.ID); err != nil {
		t.Fatal(err)
	}
	legit := netip.MustParseAddrPort("9.9.9.9:41710") // B's real endpoint, known from gossip
	a.conn.AddCandidates(bDev.ID, []netip.AddrPort{legit}, SrcGossip)

	r8 := bDev.ID.Route8()
	aAddr := net.UDPAddrFromAddrPort(netip.AddrPortFrom(ip("1.1.1.1"), 41000))
	forgeries := [][]byte{
		append(append([]byte{typeData}, r8[:]...), 0xde, 0xad),                                  // the old format: no MAC at all
		append(append(append([]byte{typeData}, r8[:]...), make([]byte, 8)...), "payload"...),    // a zero MAC
		append(append(append([]byte{typeData}, r8[:]...), []byte("12345678")...), "payload"...), // a made-up MAC
	}
	var socks []*netsim.Socket
	for i := 0; i < 20; i++ {
		s, err := hostX.ListenPacket(uint16(6000 + i))
		if err != nil {
			t.Fatal(err)
		}
		defer s.Close()
		socks = append(socks, s)
		for _, f := range forgeries {
			_, _ = s.WriteTo(f, aAddr)
		}
	}
	vs, _ := hostV.ListenPacket(7777) // a spoofed source: the "victim"
	defer vs.Close()
	for _, f := range forgeries {
		_, _ = vs.WriteTo(f, aAddr)
	}
	time.Sleep(1500 * time.Millisecond)

	pr := a.conn.peerByID(bDev.ID)
	pr.mu.Lock()
	_, legitStill := pr.cands[legit]
	n := len(pr.cands)
	pr.mu.Unlock()
	if !legitStill || n != 1 {
		t.Fatalf("forged packets changed the candidate set: %d candidates, legitimate one present: %v", n, legitStill)
	}
	got := 0
	_ = vs.SetReadDeadline(time.Now().Add(300 * time.Millisecond))
	buf := make([]byte, 2048)
	for {
		m, _, err := vs.ReadFrom(buf)
		if err != nil {
			break
		}
		if m > 0 && buf[0] == typeDisco {
			got++
		}
	}
	if got != 0 {
		t.Fatalf("an innocent third party received %d probes triggered by forged packets", got)
	}
	if pi := a.conn.PathInfo(bDev.ID); pi.Kind == PathDirect {
		t.Fatalf("a forged packet produced a direct path: %+v", pi)
	}
}

// The disco box key is the same in both directions. Reflecting a device's own
// sealed packets back at it with the sender swapped used to make it adopt the
// reflector's address as a *verified direct path* to a device that never took part.
func TestDiscoReflectionCannotCreateAPath(t *testing.T) {
	nw := netsim.New()
	hostA := nw.Internet().NewHost(ip("1.1.1.1"))
	hostX := nw.Internet().NewHost(ip("6.6.6.6"))
	a := newNode(t, hostA, nodeOpts{port: 41000})
	bDev := identity.GenerateDevice() // B is not running; only its public id is known
	if err := a.conn.AddPeer(bDev.ID); err != nil {
		t.Fatal(err)
	}
	xs, err := hostX.ListenPacket(5555)
	if err != nil {
		t.Fatal(err)
	}
	defer xs.Close()
	aAddr := netip.AddrPortFrom(ip("1.1.1.1"), 41000)
	xAddr := netip.AddrPortFrom(ip("6.6.6.6"), 5555)
	send := func(b []byte) { _, _ = xs.WriteTo(b, net.UDPAddrFromAddrPort(aAddr)) }
	recv := func(d time.Duration) []byte {
		buf := make([]byte, 4096)
		_ = xs.SetReadDeadline(time.Now().Add(d))
		n, _, err := xs.ReadFrom(buf)
		if err != nil {
			return nil
		}
		return buf[:n]
	}
	// Even if A is somehow made to probe the attacker's address (here: told as a candidate)...
	a.conn.AddCandidates(bDev.ID, []netip.AddrPort{xAddr}, SrcGossip)
	var ping []byte
	for deadline := time.Now().Add(3 * time.Second); time.Now().Before(deadline) && ping == nil; {
		if p := recv(300 * time.Millisecond); len(p) > 0 && p[0] == typeDisco {
			ping = p
		}
	}
	if ping == nil {
		t.Fatal("A never probed the candidate")
	}
	flip := func(p []byte) []byte { // swap the header sender from A to B
		q := append([]byte(nil), p...)
		copy(q[1:33], bDev.ID[:])
		return q
	}
	// ...bouncing its ping back "from B" must not elicit a pong...
	send(flip(ping))
	for deadline := time.Now().Add(1500 * time.Millisecond); time.Now().Before(deadline); {
		p := recv(300 * time.Millisecond)
		// (A keeps probing the candidate itself with fresh pings of the same size; a pong
		// carries the observed address and is longer)
		if len(p) > 0 && p[0] == typeDisco && len(p) != len(ping) {
			t.Fatal("A answered a reflected ping")
		}
	}
	// ...and a path is never confirmed through reflection.
	if pi := a.conn.PathInfo(bDev.ID); pi.Kind == PathDirect {
		t.Fatalf("A adopted %v as a direct path to B by reflection", pi.Addr)
	}
}
