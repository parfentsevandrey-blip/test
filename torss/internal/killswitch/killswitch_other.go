//go:build !windows

package killswitch

func Enable(programs []string) error { return nil }
func Disable() error                 { return nil }
