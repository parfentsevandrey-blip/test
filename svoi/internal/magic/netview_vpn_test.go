package magic

import (
	"net"
	"testing"
)

// A VPN's tunnel is no local network: nothing is announced there (its operator would read our name), and traffic to the
// internet that leaves through one means a VPN is on.
func TestTunnelsAreNoLocalNetworks(t *testing.T) {
	for _, c := range []struct {
		what           string
		f              net.Flags
		shared, viaVPN bool
	}{
		{"Wi-Fi or Ethernet", net.FlagUp | net.FlagBroadcast | net.FlagMulticast, true, false},
		{"utun on a Mac, tun on Linux", net.FlagUp | net.FlagPointToPoint | net.FlagMulticast, false, true},
		{"WireGuard on Linux", net.FlagUp | net.FlagPointToPoint, false, true},
		{"WireGuard or Wintun on Windows", net.FlagUp, false, true},
		{"loopback", net.FlagUp | net.FlagLoopback | net.FlagMulticast, false, false},
		{"a card that is down", net.FlagBroadcast | net.FlagMulticast, false, false},
	} {
		if got := sharedLink(c.f); got != c.shared {
			t.Errorf("%s (%v): shared link %v, want %v", c.what, c.f, got, c.shared)
		}
		if got := viaTunnel(c.f); got != c.viaVPN {
			t.Errorf("%s (%v): a VPN's tunnel %v, want %v", c.what, c.f, got, c.viaVPN)
		}
	}
}

// On a phone the app says whether a VPN is on, and whether it takes the local network's traffic too; the marks are not
// addresses.
func TestVPNFromTheAppsFile(t *testing.T) {
	t.Setenv(HideInterfacesEnv, "1")
	for _, c := range []struct{ file, want string }{
		{"192.168.1.50/24\n10.8.0.2\nvpn-lan\n", "lan"},
		{"192.168.1.50/24\nvpn\n", "on"},
		{"vpn, vpn-lan", "lan"},
		{"192.168.1.50/24\n10.8.0.2\n", ""},
		{"", ""},
	} {
		writeAddrsFile(t, c.file)
		if got := VPN(); got != c.want {
			t.Errorf("file %q: VPN %q, want %q", c.file, got, c.want)
		}
	}
	writeAddrsFile(t, "192.168.1.50/24\nvpn-lan\n")
	nets := LocalNets()
	if len(nets) != 1 || nets[0].String() != "192.168.1.50/24" {
		t.Fatalf("local networks %v, want only 192.168.1.50/24", nets)
	}
}
