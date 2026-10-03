// Package tun gives the mesh a real network interface: every device has a
// stable overlay address (100.64.0.0/10 and an fd00::/8 prefix) and any
// program - ssh, a browser, a media player, SMB - can talk to other devices by
// that address or by <name>.svoi, with no application support. IP packets are
// carried over the same encrypted QUIC links as everything else (as unreliable
// datagrams, like WireGuard does).
//
// Only Linux is supported at the moment; elsewhere use port forwards or the
// SOCKS5 proxy.
package tun

import (
	"context"
	"errors"
	"fmt"
	"net"
	"net/netip"
	"os"
	"sort"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/mesh"
)

const (
	// DefaultName is the interface name.
	DefaultName = "svoi0"
	// MTU leaves room inside a QUIC datagram (one packet of 1280 bytes) for the
	// QUIC and framing overhead.
	MTU = 1150

	datagramKind byte = 0x01
)

// Status describes the TUN state for the UI.
type Status struct {
	State     string `json:"state"` // off | running | error
	Name      string `json:"name"`
	Error     string `json:"error"`
	Supported bool   `json:"supported"`
	TxPackets uint64 `json:"txPackets"`
	RxPackets uint64 `json:"rxPackets"`
	Dropped   uint64 `json:"dropped"`
}

// Manager owns the TUN interface.
type Manager struct {
	node        *mesh.Node
	name        string
	manageHosts atomic.Bool

	mu     sync.Mutex
	dev    *device
	cancel context.CancelFunc
	errMsg string
	routes atomic.Pointer[routeTable]
	hasV6  atomic.Bool

	tx, rx, dropped atomic.Uint64
}

type routeTable struct {
	byAddr map[netip.Addr]*mesh.Peer
	self4  netip.Addr
	self6  netip.Addr
}

// New creates a manager (it does nothing until Start).
func New(node *mesh.Node, name string, manageHosts bool) *Manager {
	if name == "" {
		name = DefaultName
	}
	m := &Manager{node: node, name: name}
	m.manageHosts.Store(manageHosts)
	node.HandleDatagram(datagramKind, m.onDatagram)
	return m
}

// SetManageHosts switches maintenance of the <name>.svoi block in /etc/hosts.
func (m *Manager) SetManageHosts(on bool) {
	m.manageHosts.Store(on)
	if !on {
		_ = writeHosts(nil)
	} else if m.Status().State == "running" {
		m.rebuildRoutes()
		m.syncHosts()
	}
}

// Status returns the current state.
func (m *Manager) Status() Status {
	m.mu.Lock()
	defer m.mu.Unlock()
	st := Status{Supported: Supported, Name: m.name, TxPackets: m.tx.Load(), RxPackets: m.rx.Load(), Dropped: m.dropped.Load()}
	switch {
	case m.dev != nil:
		st.State = "running"
	case m.errMsg != "":
		st.State, st.Error = "error", m.errMsg
	default:
		st.State = "off"
	}
	return st
}

// Start creates and configures the interface and starts moving packets.
func (m *Manager) Start() error {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.dev != nil {
		return nil
	}
	fail := func(err error) error {
		m.errMsg = err.Error()
		return err
	}
	self := m.node.Self()
	if !self.Configured {
		return fail(mesh.ErrNotConfigured)
	}
	ip4, err4 := netip.ParseAddr(self.IP4)
	ip6, err6 := netip.ParseAddr(self.IP6)
	if err4 != nil || err6 != nil {
		return fail(errors.New("this device has no overlay address yet"))
	}
	dev, err := createDevice(m.name)
	if err != nil {
		return fail(err)
	}
	hasV6, err := dev.configure(
		&net.IPNet{IP: ip4.AsSlice(), Mask: net.CIDRMask(10, 32)},
		&net.IPNet{IP: ip6.AsSlice(), Mask: net.CIDRMask(48, 128)},
		MTU)
	if err != nil {
		dev.Close()
		return fail(err)
	}
	m.hasV6.Store(hasV6)
	if !hasV6 {
		m.node.Logger().Warn("IPv6 is unavailable on this system; the overlay works over IPv4 only")
	}
	ctx, cancel := context.WithCancel(context.Background())
	m.dev, m.cancel, m.errMsg = dev, cancel, ""
	m.rebuildRoutes()
	go m.readLoop(ctx, dev)
	go m.watchPeers(ctx)
	m.node.Logger().Info("TUN interface up", "name", dev.Name(), "ip4", ip4, "ip6", ip6)
	return nil
}

// Stop removes the interface.
func (m *Manager) Stop() {
	m.mu.Lock()
	dev, cancel := m.dev, m.cancel
	m.dev, m.cancel, m.errMsg = nil, nil, ""
	m.mu.Unlock()
	if cancel != nil {
		cancel()
	}
	if dev != nil {
		dev.Close()
	}
	if m.manageHosts.Load() {
		_ = writeHosts(nil)
	}
}

func (m *Manager) rebuildRoutes() {
	self := m.node.Self()
	rt := &routeTable{byAddr: map[netip.Addr]*mesh.Peer{}}
	rt.self4, _ = netip.ParseAddr(self.IP4)
	rt.self6, _ = netip.ParseAddr(self.IP6)
	for _, p := range m.node.Peers() {
		mem := p.Member()
		if mem.IPv4.IsValid() {
			rt.byAddr[mem.IPv4] = p
		}
		if mem.IPv6.IsValid() {
			rt.byAddr[mem.IPv6] = p
		}
	}
	m.routes.Store(rt)
}

// syncHosts rewrites the /etc/hosts block from the current member list.
func (m *Manager) syncHosts() {
	if !m.manageHosts.Load() {
		return
	}
	var entries []hostEntry
	for _, p := range m.node.Peers() {
		mem := p.Member()
		entries = append(entries, hostEntry{mem.IPv4.String(), mem.Name + "." + identity.DNSSuffix})
		// Without IPv6 on this system an AAAA-first lookup would stall connections.
		if m.hasV6.Load() {
			entries = append(entries, hostEntry{mem.IPv6.String(), mem.Name + "." + identity.DNSSuffix})
		}
	}
	if err := writeHosts(entries); err != nil {
		m.node.Logger().Debug("cannot update /etc/hosts", "err", err)
	}
}

func (m *Manager) watchPeers(ctx context.Context) {
	events, cancel := m.node.Subscribe()
	defer cancel()
	deb := time.NewTimer(time.Hour)
	deb.Stop()
	update := func() {
		m.rebuildRoutes()
		m.syncHosts()
	}
	update()
	for {
		select {
		case <-ctx.Done():
			return
		case ev := <-events:
			if ev.Kind == mesh.EvMembers {
				deb.Reset(300 * time.Millisecond)
			}
		case <-deb.C:
			update()
		}
	}
}

// ---- data plane ----

// packetDst extracts the destination address of an IP packet.
func packetAddrs(pkt []byte) (src, dst netip.Addr, ok bool) {
	if len(pkt) < 1 {
		return
	}
	switch pkt[0] >> 4 {
	case 4:
		if len(pkt) < 20 {
			return
		}
		ihl := int(pkt[0]&0x0f) * 4
		total := int(pkt[2])<<8 | int(pkt[3])
		if ihl < 20 || total < ihl || total > len(pkt) {
			return
		}
		src, _ = netip.AddrFromSlice(pkt[12:16])
		dst, _ = netip.AddrFromSlice(pkt[16:20])
		return src, dst, true
	case 6:
		if len(pkt) < 40 {
			return
		}
		plen := int(pkt[4])<<8 | int(pkt[5])
		if 40+plen > len(pkt) {
			return
		}
		src, _ = netip.AddrFromSlice(pkt[8:24])
		dst, _ = netip.AddrFromSlice(pkt[24:40])
		return src, dst, true
	}
	return
}

func (m *Manager) readLoop(ctx context.Context, dev *device) {
	buf := make([]byte, 4096)
	for {
		n, err := dev.Read(buf)
		if err != nil {
			if ctx.Err() != nil || errors.Is(err, os.ErrClosed) {
				return
			}
			m.node.Logger().Warn("TUN read failed", "err", err)
			time.Sleep(100 * time.Millisecond)
			continue
		}
		if n == 0 {
			continue
		}
		_, dst, ok := packetAddrs(buf[:n])
		if !ok {
			m.dropped.Add(1)
			continue
		}
		rt := m.routes.Load()
		if rt == nil {
			continue
		}
		peer := rt.byAddr[dst]
		if peer == nil {
			m.dropped.Add(1)
			continue
		}
		out := make([]byte, 1+n)
		out[0] = datagramKind
		copy(out[1:], buf[:n])
		if err := peer.SendDatagram(out); err != nil {
			m.dropped.Add(1)
			continue
		}
		m.tx.Add(1)
	}
}

// onDatagram receives an IP packet from a peer and hands it to the kernel,
// after checking that the peer really owns the source address ("cryptokey
// routing": a member can only send packets from its own overlay address).
func (m *Manager) onDatagram(p *mesh.Peer, data []byte) {
	m.mu.Lock()
	dev := m.dev
	m.mu.Unlock()
	rt := m.routes.Load()
	if dev == nil || rt == nil {
		return
	}
	src, dst, ok := packetAddrs(data)
	if !ok {
		m.dropped.Add(1)
		return
	}
	mem := p.Member()
	if src != mem.IPv4 && src != mem.IPv6 {
		m.dropped.Add(1)
		return
	}
	if dst != rt.self4 && dst != rt.self6 {
		m.dropped.Add(1)
		return
	}
	if _, err := dev.Write(data); err != nil {
		m.dropped.Add(1)
		return
	}
	m.rx.Add(1)
}

// ---- /etc/hosts ----

type hostEntry struct{ ip, name string }

const (
	hostsBegin = "# BEGIN svoi"
	hostsEnd   = "# END svoi"
)

// hostsPath is a variable only so tests can point it elsewhere.
var hostsPath = "/etc/hosts"

// writeHosts replaces the svoi block of /etc/hosts (nil removes it). Everything
// outside the block stays byte for byte as it was, and when there is nothing to
// change the file is not written at all (not even to tidy it up).
func writeHosts(entries []hostEntry) error {
	raw, err := os.ReadFile(hostsPath)
	if err != nil {
		return err
	}
	var kept []string // lines outside our block, each with its line ending
	hadBlock, skipping := false, false
	for _, line := range strings.SplitAfter(string(raw), "\n") {
		switch trimmed := strings.TrimSpace(line); {
		case trimmed == hostsBegin:
			hadBlock, skipping = true, true
		case trimmed == hostsEnd:
			skipping = false
		case !skipping && line != "":
			kept = append(kept, line)
		}
	}
	if len(entries) == 0 && !hadBlock {
		return nil // never ours, never touched
	}
	text := strings.Join(kept, "")
	if len(entries) > 0 {
		if text != "" && !strings.HasSuffix(text, "\n") {
			text += "\n"
		}
		sort.Slice(entries, func(i, j int) bool { return entries[i].name+entries[i].ip < entries[j].name+entries[j].ip })
		var b strings.Builder
		b.WriteString(hostsBegin + "\n")
		for _, e := range entries {
			fmt.Fprintf(&b, "%s %s\n", e.ip, e.name)
		}
		b.WriteString(hostsEnd + "\n")
		text += b.String()
	}
	if text == string(raw) {
		return nil
	}
	// Written in place: /etc/hosts is often a bind mount that cannot be replaced.
	return os.WriteFile(hostsPath, []byte(text), 0o644)
}
