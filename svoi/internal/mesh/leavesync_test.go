package mesh

import (
	"testing"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/netsim"
)

// A device that has just left the mesh can still be asked for a sync by a peer whose request was
// already on its way, or be told to reissue its own certificate. Both used to dereference the
// certificate that Leave had just forgotten while the node's lock was held; the RPC layer swallows
// a handler's panic, so the lock stayed taken for good and the next join (or any other call) hung
// forever. Found by CI: TestLeaveForgetsEverything once hung for 15 minutes.
func TestSyncAfterLeaveNeitherPanicsNorKeepsTheLock(t *testing.T) {
	nw := netsim.New()
	a := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.1")), "alpha", nil)
	if err := a.CreateMesh("Home", "alpha", "x"); err != nil {
		t.Fatal(err)
	}
	if err := a.Leave(); err != nil {
		t.Fatal(err)
	}

	// Somebody else may hold the lock for a moment; a lock that stays taken is a leak.
	lockFree := func() bool {
		for i := 0; i < 200; i++ {
			if a.mu.TryLock() {
				a.mu.Unlock()
				return true
			}
			time.Sleep(10 * time.Millisecond)
		}
		return false
	}
	try := func(what string, f func()) {
		t.Helper()
		func() {
			defer func() {
				if r := recover(); r != nil {
					t.Errorf("%s panicked after Leave: %v", what, r)
				}
			}()
			f()
		}()
		if !lockFree() {
			// (a leaked read lock would also make the cleanup wait for ever: let go of it so that the test can fail)
			if a.mu.TryRLock() {
				a.mu.RUnlock()
				a.mu.RUnlock()
			}
			t.Fatalf("%s left the node's lock taken", what)
		}
	}
	try("answering a sync", func() { _ = a.buildSync(nil) })
	try("reissuing its own certificate", func() { _ = a.Reissue(a.ID(), "alpha", false) })
}

// A handler that began before Leave must not bring a member back into a mesh the device left
// (CI and a local loop caught `TestLeaveForgetsEverything` finding a peer after Leave in 2% of runs).
func TestLateHandlerCannotRestoreMembersAfterLeave(t *testing.T) {
	nw := netsim.New()
	a := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.1")), "alpha", nil)
	b := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.2")), "beta", nil)
	if err := a.CreateMesh("Home", "alpha", "x"); err != nil {
		t.Fatal(err)
	}
	join(t, a, b, "beta", false)
	waitFor(t, 10*time.Second, "link", online(b, a))

	// What a sync that was already on its way would hand to beta: alpha's certificate, verified
	// against the mesh root while beta was still a member.
	a.mu.RLock()
	cert := a.self.CertDER
	a.mu.RUnlock()
	b.mu.RLock()
	root := b.root
	b.mu.RUnlock()
	late, err := root.Verify(cert)
	if err != nil {
		t.Fatal(err)
	}

	if err := b.Leave(); err != nil {
		t.Fatal(err)
	}
	if p := b.learnMember(late); p != nil {
		t.Fatalf("a member was learned after Leave: %s", p.ID.Short())
	}
	if len(b.Peers()) != 0 || b.Configured() {
		t.Fatal("the device is a member again after Leave")
	}
}
