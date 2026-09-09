// Package core wires TorVeil's parts together and exposes the API the user
// interface binds to.
package core

import (
	"context"
	"errors"
	"fmt"
	"net"
	"net/netip"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/parfentsevandrey-blip/torveil/internal/config"
	"github.com/parfentsevandrey-blip/torveil/internal/logging"
	"github.com/parfentsevandrey-blip/torveil/internal/proxy"
	"github.com/parfentsevandrey-blip/torveil/internal/shaper"
	"github.com/parfentsevandrey-blip/torveil/internal/tor"
	"github.com/parfentsevandrey-blip/torveil/internal/tunnel"
	"github.com/parfentsevandrey-blip/torveil/internal/winsys"
)

// State is the engine's lifecycle state.
type State string

// Engine states, in the order a successful connection passes through them.
const (
	StateDisconnected State = "disconnected"
	StateStarting     State = "starting"
	StateBootstrap    State = "bootstrapping"
	StateDirectory    State = "loading-directory"
	StateCircuits     State = "building-circuits"
	StateConnected    State = "connected"
	StateStopping     State = "stopping"
	StateFailed       State = "failed"
)

// warmCircuits is how many circuits are pre-built before the engine reports
// itself connected, so the first request does not pay the build latency.
const warmCircuits = 2

// Status is a full snapshot of the engine for the UI.
type Status struct {
	State     State  `json:"state"`
	Detail    string `json:"detail"`
	Error     string `json:"error"`
	Since     string `json:"since"`
	Elevated  bool   `json:"elevated"`
	Mode      string `json:"mode"`
	Transport string `json:"transport"`

	BootstrapPercent int    `json:"bootstrapPercent"`
	BootstrapSummary string `json:"bootstrapSummary"`

	Hops         int    `json:"hops"`
	EntryCountry string `json:"entryCountry"`
	ExitCountry  string `json:"exitCountry"`
	PinnedGuard  string `json:"pinnedGuard"`

	Profile shaper.Profile `json:"profile"`
	Shaper  shaper.Stats   `json:"shaper"`
	Proxy   proxy.Stats    `json:"proxy"`
	Tunnel  tunnel.Status  `json:"tunnel"`

	Circuits   []tor.CircuitInfo `json:"circuits"`
	RelayCount int               `json:"relayCount"`
	ChaffOnion string            `json:"chaffOnion"`

	Warnings []string `json:"warnings"`
}

// Engine owns every moving part of a TorVeil session.
type Engine struct {
	logs *logging.Buffer

	mu        sync.RWMutex
	cfg       config.Config
	state     State
	detail    string
	lastError string
	since     time.Time
	warnings  []string

	torProc  *tor.Process
	dir      *tor.Directory
	circuits *tor.Manager
	shape    *shaper.Shaper
	chaff    *shaper.Chaff
	proxySrv *proxy.Server
	tun      *tunnel.Tunnel
	binaries tor.Binaries

	cancel  context.CancelFunc
	running bool

	onChange func()
}

// New creates an engine from a configuration.
func New(cfg config.Config, logs *logging.Buffer) *Engine {
	if logs == nil {
		logs = logging.New(2000)
	}
	return &Engine{
		logs:  logs,
		cfg:   cfg,
		state: StateDisconnected,
		dir:   tor.NewDirectory(),
	}
}

// Logs returns the engine's log buffer.
func (e *Engine) Logs() *logging.Buffer { return e.logs }

// SetOnChange registers a callback fired whenever the status changes, so the
// UI can push updates instead of polling.
func (e *Engine) SetOnChange(f func()) {
	e.mu.Lock()
	e.onChange = f
	e.mu.Unlock()
}

func (e *Engine) notify() {
	e.mu.RLock()
	f := e.onChange
	e.mu.RUnlock()
	if f != nil {
		f()
	}
}

func (e *Engine) setState(s State, detail string) {
	e.mu.Lock()
	e.state, e.detail = s, detail
	if s == StateConnected && e.since.IsZero() {
		e.since = time.Now()
	}
	if s == StateDisconnected || s == StateFailed {
		e.since = time.Time{}
	}
	e.mu.Unlock()
	if detail != "" {
		e.logs.Logf("info", "%s: %s", s, detail)
	}
	e.notify()
}

func (e *Engine) fail(err error) error {
	e.mu.Lock()
	e.state = StateFailed
	e.lastError = err.Error()
	e.mu.Unlock()
	e.logs.Logf("error", "%v", err)
	e.notify()
	return err
}

func (e *Engine) warn(format string, args ...any) {
	msg := fmt.Sprintf(format, args...)
	e.mu.Lock()
	e.warnings = append(e.warnings, msg)
	e.mu.Unlock()
	e.logs.Log("warn", msg)
}

// Config returns the current configuration.
func (e *Engine) Config() config.Config {
	e.mu.RLock()
	defer e.mu.RUnlock()
	return e.cfg
}

// SetConfig replaces the configuration and persists it.
//
// Settings that only affect path selection are applied to the running session
// immediately; anything that changes how Tor itself is launched needs a
// reconnect, and the caller is told so.
func (e *Engine) SetConfig(cfg config.Config) (needsReconnect bool, err error) {
	old := e.Config()
	cfg = cfg.Normalized()

	needsReconnect = cfg.Transport != old.Transport ||
		cfg.DataDir != old.DataDir ||
		cfg.Mode != old.Mode ||
		!equalStrings(cfg.SnowflakeBridges, old.SnowflakeBridges) ||
		!equalStrings(cfg.Obfs4Bridges, old.Obfs4Bridges) ||
		cfg.SOCKSListen != old.SOCKSListen ||
		cfg.HTTPListen != old.HTTPListen

	e.mu.Lock()
	e.cfg = cfg
	running := e.running
	circuits, shape := e.circuits, e.shape
	e.mu.Unlock()

	if err := cfg.Save(); err != nil {
		e.logs.Logf("warn", "save configuration: %v", err)
	}

	if running && !needsReconnect {
		if circuits != nil {
			circuits.SetPolicy(e.pathPolicy(cfg))
		}
		if shape != nil {
			shape.SetProfile(e.profileFor(cfg))
		}
		e.logs.Log("info", "settings applied to the running session")
	}
	e.notify()
	return needsReconnect, nil
}

func equalStrings(a, b []string) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}

// profileFor resolves the shaping profile, applying any user override of the
// cover-traffic ceiling.
func (e *Engine) profileFor(cfg config.Config) shaper.Profile {
	p := shaper.MustProfile(shaper.ProfileID(cfg.ShapingProfile))
	if cfg.ChaffRateOverride > 0 && p.ChaffMode != shaper.ChaffOff {
		p.ChaffRate = cfg.ChaffRateOverride
	}
	return p
}

// pathPolicy translates the configuration into a circuit path policy.
func (e *Engine) pathPolicy(cfg config.Config) tor.PathPolicy {
	p := tor.PathPolicy{
		Hops:             cfg.Hops,
		EntryCountry:     cfg.EntryCountry,
		ExitCountry:      cfg.ExitCountry,
		ExcludeCountries: append([]string(nil), cfg.ExcludeCountries...),
		PinnedGuard:      cfg.PinnedGuard,
	}
	if fp := e.bridgeFingerprint(cfg); fp != "" {
		p.BridgeFingerprint = fp
	}
	return p
}

// bridgeFingerprint returns the identity of the bridge that will be the first
// hop, or "" when connecting directly.
func (e *Engine) bridgeFingerprint(cfg config.Config) string {
	for _, line := range cfg.ActiveBridges() {
		b, err := tor.ParseBridgeLine(line)
		if err != nil || b.Fingerprint == "" {
			continue
		}
		return b.Fingerprint
	}
	return ""
}

// Connect brings the session up. It returns once the engine is connected or
// has failed.
func (e *Engine) Connect(ctx context.Context) error {
	e.mu.Lock()
	if e.running {
		e.mu.Unlock()
		return errors.New("already connected")
	}
	e.running = true
	e.warnings = nil
	e.lastError = ""
	cfg := e.cfg
	runCtx, cancel := context.WithCancel(context.Background())
	e.cancel = cancel
	e.mu.Unlock()

	if err := e.connect(ctx, runCtx, cfg); err != nil {
		e.shutdown()
		return err
	}
	return nil
}

func (e *Engine) connect(ctx, runCtx context.Context, cfg config.Config) error {
	e.setState(StateStarting, "locating Tor")

	bins, err := tor.Locate(cfg.TorSearchDirs)
	if err != nil {
		return e.fail(fmt.Errorf("%w\n\nTorVeil does not bundle Tor. Install the Tor Expert Bundle or Tor Browser, "+
			"or point \"Tor directory\" in settings at a folder containing tor.exe", err))
	}
	e.mu.Lock()
	e.binaries = bins
	e.mu.Unlock()

	transport := tor.Transport(cfg.Transport)
	if transport == tor.TransportSnowflake && bins.Snowflake == "" {
		return e.fail(errors.New("snowflake selected but snowflake-client.exe was not found next to tor.exe"))
	}
	if transport == tor.TransportObfs4 && bins.Obfs4 == "" {
		return e.fail(errors.New("obfs4 selected but lyrebird.exe / obfs4proxy.exe was not found next to tor.exe"))
	}
	if bins.GeoIP == "" {
		e.warn("Tor's geoip database was not found, so country selection is unavailable")
	}

	profile := e.profileFor(cfg)
	extra := map[string]string{}
	// User options first, so the profile keeps control of the padding
	// settings that define it.
	for k, v := range cfg.ExtraTorrc {
		extra[k] = v
	}
	for k, v := range profile.TorOptions {
		extra[k] = v
	}
	if cfg.Mode == config.ModeTunnel {
		// Onion names resolved through the DNSPort get a virtual address;
		// Tor's default range is inside 127/8, which Windows never routes to
		// an interface, so onion sites would be unreachable in tunnel mode.
		extra["VirtualAddrNetworkIPv4"] = tunnel.AutomapPrefix.String()
	}

	e.setState(StateStarting, "starting Tor")
	proc, err := tor.Start(ctx, tor.Options{
		DataDir:    cfg.DataDir,
		Binaries:   bins,
		Transport:  transport,
		Bridges:    cfg.ActiveBridges(),
		ExtraTorrc: extra,
		Log:        e.logs.Func(),
		OnBootstrap: func(b tor.Bootstrap) {
			e.setState(StateBootstrap, fmt.Sprintf("%d%% %s", b.Percent, b.Summary))
		},
	})
	if err != nil {
		return e.fail(fmt.Errorf("start Tor: %w", err))
	}
	e.mu.Lock()
	e.torProc = proc
	e.mu.Unlock()

	e.setState(StateBootstrap, "connecting to the Tor network")
	bootCtx, cancelBoot := context.WithTimeout(ctx, bootstrapTimeout(transport))
	err = proc.WaitBootstrapped(bootCtx)
	cancelBoot()
	if err != nil {
		return e.fail(fmt.Errorf("%w\n\n%s", err, bootstrapHint(transport)))
	}

	e.setState(StateDirectory, "loading the relay directory")
	if err := e.dir.Refresh(ctx, proc.Control()); err != nil {
		return e.fail(fmt.Errorf("load relay directory: %w", err))
	}
	e.logs.Logf("info", "relay directory loaded: %d relays", e.dir.Len())

	policy := e.pathPolicy(cfg)
	if err := e.pinGuard(ctx, &policy, &cfg, proc); err != nil {
		e.warn("%v", err)
	}

	mgr := tor.NewManager(proc.Control(), e.dir, e.logs.Func())
	mgr.SetPolicy(policy)
	mgr.SetOnChange(e.notify)
	if err := mgr.Engage(ctx); err != nil {
		return e.fail(fmt.Errorf("take over stream attachment: %w", err))
	}
	e.mu.Lock()
	e.circuits = mgr
	e.mu.Unlock()

	// Shaping and the local listeners come up before the first circuit, so a
	// stream that arrives early is already on the shaped path.
	shape := shaper.New(profile)
	shape.Start()

	upstream := &proxy.Upstream{
		Address: proc.SOCKSAddress(),
		Timeout: 90 * time.Second,
		Wrap:    shape.Wrap,
	}
	srv := proxy.New(upstream, e.logs.Func())
	if err := srv.Start(cfg.SOCKSListen, cfg.HTTPListen); err != nil {
		shape.Stop()
		return e.fail(fmt.Errorf("%w\n\nAnother program may already be using that port", err))
	}
	e.mu.Lock()
	e.shape, e.proxySrv = shape, srv
	e.mu.Unlock()

	e.setState(StateCircuits, fmt.Sprintf("building %d-hop circuits", policy.Hops))
	warmCtx, cancelWarm := context.WithTimeout(ctx, 3*time.Minute)
	mgr.Warm(warmCtx, warmCircuits)
	cancelWarm()

	// Cover traffic is dialled without shaping: it is already emitted on the
	// shaper's clock, and passing it through the shaped wrapper would both
	// double-delay it and count it as real traffic.
	chaffDialer := &proxy.Upstream{Address: proc.SOCKSAddress(), Timeout: 120 * time.Second}
	ch := shaper.NewChaff(shape, chaffDialer, proc.Control().AddEphemeralOnion, e.logs.Func())
	if err := ch.Start(runCtx); err != nil {
		e.warn("cover traffic unavailable: %v", err)
	}
	e.mu.Lock()
	e.chaff = ch
	e.mu.Unlock()

	if cfg.Mode == config.ModeTunnel {
		if err := e.startTunnel(cfg, upstream, proc); err != nil {
			return e.fail(err)
		}
	}

	go e.watch(runCtx, proc)

	e.setState(StateConnected, "")
	e.logs.Logf("info", "connected: %s transport, %d hops, %s shaping", cfg.Transport, policy.Hops, profile.Name)
	return nil
}

// pinGuard fixes the entry relay for the session and, when connecting
// directly, tells Tor to use the same one for the circuits it builds itself.
func (e *Engine) pinGuard(ctx context.Context, policy *tor.PathPolicy, cfg *config.Config, proc *tor.Process) error {
	if policy.UsesBridge() {
		// The bridge is the entry; there is nothing to pin.
		return nil
	}
	guard, err := e.dir.ChooseGuard(*policy)
	if err != nil {
		return fmt.Errorf("choose an entry relay: %w", err)
	}
	policy.PinnedGuard = guard.Fingerprint

	e.mu.Lock()
	e.cfg.PinnedGuard = guard.Fingerprint
	saved := e.cfg
	e.mu.Unlock()
	cfg.PinnedGuard = guard.Fingerprint
	if err := saved.Save(); err != nil {
		e.logs.Logf("info", "persist pinned guard: %v", err)
	}

	// Tor builds circuits of its own for onion service descriptors and for
	// the cover-traffic channel. Pinning the same entry relay in Tor's own
	// configuration keeps all of it on one connection, which is what makes
	// the cover traffic actually cover anything.
	if err := proc.Control().SetConf(ctx, map[string]string{
		"EntryNodes":  guard.Fingerprint,
		"StrictNodes": "1",
	}); err != nil {
		return fmt.Errorf("pin entry relay in Tor: %w", err)
	}
	e.logs.Logf("info", "entry relay pinned: %s (%s)", guard.Nickname, strings.ToUpper(guard.Country))
	return nil
}

// startTunnel brings up full-tunnel mode.
func (e *Engine) startTunnel(cfg config.Config, dialer tunnel.Dialer, proc *tor.Process) error {
	address, err := netip.ParsePrefix(cfg.Tunnel.Address)
	if err != nil {
		return fmt.Errorf("tunnel address %q: %w", cfg.Tunnel.Address, err)
	}
	gateway, err := netip.ParseAddr(cfg.Tunnel.Gateway)
	if err != nil {
		return fmt.Errorf("tunnel gateway %q: %w", cfg.Tunnel.Gateway, err)
	}

	bypass := make([]netip.Prefix, 0, len(cfg.Tunnel.BypassCIDRs)+4)
	for _, c := range cfg.Tunnel.BypassCIDRs {
		p, err := netip.ParsePrefix(strings.TrimSpace(c))
		if err != nil {
			e.warn("ignoring bypass entry %q: %v", c, err)
			continue
		}
		bypass = append(bypass, p)
	}
	bypass = append(bypass, e.bridgeBypassPrefixes(cfg)...)

	if tor.Transport(cfg.Transport) == tor.TransportSnowflake {
		e.warn("Snowflake with full-tunnel mode is fragile: Snowflake's WebRTC transport is UDP, " +
			"and Windows exposes no remote address for UDP sockets, so those peers cannot be " +
			"routed around the tunnel automatically. If the connection drops after the tunnel " +
			"comes up, use obfs4 or a direct connection for full-tunnel mode, or keep Snowflake " +
			"and use proxy mode")
	}

	e.setState(StateCircuits, "bringing up the tunnel adapter")
	t, err := tunnel.Start(tunnel.Options{
		AdapterName:    cfg.Tunnel.AdapterName,
		Address:        address,
		Gateway:        gateway,
		MTU:            cfg.Tunnel.MTU,
		Dialer:         dialer,
		DNSUpstream:    proc.DNSAddress(),
		DNSListen:      "127.0.0.1:53",
		BypassCIDRs:    bypass,
		WatchProcesses: e.watchedProcesses(),
		AllowPrograms:  e.allowedPrograms(),
		StateDir:       cfg.DataDir,
		ExcludeLAN:     cfg.Tunnel.ExcludeLAN,
		BlockIPv6:      cfg.Tunnel.BlockIPv6,
		BlockQUIC:      cfg.Tunnel.BlockQUIC,
		KillSwitch:     cfg.Tunnel.KillSwitch,
		Log:            e.logs.Func(),
	})
	if err != nil {
		if errors.Is(err, tunnel.ErrNeedsAdmin) {
			return fmt.Errorf("%w\n\nRestart TorVeil as administrator, or switch to proxy mode", err)
		}
		return fmt.Errorf("start tunnel: %w", err)
	}
	e.mu.Lock()
	e.tun = t
	e.mu.Unlock()
	return nil
}

// bridgeBypassPrefixes returns host routes for bridges whose address is a
// literal IP, so Tor can still reach them once the tunnel takes the default
// route. Snowflake's placeholder addresses are not real endpoints and are
// skipped.
func (e *Engine) bridgeBypassPrefixes(cfg config.Config) []netip.Prefix {
	var out []netip.Prefix
	for _, line := range cfg.ActiveBridges() {
		b, err := tor.ParseBridgeLine(line)
		if err != nil {
			continue
		}
		host, _, err := net.SplitHostPort(b.Address)
		if err != nil {
			continue
		}
		addr, err := netip.ParseAddr(host)
		if err != nil || !addr.Is4() {
			continue
		}
		if b.Transport == "snowflake" {
			// 192.0.2.0/24 is the documentation range; Snowflake bridge lines
			// use it as a placeholder because the real endpoint is a WebRTC
			// proxy discovered at runtime.
			continue
		}
		out = append(out, netip.PrefixFrom(addr, 32))
	}
	return out
}

// watchedProcesses names the executables whose outbound connections must
// bypass the tunnel.
func (e *Engine) watchedProcesses() []string {
	e.mu.RLock()
	bins := e.binaries
	e.mu.RUnlock()

	names := []string{}
	for _, p := range []string{bins.Tor, bins.Snowflake, bins.Obfs4} {
		if p != "" {
			names = append(names, filepath.Base(p))
		}
	}
	return names
}

// allowedPrograms lists the executables exempt from the kill switch.
func (e *Engine) allowedPrograms() []string {
	e.mu.RLock()
	bins := e.binaries
	e.mu.RUnlock()

	progs := []string{}
	if self, err := os.Executable(); err == nil {
		progs = append(progs, self)
	}
	for _, p := range []string{bins.Tor, bins.Snowflake, bins.Obfs4} {
		if p != "" {
			progs = append(progs, p)
		}
	}
	return progs
}

// watch reacts to Tor dying underneath the session.
func (e *Engine) watch(ctx context.Context, proc *tor.Process) {
	select {
	case <-ctx.Done():
		return
	case <-proc.Exited():
	}

	e.mu.RLock()
	tun, killSwitch := e.tun, e.cfg.Tunnel.KillSwitch
	e.mu.RUnlock()

	e.logs.Log("error", "Tor exited unexpectedly")

	// With the tunnel up, losing Tor means every tunnelled connection is dead
	// but the routes still point at the adapter. Engaging the kill switch
	// before tearing that down keeps traffic from quietly resuming in the
	// clear during the teardown.
	if tun != nil && killSwitch {
		if err := tun.EngageKillSwitch(); err != nil {
			e.logs.Logf("error", "engage kill switch: %v", err)
		}
	}

	e.shutdown()
	e.mu.Lock()
	e.state = StateFailed
	e.lastError = "Tor exited unexpectedly"
	e.mu.Unlock()
	e.notify()
}

// Disconnect tears the session down.
func (e *Engine) Disconnect() error {
	e.setState(StateStopping, "shutting down")
	e.shutdown()
	e.setState(StateDisconnected, "")
	return nil
}

// shutdown stops everything in the reverse of start-up order, so the network
// configuration is restored before the process that depends on it goes away.
func (e *Engine) shutdown() {
	e.mu.Lock()
	cancel := e.cancel
	tun, chaff, srv, shape, mgr, proc := e.tun, e.chaff, e.proxySrv, e.shape, e.circuits, e.torProc
	e.tun, e.chaff, e.proxySrv, e.shape, e.circuits, e.torProc = nil, nil, nil, nil, nil, nil
	e.cancel, e.running = nil, false
	e.mu.Unlock()

	if cancel != nil {
		cancel()
	}
	if tun != nil {
		_ = tun.Stop()
	}
	if chaff != nil {
		chaff.Stop()
	}
	if srv != nil {
		srv.Stop()
	}
	if mgr != nil {
		ctx, c := context.WithTimeout(context.Background(), 3*time.Second)
		_ = mgr.Release(ctx)
		c()
	}
	if shape != nil {
		shape.Stop()
	}
	if proc != nil {
		proc.Stop()
	}
	e.notify()
}

// NewIdentity drops the current circuits and asks Tor for fresh ones.
func (e *Engine) NewIdentity(ctx context.Context) error {
	e.mu.RLock()
	mgr := e.circuits
	e.mu.RUnlock()
	if mgr == nil {
		return errors.New("not connected")
	}
	if err := mgr.NewIdentity(ctx); err != nil {
		return err
	}
	e.logs.Log("info", "new identity: circuits rebuilt")
	return nil
}

// Countries lists the countries relays are available in.
func (e *Engine) Countries() []tor.CountryStat { return e.dir.Countries() }

// Status returns a snapshot for the UI.
func (e *Engine) Status() Status {
	e.mu.RLock()
	cfg := e.cfg
	s := Status{
		State:     e.state,
		Detail:    e.detail,
		Error:     e.lastError,
		Mode:      string(cfg.Mode),
		Transport: cfg.Transport,
		Hops:      cfg.Hops,

		EntryCountry: strings.ToUpper(cfg.EntryCountry),
		ExitCountry:  strings.ToUpper(cfg.ExitCountry),
		PinnedGuard:  cfg.PinnedGuard,
		Warnings:     append([]string(nil), e.warnings...),
	}
	if !e.since.IsZero() {
		s.Since = e.since.Format(time.RFC3339)
	}
	proc, mgr, shape, srv, tun, chaff := e.torProc, e.circuits, e.shape, e.proxySrv, e.tun, e.chaff
	e.mu.RUnlock()

	s.Profile = e.profileFor(cfg)
	s.RelayCount = e.dir.Len()
	s.Elevated = winsys.IsElevated()

	if proc != nil {
		b := proc.Bootstrap()
		s.BootstrapPercent, s.BootstrapSummary = b.Percent, b.Summary
	}
	if mgr != nil {
		s.Circuits = mgr.Circuits()
	}
	if shape != nil {
		s.Shaper = shape.Stats()
	}
	if srv != nil {
		s.Proxy = srv.Stats()
	}
	if tun != nil {
		s.Tunnel = tun.Status()
	}
	if chaff != nil {
		s.ChaffOnion = chaff.OnionAddress()
	}
	return s
}

// bootstrapTimeout allows longer for transports that have to negotiate a
// rendezvous before Tor can even begin.
func bootstrapTimeout(t tor.Transport) time.Duration {
	if t == tor.TransportSnowflake {
		return 4 * time.Minute
	}
	return 2 * time.Minute
}

// bootstrapHint explains what a user can actually do about a stalled start.
func bootstrapHint(t tor.Transport) string {
	switch t {
	case tor.TransportSnowflake:
		return "Snowflake needs to reach its broker through a CDN and then find a volunteer proxy. " +
			"If this keeps failing, the bridge lines may be out of date: replace them in settings with " +
			"current ones from Tor Browser, or try obfs4 instead."
	case tor.TransportObfs4:
		return "The configured obfs4 bridges may be blocked or out of date. Request fresh ones from " +
			"bridges.torproject.org and paste them into settings."
	default:
		return "A direct connection to the Tor network appears to be blocked. Switch the transport to " +
			"Snowflake or obfs4 in settings."
	}
}
