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
// not loopback); when the system will not give it, from the address file the app writes. Look again from
// time to time: Wi-Fi comes and goes.
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
			if ifc.Flags&net.FlagUp == 0 || ifc.Flags&net.FlagLoopback != 0 || ifc.Flags&net.FlagMulticast == 0 {
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
// that claims to come from the local network may legitimately come from. The system's list, and the
// address file's entries that carry a prefix length.
func LocalPrefixes() []netip.Prefix {
	out := []netip.Prefix{netip.MustParsePrefix("127.0.0.0/8"), netip.MustParsePrefix("::1/128")}
	if addrs, err := InterfaceAddrs(); err == nil {
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
