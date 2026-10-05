package mail

import (
	"context"
	"crypto/ed25519"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"io"
	"sort"
	"strings"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/inetmail"
	"github.com/parfentsevandrey-blip/test/svoi/internal/mesh"
)

// Mail with the Internet.
//
// One device of the mesh - the gateway - speaks SMTP to the world (internal/mailgw). A letter that comes in from the
// Internet is turned into an ordinary mesh letter by the gateway: the gateway signs it, names the devices of the mailbox it was
// written to as its recipients, and puts what the Internet said about it in Ext.In. A letter that a device writes to an address
// on the Internet is an ordinary mesh letter addressed to the gateway (and to the other devices named in it), with the addresses
// in Ext.Out; the gateway builds the real letter out of it, signs it with the key of the domain and sends it, and tells the
// device what became of each recipient (mail.report).
//
// A device believes Ext.In only from a gateway it trusts: an administrator of the mesh, or a device of its own owner (the
// owner of a device is set by whoever invited it, never by the device).

// FolderRelay holds the letters that this device only passes on (the gateway's copies): they are not shown.
const FolderRelay = "relay"

// ExtAddr is a mailbox on the Internet.
type ExtAddr struct {
	Name string `json:"name,omitempty"`
	Addr string `json:"addr"`
}

func (a ExtAddr) display() string {
	if strings.TrimSpace(a.Name) != "" {
		return a.Name
	}
	return a.Addr
}

// ExtIn is what the gateway says about a letter from the Internet.
type ExtIn struct {
	MessageID  string    `json:"messageId,omitempty"`
	References []string  `json:"references,omitempty"`
	From       ExtAddr   `json:"from"`
	To         []ExtAddr `json:"to,omitempty"`
	Cc         []ExtAddr `json:"cc,omitempty"`
	ReplyTo    []ExtAddr `json:"replyTo,omitempty"`
	Mailbox    string    `json:"mailbox"`        // our address that it was written to
	HTML       string    `json:"html,omitempty"` // the HTML of the letter, cleaned by the gateway
	// RemoteImages counts the pictures of the HTML that live on the Internet (the interface does not load them unless asked).
	RemoteImages int `json:"remoteImages,omitempty"`
	// Verdict: what the checks of the gateway say about the sender: "verified", "unverified" or "suspicious" (inetmail.Verdict*).
	Verdict   string `json:"verdict"`
	SPF       string `json:"spf,omitempty"`
	DKIM      string `json:"dkim,omitempty"`
	DMARC     string `json:"dmarc,omitempty"`
	Internal  bool   `json:"internal,omitempty"` // written by another mailbox of the same gateway: nothing to check
	Truncated bool   `json:"truncated,omitempty"`
}

// ExtOut says that a letter goes to the Internet too.
type ExtOut struct {
	Via        identity.ID `json:"via"`               // the gateway
	ViaOnly    bool        `json:"viaOnly,omitempty"` // the gateway is only passing it on (it is not one of the people it is written to)
	From       ExtAddr     `json:"from"`              // the mailbox it goes out from
	To         []ExtAddr   `json:"to"`
	Cc         []ExtAddr   `json:"cc,omitempty"`
	MessageID  string      `json:"messageId"`
	InReplyTo  string      `json:"inReplyTo,omitempty"`
	References []string    `json:"references,omitempty"`
}

// Ext is the part of the signed core of a letter that has to do with the Internet.
type Ext struct {
	In  *ExtIn  `json:"in,omitempty"`
	Out *ExtOut `json:"out,omitempty"`
}

// extDelivery is how a letter fared on its way to an address on the Internet.
type extDelivery struct {
	State string `json:"state"` // inetmail.RcptQueued, RcptDeferred, RcptDelivered or RcptFailed
	Code  int    `json:"code,omitempty"`
	Text  string `json:"text,omitempty"`
	At    int64  `json:"at,omitempty"`
	Told  string `json:"told,omitempty"` // the gateway's copy: the state the author has been told
}

func (e *extDelivery) final() bool {
	return e.State == inetmail.RcptDelivered || e.State == inetmail.RcptFailed
}

// ---- what the manager asks of the gateway ----

// GatewayInfo is what a gateway offers a device.
type GatewayInfo struct {
	Domain    string   `json:"domain"`
	Host      string   `json:"host"`
	Mailboxes []string `json:"mailboxes"` // the addresses the device may write from and receives at
	Ready     bool     `json:"ready"`     // the domain is set up and the gateway is listening
}

// OutAttach is a file of a letter that goes out.
type OutAttach struct {
	Name   string
	Mime   string
	Size   int64
	SHA256 string
	Open   func() (io.ReadCloser, error)
}

// OutLetter is a letter for the gateway to send.
type OutLetter struct {
	ID         string // the id of the mesh letter: sending it twice is sending it once
	Sender     identity.ID
	From       ExtAddr
	To, Cc     []ExtAddr
	Subject    string
	Text       string
	MessageID  string
	InReplyTo  string
	References []string
	Created    int64
	Attach     []OutAttach
}

// Gateway is what the manager asks of the part of this device that speaks SMTP.
type Gateway interface {
	// Info says what the gateway offers the device peer (ok: it offers anything at all).
	Info(peer identity.ID) (GatewayInfo, bool)
	// CanSend says whether the device peer may send letters from the mailbox from.
	CanSend(peer identity.ID, from string) error
	// SendOut takes a letter that is to go to the Internet. The states of its recipients come back through ExtReport.
	SendOut(l OutLetter) error
}

// SetGateway makes this device a gateway (nil: it is not one).
func (m *Manager) SetGateway(g Gateway) {
	m.mu.Lock()
	m.gw = g
	m.mu.Unlock()
	m.Kick()
}

// ---- who is a gateway ----

// trustedGateway says whether letters from the Internet may be believed from this peer.
func (m *Manager) trustedGateway(p *mesh.Peer) bool {
	mem := p.Member()
	if mem == nil {
		return false
	}
	if mem.Admin {
		return true
	}
	self := m.node.Self()
	return mem.Owner != "" && mem.Owner == self.Owner
}

// GatewayView is a gateway as the interface shows it.
type GatewayView struct {
	ID        identity.ID `json:"id"`
	Name      string      `json:"name"`
	Self      bool        `json:"self"`
	Online    bool        `json:"online"`
	Domain    string      `json:"domain"`
	Host      string      `json:"host"`
	Mailboxes []string    `json:"mailboxes"`
	Ready     bool        `json:"ready"`
}

type gwEntry struct {
	info GatewayInfo
	seen time.Time
}

// Gateways lists the gateways this device knows (itself included) with the mailboxes it may use at each.
func (m *Manager) Gateways() []GatewayView {
	self := m.node.ID()
	m.mu.Lock()
	gw := m.gw
	cached := make(map[identity.ID]gwEntry, len(m.gateways))
	for id, e := range m.gateways {
		cached[id] = e
	}
	m.mu.Unlock()
	var out []GatewayView
	if gw != nil {
		if info, ok := gw.Info(self); ok {
			out = append(out, GatewayView{ID: self, Name: m.name(self), Self: true, Online: true, Domain: info.Domain, Host: info.Host, Mailboxes: info.Mailboxes, Ready: info.Ready})
		}
	}
	for id, e := range cached {
		p := m.node.Peer(id)
		if p == nil || !m.trustedGateway(p) {
			continue
		}
		out = append(out, GatewayView{ID: id, Name: p.Name(), Online: p.Online(), Domain: e.info.Domain, Host: e.info.Host, Mailboxes: e.info.Mailboxes, Ready: e.info.Ready})
	}
	sort.Slice(out, func(i, j int) bool { return out[i].ID.String() < out[j].ID.String() })
	return out
}

// pickGateway chooses the gateway that has the mailbox from for this device.
func (m *Manager) pickGateway(from string) (GatewayView, bool) {
	for _, g := range m.Gateways() {
		for _, mb := range g.Mailboxes {
			if strings.EqualFold(mb, from) {
				return g, true
			}
		}
	}
	return GatewayView{}, false
}

// refreshGateways asks the peers that could be gateways what they offer.
func (m *Manager) refreshGateways(ctx context.Context) {
	for _, p := range m.node.Peers() {
		if !p.Online() || !m.trustedGateway(p) {
			continue
		}
		cctx, cancel := context.WithTimeout(ctx, 10*time.Second)
		var info GatewayInfo
		err := p.Call(cctx, "mailgw.info", nil, &info)
		cancel()
		m.mu.Lock()
		if err == nil && len(info.Mailboxes) > 0 {
			m.gateways[p.ID] = gwEntry{info: info, seen: time.Now()}
		} else if err == nil || mesh.IsCode(err, mesh.CodeNotFound) || mesh.IsCode(err, mesh.CodeDenied) {
			delete(m.gateways, p.ID)
		}
		m.mu.Unlock()
	}
}

func (m *Manager) registerExt() {
	m.node.Handle("mailgw.info", func(ctx context.Context, c *mesh.Call) (any, error) {
		m.mu.Lock()
		gw := m.gw
		m.mu.Unlock()
		if gw == nil {
			return nil, mesh.Errf(mesh.CodeNotFound, "this device is not a mail gateway")
		}
		info, ok := gw.Info(c.Peer.ID)
		if !ok {
			return nil, mesh.Errf(mesh.CodeNotFound, "this device is not a mail gateway")
		}
		return info, nil
	})
	m.node.Handle("mail.report", func(ctx context.Context, c *mesh.Call) (any, error) {
		var rep extReport
		if err := c.Decode(&rep); err != nil {
			return nil, err
		}
		if err := m.takeReport(c.Peer.ID, rep); err != nil {
			return nil, err
		}
		return map[string]bool{"ok": true}, nil
	})
}

// ---- checking what arrives ----

const (
	maxExtList     = 100
	maxExtHTML     = 1536 << 10
	maxExtOutBytes = 20 << 20 // what a letter to the Internet carries along as files
)

func cleanExtAddr(a *ExtAddr) bool {
	a.Name = cleanText(a.Name, 200)
	a.Addr = strings.TrimSpace(cleanText(a.Addr, 254))
	return a.Addr != "" && strings.Count(a.Addr, "@") >= 1 && !strings.ContainsAny(a.Addr, " \t\r\n<>,;")
}

func cleanExtList(l []ExtAddr) ([]ExtAddr, bool) {
	if len(l) > maxExtList {
		return nil, false
	}
	out := make([]ExtAddr, 0, len(l))
	for _, a := range l {
		if !cleanExtAddr(&a) {
			return nil, false
		}
		out = append(out, a)
	}
	return out, true
}

// checkExt looks at the Ext part of a letter that a peer delivers and cleans what it says. It runs before the letter is stored.
func (m *Manager) checkExt(peer *mesh.Peer, cr *core) error {
	e := cr.Ext
	if e == nil {
		return nil
	}
	bad := mesh.Errf(mesh.CodeInvalid, "bad message")
	if cr.Kind != "mail" || (e.In != nil) == (e.Out != nil) {
		return bad
	}
	if in := e.In; in != nil {
		if !m.trustedGateway(peer) {
			return mesh.Errf(mesh.CodeDenied, "this device is not allowed to bring letters from the Internet")
		}
		var ok1, ok2, ok3, ok4 bool
		in.To, ok1 = cleanExtList(in.To)
		in.Cc, ok2 = cleanExtList(in.Cc)
		in.ReplyTo, ok3 = cleanExtList(in.ReplyTo)
		ok4 = cleanExtAddr(&in.From)
		if !ok1 || !ok2 || !ok3 || !ok4 || len(in.HTML) > maxExtHTML || len(in.References) > 60 {
			return bad
		}
		in.MessageID = cleanText(in.MessageID, 300)
		in.Mailbox = cleanText(in.Mailbox, 254)
		for i := range in.References {
			in.References[i] = cleanText(in.References[i], 300)
		}
		switch in.Verdict {
		case inetmail.VerdictVerified, inetmail.VerdictUnverified, inetmail.VerdictSuspicious:
		default:
			in.Verdict = inetmail.VerdictUnverified
		}
		in.SPF, in.DKIM, in.DMARC = cleanText(in.SPF, 40), cleanText(in.DKIM, 200), cleanText(in.DMARC, 40)
	}
	if out := e.Out; out != nil {
		var ok1, ok2 bool
		out.To, ok1 = cleanExtList(out.To)
		out.Cc, ok2 = cleanExtList(out.Cc)
		if !ok1 || !ok2 || !cleanExtAddr(&out.From) || len(out.To)+len(out.Cc) == 0 || len(out.To)+len(out.Cc) > maxExtList || len(out.References) > 60 {
			return bad
		}
		out.MessageID, out.InReplyTo = cleanText(out.MessageID, 300), cleanText(out.InReplyTo, 300)
		for i := range out.References {
			out.References[i] = cleanText(out.References[i], 300)
		}
		if out.Via == m.node.ID() {
			// this device is the gateway of the letter: it must be one, and the device that wrote it must be allowed to
			m.mu.Lock()
			gw := m.gw
			m.mu.Unlock()
			if gw == nil {
				return mesh.Errf(mesh.CodeInvalid, "this device is not a mail gateway")
			}
			if err := gw.CanSend(peer.ID, out.From.Addr); err != nil {
				return mesh.Errf(mesh.CodeDenied, "%v", err)
			}
		}
	}
	return nil
}

// ---- the letters from the Internet ----

// ExtLetter is a letter from the Internet that the gateway hands to the manager.
type ExtLetter struct {
	// MessageID makes the id of the letter: the same letter (a sender that tries again) is stored once.
	MessageID string
	// Mailbox is the address of ours that it was written to, Devices the devices that have that mailbox.
	Mailbox string
	Devices []identity.ID
	In      ExtIn
	Subject string
	Text    string
	Attach  []Attachment // already in the blob store
	Created int64
}

// ReceiveExternal stores a letter from the Internet, signs it as this device (the gateway) and sends it to the devices of the mailbox.
func (m *Manager) ReceiveExternal(l ExtLetter) (string, error) {
	self := m.node.ID()
	if len(l.Devices) == 0 {
		return "", mesh.Errf(mesh.CodeInvalid, "the mailbox has no devices")
	}
	h := sha256.Sum256([]byte(l.MessageID + "\x00" + strings.ToLower(l.Mailbox)))
	id := "m_" + self.Short() + "_" + hex.EncodeToString(h[:9])
	if l.MessageID == "" {
		id = newID("m_", self)
	}
	in := l.In
	in.Mailbox = l.Mailbox
	if len(in.HTML) > maxExtHTML {
		in.HTML, in.RemoteImages, in.Truncated = "", 0, true
	}
	text := l.Text
	if len(text) > maxBody {
		text, in.Truncated = strings.ToValidUTF8(text[:maxBody], ""), true
	}
	seen := map[identity.ID]bool{}
	var to []identity.ID
	for _, d := range l.Devices {
		if seen[d] {
			continue
		}
		seen[d] = true
		if d != self && m.node.Peer(d) == nil {
			continue // a device that has left the mesh
		}
		to = append(to, d)
	}
	if len(to) == 0 {
		return "", mesh.Errf(mesh.CodeInvalid, "the mailbox has no devices left")
	}
	created := l.Created
	if created <= 0 || created > time.Now().Unix()+3600 {
		created = time.Now().Unix()
	}
	c := core{
		ID: id, Kind: "mail", From: self, To: to, Subject: cleanText(strings.TrimSpace(l.Subject), 1000), Body: text,
		Attach: l.Attach, Created: created, Ext: &Ext{In: &in},
	}
	m.mu.Lock()
	if _, dup := m.msgs[id]; dup {
		m.mu.Unlock()
		return id, nil
	}
	c.Thread = id
	if t := m.threadOfLocked(in.References, in.MessageID); t != "" {
		c.Thread = t
	}
	raw, err := json.Marshal(c)
	if err != nil {
		m.mu.Unlock()
		return "", err
	}
	if len(raw) > maxRawSize {
		m.mu.Unlock()
		return "", mesh.Errf(mesh.CodeTooLarge, "the letter is too large")
	}
	sig := ed25519.Sign(m.node.Device().Priv, raw)
	r := &record{Core: c, Raw: raw, Sig: sig, Delivery: map[string]*delivery{}, Received: time.Now().Unix(), Folder: FolderRelay}
	for _, d := range to {
		if d == self {
			r.Folder, r.Unread = FolderInbox, true
			continue
		}
		r.Delivery[d.String()] = &delivery{State: DelQueued}
	}
	m.msgs[id] = r
	m.indexLocked(r, +1)
	m.indexExtLocked(r)
	m.save(r)
	ev := Event{Kind: "mail", ID: id, Folder: r.Folder, Unread: r.Unread}
	m.mu.Unlock()
	m.fire(ev)
	m.fireCounters()
	m.Kick()
	return id, nil
}

// indexExtLocked remembers the Message-ID of a letter, so that an answer to it finds its conversation.
func (m *Manager) indexExtLocked(r *record) {
	if e := r.Core.Ext; e != nil {
		if e.In != nil && e.In.MessageID != "" {
			m.extIDs[e.In.MessageID] = r.Core.ID
		}
		if e.Out != nil && e.Out.MessageID != "" {
			m.extIDs[e.Out.MessageID] = r.Core.ID
		}
	}
}

// threadOfLocked finds the conversation that a letter belongs to by the Message-IDs it answers.
func (m *Manager) threadOfLocked(refs []string, own string) string {
	for i := len(refs) - 1; i >= 0; i-- {
		if id, ok := m.extIDs[refs[i]]; ok {
			if r := m.msgs[id]; r != nil && r.Core.Thread != "" {
				return r.Core.Thread
			}
		}
	}
	return ""
}

// ---- the gateway sends what the devices wrote ----

// extReport is what the gateway tells the author of a letter about one recipient.
type extReport struct {
	ID    string `json:"id"`
	Rcpt  string `json:"rcpt"`
	State string `json:"state"`
	Code  int    `json:"code,omitempty"`
	Text  string `json:"text,omitempty"`
	At    int64  `json:"at"`
}

// ExtReport is how the gateway tells the manager what became of a recipient on the Internet. The author of the letter is told too
// (from the loop of the manager, until it has taken the report).
func (m *Manager) ExtReport(id, rcpt, state string, code int, text string, at int64) {
	m.mu.Lock()
	r := m.msgs[id]
	if r == nil || r.Core.Ext == nil || r.Core.Ext.Out == nil {
		m.mu.Unlock()
		return
	}
	m.setExtDelLocked(r, rcpt, state, code, text, at)
	m.save(r)
	ev := Event{Kind: "mail", ID: id, Folder: r.Folder}
	m.mu.Unlock()
	m.fire(ev)
	m.Kick()
}

func (m *Manager) setExtDelLocked(r *record, rcpt, state string, code int, text string, at int64) {
	if r.ExtDel == nil {
		r.ExtDel = map[string]*extDelivery{}
	}
	key := strings.ToLower(rcpt)
	d := r.ExtDel[key]
	if d == nil {
		d = &extDelivery{}
		r.ExtDel[key] = d
	}
	if d.final() && d.State == state {
		return
	}
	if d.final() && state != inetmail.RcptDelivered && state != inetmail.RcptFailed {
		return // a final state is not taken back
	}
	d.State, d.Code, d.Text, d.At = state, code, cleanText(text, 300), at
}

// takeReport is the author's side of mail.report.
func (m *Manager) takeReport(from identity.ID, rep extReport) error {
	m.mu.Lock()
	r := m.msgs[rep.ID]
	if r == nil || r.Core.From != m.node.ID() || r.Core.Ext == nil || r.Core.Ext.Out == nil || r.Core.Ext.Out.Via != from {
		m.mu.Unlock()
		return mesh.Errf(mesh.CodeNotFound, "no such letter")
	}
	switch rep.State {
	case inetmail.RcptDeferred, inetmail.RcptDelivered, inetmail.RcptFailed:
	default:
		m.mu.Unlock()
		return mesh.Errf(mesh.CodeInvalid, "bad report")
	}
	known := false
	for _, a := range append(append([]ExtAddr(nil), r.Core.Ext.Out.To...), r.Core.Ext.Out.Cc...) {
		if strings.EqualFold(a.Addr, rep.Rcpt) {
			known = true
		}
	}
	if !known {
		m.mu.Unlock()
		return mesh.Errf(mesh.CodeInvalid, "no such recipient")
	}
	at := rep.At
	if at <= 0 {
		at = time.Now().Unix()
	}
	m.setExtDelLocked(r, rep.Rcpt, rep.State, rep.Code, rep.Text, at)
	m.save(r)
	ev := Event{Kind: "mail", ID: r.Core.ID, Folder: r.Folder}
	m.mu.Unlock()
	m.fire(ev)
	return nil
}

// pumpExt does the work of the gateway and of the authors that depends on the Internet part of the letters: it hands the
// letters that are ready to the gateway, tells the authors what became of their recipients, forgets old copies it only passed on.
func (m *Manager) pumpExt(ctx context.Context) {
	self := m.node.ID()
	now := time.Now().Unix()
	type outJob struct{ r *record }
	var jobs []outJob
	type repJob struct {
		r   *record
		rep extReport
		key string
	}
	var reps []repJob
	var old []*record
	m.mu.Lock()
	gw := m.gw
	for _, r := range m.msgs {
		e := r.Core.Ext
		if e == nil {
			continue
		}
		if e.Out != nil && e.Out.Via == self && gw != nil && !r.OutTaken {
			ready := true
			for _, a := range r.Core.Attach {
				if _, ok := m.blobs.Has(a.SHA256); !ok {
					ready = false
				}
			}
			if ready {
				jobs = append(jobs, outJob{r})
			}
		}
		if e.Out != nil && e.Out.Via == self && r.Core.From != self {
			// the gateway's copy: the author wants to hear
			for key, d := range r.ExtDel {
				if d.Told != d.State && d.State != "" && d.State != inetmail.RcptQueued {
					reps = append(reps, repJob{r, extReport{ID: r.Core.ID, Rcpt: key, State: d.State, Code: d.Code, Text: d.Text, At: d.At}, key})
				}
			}
		}
		if r.Folder == FolderRelay && r.Received > 0 && now-r.Received > int64(relayKeep/time.Second) && m.allDeliveredLocked(r) {
			old = append(old, r)
		}
	}
	m.mu.Unlock()

	for _, j := range jobs {
		m.sendOut(j.r, gw)
	}
	for _, j := range reps {
		p := m.node.Peer(j.r.Core.From)
		if p == nil || !p.Online() {
			continue
		}
		cctx, cancel := context.WithTimeout(ctx, 20*time.Second)
		err := p.Call(cctx, "mail.report", j.rep, nil)
		cancel()
		m.mu.Lock()
		if d := j.r.ExtDel[j.key]; d != nil && (err == nil || mesh.IsCode(err, mesh.CodeNotFound)) && d.State == j.rep.State {
			d.Told = j.rep.State
			m.save(j.r)
		}
		m.mu.Unlock()
	}
	for _, r := range old {
		_ = m.removeRelay(r)
	}
}

// relayKeep is how long the gateway keeps a letter it has only passed on (an attachment may still be on its way to a device).
const relayKeep = 30 * 24 * time.Hour

func (m *Manager) allDeliveredLocked(r *record) bool {
	for _, d := range r.Delivery {
		if d.State == DelQueued {
			return false
		}
	}
	if e := r.Core.Ext; e != nil && e.Out != nil {
		for _, d := range r.ExtDel {
			if !d.final() || d.Told != d.State {
				return false
			}
		}
	}
	return true
}

func (m *Manager) removeRelay(r *record) error {
	m.mu.Lock()
	if m.msgs[r.Core.ID] != r {
		m.mu.Unlock()
		return nil
	}
	delete(m.msgs, r.Core.ID)
	m.indexLocked(r, -1)
	_ = m.db.Delete(bucketMsgs, r.Core.ID)
	m.gcBlobsLocked(r)
	m.mu.Unlock()
	return nil
}

// sendOut hands a letter that is ready to the gateway of this device.
func (m *Manager) sendOut(r *record, gw Gateway) {
	e := r.Core.Ext.Out
	l := OutLetter{
		ID: r.Core.ID, Sender: r.Core.From, From: e.From, To: e.To, Cc: e.Cc, Subject: r.Core.Subject, Text: r.Core.Body,
		MessageID: e.MessageID, InReplyTo: e.InReplyTo, References: e.References, Created: r.Core.Created,
	}
	for _, a := range r.Core.Attach {
		sha := a.SHA256
		l.Attach = append(l.Attach, OutAttach{Name: a.Name, Mime: a.Mime, Size: a.Size, SHA256: sha, Open: func() (io.ReadCloser, error) { return m.blobs.Open(sha) }})
	}
	err := gw.SendOut(l)
	m.mu.Lock()
	defer m.mu.Unlock()
	if err != nil {
		if errors.Is(err, ErrNotYet) {
			return // the gateway is not ready: tried again on the next round
		}
		// a refusal (a mailbox that is not the sender's, a limit): every recipient fails with the reason
		now := time.Now().Unix()
		for _, a := range append(append([]ExtAddr(nil), e.To...), e.Cc...) {
			m.setExtDelLocked(r, a.Addr, inetmail.RcptFailed, 0, err.Error(), now)
		}
	}
	r.OutTaken = true
	m.save(r)
}

// ErrNotYet is what a gateway answers when it cannot take the letter just now.
var ErrNotYet = errors.New("the gateway is not ready")

// ---- reading ----

// ExtRecipient is a recipient on the Internet with what became of the letter.
type ExtRecipient struct {
	Addr  string `json:"addr"`
	Name  string `json:"name,omitempty"`
	Kind  string `json:"kind"` // "to" or "cc"
	State string `json:"state"`
	Code  int    `json:"code,omitempty"`
	Text  string `json:"text,omitempty"`
	At    *int64 `json:"at"`
}

// ExtView is the Internet side of a letter, for the interface.
type ExtView struct {
	// Dir is "in" (a letter from the Internet) or "out" (a letter that goes to the Internet).
	Dir     string         `json:"dir"`
	From    ExtAddr        `json:"from"`
	To      []ExtAddr      `json:"to,omitempty"`
	Cc      []ExtAddr      `json:"cc,omitempty"`
	ReplyTo []ExtAddr      `json:"replyTo,omitempty"`
	Mailbox string         `json:"mailbox,omitempty"`
	Verdict string         `json:"verdict,omitempty"`
	SPF     string         `json:"spf,omitempty"`
	DKIM    string         `json:"dkim,omitempty"`
	DMARC   string         `json:"dmarc,omitempty"`
	Via     *Person        `json:"via,omitempty"` // the gateway
	HasHTML bool           `json:"hasHtml,omitempty"`
	Images  int            `json:"remoteImages,omitempty"`
	Cut     bool           `json:"truncated,omitempty"`
	MsgID   string         `json:"messageId,omitempty"`
	Out     []ExtRecipient `json:"recipients,omitempty"`
}

func (m *Manager) extViewLocked(r *record) *ExtView {
	e := r.Core.Ext
	if e == nil {
		return nil
	}
	if in := e.In; in != nil {
		return &ExtView{
			Dir: "in", From: in.From, To: in.To, Cc: in.Cc, ReplyTo: in.ReplyTo, Mailbox: in.Mailbox, Verdict: in.Verdict, SPF: in.SPF, DKIM: in.DKIM, DMARC: in.DMARC,
			Via: &Person{ID: r.Core.From, Name: m.name(r.Core.From)}, HasHTML: in.HTML != "", Images: in.RemoteImages, Cut: in.Truncated, MsgID: in.MessageID,
		}
	}
	o := e.Out
	v := &ExtView{Dir: "out", From: o.From, To: o.To, Cc: o.Cc, Mailbox: o.From.Addr, Via: &Person{ID: o.Via, Name: m.name(o.Via)}, MsgID: o.MessageID}
	add := func(l []ExtAddr, kind string) {
		for _, a := range l {
			rc := ExtRecipient{Addr: a.Addr, Name: a.Name, Kind: kind, State: inetmail.RcptQueued}
			if d := r.ExtDel[strings.ToLower(a.Addr)]; d != nil {
				rc.State, rc.Code, rc.Text = d.State, d.Code, d.Text
				if d.At != 0 {
					at := d.At
					rc.At = &at
				}
			}
			v.Out = append(v.Out, rc)
		}
	}
	add(o.To, "to")
	add(o.Cc, "cc")
	return v
}

// HTMLOf returns the cleaned HTML of a letter from the Internet.
func (m *Manager) HTMLOf(id string) (html string, ok bool) {
	m.mu.Lock()
	defer m.mu.Unlock()
	r := m.msgs[id]
	if r == nil || r.Core.Ext == nil || r.Core.Ext.In == nil {
		return "", false
	}
	return r.Core.Ext.In.HTML, true
}

// displaySender is what a list of letters shows as the sender.
func (m *Manager) displaySender(r *record) (identity.ID, string) {
	if e := r.Core.Ext; e != nil && e.In != nil {
		return r.Core.From, e.In.From.display()
	}
	return r.Core.From, m.name(r.Core.From)
}

// ---- writing to the Internet ----

func containsID(l []identity.ID, id identity.ID) bool {
	for _, x := range l {
		if x == id {
			return true
		}
	}
	return false
}

// parseExtAddrs reads addresses as people write them ("a@b.c", "Name <a@b.c>", several in one string).
func parseExtAddrs(l []string) ([]ExtAddr, error) {
	var out []ExtAddr
	for _, s := range l {
		as, err := inetmail.ParseAddressList(s)
		if err != nil {
			return nil, mesh.Errf(mesh.CodeInvalid, "%v", err)
		}
		for _, a := range as {
			out = append(out, ExtAddr{Name: a.Name, Addr: a.Addr()})
		}
	}
	return out, nil
}

// planExtOut works out where a letter to the Internet goes through and what it says on the way.
func (m *Manager) planExtOut(in SendInput, to []identity.ID) (*ExtOut, error) {
	from := strings.TrimSpace(in.ExtFrom)
	var gw GatewayView
	found := false
	if from == "" {
		type choice struct {
			gw GatewayView
			mb string
		}
		var all []choice
		for _, g := range m.Gateways() {
			for _, mb := range g.Mailboxes {
				all = append(all, choice{g, mb})
			}
		}
		switch len(all) {
		case 0:
		case 1:
			gw, from, found = all[0].gw, all[0].mb, true
		default:
			return nil, mesh.Errf(mesh.CodeInvalid, "choose the mailbox the letter goes out from")
		}
	} else {
		gw, found = m.pickGateway(from)
		for _, mb := range gw.Mailboxes {
			if strings.EqualFold(mb, from) {
				from = mb
			}
		}
	}
	if !found {
		return nil, mesh.Errf(mesh.CodeNotFound, "this device has no mailbox on the Internet: set up an address of its own first")
	}
	toList, err := parseExtAddrs(in.ExtTo)
	if err != nil {
		return nil, err
	}
	ccList, err := parseExtAddrs(in.ExtCc)
	if err != nil {
		return nil, err
	}
	seen := map[string]bool{}
	dedupe := func(l []ExtAddr) []ExtAddr {
		var out []ExtAddr
		for _, a := range l {
			if k := strings.ToLower(a.Addr); !seen[k] {
				seen[k] = true
				out = append(out, a)
			}
		}
		return out
	}
	toList, ccList = dedupe(toList), dedupe(ccList)
	if len(toList)+len(ccList) == 0 || len(toList)+len(ccList) > maxExtList {
		return nil, mesh.Errf(mesh.CodeInvalid, "a letter goes to 1 to %d addresses on the Internet", maxExtList)
	}
	dom := from[strings.LastIndexByte(from, '@')+1:]
	out := &ExtOut{
		Via: gw.ID, From: ExtAddr{Name: m.node.Self().Owner, Addr: from}, To: toList, Cc: ccList, MessageID: inetmail.NewMessageID(dom),
	}
	// an answer carries the headers that tie it to the letter it answers
	if in.InReplyTo != "" {
		m.mu.Lock()
		if prev := m.msgs[in.InReplyTo]; prev != nil && prev.Core.Ext != nil {
			var mid string
			var refs []string
			if i := prev.Core.Ext.In; i != nil {
				mid, refs = i.MessageID, i.References
			} else if o := prev.Core.Ext.Out; o != nil {
				mid, refs = o.MessageID, o.References
			}
			if mid != "" {
				out.InReplyTo = mid
				out.References = append(append([]string(nil), refs...), mid)
				if len(out.References) > 20 {
					out.References = out.References[len(out.References)-20:]
				}
			}
		}
		m.mu.Unlock()
	}
	return out, nil
}
