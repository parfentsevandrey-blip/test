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

// Nearby: devices with The Mesh installed find each other on the local network without anybody typing or scanning
// anything. An admin device of a mesh announces on the network that it is ready to add a device ("Andrey's MacBook,
// mesh «Home»"); a device that is not in any mesh yet lists the announcements it hears, and a tap on one asks that
// device to add it. The asking and the answering are described in nearbyjoin.go; this file is the announcements, the
// list of what was heard, and the requests waiting for an answer.
//
// An announcement is meant to be read by a device that is not in the mesh yet, so unlike a member's beacon (lan.go) it
// cannot be sealed with the mesh's key: it is plain, and signed with the device key it names, so that it cannot be altered
// or made up for somebody else's key. What it gives away to the whole network is the device's name and the mesh's name,
// for as long as the device is willing to add devices (a switch in Settings). It carries no secret: what lets a device
// join is the answer of the person at the inviting device, who compares a code that both screens show (nearbyjoin.go).
//
//	[0x4E][format 1][device id 32][udp port 2][ticket 16][name][mesh name][platform][signature 64]
//
// where each of the three texts is one length byte and that many bytes of UTF-8. The ticket changes every few minutes
// and goes in the server name of the TLS handshake: only a device that has heard an announcement lately (a neighbour on
// this network) gets any answer from the inviting device at all, not somebody who found its UDP port from afar.
//
// A device that is not in a mesh asks "who can add me?" with a ten byte query ([0x51][format][8 random bytes]); an
// announcing device answers it straight away, to the sender only, so that the list fills in a moment and a network that
// drops multicast still works.
const (
	nearbyAnnounceMarker = 0x4E
	nearbyQueryMarker    = 0x51
	nearbyFormat         = 1
	nearbySigLabel       = "themesh-nearby-announce/v1\x00"
	nearbyTextMax        = 40 // bytes of a name in an announcement
	nearbyQueryLen       = 10

	nearbyTicketLife  = 2 * time.Minute  // a ticket is replaced by a new one this often (the previous one still works)
	nearbyListedFor   = 16 * time.Second // a device that has not been heard for this long leaves the list
	nearbyMaxListed   = 32
	nearbyMaxRequests = 3 // requests waiting for an answer at one time
	nearbyReplyEvery  = 700 * time.Millisecond

	// ALPNNearby is the protocol of a request to be added to a mesh (see nearbyjoin.go).
	ALPNNearby = "themesh-nearby/1"
)

// NearbyDevice is a device that is ready to add this one to its mesh.
type NearbyDevice struct {
	ID       string `json:"id"`
	Name     string `json:"name"`
	MeshName string `json:"meshName"`
	OS       string `json:"os"`
	Seen     int64  `json:"seen"` // when it was last heard (unix seconds)
}

type nearbyEntry struct {
	NearbyDevice
	devID  identity.ID
	addr   netip.AddrPort
	ticket [16]byte
	heard  time.Time
}

// nearbyAnnouncement is what an announcement says.
type nearbyAnnouncement struct {
	ID       identity.ID
	Port     uint16
	Ticket   [16]byte
	Name     string
	MeshName string
	OS       string
}

// cutText makes a name safe to show and short enough for an announcement: printable, one line, at most max bytes.
func cutText(s string, max int) string {
	s = identity.SanitizeOwner(s)
	for len(s) > max {
		_, size := utf8.DecodeLastRuneInString(s)
		s = s[:len(s)-size]
	}
	return strings.TrimSpace(s)
}

func (a *nearbyAnnouncement) encode(dev *identity.Device) []byte {
	b := make([]byte, 0, 2+32+2+16+3+3*nearbyTextMax+ed25519.SignatureSize)
	b = append(b, nearbyAnnounceMarker, nearbyFormat)
	b = append(b, a.ID[:]...)
	b = binary.BigEndian.AppendUint16(b, a.Port)
	b = append(b, a.Ticket[:]...)
	for _, s := range []string{a.Name, a.MeshName, a.OS} {
		s = cutText(s, nearbyTextMax)
		b = append(b, byte(len(s)))
		b = append(b, s...)
	}
	sig := ed25519.Sign(dev.Priv, append([]byte(nearbySigLabel), b...))
	return append(b, sig...)
}

// decodeNearbyAnnouncement checks the structure and the signature of an announcement; ok is false for anything else.
func decodeNearbyAnnouncement(b []byte) (a nearbyAnnouncement, ok bool) {
	const fixed = 2 + 32 + 2 + 16
	if len(b) < fixed+3+ed25519.SignatureSize || len(b) > fixed+3+3*nearbyTextMax+ed25519.SignatureSize || b[0] != nearbyAnnounceMarker || b[1] != nearbyFormat {
		return a, false
	}
	body, sig := b[:len(b)-ed25519.SignatureSize], b[len(b)-ed25519.SignatureSize:]
	copy(a.ID[:], b[2:34])
	if a.ID.IsZero() || !ed25519.Verify(a.ID.PublicKey(), append([]byte(nearbySigLabel), body...), sig) {
		return a, false
	}
	a.Port = binary.BigEndian.Uint16(b[34:36])
	copy(a.Ticket[:], b[36:52])
	rest := body[fixed:]
	var texts [3]string
	for i := range texts {
		if len(rest) < 1 || int(rest[0]) > nearbyTextMax || len(rest) < 1+int(rest[0]) {
			return a, false
		}
		n := int(rest[0])
		if !utf8.Valid(rest[1 : 1+n]) {
			return a, false
		}
		texts[i] = cutText(string(rest[1:1+n]), nearbyTextMax)
		rest = rest[1+n:]
	}
	if len(rest) != 0 || a.Port == 0 || texts[0] == "" {
		return a, false
	}
	a.Name, a.MeshName, a.OS = texts[0], texts[1], strings.ToLower(texts[2])
	return a, true
}

// nearbyLifeNanos is how long a request waits for the two people (adjustable so that tests need not wait for minutes).
var nearbyLifeNanos atomic.Int64

func init() { nearbyLifeNanos.Store(int64(2 * time.Minute)) }

func nearbyLife() time.Duration { return time.Duration(nearbyLifeNanos.Load()) }

// nearbyState is everything the node knows about the devices around it.
type nearbyState struct {
	mu        sync.Mutex
	seen      map[identity.ID]*nearbyEntry
	cur, prev [16]byte // tickets of this device as an inviter
	rotated   time.Time
	requests  map[string]*nearbyRequest // asks that wait for an answer (this device is the inviter)
	join      *nearbyJoin               // the attempt this device is making (it is the newcomer)
	lastReply map[netip.Addr]time.Time
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

// ---- what this device announces ----

// nearbyReady says whether this device should announce itself now, and what it would say: it must be an admin of a mesh
// (only an admin can add a device), the person must not have switched it off, and it must be able to take another request.
func (n *Node) nearbyReady() (name, mesh string, port int, ok bool) {
	n.mu.RLock()
	admin := n.auth != nil && n.root != nil && n.self != nil
	if admin {
		name, mesh = n.self.Name, n.meshName
	}
	port = n.udpPort
	off := n.cfg.NoNearby
	n.mu.RUnlock()
	if !admin || off || port <= 0 || port > 65535 {
		return "", "", 0, false
	}
	n.nearby.mu.Lock()
	full := len(n.nearby.requests) >= nearbyMaxRequests
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

// nearbyOpen: is this device willing to take a request from a newcomer right now.
func (n *Node) nearbyOpen() bool {
	_, _, _, ok := n.nearbyReady()
	return ok
}

func nearbySNI(ticket [16]byte) string { return hex.EncodeToString(ticket[:]) + "." + nearbySNISuffix }

const nearbySNISuffix = "nearby.mesh"

// checkNearbySNI reports whether sni names a current ticket of this device, i.e. whether whoever is asking has heard an
// announcement of ours a moment ago.
func (n *Node) checkNearbySNI(sni string) bool {
	host, ok := strings.CutSuffix(strings.TrimSuffix(sni, "."), "."+nearbySNISuffix)
	if !ok {
		return false
	}
	got, err := hex.DecodeString(host)
	if err != nil || len(got) != 16 {
		return false
	}
	n.nearby.mu.Lock()
	cur, prev := n.nearby.tickets(time.Now())
	n.nearby.mu.Unlock()
	return subtle.ConstantTimeCompare(got, cur[:]) == 1 || subtle.ConstantTimeCompare(got, prev[:]) == 1
}

// SetNearbyVisible switches on or off the announcements of this device (it still joins devices by invitation).
func (n *Node) SetNearbyVisible(on bool) {
	n.mu.Lock()
	n.cfg.NoNearby = !on
	n.mu.Unlock()
	n.updateAnonymous()
	n.emit(Event{Kind: EvNearby})
}

// NearbyVisible reports whether this device announces itself when it can add devices.
func (n *Node) NearbyVisible() bool {
	n.mu.RLock()
	defer n.mu.RUnlock()
	return !n.cfg.NoNearby
}

func nearbyQueryPacket() []byte {
	b := make([]byte, nearbyQueryLen)
	b[0], b[1] = nearbyQueryMarker, nearbyFormat
	_, _ = rand.Read(b[2:])
	return b
}

// ---- what this device hears ----

// nearbyAnnounced notes an announcement heard from a neighbour (only a device that is not in a mesh keeps a list).
func (n *Node) nearbyAnnounced(raw []byte, from netip.AddrPort) {
	if n.Configured() {
		return
	}
	a, ok := decodeNearbyAnnouncement(raw)
	if !ok || a.ID == n.device().ID {
		return
	}
	now := time.Now()
	n.nearby.mu.Lock()
	if n.nearby.seen == nil {
		n.nearby.seen = map[identity.ID]*nearbyEntry{}
	}
	e := n.nearby.seen[a.ID]
	changed := e == nil || e.Name != a.Name || e.MeshName != a.MeshName || e.OS != a.OS || e.addr != netip.AddrPortFrom(from.Addr().Unmap(), a.Port)
	if e == nil {
		if len(n.nearby.seen) >= nearbyMaxListed {
			n.nearby.mu.Unlock()
			return
		}
		e = &nearbyEntry{devID: a.ID}
		n.nearby.seen[a.ID] = e
	}
	e.NearbyDevice = NearbyDevice{ID: a.ID.String(), Name: a.Name, MeshName: a.MeshName, OS: a.OS, Seen: now.Unix()}
	e.addr = netip.AddrPortFrom(from.Addr().Unmap(), a.Port)
	e.ticket = a.Ticket
	e.heard = now
	n.nearby.mu.Unlock()
	if changed {
		n.emit(Event{Kind: EvNearby})
	}
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

// nearbyExpire drops what has gone stale: devices not heard for a while, requests nobody answered.
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
	n.nearby.mu.Unlock()
	if changed {
		n.emit(Event{Kind: EvNearby})
	}
}

// NearbyList lists the devices around that are ready to add this one (empty once this device is in a mesh).
func (n *Node) NearbyList() []NearbyDevice {
	n.nearbyExpire()
	out := []NearbyDevice{}
	if n.Configured() {
		return out
	}
	n.nearby.mu.Lock()
	for _, e := range n.nearby.seen {
		out = append(out, e.NearbyDevice)
	}
	n.nearby.mu.Unlock()
	sort.Slice(out, func(i, j int) bool {
		if out[i].Name != out[j].Name {
			return out[i].Name < out[j].Name
		}
		return out[i].ID < out[j].ID
	})
	return out
}

// forgetNearby clears the list (the device has joined a mesh).
func (n *Node) forgetNearby() {
	n.nearby.mu.Lock()
	n.nearby.seen = nil
	n.nearby.mu.Unlock()
}

// ErrNearbyGone: the device asked for is no longer on the list (it left the network, or switched its announcements off).
var ErrNearbyGone = errors.New("mesh: that device is no longer nearby")

// ErrNoSuchRequest: there is no such request waiting for an answer (it may have expired).
var ErrNoSuchRequest = errors.New("mesh: no such request (it may have expired)")
