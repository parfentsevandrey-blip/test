//go:build windows

package tor

import (
	"os/exec"
	"syscall"
)

// hideWindow keeps tor.exe and the pluggable transports from flashing a
// console window on top of the GUI.
func hideWindow(cmd *exec.Cmd) {
	cmd.SysProcAttr = &syscall.SysProcAttr{
		HideWindow:    true,
		CreationFlags: 0x08000000, // CREATE_NO_WINDOW
	}
}
