package tun

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func useHostsFile(t *testing.T, content string) string {
	t.Helper()
	p := filepath.Join(t.TempDir(), "hosts")
	if err := os.WriteFile(p, []byte(content), 0o644); err != nil {
		t.Fatal(err)
	}
	old := hostsPath
	hostsPath = p
	t.Cleanup(func() { hostsPath = old })
	// an old timestamp, so that any rewrite is visible
	past := time.Now().Add(-48 * time.Hour)
	if err := os.Chtimes(p, past, past); err != nil {
		t.Fatal(err)
	}
	return p
}

func readFile(t *testing.T, p string) string {
	t.Helper()
	b, err := os.ReadFile(p)
	if err != nil {
		t.Fatal(err)
	}
	return string(b)
}

func untouched(t *testing.T, p string) bool {
	t.Helper()
	fi, err := os.Stat(p)
	if err != nil {
		t.Fatal(err)
	}
	return time.Since(fi.ModTime()) > time.Hour
}

// A machine that never used the feature must never have /etc/hosts rewritten
// (the old code "tidied" the file on every start, even with the feature off).
func TestHostsFileIsLeftAloneWhenThereIsNothingOfOurs(t *testing.T) {
	orig := "127.0.0.1 localhost\r\n::1 ip6-localhost\n\n\n   \n10.0.0.1 router"
	p := useHostsFile(t, orig)
	if err := writeHosts(nil); err != nil {
		t.Fatal(err)
	}
	if readFile(t, p) != orig || !untouched(t, p) {
		t.Fatal("/etc/hosts was rewritten although there was no svoi block to remove")
	}
}

func TestHostsBlockIsAddedReplacedAndRemoved(t *testing.T) {
	orig := "127.0.0.1 localhost\n::1 ip6-localhost\n\n"
	p := useHostsFile(t, orig)
	nas := hostEntry{"100.64.0.2", "nas.svoi"}
	laptop := hostEntry{"100.64.0.3", "laptop.svoi"}

	if err := writeHosts([]hostEntry{nas, laptop}); err != nil {
		t.Fatal(err)
	}
	got := readFile(t, p)
	if !strings.HasPrefix(got, orig) {
		t.Fatalf("the original lines were disturbed:\n%q", got)
	}
	want := orig + "# BEGIN svoi\n100.64.0.3 laptop.svoi\n100.64.0.2 nas.svoi\n# END svoi\n"
	if got != want {
		t.Fatalf("block:\n%q\nwant\n%q", got, want)
	}

	// The same entries again: no write at all.
	past := time.Now().Add(-48 * time.Hour)
	os.Chtimes(p, past, past)
	if err := writeHosts([]hostEntry{laptop, nas}); err != nil {
		t.Fatal(err)
	}
	if !untouched(t, p) || readFile(t, p) != want {
		t.Fatal("an unchanged block was rewritten")
	}

	// Different entries replace the block, they do not pile up.
	if err := writeHosts([]hostEntry{nas}); err != nil {
		t.Fatal(err)
	}
	if got := readFile(t, p); got != orig+"# BEGIN svoi\n100.64.0.2 nas.svoi\n# END svoi\n" {
		t.Fatalf("replaced block:\n%q", got)
	}

	// Removing it gives back exactly what was there.
	if err := writeHosts(nil); err != nil {
		t.Fatal(err)
	}
	if got := readFile(t, p); got != orig {
		t.Fatalf("after removal:\n%q\nwant\n%q", got, orig)
	}
}

func TestHostsBlockWorksWhenTheFileHasNoFinalNewline(t *testing.T) {
	p := useHostsFile(t, "127.0.0.1 localhost")
	if err := writeHosts([]hostEntry{{"100.64.0.2", "nas.svoi"}}); err != nil {
		t.Fatal(err)
	}
	if got := readFile(t, p); got != "127.0.0.1 localhost\n# BEGIN svoi\n100.64.0.2 nas.svoi\n# END svoi\n" {
		t.Fatalf("%q", got)
	}
}
