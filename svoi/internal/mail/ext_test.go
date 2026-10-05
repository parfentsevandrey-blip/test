package mail

import (
	"bytes"
	"errors"
	"io"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/inetmail"
	"github.com/parfentsevandrey-blip/test/svoi/internal/meshtest"
)

// fakeGW is the part of a gateway that the manager talks to: it has mailboxes for devices, takes letters and says what it did.
type fakeGW struct {
	mu        sync.Mutex
	mailboxes map[identity.ID][]string
	sent      []OutLetter
	files     map[string][]byte
	deny      error
	notYet    bool
}

func (g *fakeGW) Info(peer identity.ID) (GatewayInfo, bool) {
	g.mu.Lock()
	defer g.mu.Unlock()
	mb := g.mailboxes[peer]
	if len(mb) == 0 {
		return GatewayInfo{}, false
	}
	return GatewayInfo{Domain: "example.org", Host: "mail.example.org", Mailboxes: mb, Ready: true}, true
}

func (g *fakeGW) CanSend(peer identity.ID, from string) error {
	g.mu.Lock()
	defer g.mu.Unlock()
	if g.deny != nil {
		return g.deny
	}
	for _, mb := range g.mailboxes[peer] {
		if strings.EqualFold(mb, from) {
			return nil
		}
	}
	return errors.New("this device has no such mailbox")
}

func (g *fakeGW) SendOut(l OutLetter) error {
	g.mu.Lock()
	defer g.mu.Unlock()
	if g.notYet {
		return ErrNotYet
	}
	for _, a := range l.Attach {
		rc, err := a.Open()
		if err != nil {
			return err
		}
		b, _ := io.ReadAll(rc)
		rc.Close()
		if g.files == nil {
			g.files = map[string][]byte{}
		}
		g.files[a.Name] = b
	}
	g.sent = append(g.sent, l)
	return nil
}

func (g *fakeGW) letters() []OutLetter {
	g.mu.Lock()
	defer g.mu.Unlock()
	return append([]OutLetter(nil), g.sent...)
}

// threeDevices: a laptop (the administrator), a phone and a home server that is the gateway; all of one owner.
func threeDevices(t *testing.T) (laptop, phone, home *box, gw *fakeGW) {
	t.Helper()
	h := meshtest.New(t)
	a := h.Public("laptop", "198.51.100.1")
	b := h.Public("phone", "198.51.100.2")
	c := h.Public("home", "198.51.100.3")
	h.Mesh(a, b, c)
	laptop, phone, home = newBox(t, a), newBox(t, b), newBox(t, c)
	gw = &fakeGW{mailboxes: map[identity.ID][]string{a.ID(): {"andrey@example.org"}, b.ID(): {"andrey@example.org"}}}
	home.m.SetGateway(gw)
	return
}

func TestLetterFromTheInternet(t *testing.T) {
	laptop, phone, home, _ := threeDevices(t)
	file := bytes.Repeat([]byte("pdf!"), 1000)
	sha, size, err := home.blobs.Put(bytes.NewReader(file))
	if err != nil {
		t.Fatal(err)
	}
	id, err := home.m.ReceiveExternal(ExtLetter{
		MessageID: "<abc123@mail.gmail.example>", Mailbox: "andrey@example.org",
		Devices: []identity.ID{laptop.node.ID(), phone.node.ID()},
		In: ExtIn{
			MessageID: "abc123@mail.gmail.example", From: ExtAddr{Name: "Alice Example", Addr: "alice@gmail.example"}, To: []ExtAddr{{Addr: "andrey@example.org"}},
			HTML: "<p>Hello <b>Andrey</b></p>", Verdict: inetmail.VerdictVerified, SPF: "pass", DMARC: "pass", DKIM: "gmail.example",
		},
		Subject: "Привет с Gmail", Text: "Hello Andrey", Created: time.Now().Unix(),
		Attach: []Attachment{{Name: "report.pdf", Size: size, Mime: "application/pdf", SHA256: sha}},
	})
	if err != nil {
		t.Fatal(err)
	}
	if id2, _ := home.m.ReceiveExternal(ExtLetter{MessageID: "<abc123@mail.gmail.example>", Mailbox: "andrey@example.org", Devices: []identity.ID{laptop.node.ID()}, Subject: "again"}); id2 != id {
		t.Errorf("the same letter again is the same letter: %s %s", id2, id)
	}
	for _, b := range []*box{laptop, phone} {
		meshtest.WaitFor(t, 15*time.Second, "the letter to arrive", func() bool { return b.m.List(FolderInbox, "", 50, 0).Total == 1 })
		it := b.m.List(FolderInbox, "", 50, 0).Items[0]
		if it.From.Name != "Alice Example" || it.Subject != "Привет с Gmail" || !it.Unread || it.Ext == nil || it.Ext.Dir != "in" ||
			it.Ext.From.Addr != "alice@gmail.example" || it.Ext.Verdict != inetmail.VerdictVerified || it.Ext.Mailbox != "andrey@example.org" || it.Ext.Via == nil || it.Ext.Via.Name != "gamma" {
			t.Errorf("%s: %+v / %+v", b.node.Self().Name, it, it.Ext)
		}
		msg, _ := b.m.Get(id)
		if msg.HTML != "<p>Hello <b>Andrey</b></p>" || msg.Body != "Hello Andrey" || len(msg.Attachments) != 1 {
			t.Errorf("%s: full letter %+v", b.node.Self().Name, msg)
		}
		if r := b.m.List(FolderInbox, "alice@gmail", 50, 0); r.Total != 1 {
			t.Errorf("a search by the address of the sender: %+v", r)
		}
		// the attachment is pulled from the gateway like any other
		meshtest.WaitFor(t, 15*time.Second, "the attachment", func() bool {
			m, _ := b.m.Get(id)
			return m != nil && m.Attachments[0].State == AttReady
		})
	}
	// the gateway keeps its copy out of sight: it is not a recipient
	if home.m.List(FolderInbox, "", 50, 0).Total != 0 || home.m.List(FolderRelay, "", 50, 0).Total != 1 {
		t.Errorf("the gateway's copy: inbox %d relay %d", home.m.List(FolderInbox, "", 50, 0).Total, home.m.List(FolderRelay, "", 50, 0).Total)
	}
	if mu, _ := home.m.Counters(); mu != 0 {
		t.Errorf("a letter that only passes through is not unread mail: %d", mu)
	}
	// an answer on the Internet lands in the same conversation
	id3, err := home.m.ReceiveExternal(ExtLetter{
		MessageID: "<def456@mail.gmail.example>", Mailbox: "andrey@example.org", Devices: []identity.ID{laptop.node.ID()},
		In:      ExtIn{MessageID: "def456@mail.gmail.example", References: []string{"abc123@mail.gmail.example"}, From: ExtAddr{Addr: "alice@gmail.example"}, Verdict: inetmail.VerdictUnverified},
		Subject: "Re: Привет с Gmail", Text: "second",
	})
	if err != nil {
		t.Fatal(err)
	}
	meshtest.WaitFor(t, 15*time.Second, "the second letter", func() bool { return laptop.m.List(FolderInbox, "", 50, 0).Total == 2 })
	a, _ := laptop.m.Get(id)
	b, _ := laptop.m.Get(id3)
	if a.Thread != b.Thread {
		t.Errorf("an answer by References is in the same thread: %q %q", a.Thread, b.Thread)
	}
}

func TestLetterToTheInternet(t *testing.T) {
	laptop, _, home, gw := threeDevices(t)
	meshtest.WaitFor(t, 20*time.Second, "the laptop to learn which mailboxes it has", func() bool {
		g := laptop.m.Gateways()
		return len(g) == 1 && len(g[0].Mailboxes) == 1 && g[0].Name == "gamma" && g[0].Domain == "example.org"
	})
	if g := home.m.Gateways(); len(g) != 1 || !g[0].Self || g[0].Mailboxes != nil && len(g[0].Mailboxes) != 0 {
		// (the gateway itself has no mailbox of its own in this test)
		t.Logf("home: %+v", g)
	}
	sha := laptop.upload(t, "invoice.pdf", []byte("invoice-bytes"))
	id, err := laptop.m.Send(SendInput{Kind: "mail", Subject: "Счёт", Body: "See attached", Attach: []string{sha}, ExtTo: []string{"Bob <bob@gmail.example>"}, ExtCc: []string{"carol@yahoo.example"}})
	if err != nil {
		t.Fatal(err)
	}
	msg, _ := laptop.m.Get(id)
	if msg.Ext == nil || msg.Ext.Dir != "out" || msg.Ext.From.Addr != "andrey@example.org" || len(msg.Ext.Out) != 2 || msg.Ext.Out[0].State != inetmail.RcptQueued || msg.Ext.Out[1].Kind != "cc" {
		t.Fatalf("the letter as the author sees it: %+v", msg.Ext)
	}
	if len(msg.To) != 0 || msg.Folder != FolderSent {
		t.Errorf("the gateway is not a recipient of the author's letter: %+v", msg.To)
	}
	meshtest.WaitFor(t, 20*time.Second, "the gateway to take the letter", func() bool { return len(gw.letters()) == 1 })
	l := gw.letters()[0]
	if l.ID != id || l.From.Addr != "andrey@example.org" || len(l.To) != 1 || l.To[0].Addr != "bob@gmail.example" || len(l.Cc) != 1 || l.Subject != "Счёт" || l.Text != "See attached" ||
		!strings.HasSuffix(l.MessageID, "@example.org") || l.Sender != laptop.node.ID() {
		t.Errorf("what the gateway got: %+v", l)
	}
	if string(gw.files["invoice.pdf"]) != "invoice-bytes" {
		t.Errorf("the gateway reads the attachment: %q", gw.files["invoice.pdf"])
	}
	if home.m.List(FolderInbox, "", 50, 0).Total != 0 || home.m.List(FolderRelay, "", 50, 0).Total != 1 {
		t.Error("the gateway keeps the letter out of its owner's sight")
	}
	// the gateway tells what became of each address; the author hears it
	home.m.ExtReport(id, "bob@gmail.example", inetmail.RcptDelivered, 250, "2.0.0 OK queued as 123", time.Now().Unix())
	home.m.ExtReport(id, "carol@yahoo.example", inetmail.RcptDeferred, 451, "4.7.0 try later", time.Now().Unix())
	meshtest.WaitFor(t, 20*time.Second, "the author to hear", func() bool {
		m, _ := laptop.m.Get(id)
		return m.Ext.Out[0].State == inetmail.RcptDelivered && m.Ext.Out[1].State == inetmail.RcptDeferred
	})
	home.m.ExtReport(id, "carol@yahoo.example", inetmail.RcptFailed, 550, "5.1.1 no such user", time.Now().Unix())
	meshtest.WaitFor(t, 20*time.Second, "the failure", func() bool {
		m, _ := laptop.m.Get(id)
		return m.Ext.Out[1].State == inetmail.RcptFailed && m.Ext.Out[1].Code == 550 && strings.Contains(m.Ext.Out[1].Text, "no such user")
	})
	// a final state is not taken back by a later report
	home.m.ExtReport(id, "carol@yahoo.example", inetmail.RcptDeferred, 451, "again", time.Now().Unix())
	time.Sleep(200 * time.Millisecond)
	if m, _ := home.m.Get(id); m.Ext.Out[1].State != inetmail.RcptFailed {
		t.Errorf("failed stays failed: %+v", m.Ext.Out[1])
	}
	// the author's folder and unread counters are what they were
	if r := laptop.m.List(FolderSent, "", 50, 0); r.Total != 1 {
		t.Errorf("sent: %+v", r)
	}
	// replying to a letter of the Internet carries its Message-ID
	rid, err := home.m.ReceiveExternal(ExtLetter{
		MessageID: "<q1@gmail.example>", Mailbox: "andrey@example.org", Devices: []identity.ID{laptop.node.ID()},
		In: ExtIn{MessageID: "q1@gmail.example", References: []string{"root@gmail.example"}, From: ExtAddr{Addr: "bob@gmail.example"}, Verdict: inetmail.VerdictVerified}, Subject: "Question", Text: "?",
	})
	if err != nil {
		t.Fatal(err)
	}
	meshtest.WaitFor(t, 15*time.Second, "the question", func() bool { _, ok := laptop.m.Get(rid); return ok })
	if _, err := laptop.m.Send(SendInput{Kind: "mail", Subject: "Re: Question", Body: "answer", InReplyTo: rid, ExtTo: []string{"bob@gmail.example"}}); err != nil {
		t.Fatal(err)
	}
	meshtest.WaitFor(t, 20*time.Second, "the answer to reach the gateway", func() bool { return len(gw.letters()) == 2 })
	if a := gw.letters()[1]; a.InReplyTo != "q1@gmail.example" || len(a.References) != 2 || a.References[1] != "q1@gmail.example" {
		t.Errorf("an answer carries the headers of the conversation: %+v", a)
	}
}

func TestLetterToTheInternetNeedsAMailbox(t *testing.T) {
	h := meshtest.New(t)
	a := h.Public("alpha", "198.51.100.1")
	b := h.Public("beta", "198.51.100.2")
	h.Mesh(a, b)
	ma, mb := newBox(t, a), newBox(t, b)
	_ = mb
	if _, err := ma.m.Send(SendInput{Kind: "mail", Body: "x", ExtTo: []string{"bob@gmail.example"}}); err == nil || !strings.Contains(err.Error(), "no mailbox") {
		t.Errorf("a device that has no mailbox cannot write to the Internet: %v", err)
	}
	if _, err := ma.m.Send(SendInput{Kind: "chat", To: []identity.ID{b.ID()}, Body: "x", ExtTo: []string{"bob@gmail.example"}}); err == nil {
		t.Error("a chat message does not go to the Internet")
	}
	if _, err := ma.m.Send(SendInput{Kind: "mail", Body: "x", ExtFrom: "me@example.org", ExtTo: []string{"not an address"}}); err == nil {
		t.Error("an address that is not one")
	}
}

func TestTheGatewayRefusesWhatIsNotTheDevicesMailbox(t *testing.T) {
	laptop, phone, home, gw := threeDevices(t)
	meshtest.WaitFor(t, 20*time.Second, "gateways", func() bool { return len(laptop.m.Gateways()) == 1 })
	gw.mu.Lock()
	gw.deny = errors.New("this device may not send mail now")
	gw.mu.Unlock()
	id, err := laptop.m.Send(SendInput{Kind: "mail", Body: "x", ExtTo: []string{"bob@gmail.example"}})
	if err != nil {
		t.Fatal(err)
	}
	meshtest.WaitFor(t, 20*time.Second, "the refusal to reach the author", func() bool {
		m, _ := laptop.m.Get(id)
		return m.Ext.Out[0].State == inetmail.RcptFailed && strings.Contains(m.Ext.Out[0].Text, "may not send mail")
	})
	if len(gw.letters()) != 0 || home.m.List(FolderRelay, "", 50, 0).Total != 0 {
		t.Error("a refused letter is not kept")
	}
	_ = phone
}

func TestGatewayNotReadyWaits(t *testing.T) {
	laptop, _, _, gw := threeDevices(t)
	meshtest.WaitFor(t, 20*time.Second, "gateways", func() bool { return len(laptop.m.Gateways()) == 1 })
	gw.mu.Lock()
	gw.notYet = true
	gw.mu.Unlock()
	id, err := laptop.m.Send(SendInput{Kind: "mail", Body: "x", ExtTo: []string{"bob@gmail.example"}})
	if err != nil {
		t.Fatal(err)
	}
	time.Sleep(4 * time.Second)
	if len(gw.letters()) != 0 {
		t.Fatal("not yet means not yet")
	}
	if m, _ := laptop.m.Get(id); m.Ext.Out[0].State != inetmail.RcptQueued {
		t.Errorf("it waits: %+v", m.Ext.Out[0])
	}
	gw.mu.Lock()
	gw.notYet = false
	gw.mu.Unlock()
	meshtest.WaitFor(t, 15*time.Second, "the gateway takes it when it is ready", func() bool { return len(gw.letters()) == 1 })
}

func TestOnlyATrustedGatewayBringsLettersFromTheInternet(t *testing.T) {
	h := meshtest.New(t)
	a := h.Public("laptop", "198.51.100.1")
	b := h.Public("stranger", "198.51.100.2")
	if err := a.CreateMesh("Test", "laptop", "tester"); err != nil {
		t.Fatal(err)
	}
	h.Join(a, b, "stranger", "somebody-else", false) // a device of another person: not an administrator, not of the owner of the laptop
	h.WaitConnected(a, b)
	ma, mb := newBox(t, a), newBox(t, b)
	if _, err := mb.m.ReceiveExternal(ExtLetter{MessageID: "<x@evil.example>", Mailbox: "andrey@example.org", Devices: []identity.ID{a.ID()}, In: ExtIn{From: ExtAddr{Addr: "ceo@bank.example"}, Verdict: inetmail.VerdictVerified}, Subject: "Pay now", Text: "..."}); err != nil {
		t.Fatal(err)
	}
	meshtest.WaitFor(t, 15*time.Second, "the refusal", func() bool {
		m, ok := mb.m.Get(mustFirstID(t, mb.m))
		return ok && m.To[0].State == DelFailed && strings.Contains(m.To[0].State, "failed")
	})
	if ma.m.List(FolderInbox, "", 50, 0).Total != 0 {
		t.Error("letters from the Internet are believed only from a gateway that is trusted")
	}
}

func mustFirstID(t *testing.T, m *Manager) string {
	t.Helper()
	r := m.List(FolderRelay, "", 50, 0)
	if len(r.Items) == 0 {
		return ""
	}
	return r.Items[0].ID
}
