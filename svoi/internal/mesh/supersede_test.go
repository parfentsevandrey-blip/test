package mesh

import (
	"context"
	"errors"
	"fmt"
	"sync/atomic"
	"testing"
	"time"

	"github.com/quic-go/quic-go"

	"github.com/parfentsevandrey-blip/test/svoi/internal/netsim"
)

func TestReplacedRecognisesALinkClosedForAnotherOne(t *testing.T) {
	for _, c := range []struct {
		name string
		err  error
		want bool
	}{
		{"closed here as superseded", &quic.ApplicationError{ErrorCode: closeDuplicate, ErrorMessage: "superseded"}, true},
		{"closed by the peer as a duplicate", &quic.ApplicationError{Remote: true, ErrorCode: closeDuplicate, ErrorMessage: "duplicate connection"}, true},
		{"wrapped", fmt.Errorf("call: %w", &quic.ApplicationError{ErrorCode: closeDuplicate}), true},
		{"closed because the member was revoked", &quic.ApplicationError{Remote: true, ErrorCode: closeRevoked}, false},
		{"closed on shutdown", &quic.ApplicationError{ErrorCode: closeShutdown}, false},
		{"an idle link", &quic.IdleTimeoutError{}, false},
		{"something else", errors.New("boom"), false},
		{"nothing", nil, false},
	} {
		if got := replaced(c.err); got != c.want {
			t.Errorf("%s: replaced = %v, want %v", c.name, got, c.want)
		}
	}
}

// Two links to one peer exist for a moment when both devices dial each other at once (right after a
// join, say) or when a device comes back from a restart; one of them is closed as "superseded". A call
// that happens to be on it must not fail for that: it goes again on the link that stays.
func TestACallOnASupersededLinkGoesAgainOnTheNewOne(t *testing.T) {
	nw := netsim.New()
	ha, hb := nw.Internet().NewHost(ip("198.51.100.1")), nw.Internet().NewHost(ip("198.51.100.2"))
	a := newTestNode(t, ha, "alpha", nil)
	b := newTestNode(t, hb, "beta", nil)
	if err := a.CreateMesh("Home", "alpha", "x"); err != nil {
		t.Fatal(err)
	}
	join(t, a, b, "beta", false)
	waitFor(t, 10*time.Second, "link", func() bool { return online(a, b)() && online(b, a)() })

	// The new link must come from the side that dialled the old one (that is "dialling again", and
	// the newcomer then wins on both ends); which side that is depends on the identities.
	caller, callee := a, b
	pa := a.Peer(b.ID())
	pa.mu.Lock()
	if pa.connDialer != a.ID() {
		caller, callee = b, a
	}
	pa.mu.Unlock()
	p := caller.Peer(callee.ID())
	old := p.currentConn()
	if old == nil {
		t.Fatal("no link to start with")
	}

	var runs atomic.Int32
	started := make(chan struct{})
	release := make(chan struct{})
	t.Cleanup(func() { close(release) })
	callee.Handle("test.slow", func(ctx context.Context, c *Call) (any, error) {
		if runs.Add(1) == 1 {
			close(started)
			select { // the first one is stuck: its link is about to go
			case <-release:
			case <-ctx.Done():
			}
			return nil, ctx.Err()
		}
		return map[string]int{"run": int(runs.Load())}, nil
	})

	var out struct{ Run int }
	done := make(chan error, 1)
	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
		defer cancel()
		done <- p.Call(ctx, "test.slow", nil, &out)
	}()
	select {
	case <-started:
	case <-time.After(10 * time.Second):
		t.Fatal("the call never reached its handler")
	}

	// the same side dials again while the call is on the old link
	caller.mu.RLock()
	mg, tr := caller.magic, caller.tr
	caller.mu.RUnlock()
	vaddr, ok := mg.VirtualAddr(callee.ID())
	if !ok {
		t.Fatal("no address for the peer")
	}
	dctx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
	defer cancel()
	conn, err := tr.Dial(dctx, vaddr, caller.clientTLS(callee.ID()), caller.quicConf())
	if err != nil {
		t.Fatal(err)
	}
	caller.attach(p, conn, true)
	if p.currentConn() == old {
		t.Fatal("the new link was turned away: the situation under test did not happen")
	}

	select {
	case err := <-done:
		if err != nil {
			t.Fatalf("a call on a link that was replaced failed: %v", err)
		}
	case <-time.After(10 * time.Second):
		t.Fatal("the call did not end")
	}
	if out.Run != 2 {
		t.Fatalf("the call should have run again on the new link (run %d)", out.Run)
	}
}
