package files

import (
	"bytes"
	"context"
	"crypto/rand"
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/mesh"
	"github.com/parfentsevandrey-blip/test/svoi/internal/meshtest"
	"github.com/parfentsevandrey-blip/test/svoi/internal/store"
)

// newManager builds a Manager for a node with a mutable share list.
func newManager(t *testing.T, n *mesh.Node, shares *[]Share, set *TransferSettings) *Manager {
	t.Helper()
	m := NewManager(Config{Node: n, Shares: func() []Share { return *shares }, Protected: []string{n.Dir()}})
	m.RegisterRPC(n)
	db, err := store.Open(filepath.Join(n.Dir(), "themesh.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { db.Close() })
	if err := m.InitTransfers(db, n.Dir(), func() TransferSettings { return *set }, nil); err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	t.Cleanup(cancel)
	go m.RunTransfers(ctx)
	return m
}

func writeFile(t *testing.T, path string, data []byte) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, data, 0o644); err != nil {
		t.Fatal(err)
	}
}

func TestSandboxPreventsEscapes(t *testing.T) {
	root := t.TempDir()
	shareDir := filepath.Join(root, "share")
	outside := filepath.Join(root, "secret.txt")
	writeFile(t, outside, []byte("TOP SECRET"))
	writeFile(t, filepath.Join(shareDir, "hello.txt"), []byte("hello"))
	writeFile(t, filepath.Join(shareDir, "sub", "deep.txt"), []byte("deep"))
	if err := os.Symlink(outside, filepath.Join(shareDir, "link-out")); err != nil {
		t.Skip("symlinks unavailable:", err)
	}
	if err := os.Symlink(root, filepath.Join(shareDir, "dir-out")); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink("hello.txt", filepath.Join(shareDir, "link-in")); err != nil {
		t.Fatal(err)
	}

	m := NewManager(Config{Shares: func() []Share {
		return []Share{{ID: "s1", Name: "S", Path: shareDir, Mode: "rw", Allow: []string{"*"}}}
	}})
	var actor Actor // local user

	res, err := m.List(actor, "s1", "/")
	if err != nil {
		t.Fatal(err)
	}
	names := map[string]bool{}
	for _, e := range res.Entries {
		names[e.Name] = true
	}
	if !names["hello.txt"] || !names["sub"] || !names["link-in"] {
		t.Fatalf("expected normal entries, got %v", names)
	}
	if names["link-out"] || names["dir-out"] {
		t.Fatalf("symlinks leaving the share must be hidden, got %v", names)
	}
	if res.Entries[0].Name != "sub" || !res.Entries[0].IsDir {
		t.Fatal("directories must be listed first")
	}

	for _, bad := range []string{"../secret.txt", "/../secret.txt", "sub/../../secret.txt", "link-out", "dir-out/secret.txt", "..\\secret.txt", "sub/../../../etc/passwd"} {
		if f, _, err := m.OpenRead(actor, "s1", bad); err == nil {
			data, _ := io.ReadAll(f)
			f.Close()
			if strings.Contains(string(data), "TOP SECRET") {
				t.Fatalf("path %q escaped the sandbox and leaked data", bad)
			}
		}
	}
	if _, err := m.List(actor, "s1", "dir-out"); err == nil {
		t.Fatal("listing through an escaping symlink succeeded")
	}
	// A normal symlink inside the share works.
	f, meta, err := m.OpenRead(actor, "s1", "link-in")
	if err != nil {
		t.Fatalf("symlink within the share should work: %v", err)
	}
	b, _ := io.ReadAll(f)
	f.Close()
	if string(b) != "hello" || meta.Size != 5 {
		t.Fatalf("bad content %q %+v", b, meta)
	}

	// Writes: create, no-overwrite, overwrite, ops.
	pf, err := m.Create(actor, "s1", "sub/new.txt", false)
	if err != nil {
		t.Fatal(err)
	}
	pf.Write([]byte("fresh"))
	if err := pf.Commit(); err != nil {
		t.Fatal(err)
	}
	if got, _ := os.ReadFile(filepath.Join(shareDir, "sub", "new.txt")); string(got) != "fresh" {
		t.Fatalf("file not written: %q", got)
	}
	pf, _ = m.Create(actor, "s1", "sub/new.txt", false)
	pf.Write([]byte("clobber"))
	if err := pf.Commit(); !mesh.IsCode(err, mesh.CodeExists) {
		t.Fatalf("expected exists, got %v", err)
	}
	pf, _ = m.Create(actor, "s1", "sub/new.txt", true)
	pf.Write([]byte("replaced"))
	if err := pf.Commit(); err != nil {
		t.Fatal(err)
	}
	if got, _ := os.ReadFile(filepath.Join(shareDir, "sub", "new.txt")); string(got) != "replaced" {
		t.Fatalf("overwrite failed: %q", got)
	}
	// No temp files left behind.
	ents, _ := os.ReadDir(filepath.Join(shareDir, "sub"))
	for _, e := range ents {
		if strings.HasPrefix(e.Name(), ".themesh-part") {
			t.Fatalf("temp file leaked: %s", e.Name())
		}
	}
	if pf, err := m.Create(actor, "s1", "../evil.txt", true); err == nil {
		pf.Abort() // (an open file would keep Windows from deleting the folder around it)
		// cleaned to "evil.txt" inside the share, which is fine, but must not be outside
		if _, err := os.Stat(filepath.Join(root, "evil.txt")); err == nil {
			t.Fatal("wrote outside the share")
		}
	}
	if err := m.Op(actor, "s1", "mkdir", "photos", ""); err != nil {
		t.Fatal(err)
	}
	if err := m.Op(actor, "s1", "rename", "photos", "pictures"); err != nil {
		t.Fatal(err)
	}
	// "../outside" is cleaned to "outside" *inside* the share: it can never leave it.
	if err := m.Op(actor, "s1", "rename", "pictures", "../outside"); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(filepath.Join(root, "outside")); err == nil {
		t.Fatal("rename escaped the share")
	}
	if _, err := os.Stat(filepath.Join(shareDir, "outside")); err != nil {
		t.Fatal("rename did not land inside the share")
	}
	if err := m.Op(actor, "s1", "delete", "outside", ""); err != nil {
		t.Fatal(err)
	}
	if err := m.Op(actor, "s1", "delete", "/", ""); err == nil {
		t.Fatal("deleting the share root must be refused")
	}
	if err := m.Op(actor, "s1", "mkdir", "bad/../..", ""); err == nil {
		// resolves to the root which exists -> exists/invalid either way
		t.Log("mkdir of '.' refused as expected")
	}
}

func TestShareAccessControl(t *testing.T) {
	dir := t.TempDir()
	writeFile(t, filepath.Join(dir, "a.txt"), []byte("a"))
	allowed, stranger := identity.GenerateDevice().ID, identity.GenerateDevice().ID
	m := NewManager(Config{Shares: func() []Share {
		return []Share{
			{ID: "ro", Name: "RO", Path: dir, Mode: "ro", Allow: []string{allowed.String()}},
			{ID: "open", Name: "Open", Path: dir, Mode: "ro", Allow: []string{"*"}},
		}
	}})
	if len(m.VisibleShares(allowed)) != 2 || len(m.VisibleShares(stranger)) != 1 {
		t.Fatalf("visibility wrong: %v / %v", m.VisibleShares(allowed), m.VisibleShares(stranger))
	}
	if _, err := m.List(stranger, "ro", "/"); !mesh.IsCode(err, mesh.CodeNotFound) {
		t.Fatalf("hidden share must look nonexistent, got %v", err)
	}
	if _, err := m.List(allowed, "ro", "/"); err != nil {
		t.Fatal(err)
	}
	if _, err := m.Create(allowed, "ro", "x.txt", false); !mesh.IsCode(err, mesh.CodeDenied) {
		t.Fatalf("write to a read-only share: %v", err)
	}
	if err := m.Op(allowed, "ro", "delete", "a.txt", ""); !mesh.IsCode(err, mesh.CodeDenied) {
		t.Fatalf("delete in a read-only share: %v", err)
	}
}

func TestSanitizeName(t *testing.T) {
	cases := map[string]string{
		"../../etc/passwd":          "passwd",
		`C:\Windows\system32\x.exe`: "x.exe",
		"a<b>c:d|e?.txt":            "abcde.txt",
		"  ..hidden. ":              "hidden",
		"":                          "file",
		"con.txt":                   "_con.txt",
		"na\x00me\x07.txt":          "name.txt",
		"фото 2024.jpg":             "фото 2024.jpg",
	}
	for in, want := range cases {
		if got := SanitizeName(in); got != want {
			t.Errorf("SanitizeName(%q) = %q, want %q", in, got, want)
		}
	}
	long := SanitizeName(strings.Repeat("я", 300) + ".mp4")
	if len(long) > 200 || !strings.HasSuffix(long, ".mp4") {
		t.Errorf("long name not shortened properly: %d bytes", len(long))
	}
}

func TestRemoteBrowseDownloadUploadAndOps(t *testing.T) {
	h := meshtest.New(t)
	nas := h.Public("nas", "198.51.100.1")
	laptop := h.Public("laptop", "198.51.100.2")
	h.Mesh(nas, laptop)

	dir := t.TempDir()
	payload := make([]byte, 3<<20+123)
	rand.Read(payload)
	writeFile(t, filepath.Join(dir, "movies", "film.bin"), payload)
	writeFile(t, filepath.Join(dir, "notes.txt"), []byte("remember the milk"))
	shares := []Share{
		{ID: "media", Name: "Media", Path: dir, Mode: "rw", Allow: []string{"*"}},
		{ID: "private", Name: "Private", Path: dir, Mode: "ro", Allow: []string{"nobody"}},
	}
	set := &TransferSettings{DownloadDir: t.TempDir(), AutoAccept: "all"}
	nasM := newManager(t, nas, &shares, set)
	lapShares := []Share{}
	lapM := newManager(t, laptop, &lapShares, &TransferSettings{DownloadDir: t.TempDir(), AutoAccept: "all"})
	_ = nasM

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	id := nas.ID()

	got, err := lapM.RemoteShares(ctx, id)
	if err != nil || len(got) != 1 || got[0].ID != "media" || got[0].Mode != "rw" {
		t.Fatalf("remote shares: %v %v", got, err)
	}
	ls, err := lapM.ListAny(ctx, id, "media", "/")
	if err != nil || len(ls.Entries) != 2 || !ls.CanWrite || ls.Entries[0].Name != "movies" {
		t.Fatalf("remote list: %+v %v", ls, err)
	}
	if _, err := lapM.ListAny(ctx, id, "private", "/"); !mesh.IsCode(err, mesh.CodeNotFound) {
		t.Fatalf("forbidden share must be notfound: %v", err)
	}
	if _, err := lapM.ListAny(ctx, id, "media", "/../.."); err != nil {
		// cleaned to root: allowed. The point is it cannot go above.
		t.Fatal(err)
	}

	// Full download.
	rr, err := lapM.RemoteOpen(ctx, id, "media", "/movies/film.bin", 0, 0)
	if err != nil {
		t.Fatal(err)
	}
	all, err := io.ReadAll(rr)
	rr.Close()
	if err != nil || !bytes.Equal(all, payload) {
		t.Fatalf("download mismatch: %d bytes, err=%v", len(all), err)
	}
	// Range download from the middle (what a video player does when seeking).
	ra, err := lapM.OpenAny(ctx, id, "media", "/movies/film.bin")
	if err != nil || ra.Meta.Size != int64(len(payload)) || ra.Meta.Mime != "application/octet-stream" {
		t.Fatalf("OpenAny: %+v %v", ra, err)
	}
	sec, err := ra.Section(1_000_000, 4096)
	if err != nil {
		t.Fatal(err)
	}
	part, _ := io.ReadAll(sec)
	sec.Close()
	if !bytes.Equal(part, payload[1_000_000:1_004_096]) {
		t.Fatal("range read returned wrong bytes")
	}

	// Upload (streamed) and verify on disk; second upload without overwrite must conflict.
	up := make([]byte, 2<<20)
	rand.Read(up)
	n, err := lapM.RemotePut(ctx, id, "media", "/uploaded.bin", bytes.NewReader(up), int64(len(up)), false)
	if err != nil || n != int64(len(up)) {
		t.Fatalf("upload: %d %v", n, err)
	}
	if disk, _ := os.ReadFile(filepath.Join(dir, "uploaded.bin")); !bytes.Equal(disk, up) {
		t.Fatal("uploaded bytes differ on disk")
	}
	if _, err := lapM.RemotePut(ctx, id, "media", "/uploaded.bin", bytes.NewReader(up), int64(len(up)), false); !mesh.IsCode(err, mesh.CodeExists) {
		t.Fatalf("expected exists, got %v", err)
	}
	if _, err := lapM.RemotePut(ctx, id, "media", "/uploaded.bin", bytes.NewReader([]byte("new")), 3, true); err != nil {
		t.Fatal(err)
	}
	// A truncated upload must not leave a partial file under the final name.
	_, err = lapM.RemotePut(ctx, id, "media", "/broken.bin", io.LimitReader(bytes.NewReader(up), 1000), int64(len(up)), false)
	if err == nil {
		t.Fatal("short upload reported success")
	}
	time.Sleep(200 * time.Millisecond)
	if _, err := os.Stat(filepath.Join(dir, "broken.bin")); err == nil {
		t.Fatal("partial upload became visible")
	}

	// Ops.
	if err := lapM.OpAny(ctx, id, "media", "mkdir", "/new-folder", ""); err != nil {
		t.Fatal(err)
	}
	if err := lapM.OpAny(ctx, id, "media", "rename", "/notes.txt", "/new-folder/notes2.txt"); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(filepath.Join(dir, "new-folder", "notes2.txt")); err != nil {
		t.Fatal("rename not applied")
	}
	if err := lapM.OpAny(ctx, id, "media", "delete", "/new-folder", ""); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(filepath.Join(dir, "new-folder")); err == nil {
		t.Fatal("delete not applied")
	}
	if _, err := lapM.ListAny(ctx, id, "missing", "/"); !mesh.IsCode(err, mesh.CodeNotFound) {
		t.Fatalf("missing share: %v", err)
	}
}

func waitTransfer(t *testing.T, m *Manager, id, state string, d time.Duration) Transfer {
	t.Helper()
	var last Transfer
	meshtest.WaitFor(t, d, "transfer "+id+" -> "+state, func() bool {
		tr, ok := m.Transfer(id)
		last = tr
		return ok && tr.State == state
	})
	return last
}

func TestTransferAutoAcceptAndManual(t *testing.T) {
	h := meshtest.New(t)
	phone := h.Public("phone", "198.51.100.1")
	laptop := h.Public("laptop", "198.51.100.2")
	h.Mesh(phone, laptop)

	phoneDL, laptopDL := t.TempDir(), t.TempDir()
	phoneM := newManager(t, phone, &[]Share{}, &TransferSettings{DownloadDir: phoneDL, AutoAccept: "ask"})
	laptopSet := &TransferSettings{DownloadDir: laptopDL, AutoAccept: "all"}
	laptopM := newManager(t, laptop, &[]Share{}, laptopSet)
	_ = phoneM

	data := make([]byte, 5<<20+17)
	rand.Read(data)

	// 1. phone -> laptop, laptop auto-accepts.
	trs, err := phoneM.SendFile([]identity.ID{laptop.ID()}, "../holiday photo.jpg", "image/jpeg", bytes.NewReader(data))
	if err != nil || len(trs) != 1 {
		t.Fatal(err)
	}
	sent := waitTransfer(t, phoneM, trs[0].ID, StateDone, 20*time.Second)
	if sent.Name != "holiday photo.jpg" || sent.Size != int64(len(data)) {
		t.Fatalf("sender view: %+v", sent)
	}
	var recv Transfer
	for _, tr := range laptopM.Transfers() {
		recv = tr
	}
	recv = waitTransfer(t, laptopM, recv.ID, StateDone, 20*time.Second)
	got, err := os.ReadFile(recv.Path)
	if err != nil || !bytes.Equal(got, data) {
		t.Fatalf("received file differs (err=%v, %d bytes)", err, len(got))
	}
	if filepath.Dir(recv.Path) != laptopDL || filepath.Base(recv.Path) != "holiday photo.jpg" {
		t.Fatalf("file landed at %s", recv.Path)
	}
	// Staged copy is cleaned up after completion.
	meshtest.WaitFor(t, 5*time.Second, "outbox cleaned", func() bool {
		ents, _ := os.ReadDir(filepath.Join(phone.Dir(), "outbox"))
		return len(ents) == 0
	})

	// 2. laptop -> phone, phone asks first: offered -> accept -> done. Name collision gets a suffix.
	writeFile(t, filepath.Join(phoneDL, "report.pdf"), []byte("existing"))
	src := filepath.Join(t.TempDir(), "report.pdf")
	writeFile(t, src, []byte("%PDF fake report"))
	trs, err = laptopM.SendPath([]identity.ID{phone.ID()}, src)
	if err != nil {
		t.Fatal(err)
	}
	waitTransfer(t, laptopM, trs[0].ID, StateOffered, 10*time.Second)
	var offer Transfer
	meshtest.WaitFor(t, 5*time.Second, "offer arrives", func() bool {
		for _, tr := range phoneM.Transfers() {
			if tr.Dir == "in" && tr.State == StateOffered {
				offer = tr
				return true
			}
		}
		return false
	})
	if phoneM.PendingOffers() != 1 {
		t.Fatalf("pending offers = %d", phoneM.PendingOffers())
	}
	if _, err := phoneM.Accept(offer.ID); err != nil {
		t.Fatal(err)
	}
	done := waitTransfer(t, phoneM, offer.ID, StateDone, 15*time.Second)
	if filepath.Base(done.Path) != "report (1).pdf" {
		t.Fatalf("collision not handled: %s", done.Path)
	}
	if b, _ := os.ReadFile(filepath.Join(phoneDL, "report.pdf")); string(b) != "existing" {
		t.Fatal("existing file was overwritten")
	}
	waitTransfer(t, laptopM, trs[0].ID, StateDone, 10*time.Second)
	if _, err := os.Stat(src); err != nil {
		t.Fatal("SendPath must not touch the source file")
	}

	// 3. Decline.
	src2 := filepath.Join(t.TempDir(), "spam.zip")
	writeFile(t, src2, []byte("zip"))
	trs, _ = laptopM.SendPath([]identity.ID{phone.ID()}, src2)
	meshtest.WaitFor(t, 5*time.Second, "second offer", func() bool { return phoneM.PendingOffers() == 1 })
	for _, tr := range phoneM.Transfers() {
		if tr.State == StateOffered {
			if _, err := phoneM.Decline(tr.ID); err != nil {
				t.Fatal(err)
			}
		}
	}
	waitTransfer(t, laptopM, trs[0].ID, StateDeclined, 10*time.Second)

	// History management.
	if err := phoneM.Remove(offer.ID); err != nil {
		t.Fatal(err)
	}
	if _, ok := phoneM.Transfer(offer.ID); ok {
		t.Fatal("removed transfer still listed")
	}
	if err := laptopM.Remove("nope"); !mesh.IsCode(err, mesh.CodeNotFound) {
		t.Fatalf("removing a missing transfer: %v", err)
	}
}

func TestTransferResumesPartialDownload(t *testing.T) {
	h := meshtest.New(t)
	a := h.Public("a", "198.51.100.1")
	b := h.Public("b", "198.51.100.2")
	h.Mesh(a, b)
	aM := newManager(t, a, &[]Share{}, &TransferSettings{DownloadDir: t.TempDir(), AutoAccept: "all"})
	bDL := t.TempDir()
	bSet := &TransferSettings{DownloadDir: bDL, AutoAccept: "ask"}
	bM := newManager(t, b, &[]Share{}, bSet)

	data := make([]byte, 4<<20)
	rand.Read(data)
	src := filepath.Join(t.TempDir(), "big.bin")
	writeFile(t, src, data)
	trs, err := aM.SendPath([]identity.ID{b.ID()}, src)
	if err != nil {
		t.Fatal(err)
	}
	var offer Transfer
	meshtest.WaitFor(t, 5*time.Second, "offer", func() bool {
		for _, tr := range bM.Transfers() {
			if tr.State == StateOffered {
				offer = tr
				return true
			}
		}
		return false
	})
	// Simulate an interrupted earlier attempt: a partial file with the first 1.5 MB.
	part := filepath.Join(bDL, ".themesh-"+offer.ID+".part")
	if err := os.WriteFile(part, data[:1_500_000], 0o600); err != nil {
		t.Fatal(err)
	}
	bM.tr().mu.Lock()
	bM.tr().items[offer.ID].Part = part
	bM.tr().mu.Unlock()
	if _, err := bM.Accept(offer.ID); err != nil {
		t.Fatal(err)
	}
	done := waitTransfer(t, bM, offer.ID, StateDone, 20*time.Second)
	got, _ := os.ReadFile(done.Path)
	if !bytes.Equal(got, data) {
		t.Fatal("resumed download produced a corrupt file")
	}
	waitTransfer(t, aM, trs[0].ID, StateDone, 10*time.Second)
}

func TestTransferToOfflinePeerWaits(t *testing.T) {
	h := meshtest.New(t)
	a := h.Public("a", "198.51.100.1")
	b := h.Public("b", "198.51.100.2")
	h.Mesh(a, b)
	aM := newManager(t, a, &[]Share{}, &TransferSettings{DownloadDir: t.TempDir()})

	// b is switched off.
	if err := b.Close(); err != nil {
		t.Fatal(err)
	}
	meshtest.WaitFor(t, 15*time.Second, "b offline", func() bool { return !a.Peer(b.ID()).Online() })
	trs, err := aM.SendFile([]identity.ID{b.ID()}, "later.txt", "text/plain", strings.NewReader("delivered eventually"))
	if err != nil {
		t.Fatal(err)
	}
	time.Sleep(1500 * time.Millisecond)
	if tr, _ := aM.Transfer(trs[0].ID); tr.State != StateQueued {
		t.Fatalf("expected queued while the recipient is offline, got %s", tr.State)
	}

	// b comes back and auto-accepts: the file arrives by itself.
	bDL := t.TempDir()
	b2 := h.Restart(b)
	bM := newManager(t, b2, &[]Share{}, &TransferSettings{DownloadDir: bDL, AutoAccept: "all"})
	waitTransfer(t, aM, trs[0].ID, StateDone, 40*time.Second)
	var got Transfer
	for _, tr := range bM.Transfers() {
		got = tr
	}
	got = waitTransfer(t, bM, got.ID, StateDone, 10*time.Second)
	if data, _ := os.ReadFile(got.Path); string(data) != "delivered eventually" {
		t.Fatalf("content %q", data)
	}
}

func TestCancelAndRetry(t *testing.T) {
	h := meshtest.New(t)
	a := h.Public("a", "198.51.100.1")
	b := h.Public("b", "198.51.100.2")
	h.Mesh(a, b)
	aM := newManager(t, a, &[]Share{}, &TransferSettings{DownloadDir: t.TempDir()})
	bM := newManager(t, b, &[]Share{}, &TransferSettings{DownloadDir: t.TempDir(), AutoAccept: "ask"})

	trs, _ := aM.SendFile([]identity.ID{b.ID()}, "x.txt", "", strings.NewReader("hello"))
	waitTransfer(t, aM, trs[0].ID, StateOffered, 10*time.Second)
	if _, err := aM.Cancel(trs[0].ID); err != nil {
		t.Fatal(err)
	}
	meshtest.WaitFor(t, 5*time.Second, "recipient sees cancel", func() bool {
		for _, tr := range bM.Transfers() {
			if tr.State == StateCanceled {
				return true
			}
		}
		return false
	})
	if _, err := aM.Cancel(trs[0].ID); err == nil {
		t.Fatal("canceling a finished transfer should fail")
	}
}

// replaceLinkUnder makes the node with the lower ID dial the other again: that dial wins at both ends (see
// mesh.keepNewConn), the link in use is closed as superseded - what happens by itself when two devices dial
// each other at the same moment, and what cut a download of the test above short on a slow machine.
func replaceLinkUnder(t *testing.T, ctx context.Context, a, b *mesh.Node) {
	t.Helper()
	ida, idb := a.ID(), b.ID()
	lower, upper := a, b
	if bytes.Compare(idb[:], ida[:]) < 0 {
		lower, upper = b, a
	}
	if err := lower.ReplaceLink(ctx, upper.ID()); err != nil {
		t.Fatalf("replace the link: %v", err)
	}
}

func TestADownloadGoesOnWhenItsLinkIsReplaced(t *testing.T) {
	h := meshtest.New(t)
	nas := h.Public("nas", "198.51.100.1")
	laptop := h.Public("laptop", "198.51.100.2")
	h.Mesh(nas, laptop)

	dir := t.TempDir()
	payload := make([]byte, 32<<20+5) // more than any window the link has, so most of it is still on the way
	rand.Read(payload)
	writeFile(t, filepath.Join(dir, "film.bin"), payload)
	shares := []Share{{ID: "media", Name: "Media", Path: dir, Mode: "ro", Allow: []string{"*"}}}
	newManager(t, nas, &shares, &TransferSettings{DownloadDir: t.TempDir(), AutoAccept: "all"})
	lapShares := []Share{}
	lapM := newManager(t, laptop, &lapShares, &TransferSettings{DownloadDir: t.TempDir(), AutoAccept: "all"})

	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()
	rr, err := lapM.RemoteOpen(ctx, nas.ID(), "media", "/film.bin", 0, 0)
	if err != nil {
		t.Fatal(err)
	}
	defer rr.Close()
	got := make([]byte, 256<<10)
	if _, err := io.ReadFull(rr, got); err != nil {
		t.Fatal(err)
	}

	replaceLinkUnder(t, ctx, nas, laptop) // under the download, which is far from done

	rest, err := io.ReadAll(rr)
	got = append(got, rest...)
	if err != nil || !bytes.Equal(got, payload) {
		t.Fatalf("download mismatch: %d of %d bytes, err=%v", len(got), len(payload), err)
	}
	if rr.resumes == 0 {
		t.Fatal("the link was not replaced under the download, so this test proved nothing")
	}
	t.Logf("the download went on over the new link %d time(s)", rr.resumes)
}

func TestARangeReadGoesOnWhenItsLinkIsReplaced(t *testing.T) {
	h := meshtest.New(t)
	nas := h.Public("nas", "198.51.100.1")
	laptop := h.Public("laptop", "198.51.100.2")
	h.Mesh(nas, laptop)

	dir := t.TempDir()
	payload := make([]byte, 24<<20)
	rand.Read(payload)
	writeFile(t, filepath.Join(dir, "film.bin"), payload)
	shares := []Share{{ID: "media", Name: "Media", Path: dir, Mode: "ro", Allow: []string{"*"}}}
	newManager(t, nas, &shares, &TransferSettings{DownloadDir: t.TempDir(), AutoAccept: "all"})
	lapShares := []Share{}
	lapM := newManager(t, laptop, &lapShares, &TransferSettings{DownloadDir: t.TempDir(), AutoAccept: "all"})

	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()
	// what a video player asks for after a seek: bytes from the middle, a bounded range
	const from, count = 5 << 20, 16 << 20
	rr, err := lapM.RemoteOpen(ctx, nas.ID(), "media", "/film.bin", from, count)
	if err != nil {
		t.Fatal(err)
	}
	defer rr.Close()
	got := make([]byte, 128<<10)
	if _, err := io.ReadFull(rr, got); err != nil {
		t.Fatal(err)
	}
	replaceLinkUnder(t, ctx, nas, laptop)
	rest, err := io.ReadAll(rr)
	got = append(got, rest...)
	if err != nil || !bytes.Equal(got, payload[from:from+count]) {
		t.Fatalf("range mismatch: %d of %d bytes, err=%v", len(got), count, err)
	}
	if rr.resumes == 0 {
		t.Fatal("the link was not replaced under the read, so this test proved nothing")
	}
}

func TestADownloadDoesNotGoOnWithAnotherFile(t *testing.T) {
	h := meshtest.New(t)
	nas := h.Public("nas", "198.51.100.1")
	laptop := h.Public("laptop", "198.51.100.2")
	h.Mesh(nas, laptop)

	dir := t.TempDir()
	payload := make([]byte, 32<<20)
	rand.Read(payload)
	path := filepath.Join(dir, "film.bin")
	writeFile(t, path, payload)
	shares := []Share{{ID: "media", Name: "Media", Path: dir, Mode: "ro", Allow: []string{"*"}}}
	newManager(t, nas, &shares, &TransferSettings{DownloadDir: t.TempDir(), AutoAccept: "all"})
	lapShares := []Share{}
	lapM := newManager(t, laptop, &lapShares, &TransferSettings{DownloadDir: t.TempDir(), AutoAccept: "all"})

	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()
	rr, err := lapM.RemoteOpen(ctx, nas.ID(), "media", "/film.bin", 0, 0)
	if err != nil {
		t.Fatal(err)
	}
	defer rr.Close()
	got := make([]byte, 256<<10)
	if _, err := io.ReadFull(rr, got); err != nil {
		t.Fatal(err)
	}
	// the file is replaced by another one of another size, then the link is replaced
	other := make([]byte, 20<<20)
	rand.Read(other)
	writeFile(t, path, other)
	replaceLinkUnder(t, ctx, nas, laptop)

	rest, err := io.ReadAll(rr)
	if err == nil {
		t.Fatalf("the rest of another file was read as the rest of the first: %d bytes", len(rest))
	}
	if !mesh.IsReplaced(err) {
		t.Fatalf("the reader should report what really happened to its link, got %v", err)
	}
}
