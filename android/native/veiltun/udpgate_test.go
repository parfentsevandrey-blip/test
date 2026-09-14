package veiltun

import (
	"context"
	"testing"
	"time"

	"github.com/xjasonlyu/tun2socks/v2/core"
	"github.com/xjasonlyu/tun2socks/v2/core/adapter"
	"gvisor.dev/gvisor/pkg/buffer"
	"gvisor.dev/gvisor/pkg/tcpip"
	"gvisor.dev/gvisor/pkg/tcpip/checksum"
	"gvisor.dev/gvisor/pkg/tcpip/header"
	"gvisor.dev/gvisor/pkg/tcpip/link/channel"
	"gvisor.dev/gvisor/pkg/tcpip/network/ipv4"
	"gvisor.dev/gvisor/pkg/tcpip/stack"
)

// A stack over a channel link rather than a TUN, so a packet can be pushed in
// and whatever comes back out can be read without a device.
func gatedStack(t *testing.T, blockUDP bool) (*channel.Endpoint, *handler) {
	t.Helper()
	cfg := NewConfig()
	cfg.SocksAddr = "127.0.0.1:1"
	cfg.DNSMode = DNSDrop
	cfg.BlockUDP = blockUDP
	h, err := newHandler(cfg)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(h.Close)

	link := channel.New(32, 1500, "")
	st, err := core.CreateStack(&core.Config{LinkEndpoint: link, TransportHandler: h})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(st.Close)
	installUDPGate(st, h)
	return link, h
}

var _ adapter.TransportHandler = (*handler)(nil)

// One UDP datagram from the phone's side of the tunnel to a port on the
// internet, framed as it would arrive on the TUN.
func udpDatagram(dstPort uint16) *stack.PacketBuffer {
	src := tcpip.AddrFrom4([4]byte{10, 55, 0, 1})
	dst := tcpip.AddrFrom4([4]byte{1, 1, 1, 1})
	payload := []byte("hello")

	buf := make([]byte, header.IPv4MinimumSize+header.UDPMinimumSize+len(payload))
	ip := header.IPv4(buf)
	ip.Encode(&header.IPv4Fields{
		TotalLength: uint16(len(buf)),
		TTL:         64,
		Protocol:    uint8(header.UDPProtocolNumber),
		SrcAddr:     src,
		DstAddr:     dst,
	})
	ip.SetChecksum(^ip.CalculateChecksum())

	u := header.UDP(buf[header.IPv4MinimumSize:])
	u.Encode(&header.UDPFields{
		SrcPort: 40000,
		DstPort: dstPort,
		Length:  uint16(header.UDPMinimumSize + len(payload)),
	})
	copy(u.Payload(), payload)
	sum := header.PseudoHeaderChecksum(header.UDPProtocolNumber, src, dst, u.Length())
	sum = checksum.Checksum(payload, sum)
	u.SetChecksum(^u.CalculateChecksum(sum))

	return stack.NewPacketBuffer(stack.PacketBufferOptions{Payload: buffer.MakeWithData(buf)})
}

// What came back out, if anything: the IPv4 protocol and, for ICMP, its type
// and code.
func readReply(t *testing.T, link *channel.Endpoint, wait time.Duration) (proto uint8, icmpType header.ICMPv4Type, icmpCode header.ICMPv4Code, ok bool) {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), wait)
	defer cancel()
	pkt := link.ReadContext(ctx)
	if pkt == nil {
		return 0, 0, 0, false
	}
	defer pkt.DecRef()
	raw := pkt.ToView().AsSlice()
	ip := header.IPv4(raw)
	if !ip.IsValid(len(raw)) {
		t.Fatalf("reply is not IPv4: %x", raw)
	}
	proto = ip.Protocol()
	if proto == uint8(header.ICMPv4ProtocolNumber) {
		icmp := header.ICMPv4(raw[ip.HeaderLength():])
		return proto, icmp.Type(), icmp.Code(), true
	}
	return proto, 0, 0, true
}

func TestBlockedUDPIsRefusedWithICMP(t *testing.T) {
	link, _ := gatedStack(t, true)
	before := stats.blocked.Load()

	link.InjectInbound(ipv4.ProtocolNumber, udpDatagram(443))

	proto, typ, code, ok := readReply(t, link, 2*time.Second)
	if !ok {
		t.Fatal("a QUIC datagram vanished: nothing came back, so the application would wait out its own timeout")
	}
	if proto != uint8(header.ICMPv4ProtocolNumber) || typ != header.ICMPv4DstUnreachable || code != header.ICMPv4PortUnreachable {
		t.Fatalf("got proto %d icmp type %d code %d, want ICMP destination unreachable / port unreachable", proto, typ, code)
	}
	if stats.blocked.Load() != before+1 {
		t.Fatalf("the leak guard's counter did not move: %d -> %d", before, stats.blocked.Load())
	}
}

func TestDNSIsNotRefused(t *testing.T) {
	link, _ := gatedStack(t, true)

	// DNS goes to the resolver, which in this test drops it. What matters is
	// that the stack did not send port unreachable: the flow was accepted.
	link.InjectInbound(ipv4.ProtocolNumber, udpDatagram(53))

	if proto, typ, _, ok := readReply(t, link, 300*time.Millisecond); ok &&
		proto == uint8(header.ICMPv4ProtocolNumber) && typ == header.ICMPv4DstUnreachable {
		t.Fatal("a DNS query was refused with ICMP; it must be accepted and answered by the resolver")
	}
}

func TestUDPIsAcceptedWhenNotBlocked(t *testing.T) {
	link, _ := gatedStack(t, false)

	// With the guard off the datagram is handed to the relay, which fails to
	// reach the proxy in this test; either way there is no ICMP refusal.
	link.InjectInbound(ipv4.ProtocolNumber, udpDatagram(443))

	if proto, typ, _, ok := readReply(t, link, 300*time.Millisecond); ok &&
		proto == uint8(header.ICMPv4ProtocolNumber) && typ == header.ICMPv4DstUnreachable {
		t.Fatal("UDP was refused although the leak guard is off")
	}
}
