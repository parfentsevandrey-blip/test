//go:build windows

package mesh

import (
	"net"
	"net/netip"

	"golang.org/x/sys/windows"
)

// The same two operations as on the other systems (see lansock_unix.go); Windows always lists its
// interfaces, so they are only a fallback here.

func joinGroupByAddr(c *net.UDPConn, group, local netip.Addr) error {
	return control(c, func(fd windows.Handle) error {
		return windows.SetsockoptIPMreq(fd, windows.IPPROTO_IP, windows.IP_ADD_MEMBERSHIP, &windows.IPMreq{Multiaddr: group.As4(), Interface: local.As4()})
	})
}

func setMulticastIfByAddr(c *net.UDPConn, local netip.Addr) error {
	return control(c, func(fd windows.Handle) error {
		return windows.SetsockoptInet4Addr(fd, windows.IPPROTO_IP, windows.IP_MULTICAST_IF, local.As4())
	})
}

func control(c *net.UDPConn, f func(fd windows.Handle) error) error {
	rc, err := c.SyscallConn()
	if err != nil {
		return err
	}
	var serr error
	if err := rc.Control(func(fd uintptr) { serr = f(windows.Handle(fd)) }); err != nil {
		return err
	}
	return serr
}
