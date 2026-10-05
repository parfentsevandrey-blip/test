package inetmail

import (
	"bytes"
	"context"
	"crypto/tls"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/emersion/go-sasl"
	"github.com/emersion/go-smtp"

	"github.com/parfentsevandrey-blip/test/svoi/internal/store"
)

var quiet = slog.New(slog.NewTextHandler(io.Discard, nil))

// letterIn is a letter the receiving server handed over.
type letterIn struct {
	in  *Inbound
	raw []byte
}

type world struct {
	t      *testing.T
	dns    *FakeDNS
	srv    *Server
	port   int
	got    chan letterIn
	mu     sync.Mutex
	reject func(*Inbound) error // what Deliver answers (nil: takes the letter)
	mbox   map[string]bool
}

// newWorld starts a mail server for recv.test on a loopback port and a DNS that knows it and the domain send.test, whose key,
// SPF and DMARC are published.
func newWorld(t *testing.T) (*world, *DKIMKey) {
	t.Helper()
	w := &world{t: t, dns: NewFakeDNS(), got: make(chan letterIn, 16), mbox: map[string]bool{"bob": true}}
	w.dns.AddMX("recv.test", "mail.recv.test", 10)
	w.dns.AddA("mail.recv.test", "127.0.0.1")
	key, err := NewDKIMKey("mesh")
	if err != nil {
		t.Fatal(err)
	}
	w.dns.AddTXT(key.RecordName("send.test"), key.DNSValue())
	w.dns.AddTXT("send.test", "v=spf1 ip4:127.0.0.1 -all")
	w.dns.AddTXT("_dmarc.send.test", "v=DMARC1; p=reject")
	w.dns.AddMX("send.test", "mail.send.test", 10)
	w.dns.AddA("mail.send.test", "127.0.0.1")

	certPEM, keyPEM, err := SelfSignedCert("mail.recv.test")
	if err != nil {
		t.Fatal(err)
	}
	cert, err := tls.X509KeyPair(certPEM, keyPEM)
	if err != nil {
		t.Fatal(err)
	}
	srv, err := NewServer(ServerConfig{
		Hostname: "mail.recv.test", Resolver: w.dns, TLS: &tls.Config{Certificates: []tls.Certificate{cert}}, SpoolDir: t.TempDir(), Log: quiet,
		IsLocalDomain: func(d string) bool { return d == "recv.test" },
		Accept: func(a Address) error {
			w.mu.Lock()
			defer w.mu.Unlock()
			if !w.mbox[strings.ToLower(a.Local)] {
				return ErrNoMailbox
			}
			return nil
		},
		Deliver: func(ctx context.Context, in *Inbound) error {
			f, err := in.Open()
			if err != nil {
				return err
			}
			raw, _ := io.ReadAll(f)
			f.Close()
			w.mu.Lock()
			rej := w.reject
			w.mu.Unlock()
			if rej != nil {
				if err := rej(in); err != nil {
					return err
				}
			}
			w.got <- letterIn{in, raw}
			return nil
		},
	})
	if err != nil {
		t.Fatal(err)
	}
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	w.srv, w.port = srv, ln.Addr().(*net.TCPAddr).Port
	go srv.Serve(ln)
	t.Cleanup(func() { srv.Close() })
	return w, key
}

// clock is a time that only moves when the test says so.
type clock struct {
	mu sync.Mutex
	t  time.Time
}

func (c *clock) now() time.Time      { c.mu.Lock(); defer c.mu.Unlock(); return c.t }
func (c *clock) add(d time.Duration) { c.mu.Lock(); c.t = c.t.Add(d); c.mu.Unlock() }

type reports struct {
	mu   sync.Mutex
	list []Report
}

func (r *reports) take(rep Report) error {
	r.mu.Lock()
	r.list = append(r.list, rep)
	r.mu.Unlock()
	return nil
}
func (r *reports) last(rcpt string) (Report, bool) {
	r.mu.Lock()
	defer r.mu.Unlock()
	for i := len(r.list) - 1; i >= 0; i-- {
		if r.list[i].Rcpt == rcpt {
			return r.list[i], true
		}
	}
	return Report{}, false
}

func (w *world) sender(t *testing.T, key *DKIMKey, mod func(*SenderConfig)) (*Sender, *reports, *clock) {
	t.Helper()
	db, err := store.Open(filepath.Join(t.TempDir(), "q.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { db.Close() })
	rep := &reports{}
	clk := &clock{t: time.Date(2026, 5, 17, 10, 0, 0, 0, time.UTC)}
	cfg := SenderConfig{
		DB: db, SpoolDir: t.TempDir(), Hostname: "mail.send.test", Resolver: w.dns, Port: w.port, AllowPrivate: true, Log: quiet,
		DKIM:   func(d string) *DKIMKey { return key },
		Report: rep.take, Now: clk.now,
	}
	if mod != nil {
		mod(&cfg)
	}
	s, err := NewSender(cfg)
	if err != nil {
		t.Fatal(err)
	}
	return s, rep, clk
}

func letter(t *testing.T, to ...Address) []byte {
	t.Helper()
	var buf bytes.Buffer
	_, err := BuildMIME(&buf, &OutMessage{
		From: Address{Name: "Alice", Local: "alice", Domain: "send.test"}, To: to, Subject: "Привет из сети",
		Text: "Hello Bob,\nthis letter has crossed the Internet.\n", HTML: "<p>Hello <b>Bob</b></p>",
		Attachments: []OutAttachment{{Name: "note.txt", Mime: "text/plain", Open: bytesOpen([]byte("an attachment"))}},
	})
	if err != nil {
		t.Fatal(err)
	}
	return buf.Bytes()
}

func wait(t *testing.T, what string, ok func() bool) {
	t.Helper()
	deadline := time.Now().Add(15 * time.Second)
	for !ok() {
		if time.Now().After(deadline) {
			t.Fatalf("timeout: %s", what)
		}
		time.Sleep(10 * time.Millisecond)
	}
}

func TestLetterCrossesTheInternet(t *testing.T) {
	w, key := newWorld(t)
	s, rep, _ := w.sender(t, key, nil)
	bob := Address{Name: "Bob", Local: "bob", Domain: "recv.test"}
	raw := letter(t, bob)
	id, err := s.Enqueue("alice@send.test", []string{"bob@recv.test", "nobody@recv.test", "someone@nonexistent.test"}, "m_1", bytes.NewReader(raw))
	if err != nil {
		t.Fatal(err)
	}
	if id2, _ := s.Enqueue("alice@send.test", []string{"bob@recv.test"}, "m_1", bytes.NewReader(raw)); id2 != id {
		t.Errorf("the same origin is the same letter: %s %s", id2, id)
	}
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go s.Run(ctx)

	var got letterIn
	select {
	case got = <-w.got:
	case <-time.After(20 * time.Second):
		t.Fatal("no letter arrived")
	}
	wait(t, "the reports", func() bool {
		a, ok1 := rep.last("bob@recv.test")
		b, ok2 := rep.last("nobody@recv.test")
		c, ok3 := rep.last("someone@nonexistent.test")
		return ok1 && ok2 && ok3 && a.Final && b.Final && c.Final
	})
	if r, _ := rep.last("bob@recv.test"); r.State != RcptDelivered || r.Origin != "m_1" || r.ID != id {
		t.Errorf("bob: %+v", r)
	}
	if r, _ := rep.last("nobody@recv.test"); r.State != RcptFailed || r.Code != 550 || !strings.Contains(r.Text, "5.1.1") {
		t.Errorf("a mailbox that does not exist is a final no, in the words of the server: %+v", r)
	}
	if r, _ := rep.last("someone@nonexistent.test"); r.State != RcptFailed || !strings.Contains(r.Text, "does not exist") {
		t.Errorf("a domain that does not exist: %+v", r)
	}

	in := got.in
	if len(in.Rcpts) != 1 || in.Rcpts[0].Addr() != "bob@recv.test" || in.MailFrom != "alice@send.test" || !in.TLS {
		t.Errorf("envelope: %+v", in)
	}
	if in.Auth.SPF != SPFPass || in.Auth.DMARC.Result != "pass" || in.Auth.Verdict() != VerdictVerified || len(in.Auth.DKIM) != 1 || !in.Auth.DKIM[0].Pass || in.Auth.DKIM[0].Domain != "send.test" {
		t.Errorf("the proofs of the sender: %+v", in.Auth)
	}
	text := string(got.raw)
	if !strings.HasPrefix(text, "Return-Path: <alice@send.test>\r\nReceived: from mail.send.test ([127.0.0.1]) by mail.recv.test with ESMTPS id ") ||
		!strings.Contains(text, "Authentication-Results: mail.recv.test; spf=pass") || !strings.Contains(text, "DKIM-Signature:") {
		t.Errorf("trace headers:\n%s", text[:min(len(text), 900)])
	}
	p, err := ParseMessage(bytes.NewReader(got.raw), nil, ParseLimits{})
	if err != nil {
		t.Fatal(err)
	}
	if p.Subject != "Привет из сети" || !strings.Contains(p.Text, "crossed the Internet") || !strings.Contains(p.HTML, "<b>Bob</b>") ||
		len(p.Attachments) != 1 || p.Attachments[0].Name != "note.txt" || p.Attachments[0].Size != 13 {
		t.Errorf("the letter that arrived: %+v", p)
	}
	// everything is reported and told: the queue is empty again
	wait(t, "the queue to empty", func() bool { s.mu.Lock(); defer s.mu.Unlock(); return len(s.items) == 0 })
}

func TestDMARCRejectionIsAFinalNo(t *testing.T) {
	w, key := newWorld(t)
	other, _ := NewDKIMKey("mesh") // a key that the DNS does not know
	w.dns.SetTXT("send.test", "v=spf1 ip4:198.51.100.9 -all")
	s, rep, _ := w.sender(t, key, func(c *SenderConfig) { c.DKIM = func(string) *DKIMKey { return other } })
	if _, err := s.Enqueue("alice@send.test", []string{"bob@recv.test"}, "m_2", bytes.NewReader(letter(t, Address{Local: "bob", Domain: "recv.test"}))); err != nil {
		t.Fatal(err)
	}
	s.pump(context.Background())
	r, _ := rep.last("bob@recv.test")
	if r.State != RcptFailed || r.Code != 550 || !strings.Contains(r.Text, "DMARC") {
		t.Errorf("a letter that fails the policy of its own domain is refused: %+v", r)
	}
	select {
	case l := <-w.got:
		t.Errorf("it must not be delivered: %+v", l.in)
	default:
	}
}

func TestDeferralRetryAndGivingUp(t *testing.T) {
	w, key := newWorld(t)
	var mu sync.Mutex
	busy := true
	w.reject = func(*Inbound) error {
		mu.Lock()
		defer mu.Unlock()
		if busy {
			return ErrTemporary
		}
		return nil
	}
	s, rep, clk := w.sender(t, key, nil)
	if _, err := s.Enqueue("alice@send.test", []string{"bob@recv.test"}, "m_3", bytes.NewReader(letter(t, Address{Local: "bob", Domain: "recv.test"}))); err != nil {
		t.Fatal(err)
	}
	ctx := context.Background()
	s.pump(ctx)
	r, _ := rep.last("bob@recv.test")
	if r.State != RcptDeferred || r.Code != 451 || r.Final {
		t.Fatalf("a server that says "+"\"not now\""+" is tried again: %+v", r)
	}
	q := s.Queue()
	if len(q) != 1 || q[0].Rcpts[0].Next != clk.now().Add(time.Minute).Unix() || q[0].Rcpts[0].Attempts != 1 {
		t.Fatalf("queue: %+v", q)
	}
	// not before its time
	s.pump(ctx)
	if q := s.Queue(); q[0].Rcpts[0].Attempts != 1 {
		t.Errorf("tried too early: %+v", q[0].Rcpts[0])
	}
	// the server is well again
	mu.Lock()
	busy = false
	mu.Unlock()
	clk.add(2 * time.Minute)
	s.pump(ctx)
	r, _ = rep.last("bob@recv.test")
	if r.State != RcptDelivered || !r.Final {
		t.Errorf("after the wait: %+v", r)
	}
	select {
	case <-w.got:
	case <-time.After(5 * time.Second):
		t.Error("no letter")
	}

	// a server that cannot be reached: tried for MaxAge, then given up (and the person is told why)
	w.srv.Close()
	s2, rep2, clk2 := w.sender(t, key, func(c *SenderConfig) { c.MaxAge = 2 * time.Hour })
	if _, err := s2.Enqueue("alice@send.test", []string{"bob@recv.test"}, "m_4", bytes.NewReader(letter(t, Address{Local: "bob", Domain: "recv.test"}))); err != nil {
		t.Fatal(err)
	}
	s2.pump(ctx)
	if r, _ := rep2.last("bob@recv.test"); r.State != RcptDeferred || !strings.Contains(r.Text, "mail.recv.test") {
		t.Fatalf("connection refused is a deferral that names the server: %+v", r)
	}
	for i := 0; i < 20; i++ {
		clk2.add(30 * time.Minute)
		s2.pump(ctx)
	}
	if r, _ := rep2.last("bob@recv.test"); r.State != RcptFailed || !strings.Contains(r.Text, "gave up after 2h") {
		t.Errorf("giving up: %+v", r)
	}
}

func TestOnlyPublicMailServersAreDialled(t *testing.T) {
	w, key := newWorld(t)
	s, rep, _ := w.sender(t, key, func(c *SenderConfig) { c.AllowPrivate = false })
	if _, err := s.Enqueue("alice@send.test", []string{"bob@recv.test"}, "m_5", bytes.NewReader(letter(t, Address{Local: "bob", Domain: "recv.test"}))); err != nil {
		t.Fatal(err)
	}
	s.pump(context.Background())
	if r, _ := rep.last("bob@recv.test"); r.State != RcptFailed || !strings.Contains(r.Text, "not on the Internet") {
		t.Errorf("a domain whose mail server is at 127.0.0.1 is not dialled: %+v", r)
	}
	for _, ip := range []string{"127.0.0.1", "10.1.2.3", "192.168.0.9", "172.16.5.5", "169.254.1.1", "100.64.0.1", "::1", "fe80::1", "fd00::1", "0.0.0.0", "224.0.0.1", "203.0.113.5"} {
		if !notPublic(net.ParseIP(ip)) {
			t.Errorf("%s must not be dialled", ip)
		}
	}
	for _, ip := range []string{"8.8.8.8", "142.250.74.37", "2a00:1450:4001:81b::2005"} {
		if notPublic(net.ParseIP(ip)) {
			t.Errorf("%s is a public address", ip)
		}
	}
}

func TestEnqueueChecks(t *testing.T) {
	w, key := newWorld(t)
	s, _, _ := w.sender(t, key, func(c *SenderConfig) { c.MaxSize = 100; c.MaxQueue = 2 })
	ok := func(id string, rc ...string) error {
		_, err := s.Enqueue("alice@send.test", rc, id, strings.NewReader("Subject: x\r\n\r\nbody"))
		return err
	}
	if err := ok("a", "bob@recv.test"); err != nil {
		t.Fatal(err)
	}
	if err := ok("b", "bob@recv.test", "bob@recv.test", "BOB@recv.test"); err != nil {
		t.Fatal(err)
	}
	if q := s.Queue(); len(q) != 2 || len(q[1].Rcpts) != 1 {
		t.Errorf("the same recipient three ways is one recipient: %+v", q)
	}
	if err := ok("c", "bob@recv.test"); err == nil || !strings.Contains(err.Error(), "full") {
		t.Errorf("a full queue: %v", err)
	}
	if _, err := s.Enqueue("not an address", []string{"bob@recv.test"}, "d", strings.NewReader("x")); err == nil {
		t.Error("a bad sender")
	}
	if err := ok("e"); err == nil {
		t.Error("no recipient")
	}
	if err := ok("f", "bad address"); err == nil {
		t.Error("a bad recipient")
	}
	s2, _, _ := w.sender(t, key, func(c *SenderConfig) { c.MaxSize = 20 })
	if _, err := s2.Enqueue("alice@send.test", []string{"bob@recv.test"}, "g", strings.NewReader(strings.Repeat("x", 100))); !errors.Is(err, ErrTooLarge) {
		t.Errorf("a letter that is too large: %v", err)
	}
	if entries, _ := filepath.Glob(filepath.Join(s2.cfg.SpoolDir, "*")); len(entries) != 0 {
		t.Errorf("nothing is left in the spool: %v", entries)
	}
	// cancelling gives up on what waits
	if !s.Cancel(s.Queue()[0].ID) {
		t.Error("cancel")
	}
	if q := s.Queue(); len(q) != 1 {
		t.Errorf("a cancelled letter is not waiting any more: %+v", q)
	}
}

// A letter that is given up on while a conversation with the server is going on stays given up, whatever the server then says; and
// a letter that did get through has got through, whatever was decided meanwhile.
func TestCancelWhileTheLetterIsOnItsWay(t *testing.T) {
	for _, tc := range []struct {
		name  string
		reply error // what the server answers when it is let go on
		want  string
	}{
		{"not now", ErrTemporary, RcptFailed},
		{"taken", nil, RcptDelivered},
	} {
		t.Run(tc.name, func(t *testing.T) {
			w, key := newWorld(t)
			entered, gate := make(chan struct{}, 1), make(chan struct{})
			w.reject = func(*Inbound) error {
				entered <- struct{}{}
				<-gate
				return tc.reply
			}
			s, rep, _ := w.sender(t, key, nil)
			id, err := s.Enqueue("alice@send.test", []string{"bob@recv.test"}, "m_c", bytes.NewReader(letter(t, Address{Local: "bob", Domain: "recv.test"})))
			if err != nil {
				t.Fatal(err)
			}
			done := make(chan struct{})
			go func() { s.pump(context.Background()); close(done) }()
			select {
			case <-entered:
			case <-time.After(10 * time.Second):
				t.Fatal("the server was not reached")
			}
			if !s.Cancel(id) {
				t.Fatal("cancel")
			}
			close(gate)
			<-done
			if q := s.Queue(); len(q) != 0 {
				t.Errorf("the queue still holds the letter: %+v", q)
			}
			r, _ := rep.last("bob@recv.test")
			if r.State != tc.want || !r.Final {
				t.Errorf("the final state is %+v, want %s", r, tc.want)
			}
			if tc.want == RcptFailed && !strings.Contains(r.Text, "cancelled") {
				t.Errorf("the reason: %q", r.Text)
			}
		})
	}
}

// ---- the receiving side, spoken to by hand ----

func dial(t *testing.T, w *world) *smtp.Client {
	t.Helper()
	conn, err := net.Dial("tcp", fmt.Sprintf("127.0.0.1:%d", w.port))
	if err != nil {
		t.Fatal(err)
	}
	c := smtp.NewClient(conn)
	if err := c.Hello("client.test"); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { c.Close() })
	return c
}

func codeOf(err error) int {
	var se *smtp.SMTPError
	if errors.As(err, &se) {
		return se.Code
	}
	return 0
}

func TestServerRefusesWhatItShould(t *testing.T) {
	w, _ := newWorld(t)
	c := dial(t, w)
	if err := c.Mail("x@elsewhere.test", nil); err != nil {
		t.Fatal(err)
	}
	if err := c.Rcpt("someone@other.test", nil); codeOf(err) != 554 || !strings.Contains(err.Error(), "relay access denied") {
		t.Errorf("not a relay: %v", err)
	}
	if err := c.Rcpt("ghost@recv.test", nil); codeOf(err) != 550 {
		t.Errorf("no such mailbox: %v", err)
	}
	if err := c.Rcpt("not an address", nil); codeOf(err) != 501 {
		t.Errorf("garbage: %v", err)
	}
	if err := c.Rcpt("Bob@RECV.test", nil); err != nil {
		t.Errorf("case does not matter: %v", err)
	}
	if err := c.Rcpt("bob@recv.test", nil); err != nil {
		t.Errorf("the same recipient twice: %v", err)
	}
	if err := c.Mail("with space@x.test", nil); codeOf(err) != 501 {
		t.Errorf("a bad sender: %v", err)
	}
	// a letter that says it is larger than the server takes
	c2 := dial(t, w)
	if err := c2.Mail("x@elsewhere.test", &smtp.MailOptions{Size: 100 << 20}); codeOf(err) != 552 {
		t.Errorf("SIZE: %v", err)
	}
}

func TestServerStripsTheProofsOfOthers(t *testing.T) {
	w, _ := newWorld(t)
	c := dial(t, w)
	if err := c.Mail("x@elsewhere.test", nil); err != nil {
		t.Fatal(err)
	}
	if err := c.Rcpt("bob@recv.test", nil); err != nil {
		t.Fatal(err)
	}
	msg := "Authentication-Results: mail.recv.test; dmarc=pass\r\n  (folded)\r\nReturn-Path: <ceo@bank.test>\r\nX-Mesh-Auth: verified\r\nFrom: Eve <eve@elsewhere.test>\r\nTo: bob@recv.test\r\nSubject: hi\r\n\r\nAuthentication-Results: this one is in the body\r\n"
	if err := c.SendMail("x@elsewhere.test", []string{"bob@recv.test"}, strings.NewReader(msg)); err == nil {
		// (SendMail starts a new transaction on a connection that already has one: Reset first)
		_ = err
	}
	var got letterIn
	select {
	case got = <-w.got:
	case <-time.After(10 * time.Second):
		t.Fatal("no letter")
	}
	text := string(got.raw)
	head, body, _ := strings.Cut(text, "\r\n\r\n")
	if strings.Contains(head, "dmarc=pass") || strings.Contains(head, "ceo@bank.test") || strings.Contains(head, "X-Mesh-Auth") || strings.Contains(head, "folded") {
		t.Errorf("what the sender says about the checks is not believed:\n%s", head)
	}
	if !strings.Contains(body, "this one is in the body") {
		t.Errorf("the body is left alone: %q", body)
	}
	if got.in.Auth.SPF != SPFNone || got.in.Auth.Verdict() == VerdictVerified {
		t.Errorf("a domain with no records is not verified: %+v", got.in.Auth)
	}
}

func TestServerLimits(t *testing.T) {
	w, _ := newWorld(t)
	w.srv.cfg.MaxConnsPerIP = 2
	c1, c2 := dial(t, w), dial(t, w)
	_ = c1
	_ = c2
	conn, err := net.Dial("tcp", fmt.Sprintf("127.0.0.1:%d", w.port))
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close()
	conn.SetReadDeadline(time.Now().Add(5 * time.Second))
	fmt.Fprintf(conn, "EHLO client.test\r\n") // (the server makes the session at the first command)
	said, _ := io.ReadAll(conn)
	if !strings.Contains(string(said), "421 4.7.0 too many connections") {
		t.Errorf("too many connections from one address: %q", said)
	}
	// letters per hour
	w.srv.cfg.MaxConnsPerIP = 10
	w.srv.cfg.MaxMsgsPerIPHour = 2
	w.srv.mu.Lock()
	w.srv.msgs["127.0.0.1"] = []time.Time{time.Now(), time.Now()}
	w.srv.mu.Unlock()
	c3 := dial(t, w)
	if err := c3.Mail("x@elsewhere.test", nil); codeOf(err) != 450 {
		t.Errorf("rate: %v", err)
	}
}

// ---- through a relay ----

type relayBackend struct {
	mu     sync.Mutex
	user   string
	pass   string
	from   string
	rcpts  []string
	data   []byte
	tlsOn  bool
	logins int
}

type relaySession struct{ b *relayBackend }

func (b *relayBackend) NewSession(c *smtp.Conn) (smtp.Session, error) { return &relaySession{b}, nil }
func (s *relaySession) AuthMechanisms() []string                      { return []string{sasl.Plain} }
func (s *relaySession) Auth(mech string) (sasl.Server, error) {
	return sasl.NewPlainServer(func(identity, username, password string) error {
		s.b.mu.Lock()
		defer s.b.mu.Unlock()
		s.b.logins++
		if username != s.b.user || password != s.b.pass {
			return errors.New("bad credentials")
		}
		return nil
	}), nil
}
func (s *relaySession) Reset()        {}
func (s *relaySession) Logout() error { return nil }
func (s *relaySession) Mail(from string, o *smtp.MailOptions) error {
	s.b.mu.Lock()
	s.b.from = from
	s.b.mu.Unlock()
	return nil
}
func (s *relaySession) Rcpt(to string, o *smtp.RcptOptions) error {
	s.b.mu.Lock()
	s.b.rcpts = append(s.b.rcpts, to)
	s.b.mu.Unlock()
	return nil
}
func (s *relaySession) Data(r io.Reader) error {
	b, _ := io.ReadAll(r)
	s.b.mu.Lock()
	s.b.data = b
	s.b.mu.Unlock()
	return nil
}

func TestThroughARelay(t *testing.T) {
	w, key := newWorld(t)
	be := &relayBackend{user: "mesh", pass: "s3cret"}
	srv := smtp.NewServer(be)
	srv.Domain = "relay.test"
	srv.AllowInsecureAuth = true
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	go srv.Serve(ln)
	defer srv.Close()
	port := ln.Addr().(*net.TCPAddr).Port

	s, rep, _ := w.sender(t, key, func(c *SenderConfig) {
		c.Relay = &Relay{Host: "127.0.0.1", Port: port, Username: "mesh", Password: "s3cret", Mode: "plain"}
	})
	if _, err := s.Enqueue("alice@send.test", []string{"bob@recv.test", "carol@gmail.example"}, "m_6", bytes.NewReader(letter(t, Address{Local: "bob", Domain: "recv.test"}))); err != nil {
		t.Fatal(err)
	}
	s.pump(context.Background())
	for _, a := range []string{"bob@recv.test", "carol@gmail.example"} {
		if r, _ := rep.last(a); r.State != RcptDelivered {
			t.Errorf("%s: %+v", a, r)
		}
	}
	be.mu.Lock()
	if be.from != "alice@send.test" || len(be.rcpts) != 2 || be.logins != 1 || !bytes.HasPrefix(be.data, []byte("DKIM-Signature:")) {
		t.Errorf("the relay got: from %q rcpts %v logins %d data %.40q", be.from, be.rcpts, be.logins, be.data)
	}
	be.mu.Unlock()

	// a wrong password is a deferral (somebody has to look at it), not a verdict on the letter
	s2, rep2, _ := w.sender(t, key, func(c *SenderConfig) {
		c.Relay = &Relay{Host: "127.0.0.1", Port: port, Username: "mesh", Password: "wrong", Mode: "plain"}
	})
	if _, err := s2.Enqueue("alice@send.test", []string{"bob@recv.test"}, "m_7", bytes.NewReader(letter(t, Address{Local: "bob", Domain: "recv.test"}))); err != nil {
		t.Fatal(err)
	}
	s2.pump(context.Background())
	if r, _ := rep2.last("bob@recv.test"); r.State == RcptDelivered {
		t.Errorf("a wrong password must not deliver: %+v", r)
	}
}
