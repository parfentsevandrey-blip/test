package magic

import (
	"errors"
	"net"
	"net/netip"
	"os"
	"strconv"
	"strings"
	"unicode"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
)

// What this device knows about the networks it sits on, in a form that survives an operating system
// that refuses to say.
//
// Since Android 11 an app may not make the netlink request behind net.Interfaces and net.InterfaceAddrs
// ("permission denied"), so on a phone the program cannot look at its own network cards. The app that runs
// it can, and writes what Android tells it into the file named by THEMESH_LOCAL_ADDRS_FILE, again whenever
// the network changes. One address per line (whitespace, commas and semicolons also separate):
//
//	192.168.1.50/24    an address on a network other devices share: Wi-Fi, Ethernet, a hotspot.
//	                   Local discovery announces itself there, and answers from that network are believed.
//	10.20.30.40        an address on a link nobody else is on (mobile data): offered to peers as a place to
//	                   reach us, nothing more. The same goes for any line without a prefix length.
//	vpn                a VPN is on, and the local network goes around it (see VPN).
//	vpn-lan            a VPN is on and takes the local network's traffic too (its routes cover the Wi-Fi's addresses).
//
// Everything here keeps working when the system does answer, so a desktop program never needs the file.
const (
	localAddrsFileEnv = "THEMESH_LOCAL_ADDRS_FILE"

	// HideInterfacesEnv makes Interfaces and InterfaceAddrs fail the way they fail on Android 11 and later.
	// For tests, and for seeing what a phone would do on an ordinary computer.
	HideInterfacesEnv = "THEMESH_HIDE_INTERFACES"
)

var errInterfacesHidden = errors.New("route ip+net: netlinkrib: permission denied (" + HideInterfacesEnv + " is set)")

// Interfaces is net.Interfaces. With THEMESH_HIDE_INTERFACES set it fails like Android 11+ does.
func Interfaces() ([]net.Interface, error) {
	if os.Getenv(HideInterfacesEnv) != "" {
		return nil, errInterfacesHidden
	}
	return net.Interfaces()
}

// InterfaceAddrs is net.InterfaceAddrs. With THEMESH_HIDE_INTERFACES set it fails like Android 11+ does.
func InterfaceAddrs() ([]net.Addr, error) {
	if os.Getenv(HideInterfacesEnv) != "" {
		return nil, errInterfacesHidden
	}
	return net.InterfaceAddrs()
}

// fileAddr is one entry of the app's address file; Prefix is -1 when the line named no prefix length.
type fileAddr struct {
	Addr   netip.Addr
	Prefix int
}

// parseAddrFile reads the app's address file (see above). Anything that is not an address, or whose
// prefix length is impossible, is ignored; so is a repeated address.
func parseAddrFile(raw []byte) []fileAddr {
	var out []fileAddr
	seen := map[netip.Addr]bool{}
	for _, f := range strings.FieldsFunc(string(raw), func(r rune) bool { return r == ',' || r == ';' || unicode.IsSpace(r) }) {
		prefix := -1
		host := f
		if i := strings.LastIndexByte(f, '/'); i >= 0 {
			p, err := strconv.Atoi(f[i+1:])
			if err != nil || p < 0 {
				continue
			}
			host, prefix = f[:i], p
		}
		a, err := netip.ParseAddr(host)
		if err != nil || a.Zone() != "" {
			continue
		}
		if prefix > a.BitLen() {
			continue
		}
		if seen[a] {
			continue
		}
		seen[a] = true
		out = append(out, fileAddr{a, prefix})
	}
	return out
}

// readLocalAddrsFile is the content of the app's address file; a missing or oversized one is an empty list.
func readLocalAddrsFile() []fileAddr {
	path := os.Getenv(localAddrsFileEnv)
	if path == "" {
		return nil
	}
	raw, err := os.ReadFile(path)
	if err != nil || len(raw) > 64<<10 {
		return nil
	}
	return parseAddrFile(raw)
}

// LocalNet is an IPv4 network this device shares with others: the device's address on it and how long the
// network's prefix is.
type LocalNet struct {
	Addr   netip.Addr     // this device's address there
	Prefix int            // length of the network prefix, 1..32
	Iface  *net.Interface // the network card; nil when the system would not say (Android 11+)
}

// String is "192.168.1.50/24", with the interface's name when there is one: "192.168.1.50/24 (en0)".
func (n LocalNet) String() string {
	s := netip.PrefixFrom(n.Addr, n.Prefix).String()
	if n.Iface != nil && n.Iface.Name != "" {
		s += " (" + n.Iface.Name + ")"
	}
	return s
}

// Network is the network's address with the host bits cleared.
func (n LocalNet) Network() netip.Prefix { return netip.PrefixFrom(n.Addr, n.Prefix).Masked() }

// Broadcast is the address that reaches every device on the network, if the network has one (a /31 or a
// /32 has none).
func (n LocalNet) Broadcast() (netip.Addr, bool) {
	if !n.Addr.Is4() || n.Prefix < 1 || n.Prefix > 30 {
		return netip.Addr{}, false
	}
	a := n.Addr.As4()
	host := ^uint32(0) >> uint(n.Prefix) // the host bits
	for i := 0; i < 4; i++ {
		a[i] |= byte(host >> (24 - 8*uint(i)))
	}
	return netip.AddrFrom4(a), true
}

// usableOnLAN: an address that other devices can be reached from and reach us at.
func usableOnLAN(a netip.Addr) bool {
	a = a.Unmap()
	return a.Is4() && !(a.IsLoopback() || a.IsLinkLocalUnicast() || a.IsMulticast() || a.IsUnspecified() || identity.IsOverlayAddr(a))
}

// LocalNets lists the IPv4 networks this device shares with others, which is where local discovery
// announces itself. They come from the system's own list of network interfaces (up, multicast capable,
// not loopback, not a tunnel); when the system will not give it, from the address file the app writes. Look
// again from time to time: Wi-Fi comes and goes.
func LocalNets() []LocalNet {
	var out []LocalNet
	have := map[netip.Addr]bool{}
	add := func(n LocalNet) {
		if have[n.Addr] || !usableOnLAN(n.Addr) || n.Prefix < 1 || n.Prefix > 32 {
			return
		}
		have[n.Addr] = true
		out = append(out, n)
	}
	if ifs, err := Interfaces(); err == nil {
		for _, ifc := range ifs {
			if !sharedLink(ifc.Flags) {
				continue
			}
			addrs, err := ifc.Addrs()
			if err != nil {
				continue
			}
			for _, a := range addrs {
				ipn, ok := a.(*net.IPNet)
				if !ok {
					continue
				}
				ip, ok := netip.AddrFromSlice(ipn.IP)
				ones, bits := ipn.Mask.Size()
				if !ok || bits != 32 && !(bits == 128 && ip.Is4In6()) {
					continue
				}
				if bits == 128 {
					ones -= 96
				}
				ifc := ifc
				add(LocalNet{Addr: ip.Unmap(), Prefix: ones, Iface: &ifc})
			}
		}
	}
	for _, e := range readLocalAddrsFile() {
		if e.Addr.Is4() && e.Prefix >= 0 {
			add(LocalNet{Addr: e.Addr, Prefix: e.Prefix})
		}
	}
	return out
}

// LocalPrefixes lists every network this device is directly attached to, loopback included: where a packet
// that claims to come from the local network may legitimately come from. The system's list (without the
// networks of VPN tunnels: whoever else is on a VPN provider's network is no neighbour), and the address
// file's entries that carry a prefix length.
func LocalPrefixes() []netip.Prefix {
	out := []netip.Prefix{netip.MustParsePrefix("127.0.0.0/8"), netip.MustParsePrefix("::1/128")}
	var addrs []net.Addr
	if ifs, err := Interfaces(); err == nil {
		for _, ifc := range ifs {
			if tunnel(ifc.Flags) {
				continue
			}
			if as, err := ifc.Addrs(); err == nil {
				addrs = append(addrs, as...)
			}
		}
	} else if as, err := InterfaceAddrs(); err == nil {
		addrs = as
	}
	for _, a := range addrs {
		ipn, ok := a.(*net.IPNet)
		if !ok {
			continue
		}
		ip, ok := netip.AddrFromSlice(ipn.IP)
		ones, bits := ipn.Mask.Size()
		if !ok || bits == 0 {
			continue
		}
		if bits == 128 && ip.Is4In6() {
			ones, bits = ones-96, 32
		}
		if ones < 1 {
			continue
		}
		if p := netip.PrefixFrom(ip.Unmap(), ones); p.IsValid() {
			out = append(out, p)
		}
	}
	for _, e := range readLocalAddrsFile() {
		if e.Prefix < 1 {
			continue
		}
		if p := netip.PrefixFrom(e.Addr, e.Prefix); p.IsValid() {
			out = append(out, p)
		}
	}
	return out
}

// sharedLink: an interface whose network other devices share — up, multicast capable, not loopback, and not a
// tunnel. A VPN's tunnel (utun on a Mac, tun, wg or ppp elsewhere) is a link to the VPN's server: announcing
// ourselves there would tell the VPN's operator our name, and nobody nearby is there to hear it.
func sharedLink(f net.Flags) bool {
	return f&net.FlagUp != 0 && f&net.FlagLoopback == 0 && f&net.FlagMulticast != 0 && !tunnel(f)
}

// tunnel: a point-to-point link, which is what the tunnels of VPNs are.
func tunnel(f net.Flags) bool { return f&net.FlagPointToPoint != 0 }

// VPN says whether a VPN is on, as far as local discovery is concerned:
//
//	"lan"  a VPN takes even the local network's traffic. Only the phone's app can tell (from the VPN's routes,
//	       see the address file above): devices nearby can still be heard, but what this device sends them goes
//	       into the VPN, which delivers it only if it lets local addresses go around it.
//	"on"   traffic to the internet leaves through a VPN tunnel; the local network most likely still goes around
//	       it (a network a device sits on wins over the VPN's routes), unless the VPN blocks it.
//	""     no VPN, or none that can be seen.
func VPN() string {
	if path := os.Getenv(localAddrsFileEnv); path != "" {
		raw, err := os.ReadFile(path)
		if err != nil || len(raw) > 64<<10 {
			return ""
		}
		return vpnInFile(raw)
	}
	if name, ok := internetVia(); ok && name != "" {
		return "on"
	}
	return ""
}

// vpnInFile reads the VPN's mark from the app's address file: "lan", "on" or "".
func vpnInFile(raw []byte) string {
	vpn := ""
	for _, f := range strings.FieldsFunc(string(raw), func(r rune) bool { return r == ',' || r == ';' || unicode.IsSpace(r) }) {
		switch f {
		case "vpn-lan":
			return "lan"
		case "vpn":
			vpn = "on"
		}
	}
	return vpn
}

// internetVia names the tunnel that traffic to the internet leaves through, if it is a tunnel; ok is false when that
// cannot be found out. Nothing is sent: connecting a UDP socket only asks the system for its route.
func internetVia() (tunnelName string, ok bool) {
	c, err := net.Dial("udp4", "198.51.100.1:9") // an address of the documentation range: routed like any other
	if err != nil {
		return "", false
	}
	local, _ := netip.AddrFromSlice(c.LocalAddr().(*net.UDPAddr).IP)
	_ = c.Close()
	local = local.Unmap()
	ifs, err := Interfaces()
	if err != nil {
		return "", false
	}
	for _, ifc := range ifs {
		addrs, err := ifc.Addrs()
		if err != nil {
			continue
		}
		for _, a := range addrs {
			if ipn, isNet := a.(*net.IPNet); isNet {
				if ip, good := netip.AddrFromSlice(ipn.IP); good && ip.Unmap() == local {
					if viaTunnel(ifc.Flags) {
						return ifc.Name, true
					}
					return "", true
				}
			}
		}
	}
	return "", false
}

// viaTunnel: is an interface that traffic to the internet leaves through a VPN's tunnel — a point-to-point link, or one
// that can neither broadcast nor be a loopback (Windows reports a WireGuard or Wintun adapter so).
func viaTunnel(f net.Flags) bool {
	return f&net.FlagLoopback == 0 && (tunnel(f) || f&net.FlagBroadcast == 0)
}
