//go:build windows

package tunnel

import (
	"context"
	"errors"
	"fmt"
	"net"
	"net/netip"
	"sync"
	"time"

	t2core "github.com/xjasonlyu/tun2socks/v2/core"
	t2device "github.com/xjasonlyu/tun2socks/v2/core/device"
	t2tun "github.com/xjasonlyu/tun2socks/v2/core/device/tun"
	t2meta "github.com/xjasonlyu/tun2socks/v2/metadata"
	t2tunnel "github.com/xjasonlyu/tun2socks/v2/tunnel"
	"golang.org/x/sys/windows"
	"golang.zx2c4.com/wireguard/windows/tunnel/winipcfg"
	gstack "gvisor.dev/gvisor/pkg/tcpip/stack"
)

// adapterWaitTimeout bounds how long we wait for Windows to publish the
// freshly created Wintun adapter in the interface table.
const adapterWaitTimeout = 15 * time.Second

// Tunnel is a running full-tunnel session.
type Tunnel struct {
	opts Options

	device   t2device.Device
	stack    *gstack.Stack
	dns      *DNSForwarder
	bypass   *bypassWatcher
	firewall *firewall

	tunLUID     winipcfg.LUID
	physLUID    winipcfg.LUID
	physGateway netip.Addr

	lanRoutes []netip.Prefix
	warnings  []string

	mu       sync.Mutex
	stopOnce sync.Once
	running  bool
}

// torDialer adapts TorVeil's shaped dialer to the interface the userspace
// network stack expects.
type torDialer struct {
	dial Dialer
}

func (d *torDialer) DialContext(ctx context.Context, m *t2meta.Metadata) (net.Conn, error) {
	return d.dial.DialContext(ctx, "tcp", m.DestinationAddress())
}

// DialUDP refuses every UDP flow. Tor has no UDP transport, so there is
// nowhere for these packets to go; failing immediately is better than holding
// the flow open until the application times out.
func (d *torDialer) DialUDP(*t2meta.Metadata) (net.PacketConn, error) {
	return nil, errors.New("tunnel: Tor carries TCP only, UDP cannot be tunnelled")
}

// Start brings the tunnel up.
//
// The order matters. The DNS forwarder and the bypass routes are established
// before the default route moves, so that from the moment traffic starts
// following the tunnel there is already a way for Tor's own connections to get
// out and for names to be resolved.
func Start(opts Options) (t *Tunnel, err error) {
	if err := opts.validate(); err != nil {
		return nil, err
	}
	if !isElevated() {
		return nil, ErrNeedsAdmin
	}

	physLUID, physGateway, err := findDefaultRoute()
	if err != nil {
		return nil, fmt.Errorf("tunnel: %w", err)
	}

	t = &Tunnel{
		opts:        opts,
		physLUID:    physLUID,
		physGateway: physGateway,
	}
	defer func() {
		if err != nil {
			t.teardown()
		}
	}()

	// Refuse to start without working DNS. Bringing the tunnel up with no
	// resolver would leave the machine unable to resolve anything, and the
	// obvious "fix" a user would reach for is to point DNS back at their ISP,
	// which is exactly the leak this is meant to prevent.
	t.dns = NewDNSForwarder(opts.DNSUpstream, opts.Log)
	if err := t.dns.Start(opts.DNSListen); err != nil {
		return nil, fmt.Errorf("tunnel: %w (is another DNS server bound to %s?)", err, opts.DNSListen)
	}

	t.bypass = newBypassWatcher(opts.WatchProcesses, physLUID, physGateway, opts.Address.Masked(), opts.Log)
	for _, p := range opts.BypassCIDRs {
		t.bypass.AddStatic(p)
	}
	// Pick up the connections Tor already holds before anything is rerouted.
	t.bypass.sweep()
	t.bypass.start()

	if opts.ExcludeLAN {
		t.addLANRoutes()
	}

	device, err := t2tun.Open(opts.AdapterName, uint32(opts.MTU))
	if err != nil {
		return nil, fmt.Errorf("tunnel: create Wintun adapter %q: %w (is wintun.dll next to the executable?)", opts.AdapterName, err)
	}
	t.device = device

	t2tunnel.T().SetProxy(&torDialer{dial: opts.Dialer})
	t2tunnel.T().SetUDPTimeout(30 * time.Second)

	stk, err := t2core.CreateStack(&t2core.Config{
		LinkEndpoint:     device,
		TransportHandler: t2tunnel.T(),
	})
	if err != nil {
		return nil, fmt.Errorf("tunnel: create network stack: %w", err)
	}
	t.stack = stk

	luid, err := adapterLUID(opts.AdapterName)
	if err != nil {
		return nil, fmt.Errorf("tunnel: %w", err)
	}
	t.tunLUID = luid

	if err := t.configureInterface(); err != nil {
		return nil, fmt.Errorf("tunnel: %w", err)
	}

	if err := t.firewallRules(); err != nil {
		// Leak protection failing is worth surfacing, but it does not stop
		// the tunnel from carrying traffic.
		t.warnings = append(t.warnings, err.Error())
		opts.logf("warn", "%v", err)
	}

	t.mu.Lock()
	t.running = true
	t.mu.Unlock()

	opts.logf("info", "tunnel up on %q (%s), DNS via %s", opts.AdapterName, opts.Address, opts.DNSListen)
	return t, nil
}

// configureInterface assigns the address, metric and routes to the adapter.
func (t *Tunnel) configureInterface() error {
	luid := t.tunLUID

	if err := luid.SetIPAddressesForFamily(windows.AF_INET, []netip.Prefix{t.opts.Address}); err != nil {
		return fmt.Errorf("assign %s to the adapter: %w", t.opts.Address, err)
	}

	// A low, fixed interface metric makes the tunnel's routes win against the
	// physical interface's, which Windows would otherwise decide by link speed.
	if row, err := luid.IPInterface(windows.AF_INET); err == nil {
		row.UseAutomaticMetric = false
		row.Metric = 1
		row.NLMTU = uint32(t.opts.MTU)
		if err := row.Set(); err != nil {
			t.opts.logf("warn", "set interface metric: %v", err)
		}
	}

	routes := make([]*winipcfg.RouteData, 0, 3)
	for _, p := range defaultSplit() {
		routes = append(routes, &winipcfg.RouteData{Destination: p, NextHop: t.opts.Gateway, Metric: 0})
	}
	// Onion addresses resolve to an automapped virtual address; it has to
	// route into the tunnel for Tor to map it back on the way out.
	routes = append(routes, &winipcfg.RouteData{Destination: AutomapPrefix, NextHop: t.opts.Gateway, Metric: 0})

	if err := luid.AddRoutes(routes); err != nil {
		return fmt.Errorf("install tunnel routes: %w", err)
	}

	// Point the system at the loopback forwarder rather than at a server
	// inside the tunnel: DNS is UDP, and the tunnel cannot carry UDP.
	dnsHost, _, err := net.SplitHostPort(t.opts.DNSListen)
	if err != nil {
		return fmt.Errorf("parse DNS listen address: %w", err)
	}
	dnsAddr, err := netip.ParseAddr(dnsHost)
	if err != nil {
		return fmt.Errorf("parse DNS listen address: %w", err)
	}
	if err := luid.SetDNS(windows.AF_INET, []netip.Addr{dnsAddr}, nil); err != nil {
		return fmt.Errorf("set adapter DNS to %s: %w", dnsAddr, err)
	}
	return nil
}

// addLANRoutes keeps private ranges on the physical interface so local
// devices stay reachable.
func (t *Tunnel) addLANRoutes() {
	for _, p := range lanPrefixes() {
		if err := t.physLUID.AddRoute(p, t.physGateway, 0); err != nil {
			if !isAlreadyExists(err) {
				t.opts.logf("info", "keep %s on the local network: %v", p, err)
			}
			continue
		}
		t.lanRoutes = append(t.lanRoutes, p)
	}
}

func (t *Tunnel) firewallRules() error {
	t.firewall = newFirewall(t.opts.StateDir, t.opts.Log)
	return t.firewall.ApplyLeakRules(t.opts)
}

// Stop tears the tunnel down and restores the previous network configuration.
func (t *Tunnel) Stop() error {
	t.stopOnce.Do(func() { t.teardown() })
	return nil
}

// EngageKillSwitch blocks outbound traffic until the tunnel returns. It is
// meant for the case where the tunnel went away unexpectedly and traffic would
// otherwise silently revert to the open network.
func (t *Tunnel) EngageKillSwitch() error {
	if t.firewall == nil {
		t.firewall = newFirewall(t.opts.StateDir, t.opts.Log)
	}
	return t.firewall.EngageLockdown(t.opts.AllowPrograms)
}

func (t *Tunnel) teardown() {
	t.mu.Lock()
	t.running = false
	t.mu.Unlock()

	if t.firewall != nil {
		t.firewall.Clear()
	}
	if t.bypass != nil {
		t.bypass.stop()
	}
	for _, p := range t.lanRoutes {
		if err := t.physLUID.DeleteRoute(p, t.physGateway); err != nil && !isNotFound(err) {
			t.opts.logf("info", "remove LAN route %s: %v", p, err)
		}
	}
	t.lanRoutes = nil

	if t.stack != nil {
		t.stack.Close()
		t.stack.Wait()
		t.stack = nil
	}
	if t.device != nil {
		t.device.Close()
		t.device = nil
	}
	// Hand the userspace stack back to its rejecting default so a stale proxy
	// reference cannot serve traffic after shutdown.
	t2tunnel.T().SetProxy(rejectProxy{})

	if t.dns != nil {
		t.dns.Stop()
		t.dns = nil
	}
	t.opts.logf("info", "tunnel stopped")
}

// rejectProxy refuses everything; it replaces the live dialer at shutdown.
type rejectProxy struct{}

func (rejectProxy) DialContext(context.Context, *t2meta.Metadata) (net.Conn, error) {
	return nil, errors.New("tunnel: stopped")
}

func (rejectProxy) DialUDP(*t2meta.Metadata) (net.PacketConn, error) {
	return nil, errors.New("tunnel: stopped")
}

// Status reports the tunnel's current state.
func (t *Tunnel) Status() Status {
	t.mu.Lock()
	running := t.running
	t.mu.Unlock()

	s := Status{
		Running:    running,
		Adapter:    t.opts.AdapterName,
		Address:    t.opts.Address.String(),
		DNSListen:  t.opts.DNSListen,
		KillSwitch: t.opts.KillSwitch,
		Warnings:   append([]string(nil), t.warnings...),
	}
	if t.bypass != nil {
		s.BypassRoutes = t.bypass.routes()
	}
	return s
}

// findDefaultRoute returns the interface and gateway currently carrying the
// default route, which is where Tor's own traffic has to keep going once the
// tunnel takes over.
func findDefaultRoute() (winipcfg.LUID, netip.Addr, error) {
	rows, err := winipcfg.GetIPForwardTable2(windows.AF_INET)
	if err != nil {
		return 0, netip.Addr{}, fmt.Errorf("read routing table: %w", err)
	}

	ifaceMetric := map[winipcfg.LUID]uint32{}
	if ifaces, err := winipcfg.GetIPInterfaceTable(windows.AF_INET); err == nil {
		for i := range ifaces {
			ifaceMetric[ifaces[i].InterfaceLUID] = ifaces[i].Metric
		}
	}

	var (
		bestLUID   winipcfg.LUID
		bestGW     netip.Addr
		bestMetric = ^uint32(0)
		found      bool
	)
	for i := range rows {
		row := &rows[i]
		prefix := row.DestinationPrefix.Prefix()
		if !prefix.IsValid() || prefix.Bits() != 0 || !prefix.Addr().Is4() {
			continue
		}
		gw := row.NextHop.Addr()
		if !gw.IsValid() || gw.IsUnspecified() {
			continue
		}
		metric := row.Metric + ifaceMetric[row.InterfaceLUID]
		if metric < bestMetric {
			bestLUID, bestGW, bestMetric, found = row.InterfaceLUID, gw, metric, true
		}
	}
	if !found {
		return 0, netip.Addr{}, errors.New("no IPv4 default route found; the machine appears to be offline")
	}
	return bestLUID, bestGW, nil
}

// adapterLUID finds the LUID of the adapter with the given friendly name,
// waiting for Windows to publish it.
func adapterLUID(name string) (winipcfg.LUID, error) {
	deadline := time.Now().Add(adapterWaitTimeout)
	for {
		adapters, err := winipcfg.GetAdaptersAddresses(windows.AF_UNSPEC, winipcfg.GAAFlagIncludeAll)
		if err == nil {
			for _, a := range adapters {
				if a.FriendlyName() == name {
					return a.LUID, nil
				}
			}
		}
		if time.Now().After(deadline) {
			return 0, fmt.Errorf("adapter %q did not appear within %s", name, adapterWaitTimeout)
		}
		time.Sleep(200 * time.Millisecond)
	}
}

// isElevated reports whether the process is running with administrator rights,
// which Wintun and the routing table both require.
func isElevated() bool {
	return windows.GetCurrentProcessToken().IsElevated()
}
