package mesh

import (
	"bytes"
	"context"
	"crypto/sha256"
	"errors"
	"io"
	"log/slog"
	"net"
	"net/netip"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/magic"
	"github.com/parfentsevandrey-blip/test/svoi/internal/netsim"
)

var testTiming = magic.Timing{
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

func ip(s string) netip.Addr { return netip.MustParseAddr(s) }

type tnode struct {
	*Node
	dir  string
	host *netsim.Host
	cfg  Config
}

func newTestNode(t *testing.T, host *netsim.Host, name string, mod func(*Config)) *tnode {
	t.Helper()
	dir := t.TempDir()
	return openTestNode(t, host, name, dir, mod)
}

func openTestNode(t *testing.T, host *netsim.Host, name, dir string, mod func(*Config)) *tnode {
	t.Helper()
	cfg := Config{
		Dir:         dir,
		DeviceName:  name,
		Owner:       "tester",
		UDPPort:     41710,
		Listen:      func(port int) (net.PacketConn, error) { return host.ListenPacket(uint16(port)) },
		LocalAddrs:  func() []netip.Addr { return host.Addrs() },
		Timing:      testTiming,
		LANPort:     -1,
		SyncEvery:   2 * time.Second,
		ConnectTick: 100 * time.Millisecond,
		PassiveWait: time.Second,
		DialTimeout: 8 * time.Second,
	}
	if os.Getenv("SVOI_TEST_DEBUG") != "" {
		cfg.Logger = slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelDebug})).With("node", name)
	}
	if mod != nil {
		mod(&cfg)
	}
	n, err := Open(cfg)
	if err != nil {
		t.Fatal(err)
	}
	tn := &tnode{Node: n, dir: dir, host: host, cfg: cfg}
	t.Cleanup(func() { n.Close() })
	return tn
}

func waitFor(t *testing.T, d time.Duration, what string, cond func() bool) {
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

func online(a *tnode, b *tnode) func() bool {
	return func() bool {
		p := a.Peer(b.ID())
		return p != nil && p.Online()
	}
}

// join makes joiner a member of founder's mesh through a fresh invitation.
func join(t *testing.T, founder, joiner *tnode, name string, admin bool) {
	t.Helper()
	inv, err := founder.NewInvite(admin, 5*time.Minute)
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	if err := joiner.JoinMesh(ctx, inv.Code, name, "tester"); err != nil {
		t.Fatalf("join %s: %v", name, err)
	}
}

func TestCreateJoinAndRPC(t *testing.T) {
	nw := netsim.New()
	a := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.1")), "alpha", nil)
	b := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.2")), "beta", nil)
	if a.Configured() || b.Configured() {
		t.Fatal("fresh nodes must not be configured")
	}
	if err := a.CreateMesh("Home", "alpha", "andrey"); err != nil {
		t.Fatal(err)
	}
	if !a.IsAdmin() || a.Self().MeshName != "Home" {
		t.Fatalf("founder state wrong: %+v", a.Self())
	}
	join(t, a, b, "beta", false)
	if b.IsAdmin() {
		t.Fatal("ordinary invite produced an admin")
	}
	waitFor(t, 10*time.Second, "a sees b online", online(a, b))
	waitFor(t, 10*time.Second, "b sees a online", online(b, a))

	a.Handle("test.echo", func(ctx context.Context, c *Call) (any, error) {
		var in struct{ Text string }
		if err := c.Decode(&in); err != nil {
			return nil, err
		}
		return map[string]string{"echo": strings.ToUpper(in.Text), "from": c.Peer.Name()}, nil
	})
	var out map[string]string
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := b.Peer(a.ID()).Call(ctx, "test.echo", map[string]string{"Text": "ping"}, &out); err != nil {
		t.Fatal(err)
	}
	if out["echo"] != "PING" || out["from"] != "beta" {
		t.Fatalf("unexpected reply %v", out)
	}
	// Errors travel as typed errors.
	a.Handle("test.fail", func(ctx context.Context, c *Call) (any, error) {
		return nil, Errf(CodeDenied, "no way")
	})
	err := b.Peer(a.ID()).Call(ctx, "test.fail", nil, nil)
	if !IsCode(err, CodeDenied) {
		t.Fatalf("expected denied, got %v", err)
	}
	if err := b.Peer(a.ID()).Call(ctx, "test.nothing", nil, nil); !IsCode(err, CodeUnsupported) {
		t.Fatalf("expected unsupported, got %v", err)
	}
	if rtt, err := b.Peer(a.ID()).Ping(ctx); err != nil || rtt <= 0 {
		t.Fatalf("ping: %v %v", rtt, err)
	}
	// Both sides know each other's certificates and overlay addresses.
	info := a.Peer(b.ID()).Info()
	if info.Name != "beta" || !strings.HasPrefix(info.IP4, "100.") || !info.Online {
		t.Fatalf("peer info %+v", info)
	}
	if info.Hello == nil || info.Hello.Version != Version {
		t.Fatalf("hello not exchanged: %+v", info.Hello)
	}
}

func TestInviteIsSingleUseAndExpires(t *testing.T) {
	nw := netsim.New()
	a := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.1")), "alpha", nil)
	b := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.2")), "beta", nil)
	c := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.3")), "gamma", nil)
	if err := a.CreateMesh("Home", "alpha", "x"); err != nil {
		t.Fatal(err)
	}
	inv, err := a.NewInvite(false, time.Minute)
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	if err := b.JoinMesh(ctx, inv.Code, "beta", "x"); err != nil {
		t.Fatal(err)
	}
	if len(a.Invites()) != 0 {
		t.Fatalf("invite still listed after use: %+v", a.Invites())
	}
	// Re-using the same code must fail.
	ctx2, cancel2 := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel2()
	err = c.JoinMesh(ctx2, inv.Code, "gamma", "x")
	if err == nil {
		t.Fatal("a used invitation was accepted twice")
	}
	if c.Configured() {
		t.Fatal("failed join left the device configured")
	}
	// A tampered code (bad checksum) is rejected before any network traffic.
	bad := inv.Code[:len(inv.Code)-3] + "AAA"
	if err := c.JoinMesh(ctx2, bad, "gamma", "x"); err == nil {
		t.Fatal("tampered invitation accepted")
	}
	// Non-admins cannot invite; only the founder can.
	waitFor(t, 10*time.Second, "b online", online(b, a))
	if _, err := b.NewInvite(false, time.Minute); !errors.Is(err, ErrNotAdmin) {
		t.Fatalf("expected ErrNotAdmin, got %v", err)
	}
	// Cancel works.
	inv2, _ := a.NewInvite(false, time.Minute)
	if !a.CancelInvite(inv2.ID) || len(a.Invites()) != 0 {
		t.Fatal("CancelInvite failed")
	}
	if err := c.JoinMesh(ctx2, inv2.Code, "gamma", "x"); err == nil {
		t.Fatal("cancelled invitation accepted")
	}
}

func TestJoinPinsTheInviter(t *testing.T) {
	// An impostor listening at the address from the invite must not be able to
	// complete the join: its certificate does not chain to the mesh root.
	nw := netsim.New()
	a := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.1")), "alpha", nil)
	evil := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.66")), "evil", nil)
	victim := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.2")), "victim", nil)
	if err := a.CreateMesh("Home", "alpha", "x"); err != nil {
		t.Fatal(err)
	}
	if err := evil.CreateMesh("Evil", "evil", "x"); err != nil {
		t.Fatal(err)
	}
	inv, _ := a.NewInvite(false, time.Minute)
	// Forge an invite that keeps alpha's root/secret but points to the impostor.
	parsed, err := identity.ParseInvite(inv.Code)
	if err != nil {
		t.Fatal(err)
	}
	parsed.Endpoints = []netip.AddrPort{netip.AddrPortFrom(ip("198.51.100.66"), 41710)}
	evil.NewInvite(false, time.Minute) // makes the impostor accept anonymous traffic
	ctx, cancel := context.WithTimeout(context.Background(), 12*time.Second)
	defer cancel()
	if err := victim.JoinMesh(ctx, parsed.Encode(), "victim", "x"); err == nil {
		t.Fatal("joined through an impostor")
	}
	if victim.Configured() {
		t.Fatal("victim is configured after a failed join")
	}
}

func TestMembershipGossipAndThreeNodeMesh(t *testing.T) {
	nw := netsim.New()
	a := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.1")), "alpha", nil)
	b := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.2")), "beta", nil)
	c := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.3")), "gamma", nil)
	if err := a.CreateMesh("Home", "alpha", "x"); err != nil {
		t.Fatal(err)
	}
	join(t, a, b, "beta", false)
	waitFor(t, 10*time.Second, "a-b link", online(a, b))
	join(t, a, c, "gamma", false)
	// b has never talked to c: it must learn about c via a and then connect.
	waitFor(t, 15*time.Second, "b learns c", func() bool { return b.Peer(c.ID()) != nil })
	waitFor(t, 20*time.Second, "b-c link", online(b, c))
	waitFor(t, 20*time.Second, "c-b link", online(c, b))
	if got := len(b.Peers()); got != 2 {
		t.Fatalf("b sees %d peers, want 2", got)
	}
	if p := b.FindPeer("gamma.svoi"); p == nil || p.ID != c.ID() {
		t.Fatal("FindPeer by DNS-style name failed")
	}
	if p := b.FindPeer(c.Self().IP4); p == nil || p.ID != c.ID() {
		t.Fatal("FindPeer by overlay IP failed")
	}
	if p := b.PeerByIP(netip.MustParseAddr(c.Self().IP4)); p == nil {
		t.Fatal("PeerByIP failed")
	}
}

func TestAdminInviteTransfersAuthority(t *testing.T) {
	nw := netsim.New()
	a := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.1")), "alpha", nil)
	b := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.2")), "beta", nil)
	c := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.3")), "gamma", nil)
	a.CreateMesh("Home", "alpha", "x")
	join(t, a, b, "beta", true) // b becomes an admin
	if !b.IsAdmin() || !b.Self().Admin {
		t.Fatal("admin invite did not make the joiner an admin")
	}
	waitFor(t, 10*time.Second, "a-b link", online(a, b))
	// b can now enrol c on its own, with a offline-capable authority.
	join(t, b, c, "gamma", false)
	waitFor(t, 15*time.Second, "a learns c from b", func() bool { return a.Peer(c.ID()) != nil })
	if p := a.Peer(b.ID()); p == nil || !p.Member().Admin {
		t.Fatal("a does not know b is an admin")
	}
}

func TestRevocationDisconnectsAndBans(t *testing.T) {
	nw := netsim.New()
	a := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.1")), "alpha", nil)
	b := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.2")), "beta", nil)
	c := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.3")), "gamma", nil)
	a.CreateMesh("Home", "alpha", "x")
	join(t, a, b, "beta", false)
	join(t, a, c, "gamma", false)
	waitFor(t, 20*time.Second, "full mesh", func() bool {
		return online(a, b)() && online(a, c)() && online(b, c)()
	})
	if err := b.Revoke(c.ID()); !errors.Is(err, ErrNotAdmin) {
		t.Fatalf("non-admin revoke: %v", err)
	}
	oldC := c.ID()
	if err := a.Revoke(c.ID()); err != nil {
		t.Fatal(err)
	}
	if a.Peer(c.ID()) != nil {
		t.Fatal("revoked peer still known to the admin")
	}
	waitFor(t, 10*time.Second, "b forgets c via gossip", func() bool { return b.Peer(c.ID()) == nil })
	waitFor(t, 10*time.Second, "c loses its links", func() bool {
		for _, p := range c.Peers() {
			if p.Online() {
				return false
			}
		}
		return true
	})
	// And it cannot come back: b rejects its certificate.
	time.Sleep(1500 * time.Millisecond)
	if online(b, c)() {
		t.Fatal("revoked device reconnected")
	}

	// c was told while its link still existed: it knows it was removed, has
	// forgotten the mesh and took a fresh identity (the old one stays revoked).
	waitFor(t, 10*time.Second, "c learns it was removed", func() bool { return !c.Configured() && c.Removed() != nil })
	if ri := c.Removed(); ri.MeshName != "Home" || ri.At == 0 {
		t.Fatalf("removal note: %+v", ri)
	}
	if c.ID() == oldC {
		t.Fatal("the removed device kept its burned identity")
	}
	// A new invitation lets the same device join again, under its new identity.
	join(t, a, c, "gamma", false)
	waitFor(t, 20*time.Second, "c is back in the mesh", online(a, c))
	if c.Removed() != nil {
		t.Fatal("the removal note should vanish once the device is in a mesh again")
	}
	// A forged "you were removed" cannot come from a member: it needs the root's signature.
	forged := identity.Revocation{ID: c.ID(), At: time.Now().Unix()}
	p := b.Peer(c.ID())
	if p != nil && p.Online() {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		if err := p.Call(ctx, "mesh.revoked", forged, nil); err == nil && !c.Configured() {
			t.Fatal("an unsigned revocation made the device leave its mesh")
		}
	}
	time.Sleep(600 * time.Millisecond)
	if !c.Configured() {
		t.Fatal("an unsigned revocation made the device leave its mesh")
	}
}

func TestRestartKeepsMembershipAndReconnects(t *testing.T) {
	nw := netsim.New()
	ha := nw.Internet().NewHost(ip("198.51.100.1"))
	hb := nw.Internet().NewHost(ip("198.51.100.2"))
	a := newTestNode(t, ha, "alpha", nil)
	b := newTestNode(t, hb, "beta", nil)
	a.CreateMesh("Home", "alpha", "x")
	join(t, a, b, "beta", false)
	waitFor(t, 10*time.Second, "link", online(a, b))
	aid, bid := a.ID(), b.ID()

	// Restart b from its state directory.
	b.Close()
	b2 := openTestNode(t, hb, "ignored", b.dir, nil)
	if b2.ID() != bid || !b2.Configured() {
		t.Fatal("identity or membership lost across restart")
	}
	if p := b2.Peer(aid); p == nil || p.Member().Name != "alpha" {
		t.Fatal("member list lost across restart")
	}
	waitFor(t, 15*time.Second, "reconnect after restart", func() bool {
		p := b2.Peer(aid)
		return p != nil && p.Online()
	})
	waitFor(t, 15*time.Second, "a sees b again", online(a, b2))
}

func TestStreamingBulkTransfer(t *testing.T) {
	nw := netsim.New()
	a := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.1")), "alpha", nil)
	b := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.2")), "beta", nil)
	a.CreateMesh("Home", "alpha", "x")
	join(t, a, b, "beta", false)
	waitFor(t, 10*time.Second, "link", online(b, a))

	// A "download" streams a deterministic 3 MB body; an "upload" checks a hash.
	payload := bytes.Repeat([]byte("0123456789abcdef"), 3<<16)
	a.HandleStream("test.download", func(ctx context.Context, c *Call, s *ServerStream) error {
		if err := s.Reply(map[string]int{"size": len(payload)}); err != nil {
			return err
		}
		_, err := s.Write(payload)
		return err
	})
	a.HandleStream("test.upload", func(ctx context.Context, c *Call, s *ServerStream) error {
		h := sha256.New()
		n, err := io.Copy(h, s)
		if err != nil {
			return err
		}
		return s.Reply(map[string]any{"n": n, "sum": h.Sum(nil)})
	})

	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	cs, err := b.Peer(a.ID()).OpenStream(ctx, "test.download", nil)
	if err != nil {
		t.Fatal(err)
	}
	cs.CloseWrite()
	var meta map[string]int
	if err := cs.ReadResponse(&meta); err != nil {
		t.Fatal(err)
	}
	got, err := io.ReadAll(cs)
	if err != nil {
		t.Fatal(err)
	}
	if meta["size"] != len(payload) || !bytes.Equal(got, payload) {
		t.Fatalf("download corrupted: size=%d got=%d", meta["size"], len(got))
	}

	cs, err = b.Peer(a.ID()).OpenStream(ctx, "test.upload", nil)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := cs.Write(payload); err != nil {
		t.Fatal(err)
	}
	cs.CloseWrite()
	var up struct {
		N   int64
		Sum []byte
	}
	if err := cs.ReadResponse(&up); err != nil {
		t.Fatal(err)
	}
	want := sha256.Sum256(payload)
	if up.N != int64(len(payload)) || !bytes.Equal(up.Sum, want[:]) {
		t.Fatalf("upload mismatch: %+v", up)
	}
}

func TestConcurrentCallsAndDatagrams(t *testing.T) {
	nw := netsim.New()
	a := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.1")), "alpha", nil)
	b := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.2")), "beta", nil)
	a.CreateMesh("Home", "alpha", "x")
	join(t, a, b, "beta", false)
	waitFor(t, 10*time.Second, "link", online(b, a))
	a.Handle("test.add", func(ctx context.Context, c *Call) (any, error) {
		var in [2]int
		if err := c.Decode(&in); err != nil {
			return nil, err
		}
		return in[0] + in[1], nil
	})
	var wg sync.WaitGroup
	errs := make(chan error, 100)
	for i := 0; i < 100; i++ {
		wg.Add(1)
		go func(i int) {
			defer wg.Done()
			var sum int
			ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
			defer cancel()
			if err := b.Peer(a.ID()).Call(ctx, "test.add", [2]int{i, 1000}, &sum); err != nil {
				errs <- err
				return
			}
			if sum != i+1000 {
				errs <- errors.New("wrong sum")
			}
		}(i)
	}
	wg.Wait()
	close(errs)
	for err := range errs {
		t.Fatal(err)
	}

	got := make(chan []byte, 1)
	a.HandleDatagram(0x42, func(p *Peer, data []byte) { got <- append([]byte(nil), data...) })
	if err := b.Peer(a.ID()).SendDatagram(append([]byte{0x42}, "datagram!"...)); err != nil {
		t.Fatal(err)
	}
	select {
	case d := <-got:
		if string(d) != "datagram!" {
			t.Fatalf("datagram %q", d)
		}
	case <-time.After(3 * time.Second):
		t.Fatal("datagram lost")
	}
}

// The scenario the whole project exists for: two devices behind NATs that have
// never talked to each other find a direct path with the help of a third member.
func TestMeshAcrossNATsBecomesDirect(t *testing.T) {
	nw := netsim.New()
	inet := nw.Internet()
	natB := inet.NewNAT(ip("203.0.113.2"), netsim.PortRestricted)
	natC := inet.NewNAT(ip("203.0.113.3"), netsim.PortRestricted)
	a := newTestNode(t, inet.NewHost(ip("203.0.113.100")), "anchor", nil)
	b := newTestNode(t, natB.Inner().NewHost(ip("192.168.2.10")), "phone", nil)
	c := newTestNode(t, natC.Inner().NewHost(ip("192.168.3.10")), "laptop", nil)
	a.CreateMesh("Home", "anchor", "x")
	join(t, a, b, "phone", false)
	join(t, a, c, "laptop", false)

	waitFor(t, 20*time.Second, "everyone online", func() bool {
		return online(a, b)() && online(a, c)() && online(b, c)() && online(c, b)()
	})
	waitFor(t, 15*time.Second, "direct b-c path", func() bool {
		pi := b.Peer(c.ID()).Info()
		return pi.Path == "direct"
	})
	// Application traffic works over the punched path.
	c.Handle("test.hi", func(ctx context.Context, cl *Call) (any, error) { return "hello " + cl.Peer.Name(), nil })
	var out string
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := b.Peer(c.ID()).Call(ctx, "test.hi", nil, &out); err != nil || out != "hello phone" {
		t.Fatalf("call over direct path: %q %v", out, err)
	}
}

func TestMeshAcrossSymmetricNATUsesRelay(t *testing.T) {
	nw := netsim.New()
	inet := nw.Internet()
	natB := inet.NewNAT(ip("203.0.113.2"), netsim.Symmetric)
	natC := inet.NewNAT(ip("203.0.113.3"), netsim.PortRestricted)
	a := newTestNode(t, inet.NewHost(ip("203.0.113.100")), "anchor", nil)
	b := newTestNode(t, natB.Inner().NewHost(ip("192.168.2.10")), "phone", nil)
	c := newTestNode(t, natC.Inner().NewHost(ip("192.168.3.10")), "laptop", nil)
	a.CreateMesh("Home", "anchor", "x")
	join(t, a, b, "phone", false)
	join(t, a, c, "laptop", false)

	waitFor(t, 30*time.Second, "b-c link through relay", func() bool { return online(b, c)() && online(c, b)() })
	pi := b.Peer(c.ID()).Info()
	if pi.Path != "relay" || pi.RelayVia != "anchor" {
		t.Fatalf("expected relay via anchor, got path=%s via=%q", pi.Path, pi.RelayVia)
	}
	c.Handle("test.hi", func(ctx context.Context, cl *Call) (any, error) { return "relayed hello", nil })
	var out string
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := b.Peer(c.ID()).Call(ctx, "test.hi", nil, &out); err != nil || out != "relayed hello" {
		t.Fatalf("call over relay: %q %v", out, err)
	}
	if self := a.Self(); self.Relayed.Packets == 0 {
		t.Fatalf("anchor did not forward anything: %+v", self.Relayed)
	}
}

func TestLeaveForgetsEverything(t *testing.T) {
	nw := netsim.New()
	a := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.1")), "alpha", nil)
	b := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.2")), "beta", nil)
	a.CreateMesh("Home", "alpha", "x")
	join(t, a, b, "beta", false)
	waitFor(t, 10*time.Second, "link", online(b, a))
	if err := b.Leave(); err != nil {
		t.Fatal(err)
	}
	if b.Configured() || len(b.Peers()) != 0 {
		t.Fatal("device still configured after Leave")
	}
	if _, err := filepath.Glob(filepath.Join(b.dir, "mesh.json")); err != nil {
		t.Fatal(err)
	}
	waitFor(t, 10*time.Second, "a sees b offline", func() bool { return !online(a, b)() })
	// The same device can join again with a fresh invitation.
	join(t, a, b, "beta2", false)
	waitFor(t, 10*time.Second, "relinked", online(b, a))
}

func TestWrongMeshCannotConnect(t *testing.T) {
	nw := netsim.New()
	a := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.1")), "alpha", nil)
	x := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.9")), "xeno", nil)
	a.CreateMesh("Home", "alpha", "x")
	x.CreateMesh("Other", "xeno", "x")
	// x learns a's ID and address by accident (e.g. a stale configuration) but
	// holds a certificate from a different root: no link may form.
	am := a.Self()
	aid, _ := identity.ParseID(am.ID)
	xm := x.Magic()
	if err := xm.AddPeer(aid); err != nil {
		t.Fatal(err)
	}
	xm.AddCandidates(aid, []netip.AddrPort{netip.AddrPortFrom(ip("198.51.100.1"), 41710)}, magic.SrcInvite)
	xm.Poke(aid)
	time.Sleep(2 * time.Second)
	for _, p := range a.Peers() {
		if p.Online() {
			t.Fatal("a device from another mesh got a link")
		}
	}
}
