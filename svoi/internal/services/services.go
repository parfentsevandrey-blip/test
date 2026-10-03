// Package services shares TCP services between devices: a NAS can publish its
// SSH and web UI, a laptop can bind them to local ports (port forwards) or reach
// them through a SOCKS5 proxy, all over the encrypted mesh links.
package services

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/mesh"
)

// Service is a local TCP service published to other devices.
type Service struct {
	ID          string   `json:"id"`
	Name        string   `json:"name"`
	Addr        string   `json:"addr"` // host:port on this device
	Description string   `json:"description"`
	Allow       []string `json:"allow"` // "*" or device IDs
}

// RemoteService is how a published service looks to others.
type RemoteService struct {
	Name        string `json:"name"`
	Port        int    `json:"port"`
	Description string `json:"description"`
}

const maxTunnelsPerPeer = 256

// Manager serves this device's services and dials other devices'.
type Manager struct {
	node     *mesh.Node
	services func() []Service

	mu      sync.Mutex
	tunnels map[identity.ID]int
}

// NewManager creates a manager; services returns the current definitions.
func NewManager(node *mesh.Node, services func() []Service) *Manager {
	return &Manager{node: node, services: services, tunnels: map[identity.ID]int{}}
}

func allows(s Service, peer identity.ID) bool {
	for _, a := range s.Allow {
		if a == "*" || a == peer.String() {
			return true
		}
	}
	return false
}

func portOf(addr string) int {
	_, ps, err := net.SplitHostPort(addr)
	if err != nil {
		return 0
	}
	p, _ := strconv.Atoi(ps)
	return p
}

// Visible lists the services a peer may use.
func (m *Manager) Visible(peer identity.ID) []RemoteService {
	var out []RemoteService
	for _, s := range m.services() {
		if allows(s, peer) {
			out = append(out, RemoteService{Name: s.Name, Port: portOf(s.Addr), Description: s.Description})
		}
	}
	return out
}

// RegisterRPC installs the handlers other devices call.
func (m *Manager) RegisterRPC() {
	m.node.Handle("svc.list", func(ctx context.Context, c *mesh.Call) (any, error) {
		return m.Visible(c.Peer.ID), nil
	})
	m.node.HandleStream("svc.connect", func(ctx context.Context, c *mesh.Call, s *mesh.ServerStream) error {
		var a struct {
			Name string `json:"name"`
			Port int    `json:"port"`
		}
		if err := c.Decode(&a); err != nil {
			return err
		}
		var target *Service
		for _, sv := range m.services() {
			sv := sv
			if !allows(sv, c.Peer.ID) {
				continue
			}
			if (a.Name != "" && strings.EqualFold(sv.Name, a.Name)) || (a.Name == "" && a.Port != 0 && portOf(sv.Addr) == a.Port) {
				target = &sv
				break
			}
		}
		if target == nil {
			return mesh.Errf(mesh.CodeNotFound, "no such service")
		}
		m.mu.Lock()
		if m.tunnels[c.Peer.ID] >= maxTunnelsPerPeer {
			m.mu.Unlock()
			return mesh.Errf(mesh.CodeBusy, "too many open connections")
		}
		m.tunnels[c.Peer.ID]++
		m.mu.Unlock()
		defer func() {
			m.mu.Lock()
			m.tunnels[c.Peer.ID]--
			m.mu.Unlock()
		}()

		conn, err := net.DialTimeout("tcp", target.Addr, 5*time.Second)
		if err != nil {
			return mesh.Errf(mesh.CodeOffline, "the service is not running (%v)", errShort(err))
		}
		defer conn.Close()
		if err := s.Reply(nil); err != nil {
			return err
		}
		tc, _ := conn.(*net.TCPConn)
		done := make(chan struct{}, 2)
		go func() { // peer -> service
			_, _ = io.Copy(conn, s)
			if tc != nil {
				_ = tc.CloseWrite()
			}
			done <- struct{}{}
		}()
		go func() { // service -> peer
			_, _ = io.Copy(s, conn)
			_ = s.CloseWrite()
			done <- struct{}{}
		}()
		select {
		case <-done:
			// One direction finished; give the other a moment to drain, then end.
			select {
			case <-done:
			case <-time.After(30 * time.Second):
			case <-ctx.Done():
			}
		case <-ctx.Done():
		}
		return nil
	})
}

func errShort(err error) string {
	var oe *net.OpError
	if errors.As(err, &oe) && oe.Err != nil {
		return oe.Err.Error()
	}
	return err.Error()
}

// RemoteServices lists the services a device offers us.
func (m *Manager) RemoteServices(ctx context.Context, id identity.ID) ([]RemoteService, error) {
	p := m.node.Peer(id)
	if p == nil {
		return nil, mesh.Errf(mesh.CodeNotFound, "unknown device")
	}
	if !p.Online() {
		return nil, mesh.Errf(mesh.CodeOffline, "%s is not online", p.Name())
	}
	var out []RemoteService
	if err := p.Call(ctx, "svc.list", nil, &out); err != nil {
		return nil, err
	}
	return out, nil
}

// Conn is a tunnelled TCP connection to a service on another device.
type Conn struct {
	cs *mesh.ClientStream
}

// Read implements io.Reader.
func (c *Conn) Read(p []byte) (int, error) { return c.cs.Read(p) }

// Write implements io.Writer.
func (c *Conn) Write(p []byte) (int, error) { return c.cs.Write(p) }

// CloseWrite signals that no more data will be sent.
func (c *Conn) CloseWrite() error { return c.cs.CloseWrite() }

// Close ends the connection.
func (c *Conn) Close() error { return c.cs.Close() }

// Dial opens a tunnel to a named service (or, if name is empty, to the service
// published on the given port) of a device.
func (m *Manager) Dial(ctx context.Context, id identity.ID, name string, port int) (*Conn, error) {
	p := m.node.Peer(id)
	if p == nil {
		return nil, mesh.Errf(mesh.CodeNotFound, "unknown device")
	}
	if !p.Online() {
		return nil, mesh.Errf(mesh.CodeOffline, "%s is not online", p.Name())
	}
	cs, err := p.OpenStream(ctx, "svc.connect", map[string]any{"name": name, "port": port})
	if err != nil {
		return nil, err
	}
	if err := cs.ReadResponse(nil); err != nil {
		cs.Cancel()
		return nil, err
	}
	return &Conn{cs: cs}, nil
}

// Pipe copies between a local TCP connection and a tunnel until both sides end.
func Pipe(local net.Conn, remote *Conn) {
	defer local.Close()
	defer remote.Close()
	var wg sync.WaitGroup
	wg.Add(2)
	tc, _ := local.(*net.TCPConn)
	go func() {
		defer wg.Done()
		_, _ = io.Copy(remote, local)
		_ = remote.CloseWrite()
	}()
	go func() {
		defer wg.Done()
		_, _ = io.Copy(local, remote)
		if tc != nil {
			_ = tc.CloseWrite()
		}
	}()
	wg.Wait()
}

// ---- forwards: local listeners that tunnel to a remote service ----

// Forward binds a local address to a service of another device.
type Forward struct {
	ID      string      `json:"id"`
	Peer    identity.ID `json:"peer"`
	Service string      `json:"service"`
	Listen  string      `json:"listen"`
}

// ForwardStatus is a forward with its runtime state.
type ForwardStatus struct {
	Forward
	PeerName string `json:"peerName"`
	State    string `json:"state"` // listening | error | stopped
	Error    string `json:"error"`
	Conns    int    `json:"conns"`
}

type activeForward struct {
	fw     Forward
	ln     net.Listener
	cancel context.CancelFunc
	conns  atomic.Int64
	err    string
}

// Forwarder runs the configured forwards.
type Forwarder struct {
	m        *Manager
	onChange func()

	mu     sync.Mutex
	active map[string]*activeForward
	ctx    context.Context
}

// NewForwarder creates a forwarder; onChange is called when statuses change.
func NewForwarder(m *Manager, onChange func()) *Forwarder {
	return &Forwarder{m: m, onChange: onChange, active: map[string]*activeForward{}}
}

// Start begins serving until ctx ends.
func (f *Forwarder) Start(ctx context.Context) {
	f.mu.Lock()
	f.ctx = ctx
	f.mu.Unlock()
	go func() {
		<-ctx.Done()
		f.mu.Lock()
		for _, a := range f.active {
			a.cancel()
			a.ln.Close()
		}
		f.mu.Unlock()
	}()
}

// Open starts listening for a forward and returns its actual listen address.
func (f *Forwarder) Open(fw Forward) (Forward, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.ctx == nil || f.ctx.Err() != nil {
		return fw, errors.New("services: forwarder is not running")
	}
	if old, ok := f.active[fw.ID]; ok {
		old.cancel()
		old.ln.Close()
		delete(f.active, fw.ID)
	}
	if fw.Listen == "" {
		fw.Listen = "127.0.0.1:0"
	}
	ln, err := net.Listen("tcp", fw.Listen)
	if err != nil {
		f.active[fw.ID] = &activeForward{fw: fw, ln: closedListener{}, cancel: func() {}, err: errShort(err)}
		return fw, fmt.Errorf("cannot listen on %s: %w", fw.Listen, err)
	}
	fw.Listen = ln.Addr().String()
	ctx, cancel := context.WithCancel(f.ctx)
	a := &activeForward{fw: fw, ln: ln, cancel: cancel}
	f.active[fw.ID] = a
	go f.accept(ctx, a)
	return fw, nil
}

// Close stops a forward.
func (f *Forwarder) Close(id string) {
	f.mu.Lock()
	if a, ok := f.active[id]; ok {
		a.cancel()
		a.ln.Close()
		delete(f.active, id)
	}
	f.mu.Unlock()
}

func (f *Forwarder) accept(ctx context.Context, a *activeForward) {
	for {
		c, err := a.ln.Accept()
		if err != nil {
			return
		}
		go func() {
			a.conns.Add(1)
			defer a.conns.Add(-1)
			dctx, cancel := context.WithTimeout(ctx, 15*time.Second)
			remote, err := f.m.Dial(dctx, a.fw.Peer, a.fw.Service, 0)
			cancel()
			if err != nil {
				c.Close()
				f.mu.Lock()
				a.err = describe(err)
				f.mu.Unlock()
				if f.onChange != nil {
					f.onChange()
				}
				return
			}
			f.mu.Lock()
			a.err = ""
			f.mu.Unlock()
			Pipe(c, remote)
		}()
	}
}

func describe(err error) string {
	var re *mesh.RPCError
	if errors.As(err, &re) {
		return re.Msg
	}
	return err.Error()
}

// Status returns the runtime state of a forward.
func (f *Forwarder) Status(fw Forward) ForwardStatus {
	st := ForwardStatus{Forward: fw, State: "stopped"}
	if p := f.m.node.Peer(fw.Peer); p != nil {
		st.PeerName = p.Name()
	}
	f.mu.Lock()
	defer f.mu.Unlock()
	if a, ok := f.active[fw.ID]; ok {
		st.Listen = a.fw.Listen
		st.Conns = int(a.conns.Load())
		if _, dead := a.ln.(closedListener); dead {
			st.State, st.Error = "error", a.err
		} else {
			st.State, st.Error = "listening", a.err
		}
	}
	return st
}

type closedListener struct{}

func (closedListener) Accept() (net.Conn, error) { return nil, net.ErrClosed }
func (closedListener) Close() error              { return nil }
func (closedListener) Addr() net.Addr            { return &net.TCPAddr{} }
