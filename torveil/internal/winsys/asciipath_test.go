package winsys

import (
	"runtime"
	"testing"
)

func TestIsASCII(t *testing.T) {
	ascii := []string{
		`C:\Users\Andrew\AppData\Roaming\TorVeil\tor`,
		`C:\Users\ANDREY~1\AppData\Roaming\TorVeil\tor`,
		`C:\TorVeil`,
		"",
		"/home/user/.config/torveil",
	}
	for _, s := range ascii {
		if !IsASCII(s) {
			t.Errorf("IsASCII(%q) = false, want true", s)
		}
	}

	// The path from the bug report this exists to prevent: Tor reports
	// "No such file or directory" for a directory that is plainly there,
	// because what reaches the filesystem is the mangled name.
	nonASCII := []string{
		`C:\Users\Андрей\AppData\Roaming\TorVeil\tor`,
		`C:\Users\Müller\AppData`,
		`C:\Users\张伟\AppData`,
		`C:\Users\naïve`,
	}
	for _, s := range nonASCII {
		if IsASCII(s) {
			t.Errorf("IsASCII(%q) = true, want false", s)
		}
	}
}

func TestASCIIPathRejectsEmpty(t *testing.T) {
	if _, err := ASCIIPath(""); err == nil {
		t.Error("an empty path should be refused")
	}
}

func TestASCIIPathPassesThroughASCII(t *testing.T) {
	const in = `C:\TorVeil\tor`
	got, err := ASCIIPath(in)
	if err != nil {
		t.Fatalf("ASCIIPath(%q): %v", in, err)
	}
	// An ASCII path is already usable, so it must come back untouched rather
	// than being rewritten into a short name that is harder to recognise.
	if got != in {
		t.Errorf("ASCIIPath(%q) = %q, want it unchanged", in, got)
	}
}

func TestASCIIPathIsAPassthroughAwayFromWindows(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("Windows resolves short names instead")
	}
	const in = "/home/Андрей/.config/torveil"
	got, err := ASCIIPath(in)
	if err != nil {
		t.Fatalf("ASCIIPath: %v", err)
	}
	if got != in {
		t.Errorf("got %q, want %q — only Tor on Windows has this problem", got, in)
	}
}
