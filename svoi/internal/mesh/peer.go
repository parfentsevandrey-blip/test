package mesh

import (
	"context"
	"encoding/json"
	"net/netip"
	"sync"
	"sync/atomic"
	"time"

	"github.com/quic-go/quic-go"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/magic"
)

// Hello is what a device tells others about itself.
type Hello struct {
	Name      string                     `json:"name"`
	Owner     string                     `json:"owner,omitempty"`
	OS        string                     `json:"os"`
	Arch      string                     `json:"arch"`
	Version   string                     `json:"version"`
	Proto     int                        `json:"proto"`
	Caps      []string                   `json:"caps,omitempty"`
	Started   int64                      `json:"started"`
	Now       int64                      `json:"now"` // unix milliseconds, to estimate clock skew
	Endpoints []string                   `json:"endpoints,omitempty"`
	Extra     map[string]json.RawMessage `json:"extra,omitempty"`
}

// Peer is another member of the mesh as seen from this device.
type Peer struct {
	n  *Node
	ID identity.ID

	mu              sync.RWMutex
	member          *identity.Member
	alias           string
	conn            *quic.Conn
	connDialer      identity.ID
	connectedAt     time.Time
	hello           *Hello
	helloAt         time.Time
	dialing         bool
	cancelDial      context.CancelFunc
	restartDial     bool
	failures        int
	nextDial        time.Time
	wantSince       time.Time
	path            magic.PathInfo
	storedEndpoints []string
	lastErr         string

	lastSeen atomic.Int64 // unix seconds
}

func newPeer(n *Node, m *identity.Member) *Peer {
	return &Peer{n: n, ID: m.ID, member: m}
}

// Member returns the peer's current certificate data.
func (p *Peer) Member() *identity.Member {
	p.mu.RLock()
	defer p.mu.RUnlock()
	return p.member
}

// Name is the display name: the local alias if set, else the device name.
func (p *Peer) Name() string {
	p.mu.RLock()
	defer p.mu.RUnlock()
	if p.alias != "" {
		return p.alias
	}
	return p.member.Name
}

// Alias returns the local nickname, if any.
func (p *Peer) Alias() string {
	p.mu.RLock()
	defer p.mu.RUnlock()
	return p.alias
}

// Online reports whether a link to the peer is up.
func (p *Peer) Online() bool {
	p.mu.RLock()
	defer p.mu.RUnlock()
	return p.conn != nil
}

func (p *Peer) currentConn() *quic.Conn {
	p.mu.RLock()
	defer p.mu.RUnlock()
	return p.conn
}

func (p *Peer) touch() { p.lastSeen.Store(time.Now().Unix()) }

// LastSeen returns when the peer was last known to be alive (zero if never).
func (p *Peer) LastSeen() time.Time {
	if s := p.lastSeen.Load(); s > 0 {
		return time.Unix(s, 0)
	}
	return time.Time{}
}

// Hello returns the last info the peer reported about itself (nil if none yet).
func (p *Peer) Hello() *Hello {
	p.mu.RLock()
	defer p.mu.RUnlock()
	return p.hello
}

// Wait blocks until the peer is online or ctx ends.
func (p *Peer) Wait(ctx context.Context) error {
	for {
		if p.Online() {
			return nil
		}
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(100 * time.Millisecond):
		}
	}
}

// PeerInfo is a snapshot of a peer for display.
type PeerInfo struct {
	ID          string   `json:"id"`
	Short       string   `json:"short"`
	Name        string   `json:"name"`
	DeviceName  string   `json:"deviceName"`
	Alias       string   `json:"alias,omitempty"`
	Owner       string   `json:"owner,omitempty"`
	IP4         string   `json:"ip4"`
	IP6         string   `json:"ip6"`
	Admin       bool     `json:"admin"`
	Online      bool     `json:"online"`
	Path        string   `json:"path"` // lan | direct | relay | none
	RelayVia    string   `json:"relayVia,omitempty"`
	RTTms       float64  `json:"rttMs"`
	Addr        string   `json:"addr,omitempty"`
	LastSeen    int64    `json:"lastSeen"`
	ConnectedAt int64    `json:"connectedAt,omitempty"`
	Hello       *Hello   `json:"hello,omitempty"`
	TxBytes     uint64   `json:"txBytes"`
	RxBytes     uint64   `json:"rxBytes"`
	TxRelay     uint64   `json:"txRelay"`
	RxRelay     uint64   `json:"rxRelay"`
	LastError   string   `json:"lastError,omitempty"`
	Endpoints   []string `json:"endpoints,omitempty"`
	Skew        int64    `json:"clockSkewMs,omitempty"`
}

// Info builds a display snapshot.
func (p *Peer) Info() PeerInfo {
	n := p.n
	p.mu.RLock()
	m := p.member
	info := PeerInfo{
		ID:         p.ID.String(),
		Short:      p.ID.Short(),
		Name:       m.Name,
		DeviceName: m.Name,
		Alias:      p.alias,
		Owner:      m.Owner,
		IP4:        m.IPv4.String(),
		IP6:        m.IPv6.String(),
		Admin:      m.Admin,
		Online:     p.conn != nil,
		LastSeen:   p.lastSeen.Load(),
		Hello:      p.hello,
		LastError:  p.lastErr,
	}
	if p.alias != "" {
		info.Name = p.alias
	}
	if p.conn != nil {
		info.ConnectedAt = p.connectedAt.Unix()
	}
	p.mu.RUnlock()

	if mg := n.Magic(); mg != nil {
		pi := mg.PathInfo(p.ID)
		info.Path = pi.Kind.String()
		info.RTTms = float64(pi.RTT) / float64(time.Millisecond)
		if pi.Addr.IsValid() {
			info.Addr = pi.Addr.String()
		}
		if pi.Kind == magic.PathRelay {
			if rp := n.Peer(pi.Relay); rp != nil {
				info.RelayVia = rp.Name()
			}
		}
		st := mg.Stats(p.ID)
		info.TxBytes, info.RxBytes = st.TxDirect+st.TxRelay, st.RxDirect+st.RxRelay
		info.TxRelay, info.RxRelay = st.TxRelay, st.RxRelay
		for _, ap := range mg.KnownEndpoints(p.ID, 4) {
			info.Endpoints = append(info.Endpoints, ap.String())
		}
	} else {
		info.Path = "none"
	}
	if h := info.Hello; h != nil && h.Now != 0 {
		info.Skew = h.Now - time.Now().UnixMilli()
	}
	return info
}

func (n *Node) endpointsFor(p *Peer) []string {
	mg := n.magic
	if mg == nil {
		p.mu.RLock()
		defer p.mu.RUnlock()
		return p.storedEndpoints
	}
	var out []string
	for _, ap := range mg.KnownEndpoints(p.ID, 4) {
		out = append(out, ap.String())
	}
	if len(out) == 0 {
		p.mu.RLock()
		out = p.storedEndpoints
		p.mu.RUnlock()
	}
	return out
}

// SelfInfo describes this device.
type SelfInfo struct {
	ID         string         `json:"id"`
	Short      string         `json:"short"`
	Name       string         `json:"name"`
	Owner      string         `json:"owner,omitempty"`
	IP4        string         `json:"ip4,omitempty"`
	IP6        string         `json:"ip6,omitempty"`
	Admin      bool           `json:"admin"`
	MeshID     string         `json:"meshId,omitempty"`
	MeshName   string         `json:"meshName,omitempty"`
	UDPPort    int            `json:"udpPort,omitempty"`
	Endpoints  []EndpointInfo `json:"endpoints,omitempty"`
	NAT        NATInfo        `json:"nat"`
	Version    string         `json:"version"`
	OS         string         `json:"os"`
	Arch       string         `json:"arch"`
	Started    int64          `json:"started,omitempty"`
	Configured bool           `json:"configured"`
	Relay      bool           `json:"relay"`
	Relayed    RelayedInfo    `json:"relayed"`
}

// EndpointInfo is one of our reachable addresses.
type EndpointInfo struct {
	Addr string `json:"addr"`
	Kind string `json:"kind"` // local | stun | observed
}

// NATInfo is the NAT diagnosis shown in the UI.
type NATInfo struct {
	MappingVaries bool     `json:"mappingVaries"`
	Public        []string `json:"public"`
	HasIPv6       bool     `json:"hasIPv6"`
	STUN          bool     `json:"stun"`
	// Difficulty: "open" (public address), "easy" (hole punching works),
	// "hard" (symmetric NAT: relays will be used) or "unknown".
	Difficulty string `json:"difficulty"`
}

// RelayedInfo counts traffic this device forwarded for others.
type RelayedInfo struct {
	Packets uint64 `json:"packets"`
	Bytes   uint64 `json:"bytes"`
}

// Self returns a description of this device.
func (n *Node) Self() SelfInfo {
	n.mu.RLock()
	defer n.mu.RUnlock()
	hos, harch := n.platform()
	info := SelfInfo{
		ID: n.device().ID.String(), Short: n.device().ID.Short(),
		Version: Version, OS: hos, Arch: harch,
		Configured: n.root != nil,
	}
	if n.root == nil {
		return info
	}
	info.Name, info.Owner = n.self.Name, n.self.Owner
	info.IP4, info.IP6 = n.self.IPv4.String(), n.self.IPv6.String()
	info.Admin = n.auth != nil
	info.MeshID, info.MeshName = n.root.MeshID(), n.meshName
	info.UDPPort = n.udpPort
	info.Relay = !n.cfg.NoRelay
	if !n.started.IsZero() {
		info.Started = n.started.Unix()
	}
	if n.magic != nil {
		for _, e := range n.magic.Endpoints() {
			info.Endpoints = append(info.Endpoints, EndpointInfo{Addr: e.Addr.String(), Kind: e.Kind.String()})
		}
		nat := n.magic.NAT()
		info.NAT.MappingVaries = nat.MappingVaries
		info.NAT.HasIPv6 = nat.HasIPv6
		info.NAT.STUN = nat.STUNWorks
		for _, ap := range nat.Public {
			info.NAT.Public = append(info.NAT.Public, ap.String())
		}
		info.NAT.Difficulty = natDifficulty(nat, n.magic.Endpoints())
		pk, by := n.magic.RelayStats()
		info.Relayed = RelayedInfo{Packets: pk, Bytes: by}
	}
	return info
}

func natDifficulty(nat magic.NATReport, eps []magic.Endpoint) string {
	if nat.MappingVaries {
		return "hard"
	}
	// If one of our own interface addresses is also public we are directly reachable.
	local := map[netip.Addr]bool{}
	for _, e := range eps {
		if e.Kind == magic.EPLocal && !isPrivate(e.Addr.Addr()) {
			local[e.Addr.Addr()] = true
		}
	}
	for _, ap := range nat.Public {
		if local[ap.Addr()] {
			return "open"
		}
	}
	if len(nat.Public) > 0 {
		return "easy"
	}
	return "unknown"
}

func isPrivate(a netip.Addr) bool {
	a = a.Unmap()
	return a.IsPrivate() || a.IsLoopback() || a.IsLinkLocalUnicast()
}
