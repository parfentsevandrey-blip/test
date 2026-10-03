package mesh

import (
	"bytes"
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/netsim"
)

func TestKeepNewConnRule(t *testing.T) {
	lo, hi := identity.ID{1}, identity.ID{2}
	young, old := 2*time.Second, 30*time.Second
	for _, c := range []struct {
		name                           string
		self, peer, oldDialer, newDial identity.ID
		age                            time.Duration
		keep                           bool
	}{
		{"the same side dialled again: the old link is gone", lo, hi, hi, hi, young, true},
		{"crossing dials, the newcomer was started by the lower ID", hi, lo, hi, lo, young, true},
		{"crossing dials, the newcomer was started by the higher ID", lo, hi, lo, hi, young, false},
		{"crossing dials seen from the other end", hi, lo, lo, hi, young, false},
		{"a restarted peer dials while an old link is still held (higher ID)", lo, hi, lo, hi, old, true},
		{"a restarted peer dials while an old link is still held (lower ID)", hi, lo, hi, lo, old, true},
		{"exactly at the edge it is still a crossing pair", lo, hi, lo, hi, crossingDials, false},
		{"just past the edge it is not", lo, hi, lo, hi, crossingDials + time.Millisecond, true},
	} {
		if got := keepNewConn(c.self, c.peer, c.oldDialer, c.newDial, c.age); got != c.keep {
			t.Errorf("%s: keepNewConn = %v, want %v", c.name, got, c.keep)
		}
	}
}

// A device that crashes (or loses power) says no goodbye, and the others keep its old link for as
// long as QUIC's idle timeout - 40 seconds. When it starts again and dials, the link it brings is
// the live one. This used to be turned away as a "duplicate" whenever the old link had been
// dialled by the surviving side and the restarted device had the higher ID.
func TestARestartedDeviceReplacesItsStaleLink(t *testing.T) {
	nw := netsim.New()
	ipA, ipB := ip("198.51.100.1"), ip("198.51.100.2")
	ha, hb := nw.Internet().NewHost(ipA), nw.Internet().NewHost(ipB)

	// beta (which joins, so dials alpha, and survives) must have the lower ID, alpha (which crashes)
	// the higher one: that is the case that was turned away. Unconfigured nodes cost nothing.
	var a, b *tnode
	for i := 0; ; i++ {
		a = newTestNode(t, ha, "alpha", nil)
		b = newTestNode(t, hb, "beta", nil)
		ai, bi := a.ID(), b.ID()
		if bytes.Compare(bi[:], ai[:]) < 0 {
			break
		}
		if i > 40 {
			t.Fatal("no pair of identities in the order this test needs")
		}
	}
	if err := a.CreateMesh("Home", "alpha", "x"); err != nil {
		t.Fatal(err)
	}
	join(t, a, b, "beta", false)
	waitFor(t, 10*time.Second, "link", func() bool { return online(a, b)() && online(b, a)() })

	// alpha crashes: its goodbye never reaches beta
	nw.Block(ipA, ipB, true)
	if err := a.Close(); err != nil {
		t.Fatal(err)
	}
	nw.Block(ipA, ipB, false)
	if p := b.Peer(a.ID()); p == nil || !p.Online() {
		t.Fatal("beta should still believe in the old link (that is the situation under test)")
	}

	// alpha starts again from its data directory and dials
	started := time.Now()
	a2 := openTestNode(t, ha, "alpha", a.dir, nil)
	waitFor(t, 15*time.Second, "beta to take the link of the restarted alpha", func() bool {
		p := b.Peer(a.ID())
		if p == nil {
			return false
		}
		p.mu.Lock()
		defer p.mu.Unlock()
		return p.conn != nil && p.connectedAt.After(started)
	})
	waitFor(t, 10*time.Second, "the restarted alpha to see beta", online(a2, b))
}

// The reset key is the same for every start of a device (so a restarted node can reset the links of
// its previous life), different between devices (so nobody can reset somebody else's), and the
// transport really carries it.
func TestStatelessResetKeyIsStableAndPrivate(t *testing.T) {
	nw := netsim.New()
	a := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.1")), "alpha", nil)
	b := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.2")), "beta", nil)
	ka, kb := a.statelessResetKey(), b.statelessResetKey()
	if *ka == [32]byte{} || *ka == *kb {
		t.Fatal("the key must be non-zero and differ between devices")
	}
	a2 := openTestNode(t, nw.Internet().NewHost(ip("198.51.100.3")), "alpha", a.dir, nil)
	if a2.ID() != a.ID() || *a2.statelessResetKey() != *ka {
		t.Fatal("a restarted device must derive the same key")
	}
	if err := a.CreateMesh("Home", "alpha", "x"); err != nil {
		t.Fatal(err)
	}
	a.mu.RLock()
	tr := a.tr
	a.mu.RUnlock()
	if tr == nil || tr.StatelessResetKey == nil || *tr.StatelessResetKey != *ka {
		t.Fatal("the QUIC transport does not carry the device's reset key")
	}
}

// A member that has just joined is on disk before the join is answered: a crash right after must not
// make the inviter forget it (it would not dial it after the restart, and the member's old link would
// sit unnoticed until it timed out).
func TestANewMemberIsOnDiskAtOnce(t *testing.T) {
	nw := netsim.New()
	a := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.1")), "alpha", nil)
	b := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.2")), "beta", nil)
	if err := a.CreateMesh("Home", "alpha", "x"); err != nil {
		t.Fatal(err)
	}
	join(t, a, b, "beta", false)
	raw, err := os.ReadFile(filepath.Join(a.dir, "mesh.json"))
	if err != nil {
		t.Fatal(err)
	}
	var sf stateFile
	if err := json.Unmarshal(raw, &sf); err != nil {
		t.Fatal(err)
	}
	if len(sf.Members) != 1 {
		t.Fatalf("mesh.json lists %d members right after the join, want 1", len(sf.Members))
	}
}
