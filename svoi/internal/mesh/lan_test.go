package mesh

import (
	"bytes"
	"crypto/rand"
	"net"
	"net/netip"
	"testing"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/magic"
)

func testKey(t *testing.T) [32]byte {
	t.Helper()
	var k [32]byte
	if _, err := rand.Read(k[:]); err != nil {
		t.Fatal(err)
	}
	return k
}

func TestBeaconRoundTrip(t *testing.T) {
	key := testKey(t)
	var id identity.ID
	rand.Read(id[:])
	b := encodeBeacon(key, id, 41710)
	if len(b) != beaconLen {
		t.Fatalf("beacon is %d bytes, want %d", len(b), beaconLen)
	}
	got, port, ok := decodeBeacon(key, b)
	if !ok || got != id || port != 41710 {
		t.Fatalf("round trip: ok=%v id-equal=%v port=%d", ok, got == id, port)
	}
}

// A bystander on the same network must learn nothing from a beacon: not the
// device key, not the mesh, and not even that two beacons come from one device.
func TestBeaconRevealsNothingToOutsiders(t *testing.T) {
	key := testKey(t)
	var id identity.ID
	rand.Read(id[:])
	b1, b2 := encodeBeacon(key, id, 41710), encodeBeacon(key, id, 41710)
	if bytes.Equal(b1, b2) {
		t.Fatal("two beacons of one device are identical: they can be linked")
	}
	for _, b := range [][]byte{b1, b2} {
		if bytes.Contains(b, id[:]) || bytes.Contains(b, id[:8]) {
			t.Fatal("the device key appears in the clear")
		}
	}
	// ... and the common bytes between two beacons are no more than chance would give.
	same := 0
	for i := range b1 {
		if b1[i] == b2[i] {
			same++
		}
	}
	if same > 8 { // the version byte plus a few lucky matches
		t.Fatalf("%d of %d bytes are identical between two beacons", same, len(b1))
	}
	// Another mesh (or a stranger) cannot open it, and cannot make one that passes.
	other := testKey(t)
	if _, _, ok := decodeBeacon(other, b1); ok {
		t.Fatal("a beacon of one mesh was accepted by another")
	}
	if _, _, ok := decodeBeacon(other, encodeBeacon(other, id, 41710)); !ok {
		t.Fatal("sanity: the other mesh cannot read its own beacons")
	}
}

func TestBeaconRejectsDamage(t *testing.T) {
	key := testKey(t)
	var id identity.ID
	rand.Read(id[:])
	good := encodeBeacon(key, id, 41710)
	for i := range good { // every single flipped bit is caught
		bad := append([]byte(nil), good...)
		bad[i] ^= 0x01
		if _, _, ok := decodeBeacon(key, bad); ok {
			t.Fatalf("a beacon with byte %d damaged was accepted", i)
		}
	}
	for _, n := range []int{0, 1, beaconLen - 1, beaconLen + 1} {
		bad := make([]byte, n)
		copy(bad, good)
		if _, _, ok := decodeBeacon(key, bad); ok {
			t.Fatalf("a beacon of %d bytes was accepted", n)
		}
	}
	old := append([]byte(nil), good...)
	old[0] = 1 // the previous, unsealed format
	if _, _, ok := decodeBeacon(key, old); ok {
		t.Fatal("an old-format beacon was accepted")
	}
}

func TestOnLink(t *testing.T) {
	_, home, _ := net.ParseCIDR("192.168.1.5/24")
	home.IP = net.ParseIP("192.168.1.5") // an interface address keeps its host bits
	_, lo, _ := net.ParseCIDR("127.0.0.1/8")
	_, v6, _ := net.ParseCIDR("2001:db8::5/64")
	addrs := []net.Addr{home, lo, v6, &net.UnixAddr{Name: "x"}}
	for ip, want := range map[string]bool{
		"192.168.1.77":       true,
		"192.168.2.77":       false,
		"8.8.8.8":            false, // a routed or spoofed source
		"127.0.0.1":          true,  // several nodes on one machine
		"::ffff:192.168.1.9": true,  // the same address in its IPv6 spelling
		"10.0.0.1":           false,
	} {
		if got := onLink(netip.MustParseAddr(ip), addrs); got != want {
			t.Errorf("onLink(%s) = %v, want %v", ip, got, want)
		}
	}
	if onLink(netip.MustParseAddr("192.168.1.9"), nil) {
		t.Error("with no interfaces nothing is on-link")
	}
}

// Against the real interfaces of this machine: its own addresses are on-link,
// and a far-away public address is not.
func TestOnLinkWithRealInterfaces(t *testing.T) {
	addrs, err := net.InterfaceAddrs()
	if err != nil {
		t.Skip(err)
	}
	checked := 0
	for _, a := range addrs {
		ipn, ok := a.(*net.IPNet)
		if !ok || ipn.IP.To4() == nil {
			continue
		}
		ip, _ := netip.AddrFromSlice(ipn.IP)
		if !fromLocalNetwork(ip.Unmap()) {
			t.Errorf("the interface's own address %v is not on-link", ip.Unmap())
		}
		checked++
	}
	if checked == 0 {
		t.Skip("no IPv4 interface addresses here")
	}
	if fromLocalNetwork(netip.MustParseAddr("203.0.113.200")) && !hasNet(addrs, "203.0.113.") {
		t.Error("an address on no local network is on-link")
	}
}

func hasNet(addrs []net.Addr, prefix string) bool {
	for _, a := range addrs {
		if ipn, ok := a.(*net.IPNet); ok && len(ipn.IP.String()) >= len(prefix) && ipn.IP.String()[:len(prefix)] == prefix {
			return true
		}
	}
	return false
}

// A router that forwards a public address to us makes this device directly
// reachable, whatever kind of NAT it is.
func TestNATDifficultyWithAPortMapping(t *testing.T) {
	mapped := netip.MustParseAddrPort("203.0.113.5:41710")
	if got := natDifficulty(magic.NATReport{MappingVaries: true, Mapped: mapped}, nil); got != "open" {
		t.Fatalf("a mapped device is reachable: %q", got)
	}
	if got := natDifficulty(magic.NATReport{MappingVaries: true}, nil); got != "hard" {
		t.Fatalf("without a mapping a symmetric NAT stays hard: %q", got)
	}
}
