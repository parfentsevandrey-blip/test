//go:build windows

// Package winsys holds the small Windows-specific helpers the rest of the
// application needs, with portable fallbacks so everything still builds and
// tests on other platforms.
package winsys

import "golang.org/x/sys/windows"

// IsElevated reports whether the process holds administrator rights. Creating
// the Wintun adapter and editing the routing table both require them, so the
// UI checks this before offering full-tunnel mode rather than letting the user
// discover it as a failure.
func IsElevated() bool {
	return windows.GetCurrentProcessToken().IsElevated()
}
