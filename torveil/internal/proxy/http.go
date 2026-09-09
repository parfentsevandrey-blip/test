package proxy

import (
	"bufio"
	"context"
	"fmt"
	"net"
	"net/http"
	"strings"
	"time"
)

// hopByHopHeaders must not be forwarded to the origin server.
var hopByHopHeaders = []string{
	"Connection",
	"Proxy-Connection",
	"Proxy-Authenticate",
	"Proxy-Authorization",
	"Keep-Alive",
	"Te",
	"Trailer",
	"Transfer-Encoding",
	"Upgrade",
}

// serveHTTP handles one inbound HTTP proxy connection. CONNECT is tunnelled;
// an absolute-URI request is forwarded once and the connection then closed.
func (s *Server) serveHTTP(ctx context.Context, client net.Conn) {
	defer client.Close()
	_ = client.SetReadDeadline(time.Now().Add(handshakeTimeout))

	br := bufio.NewReader(client)
	req, err := http.ReadRequest(br)
	if err != nil {
		return
	}
	_ = client.SetReadDeadline(time.Time{})

	if req.Method == http.MethodConnect {
		s.httpConnect(ctx, client, req)
		return
	}
	s.httpForward(ctx, client, br, req)
}

// httpConnect tunnels a CONNECT request.
func (s *Server) httpConnect(ctx context.Context, client net.Conn, req *http.Request) {
	target := req.Host
	if _, _, err := net.SplitHostPort(target); err != nil {
		target = net.JoinHostPort(target, "443")
	}

	upstream, err := s.dialer.DialContext(ctx, "tcp", target)
	if err != nil {
		s.failed.Add(1)
		s.logf("info", "http connect %s: %v", target, err)
		writeHTTPError(client, http.StatusBadGateway, err)
		return
	}
	defer upstream.Close()

	if _, err := client.Write([]byte("HTTP/1.1 200 Connection established\r\n\r\n")); err != nil {
		return
	}
	s.pipe(client, upstream)
}

// httpForward relays a plain (non-CONNECT) proxy request.
//
// The request is rewritten to origin form and marked Connection: close, so one
// request is served per connection. That costs a little efficiency on the rare
// plaintext request and avoids a half-implemented keep-alive state machine
// carrying requests for two different origins over one circuit.
func (s *Server) httpForward(ctx context.Context, client net.Conn, br *bufio.Reader, req *http.Request) {
	if req.URL == nil || req.URL.Host == "" {
		writeHTTPError(client, http.StatusBadRequest, fmt.Errorf("proxy requests must use an absolute URI"))
		return
	}
	if scheme := strings.ToLower(req.URL.Scheme); scheme != "" && scheme != "http" {
		writeHTTPError(client, http.StatusBadRequest, fmt.Errorf("unsupported scheme %q", scheme))
		return
	}

	target := req.URL.Host
	if _, _, err := net.SplitHostPort(target); err != nil {
		target = net.JoinHostPort(target, "80")
	}

	upstream, err := s.dialer.DialContext(ctx, "tcp", target)
	if err != nil {
		s.failed.Add(1)
		s.logf("info", "http forward %s: %v", target, err)
		writeHTTPError(client, http.StatusBadGateway, err)
		return
	}
	defer upstream.Close()

	outbound := req.Clone(ctx)
	outbound.RequestURI = ""
	for _, h := range hopByHopHeaders {
		outbound.Header.Del(h)
	}
	outbound.Header.Set("Connection", "close")
	outbound.Close = true

	if err := outbound.Write(upstream); err != nil {
		s.logf("info", "http write %s: %v", target, err)
		return
	}

	// Anything the client pipelined behind the first request still needs to
	// reach the origin, so both directions are copied after the rewrite.
	s.pipe(bufferedConn{Conn: client, r: br}, upstream)
}

// bufferedConn lets the pipe read bytes already pulled into the bufio.Reader
// during request parsing.
type bufferedConn struct {
	net.Conn
	r *bufio.Reader
}

func (c bufferedConn) Read(p []byte) (int, error) { return c.r.Read(p) }

func writeHTTPError(w net.Conn, status int, err error) {
	body := fmt.Sprintf("TorVeil proxy: %v\n", err)
	fmt.Fprintf(w,
		"HTTP/1.1 %d %s\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: %d\r\nConnection: close\r\n\r\n%s",
		status, http.StatusText(status), len(body), body)
}
