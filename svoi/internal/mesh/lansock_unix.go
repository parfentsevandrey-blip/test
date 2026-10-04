//go:build unix

package mesh

import (
	"net"
	"net/netip"

	"golang.org/x/sys/unix"
)

// A program that may not list its network interfaces (Android 11+) still knows the address it has on a
// network, and the system finds the card by that address: this is how it joins the multicast group there
// and sends to it.

func joinGroupByAddr(c *net.UDPConn, group, local netip.Addr) error {
	return control(c, func(fd int) error {
		return unix.SetsockoptIPMreq(fd, unix.IPPROTO_IP, unix.IP_ADD_MEMBERSHIP, &unix.IPMreq{Multiaddr: group.As4(), Interface: local.As4()})
	})
}

func setMulticastIfByAddr(c *net.UDPConn, local netip.Addr) error {
	return control(c, func(fd int) error {
		return unix.SetsockoptInet4Addr(fd, unix.IPPROTO_IP, unix.IP_MULTICAST_IF, local.As4())
	})
}

func control(c *net.UDPConn, f func(fd int) error) error {
	rc, err := c.SyscallConn()
	if err != nil {
		return err
	}
	var serr error
	if err := rc.Control(func(fd uintptr) { serr = f(int(fd)) }); err != nil {
		return err
	}
	return serr
}
