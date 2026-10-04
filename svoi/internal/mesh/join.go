package mesh

import (
	"bytes"
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"math/big"
	"net"
	"net/netip"
	"sort"
	"strings"
	"time"

	"github.com/quic-go/quic-go"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/magic"
)

// Joining a mesh without a server: an admin device creates an invite that
// carries the mesh root key, its own device key, a one-time secret and a few
// addresses. The newcomer dials the inviter directly (QUIC, ALPN themesh-join/1),
// verifies the inviter's certificate against the root key from the invite,
// proves knowledge of the secret bound to this very TLS session, and receives a
// member certificate (plus, for admin invites, the authority key).

const (
	maxJoinFails = 5
	maxJoinFrame = 4 << 10 // a join request is a handle, a proof and two short names
)

// Why a join failed, told apart so that an interface can say what to do about it instead of
// calling everything a bad code.
var (
	// ErrInviterUnreachable: no address of the invitation answered. From outside this is also what a
	// used, cancelled or expired invitation looks like: the inviter stays silent to strangers it has
	// no invitation for.
	ErrInviterUnreachable = errors.New("mesh: cannot reach the inviting device")
	// ErrInviteExpired: the invitation is past its lifetime by this device's clock.
	ErrInviteExpired = errors.New("mesh: this invitation has expired")
	// ErrJoinRefused: the inviter answered, and said no.
	ErrJoinRefused = errors.New("mesh: join refused")
)

// joinError is a message written for people that is also one of the reasons above.
type joinError struct {
	reason error
	msg    string
}

func (e *joinError) Error() string { return e.msg }
func (e *joinError) Unwrap() error { return e.reason }

type joinRequest struct {
	Handle   []byte `json:"h"`
	Proof    []byte `json:"p"`
	Name     string `json:"name"`
	Platform string `json:"platform,omitempty"`
}

type joinResponse struct {
	RootCert []byte   `json:"root"`
	Cert     []byte   `json:"cert"`
	Members  [][]byte `json:"members,omitempty"`
	AuthSeed []byte   `json:"seed,omitempty"`
	MeshName string   `json:"mesh"`
}

// InviteInfo describes a pending invitation.
type InviteInfo struct {
	ID      string `json:"id"`
	Code    string `json:"code"`
	Admin   bool   `json:"admin"`
	Owner   string `json:"owner"` // whose device this is meant for; set by the inviter
	Created int64  `json:"created"`
	Expires int64  `json:"expires"`
}

func inviteID(h [8]byte) string { return hex.EncodeToString(h[:]) }

// maxInviteEndpoints is how many of the inviter's addresses go into an invitation. The code is read off a screen by a
// phone camera and pasted by people, and every address makes it longer: an IPv4 one by 7 bytes, an IPv6 one by 19.
const maxInviteEndpoints = 6

// inviteEndpoints picks the addresses for an invitation out of everything the device is reachable at. The newcomer tries
// them all at once, so what matters is that each way it may come is there: an address the router forwards, an address on
// the inviter's own network (a newcomer on the same Wi-Fi can only use that one), the public IPv4 address as the world
// sees it, a global IPv6 address. "Public ones first, the rest after, cut at the limit" used to push the home-network
// address out of the code on hosts that have many IPv6 addresses.
func inviteEndpoints(eps []magic.Endpoint) []netip.AddrPort {
	type class struct {
		limit int
		list  []netip.AddrPort
	}
	var loop, mapped, lan4, pub4, pub6, lan6 = class{limit: 1}, class{limit: 1}, class{limit: 3}, class{limit: 2}, class{limit: 2}, class{limit: 1}
	for _, e := range eps {
		a := e.Addr.Addr().Unmap()
		var c *class
		switch {
		case a.IsLoopback():
			c = &loop // only when asked for (tests, demos, several devices on one machine)
		case e.Kind == magic.EPMapped:
			c = &mapped
		case isPrivate(a) && a.Is4():
			c = &lan4
		case isPrivate(a):
			c = &lan6
		case a.Is4():
			c = &pub4
		default:
			c = &pub6
		}
		if len(c.list) < c.limit {
			c.list = append(c.list, e.Addr)
		}
	}
	// One from each kind in turn, the kinds in order of how much a missing one hurts.
	kinds := []class{loop, mapped, lan4, pub4, pub6, lan6}
	out := make([]netip.AddrPort, 0, maxInviteEndpoints)
	for round := 0; len(out) < maxInviteEndpoints; round++ {
		took := false
		for _, k := range kinds {
			if round < len(k.list) && len(out) < maxInviteEndpoints {
				out = append(out, k.list[round])
				took = true
			}
		}
		if !took {
			break
		}
	}
	return out
}

// NewInvite creates a one-time invitation for another device of this device's own
// owner. Only admin devices can invite.
func (n *Node) NewInvite(admin bool, ttl time.Duration) (InviteInfo, error) {
	return n.NewInviteFor(admin, ttl, "")
}

// NewInviteFor creates a one-time invitation whose device will carry the given
// owner label in its certificate (empty: this device's own owner). The label is
// chosen by the inviter, never by the joiner: "own devices" (auto-accepting
// files, for example) must mean devices an administrator vouched for as one
// person's, not whatever a newcomer typed.
func (n *Node) NewInviteFor(admin bool, ttl time.Duration, owner string) (InviteInfo, error) {
	n.mu.Lock()
	if n.root == nil {
		n.mu.Unlock()
		return InviteInfo{}, ErrNotConfigured
	}
	if n.auth == nil {
		n.mu.Unlock()
		return InviteInfo{}, ErrNotAdmin
	}
	if ttl <= 0 {
		ttl = 30 * time.Minute
	}
	if ttl > 7*24*time.Hour {
		ttl = 7 * 24 * time.Hour
	}
	mg := n.magic
	root, meshName := n.root, n.meshName
	if owner = identity.SanitizeOwner(owner); owner == "" && n.self != nil {
		owner = n.self.Owner
	}
	n.mu.Unlock()

	var eps []netip.AddrPort
	if mg != nil {
		eps = inviteEndpoints(mg.Endpoints())
	}
	if len(eps) == 0 {
		return InviteInfo{}, errors.New("mesh: this device has no network address yet; connect to a network and try again")
	}
	ident, err := identity.NewInvite(root.Pub, n.device().ID, ttl, admin, eps, meshName)
	if err != nil {
		return InviteInfo{}, err
	}
	h := ident.Handle()
	rec := &invite{
		secret:  append([]byte(nil), ident.Secret[:]...),
		expires: ident.Expires,
		admin:   admin,
		owner:   owner,
		created: time.Now(),
		code:    ident.Encode(),
	}
	n.mu.Lock()
	n.invites[h] = rec
	n.mu.Unlock()
	n.updateAnonymous()
	n.saveNow()
	return rec.info(h), nil
}

func (i *invite) info(h [8]byte) InviteInfo {
	return InviteInfo{ID: inviteID(h), Code: i.code, Admin: i.admin, Owner: i.owner, Created: i.created.Unix(), Expires: i.expires.Unix()}
}

// Invites lists pending invitations.
func (n *Node) Invites() []InviteInfo {
	n.expireInvites()
	n.mu.RLock()
	defer n.mu.RUnlock()
	var out []InviteInfo
	for h, i := range n.invites {
		out = append(out, i.info(h))
	}
	sort.Slice(out, func(a, b int) bool { return out[a].Created < out[b].Created })
	return out
}

// CancelInvite withdraws a pending invitation.
func (n *Node) CancelInvite(id string) bool {
	n.mu.Lock()
	var found bool
	for h := range n.invites {
		if inviteID(h) == id {
			delete(n.invites, h)
			found = true
		}
	}
	n.mu.Unlock()
	n.updateAnonymous()
	n.saveNow()
	return found
}

// updateAnonymous keeps magic's anonymous (joiner) mode in step with reality:
// on while an invitation is outstanding or a join handshake is still running.
func (n *Node) updateAnonymous() {
	n.mu.RLock()
	mg := n.magic
	open := n.joinOpenLocked()
	n.mu.RUnlock()
	if mg != nil {
		mg.SetAnonymous(open || n.joinActive.Load() > 0)
	}
}

func (n *Node) joinOpenLocked() bool {
	now := time.Now()
	for _, i := range n.invites {
		if now.Before(i.expires) && i.fails < maxJoinFails {
			return true
		}
	}
	return false
}

func (n *Node) joinOpen() bool {
	n.mu.RLock()
	defer n.mu.RUnlock()
	return n.joinOpenLocked()
}

func (n *Node) expireInvites() {
	now := time.Now()
	n.mu.Lock()
	changed := false
	for h, i := range n.invites {
		if !now.Before(i.expires) {
			delete(n.invites, h)
			changed = true
		}
	}
	n.mu.Unlock()
	if changed {
		n.updateAnonymous()
		n.saveSoon()
	}
}

// handleJoin runs on the inviter for a connection using the join ALPN.
func (n *Node) handleJoin(conn *quic.Conn) {
	n.joinActive.Add(1)
	defer func() {
		n.joinActive.Add(-1)
		n.updateAnonymous()
	}()
	defer func() {
		// Give the client time to read the answer before the connection goes away.
		select {
		case <-conn.Context().Done():
		case <-time.After(5 * time.Second):
		}
		_ = conn.CloseWithError(closeNormal, "done")
	}()
	ctx, cancel := context.WithTimeout(conn.Context(), 15*time.Second)
	defer cancel()
	s, err := conn.AcceptStream(ctx)
	if err != nil {
		return
	}
	_ = s.SetDeadline(time.Now().Add(15 * time.Second))
	var req joinRequest
	if err := readFrameMax(s, &req, maxJoinFrame); err != nil {
		return
	}
	reply := func(resp *joinResponse, rerr error) {
		var f responseFrame
		if rerr != nil {
			f.Err = asRPCError(rerr)
		} else {
			f.OK = true
			f.Result = mustJSON(resp)
		}
		_ = writeFrame(s, f)
		_ = s.Close()
	}

	cs := conn.ConnectionState().TLS
	if len(cs.PeerCertificates) == 0 {
		reply(nil, Errf(CodeInvalid, "no certificate"))
		return
	}
	pub, ok := cs.PeerCertificates[0].PublicKey.(ed25519.PublicKey)
	if !ok {
		reply(nil, Errf(CodeInvalid, "device key must be Ed25519"))
		return
	}
	joinerID, err := identity.IDFromPublicKey(pub)
	if err != nil {
		reply(nil, Errf(CodeInvalid, "bad device key"))
		return
	}
	exporter, err := cs.ExportKeyingMaterial("themesh-join", nil, 32)
	if err != nil {
		reply(nil, Errf(CodeInternal, "cannot bind to the TLS session"))
		return
	}
	if len(req.Handle) != 8 {
		reply(nil, Errf(CodeDenied, "invalid invitation"))
		return
	}
	var handle [8]byte
	copy(handle[:], req.Handle)

	n.mu.Lock()
	inv := n.invites[handle]
	auth, root := n.auth, n.root
	if inv == nil || auth == nil || !time.Now().Before(inv.expires) || inv.fails >= maxJoinFails {
		n.mu.Unlock()
		reply(nil, Errf(CodeDenied, "this invitation is invalid or has expired"))
		return
	}
	ident := inv.toIdentity(root.Pub, n.device().ID)
	if !ident.CheckProof(exporter, req.Proof) {
		inv.fails++
		n.mu.Unlock()
		reply(nil, Errf(CodeDenied, "this invitation is invalid or has expired"))
		return
	}
	if _, revoked := n.revoked[joinerID]; revoked {
		n.mu.Unlock()
		reply(nil, Errf(CodeDenied, "this device was revoked from the mesh"))
		return
	}
	// The invitation is single use: consume it before issuing anything.
	delete(n.invites, handle)
	existing := make([]*identity.Member, 0, len(n.peers)+1)
	existing = append(existing, n.self)
	for _, p := range n.peers {
		existing = append(existing, p.Member())
	}
	meshName := n.meshName
	mg := n.magic
	n.mu.Unlock()

	m, err := auth.Issue(identity.IssueRequest{ID: joinerID, Name: req.Name, Owner: inv.owner, Admin: inv.admin}, existing)
	if err != nil {
		reply(nil, Errf(CodeInternal, "cannot issue certificate: %v", err))
		return
	}
	resp := &joinResponse{RootCert: root.DER, Cert: m.CertDER, MeshName: meshName}
	for _, e := range existing {
		resp.Members = append(resp.Members, e.CertDER)
	}
	if inv.admin {
		resp.AuthSeed = auth.Priv.Seed()
	}
	n.learnMember(m)
	if mg != nil {
		if ua, ok := conn.RemoteAddr().(*net.UDPAddr); ok {
			mg.AddCandidates(joinerID, []netip.AddrPort{ua.AddrPort()}, magic.SrcObserved)
		}
		mg.Poke(joinerID)
	}
	n.log.Info("device joined the mesh", "name", m.Name, "admin", inv.admin)
	reply(resp, nil)
	n.saveSoon()
	go n.pushSyncToAll()
}

// ---- joiner side ----

// selfSignedCert is the certificate of the join handshake, for both sides: it is
// signed by the device key itself and says nothing about the device or its owner
// (the handshake proves possession of the key, which is what the other side pins).
func selfSignedCert(dev *identity.Device) tls.Certificate {
	serial, _ := rand.Int(rand.Reader, new(big.Int).Lsh(big.NewInt(1), 62))
	tpl := &x509.Certificate{
		SerialNumber: serial,
		Subject:      pkix.Name{CommonName: "themesh"},
		NotBefore:    time.Now().Add(-24 * time.Hour),
		NotAfter:     time.Now().AddDate(1, 0, 0),
		KeyUsage:     x509.KeyUsageDigitalSignature,
		ExtKeyUsage:  []x509.ExtKeyUsage{x509.ExtKeyUsageClientAuth},
	}
	der, err := x509.CreateCertificate(rand.Reader, tpl, tpl, dev.Priv.Public(), dev.Priv)
	if err != nil {
		panic("mesh: cannot create self-signed certificate: " + err.Error())
	}
	return tls.Certificate{Certificate: [][]byte{der}, PrivateKey: dev.Priv}
}

func (n *Node) joinTLS(inv *identity.Invite) *tls.Config {
	return &tls.Config{
		MinVersion:         tls.VersionTLS13,
		ServerName:         "themesh",
		InsecureSkipVerify: true, // pinned below to the root key and inviter from the invite
		Certificates:       []tls.Certificate{selfSignedCert(n.device())},
		NextProtos:         []string{ALPNJoin},
		VerifyPeerCertificate: func(raw [][]byte, _ [][]*x509.Certificate) error {
			if len(raw) == 0 {
				return errors.New("no server certificate")
			}
			leaf, err := x509.ParseCertificate(raw[0])
			if err != nil {
				return err
			}
			// The inviter shows a plain self-signed certificate (its member certificate
			// would tell a not-yet-member its name, owner and overlay addresses). TLS has
			// proved that it holds the key in it; the invitation names the key it must be.
			// The mesh itself is vouched for by the root key in the invitation, which
			// signs everything the inviter sends back.
			pub, ok := leaf.PublicKey.(ed25519.PublicKey)
			if !ok || !bytes.Equal(pub, inv.Inviter[:]) {
				return errors.New("the device that answered is not the one that made the invitation")
			}
			return nil
		},
	}
}

// JoinMesh joins the mesh described by an invitation code.
func (n *Node) JoinMesh(ctx context.Context, code, deviceName string) error {
	n.mu.RLock()
	configured := n.root != nil
	n.mu.RUnlock()
	if configured {
		return errors.New("mesh: already part of a mesh; leave it first")
	}
	inv, err := identity.ParseInvite(code)
	if err != nil {
		return err
	}
	// An inviter that has no valid invitation left does not answer strangers at all,
	// so an expired code would end in a long wait and a misleading "cannot reach".
	// Say it at once, and name the one reason it can be wrong: this device's clock.
	if inv.Expired(time.Now()) {
		return &joinError{ErrInviteExpired, "mesh: this invitation has expired; ask for a new one (if it should still be valid, check the date and time on this device)"}
	}
	if len(inv.Endpoints) == 0 {
		return errors.New("mesh: the invitation contains no addresses to connect to")
	}
	if deviceName == "" {
		deviceName = n.cfg.DeviceName
	}

	// A temporary UDP endpoint that only speaks to the inviter.
	port := n.udpPortOrDefault()
	mg, err := magic.New(magic.Config{
		Device: n.device(), Port: port, Listen: n.cfg.Listen, LocalAddrs: n.cfg.LocalAddrs, Timing: n.cfg.Timing,
	})
	if err != nil && n.cfg.UDPPort == 0 {
		mg, err = magic.New(magic.Config{
			Device: n.device(), Port: 0, Listen: n.cfg.Listen, LocalAddrs: n.cfg.LocalAddrs, Timing: n.cfg.Timing,
		})
	}
	if err != nil {
		return err
	}
	mg.SetAnonymous(true)
	tr := &quic.Transport{Conn: mg}
	defer func() {
		_ = tr.Close()
		_ = mg.Close()
	}()

	dctx, cancel := context.WithTimeout(ctx, 25*time.Second)
	defer cancel()
	type result struct {
		conn *quic.Conn
		err  error
	}
	results := make(chan result, len(inv.Endpoints))
	tlsConf := n.joinTLS(inv)
	qconf := n.quicConf()
	for _, ep := range inv.Endpoints {
		go func(ep netip.AddrPort) {
			tc := tlsConf.Clone()
			tc.ServerName = joinSNI(inv.Secret[:]) // each attempt shows its own token
			c, err := tr.Dial(dctx, net.UDPAddrFromAddrPort(ep), tc, qconf)
			results <- result{c, err}
		}(ep)
	}
	var conn *quic.Conn
	var firstErr error
	for i := 0; i < len(inv.Endpoints); i++ {
		r := <-results
		if r.err == nil && conn == nil {
			conn = r.conn
			cancel() // stop the other attempts
		} else if r.err == nil {
			_ = r.conn.CloseWithError(closeNormal, "")
		} else if firstErr == nil || (errors.Is(firstErr, context.Canceled) && !errors.Is(r.err, context.Canceled)) {
			firstErr = r.err
		}
	}
	if conn == nil {
		if firstErr == nil {
			firstErr = errors.New("no response")
		}
		// A device with no valid invitation left stays silent to strangers on purpose, so
		// "it was used, cancelled or has expired" looks exactly like "it is offline".
		tried := make([]string, 0, len(inv.Endpoints))
		for _, ep := range inv.Endpoints {
			tried = append(tried, ep.String())
		}
		return &joinError{ErrInviterUnreachable, fmt.Sprintf("mesh: cannot reach the inviting device (%v; tried %s). Make sure it is online and reachable from this network, and that the invitation is still valid (it may have been used, cancelled or have expired)", firstErr, strings.Join(tried, ", "))}
	}
	defer conn.CloseWithError(closeNormal, "done")

	cs := conn.ConnectionState().TLS
	exporter, err := cs.ExportKeyingMaterial("themesh-join", nil, 32)
	if err != nil {
		return err
	}
	hctx, hcancel := context.WithTimeout(ctx, 20*time.Second)
	defer hcancel()
	s, err := conn.OpenStreamSync(hctx)
	if err != nil {
		return err
	}
	_ = s.SetDeadline(time.Now().Add(20 * time.Second))
	h := inv.Handle()
	if err := writeFrame(s, joinRequest{
		Handle: h[:], Proof: inv.Proof(exporter), Name: deviceName, Platform: Platform(),
	}); err != nil {
		return err
	}
	cs2 := &ClientStream{s: s}
	var resp joinResponse
	if err := cs2.ReadResponse(&resp); err != nil {
		var re *RPCError
		if errors.As(err, &re) {
			return &joinError{ErrJoinRefused, fmt.Sprintf("mesh: join refused: %s", re.Msg)}
		}
		return err
	}

	root, err := identity.ParseRoot(resp.RootCert)
	if err != nil {
		return err
	}
	if !bytes.Equal(root.Pub, inv.Root) {
		return errors.New("mesh: the mesh root in the answer does not match the invitation")
	}
	self, err := root.Verify(resp.Cert)
	if err != nil {
		return err
	}
	if self.ID != n.device().ID {
		return errors.New("mesh: the issued certificate is for a different device")
	}
	var auth *identity.Authority
	if len(resp.AuthSeed) > 0 {
		if auth, err = identity.AuthorityFromSeed(resp.AuthSeed, resp.RootCert); err != nil {
			return err
		}
		if !self.Admin {
			return errors.New("mesh: received an authority key without an admin certificate")
		}
	}

	n.mu.Lock()
	n.root, n.auth, n.self = root, auth, self
	n.meshName = strings.TrimSpace(resp.MeshName)
	for _, der := range resp.Members {
		if m, err := root.Verify(der); err == nil && m.ID != n.device().ID {
			if _, ok := n.peers[m.ID]; !ok {
				p := newPeer(n, m)
				n.peers[m.ID] = p
			}
		}
	}
	// The inviter is reachable at the address we just used: remember it.
	if p := n.peers[inv.Inviter]; p != nil {
		for _, ep := range inv.Endpoints {
			p.storedEndpoints = append(p.storedEndpoints, ep.String())
		}
	}
	n.mu.Unlock()
	if err := n.saveState(); err != nil {
		return err
	}
	// Release the temporary socket before the permanent one binds the same port.
	_ = conn.CloseWithError(closeNormal, "done")
	_ = tr.Close()
	_ = mg.Close()
	return n.startMember()
}

func mustJSON(v any) []byte {
	b, err := json.Marshal(v)
	if err != nil {
		panic(err)
	}
	return b
}
