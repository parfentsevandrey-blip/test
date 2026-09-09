// Package proxy provides the loopback listeners applications connect to, and
// the dialer that carries their traffic into Tor.
//
// Both of TorVeil's modes share this path. In proxy mode an application talks
// SOCKS5 or HTTP to these listeners directly; in full-tunnel mode the TUN
// stack does. Keeping one data path means the traffic shaper and the circuit
// policy apply identically either way.
package proxy

import (
	"context"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"net"
	"strconv"
	"strings"
	"time"
)

// SOCKS5 wire constants.
const (
	socks5Version = 0x05

	authNone         = 0x00
	authNoAcceptable = 0xFF

	cmdConnect      = 0x01
	cmdBind         = 0x02
	cmdUDPAssociate = 0x03

	atypIPv4   = 0x01
	atypDomain = 0x03
	atypIPv6   = 0x04

	repSucceeded            = 0x00
	repGeneralFailure       = 0x01
	repConnectionNotAllowed = 0x02
	repHostUnreachable      = 0x04
	repConnectionRefused    = 0x05
	repCommandNotSupported  = 0x07
	repAddressNotSupported  = 0x08
)

// handshakeTimeout bounds the SOCKS negotiation, separately from the lifetime
// of the tunnelled connection.
const handshakeTimeout = 30 * time.Second

// ErrUDPUnsupported reports a UDP association request. Tor carries TCP only,
// so this is a permanent, structural limitation rather than a missing feature.
var ErrUDPUnsupported = errors.New("Tor carries TCP only; UDP is not supported")

// Upstream dials a target through Tor's SOCKS5 port.
//
// Hostnames are always passed through unresolved. Resolving locally would send
// a DNS query outside the tunnel, which leaks the destination to the local
// resolver and the ISP no matter how well the connection itself is protected.
type Upstream struct {
	// Address is Tor's SOCKS listener, e.g. "127.0.0.1:9050".
	Address string

	// Timeout bounds the connection to the SOCKS port and the handshake.
	Timeout time.Duration

	// Wrap, when set, decorates the connection before it is returned. TorVeil
	// uses it to apply traffic shaping.
	Wrap func(net.Conn) net.Conn
}

// DialContext opens a TCP connection to address through Tor.
func (u *Upstream) DialContext(ctx context.Context, network, address string) (net.Conn, error) {
	if !strings.HasPrefix(network, "tcp") {
		return nil, fmt.Errorf("%w (requested %s)", ErrUDPUnsupported, network)
	}
	host, portStr, err := net.SplitHostPort(address)
	if err != nil {
		return nil, fmt.Errorf("parse target %q: %w", address, err)
	}
	port, err := strconv.Atoi(portStr)
	if err != nil || port < 1 || port > 65535 {
		return nil, fmt.Errorf("invalid port in %q", address)
	}

	timeout := u.Timeout
	if timeout <= 0 {
		timeout = 60 * time.Second
	}
	d := net.Dialer{Timeout: timeout}
	conn, err := d.DialContext(ctx, "tcp", u.Address)
	if err != nil {
		return nil, fmt.Errorf("connect to Tor SOCKS port: %w", err)
	}

	if deadline, ok := ctx.Deadline(); ok {
		_ = conn.SetDeadline(deadline)
	} else {
		_ = conn.SetDeadline(time.Now().Add(timeout))
	}

	if err := clientHandshake(conn, host, port); err != nil {
		conn.Close()
		return nil, err
	}
	_ = conn.SetDeadline(time.Time{})

	if u.Wrap != nil {
		return u.Wrap(conn), nil
	}
	return conn, nil
}

// clientHandshake performs the SOCKS5 negotiation against Tor.
func clientHandshake(conn net.Conn, host string, port int) error {
	if _, err := conn.Write([]byte{socks5Version, 1, authNone}); err != nil {
		return fmt.Errorf("socks greeting: %w", err)
	}
	resp := make([]byte, 2)
	if _, err := io.ReadFull(conn, resp); err != nil {
		return fmt.Errorf("socks greeting reply: %w", err)
	}
	if resp[0] != socks5Version || resp[1] != authNone {
		return fmt.Errorf("socks: upstream rejected no-auth (version %#x, method %#x)", resp[0], resp[1])
	}

	req, err := buildRequest(cmdConnect, host, port)
	if err != nil {
		return err
	}
	if _, err := conn.Write(req); err != nil {
		return fmt.Errorf("socks connect request: %w", err)
	}

	head := make([]byte, 4)
	if _, err := io.ReadFull(conn, head); err != nil {
		return fmt.Errorf("socks connect reply: %w", err)
	}
	if head[0] != socks5Version {
		return fmt.Errorf("socks: bad reply version %#x", head[0])
	}
	if head[1] != repSucceeded {
		return fmt.Errorf("socks: %s", replyMessage(head[1]))
	}
	if err := discardAddress(conn, head[3]); err != nil {
		return err
	}
	return nil
}

// buildRequest encodes a SOCKS5 request, preferring the domain address type so
// the name reaches Tor unresolved.
func buildRequest(cmd byte, host string, port int) ([]byte, error) {
	buf := []byte{socks5Version, cmd, 0x00}
	if ip := net.ParseIP(host); ip != nil {
		if v4 := ip.To4(); v4 != nil {
			buf = append(buf, atypIPv4)
			buf = append(buf, v4...)
		} else {
			buf = append(buf, atypIPv6)
			buf = append(buf, ip.To16()...)
		}
	} else {
		if len(host) > 255 {
			return nil, fmt.Errorf("socks: hostname %q is longer than 255 bytes", host)
		}
		buf = append(buf, atypDomain, byte(len(host)))
		buf = append(buf, host...)
	}
	return binary.BigEndian.AppendUint16(buf, uint16(port)), nil
}

// discardAddress consumes the bound address of a SOCKS reply.
func discardAddress(r io.Reader, atyp byte) error {
	switch atyp {
	case atypIPv4:
		return skip(r, 4+2)
	case atypIPv6:
		return skip(r, 16+2)
	case atypDomain:
		l := make([]byte, 1)
		if _, err := io.ReadFull(r, l); err != nil {
			return err
		}
		return skip(r, int(l[0])+2)
	default:
		return fmt.Errorf("socks: unsupported address type %#x", atyp)
	}
}

func skip(r io.Reader, n int) error {
	_, err := io.CopyN(io.Discard, r, int64(n))
	return err
}

func replyMessage(code byte) string {
	switch code {
	case repGeneralFailure:
		return "general failure"
	case repConnectionNotAllowed:
		return "connection not allowed by ruleset"
	case 0x03:
		return "network unreachable"
	case repHostUnreachable:
		return "host unreachable"
	case repConnectionRefused:
		return "connection refused"
	case 0x06:
		return "TTL expired"
	case repCommandNotSupported:
		return "command not supported"
	case repAddressNotSupported:
		return "address type not supported"
	default:
		return fmt.Sprintf("reply code %#x", code)
	}
}

// serveSOCKS handles one inbound SOCKS5 client connection.
func (s *Server) serveSOCKS(ctx context.Context, client net.Conn) {
	defer client.Close()
	_ = client.SetDeadline(time.Now().Add(handshakeTimeout))

	target, cmd, err := acceptHandshake(client)
	if err != nil {
		s.logf("info", "socks handshake from %s: %v", client.RemoteAddr(), err)
		return
	}
	if cmd != cmdConnect {
		code := byte(repCommandNotSupported)
		s.logf("info", "socks: rejecting %s from %s", commandName(cmd), client.RemoteAddr())
		_ = writeReply(client, code)
		return
	}

	_ = client.SetDeadline(time.Time{})
	upstream, err := s.dialer.DialContext(ctx, "tcp", target)
	if err != nil {
		s.logf("info", "socks connect %s: %v", target, err)
		_ = writeReply(client, dialErrorCode(err))
		return
	}
	defer upstream.Close()

	if err := writeReply(client, repSucceeded); err != nil {
		return
	}
	s.pipe(client, upstream)
}

// acceptHandshake performs the server side of the SOCKS5 negotiation and
// returns the requested target as "host:port".
func acceptHandshake(conn net.Conn) (string, byte, error) {
	head := make([]byte, 2)
	if _, err := io.ReadFull(conn, head); err != nil {
		return "", 0, fmt.Errorf("read greeting: %w", err)
	}
	if head[0] != socks5Version {
		return "", 0, fmt.Errorf("unsupported SOCKS version %#x", head[0])
	}
	methods := make([]byte, head[1])
	if _, err := io.ReadFull(conn, methods); err != nil {
		return "", 0, fmt.Errorf("read auth methods: %w", err)
	}
	offersNone := false
	for _, m := range methods {
		if m == authNone {
			offersNone = true
		}
	}
	if !offersNone {
		_, _ = conn.Write([]byte{socks5Version, authNoAcceptable})
		return "", 0, errors.New("client did not offer no-auth")
	}
	if _, err := conn.Write([]byte{socks5Version, authNone}); err != nil {
		return "", 0, err
	}

	req := make([]byte, 4)
	if _, err := io.ReadFull(conn, req); err != nil {
		return "", 0, fmt.Errorf("read request: %w", err)
	}
	if req[0] != socks5Version {
		return "", 0, fmt.Errorf("bad request version %#x", req[0])
	}
	cmd := req[1]

	var host string
	switch req[3] {
	case atypIPv4:
		b := make([]byte, 4)
		if _, err := io.ReadFull(conn, b); err != nil {
			return "", cmd, err
		}
		host = net.IP(b).String()
	case atypIPv6:
		b := make([]byte, 16)
		if _, err := io.ReadFull(conn, b); err != nil {
			return "", cmd, err
		}
		host = net.IP(b).String()
	case atypDomain:
		l := make([]byte, 1)
		if _, err := io.ReadFull(conn, l); err != nil {
			return "", cmd, err
		}
		b := make([]byte, l[0])
		if _, err := io.ReadFull(conn, b); err != nil {
			return "", cmd, err
		}
		host = string(b)
	default:
		_ = writeReply(conn, repAddressNotSupported)
		return "", cmd, fmt.Errorf("unsupported address type %#x", req[3])
	}

	pb := make([]byte, 2)
	if _, err := io.ReadFull(conn, pb); err != nil {
		return "", cmd, err
	}
	port := binary.BigEndian.Uint16(pb)
	return net.JoinHostPort(host, strconv.Itoa(int(port))), cmd, nil
}

// writeReply sends a SOCKS5 reply with an all-zero bound address, which is
// what a client that only needs a tunnel expects.
func writeReply(conn net.Conn, code byte) error {
	_, err := conn.Write([]byte{socks5Version, code, 0x00, atypIPv4, 0, 0, 0, 0, 0, 0})
	return err
}

func commandName(cmd byte) string {
	switch cmd {
	case cmdBind:
		return "BIND"
	case cmdUDPAssociate:
		return "UDP ASSOCIATE (Tor carries TCP only)"
	default:
		return fmt.Sprintf("command %#x", cmd)
	}
}

// dialErrorCode maps a dial failure onto the closest SOCKS reply code, so the
// client shows a sensible error instead of a generic one.
func dialErrorCode(err error) byte {
	msg := strings.ToLower(err.Error())
	switch {
	case strings.Contains(msg, "connection refused"):
		return repConnectionRefused
	case strings.Contains(msg, "host unreachable"), strings.Contains(msg, "no such host"):
		return repHostUnreachable
	case strings.Contains(msg, "not allowed"):
		return repConnectionNotAllowed
	default:
		return repGeneralFailure
	}
}
