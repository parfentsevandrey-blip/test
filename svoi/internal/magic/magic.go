// Package magic is the UDP layer under the mesh. It makes a set of devices that
// sit behind home routers, mobile carriers and firewalls look like they can
// send datagrams to each other directly.
//
// To the QUIC stack above it, magic is an ordinary net.PacketConn whose
// "addresses" are virtual: one per mesh member. Underneath, for every packet it
// decides which real path to use:
//
//  1. a direct UDP path that was verified with an authenticated ping/pong
//     exchange (hole punching: both sides probe each other's candidate
//     endpoints at the same time, which opens the NAT mappings), or
//  2. a relay through another mesh member that has a direct path to both ends
//     (the relayed packets stay end-to-end encrypted by QUIC).
//
// There is no coordination server. Endpoint information travels between
// members inside the mesh itself (peer-state gossip, LAN beacons, invites) and
// the relay is simply a member that agrees to forward.
package magic

import (
	"context"
	"crypto/subtle"
	"errors"
	"fmt"
	"net"
	"net/netip"
	"os"
	"sort"
	"sync"
	"sync/atomic"
	"time"

	"golang.org/x/crypto/blake2s"
	"golang.org/x/crypto/nacl/box"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/portmap"
)

// Source says where a candidate endpoint came from.
type Source uint8

// Candidate sources.
const (
	SrcInvite   Source = 1 << iota // from an invitation
	SrcLAN                         // heard in a LAN beacon
	SrcGossip                      // told to us by a member
	SrcObserved                    // a verified packet arrived from it
	SrcStored                      // remembered from an earlier run
)

// PathKind classifies the path currently used to reach a peer.
type PathKind uint8

// Path kinds.
const (
	PathNone   PathKind = iota
	PathLAN             // direct, private address
	PathDirect          // direct, public address (hole punched or reachable)
	PathRelay           // through another member
)

func (k PathKind) String() string {
	return [...]string{"none", "lan", "direct", "relay"}[k]
}

// PathInfo describes the current path to a peer.
type PathInfo struct {
	Kind       PathKind
	Addr       netip.AddrPort // direct address (Kind LAN/Direct)
	Relay      identity.ID    // relay member (Kind Relay)
	RTT        time.Duration
	LastHeard  time.Time
	Candidates int
}

// EndpointKind classifies one of our own endpoints.
type EndpointKind uint8

// Endpoint kinds.
const (
	EPLocal    EndpointKind = iota // an interface address
	EPSTUN                         // learned from a STUN server
	EPObserved                     // a member told us how it sees us
	EPMapped                       // the router forwards this public address to us (UPnP / NAT-PMP)
)

func (k EndpointKind) String() string { return [...]string{"local", "stun", "observed", "mapped"}[k] }

// Endpoint is one address other members may try to reach us at.
type Endpoint struct {
	Addr netip.AddrPort
	Kind EndpointKind
}

// Timing holds the protocol timers; tests shrink them.
type Timing struct {
	Tick          time.Duration // maintenance loop period
	ProbeFast     time.Duration // probe interval while actively connecting
	ProbeSlow     time.Duration // probe interval for peers nobody wants right now
	KeepAlive     time.Duration // direct-path ping interval while the peer is wanted
	KeepAliveIdle time.Duration // ... and while it is idle
	PathTTL       time.Duration // a direct path with no pong for this long is dead
	WantFor       time.Duration // how long traffic or Poke keeps a peer "wanted"
	CallMeMaybe   time.Duration // minimum gap between call-me-maybe messages
	STUNEvery     time.Duration
	NetCheckEvery time.Duration
	StateEvery    time.Duration // peer-state gossip period
}

func (t *Timing) fill() {
	def := func(p *time.Duration, v time.Duration) {
		if *p == 0 {
			*p = v
		}
	}
	def(&t.Tick, 250*time.Millisecond)
	def(&t.ProbeFast, time.Second)
	def(&t.ProbeSlow, 20*time.Second)
	def(&t.KeepAlive, 5*time.Second)
	def(&t.KeepAliveIdle, 20*time.Second)
	def(&t.PathTTL, 16*time.Second)
	def(&t.WantFor, 30*time.Second)
	def(&t.CallMeMaybe, 4*time.Second)
	def(&t.STUNEvery, 60*time.Second)
	def(&t.NetCheckEvery, 5*time.Second)
	def(&t.StateEvery, 30*time.Second)
}

// Config configures a Conn.
type Config struct {
	Device *identity.Device
	// Port is the UDP port to listen on (0 = pick one).
	Port int
	// Listen opens the UDP socket. Default: a dual-stack UDP socket.
	Listen func(port int) (net.PacketConn, error)
	// LocalAddrs lists this host's interface addresses. Default: the OS list
	// minus loopback, link-local and overlay addresses.
	LocalAddrs func() []netip.Addr
	// STUN servers ("host:port") used to learn our public endpoint. Optional.
	STUN []string
	// ResolveSTUN resolves a STUN server name. Default: net.LookupHost.
	ResolveSTUN func(hostport string) ([]netip.AddrPort, error)
	// SelfCert returns our member certificate (DER); it is attached to the first
	// pings so peers that have not heard of us yet can verify and accept us.
	SelfCert func() []byte
	// AcceptUnknown is asked about a sender that is not a registered peer but
	// presented a certificate. It should verify the certificate and, if it is
	// valid for this mesh, call AddPeer and return true.
	AcceptUnknown func(id identity.ID, cert []byte) bool
	// AllowRelay says whether we agree to forward packets between other members.
	AllowRelay func() bool
	// OnPath is called (from an internal goroutine) when a peer's path changes.
	OnPath func(id identity.ID, info PathInfo)
	// PortMap asks the router (UPnP IGD, NAT-PMP) to forward our UDP port, so that
	// a device behind a home router is reachable from outside. Off by default.
	PortMap bool
	// OnEndpoints is called when our own endpoint list changes.
	OnEndpoints func(eps []Endpoint)
	// OnPeerEndpoints is called when a peer tells us where it can be reached.
	OnPeerEndpoints func(id identity.ID, eps []netip.AddrPort)
	Logf            func(format string, args ...any)
	Timing          Timing
}

type inPkt struct {
	buf  *[]byte
	n    int
	addr *net.UDPAddr
}

// Conn is the magic socket. It implements net.PacketConn.
type Conn struct {
	cfg Config
	dev *identity.Device
	// Our X25519 key pair for disco boxes, and our 8 byte routing id.
	xpriv, xpub [32]byte
	r8          [8]byte

	sock udpSock
	port uint16

	ctx    context.Context
	cancel context.CancelFunc
	wg     sync.WaitGroup
	closed atomic.Bool

	portmap *portmap.Mapper // nil unless Config.PortMap

	mu    sync.RWMutex
	peers map[identity.ID]*peer
	byR8  map[[8]byte]*peer
	byV   map[netip.AddrPort]*peer

	inbound chan inPkt
	anonOK  atomic.Bool

	rdMu       sync.Mutex
	rdDeadline time.Time
	rdWake     chan struct{}

	unknownGate *netGate // disco packets of senders we do not know (see handleDisco)
	anonLimit   *anonGate

	self selfState

	bufPool sync.Pool

	allowRelayCached atomic.Bool
	kick             chan struct{}
}

var _ net.PacketConn = (*Conn)(nil)

// New opens the UDP socket and starts the background loops.
func New(cfg Config) (*Conn, error) {
	if cfg.Device == nil {
		return nil, errors.New("magic: device identity required")
	}
	cfg.Timing.fill()
	if cfg.Logf == nil {
		cfg.Logf = func(string, ...any) {}
	}
	if cfg.Listen == nil {
		cfg.Listen = listenUDP
	}
	if cfg.LocalAddrs == nil {
		cfg.LocalAddrs = DefaultLocalAddrs
	}
	if cfg.ResolveSTUN == nil {
		cfg.ResolveSTUN = resolveHostPort
	}
	if cfg.AllowRelay == nil {
		cfg.AllowRelay = func() bool { return true }
	}
	pc, err := cfg.Listen(cfg.Port)
	if err != nil {
		return nil, fmt.Errorf("magic: listen: %w", err)
	}
	c := &Conn{
		cfg:     cfg,
		dev:     cfg.Device,
		sock:    wrapSock(pc),
		peers:   map[identity.ID]*peer{},
		byR8:    map[[8]byte]*peer{},
		byV:     map[netip.AddrPort]*peer{},
		inbound: make(chan inPkt, 4096),
		rdWake:  make(chan struct{}, 1),
		kick:    make(chan struct{}, 1),
	}
	c.r8 = cfg.Device.ID.Route8()
	c.xpriv, c.xpub = cfg.Device.X25519()
	c.port = uint16(pc.LocalAddr().(*net.UDPAddr).Port)
	c.ctx, c.cancel = context.WithCancel(context.Background())
	c.bufPool.New = func() any { b := make([]byte, maxPacket); return &b }
	c.unknownGate = newNetGate(unknownPerNetRate, unknownPerNetBurst, 100, 200)
	c.anonLimit = newAnonGate()
	c.self.init()
	// Know our interface addresses before anyone asks (an invitation created right
	// after startup must already contain them).
	local := cfg.LocalAddrs()
	sort.Slice(local, func(i, j int) bool { return local[i].Less(local[j]) })
	c.self.local = local
	c.self.lastEps = c.endpointsLocked(time.Now())

	if cfg.PortMap { // before the loops start: they look at c.portmap
		c.startPortMap()
	}
	c.wg.Add(2)
	go c.readLoop()
	go c.maintLoop()
	c.wg.Add(1)
	go c.endpointLoop()
	return c, nil
}

// Port returns the UDP port the socket is bound to.
func (c *Conn) Port() int { return int(c.port) }

// Close stops all loops and closes the socket.
func (c *Conn) Close() error {
	if !c.closed.CompareAndSwap(false, true) {
		return nil
	}
	c.cancel()
	if c.portmap != nil {
		c.portmap.Close() // takes the mapping off the router (bounded)
	}
	err := c.sock.Close()
	c.wg.Wait()
	return err
}

// SetAnonymous controls whether packets from non-members (joiners) are passed
// up to QUIC. It is only on while an invitation is outstanding or while we are
// ourselves trying to join a mesh, so an idle node is silent to strangers.
func (c *Conn) SetAnonymous(on bool) { c.anonOK.Store(on) }

// ---- peer registry ----

// AddPeer registers a mesh member. It is idempotent.
func (c *Conn) AddPeer(id identity.ID) error {
	if id == c.dev.ID {
		return errors.New("magic: cannot add self")
	}
	c.mu.Lock()
	defer c.mu.Unlock()
	if _, ok := c.peers[id]; ok {
		return nil
	}
	pub, err := identity.IDToX25519(id)
	if err != nil {
		return err
	}
	p := &peer{
		c:           c,
		id:          id,
		r8:          id.Route8(),
		xpub:        pub,
		cands:       map[netip.AddrPort]*cand{},
		pending:     map[[12]byte]pingRec{},
		theirDirect: map[[8]byte]bool{},
	}
	if other, clash := c.byR8[p.r8]; clash {
		return fmt.Errorf("magic: routing id clash between %s and %s", id.Short(), other.id.Short())
	}
	box.Precompute(&p.shared, &pub, &c.xpriv)
	p.macKey = deriveMACKey(p.shared)
	p.dataKey = deriveKey("themesh/data-mac/v1", p.shared)
	var v [16]byte
	v[0], v[1], v[2], v[3] = virtualPrefix[0], virtualPrefix[1], virtualPrefix[2], virtualPrefix[3]
	copy(v[8:], p.r8[:])
	p.vap = netip.AddrPortFrom(netip.AddrFrom16(v), 1)
	p.vaddr = net.UDPAddrFromAddrPort(p.vap)
	c.peers[id] = p
	c.byR8[p.r8] = p
	c.byV[p.vap] = p
	return nil
}

// RemovePeer forgets a member (for example after revocation).
func (c *Conn) RemovePeer(id identity.ID) {
	c.mu.Lock()
	p := c.peers[id]
	if p != nil {
		delete(c.peers, id)
		delete(c.byR8, p.r8)
		delete(c.byV, p.vap)
	}
	c.mu.Unlock()
	if p != nil {
		c.recomputeRelays()
	}
}

// VirtualAddr is the address QUIC should use to talk to the peer.
func (c *Conn) VirtualAddr(id identity.ID) (*net.UDPAddr, bool) {
	c.mu.RLock()
	defer c.mu.RUnlock()
	if p := c.peers[id]; p != nil {
		return p.vaddr, true
	}
	return nil, false
}

// PeerForAddr maps a virtual address back to the member.
func (c *Conn) PeerForAddr(a net.Addr) (identity.ID, bool) {
	ua, ok := a.(*net.UDPAddr)
	if !ok {
		return identity.ID{}, false
	}
	ap := normalize(ua.AddrPort())
	c.mu.RLock()
	defer c.mu.RUnlock()
	if p := c.byV[ap]; p != nil {
		return p.id, true
	}
	return identity.ID{}, false
}

func (c *Conn) peerByID(id identity.ID) *peer {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.peers[id]
}

func (c *Conn) peerByR8(r8 [8]byte) *peer {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.byR8[r8]
}

func (c *Conn) allPeers() []*peer {
	c.mu.RLock()
	defer c.mu.RUnlock()
	out := make([]*peer, 0, len(c.peers))
	for _, p := range c.peers {
		out = append(out, p)
	}
	return out
}

// AddCandidates tells magic where a peer might be reachable.
func (c *Conn) AddCandidates(id identity.ID, eps []netip.AddrPort, src Source) {
	p := c.peerByID(id)
	if p == nil {
		return
	}
	if p.addCandidates(eps, src) {
		c.Kick()
	}
}

// Poke says "I want to talk to this peer": probing is stepped up.
func (c *Conn) Poke(id identity.ID) {
	p := c.peerByID(id)
	if p == nil {
		return
	}
	p.mu.Lock()
	p.wantUntil = time.Now().Add(c.cfg.Timing.WantFor)
	if p.nextProbe.After(time.Now()) {
		p.nextProbe = time.Now()
	}
	p.mu.Unlock()
	c.Kick()
}

// Kick wakes the maintenance loop immediately.
func (c *Conn) Kick() {
	select {
	case c.kick <- struct{}{}:
	default:
	}
}

// PathInfo returns the current path to the peer.
func (c *Conn) PathInfo(id identity.ID) PathInfo {
	p := c.peerByID(id)
	if p == nil {
		return PathInfo{}
	}
	p.mu.Lock()
	defer p.mu.Unlock()
	return p.pathLocked(time.Now())
}

// KnownEndpoints returns up to max addresses at which the peer has answered a
// verified probe, most recently confirmed first. They are good hints to hand to
// other members or to remember across restarts.
func (c *Conn) KnownEndpoints(id identity.ID, max int) []netip.AddrPort {
	p := c.peerByID(id)
	if p == nil {
		return nil
	}
	p.mu.Lock()
	defer p.mu.Unlock()
	var list []*cand
	for _, cd := range p.cands {
		if !cd.lastPong.IsZero() {
			list = append(list, cd)
		}
	}
	sort.Slice(list, func(i, j int) bool { return list[i].lastPong.After(list[j].lastPong) })
	var out []netip.AddrPort
	for _, cd := range list {
		if len(out) >= max {
			break
		}
		out = append(out, cd.ap)
	}
	return out
}

// PeerStats are cumulative traffic counters for a peer.
type PeerStats struct {
	TxDirect, RxDirect, TxRelay, RxRelay uint64 // bytes of QUIC payload
}

// Stats returns traffic counters for the peer.
func (c *Conn) Stats(id identity.ID) PeerStats {
	p := c.peerByID(id)
	if p == nil {
		return PeerStats{}
	}
	return PeerStats{
		TxDirect: p.txDirect.Load(), RxDirect: p.rxDirect.Load(),
		TxRelay: p.txRelay.Load(), RxRelay: p.rxRelay.Load(),
	}
}

// RelayStats reports how much traffic this node has forwarded for others.
func (c *Conn) RelayStats() (packets, bytes uint64) {
	return c.self.relayedPkts.Load(), c.self.relayedBytes.Load()
}

// ---- net.PacketConn ----

// LocalAddr implements net.PacketConn.
func (c *Conn) LocalAddr() net.Addr {
	return &net.UDPAddr{IP: net.IPv6unspecified, Port: int(c.port)}
}

// ReadFrom implements net.PacketConn: it returns QUIC packets from peers
// (with the peer's virtual address) and from anonymous joiners (with their
// real address).
func (c *Conn) ReadFrom(p []byte) (int, net.Addr, error) {
	for {
		c.rdMu.Lock()
		dl := c.rdDeadline
		c.rdMu.Unlock()
		var timer <-chan time.Time
		var t *time.Timer
		if !dl.IsZero() {
			d := time.Until(dl)
			if d <= 0 {
				return 0, nil, os.ErrDeadlineExceeded
			}
			t = time.NewTimer(d)
			timer = t.C
		}
		select {
		case pk := <-c.inbound:
			if t != nil {
				t.Stop()
			}
			n := copy(p, (*pk.buf)[:pk.n])
			c.bufPool.Put(pk.buf)
			return n, pk.addr, nil
		case <-c.rdWake:
			if t != nil {
				t.Stop()
			}
		case <-timer:
			return 0, nil, os.ErrDeadlineExceeded
		case <-c.ctx.Done():
			if t != nil {
				t.Stop()
			}
			return 0, nil, net.ErrClosed
		}
	}
}

// WriteTo implements net.PacketConn. Packets to a peer's virtual address take
// the best path; packets to any other address go out as anonymous join traffic.
func (c *Conn) WriteTo(p []byte, addr net.Addr) (int, error) {
	if c.closed.Load() {
		return 0, net.ErrClosed
	}
	ua, ok := addr.(*net.UDPAddr)
	if !ok {
		return 0, errors.New("magic: expected *net.UDPAddr")
	}
	ap := normalize(ua.AddrPort())
	c.mu.RLock()
	pr := c.byV[ap]
	c.mu.RUnlock()
	if pr != nil {
		c.sendData(pr, p)
		return len(p), nil
	}
	if !c.anonOK.Load() {
		return len(p), nil // silently drop: nobody should be talking to strangers
	}
	bp := c.bufPool.Get().(*[]byte)
	pkt := append((*bp)[:0], typeAnon)
	pkt = append(pkt, p...)
	_, _ = c.sock.WriteToAddrPort(pkt, ap)
	c.bufPool.Put(bp)
	return len(p), nil
}

// SetDeadline implements net.PacketConn.
func (c *Conn) SetDeadline(t time.Time) error { return c.SetReadDeadline(t) }

// SetReadDeadline implements net.PacketConn.
func (c *Conn) SetReadDeadline(t time.Time) error {
	c.rdMu.Lock()
	c.rdDeadline = t
	c.rdMu.Unlock()
	select {
	case c.rdWake <- struct{}{}:
	default:
	}
	return nil
}

// SetWriteDeadline implements net.PacketConn (writes never block).
func (c *Conn) SetWriteDeadline(time.Time) error { return nil }

// ---- receive path ----

func (c *Conn) readLoop() {
	defer c.wg.Done()
	buf := make([]byte, maxPacket*2)
	for {
		n, from, err := c.sock.ReadFromAddrPort(buf)
		if err != nil {
			if c.closed.Load() || errors.Is(err, net.ErrClosed) {
				return
			}
			c.cfg.Logf("magic: read error: %v", err)
			select {
			case <-time.After(50 * time.Millisecond):
			case <-c.ctx.Done():
				return
			}
			continue
		}
		if n == 0 {
			continue
		}
		c.handlePacket(buf[:n], normalize(from))
	}
}

func (c *Conn) handlePacket(pkt []byte, from netip.AddrPort) {
	switch pkt[0] {
	case typeData:
		if len(pkt) <= dataHeader {
			return
		}
		pr := c.peerByR8([8]byte(pkt[1:9]))
		if pr == nil || !c.dataMACOK(pr, [8]byte(pkt[9:17]), pkt[dataHeader:]) {
			return // unknown sender or forged: never reaches QUIC and never teaches us an address
		}
		pr.noteDirectSource(from)
		c.deliver(pr, pkt[dataHeader:], false)
	case typeDisco:
		c.handleDisco(pkt, from, nil)
	case typeRelayOut:
		c.handleRelayOut(pkt)
	case typeRelayIn:
		c.handleRelayIn(pkt)
	case typeAnon:
		if !c.anonOK.Load() || len(pkt) <= 1 {
			return
		}
		// Stand-in addresses belong to members, whose packets arrive on the authenticated
		// path. A stranger's packet that claims one as its source is a forgery: it would
		// skip QUIC's address check and make us answer a member.
		if isVirtualAddr(from.Addr()) {
			return
		}
		if !c.anonLimit.allow(from.Addr()) {
			return
		}
		c.enqueue(pkt[1:], net.UDPAddrFromAddrPort(from))
	default:
		if looksLikeSTUN(pkt) {
			c.handleSTUN(pkt, from)
		}
	}
}

// deliver hands a QUIC packet from a peer up to the QUIC stack.
func (c *Conn) deliver(pr *peer, payload []byte, relayed bool) {
	now := time.Now()
	pr.mu.Lock()
	pr.lastRx = now
	pr.mu.Unlock()
	if relayed {
		pr.rxRelay.Add(uint64(len(payload)))
	} else {
		pr.rxDirect.Add(uint64(len(payload)))
	}
	c.enqueue(payload, pr.vaddr)
}

func (c *Conn) enqueue(payload []byte, addr *net.UDPAddr) {
	bp := c.bufPool.Get().(*[]byte)
	n := copy(*bp, payload)
	select {
	case c.inbound <- inPkt{buf: bp, n: n, addr: addr}:
	default:
		c.bufPool.Put(bp) // queue full: drop like a congested NIC would
	}
}

// ---- send path ----

func (c *Conn) sendData(pr *peer, payload []byte) {
	now := time.Now()
	pr.mu.Lock()
	pr.lastTx = now
	if want := now.Add(c.cfg.Timing.WantFor); want.After(pr.wantUntil) {
		pr.wantUntil = want
	}
	direct, ok := pr.directLocked(now, c.cfg.Timing.PathTTL)
	relay := pr.relayVia
	pr.mu.Unlock()

	bp := c.bufPool.Get().(*[]byte)
	defer c.bufPool.Put(bp)
	switch {
	case ok:
		mac := dataMAC(&pr.dataKey, c.r8, pr.r8, payload)
		pkt := append((*bp)[:0], typeData)
		pkt = append(pkt, c.r8[:]...)
		pkt = append(pkt, mac[:]...)
		pkt = append(pkt, payload...)
		_, _ = c.sock.WriteToAddrPort(pkt, direct)
		pr.txDirect.Add(uint64(len(payload)))
	case relay != nil:
		// inner packet is a normal data packet, wrapped for the relay
		mac := dataMAC(&pr.dataKey, c.r8, pr.r8, payload)
		inner := make([]byte, 0, dataHeader+len(payload))
		inner = append(inner, typeData)
		inner = append(inner, c.r8[:]...)
		inner = append(inner, mac[:]...)
		inner = append(inner, payload...)
		if c.sendRelayed(relay, pr, inner, *bp) {
			pr.txRelay.Add(uint64(len(payload)))
		}
	default:
		c.Kick()
	}
}

// ---- crypto helpers ----

// virtualPrefix starts the stand-in IPv6 address QUIC sees for each member.
var virtualPrefix = [4]byte{0xfd, 0xc7, 0x5e, 0x57}

// IsVirtual reports whether addr is the stand-in address of a member (as opposed
// to the real address of an anonymous joiner).
func IsVirtual(addr net.Addr) bool {
	ua, ok := addr.(*net.UDPAddr)
	if !ok || len(ua.IP) != net.IPv6len {
		return false
	}
	return ua.IP[0] == virtualPrefix[0] && ua.IP[1] == virtualPrefix[1] && ua.IP[2] == virtualPrefix[2] && ua.IP[3] == virtualPrefix[3]
}

// isVirtualAddr is IsVirtual for a plain address.
func isVirtualAddr(a netip.Addr) bool {
	if !a.Is6() || a.Is4In6() {
		return false
	}
	b := a.As16()
	return b[0] == virtualPrefix[0] && b[1] == virtualPrefix[1] && b[2] == virtualPrefix[2] && b[3] == virtualPrefix[3]
}

func deriveMACKey(shared [32]byte) [32]byte { return deriveKey("themesh/relay-mac/v1", shared) }

func deriveKey(label string, shared [32]byte) [32]byte {
	h, _ := blake2s.New256(nil)
	h.Write([]byte(label))
	h.Write(shared[:])
	var out [32]byte
	h.Sum(out[:0])
	return out
}

// dataMAC authenticates one data packet from src to dst. Both route ids are part
// of the input although only the sender's travels in the header, so a packet
// cannot be reflected back at its sender or replayed to a third device.
func dataMAC(key *[32]byte, src, dst [8]byte, payload []byte) [8]byte {
	return mac8(key, []byte{typeData}, src[:], dst[:], payload)
}

func (c *Conn) dataMACOK(from *peer, mac [8]byte, payload []byte) bool {
	want := dataMAC(&from.dataKey, from.r8, c.r8, payload)
	return subtle.ConstantTimeCompare(want[:], mac[:]) == 1
}

// mac8 computes the truncated keyed hash protecting relay envelopes.
func mac8(key *[32]byte, parts ...[]byte) [8]byte {
	h, _ := blake2s.New256(key[:])
	for _, p := range parts {
		h.Write(p)
	}
	var full [32]byte
	h.Sum(full[:0])
	var out [8]byte
	copy(out[:], full[:8])
	return out
}

func normalize(a netip.AddrPort) netip.AddrPort {
	return netip.AddrPortFrom(a.Addr().Unmap(), a.Port())
}

// tokenBucket is a small rate limiter for work triggered by unauthenticated input.
type tokenBucket struct {
	mu     sync.Mutex
	rate   float64
	burst  float64
	tokens float64
	last   time.Time
}

func newTokenBucket(perSecond, burst float64) tokenBucket {
	return tokenBucket{rate: perSecond, burst: burst, tokens: burst, last: time.Now()}
}

func (b *tokenBucket) allow() bool {
	b.mu.Lock()
	defer b.mu.Unlock()
	now := time.Now()
	b.tokens += now.Sub(b.last).Seconds() * b.rate
	if b.tokens > b.burst {
		b.tokens = b.burst
	}
	b.last = now
	if b.tokens < 1 {
		return false
	}
	b.tokens--
	return true
}
