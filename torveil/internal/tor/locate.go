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
	Tor string // tor.exe

	// Snowflake and Obfs4 are the executables implementing each transport.
	//
	// They are usually the same file. Snowflake used to ship as a separate
	// snowflake-client, but current Tor releases fold it into lyrebird
	// alongside obfs4, meek_lite and webtunnel, and a standalone
	// snowflake-client no longer exists in the Expert Bundle. Both spellings
	// are resolved so either layout works.
	Snowflake string
	Obfs4     string

	GeoIP   string // geoip database, needed for country selection
	GeoIPv6 string

	// PTConfig is the pt_config.json shipped beside the transports. It
	// carries the bridge lines the Tor Project currently recommends, which is
	// a far better source than anything compiled into TorVeil: fronting
	// domains and STUN servers get rotated, and a stale list simply fails to
	// bootstrap.
	PTConfig string
}

// ErrTorNotFound reports that no tor executable could be located.
var ErrTorNotFound = errors.New("tor executable not found")

// NotFoundError carries every directory that was searched.
//
// The list matters to whoever has to fix this: without it the obvious guess is
// that TorVeil only looked next to itself, and someone with Tor Browser
// already installed has no way to tell that it was checked and came up empty.
type NotFoundError struct {
	Searched []string
}

func (e *NotFoundError) Error() string {
	return fmt.Sprintf("%v (searched %d locations)", ErrTorNotFound, len(e.Searched))
}

func (e *NotFoundError) Unwrap() error { return ErrTorNotFound }

// SearchedList renders the searched directories one per line, indented.
func (e *NotFoundError) SearchedList() string {
	var b strings.Builder
	for _, r := range e.Searched {
		b.WriteString("    ")
		b.WriteString(r)
		b.WriteString("\n")
	}
	return strings.TrimRight(b.String(), "\n")
}

func exeName(base string) string {
	if runtime.GOOS == "windows" {
		return base + ".exe"
	}
	return base
}

// searchRoots returns the directories scanned for a bundled Tor, most specific
// first. extra comes from configuration and always wins.
func searchRoots(extra []string) []string {
	var roots []string

	// expand covers a directory and the layouts a Tor distribution unpacks
	// into underneath it.
	//
	// A user pointing TorVeil at a folder means "Tor is around here"; they
	// should not have to know whether the Expert Bundle puts tor.exe at the
	// top level (it does not, it uses a "tor" subdirectory) or where Tor
	// Browser hides it. Getting this wrong is invisible: the setting looks
	// applied and the lookup still fails.
	expand := func(dir string) {
		if dir == "" {
			return
		}
		roots = append(roots,
			dir,
			filepath.Join(dir, "tor"), // Tor Expert Bundle
			filepath.Join(dir, "Tor"),
			filepath.Join(dir, "bin"),
			filepath.Join(dir, "Browser", "TorBrowser", "Tor"), // Tor Browser root
		)
	}

	for _, d := range extra {
		expand(d)
	}
	if exe, err := os.Executable(); err == nil {
		expand(filepath.Dir(exe))
	}
	if wd, err := os.Getwd(); err == nil {
		expand(wd)
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
		return b, &NotFoundError{Searched: dedupe(roots)}
	}

	// Pluggable transports normally sit next to tor, or in a sibling
	// PluggableTransports directory in the Tor Browser layout.
	ptRoots := append([]string{
		filepath.Dir(b.Tor),
		filepath.Join(filepath.Dir(b.Tor), "pluggable_transports"),
		filepath.Join(filepath.Dir(filepath.Dir(b.Tor)), "PluggableTransports"),
	}, roots...)

	b.Obfs4 = findIn(ptRoots, exeName("lyrebird"), exeName("obfs4proxy"))

	// Prefer a standalone snowflake-client where one exists, for older
	// installations; otherwise lyrebird provides the transport itself.
	b.Snowflake = findIn(ptRoots, exeName("snowflake-client"))
	if b.Snowflake == "" && strings.EqualFold(filepath.Base(b.Obfs4), exeName("lyrebird")) {
		b.Snowflake = b.Obfs4
	}

	b.PTConfig = findIn(ptRoots, "pt_config.json")

	geoRoots := append(dataRoots(b.Tor), roots...)
	b.GeoIP = findIn(geoRoots, "geoip")
	b.GeoIPv6 = findIn(geoRoots, "geoip6")

	return b, nil
}

// dedupe removes empty and repeated entries while preserving order. The
// search roots overlap by design, and a list that repeats the same path is
// harder to scan than one that does not.
func dedupe(in []string) []string {
	seen := make(map[string]bool, len(in))
	out := make([]string, 0, len(in))
	for _, s := range in {
		if s == "" || seen[s] {
			continue
		}
		seen[s] = true
		out = append(out, s)
	}
	return out
}
