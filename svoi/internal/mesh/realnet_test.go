package mesh

import (
	"context"
	"log/slog"
	"net/netip"
	"os"
	"testing"
	"time"
)

// The other tests of this package run on a simulated Internet. This one uses the operating system's own
// UDP sockets, as two copies of the program on one machine do: one device invites, the other joins with the
// code, and they find each other. Both prefer the same default port, so the second one has to take another.
func TestJoinOverRealSockets(t *testing.T) {
	open := func(name string) *Node {
		cfg := Config{
			Dir:         t.TempDir(),
			DeviceName:  name,
			Owner:       "tester",
			LocalAddrs:  func() []netip.Addr { return []netip.Addr{ip("127.0.0.1")} },
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
		n, err := Open(cfg)
		if err != nil {
			t.Fatal(err)
		}
		t.Cleanup(func() { n.Close() })
		return n
	}
	a, b := open("alpha"), open("beta")
	if err := a.CreateMesh("Home", "alpha", "andrey"); err != nil {
		t.Fatal(err)
	}
	inv, err := a.NewInvite(false, 5*time.Minute)
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	if err := b.JoinMesh(ctx, inv.Code, "beta"); err != nil {
		t.Fatalf("join over real sockets: %v", err)
	}
	waitFor(t, 15*time.Second, "alpha sees beta online", func() bool { p := a.Peer(b.ID()); return p != nil && p.Online() })
	waitFor(t, 15*time.Second, "beta sees alpha online", func() bool { p := b.Peer(a.ID()); return p != nil && p.Online() })
}
