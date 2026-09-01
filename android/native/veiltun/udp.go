package veiltun

import (
	"context"
	"encoding/binary"
	"fmt"
	"io"
	"net"
	"net/netip"
	"time"

	"github.com/xjasonlyu/tun2socks/v2/core/adapter"
)

// relayUDP carries a non-DNS UDP flow over a SOCKS5 UDP ASSOCIATE. This path
// is unused with Tor (which is TCP-only and therefore runs with BlockUDP set)
// but lets the same tunnel serve engines that do support UDP.
func (h *handler) relayUDP(conn adapter.UDPConn, dst netip.AddrPort) {
	ctx, cancel := context.WithCancel(h.ctx)
	defer cancel()

	assoc, err := h.dialUDPAssociate(ctx)
	if err != nil {
		stats.blocked.Add(1)
		logf("warn", "udp %s: %v", dst, err)
		return
	}
	defer assoc.Close()

	idle := time.Duration(h.cfg.UDPTimeoutSec) * time.Second

	// Downstream: relay -> app.
	go func() {
		buf := make([]byte, 65535)
		for {
			_ = assoc.pc.SetReadDeadline(time.Now().Add(idle))
			n, err := assoc.pc.Read(buf)
			if err != nil {
				cancel()
				return
			}
			payload, _, err := decapsulate(buf[:n])
			if err != nil {
				continue
			}
			stats.rx.Add(int64(len(payload)))
			_ = conn.SetWriteDeadline(time.Now().Add(10 * time.Second))
			if _, err := conn.Write(payload); err != nil {
				cancel()
				return
			}
		}
	}()

	// Upstream: app -> relay.
	buf := make([]byte, 65535)
	for {
		select {
		case <-ctx.Done():
			return
		default:
		}
		_ = conn.SetReadDeadline(time.Now().Add(idle))
		n, err := conn.Read(buf)
		if err != nil {
			return
		}
		framed := encapsulate(dst, buf[:n])
		_ = assoc.pc.SetWriteDeadline(time.Now().Add(10 * time.Second))
		if _, err := assoc.pc.Write(framed); err != nil {
			return
		}
		stats.tx.Add(int64(n))
	}
}

type udpAssociation struct {
	ctrl net.Conn     // keeps the association alive
	pc   *net.UDPConn // connected to the relay endpoint
}

func (a *udpAssociation) Close() {
	if a.pc != nil {
		_ = a.pc.Close()
	}
	if a.ctrl != nil {
		_ = a.ctrl.Close()
	}
}

// dialUDPAssociate performs the SOCKS5 handshake and UDP ASSOCIATE request by
// hand, because golang.org/x/net/proxy only implements CONNECT.
func (h *handler) dialUDPAssociate(ctx context.Context) (*udpAssociation, error) {
	d := net.Dialer{Timeout: time.Duration(h.cfg.DialTimeoutSec) * time.Second}
	ctrl, err := d.DialContext(ctx, "tcp", h.cfg.SocksAddr)
	if err != nil {
		return nil, err
	}
	assoc := &udpAssociation{ctrl: ctrl}
	ok := false
	defer func() {
		if !ok {
			assoc.Close()
		}
	}()

	_ = ctrl.SetDeadline(time.Now().Add(time.Duration(h.cfg.DialTimeoutSec) * time.Second))

	user, pass := h.cfg.SocksUser, h.cfg.SocksPass
	methods := []byte{0x00}
	if user != "" || pass != "" {
		methods = []byte{0x00, 0x02}
	}
	greet := append([]byte{0x05, byte(len(methods))}, methods...)
	if _, err := ctrl.Write(greet); err != nil {
		return nil, err
	}
	sel := make([]byte, 2)
	if _, err := io.ReadFull(ctrl, sel); err != nil {
		return nil, err
	}
	if sel[0] != 0x05 {
		return nil, fmt.Errorf("socks: bad version %d", sel[0])
	}
	switch sel[1] {
	case 0x00:
	case 0x02:
		req := []byte{0x01, byte(len(user))}
		req = append(req, user...)
		req = append(req, byte(len(pass)))
		req = append(req, pass...)
		if _, err := ctrl.Write(req); err != nil {
			return nil, err
		}
		ack := make([]byte, 2)
		if _, err := io.ReadFull(ctrl, ack); err != nil {
			return nil, err
		}
		if ack[1] != 0x00 {
			return nil, errNoUDP
		}
	default:
		return nil, fmt.Errorf("socks: no acceptable auth method (%d)", sel[1])
	}

	// UDP ASSOCIATE with an unspecified client endpoint: we let the proxy
	// accept datagrams from whatever source address we end up using.
	if _, err := ctrl.Write([]byte{0x05, 0x03, 0x00, 0x01, 0, 0, 0, 0, 0, 0}); err != nil {
		return nil, err
	}
	head := make([]byte, 4)
	if _, err := io.ReadFull(ctrl, head); err != nil {
		return nil, err
	}
	if head[1] != 0x00 {
		return nil, errNoUDP
	}
	relayAddr, err := readSocksAddr(ctrl, head[3])
	if err != nil {
		return nil, err
	}
	_ = ctrl.SetDeadline(time.Time{})

	// If the proxy advertises an unspecified address, datagrams go back to the
	// same host we opened the control connection to.
	if !relayAddr.Addr().IsValid() || relayAddr.Addr().IsUnspecified() {
		host, _, _ := net.SplitHostPort(h.cfg.SocksAddr)
		if a, e := netip.ParseAddr(host); e == nil {
			relayAddr = netip.AddrPortFrom(a, relayAddr.Port())
		}
	}

	pc, err := net.DialUDP("udp", nil, net.UDPAddrFromAddrPort(relayAddr))
	if err != nil {
		return nil, err
	}
	assoc.pc = pc

	// The association lives as long as the control connection: drain it so we
	// notice when the proxy hangs up.
	go func() {
		_, _ = io.Copy(io.Discard, ctrl)
		_ = pc.Close()
	}()

	ok = true
	return assoc, nil
}

func readSocksAddr(r io.Reader, atyp byte) (netip.AddrPort, error) {
	var host netip.Addr
	switch atyp {
	case 0x01:
		b := make([]byte, 4)
		if _, err := io.ReadFull(r, b); err != nil {
			return netip.AddrPort{}, err
		}
		host, _ = netip.AddrFromSlice(b)
	case 0x04:
		b := make([]byte, 16)
		if _, err := io.ReadFull(r, b); err != nil {
			return netip.AddrPort{}, err
		}
		host, _ = netip.AddrFromSlice(b)
	case 0x03:
		l := make([]byte, 1)
		if _, err := io.ReadFull(r, l); err != nil {
			return netip.AddrPort{}, err
		}
		b := make([]byte, l[0])
		if _, err := io.ReadFull(r, b); err != nil {
			return netip.AddrPort{}, err
		}
		host, _ = netip.ParseAddr(string(b))
	default:
		return netip.AddrPort{}, fmt.Errorf("socks: unsupported address type %d", atyp)
	}
	p := make([]byte, 2)
	if _, err := io.ReadFull(r, p); err != nil {
		return netip.AddrPort{}, err
	}
	return netip.AddrPortFrom(host, binary.BigEndian.Uint16(p)), nil
}

// encapsulate wraps a datagram in a SOCKS5 UDP request header (RFC 1928 §7).
func encapsulate(dst netip.AddrPort, payload []byte) []byte {
	out := make([]byte, 0, len(payload)+22)
	out = append(out, 0x00, 0x00, 0x00) // RSV, RSV, FRAG
	if dst.Addr().Is4() {
		out = append(out, 0x01)
		a := dst.Addr().As4()
		out = append(out, a[:]...)
	} else {
		out = append(out, 0x04)
		a := dst.Addr().As16()
		out = append(out, a[:]...)
	}
	out = binary.BigEndian.AppendUint16(out, dst.Port())
	return append(out, payload...)
}

// decapsulate strips the SOCKS5 UDP response header.
func decapsulate(b []byte) ([]byte, netip.AddrPort, error) {
	if len(b) < 10 || b[2] != 0x00 {
		return nil, netip.AddrPort{}, fmt.Errorf("socks: bad or fragmented UDP datagram")
	}
	var hdr int
	var src netip.Addr
	switch b[3] {
	case 0x01:
		if len(b) < 10 {
			return nil, netip.AddrPort{}, io.ErrUnexpectedEOF
		}
		src, _ = netip.AddrFromSlice(b[4:8])
		hdr = 10
	case 0x04:
		if len(b) < 22 {
			return nil, netip.AddrPort{}, io.ErrUnexpectedEOF
		}
		src, _ = netip.AddrFromSlice(b[4:20])
		hdr = 22
	case 0x03:
		l := int(b[4])
		if len(b) < 5+l+2 {
			return nil, netip.AddrPort{}, io.ErrUnexpectedEOF
		}
		src, _ = netip.ParseAddr(string(b[5 : 5+l]))
		hdr = 5 + l + 2
	default:
		return nil, netip.AddrPort{}, fmt.Errorf("socks: unsupported address type %d", b[3])
	}
	port := binary.BigEndian.Uint16(b[hdr-2 : hdr])
	return b[hdr:], netip.AddrPortFrom(src, port), nil
}
