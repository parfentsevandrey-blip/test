// Package tor implements a client for the Tor control protocol (control-spec.txt)
// together with process supervision and circuit management.
package tor

import (
	"bufio"
	"context"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"net"
	"os"
	"strings"
	"sync"
	"time"
)

// Control protocol status codes we care about.
const (
	statusOK           = "250"
	statusUnnecessary  = "251"
	statusAsyncEvent   = "650"
	statusNeedPassword = "515"
)

var (
	// ErrClosed is returned once the control connection has been shut down.
	ErrClosed = errors.New("tor control: connection closed")
	// ErrAuthFailed is returned when no authentication method succeeded.
	ErrAuthFailed = errors.New("tor control: authentication failed")
)

// safecookie HMAC personalization strings from control-spec section 3.24.
const (
	safeCookieServerKey = "Tor safe cookie authentication server-to-controller hash"
	safeCookieClientKey = "Tor safe cookie authentication controller-to-server hash"
)

// ReplyLine is one line of a control-port reply. Data holds the payload of a
// "250+key=" style multi-line reply; it is empty for ordinary lines.
type ReplyLine struct {
	Code string
	Text string
	Data string
}

// Reply is a complete (non-async) control port response.
type Reply struct {
	Lines []ReplyLine
}

// Code returns the status code of the terminating line.
func (r *Reply) Code() string {
	if len(r.Lines) == 0 {
		return ""
	}
	return r.Lines[len(r.Lines)-1].Code
}

// IsOK reports whether the reply terminated with a success code.
func (r *Reply) IsOK() bool {
	c := r.Code()
	return c == statusOK || c == statusUnnecessary
}

// Text joins every reply line, which is the convenient form for single-value
// GETINFO results.
func (r *Reply) Text() string {
	parts := make([]string, 0, len(r.Lines))
	for _, l := range r.Lines {
		if l.Data != "" {
			parts = append(parts, l.Data)
			continue
		}
		parts = append(parts, l.Text)
	}
	return strings.Join(parts, "\n")
}

func (r *Reply) err() error {
	if r.IsOK() {
		return nil
	}
	return fmt.Errorf("tor control: %s %s", r.Code(), r.Text())
}

// Event is an asynchronous 650 message. Type is the event keyword (CIRC,
// STREAM, STATUS_CLIENT, ...) and Body is the remainder of the first line.
// Data carries the payload of multi-line events.
type Event struct {
	Type string
	Body string
	Data string
	At   time.Time
}

// EventHandler receives asynchronous events. Handlers run on a dedicated
// dispatch goroutine and must not block for long.
type EventHandler func(Event)

// Conn is a connection to Tor's control port. It is safe for concurrent use:
// commands are serialized, and asynchronous events are delivered to registered
// handlers rather than interleaved into command replies.
type Conn struct {
	conn net.Conn
	br   *bufio.Reader

	cmdMu sync.Mutex // serializes command/response round-trips

	writeMu sync.Mutex

	replies chan *Reply
	readErr chan error

	handlersMu sync.RWMutex
	handlers   map[string][]EventHandler
	nextHandle int

	events chan Event

	closeOnce sync.Once
	closed    chan struct{}
}

// Dial connects to a Tor control port.
func Dial(ctx context.Context, address string) (*Conn, error) {
	var d net.Dialer
	c, err := d.DialContext(ctx, "tcp", address)
	if err != nil {
		return nil, fmt.Errorf("dial control port %s: %w", address, err)
	}
	conn := &Conn{
		conn:     c,
		br:       bufio.NewReaderSize(c, 64*1024),
		replies:  make(chan *Reply, 1),
		readErr:  make(chan error, 1),
		handlers: make(map[string][]EventHandler),
		events:   make(chan Event, 256),
		closed:   make(chan struct{}),
	}
	go conn.readLoop()
	go conn.dispatchLoop()
	return conn, nil
}

// Close shuts the connection down. It is idempotent.
func (c *Conn) Close() error {
	var err error
	c.closeOnce.Do(func() {
		close(c.closed)
		err = c.conn.Close()
	})
	return err
}

// readLoop parses reply lines and routes them to either the pending command or
// the event dispatcher.
func (c *Conn) readLoop() {
	for {
		reply, isEvent, err := c.readReply()
		if err != nil {
			select {
			case c.readErr <- err:
			default:
			}
			c.Close()
			close(c.events)
			return
		}
		if isEvent {
			c.emit(reply)
			continue
		}
		select {
		case c.replies <- reply:
		case <-c.closed:
			return
		}
	}
}

// readReply reads one complete reply. A reply is a sequence of "code-text" or
// "code+text\r\n<data>\r\n.\r\n" lines terminated by a "code text" line.
func (c *Conn) readReply() (*Reply, bool, error) {
	reply := &Reply{}
	isEvent := false
	for {
		line, err := c.readLine()
		if err != nil {
			return nil, false, err
		}
		if len(line) < 4 {
			return nil, false, fmt.Errorf("tor control: short reply line %q", line)
		}
		code, sep, text := line[:3], line[3], line[4:]
		if code == statusAsyncEvent {
			isEvent = true
		}
		rl := ReplyLine{Code: code, Text: text}
		if sep == '+' {
			data, err := c.readData()
			if err != nil {
				return nil, false, err
			}
			rl.Data = data
		}
		reply.Lines = append(reply.Lines, rl)
		if sep == ' ' {
			return reply, isEvent, nil
		}
		if sep != '-' && sep != '+' {
			return nil, false, fmt.Errorf("tor control: bad separator %q in %q", sep, line)
		}
	}
}

// readData reads a dot-terminated data block, undoing dot-stuffing.
func (c *Conn) readData() (string, error) {
	var sb strings.Builder
	for {
		line, err := c.readLine()
		if err != nil {
			return "", err
		}
		if line == "." {
			return sb.String(), nil
		}
		// Leading "." is escaped as ".." on the wire.
		if strings.HasPrefix(line, "..") {
			line = line[1:]
		}
		sb.WriteString(line)
		sb.WriteByte('\n')
	}
}

func (c *Conn) readLine() (string, error) {
	line, err := c.br.ReadString('\n')
	if err != nil {
		return "", err
	}
	return strings.TrimRight(line, "\r\n"), nil
}

// emit converts a 650 reply into an Event and queues it for dispatch.
func (c *Conn) emit(r *Reply) {
	if len(r.Lines) == 0 {
		return
	}
	first := r.Lines[0]
	kind, body, _ := strings.Cut(first.Text, " ")
	ev := Event{Type: strings.ToUpper(kind), Body: body, At: time.Now()}

	// Collect any data payloads and continuation lines.
	var data []string
	for i, l := range r.Lines {
		if l.Data != "" {
			data = append(data, l.Data)
		} else if i > 0 {
			data = append(data, l.Text)
		}
	}
	ev.Data = strings.Join(data, "\n")

	select {
	case c.events <- ev:
	default:
		// Drop events rather than stall Tor's control connection.
	}
}

func (c *Conn) dispatchLoop() {
	for ev := range c.events {
		c.handlersMu.RLock()
		hs := append([]EventHandler(nil), c.handlers[ev.Type]...)
		hs = append(hs, c.handlers["*"]...)
		c.handlersMu.RUnlock()
		for _, h := range hs {
			h(ev)
		}
	}
}

// OnEvent registers a handler for an event type. Use "*" to receive every
// event. Handlers are invoked serially on one goroutine.
func (c *Conn) OnEvent(eventType string, h EventHandler) {
	c.handlersMu.Lock()
	defer c.handlersMu.Unlock()
	key := strings.ToUpper(eventType)
	c.handlers[key] = append(c.handlers[key], h)
}

// Send issues a command and waits for its reply.
func (c *Conn) Send(ctx context.Context, format string, args ...any) (*Reply, error) {
	cmd := format
	if len(args) > 0 {
		cmd = fmt.Sprintf(format, args...)
	}

	c.cmdMu.Lock()
	defer c.cmdMu.Unlock()

	select {
	case <-c.closed:
		return nil, ErrClosed
	default:
	}

	if err := c.writeLine(cmd); err != nil {
		return nil, err
	}

	select {
	case r := <-c.replies:
		return r, nil
	case err := <-c.readErr:
		return nil, err
	case <-c.closed:
		return nil, ErrClosed
	case <-ctx.Done():
		return nil, ctx.Err()
	}
}

// SendOK issues a command and fails if the reply is not a success code.
func (c *Conn) SendOK(ctx context.Context, format string, args ...any) (*Reply, error) {
	r, err := c.Send(ctx, format, args...)
	if err != nil {
		return nil, err
	}
	if err := r.err(); err != nil {
		return nil, fmt.Errorf("%s: %w", strings.SplitN(cmdName(format), " ", 2)[0], err)
	}
	return r, nil
}

func cmdName(format string) string {
	if i := strings.IndexByte(format, ' '); i > 0 {
		return format[:i]
	}
	return format
}

func (c *Conn) writeLine(s string) error {
	c.writeMu.Lock()
	defer c.writeMu.Unlock()
	_ = c.conn.SetWriteDeadline(time.Now().Add(30 * time.Second))
	defer c.conn.SetWriteDeadline(time.Time{})
	if _, err := c.conn.Write([]byte(s + "\r\n")); err != nil {
		return fmt.Errorf("tor control: write %q: %w", cmdName(s), err)
	}
	return nil
}

// ProtocolInfo is the parsed result of the PROTOCOLINFO command.
type ProtocolInfo struct {
	AuthMethods []string
	CookieFile  string
	TorVersion  string
}

// HasMethod reports whether Tor advertises the given auth method.
func (p ProtocolInfo) HasMethod(m string) bool {
	for _, have := range p.AuthMethods {
		if strings.EqualFold(have, m) {
			return true
		}
	}
	return false
}

// ProtocolInfo queries the authentication methods Tor will accept.
func (c *Conn) ProtocolInfo(ctx context.Context) (ProtocolInfo, error) {
	var pi ProtocolInfo
	r, err := c.Send(ctx, "PROTOCOLINFO 1")
	if err != nil {
		return pi, err
	}
	if err := r.err(); err != nil {
		return pi, err
	}
	for _, l := range r.Lines {
		fields := splitQuoted(l.Text)
		if len(fields) == 0 {
			continue
		}
		switch strings.ToUpper(fields[0]) {
		case "AUTH":
			for _, f := range fields[1:] {
				k, v, ok := strings.Cut(f, "=")
				if !ok {
					continue
				}
				switch strings.ToUpper(k) {
				case "METHODS":
					pi.AuthMethods = strings.Split(v, ",")
				case "COOKIEFILE":
					pi.CookieFile = unquote(v)
				}
			}
		case "VERSION":
			for _, f := range fields[1:] {
				if k, v, ok := strings.Cut(f, "="); ok && strings.EqualFold(k, "Tor") {
					pi.TorVersion = unquote(v)
				}
			}
		}
	}
	return pi, nil
}

// Authenticate performs control port authentication. SAFECOOKIE is preferred
// because it never puts the cookie itself on the wire; plain COOKIE and
// password auth are used as fallbacks.
func (c *Conn) Authenticate(ctx context.Context, password string) error {
	pi, err := c.ProtocolInfo(ctx)
	if err != nil {
		return err
	}

	var errs []error

	if pi.CookieFile != "" {
		cookie, rerr := os.ReadFile(pi.CookieFile)
		switch {
		case rerr != nil:
			errs = append(errs, fmt.Errorf("read cookie file: %w", rerr))
		case pi.HasMethod("SAFECOOKIE"):
			if err := c.authSafeCookie(ctx, cookie); err == nil {
				return nil
			} else {
				errs = append(errs, err)
			}
			fallthrough
		case pi.HasMethod("COOKIE"):
			r, err := c.Send(ctx, "AUTHENTICATE %s", hex.EncodeToString(cookie))
			if err != nil {
				return err
			}
			if r.IsOK() {
				return nil
			}
			errs = append(errs, r.err())
		}
	}

	if password != "" && pi.HasMethod("HASHEDPASSWORD") {
		r, err := c.Send(ctx, "AUTHENTICATE %q", password)
		if err != nil {
			return err
		}
		if r.IsOK() {
			return nil
		}
		errs = append(errs, r.err())
	}

	if pi.HasMethod("NULL") {
		r, err := c.Send(ctx, "AUTHENTICATE")
		if err != nil {
			return err
		}
		if r.IsOK() {
			return nil
		}
		errs = append(errs, r.err())
	}

	errs = append(errs, fmt.Errorf("methods offered: %s", strings.Join(pi.AuthMethods, ",")))
	return fmt.Errorf("%w: %w", ErrAuthFailed, errors.Join(errs...))
}

func (c *Conn) authSafeCookie(ctx context.Context, cookie []byte) error {
	clientNonce := make([]byte, 32)
	if _, err := rand.Read(clientNonce); err != nil {
		return err
	}
	r, err := c.Send(ctx, "AUTHCHALLENGE SAFECOOKIE %s", hex.EncodeToString(clientNonce))
	if err != nil {
		return err
	}
	if err := r.err(); err != nil {
		return err
	}

	var serverHash, serverNonce []byte
	for _, f := range splitQuoted(r.Text()) {
		k, v, ok := strings.Cut(f, "=")
		if !ok {
			continue
		}
		raw, derr := hex.DecodeString(unquote(v))
		if derr != nil {
			continue
		}
		switch strings.ToUpper(k) {
		case "SERVERHASH":
			serverHash = raw
		case "SERVERNONCE":
			serverNonce = raw
		}
	}
	if len(serverHash) == 0 || len(serverNonce) == 0 {
		return fmt.Errorf("tor control: malformed AUTHCHALLENGE reply %q", r.Text())
	}

	msg := make([]byte, 0, len(cookie)+len(clientNonce)+len(serverNonce))
	msg = append(msg, cookie...)
	msg = append(msg, clientNonce...)
	msg = append(msg, serverNonce...)

	if !hmac.Equal(hmacSHA256(safeCookieServerKey, msg), serverHash) {
		return errors.New("tor control: SAFECOOKIE server hash mismatch (wrong cookie file?)")
	}

	clientHash := hmacSHA256(safeCookieClientKey, msg)
	ar, err := c.Send(ctx, "AUTHENTICATE %s", hex.EncodeToString(clientHash))
	if err != nil {
		return err
	}
	return ar.err()
}

func hmacSHA256(key string, msg []byte) []byte {
	m := hmac.New(sha256.New, []byte(key))
	m.Write(msg)
	return m.Sum(nil)
}

// GetInfo runs GETINFO for one or more keys and returns key/value pairs.
func (c *Conn) GetInfo(ctx context.Context, keys ...string) (map[string]string, error) {
	if len(keys) == 0 {
		return map[string]string{}, nil
	}
	r, err := c.SendOK(ctx, "GETINFO %s", strings.Join(keys, " "))
	if err != nil {
		return nil, err
	}
	out := make(map[string]string, len(keys))
	for _, l := range r.Lines {
		if l.Text == "OK" && l.Data == "" {
			continue
		}
		k, v, ok := strings.Cut(l.Text, "=")
		if !ok {
			continue
		}
		if l.Data != "" {
			out[k] = l.Data
		} else {
			out[k] = v
		}
	}
	return out, nil
}

// GetInfoValue is GetInfo for a single key.
func (c *Conn) GetInfoValue(ctx context.Context, key string) (string, error) {
	m, err := c.GetInfo(ctx, key)
	if err != nil {
		return "", err
	}
	v, ok := m[key]
	if !ok {
		return "", fmt.Errorf("tor control: GETINFO %s returned no value", key)
	}
	return v, nil
}

// SetConf applies configuration options at runtime. Values are quoted, so they
// may contain spaces.
func (c *Conn) SetConf(ctx context.Context, kv map[string]string) error {
	if len(kv) == 0 {
		return nil
	}
	parts := make([]string, 0, len(kv))
	for k, v := range kv {
		if v == "" {
			parts = append(parts, k)
			continue
		}
		parts = append(parts, fmt.Sprintf("%s=%q", k, v))
	}
	_, err := c.SendOK(ctx, "SETCONF %s", strings.Join(parts, " "))
	return err
}

// SetEvents subscribes to the given asynchronous event types, replacing any
// previous subscription.
func (c *Conn) SetEvents(ctx context.Context, events ...string) error {
	_, err := c.SendOK(ctx, "SETEVENTS %s", strings.Join(events, " "))
	return err
}

// Signal sends a control signal such as NEWNYM, RELOAD or HEARTBEAT.
func (c *Conn) Signal(ctx context.Context, sig string) error {
	_, err := c.SendOK(ctx, "SIGNAL %s", sig)
	return err
}

// ExtendCircuit builds a new circuit (circuitID 0) or extends an existing one.
// An empty path lets Tor choose the hops itself. Returns the circuit ID.
func (c *Conn) ExtendCircuit(ctx context.Context, circuitID string, path []string) (string, error) {
	cmd := "EXTENDCIRCUIT " + circuitID
	if len(path) > 0 {
		cmd += " " + strings.Join(path, ",")
	}
	r, err := c.SendOK(ctx, "%s", cmd)
	if err != nil {
		return "", err
	}
	// Reply is "250 EXTENDED <circuit id>".
	fields := strings.Fields(r.Text())
	if len(fields) < 2 {
		return "", fmt.Errorf("tor control: unexpected EXTENDCIRCUIT reply %q", r.Text())
	}
	return fields[len(fields)-1], nil
}

// AttachStream attaches a pending stream to a circuit. Passing circuit ID "0"
// asks Tor to pick a circuit itself.
func (c *Conn) AttachStream(ctx context.Context, streamID, circuitID string) error {
	_, err := c.SendOK(ctx, "ATTACHSTREAM %s %s", streamID, circuitID)
	return err
}

// CloseCircuit tears a circuit down.
func (c *Conn) CloseCircuit(ctx context.Context, circuitID string) error {
	_, err := c.SendOK(ctx, "CLOSECIRCUIT %s", circuitID)
	return err
}

// CloseStream tears a stream down with the given RELAY_END reason code
// (1 = REASON_MISC).
func (c *Conn) CloseStream(ctx context.Context, streamID string, reason int) error {
	_, err := c.SendOK(ctx, "CLOSESTREAM %s %d", streamID, reason)
	return err
}

// AddEphemeralOnion creates an ephemeral v3 onion service forwarding
// virtualPort to target. Returns the .onion address without the ".onion"
// suffix.
//
// The key is discarded and the service is deliberately not detached, so it
// disappears when this control connection closes. A detached service would
// outlive TorVeil and keep a published descriptor pointing at a machine that
// is no longer listening.
func (c *Conn) AddEphemeralOnion(ctx context.Context, virtualPort int, target string) (string, error) {
	r, err := c.SendOK(ctx, "ADD_ONION NEW:ED25519-V3 Flags=DiscardPK Port=%d,%s", virtualPort, target)
	if err != nil {
		return "", err
	}
	for _, l := range r.Lines {
		if k, v, ok := strings.Cut(l.Text, "="); ok && strings.EqualFold(k, "ServiceID") {
			return v, nil
		}
	}
	return "", fmt.Errorf("tor control: ADD_ONION returned no ServiceID: %q", r.Text())
}

// DelOnion removes an ephemeral onion service previously added.
func (c *Conn) DelOnion(ctx context.Context, serviceID string) error {
	_, err := c.SendOK(ctx, "DEL_ONION %s", serviceID)
	return err
}

// splitQuoted splits a control-protocol line on spaces, keeping double-quoted
// runs (with backslash escapes) together.
func splitQuoted(s string) []string {
	var (
		out     []string
		cur     strings.Builder
		inQuote bool
		escaped bool
		started bool
	)
	flush := func() {
		if started {
			out = append(out, cur.String())
			cur.Reset()
			started = false
		}
	}
	for _, r := range s {
		switch {
		case escaped:
			cur.WriteRune(r)
			escaped = false
			started = true
		case r == '\\' && inQuote:
			cur.WriteRune(r)
			escaped = true
		case r == '"':
			inQuote = !inQuote
			cur.WriteRune(r)
			started = true
		case r == ' ' && !inQuote:
			flush()
		default:
			cur.WriteRune(r)
			started = true
		}
	}
	flush()
	return out
}

// unquote removes surrounding double quotes and undoes backslash escapes.
func unquote(s string) string {
	if len(s) < 2 || s[0] != '"' || s[len(s)-1] != '"' {
		return s
	}
	s = s[1 : len(s)-1]
	var sb strings.Builder
	sb.Grow(len(s))
	escaped := false
	for _, r := range s {
		if escaped {
			switch r {
			case 'n':
				sb.WriteByte('\n')
			case 'r':
				sb.WriteByte('\r')
			case 't':
				sb.WriteByte('\t')
			default:
				sb.WriteRune(r)
			}
			escaped = false
			continue
		}
		if r == '\\' {
			escaped = true
			continue
		}
		sb.WriteRune(r)
	}
	return sb.String()
}
