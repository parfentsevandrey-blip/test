package mesh

import (
	"bytes"
	"context"
	"encoding/binary"
	"net"
	"net/netip"
	"strconv"
	"syscall"
	"time"

	"golang.org/x/net/ipv4"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/magic"
)

// LAN discovery: devices on the same network announce themselves with tiny UDP
// multicast/broadcast beacons so they find each other instantly, without any
// internet connectivity and without going through a NAT. A beacon is only a
// hint ("this device is probably at this address"); the real authentication
// happens in the disco handshake, so a forged beacon can at worst cause a few
// wasted probes.

const (
	defaultLANPort = 41711
	lanVersion     = 1
	beaconLen      = 4 + 1 + 8 + 32 + 2
)

var lanGroup = net.IPv4(239, 255, 77, 77)

type lanDiscovery struct {
	n      *Node
	conn   *net.UDPConn
	pc4    *ipv4.PacketConn
	port   int
	cancel context.CancelFunc
	done   chan struct{}
}

func encodeBeacon(tag [8]byte, id identity.ID, udpPort int) []byte {
	b := make([]byte, 0, beaconLen)
	b = append(b, 'S', 'V', 'L', 'N', lanVersion)
	b = append(b, tag[:]...)
	b = append(b, id[:]...)
	b = binary.BigEndian.AppendUint16(b, uint16(udpPort))
	return b
}

func decodeBeacon(b []byte) (tag [8]byte, id identity.ID, udpPort int, ok bool) {
	if len(b) != beaconLen || !bytes.Equal(b[:4], []byte("SVLN")) || b[4] != lanVersion {
		return tag, id, 0, false
	}
	copy(tag[:], b[5:13])
	copy(id[:], b[13:45])
	return tag, id, int(binary.BigEndian.Uint16(b[45:47])), true
}

func (n *Node) startLAN(parent context.Context) {
	port := n.cfg.LANPort
	if port == 0 {
		port = defaultLANPort
	}
	lc := net.ListenConfig{Control: reuseAddrControl}
	pc, err := lc.ListenPacket(context.Background(), "udp4", ":"+strconv.Itoa(port))
	if err != nil {
		n.log.Warn("LAN discovery disabled", "err", err)
		return
	}
	conn := pc.(*net.UDPConn)
	ctx, cancel := context.WithCancel(parent)
	l := &lanDiscovery{n: n, conn: conn, pc4: ipv4.NewPacketConn(conn), port: port, cancel: cancel, done: make(chan struct{})}
	n.mu.Lock()
	n.lan = l
	n.mu.Unlock()
	go l.run(ctx)
}

func (l *lanDiscovery) stop() {
	l.cancel()
	_ = l.conn.Close()
	<-l.done
}

func (l *lanDiscovery) interfaces() []net.Interface {
	ifs, err := net.Interfaces()
	if err != nil {
		return nil
	}
	var out []net.Interface
	for _, ifc := range ifs {
		if ifc.Flags&net.FlagUp == 0 || ifc.Flags&net.FlagLoopback != 0 || ifc.Flags&net.FlagMulticast == 0 {
			continue
		}
		out = append(out, ifc)
	}
	return out
}

func (l *lanDiscovery) run(ctx context.Context) {
	defer close(l.done)
	go l.readLoop(ctx)

	joined := map[string]bool{}
	send := func() {
		root := l.n.Root()
		if root == nil {
			return
		}
		beacon := encodeBeacon(root.LANTag(), l.n.dev.ID, l.n.udpPort)
		for _, ifc := range l.interfaces() {
			if !joined[ifc.Name] {
				_ = l.pc4.JoinGroup(&ifc, &net.UDPAddr{IP: lanGroup})
				joined[ifc.Name] = true
			}
			_ = l.pc4.SetMulticastInterface(&ifc)
			_ = l.pc4.SetMulticastLoopback(true)
			_, _ = l.conn.WriteToUDP(beacon, &net.UDPAddr{IP: lanGroup, Port: l.port})
			// Also the subnet broadcast: some Wi-Fi access points drop multicast.
			addrs, _ := ifc.Addrs()
			for _, a := range addrs {
				ipn, ok := a.(*net.IPNet)
				if !ok {
					continue
				}
				ip4 := ipn.IP.To4()
				mask := ipn.Mask
				if len(mask) == 16 {
					mask = mask[12:]
				}
				if ip4 == nil || len(mask) != 4 {
					continue
				}
				bc := make(net.IP, 4)
				for i := range bc {
					bc[i] = ip4[i] | ^mask[i]
				}
				_, _ = l.conn.WriteToUDP(beacon, &net.UDPAddr{IP: bc, Port: l.port})
			}
		}
	}
	t := time.NewTicker(5 * time.Second)
	defer t.Stop()
	time.Sleep(500 * time.Millisecond)
	send()
	for {
		select {
		case <-t.C:
			send()
		case <-ctx.Done():
			return
		}
	}
}

func (l *lanDiscovery) readLoop(ctx context.Context) {
	buf := make([]byte, 256)
	for {
		nr, from, err := l.conn.ReadFromUDPAddrPort(buf)
		if err != nil {
			if ctx.Err() != nil {
				return
			}
			if ne, ok := err.(net.Error); ok && ne.Timeout() {
				continue
			}
			if isClosedErr(err) {
				return
			}
			time.Sleep(100 * time.Millisecond)
			continue
		}
		l.n.lanHeard(buf[:nr], from)
	}
}

func isClosedErr(err error) bool {
	if err == nil {
		return false
	}
	if err == net.ErrClosed {
		return true
	}
	if oe, ok := err.(*net.OpError); ok {
		return oe.Err == net.ErrClosed || oe.Err == syscall.EBADF
	}
	return false
}

// lanHeard handles one received beacon.
func (n *Node) lanHeard(b []byte, from netip.AddrPort) {
	tag, id, port, ok := decodeBeacon(b)
	if !ok || id == n.dev.ID {
		return
	}
	root := n.Root()
	if root == nil || tag != root.LANTag() {
		return
	}
	p := n.Peer(id)
	mg := n.Magic()
	if p == nil || mg == nil || port == 0 {
		return // an unknown device: wait until the member list tells us about it
	}
	addr := netip.AddrPortFrom(from.Addr().Unmap(), uint16(port))
	mg.AddCandidates(id, []netip.AddrPort{addr}, magic.SrcLAN)
	if !p.Online() {
		p.mu.Lock()
		p.nextDial = time.Now()
		p.mu.Unlock()
		mg.Poke(id)
		n.kick()
	}
}
