package veiltun

import (
	"bytes"
	"context"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"time"
)

// dnsResolver answers a raw DNS wire-format query with a raw wire-format
// response. Working in wire format throughout means we never have to parse
// DNS, and it maps one-to-one onto RFC 8484's application/dns-message.
type dnsResolver interface {
	Exchange(ctx context.Context, query []byte) ([]byte, error)
	Close()
}

type dialFunc func(ctx context.Context, network, addr string) (net.Conn, error)

func newDNSResolver(cfg *Config, dial dialFunc) (dnsResolver, error) {
	switch cfg.DNSMode {
	case DNSDrop:
		return dropResolver{}, nil

	case DNSUDPLoopback:
		// Used with Tor's DNSPort: the listener is on loopback inside our own
		// process' network namespace, so the datagram never touches the TUN
		// and never reaches the local network.
		if cfg.DNSAddr == "" {
			return nil, errors.New("dns: udp mode needs an address")
		}
		return &udpResolver{addr: cfg.DNSAddr}, nil

	case DNSOverTCP:
		// DNS over TCP survives everywhere Tor does, because Tor carries TCP.
		if cfg.DNSAddr == "" {
			return nil, errors.New("dns: tcp mode needs an address")
		}
		return &tcpResolver{addr: cfg.DNSAddr, dial: dial}, nil

	case DNSOverHTTPS:
		if cfg.DNSAddr == "" {
			return nil, errors.New("dns: doh mode needs a URL")
		}
		u, err := url.Parse(cfg.DNSAddr)
		if err != nil || u.Scheme != "https" {
			return nil, fmt.Errorf("dns: bad DoH URL %q", cfg.DNSAddr)
		}
		return newDoHResolver(u.String(), dial), nil

	default:
		return nil, fmt.Errorf("dns: unknown mode %q", cfg.DNSMode)
	}
}

// dropResolver answers nothing. Only useful to prove a leak is closed.
type dropResolver struct{}

func (dropResolver) Exchange(context.Context, []byte) ([]byte, error) {
	return nil, errors.New("dns disabled")
}
func (dropResolver) Close() {}

// udpResolver forwards to a plain UDP resolver, expected to be on loopback.
type udpResolver struct{ addr string }

func (r *udpResolver) Exchange(ctx context.Context, query []byte) ([]byte, error) {
	d := net.Dialer{Timeout: 10 * time.Second}
	c, err := d.DialContext(ctx, "udp", r.addr)
	if err != nil {
		return nil, err
	}
	defer c.Close()

	if dl, ok := ctx.Deadline(); ok {
		_ = c.SetDeadline(dl)
	} else {
		_ = c.SetDeadline(time.Now().Add(10 * time.Second))
	}
	if _, err := c.Write(query); err != nil {
		return nil, err
	}
	buf := make([]byte, 4096)
	n, err := c.Read(buf)
	if err != nil {
		return nil, err
	}
	return buf[:n], nil
}

func (r *udpResolver) Close() {}

// tcpResolver speaks RFC 1035 section 4.2.2 (two-byte length prefix) through
// the SOCKS proxy.
type tcpResolver struct {
	addr string
	dial dialFunc
}

func (r *tcpResolver) Exchange(ctx context.Context, query []byte) ([]byte, error) {
	c, err := r.dial(ctx, "tcp", r.addr)
	if err != nil {
		return nil, err
	}
	defer c.Close()

	if dl, ok := ctx.Deadline(); ok {
		_ = c.SetDeadline(dl)
	}
	var framed bytes.Buffer
	_ = binary.Write(&framed, binary.BigEndian, uint16(len(query)))
	framed.Write(query)
	if _, err := c.Write(framed.Bytes()); err != nil {
		return nil, err
	}

	var length uint16
	if err := binary.Read(c, binary.BigEndian, &length); err != nil {
		return nil, err
	}
	if length == 0 || length > 8192 {
		return nil, fmt.Errorf("dns: implausible response length %d", length)
	}
	resp := make([]byte, length)
	if _, err := io.ReadFull(c, resp); err != nil {
		return nil, err
	}
	return resp, nil
}

func (r *tcpResolver) Close() {}

// dohResolver posts the query as application/dns-message over HTTPS, with
// every byte of the exchange dialled through the SOCKS proxy.
type dohResolver struct {
	endpoint string
	client   *http.Client
}

func newDoHResolver(endpoint string, dial dialFunc) *dohResolver {
	return &dohResolver{
		endpoint: endpoint,
		client: &http.Client{
			Timeout: 25 * time.Second,
			Transport: &http.Transport{
				DialContext:           dial,
				ForceAttemptHTTP2:     true,
				MaxIdleConns:          8,
				IdleConnTimeout:       90 * time.Second,
				TLSHandshakeTimeout:   15 * time.Second,
				ResponseHeaderTimeout: 20 * time.Second,
			},
		},
	}
}

func (r *dohResolver) Exchange(ctx context.Context, query []byte) ([]byte, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, r.endpoint, bytes.NewReader(query))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/dns-message")
	req.Header.Set("Accept", "application/dns-message")

	resp, err := r.client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("dns: DoH status %d", resp.StatusCode)
	}
	return io.ReadAll(io.LimitReader(resp.Body, 8192))
}

func (r *dohResolver) Close() {
	r.client.CloseIdleConnections()
}
