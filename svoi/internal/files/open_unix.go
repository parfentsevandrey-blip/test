//go:build unix

package files

import "syscall"

// oNonblock keeps open(2) from waiting for a writer when a path inside a share
// turns out to be a FIFO (without it one such request would hang a handler and
// hold a descriptor forever). Regular files and directories are unaffected.
const oNonblock = syscall.O_NONBLOCK
