package tor

import (
	"strings"
	"testing"
)

// A trimmed but structurally faithful "GETINFO ns/all" response.
// Each relay sits in a distinct /16, because Tor refuses to place two hops of
// one circuit in the same one and a shared /16 here would quietly make paths
// unsatisfiable.
const sampleConsensus = `
r Alpha AAAAAAAAAAAAAAAAAAAAAAAAAAA yyyyyyyyyyyyyyyyyyyyyyyyyyy 2026-01-01 00:00:00 198.51.100.1 9001 0
s Fast Guard Running Stable V2Dir Valid
w Bandwidth=12000
p reject 1-65535
r Bravo BBBBBBBBBBBBBBBBBBBBBBBBBBB zzzzzzzzzzzzzzzzzzzzzzzzzzz 2026-01-01 00:00:00 203.0.113.2 9001 0
s Exit Fast Running Stable Valid
w Bandwidth=8000
p accept 80,443,8000-8100
r Charlie CCCCCCCCCCCCCCCCCCCCCCCCCCC wwwwwwwwwwwwwwwwwwwwwwwwwww 2026-01-01 00:00:00 192.0.2.5 9001 0
s Fast Running Valid
w Bandwidth=4000
p reject 1-65535
r Delta DDDDDDDDDDDDDDDDDDDDDDDDDDD vvvvvvvvvvvvvvvvvvvvvvvvvvv 2026-01-01 00:00:00 198.18.0.9 9001 0
s BadExit Exit Fast Running Valid
w Bandwidth=9000
p accept 80,443
`

// newTestDirectory builds a Directory from the sample consensus, assigning
// each relay the country named by countries[nickname].
func newTestDirectory(t *testing.T, countries map[string]string) *Directory {
	t.Helper()
	relays := parseSample(t)
	d := NewDirectory()
	d.relays = map[string]*Relay{}
	for _, r := range relays {
		if cc, ok := countries[r.Nickname]; ok {
			r.Country = cc
		}
		d.relays[r.Fingerprint] = r
		d.order = append(d.order, r)
	}
	return d
}

func parseSample(t *testing.T) []*Relay {
	t.Helper()
	relays, err := parseNetworkStatus(sampleConsensus)
	if err != nil {
		t.Fatalf("parseNetworkStatus: %v", err)
	}
	if len(relays) != 4 {
		t.Fatalf("got %d relays, want 4", len(relays))
	}
	return relays
}

func TestParseNetworkStatus(t *testing.T) {
	relays := parseSample(t)

	alpha := relays[0]
	if alpha.Nickname != "Alpha" {
		t.Errorf("nickname = %q, want Alpha", alpha.Nickname)
	}
	if alpha.IP != "198.51.100.1" || alpha.ORPort != 9001 {
		t.Errorf("address = %s:%d, want 198.51.100.1:9001", alpha.IP, alpha.ORPort)
	}
	if alpha.Bandwidth != 12000 {
		t.Errorf("bandwidth = %d, want 12000", alpha.Bandwidth)
	}
	if !alpha.IsGuard() {
		t.Error("Alpha has Guard, Fast, Stable, Running and Valid, so it should qualify as a guard")
	}
	if alpha.IsExit() {
		t.Error("Alpha has no Exit flag")
	}

	// Fingerprints must come back as 40 uppercase hex characters, because that
	// is the only form EXTENDCIRCUIT accepts.
	if len(alpha.Fingerprint) != 40 {
		t.Errorf("fingerprint %q is %d characters, want 40", alpha.Fingerprint, len(alpha.Fingerprint))
	}
	if alpha.Fingerprint != strings.ToUpper(alpha.Fingerprint) {
		t.Errorf("fingerprint %q is not uppercase", alpha.Fingerprint)
	}

	if relays[3].IsExit() {
		t.Error("Delta carries BadExit and must never be selected as an exit")
	}
}

func TestExitPolicy(t *testing.T) {
	relays := parseSample(t)
	bravo := relays[1] // p accept 80,443,8000-8100
	charlie := relays[2]

	for _, port := range []int{80, 443, 8000, 8050, 8100} {
		if !bravo.AllowsPort(port) {
			t.Errorf("Bravo should allow port %d", port)
		}
	}
	for _, port := range []int{22, 7999, 8101} {
		if bravo.AllowsPort(port) {
			t.Errorf("Bravo should reject port %d", port)
		}
	}
	if charlie.AllowsPort(443) {
		t.Error("Charlie rejects 1-65535 and should allow nothing")
	}
}

func TestSelectPathRespectsPolicy(t *testing.T) {
	d := newTestDirectory(t, map[string]string{
		"Alpha": "de", "Bravo": "de", "Charlie": "nl", "Delta": "nl",
	})

	path, err := d.SelectPath(PathPolicy{Hops: 3, ExitCountry: "de"}, 443)
	if err != nil {
		t.Fatalf("SelectPath: %v", err)
	}
	if len(path.Fingerprints) != 3 {
		t.Fatalf("path has %d hops, want 3", len(path.Fingerprints))
	}

	exit := path.Relays[2]
	if exit.Nickname != "Bravo" {
		t.Errorf("exit = %s, want Bravo (the only non-BadExit exit in DE allowing 443)", exit.Nickname)
	}
	if path.Relays[0].Nickname != "Alpha" {
		t.Errorf("entry = %s, want Alpha (the only guard)", path.Relays[0].Nickname)
	}

	seen := map[string]bool{}
	for _, fp := range path.Fingerprints {
		if seen[fp] {
			t.Errorf("relay %s appears twice in one path", fp)
		}
		seen[fp] = true
	}
}

func TestSelectPathReportsImpossibleConstraints(t *testing.T) {
	d := newTestDirectory(t, map[string]string{
		"Alpha": "de", "Bravo": "de", "Charlie": "de", "Delta": "de",
	})

	// No exit relay is in France, so this must fail loudly rather than
	// silently falling back to some other country.
	_, err := d.SelectPath(PathPolicy{Hops: 3, ExitCountry: "fr"}, 443)
	if err == nil {
		t.Fatal("expected an error when no exit exists in the requested country")
	}
	if !strings.Contains(err.Error(), "FR") {
		t.Errorf("error should name the requested country, got: %v", err)
	}

	// The only exit that allows 443 is Bravo; asking for a port it rejects
	// must fail rather than hand back an exit that cannot serve the stream.
	if _, err := d.SelectPath(PathPolicy{Hops: 3}, 22); err == nil {
		t.Error("expected an error when no exit allows the requested port")
	}
}

// TestSelectPathKeepsHopsInDistinctSubnets guards the rule that stops one
// network operator from holding two hops of the same circuit.
func TestSelectPathKeepsHopsInDistinctSubnets(t *testing.T) {
	d := newTestDirectory(t, nil)
	for i := 0; i < 20; i++ {
		path, err := d.SelectPath(PathPolicy{Hops: 3}, 443)
		if err != nil {
			t.Fatalf("SelectPath: %v", err)
		}
		seen := map[string]string{}
		for _, r := range path.Relays {
			if r == nil {
				continue
			}
			net := r.slash16()
			if prev, ok := seen[net]; ok {
				t.Fatalf("%s and %s are both in %s.0.0/16", prev, r.Nickname, net)
			}
			seen[net] = r.Nickname
		}
	}
}

func TestSelectPathWithBridgeUsesBridgeAsFirstHop(t *testing.T) {
	d := newTestDirectory(t, map[string]string{
		"Alpha": "de", "Bravo": "de", "Charlie": "de", "Delta": "de",
	})

	const bridgeFP = "2B280B23E1107BB62ABFC40DDCC8824814F80A72"
	path, err := d.SelectPath(PathPolicy{Hops: 4, BridgeFingerprint: bridgeFP}, 443)
	if err != nil {
		t.Fatalf("SelectPath: %v", err)
	}
	if len(path.Fingerprints) != 4 {
		t.Fatalf("path has %d hops, want 4", len(path.Fingerprints))
	}
	if path.Fingerprints[0] != bridgeFP {
		t.Errorf("first hop = %s, want the bridge %s", path.Fingerprints[0], bridgeFP)
	}
	if path.Relays[0] != nil {
		t.Error("a bridge is not in the consensus, so its relay entry should be nil")
	}
}

func TestParseFamily(t *testing.T) {
	md := `onion-key
ntor-onion-key abc
family $AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA $BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=nick somenickname
p accept 80,443
`
	fam := parseFamily(md)
	if len(fam) != 2 {
		t.Fatalf("got %d family members, want 2 (bare nicknames are unverifiable and must be dropped): %v", len(fam), fam)
	}
	if fam[0] != "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" {
		t.Errorf("first family member = %q", fam[0])
	}
}

func TestFamilyConflictRequiresMutualDeclaration(t *testing.T) {
	d := NewDirectory()
	a := &Relay{Fingerprint: "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"}
	b := &Relay{Fingerprint: "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"}
	c := &Relay{Fingerprint: "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"}
	path := Path{
		Fingerprints: []string{a.Fingerprint, b.Fingerprint, c.Fingerprint},
		Relays:       []*Relay{a, b, c},
	}

	// One-sided declarations are not a family: Tor requires both relays to
	// list each other, otherwise anyone could exclude a relay by naming it.
	a.Family = []string{b.Fingerprint}
	b.Family = nil
	if _, conflict := d.FamilyConflict(path); conflict {
		t.Error("a one-sided family declaration should not count as a conflict")
	}

	b.Family = []string{a.Fingerprint}
	if _, conflict := d.FamilyConflict(path); !conflict {
		t.Error("mutual family declarations should be reported as a conflict")
	}
}

func TestIsolationKey(t *testing.T) {
	tests := map[string]string{
		"example.com":            "example.com",
		"www.example.com":        "example.com",
		"cdn.assets.example.com": "example.com",
		"other.org":              "other.org",
		"192.0.2.4":              "192.0.2.4",
		"localhost":              "localhost",
		"":                       "default",
	}
	for in, want := range tests {
		if got := isolationKey(in); got != want {
			t.Errorf("isolationKey(%q) = %q, want %q", in, got, want)
		}
	}
	if isolationKey("a.example.com") == isolationKey("a.example.org") {
		t.Error("unrelated sites must not share a circuit")
	}
}

func TestSplitTarget(t *testing.T) {
	tests := []struct {
		in   string
		host string
		port int
	}{
		{"example.com:443", "example.com", 443},
		{"192.0.2.1:80", "192.0.2.1", 80},
		{"[2001:db8::1]:443", "2001:db8::1", 443},
		{"example.com", "example.com", 0}, // NEWRESOLVE has no port
	}
	for _, tc := range tests {
		host, port := splitTarget(tc.in)
		if host != tc.host || port != tc.port {
			t.Errorf("splitTarget(%q) = (%q, %d), want (%q, %d)", tc.in, host, port, tc.host, tc.port)
		}
	}
}

func TestCircuitHopCount(t *testing.T) {
	status := "4 BUILT $AAA~one,$BBB~two,$CCC~three PURPOSE=GENERAL\n5 LAUNCHED  PURPOSE=GENERAL"
	if n, ok := circuitHopCount(status, "4"); !ok || n != 3 {
		t.Errorf("circuitHopCount = (%d, %v), want (3, true)", n, ok)
	}
	if _, ok := circuitHopCount(status, "99"); ok {
		t.Error("an unknown circuit should not be reported")
	}
}
