// Package netsim is a tiny in-process model of the Internet with NAT gateways.
//
// It exists so the hole-punching and relay logic can be tested
// deterministically, for every combination of NAT behaviours, without root
// privileges or real network namespaces. It is also what powers `themesh demo`,
// which runs several virtual devices (some "behind" NATs) inside one process.
//
// The model: a Network has a root Realm (the public Internet). A NAT connects
// a private Realm to its parent. Hosts live in realms and open UDP sockets that
// implement net.PacketConn.
package netsim

import (
	"errors"
	"fmt"
	"math/rand"
	"net"
	"net/netip"
	"os"
	"sync"
	"sync/atomic"
	"time"
)

// Mapping describes how a NAT picks the external port for outbound flows
// (RFC 4787 terminology).
type Mapping int

const (
	// EndpointIndependent: one external port per internal socket, reused for all destinations.
	EndpointIndependent Mapping = iota
	// AddressDependent: a new external port per destination IP.
	AddressDependent
	// AddressPortDependent: a new external port per destination IP:port ("symmetric").
	AddressPortDependent
)

// Filtering describes which inbound packets a NAT lets through to a mapping.
type Filtering int

const (
	// FilterOpen accepts packets from anyone (full cone).
	FilterOpen Filtering = iota
	// FilterAddress accepts packets from IPs the inside host has sent to.
	FilterAddress
	// FilterAddressPort accepts packets only from IP:port pairs the inside host has sent to.
	FilterAddressPort
)

// NATConfig selects the behaviour of a NAT.
type NATConfig struct {
	Mapping   Mapping
	Filtering Filtering
	// Hairpin lets inside hosts reach each other through the NAT's public address.
	Hairpin bool
	// Timeout is the idle lifetime of a mapping. Zero means 2 minutes.
	Timeout time.Duration
}

// Common NAT personalities.
var (
	FullCone       = NATConfig{Mapping: EndpointIndependent, Filtering: FilterOpen, Hairpin: true}
	RestrictedCone = NATConfig{Mapping: EndpointIndependent, Filtering: FilterAddress, Hairpin: true}
	PortRestricted = NATConfig{Mapping: EndpointIndependent, Filtering: FilterAddressPort, Hairpin: false}
	Symmetric      = NATConfig{Mapping: AddressPortDependent, Filtering: FilterAddressPort, Hairpin: false}
)

// Event is reported to taps for every packet the network handles.
type Event struct {
	From, To netip.AddrPort // as seen at the point of delivery or drop
	Size     int
	Dropped  string // non-empty reason if the packet was dropped
}

// Network is the whole simulated Internet.
type Network struct {
	mu       sync.Mutex
	root     *Realm
	loss     float64
	latency  time.Duration
	rng      *rand.Rand
	blocked  map[[2]netip.Addr]bool
	taps     []func(Event)
	packets  atomic.Int64
	nextPort uint16
}

// New creates an empty network containing only the public Internet realm.
func New() *Network {
	n := &Network{
		rng:      rand.New(rand.NewSource(1)),
		blocked:  map[[2]netip.Addr]bool{},
		nextPort: 20000,
	}
	n.root = &Realm{net: n, name: "internet", hosts: map[netip.Addr]*Host{}, nats: map[netip.Addr]*NAT{}}
	return n
}

// Internet returns the public realm.
func (n *Network) Internet() *Realm { return n.root }

// SetLoss sets the probability that any packet is silently dropped.
func (n *Network) SetLoss(p float64) { n.mu.Lock(); n.loss = p; n.mu.Unlock() }

// SetLatency sets a fixed one-way delay applied to every delivery.
func (n *Network) SetLatency(d time.Duration) { n.mu.Lock(); n.latency = d; n.mu.Unlock() }

// Block drops all traffic between the two host addresses (in both directions),
// as seen on the realm where they are hosts. Useful to simulate a cut link.
func (n *Network) Block(a, b netip.Addr, blocked bool) {
	n.mu.Lock()
	n.blocked[[2]netip.Addr{a, b}] = blocked
	n.blocked[[2]netip.Addr{b, a}] = blocked
	n.mu.Unlock()
}

// Tap registers an observer for every packet. It must not block.
func (n *Network) Tap(f func(Event)) { n.mu.Lock(); n.taps = append(n.taps, f); n.mu.Unlock() }

// Packets returns the number of packets handled so far.
func (n *Network) Packets() int64 { return n.packets.Load() }

// Realm is an addressing domain: the Internet or a NAT's private side.
type Realm struct {
	net     *Network
	name    string
	gateway *NAT // NAT that connects this realm to its parent (nil for the root)
	hosts   map[netip.Addr]*Host
	nats    map[netip.Addr]*NAT // NATs whose outside is this realm, by public IP
}

// NewHost adds a host with the given address to the realm.
func (r *Realm) NewHost(ip netip.Addr) *Host {
	r.net.mu.Lock()
	defer r.net.mu.Unlock()
	if _, dup := r.hosts[ip]; dup {
		panic("netsim: duplicate host " + ip.String())
	}
	h := &Host{realm: r, ip: ip, socks: map[uint16]*Socket{}}
	r.hosts[ip] = h
	return h
}

// NewNAT attaches a private realm to r behind the public address publicIP.
func (r *Realm) NewNAT(publicIP netip.Addr, cfg NATConfig) *NAT {
	r.net.mu.Lock()
	defer r.net.mu.Unlock()
	if cfg.Timeout == 0 {
		cfg.Timeout = 2 * time.Minute
	}
	nat := &NAT{
		cfg:      cfg,
		outer:    r,
		outerIP:  publicIP,
		byKey:    map[mapKey]*mapping{},
		byExtern: map[uint16]*mapping{},
	}
	nat.inner = &Realm{net: r.net, name: "lan-behind-" + publicIP.String(), gateway: nat,
		hosts: map[netip.Addr]*Host{}, nats: map[netip.Addr]*NAT{}}
	r.nats[publicIP] = nat
	return nat
}

// Host is a machine with an IP address that can open UDP sockets.
type Host struct {
	realm *Realm
	ip    netip.Addr
	socks map[uint16]*Socket // guarded by realm.net.mu
}

// Addr returns the host's IP address in its realm.
func (h *Host) Addr() netip.Addr { return h.ip }

// Addrs lists the host's interface addresses (a single one in this model).
func (h *Host) Addrs() []netip.Addr { return []netip.Addr{h.ip} }

// ListenPacket opens a UDP socket; port 0 picks an ephemeral port.
func (h *Host) ListenPacket(port uint16) (*Socket, error) {
	n := h.realm.net
	n.mu.Lock()
	defer n.mu.Unlock()
	if port == 0 {
		for i := 0; i < 20000; i++ {
			n.nextPort++
			if n.nextPort < 20000 {
				n.nextPort = 20000
			}
			if _, used := h.socks[n.nextPort]; !used {
				port = n.nextPort
				break
			}
		}
		if port == 0 {
			return nil, errors.New("netsim: no free ports")
		}
	} else if _, used := h.socks[port]; used {
		return nil, fmt.Errorf("netsim: %s:%d already in use", h.ip, port)
	}
	s := &Socket{
		host:  h,
		port:  port,
		in:    make(chan packet, 1024),
		wake:  make(chan struct{}, 1),
		close: make(chan struct{}),
	}
	h.socks[port] = s
	return s, nil
}

type packet struct {
	from netip.AddrPort
	data []byte
}

// Socket is a simulated UDP socket implementing net.PacketConn.
type Socket struct {
	host  *Host
	port  uint16
	in    chan packet
	wake  chan struct{}
	close chan struct{}
	once  sync.Once

	dmu   sync.Mutex
	rdl   time.Time
	Sent  atomic.Int64
	Recvd atomic.Int64
}

var _ net.PacketConn = (*Socket)(nil)

// LocalAddr returns the socket's address inside its realm.
func (s *Socket) LocalAddr() net.Addr {
	return net.UDPAddrFromAddrPort(netip.AddrPortFrom(s.host.ip, s.port))
}

// ReadFrom implements net.PacketConn.
func (s *Socket) ReadFrom(p []byte) (int, net.Addr, error) {
	for {
		s.dmu.Lock()
		dl := s.rdl
		s.dmu.Unlock()
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
		case pk := <-s.in:
			if t != nil {
				t.Stop()
			}
			s.Recvd.Add(1)
			n := copy(p, pk.data)
			return n, net.UDPAddrFromAddrPort(pk.from), nil
		case <-s.wake:
			if t != nil {
				t.Stop()
			}
		case <-timer:
			return 0, nil, os.ErrDeadlineExceeded
		case <-s.close:
			if t != nil {
				t.Stop()
			}
			return 0, nil, net.ErrClosed
		}
	}
}

// WriteTo implements net.PacketConn.
func (s *Socket) WriteTo(p []byte, addr net.Addr) (int, error) {
	select {
	case <-s.close:
		return 0, net.ErrClosed
	default:
	}
	ua, ok := addr.(*net.UDPAddr)
	if !ok {
		return 0, errors.New("netsim: need *net.UDPAddr")
	}
	dst := ua.AddrPort()
	dst = netip.AddrPortFrom(dst.Addr().Unmap(), dst.Port())
	data := append([]byte(nil), p...)
	s.Sent.Add(1)
	s.host.realm.net.send(s.host.realm, netip.AddrPortFrom(s.host.ip, s.port), dst, data)
	return len(p), nil
}

// Close implements net.PacketConn.
func (s *Socket) Close() error {
	s.once.Do(func() {
		close(s.close)
		n := s.host.realm.net
		n.mu.Lock()
		delete(s.host.socks, s.port)
		n.mu.Unlock()
	})
	return nil
}

// SetDeadline implements net.PacketConn.
func (s *Socket) SetDeadline(t time.Time) error { return s.SetReadDeadline(t) }

// SetReadDeadline implements net.PacketConn.
func (s *Socket) SetReadDeadline(t time.Time) error {
	s.dmu.Lock()
	s.rdl = t
	s.dmu.Unlock()
	select {
	case s.wake <- struct{}{}:
	default:
	}
	return nil
}

// SetWriteDeadline implements net.PacketConn; writes never block.
func (s *Socket) SetWriteDeadline(time.Time) error { return nil }

// SetReadBuffer is accepted and ignored (quic-go calls it on its conn).
func (s *Socket) SetReadBuffer(int) error { return nil }

// SetWriteBuffer is accepted and ignored.
func (s *Socket) SetWriteBuffer(int) error { return nil }

// ---- NAT ----

// NAT is a gateway between a private realm and its parent.
type NAT struct {
	cfg     NATConfig
	outer   *Realm
	inner   *Realm
	outerIP netip.Addr

	byKey    map[mapKey]*mapping
	byExtern map[uint16]*mapping
}

// Inner returns the private realm behind this NAT.
func (nat *NAT) Inner() *Realm { return nat.inner }

// PublicIP returns the NAT's address on the outside.
func (nat *NAT) PublicIP() netip.Addr { return nat.outerIP }

// SetConfig changes the NAT's behaviour at runtime (e.g. to simulate a roaming
// change). Existing mappings are kept.
func (nat *NAT) SetConfig(cfg NATConfig) {
	nat.outer.net.mu.Lock()
	defer nat.outer.net.mu.Unlock()
	if cfg.Timeout == 0 {
		cfg.Timeout = nat.cfg.Timeout
	}
	nat.cfg = cfg
}

// Flush forgets all mappings, like a router reboot or a NAT table timeout.
func (nat *NAT) Flush() {
	nat.outer.net.mu.Lock()
	defer nat.outer.net.mu.Unlock()
	nat.byKey = map[mapKey]*mapping{}
	nat.byExtern = map[uint16]*mapping{}
}

type mapKey struct {
	internal netip.AddrPort
	remote   netip.AddrPort // zero unless the mapping is destination dependent
	remoteIP netip.Addr
}

type mapping struct {
	internal netip.AddrPort
	external uint16
	last     time.Time
	addrs    map[netip.Addr]time.Time
	addrPort map[netip.AddrPort]time.Time
}

func (nat *NAT) keyFor(src, dst netip.AddrPort) mapKey {
	switch nat.cfg.Mapping {
	case AddressDependent:
		return mapKey{internal: src, remoteIP: dst.Addr()}
	case AddressPortDependent:
		return mapKey{internal: src, remote: dst}
	default:
		return mapKey{internal: src}
	}
}

// outbound translates a packet leaving the private realm. Called with net.mu held.
func (nat *NAT) outbound(src, dst netip.AddrPort, now time.Time) netip.AddrPort {
	k := nat.keyFor(src, dst)
	m := nat.byKey[k]
	if m != nil && now.Sub(m.last) > nat.cfg.Timeout {
		delete(nat.byKey, k)
		delete(nat.byExtern, m.external)
		m = nil
	}
	if m == nil {
		m = &mapping{internal: src, addrs: map[netip.Addr]time.Time{}, addrPort: map[netip.AddrPort]time.Time{}}
		m.external = nat.allocPort(src.Port())
		nat.byKey[k] = m
		nat.byExtern[m.external] = m
	}
	m.last = now
	m.addrs[dst.Addr()] = now
	m.addrPort[dst] = now
	return netip.AddrPortFrom(nat.outerIP, m.external)
}

// allocPort picks an external port. Endpoint-independent NATs try to preserve
// the internal port (like most real routers); others pick randomly. Called
// with net.mu held.
func (nat *NAT) allocPort(want uint16) uint16 {
	if nat.cfg.Mapping == EndpointIndependent && want != 0 {
		if _, used := nat.byExtern[want]; !used {
			return want
		}
	}
	for {
		p := uint16(30000 + nat.outer.net.rng.Intn(30000))
		if _, used := nat.byExtern[p]; !used {
			return p
		}
	}
}

// inbound translates a packet arriving at the NAT's public address. ok is
// false (with a reason) if no mapping exists or filtering rejects the packet.
func (nat *NAT) inbound(src, dst netip.AddrPort, now time.Time) (netip.AddrPort, string) {
	m := nat.byExtern[dst.Port()]
	if m == nil {
		return netip.AddrPort{}, "nat: no mapping"
	}
	if now.Sub(m.last) > nat.cfg.Timeout {
		return netip.AddrPort{}, "nat: mapping expired"
	}
	switch nat.cfg.Filtering {
	case FilterAddress:
		t, ok := m.addrs[src.Addr()]
		if !ok || now.Sub(t) > nat.cfg.Timeout {
			return netip.AddrPort{}, "nat: filtered (address)"
		}
	case FilterAddressPort:
		t, ok := m.addrPort[src]
		if !ok || now.Sub(t) > nat.cfg.Timeout {
			return netip.AddrPort{}, "nat: filtered (address+port)"
		}
	}
	return m.internal, ""
}

// ---- routing ----

func (n *Network) emit(ev Event) {
	n.packets.Add(1)
	for _, t := range n.taps {
		t(ev)
	}
}

// send routes one datagram from a socket in realm `from`.
func (n *Network) send(from *Realm, src, dst netip.AddrPort, data []byte) {
	n.mu.Lock()
	var deliver func()
	realm := from
	now := time.Now()
	origSrc := src
	var lastOut *NAT // the NAT the packet most recently left through
	var drop string
	for hops := 0; ; hops++ {
		if hops > 8 {
			drop = "routing loop"
			break
		}
		if n.blocked[[2]netip.Addr{src.Addr(), dst.Addr()}] {
			drop = "blocked"
			break
		}
		// Destination is a host in this realm.
		if h, ok := realm.hosts[dst.Addr()]; ok {
			if s, ok := h.socks[dst.Port()]; ok {
				if n.loss > 0 && n.rng.Float64() < n.loss {
					drop = "loss"
					break
				}
				pk := packet{from: src, data: data}
				lat := n.latency
				deliver = func() {
					select {
					case s.in <- pk:
					default: // receive buffer full: drop like UDP
					}
				}
				n.emit(Event{From: src, To: dst, Size: len(data)})
				n.mu.Unlock()
				if lat > 0 {
					time.AfterFunc(lat, deliver)
				} else {
					deliver()
				}
				return
			}
			drop = "no socket"
			break
		}
		// Destination is the public address of a NAT whose outside is this realm.
		if nat, ok := realm.nats[dst.Addr()]; ok {
			if lastOut == nat && !nat.cfg.Hairpin {
				// A host behind this NAT sent to the NAT's own public address.
				drop = "nat: no hairpin"
				break
			}
			internal, why := nat.inbound(src, dst, now)
			if why != "" {
				drop = why
				break
			}
			realm = nat.inner
			dst = internal
			continue
		}
		// Not local: leave through this realm's gateway, if any.
		if realm.gateway != nil {
			nat := realm.gateway
			src = nat.outbound(src, dst, now)
			realm = nat.outer
			lastOut = nat
			continue
		}
		drop = "no route"
		break
	}
	n.emit(Event{From: origSrc, To: dst, Size: len(data), Dropped: drop})
	n.mu.Unlock()
}
