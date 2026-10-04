package mesh

import (
	"bytes"
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"encoding/binary"
	"encoding/hex"
	"errors"
	"fmt"
	"net"
	"net/netip"
	"sort"
	"sync"
	"time"

	"github.com/quic-go/quic-go"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/magic"
)

// Asking a nearby device to add this one (see nearby.go for how the device is found).
//
//	newcomer                                            inviter (an admin that announced itself)
//	QUIC, ALPN themesh-nearby/1, server name
//	<ticket from its announcement>.nearby.mesh    ->    only a neighbour that heard the announcement is answered
//	hello{name, os}                               ->    the request is listed for the person at this device
//	                                              <-    ack{waiting}
//	both screens show the same six digits: they come from the keys of this very TLS session (RFC 5705 exporter), so
//	two sessions stitched together by somebody in the middle show two different numbers
//	the person compares them and taps "they match" on the newcomer: confirm{true}   ->
//	the person taps "Add" on the inviter (the same request lists the name and the digits)
//	                                              <-    the membership: the same answer as to an invitation
//
// The answer is given only when both people have said yes, within two minutes; whoever says no, goes away or is silent
// ends it. A newcomer never learns anything of the mesh before that, and the inviter never gives anything to a device
// that nobody at it approved: an announcement alone, which anybody on the network can send, does not add a device.
const (
	nearbySASLabel = "themesh-nearby-sas"
)

// nearbyCode is the six digits both people compare, from the keying material of the TLS session.
func nearbyCode(exporter []byte) string {
	return fmt.Sprintf("%06d", binary.BigEndian.Uint64(exporter[:8])%1_000_000)
}

type nearbyHello struct {
	Name string `json:"name"`
	OS   string `json:"os,omitempty"`
}

type nearbyConfirm struct {
	Match bool `json:"match"`
}

// ---- the inviter ----

// NearbyRequest is a newcomer that asks this device to add it.
type NearbyRequest struct {
	ID        string `json:"id"`
	Name      string `json:"name"` // what the newcomer calls itself (it says it without proof)
	OS        string `json:"os"`
	Code      string `json:"code"`      // the six digits, as the newcomer shows them
	Confirmed bool   `json:"confirmed"` // the person at the newcomer has said that the digits match
	Created   int64  `json:"created"`
	Expires   int64  `json:"expires"`
}

type nearbyDecision struct {
	approve bool
	owner   string
}

type nearbyRequest struct {
	NearbyRequest
	joiner   identity.ID
	addr     netip.Addr
	decision chan nearbyDecision
}

// NearbyRequests lists the devices that asked to be added and wait for an answer.
func (n *Node) NearbyRequests() []NearbyRequest {
	out := []NearbyRequest{}
	n.nearby.mu.Lock()
	for _, r := range n.nearby.requests {
		out = append(out, r.NearbyRequest)
	}
	n.nearby.mu.Unlock()
	sort.Slice(out, func(i, j int) bool { return out[i].Created < out[j].Created })
	return out
}

// AnswerNearbyRequest approves or refuses a request. owner is whose device the newcomer is (empty: the same person's as
// this one); the newcomer is added only when it has confirmed the digits as well.
func (n *Node) AnswerNearbyRequest(id string, approve bool, owner string) error {
	n.mu.RLock()
	admin := n.auth != nil
	n.mu.RUnlock()
	if !admin {
		return ErrNotAdmin
	}
	n.nearby.mu.Lock()
	r := n.nearby.requests[id]
	n.nearby.mu.Unlock()
	if r == nil {
		return ErrNoSuchRequest
	}
	select {
	case r.decision <- nearbyDecision{approve: approve, owner: identity.SanitizeOwner(owner)}:
	default: // already answered
	}
	return nil
}

func (n *Node) nearbyAddRequest(joiner identity.ID, hello nearbyHello, addr netip.Addr, code string) (*nearbyRequest, error) {
	id := make([]byte, 6)
	_, _ = rand.Read(id)
	now := time.Now()
	r := &nearbyRequest{
		NearbyRequest: NearbyRequest{
			ID: hex.EncodeToString(id), Name: cutText(hello.Name, nearbyTextMax), OS: cutText(hello.OS, 16), Code: code,
			Created: now.Unix(), Expires: now.Add(nearbyLife()).Unix(),
		},
		joiner: joiner, addr: addr.Unmap(), decision: make(chan nearbyDecision, 1),
	}
	if r.Name == "" {
		r.Name = "?"
	}
	n.nearby.mu.Lock()
	if n.nearby.requests == nil {
		n.nearby.requests = map[string]*nearbyRequest{}
	}
	if len(n.nearby.requests) >= nearbyMaxRequests {
		n.nearby.mu.Unlock()
		return nil, errors.New("too many requests")
	}
	for _, o := range n.nearby.requests {
		if o.addr == r.addr || o.joiner == joiner { // one at a time from an address, and from a device
			n.nearby.mu.Unlock()
			return nil, errors.New("a request from there is already waiting")
		}
	}
	n.nearby.requests[r.ID] = r
	n.nearby.mu.Unlock()
	n.updateAnonymous()
	n.emit(Event{Kind: EvNearby})
	return r, nil
}

func (n *Node) nearbyRemoveRequest(id string) {
	n.nearby.mu.Lock()
	delete(n.nearby.requests, id)
	n.nearby.mu.Unlock()
	n.updateAnonymous()
	n.emit(Event{Kind: EvNearby})
}

// nearbyTLS is the configuration for a newcomer's handshake: the same self-signed certificates as a join (the key is what
// counts; the person decides whether to trust the device behind it).
func (n *Node) nearbyTLS() *tls.Config {
	return &tls.Config{
		MinVersion:   tls.VersionTLS13,
		Certificates: []tls.Certificate{selfSignedCert(n.device())},
		ClientAuth:   tls.RequireAnyClientCert,
		NextProtos:   []string{ALPNNearby},
	}
}

// handleNearby runs on the inviter for a connection that uses ALPNNearby.
func (n *Node) handleNearby(conn *quic.Conn) {
	n.joinActive.Add(1)
	defer func() {
		n.joinActive.Add(-1)
		n.updateAnonymous()
	}()
	defer func() {
		// Give the newcomer time to read the answer before the connection goes away.
		select {
		case <-conn.Context().Done():
		case <-time.After(5 * time.Second):
		}
		_ = conn.CloseWithError(closeNormal, "done")
	}()
	ctx, cancel := context.WithTimeout(conn.Context(), 15*time.Second)
	s, err := conn.AcceptStream(ctx)
	cancel()
	if err != nil {
		return
	}
	_ = s.SetDeadline(time.Now().Add(15 * time.Second))
	var hello nearbyHello
	if err := readFrameMax(s, &hello, maxJoinFrame); err != nil {
		return
	}
	fail := func(err error) {
		_ = s.SetWriteDeadline(time.Now().Add(10 * time.Second))
		_ = writeFrame(s, responseFrame{Err: asRPCError(err)})
		_ = s.Close()
	}

	cs := conn.ConnectionState().TLS
	if len(cs.PeerCertificates) == 0 {
		fail(Errf(CodeInvalid, "no certificate"))
		return
	}
	pub, ok := cs.PeerCertificates[0].PublicKey.(ed25519.PublicKey)
	if !ok {
		fail(Errf(CodeInvalid, "device key must be Ed25519"))
		return
	}
	joinerID, err := identity.IDFromPublicKey(pub)
	if err != nil {
		fail(Errf(CodeInvalid, "bad device key"))
		return
	}
	exporter, err := cs.ExportKeyingMaterial(nearbySASLabel, nil, 8)
	if err != nil {
		fail(Errf(CodeInternal, "cannot bind to the TLS session"))
		return
	}
	n.mu.RLock()
	_, revoked := n.revoked[joinerID]
	n.mu.RUnlock()
	if revoked {
		fail(Errf(CodeDenied, "this device was revoked from the mesh"))
		return
	}
	var addr netip.Addr
	if ua, ok := conn.RemoteAddr().(*net.UDPAddr); ok {
		addr = ua.AddrPort().Addr()
	}
	req, err := n.nearbyAddRequest(joinerID, hello, addr, nearbyCode(exporter))
	if err != nil {
		fail(Errf(CodeDenied, "%v", err))
		return
	}
	defer n.nearbyRemoveRequest(req.ID)
	n.log.Info("a device nearby asks to be added", "name", req.Name, "from", addr)

	_ = s.SetWriteDeadline(time.Now().Add(10 * time.Second))
	if err := writeFrame(s, responseFrame{OK: true, Result: mustJSON(map[string]string{"state": "waiting"})}); err != nil {
		return
	}

	// The newcomer's person says "the digits match" (or the newcomer goes away).
	confirmed := make(chan bool, 1)
	go func() {
		var c nearbyConfirm
		_ = s.SetReadDeadline(time.Now().Add(nearbyLife() + 10*time.Second))
		if err := readFrameMax(s, &c, maxJoinFrame); err != nil {
			confirmed <- false
			return
		}
		confirmed <- c.Match
	}()

	var decision *nearbyDecision
	matched := false
	timer := time.NewTimer(nearbyLife())
	defer timer.Stop()
	for decision == nil || !matched {
		select {
		case d := <-req.decision:
			if !d.approve {
				fail(Errf(CodeDenied, "the person at the other device said no"))
				return
			}
			decision = &d
		case ok := <-confirmed:
			if !ok {
				return // the newcomer's person said "they differ", or it went away
			}
			matched = true
			n.nearby.mu.Lock()
			req.Confirmed = true
			n.nearby.mu.Unlock()
			n.emit(Event{Kind: EvNearby})
		case <-conn.Context().Done():
			return
		case <-timer.C:
			fail(Errf(CodeDenied, "the request expired"))
			return
		}
	}

	owner := decision.owner
	n.mu.RLock()
	if owner == "" && n.self != nil {
		owner = n.self.Owner
	}
	n.mu.RUnlock()
	resp, err := n.issueMembership(conn, joinerID, hello.Name, owner, false)
	if err != nil {
		fail(Errf(CodeInternal, "cannot issue certificate: %v", err))
		return
	}
	_ = s.SetWriteDeadline(time.Now().Add(15 * time.Second))
	_ = writeFrame(s, responseFrame{OK: true, Result: mustJSON(resp)})
	_ = s.Close()
	n.saveSoon()
	go n.pushSyncToAll()
}

// ---- the newcomer ----

// NearbyJoinStatus is what this device is doing to get added by a device nearby.
type NearbyJoinStatus struct {
	// State: idle | connecting | waiting (both screens show the digits) | confirmed (the person said they match; the
	// other person is to add this device) | joined | denied | failed | canceled.
	State  string        `json:"state"`
	Peer   *NearbyDevice `json:"peer,omitempty"`
	Code   string        `json:"code,omitempty"`
	Reason string        `json:"reason,omitempty"` // with denied and failed: offline | denied | expired | invalid
	Error  string        `json:"error,omitempty"`
}

type nearbyJoin struct {
	status  NearbyJoinStatus
	confirm chan struct{}
	cancel  context.CancelFunc
	once    sync.Once
}

// NearbyJoinStatus reports the attempt of this device to be added (State "idle" when there is none).
func (n *Node) NearbyJoinStatus() NearbyJoinStatus {
	n.nearby.mu.Lock()
	defer n.nearby.mu.Unlock()
	if n.nearby.join == nil {
		return NearbyJoinStatus{State: "idle"}
	}
	return n.nearby.join.status
}

func (n *Node) setNearbyJoin(j *nearbyJoin, f func(*NearbyJoinStatus)) {
	n.nearby.mu.Lock()
	f(&j.status)
	n.nearby.mu.Unlock()
	n.emit(Event{Kind: EvNearby})
}

func (j *nearbyJoin) running() bool {
	switch j.status.State {
	case "connecting", "waiting", "confirmed":
		return true
	}
	return false
}

// StartNearbyJoin asks the device nearby with this id to add this one, calling this device deviceName (empty: its usual name).
// Progress is in NearbyJoinStatus; it ends in "joined" (the device is a member now) or a reason.
func (n *Node) StartNearbyJoin(id, deviceName string) error {
	if n.Configured() {
		return errors.New("mesh: already part of a mesh; leave it first")
	}
	n.nearby.mu.Lock()
	if cur := n.nearby.join; cur != nil && cur.running() {
		n.nearby.mu.Unlock()
		return errors.New("mesh: a request is already being made")
	}
	var found *nearbyEntry
	for _, e := range n.nearby.seen {
		if e.ID == id && time.Since(e.heard) <= nearbyListedFor {
			cp := *e
			found = &cp
		}
	}
	if found == nil {
		n.nearby.mu.Unlock()
		return ErrNearbyGone
	}
	ctx, cancel := context.WithCancel(context.Background())
	peer := found.NearbyDevice
	j := &nearbyJoin{status: NearbyJoinStatus{State: "connecting", Peer: &peer}, confirm: make(chan struct{}), cancel: cancel}
	n.nearby.join = j
	n.nearby.mu.Unlock()
	n.emit(Event{Kind: EvNearby})
	if deviceName == "" {
		deviceName = n.cfg.DeviceName
	}
	go func() {
		defer cancel()
		n.runNearbyJoin(ctx, j, found, deviceName)
	}()
	return nil
}

// ConfirmNearbyJoin: the person says that the digits on both screens are the same.
func (n *Node) ConfirmNearbyJoin() error {
	n.nearby.mu.Lock()
	j := n.nearby.join
	ok := j != nil && j.status.State == "waiting"
	n.nearby.mu.Unlock()
	if !ok {
		return errors.New("mesh: nothing to confirm")
	}
	j.once.Do(func() { close(j.confirm) })
	return nil
}

// CancelNearbyJoin ends the attempt (or, when it is over, forgets how it ended).
func (n *Node) CancelNearbyJoin() {
	n.nearby.mu.Lock()
	j := n.nearby.join
	if j == nil {
		n.nearby.mu.Unlock()
		return
	}
	if !j.running() {
		n.nearby.join = nil
		n.nearby.mu.Unlock()
		n.emit(Event{Kind: EvNearby})
		return
	}
	n.nearby.mu.Unlock()
	j.cancel()
}

func (n *Node) runNearbyJoin(ctx context.Context, j *nearbyJoin, e *nearbyEntry, deviceName string) {
	fail := func(reason string, err error) {
		state := "failed"
		switch {
		case ctx.Err() != nil && errors.Is(err, context.Canceled):
			state, reason = "canceled", ""
		case reason == "denied" || reason == "expired":
			state = "denied"
		}
		n.setNearbyJoin(j, func(s *NearbyJoinStatus) {
			s.State, s.Reason, s.Code = state, reason, ""
			if err != nil {
				s.Error = err.Error()
			}
		})
	}

	port := n.udpPortOrDefault()
	mg, err := magic.New(magic.Config{Device: n.device(), Port: port, Listen: n.cfg.Listen, LocalAddrs: n.cfg.LocalAddrs, Timing: n.cfg.Timing})
	if err != nil && n.cfg.UDPPort == 0 {
		mg, err = magic.New(magic.Config{Device: n.device(), Port: 0, Listen: n.cfg.Listen, LocalAddrs: n.cfg.LocalAddrs, Timing: n.cfg.Timing})
	}
	if err != nil {
		fail("failed", err)
		return
	}
	mg.SetAnonymous(true)
	tr := &quic.Transport{Conn: mg}
	closed := false
	closeAll := func() {
		if !closed {
			closed = true
			_ = tr.Close()
			_ = mg.Close()
		}
	}
	defer closeAll()

	tlsConf := &tls.Config{
		MinVersion:         tls.VersionTLS13,
		ServerName:         nearbySNI(e.ticket),
		InsecureSkipVerify: true, // pinned below to the key that announced itself
		Certificates:       []tls.Certificate{selfSignedCert(n.device())},
		NextProtos:         []string{ALPNNearby},
		VerifyPeerCertificate: func(raw [][]byte, _ [][]*x509.Certificate) error {
			if len(raw) == 0 {
				return errors.New("no server certificate")
			}
			leaf, err := x509.ParseCertificate(raw[0])
			if err != nil {
				return err
			}
			pub, ok := leaf.PublicKey.(ed25519.PublicKey)
			if !ok || !bytes.Equal(pub, e.devID[:]) {
				return errors.New("the device that answered is not the one that announced itself")
			}
			return nil
		},
	}
	qc := *n.quicConf()
	qc.GetConfigForClient = nil
	qc.KeepAlivePeriod = 8 * time.Second // the person at the other device takes their time: keep the connection alive
	qc.MaxIdleTimeout = 30 * time.Second

	dctx, dcancel := context.WithTimeout(ctx, 15*time.Second)
	conn, err := tr.Dial(dctx, net.UDPAddrFromAddrPort(e.addr), tlsConf, &qc)
	dcancel()
	if err != nil {
		fail("offline", fmt.Errorf("mesh: cannot reach the device nearby (%v)", err))
		return
	}
	defer conn.CloseWithError(closeNormal, "done")
	go func() { // a person who cancels ends the connection, which ends every read below
		select {
		case <-ctx.Done():
			_ = conn.CloseWithError(closeNormal, "canceled")
		case <-conn.Context().Done():
		}
	}()

	tlsState := conn.ConnectionState().TLS
	exporter, err := tlsState.ExportKeyingMaterial(nearbySASLabel, nil, 8)
	if err != nil {
		fail("failed", err)
		return
	}
	hctx, hcancel := context.WithTimeout(ctx, 15*time.Second)
	s, err := conn.OpenStreamSync(hctx)
	hcancel()
	if err != nil {
		fail("offline", err)
		return
	}
	os, _ := n.platform()
	_ = s.SetDeadline(time.Now().Add(15 * time.Second))
	if err := writeFrame(s, nearbyHello{Name: deviceName, OS: os}); err != nil {
		fail("failed", err)
		return
	}
	cs := &ClientStream{s: s}
	if err := cs.ReadResponse(nil); err != nil {
		var re *RPCError
		if errors.As(err, &re) {
			fail("denied", errors.New(re.Msg))
		} else {
			fail("failed", err)
		}
		return
	}

	// The answer of the inviter (a refusal can come at any time, the membership only after both people said yes) is
	// read all the while, so that a "no" reaches this person at once, not when they tap.
	type answer struct {
		resp joinResponse
		err  error
	}
	answered := make(chan answer, 1)
	go func() {
		_ = s.SetReadDeadline(time.Now().Add(nearbyLife() + 20*time.Second))
		var a answer
		a.err = cs.ReadResponse(&a.resp)
		answered <- a
	}()
	refused := func(err error) {
		var re *RPCError
		switch {
		case errors.As(err, &re):
			reason := "denied"
			if re.Msg == "the request expired" {
				reason = "expired"
			}
			fail(reason, errors.New(re.Msg))
		case ctx.Err() != nil:
			fail("failed", context.Canceled)
		default:
			fail("failed", err)
		}
	}

	// Both screens show the digits; this person says whether they match.
	n.setNearbyJoin(j, func(s *NearbyJoinStatus) { s.State, s.Code = "waiting", nearbyCode(exporter) })
	var a answer
	select {
	case <-j.confirm:
	case a = <-answered:
		if a.err == nil {
			a.err = errors.New("mesh: the device nearby answered before the digits were confirmed")
		}
		refused(a.err)
		return
	case <-ctx.Done():
		fail("failed", context.Canceled)
		return
	case <-conn.Context().Done():
		fail("failed", errors.New("mesh: the device nearby went away"))
		return
	case <-time.After(nearbyLife()):
		fail("expired", errors.New("mesh: nobody confirmed in time"))
		return
	}
	_ = s.SetWriteDeadline(time.Now().Add(10 * time.Second))
	if err := writeFrame(s, nearbyConfirm{Match: true}); err != nil {
		fail("failed", err)
		return
	}
	n.setNearbyJoin(j, func(s *NearbyJoinStatus) { s.State = "confirmed" })

	// The other person adds this device (or does not).
	select {
	case a = <-answered:
	case <-ctx.Done():
		fail("failed", context.Canceled)
		return
	}
	if a.err != nil {
		refused(a.err)
		return
	}
	if err := n.adoptMembership(&a.resp, nil, e.devID, []string{e.addr.String()}); err != nil {
		fail("invalid", err)
		return
	}
	// Release the temporary socket before the permanent one binds the same port.
	_ = conn.CloseWithError(closeNormal, "done")
	closeAll()
	if err := n.startMember(); err != nil {
		fail("failed", err)
		return
	}
	n.forgetNearby()
	n.setNearbyJoin(j, func(s *NearbyJoinStatus) { s.State, s.Code = "joined", "" })
}
