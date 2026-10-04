package magic

import (
	"net/netip"
	"os"
	"path/filepath"
	"testing"
)

func writeAddrsFile(t *testing.T, body string) {
	t.Helper()
	f := filepath.Join(t.TempDir(), "local-addrs.txt")
	if err := os.WriteFile(f, []byte(body), 0o600); err != nil {
		t.Fatal(err)
	}
	t.Setenv(localAddrsFileEnv, f)
}

func TestParseAddrFile(t *testing.T) {
	got := parseAddrFile([]byte("192.168.1.50/24, 10.20.30.40\n" +
		"2001:db8::5/64;fe80::1%wlan0 192.168.1.50/24 1.2.3.4/33 1.2.3.5/-1 1.2.3.6/x not-an-address 8.8.8.8/32\n" +
		"::ffff:10.1.1.1 100.64.0.9/10\n"))
	want := []fileAddr{
		{netip.MustParseAddr("192.168.1.50"), 24},
		{netip.MustParseAddr("10.20.30.40"), -1},
		{netip.MustParseAddr("2001:db8::5"), 64},
		{netip.MustParseAddr("8.8.8.8"), 32},
		{netip.MustParseAddr("::ffff:10.1.1.1"), -1},
		{netip.MustParseAddr("100.64.0.9"), 10},
	}
	if len(got) != len(want) {
		t.Fatalf("parsed %v, want %v", got, want)
	}
	for i := range want {
		if got[i] != want[i] {
			t.Errorf("entry %d: %v, want %v", i, got[i], want[i])
		}
	}
}

// What the phone's app writes, and what a phone's program makes of it when the system will not list the
// network interfaces (Android 11+): only the addresses on shared networks are announced on.
func TestLocalNetsFromTheAppsFileWhenTheSystemWillNotList(t *testing.T) {
	t.Setenv(HideInterfacesEnv, "1")
	writeAddrsFile(t, "192.168.1.50/24\n10.20.30.40\n127.0.0.1/8\n169.254.7.7/16\n100.64.1.1/10\n224.0.0.1/4\n0.0.0.0/0\n2001:db8::5/64\n172.16.5.9/22\n")
	if _, err := Interfaces(); err == nil {
		t.Fatal("the interfaces are listed although they are hidden")
	}
	var got []string
	for _, n := range LocalNets() {
		if n.Iface != nil {
			t.Errorf("%v has an interface although none can be listed", n)
		}
		got = append(got, n.String())
	}
	want := []string{"192.168.1.50/24", "172.16.5.9/22"} // not: bare, loopback, link-local, the overlay's range, multicast, IPv6
	if len(got) != len(want) || got[0] != want[0] || got[1] != want[1] {
		t.Fatalf("local networks %v, want %v", got, want)
	}
}

func TestBroadcastAddress(t *testing.T) {
	for _, c := range []struct {
		addr   string
		prefix int
		want   string // "" = the network has no broadcast address
	}{
		{"192.168.1.50", 24, "192.168.1.255"},
		{"192.168.1.50", 22, "192.168.3.255"},
		{"10.20.30.40", 8, "10.255.255.255"},
		{"172.16.5.9", 30, "172.16.5.11"},
		{"172.16.5.9", 31, ""},
		{"172.16.5.9", 32, ""},
		{"2001:db8::5", 64, ""},
	} {
		got, ok := LocalNet{Addr: netip.MustParseAddr(c.addr), Prefix: c.prefix}.Broadcast()
		switch {
		case c.want == "" && ok:
			t.Errorf("%s/%d has a broadcast address %v", c.addr, c.prefix, got)
		case c.want != "" && (!ok || got.String() != c.want):
			t.Errorf("%s/%d: broadcast %v (ok=%v), want %s", c.addr, c.prefix, got, ok, c.want)
		}
	}
}

func TestLocalPrefixesWhenTheSystemWillNotList(t *testing.T) {
	t.Setenv(HideInterfacesEnv, "1")
	writeAddrsFile(t, "192.168.1.50/24 10.20.30.40 192.168.9.9/0")
	got := map[string]bool{}
	for _, p := range LocalPrefixes() {
		got[p.String()] = true
	}
	for _, want := range []string{"192.168.1.50/24", "127.0.0.0/8"} {
		if !got[want] {
			t.Errorf("%s is missing from %v", want, got)
		}
	}
	for p := range got {
		if p == "10.20.30.40/32" || p == "192.168.9.9/0" {
			t.Errorf("%s is not a network this device is attached to", p)
		}
	}
	if contains := func(ip string) bool {
		for _, p := range LocalPrefixes() {
			if p.Contains(netip.MustParseAddr(ip)) {
				return true
			}
		}
		return false
	}; !contains("192.168.1.99") || contains("192.168.2.99") || contains("8.8.8.8") {
		t.Error("the on-link answer is wrong for the app's network")
	}
}
