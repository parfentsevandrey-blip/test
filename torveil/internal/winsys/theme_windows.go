//go:build windows

package winsys

import "golang.org/x/sys/windows/registry"

// SystemPrefersLight reports whether Windows is set to the light theme for
// applications.
//
// It is read only to pick the colour the window is painted before the page
// loads; the page then resolves the theme itself from prefers-color-scheme and
// stays authoritative. Getting this wrong costs a dark flash on launch, not
// correctness, so any failure to read the setting falls back to dark.
func SystemPrefersLight() bool {
	key, err := registry.OpenKey(registry.CURRENT_USER,
		`Software\Microsoft\Windows\CurrentVersion\Themes\Personalize`,
		registry.QUERY_VALUE)
	if err != nil {
		return false
	}
	defer key.Close()

	v, _, err := key.GetIntegerValue("AppsUseLightTheme")
	if err != nil {
		return false
	}
	return v == 1
}
