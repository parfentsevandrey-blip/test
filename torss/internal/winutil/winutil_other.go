//go:build !windows

package winutil

import (
	"errors"
	"fmt"
	"os"
)

var ErrAlreadyRunning = errors.New("torss is already running")

func IsElevated() bool           { return os.Geteuid() == 0 }
func RelaunchElevated() error    { return errors.New("elevation is only implemented on Windows") }
func OpenPath(path string) error { fmt.Println("open:", path); return nil }
func MessageBox(title, text string, isError bool) {
	fmt.Fprintf(os.Stderr, "[%s] %s\n", title, text)
}
func SingleInstance(name string) error { return nil }
func AutostartEnabled() bool           { return false }
func SetAutostart(enable bool) error   { return errors.New("autostart is only implemented on Windows") }
