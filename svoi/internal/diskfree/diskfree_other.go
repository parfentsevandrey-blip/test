//go:build !linux && !darwin && !freebsd && !windows

// Package diskfree reports the free space of a volume, where the platform allows.
package diskfree

// Free is not available on this platform; the free-space guard is skipped.
func Free(string) (uint64, bool) { return 0, false }
