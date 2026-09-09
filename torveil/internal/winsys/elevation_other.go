//go:build !windows

// Package winsys holds the small Windows-specific helpers the rest of the
// application needs, with portable fallbacks so everything still builds and
// tests on other platforms.
package winsys

// IsElevated always reports false away from Windows, where full-tunnel mode is
// unavailable anyway.
func IsElevated() bool { return false }
