package proxy

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net"
	"sync"
	"sync/atomic"
	"time"
)

// Dialer opens a connection to a target through Tor.
type Dialer interface {
	DialContext(ctx context.Context, network, address string) (net.Conn, error)
}

// LogFunc receives proxy diagnostics.
type LogFunc func(level, msg string)

// Stats counts what the local listeners have carried.
type Stats struct {
	SOCKSAddr    string `json:"socksAddr"`
	HTTPAddr     string `json:"httpAddr"`
	Active       int64  `json:"active"`
	Total        int64  `json:"total"`
	Failed       int64  `json:"failed"`
	BytesToTor   int64  `json:"bytesToTor"`
	BytesFromTor int64  `json:"bytesFromTor"`
}

// Server runs the loopback SOCKS5 and HTTP listeners.
//
// It binds to 127.0.0.1 only. Binding to a routable address would turn the
// machine into an open proxy for the local network, which is a far larger
// exposure than anything TorVeil is trying to protect against.
type Server struct {
	dialer Dialer
	log    LogFunc

	mu      sync.Mutex
	socksLn net.Listener
	httpLn  net.Listener
	cancel  context.CancelFunc
	wg      sync.WaitGroup
	running bool

	active  atomic.Int64
	total   atomic.Int64
	failed  atomic.Int64
	toTor   atomic.Int64
	fromTor atomic.Int64
}

// New creates a proxy server.
func New(dialer Dialer, log LogFunc) *Server {
	return &Server{dialer: dialer, log: log}
}

func (s *Server) logf(level, format string, args ...any) {
	if s.log != nil {
		s.log(level, fmt.Sprintf(format, args...))
	}
}

// Start binds the listeners. A port of 0 in either address asks the OS to
// choose one; use SOCKSAddr and HTTPAddr afterwards to learn what was bound.
func (s *Server) Start(socksAddr, httpAddr string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.running {
		return errors.New("proxy server is already running")
	}

	socksLn, err := listenLoopback(socksAddr)
	if err != nil {
		return fmt.Errorf("bind SOCKS listener: %w", err)
	}
	httpLn, err := listenLoopback(httpAddr)
	if err != nil {
		socksLn.Close()
		return fmt.Errorf("bind HTTP listener: %w", err)
	}

	ctx, cancel := context.WithCancel(context.Background())
	s.socksLn, s.httpLn, s.cancel, s.running = socksLn, httpLn, cancel, true

	s.wg.Add(2)
	go s.accept(ctx, socksLn, s.serveSOCKS, "socks")
	go s.accept(ctx, httpLn, s.serveHTTP, "http")

	s.logf("info", "local proxy listening: socks5://%s, http://%s", socksLn.Addr(), httpLn.Addr())
	return nil
}

// listenLoopback binds addr, forcing the host to loopback when none is given.
func listenLoopback(addr string) (net.Listener, error) {
	host, port, err := net.SplitHostPort(addr)
	if err != nil {
		return nil, fmt.Errorf("parse listen address %q: %w", addr, err)
	}
	if host == "" {
		host = "127.0.0.1"
	}
	if ip := net.ParseIP(host); ip == nil || !ip.IsLoopback() {
		return nil, fmt.Errorf("refusing to listen on non-loopback address %q", host)
	}
	return net.Listen("tcp", net.JoinHostPort(host, port))
}

// Stop closes the listeners and waits for in-flight connections to finish.
func (s *Server) Stop() {
	s.mu.Lock()
	if !s.running {
		s.mu.Unlock()
		return
	}
	s.running = false
	cancel, socksLn, httpLn := s.cancel, s.socksLn, s.httpLn
	s.socksLn, s.httpLn, s.cancel = nil, nil, nil
	s.mu.Unlock()

	cancel()
	if socksLn != nil {
		socksLn.Close()
	}
	if httpLn != nil {
		httpLn.Close()
	}
	s.wg.Wait()
}

// SOCKSAddr returns the bound SOCKS address, or "" when not running.
func (s *Server) SOCKSAddr() string {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.socksLn == nil {
		return ""
	}
	return s.socksLn.Addr().String()
}

// HTTPAddr returns the bound HTTP address, or "" when not running.
func (s *Server) HTTPAddr() string {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.httpLn == nil {
		return ""
	}
	return s.httpLn.Addr().String()
}

// Stats returns a snapshot of the counters.
func (s *Server) Stats() Stats {
	return Stats{
		SOCKSAddr:    s.SOCKSAddr(),
		HTTPAddr:     s.HTTPAddr(),
		Active:       s.active.Load(),
		Total:        s.total.Load(),
		Failed:       s.failed.Load(),
		BytesToTor:   s.toTor.Load(),
		BytesFromTor: s.fromTor.Load(),
	}
}

func (s *Server) accept(ctx context.Context, ln net.Listener, handle func(context.Context, net.Conn), kind string) {
	defer s.wg.Done()
	for {
		conn, err := ln.Accept()
		if err != nil {
			select {
			case <-ctx.Done():
				return
			default:
			}
			if errors.Is(err, net.ErrClosed) {
				return
			}
			s.logf("warn", "%s accept: %v", kind, err)
			// Back off briefly so a persistent accept error does not spin.
			select {
			case <-time.After(100 * time.Millisecond):
			case <-ctx.Done():
				return
			}
			continue
		}
		s.total.Add(1)
		s.active.Add(1)
		s.wg.Add(1)
		go func() {
			defer s.wg.Done()
			defer s.active.Add(-1)
			handle(ctx, conn)
		}()
	}
}

// pipe copies in both directions until either side closes, then shuts the
// other side's write half down so the peer sees a clean EOF rather than a
// reset.
func (s *Server) pipe(client, upstream net.Conn) {
	var wg sync.WaitGroup
	wg.Add(2)

	go func() {
		defer wg.Done()
		n, _ := io.Copy(upstream, client)
		s.toTor.Add(n)
		closeWrite(upstream)
	}()
	go func() {
		defer wg.Done()
		n, _ := io.Copy(client, upstream)
		s.fromTor.Add(n)
		closeWrite(client)
	}()

	wg.Wait()
}

// closeWrite half-closes a connection when the type supports it.
func closeWrite(c net.Conn) {
	type closeWriter interface{ CloseWrite() error }
	if cw, ok := c.(closeWriter); ok {
		_ = cw.CloseWrite()
		return
	}
	_ = c.Close()
}
