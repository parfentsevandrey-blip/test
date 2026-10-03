package services

import (
	"context"
	"encoding/binary"
	"io"
	"net"
	"net/netip"
	"strings"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/mesh"
)

// A tiny SOCKS5 server (CONNECT only, no authentication, loopback by default)
// that routes connections to devices of the mesh: `ssh -o
// ProxyCommand='nc -X 5 -x 127.0.0.1:1080 %h %p' nas.svoi` or any application
// with a SOCKS setting can reach `nas.svoi:22` or `100.64.x.y:22`, provided the
// target device publishes a service on that port.

// SOCKSServer is a SOCKS5 listener bound to the mesh.
type SOCKSServer struct {
	m  *Manager
	ln net.Listener
}

// ListenSOCKS starts the proxy on addr (for example "127.0.0.1:1080").
func (m *Manager) ListenSOCKS(ctx context.Context, addr string) (*SOCKSServer, error) {
	ln, err := net.Listen("tcp", addr)
	if err != nil {
		return nil, err
	}
	s := &SOCKSServer{m: m, ln: ln}
	go func() {
		<-ctx.Done()
		ln.Close()
	}()
	go s.serve(ctx)
	return s, nil
}

// Addr returns the listening address.
func (s *SOCKSServer) Addr() string { return s.ln.Addr().String() }

// Close stops the proxy.
func (s *SOCKSServer) Close() error { return s.ln.Close() }

func (s *SOCKSServer) serve(ctx context.Context) {
	for {
		c, err := s.ln.Accept()
		if err != nil {
			return
		}
		go s.handle(ctx, c)
	}
}

const (
	socksVer         = 5
	socksCmdConnect  = 1
	socksAtypIPv4    = 1
	socksAtypDomain  = 3
	socksAtypIPv6    = 4
	socksOK          = 0
	socksFail        = 1
	socksHostUnreach = 4
	socksRefused     = 5
	socksCmdUnsup    = 7
)

func (s *SOCKSServer) handle(ctx context.Context, c net.Conn) {
	defer c.Close()
	_ = c.SetDeadline(time.Now().Add(15 * time.Second))
	// Greeting: accept "no authentication" only.
	var hdr [2]byte
	if _, err := io.ReadFull(c, hdr[:]); err != nil || hdr[0] != socksVer {
		return
	}
	methods := make([]byte, hdr[1])
	if _, err := io.ReadFull(c, methods); err != nil {
		return
	}
	if !strings.ContainsRune(string(methods), 0) {
		_, _ = c.Write([]byte{socksVer, 0xFF})
		return
	}
	_, _ = c.Write([]byte{socksVer, 0})

	var req [4]byte
	if _, err := io.ReadFull(c, req[:]); err != nil || req[0] != socksVer {
		return
	}
	if req[1] != socksCmdConnect {
		reply(c, socksCmdUnsup)
		return
	}
	var host string
	switch req[3] {
	case socksAtypIPv4:
		var b [4]byte
		if _, err := io.ReadFull(c, b[:]); err != nil {
			return
		}
		host = netip.AddrFrom4(b).String()
	case socksAtypIPv6:
		var b [16]byte
		if _, err := io.ReadFull(c, b[:]); err != nil {
			return
		}
		host = netip.AddrFrom16(b).String()
	case socksAtypDomain:
		var l [1]byte
		if _, err := io.ReadFull(c, l[:]); err != nil {
			return
		}
		b := make([]byte, l[0])
		if _, err := io.ReadFull(c, b); err != nil {
			return
		}
		host = string(b)
	default:
		reply(c, socksFail)
		return
	}
	var pb [2]byte
	if _, err := io.ReadFull(c, pb[:]); err != nil {
		return
	}
	port := int(binary.BigEndian.Uint16(pb[:]))

	peer := s.resolve(host)
	if peer == nil {
		reply(c, socksHostUnreach)
		return
	}
	dctx, cancel := context.WithTimeout(ctx, 15*time.Second)
	remote, err := s.m.Dial(dctx, peer.ID, "", port)
	cancel()
	if err != nil {
		switch {
		case mesh.IsCode(err, mesh.CodeNotFound):
			reply(c, socksRefused)
		case mesh.IsCode(err, mesh.CodeOffline):
			reply(c, socksHostUnreach)
		default:
			reply(c, socksFail)
		}
		return
	}
	reply(c, socksOK)
	_ = c.SetDeadline(time.Time{})
	Pipe(c, remote)
}

func (s *SOCKSServer) resolve(host string) *mesh.Peer {
	host = strings.TrimSuffix(strings.ToLower(host), ".")
	if a, err := netip.ParseAddr(host); err == nil {
		return s.m.node.PeerByIP(a)
	}
	return s.m.node.FindPeer(host)
}

func reply(c net.Conn, code byte) {
	_, _ = c.Write([]byte{socksVer, code, 0, socksAtypIPv4, 0, 0, 0, 0, 0, 0})
}
