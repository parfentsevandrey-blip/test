package veiltun

// The HTTP proxy for the mode in which there is no tunnel.
//
// On macOS a packet tunnel needs an entitlement that Apple issues only to a
// paid developer team. Without one the app falls back on the system's proxy
// settings, and those come in three parts: a SOCKS proxy, which tor serves
// itself, and an HTTP and an HTTPS proxy, which it does not. tor's
// HTTPTunnelPort speaks CONNECT and nothing else — a browser fetching an
// http:// page sends a plain GET with an absolute URL to its HTTP proxy, and
// tor answers that with 405. So this is the small proxy that stands in front:
// CONNECT for anything TLS, absolute-URI requests for plain HTTP, and every
// connection dialled through tor's SOCKS port *by name*, so that nothing is
// resolved on the local network.
//
// It runs in the app's process rather than the extension's, because in that
// mode it is the app that owns tor. The ad blocker applies here as it does in
// the tunnel's resolver — same list, same parent-domain rule — and a re-dial
// underneath holds connections open the same way the tunnel does.

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"

	"golang.org/x/net/proxy"
)

type httpProxy struct {
	listener  net.Listener
	server    *http.Server
	transport *http.Transport
	dialer    proxy.ContextDialer
	block     *blocklist
	ctx       context.Context
	cancel    context.CancelFunc
}

var (
	httpProxyMu      sync.Mutex
	httpProxyCurrent *httpProxy
)

// How long one upstream dial may take. Long enough for a first stream over
// Snowflake, which is the slow case that matters.
const httpProxyDialTimeout = 30 * time.Second

// StartHTTPProxy starts the proxy on a loopback port of the system's choosing
// and returns that port.
//
// socksNetwork is "unix" or "tcp" and socksAddr the matching address of tor's
// SOCKS listener. blocklistPath may be empty, in which case nothing is blocked.
func StartHTTPProxy(socksNetwork, socksAddr, blocklistPath string) (int, error) {
	httpProxyMu.Lock()
	defer httpProxyMu.Unlock()
	if httpProxyCurrent != nil {
		return 0, errors.New("veiltun: http proxy already running")
	}
	if socksAddr == "" {
		return 0, errors.New("veiltun: empty socks address")
	}
	if socksNetwork == "" {
		socksNetwork = "tcp"
	}

	d, err := proxy.SOCKS5(socksNetwork, socksAddr, nil, &net.Dialer{Timeout: httpProxyDialTimeout})
	if err != nil {
		return 0, fmt.Errorf("veiltun: socks dialer: %w", err)
	}
	dialer, ok := d.(proxy.ContextDialer)
	if !ok {
		return 0, errors.New("veiltun: socks dialer cannot dial with a context")
	}

	var block *blocklist
	if blocklistPath != "" {
		block, err = loadBlocklist(blocklistPath)
		if err != nil {
			// A proxy without an ad blocker still carries traffic; a proxy
			// that refused to start would not.
			logf("warn", "http proxy: blocklist not loaded: %v", err)
			block = nil
		}
	}

	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return 0, fmt.Errorf("veiltun: listen: %w", err)
	}

	ctx, cancel := context.WithCancel(context.Background())
	p := &httpProxy{listener: listener, dialer: dialer, block: block, ctx: ctx, cancel: cancel}
	p.transport = &http.Transport{
		Proxy:       nil,
		DialContext: p.dialHeld,
		// Bodies pass through as they are. The browser asked for whatever
		// encodings it asked for and must get exactly those back; a proxy
		// that decompressed on its behalf would hand it a body whose
		// headers no longer describe it.
		DisableCompression:    true,
		ForceAttemptHTTP2:     false,
		MaxIdleConns:          64,
		IdleConnTimeout:       90 * time.Second,
		ResponseHeaderTimeout: 60 * time.Second,
		ExpectContinueTimeout: time.Second,
	}
	p.server = &http.Server{
		Handler:           p,
		ReadHeaderTimeout: 30 * time.Second,
		IdleTimeout:       120 * time.Second,
	}
	go func() {
		err := p.server.Serve(listener)
		if err != nil && !errors.Is(err, http.ErrServerClosed) {
			logf("warn", "http proxy stopped: %v", err)
		}
	}()

	httpProxyCurrent = p
	port := listener.Addr().(*net.TCPAddr).Port
	logf("info", "http proxy on 127.0.0.1:%d via %s://%s, %d names blocked",
		port, socksNetwork, socksAddr, block.size())
	return port, nil
}

// StopHTTPProxy stops the proxy. Connections in flight are cut; the mode is
// being left, and there is nothing to carry them into.
func StopHTTPProxy() {
	httpProxyMu.Lock()
	p := httpProxyCurrent
	httpProxyCurrent = nil
	httpProxyMu.Unlock()
	if p == nil {
		return
	}
	p.cancel()
	_ = p.server.Close()
	p.transport.CloseIdleConnections()
}

// HTTPProxyPort reports the running proxy's port, or 0.
func HTTPProxyPort() int {
	httpProxyMu.Lock()
	defer httpProxyMu.Unlock()
	if httpProxyCurrent == nil {
		return 0
	}
	return httpProxyCurrent.listener.Addr().(*net.TCPAddr).Port
}

// ServeHTTP is one request from a proxy client: a CONNECT to tunnel, or an
// absolute-URI request to forward.
func (p *httpProxy) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if r.Method == http.MethodConnect {
		p.tunnel(w, r)
		return
	}
	if r.URL == nil || !r.URL.IsAbs() || r.URL.Host == "" {
		// Addressed to the proxy itself. There is nothing here to see.
		http.Error(w, "Veil: this is a proxy, not a site", http.StatusBadRequest)
		return
	}
	if p.blocked(r.URL.Hostname()) {
		refuse(w)
		return
	}

	out := r.Clone(r.Context())
	out.RequestURI = ""
	stripHopByHop(out.Header)

	resp, err := p.transport.RoundTrip(out)
	if err != nil {
		stats.dialErrors.Add(1)
		http.Error(w, "Veil: "+err.Error(), http.StatusBadGateway)
		return
	}
	defer resp.Body.Close()

	stripHopByHop(resp.Header)
	header := w.Header()
	for key, values := range resp.Header {
		for _, value := range values {
			header.Add(key, value)
		}
	}
	w.WriteHeader(resp.StatusCode)
	n, _ := io.Copy(flushWriter{w}, resp.Body)
	stats.rx.Add(n)
}

// tunnel answers a CONNECT: dial through tor, say so, then move bytes until
// one side is done.
func (p *httpProxy) tunnel(w http.ResponseWriter, r *http.Request) {
	target := r.Host
	if r.URL != nil && r.URL.Host != "" {
		target = r.URL.Host
	}
	host, port, err := net.SplitHostPort(target)
	if err != nil {
		host, port = target, "443"
		target = net.JoinHostPort(host, port)
	}
	if p.blocked(host) {
		refuse(w)
		return
	}

	up, err := p.dialHeld(r.Context(), "tcp", target)
	if err != nil {
		stats.dialErrors.Add(1)
		http.Error(w, "Veil: "+err.Error(), http.StatusBadGateway)
		return
	}

	hijacker, ok := w.(http.Hijacker)
	if !ok {
		up.Close()
		http.Error(w, "Veil: the connection cannot be taken over", http.StatusInternalServerError)
		return
	}
	client, buffered, err := hijacker.Hijack()
	if err != nil {
		up.Close()
		return
	}
	// From here the connection is ours, and the server's deadlines are not.
	_ = client.SetDeadline(time.Time{})

	if _, err := buffered.WriteString("HTTP/1.1 200 Connection Established\r\n\r\n"); err != nil {
		client.Close()
		up.Close()
		return
	}
	if err := buffered.Flush(); err != nil {
		client.Close()
		up.Close()
		return
	}
	// Anything the client sent ahead of the answer — a TLS ClientHello,
	// typically — is sitting in the reader's buffer and must go up first, or
	// the handshake starts with its first bytes missing.
	if n := buffered.Reader.Buffered(); n > 0 {
		ahead, _ := buffered.Reader.Peek(n)
		if _, err := up.Write(ahead); err != nil {
			client.Close()
			up.Close()
			return
		}
	}

	stats.tcpTotal.Add(1)
	stats.tcpOpen.Add(1)
	defer stats.tcpOpen.Add(-1)
	relay(client, up)
}

// dialHeld dials through tor and, while the path underneath is being
// re-dialled, keeps trying rather than failing the client's request: the same
// hold the tunnel gives applications, for the same reason.
func (p *httpProxy) dialHeld(ctx context.Context, network, addr string) (net.Conn, error) {
	started := time.Now()
	for {
		dialCtx, cancel := context.WithTimeout(ctx, httpProxyDialTimeout)
		conn, err := p.dialer.DialContext(dialCtx, network, addr)
		cancel()
		if err == nil {
			return conn, nil
		}
		if ctx.Err() != nil || p.ctx.Err() != nil {
			return nil, err
		}
		window := time.Duration(0)
		switch {
		case rebuilding.Load():
			window = holdWindow
		case proxyDown(err):
			window = holdWhenProxyDown
		}
		if time.Since(started) >= window {
			return nil, err
		}
		select {
		case <-ctx.Done():
			return nil, err
		case <-p.ctx.Done():
			return nil, err
		case <-time.After(holdRetry):
		}
	}
}

func (p *httpProxy) blocked(name string) bool {
	return p.block.blocked(name)
}

// refuse is the proxy's NXDOMAIN: the request for an advertising name goes
// nowhere, quickly, and is counted with the resolver's refusals.
func refuse(w http.ResponseWriter) {
	stats.dnsBlocked.Add(1)
	w.Header().Set("Content-Type", "text/plain; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(http.StatusForbidden)
	_, _ = io.WriteString(w, "blocked by Veil\n")
}

// The headers that describe one hop and must not be carried to the next.
var hopByHop = []string{
	"Connection", "Keep-Alive", "Proxy-Authenticate", "Proxy-Authorization",
	"Proxy-Connection", "Te", "Trailer", "Transfer-Encoding", "Upgrade",
}

func stripHopByHop(h http.Header) {
	for _, field := range strings.Split(h.Get("Connection"), ",") {
		if field = strings.TrimSpace(field); field != "" {
			h.Del(field)
		}
	}
	for _, key := range hopByHop {
		h.Del(key)
	}
}

// flushWriter pushes each chunk of a response out as it arrives, so a
// streamed response streams instead of collecting in a buffer.
type flushWriter struct{ w http.ResponseWriter }

func (f flushWriter) Write(b []byte) (int, error) {
	n, err := f.w.Write(b)
	if flusher, ok := f.w.(http.Flusher); ok {
		flusher.Flush()
	}
	return n, err
}
