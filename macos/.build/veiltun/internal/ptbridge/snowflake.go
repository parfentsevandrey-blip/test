package ptbridge

// The Snowflake rendezvous, raced.
//
// Snowflake reaches its broker one of two ways: a domain-fronted HTTPS request
// to a CDN mirror, or a request through Google's AMP cache. On the networks
// this app is built for one of them is usually blocked and the other is not,
// and which is which differs from network to network. Measured on one user's
// phone: on the mobile carrier the fronted request never got an answer while
// the AMP one found a proxy in about a second; on their Wi-Fi both worked.
//
// The app used to race the two by giving tor two bridge lines, one per method.
// That cannot work, and it is worth recording why, because from the outside it
// looked as if it did. Both lines name the same bridge, and tor keys bridges on
// identity: the lines became two bridge entries and two guards for one node,
// and every descriptor fetch through one of them rewrote the node's address to
// that line's placeholder (rewrite_node_address_for_bridge, bridges.c). Tor
// extends a circuit to the node's current address and reuses a connection only
// when its address matches (channel_get_for_extend, channel.c) — so whenever
// the fronted line had rewritten the address last, tor launched a new
// connection through the blocked rendezvous and let the working one sit idle.
// Half a minute of every connect, and sometimes all of it, went there.
//
// So the race lives here, inside the transport, where tor cannot see it: one
// bridge line, one bridge, and each time the client needs a proxy the two ways
// of asking for one are tried together. Whichever answered last time goes
// first; the other starts a few seconds later, and only if the first has not
// answered yet, so a network where the first way works does not ask the broker
// twice and does not waste a volunteer's proxy on an answer nobody uses.

import (
	"bytes"
	"context"
	"crypto/tls"
	"errors"
	"fmt"
	"io"
	"math/rand"
	"net"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"sync"
	"time"

	utls "github.com/refraction-networking/utls"
	pt "gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/goptlib"
	ptlog "gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/lyrebird/common/log"
	"gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/lyrebird/transports"
	"gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/lyrebird/transports/base"
	utlsutil "gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/ptutil/utls"
	sf "gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/snowflake/v2/client/lib"
	"gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/snowflake/v2/common/amp"
	"gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/snowflake/v2/common/certs"
	"gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/snowflake/v2/common/event"
	"gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/snowflake/v2/common/messages"
	"gitlab.torproject.org/tpo/anti-censorship/pluggable-transports/snowflake/v2/common/nat"
)

const (
	// A leg that is not going to answer should get out of the way. Upstream's
	// broker transport has no dial or TLS handshake timeout at all, and on a
	// network that black-holes the fronted request — the very case this race
	// exists for — such a leg hangs for as long as the kernel's connect
	// timeout, which is minutes.
	legDialTimeout = 12 * time.Second

	// The broker holds a request open while it looks for a proxy to match,
	// up to about ten seconds, and the AMP cache adds a little on top.
	legResponseTimeout = 25 * time.Second

	// The whole exchange, headers and body.
	legTotalTimeout = 40 * time.Second

	// How long the second way waits before starting, when nothing else is set.
	defaultRaceStagger = 3 * time.Second

	// Maximum number of bytes read from a broker response, as upstream.
	readLimit = 100000
)

// snowflakeRace is the alternative rendezvous the transport is given, to run
// alongside whichever one the bridge line names.
type snowflakeRace struct {
	// The fronted broker and its fronts, for a line that names an AMP cache.
	frontBroker  string
	frontDomains []string

	// The AMP triple, for a line that names a fronted broker.
	ampBroker string
	ampCache  string
	ampFronts []string

	// How long the second way waits before it starts.
	stagger time.Duration

	// What the app measured this network's NAT to be — "restricted",
	// "unrestricted" or "" for not known — to put in the broker request from
	// the first attempt. Snowflake measures the same thing itself, but in the
	// background, after the first offer has already gone out: that first
	// request says "unknown", which the client turns into a guess of
	// "unrestricted" (see NATPolicy in the client library). Behind a NAT that
	// is in fact restricted the guess pairs the client with a proxy it cannot
	// reach, and the attempt fails before the real type is known — ten to
	// fifteen seconds of a first connect, on exactly the networks where
	// Snowflake is already hardest. The app has the answer before tor is even
	// started, from the STUN survey it runs for the ICE list, so it is used.
	natType string
}

// sfEventLogger hands Snowflake's own events to the controller's callback.
type sfEventLogger struct {
	mu sync.Mutex
	cb func(e base.TransportEvent)
}

func (l *sfEventLogger) OnNewSnowflakeEvent(e event.SnowflakeEvent) {
	l.mu.Lock()
	cb := l.cb
	l.mu.Unlock()
	if cb != nil {
		cb(e)
	}
}

// snowflakeFactory is what tor's SOCKS connections are handed to. It parses
// the bridge line's arguments exactly as the Tor Project's own wrapper does,
// builds the same client, and then replaces the client's rendezvous with the
// racing one.
type snowflakeFactory struct {
	transport base.Transport
	events    *sfEventLogger
	race      snowflakeRace
	report    func(phase, detail string)
	memory    raceMemory
}

func newSnowflakeFactory(race snowflakeRace, report func(phase, detail string)) *snowflakeFactory {
	return &snowflakeFactory{
		transport: transports.Get(Snowflake),
		events:    &sfEventLogger{},
		race:      race,
		report:    report,
	}
}

func (f *snowflakeFactory) Transport() base.Transport { return f.transport }

func (f *snowflakeFactory) OnEvent(cb func(e base.TransportEvent)) {
	f.events.mu.Lock()
	f.events.cb = cb
	f.events.mu.Unlock()
}

// ParseArgs mirrors lyrebird's Snowflake wrapper: the same keys, the same
// precedence — `fronts` over `front` — so a bridge line means the same thing
// here as it does to Tor Browser.
func (f *snowflakeFactory) ParseArgs(args *pt.Args) (interface{}, error) {
	config := sf.ClientConfig{}
	if arg, ok := args.Get("ampcache"); ok {
		config.AmpCacheURL = arg
	}
	if arg, ok := args.Get("sqsqueue"); ok {
		config.SQSQueueURL = arg
	}
	if arg, ok := args.Get("sqscreds"); ok {
		config.SQSCredsStr = arg
	}
	if arg, ok := args.Get("fronts"); ok {
		if arg != "" {
			config.FrontDomains = splitList(arg)
		}
	} else if arg, ok := args.Get("front"); ok {
		config.FrontDomains = splitList(arg)
	}
	if arg, ok := args.Get("ice"); ok {
		config.ICEAddresses = splitList(arg)
	}
	if arg, ok := args.Get("max"); ok {
		max, err := strconv.Atoi(arg)
		if err != nil {
			return nil, fmt.Errorf("invalid SOCKS arg: max=%s", arg)
		}
		config.Max = max
	}
	if arg, ok := args.Get("url"); ok {
		config.BrokerURL = arg
	}
	if arg, ok := args.Get("utls-nosni"); ok {
		switch strings.ToLower(arg) {
		case "true", "yes":
			config.UTLSRemoveSNI = true
		}
	}
	if arg, ok := args.Get("utls-imitate"); ok {
		config.UTLSClientID = arg
	}
	if arg, ok := args.Get("fingerprint"); ok {
		config.BridgeFingerprint = arg
	}
	// The DTLS shape of the data path, which the Tor Project's PT wrapper
	// does not read from the line at all; the client itself does, and the
	// app sets it on every line, so it is honoured here.
	if arg, ok := args.Get("covertdtls-config"); ok {
		config.CovertDTLSConfig = arg
	}
	if arg, ok := args.Get("covertdtls-fingerprint"); ok {
		config.CovertDTLSFingerprint = arg
	}
	return config, nil
}

func (f *snowflakeFactory) Dial(network, address string, dialFn base.DialFunc, args interface{}) (net.Conn, error) {
	config, ok := args.(sf.ClientConfig)
	if !ok {
		return nil, errors.New("invalid type for args")
	}
	client, err := sf.NewSnowflakeClient(config)
	if err != nil {
		return nil, err
	}
	client.AddSnowflakeEventListener(f.events)
	if racer := f.racerFor(config); racer != nil {
		client.SetRendezvousMethod(racer)
	}
	return client.Dial()
}

// racerFor builds the race for one connection: the way the line names, and
// the other way from the controller's configuration, if there is one.
func (f *snowflakeFactory) racerFor(config sf.ClientConfig) sf.RendezvousMethod {
	if config.SQSQueueURL != "" {
		// SQS is its own thing, with its own credentials; leave it alone.
		return nil
	}
	rt := newBrokerRoundTripper(config.UTLSClientID, config.UTLSRemoveSNI)
	var legs []rendezvousLeg
	add := func(name string, m sf.RendezvousMethod, err error) {
		if err != nil {
			ptlog.Warnf("snowflake: cannot set up the %s rendezvous: %v", name, err)
			return
		}
		legs = append(legs, rendezvousLeg{name: name, method: m})
	}
	switch {
	case config.AmpCacheURL != "" && config.BrokerURL != "":
		m, err := newAMPRendezvous(config.BrokerURL, config.AmpCacheURL, config.FrontDomains, rt)
		add("amp", m, err)
		if f.race.frontBroker != "" {
			m, err := newHTTPRendezvous(f.race.frontBroker, f.race.frontDomains, rt)
			add("fronted", m, err)
		}
	case config.BrokerURL != "":
		m, err := newHTTPRendezvous(config.BrokerURL, config.FrontDomains, rt)
		add("fronted", m, err)
		if f.race.ampBroker != "" && f.race.ampCache != "" {
			m, err := newAMPRendezvous(f.race.ampBroker, f.race.ampCache, f.race.ampFronts, rt)
			add("amp", m, err)
		}
	}
	if len(legs) == 0 {
		return nil
	}
	stagger := f.race.stagger
	if stagger <= 0 {
		stagger = defaultRaceStagger
	}
	return &racingRendezvous{
		legs:    legs,
		stagger: stagger,
		memory:  &f.memory,
		report:  f.report,
		natType: f.race.natType,
	}
}

// withNATType rewrites the broker request's NAT field to what the app
// measured, when the client itself does not know yet. Once the client has
// its own answer that answer stands: it is the fresher of the two and it is
// the one the client's own retry policy reasons about.
func withNATType(encPollReq []byte, natType string) []byte {
	if natType != nat.NATRestricted && natType != nat.NATUnrestricted {
		return encPollReq
	}
	req, err := messages.DecodeClientPollRequest(encPollReq)
	if err != nil || req.NAT != nat.NATUnknown && req.NAT != nat.NATUnrestricted {
		// Either unreadable, or the client has measured "restricted" for
		// itself, which is the one answer worth more than the survey's.
		return encPollReq
	}
	if req.NAT == natType {
		return encPollReq
	}
	req.NAT = natType
	out, err := req.EncodeClientPollRequest()
	if err != nil {
		return encPollReq
	}
	return out
}

// newBrokerRoundTripper is the Tor Project's broker transport with the
// timeouts it lacks, and the same Client Hello shaping on top of it.
func newBrokerRoundTripper(utlsID string, removeSNI bool) http.RoundTripper {
	plain := &http.Transport{
		TLSClientConfig:       &tls.Config{RootCAs: certs.GetRootCAs()},
		Proxy:                 nil,
		DialContext:           (&net.Dialer{Timeout: legDialTimeout}).DialContext,
		TLSHandshakeTimeout:   legDialTimeout,
		ResponseHeaderTimeout: legResponseTimeout,
	}
	if utlsID == "" {
		return plain
	}
	id, err := utlsutil.NameToUTLSID(utlsID)
	if err != nil {
		ptlog.Warnf("snowflake: unknown utls-imitate %q; using Go's own TLS", utlsID)
		return plain
	}
	return utlsutil.NewUTLSHTTPRoundTripperWithProxy(
		id, &utls.Config{RootCAs: certs.GetRootCAs()}, plain, removeSNI, nil,
	)
}

// --- The two ways of asking ---------------------------------------------------

// httpRendezvous is the fronted request to the broker's /client route, as
// upstream's, with a deadline.
type httpRendezvous struct {
	brokerURL *url.URL
	fronts    []string
	transport http.RoundTripper
}

func newHTTPRendezvous(broker string, fronts []string, transport http.RoundTripper) (*httpRendezvous, error) {
	brokerURL, err := url.Parse(broker)
	if err != nil {
		return nil, err
	}
	return &httpRendezvous{brokerURL: brokerURL, fronts: fronts, transport: transport}, nil
}

func (r *httpRendezvous) Exchange(encPollReq []byte) ([]byte, error) {
	reqURL := r.brokerURL.ResolveReference(&url.URL{Path: "client"})
	req, err := http.NewRequest("POST", reqURL.String(), bytes.NewReader(encPollReq))
	if err != nil {
		return nil, err
	}
	ctx, cancel := context.WithTimeout(context.Background(), legTotalTimeout)
	defer cancel()
	req = req.WithContext(ctx)
	front(req, r.fronts)

	resp, err := r.transport.RoundTrip(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("broker answered %s", resp.Status)
	}
	return limitedRead(resp.Body, readLimit)
}

// ampRendezvous is the request through an AMP cache to the broker's
// /amp/client route, as upstream's, with a deadline.
type ampRendezvous struct {
	brokerURL *url.URL
	cacheURL  *url.URL
	fronts    []string
	transport http.RoundTripper
}

func newAMPRendezvous(broker, cache string, fronts []string, transport http.RoundTripper) (*ampRendezvous, error) {
	brokerURL, err := url.Parse(broker)
	if err != nil {
		return nil, err
	}
	var cacheURL *url.URL
	if cache != "" {
		cacheURL, err = url.Parse(cache)
		if err != nil {
			return nil, err
		}
	}
	return &ampRendezvous{brokerURL: brokerURL, cacheURL: cacheURL, fronts: fronts, transport: transport}, nil
}

func (r *ampRendezvous) Exchange(encPollReq []byte) ([]byte, error) {
	// A body cannot be POSTed through an AMP cache, so the request travels in
	// the path of a GET.
	reqURL := r.brokerURL.ResolveReference(&url.URL{
		Path: "amp/client/" + amp.EncodePath(encPollReq),
	})
	if r.cacheURL != nil {
		var err error
		reqURL, err = amp.CacheURL(reqURL, r.cacheURL, "c")
		if err != nil {
			return nil, err
		}
	}
	req, err := http.NewRequest("GET", reqURL.String(), nil)
	if err != nil {
		return nil, err
	}
	ctx, cancel := context.WithTimeout(context.Background(), legTotalTimeout)
	defer cancel()
	req = req.WithContext(ctx)
	front(req, r.fronts)

	resp, err := r.transport.RoundTrip(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		// The cache turns the broker's errors into a 404, and a broker page
		// that is not valid AMP into a redirect that would bypass the cache.
		return nil, fmt.Errorf("cache answered %s", resp.Status)
	}
	if _, err := resp.Location(); err == nil {
		// A "silent redirect": status 200, a Location header and a script
		// that would send a browser to the origin. Nothing usable in it.
		return nil, errors.New("cache redirected instead of answering")
	}
	lr := io.LimitReader(resp.Body, readLimit+1)
	dec, err := amp.NewArmorDecoder(lr)
	if err != nil {
		return nil, err
	}
	encPollResp, err := io.ReadAll(dec)
	if err != nil {
		return nil, err
	}
	if lr.(*io.LimitedReader).N == 0 {
		return nil, io.ErrUnexpectedEOF
	}
	return encPollResp, nil
}

// front rewrites the request for domain fronting: the real host moves into
// the Host header and a front, chosen at random, becomes the address dialled.
func front(req *http.Request, fronts []string) {
	if len(fronts) == 0 {
		return
	}
	req.Host = req.URL.Host
	req.URL.Host = fronts[rand.Intn(len(fronts))]
}

func limitedRead(r io.Reader, limit int64) ([]byte, error) {
	p, err := io.ReadAll(&io.LimitedReader{R: r, N: limit + 1})
	if err != nil {
		return p, err
	} else if int64(len(p)) == limit+1 {
		return p[0:limit], io.ErrUnexpectedEOF
	}
	return p, err
}

func splitList(s string) []string {
	var out []string
	for _, item := range strings.Split(s, ",") {
		if item = strings.TrimSpace(item); item != "" {
			out = append(out, item)
		}
	}
	return out
}

// --- The race -----------------------------------------------------------------

type rendezvousLeg struct {
	name   string
	method sf.RendezvousMethod
}

// raceMemory remembers which way answered last, across every connection the
// factory makes. Peers are collected one after another for the life of a
// session, and tor may open more than one connection; none of them should
// have to learn the same thing again.
type raceMemory struct {
	mu     sync.Mutex
	winner string
	failed map[string]time.Time
}

func (m *raceMemory) get() string {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.winner
}

func (m *raceMemory) set(name string) {
	m.mu.Lock()
	m.winner = name
	delete(m.failed, name)
	m.mu.Unlock()
}

// noteFailure records that a way did not answer, so it can be left alone
// for a while. A fronted request that the network "froze" — TCP open,
// Client Hello sent, then silence — is a request the DPI has scored, and
// repeating it inside the penalty window is reported to lengthen the
// penalty rather than get through. The other way is asked instead; this one
// is tried again only once the window has passed, or if it is all there is.
func (m *raceMemory) noteFailure(name string) {
	m.mu.Lock()
	if m.failed == nil {
		m.failed = map[string]time.Time{}
	}
	m.failed[name] = time.Now()
	m.mu.Unlock()
}

func (m *raceMemory) coolingDown(name string, window time.Duration) bool {
	m.mu.Lock()
	defer m.mu.Unlock()
	at, ok := m.failed[name]
	return ok && time.Since(at) < window
}

// How long a way that did not answer is left alone. Matches the app's own
// cooldown for a frozen endpoint, which comes from the same observations.
const legCooldown = 150 * time.Second

type racingRendezvous struct {
	legs    []rendezvousLeg
	stagger time.Duration
	memory  *raceMemory
	report  func(phase, detail string)
	natType string
}

// order puts last time's winner first. With nothing remembered the legs stay
// as configured: the line's own way, then the alternative.
func (r *racingRendezvous) order() []rendezvousLeg {
	winner := r.memory.get()
	ordered := make([]rendezvousLeg, 0, len(r.legs))
	for _, leg := range r.legs {
		if leg.name == winner {
			ordered = append(ordered, leg)
		}
	}
	for _, leg := range r.legs {
		if leg.name != winner {
			ordered = append(ordered, leg)
		}
	}
	// Leave out a way that failed recently, unless that would leave nothing.
	usable := make([]rendezvousLeg, 0, len(ordered))
	for _, leg := range ordered {
		if !r.memory.coolingDown(leg.name, legCooldown) {
			usable = append(usable, leg)
		}
	}
	if len(usable) == 0 {
		return ordered
	}
	return usable
}

type legResult struct {
	name    string
	resp    []byte
	err     error
	took    time.Duration
	skipped bool
}

// Exchange asks the broker both ways, staggered, and returns the first answer.
//
// Every leg sends exactly one result, so the collector reads exactly one per
// leg and the channel, sized for all of them, never blocks a late leg. A leg
// whose turn comes after an answer has already arrived reports itself skipped
// and never touches the network. A leg still in flight when another wins is
// abandoned; its request is bounded by the deadlines above.
func (r *racingRendezvous) Exchange(encPollReq []byte) ([]byte, error) {
	encPollReq = withNATType(encPollReq, r.natType)
	legs := r.order()
	results := make(chan legResult, len(legs))
	done := make(chan struct{})
	var closeOnce sync.Once
	finish := func() { closeOnce.Do(func() { close(done) }) }
	defer finish()
	start := time.Now()

	for i, leg := range legs {
		delay := time.Duration(i) * r.stagger
		go func(leg rendezvousLeg, delay time.Duration) {
			if delay > 0 {
				select {
				case <-time.After(delay):
				case <-done:
					results <- legResult{name: leg.name, skipped: true}
					return
				}
			}
			resp, err := leg.method.Exchange(encPollReq)
			results <- legResult{name: leg.name, resp: resp, err: err, took: time.Since(start)}
		}(leg, delay)
	}

	var failures []string
	for range legs {
		res := <-results
		if res.skipped {
			continue
		}
		if res.err == nil {
			r.memory.set(res.name)
			if r.report != nil {
				r.report("rendezvous-via", fmt.Sprintf("%s in %dms", res.name, res.took.Milliseconds()))
			}
			return res.resp, nil
		}
		r.memory.noteFailure(res.name)
		failures = append(failures, fmt.Sprintf("%s: %v (%dms)", res.name, res.err, res.took.Milliseconds()))
	}
	return nil, errors.New("no way of reaching the broker answered: " + strings.Join(failures, "; "))
}
