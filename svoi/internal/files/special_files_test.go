//go:build unix

package files

import (
	"fmt"
	"os"
	"path/filepath"
	"syscall"
	"testing"
	"time"
)

// A pipe inside a shared folder used to block open(2) until somebody wrote to
// it, so one request from any member pinned a handler and a descriptor for ever.
func TestPipesInsideAShareDoNotHangTheNode(t *testing.T) {
	shareDir := t.TempDir()
	writeFile(t, filepath.Join(shareDir, "ok.txt"), []byte("fine"))
	if err := syscall.Mkfifo(filepath.Join(shareDir, "pipe"), 0o644); err != nil {
		t.Skip("cannot create a FIFO here:", err)
	}
	m := NewManager(Config{Shares: func() []Share {
		return []Share{{ID: "s1", Name: "S", Path: shareDir, Mode: "rw", Allow: []string{"*"}}}
	}})
	var actor Actor
	done := make(chan struct{})
	go func() {
		defer close(done)
		if f, _, err := m.OpenRead(actor, "s1", "pipe"); err == nil {
			f.Close()
			t.Error("a pipe was opened for reading")
		}
		if _, err := m.List(actor, "s1", "pipe"); err == nil {
			t.Error("a pipe was listed as a folder")
		}
		if f, meta, err := m.OpenRead(actor, "s1", "ok.txt"); err != nil || meta.Size != 4 {
			t.Errorf("a normal file stopped working next to a pipe: %v %+v", err, meta)
		} else {
			f.Close()
		}
	}()
	select {
	case <-done:
	case <-time.After(5 * time.Second):
		t.Fatal("opening a pipe inside a share hangs")
	}
}

// One request for a folder with a huge number of files returns a bounded listing.
func TestHugeFolderListingIsBounded(t *testing.T) {
	if testing.Short() {
		t.Skip("creates tens of thousands of files")
	}
	shareDir := t.TempDir()
	for i := 0; i < maxListEntries+50; i++ {
		f, err := os.Create(filepath.Join(shareDir, fmt.Sprintf("f%06d", i)))
		if err != nil {
			t.Fatal(err)
		}
		f.Close()
	}
	m := NewManager(Config{Shares: func() []Share {
		return []Share{{ID: "s1", Name: "S", Path: shareDir, Mode: "ro", Allow: []string{"*"}}}
	}})
	res, err := m.List(Actor{}, "s1", "/")
	if err != nil {
		t.Fatal(err)
	}
	if len(res.Entries) != maxListEntries {
		t.Fatalf("%d entries listed, want the cap of %d", len(res.Entries), maxListEntries)
	}
}
