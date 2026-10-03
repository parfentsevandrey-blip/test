package portmap

import (
	"encoding/binary"
	"encoding/xml"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"net/netip"
	"strconv"
	"strings"
	"sync"
	"testing"
	"time"
)

// ---- a fake router: SSDP responder + UPnP IGD + NAT-PMP ----

type fakeRouter struct {
	t   *testing.T
	wan string // the public address it reports

	mu            sync.Mutex
	mappings      map[int]string // external port -> internal client:port
	calls         []string       // "Action ext lease"
	onlyPermanent bool
	taken         map[int]bool
	refuse        bool // every AddPortMapping fails (the router "forgot" us)

	http     *httptest.Server
	ssdp     *net.UDPConn
	locHost  string // host put in LOCATION (default: the real one)
	ssdpHits int
}

func newFakeRouter(t *testing.T, wan string) *fakeRouter {
	f := &fakeRouter{t: t, wan: wan, mappings: map[int]string{}, taken: map[int]bool{}}
	mux := http.NewServeMux()
	mux.HandleFunc("/desc.xml", func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprint(w, `<?xml version="1.0"?><root xmlns="urn:schemas-upnp-org:device-1-0"><device>
<deviceType>urn:schemas-upnp-org:device:InternetGatewayDevice:1</deviceType>
<deviceList><device><deviceType>urn:schemas-upnp-org:device:WANDevice:1</deviceType>
<deviceList><device><deviceType>urn:schemas-upnp-org:device:WANConnectionDevice:1</deviceType>
<serviceList><service><serviceType>urn:schemas-upnp-org:service:WANIPConnection:1</serviceType>
<controlURL>/ctl/IPConn</controlURL></service></serviceList></device></deviceList></device></deviceList></device></root>`)
	})
	mux.HandleFunc("/ctl/IPConn", f.soap)
	f.http = httptest.NewServer(mux)
	t.Cleanup(f.http.Close)

	conn, err := net.ListenUDP("udp4", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1)})
	if err != nil {
		t.Fatal(err)
	}
	f.ssdp = conn
	t.Cleanup(func() { conn.Close() })
	go func() {
		buf := make([]byte, 2048)
		for {
			n, from, err := conn.ReadFromUDP(buf)
			if err != nil {
				return
			}
			if !strings.HasPrefix(string(buf[:n]), "M-SEARCH") {
				continue
			}
			f.mu.Lock()
			f.ssdpHits++
			host := f.locHost
			f.mu.Unlock()
			if host == "" {
				host = f.http.Listener.Addr().String()
			}
			_, _ = conn.WriteToUDP([]byte("HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=120\r\nLOCATION: http://"+host+"/desc.xml\r\nST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n\r\n"), from)
		}
	}()
	return f
}

func (f *fakeRouter) soap(w http.ResponseWriter, r *http.Request) {
	body, _ := io.ReadAll(r.Body)
	vals := leafValues(strings.NewReader(string(body)))
	action := r.Header.Get("SOAPAction")
	action = action[strings.Index(action, "#")+1 : len(action)-1]
	f.mu.Lock()
	defer f.mu.Unlock()
	fail := func(code int, desc string) {
		w.WriteHeader(500)
		fmt.Fprintf(w, `<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><s:Fault><detail><UPnPError xmlns="urn:schemas-upnp-org:control-1-0"><errorCode>%d</errorCode><errorDescription>%s</errorDescription></UPnPError></detail></s:Fault></s:Body></s:Envelope>`, code, desc)
	}
	ext, _ := strconv.Atoi(vals["NewExternalPort"])
	switch action {
	case "GetExternalIPAddress":
		fmt.Fprintf(w, `<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:GetExternalIPAddressResponse xmlns:u="x"><NewExternalIPAddress>%s</NewExternalIPAddress></u:GetExternalIPAddressResponse></s:Body></s:Envelope>`, f.wan)
	case "AddPortMapping":
		f.calls = append(f.calls, fmt.Sprintf("Add %d lease=%s", ext, vals["NewLeaseDuration"]))
		switch {
		case f.refuse:
			fail(501, "ActionFailed")
		case f.onlyPermanent && vals["NewLeaseDuration"] != "0":
			fail(725, "OnlyPermanentLeasesSupported")
		case f.taken[ext]:
			fail(718, "ConflictInMappingEntry")
		case vals["NewProtocol"] != "UDP" || vals["NewEnabled"] != "1":
			fail(402, "Invalid Args")
		default:
			f.mappings[ext] = vals["NewInternalClient"] + ":" + vals["NewInternalPort"]
			fmt.Fprint(w, `<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:AddPortMappingResponse xmlns:u="x"/></s:Body></s:Envelope>`)
		}
	case "DeletePortMapping":
		f.calls = append(f.calls, fmt.Sprintf("Delete %d", ext))
		if _, ok := f.mappings[ext]; !ok {
			fail(714, "NoSuchEntryInArray")
			return
		}
		delete(f.mappings, ext)
		fmt.Fprint(w, `<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:DeletePortMappingResponse xmlns:u="x"/></s:Body></s:Envelope>`)
	default:
		fail(401, "Invalid Action")
	}
}

func (f *fakeRouter) snapshot() (map[int]string, []string) {
	f.mu.Lock()
	defer f.mu.Unlock()
	m := map[int]string{}
	for k, v := range f.mappings {
		m[k] = v
	}
	return m, append([]string(nil), f.calls...)
}

// ---- helpers ----

func init() {
	ssdpWait = 300 * time.Millisecond // the fake answers at once
	allowLoopbackIGD.Store(true)      // and lives on loopback
}

var loopback = []netip.Addr{netip.MustParseAddr("127.0.0.1")}

func testConfig(f *fakeRouter, changed func(*Mapping)) Config {
	return Config{
		Port:       41710,
		LocalAddrs: func() []netip.Addr { return loopback },
		Changed:    changed,
		ssdpTarget: f.ssdp.LocalAddr().String(),
		gateway:    func() netip.Addr { return netip.Addr{} },
		retry:      []time.Duration{50 * time.Millisecond},
	}
}

func waitFor(t *testing.T, what string, cond func() bool) {
	t.Helper()
	deadline := time.Now().Add(8 * time.Second)
	for time.Now().Before(deadline) {
		if cond() {
			return
		}
		time.Sleep(10 * time.Millisecond)
	}
	t.Fatalf("timed out waiting for %s", what)
}

type recorder struct {
	mu   sync.Mutex
	last *Mapping
	n    int
}

func (r *recorder) changed(m *Mapping) {
	r.mu.Lock()
	r.last, r.n = m, r.n+1
	r.mu.Unlock()
}
func (r *recorder) get() *Mapping { r.mu.Lock(); defer r.mu.Unlock(); return r.last }

// ---- tests ----

func TestMapsThroughUPnPAndRemovesTheMappingOnClose(t *testing.T) {
	f := newFakeRouter(t, "203.0.113.7")
	var rec recorder
	m := Start(testConfig(f, rec.changed))
	waitFor(t, "a mapping", func() bool { return rec.get() != nil })
	mp := rec.get()
	if mp.External != netip.MustParseAddrPort("203.0.113.7:41710") || mp.Protocol != UPnP {
		t.Fatalf("mapping: %+v", mp)
	}
	st := m.Status()
	if st.State != "mapped" || st.External != "203.0.113.7:41710" || st.Protocol != UPnP {
		t.Fatalf("status: %+v", st)
	}
	maps, calls := f.snapshot()
	if maps[41710] != "127.0.0.1:41710" {
		t.Fatalf("the router maps %v (calls %v)", maps, calls)
	}
	if len(calls) == 0 || !strings.Contains(calls[0], "lease=3600") {
		t.Fatalf("the first request should be a lease of an hour: %v", calls)
	}
	m.Close()
	maps, calls = f.snapshot()
	if len(maps) != 0 {
		t.Fatalf("the mapping was left on the router after Close: %v (calls %v)", maps, calls)
	}
	if rec.get() != nil {
		t.Fatal("the owner was not told that the mapping is gone")
	}
}

func TestFallsBackToAPermanentLease(t *testing.T) {
	f := newFakeRouter(t, "203.0.113.7")
	f.onlyPermanent = true
	var rec recorder
	m := Start(testConfig(f, rec.changed))
	defer m.Close()
	waitFor(t, "a mapping", func() bool { return rec.get() != nil })
	_, calls := f.snapshot()
	if len(calls) != 2 || !strings.Contains(calls[0], "lease=3600") || !strings.Contains(calls[1], "lease=0") {
		t.Fatalf("expected a timed request, then a permanent one: %v", calls)
	}
}

func TestPicksAnotherPortWhenOursIsTaken(t *testing.T) {
	f := newFakeRouter(t, "203.0.113.7")
	f.taken[41710] = true
	var rec recorder
	m := Start(testConfig(f, rec.changed))
	defer m.Close()
	waitFor(t, "a mapping", func() bool { return rec.get() != nil })
	mp := rec.get()
	if mp.External.Port() == 41710 || mp.External.Port() < 20000 {
		t.Fatalf("expected another external port, got %v", mp.External)
	}
	maps, _ := f.snapshot()
	if maps[int(mp.External.Port())] != "127.0.0.1:41710" {
		t.Fatalf("the router maps %v, the mapper says %v", maps, mp.External)
	}
}

func TestARoutersPrivateAddressIsNotAdvertised(t *testing.T) {
	for _, wan := range []string{"10.1.2.3", "192.168.0.5", "100.72.1.1", "0.0.0.0", "172.16.5.5"} {
		f := newFakeRouter(t, wan)
		var rec recorder
		m := Start(testConfig(f, rec.changed))
		waitFor(t, "the verdict for "+wan, func() bool { return m.Status().State == "private" })
		m.Close()
		maps, _ := f.snapshot()
		if rec.get() != nil || len(maps) != 0 {
			t.Fatalf("%s: a mapping that cannot make us reachable was made or advertised (%v, %v)", wan, rec.get(), maps)
		}
	}
}

// Any host on the LAN can answer an SSDP search with any URL. Only the device
// that answered, on our own network, is ever contacted.
func TestIgnoresADescriptionThatIsNotAtTheResponder(t *testing.T) {
	f := newFakeRouter(t, "203.0.113.7")
	var otherHits int
	var mu sync.Mutex
	other, err := net.Listen("tcp4", "127.0.0.2:0")
	if err != nil {
		t.Skip("no second loopback address here:", err)
	}
	srv := &http.Server{Handler: http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { mu.Lock(); otherHits++; mu.Unlock() })}
	go srv.Serve(other)
	defer srv.Close()
	f.locHost = other.Addr().String() // the responder (127.0.0.1) points us at 127.0.0.2
	var rec recorder
	m := Start(testConfig(f, rec.changed))
	waitFor(t, "an answer to be seen", func() bool { f.mu.Lock(); defer f.mu.Unlock(); return f.ssdpHits > 0 })
	waitFor(t, "the verdict", func() bool { return m.Status().State == "unavailable" })
	m.Close()
	mu.Lock()
	defer mu.Unlock()
	if otherHits != 0 || rec.get() != nil {
		t.Fatalf("followed a LOCATION that is not the responder (%d requests, mapping %v)", otherHits, rec.get())
	}
}

func TestRenewsTheLease(t *testing.T) {
	f := newFakeRouter(t, "203.0.113.7")
	var rec recorder
	cfg := testConfig(f, rec.changed)
	cfg.renewEvery = 40 * time.Millisecond
	m := Start(cfg)
	defer m.Close()
	waitFor(t, "several renewals", func() bool {
		_, calls := f.snapshot()
		adds := 0
		for _, c := range calls {
			if strings.HasPrefix(c, "Add") {
				adds++
			}
		}
		return adds >= 4
	})
	if st := m.Status(); st.State != "mapped" {
		t.Fatalf("status %+v", st)
	}
}

func TestRecoversWhenTheRouterForgetsUs(t *testing.T) {
	f := newFakeRouter(t, "203.0.113.7")
	var rec recorder
	cfg := testConfig(f, rec.changed)
	cfg.renewEvery = 40 * time.Millisecond
	m := Start(cfg)
	defer m.Close()
	waitFor(t, "a mapping", func() bool { return rec.get() != nil })
	f.mu.Lock()
	f.refuse = true
	f.mu.Unlock()
	waitFor(t, "the loss to be noticed", func() bool { return rec.get() == nil })
	f.mu.Lock()
	f.refuse = false
	f.mu.Unlock()
	waitFor(t, "the mapping to come back", func() bool { return rec.get() != nil })
}

func TestNoRouterAtAll(t *testing.T) {
	dead, err := net.ListenUDP("udp4", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1)})
	if err != nil {
		t.Fatal(err)
	}
	target := dead.LocalAddr().String()
	dead.Close() // nobody listens there
	var rec recorder
	m := Start(Config{
		Port: 41710, LocalAddrs: func() []netip.Addr { return loopback }, Changed: rec.changed,
		ssdpTarget: target, gateway: func() netip.Addr { return netip.Addr{} }, retry: []time.Duration{time.Hour},
	})
	waitFor(t, "the verdict", func() bool { return m.Status().State == "unavailable" })
	start := time.Now()
	m.Close()
	if d := time.Since(start); d > 2*time.Second {
		t.Fatalf("Close took %v with no router", d)
	}
	if rec.get() != nil {
		t.Fatal("a mapping out of nothing")
	}
}

// ---- NAT-PMP ----

type fakePMP struct {
	conn    *net.UDPConn
	mu      sync.Mutex
	reqs    []string
	granted int // the external port it grants (0: the one asked for)
	wan     [4]byte
}

func newFakePMP(t *testing.T, granted int) *fakePMP {
	conn, err := net.ListenUDP("udp4", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1)})
	if err != nil {
		t.Fatal(err)
	}
	f := &fakePMP{conn: conn, wan: [4]byte{203, 0, 113, 9}, granted: granted}
	t.Cleanup(func() { conn.Close() })
	go func() {
		buf := make([]byte, 64)
		for {
			n, from, err := conn.ReadFromUDP(buf)
			if err != nil {
				return
			}
			switch {
			case n == 2 && buf[0] == 0 && buf[1] == 0:
				rep := make([]byte, 12)
				rep[1] = 128
				copy(rep[8:], f.wan[:])
				conn.WriteToUDP(rep, from)
			case n == 12 && buf[0] == 0 && buf[1] == 1:
				internal := binary.BigEndian.Uint16(buf[4:])
				ext := binary.BigEndian.Uint16(buf[6:])
				life := binary.BigEndian.Uint32(buf[8:])
				f.mu.Lock()
				f.reqs = append(f.reqs, fmt.Sprintf("map int=%d ext=%d life=%d", internal, ext, life))
				if f.granted != 0 {
					ext = uint16(f.granted)
				}
				f.mu.Unlock()
				rep := make([]byte, 16)
				rep[1] = 129
				binary.BigEndian.PutUint16(rep[8:], internal)
				binary.BigEndian.PutUint16(rep[10:], ext)
				binary.BigEndian.PutUint32(rep[12:], life)
				conn.WriteToUDP(rep, from)
			}
		}
	}()
	return f
}

func TestNATPMP(t *testing.T) {
	g := newFakePMP(t, 41999)
	dead, _ := net.ListenUDP("udp4", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1)})
	target := dead.LocalAddr().String()
	dead.Close() // no UPnP here
	var rec recorder
	m := Start(Config{
		Port: 41710, LocalAddrs: func() []netip.Addr { return loopback }, Changed: rec.changed,
		ssdpTarget: target, gateway: func() netip.Addr { return loopback[0] }, pmpPort: g.conn.LocalAddr().(*net.UDPAddr).Port,
		retry: []time.Duration{50 * time.Millisecond},
	})
	waitFor(t, "a mapping", func() bool { return rec.get() != nil })
	mp := rec.get()
	if mp.External != netip.MustParseAddrPort("203.0.113.9:41999") || mp.Protocol != NATPMP {
		t.Fatalf("mapping: %+v", mp)
	}
	m.Close()
	g.mu.Lock()
	defer g.mu.Unlock()
	if len(g.reqs) < 2 || g.reqs[0] != "map int=41710 ext=41710 life=3600" || !strings.HasSuffix(g.reqs[len(g.reqs)-1], "life=0") {
		t.Fatalf("requests: %v", g.reqs)
	}
}

// ---- pure functions ----

func TestIsPublicIPv4(t *testing.T) {
	for ip, want := range map[string]bool{
		"8.8.8.8": true, "203.0.113.5": true, "1.1.1.1": true, "93.184.216.34": true,
		"10.0.0.1": false, "172.16.0.1": false, "172.31.255.255": false, "192.168.1.1": false,
		"100.64.0.1": false, "100.127.255.255": false, "100.128.0.1": true,
		"127.0.0.1": false, "169.254.1.1": false, "0.0.0.0": false, "224.0.0.1": false, "255.255.255.255": false,
		"198.18.0.1": false, "198.20.0.1": true, "240.0.0.1": false,
	} {
		if got := isPublicIPv4(netip.MustParseAddr(ip)); got != want {
			t.Errorf("isPublicIPv4(%s) = %v, want %v", ip, got, want)
		}
	}
	if isPublicIPv4(netip.MustParseAddr("2001:db8::1")) {
		t.Error("an IPv6 address is not a public IPv4 address")
	}
}

func TestParseSSDPResponse(t *testing.T) {
	from := netip.MustParseAddr("192.168.1.1")
	ok := "HTTP/1.1 200 OK\r\nLocation: http://192.168.1.1:5000/rootDesc.xml\r\nST: x\r\n\r\n"
	if u, good := parseSSDPResponse([]byte(ok), from); !good || u.Host != "192.168.1.1:5000" {
		t.Fatalf("a good answer: %v %v", u, good)
	}
	for name, pkt := range map[string]string{
		"other host":     "HTTP/1.1 200 OK\r\nLOCATION: http://192.168.1.99:5000/d.xml\r\n\r\n",
		"public host":    "HTTP/1.1 200 OK\r\nLOCATION: http://8.8.8.8/d.xml\r\n\r\n",
		"https":          "HTTP/1.1 200 OK\r\nLOCATION: https://192.168.1.1/d.xml\r\n\r\n",
		"credentials":    "HTTP/1.1 200 OK\r\nLOCATION: http://user:pw@192.168.1.1/d.xml\r\n\r\n",
		"a name":         "HTTP/1.1 200 OK\r\nLOCATION: http://router.lan/d.xml\r\n\r\n",
		"no location":    "HTTP/1.1 200 OK\r\nST: x\r\n\r\n",
		"bad port":       "HTTP/1.1 200 OK\r\nLOCATION: http://192.168.1.1:99999/d.xml\r\n\r\n",
		"not a response": "NOTIFY * HTTP/1.1\r\nLOCATION: http://192.168.1.1/d.xml\r\n\r\n",
		"garbage":        "\x00\x01\x02",
	} {
		if _, good := parseSSDPResponse([]byte(pkt), from); good {
			t.Errorf("%s was accepted", name)
		}
	}
}

// A router is never on 127.0.0.1: a process of this machine that answers an SSDP search
// from there must not be able to make us talk to ports of the loopback interface.
func TestALoopbackRouterIsRefusedUnlessTheTestsAllowIt(t *testing.T) {
	allowLoopbackIGD.Store(false)
	defer allowLoopbackIGD.Store(true)
	pkt := "HTTP/1.1 200 OK\r\nLOCATION: http://127.0.0.1:5000/rootDesc.xml\r\nST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n\r\n"
	if _, good := parseSSDPResponse([]byte(pkt), netip.MustParseAddr("127.0.0.1")); good {
		t.Fatal("a description on loopback was accepted")
	}
	lan := strings.ReplaceAll(pkt, "127.0.0.1", "192.168.1.1")
	if _, good := parseSSDPResponse([]byte(lan), netip.MustParseAddr("192.168.1.1")); !good {
		t.Fatal("a description on the LAN was refused")
	}
}

func TestDescriptionIsFoundWhereverTheServiceSits(t *testing.T) {
	var root descRoot
	if err := xmlUnmarshal(`<root><URLBase>http://192.168.1.1:49152/</URLBase><device><deviceList><device><deviceList><device><serviceList>
<service><serviceType>urn:schemas-upnp-org:service:WANPPPConnection:1</serviceType><controlURL>/ctl/ppp</controlURL></service>
</serviceList></device></deviceList></device></deviceList></device></root>`, &root); err != nil {
		t.Fatal(err)
	}
	svc, ok := root.Device.find("urn:schemas-upnp-org:service:WANPPPConnection:1")
	if !ok || svc.Control != "/ctl/ppp" || root.URLBase != "http://192.168.1.1:49152/" {
		t.Fatalf("%+v %v %q", svc, ok, root.URLBase)
	}
	if _, ok := root.Device.find("urn:schemas-upnp-org:service:WANIPConnection:1"); ok {
		t.Fatal("found a service that is not there")
	}
}

func TestSoapErrorsAreParsed(t *testing.T) {
	vals := leafValues(strings.NewReader(`<s:Envelope xmlns:s="x"><s:Body><s:Fault><faultcode>s:Client</faultcode><detail><UPnPError xmlns="y"><errorCode>725</errorCode><errorDescription>OnlyPermanentLeasesSupported</errorDescription></UPnPError></detail></s:Fault></s:Body></s:Envelope>`))
	if vals["errorCode"] != "725" || vals["errorDescription"] != "OnlyPermanentLeasesSupported" {
		t.Fatalf("%v", vals)
	}
	// Truncated or hostile XML must not hang or panic.
	_ = leafValues(strings.NewReader("<a><b>text"))
	_ = leafValues(strings.NewReader(strings.Repeat("<a>", 100000)))
}

func xmlUnmarshal(s string, v any) error {
	dec := xml.NewDecoder(strings.NewReader(s))
	dec.Strict = false
	return dec.Decode(v)
}
