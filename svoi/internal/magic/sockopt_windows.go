//go:build windows

package magic

import (
	"errors"
	"syscall"

	"golang.org/x/sys/windows"
)

// soExclusiveAddrUse is SO_EXCLUSIVEADDRUSE, which is ~SO_REUSEADDR in winsock2.h.
const soExclusiveAddrUse = ^windows.SO_REUSEADDR

// listenControl makes the port ours alone: no other socket may bind it, whatever options it asks for.
// Without this Windows lets a program that asks for it (and even one that does not, if its address
// family differs) share the port of another one and take the traffic away from it.
func listenControl(network, address string, c syscall.RawConn) error {
	var serr error
	err := c.Control(func(fd uintptr) {
		serr = windows.SetsockoptInt(windows.Handle(fd), windows.SOL_SOCKET, soExclusiveAddrUse, 1)
	})
	if err != nil {
		return err
	}
	return serr
}

// addrInUse says whether binding failed because another socket has the port: WSAEADDRINUSE, or WSAEACCES
// when it holds the port exclusively.
func addrInUse(err error) bool {
	var errno syscall.Errno
	return errors.As(err, &errno) && (errno == windows.WSAEADDRINUSE || errno == windows.WSAEACCES)
}
