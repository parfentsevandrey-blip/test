package tor

import (
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
)

// Binaries holds the resolved paths of the external programs the engine drives.
// TorVeil does not vendor these: it locates a Tor Expert Bundle (or Tor
// Browser) installation, so the user keeps control over which signed Tor build
// is executed.
type Binaries struct {
	Tor       string // tor.exe
	Snowflake string // snowflake-client.exe (optional)
	Obfs4     string // lyrebird.exe / obfs4proxy.exe (optional)
	GeoIP     string // geoip database (optional but needed for country policy)
	GeoIPv6   string
}

// ErrTorNotFound reports that no tor executable could be located.
var ErrTorNotFound = errors.New("tor executable not found")

func exeName(base string) string {
	if runtime.GOOS == "windows" {
		return base + ".exe"
	}
	return base
}

// searchRoots returns the directories scanned for a bundled Tor, most specific
// first. extra comes from configuration and always wins.
func searchRoots(extra []string) []string {
	roots := append([]string{}, extra...)

	if exe, err := os.Executable(); err == nil {
		dir := filepath.Dir(exe)
		roots = append(roots,
			dir,
			filepath.Join(dir, "tor"),
			filepath.Join(dir, "bin"),
			filepath.Join(dir, "Tor"),
		)
	}
	if wd, err := os.Getwd(); err == nil {
		roots = append(roots, filepath.Join(wd, "bin"), filepath.Join(wd, "tor"))
	}

	if runtime.GOOS == "windows" {
		for _, env := range []string{"ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA", "APPDATA", "USERPROFILE"} {
			base := os.Getenv(env)
			if base == "" {
				continue
			}
			roots = append(roots,
				filepath.Join(base, "Tor Browser", "Browser", "TorBrowser", "Tor"),
				filepath.Join(base, "Desktop", "Tor Browser", "Browser", "TorBrowser", "Tor"),
				filepath.Join(base, "Tor", "tor"),
				filepath.Join(base, "Tor"),
			)
		}
	} else {
		roots = append(roots, "/usr/bin", "/usr/local/bin", "/usr/sbin")
	}
	return roots
}

// dataRoots returns directories that may hold the geoip databases, derived
// from the directory the tor binary was found in.
func dataRoots(torPath string) []string {
	if torPath == "" {
		return nil
	}
	dir := filepath.Dir(torPath)
	parent := filepath.Dir(dir)
	return []string{
		filepath.Join(dir, "data"),
		dir,
		filepath.Join(parent, "data"),
		filepath.Join(parent, "Data", "Tor"),
		"/usr/share/tor",
	}
}

func findIn(roots []string, names ...string) string {
	seen := make(map[string]bool, len(roots))
	for _, root := range roots {
		if root == "" || seen[root] {
			continue
		}
		seen[root] = true
		for _, name := range names {
			p := filepath.Join(root, name)
			if st, err := os.Stat(p); err == nil && !st.IsDir() {
				if abs, err := filepath.Abs(p); err == nil {
					return abs
				}
				return p
			}
		}
	}
	return ""
}

// Locate resolves the external binaries. extraDirs are searched first so a
// configured path always takes precedence. A missing pluggable transport is
// not an error; a missing tor binary is.
func Locate(extraDirs []string) (Binaries, error) {
	var b Binaries
	roots := searchRoots(extraDirs)

	b.Tor = findIn(roots, exeName("tor"))
	if b.Tor == "" {
		if p, err := exec.LookPath(exeName("tor")); err == nil {
			b.Tor = p
		}
	}
	if b.Tor == "" {
		return b, fmt.Errorf("%w: searched %s", ErrTorNotFound, strings.Join(roots[:min(len(roots), 6)], ", "))
	}

	// Pluggable transports normally sit next to tor, or in a sibling
	// PluggableTransports directory in the Tor Browser layout.
	ptRoots := append([]string{
		filepath.Dir(b.Tor),
		filepath.Join(filepath.Dir(b.Tor), "pluggable_transports"),
		filepath.Join(filepath.Dir(filepath.Dir(b.Tor)), "PluggableTransports"),
	}, roots...)

	b.Snowflake = findIn(ptRoots, exeName("snowflake-client"), exeName("client"))
	b.Obfs4 = findIn(ptRoots, exeName("lyrebird"), exeName("obfs4proxy"))

	geoRoots := append(dataRoots(b.Tor), roots...)
	b.GeoIP = findIn(geoRoots, "geoip")
	b.GeoIPv6 = findIn(geoRoots, "geoip6")

	return b, nil
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}
