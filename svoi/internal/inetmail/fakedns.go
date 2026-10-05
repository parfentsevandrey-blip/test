package inetmail

import (
	"context"
	"net"
	"strings"
	"sync"
)

// FakeDNS is a DNS that lives in memory: for the tests (of this package, of the gateway and of the end-to-end checks)
// and for the demo, where a "domain" must work without a registrar. It answers like the real thing: a name without
// the record asked for is "no such host" (IsNotFound), a name made to fail is a temporary failure.
type FakeDNS struct {
	mu   sync.Mutex
	txt  map[string][]string
	mx   map[string][]*net.MX
	addr map[string][]net.IP
	ptr  map[string][]string
	fail map[string]bool
}

// NewFakeDNS returns an empty DNS.
func NewFakeDNS() *FakeDNS {
	return &FakeDNS{
		txt: map[string][]string{}, mx: map[string][]*net.MX{}, addr: map[string][]net.IP{},
		ptr: map[string][]string{}, fail: map[string]bool{},
	}
}

// AddTXT adds TXT records to name.
func (d *FakeDNS) AddTXT(name string, values ...string) {
	d.mu.Lock()
	defer d.mu.Unlock()
	d.txt[fqdn(name)] = append(d.txt[fqdn(name)], values...)
}

// SetTXT replaces the TXT records of name (no values: removes them).
func (d *FakeDNS) SetTXT(name string, values ...string) {
	d.mu.Lock()
	defer d.mu.Unlock()
	if len(values) == 0 {
		delete(d.txt, fqdn(name))
		return
	}
	d.txt[fqdn(name)] = append([]string(nil), values...)
}

// AddMX adds a mail exchanger to a domain.
func (d *FakeDNS) AddMX(domain, host string, pref uint16) {
	d.mu.Lock()
	defer d.mu.Unlock()
	d.mx[fqdn(domain)] = append(d.mx[fqdn(domain)], &net.MX{Host: fqdn(host) + ".", Pref: pref})
}

// AddA adds addresses to a host name.
func (d *FakeDNS) AddA(host string, ips ...string) {
	d.mu.Lock()
	defer d.mu.Unlock()
	for _, s := range ips {
		if ip := net.ParseIP(s); ip != nil {
			d.addr[fqdn(host)] = append(d.addr[fqdn(host)], ip)
		}
	}
}

// AddPTR sets the reverse name of an address.
func (d *FakeDNS) AddPTR(ip, host string) {
	d.mu.Lock()
	defer d.mu.Unlock()
	if p := net.ParseIP(ip); p != nil {
		d.ptr[p.String()] = append(d.ptr[p.String()], fqdn(host)+".")
	}
}

// Fail makes every lookup of name a temporary failure (or stops doing so).
func (d *FakeDNS) Fail(name string, on bool) {
	d.mu.Lock()
	defer d.mu.Unlock()
	if on {
		d.fail[fqdn(name)] = true
	} else {
		delete(d.fail, fqdn(name))
	}
}

func (d *FakeDNS) check(name string) (string, error) {
	n := fqdn(name)
	if d.fail[n] {
		return n, &net.DNSError{Err: "server misbehaving", Name: n, IsTemporary: true}
	}
	return n, nil
}

func notFound(n string) error { return &net.DNSError{Err: "no such host", Name: n, IsNotFound: true} }

// LookupTXT implements Resolver.
func (d *FakeDNS) LookupTXT(_ context.Context, name string) ([]string, error) {
	d.mu.Lock()
	defer d.mu.Unlock()
	n, err := d.check(name)
	if err != nil {
		return nil, err
	}
	if v := d.txt[n]; len(v) > 0 {
		return append([]string(nil), v...), nil
	}
	return nil, notFound(n)
}

// LookupMX implements Resolver.
func (d *FakeDNS) LookupMX(_ context.Context, name string) ([]*net.MX, error) {
	d.mu.Lock()
	defer d.mu.Unlock()
	n, err := d.check(name)
	if err != nil {
		return nil, err
	}
	if v := d.mx[n]; len(v) > 0 {
		out := make([]*net.MX, len(v))
		for i, m := range v {
			c := *m
			out[i] = &c
		}
		return out, nil
	}
	return nil, notFound(n)
}

// LookupIPAddr implements Resolver (a literal address resolves to itself).
func (d *FakeDNS) LookupIPAddr(_ context.Context, host string) ([]net.IPAddr, error) {
	if ip := net.ParseIP(strings.Trim(host, "[]")); ip != nil {
		return []net.IPAddr{{IP: ip}}, nil
	}
	d.mu.Lock()
	defer d.mu.Unlock()
	n, err := d.check(host)
	if err != nil {
		return nil, err
	}
	if v := d.addr[n]; len(v) > 0 {
		out := make([]net.IPAddr, len(v))
		for i, ip := range v {
			out[i] = net.IPAddr{IP: ip}
		}
		return out, nil
	}
	return nil, notFound(n)
}

// LookupAddr implements Resolver.
func (d *FakeDNS) LookupAddr(_ context.Context, addr string) ([]string, error) {
	d.mu.Lock()
	defer d.mu.Unlock()
	ip := net.ParseIP(addr)
	if ip == nil {
		return nil, notFound(addr)
	}
	if v := d.ptr[ip.String()]; len(v) > 0 {
		return append([]string(nil), v...), nil
	}
	return nil, notFound(addr)
}
