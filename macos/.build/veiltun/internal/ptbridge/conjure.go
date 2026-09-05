package ptbridge

// Conjure, added on top of the vendored IPtProxy controller.
//
// Every other transport here connects to a bridge whose address is written on
// the bridge line, which is also the thing a censor blocks. Conjure does not
// have one. The client registers with a station operated inside a cooperating
// ISP, is given a "phantom" address in that ISP's space that nobody is
// listening on, and connects to it; the station recognises the connection in
// passing and diverts it. From the network's point of view the client opened a
// connection to an ordinary unremarkable host that happens not to answer.
//
// There is therefore nothing to add to a blocklist short of the ISP itself,
// and unlike Snowflake it is ordinary TCP — so a mobile carrier's NAT, which
// is what usually stops Snowflake working on mobile data, does not touch it.
//
// The registration and dialling logic mirrors the Tor Project's own Conjure
// client (BSD-3), but is written out here rather than imported, for a reason
// worth recording: that client pulls in Conjure's transport *registry*, and the
// registry imports a second, forked copy of obfs4. Both copies register a
// command-line flag called `obfs4-distBias` in `init()`, so linking them
// together panics the moment the Go runtime starts — which on Android means the
// app dies as the library loads, whether or not anyone asked for Conjure.
// Building the two transports we actually use avoids the registry, and with it
// both the clash and a forked DTLS library we would otherwise have to carry.

import (
	"bytes"
	"context"
	"crypto/tls"
	"errors"
	"fmt"
	"io"
	mrand "math/rand"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/refraction-networking/conjure/pkg/client/assets"
	"github.com/refraction-networking/conjure/pkg/core/interfaces"
	"veil.app/veiltun/internal/ptbridge/conjurereg"
	"github.com/refraction-networking/conjure/pkg/transports/wrapping/min"
	"github.com/refraction-networking/conjure/pkg/transports/wrapping/prefix"
	pb "github.com/refraction-networking/conjure/proto"
	"github.com/refraction-networking/gotapdance/tapdance"
	utls "github.com/refraction-networking/utls"
	pt "gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/goptlib"
	ptlog "gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/lyrebird/common/log"
	utlsutil "gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/ptutil/utls"
	"gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/snowflake/v2/common/certs"
)

// Conjure - Refraction networking transport.
const Conjure = "conjure"

const (
	// The station rejects rather than queues when it is busy, so retrying is
	// the expected path rather than an error case.
	conjureRetryInterval = 8 * time.Second

	// Enough attempts to ride out a busy station, few enough that a route that
	// is genuinely blocked gives the ladder its turn back.
	conjureAttempts = 4

	conjureRegistrationTimeout = 20 * time.Second
)

// conjureConfig is everything the bridge line can say about how to register.
type conjureConfig struct {
	registrar     string
	registerURL   string
	fronts        []string
	ampCacheURL   string
	bridgeAddress string
	utlsClientID  string
	utlsRemoveSNI bool
	transport     string
	stunAddr      string
}

func (c *Controller) startConjure() error {
	// The compiled-in station key and decoy list are installed by this call
	// before it goes looking for a file to override them with, so the error it
	// returns on a first run — there being no such file yet — is not a failure
	// and must not be treated as one. Upstream's client ignores it for the same
	// reason.
	if _, err := assets.AssetsSetDir(c.stateDir); err != nil {
		ptlog.Noticef("conjure: using built-in station configuration (%s)", err.Error())
	}

	ln, err := pt.ListenSocks("tcp", "127.0.0.1:0")
	if err != nil {
		ptlog.Errorf("Failed to initialize %s: %s", Conjure, err.Error())
		return err
	}

	c.listeners[Conjure] = ln
	c.shutdown[Conjure] = make(chan struct{})

	go conjureAcceptLoop(ln, c.shutdown[Conjure], c.transportEvents)

	return nil
}

func conjureAcceptLoop(ln *pt.SocksListener, shutdown chan struct{}, events OnTransportEvents) {
	defer ln.Close()
	for {
		conn, err := ln.AcceptSocks()
		if err != nil {
			var e net.Error
			if errors.As(err, &e) && !e.Temporary() {
				return
			}
			continue
		}
		go conjureHandler(conn, shutdown, events)
	}
}

func conjureHandler(conn *pt.SocksConn, shutdown chan struct{}, events OnTransportEvents) {
	defer conn.Close()

	config := &conjureConfig{
		// The bidirectional API registrar, reached through a fronted request.
		// The alternatives — an AMP cache, or DNS — are selected by the bridge
		// line, because which of them survives is a property of the network
		// rather than of the client.
		registrar: "bdapi",
		// "prefix" makes the phantom connection start with the first bytes of
		// some ordinary protocol rather than with randomness, which is what a
		// classifier looking for "high entropy from the first byte" keys on.
		transport:     "prefix",
		bridgeAddress: conn.Req.Target,
	}
	applyConjureArgs(conn, config)

	bridgeAddr, err := net.ResolveTCPAddr("tcp", conn.Req.Target)
	if err != nil {
		_ = conn.Reject()
		conjureReport(events, func(e OnTransportEvents) { e.Error(Conjure, err) })
		return
	}

	// Granted before the phantom exists on purpose. Registration is a fronted
	// request to a station that may be under load and can take tens of seconds;
	// the alternative is leaving tor with an unanswered SOCKS request for that
	// whole time, which it reads as a broken proxy rather than a slow one.
	if err := conn.Grant(bridgeAddr); err != nil {
		conjureReport(events, func(e OnTransportEvents) { e.Error(Conjure, err) })
		return
	}

	phantom := newPendingConn()
	go func() {
		defer phantom.finishDialling()
		for attempt := 0; attempt < conjureAttempts; attempt++ {
			c, err := conjureRegister(config)
			if err == nil {
				if phantom.attach(c) {
					conjureReport(events, func(e OnTransportEvents) { e.Connected(Conjure) })
				} else {
					_ = c.Close()
				}
				return
			}
			ptlog.Warnf("conjure: registration failed: %s", err.Error())
			conjureReport(events, func(e OnTransportEvents) { e.Error(Conjure, err) })
			select {
			case <-time.After(conjureRetryInterval):
			case <-shutdown:
				return
			case <-phantom.done:
				return
			}
		}
	}()

	conjureProxy(conn, phantom)
	conjureReport(events, func(e OnTransportEvents) { e.Stopped(Conjure, nil) })
}

// applyConjureArgs reads what the bridge line said. Everything Conjure needs
// beyond the bridge address travels there, which is what lets the app change
// registration method without restarting anything.
func applyConjureArgs(conn *pt.SocksConn, config *conjureConfig) {
	if arg, ok := conn.Req.Args.Get("registrar"); ok {
		config.registrar = arg
	}
	if arg, ok := conn.Req.Args.Get("ampcache"); ok {
		config.ampCacheURL = arg
	}
	if arg, ok := conn.Req.Args.Get("url"); ok {
		config.registerURL = arg
	}
	if arg, ok := conn.Req.Args.Get("fronts"); ok {
		if arg != "" {
			config.fronts = strings.Split(strings.TrimSpace(arg), ",")
		}
	} else if arg, ok := conn.Req.Args.Get("front"); ok {
		config.fronts = strings.Split(strings.TrimSpace(arg), ",")
	}
	if arg, ok := conn.Req.Args.Get("utls-imitate"); ok {
		config.utlsClientID = arg
	}
	if arg, ok := conn.Req.Args.Get("utls-nosni"); ok {
		switch strings.ToLower(arg) {
		case "true", "yes":
			config.utlsRemoveSNI = true
		}
	}
	if arg, ok := conn.Req.Args.Get("transport"); ok {
		config.transport = arg
	}
	if arg, ok := conn.Req.Args.Get("stun"); ok {
		config.stunAddr = arg
	}
}

// conjureRegister obtains a phantom and connects to it.
func conjureRegister(config *conjureConfig) (net.Conn, error) {
	dialer := &tapdance.Dialer{
		// Conjure rather than plain TapDance: connect to phantom addresses
		// instead of to a decoy that has to terminate the connection.
		DarkDecoy: true,
		// The station tells the bridge who we are, so the bridge does not see
		// every client as coming from the station.
		UseProxyHeader: true,
		V6Support:      false,
	}

	registrar, err := conjureRegistrar(config)
	if err != nil {
		return nil, err
	}
	dialer.DarkDecoyRegistrar = registrar

	dialer.TransportConfig, err = conjureTransport(config.transport)
	if err != nil {
		return nil, err
	}

	ctx, cancel := context.WithTimeout(context.Background(), conjureRegistrationTimeout)
	defer cancel()
	return dialer.DialContext(ctx, "tcp", config.bridgeAddress)
}

// conjureTransport builds the phantom-side wrapping directly, without going
// through Conjure's registry — see the note at the top of this file.
func conjureTransport(name string) (interfaces.Transport, error) {
	switch name {
	case "prefix":
		randomize := true
		id := int32(-1)
		t := &prefix.ClientTransport{}
		if err := t.SetParams(
			&pb.PrefixTransportParams{RandomizeDstPort: &randomize, PrefixId: &id},
		); err != nil {
			return nil, err
		}
		return t, nil

	case "min", "":
		t := &min.ClientTransport{}
		if err := t.SetParams(&pb.GenericTransportParams{}); err != nil {
			return nil, err
		}
		return t, nil

	default:
		return nil, fmt.Errorf("conjure: unsupported transport %q", name)
	}
}

func conjureRegistrar(config *conjureConfig) (tapdance.Registrar, error) {
	transport := conjureRegistrationTransport()
	if config.utlsClientID != "" {
		helloID, err := utlsutil.NameToUTLSID(config.utlsClientID)
		if err != nil {
			return nil, fmt.Errorf("conjure: unknown client hello %q", config.utlsClientID)
		}
		transport = utlsutil.NewUTLSHTTPRoundTripperWithProxy(
			helloID,
			&utls.Config{RootCAs: certs.GetRootCAs()},
			transport,
			config.utlsRemoveSNI,
			nil,
		)
	}

	client := &http.Client{
		Transport: &conjureRendezvous{
			registerURL: config.registerURL,
			fronts:      config.fronts,
			transport:   transport,
		},
	}

	regConfig := &conjurereg.Config{
		Bidirectional: true,
		HTTPClient:    client,
		STUNAddr:      config.stunAddr,
	}

	switch config.registrar {
	case "ampcache":
		if config.ampCacheURL == "" {
			return nil, errors.New("conjure: AMP cache registrar with no cache URL")
		}
		regConfig.Target = config.registerURL + "/amp/register-bidirectional"
		regConfig.AMPCacheURL = config.ampCacheURL
		return conjurereg.NewAMPCacheRegistrar(regConfig)

	case "dns":
		// Nothing but a working DNS-over-HTTPS resolver is needed here, which
		// is close to the last thing a network can take away and still be a
		// network. The parameters come from the compiled-in station config.
		dnsConf := assets.Assets().GetDNSRegConf()
		if dnsConf == nil || dnsConf.Target == nil || dnsConf.Domain == nil {
			return nil, errors.New("conjure: no DNS registration configuration")
		}
		pubkey := dnsConf.Pubkey
		if pubkey == nil {
			key := assets.Assets().GetConjurePubkey()
			if key == nil {
				return nil, errors.New("conjure: no station key")
			}
			pubkey = key[:]
		}
		method := conjurereg.DoH
		if dnsConf.DnsRegMethod != nil {
			switch *dnsConf.DnsRegMethod {
			case pb.DnsRegMethod_UDP:
				method = conjurereg.UDP
			case pb.DnsRegMethod_DOT:
				method = conjurereg.DoT
			default:
				method = conjurereg.DoH
			}
		}
		if dnsConf.UtlsDistribution != nil {
			regConfig.UTLSDistribution = *dnsConf.UtlsDistribution
		}
		if dnsConf.StunServer != nil {
			regConfig.STUNAddr = *dnsConf.StunServer
		}
		regConfig.DNSTransportMethod = method
		regConfig.Target = *dnsConf.Target
		regConfig.BaseDomain = *dnsConf.Domain
		regConfig.Pubkey = pubkey
		regConfig.MaxRetries = 3
		return conjurereg.NewDNSRegistrar(regConfig)

	default:
		if config.registerURL == "" {
			return nil, errors.New("conjure: no registration URL")
		}
		regConfig.Target = config.registerURL + "/api/register-bidirectional"
		return conjurereg.NewAPIRegistrar(regConfig)
	}
}

// conjureRendezvous puts a front domain in the TLS handshake and the real
// registration host in the HTTP request, so the connection looks like traffic
// to whatever large site the front names.
type conjureRendezvous struct {
	registerURL string
	fronts      []string
	transport   http.RoundTripper
}

func (r *conjureRendezvous) RoundTrip(req *http.Request) (*http.Response, error) {
	if len(r.fronts) > 0 {
		front := r.fronts[randomIndex(len(r.fronts))]
		req.Host = req.URL.Host
		req.URL.Host = front
	}
	return r.transport.RoundTrip(req)
}

// A copy of http.DefaultTransport's behaviour minus the environment proxy,
// which on Android would be whatever the network last advertised.
func conjureRegistrationTransport() http.RoundTripper {
	return &http.Transport{
		TLSClientConfig:       &tls.Config{RootCAs: certs.GetRootCAs()},
		Proxy:                 nil,
		ResponseHeaderTimeout: 15 * time.Second,
	}
}

// --- Holding tor's bytes while registration happens ----------------------

// pendingConn accepts writes before the phantom connection exists and replays
// them once it does. Tor starts its handshake the moment the SOCKS request is
// granted, and registration takes far longer than that; without somewhere to
// put those first bytes they would be lost.
type pendingConn struct {
	mu      sync.Mutex
	ready   *sync.Cond
	conn    net.Conn
	buffer  bytes.Buffer
	closed  bool
	dialled bool
	done    chan struct{}
}

func newPendingConn() *pendingConn {
	p := &pendingConn{done: make(chan struct{})}
	p.ready = sync.NewCond(&p.mu)
	return p
}

// attach installs the real connection and flushes whatever arrived first.
func (p *pendingConn) attach(conn net.Conn) bool {
	p.mu.Lock()
	defer p.mu.Unlock()
	if p.closed || p.conn != nil {
		return false
	}
	if p.buffer.Len() > 0 {
		if _, err := conn.Write(p.buffer.Bytes()); err != nil {
			return false
		}
		p.buffer.Reset()
	}
	p.conn = conn
	p.ready.Broadcast()
	return true
}

// finishDialling reports that no further attempt will be made, so a reader
// waiting for a connection that is never coming is released.
func (p *pendingConn) finishDialling() {
	p.mu.Lock()
	defer p.mu.Unlock()
	if !p.dialled {
		p.dialled = true
		p.ready.Broadcast()
	}
}

func (p *pendingConn) Write(b []byte) (int, error) {
	p.mu.Lock()
	if p.closed {
		p.mu.Unlock()
		return 0, net.ErrClosed
	}
	if p.conn == nil {
		defer p.mu.Unlock()
		return p.buffer.Write(b)
	}
	conn := p.conn
	p.mu.Unlock()
	return conn.Write(b)
}

func (p *pendingConn) Read(b []byte) (int, error) {
	p.mu.Lock()
	for p.conn == nil && !p.closed && !p.dialled {
		p.ready.Wait()
	}
	if p.closed {
		p.mu.Unlock()
		return 0, net.ErrClosed
	}
	conn := p.conn
	p.mu.Unlock()
	if conn == nil {
		return 0, errors.New("conjure: registration did not succeed")
	}
	return conn.Read(b)
}

func (p *pendingConn) Close() error {
	p.mu.Lock()
	if p.closed {
		p.mu.Unlock()
		return nil
	}
	p.closed = true
	conn := p.conn
	p.ready.Broadcast()
	close(p.done)
	p.mu.Unlock()
	if conn != nil {
		return conn.Close()
	}
	return nil
}

func conjureProxy(socks, phantom io.ReadWriteCloser) {
	var wg sync.WaitGroup
	wg.Add(2)
	copyOne := func(dst, src io.ReadWriteCloser) {
		defer wg.Done()
		if _, err := io.Copy(dst, src); err != nil && !errors.Is(err, io.ErrClosedPipe) {
			ptlog.Warnf("conjure: copy ended: %s", err.Error())
		}
		_ = socks.Close()
		_ = phantom.Close()
	}
	go copyOne(socks, phantom)
	go copyOne(phantom, socks)
	wg.Wait()
}

// randomIndex picks a front. Which one is used should not be predictable from
// the outside, but it does not need to be unguessable either.
func randomIndex(n int) int {
	if n <= 1 {
		return 0
	}
	return mrand.Intn(n)
}

func conjureReport(events OnTransportEvents, f func(OnTransportEvents)) {
	if events == nil {
		return
	}
	go f(events)
}
