// Package portmap asks the home router to forward our UDP port (UPnP IGD and
// NAT-PMP), so that a device behind a typical home router becomes reachable
// from outside without anybody opening a port by hand. That is what makes a
// home server a usable "anchor" for the rest of the mesh.
//
// It is best effort: no router, no UPnP, a router with a private WAN address
// (carrier-grade NAT) - all of that just means "no mapping", and everything
// else in svoi keeps working as before (hole punching, relays). The mapping is
// removed again when the device stops.
package portmap

import (
	"context"
	"errors"
	"fmt"
	"net"
	"net/http"
	"net/netip"
	"sync"
	"time"
)

// Protocol names how the mapping was made.
type Protocol string

// Protocols.
const (
	UPnP   Protocol = "upnp"
	NATPMP Protocol = "natpmp"
)

// Mapping is an address on the Internet side of the router that reaches us.
type Mapping struct {
	External netip.AddrPort
	Protocol Protocol
	Gateway  netip.Addr
}

// Status describes what the mapper is doing, for the interface.
type Status struct {
	// State: "searching" (looking for a router that can do it), "mapped",
	// "private" (the router's own address is not public: double NAT, CGNAT),
	// "unavailable" (no router answered).
	State    string   `json:"state"`
	Protocol Protocol `json:"protocol,omitempty"`
	External string   `json:"external,omitempty"`
	Gateway  string   `json:"gateway,omitempty"`
	Error    string   `json:"error,omitempty"`
}

// Config configures a Mapper.
type Config struct {
	// Port is the local UDP port to expose.
	Port int
	// LocalAddrs lists this machine's IPv4 addresses (the ones that may sit
	// behind a router). Default: all up, non-loopback interfaces.
	LocalAddrs func() []netip.Addr
	Logf       func(format string, args ...any)
	// Changed is called (from the mapper's goroutine) when the mapping appears,
	// changes or is lost (nil).
	Changed func(*Mapping)

	// Test hooks.
	ssdpTarget string            // default 239.255.255.250:1900
	gateway    func() netip.Addr // default: the system's default gateway
	pmpPort    int               // default 5351
	retry      []time.Duration   // waits between attempts to find a router
	renewEvery time.Duration     // 0: half the lease
}

// Mapper keeps one port mapping alive.
type Mapper struct {
	cfg    Config
	ctx    context.Context
	cancel context.CancelFunc
	wg     sync.WaitGroup

	mu   sync.Mutex
	st   Status
	cur  *Mapping
	kick chan struct{}
}

const (
	leaseSeconds = 3600
	maxRenew     = 30 * time.Minute
)

var defaultRetry = []time.Duration{20 * time.Second, time.Minute, 2 * time.Minute, 5 * time.Minute, 15 * time.Minute, 30 * time.Minute}

// Start begins looking for a router and mapping cfg.Port in the background.
func Start(cfg Config) *Mapper {
	if cfg.Logf == nil {
		cfg.Logf = func(string, ...any) {}
	}
	if cfg.LocalAddrs == nil {
		cfg.LocalAddrs = systemIPv4Addrs
	}
	if cfg.gateway == nil {
		cfg.gateway = defaultGateway
	}
	if cfg.pmpPort == 0 {
		cfg.pmpPort = 5351
	}
	if cfg.ssdpTarget == "" {
		cfg.ssdpTarget = "239.255.255.250:1900"
	}
	if len(cfg.retry) == 0 {
		cfg.retry = defaultRetry
	}
	ctx, cancel := context.WithCancel(context.Background())
	m := &Mapper{cfg: cfg, ctx: ctx, cancel: cancel, st: Status{State: "searching"}, kick: make(chan struct{}, 1)}
	m.wg.Add(1)
	go m.run()
	return m
}

// Close stops the mapper and removes the mapping from the router (bounded: a
// router that does not answer must not hold up shutdown).
func (m *Mapper) Close() {
	m.cancel()
	m.wg.Wait()
}

// Poke says the network around us changed (a new address, another Wi-Fi): look
// again now instead of waiting out the current delay.
func (m *Mapper) Poke() {
	select {
	case m.kick <- struct{}{}:
	default:
	}
}

// Status returns the current state.
func (m *Mapper) Status() Status {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.st
}

func (m *Mapper) set(st Status, mp *Mapping) {
	m.mu.Lock()
	changed := (m.cur == nil) != (mp == nil) || (mp != nil && *m.cur != *mp)
	m.st, m.cur = st, mp
	m.mu.Unlock()
	if changed && m.cfg.Changed != nil {
		m.cfg.Changed(mp)
	}
}

func (m *Mapper) run() {
	defer m.wg.Done()
	attempt := 0
	for m.ctx.Err() == nil {
		c, err := m.discover(m.ctx)
		if err == nil {
			if m.hold(c) { // held a mapping until told to stop
				return
			}
			attempt = 0 // it worked for a while: start the retries from the beginning
		} else if m.ctx.Err() == nil {
			m.cfg.Logf("portmap: %v", err)
			m.set(Status{State: "unavailable", Error: err.Error()}, nil)
		}
		wait := m.cfg.retry[min(attempt, len(m.cfg.retry)-1)]
		attempt++
		select {
		case <-m.ctx.Done():
			return
		case <-m.kick:
			attempt = 0
		case <-time.After(wait):
		}
	}
}

// hold maps the port through c and renews it until it fails (false) or we are
// told to stop (true; the mapping is removed first).
func (m *Mapper) hold(c client) (stopped bool) {
	ext, err := m.establish(c)
	if err != nil {
		m.cfg.Logf("portmap: %s: %v", c.protocol(), err)
		var priv *privateError
		if errors.As(err, &priv) {
			m.set(Status{State: "private", Protocol: c.protocol(), Gateway: c.gatewayAddr().String(), External: priv.ip.String(), Error: err.Error()}, nil)
		} else {
			m.set(Status{State: "unavailable", Protocol: c.protocol(), Gateway: c.gatewayAddr().String(), Error: err.Error()}, nil)
		}
		return m.ctx.Err() != nil
	}
	mp := &Mapping{External: ext.addr, Protocol: c.protocol(), Gateway: c.gatewayAddr()}
	m.set(Status{State: "mapped", Protocol: c.protocol(), Gateway: mp.Gateway.String(), External: ext.addr.String()}, mp)
	m.cfg.Logf("portmap: %s mapped %s -> local port %d via %s", c.protocol(), ext.addr, m.cfg.Port, mp.Gateway)

	every := ext.lease / 2
	if every <= 0 || every > maxRenew {
		every = maxRenew
	}
	if m.cfg.renewEvery > 0 {
		every = m.cfg.renewEvery
	}
	for {
		select {
		case <-m.ctx.Done():
			dctx, cancel := context.WithTimeout(context.Background(), 2500*time.Millisecond)
			_ = c.deleteMapping(dctx, int(ext.addr.Port()))
			cancel()
			m.set(Status{State: "searching"}, nil)
			return true
		case <-m.kick: // the network changed: check at once that the mapping still holds
		case <-time.After(every):
		}
		rctx, cancel := context.WithTimeout(m.ctx, 8*time.Second)
		_, _, err := c.addMapping(rctx, m.cfg.Port, int(ext.addr.Port()), leaseSeconds, ext.lease == 0)
		if err == nil {
			// The router may have restarted with another public address.
			if ip, e2 := c.externalIP(rctx); e2 == nil && ip != ext.addr.Addr() && isPublicIPv4(ip) {
				cancel()
				m.cfg.Logf("portmap: the router's public address changed to %s", ip)
				m.set(Status{State: "searching"}, nil)
				return false
			}
		}
		cancel()
		if err != nil {
			if m.ctx.Err() != nil {
				continue // handled by the case above
			}
			m.cfg.Logf("portmap: renewal failed: %v", err)
			m.set(Status{State: "searching"}, nil)
			return false
		}
	}
}

type established struct {
	addr  netip.AddrPort
	lease time.Duration // 0: permanent
}

// privateError: the router's address on the Internet side is not public.
type privateError struct{ ip netip.Addr }

func (e *privateError) Error() string {
	return fmt.Sprintf("the router's own address %s is not public (double NAT or carrier-grade NAT): a mapping would not make us reachable", e.ip)
}

// establish asks the router for its public address and a mapping.
func (m *Mapper) establish(c client) (established, error) {
	ctx, cancel := context.WithTimeout(m.ctx, 15*time.Second)
	defer cancel()
	ip, err := c.externalIP(ctx)
	if err != nil {
		return established{}, fmt.Errorf("asking for the router's public address: %w", err)
	}
	if !isPublicIPv4(ip) {
		return established{}, &privateError{ip}
	}
	port, lease, err := c.addMapping(ctx, m.cfg.Port, m.cfg.Port, leaseSeconds, false)
	if err != nil {
		return established{}, err
	}
	return established{addr: netip.AddrPortFrom(ip, uint16(port)), lease: lease}, nil
}

// client is one way of talking to a router.
type client interface {
	protocol() Protocol
	gatewayAddr() netip.Addr
	externalIP(ctx context.Context) (netip.Addr, error)
	// addMapping maps wantExternal -> internalPort (or another external port if
	// that one is taken) and reports what it got. permanent: ask for no expiry.
	addMapping(ctx context.Context, internalPort, wantExternal, lifetimeSeconds int, permanent bool) (external int, lease time.Duration, err error)
	deleteMapping(ctx context.Context, external int) error
}

// discover looks for a router that can map ports: UPnP first, then NAT-PMP.
func (m *Mapper) discover(ctx context.Context) (client, error) {
	m.set(Status{State: "searching"}, nil)
	addrs := m.cfg.LocalAddrs()
	if len(addrs) == 0 {
		return nil, errors.New("no network address to search from")
	}
	dctx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()
	var gwFromUPnP netip.Addr
	if c, gw, err := discoverUPnP(dctx, m.cfg, addrs); err == nil {
		return c, nil
	} else {
		gwFromUPnP = gw
		m.cfg.Logf("portmap: UPnP: %v", err)
	}
	gw := m.cfg.gateway()
	if !gw.IsValid() {
		gw = gwFromUPnP
	}
	if gw.IsValid() {
		if c, err := discoverNATPMP(dctx, m.cfg, gw); err == nil {
			return c, nil
		} else {
			m.cfg.Logf("portmap: NAT-PMP: %v", err)
		}
	}
	return nil, errors.New("no router answered (UPnP and NAT-PMP)")
}

// isPublicIPv4 reports whether ip can be reached from the Internet at large.
func isPublicIPv4(ip netip.Addr) bool {
	ip = ip.Unmap()
	if !ip.Is4() || ip.IsUnspecified() || ip.IsLoopback() || ip.IsPrivate() || ip.IsLinkLocalUnicast() ||
		ip.IsMulticast() || ip.IsInterfaceLocalMulticast() {
		return false
	}
	b := ip.As4()
	switch {
	case b[0] == 0, b[0] >= 240: // "this network", reserved, broadcast
		return false
	case b[0] == 100 && b[1]&0xC0 == 64: // 100.64.0.0/10 carrier-grade NAT
		return false
	case b[0] == 198 && (b[1] == 18 || b[1] == 19): // benchmarking
		return false
	}
	return true
}

// systemIPv4Addrs lists the IPv4 addresses of up, non-loopback interfaces.
func systemIPv4Addrs() []netip.Addr {
	var out []netip.Addr
	ifs, err := net.Interfaces()
	if err != nil {
		return nil
	}
	for _, ifc := range ifs {
		if ifc.Flags&net.FlagUp == 0 || ifc.Flags&net.FlagLoopback != 0 {
			continue
		}
		addrs, _ := ifc.Addrs()
		for _, a := range addrs {
			if ipn, ok := a.(*net.IPNet); ok {
				if ip, ok := netip.AddrFromSlice(ipn.IP.To4()); ok && ip.IsValid() && !ip.IsLinkLocalUnicast() {
					out = append(out, ip)
				}
			}
		}
	}
	return out
}

var errNoRouter = errors.New("no router")

func newHTTPClient() *http.Client {
	return &http.Client{
		Timeout: 5 * time.Second,
		Transport: &http.Transport{
			Proxy:             nil, // never through a proxy: this is a conversation with the router
			DisableKeepAlives: true,
			DialContext:       (&net.Dialer{Timeout: 3 * time.Second}).DialContext,
		},
		CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse },
	}
}
