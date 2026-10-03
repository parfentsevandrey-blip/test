package magic

import (
	"fmt"
	"net"
	"net/netip"
	"strconv"

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

// listenUDP opens a dual-stack UDP socket (falling back to IPv4 only) with
// generous buffers so bulk transfers do not drop packets in the kernel.
func listenUDP(port int) (net.PacketConn, error) {
	pc, err := net.ListenPacket("udp", ":"+strconv.Itoa(port))
	if err != nil {
		pc, err = net.ListenPacket("udp4", ":"+strconv.Itoa(port))
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

// defaultLocalAddrs enumerates interface addresses that are useful as
// endpoints: not loopback, not link-local, not multicast and not part of the
// svoi overlay itself (probing through our own tunnel would be circular).
func defaultLocalAddrs() []netip.Addr {
	var out []netip.Addr
	ifs, err := net.Interfaces()
	if err != nil {
		return nil
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
			if na.IsLoopback() || na.IsLinkLocalUnicast() || na.IsMulticast() || na.IsUnspecified() {
				continue
			}
			if identity.IsOverlayAddr(na) {
				continue
			}
			out = append(out, na)
		}
	}
	return out
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
	ips, err := net.LookupHost(host)
	if err != nil {
		return nil, err
	}
	var out []netip.AddrPort
	for _, s := range ips {
		if ip, err := netip.ParseAddr(s); err == nil {
			out = append(out, netip.AddrPortFrom(ip.Unmap(), uint16(port)))
		}
	}
	return out, nil
}
