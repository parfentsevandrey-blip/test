package mesh

import (
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"encoding/binary"
	"errors"
	"net"
	"net/netip"
	"strconv"
	"strings"
	"sync"
	"syscall"
	"time"

	"golang.org/x/crypto/chacha20poly1305"
	"golang.org/x/net/ipv4"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/magic"
)

// LAN discovery: devices on the same network announce themselves with tiny UDP
// multicast/broadcast beacons so they find each other instantly, without any
// internet connectivity and without going through a NAT. A beacon is only a
// hint ("this device is probably at this address"); the real authentication
// happens in the disco handshake, so a forged beacon can at worst cause a few
// wasted probes.
//
// A beacon is sealed with a key derived from the mesh's root public key (AEAD,
// fresh random nonce each time): a bystander on the same network can neither read
// the device key inside nor recognise two beacons as coming from the same device
// or mesh, so the broadcast cannot be used to follow someone from café to café.
// That key is not a secret of the mesh - everybody who has seen an invitation
// knows the root public key - so what vouches for a beacon is the signature of the
// device it names: nobody can announce a member's device in that member's name.
// (A beacon that was recorded can be sent again; see the note above.)

const (
	defaultLANPort = 41711
	lanVersion     = 3
	beaconBody     = 32 + 2 + ed25519.SignatureSize // device id, udp port, signature
	beaconLen      = 1 + chacha20poly1305.NonceSizeX + beaconBody + chacha20poly1305.Overhead
	beaconSigLabel = "themesh-lan-beacon/v3\x00"
)

var (
	lanGroup     = net.IPv4(239, 255, 77, 77)
	lanGroupAddr = netip.AddrFrom4([4]byte{239, 255, 77, 77})
)

// What a device announces on is the list of networks magic.LocalNets gives: the system's own list of network
// cards, or, where the system refuses to give it (Android 11+), the addresses the phone's app writes down for
// the program (see magic/netview.go). Either way a beacon goes to the multicast group and to the network's
// broadcast address, one per network, every few seconds; the list is looked at again each time, so a phone
// that joins Wi-Fi after the program started is found, and finds, a few seconds later.

// LANStatus says what local discovery is doing, so that the interface can explain why two devices on one
// network do not find each other.
type LANStatus struct {
	Enabled  bool     `json:"enabled"`            // the beacon socket is open
	Networks []string `json:"networks,omitempty"` // where it announces: "192.168.1.50/24 (en0)"
	// Problem is "" when all is well, or why announcing does not work: "no-network" (no Wi-Fi or Ethernet
	// address to announce on), "blocked" (the system refuses to send on the local network: on a Mac, the
	// "Local Network" permission of the app is off) or "failed" (some other error, see Detail).
	Problem string `json:"problem,omitempty"`
	Detail  string `json:"detail,omitempty"` // the system's own words
}

type lanDiscovery struct {
	n      *Node
	conn   *net.UDPConn
	pc4    *ipv4.PacketConn
	port   int
	cancel context.CancelFunc
	done   chan struct{}

	mu           sync.Mutex
	status       LANStatus
	failedRounds int    // rounds in a row in which nothing could be sent
	logged       string // the networks as last written to the log
}

// beaconSigned is what a device signs: this very beacon's nonce, its id and its port.
func beaconSigned(nonce []byte, id identity.ID, udpPort uint16) []byte {
	m := make([]byte, 0, len(beaconSigLabel)+len(nonce)+34)
	m = append(m, beaconSigLabel...)
	m = append(m, nonce...)
	m = append(m, id[:]...)
	return binary.BigEndian.AppendUint16(m, udpPort)
}

// encodeBeacon: [version][nonce 24][seal(device id 32 | udp port 2 | signature 64)].
func encodeBeacon(key [32]byte, dev *identity.Device, udpPort int) []byte {
	aead, err := chacha20poly1305.NewX(key[:])
	if err != nil {
		return nil
	}
	b := make([]byte, 0, beaconLen)
	b = append(b, lanVersion)
	b = b[:1+aead.NonceSize()]
	_, _ = rand.Read(b[1:])
	plain := make([]byte, 0, beaconBody)
	plain = append(plain, dev.ID[:]...)
	plain = binary.BigEndian.AppendUint16(plain, uint16(udpPort))
	plain = append(plain, ed25519.Sign(dev.Priv, beaconSigned(b[1:], dev.ID, uint16(udpPort)))...)
	return aead.Seal(b, b[1:], plain, []byte{lanVersion})
}

// decodeBeacon opens a beacon with the mesh's key and checks the signature of the
// device it names; ok is false for anything else.
func decodeBeacon(key [32]byte, b []byte) (id identity.ID, udpPort int, ok bool) {
	if len(b) != beaconLen || b[0] != lanVersion {
		return id, 0, false
	}
	aead, err := chacha20poly1305.NewX(key[:])
	if err != nil {
		return id, 0, false
	}
	plain, err := aead.Open(nil, b[1:1+aead.NonceSize()], b[1+aead.NonceSize():], []byte{lanVersion})
	if err != nil || len(plain) != beaconBody {
		return id, 0, false
	}
	copy(id[:], plain[:32])
	port := binary.BigEndian.Uint16(plain[32:34])
	if !ed25519.Verify(id.PublicKey(), beaconSigned(b[1:1+aead.NonceSize()], id, port), plain[34:]) {
		return identity.ID{}, 0, false
	}
	return id, int(port), true
}

func (n *Node) startLAN(parent context.Context) {
	n.lanMu.Lock()
	defer n.lanMu.Unlock()
	n.mu.RLock()
	running := n.lan != nil
	n.mu.RUnlock()
	if running {
		return // already listening (a device that is not in a mesh listens too, for the devices nearby)
	}
	port := n.cfg.LANPort
	if port == 0 {
		port = defaultLANPort
	}
	lc := net.ListenConfig{Control: reuseAddrControl}
	pc, err := lc.ListenPacket(context.Background(), "udp4", ":"+strconv.Itoa(port))
	if err != nil {
		n.log.Warn("LAN discovery disabled", "err", err)
		return
	}
	conn := pc.(*net.UDPConn)
	ctx, cancel := context.WithCancel(parent)
	l := &lanDiscovery{n: n, conn: conn, pc4: ipv4.NewPacketConn(conn), port: port, cancel: cancel, done: make(chan struct{})}
	_ = l.pc4.SetMulticastLoopback(true) // several nodes on one machine (tests, demos) hear each other
	l.status.Enabled = true
	n.mu.Lock()
	n.lan = l
	n.mu.Unlock()
	go l.run(ctx)
}

// startIdleLAN starts listening on the local network for a device that is not in a mesh (it hears the devices nearby
// that can add it, nearby.go); a device in a mesh starts it with its network.
func (n *Node) startIdleLAN() {
	if n.cfg.LANPort >= 0 {
		n.startLAN(context.Background())
	}
}

func (l *lanDiscovery) stop() {
	l.cancel()
	_ = l.conn.Close()
	<-l.done
}

// join makes this socket a member of the beacon group on the network ln, so that the group's packets
// arrive from there.
func (l *lanDiscovery) join(ln magic.LocalNet) error {
	if ln.Iface != nil {
		return l.pc4.JoinGroup(ln.Iface, &net.UDPAddr{IP: lanGroup})
	}
	return joinGroupByAddr(l.conn, lanGroupAddr, ln.Addr)
}

// sendVia makes multicast packets of this socket leave through the network ln.
func (l *lanDiscovery) sendVia(ln magic.LocalNet) error {
	if ln.Iface != nil {
		return l.pc4.SetMulticastInterface(ln.Iface)
	}
	return setMulticastIfByAddr(l.conn, ln.Addr)
}

// round announces this device on every network it is on, once: its beacon to the mesh it is in, the announcement that it
// can add devices (an admin that is visible), or, when it is not in a mesh, the question who nearby can add it.
func (l *lanDiscovery) round(joined map[netip.Addr]bool) {
	l.n.nearbyExpire()
	root := l.n.Root()
	advert := l.n.nearbyAdvert()
	var query []byte
	if root == nil {
		query = nearbyQueryPacket()
	}
	nets := magic.LocalNets()
	var sent, failed int
	var firstErr error
	live := map[netip.Addr]bool{}
	for _, ln := range nets {
		live[ln.Addr] = true
		if !joined[ln.Addr] {
			// Not marked as joined until it worked: a network card that was not quite ready is tried again.
			if err := l.join(ln); err == nil || errors.Is(err, syscall.EADDRINUSE) {
				joined[ln.Addr] = true
			} else {
				l.n.log.Debug("LAN discovery: cannot join the group", "network", ln.String(), "err", err)
			}
		}
		var packets [][]byte
		if root != nil {
			if beacon := encodeBeacon(root.LANKey(), l.n.device(), l.n.udpPort); beacon != nil { // fresh nonce per send: unlinkable
				packets = append(packets, beacon)
			}
		}
		if advert != nil {
			packets = append(packets, advert)
		}
		if query != nil {
			packets = append(packets, query)
		}
		if len(packets) == 0 {
			continue
		}
		if err := l.sendVia(ln); err != nil {
			l.n.log.Debug("LAN discovery: cannot pick the network to send on", "network", ln.String(), "err", err)
		}
		// The group, and also the network's broadcast address: some Wi-Fi access points drop multicast.
		targets := []netip.Addr{lanGroupAddr}
		if bc, ok := ln.Broadcast(); ok {
			targets = append(targets, bc)
		}
		for _, pkt := range packets {
			for _, to := range targets {
				if _, err := l.conn.WriteToUDPAddrPort(pkt, netip.AddrPortFrom(to, uint16(l.port))); err != nil {
					failed++
					if firstErr == nil {
						firstErr = err
					}
				} else {
					sent++
				}
			}
		}
	}
	for a := range joined {
		if !live[a] {
			delete(joined, a) // the network went away: join again when it is back
		}
	}
	l.record(nets, sent, failed, firstErr)
}

// record keeps what the last round did, for LANStatus, and writes a line to the log when it changes.
func (l *lanDiscovery) record(nets []magic.LocalNet, sent, failed int, firstErr error) {
	st := LANStatus{Enabled: true}
	names := make([]string, 0, len(nets))
	for _, ln := range nets {
		names = append(names, ln.String())
	}
	st.Networks = names
	l.mu.Lock()
	defer l.mu.Unlock()
	switch {
	case len(nets) == 0:
		st.Problem = "no-network"
		l.failedRounds = 0
	case sent == 0 && failed > 0:
		// One round that sends nothing can be a network that is just changing; two are a problem.
		if l.failedRounds++; l.failedRounds >= 2 {
			st.Problem, st.Detail = "failed", firstErr.Error()
			if errors.Is(firstErr, syscall.EHOSTUNREACH) || errors.Is(firstErr, syscall.EPERM) || errors.Is(firstErr, syscall.EACCES) {
				st.Problem = "blocked"
			}
		} else {
			st.Problem, st.Detail = l.status.Problem, l.status.Detail
		}
	default:
		l.failedRounds = 0
	}
	if desc := strings.Join(names, ", "); desc != l.logged {
		l.logged = desc
		if desc == "" {
			l.n.log.Info("LAN discovery: no local network to announce on")
		} else {
			l.n.log.Info("LAN discovery announcing", "networks", desc)
		}
	}
	if st.Problem != l.status.Problem {
		if st.Problem == "" {
			l.n.log.Info("LAN discovery works again")
		} else {
			l.n.log.Warn("LAN discovery cannot send", "problem", st.Problem, "err", st.Detail)
		}
	}
	l.status = st
}

func (l *lanDiscovery) run(ctx context.Context) {
	defer close(l.done)
	go l.readLoop(ctx)

	joined := map[netip.Addr]bool{} // networks, by our address on them, whose group this socket has joined
	t := time.NewTicker(5 * time.Second)
	defer t.Stop()
	select {
	case <-time.After(500 * time.Millisecond):
	case <-ctx.Done():
		return
	}
	l.round(joined)
	for {
		select {
		case <-t.C:
			l.round(joined)
		case <-ctx.Done():
			return
		}
	}
}

func (l *lanDiscovery) readLoop(ctx context.Context) {
	buf := make([]byte, 256)
	for {
		nr, from, err := l.conn.ReadFromUDPAddrPort(buf)
		if err != nil {
			if ctx.Err() != nil {
				return
			}
			if ne, ok := err.(net.Error); ok && ne.Timeout() {
				continue
			}
			if isClosedErr(err) {
				return
			}
			time.Sleep(100 * time.Millisecond)
			continue
		}
		l.heard(buf[:nr], from)
	}
}

func isClosedErr(err error) bool {
	if err == nil {
		return false
	}
	if err == net.ErrClosed {
		return true
	}
	if oe, ok := err.(*net.OpError); ok {
		return oe.Err == net.ErrClosed || oe.Err == syscall.EBADF
	}
	return false
}

// onLink reports whether ip lies inside a network this device is attached to (nets).
// A beacon is a local-network affair: one that claims to come from anywhere else
// (a spoofed source, a routed packet) is not allowed to make us probe that address.
func onLink(ip netip.Addr, nets []netip.Prefix) bool {
	ip = ip.Unmap()
	for _, p := range nets {
		if p.Contains(ip) {
			return true
		}
	}
	return false
}

// localNetworks is the cached list of the networks this machine is attached to.
var localNetworks struct {
	mu   sync.Mutex
	at   time.Time
	nets []netip.Prefix
}

func fromLocalNetwork(ip netip.Addr) bool {
	localNetworks.mu.Lock()
	defer localNetworks.mu.Unlock()
	if time.Since(localNetworks.at) > 2*time.Second {
		localNetworks.nets = magic.LocalPrefixes()
		localNetworks.at = time.Now()
	}
	return onLink(ip, localNetworks.nets)
}

// snapshot is the status as of the last round.
func (l *lanDiscovery) snapshot() LANStatus {
	l.mu.Lock()
	defer l.mu.Unlock()
	st := l.status
	st.Networks = append([]string(nil), st.Networks...)
	return st
}

// LANStatus reports what local discovery is doing now; the zero value when it is switched off.
func (n *Node) LANStatus() LANStatus {
	n.mu.RLock()
	l := n.lan
	n.mu.RUnlock()
	if l == nil {
		return LANStatus{}
	}
	return l.snapshot()
}

// heard handles one packet from the local network: a member's beacon, an announcement of a device that can add devices
// (nearby.go), or a question who can.
func (l *lanDiscovery) heard(b []byte, from netip.AddrPort) {
	if len(b) == 0 || !fromLocalNetwork(from.Addr()) {
		return
	}
	switch b[0] {
	case nearbyAnnounceMarker:
		l.n.nearbyAnnounced(b, from)
	case nearbyQueryMarker:
		if len(b) == nearbyQueryLen && b[1] == nearbyFormat {
			if advert := l.n.nearbyAdvert(); advert != nil && l.n.nearbyShouldReply(from.Addr()) {
				_, _ = l.conn.WriteToUDPAddrPort(advert, from) // to the asker only: it also works where multicast does not
			}
		}
	default:
		l.n.lanHeard(b, from)
	}
}

// lanHeard handles one received beacon.
func (n *Node) lanHeard(b []byte, from netip.AddrPort) {
	root := n.Root()
	if root == nil || !fromLocalNetwork(from.Addr()) {
		return
	}
	id, port, ok := decodeBeacon(root.LANKey(), b)
	if !ok || id == n.device().ID {
		return
	}
	p := n.Peer(id)
	mg := n.Magic()
	if p == nil || mg == nil || port == 0 {
		return // an unknown device: wait until the member list tells us about it
	}
	addr := netip.AddrPortFrom(from.Addr().Unmap(), uint16(port))
	mg.AddCandidates(id, []netip.AddrPort{addr}, magic.SrcLAN)
	if !p.Online() {
		p.mu.Lock()
		p.nextDial = time.Now()
		p.mu.Unlock()
		mg.Poke(id)
		n.kick()
	}
}
