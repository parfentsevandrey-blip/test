package mail

import (
	"bytes"
	"context"
	"crypto/ed25519"
	"encoding/json"
	"strings"
	"testing"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/meshtest"
)

// What a mailbox lets us hand to whom (see MayFetchBlob), with made-up messages.
func TestBlobACLFollowsTheMessagesWeHold(t *testing.T) {
	h := meshtest.New(t)
	a := h.Public("alpha", "198.51.100.1")
	h.Mesh(a)
	m := newBox(t, a).m
	self := a.ID()
	x, y, z := identity.GenerateDevice().ID, identity.GenerateDevice().ID, identity.GenerateDevice().ID
	sha := strings.Repeat("ab", 32)
	att := []Attachment{{Name: "f", SHA256: sha}}
	add := func(id string, from identity.ID, to []identity.ID, f *fetchState) *record {
		r := &record{Core: core{ID: id, Kind: "mail", From: from, To: to, Attach: att}}
		if f != nil {
			r.Fetch = map[string]*fetchState{sha: f}
		}
		m.mu.Lock()
		m.msgs[id] = r
		m.indexLocked(r, +1)
		m.mu.Unlock()
		return r
	}
	remove := func(r *record) {
		m.mu.Lock()
		delete(m.msgs, r.Core.ID)
		m.indexLocked(r, -1)
		m.mu.Unlock()
	}
	may := func(p identity.ID) bool { return m.MayFetchBlob(p, sha) }

	// Somebody else's message that merely names the hash gives nobody anything: not its
	// author, not the recipients it lists (those are the author's words, and the
	// blob did not come from it).
	forged := add("m_forged", z, []identity.ID{self, z, y}, &fetchState{State: AttReady})
	if may(z) || may(y) || may(x) {
		t.Fatal("a message that names a hash we already had authorised its author or its recipients")
	}
	fromElsewhere := add("m_else", x, []identity.ID{self, y}, &fetchState{State: AttReady, Via: false})
	if may(x) || may(y) {
		t.Fatal("an attachment that did not come from the message's author was passed on")
	}

	// Our own message: its recipients (and only they).
	own := add("m_own", self, []identity.ID{x}, nil)
	if !may(x) || may(y) || may(z) || may(self) {
		t.Fatalf("own message: x=%v y=%v z=%v self=%v, want true false false false", may(x), may(y), may(z), may(self))
	}

	// A message whose attachment we pulled from its author may be passed on to the others it lists.
	pulled := add("m_pulled", z, []identity.ID{self, y}, &fetchState{State: AttReady, Via: true})
	if !may(y) || !may(z) {
		t.Fatal("an attachment pulled from the author was not passed on to the co-recipient")
	}
	// Two messages say the same: dropping one keeps the permission, dropping both ends it.
	remove(pulled)
	if may(y) || may(z) {
		t.Fatal("permission outlived the message that gave it")
	}
	pulled = add("m_pulled", z, []identity.ID{self, y}, &fetchState{State: AttReady, Via: true})
	pulled2 := add("m_pulled2", z, []identity.ID{self, y}, &fetchState{State: AttReady, Via: true})
	remove(pulled)
	if !may(y) {
		t.Fatal("dropping one of two identical grants removed both")
	}
	remove(pulled2)
	remove(own)
	remove(forged)
	remove(fromElsewhere)
	m.mu.Lock()
	left, refs := len(m.blobAuth), len(m.blobRefs)
	m.mu.Unlock()
	if left != 0 || refs != 0 {
		t.Fatalf("the index kept %d permissions and %d references after every message was removed", left, refs)
	}

	// An attachment listed twice in one message counts once.
	r := &record{Core: core{ID: "m_twice", Kind: "mail", From: self, To: []identity.ID{x}, Attach: []Attachment{{SHA256: sha}, {SHA256: sha}}}}
	m.mu.Lock()
	m.msgs[r.Core.ID] = r
	m.indexLocked(r, +1)
	m.mu.Unlock()
	remove(r)
	if may(x) {
		t.Fatal("a doubly listed attachment was still authorised after its message was removed")
	}
}

// Anybody in the mesh can send us a message naming any hash. That must not let them
// fetch a blob we hold for somebody else, however they address the message.
func TestAMessageNamingAHashDoesNotAuthoriseItsSender(t *testing.T) {
	h := meshtest.New(t)
	a := h.Public("alpha", "198.51.100.1")
	b := h.Public("beta", "198.51.100.2")
	c := h.Public("gamma", "198.51.100.3") // never addressed
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
	size := int64(len(secret))
	if err := mc.blobs.Fetch(ctx, mc.node.Peer(a.ID()), sha, size, nil); err == nil {
		t.Fatal("baseline broken: gamma fetched the blob without any trick")
	}
	for i, to := range [][]identity.ID{
		{a.ID()},         // a message to alpha from gamma that lists the hash
		{a.ID(), c.ID()}, // ...which also lists gamma itself as a recipient
		{a.ID(), b.ID()}, // ...and one that lists beta as well
		{b.ID(), a.ID()},
	} {
		cr := core{
			ID: newID("m_", c.ID()), Kind: "mail", From: c.ID(), To: to,
			Subject: "hi", Body: "see attachment", Created: time.Now().Unix(),
			Attach: []Attachment{{Name: "x.bin", Size: size, Mime: "application/octet-stream", SHA256: sha}},
		}
		raw, _ := json.Marshal(cr)
		msg := signedMsg{Core: raw, Sig: ed25519.Sign(c.Device().Priv, raw)}
		if err := c.Peer(a.ID()).Call(ctx, "mail.deliver", msg, nil); err != nil {
			t.Fatalf("variant %d: delivery refused: %v", i, err)
		}
		if err := mc.blobs.Fetch(ctx, mc.node.Peer(a.ID()), sha, size, nil); err == nil {
			t.Fatalf("variant %d: gamma was never addressed, yet it holds alpha's blob after delivering a message that names the hash", i)
		}
		if ma.m.MayFetchBlob(c.ID(), sha) {
			t.Fatalf("variant %d: MayFetchBlob says yes", i)
		}
	}
	if !ma.m.MayFetchBlob(b.ID(), sha) {
		t.Fatal("the real recipient lost its permission")
	}
}

// The people a message was written to may get the attachment from each other, once
// one of them has it from the author (the author may be offline).
func TestCoRecipientsServeEachOther(t *testing.T) {
	h := meshtest.New(t)
	a := h.Public("alpha", "198.51.100.1")
	b := h.Public("beta", "198.51.100.2")
	c := h.Public("gamma", "198.51.100.3")
	d := h.Public("delta", "198.51.100.4") // not a recipient
	h.Mesh(a, b, c, d)
	ma, mb, mc, md := newBox(t, a), newBox(t, b), newBox(t, c), newBox(t, d)
	_ = md

	data := bytes.Repeat([]byte("both of you "), 400)
	sha := ma.upload(t, "shared.txt", data)
	id, err := ma.m.Send(SendInput{Kind: "mail", To: []identity.ID{b.ID(), c.ID()}, Subject: "both", Body: "x", Attach: []string{sha}})
	if err != nil {
		t.Fatal(err)
	}
	for _, mb := range []*box{mb, mc} {
		mb := mb
		meshtest.WaitFor(t, 15*time.Second, "the attachment arrived", func() bool {
			msg, ok := mb.m.Get(id)
			return ok && len(msg.Attachments) == 1 && msg.Attachments[0].State == AttReady
		})
	}
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	mc.blobs.Remove(sha)
	if err := mc.blobs.Fetch(ctx, mc.node.Peer(b.ID()), sha, int64(len(data)), nil); err != nil {
		t.Fatalf("a co-recipient could not get the attachment from the one that had it from the author: %v", err)
	}
	if err := md.blobs.Fetch(ctx, md.node.Peer(b.ID()), sha, int64(len(data)), nil); err == nil {
		t.Fatal("somebody who was not addressed got it from a recipient")
	}
}

// The check runs for every blob request, authorised or not, under the lock the whole
// mailbox shares: it must not depend on how many messages there are.
func TestBlobChecksDoNotScaleWithTheMailbox(t *testing.T) {
	h := meshtest.New(t)
	a := h.Public("alpha", "198.51.100.1")
	h.Mesh(a)
	m := newBox(t, a).m
	author, me := identity.GenerateDevice().ID, identity.GenerateDevice().ID
	hot := strings.Repeat("ab", 32)
	other := strings.Repeat("cd", 32)
	const n = 50_000
	m.mu.Lock()
	for i := 0; len(m.msgs) < n; i++ {
		id := newID("m_", author)
		c := core{ID: id, Kind: "mail", From: author, To: []identity.ID{me}}
		for k := 0; k < 5; k++ {
			c.Attach = append(c.Attach, Attachment{Name: "f", SHA256: other})
		}
		c.Attach = append(c.Attach, Attachment{Name: "h", SHA256: hot})
		r := &record{Core: c}
		m.msgs[id] = r
		m.indexLocked(r, +1)
	}
	m.mu.Unlock()
	start := time.Now()
	const reqs = 2000
	for i := 0; i < reqs; i++ {
		_ = m.MayFetchBlob(me, hot)
		_ = m.MayFetchBlob(me, other)
		_ = m.MayFetchBlob(me, strings.Repeat("ef", 32))
	}
	if per := time.Since(start) / (3 * reqs); per > 100*time.Microsecond {
		t.Fatalf("%v per authorisation check with %d messages in the mailbox", per, n)
	}
	// Removing a message and cleaning up its blobs is as cheap.
	var victim *record
	m.mu.Lock()
	for _, r := range m.msgs {
		victim = r
		break
	}
	start = time.Now()
	delete(m.msgs, victim.Core.ID)
	m.indexLocked(victim, -1)
	m.gcBlobsLocked(victim)
	took := time.Since(start)
	m.mu.Unlock()
	if took > 20*time.Millisecond {
		t.Fatalf("deleting one message took %v with %d in the mailbox", took, n)
	}
}
