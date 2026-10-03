//go:build !linux && !darwin && !freebsd && !windows

package files

// diskFree is not available on this platform; the free-space guard is skipped.
func diskFree(string) (uint64, bool) { return 0, false }
