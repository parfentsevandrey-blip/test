package mesh

import (
	"context"
	"crypto/rand"
	"encoding/binary"
	"net"
	"net/netip"
	"strconv"
	"syscall"
	"time"

	"golang.org/x/crypto/chacha20poly1305"
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
//
// A beacon is sealed with a key derived from the mesh's root key (AEAD, fresh
// random nonce each time): a bystander on the same network can neither read the
// device key inside nor recognise two beacons as coming from the same device or
// mesh, so the broadcast cannot be used to follow someone from café to café, and
// cannot be forged by anyone outside the mesh.

const (
	defaultLANPort = 41711
	lanVersion     = 2
	beaconLen      = 1 + chacha20poly1305.NonceSizeX + 32 + 2 + chacha20poly1305.Overhead
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

// encodeBeacon: [version][nonce 24][seal(device id 32 | udp port 2)].
func encodeBeacon(key [32]byte, id identity.ID, udpPort int) []byte {
	aead, err := chacha20poly1305.NewX(key[:])
	if err != nil {
		return nil
	}
	b := make([]byte, 0, beaconLen)
	b = append(b, lanVersion)
	b = b[:1+aead.NonceSize()]
	_, _ = rand.Read(b[1:])
	plain := make([]byte, 0, 34)
	plain = append(plain, id[:]...)
	plain = binary.BigEndian.AppendUint16(plain, uint16(udpPort))
	return aead.Seal(b, b[1:], plain, []byte{lanVersion})
}

// decodeBeacon opens a beacon with the mesh's key; ok is false for anything
// that was not sealed by a member of this mesh.
func decodeBeacon(key [32]byte, b []byte) (id identity.ID, udpPort int, ok bool) {
	if len(b) != beaconLen || b[0] != lanVersion {
		return id, 0, false
	}
	aead, err := chacha20poly1305.NewX(key[:])
	if err != nil {
		return id, 0, false
	}
	plain, err := aead.Open(nil, b[1:1+aead.NonceSize()], b[1+aead.NonceSize():], []byte{lanVersion})
	if err != nil || len(plain) != 34 {
		return id, 0, false
	}
	copy(id[:], plain[:32])
	return id, int(binary.BigEndian.Uint16(plain[32:])), true
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
		for _, ifc := range l.interfaces() {
			beacon := encodeBeacon(root.LANKey(), l.n.device().ID, l.n.udpPort) // fresh nonce per send: unlinkable
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
	root := n.Root()
	if root == nil {
		return
	}
	id, port, ok := decodeBeacon(root.LANKey(), b)
	if !ok || id == n.device().ID {
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
