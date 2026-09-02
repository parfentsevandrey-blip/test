// Command vpngatelab connects to a VPN Gate volunteer server and fetches a
// page through it, using exactly the libraries the app would.
//
// The point is to answer, before a line of the app changes, whether the whole
// chain works: the public server list, an OpenVPN client in pure Go, and a
// userspace IP stack on top of it that can open an ordinary TCP connection.
package main

import (
	"bufio"
	"context"
	"encoding/base64"
	"encoding/csv"
	"flag"
	"fmt"
	"io"
	"net"
	"net/http"
	"os"
	"sort"
	"strconv"
	"strings"
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

type server struct {
	host    string
	ip      string
	country string
	score   int
	ping    int
	config  string
	proto   string
	port    int
}

func main() {
	listPath := flag.String("list", "/tmp/vpngate.csv", "path to the VPN Gate CSV")
	target := flag.String("target", "http://1.1.1.1/", "URL to fetch through the tunnel")
	tries := flag.Int("tries", 3, "how many servers to try")
	country := flag.String("country", "", "only servers in this country code")
	// Only for running this harness inside a sandbox that has no raw outbound
	// TCP. The app dials directly; nothing about the tunnel changes.
	proxy := flag.String("proxy", "", "reach the server through this HTTP CONNECT proxy (host:port)")
	flag.Parse()
	connectProxy = *proxy

	servers, err := loadServers(*listPath, *country)
	if err != nil {
		fmt.Println("could not read the list:", err)
		os.Exit(1)
	}
	fmt.Printf("%d TCP servers in the list\n", len(servers))

	for i, s := range servers {
		if i >= *tries {
			break
		}
		fmt.Printf("\n--- %s (%s) %s:%d score=%d ping=%dms ---\n",
			s.host, s.country, s.ip, s.port, s.score, s.ping)
		if err := attempt(s, *target); err != nil {
			fmt.Println("  failed:", err)
			continue
		}
		fmt.Println("  WORKED")
		return
	}
	fmt.Println("\nno server worked")
	os.Exit(1)
}

var connectProxy string

// SimpleDialer as minivpn wants it.
type dialFunc func(ctx context.Context, network, endpoint string) (net.Conn, error)

func (f dialFunc) DialContext(ctx context.Context, network, endpoint string) (net.Conn, error) {
	return f(ctx, network, endpoint)
}

func dialer() dialFunc {
	base := &net.Dialer{Timeout: 15 * time.Second}
	if connectProxy == "" {
		return base.DialContext
	}
	return func(ctx context.Context, network, endpoint string) (net.Conn, error) {
		conn, err := base.DialContext(ctx, "tcp", connectProxy)
		if err != nil {
			return nil, err
		}
		request := fmt.Sprintf("CONNECT %s HTTP/1.1\r\nHost: %s\r\n\r\n", endpoint, endpoint)
		if _, err := conn.Write([]byte(request)); err != nil {
			conn.Close()
			return nil, err
		}
		reply := make([]byte, 256)
		n, err := conn.Read(reply)
		if err != nil {
			conn.Close()
			return nil, err
		}
		if !strings.Contains(string(reply[:n]), " 200 ") {
			conn.Close()
			return nil, fmt.Errorf("proxy refused: %q", strings.SplitN(string(reply[:n]), "\r\n", 2)[0])
		}
		return conn, nil
	}
}

func attempt(s server, target string) error {
	path := "/tmp/vpngate-" + s.host + ".ovpn"
	if err := os.WriteFile(path, []byte(s.config), 0o600); err != nil {
		return err
	}
	defer os.Remove(path)

	cfg := config.NewConfig(config.WithConfigFile(path))
	ctx, cancel := context.WithTimeout(context.Background(), 45*time.Second)
	defer cancel()

	started := time.Now()
	tun, err := tunnel.Start(ctx, dialer(), cfg)
	if err != nil {
		return fmt.Errorf("openvpn handshake: %w", err)
	}
	defer tun.Close()
	fmt.Printf("  tunnel up in %v, address %s, gateway %s\n",
		time.Since(started).Round(time.Millisecond), tun.LocalAddr(), tun.RemoteAddr())

	net, err := newStack(tun)
	if err != nil {
		return fmt.Errorf("ip stack: %w", err)
	}

	client := &http.Client{
		Transport: &http.Transport{DialContext: net.dial},
		Timeout:   20 * time.Second,
	}
	fetchStarted := time.Now()
	resp, err := client.Get(target)
	if err != nil {
		return fmt.Errorf("fetch: %w", err)
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(io.LimitReader(resp.Body, 256))
	fmt.Printf("  %s in %v, %d bytes: %q\n", resp.Status,
		time.Since(fetchStarted).Round(time.Millisecond), len(body),
		strings.TrimSpace(string(body))[:min(80, len(strings.TrimSpace(string(body))))])
	return nil
}

// --- the userspace IP stack over the tunnel -----------------------------

type netStack struct {
	stack *stack.Stack
	nicID tcpip.NICID
}

func newStack(tun *tunnel.TUN) (*netStack, error) {
	local := net.ParseIP(strings.Split(tun.LocalAddr().String(), ":")[0])
	if local == nil || local.To4() == nil {
		return nil, fmt.Errorf("no usable address from the server: %q", tun.LocalAddr())
	}

	s := stack.New(stack.Options{
		NetworkProtocols:   []stack.NetworkProtocolFactory{ipv4.NewProtocol},
		TransportProtocols: []stack.TransportProtocolFactory{tcp.NewProtocol, udp.NewProtocol},
	})
	const nicID = tcpip.NICID(1)
	ep := channel.New(512, 1500, "")
	if err := s.CreateNIC(nicID, ep); err != nil {
		return nil, fmt.Errorf("CreateNIC: %v", err)
	}
	addr := tcpip.AddrFromSlice(local.To4())
	protoAddr := tcpip.ProtocolAddress{
		Protocol:          ipv4.ProtocolNumber,
		AddressWithPrefix: addr.WithPrefix(),
	}
	if err := s.AddProtocolAddress(nicID, protoAddr, stack.AddressProperties{}); err != nil {
		return nil, fmt.Errorf("AddProtocolAddress: %v", err)
	}
	everything, tcpErr := tcpip.NewSubnet(
		tcpip.AddrFromSlice(net.IPv4zero.To4()),
		tcpip.MaskFromBytes(net.IPv4Mask(0, 0, 0, 0)),
	)
	if tcpErr != nil {
		return nil, fmt.Errorf("route: %v", tcpErr)
	}
	s.SetRouteTable([]tcpip.Route{{Destination: everything, NIC: nicID}})
	s.SetSpoofing(nicID, true)
	s.SetPromiscuousMode(nicID, true)

	// Tunnel to stack.
	go func() {
		buf := make([]byte, 2048)
		for {
			n, err := tun.Read(buf)
			if err != nil {
				return
			}
			packet := make([]byte, n)
			copy(packet, buf[:n])
			pkt := stack.NewPacketBuffer(stack.PacketBufferOptions{
				Payload: buffer.MakeWithData(packet),
			})
			ep.InjectInbound(ipv4.ProtocolNumber, pkt)
			pkt.DecRef()
		}
	}()

	// Stack to tunnel.
	go func() {
		for {
			pkt := ep.ReadContext(context.Background())
			if pkt == nil {
				return
			}
			view := pkt.ToView()
			out, _ := io.ReadAll(view)
			pkt.DecRef()
			if _, err := tun.Write(out); err != nil {
				return
			}
		}
	}()

	return &netStack{stack: s, nicID: nicID}, nil
}

func (n *netStack) dial(ctx context.Context, network, address string) (net.Conn, error) {
	host, portText, err := net.SplitHostPort(address)
	if err != nil {
		return nil, err
	}
	port, err := strconv.Atoi(portText)
	if err != nil {
		return nil, err
	}
	ip := net.ParseIP(host)
	if ip == nil || ip.To4() == nil {
		return nil, fmt.Errorf("this harness only dials IPv4 literals, got %q", host)
	}
	full := tcpip.FullAddress{
		NIC:  n.nicID,
		Addr: tcpip.AddrFromSlice(ip.To4()),
		Port: uint16(port),
	}
	return gonet.DialContextTCP(ctx, n.stack, full, ipv4.ProtocolNumber)
}

// --- the server list ----------------------------------------------------

func loadServers(path, country string) ([]server, error) {
	file, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer file.Close()

	scanner := bufio.NewScanner(file)
	scanner.Buffer(make([]byte, 1024*1024), 8*1024*1024)
	var lines []string
	for scanner.Scan() {
		line := scanner.Text()
		if strings.HasPrefix(line, "*") {
			continue
		}
		lines = append(lines, strings.TrimPrefix(line, "#"))
	}
	reader := csv.NewReader(strings.NewReader(strings.Join(lines, "\n")))
	reader.FieldsPerRecord = -1
	records, err := reader.ReadAll()
	if err != nil {
		return nil, err
	}
	if len(records) < 2 {
		return nil, fmt.Errorf("list has no rows")
	}
	index := map[string]int{}
	for i, name := range records[0] {
		index[name] = i
	}

	var out []server
	for _, row := range records[1:] {
		get := func(name string) string {
			i, ok := index[name]
			if !ok || i >= len(row) {
				return ""
			}
			return row[i]
		}
		raw, err := base64.StdEncoding.DecodeString(get("OpenVPN_ConfigData_Base64"))
		if err != nil || len(raw) == 0 {
			continue
		}
		text := string(raw)
		proto, port := remoteOf(text)
		if proto != "tcp" {
			continue
		}
		if country != "" && !strings.EqualFold(get("CountryShort"), country) {
			continue
		}
		score, _ := strconv.Atoi(get("Score"))
		ping, _ := strconv.Atoi(get("Ping"))
		out = append(out, server{
			host:    get("HostName"),
			ip:      get("IP"),
			country: get("CountryShort"),
			score:   score,
			ping:    ping,
			config:  text,
			proto:   proto,
			port:    port,
		})
	}
	sort.Slice(out, func(i, j int) bool { return out[i].score > out[j].score })
	return out, nil
}

func remoteOf(config string) (string, int) {
	proto, port := "", 0
	for _, line := range strings.Split(config, "\n") {
		fields := strings.Fields(strings.TrimSpace(line))
		if len(fields) == 0 {
			continue
		}
		switch fields[0] {
		case "proto":
			if len(fields) > 1 {
				proto = strings.ToLower(strings.TrimSuffix(fields[1], "\r"))
			}
		case "remote":
			if len(fields) > 2 {
				port, _ = strconv.Atoi(strings.TrimSpace(fields[2]))
			}
		}
	}
	return proto, port
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}
