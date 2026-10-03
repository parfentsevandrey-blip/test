//go:build windows

package mesh

import (
	"syscall"

	"golang.org/x/sys/windows"
)

func reuseAddrControl(network, address string, c syscall.RawConn) error {
	var serr error
	err := c.Control(func(fd uintptr) {
		serr = windows.SetsockoptInt(windows.Handle(fd), windows.SOL_SOCKET, windows.SO_REUSEADDR, 1)
	})
	if err != nil {
		return err
	}
	return serr
}
