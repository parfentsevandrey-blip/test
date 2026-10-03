package mesh

import (
	"context"
	"crypto/tls"
	"net"
	"net/netip"
	"runtime"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/quic-go/quic-go"

	"github.com/parfentsevandrey-blip/test/svoi/internal/netsim"
)

// anonConn lets a stranger speak QUIC to a node the way a joiner does (packets
// wrapped as "anonymous" 0xA4), counting what it sends.
type anonConn struct {
	sock *netsim.Socket
	to   net.Addr
	sent atomic.Int64
	recv atomic.Int64
	done chan struct{}
	wake chan struct{}
	once sync.Once
}

func (c *anonConn) WriteTo(p []byte, _ net.Addr) (int, error) {
	pkt := append([]byte{0xA4}, p...)
	c.sent.Add(int64(len(pkt)))
	_, err := c.sock.WriteTo(pkt, c.to)
	return len(p), err
}

// ReadFrom never delivers anything: this stranger is silent (or spoofing its
// source), so no handshake of its ever progresses beyond the first flight. It
// returns when the transport asks to stop reading (a deadline in the past).
func (c *anonConn) ReadFrom(p []byte) (int, net.Addr, error) {
	select {
	case <-c.done:
	case <-c.wake:
	}
	return 0, nil, net.ErrClosed
}
func (c *anonConn) Close() error {
	c.once.Do(func() { close(c.wake) })
	close(c.done)
	return c.sock.Close()
}
func (c *anonConn) LocalAddr() net.Addr           { return c.sock.LocalAddr() }
func (c *anonConn) SetDeadline(t time.Time) error { return c.sock.SetDeadline(t) }
func (c *anonConn) SetReadDeadline(t time.Time) error {
	if !t.IsZero() && time.Until(t) < time.Second {
		c.once.Do(func() { close(c.wake) })
	}
	return nil
}
func (c *anonConn) SetWriteDeadline(t time.Time) error { return c.sock.SetWriteDeadline(t) }

// While an invitation is pending the node listens to anyone. A stranger must not
// be able to make it hold state per forged handshake or answer with large flights
// to a spoofed address: unvalidated sources only get a Retry.
func TestAnonymousHandshakeFloodCostsNothing(t *testing.T) {
	nw := netsim.New()
	a := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.1")), "alpha", nil)
	if err := a.CreateMesh("Home", "alpha", "x"); err != nil {
		t.Fatal(err)
	}
	if _, err := a.NewInvite(false, 30*time.Minute); err != nil { // an invitation is pending
		t.Fatal(err)
	}
	hostX := nw.Internet().NewHost(ip("198.51.100.66"))
	xs, err := hostX.ListenPacket(5555)
	if err != nil {
		t.Fatal(err)
	}
	conn := &anonConn{sock: xs, to: net.UDPAddrFromAddrPort(netip.MustParseAddrPort("198.51.100.1:41710")), done: make(chan struct{}), wake: make(chan struct{})}
	go func() { // what the node sends back goes to the "spoofed" address: we only count it
		buf := make([]byte, 4096)
		for {
			n, _, err := xs.ReadFrom(buf)
			if err != nil {
				return
			}
			conn.recv.Add(int64(n))
		}
	}()
	tr := &quic.Transport{Conn: conn}
	defer tr.Close()

	var m0, m1 runtime.MemStats
	runtime.GC()
	runtime.ReadMemStats(&m0)
	g0 := runtime.NumGoroutine()

	const attempts = 600
	var wg sync.WaitGroup
	sem := make(chan struct{}, 100)
	for i := 0; i < attempts; i++ {
		wg.Add(1)
		sem <- struct{}{}
		go func() {
			defer wg.Done()
			defer func() { <-sem }()
			ctx, cancel := context.WithTimeout(context.Background(), 150*time.Millisecond)
			defer cancel()
			// A client that never completes address validation (it "spoofs" its source:
			// here it simply does not follow the Retry because it gives up after 150 ms).
			_, _ = tr.Dial(ctx, &net.UDPAddr{IP: net.IPv4(10, 0, 0, 2), Port: 2}, &tls.Config{
				InsecureSkipVerify: true, NextProtos: []string{"themesh/1"}, ServerName: "themesh", MinVersion: tls.VersionTLS13,
			}, &quic.Config{HandshakeIdleTimeout: 200 * time.Millisecond})
		}()
	}
	wg.Wait()
	time.Sleep(700 * time.Millisecond)
	runtime.ReadMemStats(&m1)
	g1 := runtime.NumGoroutine()
	grown := int64(m1.HeapInuse) - int64(m0.HeapInuse)
	t.Logf("%d forged handshakes: heap %+d KB, goroutines %+d, sent %d B, answered %d B", attempts, grown>>10, g1-g0, conn.sent.Load(), conn.recv.Load())
	if g1-g0 > 150 {
		t.Errorf("the node holds %d extra goroutines after a flood of unvalidated handshakes", g1-g0)
	}
	if grown > 30<<20 {
		t.Errorf("heap grew by %d MB", grown>>20)
	}
	// Whatever is sent back to an unvalidated address must not exceed what was received (no amplification).
	if conn.recv.Load() > conn.sent.Load() {
		t.Errorf("amplification: sent %d bytes, got %d back", conn.sent.Load(), conn.recv.Load())
	}
}
