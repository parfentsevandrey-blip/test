package files

import (
	"context"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/mesh"
	"github.com/parfentsevandrey-blip/test/svoi/internal/meshtest"
)

// The id of an incoming offer is chosen by the sender. It used to become part of
// the partial-download path, so a member could make the victim create, append to
// and delete "*.part" files anywhere (and move them into Downloads).
func TestOfferIDsNeverReachTheFileSystem(t *testing.T) {
	h := meshtest.New(t)
	victim := h.Public("victim", "198.51.100.1")
	mallory := h.Public("mallory", "198.51.100.2")
	h.Mesh(victim, mallory)

	root := t.TempDir()
	dl := filepath.Join(root, "home", "user", "Downloads", "The Mesh")
	bait := filepath.Join(root, "home", "user", ".bashrc.d", "x.part")
	writeFile(t, bait, []byte("# existing\n"))
	set := &TransferSettings{DownloadDir: dl, AutoAccept: "all"}
	vm := newManager(t, victim, &[]Share{}, set)

	// A sender that streams some bytes if (wrongly) asked for the transfer.
	mallory.HandleStream("xfer.get", func(ctx context.Context, c *mesh.Call, s *mesh.ServerStream) error {
		_ = s.Reply(map[string]any{"size": int64(1100)})
		_, _ = s.Write([]byte("echo pwned >/tmp/pwned\n"))
		<-ctx.Done()
		return nil
	})
	p := mallory.Peer(victim.ID())
	for _, id := range []string{
		"/../../../.bashrc.d/x", "../../.bashrc.d/x", "t_../../x", "", "t_", "t_DEADBEEFDEADBEEF", "t_deadbeef", "t_deadbeefdeadbeef00",
		"t_deadbeefdeadbee\x00", "C:\\Windows\\x", strings.Repeat("a", 5000),
	} {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		var resp struct{ State string }
		err := p.Call(ctx, "xfer.offer", map[string]any{"id": id, "name": "cat.jpg", "size": 1100}, &resp)
		cancel()
		if err == nil || !mesh.IsCode(err, mesh.CodeInvalid) {
			t.Errorf("offer with id %q: err=%v resp=%+v, want an invalid-offer error", id, err, resp)
		}
	}
	time.Sleep(500 * time.Millisecond)
	if b, _ := os.ReadFile(bait); string(b) != "# existing\n" {
		t.Fatalf("a file outside the download folder was modified: %q", b)
	}
	if len(vm.Transfers()) != 0 {
		t.Fatalf("rejected offers created transfers: %+v", vm.Transfers())
	}

	// A well-formed offer works, and its partial file has a local random name in the download folder.
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	var resp struct{ State string }
	if err := p.Call(ctx, "xfer.offer", map[string]any{"id": "t_0123456789abcdef", "name": "cat.jpg", "size": 1100}, &resp); err != nil {
		t.Fatal(err)
	}
	meshtest.WaitFor(t, 10*time.Second, "the partial file to appear", func() bool {
		ents, _ := os.ReadDir(dl)
		for _, e := range ents {
			if partNameRe.MatchString(e.Name()) && !strings.Contains(e.Name(), "0123456789abcdef") {
				return true
			}
		}
		return false
	})
	if b, _ := os.ReadFile(bait); string(b) != "# existing\n" {
		t.Fatalf("the bait file changed: %q", b)
	}
}

// What auto-accept judged (the announced size) is what may be received.
func TestAnnouncedSizeIsEnforced(t *testing.T) {
	h := meshtest.New(t)
	victim := h.Public("victim", "198.51.100.1")
	mallory := h.Public("mallory", "198.51.100.2")
	h.Mesh(victim, mallory)
	dl := t.TempDir()
	set := &TransferSettings{DownloadDir: dl, AutoAccept: "all", MaxAutoByte: 1 << 20}
	vm := newManager(t, victim, &[]Share{}, set)

	const big = 24 << 20
	mallory.HandleStream("xfer.get", func(ctx context.Context, c *mesh.Call, s *mesh.ServerStream) error {
		if err := s.Reply(map[string]any{"size": int64(big)}); err != nil {
			return err
		}
		buf := make([]byte, 64<<10)
		for sent := 0; sent < big; sent += len(buf) {
			if _, err := s.Write(buf); err != nil {
				return err
			}
		}
		return nil
	})
	var resp struct{ State string }
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := mallory.Peer(victim.ID()).Call(ctx, "xfer.offer", map[string]any{"id": "t_deadbeefdeadbeef", "name": "tiny.txt", "size": 10}, &resp); err != nil {
		t.Fatal(err)
	}
	var got Transfer
	meshtest.WaitFor(t, 20*time.Second, "the transfer to be refused", func() bool {
		for _, tr := range vm.Transfers() {
			if tr.State == StateFailed {
				got = tr
				return true
			}
			if tr.State == StateDone {
				t.Fatalf("a transfer announced as 10 bytes completed: %+v", tr)
			}
		}
		return false
	})
	if !strings.Contains(got.Error, "size") {
		t.Fatalf("error should name the size change, got %q", got.Error)
	}
	ents, _ := os.ReadDir(dl)
	var total int64
	for _, e := range ents {
		if fi, err := e.Info(); err == nil {
			total += fi.Size()
		}
	}
	if total > 1<<20 {
		t.Fatalf("%d bytes were written for a 10-byte offer", total)
	}
}

func TestReservedWindowsNames(t *testing.T) {
	for _, n := range []string{"COM1", "com5.txt", "LPT9", "lpt7.log", "Con", "NUL.txt", "aux"} {
		if got := SanitizeName(n); !strings.HasPrefix(got, "_") {
			t.Errorf("SanitizeName(%q) = %q, want a leading underscore", n, got)
		}
	}
	for _, n := range []string{"COM", "COM10.txt", "LPT0", "comet.txt", "readme"} {
		if got := SanitizeName(n); strings.HasPrefix(got, "_") {
			t.Errorf("SanitizeName(%q) = %q, should be left alone", n, got)
		}
	}
}

func TestUniquePathTerminates(t *testing.T) {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "a.txt"), nil, 0o644); err != nil {
		t.Fatal(err)
	}
	done := make(chan string, 1)
	go func() { done <- uniquePath(dir, "a.txt") }()
	select {
	case p := <-done:
		if filepath.Base(p) != "a (1).txt" {
			t.Fatalf("got %s", p)
		}
	case <-time.After(5 * time.Second):
		t.Fatal("uniquePath did not return")
	}
}

// If an administrator shared a folder that contains the themesh data folder (the home
// folder, say), any member could read mesh.json and with it the mesh authority key.
func TestSharesCannotExposeTheDataFolder(t *testing.T) {
	h := meshtest.New(t)
	admin := h.Public("admin", "198.51.100.1")
	member := h.Public("member", "198.51.100.2")
	h.Mesh(admin, member)
	home := filepath.Dir(admin.Dir())
	if err := os.WriteFile(filepath.Join(admin.Dir(), "mesh.json"), []byte(`{"auth_seed":"secret"}`), 0o600); err != nil {
		t.Fatal(err)
	}
	writeFile(t, filepath.Join(home, "notes.txt"), []byte("harmless"))
	shares := []Share{{ID: "sh_home", Name: "home", Path: home, Mode: "ro", Allow: []string{"*"}}}
	am := newManager(t, admin, &shares, &TransferSettings{DownloadDir: t.TempDir()})
	mm := newManager(t, member, &[]Share{}, &TransferSettings{DownloadDir: t.TempDir()})

	// Saving such a share is refused outright; an old or hand-edited one is never served.
	if err := am.CheckShareRoot(home); !errors.Is(err, ErrProtected) {
		t.Fatalf("home: %v", err)
	}
	for _, d := range []string{admin.Dir(), filepath.Join(admin.Dir(), "blobs"), filepath.Dir(home)} {
		if err := am.CheckShareRoot(d); !errors.Is(err, ErrProtected) {
			t.Errorf("%s: %v", d, err)
		}
	}
	link := filepath.Join(t.TempDir(), "innocent")
	if err := os.Symlink(admin.Dir(), link); err == nil {
		if err := am.CheckShareRoot(link); !errors.Is(err, ErrProtected) {
			t.Errorf("a symlink to the data folder was accepted: %v", err)
		}
	}
	if err := am.CheckShareRoot(t.TempDir()); err != nil {
		t.Errorf("an unrelated folder is refused: %v", err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	if list, err := mm.RemoteShares(ctx, admin.ID()); err == nil && len(list) != 0 {
		t.Fatalf("a share reaching the data folder is still listed: %+v", list)
	}
	if _, err := mm.RemoteOpen(ctx, admin.ID(), "sh_home", filepath.Base(admin.Dir())+"/mesh.json", 0, 0); err == nil {
		t.Fatal("a member read mesh.json through a share")
	}
	if _, err := mm.RemoteOpen(ctx, admin.ID(), "sh_home", "notes.txt", 0, 0); err == nil {
		t.Fatal("a share that contains the data folder must not be served at all")
	}
}

// A folder that is not there (yet) is still reached through the links of the part that is: the check
// must see the same place however the path is spelled.
func TestRealPathOfAFolderThatDoesNotExistYet(t *testing.T) {
	dir := t.TempDir()
	want := filepath.Join(realPath(dir), "a", "b")
	if got := realPath(filepath.Join(dir, "a", "b")); got != want {
		t.Fatalf("realPath(%s/a/b) = %s, want %s", dir, got, want)
	}
	link := filepath.Join(t.TempDir(), "link")
	if err := os.Symlink(dir, link); err != nil {
		t.Skip("cannot create a symbolic link here:", err)
	}
	if got := realPath(filepath.Join(link, "a", "b")); got != want {
		t.Fatalf("through a link: realPath = %s, want %s", got, want)
	}
}

// A staged copy that is still open when its transfer ends (Windows will not delete an open file) is removed as
// soon as it is closed.
func TestAStagedCopyThatIsStillOpenIsRemovedLater(t *testing.T) {
	stage := filepath.Join(t.TempDir(), "outbox", "b_1")
	file := filepath.Join(stage, "photo.jpg")
	writeFile(t, file, []byte("data"))
	f, err := os.Open(file)
	if err != nil {
		t.Fatal(err)
	}
	removeStage(stage)
	time.Sleep(700 * time.Millisecond)
	f.Close()
	deadline := time.Now().Add(10 * time.Second)
	for {
		if _, err := os.Stat(stage); os.IsNotExist(err) {
			return
		}
		if time.Now().After(deadline) {
			t.Fatal("the staged copy is still there")
		}
		time.Sleep(100 * time.Millisecond)
	}
}

// What nothing needs any more is cleared out of the outbox when the program starts; what an unfinished
// transfer still needs stays.
func TestSweepOutboxKeepsWhatIsStillNeeded(t *testing.T) {
	dir := t.TempDir()
	keep := filepath.Join(dir, "outbox", "b_keep")
	drop := filepath.Join(dir, "outbox", "b_drop")
	stray := filepath.Join(dir, "outbox", "b_stray")
	for _, d := range []string{keep, drop, stray} {
		writeFile(t, filepath.Join(d, "x.bin"), []byte("x"))
	}
	tr := &transfers{dataDir: dir, items: map[string]*record{
		"t_1": {ID: "t_1", Dir: "out", State: StateQueued, Stage: keep},
		"t_2": {ID: "t_2", Dir: "out", State: StateDone, Stage: drop},
	}}
	tr.sweepOutbox()
	if _, err := os.Stat(keep); err != nil {
		t.Fatalf("a staged copy of a queued transfer was removed: %v", err)
	}
	for _, d := range []string{drop, stray} {
		if _, err := os.Stat(d); err == nil {
			t.Fatalf("%s was not swept", d)
		}
	}
}
