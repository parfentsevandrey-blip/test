// Package tunnel implements TorVeil's full-tunnel mode: a Wintun adapter whose
// TCP traffic is turned into streams and handed to Tor.
//
// # Why a TUN adapter and not a real VPN tunnel
//
// A conventional VPN encapsulates IP packets and sends them to a server. Tor
// carries TCP streams, not packets, and has no UDP at all. So the adapter here
// terminates locally: a userspace network stack accepts each TCP connection
// off the virtual interface and re-opens it through Tor. The consequences are
// worth stating plainly rather than discovering later:
//
//   - UDP does not work. That includes QUIC, which is why the firewall blocks
//     UDP/443: browsers then fall back to TCP instead of stalling.
//   - ICMP does not work, so ping reports nothing useful about connectivity.
//   - DNS is handled out of band by a loopback forwarder that talks to Tor's
//     DNSPort, rather than being routed through the adapter.
//   - IPv6 is blocked rather than tunnelled, because an IPv6-capable machine
//     would otherwise reach IPv6 sites straight past the tunnel.
package tunnel

import (
	"context"
	"errors"
	"fmt"
	"net"
	"net/netip"
)

// Dialer opens a TCP connection through Tor.
type Dialer interface {
	DialContext(ctx context.Context, network, address string) (net.Conn, error)
}

// AutomapPrefix is the virtual address range Tor hands out for .onion names
// resolved through its DNSPort.
//
// Tor's default automap range sits inside 127.0.0.0/8, which Windows treats as
// loopback and never routes to an interface, so onion addresses would be
// unreachable in full-tunnel mode. TorVeil moves the range somewhere routable
// and points it at the tunnel instead.
var AutomapPrefix = netip.MustParsePrefix("10.192.0.0/10")

// ErrUnsupported reports that full-tunnel mode is not available on this
// platform. TorVeil's proxy mode works everywhere; the TUN path is Windows
// only.
var ErrUnsupported = errors.New("full-tunnel mode is only available on Windows")

// ErrNeedsAdmin reports that the adapter could not be created because the
// process is not elevated.
var ErrNeedsAdmin = errors.New("full-tunnel mode requires administrator rights")

// LogFunc receives tunnel diagnostics.
type LogFunc func(level, msg string)

// Options configures the tunnel.
type Options struct {
	// AdapterName is the Wintun adapter's display name.
	AdapterName string

	// Address is the tunnel interface address, e.g. 10.65.0.2/24.
	Address netip.Prefix

	// Gateway is the address the userspace stack answers on.
	Gateway netip.Addr

	// MTU of the tunnel interface.
	MTU int

	// Dialer opens each tunnelled TCP connection. It is TorVeil's shaped
	// dialer into Tor, the same one the loopback proxy uses, so both modes get
	// identical shaping and the same circuit policy.
	Dialer Dialer

	// DNSUpstream is Tor's DNSPort.
	DNSUpstream string

	// DNSListen is where the loopback DNS forwarder binds, normally
	// "127.0.0.1:53".
	DNSListen string

	// BypassCIDRs are destinations that must stay on the physical interface.
	BypassCIDRs []netip.Prefix

	// WatchProcesses names the executables whose outbound connections must
	// bypass the tunnel, discovered dynamically while running.
	WatchProcesses []string

	// AllowPrograms are executables exempt from the kill switch.
	AllowPrograms []string

	// StateDir is where the firewall journal is written, so rules left behind
	// by a crash can be cleaned up on the next start.
	StateDir string

	// ExcludeLAN keeps private ranges on the local network.
	ExcludeLAN bool

	// BlockIPv6, BlockQUIC and KillSwitch control the leak-protection rules.
	BlockIPv6  bool
	BlockQUIC  bool
	KillSwitch bool

	Log LogFunc
}

// Status describes the running tunnel.
type Status struct {
	Running      bool     `json:"running"`
	Adapter      string   `json:"adapter"`
	Address      string   `json:"address"`
	DNSListen    string   `json:"dnsListen"`
	BypassRoutes []string `json:"bypassRoutes"`
	KillSwitch   bool     `json:"killSwitch"`
	Warnings     []string `json:"warnings"`
}

func (o Options) validate() error {
	if o.AdapterName == "" {
		return errors.New("tunnel: adapter name is required")
	}
	if !o.Address.IsValid() {
		return errors.New("tunnel: interface address is required")
	}
	if !o.Gateway.IsValid() {
		return errors.New("tunnel: gateway address is required")
	}
	if o.Dialer == nil {
		return errors.New("tunnel: a dialer is required")
	}
	if o.DNSUpstream == "" {
		return errors.New("tunnel: DNS upstream address is required")
	}
	if o.MTU < 576 {
		return fmt.Errorf("tunnel: MTU %d is too small", o.MTU)
	}
	return nil
}

func (o Options) logf(level, format string, args ...any) {
	if o.Log != nil {
		o.Log(level, fmt.Sprintf(format, args...))
	}
}

// lanPrefixes are the ranges kept off the tunnel when ExcludeLAN is set.
// The tunnel's own subnet is on-link on the virtual adapter and therefore
// more specific than any of these, so it is not affected.
func lanPrefixes() []netip.Prefix {
	return []netip.Prefix{
		netip.MustParsePrefix("10.0.0.0/8"),
		netip.MustParsePrefix("172.16.0.0/12"),
		netip.MustParsePrefix("192.168.0.0/16"),
		netip.MustParsePrefix("169.254.0.0/16"),
		netip.MustParsePrefix("224.0.0.0/4"),
		netip.MustParsePrefix("255.255.255.255/32"),
	}
}

// defaultSplit is the pair of routes used instead of a literal 0.0.0.0/0.
//
// Two half-sized routes beat the existing default route on prefix length
// without deleting it, so the original default is still there to be restored
// and to serve the bypass routes while the tunnel is up.
func defaultSplit() []netip.Prefix {
	return []netip.Prefix{
		netip.MustParsePrefix("0.0.0.0/1"),
		netip.MustParsePrefix("128.0.0.0/1"),
	}
}
