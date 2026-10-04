package mesh

import (
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"errors"
	"net/netip"
	"strings"
	"testing"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/magic"
	"github.com/parfentsevandrey-blip/test/svoi/internal/netsim"
)

func endpoint(kind magic.EndpointKind, s string) magic.Endpoint {
	return magic.Endpoint{Addr: netip.MustParseAddrPort(s), Kind: kind}
}

func hasEndpoint(list []netip.AddrPort, s string) bool {
	want := netip.MustParseAddrPort(s)
	for _, e := range list {
		if e == want {
			return true
		}
	}
	return false
}

// A host with a lot of IPv6 addresses (a Mac has several per interface) must not push its home-network address out of the
// invitation: a newcomer on the same Wi-Fi can use nothing else.
func TestInviteKeepsTheHomeAddressOnAHostWithManyIPv6Addresses(t *testing.T) {
	eps := []magic.Endpoint{endpoint(magic.EPSTUN, "203.0.113.5:41710")}
	for _, a := range []string{"2001:db8::1", "2001:db8::2", "2001:db8::3", "2001:db8::4", "2001:db8::5", "2001:db8::6", "2001:db8::7", "2001:db8::8", "2001:db8::9"} {
		eps = append(eps, endpoint(magic.EPLocal, "["+a+"]:41710"))
	}
	eps = append(eps, endpoint(magic.EPLocal, "192.168.1.23:41710"))

	got := inviteEndpoints(eps)
	if !hasEndpoint(got, "192.168.1.23:41710") {
		t.Fatalf("the address on the home network is not in the invitation: %v", got)
	}
	if !hasEndpoint(got, "203.0.113.5:41710") {
		t.Fatalf("the public IPv4 address is not in the invitation: %v", got)
	}
	v6 := 0
	for _, e := range got {
		if e.Addr().Is6() {
			v6++
		}
	}
	if v6 == 0 || v6 > 2 {
		t.Fatalf("want one or two IPv6 addresses, got %d: %v", v6, got)
	}
	if len(got) > maxInviteEndpoints {
		t.Fatalf("%d addresses, the limit is %d", len(got), maxInviteEndpoints)
	}
}

// Every way a newcomer may come is represented, in order of how much a missing one hurts, and the limit holds.
func TestInviteEndpointsKeepEveryKindWithinTheLimit(t *testing.T) {
	eps := []magic.Endpoint{
		endpoint(magic.EPMapped, "198.51.100.7:41710"),
		endpoint(magic.EPObserved, "198.51.100.8:50001"),
		endpoint(magic.EPObserved, "198.51.100.9:50002"),
		endpoint(magic.EPSTUN, "198.51.100.10:50003"),
		endpoint(magic.EPLocal, "192.168.1.23:41710"),
		endpoint(magic.EPLocal, "172.17.0.1:41710"),
		endpoint(magic.EPLocal, "10.0.0.4:41710"),
		endpoint(magic.EPLocal, "[2001:db8::1]:41710"),
		endpoint(magic.EPLocal, "[2001:db8::2]:41710"),
		endpoint(magic.EPLocal, "[fd00::1]:41710"),
		endpoint(magic.EPLocal, "127.0.0.1:41710"),
	}
	got := inviteEndpoints(eps)
	if len(got) != maxInviteEndpoints {
		t.Fatalf("want %d addresses, got %d: %v", maxInviteEndpoints, len(got), got)
	}
	for _, must := range []string{
		"127.0.0.1:41710",     // asked for explicitly (tests, several devices on one machine)
		"198.51.100.7:41710",  // the router forwards it: works for anybody
		"192.168.1.23:41710",  // the home network
		"198.51.100.8:50001",  // public IPv4
		"[2001:db8::1]:41710", // global IPv6
	} {
		if !hasEndpoint(got, must) {
			t.Fatalf("%s is missing from %v", must, got)
		}
	}
	seen := map[netip.AddrPort]bool{}
	for _, e := range got {
		if seen[e] {
			t.Fatalf("%s twice in %v", e, got)
		}
		seen[e] = true
	}
	if again := inviteEndpoints(eps); len(again) != len(got) || again[0] != got[0] || again[len(again)-1] != got[len(got)-1] {
		t.Fatalf("the choice is not stable: %v then %v", got, again)
	}
}

func TestInviteEndpointsOfNothing(t *testing.T) {
	if got := inviteEndpoints(nil); len(got) != 0 {
		t.Fatalf("got %v", got)
	}
}

// A join that fails says why, so that an interface can tell "this code is no good" from "that device did not answer".
func TestJoinErrorsSayWhy(t *testing.T) {
	nw := netsim.New()
	b := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.2")), "beta", nil)
	root, _, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	inviter := identity.ID{}
	if _, err := rand.Read(inviter[:]); err != nil {
		t.Fatal(err)
	}
	invite := func(ttl time.Duration, eps ...string) string {
		var list []netip.AddrPort
		for _, e := range eps {
			list = append(list, netip.MustParseAddrPort(e))
		}
		inv, err := identity.NewInvite(root, inviter, ttl, false, list, "Home")
		if err != nil {
			t.Fatal(err)
		}
		return inv.Encode()
	}

	// past its lifetime: said at once, without any network
	err = b.JoinMesh(context.Background(), invite(-time.Minute, "198.51.100.1:41710"), "beta")
	if !errors.Is(err, ErrInviteExpired) || errors.Is(err, ErrInviterUnreachable) {
		t.Fatalf("an expired invitation: %v", err)
	}
	if !strings.Contains(err.Error(), "check the date and time") {
		t.Fatalf("the message lost its advice: %v", err)
	}

	// nothing answers at the address in the invitation: unreachable, and the message names the addresses that were tried
	ctx, cancel := context.WithTimeout(context.Background(), 1500*time.Millisecond)
	defer cancel()
	err = b.JoinMesh(ctx, invite(time.Hour, "198.51.100.9:41710"), "beta")
	if !errors.Is(err, ErrInviterUnreachable) || errors.Is(err, ErrInviteExpired) {
		t.Fatalf("an inviter that does not answer: %v", err)
	}
	if !strings.Contains(err.Error(), "tried 198.51.100.9:41710") {
		t.Fatalf("the message does not say where it looked: %v", err)
	}
	if b.Configured() {
		t.Fatal("a failed join left the device configured")
	}

	// a code that is no invitation at all is neither
	err = b.JoinMesh(context.Background(), "MESH1-NOTACODE", "beta")
	if err == nil || errors.Is(err, ErrInviterUnreachable) || errors.Is(err, ErrInviteExpired) || errors.Is(err, ErrJoinRefused) {
		t.Fatalf("a malformed code: %v", err)
	}
}
