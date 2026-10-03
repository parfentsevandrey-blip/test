//go:build linux || darwin || freebsd

package files

import "golang.org/x/sys/unix"

// diskFree reports the bytes available to an unprivileged user on the volume holding dir.
func diskFree(dir string) (uint64, bool) {
	var st unix.Statfs_t
	if err := unix.Statfs(dir, &st); err != nil {
		return 0, false
	}
	return uint64(st.Bavail) * uint64(st.Bsize), true
}
