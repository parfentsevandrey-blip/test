package portmap

import (
	"bytes"
	"context"
	"encoding/xml"
	"errors"
	"fmt"
	"io"
	"math/rand/v2"
	"net"
	"net/http"
	"net/netip"
	"net/url"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

// UPnP Internet Gateway Device: find the router with SSDP, read its description,
// then talk SOAP to its WAN connection service.

const (
	maxBody     = 256 << 10
	maxSSDPNics = 8
)

// ssdpWait is how long we listen for answers (the search asks routers to answer
// within 2 seconds; tests shorten this).
var ssdpWait = 2500 * time.Millisecond

var igdSearchTargets = []string{
	"urn:schemas-upnp-org:device:InternetGatewayDevice:1",
	"urn:schemas-upnp-org:device:InternetGatewayDevice:2",
	"upnp:rootdevice",
}

// wanServices are the services that can map ports, best first.
var wanServices = []string{
	"urn:schemas-upnp-org:service:WANIPConnection:2",
	"urn:schemas-upnp-org:service:WANIPConnection:1",
	"urn:schemas-upnp-org:service:WANPPPConnection:1",
}

type ssdpHit struct {
	location *url.URL
	from     netip.Addr
	local    netip.Addr
}

// discoverUPnP finds an IGD on one of the local networks. The second result is
// the address of the first device that answered at all (a hint for NAT-PMP).
func discoverUPnP(ctx context.Context, cfg Config, locals []netip.Addr) (client, netip.Addr, error) {
	target, err := net.ResolveUDPAddr("udp4", cfg.ssdpTarget)
	if err != nil {
		return nil, netip.Addr{}, err
	}
	if len(locals) > maxSSDPNics {
		locals = locals[:maxSSDPNics]
	}
	hits := make(chan ssdpHit, 32)
	var wg sync.WaitGroup
	for _, la := range locals {
		la := la
		wg.Add(1)
		go func() {
			defer wg.Done()
			searchSSDP(ctx, target, la, hits)
		}()
	}
	go func() { wg.Wait(); close(hits) }()

	var firstResponder netip.Addr
	seen := map[string]bool{}
	var lastErr error
	httpc := newHTTPClient()
	for hit := range hits {
		if !firstResponder.IsValid() {
			firstResponder = hit.from
		}
		if seen[hit.location.String()] {
			continue
		}
		seen[hit.location.String()] = true
		g, err := describeIGD(ctx, httpc, hit)
		if err != nil {
			lastErr = err
			continue
		}
		return g, firstResponder, nil
	}
	if lastErr != nil {
		return nil, firstResponder, lastErr
	}
	return nil, firstResponder, errNoRouter
}

// searchSSDP sends M-SEARCH from one local address and reports valid answers.
func searchSSDP(ctx context.Context, target *net.UDPAddr, local netip.Addr, out chan<- ssdpHit) {
	conn, err := net.ListenUDP("udp4", &net.UDPAddr{IP: local.AsSlice()})
	if err != nil {
		return
	}
	defer conn.Close()
	for _, st := range igdSearchTargets {
		req := "M-SEARCH * HTTP/1.1\r\nHOST: " + target.String() + "\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: " + st + "\r\n\r\n"
		_, _ = conn.WriteToUDP([]byte(req), target)
	}
	deadline := time.Now().Add(ssdpWait)
	if d, ok := ctx.Deadline(); ok && d.Before(deadline) {
		deadline = d
	}
	_ = conn.SetReadDeadline(deadline)
	go func() { // stop reading when the caller gives up
		select {
		case <-ctx.Done():
			_ = conn.SetReadDeadline(time.Now())
		case <-time.After(ssdpWait + time.Second):
		}
	}()
	buf := make([]byte, 2048)
	for {
		n, from, err := conn.ReadFromUDPAddrPort(buf)
		if err != nil {
			return
		}
		loc, ok := parseSSDPResponse(buf[:n], from.Addr().Unmap())
		if !ok {
			continue
		}
		select {
		case out <- ssdpHit{location: loc, from: from.Addr().Unmap(), local: local}:
		case <-ctx.Done():
			return
		}
	}
}

// allowLoopbackIGD lets the tests talk to a stand-in router on 127.0.0.1. A real
// router is never on loopback: a process of this very machine that answers a search
// from there could otherwise steer us to any port of the machine's loopback.
var allowLoopbackIGD atomic.Bool

// parseSSDPResponse extracts the description URL from an SSDP answer and checks
// that it points at the device that sent it, on our own network: anything else
// would let any host on the LAN make us fetch arbitrary addresses.
func parseSSDPResponse(pkt []byte, from netip.Addr) (*url.URL, bool) {
	lines := strings.Split(string(pkt), "\r\n")
	if len(lines) < 2 || !strings.HasPrefix(strings.ToUpper(lines[0]), "HTTP/1.") {
		return nil, false
	}
	var loc string
	for _, l := range lines[1:] {
		if k, v, ok := strings.Cut(l, ":"); ok && strings.EqualFold(strings.TrimSpace(k), "location") {
			loc = strings.TrimSpace(v)
		}
	}
	u, err := url.Parse(loc)
	if err != nil || u.Scheme != "http" || u.User != nil {
		return nil, false
	}
	host, err := netip.ParseAddr(u.Hostname())
	if err != nil || host.Unmap() != from {
		return nil, false
	}
	if !(host.IsPrivate() || host.IsLinkLocalUnicast() || (allowLoopbackIGD.Load() && host.IsLoopback())) {
		return nil, false
	}
	if p := u.Port(); p != "" {
		if n, err := strconv.Atoi(p); err != nil || n < 1 || n > 65535 {
			return nil, false
		}
	}
	return u, true
}

type descDevice struct {
	Services []descService `xml:"serviceList>service"`
	Devices  []descDevice  `xml:"deviceList>device"`
}

type descService struct {
	Type    string `xml:"serviceType"`
	Control string `xml:"controlURL"`
}

type descRoot struct {
	URLBase string     `xml:"URLBase"`
	Device  descDevice `xml:"device"`
}

func (d descDevice) find(typ string) (descService, bool) {
	for _, s := range d.Services {
		if s.Type == typ && s.Control != "" {
			return s, true
		}
	}
	for _, sub := range d.Devices {
		if s, ok := sub.find(typ); ok {
			return s, true
		}
	}
	return descService{}, false
}

// describeIGD fetches the device description and builds a client for its WAN service.
func describeIGD(ctx context.Context, httpc *http.Client, hit ssdpHit) (*igd, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, hit.location.String(), nil)
	if err != nil {
		return nil, err
	}
	resp, err := httpc.Do(req)
	if err != nil {
		return nil, fmt.Errorf("reading the router's description: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("the router's description: %s", resp.Status)
	}
	raw, err := io.ReadAll(io.LimitReader(resp.Body, maxBody))
	if err != nil {
		return nil, err
	}
	var root descRoot
	dec := xml.NewDecoder(bytes.NewReader(raw))
	dec.Strict = false
	if err := dec.Decode(&root); err != nil {
		return nil, fmt.Errorf("the router's description is not XML: %w", err)
	}
	base := hit.location
	if root.URLBase != "" {
		if b, err := url.Parse(strings.TrimSpace(root.URLBase)); err == nil && b.Host != "" {
			base = b
		}
	}
	for _, typ := range wanServices {
		svc, ok := root.Device.find(typ)
		if !ok {
			continue
		}
		ctl, err := base.Parse(strings.TrimSpace(svc.Control))
		if err != nil {
			continue
		}
		// Only ever talk to the device that answered the search.
		if ctl.Scheme != "http" || ctl.Hostname() != hit.location.Hostname() {
			continue
		}
		return &igd{httpc: httpc, ctl: ctl, svc: typ, gw: hit.from, local: hit.local}, nil
	}
	return nil, errors.New("the router offers no port mapping service")
}

// igd is a UPnP router's WAN connection service.
type igd struct {
	httpc *http.Client
	ctl   *url.URL
	svc   string
	gw    netip.Addr
	local netip.Addr
}

func (g *igd) protocol() Protocol      { return UPnP }
func (g *igd) gatewayAddr() netip.Addr { return g.gw }

type soapError struct {
	Code int
	Desc string
	HTTP int
}

func (e *soapError) Error() string {
	if e.Code != 0 {
		return fmt.Sprintf("the router refused (UPnP error %d %s)", e.Code, e.Desc)
	}
	return fmt.Sprintf("the router answered HTTP %d", e.HTTP)
}

func (g *igd) soap(ctx context.Context, action string, args [][2]string) (map[string]string, error) {
	var b strings.Builder
	b.WriteString(`<?xml version="1.0"?>` + "\r\n")
	b.WriteString(`<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body>`)
	fmt.Fprintf(&b, `<u:%s xmlns:u="%s">`, action, g.svc)
	for _, a := range args {
		var esc bytes.Buffer
		_ = xml.EscapeText(&esc, []byte(a[1]))
		fmt.Fprintf(&b, "<%s>%s</%s>", a[0], esc.String(), a[0])
	}
	fmt.Fprintf(&b, `</u:%s></s:Body></s:Envelope>`, action)
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, g.ctl.String(), strings.NewReader(b.String()))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", `text/xml; charset="utf-8"`)
	req.Header.Set("SOAPAction", `"`+g.svc+`#`+action+`"`)
	resp, err := g.httpc.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	vals := leafValues(io.LimitReader(resp.Body, maxBody))
	if resp.StatusCode != http.StatusOK {
		code, _ := strconv.Atoi(vals["errorCode"])
		return nil, &soapError{Code: code, Desc: vals["errorDescription"], HTTP: resp.StatusCode}
	}
	return vals, nil
}

// leafValues collects the text of every leaf element of an XML document by its
// local name (enough for SOAP replies, which are flat).
func leafValues(r io.Reader) map[string]string {
	out := map[string]string{}
	dec := xml.NewDecoder(r)
	dec.Strict = false
	var stack []string
	for {
		tok, err := dec.Token()
		if err != nil {
			return out
		}
		switch t := tok.(type) {
		case xml.StartElement:
			stack = append(stack, t.Name.Local)
		case xml.EndElement:
			if len(stack) > 0 {
				stack = stack[:len(stack)-1]
			}
		case xml.CharData:
			if v := strings.TrimSpace(string(t)); v != "" && len(stack) > 0 {
				out[stack[len(stack)-1]] = v
			}
		}
	}
}

func (g *igd) externalIP(ctx context.Context) (netip.Addr, error) {
	vals, err := g.soap(ctx, "GetExternalIPAddress", nil)
	if err != nil {
		return netip.Addr{}, err
	}
	ip, err := netip.ParseAddr(vals["NewExternalIPAddress"])
	if err != nil {
		return netip.Addr{}, fmt.Errorf("the router reported no usable public address (%q)", vals["NewExternalIPAddress"])
	}
	return ip.Unmap(), nil
}

func (g *igd) add(ctx context.Context, external, internal, lease int) error {
	_, err := g.soap(ctx, "AddPortMapping", [][2]string{
		{"NewRemoteHost", ""},
		{"NewExternalPort", strconv.Itoa(external)},
		{"NewProtocol", "UDP"},
		{"NewInternalPort", strconv.Itoa(internal)},
		{"NewInternalClient", g.local.String()},
		{"NewEnabled", "1"},
		{"NewPortMappingDescription", "themesh"},
		{"NewLeaseDuration", strconv.Itoa(lease)},
	})
	return err
}

func (g *igd) addMapping(ctx context.Context, internalPort, wantExternal, lifetime int, permanent bool) (int, time.Duration, error) {
	candidates := []int{wantExternal}
	for len(candidates) < 6 {
		candidates = append(candidates, 20000+rand.IntN(40000))
	}
	lease := lifetime
	if permanent {
		lease = 0
	}
	for _, ext := range candidates {
		err := g.add(ctx, ext, internalPort, lease)
		var se *soapError
		if errors.As(err, &se) && se.Code == 725 && lease != 0 { // only permanent leases
			lease = 0
			err = g.add(ctx, ext, internalPort, 0)
		}
		if err == nil {
			return ext, time.Duration(lease) * time.Second, nil
		}
		if errors.As(err, &se) && (se.Code == 718 || se.Code == 724 || se.Code == 727) { // taken / needs other values
			continue
		}
		return 0, 0, err
	}
	return 0, 0, errors.New("the router refused every external port we tried")
}

func (g *igd) deleteMapping(ctx context.Context, external int) error {
	_, err := g.soap(ctx, "DeletePortMapping", [][2]string{
		{"NewRemoteHost", ""},
		{"NewExternalPort", strconv.Itoa(external)},
		{"NewProtocol", "UDP"},
	})
	return err
}
