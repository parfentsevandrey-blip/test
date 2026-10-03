package magic

import (
	"net"
	"net/netip"
	"testing"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
)

// The other tests of this package run on a simulated Internet. This one puts the operating system's
// own UDP sockets under the exchange a joining device has with the one that invited it (two copies of
// the program on one machine talk the same way): whatever differs between systems in how a datagram
// is sent, received and addressed (a dual-stack or an IPv4-only socket, how an IPv4 sender looks when
// it is read back) shows up here.
func TestAnonymousTrafficOverRealSockets(t *testing.T) {
	open := func() *Conn {
		c, err := New(Config{
			Device:     identity.GenerateDevice(),
			LocalAddrs: func() []netip.Addr { return []netip.Addr{ip("127.0.0.1")} },
			Timing:     fastTiming,
			Logf:       t.Logf,
		})
		if err != nil {
			t.Fatal(err)
		}
		t.Cleanup(func() { c.Close() })
		c.SetAnonymous(true)
		return c
	}
	a, b := open(), open()
	t.Logf("the sockets: a on port %d, b on port %d", a.port, b.port)

	target := &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1), Port: int(b.port)}
	if _, err := a.WriteTo([]byte("join me"), target); err != nil {
		t.Fatal(err)
	}
	_ = b.SetReadDeadline(time.Now().Add(3 * time.Second))
	buf := make([]byte, 100)
	n, from, err := b.ReadFrom(buf)
	if err != nil || string(buf[:n]) != "join me" {
		t.Fatalf("the request did not arrive: %v %q", err, buf[:n])
	}
	t.Logf("b read it from %v", from)
	if _, err := b.WriteTo([]byte("welcome"), from); err != nil {
		t.Fatal(err)
	}
	_ = a.SetReadDeadline(time.Now().Add(3 * time.Second))
	n, _, err = a.ReadFrom(buf)
	if err != nil || string(buf[:n]) != "welcome" {
		t.Fatalf("the answer did not arrive: %v %q", err, buf[:n])
	}
}
