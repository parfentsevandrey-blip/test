package mesh

import (
	"bytes"
	"context"
	"crypto/tls"
	"crypto/x509"
	"net"
	"net/netip"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/quic-go/quic-go"

	"github.com/parfentsevandrey-blip/test/svoi/internal/identity"
	"github.com/parfentsevandrey-blip/test/svoi/internal/magic"
	"github.com/parfentsevandrey-blip/test/svoi/internal/netsim"
)

// A stranger is somebody who is not a member and does not hold an invitation. What a
// node shows and spends on strangers is what these tests pin down.

// knock opens a QUIC connection to target the way a joiner does (a temporary endpoint in
// anonymous mode), with the given ALPN and server name. It returns the server
// certificate it was shown, if any, and the dial error.
func knock(t *testing.T, host *netsim.Host, target netip.AddrPort, alpn, serverName string) (*x509.Certificate, error) {
	t.Helper()
	dev := identity.GenerateDevice()
	mg, err := magic.New(magic.Config{
		Device: dev, Port: 0,
		Listen:     func(port int) (net.PacketConn, error) { return host.ListenPacket(uint16(port)) },
		LocalAddrs: func() []netip.Addr { return host.Addrs() },
		Timing:     testTiming,
	})
	if err != nil {
		t.Fatal(err)
	}
	mg.SetAnonymous(true)
	tr := &quic.Transport{Conn: mg}
	defer func() { _ = tr.Close(); _ = mg.Close() }()
	var seen *x509.Certificate
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	conn, err := tr.Dial(ctx, net.UDPAddrFromAddrPort(target), &tls.Config{
		MinVersion: tls.VersionTLS13, ServerName: serverName, InsecureSkipVerify: true,
		Certificates: []tls.Certificate{selfSignedCert(dev)},
		NextProtos:   []string{alpn},
		VerifyPeerCertificate: func(raw [][]byte, _ [][]*x509.Certificate) error {
			if len(raw) > 0 {
				seen, _ = x509.ParseCertificate(raw[0])
			}
			return nil
		},
	}, &quic.Config{HandshakeIdleTimeout: 3 * time.Second})
	if err == nil {
		_ = conn.CloseWithError(0, "")
	}
	return seen, err
}

func inviteSecret(t *testing.T, code string) []byte {
	t.Helper()
	inv, err := identity.ParseInvite(code)
	if err != nil {
		t.Fatal(err)
	}
	return inv.Secret[:]
}

func TestStrangersAreShownNothing(t *testing.T) {
	nw := netsim.New()
	a := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.1")), "alpha", nil)
	if err := a.CreateMesh("Home", "alpha", "Andrey Parfentsev"); err != nil {
		t.Fatal(err)
	}
	inv, err := a.NewInvite(false, 30*time.Minute) // an invitation is pending: the node listens to anyone
	if err != nil {
		t.Fatal(err)
	}
	secret := inviteSecret(t, inv.Code)
	target := netip.MustParseAddrPort("198.51.100.1:41710")
	evil := nw.Internet().NewHost(ip("198.51.100.66"))

	wrong := append([]byte(nil), secret...)
	wrong[0] ^= 0xFF
	for _, tc := range []struct{ name, alpn, sni string }{
		{"member protocol", ALPNMesh, "themesh"},
		{"member protocol with a token", ALPNMesh, joinSNI(secret)},
		{"join without a token", ALPNJoin, "themesh"},
		{"join with a token for another secret", ALPNJoin, joinSNI(wrong)},
		{"join with a mangled token", ALPNJoin, "zz.zz.join.mesh"},
	} {
		cert, err := knock(t, evil, target, tc.alpn, tc.sni)
		if cert != nil {
			t.Errorf("%s: a stranger was shown the certificate of %q (owner %v)", tc.name, cert.Subject.CommonName, cert.Subject.Organization)
		}
		if err == nil {
			t.Errorf("%s: the handshake succeeded", tc.name)
		}
	}

	// ...while the holder of the invitation gets in.
	tok := joinSNI(secret)
	cert, err := knock(t, evil, target, ALPNJoin, tok)
	if err != nil || cert == nil {
		t.Fatalf("a valid token was refused: cert=%v err=%v", cert != nil, err)
	}
	// Even then the certificate is a plain one: nothing about the device, its owner, the
	// mesh or its addresses (those are for members).
	if len(cert.Subject.Organization) != 0 || len(cert.Issuer.Organization) != 0 || len(cert.DNSNames) != 0 ||
		len(cert.IPAddresses) != 0 || len(cert.URIs) != 0 || len(cert.EmailAddresses) != 0 || cert.Subject.CommonName != "themesh" ||
		len(cert.Extensions) > 2 {
		t.Errorf("the join certificate says too much: subject %q issuer %q dns %v ips %v, %d extensions", cert.Subject, cert.Issuer, cert.DNSNames, cert.IPAddresses, len(cert.Extensions))
	}
	if bytes.Contains(cert.Raw, []byte("Andrey")) || bytes.Contains(cert.Raw, []byte("alpha")) || bytes.Contains(cert.Raw, []byte("Home")) {
		t.Error("the join certificate contains the owner, the device name or the mesh name")
	}
	// A token is good once: copying it off the wire gains nothing.
	if cert, err := knock(t, evil, target, ALPNJoin, tok); err == nil || cert != nil {
		t.Fatalf("a used token was accepted again (cert=%v err=%v)", cert != nil, err)
	}
}

// A token of an invitation that was used up, cancelled or has expired is worth nothing.
func TestTokenOfAGoneInvitationIsRefused(t *testing.T) {
	nw := netsim.New()
	a := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.1")), "alpha", nil)
	if err := a.CreateMesh("Home", "alpha", "x"); err != nil {
		t.Fatal(err)
	}
	inv, err := a.NewInvite(false, 30*time.Minute)
	if err != nil {
		t.Fatal(err)
	}
	other, err := a.NewInvite(false, 30*time.Minute) // keeps the node open to joiners
	if err != nil {
		t.Fatal(err)
	}
	_ = other
	secret := inviteSecret(t, inv.Code)
	if !a.CancelInvite(inv.ID) {
		t.Fatal("the invitation was not found")
	}
	cert, err := knock(t, nw.Internet().NewHost(ip("198.51.100.66")), netip.MustParseAddrPort("198.51.100.1:41710"), ALPNJoin, joinSNI(secret))
	if err == nil || cert != nil {
		t.Fatalf("the token of a cancelled invitation worked (cert=%v err=%v)", cert != nil, err)
	}
}

// Somebody rotating through source addresses used to empty the global join budget
// and keep the person who holds the invitation out. Now a handshake costs budget only
// after it has shown a token, so a stranger cannot spend it at all.
func TestAStrangerWithManyAddressesCannotLockOutTheInvitedDevice(t *testing.T) {
	nw := netsim.New()
	a := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.1")), "alpha", nil)
	friend := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.2")), "friend", nil)
	if err := a.CreateMesh("Home", "alpha", "x"); err != nil {
		t.Fatal(err)
	}
	inv, err := a.NewInvite(false, 30*time.Minute)
	if err != nil {
		t.Fatal(err)
	}
	target := netip.MustParseAddrPort("198.51.100.1:41710")
	const attackers = 150
	var hosts []*netsim.Host
	for i := 0; i < attackers; i++ {
		hosts = append(hosts, nw.Internet().NewHost(netip.AddrFrom4([4]byte{203, 0, byte(i >> 8), byte(i)})))
	}
	var stop atomic.Bool
	var wg sync.WaitGroup
	var cert atomic.Int32
	wg.Add(1)
	go func() { // a new source address for every handshake
		defer wg.Done()
		tick := time.NewTicker(10 * time.Millisecond)
		defer tick.Stop()
		for i := 0; !stop.Load() && i < len(hosts); i++ {
			<-tick.C
			h := hosts[i]
			wg.Add(1)
			go func() {
				defer wg.Done()
				if c, _ := knock(t, h, target, ALPNJoin, "themesh"); c != nil {
					cert.Add(1)
				}
			}()
		}
	}()
	time.Sleep(700 * time.Millisecond) // the flood is on
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	err = friend.JoinMesh(ctx, inv.Code, "friend")
	stop.Store(true)
	wg.Wait()
	if err != nil {
		t.Fatalf("the invited device could not join during the flood: %v", err)
	}
	if n := cert.Load(); n != 0 {
		t.Fatalf("%d of the strangers were shown a certificate", n)
	}
}

// Packets from a stranger's address that claim to come from a member's stand-in
// address (fdc7:5e57::/32) are forgeries: the node must neither answer them nor send
// anything to the member they impersonate.
func TestForgedStandInSourceIsDropped(t *testing.T) {
	nw := netsim.New()
	a := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.1")), "alpha", nil)
	b := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.2")), "beta", nil)
	if err := a.CreateMesh("Home", "alpha", "x"); err != nil {
		t.Fatal(err)
	}
	join(t, a, b, "beta", false)
	waitFor(t, 10*time.Second, "a sees b online", online(a, b))
	waitFor(t, 10*time.Second, "b sees a online", online(b, a))
	if _, err := a.NewInvite(false, 30*time.Minute); err != nil { // anonymous mode on
		t.Fatal(err)
	}
	r8 := b.ID().Route8() // the stand-in address alpha gives beta
	var v [16]byte
	v[0], v[1], v[2], v[3] = 0xfd, 0xc7, 0x5e, 0x57
	copy(v[8:], r8[:])
	hostX := nw.Internet().NewHost(netip.AddrFrom16(v))
	xs, err := hostX.ListenPacket(1)
	if err != nil {
		t.Fatal(err)
	}
	conn := &anonConn{sock: xs, to: net.UDPAddrFromAddrPort(netip.MustParseAddrPort("198.51.100.1:41710")), done: make(chan struct{}), wake: make(chan struct{})}
	var answered atomic.Int64
	go func() {
		buf := make([]byte, 4096)
		for {
			n, _, err := xs.ReadFrom(buf)
			if err != nil {
				return
			}
			answered.Add(int64(n))
		}
	}()
	tr := &quic.Transport{Conn: conn}
	defer tr.Close()
	time.Sleep(300 * time.Millisecond)
	before := b.Magic().Stats(a.ID())
	var wg sync.WaitGroup
	sem := make(chan struct{}, 100)
	for i := 0; i < 200; i++ {
		wg.Add(1)
		sem <- struct{}{}
		go func() {
			defer wg.Done()
			defer func() { <-sem }()
			ctx, cancel := context.WithTimeout(context.Background(), 150*time.Millisecond)
			defer cancel()
			_, _ = tr.Dial(ctx, &net.UDPAddr{IP: net.IPv4(10, 0, 0, 2), Port: 2}, &tls.Config{
				InsecureSkipVerify: true, NextProtos: []string{ALPNMesh}, ServerName: "themesh", MinVersion: tls.VersionTLS13,
			}, &quic.Config{HandshakeIdleTimeout: 200 * time.Millisecond})
		}()
	}
	wg.Wait()
	time.Sleep(300 * time.Millisecond)
	after := b.Magic().Stats(a.ID())
	if got := (after.RxDirect + after.RxRelay) - (before.RxDirect + before.RxRelay); got > 4096 {
		t.Errorf("alpha sent %d extra QUIC bytes to beta in answer to forged Initials (%d B sent)", got, conn.sent.Load())
	}
	if n := answered.Load(); n != 0 {
		t.Errorf("the forged-source stranger was answered with %d bytes", n)
	}
	// The two members are still talking, undisturbed.
	if !online(a, b)() || !online(b, a)() {
		t.Error("the members lost each other")
	}
}

// A join is one small request and one answer. Whoever holds an invitation (or has
// stolen one) must not be able to make the node keep megabytes of buffers or hundreds
// of streams for a connection of a device that is not a member yet.
func TestAJoinConnectionGetsSmallWindowsAndFewStreams(t *testing.T) {
	nw := netsim.New()
	a := newTestNode(t, nw.Internet().NewHost(ip("198.51.100.1")), "alpha", nil)
	if err := a.CreateMesh("Home", "alpha", "x"); err != nil {
		t.Fatal(err)
	}
	inv, err := a.NewInvite(false, 30*time.Minute)
	if err != nil {
		t.Fatal(err)
	}
	secret := inviteSecret(t, inv.Code)

	host := nw.Internet().NewHost(ip("198.51.100.66"))
	dev := identity.GenerateDevice()
	mg, err := magic.New(magic.Config{
		Device: dev, Port: 0,
		Listen:     func(port int) (net.PacketConn, error) { return host.ListenPacket(uint16(port)) },
		LocalAddrs: func() []netip.Addr { return host.Addrs() },
		Timing:     testTiming,
	})
	if err != nil {
		t.Fatal(err)
	}
	mg.SetAnonymous(true)
	tr := &quic.Transport{Conn: mg}
	defer func() { _ = tr.Close(); _ = mg.Close() }()
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	conn, err := tr.Dial(ctx, net.UDPAddrFromAddrPort(netip.MustParseAddrPort("198.51.100.1:41710")), &tls.Config{
		MinVersion: tls.VersionTLS13, ServerName: joinSNI(secret), InsecureSkipVerify: true,
		Certificates: []tls.Certificate{selfSignedCert(dev)}, NextProtos: []string{ALPNJoin},
	}, &quic.Config{HandshakeIdleTimeout: 3 * time.Second, MaxIdleTimeout: 20 * time.Second, EnableDatagrams: true})
	if err != nil {
		t.Fatalf("a valid token was refused: %v", err)
	}
	defer conn.CloseWithError(0, "")

	streams := 0
	var written int64
	for i := 0; i < 50; i++ {
		s, err := conn.OpenStream()
		if err != nil {
			break
		}
		streams++
		_ = s.SetWriteDeadline(time.Now().Add(300 * time.Millisecond))
		junk := make([]byte, 16<<10)
		for {
			n, err := s.Write(junk)
			written += int64(n)
			if err != nil {
				break
			}
		}
	}
	if streams > 2 {
		t.Errorf("the node accepted %d streams from a joining device, want at most 2", streams)
	}
	if written > 256<<10 {
		t.Errorf("the node took %d KiB of unread data from a joining device", written>>10)
	}
	if err := conn.SendDatagram([]byte("x")); err == nil {
		t.Error("datagrams were accepted on a join connection")
	}
}
