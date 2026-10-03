// Package meshtest builds small simulated meshes for tests of the application
// layers (files, mail, services) without touching the real network.
package meshtest

import (
	"context"
	"net"
	"net/netip"
	"os"
	"testing"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/magic"
	"github.com/parfentsevandrey-blip/test/svoi/internal/mesh"
	"github.com/parfentsevandrey-blip/test/svoi/internal/netsim"
)

func init() {
	// quic-go complains that it cannot tune buffers of a non-UDP conn; magic sizes
	// the real socket itself.
	os.Setenv("QUIC_GO_DISABLE_RECEIVE_BUFFER_WARNING", "true")
}

// FastTiming shrinks magic's timers so tests converge in about a second.
var FastTiming = magic.Timing{
	Tick:          40 * time.Millisecond,
	ProbeFast:     150 * time.Millisecond,
	ProbeSlow:     time.Second,
	KeepAlive:     300 * time.Millisecond,
	KeepAliveIdle: time.Second,
	PathTTL:       1500 * time.Millisecond,
	WantFor:       30 * time.Second,
	CallMeMaybe:   250 * time.Millisecond,
	STUNEvery:     time.Second,
	NetCheckEvery: 200 * time.Millisecond,
	StateEvery:    time.Second,
}

// Harness owns a simulated Internet.
type Harness struct {
	T   testing.TB
	Net *netsim.Network

	cfgs map[*mesh.Node]mesh.Config
}

// New creates a harness.
func New(t testing.TB) *Harness {
	return &Harness{T: t, Net: netsim.New(), cfgs: map[*mesh.Node]mesh.Config{}}
}

// Restart closes a node and opens a new one on the same state directory and
// simulated host, like a device being switched off and on again.
func (h *Harness) Restart(n *mesh.Node) *mesh.Node {
	h.T.Helper()
	cfg, ok := h.cfgs[n]
	if !ok {
		h.T.Fatal("meshtest: node was not opened by this harness")
	}
	_ = n.Close()
	n2, err := mesh.Open(cfg)
	if err != nil {
		h.T.Fatal(err)
	}
	h.cfgs[n2] = cfg
	h.T.Cleanup(func() { n2.Close() })
	return n2
}

// Config returns a node configuration suited to tests.
func Config(t testing.TB, host *netsim.Host, name string) mesh.Config {
	return mesh.Config{
		Dir:         t.TempDir(),
		DeviceName:  name,
		Owner:       "tester",
		UDPPort:     41710,
		Listen:      func(port int) (net.PacketConn, error) { return host.ListenPacket(uint16(port)) },
		LocalAddrs:  func() []netip.Addr { return host.Addrs() },
		Timing:      FastTiming,
		LANPort:     -1,
		SyncEvery:   2 * time.Second,
		ConnectTick: 100 * time.Millisecond,
		PassiveWait: time.Second,
		DialTimeout: 8 * time.Second,
	}
}

// Public opens a node on a host with a public address.
func (h *Harness) Public(name, ip string) *mesh.Node {
	return h.Open(h.Net.Internet().NewHost(netip.MustParseAddr(ip)), name)
}

// Behind opens a node on a host behind a NAT.
func (h *Harness) Behind(nat *netsim.NAT, name, ip string) *mesh.Node {
	return h.Open(nat.Inner().NewHost(netip.MustParseAddr(ip)), name)
}

// Open opens a node on an existing simulated host.
func (h *Harness) Open(host *netsim.Host, name string) *mesh.Node {
	h.T.Helper()
	cfg := Config(h.T, host, name)
	n, err := mesh.Open(cfg)
	if err != nil {
		h.T.Fatal(err)
	}
	h.cfgs[n] = cfg
	h.T.Cleanup(func() { n.Close() })
	return n
}

// Join enrols joiner into founder's mesh.
func (h *Harness) Join(founder, joiner *mesh.Node, name, owner string, admin bool) {
	h.T.Helper()
	inv, err := founder.NewInviteFor(admin, 5*time.Minute, owner)
	if err != nil {
		h.T.Fatal(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 25*time.Second)
	defer cancel()
	if err := joiner.JoinMesh(ctx, inv.Code, name); err != nil {
		h.T.Fatalf("join %s: %v", name, err)
	}
}

// Mesh makes the first node the founder and joins the rest, then waits until
// every pair of nodes is connected.
func (h *Harness) Mesh(nodes ...*mesh.Node) {
	h.T.Helper()
	if err := nodes[0].CreateMesh("Test", nodes[0].Self().Name, "tester"); err != nil {
		h.T.Fatal(err)
	}
	for i, n := range nodes[1:] {
		h.Join(nodes[0], n, []string{"beta", "gamma", "delta", "epsilon", "zeta"}[i], "tester", false)
	}
	h.WaitConnected(nodes...)
}

// WaitConnected waits until all nodes see each other online.
func (h *Harness) WaitConnected(nodes ...*mesh.Node) {
	h.T.Helper()
	WaitFor(h.T, 30*time.Second, "full mesh connectivity", func() bool {
		for _, a := range nodes {
			for _, b := range nodes {
				if a == b {
					continue
				}
				p := a.Peer(b.ID())
				if p == nil || !p.Online() {
					return false
				}
			}
		}
		return true
	})
}

// WaitFor polls cond until it is true or the deadline passes.
func WaitFor(t testing.TB, d time.Duration, what string, cond func() bool) {
	t.Helper()
	deadline := time.Now().Add(d)
	for time.Now().Before(deadline) {
		if cond() {
			return
		}
		time.Sleep(25 * time.Millisecond)
	}
	t.Fatalf("timed out after %v waiting for: %s", d, what)
}
