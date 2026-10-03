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

// A marker that has lost its partner (a write cut short by a crash, an editor that
// removed the END line, a second BEGIN, a stray END) must never cost the user a line
// of their own.
func TestHostsMarkersWithoutPartnersNeverEatUserLines(t *testing.T) {
	nas := hostEntry{"100.64.0.2", "nas.svoi"}
	block := "# BEGIN svoi\n100.64.0.2 nas.svoi\n# END svoi\n"
	for name, c := range map[string]struct{ orig, want string }{
		"BEGIN without END": {
			"127.0.0.1 localhost\n# BEGIN svoi\n100.64.0.9 old.svoi\n10.1.1.1 printer.lan\n192.168.0.9 build-server\n",
			"127.0.0.1 localhost\n10.1.1.1 printer.lan\n192.168.0.9 build-server\n" + block,
		},
		"BEGIN without END, nothing after it": {
			"127.0.0.1 localhost\n# BEGIN svoi\n100.64.0.9 old.svoi\n",
			"127.0.0.1 localhost\n" + block,
		},
		"BEGIN without END at the very end": {
			"127.0.0.1 localhost\n# BEGIN svoi\n",
			"127.0.0.1 localhost\n" + block,
		},
		"END without BEGIN": {
			"127.0.0.1 localhost\n10.1.1.1 printer.lan\n# END svoi\n192.168.0.9 build-server\n",
			"127.0.0.1 localhost\n10.1.1.1 printer.lan\n192.168.0.9 build-server\n" + block,
		},
		"two BEGINs": {
			"127.0.0.1 localhost\n# BEGIN svoi\n100.64.0.9 old.svoi\n10.1.1.1 printer.lan\n# BEGIN svoi\n100.64.0.8 older.svoi\n# END svoi\n192.168.0.9 build-server\n",
			"127.0.0.1 localhost\n10.1.1.1 printer.lan\n192.168.0.9 build-server\n" + block,
		},
		"a user's line inside a whole block": {
			"127.0.0.1 localhost\n# BEGIN svoi\n100.64.0.9 old.svoi\n10.1.1.1 printer.lan\n100.64.0.8 older.svoi\n# END svoi\n192.168.0.9 build-server\n",
			"127.0.0.1 localhost\n10.1.1.1 printer.lan\n192.168.0.9 build-server\n" + block,
		},
		"windows line endings": {
			"127.0.0.1 localhost\r\n# BEGIN svoi\r\n100.64.0.9 old.svoi\r\n# END svoi\r\n10.1.1.1 printer.lan\r\n",
			"127.0.0.1 localhost\r\n10.1.1.1 printer.lan\r\n" + block,
		},
		"a svoi-looking line outside the block is the user's": {
			"127.0.0.1 localhost\n100.64.0.9 mine.svoi\n",
			"127.0.0.1 localhost\n100.64.0.9 mine.svoi\n" + block,
		},
	} {
		p := useHostsFile(t, c.orig)
		if err := writeHosts([]hostEntry{nas}); err != nil {
			t.Fatalf("%s: %v", name, err)
		}
		if got := readFile(t, p); got != c.want {
			t.Errorf("%s:\n got %q\nwant %q", name, got, c.want)
		}
		// and taking the block away leaves only what was never ours
		if err := writeHosts(nil); err != nil {
			t.Fatalf("%s: %v", name, err)
		}
		if got := readFile(t, p); strings.Contains(got, "svoi\n# END") || strings.Contains(got, "BEGIN svoi") {
			t.Errorf("%s: markers left after removal: %q", name, got)
		}
		if got := readFile(t, p); !strings.Contains(got, "127.0.0.1 localhost") {
			t.Errorf("%s: lost localhost: %q", name, got)
		}
	}
}

func TestOurHostsLine(t *testing.T) {
	for line, want := range map[string]bool{
		"100.64.0.2 nas.svoi\n":     true,
		"100.64.0.2 NAS.SVOI":       true,
		"fd7a:115c::2 nas.svoi\r\n": true,
		"10.0.0.2 nas.svoi\n":       false, // not an overlay address
		"100.64.0.2 nas.example\n":  false,
		"100.64.0.2 nas.svoi alias": false, // more than we write
		"100.64.0.2\n":              false,
		"# 100.64.0.2 nas.svoi\n":   false,
		"\n":                        false,
	} {
		if got := ourHostsLine(line); got != want {
			t.Errorf("ourHostsLine(%q) = %v, want %v", line, got, want)
		}
	}
}
