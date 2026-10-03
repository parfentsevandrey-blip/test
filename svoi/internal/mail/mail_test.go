package mail

import (
	"bytes"
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/blob"
	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/mesh"
	"github.com/parfentsevandrey-blip/test/svoi/internal/meshtest"
	"github.com/parfentsevandrey-blip/test/svoi/internal/store"
)

type box struct {
	node  *mesh.Node
	m     *Manager
	blobs *blob.Store
	db    *store.DB
	dir   string
}

func newBox(t *testing.T, n *mesh.Node) *box {
	t.Helper()
	dir := n.Dir()
	db, err := store.Open(filepath.Join(dir, "svoi.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { db.Close() })
	bs, err := blob.Open(filepath.Join(dir, "blobs"))
	if err != nil {
		t.Fatal(err)
	}
	m, err := New(n, db, bs, nil)
	if err != nil {
		t.Fatal(err)
	}
	m.Register()
	bs.RegisterRPC(n)
	ctx, cancel := context.WithCancel(context.Background())
	t.Cleanup(cancel)
	go m.Run(ctx)
	return &box{node: n, m: m, blobs: bs, db: db, dir: dir}
}

func (b *box) upload(t *testing.T, name string, data []byte) string {
	t.Helper()
	sha, size, err := b.blobs.Put(bytes.NewReader(data))
	if err != nil {
		t.Fatal(err)
	}
	if err := b.m.RegisterUpload(sha, name, "application/octet-stream", size); err != nil {
		t.Fatal(err)
	}
	return sha
}

func TestMailDeliveryAndFlags(t *testing.T) {
	h := meshtest.New(t)
	a := h.Public("alpha", "198.51.100.1")
	b := h.Public("beta", "198.51.100.2")
	h.Mesh(a, b)
	ma, mb := newBox(t, a), newBox(t, b)

	id, err := ma.m.Send(SendInput{Kind: "mail", To: []identity.ID{b.ID()}, Subject: "Backup report", Body: "All good.\nSee you!"})
	if err != nil {
		t.Fatal(err)
	}
	meshtest.WaitFor(t, 10*time.Second, "delivered", func() bool {
		msg, ok := ma.m.Get(id)
		return ok && msg.To[0].State == DelDelivered && msg.To[0].At != nil
	})
	// Sender's copy is in "sent", recipient's in "inbox", unread.
	sent := ma.m.List(FolderSent, "", 50, 0)
	if sent.Total != 1 || sent.Items[0].Subject != "Backup report" {
		t.Fatalf("sent folder: %+v", sent)
	}
	inbox := mb.m.List(FolderInbox, "", 50, 0)
	if inbox.Total != 1 || inbox.Unread != 1 || inbox.Items[0].From.Name != "alpha" || inbox.Items[0].Snippet != "All good. See you!" {
		t.Fatalf("inbox: %+v", inbox)
	}
	if mu, _ := mb.m.Counters(); mu != 1 {
		t.Fatalf("unread counter = %d", mu)
	}
	full, _ := mb.m.Get(id)
	if full.Body != "All good.\nSee you!" || full.Unread != true {
		t.Fatalf("full message: %+v", full)
	}
	// Search.
	if r := mb.m.List(FolderInbox, "backup", 50, 0); r.Total != 1 {
		t.Fatalf("search failed: %+v", r)
	}
	if r := mb.m.List(FolderInbox, "nothing-here", 50, 0); r.Total != 0 {
		t.Fatalf("search false positive: %+v", r)
	}
	// Flags and folders.
	f, tr := false, FolderTrash
	if err := mb.m.SetFlags(id, Flags{Unread: &f}); err != nil {
		t.Fatal(err)
	}
	if mu, _ := mb.m.Counters(); mu != 0 {
		t.Fatalf("counter after read = %d", mu)
	}
	if err := mb.m.SetFlags(id, Flags{Folder: &tr}); err != nil {
		t.Fatal(err)
	}
	if mb.m.List(FolderInbox, "", 50, 0).Total != 0 || mb.m.List(FolderTrash, "", 50, 0).Total != 1 {
		t.Fatal("move to trash failed")
	}
	in := FolderInbox
	mb.m.SetFlags(id, Flags{Folder: &in})
	if mb.m.List(FolderInbox, "", 50, 0).Total != 1 {
		t.Fatal("restore from trash failed")
	}
	mb.m.Delete(id) // -> trash
	mb.m.Delete(id) // -> gone
	if _, ok := mb.m.Get(id); ok {
		t.Fatal("message not deleted for good")
	}
	// Reply threading.
	reply, err := mb.m.Send(SendInput{Kind: "mail", To: []identity.ID{a.ID()}, Subject: "Re: Backup report", Body: "thanks", InReplyTo: id})
	if err != nil {
		t.Fatal(err)
	}
	if r, _ := mb.m.Get(reply); r.InReplyTo == nil || *r.InReplyTo != id {
		t.Fatal("inReplyTo lost")
	}
}

func TestMailQueuedUntilRecipientAppears(t *testing.T) {
	h := meshtest.New(t)
	a := h.Public("alpha", "198.51.100.1")
	b := h.Public("beta", "198.51.100.2")
	h.Mesh(a, b)
	ma := newBox(t, a)

	// beta is switched off.
	if err := b.Close(); err != nil {
		t.Fatal(err)
	}
	meshtest.WaitFor(t, 15*time.Second, "beta offline", func() bool { return !a.Peer(b.ID()).Online() })

	id, err := ma.m.Send(SendInput{Kind: "mail", To: []identity.ID{b.ID()}, Subject: "while you were out", Body: "hello"})
	if err != nil {
		t.Fatal(err)
	}
	time.Sleep(2 * time.Second)
	msg, _ := ma.m.Get(id)
	if msg.To[0].State != DelQueued {
		t.Fatalf("expected queued, got %s", msg.To[0].State)
	}

	// beta is switched back on: the queued message arrives without anyone doing anything.
	b2 := h.Restart(b)
	mb := newBox(t, b2)
	meshtest.WaitFor(t, 30*time.Second, "queued mail delivered after beta returns", func() bool {
		msg, _ := ma.m.Get(id)
		return msg != nil && msg.To[0].State == DelDelivered
	})
	if got := mb.m.List(FolderInbox, "", 50, 0); got.Total != 1 || got.Items[0].Subject != "while you were out" {
		t.Fatalf("beta's inbox: %+v", got)
	}

	// The outbox also survives a restart of the *sender*.
	b2.Close()
	meshtest.WaitFor(t, 15*time.Second, "beta offline again", func() bool { return !a.Peer(b.ID()).Online() })
	id2, _ := ma.m.Send(SendInput{Kind: "mail", To: []identity.ID{b.ID()}, Subject: "second", Body: "x"})
	m2, err := New(a, ma.db, ma.blobs, nil)
	if err != nil {
		t.Fatal(err)
	}
	if got, ok := m2.Get(id2); !ok || got.To[0].State != DelQueued {
		t.Fatal("outbox not persisted")
	}
}

func TestChatFlow(t *testing.T) {
	h := meshtest.New(t)
	a := h.Public("alpha", "198.51.100.1")
	b := h.Public("beta", "198.51.100.2")
	h.Mesh(a, b)
	ma, mb := newBox(t, a), newBox(t, b)

	for i, txt := range []string{"hi", "how are you?", "привет 👋"} {
		if _, err := ma.m.Send(SendInput{Kind: "chat", To: []identity.ID{b.ID()}, Body: txt}); err != nil {
			t.Fatal(err)
		}
		time.Sleep(1100 * time.Millisecond) // distinct timestamps
		_ = i
	}
	meshtest.WaitFor(t, 10*time.Second, "chat delivered", func() bool {
		th := mb.m.ChatThreads()
		return len(th) == 1 && th[0].Unread == 3
	})
	threads := mb.m.ChatThreads()
	if threads[0].Peer.Name != "alpha" || threads[0].Last.Text != "привет 👋" {
		t.Fatalf("threads: %+v", threads)
	}
	msgs := mb.m.ChatMessages(a.ID(), 0, 50)
	if len(msgs) != 3 || msgs[0].Text != "hi" || msgs[2].Text != "привет 👋" || msgs[0].Mine {
		t.Fatalf("messages: %+v", msgs)
	}
	if _, c := mb.m.Counters(); c != 3 {
		t.Fatalf("chat counter %d", c)
	}
	mb.m.ChatRead(a.ID())
	if _, c := mb.m.Counters(); c != 0 {
		t.Fatalf("chat counter after read %d", c)
	}
	// Reply from beta; alpha sees it as not mine, its own as mine with delivered state.
	if _, err := mb.m.Send(SendInput{Kind: "chat", To: []identity.ID{a.ID()}, Body: "fine!"}); err != nil {
		t.Fatal(err)
	}
	meshtest.WaitFor(t, 10*time.Second, "reply", func() bool { return len(ma.m.ChatMessages(b.ID(), 0, 50)) == 4 })
	am := ma.m.ChatMessages(b.ID(), 0, 50)
	if !am[0].Mine || am[3].Mine || am[3].Text != "fine!" {
		t.Fatalf("alpha's view: %+v", am)
	}
	meshtest.WaitFor(t, 10*time.Second, "ticks", func() bool {
		for _, m := range ma.m.ChatMessages(b.ID(), 0, 50) {
			if m.Mine && m.State != DelDelivered {
				return false
			}
		}
		return true
	})
	// Paging.
	if page := ma.m.ChatMessages(b.ID(), am[2].TS, 50); len(page) != 2 {
		t.Fatalf("paging returned %d messages", len(page))
	}
	// Chat to several people is not allowed.
	if _, err := ma.m.Send(SendInput{Kind: "chat", To: []identity.ID{b.ID(), a.ID()}, Body: "x"}); err == nil {
		t.Fatal("multi-recipient chat accepted")
	}
}

func TestAttachmentsAreFetchedAndVerified(t *testing.T) {
	h := meshtest.New(t)
	a := h.Public("alpha", "198.51.100.1")
	b := h.Public("beta", "198.51.100.2")
	h.Mesh(a, b)
	ma, mb := newBox(t, a), newBox(t, b)

	big := make([]byte, 3<<20)
	rand.Read(big)
	sha1 := ma.upload(t, "photo.bin", big)
	sha2 := ma.upload(t, "note.txt", []byte("small attachment"))
	id, err := ma.m.Send(SendInput{Kind: "mail", To: []identity.ID{b.ID()}, Subject: "files", Body: "see attached", Attach: []string{sha1, sha2}})
	if err != nil {
		t.Fatal(err)
	}
	meshtest.WaitFor(t, 15*time.Second, "attachments ready", func() bool {
		msg, ok := mb.m.Get(id)
		if !ok || len(msg.Attachments) != 2 {
			return false
		}
		for _, a := range msg.Attachments {
			if a.State != AttReady {
				return false
			}
		}
		return true
	})
	path, att, err := mb.m.AttachmentFile(id, 0)
	if err != nil {
		t.Fatal(err)
	}
	got, _ := os.ReadFile(path)
	if att.Name != "photo.bin" || !bytes.Equal(got, big) {
		t.Fatalf("attachment 0 wrong: %s, %d bytes", att.Name, len(got))
	}
	if _, att, _ := mb.m.AttachmentFile(id, 1); att.Name != "note.txt" {
		t.Fatalf("attachment 1: %+v", att)
	}
	if _, _, err := mb.m.AttachmentFile(id, 5); !mesh.IsCode(err, mesh.CodeNotFound) {
		t.Fatalf("out-of-range attachment: %v", err)
	}
	// Unknown blob hashes cannot be attached.
	if _, err := ma.m.Send(SendInput{Kind: "mail", To: []identity.ID{b.ID()}, Body: "x", Attach: []string{strings.Repeat("ab", 32)}}); err == nil {
		t.Fatal("attached a blob that was never uploaded")
	}
}

func TestBlobFetchRejectsCorruptedContent(t *testing.T) {
	h := meshtest.New(t)
	a := h.Public("alpha", "198.51.100.1")
	b := h.Public("beta", "198.51.100.2")
	h.Mesh(a, b)
	ma, mb := newBox(t, a), newBox(t, b)
	data := bytes.Repeat([]byte("genuine"), 1000)
	sha := ma.upload(t, "x", data)
	// Corrupt the stored blob on alpha (same length): serving it must not poison beta's store.
	if err := os.WriteFile(ma.blobs.Path(sha), bytes.Repeat([]byte("tamper!"), 1000), 0o600); err != nil {
		t.Fatal(err)
	}
	// A message makes beta a legitimate recipient of the attachment.
	id, err := ma.m.Send(SendInput{Kind: "mail", To: []identity.ID{b.ID()}, Subject: "x", Body: "x", Attach: []string{sha}})
	if err != nil {
		t.Fatal(err)
	}
	meshtest.WaitFor(t, 10*time.Second, "the message arrives", func() bool { _, ok := mb.m.Get(id); return ok })
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	err = mb.blobs.Fetch(ctx, mb.node.Peer(a.ID()), sha, int64(len(data)), nil)
	if err == nil {
		t.Fatal("corrupted blob accepted")
	}
	if _, ok := mb.blobs.Has(sha); ok {
		t.Fatal("corrupted blob was stored")
	}
}

// Knowing a hash is not enough: attachments are served to the author and the
// recipients of the message that carries them, and everybody else gets the same
// "no such blob" as for a file that does not exist (so it cannot even be probed).
func TestBlobsAreOnlyServedToTheParticipantsOfAMessage(t *testing.T) {
	h := meshtest.New(t)
	a := h.Public("alpha", "198.51.100.1")
	b := h.Public("beta", "198.51.100.2")
	c := h.Public("gamma", "198.51.100.3")
	h.Mesh(a, b, c)
	ma, mb, mc := newBox(t, a), newBox(t, b), newBox(t, c)

	secret := bytes.Repeat([]byte("for beta only "), 500)
	sha := ma.upload(t, "private.txt", secret)
	id, err := ma.m.Send(SendInput{Kind: "mail", To: []identity.ID{b.ID()}, Subject: "private", Body: "x", Attach: []string{sha}})
	if err != nil {
		t.Fatal(err)
	}
	meshtest.WaitFor(t, 15*time.Second, "beta has the attachment", func() bool {
		msg, ok := mb.m.Get(id)
		return ok && len(msg.Attachments) == 1 && msg.Attachments[0].State == AttReady
	})

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	// A member that was not addressed cannot get it, from the author or from a recipient...
	errFromAuthor := mc.blobs.Fetch(ctx, mc.node.Peer(a.ID()), sha, int64(len(secret)), nil)
	errFromRecipient := mc.blobs.Fetch(ctx, mc.node.Peer(b.ID()), sha, int64(len(secret)), nil)
	if errFromAuthor == nil || errFromRecipient == nil {
		t.Fatalf("a member that was not addressed fetched the attachment (author: %v, recipient: %v)", errFromAuthor, errFromRecipient)
	}
	if _, ok := mc.blobs.Has(sha); ok {
		t.Fatal("the attachment ended up in a stranger's store")
	}
	// ...and cannot tell it from a blob that does not exist at all.
	errMissing := mc.blobs.Fetch(ctx, mc.node.Peer(a.ID()), strings.Repeat("cd", 32), 10, nil)
	if errMissing == nil || errMissing.Error() != errFromAuthor.Error() {
		t.Fatalf("existing and missing blobs are distinguishable: %v / %v", errFromAuthor, errMissing)
	}
	// The recipient still can (say it lost its copy), and so can the author's side.
	mb.blobs.Remove(sha)
	if err := mb.blobs.Fetch(ctx, mb.node.Peer(a.ID()), sha, int64(len(secret)), nil); err != nil {
		t.Fatalf("the recipient was refused its own attachment: %v", err)
	}
	if !ma.m.MayFetchBlob(b.ID(), sha) || !ma.m.MayFetchBlob(a.ID(), sha) || ma.m.MayFetchBlob(c.ID(), sha) {
		t.Fatal("MayFetchBlob disagrees with the message's participants")
	}
}

func TestDeliveryRejectsForgeriesAndStrangers(t *testing.T) {
	h := meshtest.New(t)
	a := h.Public("alpha", "198.51.100.1")
	b := h.Public("beta", "198.51.100.2")
	c := h.Public("gamma", "198.51.100.3")
	h.Mesh(a, b, c)
	_, mb := newBox(t, a), newBox(t, b)
	newBox(t, c)

	call := func(from *mesh.Node, to *mesh.Node, msg signedMsg) error {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		return from.Peer(to.ID()).Call(ctx, "mail.deliver", msg, nil)
	}
	mk := func(sender *identity.Device, mut func(*core)) signedMsg {
		cr := core{ID: newID("m_", sender.ID), Kind: "mail", From: sender.ID, To: []identity.ID{b.ID()}, Subject: "s", Body: "b", Created: time.Now().Unix()}
		if mut != nil {
			mut(&cr)
		}
		raw, _ := json.Marshal(cr)
		return signedMsg{Core: raw, Sig: ed25519.Sign(sender.Priv, raw)}
	}
	// 1. gamma claims the message is from alpha (signature by gamma): rejected.
	forged := mk(c.Device(), func(cr *core) { cr.From = a.ID() })
	if err := call(c, b, forged); !mesh.IsCode(err, mesh.CodeDenied) {
		t.Fatalf("forged sender: %v", err)
	}
	// 2. gamma delivers a message claiming to be alpha's, relaying alpha's signature over different content.
	good := mk(a.Device(), nil)
	tampered := good
	tampered.Core = bytes.Replace(good.Core, []byte(`"b"`), []byte(`"EVIL"`), 1)
	if err := call(a, b, tampered); !mesh.IsCode(err, mesh.CodeDenied) {
		t.Fatalf("tampered content: %v", err)
	}
	// 3. valid, but not addressed to beta.
	other := mk(a.Device(), func(cr *core) { cr.To = []identity.ID{c.ID()} })
	if err := call(a, b, other); !mesh.IsCode(err, mesh.CodeInvalid) {
		t.Fatalf("misaddressed: %v", err)
	}
	// 3b. ids belong to their author: gamma cannot announce an id carrying alpha's name
	// (that is how one co-recipient used to suppress another device's message), and
	// malformed or oversized fields are refused.
	hijack := mk(c.Device(), func(cr *core) { cr.ID = good2ID(a) })
	if err := call(c, b, hijack); !mesh.IsCode(err, mesh.CodeInvalid) {
		t.Fatalf("an id carrying another device's name was accepted: %v", err)
	}
	for _, mut := range []func(*core){
		func(cr *core) { cr.ID = "m_forged" },
		func(cr *core) { cr.ID = strings.Repeat("m", 5000) },
		func(cr *core) { cr.Subject = strings.Repeat("s", 5000) },
		func(cr *core) { cr.InReplyTo = strings.Repeat("r", 100) },
		func(cr *core) {
			for i := 0; i < maxRecipients+1; i++ {
				cr.To = append(cr.To, identity.GenerateDevice().ID)
			}
		},
	} {
		if err := call(a, b, mk(a.Device(), mut)); !mesh.IsCode(err, mesh.CodeInvalid) {
			t.Fatalf("malformed message accepted: %v", err)
		}
	}
	// 4. a genuine message is accepted, and re-delivery is idempotent.
	if err := call(a, b, good); err != nil {
		t.Fatal(err)
	}
	if err := call(a, b, good); err != nil {
		t.Fatalf("re-delivery must be acked: %v", err)
	}
	if got := mb.m.List(FolderInbox, "", 50, 0); got.Total != 1 {
		t.Fatalf("duplicate stored or forgeries accepted: %+v", got)
	}
}

func TestSendValidation(t *testing.T) {
	h := meshtest.New(t)
	a := h.Public("alpha", "198.51.100.1")
	b := h.Public("beta", "198.51.100.2")
	h.Mesh(a, b)
	ma := newBox(t, a)
	if _, err := ma.m.Send(SendInput{Kind: "mail", Body: "x"}); err == nil {
		t.Fatal("no recipients accepted")
	}
	if _, err := ma.m.Send(SendInput{Kind: "mail", To: []identity.ID{b.ID()}}); err == nil {
		t.Fatal("empty message accepted")
	}
	if _, err := ma.m.Send(SendInput{Kind: "mail", To: []identity.ID{identity.GenerateDevice().ID}, Body: "x"}); !mesh.IsCode(err, mesh.CodeNotFound) {
		t.Fatalf("unknown recipient: %v", err)
	}
	if _, err := ma.m.Send(SendInput{Kind: "mail", To: []identity.ID{b.ID()}, Body: strings.Repeat("x", maxBody+1)}); !mesh.IsCode(err, mesh.CodeTooLarge) {
		t.Fatalf("oversized body: %v", err)
	}
	// A note to self lands in the inbox, already read.
	id, err := ma.m.Send(SendInput{Kind: "mail", To: []identity.ID{a.ID()}, Subject: "todo", Body: "buy milk"})
	if err != nil {
		t.Fatal(err)
	}
	msg, _ := ma.m.Get(id)
	if msg.Folder != FolderInbox || msg.Unread {
		t.Fatalf("note to self: %+v", msg.Summary)
	}
}

// an id that looks like one of alpha's
func good2ID(a *mesh.Node) string { return newID("m_", a.ID()) }

// A member can mail an attachment of any announced size. Small ones are fetched
// automatically; a large one waits for the user's consent; one that cannot be
// fetched is retried with growing pauses and finally given up; and a sender that
// streams more than it announced is cut off.
func TestAttachmentFetchPolicy(t *testing.T) {
	h := meshtest.New(t)
	victim := h.Public("victim", "198.51.100.1")
	mallory := h.Public("mallory", "198.51.100.2")
	h.Mesh(victim, mallory)
	vb := newBox(t, victim)

	var served atomic.Int64
	mallory.HandleStream("blob.get", func(ctx context.Context, c *mesh.Call, s *mesh.ServerStream) error {
		served.Add(1)
		const announced = 1 << 20
		if err := s.Reply(map[string]int64{"size": announced}); err != nil {
			return err
		}
		buf := make([]byte, 64<<10)
		for sent := 0; sent < 4*announced; sent += len(buf) { // ...but streams four times as much
			if _, err := s.Write(buf); err != nil {
				return err
			}
		}
		return nil
	})
	deliver := func(att Attachment) string {
		cr := core{ID: newID("m_", mallory.ID()), Kind: "mail", From: mallory.ID(), To: []identity.ID{victim.ID()},
			Subject: "hi", Body: "see attachment", Created: time.Now().Unix(), Attach: []Attachment{att}}
		raw, _ := json.Marshal(cr)
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		if err := mallory.Peer(victim.ID()).Call(ctx, "mail.deliver", signedMsg{Core: raw, Sig: ed25519.Sign(mallory.Device().Priv, raw)}, nil); err != nil {
			t.Fatal(err)
		}
		return cr.ID
	}
	state := func(id string) AttachmentView {
		msg, ok := vb.m.Get(id)
		if !ok || len(msg.Attachments) != 1 {
			t.Fatalf("message %s: %v", id, ok)
		}
		return msg.Attachments[0]
	}

	// 1. Too large to fetch unasked: nothing is downloaded and the user is told.
	big := deliver(Attachment{Name: "film.mkv", Size: 400 << 20, Mime: "video/x-matroska", SHA256: strings.Repeat("a", 64)})
	time.Sleep(4 * time.Second) // more than one pump
	if v := state(big); v.State != AttRemote || !v.NeedsConsent {
		t.Fatalf("a 400 MB attachment: %+v", v)
	}
	if served.Load() != 0 {
		t.Fatalf("the victim fetched a huge attachment without being asked (%d requests)", served.Load())
	}

	// 2. A small one is fetched, but the sender streams more than it announced: refused, backs off.
	small := deliver(Attachment{Name: "a.bin", Size: 1 << 20, Mime: "application/octet-stream", SHA256: strings.Repeat("b", 64)})
	meshtest.WaitFor(t, 15*time.Second, "the first attempt", func() bool { return served.Load() >= 1 })
	time.Sleep(time.Second)
	v := state(small)
	if v.State == AttReady {
		t.Fatal("a blob that does not match its hash was accepted")
	}
	ents, _ := os.ReadDir(filepath.Join(victim.Dir(), "blobs"))
	for _, e := range ents {
		if fi, err := e.Info(); err == nil && !fi.IsDir() && fi.Size() > (1<<20)+(256<<10) {
			t.Fatalf("%d bytes were kept for a 1 MiB announcement", fi.Size())
		}
	}
	before := served.Load()
	time.Sleep(4 * time.Second) // pump runs every ~3 s; the pause after a failure is at least 30 s
	if served.Load() != before {
		t.Fatalf("the failed fetch was retried at once (%d -> %d requests)", before, served.Load())
	}

	// 3. Consent: asking for the big one starts the download (which fails here, but is attempted).
	if err := vb.m.FetchAttachment(big, 0); err != nil {
		t.Fatal(err)
	}
	meshtest.WaitFor(t, 15*time.Second, "the consented download", func() bool { return served.Load() > before })
	if err := vb.m.FetchAttachment(big, 7); !mesh.IsCode(err, mesh.CodeNotFound) {
		t.Fatalf("an unknown attachment index: %v", err)
	}
}
