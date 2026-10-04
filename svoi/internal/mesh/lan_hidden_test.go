package mesh

import (
	"context"
	"net"
	"net/netip"
	"os"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"testing"
	"time"

	"golang.org/x/net/ipv4"

	"github.com/parfentsevandrey-blip/test/svoi/internal/magic"
)

// A device whose system will not list its network interfaces (a phone, since Android 11) knows its addresses
// only from the file its app writes. These tests are about what such a device does: the same things as any other.

// hideInterfaces makes the system refuse to list the interfaces, as Android does, and gives the program the
// file the app would write.
func hideInterfaces(t *testing.T, fileContent string) {
	t.Helper()
	t.Setenv(magic.HideInterfacesEnv, "1")
	f := filepath.Join(t.TempDir(), "local-addrs.txt")
	if err := os.WriteFile(f, []byte(fileContent), 0o600); err != nil {
		t.Fatal(err)
	}
	t.Setenv("THEMESH_LOCAL_ADDRS_FILE", f)
	localNetworks.at = time.Time{} // do not trust what an earlier test cached
	t.Cleanup(func() { localNetworks.at = time.Time{} })
}

func TestBeaconFromTheLocalNetworkIsBelievedWithoutAnInterfaceList(t *testing.T) {
	hideInterfaces(t, "192.168.1.50/24\n10.20.30.40\n")
	for ip, want := range map[string]bool{
		"192.168.1.77": true,  // a neighbour on the phone's Wi-Fi
		"192.168.2.77": false, // another network
		"8.8.8.8":      false, // a routed or spoofed source
		"10.20.30.41":  false, // the mobile data link has no neighbours: its line names no prefix
		"127.0.0.1":    true,  // several nodes on one machine
	} {
		if got := fromLocalNetwork(netip.MustParseAddr(ip)); got != want {
			t.Errorf("fromLocalNetwork(%s) = %v, want %v", ip, got, want)
		}
	}
}

func freeUDPPort(t *testing.T) int {
	t.Helper()
	pc, err := net.ListenPacket("udp4", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer pc.Close()
	return pc.LocalAddr().(*net.UDPAddr).Port
}

// The node's beacons leave on the one network it has an address on, to the multicast group and to the
// broadcast address, although it could not have found that network by itself.
func TestBeaconsLeaveWithoutAnInterfaceList(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("Windows always lists its interfaces; the fallback is not used there")
	}
	var ln magic.LocalNet
	for _, n := range magic.LocalNets() {
		if _, ok := n.Broadcast(); ok && n.Iface != nil {
			ln = n
			break
		}
	}
	if ln.Iface == nil {
		t.Skip("no network card with a multicast-capable IPv4 address and a broadcast address here")
	}
	bcast, _ := ln.Broadcast()
	port := freeUDPPort(t)

	// Somebody else on the network, listening for beacons.
	lc := net.ListenConfig{Control: reuseAddrControl}
	pc, err := lc.ListenPacket(context.Background(), "udp4", ":"+strconv.Itoa(port))
	if err != nil {
		t.Fatal(err)
	}
	defer pc.Close()
	p4 := ipv4.NewPacketConn(pc)
	if err := p4.JoinGroup(ln.Iface, &net.UDPAddr{IP: lanGroup}); err != nil {
		t.Skipf("cannot listen to the multicast group on %s here: %v", ln.Iface.Name, err)
	}
	if err := p4.SetControlMessage(ipv4.FlagDst, true); err != nil {
		t.Skipf("cannot see the destination of a packet here: %v", err)
	}

	// The phone: it cannot list its interfaces, and its app says it is at ln.Addr.
	hideInterfaces(t, netip.PrefixFrom(ln.Addr, ln.Prefix).String()+"\n")
	n, err := Open(Config{
		Dir:        t.TempDir(),
		DeviceName: "phone",
		Owner:      "tester",
		Timing:     testTiming,
		LANPort:    port,
	})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { n.Close() })
	if err := n.CreateMesh("Home", "phone", "tester"); err != nil {
		t.Fatal(err)
	}
	key := n.Root().LANKey()

	var gotMulticast, gotBroadcast bool
	buf := make([]byte, 512)
	deadline := time.Now().Add(15 * time.Second)
	for time.Now().Before(deadline) && !(gotMulticast && gotBroadcast) {
		_ = pc.SetReadDeadline(time.Now().Add(time.Second))
		nr, cm, from, err := p4.ReadFrom(buf)
		if err != nil {
			continue
		}
		id, udpPort, ok := decodeBeacon(key, buf[:nr])
		if !ok || id != n.ID() {
			continue
		}
		if udpPort != n.udpPort {
			t.Errorf("the beacon names port %d, the node listens on %d", udpPort, n.udpPort)
		}
		if got := from.(*net.UDPAddr).IP.String(); got != ln.Addr.String() {
			t.Errorf("the beacon came from %s, want the app's address %s", got, ln.Addr)
		}
		switch {
		case cm != nil && cm.Dst.Equal(lanGroup):
			gotMulticast = true
		case cm != nil && cm.Dst.Equal(net.IP(bcast.AsSlice())):
			gotBroadcast = true
		}
	}
	// A machine that cannot send to the group at all cannot show that this node can: the macOS machines of CI answer "no route to
	// host" to every multicast send (the system's permission for the local network is not given to a program there), a plain
	// socket included. The multicast of such a machine is not judged; its broadcast still is.
	viaInterface, byAddress := "not tried", "not tried"
	multicastPossible := true
	if !gotMulticast {
		viaInterface, byAddress = probeMulticast(t, pc, ln, false), probeMulticast(t, pc, ln, true)
		if strings.HasPrefix(viaInterface, "send failed") && strings.HasPrefix(byAddress, "send failed") {
			t.Logf("this machine cannot send to the multicast group at all (%s): only the broadcast is checked", viaInterface)
			multicastPossible = false
		}
	}
	if (multicastPossible && !gotMulticast) || (!gotBroadcast && (runtime.GOOS == "linux" || !multicastPossible)) {
		// Say what this machine can do, so that a failure can be told apart: a network that does not deliver multicast to
		// the machine's own listeners (a virtual machine) from a fault in the way the group is reached by address.
		t.Fatalf("beacons that reached a listener on %s: multicast=%v broadcast(%v)=%v; LAN status of the node: %+v; "+
			"a plain multicast sender through the interface reaches the listener: %v; one that picks the network by address: %v",
			ln, gotMulticast, bcast, gotBroadcast, n.LANStatus(), viaInterface, byAddress)
	}
	st := n.LANStatus()
	if !st.Enabled || st.Problem != "" || len(st.Networks) != 1 {
		t.Errorf("LAN status of a working node: %+v", st)
	}
}

// probeMulticast sends one multicast datagram to the beacon group from a fresh socket, choosing the network by the interface
// or by the address, and reports whether the listener pc hears it (or what stopped it).
func probeMulticast(t *testing.T, pc net.PacketConn, ln magic.LocalNet, byAddr bool) string {
	t.Helper()
	c, err := net.ListenUDP("udp4", &net.UDPAddr{IP: net.IP(ln.Addr.AsSlice())})
	if err != nil {
		return "no socket: " + err.Error()
	}
	defer c.Close()
	if byAddr {
		if err := setMulticastIfByAddr(c, ln.Addr); err != nil {
			return "cannot choose the network by address: " + err.Error()
		}
	} else if err := ipv4.NewPacketConn(c).SetMulticastInterface(ln.Iface); err != nil {
		return "cannot choose the interface: " + err.Error()
	}
	_ = ipv4.NewPacketConn(c).SetMulticastLoopback(true)
	port := pc.LocalAddr().(*net.UDPAddr).Port
	probe := []byte("themesh-probe")
	if _, err := c.WriteToUDP(probe, &net.UDPAddr{IP: lanGroup, Port: port}); err != nil {
		return "send failed: " + err.Error()
	}
	buf := make([]byte, 64)
	end := time.Now().Add(3 * time.Second)
	for time.Now().Before(end) {
		_ = pc.SetReadDeadline(time.Now().Add(500 * time.Millisecond))
		n, _, err := pc.ReadFrom(buf)
		if err == nil && string(buf[:n]) == string(probe) {
			return "yes"
		}
	}
	return "no (sent, nothing came)"
}
