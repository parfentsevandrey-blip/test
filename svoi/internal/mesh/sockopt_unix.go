//go:build unix

package mesh

import (
	"runtime"
	"syscall"

	"golang.org/x/sys/unix"
)

// reuseAddrControl lets several processes on one machine listen on the LAN
// beacon port, which makes running multiple nodes locally (tests, demos) work.
func reuseAddrControl(network, address string, c syscall.RawConn) error {
	var serr error
	err := c.Control(func(fd uintptr) {
		serr = unix.SetsockoptInt(int(fd), unix.SOL_SOCKET, unix.SO_REUSEADDR, 1)
		if serr == nil && runtime.GOOS != "linux" {
			// BSD and macOS need SO_REUSEPORT to share multicast listeners.
			serr = unix.SetsockoptInt(int(fd), unix.SOL_SOCKET, unix.SO_REUSEPORT, 1)
		}
	})
	if err != nil {
		return err
	}
	return serr
}
