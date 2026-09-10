//go:build !windows

package winsys

import "fmt"

// IsASCII reports whether every byte of s is in the ASCII range.
func IsASCII(s string) bool {
	for i := 0; i < len(s); i++ {
		if s[i] > 0x7F {
			return false
		}
	}
	return true
}

// ASCIIPath returns dir unchanged. Only Tor on Windows has trouble with
// non-ASCII paths; everywhere else the filesystem and Tor agree on UTF-8.
func ASCIIPath(dir string) (string, error) {
	if dir == "" {
		return "", fmt.Errorf("empty path")
	}
	return dir, nil
}

// DescribeNonASCII is unused away from Windows but keeps the API uniform.
func DescribeNonASCII(dir string, err error) string {
	return fmt.Sprintf("cannot use %s: %v", dir, err)
}
