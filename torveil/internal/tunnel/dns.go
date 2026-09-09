package tunnel

import (
	"errors"
	"fmt"
	"io"
	"net"
	"sync"
	"time"
)

// dnsQueryTimeout bounds one upstream lookup. Tor resolves names at the exit
// relay, so a query crosses the whole circuit and back; the timeout has to be
// generous compared with a LAN resolver.
const dnsQueryTimeout = 20 * time.Second

// maxDNSMessage is the largest UDP DNS message accepted, matching the EDNS0
// buffer size Tor's DNSPort works with.
const maxDNSMessage = 4096

// DNSForwarder bridges the system resolver to Tor's DNSPort.
//
// Windows always sends DNS to port 53, while Tor's DNSPort is on an ephemeral
// loopback port, so something has to sit in between. Keeping DNS out of the
// TUN entirely also sidesteps a structural problem: DNS is UDP, Tor carries no
// UDP, and a query that entered the tunnel would have nowhere to go.
//
// Messages are relayed byte for byte. There is no parsing, no caching and no
// rewriting, so there is nothing here that can leak a name by mistake.
type DNSForwarder struct {
	upstream string
	log      LogFunc

	mu      sync.Mutex
	udp     *net.UDPConn
	tcp     net.Listener
	running bool
	wg      sync.WaitGroup
	done    chan struct{}
}

// NewDNSForwarder creates a forwarder pointing at Tor's DNSPort.
func NewDNSForwarder(upstream string, log LogFunc) *DNSForwarder {
	return &DNSForwarder{upstream: upstream, log: log}
}

func (f *DNSForwarder) logf(level, format string, args ...any) {
	if f.log != nil {
		f.log(level, fmt.Sprintf(format, args...))
	}
}

// Start binds the forwarder. listen is normally "127.0.0.1:53".
func (f *DNSForwarder) Start(listen string) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.running {
		return errors.New("DNS forwarder is already running")
	}

	udpAddr, err := net.ResolveUDPAddr("udp", listen)
	if err != nil {
		return fmt.Errorf("resolve DNS listen address %q: %w", listen, err)
	}
	udp, err := net.ListenUDP("udp", udpAddr)
	if err != nil {
		return fmt.Errorf("bind DNS forwarder on %s: %w", listen, err)
	}
	tcp, err := net.Listen("tcp", listen)
	if err != nil {
		udp.Close()
		return fmt.Errorf("bind DNS forwarder (TCP) on %s: %w", listen, err)
	}

	f.udp, f.tcp, f.running = udp, tcp, true
	f.done = make(chan struct{})

	f.wg.Add(2)
	go f.serveUDP(udp)
	go f.serveTCP(tcp)

	f.logf("info", "DNS forwarder listening on %s, resolving via Tor at %s", listen, f.upstream)
	return nil
}

// Stop closes the forwarder.
func (f *DNSForwarder) Stop() {
	f.mu.Lock()
	if !f.running {
		f.mu.Unlock()
		return
	}
	f.running = false
	udp, tcp, done := f.udp, f.tcp, f.done
	f.udp, f.tcp = nil, nil
	f.mu.Unlock()

	close(done)
	if udp != nil {
		udp.Close()
	}
	if tcp != nil {
		tcp.Close()
	}
	f.wg.Wait()
}

func (f *DNSForwarder) serveUDP(conn *net.UDPConn) {
	defer f.wg.Done()
	buf := make([]byte, maxDNSMessage)
	for {
		n, client, err := conn.ReadFromUDP(buf)
		if err != nil {
			if errors.Is(err, net.ErrClosed) {
				return
			}
			select {
			case <-f.done:
				return
			default:
			}
			f.logf("warn", "DNS read: %v", err)
			continue
		}
		query := make([]byte, n)
		copy(query, buf[:n])

		f.wg.Add(1)
		go func() {
			defer f.wg.Done()
			f.forwardUDP(conn, client, query)
		}()
	}
}

// forwardUDP relays one query to Tor and writes the answer back.
func (f *DNSForwarder) forwardUDP(conn *net.UDPConn, client *net.UDPAddr, query []byte) {
	up, err := net.DialTimeout("udp", f.upstream, 5*time.Second)
	if err != nil {
		f.logf("warn", "DNS upstream dial: %v", err)
		return
	}
	defer up.Close()

	_ = up.SetDeadline(time.Now().Add(dnsQueryTimeout))
	if _, err := up.Write(query); err != nil {
		f.logf("warn", "DNS upstream write: %v", err)
		return
	}

	resp := make([]byte, maxDNSMessage)
	n, err := up.Read(resp)
	if err != nil {
		f.logf("info", "DNS upstream read: %v", err)
		return
	}
	if _, err := conn.WriteToUDP(resp[:n], client); err != nil {
		f.logf("warn", "DNS reply: %v", err)
	}
}

func (f *DNSForwarder) serveTCP(ln net.Listener) {
	defer f.wg.Done()
	for {
		client, err := ln.Accept()
		if err != nil {
			if errors.Is(err, net.ErrClosed) {
				return
			}
			select {
			case <-f.done:
				return
			default:
			}
			f.logf("warn", "DNS accept: %v", err)
			return
		}
		f.wg.Add(1)
		go func() {
			defer f.wg.Done()
			defer client.Close()
			f.forwardTCP(client)
		}()
	}
}

// forwardTCP pipes a DNS-over-TCP session to Tor. The framing is
// length-prefixed in both directions, so relaying the raw stream is correct
// without interpreting any of it.
func (f *DNSForwarder) forwardTCP(client net.Conn) {
	up, err := net.DialTimeout("tcp", f.upstream, 5*time.Second)
	if err != nil {
		f.logf("warn", "DNS upstream dial (TCP): %v", err)
		return
	}
	defer up.Close()

	_ = client.SetDeadline(time.Now().Add(dnsQueryTimeout))
	_ = up.SetDeadline(time.Now().Add(dnsQueryTimeout))

	var wg sync.WaitGroup
	wg.Add(2)
	go func() { defer wg.Done(); _, _ = io.Copy(up, client) }()
	go func() { defer wg.Done(); _, _ = io.Copy(client, up) }()
	wg.Wait()
}
