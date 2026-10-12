package mesh

import (
	"net"
	"testing"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
)

// A phone whose VPN takes what it sends to the group and to broadcast addresses (it lets plain local addresses through)
// is heard by nobody; but it hears the devices around, and a device that hears it answers straight at its address. Here
// the phone is a bare socket that sends one packet to the Mac's address: the Mac answers with its announcement, so the
// phone lists the Mac and can be invited by it, and it answers once per short while, not to every packet.
func TestLANAnswersADeviceHeardStraightAtItsAddress(t *testing.T) {
	port := freeUDPPort(t)
	mac := lanNode(t, "mac", port)
	if err := mac.CreateMesh("Home", "mac", "andrey"); err != nil {
		t.Fatal(err)
	}
	waitFor(t, 10*time.Second, "the Mac to announce that it can add devices", func() bool { return mac.nearbyAdvert() != nil })

	phone := identity.GenerateDevice()
	sock, err := net.ListenUDP("udp4", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1)})
	if err != nil {
		t.Fatal(err)
	}
	defer sock.Close()
	newcomer := (&nearbyNewcomer{ID: phone.ID, Name: "phone", OS: "android"}).encode(phone)
	macAddr := &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1), Port: port}

	buf := make([]byte, nearbyPacketMax)
	read := func(wait time.Duration) (nearbyAnnouncement, bool) {
		_ = sock.SetReadDeadline(time.Now().Add(wait))
		for {
			n, err := sock.Read(buf)
			if err != nil {
				return nearbyAnnouncement{}, false
			}
			if a, ok := decodeNearbyAnnouncement(buf[:n]); ok {
				return a, true
			}
		}
	}
	var got nearbyAnnouncement
	waitFor(t, 10*time.Second, "the Mac to answer the phone at its address", func() bool {
		if _, err := sock.WriteToUDP(newcomer, macAddr); err != nil {
			t.Fatal(err)
		}
		a, ok := read(time.Second)
		got = a
		return ok
	})
	if got.ID != mac.ID() || got.MeshName != "Home" || got.Name != "mac" {
		t.Fatalf("the answer announces %+v", got)
	}
	if _, err := sock.WriteToUDP(newcomer, macAddr); err != nil {
		t.Fatal(err)
	}
	if a, ok := read(nearbyReplyEvery / 2); ok {
		t.Fatalf("a second answer a moment after the first: %+v", a)
	}
}
