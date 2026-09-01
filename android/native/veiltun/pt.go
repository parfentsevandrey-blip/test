package veiltun

import (
	"sync"

	"veil.app/veiltun/internal/ptbridge"
)

// Transport names, matching what tor writes in a `ClientTransportPlugin` line.
const (
	TransportObfs4     = ptbridge.Obfs4
	TransportMeekLite  = ptbridge.MeekLite
	TransportWebtunnel = ptbridge.Webtunnel
	TransportSnowflake = ptbridge.Snowflake
	TransportConjure   = ptbridge.Conjure
)

// TransportEvents is how the app learns that a transport connected, failed or
// stopped. Errors arrive as strings because that is what crosses the language
// boundary cleanly.
type TransportEvents interface {
	Connected(name string)
	Failed(name string, message string)
	Stopped(name string, message string)
}

// Transports owns the pluggable transport clients.
//
// Each started transport listens for SOCKS connections on a local port; tor is
// then told to reach its bridges through that port. Keeping this in the same
// Go module as the tunnel is not a stylistic choice: two gomobile libraries in
// one app would each ship their own `libgojni.so` and their own copy of the Go
// runtime support classes, and only one of them would ever load.
type Transports struct {
	mu     sync.Mutex
	ctrl   *ptbridge.Controller
	events TransportEvents
}

type eventAdapter struct{ events TransportEvents }

func (a *eventAdapter) Stopped(name string, err error) {
	if a.events == nil {
		return
	}
	a.events.Stopped(name, errText(err))
}

func (a *eventAdapter) Error(name string, err error) {
	if a.events == nil {
		return
	}
	a.events.Failed(name, errText(err))
}

func (a *eventAdapter) Connected(name string) {
	if a.events == nil {
		return
	}
	a.events.Connected(name)
}

func errText(err error) string {
	if err == nil {
		return ""
	}
	return err.Error()
}

// NewTransports prepares the transport controller.
//
// Logging is off: a pluggable transport's log records which bridges were used,
// and that file would sit in the app's data directory afterwards.
func NewTransports(stateDir string, events TransportEvents) *Transports {
	ctrl := ptbridge.NewController(
		stateDir,
		/* enableLogging = */ false,
		/* unsafeLogging = */ false,
		"ERROR",
		&eventAdapter{events: events},
	)
	return &Transports{ctrl: ctrl, events: events}
}

// Ready reports whether the controller came up. It returns false when the
// state directory could not be created, which is otherwise silent.
func (t *Transports) Ready() bool {
	return t != nil && t.ctrl != nil
}

// ConfigureSnowflake sets the rendezvous parameters taken from the Snowflake
// bridge line. Passing a non-empty ampCache switches rendezvous from a
// domain-fronted request to Google's AMP cache, which survives in places where
// the fronted request itself is blocked.
func (t *Transports) ConfigureSnowflake(
	iceServers string,
	brokerURL string,
	frontDomains string,
	ampCacheURL string,
	sqsURL string,
	sqsCreds string,
	maxPeers int,
) {
	if !t.Ready() {
		return
	}
	t.mu.Lock()
	defer t.mu.Unlock()
	t.ctrl.SnowflakeIceServers = iceServers
	t.ctrl.SnowflakeBrokerUrl = brokerURL
	t.ctrl.SnowflakeFrontDomains = frontDomains
	t.ctrl.SnowflakeAmpCacheUrl = ampCacheURL
	t.ctrl.SnowflakeSqsUrl = sqsURL
	t.ctrl.SnowflakeSqsCreds = sqsCreds
	t.ctrl.SnowflakeMaxPeers = maxPeers
}

// Start brings up one transport. It returns once the SOCKS listener is
// accepting, except for Snowflake, whose proxy discovery continues afterwards.
func (t *Transports) Start(name string) error {
	if !t.Ready() {
		return errNoController
	}
	t.mu.Lock()
	defer t.mu.Unlock()
	return t.ctrl.Start(name, "")
}

// Port is the local SOCKS port for a started transport, or 0.
func (t *Transports) Port(name string) int {
	if !t.Ready() {
		return 0
	}
	t.mu.Lock()
	defer t.mu.Unlock()
	return t.ctrl.Port(name)
}

func (t *Transports) Stop(name string) {
	if !t.Ready() {
		return
	}
	t.mu.Lock()
	defer t.mu.Unlock()
	t.ctrl.Stop(name)
}

// StopAll shuts every transport down, so a retry can start from a clean state.
func (t *Transports) StopAll() {
	if !t.Ready() {
		return
	}
	t.mu.Lock()
	defer t.mu.Unlock()
	for _, name := range []string{
		ptbridge.Snowflake,
		ptbridge.Conjure,
		ptbridge.Obfs4,
		ptbridge.MeekLite,
		ptbridge.Webtunnel,
	} {
		t.ctrl.Stop(name)
	}
}

func LyrebirdVersion() string  { return ptbridge.LyrebirdVersion() }
func SnowflakeVersion() string { return ptbridge.SnowflakeVersion() }

// --- Running a Snowflake proxy for other people --------------------------

// SnowflakeProxyEvents reports what the donated proxy is doing.
type SnowflakeProxyEvents interface {
	ClientConnected()
	ClientDisconnected(country string)
	ClientFailed()
}

type snowflakeProxyAdapter struct{ events SnowflakeProxyEvents }

func (a *snowflakeProxyAdapter) Connected() {
	if a.events != nil {
		a.events.ClientConnected()
	}
}

func (a *snowflakeProxyAdapter) Disconnected(country string) {
	if a.events != nil {
		a.events.ClientDisconnected(country)
	}
}

func (a *snowflakeProxyAdapter) ConnectionFailed() {
	if a.events != nil {
		a.events.ClientFailed()
	}
}

func (a *snowflakeProxyAdapter) Stats(
	connectionCount int,
	failedConnectionCount int64,
	inboundBytes, outboundBytes int64,
	inboundUnit, outboundUnit string,
	summaryInterval int64,
) {
	// Deliberately not surfaced: periodic traffic totals for a proxy the user
	// is donating are noise, and keeping them would be a record of who was
	// helped and when.
}

func (a *snowflakeProxyAdapter) NatTypeUpdated(natType string) {
	// The NAT type decides whether this device can serve restricted peers. It
	// is useful to know but not worth interrupting anyone over.
}

var (
	proxyMu     sync.Mutex
	activeProxy *ptbridge.SnowflakeProxy
)

// StartSnowflakeProxy turns this device into one of the volunteer WebRTC hops
// that people behind a firewall are matched with.
//
// The proxy type identifier is deliberately left at the upstream default: it
// feeds the Tor Project's public statistics, and changing it without asking
// them would quietly corrupt that data.
func StartSnowflakeProxy(capacity int, events SnowflakeProxyEvents) error {
	proxyMu.Lock()
	defer proxyMu.Unlock()
	if activeProxy != nil && activeProxy.IsRunning() {
		return nil
	}
	p := &ptbridge.SnowflakeProxy{
		Capacity:     capacity,
		ClientEvents: &snowflakeProxyAdapter{events: events},
	}
	if err := p.Start(); err != nil {
		return err
	}
	activeProxy = p
	return nil
}

func StopSnowflakeProxy() {
	proxyMu.Lock()
	defer proxyMu.Unlock()
	if activeProxy != nil {
		activeProxy.Stop()
		activeProxy = nil
	}
}

func IsSnowflakeProxyRunning() bool {
	proxyMu.Lock()
	defer proxyMu.Unlock()
	return activeProxy != nil && activeProxy.IsRunning()
}
