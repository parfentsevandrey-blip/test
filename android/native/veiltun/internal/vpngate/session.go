// Package vpngate carries the device's traffic through a volunteer OpenVPN
// server from the VPN Gate project.
//
// The shape of this is dictated by what the app already is. Everything above
// the tunnel — the TUN interface, per-app routing, DNS, circuit isolation —
// talks to one thing: a SOCKS5 proxy on loopback. Tor provides one. So rather
// than teaching the tunnel about a second kind of upstream, this provides one
// too, and nothing above it needs to know the difference.
//
// Underneath, three pieces stack up. An OpenVPN client in pure Go (minivpn)
// hands us raw IP packets. A userspace TCP/IP stack (gVisor, which the tunnel
// already carries) turns those packets into connections. And a small SOCKS5
// server puts those connections behind the interface the rest of the app
// speaks.
//
// What this is not is anonymity. The volunteer running the server sees every
// destination and, for anything not itself encrypted, every byte. VPN Gate says
// so plainly and keeps connection logs for abuse handling. It is a way past a
// filter, and the app must say that where the user chooses it.
package vpngate

import (
	"context"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"net"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/ooni/minivpn/pkg/config"
	"github.com/ooni/minivpn/pkg/tunnel"

	"gvisor.dev/gvisor/pkg/buffer"
	"gvisor.dev/gvisor/pkg/tcpip"
	"gvisor.dev/gvisor/pkg/tcpip/adapters/gonet"
	"gvisor.dev/gvisor/pkg/tcpip/link/channel"
	"gvisor.dev/gvisor/pkg/tcpip/network/ipv4"
	"gvisor.dev/gvisor/pkg/tcpip/stack"
	"gvisor.dev/gvisor/pkg/tcpip/transport/tcp"
	"gvisor.dev/gvisor/pkg/tcpip/transport/udp"
)

// Events reports what a session is doing, for the app's log.
type Events interface {
	Connected(server string, millis int64)
	Failed(server string, reason string)
	Stopped(reason string)
}

// Session is one live tunnel plus the loopback SOCKS5 server in front of it.
type Session struct {
	mu       sync.Mutex
	tun      *tunnel.TUN
	stack    *stack.Stack
	nicID    tcpip.NICID
	listener net.Listener
	closed   chan struct{}
	closeOne sync.Once

	// Address the server handed us, kept for the diagnostic.
	Address string
	// Where the SOCKS5 server is listening.
	Port int
}

// Dial brings up a tunnel to one server and returns it with its SOCKS5 port
// already accepting.
//
// stateDir is where the configuration is written for the moment minivpn needs
// to read it back; it is removed before this returns. The material in it is a
// client certificate and key that VPN Gate publishes to everyone, identical in
// every configuration it hands out, so this is not a secret being spilled — but
// it is still not left lying about.
func Dial(ctx context.Context, stateDir, ovpn string, events Events) (*Session, error) {
	remote := remoteOf(ovpn)

	path := filepath.Join(stateDir, "vpngate.ovpn")
	if err := os.WriteFile(path, []byte(ovpn), 0o600); err != nil {
		return nil, fmt.Errorf("could not stage the configuration: %w", err)
	}
	defer os.Remove(path)

	started := time.Now()
	cfg := config.NewConfig(config.WithConfigFile(path))
	tun, err := tunnel.Start(ctx, &net.Dialer{Timeout: dialTimeout}, cfg)
	if err != nil {
		if events != nil {
			events.Failed(remote, err.Error())
		}
		return nil, fmt.Errorf("openvpn: %w", err)
	}

	session := &Session{tun: tun, closed: make(chan struct{})}
	if err := session.bringUpStack(); err != nil {
		tun.Close()
		if events != nil {
			events.Failed(remote, err.Error())
		}
		return nil, err
	}
	if err := session.serve(); err != nil {
		session.Close()
		if events != nil {
			events.Failed(remote, err.Error())
		}
		return nil, err
	}
	if events != nil {
		events.Connected(remote, time.Since(started).Milliseconds())
	}
	return session, nil
}

func (s *Session) Close() {
	s.closeOne.Do(func() {
		close(s.closed)
		s.mu.Lock()
		defer s.mu.Unlock()
		if s.listener != nil {
			s.listener.Close()
		}
		if s.tun != nil {
			s.tun.Close()
		}
	})
}

// --- the IP stack over the tunnel ---------------------------------------

func (s *Session) bringUpStack() error {
	local := addressOf(s.tun.LocalAddr())
	if local == nil {
		return fmt.Errorf("the server did not give us an address (%q)", s.tun.LocalAddr())
	}
	s.Address = local.String()

	network := stack.New(stack.Options{
		NetworkProtocols:   []stack.NetworkProtocolFactory{ipv4.NewProtocol},
		TransportProtocols: []stack.TransportProtocolFactory{tcp.NewProtocol, udp.NewProtocol},
	})
	const nicID = tcpip.NICID(1)
	endpoint := channel.New(queueDepth, tunnelMTU, "")
	if err := network.CreateNIC(nicID, endpoint); err != nil {
		return fmt.Errorf("stack: %v", err)
	}
	address := tcpip.AddrFromSlice(local.To4())
	if err := network.AddProtocolAddress(nicID, tcpip.ProtocolAddress{
		Protocol:          ipv4.ProtocolNumber,
		AddressWithPrefix: address.WithPrefix(),
	}, stack.AddressProperties{}); err != nil {
		return fmt.Errorf("stack address: %v", err)
	}
	everywhere, subnetErr := tcpip.NewSubnet(
		tcpip.AddrFromSlice(net.IPv4zero.To4()),
		tcpip.MaskFromBytes(net.IPv4Mask(0, 0, 0, 0)),
	)
	if subnetErr != nil {
		return fmt.Errorf("stack route: %v", subnetErr)
	}
	network.SetRouteTable([]tcpip.Route{{Destination: everywhere, NIC: nicID}})
	s.stack = network
	s.nicID = nicID

	go s.pumpInbound(endpoint)
	go s.pumpOutbound(endpoint)
	return nil
}

// Packets arriving from the VPN server, handed to the stack.
func (s *Session) pumpInbound(endpoint *channel.Endpoint) {
	frame := make([]byte, tunnelMTU+headroom)
	for {
		select {
		case <-s.closed:
			return
		default:
		}
		n, err := s.tun.Read(frame)
		if err != nil {
			s.Close()
			return
		}
		packet := make([]byte, n)
		copy(packet, frame[:n])
		buffered := stack.NewPacketBuffer(stack.PacketBufferOptions{
			Payload: buffer.MakeWithData(packet),
		})
		endpoint.InjectInbound(ipv4.ProtocolNumber, buffered)
		buffered.DecRef()
	}
}

// Packets the stack wants to send, handed to the VPN server.
func (s *Session) pumpOutbound(endpoint *channel.Endpoint) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go func() {
		<-s.closed
		cancel()
	}()
	for {
		packet := endpoint.ReadContext(ctx)
		if packet == nil {
			return
		}
		view := packet.ToView()
		out, err := io.ReadAll(view)
		packet.DecRef()
		if err != nil {
			continue
		}
		if _, err := s.tun.Write(out); err != nil {
			s.Close()
			return
		}
	}
}

func (s *Session) dial(ctx context.Context, address string) (net.Conn, error) {
	host, portText, err := net.SplitHostPort(address)
	if err != nil {
		return nil, err
	}
	port, err := parsePort(portText)
	if err != nil {
		return nil, err
	}
	ip := net.ParseIP(host)
	if ip == nil {
		resolved, err := s.resolve(ctx, host)
		if err != nil {
			return nil, err
		}
		ip = resolved
	}
	if ip.To4() == nil {
		// The tunnel is IPv4 only: VPN Gate servers push a v4 address and
		// nothing else. Failing here is better than a connection that appears
		// to be made and then goes nowhere.
		return nil, fmt.Errorf("no IPv6 through this tunnel (%s)", ip)
	}
	return gonet.DialContextTCP(ctx, s.stack, tcpip.FullAddress{
		NIC:  s.nicID,
		Addr: tcpip.AddrFromSlice(ip.To4()),
		Port: port,
	}, ipv4.ProtocolNumber)
}

// resolve asks a public resolver, inside the tunnel, for one name.
//
// Names normally arrive here already resolved — the tunnel above does its own
// DNS and dials by address — so this is the path for a client that hands us a
// name anyway. Doing it inside the tunnel is the point: a lookup made outside
// it would tell the local network exactly what the tunnel was for.
func (s *Session) resolve(ctx context.Context, name string) (net.IP, error) {
	query, err := dnsQuery(name)
	if err != nil {
		return nil, err
	}
	var lastErr error
	for _, resolver := range resolvers {
		conn, err := gonet.DialUDP(s.stack, nil, &tcpip.FullAddress{
			NIC:  s.nicID,
			Addr: tcpip.AddrFromSlice(net.ParseIP(resolver).To4()),
			Port: 53,
		}, ipv4.ProtocolNumber)
		if err != nil {
			lastErr = fmt.Errorf("dns socket: %v", err)
			continue
		}
		deadline, ok := ctx.Deadline()
		if !ok {
			deadline = time.Now().Add(resolveTimeout)
		}
		conn.SetDeadline(deadline)
		if _, err := conn.Write(query); err != nil {
			conn.Close()
			lastErr = err
			continue
		}
		reply := make([]byte, 512)
		n, err := conn.Read(reply)
		conn.Close()
		if err != nil {
			lastErr = err
			continue
		}
		if ip := firstAddress(reply[:n]); ip != nil {
			return ip, nil
		}
		lastErr = fmt.Errorf("no address for %q", name)
	}
	if lastErr == nil {
		lastErr = errors.New("no resolver answered")
	}
	return nil, lastErr
}

// --- the SOCKS5 server --------------------------------------------------

func (s *Session) serve() error {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return fmt.Errorf("could not open the local proxy: %w", err)
	}
	s.mu.Lock()
	s.listener = listener
	s.Port = listener.Addr().(*net.TCPAddr).Port
	s.mu.Unlock()

	go func() {
		for {
			conn, err := listener.Accept()
			if err != nil {
				return
			}
			go s.handle(conn)
		}
	}()
	return nil
}

func (s *Session) handle(conn net.Conn) {
	defer conn.Close()
	conn.SetDeadline(time.Now().Add(handshakeTimeout))

	target, err := socksHandshake(conn)
	if err != nil {
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), dialTimeout)
	defer cancel()
	upstream, err := s.dial(ctx, target)
	if err != nil {
		// 0x01, a general failure: the client only needs to know it did not
		// happen, and a more specific code would be a guess.
		conn.Write([]byte{5, 1, 0, 1, 0, 0, 0, 0, 0, 0})
		return
	}
	defer upstream.Close()

	if _, err := conn.Write([]byte{5, 0, 0, 1, 0, 0, 0, 0, 0, 0}); err != nil {
		return
	}
	conn.SetDeadline(time.Time{})

	done := make(chan struct{}, 2)
	go func() { io.Copy(upstream, conn); done <- struct{}{} }()
	go func() { io.Copy(conn, upstream); done <- struct{}{} }()
	select {
	case <-done:
	case <-s.closed:
	}
}

// socksHandshake reads a SOCKS5 CONNECT request and returns its destination.
//
// Username and password authentication is accepted and the credentials are
// then discarded. That is not laziness: the tunnel above derives a fresh
// credential per destination or per connection, because that is what makes Tor
// use a separate circuit for each. There are no circuits here, so the
// credentials mean nothing — but refusing them would refuse every connection
// the app makes.
func socksHandshake(conn net.Conn) (string, error) {
	header := make([]byte, 2)
	if _, err := io.ReadFull(conn, header); err != nil {
		return "", err
	}
	if header[0] != 5 {
		return "", fmt.Errorf("not SOCKS5")
	}
	methods := make([]byte, int(header[1]))
	if _, err := io.ReadFull(conn, methods); err != nil {
		return "", err
	}
	offersUserPass := false
	for _, method := range methods {
		if method == 2 {
			offersUserPass = true
		}
	}
	if offersUserPass {
		if _, err := conn.Write([]byte{5, 2}); err != nil {
			return "", err
		}
		if err := readUserPass(conn); err != nil {
			return "", err
		}
	} else if _, err := conn.Write([]byte{5, 0}); err != nil {
		return "", err
	}

	request := make([]byte, 4)
	if _, err := io.ReadFull(conn, request); err != nil {
		return "", err
	}
	if request[1] != 1 {
		conn.Write([]byte{5, 7, 0, 1, 0, 0, 0, 0, 0, 0}) // command not supported
		return "", fmt.Errorf("only CONNECT is supported")
	}

	var host string
	switch request[3] {
	case 1:
		address := make([]byte, 4)
		if _, err := io.ReadFull(conn, address); err != nil {
			return "", err
		}
		host = net.IP(address).String()
	case 3:
		size := make([]byte, 1)
		if _, err := io.ReadFull(conn, size); err != nil {
			return "", err
		}
		name := make([]byte, int(size[0]))
		if _, err := io.ReadFull(conn, name); err != nil {
			return "", err
		}
		host = string(name)
	case 4:
		address := make([]byte, 16)
		if _, err := io.ReadFull(conn, address); err != nil {
			return "", err
		}
		host = net.IP(address).String()
	default:
		return "", fmt.Errorf("unknown address type %d", request[3])
	}

	portBytes := make([]byte, 2)
	if _, err := io.ReadFull(conn, portBytes); err != nil {
		return "", err
	}
	return net.JoinHostPort(host, fmt.Sprint(binary.BigEndian.Uint16(portBytes))), nil
}

func readUserPass(conn net.Conn) error {
	header := make([]byte, 2)
	if _, err := io.ReadFull(conn, header); err != nil {
		return err
	}
	user := make([]byte, int(header[1]))
	if _, err := io.ReadFull(conn, user); err != nil {
		return err
	}
	size := make([]byte, 1)
	if _, err := io.ReadFull(conn, size); err != nil {
		return err
	}
	password := make([]byte, int(size[0]))
	if _, err := io.ReadFull(conn, password); err != nil {
		return err
	}
	_, err := conn.Write([]byte{1, 0})
	return err
}

// --- small helpers ------------------------------------------------------

func addressOf(addr net.Addr) net.IP {
	text := addr.String()
	if host, _, err := net.SplitHostPort(text); err == nil {
		text = host
	}
	ip := net.ParseIP(strings.TrimSpace(text))
	if ip == nil || ip.To4() == nil {
		return nil
	}
	return ip
}

func parsePort(text string) (uint16, error) {
	var port uint16
	if _, err := fmt.Sscanf(text, "%d", &port); err != nil {
		return 0, fmt.Errorf("bad port %q", text)
	}
	return port, nil
}

// remoteOf pulls the server endpoint out of a configuration, for logging.
func remoteOf(ovpn string) string {
	for _, line := range strings.Split(ovpn, "\n") {
		fields := strings.Fields(strings.TrimSpace(line))
		if len(fields) >= 3 && fields[0] == "remote" {
			return fields[1] + ":" + fields[2]
		}
	}
	return "unknown"
}

func dnsQuery(name string) ([]byte, error) {
	labels := strings.Split(strings.TrimSuffix(name, "."), ".")
	out := make([]byte, 0, 32+len(name))
	out = append(out, 0x00, 0x01) // a fixed id is fine on a fresh socket
	out = append(out, 0x01, 0x00) // recursion desired
	out = append(out, 0x00, 0x01) // one question
	out = append(out, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
	for _, label := range labels {
		if len(label) == 0 || len(label) > 63 {
			return nil, fmt.Errorf("bad name %q", name)
		}
		out = append(out, byte(len(label)))
		out = append(out, label...)
	}
	out = append(out, 0x00)
	out = append(out, 0x00, 0x01) // A
	out = append(out, 0x00, 0x01) // IN
	return out, nil
}

// firstAddress walks a DNS reply and returns the first A record in it.
func firstAddress(reply []byte) net.IP {
	if len(reply) < 12 {
		return nil
	}
	questions := int(binary.BigEndian.Uint16(reply[4:6]))
	answers := int(binary.BigEndian.Uint16(reply[6:8]))
	offset := 12
	skipName := func() bool {
		for offset < len(reply) {
			size := int(reply[offset])
			if size == 0 {
				offset++
				return true
			}
			if size&0xC0 == 0xC0 {
				offset += 2
				return true
			}
			offset += size + 1
		}
		return false
	}
	for i := 0; i < questions; i++ {
		if !skipName() || offset+4 > len(reply) {
			return nil
		}
		offset += 4
	}
	for i := 0; i < answers; i++ {
		if !skipName() || offset+10 > len(reply) {
			return nil
		}
		recordType := binary.BigEndian.Uint16(reply[offset : offset+2])
		size := int(binary.BigEndian.Uint16(reply[offset+8 : offset+10]))
		offset += 10
		if offset+size > len(reply) {
			return nil
		}
		if recordType == 1 && size == 4 {
			return net.IP(append([]byte(nil), reply[offset:offset+4]...))
		}
		offset += size
	}
	return nil
}

const (
	tunnelMTU        = 1500
	headroom         = 512
	queueDepth       = 512
	dialTimeout      = 20 * time.Second
	handshakeTimeout = 15 * time.Second
	resolveTimeout   = 8 * time.Second
)

// Public resolvers reached from inside the tunnel. Two, so one being unreachable
// from a particular volunteer's network is not the end of it.
var resolvers = []string{"1.1.1.1", "9.9.9.9"}
