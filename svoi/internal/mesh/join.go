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
// addresses. The newcomer dials the inviter directly (QUIC, ALPN svoi-join/1),
// verifies the inviter's certificate against the root key from the invite,
// proves knowledge of the secret bound to this very TLS session, and receives a
// member certificate (plus, for admin invites, the authority key).

const maxJoinFails = 5

type joinRequest struct {
	Handle   []byte `json:"h"`
	Proof    []byte `json:"p"`
	Name     string `json:"name"`
	Owner    string `json:"owner"`
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
	Created int64  `json:"created"`
	Expires int64  `json:"expires"`
}

func inviteID(h [8]byte) string { return hex.EncodeToString(h[:]) }

// NewInvite creates a one-time invitation. Only admin devices can invite.
func (n *Node) NewInvite(admin bool, ttl time.Duration) (InviteInfo, error) {
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
	n.mu.Unlock()

	var eps []netip.AddrPort
	if mg != nil {
		// Public endpoints first, then LAN addresses; at most 8 fit in the code.
		pub, lan := []netip.AddrPort{}, []netip.AddrPort{}
		for _, e := range mg.Endpoints() {
			if isPrivate(e.Addr.Addr()) {
				lan = append(lan, e.Addr)
			} else {
				pub = append(pub, e.Addr)
			}
		}
		eps = append(pub, lan...)
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
		created: time.Now(),
		code:    ident.Encode(),
	}
	n.mu.Lock()
	n.invites[h] = rec
	n.mu.Unlock()
	n.updateAnonymous()
	n.saveSoon()
	return rec.info(h), nil
}

func (i *invite) info(h [8]byte) InviteInfo {
	return InviteInfo{ID: inviteID(h), Code: i.code, Admin: i.admin, Created: i.created.Unix(), Expires: i.expires.Unix()}
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
	n.saveSoon()
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
	if err := readFrame(s, &req); err != nil {
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
	exporter, err := cs.ExportKeyingMaterial("svoi-join", nil, 32)
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

	m, err := auth.Issue(identity.IssueRequest{ID: joinerID, Name: req.Name, Owner: req.Owner, Admin: inv.admin}, existing)
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

func selfSignedCert(dev *identity.Device) tls.Certificate {
	serial, _ := rand.Int(rand.Reader, new(big.Int).Lsh(big.NewInt(1), 62))
	tpl := &x509.Certificate{
		SerialNumber: serial,
		Subject:      pkix.Name{CommonName: "svoi joiner"},
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
		ServerName:         "svoi",
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
			if !ed25519.Verify(inv.Root, leaf.RawTBSCertificate, leaf.Signature) {
				return errors.New("the device you are joining through is not part of the mesh in the invitation")
			}
			pub, ok := leaf.PublicKey.(ed25519.PublicKey)
			if !ok || !bytes.Equal(pub, inv.Inviter[:]) {
				return errors.New("unexpected inviter identity")
			}
			return nil
		},
	}
}

// JoinMesh joins the mesh described by an invitation code.
func (n *Node) JoinMesh(ctx context.Context, code, deviceName, owner string) error {
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
	if inv.Expired(time.Now()) {
		return errors.New("mesh: this invitation has expired; ask for a new one")
	}
	if len(inv.Endpoints) == 0 {
		return errors.New("mesh: the invitation contains no addresses to connect to")
	}
	if deviceName == "" {
		deviceName = n.cfg.DeviceName
	}
	if owner == "" {
		owner = n.cfg.Owner
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
			c, err := tr.Dial(dctx, net.UDPAddrFromAddrPort(ep), tlsConf, qconf)
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
		return fmt.Errorf("mesh: cannot reach the inviting device (%v). Make sure it is online and reachable from this network", firstErr)
	}
	defer conn.CloseWithError(closeNormal, "done")

	cs := conn.ConnectionState().TLS
	exporter, err := cs.ExportKeyingMaterial("svoi-join", nil, 32)
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
		Handle: h[:], Proof: inv.Proof(exporter), Name: deviceName, Owner: owner, Platform: Platform(),
	}); err != nil {
		return err
	}
	cs2 := &ClientStream{s: s}
	var resp joinResponse
	if err := cs2.ReadResponse(&resp); err != nil {
		var re *RPCError
		if errors.As(err, &re) {
			return fmt.Errorf("mesh: join refused: %s", re.Msg)
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
