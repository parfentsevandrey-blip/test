package mesh

import (
	"bytes"
	"crypto/rand"
	"testing"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
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
