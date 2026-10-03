package magic

import (
	"bytes"
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
	// Small packets, and the sizes QUIC uses: its first packets are 1280 bytes, with the one byte this layer
	// puts in front of them.
	for _, size := range []int{7, 600, 1200, 1280, 1281, 1452, 1500} {
		request := bytes.Repeat([]byte{'q'}, size)
		answer := bytes.Repeat([]byte{'a'}, size)
		if _, err := a.WriteTo(request, target); err != nil {
			t.Fatalf("%d bytes: %v", size, err)
		}
		_ = b.SetReadDeadline(time.Now().Add(3 * time.Second))
		buf := make([]byte, 4096)
		n, from, err := b.ReadFrom(buf)
		if err != nil || !bytes.Equal(buf[:n], request) {
			t.Fatalf("%d bytes: the request did not arrive: n=%d err=%v", size, n, err)
		}
		if _, err := b.WriteTo(answer, from); err != nil {
			t.Fatalf("%d bytes: %v", size, err)
		}
		_ = a.SetReadDeadline(time.Now().Add(3 * time.Second))
		n, _, err = a.ReadFrom(buf)
		if err != nil || !bytes.Equal(buf[:n], answer) {
			t.Fatalf("%d bytes: the answer did not arrive: n=%d err=%v", size, n, err)
		}
		t.Logf("%d bytes: there and back (b saw the sender as %v)", size, from)
	}
}
