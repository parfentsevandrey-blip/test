// Package mail implements mail and chat between devices of the mesh.
//
// A message is created and signed by the sending device (Ed25519, the device
// key is its identity), then delivered to each recipient device with
// acknowledgements and retries: if a recipient is offline the message waits in
// the sender's outbox and goes out the moment the recipient appears. Delivery
// is idempotent (messages are de-duplicated by ID), so a lost acknowledgement
// can never produce a duplicate. Attachments are content-addressed blobs that
// the recipient fetches from the sender (or any other member that has them) and
// verifies by hash.
package mail

import (
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"regexp"
	"sort"
	"strings"
	"sync"
	"time"
	"unicode"
	"unicode/utf8"

	"github.com/parfentsevandrey-blip/test/svoi/internal/blob"
	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/mesh"
	"github.com/parfentsevandrey-blip/test/svoi/internal/store"
)

// Folders.
const (
	FolderInbox = "inbox"
	FolderSent  = "sent"
	FolderTrash = "trash"
	FolderChat  = "chat"
)

// Delivery states.
const (
	DelQueued    = "queued"
	DelSent      = "sent"
	DelDelivered = "delivered"
	DelFailed    = "failed"
)

// Attachment states.
const (
	AttReady    = "ready"
	AttFetching = "fetching"
	AttRemote   = "remote"
	AttFailed   = "failed"
)

const (
	bucketMsgs    = "mail"
	bucketUploads = "uploads"
	maxRawSize    = 4 << 20
	maxBody       = 1 << 20
	maxAttach     = 50
	maxRecipients = 256
)

// Attachment describes a file attached to a message.
type Attachment struct {
	Name   string `json:"name"`
	Size   int64  `json:"size"`
	Mime   string `json:"mime"`
	SHA256 string `json:"sha256"`
}

// core is the immutable, signed content of a message.
type core struct {
	ID        string        `json:"id"`
	Kind      string        `json:"kind"` // mail | chat
	From      identity.ID   `json:"from"`
	To        []identity.ID `json:"to"`
	Subject   string        `json:"subject,omitempty"`
	Body      string        `json:"body"`
	Attach    []Attachment  `json:"attach,omitempty"`
	Created   int64         `json:"created"`
	InReplyTo string        `json:"inReplyTo,omitempty"`
	Thread    string        `json:"thread,omitempty"`
}

type delivery struct {
	State    string `json:"state"`
	At       int64  `json:"at,omitempty"`
	Attempts int    `json:"attempts,omitempty"`
	Next     int64  `json:"next,omitempty"`
	Err      string `json:"err,omitempty"`
}

type fetchState struct {
	State string `json:"state"`
	Got   int64  `json:"got"`
}

type record struct {
	Core     core                   `json:"core"`
	Raw      json.RawMessage        `json:"raw"`
	Sig      []byte                 `json:"sig"`
	Folder   string                 `json:"folder"`
	Prev     string                 `json:"prev,omitempty"`
	Unread   bool                   `json:"unread"`
	Starred  bool                   `json:"starred,omitempty"`
	Received int64                  `json:"received,omitempty"`
	Delivery map[string]*delivery   `json:"delivery,omitempty"`
	Fetch    map[string]*fetchState `json:"fetch,omitempty"`
}

// signedMsg is the wire form.
type signedMsg struct {
	Core json.RawMessage `json:"core"`
	Sig  []byte          `json:"sig"`
}

// Event is published when mail or chat changes.
type Event struct {
	Kind   string // "mail" | "chat" | "counters"
	ID     string
	Folder string
	Unread bool
	Peer   identity.ID
}

// Manager owns the mailbox of this device.
type Manager struct {
	node  *mesh.Node
	db    *store.DB
	blobs *blob.Store
	emit  func(Event)

	mu       sync.Mutex
	msgs     map[string]*record
	inflight map[string]bool
	kick     chan struct{}
}

// New loads the mailbox from the database.
func New(node *mesh.Node, db *store.DB, blobs *blob.Store, emit func(Event)) (*Manager, error) {
	m := &Manager{
		node: node, db: db, blobs: blobs, emit: emit,
		msgs: map[string]*record{}, inflight: map[string]bool{}, kick: make(chan struct{}, 1),
	}
	err := db.ForEach(bucketMsgs, func(key string, raw []byte) error {
		var r record
		if err := json.Unmarshal(raw, &r); err != nil {
			return nil
		}
		for _, f := range r.Fetch {
			if f.State == AttFetching {
				f.State = AttRemote
			}
		}
		m.msgs[r.Core.ID] = &r
		return nil
	})
	if err != nil {
		return nil, err
	}
	return m, nil
}

func (m *Manager) fire(e Event) {
	if m.emit != nil {
		go m.emit(e)
	}
}

func (m *Manager) save(r *record) {
	if err := m.db.PutJSON(bucketMsgs, r.Core.ID, r); err != nil {
		m.node.Logger().Error("saving message failed", "err", err)
	}
}

// Kick wakes the delivery worker.
func (m *Manager) Kick() {
	select {
	case m.kick <- struct{}{}:
	default:
	}
}

// Run delivers queued messages and fetches attachments until ctx ends.
func (m *Manager) Run(ctx context.Context) {
	events, cancel := m.node.Subscribe()
	defer cancel()
	tick := time.NewTicker(3 * time.Second)
	defer tick.Stop()
	m.pump(ctx)
	for {
		select {
		case <-ctx.Done():
			return
		case <-tick.C:
		case <-m.kick:
		case ev := <-events:
			if ev.Kind != mesh.EvPeer {
				continue
			}
		}
		m.pump(ctx)
	}
}

// newID makes a message id that carries its author: "m_<author>_<random>". The
// receiver checks that the author part is the device that signed the message, so
// two different devices can never produce the same id (and nobody can pre-empt
// another device's message by announcing its id first).
func newID(prefix string, author identity.ID) string {
	b := make([]byte, 9)
	_, _ = rand.Read(b)
	return prefix + author.Short() + "_" + hex.EncodeToString(b)
}

var msgIDRe = regexp.MustCompile(`^[mc]_[a-z2-7]{8}_[0-9a-f]{18}$`)

// validMsgID checks the shape of a message id and that it belongs to its author.
func validMsgID(id string, author identity.ID) bool {
	return msgIDRe.MatchString(id) && id[2:10] == author.Short()
}

// cleanText removes control characters (other than newlines and tabs) from a
// peer-supplied string and cuts it to max bytes on a rune boundary.
func cleanText(s string, max int) string {
	s = strings.Map(func(r rune) rune {
		if (r < 32 && r != '\n' && r != '\t') || r == 127 || unicode.Is(unicode.Cf, r) {
			return -1
		}
		return r
	}, s)
	if len(s) > max {
		s = s[:max]
		for len(s) > 0 && !utf8.RuneStart(s[len(s)-1]) {
			s = s[:len(s)-1]
		}
		if !utf8.ValidString(s) {
			s = strings.ToValidUTF8(s, "")
		}
	}
	return s
}

// ---- sending ----

// SendInput describes a message to send.
type SendInput struct {
	Kind      string // mail | chat
	To        []identity.ID
	Subject   string
	Body      string
	Attach    []string // blob hashes uploaded earlier
	InReplyTo string
}

// RegisterUpload remembers the name and type of an uploaded blob so it can be
// attached to a message by hash alone.
func (m *Manager) RegisterUpload(sha, name, mimeType string, size int64) error {
	return m.db.PutJSON(bucketUploads, sha, Attachment{Name: name, Size: size, Mime: mimeType, SHA256: sha})
}

// Send creates, signs and queues a message.
func (m *Manager) Send(in SendInput) (string, error) {
	if in.Kind != "mail" && in.Kind != "chat" {
		return "", mesh.Errf(mesh.CodeInvalid, "unknown message kind")
	}
	if len(in.To) == 0 {
		return "", mesh.Errf(mesh.CodeInvalid, "choose at least one recipient")
	}
	if len(in.Body) > maxBody || len(in.Subject) > 1000 {
		return "", mesh.Errf(mesh.CodeTooLarge, "message is too long")
	}
	if strings.TrimSpace(in.Body) == "" && len(in.Attach) == 0 && strings.TrimSpace(in.Subject) == "" {
		return "", mesh.Errf(mesh.CodeInvalid, "the message is empty")
	}
	if len(in.Attach) > maxAttach {
		return "", mesh.Errf(mesh.CodeInvalid, "too many attachments")
	}
	self := m.node.ID()
	seen := map[identity.ID]bool{}
	var to []identity.ID
	for _, id := range in.To {
		if seen[id] {
			continue
		}
		seen[id] = true
		if id != self && m.node.Peer(id) == nil {
			return "", mesh.Errf(mesh.CodeNotFound, "unknown recipient")
		}
		to = append(to, id)
	}
	c := core{
		Kind: in.Kind, From: self, To: to, Subject: strings.TrimSpace(in.Subject), Body: in.Body,
		Created: time.Now().Unix(), InReplyTo: in.InReplyTo,
	}
	if in.Kind == "chat" {
		c.ID = newID("c_", self)
		if len(to) != 1 {
			return "", mesh.Errf(mesh.CodeInvalid, "a chat message has exactly one recipient")
		}
	} else {
		c.ID = newID("m_", self)
	}
	for _, sha := range in.Attach {
		var a Attachment
		ok, err := m.db.GetJSON(bucketUploads, sha, &a)
		if err != nil {
			return "", err
		}
		size, have := m.blobs.Has(sha)
		if !have {
			return "", mesh.Errf(mesh.CodeNotFound, "attachment was not uploaded")
		}
		if !ok {
			a = Attachment{Name: sha[:8], Mime: "application/octet-stream", SHA256: sha}
		}
		a.Size = size
		c.Attach = append(c.Attach, a)
	}
	m.mu.Lock()
	c.Thread = c.ID
	if in.InReplyTo != "" {
		if prev := m.msgs[in.InReplyTo]; prev != nil && prev.Core.Thread != "" {
			c.Thread = prev.Core.Thread
		}
	}
	raw, err := json.Marshal(c)
	if err != nil {
		m.mu.Unlock()
		return "", err
	}
	sig := ed25519.Sign(m.node.Device().Priv, raw)
	r := &record{Core: c, Raw: raw, Sig: sig, Delivery: map[string]*delivery{}}
	switch {
	case in.Kind == "chat":
		r.Folder = FolderChat
	default:
		r.Folder = FolderSent
	}
	onlySelf := len(to) == 1 && to[0] == self
	for _, id := range to {
		if id == self {
			continue
		}
		r.Delivery[id.String()] = &delivery{State: DelQueued}
	}
	if onlySelf { // a note to self lands in the inbox, already read
		r.Folder = FolderInbox
		if in.Kind == "chat" {
			r.Folder = FolderChat
		}
	}
	m.msgs[c.ID] = r
	m.save(r)
	m.mu.Unlock()
	m.fire(Event{Kind: in.Kind, ID: c.ID, Folder: r.Folder, Peer: chatPeer(r, self)})
	m.fireCounters()
	m.Kick()
	return c.ID, nil
}

func chatPeer(r *record, self identity.ID) identity.ID {
	if r.Core.Kind != "chat" {
		return identity.ID{}
	}
	if r.Core.From == self {
		return r.Core.To[0]
	}
	return r.Core.From
}

// ---- delivery worker ----

func (m *Manager) pump(ctx context.Context) {
	now := time.Now().Unix()
	type job struct {
		r    *record
		peer *mesh.Peer
	}
	var jobs []job
	var fetches []*record
	m.mu.Lock()
	for _, r := range m.msgs {
		if r.Core.From == m.node.ID() {
			for idStr, d := range r.Delivery {
				if d.State != DelQueued || now < d.Next {
					continue
				}
				id, err := identity.ParseID(idStr)
				if err != nil {
					continue
				}
				p := m.node.Peer(id)
				if p == nil {
					d.State, d.Err = DelFailed, "device was removed from the mesh"
					m.save(r)
					continue
				}
				if !p.Online() || m.inflight[r.Core.ID+"|"+idStr] {
					continue
				}
				m.inflight[r.Core.ID+"|"+idStr] = true
				jobs = append(jobs, job{r, p})
			}
		} else {
			for _, a := range r.Core.Attach {
				if f := r.Fetch[a.SHA256]; f != nil && f.State == AttRemote && !m.inflight["f|"+a.SHA256] {
					fetches = append(fetches, r)
					break
				}
			}
		}
	}
	m.mu.Unlock()
	for _, j := range jobs {
		go m.deliver(ctx, j.r, j.peer)
	}
	for _, r := range fetches {
		go m.fetchAttachments(ctx, r)
	}
}

func (m *Manager) deliver(ctx context.Context, r *record, p *mesh.Peer) {
	key := r.Core.ID + "|" + p.ID.String()
	defer func() {
		m.mu.Lock()
		delete(m.inflight, key)
		m.mu.Unlock()
	}()
	m.mu.Lock()
	msg := signedMsg{Core: r.Raw, Sig: r.Sig}
	m.mu.Unlock()
	cctx, cancel := context.WithTimeout(ctx, 30*time.Second)
	defer cancel()
	err := p.Call(cctx, "mail.deliver", msg, nil)

	m.mu.Lock()
	d := r.Delivery[p.ID.String()]
	if d == nil {
		m.mu.Unlock()
		return
	}
	d.Attempts++
	switch {
	case err == nil:
		d.State, d.At, d.Err = DelDelivered, time.Now().Unix(), ""
	case mesh.IsCode(err, mesh.CodeDenied), mesh.IsCode(err, mesh.CodeInvalid), mesh.IsCode(err, mesh.CodeTooLarge):
		d.State, d.Err = DelFailed, describe(err)
	default:
		backoff := time.Duration(1<<min(d.Attempts, 6)) * 5 * time.Second
		if backoff > 5*time.Minute {
			backoff = 5 * time.Minute
		}
		d.Next = time.Now().Add(backoff).Unix()
		d.Err = describe(err)
	}
	m.save(r)
	self := m.node.ID()
	ev := Event{Kind: r.Core.Kind, ID: r.Core.ID, Folder: r.Folder, Peer: chatPeer(r, self)}
	m.mu.Unlock()
	m.fire(ev)
}

func describe(err error) string {
	var re *mesh.RPCError
	if errors.As(err, &re) {
		return re.Msg
	}
	return err.Error()
}

// ---- receiving ----

// Register installs the RPC handlers.
func (m *Manager) Register() {
	m.node.Handle("mail.deliver", func(ctx context.Context, c *mesh.Call) (any, error) {
		var msg signedMsg
		if err := c.Decode(&msg); err != nil {
			return nil, err
		}
		if len(msg.Core) == 0 || len(msg.Core) > maxRawSize {
			return nil, mesh.Errf(mesh.CodeInvalid, "bad message")
		}
		var cr core
		if err := json.Unmarshal(msg.Core, &cr); err != nil {
			return nil, mesh.Errf(mesh.CodeInvalid, "bad message: %v", err)
		}
		// The sender is the device that delivers it; its signature proves the
		// content was not altered on the way (or by a relay).
		if cr.From != c.Peer.ID {
			return nil, mesh.Errf(mesh.CodeDenied, "sender mismatch")
		}
		if !ed25519.Verify(cr.From.PublicKey(), msg.Core, msg.Sig) {
			return nil, mesh.Errf(mesh.CodeDenied, "bad signature")
		}
		if (cr.Kind != "mail" && cr.Kind != "chat") || !validMsgID(cr.ID, cr.From) || cr.ID[0] != cr.Kind[0] ||
			len(cr.Body) > maxBody || len(cr.Attach) > maxAttach || len(cr.Subject) > 1000 || len(cr.To) > maxRecipients ||
			len(cr.InReplyTo) > 64 || len(cr.Thread) > 64 {
			return nil, mesh.Errf(mesh.CodeInvalid, "bad message")
		}
		self := m.node.ID()
		forMe := false
		for _, id := range cr.To {
			if id == self {
				forMe = true
			}
		}
		if !forMe || (cr.Kind == "chat" && len(cr.To) != 1) {
			return nil, mesh.Errf(mesh.CodeInvalid, "not addressed to this device")
		}
		for i := range cr.Attach {
			a := &cr.Attach[i]
			if !blob.ValidSHA(a.SHA256) || a.Size < 0 {
				return nil, mesh.Errf(mesh.CodeInvalid, "bad attachment")
			}
			// The signed bytes (Raw) stay as they arrived; what is shown is cleaned.
			a.Name, a.Mime = cleanText(a.Name, 255), cleanText(a.Mime, 100)
		}
		m.mu.Lock()
		if _, dup := m.msgs[cr.ID]; dup {
			m.mu.Unlock()
			return map[string]bool{"ok": true}, nil // already have it: re-ack
		}
		r := &record{
			Core: cr, Raw: msg.Core, Sig: msg.Sig, Folder: FolderInbox, Unread: true,
			Received: time.Now().Unix(), Fetch: map[string]*fetchState{},
		}
		if cr.Kind == "chat" {
			r.Folder = FolderChat
		}
		for _, a := range cr.Attach {
			st := AttRemote
			if _, ok := m.blobs.Has(a.SHA256); ok {
				st = AttReady
			}
			r.Fetch[a.SHA256] = &fetchState{State: st}
		}
		m.msgs[cr.ID] = r
		m.save(r)
		ev := Event{Kind: cr.Kind, ID: cr.ID, Folder: r.Folder, Unread: true, Peer: chatPeer(r, self)}
		m.mu.Unlock()
		m.fire(ev)
		m.fireCounters()
		m.Kick()
		return map[string]bool{"ok": true}, nil
	})
}

// fetchAttachments downloads the blobs of a received message.
func (m *Manager) fetchAttachments(ctx context.Context, r *record) {
	for _, a := range r.Core.Attach {
		m.mu.Lock()
		f := r.Fetch[a.SHA256]
		if f == nil || f.State == AttReady || f.State == AttFetching || m.inflight["f|"+a.SHA256] {
			m.mu.Unlock()
			continue
		}
		m.inflight["f|"+a.SHA256] = true
		f.State = AttFetching
		m.mu.Unlock()

		ok := m.fetchOne(ctx, r, a)

		m.mu.Lock()
		delete(m.inflight, "f|"+a.SHA256)
		if ok {
			f.State, f.Got = AttReady, a.Size
		} else if f.State == AttFetching {
			f.State = AttRemote // try again later, when someone who has it is online
		}
		m.save(r)
		ev := Event{Kind: r.Core.Kind, ID: r.Core.ID, Folder: r.Folder, Peer: chatPeer(r, m.node.ID())}
		m.mu.Unlock()
		m.fire(ev)
	}
}

func (m *Manager) fetchOne(ctx context.Context, r *record, a Attachment) bool {
	// The sender is the most likely holder; fall back to any other online member.
	var peers []*mesh.Peer
	if p := m.node.Peer(r.Core.From); p != nil && p.Online() {
		peers = append(peers, p)
	}
	for _, p := range m.node.Peers() {
		if p.ID != r.Core.From && p.Online() {
			peers = append(peers, p)
		}
	}
	for _, p := range peers {
		cctx, cancel := context.WithTimeout(ctx, 30*time.Minute)
		err := m.blobs.Fetch(cctx, p, a.SHA256, a.Size, func(n int64) {
			m.mu.Lock()
			if f := r.Fetch[a.SHA256]; f != nil {
				f.Got = n
			}
			m.mu.Unlock()
		})
		cancel()
		if err == nil {
			return true
		}
	}
	return false
}

// ---- queries ----

// Person is a device reference in API output.
type Person struct {
	ID   identity.ID `json:"id"`
	Name string      `json:"name"`
}

// Recipient is a recipient with its delivery state.
type Recipient struct {
	ID    identity.ID `json:"id"`
	Name  string      `json:"name"`
	State string      `json:"state"`
	At    *int64      `json:"at"`
}

// Summary is the list view of a message.
type Summary struct {
	ID          string      `json:"id"`
	Kind        string      `json:"kind"`
	Folder      string      `json:"folder"`
	From        Person      `json:"from"`
	To          []Recipient `json:"to"`
	Subject     string      `json:"subject"`
	Snippet     string      `json:"snippet"`
	TS          int64       `json:"ts"`
	Unread      bool        `json:"unread"`
	Attachments int         `json:"attachments"`
	Thread      string      `json:"thread"`
	Starred     bool        `json:"starred"`
}

// AttachmentView is an attachment with its local availability.
type AttachmentView struct {
	Attachment
	State string `json:"state"`
	Got   int64  `json:"got"`
}

// Message is the full view.
type Message struct {
	Summary
	Body      string  `json:"body"`
	InReplyTo *string `json:"inReplyTo"`
	// Attachments shadows Summary.Attachments (the count) with the full list.
	Attachments []AttachmentView `json:"attachments"`
}

func (m *Manager) name(id identity.ID) string {
	if id == m.node.ID() {
		return m.node.Self().Name
	}
	if p := m.node.Peer(id); p != nil {
		return p.Name()
	}
	return id.Short()
}

func snippet(s string) string {
	s = strings.Join(strings.Fields(s), " ")
	if utf8.RuneCountInString(s) > 140 {
		rs := []rune(s)
		s = string(rs[:140]) + "…"
	}
	return s
}

func (m *Manager) summaryLocked(r *record) Summary {
	c := r.Core
	s := Summary{
		ID: c.ID, Kind: c.Kind, Folder: r.Folder, From: Person{ID: c.From, Name: m.name(c.From)},
		Subject: c.Subject, Snippet: snippet(c.Body), TS: c.Created, Unread: r.Unread,
		Attachments: len(c.Attach), Thread: c.Thread, Starred: r.Starred,
	}
	for _, id := range c.To {
		rc := Recipient{ID: id, Name: m.name(id), State: DelDelivered}
		if d := r.Delivery[id.String()]; d != nil {
			rc.State = d.State
			if d.At != 0 {
				at := d.At
				rc.At = &at
			}
		} else if id == m.node.ID() && c.From == m.node.ID() {
			at := c.Created
			rc.At = &at
		}
		s.To = append(s.To, rc)
	}
	return s
}

// ListResult is a page of messages.
type ListResult struct {
	Items  []Summary `json:"items"`
	Total  int       `json:"total"`
	Unread int       `json:"unread"`
}

// List returns messages of a mail folder, newest first.
func (m *Manager) List(folder, query string, limit int, before int64) ListResult {
	if limit <= 0 || limit > 200 {
		limit = 50
	}
	query = strings.ToLower(strings.TrimSpace(query))
	m.mu.Lock()
	defer m.mu.Unlock()
	var matches []*record
	unread := 0
	for _, r := range m.msgs {
		if r.Core.Kind != "mail" {
			continue
		}
		if r.Folder != folder {
			continue
		}
		if r.Unread {
			unread++
		}
		if query != "" {
			hay := strings.ToLower(r.Core.Subject + "\n" + r.Core.Body + "\n" + m.name(r.Core.From))
			if !strings.Contains(hay, query) {
				continue
			}
		}
		matches = append(matches, r)
	}
	sort.Slice(matches, func(i, j int) bool {
		if matches[i].Core.Created != matches[j].Core.Created {
			return matches[i].Core.Created > matches[j].Core.Created
		}
		return matches[i].Core.ID > matches[j].Core.ID
	})
	res := ListResult{Total: len(matches), Unread: unread, Items: []Summary{}}
	for _, r := range matches {
		if before > 0 && r.Core.Created >= before {
			continue
		}
		res.Items = append(res.Items, m.summaryLocked(r))
		if len(res.Items) >= limit {
			break
		}
	}
	return res
}

// Get returns a full message.
func (m *Manager) Get(id string) (*Message, bool) {
	m.mu.Lock()
	defer m.mu.Unlock()
	r := m.msgs[id]
	if r == nil {
		return nil, false
	}
	msg := &Message{Summary: m.summaryLocked(r), Body: r.Core.Body}
	if r.Core.InReplyTo != "" {
		s := r.Core.InReplyTo
		msg.InReplyTo = &s
	}
	msg.Attachments = m.attachViewsLocked(r)
	return msg, true
}

func (m *Manager) attachViewsLocked(r *record) []AttachmentView {
	out := make([]AttachmentView, 0, len(r.Core.Attach))
	for _, a := range r.Core.Attach {
		v := AttachmentView{Attachment: a, State: AttReady, Got: a.Size}
		if _, ok := m.blobs.Has(a.SHA256); !ok {
			v.State = AttRemote
			v.Got = 0
			if f := r.Fetch[a.SHA256]; f != nil {
				v.State, v.Got = f.State, f.Got
			}
		}
		out = append(out, v)
	}
	return out
}

// AttachmentFile resolves attachment idx of a message to a local file.
func (m *Manager) AttachmentFile(id string, idx int) (path string, att Attachment, err error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	r := m.msgs[id]
	if r == nil || idx < 0 || idx >= len(r.Core.Attach) {
		return "", Attachment{}, mesh.Errf(mesh.CodeNotFound, "no such attachment")
	}
	a := r.Core.Attach[idx]
	if _, ok := m.blobs.Has(a.SHA256); !ok {
		return "", a, mesh.Errf(mesh.CodeExists, "the attachment has not been downloaded yet")
	}
	return m.blobs.Path(a.SHA256), a, nil
}

// Flags selects fields to change; nil means "leave as is".
type Flags struct {
	Unread  *bool   `json:"unread"`
	Starred *bool   `json:"starred"`
	Folder  *string `json:"folder"`
}

// SetFlags updates read/star state or moves a message between folders.
func (m *Manager) SetFlags(id string, f Flags) error {
	m.mu.Lock()
	r := m.msgs[id]
	if r == nil {
		m.mu.Unlock()
		return mesh.Errf(mesh.CodeNotFound, "no such message")
	}
	if f.Unread != nil {
		r.Unread = *f.Unread
	}
	if f.Starred != nil {
		r.Starred = *f.Starred
	}
	if f.Folder != nil && r.Core.Kind == "mail" {
		switch *f.Folder {
		case FolderTrash:
			if r.Folder != FolderTrash {
				r.Prev, r.Folder = r.Folder, FolderTrash
			}
		case FolderInbox, FolderSent:
			r.Folder, r.Prev = *f.Folder, ""
		default:
			m.mu.Unlock()
			return mesh.Errf(mesh.CodeInvalid, "unknown folder")
		}
	}
	m.save(r)
	ev := Event{Kind: r.Core.Kind, ID: id, Folder: r.Folder, Unread: r.Unread, Peer: chatPeer(r, m.node.ID())}
	m.mu.Unlock()
	m.fire(ev)
	m.fireCounters()
	return nil
}

// Delete moves a message to the trash, or removes it for good if it is already there.
func (m *Manager) Delete(id string) error {
	m.mu.Lock()
	r := m.msgs[id]
	if r == nil {
		m.mu.Unlock()
		return mesh.Errf(mesh.CodeNotFound, "no such message")
	}
	if r.Folder == FolderTrash || r.Core.Kind == "chat" {
		delete(m.msgs, id)
		_ = m.db.Delete(bucketMsgs, id)
		m.gcBlobsLocked(r)
		ev := Event{Kind: r.Core.Kind, ID: id, Folder: r.Folder, Peer: chatPeer(r, m.node.ID())}
		m.mu.Unlock()
		m.fire(ev)
		m.fireCounters()
		return nil
	}
	m.mu.Unlock()
	to := FolderTrash
	return m.SetFlags(id, Flags{Folder: &to})
}

// gcBlobsLocked removes attachment blobs no remaining message refers to.
func (m *Manager) gcBlobsLocked(gone *record) {
	for _, a := range gone.Core.Attach {
		used := false
		for _, o := range m.msgs {
			for _, oa := range o.Core.Attach {
				if oa.SHA256 == a.SHA256 {
					used = true
				}
			}
		}
		if !used {
			m.blobs.Remove(a.SHA256)
		}
	}
}

// Counters returns unread totals for mail (inbox) and chat.
func (m *Manager) Counters() (mailUnread, chatUnread int) {
	m.mu.Lock()
	defer m.mu.Unlock()
	for _, r := range m.msgs {
		if !r.Unread {
			continue
		}
		switch {
		case r.Core.Kind == "mail" && r.Folder == FolderInbox:
			mailUnread++
		case r.Core.Kind == "chat" && r.Core.From != m.node.ID():
			chatUnread++
		}
	}
	return
}

func (m *Manager) fireCounters() { m.fire(Event{Kind: "counters"}) }

// ---- chat ----

// ChatThread is one conversation in the list.
type ChatThread struct {
	Peer   ChatPeer `json:"peer"`
	Last   ChatLast `json:"last"`
	Unread int      `json:"unread"`
}

// ChatPeer identifies the other side.
type ChatPeer struct {
	ID     identity.ID `json:"id"`
	Name   string      `json:"name"`
	Online bool        `json:"online"`
}

// ChatLast is the latest message of a thread.
type ChatLast struct {
	Text  string      `json:"text"`
	TS    int64       `json:"ts"`
	From  identity.ID `json:"from"`
	State string      `json:"state"`
}

// ChatMessage is one chat message.
type ChatMessage struct {
	ID          string           `json:"id"`
	From        identity.ID      `json:"from"`
	To          identity.ID      `json:"to"`
	Mine        bool             `json:"mine"`
	Text        string           `json:"text"`
	TS          int64            `json:"ts"`
	State       string           `json:"state"`
	Attachments []AttachmentView `json:"attachments"`
}

func (m *Manager) chatMsgLocked(r *record) ChatMessage {
	self := m.node.ID()
	c := r.Core
	cm := ChatMessage{ID: c.ID, From: c.From, To: c.To[0], Mine: c.From == self, Text: c.Body, TS: c.Created, State: DelDelivered}
	if d := r.Delivery[c.To[0].String()]; d != nil {
		cm.State = d.State
	}
	cm.Attachments = m.attachViewsLocked(r)
	return cm
}

// ChatThreads lists conversations, newest first.
func (m *Manager) ChatThreads() []ChatThread {
	m.mu.Lock()
	defer m.mu.Unlock()
	self := m.node.ID()
	byPeer := map[identity.ID]*ChatThread{}
	for _, r := range m.msgs {
		if r.Core.Kind != "chat" {
			continue
		}
		peer := chatPeer(r, self)
		th := byPeer[peer]
		if th == nil {
			th = &ChatThread{Peer: ChatPeer{ID: peer, Name: m.name(peer)}}
			if p := m.node.Peer(peer); p != nil {
				th.Peer.Online = p.Online()
			}
			byPeer[peer] = th
		}
		if r.Unread && r.Core.From != self {
			th.Unread++
		}
		if r.Core.Created >= th.Last.TS {
			cm := m.chatMsgLocked(r)
			text := cm.Text
			if text == "" && len(cm.Attachments) > 0 {
				text = "📎 " + cm.Attachments[0].Name
			}
			th.Last = ChatLast{Text: snippet(text), TS: cm.TS, From: cm.From, State: cm.State}
		}
	}
	out := make([]ChatThread, 0, len(byPeer))
	for _, th := range byPeer {
		out = append(out, *th)
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Last.TS > out[j].Last.TS })
	return out
}

// ChatMessages returns a page of a conversation, oldest to newest.
func (m *Manager) ChatMessages(peer identity.ID, before int64, limit int) []ChatMessage {
	if limit <= 0 || limit > 200 {
		limit = 50
	}
	m.mu.Lock()
	defer m.mu.Unlock()
	self := m.node.ID()
	var rs []*record
	for _, r := range m.msgs {
		if r.Core.Kind == "chat" && chatPeer(r, self) == peer && (before <= 0 || r.Core.Created < before) {
			rs = append(rs, r)
		}
	}
	sort.Slice(rs, func(i, j int) bool {
		if rs[i].Core.Created != rs[j].Core.Created {
			return rs[i].Core.Created < rs[j].Core.Created
		}
		return rs[i].Core.ID < rs[j].Core.ID
	})
	if len(rs) > limit {
		rs = rs[len(rs)-limit:]
	}
	out := make([]ChatMessage, 0, len(rs))
	for _, r := range rs {
		out = append(out, m.chatMsgLocked(r))
	}
	return out
}

// ChatMessageByID returns one chat message and the peer it belongs to.
func (m *Manager) ChatMessageByID(id string) (ChatMessage, identity.ID, bool) {
	m.mu.Lock()
	defer m.mu.Unlock()
	r := m.msgs[id]
	if r == nil || r.Core.Kind != "chat" {
		return ChatMessage{}, identity.ID{}, false
	}
	return m.chatMsgLocked(r), chatPeer(r, m.node.ID()), true
}

// ChatRead marks every message of a conversation as read.
func (m *Manager) ChatRead(peer identity.ID) {
	m.mu.Lock()
	self := m.node.ID()
	changed := false
	for _, r := range m.msgs {
		if r.Core.Kind == "chat" && r.Unread && r.Core.From != self && chatPeer(r, self) == peer {
			r.Unread = false
			m.save(r)
			changed = true
		}
	}
	m.mu.Unlock()
	if changed {
		m.fireCounters()
	}
}
