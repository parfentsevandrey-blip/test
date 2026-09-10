//go:build windows

package winsys

import (
	"fmt"
	"os"
	"strings"

	"golang.org/x/sys/windows"
)

// IsASCII reports whether every byte of s is in the ASCII range.
func IsASCII(s string) bool {
	for i := 0; i < len(s); i++ {
		if s[i] > 0x7F {
			return false
		}
	}
	return true
}

// ASCIIPath returns a form of dir that contains only ASCII characters,
// creating the directory if it does not exist.
//
// Tor cannot open a path containing non-ASCII characters on Windows. A user
// account named in Cyrillic, Greek or any non-Latin script puts every
// per-user directory out of its reach, and the failure is opaque: Tor
// reports "No such file or directory" for a directory that plainly exists,
// because what reaches the filesystem is the mangled remains of the name.
//
// Windows keeps an 8.3 short name for such paths, which is ASCII by
// construction — C:\Users\Андрей becomes something like C:\Users\ANDREY~1 —
// and both names refer to the same directory. The short name only exists if
// the volume generates them, which the system volume does by default; when it
// does not, this reports the problem rather than handing Tor a path it will
// choke on.
func ASCIIPath(dir string) (string, error) {
	if dir == "" {
		return "", fmt.Errorf("empty path")
	}
	if IsASCII(dir) {
		return dir, nil
	}

	// GetShortPathName resolves against the filesystem, so the directory has
	// to exist before there is a short name to ask for.
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return "", fmt.Errorf("create %s: %w", dir, err)
	}

	short, err := shortPath(dir)
	if err != nil {
		return "", fmt.Errorf("resolve a short name for %s: %w", dir, err)
	}
	if !IsASCII(short) {
		return "", fmt.Errorf("%s has no ASCII short name, which means 8.3 name creation is "+
			"disabled on this volume", dir)
	}
	return short, nil
}

func shortPath(path string) (string, error) {
	long, err := windows.UTF16PtrFromString(path)
	if err != nil {
		return "", err
	}

	n, err := windows.GetShortPathName(long, nil, 0)
	if err != nil {
		return "", err
	}
	buf := make([]uint16, n)
	n, err = windows.GetShortPathName(long, &buf[0], n)
	if err != nil {
		return "", err
	}
	return windows.UTF16ToString(buf[:n]), nil
}

// DescribeNonASCII explains the problem in terms the person reading it can act
// on, naming the offending path.
func DescribeNonASCII(dir string, err error) string {
	var b strings.Builder
	fmt.Fprintf(&b, "Tor cannot use %s because the path contains characters outside ASCII, "+
		"and Windows could not provide a short name for it (%v).\n\n", dir, err)
	b.WriteString("This happens when the Windows account name is not written in Latin letters. " +
		"Set \"Data directory\" in Settings to a path made only of Latin letters and digits, " +
		"for example C:\\TorVeil.")
	return b.String()
}
