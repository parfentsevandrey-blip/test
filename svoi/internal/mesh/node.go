// Package mesh turns the magic UDP layer into a private network of devices:
// authenticated QUIC links between every pair of members, a stream RPC for
// applications, membership gossip and the join handshake. It is the part that
// replaces Tailscale's coordination server - every member holds the full
// membership list and members introduce each other.
package mesh

import (
	"bytes"
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"math/rand"
	"net"
	"net/netip"
	"os"
	"path/filepath"
	"runtime"
	"sort"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/quic-go/quic-go"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/magic"
)

// Version of the application, reported to peers. Release builds override it
// with -ldflags "-X .../internal/mesh.Version=1.2.3".
var Version = "0.1.0"

// ALPN protocol names.
const (
	ALPNMesh = "themesh/1"
	ALPNJoin = "themesh-join/1"
	// (ALPNNearby, the request of a device nearby to be added, is in nearby.go.)
)

// QUIC application error codes used when closing connections.
const (
	closeNormal    quic.ApplicationErrorCode = 0
	closeDuplicate quic.ApplicationErrorCode = 0x10
	closeRevoked   quic.ApplicationErrorCode = 0x11
	closeShutdown  quic.ApplicationErrorCode = 0x12
	closeBadALPN   quic.ApplicationErrorCode = 0x13
)

// ErrNotConfigured is returned by operations that need a mesh.
var ErrNotConfigured = errors.New("mesh: this device is not part of a mesh yet")

// ErrNotAdmin is returned when a non-admin attempts an admin operation.
var ErrNotAdmin = errors.New("mesh: only an admin device can do this")

// Config configures a Node.
type Config struct {
	// Dir holds device.key, mesh.json and application data. Required.
	Dir string
	// DeviceName / Owner are defaults for CreateMesh and JoinMesh.
	DeviceName string
	Owner      string
	// UDPPort is the preferred UDP port (0 = pick and remember).
	UDPPort int
	// STUN servers; empty disables STUN.
	STUN []string
	// NoRelay stops this node from forwarding traffic for other members.
	NoRelay bool
	// PortMap asks the home router (UPnP / NAT-PMP) to forward our UDP port.
	PortMap bool
	Logger  *slog.Logger

	// Platform overrides the reported "os/arch" (used by the demo to show a
	// phone and a NAS in one process).
	Platform string
	// Loopback adds 127.0.0.1 to the advertised addresses: for running several
	// nodes on one machine (demos, tests).
	Loopback bool

	// Test and simulation hooks.
	Listen     func(port int) (net.PacketConn, error)
	LocalAddrs func() []netip.Addr
	Timing     magic.Timing
	// LANPort is the UDP port for LAN beacons (0 = default, <0 disables).
	LANPort int
	// NoNearby stops an admin device from announcing on the local network that it can add devices (see nearby.go);
	// it still adds them by invitation.
	NoNearby bool
	// Timers (zero = default).
	SyncEvery   time.Duration
	ConnectTick time.Duration
	PassiveWait time.Duration
	DialTimeout time.Duration
}

func (c *Config) fill() {
	if c.LocalAddrs == nil && c.Loopback {
		c.LocalAddrs = func() []netip.Addr {
			return append(magic.DefaultLocalAddrs(), netip.MustParseAddr("127.0.0.1"))
		}
	}
	if c.Logger == nil {
		c.Logger = slog.New(slog.NewTextHandler(io.Discard, nil))
	}
	if c.SyncEvery == 0 {
		c.SyncEvery = 45 * time.Second
	}
	if c.ConnectTick == 0 {
		c.ConnectTick = 500 * time.Millisecond
	}
	if c.PassiveWait == 0 {
		c.PassiveWait = 6 * time.Second
	}
	if c.DialTimeout == 0 {
		c.DialTimeout = 20 * time.Second
	}
	if c.DeviceName == "" {
		h, _ := os.Hostname()
		c.DeviceName = h
	}
}

// EventKind classifies node events.
type EventKind string

// Event kinds.
const (
	EvPeer    EventKind = "peer"    // a peer's state or info changed
	EvMembers EventKind = "members" // the member list changed
	EvSelf    EventKind = "self"    // own endpoints / NAT info changed
	EvRemoved EventKind = "removed" // an administrator removed this device from the mesh
	EvNearby  EventKind = "nearby"  // the devices nearby, a request to add one, or this device's own request changed
)

// Event is published on the node's event bus.
type Event struct {
	Kind EventKind
	Peer identity.ID
}

// Node is one device's membership in a mesh.
type Node struct {
	cfg   Config
	log   *slog.Logger
	devMu sync.RWMutex
	dev   *identity.Device // replaced when a removed device is given a fresh identity

	mu       sync.RWMutex
	root     *identity.Root
	auth     *identity.Authority
	self     *identity.Member
	meshName string
	peers    map[identity.ID]*Peer
	revoked  map[identity.ID]identity.Revocation
	invites  map[[8]byte]*invite

	// Networking; nil while not configured.
	magic   *magic.Conn
	tr      *quic.Transport
	ln      *quic.Listener
	tlsCert tls.Certificate
	udpPort int
	started time.Time
	ctx     context.Context
	cancel  context.CancelFunc
	wg      sync.WaitGroup
	lan     *lanDiscovery
	lanMu   sync.Mutex  // one start of the LAN discovery at a time
	nearby  nearbyState // devices nearby and requests (nearby.go)

	hmu            sync.RWMutex
	handlers       map[string]handlerEntry
	dgram          map[byte]DatagramHandler
	helloProviders map[string]func() any

	saveMu    sync.Mutex
	saveTimer *time.Timer

	busMu sync.Mutex
	subs  map[int]chan Event
	subID int

	kickDial   chan struct{}
	joinLimit  *joinLimiter
	joinActive atomic.Int32
	sniMu      sync.Mutex
	sniSeen    map[[16]byte]time.Time // join tokens already used (see joinsni.go)

	// relayOn is whether we forward traffic for other members; it can change
	// while the network is running (see SetRelay).
	relayOn atomic.Bool

	removing atomic.Bool // the removal-from-mesh sequence is running
}

// Open loads (or creates) the device identity and, if the device already
// belongs to a mesh, brings the network up.
func Open(cfg Config) (*Node, error) {
	cfg.fill()
	if cfg.Dir == "" {
		return nil, errors.New("mesh: Config.Dir is required")
	}
	if err := identity.EnsurePrivateDir(cfg.Dir); err != nil {
		return nil, err
	}
	dev, _, err := identity.LoadOrCreateDevice(filepath.Join(cfg.Dir, "device.key"))
	if err != nil {
		return nil, err
	}
	n := &Node{
		cfg:            cfg,
		log:            cfg.Logger,
		dev:            dev,
		peers:          map[identity.ID]*Peer{},
		revoked:        map[identity.ID]identity.Revocation{},
		invites:        map[[8]byte]*invite{},
		handlers:       map[string]handlerEntry{},
		dgram:          map[byte]DatagramHandler{},
		helloProviders: map[string]func() any{},
		subs:           map[int]chan Event{},
		kickDial:       make(chan struct{}, 1),
		joinLimit:      newJoinLimiter(),
	}
	n.registerCoreHandlers()
	if err := n.loadState(); err != nil {
		return nil, err
	}
	if n.root != nil {
		if err := n.startMember(); err != nil {
			return nil, err
		}
	} else {
		n.startIdleLAN()
	}
	return n, nil
}

// ID returns this device's identity.
func (n *Node) ID() identity.ID { return n.device().ID }

// Device returns the device key (for application-level signing).
func (n *Node) Device() *identity.Device { return n.device() }

func (n *Node) device() *identity.Device {
	n.devMu.RLock()
	defer n.devMu.RUnlock()
	return n.dev
}

// Dir returns the state directory.
func (n *Node) Dir() string { return n.cfg.Dir }

// Logger returns the node's logger.
func (n *Node) Logger() *slog.Logger { return n.log }

// Configured reports whether the device belongs to a mesh.
func (n *Node) Configured() bool {
	n.mu.RLock()
	defer n.mu.RUnlock()
	return n.root != nil
}

// IsAdmin reports whether this device holds the mesh authority.
func (n *Node) IsAdmin() bool {
	n.mu.RLock()
	defer n.mu.RUnlock()
	return n.auth != nil
}

// Root returns the mesh root (nil if not configured).
func (n *Node) Root() *identity.Root {
	n.mu.RLock()
	defer n.mu.RUnlock()
	return n.root
}

// Magic exposes the UDP layer for diagnostics.
func (n *Node) Magic() *magic.Conn {
	n.mu.RLock()
	defer n.mu.RUnlock()
	return n.magic
}

// CreateMesh founds a new mesh with this device as its first admin.
func (n *Node) CreateMesh(meshName, deviceName, owner string) error {
	n.mu.Lock()
	if n.root != nil {
		n.mu.Unlock()
		return errors.New("mesh: already part of a mesh; leave it first")
	}
	if deviceName == "" {
		deviceName = n.cfg.DeviceName
	}
	if owner == "" {
		owner = n.cfg.Owner
	}
	auth, err := identity.NewAuthority(meshName)
	if err != nil {
		n.mu.Unlock()
		return err
	}
	self, err := auth.Issue(identity.IssueRequest{ID: n.device().ID, Name: deviceName, Owner: owner, Admin: true}, nil)
	if err != nil {
		n.mu.Unlock()
		return err
	}
	n.auth, n.root, n.self, n.meshName = auth, auth.Root, self, strings.TrimSpace(meshName)
	if n.meshName == "" {
		n.meshName = "mesh"
	}
	n.mu.Unlock()
	if err := n.saveState(); err != nil {
		return err
	}
	return n.startMember()
}

// Reconfigure changes network settings (STUN servers, relay, UDP port, LAN
// discovery) and restarts the network part of the node so they take effect.
func (n *Node) Reconfigure(mod func(*Config)) error {
	n.stopNetwork()
	n.mu.Lock()
	mod(&n.cfg)
	configured := n.root != nil
	n.mu.Unlock()
	if configured {
		return n.startMember()
	}
	n.startIdleLAN()
	return nil
}

// SetRelay switches relaying for other members on or off without restarting the
// network (links stay up). Other members learn about it with the next state
// gossip, which is triggered right away.
func (n *Node) SetRelay(on bool) {
	n.mu.Lock()
	n.cfg.NoRelay = !on
	mg := n.magic
	n.mu.Unlock()
	n.relayOn.Store(on)
	if mg != nil {
		mg.StateChanged()
		go n.pushSyncToAll() // refresh the capabilities (hello) other members show
	}
	n.emit(Event{Kind: EvSelf})
}

// Leave removes this device from the mesh (locally) and stops the network.
// The device key is kept, so the device can join again with a new invite.
func (n *Node) Leave() error {
	n.stopNetwork()
	n.mu.Lock()
	n.root, n.auth, n.self, n.meshName = nil, nil, nil, ""
	n.peers = map[identity.ID]*Peer{}
	n.revoked = map[identity.ID]identity.Revocation{}
	n.invites = map[[8]byte]*invite{}
	n.mu.Unlock()
	n.nearby.mu.Lock()
	n.nearby.join = nil // how an earlier request of this device ended is of no interest to the next one
	n.nearby.mu.Unlock()
	if err := os.Remove(n.statePath()); err != nil && !errors.Is(err, os.ErrNotExist) {
		return err
	}
	n.startIdleLAN() // not in a mesh any more: listen for the devices nearby that could add this one
	n.emit(Event{Kind: EvMembers})
	return nil
}

// Close shuts the node down.
func (n *Node) Close() error {
	n.saveMu.Lock()
	if n.saveTimer != nil {
		n.saveTimer.Stop()
		n.saveTimer = nil
	}
	n.saveMu.Unlock()
	_ = n.saveState()
	n.stopNetwork()
	return nil
}

// ---- lifecycle of the network part ----

func (n *Node) udpPortOrDefault() int {
	if n.cfg.UDPPort != 0 {
		return n.cfg.UDPPort
	}
	// Remember the port we got last time so port forwards keep working.
	if b, err := os.ReadFile(filepath.Join(n.cfg.Dir, "udp.port")); err == nil {
		var p int
		if _, err := fmt.Sscanf(strings.TrimSpace(string(b)), "%d", &p); err == nil && p > 0 && p < 65536 {
			return p
		}
	}
	return 41710
}

func (n *Node) startMember() error {
	_ = os.Remove(n.removedPath())
	n.mu.Lock()
	if n.magic != nil {
		n.mu.Unlock()
		return nil
	}
	self := n.self
	n.tlsCert = tls.Certificate{Certificate: [][]byte{self.CertDER}, PrivateKey: n.device().Priv}
	n.ctx, n.cancel = context.WithCancel(context.Background())
	ctx := n.ctx
	n.started = time.Now()
	n.mu.Unlock()

	listen := n.cfg.Listen
	port := n.udpPortOrDefault()
	mg, err := n.openMagic(port, listen)
	if err != nil && n.cfg.UDPPort == 0 && port != 0 {
		n.log.Warn("preferred UDP port is busy, picking another", "port", port, "err", err)
		mg, err = n.openMagic(0, listen)
	}
	if err != nil {
		n.cancel()
		return err
	}
	n.udpPort = mg.Port()
	_ = os.WriteFile(filepath.Join(n.cfg.Dir, "udp.port"), []byte(fmt.Sprintf("%d\n", n.udpPort)), 0o600)

	// Members are authenticated per packet (see magic); whoever else reaches the
	// QUIC listener is a joiner and must prove it can receive at its source
	// address (Retry) before any handshake state is kept or any large answer is
	// sent, so forged Initials cost us nothing and cannot be used for reflection.
	tr := &quic.Transport{Conn: mg, VerifySourceAddress: func(a net.Addr) bool { return !magic.IsVirtual(a) }, StatelessResetKey: n.statelessResetKey()}
	ln, err := tr.Listen(n.serverTLS(), n.quicConf())
	if err != nil {
		_ = tr.Close()
		_ = mg.Close()
		n.cancel()
		return err
	}
	n.mu.Lock()
	n.magic, n.tr, n.ln = mg, tr, ln
	peers := make([]*Peer, 0, len(n.peers))
	for _, p := range n.peers {
		peers = append(peers, p)
	}
	n.mu.Unlock()

	for _, p := range peers {
		if err := mg.AddPeer(p.ID); err != nil {
			n.log.Warn("cannot register peer", "peer", p.ID.Short(), "err", err)
			continue
		}
		var eps []netip.AddrPort
		for _, s := range p.storedEndpoints {
			if ap, err := netip.ParseAddrPort(s); err == nil {
				eps = append(eps, ap)
			}
		}
		mg.AddCandidates(p.ID, eps, magic.SrcStored)
	}
	n.updateAnonymous()

	n.wg.Add(3)
	go n.acceptLoop(ctx, ln)
	go n.dialLoop(ctx)
	go n.housekeepingLoop(ctx)
	if n.cfg.LANPort >= 0 {
		n.startLAN(ctx)
	}
	n.log.Info("mesh network started", "name", self.Name, "udp", n.udpPort, "peers", len(peers))
	n.emit(Event{Kind: EvMembers})
	return nil
}

func (n *Node) openMagic(port int, listen func(int) (net.PacketConn, error)) (*magic.Conn, error) {
	n.relayOn.Store(!n.cfg.NoRelay)
	return magic.New(magic.Config{
		Device:     n.device(),
		Port:       port,
		Listen:     listen,
		LocalAddrs: n.cfg.LocalAddrs,
		STUN:       n.cfg.STUN,
		PortMap:    n.cfg.PortMap,
		SelfCert: func() []byte {
			n.mu.RLock()
			defer n.mu.RUnlock()
			if n.self == nil { // the device left the mesh while a packet was being built
				return nil
			}
			return n.self.CertDER
		},
		AcceptUnknown:   n.acceptUnknown,
		AllowRelay:      n.relayOn.Load,
		OnPath:          n.onPath,
		OnEndpoints:     func([]magic.Endpoint) { n.emit(Event{Kind: EvSelf}) },
		OnPeerEndpoints: func(identity.ID, []netip.AddrPort) {},
		Logf:            func(f string, a ...any) { n.log.Debug(fmt.Sprintf(f, a...)) },
		Timing:          n.cfg.Timing,
	})
}

func (n *Node) stopNetwork() {
	n.mu.Lock()
	cancel, mg, tr, ln, lan := n.cancel, n.magic, n.tr, n.ln, n.lan
	peers := make([]*Peer, 0, len(n.peers))
	for _, p := range n.peers {
		peers = append(peers, p)
	}
	n.magic, n.tr, n.ln, n.lan, n.cancel = nil, nil, nil, nil, nil
	n.mu.Unlock()
	if lan != nil {
		lan.stop() // (a device that is not in a mesh listens too, with no network running)
	}
	if cancel == nil {
		return
	}
	cancel()
	for _, p := range peers {
		if c := p.currentConn(); c != nil {
			_ = c.CloseWithError(closeShutdown, "shutting down")
		}
	}
	if ln != nil {
		_ = ln.Close()
	}
	if tr != nil {
		_ = tr.Close()
	}
	if mg != nil {
		_ = mg.Close()
	}
	n.wg.Wait()
}

// ---- TLS / QUIC configuration ----

func (n *Node) quicConf() *quic.Config {
	member := &quic.Config{
		HandshakeIdleTimeout:           12 * time.Second,
		MaxIdleTimeout:                 40 * time.Second,
		KeepAlivePeriod:                12 * time.Second,
		MaxIncomingStreams:             512,
		MaxIncomingUniStreams:          16,
		EnableDatagrams:                true,
		InitialStreamReceiveWindow:     1 << 20,
		MaxStreamReceiveWindow:         16 << 20,
		InitialConnectionReceiveWindow: 4 << 20,
		MaxConnectionReceiveWindow:     64 << 20,
	}
	// A connection that comes from a real address is not a member (members arrive from
	// stand-in addresses): it is somebody joining, who sends one request of a few
	// hundred bytes (see maxJoinFrame) and reads one answer. Such a connection gets
	// small windows and a few streams, so a holder of an invitation cannot make the node
	// keep megabytes of buffers or hundreds of streams for it while it waits for the request.
	joiner := *member
	joiner.GetConfigForClient = nil
	joiner.MaxIncomingStreams = 2
	joiner.MaxIncomingUniStreams = -1 // none
	joiner.EnableDatagrams = false
	joiner.KeepAlivePeriod = 0
	joiner.MaxIdleTimeout = 20 * time.Second
	joiner.InitialStreamReceiveWindow = 16 << 10
	joiner.MaxStreamReceiveWindow = 32 << 10
	joiner.InitialConnectionReceiveWindow = 32 << 10
	joiner.MaxConnectionReceiveWindow = 64 << 10
	member.GetConfigForClient = func(ci *quic.ClientInfo) (*quic.Config, error) {
		if ci != nil && !magic.IsVirtual(ci.RemoteAddr) {
			return &joiner, nil
		}
		plain := *member // (nil would mean quic-go's defaults, not this configuration)
		plain.GetConfigForClient = nil
		return &plain, nil
	}
	return member
}

// verifyLeaf validates a presented member certificate against the mesh root and
// the revocation list.
func (n *Node) verifyLeaf(der []byte) (*identity.Member, error) {
	n.mu.RLock()
	root := n.root
	n.mu.RUnlock()
	if root == nil {
		return nil, ErrNotConfigured
	}
	m, err := root.Verify(der)
	if err != nil {
		return nil, err
	}
	n.mu.RLock()
	_, revoked := n.revoked[m.ID]
	n.mu.RUnlock()
	if revoked {
		return nil, errors.New("mesh: device has been revoked")
	}
	return m, nil
}

func (n *Node) serverTLS() *tls.Config {
	mesh := &tls.Config{
		MinVersion:   tls.VersionTLS13,
		Certificates: []tls.Certificate{n.tlsCert},
		ClientAuth:   tls.RequireAnyClientCert,
		NextProtos:   []string{ALPNMesh},
		VerifyPeerCertificate: func(raw [][]byte, _ [][]*x509.Certificate) error {
			if len(raw) == 0 {
				return errors.New("no client certificate")
			}
			_, err := n.verifyLeaf(raw[0])
			return err
		},
	}
	join := &tls.Config{
		MinVersion: tls.VersionTLS13,
		// Not the member certificate: a device that is not a member yet gets to know
		// only the key it was told to expect (see joinTLS).
		Certificates: []tls.Certificate{selfSignedCert(n.device())},
		ClientAuth:   tls.RequireAnyClientCert, // self-signed: the invite secret authenticates the joiner
		NextProtos:   []string{ALPNJoin},
	}
	nearby := n.nearbyTLS()
	return &tls.Config{
		MinVersion:   tls.VersionTLS13,
		Certificates: []tls.Certificate{n.tlsCert},
		NextProtos:   []string{ALPNMesh, ALPNJoin, ALPNNearby},
		GetConfigForClient: func(chi *tls.ClientHelloInfo) (*tls.Config, error) {
			var remote net.Addr
			if chi.Conn != nil {
				remote = chi.Conn.RemoteAddr()
			}
			for _, p := range chi.SupportedProtos {
				if p == ALPNNearby {
					// A device nearby asks to be added. It must show a ticket from an announcement of ours
					// (so it is a neighbour that heard one a moment ago), and we must be willing: a person
					// at this device decides about the request, not the network.
					if !n.nearbyOpen() || !n.checkNearbySNI(chi.ServerName) || !n.joinLimit.allow(remoteAddr(remote)) {
						return nil, errors.New("not accepting new devices")
					}
					return nearby, nil
				}
				if p == ALPNJoin {
					// A joiner has to show, in the server name, that it knows the secret of a
					// pending invitation; only then does it count against the (per address and
					// overall) join budget. Strangers get nothing: no certificate, no state.
					if !n.joinOpen() || !n.checkJoinSNI(chi.ServerName) || !n.joinLimit.allow(remoteAddr(remote)) {
						return nil, errors.New("not accepting new devices")
					}
					return join, nil
				}
			}
			// The member protocol. Members come in over the authenticated path, which hands QUIC
			// their stand-in addresses; whatever arrives from a real address is a stranger and
			// is not shown our certificate (name, owner, overlay addresses).
			if remote == nil || !magic.IsVirtual(remote) {
				return nil, errors.New("not a member")
			}
			return mesh, nil
		},
	}
}

func (n *Node) clientTLS(expect identity.ID) *tls.Config {
	return &tls.Config{
		MinVersion: tls.VersionTLS13,
		ServerName: "themesh",
		// The certificate is verified below against the mesh root and the expected
		// device; the standard web PKI checks do not apply to this network.
		InsecureSkipVerify: true,
		Certificates:       []tls.Certificate{n.tlsCert},
		NextProtos:         []string{ALPNMesh},
		VerifyPeerCertificate: func(raw [][]byte, _ [][]*x509.Certificate) error {
			if len(raw) == 0 {
				return errors.New("no server certificate")
			}
			m, err := n.verifyLeaf(raw[0])
			if err != nil {
				return err
			}
			if m.ID != expect {
				return fmt.Errorf("connected to %s but expected %s", m.ID.Short(), expect.Short())
			}
			return nil
		},
	}
}

// ---- accepting and dialing ----

func (n *Node) acceptLoop(ctx context.Context, ln *quic.Listener) {
	defer n.wg.Done()
	for {
		conn, err := ln.Accept(ctx)
		if err != nil {
			if ctx.Err() != nil {
				return
			}
			n.log.Debug("accept failed", "err", err)
			select {
			case <-time.After(100 * time.Millisecond):
			case <-ctx.Done():
				return
			}
			continue
		}
		go n.handleIncoming(conn)
	}
}

func (n *Node) handleIncoming(conn *quic.Conn) {
	cs := conn.ConnectionState().TLS
	switch cs.NegotiatedProtocol {
	case ALPNJoin:
		n.handleJoin(conn)
	case ALPNNearby:
		n.handleNearby(conn)
	case ALPNMesh:
		if len(cs.PeerCertificates) == 0 {
			_ = conn.CloseWithError(closeBadALPN, "no certificate")
			return
		}
		m, err := n.verifyLeaf(cs.PeerCertificates[0].Raw)
		if err != nil {
			_ = conn.CloseWithError(closeRevoked, err.Error())
			return
		}
		p := n.learnMember(m)
		if p == nil {
			_ = conn.CloseWithError(closeRevoked, "unknown member")
			return
		}
		n.attach(p, conn, false)
	default:
		_ = conn.CloseWithError(closeBadALPN, "unsupported protocol")
	}
}

func (n *Node) dialLoop(ctx context.Context) {
	defer n.wg.Done()
	t := time.NewTicker(n.cfg.ConnectTick)
	defer t.Stop()
	for {
		select {
		case <-t.C:
		case <-n.kickDial:
		case <-ctx.Done():
			return
		}
		for _, p := range n.peerList() {
			n.maybeDial(p)
		}
	}
}

func (n *Node) kick() {
	select {
	case n.kickDial <- struct{}{}:
	default:
	}
}

func (n *Node) maybeDial(p *Peer) {
	now := time.Now()
	p.mu.Lock()
	if p.conn != nil || p.dialing || now.Before(p.nextDial) {
		p.mu.Unlock()
		return
	}
	if p.wantSince.IsZero() {
		p.wantSince = now
	}
	designated := bytes.Compare(n.device().ID[:], p.ID[:]) < 0
	if !designated && now.Sub(p.wantSince) < n.cfg.PassiveWait {
		p.mu.Unlock()
		if mg := n.Magic(); mg != nil {
			mg.Poke(p.ID) // keep probing so the other side's dial can get through
		}
		return
	}
	p.dialing = true
	p.mu.Unlock()
	go n.dial(p)
}

func (n *Node) dial(p *Peer) {
	n.mu.RLock()
	mg, tr, ctx := n.magic, n.tr, n.ctx
	n.mu.RUnlock()
	if mg == nil || tr == nil {
		return
	}
	dctx, cancel := context.WithTimeout(ctx, n.cfg.DialTimeout)
	p.mu.Lock()
	p.cancelDial = cancel
	p.mu.Unlock()
	defer cancel()

	mg.Poke(p.ID)
	vaddr, ok := mg.VirtualAddr(p.ID)
	if !ok {
		p.mu.Lock()
		p.dialing = false
		p.mu.Unlock()
		return
	}
	conn, err := tr.Dial(dctx, vaddr, n.clientTLS(p.ID), n.quicConf())
	p.mu.Lock()
	p.dialing = false
	p.cancelDial = nil
	if err != nil {
		p.failures++
		backoff := time.Second << min(p.failures, 6)
		if backoff > 60*time.Second {
			backoff = 60 * time.Second
		}
		backoff += time.Duration(rand.Int63n(int64(backoff) / 4))
		if p.restartDial {
			p.restartDial = false
			backoff = 0
		}
		p.nextDial = time.Now().Add(backoff)
		p.lastErr = err.Error()
		p.mu.Unlock()
		n.log.Debug("dial failed", "peer", p.Name(), "err", err)
		if backoff == 0 {
			n.kick()
		}
		return
	}
	p.lastErr = ""
	p.mu.Unlock()
	n.attach(p, conn, true)
}

// ReplaceLink dials the peer again and puts the new link in place of the one in use, which is closed as
// superseded: what happens by itself when two devices dial each other at the same moment (or when a
// device that was restarted dials a link it did not know it had). It is here for the tests of the layers
// above (see meshtest), which need to see what a transfer does when its link is replaced under it.
// Whether the new link is the one that stays is decided by keepNewConn, so call it on the device whose
// dial wins (the one that dialled the old link, or the one with the lower ID).
func (n *Node) ReplaceLink(ctx context.Context, id identity.ID) error {
	p := n.Peer(id)
	if p == nil {
		return Errf(CodeNotFound, "no such device")
	}
	n.mu.RLock()
	mg, tr := n.magic, n.tr
	n.mu.RUnlock()
	if mg == nil || tr == nil {
		return Errf(CodeOffline, "the node is not running")
	}
	vaddr, ok := mg.VirtualAddr(p.ID)
	if !ok {
		return Errf(CodeOffline, "%s has no path yet", p.Name())
	}
	conn, err := tr.Dial(ctx, vaddr, n.clientTLS(p.ID), n.quicConf())
	if err != nil {
		return err
	}
	n.attach(p, conn, true)
	return nil
}

// statelessResetKey lets a restarted node answer a packet of a link it no longer knows with a
// "stateless reset" (RFC 9000, section 10.3) that the other end accepts as genuine: the key, and
// so the reset tokens the old incarnation handed out, is derived from the device key, which
// survives the restart (and which only this device has, so nobody else can reset its links).
// The other end then drops the dead link at its next packet instead of after 40 s of silence.
func (n *Node) statelessResetKey() *quic.StatelessResetKey {
	m := hmac.New(sha256.New, n.device().Priv.Seed())
	m.Write([]byte("themesh/quic-stateless-reset/v1"))
	var k quic.StatelessResetKey
	copy(k[:], m.Sum(nil))
	return &k
}

func minID(a, b identity.ID) identity.ID {
	if bytes.Compare(a[:], b[:]) < 0 {
		return a
	}
	return b
}

// crossingDials is how long two dials that pass each other on the wire can take. A connection
// that has been up longer than this is not one half of such a pair (both handshakes of a crossing pair
// finish within about a round trip of each other).
const crossingDials = 5 * time.Second

// keepNewConn decides between the link we have and a second one that has just come up with the
// same peer. Two cases look alike from here:
//   - both devices dialled each other at the same moment: exactly one link must survive on both
//     ends, so the connection started by the device with the lower ID wins;
//   - the peer restarted (or its path died without a word) and dials again while we still hold
//     the old link, which nobody will close for another 40 seconds of silence: the newcomer is
//     the live one. Turning it away as a "duplicate" kept a restarted device offline for as long.
//
// Only a young link can be half of a crossing pair; an older one yields to the newcomer.
func keepNewConn(self, peer, oldDialer, newDialer identity.ID, oldAge time.Duration) bool {
	if oldDialer == newDialer { // the same side dialled again: it has lost the old link
		return true
	}
	if oldAge > crossingDials {
		return true
	}
	return newDialer == minID(self, peer)
}

// attach installs conn as the peer's link, resolving simultaneous dials (see keepNewConn).
func (n *Node) attach(p *Peer, conn *quic.Conn, outbound bool) {
	dialer := p.ID
	if outbound {
		dialer = n.device().ID
	}
	p.mu.Lock()
	old := p.conn
	if old != nil {
		keepNew := keepNewConn(n.device().ID, p.ID, p.connDialer, dialer, time.Since(p.connectedAt))
		if !keepNew {
			p.mu.Unlock()
			_ = conn.CloseWithError(closeDuplicate, "duplicate connection")
			return
		}
	}
	p.conn = conn
	p.connDialer = dialer
	p.connectedAt = time.Now()
	p.failures = 0
	p.wantSince = time.Time{}
	p.lastSeen.Store(time.Now().Unix())
	p.mu.Unlock()
	if old != nil {
		_ = old.CloseWithError(closeDuplicate, "superseded")
	}
	n.log.Info("peer connected", "peer", p.Name(), "outbound", outbound)
	go n.serveConn(p, conn)
	go n.serveDatagrams(p, conn)
	go func() {
		<-conn.Context().Done()
		n.detach(p, conn)
	}()
	n.emit(Event{Kind: EvPeer, Peer: p.ID})
	go n.syncWith(p)
}

func (n *Node) detach(p *Peer, conn *quic.Conn) {
	p.mu.Lock()
	if p.conn != conn {
		p.mu.Unlock()
		return
	}
	p.conn = nil
	p.lastSeen.Store(time.Now().Unix())
	p.nextDial = time.Now().Add(time.Second)
	p.wantSince = time.Now()
	p.mu.Unlock()
	n.log.Info("peer disconnected", "peer", p.Name())
	n.emit(Event{Kind: EvPeer, Peer: p.ID})
	n.saveSoon()
}

// onPath is called by magic when the path to a peer changes.
func (n *Node) onPath(id identity.ID, info magic.PathInfo) {
	p := n.Peer(id)
	if p == nil {
		return
	}
	p.mu.Lock()
	p.path = info
	n.log.Debug("path changed", "peer", p.member.Name, "kind", info.Kind.String(), "addr", info.Addr.String(), "relay", info.Relay.Short(), "rtt", info.RTT.String())
	reachable := info.Kind != magic.PathNone
	if reachable && p.conn == nil {
		// A path just appeared: dial now instead of waiting out a backoff or
		// the remainder of a handshake that was running into the void.
		p.nextDial = time.Now()
		if p.dialing && p.cancelDial != nil {
			p.restartDial = true
			p.cancelDial()
		}
	}
	p.mu.Unlock()
	n.emit(Event{Kind: EvPeer, Peer: id})
	if reachable {
		n.kick()
	}
}

// acceptUnknown is magic's callback for a sender it has not met: accept it if
// it carries a valid certificate for this mesh.
func (n *Node) acceptUnknown(id identity.ID, cert []byte) bool {
	m, err := n.verifyLeaf(cert)
	if err != nil || m.ID != id {
		return false
	}
	return n.learnMember(m) != nil
}

// housekeepingLoop does periodic chores: syncing with peers, expiring invites.
func (n *Node) housekeepingLoop(ctx context.Context) {
	defer n.wg.Done()
	t := time.NewTicker(n.cfg.SyncEvery)
	defer t.Stop()
	for {
		select {
		case <-t.C:
			n.expireInvites()
			for _, p := range n.peerList() {
				if p.Online() {
					go n.syncWith(p)
				}
			}
			n.saveSoon()
		case <-ctx.Done():
			return
		}
	}
}

// ---- membership ----

func (n *Node) peerList() []*Peer {
	n.mu.RLock()
	defer n.mu.RUnlock()
	out := make([]*Peer, 0, len(n.peers))
	for _, p := range n.peers {
		out = append(out, p)
	}
	return out
}

// Peer returns the peer with the given ID, or nil.
func (n *Node) Peer(id identity.ID) *Peer {
	n.mu.RLock()
	defer n.mu.RUnlock()
	return n.peers[id]
}

// Peers returns all known members except this device, sorted by name.
func (n *Node) Peers() []*Peer {
	out := n.peerList()
	sort.Slice(out, func(i, j int) bool { return out[i].Name() < out[j].Name() })
	return out
}

// FindPeer resolves a name ("nas", "nas.mesh", an ID or ID prefix, an overlay IP).
func (n *Node) FindPeer(q string) *Peer {
	q = strings.TrimSpace(strings.ToLower(q))
	q = strings.TrimSuffix(q, "."+identity.DNSSuffix)
	if q == "" {
		return nil
	}
	var byPrefix *Peer
	for _, p := range n.peerList() {
		m := p.Member()
		if strings.ToLower(p.Name()) == q || m.Name == q || strings.ToLower(p.alias) == q {
			return p
		}
		if m.IPv4.String() == q || m.IPv6.String() == q {
			return p
		}
		if id := p.ID.String(); id == q || (len(q) >= 6 && strings.HasPrefix(id, q)) {
			byPrefix = p
		}
	}
	return byPrefix
}

// PeerByIP maps an overlay address back to a peer.
func (n *Node) PeerByIP(a netip.Addr) *Peer {
	a = a.Unmap()
	for _, p := range n.peerList() {
		m := p.Member()
		if m.IPv4 == a || m.IPv6 == a {
			return p
		}
	}
	return nil
}

// learnMember registers a verified member certificate. It returns the peer
// (nil for ourselves or a revoked device).
func (n *Node) learnMember(m *identity.Member) *Peer {
	n.mu.Lock()
	if n.root == nil { // a handler that started before Leave must not bring a member back into a mesh this device left
		n.mu.Unlock()
		return nil
	}
	if _, rev := n.revoked[m.ID]; rev {
		n.mu.Unlock()
		return nil
	}
	if m.ID == n.device().ID {
		changed := false
		if m.Newer(n.self) {
			n.self = m
			n.tlsCert = tls.Certificate{Certificate: [][]byte{m.CertDER}, PrivateKey: n.device().Priv}
			changed = true
		}
		n.mu.Unlock()
		if changed {
			n.saveNow()
			n.emit(Event{Kind: EvSelf})
		}
		return nil
	}
	p := n.peers[m.ID]
	isNew := p == nil
	if isNew {
		p = newPeer(n, m)
		n.peers[m.ID] = p
	} else {
		p.mu.Lock()
		if m.Newer(p.member) {
			p.member = m
		}
		p.mu.Unlock()
	}
	mg := n.magic
	n.mu.Unlock()
	if isNew {
		if mg != nil {
			if err := mg.AddPeer(m.ID); err != nil {
				n.log.Warn("cannot register member", "peer", m.ID.Short(), "err", err)
			}
		}
		n.log.Info("new member", "name", m.Name, "id", m.ID.Short())
		n.saveNow()
		n.emit(Event{Kind: EvMembers})
		n.kick()
	}
	return p
}

// applyRevocation records a verified revocation and drops the device.
func (n *Node) applyRevocation(rv identity.Revocation) bool {
	n.mu.Lock()
	if n.root == nil || n.root.VerifyRevocation(rv) != nil {
		n.mu.Unlock()
		return false
	}
	if _, dup := n.revoked[rv.ID]; dup {
		n.mu.Unlock()
		return false
	}
	if rv.ID == n.device().ID {
		n.mu.Unlock()
		n.removedFromMesh()
		return false
	}
	n.revoked[rv.ID] = rv
	p := n.peers[rv.ID]
	delete(n.peers, rv.ID)
	mg := n.magic
	n.mu.Unlock()
	if p != nil {
		if c := p.currentConn(); c != nil {
			_ = c.CloseWithError(closeRevoked, "revoked")
		}
	}
	if mg != nil {
		mg.RemovePeer(rv.ID)
	}
	n.saveNow()
	n.emit(Event{Kind: EvMembers})
	return true
}

// RemovedInfo records that an administrator removed this device from a mesh.
type RemovedInfo struct {
	MeshName string `json:"meshName"`
	At       int64  `json:"at"`
}

func (n *Node) removedPath() string { return filepath.Join(n.cfg.Dir, "removed.json") }

// Removed reports that an administrator removed this device from a mesh and it
// has not joined another one since (nil otherwise).
func (n *Node) Removed() *RemovedInfo {
	if n.Configured() {
		return nil
	}
	raw, err := os.ReadFile(n.removedPath())
	if err != nil {
		return nil
	}
	var ri RemovedInfo
	if json.Unmarshal(raw, &ri) != nil {
		return nil
	}
	return &ri
}

// removedFromMesh handles a valid revocation of this very device: remember it,
// tell the application, forget the mesh and take a fresh identity (the old key
// stays revoked everywhere, so it could never join again).
func (n *Node) removedFromMesh() {
	if !n.removing.CompareAndSwap(false, true) {
		return
	}
	n.mu.RLock()
	name := n.meshName
	n.mu.RUnlock()
	n.log.Warn("an administrator removed this device from the mesh", "mesh", name)
	if raw, err := json.Marshal(RemovedInfo{MeshName: name, At: time.Now().Unix()}); err == nil {
		_ = identity.WriteFileAtomic(n.removedPath(), raw, 0o600)
	}
	n.emit(Event{Kind: EvRemoved})
	go func() {
		defer n.removing.Store(false)
		time.Sleep(300 * time.Millisecond) // let the call that brought the news be answered
		if err := n.Leave(); err != nil {
			n.log.Warn("leaving the mesh failed", "err", err)
		}
		if err := n.rotateDevice(); err != nil {
			n.log.Warn("cannot create a new device identity", "err", err)
		}
		n.emit(Event{Kind: EvMembers})
	}()
}

// rotateDevice replaces the device key with a new one.
func (n *Node) rotateDevice() error {
	path := filepath.Join(n.cfg.Dir, "device.key")
	if err := os.Remove(path); err != nil && !errors.Is(err, os.ErrNotExist) {
		return err
	}
	dev, _, err := identity.LoadOrCreateDevice(path)
	if err != nil {
		return err
	}
	n.devMu.Lock()
	n.dev = dev
	n.devMu.Unlock()
	return nil
}

// Revoke withdraws a device from the mesh (admin only) and tells everyone.
func (n *Node) Revoke(id identity.ID) error {
	n.mu.RLock()
	auth := n.auth
	n.mu.RUnlock()
	if auth == nil {
		return ErrNotAdmin
	}
	if id == n.device().ID {
		return errors.New("mesh: cannot revoke this device itself; use Leave")
	}
	rv := auth.Revoke(id, time.Now())
	// Tell the device itself first, while the link still exists, so it can say
	// it was removed instead of just seeing everybody go offline.
	if p := n.Peer(id); p != nil && p.Online() {
		ctx, cancel := context.WithTimeout(n.ctxOrBackground(), 3*time.Second)
		_ = p.Call(ctx, "mesh.revoked", rv, nil)
		cancel()
	}
	if !n.applyRevocation(rv) {
		return errors.New("mesh: unknown or already revoked device")
	}
	n.pushSyncToAll()
	return nil
}

// SetAlias stores a local nickname for a peer (display only).
func (n *Node) SetAlias(id identity.ID, alias string) error {
	p := n.Peer(id)
	if p == nil {
		return errors.New("mesh: unknown device")
	}
	p.mu.Lock()
	p.alias = strings.TrimSpace(alias)
	p.mu.Unlock()
	n.saveSoon()
	n.emit(Event{Kind: EvPeer, Peer: id})
	return nil
}

// Reissue gives a device a fresh certificate (rename and/or change admin
// status). Admin only; the new certificate spreads by gossip.
func (n *Node) Reissue(id identity.ID, name string, admin bool) error {
	n.mu.RLock()
	auth := n.auth
	var existing []*identity.Member
	for _, p := range n.peers {
		existing = append(existing, p.Member())
	}
	if n.self != nil {
		existing = append(existing, n.self)
	}
	var owner string
	if n.self == nil { // not in a mesh (any more)
		n.mu.RUnlock()
		return ErrNotConfigured
	}
	if id == n.device().ID {
		owner = n.self.Owner
	} else if p := n.peers[id]; p != nil {
		owner = p.Member().Owner
	} else {
		n.mu.RUnlock()
		return errors.New("mesh: unknown device")
	}
	n.mu.RUnlock()
	if auth == nil {
		return ErrNotAdmin
	}
	m, err := auth.Issue(identity.IssueRequest{ID: id, Name: name, Owner: owner, Admin: admin}, existing)
	if err != nil {
		return err
	}
	n.learnMember(m)
	n.pushSyncToAll()
	return nil
}

// ---- event bus ----

// Subscribe returns a channel of node events and a function to cancel it. A
// slow subscriber loses events rather than blocking the node.
func (n *Node) Subscribe() (<-chan Event, func()) {
	ch := make(chan Event, 128)
	n.busMu.Lock()
	n.subID++
	id := n.subID
	n.subs[id] = ch
	n.busMu.Unlock()
	return ch, func() {
		n.busMu.Lock()
		delete(n.subs, id)
		n.busMu.Unlock()
	}
}

func (n *Node) emit(e Event) {
	n.busMu.Lock()
	defer n.busMu.Unlock()
	for _, ch := range n.subs {
		select {
		case ch <- e:
		default:
		}
	}
}

// Platform is "os/arch" of this build.
func Platform() string { return runtime.GOOS + "/" + runtime.GOARCH }

// tokenGate is a small rate limiter.
type tokenGate struct {
	mu     sync.Mutex
	rate   float64
	burst  float64
	tokens float64
	last   time.Time
}

func newTokenGate(perSecond, burst float64) tokenGate {
	return tokenGate{rate: perSecond, burst: burst, tokens: burst, last: time.Now()}
}

func (g *tokenGate) allow() bool {
	g.mu.Lock()
	defer g.mu.Unlock()
	now := time.Now()
	g.tokens += now.Sub(g.last).Seconds() * g.rate
	if g.tokens > g.burst {
		g.tokens = g.burst
	}
	g.last = now
	if g.tokens < 1 {
		return false
	}
	g.tokens--
	return true
}
