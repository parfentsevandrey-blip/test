//go:build !windows

package magic

import (
	"errors"
	"syscall"
)

// listenControl: nothing to set; the system already refuses a second bind of a port that is in use.
var listenControl func(network, address string, c syscall.RawConn) error

func addrInUse(err error) bool { return errors.Is(err, syscall.EADDRINUSE) }
