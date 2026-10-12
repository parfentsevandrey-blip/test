package mesh

import (
	"bytes"
	"context"
	"errors"
	"net"
	"net/netip"
	"sort"
	"strings"
	"time"

	"github.com/quic-go/quic-go"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/magic"
)

// Invitations between devices nearby (nearby.go is how the devices find each other, nearbyjoin.go how one is added).
//
// A device in a mesh that can add devices invites one that is not in a mesh: the invitation — signed, sent to that device
// alone and repeated every few seconds while it waits — names the mesh and carries what lets the device in; its person
// accepts, and it is added without another question, since the person at the inviting device already said whom. A device
// that is not in a mesh asks another one that is not in a mesh either to make one with it: the person there accepts, that
// device makes the mesh and invites the first one, which accepts by itself — it asked. When both people tap at once, the
// device with the smaller key makes the mesh. A device that gets into a mesh while such requests of its own wait turns
// them into invitations to its mesh, so that three devices tapped at once end up in one mesh.
//
// Nobody is added by a packet: an invitation names the device it is for and echoes that device's ticket, the device that
// accepts it connects with its own key, and that key must be the one the invitation was for.
//
// A member that is not an administrator holds no key to sign certificates with: it adds a device by asking an
// administrator of its mesh that is online to sign one (mesh.nearbyAdd). The administrator does that for any member — the
// person at the member said yes to the device in front of them — at most a few times a minute, and tells its own person.

// NearbyInvite is an invitation to this device from a device nearby.
type NearbyInvite struct {
	ID       string `json:"id"`   // the device that invites
	Kind     string `json:"kind"` // "invite": to join its mesh (MeshName); "pair": neither is in a mesh — to make one together
	Name     string `json:"name"`
	MeshName string `json:"meshName,omitempty"`
	OS       string `json:"os"`
	Received int64  `json:"received"`
}

// NearbyOutgoing is an invitation this device sent to a device nearby.
type NearbyOutgoing struct {
	ID   string `json:"id"` // the device invited
	Kind string `json:"kind"`
	Name string `json:"name"`
	OS   string `json:"os"`
	// State: sent (waits for the person there) | joining (accepted: the device is being added) | joined | declined |
	// expired | failed (Error says why).
	State   string `json:"state"`
	Error   string `json:"error,omitempty"`
	Created int64  `json:"created"`
	Expires int64  `json:"expires"`
}

// NearbyOptions is what the person chose for connecting with a device nearby.
type NearbyOptions struct {
	DeviceName string `json:"deviceName,omitempty"` // the name this device takes in the mesh it joins or makes
	MeshName   string `json:"meshName,omitempty"`   // the name of the mesh, when this device makes one for the two
	Owner      string `json:"owner,omitempty"`      // whose device this one is, when it makes the mesh
	PeerOwner  string `json:"peerOwner,omitempty"`  // whose device the other one is, when this one adds it (empty: the same person's)
}

const (
	nearbyInviteKind = "invite"
	nearbyPairKind   = "pair"
)

func kindName(k byte) string {
	if k == nearbyKindPair {
		return nearbyPairKind
	}
	return nearbyInviteKind
}

type nearbyIncoming struct {
	NearbyInvite
	from   identity.ID
	lan    netip.AddrPort // the inviting device's socket for the local network (a decline goes there)
	addr   netip.AddrPort // an invitation to a mesh: where to connect (its UDP port)
	ticket [16]byte
	heard  time.Time
}

type nearbyOutgoing struct {
	NearbyOutgoing
	to      identity.ID
	lan     netip.AddrPort
	ticket  [16]byte      // the invited device's ticket, echoed in the invitation
	owner   string        // whose device the invited one is (empty: this device's person's)
	opts    NearbyOptions // a request to make a mesh: how this device makes it, if it is the one to
	expires time.Time
	ended   time.Time
}

// ErrNoSuchInvite: there is no such invitation (it may have expired, or been taken back).
var ErrNoSuchInvite = errors.New("mesh: no such invitation (it may have expired)")

// ErrNoAdminOnline: this device is a member of its mesh, not an administrator, and no administrator is online to add a
// device.
var ErrNoAdminOnline = errors.New("mesh: an administrator of the mesh must be online to add a device")

func firstText(texts ...string) string {
	for _, s := range texts {
		if s = strings.TrimSpace(s); s != "" {
			return s
		}
	}
	return ""
}

// ---- what the interface shows ----

// NearbyInvites lists the invitations to this device (in a mesh: the devices that ask to make one with it).
func (n *Node) NearbyInvites() []NearbyInvite {
	n.nearbyExpire()
	configured := n.Configured()
	out := []NearbyInvite{}
	n.nearby.mu.Lock()
	for _, v := range n.nearby.incoming {
		if !configured || v.Kind == nearbyPairKind {
			out = append(out, v.NearbyInvite)
		}
	}
	n.nearby.mu.Unlock()
	sort.Slice(out, func(i, j int) bool {
		if out[i].Received != out[j].Received {
			return out[i].Received < out[j].Received
		}
		return out[i].ID < out[j].ID
	})
	return out
}

// NearbySent lists the invitations this device sent, and for a little while how they ended.
func (n *Node) NearbySent() []NearbyOutgoing {
	n.nearbyExpire()
	out := []NearbyOutgoing{}
	n.nearby.mu.Lock()
	for _, o := range n.nearby.outgoing {
		out = append(out, o.NearbyOutgoing)
	}
	n.nearby.mu.Unlock()
	sort.Slice(out, func(i, j int) bool {
		if out[i].Created != out[j].Created {
			return out[i].Created < out[j].Created
		}
		return out[i].ID < out[j].ID
	})
	return out
}

// ---- what the person does ----

// ConnectNearby connects this device with the device nearby id in the way that fits the two: accepts what that device
// asked of this one; asks it to add this one (it can add devices to its mesh); invites it (this device is in a mesh, that
// one is not); or asks it to make a mesh together (neither is in one).
func (n *Node) ConnectNearby(id string, o NearbyOptions) error {
	devID, err := identity.ParseID(id)
	if err != nil {
		return ErrNearbyGone
	}
	n.nearbyExpire()
	configured := n.Configured()
	n.nearby.mu.Lock()
	in := n.nearby.incoming[devID]
	asked := in != nil && (!configured || in.Kind == nearbyPairKind)
	n.nearby.mu.Unlock()
	if asked {
		return n.acceptNearby(devID, o, false)
	}
	e, ok := n.nearbyEntryOf(id)
	if !ok {
		return ErrNearbyGone
	}
	switch {
	case !configured && e.Kind == NearbyKindMesh:
		return n.startNearbyJoin(&e, o.DeviceName, true)
	case configured && e.Kind == NearbyKindNew:
		return n.inviteNearby(e.devID, e.Name, e.OS, e.lan, e.ticket, nearbyInviteKind, o.PeerOwner, NearbyOptions{})
	case !configured && e.Kind == NearbyKindNew:
		return n.inviteNearby(e.devID, e.Name, e.OS, e.lan, e.ticket, nearbyPairKind, "", o)
	}
	return ErrNearbyGone
}

// AcceptNearby accepts the invitation of the device id: joins its mesh; or, when it asks to make a mesh together, makes
// one (named o.MeshName, this device called o.DeviceName) and invites it — or, being in a mesh already, invites it there.
func (n *Node) AcceptNearby(id string, o NearbyOptions) error {
	devID, err := identity.ParseID(id)
	if err != nil {
		return ErrNoSuchInvite
	}
	return n.acceptNearby(devID, o, false)
}

func (n *Node) acceptNearby(from identity.ID, o NearbyOptions, auto bool) error {
	n.nearby.mu.Lock()
	in := n.nearby.incoming[from]
	if in == nil {
		n.nearby.mu.Unlock()
		return ErrNoSuchInvite
	}
	inv := *in
	// What this device asked of that one (if anything) is answered now: once. Accepted without asking the person
	// (auto) only because this device asked for it itself.
	mine := n.nearby.outgoing[from]
	if mine == nil || mine.State != "sent" {
		if auto {
			n.nearby.mu.Unlock()
			return nil
		}
		mine = nil
	} else {
		mine.State = "joining"
	}
	chosen := n.nearby.lookName
	n.nearby.mu.Unlock()

	var err error
	switch configured := n.Configured(); {
	case inv.Kind == nearbyInviteKind && !configured:
		e := &nearbyEntry{
			NearbyDevice: NearbyDevice{ID: inv.ID, Kind: NearbyKindMesh, Name: inv.Name, MeshName: inv.MeshName, OS: inv.OS, Seen: inv.heard.Unix()},
			devID:        from, lan: inv.lan, addr: inv.addr, ticket: inv.ticket, heard: inv.heard,
		}
		if err = n.startNearbyJoin(e, o.DeviceName, true); err == nil {
			n.nearby.mu.Lock()
			delete(n.nearby.incoming, from)
			n.nearby.mu.Unlock()
		}
	case inv.Kind == nearbyPairKind && !configured:
		if n.nearbyJoinRunning() {
			err = errors.New("mesh: a request is already being made")
			break
		}
		if mine != nil && bytes.Compare(n.device().ID[:], from[:]) > 0 {
			// Both asked each other: the device with the smaller key makes the mesh (nearbyInvited), and this one takes
			// its invitation by itself — it still asks for that.
			n.nearby.mu.Lock()
			mine.State = "sent"
			n.nearby.mu.Unlock()
			return nil
		}
		if err = n.CreateMesh(o.MeshName, firstText(o.DeviceName, chosen), o.Owner); err != nil {
			break
		}
		n.log.Info("made a mesh with a device nearby", "mesh", o.MeshName, "with", inv.Name)
		err = n.inviteNearby(from, inv.Name, inv.OS, inv.lan, inv.ticket, nearbyInviteKind, o.PeerOwner, NearbyOptions{})
	case inv.Kind == nearbyPairKind && configured:
		err = n.inviteNearby(from, inv.Name, inv.OS, inv.lan, inv.ticket, nearbyInviteKind, o.PeerOwner, NearbyOptions{})
	default:
		err = ErrNoSuchInvite
	}
	if err != nil && mine != nil {
		n.nearbyOutgoingEnd(mine, "failed", err)
	}
	n.emit(Event{Kind: EvNearby})
	return err
}

func (n *Node) nearbyJoinRunning() bool {
	n.nearby.mu.Lock()
	defer n.nearby.mu.Unlock()
	return n.nearby.join != nil && n.nearby.join.running()
}

// DeclineNearby says no to the invitation of the device id, and tells that device.
func (n *Node) DeclineNearby(id string) error {
	devID, err := identity.ParseID(id)
	if err != nil {
		return ErrNoSuchInvite
	}
	n.nearby.mu.Lock()
	in := n.nearby.incoming[devID]
	delete(n.nearby.incoming, devID)
	n.nearby.mu.Unlock()
	if in == nil {
		return ErrNoSuchInvite
	}
	n.nearbySendDecline(devID, in.lan, in.ticket)
	n.emit(Event{Kind: EvNearby})
	return nil
}

// CancelNearby takes back the invitation this device sent to the device id (and tells that device), or forgets how it
// ended.
func (n *Node) CancelNearby(id string) error {
	devID, err := identity.ParseID(id)
	if err != nil {
		return ErrNoSuchInvite
	}
	n.nearby.mu.Lock()
	o := n.nearby.outgoing[devID]
	delete(n.nearby.outgoing, devID)
	n.nearby.mu.Unlock()
	if o == nil {
		return ErrNoSuchInvite
	}
	if o.State == "sent" {
		n.nearbySendDecline(devID, o.lan, o.ticket)
	}
	n.updateAnonymous()
	n.emit(Event{Kind: EvNearby})
	return nil
}

// inviteNearby sends an invitation of the kind (nearbyInviteKind: to this device's mesh; nearbyPairKind: to make one) to
// the device to, heard at lan with the ticket.
func (n *Node) inviteNearby(to identity.ID, name, os string, lan netip.AddrPort, ticket [16]byte, kind, owner string, o NearbyOptions) error {
	configured := n.Configured()
	switch {
	case kind == nearbyInviteKind && !configured:
		return ErrNotConfigured
	case kind == nearbyInviteKind && !n.nearbyCanAdd():
		return ErrNoAdminOnline
	case kind == nearbyPairKind && configured:
		return errors.New("mesh: already part of a mesh")
	}
	now := time.Now()
	owner = identity.SanitizeOwner(owner)
	n.nearby.mu.Lock()
	if kind == nearbyPairKind && n.nearby.join != nil && n.nearby.join.running() {
		n.nearby.mu.Unlock()
		return errors.New("mesh: a request is already being made")
	}
	if old := n.nearby.outgoing[to]; old != nil && old.Kind == kind && (old.State == "sent" || old.State == "joining") {
		n.nearby.mu.Unlock()
		n.nearbySendPending(to) // tapped again: say it again
		return nil
	}
	if n.nearby.outgoing == nil {
		n.nearby.outgoing = map[identity.ID]*nearbyOutgoing{}
	}
	out := &nearbyOutgoing{
		NearbyOutgoing: NearbyOutgoing{ID: to.String(), Kind: kind, Name: name, OS: os, State: "sent", Created: now.Unix(), Expires: now.Add(nearbyLife()).Unix()},
		to:             to, lan: lan, ticket: ticket, owner: owner, opts: o, expires: now.Add(nearbyLife()),
	}
	n.nearby.outgoing[to] = out
	delete(n.nearby.incoming, to) // whatever it asked of this device, this answers it
	var waiting *nearbyRequest
	if kind == nearbyInviteKind {
		for _, r := range n.nearby.requests {
			if r.joiner == to && r.invite == nil {
				waiting = r // it asks to be added already: the person here says yes to that
			}
		}
		if waiting != nil {
			waiting.invite = out
			out.State = "joining"
		}
	}
	n.nearby.mu.Unlock()
	if waiting != nil {
		select {
		case waiting.decision <- nearbyDecision{approve: true, owner: owner}:
		default:
		}
	}
	n.updateAnonymous()
	n.nearbySendPending(to)
	n.emit(Event{Kind: EvNearby})
	return nil
}

// nearbyOutgoingEnd notes how an invitation ended.
func (n *Node) nearbyOutgoingEnd(o *nearbyOutgoing, state string, err error) {
	n.nearby.mu.Lock()
	o.State, o.ended = state, time.Now()
	if err != nil {
		o.Error = err.Error()
	}
	n.nearby.mu.Unlock()
	n.updateAnonymous()
	n.emit(Event{Kind: EvNearby})
}

// nearbyInvitePending: this device invited a device that may come now.
func (n *Node) nearbyInvitePending() bool {
	n.mu.RLock()
	port := n.udpPort
	n.mu.RUnlock()
	if port <= 0 {
		return false
	}
	now := time.Now()
	pending := false
	n.nearby.mu.Lock()
	for _, o := range n.nearby.outgoing {
		if o.Kind == nearbyInviteKind && o.State == "sent" && now.Before(o.expires) {
			pending = true
		}
	}
	n.nearby.mu.Unlock()
	return pending && n.nearbyCanAdd()
}

// nearbyInMesh: this device got into a mesh — it made one, or joined one (joined: the device that added it). What it heard
// and was offered as a device outside of a mesh is of no use now: the devices that invited it in vain are told no, and
// its requests to make a mesh that still wait become invitations to its mesh.
func (n *Node) nearbyInMesh(joined identity.ID) {
	now := time.Now()
	var tell []nearbyIncoming
	n.nearby.mu.Lock()
	for id, e := range n.nearby.seen {
		if e.Kind == NearbyKindMesh || id == joined {
			delete(n.nearby.seen, id)
		}
	}
	for id, v := range n.nearby.incoming {
		if v.Kind == nearbyInviteKind || id == joined {
			if id != joined {
				tell = append(tell, *v)
			}
			delete(n.nearby.incoming, id)
		}
	}
	for id, o := range n.nearby.outgoing {
		switch {
		case id == joined:
			o.State, o.ended = "joined", now
		case o.Kind == nearbyPairKind && o.State == "sent":
			o.Kind, o.expires = nearbyInviteKind, now.Add(nearbyLife())
			o.Created, o.Expires = now.Unix(), o.expires.Unix()
		}
	}
	n.nearby.mu.Unlock()
	for _, v := range tell {
		n.nearbySendDecline(v.from, v.lan, v.ticket)
	}
	n.updateAnonymous()
	n.emit(Event{Kind: EvNearby})
}

// nearbyLeft: this device left its mesh, and its invitations with it.
func (n *Node) nearbyLeft() {
	n.nearby.mu.Lock()
	n.nearby.join = nil // how an earlier request of this device ended is of no interest to the next one
	n.nearby.outgoing = nil
	n.nearby.incoming = nil
	n.nearby.mu.Unlock()
}

// ---- the packets ----

type nearbyUnicast struct {
	pkt []byte
	to  netip.AddrPort
}

// nearbyPending builds the packets of the invitations that wait for an answer (only: the one to that device; zero: all).
// A device in a mesh sends its invitations while it can add devices; a device outside of one its requests to make one.
func (n *Node) nearbyPending(only identity.ID) []nearbyUnicast {
	n.mu.RLock()
	configured := n.root != nil && n.self != nil
	var name, mesh string
	if configured {
		name, mesh = n.self.Name, n.meshName
	}
	port := n.udpPort
	n.mu.RUnlock()
	if configured && (port <= 0 || port > 65535 || !n.nearbyCanAdd()) {
		return nil
	}
	dev := n.device()
	os, _ := n.platform()
	now := time.Now()
	var invs []nearbyInvitation
	var tos []netip.AddrPort
	n.nearby.mu.Lock()
	cur, _ := n.nearby.tickets(now)
	chosen := n.nearby.lookName
	for _, o := range n.nearby.outgoing {
		if (!only.IsZero() && o.to != only) || o.State != "sent" || !now.Before(o.expires) || !o.lan.IsValid() {
			continue
		}
		v := nearbyInvitation{From: dev.ID, FromTicket: cur, To: o.to, ToTicket: o.ticket, OS: os}
		switch {
		case configured && o.Kind == nearbyInviteKind:
			v.Kind, v.Port, v.Name, v.MeshName = nearbyKindInvite, uint16(port), name, mesh
		case !configured && o.Kind == nearbyPairKind:
			v.Kind, v.Name = nearbyKindPair, firstText(o.opts.DeviceName, chosen)
		default:
			continue
		}
		invs = append(invs, v)
		tos = append(tos, o.lan)
	}
	n.nearby.mu.Unlock()
	out := make([]nearbyUnicast, 0, len(invs))
	for i := range invs {
		if invs[i].Kind == nearbyKindPair {
			invs[i].Name = n.nearbyName(invs[i].Name)
		}
		out = append(out, nearbyUnicast{pkt: invs[i].encode(dev), to: tos[i]})
	}
	return out
}

// nearbySendPending sends the invitations that wait for an answer right now (see nearbyPending).
func (n *Node) nearbySendPending(only identity.ID) {
	for _, u := range n.nearbyPending(only) {
		n.nearbySend(u.pkt, u.to)
	}
}

// nearbySend sends a packet meant for one device on the local network, from the socket of the local discovery (see
// lanDiscovery.sendTo).
func (n *Node) nearbySend(pkt []byte, to netip.AddrPort) {
	n.mu.RLock()
	l := n.lan
	n.mu.RUnlock()
	if l == nil || pkt == nil {
		return
	}
	l.sendTo(pkt, to, magic.LocalNets())
}

func (n *Node) nearbySendDecline(to identity.ID, lan netip.AddrPort, ticket [16]byte) {
	d := nearbyDecline{From: n.device().ID, To: to, ToTicket: ticket}
	n.nearbySend(d.encode(n.device()), lan)
}

// nearbyInvited handles an invitation to this device, or a request to make a mesh with it.
func (n *Node) nearbyInvited(raw []byte, from netip.AddrPort) {
	v, ok := decodeNearbyInvitation(raw)
	if !ok || v.To != n.device().ID || !n.ownTicket(v.ToTicket[:]) {
		return
	}
	configured := n.Configured()
	if (v.Kind == nearbyKindInvite && configured) || n.Peer(v.From) != nil {
		return // this device is in a mesh already (or the other one is in this mesh already)
	}
	kind := kindName(v.Kind)
	lan := netip.AddrPortFrom(from.Addr().Unmap(), from.Port())
	now := time.Now()
	n.nearby.mu.Lock()
	if n.nearby.incoming == nil {
		n.nearby.incoming = map[identity.ID]*nearbyIncoming{}
	}
	in := n.nearby.incoming[v.From]
	if in == nil && len(n.nearby.incoming) >= nearbyMaxListed {
		n.nearby.mu.Unlock()
		return
	}
	changed := in == nil || in.Kind != kind || in.Name != v.Name || in.MeshName != v.MeshName || in.OS != v.OS
	if in == nil || in.Kind != kind {
		in = &nearbyIncoming{from: v.From}
		in.Received = now.Unix()
		n.nearby.incoming[v.From] = in
	}
	in.NearbyInvite = NearbyInvite{ID: v.From.String(), Kind: kind, Name: v.Name, MeshName: v.MeshName, OS: v.OS, Received: in.Received}
	in.lan, in.ticket, in.heard, in.addr = lan, v.FromTicket, now, netip.AddrPort{}
	if v.Kind == nearbyKindInvite {
		in.addr = netip.AddrPortFrom(lan.Addr(), v.Port)
	}
	var asked nearbyOutgoing
	mine := n.nearby.outgoing[v.From]
	if mine != nil {
		asked = *mine
	}
	running := n.nearby.join != nil && n.nearby.join.running()
	n.nearby.mu.Unlock()
	if changed {
		n.emit(Event{Kind: EvNearby})
	}

	// What this device asked for is what the other one offers: the person is not asked again.
	if mine == nil || asked.State != "sent" || running {
		return
	}
	switch {
	case asked.Kind == nearbyPairKind && v.Kind == nearbyKindInvite:
		// it asked that device to make a mesh with it; that one made it and invites it in
		go func() { _ = n.acceptNearby(v.From, asked.opts, true) }()
	case asked.Kind == nearbyPairKind && v.Kind == nearbyKindPair && bytes.Compare(n.device().ID[:], v.From[:]) < 0:
		// both asked each other at once: the device with the smaller key makes the mesh
		go func() { _ = n.acceptNearby(v.From, asked.opts, true) }()
	case asked.Kind == nearbyInviteKind && v.Kind == nearbyKindPair:
		// it asks this device, which invited it already, to make a mesh: the invitation is the answer (and that device
		// accepts it by itself, since it asked) — say it again now
		n.nearbySendPending(v.From)
	}
}

// nearbyDeclined handles a "no" to an invitation of this device, or an invitation to it taken back.
func (n *Node) nearbyDeclined(raw []byte, from netip.AddrPort) {
	d, ok := decodeNearbyDecline(raw)
	if !ok || d.To != n.device().ID || !n.ownTicket(d.ToTicket[:]) {
		return
	}
	changed := false
	n.nearby.mu.Lock()
	if o := n.nearby.outgoing[d.From]; o != nil && o.State == "sent" {
		o.State, o.ended = "declined", time.Now()
		changed = true
	}
	if _, ok := n.nearby.incoming[d.From]; ok {
		delete(n.nearby.incoming, d.From)
		changed = true
	}
	n.nearby.mu.Unlock()
	if changed {
		n.updateAnonymous()
		n.emit(Event{Kind: EvNearby})
	}
}

// ---- a member adds a device: an administrator signs ----

type nearbyAddRequest struct {
	Device identity.ID `json:"device"`
	Name   string      `json:"name"`
	Owner  string      `json:"owner,omitempty"`
}

// nearbyAddsPerMinute is how many devices an administrator adds for members in a minute, at most.
const nearbyAddsPerMinute = 6

// nearbyIssue makes the newcomer a member: an administrator signs its certificate itself; a member asks an administrator
// that is online to.
func (n *Node) nearbyIssue(ctx context.Context, conn *quic.Conn, joiner identity.ID, name, owner string) (*joinResponse, error) {
	if n.IsAdmin() {
		return n.issueMembership(conn, joiner, name, owner, false)
	}
	var admins []*Peer
	for _, p := range n.peerList() {
		if p.Online() && p.Member().Admin {
			admins = append(admins, p)
		}
	}
	if len(admins) == 0 {
		return nil, ErrNoAdminOnline
	}
	root := n.Root()
	if root == nil {
		return nil, ErrNotConfigured
	}
	err := ErrNoAdminOnline
	for _, p := range admins {
		var resp joinResponse
		cctx, cancel := context.WithTimeout(ctx, 20*time.Second)
		cerr := p.Call(cctx, "mesh.nearbyAdd", nearbyAddRequest{Device: joiner, Name: name, Owner: owner}, &resp)
		cancel()
		if cerr != nil {
			err = cerr
			continue
		}
		// What goes to the newcomer is checked here first: the mesh's root, its own certificate (not an administrator's),
		// and no authority key.
		m, verr := root.Verify(resp.Cert)
		if verr != nil || m.ID != joiner || m.Admin || len(resp.AuthSeed) > 0 || !bytes.Equal(resp.RootCert, root.DER) {
			err = errors.New("mesh: the administrator answered with something else than a certificate for that device")
			continue
		}
		n.learnMember(m)
		if mg := n.Magic(); mg != nil {
			if ua, ok := conn.RemoteAddr().(*net.UDPAddr); ok {
				mg.AddCandidates(joiner, []netip.AddrPort{ua.AddrPort()}, magic.SrcObserved)
			}
			mg.Poke(joiner)
		}
		n.log.Info("an administrator added a device nearby for this one", "admin", p.Name(), "name", m.Name)
		return &resp, nil
	}
	return nil, err
}

// nearbyAddAllowed counts a device added for a member; false when there were too many lately.
func (n *Node) nearbyAddAllowed() bool {
	now := time.Now()
	n.nearby.mu.Lock()
	defer n.nearby.mu.Unlock()
	kept := n.nearby.added[:0]
	for _, t := range n.nearby.added {
		if now.Sub(t) < time.Minute {
			kept = append(kept, t)
		}
	}
	n.nearby.added = kept
	if len(kept) >= nearbyAddsPerMinute {
		return false
	}
	n.nearby.added = append(n.nearby.added, now)
	return true
}

func (n *Node) registerNearbyHandlers() {
	n.Handle("mesh.nearbyAdd", func(ctx context.Context, c *Call) (any, error) {
		// A member of the mesh adds a device nearby that its person said yes to, and asks this administrator to sign the
		// device's certificate (the member has no key to do it with). Any member may; a few a minute; this device's person
		// is told.
		var req nearbyAddRequest
		if err := c.Decode(&req); err != nil {
			return nil, err
		}
		if !n.IsAdmin() {
			return nil, ErrNotAdmin
		}
		if req.Device.IsZero() || req.Device == n.device().ID {
			return nil, Errf(CodeInvalid, "no device")
		}
		n.mu.RLock()
		_, revoked := n.revoked[req.Device]
		n.mu.RUnlock()
		if revoked {
			return nil, Errf(CodeDenied, "this device was removed from the mesh")
		}
		if !n.nearbyAddAllowed() {
			return nil, Errf(CodeDenied, "too many devices were added in the last minute; try again a little later")
		}
		owner := identity.SanitizeOwner(req.Owner)
		if owner == "" {
			owner = c.Peer.Member().Owner
		}
		resp, err := n.issueMembership(nil, req.Device, req.Name, owner, false)
		if err != nil {
			return nil, err
		}
		n.log.Info("a member added a device nearby", "member", c.Peer.Name(), "device", req.Name)
		n.emit(Event{Kind: EvAdded, Peer: req.Device, By: c.Peer.ID})
		n.saveSoon()
		go n.pushSyncToAll()
		return resp, nil
	})
}
