package magic

import (
	"context"
	"crypto/rand"
	"encoding/binary"
	"fmt"
	"net"
	"net/netip"
	"os"
	"strconv"
	"strings"
	"time"
	"unicode"

	"golang.org/x/net/dns/dnsmessage"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
)

// udpSock is the minimal datagram socket magic needs. Real sockets use the
// allocation-free netip based methods; simulated ones go through net.PacketConn.
type udpSock interface {
	ReadFromAddrPort(b []byte) (int, netip.AddrPort, error)
	WriteToAddrPort(b []byte, to netip.AddrPort) (int, error)
	Close() error
}

type udpConnSock struct{ *net.UDPConn }

func (s udpConnSock) ReadFromAddrPort(b []byte) (int, netip.AddrPort, error) {
	return s.UDPConn.ReadFromUDPAddrPort(b)
}

func (s udpConnSock) WriteToAddrPort(b []byte, to netip.AddrPort) (int, error) {
	return s.UDPConn.WriteToUDPAddrPort(b, to)
}

type pcSock struct{ net.PacketConn }

func (s pcSock) ReadFromAddrPort(b []byte) (int, netip.AddrPort, error) {
	n, a, err := s.PacketConn.ReadFrom(b)
	if err != nil {
		return 0, netip.AddrPort{}, err
	}
	if ua, ok := a.(*net.UDPAddr); ok {
		return n, ua.AddrPort(), nil
	}
	ap, err := netip.ParseAddrPort(a.String())
	return n, ap, err
}

func (s pcSock) WriteToAddrPort(b []byte, to netip.AddrPort) (int, error) {
	return s.PacketConn.WriteTo(b, net.UDPAddrFromAddrPort(to))
}

func wrapSock(pc net.PacketConn) udpSock {
	if uc, ok := pc.(*net.UDPConn); ok {
		return udpConnSock{uc}
	}
	return pcSock{pc}
}

// listenUDP opens a dual-stack UDP socket (an IPv4 one where there is no IPv6) with generous buffers so
// bulk transfers do not drop packets in the kernel.
//
// A port that is taken is reported as taken. It is never answered with an IPv4-only socket on the same port:
// Windows lets that one in beside the dual-stack socket of another node and hands it the IPv4 traffic, so
// two nodes on one machine (a join over loopback, a second copy of the program) would talk to themselves.
func listenUDP(port int) (net.PacketConn, error) {
	addr := ":" + strconv.Itoa(port)
	lc := net.ListenConfig{Control: listenControl}
	pc, err := lc.ListenPacket(context.Background(), "udp", addr)
	if err != nil {
		if addrInUse(err) {
			return nil, err
		}
		pc, err = lc.ListenPacket(context.Background(), "udp4", addr)
		if err != nil {
			return nil, err
		}
	}
	if uc, ok := pc.(*net.UDPConn); ok {
		_ = uc.SetReadBuffer(7 << 20)
		_ = uc.SetWriteBuffer(7 << 20)
	}
	return pc, nil
}

// DefaultLocalAddrs enumerates interface addresses that are useful as
// endpoints: not loopback, not link-local, not multicast and not part of the
// svoi overlay itself (probing through our own tunnel would be circular).
//
// Since Android 11 an app may not list the network interfaces at all (net.Interfaces fails with
// "permission denied"); the app that runs svoi then writes the addresses it can see into the file
// named by SVOI_LOCAL_ADDRS_FILE (whitespace or comma separated), and they are used as well.
func DefaultLocalAddrs() []netip.Addr {
	var out []netip.Addr
	usable := func(na netip.Addr) bool {
		na = na.Unmap()
		return !(na.IsLoopback() || na.IsLinkLocalUnicast() || na.IsMulticast() || na.IsUnspecified() || identity.IsOverlayAddr(na))
	}
	if f := os.Getenv("SVOI_LOCAL_ADDRS_FILE"); f != "" {
		for _, na := range readAddrFile(f) {
			if usable(na) {
				out = append(out, na.Unmap())
			}
		}
	}
	ifs, err := net.Interfaces()
	if err != nil {
		return out
	}
	for _, ifc := range ifs {
		if ifc.Flags&net.FlagUp == 0 || ifc.Flags&net.FlagLoopback != 0 {
			continue
		}
		addrs, err := ifc.Addrs()
		if err != nil {
			continue
		}
		for _, a := range addrs {
			var ip net.IP
			switch v := a.(type) {
			case *net.IPNet:
				ip = v.IP
			case *net.IPAddr:
				ip = v.IP
			}
			na, ok := netip.AddrFromSlice(ip)
			if !ok {
				continue
			}
			na = na.Unmap()
			if !usable(na) || containsAddr(out, na) {
				continue
			}
			out = append(out, na)
		}
	}
	return out
}

func containsAddr(list []netip.Addr, a netip.Addr) bool {
	for _, x := range list {
		if x == a {
			return true
		}
	}
	return false
}

// readAddrFile reads IP addresses (whitespace or comma separated) from a small file; anything that is
// not an address is ignored, a missing file is an empty list.
func readAddrFile(path string) []netip.Addr {
	raw, err := os.ReadFile(path)
	if err != nil || len(raw) > 64<<10 {
		return nil
	}
	var out []netip.Addr
	for _, f := range strings.FieldsFunc(string(raw), func(r rune) bool { return r == ',' || r == ';' || unicode.IsSpace(r) }) {
		if a, err := netip.ParseAddr(f); err == nil && !containsAddr(out, a) {
			out = append(out, a)
		}
	}
	return out
}

// Name resolution for STUN servers. The system resolver comes first; when it
// does not work at all (a static binary on Android/Termux has no resolv.conf, a
// minimal container has none either) well-known public DNS servers are asked
// directly. Only STUN host names are ever resolved, and only while STUN is enabled.
var (
	publicDNS = []string{"1.1.1.1:53", "8.8.8.8:53"}

	systemLookup = func(ctx context.Context, host string) ([]string, error) {
		return net.DefaultResolver.LookupHost(ctx, host)
	}
	fallbackLookup = func(ctx context.Context, host string) ([]string, error) {
		return dnsLookup(ctx, host, publicDNS)
	}
)

// dnsLookup asks the given DNS servers (in order) for the A and AAAA records of host.
func dnsLookup(ctx context.Context, host string, servers []string) ([]string, error) {
	var last error
	for _, srv := range servers {
		addrs, err := queryDNS(ctx, srv, host)
		if len(addrs) > 0 {
			return addrs, nil
		}
		if err != nil {
			last = err
		}
	}
	if last == nil {
		last = fmt.Errorf("no addresses for %q", host)
	}
	return nil, last
}

func queryDNS(ctx context.Context, server, host string) ([]string, error) {
	name, err := dnsmessage.NewName(strings.TrimSuffix(host, ".") + ".")
	if err != nil {
		return nil, err
	}
	var d net.Dialer
	conn, err := d.DialContext(ctx, "udp", server)
	if err != nil {
		return nil, err
	}
	defer conn.Close()
	deadline := time.Now().Add(1200 * time.Millisecond)
	if dl, ok := ctx.Deadline(); ok && dl.Before(deadline) {
		deadline = dl
	}
	_ = conn.SetDeadline(deadline)

	pending := map[uint16]bool{}
	for _, qt := range []dnsmessage.Type{dnsmessage.TypeA, dnsmessage.TypeAAAA} {
		var id [2]byte
		if _, err := rand.Read(id[:]); err != nil {
			return nil, err
		}
		txid := binary.BigEndian.Uint16(id[:])
		q := dnsmessage.Message{
			Header:    dnsmessage.Header{ID: txid, RecursionDesired: true},
			Questions: []dnsmessage.Question{{Name: name, Type: qt, Class: dnsmessage.ClassINET}},
		}
		b, err := q.Pack()
		if err != nil {
			return nil, err
		}
		if _, err := conn.Write(b); err != nil {
			return nil, err
		}
		pending[txid] = true
	}
	var out []string
	var rerr error
	buf := make([]byte, 1500)
	for len(pending) > 0 {
		n, err := conn.Read(buf)
		if err != nil {
			rerr = err
			break
		}
		var m dnsmessage.Message
		if m.Unpack(buf[:n]) != nil || !m.Response || !pending[m.ID] {
			continue
		}
		delete(pending, m.ID)
		for _, a := range m.Answers {
			switch r := a.Body.(type) {
			case *dnsmessage.AResource:
				out = append(out, netip.AddrFrom4(r.A).String())
			case *dnsmessage.AAAAResource:
				out = append(out, netip.AddrFrom16(r.AAAA).String())
			}
		}
		if len(out) > 0 {
			// Do not wait long for the other record type.
			_ = conn.SetDeadline(time.Now().Add(150 * time.Millisecond))
		}
	}
	if len(out) == 0 {
		return nil, rerr
	}
	return out, nil
}

// resolveHostPort resolves "host:port" to addresses.
func resolveHostPort(hp string) ([]netip.AddrPort, error) {
	host, ps, err := net.SplitHostPort(hp)
	if err != nil {
		return nil, err
	}
	port, err := strconv.Atoi(ps)
	if err != nil {
		return nil, fmt.Errorf("bad port in %q", hp)
	}
	if ip, err := netip.ParseAddr(host); err == nil {
		return []netip.AddrPort{netip.AddrPortFrom(ip.Unmap(), uint16(port))}, nil
	}
	var lastErr error
	for _, lookup := range []func(context.Context, string) ([]string, error){systemLookup, fallbackLookup} {
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		ips, err := lookup(ctx, host)
		cancel()
		var out []netip.AddrPort
		for _, s := range ips {
			if ip, perr := netip.ParseAddr(s); perr == nil {
				out = append(out, netip.AddrPortFrom(ip.Unmap(), uint16(port)))
			}
		}
		if len(out) > 0 {
			return out, nil
		}
		if err != nil {
			lastErr = err
		}
	}
	if lastErr == nil {
		lastErr = fmt.Errorf("no addresses for %q", host)
	}
	return nil, lastErr
}

// SetReadBuffer and SetWriteBuffer let the QUIC stack ask for bigger kernel
// buffers on the socket underneath (which is ours); without them it logs a
// warning that it cannot, on every start.
func (c *Conn) SetReadBuffer(n int) error {
	if s, ok := c.sock.(interface{ SetReadBuffer(int) error }); ok {
		return s.SetReadBuffer(n)
	}
	return nil
}

// SetWriteBuffer: see SetReadBuffer.
func (c *Conn) SetWriteBuffer(n int) error {
	if s, ok := c.sock.(interface{ SetWriteBuffer(int) error }); ok {
		return s.SetWriteBuffer(n)
	}
	return nil
}
