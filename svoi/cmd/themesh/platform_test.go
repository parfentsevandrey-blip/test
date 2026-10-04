package main

import "testing"

// The phone app runs a Linux build on Android and tells the program what it really is.
func TestPlatformFromTheEnvironment(t *testing.T) {
	for in, want := range map[string]string{
		"android/arm64":         "android/arm64",
		" Android/ARM64 ":       "android/arm64", // case and blanks do not matter
		"android":               "android",
		"android/x86_64":        "android/x86_64",
		"":                      "",
		"linux/arm64/extra":     "",
		"android/":              "",
		"/arm64":                "",
		"a":                     "",
		"android arm64":         "",
		"android/arm64\nevil":   "",
		"../../etc/passwd":      "",
		"androidandroidandroid": "", // longer than any real name
	} {
		t.Setenv("THEMESH_PLATFORM", in)
		if got := platformFromEnv(); got != want {
			t.Errorf("THEMESH_PLATFORM=%q: platform %q, want %q", in, got, want)
		}
	}
}
