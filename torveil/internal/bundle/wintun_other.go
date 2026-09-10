//go:build !windows

package bundle

import "errors"

// EnsureWintunLoadable is a no-op away from Windows, where there is no Wintun
// and no full-tunnel mode to need it.
func EnsureWintunLoadable(string) error {
	return errors.New("wintun is only used on Windows")
}
