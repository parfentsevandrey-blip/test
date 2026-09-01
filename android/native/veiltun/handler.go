package veiltun

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"io"
	"net"
	"net/netip"
	"strconv"
	"sync"
	"time"

	"github.com/xjasonlyu/tun2socks/v2/core/adapter"
	"golang.org/x/net/proxy"
	"gvisor.dev/gvisor/pkg/tcpip"
	"gvisor.dev/gvisor/pkg/tcpip/stack"
)

const relayBufSize = 32 * 1024

var bufPool = sync.Pool{New: func() any { b := make([]byte, relayBufSize); return &b }}

type handler struct {
	cfg *Config

	// sessionSalt keeps derived SOCKS passwords unpredictable and makes
	// circuits differ between app runs.
	sessionSalt string

	dnsResolver dnsResolver
	bypass      *bypass

	ctx    context.Context
	cancel context.CancelFunc
	wg     sync.WaitGroup
}

func newHandler(cfg *Config) (*handler, error) {
	salt := make([]byte, 8)
	if _, err := rand.Read(salt); err != nil {
		return nil, err
	}
	ctx, cancel := context.WithCancel(context.Background())
	h := &handler{
		cfg:         cfg,
		sessionSalt: hex.EncodeToString(salt),
		ctx:         ctx,
		cancel:      cancel,
	}
	r, err := newDNSResolver(cfg, h.dialUpstream)
	if err != nil {
		cancel()
		return nil, err
	}
	h.dnsResolver = r
	h.bypass = newBypass(cfg.BypassSuffixes, cfg.BypassDNS)
	if h.bypass.enabled() {
		logf("info", "bypassing the tunnel for %v via %v", h.bypass.suffixes, h.bypass.resolvers)
	}
	return h, nil
}

func (h *handler) Close() {
	h.cancel()
	if h.dnsResolver != nil {
		h.dnsResolver.Close()
	}
	// Give in-flight relays a moment to notice the cancellation, but never
	// block teardown of the UI thread's stop request for long.
	done := make(chan struct{})
	go func() { h.wg.Wait(); close(done) }()
	select {
	case <-done:
	case <-time.After(2 * time.Second):
	}
}

// credentialsFor derives SOCKS5 credentials so Tor's IsolateSOCKSAuth gives us
// the isolation policy the user picked.
func (h *handler) credentialsFor(dst netip.AddrPort) (user, pass string) {
	switch h.cfg.IsolateBy {
	case IsolateConn:
		b := make([]byte, 8)
		_, _ = rand.Read(b)
		return hex.EncodeToString(b), h.sessionSalt
	case IsolateHost:
		return dst.Addr().String(), h.sessionSalt
	default:
		return h.cfg.SocksUser, h.cfg.SocksPass
	}
}

// dialUpstream opens a TCP connection to addr through the SOCKS5 proxy.
func (h *handler) dialUpstream(ctx context.Context, network, addr string) (net.Conn, error) {
	var auth *proxy.Auth
	user, pass := h.cfg.SocksUser, h.cfg.SocksPass
	if ap, err := netip.ParseAddrPort(addr); err == nil {
		user, pass = h.credentialsFor(ap)
	}
	if user != "" || pass != "" {
		auth = &proxy.Auth{User: user, Password: pass}
	}
	d, err := proxy.SOCKS5(h.cfg.socksNetwork(), h.cfg.SocksAddr, auth, &net.Dialer{
		Timeout: time.Duration(h.cfg.DialTimeoutSec) * time.Second,
	})
	if err != nil {
		return nil, err
	}
	cd, ok := d.(proxy.ContextDialer)
	if !ok {
		return d.Dial(network, addr)
	}
	return cd.DialContext(ctx, network, addr)
}

// HandleTCP is called by the netstack for every accepted TCP flow.
func (h *handler) HandleTCP(conn adapter.TCPConn) {
	h.wg.Add(1)
	go func() {
		defer h.wg.Done()
		h.relayTCP(conn)
	}()
}

func (h *handler) relayTCP(conn adapter.TCPConn) {
	defer conn.Close()

	dst := endpointDst(conn.ID())
	stats.tcpTotal.Add(1)
	stats.tcpOpen.Add(1)
	defer stats.tcpOpen.Add(-1)

	ctx, cancel := context.WithTimeout(h.ctx, time.Duration(h.cfg.DialTimeoutSec)*time.Second)
	var up net.Conn
	var err error
	if h.bypass.shouldDialDirect(dst.Addr()) {
		// Resolved from a name the user asked to keep off the tunnel.
		up, err = (&net.Dialer{}).DialContext(ctx, "tcp", dst.String())
	} else {
		up, err = h.dialUpstream(ctx, "tcp", dst.String())
	}
	cancel()
	if err != nil {
		stats.dialErrors.Add(1)
		logf("warn", "tcp %s: %v", dst, err)
		return
	}
	defer up.Close()

	relay(conn, up)
}

// HandleUDP is called by the netstack for every UDP flow.
func (h *handler) HandleUDP(conn adapter.UDPConn) {
	h.wg.Add(1)
	go func() {
		defer h.wg.Done()
		h.serveUDP(conn)
	}()
}

func (h *handler) serveUDP(conn adapter.UDPConn) {
	defer conn.Close()

	dst := endpointDst(conn.ID())
	stats.udpTotal.Add(1)

	if dst.Port() == 53 {
		h.serveDNS(conn)
		return
	}
	if h.bypass.shouldDialDirect(dst.Addr()) {
		h.relayUDPDirect(conn, dst)
		return
	}
	if h.cfg.BlockUDP {
		// Tor has no UDP transport. Silently dropping is the correct and
		// leak-free behaviour: QUIC and WebRTC fall back to TCP.
		stats.blocked.Add(1)
		return
	}
	h.relayUDP(conn, dst)
}

// serveDNS answers queries on this flow through the configured resolver, so
// that name resolution is never exposed to the local network.
func (h *handler) serveDNS(conn adapter.UDPConn) {
	idle := time.Duration(h.cfg.UDPTimeoutSec) * time.Second
	buf := make([]byte, 1500)
	for {
		_ = conn.SetReadDeadline(time.Now().Add(idle))
		// net.Conn semantics, not PacketConn: the endpoint is already bound to
		// this flow's 5-tuple, and gVisor rejects a write that names a
		// destination on a connected endpoint.
		n, err := conn.Read(buf)
		if err != nil {
			return
		}
		if n < 12 { // shorter than a DNS header
			continue
		}
		query := make([]byte, n)
		copy(query, buf[:n])

		h.wg.Add(1)
		go func() {
			defer h.wg.Done()
			stats.dnsQueries.Add(1)
			ctx, cancel := context.WithTimeout(h.ctx, 20*time.Second)
			resp, err := h.resolve(ctx, query)
			cancel()
			if err != nil {
				stats.dnsErrors.Add(1)
				logf("warn", "dns via %s: %v", h.cfg.DNSMode, err)
				if resp = servFail(query); resp == nil {
					return
				}
			}
			_ = conn.SetWriteDeadline(time.Now().Add(10 * time.Second))
			if _, err := conn.Write(resp); err != nil {
				logf("warn", "dns reply: %v", err)
			}
		}()
	}
}

// resolve answers a query, sending it around the tunnel when the name is one
// the user asked to keep off it.
//
// A bypassed lookup is deliberately not routed through tor: the point is to
// obtain the destination's real address, and tor's AutomapHostsOnResolve would
// hand back a virtual one that only tor can reach. The addresses that come back
// are remembered so the connection that follows can be dialled directly.
func (h *handler) resolve(ctx context.Context, query []byte) ([]byte, error) {
	if h.bypass.enabled() {
		if name := queryName(query); name != "" && h.bypass.matchesName(name) {
			resp, err := h.bypassExchange(ctx, query)
			if err == nil {
				for _, answer := range answerAddresses(resp) {
					h.bypass.remember(answer.addr, answer.ttl)
				}
				return resp, nil
			}
			// A resolver on the local network that will not answer is not a
			// reason to fail the lookup; fall back to the tunnel.
			logf("warn", "bypass lookup for %s failed, using the tunnel: %v", name, err)
		}
	}
	return h.dnsResolver.Exchange(ctx, query)
}

// bypassExchange asks a resolver on the real network, trying each in turn.
func (h *handler) bypassExchange(ctx context.Context, query []byte) ([]byte, error) {
	var lastErr error
	for _, resolver := range h.bypass.resolvers {
		r := &udpResolver{addr: resolver}
		resp, err := r.Exchange(ctx, query)
		if err == nil {
			return resp, nil
		}
		lastErr = err
	}
	if lastErr == nil {
		lastErr = errNoBypassResolver
	}
	return nil, lastErr
}

// servFail turns a query into a SERVFAIL response so the client fails fast
// instead of waiting out its full timeout.
func servFail(query []byte) []byte {
	if len(query) < 12 {
		return nil
	}
	resp := make([]byte, 12)
	copy(resp, query[:12])
	resp[2] = 0x81 // QR=1, RD copied loosely
	resp[3] = 0x82 // RA=1, RCODE=2 (SERVFAIL)
	resp[4], resp[5] = 0, 0
	resp[6], resp[7] = 0, 0
	resp[8], resp[9] = 0, 0
	resp[10], resp[11] = 0, 0
	return resp
}

func endpointDst(id stack.TransportEndpointID) netip.AddrPort {
	return netip.AddrPortFrom(addrFrom(id.LocalAddress), id.LocalPort)
}

func addrFrom(a tcpip.Address) netip.Addr {
	s := a.AsSlice()
	addr, _ := netip.AddrFromSlice(s)
	return addr.Unmap()
}

// relay copies in both directions and stops as soon as either side finishes.
func relay(local io.ReadWriteCloser, remote net.Conn) {
	done := make(chan struct{}, 2)

	go func() {
		b := bufPool.Get().(*[]byte)
		defer bufPool.Put(b)
		n, _ := io.CopyBuffer(remote, local, *b)
		stats.tx.Add(n)
		if cw, ok := remote.(interface{ CloseWrite() error }); ok {
			_ = cw.CloseWrite()
		} else {
			_ = remote.Close()
		}
		done <- struct{}{}
	}()

	go func() {
		b := bufPool.Get().(*[]byte)
		defer bufPool.Put(b)
		n, _ := io.CopyBuffer(local, remote, *b)
		stats.rx.Add(n)
		_ = local.Close()
		done <- struct{}{}
	}()

	<-done
	<-done
}

var (
	errNoUDP            = errors.New("upstream proxy does not support UDP")
	errNoBypassResolver = errors.New("no bypass resolver answered")
)

func joinHostPort(host string, port uint16) string {
	return net.JoinHostPort(host, strconv.Itoa(int(port)))
}
