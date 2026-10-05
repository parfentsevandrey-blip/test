package mailgw

import (
	"bytes"
	"context"
	"crypto/tls"
	"io"
	"log/slog"
	"net"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/blob"
	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/inetmail"
	"github.com/parfentsevandrey-blip/test/svoi/internal/mail"
	"github.com/parfentsevandrey-blip/test/svoi/internal/mesh"
	"github.com/parfentsevandrey-blip/test/svoi/internal/meshtest"
	"github.com/parfentsevandrey-blip/test/svoi/internal/store"
)

var quiet = slog.New(slog.NewTextHandler(io.Discard, nil))

type box struct {
	node  *mesh.Node
	m     *mail.Manager
	blobs *blob.Store
	db    *store.DB
}

func newBox(t *testing.T, n *mesh.Node) *box {
	t.Helper()
	db, err := store.Open(filepath.Join(n.Dir(), "themesh.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { db.Close() })
	bs, err := blob.Open(filepath.Join(n.Dir(), "blobs"))
	if err != nil {
		t.Fatal(err)
	}
	m, err := mail.New(n, db, bs, nil)
	if err != nil {
		t.Fatal(err)
	}
	m.Register()
	bs.RegisterRPC(n)
	ctx, cancel := context.WithCancel(context.Background())
	t.Cleanup(cancel)
	go m.Run(ctx)
	return &box{node: n, m: m, blobs: bs, db: db}
}

// remoteMTA is the mail server of another domain on the Internet (gmail.test), here on loopback.
type remoteMTA struct {
	srv  *inetmail.Server
	port int
	mu   sync.Mutex
	got  []*received
}

type received struct {
	in  *inetmail.Inbound
	raw []byte
}

func (r *remoteMTA) letters() []*received {
	r.mu.Lock()
	defer r.mu.Unlock()
	return append([]*received(nil), r.got...)
}

func startRemote(t *testing.T, dns inetmail.Resolver) *remoteMTA {
	t.Helper()
	r := &remoteMTA{}
	certPEM, keyPEM, err := inetmail.SelfSignedCert("mail.gmail.test")
	if err != nil {
		t.Fatal(err)
	}
	cert, err := tls.X509KeyPair(certPEM, keyPEM)
	if err != nil {
		t.Fatal(err)
	}
	srv, err := inetmail.NewServer(inetmail.ServerConfig{
		Hostname: "mail.gmail.test", Resolver: dns, TLS: &tls.Config{Certificates: []tls.Certificate{cert}}, SpoolDir: t.TempDir(), Log: quiet,
		IsLocalDomain: func(d string) bool { return d == "gmail.test" },
		Accept: func(a inetmail.Address) error {
			if a.Local != "bob" {
				return inetmail.ErrNoMailbox
			}
			return nil
		},
		Deliver: func(ctx context.Context, in *inetmail.Inbound) error {
			f, err := in.Open()
			if err != nil {
				return err
			}
			raw, _ := io.ReadAll(f)
			f.Close()
			r.mu.Lock()
			r.got = append(r.got, &received{in, raw})
			r.mu.Unlock()
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
	r.srv, r.port = srv, ln.Addr().(*net.TCPAddr).Port
	go srv.Serve(ln)
	t.Cleanup(func() { srv.Close() })
	return r
}

type setup struct {
	laptop, phone, home *box
	gw                  *Gateway
	dns                 *inetmail.FakeDNS
	remote              *remoteMTA
	cfg                 Config
}

// publishPlan puts the records the gateway asks for into the DNS, as its owner would at the registrar.
func publishPlan(t *testing.T, gw *Gateway, dns *inetmail.FakeDNS) {
	t.Helper()
	v, err := gw.DNS(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	for _, rc := range v.Report.Records {
		switch rc.Type {
		case "MX":
			dns.AddMX(rc.Name, strings.TrimSuffix(strings.Fields(rc.Value)[1], "."), 10)
		case "A", "AAAA":
			dns.AddA(rc.Name, rc.Value)
		case "TXT":
			dns.AddTXT(rc.Name, rc.Value)
		case "PTR":
			dns.AddPTR("127.0.0.1", strings.TrimSuffix(rc.Value, "."))
		}
	}
}

func newSetup(t *testing.T) *setup {
	t.Helper()
	h := meshtest.New(t)
	a := h.Public("laptop", "198.51.100.1")
	b := h.Public("phone", "198.51.100.2")
	c := h.Public("home", "198.51.100.3")
	h.Mesh(a, b, c)
	s := &setup{laptop: newBox(t, a), phone: newBox(t, b), home: newBox(t, c), dns: inetmail.NewFakeDNS()}
	s.remote = startRemote(t, s.dns)
	s.dns.AddMX("gmail.test", "mail.gmail.test", 10)
	s.dns.AddA("mail.gmail.test", "127.0.0.1")
	gw, err := New(Options{
		Dir: c.Dir(), Node: c, Mail: s.home.m, Blobs: s.home.blobs, DB: s.home.db, Log: quiet,
		Resolver: s.dns, SMTPPort: s.remote.port, AllowPrivate: true,
	})
	if err != nil {
		t.Fatal(err)
	}
	s.gw = gw
	s.home.m.SetGateway(gw)
	ctx, cancel := context.WithCancel(context.Background())
	t.Cleanup(cancel)
	go gw.Run(ctx)
	s.cfg = Config{
		Enabled: true, Domain: "example.org", Listen: "127.0.0.1:0", PublicIPv4: "127.0.0.1",
		Mailboxes: []Mailbox{{Name: "andrey", Devices: []identity.ID{a.ID(), b.ID()}}, {Name: "anna", Devices: []identity.ID{b.ID()}}},
	}
	if err := gw.SetConfig(s.cfg); err != nil {
		t.Fatal(err)
	}
	meshtest.WaitFor(t, 10*time.Second, "the gateway to listen", func() bool { return gw.Status().Listening })
	publishPlan(t, gw, s.dns)
	return s
}

func (s *setup) smtpPort(t *testing.T) int {
	t.Helper()
	_, p, err := net.SplitHostPort(s.gw.Status().ListenAddr)
	if err != nil {
		t.Fatal(err)
	}
	n, _ := strconv.Atoi(p)
	return n
}

func TestSetup(t *testing.T) {
	s := newSetup(t)
	st := s.gw.Status()
	if !st.Enabled || !st.Running || !st.Listening || st.ListenError != "" || st.PublicIP != "127.0.0.1" || st.Selector != "mesh" {
		t.Errorf("status: %+v", st)
	}
	v, err := s.gw.DNS(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	if !v.Report.Ready {
		t.Errorf("records published as planned must check out: %+v", v.Report)
	}
	// what each device may use
	if info, ok := s.gw.Info(s.laptop.node.ID()); !ok || len(info.Mailboxes) != 1 || info.Mailboxes[0] != "andrey@example.org" || !info.Ready {
		t.Errorf("laptop: %+v %v", info, ok)
	}
	if info, _ := s.gw.Info(s.phone.node.ID()); len(info.Mailboxes) != 2 {
		t.Errorf("phone has two mailboxes: %+v", info)
	}
	if _, ok := s.gw.Info(s.home.node.ID()); ok {
		t.Error("the gateway has no mailbox of its own here")
	}
	if err := s.gw.CanSend(s.laptop.node.ID(), "ANDREY@example.org"); err != nil {
		t.Errorf("case does not matter: %v", err)
	}
	if err := s.gw.CanSend(s.laptop.node.ID(), "anna@example.org"); err == nil {
		t.Error("the laptop has no mailbox anna")
	}
	// the key is kept: the same record next time
	k1 := loadKey(t, s.gw)
	if err := s.gw.SetConfig(s.cfg); err != nil {
		t.Fatal(err)
	}
	if loadKey(t, s.gw) != k1 {
		t.Error("the key of the domain must not change when the setup is saved again")
	}
}

func loadKey(t *testing.T, g *Gateway) string {
	t.Helper()
	k, err := loadOrCreateKey(g.dir, "mesh")
	if err != nil {
		t.Fatal(err)
	}
	return k.DNSValue()
}

// theInternetSends delivers a letter from alice@gmail.test to the gateway the way a mail server would.
func (s *setup) theInternetSends(t *testing.T, rcpt string, attach bool) {
	t.Helper()
	key, err := inetmail.NewDKIMKey("g1")
	if err != nil {
		t.Fatal(err)
	}
	s.dns.AddTXT(key.RecordName("gmail.test"), key.DNSValue())
	s.dns.SetTXT("gmail.test", "v=spf1 ip4:127.0.0.1 -all")
	s.dns.SetTXT("_dmarc.gmail.test", "v=DMARC1; p=reject")
	db, err := store.Open(filepath.Join(t.TempDir(), "gmail.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { db.Close() })
	snd, err := inetmail.NewSender(inetmail.SenderConfig{
		DB: db, SpoolDir: t.TempDir(), Hostname: "mail.gmail.test", Resolver: s.dns, Port: s.smtpPort(t), AllowPrivate: true, Log: quiet,
		DKIM: func(string) *inetmail.DKIMKey { return key },
	})
	if err != nil {
		t.Fatal(err)
	}
	m := &inetmail.OutMessage{
		From: inetmail.Address{Name: "Alice Example", Local: "alice", Domain: "gmail.test"}, To: []inetmail.Address{{Local: strings.SplitN(rcpt, "@", 2)[0], Domain: "example.org"}},
		Subject: "Привет из Gmail", Text: "Hello from the Internet", HTML: `<p>Hello <b>Andrey</b><img src="https://tracker.example/p.gif"><script>alert(1)</script></p>`,
	}
	if attach {
		m.Attachments = []inetmail.OutAttachment{{Name: "scan.pdf", Mime: "application/pdf", Open: func() (io.ReadCloser, error) {
			return io.NopCloser(bytes.NewReader(bytes.Repeat([]byte("%PDF"), 5000))), nil
		}}}
	}
	var buf bytes.Buffer
	if _, err := inetmail.BuildMIME(&buf, m); err != nil {
		t.Fatal(err)
	}
	if _, err := snd.Enqueue("alice@gmail.test", []string{rcpt}, "x", &buf); err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go snd.Run(ctx)
	meshtest.WaitFor(t, 20*time.Second, "the letter to leave the other side", func() bool { return len(snd.Queue()) == 0 })
}

func TestLetterFromGmail(t *testing.T) {
	s := newSetup(t)
	s.theInternetSends(t, "andrey@example.org", true)
	for _, b := range []*box{s.laptop, s.phone} {
		meshtest.WaitFor(t, 20*time.Second, "the letter on "+b.node.Self().Name, func() bool { return b.m.List(mail.FolderInbox, "", 50, 0).Total == 1 })
		it := b.m.List(mail.FolderInbox, "", 50, 0).Items[0]
		if it.From.Name != "Alice Example" || it.Subject != "Привет из Gmail" || it.Ext == nil || it.Ext.From.Addr != "alice@gmail.test" || it.Ext.Mailbox != "andrey@example.org" ||
			it.Ext.Verdict != inetmail.VerdictVerified || it.Ext.SPF != "pass" || it.Ext.DMARC != "pass" || !strings.Contains(it.Ext.DKIM, "gmail.test") {
			t.Errorf("%s: %+v / %+v", b.node.Self().Name, it, it.Ext)
		}
		msg, _ := b.m.Get(it.ID)
		if msg.Body != "Hello from the Internet" || !strings.Contains(msg.HTML, "<b>Andrey</b>") || strings.Contains(msg.HTML, "script") || len(msg.Attachments) != 1 || msg.Attachments[0].Name != "scan.pdf" {
			t.Errorf("%s: the letter: %q %q %+v", b.node.Self().Name, msg.Body, msg.HTML, msg.Attachments)
		}
		if msg.Ext.Images != 1 {
			t.Errorf("the picture on the Internet is counted, not loaded: %d", msg.Ext.Images)
		}
		meshtest.WaitFor(t, 20*time.Second, "the attachment", func() bool { m, _ := b.m.Get(it.ID); return m.Attachments[0].State == mail.AttReady })
	}
	// the other mailbox did not get it, and nobody got it twice
	if s.phone.m.List(mail.FolderInbox, "", 50, 0).Total != 1 {
		t.Error("once")
	}
}

func TestLetterToAMailboxThatDoesNotExistIsRefused(t *testing.T) {
	s := newSetup(t)
	conn, err := net.Dial("tcp", s.gw.Status().ListenAddr)
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close()
	conn.SetDeadline(time.Now().Add(10 * time.Second))
	buf := make([]byte, 4096)
	conn.Read(buf)
	io.WriteString(conn, "EHLO x.test\r\nMAIL FROM:<a@gmail.test>\r\nRCPT TO:<nobody@example.org>\r\nRCPT TO:<x@elsewhere.test>\r\nRCPT TO:<postmaster@example.org>\r\nQUIT\r\n")
	var all []byte
	for {
		n, err := conn.Read(buf)
		all = append(all, buf[:n]...)
		if err != nil {
			break
		}
	}
	out := string(all)
	if !strings.Contains(out, "550 5.1.1") || !strings.Contains(out, "554 5.7.1 relay access denied") || strings.Count(out, "gets this") != 1 {
		t.Errorf("an unknown mailbox is refused, a foreign domain is not relayed, postmaster is always taken:\n%s", out)
	}
}

func TestLetterToGmail(t *testing.T) {
	s := newSetup(t)
	meshtest.WaitFor(t, 20*time.Second, "the laptop to learn its mailbox", func() bool { return len(s.laptop.m.Gateways()) == 1 })
	sha, size, _ := s.laptop.blobs.Put(bytes.NewReader([]byte("quarterly numbers")))
	_ = size
	s.laptop.m.RegisterUpload(sha, "numbers.txt", "text/plain", 17)
	id, err := s.laptop.m.Send(mail.SendInput{Kind: "mail", Subject: "Отчёт", Body: "Numbers attached", Attach: []string{sha}, ExtTo: []string{"Bob <bob@gmail.test>"}, ExtCc: []string{"ghost@gmail.test"}})
	if err != nil {
		t.Fatal(err)
	}
	meshtest.WaitFor(t, 30*time.Second, "the reports to come back", func() bool {
		m, _ := s.laptop.m.Get(id)
		if m == nil || m.Ext == nil || len(m.Ext.Out) != 2 {
			return false
		}
		return m.Ext.Out[0].State == inetmail.RcptDelivered && m.Ext.Out[1].State == inetmail.RcptFailed
	})
	m, _ := s.laptop.m.Get(id)
	if m.Ext.Out[1].Code != 550 || !strings.Contains(m.Ext.Out[1].Text, "5.1.1") {
		t.Errorf("the refusal of the other side is told in its words: %+v", m.Ext.Out[1])
	}
	got := s.remote.letters()
	if len(got) != 1 {
		t.Fatalf("the other side got %d letters", len(got))
	}
	in := got[0].in
	if in.MailFrom != "andrey@example.org" || len(in.Rcpts) != 1 || in.Rcpts[0].Addr() != "bob@gmail.test" || !in.TLS {
		t.Errorf("envelope: %+v", in)
	}
	if in.Auth.SPF != inetmail.SPFPass || in.Auth.DMARC.Result != "pass" || in.Auth.Verdict() != inetmail.VerdictVerified {
		t.Errorf("what Gmail would see of the sender: %+v", in.Auth)
	}
	p, err := inetmail.ParseMessage(bytes.NewReader(got[0].raw), nil, inetmail.ParseLimits{})
	if err != nil {
		t.Fatal(err)
	}
	if p.Subject != "Отчёт" || p.Text != "Numbers attached" || len(p.Attachments) != 1 || p.Attachments[0].Name != "numbers.txt" || p.Attachments[0].Size != 17 ||
		len(p.From) != 1 || p.From[0].Addr() != "andrey@example.org" || len(p.To) != 1 || len(p.Cc) != 1 || !strings.HasSuffix(p.MessageID, "@example.org") {
		t.Errorf("the letter as it arrived: %+v", p)
	}
	if !strings.Contains(string(got[0].raw), "X-Mailer: The Mesh") {
		t.Error("a letter says what wrote it")
	}
}

func TestLetterToAMailboxOfTheSameDomainStaysInTheMesh(t *testing.T) {
	s := newSetup(t)
	meshtest.WaitFor(t, 20*time.Second, "mailbox", func() bool { return len(s.laptop.m.Gateways()) == 1 })
	id, err := s.laptop.m.Send(mail.SendInput{Kind: "mail", Subject: "To Anna", Body: "hi anna", ExtTo: []string{"anna@example.org", "nobody@example.org"}})
	if err != nil {
		t.Fatal(err)
	}
	meshtest.WaitFor(t, 20*time.Second, "delivery inside the mesh", func() bool {
		m, _ := s.laptop.m.Get(id)
		return m != nil && m.Ext != nil && len(m.Ext.Out) == 2 && m.Ext.Out[0].State == inetmail.RcptDelivered && m.Ext.Out[1].State == inetmail.RcptFailed
	})
	meshtest.WaitFor(t, 20*time.Second, "Anna's phone", func() bool { return s.phone.m.List(mail.FolderInbox, "", 50, 0).Total == 1 })
	it := s.phone.m.List(mail.FolderInbox, "", 50, 0).Items[0]
	if it.Subject != "To Anna" || it.Ext == nil || it.Ext.Mailbox != "anna@example.org" || it.Ext.Verdict != inetmail.VerdictVerified || it.Ext.From.Addr != "andrey@example.org" {
		t.Errorf("%+v / %+v", it, it.Ext)
	}
	if len(s.remote.letters()) != 0 {
		t.Error("nothing went out to the Internet")
	}
}

func TestADeviceCannotWriteFromAMailboxItDoesNotHave(t *testing.T) {
	s := newSetup(t)
	meshtest.WaitFor(t, 20*time.Second, "mailbox", func() bool { return len(s.laptop.m.Gateways()) == 1 })
	// the gateway knows better than the device what the device may do
	err := s.gw.SendOut(mail.OutLetter{ID: "m_x", Sender: s.laptop.node.ID(), From: mail.ExtAddr{Addr: "anna@example.org"}, To: []mail.ExtAddr{{Addr: "bob@gmail.test"}}, Subject: "x", MessageID: "a@example.org", Created: time.Now().Unix()})
	if err == nil || !strings.Contains(err.Error(), "no mailbox") {
		t.Errorf("another person's mailbox: %v", err)
	}
	if len(s.gw.Queue()) != 0 {
		t.Error("nothing is queued")
	}
}

func TestLimitsPerDevice(t *testing.T) {
	s := newSetup(t)
	now := time.Now()
	s.gw.now = func() time.Time { return now }
	for i := 0; i < maxLettersPerDevicePerDay; i++ {
		if err := s.gw.takeRate(s.laptop.node.ID(), 1); err != nil {
			t.Fatalf("letter %d: %v", i, err)
		}
	}
	if err := s.gw.takeRate(s.laptop.node.ID(), 1); err == nil || !strings.Contains(err.Error(), "limit") {
		t.Errorf("the 201st letter of a day: %v", err)
	}
	if err := s.gw.takeRate(s.phone.node.ID(), 1); err != nil {
		t.Errorf("another device is not affected: %v", err)
	}
	now = now.Add(25 * time.Hour)
	if err := s.gw.takeRate(s.laptop.node.ID(), 1); err != nil {
		t.Errorf("a day later: %v", err)
	}
}

func TestConfigValidation(t *testing.T) {
	id := func(s string) identity.ID { x, _ := identity.ParseID(s); return x }
	_ = id
	self := identity.ID{1}
	known := func(d identity.ID) bool { return d == self }
	bad := map[string]Config{
		"no domain":   {Enabled: true, Mailboxes: []Mailbox{{Name: "ab", Devices: []identity.ID{self}}}},
		"no mailbox":  {Enabled: true, Domain: "example.org"},
		"bad domain":  {Domain: "not a domain"},
		"one label":   {Domain: "localhost"},
		"bad listen":  {Domain: "example.org", Listen: "25"},
		"bad port":    {Domain: "example.org", Listen: ":99999"},
		"bad mailbox": {Domain: "example.org", Mailboxes: []Mailbox{{Name: "A B", Devices: []identity.ID{self}}}},
		"reserved":    {Domain: "example.org", Mailboxes: []Mailbox{{Name: "postmaster", Devices: []identity.ID{self}}}},
		"twice":       {Domain: "example.org", Mailboxes: []Mailbox{{Name: "ab", Devices: []identity.ID{self}}, {Name: "AB", Devices: []identity.ID{self}}}},
		"no devices":  {Domain: "example.org", Mailboxes: []Mailbox{{Name: "ab"}}},
		"stranger":    {Domain: "example.org", Mailboxes: []Mailbox{{Name: "ab", Devices: []identity.ID{{9}}}}},
		"relay mode":  {Domain: "example.org", Relay: &RelayConfig{Host: "smtp.example.net", Mode: "carrier-pigeon"}},
		"relay port":  {Domain: "example.org", Relay: &RelayConfig{Host: "smtp.example.net", Port: 70000}},
		"bad ip":      {Domain: "example.org", PublicIPv4: "2001:db8::1"},
		"selector":    {Domain: "example.org", DKIMSelector: "Bad Selector"},
		"relay spf":   {Domain: "example.org", Relay: &RelayConfig{Host: "smtp.example.net", SPFInclude: "not a domain"}},
	}
	for name, c := range bad {
		if err := c.Validate(known); err == nil {
			t.Errorf("%s: must be refused", name)
		}
	}
	ok := Config{
		Enabled: true, Domain: "Example.ORG", Host: "Mail.Example.org", Listen: "127.0.0.1:2525", DKIMSelector: "Mesh2",
		Mailboxes: []Mailbox{{Name: "Andrey", Devices: []identity.ID{self, self}}}, PublicIPv4: " 203.0.113.7 ",
		Relay: &RelayConfig{Host: "smtp.example.net", Username: "u"},
	}
	if err := ok.Validate(known); err != nil {
		t.Fatal(err)
	}
	if ok.Domain != "example.org" || ok.Host != "mail.example.org" || ok.Mailboxes[0].Name != "andrey" || len(ok.Mailboxes[0].Devices) != 1 || ok.PublicIPv4 != "203.0.113.7" ||
		ok.DKIMSelector != "mesh2" || ok.Relay.Port != 587 || ok.Relay.Mode != "starttls" {
		t.Errorf("a valid setup is put in its canonical form: %+v relay %+v", ok, ok.Relay)
	}
	// a relay with no host is no relay
	c := Config{Domain: "example.org", Relay: &RelayConfig{Host: "  "}}
	if err := c.Validate(known); err != nil || c.Relay != nil {
		t.Errorf("%v %+v", err, c.Relay)
	}
}

func TestRelayPasswordIsKept(t *testing.T) {
	s := newSetup(t)
	c := s.cfg
	c.Relay = &RelayConfig{Host: "smtp.example.net", Port: 587, Username: "mesh", Password: "s3cret", Mode: "starttls"}
	if err := s.gw.SetConfig(c); err != nil {
		t.Fatal(err)
	}
	if got := s.gw.Config(); got.Relay == nil || got.Relay.Password != "" || !s.gw.RelayPasswordSet() {
		t.Errorf("the interface never gets the password back: %+v", got.Relay)
	}
	// saved again from what the interface knows (no password): the one kept stays
	c2 := s.gw.Config()
	if err := s.gw.SetConfig(c2); err != nil {
		t.Fatal(err)
	}
	if !s.gw.RelayPasswordSet() {
		t.Error("the password must survive saving the setup without it")
	}
	// another account is another password
	c3 := s.gw.Config()
	c3.Relay.Username = "someone-else"
	if err := s.gw.SetConfig(c3); err != nil {
		t.Fatal(err)
	}
	if s.gw.RelayPasswordSet() {
		t.Error("the password of another account is not carried over")
	}
	// switched off: nothing listens, nobody has a mailbox
	c4 := s.gw.Config()
	c4.Enabled = false
	if err := s.gw.SetConfig(c4); err != nil {
		t.Fatal(err)
	}
	if st := s.gw.Status(); st.Listening || st.Running {
		t.Errorf("off is off: %+v", st)
	}
	if _, ok := s.gw.Info(s.laptop.node.ID()); ok {
		t.Error("a gateway that is off offers nothing")
	}
}
