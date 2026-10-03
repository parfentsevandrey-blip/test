//go:build linux

package tun

import (
	"fmt"
	"net"
	"os"
	"unsafe"

	"github.com/vishvananda/netlink"
	"golang.org/x/sys/unix"
)

// device is a Linux TUN interface carrying raw IP packets.
type device struct {
	f    *os.File
	name string
}

type ifreq struct {
	name  [unix.IFNAMSIZ]byte
	flags uint16
	_     [22]byte
}

func createDevice(name string) (*device, error) {
	fd, err := unix.Open("/dev/net/tun", unix.O_RDWR|unix.O_CLOEXEC|unix.O_NONBLOCK, 0)
	if err != nil {
		return nil, fmt.Errorf("cannot open /dev/net/tun (run as root, or grant CAP_NET_ADMIN): %w", err)
	}
	var req ifreq
	copy(req.name[:], name)
	req.flags = unix.IFF_TUN | unix.IFF_NO_PI
	if _, _, errno := unix.Syscall(unix.SYS_IOCTL, uintptr(fd), uintptr(unix.TUNSETIFF), uintptr(unsafe.Pointer(&req))); errno != 0 {
		unix.Close(fd)
		return nil, fmt.Errorf("cannot create the TUN interface %q: %w", name, errno)
	}
	real := string(req.name[:])
	for i, c := range req.name {
		if c == 0 {
			real = string(req.name[:i])
			break
		}
	}
	return &device{f: os.NewFile(uintptr(fd), "/dev/net/tun"), name: real}, nil
}

func (d *device) Name() string                { return d.name }
func (d *device) Read(p []byte) (int, error)  { return d.f.Read(p) }
func (d *device) Write(p []byte) (int, error) { return d.f.Write(p) }
func (d *device) Close() error                { return d.f.Close() }

// configure assigns the IPv4 address (required) and the IPv6 address (best
// effort: some kernels and containers have IPv6 disabled), sets the MTU and
// brings the interface up. It reports whether IPv6 was configured.
func (d *device) configure(v4, v6 *net.IPNet, mtu int) (hasV6 bool, err error) {
	link, err := netlink.LinkByName(d.name)
	if err != nil {
		return false, err
	}
	if err := netlink.LinkSetMTU(link, mtu); err != nil {
		return false, fmt.Errorf("set MTU: %w", err)
	}
	if err := netlink.AddrReplace(link, &netlink.Addr{IPNet: v4}); err != nil {
		return false, fmt.Errorf("add address %s: %w", v4, err)
	}
	if err := netlink.LinkSetUp(link); err != nil {
		return false, err
	}
	if v6 != nil {
		if err := netlink.AddrReplace(link, &netlink.Addr{IPNet: v6}); err == nil {
			hasV6 = true
		}
	}
	return hasV6, nil
}

// Supported reports whether TUN mode exists on this platform.
const Supported = true
