package mesh

import (
	"crypto/ed25519"
	"crypto/rand"
	"crypto/subtle"
	"encoding/binary"
	"encoding/hex"
	"errors"
	"net/netip"
	"sort"
	"strings"
	"sync"
	"sync/atomic"
	"time"
	"unicode/utf8"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
)

// Nearby: devices with The Mesh installed find each other on the local network and connect in a tap, without anybody
// typing or scanning anything.
//
// Who makes itself known, and who lists whom:
//   - a device in a mesh that can add devices to it announces so: "Andrey's MacBook, mesh «Home»". An administrator can;
//     a member can while an administrator of its mesh is online to sign the newcomer's certificate (nearbyinvite.go);
//   - a device that is not in a mesh makes itself known while its person looks at the list: "Pixel 8";
//   - a device that is not in a mesh lists both; a device in a mesh lists the devices that are not in one.
//
// A tap on a device in the list connects the two in the way that fits them: a device that is not in a mesh asks one that
// announced a mesh to add it (the person there says yes, nearbyjoin.go); a device in a mesh invites one that is not (the
// person there accepts, and it is added without another question); two devices that are not in a mesh make one together
// (the one whose person accepts makes it and invites the other). This file is the packets, the lists and the looking;
// nearbyinvite.go is the invitations, nearbyjoin.go how a device is added.
//
// The packets are meant to be read by devices outside of the mesh, so unlike a member's beacon (lan.go) they cannot be
// sealed with the mesh's key: they are plain, and signed with the device key they name, so that nobody can alter one or
// make one up for somebody else's key. Each carries its device's ticket, 16 random bytes that change every two minutes;
// whoever turns to a device must show its ticket (in the server name of a TLS handshake, or echoed in an invitation), so
// only a neighbour that heard it lately gets anywhere, not somebody who found its port from afar or kept an old packet.
//
//	announcement  [0x4E][1][device id 32][udp port 2][ticket 16][name][mesh name][platform][signature 64]
//	newcomer      [0x46][1][device id 32][ticket 16][name][platform][signature 64]
//	query         [0x51][1][8 random bytes]
//	invitation    [0x49][1][kind][from 32][its ticket 16][its udp port 2][to 32][to's ticket 16][name][mesh name][platform][signature 64]
//	decline       [0x44][1][from 32][to 32][to's ticket 16][signature 64]
//
// where each text is one length byte and that many bytes of UTF-8. A query ("who is around?") is answered at once, to the
// sender only, by every device that would make itself known, so that a list fills in a moment and a network that drops
// multicast still works. What the packets give away to the whole network is a device's name (and its mesh's name), and
// only while it can add devices (a switch in Settings) or while its person looks at the list; they carry no secret: what
// adds a device is a person saying yes.
const (
	nearbyAnnounceMarker = 0x4E
	nearbyQueryMarker    = 0x51
	nearbyNewcomerMarker = 0x46
	nearbyInviteMarker   = 0x49
	nearbyDeclineMarker  = 0x44
	nearbyFormat         = 1

	nearbySigLabel      = "themesh-nearby-announce/v1\x00"
	nearbyNewcomerLabel = "themesh-nearby-newcomer/v1\x00"
	nearbyInviteLabel   = "themesh-nearby-invite/v1\x00"
	nearbyDeclineLabel  = "themesh-nearby-decline/v1\x00"

	nearbyTextMax   = 40 // bytes of a name in a packet
	nearbyQueryLen  = 10
	nearbyPacketMax = 512 // more than the longest packet (an invitation is under 300 bytes)

	nearbyTicketLife  = 2 * time.Minute  // a ticket is replaced by a new one this often (the previous one still works)
	nearbyListedFor   = 16 * time.Second // a device that has not been heard for this long leaves the list
	nearbyMaxListed   = 32
	nearbyMaxRequests = 3 // requests waiting for an answer at one time (from devices nobody invited)
	nearbyReplyEvery  = 700 * time.Millisecond
	nearbyLookFor     = 90 * time.Second // the interface says that the person looks every half a minute
	nearbyEndShown    = 30 * time.Second // how an invitation ended stays listed this long

	// ALPNNearby is the protocol of a request to be added to a mesh (see nearbyjoin.go).
	ALPNNearby = "themesh-nearby/1"
)

// Kinds of devices nearby.
const (
	NearbyKindMesh = "mesh" // in a mesh, and can add this device to it
	NearbyKindNew  = "new"  // not in a mesh yet
)

// NearbyDevice is a device around that this one can connect with.
type NearbyDevice struct {
	ID       string `json:"id"`
	Kind     string `json:"kind"` // NearbyKindMesh or NearbyKindNew
	Name     string `json:"name"`
	MeshName string `json:"meshName"` // (empty for a device that is not in a mesh)
	OS       string `json:"os"`
	Seen     int64  `json:"seen"` // when it was last heard (unix seconds)
}

type nearbyEntry struct {
	NearbyDevice
	devID  identity.ID
	lan    netip.AddrPort // where its packets come from: its socket for the local network
	addr   netip.AddrPort // a device in a mesh: where it takes a request to be added (its UDP port)
	ticket [16]byte
	heard  time.Time
}

// cutText makes a name safe to show and short enough for a packet: printable, one line, at most max bytes.
func cutText(s string, max int) string {
	s = identity.SanitizeOwner(s)
	for len(s) > max {
		_, size := utf8.DecodeLastRuneInString(s)
		s = s[:len(s)-size]
	}
	return strings.TrimSpace(s)
}

func appendTexts(b []byte, texts ...string) []byte {
	for _, s := range texts {
		s = cutText(s, nearbyTextMax)
		b = append(b, byte(len(s)))
		b = append(b, s...)
	}
	return b
}

// readTexts reads n texts from the start of b; ok is false when they are not all there, too long, or not UTF-8.
func readTexts(b []byte, n int) (texts []string, rest []byte, ok bool) {
	texts = make([]string, n)
	for i := range texts {
		if len(b) < 1 || int(b[0]) > nearbyTextMax || len(b) < 1+int(b[0]) {
			return nil, nil, false
		}
		l := int(b[0])
		if !utf8.Valid(b[1 : 1+l]) {
			return nil, nil, false
		}
		texts[i] = cutText(string(b[1:1+l]), nearbyTextMax)
		b = b[1+l:]
	}
	return texts, b, true
}

// signNearby appends the signature of dev over the label and b.
func signNearby(dev *identity.Device, label string, b []byte) []byte {
	return append(b, ed25519.Sign(dev.Priv, append([]byte(label), b...))...)
}

// openNearby checks the signature at the end of b by the key id, and returns what it covers.
func openNearby(b []byte, label string, id identity.ID) (body []byte, ok bool) {
	if len(b) < ed25519.SignatureSize || id.IsZero() {
		return nil, false
	}
	body, sig := b[:len(b)-ed25519.SignatureSize], b[len(b)-ed25519.SignatureSize:]
	if !ed25519.Verify(id.PublicKey(), append([]byte(label), body...), sig) {
		return nil, false
	}
	return body, true
}

// ---- the packets ----

// nearbyAnnouncement is what a device that can add devices to its mesh says.
type nearbyAnnouncement struct {
	ID       identity.ID
	Port     uint16
	Ticket   [16]byte
	Name     string
	MeshName string
	OS       string
}

func (a *nearbyAnnouncement) encode(dev *identity.Device) []byte {
	b := make([]byte, 0, 2+32+2+16+3+3*nearbyTextMax+ed25519.SignatureSize)
	b = append(b, nearbyAnnounceMarker, nearbyFormat)
	b = append(b, a.ID[:]...)
	b = binary.BigEndian.AppendUint16(b, a.Port)
	b = append(b, a.Ticket[:]...)
	b = appendTexts(b, a.Name, a.MeshName, a.OS)
	return signNearby(dev, nearbySigLabel, b)
}

// decodeNearbyAnnouncement checks the structure and the signature of an announcement; ok is false for anything else.
func decodeNearbyAnnouncement(b []byte) (a nearbyAnnouncement, ok bool) {
	const fixed = 2 + 32 + 2 + 16
	if len(b) < fixed+3+ed25519.SignatureSize || len(b) > fixed+3+3*nearbyTextMax+ed25519.SignatureSize || b[0] != nearbyAnnounceMarker || b[1] != nearbyFormat {
		return a, false
	}
	copy(a.ID[:], b[2:34])
	body, ok := openNearby(b, nearbySigLabel, a.ID)
	if !ok {
		return nearbyAnnouncement{}, false
	}
	a.Port = binary.BigEndian.Uint16(b[34:36])
	copy(a.Ticket[:], b[36:52])
	texts, rest, ok := readTexts(body[fixed:], 3)
	if !ok || len(rest) != 0 || a.Port == 0 || texts[0] == "" {
		return nearbyAnnouncement{}, false
	}
	a.Name, a.MeshName, a.OS = texts[0], texts[1], strings.ToLower(texts[2])
	return a, true
}

// nearbyNewcomer is what a device that is not in a mesh says while its person looks around.
type nearbyNewcomer struct {
	ID     identity.ID
	Ticket [16]byte
	Name   string
	OS     string
}

func (a *nearbyNewcomer) encode(dev *identity.Device) []byte {
	b := make([]byte, 0, 2+32+16+2+2*nearbyTextMax+ed25519.SignatureSize)
	b = append(b, nearbyNewcomerMarker, nearbyFormat)
	b = append(b, a.ID[:]...)
	b = append(b, a.Ticket[:]...)
	b = appendTexts(b, a.Name, a.OS)
	return signNearby(dev, nearbyNewcomerLabel, b)
}

func decodeNearbyNewcomer(b []byte) (a nearbyNewcomer, ok bool) {
	const fixed = 2 + 32 + 16
	if len(b) < fixed+2+ed25519.SignatureSize || len(b) > fixed+2+2*nearbyTextMax+ed25519.SignatureSize || b[0] != nearbyNewcomerMarker || b[1] != nearbyFormat {
		return a, false
	}
	copy(a.ID[:], b[2:34])
	body, ok := openNearby(b, nearbyNewcomerLabel, a.ID)
	if !ok {
		return nearbyNewcomer{}, false
	}
	copy(a.Ticket[:], b[34:50])
	texts, rest, ok := readTexts(body[fixed:], 2)
	if !ok || len(rest) != 0 || texts[0] == "" {
		return nearbyNewcomer{}, false
	}
	a.Name, a.OS = texts[0], strings.ToLower(texts[1])
	return a, true
}

// The kinds of an invitation.
const (
	nearbyKindInvite = 1 // "join my mesh": the invited device connects to the inviting one (its UDP port, its ticket)
	nearbyKindPair   = 2 // "neither of us is in a mesh: let us make one" (the invited device makes it, and invites back)
)

// nearbyInvitation is sent to one device: an invitation, or a request to make a mesh together.
type nearbyInvitation struct {
	Kind       byte
	From       identity.ID
	FromTicket [16]byte
	Port       uint16 // an invitation to a mesh: the UDP port of the inviting device; 0 otherwise
	To         identity.ID
	ToTicket   [16]byte // from the invited device's own packet: the invitation is for a neighbour that was heard lately
	Name       string
	MeshName   string
	OS         string
}

func (v *nearbyInvitation) encode(dev *identity.Device) []byte {
	b := make([]byte, 0, 3+32+16+2+32+16+3+3*nearbyTextMax+ed25519.SignatureSize)
	b = append(b, nearbyInviteMarker, nearbyFormat, v.Kind)
	b = append(b, v.From[:]...)
	b = append(b, v.FromTicket[:]...)
	b = binary.BigEndian.AppendUint16(b, v.Port)
	b = append(b, v.To[:]...)
	b = append(b, v.ToTicket[:]...)
	b = appendTexts(b, v.Name, v.MeshName, v.OS)
	return signNearby(dev, nearbyInviteLabel, b)
}

func decodeNearbyInvitation(b []byte) (v nearbyInvitation, ok bool) {
	const fixed = 3 + 32 + 16 + 2 + 32 + 16
	if len(b) < fixed+3+ed25519.SignatureSize || len(b) > fixed+3+3*nearbyTextMax+ed25519.SignatureSize || b[0] != nearbyInviteMarker || b[1] != nearbyFormat {
		return v, false
	}
	v.Kind = b[2]
	copy(v.From[:], b[3:35])
	body, ok := openNearby(b, nearbyInviteLabel, v.From)
	if !ok {
		return nearbyInvitation{}, false
	}
	copy(v.FromTicket[:], b[35:51])
	v.Port = binary.BigEndian.Uint16(b[51:53])
	copy(v.To[:], b[53:85])
	copy(v.ToTicket[:], b[85:101])
	texts, rest, ok := readTexts(body[fixed:], 3)
	if !ok || len(rest) != 0 || texts[0] == "" || v.To.IsZero() || v.To == v.From {
		return nearbyInvitation{}, false
	}
	v.Name, v.MeshName, v.OS = texts[0], texts[1], strings.ToLower(texts[2])
	switch {
	case v.Kind == nearbyKindInvite && v.Port != 0:
	case v.Kind == nearbyKindPair && v.Port == 0 && v.MeshName == "":
	default:
		return nearbyInvitation{}, false
	}
	return v, true
}

// nearbyDecline says no to an invitation, or takes back one (or a request to make a mesh): "forget what is between us".
type nearbyDecline struct {
	From, To identity.ID
	ToTicket [16]byte
}

func (d *nearbyDecline) encode(dev *identity.Device) []byte {
	b := make([]byte, 0, 2+32+32+16+ed25519.SignatureSize)
	b = append(b, nearbyDeclineMarker, nearbyFormat)
	b = append(b, d.From[:]...)
	b = append(b, d.To[:]...)
	b = append(b, d.ToTicket[:]...)
	return signNearby(dev, nearbyDeclineLabel, b)
}

func decodeNearbyDecline(b []byte) (d nearbyDecline, ok bool) {
	if len(b) != 2+32+32+16+ed25519.SignatureSize || b[0] != nearbyDeclineMarker || b[1] != nearbyFormat {
		return d, false
	}
	copy(d.From[:], b[2:34])
	if _, ok := openNearby(b, nearbyDeclineLabel, d.From); !ok {
		return nearbyDecline{}, false
	}
	copy(d.To[:], b[34:66])
	copy(d.ToTicket[:], b[66:82])
	if d.To.IsZero() || d.To == d.From {
		return nearbyDecline{}, false
	}
	return d, true
}

func nearbyQueryPacket() []byte {
	b := make([]byte, nearbyQueryLen)
	b[0], b[1] = nearbyQueryMarker, nearbyFormat
	_, _ = rand.Read(b[2:])
	return b
}

// ---- the state ----

// nearbyLifeNanos is how long a request or an invitation waits for the people (adjustable so that tests need not wait
// for minutes).
var nearbyLifeNanos atomic.Int64

func init() { nearbyLifeNanos.Store(int64(2 * time.Minute)) }

func nearbyLife() time.Duration { return time.Duration(nearbyLifeNanos.Load()) }

// nearbyState is everything the node knows about the devices around it.
type nearbyState struct {
	mu        sync.Mutex
	seen      map[identity.ID]*nearbyEntry
	cur, prev [16]byte // tickets of this device
	rotated   time.Time
	requests  map[string]*nearbyRequest // asks that wait for an answer (this device adds)
	join      *nearbyJoin               // the attempt this device is making to be added
	lastReply map[netip.Addr]time.Time
	outgoing  map[identity.ID]*nearbyOutgoing // invitations this device sent (nearbyinvite.go)
	incoming  map[identity.ID]*nearbyIncoming // invitations to this device
	lookUntil time.Time                       // the person looks at the list until then (SetNearbyLooking)
	lookName  string                          // the name this device gives while it is not in a mesh
	added     []time.Time                     // devices an administrator added for members lately (nearbyinvite.go)
	// when an invitation of that device ended: declined here, or taken back there (nearbyinvite.go). What still comes from
	// it for a moment was on its way before (each invitation goes three ways, and the copies do not arrive together).
	ended map[identity.ID]time.Time
}

// tickets returns the current and the previous ticket, replacing them when it is time.
func (s *nearbyState) tickets(now time.Time) (cur, prev [16]byte) {
	if s.rotated.IsZero() {
		_, _ = rand.Read(s.cur[:])
		s.prev = s.cur
		s.rotated = now
	} else if now.Sub(s.rotated) >= nearbyTicketLife {
		s.prev = s.cur
		_, _ = rand.Read(s.cur[:])
		s.rotated = now
	}
	return s.cur, s.prev
}

// ownTicket reports whether t is a ticket of this device now (the previous one still counts).
func (n *Node) ownTicket(t []byte) bool {
	if len(t) != 16 {
		return false
	}
	n.nearby.mu.Lock()
	cur, prev := n.nearby.tickets(time.Now())
	n.nearby.mu.Unlock()
	return subtle.ConstantTimeCompare(t, cur[:]) == 1 || subtle.ConstantTimeCompare(t, prev[:]) == 1
}

// ---- what this device says ----

// nearbyCanAdd reports whether this device can add a device to its mesh now: an administrator can; a member can while an
// administrator of the mesh is online to sign the newcomer's certificate.
func (n *Node) nearbyCanAdd() bool {
	n.mu.RLock()
	in, admin := n.root != nil && n.self != nil, n.auth != nil
	peers := make([]*Peer, 0, len(n.peers))
	for _, p := range n.peers {
		peers = append(peers, p)
	}
	n.mu.RUnlock()
	if !in || admin {
		return in
	}
	for _, p := range peers {
		if p.Member().Admin && p.Online() {
			return true
		}
	}
	return false
}

// NearbyCanAdd reports whether this device can add a device nearby to its mesh right now (see nearbyCanAdd).
func (n *Node) NearbyCanAdd() bool { return n.nearbyCanAdd() }

// nearbyReady says whether this device should announce that it can add devices, and what it would say: it must be able
// to (nearbyCanAdd), the person must not have switched it off (a member: must be looking at the list), and it must be
// able to take another request.
func (n *Node) nearbyReady() (name, mesh string, port int, ok bool) {
	n.mu.RLock()
	in, admin := n.root != nil && n.self != nil, n.auth != nil
	if in {
		name, mesh = n.self.Name, n.meshName
	}
	port = n.udpPort
	off := n.cfg.NoNearby
	n.mu.RUnlock()
	if !in || off || port <= 0 || port > 65535 {
		return "", "", 0, false
	}
	// An administrator (often a computer nobody sits at) announces all along, so that a newcomer finds it on its own; a
	// member — mostly a phone, carried from one network to another — only while its person looks at the list.
	if !admin && !n.NearbyLooking() || !n.nearbyCanAdd() {
		return "", "", 0, false
	}
	n.nearby.mu.Lock()
	full := n.nearby.waitingLocked() >= nearbyMaxRequests
	n.nearby.mu.Unlock()
	return name, mesh, port, !full
}

// nearbyAdvert is the announcement of this device, or nil when it should not announce (see nearbyReady).
func (n *Node) nearbyAdvert() []byte {
	name, mesh, port, ok := n.nearbyReady()
	if !ok {
		return nil
	}
	n.nearby.mu.Lock()
	cur, _ := n.nearby.tickets(time.Now())
	n.nearby.mu.Unlock()
	os, _ := n.platform()
	a := nearbyAnnouncement{ID: n.device().ID, Port: uint16(port), Ticket: cur, Name: name, MeshName: mesh, OS: os}
	return a.encode(n.device())
}

// nearbyOpen: is this device willing to take a request from a device nearby right now — it announces that it can add
// devices, or it invited one that may come.
func (n *Node) nearbyOpen() bool {
	if _, _, _, ok := n.nearbyReady(); ok {
		return true
	}
	return n.nearbyInvitePending()
}

// nearbyName is the name this device gives nearby while it is not in a mesh: the one its person chose, or its usual one.
func (n *Node) nearbyName(chosen string) string {
	n.mu.RLock()
	usual := n.cfg.DeviceName
	n.mu.RUnlock()
	for _, s := range []string{chosen, usual} {
		if s = cutText(s, nearbyTextMax); s != "" {
			return s
		}
	}
	return "device"
}

// nearbyNewcomerAdvert is the packet of this device as one that is not in a mesh, or nil when it keeps quiet: it is in a
// mesh, its person does not look at the list, or they switched being seen off.
func (n *Node) nearbyNewcomerAdvert() []byte {
	n.mu.RLock()
	quiet := n.root != nil || n.cfg.NoNearby
	n.mu.RUnlock()
	if quiet {
		return nil
	}
	now := time.Now()
	n.nearby.mu.Lock()
	looking := now.Before(n.nearby.lookUntil)
	chosen := n.nearby.lookName
	cur, _ := n.nearby.tickets(now)
	n.nearby.mu.Unlock()
	if !looking {
		return nil
	}
	os, _ := n.platform()
	a := nearbyNewcomer{ID: n.device().ID, Ticket: cur, Name: n.nearbyName(chosen), OS: os}
	return a.encode(n.device())
}

// SetNearbyLooking says that the person looks at the list of devices nearby (on), or no longer does. The interface says
// so every half a minute while it shows the list; that counts for a minute and a half. While the person looks — and only
// then — a device that is not in a mesh makes itself known on the local network, under name (empty: the name given last
// time, or its usual one), so that the devices around can offer to connect with it; and a device in a mesh asks who is
// around, so that its list fills at once.
func (n *Node) SetNearbyLooking(on bool, name string) {
	name = cutText(name, nearbyTextMax)
	n.nearby.mu.Lock()
	was := time.Now().Before(n.nearby.lookUntil)
	if on {
		n.nearby.lookUntil = time.Now().Add(nearbyLookFor)
		if name != "" {
			n.nearby.lookName = name
		}
	} else {
		n.nearby.lookUntil = time.Time{}
	}
	n.nearby.mu.Unlock()
	if on && !was {
		n.lanSoon()
	}
}

// NearbyLooking reports whether the person looks at the list now (see SetNearbyLooking).
func (n *Node) NearbyLooking() bool {
	n.nearby.mu.Lock()
	defer n.nearby.mu.Unlock()
	return time.Now().Before(n.nearby.lookUntil)
}

func nearbySNI(ticket [16]byte) string { return hex.EncodeToString(ticket[:]) + "." + nearbySNISuffix }

const nearbySNISuffix = "nearby.mesh"

// checkNearbySNI reports whether sni names a current ticket of this device, i.e. whether whoever is asking has heard an
// announcement or an invitation of ours a moment ago.
func (n *Node) checkNearbySNI(sni string) bool {
	host, ok := strings.CutSuffix(strings.TrimSuffix(sni, "."), "."+nearbySNISuffix)
	if !ok {
		return false
	}
	got, err := hex.DecodeString(host)
	if err != nil {
		return false
	}
	return n.ownTicket(got)
}

// SetNearbyVisible switches on or off what this device says about itself nearby: that it can add devices (in a mesh),
// or its name while its person looks at the list (not in a mesh). It still invites, and can be invited by, the devices
// its person picks.
func (n *Node) SetNearbyVisible(on bool) {
	n.mu.Lock()
	n.cfg.NoNearby = !on
	n.mu.Unlock()
	n.updateAnonymous()
	n.emit(Event{Kind: EvNearby})
}

// NearbyVisible reports whether this device makes itself known nearby (see SetNearbyVisible).
func (n *Node) NearbyVisible() bool {
	n.mu.RLock()
	defer n.mu.RUnlock()
	return !n.cfg.NoNearby
}

// ---- what this device hears ----

// nearbyHeard notes a device heard on the local network.
func (n *Node) nearbyHeard(id identity.ID, kind, name, mesh, os string, lan, addr netip.AddrPort, ticket [16]byte) {
	now := time.Now()
	n.nearby.mu.Lock()
	if n.nearby.seen == nil {
		n.nearby.seen = map[identity.ID]*nearbyEntry{}
	}
	e := n.nearby.seen[id]
	changed := e == nil || e.Kind != kind || e.Name != name || e.MeshName != mesh || e.OS != os || e.addr != addr
	if e == nil {
		if len(n.nearby.seen) >= nearbyMaxListed {
			n.nearby.mu.Unlock()
			return
		}
		e = &nearbyEntry{devID: id}
		n.nearby.seen[id] = e
	}
	e.NearbyDevice = NearbyDevice{ID: id.String(), Kind: kind, Name: name, MeshName: mesh, OS: os, Seen: now.Unix()}
	e.lan, e.addr, e.ticket, e.heard = lan, addr, ticket, now
	if o := n.nearby.outgoing[id]; o != nil && kind == NearbyKindNew {
		o.lan, o.ticket = lan, ticket // an invitation that waits follows the device's latest packet
	}
	n.nearby.mu.Unlock()
	if changed {
		n.emit(Event{Kind: EvNearby})
	}
}

// nearbyAnnounced notes the announcement of a device that can add devices (kept only by a device that is not in a mesh).
func (n *Node) nearbyAnnounced(raw []byte, from netip.AddrPort) {
	if n.Configured() {
		return
	}
	a, ok := decodeNearbyAnnouncement(raw)
	if !ok || a.ID == n.device().ID {
		return
	}
	lan := netip.AddrPortFrom(from.Addr().Unmap(), from.Port())
	n.nearbyHeard(a.ID, NearbyKindMesh, a.Name, a.MeshName, a.OS, lan, netip.AddrPortFrom(lan.Addr(), a.Port), a.Ticket)
}

// nearbyNewcomerHeard notes a device that is not in a mesh.
func (n *Node) nearbyNewcomerHeard(raw []byte, from netip.AddrPort) {
	a, ok := decodeNearbyNewcomer(raw)
	if !ok || a.ID == n.device().ID || n.Peer(a.ID) != nil { // (a member of this mesh is no newcomer to it: an old packet)
		return
	}
	lan := netip.AddrPortFrom(from.Addr().Unmap(), from.Port())
	n.nearbyHeard(a.ID, NearbyKindNew, a.Name, "", a.OS, lan, netip.AddrPort{}, a.Ticket)
}

// nearbyShouldReply: should this device answer a query from addr now (once per short while per address).
func (n *Node) nearbyShouldReply(addr netip.Addr) bool {
	addr = addr.Unmap()
	now := time.Now()
	n.nearby.mu.Lock()
	defer n.nearby.mu.Unlock()
	if n.nearby.lastReply == nil {
		n.nearby.lastReply = map[netip.Addr]time.Time{}
	}
	if t, ok := n.nearby.lastReply[addr]; ok && now.Sub(t) < nearbyReplyEvery {
		return false
	}
	if len(n.nearby.lastReply) >= 256 {
		for a, t := range n.nearby.lastReply {
			if now.Sub(t) > time.Minute {
				delete(n.nearby.lastReply, a)
			}
		}
		if len(n.nearby.lastReply) >= 256 {
			return false
		}
	}
	n.nearby.lastReply[addr] = now
	return true
}

// nearbyExpire drops what has gone stale: devices and invitations not heard for a while, invitations nobody answered.
func (n *Node) nearbyExpire() {
	now := time.Now()
	changed := false
	n.nearby.mu.Lock()
	for id, e := range n.nearby.seen {
		if now.Sub(e.heard) > nearbyListedFor {
			delete(n.nearby.seen, id)
			changed = true
		}
	}
	for id, v := range n.nearby.incoming {
		if now.Sub(v.heard) > nearbyListedFor {
			delete(n.nearby.incoming, id)
			changed = true
		}
	}
	for id, t := range n.nearby.ended {
		if now.Sub(t) >= nearbyEndQuiet {
			delete(n.nearby.ended, id)
		}
	}
	for id, o := range n.nearby.outgoing {
		switch {
		case o.State == "sent" && !now.Before(o.expires):
			o.State, o.ended = "expired", now
			changed = true
		case !o.ended.IsZero() && now.Sub(o.ended) > nearbyEndShown:
			delete(n.nearby.outgoing, id)
			changed = true
		}
	}
	n.nearby.mu.Unlock()
	if changed {
		n.emit(Event{Kind: EvNearby})
	}
}

// NearbyList lists the devices around that this one can connect with: to a device that is not in a mesh, the ones that
// can add it and the ones that are not in a mesh either; to a device in a mesh, the ones that are not in one.
func (n *Node) NearbyList() []NearbyDevice {
	n.nearbyExpire()
	configured := n.Configured()
	var heard []NearbyDevice
	n.nearby.mu.Lock()
	for _, e := range n.nearby.seen {
		if !configured || e.Kind == NearbyKindNew {
			heard = append(heard, e.NearbyDevice)
		}
	}
	n.nearby.mu.Unlock()
	out := make([]NearbyDevice, 0, len(heard))
	for _, d := range heard {
		if id, err := identity.ParseID(d.ID); err == nil && configured && n.Peer(id) != nil {
			continue // it joined this mesh a moment ago
		}
		out = append(out, d)
	}
	sort.Slice(out, func(i, j int) bool {
		if out[i].Name != out[j].Name {
			return out[i].Name < out[j].Name
		}
		return out[i].ID < out[j].ID
	})
	return out
}

// nearbyEntryOf is what is known about the device id, if it was heard lately.
func (n *Node) nearbyEntryOf(id string) (nearbyEntry, bool) {
	n.nearby.mu.Lock()
	defer n.nearby.mu.Unlock()
	for _, e := range n.nearby.seen {
		if e.ID == id && time.Since(e.heard) <= nearbyListedFor {
			return *e, true
		}
	}
	return nearbyEntry{}, false
}

// ErrNearbyGone: the device asked for is no longer on the list (it left the network, or stopped making itself known).
var ErrNearbyGone = errors.New("mesh: that device is no longer nearby")

// ErrNoSuchRequest: there is no such request waiting for an answer (it may have expired).
var ErrNoSuchRequest = errors.New("mesh: no such request (it may have expired)")
