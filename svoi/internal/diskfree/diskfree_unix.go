//go:build linux || darwin || freebsd

// Package diskfree reports the free space of a volume, where the platform allows.
package diskfree

import "golang.org/x/sys/unix"

// Free reports the bytes available to an unprivileged user on the volume holding dir.
func Free(dir string) (uint64, bool) {
	var st unix.Statfs_t
	if err := unix.Statfs(dir, &st); err != nil {
		return 0, false
	}
	return uint64(st.Bavail) * uint64(st.Bsize), true
}
