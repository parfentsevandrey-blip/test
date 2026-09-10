//go:build bundled

package bundle

import (
	"os"
	"path/filepath"
	"testing"
)

// TestEmbeddedRuntimeUnpacksIntoTheExpectedLayout checks the archive actually
// carried in this build, not a synthetic one.
//
// The layout is a contract between build/fetch-assets.sh, which builds the
// archive, and internal/tor.Locate, which searches it. Nothing else enforces
// that contract, and getting it wrong produces "Tor was not found" on a build
// that is carrying Tor.
func TestEmbeddedRuntimeUnpacksIntoTheExpectedLayout(t *testing.T) {
	if !Available() {
		t.Skip("build without -tags bundled")
	}

	base := t.TempDir()
	rt, err := Ensure(base, nil)
	if err != nil {
		t.Fatalf("Ensure: %v", err)
	}

	// Paths Locate derives: <root>/tor for the binary, <root>/data for geoip
	// as a sibling, transports under the tor directory.
	required := []string{
		filepath.Join("tor", "tor.exe"),
		filepath.Join("tor", "pluggable_transports", "lyrebird.exe"),
		filepath.Join("tor", "pluggable_transports", "pt_config.json"),
		filepath.Join("data", "geoip"),
		filepath.Join("data", "geoip6"),
		filepath.Join("wintun", "wintun.dll"),
		filepath.Join("wintun", "LICENSE.txt"),
		"VERSIONS.txt",
	}
	for _, rel := range required {
		p := filepath.Join(rt.Dir, rel)
		st, err := os.Stat(p)
		if err != nil {
			t.Errorf("%s is missing from the embedded runtime: %v", rel, err)
			continue
		}
		if st.Size() == 0 {
			t.Errorf("%s was extracted empty", rel)
		}
	}

	if rt.WintunDLL == "" {
		t.Error("WintunDLL was not resolved, so full-tunnel mode could not load the driver")
	}

	// A second call must not unpack again: 70 MB of writes on every launch
	// would be both slow and pointless.
	before, err := os.Stat(filepath.Join(rt.Dir, ".complete"))
	if err != nil {
		t.Fatalf("completion marker missing: %v", err)
	}
	again, err := Ensure(base, nil)
	if err != nil {
		t.Fatalf("second Ensure: %v", err)
	}
	if again.Dir != rt.Dir {
		t.Errorf("second Ensure chose a different directory: %s vs %s", again.Dir, rt.Dir)
	}
	after, err := os.Stat(filepath.Join(rt.Dir, ".complete"))
	if err != nil {
		t.Fatal(err)
	}
	if !after.ModTime().Equal(before.ModTime()) {
		t.Error("the runtime was unpacked a second time")
	}
}

// TestEmbeddedRuntimeIsPlausiblySized guards against an archive that built but
// is missing its binaries.
func TestEmbeddedRuntimeIsPlausiblySized(t *testing.T) {
	if !Available() {
		t.Skip("build without -tags bundled")
	}
	const minimum = 8 << 20 // tor.exe and lyrebird.exe alone exceed this
	if got := len(archive()); got < minimum {
		t.Errorf("embedded runtime is %d bytes, which is too small to contain Tor", got)
	}
}
