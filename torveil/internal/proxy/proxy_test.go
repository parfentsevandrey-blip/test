package proxy

import (
	"bufio"
	"context"
	"encoding/binary"
	"io"
	"net"
	"net/http"
	"strings"
	"sync"
	"testing"
	"time"
)

// fakeTor stands in for Tor's SOCKS port. It records the target each client
// asked for, then pipes the connection to an echo service.
type fakeTor struct {
	ln net.Listener

	mu      sync.Mutex
	targets []string
}

func newFakeTor(t *testing.T) *fakeTor {
	t.Helper()
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("listen: %v", err)
	}
	f := &fakeTor{ln: ln}
	go f.serve()
	t.Cleanup(func() { ln.Close() })
	return f
}

func (f *fakeTor) addr() string { return f.ln.Addr().String() }

func (f *fakeTor) seenTargets() []string {
	f.mu.Lock()
	defer f.mu.Unlock()
	return append([]string(nil), f.targets...)
}

func (f *fakeTor) serve() {
	for {
		conn, err := f.ln.Accept()
		if err != nil {
			return
		}
		go f.handle(conn)
	}
}

func (f *fakeTor) handle(conn net.Conn) {
	defer conn.Close()

	head := make([]byte, 2)
	if _, err := io.ReadFull(conn, head); err != nil {
		return
	}
	methods := make([]byte, head[1])
	if _, err := io.ReadFull(conn, methods); err != nil {
		return
	}
	if _, err := conn.Write([]byte{socks5Version, authNone}); err != nil {
		return
	}

	req := make([]byte, 4)
	if _, err := io.ReadFull(conn, req); err != nil {
		return
	}
	var host string
	switch req[3] {
	case atypDomain:
		l := make([]byte, 1)
		if _, err := io.ReadFull(conn, l); err != nil {
			return
		}
		b := make([]byte, l[0])
		if _, err := io.ReadFull(conn, b); err != nil {
			return
		}
		host = string(b)
	case atypIPv4:
		b := make([]byte, 4)
		if _, err := io.ReadFull(conn, b); err != nil {
			return
		}
		host = net.IP(b).String()
	default:
		return
	}
	pb := make([]byte, 2)
	if _, err := io.ReadFull(conn, pb); err != nil {
		return
	}

	f.mu.Lock()
	f.targets = append(f.targets, host+":"+itoa(int(binary.BigEndian.Uint16(pb))))
	f.mu.Unlock()

	if _, err := conn.Write([]byte{socks5Version, repSucceeded, 0, atypIPv4, 0, 0, 0, 0, 0, 0}); err != nil {
		return
	}
	// Echo everything back, so the test can prove the tunnel carries data.
	_, _ = io.Copy(conn, conn)
}

func itoa(n int) string {
	if n == 0 {
		return "0"
	}
	var b [8]byte
	i := len(b)
	for n > 0 {
		i--
		b[i] = byte('0' + n%10)
		n /= 10
	}
	return string(b[i:])
}

func startServer(t *testing.T, upstreamAddr string) *Server {
	t.Helper()
	up := &Upstream{Address: upstreamAddr, Timeout: 5 * time.Second}
	srv := New(up, nil)
	if err := srv.Start("127.0.0.1:0", "127.0.0.1:0"); err != nil {
		t.Fatalf("start proxy: %v", err)
	}
	t.Cleanup(srv.Stop)
	return srv
}

// socksConnect performs a client-side SOCKS5 CONNECT against addr.
func socksConnect(t *testing.T, proxyAddr, target string) net.Conn {
	t.Helper()
	conn, err := net.DialTimeout("tcp", proxyAddr, 5*time.Second)
	if err != nil {
		t.Fatalf("dial proxy: %v", err)
	}
	_ = conn.SetDeadline(time.Now().Add(5 * time.Second))

	if _, err := conn.Write([]byte{socks5Version, 1, authNone}); err != nil {
		t.Fatalf("greeting: %v", err)
	}
	resp := make([]byte, 2)
	if _, err := io.ReadFull(conn, resp); err != nil {
		t.Fatalf("greeting reply: %v", err)
	}

	host, portStr, _ := net.SplitHostPort(target)
	port := 0
	for _, c := range portStr {
		port = port*10 + int(c-'0')
	}
	req, err := buildRequest(cmdConnect, host, port)
	if err != nil {
		t.Fatalf("build request: %v", err)
	}
	if _, err := conn.Write(req); err != nil {
		t.Fatalf("connect request: %v", err)
	}
	head := make([]byte, 4)
	if _, err := io.ReadFull(conn, head); err != nil {
		t.Fatalf("connect reply: %v", err)
	}
	if head[1] != repSucceeded {
		t.Fatalf("SOCKS reply code %#x: %s", head[1], replyMessage(head[1]))
	}
	if err := discardAddress(conn, head[3]); err != nil {
		t.Fatalf("bound address: %v", err)
	}
	return conn
}

// TestHostnamesReachTorUnresolved is the important one: if TorVeil ever
// resolved a hostname itself, the query would go to the local resolver and
// leak the destination no matter how well the connection is protected.
func TestHostnamesReachTorUnresolved(t *testing.T) {
	tor := newFakeTor(t)
	srv := startServer(t, tor.addr())

	conn := socksConnect(t, srv.SOCKSAddr(), "secret.example.com:443")
	defer conn.Close()

	targets := tor.seenTargets()
	if len(targets) != 1 {
		t.Fatalf("Tor saw %d connections, want 1", len(targets))
	}
	if targets[0] != "secret.example.com:443" {
		t.Errorf("Tor was asked for %q; the hostname must be passed through unresolved", targets[0])
	}
}

func TestSOCKSCarriesData(t *testing.T) {
	tor := newFakeTor(t)
	srv := startServer(t, tor.addr())

	conn := socksConnect(t, srv.SOCKSAddr(), "example.com:80")
	defer conn.Close()

	payload := "the quick brown fox"
	if _, err := conn.Write([]byte(payload)); err != nil {
		t.Fatalf("write: %v", err)
	}
	buf := make([]byte, len(payload))
	if _, err := io.ReadFull(conn, buf); err != nil {
		t.Fatalf("read: %v", err)
	}
	if string(buf) != payload {
		t.Errorf("got %q, want %q", buf, payload)
	}

	stats := srv.Stats()
	if stats.Total != 1 {
		t.Errorf("Total = %d, want 1", stats.Total)
	}
}

func TestHTTPConnectTunnel(t *testing.T) {
	tor := newFakeTor(t)
	srv := startServer(t, tor.addr())

	conn, err := net.DialTimeout("tcp", srv.HTTPAddr(), 5*time.Second)
	if err != nil {
		t.Fatalf("dial: %v", err)
	}
	defer conn.Close()
	_ = conn.SetDeadline(time.Now().Add(5 * time.Second))

	if _, err := conn.Write([]byte("CONNECT example.com:443 HTTP/1.1\r\nHost: example.com:443\r\n\r\n")); err != nil {
		t.Fatalf("write CONNECT: %v", err)
	}
	br := bufio.NewReader(conn)
	resp, err := http.ReadResponse(br, nil)
	if err != nil {
		t.Fatalf("read response: %v", err)
	}
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("status = %d, want 200", resp.StatusCode)
	}

	if targets := tor.seenTargets(); len(targets) != 1 || targets[0] != "example.com:443" {
		t.Errorf("Tor saw %v, want [example.com:443]", targets)
	}
}

func TestUDPAssociateIsRejected(t *testing.T) {
	tor := newFakeTor(t)
	srv := startServer(t, tor.addr())

	conn, err := net.DialTimeout("tcp", srv.SOCKSAddr(), 5*time.Second)
	if err != nil {
		t.Fatalf("dial: %v", err)
	}
	defer conn.Close()
	_ = conn.SetDeadline(time.Now().Add(5 * time.Second))

	if _, err := conn.Write([]byte{socks5Version, 1, authNone}); err != nil {
		t.Fatalf("greeting: %v", err)
	}
	if _, err := io.ReadFull(conn, make([]byte, 2)); err != nil {
		t.Fatalf("greeting reply: %v", err)
	}

	req, _ := buildRequest(cmdUDPAssociate, "127.0.0.1", 5300)
	if _, err := conn.Write(req); err != nil {
		t.Fatalf("request: %v", err)
	}
	head := make([]byte, 4)
	if _, err := io.ReadFull(conn, head); err != nil {
		t.Fatalf("reply: %v", err)
	}
	// Tor has no UDP transport, so the only correct answer is a clean refusal
	// rather than a connection that appears to work and silently drops.
	if head[1] != repCommandNotSupported {
		t.Errorf("reply code = %#x, want command-not-supported (%#x)", head[1], repCommandNotSupported)
	}
}

func TestUpstreamRefusesUDP(t *testing.T) {
	up := &Upstream{Address: "127.0.0.1:1"}
	_, err := up.DialContext(context.Background(), "udp", "example.com:53")
	if err == nil {
		t.Fatal("expected UDP to be refused")
	}
	if !strings.Contains(err.Error(), "TCP only") {
		t.Errorf("error should explain why UDP cannot work, got: %v", err)
	}
}

func TestListenerRefusesNonLoopback(t *testing.T) {
	srv := New(&Upstream{Address: "127.0.0.1:1"}, nil)
	err := srv.Start("0.0.0.0:0", "127.0.0.1:0")
	if err == nil {
		srv.Stop()
		t.Fatal("binding the proxy to a routable address would make it an open proxy for the LAN")
	}
	if !strings.Contains(err.Error(), "loopback") {
		t.Errorf("error should say why, got: %v", err)
	}
}
