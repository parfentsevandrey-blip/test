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
	"sync/atomic"
	"time"
	"unicode"
	"unicode/utf8"

	"github.com/parfentsevandrey-blip/test/svoi/internal/blob"
	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/inetmail"
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
	// ContentID: the HTML of a letter from the Internet shows this file as cid:<ContentID> (a picture inside the text).
	ContentID string `json:"cid,omitempty"`
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
	// Ext: the letter has to do with the Internet (ext.go).
	Ext *Ext `json:"ext,omitempty"`
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
	// Via: we pulled this attachment from the message's author. Only then may it be
	// passed on to the other recipients (see blobPeers).
	Via   bool  `json:"via,omitempty"`
	Want  bool  `json:"want,omitempty"` // the user asked for it: consent for a large attachment
	Fails int   `json:"fails,omitempty"`
	Next  int64 `json:"next,omitempty"` // do not try again before this time (unix seconds)
}

// Attachments are pulled from the sender automatically only while small; a
// larger one waits until the user asks for it (FetchAttachment). Failures back
// off and eventually stop, so a sender cannot keep us busy and writing forever.
const (
	maxAutoFetch       = 25 << 20
	maxFetchTries      = 8
	maxConcurrentFetch = 2
)

func fetchBackoff(fails int) time.Duration {
	d := 30 * time.Second << min(fails, 10)
	if d > 6*time.Hour {
		d = 6 * time.Hour
	}
	return d
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
	// ExtDel: how the letter fared on its way to each address on the Internet; OutTaken: the gateway of this device has it.
	ExtDel   map[string]*extDelivery `json:"extDel,omitempty"`
	OutTaken bool                    `json:"outTaken,omitempty"`
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
	fetching int // attachment downloads running
	kick     chan struct{}

	// blobAuth says, per attachment hash, which devices the messages we hold let us
	// hand that blob to (and how many messages say so); blobRefs counts the messages
	// that carry a hash at all. Both make the checks of the blob server and of the
	// clean-up independent of the size of the mailbox.
	blobAuth map[string]map[identity.ID]int
	blobRefs map[string]int

	// the Internet (ext.go): the gateway of this device, the gateways of the others, the conversations by Message-ID
	gw       Gateway
	gateways map[identity.ID]gwEntry
	extIDs   map[string]string
}

// New loads the mailbox from the database.
func New(node *mesh.Node, db *store.DB, blobs *blob.Store, emit func(Event)) (*Manager, error) {
	m := &Manager{
		node: node, db: db, blobs: blobs, emit: emit,
		msgs: map[string]*record{}, inflight: map[string]bool{}, kick: make(chan struct{}, 1),
		blobAuth: map[string]map[identity.ID]int{}, blobRefs: map[string]int{},
		gateways: map[identity.ID]gwEntry{}, extIDs: map[string]string{},
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
		m.indexLocked(&r, +1)
		m.indexExtLocked(&r)
		return nil
	})
	if err != nil {
		return nil, err
	}
	// Attachments are served only to the people a message was written to (and its
	// author); knowing a hash is not enough.
	blobs.SetAuthorizer(m.MayFetchBlob)
	return m, nil
}

// MayFetchBlob reports whether we may hand the attachment with this hash to peer.
//
// Knowing a hash proves nothing, and neither does being the author of a message
// that names it: anybody can send us a message that lists any hash. What counts is
// who the owner of the blob decided to send it to:
//
//   - a message we wrote ourselves lets us give its attachments to its recipients;
//   - a message that was written to us lets us pass its attachments on to the other
//     recipients, but only those we pulled from its author - the author held the
//     blob and named the recipients, which is the author's decision to make. A blob
//     we already had, or got from somebody else, is not made available by a message
//     that merely mentions it.
func (m *Manager) MayFetchBlob(peer identity.ID, sha string) bool {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.blobAuth[sha][peer] > 0
}

// blobPeers lists the devices the message r lets us hand its attachment sha to.
func (m *Manager) blobPeers(r *record, sha string) []identity.ID {
	self := m.node.ID()
	switch {
	case r.Core.From == self:
	case r.Fetch[sha] != nil && r.Fetch[sha].Via:
	default:
		return nil
	}
	out := make([]identity.ID, 0, len(r.Core.To)+1)
	for _, id := range r.Core.To {
		if id != self {
			out = append(out, id)
		}
	}
	if r.Core.From != self {
		out = append(out, r.Core.From)
	}
	return out
}

// reindexLocked adds delta (+1 or -1) to what message r contributes for sha.
func (m *Manager) reindexLocked(r *record, sha string, delta int) {
	for _, id := range m.blobPeers(r, sha) {
		set := m.blobAuth[sha]
		if set == nil {
			if delta < 0 {
				continue
			}
			set = map[identity.ID]int{}
			m.blobAuth[sha] = set
		}
		set[id] += delta
		if set[id] <= 0 {
			delete(set, id)
		}
		if len(set) == 0 {
			delete(m.blobAuth, sha)
		}
	}
}

// indexLocked adds (+1) or removes (-1) everything message r contributes to the
// indexes. Call it when a message is stored or removed.
func (m *Manager) indexLocked(r *record, delta int) {
	seen := make(map[string]bool, len(r.Core.Attach))
	for _, a := range r.Core.Attach {
		if seen[a.SHA256] {
			continue
		}
		seen[a.SHA256] = true
		m.reindexLocked(r, a.SHA256, delta)
		m.blobRefs[a.SHA256] += delta
		if m.blobRefs[a.SHA256] <= 0 {
			delete(m.blobRefs, a.SHA256)
		}
	}
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
	var lastGW time.Time
	var gwBusy atomic.Bool
	refresh := func() {
		// (which devices are mail gateways, and what they offer this device: asked every minute and when a device appears)
		if gwBusy.CompareAndSwap(false, true) {
			lastGW = time.Now()
			go func() { defer gwBusy.Store(false); m.refreshGateways(ctx) }()
		}
	}
	m.pump(ctx)
	m.pumpExt(ctx)
	refresh()
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
			refresh()
		}
		m.pump(ctx)
		m.pumpExt(ctx)
		if time.Since(lastGW) > time.Minute {
			refresh()
		}
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
	// The Internet (ext.go): the mailbox of ours that the letter goes out from ("" - the only one this device has) and the
	// addresses it is written to. A letter to the Internet is sent through the gateway that has that mailbox.
	ExtFrom string
	ExtTo   []string
	ExtCc   []string
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
	if len(in.To) == 0 && len(in.ExtTo)+len(in.ExtCc) == 0 {
		return "", mesh.Errf(mesh.CodeInvalid, "choose at least one recipient")
	}
	if in.Kind == "chat" && len(in.ExtTo)+len(in.ExtCc) > 0 {
		return "", mesh.Errf(mesh.CodeInvalid, "a chat message goes to a device")
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
	var extOut *ExtOut
	if len(in.ExtTo)+len(in.ExtCc) > 0 {
		var err error
		if extOut, err = m.planExtOut(in, to); err != nil {
			return "", err
		}
		c.Ext = &Ext{Out: extOut}
		if extOut.Via != self && !containsID(to, extOut.Via) {
			to = append(to, extOut.Via) // the gateway is written to as well: it has to send the letter
			c.To = to
			extOut.ViaOnly = true
		}
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
	if extOut != nil {
		var files int64
		for _, a := range c.Attach {
			files += a.Size
		}
		if files > maxExtOutBytes {
			return "", mesh.Errf(mesh.CodeTooLarge, "a letter to the Internet carries at most %d MB of files", maxExtOutBytes>>20)
		}
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
	onlySelf := len(to) == 1 && to[0] == self && extOut == nil
	for _, id := range to {
		if id == self {
			continue
		}
		r.Delivery[id.String()] = &delivery{State: DelQueued}
	}
	if extOut != nil {
		r.ExtDel = map[string]*extDelivery{}
		for _, a := range append(append([]ExtAddr(nil), extOut.To...), extOut.Cc...) {
			r.ExtDel[strings.ToLower(a.Addr)] = &extDelivery{State: inetmail.RcptQueued}
		}
	}
	if onlySelf { // a note to self lands in the inbox, already read
		r.Folder = FolderInbox
		if in.Kind == "chat" {
			r.Folder = FolderChat
		}
	}
	m.msgs[c.ID] = r
	m.indexLocked(r, +1)
	m.indexExtLocked(r)
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
				if f := r.Fetch[a.SHA256]; m.wantsFetchLocked(f, a, now) && !m.inflight["f|"+a.SHA256] {
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
		m.mu.Lock()
		busy := m.fetching >= maxConcurrentFetch
		if !busy {
			m.fetching++
		}
		m.mu.Unlock()
		if busy {
			break // the next pump (a few seconds later) continues
		}
		go func(r *record) {
			defer func() { m.mu.Lock(); m.fetching--; m.mu.Unlock() }()
			m.fetchAttachments(ctx, r)
		}(r)
	}
}

// wantsFetchLocked reports whether an attachment should be fetched now.
func (m *Manager) wantsFetchLocked(f *fetchState, a Attachment, now int64) bool {
	if f == nil || f.State != AttRemote || now < f.Next {
		return false
	}
	return a.Size <= maxAutoFetch || f.Want
}

// FetchAttachment records the user's consent to download attachment idx of a
// message (needed for large ones) and retries one that failed.
func (m *Manager) FetchAttachment(id string, idx int) error {
	m.mu.Lock()
	r := m.msgs[id]
	if r == nil || idx < 0 || idx >= len(r.Core.Attach) {
		m.mu.Unlock()
		return mesh.Errf(mesh.CodeNotFound, "no such attachment")
	}
	a := r.Core.Attach[idx]
	f := r.Fetch[a.SHA256]
	if f == nil {
		m.mu.Unlock()
		return nil // our own attachment: nothing to fetch
	}
	if f.State == AttFailed || f.State == AttRemote {
		f.State, f.Want, f.Fails, f.Next = AttRemote, true, 0, 0
		m.save(r)
	}
	m.mu.Unlock()
	m.Kick()
	return nil
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
		if e := r.Core.Ext; e != nil && e.Out != nil && e.Out.Via == p.ID {
			// the gateway refused the letter: none of the addresses on the Internet will get it
			for _, a := range append(append([]ExtAddr(nil), e.Out.To...), e.Out.Cc...) {
				m.setExtDelLocked(r, a.Addr, inetmail.RcptFailed, 0, describe(err), time.Now().Unix())
			}
		}
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
	m.registerExt()
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
		if err := m.checkExt(c.Peer, &cr); err != nil {
			return nil, err
		}
		var filesSize int64
		for i := range cr.Attach {
			a := &cr.Attach[i]
			if !blob.ValidSHA(a.SHA256) || a.Size < 0 {
				return nil, mesh.Errf(mesh.CodeInvalid, "bad attachment")
			}
			filesSize += a.Size
			// The signed bytes (Raw) stay as they arrived; what is shown is cleaned.
			a.Name, a.Mime, a.ContentID = cleanText(a.Name, 255), cleanText(a.Mime, 100), cleanText(a.ContentID, 200)
		}
		if o := cr.Ext; o != nil && o.Out != nil && o.Out.Via == self && filesSize > maxExtOutBytes {
			return nil, mesh.Errf(mesh.CodeTooLarge, "a letter to the Internet carries at most %d MB of files", maxExtOutBytes>>20)
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
		if o := cr.Ext; o != nil && o.Out != nil && o.Out.Via == self {
			if o.Out.ViaOnly {
				r.Folder, r.Unread = FolderRelay, false // this device only passes the letter on: it is not for its owner to read
			}
			r.ExtDel = map[string]*extDelivery{}
		}
		for _, a := range cr.Attach {
			st := AttRemote
			if _, ok := m.blobs.Has(a.SHA256); ok {
				st = AttReady
			}
			r.Fetch[a.SHA256] = &fetchState{State: st}
		}
		m.msgs[cr.ID] = r
		m.indexLocked(r, +1)
		m.indexExtLocked(r)
		m.save(r)
		ev := Event{Kind: cr.Kind, ID: cr.ID, Folder: r.Folder, Unread: r.Unread, Peer: chatPeer(r, self)}
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
		if !m.wantsFetchLocked(f, a, time.Now().Unix()) || m.inflight["f|"+a.SHA256] {
			m.mu.Unlock()
			continue
		}
		m.inflight["f|"+a.SHA256] = true
		f.State = AttFetching
		m.mu.Unlock()

		ok, fromAuthor := m.fetchOne(ctx, r, a)

		m.mu.Lock()
		delete(m.inflight, "f|"+a.SHA256)
		if ok {
			live := m.msgs[r.Core.ID] == r // (the message may have been deleted meanwhile)
			if live {
				m.reindexLocked(r, a.SHA256, -1)
			}
			f.State, f.Got, f.Via = AttReady, a.Size, fromAuthor
			if live {
				m.reindexLocked(r, a.SHA256, +1)
			}
		} else if f.State == AttFetching {
			f.Fails++
			f.Got = 0
			if f.Fails >= maxFetchTries {
				f.State = AttFailed
			} else {
				f.State = AttRemote // try again later, when someone who has it is online
				f.Next = time.Now().Add(fetchBackoff(f.Fails)).Unix()
			}
		}
		m.save(r)
		ev := Event{Kind: r.Core.Kind, ID: r.Core.ID, Folder: r.Folder, Peer: chatPeer(r, m.node.ID())}
		m.mu.Unlock()
		m.fire(ev)
	}
}

// fetchOne pulls an attachment; fromAuthor tells whether the message's author was the one that served it.
func (m *Manager) fetchOne(ctx context.Context, r *record, a Attachment) (ok, fromAuthor bool) {
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
			return true, p.ID == r.Core.From
		}
	}
	return false, false
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
	// Ext: the letter came from the Internet or goes there (ext.go).
	Ext *ExtView `json:"ext,omitempty"`
}

// AttachmentView is an attachment with its local availability.
type AttachmentView struct {
	Attachment
	State string `json:"state"`
	Got   int64  `json:"got"`
	// NeedsConsent: too large to fetch on its own; POST .../fetch to download it.
	NeedsConsent bool `json:"needsConsent,omitempty"`
}

// Message is the full view.
type Message struct {
	Summary
	Body string `json:"body"`
	// HTML is the cleaned HTML of a letter from the Internet. The interface does not get it here but as a page of its own
	// (GET /api/mail/{id}/html), which a frame that may load nothing shows.
	HTML      string  `json:"-"`
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
	fromID, fromName := m.displaySender(r)
	s := Summary{
		ID: c.ID, Kind: c.Kind, Folder: r.Folder, From: Person{ID: fromID, Name: fromName},
		Subject: c.Subject, Snippet: snippet(c.Body), TS: c.Created, Unread: r.Unread,
		Attachments: len(c.Attach), Thread: c.Thread, Starred: r.Starred, Ext: m.extViewLocked(r),
	}
	for _, id := range c.To {
		if o := c.Ext; o != nil && o.Out != nil && o.Out.ViaOnly && id == o.Out.Via {
			continue // the gateway only passes the letter on: it is not one of the people it is written to
		}
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
			_, who := m.displaySender(r)
			if e := r.Core.Ext; e != nil && e.In != nil {
				who += " " + e.In.From.Addr
			}
			hay := strings.ToLower(r.Core.Subject + "\n" + r.Core.Body + "\n" + who)
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
	if e := r.Core.Ext; e != nil && e.In != nil {
		msg.HTML = e.In.HTML
	}
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
				v.NeedsConsent = f.State == AttRemote && a.Size > maxAutoFetch && !f.Want
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
		m.indexLocked(r, -1)
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
		if m.blobRefs[a.SHA256] <= 0 {
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
