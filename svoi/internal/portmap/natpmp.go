package portmap

import (
	"context"
	"encoding/binary"
	"errors"
	"fmt"
	"net"
	"net/netip"
	"time"
)

// NAT-PMP (RFC 6886): two tiny UDP requests to the default gateway.

type pmp struct {
	gw   netip.Addr
	port int
}

func (p *pmp) protocol() Protocol      { return NATPMP }
func (p *pmp) gatewayAddr() netip.Addr { return p.gw }

// request sends req to the gateway and waits for a reply of wantLen bytes
// (retransmitting a few times, as the RFC asks).
func (p *pmp) request(ctx context.Context, req []byte, wantOp byte, wantLen int) ([]byte, error) {
	conn, err := net.DialUDP("udp4", nil, &net.UDPAddr{IP: p.gw.AsSlice(), Port: p.port})
	if err != nil {
		return nil, err
	}
	defer conn.Close()
	buf := make([]byte, 64)
	wait := 250 * time.Millisecond
	for try := 0; try < 3; try++ {
		if _, err := conn.Write(req); err != nil {
			return nil, err
		}
		deadline := time.Now().Add(wait)
		if d, ok := ctx.Deadline(); ok && d.Before(deadline) {
			deadline = d
		}
		_ = conn.SetReadDeadline(deadline)
		for {
			n, err := conn.Read(buf)
			if err != nil {
				var ne net.Error
				if errors.As(err, &ne) && ne.Timeout() {
					break // retransmit
				}
				return nil, err
			}
			if n >= wantLen && buf[0] == 0 && buf[1] == wantOp {
				res := binary.BigEndian.Uint16(buf[2:4])
				if res != 0 {
					return nil, fmt.Errorf("the gateway refused (NAT-PMP result %d)", res)
				}
				return append([]byte(nil), buf[:n]...), nil
			}
		}
		if ctx.Err() != nil {
			return nil, ctx.Err()
		}
		wait *= 2
	}
	return nil, errors.New("the gateway does not answer NAT-PMP")
}

func (p *pmp) externalIP(ctx context.Context) (netip.Addr, error) {
	rep, err := p.request(ctx, []byte{0, 0}, 128, 12)
	if err != nil {
		return netip.Addr{}, err
	}
	ip, _ := netip.AddrFromSlice(rep[8:12])
	return ip, nil
}

func (p *pmp) mapUDP(ctx context.Context, internal, external int, lifetime uint32) (int, uint32, error) {
	req := make([]byte, 12)
	req[1] = 1 // map UDP
	binary.BigEndian.PutUint16(req[4:], uint16(internal))
	binary.BigEndian.PutUint16(req[6:], uint16(external))
	binary.BigEndian.PutUint32(req[8:], lifetime)
	rep, err := p.request(ctx, req, 129, 16)
	if err != nil {
		return 0, 0, err
	}
	return int(binary.BigEndian.Uint16(rep[10:12])), binary.BigEndian.Uint32(rep[12:16]), nil
}

func (p *pmp) addMapping(ctx context.Context, internalPort, wantExternal, lifetime int, _ bool) (int, time.Duration, error) {
	ext, granted, err := p.mapUDP(ctx, internalPort, wantExternal, uint32(lifetime))
	if err != nil {
		return 0, 0, err
	}
	return ext, time.Duration(granted) * time.Second, nil
}

func (p *pmp) deleteMapping(ctx context.Context, external int) error {
	// A request with lifetime 0 removes the mapping (the internal port is the one it was made for).
	_, _, err := p.mapUDP(ctx, external, 0, 0)
	return err
}

// discoverNATPMP checks that the gateway speaks NAT-PMP.
func discoverNATPMP(ctx context.Context, cfg Config, gw netip.Addr) (client, error) {
	p := &pmp{gw: gw, port: cfg.pmpPort}
	if _, err := p.externalIP(ctx); err != nil {
		return nil, err
	}
	return p, nil
}
