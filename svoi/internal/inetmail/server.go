package inetmail

import (
	"bufio"
	"context"
	"crypto/rand"
	"crypto/tls"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net"
	netmail "net/mail"
	"os"
	"strings"
	"sync"
	"time"

	"github.com/emersion/go-smtp"
)

// Errors that the functions of the gateway return to the server, which turns them into SMTP replies.
var (
	// ErrNoMailbox: there is no such mailbox here (550 5.1.1: the sender is told at once and writes no more).
	ErrNoMailbox = errors.New("no such mailbox")
	// ErrTemporary: not now (451: the sender tries again later).
	ErrTemporary = errors.New("temporary failure, try again later")
	// ErrRejected: never (554: the sender is told that the letter will not be taken).
	ErrRejected = errors.New("the letter is rejected")
)

// Inbound is a letter that has been received and checked: who sent it, which of the mailboxes it is for, whether
// the proofs of its sender hold.
type Inbound struct {
	ID         string
	RemoteIP   net.IP
	Helo       string
	MailFrom   string    // the sender of the envelope ("" for a bounce)
	Rcpts      []Address // the mailboxes it is for (all of them are ours)
	Size       int64
	Received   time.Time
	TLS        bool
	Auth       Auth
	FromDomain string // the domain of the From header

	path string
}

// Open reads the letter, with the header fields of the trace (Return-Path, Received, Authentication-Results) in front.
func (in *Inbound) Open() (*os.File, error) { return os.Open(in.path) }

// ServerConfig is what the receiving server needs.
type ServerConfig struct {
	Hostname string // the name it says in its greeting and puts in Received: the host the MX record points at
	Resolver Resolver
	TLS      *tls.Config // nil: no STARTTLS
	SpoolDir string      // where letters wait while they are checked
	Log      *slog.Logger

	// IsLocalDomain says whether mail for a domain is ours. A letter for any other domain is refused: the server is
	// not a relay.
	IsLocalDomain func(domain string) bool
	// Accept decides about one recipient before the letter is sent: nil takes it; ErrNoMailbox, ErrTemporary or another
	// error refuses it.
	Accept func(Address) error
	// Deliver takes a letter that has been checked. nil: it is stored, and the sender is told so; ErrTemporary: not now;
	// ErrRejected: never; any other error counts as ErrTemporary.
	Deliver func(ctx context.Context, in *Inbound) error

	MaxSize          int64 // bytes of one letter (default 40 MiB)
	MaxRcpts         int   // recipients of one letter (default 50)
	MaxConnsPerIP    int   // connections open at once from one address (default 10)
	MaxMsgsPerIPHour int   // letters from one address in an hour (default 120)
	MaxChecks        int   // letters being checked at once (default 4)
	Now              func() time.Time
}

// Server is the SMTP server that receives letters from the Internet.
type Server struct {
	cfg  ServerConfig
	smtp *smtp.Server
	sem  chan struct{}

	mu    sync.Mutex
	conns map[string]int
	msgs  map[string][]time.Time
}

// NewServer prepares the server; Serve starts it.
func NewServer(cfg ServerConfig) (*Server, error) {
	if cfg.Hostname == "" || cfg.Resolver == nil || cfg.IsLocalDomain == nil || cfg.Accept == nil || cfg.Deliver == nil || cfg.SpoolDir == "" {
		return nil, errors.New("inetmail: the server is not configured")
	}
	if cfg.MaxSize <= 0 {
		cfg.MaxSize = 40 << 20
	}
	if cfg.MaxRcpts <= 0 {
		cfg.MaxRcpts = 50
	}
	if cfg.MaxConnsPerIP <= 0 {
		cfg.MaxConnsPerIP = 10
	}
	if cfg.MaxMsgsPerIPHour <= 0 {
		cfg.MaxMsgsPerIPHour = 120
	}
	if cfg.MaxChecks <= 0 {
		cfg.MaxChecks = 4
	}
	if cfg.Now == nil {
		cfg.Now = time.Now
	}
	if cfg.Log == nil {
		cfg.Log = slog.Default()
	}
	if err := os.MkdirAll(cfg.SpoolDir, 0o700); err != nil {
		return nil, err
	}
	s := &Server{cfg: cfg, sem: make(chan struct{}, cfg.MaxChecks), conns: map[string]int{}, msgs: map[string][]time.Time{}}
	srv := smtp.NewServer(s)
	srv.Domain = cfg.Hostname
	srv.MaxRecipients = cfg.MaxRcpts
	srv.MaxMessageBytes = cfg.MaxSize
	srv.ReadTimeout = 5 * time.Minute
	srv.WriteTimeout = 5 * time.Minute
	srv.TLSConfig = cfg.TLS
	srv.ErrorLog = slogLogger{cfg.Log}
	s.smtp = srv
	return s, nil
}

type slogLogger struct{ l *slog.Logger }

func (s slogLogger) Printf(format string, v ...any) {
	s.l.Debug("smtp server: " + fmt.Sprintf(format, v...))
}
func (s slogLogger) Println(v ...any) { s.l.Debug("smtp server: " + fmt.Sprint(v...)) }

// Serve accepts connections on l until Close.
func (s *Server) Serve(l net.Listener) error {
	err := s.smtp.Serve(l)
	if errors.Is(err, smtp.ErrServerClosed) {
		return nil
	}
	return err
}

// Close stops the server and drops the connections.
func (s *Server) Close() error { return s.smtp.Close() }

// ---- sessions ----

func remoteIP(c *smtp.Conn) net.IP {
	if c == nil || c.Conn() == nil {
		return nil
	}
	h, _, err := net.SplitHostPort(c.Conn().RemoteAddr().String())
	if err != nil {
		return nil
	}
	return net.ParseIP(h)
}

// NewSession implements smtp.Backend.
func (s *Server) NewSession(c *smtp.Conn) (smtp.Session, error) {
	ip := remoteIP(c)
	key := ""
	if ip != nil {
		key = ip.String()
	}
	s.mu.Lock()
	if s.conns[key] >= s.cfg.MaxConnsPerIP {
		s.mu.Unlock()
		return nil, &smtp.SMTPError{Code: 421, EnhancedCode: smtp.EnhancedCode{4, 7, 0}, Message: "too many connections from your address, try again later"}
	}
	s.conns[key]++
	s.mu.Unlock()
	return &session{srv: s, conn: c, ip: ip, key: key}, nil
}

type session struct {
	srv   *Server
	conn  *smtp.Conn
	ip    net.IP
	key   string
	from  string
	rcpts []Address
	seen  map[string]bool
}

func (s *session) Reset() {
	s.from, s.rcpts, s.seen = "", nil, nil
}

func (s *session) Logout() error {
	s.srv.mu.Lock()
	if s.srv.conns[s.key]--; s.srv.conns[s.key] <= 0 {
		delete(s.srv.conns, s.key)
	}
	s.srv.mu.Unlock()
	return nil
}

func smtpErr(code int, a, b, c int, msg string) *smtp.SMTPError {
	return &smtp.SMTPError{Code: code, EnhancedCode: smtp.EnhancedCode{a, b, c}, Message: msg}
}

func (s *session) Mail(from string, opts *smtp.MailOptions) error {
	s.Reset()
	if from != "" {
		at := strings.LastIndexByte(from, '@')
		if at <= 0 || at == len(from)-1 || len(from) > maxAddress || strings.ContainsAny(from, " \t\r\n<>") {
			return smtpErr(501, 5, 1, 7, "the address of the sender is not valid")
		}
	}
	if opts != nil && opts.Size > s.srv.cfg.MaxSize {
		return smtpErr(552, 5, 3, 4, "the letter is larger than this server takes")
	}
	// rate: letters per hour from one address
	now := s.srv.cfg.Now()
	s.srv.mu.Lock()
	l := s.srv.msgs[s.key]
	keep := l[:0]
	for _, t := range l {
		if now.Sub(t) < time.Hour {
			keep = append(keep, t)
		}
	}
	over := len(keep) >= s.srv.cfg.MaxMsgsPerIPHour
	s.srv.msgs[s.key] = keep
	s.srv.mu.Unlock()
	if over {
		return smtpErr(450, 4, 7, 1, "too many letters from your address, try again later")
	}
	s.from = from
	return nil
}

func (s *session) Rcpt(to string, opts *smtp.RcptOptions) error {
	a, err := ParseAddress(to)
	if err != nil {
		return smtpErr(501, 5, 1, 3, "the address of the recipient is not valid")
	}
	if !s.srv.cfg.IsLocalDomain(a.Domain) {
		return smtpErr(554, 5, 7, 1, "relay access denied")
	}
	if s.seen == nil {
		s.seen = map[string]bool{}
	}
	k := strings.ToLower(a.Addr())
	if s.seen[k] {
		return nil // the same recipient twice is one recipient
	}
	s.seen[k] = true
	if err := s.srv.cfg.Accept(a); err != nil {
		switch {
		case errors.Is(err, ErrTemporary):
			return smtpErr(451, 4, 3, 0, "try again later")
		case errors.Is(err, ErrNoMailbox):
			return smtpErr(550, 5, 1, 1, "no such mailbox here")
		}
		return smtpErr(550, 5, 1, 0, clip(err.Error(), 200))
	}
	s.rcpts = append(s.rcpts, a)
	return nil
}

func newID() string {
	b := make([]byte, 8)
	_, _ = rand.Read(b)
	return hex.EncodeToString(b)
}

// headerDrop says which header fields of a received letter are not believed: the proofs and the trace are ours to make.
func headerDrop(name string) bool {
	return name == "authentication-results" || name == "return-path" || name == "x-mesh-auth" || strings.HasPrefix(name, "x-mesh-")
}

// copyFiltered copies a letter to dst without the header fields headerDrop names and says how many Received fields it had.
func copyFiltered(dst io.Writer, src io.Reader) (hops int, err error) {
	br := bufio.NewReaderSize(src, 64<<10)
	skipping := false
	for {
		line, rerr := br.ReadString('\n')
		if line != "" {
			if line == "\r\n" || line == "\n" {
				if _, err := io.WriteString(dst, line); err != nil {
					return hops, err
				}
				break
			}
			if line[0] != ' ' && line[0] != '\t' {
				name := ""
				if i := strings.IndexByte(line, ':'); i > 0 {
					name = strings.ToLower(strings.TrimSpace(line[:i]))
				}
				skipping = headerDrop(name)
				if name == "received" {
					hops++
				}
			}
			if !skipping {
				if _, err := io.WriteString(dst, line); err != nil {
					return hops, err
				}
			}
		}
		if rerr != nil {
			if rerr == io.EOF {
				return hops, nil
			}
			return hops, rerr
		}
	}
	_, err = io.Copy(dst, br)
	return hops, err
}

func fromDomainOf(path string) string {
	f, err := os.Open(path)
	if err != nil {
		return ""
	}
	defer f.Close()
	m, err := netmail.ReadMessage(f)
	if err != nil {
		return ""
	}
	l, err := m.Header.AddressList("From")
	if err != nil || len(l) == 0 {
		return ""
	}
	if at := strings.LastIndexByte(l[0].Address, '@'); at > 0 {
		d, _ := CleanDomain(l[0].Address[at+1:])
		return d
	}
	return ""
}

func (s *session) Data(r io.Reader) error {
	if len(s.rcpts) == 0 {
		return smtpErr(554, 5, 5, 1, "no valid recipients")
	}
	srv := s.srv
	s.srv.mu.Lock()
	s.srv.msgs[s.key] = append(s.srv.msgs[s.key], srv.cfg.Now())
	s.srv.mu.Unlock()
	select {
	case srv.sem <- struct{}{}:
		defer func() { <-srv.sem }()
	case <-time.After(30 * time.Second):
		return smtpErr(451, 4, 3, 2, "the server is busy, try again later")
	}

	raw, err := os.CreateTemp(srv.cfg.SpoolDir, "in-*.eml")
	if err != nil {
		srv.cfg.Log.Error("smtp: cannot spool a letter", "err", err)
		return smtpErr(451, 4, 3, 0, "local error, try again later")
	}
	rawPath := raw.Name()
	defer os.Remove(rawPath)
	size, err := io.Copy(raw, r)
	if err != nil {
		raw.Close()
		return err // (too large: go-smtp's own error, a connection that broke: nobody to tell)
	}
	if err := raw.Close(); err != nil {
		return smtpErr(451, 4, 3, 0, "local error, try again later")
	}

	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Minute)
	defer cancel()
	helo := cleanHeader(s.conn.Hostname())
	spfDomain := ""
	if i := strings.LastIndexByte(s.from, '@'); i >= 0 {
		spfDomain = fqdn(s.from[i+1:])
	}
	if spfDomain == "" {
		spfDomain = fqdn(helo)
	}
	auth := Auth{SPFDomain: spfDomain}
	auth.SPF = CheckSPF(ctx, srv.cfg.Resolver, s.ip, helo, s.from)
	if f, err := os.Open(rawPath); err == nil {
		auth.DKIM = VerifyDKIM(ctx, srv.cfg.Resolver, f)
		f.Close()
	}
	fromDomain := fromDomainOf(rawPath)
	auth.DMARC = EvalDMARC(ctx, srv.cfg.Resolver, fromDomain, auth.SPF, spfDomain, auth.DKIM)
	if auth.DMARC.Result == "fail" && auth.DMARC.Policy == "reject" {
		srv.cfg.Log.Info("smtp: letter refused by the DMARC policy of its sender", "from", s.from, "ip", s.key, "domain", fromDomain)
		return smtpErr(550, 5, 7, 1, "the policy (DMARC) of "+clip(fromDomain, 100)+" says that letters that fail its checks are to be rejected")
	}

	// the letter as it is kept: our trace in front, what the sender says about the trace and the checks taken out
	id := newID()
	final, err := os.CreateTemp(srv.cfg.SpoolDir, "in-*.eml")
	if err != nil {
		return smtpErr(451, 4, 3, 0, "local error, try again later")
	}
	finalPath := final.Name()
	defer os.Remove(finalPath)
	_, tlsOn := s.conn.TLSConnectionState()
	proto := "ESMTP"
	if tlsOn {
		proto = "ESMTPS"
	}
	now := srv.cfg.Now()
	trace := fmt.Sprintf("Return-Path: <%s>\r\nReceived: from %s ([%s]) by %s with %s id %s; %s\r\nAuthentication-Results: %s\r\n",
		cleanHeader(s.from), orDash(helo), s.key, srv.cfg.Hostname, proto, id, now.Format(time.RFC1123Z), auth.Header(srv.cfg.Hostname))
	if _, err := io.WriteString(final, trace); err != nil {
		final.Close()
		return smtpErr(451, 4, 3, 0, "local error, try again later")
	}
	src, err := os.Open(rawPath)
	if err != nil {
		final.Close()
		return smtpErr(451, 4, 3, 0, "local error, try again later")
	}
	hops, err := copyFiltered(final, src)
	src.Close()
	if cerr := final.Close(); err == nil {
		err = cerr
	}
	if err != nil {
		return smtpErr(451, 4, 3, 0, "local error, try again later")
	}
	if hops > 40 {
		return smtpErr(554, 5, 4, 6, "too many hops: the letter is going in circles")
	}

	in := &Inbound{
		ID: id, RemoteIP: s.ip, Helo: helo, MailFrom: s.from, Rcpts: append([]Address(nil), s.rcpts...), Size: size,
		Received: now, TLS: tlsOn, Auth: auth, FromDomain: fromDomain, path: finalPath,
	}
	if err := srv.cfg.Deliver(ctx, in); err != nil {
		switch {
		case errors.Is(err, ErrRejected):
			return smtpErr(554, 5, 7, 1, clip(err.Error(), 200))
		case errors.Is(err, ErrTemporary):
			return smtpErr(451, 4, 3, 0, "try again later")
		}
		srv.cfg.Log.Error("smtp: delivery of a letter failed", "id", id, "err", err)
		return smtpErr(451, 4, 3, 0, "local error, try again later")
	}
	srv.cfg.Log.Info("smtp: letter received", "id", id, "from", s.from, "ip", s.key, "rcpts", len(s.rcpts), "size", size,
		"spf", string(auth.SPF), "dmarc", auth.DMARC.Result, "tls", tlsOn)
	return nil
}

func orDash(s string) string {
	if s == "" {
		return "unknown"
	}
	return s
}
