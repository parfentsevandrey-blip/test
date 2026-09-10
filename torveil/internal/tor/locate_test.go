package tor

import (
	"errors"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
)

// TestSearchRootsExpandsConfiguredDirectory guards the distinction between
// "the folder holding tor.exe" and "the folder I unpacked Tor into". Only the
// second is what a user naturally puts in the settings field, and the Expert
// Bundle puts the binary one level down from it.
func TestSearchRootsExpandsConfiguredDirectory(t *testing.T) {
	const configured = "/opt/tor-bundle"
	roots := searchRoots([]string{configured})

	has := func(p string) bool {
		for _, r := range roots {
			if r == p {
				return true
			}
		}
		return false
	}

	for _, want := range []string{
		configured,
		filepath.Join(configured, "tor"), // Expert Bundle
		filepath.Join(configured, "Browser", "TorBrowser", "Tor"), // Tor Browser root
	} {
		if !has(want) {
			t.Errorf("configured directory did not expand to %q\nsearched: %v", want, roots)
		}
	}
	if roots[0] != configured {
		t.Errorf("configured directory should come first, got %q", roots[0])
	}
}

func TestNotFoundErrorCarriesSearchedPaths(t *testing.T) {
	err := error(&NotFoundError{Searched: []string{`C:\a`, `C:\b`, `C:\c`}})

	if !errors.Is(err, ErrTorNotFound) {
		t.Errorf("error does not unwrap to ErrTorNotFound: %v", err)
	}

	var notFound *NotFoundError
	if !errors.As(err, &notFound) {
		t.Fatalf("errors.As failed for %T", err)
	}
	if !strings.Contains(err.Error(), "3") {
		t.Errorf("summary should say how many locations were checked: %q", err.Error())
	}

	list := notFound.SearchedList()
	if lines := strings.Split(list, "\n"); len(lines) != 3 {
		t.Errorf("SearchedList should print one path per line, got %d:\n%s", len(lines), list)
	}
	if strings.HasSuffix(list, "\n") {
		t.Error("SearchedList should not end with a blank line")
	}
}

func TestDedupePreservesOrderAndDropsBlanks(t *testing.T) {
	got := dedupe([]string{"a", "", "b", "a", "c", "b", ""})
	want := []string{"a", "b", "c"}
	if strings.Join(got, ",") != strings.Join(want, ",") {
		t.Errorf("dedupe = %v, want %v", got, want)
	}
}

func TestLocateFindsAnExpertBundleLayout(t *testing.T) {
	// Recreate the layout of an extracted Tor Expert Bundle, which is what the
	// error message tells users to produce.
	root := t.TempDir()
	torDir := filepath.Join(root, "tor")
	ptDir := filepath.Join(torDir, "pluggable_transports")
	dataDir := filepath.Join(root, "data")
	for _, d := range []string{torDir, ptDir, dataDir} {
		if err := os.MkdirAll(d, 0o755); err != nil {
			t.Fatal(err)
		}
	}
	write := func(path string) {
		if err := os.WriteFile(path, []byte("stub"), 0o755); err != nil {
			t.Fatal(err)
		}
	}
	write(filepath.Join(torDir, exeName("tor")))
	write(filepath.Join(ptDir, exeName("snowflake-client")))
	write(filepath.Join(ptDir, exeName("lyrebird")))
	write(filepath.Join(dataDir, "geoip"))
	write(filepath.Join(dataDir, "geoip6"))

	// searchRoots derives "<dir>/tor" from the configured directory, which is
	// what makes extracting the bundle beside the executable work.
	bins, err := Locate([]string{root})
	if err != nil {
		t.Fatalf("Locate: %v", err)
	}

	if filepath.Base(bins.Tor) != exeName("tor") {
		t.Errorf("tor = %q", bins.Tor)
	}
	if bins.Snowflake == "" {
		t.Error("snowflake-client was not found in tor/pluggable_transports")
	}
	if bins.Obfs4 == "" {
		t.Error("lyrebird was not found in tor/pluggable_transports")
	}
	// Without geoip there is no country selection, and the bundle puts it in a
	// sibling of the tor directory rather than inside it.
	if bins.GeoIP == "" || bins.GeoIPv6 == "" {
		t.Errorf("geoip databases not found: %q / %q", bins.GeoIP, bins.GeoIPv6)
	}
}

func TestExeNameMatchesPlatform(t *testing.T) {
	got := exeName("tor")
	want := "tor"
	if runtime.GOOS == "windows" {
		want = "tor.exe"
	}
	if got != want {
		t.Errorf("exeName(tor) = %q, want %q", got, want)
	}
}
