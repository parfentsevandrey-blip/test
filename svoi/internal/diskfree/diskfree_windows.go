//go:build windows

// Package diskfree reports the free space of a volume, where the platform allows.
package diskfree

import "golang.org/x/sys/windows"

func Free(dir string) (uint64, bool) {
	p, err := windows.UTF16PtrFromString(dir)
	if err != nil {
		return 0, false
	}
	var avail, total, free uint64
	if err := windows.GetDiskFreeSpaceEx(p, &avail, &total, &free); err != nil {
		return 0, false
	}
	return avail, true
}
