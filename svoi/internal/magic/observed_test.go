package magic

import (
	"fmt"
	"net/netip"
	"testing"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
)

func TestObservedAddressesStayBounded(t *testing.T) {
	var s selfState
	s.init()
	now := time.Now()
	var a, b, liar identity.ID
	a[0], b[0], liar[0] = 1, 2, 3
	real := netip.MustParseAddrPort("203.0.113.5:41710")
	s.recordObservedLocked(real, a, now) // two members agree on the real one
	s.recordObservedLocked(real, b, now)
	for i := 0; i < 5000; i++ { // one member keeps inventing addresses
		now = now.Add(time.Millisecond)
		s.recordObservedLocked(netip.MustParseAddrPort(fmt.Sprintf("198.51.%d.%d:4000", i/250%250, i%250+1)), liar, now)
	}
	if len(s.observed) > maxObserved {
		t.Fatalf("%d observed addresses kept, cap is %d", len(s.observed), maxObserved)
	}
	if s.observed[real] == nil {
		t.Fatal("the address two members vouch for was pushed out by one member's inventions")
	}
}

func TestStaleObservedAddressesAreForgotten(t *testing.T) {
	var s selfState
	s.init()
	now := time.Now()
	var by identity.ID
	by[0] = 9
	for i := 0; i < maxObserved; i++ {
		s.recordObservedLocked(netip.MustParseAddrPort(fmt.Sprintf("203.0.113.%d:1000", i+1)), by, now)
	}
	later := now.Add(observedTTL + time.Second)
	s.recordObservedLocked(netip.MustParseAddrPort("203.0.113.200:1000"), by, later)
	if len(s.observed) != 1 {
		t.Fatalf("%d entries after everything went stale, want just the new one", len(s.observed))
	}
}
