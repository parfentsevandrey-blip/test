//go:build windows

// Package winutil wraps the few Win32 calls the tray app needs.
package winutil

import (
	"context"
	"errors"
	"fmt"
	"os"
	"strings"
	"syscall"
	"time"
	"unsafe"

	"golang.org/x/sys/windows"

	"torss/internal/proc"
)

var (
	shell32           = windows.NewLazySystemDLL("shell32.dll")
	user32            = windows.NewLazySystemDLL("user32.dll")
	procShellExecuteW = shell32.NewProc("ShellExecuteW")
	procMessageBoxW   = user32.NewProc("MessageBoxW")
)

// IsElevated reports whether we run with administrator rights.
func IsElevated() bool {
	return windows.GetCurrentProcessToken().IsElevated()
}

// RelaunchElevated starts the same executable with the same arguments via the
// UAC prompt and returns once the new process was launched.
func RelaunchElevated() error {
	exe, err := os.Executable()
	if err != nil {
		return err
	}
	var args []string
	for _, a := range os.Args[1:] {
		args = append(args, syscall.EscapeArg(a))
	}
	verb, _ := windows.UTF16PtrFromString("runas")
	file, _ := windows.UTF16PtrFromString(exe)
	params, _ := windows.UTF16PtrFromString(strings.Join(args, " "))
	dir, _ := windows.UTF16PtrFromString(os.Getenv("SystemRoot"))
	const swNormal = 1
	r, _, _ := procShellExecuteW.Call(0, uintptr(unsafe.Pointer(verb)), uintptr(unsafe.Pointer(file)),
		uintptr(unsafe.Pointer(params)), uintptr(unsafe.Pointer(dir)), swNormal)
	if r <= 32 {
		return fmt.Errorf("ShellExecute runas failed (code %d) - UAC declined?", r)
	}
	return nil
}

// OpenPath opens a file or folder with its default application.
func OpenPath(path string) error {
	verb, _ := windows.UTF16PtrFromString("open")
	file, _ := windows.UTF16PtrFromString(path)
	const swNormal = 1
	r, _, _ := procShellExecuteW.Call(0, uintptr(unsafe.Pointer(verb)), uintptr(unsafe.Pointer(file)), 0, 0, swNormal)
	if r <= 32 {
		return fmt.Errorf("ShellExecute open %s failed (code %d)", path, r)
	}
	return nil
}

// MessageBox shows a modal message box (used because there is no console).
func MessageBox(title, text string, isError bool) {
	t, _ := windows.UTF16PtrFromString(title)
	m, _ := windows.UTF16PtrFromString(text)
	var flags uintptr = 0x40 // MB_ICONINFORMATION
	if isError {
		flags = 0x10 // MB_ICONERROR
	}
	procMessageBoxW.Call(0, uintptr(unsafe.Pointer(m)), uintptr(unsafe.Pointer(t)), flags|0x1000 /*MB_SYSTEMMODAL*/)
}

var instanceMutex windows.Handle

// ErrAlreadyRunning is returned by SingleInstance if another torss is running.
var ErrAlreadyRunning = errors.New("torss is already running")

// SingleInstance acquires a global named mutex for the lifetime of the process.
func SingleInstance(name string) error {
	n, _ := windows.UTF16PtrFromString(`Global\` + name)
	h, err := windows.CreateMutex(nil, false, n)
	if err != nil {
		if errors.Is(err, windows.ERROR_ALREADY_EXISTS) {
			return ErrAlreadyRunning
		}
		return err
	}
	if windows.GetLastError() == windows.ERROR_ALREADY_EXISTS {
		return ErrAlreadyRunning
	}
	instanceMutex = h
	return nil
}

const taskName = "TorSS"

// AutostartEnabled reports whether the logon task exists.
func AutostartEnabled() bool {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	_, err := proc.Run(ctx, "schtasks", "/Query", "/TN", taskName)
	return err == nil
}

// SetAutostart creates or deletes a Task Scheduler entry that starts torss
// at logon with highest privileges (a plain Run key cannot start an elevated
// program without a UAC prompt on every logon).
func SetAutostart(enable bool) error {
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	if !enable {
		_, err := proc.Run(ctx, "schtasks", "/Delete", "/TN", taskName, "/F")
		return err
	}
	exe, err := os.Executable()
	if err != nil {
		return err
	}
	_, err = proc.Run(ctx, "schtasks", "/Create", "/F", "/TN", taskName,
		"/SC", "ONLOGON", "/RL", "HIGHEST", "/DELAY", "0000:15",
		"/TR", `"`+exe+`"`)
	return err
}
