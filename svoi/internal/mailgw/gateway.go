package mailgw

import (
	"bytes"
	"context"
	"crypto/tls"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/blob"
	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/inetmail"
	"github.com/parfentsevandrey-blip/test/svoi/internal/mail"
	"github.com/parfentsevandrey-blip/test/svoi/internal/mesh"
	"github.com/parfentsevandrey-blip/test/svoi/internal/store"
)

// Options are what the gateway is built from.
type Options struct {
	Dir   string // the data directory of the device; the gateway keeps its files in <Dir>/mailgw
	Node  *mesh.Node
	Mail  *mail.Manager
	Blobs *blob.Store
	DB    *store.DB
	Log   *slog.Logger

	// The rest is for the tests: a DNS of their own, another port to send to, a way to reach loopback.
	Resolver     inetmail.Resolver
	SMTPPort     int
	AllowPrivate bool
	Now          func() time.Time
}

// What one device may send in a day (a stolen device must not turn the gateway into a source of spam).
const (
	maxLettersPerDevicePerDay = 200
	maxRcptsPerDay            = 2000
)

// Gateway is the mail gateway of this device.
type Gateway struct {
	opts Options
	dir  string
	log  *slog.Logger
	now  func() time.Time

	mu        sync.Mutex
	cfg       Config
	key       *inetmail.DKIMKey
	ctx       context.Context
	cancel    context.CancelFunc // stops the server and the sender that run now
	srv       *inetmail.Server
	ln        net.Listener
	snd       *inetmail.Sender
	listenErr string
	listenWhy string // listenErr as a kind the interface can explain in words: "permission", "inuse" or "other"
	byDevice  map[identity.ID][]time.Time
	rcptsDay  []time.Time
}

// New loads the setup of the gateway; Run starts it.
func New(opts Options) (*Gateway, error) {
	if opts.Dir == "" || opts.Node == nil || opts.Mail == nil || opts.Blobs == nil || opts.DB == nil {
		return nil, errors.New("mailgw: not configured")
	}
	dir := filepath.Join(opts.Dir, "mailgw")
	if err := identity.EnsurePrivateDir(dir); err != nil {
		return nil, err
	}
	cfg, err := loadConfig(dir)
	if err != nil {
		return nil, err
	}
	if opts.Resolver == nil {
		opts.Resolver = inetmail.SystemResolver
	}
	if opts.Log == nil {
		opts.Log = slog.Default()
	}
	g := &Gateway{opts: opts, dir: dir, log: opts.Log, now: opts.Now, cfg: cfg, byDevice: map[identity.ID][]time.Time{}}
	if g.now == nil {
		g.now = time.Now
	}
	return g, nil
}

// Run starts the gateway if it is switched on, and keeps it going until ctx ends.
func (g *Gateway) Run(ctx context.Context) {
	g.mu.Lock()
	g.ctx = ctx
	g.applyLocked()
	g.mu.Unlock()
	<-ctx.Done()
	g.mu.Lock()
	g.stopLocked()
	g.mu.Unlock()
}

// Config returns the setup (without the password of the mail service).
func (g *Gateway) Config() Config {
	g.mu.Lock()
	defer g.mu.Unlock()
	c := g.cfg
	c.Mailboxes = append([]Mailbox(nil), c.Mailboxes...)
	if c.Relay != nil {
		r := *c.Relay
		r.Password = ""
		c.Relay = &r
	}
	return c
}

// RelayPasswordSet tells whether a password of the mail service is kept.
func (g *Gateway) RelayPasswordSet() bool {
	g.mu.Lock()
	defer g.mu.Unlock()
	return g.cfg.Relay != nil && g.cfg.Relay.Password != ""
}

// SetConfig checks, saves and applies a new setup. A relay without a password keeps the password it had (the interface never gets it).
func (g *Gateway) SetConfig(c Config) error {
	self := g.opts.Node.ID()
	if err := c.Validate(func(id identity.ID) bool { return id == self || g.opts.Node.Peer(id) != nil }); err != nil {
		return err
	}
	g.mu.Lock()
	defer g.mu.Unlock()
	if c.Relay != nil && c.Relay.Password == "" && g.cfg.Relay != nil && g.cfg.Relay.Host == c.Relay.Host && g.cfg.Relay.Username == c.Relay.Username {
		c.Relay.Password = g.cfg.Relay.Password
	}
	if err := saveConfig(g.dir, c); err != nil {
		return err
	}
	g.cfg = c
	g.applyLocked()
	return nil
}

// ---- starting and stopping ----

func (g *Gateway) stopLocked() {
	if g.cancel != nil {
		g.cancel()
		g.cancel = nil
	}
	if g.srv != nil {
		_ = g.srv.Close()
		g.srv = nil
	}
	g.ln = nil
	g.snd = nil
	g.listenErr, g.listenWhy = "", ""
}

// applyLocked brings the running parts in line with the setup: nothing when it is off, a sender and a server when it is on. A server that
// cannot listen (the port is taken, or needs rights this program does not have) does not stop the sender: the letters of the devices
// still go out.
func (g *Gateway) applyLocked() {
	g.stopLocked()
	if g.ctx == nil || !g.cfg.Enabled || g.ctx.Err() != nil {
		return
	}
	cfg := g.cfg
	key, err := loadOrCreateKey(g.dir, cfg.selector())
	if err != nil {
		g.listenErr = "the key of the signature cannot be made: " + err.Error()
		g.listenWhy = "other"
		return
	}
	g.key = key
	ctx, cancel := context.WithCancel(g.ctx)
	g.cancel = cancel

	scfg := inetmail.SenderConfig{
		DB: g.opts.DB, SpoolDir: filepath.Join(g.dir, "queue"), Hostname: cfg.host(), Resolver: g.opts.Resolver,
		DKIM: func(domain string) *inetmail.DKIMKey {
			if domain == cfg.Domain {
				return key
			}
			return nil
		},
		Report: g.report, Log: g.log, Port: g.opts.SMTPPort, AllowPrivate: g.opts.AllowPrivate, Now: g.opts.Now,
	}
	if r := cfg.Relay; r != nil {
		scfg.Relay = &inetmail.Relay{Host: r.Host, Port: r.Port, Username: r.Username, Password: r.Password, Mode: r.Mode}
	}
	snd, err := inetmail.NewSender(scfg)
	if err != nil {
		g.listenErr = "the queue of outgoing letters cannot be opened: " + err.Error()
		g.listenWhy = "other"
		cancel()
		g.cancel = nil
		return
	}
	g.snd = snd
	go snd.Run(ctx)

	cert, err := inetmail.LoadOrCreateCert(g.dir, cfg.host())
	if err != nil {
		g.listenErr = "the certificate of the mail server cannot be made: " + err.Error()
		g.listenWhy = "other"
		return
	}
	srv, err := inetmail.NewServer(inetmail.ServerConfig{
		Hostname: cfg.host(), Resolver: g.opts.Resolver, TLS: &tls.Config{Certificates: []tls.Certificate{cert}, MinVersion: tls.VersionTLS12},
		SpoolDir: filepath.Join(g.dir, "spool"), Log: g.log,
		IsLocalDomain: func(d string) bool { return d == cfg.Domain },
		Accept:        g.accept, Deliver: g.deliver,
	})
	if err != nil {
		g.listenErr, g.listenWhy = err.Error(), "other"
		return
	}
	ln, err := net.Listen("tcp", cfg.listen())
	if err != nil {
		g.listenErr, g.listenWhy = describeListenError(err, cfg.listen())
		g.log.Warn("mail gateway: the server cannot listen", "addr", cfg.listen(), "err", err)
		return
	}
	g.srv, g.ln = srv, ln
	go func() {
		if err := srv.Serve(ln); err != nil {
			g.log.Warn("mail gateway: the server stopped", "err", err)
		}
	}()
	g.log.Info("mail gateway: listening", "addr", ln.Addr().String(), "domain", cfg.Domain, "host", cfg.host())
}

func describeListenError(err error, addr string) (text, kind string) {
	switch {
	case errors.Is(err, os.ErrPermission) || strings.Contains(err.Error(), "permission denied"):
		return fmt.Sprintf("%s: a program may take the port 25 only with the rights of an administrator; run it with them (or give it the right to bind low ports), or listen on another port and let the router forward 25 to it", addr), "permission"
	case strings.Contains(err.Error(), "address already in use"):
		return fmt.Sprintf("%s: the port is taken by another program (a mail server of this computer?)", addr), "inuse"
	}
	return err.Error(), "other"
}

// Status is what the interface shows about the running gateway.
type Status struct {
	Enabled     bool   `json:"enabled"`
	Running     bool   `json:"running"` // the sender works (letters can go out)
	Listening   bool   `json:"listening"`
	ListenAddr  string `json:"listenAddr,omitempty"`
	ListenError string `json:"listenError,omitempty"`
	// ListenErrorKind is what ListenError is about, for the interface to say in its own words: "permission" (the port needs rights), "inuse" or "other".
	ListenErrorKind string `json:"listenErrorKind,omitempty"`
	Relay           bool   `json:"relay"`
	Queue           int    `json:"queue"`
	Selector        string `json:"selector"`
	DetectedIP      string `json:"detectedIPv4,omitempty"`
	PublicIP        string `json:"publicIPv4,omitempty"` // the one in use: the setting, else what the node found out
}

// Status describes the gateway now.
func (g *Gateway) Status() Status {
	g.mu.Lock()
	cfg, snd, ln, lerr, lwhy := g.cfg, g.snd, g.ln, g.listenErr, g.listenWhy
	g.mu.Unlock()
	st := Status{Enabled: cfg.Enabled, Running: snd != nil, Listening: ln != nil, ListenError: lerr, ListenErrorKind: lwhy, Relay: cfg.Relay != nil, Selector: cfg.selector()}
	if ln != nil {
		st.ListenAddr = ln.Addr().String()
	}
	if snd != nil {
		st.Queue = len(snd.Queue())
	}
	st.DetectedIP = g.detectedIP()
	st.PublicIP = cfg.PublicIPv4
	if st.PublicIP == "" {
		st.PublicIP = st.DetectedIP
	}
	return st
}

// detectedIP is the public IPv4 address that the node has learned about itself (from STUN, from what the others see, from the router).
func (g *Gateway) detectedIP() string {
	for _, e := range g.opts.Node.Self().Endpoints {
		if e.Kind != "stun" && e.Kind != "observed" && e.Kind != "mapped" {
			continue
		}
		host, _, err := net.SplitHostPort(e.Addr)
		if err != nil {
			continue
		}
		if ip := net.ParseIP(host); ip != nil && ip.To4() != nil && inetmail.IsPublicIP(ip) {
			return ip.String()
		}
	}
	return ""
}

// DNSView is the records a domain needs and what the real DNS says about them.
type DNSView struct {
	Domain   string             `json:"domain"`
	Host     string             `json:"host"`
	PublicIP string             `json:"publicIPv4,omitempty"`
	Relay    bool               `json:"relay"`
	Report   inetmail.DNSReport `json:"report"`
}

// DNS checks the records of the domain against the real DNS.
func (g *Gateway) DNS(ctx context.Context) (DNSView, error) {
	g.mu.Lock()
	cfg := g.cfg
	g.mu.Unlock()
	if cfg.Domain == "" {
		return DNSView{}, errors.New("name the domain first")
	}
	key, err := loadOrCreateKey(g.dir, cfg.selector())
	if err != nil {
		return DNSView{}, err
	}
	st := g.Status()
	dc := inetmail.DNSConfig{Domain: cfg.Domain, MailHost: cfg.host(), DKIM: key, Relay: cfg.Relay != nil}
	if ip := net.ParseIP(st.PublicIP); ip != nil {
		dc.IPv4 = []net.IP{ip}
	}
	if cfg.Relay != nil {
		dc.SPFInclude = cfg.Relay.SPFInclude
	}
	return DNSView{Domain: cfg.Domain, Host: cfg.host(), PublicIP: st.PublicIP, Relay: cfg.Relay != nil, Report: inetmail.CheckDNS(ctx, g.opts.Resolver, dc)}, nil
}

// Queue lists the letters that are on their way to the Internet.
func (g *Gateway) Queue() []inetmail.QueueEntry {
	g.mu.Lock()
	snd := g.snd
	g.mu.Unlock()
	if snd == nil {
		return nil
	}
	return snd.Queue()
}

// RetryNow tries the letters that wait for another attempt at once.
func (g *Gateway) RetryNow() {
	g.mu.Lock()
	snd := g.snd
	g.mu.Unlock()
	if snd != nil {
		snd.RetryNow()
	}
}

// Cancel gives up on a letter of the queue.
func (g *Gateway) Cancel(id string) bool {
	g.mu.Lock()
	snd := g.snd
	g.mu.Unlock()
	return snd != nil && snd.Cancel(id)
}

// ---- what the mail manager asks ----

var _ mail.Gateway = (*Gateway)(nil)

// Info implements mail.Gateway: what this gateway offers the device peer.
func (g *Gateway) Info(peer identity.ID) (mail.GatewayInfo, bool) {
	g.mu.Lock()
	defer g.mu.Unlock()
	if !g.cfg.Enabled {
		return mail.GatewayInfo{}, false
	}
	addrs := g.cfg.addressesOf(peer)
	if len(addrs) == 0 {
		return mail.GatewayInfo{}, false
	}
	return mail.GatewayInfo{Domain: g.cfg.Domain, Host: g.cfg.host(), Mailboxes: addrs, Ready: g.snd != nil}, true
}

// CanSend implements mail.Gateway.
func (g *Gateway) CanSend(peer identity.ID, from string) error {
	g.mu.Lock()
	defer g.mu.Unlock()
	if !g.cfg.Enabled {
		return errors.New("the mail gateway of this device is switched off")
	}
	for _, a := range g.cfg.addressesOf(peer) {
		if strings.EqualFold(a, from) {
			return nil
		}
	}
	return fmt.Errorf("this device has no mailbox %s", from)
}

func (g *Gateway) takeRate(sender identity.ID, rcpts int) error {
	now := g.now()
	g.mu.Lock()
	defer g.mu.Unlock()
	keep := func(l []time.Time) []time.Time {
		out := l[:0]
		for _, t := range l {
			if now.Sub(t) < 24*time.Hour {
				out = append(out, t)
			}
		}
		return out
	}
	g.byDevice[sender] = keep(g.byDevice[sender])
	g.rcptsDay = keep(g.rcptsDay)
	if len(g.byDevice[sender]) >= maxLettersPerDevicePerDay {
		return fmt.Errorf("this device has sent %d letters in a day: that is the limit of the gateway", maxLettersPerDevicePerDay)
	}
	if len(g.rcptsDay)+rcpts > maxRcptsPerDay {
		return fmt.Errorf("the gateway has sent to %d addresses in a day: that is its limit", maxRcptsPerDay)
	}
	g.byDevice[sender] = append(g.byDevice[sender], now)
	for i := 0; i < rcpts; i++ {
		g.rcptsDay = append(g.rcptsDay, now)
	}
	return nil
}

// SendOut implements mail.Gateway: a letter of a device for the Internet. The addresses of the gateway's own domain are delivered inside
// the mesh at once; the rest goes to the queue of the sender.
func (g *Gateway) SendOut(l mail.OutLetter) error {
	g.mu.Lock()
	cfg, snd := g.cfg, g.snd
	g.mu.Unlock()
	if !cfg.Enabled {
		return errors.New("the mail gateway of this device is switched off")
	}
	if snd == nil {
		return mail.ErrNotYet
	}
	if err := g.CanSend(l.Sender, l.From.Addr); err != nil {
		return err
	}
	from, err := inetmail.ParseAddress(l.From.Addr)
	if err != nil {
		return err
	}
	from.Name = l.From.Name
	var to, cc []inetmail.Address
	var external []string
	var local []mail.ExtAddr
	for _, set := range []struct {
		src []mail.ExtAddr
		dst *[]inetmail.Address
	}{{l.To, &to}, {l.Cc, &cc}} {
		for _, a := range set.src {
			pa, err := inetmail.ParseAddress(a.Addr)
			if err != nil {
				return fmt.Errorf("%s: %w", a.Addr, err)
			}
			pa.Name = a.Name
			*set.dst = append(*set.dst, pa)
			if pa.Domain == cfg.Domain {
				local = append(local, a)
			} else {
				external = append(external, pa.Addr())
			}
		}
	}
	if err := g.takeRate(l.Sender, len(to)+len(cc)); err != nil {
		return err
	}
	out := &inetmail.OutMessage{
		From: from, To: to, Cc: cc, Subject: l.Subject, Text: l.Text, MessageID: l.MessageID, InReplyTo: l.InReplyTo,
		References: l.References, Date: time.Unix(l.Created, 0), Mailer: "The Mesh",
	}
	for _, a := range l.Attach {
		out.Attachments = append(out.Attachments, inetmail.OutAttachment{Name: a.Name, Mime: a.Mime, Open: a.Open})
	}
	var buf bytes.Buffer
	if _, err := inetmail.BuildMIME(&buf, out); err != nil {
		return err
	}
	if len(external) > 0 {
		if _, err := snd.Enqueue(from.Addr(), external, l.ID, bytes.NewReader(buf.Bytes())); err != nil {
			return err
		}
	}
	for _, a := range local {
		g.deliverLocal(cfg, l, a)
	}
	return nil
}

// deliverLocal puts a letter to a mailbox of this very domain in the mesh without going out to the Internet and back.
func (g *Gateway) deliverLocal(cfg Config, l mail.OutLetter, rcpt mail.ExtAddr) {
	now := g.now().Unix()
	pa, _ := inetmail.ParseAddress(rcpt.Addr)
	mb, ok := cfg.mailbox(pa.Local)
	if !ok {
		g.opts.Mail.ExtReport(l.ID, rcpt.Addr, inetmail.RcptFailed, 550, "5.1.1 no such mailbox here", now)
		return
	}
	var attach []mail.Attachment
	for _, a := range l.Attach {
		attach = append(attach, mail.Attachment{Name: a.Name, Mime: a.Mime, Size: a.Size, SHA256: a.SHA256})
	}
	var to, cc []mail.ExtAddr
	to, cc = append(to, l.To...), append(cc, l.Cc...)
	_, err := g.opts.Mail.ReceiveExternal(mail.ExtLetter{
		MessageID: l.MessageID, Mailbox: pa.Addr(), Devices: mb.Devices,
		In: mail.ExtIn{
			MessageID: l.MessageID, References: l.References, From: l.From, To: to, Cc: cc, Verdict: inetmail.VerdictVerified, Internal: true,
		},
		Subject: l.Subject, Text: l.Text, Attach: attach, Created: now,
	})
	if err != nil {
		g.opts.Mail.ExtReport(l.ID, rcpt.Addr, inetmail.RcptFailed, 451, err.Error(), now)
		return
	}
	g.opts.Mail.ExtReport(l.ID, rcpt.Addr, inetmail.RcptDelivered, 250, "delivered inside the mesh", now)
}

// report is the queue of the sender telling what became of a recipient.
func (g *Gateway) report(r inetmail.Report) error {
	g.opts.Mail.ExtReport(r.Origin, r.Rcpt, r.State, r.Code, r.Text, r.At)
	return nil
}

// ---- letters from the Internet ----

func (g *Gateway) accept(a inetmail.Address) error {
	g.mu.Lock()
	defer g.mu.Unlock()
	if !g.cfg.Enabled || a.Domain != g.cfg.Domain {
		return inetmail.ErrNoMailbox
	}
	if _, ok := g.cfg.mailbox(a.Local); !ok {
		return inetmail.ErrNoMailbox
	}
	return nil
}

func exts(l []inetmail.Address) []mail.ExtAddr {
	out := make([]mail.ExtAddr, 0, len(l))
	for _, a := range l {
		out = append(out, mail.ExtAddr{Name: a.Name, Addr: a.Addr()})
	}
	return out
}

func (g *Gateway) deliver(ctx context.Context, in *inetmail.Inbound) error {
	g.mu.Lock()
	cfg := g.cfg
	g.mu.Unlock()
	f, err := in.Open()
	if err != nil {
		return inetmail.ErrTemporary
	}
	defer f.Close()
	put := func(name, typ string, r io.Reader) (string, int64, error) { return g.opts.Blobs.Put(r) }
	p, err := inetmail.ParseMessage(f, put, inetmail.ParseLimits{})
	if err != nil {
		return fmt.Errorf("%w: the letter cannot be read", inetmail.ErrRejected)
	}
	var from mail.ExtAddr
	if len(p.From) > 0 {
		from = mail.ExtAddr{Name: p.From[0].Name, Addr: p.From[0].Addr()}
	} else {
		from = mail.ExtAddr{Addr: in.MailFrom}
	}
	var dk []string
	for _, d := range in.Auth.DKIM {
		if d.Pass {
			dk = append(dk, d.Domain)
		}
	}
	var attach []mail.Attachment
	for _, a := range p.Attachments {
		at := mail.Attachment{Name: a.Name, Mime: a.Mime, Size: a.Size, SHA256: a.SHA256}
		if a.Inline {
			at.ContentID = a.ContentID
		}
		attach = append(attach, at)
	}
	msgID := p.MessageID
	if msgID == "" {
		msgID = in.ID
	}
	done := map[string]bool{}
	for _, rc := range in.Rcpts {
		mb, ok := cfg.mailbox(rc.Local)
		if !ok || done[mb.Name] {
			continue
		}
		done[mb.Name] = true
		_, err := g.opts.Mail.ReceiveExternal(mail.ExtLetter{
			MessageID: msgID, Mailbox: mb.Name + "@" + cfg.Domain, Devices: mb.Devices,
			In: mail.ExtIn{
				MessageID: p.MessageID, References: p.References, From: from, To: exts(p.To), Cc: exts(p.Cc), ReplyTo: exts(p.ReplyTo),
				HTML: p.HTML, RemoteImages: p.RemoteImages, Verdict: in.Auth.Verdict(), SPF: string(in.Auth.SPF),
				DKIM: strings.Join(dk, ","), DMARC: in.Auth.DMARC.Result, Truncated: p.Truncated,
			},
			Subject: p.Subject, Text: p.Text, Attach: attach, Created: in.Received.Unix(),
		})
		if err != nil {
			g.log.Error("mail gateway: a letter cannot be handed to the mesh", "err", err)
			return inetmail.ErrTemporary
		}
	}
	return nil
}
