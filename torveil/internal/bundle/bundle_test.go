package bundle

import (
	"archive/tar"
	"bytes"
	"compress/gzip"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// buildArchive produces a gzipped tar from a name/content map.
func buildArchive(t *testing.T, files map[string]string) []byte {
	t.Helper()
	var buf bytes.Buffer
	gz := gzip.NewWriter(&buf)
	tw := tar.NewWriter(gz)
	for name, content := range files {
		hdr := &tar.Header{
			Name:     name,
			Mode:     0o700,
			Size:     int64(len(content)),
			Typeflag: tar.TypeReg,
		}
		if err := tw.WriteHeader(hdr); err != nil {
			t.Fatal(err)
		}
		if _, err := tw.Write([]byte(content)); err != nil {
			t.Fatal(err)
		}
	}
	if err := tw.Close(); err != nil {
		t.Fatal(err)
	}
	if err := gz.Close(); err != nil {
		t.Fatal(err)
	}
	return buf.Bytes()
}

func TestExtractWritesTheTree(t *testing.T) {
	dir := t.TempDir()
	data := buildArchive(t, map[string]string{
		"./tor/tor.exe": "binary",
		"./tor/pluggable_transports/lyrebird.exe":   "binary",
		"./tor/pluggable_transports/pt_config.json": "{}",
		"./data/geoip":        "ranges",
		"./wintun/wintun.dll": "dll",
	})

	if err := extract(data, dir); err != nil {
		t.Fatalf("extract: %v", err)
	}
	for _, want := range []string{
		"tor/tor.exe",
		"tor/pluggable_transports/lyrebird.exe",
		"data/geoip",
		"wintun/wintun.dll",
	} {
		if !fileExists(filepath.Join(dir, filepath.FromSlash(want))) {
			t.Errorf("%s was not extracted", want)
		}
	}
}

// TestExtractRefusesPathTraversal matters more than most: this code writes
// executables that are then run, so an archive member escaping the
// destination would be a way to drop a binary anywhere the user can write.
func TestExtractRefusesPathTraversal(t *testing.T) {
	for _, name := range []string{
		"../escaped.exe",
		"./tor/../../escaped.exe",
		`..\escaped.exe`,
	} {
		t.Run(name, func(t *testing.T) {
			dir := t.TempDir()
			err := extract(buildArchive(t, map[string]string{name: "payload"}), dir)
			if err == nil {
				t.Fatalf("extracting %q was allowed", name)
			}
			if !strings.Contains(err.Error(), "escapes") {
				t.Errorf("error should say the member escapes the directory, got: %v", err)
			}
			if fileExists(filepath.Join(filepath.Dir(dir), "escaped.exe")) {
				t.Error("a file was written outside the destination directory")
			}
		})
	}
}

func TestExtractRejectsUnexpectedEntryTypes(t *testing.T) {
	var buf bytes.Buffer
	gz := gzip.NewWriter(&buf)
	tw := tar.NewWriter(gz)
	if err := tw.WriteHeader(&tar.Header{
		Name:     "link",
		Linkname: "/etc/passwd",
		Typeflag: tar.TypeSymlink,
		Mode:     0o777,
	}); err != nil {
		t.Fatal(err)
	}
	tw.Close()
	gz.Close()

	if err := extract(buf.Bytes(), t.TempDir()); err == nil {
		t.Fatal("a symlink member should be rejected")
	}
}

func TestSafeJoin(t *testing.T) {
	base := filepath.Join(string(filepath.Separator), "runtime")

	ok := map[string]string{
		"./tor/tor.exe": filepath.Join(base, "tor", "tor.exe"),
		"data/geoip":    filepath.Join(base, "data", "geoip"),
		".":             base,
	}
	for in, want := range ok {
		got, err := safeJoin(base, in)
		if err != nil {
			t.Errorf("safeJoin(%q): %v", in, err)
			continue
		}
		if got != want {
			t.Errorf("safeJoin(%q) = %q, want %q", in, got, want)
		}
	}

	for _, bad := range []string{"../x", "../../x", "/absolute", `..\x`} {
		if _, err := safeJoin(base, bad); err == nil {
			t.Errorf("safeJoin(%q) should have been refused", bad)
		}
	}
}

func TestEnsureWithoutABundleSaysSo(t *testing.T) {
	if Available() {
		t.Skip("this build embeds a runtime")
	}
	if _, err := Ensure(t.TempDir(), nil); err == nil {
		t.Fatal("expected an error from a build with no embedded runtime")
	}
}

func TestPruneOldVersionsKeepsOnlyTheCurrent(t *testing.T) {
	root := t.TempDir()
	for _, v := range []string{"aaaa", "bbbb", "cccc"} {
		if err := os.MkdirAll(filepath.Join(root, v), 0o700); err != nil {
			t.Fatal(err)
		}
	}
	pruneOldVersions(root, "bbbb", nil)

	entries, err := os.ReadDir(root)
	if err != nil {
		t.Fatal(err)
	}
	if len(entries) != 1 || entries[0].Name() != "bbbb" {
		var names []string
		for _, e := range entries {
			names = append(names, e.Name())
		}
		t.Errorf("kept %v, want only bbbb — each stale runtime is ~70 MB", names)
	}
}
