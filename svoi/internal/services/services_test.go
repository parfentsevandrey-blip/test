package services

import (
	"bytes"
	"context"
	"crypto/rand"
	"encoding/binary"
	"io"
	"net"
	"strconv"
	"testing"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/mesh"
	"github.com/parfentsevandrey-blip/test/svoi/internal/meshtest"
)

// echoServer accepts connections and echoes everything until the client half-closes.
func echoServer(t *testing.T) (addr string, port int) {
	t.Helper()
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { ln.Close() })
	go func() {
		for {
			c, err := ln.Accept()
			if err != nil {
				return
			}
			go func() {
				defer c.Close()
				io.Copy(c, c)
				if tc, ok := c.(*net.TCPConn); ok {
					tc.CloseWrite()
				}
			}()
		}
	}()
	return ln.Addr().String(), ln.Addr().(*net.TCPAddr).Port
}

type env struct {
	nas, laptop *mesh.Node
	nasM        *Manager
	lapM        *Manager
	port        int
	svcs        *[]Service
}

func setup(t *testing.T) *env {
	t.Helper()
	h := meshtest.New(t)
	nas := h.Public("nas", "198.51.100.1")
	laptop := h.Public("laptop", "198.51.100.2")
	h.Mesh(nas, laptop)
	addr, port := echoServer(t)
	svcs := &[]Service{{ID: "sv1", Name: "echo", Addr: addr, Description: "Echo", Allow: []string{"*"}}}
	nasM := NewManager(nas, func() []Service { return *svcs })
	nasM.RegisterRPC()
	lapM := NewManager(laptop, func() []Service { return nil })
	lapM.RegisterRPC()
	return &env{nas: nas, laptop: laptop, nasM: nasM, lapM: lapM, port: port, svcs: svcs}
}

func TestDialTunnelsBothDirectionsWithHalfClose(t *testing.T) {
	e := setup(t)
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	list, err := e.lapM.RemoteServices(ctx, e.nas.ID())
	if err != nil || len(list) != 1 || list[0].Name != "echo" || list[0].Port != e.port {
		t.Fatalf("remote services: %+v %v", list, err)
	}
	c, err := e.lapM.Dial(ctx, e.nas.ID(), "echo", 0)
	if err != nil {
		t.Fatal(err)
	}
	payload := make([]byte, 2<<20)
	rand.Read(payload)
	go func() {
		c.Write(payload)
		c.CloseWrite() // the echo server only finishes after seeing our EOF
	}()
	got, err := io.ReadAll(c)
	c.Close()
	if err != nil || !bytes.Equal(got, payload) {
		t.Fatalf("echo mismatch: %d bytes, err=%v", len(got), err)
	}
	// By port instead of name.
	c2, err := e.lapM.Dial(ctx, e.nas.ID(), "", e.port)
	if err != nil {
		t.Fatal(err)
	}
	c2.Write([]byte("ping"))
	c2.CloseWrite()
	b, _ := io.ReadAll(c2)
	c2.Close()
	if string(b) != "ping" {
		t.Fatalf("dial by port: %q", b)
	}
}

func TestAccessControlAndFailures(t *testing.T) {
	e := setup(t)
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()

	// A service restricted to someone else is invisible and cannot be dialed.
	(*e.svcs)[0].Allow = []string{identity.GenerateDevice().ID.String()}
	if list, _ := e.lapM.RemoteServices(ctx, e.nas.ID()); len(list) != 0 {
		t.Fatalf("restricted service is visible: %+v", list)
	}
	if _, err := e.lapM.Dial(ctx, e.nas.ID(), "echo", 0); !mesh.IsCode(err, mesh.CodeNotFound) {
		t.Fatalf("dial restricted: %v", err)
	}
	if _, err := e.lapM.Dial(ctx, e.nas.ID(), "nope", 0); !mesh.IsCode(err, mesh.CodeNotFound) {
		t.Fatalf("dial unknown: %v", err)
	}
	// Allowed again, but the process behind it is gone.
	(*e.svcs)[0].Allow = []string{"*"}
	(*e.svcs)[0].Addr = "127.0.0.1:1" // nothing listens there
	if _, err := e.lapM.Dial(ctx, e.nas.ID(), "echo", 0); !mesh.IsCode(err, mesh.CodeOffline) {
		t.Fatalf("dial dead service: %v", err)
	}
	if _, err := e.lapM.Dial(ctx, identity.GenerateDevice().ID, "echo", 0); !mesh.IsCode(err, mesh.CodeNotFound) {
		t.Fatalf("dial unknown device: %v", err)
	}
}

func TestForwarder(t *testing.T) {
	e := setup(t)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	changed := make(chan struct{}, 10)
	f := NewForwarder(e.lapM, func() { changed <- struct{}{} })
	f.Start(ctx)

	fw, err := f.Open(Forward{ID: "fw1", Peer: e.nas.ID(), Service: "echo", Listen: "127.0.0.1:0"})
	if err != nil {
		t.Fatal(err)
	}
	if fw.Listen == "127.0.0.1:0" {
		t.Fatal("real port not reported")
	}
	st := f.Status(fw)
	if st.State != "listening" || st.PeerName != "nas" {
		t.Fatalf("status: %+v", st)
	}
	conn, err := net.DialTimeout("tcp", fw.Listen, 3*time.Second)
	if err != nil {
		t.Fatal(err)
	}
	conn.Write([]byte("through the mesh"))
	conn.(*net.TCPConn).CloseWrite()
	got, _ := io.ReadAll(conn)
	conn.Close()
	if string(got) != "through the mesh" {
		t.Fatalf("forward echo: %q", got)
	}
	// Port conflicts are reported, not fatal.
	if _, err := f.Open(Forward{ID: "fw2", Peer: e.nas.ID(), Service: "echo", Listen: fw.Listen}); err == nil {
		t.Fatal("binding an occupied port succeeded")
	}
	if st := f.Status(Forward{ID: "fw2", Peer: e.nas.ID()}); st.State != "error" {
		t.Fatalf("occupied port status: %+v", st)
	}
	f.Close("fw1")
	if st := f.Status(fw); st.State != "stopped" {
		t.Fatalf("after close: %+v", st)
	}
	if c, err := net.DialTimeout("tcp", fw.Listen, 500*time.Millisecond); err == nil {
		c.Close()
		t.Fatal("listener still open after Close")
	}
}

// socksConnect performs a SOCKS5 CONNECT by domain name and returns the reply code.
func socksConnect(t *testing.T, proxy, host string, port int) (net.Conn, byte) {
	t.Helper()
	c, err := net.DialTimeout("tcp", proxy, 3*time.Second)
	if err != nil {
		t.Fatal(err)
	}
	c.SetDeadline(time.Now().Add(15 * time.Second))
	c.Write([]byte{5, 1, 0})
	var sel [2]byte
	if _, err := io.ReadFull(c, sel[:]); err != nil || sel[1] != 0 {
		t.Fatalf("method selection: %v %v", sel, err)
	}
	req := []byte{5, 1, 0, 3, byte(len(host))}
	req = append(req, host...)
	req = binary.BigEndian.AppendUint16(req, uint16(port))
	c.Write(req)
	var rep [10]byte
	if _, err := io.ReadFull(c, rep[:]); err != nil {
		t.Fatal(err)
	}
	return c, rep[1]
}

func TestSOCKS5ProxyReachesMeshServices(t *testing.T) {
	e := setup(t)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	srv, err := e.lapM.ListenSOCKS(ctx, "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}

	for _, host := range []string{"nas.mesh", "nas", e.nas.Self().IP4} {
		c, code := socksConnect(t, srv.Addr(), host, e.port)
		if code != 0 {
			t.Fatalf("connect to %s: reply %d", host, code)
		}
		c.Write([]byte("hello " + host))
		c.(*net.TCPConn).CloseWrite()
		b, _ := io.ReadAll(c)
		c.Close()
		if string(b) != "hello "+host {
			t.Fatalf("via %s got %q", host, b)
		}
	}
	// Unknown host -> host unreachable (4); known host but unpublished port -> refused (5).
	if c, code := socksConnect(t, srv.Addr(), "ghost.mesh", e.port); code != 4 {
		t.Fatalf("unknown host reply %d", code)
	} else {
		c.Close()
	}
	if c, code := socksConnect(t, srv.Addr(), "nas.mesh", e.port+1); code != 5 {
		t.Fatalf("unpublished port reply %d", code)
	} else {
		c.Close()
	}
	// Non-CONNECT commands and auth-requiring clients are rejected politely.
	c, _ := net.Dial("tcp", srv.Addr())
	c.Write([]byte{5, 1, 2}) // only username/password offered
	var sel [2]byte
	io.ReadFull(c, sel[:])
	if sel[1] != 0xFF {
		t.Fatalf("expected no-acceptable-method, got %v", sel)
	}
	c.Close()
	_ = strconv.Itoa
}
