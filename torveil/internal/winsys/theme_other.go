//go:build !windows

package winsys

// SystemPrefersLight reports the system theme. Away from Windows there is no
// single place to read it from, and the only caller is the Windows window
// background, so it answers dark.
func SystemPrefersLight() bool { return false }
