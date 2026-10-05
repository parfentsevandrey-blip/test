package inetmail

import (
	"context"
	"crypto/tls"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/emersion/go-sasl"
	"github.com/emersion/go-smtp"

	"github.com/parfentsevandrey-blip/test/svoi/internal/store"
)

// The states of a recipient of a letter that is being sent.
const (
	RcptQueued    = "queued"    // waits for its turn
	RcptDeferred  = "deferred"  // tried, not accepted yet: will be tried again
	RcptDelivered = "delivered" // the receiving server took the letter
	RcptFailed    = "failed"    // it will not be delivered: refused for good, or given up
)

const bucketQueue = "mailq"

// RcptState is one recipient of a letter in the queue.
type RcptState struct {
	Addr     string `json:"addr"`
	State    string `json:"state"`
	Code     int    `json:"code,omitempty"` // the SMTP reply that decided it
	Text     string `json:"text,omitempty"`
	Attempts int    `json:"attempts,omitempty"`
	Next     int64  `json:"next,omitempty"` // not before this time (unix seconds)
	At       int64  `json:"at,omitempty"`   // when it reached its final state
	Told     string `json:"told,omitempty"` // the state the origin was told last
	Reported bool   `json:"reported,omitempty"`
}

// OutItem is a letter in the queue.
type OutItem struct {
	ID      string       `json:"id"`
	From    string       `json:"from"`   // the sender of the envelope: a mailbox of ours
	Domain  string       `json:"domain"` // the domain whose key signs it
	Origin  string       `json:"origin"` // who asked for it (an opaque name that comes back in the reports)
	Rcpts   []*RcptState `json:"rcpts"`
	Size    int64        `json:"size"`
	Created int64        `json:"created"`
}

// Report says how it went for one recipient of a letter. The gateway passes it on to the device that wrote the letter.
type Report struct {
	ID     string
	Origin string
	Rcpt   string
	State  string // RcptDeferred, RcptDelivered or RcptFailed
	Code   int
	Text   string
	At     int64
	Final  bool
}

// Relay is a mail service that takes our letters and delivers them ("smart host"): the way out when the address of the
// gateway is one that the big receivers do not trust (a home connection) or port 25 is closed.
type Relay struct {
	Host     string `json:"host"`
	Port     int    `json:"port"`
	Username string `json:"username,omitempty"`
	Password string `json:"password,omitempty"`
	// Mode: "starttls" (port 587), "tls" (port 465) or "plain" (no encryption: a relay on this very machine).
	Mode string `json:"mode"`
}

// SenderConfig is what the sender needs.
type SenderConfig struct {
	DB       *store.DB
	SpoolDir string
	Hostname string // what the gateway calls itself in EHLO: the host the MX record points at
	Resolver Resolver
	// DKIM gives the key that signs the letters of a domain (nil: they are not signed).
	DKIM  func(domain string) *DKIMKey
	Relay *Relay // nil: the letters go straight to the mail servers of the recipients
	// Report is told about every change that the origin should know of; an error means "not taken, tell me again later".
	Report func(Report) error
	Log    *slog.Logger

	Port         int // the SMTP port of the receiving servers (25)
	AllowPrivate bool
	// Dial opens connections (default: net.Dialer). The tests put their own.
	Dial        func(ctx context.Context, network, addr string) (net.Conn, error)
	MaxSize     int64         // bytes of one letter (default 40 MiB)
	MaxQueue    int           // letters waiting at once (default 2000)
	MaxAge      time.Duration // how long a letter is tried before it is given up (default 3 days)
	Concurrency int           // letters being sent at once (default 4)
	Now         func() time.Time
}

// Sender keeps the queue of outgoing letters and delivers them.
type Sender struct {
	cfg  SenderConfig
	kick chan struct{}
	sem  chan struct{}

	mu    sync.Mutex
	items map[string]*OutItem
	busy  map[string]bool
}

// NewSender loads the queue and prepares the sender; Run does the work.
func NewSender(cfg SenderConfig) (*Sender, error) {
	if cfg.DB == nil || cfg.SpoolDir == "" || cfg.Hostname == "" || cfg.Resolver == nil {
		return nil, errors.New("inetmail: the sender is not configured")
	}
	if cfg.Port == 0 {
		cfg.Port = 25
	}
	if cfg.MaxSize <= 0 {
		cfg.MaxSize = 40 << 20
	}
	if cfg.MaxQueue <= 0 {
		cfg.MaxQueue = 2000
	}
	if cfg.MaxAge <= 0 {
		cfg.MaxAge = 3 * 24 * time.Hour
	}
	if cfg.Concurrency <= 0 {
		cfg.Concurrency = 4
	}
	if cfg.Now == nil {
		cfg.Now = time.Now
	}
	if cfg.Log == nil {
		cfg.Log = slog.Default()
	}
	if cfg.Dial == nil {
		d := &net.Dialer{Timeout: 30 * time.Second}
		cfg.Dial = d.DialContext
	}
	if err := os.MkdirAll(cfg.SpoolDir, 0o700); err != nil {
		return nil, err
	}
	s := &Sender{cfg: cfg, kick: make(chan struct{}, 1), sem: make(chan struct{}, cfg.Concurrency), items: map[string]*OutItem{}, busy: map[string]bool{}}
	err := cfg.DB.ForEach(bucketQueue, func(key string, raw []byte) error {
		var it OutItem
		if err := json.Unmarshal(raw, &it); err != nil {
			return nil
		}
		s.items[it.ID] = &it
		return nil
	})
	if err != nil {
		return nil, err
	}
	return s, nil
}

func (s *Sender) spoolPath(id string) string { return filepath.Join(s.cfg.SpoolDir, id+".eml") }

// Kick wakes the worker (a new letter, a letter to be tried again at once).
func (s *Sender) Kick() {
	select {
	case s.kick <- struct{}{}:
	default:
	}
}

const maxRcptsOut = 100

// Enqueue puts a letter in the queue: from is the envelope sender (a mailbox of ours), rcpts the addresses to deliver
// it to, origin an opaque name for whoever asked (a second Enqueue with the same origin is the same letter: the id of
// the first is returned, nothing is sent twice), r the letter as BuildMIME made it. It returns the id of the queue entry.
func (s *Sender) Enqueue(from string, rcpts []string, origin string, r io.Reader) (string, error) {
	fa, err := ParseAddress(from)
	if err != nil {
		return "", fmt.Errorf("sender: %w", err)
	}
	if len(rcpts) == 0 || len(rcpts) > maxRcptsOut {
		return "", fmt.Errorf("a letter goes to 1 to %d recipients", maxRcptsOut)
	}
	seen := map[string]bool{}
	var list []*RcptState
	for _, r := range rcpts {
		a, err := ParseAddress(r)
		if err != nil {
			return "", fmt.Errorf("recipient: %w", err)
		}
		k := strings.ToLower(a.Addr())
		if seen[k] {
			continue
		}
		seen[k] = true
		list = append(list, &RcptState{Addr: a.Addr(), State: RcptQueued})
	}

	s.mu.Lock()
	if origin != "" {
		for _, it := range s.items {
			if it.Origin == origin {
				id := it.ID
				s.mu.Unlock()
				return id, nil
			}
		}
	}
	if len(s.items) >= s.cfg.MaxQueue {
		s.mu.Unlock()
		return "", errors.New("the queue of outgoing letters is full")
	}
	s.mu.Unlock()

	id := newID()
	f, err := os.OpenFile(s.spoolPath(id), os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o600)
	if err != nil {
		return "", err
	}
	n, err := io.Copy(f, io.LimitReader(r, s.cfg.MaxSize+1))
	if cerr := f.Close(); err == nil {
		err = cerr
	}
	if err == nil && n > s.cfg.MaxSize {
		err = ErrTooLarge
	}
	if err != nil {
		os.Remove(s.spoolPath(id))
		return "", err
	}
	it := &OutItem{ID: id, From: fa.Addr(), Domain: fa.Domain, Origin: origin, Rcpts: list, Size: n, Created: s.cfg.Now().Unix()}
	s.mu.Lock()
	s.items[id] = it
	s.saveLocked(it)
	s.mu.Unlock()
	s.Kick()
	return id, nil
}

func (s *Sender) saveLocked(it *OutItem) {
	if err := s.cfg.DB.PutJSON(bucketQueue, it.ID, it); err != nil {
		s.cfg.Log.Error("mail queue: cannot save", "err", err)
	}
}

func (s *Sender) dropLocked(it *OutItem) {
	delete(s.items, it.ID)
	_ = s.cfg.DB.Delete(bucketQueue, it.ID)
	_ = os.Remove(s.spoolPath(it.ID))
}

// Run delivers the queue until ctx ends.
func (s *Sender) Run(ctx context.Context) {
	tick := time.NewTicker(5 * time.Second)
	defer tick.Stop()
	for {
		s.pump(ctx)
		select {
		case <-ctx.Done():
			return
		case <-tick.C:
		case <-s.kick:
		}
	}
}

func (s *Sender) pump(ctx context.Context) {
	now := s.cfg.Now().Unix()
	var due []*OutItem
	s.mu.Lock()
	for _, it := range s.items {
		if s.busy[it.ID] {
			continue
		}
		for _, r := range it.Rcpts {
			if (r.State == RcptQueued || r.State == RcptDeferred) && r.Next <= now {
				due = append(due, it)
				s.busy[it.ID] = true
				break
			}
		}
	}
	s.mu.Unlock()
	var wg sync.WaitGroup
	for _, it := range due {
		wg.Add(1)
		go func(it *OutItem) {
			defer wg.Done()
			defer func() { s.mu.Lock(); delete(s.busy, it.ID); s.mu.Unlock() }()
			select {
			case s.sem <- struct{}{}:
				defer func() { <-s.sem }()
			case <-ctx.Done():
				return
			}
			s.attempt(ctx, it)
		}(it)
	}
	wg.Wait()
	s.flushReports(ctx)
}

// result is what one conversation with a mail server made of a recipient.
type result struct {
	rcpt  *RcptState
	state string
	code  int
	text  string
}

func (s *Sender) attempt(ctx context.Context, it *OutItem) {
	now := s.cfg.Now()
	s.mu.Lock()
	groups := map[string][]*RcptState{}
	for _, r := range it.Rcpts {
		if (r.State != RcptQueued && r.State != RcptDeferred) || r.Next > now.Unix() {
			continue
		}
		dom := "relay"
		if s.cfg.Relay == nil {
			dom = r.Addr[strings.LastIndexByte(r.Addr, '@')+1:]
		}
		groups[dom] = append(groups[dom], r)
	}
	s.mu.Unlock()
	if len(groups) == 0 {
		return
	}

	sig := ""
	if s.cfg.DKIM != nil {
		if key := s.cfg.DKIM(it.Domain); key != nil {
			if f, err := os.Open(s.spoolPath(it.ID)); err == nil {
				sig, _ = key.Signature(it.Domain, f)
				f.Close()
			}
		}
	}

	var results []result
	var rmu sync.Mutex
	var wg sync.WaitGroup
	doms := make([]string, 0, len(groups))
	for d := range groups {
		doms = append(doms, d)
	}
	sort.Strings(doms)
	for _, dom := range doms {
		wg.Add(1)
		go func(dom string, rs []*RcptState) {
			defer wg.Done()
			for len(rs) > 0 {
				n := min(len(rs), maxRcptsOut)
				res := s.deliverGroup(ctx, it, dom, rs[:n], sig)
				rmu.Lock()
				results = append(results, res...)
				rmu.Unlock()
				rs = rs[n:]
			}
		}(dom, groups[dom])
	}
	wg.Wait()

	s.mu.Lock()
	for _, res := range results {
		r := res.rcpt
		if r.State == RcptDelivered || (r.State == RcptFailed && res.state != RcptDelivered) {
			// Given up on (cancelled) while the letter was on its way: what was decided stays decided, except that a letter that
			// did get through has got through.
			continue
		}
		r.Attempts++
		r.Code, r.Text = res.code, clip(cleanHeader(res.text), 300)
		switch res.state {
		case RcptDelivered, RcptFailed:
			r.State, r.At = res.state, now.Unix()
		default:
			r.State = RcptDeferred
			if now.Sub(time.Unix(it.Created, 0)) > s.cfg.MaxAge {
				r.State, r.At = RcptFailed, now.Unix()
				r.Text = clip("gave up after "+humanAge(s.cfg.MaxAge)+": "+r.Text, 300)
			} else {
				r.Next = now.Add(retryDelay(r.Attempts)).Unix()
			}
		}
	}
	s.saveLocked(it)
	s.mu.Unlock()
}

func humanAge(d time.Duration) string {
	if d >= 48*time.Hour {
		return strconv.Itoa(int(d.Hours()/24)) + " days"
	}
	return d.Round(time.Minute).String()
}

// retryDelay is how long a letter waits after its n-th failed attempt: soon at first (a server that was busy), then
// less and less often.
func retryDelay(n int) time.Duration {
	steps := []time.Duration{time.Minute, 5 * time.Minute, 15 * time.Minute, 30 * time.Minute, time.Hour, 2 * time.Hour, 4 * time.Hour, 8 * time.Hour}
	if n < 1 {
		n = 1
	}
	if n > len(steps) {
		return 12 * time.Hour
	}
	return steps[n-1]
}

func allResults(rs []*RcptState, state string, code int, text string) []result {
	out := make([]result, len(rs))
	for i, r := range rs {
		out[i] = result{rcpt: r, state: state, code: code, text: text}
	}
	return out
}

// classify turns what a server said (or the failure of the connection) into a state.
func classify(err error) (state string, code int, text string) {
	var se *smtp.SMTPError
	if errors.As(err, &se) {
		text = se.Message
		if se.EnhancedCode != (smtp.EnhancedCode{}) {
			text = fmt.Sprintf("%d.%d.%d %s", se.EnhancedCode[0], se.EnhancedCode[1], se.EnhancedCode[2], text)
		}
		if se.Code >= 500 {
			return RcptFailed, se.Code, text
		}
		return RcptDeferred, se.Code, text
	}
	return RcptDeferred, 0, err.Error()
}

// deliverGroup delivers a letter to the recipients that live at one domain (or, with a relay, to all of them).
func (s *Sender) deliverGroup(ctx context.Context, it *OutItem, dom string, rs []*RcptState, sig string) []result {
	hosts, perm, err := s.route(ctx, dom)
	if err != nil {
		if perm {
			return allResults(rs, RcptFailed, 0, err.Error())
		}
		return allResults(rs, RcptDeferred, 0, err.Error())
	}
	last := allResults(rs, RcptDeferred, 0, "no mail server of "+dom+" could be reached")
	for _, h := range hosts {
		res, err := s.talk(ctx, h, it, rs, sig)
		if err == nil {
			return res
		}
		st, code, text := classify(err)
		if st == RcptFailed {
			// a server that answers for good (a refusal of the sender, of the letter) is the answer of the domain
			return allResults(rs, st, code, text)
		}
		last = allResults(rs, RcptDeferred, code, h.name+": "+text)
	}
	return last
}

// mxHost is a place to try: its name (for TLS and for the log) and its addresses.
type mxHost struct {
	name string
	port int
	ips  []net.IP // empty: resolved when it is used
	// relay credentials
	relay *Relay
}

// route says where a letter for a domain is to be taken: the relay, or the mail servers of the domain in the order of
// their preference. perm tells that the answer is a final "never" (the domain does not exist, it takes no mail).
func (s *Sender) route(ctx context.Context, dom string) (hosts []mxHost, perm bool, err error) {
	if r := s.cfg.Relay; r != nil {
		return []mxHost{{name: r.Host, port: r.Port, relay: r}}, false, nil
	}
	ctx, cancel := context.WithTimeout(ctx, dnsTimeout)
	defer cancel()
	mx, err := s.cfg.Resolver.LookupMX(ctx, dom)
	if err != nil && !isNotFound(err) {
		return nil, false, fmt.Errorf("the DNS did not answer for %s: %v", dom, err)
	}
	if len(mx) == 0 {
		// no MX: the domain itself is the mail server (RFC 5321 5.1), if it has an address at all
		if _, aerr := s.cfg.Resolver.LookupIPAddr(ctx, dom); aerr != nil {
			if isNotFound(aerr) {
				return nil, true, fmt.Errorf("the domain %s does not exist or takes no mail", dom)
			}
			return nil, false, fmt.Errorf("the DNS did not answer for %s: %v", dom, aerr)
		}
		return []mxHost{{name: dom, port: s.cfg.Port}}, false, nil
	}
	sort.SliceStable(mx, func(i, j int) bool { return mx[i].Pref < mx[j].Pref })
	if len(mx) == 1 && (fqdn(mx[0].Host) == "" || mx[0].Host == ".") {
		return nil, true, fmt.Errorf("the domain %s says it takes no mail (null MX)", dom)
	}
	for _, m := range mx {
		hosts = append(hosts, mxHost{name: fqdn(m.Host), port: s.cfg.Port})
		if len(hosts) >= 5 {
			break
		}
	}
	return hosts, false, nil
}

// IsPublicIP reports whether an address can be that of a mail server on the Internet (not loopback, private, reserved).
func IsPublicIP(ip net.IP) bool { return !notPublic(ip) }

// notPublic reports whether an address is one that a mail server on the Internet cannot have: ours, private, reserved.
func notPublic(ip net.IP) bool {
	if ip.IsLoopback() || ip.IsPrivate() || ip.IsLinkLocalUnicast() || ip.IsLinkLocalMulticast() || ip.IsMulticast() || ip.IsUnspecified() || ip.IsInterfaceLocalMulticast() {
		return true
	}
	if v4 := ip.To4(); v4 != nil {
		switch {
		case v4[0] == 0, v4[0] >= 240, v4[0] == 100 && v4[1]&0xc0 == 64, v4[0] == 192 && v4[1] == 0 && v4[2] == 0,
			v4[0] == 192 && v4[1] == 0 && v4[2] == 2, v4[0] == 198 && (v4[1] == 18 || v4[1] == 19), v4[0] == 198 && v4[1] == 51 && v4[2] == 100,
			v4[0] == 203 && v4[1] == 0 && v4[2] == 113:
			return true
		}
	}
	return false
}

// addrs returns the addresses to dial for a host.
func (s *Sender) addrs(ctx context.Context, h mxHost) ([]net.IP, error) {
	if len(h.ips) > 0 {
		return h.ips, nil
	}
	if ip := net.ParseIP(h.name); ip != nil {
		return []net.IP{ip}, nil
	}
	c, cancel := context.WithTimeout(ctx, dnsTimeout)
	defer cancel()
	ias, err := s.cfg.Resolver.LookupIPAddr(c, h.name)
	if err != nil {
		if isNotFound(err) {
			return nil, &smtp.SMTPError{Code: 450, EnhancedCode: smtp.EnhancedCode{4, 4, 4}, Message: "the mail server " + h.name + " has no address"}
		}
		return nil, fmt.Errorf("the DNS did not answer for %s: %v", h.name, err)
	}
	var v4, v6 []net.IP
	for _, ia := range ias {
		if s.cfg.AllowPrivate || !notPublic(ia.IP) {
			if ia.IP.To4() != nil {
				v4 = append(v4, ia.IP)
			} else {
				v6 = append(v6, ia.IP)
			}
		}
	}
	out := append(v4, v6...)
	if len(out) == 0 && len(ias) > 0 {
		// every address is one that must not be dialled: a domain that sends us to our own network is not a mail domain
		return nil, &smtp.SMTPError{Code: 550, EnhancedCode: smtp.EnhancedCode{5, 4, 4}, Message: "the mail server " + h.name + " is at an address that is not on the Internet"}
	}
	return out, nil
}

// connect opens a connection to a mail server, with encryption when the server offers it (or demands it, for a relay).
func (s *Sender) connect(ctx context.Context, h mxHost) (*smtp.Client, bool, error) {
	ips, err := s.addrs(ctx, h)
	if err != nil {
		return nil, false, err
	}
	var lastErr error
	for _, ip := range ips {
		addr := net.JoinHostPort(ip.String(), strconv.Itoa(h.port))
		dctx, cancel := context.WithTimeout(ctx, 30*time.Second)
		conn, err := s.cfg.Dial(dctx, "tcp", addr)
		cancel()
		if err != nil {
			lastErr = err
			continue
		}
		c, secure, err := s.greet(conn, h)
		if err != nil {
			_ = conn.Close()
			lastErr = err
			// an offer of encryption that goes wrong is no reason to give up on a server that talks without it
			if h.relay == nil && secure {
				if c2, ok := s.plain(ctx, addr, h); ok {
					return c2, false, nil
				}
			}
			continue
		}
		return c, secure, nil
	}
	if lastErr == nil {
		lastErr = errors.New("no address to connect to")
	}
	return nil, false, lastErr
}

func (s *Sender) plain(ctx context.Context, addr string, h mxHost) (*smtp.Client, bool) {
	dctx, cancel := context.WithTimeout(ctx, 30*time.Second)
	defer cancel()
	conn, err := s.cfg.Dial(dctx, "tcp", addr)
	if err != nil {
		return nil, false
	}
	c := smtp.NewClient(conn)
	c.CommandTimeout, c.SubmissionTimeout = 2*time.Minute, 5*time.Minute
	if err := c.Hello(s.cfg.Hostname); err != nil {
		_ = c.Close()
		return nil, false
	}
	return c, true
}

// greet starts the conversation on conn. The second result tells whether encryption was tried (so that a failure of it can be
// answered with a plain conversation).
func (s *Sender) greet(conn net.Conn, h mxHost) (*smtp.Client, bool, error) {
	tcfg := &tls.Config{ServerName: h.name, MinVersion: tls.VersionTLS12}
	if h.relay == nil {
		tcfg.InsecureSkipVerify = true // mail servers do not prove their names to each other (see tlscert.go)
	}
	var c *smtp.Client
	secure := false
	switch {
	case h.relay != nil && h.relay.Mode == "tls":
		c = smtp.NewClient(tls.Client(conn, tcfg))
		secure = true
	case h.relay != nil && h.relay.Mode == "plain":
		c = smtp.NewClient(conn)
	default:
		cc, err := smtp.NewClientStartTLS(conn, tcfg)
		if err != nil {
			if h.relay == nil && strings.Contains(err.Error(), "doesn't support STARTTLS") {
				return nil, true, err
			}
			return nil, h.relay == nil, err
		}
		c, secure = cc, true
	}
	c.CommandTimeout, c.SubmissionTimeout = 2*time.Minute, 5*time.Minute
	if err := c.Hello(s.cfg.Hostname); err != nil {
		_ = c.Close()
		return nil, secure, err
	}
	if h.relay != nil && h.relay.Username != "" {
		if err := c.Auth(sasl.NewPlainClient("", h.relay.Username, h.relay.Password)); err != nil {
			_ = c.Close()
			return nil, secure, err
		}
	}
	return c, secure, nil
}

// talk delivers a letter to one server. An error is a failure of the conversation as a whole (the connection, the
// sender or the letter refused); what each recipient got is in the results.
func (s *Sender) talk(ctx context.Context, h mxHost, it *OutItem, rs []*RcptState, sig string) ([]result, error) {
	c, _, err := s.connect(ctx, h)
	if err != nil {
		return nil, err
	}
	defer c.Close()
	if err := c.Mail(it.From, nil); err != nil {
		_ = c.Quit()
		return nil, err
	}
	var accepted []*RcptState
	results := make([]result, 0, len(rs))
	for _, r := range rs {
		if err := c.Rcpt(r.Addr, nil); err != nil {
			st, code, text := classify(err)
			results = append(results, result{rcpt: r, state: st, code: code, text: text})
			continue
		}
		accepted = append(accepted, r)
	}
	if len(accepted) == 0 {
		_ = c.Quit()
		return results, nil
	}
	f, err := os.Open(s.spoolPath(it.ID))
	if err != nil {
		return nil, fmt.Errorf("the letter is gone from the queue: %w", err)
	}
	defer f.Close()
	w, err := c.Data()
	if err != nil {
		return nil, err
	}
	if sig != "" {
		if _, err := io.WriteString(w, sig); err != nil {
			return nil, err
		}
	}
	if _, err := io.Copy(w, f); err != nil {
		return nil, err
	}
	resp, err := w.CloseWithResponse()
	if err != nil {
		st, code, text := classify(err)
		for _, r := range accepted {
			results = append(results, result{rcpt: r, state: st, code: code, text: text})
		}
		_ = c.Quit()
		return results, nil
	}
	text := "accepted"
	if resp != nil && resp.StatusText != "" {
		text = resp.StatusText
	}
	for _, r := range accepted {
		results = append(results, result{rcpt: r, state: RcptDelivered, code: 250, text: text})
	}
	_ = c.Quit()
	return results, nil
}

// ---- reports ----

// flushReports tells the origins what changed, and forgets the letters that are done and told.
func (s *Sender) flushReports(ctx context.Context) {
	if s.cfg.Report == nil {
		return
	}
	type pending struct {
		it  *OutItem
		r   *RcptState
		rep Report
	}
	var todo []pending
	s.mu.Lock()
	for _, it := range s.items {
		for _, r := range it.Rcpts {
			if r.State != RcptQueued && r.Told != r.State {
				todo = append(todo, pending{it, r, Report{
					ID: it.ID, Origin: it.Origin, Rcpt: r.Addr, State: r.State, Code: r.Code, Text: r.Text, At: s.cfg.Now().Unix(),
					Final: r.State == RcptDelivered || r.State == RcptFailed,
				}})
			}
		}
	}
	s.mu.Unlock()
	for _, p := range todo {
		if ctx.Err() != nil {
			return
		}
		if err := s.cfg.Report(p.rep); err != nil {
			s.cfg.Log.Debug("mail queue: the report was not taken", "id", p.it.ID, "err", err)
			continue
		}
		s.mu.Lock()
		p.r.Told = p.rep.State
		if p.rep.Final {
			p.r.Reported = true
		}
		s.saveLocked(p.it)
		s.mu.Unlock()
	}
	s.mu.Lock()
	for _, it := range s.items {
		done := true
		for _, r := range it.Rcpts {
			if !r.Reported {
				done = false
				break
			}
		}
		if done && !s.busy[it.ID] {
			s.dropLocked(it)
		}
	}
	s.mu.Unlock()
}

// ---- looking at the queue ----

// QueueEntry is a letter of the queue as the interface shows it.
type QueueEntry struct {
	ID      string      `json:"id"`
	From    string      `json:"from"`
	Origin  string      `json:"origin"`
	Size    int64       `json:"size"`
	Created int64       `json:"created"`
	Rcpts   []RcptState `json:"rcpts"`
}

// Queue lists the letters that are still on their way (those with a recipient that is not yet delivered or failed),
// the oldest first.
func (s *Sender) Queue() []QueueEntry {
	s.mu.Lock()
	defer s.mu.Unlock()
	var out []QueueEntry
	for _, it := range s.items {
		e := QueueEntry{ID: it.ID, From: it.From, Origin: it.Origin, Size: it.Size, Created: it.Created}
		open := false
		for _, r := range it.Rcpts {
			e.Rcpts = append(e.Rcpts, *r)
			if r.State == RcptQueued || r.State == RcptDeferred {
				open = true
			}
		}
		if open {
			out = append(out, e)
		}
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Created < out[j].Created })
	return out
}

// RetryNow makes the letters that wait for another try be tried at once.
func (s *Sender) RetryNow() {
	s.mu.Lock()
	for _, it := range s.items {
		changed := false
		for _, r := range it.Rcpts {
			if r.State == RcptDeferred && r.Next != 0 {
				r.Next, changed = 0, true
			}
		}
		if changed {
			s.saveLocked(it)
		}
	}
	s.mu.Unlock()
	s.Kick()
}

// Cancel gives up on a letter that has not been delivered yet: its recipients that are still waiting fail.
func (s *Sender) Cancel(id string) bool {
	s.mu.Lock()
	it := s.items[id]
	if it == nil {
		s.mu.Unlock()
		return false
	}
	now := s.cfg.Now().Unix()
	for _, r := range it.Rcpts {
		if r.State == RcptQueued || r.State == RcptDeferred {
			r.State, r.Text, r.At = RcptFailed, "cancelled", now
		}
	}
	s.saveLocked(it)
	s.mu.Unlock()
	s.Kick()
	return true
}
