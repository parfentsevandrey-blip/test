package mesh

import (
	"bytes"
	"crypto/ed25519"
	"crypto/rand"
	"encoding/binary"
	"net"
	"net/netip"
	"testing"

	"golang.org/x/crypto/chacha20poly1305"

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
	dev := identity.GenerateDevice()
	id := dev.ID
	b := encodeBeacon(key, dev, 41710)
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
	dev := identity.GenerateDevice()
	id := dev.ID
	b1, b2 := encodeBeacon(key, dev, 41710), encodeBeacon(key, dev, 41710)
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
	if _, _, ok := decodeBeacon(other, encodeBeacon(other, dev, 41710)); !ok {
		t.Fatal("sanity: the other mesh cannot read its own beacons")
	}
}

func TestBeaconRejectsDamage(t *testing.T) {
	key := testKey(t)
	dev := identity.GenerateDevice()
	good := encodeBeacon(key, dev, 41710)
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

// The key that seals beacons comes from the root's public key, which is in every
// invitation. Somebody who has seen one can therefore seal a beacon, but not sign it
// as a member: a beacon that names a device without that device's signature is not
// accepted, whatever it says.
func TestBeaconNeedsTheSignatureOfTheDeviceItNames(t *testing.T) {
	key := testKey(t) // known to the attacker
	victim, attacker := identity.GenerateDevice(), identity.GenerateDevice()
	seal := func(nonce []byte, plain []byte) []byte {
		aead, err := chacha20poly1305.NewX(key[:])
		if err != nil {
			t.Fatal(err)
		}
		b := append([]byte{lanVersion}, nonce...)
		return aead.Seal(b, nonce, plain, []byte{lanVersion})
	}
	nonce := make([]byte, chacha20poly1305.NonceSizeX)
	rand.Read(nonce)
	body := func(id identity.ID, port uint16, sig []byte) []byte {
		p := append([]byte(nil), id[:]...)
		p = binary.BigEndian.AppendUint16(p, port)
		return append(p, sig...)
	}
	// Names the victim, signed by the attacker.
	forged := seal(nonce, body(victim.ID, 4444, ed25519.Sign(attacker.Priv, beaconSigned(nonce, victim.ID, 4444))))
	if _, _, ok := decodeBeacon(key, forged); ok {
		t.Fatal("a beacon naming a member but signed by somebody else was accepted")
	}
	// No signature at all (the previous format's body).
	if _, _, ok := decodeBeacon(key, seal(nonce, body(victim.ID, 4444, make([]byte, ed25519.SignatureSize)))); ok {
		t.Fatal("an unsigned beacon was accepted")
	}
	// A genuine beacon of the victim, with the port changed by somebody who re-seals it.
	real := encodeBeacon(key, victim, 41710)
	aead, _ := chacha20poly1305.NewX(key[:])
	n := real[1 : 1+aead.NonceSize()]
	plain, err := aead.Open(nil, n, real[1+aead.NonceSize():], []byte{lanVersion})
	if err != nil {
		t.Fatal(err)
	}
	binary.BigEndian.PutUint16(plain[32:34], 4444)
	if _, _, ok := decodeBeacon(key, seal(n, plain)); ok {
		t.Fatal("a genuine beacon with its port rewritten was accepted")
	}
	// ...and with a fresh nonce the old signature does not carry over.
	nonce2 := make([]byte, chacha20poly1305.NonceSizeX)
	rand.Read(nonce2)
	plain2, _ := aead.Open(nil, n, real[1+aead.NonceSize():], []byte{lanVersion})
	if _, _, ok := decodeBeacon(key, seal(nonce2, plain2)); ok {
		t.Fatal("a genuine beacon was accepted under a nonce it was not signed for")
	}
	// The genuine one is fine.
	if id, port, ok := decodeBeacon(key, real); !ok || id != victim.ID || port != 41710 {
		t.Fatalf("the genuine beacon was refused (ok=%v port=%d)", ok, port)
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
