package magic

import (
	"net/netip"
	"testing"

	"github.com/parfentsevandrey-blip/test/svoi/internal/netsim"
	"github.com/parfentsevandrey-blip/test/svoi/internal/portmap"
)

// An address the router forwards to us is the first one we offer, and it is
// withdrawn again when the mapping goes away.
func TestMappedEndpointIsOfferedFirstAndWithdrawn(t *testing.T) {
	nw := netsim.New()
	n := newNode(t, nw.Internet().NewHost(ip("198.51.100.1")), nodeOpts{})
	if _, on := n.conn.PortMapStatus(); on {
		t.Fatal("the port mapping must be off unless asked for")
	}
	var seen []Endpoint
	n.conn.cfg.OnEndpoints = func(eps []Endpoint) { seen = eps }

	mapped := netip.MustParseAddrPort("203.0.113.50:41000")
	n.conn.setMapped(&portmap.Mapping{External: mapped, Protocol: portmap.UPnP})
	eps := n.conn.Endpoints()
	if len(eps) == 0 || eps[0].Addr != mapped || eps[0].Kind != EPMapped || eps[0].Kind.String() != "mapped" {
		t.Fatalf("endpoints: %+v", eps)
	}
	if got := n.conn.NAT().Mapped; got != mapped {
		t.Fatalf("NAT report: %v", got)
	}
	if len(seen) == 0 || seen[0].Kind != EPMapped {
		t.Fatalf("the owner was not told about the new endpoint: %+v", seen)
	}

	n.conn.setMapped(nil)
	for _, e := range n.conn.Endpoints() {
		if e.Kind == EPMapped {
			t.Fatalf("the mapping was withdrawn but is still offered: %+v", e)
		}
	}
	if n.conn.NAT().Mapped.IsValid() {
		t.Fatal("the NAT report still shows a mapping")
	}
}
