//go:build !windows

package tor

import "os/exec"

// hideWindow is a no-op away from Windows; it exists so the engine builds and
// its tests run on the development host.
func hideWindow(*exec.Cmd) {}
